"""Minimal read-only Anvil reader: surface data per column for a block rectangle."""
import os, struct, zlib, gzip, math
import numpy as np

class R:
    __slots__ = ('b', 'p')
    def __init__(self, b): self.b, self.p = b, 0
    def u(self, fmt, n):
        v = struct.unpack_from(fmt, self.b, self.p)[0]; self.p += n; return v
    def s(self):
        n = self.u('>H', 2); v = self.b[self.p:self.p+n].decode('utf-8', 'replace'); self.p += n; return v

def payload(r, t):
    if t == 1: return r.u('>b', 1)
    if t == 2: return r.u('>h', 2)
    if t == 3: return r.u('>i', 4)
    if t == 4: return r.u('>q', 8)
    if t == 5: return r.u('>f', 4)
    if t == 6: return r.u('>d', 8)
    if t == 7:
        n = r.u('>i', 4); v = r.b[r.p:r.p+n]; r.p += n; return v
    if t == 8: return r.s()
    if t == 9:
        et = r.u('>b', 1); n = r.u('>i', 4)
        return [payload(r, et) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            et = r.u('>b', 1)
            if et == 0: return d
            k = r.s(); d[k] = payload(r, et)
    if t == 11:
        n = r.u('>i', 4); v = np.frombuffer(r.b, '>i4', n, r.p); r.p += 4*n; return v
    if t == 12:
        n = r.u('>i', 4); v = np.frombuffer(r.b, '>u8', n, r.p); r.p += 8*n; return v
    raise ValueError(t)

def read_nbt(b):
    r = R(b); t = r.u('>b', 1); r.s(); return payload(r, t)

def chunk_nbt(region_dir, cx, cz, cache={}):
    rx, rz = cx >> 5, cz >> 5
    path = os.path.join(region_dir, f"r.{rx}.{rz}.mca")
    if path not in cache:
        if not os.path.exists(path): cache[path] = None
        else:
            with open(path, 'rb') as f: cache[path] = f.read()
    data = cache[path]
    if not data: return None
    i = 4 * ((cx & 31) + (cz & 31) * 32)
    off = int.from_bytes(data[i:i+3], 'big') * 4096
    if off == 0: return None
    ln = int.from_bytes(data[off:off+4], 'big')
    comp = data[off+4]
    raw = data[off+5:off+4+ln]
    if comp == 2: raw = zlib.decompress(raw)
    elif comp == 1: raw = gzip.decompress(raw)
    elif comp == 3: pass
    else: return None
    return read_nbt(raw)

def unpack(longs, bits, count):
    per = 64 // bits
    mask = (1 << bits) - 1
    out = np.zeros(count, dtype=np.int32)
    for i in range(count):
        out[i] = (int(longs[i // per]) >> ((i % per) * bits)) & mask
    return out

def section_blocks(sec):
    bs = sec.get('block_states')
    if not bs: return None, None
    pal = [p['Name'].replace('minecraft:', '') for p in bs['palette']]
    if len(pal) == 1 or 'data' not in bs:
        return pal, None
    bits = max(4, math.ceil(math.log2(len(pal))))
    per = 64 // bits
    longs = bs['data'].astype('u8')
    idx = np.repeat(np.arange(len(longs)), per)[:4096]
    shift = np.tile(np.arange(per) * bits, len(longs))[:4096].astype('u8')
    vals = (longs[idx] >> shift) & np.uint64((1 << bits) - 1)
    return pal, vals.astype(np.int32)

def read_surface(region_dir, x0, z0, w, h):
    """Returns ground, top, loaded, names (object array of surface block names)."""
    top = np.zeros((h, w), np.int32); ground = np.zeros((h, w), np.int32); floor = np.zeros((h, w), np.int32)
    loaded = np.zeros((h, w), bool)
    names = np.full((h, w), 'air', dtype=object)
    for cz in range(z0 >> 4, (z0 + h - 1 >> 4) + 1):
        for cx in range(x0 >> 4, (x0 + w - 1 >> 4) + 1):
            c = chunk_nbt(region_dir, cx, cz)
            if not c or 'Heightmaps' not in c: continue
            status = str(c.get('Status', '')).replace('minecraft:', '')
            if status != 'full': continue
            hm = c['Heightmaps']
            if 'WORLD_SURFACE' not in hm: continue
            miny = c.get('yPos', -4) * 16
            ws = unpack(hm['WORLD_SURFACE'], 9, 256).reshape(16, 16) + miny - 1
            ml = unpack(hm.get('MOTION_BLOCKING_NO_LEAVES', hm['WORLD_SURFACE']), 9, 256).reshape(16, 16) + miny - 1
            of = unpack(hm.get('OCEAN_FLOOR', hm['WORLD_SURFACE']), 9, 256).reshape(16, 16) + miny - 1
            secs = {s['Y']: s for s in c.get('sections', [])}
            dec = {}
            for lz in range(16):
                wz = cz * 16 + lz
                if not (z0 <= wz < z0 + h): continue
                for lx in range(16):
                    wx = cx * 16 + lx
                    if not (x0 <= wx < x0 + w): continue
                    y = int(ws[lz, lx]); sy = y >> 4
                    if sy not in dec:
                        dec[sy] = section_blocks(secs[sy]) if sy in secs else (None, None)
                    pal, vals = dec[sy]
                    nm = 'air'
                    if pal is not None:
                        nm = pal[0] if vals is None else pal[vals[((y & 15) << 8) | (lz << 4) | lx]]
                    gy, gx = wz - z0, wx - x0
                    top[gy, gx], ground[gy, gx], floor[gy, gx] = y, ml[lz, lx], of[lz, lx]
                    names[gy, gx] = nm; loaded[gy, gx] = True
    return ground, top, floor, loaded, names
