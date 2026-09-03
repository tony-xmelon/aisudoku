package io.github.tonyxmelon.aisudoku.app

/**
 * How the puzzle screen arranges itself in the window it is given, in density-independent
 * pixels. Pure arithmetic, so it can be tested without a device - see [PuzzleLayoutTest].
 */
data class PuzzleArrangement(
    /** True when the controls sit beside the photograph rather than under it. */
    val sideBySide: Boolean,
    /** The side of the square the photograph is drawn in. */
    val photoSide: Float,
)

object PuzzleLayout {

    /** The gap either side of the photograph when it is stacked above the controls. */
    private const val MARGIN = 24f

    /**
     * How much of an upright window's height the photograph may take. Deliberately
     * under half: the pane below has to hold a sentence of reasoning, a key, and four
     * buttons, and the buttons must stay under the thumb.
     */
    private const val UPRIGHT_SHARE = 0.52f

    /** The app bar, the empty-count under the grid, and the padding around them. */
    private const val CHROME = 94f

    /** Four buttons in a row and a sentence of tutoring; less is not worth the split. */
    const val MIN_CONTROLS_WIDTH = 300f

    /**
     * Past this the grid stops growing. A photograph of a newspaper puzzle has no more
     * detail to show at 700dp than at 500, and a grid that fills a tablet leaves the
     * controls stranded at the edge of the screen, a hand's width from the eye.
     */
    const val MAX_PHOTO = 520f

    /**
     * The arrangement that shows the larger grid.
     *
     * Both arrangements are costed and the better one wins, rather than switching on
     * orientation. The two come out very close on a near-square window - an unfolded
     * foldable - and a rule that reads the shape of the window instead would flip
     * between them over a pixel while the user is unfolding it. On a tie the photograph
     * stays above the controls, which is the arrangement that has been tested on a real
     * phone.
     */
    fun forWindow(width: Float, height: Float): PuzzleArrangement {
        val stacked = minOf(width - MARGIN, height * UPRIGHT_SHARE)
            .coerceAtMost(MAX_PHOTO)
            .coerceAtLeast(0f)

        // Side by side, the photograph is bounded by the height left under the app bar,
        // and by the width left once the controls have taken theirs.
        val beside = minOf(height - CHROME, width - MIN_CONTROLS_WIDTH - MARGIN)
            .coerceAtMost(MAX_PHOTO)
            .coerceAtLeast(0f)

        return if (beside > stacked) {
            PuzzleArrangement(sideBySide = true, photoSide = beside)
        } else {
            PuzzleArrangement(sideBySide = false, photoSide = stacked)
        }
    }
}
