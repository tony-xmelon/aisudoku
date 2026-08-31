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

/** Which help the user has asked for. Exactly one at a time, so nothing has to blend. */
enum class OverlayMode { NONE, HINT, CHECK, SOLUTION, READING }

/** Whether a hint names the technique behind it or only gives the digit. */
enum class HintStyle { REVEAL, EXPLAIN }

/**
 * What a digit drawn on the photograph means.
 *
 * No two roles are ever drawn on the same cell. The first version tinted uncertain cells
 * yellow and wrong ones red, so a cell that was both came out orange, and orange meant
 * nothing. Doubt is now drawn as a ring around the cell rather than a fill, which can sit
 * over any of these without inventing a new colour.
 */
enum class OverlayRole { SOLUTION, CORRECT, INCORRECT, HINT }

data class OverlayDigit(val digit: Int, val role: OverlayRole)

/** What the overlay should draw, in cell coordinates. */
data class Overlay(
    val digits: Map<Int, OverlayDigit>,
    /** Cells that are evidence for the current hint. */
    val evidence: Set<Int>,
)

/** One entry in the key shown under the photograph. */
enum class LegendKey { CORRECT, INCORRECT, SOLUTION, HINT, EVIDENCE, UNCERTAIN, PRINTED, WRITTEN, MARKS }

/** How worried the status line should look. */
enum class Tone { NEUTRAL, GOOD, BAD }

data class Status(val text: String, val tone: Tone)

/**
 * Everything the puzzle screen derives from a grid.
 *
 * Kept free of Android types on purpose: this is the part with rules in it, so it is the
 * part worth testing, and a `Bitmap` in the same class would have made that need a
 * device.
 */
object PuzzleLogic {

    fun hint(grid: Grid, style: HintStyle): Hint? = when (style) {
        HintStyle.REVEAL -> RevealHintEngine.nextHint(grid)
        HintStyle.EXPLAIN -> ExplainedHintEngine.nextHint(grid)
    }

    /** True when there is still something to hint at. Drives whether the button is live. */
    fun canHint(grid: Grid, style: HintStyle): Boolean = hint(grid, style) != null

    fun overlay(grid: Grid, mode: OverlayMode, style: HintStyle): Overlay {
        val digits = mutableMapOf<Int, OverlayDigit>()
        var evidence = emptySet<Int>()

        when (mode) {
            OverlayMode.NONE -> Unit

            // Every cell that is not printed, so a finished puzzle shows the whole answer
            // rather than the handful of cells recognition happened to miss.
            OverlayMode.SOLUTION -> (Solver.solve(grid) as? SolveResult.Unique)?.let { solved ->
                for (i in 0 until 81) {
                    if (grid[i].source != CellSource.GIVEN) {
                        digits[i] = OverlayDigit(solved.solution[i].digit!!, OverlayRole.SOLUTION)
                    }
                }
            }

            OverlayMode.CHECK -> (AnswerChecker.check(grid) as? AnswerCheck.Checked)?.let { checked ->
                for (i in checked.correct) digits[i] = OverlayDigit(grid[i].digit!!, OverlayRole.CORRECT)
                // The digit carried here is what the app *read*, not what is on the paper.
                // Drawing it is the whole point: a misread then looks like a misread
                // instead of the app calling a correct answer wrong.
                for (i in checked.incorrect) digits[i] = OverlayDigit(grid[i].digit!!, OverlayRole.INCORRECT)
            }

            // Drawn from the readings rather than from the grid, so it can show what
            // was thrown away as well as what was kept.
            OverlayMode.READING -> Unit

            OverlayMode.HINT -> hint(grid, style)?.let { h ->
                digits[h.index] = OverlayDigit(h.digit, OverlayRole.HINT)
                if (h is Hint.Explained) evidence = h.supportingCells - h.index
            }
        }
        return Overlay(digits, evidence)
    }

    /**
     * The key to whatever is on the photograph right now.
     *
     * Derived from the overlay rather than from the mode, so it names what is actually
     * drawn: no "Wrong" when nothing is wrong, and no "The reason" when the hint had no
     * technique behind it to point at.
     */
    fun legend(overlay: Overlay, mode: OverlayMode, hasUncertain: Boolean): List<LegendKey> {
        val keys = mutableListOf<LegendKey>()
        if (mode == OverlayMode.READING) {
            keys += listOf(LegendKey.PRINTED, LegendKey.WRITTEN, LegendKey.MARKS)
        }
        val roles = overlay.digits.values.mapTo(mutableSetOf()) { it.role }
        if (OverlayRole.CORRECT in roles) keys += LegendKey.CORRECT
        if (OverlayRole.INCORRECT in roles) keys += LegendKey.INCORRECT
        if (OverlayRole.SOLUTION in roles) keys += LegendKey.SOLUTION
        if (OverlayRole.HINT in roles) keys += LegendKey.HINT
        if (overlay.evidence.isNotEmpty()) keys += LegendKey.EVIDENCE
        if (hasUncertain) keys += LegendKey.UNCERTAIN
        return keys
    }

    /** One short line about the puzzle. Instructions belong next to the control they explain. */
    fun status(grid: Grid): Status = when (Solver.solve(grid)) {
        is SolveResult.Unique -> {
            val wrong = (AnswerChecker.check(grid) as? AnswerCheck.Checked)?.incorrect?.size ?: 0
            val empty = (0 until 81).count { !grid[it].isFilled }
            when {
                wrong > 0 -> Status(
                    if (wrong == 1) "1 answer disagrees with the solution."
                    else "$wrong answers disagree with the solution.",
                    Tone.BAD,
                )

                empty == 1 -> Status("One cell to go.", Tone.NEUTRAL)
                empty > 0 -> Status("$empty cells to go.", Tone.NEUTRAL)
                else -> Status("Solved, and every answer is right.", Tone.GOOD)
            }
        }

        is SolveResult.None ->
            Status("These printed digits do not make a solvable puzzle.", Tone.BAD)

        is SolveResult.Multiple ->
            Status("More than one solution, so a printed digit was missed.", Tone.BAD)
    }

    /** The sentence under the controls, explaining whatever is on screen right now. */
    fun guidance(grid: Grid, mode: OverlayMode, style: HintStyle): String? = when (mode) {
        OverlayMode.NONE -> null

        OverlayMode.SOLUTION -> "Blue digits are the solution. Tap any cell to correct what was read."

        OverlayMode.READING -> "Grey squares hold pencil marks, which the app ignores. The " +
            "bar under a digit is how sure it was. Tap a square for the detail."

        OverlayMode.CHECK ->
            if ((AnswerChecker.check(grid) as? AnswerCheck.Checked)?.incorrect.isNullOrEmpty()) {
                "Everything you have written so far is right."
            } else {
                "A red cell shows the digit the app read there. If that is not what you " +
                    "wrote, tap the cell to fix it."
            }

        OverlayMode.HINT -> when (val h = hint(grid, style)) {
            is Hint.Explained -> "${h.technique}. ${h.explanation}"
            is Hint.Reveal -> "Row ${h.index / 9 + 1}, column ${h.index % 9 + 1}."
            null -> "Nothing left to work out."
        }
    }
}
