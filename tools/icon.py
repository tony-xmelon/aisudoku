"""Draws the AI Sudoku launcher icon, raster and geometry.

The icon is the thing the app looks at: a printed sudoku on paper, zoomed until one box
fills the frame, with the ruled lines running off the edges because the grid carries on.
One cell is filled in the app's cyan - what it does to a square it has just read.

Three things are deliberate and easy to undo by accident:

  * **Digits, and a heavy box rule.** A bare three-by-three grid is noughts and crosses.
    What makes a grid read as sudoku is numbers in it and box rules heavier than cell
    rules. Both cost legibility, which is why the grid is one box rather than all nine -
    a whole nine-by-nine is unreadable mush below about seventy pixels.

  * **Paper, not ink.** A dark tile is stylish and says nothing. White paper with black
    rules says *printed puzzle*, which is what the camera is pointed at, and it holds up
    against a dark wallpaper and a light one.

  * **The corners are empty.** An adaptive icon is only guaranteed to show the middle
    circle of its 108 units, and the corner cells of a three-by-three sit exactly where a
    round mask bites. Nothing is put there that would be missed.

Geometry is in a 108-unit square so these numbers also serve the vector drawables in
app/src/main/res/drawable. The two must not drift apart.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

S = 108.0
STEP = 24.0                      # one cell
BOX = (18.0, 90.0)               # the box rules, and the adaptive safe area
CYAN = (77, 208, 225)
INK = (17, 21, 26)
PAPER = (243, 246, 248)
THIN, HEAVY = 1.5, 3.4
FONT = r"C:\Windows\Fonts\tahomabd.ttf"

# A plausibly part-solved box, with the corners left empty so a round mask has nothing
# to take. Sequential digits would read as a telephone keypad.
CELLS = {(1, 0): "5", (0, 1): "9", (1, 1): "7", (2, 1): "3", (1, 2): "1"}
HILITE = (1, 1)


def centred(d, box, text, font, fill):
    left, top, right, bottom = d.textbbox((0, 0), text, font=font)
    d.text(
        (box[0] + (box[2] - box[0] - (right - left)) / 2 - left,
         box[1] + (box[3] - box[1] - (bottom - top)) / 2 - top),
        text, font=font, fill=fill,
    )


def draw(img, k, layer="all"):
    """The mark, in units of the 108 grid scaled by k.

    An adaptive icon wants it in two halves: the paper, the cell and the rules underneath,
    and the digits on top where the safe zone protects them.
    """
    d = ImageDraw.Draw(img, "RGBA")
    step = STEP * k

    if layer in ("all", "background"):
        for (col, row) in CELLS:
            if (col, row) != HILITE:
                continue
            x0 = BOX[0] * k + col * step
            y0 = BOX[0] * k + row * step
            d.rectangle((x0, y0, x0 + step, y0 + step), fill=CYAN + (255,))

    # Rules run the full width, so the grid is a fragment of something larger rather than
    # an object with a border round it.
    font = ImageFont.truetype(FONT, max(6, int(step * 0.68)))
    if layer in ("all", "background", "monochrome"):
        for i in range(-2, 6):
            at = (BOX[0] + i * STEP) * k
            if at < -HEAVY * k or at > S * k + HEAVY * k:
                continue
            heavy = i in (0, 3)
            w = (HEAVY if heavy else THIN) * k
            colour = INK + (255 if heavy else 120,)
            d.rectangle([-w, at - w / 2, S * k + w, at + w / 2], fill=colour)
            d.rectangle([at - w / 2, -w, at + w / 2, S * k + w], fill=colour)

    if layer != "background":
        for (col, row), text in CELLS.items():
            x0 = BOX[0] * k + col * step
            y0 = BOX[0] * k + row * step
            centred(d, (x0, y0, x0 + step, y0 + step), text, font, INK)


def render(size, shape, supersample=4):
    px = int(size * supersample)
    img = Image.new("RGBA", (px, px), PAPER + (255,))
    draw(img, px / S)

    mask = Image.new("L", (px, px), 0)
    m = ImageDraw.Draw(mask)
    if shape == "round":
        m.ellipse([0, 0, px, px], fill=255)
    else:
        m.rounded_rectangle([0, 0, px, px], radius=px * 0.22, fill=255)
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


def layer(name, px=432):
    """One adaptive-icon layer, full bleed, on transparency.

    Everything but the paper lives in the foreground, and the background is a flat colour.
    That is not how these are usually split, and the reason is the launch screen: from
    Android 12 the system draws it from the FOREGROUND layer alone, so anything left in
    the background is missing from it. With the whole mark in front, the splash is the
    icon.
    """
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    draw(img, px / S, layer="all" if name == "foreground" else name)
    return img


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)

    # Adaptive layers as bitmaps rather than vectors: the mark contains type, and tracing
    # five glyphs to path data by hand is a good way to ship a wonky 9.
    adaptive = os.path.join(out, "drawable-nodpi")
    os.makedirs(adaptive, exist_ok=True)
    for name in ("foreground", "monochrome"):
        layer(name).save(os.path.join(adaptive, "ic_launcher_%s.png" % name))

    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                          ("xxhdpi", 144), ("xxxhdpi", 192)]:
        folder = os.path.join(out, "mipmap-" + density)
        os.makedirs(folder, exist_ok=True)
        for shape, name in [("square", "ic_launcher"), ("round", "ic_launcher_round")]:
            render(size, shape).save(os.path.join(folder, name + ".png"))

    sizes = [192, 96, 72, 48, 36]
    sheet = Image.new("RGBA", (20 + sum(s + 16 for s in sizes), 420), (26, 26, 30, 255))
    x = 14
    for size in sizes:
        for row, shape in enumerate(("square", "round")):
            art = render(size, shape)
            sheet.paste(art, (x, 14 + row * 200 + (192 - size) // 2), art)
        x += size + 16
    sheet.save(os.path.join(out, "preview.png"))
    print("wrote icons to", out)


if __name__ == "__main__":
    main()
