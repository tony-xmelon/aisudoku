package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * How long it takes to read one photograph, which decides where that work may run.
 *
 * The app does all of this on the main thread when the shutter is pressed. That is only
 * survivable if it is fast, so the cost is measured rather than assumed - and a desktop
 * figure is a floor, since a phone is several times slower again.
 */
class ReadingBudgetTest {

    @Test
    fun `report what reading a photograph costs`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        println("=== cost of reading one photograph (desktop JVM) ===")
        for (file in CorpusFixtures.photos) {
            val image = CorpusFixtures.load(file)
            var verdict: GateVerdict? = null
            val gate = measureTime { verdict = StructuralGate.assess(image) }
            val usable = verdict as? GateVerdict.Usable ?: continue
            val read = measureTime { GridReader().read(usable.cells) }
            println(
                "  %-40s locate %5d ms   read %5d ms   total %5d ms".format(
                    file.name, gate.inWholeMilliseconds, read.inWholeMilliseconds,
                    (gate + read).inWholeMilliseconds,
                )
            )
        }
    }
}
