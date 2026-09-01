package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Where the grid is, or why it could not be found. */
sealed interface GridLocation {

    data class Found(
        val quad: Quad,
        val gridScore: Double,
        val rectified: GrayImage,
    ) : GridLocation

    /**
     * No candidate contained anything resembling a 9x9 grid.
     *
     * [best] is the shape it came closest on, kept so the camera can draw what it is
     * looking at. Being shown the app outlining the book instead of the puzzle explains a
     * failure that no wording ever could.
     */
    data class NoGrid(
        val bestScore: Double,
        val candidatesConsidered: Int,
        val best: Quad? = null,
    ) : GridLocation
}

/**
 * Finds the sudoku grid in a photograph and straightens it.
 *
 * Candidates come from [QuadDetector] ordered by size; the one chosen is whichever most
 * looks like a grid once rectified, which is not usually the largest.
 */
object GridLocator {

    const val RECTIFIED_SIZE = 1152          // 128 pixels per cell
    const val MIN_GRID_SCORE = 0.35

    /**
     * Smallest square a candidate is scored on: 32 pixels a cell, enough for lines.
     *
     * Scoring happens at the grid's own size rather than at [RECTIFIED_SIZE], and this is
     * the floor for that. Below it there is not enough of a line left to measure.
     */
    private const val MIN_SCORING_SIZE = 288

    fun locate(image: GrayImage): GridLocation {
        val full = image.toMat()
        val candidates = QuadDetector.detect(image)

        // Scored at the size the grid actually is in the photograph, not blown up to the
        // working size first.
        //
        // Every candidate used to be stretched to 1152 square before scoring, which is
        // three or four times life size for a grid on a preview frame - and a line
        // upscaled that far is a soft ramp rather than a line. The score is the weakest of
        // the twenty lines a grid must have, so one line lost to blur takes the whole
        // score down with it: a perfectly framed puzzle scored 0.71 filling the frame and
        // 0.32 at half that size, and the second one was rejected. Reported from the phone
        // as a grid the app would not see however it was held.
        //
        // Scoring small also means only the winner is stretched to the working size, which
        // is most of the work this function used to do on every frame.
        val scored = candidates.map { quad -> quad to GridScorer.score(rectify(full, quad, scoringSize(quad))) }

        val winner = scored.maxByOrNull { it.second }
            ?: return GridLocation.NoGrid(bestScore = 0.0, candidatesConsidered = 0)

        if (winner.second < MIN_GRID_SCORE) {
            return GridLocation.NoGrid(winner.second, candidates.size, winner.first)
        }
        val rectified = rectify(full, winner.first, RECTIFIED_SIZE.toDouble())
        return GridLocation.Found(winner.first, winner.second, rectified.toGrayImage())
    }

    /** The grid's own size in the photograph, within the bounds worth working at. */
    private fun scoringSize(quad: Quad): Double {
        val longest = maxOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        return longest.coerceIn(MIN_SCORING_SIZE.toDouble(), RECTIFIED_SIZE.toDouble())
    }

    /** Warps [quad] out of the full-resolution image onto a square of [side] pixels. */
    internal fun rectify(full: Mat, quad: Quad, side: Double = RECTIFIED_SIZE.toDouble()): Mat {
        val transform = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*quad.corners.map { Point(it.x, it.y) }.toTypedArray()),
            MatOfPoint2f(
                Point(0.0, 0.0), Point(side, 0.0),
                Point(side, side), Point(0.0, side),
            ),
        )
        val out = Mat()
        Imgproc.warpPerspective(full, out, transform, Size(side, side))
        return out
    }
}
