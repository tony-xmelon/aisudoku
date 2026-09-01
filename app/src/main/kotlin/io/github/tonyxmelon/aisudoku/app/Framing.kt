package io.github.tonyxmelon.aisudoku.app

/** A point as fractions of something's width and height. */
data class Fraction(val x: Float, val y: Float)

/**
 * What the reader can see in the frame, ready to be drawn over the preview.
 *
 * The corners are fractions of the *analysis frame*, which is not the picture on screen:
 * the camera hands frames over in sensor orientation, and the viewfinder shows them turned
 * upright and cropped to fill a tall screen. [Framing] does that same turn and crop to the
 * corners, so the outline lands on the thing it outlines.
 */
data class Sighting(
    val corners: List<Fraction>,
    /** True when this is a grid the app accepts, rather than the nearest thing it found. */
    val accepted: Boolean,
    val rotationDegrees: Int,
    val frameWidth: Int,
    val frameHeight: Int,
)

/**
 * Where a point in the camera's frame lands on the preview showing it.
 *
 * Assumes the preview fills its view and centre-crops the overflow, which is what
 * `PreviewView.ScaleType.FILL_CENTER` does and what the camera screen asks for
 * explicitly - the mapping below is only true for that one scale type.
 */
object Framing {

    /** The point turned by the rotation the camera reports, still as fractions. */
    fun turned(point: Fraction, rotationDegrees: Int): Fraction =
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Fraction(1f - point.y, point.x)
            180 -> Fraction(1f - point.x, 1f - point.y)
            270 -> Fraction(point.y, 1f - point.x)
            else -> point
        }

    /**
     * The same point in the view's own pixels.
     *
     * Returns coordinates outside the view when the point falls in the part of the frame
     * the viewfinder has cropped away - which is honest, and is exactly what happens to a
     * shape the app has found beyond the edges of what the user can see.
     */
    fun onScreen(
        point: Fraction,
        sighting: Sighting,
        viewWidth: Float,
        viewHeight: Float,
    ): Fraction {
        val quarter = ((sighting.rotationDegrees % 360) + 360) % 360
        val sideways = quarter == 90 || quarter == 270
        val shownWidth = (if (sideways) sighting.frameHeight else sighting.frameWidth).toFloat()
        val shownHeight = (if (sideways) sighting.frameWidth else sighting.frameHeight).toFloat()
        if (shownWidth <= 0f || shownHeight <= 0f) return Fraction(0f, 0f)

        // Fill: the frame is scaled until it covers the view, and what hangs over the
        // edges is cropped away evenly on both sides.
        val scale = maxOf(viewWidth / shownWidth, viewHeight / shownHeight)
        val drawnWidth = shownWidth * scale
        val drawnHeight = shownHeight * scale

        val turned = turned(point, sighting.rotationDegrees)
        return Fraction(
            (viewWidth - drawnWidth) / 2f + turned.x * drawnWidth,
            (viewHeight - drawnHeight) / 2f + turned.y * drawnHeight,
        )
    }
}
