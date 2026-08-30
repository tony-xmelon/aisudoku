package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A cell with one candidate left must hold it.
 *
 * [SolverState] already treats a one-candidate cell as solved, so this technique reports
 * cells that propagation has settled but the user has not yet been told about.
 */
object NakedSingle : Technique {

    override val name = "Naked single"
    override val difficulty = Difficulty.EASY

    override fun find(state: SolverState): Deduction? {
        for (index in 0 until Coordinates.CELL_COUNT) {
            val digit = state.candidatesAt(index).single ?: continue
            if (state.isReported(index)) continue
            return Deduction.Placement(
                technique = name,
                difficulty = difficulty,
                explanation = "Only $digit can go here — every other digit already appears " +
                    "in this row, column or box.",
                supportingCells = setOf(index),
                index = index,
                digit = digit,
            )
        }
        return null
    }
}
