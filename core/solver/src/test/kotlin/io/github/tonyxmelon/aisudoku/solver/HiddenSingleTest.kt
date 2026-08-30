package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HiddenSingleTest {

    /**
     * Row 0: only cell 4 can still be a 5, though cell 4 could also be a 6.
     * A naked single would miss this; the cell is not down to one candidate.
     */
    private fun rowWithOnePlaceForFive(): SolverState = stateWithCandidates(
        0 to CandidateSet.of(1, 2),
        1 to CandidateSet.of(1, 2),
        2 to CandidateSet.of(3, 4),
        3 to CandidateSet.of(3, 4),
        4 to CandidateSet.of(5, 6),
        5 to CandidateSet.of(6, 7),
        6 to CandidateSet.of(7, 8),
        7 to CandidateSet.of(8, 9),
        8 to CandidateSet.of(9, 1),
    )

    @Test
    fun `finds the only cell in a unit that can hold a digit`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertEquals(4, deduction.index)
        assertEquals(5, deduction.digit)
        assertEquals(Difficulty.MEDIUM, deduction.difficulty)
    }

    @Test
    fun `offers the whole unit as evidence`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertEquals((0..8).toSet(), deduction.supportingCells)
    }

    @Test
    fun `names the unit it reasoned about`() {
        val deduction = assertIs<Deduction.Placement>(HiddenSingle.find(rowWithOnePlaceForFive()))
        assertTrue(deduction.explanation.contains("row"), deduction.explanation)
        assertTrue(deduction.explanation.contains("5"), deduction.explanation)
    }

    @Test
    fun `finds nothing when every digit has several homes`() {
        val state = stateWithCandidates(
            *(0..8).map { it to CandidateSet.ALL }.toTypedArray()
        )
        assertNull(HiddenSingle.find(state))
    }
}
