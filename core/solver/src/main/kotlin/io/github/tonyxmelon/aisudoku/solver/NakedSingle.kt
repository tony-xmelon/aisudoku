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

    /**
     * One neighbour for each digit this square cannot be.
     *
     * The evidence for a naked single is not the square itself. It is the eight squares
     * around it that have used up the other eight digits, and pointing at those is the
     * entire lesson - pointing at the square only says "look here", which the user can
     * already see.
     *
     * There is always exactly one such neighbour per digit: a candidate is only ever
     * struck out by a digit being placed where this square can see it.
     */
    private fun eliminators(state: SolverState, index: Int, answer: Int): Set<Int> {
        val byDigit = mutableMapOf<Int, Int>()
        for (peer in Coordinates.peers[index]) {
            val value = state.valueAt(peer) ?: continue
            if (value != answer) byDigit.putIfAbsent(value, peer)
        }
        return byDigit.values.toSet()
    }

    override fun findAll(state: SolverState): List<Deduction> =
        (0 until Coordinates.CELL_COUNT).mapNotNull { index ->
            val digit = state.candidatesAt(index).single ?: return@mapNotNull null
            if (state.isReported(index)) return@mapNotNull null
            Deduction.Placement(
                technique = name,
                difficulty = difficulty,
                explanation = "Only $digit can go here - every other digit already appears " +
                    "in this row, column or box.",
                supportingCells = eliminators(state, index, digit),
                index = index,
                digit = digit,
            )
        }
}
