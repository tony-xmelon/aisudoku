package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/** What the solver made of a puzzle. */
sealed interface SolveResult {

    /** Exactly one solution. A properly set puzzle always lands here. */
    data class Unique(val solution: Grid) : SolveResult

    /** The givens contradict, or no completion exists. */
    data object None : SolveResult

    /**
     * At least two solutions. Both are carried because the cells where they disagree
     * are exactly the under-determined ones, which is useful diagnostic information.
     */
    data class Multiple(val first: Grid, val second: Grid) : SolveResult {
        val ambiguousCells: Set<Int>
            get() = (0 until Coordinates.CELL_COUNT)
                .filter { first[it].digit != second[it].digit }
                .toSet()
    }
}

/**
 * Solves from the GIVENS of a grid, ignoring guesses.
 *
 * Constraint propagation does most of the work; search only picks up what propagation
 * cannot settle. The search always branches on the cell with the fewest candidates
 * remaining, which keeps the tree narrow — without it, hard puzzles take minutes.
 *
 * Search stops after the second solution, since "more than one" is all any caller needs.
 */
object Solver {

    fun solve(grid: Grid): SolveResult {
        val state = SolverState.from(grid) ?: return SolveResult.None
        val found = mutableListOf<Grid>()
        search(state, found)
        return when (found.size) {
            0 -> SolveResult.None
            1 -> SolveResult.Unique(found[0])
            else -> SolveResult.Multiple(found[0], found[1])
        }
    }

    /** True when the puzzle has exactly one solution. */
    fun hasUniqueSolution(grid: Grid): Boolean = solve(grid) is SolveResult.Unique

    /** Depth-first search. Returns true once enough solutions have been collected to stop. */
    private fun search(state: SolverState, found: MutableList<Grid>): Boolean {
        if (state.isSolved) {
            found += state.toGrid()
            return found.size >= 2
        }

        val branchCell = mostConstrainedCell(state) ?: return false

        for (digit in state.candidatesAt(branchCell).digits()) {
            val attempt = state.copy()
            if (attempt.assign(branchCell, digit)) {
                if (search(attempt, found)) return true
            }
        }
        return false
    }

    /** The unsolved cell with the fewest candidates, or null when none are left. */
    private fun mostConstrainedCell(state: SolverState): Int? {
        var best: Int? = null
        var bestSize = 10
        for (i in 0 until Coordinates.CELL_COUNT) {
            val size = state.candidatesAt(i).size
            if (size in 2 until bestSize) {
                best = i
                bestSize = size
                if (size == 2) break  // cannot do better among unsolved cells
            }
        }
        return best
    }
}
