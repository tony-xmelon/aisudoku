package io.github.tonyxmelon.aisudoku.app

import android.graphics.Bitmap
import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.Chain
import io.github.tonyxmelon.aisudoku.solver.Hint
import io.github.tonyxmelon.aisudoku.solver.TechniqueSolver
import io.github.tonyxmelon.aisudoku.solver.Techniques
import io.github.tonyxmelon.aisudoku.solver.Walkthrough

/**
 * Where the grid lines really are, as fractions of the photograph's width and height.
 *
 * Ten of each. The overlay cannot simply divide by nine: paper is not flat, and the
 * extractor already fits the real lines because a single homography leaves cells
 * progressively misaligned towards the edges. Drawing on ninths would put the tints and
 * digits a few pixels off exactly where the page is most bowed.
 */
data class GridLines(val vertical: List<Float>, val horizontal: List<Float>) {
    init {
        require(vertical.size == 10 && horizontal.size == 10) { "expected ten lines each way" }
    }

    companion object {
        /** Even ninths, for a puzzle reopened from history with no geometry kept. */
        val EVEN = GridLines(List(10) { it / 9f }, List(10) { it / 9f })
    }
}

/**
 * Everything the puzzle screen shows.
 *
 * The rules live in [PuzzleLogic], which knows nothing about Android and is therefore
 * testable without a device. This holds the photograph and what the user has touched.
 */
data class PuzzleState(
    val photo: Bitmap,
    val grid: Grid,
    /** Cells the reader was not sure of. Drawn as a ring, and cleared as they are settled. */
    val uncertainCells: Set<Int>,
    val readingNote: String?,
    /** Where the grid lines are, so the overlay lands on the squares it means. */
    val lines: GridLines = GridLines.EVEN,
    /**
     * What the reader made of each square, when this puzzle came from a photograph.
     *
     * The whole list is null for a puzzle reopened from history, where only the grid was
     * kept. A single entry is null once the user has corrected that square: the reading
     * is then no longer what is there, and saying otherwise is worse than saying nothing.
     */
    val reports: List<CellReport?>? = null,
    val overlay: OverlayMode = OverlayMode.NONE,
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val selectedCell: Int? = null,
    /** How far the current hint has been pushed. See [PuzzleLogic.HINT_DEPTHS]. */
    val hintDepth: Int = 0,
    /** Which step of the walkthrough is being shown. */
    val lessonStep: Int = 0,
    /**
     * The technique being browsed, when the user has gone looking at one on purpose.
     *
     * Null means the tutor is walking its own route. Set means the user is exploring one
     * technique's findings in this position instead, which is the same machinery pointed
     * at a different list.
     */
    val tutorTechnique: String? = null,
    /**
     * Squares the user typed in themselves.
     *
     * Not derivable from the grid: a digit read from the paper and a digit thumbed in are
     * the same cell, and only one of them is actually written on the photograph. Kept so
     * the second kind can be drawn, since otherwise answering a square changes nothing on
     * screen at all.
     */
    val entered: Set<Int> = emptySet(),
) {
    // Computed once per state rather than once per read. Every one of these runs the
    // solver, and Compose asks for them again on every recomposition - including one per
    // tap on the photograph. The state is immutable, so caching is free of risk.
    val hint: Hint? by lazy { PuzzleLogic.hint(grid, hintStyle) }

    /** News about the puzzle, when there is any. Null is the ordinary case. */
    val status: Status? by lazy { PuzzleLogic.status(grid) }

    /** How much is left, for the counter under the grid. */
    val progress: String by lazy { PuzzleLogic.progress(grid) }

    val guidance: Guidance? by lazy {
        PuzzleLogic.guidance(
            grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep, tutorTechnique,
        )
    }

    val legend: List<LegendKey>
        get() = PuzzleLogic.legend(computed, overlay, uncertainCells.isNotEmpty())

    /** What to call the evidence colour in the key: the technique it belongs to. */
    val evidenceLabel: String?
        get() = PuzzleLogic.evidenceLabel(
            grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep,
        )

    /**
     * The whole route from here to the answer, in human steps.
     *
     * Costs a full technique solve, so it is worked out once per state and only when
     * something asks for it - which the button offering it does, on every recomposition.
     */
    val walkthrough: Walkthrough? by lazy {
        val chosen = tutorTechnique?.let { Techniques.byName(it) }
        if (chosen != null) TechniqueSolver.findings(grid, chosen)
        else TechniqueSolver.walkthrough(grid)
    }

    /** The route grouped into runs of one technique, for the tutor's progress line. */
    val chapters: List<Chapter> by lazy { PuzzleLogic.chapters(walkthrough) }

    /** How many places each technique applies right now, for the tutor's own menu. */
    val findingCounts: Map<String, Int> by lazy { TechniqueSolver.findingCounts(grid) }

    private val computed: Overlay by lazy {
        PuzzleLogic.overlay(grid, overlay, hintStyle, hintDepth, walkthrough, lessonStep, entered)
    }

    fun overlayDigits(): Map<Int, OverlayDigit> = computed.digits

    fun evidenceCells(): Set<Int> = computed.evidence

    fun focusCell(): Int? = computed.focus

    /** The forcing chain being walked, when the step showing is one. */
    fun chain(): Chain? = computed.chain

    /**
     * Turning a layer on, or - for a hint - pushing the one already showing one step
     * further.
     *
     * Pressing Hint again is what walks down the staircase, so that asking for more help
     * needs no second control and no explanation of where to find it. Once there is
     * nothing left to reveal, the same press turns it off, which is what every other
     * layer's second press does.
     */
    fun show(mode: OverlayMode): PuzzleState {
        val next = PuzzleLogic.press(overlay, mode, hintDepth, hintStyle)
        return copy(
            overlay = next.mode,
            hintDepth = next.hintDepth,
            lessonStep = if (next.mode == overlay) lessonStep else 0,
            selectedCell = null,
        )
    }

    /**
     * Putting whatever layer is showing away, outright.
     *
     * Not the same as pressing its button again: pressing Hint walks further down the
     * staircase, which is right for a press of Hint and wrong for a press of Back. Back
     * means undo the last thing that happened on this screen, and the last thing that
     * happened was the layer appearing.
     */
    fun close(): PuzzleState = copy(
        overlay = OverlayMode.NONE,
        hintDepth = 0,
        lessonStep = 0,
        tutorTechnique = null,
        selectedCell = null,
    )

    /** Starting the tutor, either on its own route or on one technique the user picked. */
    fun tutor(technique: String? = null): PuzzleState = copy(
        tutorTechnique = technique,
        overlay = OverlayMode.LESSON,
        lessonStep = 0,
        selectedCell = null,
    )

    /**
     * Moving through the walkthrough. Clamped, so the ends are simply inert.
     *
     * Zero is the introduction and the route's own steps run from one, so the last
     * position is the number of steps rather than one less than it.
     */
    fun stepTo(step: Int): PuzzleState = copy(
        lessonStep = step.coerceIn(0, PuzzleLogic.lastStep(walkthrough)),
        selectedCell = null,
    )

    fun withCell(index: Int, digit: Int?, source: CellSource): PuzzleState {
        val cell = when {
            digit == null -> Cell.Empty
            source == CellSource.GIVEN -> Cell.given(digit)
            else -> Cell.guess(digit)
        }
        return copy(
            grid = grid.with(index, cell),
            uncertainCells = uncertainCells - index,
            // A square the user has answered is theirs now, and is drawn as theirs.
            // Clearing it hands it back.
            entered = if (digit == null) entered - index else entered + index,
            // The route and the hint were both worked out from a grid that has just
            // changed underneath them.
            hintDepth = 0,
            lessonStep = 0,
            // The reader's account of this square is now out of date - the user has just
            // overruled it - so it goes. Keeping it left the reading layer colouring the
            // square as whatever it had been read as, after being told it is empty.
            reports = reports?.mapIndexed { i, report -> if (i == index) null else report },
        )
    }

    /** The user has looked at everything the reader flagged and is happy with it. */
    fun acceptReading(): PuzzleState = copy(uncertainCells = emptySet())
}
