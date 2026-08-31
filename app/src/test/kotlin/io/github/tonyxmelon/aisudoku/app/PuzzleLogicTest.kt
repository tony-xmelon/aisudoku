package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import io.github.tonyxmelon.aisudoku.solver.TechniqueSolver
import io.github.tonyxmelon.aisudoku.solver.Techniques
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertTrue(PuzzleLogic.guidance(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN)!!.body.isNotBlank())
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
    fun `the status line speaks only when there is news, and the counter carries the rest`() {
        // A puzzle in progress is not news, and saying "46 cells to go" cost the pane a
        // line on every screen. The count lives under the grid now.
        assertNull(PuzzleLogic.status(puzzle))
        assertEquals("51/81", PuzzleLogic.progress(puzzle))
        assertEquals("0/81", PuzzleLogic.progress(finished()))

        // Whether an answer is right is the Check button's business. Marking one wrong
        // the moment it is written made every square a test the app graded, silently when
        // you were right and out loud when you were not.
        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }
        assertNull(PuzzleLogic.status(puzzle.with(empty, Cell.guess(wrongDigit))))

        // Finished is the one moment it is worth saying unasked: there is nothing left to
        // work on, so "not yet" is the whole news.
        val botched = finished().with(empty, Cell.guess(wrongDigit))
        val wrong = assertNotNull(PuzzleLogic.status(botched))
        assertEquals(Tone.BAD, wrong.tone)
        assertTrue(wrong.text.contains("one answer disagrees"), wrong.text)

        val done = assertNotNull(PuzzleLogic.status(finished()))
        assertEquals(Tone.GOOD, done.tone)
        assertTrue(done.text.contains("every answer is right"), done.text)

        val broken = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertTrue(assertNotNull(PuzzleLogic.status(broken)).text.contains("not make a solvable puzzle"))
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
        assertTrue(assertNotNull(PuzzleLogic.status(ambiguous)).text.contains("More than one solution"))
    }

    /**
     * Reported from the phone: answering a square did nothing visible unless the answer
     * was wrong, in which case a line appeared saying so. Backwards on both counts - the
     * digit is on no photograph, so it has to be drawn, and grading it is the Check
     * button's job.
     */
    @Test
    fun `a digit you type is drawn wherever nothing else speaks for that square`() {
        val empty = (0 until 81).first { !puzzle[it].isFilled }
        val wrongDigit = (1..9).first { it != solution[empty].digit }
        val answered = puzzle.with(empty, Cell.guess(wrongDigit))
        val typed = setOf(empty)

        val plain = PuzzleLogic.overlay(answered, OverlayMode.NONE, HintStyle.EXPLAIN, entered = typed)
        assertEquals(wrongDigit, plain.digits[empty]?.digit)
        assertEquals(OverlayRole.WRITTEN, plain.digits[empty]?.role)
        assertEquals(listOf(LegendKey.WRITTEN), PuzzleLogic.legend(plain, OverlayMode.NONE, false))
        assertNull(PuzzleLogic.status(answered), "answering a square is not a test")

        // Check and Solve already have something to say about an answered square, and
        // what they say wins.
        val checked = PuzzleLogic.overlay(answered, OverlayMode.CHECK, HintStyle.EXPLAIN, entered = typed)
        assertEquals(OverlayRole.INCORRECT, checked.digits[empty]?.role)
        val solved = PuzzleLogic.overlay(answered, OverlayMode.SOLUTION, HintStyle.EXPLAIN, entered = typed)
        assertEquals(OverlayRole.SOLUTION, solved.digits[empty]?.role)

        // The reading layer draws it from the grid itself, so a second copy would land on
        // top of the first.
        val read = PuzzleLogic.overlay(answered, OverlayMode.READING, HintStyle.EXPLAIN, entered = typed)
        assertTrue(read.digits.isEmpty(), "the reading layer draws its own")
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

        // The explained style always has something to point at, including for a naked
        // single: its evidence is the neighbours that used the other digits up. When that
        // was the square itself, two rungs of the staircase looked identical on screen.
        val explained = PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN)
        assertTrue(explained.evidence.isNotEmpty())
        assertEquals(
            listOf(LegendKey.HINT, LegendKey.EVIDENCE),
            PuzzleLogic.legend(explained, OverlayMode.HINT, false),
        )
    }

    // ---------------------------------------------------------------- training

    private fun hintOverlay(depth: Int) =
        PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN, hintDepth = depth)

    @Test
    fun `an explained hint gives away nothing until the last step of the staircase`() {
        for (depth in 0 until PuzzleLogic.HINT_DEPTHS - 1) {
            val step = hintOverlay(depth)
            assertTrue(step.digits.isEmpty(), "depth $depth handed over the digit")
            assertTrue(
                step.evidence.isNotEmpty() || step.focus != null,
                "depth $depth points at nothing at all",
            )
        }
        val last = hintOverlay(PuzzleLogic.HINT_DEPTHS - 1)
        val (index, drawn) = last.digits.entries.single()
        assertEquals(solution[index].digit, drawn.digit)
    }

    /**
     * Reported from the phone: the first two presses of Hint drew the same eight squares,
     * so the second looked like it had done nothing.
     */
    @Test
    fun `no two rungs of the staircase look the same`() {
        val seen = (0 until PuzzleLogic.HINT_DEPTHS).map {
            val step = hintOverlay(it)
            Triple(step.evidence, step.focus, step.digits.keys)
        }
        assertEquals(seen.size, seen.toSet().size, "two rungs draw the same thing: $seen")
    }

    @Test
    fun `the staircase widens from a region to a square before naming the digit`() {
        assertNull(hintOverlay(0).focus, "the first step should not single out a square")
        assertNull(hintOverlay(1).focus, "naming the technique is not naming the square")
        val focus = assertNotNull(hintOverlay(2).focus)
        assertFalse(puzzle[focus].isFilled)
        assertEquals(focus, hintOverlay(3).digits.keys.single())
    }

    @Test
    fun `each press of Hint goes one step down, then turns it off`() {
        var mode = OverlayMode.NONE
        var depth = 0
        val seen = mutableListOf<Int>()
        repeat(PuzzleLogic.HINT_DEPTHS + 1) {
            val next = PuzzleLogic.press(mode, OverlayMode.HINT, depth, HintStyle.EXPLAIN)
            mode = next.mode
            depth = next.hintDepth
            if (mode == OverlayMode.HINT) seen += depth
        }
        assertEquals((0 until PuzzleLogic.HINT_DEPTHS).toList(), seen)
        assertEquals(OverlayMode.NONE, mode, "the press after the last step turns it off")
    }

    @Test
    fun `the plain hint style has no staircase to walk`() {
        val next = PuzzleLogic.press(OverlayMode.HINT, OverlayMode.HINT, 0, HintStyle.REVEAL)
        assertEquals(OverlayMode.NONE, next.mode)
        assertEquals(1, hintOverlay(0).let {
            PuzzleLogic.overlay(puzzle, OverlayMode.HINT, HintStyle.REVEAL, hintDepth = 0).digits.size
        })
    }

    @Test
    fun `pressing another layer switches to it rather than deepening the hint`() {
        val next = PuzzleLogic.press(OverlayMode.HINT, OverlayMode.CHECK, 2, HintStyle.EXPLAIN)
        assertEquals(OverlayMode.CHECK, next.mode)
        assertEquals(0, next.hintDepth)
    }

    @Test
    fun `the walkthrough fills the board in as it goes, and points at the current move`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        assertTrue(route.steps.size > 3)

        fun at(step: Int) =
            PuzzleLogic.overlay(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
                walkthrough = route, lessonStep = step)

        assertTrue(at(0).digits.size <= at(3).digits.size, "the board should fill in, not empty")
        for ((index, drawn) in at(route.steps.size - 1).digits) {
            assertEquals(solution[index].digit, drawn.digit, "step teaches the wrong digit")
            assertEquals(OverlayRole.SOLUTION, drawn.role)
        }
        // The square the current step is about is singled out, and it is one being placed.
        val focus = assertNotNull(at(0).focus)
        assertTrue(!puzzle[focus].isFilled)
    }

    @Test
    fun `the outlook names the hardest technique rather than only counting steps`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val outlook = assertNotNull(PuzzleLogic.outlook(route))
        assertTrue(outlook.contains("steps"), outlook)
        assertTrue(outlook.contains("naked single"), outlook)
        assertNull(PuzzleLogic.outlook(null))
    }

    @Test
    fun `a walked step explains itself and says how to find the next one unaided`() {
        val route = assertNotNull(TechniqueSolver.walkthrough(puzzle))
        val said = assertNotNull(
            PuzzleLogic.guidance(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
                walkthrough = route, lessonStep = 0)
        )
        val technique = assertNotNull(Techniques.byName(route.steps[0].technique))
        assertEquals(route.steps[0].explanation, said.body)
        assertEquals(technique.howTo, said.howTo, "a step should carry the technique's how-to")
        assertTrue(assertNotNull(said.effect).isNotBlank(), "a step should say what it does")

        // The name is not printed in the pane any more, because the key beside the
        // photograph carries it - next to the colour of the squares it is talking about.
        assertFalse(said.body.startsWith(technique.name), said.body)
        assertEquals(
            technique.name,
            PuzzleLogic.evidenceLabel(puzzle, OverlayMode.LESSON, HintStyle.EXPLAIN,
                walkthrough = route, lessonStep = 0),
        )
    }

    @Test
    fun `the key does not name the technique before the hint has offered it`() {
        // The first rung highlights the box precisely so as not to name the technique. A
        // key that named it there would hand over the tread the user has not pressed for.
        assertEquals(
            "Box",
            PuzzleLogic.evidenceLabel(puzzle, OverlayMode.HINT, HintStyle.EXPLAIN, hintDepth = 0),
        )
        for (depth in 1 until PuzzleLogic.HINT_DEPTHS) {
            val named = PuzzleLogic.evidenceLabel(
                puzzle, OverlayMode.HINT, HintStyle.EXPLAIN, hintDepth = depth,
            )
            assertNotNull(Techniques.byName(assertNotNull(named)), "rung $depth named no technique")
        }
        // Nothing is highlighted in the plain style, so there is nothing to name.
        assertNull(PuzzleLogic.evidenceLabel(puzzle, OverlayMode.HINT, HintStyle.REVEAL))
        assertNull(PuzzleLogic.evidenceLabel(puzzle, OverlayMode.CHECK, HintStyle.EXPLAIN))
    }
}
