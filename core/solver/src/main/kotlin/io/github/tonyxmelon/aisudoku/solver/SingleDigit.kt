package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * True when [unit] has not been given [digit] yet.
 *
 * Worth stating every time. These states do not propagate - placing a digit does not
 * strike it from its neighbours' candidate lists - so a unit can already hold a digit and
 * still show stale candidates for it elsewhere. Any technique that counts such a unit as
 * still needing the digit will happily eliminate a true candidate somewhere else, which is
 * exactly how the swordfish went wrong once.
 */
internal fun SolverState.needs(unit: List<Int>, digit: Int): Boolean =
    unit.none { valueAt(it) == digit }

/** Where a digit can still go in a line, but only if the line still wants it. */
internal fun SolverState.strongPlaces(unit: List<Int>, digit: Int): List<Int>? {
    if (!needs(unit, digit)) return null
    val places = placesFor(unit, digit)
    return places.takeIf { it.size == 2 }
}

/** Every square that can see both of these two. */
internal fun bothSeen(first: Int, second: Int): Set<Int> =
    Coordinates.peers[first].intersect(Coordinates.peers[second]) - first - second

/** Row 4, column 7 - how a square is named in a sentence. */
internal fun square(index: Int): String = "r${index / 9 + 1}c${index % 9 + 1}"

/**
 * Two lines where a digit has only two homes each, sharing one crossing line.
 *
 * The two squares in the shared line cannot both hold the digit, so at least one of the
 * other two must - and anything seeing both of those is out. It is the simplest of the
 * single-digit patterns and the one to look for before reaching for anything longer.
 */
object Skyscraper : Technique {

    override val name = "Skyscraper"
    override val difficulty = Difficulty.HARD

    override val rule = "If a digit has two homes in each of two rows and they share a " +
        "column, one of the other two homes must hold it."

    override val howTo = "Pick a digit and look down the rows for the ones where it has " +
        "exactly two squares left. Take two such rows and see whether one of their squares " +
        "lands in the same column.\n\nIf it does, those two squares are in the same column " +
        "and cannot both hold the digit. So at least one of the far ends does - the two " +
        "squares at the other side. You do not know which, and you do not need to: any " +
        "square that can see both far ends can give the digit up.\n\nThen do it again with " +
        "columns instead of rows. The shape is a tall building with a short one beside it, " +
        "which is where the name comes from and how you spot it once you have seen a few."

    override fun findAll(state: SolverState): List<Deduction> =
        search(state, byRows = true) + search(state, byRows = false)

    private fun search(state: SolverState, byRows: Boolean): List<Deduction> {
        val lines = if (byRows) Coordinates.rowIndices else Coordinates.colIndices
        val word = if (byRows) "rows" else "columns"
        val across = if (byRows) "column" else "row"
        fun crossOf(index: Int) = if (byRows) Coordinates.colOf(index) else Coordinates.rowOf(index)

        val out = mutableListOf<Deduction>()
        for (digit in 1..9) {
            val pairs = lines.mapNotNull { state.strongPlaces(it, digit) }

            for (group in combinations(pairs, 2)) {
                val (first, second) = group
                if (first.toSet().intersect(second.toSet()).isNotEmpty()) continue

                // The base: one square from each line, sharing a crossing line.
                for (base in first) {
                    val partner = second.firstOrNull { crossOf(it) == crossOf(base) } ?: continue
                    val roofs = listOf(
                        first.first { it != base },
                        second.first { it != partner },
                    )
                    if (crossOf(roofs[0]) == crossOf(roofs[1])) continue

                    val targets = bothSeen(roofs[0], roofs[1])
                        .filter { state.valueAt(it) == null && digit in state.candidatesAt(it) }
                        .toSet() - setOf(base, partner)
                    if (targets.isEmpty()) continue

                    out += Deduction.Elimination(
                        technique = name,
                        difficulty = difficulty,
                        explanation = "In these two $word, $digit has only two homes each, " +
                            "and ${square(base)} and ${square(partner)} share a $across - so " +
                            "they cannot both be $digit. One of ${square(roofs[0])} and " +
                            "${square(roofs[1])} therefore is, which rules $digit out of " +
                            "every square that sees them both.",
                        supportingCells = (first + second).toSet(),
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
 * A row and a column where a digit has two homes each, with one end of each in the same box.
 *
 * The two ends in the box cannot both hold the digit, so one of the far ends must. The
 * same argument as a skyscraper with the shared line replaced by a shared box, which is
 * why it is worth learning them together and why this one is missed: nothing lines up.
 */
object TwoStringKite : Technique {

    override val name = "Two-string kite"
    override val difficulty = Difficulty.HARD

    override val rule = "If a digit has two homes in a row and two in a column, and one " +
        "from each share a box, one of the other two must hold it."

    override val howTo = "Pick a digit. Find a row where it has exactly two homes and a " +
        "column where it has exactly two homes. Now check whether one square of the row and " +
        "one square of the column sit in the same box.\n\nIf they do, those two cannot both " +
        "be the digit - one box, one digit. So at least one of the two remaining ends is, " +
        "and anything seeing both ends can give the digit up.\n\nThe reason people miss " +
        "this one is that there is nothing to line up: the four squares make a lopsided " +
        "shape, and the only thing holding it together is the box the two inner ends share."

    override fun findAll(state: SolverState): List<Deduction> {
        val out = mutableListOf<Deduction>()
        for (digit in 1..9) {
            val rows = Coordinates.rowIndices.mapNotNull { state.strongPlaces(it, digit) }
            val columns = Coordinates.colIndices.mapNotNull { state.strongPlaces(it, digit) }

            for (row in rows) {
                for (column in columns) {
                    if (row.toSet().intersect(column.toSet()).isNotEmpty()) continue

                    for (inRow in row) {
                        val inColumn = column.firstOrNull {
                            Coordinates.boxOf(it) == Coordinates.boxOf(inRow)
                        } ?: continue

                        val ends = listOf(
                            row.first { it != inRow },
                            column.first { it != inColumn },
                        )
                        if (Coordinates.boxOf(ends[0]) == Coordinates.boxOf(ends[1])) continue

                        val targets = bothSeen(ends[0], ends[1])
                            .filter { state.valueAt(it) == null && digit in state.candidatesAt(it) }
                            .toSet() - setOf(inRow, inColumn)
                        if (targets.isEmpty()) continue

                        out += Deduction.Elimination(
                            technique = name,
                            difficulty = difficulty,
                            explanation = "$digit has two homes in this row and two in this " +
                                "column. ${square(inRow)} and ${square(inColumn)} share a box, " +
                                "so they cannot both be $digit - which leaves one of " +
                                "${square(ends[0])} and ${square(ends[1])} holding it, and " +
                                "rules $digit out of everything that sees them both.",
                            supportingCells = (row + column).toSet(),
                            digit = digit,
                            fromCells = targets,
                        )
                    }
                }
            }
        }
        return out
    }
}
