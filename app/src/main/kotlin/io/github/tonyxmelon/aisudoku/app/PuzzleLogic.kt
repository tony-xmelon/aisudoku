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
enum class OverlayRole {
    SOLUTION,
    CORRECT,
    INCORRECT,
    HINT,

    /**
     * A digit the user typed in themselves.
     *
     * It is on no photograph, so unless it is drawn the square looks exactly as empty
     * after answering it as before. Drawn the way the reading layer draws handwriting,
     * because that is what it is - handwriting, just entered with a thumb.
     */
    WRITTEN,
}

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

/**
 * A run of consecutive steps that all use the same technique.
 *
 * What the route's progress line is drawn from. Sixty steps is far too many to draw one
 * mark each - the marks come out finer than a fingertip and say nothing about what they
 * are. Grouped into runs there are usually a dozen or so, each wide enough to hit, and
 * the shape of the puzzle becomes visible: a long block of eliminations is where it is
 * stuck, a run of naked singles is where it opens up.
 */
data class Chapter(val technique: String, val from: Int, val count: Int) {
    val until: Int get() = from + count
}

/** How worried the status line should look. */
enum class Tone { NEUTRAL, GOOD, BAD }

data class Status(val text: String, val tone: Tone)

/**
 * What to say under the photograph, split into the parts the screen lays out differently.
 *
 * One string would be simpler, and it is what this was: the pane joined the technique's
 * name, its explanation, the whole of its how-to and what the step does into a single
 * paragraph. On a phone that paragraph is taller than the space under the grid, so all
 * anyone saw was its first two lines - the least useful two, because they named the
 * technique the key already names.
 */
data class Guidance(
    /** Why this move is available here. The part worth reading first. */
    val body: String,
    /**
     * How to hunt for this technique on paper, in general.
     *
     * Held back behind a control rather than printed: it runs to paragraphs, it is the
     * same words every time the technique comes round, and it is not what the reader
     * wants while looking at one square.
     */
    val howTo: String? = null,
    /**
     * What this step does to the board, said last.
     *
     * Last because it is a summary, and because an elimination that fills nothing in
     * looks like a failed move until the reasoning above it has been read.
     */
    val effect: String? = null,
)

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
        /** Squares the user typed in, which appear on no photograph. */
        entered: Set<Int> = emptySet(),
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

                // Each rung has to change what is on screen, or pressing again looks like
                // nothing happened - which is what it did. The first rung answers "where
                // should I look" with the box; every rung after it shows the evidence,
                // which is a different set of squares.
                evidence = if (hintDepth == 0 || supporting.isEmpty()) {
                    Coordinates.boxIndices[Coordinates.boxOf(h.index)].toSet() - h.index
                } else {
                    supporting
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

        // What the user has written, wherever the layer on top has not already said
        // something about that square. Check and Solve both draw every answered square
        // already; the reading layer draws it from the grid when the reading is gone.
        // The rest - including no layer at all - showed nothing, so answering a square
        // looked exactly like not answering it.
        if (mode != OverlayMode.READING) {
            for (index in entered) {
                val digit = grid[index].digit ?: continue
                if (grid[index].source == CellSource.GIVEN) continue
                if (index in digits) continue
                digits[index] = OverlayDigit(digit, OverlayRole.WRITTEN)
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
        if (OverlayRole.WRITTEN in roles && mode != OverlayMode.READING) keys += LegendKey.WRITTEN
        if (overlay.evidence.isNotEmpty()) keys += LegendKey.EVIDENCE
        if (hasUncertain) keys += LegendKey.UNCERTAIN
        return keys
    }

    /** How many squares are still empty, over how many there are in a grid. */
    fun progress(grid: Grid): String =
        "${Coordinates.CELL_COUNT - grid.filledCount}/${Coordinates.CELL_COUNT}"

    /**
     * News about the puzzle, or nothing at all.
     *
     * Null when there is none, which is most of the time. It used to say "46 cells to go"
     * in that case, which is a number rather than news: it cost a line of the pane on
     * every screen, and the pane is the scarcest space in the app. That number now sits
     * in small type under the grid, where it is read at a glance and costs nothing.
     */
    fun status(grid: Grid): Status? = when (Solver.solve(grid)) {
        is SolveResult.Unique -> {
            val wrong = (AnswerChecker.check(grid) as? AnswerCheck.Checked)?.incorrect?.size ?: 0
            when {
                // Whether an answer is right is the Check button's business, and marking
                // one wrong the moment it is written turns every square into a test the
                // app grades. It stayed silent when you were right and spoke up when you
                // were not, which is a worse way to be told than being told.
                !grid.isComplete -> null

                wrong == 0 -> Status("Solved, and every answer is right.", Tone.GOOD)

                // Finished is the one moment correctness is worth saying unasked: there
                // is nothing left to work on, so "not yet" is the whole news.
                wrong == 1 -> Status(
                    "Every square is filled, but one answer disagrees with the solution.",
                    Tone.BAD,
                )

                else -> Status(
                    "Every square is filled, but $wrong answers disagree with the solution.",
                    Tone.BAD,
                )
            }
        }

        is SolveResult.None ->
            Status("These printed digits do not make a solvable puzzle.", Tone.BAD)

        is SolveResult.Multiple ->
            Status("More than one solution, so a printed digit was missed.", Tone.BAD)
    }

    /**
     * What to call the evidence squares in the key.
     *
     * The key used to label them "Why", which says only that they are a reason - and the
     * technique's name was then printed again at the top of the pane, where it was the
     * first thing pushed off the bottom. Naming the swatch after the technique says the
     * same thing in a place that was being wasted, and gives the pane its line back.
     *
     * Deliberately not the technique on the first rung of a hint: that rung highlights the
     * box precisely so as not to name the technique yet, and a key that named it would
     * hand over a tread the user has not asked for.
     */
    fun evidenceLabel(
        grid: Grid,
        mode: OverlayMode,
        style: HintStyle,
        hintDepth: Int = HINT_DEPTHS - 1,
        walkthrough: Walkthrough? = null,
        lessonStep: Int = 0,
    ): String? = when (mode) {
        OverlayMode.LESSON -> walkthrough?.steps?.getOrNull(
            lessonStep.coerceIn(0, maxOf(0, walkthrough.steps.size - 1))
        )?.technique

        OverlayMode.HINT -> when {
            style == HintStyle.REVEAL -> null
            hintDepth == 0 -> "Box"
            else -> (hint(grid, style) as? Hint.Explained)?.technique
        }

        else -> null
    }

    /** The route's steps grouped into runs of one technique, in order. */
    fun chapters(walkthrough: Walkthrough?): List<Chapter> {
        val out = mutableListOf<Chapter>()
        for ((i, step) in walkthrough?.steps.orEmpty().withIndex()) {
            val last = out.lastOrNull()
            if (last != null && last.technique == step.technique) {
                out[out.lastIndex] = last.copy(count = last.count + 1)
            } else {
                out += Chapter(step.technique, i, 1)
            }
        }
        return out
    }

    /**
     * What the route ahead asks of you, said before you set off.
     *
     * The number of steps matters much less than the hardest one among them: that is the
     * technique worth going and reading about, and the reason a puzzle feels stuck.
     */
    fun outlook(walkthrough: Walkthrough?): String? {
        if (walkthrough == null || walkthrough.isEmpty) return null
        val total = walkthrough.steps.size
        val places = walkthrough.steps.count { it is Deduction.Placement }
        val hardest = walkthrough.hardestTechnique?.lowercase() ?: "nothing unusual"

        // What the steps DO matters more than how many there are. A run of eliminations
        // reads as a list of unrelated facts unless it is said in advance that none of
        // them fill a square in, and that clearing the way is the whole job.
        val shape = when {
            places == 0 -> "None of them puts a digit in a square: they clear candidates " +
                "out of the way, which is how a hard puzzle is opened up."

            places == total -> "Every one of them fills a square in."

            else -> "$places of them fill a square in; the rest clear candidates out of " +
                "the way first."
        }
        val ending = when {
            !walkthrough.finishes ->
                "They do not reach the end: something here has defeated the app entirely."

            walkthrough.triedOut == 0 -> "That is the rest of the puzzle, start to finish."

            walkthrough.triedOut == 1 -> "That is the rest of the puzzle. One square in it " +
                "yields to no technique at all and has to be settled by trying its " +
                "candidates out, which is what makes this a hard one."

            else -> "That is the rest of the puzzle. ${walkthrough.triedOut} squares in it " +
                "yield to no technique at all and have to be settled by trying their " +
                "candidates out, which is what makes this a hard one."
        }
        return "$total steps can be reasoned out from here, the hardest a $hardest. " +
            "$shape $ending"
    }

    /** What one step of the route actually does, said after the reasoning behind it. */
    private fun effect(step: Deduction): String = when (step) {
        is Deduction.Placement ->
            "Put ${step.digit} in row ${step.index / 9 + 1}, column ${step.index % 9 + 1}."

        is Deduction.Elimination -> {
            val squares = if (step.fromCells.size == 1) "one square" else "${step.fromCells.size} squares"
            "This fills nothing in. It rules ${step.digit} out of $squares."
        }
    }

    /** What to say under the controls about whatever is on screen right now. */
    fun guidance(
        grid: Grid,
        mode: OverlayMode,
        style: HintStyle,
        hintDepth: Int = HINT_DEPTHS - 1,
        walkthrough: Walkthrough? = null,
        lessonStep: Int = 0,
    ): Guidance? = when (mode) {
        OverlayMode.NONE -> null

        OverlayMode.SOLUTION -> Guidance(
            "Blue digits are the solution. Tap any square to correct what was read."
        )

        OverlayMode.READING -> Guidance(
            "Grey squares hold pencil marks, which the app ignores. The bar under a digit " +
                "is how sure it was. Tap a square for the detail."
        )

        OverlayMode.CHECK -> Guidance(
            if ((AnswerChecker.check(grid) as? AnswerCheck.Checked)?.incorrect.isNullOrEmpty()) {
                "Everything you have written so far is right."
            } else {
                "A red square shows the digit the app read there. If that is not what you " +
                    "wrote, tap the square to fix it."
            }
        )

        // The technique is named by the key, so it is not named again here. What is left is
        // the part that is only true of this position, which is what the space is worth
        // spending on.
        OverlayMode.LESSON -> walkthrough?.takeIf { it.steps.isNotEmpty() }?.let { route ->
            val step = route.steps[lessonStep.coerceIn(0, route.steps.size - 1)]
            Guidance(
                body = step.explanation,
                howTo = Techniques.byName(step.technique)?.howTo,
                effect = effect(step),
            )
        } ?: Guidance("Nothing more here can be reasoned out by the techniques this app knows.")

        OverlayMode.HINT -> hintGuidance(grid, style, hintDepth)
    }

    /**
     * The staircase, one tread at a time.
     *
     * Every rung names exactly what the next press will give, so the user can stop at the
     * one that was enough for them.
     *
     * The long "how to hunt for this" text deliberately does not appear here. A hint is
     * read while stuck on one square, and a paragraph about the technique in general is
     * the wrong thing to meet at that moment; it is one tap away under Strategies.
     */
    private fun hintGuidance(grid: Grid, style: HintStyle, depth: Int): Guidance {
        val hint = hint(grid, style) ?: return Guidance("Nothing left to work out.")
        if (style == HintStyle.REVEAL || hint !is Hint.Explained) {
            return Guidance("Row ${hint.index / 9 + 1}, column ${hint.index % 9 + 1}.")
        }
        val rule = Techniques.byName(hint.technique)?.rule.orEmpty()
        return Guidance(
            when (depth) {
                0 -> "There is a square you can fill in the highlighted box.\n\n" +
                    "Press Hint again to be told which technique finds it."

                // The technique is named in the key, beside the colour of the very squares
                // that prove it, so what is said here is the rule rather than its name.
                1 -> "$rule\n\nThe highlighted squares are what proves it.\n\n" +
                    "Press Hint again to be shown which square."

                2 -> "It is the ringed square.\n\nPress Hint again for the digit."

                else -> hint.explanation
            }
        )
    }
}
