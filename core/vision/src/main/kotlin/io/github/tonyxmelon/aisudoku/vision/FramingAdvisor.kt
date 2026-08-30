package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs

/** One line of on-screen advice, and whether the framing is good enough to capture. */
data class Guidance(val message: String, val readyToCapture: Boolean)

/**
 * Turns a preview frame into exactly one instruction.
 *
 * These are cheap proxies and they do not decide whether a photograph is acceptable -
 * that is judged after recognition, on whether extraction actually worked. Their job is
 * to steer the camera toward a shot worth taking.
 *
 * The order matters: the first failing condition is the one reported, so the user is
 * given one thing to fix rather than a list.
 */
class FramingAdvisor(
    /** Consecutive good frames required before the shutter fires. */
    private val stableFramesRequired: Int = 5,
    /** How far a corner may drift between frames and still count as steady, in pixels. */
    private val steadyTolerance: Double = 12.0,
) {

    private var lastQuad: Quad? = null
    private var stableFrames = 0

    /** Resets the stability counter, e.g. after a capture. */
    fun reset() {
        lastQuad = null
        stableFrames = 0
    }

    fun advise(frame: GrayImage): Guidance {
        val located = GridLocator.locate(frame)
        if (located !is GridLocation.Found) {
            reset()
            return Guidance("Point the camera at a sudoku puzzle", false)
        }

        val quad = located.quad
        val quality = ImageQuality.of(located.rectified)

        val shortestSide = minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        val frameShortSide = minOf(frame.width, frame.height).toDouble()

        val complaint = when {
            quality.meanLuma < 60 -> "More light needed"
            quality.clippedWhiteFraction > 0.04 -> "Avoid the glare"
            touchesEdge(quad, frame) -> "Fit the whole grid in view"
            shortestSide < frameShortSide * 0.30 -> "Move closer"
            shortestSide > frameShortSide * 0.98 -> "Move back"
            quad.maxCornerAngleDeviation > 18.0 -> "Hold the phone flat"
            abs(quad.rotationDegrees) > 15.0 -> "Straighten up"
            else -> null
        }

        if (complaint != null) {
            reset()
            return Guidance(complaint, false)
        }

        val previous = lastQuad
        val steady = previous != null && quad.corners.zip(previous.corners).all { (a, b) ->
            abs(a.x - b.x) <= steadyTolerance && abs(a.y - b.y) <= steadyTolerance
        }
        stableFrames = if (steady) stableFrames + 1 else 0
        lastQuad = quad

        return if (stableFrames >= stableFramesRequired) {
            Guidance("Hold still...", true)
        } else {
            Guidance("Hold still...", false)
        }
    }

    private fun touchesEdge(quad: Quad, frame: GrayImage): Boolean {
        val marginX = frame.width * 0.01
        val marginY = frame.height * 0.01
        return quad.corners.any {
            it.x <= marginX || it.y <= marginY ||
                it.x >= frame.width - marginX || it.y >= frame.height - marginY
        }
    }
}
