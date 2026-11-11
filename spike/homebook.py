"""The open journal at GUI pixels, built on vanilla's book texture. Every mark is placed on purpose. Wear is physical, never stains."""
import os, sys, math
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from PIL import Image, ImageDraw
import vpage as P
from vpage import SW, SH

INK = (54, 40, 27)
# ribbon tones, dark to bright. The red includes vanilla's binding thread colours.
RED_RIBBON = [0x4E0C08, 0x6E120E, 0x951A13, 0xB01F18, 0xC3251D, 0xD23A30, 0xE66455]
BLUE_RIBBON = [0x14173F, 0x1E225C, 0x2A3080, 0x343B98, 0x3C44AA, 0x5059BC, 0x7A82D6]
# columns: edge, hem, groove | light side (7) | middle (11) | shade side (6) | groove, hem, edge
RIBBON_ROWS = """
011 1111111 11111111111 111111 110
022 2222222 22222222222 222222 120
043 4444444 33333333333 222222 130
053 5555555 44444444444 333333 130
053 5566555 44444444444 333333 130
053 5566655 44444444443 333333 130
053 5555555 44444444433 333322 130
064 6666665 55555555544 444433 130
064 6666555 55555544444 443333 130
053 5555544 44444433333 332222 130
053 5544444 44443333333 222222 120
053 5554444 44443333332 222222 120
053 5555544 44344444333 332222 130
053 5555544 44334544333 333222 130
053 5555544 44433454433 333322 130
053 5555544 44443345443 333322 130
053 5555554 44444334544 333332 130
053 5555554 44444433454 433332 130
053 5555555 44444443345 443332 130
053 5555555 44444444334 544332 130
053 5555555 44444444433 454332 130
053 5555555 44 00 .. 00 334433332 130
053 5555555 00 ...... 00 3443332 130
053 55555 00 .......... 00 44332 130
053 555 00 .............. 00 332 130
053 5 00 .................. 00 2 130
05 00 ...................... 00 30
00 .......................... 00
"""

def ribbon_sprite(pal, short=0):
    rows = [l.replace(' ', '') for l in RIBBON_ROWS.strip().split('\n')]
    if short:
        rows = [r for i, r in enumerate(rows) if not 15 <= i < 15 + short]
    a = np.zeros((len(rows), 30, 4), np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                v = pal[int(ch)]
                a[y, x] = ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)
    return Image.fromarray(a, 'RGBA')

TAB = {'L': ((0xC6, 0x8E, 0x58), (0xDC, 0xA8, 0x74), (0x7C, 0x54, 0x30)),
       'D': ((0x8E, 0x9A, 0x5A), (0xA8, 0xB4, 0x74), (0x56, 0x60, 0x34)),
       'S': ((0xBE, 0x62, 0x54), (0xD6, 0x80, 0x72), (0x78, 0x38, 0x30)),
       'N': ((0x6E, 0x8E, 0xA4), (0x8C, 0xAA, 0xBE), (0x42, 0x58, 0x6C))}
DARK = (0x1C, 0x0F, 0x00)

def bres(x0, y0, x1, y1):
    out = []
    dx, dy = abs(x1 - x0), abs(y1 - y0)
    sx, sy = (1 if x1 > x0 else -1), (1 if y1 > y0 else -1)
    err = dx - dy
    while True:
        out.append((x0, y0))
        if x0 == x1 and y0 == y1: return out
        e = 2 * err
        if e > -dy: err -= dy; x0 += sx
        if e < dx: err += dx; y0 += sy

def rot_nn(im, deg):
    if not deg: return im.copy()
    k = 4
    big = im.resize((im.size[0] * k, im.size[1] * k), Image.NEAREST).rotate(deg, resample=Image.NEAREST, expand=True)
    w, h = big.size
    a = np.array(big)
    return Image.fromarray(a[k // 2:h:k, k // 2:w:k], 'RGBA')

def shadow(g, im, x, y, alpha=100):
    a = np.array(im)
    s = np.zeros_like(a); s[..., :3] = DARK; s[..., 3] = (a[..., 3] > 0) * alpha
    g.alpha_composite(Image.fromarray(s, 'RGBA'), (x + 1, y + 1))

def slip(g, sprite, x, y, deg=0):
    """A loose thing slipped between the pages. The pages later hide its inner end."""
    im = rot_nn(sprite, deg)
    shadow(g, im, x, y)
    g.alpha_composite(im, (x, y))

def letter(w, h, seed=1, tone=(0xF4, 0xEA, 0xD0), edge=(0xB0, 0x9C, 0x7C), ink=(0x6E, 0x5E, 0x4C), fold_y=None):
    """A written letter: its own paper, a fold across it, lines of words."""
    r = np.random.default_rng(seed)
    im = Image.new('RGBA', (w, h), tone + (255,))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=edge + (255,))
    y = 4
    first = True
    while y < h - 3:
        x = 5
        end = w - 6 - int(r.integers(0, 12))
        if first: end = 5 + int(w * .4)
        while x < end:
            ln = int(r.integers(3, 9))
            d.line([(x, y), (min(x + ln, end), y)], fill=ink + (255,))
            x += ln + 2
        y += 4 if first else 3
        first = False
    if fold_y:
        d.line([(1, fold_y), (w - 2, fold_y)], fill=(0xDC, 0xCC, 0xA8, 255))
        d.line([(1, fold_y + 1), (w - 2, fold_y + 1)], fill=(0xFC, 0xF4, 0xDE, 255))
    # the top right corner has been folded over at some point
    f = 6
    for i in range(f):
        for j in range(f - i):
            im.putpixel((w - 1 - j, i), (0, 0, 0, 0))
    for i in range(f):
        d.point((w - f + i, i), fill=edge + (255,))
    for i in range(1, f):
        for j in range(i):
            if i - j > 0:
                im.putpixel((w - f + j, i), (0xE6, 0xD8, 0xB6, 255))
    d.line([(w - f, f - 1), (w - f, f - 1)], fill=edge + (255,))
    return im

def photo_print(pix, border=3):
    w, h = pix.size
    im = Image.new('RGBA', (w + border * 2, h + border * 2), (0xF2, 0xEC, 0xDA, 255))
    im.paste(pix.convert('RGBA'), (border, border))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], outline=(0xB8, 0xAE, 0x96, 255))
    return im

def scale2x(a):
    h, w = a.shape[:2]
    out = np.zeros((h * 2, w * 2, 4), a.dtype)
    p = np.pad(a, ((1, 1), (1, 1), (0, 0)), mode='edge')
    for y in range(h):
        for x in range(w):
            E = p[y + 1, x + 1]; B = p[y, x + 1]; H_ = p[y + 2, x + 1]; D = p[y + 1, x]; F = p[y + 1, x + 2]
            e = np.tile(E, (4, 1))
            if not (B == H_).all() and not (D == F).all():
                if (D == B).all(): e[0] = D
                if (B == F).all(): e[1] = F
                if (D == H_).all(): e[2] = D
                if (H_ == F).all(): e[3] = F
            out[y * 2, x * 2], out[y * 2, x * 2 + 1], out[y * 2 + 1, x * 2], out[y * 2 + 1, x * 2 + 1] = e
    return out

def rotsprite(im, deg):
    """Turn a sprite and keep every pixel on the GUI grid."""
    a = np.array(im.convert('RGBA'))
    for _ in range(3): a = scale2x(a)
    b = Image.fromarray(a, 'RGBA').rotate(deg, resample=Image.NEAREST, expand=True)
    w, h = b.size
    b = b.crop((w % 8 // 2, h % 8 // 2, w - w % 8 + w % 8 // 2, h - h % 8 + h % 8 // 2))
    arr = np.array(b)
    return Image.fromarray(arr[4::8, 4::8], 'RGBA')

# one stitch, broken in the middle. Each end pulls away from the break. Same colours as vanilla's thread.
SNAPPED = ["....dm...",
           "....dm...",
           "...am....",
           "..m......",
           ".l....l..",
           ".....m...",
           "...am....",
           "....dm...",
           "....dm..."]
THREAD_TONES = {'l': (0xC8, 0x31, 0x29), 'm': (0xC3, 0x25, 0x1D), 'a': (0xA1, 0x1F, 0x18), 'd': (0x95, 0x1A, 0x13)}

def snapped_stitch():
    a = np.zeros((len(SNAPPED), len(SNAPPED[0]), 4), np.uint8)
    for y, row in enumerate(SNAPPED):
        for x, ch in enumerate(row):
            if ch in THREAD_TONES:
                a[y, x] = THREAD_TONES[ch] + (255,)
    return Image.fromarray(a, 'RGBA')

class HomeBook:
    """x, y: top-left of the 312 x 196 book, same layout numbers as Location Tooltip. stage 0-4 sets wear."""
    def __init__(self, W, H, x, y, tex, stage=2):
        self.W, self.H, self.x, self.y, self.stage = W, H, x, y, stage
        self.lx, self.rx, self.top, self.bottom = x + 17, x + 166, y + 16, y + 180
        self.a = P.load(tex)
        self.base = P.page_base(self.a)
        self.loose = P.loose_page(self.base)
        self.thread = Image.fromarray(P.thread_sprite(self.a), 'RGBA')

    def cover(self):
        g = Image.new('RGBA', (self.W, self.H), (0, 0, 0, 0))
        g.alpha_composite(Image.fromarray(P.spread_cover(self.a, self.stage), 'RGBA'), (self.x, self.y))
        return g

    def askew(self, g, side, deg, dx=0, dy=0):
        """A whole page out of line. Only its corner and a thin wedge of its edge show past the book."""
        spr, pad_ = P.askew(self.loose, deg, side)
        im = Image.fromarray(spr, 'RGBA')
        if side == 'R':
            px, py = self.x + 156 - pad_ + dx, self.y + 8 - pad_ + dy
        else:
            px, py = self.x + 156 - spr.shape[1] + pad_ + dx, self.y + 8 - pad_ + dy
        shadow(g, im, px, py)
        g.alpha_composite(im, (px, py))

    def pages(self, g):
        r = P.sheet(self.base, self.stage, 'R')
        l = P.sheet(self.base, self.stage, 'L')[:, ::-1]
        g.alpha_composite(Image.fromarray(r, 'RGBA'), (self.x + 156, self.y + 8))
        g.alpha_composite(Image.fromarray(l, 'RGBA'), (self.x + 156 - l.shape[1], self.y + 8))

    def binding(self, g):
        """Vanilla's red thread, sewn down the gutter. On the oldest book one stitch has snapped, low on the page."""
        tw = self.thread.size[0]
        i = 0
        y_ = self.y + 15
        while y_ < self.y + 176:
            x_ = self.x + 155 - tw // 2
            if self.stage >= 4 and i == 10:
                g.alpha_composite(snapped_stitch(), (x_ - 3, y_))
            else:
                g.alpha_composite(self.thread, (x_, y_))
            y_ += 12
            i += 1

    def tab(self, g, V, y, reach, h, key, letter=None):
        """A cut paper tab, glued to a page edge. Clicking it jumps to that section."""
        c, l, s = TAB[key]
        x0 = self.x + 292
        x1 = self.x + 312 + reach
        d = ImageDraw.Draw(g)
        d.rectangle([x0 + 1, y + 1, x1 + 1, y + h], fill=DARK + (100,))
        d.rectangle([x0, y, x1, y + h - 1], fill=c + (255,), outline=s + (255,))
        d.line([(x0, y + 1), (x1 - 1, y + 1)], fill=l + (255,))
        g.putpixel((x1, y), (0, 0, 0, 0)); g.putpixel((x1, y + h - 1), (0, 0, 0, 0))
        if letter:
            V.text(g, x1 - 1 - V.width(letter) + 1, y + (h - 8) // 2 + 1, letter, (40, 28, 18))

    def ribbon(self, g, x, pal=None, short=0):
        """A wide cloth ribbon. It comes out from under the page, bends over the cover's edge, and hangs. Hand shaded: a raised hem, a soft round, one pressed fold."""
        im = ribbon_sprite(pal or RED_RIBBON, short)
        px, py = x, self.y + 188
        shadow(g, im, px, py, 90)
        g.alpha_composite(im, (px, py))
