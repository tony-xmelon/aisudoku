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

**Confirm the quad before trusting it, and choose by that rather than by size.** Backgrounds
contain long straight edges — wood-plank seams, table edges, a clipboard, floor tiles all appear in
the corpus. More importantly, in one photograph the sheet of paper is a *larger and cleaner*
quadrilateral than the grid printed on it: it covers 59% of the frame against the grid's 36% and
approximates neatly to four corners, so picking the biggest candidate rectifies the page furniture
instead of the puzzle.

Candidates are therefore scored on whether the rectification actually contains a 9x9 grid, and the
best-scoring one wins. The score is the weakest of the twenty places a grid line must appear,
relative to the strongest line present. Measured on the corpus: the six photographs score 0.41,
0.47, 0.56, 0.66, 0.80 and 0.96 against a 0.35 accept threshold, and the paper decoy that beat the
grid on size scores 0.28.

Counting line peaks was tried first and does not work — a thick outer border splits into two peaks
and a column of digit strokes can align into a spurious one, so an "exactly ten" rule rejected
three of the six good photographs. Asking whether a line exists where one is *required* is
insensitive to extras.

This doubles as the "that is actually a sudoku" test for live guidance, so the app does not sit
saying *Hold still...* at a picture frame.

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

**The preview fills the screen; the square is drawn on top of it.** The first build made the
*preview itself* square, which put it under the notch and the status bar and left the bottom half of
the screen black. A camera should look like a camera: full-bleed preview, four corner brackets
marking where the puzzle should sit, the guidance on a pill above the shutter, and every control
inset from the system bars it would otherwise hide under.

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

**Blur cannot be caught structurally, and this is measured.** Blurring a corpus photograph at
radius 25 *raises* its grid score from 0.66 to 0.96 while sharpness collapses from 56 to 1: the
blur erases the digit noise from the line projections, so the grid becomes easier to find at
exactly the moment the digits become unreadable. The grid survives to about radius 60 and is gone
by 90. Two consequences follow. The grid score must never be read as a quality signal. And blur is
the clearest example of why acceptance is judged on extraction certainty rather than on image
structure — no structural check can see it.

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

### 5.1 Find the print first; everything else follows from it

*This replaces two earlier designs. The first decided given-versus-guess from ink darkness, which
fails because one corpus photo has handwriting as dark as the print beside it. The second clustered
glyph shapes and called the tight clusters printed, which is sound but needs a reference height it
does not have until it has already run. What is here now was arrived at from the user's own
observation, and it is simpler than either.*

The printed digits are the one population on the page whose properties are guaranteed:

- **one font, one ink, one size** — they are mechanically identical to each other, while
  handwriting is never identical even to itself;
- **at least seventeen of them**, because no sudoku with a single solution can have fewer. That is
  a proven bound, not a heuristic.

So find them first, and every other threshold follows from what was measured rather than from a
constant chosen in advance.

**Finding the core.** Take the largest ink blob in each cell. Slide a window of seventeen over them
sorted by height and score each window by

```
height spread  +  0.5 x darkness spread  +  1.0 x median darkness
```

The first two terms ask for a group that agrees on size and on ink. The third asks for the darkest
such group, and it is what makes this work on a page covered in candidate marks: there the marks
are numerous and uniform enough that seventeen of *them* agree on size more tightly than the print
does, and the tie is broken by the fact that print is toner and everything else is pencil. Without
that term the core lands on the marks; with it, the core is the printed digits on all seven corpus
photographs.

**Classifying every cell against the core.** Measured across the corpus, relative to the core's
median height:

| | height / core | vertical offset |
|---|---|---|
| printed digits | 0.93 – 1.05 | −0.15 to +0.19 |
| handwritten answers | 1.15 – 1.68 | −0.10 to +0.16 |
| candidate marks reaching digit size | up to 1.22 | **−0.14 to −0.24** |

The printed band is tight enough to be decided on height alone. Handwriting is always at least 15%
taller than the print. The only things that reach into either band are large candidate marks — a
ringed pair of digits, say — and *where they sit* gives them away: candidate marks are written along
the top edge of a cell and answers in the middle of it. That is the whole rule:

- height within 0.90–1.09 of the core, not sitting high in the cell -> **printed**
- height at least 1.10 of the core, not sitting high in the cell -> **a written answer**
- anything else -> **a candidate mark**, and the cell counts as empty

This sorts all 532 ink blobs in the corpus correctly, including the photograph where marks are
taller than the print.

**No absolute thresholds survive.** Every number above is a ratio to something measured in the same
photograph. The one exception is a floor below which a blob is too small to join the search at all.

### 5.2 When the printed digits do not make a puzzle

A grid whose givens have no unique solution is proof that the printed reading is wrong somewhere —
and *only* the printed reading can be wrong, because the solver works from the givens alone, so no
handwritten answer, right or wrong, changes the outcome.

Repair questions the least print-like given first, and tries **removing it before changing it**.
The observed failure is a false positive — a clump of candidate marks matching the print in size —
and removing a real given almost never yields a unique puzzle, whereas changing one can quietly
produce a *different* puzzle that solves cleanly. An earlier version tried changes first and
"fixed" a correctly read printed 1 into a 7, reporting success.

### 5.3 Recognizing the digits

Blob centred by mass and size-normalized to 20x20 inside a 28x28 box: the MNIST convention, where
matching the preprocessing matters more than the model does. A small CNN, 9 classes, about 105k
parameters, shipped as raw float32 weights and run by a few hundred lines of Kotlin — which keeps
the whole inference path JVM-testable and adds nothing to the APK.

Four training sources, each added because the one before it was measurably insufficient:

- **MNIST digits 1–9**, for handwriting in general.
- **Synthetic printed digits** from 120 system fonts. MNIST contains no printed glyphs at all, and
  an MNIST-only model read every printed 6 as an 8.
- **Synthetic continental digits.** MNIST is American handwriting. The corpus writer forms a 1 with
  a flag descending half the digit's height and a 7 with a crossbar; MNIST has almost none of
  either, and the model read eight of nine such ones as a 4 — the flag and the stem make the same
  corner a 4's diagonal does. No system font draws a flag anywhere near that long, so these are
  drawn stroke by stroke.
- **The corpus cells themselves**, heavily augmented, so the model adapts to the hand it will
  actually be reading.

Only the last of those can flatter its own score, so it is measured by **leave-one-photograph-out**:
for each photograph, a model trained without it is scored on it. That number, not the one from the
shipped model, is what the reader is worth on a photograph it has never seen.

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

The working surface is the rectified photo with an overlay drawn on top, under a fixed bar of
controls. The screen is a three-part column — bar, photograph, controls — so everything is visible
at once and the photograph takes whatever room is left.

### 7.1 The colour rules

Learned from the first build, which drew doubt as a yellow fill and a wrong answer as a red one, so
a cell that was both came out orange — and orange meant nothing. The user could not tell what the
shades meant, and was right not to.

- **One meaning per colour, and at most one fill per cell.**
- **Doubt is a ring, never a fill**, so it can sit over any fill without inventing a colour.
- **Every mode shows its own key** under the photograph. Nothing is drawn that is not named.
- **What the app read is drawn on a chip**, not as loose ink, so it reads as the app talking rather
  than as a digit on the paper.

### 7.2 The four layers

- **Hint.** Setting-controlled.
  - *Just the digit* — the most constrained cell the user has left empty.
  - *Explain it* — name the technique (naked single, hidden single, pointing pair, box-line
    reduction), highlight the cells that prove it, and give the digit.

  A hint reasons from **the givens plus every answer the user has written that is correct**. Without
  that it explains a step they took ten minutes ago: the first build pointed at a cell already
  holding a 9 and explained that it could only be a 9. Wrong answers are left out rather than
  trusted, since reasoning from a wrong digit produces wrong advice.

  There is no separate *reveal the digit* step. Every technique's explanation names the digit in
  passing — "only 4 can go here" — so the button asked a question that had already been answered.

- **Check my answers.** Solve from the givens, then colour each handwritten answer green or red.
  Because the puzzle has exactly one solution, every answer is definitively right or wrong; there is
  no third state. A red cell also carries the digit the app *read* there, so a misread looks like a
  misread rather than like the app calling a correct answer wrong.
- **Full solution.** Every cell that is not printed, so a finished puzzle shows the whole answer
  rather than the handful of cells recognition happened to miss.
- **What was read.** The recognizer's own account of the page: each square tinted by what it was
  taken to be — printed, handwritten, pencil marks, or empty — with the digit read and a bar showing
  the classifier's confidence. Tapping a square gives the exact figures and the runner-up.

  This is deliberately quieter than the other three, and it answers a different question: not "help
  me" but "what did you actually see?". Without it a misread is invisible until it causes a wrong
  answer somewhere else, and there is no way to tell a confident mistake — worth a photograph and a
  fix — from a coin flip, which is just a cell to correct and move on from.

- **Fix a misread.** Tap any cell for a modal sheet with a three-by-three keypad: set a digit, clear
  it, or switch it between printed and handwritten. Every correction re-runs the solve immediately.

### 7.3 Confirming the reading

Cells the reader was unsure of are ringed, and a banner says how many and what to do. It stays until
every one has been corrected or accepted, and then it goes — the first build left the message up
after the user had dealt with all of them, which read as though the app had not noticed.

The technique solver also grades puzzle difficulty as a by-product, from the hardest technique
required to finish.

**Known limit, measured.** The four implemented techniques finish an easy puzzle in around 45 steps,
but find *nothing at all* on Arto Inkala's 2012 puzzle: reasoning stalls before placing a single
digit. On puzzles that hard, *Explain it* silently degrades to *Just the digit* via the fallback.
That is the correct behaviour — a user who asks for help must get help — but it means the
explanation feature is only as good as the technique list. Whether to extend it should be decided
from real puzzles, by measuring how often the fallback fires, not from this one adversarial case.

## 8. Testing

**Solver and hints** — TDD on the JVM. Generated puzzles checked for uniqueness, a set of known hard
puzzles, and hand-built fixtures for each technique with the expected explanation.

**Recognition — the regression harness.** A corpus of real photographs, each with a hand-labelled 9x9
JSON of digit and source, plus a runner that reports per-cell digit accuracy, given/guess accuracy,
and end-to-end unique-solve rate. Without this, "did that change help?" has no answer.

A small labelling helper (local HTML page or CLI) is part of the work.

**Corpus as of 2026-08-31: seven photographs in `corpus/`,** one puzzle source
(sudoku.cba.si), one device. Between them they exercise most of the pipeline:

| File | What it tests |
| --- | --- |
| `IMG20260830142203` | Completed grid, all answers in pencil. Heavy eraser residue and ghost digits. Curled paper. Dark background, high paper contrast. |
| `IMG20260830142243` | Partly solved. Dense multi-digit candidate marks. Faint pencil answers alongside bold print. |
| `IMG20260830142250` | Mostly empty, dense and very faint candidate marks. Paper barely brighter than the table. |
| `IMG20260830142301` | Unsolved, printed only. The clean baseline. |
| `IMG20260830142308` | Unsolved, printed only. Strong straight background edges to mislead quad detection. |
| `IMG20260830142356` | Partly solved with bold dark handwriting. Shadow across the page, page bowed on a clipboard, busy background. |
| `viber_image_2026-08-31_01-58-40-177` | Puzzle #89114, difficulty *hardest*, in progress. Twenty-four givens, ten handwritten answers, all correct. Nearly every remaining cell carries pencilled candidates, several of them ringed. The hardest triage case in the corpus: the marks are the same graphite as the answers, and a ringed pair is *taller* than any printed digit. Contributed after the app failed to read it on a phone. |

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

### 8.1 Measured, 2026-08-31

| | |
| --- | --- |
| Photographs read into a grid | 7 of 7. Six accepted outright, one with a single cell to confirm |
| Printed digits | 167 of 167 |
| Cells sorted into print / handwriting / marks | 567 of 567 |
| Handwriting, **shipped model** | 93 of 94 |
| Handwriting, **leave-one-photograph-out** | **90 of 94 (95.7%)** |
| MNIST test set | 98.3% |

The leave-one-photograph-out figure is the honest one and the only one worth quoting: the shipped
model has seen every corpus cell, so its 93/94 measures nothing but consistency between the Kotlin
and PyTorch inference paths. That one cell of disagreement is real drift — scipy's binary opening
and OpenCV's differ by a pixel at the mask edge — and is left alone rather than chased.

Before the continental digits and corpus adaptation were added, handwriting stood at 76 of 94, with
eight of the eighteen errors being a 1 read as a 4.

Both numbers still come from one writer, one puzzle source and one device. They say the pipeline
works on this hand; they do not say what it does on anyone else's.

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
| M0 | DONE — Repo, project skeleton, CI, JDK 21 toolchain pinned, empty app builds and runs |
| M1 | DONE — `core:model` and backtracking solver with uniqueness counting, pure Kotlin, TDD |
| M2 | DONE — Technique solver and `HintEngine`, both hint styles, TDD |
| M3 | DONE — Vision pipeline — quad detection with interior-line validation, rectification, grid line fitting, cell extraction — plus the structural early-out, the labelling helper and the regression harness, run against `corpus/` |
| M4 | DONE — Classifier trained in Python, exported to LiteRT, integrated, measured on the corpus |
| M5 | DONE — `GridReader`: ink triage, glyph clustering and style, solver-guided repair, and the certainty verdict of section 4.2. End to end from a still image |
| M6 | DONE — CameraX live guidance, the preview checks of section 4.1, and auto-capture |
| M7 | DONE — Overlay modes and settings |
| M8 | DONE — Correction UI |
| M9 | DONE — Polish, permissions, error states, release build |

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

