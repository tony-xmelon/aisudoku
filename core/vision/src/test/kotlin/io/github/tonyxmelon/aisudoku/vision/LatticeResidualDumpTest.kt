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
import java.io.File
import kotlin.test.Test

/**
 * How far each cell of a photograph sits from the flat grid that best explains all of them.
 *
 * "No single plane fits this page" is a conclusion drawn from one number - the mean
 * residual of the homography - and that number cannot tell a curled page from a few cells
 * whose contours broke. The two want completely different fixes, and the map tells them
 * apart where the average cannot: curvature is smooth, so the error grows across the grid
 * and reads as a slope, while a broken contour is a spike sitting among neighbours that
 * are fine.
 *
 * Measured on the newsprint set, one page runs 2 pixels to 44 in a clean gradient and the
 * others sit at 0 to 5 with three isolated spikes. That is what says the first is the sheet
 * of paper bending and the others are two or three cells measured badly.
 *
 * The map also shows, as dots, the lattice places no cell was assigned to - which is a
 * different fault again, and on the curled page the revealing one: all eighty-one cells are
 * found there, but three land on places already taken and leave its top left corner empty.
 *
 *   ./gradlew :core:vision:test --tests '*LatticeResidualDumpTest*' -Ddump=true -Dscan=<dir>
 */
class LatticeResidualDumpTest {

    @Test
    fun `map each cell's distance from the best flat grid`() {
        if (System.getProperty("dump") != "true") return
        val folder = System.getProperty("scan")?.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in folder.listFiles { f: File -> f.extension.lowercase() in setOf("jpg", "png") }
            ?.sortedBy { it.name }.orEmpty()) {
            println("\n${file.name}")
            report(CorpusFixtures.load(file), 1600.0)
        }
    }

    private fun report(image: GrayImage, workingEdge: Double) {
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

        val holes = contours.indices
            .filter { hierarchy.get(0, it)[3] >= 0.0 }
            .map { Imgproc.boundingRect(contours[it]) to Imgproc.contourArea(contours[it]) }
            .filter { it.second > frameArea / 2000 }
        if (holes.size < 30) return println("  only ${holes.size} holes")
        val median = holes.map { it.second }.sorted()[holes.size / 2]
        val alike = holes.filter { it.second in (median * 0.5)..(median * 2.0) }

        // The largest group, which is the puzzle when the page carries more than one.
        val cells = biggestGroup(alike.map { it.first }, Math.sqrt(median) * 1.6) ?: return
        val centres = cells.map { Point(it.x + it.width / 2.0, it.y + it.height / 2.0) }

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
        fun place(values: List<Double>, of: Int): Int {
            val low = values.min()
            val span = (values.max() - low).coerceAtLeast(1e-6)
            return Math.round((values[of] - low) / span * 8).toInt()
        }
        var places = centres.indices.map { place(alongRow, it) to place(downColumn, it) }

        var fitted: Mat? = null
        repeat(3) {
            val lattice = mutableListOf<Point>()
            val onPage = mutableListOf<Point>()
            val taken = mutableSetOf<Int>()
            for (i in centres.indices) {
                val (column, row) = places[i]
                if (column !in 0..8 || row !in 0..8 || !taken.add(row * 9 + column)) continue
                lattice += Point(column.toDouble(), row.toDouble())
                onPage += centres[i]
            }
            if (lattice.size < 20) return@repeat
            val fit = Calib3d.findHomography(
                MatOfPoint2f(*lattice.toTypedArray()), MatOfPoint2f(*onPage.toTypedArray()),
            )
            if (fit.empty()) return@repeat
            fitted = fit
            val read = MatOfPoint2f()
            Core.perspectiveTransform(MatOfPoint2f(*centres.toTypedArray()), read, fit.inv())
            places = read.toArray().map { Math.round(it.x).toInt() to Math.round(it.y).toInt() }
        }
        val solved = fitted ?: return println("  no fit")

        val map = Array(9) { Array(9) { "  ." } }
        val errors = mutableListOf<Double>()
        val placed = MatOfPoint2f()
        val taken = mutableSetOf<Int>()
        var collided = 0
        for (i in centres.indices) {
            val (column, row) = places[i]
            if (column !in 0..8 || row !in 0..8) continue
            if (!taken.add(row * 9 + column)) { collided++; continue }
            Core.perspectiveTransform(
                MatOfPoint2f(Point(column.toDouble(), row.toDouble())), placed, solved,
            )
            val at = placed.toArray()[0]
            val off = Math.hypot(at.x - centres[i].x, at.y - centres[i].y)
            errors += off
            map[row][column] = "%3.0f".format(off)
        }
        println(
            "  cell pitch %.0f px, %d cells found, %d placed, %d landed on a taken place"
                .format(Math.sqrt(median), cells.size, errors.size, collided)
        )
        println(
            "  residual median %.1f mean %.1f max %.1f px"
                .format(errors.sorted()[errors.size / 2], errors.average(), errors.max())
        )
        for (row in map) println("    " + row.joinToString(" "))
    }

    private fun biggestGroup(cells: List<Rect>, reach: Double): List<Rect>? {
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
        return cells.indices.groupBy { root(it) }.values.maxByOrNull { it.size }?.map { cells[it] }
    }
}
