package io.github.tonyxmelon.aisudoku.app

/** Which screen is showing. History is a drawer over whichever of these is up. */
enum class Screen { CAMERA, PUZZLE, SETTINGS, ABOUT, STRATEGIES }

/**
 * Where Back goes.
 *
 * A list rather than a rule per screen, because rules per screen do not compose. The
 * obvious pair - "Back from the puzzle shows the camera" and "Back from the camera shows
 * the puzzle" - are each reasonable alone and together they are a loop with no way out of
 * the app at all. A stack cannot do that: every press pops exactly one thing that was
 * pushed, so however you wandered in, that many presses walk you out.
 *
 * Deliberately small. It records screens, not history entries: which puzzle is on screen
 * lives in one slot beside this, and Back has never meant "the puzzle before this one".
 */
data class Navigation(
    val screen: Screen,
    /** Where Back goes, oldest first. Empty means Back leaves the app. */
    val previous: List<Screen> = emptyList(),
) {

    val canGoBack: Boolean get() = previous.isNotEmpty()

    /**
     * Opening something on top of what is showing.
     *
     * Going where you already are pushes nothing, so that reopening a puzzle from the
     * drawer does not cost a press to undo something the user cannot see happening.
     */
    fun go(target: Screen): Navigation = when (target) {
        screen -> this
        else -> Navigation(target, (previous + screen).takeLast(DEPTH))
    }

    /** One press of Back. Null when there is nothing left to undo and the app should close. */
    fun back(): Navigation? =
        if (previous.isEmpty()) null
        else Navigation(previous.last(), previous.dropLast(1))

    /** Starting again with nothing behind, for when what you were looking at is gone. */
    fun reset(target: Screen): Navigation = Navigation(target)

    private companion object {
        /**
         * How far back to remember.
         *
         * Enough that no ordinary wander overflows it, and bounded so that a user
         * ping-ponging between the camera and a puzzle cannot build a stack that takes
         * fifty presses to escape. Overflow drops the oldest, so Back still always ends
         * at the door.
         */
        const val DEPTH = 8
    }
}
