package io.github.tonyxmelon.aisudoku.vision

import java.io.File
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The screenshots sent back from the phone, run through the reader again.
 *
 * A screenshot of the camera is a photograph of the scene with some buttons drawn on it,
 * so the locator can be pointed straight at one. That makes every report of "it would not
 * scan this" into a case that can be re-run here for ever afterwards, which is the whole
 * value of keeping them.
 *
 * Local only, like the corpus: they are photographs of a real page. Skips on CI.
 *
 * The bar is deliberately low - a screenshot is a picture of a phone screen showing a
 * picture of a page, at a fraction of the resolution, so failing here is not damning. It
 * exists to stop a fix being claimed without the reported case ever being tried.
 */
class FeedbackScreenshotTest {

    private val directory = File("../../feedback").canonicalFile

    private fun screenshots(): List<File> = directory
        .walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png") }
        .sortedBy { it.name }
        .toList()

    @Test
    fun `every screenshot sent back is re-read, and reported either way`() {
        val shots = screenshots()
        assumeTrue(shots.isNotEmpty(), "no feedback screenshots at $directory - skipping")
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val found = mutableListOf<String>()
        val missed = mutableListOf<String>()
        for (file in shots) {
            val image = CorpusFixtures.load(file)
            when (val located = GridLocator.locate(image)) {
                is GridLocation.Found ->
                    found += "%s %.2f".format(file.name, located.gridScore)

                is GridLocation.NoGrid ->
                    missed += "%s best %.2f of %d".format(
                        file.name, located.bestScore, located.candidatesConsidered,
                    )
            }
        }

        println("=== screenshots from the phone ===")
        for (line in found) println("  found   $line")
        for (line in missed) println("  missed  $line")

        // Whatever it finds must at least be a plausible grid rather than a sliver of
        // background - the fault the outline drawn on screen made visible.
        for (file in shots) {
            for (quad in QuadDetector.detect(CorpusFixtures.load(file))) {
                val edges = listOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
                assertTrue(
                    edges.min() / edges.max() > 0.4,
                    "${file.name}: offered a shape ${edges.min()} by ${edges.max()}",
                )
            }
        }
    }
}
