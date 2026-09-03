package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test

/**
 * Measures how much framing error the gate actually tolerates, on real photographs.
 *
 * The thresholds are all written down, but what a threshold costs the user is a different
 * question: how close to the edge of the frame, or how far from square, a perfectly
 * readable page may be before the app refuses it. This walks a good photograph away from
 * ideal in one direction at a time and reports where it stops being accepted.
 *
 * A measuring instrument, not a test:
 *   ./gradlew :core:vision:test --tests '*FramingToleranceDumpTest*' -Ddump=true --rerun-tasks
 */
class FramingToleranceDumpTest {

    private fun verdict(image: GrayImage): String = when (val v = StructuralGate.assess(image)) {
        is GateVerdict.Usable -> "USABLE"
        is GateVerdict.Rejected -> v.reason::class.simpleName ?: "?"
    }

    private fun toMat(image: GrayImage): Mat =
        Mat(image.height, image.width, CvType.CV_8UC1).also { it.put(0, 0, image.pixels) }

    private fun toGray(mat: Mat): GrayImage {
        val bytes = ByteArray(mat.rows() * mat.cols())
        mat.get(0, 0, bytes)
        return GrayImage(mat.cols(), mat.rows(), bytes)
    }

    @Test
    fun `how tightly may the grid be framed`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos.take(4)) {
            val image = CorpusFixtures.load(file)
            val located = GridLocator.locate(image)
            if (located !is GridLocation.Found) {
                println("${file.name}: no grid to start from"); continue
            }
            val quad = located.quad
            val minX = quad.corners.minOf { it.x }
            val maxX = quad.corners.maxOf { it.x }
            val minY = quad.corners.minOf { it.y }
            val maxY = quad.corners.maxOf { it.y }
            val side = max(maxX - minX, maxY - minY)

            val mat = toMat(image)
            println("\n${file.name}  grid side ${side.toInt()}px in ${image.width}x${image.height}")
            for (marginFraction in listOf(0.25, 0.15, 0.10, 0.05, 0.02, 0.01, 0.005, 0.0)) {
                val pad = side * marginFraction
                val x0 = max(0.0, minX - pad).toInt()
                val y0 = max(0.0, minY - pad).toInt()
                val x1 = min(image.width.toDouble(), maxX + pad).toInt()
                val y1 = min(image.height.toDouble(), maxY + pad).toInt()
                val cropped = Mat(mat, Rect(x0, y0, x1 - x0, y1 - y0))
                println(
                    "  margin %5.1f%% of the grid (%4dpx): %s"
                        .format(marginFraction * 100, pad.toInt(), verdict(toGray(cropped)))
                )
            }
        }
    }

    @Test
    fun `how far from upright may the page be`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos.take(3)) {
            val image = CorpusFixtures.load(file)
            // Pad first, so that turning the page cannot push it out of frame. Without
            // this the grid is clipped by the rotation itself and the result says
            // nothing about how much tilt the reader tolerates.
            val padded = Mat()
            val border = max(image.width, image.height) / 2
            Core.copyMakeBorder(
                toMat(image), padded, border, border, border, border, Core.BORDER_REPLICATE,
            )
            println("\n${file.name}")
            for (degrees in listOf(0.0, 5.0, 8.0, 10.0, 12.0, 14.0, 15.0, 17.0, 20.0)) {
                val centre = Point(padded.cols() / 2.0, padded.rows() / 2.0)
                val rotation = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
                val turned = Mat()
                Imgproc.warpAffine(
                    padded, turned, rotation,
                    Size(padded.cols().toDouble(), padded.rows().toDouble()),
                    Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar(0.0),
                )
                println("  tilted %4.1f degrees: %s".format(degrees, verdict(toGray(turned))))
            }
        }
    }
}
