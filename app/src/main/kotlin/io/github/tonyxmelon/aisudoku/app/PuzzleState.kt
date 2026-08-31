package io.github.tonyxmelon.aisudoku.app

import android.graphics.Bitmap
import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.Hint

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
    /**
     * What the reader made of each square, when this puzzle came from a photograph.
     * Null for a puzzle reopened from history, where only the grid was kept.
     */
    val reports: List<CellReport>? = null,
    val overlay: OverlayMode = OverlayMode.NONE,
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val selectedCell: Int? = null,
) {
    // Computed once per state rather than once per read. Every one of these runs the
    // solver, and Compose asks for them again on every recomposition - including one per
    // tap on the photograph. The state is immutable, so caching is free of risk.
    val hint: Hint? by lazy { PuzzleLogic.hint(grid, hintStyle) }

    val status: Status by lazy { PuzzleLogic.status(grid) }

    val guidance: String? by lazy { PuzzleLogic.guidance(grid, overlay, hintStyle) }

    val legend: List<LegendKey>
        get() = PuzzleLogic.legend(computed, overlay, uncertainCells.isNotEmpty())

    private val computed: Overlay by lazy { PuzzleLogic.overlay(grid, overlay, hintStyle) }

    fun overlayDigits(): Map<Int, OverlayDigit> = computed.digits

    fun evidenceCells(): Set<Int> = computed.evidence

    fun show(mode: OverlayMode): PuzzleState =
        copy(overlay = if (overlay == mode) OverlayMode.NONE else mode, selectedCell = null)

    fun withCell(index: Int, digit: Int?, source: CellSource): PuzzleState {
        val cell = when {
            digit == null -> Cell.Empty
            source == CellSource.GIVEN -> Cell.given(digit)
            else -> Cell.guess(digit)
        }
        return copy(grid = grid.with(index, cell), uncertainCells = uncertainCells - index)
    }

    /** The user has looked at everything the reader flagged and is happy with it. */
    fun acceptReading(): PuzzleState = copy(uncertainCells = emptySet())
}
