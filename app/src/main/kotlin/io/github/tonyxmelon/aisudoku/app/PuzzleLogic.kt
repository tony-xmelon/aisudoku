package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.model.Coordinates
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.AnswerCheck
import io.github.tonyxmelon.aisudoku.solver.AnswerChecker
import io.github.tonyxmelon.aisudoku.solver.Deduction
import io.github.tonyxmelon.aisudoku.solver.ExplainedHintEngine
import io.github.tonyxmelon.aisudoku.solver.Hint
import io.github.tonyxmelon.aisudoku.solver.RevealHintEngine
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import io.github.tonyxmelon.aisudoku.solver.Techniques
import io.github.tonyxmelon.aisudoku.solver.Walkthrough

/** Which help the user has asked for. Exactly one at a time, so nothing has to blend. */
enum class OverlayMode { NONE, HINT, CHECK, SOLUTION, READING, LESSON }

/** Whether a hint names the technique behind it or only gives the digit. */
enum class HintStyle { REVEAL, EXPLAIN }

/**
 * What a digit drawn on the photograph means.
 *
 * No two roles are ever drawn on the same cell. The first version tinted uncertain cells
 * yellow and wrong ones red, so a cell that was both came out orange, and orange meant
 * nothing. Doubt is now carried by the confidence bar rather than by the square.
 */
enum class OverlayRole { SOLUTION, CORRECT, INCORRECT, HINT }

data class OverlayDigit(val digit: Int, val role: OverlayRole)

/** What the overlay should draw, in cell coordinates. */
data class Overlay(
    val digits: Map<Int, OverlayDigit>,
    /** Cells that are evidence for the current hint or step. */
    val evidence: Set<Int>,
    /** The one square being pointed at: ringed, but not necessarily answered. */
    val focus: Int? = null,
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

    /**
     * How far an explained hint can be pushed before it simply gives the answer.
     *
     * A hint that hands over a digit teaches nothing, and one that says "work it out"
     * helps nobody. So it is a staircase: the region, then the technique, then the
     * square, then the digit. Each press of Hint goes down one tread, and most of the
     * time the user has what they needed before the bottom.
     */
    const val HINT_DEPTHS = 4

    /** Which layer is showing, and how far its hint has been pushed. */
    data class LayerPress(val mode: OverlayMode, val hintDepth: Int)

    /**
     * What pressing a layer's own button should do next.
     *
     * Pressing Hint again walks down the staircase, so asking for more help needs no
     * second control and no explanation of where to find it. Once there is nothing left
     * to reveal, that same press turns the layer off, which is what a second press does
     * everywhere else.
     */
    fun press(
        showing: OverlayMode,
        pressed: OverlayMode,
        hintDepth: Int,
        style: HintStyle,
    ): LayerPress = when {
        showing != pressed -> LayerPress(pressed, 0)

        pressed == OverlayMode.HINT && style == HintStyle.EXPLAIN &&
            hintDepth < HINT_DEPTHS - 1 -> LayerPress(pressed, hintDepth + 1)

        else -> LayerPress(OverlayMode.NONE, 0)
    }

    fun hint(grid: Grid, style: HintStyle): Hint? = when (style) {
        HintStyle.REVEAL -> RevealHintEngine.nextHint(grid)
        HintStyle.EXPLAIN -> ExplainedHintEngine.nextHint(grid)
    }

    /** True when there is still something to hint at. Drives whether the button is live. */
    fun canHint(grid: Grid, style: HintStyle): Boolean = hint(grid, style) != null

    fun overlay(
        grid: Grid,
        mode: OverlayMode,
        style: HintStyle,
        hintDepth: Int = HINT_DEPTHS - 1,
        walkthrough: Walkthrough? = null,
        lessonStep: Int = 0,
    ): Overlay {
        val digits = mutableMapOf<Int, OverlayDigit>()
        var evidence = emptySet<Int>()
        var focus: Int? = null

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

            // Drawn from the readings rather than from the grid, so it can show what was
            // thrown away as well as what was kept.
            OverlayMode.READING -> Unit

            OverlayMode.HINT -> hint(grid, style)?.let { h ->
                if (style == HintStyle.REVEAL) {
                    // Asked for the digit and nothing else. Highlighting a region as well
                    // would be answering a question this style exists to skip.
                    digits[h.index] = OverlayDigit(h.digit, OverlayRole.HINT)
                    return@let
                }

                val supporting = (h as? Hint.Explained)?.supportingCells.orEmpty() - h.index

                // A naked single's evidence is the square itself, which points at nothing
                // once the square is taken out. Its box is the honest answer to "where
                // should I be looking" - until the ring goes round the square, which says
                // it better.
                evidence = when {
                    supporting.isNotEmpty() -> supporting
                    hintDepth <= 1 ->
                        Coordinates.boxIndices[Coordinates.boxOf(h.index)].toSet() - h.index

                    else -> emptySet()
                }
                if (hintDepth >= 2) focus = h.index
                if (hintDepth >= HINT_DEPTHS - 1) {
                    digits[h.index] = OverlayDigit(h.digit, OverlayRole.HINT)
                }
            }

            OverlayMode.LESSON -> walkthrough?.takeIf { it.steps.isNotEmpty() }?.let { route ->
                val at = lessonStep.coerceIn(0, route.steps.size - 1)
                // On a route, everything up to and including this step, so the board
                // fills in as it is walked and each move is seen from the position it was
                // made in. When browsing one technique the steps are alternatives from a
                // single position, so only the one being looked at is drawn.
                val from = if (route.cumulative) 0 else at
                for (i in from..at) {
                    val step = route.steps[i] as? Deduction.Placement ?: continue
                    digits[step.index] = OverlayDigit(step.digit, OverlayRole.SOLUTION)
                }
                val step = route.steps[at]
                focus = (step as? Deduction.Placement)?.index
                evidence = step.supportingCells - setOfNotNull(focus)
            }
        }
        return Overlay(digits, evidence, focus)
    }

    /**
     * The key to whatever is on the photograph right now.
     *
     * Derived from the overlay rather than from the mode, so it names what is actually
     * drawn: no "Wrong" when nothing is wrong, and no "Why" when the hint had no
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

    /**
     * What the route ahead asks of you, said before you set off.
     *
     * The number of steps matters much less than the hardest one among them: that is the
     * technique worth going and reading about, and the reason a puzzle feels stuck.
     */
    fun outlook(walkthrough: Walkthrough?): String? {
        if (walkthrough == null || walkthrough.isEmpty) return null
        val steps = if (walkthrough.steps.size == 1) "one step" else "${walkthrough.steps.size} steps"
        val hardest = walkthrough.hardestTechnique?.lowercase() ?: "nothing unusual"
        return if (walkthrough.finishes) {
            "From here it is $steps, and the hardest thing you need is a $hardest."
        } else {
            "$steps can be reasoned out from here, the hardest being a $hardest. After " +
                "that this app runs out of techniques, and the rest needs one it has not " +
                "been taught."
        }
    }

    /** The sentence under the controls, explaining whatever is on screen right now. */
    fun guidance(
        grid: Grid,
        mode: OverlayMode,
        style: HintStyle,
        hintDepth: Int = HINT_DEPTHS - 1,
        walkthrough: Walkthrough? = null,
        lessonStep: Int = 0,
    ): String? = when (mode) {
        OverlayMode.NONE -> null

        OverlayMode.SOLUTION -> "Blue digits are the solution. Tap any square to correct what was read."

        OverlayMode.READING -> "Grey squares hold pencil marks, which the app ignores. The " +
            "bar under a digit is how sure it was. Tap a square for the detail."

        OverlayMode.CHECK ->
            if ((AnswerChecker.check(grid) as? AnswerCheck.Checked)?.incorrect.isNullOrEmpty()) {
                "Everything you have written so far is right."
            } else {
                "A red square shows the digit the app read there. If that is not what you " +
                    "wrote, tap the square to fix it."
            }

        OverlayMode.LESSON -> walkthrough?.takeIf { it.steps.isNotEmpty() }?.let { route ->
            val step = route.steps[lessonStep.coerceIn(0, route.steps.size - 1)]
            listOfNotNull(
                "${step.technique}. ${step.explanation}",
                Techniques.byName(step.technique)?.howTo,
            ).joinToString("\n\n")
        } ?: "Nothing more here can be reasoned out by the techniques this app knows."

        OverlayMode.HINT -> hintGuidance(grid, style, hintDepth)
    }

    /**
     * The staircase, one tread at a time.
     *
     * Every rung but the last says another press will say more, because a hint the user
     * does not know can be pushed further is a hint that gave everything away at once.
     */
    private fun hintGuidance(grid: Grid, style: HintStyle, depth: Int): String {
        val hint = hint(grid, style) ?: return "Nothing left to work out."
        if (style == HintStyle.REVEAL || hint !is Hint.Explained) {
            return "Row ${hint.index / 9 + 1}, column ${hint.index % 9 + 1}."
        }
        val technique = Techniques.byName(hint.technique)
        val more = "\n\nPress Hint again for more."
        return when (depth) {
            0 -> "There is a move to be found in the highlighted box.$more"

            1 -> listOfNotNull(
                "${hint.technique}. ${technique?.rule.orEmpty()}".trim(),
                technique?.howTo,
            ).joinToString("\n\n") + more

            2 -> ("It is this square. ${hint.technique}." +
                (technique?.rule?.let { " $it" } ?: "")) + more

            else -> "${hint.technique}. ${hint.explanation}"
        }
    }
}
