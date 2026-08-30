package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CellTest {

    @Test
    fun `a given carries its digit`() {
        val cell = Cell.given(7)
        assertEquals(7, cell.digit)
        assertEquals(CellSource.GIVEN, cell.source)
        assertTrue(cell.isFilled)
    }

    @Test
    fun `a guess carries its digit`() {
        val cell = Cell.guess(3)
        assertEquals(3, cell.digit)
        assertEquals(CellSource.GUESS, cell.source)
        assertTrue(cell.isFilled)
    }

    @Test
    fun `the empty cell has no digit`() {
        assertNull(Cell.Empty.digit)
        assertEquals(CellSource.EMPTY, Cell.Empty.source)
        assertFalse(Cell.Empty.isFilled)
    }

    @Test
    fun `digits outside one to nine are rejected`() {
        assertFailsWith<IllegalArgumentException> { Cell.given(0) }
        assertFailsWith<IllegalArgumentException> { Cell.given(10) }
        assertFailsWith<IllegalArgumentException> { Cell.guess(-1) }
    }

    @Test
    fun `a filled cell cannot claim to be empty and an empty cell cannot hold a digit`() {
        assertFailsWith<IllegalArgumentException> { Cell(4, CellSource.EMPTY) }
        assertFailsWith<IllegalArgumentException> { Cell(null, CellSource.GIVEN) }
        assertFailsWith<IllegalArgumentException> { Cell(null, CellSource.GUESS) }
    }
}
