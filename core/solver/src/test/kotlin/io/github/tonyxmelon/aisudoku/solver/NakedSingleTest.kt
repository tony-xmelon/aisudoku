package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    /**
     * The evidence is the neighbours that used the other digits up, not the square
     * itself. Pointing at the square tells the user to look where they are already
     * looking, and it made the second rung of the hint staircase change nothing at all.
     */
    @Test
    fun `points at the neighbours that ruled the other digits out`() {
        val grid = io.github.tonyxmelon.aisudoku.model.Grid.fromRows(
            "12345678.",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
        )
        val state = assertIs<SolverState>(SolverState.candidatesOnly(grid))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))

        assertEquals(8, deduction.index)
        assertEquals(9, deduction.digit)
        assertEquals((0..7).toSet(), deduction.supportingCells)
    }

    @Test
    fun `explains itself in terms a person would use`() {
        val state = stateWithCandidates(40 to CandidateSet.of(7))
        val deduction = assertIs<Deduction.Placement>(NakedSingle.find(state))
        assertTrue(deduction.explanation.contains("7"), deduction.explanation)
        assertTrue(deduction.explanation.isNotBlank())
    }

    /**
     * Reported from the phone, on a square the user had pencilled as 1 or 5: the app said
     * "naked single, only 5 can go here - every other digit already appears in this row,
     * column or box", highlighted six squares, and the user asked why it was not a 1.
     *
     * It was a fair question. Six squares can only account for six digits, and the
     * sentence was false: 1 and 6 appeared nowhere near that square. They had been struck
     * out by earlier steps of the walkthrough, which place nothing and so leave nothing on
     * the board to point at.
     */
    @Test
    fun `never claims a digit is visible in the row, column or box when it is not`() {
        val solution = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.HARDEST)).solution
        val state = assertIs<SolverState>(
            SolverState.candidatesOnly(progressGrid(Puzzles.HARDEST, solution))
        )
        val route = assertNotNull(TechniqueSolver.walkthrough(Puzzles.HARDEST))

        var checked = 0
        for (step in route.steps) {
            if (step is Deduction.Placement && step.technique == NakedSingle.name) {
                val visible = Coordinates.peers[step.index]
                    .mapNotNull { state.valueAt(it) }
                    .toSet() - step.digit
                val gone = (1..9) - step.digit - visible

                if (gone.isEmpty()) {
                    assertTrue(
                        step.explanation.contains("every other digit already appears"),
                        step.explanation,
                    )
                } else {
                    checked++
                    assertFalse(
                        step.explanation.contains("every other digit already appears"),
                        "claimed ${gone.size} digits were visible when they were not: " +
                            step.explanation,
                    )
                    // Named, so the user can see where the hole in the highlight went.
                    for (digit in gone) {
                        assertTrue(
                            step.explanation.contains("$digit"),
                            "did not account for $digit: " + step.explanation,
                        )
                    }
                    assertEquals(
                        visible.size,
                        step.supportingCells.size,
                        "the highlight should show every digit it claims to: " + step.explanation,
                    )
                }
            }
            TechniqueSolver.apply(state, step)
        }
        assertTrue(checked > 0, "this puzzle no longer exercises the case at all")
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
