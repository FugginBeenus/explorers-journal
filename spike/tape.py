"""Paper tape: the old strips, reworked so they stand out from the page. Darker than the paper, a little see-through, torn ends, and a thin shadow under them."""
import numpy as np
from PIL import Image

TOP, BODY, LOW = (0xE2, 0xCE, 0xA0), (0xCE, 0xB2, 0x7E), (0xA6, 0x8A, 0x5C)
# torn ends, hand placed: how many pixels each row loses at the end. Three shapes for each height.
TORN = {5: [[1, 0, 2, 0, 1], [0, 2, 0, 1, 0], [2, 0, 1, 2, 0]],
        6: [[1, 0, 2, 0, 1, 0], [0, 2, 0, 1, 0, 2], [2, 0, 1, 2, 0, 1]]}
ALPHA = 216

def strip(g, x, y, length, rise=0, h=5, ends=(0, 1), crease=None):
    """Lay one strip on g. rise: how many pixels it climbs or drops over its length. Every pixel stays on the grid."""
    L, R = TORN[h][ends[0] % 3], TORN[h][ends[1] % 3]
    W, H = g.size
    lay = np.zeros((H, W, 4), np.uint8)
    for i in range(length):
        yy = y + (round(i * rise / max(1, length - 1)) if rise else 0)
        for j in range(h):
            if i < L[j] or i >= length - R[j]:
                continue
            c = TOP if j == 0 else (LOW if j == h - 1 else BODY)
            if crease is not None and i == crease and 0 < j < h - 1:
                c = TOP
            px, py = x + i, yy + j
            if 0 <= px < W and 0 <= py < H:
                lay[py, px] = c + (ALPHA,)
        # a thin shadow under the strip
        py = yy + h
        if not (i < L[h - 1] or i >= length - R[h - 1]) and 0 <= py < H and 0 <= x + i < W:
            lay[py, x + i] = (0x5A, 0x44, 0x28, 46)
    g.alpha_composite(Image.fromarray(lay, 'RGBA'))
