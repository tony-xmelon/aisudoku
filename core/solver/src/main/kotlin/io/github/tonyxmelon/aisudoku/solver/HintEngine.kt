package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** A suggestion for the user's next move. */
sealed interface Hint {

    /** The cell to fill and what goes in it. */
    val index: Int
    val digit: Int

    /** Straight to the answer. */
    data class Reveal(override val index: Int, override val digit: Int) : Hint

    /** The answer, with the reasoning that gets there and the cells that prove it. */
    data class Explained(
        val technique: String,
        val explanation: String,
        val supportingCells: Set<Int>,
        val difficulty: Difficulty,
        override val index: Int,
        override val digit: Int,
    ) : Hint
}

/** Produces the next hint for a puzzle, or null when there is nothing useful to say. */
interface HintEngine {
    fun nextHint(grid: Grid): Hint?
}

/**
 * The puzzle as the user has actually got it: the printed givens, plus every answer they
 * have written that agrees with the solution.
 *
 * A hint has to start from where the user is. Reasoning from the givens alone points at
 * cells they filled in ten minutes ago and tells them a digit they can already see -
 * which is exactly what the first version did.
 *
 * Answers that are wrong are left out rather than trusted: reasoning from a wrong digit
 * produces wrong advice. Checking answers is a separate question with its own answer.
 */
internal fun progressGrid(grid: Grid, solution: Grid): Grid {
    var out = grid.givensOnly()
    for (i in 0 until Coordinates.CELL_COUNT) {
        val cell = grid[i]
        val digit = cell.digit
        if (cell.source == CellSource.GUESS && digit != null && digit == solution[i].digit) {
            out = out.with(i, Cell.given(digit))
        }
    }
    return out
}

/** Cells with nothing written in them at all - the only place a hint is worth putting. */
internal fun openCells(grid: Grid): List<Int> =
    (0 until Coordinates.CELL_COUNT).filter { !grid[it].isFilled }

/**
 * Names a cell and its digit, choosing the most constrained cell the user has left
 * empty, so the hint lands somewhere they could plausibly have worked out next.
 */
object RevealHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        val state = SolverState.candidatesOnly(progressGrid(grid, solution)) ?: return null

        val target = openCells(grid).minByOrNull { state.candidatesAt(it).size } ?: return null
        return Hint.Reveal(target, solution[target].digit ?: return null)
    }
}

/**
 * Explains the next step in human terms.
 *
 * Deductions are walked in order and applied until one lands on a cell the user has not
 * filled. Without that the engine happily explains a step the user took long ago, or one
 * that only contradicts an answer they got wrong - both true, neither a hint.
 *
 * Falls back to [RevealHintEngine] when the puzzle needs a technique this engine does not
 * know. A user who asked for help must always get help, even if the app cannot dress it
 * up as reasoning.
 */
object ExplainedHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        val open = openCells(grid).toSet()
        if (open.isEmpty()) return null

        val state = SolverState.candidatesOnly(progressGrid(grid, solution)) ?: return null

        // Bounded by construction: every deduction strictly reduces the candidates left
        // on the board, so this cannot run longer than there are candidates.
        repeat(Coordinates.CELL_COUNT * 9) {
            val deduction = TechniqueSolver.nextDeduction(state)
                ?: return RevealHintEngine.nextHint(grid)

            if (deduction is Deduction.Placement && deduction.index in open) {
                return Hint.Explained(
                    technique = deduction.technique,
                    explanation = deduction.explanation,
                    supportingCells = deduction.supportingCells,
                    difficulty = deduction.difficulty,
                    index = deduction.index,
                    digit = deduction.digit,
                )
            }
            if (!TechniqueSolver.apply(state, deduction)) return RevealHintEngine.nextHint(grid)
        }
        return RevealHintEngine.nextHint(grid)
    }
}
