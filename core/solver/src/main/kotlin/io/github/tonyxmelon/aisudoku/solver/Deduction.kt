package io.github.tonyxmelon.aisudoku.solver

/** Ranks techniques from easiest to hardest. Also grades puzzles: see [TechniqueSolver]. */
enum class Difficulty { EASY, MEDIUM, HARD, VERY_HARD }

/**
 * One step of human reasoning.
 *
 * [supportingCells] are the cells a person would point at to justify the step. The
 * overlay highlights them, so they must be the actual evidence and not merely related.
 */
sealed interface Deduction {
    val technique: String
    val difficulty: Difficulty
    val explanation: String
    val supportingCells: Set<Int>

    /** A digit belongs in a cell. */
    data class Placement(
        override val technique: String,
        override val difficulty: Difficulty,
        override val explanation: String,
        override val supportingCells: Set<Int>,
        val index: Int,
        val digit: Int,
    ) : Deduction

    /** A digit can be ruled out of some cells, without placing anything yet. */
    data class Elimination(
        override val technique: String,
        override val difficulty: Difficulty,
        override val explanation: String,
        override val supportingCells: Set<Int>,
        val digit: Int,
        val fromCells: Set<Int>,
    ) : Deduction {
        init {
            require(fromCells.isNotEmpty()) {
                "an elimination that removes nothing is not a deduction"
            }
        }
    }
}
