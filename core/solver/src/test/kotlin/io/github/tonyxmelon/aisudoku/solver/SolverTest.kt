package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SolverTest {

    /** A solution must be full, legal, and consistent with the puzzle it came from. */
    private fun assertSolves(puzzle: Grid, solution: Grid) {
        assertTrue(solution.isComplete, "solution has empty cells:\n$solution")
        assertTrue(solution.isValid, "solution breaks the rules:\n$solution")
        for (i in 0 until 81) {
            val given = puzzle[i]
            if (given.source == CellSource.GIVEN) {
                assertEquals(given.digit, solution[i].digit, "cell $i disagrees with its given")
            }
        }
    }

    @Test
    fun `solves an easy puzzle uniquely`() {
        val result = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY))
        assertSolves(Puzzles.EASY, result.solution)
    }

    @Test
    fun `solves the hardest known puzzle uniquely`() {
        val result = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.HARDEST))
        assertSolves(Puzzles.HARDEST, result.solution)
    }

    @Test
    fun `reports no solution when the givens contradict`() {
        assertIs<SolveResult.None>(Solver.solve(Puzzles.CONTRADICTORY))
    }

    @Test
    fun `reports multiple solutions and they genuinely differ`() {
        val result = assertIs<SolveResult.Multiple>(Solver.solve(Puzzles.AMBIGUOUS))
        assertSolves(Puzzles.AMBIGUOUS, result.first)
        assertSolves(Puzzles.AMBIGUOUS, result.second)
        assertTrue(result.first != result.second)
        assertTrue(result.ambiguousCells.isNotEmpty())
        assertTrue(result.ambiguousCells.all { result.first[it].digit != result.second[it].digit })
    }

    @Test
    fun `an empty grid has many solutions`() {
        assertIs<SolveResult.Multiple>(Solver.solve(Grid.Empty))
    }

    @Test
    fun `a completed grid solves to itself`() {
        val solved = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val asGivens = Grid.of(solved.cells.map { Cell.given(it.digit!!) })
        val again = assertIs<SolveResult.Unique>(Solver.solve(asGivens))
        assertEquals(asGivens.toGivensString(), again.solution.toGivensString())
    }

    @Test
    fun `guesses do not influence the solution`() {
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val wrongDigit = (1..9).first { it != truth[2].digit }
        val withGuess = Puzzles.EASY.with(2, Cell.guess(wrongDigit))
        val result = assertIs<SolveResult.Unique>(Solver.solve(withGuess))
        assertEquals(truth.toGivensString(), result.solution.toGivensString())
    }

    @Test
    fun `every puzzle produced by removing cells from a solution is still solvable`() {
        val random = Random(seed = 20260830)
        val full = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        repeat(20) {
            val keep = (0 until 81).shuffled(random).take(40).toSet()
            val puzzle = Grid.of(full.cells.mapIndexed { i, c ->
                if (i in keep) Cell.given(c.digit!!) else Cell.Empty
            })
            val result = Solver.solve(puzzle)
            assertTrue(
                result is SolveResult.Unique || result is SolveResult.Multiple,
                "a puzzle carved from a real solution must be solvable, got $result",
            )
        }
    }

    @Test
    fun `the hardest puzzle solves well inside the interactive budget`() {
        val start = System.nanoTime()
        Solver.solve(Puzzles.HARDEST)
        val millis = (System.nanoTime() - start) / 1_000_000
        assertTrue(millis < 1000, "took ${millis}ms, which is too slow to sit behind a button")
    }
}
