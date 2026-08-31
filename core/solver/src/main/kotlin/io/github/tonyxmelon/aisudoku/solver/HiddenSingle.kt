package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A digit with only one possible home in a unit belongs there, whatever else that cell
 * might also have accepted.
 */
object HiddenSingle : Technique {

    override val name = "Hidden single"
    override val difficulty = Difficulty.MEDIUM

    override val rule =
        "A digit with only one place left in a row, column or box belongs in that place."

    override val howTo = "Take one digit and one unit at a time - say, where can a 4 go " +
        "in this box? Cross off every square in the unit that already has a digit, or that " +
        "sees a 4 in its own row or column. If one square is left, the 4 goes there.\n\n" +
        "The trap is that you cannot see this by looking at the square: it may happily " +
        "accept five other digits too, so nothing about it looks forced. Look at the digit " +
        "instead of the square, and it appears immediately."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for ((unitIndex, unit) in Coordinates.units.withIndex()) {
            for (digit in 1..9) {
                if (unit.any { state.valueAt(it) == digit }) continue  // already placed here

                val places = unit.filter { digit in state.candidatesAt(it) && state.valueAt(it) == null }
                if (places.size != 1) continue

                val index = places[0]
                if (state.isReported(index)) continue

                out += Deduction.Placement(
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
        return out
    }

    /** Units are stored as nine rows, then nine columns, then nine boxes. */
    private fun unitName(unitIndex: Int): String = when {
        unitIndex < 9 -> "row"
        unitIndex < 18 -> "column"
        else -> "box"
    }
}
