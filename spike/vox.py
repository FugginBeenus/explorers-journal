"""Read-only voxel loader and block classes for the world-based sketch test."""
import os, re, sys, math
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from anvil import chunk_nbt

PASS = re.compile(r'^(air|cave_air|void_air|short_grass|grass|tall_grass|fern|large_fern|dandelion|poppy|blue_orchid|allium|azure_bluet|red_tulip|orange_tulip|white_tulip|pink_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|torch|wall_torch|soul_torch|soul_wall_torch|vine|glow_lichen|sugar_cane|dead_bush|.*_sapling|brown_mushroom|red_mushroom|redstone_wire|rail|powered_rail|detector_rail|activator_rail|lever|.*_button|tripwire.*|.*_carpet|snow|.*_pressure_plate|seagrass|tall_seagrass|kelp|kelp_plant|lily_pad|ladder|structure_void|light|barrier|.*_sign|.*_wall_sign|.*_hanging_sign|flower_pot|potted_.*|moving_piston|pink_petals|spore_blossom|hanging_roots|small_dripleaf|big_dripleaf_stem|cobweb|sweet_berry_bush|wheat|carrots|potatoes|beetroots|bamboo_sapling|nether_sprouts|crimson_roots|warped_roots|lantern|soul_lantern|end_rod|candle|.*_candle|bell)$')
PLANT = re.compile(r'^(short_grass|grass|tall_grass|fern|large_fern|dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|sweet_berry_bush|wheat|carrots|potatoes|beetroots)$')
FLOWER = re.compile(r'^(dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony)$')
GROUND = re.compile(r'^(grass_block|dirt|coarse_dirt|podzol|mycelium|rooted_dirt|dirt_path|farmland|mud|moss_block|snow_block|powder_snow|sand|red_sand|gravel|clay|stone|andesite|diorite|granite|deepslate|tuff|calcite|sandstone|red_sandstone|terracotta|.*_ore|packed_ice|ice|blue_ice|bedrock|dripstone_block|netherrack|end_stone|soul_sand|soul_soil|basalt|blackstone)$')

F_NONE, F_GROUND, F_LEAVES, F_LOG, F_PLANKS, F_MASONRY, F_GLASS, F_DOOR, F_WATER, F_OTHER, F_MOB = range(11)

def family(name):
    if name in ('water', 'bubble_column'): return F_WATER
    if name.endswith('_leaves'): return F_LEAVES
    if re.search(r'_log$|_wood$|^stripped_|_stem$|_hyphae$', name): return F_LOG
    if 'door' in name and 'trapdoor' not in name: return F_DOOR
    if 'glass' in name: return F_GLASS
    if GROUND.match(name): return F_GROUND
    if re.search(r'cobble|brick|smooth_stone|polished_|stone_stairs|stone_slab|^stone_|andesite_|diorite_|granite_|sandstone_|_wall$|prismarine|quartz|mossy', name): return F_MASONRY
    if re.search(r'planks|oak_|spruce_|birch_|jungle_|acacia_|dark_oak_|mangrove_|cherry_|bamboo_|crimson_|warped_', name): return F_PLANKS
    return F_OTHER

# boxes: (x0, y0, z0, x1, y1, z1)
FULL = ((0, 0, 0, 1, 1, 1),)
def shape(name, props):
    if PASS.match(name): return ()
    if name.endswith('_slab'):
        t = props.get('type', 'bottom')
        if t == 'bottom': return ((0, 0, 0, 1, .5, 1),)
        if t == 'top': return ((0, .5, 0, 1, 1, 1),)
        return FULL
    if name.endswith('_stairs'):
        low = props.get('half', 'bottom') == 'bottom'
        base = (0, 0, 0, 1, .5, 1) if low else (0, .5, 0, 1, 1, 1)
        y0, y1 = (.5, 1) if low else (0, .5)
        f = props.get('facing', 'north')
        top = {'north': (0, y0, 0, 1, y1, .5), 'south': (0, y0, .5, 1, y1, 1),
               'west': (0, y0, 0, .5, y1, 1), 'east': (.5, y0, 0, 1, y1, 1)}[f]
        return (base, top)
    if name.endswith('_fence') or name.endswith('_wall') and 'sign' not in name:
        return ((.375, 0, .375, .625, 1, .625),)
    if name.endswith('_trapdoor'):
        return ((0, 0, 0, 1, .19, 1),) if props.get('open') != 'true' else ()
    if name.endswith('_bed'): return ((0, 0, 0, 1, .56, 1),)
    if name in ('chest', 'trapped_chest', 'ender_chest'): return ((.06, 0, .06, .94, .88, .94),)
    return FULL

class World:
    def __init__(self, region, x0, x1, y0, y1, z0, z1):
        self.x0, self.y0, self.z0 = x0, y0, z0
        self.nx, self.ny, self.nz = x1 - x0, y1 - y0, z1 - z0
        self.ids = np.zeros((self.ny, self.nz, self.nx), np.uint16)
        self.names = ['air']; self.props = [{}]
        key = {('air', ()): 0}
        for cz in range(z0 >> 4, ((z1 - 1) >> 4) + 1):
            for cx in range(x0 >> 4, ((x1 - 1) >> 4) + 1):
                c = chunk_nbt(region, cx, cz)
                if not c or 'sections' not in c: continue
                for sec in c['sections']:
                    sy = sec['Y'] * 16
                    if sy + 16 <= y0 or sy >= y1: continue
                    bs = sec.get('block_states')
                    if not bs: continue
                    pal = []
                    for p in bs['palette']:
                        nm = p['Name'].replace('minecraft:', '')
                        pr = p.get('Properties', {})
                        k = (nm, tuple(sorted(pr.items())))
                        if k not in key:
                            key[k] = len(self.names); self.names.append(nm); self.props.append(pr)
                        pal.append(key[k])
                    pal = np.array(pal, np.uint16)
                    if 'data' in bs and len(pal) > 1:
                        bits = max(4, math.ceil(math.log2(len(pal))))
                        per = 64 // bits
                        longs = bs['data'].astype('u8')
                        idx = np.repeat(np.arange(len(longs)), per)[:4096]
                        sh = np.tile(np.arange(per) * bits, len(longs))[:4096].astype('u8')
                        vals = ((longs[idx] >> sh) & np.uint64((1 << bits) - 1)).astype(np.int32)
                        arr = pal[vals].reshape(16, 16, 16)
                    else:
                        arr = np.full((16, 16, 16), pal[0], np.uint16)
                    ax0, az0 = cx * 16, cz * 16
                    xa, xb = max(x0, ax0), min(x1, ax0 + 16)
                    za, zb = max(z0, az0), min(z1, az0 + 16)
                    ya, yb = max(y0, sy), min(y1, sy + 16)
                    if xa >= xb or za >= zb or ya >= yb: continue
                    self.ids[ya - y0:yb - y0, za - z0:zb - z0, xa - x0:xb - x0] = \
                        arr[ya - sy:yb - sy, za - az0:zb - az0, xa - ax0:xb - ax0]
        n = len(self.names)
        self.fam = np.array([family(nm) for nm in self.names], np.uint8)
        self.plant = np.array([bool(PLANT.match(nm)) for nm in self.names], bool)
        self.flower = np.array([bool(FLOWER.match(nm)) for nm in self.names], bool)
        self.upper = np.array([pr.get('half') == 'upper' for pr in self.props], bool)
        self.sub = np.array([1 if nm == 'dirt_path' else 2 if nm in ('sand', 'red_sand', 'gravel') else 3 if nm == 'farmland' else 4 if nm in ('stone', 'andesite', 'diorite', 'granite', 'tuff', 'deepslate') else 0 for nm in self.names], np.uint8)
        self.boxes = np.zeros((n, 2, 6), np.float32)
        self.nbox = np.zeros(n, np.uint8)
        for i, (nm, pr) in enumerate(zip(self.names, self.props)):
            b = shape(nm, pr)
            if nm == 'water': b = ((0, 0, 0, 1, .89, 1),)
            self.nbox[i] = len(b)
            for j, bb in enumerate(b): self.boxes[i, j] = bb
