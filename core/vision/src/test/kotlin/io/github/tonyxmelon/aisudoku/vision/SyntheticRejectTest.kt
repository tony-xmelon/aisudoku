package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the rejection paths using degraded copies of known-good photographs.
 *
 * These assert the *reason*, not just that something failed. A gate that rejects
 * everything for the wrong reason is useless: the message is what the user acts on.
 */
class SyntheticRejectTest {

    private fun good() = CorpusFixtures.photo("142301")

    private fun setUp() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    @Test
    fun `the undegraded original passes, so any rejection below is caused by the degradation`() {
        setUp()
        assertIs<GateVerdict.Usable>(StructuralGate.assess(good()))
    }

    /**
     * Framing is remarked on, not refused.
     *
     * These used to be rejections and are now complaints carried alongside the reading.
     * Losing the grid is the only thing that stops the app, so what is asserted here is
     * that the complaint is still *made* - a photograph taken from too far away must still
     * say so, because "the grid was small" is the likeliest explanation for a page of
     * wrong digits and the user is owed it.
     */
    @Test
    fun `a photo taken from too far away says so without refusing it`() {
        setUp()
        when (val verdict = StructuralGate.assess(Degrade.shrink(good(), 0.15))) {
            is GateVerdict.Rejected -> assertTrue(
                verdict.reason is RejectionReason.NoGrid,
                "the only refusal left is losing the grid, but got ${verdict.reason}",
            )

            is GateVerdict.Usable -> assertTrue(
                verdict.complaint is RejectionReason.GridTooSmall,
                "a grid this small should be remarked on, but got ${verdict.complaint}",
            )
        }
    }

    @Test
    fun `a grid running out of frame says so without refusing it`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.cropLeft(good(), 0.30))
        val complaint = when (verdict) {
            is GateVerdict.Rejected -> verdict.reason
            is GateVerdict.Usable -> verdict.complaint
        }
        assertTrue(
            complaint is RejectionReason.GridCutOff || complaint is RejectionReason.NoGrid,
            "expected a framing complaint but got $complaint",
        )
    }

    @Test
    fun `a heavily rotated photo says so without refusing it`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.rotate(good(), 35.0))
        val complaint = when (verdict) {
            is GateVerdict.Rejected -> verdict.reason
            is GateVerdict.Usable -> verdict.complaint
        }
        assertTrue(
            complaint is RejectionReason.NotUpright ||
                complaint is RejectionReason.NoGrid ||
                // Turning a photograph inside a fixed canvas takes the corners of the page
                // off the edge of it, so a grid that is found at this angle really has been
                // cut off. That is a better answer than "no grid here" and it only became
                // available once the detector could see a grid this far over.
                complaint is RejectionReason.GridCutOff,
            "expected a complaint about the angle or the edges but got $complaint",
        )
    }

    @Test
    fun `blur is deliberately not a structural rejection, and the grid score rises with it`() {
        // Measured on this photo: blurring at radius 25 raises the grid score from 0.66
        // to 0.93 while sharpness collapses from 56 to 1. Blur removes the digit noise
        // from the line projections, so the grid becomes *easier* to find exactly as the
        // digits become unreadable.
        //
        // Two consequences, both deliberate. The structural gate cannot catch blur and
        // does not try; that is the certainty verdict's job once recognition runs, per
        // spec section 4.2. And the grid score must never be read as a quality signal.
        setUp()
        val original = good()
        val blurred = Degrade.blur(original, 25)

        val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(blurred))
        val originalVerdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(original))
        assertTrue(
            verdict.gridScore > originalVerdict.gridScore,
            "blur was expected to raise the grid score, not lower it",
        )
        assertTrue(
            verdict.quality.sharpness < originalVerdict.quality.sharpness * 0.1,
            "sharpness should have collapsed: ${verdict.quality.sharpness}",
        )
    }

    @Test
    fun `blur severe enough to erase the grid lines is rejected`() {
        // The grid survives to about radius 60 on a 3000x4000 photo and is gone by 90.
        setUp()
        assertIs<GateVerdict.Rejected>(StructuralGate.assess(Degrade.blur(good(), 110)))
    }

    @Test
    fun `degradations that should still be readable are not rejected`() {
        // The gate must not be so eager that it refuses usable photographs. A moderately
        // dark image and a small glare patch both remain readable.
        setUp()
        assertIs<GateVerdict.Usable>(StructuralGate.assess(Degrade.darken(good(), 0.55)))
        assertIs<GateVerdict.Usable>(StructuralGate.assess(Degrade.addGlare(good(), 0.10)))
    }
}
