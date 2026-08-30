"""Trains the digit classifier and measures it against the real corpus.

Deliberately reports accuracy on the photographs separately from held-out MNIST,
because the gap between the two is the whole question: MNIST is loose-leaf
handwriting, not digits written into a 5mm square next to printed ones.
"""
import os
import sys

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
from PIL import Image
from scipy import ndimage
from torch.utils.data import DataLoader, TensorDataset
from torchvision import datasets, transforms

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cells import CELLS, components, load_labels, normalise  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "data")
MIN_DIGIT_HEIGHT = 0.53   # measured: lowest real digit 0.556, highest empty cell 0.505


class Net(nn.Module):
    """Small CNN over a 28x28 binary digit. Nine classes: the digits 1..9."""

    def __init__(self):
        super().__init__()
        self.c1 = nn.Conv2d(1, 32, 3, padding=1)
        self.c2 = nn.Conv2d(32, 64, 3, padding=1)
        self.fc1 = nn.Linear(64 * 7 * 7, 128)
        self.drop = nn.Dropout(0.3)
        self.fc2 = nn.Linear(128, 9)

    def forward(self, x):
        x = F.max_pool2d(F.relu(self.c1(x)), 2)
        x = F.max_pool2d(F.relu(self.c2(x)), 2)
        x = x.flatten(1)
        x = self.drop(F.relu(self.fc1(x)))
        return self.fc2(x)


def mask_of(gray):
    local = ndimage.uniform_filter(gray, size=31)
    return ndimage.binary_opening(gray < (local - 6), np.ones((2, 2)), iterations=1)


def mnist_tensors(train):
    ds = datasets.MNIST(DATA, train=train, download=True, transform=transforms.ToTensor())
    x = ds.data.float() / 255.0
    y = ds.targets
    keep = y != 0                       # the puzzle never contains a zero
    return x[keep].unsqueeze(1), (y[keep] - 1)


def corpus_tensors():
    """Every real cell the triage calls a digit, with its ground-truth label."""
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
                continue                # triage false positive; counted elsewhere
            xs.append(normalise(gray, big))
            ys.append(digit - 1)
            sources.append(source)
    x = torch.tensor(np.stack(xs)).unsqueeze(1)
    return x, torch.tensor(ys), sources


def accuracy(model, x, y, batch=512):
    model.eval()
    correct = 0
    with torch.no_grad():
        for i in range(0, len(x), batch):
            correct += (model(x[i:i + batch]).argmax(1) == y[i:i + batch]).sum().item()
    return correct / len(x)


def main():
    torch.manual_seed(20260830)
    print("loading MNIST ...")
    xtr, ytr = mnist_tensors(True)
    xte, yte = mnist_tensors(False)
    print(f"  train {len(xtr)}  test {len(xte)}")

    print("loading corpus cells ...")
    xc, yc, sources = corpus_tensors()
    print(f"  {len(xc)} real digits ({sources.count('given')} given, {sources.count('guess')} guess)")

    model = Net()
    optimiser = torch.optim.Adam(model.parameters(), lr=1e-3)
    loader = DataLoader(TensorDataset(xtr, ytr), batch_size=256, shuffle=True)

    for epoch in range(1, 4):
        model.train()
        for xb, yb in loader:
            optimiser.zero_grad()
            F.cross_entropy(model(xb), yb).backward()
            optimiser.step()
        given = [i for i, s in enumerate(sources) if s == "given"]
        guess = [i for i, s in enumerate(sources) if s == "guess"]
        print(
            f"epoch {epoch}: mnist-test {accuracy(model, xte, yte):.4f}   "
            f"corpus-all {accuracy(model, xc, yc):.4f}   "
            f"corpus-given {accuracy(model, xc[given], yc[given]):.4f}   "
            f"corpus-guess {accuracy(model, xc[guess], yc[guess]):.4f}"
        )

    torch.save(model.state_dict(), os.path.join(HERE, "digits.pt"))
    print("saved digits.pt")

    # Where does it actually go wrong?
    model.eval()
    with torch.no_grad():
        pred = model(xc).argmax(1)
    confusion = {}
    for p, t, s in zip(pred.tolist(), yc.tolist(), sources):
        if p != t:
            confusion[(t + 1, p + 1, s)] = confusion.get((t + 1, p + 1, s), 0) + 1
    print("\nmisreads (truth -> predicted, source): count")
    for (t, p, s), n in sorted(confusion.items(), key=lambda kv: -kv[1]):
        print(f"  {t} -> {p}  ({s}): {n}")


if __name__ == "__main__":
    main()
