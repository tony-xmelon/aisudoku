package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the camera says when it cannot find a grid.
 *
 * Reported from the phone: a puzzle filling the frame, and the app answering "point the
 * camera at a sudoku puzzle" - which is advice to do the thing already being done. The
 * message is the only thing the user can act on, so each failure has to say something
 * different and true.
 *
 * Built from plain synthetic frames rather than the corpus, so these run anywhere.
 */
class FramingAdvisorTest {

    @BeforeTest
    fun setUp() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    private fun flat(shade: Int, width: Int = 480, height: Int = 360) =
        GrayImage(width, height, ByteArray(width * height) { shade.toByte() })

    /** A filled rectangle on a light ground: something square-cornered, but not a grid. */
    private fun blankSquare(): GrayImage {
        val image = flat(230)
        for (y in 60 until 300) {
            for (x in 120 until 360) {
                val edge = y < 66 || y >= 294 || x < 126 || x >= 354
                image.pixels[y * image.width + x] = (if (edge) 20 else 245).toByte()
            }
        }
        return image
    }

    private fun advise(image: GrayImage) = FramingAdvisor().advise(image)

    @Test
    fun `a dark frame says so rather than blaming the aim`() {
        val guidance = advise(flat(20))
        assertEquals("Too dark to make out the grid lines", guidance.message)
        assertFalse(guidance.readyToCapture)
    }

    @Test
    fun `a blown-out frame blames the light rather than the aim`() {
        assertEquals(
            "Glare is washing the lines out - tilt the page away from the light",
            advise(flat(255)).message,
        )
    }

    /** Nothing square-cornered anywhere: the original advice is the right advice. */
    @Test
    fun `an empty scene still asks for a puzzle`() {
        assertEquals("Point the camera at a sudoku puzzle", advise(flat(140)).message)
    }

    /**
     * The case that prompted all this: something is plainly in frame, well lit, and square
     * - it just does not read as nine rows and nine columns. Saying "point the camera at a
     * sudoku puzzle" there is the one answer that cannot be acted on.
     */
    @Test
    fun `a square that is not a grid says what is wrong with it`() {
        val message = advise(blankSquare()).message
        assertTrue(
            message.contains("nine by nine") || message.startsWith("Almost"),
            "expected an explanation of what does not read as a grid, but got: $message",
        )
    }
}
