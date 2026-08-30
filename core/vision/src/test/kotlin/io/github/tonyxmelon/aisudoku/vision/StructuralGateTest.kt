package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StructuralGateTest {

    @Test
    fun `every corpus photo passes`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            assertIs<GateVerdict.Usable>(verdict, "${file.name} was rejected: $verdict")
        }
    }

    @Test
    fun `a photo with no grid is rejected as such`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(900, 900, ByteArray(810_000) { -1 })
        val verdict = assertIs<GateVerdict.Rejected>(StructuralGate.assess(blank))
        assertIs<RejectionReason.NoGrid>(verdict.reason)
    }

    @Test
    fun `every rejection carries a message telling the user what to do`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(900, 900, ByteArray(810_000) { -1 })
        val verdict = assertIs<GateVerdict.Rejected>(StructuralGate.assess(blank))
        assertTrue(verdict.reason.message.isNotBlank())
        assertTrue(verdict.reason.message.first().isUpperCase(), verdict.reason.message)
    }

    @Test
    fun `a usable verdict carries everything the next stage needs`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.photo("142301")))
        assertTrue(verdict.cells.size == 81)
        assertTrue(verdict.gridScore >= GridLocator.MIN_GRID_SCORE)
    }
}
