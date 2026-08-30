package io.github.tonyxmelon.aisudoku.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinatesTest {

    @Test
    fun `maps an index to its row column and box`() {
        assertEquals(0, Coordinates.rowOf(0))
        assertEquals(0, Coordinates.colOf(0))
        assertEquals(0, Coordinates.boxOf(0))

        assertEquals(4, Coordinates.rowOf(40))
        assertEquals(4, Coordinates.colOf(40))
        assertEquals(4, Coordinates.boxOf(40))

        assertEquals(8, Coordinates.rowOf(80))
        assertEquals(8, Coordinates.colOf(80))
        assertEquals(8, Coordinates.boxOf(80))
    }

    @Test
    fun `index round trips through row and column`() {
        for (i in 0 until 81) {
            assertEquals(i, Coordinates.indexOf(Coordinates.rowOf(i), Coordinates.colOf(i)))
        }
    }

    @Test
    fun `box 1 holds the top middle three by three block`() {
        assertEquals(listOf(3, 4, 5, 12, 13, 14, 21, 22, 23), Coordinates.boxIndices[1])
    }

    @Test
    fun `there are twenty seven units of nine cells each`() {
        assertEquals(27, Coordinates.units.size)
        assertTrue(Coordinates.units.all { it.size == 9 })
    }

    @Test
    fun `every cell has exactly twenty peers and is not its own peer`() {
        for (i in 0 until 81) {
            assertEquals(20, Coordinates.peers[i].size, "cell $i")
            assertTrue(i !in Coordinates.peers[i], "cell $i is its own peer")
        }
    }

    @Test
    fun `peers of the top left cell are its row column and box`() {
        val expected = (setOf(0, 1, 2, 3, 4, 5, 6, 7, 8) +   // row 0
            setOf(9, 18, 27, 36, 45, 54, 63, 72) +           // column 0
            setOf(10, 11, 19, 20)) - 0                       // rest of box 0
        assertEquals(expected, Coordinates.peers[0])
    }
}
