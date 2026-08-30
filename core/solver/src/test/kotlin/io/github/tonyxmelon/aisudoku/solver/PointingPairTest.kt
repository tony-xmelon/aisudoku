package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PointingPairTest {

    /**
     * In box 0, digit 3 can only sit at cells 0 and 1 — both in row 0.
     * So 3 belongs somewhere in row 0 inside box 0, and can go from cells 3..8.
     */
    private fun threeConfinedToRowZeroOfBoxZero(): SolverState {
        val boxCellsWithoutThree = listOf(2, 9, 10, 11, 18, 19, 20)
        return stateWithCandidates(
            0 to CandidateSet.of(3, 4),
            1 to CandidateSet.of(3, 5),
            *boxCellsWithoutThree.map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        )
    }

    @Test
    fun `eliminates the digit from the rest of the line`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertEquals(3, deduction.digit)
        assertEquals(setOf(3, 4, 5, 6, 7, 8), deduction.fromCells)
        assertEquals(Difficulty.HARD, deduction.difficulty)
    }

    @Test
    fun `points at the confined cells as evidence`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertEquals(setOf(0, 1), deduction.supportingCells)
    }

    @Test
    fun `explains the confinement`() {
        val deduction = assertIs<Deduction.Elimination>(PointingPair.find(threeConfinedToRowZeroOfBoxZero()))
        assertTrue(deduction.explanation.contains("3"), deduction.explanation)
        assertTrue(deduction.explanation.contains("box"), deduction.explanation)
    }

    @Test
    fun `finds nothing when the digit is spread across rows of the box`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(3, 4),
            10 to CandidateSet.of(3, 5),   // row 1, so not confined to one row
            *listOf(1, 2, 9, 11, 18, 19, 20)
                .map { it to CandidateSet.of(1, 2) }.toTypedArray(),
        )
        assertNull(PointingPair.find(state))
    }
}
