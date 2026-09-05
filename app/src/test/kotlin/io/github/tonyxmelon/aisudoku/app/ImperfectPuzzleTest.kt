package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the puzzle screen does with a photograph that is not a proper puzzle.
 *
 * Until recently these never arrived: the reader refused anything it could not solve, so
 * the screen was only ever asked to show puzzles with exactly one answer. Now that a
 * reading is handed on whatever it makes, both other outcomes reach the screen, and Solve
 * has to have something to say about each. Silence is what made it look like a button that
 * did nothing.
 */
class ImperfectPuzzleTest {

    /** The advertisement from the corpus: a staircase of forty-five, and many answers. */
    private val ambiguous = Grid.fromRows(
        "179852463",
        ".25934718",
        "..3176952",
        "...685371",
        "....13294",
        ".....9586",
        "......139",
        ".......45",
        "........7",
    )

    private val proper = Grid.fromRows(
        "53..7....",
        "6..195...",
        ".98....6.",
        "8...6...3",
        "4..8.3..1",
        "7...2...6",
        ".6....28.",
        "...419..5",
        "....8..79",
    )

    @Test
    fun `the advertisement really does have more than one answer`() {
        assertTrue(Solver.solve(ambiguous) is SolveResult.Multiple)
    }

    @Test
    fun `solve draws an answer for a puzzle that has several`() {
        val shown = PuzzleLogic.overlay(ambiguous, OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertTrue(
            shown.digits.isNotEmpty(),
            "Solve drew nothing, which is what made it look like it did nothing",
        )
        // Every square that is not printed gets a digit, exactly as a proper puzzle does.
        val blank = (0 until 81).count { ambiguous[it].digit == null }
        assertEquals(blank, shown.digits.size)
    }

    @Test
    fun `the squares the answers disagree about are marked`() {
        val shown = PuzzleLogic.overlay(ambiguous, OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertTrue(shown.evidence.isNotEmpty(), "nothing marked as under-determined")
        val solved = Solver.solve(ambiguous) as SolveResult.Multiple
        assertEquals(solved.ambiguousCells, shown.evidence)
    }

    @Test
    fun `pressing solve again steps to the next answer instead of putting it away`() {
        val many = Solver.solutions(ambiguous, PuzzleLogic.MOST_ANSWERS_OFFERED).size
        assertTrue(many > 1)
        assertEquals(
            1,
            PuzzleLogic.steppedAnswer(OverlayMode.SOLUTION, OverlayMode.SOLUTION, 0, many),
        )
        // And each step really shows something different.
        val first = PuzzleLogic.overlay(
            ambiguous, OverlayMode.SOLUTION, HintStyle.EXPLAIN, answerShown = 0,
        ).digits.mapValues { it.value.digit }
        val second = PuzzleLogic.overlay(
            ambiguous, OverlayMode.SOLUTION, HintStyle.EXPLAIN, answerShown = 1,
        ).digits.mapValues { it.value.digit }
        assertTrue(first != second, "the second press showed the same answer")
    }

    @Test
    fun `pressing solve twice on a proper puzzle still puts it away`() {
        assertEquals(
            null,
            PuzzleLogic.steppedAnswer(OverlayMode.SOLUTION, OverlayMode.SOLUTION, 0, 1),
            "a single answer should still toggle",
        )
    }

    @Test
    fun `an unsolvable puzzle has its squares to fix marked and named`() {
        // One digit changed to something that cannot be, which is what a misread looks like.
        val solution = (Solver.solve(proper) as SolveResult.Unique).solution
        val victim = (0 until 81).first { proper[it].digit == null }
        val wrong = (1..9).first { it != solution[victim].digit }
        val broken = proper.with(victim, Cell.given(wrong))
        assertTrue(Solver.solve(broken) is SolveResult.None, "the case must be unsolvable")

        val shown = PuzzleLogic.overlay(broken, OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertTrue(shown.evidence.isNotEmpty(), "Solve marked no squares to look at")
        assertNotNull(shown.focus, "nothing was pointed at")

        val said = PuzzleLogic.guidance(broken, OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertNotNull(said)
        assertTrue(
            "cannot make a puzzle" in said.body,
            "Solve did not say what was wrong: ${said.body}",
        )
    }

    @Test
    fun `both broken puzzles say what to do rather than only what is wrong`() {
        for (grid in listOf(ambiguous, brokenProper())) {
            val status = PuzzleLogic.status(grid)
            assertNotNull(status, "nothing said about a puzzle that will not solve")
            assertTrue(
                "Press Solve" in status.text,
                "said what was wrong without saying what to do: ${status.text}",
            )
        }
    }

    private fun brokenProper(): Grid {
        val solution = (Solver.solve(proper) as SolveResult.Unique).solution
        val victim = (0 until 81).first { proper[it].digit == null }
        val wrong = (1..9).first { it != solution[victim].digit }
        return proper.with(victim, Cell.given(wrong))
    }

}
