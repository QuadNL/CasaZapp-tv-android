"""Draws the Android TV launcher banner (#79) in the app's style.

    python design/make_banner.py

B2 (logo left, name right) becomes the app's banner; B3 (logo above the name) is kept as an
alternative in design/. Everything is drawn at twice the size and scaled down for smooth edges.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONT = ROOT / "tv/src/main/res/font/sora_700.ttf"
W, H = 1280, 720  # 320x180 dp at xxxhdpi
SS = 2  # supersampling
ACCENT = (255, 181, 71)
INK = (28, 19, 5)


def background(w: int, h: int) -> Image.Image:
    """The glow of the app's "continue watching" block: blue light over a dark diagonal."""
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    # linear-gradient(160deg, #141c28, #05070a)
    angle = np.deg2rad(160)
    dx, dy = np.sin(angle), -np.cos(angle)
    length = abs(w * dx) + abs(h * dy)
    t = np.clip(((x - w / 2) * dx + (y - h / 2) * dy) / length + 0.5, 0, 1)[..., None]
    base = np.array([20, 28, 40]) * (1 - t) + np.array([5, 7, 10]) * t
    # radial-gradient(70% 90% at 25% 30%, #1e3a5f, transparent 70%)
    r = np.sqrt(((x - 0.25 * w) / (0.7 * w)) ** 2 + ((y - 0.3 * h) / (0.9 * h)) ** 2)
    a = np.clip(1 - r / 0.7, 0, 1)[..., None]
    rgb = base * (1 - a) + np.array([30, 58, 95]) * a
    return Image.fromarray(rgb.astype(np.uint8), "RGB")


def logo(size: int) -> Image.Image:
    """The yellow tile with the house and play sign, as in the web app's logo."""
    tile = Image.new("RGBA", (size, size))
    d = ImageDraw.Draw(tile)
    d.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.23), fill=ACCENT)
    icon = size * 46 / 76  # icon size within the tile
    s = icon / 24
    ox = oy = (size - icon) / 2
    p = lambda x, y: (ox + x * s, oy + y * s)
    # Starts and ends on the flat bottom, so the open ends of the line do not show at a corner.
    house = [p(12, 21), p(4, 21), p(3.3, 20.7), p(3, 20), p(3, 10), p(12, 3), p(21, 10), p(21, 20), p(20.7, 20.7), p(20, 21), p(12, 21)]
    d.line(house, fill=INK, width=round(2.2 * s), joint="curve")
    d.polygon([p(10, 12), p(10, 18), p(15, 15)], fill=INK)
    return tile


def banner(stacked: bool) -> Image.Image:
    w, h = W * SS, H * SS
    img = background(w, h).convert("RGBA")
    d = ImageDraw.Draw(img)
    unit = w / 320  # one CSS pixel of the 320x180 mockup
    tile = logo(round((72 if stacked else 76) * unit))
    font = ImageFont.truetype(str(FONT), round((28 if stacked else 32) * unit))
    name = "CasaZapp"
    spacing = -0.5 * unit
    widths = [font.getlength(c) for c in name]
    text_w = sum(widths) + spacing * (len(name) - 1)
    ascent, descent = font.getmetrics()
    text_h = ascent + descent

    if stacked:
        gap = 10 * unit
        top = (h - tile.height - gap - text_h) / 2
        img.alpha_composite(tile, (round((w - tile.width) / 2), round(top)))
        x, y = (w - text_w) / 2, top + tile.height + gap
    else:
        gap = 16 * unit
        left = (w - tile.width - gap - text_w) / 2
        img.alpha_composite(tile, (round(left), round((h - tile.height) / 2)))
        x, y = left + tile.width + gap, (h - text_h) / 2
    for c, cw in zip(name, widths):
        d.text((x, y), c, font=font, fill=(255, 255, 255))
        x += cw + spacing
    return img.resize((W, H), Image.LANCZOS).convert("RGB")


if __name__ == "__main__":
    b2, b3 = banner(stacked=False), banner(stacked=True)
    b2.save(ROOT / "tv/src/main/res/drawable-xxxhdpi/banner.png", optimize=True)
    b2.save(ROOT / "design/banner-b2.png", optimize=True)
    b3.save(ROOT / "design/banner-b3.png", optimize=True)
    print("banner.png (B2) and design/banner-b3.png written")
