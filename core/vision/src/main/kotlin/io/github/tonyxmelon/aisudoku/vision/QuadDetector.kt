package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds quadrilaterals that might be a sudoku grid, largest first.
 *
 * This deliberately does not decide. In one corpus photograph the sheet of paper is a
 * larger and cleaner quadrilateral than the grid printed on it, so any detector that
 * returns a single "best" answer by size returns the wrong one. [GridLocator] chooses,
 * using evidence this stage cannot see.
 *
 * All parameters below were tuned against the corpus; see the plan for measurements.
 */
object QuadDetector {

    /** Longest edge the detection pass works at. Full resolution is wasted here and slow. */
    private const val WORKING_EDGE = 1000.0

    /** Ignore anything smaller than this share of the frame. */
    private const val MIN_AREA_FRACTION = 0.03

    private const val MAX_CANDIDATES = 10

    fun detect(image: GrayImage): List<Quad> {
        val full = image.toMat()
        val scale = WORKING_EDGE / maxOf(full.width(), full.height()).toDouble()

        val small = Mat()
        Imgproc.resize(full, small, Size(full.width() * scale, full.height() * scale))

        val blurred = Mat()
        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)

        // Inverted, so ink becomes white and the grid lines form a connected structure.
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0,
        )

        // Close small gaps where a printed line is broken by paper texture or a fold.
        val closed = Mat()
        Imgproc.morphologyEx(
            binary, closed, Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(closed, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val frameArea = small.width().toDouble() * small.height()
        return contours
            .filter { Imgproc.contourArea(it) > MIN_AREA_FRACTION * frameArea }
            .sortedByDescending { Imgproc.contourArea(it) }
            .take(MAX_CANDIDATES)
            .map { contour -> toQuad(contour, scale) }
    }

    /**
     * Reduces a contour to four corners in full-resolution coordinates.
     *
     * Polygon approximation usually gives exactly four points. When it does not - a
     * curled page produces a bowed outline that needs more - the extreme points of the
     * contour are used instead, which yields sensible corners for any convex blob.
     */
    private fun toQuad(contour: MatOfPoint, scale: Double): Quad {
        val asFloat = MatOfPoint2f(*contour.toArray())
        val perimeter = Imgproc.arcLength(asFloat, true)
        val approximated = MatOfPoint2f()
        Imgproc.approxPolyDP(asFloat, approximated, 0.02 * perimeter, true)

        val points = if (approximated.toArray().size == 4) approximated.toArray() else contour.toArray()
        return Quad.ordering(points.map { Corner(it.x / scale, it.y / scale) })
    }
}
