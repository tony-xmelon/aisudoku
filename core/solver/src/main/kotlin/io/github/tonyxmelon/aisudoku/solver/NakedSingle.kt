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
     * Why the other eight digits cannot go here: a neighbour holding each, where there is
     * one, and the digits for which there is not.
     *
     * The evidence for a naked single is not the square itself. It is the squares around
     * it that have used the other digits up, and pointing at those is the entire lesson -
     * pointing at the square only says "look here", which the user can already see.
     *
     * [ruledOutEarlier] is the part this used to get wrong. The comment here read "there
     * is always exactly one such neighbour per digit: a candidate is only ever struck out
     * by a digit being placed where this square can see it", and that is true only until
     * some other technique has run. A pointing pair or an x-wing strikes candidates out
     * without placing anything, so a square can come down to one candidate with six
     * visible neighbours rather than eight - and the sentence "every other digit already
     * appears in this row, column or box" was then a plain untruth, in front of a
     * highlight that could not account for the missing two. Reported from the phone as
     * "why is it not a 1?", which was a fair question with no answer on the screen.
     */
    private data class Reasons(val cells: Set<Int>, val ruledOutEarlier: List<Int>)

    private fun reasons(state: SolverState, index: Int, answer: Int): Reasons {
        val byDigit = mutableMapOf<Int, Int>()
        for (peer in Coordinates.peers[index]) {
            val value = state.valueAt(peer) ?: continue
            if (value != answer) byDigit.putIfAbsent(value, peer)
        }
        return Reasons(byDigit.values.toSet(), ((1..9) - answer - byDigit.keys).sorted())
    }

    /** "1", or "1 and 6", or "1, 4 and 6". */
    private fun list(digits: List<Int>): String = when (digits.size) {
        1 -> "${digits[0]}"
        else -> digits.dropLast(1).joinToString(", ") + " and " + digits.last()
    }

    private val counts = listOf(
        "none", "one", "two", "three", "four", "five", "six", "seven", "eight",
    )

    override fun findAll(state: SolverState): List<Deduction> =
        (0 until Coordinates.CELL_COUNT).mapNotNull { index ->
            val digit = state.candidatesAt(index).single ?: return@mapNotNull null
            if (state.isReported(index)) return@mapNotNull null
            val why = reasons(state, index, digit)
            val gone = why.ruledOutEarlier
            Deduction.Placement(
                technique = name,
                difficulty = difficulty,
                // Says exactly what the highlight can and cannot account for, so the
                // digits with nothing to point at are named rather than left as a hole in
                // the argument.
                explanation = when {
                    gone.isEmpty() ->
                        "Only $digit can go here - every other digit already appears in " +
                            "this row, column or box."

                    why.cells.isEmpty() ->
                        "Only $digit can go here, though nothing around it shows why: all " +
                            "eight other digits were ruled out of this square by earlier " +
                            "reasoning rather than by being written in its row, column or box."

                    else ->
                        "Only $digit can go here. The highlighted squares use up " +
                            "${counts[8 - gone.size]} of the other eight digits; " +
                            "${list(gone)} ${if (gone.size == 1) "was" else "were"} ruled " +
                            "out of this square earlier, not by anything you can see around it."
                },
                supportingCells = why.cells,
                index = index,
                digit = digit,
            )
        }
}
