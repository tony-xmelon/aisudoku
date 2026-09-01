package io.github.tonyxmelon.aisudoku.solver

import io.github.tonyxmelon.aisudoku.model.Coordinates

/**
 * One digit, N lines, N crossing lines.
 *
 * If a digit's places in N rows all fall inside the same N columns, then those N rows use
 * up the digit in all N columns between them - so it cannot appear in those columns
 * anywhere else. The same holds with rows and columns swapped.
 *
 * N = 2 is an X-wing, N = 3 a swordfish, N = 4 a jellyfish. Nothing changes but the size,
 * which is worth saying out loud: people learn them as three separate tricks and then
 * cannot see the second and third.
 */
class Fish(private val size: Int) : Technique {

    override val name = when (size) {
        2 -> "X-wing"
        3 -> "Swordfish"
        else -> "Jellyfish"
    }

    override val difficulty = if (size == 2) Difficulty.HARD else Difficulty.VERY_HARD

    override val rule = "If a digit's only homes in $size rows lie in the same $size " +
        "columns, it can be ruled out of those columns elsewhere - and the same with rows " +
        "and columns swapped."

    override val howTo = "Pick one digit and ignore every other. Go down the rows and note " +
        "the ones where that digit has only two or three possible squares left. Look for " +
        "$size such rows whose squares line up in the same $size columns.\n\n" +
        "If you find them, each of those $size rows must put the digit in one of those " +
        "columns, and no two can use the same column. So between them they consume the " +
        "digit in all $size columns - and every other square in those columns can give it " +
        "up.\n\nThen do the whole thing again with columns and rows swapped. Half of these " +
        "are found the second way round, and it is the half most people never look for."

    override fun findAll(state: SolverState): List<Deduction> =
        search(state, byRows = true) + search(state, byRows = false)

    private fun search(state: SolverState, byRows: Boolean): List<Deduction> {
        val lines = if (byRows) Coordinates.rowIndices else Coordinates.colIndices
        val crossing = if (byRows) Coordinates.colIndices else Coordinates.rowIndices
        val lineWord = if (byRows) "rows" else "columns"
        val crossWord = if (byRows) "columns" else "rows"
        fun crossOf(index: Int) = if (byRows) Coordinates.colOf(index) else Coordinates.rowOf(index)

        val out = mutableListOf<Deduction>()
        for (digit in 1..9) {
            // Only lines that still NEED the digit. The solver's states are not
            // propagated - placing a digit does not strike it from its neighbours' lists -
            // so a line can hold the digit already and still show stale candidates for it.
            // Counting such a line into the fish asserts it needs a digit it has, and the
            // pigeonhole argument then eliminates a true candidate somewhere else.
            val candidates = lines.indices
                .filter { line -> lines[line].none { state.valueAt(it) == digit } }
                .map { it to state.placesFor(lines[it], digit) }
                .filter { (_, places) -> places.size in 2..size }

            for (group in combinations(candidates, size)) {
                val covered = group.flatMap { (_, places) -> places.map(::crossOf) }.toSet()
                if (covered.size != size) continue

                val body = group.flatMap { (_, places) -> places }.toSet()
                val targets = covered
                    .flatMap { crossing[it] }
                    .filter { it !in body && state.valueAt(it) == null && digit in state.candidatesAt(it) }
                    .toSet()
                if (targets.isEmpty()) continue

                out += Deduction.Elimination(
                    technique = name,
                    difficulty = difficulty,
                    explanation = "In these $size $lineWord, $digit can only go in these $size " +
                        "$crossWord. Between them they use up every one, so $digit can be ruled " +
                        "out of those $crossWord everywhere else.",
                    supportingCells = body,
                    digit = digit,
                    fromCells = targets,
                )
            }
        }
        return out
    }
}

val XWing = Fish(2)
val Swordfish = Fish(3)
val Jellyfish = Fish(4)
