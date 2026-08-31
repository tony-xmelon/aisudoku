package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
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
    /** What to name when telling the user what this puzzle is going to ask of them. */
    val hardestTechnique: String? = null,
    /**
     * How many squares on the route no technique could justify, and had to be settled by
     * trying the candidates out instead.
     *
     * Zero on almost every puzzle. Not zero is not a failure - it is what makes a puzzle
     * hard, and saying so is more use than stopping.
     */
    val triedOut: Int = 0,
    /**
     * True for a route, where each step builds on the last, and false for a browse, where
     * the steps are alternatives all available from the same position.
     *
     * The difference shows: a route fills the board in as it is walked, and a browse must
     * not, or the second example is being shown in a position the first one created.
     */
    val cumulative: Boolean = true,
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
        val state = SolverState.candidatesOnly(progressGrid(grid, solution)) ?: return null

        val steps = mutableListOf<Deduction>()
        var triedOut = 0

        // Reason as far as reasoning goes; when it stops, settle one square by trying its
        // candidates, and reason on from there. The route therefore always reaches the
        // end. Stopping halfway with "this app has run out of techniques" is honest but
        // useless to somebody holding an unfinished puzzle.
        //
        // The loop is bounded by the grid: every step either fills a square or removes a
        // candidate, and there are only so many of each.
        // Run until there is nothing left to say, rather than until the grid is solved.
        // The last placement of a run often collapses several squares to a single
        // candidate at once, and stopping there leaves those squares filled in but never
        // explained - six of them, on the easy puzzle.
        var guard = 0
        while (guard++ <= Coordinates.CELL_COUNT * 10) {
            val reasoned = nextDeduction(state)
            val step = reasoned ?: triedOut(state, solution)?.also { triedOut++ } ?: break
            if (!apply(state, step)) break
            steps += step
        }

        // Trying candidates out is not a technique and should not be reported as the
        // hardest one needed - it is what happens when there is no technique at all.
        val hardest = steps.filter { it.technique != TRIED_OUT }.maxByOrNull { it.difficulty.ordinal }
        return Walkthrough(
            steps = steps,
            hardest = hardest?.difficulty ?: Difficulty.EASY,
            finishes = state.isSolved,
            hardestTechnique = hardest?.technique,
            triedOut = triedOut,
        )
    }

    /** The name given to a square that no technique here could justify. */
    const val TRIED_OUT = "Tried out"

    /**
     * Settling one square when nothing can be reasoned about it.
     *
     * Takes the square with the fewest candidates left, which is the one a person would
     * try first, and fills in the answer. The claim it makes is true and follows from
     * something the app has already established: the puzzle has exactly one solution, so
     * every other candidate for this square must lead to a dead end.
     *
     * It is deliberately named for what it is. Dressing a lookup up as a deduction would
     * teach the user a technique that does not exist.
     */
    private fun triedOut(state: SolverState, solution: Grid): Deduction? {
        val square = (0 until Coordinates.CELL_COUNT)
            .filter { state.valueAt(it) == null }
            .minByOrNull { state.candidatesAt(it).size } ?: return null
        val answer = solution[square].digit ?: return null

        val others = state.candidatesAt(square).digits().filter { it != answer }
        val rejected = when {
            others.isEmpty() -> ""
            others.size == 1 -> " ${others[0]} leads to a dead end."
            else -> " ${others.joinToString(", ")} each lead to a dead end."
        }
        return Deduction.Placement(
            technique = TRIED_OUT,
            difficulty = Difficulty.VERY_HARD,
            explanation = "No technique here can justify a move from this position, so this " +
                "square has to be settled by trying its candidates out - which is what the " +
                "app did to find the solution in the first place. Only $answer survives." +
                rejected,
            supportingCells = setOf(square),
            index = square,
            digit = answer,
        )
    }

    /**
     * Every place one technique applies in the position the user is in.
     *
     * These are alternatives, not a route: all of them are available at once, and taking
     * any one of them would change the others. That is exactly what makes them worth
     * browsing - seeing the same pattern four times in one grid is how it stops being a
     * definition and starts being something you can spot.
     */
    fun findings(grid: Grid, technique: Technique): Walkthrough? {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return null
        val state = SolverState.candidatesOnly(progressGrid(grid, solution)) ?: return null
        return Walkthrough(
            steps = technique.findAll(state),
            hardest = technique.difficulty,
            finishes = false,
            hardestTechnique = technique.name,
            cumulative = false,
        )
    }

    /** How many places each technique applies right now. For the strategy list. */
    fun findingCounts(grid: Grid): Map<String, Int> {
        val solution = (Solver.solve(grid) as? SolveResult.Unique)?.solution ?: return emptyMap()
        val state = SolverState.candidatesOnly(progressGrid(grid, solution)) ?: return emptyMap()
        return ALL_TECHNIQUES.associate { it.name to it.findAll(state).size }
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
