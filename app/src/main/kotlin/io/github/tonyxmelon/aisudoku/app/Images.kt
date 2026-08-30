package io.github.tonyxmelon.aisudoku.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageProxy
import io.github.tonyxmelon.aisudoku.vision.GrayImage

/**
 * Conversions from what Android hands us into the one type the vision module accepts.
 *
 * The preview path is free: a YUV_420_888 frame's first plane *is* the luma image, so
 * grayscale conversion is a copy rather than a computation. That matters when it runs
 * on every frame.
 */
object Images {

    /** The luma plane of a camera frame, honouring its row stride. */
    fun fromPreview(proxy: ImageProxy): GrayImage {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val width = proxy.width
        val height = proxy.height

        val pixels = ByteArray(width * height)
        val row = ByteArray(rowStride)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val available = minOf(rowStride, buffer.remaining())
            buffer.get(row, 0, available)
            System.arraycopy(row, 0, pixels, y * width, minOf(width, available))
        }
        return GrayImage(width, height, pixels)
    }

    /** A captured JPEG, rotated upright and converted to grayscale. */
    fun fromJpeg(bytes: ByteArray, rotationDegrees: Int): GrayImage {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("could not decode the captured image")
        val upright = if (rotationDegrees == 0) decoded else rotate(decoded, rotationDegrees)
        return fromBitmap(upright)
    }

    fun fromBitmap(bitmap: Bitmap): GrayImage {
        val width = bitmap.width
        val height = bitmap.height
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)

        val pixels = ByteArray(width * height)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            // Rec. 601 luma, matching what the JVM side uses so measurements transfer.
            pixels[i] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
        return GrayImage(width, height, pixels)
    }

    /** A grayscale image back to a bitmap, for display. */
    fun toBitmap(image: GrayImage): Bitmap {
        val argb = IntArray(image.width * image.height)
        for (i in argb.indices) {
            val v = image.pixels[i].toInt() and 0xFF
            argb[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(argb, image.width, image.height, Bitmap.Config.ARGB_8888)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
