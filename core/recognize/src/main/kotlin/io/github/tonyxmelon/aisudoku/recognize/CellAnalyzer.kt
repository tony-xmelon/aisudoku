package io.github.tonyxmelon.aisudoku.recognize

import io.github.tonyxmelon.aisudoku.vision.GrayImage
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** One ink blob found inside a cell. */
data class Blob(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val area: Int,
    /** Blob height as a fraction of the cell height. The primary size signal. */
    val heightRatio: Double,
    /** Vertical centre offset from the cell centre, as a fraction of cell height. */
    val verticalOffset: Double,
    /** Mean darkness of the blob's pixels, 0 (black) to 255 (white). */
    val darkness: Double,
    internal val maskLabel: Int,
)

/** What a cell contains. */
data class CellAnalysis(
    val digit: Blob?,
    val discardedMarks: Int,
    /** The digit normalised to a 28x28 MNIST-style bitmap, or null when there is none. */
    val normalised: FloatArray?,
    /** The biggest blob found, digit or not. Used when choosing a threshold. */
    val largestBlob: Blob? = null,
) {
    val hasDigit: Boolean get() = digit != null

    override fun equals(other: Any?): Boolean =
        other is CellAnalysis && other.digit == digit && other.discardedMarks == discardedMarks

    override fun hashCode(): Int = 31 * (digit?.hashCode() ?: 0) + discardedMarks
}

/**
 * Finds the digit in a cell, if there is one, and normalises it for the classifier.
 *
 * Every threshold here was measured against the corpus rather than chosen. Two of them
 * corrected assumptions in the design:
 *
 * Blobs are *not* discarded for touching the cell border. Doing that loses three
 * quarters of the handwritten digits, because people write larger than the print and
 * their strokes reach the edge; printed digits never do, so the mistake is invisible on
 * an unsolved puzzle. Grid-line remnants are identified by shape instead.
 *
 * And handwriting is larger than print, not smaller. Measured blob height over cell
 * height: printed givens 0.56 to 0.64, handwritten answers 0.68 to 1.00, empty cells
 * carrying candidate marks up to 0.51.
 */
object CellAnalyzer {

    /** Window for the local mean used as the ink threshold. */
    private const val LOCAL_WINDOW = 31

    /** How far below the local mean a pixel must be to count as ink. */
    private const val INK_MARGIN = 6.0

    /** Smallest blob worth considering, in pixels. */
    private const val MIN_BLOB_AREA = 12

    /**
     * Smallest blob height, as a fraction of cell height, that counts as a digit.
     *
     * Measured: the smallest real digit is 0.556 and the largest candidate mark is
     * 0.505, so this sits in a 0.05 gap. It is the narrowest margin in the pipeline.
     */
    const val MIN_DIGIT_HEIGHT_RATIO = 0.53

    /** A blob spanning the cell one way while this thin the other is a grid line. */
    private const val LINE_SPAN = 0.80
    private const val LINE_THICKNESS = 0.20

    /**
     * Finds the largest non-line blob in a cell, whatever its size.
     *
     * Whether that blob is a digit or a pencilled candidate mark is decided by
     * [io.github.tonyxmelon.aisudoku.recognize.GridReader], which can compare all 81
     * cells at once and set the threshold from the printed digits actually present. A
     * fixed threshold here fails on a puzzle covered in annotations, where a large
     * candidate mark can out-measure the constant.
     */
    fun analyse(cell: GrayImage, minHeightRatio: Double = MIN_DIGIT_HEIGHT_RATIO): CellAnalysis {
        val gray = Mat(cell.height, cell.width, CvType.CV_8UC1).also { it.put(0, 0, cell.pixels) }
        val blobs = findBlobs(gray, cell)

        val largest = blobs.maxByOrNull { it.area }
        val digit = largest?.takeIf { it.heightRatio >= minHeightRatio }

        val discarded = blobs.count { it !== digit }
        val normalised = digit?.let { normalise(gray, it, cell) }
        return CellAnalysis(digit, discarded, normalised, largestBlob = largest)
    }

    /** The largest non-line blob in each cell, or null where there is none. */
    fun largestBlobs(cells: List<GrayImage>): List<Blob?> = cells.map { cell ->
        val gray = Mat(cell.height, cell.width, CvType.CV_8UC1).also { it.put(0, 0, cell.pixels) }
        findBlobs(gray, cell).maxByOrNull { it.area }
    }

    private fun findBlobs(gray: Mat, cell: GrayImage): List<Blob> {
        val grayF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)

        val local = Mat()
        Imgproc.blur(grayF, local, Size(LOCAL_WINDOW.toDouble(), LOCAL_WINDOW.toDouble()),
            org.opencv.core.Point(-1.0, -1.0), Core.BORDER_REFLECT)

        val threshold = Mat()
        Core.subtract(local, org.opencv.core.Scalar(INK_MARGIN), threshold)

        val mask = Mat()
        Core.compare(grayF, threshold, mask, Core.CMP_LT)

        // Remove single-pixel speckle from paper texture before labelling.
        val opened = Mat()
        Imgproc.morphologyEx(
            mask, opened, Imgproc.MORPH_OPEN,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0)),
        )

        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        val count = Imgproc.connectedComponentsWithStats(opened, labels, stats, centroids)

        val out = mutableListOf<Blob>()
        for (label in 1 until count) {
            val left = stats.get(label, Imgproc.CC_STAT_LEFT)[0].toInt()
            val top = stats.get(label, Imgproc.CC_STAT_TOP)[0].toInt()
            val width = stats.get(label, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val height = stats.get(label, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            val area = stats.get(label, Imgproc.CC_STAT_AREA)[0].toInt()
            if (area < MIN_BLOB_AREA) continue

            val lineLike =
                (width >= LINE_SPAN * cell.width && height <= LINE_THICKNESS * cell.height) ||
                    (height >= LINE_SPAN * cell.height && width <= LINE_THICKNESS * cell.width)
            if (lineLike) continue

            out += Blob(
                left = left, top = top, width = width, height = height, area = area,
                heightRatio = height.toDouble() / cell.height,
                verticalOffset = ((top + height / 2.0) - cell.height / 2.0) / cell.height,
                darkness = meanDarkness(cell, labels, label, left, top, width, height),
                maskLabel = label,
            )
        }
        return out
    }

    private fun meanDarkness(
        cell: GrayImage, labels: Mat, label: Int,
        left: Int, top: Int, width: Int, height: Int,
    ): Double {
        var total = 0L
        var n = 0
        for (y in top until top + height) {
            for (x in left until left + width) {
                if (labels.get(y, x)[0].toInt() == label) {
                    total += cell[x, y]
                    n++
                }
            }
        }
        return if (n == 0) 255.0 else total.toDouble() / n
    }

    /**
     * The MNIST convention: the digit scaled so its longest side is 20 pixels, then
     * centred by mass in a 28x28 box. Matching this matters more than the model does.
     */
    private fun normalise(gray: Mat, blob: Blob, cell: GrayImage): FloatArray {
        val grayF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)
        val local = Mat()
        Imgproc.blur(grayF, local, Size(LOCAL_WINDOW.toDouble(), LOCAL_WINDOW.toDouble()),
            org.opencv.core.Point(-1.0, -1.0), Core.BORDER_REFLECT)
        val threshold = Mat()
        Core.subtract(local, org.opencv.core.Scalar(INK_MARGIN), threshold)
        val mask = Mat()
        Core.compare(grayF, threshold, mask, Core.CMP_LT)
        val opened = Mat()
        Imgproc.morphologyEx(
            mask, opened, Imgproc.MORPH_OPEN,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(2.0, 2.0)),
        )
        val labels = Mat()
        val stats = Mat()
        Imgproc.connectedComponentsWithStats(opened, labels, stats, Mat())

        val ink = Mat.zeros(blob.height, blob.width, CvType.CV_32F)
        for (y in 0 until blob.height) {
            for (x in 0 until blob.width) {
                if (labels.get(blob.top + y, blob.left + x)[0].toInt() == blob.maskLabel) {
                    ink.put(y, x, 1.0)
                }
            }
        }

        val scale = 20.0 / maxOf(blob.width, blob.height)
        val newWidth = maxOf(1, Math.round(blob.width * scale).toInt())
        val newHeight = maxOf(1, Math.round(blob.height * scale).toInt())
        val small = Mat()
        Imgproc.resize(ink, small, Size(newWidth.toDouble(), newHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_LINEAR)

        var massY = 0.0
        var massX = 0.0
        var mass = 0.0
        val buffer = FloatArray(newWidth * newHeight)
        small.get(0, 0, buffer)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                val v = buffer[y * newWidth + x]
                massY += y * v
                massX += x * v
                mass += v
            }
        }
        val centreY = if (mass > 0) massY / mass else newHeight / 2.0
        val centreX = if (mass > 0) massX / mass else newWidth / 2.0

        val top = Math.round(14 - centreY).toInt().coerceIn(0, 28 - newHeight)
        val left = Math.round(14 - centreX).toInt().coerceIn(0, 28 - newWidth)

        val out = FloatArray(28 * 28)
        for (y in 0 until newHeight) {
            for (x in 0 until newWidth) {
                out[(top + y) * 28 + (left + x)] = buffer[y * newWidth + x]
            }
        }
        return out
    }
}
