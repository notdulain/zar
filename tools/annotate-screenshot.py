#!/usr/bin/env python3
"""Draw numbered red circles on an app screenshot.

Usage:
  python3 tools/annotate-screenshot.py IN.png OUT.png X,Y [X,Y ...] [--width 720]

Coordinates are pixels of the input image. Circles are numbered 1, 2, 3 in the
order given. The output is scaled to --width pixels wide (default 720).
"""
import sys

from PIL import Image, ImageDraw, ImageFont

RED = (214, 40, 40)


def font(size):
    for path in ("/System/Library/Fonts/Helvetica.ttc", "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
                 "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def annotate(src, dst, points, width=720):
    image = Image.open(src).convert("RGB")
    draw = ImageDraw.Draw(image)
    radius = max(image.width // 34, 14)
    label_font = font(int(radius * 1.2))
    for number, (x, y) in enumerate(points, start=1):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=RED, outline="white", width=4)
        draw.text((x, y), str(number), fill="white", font=label_font, anchor="mm")
    if width and width < image.width:
        image = image.resize((width, round(image.height * width / image.width)), Image.LANCZOS)
    image.save(dst, optimize=True)


def main(argv):
    width = 720
    if "--width" in argv:
        i = argv.index("--width")
        width = int(argv[i + 1])
        del argv[i:i + 2]
    if len(argv) < 4:
        print(__doc__)
        return 1
    points = [tuple(int(v) for v in p.split(",")) for p in argv[3:]]
    annotate(argv[1], argv[2], points, width)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
