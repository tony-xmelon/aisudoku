package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import io.github.tonyxmelon.aisudoku.vision.GrayImage
import kotlin.math.abs

/** How the reader judged one cell. */
data class CellReading(
    val index: Int,
    /** Probabilities for digits 1..9, or null when the cell holds no digit. */
    val probabilities: FloatArray?,
    val heightRatio: Double,
    val darkness: Double,
    val hadDiscardedMarks: Boolean,
) {
    val digit: Int? get() = probabilities?.let { p -> p.indices.maxBy { p[it] } + 1 }

    /** Gap between the two most likely digits. Small means the classifier is guessing. */
    val margin: Float
        get() {
            val p = probabilities ?: return 1f
            val sorted = p.sortedDescending()
            return sorted[0] - sorted[1]
        }

    override fun equals(other: Any?): Boolean = other is CellReading && other.index == index
    override fun hashCode(): Int = index
}

/** What the reader concluded about a whole photograph. */
sealed interface ReadResult {

    /** A grid the reader stands behind. */
    data class Accepted(val grid: Grid, val readings: List<CellReading>) : ReadResult

    /**
     * A grid, but with cells the reader is unsure of. Those are exactly the cells to
     * ask the user about — rejection and correction are the same mechanism at
     * different scales.
     */
    data class NeedsConfirmation(
        val grid: Grid,
        val readings: List<CellReading>,
        val uncertainCells: Set<Int>,
        val reason: String,
    ) : ReadResult

    /** Too little was read with confidence to be worth confirming cell by cell. */
    data class Unreadable(val reason: String, val uncertainCells: Set<Int>) : ReadResult
}

/**
 * Turns 81 cell images into a grid, and decides whether to stand behind it.
 *
 * Acceptance is judged on whether extraction actually succeeded, not on how the
 * photograph scored: image metrics reject usable photographs and pass unusable ones.
 * Blur is the clearest case — it *raises* the grid-detection score while destroying
 * legibility — so the verdict here comes from classifier margins, how much the solver
 * had to overturn, and whether the puzzle has a unique solution.
 */
class GridReader(private val classifier: DigitClassifier = DigitClassifier.load()) {

    fun read(cells: List<GrayImage>): ReadResult {
        require(cells.size == 81) { "expected 81 cells but got ${cells.size}" }

        val threshold = digitHeightThreshold(CellAnalyzer.blobHeights(cells))
        val analyses = cells.map { CellAnalyzer.analyse(it, threshold) }
        val readings = analyses.mapIndexed { index, analysis ->
            CellReading(
                index = index,
                probabilities = analysis.normalised?.let { classifier.classify(it) },
                heightRatio = analysis.digit?.heightRatio ?: 0.0,
                darkness = analysis.digit?.darkness ?: 255.0,
                hadDiscardedMarks = analysis.discardedMarks > 0,
            )
        }

        val filled = readings.filter { it.digit != null }
        if (filled.size < MIN_FILLED_CELLS) {
            return ReadResult.Unreadable(
                "Could not read enough of the grid to be sure.",
                filled.map { it.index }.toSet(),
            )
        }

        val printed = printedCells(readings)
        val grid = assemble(readings, printed)

        val weak = readings
            .filter { it.digit != null && it.margin < CONFIDENT_MARGIN }
            .map { it.index }
            .toSet()

        return when (val solved = Solver.solve(grid)) {
            is SolveResult.Unique ->
                if (weak.isEmpty()) {
                    ReadResult.Accepted(grid, readings)
                } else {
                    ReadResult.NeedsConfirmation(
                        grid, readings, weak,
                        "Check the highlighted cells - they were hard to read.",
                    )
                }

            else -> repair(readings, printed, grid, weak, solved)
        }
    }

    /**
     * Which cells hold printed digits.
     *
     * Decided by size, not by darkness. Measured on the corpus, printed digits occupy
     * 0.56 to 0.64 of the cell height and handwriting 0.68 to 1.00, with no overlap;
     * whereas one photograph has handwriting drawn as dark as the print beside it, so
     * any darkness threshold that catches faint pencil misclassifies bold pencil.
     *
     * Printed digits are also mechanically uniform, so the printed set is taken as the
     * cluster around the *smallest* consistent height rather than a fixed threshold.
     * That keeps working when a different font sets its digits larger or smaller.
     */
    private fun printedCells(readings: List<CellReading>): Set<Int> {
        val filled = readings.filter { it.digit != null }
        if (filled.isEmpty()) return emptySet()

        val heights = filled.map { it.heightRatio }.sorted()
        val smallest = heights.first()

        // Printed glyphs cluster tightly. Anything within a small band of the smallest
        // digit joins that cluster; handwriting scatters above it.
        val printed = filled.filter { it.heightRatio <= smallest + PRINTED_BAND }

        // A grid whose "printed" set is everything is a fully printed puzzle, which is
        // legitimate: an unsolved puzzle, or a printed solution.
        return printed.map { it.index }.toSet()
    }

    private fun assemble(readings: List<CellReading>, printed: Set<Int>): Grid {
        var grid = Grid.Empty
        for (reading in readings) {
            val digit = reading.digit ?: continue
            grid = grid.with(
                reading.index,
                if (reading.index in printed) Cell.given(digit) else Cell.guess(digit),
            )
        }
        return grid
    }

    /**
     * The solver disagrees with the reader, so the reader is wrong somewhere.
     *
     * Cells are tried in order of how little the classifier trusted them, each swapped
     * for its runner-up, and the first reading that yields a unique puzzle wins. A
     * bounded search: this runs on a phone while the user waits.
     */
    private fun repair(
        readings: List<CellReading>,
        printed: Set<Int>,
        original: Grid,
        weak: Set<Int>,
        solved: SolveResult,
    ): ReadResult {
        val givens = original.givensOnly()
        val givenCount = givens.givenCount

        // Fewer than 17 givens cannot produce a unique puzzle - a proven bound - so a
        // read below it is definitely wrong and usually means printed digits were
        // classified as handwriting.
        if (givenCount in 1 until MIN_GIVENS && !original.isComplete) {
            return ReadResult.Unreadable(
                "Only $givenCount printed digits were found, which is too few for a puzzle.",
                weak,
            )
        }

        val candidates = readings
            .filter { it.digit != null }
            .sortedBy { it.margin }
            .take(REPAIR_CELLS)

        for (reading in candidates) {
            val probabilities = reading.probabilities ?: continue
            val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
            for (alternative in ranked.drop(1).take(REPAIR_ALTERNATIVES)) {
                val digit = alternative + 1
                val cell = if (reading.index in printed) Cell.given(digit) else Cell.guess(digit)
                val attempt = original.with(reading.index, cell)
                if (Solver.solve(attempt) is SolveResult.Unique) {
                    return ReadResult.NeedsConfirmation(
                        attempt, readings, weak + reading.index,
                        "One cell was corrected automatically - please check it.",
                    )
                }
            }
        }

        val reason = when (solved) {
            is SolveResult.Multiple -> "The puzzle has more than one solution, so a printed digit was probably missed."
            else -> "The digits read do not make a valid puzzle."
        }
        val uncertain = weak + candidates.map { it.index }
        return if (uncertain.size <= CONFIRMABLE_CELLS) {
            ReadResult.NeedsConfirmation(original, readings, uncertain, reason)
        } else {
            ReadResult.Unreadable(reason, uncertain)
        }
    }

    /**
     * Where digits stop and pencilled candidate marks begin.
     *
     * A constant, after three adaptive schemes were each measured against the corpus and
     * each proved worse:
     *
     *  - Splitting at the widest gap finds the gap between print and handwriting, so the
     *    threshold lands above the printed givens and discards them.
     *  - Taking the densest cluster finds the handwriting on a completed puzzle, where
     *    written answers outnumber printed givens.
     *  - Taking the lowest cluster of at least seventeen finds a window straddling the
     *    boundary - the tallest marks plus the shortest printed digits - and puts the
     *    split down among the marks. Requiring a clear gap below the cluster did not
     *    save it.
     *
     * Measured across seven photographs, including one covered in candidate marks,
     * printed digits occupy 0.56 to 0.64 of the cell height and marks never exceed 0.53.
     * The margin narrows from 0.05 on a tidy page to 0.03 on an annotated one, so it is
     * thin - but a constant inside it reads all seven, and none of the adaptive rules
     * did. Any future change here has to beat that, measured, on the whole corpus.
     */
    internal fun digitHeightThreshold(@Suppress("UNUSED_PARAMETER") heights: List<Double>): Double =
        MIN_DIGIT_HEIGHT_RATIO

    companion object {

        /** The fallback split, measured on the corpus: digits from 0.556, marks to 0.505. */
        const val MIN_DIGIT_HEIGHT_RATIO = 0.53


        /** Below this, there is not enough on the page to be worth confirming. */
        private const val MIN_FILLED_CELLS = 17

        /** The proven minimum number of givens for a puzzle with one solution. */
        private const val MIN_GIVENS = 17

        /** Gap between the top two probabilities below which a cell counts as weak. */
        private const val CONFIDENT_MARGIN = 0.60f

        /** Height band, as a fraction of cell height, that the printed cluster spans. */
        private const val PRINTED_BAND = 0.10

        private const val REPAIR_CELLS = 12
        private const val REPAIR_ALTERNATIVES = 2

        /** More uncertain cells than this and a retake beats confirming one by one. */
        private const val CONFIRMABLE_CELLS = 6
    }
}
