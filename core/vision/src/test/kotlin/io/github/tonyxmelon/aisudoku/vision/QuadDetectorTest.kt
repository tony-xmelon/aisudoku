package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertTrue

class QuadDetectorTest {

    @Test
    fun `finds candidates in every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val candidates = QuadDetector.detect(CorpusFixtures.load(file))
            assertTrue(candidates.isNotEmpty(), "${file.name}: no candidates at all")
            assertTrue(candidates.size <= 10, "${file.name}: ${candidates.size} candidates, expected at most 10")
        }
    }

    @Test
    fun `candidates arrive largest first`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        val areas = candidates.map { it.area }
        assertTrue(areas == areas.sortedDescending(), "candidates were not ordered by area: $areas")
    }

    @Test
    fun `the paper is found as well as the grid in the dark background photo`() {
        // This photo is the reason candidates are plural: the sheet of paper is a bigger,
        // cleaner quadrilateral than the puzzle printed on it.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        assertTrue(candidates.size >= 3, "expected the paper and the grid among candidates, got ${candidates.size}")
    }

    @Test
    fun `a blank image yields no candidates`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(400, 400, ByteArray(160_000) { -1 })
        assertTrue(QuadDetector.detect(blank).isEmpty())
    }
}
