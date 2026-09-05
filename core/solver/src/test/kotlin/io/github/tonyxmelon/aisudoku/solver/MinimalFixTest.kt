package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MinimalFixTest {

    private fun givens(rows: String): Grid {
        val text = rows.filter { it.isDigit() || it == '.' }
        require(text.length == 81) { "expected 81 cells, got ${text.length}" }
        var grid = Grid.Empty
        text.forEachIndexed { i, c ->
            if (c != '.') grid = grid.with(i, Cell.given(c - '0'))
        }
        return grid
    }

    @Test
    fun `a puzzle that solves needs nothing changed`() {
        assertEquals(emptySet(), MinimalFix.find(Puzzles.EASY))
    }

    /**
     * One digit changed to something that contradicts, which is what a misread looks like.
     *
     * The fix is not required to name the square that was altered - another square in the
     * same unit could be blamed for the same clash - but it must name one whose removal
     * lets the puzzle solve again, and there must be only one of them.
     */
    @Test
    fun `one misread digit is found and named`() {
        val solution = assertIs(Solver.solve(Puzzles.EASY))
        // Somewhere the puzzle does not already have a digit, put one that cannot be right.
        val victim = (0 until 81).first { Puzzles.EASY[it].digit == null }
        val wrong = (1..9).first { it != solution[victim].digit }
        val broken = Puzzles.EASY.with(victim, Cell.given(wrong))

        assertTrue(Solver.solve(broken) is SolveResult.None, "the test case must be unsolvable")

        val fix = MinimalFix.find(broken)
        assertNotNull(fix, "a single wrong digit should be findable")
        assertEquals(1, fix.size, "one wrong digit should need one change, not $fix")
        var repaired = broken
        for (cell in fix) repaired = repaired.with(cell, Cell.Empty)
        assertTrue(
            Solver.solve(repaired) !is SolveResult.None,
            "removing what it named should leave a solvable puzzle",
        )
    }

    @Test
    fun `it gives up rather than guessing when too much is wrong`() {
        // Three digits of the same value down one column: no one or two removals help.
        var broken = Grid.Empty
        broken = broken.with(0, Cell.given(5))
        broken = broken.with(9, Cell.given(5))
        broken = broken.with(18, Cell.given(5))
        broken = broken.with(27, Cell.given(5))
        assertTrue(Solver.solve(broken) is SolveResult.None)
        assertNull(MinimalFix.find(broken), "four in a column cannot be fixed by removing two")
    }

    @Test
    fun `the squares that clash are the ones a person would point at`() {
        var grid = Grid.Empty
        grid = grid.with(0, Cell.given(5))
        grid = grid.with(4, Cell.given(5))
        assertEquals(setOf(0, 4), MinimalFix.clashes(grid))
    }

    @Test
    fun `an ambiguous puzzle hands back as many answers as asked for`() {
        // One given: wildly ambiguous, so the limit is what decides how many come back.
        val loose = Grid.Empty.with(0, Cell.given(1))
        assertEquals(5, Solver.solutions(loose, 5).size)
        assertEquals(1, Solver.solutions(loose, 1).size)
        assertTrue(Solver.solutions(loose, 0).isEmpty())
    }

    @Test
    fun `a proper puzzle has exactly one answer however many are asked for`() {
        assertEquals(1, Solver.solutions(Puzzles.EASY, 5).size)
    }

    @Test
    fun `the answers handed back really are different from one another`() {
        val loose = Grid.Empty.with(0, Cell.given(1))
        val many = Solver.solutions(loose, 4)
        val distinct = many.map { grid -> (0 until 81).map { grid[it].digit }.joinToString() }
        assertEquals(distinct.size, distinct.distinct().size, "duplicate solutions handed back")
    }

    private fun assertIs(result: SolveResult): Grid {
        assertTrue(result is SolveResult.Unique, "expected a unique solution, got $result")
        return result.solution
    }
}
