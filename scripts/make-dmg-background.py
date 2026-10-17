#!/usr/bin/env python3
"""Draws the DMG window background (1x and @2x). Needs Pillow; the PNGs are committed."""
from pathlib import Path
from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "dari-desktop-app/packaging/dmg"
WIDTH, HEIGHT = 660, 400
# Keep in sync with icon_locations in dmg-settings.py.
APP_X, APPLICATIONS_X, ICON_Y = 190, 470, 180
BACKGROUND = (241, 243, 246)
ARROW = (58, 67, 80)


def stroke(pixels, start, end, width):
    """A line with round caps."""
    pixels.line([start, end], fill=ARROW, width=width)
    for x, y in (start, end):
        pixels.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=ARROW)


def draw(scale):
    # Draw at 4x and downsample, since Pillow strokes are not anti-aliased.
    big = scale * 4
    image = Image.new("RGB", (WIDTH * big, HEIGHT * big), BACKGROUND)
    pixels = ImageDraw.Draw(image)
    s = lambda value: value * big
    center, y, half, head, width = (APP_X + APPLICATIONS_X) / 2, ICON_Y, 24, 14, round(s(4))
    tip = (s(center + half), s(y))
    stroke(pixels, (s(center - half), s(y)), tip, width)
    stroke(pixels, tip, (s(center + half - head), s(y - head)), width)
    stroke(pixels, tip, (s(center + half - head), s(y + head)), width)
    return image.resize((WIDTH * scale, HEIGHT * scale), Image.LANCZOS)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    draw(1).save(OUT / "background.png", dpi=(72, 72))
    draw(2).save(OUT / "background@2x.png", dpi=(144, 144))
