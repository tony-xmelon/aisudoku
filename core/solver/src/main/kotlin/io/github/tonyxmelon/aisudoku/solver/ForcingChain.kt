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

    /**
     * How many squares a drawn trail may have.
     *
     * Set where it covers every chain on the hardest puzzle in the test set. A trail of
     * fifteen squares is busy, but the alternative is what the app used to do: highlight
     * one square and assert that following it through the grid leads somewhere impossible.
     * A busy picture can be studied. An assertion cannot.
     */
    private const val MOST_LINKS = 16

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

                // The traced chain first, because it is the one that can be shown. Only
                // if it finds nothing is it worth running the full propagation, which
                // refutes more but explains nothing.
                val chain = trace(state, index, digit)
                if (chain == null && !refuted(state, index, digit)) continue

                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = explain(digit, chain),
                    supportingCells = chain?.links?.mapTo(mutableSetOf()) { it.index }
                        ?: setOf(index),
                    digit = digit,
                    fromCells = setOf(index),
                    chain = chain,
                )
            }
        }
        return out
    }

    private fun explain(digit: Int, chain: Chain?): String = when {
        chain == null ->
            "Suppose this square were $digit. Following that through the grid leaves some " +
                "square with no digit at all, which cannot happen - so it is not $digit."

        chain.missing != null ->
            "Suppose this square were $digit. Follow the arrows: each square is forced by " +
                "the one it points from - either that digit is the only one it can still " +
                "hold, or that square is the only place the digit can still go. The " +
                "${chain.deadEndUnit ?: "unit"} in red is then left with nowhere at all to " +
                "put ${chain.missing}, which cannot happen - so it is not $digit."

        else ->
            "Suppose this square were $digit. Follow the arrows: each square is forced by " +
                "the one it points from - either that digit is the only one it can still " +
                "hold, or that square is the only place the digit can still go. The square " +
                "in red is then left with no digit at all, which cannot happen - so it is " +
                "not $digit."
    }

    /**
     * The same argument as [refuted], but keeping the working.
     *
     * Propagates by hand, by the two rules the fast propagation uses - a square down to
     * one digit must hold it, and a digit with one home left in a unit must go there -
     * recording for every candidate it strikes out which placement struck it. When it hits
     * a wall, those records give the placements the wall actually rests on, and their
     * ancestors: the argument, with nothing in it that the conclusion does not need.
     *
     * That closure is a tree rather than a line, and the first version of this drew the
     * line instead - the single path back from whatever hit the wall. It looked like an
     * argument and was not one: the wall leaned on side branches too, and replaying only
     * the path did not reach it. The test that replays every trail is what says so.
     *
     * Null when no contradiction is reached, and null when the tree comes out too big to
     * read. Neither means the digit is safe: [refuted] propagates over every digit at once
     * and may still refute it, only without a picture.
     *
     * Sound on a state whose candidate sets are wider than the truth, which the technique
     * solver's are: extra candidates only make a contradiction harder to reach, never
     * easier, so a trail that ends at a wall really does end at one.
     */
    private fun trace(start: SolverState, from: Int, digit: Int): Chain? {
        if (digit !in start.candidatesAt(from)) return null

        val state = start.copy()
        val forced = HashMap<Int, Int>()
        val cause = HashMap<Int, Int>()
        val order = HashMap<Int, Int>()
        // Which placement struck each candidate out. Keyed square-then-digit.
        val struck = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()

        fun mark(square: Int, gone: Int, by: Int) = struck.putIfAbsent(square * 10 + gone, by)

        forced[from] = digit
        order[from] = 0
        queue += from

        while (queue.isNotEmpty()) {
            val at = queue.removeFirst()
            val value = forced[at] ?: continue

            // Something took this digit away after the square was booked for it. The trail
            // has lost its footing rather than found a wall, so it is dropped: claiming a
            // contradiction here would be claiming one that was never reached.
            if (value !in state.candidatesAt(at)) return null

            // Fixing a square throws away the other digits it was holding, and each of
            // those may have been one of the last places its digit could go. Leaving that
            // out was why two chains in five reached no wall this way and had to be stated
            // without a picture: the fast propagation eliminates every other digit from an
            // assigned square and follows each one, and this now does the same.
            val wiped = state.candidatesAt(at).minus(value).digits()
            for (other in wiped) mark(at, other, at)
            state.fixOnly(at, value)

            for (other in wiped) {
                for (unit in Coordinates.unitsOf[at]) {
                    val places = unit.filter { other in state.candidatesAt(it) }
                    when (places.size) {
                        0 -> return chain(
                            forced, cause, order, from,
                            roots(unit, other, start, struck), unit.toSet(), other, null,
                        )

                        1 -> {
                            val only = places[0]
                            if (only !in forced && state.candidatesAt(only).size > 1) {
                                forced[only] = other
                                cause[only] = at
                                order[only] = order.size
                                queue += only
                            }
                        }
                    }
                }
            }

            for (peer in Coordinates.peers[at]) {
                val before = state.candidatesAt(peer)
                if (value !in before) continue

                val survived = state.removeCandidate(peer, value)
                mark(peer, value, at)

                if (!survived) {
                    // Nothing left in the peer at all. What the wall rests on is every
                    // placement that took one of the digits this square started with.
                    val emptied = start.candidatesAt(peer).digits()
                        .mapNotNull { struck[peer * 10 + it] }
                    return chain(forced, cause, order, from, emptied, setOf(peer), null, at)
                }

                // Two candidates before, one after: this square is forced too, by `at`.
                if (before.size == 2 && peer !in forced) {
                    state.candidatesAt(peer).single?.let {
                        forced[peer] = it
                        cause[peer] = at
                        order[peer] = order.size
                        queue += peer
                    }
                }

                // And the digit just removed may now have one home left in a unit, or none
                // at all. This is the rule that does most of the work: without it, ten
                // chains in eleven reached no wall worth drawing.
                for (unit in Coordinates.unitsOf[peer]) {
                    val places = unit.filter { value in state.candidatesAt(it) }
                    when (places.size) {
                        0 -> return chain(
                            forced, cause, order, from,
                            roots(unit, value, start, struck), unit.toSet(), value, null,
                        )

                        1 -> {
                            val only = places[0]
                            if (only !in forced && state.candidatesAt(only).size > 1) {
                                forced[only] = value
                                cause[only] = at
                                order[only] = order.size
                                queue += only
                            }
                        }
                    }
                }
            }
        }
        return null
    }

    /** Which placements struck [digit] out of every square of [unit] that could hold it. */
    private fun roots(
        unit: List<Int>,
        digit: Int,
        start: SolverState,
        struck: Map<Int, Int>,
    ): List<Int> = unit
        .filter { digit in start.candidatesAt(it) }
        .mapNotNull { struck[it * 10 + digit] }

    /**
     * The placements the wall rests on, their ancestors, and nothing else.
     *
     * Ordered as they were forced, so the assumption comes first and every square's parent
     * is already on the trail by the time it is drawn.
     */
    private fun chain(
        forced: Map<Int, Int>,
        cause: Map<Int, Int>,
        order: Map<Int, Int>,
        from: Int,
        roots: List<Int>,
        deadEnd: Set<Int>,
        missing: Int?,
        deadEndFrom: Int?,
    ): Chain? {
        val needed = mutableSetOf(from)
        val stack = ArrayDeque(roots)
        while (stack.isNotEmpty()) {
            val at = stack.removeLast()
            if (!needed.add(at)) continue
            cause[at]?.let { stack += it }
        }
        if (needed.size > MOST_LINKS) return null

        val links = needed
            .sortedBy { order[it] ?: 0 }
            .mapNotNull { i -> forced[i]?.let { ChainLink(i, it, cause[i]) } }

        return if (links.isEmpty()) null
        else Chain(links, deadEnd, missing, deadEndFrom?.takeIf { it in needed })
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
