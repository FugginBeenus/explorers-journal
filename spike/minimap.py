"""Spike: minimal location map using vanilla explorer-map rules and MapColor values."""
import sys, os, re, math, time, zlib
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from scipy import ndimage as ndi
from anvil import read_surface

# MapColor values, Brightness 135/180/220/255 as in the game
def mc(rgb, b): return tuple(int(v * b / 255) for v in rgb)
BROWN, BLUE, GREEN = (102, 76, 51), (102, 153, 216), (102, 127, 51)
WASH = (172, 200, 216)
PAL = [None,
       mc(BROWN, 135), mc(BROWN, 180), mc(BROWN, 220),     # 1,2,3
       BLUE, (122, 166, 218), (134, 176, 222),  # 4,5,6
       mc(GREEN, 135), mc(GREEN, 180), mc(GREEN, 220),     # 7,8,9
       WASH]                                               # 10
B_DARK, B_MID, B_SOFT, O_LOW, O_NORM, O_HIGH, G_DARK, G_MID, G_SOFT, W_WASH = range(1, 11)

MAN = re.compile(r'planks|cobble|brick|stairs|slab|fence|glass|wool|door|lantern|bell|terracotta|concrete|barrel|smoker|furnace|_bed$|chest|crafting|fletching|smithing|stonecutter|cartography|loom|lectern|grindstone|cauldron|composter|hay_block')
vec = np.vectorize

def classes(names):
    water = vec(lambda s: 'water' in s or s in ('ice', 'packed_ice', 'blue_ice', 'kelp', 'kelp_plant', 'seagrass', 'tall_seagrass', 'lily_pad'))(names)
    leaves = vec(lambda s: s.endswith('_leaves'))(names)
    conifer = vec(lambda s: s in ('spruce_leaves',))(names)
    man = vec(lambda s: bool(MAN.search(s)))(names)
    path = vec(lambda s: s == 'dirt_path')(names)
    farm = vec(lambda s: s in ('farmland', 'wheat', 'carrots', 'potatoes', 'beetroots'))(names)
    return water, leaves, conifer, man, path, farm

def count2(a):
    h, w = a.shape
    return a[:h // 2 * 2, :w // 2 * 2].reshape(h // 2, 2, w // 2, 2).sum((1, 3))

def thin(mask):
    """Zhang-Suen, keeps lines one pixel wide."""
    m = np.pad(mask, 1).astype(np.uint8)
    changed = True
    while changed:
        changed = False
        for phase in (0, 1):
            P2, P3, P4, P5, P6, P7, P8, P9 = (m[:-2, 1:-1], m[:-2, 2:], m[1:-1, 2:], m[2:, 2:], m[2:, 1:-1], m[2:, :-2], m[1:-1, :-2], m[:-2, :-2])
            seq = [P2, P3, P4, P5, P6, P7, P8, P9, P2]
            A = sum(((seq[i] == 0) & (seq[i + 1] == 1)).astype(np.uint8) for i in range(8))
            B = P2 + P3 + P4 + P5 + P6 + P7 + P8 + P9
            c = m[1:-1, 1:-1] == 1
            if phase == 0:
                k = (P2 * P4 * P6 == 0) & (P4 * P6 * P8 == 0)
            else:
                k = (P2 * P4 * P8 == 0) & (P2 * P6 * P8 == 0)
            d = c & (A == 1) & (B >= 2) & (B <= 6) & k
            if d.any():
                m[1:-1, 1:-1][d] = 0
                changed = True
    return m[1:-1, 1:-1].astype(bool)

TREE = ["  ddd  ", " dgggd ", " dgmgd ", " dgggd ", "  ddd  ", "   t   "]
TREE = [" ddd ", "dgggd", "dgmgd", " ddd ", "  t  "]
PINE = ["  d  ", " dgd ", " dgd ", "dgggd", "  t  "]
PEAK = ["   d   ", "  d s  ", " d   s ", "d     s"]

def stamp(img, spr, x, y, key):
    for j, row in enumerate(spr):
        for i, ch in enumerate(row):
            if ch == ' ': continue
            yy, xx = y + j, x + i
            if 0 <= yy < img.shape[0] and 0 <= xx < img.shape[1]:
                img[yy, xx] = key[ch]

def draw(g, t, f, l, n, seed=1):
    """g,t,f,l,n at block resolution, twice the map size. Returns palette image."""
    t0 = time.time()
    rng = np.random.default_rng(seed)
    H, W = g.shape[0] // 2, g.shape[1] // 2
    water, leaves, conifer, man, path, farm = classes(n)
    water &= l; leaves &= l; man &= l; path &= l; farm &= l

    wat = count2(water) >= 2
    # smooth with the border padded, so water runs off the edge cleanly
    pw = np.pad(wat, 3, mode='edge')
    pw = ndi.binary_opening(ndi.binary_closing(pw, iterations=1), iterations=1)
    wat = pw[3:-3, 3:-3]
    img = np.zeros((H, W), np.uint8)

    # water and coast: vanilla renderBiomePreviewMap rules
    p = np.pad(wat, 1, mode='edge')
    nb = sum(p[1 + dy:p.shape[0] - 1 + dy, 1 + dx:p.shape[1] - 1 + dx].astype(np.uint8)
             for dy in (-1, 0, 1) for dx in (-1, 0, 1) if dx or dy)
    for y in range(H):
        for x in range(W):
            c = nb[y, x]
            if wat[y, x]:
                if c > 7 and y % 2 == 0:
                    k = ((x + int(math.sin(y) * 7.0)) // 8) % 5
                    img[y, x] = O_LOW if k in (0, 4) else O_NORM if k in (1, 3) else O_HIGH
                elif c > 7:
                    img[y, x] = W_WASH
                elif c > 5:
                    img[y, x] = O_NORM
                else:
                    img[y, x] = O_LOW
            elif c > 0:
                img[y, x] = B_SOFT if c > 3 else B_DARK

    # roads: thin dotted line
    pth = thin(ndi.binary_closing(count2(path) >= 1, iterations=1)) & ~wat
    yy, xx = np.where(pth)
    for y, x in zip(yy, xx):
        if (x + y) % 3 != 2: img[y, x] = B_MID

    # fields: a few rows
    frm = ndi.binary_opening(count2(farm) >= 2, iterations=1) & ~wat
    yy, xx = np.where(frm)
    for y, x in zip(yy, xx):
        if y % 2 == 0: img[y, x] = G_SOFT

    # hills: small peaks only where the ground is really steep
    gnd = g.astype(np.float32).copy()
    if l.any(): gnd[~l] = np.median(gnd[l])
    gs = ndi.uniform_filter(gnd, 2)[::2, ::2][:H, :W]
    rng_hi = ndi.maximum_filter(gs, 9) - ndi.minimum_filter(gs, 9)
    cand = np.argwhere((rng_hi >= 18) & ~wat)
    rng.shuffle(cand)
    used = np.zeros((H, W), bool)
    for y, x in cand:
        if used[max(0, y - 7):y + 8, max(0, x - 10):x + 11].any(): continue
        used[y, x] = True
        stamp(img, PEAK, x - 3, y - 2, {'d': B_DARK, 's': B_SOFT})

    # trees: one mark per clump
    can = (count2(leaves) >= 2) & ~wat
    con = count2(conifer & leaves) >= 1
    cand = np.argwhere(can)
    rng.shuffle(cand)
    pts = []
    used = np.zeros((H, W), bool)
    # thick forest gets fewer marks, a lone tree still gets one
    near = ndi.uniform_filter(can.astype(np.float32), 15)
    for y, x in cand:
        gap_y, gap_x = (7, 9) if near[y, x] > 0.45 else (5, 6)
        if used[max(0, y - gap_y):y + gap_y + 1, max(0, x - gap_x):x + gap_x + 1].any(): continue
        used[y, x] = True
        pts.append((y, x))
    pts.sort()
    key = {'d': G_DARK, 'g': G_SOFT, 'm': G_MID, 't': B_DARK}
    for y, x in pts:
        stamp(img, PINE if con[y, x] else TREE, x - 2, y - 2, key)

    # buildings last, so they stay readable: real footprints, kept simple
    bld = ndi.binary_closing(count2(man) >= 2, iterations=1) & ~wat
    lab, k = ndi.label(bld)
    for i, sl in enumerate(ndi.find_objects(lab)):
        m = lab[sl] == i + 1
        if m.sum() < 3: continue
        y0, x0 = sl[0].start, sl[1].start
        h, w = m.shape
        for j in range(h):
            for ii in range(w):
                if m[j, ii]:
                    edge = j == 0 or ii == 0 or j == h - 1 or ii == w - 1 or not (m[j - 1, ii] and m[j + 1, ii] and m[j, ii - 1] and m[j, ii + 1])
                    img[y0 + j, x0 + ii] = B_DARK if edge else B_SOFT

    img[~(count2(l) >= 2)] = 0
    return img, (time.time() - t0) * 1000

def drawn_edge(img, seed=1):
    """The map just ends, the way a drawing does. Each row and column stops at its own spot."""
    r = np.random.default_rng(seed)
    H, W = img.shape
    yy, xx = np.mgrid[0:H, 0:W]
    nx, ny = (xx + .5 - W / 2) / (W / 2), (yy + .5 - H / 2) / (H / 2)
    d = (nx ** 4 + ny ** 4) ** .25
    n = ndi.gaussian_filter(r.random((H, W)), 5)
    n = (n - n.mean()) / n.std()
    out = img.copy()
    out[d + n * 0.03 > 0.95] = 0
    return out

def to_rgba(img):
    h, w = img.shape
    a = np.zeros((h, w, 4), np.uint8)
    for i, c in enumerate(PAL):
        if c: a[img == i] = c + (255,)
    return a

def size4bit(img):
    f = img.flatten().astype(np.uint8)
    f = np.concatenate([f, np.zeros(len(f) % 2, np.uint8)])
    b = (f[0::2] << 4) | f[1::2]
    return len(b), len(zlib.compress(b.tobytes(), 9))

if __name__ == '__main__':
    from PIL import Image
    region, cx, cz, out = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), sys.argv[4]
    W, H = 128, 104
    g, t, f, l, n = read_surface(region, cx - W, cz - H, W * 2, H * 2)
    img, ms = draw(g, t, f, l, n)
    img = drawn_edge(img)
    print('draw ms', round(ms), 'raw/zipped bytes', size4bit(img))
    bg = Image.new('RGBA', (W, H), (253, 246, 229, 255))
    bg.alpha_composite(Image.fromarray(to_rgba(img), 'RGBA'))
    bg.resize((W * 4, H * 4), Image.NEAREST).convert('RGB').save(out)
