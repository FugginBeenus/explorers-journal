"""Spike: minimal sketch from rays through the world's blocks. Lines are traced and redrawn so they stay clean."""
import sys, os, math, time, zlib
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from scipy import ndimage as ndi
from PIL import Image
from minimap import thin
from vox import World, F_NONE, F_GROUND, F_LEAVES, F_LOG, F_PLANKS, F_MASONRY, F_GLASS, F_DOOR, F_WATER, F_OTHER, F_MOB

SS = 2

def camera_rays(yaw, pitch, W, H, vfov):
    y, p = math.radians(yaw), math.radians(pitch)
    fwd = np.array([-math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    right = np.array([-math.cos(y), 0.0, -math.sin(y)])
    up = np.cross(right, fwd)
    h = math.tan(math.radians(vfov) / 2)
    w = h * W / H
    xs = (np.arange(W) + .5) / W * 2 - 1
    ys = 1 - (np.arange(H) + .5) / H * 2
    gx, gy = np.meshgrid(xs, ys)
    d = fwd[None, None] + gx[..., None] * w * right[None, None] + gy[..., None] * h * up[None, None]
    return d / np.linalg.norm(d, axis=-1, keepdims=True)

def cast(world, eye, dirs, maxd=140.0):
    H, W, _ = dirs.shape
    n = H * W
    D = dirs.reshape(-1, 3).astype(np.float64)
    org = np.array(eye, np.float64) - np.array([world.x0, world.y0, world.z0], np.float64)
    ix = np.floor(org[0]).astype(int) * np.ones(n, int)
    iy = np.floor(org[1]).astype(int) * np.ones(n, int)
    iz = np.floor(org[2]).astype(int) * np.ones(n, int)
    step = np.where(D >= 0, 1, -1)
    with np.errstate(divide='ignore', invalid='ignore'):
        tdelta = np.abs(1.0 / D)
        tmax = (np.stack([ix, iy, iz], 1) + (step > 0).astype(int) - org) / D
    tmax[~np.isfinite(tmax)] = 1e30
    tdelta[~np.isfinite(tdelta)] = 1e30
    tcur = np.zeros(n)
    t = np.full(n, np.inf); bid = np.zeros(n, np.int32); nrm = np.full(n, -1, np.int8)
    plant_t = np.full(n, np.inf); plant_id = np.zeros(n, np.int32)
    active = np.arange(n)
    steps = 0
    while active.size:
        steps += active.size
        ax, ay, az = ix[active], iy[active], iz[active]
        inside = (ax >= 0) & (ax < world.nx) & (ay >= 0) & (ay < world.ny) & (az >= 0) & (az < world.nz)
        cur = np.zeros(active.size, np.int32)
        cur[inside] = world.ids[ay[inside], az[inside], ax[inside]]
        pl = world.plant[cur]
        if pl.any():
            a = active[pl]
            fresh = ~np.isfinite(plant_t[a])
            plant_t[a[fresh]] = tcur[a[fresh]]; plant_id[a[fresh]] = cur[pl][fresh]
        solid = world.nbox[cur] > 0
        hitmask = np.zeros(active.size, bool)
        if solid.any():
            a = active[solid]; c = cur[solid]
            ro = org[None] - np.stack([ix[a], iy[a], iz[a]], 1).astype(np.float64)
            rd = D[a]
            with np.errstate(divide='ignore', invalid='ignore'):
                inv = 1.0 / rd
            best = np.full(a.size, np.inf); bestn = np.full(a.size, -1, np.int8)
            for bi in range(2):
                valid = world.nbox[c] > bi
                if not valid.any(): continue
                bx = world.boxes[c, bi]
                with np.errstate(invalid='ignore'):
                    t1 = (bx[:, :3] - ro) * inv
                    t2 = (bx[:, 3:] - ro) * inv
                tn = np.minimum(t1, t2); tf = np.maximum(t1, t2)
                tn[~np.isfinite(tn)] = -1e30; tf[~np.isfinite(tf)] = 1e30
                tnear = tn.max(1); tfar = tf.min(1)
                ok = valid & (tnear <= tfar) & (tfar >= 0) & (tnear >= tcur[a] - 1e-6) & (tnear < best)
                axis = tn.argmax(1)
                best[ok] = tnear[ok]
                sgn = rd[np.arange(a.size), axis] > 0
                bestn[ok] = (axis[ok] * 2 + sgn[ok].astype(int)).astype(np.int8)
            got = np.isfinite(best)
            t[a[got]] = best[got]; bid[a[got]] = c[got]; nrm[a[got]] = bestn[got]
            hitmask[np.where(solid)[0][got]] = True
        act = active[~hitmask]
        if act.size == 0: break
        tm = tmax[act]
        axis = tm.argmin(1)
        tcur[act] = tm[np.arange(act.size), axis]
        for k, arr in enumerate((ix, iy, iz)):
            m = axis == k
            arr[act[m]] += step[act[m], k]
            tmax[act[m], k] += tdelta[act[m], k]
        active = act[tcur[act] < maxd]
    return t.reshape(H, W), bid.reshape(H, W), nrm.reshape(H, W), plant_t.reshape(H, W), plant_id.reshape(H, W), steps

def add_boxes(t, nrm, part, dots, dirs, eye, boxes):
    O = np.array(eye, np.float64)
    for box in boxes:
        c, h, yw, pid = box[:4]
        face = box[4] if len(box) > 4 else None
        a = math.radians(yw)
        ca, sa = math.cos(a), math.sin(a)
        R = np.array([[ca, 0, sa], [0, 1, 0], [-sa, 0, ca]])
        ro = (O - np.array(c)) @ R
        rd = dirs @ R
        with np.errstate(divide='ignore', invalid='ignore'):
            inv = 1.0 / rd
        hh = np.array(h)
        t1 = (-hh - ro) * inv; t2 = (hh - ro) * inv
        tn = np.minimum(t1, t2); tf = np.maximum(t1, t2)
        tnear = tn.max(-1); tfar = tf.min(-1)
        ok = (tnear <= tfar) & (tfar > 0) & (tnear < t)
        axis = tn.argmax(-1)
        t[ok] = tnear[ok]; part[ok] = pid; dots[ok] = False
        nrm[ok] = (axis[ok] * 2 + (np.take_along_axis(rd, axis[..., None], -1)[..., 0][ok] > 0)).astype(np.int8)
        if face is not None:
            ph = ro + rd * tnear[..., None]
            front = ok & (np.abs(ph[..., 2] - hh[2]) < 1e-3)
            u = np.clip(((0.5 - ph[..., 0] / (2 * hh[0])) * face.shape[1]).astype(int), 0, face.shape[1] - 1)
            v = np.clip(((0.5 - ph[..., 1] / (2 * hh[1])) * face.shape[0]).astype(int), 0, face.shape[0] - 1)
            dots[front] = face[v[front], u[front]]

def hash3(x, y, z):
    h = (x.astype(np.int64) * 73856093) ^ (y.astype(np.int64) * 19349663) ^ (z.astype(np.int64) * 83492791)
    h = (h ^ (h >> 13)) * 1274126177
    return (h ^ (h >> 16)) & 0x7fffffff

def surface(world, eye, dirs, t, bid, nrm):
    """World position of each hit: block coords and face coords."""
    O = np.array(eye, np.float64)
    tt = np.where(np.isfinite(t), t, 0.0)
    p = O + dirs * (tt[..., None] + 1e-3)
    b = np.floor(p).astype(int)
    f = p - b
    axis = np.where(nrm >= 0, nrm // 2, 1)
    u = np.where(axis == 0, f[..., 2], f[..., 0])
    v = np.where(axis == 1, f[..., 2], f[..., 1])
    return b[..., 0], b[..., 1], b[..., 2], u, v, axis

def edge_pairs(H, W):
    return [((slice(None, -1), slice(None)), (slice(1, None), slice(None))), ((slice(None), slice(None, -1)), (slice(None), slice(1, None)))]

def mark(target, mask, a_side, sa, sb):
    ta = target[sa]; tb = target[sb]
    ta |= mask & a_side
    tb |= mask & ~a_side

def neighbours(p, pts):
    y, x = p
    n4 = [q for q in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)) if q in pts]
    out = list(n4)
    for q in ((y - 1, x - 1), (y - 1, x + 1), (y + 1, x - 1), (y + 1, x + 1)):
        if q in pts and not any(abs(q[0] - r[0]) + abs(q[1] - r[1]) == 1 for r in n4):
            out.append(q)
    return out

def trace(mask):
    pts = set(map(tuple, np.argwhere(mask)))
    nb = {p: neighbours(p, pts) for p in pts}
    seen = set()
    lines = []
    def walk(a, b):
        line = [a, b]
        seen.add((a, b)); seen.add((b, a))
        prev, cur = a, b
        while len(nb[cur]) == 2:
            nxt = nb[cur][0] if nb[cur][0] != prev else nb[cur][1]
            if (cur, nxt) in seen: break
            seen.add((cur, nxt)); seen.add((nxt, cur))
            line.append(nxt)
            prev, cur = cur, nxt
        return line
    for p in pts:
        if len(nb[p]) != 2:
            for q in nb[p]:
                if (p, q) not in seen:
                    lines.append(walk(p, q))
    for p in pts:
        for q in nb[p]:
            if (p, q) not in seen:
                lines.append(walk(p, q))
    return lines

def rdp(line, eps):
    if len(line) < 3: return line
    keep = np.zeros(len(line), bool); keep[0] = keep[-1] = True
    a = np.array(line, np.float32)
    stack = [(0, len(line) - 1)]
    if line[0] == line[-1]:
        mid = len(line) // 2
        keep[mid] = True
        stack = [(0, mid), (mid, len(line) - 1)]
    while stack:
        i, j = stack.pop()
        if j <= i + 1: continue
        d = a[j] - a[i]
        n = np.hypot(*d)
        seg = a[i + 1:j] - a[i]
        dist = np.abs(seg[:, 0] * d[1] - seg[:, 1] * d[0]) / n if n > 0 else np.hypot(seg[:, 0], seg[:, 1])
        k = int(dist.argmax())
        if dist[k] > eps:
            m = i + 1 + k
            keep[m] = True
            stack += [(i, m), (m, j)]
    return [line[i] for i in np.where(keep)[0]]

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

def strokes(mask, eps, min_len, H, W, dash=None):
    """Trace a thin mask at ray resolution, then redraw straight strokes at page resolution."""
    out = np.zeros((H, W), bool)
    for line in trace(mask):
        s = rdp(line, eps)
        pts = [(int((p[1] + .5) / SS), int((p[0] + .5) / SS)) for p in s]
        length = sum(max(abs(a[0] - b[0]), abs(a[1] - b[1])) for a, b in zip(pts, pts[1:]))
        if length < min_len: continue
        i = 0
        for a, b in zip(pts, pts[1:]):
            for x, y in bres(a[0], a[1], b[0], b[1]):
                on = dash is None or i % (dash[0] + dash[1]) < dash[0]
                if on and 0 <= x < W and 0 <= y < H: out[y, x] = True
                i += 1
    return out

def no_corners(m):
    p = np.pad(m, 1)
    c = p[1:-1, 1:-1]
    n, s, w, e = p[:-2, 1:-1], p[2:, 1:-1], p[1:-1, :-2], p[1:-1, 2:]
    nw, ne, sw, se = p[:-2, :-2], p[:-2, 2:], p[2:, :-2], p[2:, 2:]
    cr = c & (((n & e) & ~s & ~w & ~sw) | ((n & w) & ~s & ~e & ~se) | ((s & e) & ~n & ~w & ~nw) | ((s & w) & ~n & ~e & ~ne))
    m = m.copy(); m[cr] = False
    return m

def sketch(world, eye, dirs, t, bid, nrm, plant_t, plant_id, part, dots, W, H, seed=3, detail=1, maxd=110.0):
    """Returns ink levels at page resolution: 0 none, 1 faint, 2 mid, 3 dark."""
    rng = np.random.default_rng(seed)
    HH, WW = t.shape
    hit = np.isfinite(t) & (t < maxd)
    fam = world.fam[bid].copy()
    fam[part > 0] = F_MOB
    fam[~hit] = F_NONE
    bx, by, bz, u, v, axis = surface(world, eye, dirs, t, bid, nrm)
    hs = hash3(bx, by, bz)
    side = (axis != 1) & hit

    # small drawn details, from face coordinates so they keep perspective
    det = np.zeros((HH, WW), np.int64)
    near = t < 30
    sub = world.sub[bid]
    mas = (fam == F_MASONRY) & side
    pl = (fam == F_PLANKS) & side
    det[pl] = by[pl] + 2000
    lg = (fam == F_LOG) & side
    det[lg] = 4000 + ((u > .5) & (hs % 2 == 0))[lg].astype(np.int64)
    dr = (fam == F_DOOR) & side
    up = world.upper[bid]
    win = (u > .25) & (u < .75) & (v > .35) & (v < .8) & up
    det[dr] = 5000 + win[dr].astype(np.int64)
    gl = (fam == F_GLASS) & side
    det[gl] = 6000 + (u > .5)[gl].astype(np.int64) + 2 * (v > .5)[gl].astype(np.int64)
    gate = np.ones((HH, WW), bool)
    gate[pl] = (hs % 2 == 0)[pl]

    dark = np.zeros((HH, WW), bool); mid = np.zeros((HH, WW), bool); faint = np.zeros((HH, WW), bool)
    for sa, sb in edge_pairs(HH, WW):
        ha, hb = hit[sa], hit[sb]
        ta, tb = t[sa], t[sb]
        fa, fb = fam[sa], fam[sb]
        na, nb_ = nrm[sa], nrm[sb]
        both = ha & hb
        with np.errstate(invalid='ignore'):
            dz = np.abs(np.where(both, ta - tb, 0.0))
        dmin = np.minimum(np.where(ha, ta, 1e9), np.where(hb, tb, 1e9))
        a_near = (ta <= tb) | ~hb
        same_n = na == nb_
        sil = ha ^ hb
        jump = both & (dz > np.where(same_n, np.maximum(2.0, dmin * 0.1), np.maximum(1.0, dmin * 0.03 + np.maximum(dmin - 28, 0) * 0.025)))
        leaf = (fa == F_LEAVES) & (fb == F_LEAVES)
        jump &= ~(leaf & (dz < 3.2))
        grd = (fa == F_GROUND) & (fb == F_GROUND)
        jump &= ~(grd & (dz < np.maximum(3.0, dmin * 0.13)))
        mat = both & (fa != fb) & (dmin < 90)
        close_mat = mat & (dmin < 52)
        built = np.isin(fa, (F_PLANKS, F_MASONRY, F_LOG, F_OTHER, F_DOOR))
        crease = both & (fa == fb) & built & (na != nb_) & (dmin < 30)
        pa, pb = part[sa], part[sb]
        partline = both & (fa == F_MOB) & (fb == F_MOB) & (pa != pb)
        mark(dark, sil | jump | (mat & ((fa == F_NONE) | (fb == F_NONE))), a_near, sa, sb)
        mark(mid, close_mat | crease | partline, a_near, sa, sb)
        if detail >= 1:
            same = both & (fa == fb) & same_n & (dmin < 26)
            d1 = det[sa] != det[sb]
            g = gate[sa] | gate[sb]
            fine = same & d1 & g & (fa != F_LEAVES) & (fa != F_GROUND)
            mark(faint, fine, a_near, sa, sb)
            # roads and bare ground: a soft edge up close
            sa_sub, sb_sub = sub[sa], sub[sb]
            mark(faint, both & grd & (sa_sub != sb_sub) & (dmin < 34), a_near, sa, sb)
            # doors and window bars read as mid lines
            dw = same & d1 & np.isin(fa, (F_DOOR, F_GLASS))
            mark(mid, dw, a_near, sa, sb)
    if detail >= 1:
        near_stone = mas & (t < 22)
        a1 = (hs % 4 == 0) & (np.abs(v - .55) < .07) & (u > .15) & (u < .7)
        a2 = (hs % 4 == 1) & (np.abs(v - .3) < .07) & (u > .35) & (u < .9)
        faint |= near_stone & (a1 | a2)
    faint &= ~mid
    dk = strokes(thin(dark), 0.9, 2, H, W)
    md = strokes(thin(mid & ~dark), 0.9, 2, H, W)
    fn = strokes(thin(faint & ~dark & ~mid), 0.8, 2, H, W, dash=(4, 2)) if detail >= 1 else np.zeros((H, W), bool)
    # lines that landed side by side become one
    dk = no_corners(thin(dk))
    md = no_corners(thin(md & ~dk))
    fn = no_corners(fn & ~dk & ~md)
    lv = np.zeros((H, W), np.uint8)
    lv[fn] = 1; lv[md] = 2; lv[dk] = 3

    # page-resolution views of the buffers
    def down(a): return a[SS // 2::SS, SS // 2::SS][:H, :W]
    fam1, t1, ptt, pid = down(fam), down(t), down(plant_t), down(plant_id)
    if dots is not None and dots.any():
        lv[ndi.maximum_filter(dots.astype(np.uint8), 2)[SS // 2::SS, SS // 2::SS][:H, :W] > 0] = 3

    if detail >= 1:
        # leaf clumps: a few marks low in each canopy
        leaves = (fam1 == F_LEAVES) & (t1 < 60)
        inner = ndi.binary_erosion(leaves, iterations=2)
        low = inner & np.roll(leaves, 4, axis=0)
        cand = np.argwhere(low)
        rng.shuffle(cand)
        used = np.zeros((H, W), bool)
        n_t = 0
        for y, x in cand:
            if n_t >= 12: break
            if used[max(0, y - 6):y + 7, max(0, x - 8):x + 9].any(): continue
            if x < 1 or x + 2 >= W or y + 1 >= H or lv[y, x - 1:x + 3].any() or lv[y + 1, x - 1:x + 3].any(): continue
            used[y, x] = True; n_t += 1
            lv[y + 1, x - 1] = 2; lv[y, x] = 2; lv[y, x + 1] = 2
        # plants: grass in clusters, flowers as a stem and a dot
        cl = ndi.gaussian_filter(rng.random((H, W)), 7)
        cl = (cl - cl.mean()) / cl.std()
        is_flower = world.flower[pid]
        cand = np.argwhere(np.isfinite(ptt) & (ptt < 17) & (fam1 == F_GROUND) & (cl > 0.1))
        rng.shuffle(cand)
        used = np.zeros((H, W), bool)
        n_g = n_f = 0
        for y, x in cand:
            if n_g + n_f >= 14: break
            if used[max(0, y - 4):y + 5, max(0, x - 7):x + 8].any(): continue
            if not (0 < x < W - 1 and y > 1): continue
            if lv[y - 2:y + 1, x - 1:x + 2].any(): continue
            used[y, x] = True
            if is_flower[y, x] and n_f < 7:
                lv[y, x] = 2; lv[y - 1, x] = 2; lv[y - 2, x] = 3; n_f += 1
            else:
                lv[y, x] = 2; lv[y - 1, x - 1] = 2; lv[y - 1, x + 1] = 2; n_g += 1
        # water: a few short dashes
        wat = (fam1 == F_WATER) & (lv == 0)
        yy, xx = np.where(wat)
        for y, x in zip(yy, xx):
            if y % 3 == 0 and (x + y * 7) % 13 < 4:
                lv[y, x] = 1

    # the drawing just stops near the frame, no speckle
    yy, xx = np.mgrid[0:H, 0:W]
    r = np.maximum(np.abs(xx + .5 - W / 2) / (W / 2), np.abs(yy + .5 - H / 2) / (H / 2))
    n = ndi.gaussian_filter(rng.random((H, W)), 6)
    n = (n - n.mean()) / n.std()
    lv[r + n * 0.03 > 0.96] = 0
    return lv

def run(world, eye, yaw, pitch, W=129, H=96, vfov=62, boxes=None, seed=3, detail=1):
    t0 = time.time()
    dirs = camera_rays(yaw, pitch, W * SS, H * SS, vfov)
    t, bid, nrm, plant_t, plant_id, steps = cast(world, eye, dirs)
    part = np.zeros(t.shape, np.int32); dots = np.zeros(t.shape, bool)
    if boxes: add_boxes(t, nrm, part, dots, dirs, eye, boxes)
    ms_cast = (time.time() - t0) * 1000
    t1 = time.time()
    lv = sketch(world, eye, dirs, t, bid, nrm, plant_t, plant_id, part, dots, W, H, seed=seed, detail=detail)
    return lv, dict(cast_ms=ms_cast, draw_ms=(time.time() - t1) * 1000, voxel_steps=steps, rays=W * H * SS * SS)

INK = {1: (176, 158, 124), 2: (112, 86, 60), 3: (54, 40, 27)}
def render(lv, paper=(253, 246, 229)):
    H, W = lv.shape
    im = np.zeros((H, W, 3), np.uint8); im[...] = paper
    for k, c in INK.items(): im[lv == k] = c
    return Image.fromarray(im)

def size2bit(lv):
    flat = lv.flatten().astype(np.uint8)
    flat = np.concatenate([flat, np.zeros((-len(flat)) % 4, np.uint8)])
    b = (flat[0::4] << 6) | (flat[1::4] << 4) | (flat[2::4] << 2) | flat[3::4]
    return len(b), len(zlib.compress(b.tobytes(), 9))

if __name__ == '__main__':
    region, x, y, z, yaw, pitch, out = sys.argv[1], *map(float, sys.argv[2:7]), sys.argv[7]
    w = World(region, int(x) - 120, int(x) + 120, int(y) - 40, int(y) + 90, int(z) - 120, int(z) + 120)
    lv, st = run(w, (x, y + 1.62, z), yaw, pitch)
    print({k: round(v) for k, v in st.items()}, 'raw/zipped bytes', size2bit(lv))
    render(lv).resize((129 * 4, 96 * 4), Image.NEAREST).save(out)
