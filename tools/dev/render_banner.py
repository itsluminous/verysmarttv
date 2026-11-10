#!/usr/bin/env python3
"""Render a companion's TV-banner wordmark to a PNG (docs/BUILDING.md, "Companion banners").

Android drawables cannot draw text, so each companion's banner is a layer-list (res/drawable/banner.xml): the icon
and the slate as a vector, and the app's name as this bitmap, res/mipmap-xhdpi/banner.png, 320x180 px, which the
layer-list stretches over the 320x180 dp banner (1 px here is 1 dp there). The text is white, Outfit at weight 500
(the launcher's font_medium, launcher/src/main/res/font/outfit.ttf), 28 px high, centred, letter-spaced 2 %,
on a transparent canvas; nothing else is in the PNG, so the vector stays the one place the artwork lives.

    python3 tools/dev/render_banner.py --text Trakt     --out traktshelf/src/main/res/mipmap-xhdpi/banner.png
    python3 tools/dev/render_banner.py --text LiveScore --out livescore/src/main/res/mipmap-xhdpi/banner.png
    python3 tools/dev/render_banner.py --text Trakt     --out traktshelf/src/main/res/mipmap-xhdpi/banner.png --check

Needs Pillow (pip install pillow). --check renders and compares to the committed file instead of writing, exit 1
when they differ, so a change to this script or the font shows up as a stale PNG.
"""
import argparse
import io
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 320, 180
REPO = Path(__file__).resolve().parents[2]
FONT = REPO / "launcher/src/main/res/font/outfit.ttf"


def render(text: str, size: int, weight: int, centre_y: int, tracking: float, font_path: Path) -> Image.Image:
    font = ImageFont.truetype(str(font_path), size)
    try:
        font.set_variation_by_axes([weight])   # Outfit is a variable font: one axis, wght
    except OSError:
        pass   # a static font: its own weight
    gap = size * tracking
    advances = [font.getlength(ch) for ch in text]
    total = sum(advances) + gap * (len(text) - 1)
    image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # Centre the cap height, not the bounding box with descenders, so "Trakt" and "LiveScore" sit at one height.
    ascent, _ = font.getmetrics()
    cap_top = font.getbbox("H")[1]
    cap_height = font.getbbox("H")[3] - cap_top
    y = centre_y - cap_height / 2 - cap_top
    x = (WIDTH - total) / 2
    for ch, advance in zip(text, advances):
        draw.text((x, y), ch, font=font, fill=(255, 255, 255, 255))
        x += advance + gap
    return image


def png_bytes(image: Image.Image) -> bytes:
    out = io.BytesIO()
    image.save(out, format="PNG", optimize=True)
    return out.getvalue()


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--text", required=True, help="the wordmark, e.g. Trakt or LiveScore")
    p.add_argument("--out", required=True, type=Path, help="the PNG to write (res/mipmap-xhdpi/banner.png)")
    p.add_argument("--size", type=int, default=28, help="text height in px (= dp on the banner); default 28")
    p.add_argument("--weight", type=int, default=500, help="font weight on the wght axis; default 500")
    p.add_argument("--centre-y", type=int, default=146, help="the cap height's centre line; default 146, under a 90 dp icon")
    p.add_argument("--tracking", type=float, default=0.02, help="letter spacing as a fraction of the size; default 0.02")
    p.add_argument("--font", type=Path, default=FONT, help=f"the TTF; default {FONT.relative_to(REPO)}")
    p.add_argument("--check", action="store_true", help="compare with the file at --out instead of writing it")
    args = p.parse_args()
    data = png_bytes(render(args.text, args.size, args.weight, args.centre_y, args.tracking, args.font))
    if args.check:
        current = args.out.read_bytes() if args.out.is_file() else b""
        if current != data:
            print(f"{args.out}: stale; run without --check to rewrite it", file=sys.stderr)
            return 1
        print(f"{args.out}: up to date")
        return 0
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(data)
    print(f"wrote {args.out} ({len(data)} bytes, {WIDTH}x{HEIGHT})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
