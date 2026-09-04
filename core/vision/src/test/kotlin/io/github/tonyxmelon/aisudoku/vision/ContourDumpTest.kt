package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.test.Test

/**
 * What the detector actually sees on a photograph where it finds no grid.
 *
 * Writes the thresholded image and the contours it yields, because "no grid located" is
 * a conclusion and not a reason. Three newsprint photographs come back with two
 * candidates and a best shape larger than the puzzle, which reads like the grid's border
 * merging with the print beside it - but that is a story until the picture is looked at.
 *
 *   ./gradlew :core:vision:test --tests '*ContourDumpTest*' -Ddump=true -Dscan=<dir> --rerun-tasks
 */
class ContourDumpTest {

    @Test
    fun `write what the detector sees`() {
        if (System.getProperty("dump") != "true") return
        val folder = System.getProperty("scan")?.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        val out = File(System.getProperty("write").orEmpty().ifEmpty { folder.path }).apply { mkdirs() }
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in folder.listFiles { f: File -> f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()) {
            val image = CorpusFixtures.load(file)
            val full = image.toMat()
            val scale = 1000.0 / maxOf(full.width(), full.height())
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

            // Every candidate the detector now offers, with the score it earns.
            for (edge in QuadDetector.workingEdges()) {
                val quads = QuadDetector.detect(image, edge)
                println("   at working edge ${edge.toInt()}: ${quads.size} candidates")
                for (q in quads) {
                    val edges = listOf(q.topEdge, q.rightEdge, q.bottomEdge, q.leftEdge)
                    // The same quad grown about its own centre, because a shape traced
                    // from the cells stops at the inside of the border and the scorer
                    // wants all twenty lines, the two outer ones included.
                    val grown = listOf(1.0, 1.02, 1.04, 1.06, 1.10).map { g ->
                        val cx = q.corners.sumOf { it.x } / 4
                        val cy = q.corners.sumOf { it.y } / 4
                        val c = q.corners.map { Corner(cx + (it.x - cx) * g, cy + (it.y - cy) * g) }
                        g to GridScorer.score(
                            GridLocator.rectifyFor(image, Quad(c[0], c[1], c[2], c[3])).toMat()
                        )
                    }
                    println(
                        "     %.0f x %.0f  squareness %.2f  skew %4.1f  scores %s"
                            .format(edges.max(), edges.min(), edges.min() / edges.max(),
                                    q.maxCornerAngleDeviation,
                                    grown.joinToString(" ") { "%.2f@%.2f".format(it.second, it.first) })
                    )
                }
            }

            // The cells of a grid are holes in it: eighty-one of them, all much the same
            // size, packed together. That signature survives the border being welded to
            // whatever is beside it, which the outer contour does not.
            run {
                val cs = mutableListOf<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(closed, cs, hierarchy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE)
                val fa = small.width().toDouble() * small.height()
                // A hole is a contour whose parent is not -1 in RETR_CCOMP's two levels.
                val holes = cs.indices.filter { hierarchy.get(0, it)[3] >= 0.0 }
                    .map { cs[it] to Imgproc.contourArea(cs[it]) }
                    .filter { it.second > fa / 2000 }
                val med = holes.map { it.second }.sorted().getOrNull(holes.size / 2) ?: 0.0
                val alike = holes.filter { it.second in (med * 0.5)..(med * 2.0) }
                val boxes = alike.map { Imgproc.boundingRect(it.first) }
                if (boxes.size >= 20) {
                    val x0 = boxes.minOf { it.x }; val y0 = boxes.minOf { it.y }
                    val x1 = boxes.maxOf { it.x + it.width }; val y1 = boxes.maxOf { it.y + it.height }
                    println(
                        "   holes: %d, %d alike (median %.4f of frame), together %dx%d at (%d,%d)"
                            .format(holes.size, alike.size, med / fa, x1 - x0, y1 - y0, x0, y0)
                    )
                } else {
                    println("   holes: ${holes.size}, only ${boxes.size} alike")
                }
            }

            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(closed, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val frameArea = small.width().toDouble() * small.height()
            val big = contours.map { it to Imgproc.contourArea(it) }
                .sortedByDescending { it.second }
                .take(6)

            println("\n${file.name}: ${contours.size} contours, frame ${small.width()}x${small.height()}")
            for ((contour, area) in big) {
                val box = Imgproc.boundingRect(contour)
                println(
                    "   area %.3f of frame, box %dx%d at (%d,%d)"
                        .format(area / frameArea, box.width, box.height, box.x, box.y)
                )
            }

            // The thresholded image, with the largest contours drawn over it in mid grey.
            val canvas = Mat()
            Imgproc.cvtColor(closed, canvas, Imgproc.COLOR_GRAY2BGR)
            Imgproc.drawContours(canvas, big.map { it.first }, -1, Scalar(0.0, 0.0, 255.0), 3)
            val bytes = ByteArray(canvas.rows() * canvas.cols() * 3)
            canvas.get(0, 0, bytes)
            val png = java.awt.image.BufferedImage(
                canvas.cols(), canvas.rows(), java.awt.image.BufferedImage.TYPE_3BYTE_BGR,
            )
            png.raster.setDataElements(0, 0, canvas.cols(), canvas.rows(), bytes)
            javax.imageio.ImageIO.write(png, "png", File(out, file.nameWithoutExtension + "-seen.png"))
        }
    }
}
