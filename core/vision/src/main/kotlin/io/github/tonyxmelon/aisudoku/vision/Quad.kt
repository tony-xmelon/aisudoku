package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A point in image coordinates. */
data class Corner(val x: Double, val y: Double)

/**
 * Four corners in clockwise order from the top left.
 *
 * The derived properties are the geometric checks of spec section 4.1, kept here so the
 * gate and the framing advisor ask the same questions the same way.
 */
data class Quad(
    val topLeft: Corner,
    val topRight: Corner,
    val bottomRight: Corner,
    val bottomLeft: Corner,
) {
    val corners: List<Corner> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    private fun sideLength(a: Corner, b: Corner) = hypot(a.x - b.x, a.y - b.y)

    val topEdge: Double get() = sideLength(topLeft, topRight)
    val rightEdge: Double get() = sideLength(topRight, bottomRight)
    val bottomEdge: Double get() = sideLength(bottomRight, bottomLeft)
    val leftEdge: Double get() = sideLength(bottomLeft, topLeft)

    /** Worse of the two opposite-side ratios. 1.0 is a parallelogram; perspective raises it. */
    val oppositeSideRatio: Double
        get() = max(
            max(topEdge, bottomEdge) / min(topEdge, bottomEdge),
            max(leftEdge, rightEdge) / min(leftEdge, rightEdge),
        )

    /** Area by the shoelace formula. */
    val area: Double
        get() {
            var sum = 0.0
            val c = corners
            for (i in c.indices) {
                val a = c[i]
                val b = c[(i + 1) % c.size]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2.0
        }

    /** Tilt of the top edge from horizontal, in degrees, negative anticlockwise. */
    val rotationDegrees: Double
        get() = Math.toDegrees(atan2(topRight.y - topLeft.y, topRight.x - topLeft.x))

    /** Largest departure of any interior angle from 90 degrees, in degrees. */
    val maxCornerAngleDeviation: Double
        get() {
            val c = corners
            return c.indices.maxOf { i ->
                val prev = c[(i + 3) % 4]
                val here = c[i]
                val next = c[(i + 1) % 4]
                val a = atan2(prev.y - here.y, prev.x - here.x)
                val b = atan2(next.y - here.y, next.x - here.x)
                var angle = Math.toDegrees(abs(a - b))
                if (angle > 180.0) angle = 360.0 - angle
                abs(angle - 90.0)
            }
        }

    companion object {
        /**
         * Puts unordered points into corner order.
         *
         * The top left has the smallest x+y and the bottom right the largest; the top
         * right has the smallest y-x and the bottom left the largest. This holds for any
         * convex quad that is not rotated past 45 degrees, which the gate requires anyway.
         */
        fun ordering(points: List<Corner>): Quad {
            require(points.size >= 4) { "need at least 4 points but got ${points.size}" }
            return Quad(
                topLeft = points.minBy { it.x + it.y },
                topRight = points.minBy { it.y - it.x },
                bottomRight = points.maxBy { it.x + it.y },
                bottomLeft = points.maxBy { it.y - it.x },
            )
        }
    }
}
