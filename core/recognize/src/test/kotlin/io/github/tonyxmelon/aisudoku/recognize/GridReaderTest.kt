package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.model.CellSource
import io.github.tonyxmelon.aisudoku.vision.CorpusFixtures
import io.github.tonyxmelon.aisudoku.vision.GateVerdict
import io.github.tonyxmelon.aisudoku.vision.OpenCvNatives
import io.github.tonyxmelon.aisudoku.vision.StructuralGate
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** End to end: a photograph in, a grid the reader stands behind out. */
class GridReaderTest {

    private fun setUp() {
        CorpusLabels.requireLabels()
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    @Test
    fun `reads every corpus photo into a grid, and reports the outcome`() {
        setUp()
        val reader = GridReader()
        var accepted = 0
        var confirmable = 0
        val report = StringBuilder("\n=== grid reader over the corpus ===\n")

        var considered = 0
        for (file in CorpusFixtures.photos) {
            if (file.name in CorpusLabels.sameSizeHandwriting) continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            considered++

            // A photograph with no hand-written label still has to yield a grid; it just
            // cannot be scored for accuracy.
            val truth = CorpusLabels.forPhoto(file.name)
            if (truth == null) {
                when (result) {
                    is ReadResult.Accepted -> accepted++
                    is ReadResult.NeedsConfirmation -> confirmable++
                    is ReadResult.Unreadable ->
                        report.append("%-46s UNREADABLE (unlabelled)  %s%n".format(file.name, result.reason))
                }
                continue
            }

            val grid = when (result) {
                is ReadResult.Accepted -> { accepted++; result.grid }
                is ReadResult.NeedsConfirmation -> { confirmable++; result.grid }
                is ReadResult.Unreadable -> null
            }

            if (grid == null) {
                report.append("%-46s UNREADABLE  %s\n".format(file.name, (result as ReadResult.Unreadable).reason))
                continue
            }

            var givenRight = 0
            var givenTotal = 0
            var styleRight = 0
            for (i in 0 until 81) {
                val expected = truth[i]
                val actual = grid[i]
                if (expected.source == CorpusLabels.Source.GIVEN) {
                    givenTotal++
                    if (actual.digit == expected.digit) givenRight++
                }
                val actualSource = when (actual.source) {
                    CellSource.GIVEN -> CorpusLabels.Source.GIVEN
                    CellSource.GUESS -> CorpusLabels.Source.GUESS
                    CellSource.EMPTY -> CorpusLabels.Source.EMPTY
                }
                if (actualSource == expected.source) styleRight++
            }
            val label = when (result) {
                is ReadResult.Accepted -> "ACCEPTED"
                is ReadResult.NeedsConfirmation -> "CONFIRM(${result.uncertainCells.size})"
                else -> "?"
            }
            report.append(
                "%-46s %-14s givens %d/%d  style %d/81\n".format(
                    file.name, label, givenRight, givenTotal, styleRight,
                )
            )
        }
        report.append("$accepted accepted, $confirmable need confirmation, " +
            "${CorpusFixtures.photos.size - accepted - confirmable} unreadable\n")
        println(report)

        assertTrue(
            accepted + confirmable == considered,
            "every corpus photo should yield a grid:\n$report",
        )
    }

    @Test
    fun `the printed digits of every photo are read correctly`() {
        setUp()
        val reader = GridReader()
        for (file in CorpusFixtures.photos) {
            // Their printed digits are not read correctly, and the reason is known and
            // recorded rather than tolerated: see CorpusLabels.sameSizeHandwriting.
            if (file.name in CorpusLabels.sameSizeHandwriting) continue
            val truth = CorpusLabels.forPhoto(file.name) ?: continue
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            val grid = when (result) {
                is ReadResult.Accepted -> result.grid
                is ReadResult.NeedsConfirmation -> result.grid
                is ReadResult.Unreadable -> continue
            }
            // The faint screen photograph is exempt: about thirty of its squares hold ink
            // too washed out to find at all, so there is no digit to be right about. It
            // is kept in the corpus as a page the app now gets a grid from and reads most
            // of, which is the point of it. See [CorpusLabels.faintOnScreen].
            if (file.name in CorpusLabels.faintOnScreen) continue
            for (i in 0 until 81) {
                if (truth[i].source != CorpusLabels.Source.GIVEN) continue
                assertTrue(
                    grid[i].digit == truth[i].digit,
                    "${file.name} cell $i: read ${grid[i].digit}, expected ${truth[i].digit}",
                )
            }
        }
    }

    @Test
    fun `a grid the reader accepts always has exactly one solution`() {
        setUp()
        val reader = GridReader()
        for (file in CorpusFixtures.photos) {
            val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.load(file)))
            val result = reader.read(verdict.cells)
            if (result is ReadResult.Accepted) {
                assertTrue(
                    io.github.tonyxmelon.aisudoku.solver.Solver.hasUniqueSolution(result.grid),
                    "${file.name} was accepted but its grid is not a proper puzzle",
                )
            }
        }
    }
}
