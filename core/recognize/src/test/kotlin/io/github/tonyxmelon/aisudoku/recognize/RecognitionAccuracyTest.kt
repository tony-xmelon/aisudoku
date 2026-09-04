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
         * The pages where the triage cannot tell the writing from the print.
         *
         * Everything downstream rests on finding the printed digits first, and that rests
         * on them being the one population on the page that shares a font, a colour and a
         * size. On these five the reader wrote at the size of the print, so the size half
         * of that is simply not true: their handwriting sits at 1.02 to 1.12 of the
         * printed height where the print itself sits at 0.98 to 1.01. The printed core
         * then swallows the answers, and a finished page comes out claiming seventy-odd
         * givens and no puzzle - which is why some of them are refused outright.
         *
         * Stroke width does separate them, cleanly, page by page: the print measures
         * 1.00 to 1.08 of the core and the ballpoint 0.66 to 0.75. It is the third of the
         * three properties and [Blob.strokeWidth] already measures it. But no single
         * threshold works across the corpus - requiring 0.80 rescues 90 of the 114
         * confusions and costs 76 real printed digits - because a printed 1 is as thin
         * for its height as any pen stroke. It wants a per-page comparison, like the one
         * that finds the printed core, and that is a change worth making carefully.
         *
         * The pages themselves are listed in [CorpusLabels.sameSizeHandwriting]. They are
         * not skipped here: their digits are still scored, and this count - what the
         * collision costs today, measured - is a ceiling rather than a target.
         */
        const val SAME_SIZE_HANDWRITING_MISSORTS = 122
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
            }
        }
        println("classifier: printed $printedRight/$printedTotal, handwriting $handRight/$handTotal")
        println("misreads: " + misreads.entries.sortedByDescending { it.value }.joinToString())

        // Anything less than perfect on printed digits means Kotlin inference has
        // drifted from the model that was trained.
        assertTrue(printedRight == printedTotal, "printed digits must be perfect: $printedRight/$printedTotal")
        assertTrue(handRight >= handTotal * 0.90, "handwriting regressed: $handRight/$handTotal")
    }
}
