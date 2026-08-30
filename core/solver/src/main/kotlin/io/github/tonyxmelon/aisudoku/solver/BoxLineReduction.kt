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

    override fun find(state: SolverState): Deduction? {
        for (line in 0 until 9) {
            findIn(state, Coordinates.rowIndices[line], "row")?.let { return it }
            findIn(state, Coordinates.colIndices[line], "column")?.let { return it }
        }
        return null
    }

    private fun findIn(state: SolverState, lineCells: List<Int>, lineName: String): Deduction.Elimination? {
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

            return Deduction.Elimination(
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
        return null
    }
}
