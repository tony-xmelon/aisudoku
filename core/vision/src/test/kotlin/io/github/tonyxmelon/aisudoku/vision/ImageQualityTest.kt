package io.github.tonyxmelon.aisudoku.vision

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageQualityTest {

    private fun noise(size: Int, seed: Int): GrayImage {
        val random = Random(seed)
        return GrayImage(size, size, ByteArray(size * size) { random.nextInt(256).toByte() })
    }

    private fun flat(size: Int, value: Int) =
        GrayImage(size, size, ByteArray(size * size) { value.toByte() })

    @Test
    fun `noise is sharp and a flat field is not`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        assertTrue(ImageQuality.of(noise(128, 1)).sharpness > ImageQuality.of(flat(128, 128)).sharpness)
    }

    @Test
    fun `blurring an image lowers its sharpness`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        CorpusFixtures.requireCorpus()

        val sharp = CorpusFixtures.photo("142301")
        val blurred = Degrade.blur(sharp, radius = 9)
        assertTrue(
            ImageQuality.of(blurred).sharpness < ImageQuality.of(sharp).sharpness * 0.5,
            "blur did not reduce measured sharpness",
        )
    }

    @Test
    fun `mean luma reflects brightness`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        assertTrue(ImageQuality.of(flat(64, 30)).meanLuma < 40)
        assertTrue(ImageQuality.of(flat(64, 220)).meanLuma > 210)
    }

    @Test
    fun `a blown out region is reported as glare`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val size = 100
        val pixels = ByteArray(size * size) { 120.toByte() }
        for (y in 0 until 30) for (x in 0 until 30) pixels[y * size + x] = 255.toByte()
        val glare = ImageQuality.of(GrayImage(size, size, pixels)).clippedWhiteFraction
        assertTrue(glare > 0.08, "expected roughly 9 percent clipped but measured $glare")
    }

    @Test
    fun `quadrant sharpness spots a partially focused image`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val even = ImageQuality.of(CorpusFixtures.photo("142301"))
        assertTrue(even.worstQuadrantSharpnessRatio > 0.3, "a good photo should be evenly sharp")
    }
}
