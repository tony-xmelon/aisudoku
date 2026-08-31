"""Draws the AI Sudoku launcher icon.

The mark is the app's own visual language: a digit knocked out of a coloured square,
with the grid showing through around it. Exactly what the reading layer does to a
photograph, which is the one thing this app does that others do not.

Geometry is in a 108-unit square so the same numbers serve the adaptive vector and the
raster mipmaps.
"""
import os
import sys

from PIL import Image, ImageDraw

S = 108.0                     # the adaptive icon viewport
CYAN_TOP = (77, 208, 225)     # #4DD0E1, the app's "printed" colour
CYAN_BOTTOM = (26, 148, 168)  # #1A94A8
INK = (14, 18, 24)            # #0E1218
GRID = (0, 0, 0, 34)          # the 3x3 lines, a darkening rather than a colour

# The nine. A geometric bowl and a straight stem, which stays legible at 48 pixels
# where a typeset glyph turns to mush.
# Deliberately wider than the middle cell of the grid, so it cuts clean across the lines
# instead of grazing them. That crossing is what the reading layer does to a photograph.
BOWL = (52.0, 43.0)           # centre
BOWL_OUTER = 20.0
BOWL_INNER = 11.4
STEM_RIGHT = BOWL[0] + BOWL_OUTER
STEM_WIDTH = 9.4
STEM_BOTTOM = 84.0
GRID_AT = (36.0, 72.0)        # thirds of the canvas
GRID_WIDTH = 2.6


def draw_grid(d, k, colour):
    for at in GRID_AT:
        d.rectangle([0, (at - GRID_WIDTH / 2) * k, S * k, (at + GRID_WIDTH / 2) * k], fill=colour)
        d.rectangle([(at - GRID_WIDTH / 2) * k, 0, (at + GRID_WIDTH / 2) * k, S * k], fill=colour)


def draw_nine(d, k, colour):
    cx, cy = BOWL
    d.ellipse(
        [(cx - BOWL_OUTER) * k, (cy - BOWL_OUTER) * k,
         (cx + BOWL_OUTER) * k, (cy + BOWL_OUTER) * k],
        fill=colour,
    )
    d.rounded_rectangle(
        [(STEM_RIGHT - STEM_WIDTH) * k, cy * k, STEM_RIGHT * k, STEM_BOTTOM * k],
        radius=(STEM_WIDTH / 2) * k,
        fill=colour,
    )


def punch_counter(image, k):
    """The hole in the bowl, cut rather than painted so it works over any ground."""
    cx, cy = BOWL
    hole = Image.new("L", image.size, 255)
    ImageDraw.Draw(hole).ellipse(
        [(cx - BOWL_INNER) * k, (cy - BOWL_INNER) * k,
         (cx + BOWL_INNER) * k, (cy + BOWL_INNER) * k],
        fill=0,
    )
    alpha = image.getchannel("A")
    image.putalpha(Image.composite(alpha, Image.new("L", image.size, 0), hole))


def gradient(size):
    ramp = Image.new("RGB", (1, size))
    for y in range(size):
        t = y / max(1, size - 1)
        ramp.putpixel((0, y), tuple(
            round(CYAN_TOP[i] + (CYAN_BOTTOM[i] - CYAN_TOP[i]) * t) for i in range(3)
        ))
    return ramp.resize((size, size))


def render(size, shape, supersample=4):
    """One finished icon: cyan tile, grid lines, and the nine cut out of both."""
    k = size * supersample / S
    px = int(size * supersample)

    tile = gradient(px).convert("RGBA")
    draw_grid(ImageDraw.Draw(tile, "RGBA"), k, GRID)

    # The nine is a hole, so what shows through it is whatever the icon sits on - which
    # for the mipmaps is the ink colour underneath, and for the adaptive layer is the
    # background layer itself.
    hole = Image.new("L", (px, px), 255)
    draw_nine(ImageDraw.Draw(hole), k, 0)
    counter = Image.new("L", (px, px), 0)
    ImageDraw.Draw(counter).ellipse(
        [(BOWL[0] - BOWL_INNER) * k, (BOWL[1] - BOWL_INNER) * k,
         (BOWL[0] + BOWL_INNER) * k, (BOWL[1] + BOWL_INNER) * k],
        fill=255,
    )
    hole = Image.composite(Image.new("L", (px, px), 255), hole, counter)

    ground = Image.new("RGBA", (px, px), INK + (255,))
    art = Image.composite(tile, ground, hole)

    mask = Image.new("L", (px, px), 0)
    d = ImageDraw.Draw(mask)
    if shape == "round":
        d.ellipse([0, 0, px, px], fill=255)
    else:
        d.rounded_rectangle([0, 0, px, px], radius=px * 0.22, fill=255)
    art.putalpha(mask)
    return art.resize((size, size), Image.LANCZOS)


def main():
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96),
                          ("xxhdpi", 144), ("xxxhdpi", 192)]:
        for shape, name in [("square", "ic_launcher"), ("round", "ic_launcher_round")]:
            folder = os.path.join(out, "mipmap-" + density)
            os.makedirs(folder, exist_ok=True)
            render(size, shape).save(os.path.join(folder, name + ".png"))
    # A sheet for looking at, at the sizes that actually matter.
    sheet = Image.new("RGBA", (460, 250), (24, 24, 28, 255))
    x = 14
    for size in (192, 96, 72, 48, 36):
        square = render(size, "square")
        round_ = render(size, "round")
        sheet.paste(square, (x, 14), square)
        sheet.paste(round_, (x, 220 - size), round_)
        x += size + 14
    sheet.save(os.path.join(out, "preview.png"))
    print("wrote icons to", out)


if __name__ == "__main__":
    main()
