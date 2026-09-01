package io.github.tonyxmelon.aisudoku.app

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The outline drawn over the preview has to land on the thing it outlines.
 *
 * An outline in the wrong place is worse than none at all: it would tell the user the app
 * is looking somewhere it is not, and they would move the phone to correct a fault that
 * was in this arithmetic. Nothing here needs a device, so it can be checked properly.
 */
class FramingTest {

    private fun near(actual: Fraction, x: Float, y: Float, note: String) {
        assertTrue(
            abs(actual.x - x) < 0.6f && abs(actual.y - y) < 0.6f,
            "$note: expected about ($x, $y) but got (${actual.x}, ${actual.y})",
        )
    }

    /** A phone held upright: the camera's frame arrives on its side. */
    private val portrait = Sighting(
        corners = emptyList(),
        accepted = true,
        rotationDegrees = 90,
        frameWidth = 960,
        frameHeight = 720,
    )

    @Test
    fun `a quarter turn takes the frame's top left corner to the top right`() {
        near(Framing.turned(Fraction(0f, 0f), 90), 1f, 0f, "top left")
        near(Framing.turned(Fraction(1f, 0f), 90), 1f, 1f, "top right")
        near(Framing.turned(Fraction(1f, 1f), 90), 0f, 1f, "bottom right")
        near(Framing.turned(Fraction(0f, 1f), 90), 0f, 0f, "bottom left")
    }

    @Test
    fun `no rotation leaves a point where it was`() {
        near(Framing.turned(Fraction(0.25f, 0.75f), 0), 0.25f, 0.75f, "unturned")
    }

    /**
     * The middle of the frame is the middle of the screen, whatever the turn and crop.
     */
    @Test
    fun `the centre of the frame is the centre of the view`() {
        for (rotation in listOf(0, 90, 180, 270)) {
            val sighting = portrait.copy(rotationDegrees = rotation)
            val middle = Framing.onScreen(Fraction(0.5f, 0.5f), sighting, 1080f, 2000f)
            near(middle, 540f, 1000f, "centre at $rotation degrees")
        }
    }

    /**
     * A tall view fills its height from a frame turned upright, and crops the sides. So
     * the top and bottom of the frame reach the top and bottom of the view, while its
     * left and right edges fall outside it - which is what the user sees.
     */
    @Test
    fun `the frame fills the view and its sides are cropped away`() {
        val top = Framing.onScreen(Fraction(0f, 0.5f), portrait, 1080f, 2000f)
        val bottom = Framing.onScreen(Fraction(1f, 0.5f), portrait, 1080f, 2000f)
        near(top, 540f, 0f, "the frame's left edge is the top of the screen")
        near(bottom, 540f, 2000f, "the frame's right edge is the bottom of the screen")

        // 960x720 turned upright is 720 wide by 960 tall, an aspect of 0.75 against the
        // view's 0.54 - so the sides hang well outside it. The quarter turn puts the
        // frame's top edge on the screen's right and its bottom edge on the left, which is
        // the whole reason this arithmetic is worth testing rather than eyeballing.
        val fromTop = Framing.onScreen(Fraction(0.5f, 0f), portrait, 1080f, 2000f)
        val fromBottom = Framing.onScreen(Fraction(0.5f, 1f), portrait, 1080f, 2000f)
        assertTrue(fromTop.x > 1080f, "the frame's top should crop off the right, got ${fromTop.x}")
        assertTrue(fromBottom.x < 0f, "the frame's bottom should crop off the left, got ${fromBottom.x}")
    }
}
