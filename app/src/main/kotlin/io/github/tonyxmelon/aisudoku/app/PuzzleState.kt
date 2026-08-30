package io.github.tonyxmelon.aisudoku.app

import android.graphics.Bitmap
import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.AnswerCheck
import io.github.tonyxmelon.aisudoku.solver.AnswerChecker
import io.github.tonyxmelon.aisudoku.solver.Hint
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver

/**
 * Everything the puzzle screen shows.
 *
 * The rules live in [PuzzleLogic], which knows nothing about Android and is therefore
 * testable without a device. This holds the photograph and what the user has touched.
 */
data class PuzzleState(
    val photo: Bitmap,
    val grid: Grid,
    val uncertainCells: Set<Int>,
    val message: String?,
    val overlay: OverlayMode = OverlayMode.NONE,
    val hintStyle: HintStyle = HintStyle.EXPLAIN,
    val selectedCell: Int? = null,
    val revealedHintDigit: Boolean = false,
) {
    val solve: SolveResult get() = Solver.solve(grid)

    val check: AnswerCheck get() = AnswerChecker.check(grid)

    val hint: Hint? get() = PuzzleLogic.hint(grid, hintStyle)

    val status: String get() = PuzzleLogic.status(grid)

    private val computed: Overlay
        get() = PuzzleLogic.overlay(grid, overlay, hintStyle, revealedHintDigit)

    fun overlayDigits(): Map<Int, OverlayDigit> = computed.digits

    fun highlightedCells(): Set<Int> = computed.highlighted

    fun withCell(index: Int, digit: Int?, source: CellSource): PuzzleState {
        val cell = when {
            digit == null -> Cell.Empty
            source == CellSource.GIVEN -> Cell.given(digit)
            else -> Cell.guess(digit)
        }
        return copy(
            grid = grid.with(index, cell),
            uncertainCells = uncertainCells - index,
            revealedHintDigit = false,
        )
    }
}
