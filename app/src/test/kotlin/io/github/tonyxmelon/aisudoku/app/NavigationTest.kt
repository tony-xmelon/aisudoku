package io.github.tonyxmelon.aisudoku.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {

    private val start = Navigation(Screen.CAMERA)

    @Test
    fun `back walks out the way you came in`() {
        val onPuzzle = start.go(Screen.PUZZLE)
        val onSettings = onPuzzle.go(Screen.SETTINGS)

        assertEquals(Screen.PUZZLE, assertNotNull(onSettings.back()).screen)
        assertEquals(Screen.CAMERA, assertNotNull(onPuzzle.back()).screen)
        assertNull(start.back(), "the camera with nothing behind it is the door")
    }

    /**
     * The bug this class exists for.
     *
     * "Back from the puzzle shows the camera" and "back from the camera shows the puzzle"
     * are each reasonable on their own, and together they are a trap: the two screens
     * hand back and forth for ever and the app can never be left.
     */
    @Test
    fun `going to the camera from a puzzle and back again still ends at the door`() {
        var nav = start.go(Screen.PUZZLE).go(Screen.CAMERA)

        var presses = 0
        while (true) {
            val next = nav.back() ?: break
            nav = next
            presses++
            assertTrue(presses < 10, "back is going round in circles")
        }
        assertEquals(Screen.CAMERA, nav.screen)
        assertEquals(2, presses, "one press per thing that was opened")
    }

    @Test
    fun `going where you already are costs nothing to undo`() {
        val onPuzzle = start.go(Screen.PUZZLE)
        assertEquals(onPuzzle, onPuzzle.go(Screen.PUZZLE))
    }

    @Test
    fun `a long wander is still bounded`() {
        var nav = start
        repeat(50) { nav = nav.go(Screen.PUZZLE).go(Screen.CAMERA) }

        var presses = 0
        while (true) {
            nav = nav.back() ?: break
            presses++
        }
        assertTrue(presses <= 9, "escaping took $presses presses")
    }

    @Test
    fun `throwing the puzzle away leaves nothing to go back to`() {
        val nav = start.go(Screen.PUZZLE).go(Screen.SETTINGS).reset(Screen.CAMERA)
        assertEquals(Screen.CAMERA, nav.screen)
        assertNull(nav.back())
    }
}
