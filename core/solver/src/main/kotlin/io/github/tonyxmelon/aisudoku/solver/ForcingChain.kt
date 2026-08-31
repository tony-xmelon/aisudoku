package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * Suppose a square held a digit, follow it through, and find the grid breaking.
 *
 * Every technique before this one recognises a *pattern*. This one does not: it takes a
 * candidate, assumes it, and pushes the consequences out across the grid until either
 * nothing more follows or some square is left with no digit it can hold. A square with no
 * digit is impossible, so the assumption was wrong and the candidate can go.
 *
 * That makes it the technique of last resort, and the one that gets a hard puzzle moving
 * when the pattern-based ones have all run dry. It is also the honest name for what
 * people actually do at that point, rather than pretending they spotted a swordfish.
 *
 * Deliberately bounded. Every candidate of every square could be tried, and the search
 * behind each is a full propagation, so this only looks at squares that are nearly
 * decided already and stops once it has found enough to be useful. Those are the ones a
 * person would try first anyway.
 */
object ForcingChain : Technique {

    /** Only squares this close to being settled are worth assuming something about. */
    private const val MOST_CANDIDATES = 4

    /** Enough to make progress. Finding every one costs far more than it is worth. */
    private const val ENOUGH = 12

    override val name = "Forcing chain"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "If assuming a digit leads to a square with nothing it can hold, " +
        "that digit was wrong."

    override val howTo = "Find a square with only two or three candidates left - the fewer " +
        "the better, and the busier the row, column and box around it the better still.\n\n" +
        "Pick one of its candidates and pencil it in lightly. Now follow the consequences: " +
        "that digit is gone from everything the square can see, which may leave one of " +
        "those squares with a single candidate, which forces another, and so on. Keep " +
        "going.\n\nOne of two things happens. Either it peters out, and you have learned " +
        "nothing - rub it out and try the other candidate. Or you reach a square with no " +
        "candidates left at all, or a row with nowhere to put some digit. That is " +
        "impossible, so the digit you pencilled in was wrong, and you can rule it out for " +
        "good.\n\nThis is the one to reach for when everything else has dried up. It is " +
        "slower and it is bookkeeping rather than pattern-spotting, but it always applies, " +
        "and one elimination is usually enough to start the easy techniques working again."

    /**
     * The solver only ever wants the first, and each trial is a full propagation, so
     * stopping at one is the difference between a snappy tutor and a stalled one.
     */
    override fun find(state: SolverState): Deduction? = search(state, limit = 1).firstOrNull()

    override fun findAll(state: SolverState): List<Deduction> = search(state, ENOUGH)

    private fun search(state: SolverState, limit: Int): List<Deduction> {
        val out = mutableListOf<Deduction>()

        val worthTrying = (0 until Coordinates.CELL_COUNT)
            .filter { state.valueAt(it) == null }
            .map { it to state.candidatesAt(it) }
            .filter { (_, candidates) -> candidates.size in 2..MOST_CANDIDATES }
            .sortedBy { (_, candidates) -> candidates.size }

        for ((index, candidates) in worthTrying) {
            for (digit in candidates.digits()) {
                if (out.size >= limit) return out
                if (!refuted(state, index, digit)) continue

                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "Suppose this square were $digit. Following that through " +
                        "the grid leaves some square with no digit at all, which cannot " +
                        "happen - so it is not $digit.",
                    supportingCells = setOf(index),
                    digit = digit,
                    fromCells = setOf(index),
                )
            }
        }
        return out
    }

    /**
     * True when assuming [digit] at [index] breaks the grid.
     *
     * The trial runs on a copy, and on [SolverState.assign], which cascades - unlike the
     * non-propagating placement the technique solver uses to keep its steps explainable.
     * Here the cascade IS the argument.
     *
     * Sound on a state whose candidate sets are wider than the truth, which the technique
     * solver's are: extra candidates only make a contradiction harder to reach, never
     * easier, so anything this refutes really is refuted.
     */
    private fun refuted(state: SolverState, index: Int, digit: Int): Boolean =
        !state.copy().assign(index, digit)
}
