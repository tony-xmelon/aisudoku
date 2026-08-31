package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PuzzleLogicTest {

    private val puzzle = Grid.fromRows(
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

    private val solution = assertIs<SolveResult.Unique>(Solver.solve(puzzle)).solution

    private fun finished(): Grid {
        var out = puzzle
        for (i in 0 until 81) {
            if (!out[i].isFilled) out = out.with(i, Cell.guess(solution[i].digit!!))
        }
        return out
    }

    @Test
    fun `no overlay draws nothing`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.NONE, HintStyle.EXPLAIN)
        assertTrue(overlay.digits.isEmpty())
        assertTrue(overlay.evidence.isEmpty())
    }

    @Test
    fun `the solution fills every empty cell and touches no given`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertEquals(81 - puzzle.filledCount, overlay.digits.size)
        for ((index, drawn) in overlay.digits) {
            assertTrue(!puzzle[index].isFilled, "cell $index was already filled")
            assertEquals(solution[index].digit, drawn.digit)
            assertEquals(OverlayRole.SOLUTION, drawn.role)
        }
    }

    @Test
    fun `checking marks a right answer green and a wrong one red`() {
        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }

        val right = puzzle.with(empty, Cell.guess(solution[empty].digit!!))
        assertEquals(
            OverlayRole.CORRECT,
            PuzzleLogic.overlay(right, OverlayMode.CHECK, HintStyle.EXPLAIN).digits[empty]?.role,
        )

        val wrong = puzzle.with(empty, Cell.guess(wrongDigit))
        val overlay = PuzzleLogic.overlay(wrong, OverlayMode.CHECK, HintStyle.EXPLAIN)
        assertEquals(OverlayRole.INCORRECT, overlay.digits[empty]?.role)
        // What is drawn on a red cell is what the app READ, not the true digit. Without
        // that, a misreading looks exactly like the app marking a right answer wrong.
        assertEquals(wrongDigit, overlay.digits[empty]?.digit)
    }

    @Test
    fun `an explained hint gives the digit and points at its evidence`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN)
        assertEquals(1, overlay.digits.size)
        val (index, drawn) = overlay.digits.entries.single()
        assertEquals(solution[index].digit, drawn.digit)
        assertEquals(OverlayRole.HINT, drawn.role)
        assertFalse(index in overlay.evidence, "the answer is not its own evidence")
        assertTrue(PuzzleLogic.guidance(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN)!!.isNotBlank())
    }

    @Test
    fun `the plain hint style gives the digit with no evidence`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.REVEAL)
        assertEquals(1, overlay.digits.size)
        assertTrue(overlay.evidence.isEmpty())
        val (index, drawn) = overlay.digits.entries.single()
        assertEquals(solution[index].digit, drawn.digit)
    }

    @Test
    fun `the status line distinguishes unfinished, wrong, solved and unreadable`() {
        assertEquals(Tone.NEUTRAL, PuzzleLogic.status(puzzle).tone)
        assertTrue(PuzzleLogic.status(puzzle).text.contains("cells to go"))

        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }
        val mistake = PuzzleLogic.status(puzzle.with(empty, Cell.guess(wrongDigit)))
        assertEquals(Tone.BAD, mistake.tone)
        assertTrue(mistake.text.contains("1 answer disagrees"), mistake.text)

        val done = PuzzleLogic.status(finished())
        assertEquals(Tone.GOOD, done.tone)
        assertTrue(done.text.contains("every answer is right"), done.text)

        val broken = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertTrue(PuzzleLogic.status(broken).text.contains("not make a solvable puzzle"))
    }

    /**
     * Reported from the phone: Solve showed a single digit on a completed puzzle,
     * because only cells the app had read as empty were being drawn.
     */
    @Test
    fun `solving a finished puzzle overlays every written cell, not just the empty ones`() {
        val overlay = PuzzleLogic.overlay(finished(), OverlayMode.SOLUTION, HintStyle.EXPLAIN)
        assertEquals(81 - puzzle.givenCount, overlay.digits.size)
        assertTrue(overlay.digits.values.all { it.role == OverlayRole.SOLUTION })
    }

    /**
     * Reported from the phone: Hint pointed at a cell the user had already written 9 in
     * and explained that it could only be a 9.
     */
    @Test
    fun `a finished puzzle has no hint left to give`() {
        val done = finished()
        for (style in HintStyle.entries) {
            assertFalse(PuzzleLogic.canHint(done, style), "$style still offered a hint")
            assertTrue(PuzzleLogic.overlay(done, OverlayMode.HINT, style).digits.isEmpty())
        }
    }

    @Test
    fun `a hint never lands on a cell that already has something written in it`() {
        var grid = puzzle
        for (i in 0 until 81) {
            if (!grid[i].isFilled && i % 2 == 0) grid = grid.with(i, Cell.guess(solution[i].digit!!))
        }
        for (style in HintStyle.entries) {
            val overlay = PuzzleLogic.overlay(grid, OverlayMode.HINT, style)
            val index = overlay.digits.keys.single()
            assertFalse(grid[index].isFilled, "$style pointed at a filled cell")
        }
    }

    @Test
    fun `a puzzle missing a given is reported as ambiguous rather than solved`() {
        val ambiguous = Grid.fromRows(
            "....7....",
            "6..195...",
            ".98....6.",
            "8...6...3",
            "4..8.3..1",
            "7...2...6",
            ".6....28.",
            "...419..5",
            "....8..79",
        )
        assertTrue(PuzzleLogic.status(ambiguous).text.contains("More than one solution"))
    }

    private fun keys(grid: Grid, mode: OverlayMode, unsure: Boolean = false) =
        PuzzleLogic.legend(PuzzleLogic.overlay(grid, mode, HintStyle.EXPLAIN), mode, unsure)

    @Test
    fun `the key names exactly what is drawn and nothing else`() {
        assertEquals(emptyList(), keys(puzzle, OverlayMode.NONE))

        // Nothing has been answered yet, so "right" and "wrong" would both be lies.
        assertEquals(emptyList(), keys(puzzle, OverlayMode.CHECK))

        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }
        assertEquals(
            listOf(LegendKey.INCORRECT),
            keys(puzzle.with(empty, Cell.guess(wrongDigit)), OverlayMode.CHECK),
        )
        assertEquals(
            listOf(LegendKey.CORRECT),
            keys(puzzle.with(empty, Cell.guess(solution[empty].digit!!)), OverlayMode.CHECK),
        )

        assertEquals(
            listOf(LegendKey.SOLUTION, LegendKey.UNCERTAIN),
            keys(puzzle, OverlayMode.SOLUTION, unsure = true),
        )
        assertEquals(
            listOf(LegendKey.PRINTED, LegendKey.WRITTEN, LegendKey.MARKS),
            keys(puzzle, OverlayMode.READING),
        )
    }

    @Test
    fun `a hint with no technique behind it does not claim to show a reason`() {
        // The plain style never has evidence, so the key must not offer to explain one.
        val plain = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.REVEAL)
        assertEquals(listOf(LegendKey.HINT), PuzzleLogic.legend(plain, OverlayMode.HINT, false))

        // A naked single's evidence is the cell itself, so once the answer is taken out
        // there is nothing left to tint - and nothing left to name either.
        val explained = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN)
        assertTrue(explained.evidence.isEmpty())
        assertEquals(listOf(LegendKey.HINT), PuzzleLogic.legend(explained, OverlayMode.HINT, false))
    }
}
