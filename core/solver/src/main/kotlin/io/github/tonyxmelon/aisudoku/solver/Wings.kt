package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * A pivot of two candidates and two pincers, which between them force a digit.
 *
 * The pivot holds {A,B}. One pincer sees it and holds {A,C}, the other sees it and holds
 * {B,C}. Whichever way the pivot falls, one of the pincers is forced to C - so any square
 * that can see both pincers cannot be C.
 *
 * The chain is only three cells long, which is what makes it the first technique that
 * feels like reasoning rather than bookkeeping.
 */
object YWing : Technique {

    override val name = "Y-wing"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "A two-candidate pivot and two two-candidate pincers can force a " +
        "digit out of every square that sees both pincers."

    override val howTo = "Hunt for squares with exactly two candidates - a Y-wing is made " +
        "of three of them and nothing else.\n\nTake one as the pivot, say {2,5}. Look for a " +
        "square it can see holding {2,9}, and another it can see holding {5,9}. The pivot " +
        "is either 2 or 5. If it is 2, the second pincer must be 9; if it is 5, the first " +
        "must be 9. Either way a 9 appears in one of the pincers - you do not know which, " +
        "and you do not need to.\n\nSo no square that can see both pincers can be a 9. " +
        "Those squares are usually the two corners that complete the rectangle."

    override fun findAll(state: SolverState): List<Deduction> {
        val pairs = (0 until Coordinates.CELL_COUNT)
            .filter { state.valueAt(it) == null && state.candidatesAt(it).size == 2 }

        val out = mutableListOf<Deduction>()
        for (pivot in pairs) {
            val (a, b) = state.candidatesAt(pivot).digits()
            val seen = pairs.filter { it in Coordinates.peers[pivot] }

            for (first in seen) {
                val firstDigits = state.candidatesAt(first).digits()
                if (a !in firstDigits) continue
                val c = firstDigits.first { it != a }
                if (c == b) continue

                for (second in seen) {
                    if (second == first) continue
                    if (state.candidatesAt(second).digits().toSet() != setOf(b, c)) continue

                    val targets = (Coordinates.peers[first] intersect Coordinates.peers[second])
                        .filter {
                            it != pivot && state.valueAt(it) == null && c in state.candidatesAt(it)
                        }
                        .toSet()
                    if (targets.isEmpty()) continue

                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "The pivot here is $a or $b. Either way one of the other " +
                            "two squares is forced to $c, so no square that sees both of them " +
                            "can be $c.",
                        supportingCells = setOf(pivot, first, second),
                        digit = c,
                        fromCells = targets,
                    )
                }
            }
        }
        return out
    }
}

/**
 * A Y-wing whose pivot carries the forced digit too.
 *
 * The pivot holds {A,B,C} and the pincers {A,C} and {B,C}. One of the three is C
 * whichever way it falls, so a square that sees *all three* cannot be C. A weaker
 * conclusion than the Y-wing's, because it has to see one more square.
 */
object XyzWing : Technique {

    override val name = "XYZ-wing"
    override val difficulty = Difficulty.VERY_HARD

    override val rule = "A three-candidate pivot with two matching two-candidate pincers " +
        "forces a digit out of every square that sees all three."

    override val howTo = "The Y-wing with the pivot holding one candidate more. Look for a " +
        "square with exactly three candidates, say {1,4,7}, that can see two two-candidate " +
        "squares - one {1,7} and one {4,7}.\n\nNow all three of them can be 7, and one of " +
        "them must be. So a square has to see all three to be ruled out, not just the two " +
        "pincers - which in practice means it sits in the same box as the pivot and on a " +
        "line with a pincer.\n\nFewer eliminations than a Y-wing, and easier to miss for " +
        "the same reason: the pivot does not stand out."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        val pairs = (0 until Coordinates.CELL_COUNT)
            .filter { state.valueAt(it) == null && state.candidatesAt(it).size == 2 }

        for (pivot in 0 until Coordinates.CELL_COUNT) {
            if (state.valueAt(pivot) != null) continue
            val trio = state.candidatesAt(pivot)
            if (trio.size != 3) continue

            val seen = pairs.filter { it in Coordinates.peers[pivot] }
                .filter { state.candidatesAt(it).digits().all { d -> d in trio } }

            for (group in combinations(seen, 2)) {
                val (first, second) = group
                val shared = state.candidatesAt(first).digits()
                    .filter { it in state.candidatesAt(second) }
                if (shared.size != 1) continue
                val c = shared[0]
                // Together the pincers must cover all three of the pivot's candidates,
                // or the pivot is free to be something that breaks the argument.
                if (state.candidateUnion(group).bits != trio.bits) continue

                val targets = (Coordinates.peers[pivot] intersect Coordinates.peers[first]
                    intersect Coordinates.peers[second])
                    .filter { state.valueAt(it) == null && c in state.candidatesAt(it) }
                    .toSet()
                if (targets.isEmpty()) continue

                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "All three of these squares can be $c, and one of them has " +
                        "to be. So any square that sees all three cannot be $c.",
                    supportingCells = setOf(pivot, first, second),
                    digit = c,
                    fromCells = targets,
                )
            }
        }
        return out
    }
}
