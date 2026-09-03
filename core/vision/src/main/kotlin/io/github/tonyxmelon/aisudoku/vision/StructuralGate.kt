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
    ) : GateVerdict

    data class Rejected(val reason: RejectionReason) : GateVerdict
}

/**
 * Decides whether a captured photograph can be processed at all.
 *
 * This is deliberately the *only* proxy check that may reject on its own. Everything
 * softer - blur, uneven lighting, a marginal-looking read - is left to the certainty
 * verdict once recognition has actually run, because image metrics reject usable
 * photographs and pass unusable ones. See spec section 4.2.
 */
object StructuralGate {

    /** Minimum rectified grid side, in source pixels, for roughly 78px per cell. */
    private const val MIN_GRID_SIDE = 700.0

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

        val shortestSide = minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        if (shortestSide < MIN_GRID_SIDE) return GateVerdict.Rejected(RejectionReason.GridTooSmall)

        val clipped = quad.corners.any {
            it.x <= EDGE_MARGIN_PIXELS || it.y <= EDGE_MARGIN_PIXELS ||
                it.x >= image.width - EDGE_MARGIN_PIXELS ||
                it.y >= image.height - EDGE_MARGIN_PIXELS
        }
        if (clipped) return GateVerdict.Rejected(RejectionReason.GridCutOff)

        if (quad.oppositeSideRatio > MAX_OPPOSITE_SIDE_RATIO ||
            quad.maxCornerAngleDeviation > MAX_CORNER_ANGLE_DEVIATION
        ) {
            return GateVerdict.Rejected(RejectionReason.TooSkewed)
        }

        if (abs(quad.rotationDegrees) > MAX_ROTATION_DEGREES) {
            return GateVerdict.Rejected(RejectionReason.NotUpright)
        }

        val geometry = GridLineFitter.fit(located.rectified)
            ?: return GateVerdict.Rejected(RejectionReason.LinesNotFound)

        return GateVerdict.Usable(
            quad = quad,
            gridScore = located.gridScore,
            rectified = located.rectified,
            geometry = geometry,
            cells = CellExtractor.extract(located.rectified, geometry),
            quality = ImageQuality.of(located.rectified),
        )
    }
}
