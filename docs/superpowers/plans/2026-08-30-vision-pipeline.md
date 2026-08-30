# Vision Pipeline Implementation Plan (M3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn a photograph into either 81 clean cell images with a known geometry, or a specific reason it could not be used.

**Architecture:** A plain Kotlin JVM module wrapping OpenCV. It never exposes an OpenCV type: input is a grayscale image, output is plain Kotlin data. Candidate quadrilaterals are found by contour, then *scored on whether they actually contain a 9x9 grid* — which is what stops the pipeline rectifying a sheet of paper instead of the puzzle on it.

**Tech Stack:** Kotlin 2.2.20, OpenCV 4.9 (`org.openpnp:opencv` on the JVM, `org.opencv:opencv` AAR on Android), `kotlin("test")`.

**Spec:** `docs/superpowers/specs/2026-08-30-camera-sudoku-solver-design.md`, sections 4 and 5.1. This plan is M3 only. The classifier and `GridReader` (M4, M5) and the app (M6–M9) are separate plans.

---

## Verified before writing — do not re-derive

Everything in this section was run against `corpus/` on this machine. Treat it as measured fact.

**OpenCV on the JVM works with no manual setup.** `org.openpnp:opencv:4.9.0-0` bundles its own natives; `nu.pattern.OpenCV.loadShared()` then `Imgproc.adaptiveThreshold` and `Imgproc.warpPerspective` all ran. The official `org.opencv:opencv` artifact is an **AAR** (Android only) — hence the two-artifact split. Both expose the same `org.opencv.*` API, so `core:vision` is written once.

**The detection parameters below located the grid in all six corpus photos.**

| Stage | Setting |
| --- | --- |
| Working resolution | longest edge scaled to 1000px for detection; warp uses the full-resolution image |
| Blur | Gaussian 5x5 |
| Binarize | `adaptiveThreshold`, `ADAPTIVE_THRESH_MEAN_C`, `THRESH_BINARY_INV`, block 31, C 7 |
| Morphology | `MORPH_CLOSE`, 3x3 rect |
| Contours | `RETR_LIST`, keep area > 8% of frame, take the 10 largest |
| Corners | `approxPolyDP` at `0.02 * perimeter`; if it does not yield 4 points, fall back to the contour's extreme points |
| Corner order | TL = min(x+y), TR = min(y−x), BR = max(x+y), BL = max(y−x) |
| Rectified size | 1152x1152, i.e. 128px per cell |
| Grid score binarize | `adaptiveThreshold`, mean, inverted, block 31, C 10 |
| Accept threshold | grid score >= 0.35 |

**Grid scores measured on the corpus:**

| Photo | Accepted score | Note |
| --- | --- | --- |
| `IMG20260830142203` | **0.41** | The important one. See below. |
| `IMG20260830142243` | 0.95 | |
| `IMG20260830142250` | 0.56 | |
| `IMG20260830142301` | 0.66 | |
| `IMG20260830142308` | 0.47 | |
| `IMG20260830142356` | 0.80 | |

**Why the scoring exists at all.** In `IMG20260830142203` the sheet of white paper on dark wood is the *largest* contour, covering 59% of the frame, and it approximates cleanly to four corners. Rectifying it produces a beautiful, completely useless image containing the puzzle number, a URL and the difficulty label. The actual grid is only the third-largest candidate at 36%. Choosing by area picks the wrong one every time; choosing by grid score picks the grid (0.41) over the paper (0.28).

**Counting grid lines does not work; scoring expected positions does.** An earlier attempt required exactly ten line peaks per axis and rejected three of the six photos, because a thick outer border splits into two peaks and digit strokes can align into a spurious one. The working test asks a different question: at each of the twenty places a grid line *must* be, how strong is the darkest one, relative to the strongest line in the image? Extra peaks are then harmless.

**Margins are thin and this is the main tuning risk.** The worst accepted photo scores 0.41 against a 0.35 threshold, and the paper decoy it beat scores 0.28. That is a real separation but not a comfortable one. Every threshold change must be re-run against the whole corpus, which is what Task 10 is for.

## Corpus is not in git

`corpus/` is gitignored and stays local, so **CI cannot run any test that needs a photograph**. Tests that need the corpus must skip cleanly rather than fail. Task 2 builds that mechanism; every later corpus test uses it. Do not work around it by committing photos.

## File structure

```
core/vision/build.gradle.kts
core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/
    GrayImage.kt          plain Kotlin image type. The module's only input.
    OpenCvNatives.kt      the load-once seam; differs on JVM and Android
    Mats.kt               internal GrayImage <-> Mat conversion. Not public.
    Quad.kt               four corners, plus the geometry questions asked of them
    QuadDetector.kt       photo -> candidate quads, largest first
    GridScorer.kt         does this warp actually contain a 9x9 grid?
    GridLocator.kt        detect + score + choose. The module's main entry point.
    GridLineFitter.kt     the real interior line positions -> CellGeometry
    CellExtractor.kt      CellGeometry -> 81 cell images
    ImageQuality.kt       sharpness, luma, glare
    StructuralGate.kt     the early-out verdict of spec 4.1
core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/
    CorpusFixtures.kt     locates corpus/, skips when absent
    Degrade.kt            synthesises reject cases from good photos
    ...one test file per class above, plus CorpusHarnessTest
tools/dump-cells/         a runnable task that writes rectified grids and cell crops to look at
```

`GrayImage` in, plain data out. OpenCV stays inside the module so the app never links against it directly and so it can be replaced without touching anything above.

---

## Task 1: The `core:vision` module, with OpenCV loading

**Files:**
- Create: `core/vision/build.gradle.kts`
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/OpenCvNatives.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/OpenCvNativesTest.kt`
- Modify: `settings.gradle.kts`, `gradle/libs.versions.toml`

- [ ] **Step 1: Add the version catalog entries**

In `gradle/libs.versions.toml`, add under `[versions]` and a new `[libraries]` section:

```toml
[versions]
kotlin = "2.2.20"
opencvJvm = "4.9.0-0"

[libraries]
opencv-jvm = { module = "org.openpnp:opencv", version.ref = "opencvJvm" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 2: Register the module**

In `settings.gradle.kts`, after `include(":core:solver")`:

```kotlin
include(":core:vision")
```

- [ ] **Step 3: Write `core/vision/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    // The OpenCV Java API is needed to compile, but the implementation is supplied by
    // whoever is running: the openpnp artifact on the JVM, the AAR on Android. Declaring
    // it compileOnly keeps the desktop natives out of the Android build.
    compileOnly(libs.opencv.jvm)
    testImplementation(libs.opencv.jvm)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
    maxHeapSize = "2g"   // full-resolution 12MP Mats
}
```

- [ ] **Step 4: Write the failing test**

```kotlin
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
```

- [ ] **Step 5: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --console=plain
```

Expected: FAIL — `Unresolved reference: OpenCvNatives`.

- [ ] **Step 6: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

/**
 * Loads the OpenCV native library exactly once.
 *
 * How to load differs by platform and the module must not care which it is on: the JVM
 * uses `nu.pattern.OpenCV.loadShared()`, Android uses `OpenCVLoader.initLocal()`. The
 * caller supplies that as a lambda the first time anything in this module is used.
 */
object OpenCvNatives {

    @Volatile
    private var loaded = false

    val isLoaded: Boolean get() = loaded

    /** Runs [loader] on the first call only. Safe to call from anywhere, any number of times. */
    @Synchronized
    fun ensureLoaded(loader: () -> Unit) {
        if (loaded) return
        loader()
        loaded = true
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Add core:vision module with OpenCV native loading

The OpenCV Java API is compileOnly so the desktop natives never reach an
Android build; the JVM test classpath supplies openpnp, Android will
supply the AAR. Same org.opencv API either way."
```

---

## Task 2: `GrayImage`, and corpus fixtures that skip when absent

`GrayImage` is the module's only input type, so nothing above it ever touches OpenCV. `CorpusFixtures` is what lets corpus tests exist at all given the photos are not in git.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/GrayImage.kt`
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/Mats.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/CorpusFixtures.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/GrayImageTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GrayImageTest {

    @Test
    fun `pixels are addressable and stored row major`() {
        val image = GrayImage(3, 2, byteArrayOf(0, 1, 2, 3, 4, 5))
        assertEquals(0, image[0, 0])
        assertEquals(2, image[2, 0])
        assertEquals(3, image[0, 1])
        assertEquals(5, image[2, 1])
    }

    @Test
    fun `pixel values are unsigned`() {
        val image = GrayImage(1, 1, byteArrayOf(-1))
        assertEquals(255, image[0, 0])
    }

    @Test
    fun `the buffer must match the dimensions`() {
        assertFailsWith<IllegalArgumentException> { GrayImage(3, 2, ByteArray(5)) }
    }

    @Test
    fun `a round trip through a Mat preserves every pixel`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val original = GrayImage(5, 4, ByteArray(20) { (it * 11).toByte() })
        val restored = original.toMat().toGrayImage()
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.pixels.toList(), restored.pixels.toList())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*GrayImageTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: GrayImage`.

- [ ] **Step 3: Write `GrayImage.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

/**
 * An 8-bit grayscale image, row-major, one byte per pixel.
 *
 * This is the only thing the vision module accepts. Callers convert to it from whatever
 * they have — `BufferedImage` on the JVM, `ImageProxy` or `Bitmap` on Android — which
 * keeps every platform image type, and OpenCV itself, out of this module's API.
 */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {

    init {
        require(pixels.size == width * height) {
            "a ${width}x$height image needs ${width * height} bytes but got ${pixels.size}"
        }
        require(width > 0 && height > 0) { "image dimensions must be positive" }
    }

    /** The pixel at ([x], [y]) as an unsigned value in `0..255`. */
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    override fun toString(): String = "GrayImage(${width}x$height)"
}
```

- [ ] **Step 4: Write `Mats.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Conversion between [GrayImage] and OpenCV's `Mat`.
 *
 * Internal on purpose: `Mat` must not appear in this module's public API, or every
 * consumer inherits an OpenCV dependency and the abstraction is worthless.
 */
internal fun GrayImage.toMat(): Mat =
    Mat(height, width, CvType.CV_8UC1).also { it.put(0, 0, pixels) }

internal fun Mat.toGrayImage(): GrayImage {
    require(type() == CvType.CV_8UC1) { "expected 8-bit single channel but got ${type()}" }
    val buffer = ByteArray(rows() * cols())
    get(0, 0, buffer)
    return GrayImage(cols(), rows(), buffer)
}
```

- [ ] **Step 5: Write the corpus fixture helper**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Access to the local photograph corpus.
 *
 * The photographs are large and deliberately not in git, so on CI this directory does
 * not exist. Corpus tests must therefore *skip*, not fail. Call [requireCorpus] first in
 * any test that needs a photograph.
 */
object CorpusFixtures {

    val directory: File = File("../../corpus").canonicalFile

    val photos: List<File>
        get() = directory.listFiles { f: File -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }
            ?: emptyList()

    val isAvailable: Boolean get() = photos.isNotEmpty()

    /** Skips the calling test when the corpus is not present, e.g. on CI. */
    fun requireCorpus() {
        assumeTrue(isAvailable, "corpus not present at $directory — skipping (expected on CI)")
    }

    fun load(file: File): GrayImage = ImageIO.read(file).toGrayImage()

    fun photo(nameFragment: String): GrayImage =
        load(photos.first { it.name.contains(nameFragment) })
}

/** Converts any `BufferedImage` to grayscale bytes, ignoring EXIF orientation. */
fun BufferedImage.toGrayImage(): GrayImage {
    val buffer = ByteArray(width * height)
    var i = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val rgb = getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            // Rec. 601 luma, the same weighting OpenCV's COLOR_BGR2GRAY uses.
            buffer[i++] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
        }
    }
    return GrayImage(width, height, buffer)
}
```

Note on EXIF: every photograph in the current corpus reports orientation 1, so no rotation is applied here. The Android capture path must handle orientation itself before building a `GrayImage`; that belongs to the app plan, not this one.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew :core:vision:test --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Add a corpus sanity test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertTrue

class CorpusFixturesTest {

    @Test
    fun `the corpus loads as grayscale images`() {
        CorpusFixtures.requireCorpus()
        for (file in CorpusFixtures.photos) {
            val image = CorpusFixtures.load(file)
            assertTrue(image.width > 500 && image.height > 500, "${file.name} is ${image}")
        }
    }
}
```

- [ ] **Step 8: Verify the skip actually works**

```bash
mv corpus corpus_hidden && ./gradlew :core:vision:test --rerun-tasks --console=plain; mv corpus_hidden corpus
```

Expected: `BUILD SUCCESSFUL` both times — with the corpus hidden, corpus tests skip rather than fail. This is exactly what CI will do, so confirm it before relying on it.

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "Add GrayImage and corpus fixtures that skip when photos are absent

The corpus is gitignored, so CI has no photographs. Corpus-dependent
tests skip rather than fail."
```

---

## Task 3: `Quad` — four corners and the questions asked of them

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/Quad.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/QuadTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuadTest {

    private val square = Quad(
        topLeft = Corner(0.0, 0.0),
        topRight = Corner(100.0, 0.0),
        bottomRight = Corner(100.0, 100.0),
        bottomLeft = Corner(0.0, 100.0),
    )

    @Test
    fun `a square has equal sides and no skew`() {
        assertEquals(1.0, square.oppositeSideRatio, 0.001)
        assertEquals(0.0, square.rotationDegrees, 0.001)
        assertTrue(square.maxCornerAngleDeviation < 0.001)
    }

    @Test
    fun `area is computed by the shoelace formula`() {
        assertEquals(10_000.0, square.area, 0.001)
    }

    @Test
    fun `a trapezoid has unequal opposite sides`() {
        val trapezoid = Quad(
            Corner(20.0, 0.0), Corner(80.0, 0.0),
            Corner(100.0, 100.0), Corner(0.0, 100.0),
        )
        assertTrue(trapezoid.oppositeSideRatio > 1.5, "${trapezoid.oppositeSideRatio}")
    }

    @Test
    fun `rotation is measured from the top edge`() {
        val tilted = Quad(
            Corner(0.0, 0.0), Corner(100.0, 100.0),
            Corner(0.0, 200.0), Corner(-100.0, 100.0),
        )
        assertEquals(45.0, tilted.rotationDegrees, 0.5)
    }

    @Test
    fun `ordering assigns corners by coordinate sums and differences`() {
        val scrambled = listOf(
            Corner(100.0, 100.0), Corner(0.0, 0.0),
            Corner(0.0, 100.0), Corner(100.0, 0.0),
        )
        assertEquals(square, Quad.ordering(scrambled))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*QuadTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: Quad`.

- [ ] **Step 3: Write the implementation**

```kotlin
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

    /** Signed area by the shoelace formula. */
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
         * Puts four unordered points into corner order.
         *
         * The top left has the smallest x+y and the bottom right the largest; the top
         * right has the smallest y−x and the bottom left the largest. This holds for any
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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*QuadTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add Quad with the geometric checks of the acceptance gate"
```

---

## Task 4: `QuadDetector` — candidate quads, largest first

Produces *candidates*, deliberately not a decision. Task 5 chooses. Keeping them apart is what makes the paper-versus-grid problem solvable.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/QuadDetector.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/QuadDetectorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertTrue

class QuadDetectorTest {

    @Test
    fun `finds candidates in every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val candidates = QuadDetector.detect(CorpusFixtures.load(file))
            assertTrue(candidates.isNotEmpty(), "${file.name}: no candidates at all")
            assertTrue(candidates.size <= 10, "${file.name}: ${candidates.size} candidates, expected at most 10")
        }
    }

    @Test
    fun `candidates arrive largest first`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        val areas = candidates.map { it.area }
        assertTrue(areas == areas.sortedDescending(), "candidates were not ordered by area: $areas")
    }

    @Test
    fun `the paper is found as well as the grid in the dark background photo`() {
        // This photo is the reason candidates are plural: the sheet of paper is a bigger,
        // cleaner quadrilateral than the puzzle printed on it.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        assertTrue(candidates.size >= 3, "expected the paper and the grid among candidates, got ${candidates.size}")
    }

    @Test
    fun `a blank image yields no candidates`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(400, 400, ByteArray(160_000) { -1 })
        assertTrue(QuadDetector.detect(blank).isEmpty())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*QuadDetectorTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: QuadDetector`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds quadrilaterals that might be a sudoku grid, largest first.
 *
 * This deliberately does not decide. In one corpus photograph the sheet of paper is a
 * larger and cleaner quadrilateral than the grid printed on it, so any detector that
 * returns a single "best" answer by size returns the wrong one. [GridLocator] chooses,
 * using evidence this stage cannot see.
 *
 * All parameters below were tuned against the corpus; see the plan for measurements.
 */
object QuadDetector {

    /** Longest edge the detection pass works at. Full resolution is wasted here and slow. */
    private const val WORKING_EDGE = 1000.0

    /** Ignore anything smaller than this share of the frame. */
    private const val MIN_AREA_FRACTION = 0.08

    private const val MAX_CANDIDATES = 10

    fun detect(image: GrayImage): List<Quad> {
        val full = image.toMat()
        val scale = WORKING_EDGE / maxOf(full.width(), full.height()).toDouble()

        val small = Mat()
        Imgproc.resize(full, small, Size(full.width() * scale, full.height() * scale))

        val blurred = Mat()
        Imgproc.GaussianBlur(small, blurred, Size(5.0, 5.0), 0.0)

        // Inverted, so ink becomes white and the grid lines form a connected structure.
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            blurred, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 7.0,
        )

        // Close small gaps where a printed line is broken by paper texture or a fold.
        val closed = Mat()
        Imgproc.morphologyEx(
            binary, closed, Imgproc.MORPH_CLOSE,
            Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
        )

        val contours = mutableListOf<MatOfPoint>()
        Imgproc.findContours(closed, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

        val frameArea = small.width().toDouble() * small.height()
        return contours
            .filter { Imgproc.contourArea(it) > MIN_AREA_FRACTION * frameArea }
            .sortedByDescending { Imgproc.contourArea(it) }
            .take(MAX_CANDIDATES)
            .map { contour -> toQuad(contour, scale) }
    }

    /**
     * Reduces a contour to four corners in full-resolution coordinates.
     *
     * Polygon approximation usually gives exactly four points. When it does not — a
     * curled page produces a bowed outline that needs more — the extreme points of the
     * contour are used instead, which yields sensible corners for any convex blob.
     */
    private fun toQuad(contour: MatOfPoint, scale: Double): Quad {
        val asFloat = MatOfPoint2f(*contour.toArray())
        val perimeter = Imgproc.arcLength(asFloat, true)
        val approximated = MatOfPoint2f()
        Imgproc.approxPolyDP(asFloat, approximated, 0.02 * perimeter, true)

        val points = if (approximated.toArray().size == 4) approximated.toArray() else contour.toArray()
        return Quad.ordering(points.map { Corner(it.x / scale, it.y / scale) })
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*QuadDetectorTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add QuadDetector producing ranked candidate quads

Returns candidates rather than a single answer: in one corpus photo the
sheet of paper is a larger, cleaner quad than the grid printed on it."
```

---

## Task 5: `GridScorer` and `GridLocator` — choosing the right quad

The heart of this plan. `GridScorer` answers "does this rectification actually contain a 9x9 grid"; `GridLocator` uses it to pick.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/GridScorer.kt`
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/GridLocator.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/GridLocatorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridLocatorTest {

    private fun locate(fragment: String) =
        GridLocator.locate(CorpusFixtures.photo(fragment))

    @Test
    fun `locates a grid in every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val result = GridLocator.locate(CorpusFixtures.load(file))
            val located = assertIs<GridLocation.Found>(result, "${file.name}: $result")
            assertTrue(located.gridScore >= GridLocator.MIN_GRID_SCORE, "${file.name} scored ${located.gridScore}")
            assertEquals(GridLocator.RECTIFIED_SIZE, located.rectified.width)
            assertEquals(GridLocator.RECTIFIED_SIZE, located.rectified.height)
        }
    }

    @Test
    fun `prefers the grid over the sheet of paper it is printed on`() {
        // Measured: the paper covers 59% of this frame and scores 0.28; the grid covers
        // 36% and scores 0.41. Choosing by area gets this wrong.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val located = assertIs<GridLocation.Found>(locate("142203"))
        val candidates = QuadDetector.detect(CorpusFixtures.photo("142203"))
        assertTrue(
            located.quad.area < candidates.first().area,
            "chose the largest candidate, which is the paper rather than the grid",
        )
    }

    @Test
    fun `scores stay above the accept threshold with the margin measured on the corpus`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val scores = CorpusFixtures.photos.associate { file ->
            file.name to (GridLocator.locate(CorpusFixtures.load(file)) as GridLocation.Found).gridScore
        }
        // The worst corpus photo measured 0.41 against a 0.35 threshold. If this drops,
        // a parameter change has eaten the margin — investigate before lowering it.
        assertTrue(scores.values.min() >= 0.38, "grid score margin has regressed: $scores")
    }

    @Test
    fun `reports no grid rather than inventing one`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(800, 800, ByteArray(640_000) { -1 })
        assertIs<GridLocation.NoGrid>(GridLocator.locate(blank))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*GridLocatorTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: GridLocator`.

- [ ] **Step 3: Write `GridScorer.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Measures how much a rectified image looks like a 9x9 sudoku grid.
 *
 * The score is the weakest of the twenty places a grid line must appear, expressed as a
 * fraction of the strongest line present. A real grid has all twenty; a rectified sheet
 * of paper has the puzzle somewhere inside it and therefore misses several.
 *
 * Counting line peaks was tried first and does not work: a thick outer border splits
 * into two peaks and a column of digit strokes can align into a spurious one, so an
 * "exactly ten" rule rejected three of six good photographs. Asking whether a line is
 * present where one is *required* is insensitive to extras.
 */
internal object GridScorer {

    /** How far either side of an expected line position to look, as a fraction of a cell. */
    private const val SEARCH_WINDOW_FRACTION = 0.18

    fun score(rectified: Mat): Double {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            rectified, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0,
        )
        val (columns, rows) = projections(binary)
        return minOf(axisScore(columns), axisScore(rows))
    }

    /** Ink counts per column and per row of a binarised square image. */
    internal fun projections(binary: Mat): Pair<DoubleArray, DoubleArray> {
        val size = binary.rows()
        val buffer = ByteArray(size * size)
        binary.get(0, 0, buffer)

        val columns = DoubleArray(size)
        val rows = DoubleArray(size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (buffer[y * size + x].toInt() != 0) {
                    columns[x]++
                    rows[y]++
                }
            }
        }
        return columns to rows
    }

    /** The weakest of the ten required lines along one axis, relative to the strongest. */
    private fun axisScore(profile: DoubleArray): Double {
        val strongest = profile.max()
        if (strongest <= 0.0) return 0.0

        val size = profile.size
        val window = (size / 9.0 * SEARCH_WINDOW_FRACTION).toInt().coerceAtLeast(4)

        return (0..9).minOf { line ->
            val centre = (line * (size - 1.0) / 9.0).toInt()
            val from = (centre - window).coerceAtLeast(0)
            val to = (centre + window).coerceAtMost(size - 1)
            (from..to).maxOf { profile[it] } / strongest
        }
    }
}
```

- [ ] **Step 4: Write `GridLocator.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Where the grid is, or why it could not be found. */
sealed interface GridLocation {

    data class Found(
        val quad: Quad,
        val gridScore: Double,
        val rectified: GrayImage,
    ) : GridLocation

    /** No candidate contained anything resembling a 9x9 grid. */
    data class NoGrid(val bestScore: Double, val candidatesConsidered: Int) : GridLocation
}

/**
 * Finds the sudoku grid in a photograph and straightens it.
 *
 * Candidates come from [QuadDetector] ordered by size; the one chosen is whichever most
 * looks like a grid once rectified, which is not usually the largest.
 */
object GridLocator {

    const val RECTIFIED_SIZE = 1152          // 128 pixels per cell
    const val MIN_GRID_SCORE = 0.35

    fun locate(image: GrayImage): GridLocation {
        val full = image.toMat()
        val candidates = QuadDetector.detect(image)

        val scored = candidates.map { quad ->
            val rectified = rectify(full, quad)
            GridLocation.Found(quad, GridScorer.score(rectified), rectified.toGrayImage())
        }

        val winner = scored.maxByOrNull { it.gridScore }
            ?: return GridLocation.NoGrid(bestScore = 0.0, candidatesConsidered = 0)

        return if (winner.gridScore < MIN_GRID_SCORE) {
            GridLocation.NoGrid(winner.gridScore, candidates.size)
        } else {
            winner
        }
    }

    /** Warps [quad] out of the full-resolution image onto a square. */
    internal fun rectify(full: Mat, quad: Quad): Mat {
        val side = RECTIFIED_SIZE.toDouble()
        val transform = Imgproc.getPerspectiveTransform(
            MatOfPoint2f(*quad.corners.map { Point(it.x, it.y) }.toTypedArray()),
            MatOfPoint2f(
                Point(0.0, 0.0), Point(side, 0.0),
                Point(side, side), Point(0.0, side),
            ),
        )
        val out = Mat()
        Imgproc.warpPerspective(full, out, transform, Size(side, side))
        return out
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*GridLocatorTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`, all six photos located.

- [ ] **Step 6: Commit**

```bash
git add core/vision
git commit -m "Add grid scoring and location

Chooses among candidate quads by whether the rectification contains a 9x9
grid, not by size. Scoring required line positions rather than counting
peaks; counting rejected three of six good photos."
```

---

## Task 6: `GridLineFitter` — where the cell boundaries really are

Dividing the rectified square into ninths assumes the paper was flat. Two corpus photographs are curled or bowed, so the real lines drift from the ideal ones and evenly-spaced cropping clips digits near the edges.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/GridLineFitter.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/GridLineFitterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridLineFitterTest {

    private fun geometryFor(fragment: String): CellGeometry {
        val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.photo(fragment)))
        return assertIs<CellGeometry>(GridLineFitter.fit(located.rectified))
    }

    @Test
    fun `finds ten lines on each axis for every corpus photo`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.load(file)))
            val geometry = GridLineFitter.fit(located.rectified)
            assertIs<CellGeometry>(geometry, "${file.name}: line fitting failed")
            assertEquals(10, geometry.verticalLines.size, file.name)
            assertEquals(10, geometry.horizontalLines.size, file.name)
        }
    }

    @Test
    fun `lines are ordered and span the image`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142301")
        assertTrue(geometry.verticalLines == geometry.verticalLines.sorted())
        assertTrue(geometry.horizontalLines == geometry.horizontalLines.sorted())
        assertTrue(geometry.verticalLines.first() < GridLocator.RECTIFIED_SIZE * 0.10)
        assertTrue(geometry.verticalLines.last() > GridLocator.RECTIFIED_SIZE * 0.90)
    }

    @Test
    fun `fitted lines differ from an even ninth division on curled paper`() {
        // This is the whole reason the fitter exists. If the fitted lines were identical
        // to the ideal ones, dividing by nine would have been fine.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142203")
        val ideal = (0..9).map { it * GridLocator.RECTIFIED_SIZE / 9.0 }
        val drift = geometry.horizontalLines.zip(ideal).maxOf { (fitted, even) -> abs(fitted - even) }
        assertTrue(drift > 2.0, "expected measurable drift on curled paper but got $drift px")
    }

    @Test
    fun `cell bounds are derived from the fitted lines with an inner margin`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val geometry = geometryFor("142301")
        val cell = geometry.cellBounds(0)
        assertTrue(cell.left >= geometry.verticalLines[0])
        assertTrue(cell.right <= geometry.verticalLines[1])
        assertTrue(cell.right > cell.left && cell.bottom > cell.top)

        val last = geometry.cellBounds(80)
        assertTrue(last.left >= geometry.verticalLines[8])
        assertTrue(last.right <= geometry.verticalLines[9])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*GridLineFitterTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: GridLineFitter`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/** Pixel bounds of one cell in the rectified image. */
data class CellBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where the ten vertical and ten horizontal grid lines actually are, in rectified
 * coordinates.
 */
data class CellGeometry(
    val verticalLines: List<Double>,
    val horizontalLines: List<Double>,
) {
    init {
        require(verticalLines.size == 10) { "expected 10 vertical lines, got ${verticalLines.size}" }
        require(horizontalLines.size == 10) { "expected 10 horizontal lines, got ${horizontalLines.size}" }
    }

    /**
     * Bounds of cell [index] (row-major), inset so the printed lines themselves are
     * excluded. Ink that touches a grid line would otherwise be mistaken for a digit.
     */
    fun cellBounds(index: Int, marginFraction: Double = 0.12): CellBounds {
        val row = index / 9
        val column = index % 9
        val left = verticalLines[column]
        val right = verticalLines[column + 1]
        val top = horizontalLines[row]
        val bottom = horizontalLines[row + 1]
        val marginX = (right - left) * marginFraction
        val marginY = (bottom - top) * marginFraction
        return CellBounds(
            left = (left + marginX).toInt(),
            top = (top + marginY).toInt(),
            right = (right - marginX).toInt(),
            bottom = (bottom - marginY).toInt(),
        )
    }
}

/**
 * Locates the real grid lines in a rectified image.
 *
 * A single perspective transform assumes the page was flat. Two corpus photographs are
 * not — one sheet is curled, another is bowed over a clipboard — so the true lines drift
 * from an even ninth division and cropping by ninths clips digits near the edges.
 *
 * Each line is found by taking the strongest ink projection within a window around where
 * it ought to be, then refining to the intensity-weighted centre of that peak.
 */
object GridLineFitter {

    private const val SEARCH_WINDOW_FRACTION = 0.18

    /** Returns null when a line could not be found near one of the expected positions. */
    fun fit(rectified: GrayImage): CellGeometry? {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            rectified.toMat(), binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY_INV, 31, 10.0,
        )
        val (columns, rows) = GridScorer.projections(binary)
        val vertical = fitAxis(columns) ?: return null
        val horizontal = fitAxis(rows) ?: return null
        return CellGeometry(vertical, horizontal)
    }

    private fun fitAxis(profile: DoubleArray): List<Double>? {
        val size = profile.size
        val strongest = profile.max()
        if (strongest <= 0.0) return null

        val window = (size / 9.0 * SEARCH_WINDOW_FRACTION).toInt().coerceAtLeast(4)

        val lines = (0..9).map { line ->
            val centre = (line * (size - 1.0) / 9.0).toInt()
            val from = (centre - window).coerceAtLeast(0)
            val to = (centre + window).coerceAtMost(size - 1)

            val peak = (from..to).maxOf { profile[it] }
            if (peak < strongest * 0.20) return null

            // Intensity-weighted centre of everything near the peak, so a line two or
            // three pixels wide resolves to its middle rather than its first pixel.
            val cutoff = peak * 0.6
            var weighted = 0.0
            var weight = 0.0
            for (i in from..to) {
                if (profile[i] >= cutoff) {
                    weighted += i * profile[i]
                    weight += profile[i]
                }
            }
            if (weight <= 0.0) return null
            weighted / weight
        }

        // Ordering can break if two expected windows lock onto the same thick line.
        if (lines != lines.sorted()) return null
        if (lines.zipWithNext().any { (a, b) -> abs(b - a) < size / 9.0 * 0.4 }) return null
        return lines
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*GridLineFitterTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

If `fitted lines differ from an even ninth division` fails with a drift under 2px, the rectification of that photo is better than expected — verify visually with the Task 10 dump tool before weakening the assertion, because the alternative explanation is that the fitter is silently returning the ideal positions.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add grid line fitting for non-flat paper

Cell corners come from the located lines rather than an even ninth
division, which clips digits when the page is curled or bowed."
```

---

## Task 7: `CellExtractor` — 81 cell images

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/CellExtractor.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/CellExtractorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CellExtractorTest {

    private fun cellsOf(fragment: String): List<GrayImage> {
        val located = assertIs<GridLocation.Found>(GridLocator.locate(CorpusFixtures.photo(fragment)))
        val geometry = assertIs<CellGeometry>(GridLineFitter.fit(located.rectified))
        return CellExtractor.extract(located.rectified, geometry)
    }

    @Test
    fun `extracts eighty one cells of usable size`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val cells = cellsOf("142301")
        assertEquals(81, cells.size)
        // Spec section 4.1 wants at least 78px per cell before the margin is removed.
        assertTrue(cells.all { it.width >= 60 && it.height >= 60 }, "cells too small: ${cells[0]}")
    }

    @Test
    fun `a cell holding a printed digit is darker than an empty one`() {
        // In 142301 the top-left cell is empty and the cell at row 2 column 0 holds a 7.
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val cells = cellsOf("142301")
        fun inkFraction(c: GrayImage): Double {
            var dark = 0
            for (y in 0 until c.height) for (x in 0 until c.width) if (c[x, y] < 128) dark++
            return dark.toDouble() / (c.width * c.height)
        }
        val empty = inkFraction(cells[0])
        val withSeven = inkFraction(cells[18])
        assertTrue(withSeven > empty * 3, "empty=$empty digit=$withSeven — extraction may be misaligned")
    }

    @Test
    fun `the margin keeps grid lines out of the crop`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        // An empty cell must be almost entirely paper. If a grid line were included the
        // border rows would be dark.
        val cells = cellsOf("142301")
        val empty = cells[0]
        val borderDark = (0 until empty.width).count { empty[it, 0] < 128 }
        assertTrue(borderDark < empty.width / 4, "top edge of an empty cell is dark: a grid line is in the crop")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*CellExtractorTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: CellExtractor`.

- [ ] **Step 3: Write the implementation**

```kotlin
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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*CellExtractorTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add cell extraction from fitted grid geometry"
```

---

## Task 8: `ImageQuality` — sharpness, exposure, glare

The photometric half of spec 4.1. Measured over the grid region only, because a sharp background behind a blurred page would otherwise pass.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/ImageQuality.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/ImageQualityTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageQualityTest {

    private fun noise(size: Int, seed: Int): GrayImage {
        val random = Random(seed)
        return GrayImage(size, size, ByteArray(size * size) { random.nextInt(256).toByte() })
    }

    private fun flat(size: Int, value: Int) =
        GrayImage(size, size, ByteArray(size * size) { value.toByte() })

    @Test
    fun `noise is sharp and a flat field is not`() {
        assertTrue(ImageQuality.of(noise(128, 1)).sharpness > ImageQuality.of(flat(128, 128)).sharpness)
    }

    @Test
    fun `blurring an image lowers its sharpness`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        CorpusFixtures.requireCorpus()

        val sharp = CorpusFixtures.photo("142301")
        val blurred = Degrade.blur(sharp, radius = 9)
        assertTrue(
            ImageQuality.of(blurred).sharpness < ImageQuality.of(sharp).sharpness * 0.5,
            "blur did not reduce measured sharpness",
        )
    }

    @Test
    fun `mean luma reflects brightness`() {
        assertTrue(ImageQuality.of(flat(64, 30)).meanLuma < 40)
        assertTrue(ImageQuality.of(flat(64, 220)).meanLuma > 210)
    }

    @Test
    fun `a blown out region is reported as glare`() {
        val size = 100
        val pixels = ByteArray(size * size) { 120.toByte() }
        for (y in 0 until 30) for (x in 0 until 30) pixels[y * size + x] = 255.toByte()
        val glare = ImageQuality.of(GrayImage(size, size, pixels)).clippedWhiteFraction
        assertTrue(glare > 0.08, "expected roughly 9% clipped but measured $glare")
    }

    @Test
    fun `quadrant sharpness spots a partially focused image`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val even = ImageQuality.of(CorpusFixtures.photo("142301"))
        assertTrue(even.worstQuadrantSharpnessRatio > 0.3, "a good photo should be evenly sharp")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*ImageQualityTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: ImageQuality`. `Degrade` also does not exist yet; it is written in Task 11, so create a minimal stub now containing only `blur`, and complete it there.

- [ ] **Step 3: Write the `Degrade.blur` stub**

In `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/Degrade.kt`:

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Synthesises degraded photographs from good ones. Completed in Task 11. */
object Degrade {

    fun blur(image: GrayImage, radius: Int): GrayImage {
        val kernel = (radius * 2 + 1).toDouble()
        val out = Mat()
        Imgproc.GaussianBlur(image.toMat(), out, Size(kernel, kernel), 0.0)
        return out.toGrayImage()
    }
}
```

`toMat` and `toGrayImage` are `internal`, so this compiles only because the test source set shares the module. That is intended.

- [ ] **Step 4: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.imgproc.Imgproc

/**
 * Photometric measurements of an image, per spec section 4.1.
 *
 * Always measure the grid region rather than the whole frame: a sharp background behind
 * a blurred page would otherwise pass every sharpness test.
 */
data class ImageQuality(
    /** Variance of the Laplacian. Higher is sharper; scale depends on resolution. */
    val sharpness: Double,
    val meanLuma: Double,
    /** Fraction of pixels at or near 255, which is what glare looks like. */
    val clippedWhiteFraction: Double,
    /** Sharpness of the worst quadrant over the median quadrant. Catches partial focus. */
    val worstQuadrantSharpnessRatio: Double,
) {
    companion object {

        private const val CLIPPED_WHITE_THRESHOLD = 250

        fun of(image: GrayImage): ImageQuality {
            val mat = image.toMat()
            return ImageQuality(
                sharpness = laplacianVariance(mat),
                meanLuma = image.pixels.sumOf { (it.toInt() and 0xFF).toLong() }.toDouble() / image.pixels.size,
                clippedWhiteFraction = image.pixels.count { (it.toInt() and 0xFF) >= CLIPPED_WHITE_THRESHOLD }
                    .toDouble() / image.pixels.size,
                worstQuadrantSharpnessRatio = quadrantRatio(image),
            )
        }

        internal fun laplacianVariance(mat: Mat): Double {
            val laplacian = Mat()
            Imgproc.Laplacian(mat, laplacian, CvType.CV_64F)
            val mean = MatOfDouble()
            val stdDev = MatOfDouble()
            Core.meanStdDev(laplacian, mean, stdDev)
            val sd = stdDev.toArray()[0]
            return sd * sd
        }

        private fun quadrantRatio(image: GrayImage): Double {
            val halfWidth = image.width / 2
            val halfHeight = image.height / 2
            if (halfWidth < 8 || halfHeight < 8) return 1.0

            val sharpnesses = listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1).map { (cx, cy) ->
                val left = cx * halfWidth
                val top = cy * halfHeight
                val pixels = ByteArray(halfWidth * halfHeight)
                for (y in 0 until halfHeight) {
                    System.arraycopy(
                        image.pixels, (top + y) * image.width + left,
                        pixels, y * halfWidth, halfWidth,
                    )
                }
                laplacianVariance(GrayImage(halfWidth, halfHeight, pixels).toMat())
            }.sorted()

            val median = (sharpnesses[1] + sharpnesses[2]) / 2.0
            return if (median <= 0.0) 1.0 else sharpnesses.first() / median
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*ImageQualityTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add core/vision
git commit -m "Add photometric image quality measurements"
```

---

## Task 9: `StructuralGate` — the early-out

The only proxy check allowed to reject a captured photo on its own, per spec 4.1. Everything softer waits for the certainty verdict in the next plan.

**Files:**
- Create: `core/vision/src/main/kotlin/io/github/tonyxmelon/aisudoku/vision/StructuralGate.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/StructuralGateTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StructuralGateTest {

    @Test
    fun `every corpus photo passes`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            assertIs<GateVerdict.Usable>(verdict, "${file.name} was rejected: $verdict")
        }
    }

    @Test
    fun `a photo with no grid is rejected as such`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(900, 900, ByteArray(810_000) { -1 })
        val verdict = assertIs<GateVerdict.Rejected>(StructuralGate.assess(blank))
        assertIs<RejectionReason.NoGrid>(verdict.reason)
    }

    @Test
    fun `every rejection carries a message telling the user what to do`() {
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
        val blank = GrayImage(900, 900, ByteArray(810_000) { -1 })
        val verdict = assertIs<GateVerdict.Rejected>(StructuralGate.assess(blank))
        assertTrue(verdict.reason.message.isNotBlank())
        assertTrue(verdict.reason.message.first().isUpperCase(), verdict.reason.message)
    }

    @Test
    fun `a usable verdict carries everything the next stage needs`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val verdict = assertIs<GateVerdict.Usable>(StructuralGate.assess(CorpusFixtures.photo("142301")))
        assertTrue(verdict.cells.size == 81)
        assertTrue(verdict.gridScore >= GridLocator.MIN_GRID_SCORE)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :core:vision:test --tests '*StructuralGateTest*' --console=plain
```

Expected: FAIL — `Unresolved reference: StructuralGate`.

- [ ] **Step 3: Write the implementation**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

/** Why a photograph cannot be used, and what the user should do about it. */
sealed interface RejectionReason {
    val message: String

    data object NoGrid : RejectionReason {
        override val message = "Point the camera at a sudoku puzzle."
    }

    data object GridTooSmall : RejectionReason {
        override val message = "Move closer — the grid is too small to read."
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
        override val message = "Could not make out the grid lines — try again."
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
 * softer — blur, uneven lighting, a marginal-looking read — is left to the certainty
 * verdict once recognition has actually run, because image metrics reject usable
 * photographs and pass unusable ones. See spec section 4.2.
 */
object StructuralGate {

    /** Minimum rectified grid side, in source pixels, for roughly 78px per cell. */
    private const val MIN_GRID_SIDE = 700.0

    private const val MAX_OPPOSITE_SIDE_RATIO = 1.25
    private const val MAX_CORNER_ANGLE_DEVIATION = 15.0
    private const val MAX_ROTATION_DEGREES = 15.0

    /** How close to the frame edge a corner may sit before the grid is assumed clipped. */
    private const val EDGE_MARGIN_FRACTION = 0.005

    fun assess(image: GrayImage): GateVerdict {
        val located = GridLocator.locate(image)
        if (located !is GridLocation.Found) return GateVerdict.Rejected(RejectionReason.NoGrid)

        val quad = located.quad

        val shortestSide = minOf(quad.topEdge, quad.rightEdge, quad.bottomEdge, quad.leftEdge)
        if (shortestSide < MIN_GRID_SIDE) return GateVerdict.Rejected(RejectionReason.GridTooSmall)

        val marginX = image.width * EDGE_MARGIN_FRACTION
        val marginY = image.height * EDGE_MARGIN_FRACTION
        val clipped = quad.corners.any {
            it.x <= marginX || it.y <= marginY ||
                it.x >= image.width - marginX || it.y >= image.height - marginY
        }
        if (clipped) return GateVerdict.Rejected(RejectionReason.GridCutOff)

        if (quad.oppositeSideRatio > MAX_OPPOSITE_SIDE_RATIO ||
            quad.maxCornerAngleDeviation > MAX_CORNER_ANGLE_DEVIATION
        ) {
            return GateVerdict.Rejected(RejectionReason.TooSkewed)
        }

        if (kotlin.math.abs(quad.rotationDegrees) > MAX_ROTATION_DEGREES) {
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
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :core:vision:test --tests '*StructuralGateTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add the structural acceptance gate

The only proxy check allowed to reject a captured photo on its own;
everything softer waits for the certainty verdict after recognition."
```

---

## Task 10: The corpus harness and dump tool

Without something to look at, a silent misalignment in cell extraction is invisible — every test can pass while the crops are half a cell off.

**Files:**
- Create: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/CorpusHarnessTest.kt`
- Create: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/DumpCorpusTest.kt`

- [ ] **Step 1: Write the harness**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Runs the whole pipeline over the corpus and reports one line per photograph.
 *
 * This is the regression signal for the vision stage: any parameter change must be run
 * against it, and the printed numbers are how a change is judged better or worse.
 */
class CorpusHarnessTest {

    @Test
    fun `the whole corpus passes the pipeline`() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        var usable = 0
        val report = StringBuilder("\n=== vision corpus harness ===\n")

        for (file in CorpusFixtures.photos) {
            when (val verdict = StructuralGate.assess(CorpusFixtures.load(file))) {
                is GateVerdict.Usable -> {
                    usable++
                    val cell = verdict.cells[0]
                    report.append(
                        "%-26s OK  score=%.2f  cell=%dx%d  sharp=%.0f  luma=%.0f  glare=%.3f\n".format(
                            file.name, verdict.gridScore, cell.width, cell.height,
                            verdict.quality.sharpness, verdict.quality.meanLuma,
                            verdict.quality.clippedWhiteFraction,
                        )
                    )
                }

                is GateVerdict.Rejected ->
                    report.append("%-26s REJECTED  %s\n".format(file.name, verdict.reason))
            }
        }
        report.append("$usable/${CorpusFixtures.photos.size} usable\n")
        println(report)

        assertTrue(
            usable == CorpusFixtures.photos.size,
            "every corpus photograph is known good and must pass:\n$report",
        )
    }
}
```

- [ ] **Step 2: Write the dump tool**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Writes the rectified grid and a contact sheet of the 81 cells for each corpus photo,
 * so a human can see what the pipeline actually produced.
 *
 * Disabled by default because it writes files. Run it deliberately:
 *   ./gradlew :core:vision:test --tests '*DumpCorpusTest*' -Ddump=true --rerun-tasks
 */
class DumpCorpusTest {

    private fun toBufferedImage(image: GrayImage): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_BYTE_GRAY)
        out.raster.setDataElements(0, 0, image.width, image.height, image.pixels)
        return out
    }

    @Test
    fun `dump rectified grids and cell contact sheets`() {
        if (System.getProperty("dump") != "true") return
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }

        val out = File("build/corpus-dump").apply { mkdirs() }

        for (file in CorpusFixtures.photos) {
            val verdict = StructuralGate.assess(CorpusFixtures.load(file))
            if (verdict !is GateVerdict.Usable) {
                println("${file.name}: ${verdict}")
                continue
            }
            val stem = file.nameWithoutExtension
            ImageIO.write(toBufferedImage(verdict.rectified), "png", File(out, "${stem}_grid.png"))

            // 9x9 contact sheet of the extracted cells, each scaled into a 64px box.
            val tile = 64
            val sheet = BufferedImage(tile * 9, tile * 9, BufferedImage.TYPE_BYTE_GRAY)
            val graphics = sheet.createGraphics()
            verdict.cells.forEachIndexed { index, cell ->
                graphics.drawImage(
                    toBufferedImage(cell),
                    (index % 9) * tile, (index / 9) * tile, tile, tile, null,
                )
            }
            graphics.dispose()
            ImageIO.write(sheet, "png", File(out, "${stem}_cells.png"))
        }
        println("dumped to ${out.absolutePath}")
    }
}
```

- [ ] **Step 3: Run the harness**

```bash
./gradlew :core:vision:test --tests '*CorpusHarnessTest*' --rerun-tasks -i --console=plain
```

Expected: `6/6 usable`, and one report line per photograph.

- [ ] **Step 4: Run the dump and LOOK at the output**

```bash
./gradlew :core:vision:test --tests '*DumpCorpusTest*' -Ddump=true --rerun-tasks --console=plain
```

Then open `core/vision/build/corpus-dump/*_cells.png`. **Actually look at these.** Every cell should be centred on its own cell with no grid lines intruding and no digit clipped. A half-cell offset produces a contact sheet that is obviously wrong at a glance and completely invisible to the assertions.

- [ ] **Step 5: Commit**

```bash
git add core/vision
git commit -m "Add corpus harness and cell dump tool

The harness is the regression signal for parameter changes; the dump is
how a silent half-cell misalignment gets caught, which no assertion here
would notice."
```

---

## Task 11: Synthetic rejects — giving the gate real coverage

Every corpus photograph is usable, so nothing so far tests a rejection path. Rather than wait for bad photographs, degrade the good ones: each degradation isolates one fault and names the rejection it should cause.

**Files:**
- Modify: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/Degrade.kt`
- Test: `core/vision/src/test/kotlin/io/github/tonyxmelon/aisudoku/vision/SyntheticRejectTest.kt`

- [ ] **Step 1: Complete `Degrade.kt`**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Synthesises degraded photographs from good ones.
 *
 * The corpus contains no unusable photographs, so the rejection paths would otherwise
 * have no coverage at all. Each function here introduces exactly one fault, which real
 * bad photographs do not — they combine faults — so these complement hand-shot rejects
 * rather than replacing them.
 */
object Degrade {

    fun blur(image: GrayImage, radius: Int): GrayImage {
        val kernel = (radius * 2 + 1).toDouble()
        val out = Mat()
        Imgproc.GaussianBlur(image.toMat(), out, Size(kernel, kernel), 0.0)
        return out.toGrayImage()
    }

    /** Shrinks the whole photo, so the grid falls below the pixels-per-cell floor. */
    fun shrink(image: GrayImage, factor: Double): GrayImage {
        val out = Mat()
        Imgproc.resize(image.toMat(), out, Size(image.width * factor, image.height * factor))
        return out.toGrayImage()
    }

    /** Crops away a fraction of the left side, cutting the grid out of frame. */
    fun cropLeft(image: GrayImage, fraction: Double): GrayImage {
        val cut = (image.width * fraction).toInt()
        val width = image.width - cut
        val pixels = ByteArray(width * image.height)
        for (y in 0 until image.height) {
            System.arraycopy(image.pixels, y * image.width + cut, pixels, y * width, width)
        }
        return GrayImage(width, image.height, pixels)
    }

    /** Scales every pixel toward black. */
    fun darken(image: GrayImage, factor: Double): GrayImage =
        GrayImage(image.width, image.height, ByteArray(image.pixels.size) {
            (((image.pixels[it].toInt() and 0xFF) * factor).toInt().coerceIn(0, 255)).toByte()
        })

    /** Blows out a rectangular patch to pure white, as a specular highlight does. */
    fun addGlare(image: GrayImage, fraction: Double): GrayImage {
        val pixels = image.pixels.copyOf()
        val patchWidth = (image.width * fraction).toInt()
        val patchHeight = (image.height * fraction).toInt()
        val startX = (image.width - patchWidth) / 2
        val startY = (image.height - patchHeight) / 2
        for (y in startY until startY + patchHeight) {
            for (x in startX until startX + patchWidth) {
                pixels[y * image.width + x] = 255.toByte()
            }
        }
        return GrayImage(image.width, image.height, pixels)
    }

    /** Rotates about the centre, to push the grid past the upright tolerance. */
    fun rotate(image: GrayImage, degrees: Double): GrayImage {
        val src = image.toMat()
        val centre = org.opencv.core.Point(image.width / 2.0, image.height / 2.0)
        val rotation = Imgproc.getRotationMatrix2D(centre, degrees, 1.0)
        val out = Mat()
        Imgproc.warpAffine(
            src, out, rotation, Size(image.width.toDouble(), image.height.toDouble()),
            Imgproc.INTER_LINEAR, org.opencv.core.Core.BORDER_CONSTANT,
            org.opencv.core.Scalar(255.0),
        )
        return out.toGrayImage()
    }
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
package io.github.tonyxmelon.aisudoku.vision

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the rejection paths using degraded copies of known-good photographs.
 *
 * These assert the *reason*, not just that something failed. A gate that rejects
 * everything for the wrong reason is useless: the message is what the user acts on.
 */
class SyntheticRejectTest {

    private fun good() = CorpusFixtures.photo("142301")

    private fun setUp() {
        CorpusFixtures.requireCorpus()
        OpenCvNatives.ensureLoaded { nu.pattern.OpenCV.loadShared() }
    }

    @Test
    fun `the undegraded original passes, so any rejection below is caused by the degradation`() {
        setUp()
        assertIs<GateVerdict.Usable>(StructuralGate.assess(good()))
    }

    @Test
    fun `a photo taken from too far away is rejected for size`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.shrink(good(), 0.15))
        val rejected = assertIs<GateVerdict.Rejected>(verdict)
        assertTrue(
            rejected.reason is RejectionReason.GridTooSmall || rejected.reason is RejectionReason.NoGrid,
            "expected a size or no-grid rejection but got ${rejected.reason}",
        )
    }

    @Test
    fun `a grid running out of frame is rejected`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.cropLeft(good(), 0.30))
        val rejected = assertIs<GateVerdict.Rejected>(verdict)
        assertTrue(
            rejected.reason is RejectionReason.GridCutOff || rejected.reason is RejectionReason.NoGrid,
            "expected a framing rejection but got ${rejected.reason}",
        )
    }

    @Test
    fun `a heavily rotated photo is rejected as not upright`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.rotate(good(), 35.0))
        val rejected = assertIs<GateVerdict.Rejected>(verdict)
        assertTrue(
            rejected.reason is RejectionReason.NotUpright || rejected.reason is RejectionReason.NoGrid,
            "expected an orientation rejection but got ${rejected.reason}",
        )
    }

    @Test
    fun `severe blur destroys the grid structure`() {
        setUp()
        val verdict = StructuralGate.assess(Degrade.blur(good(), 25))
        assertIs<GateVerdict.Rejected>(verdict)
    }

    @Test
    fun `degradations that should still be readable are not rejected`() {
        // The gate must not be so eager that it refuses usable photographs. A moderately
        // dark image and a small glare patch both remain readable.
        setUp()
        assertIs<GateVerdict.Usable>(StructuralGate.assess(Degrade.darken(good(), 0.55)))
        assertIs<GateVerdict.Usable>(StructuralGate.assess(Degrade.addGlare(good(), 0.10)))
    }
}
```

- [ ] **Step 3: Run the tests**

```bash
./gradlew :core:vision:test --tests '*SyntheticRejectTest*' --console=plain
```

Expected: `BUILD SUCCESSFUL`.

These assertions accept `NoGrid` as an alternative to the specific reason, because a severe degradation often destroys detection before the specific check runs. That is correct behaviour. If a test fails because the verdict was `Usable`, the gate is too permissive and the threshold needs examining — do not weaken the test.

- [ ] **Step 4: Commit**

```bash
git add core/vision
git commit -m "Add synthetic degradations covering the rejection paths

The corpus contains no unusable photographs, so the gate had no negative
coverage. Each degradation isolates one fault and asserts the reason,
since the message is what the user acts on."
```

---

## Task 12: Close out the milestone

- [ ] **Step 1: Full clean build**

```bash
./gradlew clean build --no-build-cache --rerun-tasks --console=plain
```

Expected: `BUILD SUCCESSFUL`. Do not claim the milestone is done without seeing this.

- [ ] **Step 2: Confirm CI-equivalent behaviour with no corpus**

```bash
mv corpus corpus_hidden && ./gradlew clean build --rerun-tasks --console=plain; mv corpus_hidden corpus
```

Expected: `BUILD SUCCESSFUL`, with corpus tests skipped rather than failed. This is precisely what CI does.

- [ ] **Step 3: Look at the cell dumps one final time**

```bash
./gradlew :core:vision:test --tests '*DumpCorpusTest*' -Ddump=true --rerun-tasks --console=plain
```

Open every `_cells.png`. All 81 cells on each sheet must be correctly centred. This is the check no assertion makes.

- [ ] **Step 4: Update the spec**

In `docs/superpowers/specs/2026-08-30-camera-sudoku-solver-design.md`, prefix M3's deliverable with `DONE — `, and note in section 4 that quad selection is by grid score rather than area, with the measured figures.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Mark M3 complete: photo to 81 cells with a structural verdict"
```

---

## What this plan deliberately leaves out

- **The ground-truth labelling helper.** Spec section 10 lists it under M3, but a 9x9 label
  is only useful once there is a recognizer to compare it against, and the harness here measures
  geometry rather than accuracy. It moves to the M4/M5 plan, where it is first needed. This is a
  deliberate deferral, not an oversight.
- **Digit recognition of any kind.** M4 and M5. This plan ends at clean cell images.
- **The certainty verdict of spec 4.2.** It judges recognition output, which does not exist yet. Only the structural early-out is built here.
- **Live preview checks and the framing advisor.** They share the `Quad` geometry built in Task 3, but they need CameraX, so they belong to the app plan.
- **Android wiring.** `core:vision` is a plain JVM module and stays that way. The app supplies the OpenCV AAR and its own native loader through `OpenCvNatives.ensureLoaded`.
- **Orientation handling.** Every corpus photograph is EXIF orientation 1. Arbitrary phone photos are not, and that belongs with the capture code.
