package io.github.tonyxmelon.aisudoku.solver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TechniqueSolverTest {

    @Test
    fun `solves an easy puzzle by reasoning alone`() {
        val outcome = TechniqueSolver.solve(Puzzles.EASY)
        assertIs<TechniqueOutcome.Solved>(outcome)
        assertTrue(outcome.solution.isComplete)
        assertTrue(outcome.solution.isValid)
    }

    @Test
    fun `agrees with the backtracking solver`() {
        val byLogic = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        val bySearch = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY))
        assertEquals(bySearch.solution.toGivensString(), byLogic.solution.toGivensString())
    }

    @Test
    fun `grades a puzzle by the hardest technique it needed`() {
        val outcome = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        assertEquals(outcome.steps.maxOf { it.difficulty }, outcome.difficulty)
    }

    @Test
    fun `records the steps it took in order`() {
        val outcome = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        assertTrue(outcome.steps.isNotEmpty(), "an explainer that explains nothing is useless")
        assertTrue(outcome.steps.any { it.technique == NakedSingle.name || it.technique == HiddenSingle.name })
    }

    @Test
    fun `gives up gracefully on a puzzle beyond its techniques`() {
        val outcome = TechniqueSolver.solve(Puzzles.HARDEST)
        if (outcome is TechniqueOutcome.Stuck) {
            assertTrue(outcome.partial.filledCount >= Puzzles.HARDEST.givenCount)
        } else {
            assertIs<TechniqueOutcome.Solved>(outcome)
        }
    }

    @Test
    fun `reports an unsolvable puzzle rather than looping`() {
        assertIs<TechniqueOutcome.Invalid>(TechniqueSolver.solve(Puzzles.CONTRADICTORY))
    }
}

/**
 * Records how far the four implemented techniques actually reach.
 *
 * This is the signal for whether more techniques are worth building: when reasoning
 * stalls, [ExplainedHintEngine] falls back to a plain reveal, and a user who chose
 * "explain it" stops getting explanations.
 */
class TechniqueReachTest {

    @Test
    fun `reasoning alone finishes an easy puzzle`() {
        val outcome = assertIs<TechniqueOutcome.Solved>(TechniqueSolver.solve(Puzzles.EASY))
        println("EASY: solved in ${outcome.steps.size} steps, graded ${outcome.difficulty}")
    }

    @Test
    fun `the reach on the hardest known puzzle is recorded`() {
        when (val outcome = TechniqueSolver.solve(Puzzles.HARDEST)) {
            is TechniqueOutcome.Solved ->
                println("HARDEST: solved in ${outcome.steps.size} steps, graded ${outcome.difficulty}")

            is TechniqueOutcome.Stuck -> {
                val placed = outcome.partial.filledCount - Puzzles.HARDEST.givenCount
                println(
                    "HARDEST: stuck after ${outcome.steps.size} steps, " +
                        "placed $placed of the ${81 - Puzzles.HARDEST.givenCount} missing digits"
                )
                assertTrue(outcome.partial.filledCount >= Puzzles.HARDEST.givenCount)
            }

            TechniqueOutcome.Invalid -> error("the hardest puzzle is a valid puzzle")
        }
    }
}
