package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid

/**
 * The fewest printed digits that have to be wrong, when a puzzle has no solution at all.
 *
 * "No solution" is a true statement and a useless one. Every puzzle in this app was read
 * off a photograph, so a puzzle that cannot be solved almost always means a digit was
 * misread rather than that the newspaper printed a broken puzzle - and the useful answer
 * is not "this cannot be solved" but "these two squares are the ones to look at".
 *
 * So: the smallest set of givens which, taken away, leaves a puzzle that can be solved.
 * Taking a digit away rather than changing it is the same question asked more cheaply -
 * if the rest of the grid has a solution without it, then that solution says what the
 * square should have held, so the cell can always be changed to something that works.
 *
 * Only givens are considered, because [Solver] solves from the givens and ignores what
 * the user has written, so nothing written can be what makes it unsolvable.
 */
object MinimalFix {

    /**
     * How many digits this will consider changing before giving up.
     *
     * Two, because the search is every subset of that size and the cost climbs as a power:
     * one is at most eighty-one solves, two is a few thousand, three is hundreds of
     * thousands and this runs on a phone while somebody waits. Two is also what the fault
     * looks like in practice - a misread digit, occasionally a misread pair - and a page
     * needing three changed is one to photograph again rather than to repair.
     */
    const val MOST_CHANGES = 2

    /**
     * The squares to look at, fewest first, or null when none of that size will do.
     *
     * Null is not the same as "the puzzle is fine": it means no set this small fixes it,
     * which is worth saying differently from naming the squares.
     */
    fun find(grid: Grid, most: Int = MOST_CHANGES): Set<Int>? {
        if (Solver.solve(grid) !is SolveResult.None) return emptySet()

        val givens = (0 until Coordinates.CELL_COUNT).filter { grid[it].digit != null }
        if (givens.isEmpty()) return null

        // Anything sharing a row, column or box with an identical digit is already a
        // contradiction on its own, and where such a pair exists the fault is one of them.
        // Trying those first is not only faster - it is the answer a person would give.
        val clashing = clashes(grid)
        val ordered = givens.sortedByDescending { it in clashing }

        for (size in 1..most.coerceAtMost(2)) {
            val found = ofSize(grid, ordered, size)
            if (found != null) return found
        }
        return null
    }

    /** Every square that shares a unit with another holding the same digit. */
    fun clashes(grid: Grid): Set<Int> {
        val out = mutableSetOf<Int>()
        for (i in 0 until Coordinates.CELL_COUNT) {
            val digit = grid[i].digit ?: continue
            for (j in Coordinates.peers[i]) {
                if (grid[j].digit == digit) {
                    out += i
                    out += j
                }
            }
        }
        return out
    }

    private fun ofSize(grid: Grid, candidates: List<Int>, size: Int): Set<Int>? {
        if (size == 1) {
            for (cell in candidates) {
                if (solvableWithout(grid, listOf(cell))) return setOf(cell)
            }
            return null
        }
        for (a in candidates.indices) {
            for (b in a + 1 until candidates.size) {
                val pair = listOf(candidates[a], candidates[b])
                if (solvableWithout(grid, pair)) return pair.toSet()
            }
        }
        return null
    }

    private fun solvableWithout(grid: Grid, cells: List<Int>): Boolean {
        var attempt = grid
        for (cell in cells) attempt = attempt.with(cell, Cell.Empty)
        return Solver.solve(attempt) !is SolveResult.None
    }
}
