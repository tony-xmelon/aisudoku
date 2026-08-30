package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the whole pipeline over the corpus and reports one line per photograph.
 *
 * This is the regression signal for the vision stage: any parameter change must be run
 * against it, and the printed numbers are how a change is judged better or worse.
 */
class CorpusHarnessTest {

    @Test
    fun `the whole corpus passes the pipeline`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        var usable = 0
        val report = StringBuilder("\n=== vision corpus harness ===\n")

        for (file in CorpusFixtures.photos) {
            when (val verdict = StructuralGate.assess(CorpusFixtures.load(file))) {
                is GateVerdict.Usable -> {
                    usable++
                    val cell = verdict.cells[0]
                    report.append(
                        "%-26s OK  score=%.2f  cell=%dx%d  sharp=%.0f  luma=%.0f  glare=%.3f\n".format(
                            file.name, verdict.gridScore, cell.width, cell.height,
                            verdict.quality.sharpness, verdict.quality.meanLuma,
                            verdict.quality.clippedWhiteFraction,
                        )
                    )
                }

                is GateVerdict.Rejected ->
                    report.append("%-26s REJECTED  %s\n".format(file.name, verdict.reason))
            }
        }
        report.append("$usable/${CorpusFixtures.photos.size} usable\n")
        println(report)

        assertTrue(
            usable == CorpusFixtures.photos.size,
            "every corpus photograph is known good and must pass:\n$report",
        )
    }
}
