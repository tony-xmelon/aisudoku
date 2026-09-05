package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs

/** Why a photograph cannot be used, and what the user should do about it. */
sealed interface RejectionReason {
    val message: String

    data object NoGrid : RejectionReason {
        override val message = "Point the camera at a sudoku puzzle."
    }

    data object GridTooSmall : RejectionReason {
        override val message = "Move closer - the grid is too small to read."
    }

    data object GridCutOff : RejectionReason {
        override val message = "Fit the whole grid in view."
    }

    data object TooSkewed : RejectionReason {
        override val message = "Hold the phone flat above the puzzle."
    }

    data object NotUpright : RejectionReason {
        override val message = "Turn the phone so the puzzle is upright."
    }

    data object LinesNotFound : RejectionReason {
        override val message = "Could not make out the grid lines - try again."
    }
}

/** The structural early-out of spec section 4.1. */
sealed interface GateVerdict {

    data class Usable(
        val quad: Quad,
        val gridScore: Double,
        val rectified: GrayImage,
        val geometry: CellGeometry,
        val cells: List<GrayImage>,
        val quality: ImageQuality,
        /**
         * What was wrong with the framing, when something was, and nothing was done about
         * it. Worth repeating to the user beside a poor reading - "the grid was small" is
         * the likeliest explanation for a page full of wrong digits - and never a reason
         * to refuse the photograph.
         */
        val complaint: RejectionReason? = null,
    ) : GateVerdict

    data class Rejected(val reason: RejectionReason) : GateVerdict
}

/**
 * Decides whether a captured photograph can be processed at all.
 *
 * It used to reject on any of half a dozen counts. It now rejects on one: no grid was
 * found. Everything else it can say - the grid is small, it is tilted, it runs off the
 * edge - is a remark about the framing and not a verdict on the photograph, and a remark
 * is no reason to throw away a puzzle that has been located and straightened. What comes
 * back is read as well as it can be, the complaint travels with it, and the person
 * looking at the screen decides whether the reading is good enough.
 *
 * That is a change of principle worth stating: the app cannot tell a photograph that will
 * read badly from one that will read fine - image metrics reject usable photographs and
 * pass unusable ones, which is why blur was never a rejection - so it should not pretend
 * to. It can tell whether it found a grid. That is the whole of what it now decides.
 *
 * The live advice in the viewfinder is untouched and still says all of these things while
 * the user is aiming, which is when they are worth saying. See [FramingAdvisor].
 */
object StructuralGate {

    /**
     * Minimum rectified grid side, in source pixels: about 61 pixels to a cell.
     *
     * It was 700, chosen from how many pixels a cell ought to want rather than from what
     * the reader can do, and it was turning away photographs that read perfectly. Grids
     * of 681 and 692 pixels arrived from a reader and were refused; let through, both
     * were read confidently and correctly.
     *
     * Measured properly by shrinking every labelled photograph until its grid is a given
     * size and putting it back through the whole pipeline - see SmallestReadableGridTest.
     * Printed digits come out perfect at every size down to 360, and handwriting is
     * perfect to 440 and misses two of 175 at 400.
     *
     * This sits well above that, deliberately. Shrinking a sharp photograph is kinder
     * than standing further back with the same camera: the sweep loses resolution without
     * gaining blur or noise, so the measured edge flatters what a real distant shot would
     * do. 550 keeps most of the ground the old number gave away while staying clear of
     * anything measured to fail.
     */
    private const val MIN_GRID_SIDE = 550.0

    /**
     * The three shape limits, shared with [FramingAdvisor].
     *
     * They are internal rather than private because the advisor must not be looser than
     * the gate on any of them. When it was, the app fired the shutter by itself on
     * framing this object then refused - "hold still", a photograph, and an instruction
     * to hold the phone flat, over and over, with nothing on screen having warned that
     * the shot was not going to be accepted. Steering the user and judging the result are
     * allowed to differ in how they are worded, never in where the line is.
     */
    internal const val MAX_OPPOSITE_SIDE_RATIO = 1.25
    internal const val MAX_CORNER_ANGLE_DEVIATION = 15.0
    internal const val MAX_ROTATION_DEGREES = 15.0

    /**
     * How close to the frame edge a corner may sit before the grid is treated as clipped.
     *
     * Absolute pixels, and deliberately tiny. A corpus photograph has its grid 4px from
     * the right edge and is perfectly usable, so any proportional margin rejects good
     * input. A grid that really is cut off has its contour running along the frame
     * boundary, putting a corner within a pixel or two of it — and it also loses one of
     * the twenty lines [GridScorer] requires, so the score catches it independently.
     * This check exists only to turn that into a specific instruction for the user.
     */
    private const val EDGE_MARGIN_PIXELS = 3.0

    fun assess(image: GrayImage): GateVerdict {
        val located = GridLocator.locate(image)
        if (located !is GridLocation.Found) return GateVerdict.Rejected(RejectionReason.NoGrid)

        val quad = located.quad
        val geometry = GridLineFitter.fit(located.rectified)
            ?: CellGeometry.evenNinths(located.rectified.width)

        return GateVerdict.Usable(
            quad = quad,
            gridScore = located.gridScore,
            rectified = located.rectified,
            geometry = geometry,
            cells = CellExtractor.extract(located.rectified, geometry),
            quality = ImageQuality.of(located.rectified),
            complaint = complain(quad, image),
        )
    }

    /** What is wrong with how this was framed, if anything. Advice, not a verdict. */
    internal fun complain(quad: Quad, image: GrayImage): RejectionReason? {
        val shortestSide = minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        if (shortestSide < MIN_GRID_SIDE) return RejectionReason.GridTooSmall

        val clipped = quad.corners.any {
            it.x <= EDGE_MARGIN_PIXELS || it.y <= EDGE_MARGIN_PIXELS ||
                it.x >= image.width - EDGE_MARGIN_PIXELS ||
                it.y >= image.height - EDGE_MARGIN_PIXELS
        }
        if (clipped) return RejectionReason.GridCutOff

        if (quad.oppositeSideRatio > MAX_OPPOSITE_SIDE_RATIO ||
            quad.maxCornerAngleDeviation > MAX_CORNER_ANGLE_DEVIATION
        ) {
            return RejectionReason.TooSkewed
        }

        if (abs(quad.rotationDegrees) > MAX_ROTATION_DEGREES) return RejectionReason.NotUpright
        return null
    }
}
