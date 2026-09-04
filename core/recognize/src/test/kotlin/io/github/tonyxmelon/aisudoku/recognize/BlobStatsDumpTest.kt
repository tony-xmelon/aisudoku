package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import kotlin.test.Test

/**
 * Prints what [CellAnalyzer] measured for every cell, beside what the cell really holds.
 *
 * A measuring instrument, not a test. Run it deliberately when a threshold is in
 * question, and read the numbers rather than guessing at them:
 *   ./gradlew :core:recognize:test --tests '*BlobStatsDumpTest*' -Ddump=true --rerun-tasks
 */
class BlobStatsDumpTest {

    @Test
    fun `dump every blob measurement against the truth`() {
        if (System.getProperty("dump") != "true") return
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val reader = GridReader()
        println("photo\tcell\ttruth\trelHeight\tvOffset\tdarkRatio\tstrokeRatio\tdarkness")
        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) continue
            val inks = CellAnalyzer.inspect(verdict.cells)
            val core = reader.findPrintedCore(inks.mapNotNull { it?.blob }) ?: continue

            inks.forEachIndexed { index, ink ->
                val blob = ink?.blob ?: return@forEachIndexed
                // The paper of this very cell: the median is paper because ink is the
                // minority of any square. Contrast against it is what erasing destroys,
                // and it does not care how the photograph was exposed.
                val cell = verdict.cells[index]
                val paper = cell.pixels.map { it.toInt() and 0xFF }.sorted()[cell.pixels.size / 2]

                // Every other blob in the same square. If something smaller is markedly
                // darker than the biggest thing here, the biggest thing is not the ink
                // that was written last.
                val mat = org.opencv.core.Mat(cell.height, cell.width, org.opencv.core.CvType.CV_8UC1)
                    .also { it.put(0, 0, cell.pixels) }
                val others = CellAnalyzer.findBlobs(mat, cell).filter { it.area >= 12 }
                val darkest = others.minOfOrNull { it.darkness } ?: blob.darkness
                println(
                    "%s\tr%dc%d\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%.1f\t%d\t%.1f\t%.1f\t%.3f\t%.3f\t%.3f".format(
                        file.name.removeSuffix(".jpg"),
                        index / 9 + 1, index % 9 + 1,
                        truth[index].source,
                        blob.heightRatio / core.height,
                        blob.verticalOffset,
                        blob.darkness / core.darkness,
                        blob.strokeWidth / core.strokeWidth,
                        blob.darkness,
                        paper,
                        paper - blob.darkness,
                        blob.darkness - darkest,
                        blob.aspect,
                        blob.heightRatio,
                        // the ink score the reader itself would compute, against the
                        // core it itself selected
                        (blob.contrast / core.contrast) * (blob.strokeWidth / core.strokeWidth),
                    )
                )
            }
        }
    }
}
