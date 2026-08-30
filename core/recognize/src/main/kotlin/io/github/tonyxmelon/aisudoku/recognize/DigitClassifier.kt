package io.github.tonyxmelon.aisudoku.recognize

import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * The digit classifier, run directly in Kotlin.
 *
 * The network is small — about 105k parameters — so a hand-written forward pass is a
 * few hundred lines and costs nothing at runtime for 81 cells. That avoids LiteRT
 * entirely: no native dependency, nothing added to the APK beyond a 420KB weights file,
 * and the whole inference path stays testable on the JVM.
 *
 * Architecture, matching `tools/recognizer/train.py`:
 *   conv 1->16 3x3 pad 1, relu, maxpool 2   ->  16 x 14 x 14
 *   conv 16->32 3x3 pad 1, relu, maxpool 2  ->  32 x 7 x 7
 *   dense 1568 -> 64, relu
 *   dense 64 -> 9
 */
class DigitClassifier private constructor(
    private val c1w: FloatArray, private val c1b: FloatArray,
    private val c2w: FloatArray, private val c2b: FloatArray,
    private val f1w: FloatArray, private val f1b: FloatArray,
    private val f2w: FloatArray, private val f2b: FloatArray,
) {

    /** Probabilities for digits 1..9, in order. Input is a 28x28 image in `0..1`. */
    fun classify(image: FloatArray): FloatArray {
        require(image.size == 28 * 28) { "expected a 28x28 image but got ${image.size} values" }

        val a1 = FloatArray(C1 * 28 * 28)
        conv(image, 1, 28, a1, C1, c1w, c1b)
        relu(a1)
        val p1 = FloatArray(C1 * 14 * 14)
        maxPool(a1, C1, 28, p1)

        val a2 = FloatArray(C2 * 14 * 14)
        conv(p1, C1, 14, a2, C2, c2w, c2b)
        relu(a2)
        val p2 = FloatArray(C2 * 7 * 7)
        maxPool(a2, C2, 14, p2)

        val h = FloatArray(HIDDEN)
        dense(p2, h, f1w, f1b)
        relu(h)

        val logits = FloatArray(CLASSES)
        dense(h, logits, f2w, f2b)
        return softmax(logits)
    }

    /** 3x3 convolution with padding 1, stride 1. */
    private fun conv(
        input: FloatArray, inChannels: Int, size: Int,
        output: FloatArray, outChannels: Int,
        weight: FloatArray, bias: FloatArray,
    ) {
        val plane = size * size
        for (oc in 0 until outChannels) {
            val outBase = oc * plane
            val biasValue = bias[oc]
            for (y in 0 until size) {
                for (x in 0 until size) {
                    var sum = biasValue
                    for (ic in 0 until inChannels) {
                        val inBase = ic * plane
                        val wBase = ((oc * inChannels) + ic) * 9
                        for (ky in 0 until 3) {
                            val sy = y + ky - 1
                            if (sy < 0 || sy >= size) continue
                            for (kx in 0 until 3) {
                                val sx = x + kx - 1
                                if (sx < 0 || sx >= size) continue
                                sum += input[inBase + sy * size + sx] * weight[wBase + ky * 3 + kx]
                            }
                        }
                    }
                    output[outBase + y * size + x] = sum
                }
            }
        }
    }

    /** 2x2 max pooling, stride 2. */
    private fun maxPool(input: FloatArray, channels: Int, size: Int, output: FloatArray) {
        val half = size / 2
        for (c in 0 until channels) {
            val inBase = c * size * size
            val outBase = c * half * half
            for (y in 0 until half) {
                for (x in 0 until half) {
                    val a = input[inBase + (2 * y) * size + 2 * x]
                    val b = input[inBase + (2 * y) * size + 2 * x + 1]
                    val c2 = input[inBase + (2 * y + 1) * size + 2 * x]
                    val d = input[inBase + (2 * y + 1) * size + 2 * x + 1]
                    output[outBase + y * half + x] = maxOf(maxOf(a, b), maxOf(c2, d))
                }
            }
        }
    }

    private fun dense(input: FloatArray, output: FloatArray, weight: FloatArray, bias: FloatArray) {
        for (o in output.indices) {
            var sum = bias[o]
            val base = o * input.size
            for (i in input.indices) sum += input[i] * weight[base + i]
            output[o] = sum
        }
    }

    private fun relu(values: FloatArray) {
        for (i in values.indices) if (values[i] < 0f) values[i] = 0f
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val max = logits.max()
        var total = 0.0
        val out = FloatArray(logits.size)
        for (i in logits.indices) {
            val e = exp((logits[i] - max).toDouble())
            out[i] = e.toFloat()
            total += e
        }
        for (i in out.indices) out[i] = (out[i] / total).toFloat()
        return out
    }

    companion object {
        private const val C1 = 16
        private const val C2 = 32
        private const val HIDDEN = 64
        const val CLASSES = 9

        private const val RESOURCE = "/digits.bin"

        /** Loads the weights shipped alongside this module. */
        fun load(): DigitClassifier {
            val stream = DigitClassifier::class.java.getResourceAsStream(RESOURCE)
                ?: error("$RESOURCE is missing from the classpath")
            return stream.use { read(it.readBytes()) }
        }

        internal fun read(bytes: ByteArray): DigitClassifier {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also { buffer.get(it) }
            require(String(magic) == "SUDK") { "not a digit model file" }
            require(buffer.int == 1) { "unsupported model format version" }

            fun floats(count: Int) = FloatArray(count).also { buffer.asFloatBuffer().get(it); buffer.position(buffer.position() + count * 4) }

            return DigitClassifier(
                c1w = floats(C1 * 1 * 9), c1b = floats(C1),
                c2w = floats(C2 * C1 * 9), c2b = floats(C2),
                f1w = floats(HIDDEN * C2 * 7 * 7), f1b = floats(HIDDEN),
                f2w = floats(CLASSES * HIDDEN), f2b = floats(CLASSES),
            )
        }

        /** Unused parameter kept for clarity at call sites that stream from a file. */
        @Suppress("unused")
        internal fun read(stream: DataInputStream): DigitClassifier = read(stream.readBytes())
    }
}
