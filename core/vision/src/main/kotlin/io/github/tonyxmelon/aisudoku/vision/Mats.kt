package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Conversion between [GrayImage] and OpenCV's `Mat`.
 *
 * Internal on purpose: `Mat` must not appear in this module's public API, or every
 * consumer inherits an OpenCV dependency and the abstraction is worthless.
 */
internal fun GrayImage.toMat(): Mat =
    Mat(height, width, CvType.CV_8UC1).also { it.put(0, 0, pixels) }

internal fun Mat.toGrayImage(): GrayImage {
    require(type() == CvType.CV_8UC1) { "expected 8-bit single channel but got ${type()}" }
    val buffer = ByteArray(rows() * cols())
    get(0, 0, buffer)
    return GrayImage(cols(), rows(), buffer)
}
