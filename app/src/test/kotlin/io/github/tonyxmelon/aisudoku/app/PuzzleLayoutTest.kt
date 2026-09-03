package io.github.tonyxmelon.aisudoku.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the photograph goes in the window, and how big it is.
 *
 * This is the whole of the landscape decision, kept out of the composable so it can be
 * checked without a device - which matters here more than usual, because no layout in this
 * app had ever been run sideways when this was written. A grid that shrinks to a thumbnail
 * on a rotated phone is not a bug the compiler can see.
 */
class PuzzleLayoutTest {

    /** A phone held upright, in the density-independent pixels of a common one. */
    private val portraitPhone = PuzzleLayout.forWindow(392f, 850f)

    /** The same phone turned on its side. */
    private val landscapePhone = PuzzleLayout.forWindow(850f, 392f)

    @Test
    fun `a phone held upright stacks the photograph above the controls`() {
        assertTrue(!portraitPhone.sideBySide, "upright should stack")
    }

    @Test
    fun `a phone on its side puts the controls beside the photograph`() {
        assertTrue(landscapePhone.sideBySide, "sideways should be side by side")
    }

    @Test
    fun `turning the phone does not shrink the grid to a thumbnail`() {
        // The rule this replaces - half the window's height - would have given 204dp
        // here, a grid smaller than the one an upright phone shows, on a window twice
        // as wide. Anything much under the upright size is the failure being guarded.
        assertTrue(
            landscapePhone.photoSide > portraitPhone.photoSide * 0.75f,
            "sideways grid ${landscapePhone.photoSide} is far smaller than " +
                "upright ${portraitPhone.photoSide}",
        )
    }

    @Test
    fun `the photograph always fits the window it is drawn in`() {
        val windows = listOf(
            320f to 480f, // the smallest phone Play will offer this to
            392f to 850f,
            850f to 392f,
            600f to 960f, // a tablet upright
            1280f to 800f, // a tablet on its side
            1000f to 1100f, // an unfolded foldable, near enough square
        )
        for ((width, height) in windows) {
            val layout = PuzzleLayout.forWindow(width, height)
            assertTrue(
                layout.photoSide <= width && layout.photoSide <= height,
                "$width x $height: photograph ${layout.photoSide} does not fit",
            )
            assertTrue(layout.photoSide > 0f, "$width x $height: photograph vanished")
        }
    }

    @Test
    fun `the controls keep a usable width beside the photograph`() {
        // Four buttons in a row, and a tutor panel that has to say a sentence. Below
        // this the side-by-side arrangement is worse than stacking.
        val tablet = PuzzleLayout.forWindow(1280f, 800f)
        assertTrue(tablet.sideBySide, "a tablet on its side should be side by side")
        assertTrue(
            1280f - tablet.photoSide >= PuzzleLayout.MIN_CONTROLS_WIDTH,
            "only ${1280f - tablet.photoSide}dp left for the controls",
        )
    }

    @Test
    fun `a window too narrow to hold both stacks instead`() {
        // Wider than it is tall, but not by enough to seat the controls next to a grid
        // worth looking at. Stacking is the lesser evil.
        val squat = PuzzleLayout.forWindow(560f, 520f)
        assertTrue(!squat.sideBySide, "should have stacked rather than crush the grid")
    }

    @Test
    fun `a big screen does not blow the grid up to fill it`() {
        val tablet = PuzzleLayout.forWindow(1280f, 800f)
        assertEquals(PuzzleLayout.MAX_PHOTO, tablet.photoSide, "should stop at the cap")
    }

    @Test
    fun `an upright phone is sized exactly as it was before landscape existed`() {
        // The one arrangement Tony has actually been looking at for a fortnight. The
        // old rule was `(width - 24).coerceAtMost(height * 0.52)`; this pins it, so
        // that adding landscape cannot quietly redraw portrait.
        assertEquals(368f, portraitPhone.photoSide, "upright phone changed size")
    }
}
