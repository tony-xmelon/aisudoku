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

    override val rule = "A square with only one digit left must hold that digit."

    override val howTo = "Pick an empty square and run through 1 to 9, crossing off " +
        "anything that already appears in its row, its column or its box. If exactly one " +
        "digit survives, it goes there.\n\nThis is the technique every other one exists " +
        "to set up. Everything harder is a way of getting some square down to a single " +
        "candidate, so when a harder technique pays off, look here next."

    override fun findAll(state: SolverState): List<Deduction> =
        (0 until Coordinates.CELL_COUNT).mapNotNull { index ->
            val digit = state.candidatesAt(index).single ?: return@mapNotNull null
            if (state.isReported(index)) return@mapNotNull null
            Deduction.Placement(
                technique = name,
                difficulty = difficulty,
                explanation = "Only $digit can go here - every other digit already appears " +
                    "in this row, column or box.",
                supportingCells = setOf(index),
                index = index,
                digit = digit,
            )
        }
}
