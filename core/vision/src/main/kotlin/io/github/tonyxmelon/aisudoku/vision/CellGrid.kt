package io.github.tonyxmelon.aisudoku.vision

import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
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
 * One photograph needs more than a plane, because its page is not flat: no homography fits
 * its own cells, leaving them 13 pixels out on average and 48 at worst on a cell 72 pixels
 * across. That the page bends, rather than a few cells being measured badly, is not an
 * assumption - the map of how far each cell falls from the flat grid is an unbroken slope
 * from one corner of the grid to the other, and curvature is smooth where a broken contour
 * is a spike. So that page is straightened by a surface through its cells instead. See
 * [Lattice.flatten].
 *
 * Both halves were needed and neither was enough alone. The surface on its own reached
 * 0.46 against the 0.35 needed and still could not be read, because three of that page's
 * cells were being dropped by the place assignment and the corner they belonged to - the
 * corner where the sheet lifts - had nothing in it for the surface to follow. With the
 * cells kept, the same surface scores 0.73 and the page reads.
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
            .map { contours[it] to Imgproc.contourArea(contours[it]) }
            .filter { it.second > frameArea * MIN_CELL_FRACTION }
        if (holes.size < ENOUGH_CELLS) return emptyList()

        val median = holes.map { it.second }.sorted()[holes.size / 2]
        val alike = holes
            .filter { it.second in (median * SMALLEST_ALIKE)..(median * LARGEST_ALIKE) }
            .map { Cell(it.first) }
        if (alike.size < ENOUGH_CELLS) return emptyList()

        return group(alike, Math.sqrt(median) * SAME_GRID_SPACING)
            .mapNotNull { cells -> latticeFor(cells, scale) }
    }

    /**
     * One cell of a grid: where its middle is, and where its four corners are.
     *
     * The corners come from the smallest rotated rectangle round the hole rather than from
     * its upright bounding box, because an upright box round a cell on a tilted page is
     * bigger than the cell and biased outwards by the tilt.
     */
    private class Cell(contour: MatOfPoint) {
        val centre: Point
        /** Its own size, used to work out how much of the pitch the rules take up. */
        val span: Double
        private val points: Array<Point>

        init {
            // The middle is the middle of the upright bounding box, and the corners come
            // from the smallest rotated rectangle. Two different measurements on purpose:
            // an upright box round a cell on a tilted page is bigger than the cell and
            // biased outwards, which matters for a corner and cancels for a middle - and
            // the middle is what the flat grid is fitted to, so leaving it alone keeps
            // every photograph that reads today on exactly the mapping it reads with.
            val upright = Imgproc.boundingRect(contour)
            centre = Point(upright.x + upright.width / 2.0, upright.y + upright.height / 2.0)
            val box = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
            span = (box.size.width + box.size.height) / 2
            points = Array(4) { Point() }.also { box.points(it) }
        }

        /**
         * The four corners, each with where it sits from the cell's middle, in cells.
         *
         * Not a flat half a cell, and this is the whole of why the border used to fall off
         * the edge of the straightened picture. A cell is the *hole* in the grid, so its
         * corner is the inside of the rule and not the rule itself; labelling it half a
         * cell out puts the middle of the outer rule just beyond the picture, and the two
         * outer rules are two of the twenty lines the score is the weakest of. The inside
         * of the rule is half a cell less half a rule, and [inset] is that half rule.
         */
        fun corners(inset: Double): List<Pair<Pair<Double, Double>, Point>> {
            val middleX = points.sumOf { it.x } / 4
            val middleY = points.sumOf { it.y } / 4
            return points.map { corner ->
                val alongX = if (corner.x < middleX) inset - 0.5 else 0.5 - inset
                val alongY = if (corner.y < middleY) inset - 0.5 else 0.5 - inset
                (alongX to alongY) to corner
            }
        }
    }

    /**
     * Half the width of a grid rule, as a fraction of a cell.
     *
     * Measured off the picture rather than assumed: the cells sit a pitch apart and are
     * narrower than the pitch by exactly the rule between them.
     */
    private fun ruleInset(cells: List<Cell>): Double {
        val centres = cells.map { it.centre }
        val gaps = centres.indices.mapNotNull { i ->
            centres.indices.filter { it != i }
                .minOfOrNull { Math.hypot(centres[i].x - centres[it].x, centres[i].y - centres[it].y) }
        }
        if (gaps.isEmpty()) return 0.0
        val pitch = gaps.sorted()[gaps.size / 2]
        val span = cells.map { it.span }.sorted()[cells.size / 2]
        if (pitch <= 0.0) return 0.0
        return ((pitch - span) / (2 * pitch)).coerceIn(0.0, 0.2)
    }

    /** Cells split into puzzles: cells within [reach] of one another are the same puzzle. */
    private fun group(cells: List<Cell>, reach: Double): List<List<Cell>> {
        val parent = IntArray(cells.size) { it }
        fun root(of: Int): Int {
            var i = of
            while (parent[i] != i) {
                parent[i] = parent[parent[i]]
                i = parent[i]
            }
            return i
        }
        val centres = cells.map { it.centre }
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
        private val curve: Mat?,
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

        /**
         * The grid straightened onto a square, taking the bend of the page out with it.
         *
         * A homography can only map a plane to a plane, and a newspaper held in one hand
         * is not a plane. On the one corpus photograph where this matters the cells sit up
         * to 44 pixels off the best flat grid, on a cell 72 pixels across - and the map of
         * how far each cell is off is a smooth slope from one corner to the other, not a
         * scatter, which is what says it is the page bending rather than a few cells
         * measured badly. A surface through the cells follows that slope.
         *
         * Fitting the surface to the flat grid's error instead, and adding the two, was
         * tried and measures identically on all three photographs - the least squares fit
         * absorbs the difference - while costing a projective transform of every output
         * pixel. So the surface carries the whole mapping.
         *
         * Falls back to the flat mapping entirely when the surface could not be fitted, so
         * this is never worse than the quad.
         */
        fun flatten(full: Mat, side: Double): Mat? {
            val coefficients = curve ?: return null
            val size = side.toInt()
            val xs = FloatArray(size * size)
            val ys = FloatArray(size * size)
            // Pulled out of the Mat once: reading a coefficient back through OpenCV inside
            // a loop over a million output pixels is most of the cost of this warp.
            val toX = DoubleArray(TERMS) { coefficients.get(it, 0)[0] }
            val toY = DoubleArray(TERMS) { coefficients.get(it, 1)[0] }
            val terms = DoubleArray(TERMS)
            val slopeX = DoubleArray(TERMS)
            val slopeY = DoubleArray(TERMS)
            for (row in 0 until size) {
                val y = row.toDouble() / side * 9.0 - 0.5
                val insideY = y.coerceIn(-0.5, 8.5)
                for (column in 0 until size) {
                    val x = column.toDouble() / side * 9.0 - 0.5
                    val insideX = x.coerceIn(-0.5, 8.5)

                    // The surface is fitted over exactly this range - cell corners run
                    // from -0.5 to 8.5 - so nothing here is normally extrapolated. The
                    // clamp is a guard for rounding at the very last pixel, and beyond it
                    // the surface is continued by its slope rather than by its curve,
                    // because a cubic let loose outside its own data bows away fast.
                    fillTerms(insideX, insideY, terms)
                    var atX = 0.0
                    var atY = 0.0
                    for (t in 0 until TERMS) {
                        atX += terms[t] * toX[t]
                        atY += terms[t] * toY[t]
                    }
                    if (x != insideX || y != insideY) {
                        fillSlopes(insideX, insideY, slopeX, slopeY)
                        val overX = x - insideX
                        val overY = y - insideY
                        for (t in 0 until TERMS) {
                            atX += (overX * slopeX[t] + overY * slopeY[t]) * toX[t]
                            atY += (overX * slopeX[t] + overY * slopeY[t]) * toY[t]
                        }
                    }
                    xs[row * size + column] = atX.toFloat()
                    ys[row * size + column] = atY.toFloat()
                }
            }
            val mapX = Mat(size, size, org.opencv.core.CvType.CV_32F).also { it.put(0, 0, xs) }
            val mapY = Mat(size, size, org.opencv.core.CvType.CV_32F).also { it.put(0, 0, ys) }
            val out = Mat()
            Imgproc.remap(full, out, mapX, mapY, Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
            return out
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
    private fun latticeFor(cells: List<Cell>, scale: Double): Lattice? {
        val centres = cells.map { it.centre }

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

        return Lattice(centres, places, solved, pitch, scale, curveThrough(cells, places, scale))
    }

    /** Where a cell falls along one direction, with the cells spread evenly over the nine. */
    private fun spread(values: List<Double>, of: Int): Double {
        val low = values.min()
        val span = (values.max() - low).coerceAtLeast(1e-6)
        return (values[of] - low) / span * 8
    }
    /**
     * Order of the surface fitted through the cells, and the powers that make it up.
     *
     * Every combination of x and y whose powers together come to no more than [ORDER], so
     * the count is fixed by the order alone. Each cell contributes five places - its four
     * corners and its middle - so a full grid gives 405 measurements to fit these to.
     *
     * Four because it was measured, on the one photograph that needs a surface at all. A
     * cubic cannot follow that page's worst corner and gets it to 0.38; a quartic gets it
     * to 0.46; a quintic falls back to 0.38 again, which is what a surface with more
     * freedom than evidence does. Three cells there are never found - the top left, which
     * is exactly where the page lifts - so the fit has nothing to hold it down over that
     * corner, and how much rope to give it is the whole of this choice.
     */
    private const val ORDER = 4

    private val POWERS: List<Pair<Int, Int>> =
        (0..ORDER).flatMap { total -> (0..total).map { it to total - it } }

    private val TERMS = POWERS.size

    private fun fillTerms(x: Double, y: Double, into: DoubleArray) {
        for (t in POWERS.indices) {
            val (i, j) = POWERS[t]
            into[t] = power(x, i) * power(y, j)
        }
    }

    /** The same terms differentiated, for continuing the surface straight past its edge. */
    private fun fillSlopes(x: Double, y: Double, alongX: DoubleArray, alongY: DoubleArray) {
        for (t in POWERS.indices) {
            val (i, j) = POWERS[t]
            alongX[t] = if (i == 0) 0.0 else i * power(x, i - 1) * power(y, j)
            alongY[t] = if (j == 0) 0.0 else j * power(x, i) * power(y, j - 1)
        }
    }

    private fun power(of: Double, to: Int): Double {
        var out = 1.0
        repeat(to) { out *= of }
        return out
    }

    /**
     * A cubic surface from lattice place to the page, fitted to every cell.
     *
     * The homography above is the best flat grid, and where the page is flat that is the
     * whole answer - the surface then simply reproduces it. Where the page is curled the
     * homography cannot follow, and this can: it is fitted to the same cells with no
     * planarity assumed, so the bend goes into the coefficients instead of into the error.
     *
     * Cubic rather than anything higher because the output runs half a cell past the end
     * cells at every edge, and the corners are read off that extrapolation; a surface
     * flexible enough to thread every cell exactly is also flexible enough to swing wildly
     * just outside them.
     */
    private fun curveThrough(
        cells: List<Cell>,
        places: List<Pair<Int, Int>?>,
        scale: Double,
    ): Mat? {
        val inset = ruleInset(cells)
        val rows = mutableListOf<DoubleArray>()
        val targets = mutableListOf<Point>()
        val taken = mutableSetOf<Int>()
        for (i in cells.indices) {
            val (column, row) = places[i] ?: continue
            if (!taken.add(row * 9 + column)) continue
            // Each cell's own four corners, not just its middle. A cell corner is a place
            // where two grid rules cross, and the corners of the end cells are the outer
            // rules themselves - so the surface is fitted right out to the border instead
            // of being extended half a cell past the last thing it was fitted to. That
            // guess is what left the border bowing away from its own rule while the eight
            // inner lines came out straight, and the border is two of the twenty lines the
            // score is the weakest of.
            for ((corner, at) in cells[i].corners(inset) + (0.0 to 0.0 to cells[i].centre)) {
                val terms = DoubleArray(TERMS)
                val x = column + corner.first
                val y = row + corner.second
                fillTerms(x, y, terms)
                rows += terms
                targets += at
            }
        }
        // A surface fitted to barely more cells than it has coefficients is fitting the
        // noise. Half the grid is the least worth trusting it on, and never fewer cells
        // than there are coefficients to find.
        if (taken.size < maxOf(40, TERMS)) return null

        val a = Mat(rows.size, TERMS, org.opencv.core.CvType.CV_64F)
        val b = Mat(rows.size, 2, org.opencv.core.CvType.CV_64F)
        for (i in rows.indices) {
            a.put(i, 0, *rows[i])
            b.put(i, 0, targets[i].x / scale, targets[i].y / scale)
        }
        val coefficients = Mat()
        val solvable = Core.solve(a, b, coefficients, Core.DECOMP_QR or Core.DECOMP_NORMAL)
        return if (solvable) coefficients else null
    }

    /** Which of the nine a cell falls in, along one direction. */
    private fun place(values: List<Double>, of: Int): Int {
        val low = values.min()
        val span = (values.max() - low).coerceAtLeast(1e-6)
        return Math.round((values[of] - low) / span * 8).toInt()
    }
}
