package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `no overlay draws nothing`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.NONE, HintStyle.EXPLAIN, false)
        assertTrue(overlay.digits.isEmpty())
        assertTrue(overlay.highlighted.isEmpty())
    }

    @Test
    fun `the solution fills every empty cell and touches no given`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.SOLUTION, HintStyle.EXPLAIN, false)
        assertEquals(81 - puzzle.filledCount, overlay.digits.size)
        for ((index, drawn) in overlay.digits) {
            assertTrue(!puzzle[index].isFilled, "cell $index was already filled")
            assertEquals(solution[index].digit, drawn.digit)
            assertEquals(OverlayRole.FILLED, drawn.role)
        }
    }

    @Test
    fun `checking marks a right answer green and a wrong one red`() {
        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }

        val right = puzzle.with(empty, Cell.guess(solution[empty].digit!!))
        val rightOverlay = PuzzleLogic.overlay(right, OverlayMode.CHECK, HintStyle.EXPLAIN, false)
        assertEquals(OverlayRole.CORRECT, rightOverlay.digits[empty]?.role)

        val wrong = puzzle.with(empty, Cell.guess(wrongDigit))
        val wrongOverlay = PuzzleLogic.overlay(wrong, OverlayMode.CHECK, HintStyle.EXPLAIN, false)
        assertEquals(OverlayRole.INCORRECT, wrongOverlay.digits[empty]?.role)
    }

    @Test
    fun `an explained hint highlights its evidence and withholds the digit`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN, false)
        assertTrue(overlay.highlighted.isNotEmpty(), "an explanation must point at something")
        assertTrue(overlay.digits.isEmpty(), "the digit must not be given away until asked")
    }

    @Test
    fun `asking again reveals the digit, and it is the right one`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN, true)
        assertEquals(1, overlay.digits.size)
        val (index, drawn) = overlay.digits.entries.single()
        assertEquals(solution[index].digit, drawn.digit)
        assertEquals(OverlayRole.HINT, drawn.role)
    }

    @Test
    fun `the plain hint style gives the digit straight away`() {
        val overlay = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.REVEAL, false)
        assertEquals(1, overlay.digits.size)
        val (index, drawn) = overlay.digits.entries.single()
        assertEquals(solution[index].digit, drawn.digit)
    }

    @Test
    fun `the status line distinguishes unfinished, wrong, solved and unreadable`() {
        assertTrue(PuzzleLogic.status(puzzle).contains("still empty"), PuzzleLogic.status(puzzle))

        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }
        val withMistake = PuzzleLogic.status(puzzle.with(empty, Cell.guess(wrongDigit)))
        assertTrue(withMistake.contains("1 cell disagrees"), withMistake)
        // The wording has to say the number shown is what was *read*, because without
        // that a misreading looks exactly like the app marking a correct answer wrong.
        assertTrue(withMistake.contains("what the app read"), withMistake)

        var finished = puzzle
        for (i in 0 until 81) {
            if (!finished[i].isFilled) finished = finished.with(i, Cell.guess(solution[i].digit!!))
        }
        assertTrue(PuzzleLogic.status(finished).contains("every answer is right"), PuzzleLogic.status(finished))

        val broken = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertTrue(PuzzleLogic.status(broken).contains("not make a solvable puzzle"))
    }

    @Test
    fun `solving a finished puzzle overlays every written cell, not just the empty ones`() {
        // Reported from the phone: Solve showed a single digit on a completed puzzle,
        // because only cells the app read as empty were being drawn.
        var finished = puzzle
        for (i in 0 until 81) {
            if (!finished[i].isFilled) finished = finished.with(i, Cell.guess(solution[i].digit!!))
        }
        val overlay = PuzzleLogic.overlay(finished, OverlayMode.SOLUTION, HintStyle.EXPLAIN, false)
        assertEquals(81 - puzzle.givenCount, overlay.digits.size)
        assertTrue(overlay.digits.values.all { it.role == OverlayRole.FILLED })
    }

    @Test
    fun `a finished puzzle has no hint left to give`() {
        var finished = puzzle
        for (i in 0 until 81) {
            if (!finished[i].isFilled) finished = finished.with(i, Cell.guess(solution[i].digit!!))
        }
        assertTrue(PuzzleLogic.hint(finished, HintStyle.REVEAL) == null)
        assertTrue(PuzzleLogic.overlay(finished, OverlayMode.HINT, HintStyle.REVEAL, false).digits.isEmpty())
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
        assertTrue(PuzzleLogic.status(ambiguous).contains("More than one solution"))
    }
}
