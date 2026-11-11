"""The journal's item icon, in vanilla's book view and with vanilla's outline. A leather-bound journal the player slowly fills with memories and notes. New: tight and clean. Old: fat with slipped-in papers, rubbed pale at the edges, still held shut by its strap."""
import sys
import numpy as np
from PIL import Image

PAL = {'.': None,
       # vanilla's written book leather, the same colours as the book screen
       'A': 0x391003, 'B': 0x5D2210, 'C': 0x6C2C19, 'D': 0x75321E, 'E': 0x4D1807, 'I': 0x161005,
       # rubbed leather
       'L': 0x87412C, 'H': 0x96543C, 'R': 0xAA6C48,
       # strap
       'x': 0x4A2410, 'y': 0x74441F, 'z': 0x986636,
       # brass, vanilla's clasp colours
       'M': 0xD3A216, 'Q': 0xB9861A, 'q': 0x8E6A22,
       # paper: new white to old yellow
       'W': 0xFBF6E6, 'J': 0xEDE6D2, 'G': 0xC9BFA6, 'N': 0xA59A82, 'F': 0x6E6452, 'Y': 0xE6D9B2, 'T': 0xC8B88C,
       # things slipped in
       'P': 0xD8BE8C, 'b': 0x7AAFDC, 'g': 0x8E9A5A, 'o': 0xC68E58, 'k': 0x3E3A34,
       # ribbon
       'r': 0xC83129, 's': 0x951A13, 'd': 0x6E120E,
       'v': 0x3C44AA, 'u': 0x6068C4}

STAGES = [
# New: slim and tight. Bright pages, a clean cover, a bright clasp.
["................",
 "........AAA.....",
 "......AABCDE....",
 "....xxBCDDCDE...",
 "..AAzzyDDCCDDE..",
 "AABDBxyyDCCDDDE.",
 "EBDDDBxyyDCDDDAE",
 "EABDCDBxyzDDEEF.",
 "IBWBDCDBzMMEJJAE",
 ".IBWBDDDEMQJAEII",
 "..IBWBEEJyxEII..",
 "...IBWJJEyxI....",
 "....IBEEIx......",
 ".....III........",
 "................",
 "................"],
# Used: more pages, the ribbon in use, the first rub on the corners
["................",
 "........AAA.....",
 "......AALCDE....",
 "....xxBCDDCDE...",
 "..AAzzyDDCCDDE..",
 "AABDBxyyDCCDDDE.",
 "ELDDDBxyyDCDDDAE",
 "EABDCDBxyzDDEEF.",
 "EAGBDCDBzMMENNN.",
 "IBJGBDDDEMQNJJAE",
 ".IBJGBEENyxJAEII",
 "..rsJGNJJyxEII..",
 ".rs.BJJJEyxI....",
 "rs..IBEEIx......",
 "s....III........",
 "................"],
# Travelled: a row fatter, a letter slipped in, corners rubbed, paper warming
["........AAA.....",
 "......AAHCDE....",
 "....xxLCDDCDE...",
 "..AAzzyDDCCDDE..",
 "AALDBxyyDCCDDDE.",
 "EHDDDBxyyDCDDLAE",
 "EABDCDBxyzDDEEF.",
 "AATBDCDBzMMENNN.",
 "EAJTBDDDEMQNJJJ.",
 "IBJJTBEENyxJTTWE",
 ".IBJJTNNJyxTWWII",
 "..rsJJJJTyxEII..",
 ".rs.BJTTEyxI....",
 "rs..IBEEIx......",
 "s....III........",
 "................"],
# Worn: letters and a map between the pages, the pages yellow, edges pale, the clasp dulling
["........AAA.....",
 "......AAHLDE....",
 "....xxHLDDCDE...",
 "..AHzzyDDCCDLE..",
 "AAHLBxyyDCCDDLE.",
 "EHLDDBxyyDCDDHAE",
 "EABDCDBxyzDLEEF.",
 "AATBDCDBzMQENNN.",
 "EAYTBDDDEQQNYYY.",
 "IBYYTBEENyxYPPWE",
 ".IBYYTNNYyxPWWII",
 "..rsYYYYTyxWII..",
 ".rs.BYTTEyxI....",
 "rs..IBEEIx......",
 "s....III........",
 "................"],
# Battered: stuffed. Pages out of line on both sides, every edge pale, the clasp dull, the strap still holding.
["........AHA.....",
 "......AHRHDE....",
 "....xyHLDDCDE...",
 "..RHzzyDDCCDHE..",
 "AAHLBxyyDCCDDHR.",
 "RHLDDBxyyDCDDHHE",
 "EHBDCDBxyzDLHHF.",
 "AATLDCDBzqQLNNN.",
 "AAYTLDDDEqQNYYY.",
 "EAYYTLEENyxYPPPY",
 "IYYYYTNNYyxPYYWE",
 ".IBYYYTYPyxNWWWI",
 "..rsYYYYTyxWII..",
 ".rs.BYYTEyxI....",
 "rs..IBEEIx......",
 "s....III........"],
]

def icon(st):
    a = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(STAGES[st]):
        assert len(row) == 16, (st, y, row, len(row))
        for x, ch in enumerate(row):
            v = PAL.get(ch)
            if v is not None:
                a[y, x] = ((v >> 16) & 255, (v >> 8) & 255, v & 255, 255)
    return Image.fromarray(a, 'RGBA')

def sheet(path):
    V = 'out/vanilla/assets/minecraft/textures/item/'
    van = [Image.open(V + n + '.png').convert('RGBA').crop((0, 0, 16, 16)) for n in ('book', 'written_book', 'enchanted_book')]
    SC = 13
    sh = Image.new('RGB', (5 * (16 * SC + 10) + 10, 16 * SC + 92), (139, 139, 139))
    for i in range(5):
        im = icon(i)
        x = 10 + i * (16 * SC + 10)
        b = im.resize((16 * SC, 16 * SC), Image.NEAREST); sh.paste(b, (x, 10), b)
        for j, k in enumerate((1, 2, 3)):
            s = im.resize((16 * k, 16 * k), Image.NEAREST); sh.paste(s, (x + [0, 24, 64][j], 16 * SC + 24), s)
    for j, v in enumerate(van):
        s = v.resize((48, 48), Image.NEAREST); sh.paste(s, (6 + 4 * (16 * SC + 10) + 80 + j * 40, 16 * SC + 24), s)
    sh.save(path)

if __name__ == '__main__':
    sheet(sys.argv[1])
