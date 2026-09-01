package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * N cells in one unit sharing exactly N candidates between them.
 *
 * Those N digits have to fill those N cells in some order, so no other cell in the unit
 * can take any of them. Which cell gets which digit is not known and does not matter -
 * the elimination is what the technique is for.
 *
 * Reported one digit at a time: a naked pair usually clears two different digits out of
 * several cells, and "this pair rules 4 out of these three squares" is a step someone can
 * follow, where "this pair rules several things out of several squares" is not.
 */
class NakedSubset(private val size: Int) : Technique {

    override val name = subsetName("Naked", size)

    override val difficulty = if (size == 2) Difficulty.MEDIUM else Difficulty.HARD

    override val rule = "If $size squares in one row, column or box share exactly $size " +
        "candidates between them, those digits belong to those squares and to no others in " +
        "the unit."

    override val howTo = "Look along one row, column or box for squares with only a few " +
        "candidates left. Take $size of them and pool their candidates. If the pool has " +
        "exactly $size digits in it, those $size digits are spoken for: they have to go in " +
        "those $size squares in some order, even though you cannot yet say which goes " +
        "where.\n\nThat is enough. Every other square in the unit can give those digits " +
        "up, and it is usually one of those squares that then becomes a single.\n\nThe " +
        "squares do not all need the same candidates - {2,7}, {2,9} and {7,9} is a " +
        "perfectly good triple, and is the one people miss."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for (unit in Coordinates.units) {
            val open = state.openCells(unit).filter { state.candidatesAt(it).size in 2..size }
            if (open.size <= size) continue

            for (group in combinations(open, size)) {
                val shared = state.candidateUnion(group)
                if (shared.size != size) continue

                val others = unit.filter { it !in group && state.valueAt(it) == null }
                for (digit in shared.digits()) {
                    val targets = others.filter { digit in state.candidatesAt(it) }.toSet()
                    if (targets.isEmpty()) continue

                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "These $size squares hold only ${shared.digits().joinToString(", ")} " +
                            "between them, so those digits fill them in some order. " +
                            "That rules $digit out of the rest of this ${unitName(unit)}.",
                        supportingCells = group.toSet(),
                        digit = digit,
                        fromCells = targets,
                    )
                }
            }
        }
        return out
    }
}

/**
 * N digits in one unit that between them can only go in N cells.
 *
 * Those cells are therefore reserved for those digits, and every other candidate can be
 * struck out of them. The mirror of a naked subset, and much harder to see: the cells
 * look unremarkable, because they are still carrying all their other candidates. It is
 * the digits that are cornered, not the squares.
 */
class HiddenSubset(private val size: Int) : Technique {

    override val name = subsetName("Hidden", size)

    override val difficulty = if (size == 2) Difficulty.MEDIUM else Difficulty.HARD

    override val rule = "If $size digits in one row, column or box can only go in the same " +
        "$size squares, those squares can hold nothing else."

    override val howTo = "Work digit by digit rather than square by square. For one unit, " +
        "write down where each missing digit could still go. Then look for $size digits " +
        "whose lists between them cover only $size squares.\n\nThose $size digits have to " +
        "fill those $size squares, so everything else in them can be crossed out - which " +
        "often turns one of them into a naked single or a naked pair on the spot.\n\nThis " +
        "is the one people find hardest, because there is nothing to see when you look at " +
        "the squares: they still have plenty of candidates. The pattern is in the digits."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for (unit in Coordinates.units) {
            val missing = (1..9).filter { digit ->
                unit.none { state.valueAt(it) == digit } && state.placesFor(unit, digit).isNotEmpty()
            }
            if (missing.size <= size) continue

            for (group in combinations(missing, size)) {
                val homes = group.flatMap { state.placesFor(unit, it) }.toSet()
                if (homes.size != size) continue
                // Each digit must still fit somewhere, or this is not a subset at all.
                if (group.any { state.placesFor(unit, it).isEmpty() }) continue

                val reserved = CandidateSet(group.fold(0) { bits, d -> bits or CandidateSet.of(d).bits })
                for (digit in 1..9) {
                    if (digit in reserved) continue
                    val targets = homes.filter { digit in state.candidatesAt(it) }.toSet()
                    if (targets.isEmpty()) continue

                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "In this ${unitName(unit)}, ${group.joinToString(", ")} can " +
                            "only go in these $size squares, so those squares are spoken for. " +
                            "That rules $digit out of them.",
                        supportingCells = homes,
                        digit = digit,
                        fromCells = targets,
                    )
                }
            }
        }
        return out
    }
}

val NakedPair = NakedSubset(2)
val NakedTriple = NakedSubset(3)
val NakedQuad = NakedSubset(4)
val HiddenPair = HiddenSubset(2)
val HiddenTriple = HiddenSubset(3)
val HiddenQuad = HiddenSubset(4)

/** Pair, triple, quad. Nothing about a subset changes with its size except its name. */
private fun subsetName(kind: String, size: Int): String = when (size) {
    2 -> "$kind pair"
    3 -> "$kind triple"
    else -> "$kind quad"
}

/** Rows come first in [Coordinates.units], then columns, then boxes. */
internal fun unitName(unit: List<Int>): String = when {
    unit.all { Coordinates.rowOf(it) == Coordinates.rowOf(unit[0]) } -> "row"
    unit.all { Coordinates.colOf(it) == Coordinates.colOf(unit[0]) } -> "column"
    else -> "box"
}
