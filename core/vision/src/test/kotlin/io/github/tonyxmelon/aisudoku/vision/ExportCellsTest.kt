package io.github.tonyxmelon.aisudoku.vision

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Exports every extracted cell as a PNG, so the classifier can be trained and measured
 * against real photographs rather than only against MNIST.
 *
 * Ground truth lives beside the images in `corpus-labels/`, keyed by index, and is read
 * by the Python side rather than parsed here.
 *
 * Disabled by default because it writes files. Run it deliberately:
 *   ./gradlew :core:vision:test --tests '*ExportCellsTest*' -Ddump=true --rerun-tasks
 */
class ExportCellsTest {

    private fun toBufferedImage(image: GrayImage): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        out.raster.setDataElements(0, 0, image.width, image.height, image.pixels)
        return out
    }

    @Test
    fun `export every cell of every corpus photo`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val root = File("build/cell-export").apply { mkdirs() }
        var written = 0

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) {
                println("${file.name}: $verdict")
                continue
            }
            val directory = File(root, file.nameWithoutExtension).apply { mkdirs() }
            verdict.cells.forEachIndexed { index, cell ->
                ImageIO.write(
                    toBufferedImage(cell), "png",
                    File(directory, "cell_%02d.png".format(index)),
                )
                written++
            }
        }
        println("exported $written cells to ${root.absolutePath}")
    }
}
