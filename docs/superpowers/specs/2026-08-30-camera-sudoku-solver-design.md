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
| Given versus guess | Cluster tightness, not ink darkness | Revised after reviewing the corpus: one photo has handwriting drawn as dark as the print beside it. Printed digits are mechanically identical to one another; handwriting never is. |
| Guess recognition | Per-cell CNN | Handwriting varies too much within one grid for clustering to be safe in v1. |
| Cell geometry | Detected interior grid lines | Also from the corpus: paper curls and bows, so dividing the rectified square into ninths crops digits near the edges. |
| Error correction | The solver validates the read | A published sudoku has exactly one solution, so a read that is unsolvable or ambiguous is provably wrong. This replaces the cloud safety net. |
| Photo acceptance | Judged on extraction certainty, not on image metrics | A confidently wrong grid is the worst failure this app has: the user cannot detect it until the puzzle is ruined. But image metrics reject usable photos and pass unusable ones, so the gate asks whether the pipeline actually produced a grid it can stand behind. |
| Training and data | Entirely local | Corpus photographs and model training stay on the development machine; nothing is uploaded and the corpus is not committed. |
| Hint style | User setting | Both a plain reveal and a technique explanation are built; the user picks in settings. |
| Working surface | The rectified photo | The captured photo, straightened. Overlay geometry becomes trivial and it looks better than a tilted original. |

## 3. Module structure

```
core:model      pure Kotlin   Digit, Cell, CellSource, Grid, RecognizedGrid, CellReading
core:solver     pure Kotlin   backtracking solver, uniqueness counter, technique solver, HintEngine
core:vision     Android       GridDetector, FramingAdvisor, Rectifier, GridLineFitter,
                              CellExtractor  (OpenCV)
core:recognize  Android       InkTriage, GlyphClusterer, DigitClassifier (LiteRT),
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

**Detect the printed grid border, not the sheet of paper.** In the corpus the paper is often
almost the same luminance as the table it lies on, while the printed border is stark black in
every shot. The paper edge is also the wrong target in principle — the page carries a puzzle
number, a URL and a difficulty label outside the grid.

**Confirm the quad before trusting it.** Backgrounds contain long straight edges — wood-plank
seams, table edges, a clipboard, floor tiles all appear in the corpus. A candidate quad is
accepted only if, after rectification, it contains roughly nine evenly spaced horizontal and nine
vertical interior lines. This doubles as the "that is actually a sudoku" test for live guidance,
so the app does not sit saying *Hold still...* at a picture frame.

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

### 4.1 Preview checks: deciding when to fire the shutter

These are cheap proxies computed on preview frames. They drive the guidance text and decide when
auto-capture triggers. **They do not decide whether a photo is acceptable** — section 4.2 does
that. Their job is to steer the camera toward a good shot and to cut off input that is obviously
hopeless.

**Geometry**

| Check | Starting threshold |
| --- | --- |
| Quad found and closed | required |
| All ten horizontal and ten vertical interior lines located | required |
| Interior line spacing regularity (coefficient of variation) | <= 0.15 |
| Rectified grid side in the captured still | >= 700px, i.e. about 78px per cell |
| Grid share of the shorter frame dimension | >= 30% |
| Corner angles after rectification | 90 +/- 15 degrees |
| Ratio between opposite side lengths | <= 1.25 |
| Rotation from upright | within +/- 15 degrees |
| Fitted line bow, as deviation from straight | below tolerance |

Upright matters because a sudoku grid is rotationally symmetric — the grid itself cannot tell us
which way is up, only the digits can. Requiring near-upright input avoids that problem entirely in
v1. Detecting orientation by classifying at four rotations and keeping the most confident is a
later enhancement.

**Photometry**

| Check | Starting threshold |
| --- | --- |
| Laplacian variance over the grid region | above threshold |
| Per-quadrant sharpness against the grid median | each >= 60% |
| Clipped-white pixels inside the grid, i.e. glare | < 2% |
| Mean page luma | 80 to 235 of 255 |
| Illumination ratio, brightest page region over darkest | <= 3.0 |
| Separation between printed line darkness and page white | above threshold |

Sharpness is measured on the grid region, not the frame, because a sharp background behind a
blurred page would otherwise pass. The per-quadrant check catches a shot that focused on one
corner. The illumination ratio must stay loose enough to admit the shadowed corpus photo, which is
usable — calibrating against it is the point of having it.

All thresholds are starting values to tune against the corpus, not constants.

**Structural early-out.** After capture, one hard check runs before recognition: is there a grid at
all — a closed quad, ten by ten interior lines, above the resolution floor? If not, reject
immediately, because running the pipeline cannot produce anything. This is the only proxy allowed
to reject a captured photo on its own.

### 4.2 Acceptance is decided by extraction certainty

**A photo is accepted when the app can actually read it, not when it looks readable.** Proxy
metrics are nervous in the wrong places: they reject usable photos that happen to score badly and
pass unusable ones that happen to score well. The honest question is whether the pipeline came out
the other end holding a grid it can stand behind.

So the real gate runs *after* recognition, on the result. It costs a full pipeline run — on the
order of a tenth of a second on device — which is nothing next to a wrong answer.

**Certainty signals**

| Signal | What it says |
| --- | --- |
| Per-cell classifier margin | Gap between the top two digit probabilities. A small gap is a cell the app is guessing at. |
| Printed cluster quality | Whether clusters came out tight, multi-member, and geometrically consistent, and whether the Hungarian label assignment had a clear winner. |
| Triage ambiguity | Blobs that landed near the answer-versus-candidate size boundary, or near the ink-versus-residue floor. |
| Repair depth | How many cells section 6 had to overturn before the grid became uniquely solvable. Zero is a clean read; several means the recognizer and the solver disagreed. |
| Solution count | Exactly one, none, or many. The strongest signal available, and the only one that is a proof rather than an estimate. |

**Verdict**

- **Accepted** — exactly one solution, every cell above the confidence floor, clustering clean,
  repair depth zero. Go straight to the result.
- **Needs confirmation** — exactly one solution, but a handful of cells were weak, ambiguous, or
  overturned by repair. Show the result with exactly those cells flagged and ask the user to
  confirm them. The app is saying what it is unsure about instead of guessing or refusing.
- **Rejected** — no unique solution even after repair, or too many cells below the floor to be
  worth confirming one at a time. Retake.

The middle tier matters: **rejection and correction are the same mechanism at different scales.**
Three uncertain cells is a question worth asking. Forty is a photo worth retaking. The threshold
between them is a tuning decision, not a principle — start around five.

### 4.3 What rejection looks like

Rejection always names the specific failure and what to do about it — *Move closer, the grid is
too small to read*, *Part of the grid is cut off*, *Too blurry, hold still*, *Could not read enough
of the grid to be sure* — never a generic failure message. The user is being asked to do
something, so they have to be told what.

Where the fault is structural, the preview checks of section 4.1 supply the wording, since they
know which condition failed. Where the fault is certainty, the message names how much could not be
read, and the weakest cells are highlighted on the rejected photo so the user can see what went
wrong rather than being told to try again blind.

## 5. Reading the grid

Homography from the quad to a 1152x1152 square, 128px per cell.

**Cell boundaries come from the detected interior lines, not from dividing by nine.** Paper is not
flat: in the corpus one sheet is visibly curled and another is bowed over a clipboard. A single
planar homography leaves cells progressively misaligned toward the edges, which crops digits.
After the initial rectification, locate the ten horizontal and ten vertical grid lines and use
their actual intersections as cell corners. Each cell is then cropped with an inner margin so the
lines themselves are excluded.

### 5.1 Ink triage

Per-cell adaptive threshold — global thresholding fails on the corpus shot with a shadow across
the page — then connected components. Every blob in the cell is classified individually by height
relative to the printed digit height, which section 5.2 establishes precisely, and by its position
in the cell:

- **answer-sized, roughly centred** -> a digit, continue to recognition
- **roughly a third of digit height, sitting high in the cell** -> a candidate mark, discarded
- **faint, diffuse, low edge gradient** -> eraser residue, discarded

A cell keeps its answer blob even when candidate marks or residue are also present; those cells
are common in the corpus. `hadDiscardedMarks` records that something was thrown away.

**Eraser residue needs an absolute darkness floor, not just a relative threshold.** In the
completed puzzle in the corpus, most cells carry grey smudge and some carry the ghost of a rubbed
out digit. Otsu on such a cell happily reports "ink". A blob must be dark enough in absolute terms
relative to the page white, and have sharp enough edges, to count.

### 5.2 Glyph clustering, which decides both what is printed and what it says

Clustering runs *before* style detection, because the clustering is what reveals the style. This
is a correction to an earlier version of this design, which proposed deciding given-versus-guess
from ink darkness and saturation and then clustering the printed set afterwards.

**Darkness does not separate the two.** One corpus photo has handwritten digits drawn firmly
enough to be as dark as the printed ones sitting beside them. Any threshold that catches the
pencil in the faint photos misclassifies the bold handwriting in that one.

**Uniformity separates them perfectly.** Printed digits within one photo are mechanically
identical: one font, one size, the same offset within every cell. Handwriting is never identical
to itself. So cluster tightness *is* the style signal.

1. Normalize every answer-sized blob in the grid: deskew, binarize, centre by mass, scale to a
   fixed box.
2. Pairwise distance — Hamming distance over aligned binary masks. At most 81 glyphs, so at most
   ~3,240 comparisons. Negligible cost.
3. Agglomerative clustering at a tight threshold.
4. **The printed set** is the clusters that are tight, hold two or more members, and whose members
   agree on height and on offset within the cell. Everything else is handwriting.
5. The printed glyph height becomes the reference height that section 5.1 uses to tell answers
   from candidate marks. The two stages are mutually dependent, so triage runs twice: once with a
   provisional height estimate, then again once the printed clusters have fixed it.
6. Label each printed cluster: run the CNN on every member, average the softmax across the
   cluster, take the result. Averaging over several members cancels per-cell noise.
7. Enforce distinct labels across clusters as an assignment problem (Hungarian algorithm,
   cost = negative mean log-probability). Two clusters cannot both claim to be an 8.
8. Sanity checks: a cluster with more than nine members means the threshold over-merged; more than
   nine printed clusters means it over-split. Either triggers a retry at an adjusted threshold.

**The singleton problem.** A digit printed only once in the grid forms a one-member cluster and
looks like handwriting by the rule in step 4. Guard: once the multi-member printed clusters have
established the printed height, cell offset and stroke weight, test each singleton against that
geometry. A singleton that matches is printed.

Ink darkness and saturation survive as a weak secondary prior, useful for breaking ties, but they
no longer decide anything on their own. And the user can always toggle a cell between given and
guess — nothing downstream blocks on this being automatic.

### 5.3 Guesses: per-cell CNN

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

1. **Givens count sanity.** Fewer than 17 givens cannot yield a unique solution — 17 is the proven
   minimum for a proper sudoku — so a read below that is definitely wrong, and almost always means
   givens were misclassified as guesses. A completely filled grid of 81 givens is legitimate: it is
   a printed solution, which the app is expected to accept.
2. Take the givens. Check for duplicates in any row, column, or box.
3. Count solutions, capped at 2.
4. **Exactly one** -> accept the read.
5. **Zero or more than one** -> something was misread. Rank cells by the margin between the
   classifier's top two candidates, smallest margin first. Enumerate alternatives for the weakest
   cells in order of likelihood — including "actually empty" and "given and guess were swapped" —
   searching under a node and time budget for a reading that yields a unique puzzle. Accept the first
   one found.
6. **Nothing found** -> open the correction UI with the least-confident cells already highlighted, so
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

A small labelling helper (local HTML page or CLI) is part of the work.

**Corpus as of 2026-08-30: six photographs in `corpus/`,** 3000x4000, one puzzle source
(sudoku.cba.si), one device. Between them they exercise most of the pipeline:

| File | What it tests |
| --- | --- |
| `IMG20260830142203` | Completed grid, all answers in pencil. Heavy eraser residue and ghost digits. Curled paper. Dark background, high paper contrast. |
| `IMG20260830142243` | Partly solved. Dense multi-digit candidate marks. Faint pencil answers alongside bold print. |
| `IMG20260830142250` | Mostly empty, dense and very faint candidate marks. Paper barely brighter than the table. |
| `IMG20260830142301` | Unsolved, printed only. The clean baseline. |
| `IMG20260830142308` | Unsolved, printed only. Strong straight background edges to mislead quad detection. |
| `IMG20260830142356` | Partly solved with bold dark handwriting. Shadow across the page, page bowed on a clipboard, busy background. |

The harness also reports **certainty calibration**: for each photo, the verdict of section 4.2
against whether the read was actually correct. Accepting a wrong grid and rejecting a right one are
both failures, and neither is visible from accuracy alone.

**The corpus contains no rejects.** Every photograph in it is usable, so it cannot test the
acceptance gate of section 4.1 at all. That gate needs deliberately bad input: shot from too far,
out of focus, grid partly out of frame, heavy glare, steeply angled, page folded. These are
quick to produce and each one should be labelled with the rejection reason it ought to trigger.

**Known gaps.** One puzzle source means one font. One device, all shots roughly overhead at
similar distance. No low light, no glare or specular highlight, no newsprint or magazine stock, no
photographs of a screen, no steep angles, no landscape orientation.

This is enough to build the pipeline and the harness against, and enough to catch real regressions.
It is **not** enough to quote an accuracy figure from — a number measured on six same-source
photographs will be optimistic. Broadening to 20-30 across the gaps above should happen before any
tuning conclusion is trusted, and before release.

**Classifier** — held-out accuracy and a confusion matrix. Watch 1 against 7, 3 against 8, and 5
against 6.

**UI** — Compose tests for the correction flow. Camera behaviour stays manual.

## 9. Risks

| Risk | Mitigation |
| --- | --- |
| Handwriting accuracy, the dominant risk | Solver-guided repair, cheap manual correction, and real photographs in the training and evaluation corpus |
| Given and guess confused when someone writes dark, as in one corpus photo | Style comes from cluster tightness rather than darkness; manual toggle as the backstop |
| Eraser residue and ghost digits read as ink | Absolute darkness floor plus an edge-sharpness test in triage. The completed-puzzle photo is the regression case |
| Paper curl and bow misaligning cells toward the edges | Cell corners taken from fitted interior grid lines instead of dividing the square into ninths |
| Background straight lines mistaken for the grid | A quad is rejected unless it contains a 9x9 interior line structure |
| Candidate marks read as answers | Blob height measured against the printed digit height established by clustering; marks run about a third of answer height and sit high in the cell |
| Acceptance gate tuned too strictly, so the app feels fussy and will not capture | Only the structural early-out can reject on a proxy metric; everything else is judged on whether the read succeeded, so a photo that scores badly but reads cleanly is accepted. The live guidance tells the user how to satisfy the gate rather than just refusing |
| Confidence scores poorly calibrated, so the certainty gate trusts the wrong reads | Solution uniqueness is a proof, not an estimate, and carries the most weight in the verdict. The corpus harness reports certainty against ground truth so calibration is measurable rather than assumed |
| Corpus is single-source and single-device | Accuracy measured on it is optimistic. Broaden before trusting a number or shipping |
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
| M3 | Vision pipeline — quad detection with interior-line validation, rectification, grid line fitting, cell extraction — plus the structural early-out, the labelling helper and the regression harness, run against `corpus/` |
| M4 | Classifier trained in Python, exported to LiteRT, integrated, measured on the corpus |
| M5 | `GridReader`: ink triage, glyph clustering and style, solver-guided repair, and the certainty verdict of section 4.2. End to end from a still image |
| M6 | CameraX live guidance, the preview checks of section 4.1, and auto-capture |
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

## 12. Distribution

Test builds go out through **Firebase App Distribution**, project `aisudoku-xmelon`
(https://console.firebase.google.com/u/2/project/aisudoku-xmelon/overview). This applies from the
first installable build onward and is a concern of the Android plans, not the core engine.

Two things to get right when that work starts:

- `google-services.json` identifies the project and is normally committed. The **App Distribution
  service account key is a credential** and must never be. It is already in `.gitignore`; in CI it
  belongs in a repository secret.
- App Distribution needs the applicationId to match the Firebase app registration, which fixes
  `io.github.tonyxmelon.aisudoku` in place earlier than a Play release would. Changing it later
  means re-registering the app.

Nothing here implies analytics, Crashlytics or any other Firebase service. The app makes no network
calls (section 2); distribution is a build-time concern only, and adding a Firebase SDK to the app
itself would contradict that.

