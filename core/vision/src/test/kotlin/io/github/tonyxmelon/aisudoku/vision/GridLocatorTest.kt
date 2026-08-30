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
        // The worst corpus photo measured 0.41 against a 0.35 threshold. If this drops,
        // a parameter change has eaten the margin - investigate before lowering it.
        assertTrue(scores.values.min() >= 0.38, "grid score margin has regressed: $scores")
    }

    @Test
    fun `reports no grid rather than inventing one`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(800, 800, ByteArray(640_000) { -1 })
        assertIs<GridLocation.NoGrid>(GridLocator.locate(blank))
    }
}
