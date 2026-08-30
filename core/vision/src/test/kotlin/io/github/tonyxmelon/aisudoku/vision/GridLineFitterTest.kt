package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridLineFitterTest {

    private fun geometryFor(fragment: String): CellGeometry {
        val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.photo(fragment)))
        return assertIs<CellGeometry>(GridLineFitter.fit(located.rectified))
    }

    @Test
    fun `finds ten lines on each axis for every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.load(file)))
            val geometry = GridLineFitter.fit(located.rectified)
            assertIs<CellGeometry>(geometry, "${file.name}: line fitting failed")
            assertEquals(10, geometry.verticalLines.size, file.name)
            assertEquals(10, geometry.horizontalLines.size, file.name)
        }
    }

    @Test
    fun `lines are ordered and span the image`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142301")
        assertTrue(geometry.verticalLines == geometry.verticalLines.sorted())
        assertTrue(geometry.horizontalLines == geometry.horizontalLines.sorted())
        assertTrue(geometry.verticalLines.first() < GridLocator.RECTIFIED_SIZE * 0.10)
        assertTrue(geometry.verticalLines.last() > GridLocator.RECTIFIED_SIZE * 0.90)
    }

    @Test
    fun `fitted lines differ from an even ninth division on curled paper`() {
        // This is the whole reason the fitter exists. If the fitted lines were identical
        // to the ideal ones, dividing by nine would have been fine.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142203")
        val ideal = (0..9).map { it * GridLocator.RECTIFIED_SIZE / 9.0 }
        val drift = geometry.horizontalLines.zip(ideal).maxOf { (fitted, even) -> abs(fitted - even) }
        assertTrue(drift > 2.0, "expected measurable drift on curled paper but got $drift px")
    }

    @Test
    fun `cell bounds are derived from the fitted lines with an inner margin`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142301")
        val cell = geometry.cellBounds(0)
        assertTrue(cell.left >= geometry.verticalLines[0])
        assertTrue(cell.right <= geometry.verticalLines[1])
        assertTrue(cell.right > cell.left && cell.bottom > cell.top)

        val last = geometry.cellBounds(80)
        assertTrue(last.left >= geometry.verticalLines[8])
        assertTrue(last.right <= geometry.verticalLines[9])
    }
}
