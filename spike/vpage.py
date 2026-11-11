"""The open journal built from vanilla's own book texture. Vanilla's page is the middle condition: new books are cleaner, old ones are worse."""
import math
import numpy as np
from scipy import ndimage as ndi
from PIL import Image

PX0, PX1, PY0, PY1 = 26, 159, 8, 173     # vanilla page area
SW, SH = 148, 180                        # one sheet in the open journal
EX = 9                                   # room past the sheet for a fat stack

def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)
# vanilla's own edge colours, dark to light
E0, E1, E2, E3, E4, E5 = rgb(0x94745A), rgb(0x9D7E67), rgb(0xA68B6E), rgb(0xB9A283), rgb(0xBFAA8C), rgb(0xC5B091)
E6, E7, E8, E9, EA, EB = rgb(0xCCB998), rgb(0xCEBC9B), rgb(0xD1BFA1), rgb(0xD5C3A4), rgb(0xE0D2AE), rgb(0xE7D9BA)
EC, ED, EE_, EF, EG, EH = rgb(0xECE0C1), rgb(0xF3E7C8), rgb(0xF9EED0), rgb(0xFBF4E0), rgb(0xFEF8EC), rgb(0xFFFAEE)
UNDER = rgb(0x4C1A0B)

def load(tex):
    return np.array(Image.open(tex + 'gui/book.png').convert('RGBA'))

def stretch(arr, axis, add, lo, hi):
    """Grow pixel art by repeating single rows or columns, spread out so nothing visibly tiles."""
    picks = np.linspace(lo, hi, add).astype(int)
    idx = []
    for i in range(arr.shape[axis]):
        idx.append(i)
        idx.extend([i] * int((picks == i).sum()))
    return np.take(arr, idx, axis)

def thread_mask(a):
    return (a[..., 0] >= 0x90) & (a[..., 1] < 0x40)

def page_base(a):
    """Vanilla's right page: gutter cleaned of stitches, stretched to the sheet size, page shape in alpha."""
    v = a.copy()
    tm = thread_mask(v)
    best = next(y for y in range(20, 160) if not tm[y - 1:y + 2, 26:38].any())
    tpl = v[best, 26:38].copy()
    for y in range(PY0, PY1 - 1):
        v[y, 26:38] = tpl
    v[PY1 - 1, 26:38] = v[PY1 - 1, 60]
    crop = v[PY0:PY1, PX0:PX1].copy()
    crop[..., 3] = np.where(crop[..., 0] >= 0x90, 255, 0)
    crop = stretch(crop, 1, SW - crop.shape[1], 16, 112)
    crop = stretch(crop, 0, SH - crop.shape[0], 16, 148)
    return crop

def thread_sprite(a):
    """One of vanilla's red stitches, cut out of the page."""
    tm = thread_mask(a)
    sub = a[28:37, 29:32]
    m = tm[28:37, 29:32]
    out = np.zeros_like(sub)
    out[m] = sub[m]
    return out

def cover_base(a):
    """Vanilla's cover, right half, under-page area filled dark."""
    rim = a[1:181, 26:166].copy()
    inner = np.zeros(rim.shape[:2], bool)
    inner[PY0 - 1:PY1 - 1, :PX1 - 26] = True
    rim[inner & (rim[..., 0] >= 0x90)] = UNDER
    rim[thread_mask(rim)] = UNDER
    for x in range(0, 12):
        rim[:7, x] = rim[:7, 12]
        rim[172:, x] = rim[172:, 12]
    rim = np.concatenate([rim[:4], rim[3:4], rim[4:]], 0)
    rim = stretch(rim, 1, 156 - rim.shape[1], 16, 112)
    rim = stretch(rim, 0, 196 - rim.shape[0], 24, 150)
    return rim

LEATHER_UP = {0x4C1A0B: 0x652816, 0x652816: 0x75321E, 0x5B2312: 0x75321E, 0x75321E: 0x87412C, 0x6D2D1A: 0x87412C, 0x87412C: 0x96543C, 0x823D28: 0x96543C, 0x7D3925: 0x96543C,
              0x96543C: 0xAA6C48, 0x1C0F00: 0x4C1A0B}

def rub(px, n=1):
    for _ in range(n):
        k = (int(px[0]) << 16) | (int(px[1]) << 8) | int(px[2])
        if k in LEATHER_UP:
            k = LEATHER_UP[k]
        else:
            # any other leather tone: step up a little
            k = (min(255, int(px[0]) + 22) << 16) | (min(255, int(px[1]) + 16) << 8) | min(255, int(px[2]) + 12)
        px = np.array(rgb(k), np.uint8)
    return px

def spread_cover(a, stage=2):
    r = cover_base(a)
    c = np.concatenate([r[:, ::-1], r], 1)
    H, W = c.shape[:2]
    # the spine shows at top and bottom
    cx = W // 2
    for y in list(range(1, 7)) + list(range(H - 7, H - 1)):
        c[y, cx - 3] = rgb(0x4C1A0B); c[y, cx + 2] = rgb(0x4C1A0B)
        c[y, cx - 2:cx + 2] = rgb(0x652816)
        c[y, cx - 1:cx + 1] = rgb(0x75321E)
    # rubbed corners and edges: the leather goes lighter where a hand or a pocket touches it
    reach = [0, 5, 9, 14, 20][stage]
    if reach:
        for (cxx, cyy, sx, sy) in [(0, 0, 1, 1), (W - 1, 0, -1, 1), (0, H - 1, 1, -1), (W - 1, H - 1, -1, -1)]:
            for k in range(reach + 8):
                for l in range(reach + 8):
                    s = k + l
                    if s > reach + 6: continue
                    if k > 6 and l > 6: continue
                    x, y = cxx + sx * k, cyy + sy * l
                    if not c[y, x, 3]: continue
                    band = min(k, l)
                    if band > 3: continue
                    steps = 2 if s < reach else 1
                    if stage < 3 and steps == 2: steps = 1
                    c[y, x] = rub(c[y, x], steps)
    runs = {3: [(40, 70, 'T'), (214, 240, 'B'), (58, 86, 'L'), (120, 150, 'R')],
            4: [(26, 74, 'T'), (182, 204, 'T'), (250, 296, 'B'), (70, 116, 'B'), (44, 80, 'L'), (120, 152, 'L'), (90, 132, 'R'), (150, 176, 'R')]}.get(stage, [])
    for a_, b_, e in runs:
        for t in range(a_, b_):
            for d in (1, 2):
                # taper the ends so a scuff never starts with a hard square
                if d == 2 and (t < a_ + 4 or t > b_ - 5): continue
                if e == 'T': x, y = t, d
                elif e == 'B': x, y = t, H - 1 - d
                elif e == 'L': x, y = d, t
                else: x, y = W - 1 - d, t
                c[y, x] = rub(c[y, x], 1)
    return c

def plain_paper(base):
    """Paper only: vanilla's soft tones, no edge art, no nicks."""
    p = base.copy()
    C0 = 136
    for x in range(C0, SW):
        p[:, x] = p[:, C0 - 1]
    for y in range(SH - 32, SH):
        p[y, 118:] = p[y, 117]
    for y in range(0, 18):
        p[y, 122:] = p[y, 121]
    for y in list(range(0, 4)) + list(range(SH - 5, SH - 1)):
        med = np.median(p[y, 20:110, :3], axis=0)
        bad = np.abs(p[y, :, :3].astype(int) - med).sum(1) > 60
        bad[:12] = False
        for x in np.where(bad)[0]:
            p[y, x] = (*med.astype(np.uint8), 255)
    p[..., 3] = 255
    return p

def mode_filter(strip, win):
    """Straighten page lines: each pixel takes the most common colour along its column."""
    h, w = strip.shape[:2]
    out = strip.copy()
    key = (strip[..., 0].astype(int) << 16) | (strip[..., 1].astype(int) << 8) | strip[..., 2]
    for x in range(w):
        for y in range(h):
            a, b = max(0, y - win), min(h, y + win + 1)
            vals, cnt = np.unique(key[a:b, x], return_counts=True)
            k = int(vals[cnt.argmax()])
            out[y, x] = ((k >> 16) & 255, (k >> 8) & 255, k & 255, 255)
    return out

def pad(p):
    out = np.zeros((SH, SW + EX, 4), np.uint8)
    out[:, :SW] = p
    return out

def clean_edge(p, cols):
    n = len(cols)
    for i, c in enumerate(cols):
        p[:, SW - n + i] = c
    return p

def round_corner(p, y_end, sy, r, right=SW):
    steps = {1: [1], 2: [2, 1], 3: [3, 1, 1], 4: [4, 2, 1, 1]}[r]
    for i, k in enumerate(steps):
        p[y_end + sy * i, right - k:right] = 0

def edge_columns(base):
    """Where the top sheet's own edge sits on each row of vanilla's page."""
    e = np.zeros(SH, int)
    for y in range(SH):
        e[y] = SW - 6
        for x in range(128, SW):
            if base[y, x, 0] < 0xE1 and base[y, x, 3]:
                e[y] = x; break
    return e

def clean_van(base, paper):
    """Vanilla's page with its corner art taken out: the edge lines run straight to the top and bottom, ready to be bent."""
    v = base.copy()
    n = 22
    v[:n, SW - 16:] = base[n:2 * n, SW - 16:][::-1]
    m = 32
    v[SH - 1 - m:SH - 1, SW - 16:] = base[SH - 1 - 2 * m:SH - 1 - m, SW - 16:][::-1]
    v[:n, :SW - 16] = paper[:n, :SW - 16]
    v[SH - 1 - m:SH - 1, :SW - 16] = paper[SH - 1 - m:SH - 1, :SW - 16]
    v[v[..., 3] == 0] = E1
    v[..., 3] = 255
    v[SH - 1] = E0
    return v

def wobble(paper, van, amp):
    """Vanilla's hand-cut edge, pushed further: same jogs, bigger."""
    e = edge_columns(van)
    e0 = int(np.median(e))
    out = pad(paper)
    for y in range(SH - 1):
        ey = e[y]
        ne = max(SW - 16, min(SW - 3, int(round(e0 + (ey - e0) * amp))))
        row = van[y, ey - 1:SW]
        new = np.zeros((SW, 4), np.uint8)
        new[:ne - 1] = paper[y, :ne - 1]
        n = SW - (ne - 1)
        if len(row) >= n:
            new[ne - 1:] = row[:n]
            new[SW - 1] = row[-1]
        else:
            new[ne - 1:ne - 1 + len(row)] = row
            fill = [EA, EE_, E6]
            for i in range(ne - 1 + len(row), SW):
                new[i] = fill[i % 3]
        out[y, :SW] = new
    out[SH - 1, :SW] = E0
    return out

ARCS = {2: [2, 1], 3: [3, 1, 1], 4: [4, 2, 1, 1], 5: [5, 3, 2, 1, 1], 6: [6, 4, 2, 2, 1, 1], 7: [7, 5, 3, 2, 2, 1, 1], 8: [8, 5, 4, 3, 2, 1, 1, 1],
        9: [9, 6, 5, 3, 3, 2, 1, 1, 1], 10: [10, 7, 5, 4, 3, 2, 2, 1, 1, 1], 12: [12, 9, 7, 5, 4, 3, 3, 2, 2, 1, 1, 1], 14: [14, 10, 8, 6, 5, 4, 3, 3, 2, 2, 1, 1, 1, 1]}

def bend(p, R, top=True):
    """Round a corner by bending every page line into it. The stack and the page turn the corner together, and each line ends at the page edge."""
    arr = p if top else p[::-1]
    arc = ARCS[R]
    start = 0 if top else 1      # the bottom row is the dark line under the page
    yb = start + R
    src = arr[yb].copy()
    op = np.where(src[:, 3] > 0)[0]
    last = int(op.max())
    first = SW - 16
    for x in range(SW - 16, last + 1):
        if src[x, 0] < 0xE1 or tuple(src[x]) == EH:
            first = x; break
    for i, dx in enumerate(arc):
        y = start + i
        arr[y, first - dx:] = 0
    for c in range(last, first - 1, -1):
        col = src[c].copy()
        if not col[3]: continue
        for i in range(R - 1, -1, -1):
            y = start + i
            x = c - arc[i]
            nxt = c - (arc[i + 1] if i + 1 < R else 0)
            arr[y, x:max(x, nxt) + 1] = col
    if not top:
        # the dark line follows the page's new outline
        row = arr[1]
        xs = np.where(row[:, 3] > 0)[0]
        arr[0] = 0
        arr[0, :xs.max()] = E0
    return p

def dogear(p, a_, b_, top=False, lean=(0, 0)):
    """A corner folded over onto the page. A crisp fold, a thin shade past it, the next page under it. a_: along the page edge, b_: up the outer edge."""
    arr = p[::-1].copy() if top else p
    bot = SH - 1 if not top else SH - 1
    H, W = arr.shape[:2]
    # the top sheet's edge, row by row, so the fold stays on the sheet
    start = np.full(H, SW - 8)
    for y in range(H):
        for x in range(SW - 22, min(W, SW + 1)):
            if arr[y, x, 3] and (arr[y, x, 0] < 0xE1 or tuple(arr[y, x]) == EH):
                start[y] = x; break
    right = int(start[bot - b_]) + 1
    x0, y0 = right, bot - b_
    x1, y1 = right - a_, bot
    nx, ny = (y1 - y0), -(x1 - x0)
    L = math.hypot(nx, ny); nx /= L; ny /= L
    cx, cy = right, bot
    if (cx - x0) * nx + (cy - y0) * ny < 0: nx, ny = -nx, -ny
    d0 = (cx - x0) * nx + (cy - y0) * ny
    tipx, tipy = cx - 2 * d0 * nx + lean[0], cy - 2 * d0 * ny + lean[1]
    out = arr.copy()
    # past the fold: the next page, in the shade of the fold
    for y in range(max(0, bot - b_ - 1), bot):
        for x in range(max(0, right - a_ - 2), int(start[y])):
            d = (x - x0) * nx + (y - y0) * ny
            if d < 0 or not arr[y, x, 3]: continue
            if d < 1: c = E3
            elif d < 2: c = E7
            elif d < 3.5: c = EA
            elif d < 6: c = EB
            else: c = EC
            out[y, x] = c
    def sign(ax, ay, bx, by, cx_, cy_): return (cx_ - bx) * (ay - by) - (ax - bx) * (cy_ - by)
    A, B, C = (x0, y0), (x1, y1), (tipx, tipy)
    mask = np.zeros(arr.shape[:2], bool)
    for y in range(max(0, int(min(y0, y1, tipy)) - 1), min(H, int(max(y0, y1, tipy)) + 2)):
        for x in range(max(0, int(min(x0, x1, tipx)) - 1), min(W, int(max(x0, x1, tipx)) + 2)):
            d1, d2, d3 = sign(x, y, *A, *B), sign(x, y, *B, *C), sign(x, y, *C, *A)
            if not ((d1 < 0 or d2 < 0 or d3 < 0) and (d1 > 0 or d2 > 0 or d3 > 0)):
                mask[y, x] = True
    edge = mask & ~ndi.binary_erosion(mask)
    # soft contact shade on the page beside the flap
    grow = ndi.binary_dilation(mask, structure=np.ones((3, 3), bool))
    for y, x in zip(*np.where(grow & ~mask)):
        d = (x - x0) * nx + (y - y0) * ny
        if d < 0 and out[y, x, 3] and x < start[y] and y < bot:
            out[y, x] = ED
    for y, x in zip(*np.where(mask)):
        d = (x - x0) * nx + (y - y0) * ny
        out[y, x] = EH if d > -2.2 else EG
    def seg_dist(px, py, ax, ay, bx, by):
        vx, vy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
        return math.hypot(px - ax - t * vx, py - ay - t * vy)
    for y, x in zip(*np.where(edge)):
        d = (x - x0) * nx + (y - y0) * ny
        if d > -2.5: continue
        # the upper free edge catches light, the steep one is darker
        up = seg_dist(x, y, x0, y0, tipx, tipy) < seg_dist(x, y, x1, y1, tipx, tipy)
        out[y, x] = EA if up else E8
    # no sharp point on the tip
    tx, ty = int(round(tipx)), int(round(tipy))
    if 0 <= ty < H and 0 <= tx < W:
        out[ty, tx] = arr[ty, tx]
    if top:
        p[:] = out[::-1]
    else:
        p[:] = out
    return p

def nick(p, x, depth, top=True):
    """Vanilla's little cut in the page edge, any depth: a slanting slit, tan on one side, a light edge on the other."""
    for i in range(depth):
        px = x + i * 2 // 3
        py = i if top else SH - 2 - i
        p[py, px] = 0 if i < max(1, depth // 3) else (E0 if i < depth - 1 else E6)
        p[py, px - 1] = E6
        if i < depth - 1: p[py, px + 1] = EH
    if not top:
        p[SH - 1, x] = 0
    return p

def bite(p, x0, shape, top=True):
    """A piece torn out of the edge. shape: depth at each column, hand placed."""
    for i, d in enumerate(shape):
        x = x0 + i
        for k in range(d):
            y = k if top else SH - 1 - k
            p[y, x] = EA if k else E6
        y = d if top else SH - 1 - d
        if d: p[y, x] = EH
    return p

def side_bite(p, y0, shape, right=SW):
    """A piece torn out of the outer edge."""
    for i, d in enumerate(shape):
        y = y0 + i
        for k in range(d):
            p[y, right - 1 - k] = EA if k % 2 else E6
        if d: p[y, right - 1 - d] = EH
    return p

RUNS = {
    # bunches of pages behind the top sheet: rows, and how far each bunch sticks out. Hand placed.
    (3, 'R'): [(0, 16, 1), (16, 31, 1), (31, 46, 3), (46, 80, 2), (80, 92, 3), (92, 122, 1), (122, 151, 2), (151, 180, 1)],
    (3, 'L'): [(0, 22, 1), (22, 40, 2), (40, 69, 1), (69, 88, 3), (88, 118, 2), (118, 134, 3), (134, 180, 1)],
    (4, 'R'): [(0, 22, 2), (22, 38, 4), (38, 53, 6), (53, 68, 4), (68, 91, 3), (91, 104, 5), (104, 124, 6), (124, 139, 4), (139, 158, 5), (158, 180, 3)],
    (4, 'L'): [(0, 24, 3), (24, 44, 5), (44, 61, 3), (61, 80, 6), (80, 110, 4), (110, 122, 2), (122, 146, 5), (146, 180, 2)],
}

def bunches(p, runs):
    """More pages behind the top sheet, in bunches. Each bunch is a few straight lines with a capped end."""
    for y in range(SH):
        if not p[y, SW - 1, 3]:
            p[y, SW - 1] = E1
    w_at = np.zeros(SH + 1, int)
    for a, b, w in runs:
        w_at[a:b] = w
    for a, b, w in runs:
        for y in range(a, b):
            for j in range(w):
                c = EA if j % 3 == 0 else (EE_ if j % 3 == 1 else E6)
                if j == w - 1: c = E1
                p[y, SW + j] = c
    for a, b, w in runs:
        prev = w_at[a - 1] if a > 0 else w
        nxt = w_at[b] if b < SH else w
        if w > prev:
            for j in range(prev, w):
                p[a, SW + j] = E6
            p[a, SW + w - 1] = 0
            if w - 2 >= prev: p[a + 1, SW + w - 1] = E6
        if w > nxt:
            for j in range(nxt, w):
                p[b - 1, SW + j] = E3
            p[b - 1, SW + w - 1] = 0
    return p

def shade_stack(p):
    """The shadow the stack throws on the leather, after the corners are bent."""
    for y in range(SH):
        xs = np.where(p[y, SW - 16:, 3] > 0)[0]
        if xs.size:
            e = SW - 16 + xs.max() + 1
            if e < p.shape[1] and e >= SW:
                p[y, e] = UNDER
    return p

def loose_page(base):
    """A whole page that came out of line: plain paper and simple edge lines. Its corners are soft, with no fold art, so only clean shapes show past the book."""
    p = plain_paper(base)
    clean_edge(p, [EH, E8, ED, EA, E1])
    p[SH - 1] = E1
    p[SH - 2] = EA
    round_corner(p, 0, 1, 3)
    round_corner(p, SH - 1, -1, 3)
    return p

def sheet(base, stage, side='R'):
    """One visible page and the stack behind it, by condition. Returns SH x (SW + EX) RGBA, gutter at x=0."""
    flip = side == 'L'
    paper = plain_paper(base)
    van = clean_van(base, paper)
    if flip:
        paper[:SH - 1] = paper[:SH - 1][::-1]
        van[:SH - 1, SW - 16:] = van[:SH - 1, SW - 16:][::-1]
        van[:SH - 1, :SW - 16] = paper[:SH - 1, :SW - 16]
    if stage == 0:
        # cleaner than vanilla: straight lines, a small soft corner, no folds
        p = pad(paper)
        clean_edge(p, [EH, E8, ED, EA, E1])
        p[SH - 1, :SW] = E0
        bend(p, 2, True); bend(p, 2, False)
        return shade_stack(p)
    if stage == 1:
        # nearly new: vanilla's lines mostly straightened, soft corners
        p = pad(paper)
        strip = van[:, SW - 7:SW].copy()
        strip[..., 3] = 255
        p[:, SW - 7:SW] = mode_filter(strip, 16)
        p[:, SW - 1] = E1
        p[SH - 1, :SW] = E0
        bend(p, 2, True); bend(p, 3 if not flip else 2, False)
        if not flip: nick(p, 96, 2)
        return shade_stack(p)
    if stage == 2:
        if not flip:
            # exactly vanilla's page
            return pad(base.copy())
        p = pad(van)
        bend(p, 3, True); bend(p, 3, False)
        nick(p, 41, 3)
        return shade_stack(p)
    amp = 1.6 if stage == 3 else 2.3
    p = wobble(paper, van, amp)
    bunches(p, RUNS[(stage, side)])
    if stage == 3:
        if flip:
            bend(p, 3, True); bend(p, 3, False)
            dogear(p, 11, 14, top=True)
            nick(p, 54, 4); nick(p, 110, 3, top=False)
            bite(p, 88, [1, 2, 2, 1], top=True)
        else:
            bend(p, 3, True); bend(p, 3, False)
            dogear(p, 13, 17)
            nick(p, 92, 4); nick(p, 34, 4, top=False)
            side_bite(p, 62, [1, 2, 2, 3, 2, 1])
        return shade_stack(p)
    if flip:
        bend(p, 4, True); bend(p, 3, False)
        dogear(p, 12, 16, top=True)
        nick(p, 31, 5); nick(p, 76, 3); nick(p, 104, 6, top=False)
        bite(p, 58, [1, 2, 3, 3, 2, 2, 1], top=False)
        side_bite(p, 96, [1, 2, 3, 4, 4, 3, 2, 2, 1])
    else:
        bend(p, 4, True); bend(p, 4, False)
        dogear(p, 15, 21)
        nick(p, 88, 6); nick(p, 120, 3); nick(p, 44, 5, top=False)
        bite(p, 22, [1, 2, 2, 3, 2, 1], top=True)
        side_bite(p, 74, [1, 2, 4, 4, 3, 3, 2, 1])
    return shade_stack(p)

def askew(sheet_arr, deg, side='R', pivot_y=SH // 2):
    """A whole page, turned a little around where it is bound. Sliding whole rows and columns keeps every pixel on the grid."""
    h, w = sheet_arr.shape[:2]
    k = math.tan(math.radians(deg))
    pad_ = 20
    a = np.zeros((h + pad_ * 2, w + pad_ * 2, 4), np.uint8)
    for x in range(w):
        dy = -int(round(k * x))
        a[pad_ + dy:pad_ + dy + h, pad_ + x] = sheet_arr[:, x]
    out = np.zeros_like(a)
    for y in range(a.shape[0]):
        dx = int(round(k * (y - pad_ - pivot_y)))
        out[y] = np.roll(a[y], dx, axis=0)
    m = out[..., 3] > 0
    e = m & ~ndi.binary_erosion(m)
    out[e] = E6
    if side == 'L':
        out = out[:, ::-1]
    return out, pad_
