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

    /**
     * Ignore anything smaller than this share of the frame.
     *
     * A judgement, not a measurement. It was 0.08, and the viewfinder crops the frame, so
     * a grid filling the screen is a good deal smaller in the picture this sees - which
     * argues for room below 0.08. It was then dropped to 0.03 on the strength of a
     * synthetic scene that later measurements showed to be a poor model of a real table,
     * and that build is the one where the reader started outlining slivers of tablecloth.
     * Halfway back, with [couldBeAGrid] now keeping the junk out on shape rather than on
     * size, is as far as the evidence supports.
     */
    private const val MIN_AREA_FRACTION = 0.05

    private const val MAX_CANDIDATES = 10

    /**
     * Shortest edge over longest, below which a shape is too oblong to be a grid.
     *
     * A sudoku is square. Perspective shortens the far edge, and a photograph taken from
     * a sensible angle keeps this well above a half.
     */
    private const val MIN_EDGE_RATIO = 0.45

    /** How far a corner may sit from a right angle before the shape is not a square. */
    private const val MAX_CORNER_SKEW = 40.0

    /**
     * How much of its own bounding quad a contour must fill.
     *
     * The test that throws out wiggly blobs. A contour that wanders over a patterned
     * tablecloth can enclose a large area while filling very little of the box around it,
     * and reducing one to four corners produces a quadrilateral that means nothing - in
     * one report, a sliver drawn clean across the grid.
     */
    private const val MIN_FILL = 0.65

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

        // Sorted by size, but filtered for shape first. Taking the ten largest contours
        // and hoping one is the grid works on a clean table and fails on a patterned one:
        // the background supplies a dozen contours bigger than the puzzle, the grid never
        // reaches the list, and the reader confidently outlines a tablecloth. Whether a
        // shape could be a sudoku is cheap to ask and does not depend on what else is in
        // the picture.
        return contours
            .filter { Imgproc.contourArea(it) > MIN_AREA_FRACTION * frameArea }
            .sortedByDescending { Imgproc.contourArea(it) }
            .mapNotNull { contour ->
                val quad = toQuad(contour, scale)
                quad.takeIf { couldBeAGrid(it, Imgproc.contourArea(contour), scale) }
            }
            .take(MAX_CANDIDATES)
    }

    /** Square enough, upright enough, and solid enough to be worth scoring. */
    private fun couldBeAGrid(quad: Quad, contourArea: Double, scale: Double): Boolean {
        val edges = listOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        val shortest = edges.min()
        val longest = edges.max()
        if (longest <= 0.0 || shortest / longest < MIN_EDGE_RATIO) return false
        if (quad.maxCornerAngleDeviation > MAX_CORNER_SKEW) return false

        // The contour is measured at working scale and the quad in full-resolution
        // coordinates, so one of them has to be brought to the other.
        val quadArea = quad.area * scale * scale
        return quadArea > 0.0 && contourArea / quadArea >= MIN_FILL
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

        // Four corners from the approximation when it gives them, and the smallest
        // rotated rectangle around the contour when it does not. The extremes of a
        // wandering contour used to stand in here, and for anything but a bowed page they
        // are not corners of anything.
        val points = approximated.toArray().takeIf { it.size == 4 }
            ?: Imgproc.minAreaRect(asFloat).let { box ->
                Array(4) { org.opencv.core.Point() }.also { box.points(it) }
            }
        return Quad.ordering(points.map { Corner(it.x / scale, it.y / scale) })
    }
}
