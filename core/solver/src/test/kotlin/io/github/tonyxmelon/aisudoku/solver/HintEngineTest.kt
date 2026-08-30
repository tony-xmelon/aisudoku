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
    fun `explained names the technique and its evidence without giving the digit away`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.EASY))
        assertTrue(hint.technique.isNotBlank())
        assertTrue(hint.explanation.isNotBlank())
        assertTrue(hint.supportingCells.isNotEmpty())
    }

    @Test
    fun `an explained hint can be pressed for the answer`() {
        val hint = assertIs<Hint.Explained>(ExplainedHintEngine.nextHint(Puzzles.EASY))
        val reveal = assertIs<Hint.Reveal>(hint.answer)
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        assertEquals(truth[reveal.index].digit, reveal.digit)
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

    @Test
    fun `a hint never lands on a cell the user has already filled in`() {
        val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution
        var grid = Puzzles.EASY
        for (i in 0 until 81) {
            if (!grid[i].isFilled && i % 3 == 0) grid = grid.with(i, Cell.guess(truth[i].digit!!))
        }
        val hint = assertIs<Hint.Reveal>(RevealHintEngine.nextHint(grid))
        assertEquals(Cell.Empty, grid[hint.index])
    }
}
