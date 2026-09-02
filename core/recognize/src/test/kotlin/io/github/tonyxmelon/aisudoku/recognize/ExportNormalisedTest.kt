package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import java.io.DataOutputStream
import java.io.File
import kotlin.test.Test

/**
 * Writes out the 28x28 bitmap this side actually hands the classifier, for every cell.
 *
 * The training script builds the same bitmap in Python from the same exported cell, and
 * the two are supposed to agree. Nothing checked that they did, and they did not: the
 * model is trained on one preprocessing and runs on another, so a cell can be read
 * correctly by the trainer and wrongly by the phone with identical weights.
 *
 * Disabled by default because it writes files. Run it deliberately:
 *   ./gradlew :core:recognize:test --tests '*ExportNormalisedTest*' -Ddump=true --rerun-tasks
 */
class ExportNormalisedTest {

    @Test
    fun `export the bitmap handed to the classifier for every cell`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val root = File("build/normalised").apply { mkdirs() }
        var written = 0

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) continue
            val directory = File(root, file.nameWithoutExtension).apply { mkdirs() }

            CellAnalyzer.inspect(verdict.cells).forEachIndexed { index, ink ->
                if (ink == null) return@forEachIndexed
                DataOutputStream(
                    File(directory, "cell_%02d.f32".format(index)).outputStream().buffered()
                ).use { out ->
                    // Little-endian to match numpy's default on every machine this runs on.
                    for (v in ink.normalised) {
                        out.writeInt(java.lang.Integer.reverseBytes(java.lang.Float.floatToIntBits(v)))
                    }
                }
                written++
            }
        }
        println("exported $written normalised cells to ${root.absolutePath}")
    }
}
