package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shutter must never fire by itself on framing the gate is going to refuse.
 *
 * When the advisor was looser than the gate, the app auto-captured at between 15 and 18
 * degrees of corner deviation, and at any amount of perspective at all, then refused the
 * result. From the user's side that is the app taking a photograph of its own accord and
 * complaining about it - and it is the reason a straightforward page took a long time to
 * capture. These check the limits themselves, so no photograph is needed and CI runs them.
 */
class AdvisorAgreesWithGateTest {

    /** A quad the gate would accept: square, flat, upright. */
    private fun square(side: Double = 800.0, at: Double = 200.0) = Quad(
        topLeft = Corner(at, at),
        topRight = Corner(at + side, at),
        bottomRight = Corner(at + side, at + side),
        bottomLeft = Corner(at, at + side),
    )

    /** Slides the bottom edge sideways, which is what tilting the phone off flat does. */
    private fun sheared(by: Double) = square().let {
        it.copy(
            bottomLeft = Corner(it.bottomLeft.x + by, it.bottomLeft.y),
            bottomRight = Corner(it.bottomRight.x + by, it.bottomRight.y),
        )
    }

    /** Narrows the far edge, which is what looking at the page from an angle does. */
    private fun inPerspective(farEdgeScale: Double) = square().let {
        val centre = (it.topLeft.x + it.topRight.x) / 2
        val half = (it.topRight.x - it.topLeft.x) / 2 * farEdgeScale
        it.copy(
            topLeft = Corner(centre - half, it.topLeft.y),
            topRight = Corner(centre + half, it.topRight.y),
        )
    }

    private fun gateWouldRefuse(quad: Quad): Boolean =
        quad.oppositeSideRatio > StructuralGate.MAX_OPPOSITE_SIDE_RATIO ||
            quad.maxCornerAngleDeviation > StructuralGate.MAX_CORNER_ANGLE_DEVIATION ||
            abs(quad.rotationDegrees) > StructuralGate.MAX_ROTATION_DEGREES

    /** The real advisor's own rule, so that this cannot pass while the advisor drifts. */
    private fun advisorWouldSteer(quad: Quad): Boolean =
        FramingAdvisor().shapeComplaint(quad) != null

    @Test
    fun `nothing the gate refuses is left uncorrected by the advice`() {
        // Walk a square page away from ideal in each direction in turn, and check that
        // the advice speaks up no later than the gate does.
        val shapes = buildList {
            for (shear in 0..500 step 10) add(sheared(shear.toDouble()))
            for (scale in 60..100) add(inPerspective(scale / 100.0))
        }

        val silentButRefused = shapes.filter { gateWouldRefuse(it) && !advisorWouldSteer(it) }
        assertTrue(
            silentButRefused.isEmpty(),
            "${silentButRefused.size} shapes would be captured and then refused, " +
                "e.g. ratio ${silentButRefused.firstOrNull()?.oppositeSideRatio} " +
                "deviation ${silentButRefused.firstOrNull()?.maxCornerAngleDeviation}",
        )
    }

    @Test
    fun `a page held square and flat is not nagged`() {
        assertTrue(!advisorWouldSteer(square()), "a square page should draw no complaint")
        assertTrue(!gateWouldRefuse(square()), "a square page should not be refused")
    }

    @Test
    fun `perspective the gate refuses is now steered on`() {
        // The far edge four fifths the width of the near one: a ratio of 1.25, which the
        // gate has always refused and the advice used never to mention.
        val steep = inPerspective(0.70)
        assertTrue(gateWouldRefuse(steep), "the gate should refuse this much perspective")
        assertTrue(advisorWouldSteer(steep), "the advice should have asked for it flat")
    }
}
