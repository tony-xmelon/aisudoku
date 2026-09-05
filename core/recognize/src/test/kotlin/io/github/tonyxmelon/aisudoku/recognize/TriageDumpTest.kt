package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import kotlin.test.Test

/**
 * What the triage decided for every cell, beside what the cell really holds.
 *
 * The companion to [BlobStatsDumpTest]: that one prints the measurements a decision was
 * made from, this one prints the decision. Written as a table so that a candidate rule
 * can be tried against the whole corpus outside the build, which is the difference
 * between trying six ideas and trying one.
 *
 *   ./gradlew :core:recognize:test --tests '*TriageDumpTest*' -Ddump=true --rerun-tasks
 */
class TriageDumpTest {

    @Test
    fun `dump the triage decision for every cell`() {
        if (System.getProperty("dump") != "true") return
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val reader = GridReader()
        println(
            "TRIAGE\tphoto\tcell\tdecided\ttruth\tdigit\trelHeight\tvOffset\tink\t" +
                "company\tcontrast\tstroke\toutshone\tread\ttop\tsecond"
        )
        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) continue
            val readings = when (val result = reader.read(verdict.cells)) {
                is ReadResult.Accepted -> result.readings
                is ReadResult.NeedsConfirmation -> result.readings
                is ReadResult.Unreadable -> continue
            }
            // The same measurements the decision was made from, so that a candidate rule
            // can be tried against every cell in the corpus without running the build.
            val inks = CellAnalyzer.inspect(verdict.cells)
            val core = reader.findPrintedCore(inks.mapNotNull { it?.blob }) ?: continue
            for (reading in readings) {
                val ink = inks[reading.index]
                val blob = ink?.blob
                val relative = if (blob == null) 0.0 else blob.heightRatio / core.height
                val carried =
                    if (blob == null || core.contrast <= 0.0 || core.strokeWidth <= 0.0) 0.0
                    else (blob.contrast / core.contrast) * (blob.strokeWidth / core.strokeWidth)
                println(
                    "TRIAGE\t${file.nameWithoutExtension}\t${reading.index}\t${reading.ink}\t" +
                        "${truth[reading.index].source}\t${truth[reading.index].digit ?: 0}\t" +
                        "%.4f\t%.4f\t%.4f\t%d\t%.2f\t%.2f\t%.2f\t%d\t%.3f\t%.3f".format(
                            relative,
                            blob?.verticalOffset ?: 0.0,
                            carried,
                            ink?.company ?: 0,
                            blob?.contrast ?: 0.0,
                            blob?.strokeWidth ?: 0.0,
                            ink?.outshoneBy ?: 0.0,
                            reading.digit ?: 0,
                            reading.probabilities?.max() ?: 0f,
                            reading.probabilities?.sortedDescending()?.getOrNull(1) ?: 0f,
                        )
                )
            }
        }
    }
}
