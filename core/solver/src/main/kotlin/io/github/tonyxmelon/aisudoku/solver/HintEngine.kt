package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** A suggestion for the user's next move. */
sealed interface Hint {

    /** Straight to the answer. */
    data class Reveal(val index: Int, val digit: Int) : Hint

    /**
     * The reasoning, without the answer. [supportingCells] are highlighted in the
     * overlay; [answer] is withheld until the user asks a second time.
     */
    data class Explained(
        val technique: String,
        val explanation: String,
        val supportingCells: Set<Int>,
        val difficulty: Difficulty,
        /** The digit, withheld until the user asks again. Null when the step only eliminates. */
        val answer: Reveal?,
    ) : Hint
}

/** Produces the next hint for a puzzle, or null when there is nothing useful to say. */
interface HintEngine {
    fun nextHint(grid: Grid): Hint?
}

/**
 * Names a cell and its digit, choosing the most constrained empty cell so the hint
 * lands somewhere the user could plausibly have worked out next.
 *
 * Candidates come from the givens alone, so a wrong guess by the user cannot steer the
 * hint somewhere useless.
 */
object RevealHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        val state = SolverState.candidatesOnly(grid) ?: return null

        val target = (0 until Coordinates.CELL_COUNT)
            .filter { !grid[it].isFilled }
            .minByOrNull { state.candidatesAt(it).size }
            ?: return null

        val digit = solution[target].digit ?: return null
        return Hint.Reveal(target, digit)
    }
}

/**
 * Explains the next step in human terms.
 *
 * Falls back to [RevealHintEngine] when the puzzle needs a technique this engine does
 * not know. A user who asked for help must always get help, even if the app cannot
 * dress it up as reasoning.
 */
object ExplainedHintEngine : HintEngine {

    override fun nextHint(grid: Grid): Hint? {
        if (Solver.solve(grid) !is SolveResult.Unique) return null
        val state = SolverState.candidatesOnly(grid) ?: return null

        val deduction = TechniqueSolver.nextDeduction(state)
            ?: return RevealHintEngine.nextHint(grid)

        val answer = when (deduction) {
            is Deduction.Placement -> Hint.Reveal(deduction.index, deduction.digit)
            // An elimination has no digit to reveal, so fall back to a real answer.
            is Deduction.Elimination -> RevealHintEngine.nextHint(grid) as? Hint.Reveal
        }

        return Hint.Explained(
            technique = deduction.technique,
            explanation = deduction.explanation,
            supportingCells = deduction.supportingCells,
            difficulty = deduction.difficulty,
            answer = answer,
        )
    }
}
