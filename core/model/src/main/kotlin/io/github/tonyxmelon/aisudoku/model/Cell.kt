package io.github.tonyxmelon.aisudoku.model

/** Where a digit in a cell came from. */
enum class CellSource {
    /** Printed in the puzzle. Defines the puzzle and is never wrong. */
    GIVEN,

    /** Written in by hand. May be wrong; this is what "check my answers" checks. */
    GUESS,

    /** No digit. */
    EMPTY,
}

/**
 * One cell of a grid.
 *
 * Invariant: [digit] is null exactly when [source] is [CellSource.EMPTY]. Constructing a
 * cell that breaks this throws, so no consumer has to handle the contradiction.
 */
data class Cell(val digit: Int?, val source: CellSource) {

    init {
        if (digit != null) {
            require(digit in 1..9) { "digit must be 1..9 but was $digit" }
            require(source != CellSource.EMPTY) { "a cell holding $digit cannot be EMPTY" }
        } else {
            require(source == CellSource.EMPTY) { "a cell with no digit must be EMPTY but was $source" }
        }
    }

    val isFilled: Boolean get() = digit != null

    companion object {
        val Empty: Cell = Cell(null, CellSource.EMPTY)

        fun given(digit: Int): Cell = Cell(digit, CellSource.GIVEN)

        fun guess(digit: Int): Cell = Cell(digit, CellSource.GUESS)
    }
}
