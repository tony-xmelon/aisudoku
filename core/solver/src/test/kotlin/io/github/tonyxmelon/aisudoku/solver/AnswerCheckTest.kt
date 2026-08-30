package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnswerCheckTest {

    private val truth = assertIs<SolveResult.Unique>(Solver.solve(Puzzles.EASY)).solution

    @Test
    fun `a correct guess is marked correct`() {
        val firstEmpty = (0 until 81).first { !Puzzles.EASY[it].isFilled }
        val grid = Puzzles.EASY.with(firstEmpty, Cell.guess(truth[firstEmpty].digit!!))

        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(grid))
        assertEquals(setOf(firstEmpty), result.correct)
        assertTrue(result.incorrect.isEmpty())
    }

    @Test
    fun `a wrong guess is marked wrong`() {
        val firstEmpty = (0 until 81).first { !Puzzles.EASY[it].isFilled }
        val wrong = (1..9).first { it != truth[firstEmpty].digit }
        val grid = Puzzles.EASY.with(firstEmpty, Cell.guess(wrong))

        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(grid))
        assertEquals(setOf(firstEmpty), result.incorrect)
        assertTrue(result.correct.isEmpty())
    }

    @Test
    fun `givens are never judged`() {
        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(Puzzles.EASY))
        assertTrue(result.correct.isEmpty())
        assertTrue(result.incorrect.isEmpty())
    }

    @Test
    fun `a puzzle without a unique solution cannot be checked`() {
        assertIs<AnswerCheck.NotCheckable>(AnswerChecker.check(Puzzles.AMBIGUOUS))
        assertIs<AnswerCheck.NotCheckable>(AnswerChecker.check(Puzzles.CONTRADICTORY))
    }

    @Test
    fun `the solution is offered alongside the verdict`() {
        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(Puzzles.EASY))
        assertEquals(truth.toGivensString(), result.solution.toGivensString())
        assertNull(result.solution.cells.firstOrNull { !it.isFilled })
    }

    @Test
    fun `a wrong guess does not stop the rest being judged`() {
        val empties = (0 until 81).filter { !Puzzles.EASY[it].isFilled }.take(4)
        var grid = Puzzles.EASY
        empties.forEachIndexed { n, i ->
            val digit = if (n == 0) (1..9).first { it != truth[i].digit } else truth[i].digit!!
            grid = grid.with(i, Cell.guess(digit))
        }
        val result = assertIs<AnswerCheck.Checked>(AnswerChecker.check(grid))
        assertEquals(setOf(empties[0]), result.incorrect)
        assertEquals(empties.drop(1).toSet(), result.correct)
    }
}
