"""Pixel-exact helpers that mimic how the game draws GUI parts. Everything is drawn at 1 GUI pixel."""
import os
import numpy as np
from PIL import Image

class VGui:
    def __init__(self, vanilla, lt_atlas):
        self.T = vanilla + '/assets/minecraft/textures/'
        self.map_bg = Image.open(self.T + 'map/map_background.png').convert('RGBA')
        self.book_src = Image.open(lt_atlas + '/book.png').convert('RGBA')
        self.widgets = Image.open(lt_atlas + '/widgets.png').convert('RGBA')
        self.font_img = Image.open(self.T + 'font/ascii.png').convert('RGBA')
        self.glyphs = {}
        self.fw, self.fh = self.font_img.size[0] // 16, self.font_img.size[1] // 16
        for cy in range(16):
            for cx in range(16):
                ch = chr(cy * 16 + cx)
                g = self.font_img.crop((cx * self.fw, cy * self.fh, (cx + 1) * self.fw, (cy + 1) * self.fh))
                a = np.array(g)[..., 3]
                cols = np.where(a.any(0))[0]
                width = (cols.max() + 1) if cols.size else 0
                self.glyphs[ch] = (g, int(width) + 1 if cols.size else 4)
        self.glyphs[' '] = (self.glyphs[' '][0], 4)

    def tex(self, rel):
        return Image.open(self.T + rel).convert('RGBA')

    def width(self, s):
        return sum(self.glyphs.get(c, self.glyphs['?'])[1] for c in s)

    def text(self, img, x, y, s, col):
        r, g, b = col[:3]
        for c in s:
            gl, w = self.glyphs.get(c, self.glyphs['?'])
            a = np.array(gl)
            mask = a[..., 3] > 0
            out = np.zeros_like(a)
            out[..., 0], out[..., 1], out[..., 2] = r, g, b
            out[..., 3] = mask * 255
            img.alpha_composite(Image.fromarray(out, 'RGBA'), (int(x), int(y)))
            x += w
        return x

    def text_right(self, img, right, y, s, col):
        self.text(img, right - self.width(s), y, s, col)

    def text_center(self, img, cx, y, s, col):
        self.text(img, cx - self.width(s) // 2, y, s, col)

    def wrap(self, s, width):
        out, cur = [], ''
        for w in s.split(' '):
            t = (cur + ' ' + w).strip()
            if self.width(t) > width and cur:
                out.append(cur); cur = w
            else:
                cur = t
        if cur: out.append(cur)
        return out

    def para(self, img, x, y, s, width, col, lh=10):
        for l in self.wrap(s, width):
            self.text(img, x, y, l, col); y += lh
        return y

    def map_card(self, img, x, y, w, h):
        """Same nine-slice as LTGui.mapCard."""
        M = self.map_bg
        c, mid = 8, 64 - 16
        def blit(dx, dy, u, v, bw, bh):
            img.alpha_composite(M.crop((u, v, u + bw, v + bh)), (dx, dy))
        blit(x, y, 0, 0, c, c); blit(x + w - c, y, 64 - c, 0, c, c)
        blit(x, y + h - c, 0, 64 - c, c, c); blit(x + w - c, y + h - c, 64 - c, 64 - c, c, c)
        dx = c
        while dx < w - c:
            span = min(mid, w - c - dx)
            blit(x + dx, y, c, 0, span, c); blit(x + dx, y + h - c, c, 64 - c, span, c)
            dy = c
            while dy < h - c:
                sy = min(mid, h - c - dy)
                blit(x + dx, y + dy, c, c, span, sy); dy += mid
            dx += mid
        dy = c
        while dy < h - c:
            sy = min(mid, h - c - dy)
            blit(x, y + dy, 0, c, c, sy); blit(x + w - c, y + dy, 64 - c, c, c, sy); dy += mid

    def marker(self, img, name, x, y):
        img.alpha_composite(self.tex(f'map/decorations/{name}.png'), (x, y))

    def button(self, img, x, y, w, label, h=20):
        b = self.tex('gui/sprites/widget/button.png')
        bw = b.size[0]
        img.alpha_composite(b.crop((0, 0, w // 2, h)), (x, y))
        img.alpha_composite(b.crop((bw - (w - w // 2), 0, bw, h)), (x + w // 2, y))
        lw = self.width(label)
        self.text(img, x + (w - lw) // 2 + 1, y + 7, label, (63, 63, 63))
        self.text(img, x + (w - lw) // 2, y + 6, label, (255, 255, 255))

def upscale(img, k):
    return img.resize((img.size[0] * k, img.size[1] * k), Image.NEAREST)
