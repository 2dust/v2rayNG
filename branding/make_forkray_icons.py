#!/usr/bin/env python3
"""Generate Forkray's F icons from upstream v2rayNG's V icons.

The F keeps the V logo's stem and top-left flag, and replaces the diagonal with
two arms. Every slanted cut of the F (both arm ends and the foot of the stem)
lies on the V's outer diagonal edge, so the F is the V's own silhouette with
two notches taken out.

Each output is derived from the upstream image it replaces: the V is located in
it, erased to the background, and the F drawn over the same box at the same
scale. Everything else in the image (card, shadow, sizes) is left as upstream
drew it. Outputs go to the fdroid flavor's resources, which override the main
ones, so upstream's files stay untouched and upstream merges never conflict.

The store icon in fastlane/ is itself replaced by Forkray's, so its upstream
original is read from git, at UPSTREAM_REF (default: upstream/master).

Requires Pillow and a fetched `upstream` remote. Run from the repository root:

    python3 branding/make_forkray_icons.py
"""

import io
import os
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
MAIN_RES = ROOT / "V2rayNG/app/src/main/res"
FDROID_RES = ROOT / "V2rayNG/app/src/fdroid/res"
STORE_ICON = "fastlane/metadata/android/en-US/images/icon.png"
UPSTREAM_REF = os.environ.get("UPSTREAM_REF", "upstream/master")
DENSITIES = ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]

# Coordinates on upstream's 432 px adaptive-icon foreground
# (mipmap-xxxhdpi/ic_launcher_foreground.png), measured from the V:
#   stem x 156..184, flag x 137..156 over y 139..158, glyph top y 139,
#   outer diagonal edge x = 434.9 - 0.94 * y, reaching the stem at y ~296.7.
V_BOX = (137.0, 139.0, 304.2, 296.7)


def edge(y: float) -> float:
    """x of the V's outer diagonal edge at height y."""
    return 434.9 - 0.94 * y


ARM = 32.0          # arm thickness, close to the V diagonal's stroke weight
STEM_RIGHT = 184.0
TOP = 139.0
MIDDLE_TOP = 206.0

F_POLYGON = [
    (137.0, TOP),                                    # flag, top left
    (edge(TOP), TOP),                                # top arm, on the edge
    (edge(TOP + ARM), TOP + ARM),
    (STEM_RIGHT, TOP + ARM),
    (STEM_RIGHT, MIDDLE_TOP),                        # middle arm, on the edge
    (edge(MIDDLE_TOP), MIDDLE_TOP),
    (edge(MIDDLE_TOP + ARM), MIDDLE_TOP + ARM),
    (STEM_RIGHT, MIDDLE_TOP + ARM),
    (STEM_RIGHT, (434.9 - STEM_RIGHT) / 0.94),       # foot of the stem, on the edge
    (156.0, 296.7),                                  # the V's bottom tip
    (156.0, 158.0),                                  # flag, bottom
    (137.0, 158.0),
]

SUPERSAMPLE = 8


def glyph_mask(size: tuple[int, int], box: tuple[float, float, float, float]) -> Image.Image:
    """Antialiased F mask of `size`, with the F placed where the V's box is."""
    sx = (box[2] - box[0]) / (V_BOX[2] - V_BOX[0])
    sy = (box[3] - box[1]) / (V_BOX[3] - V_BOX[1])
    points = [
        ((box[0] + (x - V_BOX[0]) * sx) * SUPERSAMPLE, (box[1] + (y - V_BOX[1]) * sy) * SUPERSAMPLE)
        for x, y in F_POLYGON
    ]
    big = Image.new("L", (size[0] * SUPERSAMPLE, size[1] * SUPERSAMPLE), 0)
    ImageDraw.Draw(big).polygon(points, fill=255)
    return big.resize(size, Image.Resampling.LANCZOS)


def glyph_box(image: Image.Image, is_glyph) -> tuple[float, float, float, float]:
    """Bounding box of the V: the connected run of `is_glyph` pixels nearest
    the centre. Taking only that component matters on the card icons, whose
    drop shadows also contain dark pixels that are not part of the V."""
    px = image.load()
    w, h = image.size
    cx, cy = w // 2, h // 2
    seed = min(
        ((x, y) for y in range(h) for x in range(w) if is_glyph(px[x, y])),
        key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2,
    )
    seen = {seed}
    stack = [seed]
    while stack:
        x, y = stack.pop()
        for n in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= n[0] < w and 0 <= n[1] < h and n not in seen and is_glyph(px[n[0], n[1]]):
                seen.add(n)
                stack.append(n)
    xs = [p[0] for p in seen]
    ys = [p[1] for p in seen]
    # Pixel edges, not centres: the box runs to the far side of the last pixel.
    return (min(xs), min(ys), max(xs) + 1, max(ys) + 1)


def dark(p) -> bool:
    return p[3] > 128 and sum(p[:3]) < 3 * 128


def opaque(p) -> bool:
    return p[3] > 128


def upstream_file(path: str) -> io.BytesIO:
    """A file as upstream has it, for images this fork has replaced."""
    blob = subprocess.run(
        ["git", "-C", str(ROOT), "show", f"{UPSTREAM_REF}:{path}"],
        check=True, capture_output=True,
    ).stdout
    return io.BytesIO(blob)


def recolor_on_card(src, dst: Path) -> None:
    """Black glyph on a light card (launcher icons, store icon)."""
    image = Image.open(src).convert("RGBA")
    box = glyph_box(image, dark)
    px = image.load()
    card = px[max(int(box[0]) - 6, 0), int((box[1] + box[3]) / 2)]
    pad = 3
    for y in range(max(int(box[1]) - pad, 0), min(int(box[3]) + pad, image.height)):
        for x in range(max(int(box[0]) - pad, 0), min(int(box[2]) + pad, image.width)):
            if px[x, y][3] > 0 and sum(px[x, y][:3]) < sum(card[:3]) - 6:
                px[x, y] = card
    ink = Image.new("RGBA", image.size, (0, 0, 0, 255))
    image.paste(ink, mask=glyph_mask(image.size, box))
    save(image, dst)


def redraw_on_transparent(src: Path, dst: Path, colour) -> None:
    """Glyph alone on transparency (adaptive foreground, status bar icon)."""
    original = Image.open(src).convert("RGBA")
    box = glyph_box(original, opaque)
    image = Image.new("RGBA", original.size, (0, 0, 0, 0))
    image.paste(Image.new("RGBA", original.size, colour), mask=glyph_mask(original.size, box))
    save(image, dst)


def save(image: Image.Image, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    image.save(dst, optimize=True)
    print(dst.relative_to(ROOT))


def main() -> None:
    for d in DENSITIES:
        redraw_on_transparent(
            MAIN_RES / f"mipmap-{d}/ic_launcher_foreground.png",
            FDROID_RES / f"mipmap-{d}/ic_launcher_foreground.png",
            (0, 0, 0, 255),
        )
        for name in ("ic_launcher", "ic_launcher_round"):
            src = MAIN_RES / f"mipmap-{d}/{name}.png"
            if src.exists():
                recolor_on_card(src, FDROID_RES / f"mipmap-{d}/{name}.png")
        redraw_on_transparent(
            MAIN_RES / f"drawable-{d}/ic_stat_name.png",
            FDROID_RES / f"drawable-{d}/ic_stat_name.png",
            (255, 255, 255, 255),
        )
    recolor_on_card(upstream_file(STORE_ICON), ROOT / STORE_ICON)


if __name__ == "__main__":
    main()
