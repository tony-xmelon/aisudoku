package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuadTest {

    private val square = Quad(
        topLeft = Corner(0.0, 0.0),
        topRight = Corner(100.0, 0.0),
        bottomRight = Corner(100.0, 100.0),
        bottomLeft = Corner(0.0, 100.0),
    )

    @Test
    fun `a square has equal sides and no skew`() {
        assertEquals(1.0, square.oppositeSideRatio, 0.001)
        assertEquals(0.0, square.rotationDegrees, 0.001)
        assertTrue(square.maxCornerAngleDeviation < 0.001)
    }

    @Test
    fun `area is computed by the shoelace formula`() {
        assertEquals(10_000.0, square.area, 0.001)
    }

    @Test
    fun `a trapezoid has unequal opposite sides`() {
        val trapezoid = Quad(
            Corner(20.0, 0.0), Corner(80.0, 0.0),
            Corner(100.0, 100.0), Corner(0.0, 100.0),
        )
        assertTrue(trapezoid.oppositeSideRatio > 1.5, "${trapezoid.oppositeSideRatio}")
    }

    @Test
    fun `rotation is measured from the top edge`() {
        val tilted = Quad(
            Corner(0.0, 0.0), Corner(100.0, 100.0),
            Corner(0.0, 200.0), Corner(-100.0, 100.0),
        )
        assertEquals(45.0, tilted.rotationDegrees, 0.5)
    }

    @Test
    fun `ordering assigns corners by coordinate sums and differences`() {
        val scrambled = listOf(
            Corner(100.0, 100.0), Corner(0.0, 0.0),
            Corner(0.0, 100.0), Corner(100.0, 0.0),
        )
        assertEquals(square, Quad.ordering(scrambled))
    }
}
