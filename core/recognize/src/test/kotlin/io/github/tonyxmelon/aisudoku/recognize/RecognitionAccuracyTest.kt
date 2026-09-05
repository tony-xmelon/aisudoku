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
         * What is left is 125 cells on twelve pages, and they are the ones whose ink is
         * genuinely ambiguous: a pen bearing down as hard as the press did. The count is a
         * ceiling rather than a target.
         *
         * It has grown only by pages joining it: 94 on ten, then 109 when an eleventh was
         * taken in, then 125 when a twelfth was. Each time the new cells were the new
         * page's own and no cell on a page that sorted correctly before sorted wrongly
         * after. A page from this one reader costs fifteen or sixteen cells here whatever
         * else changes, which is worth knowing before reading a movement in this number as
         * a regression - and equally, a rise without a new page would be one.
         */
        const val SAME_SIZE_HANDWRITING_MISSORTS = 125
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
                } else {
                    wrong.append("\n  ${file.name} r${i / 9 + 1}c${i % 9 + 1}: $expected read as $actual")
                }
            }
        }
        println("triage: $right/$total cells sorted correctly")
        println("of which on pages that defeat it: $knownWrong")
        assertTrue(
            right + knownWrong == total,
            "cells sorted wrongly on pages that should be sorted correctly:$wrong",
        )
        assertTrue(
            knownWrong <= SAME_SIZE_HANDWRITING_MISSORTS,
            "the print/handwriting collision got worse: $knownWrong wrong, " +
                "against $SAME_SIZE_HANDWRITING_MISSORTS when it was measured",
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
        assertTrue(printedRight == printedTotal, "printed digits must be perfect: $printedRight/$printedTotal")
        assertTrue(handRight >= handTotal * 0.90, "handwriting regressed: $handRight/$handTotal")
    }
}
