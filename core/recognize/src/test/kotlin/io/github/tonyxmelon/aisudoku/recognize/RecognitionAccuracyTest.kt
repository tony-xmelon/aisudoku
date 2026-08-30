package io.github.tonyxmelon.aisudoku.recognize

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
 * The numbers here must track the Python prototype in `tools/recognizer/`. If Kotlin
 * inference drifts from the PyTorch model that produced the weights, everything
 * downstream is quietly wrong, and only a comparison like this would notice.
 *
 * Python measured: printed givens 100%, handwriting 84.5%, triage 143/143 givens and
 * 84/84 guesses found with one false positive in 259 empty cells.
 */
class RecognitionAccuracyTest {

    private fun setUp() {
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    @Test
    fun `triage finds every digit and almost no empty cell`() {
        setUp()
        var givenFound = 0
        var givenTotal = 0
        var guessFound = 0
        var guessTotal = 0
        var emptyMisread = 0
        var emptyTotal = 0

        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            verdict.cells.forEachIndexed { index, cell ->
                val analysis = CellAnalyzer.analyse(cell)
                when (truth[index].source) {
                    CorpusLabels.Source.GIVEN -> {
                        givenTotal++; if (analysis.hasDigit) givenFound++
                    }
                    CorpusLabels.Source.GUESS -> {
                        guessTotal++; if (analysis.hasDigit) guessFound++
                    }
                    CorpusLabels.Source.EMPTY -> {
                        emptyTotal++; if (analysis.hasDigit) emptyMisread++
                    }
                }
            }
        }
        println("triage: givens $givenFound/$givenTotal, guesses $guessFound/$guessTotal, " +
            "empty misread $emptyMisread/$emptyTotal")

        assertTrue(givenFound == givenTotal, "missed a printed digit: $givenFound/$givenTotal")
        assertTrue(guessFound >= guessTotal * 0.95, "missed handwriting: $guessFound/$guessTotal")
        assertTrue(emptyMisread <= emptyTotal * 0.03, "too many empty cells read as digits: $emptyMisread")
    }

    @Test
    fun `the classifier reproduces the accuracy measured in Python`() {
        setUp()
        val classifier = DigitClassifier.load()
        var givenRight = 0
        var givenTotal = 0
        var guessRight = 0
        var guessTotal = 0
        val misreads = mutableMapOf<String, Int>()

        for (file in CorpusFixtures.photos) {
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            verdict.cells.forEachIndexed { index, cell ->
                val expected = truth[index].digit ?: return@forEachIndexed
                val analysis = CellAnalyzer.analyse(cell)
                val normalised = analysis.normalised ?: return@forEachIndexed
                val probabilities = classifier.classify(normalised)
                val predicted = probabilities.indices.maxBy { probabilities[it] } + 1

                val correct = predicted == expected
                if (truth[index].source == CorpusLabels.Source.GIVEN) {
                    givenTotal++; if (correct) givenRight++
                } else {
                    guessTotal++; if (correct) guessRight++
                }
                if (!correct) {
                    val key = "$expected->$predicted (${truth[index].source})"
                    misreads[key] = (misreads[key] ?: 0) + 1
                }
            }
        }
        println("classifier: givens $givenRight/$givenTotal, guesses $guessRight/$guessTotal")
        println("misreads: " + misreads.entries.sortedByDescending { it.value }.joinToString())

        // Python measured 100% on printed digits. Anything less means Kotlin inference
        // has drifted from the model that was trained.
        assertTrue(givenRight == givenTotal, "printed digits must be perfect: $givenRight/$givenTotal")
        assertTrue(guessRight >= guessTotal * 0.78, "handwriting regressed: $guessRight/$guessTotal")
    }
}
