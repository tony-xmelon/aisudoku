package io.github.tonyxmelon.aisudoku.app

import io.github.tonyxmelon.aisudoku.recognize.CellReading
import io.github.tonyxmelon.aisudoku.recognize.Ink

/**
 * What the reader made of one square, kept so the user can look at it.
 *
 * The classifier's own numbers, not a retelling: without them there is no way to tell a
 * confident misread from a coin flip, and those want different responses - one is a bug
 * worth a photograph, the other is just a hard cell to correct and move on from.
 */
data class CellReport(
    val ink: Ink,
    val digit: Int?,
    /** How likely the classifier thought its answer was, 0 to 1. */
    val confidence: Float,
    val runnerUp: Int?,
    val runnerUpConfidence: Float,
) {
    /** One line for the cell editor. */
    fun describe(): String = when (ink) {
        Ink.PRINTED -> "Read as a printed $digit, ${percent(confidence)} sure."
        Ink.ANSWER -> "Read as a handwritten $digit, ${percent(confidence)} sure."
        Ink.MARK -> "Read as pencilled candidate marks, and ignored."
        Ink.NONE -> "Read as empty - no ink found."
    }

    /** The second guess, when there was a real contest. */
    fun secondGuess(): String? {
        if (digit == null || runnerUp == null || runnerUpConfidence < 0.02f) return null
        return "Second guess: $runnerUp, ${percent(runnerUpConfidence)}."
    }

    private fun percent(value: Float) = "${Math.round(value * 100)}%"

    companion object {
        fun of(reading: CellReading): CellReport {
            val p = reading.probabilities
                ?: return CellReport(reading.ink, null, 1f, null, 0f)
            val ranked = p.indices.sortedByDescending { p[it] }
            return CellReport(
                ink = reading.ink,
                digit = ranked[0] + 1,
                confidence = p[ranked[0]],
                runnerUp = ranked[1] + 1,
                runnerUpConfidence = p[ranked[1]],
            )
        }
    }
}
