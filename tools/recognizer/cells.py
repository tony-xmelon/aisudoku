"""Labels, and the bitmaps the Kotlin reader hands its classifier.

This used to carry a second implementation of the ink masking and MNIST-style
normalisation that CellAnalyzer does in Kotlin, on the reasoning that the thresholds
had to be measured against real photographs before being fixed in the app. Both were
then maintained by hand, and by 3 September 2026 they had drifted in four separate
ways at once - ink margin, resize filter, morphological anchor and blob connectivity
- so that not one cell out of 746 normalised to the same bitmap on both sides. The
model was trained on images the phone never produces.

So there is no longer a second implementation to keep in step. The Kotlin reader
exports the exact 28x28 bitmaps it feeds its classifier and training reads those,
which makes the two agree by construction rather than by vigilance:

    ./gradlew :core:recognize:test --tests '*ExportNormalisedTest*' -Ddump=true --rerun-tasks
"""
import json
import os
import numpy as np
from PIL import Image
from scipy import ndimage

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CELLS = os.path.join(REPO, "core", "vision", "build", "cell-export")
LABELS = os.path.join(REPO, "corpus-labels")


def load_labels():
    out = {}
    for name in sorted(os.listdir(LABELS)):
        with open(os.path.join(LABELS, name), encoding="utf-8") as f:
            d = json.load(f)
        stem = d["photo"].replace(".jpg", "")
        givens = "".join(d["givens"])
        written = "".join(d["written"])
        cells = []
        for i in range(81):
            if givens[i] != ".":
                cells.append((int(givens[i]), "given"))
            elif written[i] != ".":
                cells.append((int(written[i]), "guess"))
            else:
                cells.append((None, "empty"))
        out[stem] = cells
    return out



#: Where ExportNormalisedTest writes the bitmaps the classifier is actually given.
NORMALISED = os.path.join(REPO, "core", "recognize", "build", "normalised")


def normalised_cells(stem):
    """The bitmaps for one photograph, by cell index.

    A cell with no ink in it is absent rather than blank, exactly as CellAnalyzer reports
    it, so an empty square never reaches training as a picture of nothing.
    """
    directory = os.path.join(NORMALISED, stem)
    if not os.path.isdir(directory):
        return {}
    out = {}
    for name in sorted(os.listdir(directory)):
        if not name.endswith(".f32"):
            continue
        index = int(name[len("cell_"):-len(".f32")])
        out[index] = np.fromfile(os.path.join(directory, name), dtype="<f4").reshape(28, 28)
    return out


#: The Kotlin that produces those bitmaps. If it changes, they are out of date.
ANALYZER = os.path.join(
    REPO, "core", "recognize", "src", "main", "kotlin", "io", "github",
    "tonyxmelon", "aisudoku", "recognize", "CellAnalyzer.kt")


def export_is_stale():
    """Whether CellAnalyzer has been edited since the bitmaps were exported.

    Reading the reader's output makes the two agree by construction, but only for as long
    as the output is current. Editing the ink margin and training without re-exporting
    would reintroduce exactly the drift this arrangement removes, and silently - the
    bitmaps would still load and still look like digits.
    """
    if not os.path.isdir(NORMALISED) or not os.path.isfile(ANALYZER):
        return False
    newest = max(
        (os.path.getmtime(os.path.join(root, name))
         for root, _, names in os.walk(NORMALISED) for name in names if name.endswith(".f32")),
        default=None)
    return newest is not None and os.path.getmtime(ANALYZER) > newest
