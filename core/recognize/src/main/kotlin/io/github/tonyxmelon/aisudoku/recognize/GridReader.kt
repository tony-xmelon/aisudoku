package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.model.Cell
import io.github.tonyxmelon.aisudoku.model.Grid
import io.github.tonyxmelon.aisudoku.solver.SolveResult
import io.github.tonyxmelon.aisudoku.solver.Solver
import io.github.tonyxmelon.aisudoku.vision.GrayImage
import kotlin.math.abs

/**
 * What one cell was judged to hold.
 *
 * [MARK] and [NONE] are both empty as far as the puzzle is concerned, but they are not
 * the same thing to a person looking at the page: one cell has pencilled candidates in
 * it that the reader deliberately ignored, the other has nothing at all.
 */
enum class Ink { PRINTED, ANSWER, MARK, NONE }

/** How the reader judged one cell. */
data class CellReading(
    val index: Int,
    val ink: Ink,
    /** Probabilities for digits 1..9, or null when the cell holds no digit. */
    val probabilities: FloatArray?,
    val heightRatio: Double,
    val darkness: Double,
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
     * ask the user about - rejection and correction are the same mechanism at different
     * scales.
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
 * Everything starts from the printed digits. They are the one population on the page
 * whose properties are guaranteed: one font, one ink, one size, and never fewer than
 * seventeen of them, because no sudoku with a single solution can have fewer. Find them
 * and the rest follows - what is taller is handwriting, and what is smaller, or sits
 * high in the cell, is a pencilled candidate mark - so every threshold comes from the
 * photograph in hand rather than being fixed in advance.
 *
 * Acceptance is judged on whether extraction actually succeeded, not on how the
 * photograph scored. Image metrics reject usable photographs and pass unusable ones;
 * blur is the clearest case, raising the grid-detection score while destroying
 * legibility. So the verdict comes from classifier margins, and from whether the printed
 * digits form a puzzle with exactly one solution.
 */
class GridReader(private val classifier: DigitClassifier = DigitClassifier.load()) {

    fun read(cells: List<GrayImage>): ReadResult {
        require(cells.size == 81) { "expected 81 cells but got ${cells.size}" }

        val ink = CellAnalyzer.inspect(cells)
        val core = findPrintedCore(ink.mapNotNull { it?.blob })
            ?: return ReadResult.Unreadable(
                "Could not find the printed digits in that photo.", emptySet(),
            )

        val readings = ink.mapIndexed { index, cell ->
            val kind = if (cell == null) Ink.NONE else classify(cell.blob, core)
            CellReading(
                index = index,
                ink = kind,
                probabilities = if (cell == null || kind == Ink.MARK || kind == Ink.NONE) null
                else classifier.classify(cell.normalised),
                heightRatio = cell?.blob?.heightRatio ?: 0.0,
                darkness = cell?.blob?.darkness ?: 255.0,
            )
        }

        val printed = readings.filter { it.ink == Ink.PRINTED }
        if (printed.size < MIN_GIVENS) {
            return ReadResult.Unreadable(
                "Only ${printed.size} printed digits were found, which is too few for a puzzle.",
                printed.map { it.index }.toSet(),
            )
        }

        val grid = assemble(readings)
        val weak = readings
            .filter { it.digit != null && it.margin < CONFIDENT_MARGIN }
            .map { it.index }
            .toSet()

        return when (Solver.solve(grid)) {
            is SolveResult.Unique ->
                if (weak.isEmpty()) {
                    ReadResult.Accepted(grid, readings)
                } else {
                    ReadResult.NeedsConfirmation(
                        grid, readings, weak,
                        "Some digits were not read confidently.",
                    )
                }

            else -> repair(readings, grid, weak, core)
        }
    }

    /**
     * The printed digits, found before anything else is decided.
     *
     * Take the seventeen blobs most alike in height and in ink, preferring the darkest
     * such group. Printed digits are toner and everything else on the page is pencil, so
     * on a puzzle covered in annotations - where the marks are numerous enough that
     * seventeen of *them* are more uniform in size than the print - darkness is what
     * still tells the two apart. Measured over the corpus this picks the printed digits
     * on every photograph, including the one where size alone picks the marks.
     *
     * Height alone is not enough, and three schemes built on it each regressed
     * photographs that read perfectly: the widest gap is the one between print and
     * handwriting, the densest cluster is the handwriting on a completed puzzle, and the
     * lowest cluster of seventeen straddles the mark/print boundary.
     */
    internal fun findPrintedCore(blobs: List<Blob>): PrintedCore? {
        val present = blobs.filter { it.heightRatio >= MARK_FLOOR }.sortedBy { it.heightRatio }
        if (present.size < MIN_GIVENS) return null

        var best: List<Blob>? = null
        var bestScore = Double.MAX_VALUE
        for (start in 0..present.size - MIN_GIVENS) {
            val window = present.subList(start, start + MIN_GIVENS)
            val heightSpread = window.last().heightRatio - window.first().heightRatio
            val darknessSpread = (window.maxOf { it.darkness } - window.minOf { it.darkness }) / 255.0
            val lightness = median(window.map { it.darkness }) / 255.0
            val score = heightSpread + darknessSpread * DARKNESS_SPREAD_WEIGHT +
                lightness * LIGHTNESS_WEIGHT
            if (score < bestScore) {
                bestScore = score
                best = window
            }
        }

        val window = best ?: return null
        return PrintedCore(
            height = median(window.map { it.heightRatio }),
            darkness = median(window.map { it.darkness }),
            strokeWidth = median(window.map { it.strokeWidth }),
        )
    }

    /**
     * What one blob is, measured against the printed digits of the same photograph.
     *
     * Printed digits vary in height by no more than 5% of their own median across the
     * whole corpus, so the printed band can be tight, and handwriting is never less than
     * 15% taller than the print. The only things that reach into either band are large
     * candidate marks - a ringed pair of digits, say - and those are given away by where
     * they sit: candidate marks are written along the top of a cell, answers in the
     * middle of it. Measured, no mark reaching digit size sits lower than 0.14 of a cell
     * above centre, no printed digit higher than 0.15, and no answer higher than 0.10.
     */
    private fun classify(blob: Blob, core: PrintedCore): Ink {
        val relative = blob.heightRatio / core.height
        return when {
            relative in PRINTED_MIN..PRINTED_MAX && blob.verticalOffset >= PRINTED_TOP_LIMIT ->
                Ink.PRINTED

            relative >= ANSWER_MIN && blob.verticalOffset >= ANSWER_TOP_LIMIT -> Ink.ANSWER
            else -> Ink.MARK
        }
    }

    private fun assemble(readings: List<CellReading>): Grid {
        var grid = Grid.Empty
        for (reading in readings) {
            val digit = reading.digit ?: continue
            grid = grid.with(
                reading.index,
                if (reading.ink == Ink.PRINTED) Cell.given(digit) else Cell.guess(digit),
            )
        }
        return grid
    }

    /**
     * The printed digits do not make a puzzle, so the reading of them is wrong somewhere.
     *
     * Only printed digits can be at fault: the solver works from the givens alone, so no
     * handwritten answer, right or wrong, changes the outcome.
     *
     * Removing a given is tried before changing one. The observed failure is a false
     * positive - a clump of candidate marks that happens to match the print in size -
     * and removing a *real* given almost never yields a unique puzzle, while changing one
     * can quietly produce a different puzzle that solves cleanly. That is how an earlier
     * version turned a correctly read 1 into a 7 and reported success.
     *
     * Suspects are ranked by how far they sit from the printed core, so the least
     * print-like digit is questioned first.
     */
    private fun repair(
        readings: List<CellReading>,
        original: Grid,
        weak: Set<Int>,
        core: PrintedCore,
    ): ReadResult {
        val suspects = readings
            .filter { it.ink == Ink.PRINTED && it.digit != null }
            .sortedByDescending { deviation(it, core) }
            .take(REPAIR_CELLS)

        for (suspect in suspects) {
            val without = original.with(suspect.index, Cell.Empty)
            if (Solver.solve(without) is SolveResult.Unique) {
                return ReadResult.NeedsConfirmation(
                    without, readings, weak + suspect.index,
                    "One cell looked like a printed digit but is not.",
                )
            }
        }

        for (suspect in suspects.sortedBy { it.margin }) {
            val probabilities = suspect.probabilities ?: continue
            val ranked = probabilities.indices.sortedByDescending { probabilities[it] }
            for (alternative in ranked.drop(1).take(REPAIR_ALTERNATIVES)) {
                val attempt = original.with(suspect.index, Cell.given(alternative + 1))
                if (Solver.solve(attempt) is SolveResult.Unique) {
                    return ReadResult.NeedsConfirmation(
                        attempt, readings, weak + suspect.index,
                        "One printed digit was corrected automatically.",
                    )
                }
            }
        }

        val reason = "The printed digits do not make a solvable puzzle."
        val uncertain = weak + suspects.map { it.index }
        return if (uncertain.size <= CONFIRMABLE_CELLS) {
            ReadResult.NeedsConfirmation(original, readings, uncertain, reason)
        } else {
            ReadResult.Unreadable(reason, uncertain)
        }
    }

    /** How unlike the printed core a reading is, in units of the core's own spread. */
    private fun deviation(reading: CellReading, core: PrintedCore): Double =
        abs(reading.heightRatio / core.height - 1.0) / 0.05 +
            abs(reading.darkness - core.darkness) / 40.0

    /** The printed digits of one photograph: one font, one ink, one size. */
    internal data class PrintedCore(
        val height: Double,
        val darkness: Double,
        val strokeWidth: Double,
    )

    companion object {

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            return sorted[sorted.size / 2]
        }

        /** Below this a blob is speckle or a mark, and never joins the printed search. */
        private const val MARK_FLOOR = 0.25

        /** The proven minimum number of givens for a puzzle with one solution. */
        private const val MIN_GIVENS = 17

        /** How much agreement on ink counts next to agreement on size. */
        private const val DARKNESS_SPREAD_WEIGHT = 0.5

        /**
         * How much being the darkest group counts. Print is toner and everything else is
         * pencil, so this is what separates the print from a page of candidate marks.
         */
        private const val LIGHTNESS_WEIGHT = 1.0

        /**
         * The printed band, as a multiple of the core height. Measured across the corpus,
         * printed digits fall in 0.93 to 1.05 and handwriting starts at 1.15.
         */
        private const val PRINTED_MIN = 0.90
        private const val PRINTED_MAX = 1.09

        /** A written answer is at least this much taller than the print. */
        private const val ANSWER_MIN = 1.10

        /**
         * How far above the centre of its cell a blob may sit and still be a digit.
         *
         * Candidate marks are written along the top edge. The printed test can afford the
         * looser limit because its height band already excludes almost everything.
         */
        private const val PRINTED_TOP_LIMIT = -0.18
        private const val ANSWER_TOP_LIMIT = -0.12

        /** Gap between the top two probabilities below which a cell counts as weak. */
        private const val CONFIDENT_MARGIN = 0.60f

        private const val REPAIR_CELLS = 8
        private const val REPAIR_ALTERNATIVES = 2

        /** More uncertain cells than this and a retake beats confirming one by one. */
        private const val CONFIRMABLE_CELLS = 6
    }
}
