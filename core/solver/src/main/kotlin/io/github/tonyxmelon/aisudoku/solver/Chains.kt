package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/** Every open square holding exactly two candidates. The raw material of every chain here. */
internal fun SolverState.bivalue(): List<Int> =
    (0 until Coordinates.CELL_COUNT).filter { valueAt(it) == null && candidatesAt(it).size == 2 }

/**
 * Two squares holding the same pair, joined by a see-saw on one of its digits.
 *
 * The two ends hold {A,B} each and cannot see each other. Somewhere between them is a
 * unit where B has only two homes, one seen by each end. Whichever of those two holds B,
 * the end that sees it cannot - so that end is A. One of the ends is therefore A, and
 * nothing that sees both of them can be.
 *
 * The shortest of the chain techniques and the one worth learning first: four squares, no
 * bookkeeping, and it turns up constantly.
 */
object WWing : Technique {

    override val name = "W-wing"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "Two squares holding the same pair, linked by a see-saw on one of " +
        "its digits, force the other digit into one of them."

    override val howTo = "Find two squares with the same two candidates, say {3,7}, that " +
        "cannot see each other - they are usually nowhere near each other.\n\nNow look for " +
        "a row, column or box where 7 has only two possible squares, one of which is seen " +
        "by the first of your pair and the other by the second. That is the link.\n\nOne of " +
        "those two squares is the 7. Whichever it is, the square of your pair that sees it " +
        "cannot be 7, so it must be 3. You do not know which end is the 3, only that one of " +
        "them is - and that is enough to rule 3 out of every square that sees both ends.\n\n" +
        "The hard part is the link, so hunt for it the other way round: mark the units " +
        "where a digit has only two homes first, then look for a matching pair either side."

    override fun findAll(state: SolverState): List<Deduction> {
        val pairs = state.bivalue()
        val out = mutableListOf<Deduction>()

        for (ends in combinations(pairs, 2)) {
            val (first, second) = ends
            if (second in Coordinates.peers[first]) continue
            if (state.candidatesAt(first).bits != state.candidatesAt(second).bits) continue

            val (a, b) = state.candidatesAt(first).digits()
            for ((linked, forced) in listOf(b to a, a to b)) {
                val link = Coordinates.units.firstOrNull { unit ->
                    val places = state.strongPlaces(unit, linked) ?: return@firstOrNull false
                    if (first in places || second in places) return@firstOrNull false
                    val seesFirst = places.count { it in Coordinates.peers[first] }
                    val seesSecond = places.count { it in Coordinates.peers[second] }
                    seesFirst == 1 && seesSecond == 1 &&
                        places.none { it in Coordinates.peers[first] && it in Coordinates.peers[second] }
                } ?: continue

                val places = state.placesFor(link, linked)
                val targets = bothSeen(first, second)
                    .filter { state.valueAt(it) == null && forced in state.candidatesAt(it) }
                    .toSet() - places.toSet()
                if (targets.isEmpty()) continue

                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "${square(first)} and ${square(second)} both hold only " +
                        "$a and $b. In this ${unitName(link)}, $linked has just two homes, " +
                        "one seen by each of them - so one of those two is $linked, and the " +
                        "end that sees it must be $forced. Either way one end is $forced, " +
                        "which rules $forced out of everything that sees them both.",
                    supportingCells = setOf(first, second) + places,
                    digit = forced,
                    fromCells = targets,
                )
            }
        }
        return out
    }
}

/**
 * A chain of squares that all hold the same two candidates.
 *
 * Each square sees the next, so they alternate: A, B, A, B along the chain. Two squares an
 * odd number of links apart therefore hold different digits between them - one is A and
 * the other B - so anything that sees both of them can be neither.
 *
 * A special case of an XY-chain, kept separate because it is the one people can actually
 * find on paper: every square in it looks identical, so the chain stands out.
 */
object RemotePairs : Technique {

    override val name = "Remote pairs"
    override val difficulty = Difficulty.VERY_HARD

    /** Four squares is the shortest chain whose ends are an odd number of links apart. */
    private const val SHORTEST = 4

    /** Long enough for any chain worth drawing, short enough to stay a search rather than a hunt. */
    private const val LONGEST = 10

    override val rule = "In a chain of squares that all hold the same two candidates, the " +
        "two ends of an odd-length chain hold different digits."

    override val howTo = "Look for squares holding the same two candidates - say {4,9} - " +
        "that form a chain, each one seeing the next. Three is not enough; you need at " +
        "least four.\n\nColour them alternately as you go. Because each square sees the " +
        "next, no two neighbours can hold the same digit, so the chain reads 4, 9, 4, 9 " +
        "the whole way along - or 9, 4, 9, 4, and you cannot tell which.\n\nEither way, two " +
        "squares of opposite colours hold different digits between them: one is the 4 and " +
        "one is the 9. So anything that can see both of them can be neither, and loses both " +
        "candidates at once.\n\nThe pattern is easy to spot precisely because every square " +
        "in it looks the same. Follow the identical pairs around the grid and see where " +
        "they touch."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        val pairs = state.bivalue().groupBy { state.candidatesAt(it).bits }

        for ((bits, cells) in pairs) {
            if (cells.size < SHORTEST) continue
            val digits = CandidateSet(bits).digits()

            for (chain in walks(cells)) {
                val ends = listOf(chain.first(), chain.last())
                val targets = bothSeen(ends[0], ends[1])
                    .filter { it !in chain && state.valueAt(it) == null }
                    .toSet()

                for (digit in digits) {
                    val losing = targets.filter { digit in state.candidatesAt(it) }.toSet()
                    if (losing.isEmpty()) continue

                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "These ${chain.size} squares all hold only " +
                            "${digits[0]} and ${digits[1]}, and each sees the next, so they " +
                            "alternate the whole way along. ${square(ends[0])} and " +
                            "${square(ends[1])} are an odd number of links apart and so hold " +
                            "different digits between them - which rules $digit out of " +
                            "anything that sees them both.",
                        supportingCells = chain.toSet(),
                        digit = digit,
                        fromCells = losing,
                    )
                }
            }
        }
        return out
    }

    /**
     * The shortest chain between each pair of squares an odd number of links apart.
     *
     * Odd is the whole point: an even number of links puts the same digit at both ends and
     * proves nothing. Shortest, because a chain is something a person has to follow, and
     * every longer one between the same two ends says exactly the same thing.
     *
     * Breadth-first rather than every path depth-first. Walking every path through eight
     * mutually visible squares is thousands of chains, all but a handful of them longer
     * restatements of each other.
     */
    private fun walks(cells: List<Int>): List<List<Int>> {
        val found = mutableListOf<List<Int>>()

        for (start in cells) {
            val cameFrom = HashMap<Int, Int>()
            val depth = hashMapOf(start to 0)
            val queue = ArrayDeque(listOf(start))

            while (queue.isNotEmpty()) {
                val at = queue.removeFirst()
                val steps = depth.getValue(at)
                if (steps + 1 >= LONGEST) continue

                for (next in cells) {
                    if (next in depth || next !in Coordinates.peers[at]) continue
                    depth[next] = steps + 1
                    cameFrom[next] = at
                    queue += next

                    // An odd number of links, and long enough to say something a naked
                    // pair in one unit does not already say.
                    if ((steps + 1) % 2 == 1 && steps + 2 >= SHORTEST) {
                        val chain = mutableListOf(next)
                        while (chain.last() != start) chain += cameFrom.getValue(chain.last())
                        found += chain.reversed()
                    }
                }
            }
        }
        return found
    }
}

/**
 * A chain of two-candidate squares, each link ruling the next digit in.
 *
 * Every square on the chain holds exactly two digits and sees the next. Enter the first
 * square assuming it is *not* its outer digit, and it must be the other one; that rules
 * the same digit out of the next square, which must then be its other one; and so on. If
 * the far end comes out as the same digit the chain started with, then either the first
 * square is that digit or the last one is - so nothing seeing both ends can be.
 *
 * This is the general form that a W-wing, a remote pair and a Y-wing are all special cases
 * of, and it is the last thing to try before giving up and assuming a digit outright.
 */
object XyChain : Technique {

    override val name = "XY-chain"
    override val difficulty = Difficulty.VERY_HARD

    /** Longer than this is a forcing chain wearing a hat, and no easier to follow. */
    private const val LONGEST = 7

    override val rule = "A chain of two-candidate squares that begins and ends on the same " +
        "digit puts that digit in one end or the other."

    override val howTo = "Work only with squares that have exactly two candidates left.\n\n" +
        "Start at one, say {2,6}, and suppose it is not the 2 - then it is the 6. Move to a " +
        "square it can see that also has a 6, say {6,4}: that one cannot be the 6, so it is " +
        "the 4. Move to a square that one sees holding a 4, and carry on, each step forced " +
        "by the one before.\n\nIf you reach a square whose other digit is 2 - the digit you " +
        "started by ruling out - you are done. Either the first square was a 2 after all, or " +
        "the chain ran and the last square is a 2. One end or the other holds it, so " +
        "anything seeing both ends cannot.\n\nKeep the chains short. Four or five squares is " +
        "findable with a pencil; past that you are doing bookkeeping, and a forcing chain " +
        "is the honest name for it."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        val cells = state.bivalue()

        for (start in cells) {
            for (digit in state.candidatesAt(start).digits()) {
                // Assume `start` is not `digit`, so it holds its other candidate, and see
                // where being forced along the chain leads.
                val other = state.candidatesAt(start).minus(digit).digits().single()
                walk(state, cells, listOf(start), other, digit, out)
            }
        }
        return out
    }

    private fun walk(
        state: SolverState,
        cells: List<Int>,
        path: List<Int>,
        holding: Int,
        wanted: Int,
        out: MutableList<Deduction>,
    ) {
        val at = path.last()

        // The chain has come back to the digit it started by ruling out: one end or the
        // other holds it.
        if (path.size >= 3 && holding == wanted) {
            val ends = listOf(path.first(), at)
            val targets = bothSeen(ends[0], ends[1])
                .filter {
                    it !in path && state.valueAt(it) == null && wanted in state.candidatesAt(it)
                }
                .toSet()
            if (targets.isNotEmpty()) {
                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "Follow this chain of two-candidate squares: if " +
                        "${square(ends[0])} is not $wanted then each square along it is " +
                        "forced by the one before, and ${square(ends[1])} ends up $wanted. " +
                        "So one end or the other holds $wanted, which rules it out of " +
                        "every square that sees them both.",
                    supportingCells = path.toSet(),
                    digit = wanted,
                    fromCells = targets,
                )
            }
            return
        }

        if (path.size >= LONGEST) return
        for (next in cells) {
            if (next in path || next !in Coordinates.peers[at]) continue
            val candidates = state.candidatesAt(next)
            if (holding !in candidates) continue
            // `at` holds `holding`, so `next` cannot - which leaves it its other digit.
            walk(state, cells, path + next, candidates.minus(holding).digits().single(), wanted, out)
        }
    }
}
