# Camera Sudoku Solver — Design

**Date:** 2026-08-30
**Repo:** https://github.com/tony-xmelon/aisudoku
**Status:** Approved, ready for implementation planning

## 1. Purpose

An Android app that reads a sudoku puzzle through the camera and helps you finish it.

Point the phone at a puzzle in a book or newspaper. The app guides you into position with live
on-screen text, captures automatically when the framing is good, reads both the printed givens
and any digits you have pencilled in, solves the puzzle, and draws help on top of the straightened
photo: a hint, a check of your own answers, or the full solution. Anything it misreads, you fix by
tapping the cell.

### Non-goals for v1

- No iOS. Android only, though the core is written so a port is not a rewrite.
- No accounts, no sync, no backend, no network calls at all.
- No puzzle history or saved library.
- No recognition of pencilled candidate marks. They are detected and discarded, not read.
- No puzzle generation or play-in-app mode. This app reads paper puzzles.

## 2. Decisions and their reasons

| Decision | Choice | Why |
| --- | --- | --- |
| Platform | Native Kotlin, Android only | Camera-heavy app. CameraX plus Compose gives direct frame access with no bridging cost. |
| Portability | Solver and model layers are pure Kotlin, no Android imports | Testable on the JVM in milliseconds; movable to Kotlin Multiplatform later. |
| Recognition | Entirely on device | No API key is available, and offline is the better product anyway: no backend, no per-solve cost, no privacy exposure, works with no signal, nothing to rate limit. |
| Cloud fallback | Not built, but not designed out | `GridReader` sits behind an interface. A cloud second opinion could be added later without disturbing anything above it. |
| Given recognition | Glyph clustering plus CNN voting | Every printed digit in one photo shares a font and size, so the 81 cells collapse to roughly 9 clusters. Voting within a cluster plus a distinct-label constraint makes the givens near-certain. |
| Guess recognition | Per-cell CNN | Handwriting varies too much within one grid for clustering to be safe in v1. |
| Error correction | The solver validates the read | A published sudoku has exactly one solution, so a read that is unsolvable or ambiguous is provably wrong. This replaces the cloud safety net. |
| Hint style | User setting | Both a plain reveal and a technique explanation are built; the user picks in settings. |
| Working surface | The rectified photo | The captured photo, straightened. Overlay geometry becomes trivial and it looks better than a tilted original. |

## 3. Module structure

```
core:model      pure Kotlin   Digit, Cell, CellSource, Grid, RecognizedGrid, CellReading
core:solver     pure Kotlin   backtracking solver, uniqueness counter, technique solver, HintEngine
core:vision     Android       GridDetector, FramingAdvisor, Rectifier, CellExtractor  (OpenCV)
core:recognize  Android       DigitClassifier (LiteRT), StyleClassifier, GlyphClusterer,
                              GridReader, SolverGuidedRepair
app             Android       CameraX pipeline, Compose UI, overlay, settings, correction screen
```

`core:model` and `core:solver` have no Android dependency. That is deliberate: the hardest logic
in the app — solving, hinting, repairing a bad read — runs and is tested without a device.

### Core types

```kotlin
enum class CellSource { GIVEN, GUESS, EMPTY }

data class Cell(val digit: Int?, val source: CellSource)

data class Grid(val cells: List<Cell>)          // 81 cells, row-major

data class InkStats(                              // appearance of the ink, see section 5.2
    val meanDarkness: Float,
    val meanSaturation: Float,
    val strokeWidthVariance: Float,
    val centroidOffset: Float,                   // blob centre vs cell centre, normalized
    val relativeSize: Float,                     // blob size vs the grid median
)

data class CellReading(                          // one cell, straight out of recognition
    val index: Int,
    val probabilities: FloatArray,               // 9 entries, digits 1..9
    val inkStats: InkStats,
    val hadDiscardedMarks: Boolean,              // corner pencil marks were present and dropped
)

data class RecognizedGrid(
    val grid: Grid,
    val readings: List<CellReading>,
    val confidence: List<Float>,                 // per cell, drives the correction UI ordering
    val repairApplied: Boolean,
)
```

`hadDiscardedMarks` costs nothing now and is what a future candidate-marks feature would build on.

## 4. Capture

`ImageAnalysis` runs the detector on downscaled frames (roughly 640x480); `ImageCapture` takes the
full-resolution still once we commit.

**Detection per frame.** Grayscale, adaptive threshold, find contours, take the largest convex
quadrilateral that covers at least 25% of the frame and is roughly square once perspective is
corrected.

**Guidance.** `FramingAdvisor` maps the quad plus frame statistics to exactly one line of text,
first matching rule wins:

| Condition | Message |
| --- | --- |
| no quad found | Point at the puzzle |
| mean luma below threshold | More light needed |
| large saturated region | Avoid the glare |
| any corner within margin of the frame edge | Fit the whole grid in view |
| quad area below 25% | Move closer |
| quad area above 90% | Move back |
| corner angles far from 90 degrees | Hold the phone flat |
| Laplacian variance below threshold | Hold steady |
| all clear | Hold still... |

Messages are hysteretic — a message must hold for several frames before replacing the current one
— so the text does not flicker between two states.

**Auto-capture.** When the quad corners move less than epsilon across N consecutive frames and
sharpness is above threshold, fire `takePicture()`. A manual shutter button is always present as an
escape hatch, and auto-capture can be turned off in settings.

## 5. Reading the grid

Homography from the quad to a 1152x1152 square, 128px per cell. Each cell is cropped with an inner
margin so grid lines are excluded.

### 5.1 Ink triage

Otsu binarize the cell, then connected components:

- no significant ink -> **empty**
- ink only near the cell border, or several small scattered blobs -> **discarded marks**, cell reads
  empty with `hadDiscardedMarks = true`
- one dominant central blob -> **a digit**, continue

### 5.2 Style: given or guess

Decided from how the ink looks, never from which digit it is. Per-cell features: mean saturation and
darkness of ink pixels, stroke-width variance, offset of the blob centroid from the cell centre, and
blob size relative to the median across the grid.

These are clustered across all 81 cells rather than thresholded per cell. Printed digits form a tight
cluster — same ink, same size, same position; handwriting scatters. Judging a cell against its 80
neighbours is far more robust than judging it alone.

The user can always toggle a cell between given and guess. Nothing downstream blocks on this being
automatic.

### 5.3 Givens: glyph clustering

This is the accuracy centrepiece. Within a single photo every printed digit is the same font at the
same size, so the printed cells collapse into a handful of visually identical groups.

1. Normalize each printed glyph: deskew, binarize, centre by mass, scale to a fixed box.
2. Pairwise distance over the printed set — binary mask Hamming distance after alignment. At most 81
   glyphs, so at most ~3,240 comparisons. Negligible cost.
3. Agglomerative clustering with a distance threshold. Expect at most 9 clusters.
4. Label each cluster: run the CNN on every member, average the softmax across the cluster, and take
   the result. Averaging over several members cancels per-cell noise.
5. Enforce distinct labels across clusters by solving it as an assignment problem (Hungarian
   algorithm, cost = negative mean log-probability). Two clusters cannot both claim to be an 8.
6. Sanity checks: more than 9 members in a cluster means the threshold over-merged; more than 9
   clusters means it over-split. Either triggers a threshold retry.

The effect is that a mediocre classifier still produces excellent givens, and givens are what the
entire solve depends on.

Clustering handwriting the same way is plausible — one person's digits are also consistent — but
there are fewer instances per digit and far more variance, so it is a later enhancement, not v1.

### 5.4 Guesses: per-cell CNN

Blob centred by mass and size-normalized to 20x20 inside a 28x28 box. This is the MNIST convention,
and matching the preprocessing matters more than the model architecture does.

**Model.** Small CNN, 9 output classes (1-9), roughly 200KB as LiteRT. Trained in Python on:

- MNIST digits 1-9 for handwriting.
- Synthetic printed digits rendered from roughly 100 fonts, augmented with blur, rotation, scale
  jitter, threshold artifacts, noise, and simulated grid-line bleed.

The model serves both paths — per-cell for guesses, cluster-averaged for givens.

**Known weakness.** MNIST is loose-leaf handwriting, not digits cramped into a 5mm square touching a
grid line. Expect a real accuracy gap until the corpus in section 8 supplies photographs of actual
puzzle books. This is the single largest technical risk in the project.

## 6. The solver as a spell-checker

A published sudoku has exactly one solution. That makes the solver a proof-checker for the
recognizer, which is what replaces the cloud safety net.

1. Take the givens. Check for duplicates in any row, column, or box.
2. Count solutions, capped at 2.
3. **Exactly one** -> accept the read.
4. **Zero or more than one** -> something was misread. Rank cells by the margin between the
   classifier's top two candidates, smallest margin first. Enumerate alternatives for the weakest
   cells in order of likelihood — including "actually empty" and "given and guess were swapped" —
   searching under a node and time budget for a reading that yields a unique puzzle. Accept the first
   one found.
5. **Nothing found** -> open the correction UI with the least-confident cells already highlighted, so
   the user fixes three cells rather than hunting across 81.

More than one solution is the informative case: it usually means a given was dropped rather than
misread, which narrows the search considerably.

## 7. What the user can do

The working surface is the rectified photo with an overlay drawn on top.

- **Hint.** Setting-controlled.
  - *Just the digit* — pick the most constrained empty cell and reveal it.
  - *Explain it* — name the technique (naked single, hidden single, pointing pair, box-line
    reduction), highlight the cells that prove it, and reveal the digit only if asked again.
- **Check my answers.** Solve from the givens, then colour each handwritten guess green or red.
  Because the puzzle has exactly one solution, every guess is definitively right or wrong — there is
  no third state.
- **Full solution.** Fill every empty cell.
- **Fix a misread.** Tap a cell, get a keypad: set a digit, clear it, or toggle given and guess. Or
  retake the photo. Every correction re-runs the solve immediately.

The technique solver also grades puzzle difficulty as a by-product, from the hardest technique
required to finish.

## 8. Testing

**Solver and hints** — TDD on the JVM. Generated puzzles checked for uniqueness, a set of known hard
puzzles, and hand-built fixtures for each technique with the expected explanation.

**Recognition — the regression harness.** A corpus of real photographs, each with a hand-labelled 9x9
JSON of digit and source, plus a runner that reports per-cell digit accuracy, given/guess accuracy,
and end-to-end unique-solve rate. Without this, "did that change help?" has no answer.

This requires 20-30 photographs of real puzzles — printed, partly solved, various lighting, glossy
and matte paper. Gathering them is the highest-leverage contribution to accuracy in the whole project
and should happen early, before the classifier is tuned. A small labelling helper (local HTML page or
CLI) is part of the work.

**Classifier** — held-out accuracy and a confusion matrix. Watch 1 against 7, 3 against 8, and 5
against 6.

**UI** — Compose tests for the correction flow. Camera behaviour stays manual.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Handwriting accuracy, the dominant risk | Solver-guided repair, cheap manual correction, and real photographs in the training and evaluation corpus |
| Given and guess confused when someone writes in black pen | Manual toggle; nothing in the core flow blocks on automatic detection |
| JDK 25 is newer than current AGP supports | Pin a JDK 21 toolchain in M0 and verify the build before anything else is written |
| OpenCV adds roughly 20MB | arm64-only ABI via App Bundle; revisit only if it becomes a problem |
| Glare and shadow on glossy paper | The framing advisor refuses to auto-capture until it clears |
| Glyph clustering over-merges similar digits, e.g. 1 and 7 in some fonts | Cluster size sanity checks with threshold retry, plus the distinct-label constraint and the solver check downstream |

## 10. Milestones

| | Deliverable |
| --- | --- |
| M0 | Repo, project skeleton, CI, JDK 21 toolchain pinned, empty app builds and runs |
| M1 | `core:model` and backtracking solver with uniqueness counting, pure Kotlin, TDD |
| M2 | Technique solver and `HintEngine`, both hint styles, TDD |
| M3 | Vision pipeline — detection, rectification, cell extraction — validated on static images |
| M4 | Classifier trained in Python, exported to LiteRT, integrated, measured on the corpus |
| M5 | `GridReader`: ink triage, style clustering, glyph clustering, solver-guided repair. End to end from a still image |
| M6 | CameraX live guidance and auto-capture |
| M7 | Overlay modes and settings |
| M8 | Correction UI |
| M9 | Polish, permissions, error states, release build |

M1 through M5 need no phone. The app can read a photograph and produce a solved puzzle before any
camera code exists — which is the right order, because the camera is the easy part and the recognizer
is not.

## 11. Dependencies to pin at M0

Exact versions get resolved and locked during M0 rather than guessed here.

- Kotlin, AGP, Gradle — chosen together against a JDK 21 toolchain
- CameraX (`camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`)
- Jetpack Compose BOM
- OpenCV for Android — Maven Central artifact if available for the target version, otherwise the
  downloadable SDK as a module
- LiteRT (formerly TensorFlow Lite) for Android
- Python side: PyTorch or TensorFlow for training, plus Pillow and a font corpus for synthesis
