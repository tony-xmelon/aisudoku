package io.github.tonyxmelon.aisudoku.solver

/**
 * One square on a forcing chain: the digit it is forced to hold, and what forced it.
 *
 * [from] is null for the assumption the chain begins with, and otherwise names the square
 * whose placement left this one no choice. It is what an arrow is drawn along.
 */
data class ChainLink(val index: Int, val digit: Int, val from: Int? = null)

/**
 * An assumption, what it forces, and the wall it runs into.
 *
 * A chain is the one argument in this app whose *order* is the argument. Every other
 * technique points at a handful of squares to be taken in at once; this one says "this,
 * therefore this, therefore this - which is impossible". Drawn as an unordered set of
 * highlighted squares, as it was, it is not an argument at all.
 */
data class Chain(
    /** The assumption first, then each square it forces, in the order they follow. */
    val links: List<ChainLink>,
    /**
     * Where it breaks: one square that can hold nothing, or a whole unit with nowhere
     * left to put [missing].
     */
    val deadEnd: Set<Int>,
    /** The digit with nowhere to go, when a unit rather than a single square is the wall. */
    val missing: Int? = null,
    /** Which square emptied the dead end, when the dead end is a single square. */
    val deadEndFrom: Int? = null,
    /**
     * The squares in the dead end that could have held [missing] before the chain ran.
     *
     * Without these the picture makes a claim it does not show: a whole unit in red and a
     * sentence saying the digit has nowhere left to go, with nothing to say where it could
     * have gone or what took each of those places away. These are the squares to mark.
     */
    val blocked: Set<Int> = emptySet(),
) {
    /** What kind of unit the dead end is, when it is a unit. */
    val deadEndUnit: String?
        get() = when {
            deadEnd.size < 2 -> null
            deadEnd.all { it / 9 == deadEnd.first() / 9 } -> "row"
            deadEnd.all { it % 9 == deadEnd.first() % 9 } -> "column"
            else -> "box"
        }
}

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
        /**
         * The trail that proves it, when the proof is a trail.
         *
         * Null for every technique that recognises a pattern: there the evidence is a
         * handful of squares taken in at once and order means nothing. Only a forcing
         * chain has one.
         */
        val chain: Chain? = null,
    ) : Deduction {
        init {
            require(fromCells.isNotEmpty()) {
                "an elimination that removes nothing is not a deduction"
            }
        }
    }
}
