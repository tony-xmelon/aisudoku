package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** The verdict on a user's handwritten answers. */
sealed interface AnswerCheck {

    /**
     * Every guess judged against the one true solution.
     *
     * There is no third state. The puzzle has exactly one solution, so a guess either
     * matches it or does not.
     */
    data class Checked(
        val solution: Grid,
        val correct: Set<Int>,
        val incorrect: Set<Int>,
    ) : AnswerCheck

    /**
     * The givens do not define a single puzzle, so no guess can be judged. In practice
     * this means recognition misread a given, and the caller should say so rather than
     * marking the user wrong.
     */
    data object NotCheckable : AnswerCheck
}

/** Judges handwritten guesses against the solution implied by the givens. */
object AnswerChecker {

    fun check(grid: Grid): AnswerCheck {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution
            ?: return AnswerCheck.NotCheckable

        val correct = mutableSetOf<Int>()
        val incorrect = mutableSetOf<Int>()

        for (i in 0 until Coordinates.CELL_COUNT) {
            val cell = grid[i]
            if (cell.source != CellSource.GUESS) continue
            if (cell.digit == solution[i].digit) correct += i else incorrect += i
        }

        return AnswerCheck.Checked(solution, correct, incorrect)
    }
}
