package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HintEngineTest {

    @Test
    fun `reveal names a cell and its correct digit`() {
        val hint = assertIs<Hint.Reveal>(RevealHintEngine.nextHint(Puzzles.EASY))
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        assertEquals(truth[hint.index].digit, hint.digit)
        assertEquals(Cell.Empty, Puzzles.EASY[hint.index])
    }

    @Test
    fun `explained names the technique, its evidence, and the digit`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.EASY))
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        assertTrue(hint.technique.isNotBlank())
        assertTrue(hint.explanation.isNotBlank())
        assertTrue(hint.supportingCells.isNotEmpty())
        assertEquals(truth[hint.index].digit, hint.digit)
    }

    @Test
    fun `explained falls back to a reveal when no known technique applies`() {
        val hint = ExplainedHintEngine.nextHint(Puzzles.HARDEST)
        assertTrue(hint is Hint.Explained || hint is Hint.Reveal, "got $hint")
    }

    @Test
    fun `there is no hint for a finished puzzle`() {
        val solved = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val asGivens = Grid.of(solved.cells.map { Cell.given(it.digit!!) })
        assertNull(RevealHintEngine.nextHint(asGivens))
        assertNull(ExplainedHintEngine.nextHint(asGivens))
    }

    @Test
    fun `there is no hint for a puzzle that cannot be solved`() {
        assertNull(RevealHintEngine.nextHint(Puzzles.CONTRADICTORY))
        assertNull(ExplainedHintEngine.nextHint(Puzzles.CONTRADICTORY))
    }

    /**
     * The bug this guards against was reported from a phone: the hint pointed at a cell
     * the user had already written 9 in and explained that it could only be a 9.
     */
    @Test
    fun `no hint lands on a cell the user has already filled in`() {
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        var grid = Puzzles.EASY
        for (i in 0 until 81) {
            if (!grid[i].isFilled && i % 3 == 0) grid = grid.with(i, Cell.guess(truth[i].digit!!))
        }
        for (engine in listOf(RevealHintEngine, ExplainedHintEngine)) {
            val hint = engine.nextHint(grid)!!
            assertEquals(Cell.Empty, grid[hint.index], "$engine landed on a filled cell")
        }
    }

    /** Every cell answered correctly but for one: the hint has to be that one cell. */
    @Test
    fun `a hint reasons from the answers the user got right`() {
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        var grid = Puzzles.EASY
        val open = (0 until 81).filter { !grid[it].isFilled }
        for (i in open.drop(1)) grid = grid.with(i, Cell.guess(truth[i].digit!!))

        val hint = ExplainedHintEngine.nextHint(grid)!!
        assertEquals(open.first(), hint.index)
        assertEquals(truth[open.first()].digit, hint.digit)
    }

    /** A wrong answer must not be reasoned from, or the advice built on it is wrong too. */
    @Test
    fun `a wrong answer is ignored rather than believed`() {
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        val open = (0 until 81).filter { !Puzzles.EASY[it].isFilled }
        val spoiled = open.first()
        val wrong = (1..9).first { it != truth[spoiled].digit }
        val grid = Puzzles.EASY.with(spoiled, Cell.guess(wrong))

        val hint = ExplainedHintEngine.nextHint(grid)!!
        assertEquals(truth[hint.index].digit, hint.digit, "the hint must agree with the solution")
    }
}
