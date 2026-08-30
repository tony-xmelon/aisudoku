package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * If every home for a digit inside a box shares one row or column, the digit lies on
 * that line within the box, so it can be eliminated from the rest of the line.
 */
object PointingPair : Technique {

    override val name = "Pointing pair"
    override val difficulty = Difficulty.HARD

    override fun find(state: SolverState): Deduction? {
        for (box in 0 until 9) {
            val boxCells = Coordinates.boxIndices[box]
            for (digit in 1..9) {
                if (boxCells.any { state.valueAt(it) == digit }) continue

                val places = boxCells.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                if (places.size !in 2..3) continue

                val row = Coordinates.rowOf(places[0])
                if (places.all { Coordinates.rowOf(it) == row }) {
                    eliminationAlong(state, places, digit, Coordinates.rowIndices[row], "row")
                        ?.let { return it }
                }

                val col = Coordinates.colOf(places[0])
                if (places.all { Coordinates.colOf(it) == col }) {
                    eliminationAlong(state, places, digit, Coordinates.colIndices[col], "column")
                        ?.let { return it }
                }
            }
        }
        return null
    }

    private fun eliminationAlong(
        state: SolverState,
        places: List<Int>,
        digit: Int,
        lineCells: List<Int>,
        lineName: String,
    ): Deduction.Elimination? {
        val targets = lineCells
            .filter { it !in places }
            .filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
            .toSet()
        if (targets.isEmpty()) return null

        return Deduction.Elimination(
            technique = name,
            difficulty = difficulty,
            explanation = "Inside this box, $digit can only go in this $lineName. " +
                "So $digit is somewhere here, and can be ruled out of the rest of the $lineName.",
            supportingCells = places.toSet(),
            digit = digit,
            fromCells = targets,
        )
    }
}
