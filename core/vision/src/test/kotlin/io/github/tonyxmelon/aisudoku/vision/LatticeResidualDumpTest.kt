package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Core
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
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
 * The map also shows, as dots, the lattice places no cell reached - a different fault
 * again, and the one that turned out to matter first. It is read off [CellGrid] itself
 * rather than worked out again here, because a diagnostic that reimplements the thing it
 * is diagnosing agrees with it right up until the day it matters.
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
            val image = CorpusFixtures.load(file)
            val lattices = CellGrid.lattices(image, 1600.0)
            if (lattices.isEmpty()) println("  no cell cluster large enough to fit")
            for (lattice in lattices.sortedByDescending { it.centres.size }) report(lattice)
        }
    }

    private fun report(lattice: Lattice) {
        val map = Array(9) { Array(9) { "  ." } }
        val errors = mutableListOf<Double>()
        val where = MatOfPoint2f()

        for (i in lattice.centres.indices) {
            val (column, row) = lattice.places[i] ?: continue
            Core.perspectiveTransform(
                MatOfPoint2f(Point(column.toDouble(), row.toDouble())), where, lattice.flat,
            )
            val at = where.toArray()[0]
            val off = Math.hypot(at.x - lattice.centres[i].x, at.y - lattice.centres[i].y)
            errors += off
            map[row][column] = "%3.0f".format(off)
        }
        if (errors.isEmpty()) return println("  no cell was given a place")

        println(
            "  cell pitch %.0f px, %d cells found, %d given a place, %d without one"
                .format(lattice.pitch, lattice.centres.size, errors.size,
                        lattice.centres.size - errors.size)
        )
        println(
            "  residual median %.1f mean %.1f max %.1f px"
                .format(errors.sorted()[errors.size / 2], errors.average(), errors.max())
        )
        for (row in map) println("    " + row.joinToString(" "))
    }
}

private typealias Lattice = CellGrid.Lattice
