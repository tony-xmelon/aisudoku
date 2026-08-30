package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridTest {

    @Test
    fun `the empty grid holds eighty one empty cells`() {
        assertEquals(81, Grid.Empty.cells.size)
        assertTrue(Grid.Empty.cells.all { it == Cell.Empty })
        assertEquals(0, Grid.Empty.filledCount)
        assertFalse(Grid.Empty.isComplete)
    }

    @Test
    fun `cells are addressable by index and by row and column`() {
        val grid = Grid.Empty.with(40, Cell.given(5))
        assertEquals(Cell.given(5), grid[40])
        assertEquals(Cell.given(5), grid[4, 4])
    }

    @Test
    fun `with returns a new grid and leaves the original alone`() {
        val original = Grid.Empty
        val updated = original.with(0, Cell.guess(1))
        assertEquals(Cell.Empty, original[0])
        assertEquals(Cell.guess(1), updated[0])
    }

    @Test
    fun `parses nine rows of givens using dot for empty`() {
        val grid = Grid.fromRows(
            "12345678.",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
        )
        assertEquals(Cell.given(1), grid[0])
        assertEquals(Cell.given(8), grid[7])
        assertEquals(Cell.Empty, grid[8])
        assertEquals(8, grid.givenCount)
    }

    @Test
    fun `rejects malformed input`() {
        assertFailsWith<IllegalArgumentException> { Grid.fromRows("12345678") }        // 8 rows
        assertFailsWith<IllegalArgumentException> { Grid.fromGivens("123") }           // too short
        assertFailsWith<IllegalArgumentException> { Grid.fromGivens("x".repeat(81)) }  // bad character
    }

    @Test
    fun `round trips through its string form`() {
        val text = "12345678." + ".".repeat(72)
        assertEquals(text, Grid.fromGivens(text).toGivensString())
    }

    @Test
    fun `givensOnly discards guesses`() {
        val grid = Grid.Empty
            .with(0, Cell.given(1))
            .with(1, Cell.guess(2))
        val givens = grid.givensOnly()
        assertEquals(Cell.given(1), givens[0])
        assertEquals(Cell.Empty, givens[1])
    }

    @Test
    fun `a grid with no repeats has no conflicts`() {
        val grid = Grid.fromRows(
            "12345678.",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
        )
        assertTrue(grid.conflicts().isEmpty())
        assertTrue(grid.isValid)
    }

    @Test
    fun `conflicts reports every cell involved in a repeat`() {
        val row = Grid.Empty.with(0, Cell.given(5)).with(1, Cell.given(5))
        assertEquals(setOf(0, 1), row.conflicts())
        assertFalse(row.isValid)

        val column = Grid.Empty.with(0, Cell.given(5)).with(9, Cell.guess(5))
        assertEquals(setOf(0, 9), column.conflicts())

        val box = Grid.Empty.with(0, Cell.given(5)).with(10, Cell.given(5))
        assertEquals(setOf(0, 10), box.conflicts())
    }

    @Test
    fun `a full grid is complete`() {
        val full = Grid.of(List(81) { Cell.given((it % 9) + 1) })
        assertEquals(81, full.filledCount)
        assertTrue(full.isComplete)
    }
}
