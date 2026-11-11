#!/usr/bin/env python3
"""Render the RX Client brand assets (launcher icons, notification icon, banner).

The mark is a shield with a lightning bolt knocked out of it, on a violet -> cyan
gradient tile.  Everything is drawn from code so the assets can be regenerated:

    python3 branding/render_brand.py
"""

from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(ROOT)
RES = os.path.join(REPO, "V2rayNG", "app", "src", "main", "res")

# Brand palette
VIOLET = (109, 74, 255)
VIOLET_LIGHT = (155, 106, 255)
CYAN = (25, 217, 236)
INK = (11, 11, 20)
WHITE = (255, 255, 255)

SS = 4  # supersampling factor


def lerp(a: int, b: int, t: float) -> int:
    return int(round(a + (b - a) * t))


def mix(c1, c2, t):
    return (lerp(c1[0], c2[0], t), lerp(c1[1], c2[1], t), lerp(c1[2], c2[2], t))


def gradient(size: int) -> Image.Image:
    """Diagonal violet -> cyan gradient with a lime-free mid stop."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    maxd = (size - 1) * 2
    for y in range(size):
        for x in range(size):
            t = (x + y) / maxd
            if t < 0.55:
                c = mix(VIOLET, VIOLET_LIGHT, t / 0.55)
            else:
                c = mix(VIOLET_LIGHT, CYAN, (t - 0.55) / 0.45)
            px[x, y] = c
    return img


def cubic(p0, c1, c2, p1, steps=24):
    """Sample a cubic bezier so we can fill it as a polygon."""
    out = []
    for i in range(steps + 1):
        t = i / steps
        mt = 1 - t
        x = (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * c1[0] + 3 * mt * (t ** 2) * c2[0] + (t ** 3) * p1[0]
        y = (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * c1[1] + 3 * mt * (t ** 2) * c2[1] + (t ** 3) * p1[1]
        out.append((x, y))
    return out


def build_shield():
    """A modern shield: flat top with rounded shoulders, tapering to a soft point."""
    left, right = 104.0, 408.0
    top, shoulder, waist, tip = 116.0, 148.0, 288.0, 430.0
    r = 34.0
    pts = [(left + r, top)]
    pts += cubic((left + r, top), (left + r * 0.35, top), (left, top + r * 0.35), (left, top + r))
    pts += [(left, waist)]
    pts += cubic((left, waist), (left, 348), (168, 396), (256, tip))[1:]
    pts += cubic((256, tip), (344, 396), (right, 348), (right, waist))[1:]
    pts += [(right, top + r)]
    pts += cubic((right, top + r), (right, top + r * 0.35), (right - r * 0.35, top), (right - r, top))[1:]
    return pts


def build_bolt():
    """Lightning bolt with softly cut terminals."""
    pts = [(292.0, 168.0)]
    pts += cubic((292, 168), (250, 218), (208, 268), (172, 306), steps=16)[1:]
    pts += [(172, 306), (250, 306)]
    pts += cubic((250, 306), (246, 334), (238, 366), (230, 392), steps=16)[1:]
    pts += cubic((230, 392), (272, 342), (316, 292), (352, 254), steps=16)[1:]
    pts += [(352, 254), (274, 254)]
    pts += cubic((274, 254), (282, 224), (288, 194), (292, 168), steps=16)[1:]
    return pts


SHIELD = build_shield()
BOLT = build_bolt()


def scale(points, factor: float, offset=(0.0, 0.0)):
    return [(x * factor + offset[0], y * factor + offset[1]) for x, y in points]


def draw_mark(size: int, *, tile: bool = True, radius_ratio: float = 0.235,
              safe: float = 1.0, bg: Image.Image | None = None) -> Image.Image:
    """Render the mark at `size` px."""
    s = size * SS
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    if tile:
        grad = gradient(s)
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle(
            (0, 0, s - 1, s - 1), radius=int(s * radius_ratio), fill=255
        )
        canvas.paste(grad, (0, 0), mask)
    elif bg is not None:
        canvas.paste(bg.resize((s, s), Image.LANCZOS), (0, 0))

    # White shield with the bolt knocked out (even-odd behaviour via masks).
    shield_mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(shield_mask)
    f = (s / 512.0) * safe
    off = s * (1 - safe) / 2.0
    d.polygon(scale(SHIELD, f, (off, off)), fill=255)
    bolt_mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(bolt_mask).polygon(scale(BOLT, f, (off, off)), fill=255)
    shield_mask = Image.composite(Image.new("L", (s, s), 0), shield_mask, bolt_mask)

    white = Image.new("RGBA", (s, s), WHITE + (255,))
    canvas.paste(white, (0, 0), shield_mask)

    return canvas.resize((size, size), Image.LANCZOS)


def shield_only(size: int, color) -> Image.Image:
    """Solid monochrome mark (for the notification / status bar icon)."""
    s = size * SS
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    f = s / 512.0
    d.polygon(scale(SHIELD, f), fill=255)
    d.polygon(scale(BOLT, f), fill=0)
    canvas.paste(Image.new("RGBA", (s, s), tuple(color) + (255,)), (0, 0), mask)
    return canvas.resize((size, size), Image.LANCZOS)


def save(img: Image.Image, *parts: str) -> None:
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path, REPO))


# density -> launcher icon size, foreground size, notification size
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

if __name__ == "__main__":
    # Marketing / preview assets
    save(draw_mark(1024), ROOT, "rx-client-logo.png")
    save(shield_only(1024, WHITE), ROOT, "rx-client-mark-mono.png")

    # Play Store icon
    save(draw_mark(512), ROOT, "rx-client-playstore-512.png")

    # Feature graphic / banner (Play Store 1024x500)
    banner = Image.new("RGB", (1024, 500), INK)
    bgrad = gradient(1024).resize((1024, 500), Image.LANCZOS)
    banner.paste(bgrad, (0, 0))
    shade = Image.new("RGBA", (1024, 500), (0, 0, 0, 0))
    ImageDraw.Draw(shade).rectangle((0, 0, 1023, 499), fill=(8, 8, 16, 96))
    banner = Image.alpha_composite(banner.convert("RGBA"), shade)
    mark = draw_mark(300)
    banner.paste(mark, (96, 100), mark)
    d = ImageDraw.Draw(banner)
    try:
        bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 74)
        light = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 31)
    except OSError:
        bold = light = ImageFont.load_default()
    d.text((446, 188), "RX CLIENT", font=bold, fill=WHITE)
    d.text((450, 288), "Fast  •  Stable  •  Every network", font=light, fill=(228, 224, 255))
    save(banner.convert("RGB"), ROOT, "rx-client-feature-graphic.png")

    for density, size in DENSITIES.items():
        out = os.path.join(RES, f"mipmap-{density}")
        save(draw_mark(size), out, "ic_launcher.png")
        save(draw_mark(size, radius_ratio=0.5), out, "ic_launcher_round.png")
        # adaptive-icon foreground fallback: 108dp canvas, mark inside the safe zone
        fg = size * 108 // 48
        save(draw_mark(fg, tile=False, safe=0.62), out, "ic_launcher_foreground.png")

        # Plain (non-adaptive) brand tile used inside the app UI (navigation drawer).
        save(draw_mark(size * 2), os.path.join(RES, f"drawable-{density}"), "ic_brand_logo.png")

        stat = max(24, size // 2)
        save(shield_only(stat, WHITE), os.path.join(RES, f"drawable-{density}"), "ic_stat_name.png")
        save(shield_only(stat, (0, 0, 0)), os.path.join(RES, f"drawable-{density}"), "ic_stat_name_black.png")

    # TV / leanback banner (320x180 dp rendered at 2x) and its adaptive foreground,
    # which must stay transparent so the violet background colour shows through.
    tv = Image.new("RGBA", (640, 360), (0, 0, 0, 255))
    tv.paste(gradient(640).resize((640, 360), Image.LANCZOS).convert("RGBA"), (0, 0))
    tv.paste(draw_mark(240), (200, 60), draw_mark(240))
    save(tv, os.path.join(RES, "mipmap-xhdpi"), "ic_banner.png")
    save(draw_mark(300, tile=False, safe=0.9), os.path.join(RES, "mipmap-xhdpi"), "ic_banner_foreground.png")
