package io.github.tonyxmelon.aisudoku.vision

/** Cuts the 81 cell images out of a rectified grid. */
object CellExtractor {

    /**
     * Returns 81 cell images in row-major order, cropped inside the printed lines.
     *
     * Cells keep their natural pixel size rather than being scaled to a fixed box.
     * Normalisation is the classifier's job and depends on the ink inside the cell, not
     * on the cell itself.
     */
    fun extract(rectified: GrayImage, geometry: CellGeometry): List<GrayImage> =
        (0 until 81).map { index -> crop(rectified, geometry.cellBounds(index)) }

    private fun crop(source: GrayImage, bounds: CellBounds): GrayImage {
        val left = bounds.left.coerceIn(0, source.width - 1)
        val top = bounds.top.coerceIn(0, source.height - 1)
        val right = bounds.right.coerceIn(left + 1, source.width)
        val bottom = bounds.bottom.coerceIn(top + 1, source.height)

        val width = right - left
        val height = bottom - top
        val pixels = ByteArray(width * height)
        for (y in 0 until height) {
            System.arraycopy(
                source.pixels, (top + y) * source.width + left,
                pixels, y * width, width,
            )
        }
        return GrayImage(width, height, pixels)
    }
}
