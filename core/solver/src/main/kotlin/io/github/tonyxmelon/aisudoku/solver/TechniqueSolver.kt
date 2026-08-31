package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Grid

/** What reasoning alone made of a puzzle. */
sealed interface TechniqueOutcome {

    /** Finished without guessing. [difficulty] is the hardest technique required. */
    data class Solved(
        val solution: Grid,
        val steps: List<Deduction>,
        val difficulty: Difficulty,
    ) : TechniqueOutcome

    /** Ran out of techniques. [partial] is as far as reasoning got. */
    data class Stuck(
        val partial: Grid,
        val steps: List<Deduction>,
    ) : TechniqueOutcome

    /** The givens break the rules. */
    data object Invalid : TechniqueOutcome
}

/**
 * Solves the way a person does: apply the simplest technique that yields something,
 * repeat, and stop when nothing applies.
 *
 * This never guesses. A puzzle it cannot finish is a puzzle that needs a technique the
 * engine does not know, which is a normal and expected outcome — [Solver] handles those.
 *
 * Note it builds state with [SolverState.candidatesOnly] rather than [SolverState.from],
 * and applies steps with the non-propagating [SolverState.place]. Propagation would
 * settle whole regions of the grid at once, which is fast but leaves nothing to explain.
 * Every step here is one a person could have taken.
 */
/**
 * The whole route from where the user is to the answer, one human step at a time.
 *
 * This is the difference between a solver and something worth learning from: the answer
 * is one number, and the route is the thing that transfers.
 */
data class Walkthrough(
    val steps: List<Deduction>,
    /** The hardest technique the route needs. Also the honest grade for the puzzle. */
    val hardest: Difficulty,
    /**
     * False when reasoning runs out before the end. The route shown is still every step
     * that can be justified; what is left needs a technique this app does not know, and
     * saying so is better than pretending otherwise.
     */
    val finishes: Boolean,
) {
    val isEmpty: Boolean get() = steps.isEmpty()
}

object TechniqueSolver {

    /**
     * Every step from the user's current position, not from the printed givens.
     *
     * Reasoning from the givens alone would walk them through work they have already
     * done. Answers they got right are taken as read; answers they got wrong are left
     * out, because a route built on a wrong digit leads somewhere wrong.
     */
    fun walkthrough(grid: Grid): Walkthrough? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        return when (val outcome = solve(progressGrid(grid, solution))) {
            is TechniqueOutcome.Solved -> Walkthrough(outcome.steps, outcome.difficulty, true)

            is TechniqueOutcome.Stuck -> Walkthrough(
                outcome.steps,
                outcome.steps.maxOfOrNull { it.difficulty } ?: Difficulty.EASY,
                finishes = false,
            )

            TechniqueOutcome.Invalid -> null
        }
    }

    fun solve(grid: Grid): TechniqueOutcome {
        val state = SolverState.candidatesOnly(grid) ?: return TechniqueOutcome.Invalid
        val steps = mutableListOf<Deduction>()

        while (!state.isSolved) {
            val deduction = nextDeduction(state) ?: break
            if (!apply(state, deduction)) return TechniqueOutcome.Invalid
            steps += deduction
        }

        return if (state.isSolved) {
            TechniqueOutcome.Solved(
                solution = state.toGrid(),
                steps = steps,
                difficulty = steps.maxOfOrNull { it.difficulty } ?: Difficulty.EASY,
            )
        } else {
            TechniqueOutcome.Stuck(partial = state.toGrid(), steps = steps)
        }
    }

    /** The easiest available deduction, since a hint should offer the simplest route. */
    fun nextDeduction(state: SolverState): Deduction? =
        ALL_TECHNIQUES.firstNotNullOfOrNull { it.find(state) }

    /**
     * Applies a deduction to the state. Returns false if it produced a contradiction.
     *
     * Every step strictly reduces the total number of candidates on the board, which is
     * what guarantees the loop in [solve] terminates.
     */
    fun apply(state: SolverState, deduction: Deduction): Boolean = when (deduction) {
        is Deduction.Placement -> {
            state.markReported(deduction.index)
            state.place(deduction.index, deduction.digit)
        }

        is Deduction.Elimination ->
            deduction.fromCells.all { state.removeCandidate(it, deduction.digit) }
    }
}
