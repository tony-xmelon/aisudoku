"""Trains the digit classifier and exports it for the Kotlin runtime.

Four training sources, each added because the one before it was measurably not enough:

  * MNIST digits 1-9, for handwriting in general.
  * Synthetic printed digits rendered from system fonts, because MNIST contains no
    printed glyphs at all and an MNIST-only model read every printed 6 as an 8.
  * Synthetic continental digits, because MNIST is American handwriting. The corpus
    writer forms a 1 with a flag descending half the digit's height and a 7 with a
    crossbar; MNIST has almost none of either, and the model read eight of nine such
    ones as a 4. These are drawn stroke by stroke rather than collected.
  * The corpus cells themselves, heavily augmented, so the model adapts to the hand it
    will actually be reading.

That last source is the only one that can flatter itself, so it is measured by
leave-one-photograph-out: for each photograph, a model trained without it is scored on
it. That number, not the one from the shipped model, is what the reader is worth on a
photograph it has never seen. Run with --lopo to reproduce it.

Exports raw float32 weights rather than LiteRT. The network is small enough to run in a
few hundred lines of Kotlin, which keeps the whole inference path JVM-testable and adds
nothing to the APK.
"""
import json
import os
import struct
import sys

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage
from torch.utils.data import DataLoader, TensorDataset
from torchvision import datasets, transforms

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cells import CELLS, components, load_labels, normalise  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
FONT_DIR = r"C:\Windows\Fonts"
SEED = 20260831


class Net(nn.Module):
    """Small CNN over a 28x28 digit. Nine classes: the digits 1..9.

    Deliberately small: about 105k parameters, 420KB as float32, which ships as an
    Android asset without complaint.
    """

    def __init__(self):
        super().__init__()
        self.c1 = nn.Conv2d(1, 16, 3, padding=1)
        self.c2 = nn.Conv2d(16, 32, 3, padding=1)
        self.fc1 = nn.Linear(32 * 7 * 7, 64)
        self.drop = nn.Dropout(0.3)
        self.fc2 = nn.Linear(64, 9)

    def forward(self, x):
        x = F.max_pool2d(F.relu(self.c1(x)), 2)
        x = F.max_pool2d(F.relu(self.c2(x)), 2)
        x = x.flatten(1)
        x = self.drop(F.relu(self.fc1(x)))
        return self.fc2(x)


def mask_of(gray):
    local = ndimage.uniform_filter(gray, size=31)
    return ndimage.binary_opening(gray < (local - 6), np.ones((2, 2)), iterations=1)


def normalise_array(ink):
    """Scale an ink bitmap into 20x20 and centre it by mass in 28x28, MNIST style."""
    ys, xs = np.where(ink > 0.2)
    if len(ys) == 0:
        return np.zeros((28, 28), dtype=np.float32)
    ink = ink[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = ink.shape
    scale = 20.0 / max(h, w)
    nh, nw = max(1, int(round(h * scale))), max(1, int(round(w * scale)))
    small = np.array(
        Image.fromarray((ink * 255).astype(np.uint8)).resize((nw, nh), Image.BILINEAR)
    ) / 255.0
    out = np.zeros((28, 28), dtype=np.float32)
    cy, cx = ndimage.center_of_mass(small) if small.sum() > 0 else (nh / 2, nw / 2)
    top = max(0, min(28 - nh, int(round(14 - cy))))
    left = max(0, min(28 - nw, int(round(14 - cx))))
    out[top:top + nh, left:left + nw] = small
    return out


def photo_noise(arr, rng):
    """The distortions a phone photograph and adaptive thresholding introduce."""
    arr = ndimage.rotate(arr, float(rng.uniform(-8, 8)), reshape=False, order=1)
    arr = ndimage.gaussian_filter(arr, sigma=float(rng.uniform(0.3, 1.6)))
    arr = arr + rng.normal(0, float(rng.uniform(0.01, 0.10)), arr.shape)
    arr = np.clip(arr, 0, 1)
    return (arr > float(rng.uniform(0.30, 0.55))).astype(np.float32)


def usable_fonts(limit=120):
    """System fonts that can actually render digits. Symbol fonts are skipped."""
    out = []
    for name in sorted(os.listdir(FONT_DIR)):
        if not name.lower().endswith((".ttf", ".otf")):
            continue
        path = os.path.join(FONT_DIR, name)
        try:
            font = ImageFont.truetype(path, 48)
            image = Image.new("L", (64, 64), 0)
            ImageDraw.Draw(image).text((10, 4), "5", font=font, fill=255)
            if np.array(image).sum() < 2000:      # blank or a symbol glyph
                continue
            out.append(path)
        except Exception:
            continue
        if len(out) >= limit:
            break
    return out


def synthetic_printed(fonts, per_font=14, seed=SEED):
    """Printed digits with the distortions a phone photograph introduces."""
    rng = np.random.default_rng(seed)
    xs, ys = [], []
    for path in fonts:
        for digit in range(1, 10):
            for _ in range(per_font):
                size = int(rng.integers(34, 52))
                try:
                    font = ImageFont.truetype(path, size)
                except Exception:
                    continue
                canvas = Image.new("L", (96, 96), 0)
                ImageDraw.Draw(canvas).text((24, 16), str(digit), font=font, fill=255)
                xs.append(normalise_array(photo_noise(np.array(canvas, dtype=np.float32) / 255.0, rng)))
                ys.append(digit - 1)
    return np.stack(xs), np.array(ys)


def continental_ones(count=4000, seed=SEED + 1):
    """A 1 drawn as most of Europe draws it: a stem under a long descending flag.

    MNIST's ones are overwhelmingly bare strokes, so a model trained on it reads this
    shape as a 4 - the flag and the stem make the same corner a 4's diagonal does. The
    only fix is to show it the shape, and no system font draws a flag anywhere near this
    long, so it is drawn here: stem, flag, and sometimes the base serif that turns the
    whole thing into a shallow triangle.
    """
    rng = np.random.default_rng(seed)
    xs = []
    for _ in range(count):
        canvas = Image.new("L", (96, 96), 0)
        draw = ImageDraw.Draw(canvas)
        width = int(rng.integers(3, 7))
        height = float(rng.uniform(52, 68))
        top = 96 / 2 - height / 2
        slant = float(rng.uniform(-6, 6))
        x_top = 96 / 2 + float(rng.uniform(-4, 4)) + slant / 2
        x_bottom = x_top - slant

        draw.line([(x_top, top), (x_bottom, top + height)], fill=255, width=width)

        # The flag: down and to the left, between a fifth and three fifths of the height.
        drop = height * float(rng.uniform(0.20, 0.60))
        reach = drop * float(rng.uniform(0.45, 1.10))
        draw.line([(x_top, top), (x_top - reach, top + drop)], fill=255, width=width)

        if rng.random() < 0.30:
            serif = height * float(rng.uniform(0.20, 0.40))
            draw.line(
                [(x_bottom - serif, top + height), (x_bottom + serif, top + height)],
                fill=255, width=width,
            )
        xs.append(normalise_array(photo_noise(np.array(canvas, dtype=np.float32) / 255.0, rng)))
    return np.stack(xs), np.zeros(len(xs), dtype=np.int64)      # class 0 is the digit 1


def continental_sevens(count=2500, seed=SEED + 2):
    """A 7 with the crossbar, which MNIST has too few of to learn from."""
    rng = np.random.default_rng(seed)
    xs = []
    for _ in range(count):
        canvas = Image.new("L", (96, 96), 0)
        draw = ImageDraw.Draw(canvas)
        width = int(rng.integers(3, 7))
        height = float(rng.uniform(52, 68))
        span = height * float(rng.uniform(0.50, 0.80))
        top = 96 / 2 - height / 2
        left = 96 / 2 - span / 2
        lean = span * float(rng.uniform(0.15, 0.45))

        draw.line([(left, top), (left + span, top)], fill=255, width=width)
        draw.line([(left + span, top), (left + lean, top + height)], fill=255, width=width)
        if rng.random() < 0.75:
            bar_y = top + height * float(rng.uniform(0.45, 0.60))
            bar = span * float(rng.uniform(0.30, 0.55))
            centre = left + span - (span - lean) * (bar_y - top) / height
            draw.line([(centre - bar / 2, bar_y), (centre + bar / 2, bar_y)], fill=255, width=width)
        xs.append(normalise_array(photo_noise(np.array(canvas, dtype=np.float32) / 255.0, rng)))
    return np.stack(xs), np.full(len(xs), 6, dtype=np.int64)     # class 6 is the digit 7


def curl(x, y, seed=SEED + 3):
    """Adds a leftward hook to the foot of every 9, the way a written 9 is finished.

    Only the tail differs between the corpus writer's 9 and MNIST's, and it is enough:
    with the hook the model reads them as 8s and 3s, because a hook closing back towards
    the loop is what those digits do.
    """
    rng = np.random.default_rng(seed)
    picked = np.where(y == 8)[0]                                # class 8 is the digit 9
    out = []
    for i in picked:
        a = (x[i, 0] * 255).astype(np.uint8)
        big = Image.fromarray(a).resize((96, 96), Image.BILINEAR)
        arr = np.array(big)
        ys, xs_ = np.where(arr > 60)
        if len(ys) == 0:
            continue
        foot = ys.max()
        at = xs_[ys == foot].mean()
        draw = ImageDraw.Draw(big)
        reach = float(rng.uniform(12, 26))
        rise = float(rng.uniform(0, 10))
        draw.line([(at, foot), (at - reach, foot - rise)], fill=255, width=int(rng.integers(4, 8)))
        out.append(normalise_array(np.array(big, dtype=np.float32) / 255.0))
    return np.stack(out), np.full(len(out), 8, dtype=np.int64)


def written_eights(count=3000, seed=SEED + 5):
    """An 8 drawn by hand, where the two loops do not reliably close.

    Half the misses on unseen photographs are an 8 read as something with fewer closed
    loops - twice as a 6, once as a 5. MNIST's eights are drawn in one confident stroke
    with both loops shut, and a written one is often two circles stacked, joined where the
    pen lifted. An 8 whose top loop is open on the left is, to a model that has only seen
    MNIST, a 6 with a flourish; one open at the top is a 5.

    So the loops are drawn as arcs with a gap rather than as closed ellipses, and the gap
    is put where a pen actually leaves one. This is the same argument as the continental
    ones and sevens: no font draws the shape, so it has to be drawn here.
    """
    rng = np.random.default_rng(seed)
    xs = []
    for _ in range(count):
        canvas = Image.new("L", (96, 96), 0)
        draw = ImageDraw.Draw(canvas)
        # Wider than the other drawn shapes: at 3-7 these came out at an ink mean of
        # 0.11 against the 0.16 of the real eights in the corpus, and a stroke that
        # thin is a different thing to read than a pen on paper.
        width = int(rng.integers(5, 9))
        height = float(rng.uniform(50, 68))
        top = 96 / 2 - height / 2
        # The top loop is the smaller of the two far more often than not, and it is never
        # the wider - an 8 with a fat head reads as a 9 to anybody, model or otherwise.
        split = float(rng.uniform(0.42, 0.54))
        top_h = height * split
        full_w = height * float(rng.uniform(0.52, 0.78))
        top_w = full_w * float(rng.uniform(0.66, 1.00))
        cx = 96 / 2 + float(rng.uniform(-3, 3))

        def loop(y0, h, w, gap_centre):
            box = [cx - w / 2, y0, cx + w / 2, y0 + h]
            if rng.random() < 0.55:
                gap = float(rng.uniform(25, 80))
                draw.arc(box, gap_centre + gap / 2, gap_centre - gap / 2 + 360,
                         fill=255, width=width)
            else:
                draw.ellipse(box, outline=255, width=width)

        # Angles are clockwise from three o'clock, so 270 is the top of the loop and 180
        # its left side - the two places a pen lifting leaves the shape open.
        loop(top, top_h, top_w, float(rng.uniform(200, 300)))
        loop(top + top_h * 0.90, height - top_h * 0.90, full_w, float(rng.uniform(40, 140)))

        a = np.array(canvas, dtype=np.float32) / 255.0
        a = np.clip(ndimage.rotate(a, float(rng.uniform(-12, 12)), reshape=False, order=1), 0, 1)
        xs.append(normalise_array(photo_noise(a, rng)))
    return np.stack(xs), np.full(len(xs), 7, dtype=np.int64)     # class 7 is the digit 8


def tta_views(a):
    """The fixed set of small distortions a cell is judged over.

    Deterministic on purpose. A random set would score differently every run, and the
    Kotlin reader has to be able to apply exactly these - an average over views the phone
    cannot reproduce would flatter the figure here and change nothing on the device.

    Measured on 2 September 2026 and it gains nothing at all: 171/175 with the average and
    171/175 without it, identical on every photograph rather than close. That is a clearer
    answer than a small win would have been, and the reason is visible in the misses -
    what is left is not a cell the model nearly gets right from a slightly different
    angle, it is a shape it has the wrong idea about. Averaging five looks at the wrong
    idea returns the wrong idea.

    Kept, with the column, because it costs only inference and it is the sort of thing
    worth being talked out of twice.
    """
    views = [a]
    for angle in (-8.0, 8.0):
        views.append(np.clip(ndimage.rotate(a, angle, reshape=False, order=1), 0, 1))
    for factor in (0.92, 1.08):
        z = ndimage.zoom(a, factor, order=1)
        out = np.zeros_like(a)
        if z.shape[0] >= a.shape[0]:                     # zoomed in: take the middle
            off = (z.shape[0] - a.shape[0]) // 2
            out[:, :] = z[off:off + a.shape[0], off:off + a.shape[1]]
        else:                                            # zoomed out: sit it in the middle
            off = (a.shape[0] - z.shape[0]) // 2
            out[off:off + z.shape[0], off:off + z.shape[1]] = z
        views.append(np.clip(out, 0, 1))
    return views


def accuracy_tta(model, x, y, batch=512):
    """Accuracy with the probabilities averaged over [tta_views] rather than one look."""
    if len(x) == 0:
        return float("nan")
    model.eval()

    # Views are built once per cell, then regrouped so each view goes through the model as
    # one batch: five passes over the set rather than five per cell.
    per_cell = [tta_views(x[j, 0].numpy()) for j in range(len(x))]
    totals = None
    with torch.no_grad():
        for k in range(len(per_cell[0])):
            view = torch.tensor(np.stack([c[k] for c in per_cell]),
                                dtype=torch.float32).unsqueeze(1)
            probs = [F.softmax(model(view[i:i + batch].to(DEVICE)), dim=1).cpu()
                     for i in range(0, len(view), batch)]
            p = torch.cat(probs)
            totals = p if totals is None else totals + p
    return (totals.argmax(1) == y).float().mean().item()


def elastic(a, rng, strength=6.0):
    """Random smooth warp: the cheapest way to make a handful of samples into many."""
    dx = ndimage.gaussian_filter(rng.uniform(-1, 1, a.shape), 4) * strength
    dy = ndimage.gaussian_filter(rng.uniform(-1, 1, a.shape), 4) * strength
    yy, xx = np.meshgrid(np.arange(a.shape[0]), np.arange(a.shape[1]), indexing="ij")
    return ndimage.map_coordinates(a, [yy + dy, xx + dx], order=1, mode="constant")


def amplify(x, y, times, seed=SEED + 4):
    """Turns a few real samples into enough of them to matter, without new photographs."""
    rng = np.random.default_rng(seed)
    xs, ys = [], []
    for i in range(len(x)):
        for _ in range(times):
            a = x[i, 0] if x.ndim == 4 else x[i]
            a = elastic(a, rng, strength=float(rng.uniform(2, 7)))
            a = ndimage.rotate(a, float(rng.uniform(-12, 12)), reshape=False, order=1)
            a = ndimage.zoom(a, float(rng.uniform(0.88, 1.12)), order=1)
            a = np.clip(a, 0, 1)
            xs.append(normalise_array(a))
            ys.append(int(y[i]))
    return np.stack(xs), np.array(ys, dtype=np.int64)


def augment_mnist(x, seed=SEED):
    """Rotation and blur, so MNIST looks a little more like paper."""
    rng = np.random.default_rng(seed)
    out = np.empty_like(x)
    for i in range(len(x)):
        a = ndimage.rotate(x[i, 0], float(rng.uniform(-10, 10)), reshape=False, order=1)
        if rng.random() < 0.5:
            a = ndimage.gaussian_filter(a, sigma=float(rng.uniform(0.2, 0.8)))
        out[i, 0] = np.clip(a, 0, 1)
    return out


def mnist_tensors(train):
    ds = datasets.MNIST(DATA, train=train, download=True, transform=transforms.ToTensor())
    x = ds.data.float() / 255.0
    y = ds.targets
    keep = y != 0                       # the puzzle never contains a zero
    return x[keep].unsqueeze(1).numpy(), (y[keep] - 1).numpy()


def corpus_cells():
    """Every labelled digit in the corpus, with the photograph it came from."""
    xs, ys, sources, photos = [], [], [], []
    for stem, cl in load_labels().items():
        directory = os.path.join(CELLS, stem)
        if not os.path.isdir(directory):
            continue
        for i in range(81):
            digit, source = cl[i]
            if digit is None:
                continue
            gray = np.array(Image.open(os.path.join(directory, "cell_%02d.png" % i)).convert("L"),
                            dtype=np.float32)
            h, w = gray.shape
            blobs = components(mask_of(gray), h, w)
            if not blobs:
                continue
            xs.append(normalise(gray, max(blobs, key=lambda b: b["size"])))
            ys.append(digit - 1)
            sources.append(source)
            photos.append(stem)
    return np.stack(xs), np.array(ys, dtype=np.int64), sources, photos


DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")


def accuracy(model, x, y, batch=1024):
    if len(x) == 0:
        return float("nan")
    model.eval()
    correct = 0
    with torch.no_grad():
        for i in range(0, len(x), batch):
            xb = x[i:i + batch].to(DEVICE)
            yb = y[i:i + batch].to(DEVICE)
            correct += (model(xb).argmax(1) == yb).sum().item()
    return correct / len(x)


def fit(x, y, epochs=6, seed=SEED):
    """Trains on the GPU when there is one, and on the CPU otherwise.

    The whole training set is a hundred and twenty thousand 28x28 images and the model is
    about a hundred thousand parameters, so it fits on any card several times over. Moving
    it once, up front, rather than a batch at a time is what makes the difference: at this
    size a step is over before a batch could be copied, so per-batch transfer would leave
    the card waiting on the bus rather than the arithmetic.

    Same seed, so a run is reproducible on either - though not bit-for-bit *between* them,
    since the two use different reduction orders. The numbers below move in the third
    decimal place when the device changes, which is worth knowing before reading anything
    into a small difference.
    """
    torch.manual_seed(seed)
    model = Net().to(DEVICE)
    optimiser = torch.optim.Adam(model.parameters(), lr=1e-3)
    loader = DataLoader(
        TensorDataset(torch.tensor(x).to(DEVICE), torch.tensor(y).to(DEVICE)),
        batch_size=256, shuffle=True,
    )
    for _ in range(epochs):
        model.train()
        for xb, yb in loader:
            optimiser.zero_grad()
            F.cross_entropy(model(xb), yb).backward()
            optimiser.step()
    return model


def export(model, path):
    """Flat float32 little-endian, layer by layer, in the order Kotlin reads them."""
    order = ["c1.weight", "c1.bias", "c2.weight", "c2.bias",
             "fc1.weight", "fc1.bias", "fc2.weight", "fc2.bias"]
    state = model.state_dict()
    shapes = {}
    with open(path, "wb") as f:
        f.write(b"SUDK")                       # magic
        f.write(struct.pack("<i", 1))          # format version
        for key in order:
            tensor = state[key].detach().cpu().numpy().astype("<f4")
            shapes[key] = list(tensor.shape)
            f.write(tensor.tobytes())
    return shapes


def build_sources():
    """Everything except the corpus: the part that can never flatter a corpus score."""
    print("loading MNIST ...")
    xm, ym = mnist_tensors(True)
    print("augmenting MNIST ...")
    xm = augment_mnist(xm)

    print("rendering printed digits ...")
    fonts = usable_fonts()
    xp, yp = synthetic_printed(fonts)
    print(f"  {len(fonts)} fonts -> {len(xp)} printed samples")

    print("drawing continental ones and sevens ...")
    x1, y1 = continental_ones()
    x7, y7 = continental_sevens()
    print("curling MNIST nines ...")
    x9, y9 = curl(xm, ym)
    print("drawing eights with loops that do not close ...")
    x8, y8 = written_eights()
    print(f"  {len(x1)} ones, {len(x7)} sevens, {len(x9)} curled nines, {len(x8)} eights")

    x = np.concatenate([xm[:, 0], xp, x1, x7, x9, x8])[:, None, :, :]
    y = np.concatenate([ym, yp, y1, y7, y9, y8])
    return x, y


def main():
    print("training on %s" % (
        torch.cuda.get_device_name(0) if DEVICE.type == "cuda" else "the CPU"
    ))
    np.random.seed(SEED)
    base_x, base_y = build_sources()

    xc, yc, sources, photos = corpus_cells()
    guess = np.array([s == "guess" for s in sources])
    given = ~guess
    print(f"  corpus {len(xc)} real digits ({given.sum()} printed, {guess.sum()} handwritten)")

    if "--lopo" in sys.argv:
        print("\n=== leave-one-photograph-out ===")
        print("A model that has never seen the photograph it is scored on.\n")
        total_right = total = tta_right = 0
        lopo_wrong = []
        for stem in sorted(set(photos)):
            held = np.array([p == stem for p in photos])
            keep = ~held
            ax, ay = amplify(xc[keep][:, None], yc[keep], CORPUS_TIMES)
            model = fit(np.concatenate([base_x, ax[:, None]]), np.concatenate([base_y, ay]))
            tx = torch.tensor(xc[held][:, None])
            ty = torch.tensor(yc[held])
            hand = torch.tensor(guess[held])
            a_all = accuracy(model, tx, ty)
            a_hand = accuracy(model, tx[hand], ty[hand])
            t_hand = accuracy_tta(model, tx[hand], ty[hand]) if int(hand.sum()) else float("nan")
            total_right += round(a_hand * int(hand.sum())) if int(hand.sum()) else 0
            total += int(hand.sum())
            tta_right += round(t_hand * int(hand.sum())) if int(hand.sum()) else 0
            print(f"  {stem:<40} all {a_all:.3f}   handwriting {a_hand:.3f} "
                  f"-> {t_hand:.3f} with tta ({int(hand.sum())} cells)")

            # Which cells it got wrong, not just how many. A rate says whether to
            # keep going; the confusions say what to keep going *at* - and these are
            # the only misreads worth reading, since the shipped model has seen every
            # cell it is scored on and so reports none at all.
            model.eval()
            with torch.no_grad():
                pred = model(tx.to(DEVICE)).argmax(1).cpu()
            for pv, tv, hv in zip(pred.tolist(), ty.tolist(), hand.tolist()):
                if pv != tv:
                    lopo_wrong.append((tv + 1, pv + 1, "hand" if hv else "print", stem))
        print(f"\n  handwriting, unseen photographs: {total_right}/{total} = "
              f"{total_right / max(1, total):.3f}")
        print(f"  the same, averaged over five views:  {tta_right}/{total} = "
              f"{tta_right / max(1, total):.3f}")

        print()
        print("  every miss on an unseen photograph (truth -> read):")
        tally = {}
        for tv, pv, kind, stem in lopo_wrong:
            tally[(tv, pv, kind)] = tally.get((tv, pv, kind), 0) + 1
        for (tv, pv, kind), n in sorted(tally.items(), key=lambda kv: -kv[1]):
            print(f"    {tv} -> {pv}  ({kind}): {n}")
        for tv, pv, kind, stem in lopo_wrong:
            print(f"      {stem}: {tv} read as {pv} ({kind})")

    print("\n=== the shipped model, trained on everything ===")
    ax, ay = amplify(xc[:, None], yc, CORPUS_TIMES)
    x = np.concatenate([base_x, ax[:, None]])
    y = np.concatenate([base_y, ay])
    print(f"  training set {len(x)}")
    model = fit(x, y)

    xmt, ymt = mnist_tensors(False)
    tx, ty = torch.tensor(xc[:, None]), torch.tensor(yc)
    print(f"  mnist {accuracy(model, torch.tensor(xmt), torch.tensor(ymt)):.4f}   "
          f"corpus printed {accuracy(model, tx[given], ty[given]):.4f}   "
          f"corpus handwriting {accuracy(model, tx[guess], ty[guess]):.4f}")

    torch.save({k: v.cpu() for k, v in model.state_dict().items()},
               os.path.join(HERE, "digits.pt"))
    target = os.path.join(HERE, "digits.bin")
    shapes = export(model, target)
    with open(os.path.join(HERE, "digits.json"), "w", encoding="utf-8") as f:
        json.dump({"format": 1, "classes": list(range(1, 10)), "input": [28, 28],
                   "shapes": shapes}, f, indent=2)
    print(f"exported {target} ({os.path.getsize(target)} bytes)")

    model.eval()
    with torch.no_grad():
        pred = model(tx.to(DEVICE)).argmax(1).cpu()
    wrong = {}
    for p, t, s in zip(pred.tolist(), yc.tolist(), sources):
        if p != t:
            wrong[(t + 1, p + 1, s)] = wrong.get((t + 1, p + 1, s), 0) + 1
    print("remaining misreads (truth -> predicted, source): count")
    for (t, p, s), n in sorted(wrong.items(), key=lambda kv: -kv[1]):
        print(f"  {t} -> {p}  ({s}): {n}")


#: How many augmented copies of each real corpus cell to train on. A hundred or so is
#: what it takes for ninety-odd samples to weigh anything against sixty thousand.
CORPUS_TIMES = 120

if __name__ == "__main__":
    main()
