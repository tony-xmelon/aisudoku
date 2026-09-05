package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridLocatorTest {

    private fun locate(fragment: String) =
        GridLocator.locate(CorpusFixtures.photo(fragment))

    @Test
    fun `locates a grid in every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val result = GridLocator.locate(CorpusFixtures.load(file))
            val located = assertIs<GridLocation.Found>(result, "${file.name}: $result")
            assertTrue(located.gridScore >= GridLocator.MIN_GRID_SCORE, "${file.name} scored ${located.gridScore}")
            assertEquals(GridLocator.RECTIFIED_SIZE, located.rectified.width)
            assertEquals(GridLocator.RECTIFIED_SIZE, located.rectified.height)
        }
    }

    @Test
    fun `prefers the grid over the sheet of paper it is printed on`() {
        // Measured: the paper covers 59% of this frame and scores 0.28; the grid covers
        // 36% and scores 0.41. Choosing by area gets this wrong.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val located = assertIs<GridLocation.Found>(locate("142203"))
        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        assertTrue(
            located.quad.area < candidates.first().area,
            "chose the largest candidate, which is the paper rather than the grid",
        )
    }

    @Test
    fun `scores stay above the accept threshold with the margin measured on the corpus`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val scores = CorpusFixtures.photos.associate { file ->
            file.name to (GridLocator.locate(CorpusFixtures.load(file)) as GridLocation.Found).gridScore
        }
        // The worst corpus photograph measured 0.41 against a 0.35 threshold. If this
        // drops, a parameter change has eaten the margin - investigate before lowering it.
        //
        // One page is exempt and named rather than the floor being lowered for all of
        // them. Its grid is only found at all because the outline traced round it comes
        // back a shade inside its own printed rule, and the locator tries the best few
        // outlines slightly larger before giving up; grown, it scores 0.375. That is
        // thin, and it is thin because the photograph is thin - not because anything
        // regressed - and it is the page that proves the rescue works. Lowering the
        // floor to suit it would stop this test noticing the day something really does
        // eat the margin on the other twenty-one.
        val marginal = setOf("aisudoku-2026-09-04-newsprint-blue-4.jpg")
        val ordinary = scores.filterKeys { it !in marginal }
        assertTrue(ordinary.values.min() >= 0.38, "grid score margin has regressed: $ordinary")
        assertTrue(
            scores.filterKeys { it in marginal }.values.all { it >= GridLocator.MIN_GRID_SCORE },
            "a photograph that needed rescuing no longer clears the threshold: $scores",
        )
    }

    /**
     * The page whose border cannot be traced at all, found by its cells instead.
     *
     * Named rather than left to the sweep above, because what makes it worth keeping is
     * not that a grid is found but *how*: every shape traced round this puzzle scores
     * 0.00, and if a change ever made the outline work here it would mean the outline had
     * started matching something it should not. The two halves are asserted separately so
     * that a regression says which one moved.
     */
    @Test
    fun `finds a grid by its cells when no outline round it is one`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val image = CorpusFixtures.photo("welded-border")
        val full = image.toMat()
        val fromOutlines = QuadDetector.workingEdges().flatMap { edge ->
            QuadDetector.detect(image, edge).map { GridScorer.score(GridLocator.rectify(full, it)) }
        }
        assertTrue(
            fromOutlines.none { it >= GridLocator.MIN_GRID_SCORE },
            "an outline now scores here, which this page is kept to show cannot happen: $fromOutlines",
        )

        val located = assertIs<GridLocation.Found>(GridLocator.locate(image))
        assertTrue(
            located.gridScore >= GridLocator.MIN_GRID_SCORE,
            "the cells no longer find it: scored ${located.gridScore}",
        )
    }

    /**
     * The page that is not flat, which no plane can straighten.
     *
     * Named for the same reason as the one above: what matters is not that a grid comes
     * back but that the flat answer is genuinely unavailable here, so that if a plane ever
     * started working on this page it would mean it had begun fitting something that is
     * not the grid. Both the quad traced round it and the quad its own cells imply are
     * checked, because the second is the one that nearly works.
     */
    @Test
    fun `straightens a page that no plane fits`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val image = CorpusFixtures.photo("curled")
        val full = image.toMat()
        val flat = QuadDetector.workingEdges().flatMap { edge ->
            QuadDetector.detect(image, edge).map { GridScorer.score(GridLocator.rectify(full, it)) } +
                CellGrid.lattices(image, edge).map {
                    GridScorer.score(GridLocator.rectify(full, it.quad()))
                }
        }
        assertTrue(
            flat.none { it >= GridLocator.MIN_GRID_SCORE },
            "a flat straightening now works on the page kept to show one cannot: $flat",
        )

        val located = assertIs<GridLocation.Found>(GridLocator.locate(image))
        assertTrue(
            located.gridScore >= GridLocator.MIN_GRID_SCORE,
            "the surface no longer straightens it: scored ${located.gridScore}",
        )
    }

    /**
     * Every cell of that page is given a place of its own.
     *
     * The surface can only follow the page where it has cells to follow, so a dropped cell
     * is not a rounding detail here: three of them used to round onto places already taken
     * and vanish, leaving the corner where the sheet lifts empty, and the straightening
     * that came out of it scored 0.46 and could not be read. This is the cheap check that
     * they are all still there.
     */
    @Test
    fun `every cell of the curled page is given a place of its own`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val lattice = CellGrid.lattices(CorpusFixtures.photo("curled"), 1600.0)
            .maxByOrNull { it.centres.size }
        assertTrue(lattice != null, "no cell cluster found on the curled page")
        val placed = lattice.places.count { it != null }
        assertEquals(81, lattice.centres.size, "expected the whole grid to be found")
        assertEquals(81, placed, "cells were dropped for want of a free place")
        assertEquals(81, lattice.places.filterNotNull().distinct().size, "two cells share a place")
    }

    @Test
    fun `reports no grid rather than inventing one`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(800, 800, ByteArray(640_000) { -1 })
        assertIs<GridLocation.NoGrid>(GridLocator.locate(blank))
    }
}
