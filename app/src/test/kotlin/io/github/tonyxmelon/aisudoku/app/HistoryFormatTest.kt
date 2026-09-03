package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Every correction the user makes goes out through [HistoryFormat] and comes back in
 * again. If the round trip is not exact they lose work they did by hand, silently and
 * a day later, which is the worst way for a bug to arrive.
 */
class HistoryFormatTest {

    private fun grid(vararg cells: Pair<Int, Cell>): Grid {
        var g = Grid.Empty
        for ((i, c) in cells) g = g.with(i, c)
        return g
    }

    @Test
    fun `a printed digit comes back printed, and a written one written`() {
        val original = grid(0 to Cell.given(5), 1 to Cell.guess(3), 80 to Cell.given(9))
        val back = HistoryFormat.decode(HistoryFormat.encode(original))

        assertEquals(5, back[0].digit)
        assertEquals(CellSource.GIVEN, back[0].source)
        assertEquals(3, back[1].digit)
        assertEquals(CellSource.GUESS, back[1].source)
        assertEquals(9, back[80].digit)
        assertEquals(CellSource.GIVEN, back[80].source)
    }

    @Test
    fun `an empty grid survives the round trip`() {
        val back = HistoryFormat.decode(HistoryFormat.encode(Grid.Empty))
        assertTrue((0 until 81).none { back[it].isFilled })
    }

    @Test
    fun `a full grid survives the round trip, cell for cell`() {
        // A real solved grid, so every digit and both sources are exercised at once.
        val rows = listOf(
            "534678912", "672195348", "198342567",
            "859761423", "426853791", "713924856",
            "961537284", "287419635", "345286179",
        )
        var original = Grid.Empty
        for (r in 0 until 9) {
            for (c in 0 until 9) {
                val digit = rows[r][c] - '0'
                // Alternate the source so the two blocks cannot be confused for each other.
                original = original.with(
                    r * 9 + c,
                    if ((r + c) % 2 == 0) Cell.given(digit) else Cell.guess(digit),
                )
            }
        }

        val back = HistoryFormat.decode(HistoryFormat.encode(original))
        for (i in 0 until 81) {
            assertEquals(original[i].digit, back[i].digit, "digit at $i")
            assertEquals(original[i].source, back[i].source, "source at $i")
        }
    }

    @Test
    fun `a given wins over a written digit in the same square`() {
        // The written block holds everything on the paper, printed included, so the two
        // blocks always overlap. The printed one has to win or every given becomes a guess.
        val encoded = HistoryFormat.encode(grid(0 to Cell.given(7)))
        val (givens, written) = encoded.split("\n--\n")
        assertEquals('7', givens.filterNot { it.isWhitespace() }[0])
        assertEquals('7', written.filterNot { it.isWhitespace() }[0])
        assertEquals(CellSource.GIVEN, HistoryFormat.decode(encoded)[0].source)
    }

    @Test
    fun `malformed text is refused rather than half read`() {
        assertFailsWith<IllegalArgumentException> { HistoryFormat.decode("") }
        assertFailsWith<IllegalArgumentException> { HistoryFormat.decode("no separator here") }
        assertFailsWith<IllegalArgumentException> {
            HistoryFormat.decode(".".repeat(80) + "\n--\n" + ".".repeat(81))
        }
        assertFailsWith<IllegalArgumentException> {
            HistoryFormat.decode("x".repeat(81) + "\n--\n" + ".".repeat(81))
        }
        // A zero is not a sudoku digit, and would decode into a cell that cannot exist.
        assertFailsWith<IllegalArgumentException> {
            HistoryFormat.decode("0".repeat(81) + "\n--\n" + "0".repeat(81))
        }
    }

    @Test
    fun `the written block carries corrections the printed block does not`() {
        val corrected = grid(4 to Cell.given(2), 40 to Cell.guess(8))
        val text = HistoryFormat.encode(corrected)
        val (givens, written) = text.split("\n--\n").map { it.filterNot(Char::isWhitespace) }

        assertEquals('.', givens[40], "a written answer must not be recorded as printed")
        assertEquals('8', written[40])
        assertEquals('2', givens[4])
    }
}
