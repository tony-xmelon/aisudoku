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

        // Find the print first: everything else follows from it, since what is bigger is
        // handwriting and what is smaller is a candidate mark.
        //
        // On a page covered in annotations the marks are numerous and uniform enough that
        // seventeen of them can look tighter than the printed digits, so several
        // candidates are tried and the solver decides. That is not a fallback but the
        // point: the printed givens are, by definition, the set that forms a puzzle with
        // one solution. Nothing else on the page does.
        val blobs = CellAnalyzer.largestBlobs(cells)
        var firstAttempt: ReadResult? = null
        for (core in printedCoreCandidates(blobs)) {
            val attempt = readWith(cells, core)
            if (attempt is ReadResult.Accepted || attempt is ReadResult.NeedsConfirmation) return attempt
            if (firstAttempt == null) firstAttempt = attempt
        }
        return firstAttempt ?: readWith(cells, null)
    }

    private fun readWith(cells: List<GrayImage>, core: PrintedCore?): ReadResult {
        val analyses = cells.map { CellAnalyzer.analyse(it, core?.digitThreshold ?: FALLBACK_THRESHOLD) }
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

        val printed = printedCells(readings, core)
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
     * The printed givens, found before anything else is decided.
     *
     * Everything downstream depends on this. Once the print is known, whatever is taller
     * is handwriting and whatever is shorter is a candidate mark, so the thresholds come
     * from the photograph in hand instead of from a constant that has to suit every
     * photograph at once.
     *
     * Printed digits are the one population whose properties are guaranteed: they share
     * a font, a size and an ink, so they are near-identical to one another, while
     * handwriting varies and marks are small and scattered. And a puzzle with a single
     * solution needs at least seventeen of them, a floor nothing else on the page
     * reaches. So take the seventeen blobs most alike in height *and* darkness.
     *
     * Measured across the corpus that window is 17 out of 17 printed givens on every
     * photograph, and the threshold it yields admits no mark and misses no digit.
     *
     * Height alone is not enough. Three schemes built on it each regressed photographs
     * that read perfectly: the widest gap is the one between print and handwriting, the
     * densest cluster is the handwriting on a completed puzzle, and the lowest cluster of
     * seventeen straddles the mark/print boundary even when a clear gap is demanded.
     * Requiring agreement on ink as well as size is what makes it hold.
     */
    internal fun printedCoreCandidates(blobs: List<Blob?>): List<PrintedCore> {
        val present = blobs.filterNotNull()
            .filter { it.heightRatio >= MARK_FLOOR }
            .sortedBy { it.heightRatio }
        if (present.size < MIN_PRINTED) return emptyList()

        val scored = (0..present.size - MIN_PRINTED).map { start ->
            val window = present.subList(start, start + MIN_PRINTED)
            val heightSpread = window.last().heightRatio - window.first().heightRatio
            val darknessSpread = window.maxOf { it.darkness } - window.minOf { it.darkness }
            val score = heightSpread + darknessSpread / 255.0 * DARKNESS_WEIGHT
            score to PrintedCore(window.first().heightRatio, window.last().heightRatio)
        }

        // Overlapping windows describe the same population, so keep one per distinct
        // starting height rather than burning the budget on near-duplicates.
        return scored.sortedBy { it.first }
            .map { it.second }
            .distinctBy { Math.round(it.minHeight * 100) }
            .take(MAX_CORE_CANDIDATES)
    }

    /** The single most likely printed core, ignoring the solver. Exposed for diagnosis. */
    internal fun findPrintedCore(blobs: List<Blob?>): PrintedCore? =
        printedCoreCandidates(blobs).firstOrNull()

    /**
     * Which cells hold printed digits, judged against the core.
     *
     * Decided by size rather than darkness: one corpus photograph has handwriting drawn
     * as dark as the print beside it, so no ink threshold separates the two.
     */
    private fun printedCells(readings: List<CellReading>, core: PrintedCore?): Set<Int> {
        val filled = readings.filter { it.digit != null }
        if (filled.isEmpty()) return emptySet()

        // Without a core there were too few digits to identify the print, so fall back
        // to treating the smallest consistent group as printed.
        val ceiling = (core?.maxHeight ?: filled.minOf { it.heightRatio }) + PRINTED_TOLERANCE

        // A grid whose printed set is everything is legitimate: an unsolved puzzle, or a
        // printed solution.
        return filled.filter { it.heightRatio <= ceiling }.map { it.index }.toSet()
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

    /** The printed givens on this page, in blob-height terms. */
    internal data class PrintedCore(val minHeight: Double, val maxHeight: Double) {
        /** At or above this a blob is a digit; below it, a candidate mark. */
        val digitThreshold: Double get() = minHeight - PRINTED_MARGIN
    }

    companion object {

        /** Below this a blob is speckle or a mark, and never joins the printed search. */
        private const val MARK_FLOOR = 0.30

        /** The proven minimum number of givens for a puzzle with one solution. */
        private const val MIN_PRINTED = 17

        /** How much agreement on ink counts next to agreement on size. */
        private const val DARKNESS_WEIGHT = 0.5

        /**
         * How far under the shortest printed digit the digit threshold sits. Measured
         * across the corpus, 0.03 misses no digit and admits no mark, where 0.02 misses
         * one and 0.04 leaves less room above the marks.
         */
        private const val PRINTED_MARGIN = 0.03

        /** How much taller than the printed core a digit may be and still be printed. */
        private const val PRINTED_TOLERANCE = 0.05

        /** Used only when there are too few digits to identify a printed core at all. */
        private const val FALLBACK_THRESHOLD = 0.53

        /**
         * How many candidate printed cores to try before giving up.
         *
         * Each costs a full read, on the order of a tenth of a second, and on every
         * corpus photograph but the most heavily annotated one the first candidate is
         * already right.
         */
        private const val MAX_CORE_CANDIDATES = 6

        /** Below this, there is not enough on the page to be worth confirming. */
        private const val MIN_FILLED_CELLS = 17

        /** The proven minimum number of givens for a puzzle with one solution. */
        private const val MIN_GIVENS = 17

        /** Gap between the top two probabilities below which a cell counts as weak. */
        private const val CONFIDENT_MARGIN = 0.60f

        private const val REPAIR_CELLS = 12
        private const val REPAIR_ALTERNATIVES = 2

        /** More uncertain cells than this and a retake beats confirming one by one. */
        private const val CONFIRMABLE_CELLS = 6
    }
}
