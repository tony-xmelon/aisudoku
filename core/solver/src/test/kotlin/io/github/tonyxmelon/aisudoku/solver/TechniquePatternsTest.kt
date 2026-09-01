package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The patterns the two test puzzles happen not to contain.
 *
 * The soundness sweep audits every technique against a known solution at every position
 * those puzzles pass through, which is worth far more than any hand-built case - but it
 * can only audit what fires. These five never did, and a technique that never fires is
 * untested code claiming to be a lesson. Each one gets the position it is for, built by
 * hand, so that at least its own pattern is known to work.
 */
class TechniquePatternsTest {

    private fun board(build: SolverState.() -> Unit): SolverState =
        assertNotNull(SolverState.from(Grid.Empty)).apply(build)

    /** Leaves [digit] as a candidate only in the squares named. */
    private fun SolverState.only(digit: Int, vararg keep: Int) {
        for (index in 0 until 81) if (index !in keep) removeCandidate(index, digit)
    }

    /** Cuts one square down to exactly these candidates. */
    private fun SolverState.holds(index: Int, vararg digits: Int) {
        for (digit in 1..9) if (digit !in digits) removeCandidate(index, digit)
    }

    private fun at(row: Int, column: Int) = row * 9 + column

    private fun onlyFor(found: List<Deduction>, digit: Int): Deduction.Elimination =
        assertIs(found.filterIsInstance<Deduction.Elimination>().first { it.digit == digit })

    /**
     * Two rows where 5 has two homes each, sharing column 0. One of the far ends holds it,
     * so r2c6 - which sees both - cannot.
     */
    @Test
    fun `a skyscraper rules the digit out of what sees both roofs`() {
        val state = board {
            only(5, at(0, 0), at(0, 4), at(4, 0), at(4, 5), at(1, 5))
        }
        val found = Skyscraper.findAll(state)
        assertTrue(found.isNotEmpty(), "the skyscraper was not seen at all")

        val step = onlyFor(found, 5)
        assertEquals(setOf(at(1, 5)), step.fromCells)
        assertTrue(at(0, 4) in step.supportingCells && at(4, 5) in step.supportingCells)
    }

    /**
     * A row and a column with two homes each for 3, whose inner ends share box 5. One of
     * the outer ends holds it, and r9c2 sees both.
     */
    @Test
    fun `a two-string kite rules the digit out of what sees both ends`() {
        val state = board {
            only(3, at(3, 1), at(3, 7), at(5, 6), at(8, 6), at(8, 1))
        }
        val found = TwoStringKite.findAll(state)
        assertTrue(found.isNotEmpty(), "the kite was not seen at all")

        val step = onlyFor(found, 3)
        assertEquals(setOf(at(8, 1)), step.fromCells)
    }

    /**
     * Two squares holding {1,2} that cannot see each other, with 2 confined to two homes
     * in row 9 - one seen by each. So one of them is the 1.
     */
    @Test
    fun `a w-wing forces the other digit into one of its two ends`() {
        val state = board {
            only(2, at(0, 0), at(4, 4), at(8, 0), at(8, 4))
            holds(at(0, 0), 1, 2)
            holds(at(4, 4), 1, 2)
        }
        val found = WWing.findAll(state)
        assertTrue(found.isNotEmpty(), "the w-wing was not seen at all")

        val step = onlyFor(found, 1)
        assertEquals(setOf(at(0, 4), at(4, 0)), step.fromCells)
    }

    /**
     * Four squares holding {4,9}, each seeing the next and none seeing any other. The ends
     * are three links apart, so they hold different digits between them and anything
     * seeing both loses both.
     *
     * The corners are chosen to be far apart on purpose. The obvious little chain in one
     * corner of the grid is not a chain at all - its first and third squares share a box,
     * which is a short cut that makes the two ends an even number of links apart, and then
     * they hold the same digit and nothing follows.
     */
    @Test
    fun `remote pairs strip both digits from what sees both ends`() {
        val state = board {
            holds(at(0, 0), 4, 9)
            holds(at(0, 8), 4, 9)
            holds(at(8, 8), 4, 9)
            holds(at(8, 4), 4, 9)
        }
        val found = RemotePairs.findAll(state)
        assertTrue(found.isNotEmpty(), "the remote pair chain was not seen at all")

        for (digit in listOf(4, 9)) {
            val step = onlyFor(found, digit)
            assertTrue(
                at(0, 4) in step.fromCells && at(8, 0) in step.fromCells,
                "both squares that see the two ends should lose $digit: ${step.fromCells}",
            )
        }
    }

    /**
     * Three corners of a two-box rectangle holding only {5,7}. The fourth cannot, or the
     * puzzle would have two answers.
     */
    @Test
    fun `a unique rectangle clears the pair from its fourth corner`() {
        val state = board {
            holds(at(0, 0), 5, 7)
            holds(at(0, 3), 5, 7)
            holds(at(1, 0), 5, 7)
            holds(at(1, 3), 5, 7, 9)
        }
        val found = UniqueRectangle.findAll(state)
        assertTrue(found.isNotEmpty(), "the rectangle was not seen at all")

        for (digit in listOf(5, 7)) {
            assertEquals(setOf(at(1, 3)), onlyFor(found, digit).fromCells)
        }
    }

    /**
     * The one that would be a real bug: a rectangle spread over four boxes proves nothing,
     * because the swap it rules out would not keep the boxes legal.
     */
    @Test
    fun `a rectangle across four boxes is not a unique rectangle`() {
        val state = board {
            holds(at(0, 0), 5, 7)
            holds(at(0, 4), 5, 7)
            holds(at(4, 0), 5, 7)
            holds(at(4, 4), 5, 7, 9)
        }
        assertEquals(emptyList(), UniqueRectangle.findAll(state))
    }
}
