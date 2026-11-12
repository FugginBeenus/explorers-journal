"""Reads the folded corners off the page art, so the writing can step around them.

    python3 scripts/corners.py           prints the tables for client/Corners.java
    python3 scripts/corners.py --check   says if Corners.java and the forward arrow still match the art

Run it again after the page art changes. It needs Pillow, the same as spike/.
"""
import os
import re
import sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
BOOK = os.path.join(ROOT, "src/main/resources/assets/explorersjournal/textures/gui/book")
JAVA = os.path.join(ROOT, "src/main/java/com/fugginbeenus/explorersjournal/client")
ART = 12                     # the art sheet starts this far up and left of the book
LEFT, RIGHT, TOP, PAGE_W = 17, 166, 16, 129
TOP_ROWS = range(-4, 13)     # page rows a fold at the top can reach
BOTTOM_ROWS = range(140, 164)
ARROW_Y, ARROW_W, ARROW_H = 173, 23, 13

pages = [Image.open(os.path.join(BOOK, f"stage{i}.png")).convert("RGBA") for i in range(5)]


def changed(stage, x, y):
    """True where this stage's page differs from the clean page of stage 0."""
    a, b = pages[stage].getpixel((x + ART, y + ART)), pages[0].getpixel((x + ART, y + ART))
    return a[3] != b[3] or sum(abs(a[i] - b[i]) for i in range(3)) > 24


def top_left(stage):
    """For each row, how many pixels the fold covers from the left edge of a left page."""
    out = []
    for row in TOP_ROWS:
        xs = [x for x in range(10, 70) if changed(stage, x, TOP + row)]
        out.append(max(0, max(xs) - LEFT + 1) if xs else 0)
    return out


def bottom_right(stage):
    """For each row, the first pixel of a right page that the fold covers. PAGE_W where there is none."""
    out = []
    for row in BOTTOM_ROWS:
        xs = [x for x in range(230, 300) if changed(stage, x, TOP + row)]
        start = min(xs) - RIGHT if xs else PAGE_W
        # the page's own uneven edge is not a fold
        out.append(PAGE_W if start > PAGE_W - 5 else start)
    return out


def arrow_touches(stage, x):
    return sum(1 for dy in range(ARROW_H) for dx in range(ARROW_W) if changed(stage, x + dx, ARROW_Y + dy))


def java(rows):
    return "{" + ", ".join(str(v) for v in rows) + "}"


def tables():
    return {f"TOP_LEFT_{s}": top_left(s) for s in (3, 4)} | {f"BOTTOM_RIGHT_{s}": bottom_right(s) for s in (3, 4)}


def check():
    source = open(os.path.join(JAVA, "Corners.java")).read()
    ok = True
    for name, rows in tables().items():
        found = re.search(name + r" = \{([^}]*)\}", source)
        have = [int(v) for v in found.group(1).split(",")] if found else None
        if have != rows:
            ok = False
            print(f"{name} does not match the art.\n  art:  {java(rows)}\n  java: {have}")
    screen = open(os.path.join(JAVA, "JournalScreen.java")).read()
    xs = [int(v) for v in re.search(r"FORWARD_ARROW_X = \{([^}]*)\}", screen).group(1).split(",")]
    for stage, x in enumerate(xs):
        n = arrow_touches(stage, x)
        if n:
            ok = False
            print(f"stage {stage}: the forward arrow at x {x} lies on {n} pixels of the corner")
    print("The folds and the forward arrow match the art." if ok else "Fix the numbers above.")
    return ok


if __name__ == "__main__":
    if "--check" in sys.argv:
        sys.exit(0 if check() else 1)
    print(f"// rows {TOP_ROWS.start} to {TOP_ROWS.stop - 1} of a left page")
    for s in (3, 4):
        print(f"private static final int[] TOP_LEFT_{s} = {java(top_left(s))};")
    print(f"// rows {BOTTOM_ROWS.start} to {BOTTOM_ROWS.stop - 1} of a right page")
    for s in (3, 4):
        print(f"private static final int[] BOTTOM_RIGHT_{s} = {java(bottom_right(s))};")
