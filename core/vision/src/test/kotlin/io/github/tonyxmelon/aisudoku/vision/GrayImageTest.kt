package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrayImageTest {

    @Test
    fun `pixels are addressable and stored row major`() {
        val image = GrayImage(3, 2, byteArrayOf(0, 1, 2, 3, 4, 5))
        assertEquals(0, image[0, 0])
        assertEquals(2, image[2, 0])
        assertEquals(3, image[0, 1])
        assertEquals(5, image[2, 1])
    }

    @Test
    fun `pixel values are unsigned`() {
        val image = GrayImage(1, 1, byteArrayOf(-1))
        assertEquals(255, image[0, 0])
    }

    @Test
    fun `the buffer must match the dimensions`() {
        assertFailsWith<IllegalArgumentException> { GrayImage(3, 2, ByteArray(5)) }
    }

    @Test
    fun `a round trip through a Mat preserves every pixel`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val original = GrayImage(5, 4, ByteArray(20) { (it * 11).toByte() })
        val restored = original.toMat().toGrayImage()
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.pixels.toList(), restored.pixels.toList())
    }
}
