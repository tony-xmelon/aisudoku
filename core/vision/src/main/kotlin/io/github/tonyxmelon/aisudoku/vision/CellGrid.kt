package io.github.tonyxmelon.aisudoku.vision

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds the grid by its cells, for when its border cannot be traced.
 *
 * Everything else in the locator starts from the grid's outline, and on newsprint the
 * outline is often not there to start from: the border is printed hard against the column
 * of text beside it, or against the black bar the paper runs down the edge of the puzzle,
 * and the threshold welds them into one shape that is neither square nor the grid. Two
 * corpus photographs failed in exactly that way, and on both of them the eighty-one cells
 * were perfectly clear - which is the point. A border can be lost to whatever is printed
 * next to it. The cells cannot, because they are holes inside it.
 *
 * So the cells are found instead - contours with a parent, of much the same size, packed
 * together - grouped so that a second puzzle on the same page becomes a second group, and
 * the grid's corners are solved from where the cells are rather than measured off an
 * outline.
 *
 * The solving is the part that matters, and it is not the obvious thing. Fitting four
 * corners to the outside of the cell cloud does not work: four extreme points are four
 * measurements, and one cell whose contour broke moves one of them and shears the whole
 * rectification. Measured on these photographs, every four-point fit tried - polygon
 * approximation, the smallest enclosing rectangle, the widest inscribed quadrilateral -
 * scored 0.00, and the straightened pictures they produced were plainly askew. Eighty-one
 * cells are eighty-one measurements of one projection, and the homography fitted to all of
 * them scores 0.54 and 0.48 on the two.
 *
 * It does not rescue everything. One photograph is of a page curved enough that no single
 * plane fits its own cells - the best homography leaves them 9.7 pixels out on average and
 * 30 at worst, on a cell 65 pixels across - and that is a fact about the sheet of paper
 * rather than about the search. It is left failing.
 */
internal object CellGrid {

    /** Smallest hole worth calling a cell, as a fraction of the working frame. */
    private const val MIN_CELL_FRACTION = 1.0 / 2000

    /** How far from the median a hole's area may be and still be one of the same cells. */
    private const val SMALLEST_ALIKE = 0.5
    private const val LARGEST_ALIKE = 2.0

    /**
     * How far apart two cells may be and still belong to the same puzzle, in cell widths.
     *
     * Cells of one grid touch, so anything much over one width is a gap, and a second
     * puzzle beside the first sits further off than this. Separating them is not optional:
     * one cloud spread over two grids has no nine by nine to fit.
     */
    private const val SAME_GRID_SPACING = 1.6

    /** Fewest cells worth trying to fit a nine by nine to. */
    private const val ENOUGH_CELLS = 30

    /** Fewest lattice places filled before the homography is worth solving. */
    private const val ENOUGH_PLACES = 20

    /** How many times to read each cell's place back through the fit and fit again. */
    private const val REFITS = 3

    /** Candidate grids the cells suggest, at one working size. */
    fun detect(image: GrayImage, workingEdge: Double): List<Quad> =
        lattices(image, workingEdge).map { it.quad() }

    /** The same, with each cell's place still attached, for measuring what went on. */
    internal fun lattices(image: GrayImage, workingEdge: Double): List<Lattice> {
        val full = image.toMat()
        val scale = workingEdge / maxOf(full.width(), full.height()).toDouble()

        val small = Mat()
        Imgproc.resize(full, small, Size(full.width() * scale, full.height() * scale))
        val blurred = Mat()
        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0,
        )
        val closed = Mat()
        Imgproc.morphologyEx(
            binary, closed, Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
        val frameArea = small.width().toDouble() * small.height()

        // A cell is a hole in the grid: in two-level hierarchy terms, a contour with a
        // parent. That is the signature which survives the border being welded to the
        // print beside it.
        val holes = contours.indices
            .filter { hierarchy.get(0, it)[3] >= 0.0 }
            .map { Imgproc.boundingRect(contours[it]) to Imgproc.contourArea(contours[it]) }
            .filter { it.second > frameArea * MIN_CELL_FRACTION }
        if (holes.size < ENOUGH_CELLS) return emptyList()

        val median = holes.map { it.second }.sorted()[holes.size / 2]
        val alike = holes
            .filter { it.second in (median * SMALLEST_ALIKE)..(median * LARGEST_ALIKE) }
            .map { it.first }
        if (alike.size < ENOUGH_CELLS) return emptyList()

        return group(alike, Math.sqrt(median) * SAME_GRID_SPACING)
            .mapNotNull { cells -> latticeFor(cells, scale) }
    }

    /** Cells split into puzzles: cells within [reach] of one another are the same puzzle. */
    private fun group(cells: List<Rect>, reach: Double): List<List<Rect>> {
        val parent = IntArray(cells.size) { it }
        fun root(of: Int): Int {
            var i = of
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        val centres = cells.map { Point(it.x + it.width / 2.0, it.y + it.height / 2.0) }
        for (i in cells.indices) {
            for (j in i + 1 until cells.size) {
                val dx = centres[i].x - centres[j].x
                val dy = centres[i].y - centres[j].y
                if (dx * dx + dy * dy <= reach * reach) parent[root(i)] = root(j)
            }
        }
        return cells.indices.groupBy { root(it) }.values
            .filter { it.size >= ENOUGH_CELLS }
            .map { group -> group.map { cells[it] } }
    }

    /**
     * One puzzle's cells, each with its place in the nine by nine, and the grid they imply.
     *
     * Kept as a thing in its own right so that a measurement can look at the places rather
     * than only at the quad they produce. The alternative - a test that works the places
     * out again for itself - is how the trainer and the reader once came to disagree about
     * every cell in the corpus, and it is not worth repeating for a diagnostic.
     */
    internal class Lattice(
        val centres: List<Point>,
        val places: List<Pair<Int, Int>?>,
        val flat: Mat,
        val pitch: Double,
        private val scale: Double,
    ) {
        /** Half a cell out from the end cells' centres, every way, is the grid's own edge. */
        fun quad(): Quad {
            val edge = MatOfPoint2f()
            Core.perspectiveTransform(
                MatOfPoint2f(Point(-0.5, -0.5), Point(8.5, -0.5), Point(8.5, 8.5), Point(-0.5, 8.5)),
                edge, flat,
            )
            return Quad.ordering(edge.toArray().map { Corner(it.x / scale, it.y / scale) })
        }
    }

    /**
     * How far from a place a cell may sit and still be allowed to claim it, in cells.
     *
     * Half a cell is the point at which a cell is nearer its neighbour's place than its
     * own, so anything beyond that is not a claim on this place but a sign the fit is
     * wrong. A little over half leaves room for a page that bends without letting a stray
     * blob two cells away take a place from the cell that belongs in it.
     */
    private const val CLAIM_WITHIN = 0.7

    /**
     * Each cell given its own place in the nine by nine, nearest claim settled first.
     *
     * A place is a scarce thing and this used to be written as though it were not: every
     * cell rounded its position to the nearest place independently, and when two cells
     * rounded to the same one the second was quietly dropped. Nothing reported it. On the
     * curled newsprint page all eighty-one cells are found and three of them round onto
     * places already taken, so three real cells vanish and three places - its top left
     * corner, which is exactly where the sheet lifts - are left with nothing in them. The
     * surface fitted through the cells then has no measurement over the very corner that
     * needs one.
     *
     * Rounding cannot express the constraint, because the constraint is between cells.
     * Every cell's claim on every place is measured, the closest claim is settled first,
     * and a cell whose place has gone takes the nearest place still free rather than
     * disappearing. Where the fit is good this gives exactly what rounding gave - the
     * nearest place is free and it is taken - and it only differs where rounding was
     * losing cells.
     */
    private fun claimPlaces(atLattice: List<Point>): List<Pair<Int, Int>?> {
        val claims = mutableListOf<Triple<Double, Int, Int>>()
        for (i in atLattice.indices) {
            for (row in 0..8) {
                for (column in 0..8) {
                    val away = Math.hypot(atLattice[i].x - column, atLattice[i].y - row)
                    if (away <= CLAIM_WITHIN) claims += Triple(away, i, row * 9 + column)
                }
            }
        }
        claims.sortBy { it.first }

        val out = arrayOfNulls<Pair<Int, Int>>(atLattice.size)
        val taken = mutableSetOf<Int>()
        val settled = mutableSetOf<Int>()
        for ((_, cell, place) in claims) {
            if (cell in settled || place in taken) continue
            settled += cell
            taken += place
            out[cell] = (place % 9) to (place / 9)
        }
        return out.toList()
    }

    /** The grid these cells belong to, solved as a homography from the nine by nine. */
    private fun latticeFor(cells: List<Rect>, scale: Double): Lattice? {
        val centres = cells.map { Point(it.x + it.width / 2.0, it.y + it.height / 2.0) }

        // The cloud's own two directions, the flatter one taken as the rows.
        val corners = Array(4) { Point() }
            .also { Imgproc.minAreaRect(MatOfPoint2f(*centres.toTypedArray())).points(it) }

        fun unit(a: Point, b: Point): Pair<Double, Double> {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val length = Math.hypot(dx, dy).coerceAtLeast(1e-6)
            return dx / length to dy / length
        }
        val first = unit(corners[0], corners[1])
        val second = unit(corners[1], corners[2])
        val (across, down) =
            if (Math.abs(first.second) <= Math.abs(second.second)) first to second else second to first

        val alongRow = centres.map { it.x * across.first + it.y * across.second }
        val downColumn = centres.map { it.x * down.first + it.y * down.second }
        var places = claimPlaces(
            centres.indices.map { Point(spread(alongRow, it), spread(downColumn, it)) }
        )

        var fitted: Mat? = null

        // Fit, read each cell's place back through the fit, and fit again. The first guess
        // spreads the cells evenly between the two extremes, which a photograph taken at an
        // angle is not; reading the places back off the fit corrects for that. A couple of
        // degrees of lean is the whole difference between the outer rules landing where the
        // scorer looks for them and falling outside its window.
        repeat(REFITS) {
            val lattice = mutableListOf<Point>()
            val onPage = mutableListOf<Point>()
            for (i in centres.indices) {
                val (column, row) = places[i] ?: continue
                lattice += Point(column.toDouble(), row.toDouble())
                onPage += centres[i]
            }
            if (lattice.size < ENOUGH_PLACES) return@repeat

            // Least squares over every cell, and not RANSAC. RANSAC keeps whichever subset
            // agrees most tightly, and on a lattice that is a handful of cells in one
            // corner: measured, it took one of these photographs from 0.48 to 0.00.
            // Dropping the cells that fit worst did the same, for a related reason - the
            // cells that sit furthest off the lattice are where the page bends, which is
            // the part of the shape the corners most need to know about.
            val fit = Calib3d.findHomography(
                MatOfPoint2f(*lattice.toTypedArray()), MatOfPoint2f(*onPage.toTypedArray()),
            )
            if (fit.empty()) return@repeat
            fitted = fit

            val read = MatOfPoint2f()
            Core.perspectiveTransform(MatOfPoint2f(*centres.toTypedArray()), read, fit.inv())
            places = claimPlaces(read.toArray().toList())
        }

        val solved = fitted ?: return null
        val pitch = centres.indices.mapNotNull { i ->
            centres.indices.filter { it != i }
                .minOfOrNull { Math.hypot(centres[i].x - centres[it].x, centres[i].y - centres[it].y) }
        }.sorted().let { it.getOrElse(it.size / 2) { 0.0 } }

        return Lattice(centres, places, solved, pitch, scale)
    }

    /** Where a cell falls along one direction, with the cells spread evenly over the nine. */
    private fun spread(values: List<Double>, of: Int): Double {
        val low = values.min()
        val span = (values.max() - low).coerceAtLeast(1e-6)
        return (values[of] - low) / span * 8
    }
}
