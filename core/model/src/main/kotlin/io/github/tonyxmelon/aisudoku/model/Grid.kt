package io.github.tonyxmelon.aisudoku.model

import io.github.tonyxmelon.aisudoku.model.Coordinates.CELL_COUNT

/**
 * An immutable 9x9 grid. Every mutation returns a new grid.
 *
 * This is the boundary type between recognition, solving and the UI, so it stays
 * dumb on purpose: it knows how to describe itself and how to spot a rule violation,
 * and nothing else.
 */
class Grid private constructor(val cells: List<Cell>) {

    init {
        require(cells.size == CELL_COUNT) { "a grid needs $CELL_COUNT cells but got ${cells.size}" }
    }

    operator fun get(index: Int): Cell = cells[index]

    operator fun get(row: Int, col: Int): Cell = cells[Coordinates.indexOf(row, col)]

    fun with(index: Int, cell: Cell): Grid = Grid(cells.toMutableList().also { it[index] = cell })

    val filledCount: Int get() = cells.count { it.isFilled }

    val givenCount: Int get() = cells.count { it.source == CellSource.GIVEN }

    val isComplete: Boolean get() = filledCount == CELL_COUNT

    /** The puzzle as printed: guesses removed, givens kept. */
    fun givensOnly(): Grid =
        Grid(cells.map { if (it.source == CellSource.GIVEN) it else Cell.Empty })

    /**
     * Indices of every cell that shares a digit with another cell in the same row,
     * column or box. Empty when the grid breaks no rules.
     */
    fun conflicts(): Set<Int> {
        val bad = mutableSetOf<Int>()
        for (unit in Coordinates.units) {
            val byDigit = unit.filter { cells[it].isFilled }.groupBy { cells[it].digit }
            for ((_, indices) in byDigit) {
                if (indices.size > 1) bad += indices
            }
        }
        return bad
    }

    val isValid: Boolean get() = conflicts().isEmpty()

    /** 81 characters, `.` for empty. Filled cells lose their provenance. */
    fun toGivensString(): String =
        cells.joinToString("") { it.digit?.toString() ?: "." }

    override fun equals(other: Any?): Boolean = other is Grid && other.cells == cells

    override fun hashCode(): Int = cells.hashCode()

    override fun toString(): String =
        (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString("") { c -> this[r, c].digit?.toString() ?: "." }
        }

    companion object {
        val Empty: Grid = Grid(List(CELL_COUNT) { Cell.Empty })

        fun of(cells: List<Cell>): Grid = Grid(cells)

        /**
         * Parses 81 characters, `.` or `0` for an empty cell. Every digit becomes a GIVEN.
         * Whitespace is ignored so callers may format for readability.
         */
        fun fromGivens(text: String): Grid {
            val cleaned = text.filterNot { it.isWhitespace() }
            require(cleaned.length == CELL_COUNT) {
                "expected $CELL_COUNT characters but got ${cleaned.length}"
            }
            return Grid(cleaned.map { ch ->
                when (ch) {
                    '.', '0' -> Cell.Empty
                    in '1'..'9' -> Cell.given(ch - '0')
                    else -> throw IllegalArgumentException("unexpected character '$ch'")
                }
            })
        }

        /** Nine rows of nine characters. Far easier to read and review than one long string. */
        fun fromRows(vararg rows: String): Grid {
            require(rows.size == 9) { "expected 9 rows but got ${rows.size}" }
            rows.forEachIndexed { i, row ->
                require(row.length == 9) { "row $i has ${row.length} characters, expected 9" }
            }
            return fromGivens(rows.joinToString(""))
        }
    }
}
