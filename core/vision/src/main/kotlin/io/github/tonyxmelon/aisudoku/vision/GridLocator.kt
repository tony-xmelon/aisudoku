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

    /** No candidate contained anything resembling a 9x9 grid. */
    data class NoGrid(val bestScore: Double, val candidatesConsidered: Int) : GridLocation
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

    fun locate(image: GrayImage): GridLocation {
        val full = image.toMat()
        val candidates = QuadDetector.detect(image)

        val scored = candidates.map { quad ->
            val rectified = rectify(full, quad)
            GridLocation.Found(quad, GridScorer.score(rectified), rectified.toGrayImage())
        }

        val winner = scored.maxByOrNull { it.gridScore }
            ?: return GridLocation.NoGrid(bestScore = 0.0, candidatesConsidered = 0)

        return if (winner.gridScore < MIN_GRID_SCORE) {
            GridLocation.NoGrid(winner.gridScore, candidates.size)
        } else {
            winner
        }
    }

    /** Warps [quad] out of the full-resolution image onto a square. */
    internal fun rectify(full: Mat, quad: Quad): Mat {
        val side = RECTIFIED_SIZE.toDouble()
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
