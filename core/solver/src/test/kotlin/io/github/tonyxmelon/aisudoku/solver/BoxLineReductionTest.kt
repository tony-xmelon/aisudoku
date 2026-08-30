package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoxLineReductionTest {

    /**
     * In row 0, digit 4 can only sit at cells 0 and 1 — both in box 0.
     * So 4 is in box 0 on row 0, and can go from the box's other cells.
     */
    private fun fourConfinedToBoxZeroOfRowZero(): SolverState = stateWithCandidates(
        0 to CandidateSet.of(4, 7),
        1 to CandidateSet.of(4, 8),
        *(2..8).map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        *listOf(9, 10, 11, 18, 19, 20).map { it to CandidateSet.of(4, 9) }.toTypedArray(),
    )

    @Test
    fun `eliminates the digit from the rest of the box`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertEquals(4, deduction.digit)
        assertEquals(setOf(9, 10, 11, 18, 19, 20), deduction.fromCells)
        assertEquals(Difficulty.HARD, deduction.difficulty)
    }

    @Test
    fun `points at the confined cells as evidence`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertEquals(setOf(0, 1), deduction.supportingCells)
    }

    @Test
    fun `explains the confinement`() {
        val deduction = assertIs<Deduction.Elimination>(BoxLineReduction.find(fourConfinedToBoxZeroOfRowZero()))
        assertTrue(deduction.explanation.contains("4"), deduction.explanation)
        assertTrue(deduction.explanation.contains("box"), deduction.explanation)
    }

    @Test
    fun `finds nothing when the digit spans two boxes of the line`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(4, 7),
            4 to CandidateSet.of(4, 8),   // box 1, so the row's fours span two boxes
            *listOf(1, 2, 3, 5, 6, 7, 8).map { it to CandidateSet.of(1, 2) }.toTypedArray(),
            *listOf(9, 10, 11, 18, 19, 20).map { it to CandidateSet.of(4, 9) }.toTypedArray(),
        )
        assertNull(BoxLineReduction.find(state))
    }
}
