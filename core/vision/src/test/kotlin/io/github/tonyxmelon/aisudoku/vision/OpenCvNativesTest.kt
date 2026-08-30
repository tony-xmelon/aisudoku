package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenCvNativesTest {

    @Test
    fun `loading is idempotent and leaves opencv usable`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        OpenCvNatives.ensureLoaded { error("must not run a second time") }

        val src = Mat(Size(32.0, 32.0), CvType.CV_8UC1)
        val dst = Mat()
        Imgproc.adaptiveThreshold(
            src, dst, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 11, 2.0,
        )
        assertEquals(32, dst.rows())
        assertTrue(OpenCvNatives.isLoaded)
    }
}
