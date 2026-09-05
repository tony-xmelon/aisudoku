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
    /** Blob width over blob height. A digit is taller than it is wide; a ring is not. */
    val aspect: Double,
    /** Blob height as a fraction of the cell height. The primary size signal. */
    val heightRatio: Double,
    /** Vertical centre offset from the cell centre, as a fraction of cell height. */
    val verticalOffset: Double,
    /** Mean darkness of the blob's pixels, 0 (black) to 255 (white). */
    val darkness: Double,
    /**
     * How far the blob stands out from the paper of its own cell, in grey levels.
     *
     * Measured against the paper beside it rather than against the page, so a crease or
     * a shadow across the sheet moves the blob and its background together and leaves
     * this alone. It is the second of the three things printed digits share - colour -
     * put in a form that survives a photograph taken on a bent page.
     */
    val contrast: Double,
    /**
     * Mean stroke thickness in pixels: ink area over the longer side.
     *
     * Printed digits are set in one weight, so this barely varies among them, while a
     * pencilled candidate mark is a thin scratch. It is the third of the three things
     * printed digits share - font, colour and size - reduced to a number.
     */
    val strokeWidth: Double,
    internal val maskLabel: Int,
)

/**
 * The ink of one cell: its biggest blob, and that blob ready for the classifier.
 *
 * The two measurements beside it are about the blob's standing in its own square, which
 * is what tells fresh ink from what is left of a rubbed-out digit.
 */
class CellInk(
    val blob: Blob,
    val normalised: FloatArray,
    /**
     * How much darker the darkest other blob in the same cell is, in grey levels; zero
     * when the biggest blob is also the darkest, which is the ordinary case.
     */
    val outshoneBy: Double,
    /**
     * How many other pieces of ink of a comparable size share the cell.
     *
     * Candidate marks are written in groups - that is what makes them candidates - while
     * an answer is usually the only thing in its square: 92% of the corpus's answers have
     * nothing else beside them, against 15% of the squares that hold only marks.
     */
    val company: Int,
)

/**
 * Finds the ink in a cell and measures it, without judging what it is.
 *
 * Every threshold here was measured against the corpus rather than chosen, and one of
 * them corrected an assumption in the design: blobs are *not* discarded for touching the
 * cell border. Doing that loses three quarters of the handwritten digits, because people
 * write larger than the print and their strokes reach the edge; printed digits never do,
 * so the mistake is invisible on an unsolved puzzle. Grid-line remnants are identified by
 * shape instead.
 *
 * Nothing here decides whether a blob is a digit. That takes all 81 cells at once and
 * belongs to [GridReader], which can measure the printed digits of this photograph and
 * judge everything else against them.
 */
object CellAnalyzer {

    /** Window for the local mean used as the ink threshold. */
    private const val LOCAL_WINDOW = 31

    /**
     * How far below the local mean a pixel must be to count as ink.
     *
     * Six missed the faintest answers on a finished grid - pencil pressed lightly over a
     * rubbed-out candidate mark, which came out as an empty square rather than as a wrong
     * digit. Swept against every cell in the corpus, four is the best there is: it sorts
     * all 648, where three and five each get one wrong and six gets two.
     *
     * A peak one step wide on 648 cells is not a law of nature. It is the measured best,
     * and the neighbours are one cell worse rather than a cliff, so it is a reasonable
     * place to stand until more finished grids say otherwise.
     *
     * It has since been asked to stretch and will not. A photograph of a screen, out of
     * focus, whose digits are light blue on white, leaves about thirty of its cells with
     * no ink found at all - and lowering this to reach them makes everything worse rather
     * than better, because what it reaches first is not the faint digits but the noise:
     * over the whole corpus, four sorts 1884 cells of 2025 and reads 695 printed digits,
     * three sorts 1882 and reads 685, two sorts 1868 and reads 676. Whatever will find
     * those cells, it is not a lower bar.
     */
    private const val INK_MARGIN = 4.0

    /** Smallest blob worth considering, in pixels. */
    private const val MIN_BLOB_AREA = 12

    /** A blob spanning the cell one way while this thin the other is a grid line. */
    private const val LINE_SPAN = 0.80
    private const val LINE_THICKNESS = 0.20

    /**
     * The largest blob of every cell, already normalised for the classifier.
     *
     * What each blob *is* - print, an answer, or a candidate mark - is not decided here.
     * That needs all 81 cells at once, and belongs to [GridReader].
     */
    fun inspect(cells: List<GrayImage>): List<CellInk?> = cells.map { cell ->
        val gray = Mat(cell.height, cell.width, CvType.CV_8UC1).also { it.put(0, 0, cell.pixels) }
        val blobs = findBlobs(gray, cell)
        val largest = blobs.maxByOrNull { it.area } ?: return@map null

        val darkest = blobs.minOf { it.darkness }

        CellInk(
            blob = largest,
            normalised = normalise(gray, largest, cell),
            outshoneBy = largest.darkness - darkest,
            company = blobs.count { it !== largest && it.heightRatio >= largest.heightRatio / 2 },
        )
    }

    internal fun findBlobs(gray: Mat, cell: GrayImage): List<Blob> {
        // The median of the cell is its paper: ink is the minority of any square, even a
        // crowded one.
        val paper = cell.pixels.map { it.toInt() and 0xFF }.sorted()[cell.pixels.size / 2]

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

            val darkness = meanDarkness(cell, labels, label, left, top, width, height)
            out += Blob(
                left = left, top = top, width = width, height = height, area = area,
                aspect = width.toDouble() / height,
                heightRatio = height.toDouble() / cell.height,
                strokeWidth = area.toDouble() / maxOf(width, height),
                verticalOffset = ((top + height / 2.0) - cell.height / 2.0) / cell.height,
                darkness = darkness,
                contrast = paper - darkness,
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
        // INTER_AREA, not INTER_LINEAR. A cell is around ninety pixels across and this
        // shrinks it to twenty, and at that reduction INTER_LINEAR is the wrong operation:
        // it samples a two-by-two neighbourhood wherever it lands, so a one-pixel stroke is
        // kept or dropped according to where the grid falls, and the result aliases.
        // INTER_AREA averages the whole source region, which is what turns a thin stroke
        // into a grey one rather than into a broken one.
        //
        // Worth about a third of a cell on the corpus, which is inside the noise of a
        // single run - it is here because it is the right operation for the reduction, and
        // because over three seeds it was never worse, not because the number proves it.
        Imgproc.resize(
            ink, small, Size(newWidth.toDouble(), newHeight.toDouble()),
            0.0, 0.0, Imgproc.INTER_AREA,
        )

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
