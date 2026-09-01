package io.github.tonyxmelon.aisudoku.vision

/**
 * Turning a camera frame into the picture the user is actually looking at.
 *
 * A phone hands its analysis frames over in sensor orientation and at the sensor's own
 * aspect ratio - landscape, four by three - while the viewfinder shows them turned
 * upright and cropped to fill a tall screen. Those are two different pictures of the same
 * scene, and judging one while the user aims the other is the whole problem: they line the
 * grid up inside the frame they can see, and the advisor is looking at a wider view where
 * the grid is smaller, off to one side, and surrounded by whatever else is on the table.
 *
 * These two operations put the frame back where the user thinks it is.
 */

/** The frame turned upright, by whatever multiple of a quarter turn the camera reports. */
fun GrayImage.rotated(degrees: Int): GrayImage {
    val turns = ((degrees % 360) + 360) % 360
    if (turns == 0) return this
    require(turns % 90 == 0) { "only quarter turns are supported, not $degrees" }

    val (outWidth, outHeight) = if (turns == 180) width to height else height to width
    val out = ByteArray(pixels.size)
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            val to = when (turns) {
                90 -> x * outWidth + (outWidth - 1 - y)
                180 -> (height - 1 - y) * outWidth + (outWidth - 1 - x)
                else -> (outHeight - 1 - x) * outWidth + y
            }
            out[to] = pixels[row + x]
        }
    }
    return GrayImage(outWidth, outHeight, out)
}

/**
 * The middle of the frame at the given width-to-height ratio, which is what a viewfinder
 * showing the whole of its shorter dimension displays.
 *
 * Returns the frame unchanged when it is already close enough, so an ordinary photograph
 * is not copied for nothing.
 */
fun GrayImage.centreCropped(aspect: Double): GrayImage {
    require(aspect > 0) { "aspect must be positive but was $aspect" }
    val current = width.toDouble() / height
    if (kotlin.math.abs(current - aspect) < 0.01) return this

    val (cropWidth, cropHeight) = if (current > aspect) {
        ((height * aspect).toInt().coerceIn(1, width)) to height
    } else {
        width to ((width / aspect).toInt().coerceIn(1, height))
    }

    val left = (width - cropWidth) / 2
    val top = (height - cropHeight) / 2
    val out = ByteArray(cropWidth * cropHeight)
    for (y in 0 until cropHeight) {
        System.arraycopy(pixels, (top + y) * width + left, out, y * cropWidth, cropWidth)
    }
    return GrayImage(cropWidth, cropHeight, out)
}
