"""Builds the whole open journal for any wear stage. What pokes out comes from what is in the book: mostly its own pages out of line, a few slipped-in papers."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from PIL import Image
import minimap as mm
from anvil import read_surface
from vgui import VGui
from sketchgen_fit import fit
from homebook import HomeBook, slip, letter, photo_print, RED_RIBBON, BLUE_RIBBON

# the Location Tooltip folder, next to this one unless LT says where
LT = os.environ.get("LT", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "locationtooltip-clean-start"))
# the two letters that can be slipped in. The game turns them and adds the shadow.
LETTERS = [dict(w=70, h=36, seed=3), dict(w=64, h=40, seed=11, tone=(0xEE, 0xE0, 0xBC), fold_y=20)]

class Art:
    def __init__(self, S):
        self.S = S
        ATLAS = LT + "/src/main/resources/assets/locationtooltip/textures/gui/atlas"
        self.V = VGui(S + '/out/vanilla', ATLAS)
        self.TEX = S + '/out/vanilla/assets/minecraft/textures/'
        self._map = self._pig = None

    @property
    def MIMG(self):
        if self._map is None:
            world = LT + "/versions/1.21.1/run/saves/New World (1)/region"
            g, t, f, l, n = read_surface(world, -975 - 128, 150 - 104, 256, 208)
            self._map, _ = mm.draw(g, t, f, l, n, seed=5)
        return self._map

    @property
    def PIG(self):
        if self._pig is None:
            self._pig = Image.open(self.S + "/src/exposure-1.21.1/docs/.assets/exposure/flash.png").convert('RGB')
        return self._pig

    def map_sprite(self, w, h):
        """A map sheet: vanilla's map paper, with the drawn map inside. Picks the part with the most water along its top edge, so the edge that shows reads as a map."""
        card = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        self.V.map_card(card, 0, 0, w, h)
        m = np.array(self.MIMG)
        rgba = np.array(Image.fromarray(mm.to_rgba(self.MIMG), 'RGBA'))
        H_, W_ = rgba.shape[:2]
        iw, ih = w - 6, h - 6
        blue = (rgba[..., 2].astype(int) > rgba[..., 0].astype(int) + 30)
        best, at = -1, (0, 0)
        for cy in range(0, H_ - ih, 2):
            for cx in range(0, W_ - iw, 2):
                band = blue[cy:cy + 8, cx:cx + iw]
                score = int(band.sum())
                # some land in it as well
                if score > iw * 8 * 0.45: score = 0
                if score > best: best, at = score, (cx, cy)
        cx, cy = at
        card.alpha_composite(Image.fromarray(rgba[cy:cy + ih, cx:cx + iw], 'RGBA'), (3, 3))
        return card

    def photo_sprite(self, n=40):
        pix = fit(self.PIG, n, n).quantize(colors=24, method=Image.MEDIANCUT, dither=Image.NONE).convert('RGB')
        return photo_print(pix, 3)

    def askews(self, pk, b, stage):
        """Whole pages out of line, lowest of all. Mostly these. They are real pages, turned a little where they are bound."""
        if stage >= 4:
            b.askew(pk, 'R', 3.3, 2, 0)
            b.askew(pk, 'L', -2.9, -3, 1)
        if stage >= 3:
            b.askew(pk, 'R', -2.7, 2, 1)
            b.askew(pk, 'L', 2.4, -2, 0)
        if stage >= 2:
            b.askew(pk, 'L', -1.9, 0, 0)
        if stage >= 1:
            b.askew(pk, 'R', 2.1, 0, 0)

    def pokes(self, pk, b, stage, tabs=True):
        V = self.V
        x, y = b.x, b.y
        self.askews(pk, b, stage)
        # cut paper tabs, glued to a page edge
        if tabs:
            if stage >= 1: b.tab(pk, V, y + 22, 7, 16, 'L', 'L')
            if stage >= 2: b.tab(pk, V, y + 50, 7, 16, 'D', 'D')
            if stage >= 3: b.tab(pk, V, y + 84, 7, 16, 'S', 'S')
            if stage >= 4: b.tab(pk, V, y + 118, 6, 16, 'N', 'N')
        # a few loose papers between the pages. They only show a short way past the cover.
        if stage >= 2:
            slip(pk, letter(**LETTERS[0]), x + 44, y - 5, 2)
        if stage >= 3:
            slip(pk, self.map_sprite(66, 40), x + 214, y - 7, -3)
        if stage >= 4:
            slip(pk, letter(**LETTERS[1]), x + 40, y + 160, -3)
            slip(pk, self.photo_sprite(40), x - 7, y + 112, 6)

    def layers(self, stage, W, H, x, y):
        """The art for the game: the cover, the pages out of line, then the pages. The game draws ribbons and tabs on the cover, and the loose papers between the last two, from what is in the book."""
        b = HomeBook(W, H, x, y, self.TEX, stage=stage)
        askew = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        self.askews(askew, b, stage)
        pages = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        b.pages(pages)
        b.binding(pages)
        return b.cover(), askew, pages

    def build(self, stage, seed, W, H, x, y):
        g = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        b = HomeBook(W, H, x, y, self.TEX, stage=stage)
        g.alpha_composite(b.cover())
        pk = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        b.ribbon(pk, x + 166)
        if stage >= 3: b.ribbon(pk, x + 116, BLUE_RIBBON, 6)
        self.pokes(pk, b, stage)
        g.alpha_composite(pk)
        b.pages(g)
        b.binding(g)
        return g
