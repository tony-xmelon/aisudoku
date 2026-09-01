package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs

/** One line of on-screen advice, and whether the framing is good enough to capture. */
data class Guidance(
    val message: String,
    val readyToCapture: Boolean,
    /**
     * The shape the reader is looking at, as fractions of the frame's width and height.
     *
     * Drawn over the preview so that what the app can see is not a matter of guesswork.
     * Present even when the shape was rejected: a rectangle round the book instead of the
     * puzzle says more about what to do next than any sentence.
     */
    val outline: List<Corner>? = null,
    /** True when the outline is a grid the app accepts, rather than its best guess. */
    val outlineAccepted: Boolean = false,
)

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

    private companion object {
        /** Mean luma below which the lines are lost in the dark, on a 0..255 scale. */
        const val DARK = 60.0

        /**
         * Blown-out fraction of a whole preview frame that counts as glare.
         *
         * Higher than the 0.04 used once a grid is found, because that measures the grid
         * alone and this measures everything in shot - a bright window behind the page is
         * not glare on the page.
         */
        const val GLARE = 0.08
    }

    /** Resets the stability counter, e.g. after a capture. */
    fun reset() {
        lastQuad = null
        stableFrames = 0
    }

    fun advise(frame: GrayImage): Guidance {
        val located = GridLocator.locate(frame)
        if (located !is GridLocation.Found) {
            reset()
            val missed = located as? GridLocation.NoGrid
            return Guidance(
                whyNoGrid(frame, missed),
                readyToCapture = false,
                outline = missed?.best?.let { normalise(it, frame) },
            )
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

        val outline = normalise(quad, frame)
        if (complaint != null) {
            reset()
            return Guidance(complaint, false, outline, outlineAccepted = true)
        }

        val previous = lastQuad
        val steady = previous != null && quad.corners.zip(previous.corners).all { (a, b) ->
            abs(a.x - b.x) <= steadyTolerance && abs(a.y - b.y) <= steadyTolerance
        }
        stableFrames = if (steady) stableFrames + 1 else 0
        lastQuad = quad

        return Guidance(
            "Hold still...",
            readyToCapture = stableFrames >= stableFramesRequired,
            outline = outline,
            outlineAccepted = true,
        )
    }

    /** A quad in pixels, as fractions of the frame, so the screen can place it. */
    private fun normalise(quad: Quad, frame: GrayImage): List<Corner> =
        quad.corners.map { Corner(it.x / frame.width, it.y / frame.height) }

    /**
     * What to say when no grid was found, which is more than "point the camera at one".
     *
     * That line was the only answer to every failure, including a puzzle filling the
     * frame - which tells the user to do the thing they are already doing. The checks for
     * light and glare sat on the other side of the branch and so never ran in the one case
     * where the picture was too dark or too shiny to find a grid in at all.
     *
     * What is left is the honest distinction the locator can actually draw: whether it saw
     * anything square-cornered in the frame, and how close the best of them came to
     * reading as nine rows and nine columns.
     */
    private fun whyNoGrid(frame: GrayImage, missed: GridLocation.NoGrid?): String {
        val quality = ImageQuality.of(frame)
        return when {
            quality.meanLuma < DARK -> "Too dark to make out the grid lines"

            quality.clippedWhiteFraction > GLARE ->
                "Glare is washing the lines out - tilt the page away from the light"

            missed == null || missed.candidatesConsidered == 0 ->
                "Point the camera at a sudoku puzzle"

            missed.bestScore >= GridLocator.MIN_GRID_SCORE * 0.6 ->
                "Almost - hold the page flat and square to the camera"

            else -> "That is not reading as a nine by nine grid. Try to fill the frame " +
                "with the grid alone, straight on and flat"
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
