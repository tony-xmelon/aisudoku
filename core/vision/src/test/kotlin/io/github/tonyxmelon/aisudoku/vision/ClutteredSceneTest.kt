package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A puzzle photographed on a patterned surface, which is where scanning fell apart.
 *
 * Reported from the phone: the reader outlining a tablecloth while a well-lit grid sat
 * squarely in the middle of the frame. The candidate list was the ten largest contours,
 * and a patterned background supplies a dozen bigger than the puzzle - so the grid never
 * reached the list at all, however well it was framed.
 *
 * The corpus is all clean surfaces, which is why nothing here was caught by it.
 */
class ClutteredSceneTest {

    @BeforeTest
    fun setUp() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    /**
     * The photograph inset into a preview-sized frame on a busy ground.
     *
     * The pattern is built to be exactly the nuisance a tiled tablecloth is: large,
     * high-contrast, and irregular, so it yields plenty of contours bigger than the grid.
     */
    private fun onPatternedTable(photo: GrayImage, fill: Double): GrayImage {
        val width = 960
        val height = 720
        // Big irregular blobs, each of them larger than the puzzle will be. A tiled
        // tablecloth supplies exactly this: a dozen contours that beat the grid on size
        // and lose to it on every other measure.
        val pixels = ByteArray(width * height) { 210.toByte() }
        val random = Random(11)
        repeat(16) {
            val cx = random.nextInt(width)
            val cy = random.nextInt(height)
            val rx = 150 + random.nextInt(160)
            val ry = 130 + random.nextInt(150)
            val lean = random.nextDouble() * 0.6 - 0.3
            for (y in (cy - ry).coerceAtLeast(0) until (cy + ry).coerceAtMost(height)) {
                for (x in (cx - rx).coerceAtLeast(0) until (cx + rx).coerceAtMost(width)) {
                    val dx = (x - cx) + lean * (y - cy)
                    val dy = (y - cy).toDouble()
                    val wobble = 1.0 + 0.25 * sin(dy / 19.0) + 0.2 * sin(dx / 27.0)
                    if ((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) < wobble) {
                        pixels[y * width + x] = 35
                    }
                }
            }
        }

        val inset = Degrade.shrink(photo, height * fill / photo.height)
        val left = (width - inset.width) / 2
        val top = (height - inset.height) / 2
        for (y in 0 until inset.height) {
            System.arraycopy(
                inset.pixels, y * inset.width,
                pixels, (top + y) * width + left, inset.width,
            )
        }
        return GrayImage(width, height, pixels)
    }

    @Test
    fun `the grid is found on a patterned table, not the pattern`() {
        var found = 0
        val failures = mutableListOf<String>()

        for (file in CorpusFixtures.photos) {
            val photo = CorpusFixtures.load(file)
            for (fill in listOf(0.9, 0.7)) {
                val frame = onPatternedTable(photo, fill)
                when (val located = GridLocator.locate(frame)) {
                    is GridLocation.Found -> {
                        found++
                        // And it is the puzzle that was found, not a patch of tablecloth:
                        // the inset sits in the middle of the frame.
                        val centre = located.quad.corners
                            .fold(0.0 to 0.0) { (x, y), c -> x + c.x / 4 to y + c.y / 4 }
                        assertTrue(
                            centre.first in (frame.width * 0.3)..(frame.width * 0.7) &&
                                centre.second in (frame.height * 0.25)..(frame.height * 0.75),
                            "${file.name} at $fill: found something off in the pattern at $centre",
                        )
                    }

                    is GridLocation.NoGrid ->
                        failures += "${file.name} at $fill (best %.2f)".format(located.bestScore)
                }
            }
        }

        println("found on a patterned table: $found of ${CorpusFixtures.photos.size * 2}")
        if (failures.isNotEmpty()) println("  missed: $failures")

        // Not every photograph survives being shrunk onto a busy ground, and demanding
        // that would be tuning to this particular pattern. Most of them must.
        assertTrue(
            found >= CorpusFixtures.photos.size,
            "only $found of ${CorpusFixtures.photos.size * 2} found: $failures",
        )
    }

    /**
     * The shape offered to the user has to be a plausible grid, not a sliver of pattern.
     *
     * What is offered is not the candidate list. [QuadDetector.detect] returns ragged
     * shapes on purpose - throwing them away before scoring lost the real grid on a busy
     * ground, which is the bug this whole file exists for - and [QuadDetector.couldBeAGrid]
     * is what stands between them and the outline drawn over the preview. This once
     * asserted over every candidate, which held only until a photograph arrived that
     * produced a sliver: newsprint on a patterned table yields a 72 by 578 shape, and the
     * user never sees it.
     */
    @Test
    fun `whatever it offers is square enough to be a sudoku`() {
        for (file in CorpusFixtures.photos) {
            val frame = onPatternedTable(CorpusFixtures.load(file), 0.8)
            val offered = QuadDetector.detect(frame).filter { QuadDetector.couldBeAGrid(it) }
            for (quad in offered) {
                val edges = listOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
                assertTrue(
                    edges.min() / edges.max() > 0.4,
                    "${file.name}: offered a shape ${edges.min()} by ${edges.max()}",
                )
            }
        }
    }

    /** And the whole scene still passes end to end, so nothing here is a pyrrhic victory. */
    @Test
    fun `a clean photograph still reads`() {
        val verdict = StructuralGate.assess(CorpusFixtures.photo("142301"))
        assertIs<GateVerdict.Usable>(verdict)
    }
}
