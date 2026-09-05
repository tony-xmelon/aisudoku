package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Measures how much a rectified image looks like a 9x9 sudoku grid.
 *
 * The score is the weakest of the twenty places a grid line must appear, expressed as a
 * fraction of a typical line. A real grid has all twenty; a rectified sheet of paper has
 * the puzzle somewhere inside it and therefore misses several.
 *
 * Being the weakest of twenty is what makes it strict, and also what makes a single
 * obscured line - a thumb on a corner, a fold, a highlight across a screen - fatal to a
 * grid whose other nineteen are perfect. [obscuredAllowed] is how that is forgiven, and
 * it is deliberately not the default.
 *
 * Counting line peaks was tried first and does not work: a thick outer border splits
 * into two peaks and a column of digit strokes can align into a spurious one, so an
 * "exactly ten" rule rejected three of six good photographs. Asking whether a line is
 * present where one is *required* is insensitive to extras.
 */
internal object GridScorer {

    /** How far either side of an expected line position to look, as a fraction of a cell. */
    private const val SEARCH_WINDOW_FRACTION = 0.18

    fun score(rectified: Mat, obscuredAllowed: Int = 0): Double {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            rectified, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0,
        )
        val (columns, rows) = projections(binary)
        return minOf(axisScore(columns, obscuredAllowed), axisScore(rows, obscuredAllowed))
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

    /**
     * Ink a line must have above the page around it before it counts as a line at all,
     * as a fraction of the grid's span.
     */
    private const val REAL_LINE_FRACTION = 0.25

    /**
     * The weakest of the ten required lines along one axis, against a typical one.
     *
     * Two things had to change here, and the first is why a well-framed puzzle could sit
     * in the middle of the viewfinder and never be seen.
     *
     * The strength of a line is how far it stands above the page around it, not how much
     * ink it has: a column through a row of digits carries ink too. Measuring the rise
     * above the median column - which is a cell interior - is what separates a line from a
     * busy column of handwriting.
     *
     * And each line is judged against a typical line rather than against the strongest
     * one. The strongest is the outer border, drawn two or three times the width of the
     * inner lines, so dividing by it asks every thin line to be as inky as the thick one.
     * That bar rises as the grid gets smaller in the frame, until a perfectly framed
     * puzzle scores 0.3 for no reason except how far away it is.
     *
     * The absolute floor is what keeps the ratio honest. Comparing lines only with each
     * other, a blank square scores full marks - it has ten equally missing lines - which
     * is exactly what the framing advisor started reporting as a grid.
     */
    private fun axisScore(profile: DoubleArray, obscuredAllowed: Int): Double {
        val size = profile.size
        val window = (size / 9.0 * SEARCH_WINDOW_FRACTION).toInt().coerceAtLeast(4)
        val page = profile.sorted()[size / 2]

        val strengths = (0..9).map { line ->
            val centre = (line * (size - 1.0) / 9.0).toInt()
            val from = (centre - window).coerceAtLeast(0)
            val to = (centre + window).coerceAtMost(size - 1)
            ((from..to).maxOf { profile[it] } - page).coerceAtLeast(0.0)
        }

        val typical = strengths.sorted()[strengths.size / 2]
        if (typical < size * REAL_LINE_FRACTION) return 0.0

        // Capped at one: a line inkier than the typical one is not better than a grid
        // line, it is a grid line. Only the missing ones should count against the score.
        //
        // [obscuredAllowed] sets aside that many of the weakest lines before taking the
        // worst of the rest. At zero this is the plain minimum, which is what every
        // photograph that reads today is scored by and what ranks one candidate against
        // another - relaxing it for ranking is not harmless, and cost a corpus page when
        // it was tried, because a worse quad with no single terrible line outranked the
        // right one. Above zero it is an accept test of last resort: see
        // [GridLocator.askAgainForAnObscuredGrid].
        val ranked = strengths.map { (it / typical).coerceAtMost(1.0) }.sorted()
        return ranked[obscuredAllowed.coerceIn(0, ranked.size - 1)]
    }
}
