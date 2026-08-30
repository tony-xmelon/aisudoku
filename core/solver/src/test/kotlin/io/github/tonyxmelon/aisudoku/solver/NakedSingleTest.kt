package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NakedSingleTest {

    @Test
    fun `finds the cell with a single remaining candidate`() {
        val state = stateWithCandidates(
            40 to CandidateSet.of(7),
            41 to CandidateSet.of(2, 5),
        )
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertEquals(40, deduction.index)
        assertEquals(7, deduction.digit)
        assertEquals(Difficulty.EASY, deduction.difficulty)
    }

    @Test
    fun `points at the cell itself as the evidence`() {
        val state = stateWithCandidates(40 to CandidateSet.of(7))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertEquals(setOf(40), deduction.supportingCells)
    }

    @Test
    fun `explains itself in terms a person would use`() {
        val state = stateWithCandidates(40 to CandidateSet.of(7))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertTrue(deduction.explanation.contains("7"), deduction.explanation)
        assertTrue(deduction.explanation.isNotBlank())
    }

    @Test
    fun `finds nothing when every cell has options`() {
        val state = stateWithCandidates(
            0 to CandidateSet.of(1, 2),
            1 to CandidateSet.of(3, 4),
        )
        assertNull(NakedSingle.find(state))
    }
}
