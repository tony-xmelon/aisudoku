package io.github.tonyxmelon.aisudoku.recognize

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Telling a rubbed-out digit from an answer, on numbers rather than on photographs.
 *
 * The corpus measures this properly, but the corpus photographs are not committed, so on
 * CI those tests skip and this rule would travel unguarded. Every number below was
 * measured off a real cell, named in each case, so a change that breaks the rule fails
 * here rather than three weeks later on someone's phone.
 */
class ErasedDigitTest {

    // The triage runs before any digit is read, so the classifier is never consulted
    // here; the real one is used simply because it is the only one there is.
    private val reader = GridReader()

    /** The printed digits of a photograph: the yardstick everything is measured against. */
    private val core =
        GridReader.PrintedCore(height = 0.50, darkness = 53.0, strokeWidth = 4.0, contrast = 140.0)

    /** Digit-sized and sitting in the middle of the cell, as an answer does. */
    private fun answerShaped(contrast: Double, outshoneBy: Double) = CellInk(
        blob = Blob(
            left = 4, top = 4, width = 20, height = 30, area = 260,
            aspect = 0.66,
            heightRatio = 0.60,          // 1.20 of the printed height
            verticalOffset = 0.05,       // just below centre
            darkness = 120.0,
            contrast = contrast,
            strokeWidth = 3.0,
            maskLabel = 1,
        ),
        normalised = FloatArray(28 * 28),
        outshoneBy = outshoneBy,
    )

    @Test
    fun `an answer written over nothing is an answer`() {
        // Typical of the corpus: strong ink, and the only ink in its square.
        assertEquals(Ink.ANSWER, reader.classify(answerShaped(contrast = 60.0, outshoneBy = 0.0), core))
    }

    @Test
    fun `the erased digit under two candidate marks is not an answer`() {
        // r9c7 of the two-line-candidates page: an erased digit still faintly visible,
        // with the marks written over it a good deal darker than what is left of it.
        assertEquals(Ink.MARK, reader.classify(answerShaped(contrast = 18.5, outshoneBy = 40.9), core))
    }

    @Test
    fun `the faintest real answer in the corpus survives`() {
        // r8c2 of the completed grid: fainter than that erasure in absolute terms, and
        // still an answer, because nothing darker is written beside it. This is why
        // faintness alone cannot be the test.
        assertEquals(Ink.ANSWER, reader.classify(answerShaped(contrast = 21.3, outshoneBy = 0.0), core))
    }

    @Test
    fun `an answer with darker ink beside it survives`() {
        // r2c9 of the viber photograph: something darker shares its square, but the
        // answer itself is firmly written. This is why being outshone alone cannot be
        // the test either.
        assertEquals(Ink.ANSWER, reader.classify(answerShaped(contrast = 49.1, outshoneBy = 79.5), core))
    }
}
