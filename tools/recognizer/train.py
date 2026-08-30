"""Trains the digit classifier and exports it for the Kotlin runtime.

Two training sources, because the corpus proved one is not enough:
  * MNIST digits 1-9, for handwriting.
  * Synthetic printed digits rendered from system fonts, because MNIST contains no
    printed glyphs at all and an MNIST-only model reads every printed 6 as an 8.

Exports raw float32 weights rather than LiteRT. The network is small enough to run in
a few hundred lines of Kotlin, which keeps the whole inference path JVM-testable and
adds nothing to the APK.
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
ASSETS = os.path.join(os.path.dirname(os.path.dirname(HERE)), "SudokuAI")
MIN_DIGIT_HEIGHT = 0.53
FONT_DIR = r"C:\Windows\Fonts"


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


def synthetic_printed(fonts, per_font=14, seed=20260830):
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
                arr = np.array(canvas, dtype=np.float32) / 255.0

                angle = float(rng.uniform(-8, 8))
                arr = ndimage.rotate(arr, angle, reshape=False, order=1)
                arr = ndimage.gaussian_filter(arr, sigma=float(rng.uniform(0.3, 1.6)))
                arr = arr + rng.normal(0, float(rng.uniform(0.01, 0.10)), arr.shape)
                arr = np.clip(arr, 0, 1)
                # Thresholding artefacts, as adaptive thresholding on paper produces.
                arr = (arr > float(rng.uniform(0.30, 0.55))).astype(np.float32)

                xs.append(normalise_array(arr))
                ys.append(digit - 1)
    return np.stack(xs), np.array(ys)


def augment_mnist(x, seed=20260830):
    """Rotation, scale and threshold jitter, so MNIST looks a little more like paper."""
    rng = np.random.default_rng(seed)
    out = np.empty_like(x)
    for i in range(len(x)):
        a = x[i, 0]
        a = ndimage.rotate(a, float(rng.uniform(-10, 10)), reshape=False, order=1)
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


def corpus_tensors():
    labels = load_labels()
    xs, ys, sources = [], [], []
    for stem, cl in labels.items():
        directory = os.path.join(CELLS, stem)
        for i in range(81):
            path = os.path.join(directory, "cell_%02d.png" % i)
            gray = np.array(Image.open(path).convert("L"), dtype=np.float32)
            h, w = gray.shape
            blobs = components(mask_of(gray), h, w)
            if not blobs:
                continue
            big = max(blobs, key=lambda b: b["size"])
            if big["h"] / h < MIN_DIGIT_HEIGHT:
                continue
            digit, source = cl[i]
            if digit is None:
                continue
            xs.append(normalise(gray, big))
            ys.append(digit - 1)
            sources.append(source)
    return torch.tensor(np.stack(xs)).unsqueeze(1), torch.tensor(ys), sources


def accuracy(model, x, y, batch=1024):
    model.eval()
    correct = 0
    with torch.no_grad():
        for i in range(0, len(x), batch):
            correct += (model(x[i:i + batch]).argmax(1) == y[i:i + batch]).sum().item()
    return correct / len(x)


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


def main():
    torch.manual_seed(20260830)
    np.random.seed(20260830)

    print("loading MNIST ...")
    xm, ym = mnist_tensors(True)
    xmt, ymt = mnist_tensors(False)

    print("rendering synthetic printed digits ...")
    fonts = usable_fonts()
    xp, yp = synthetic_printed(fonts)
    print(f"  {len(fonts)} fonts -> {len(xp)} printed samples")

    print("augmenting MNIST ...")
    xm = augment_mnist(xm)

    x = torch.tensor(np.concatenate([xm, xp[:, None, :, :]]))
    y = torch.tensor(np.concatenate([ym, yp]))
    print(f"  training set {len(x)} ({len(xm)} handwritten, {len(xp)} printed)")

    xc, yc, sources = corpus_tensors()
    given = [i for i, s in enumerate(sources) if s == "given"]
    guess = [i for i, s in enumerate(sources) if s == "guess"]
    print(f"  corpus {len(xc)} real digits ({len(given)} given, {len(guess)} guess)")

    model = Net()
    optimiser = torch.optim.Adam(model.parameters(), lr=1e-3)
    loader = DataLoader(TensorDataset(x, y), batch_size=256, shuffle=True)
    xmt_t, ymt_t = torch.tensor(xmt), torch.tensor(ymt)

    best = 0.0
    for epoch in range(1, 7):
        model.train()
        for xb, yb in loader:
            optimiser.zero_grad()
            F.cross_entropy(model(xb), yb).backward()
            optimiser.step()
        a_all = accuracy(model, xc, yc)
        a_given = accuracy(model, xc[given], yc[given])
        a_guess = accuracy(model, xc[guess], yc[guess])
        print(f"epoch {epoch}: mnist {accuracy(model, xmt_t, ymt_t):.4f}  "
              f"corpus {a_all:.4f}  given {a_given:.4f}  guess {a_guess:.4f}")
        if a_all >= best:
            best = a_all
            torch.save(model.state_dict(), os.path.join(HERE, "digits.pt"))

    model.load_state_dict(torch.load(os.path.join(HERE, "digits.pt")))
    out_dir = os.path.join(os.path.dirname(os.path.dirname(HERE)),
                           os.path.basename(os.path.dirname(HERE)))
    target = os.path.join(HERE, "digits.bin")
    shapes = export(model, target)
    meta = {"format": 1, "classes": list(range(1, 10)), "input": [28, 28], "shapes": shapes}
    with open(os.path.join(HERE, "digits.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)
    print(f"\nexported {target} ({os.path.getsize(target)} bytes)")

    model.eval()
    with torch.no_grad():
        pred = model(xc).argmax(1)
    wrong = {}
    for p, t, s in zip(pred.tolist(), yc.tolist(), sources):
        if p != t:
            wrong[(t + 1, p + 1, s)] = wrong.get((t + 1, p + 1, s), 0) + 1
    print("misreads (truth -> predicted, source): count")
    for (t, p, s), n in sorted(wrong.items(), key=lambda kv: -kv[1]):
        print(f"  {t} -> {p}  ({s}): {n}")
    print(f"final: corpus {accuracy(model, xc, yc):.4f}  "
          f"given {accuracy(model, xc[given], yc[given]):.4f}  "
          f"guess {accuracy(model, xc[guess], yc[guess]):.4f}")


if __name__ == "__main__":
    main()
