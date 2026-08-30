package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

/**
 * Photometric measurements of an image, per spec section 4.1.
 *
 * Always measure the grid region rather than the whole frame: a sharp background behind
 * a blurred page would otherwise pass every sharpness test.
 */
data class ImageQuality(
    /** Variance of the Laplacian. Higher is sharper; scale depends on resolution. */
    val sharpness: Double,
    val meanLuma: Double,
    /** Fraction of pixels at or near 255, which is what glare looks like. */
    val clippedWhiteFraction: Double,
    /** Sharpness of the worst quadrant over the median quadrant. Catches partial focus. */
    val worstQuadrantSharpnessRatio: Double,
) {
    companion object {

        private const val CLIPPED_WHITE_THRESHOLD = 250

        fun of(image: GrayImage): ImageQuality = ImageQuality(
            sharpness = laplacianVariance(image.toMat()),
            meanLuma = image.pixels.sumOf { (it.toInt() and 0xFF).toLong() }.toDouble() / image.pixels.size,
            clippedWhiteFraction = image.pixels.count { (it.toInt() and 0xFF) >= CLIPPED_WHITE_THRESHOLD }
                .toDouble() / image.pixels.size,
            worstQuadrantSharpnessRatio = quadrantRatio(image),
        )

        internal fun laplacianVariance(mat: Mat): Double {
            val laplacian = Mat()
            Imgproc.Laplacian(mat, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)
            val sd = stdDev.toArray()[0]
            return sd * sd
        }

        private fun quadrantRatio(image: GrayImage): Double {
            val halfWidth = image.width / 2
            val halfHeight = image.height / 2
            if (halfWidth < 8 || halfHeight < 8) return 1.0

            val sharpnesses = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { (cx, cy) ->
                val left = cx * halfWidth
                val top = cy * halfHeight
                val pixels = ByteArray(halfWidth * halfHeight)
                for (y in 0 until halfHeight) {
                    System.arraycopy(
                        image.pixels, (top + y) * image.width + left,
                        pixels, y * halfWidth, halfWidth,
                    )
                }
                laplacianVariance(GrayImage(halfWidth, halfHeight, pixels).toMat())
            }.sorted()

            val median = (sharpnesses[1] + sharpnesses[2]) / 2.0
            return if (median <= 0.0) 1.0 else sharpnesses.first() / median
        }
    }
}
