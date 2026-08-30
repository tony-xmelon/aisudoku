"""Ink triage and MNIST-style normalisation for extracted sudoku cells.

Mirrors what core:recognize will do in Kotlin. Prototyped here first because the
thresholds have to be measured against real photographs, not guessed.
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


def ink_mask(gray):
    """Dark pixels, using a threshold relative to the cell's own paper white.

    A fixed threshold fails: the corpus spans a shadowed page and a brightly lit one.
    Otsu alone fails too, because an empty cell has no bimodal split and Otsu happily
    invents one out of paper texture and eraser smudge. Taking the threshold as a
    fraction of the cell's bright end anchors it to the paper.
    """
    paper = np.percentile(gray, 85)
    return gray < paper * 0.62


def components(mask, cell_h, cell_w):
    """Connected ink blobs, with grid-line remnants dropped.

    Discarding everything that touches the cell border was tried first and is badly
    wrong: it loses three quarters of the handwritten digits, because people write
    larger than the print and their strokes routinely reach the edge. Printed digits
    never do, which is why the mistake is invisible on an unsolved puzzle.

    A grid-line remnant is instead identified by shape: it spans most of the cell in one
    direction while being very thin in the other. No digit does that.
    """
    labelled, n = ndimage.label(mask)
    out = []
    for i in range(1, n + 1):
        ys, xs = np.where(labelled == i)
        if len(ys) < 12:
            continue
        top, bottom, left, right = ys.min(), ys.max(), xs.min(), xs.max()
        bh, bw = bottom - top + 1, right - left + 1
        line_like = ((bw >= 0.80 * cell_w and bh <= 0.20 * cell_h) or
                     (bh >= 0.80 * cell_h and bw <= 0.20 * cell_w))
        if line_like:
            continue
        out.append(dict(
            size=len(ys), top=top, bottom=bottom, left=left, right=right,
            h=bottom - top + 1, w=right - left + 1,
            cy=(top + bottom) / 2.0, cx=(left + right) / 2.0,
            mask=(labelled == i),
        ))
    return out


def normalise(gray, blob):
    """MNIST convention: the digit scaled into 20x20, centred by mass in a 28x28 box."""
    sub = (~blob["mask"][blob["top"]:blob["bottom"] + 1, blob["left"]:blob["right"] + 1])
    ink = (1.0 - sub.astype(np.float32))
    h, w = ink.shape
    scale = 20.0 / max(h, w)
    nh, nw = max(1, int(round(h * scale))), max(1, int(round(w * scale)))
    small = np.array(Image.fromarray((ink * 255).astype(np.uint8)).resize((nw, nh), Image.BILINEAR)) / 255.0
    out = np.zeros((28, 28), dtype=np.float32)
    cy, cx = ndimage.center_of_mass(small) if small.sum() > 0 else (nh / 2, nw / 2)
    top = int(round(14 - cy))
    left = int(round(14 - cx))
    top = max(0, min(28 - nh, top))
    left = max(0, min(28 - nw, left))
    out[top:top + nh, left:left + nw] = small
    return out
