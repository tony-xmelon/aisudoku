package io.github.tonyxmelon.aisudoku.vision

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Access to the local photograph corpus.
 *
 * The photographs are large and deliberately not in git, so on CI this directory does
 * not exist. Corpus tests must therefore *skip*, not fail. Call [requireCorpus] first in
 * any test that needs a photograph.
 */
object CorpusFixtures {

    val directory: File = File("../../corpus").canonicalFile

    val photos: List<File>
        get() = directory.listFiles { f: File -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }
            ?: emptyList()

    val isAvailable: Boolean get() = photos.isNotEmpty()

    /** Skips the calling test when the corpus is not present, e.g. on CI. */
    fun requireCorpus() {
        assumeTrue(isAvailable, "corpus not present at $directory - skipping (expected on CI)")
    }

    fun load(file: File): GrayImage = ImageIO.read(file).toGrayImage()

    fun photo(nameFragment: String): GrayImage =
        load(photos.first { it.name.contains(nameFragment) })
}

/** Converts any `BufferedImage` to grayscale bytes, ignoring EXIF orientation. */
fun BufferedImage.toGrayImage(): GrayImage {
    val buffer = ByteArray(width * height)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            // Rec. 601 luma, the same weighting OpenCV's COLOR_BGR2GRAY uses.
            buffer[i++] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
    }
    return GrayImage(width, height, buffer)
}
