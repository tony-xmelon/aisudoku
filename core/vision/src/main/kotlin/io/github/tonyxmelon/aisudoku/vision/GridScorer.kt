package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Measures how much a rectified image looks like a 9x9 sudoku grid.
 *
 * The score is the weakest of the twenty places a grid line must appear, expressed as a
 * fraction of the strongest line present. A real grid has all twenty; a rectified sheet
 * of paper has the puzzle somewhere inside it and therefore misses several.
 *
 * Counting line peaks was tried first and does not work: a thick outer border splits
 * into two peaks and a column of digit strokes can align into a spurious one, so an
 * "exactly ten" rule rejected three of six good photographs. Asking whether a line is
 * present where one is *required* is insensitive to extras.
 */
internal object GridScorer {

    /** How far either side of an expected line position to look, as a fraction of a cell. */
    private const val SEARCH_WINDOW_FRACTION = 0.18

    fun score(rectified: Mat): Double {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            rectified, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0,
        )
        val (columns, rows) = projections(binary)
        return minOf(axisScore(columns), axisScore(rows))
    }

    /** Ink counts per column and per row of a binarised square image. */
    fun projections(binary: Mat): Pair<DoubleArray, DoubleArray> {
        val size = binary.rows()
        val buffer = ByteArray(size * size)
        binary.get(0, 0, buffer)

        val columns = DoubleArray(size)
        val rows = DoubleArray(size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (buffer[y * size + x].toInt() != 0) {
                    columns[x]++
                    rows[y]++
                }
            }
        }
        return columns to rows
    }

    /** The weakest of the ten required lines along one axis, relative to the strongest. */
    private fun axisScore(profile: DoubleArray): Double {
        val strongest = profile.max()
        if (strongest <= 0.0) return 0.0

        val size = profile.size
        val window = (size / 9.0 * SEARCH_WINDOW_FRACTION).toInt().coerceAtLeast(4)

        return (0..9).minOf { line ->
            val centre = (line * (size - 1.0) / 9.0).toInt()
            val from = (centre - window).coerceAtLeast(0)
            val to = (centre + window).coerceAtMost(size - 1)
            (from..to).maxOf { profile[it] } / strongest
        }
    }
}
