package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid

/**
 * How a saved puzzle is written down, separately from where it is written.
 *
 * Two blocks of nine rows - what was printed, then everything on the paper - which is the
 * same shape as the test corpus labels, so a saved puzzle can be dropped straight into
 * the recogniser's test data if it ever reads one wrongly.
 *
 * Split out of [History] because it is the one part of saving a puzzle that needs no
 * Android at all, and the one part where a mistake costs the user something: every
 * correction they have made to a grid goes through here and comes back out again. It sat
 * private inside a class that cannot be built without a Context, so nothing tested it.
 */
object HistoryFormat {

    private const val SEPARATOR = "\n--\n"

    fun encode(grid: Grid): String {
        fun rows(predicate: (Cell) -> Boolean) = (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString("") { c ->
                val cell = grid[r * 9 + c]
                if (cell.isFilled && predicate(cell)) cell.digit.toString() else "."
            }
        }
        return rows { it.source == CellSource.GIVEN } + SEPARATOR + rows { true }
    }

    /**
     * Reads one back. Throws on anything malformed rather than returning a partial grid:
     * the caller drops the entry, and half a puzzle would be worse than none.
     */
    fun decode(text: String): Grid {
        val blocks = text.split(SEPARATOR)
        require(blocks.size == 2) { "expected two blocks, found ${blocks.size}" }
        val givens = blocks[0].filterNot { it.isWhitespace() }
        val written = blocks[1].filterNot { it.isWhitespace() }
        require(givens.length == 81 && written.length == 81) {
            "expected 81 cells in each block, found ${givens.length} and ${written.length}"
        }
        require(givens.all { it == '.' || it in '1'..'9' } &&
            written.all { it == '.' || it in '1'..'9' }) {
            "a cell was neither a digit nor a full stop"
        }

        var grid = Grid.Empty
        for (i in 0 until 81) {
            grid = when {
                givens[i] != '.' -> grid.with(i, Cell.given(givens[i] - '0'))
                written[i] != '.' -> grid.with(i, Cell.guess(written[i] - '0'))
                else -> grid
            }
        }
        return grid
    }
}
