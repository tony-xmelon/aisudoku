package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.GrayImage
import io.github.tonyxmelon.aisudoku.vision.GridLocation
import io.github.tonyxmelon.aisudoku.vision.GridLocator
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.test.Test

/**
 * How small a grid can be before the reader stops reading it.
 *
 * The gate turns away any grid under a fixed number of pixels, and that number was
 * chosen from how many pixels a cell ought to want rather than measured. Photographs
 * have since arrived whose grids fall a few pixels under it and read perfectly once let
 * through, which is a reason to find out where the real edge is.
 *
 * Each labelled photograph is shrunk until its grid is the size in question and put back
 * through the whole pipeline, so what is measured is the reader on a small grid rather
 * than a guess about cells.
 *
 *   ./gradlew :core:recognize:test --tests '*SmallestReadableGridTest*' -Ddump=true --rerun-tasks
 */
class SmallestReadableGridTest {

    private fun scaled(image: GrayImage, factor: Double): GrayImage {
        val source = Mat(image.height, image.width, CvType.CV_8UC1).also { it.put(0, 0, image.pixels) }
        val target = Mat()
        // Area averaging going down: the same filter the cell normaliser settled on, and
        // for the same reason - anything else aliases a thin printed stroke away.
        Imgproc.resize(
            source, target,
            Size(image.width * factor, image.height * factor),
            0.0, 0.0, Imgproc.INTER_AREA,
        )
        val bytes = ByteArray(target.rows() * target.cols())
        target.get(0, 0, bytes)
        return GrayImage(target.cols(), target.rows(), bytes)
    }

    @Test
    fun `report how the reader fares as the grid shrinks`() {
        if (System.getProperty("dump") != "true") return
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val reader = GridReader()
        val sizes = listOf(700, 650, 600, 560, 520, 480, 440, 400, 360)
        println("grid side\tphotos read\tprinted\thandwriting")

        for (side in sizes) {
            var read = 0
            var considered = 0
            var printedRight = 0
            var printedTotal = 0
            var handRight = 0
            var handTotal = 0
            val failed = mutableListOf<String>()

            for (file in CorpusFixtures.photos) {
                val truth = CorpusLabels.forPhoto(file.name) ?: continue
                if (file.name in CorpusLabels.sameSizeHandwriting) continue
                val full = CorpusFixtures.load(file)
                val located = GridLocator.locate(full)
                if (located !is GridLocation.Found) continue
                val quad = located.quad
                val was = minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
                if (was < side) continue
                considered++

                val small = scaled(full, side / was)
                val verdict = StructuralGate.assess(small)
                if (verdict !is GateVerdict.Usable) {
                    failed += "${file.name.take(28)} gate:${verdict.let { (it as GateVerdict.Rejected).reason::class.simpleName }}"
                    continue
                }
                val grid = when (val result = reader.read(verdict.cells)) {
                    is ReadResult.Accepted -> result.grid
                    is ReadResult.NeedsConfirmation -> result.grid
                    is ReadResult.Unreadable -> { failed += "${file.name.take(28)} reader"; continue }
                }
                read++
                for (i in 0 until 81) {
                    when (truth[i].source) {
                        CorpusLabels.Source.GIVEN -> {
                            printedTotal++
                            if (grid[i].digit == truth[i].digit) printedRight++
                        }
                        CorpusLabels.Source.GUESS -> {
                            handTotal++
                            if (grid[i].digit == truth[i].digit) handRight++
                        }
                        CorpusLabels.Source.EMPTY -> Unit
                    }
                }
            }
            println(
                "$side\t$read/$considered\t$printedRight/$printedTotal\t$handRight/$handTotal" +
                    if (failed.isEmpty()) "" else "\t  lost: ${failed.joinToString("; ")}"
            )
        }
    }
}
