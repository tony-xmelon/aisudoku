package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/** Pixel bounds of one cell in the rectified image. */
data class CellBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where the ten vertical and ten horizontal grid lines actually are, in rectified
 * coordinates.
 */
data class CellGeometry(
    val verticalLines: List<Double>,
    val horizontalLines: List<Double>,
) {
    init {
        require(verticalLines.size == 10) { "expected 10 vertical lines, got ${verticalLines.size}" }
        require(horizontalLines.size == 10) { "expected 10 horizontal lines, got ${horizontalLines.size}" }
    }

    /**
     * Bounds of cell [index] (row-major), inset so the printed lines themselves are
     * excluded. Ink that touches a grid line would otherwise be mistaken for a digit.
     */
    fun cellBounds(index: Int, marginFraction: Double = 0.12): CellBounds {
        val row = index / 9
        val column = index % 9
        val left = verticalLines[column]
        val right = verticalLines[column + 1]
        val top = horizontalLines[row]
        val bottom = horizontalLines[row + 1]
        val marginX = (right - left) * marginFraction
        val marginY = (bottom - top) * marginFraction
        return CellBounds(
            left = (left + marginX).toInt(),
            top = (top + marginY).toInt(),
            right = (right - marginX).toInt(),
            bottom = (bottom - marginY).toInt(),
        )
    }
}

/**
 * Locates the real grid lines in a rectified image.
 *
 * A single perspective transform assumes the page was flat. Two corpus photographs are
 * not - one sheet is curled, another is bowed over a clipboard - so the true lines drift
 * from an even ninth division and cropping by ninths clips digits near the edges.
 *
 * Each line is found by taking the strongest ink projection within a window around where
 * it ought to be, then refining to the intensity-weighted centre of that peak.
 */
object GridLineFitter {

    private const val SEARCH_WINDOW_FRACTION = 0.18

    /** Returns null when a line could not be found near one of the expected positions. */
    fun fit(rectified: GrayImage): CellGeometry? {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            rectified.toMat(), binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0,
        )
        val (columns, rows) = GridScorer.projections(binary)
        val vertical = fitAxis(columns) ?: return null
        val horizontal = fitAxis(rows) ?: return null
        return CellGeometry(vertical, horizontal)
    }

    private fun fitAxis(profile: DoubleArray): List<Double>? {
        val size = profile.size
        val strongest = profile.max()
        if (strongest <= 0.0) return null

        val window = (size / 9.0 * SEARCH_WINDOW_FRACTION).toInt().coerceAtLeast(4)

        val lines = (0..9).map { line ->
            val centre = (line * (size - 1.0) / 9.0).toInt()
            val from = (centre - window).coerceAtLeast(0)
            val to = (centre + window).coerceAtMost(size - 1)

            val peak = (from..to).maxOf { profile[it] }
            if (peak < strongest * 0.20) return null

            // Intensity-weighted centre of everything near the peak, so a line two or
            // three pixels wide resolves to its middle rather than its first pixel.
            val cutoff = peak * 0.6
            var weighted = 0.0
            var weight = 0.0
            for (i in from..to) {
                if (profile[i] >= cutoff) {
                    weighted += i * profile[i]
                    weight += profile[i]
                }
            }
            if (weight <= 0.0) return null
            weighted / weight
        }

        // Ordering can break if two expected windows lock onto the same thick line.
        if (lines != lines.sorted()) return null
        if (lines.zipWithNext().any { (a, b) -> abs(b - a) < size / 9.0 * 0.4 }) return null
        return lines
    }
}
