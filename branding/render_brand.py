#!/usr/bin/env python3
"""Render RX VPN's geometric monogram and Android brand assets.

Requires Pillow. The monogram is drawn from geometry, not a font, so launcher
icons remain reproducible without installing a proprietary typeface.
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(ROOT)
RES = os.path.join(REPO, "V2rayNG", "app", "src", "main", "res")
GRAPHITE = (23, 25, 27)
GRAPHITE_LIGHT = (48, 51, 54)
AMBER = (232, 183, 120)
IVORY = (244, 242, 237)
SS = 4

# Coordinates in a 512-unit square. Keep these in sync with ic_brand_mark.xml.
R_OUTER = [(100, 156), (192, 156), (220, 162), (238, 178), (244, 202),
           (238, 228), (216, 248), (254, 356), (204, 356), (168, 260),
           (146, 260), (146, 356), (100, 356)]
R_INNER = [(146, 196), (188, 196), (198, 200), (202, 208),
           (198, 218), (188, 222), (146, 222)]
X_SHAPE = [(266, 156), (316, 156), (342, 210), (370, 156), (420, 156),
           (368, 252), (424, 356), (372, 356), (342, 296), (312, 356),
           (260, 356), (316, 252)]


def scaled(points, size, safe=1.0):
    factor = size / 512 * safe
    offset = size * (1 - safe) / 2
    return [(x * factor + offset, y * factor + offset) for x, y in points]


def draw_monogram(size, *, mono=None, safe=1.0):
    image = Image.new("RGBA", (size, size))
    mask = Image.new("L", (size, size))
    draw = ImageDraw.Draw(mask)
    draw.polygon(scaled(R_OUTER, size, safe), fill=255)
    draw.polygon(scaled(R_INNER, size, safe), fill=0)
    image.paste(tuple(mono or IVORY) + (255,), (0, 0), mask)
    x_mask = Image.new("L", (size, size))
    ImageDraw.Draw(x_mask).polygon(scaled(X_SHAPE, size, safe), fill=255)
    image.paste(tuple(mono or AMBER) + (255,), (0, 0), x_mask)
    return image


def monogram(size, *, mono=None, safe=1.0):
    image = draw_monogram(size * SS, mono=mono, safe=safe)
    return image.resize((size, size), Image.LANCZOS)


def tile(size, *, radius_ratio=0.235, mark_safe=0.82):
    pixels = size * SS
    image = Image.new("RGBA", (pixels, pixels))
    draw = ImageDraw.Draw(image)
    for y in range(pixels):
        t = y / max(1, pixels - 1)
        color = tuple(round(a + (b - a) * t) for a, b in zip(GRAPHITE_LIGHT, GRAPHITE))
        draw.line((0, y, pixels, y), fill=color + (255,))
    mask = Image.new("L", (pixels, pixels))
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, pixels - 1, pixels - 1), radius=int(pixels * radius_ratio), fill=255)
    image.putalpha(mask)
    mark = draw_monogram(pixels, safe=mark_safe)
    image.alpha_composite(mark)
    return image.resize((size, size), Image.LANCZOS)


def save(image, *parts):
    path = os.path.join(*parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("wrote", os.path.relpath(path, REPO))


def main():
    save(tile(1024), ROOT, "rx-vpn-logo.png")
    save(monogram(1024, mono=(255, 255, 255)), ROOT, "rx-vpn-mark-mono.png")
    save(tile(512), ROOT, "rx-vpn-playstore-512.png")

    banner = Image.new("RGB", (1024, 500), GRAPHITE)
    draw = ImageDraw.Draw(banner)
    draw.rounded_rectangle((48, 48, 976, 452), radius=32, outline=(65, 67, 69), width=2)
    banner.paste(tile(248), (98, 126), tile(248))
    try:
        title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 66)
        subtitle = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 25)
    except OSError:
        title = subtitle = ImageFont.load_default()
    draw.text((405, 192), "RX VPN", font=title, fill=IVORY)
    draw.text((410, 282), "Your connection. Your choice.", font=subtitle, fill=AMBER)
    save(banner, ROOT, "rx-vpn-feature-graphic.png")

    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        out = os.path.join(RES, f"mipmap-{density}")
        save(tile(size), out, "ic_launcher.png")
        save(tile(size, radius_ratio=0.5), out, "ic_launcher_round.png")
        foreground = monogram(size * 108 // 48, safe=0.62)
        save(foreground, out, "ic_launcher_foreground.png")
        save(tile(size * 2), os.path.join(RES, f"drawable-{density}"), "ic_brand_logo.png")
        stat = max(24, size // 2)
        save(monogram(stat, mono=(255, 255, 255)), os.path.join(RES, f"drawable-{density}"), "ic_stat_name.png")
        save(monogram(stat, mono=(0, 0, 0)), os.path.join(RES, f"drawable-{density}"), "ic_stat_name_black.png")

    tv = Image.new("RGBA", (640, 360), GRAPHITE + (255,))
    mark = tile(228)
    tv.alpha_composite(mark, (206, 66))
    save(tv, os.path.join(RES, "mipmap-xhdpi"), "ic_banner.png")
    save(monogram(300, safe=0.9), os.path.join(RES, "mipmap-xhdpi"), "ic_banner_foreground.png")


if __name__ == "__main__":
    main()
