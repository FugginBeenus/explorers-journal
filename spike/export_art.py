"""Writes the book and icon art as game textures. Usage: export_art.py <folder with out/vanilla> <mod resources folder>"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from bookart import Art, LETTERS
import journal_icon as JI
from homebook import ribbon_sprite, shadow, letter, RED_RIBBON, BLUE_RIBBON

# room around the 312 x 196 book for what pokes out and the ribbons
LEFT, TOP, W, H = 12, 12, 336, 232

S, RES = sys.argv[1], sys.argv[2]
T = os.path.join(RES, 'assets/explorersjournal/textures')
art = Art(S)
for st in range(5):
    cover, askew, pages = art.layers(st, W, H, LEFT, TOP)
    print('stage', st, 'askew', askew.getbbox(), 'pages', pages.getbbox())
    cover.save(os.path.join(T, f'gui/book/cover{st}.png'))
    askew.save(os.path.join(T, f'gui/book/askew{st}.png'))
    pages.save(os.path.join(T, f'gui/book/stage{st}.png'))
    JI.icon(st).save(os.path.join(T, f'item/journal_{st}.png'))
JI.icon(2).resize((128, 128), Image.NEAREST).save(os.path.join(RES, 'assets/explorersjournal/icon.png'))

for i, spec in enumerate(LETTERS):
    letter(**spec).save(os.path.join(T, f'gui/book/letter{i}.png'))

def ribbon(pal, short, name):
    spr = ribbon_sprite(pal, short)
    out = Image.new('RGBA', (spr.size[0] + 1, spr.size[1] + 1), (0, 0, 0, 0))
    shadow(out, spr, 0, 0, 90)
    out.alpha_composite(spr)
    out.save(os.path.join(T, f'gui/book/{name}.png'))
    print(name, out.size)

ribbon(RED_RIBBON, 0, 'ribbon_red')
ribbon(BLUE_RIBBON, 6, 'ribbon_blue')
