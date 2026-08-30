package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Synthesises degraded photographs from good ones.
 *
 * The corpus contains no unusable photographs, so the rejection paths would otherwise
 * have no coverage at all. Each function here introduces exactly one fault, which real
 * bad photographs do not - they combine faults - so these complement hand-shot rejects
 * rather than replacing them.
 */
object Degrade {

    fun blur(image: GrayImage, radius: Int): GrayImage {
        val kernel = (radius * 2 + 1).toDouble()
        val out = Mat()
        Imgproc.GaussianBlur(image.toMat(), out, Size(kernel, kernel), 0.0)
        return out.toGrayImage()
    }

    /** Shrinks the whole photo, so the grid falls below the pixels-per-cell floor. */
    fun shrink(image: GrayImage, factor: Double): GrayImage {
        val out = Mat()
        Imgproc.resize(image.toMat(), out, Size(image.width * factor, image.height * factor))
        return out.toGrayImage()
    }

    /** Crops away a fraction of the left side, cutting the grid out of frame. */
    fun cropLeft(image: GrayImage, fraction: Double): GrayImage {
        val cut = (image.width * fraction).toInt()
        val width = image.width - cut
        val pixels = ByteArray(width * image.height)
        for (y in 0 until image.height) {
            System.arraycopy(image.pixels, y * image.width + cut, pixels, y * width, width)
        }
        return GrayImage(width, image.height, pixels)
    }

    /** Scales every pixel toward black. */
    fun darken(image: GrayImage, factor: Double): GrayImage =
        GrayImage(image.width, image.height, ByteArray(image.pixels.size) {
            (((image.pixels[it].toInt() and 0xFF) * factor).toInt().coerceIn(0, 255)).toByte()
        })

    /** Blows out a rectangular patch to pure white, as a specular highlight does. */
    fun addGlare(image: GrayImage, fraction: Double): GrayImage {
        val pixels = image.pixels.copyOf()
        val patchWidth = (image.width * fraction).toInt()
        val patchHeight = (image.height * fraction).toInt()
        val startX = (image.width - patchWidth) / 2
        val startY = (image.height - patchHeight) / 2
        for (y in startY until startY + patchHeight) {
            for (x in startX until startX + patchWidth) {
                pixels[y * image.width + x] = 255.toByte()
            }
        }
        return GrayImage(image.width, image.height, pixels)
    }

    /** Rotates about the centre, to push the grid past the upright tolerance. */
    fun rotate(image: GrayImage, degrees: Double): GrayImage {
        val centre = Point(image.width / 2.0, image.height / 2.0)
        val rotation = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
        val out = Mat()
        Imgproc.warpAffine(
            image.toMat(), out, rotation, Size(image.width.toDouble(), image.height.toDouble()),
            Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(255.0),
        )
        return out.toGrayImage()
    }
}
