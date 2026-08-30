package io.github.tonyxmelon.aisudoku.app

import android.graphics.Bitmap
import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.AnswerCheck
import io.github.tonyxmelon.aisudoku.solver.AnswerChecker
import io.github.tonyxmelon.aisudoku.solver.ExplainedHintEngine
import io.github.tonyxmelon.aisudoku.solver.Hint
import io.github.tonyxmelon.aisudoku.solver.RevealHintEngine
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver

/** Which help the user has asked for. */
enum class OverlayMode { NONE, HINT, CHECK, SOLUTION }

/** Whether a hint names the technique or just gives the digit. */
enum class HintStyle { REVEAL, EXPLAIN }

/**
 * Everything the puzzle screen shows.
 *
 * Recomputed from the grid on every change, because every correction can alter the
 * solution and stale help is worse than none.
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

    val solution: Grid? get() = (solve as? SolveResult.Unique)?.solution

    val check: AnswerCheck get() = AnswerChecker.check(grid)

    val hint: Hint?
        get() = when (hintStyle) {
            HintStyle.REVEAL -> RevealHintEngine.nextHint(grid)
            HintStyle.EXPLAIN -> ExplainedHintEngine.nextHint(grid)
        }

    /** Cells the overlay should draw, and in what role. */
    fun overlayDigits(): Map<Int, OverlayDigit> {
        val out = mutableMapOf<Int, OverlayDigit>()
        when (overlay) {
            OverlayMode.NONE -> Unit

            OverlayMode.SOLUTION -> solution?.let { solved ->
                for (i in 0 until 81) {
                    if (!grid[i].isFilled) {
                        out[i] = OverlayDigit(solved[i].digit!!, OverlayRole.FILLED)
                    }
                }
            }

            OverlayMode.CHECK -> (check as? AnswerCheck.Checked)?.let { checked ->
                for (i in checked.correct) out[i] = OverlayDigit(grid[i].digit!!, OverlayRole.CORRECT)
                for (i in checked.incorrect) out[i] = OverlayDigit(grid[i].digit!!, OverlayRole.INCORRECT)
            }

            OverlayMode.HINT -> {
                val h = hint
                val target = when (h) {
                    is Hint.Reveal -> h.index to h.digit
                    is Hint.Explained -> h.answer?.let { it.index to it.digit }
                    null -> null
                }
                if (target != null && (hintStyle == HintStyle.REVEAL || revealedHintDigit)) {
                    out[target.first] = OverlayDigit(target.second, OverlayRole.HINT)
                }
            }
        }
        return out
    }

    /** Cells the overlay should highlight without drawing a digit. */
    fun highlightedCells(): Set<Int> = when (overlay) {
        OverlayMode.HINT -> (hint as? Hint.Explained)?.supportingCells ?: emptySet()
        else -> emptySet()
    }

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

enum class OverlayRole { FILLED, CORRECT, INCORRECT, HINT }

data class OverlayDigit(val digit: Int, val role: OverlayRole)
