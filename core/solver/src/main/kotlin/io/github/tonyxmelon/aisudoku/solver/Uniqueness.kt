package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * Four squares in two rows, two columns and two boxes cannot all hold the same two digits.
 *
 * If they did, the two digits could be swapped diagonally and the grid would still be
 * legal - so the puzzle would have two answers. A properly set puzzle has one, so the
 * corner that has anything else left must use it.
 *
 * This is the only technique here that reasons about the puzzle rather than the position.
 * Every other one would still hold if the grid had a dozen answers; this one is an appeal
 * to the setter, and it is worth knowing which kind of argument you are making. The app
 * checks that a puzzle has exactly one answer before it offers any of this, so the appeal
 * is safe here - but on a grid someone has been filling in wrongly it would not be.
 */
object UniqueRectangle : Technique {

    override val name = "Unique rectangle"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "Four squares in two rows, two columns and two boxes cannot all " +
        "hold the same two candidates, or the puzzle would have two answers."

    override val howTo = "Look for three squares holding the same two candidates - say " +
        "{1,6} - that sit at three corners of a rectangle. The rectangle has to lie in " +
        "exactly two boxes: two rows and two columns crossing two boxes, not four.\n\nNow " +
        "look at the fourth corner. If it held only 1 and 6 as well, the puzzle would have " +
        "two answers - you could swap the 1s and 6s round the rectangle and everything else " +
        "would still work. A properly set puzzle has one answer, so the fourth corner must " +
        "be using something else: its other candidates stay, and 1 and 6 go.\n\nThis is the " +
        "one argument in the book that is about the setter rather than the grid. It is " +
        "sound on a published puzzle and worthless on one you have already filled in " +
        "wrongly, because then there may be no answer at all to be unique."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        val pairs = state.bivalue()

        for (floor in combinations(pairs, 2)) {
            val (first, second) = floor
            if (state.candidatesAt(first).bits != state.candidatesAt(second).bits) continue

            // The two squares that complete the rectangle from these two.
            val corners = rectangle(first, second) ?: continue
            val (third, fourth) = corners
            if (state.valueAt(third) != null || state.valueAt(fourth) != null) continue

            val pair = state.candidatesAt(first)
            // Exactly two boxes, or the swap is not forced to stay legal.
            if (setOf(first, second, third, fourth).map(Coordinates::boxOf).toSet().size != 2) continue

            // Three corners carry the pair and nothing else; the fourth carries it and more.
            for ((corner, other) in listOf(third to fourth, fourth to third)) {
                if (state.candidatesAt(other).bits != pair.bits) continue
                val extra = state.candidatesAt(corner)
                if (extra.bits == pair.bits) continue
                if (pair.digits().any { it !in extra }) continue

                val targets = setOf(corner)
                for (digit in pair.digits()) {
                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "These four squares make a rectangle across two boxes, " +
                            "and three of them hold only ${pair.digits().joinToString(" and ")}. " +
                            "If ${square(corner)} held only those two as well, the digits " +
                            "could be swapped round the rectangle and the puzzle would have " +
                            "two answers. It has one, so ${square(corner)} must be using " +
                            "something else - and $digit goes.",
                        supportingCells = setOf(first, second, third, fourth),
                        digit = digit,
                        fromCells = targets,
                    )
                }
            }
        }
        return out
    }

    /** The other two corners of the rectangle two squares sit on, when they fix one. */
    private fun rectangle(first: Int, second: Int): Pair<Int, Int>? {
        val (topRow, bottomRow) = Coordinates.rowOf(first) to Coordinates.rowOf(second)
        val (leftColumn, rightColumn) = Coordinates.colOf(first) to Coordinates.colOf(second)

        // Only a diagonal pair fixes the other two corners. Two squares sharing a row
        // could be completed by any of the other eight rows, so there is no rectangle to
        // speak of - and every three corners of a rectangle contain a diagonal, so
        // nothing is missed by looking only at these.
        if (topRow == bottomRow || leftColumn == rightColumn) return null
        return Pair(topRow * 9 + rightColumn, bottomRow * 9 + leftColumn)
    }
}
