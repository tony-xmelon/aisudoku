package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.recognize.CellReading
import io.github.tonyxmelon.aisudoku.recognize.Ink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the app tells the user about a square it read.
 *
 * The point of showing the classifier's own numbers is that a confident misread and a
 * coin flip want different responses - one is worth a photograph to the developer, the
 * other is just a hard cell to correct. That distinction is only useful if the numbers
 * shown are the ones the classifier actually produced.
 */
class CellReportTest {

    private fun reading(ink: Ink, vararg probabilities: Float) =
        CellReading(
            index = 0,
            ink = ink,
            probabilities = if (probabilities.isEmpty()) null else probabilities,
            heightRatio = 0.5,
            darkness = 80.0,
        )

    @Test
    fun `the digit reported is the most likely one`() {
        val p = FloatArray(9).also { it[4] = 0.7f; it[2] = 0.2f }
        val report = CellReport.of(reading(Ink.PRINTED, *p))

        assertEquals(5, report.digit, "index 4 is the digit 5")
        assertEquals(0.7f, report.confidence)
        assertEquals(3, report.runnerUp, "index 2 is the digit 3")
        assertEquals(0.2f, report.runnerUpConfidence)
    }

    @Test
    fun `a cell with no ink reports no digit and claims no doubt`() {
        val report = CellReport.of(reading(Ink.NONE))
        assertNull(report.digit)
        assertNull(report.runnerUp)
        assertTrue(report.describe().contains("empty"))
    }

    @Test
    fun `pencil marks are named as marks rather than as a digit`() {
        val report = CellReport.of(reading(Ink.MARK))
        assertTrue(report.describe().contains("candidate marks"))
    }

    @Test
    fun `print and handwriting are described differently`() {
        val p = FloatArray(9).also { it[0] = 0.9f; it[1] = 0.05f }
        assertTrue(CellReport.of(reading(Ink.PRINTED, *p)).describe().contains("printed"))
        assertTrue(CellReport.of(reading(Ink.ANSWER, *p)).describe().contains("handwritten"))
    }

    @Test
    fun `a second guess is only offered when there was a real contest`() {
        val contested = FloatArray(9).also { it[0] = 0.55f; it[1] = 0.40f }
        assertTrue(CellReport.of(reading(Ink.ANSWER, *contested)).secondGuess()!!.contains("2"))

        // Below two percent there was no contest, and naming a runner-up would invent one.
        val certain = FloatArray(9).also { it[0] = 0.995f; it[1] = 0.004f }
        assertNull(CellReport.of(reading(Ink.ANSWER, *certain)).secondGuess())
    }

    @Test
    fun `confidence is shown as a whole percentage`() {
        val p = FloatArray(9).also { it[7] = 0.826f; it[1] = 0.10f }
        assertTrue(CellReport.of(reading(Ink.PRINTED, *p)).describe().contains("83%"))
    }
}
