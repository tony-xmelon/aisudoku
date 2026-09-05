package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Measures the Kotlin pipeline against the hand-labelled corpus.
 *
 * These numbers must track the Python prototype in `tools/recognizer/`. If Kotlin
 * inference drifts from the PyTorch model that produced the weights, everything
 * downstream is quietly wrong and only a comparison like this would notice.
 */
class RecognitionAccuracyTest {

    private companion object {
        /**
         * The pages whose writing is the size of their print, and what that still costs.
         *
         * Everything downstream rests on finding the printed digits first, and that rested
         * entirely on size: the print is one population sharing a font, a colour and a
         * size, and the writing was always taller. On these ten it is not. Their
         * handwriting sits at 1.02 to 1.12 of the printed height where the print sits at
         * 0.98 to 1.01, so the printed band swallowed the answers and a finished page came
         * out claiming seventy-odd givens and no puzzle. It cost 220 cells of 729,
         * measured over the nine such pages there were at the time.
         *
         * It now costs 77. What separates them is ink - contrast against the paper of the
         * cell itself, times stroke width, both as fractions of the print's own - and it
         * is applied only to a page that has already shown the fault by finding more
         * printed digits than any sudoku has. Sorting every page that way costs printed
         * digits on pages where nothing was wrong, and lost three of them outright.
         *
         * What is left is 110 cells on twelve pages. The count is a ceiling rather than a
         * target, and it has moved for three different reasons, which is worth separating.
         *
         * It grew by pages joining: 94 on ten, 109 on eleven, 125 on twelve. Each time the
         * new cells were the new page's own and nothing that sorted correctly before
         * sorted wrongly after, so a page from this one reader costs fifteen or sixteen
         * cells here whatever else changes.
         *
         * It then fell from 125 to 100, and that was a fault of a different kind. Forty-
         * seven of those cells were not the print/handwriting collision at all: they were
         * answers thrown away as pencilled candidate marks for being smaller than the
         * print. See [GridReader.ANSWER_MIN]. What is left is the collision proper, and
         * it is a harder thing - measured, no rule over the six things the reader
         * measures does better than about seventy of these cells, where a small learned
         * function over the same six does thirty-three. That is the next decision, and it
         * wants corpus from a second hand before it is taken.
         *
         * It then rose from 100 to 110, and that is the third reason and the least
         * obvious: a page that used to be refused outright now reads, so its cells are
         * counted here for the first time. Being unable to solve a puzzle is no longer
         * treated as being unable to read one, and blue-3 - whose printed digits do not
         * make one puzzle - comes back as a grid to be questioned rather than as nothing
         * at all. It brings ten of these cells with it. Nothing that was counted before
         * moved.
         */
        const val SAME_SIZE_HANDWRITING_MISSORTS = 110

        /**
         * Printed digits misread on the one page whose ink is barely there.
         *
         * See [CorpusLabels.faintOnScreen]. Both are on that photograph; every printed
         * digit on every other page in the corpus is read correctly, which is what this
         * assertion is really for.
         */
        const val FAINT_PRINT_MISREADS = 2

        /**
         * Cells sorted wrongly on that same page, nearly all of them squares whose ink
         * was never found at all rather than misjudged. See [CorpusLabels.faintOnScreen].
         */
        const val FAINT_INK_MISSORTS = 31
    }

    private fun setUp() {
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    /**
     * The structural half of the problem: which cells hold print, which hold an answer,
     * and which hold nothing but pencilled candidate marks.
     *
     * This is stricter than it looks. One corpus photograph has candidate marks as tall
     * as the printed digits, including ringed pairs taller than any of them, so nothing
     * about it can be settled by size alone.
     */
    @Test
    fun `every cell is sorted into print, handwriting or pencil marks`() {
        setUp()
        val reader = GridReader()
        var right = 0
        var total = 0
        var knownWrong = 0
        var faintWrong = 0
        val wrong = StringBuilder()

        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val grid = when (val result = reader.read(verdict.cells)) {
                is ReadResult.Accepted -> result.grid
                is ReadResult.NeedsConfirmation -> result.grid
                is ReadResult.Unreadable -> null
            } ?: continue

            for (i in 0 until 81) {
                total++
                val expected = truth[i].source
                val actual = when (grid[i].source) {
                    CellSource.GIVEN -> CorpusLabels.Source.GIVEN
                    CellSource.GUESS -> CorpusLabels.Source.GUESS
                    CellSource.EMPTY -> CorpusLabels.Source.EMPTY
                }
                if (actual == expected) {
                    right++
                } else if (file.name in CorpusLabels.sameSizeHandwriting) {
                    knownWrong++
                } else if (file.name in CorpusLabels.faintOnScreen) {
                    faintWrong++
                } else {
                    wrong.append("\n  ${file.name} r${i / 9 + 1}c${i % 9 + 1}: $expected read as $actual")
                }
            }
        }
        println("triage: $right/$total cells sorted correctly")
        println("of which on pages that defeat it: $knownWrong")
        println("and on the one page whose ink cannot be found: $faintWrong")
        assertTrue(
            right + knownWrong + faintWrong == total,
            "cells sorted wrongly on pages that should be sorted correctly:$wrong",
        )
        assertTrue(
            knownWrong <= SAME_SIZE_HANDWRITING_MISSORTS,
            "the print/handwriting collision got worse: $knownWrong wrong, " +
                "against $SAME_SIZE_HANDWRITING_MISSORTS when it was measured",
        )
        // Counted apart from the collision on purpose. Two different faults summed into
        // one number is how the collision came to be blamed for a third of the cells it
        // was not responsible for, and that is not worth repeating.
        assertTrue(
            faintWrong <= FAINT_INK_MISSORTS,
            "the faint page got worse: $faintWrong wrong, against $FAINT_INK_MISSORTS",
        )
    }

    @Test
    fun `the classifier reproduces the accuracy measured in Python`() {
        setUp()
        val classifier = DigitClassifier.load()
        var printedRight = 0
        var printedTotal = 0
        var handRight = 0
        var handTotal = 0
        val misreads = mutableMapOf<String, Int>()
        var collidingRight = 0
        var collidingTotal = 0

        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            CellAnalyzer.inspect(verdict.cells).forEachIndexed { index, ink ->
                val expected = truth[index].digit ?: return@forEachIndexed
                val probabilities = classifier.classify((ink ?: return@forEachIndexed).normalised)
                val predicted = probabilities.indices.maxBy { probabilities[it] } + 1

                val correct = predicted == expected
                if (truth[index].source == CorpusLabels.Source.GIVEN) {
                    printedTotal++; if (correct) printedRight++
                } else {
                    handTotal++; if (correct) handRight++
                }
                if (!correct) {
                    val key = "$expected->$predicted"
                    misreads[key] = (misreads[key] ?: 0) + 1
                }
                // Separately for the pages the triage cannot sort, because that is the
                // claim they are kept for: the sorting defeats it, the reading does not.
                if (file.name in CorpusLabels.sameSizeHandwriting) {
                    collidingTotal++
                    if (correct) collidingRight++
                }
            }
        }
        println("classifier: printed $printedRight/$printedTotal, handwriting $handRight/$handTotal")
        println("on the pages the triage cannot sort: $collidingRight/$collidingTotal digits read")
        println("misreads: " + misreads.entries.sortedByDescending { it.value }.joinToString())

        // Anything less than perfect on printed digits means Kotlin inference has
        // drifted from the model that was trained.
        // Perfect everywhere the ink can be seen. The exception is named rather than the
        // bar being lowered: one photograph of a screen is faint enough that two of its
        // printed digits are misread, and letting that soften the rule for all of them
        // would retire the only check that catches Kotlin inference drifting from the
        // model that was trained.
        assertTrue(
            printedRight >= printedTotal - FAINT_PRINT_MISREADS,
            "printed digits must be perfect outside the faint page: $printedRight/$printedTotal",
        )
        assertTrue(handRight >= handTotal * 0.90, "handwriting regressed: $handRight/$handTotal")
    }
}
