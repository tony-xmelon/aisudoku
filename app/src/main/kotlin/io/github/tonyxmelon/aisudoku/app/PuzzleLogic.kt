package io.github.tonyxmelon.aisudoku.app

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

enum class OverlayRole { FILLED, CORRECT, INCORRECT, HINT }

data class OverlayDigit(val digit: Int, val role: OverlayRole)

/** What the overlay should draw, in cell coordinates. */
data class Overlay(
    val digits: Map<Int, OverlayDigit>,
    val highlighted: Set<Int>,
)

/**
 * Everything the puzzle screen derives from a grid.
 *
 * Kept free of Android types on purpose: this is the part with rules in it, so it is
 * the part worth testing, and a `Bitmap` in the same class would have made that need
 * an instrumented device.
 */
object PuzzleLogic {

    fun hint(grid: Grid, style: HintStyle): Hint? = when (style) {
        HintStyle.REVEAL -> RevealHintEngine.nextHint(grid)
        HintStyle.EXPLAIN -> ExplainedHintEngine.nextHint(grid)
    }

    fun overlay(
        grid: Grid,
        mode: OverlayMode,
        style: HintStyle,
        revealedHintDigit: Boolean,
    ): Overlay {
        val digits = mutableMapOf<Int, OverlayDigit>()
        var highlighted = emptySet<Int>()

        when (mode) {
            OverlayMode.NONE -> Unit

            // Every cell that is not printed, so a finished puzzle shows the whole
            // answer rather than the handful of cells recognition happened to miss.
            OverlayMode.SOLUTION -> (Solver.solve(grid) as? SolveResult.Unique)?.let { solved ->
                for (i in 0 until 81) {
                    if (grid[i].source != CellSource.GIVEN) {
                        digits[i] = OverlayDigit(solved.solution[i].digit!!, OverlayRole.FILLED)
                    }
                }
            }

            OverlayMode.CHECK -> (AnswerChecker.check(grid) as? AnswerCheck.Checked)?.let { checked ->
                for (i in checked.correct) digits[i] = OverlayDigit(grid[i].digit!!, OverlayRole.CORRECT)
                // The digit carried here is what the app *read*, not what is on the
                // paper. Drawing it is the whole point: a misread then looks like a
                // misread instead of the app calling a correct answer wrong.
                for (i in checked.incorrect) digits[i] = OverlayDigit(grid[i].digit!!, OverlayRole.INCORRECT)
            }

            OverlayMode.HINT -> {
                when (val h = hint(grid, style)) {
                    is Hint.Reveal -> digits[h.index] = OverlayDigit(h.digit, OverlayRole.HINT)

                    is Hint.Explained -> {
                        highlighted = h.supportingCells
                        // An explained hint withholds the digit until asked a second time.
                        if (revealedHintDigit) {
                            h.answer?.let { digits[it.index] = OverlayDigit(it.digit, OverlayRole.HINT) }
                        }
                    }

                    null -> Unit
                }
            }
        }
        return Overlay(digits, highlighted)
    }

    /** One line describing the state of the puzzle, for the status area. */
    fun status(grid: Grid): String = when (Solver.solve(grid)) {
        is SolveResult.Unique -> {
            val checked = AnswerChecker.check(grid) as? AnswerCheck.Checked
            val wrong = checked?.incorrect?.size ?: 0
            val empty = (0 until 81).count { !grid[it].isFilled }
            when {
                // The number shown on a red cell is what the app read. Saying so is what
                // stops a misread looking like the app calling a right answer wrong.
                wrong > 0 -> {
                    val cells = if (wrong == 1) "1 cell disagrees" else "$wrong cells disagree"
                    "$cells with the solution. The number shown on a red cell is what the " +
                        "app read - if that is not what you wrote, tap it to fix."
                }

                empty > 0 -> {
                    val cells = if (empty == 1) "1 cell" else "$empty cells"
                    "Read correctly so far, with $cells still empty."
                }
                else -> "Solved, and every answer is right."
            }
        }

        is SolveResult.None -> "These digits do not make a solvable puzzle - fix a cell or retake."
        is SolveResult.Multiple -> "More than one solution, so a printed digit was probably missed."
    }
}
