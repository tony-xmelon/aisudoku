package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A digit with only one possible home in a unit belongs there, whatever else that cell
 * might also have accepted.
 */
object HiddenSingle : Technique {

    override val name = "Hidden single"
    override val difficulty = Difficulty.MEDIUM

    override fun find(state: SolverState): Deduction? {
        for ((unitIndex, unit) in Coordinates.units.withIndex()) {
            for (digit in 1..9) {
                if (unit.any { state.valueAt(it) == digit }) continue  // already placed here

                val places = unit.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                if (places.size != 1) continue

                val index = places[0]
                if (state.isReported(index)) continue

                return Deduction.Placement(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "$digit has to go here: it is the only cell in this " +
                        "${unitName(unitIndex)} that can still take a $digit.",
                    supportingCells = unit.toSet(),
                    index = index,
                    digit = digit,
                )
            }
        }
        return null
    }

    /** Units are stored as nine rows, then nine columns, then nine boxes. */
    private fun unitName(unitIndex: Int): String = when {
        unitIndex < 9 -> "row"
        unitIndex < 18 -> "column"
        else -> "box"
    }
}
