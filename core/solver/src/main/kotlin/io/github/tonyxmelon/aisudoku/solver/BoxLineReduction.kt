package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * If every home for a digit in a row or column falls inside one box, the digit is in
 * that box, so it can be eliminated from the box's cells off that line.
 *
 * The mirror of [PointingPair], which reasons from the box outwards.
 */
object BoxLineReduction : Technique {

    override val name = "Box line reduction"
    override val difficulty = Difficulty.HARD

    override val rule = "If a digit's only homes along a line all sit inside one box, " +
        "that digit can be ruled out of the rest of that box."

    override val howTo = "The pointing pair read backwards. Take a row or a column and a " +
        "digit that still needs a home in it. Mark every square in that line which could " +
        "take the digit. If all of them fall inside one box, the digit must be in that " +
        "box's share of the line - so it is nowhere else in the box, including the two " +
        "rows or columns of it you were not looking at.\n\nLike the pointing pair, this " +
        "places nothing on its own; it makes room for something that does."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for (line in 0 until 9) {
            out += findIn(state, Coordinates.rowIndices[line], "row")
            out += findIn(state, Coordinates.colIndices[line], "column")
        }
        return out
    }

    private fun findIn(
        state: SolverState,
        lineCells: List<Int>,
        lineName: String,
    ): List<Deduction.Elimination> {
        val out = mutableListOf<Deduction.Elimination>()
        for (digit in 1..9) {
            if (lineCells.any { state.valueAt(it) == digit }) continue

            val places = lineCells.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
            if (places.size !in 2..3) continue

            val box = Coordinates.boxOf(places[0])
            if (places.any { Coordinates.boxOf(it) != box }) continue

            val targets = Coordinates.boxIndices[box]
                .filter { it !in places }
                .filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                .toSet()
            if (targets.isEmpty()) continue

            out += Deduction.Elimination(
                technique = name,
                difficulty = difficulty,
                explanation = "In this $lineName, $digit can only go inside one box. " +
                    "So $digit is in that box on this $lineName, and can be ruled out of " +
                    "the rest of the box.",
                supportingCells = places.toSet(),
                digit = digit,
                fromCells = targets,
            )
        }
        return out
    }
}
