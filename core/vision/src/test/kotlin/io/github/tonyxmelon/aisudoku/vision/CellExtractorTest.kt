package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CellExtractorTest {

    private fun cellsOf(fragment: String): List<GrayImage> {
        val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.photo(fragment)))
        val geometry = assertIs<CellGeometry>(GridLineFitter.fit(located.rectified))
        return CellExtractor.extract(located.rectified, geometry)
    }

    private fun inkFraction(c: GrayImage): Double {
        var dark = 0
        for (y in 0 until c.height) for (x in 0 until c.width) if (c[x, y] < 128) dark++
        return dark.toDouble() / (c.width * c.height)
    }

    @Test
    fun `extracts eighty one cells of usable size`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val cells = cellsOf("142301")
        assertEquals(81, cells.size)
        // Spec section 4.1 wants at least 78px per cell before the margin is removed.
        assertTrue(cells.all { it.width >= 60 && it.height >= 60 }, "cells too small: ${cells[0]}")
    }

    @Test
    fun `a cell holding a printed digit is darker than an empty one`() {
        // In 142301 the top-left cell is empty and the cell at row 2 column 0 holds a 7.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val cells = cellsOf("142301")
        val empty = inkFraction(cells[0])
        val withSeven = inkFraction(cells[18])
        assertTrue(withSeven > empty * 3, "empty=$empty digit=$withSeven - extraction may be misaligned")
    }

    @Test
    fun `the margin keeps grid lines out of the crop`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        // An empty cell must be almost entirely paper. If a grid line were included the
        // border rows would be dark.
        val cells = cellsOf("142301")
        val empty = cells[0]
        val borderDark = (0 until empty.width).count { empty[it, 0] < 128 }
        assertTrue(borderDark < empty.width / 4, "top edge of an empty cell is dark: a grid line is in the crop")
    }
}
