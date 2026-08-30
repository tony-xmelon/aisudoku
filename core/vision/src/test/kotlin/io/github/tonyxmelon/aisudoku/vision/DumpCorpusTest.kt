package io.github.tonyxmelon.aisudoku.vision

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Writes the rectified grid and a contact sheet of the 81 cells for each corpus photo,
 * so a human can see what the pipeline actually produced.
 *
 * Disabled by default because it writes files. Run it deliberately:
 *   ./gradlew :core:vision:test --tests '*DumpCorpusTest*' -Ddump=true --rerun-tasks
 */
class DumpCorpusTest {

    private fun toBufferedImage(image: GrayImage): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        out.raster.setDataElements(0, 0, image.width, image.height, image.pixels)
        return out
    }

    @Test
    fun `dump rectified grids and cell contact sheets`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val out = File("build/corpus-dump").apply { mkdirs() }

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) {
                println("${file.name}: $verdict")
                continue
            }
            val stem = file.nameWithoutExtension
            ImageIO.write(toBufferedImage(verdict.rectified), "png", File(out, "${stem}_grid.png"))

            // 9x9 contact sheet of the extracted cells, each scaled into a 64px box.
            val tile = 64
            val sheet = BufferedImage(tile * 9, tile * 9, BufferedImage.TYPE_BYTE_GRAY)
            val graphics = sheet.createGraphics()
            verdict.cells.forEachIndexed { index, cell ->
                graphics.drawImage(
                    toBufferedImage(cell),
                    (index % 9) * tile, (index / 9) * tile, tile, tile, null,
                )
            }
            graphics.dispose()
            ImageIO.write(sheet, "png", File(out, "${stem}_cells.png"))
        }
        println("dumped to ${out.absolutePath}")
    }
}
