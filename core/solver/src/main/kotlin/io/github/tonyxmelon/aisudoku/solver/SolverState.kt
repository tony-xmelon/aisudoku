package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/**
 * A working grid of candidate sets, with constraint propagation.
 *
 * A cell is "solved" when its candidate set is down to a single digit, so there is no
 * separate value array to keep in step. Every mutating call returns `false` if it drove
 * the grid into a contradiction, at which point the state is spent and the caller must
 * fall back to a copy taken earlier. This is how [Solver] backtracks.
 */
class SolverState private constructor(private val candidates: IntArray) {

    /**
     * Cells already accounted for: the puzzle's givens, plus any cell offered as a hint.
     * Without this a naked single would keep re-reporting the givens forever.
     */
    private val reported = BooleanArray(Coordinates.CELL_COUNT)

    fun isReported(index: Int): Boolean = reported[index]

    fun markReported(index: Int) {
        reported[index] = true
    }

    fun candidatesAt(index: Int): CandidateSet = CandidateSet(candidates[index])

    /** The digit at [index], or null while more than one remains possible. */
    fun valueAt(index: Int): Int? = CandidateSet(candidates[index]).single

    val solvedCount: Int get() = candidates.count { CandidateSet(it).size == 1 }

    val isSolved: Boolean get() = candidates.all { CandidateSet(it).size == 1 }

    fun copy(): SolverState = SolverState(candidates.copyOf()).also {
        reported.copyInto(it.reported)
    }

    /** Fix [digit] in [index] by eliminating every other digit there. */
    fun assign(index: Int, digit: Int): Boolean {
        val others = CandidateSet(candidates[index]).minus(digit)
        for (other in others.digits()) {
            if (!eliminate(index, other)) return false
        }
        return true
    }

    /** Remove [digit] from [index] and propagate. Returns false on contradiction. */
    fun eliminate(index: Int, digit: Int): Boolean {
        val current = CandidateSet(candidates[index])
        if (digit !in current) return true  // already gone, nothing to propagate

        val reduced = current.minus(digit)
        candidates[index] = reduced.bits

        if (reduced.isEmpty) return false

        // (1) The cell is down to one digit, so no peer may hold it.
        reduced.single?.let { only ->
            for (peer in Coordinates.peers[index]) {
                if (!eliminate(peer, only)) return false
            }
        }

        // (2) The eliminated digit may now have only one home left in a unit.
        for (unit in Coordinates.unitsOf[index]) {
            val places = unit.filter { digit in CandidateSet(candidates[it]) }
            when (places.size) {
                0 -> return false
                1 -> if (!assign(places[0], digit)) return false
            }
        }
        return true
    }

    /**
     * Removes a candidate without propagating.
     *
     * Only for building test fixtures. Production code must use [eliminate] so the
     * consequences of a removal are followed through.
     */
    internal fun forceEliminate(index: Int, digit: Int) {
        candidates[index] = CandidateSet(candidates[index]).minus(digit).bits
    }

    /** Solved cells become guesses; unsolved cells stay empty. */
    fun toGrid(): Grid = Grid.of(
        (0 until Coordinates.CELL_COUNT).map { i ->
            valueAt(i)?.let { Cell.guess(it) } ?: Cell.Empty
        }
    )

    companion object {
        /**
         * Builds a state from the GIVENS of [grid]. Guesses are deliberately ignored —
         * the puzzle is defined by what was printed, and the whole point of solving is
         * to judge the guesses against it.
         *
         * Returns null when the givens already break the rules.
         */
        fun from(grid: Grid): SolverState? {
            val state = SolverState(IntArray(Coordinates.CELL_COUNT) { CandidateSet.ALL.bits })
            for (i in 0 until Coordinates.CELL_COUNT) {
                val cell = grid[i]
                if (cell.source == CellSource.GIVEN) {
                    val digit = cell.digit ?: continue
                    if (!state.assign(i, digit)) return null
                }
            }
            for (i in 0 until Coordinates.CELL_COUNT) {
                if (state.valueAt(i) != null) state.markReported(i)
            }
            return state
        }
    }
}
