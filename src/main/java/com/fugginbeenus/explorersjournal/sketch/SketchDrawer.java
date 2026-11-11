package com.fugginbeenus.explorersjournal.sketch;

import com.fugginbeenus.explorersjournal.map.MapDrawer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Turns what the rays saw into a clean ink drawing. Plain Java: arrays in, arrays out, no game classes, so it runs on a background thread.
 * Lines are found at ray size, traced, simplified, and redrawn as straight strokes at page size. Ink levels: 0 none, 1 faint, 2 mid, 3 dark.
 */
public final class SketchDrawer {
    public static final int W = 129, H = 96, SS = 2;
    public static final byte F_NONE = 0, F_GROUND = 1, F_LEAVES = 2, F_LOG = 3, F_PLANKS = 4, F_MASONRY = 5, F_GLASS = 6, F_DOOR = 7, F_WATER = 8, F_OTHER = 9, F_THING = 10;
    /** Ink colours as 0xRRGGBB, by level. */
    public static final int[] INK = {0, 0xB09E7C, 0x70563C, 0x36281B};

    private static final int RW = SketchRays.W, RH = SketchRays.H, GAP_RAYS = 3, TALL_CORNER = 9;
    private static final float GAP_DEPTH = 2.5f, FLOWER_RANGE = 34f, BLOCK_ON_PAGE = 80f;
    private static final int CLOUD_PIECE = 10;
    private static final float FARTHEST = 110f, FAR_INK = 64f, LEDGE_RANGE = 26f, LEDGE_SOLID = 12f, WALL_CORNER_RANGE = 14f;

    private SketchDrawer() {}

    public static byte[] draw(SketchRays r, long seed) {
        Random rng = new Random(seed);
        int n = RW * RH;
        boolean[] hit = new boolean[n], gate = new boolean[n], dark = new boolean[n], mid = new boolean[n], faint = new boolean[n], corners = new boolean[n];
        byte[] fam = new byte[n];
        int[] detail = new int[n];
        readSurfaces(r, hit, fam, detail, gate, faint);
        int misses = 0;
        for (boolean h : hit) {
            if (!h) misses++;
        }
        // with no sky to give the shape, a cave needs more lines
        boolean enclosed = misses < n / 50;

        for (int y = 0; y < RH; y++) {
            for (int x = 0; x < RW; x++) {
                int a = y * RW + x;
                if (x + 1 < RW) compare(r, hit, fam, detail, gate, dark, mid, faint, corners, enclosed, a, a + 1);
                if (y + 1 < RH) compare(r, hit, fam, detail, gate, dark, mid, faint, corners, enclosed, a, a + RW);
            }
        }
        for (int i = 0; i < n; i++) {
            if (mid[i]) faint[i] = false;
        }

        stopShort(r, fam, hit, dark, mid, faint, corners);

        boolean[] onThing = new boolean[n];
        for (int i = 0; i < n; i++) onThing[i] = fam[i] == F_THING;
        byte[] lv = lines(without(dark, onThing), without(mid, onThing), without(faint, onThing));
        byte[] things = lines(both(dark, onThing), both(mid, onThing), both(faint, onThing));
        boolean[] tall = strokes(MapDrawer.thin(corners, RW, RH), 0.8, TALL_CORNER, 4, 2);
        for (int i = 0; i < lv.length; i++) {
            if (tall[i] && lv[i] == 0) lv[i] = 1;
        }
        drawClouds(lv, r);
        standClear(lv, things, onThing);

        byte[] fam1 = new byte[W * H];
        float[] t1 = new float[W * H], plantT = new float[W * H];
        boolean[] flower = new boolean[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x, a = (y * SS + 1) * RW + x * SS + 1;
                fam1[i] = fam[a];
                t1[i] = r.t[a];
                plantT[i] = r.plantT[a];
                flower[i] = r.plantFlower[a];
                if (r.dot[a] || r.dot[a - 1] || r.dot[a - RW] || r.dot[a - RW - 1]) lv[i] = 3;
            }
        }
        drawLeaves(lv, fam1, t1, rng);
        drawPlants(lv, fam1, plantT, flower, rng);
        drawWater(lv, fam1, t1, rng);
        drawSky(lv, fam1, r, rng);
        stopAtEdge(lv, rng);
        return lv;
    }

    /** A line on something far stops short of whatever stands in front of it, so the near thing reads first. */
    private static void stopShort(SketchRays r, byte[] fam, boolean[] hit, boolean[] dark, boolean[] mid, boolean[] faint, boolean[] corners) {
        boolean[] drop = new boolean[hit.length];
        for (int y = 0; y < RH; y++) {
            for (int x = 0; x < RW; x++) {
                int i = y * RW + x;
                if (!hit[i] || !(dark[i] || mid[i] || faint[i] || corners[i])) continue;
                float nearer = r.t[i] - Math.max(GAP_DEPTH, r.t[i] * 0.2f);
                search:
                for (int qy = Math.max(0, y - GAP_RAYS); qy <= Math.min(RH - 1, y + GAP_RAYS); qy++) {
                    for (int qx = Math.max(0, x - GAP_RAYS); qx <= Math.min(RW - 1, x + GAP_RAYS); qx++) {
                        int q = qy * RW + qx;
                        // the same flat surface, seen at a slant, is not in front of itself
                        if (r.t[q] < nearer && !(fam[q] == fam[i] && r.normal[q] == r.normal[i])) {
                            drop[i] = true;
                            break search;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < drop.length; i++) {
            if (drop[i]) dark[i] = mid[i] = faint[i] = corners[i] = false;
        }
    }

    /** Masks at ray size to ink levels at page size. */
    private static byte[] lines(boolean[] dark, boolean[] mid, boolean[] faint) {
        boolean[] dk = strokes(MapDrawer.thin(dark, RW, RH), 0.9, 2, 0, 0);
        boolean[] md = strokes(MapDrawer.thin(without(mid, dark), RW, RH), 0.9, 2, 0, 0);
        boolean[] fn = strokes(MapDrawer.thin(without(without(faint, dark), mid), RW, RH), 0.8, 2, 4, 2);
        // lines that landed side by side become one
        dk = noCorners(MapDrawer.thin(dk, W, H));
        md = noCorners(MapDrawer.thin(without(md, dk), W, H));
        fn = noCorners(without(without(fn, dk), md));
        byte[] lv = new byte[W * H];
        for (int i = 0; i < lv.length; i++) lv[i] = (byte) (dk[i] ? 3 : md[i] ? 2 : fn[i] ? 1 : 0);
        return lv;
    }

    /** What is behind a mob or a boat stops a little short of it, so it stands clear. */
    private static void standClear(byte[] lv, byte[] things, boolean[] onThing) {
        boolean[] area = new boolean[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x, a = (y * SS + 1) * RW + x * SS + 1;
                area[i] = things[i] != 0 || onThing[a] || onThing[a - 1] || onThing[a - RW] || onThing[a - RW - 1];
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (anyIn(area, x - 1, y - 1, x + 1, y + 1)) lv[i] = things[i];
            }
        }
    }

    private static boolean[] both(boolean[] a, boolean[] b) {
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] && b[i];
        return out;
    }

    private static boolean[] without(boolean[] a, boolean[] b) {
        boolean[] out = new boolean[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[i] && !b[i];
        return out;
    }

    /** Small drawn details come from where each ray hit a face, so they keep their perspective. */
    private static void readSurfaces(SketchRays r, boolean[] hit, byte[] fam, int[] detail, boolean[] gate, boolean[] faint) {
        for (int i = 0; i < hit.length; i++) {
            hit[i] = r.t[i] < FARTHEST;
            fam[i] = !hit[i] ? F_NONE : r.part[i] > 0 ? F_THING : r.family[i];
            gate[i] = true;
            if (!hit[i] || r.normal[i] / 2 == 1) continue;
            float u = r.u[i], v = r.v[i];
            int hs = r.blockHash[i];
            switch (fam[i]) {
                case F_PLANKS -> {
                    detail[i] = r.blockY[i] + 2000;
                    gate[i] = hs % 2 == 0;
                }
                case F_LOG -> detail[i] = 4000 + (u > .5f && hs % 2 == 0 ? 1 : 0);
                case F_DOOR -> detail[i] = 5000 + (u > .25f && u < .75f && v > .35f && v < .8f && r.upper[i] ? 1 : 0);
                case F_GLASS -> detail[i] = 6000 + (u > .5f ? 1 : 0) + (v > .5f ? 2 : 0);
                case F_MASONRY -> {
                    // a few short stone dashes, up close
                    boolean first = hs % 4 == 0 && Math.abs(v - .55f) < .07f && u > .15f && u < .7f;
                    boolean second = hs % 4 == 1 && Math.abs(v - .3f) < .07f && u > .35f && u < .9f;
                    if (r.t[i] < 22 && (first || second)) faint[i] = true;
                }
                default -> {
                }
            }
        }
    }

    private static void compare(SketchRays r, boolean[] hit, byte[] fam, int[] detail, boolean[] gate, boolean[] dark, boolean[] mid, boolean[] faint, boolean[] corners, boolean enclosed, int a, int b) {
        boolean ha = hit[a], hb = hit[b];
        if (!ha && !hb) return;
        float ta = r.t[a], tb = r.t[b];
        int mark = ta <= tb || !hb ? a : b;
        if (ha != hb) {
            (r.t[mark] > FAR_INK ? mid : dark)[mark] = true;
            return;
        }
        byte fa = fam[a], fb = fam[b];
        byte na = r.normal[a], nb = r.normal[b];
        float dz = Math.abs(ta - tb), near = Math.min(ta, tb);
        boolean sameNormal = na == nb;
        boolean grd = fa == F_GROUND && fb == F_GROUND;
        boolean jump = dz > (sameNormal ? Math.max(2f, near * 0.1f) : Math.max(1f, near * 0.03f + Math.max(near - 28, 0) * 0.025f));
        if (fa == F_LEAVES && fb == F_LEAVES && dz < 3.2f) jump = false;
        if (grd && dz < Math.max(3f, near * 0.13f)) jump = false;
        // tops at one height are one flat surface, however far apart a slanted view makes them look
        if (sameNormal && na == 2 && fa == fb && r.blockY[a] == r.blockY[b]) jump = false;
        if (jump) (near > FAR_INK ? mid : dark)[mark] = true;
        // the edge of a ledge, up close, so hills and caves have a shape
        if (grd && !jump && !sameNormal && near < LEDGE_RANGE) {
            boolean flatA = na / 2 == 1, flatB = nb / 2 == 1;
            if (flatA == flatB) {
                if (near < WALL_CORNER_RANGE) corners[mark] = true;
            } else if (enclosed || ledge(r, flatA ? a : b, flatA ? b : a)) {
                if (near < LEDGE_SOLID) mid[mark] = true;
                else faint[mark] = true;
            }
        }

        boolean closeMaterial = fa != fb && near < 52;
        boolean built = fa == F_PLANKS || fa == F_MASONRY || fa == F_LOG || fa == F_OTHER || fa == F_DOOR;
        boolean crease = fa == fb && built && !sameNormal && near < 30;
        boolean thing = fa == F_THING && fb == F_THING;
        boolean partLine = thing && r.part[a] != r.part[b];
        if (closeMaterial || crease || partLine) mid[mark] = true;
        // the corners of a mob's cubes
        if (thing && !partLine && !sameNormal) faint[mark] = true;

        boolean same = fa == fb && sameNormal && near < 26;
        boolean changes = detail[a] != detail[b];
        if (same && changes && (gate[a] || gate[b]) && fa != F_LEAVES && fa != F_GROUND) faint[mark] = true;
        // roads and bare ground: a soft edge up close
        if (grd && r.ground[a] != r.ground[b] && near < 34) faint[mark] = true;
        // doors and window bars read as mid lines
        if (same && changes && (fa == F_DOOR || fa == F_GLASS)) mid[mark] = true;
    }

    /** True where ground turns away from you, like the top of a step. The foot of a step is not drawn. */
    private static boolean ledge(SketchRays r, int flat, int side) {
        float sideY = r.blockY[side] + r.v[side];
        // a ray going down lands on a top, and a ray going up lands on an underside
        return r.normal[flat] % 2 == 0 ? sideY < r.blockY[flat] + 1 : sideY > r.blockY[flat];
    }

    private static int[] neighbours(boolean[] m, int p) {
        int y = p / RW, x = p % RW;
        int[] n4 = new int[4];
        int count = 0;
        if (y > 0 && m[p - RW]) n4[count++] = p - RW;
        if (y < RH - 1 && m[p + RW]) n4[count++] = p + RW;
        if (x > 0 && m[p - 1]) n4[count++] = p - 1;
        if (x < RW - 1 && m[p + 1]) n4[count++] = p + 1;
        int[] out = Arrays.copyOf(n4, 8);
        int size = count;
        int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] c : corners) {
            int qy = y + c[0], qx = x + c[1];
            if (qy < 0 || qx < 0 || qy >= RH || qx >= RW || !m[qy * RW + qx]) continue;
            boolean touches = false;
            for (int i = 0; i < count; i++) {
                int ry = n4[i] / RW, rx = n4[i] % RW;
                if (Math.abs(qy - ry) + Math.abs(qx - rx) == 1) touches = true;
            }
            if (!touches) out[size++] = qy * RW + qx;
        }
        return Arrays.copyOf(out, size);
    }

    private static long edge(int a, int b) {
        return (long) a * (RW * RH) + b;
    }

    /** Follows a thin mask from end to end. Each line is a list of ray positions. */
    private static List<int[]> trace(boolean[] mask) {
        int[][] nb = new int[mask.length][];
        for (int p = 0; p < mask.length; p++) {
            if (mask[p]) nb[p] = neighbours(mask, p);
        }
        Set<Long> seen = new HashSet<>();
        List<int[]> lines = new ArrayList<>();
        for (int p = 0; p < mask.length; p++) {
            if (!mask[p] || nb[p].length == 2) continue;
            for (int q : nb[p]) {
                if (!seen.contains(edge(p, q))) lines.add(walk(nb, seen, p, q));
            }
        }
        for (int p = 0; p < mask.length; p++) {
            if (!mask[p]) continue;
            for (int q : nb[p]) {
                if (!seen.contains(edge(p, q))) lines.add(walk(nb, seen, p, q));
            }
        }
        return lines;
    }

    private static int[] walk(int[][] nb, Set<Long> seen, int a, int b) {
        int[] line = new int[16];
        int size = 0;
        line[size++] = a;
        line[size++] = b;
        seen.add(edge(a, b));
        seen.add(edge(b, a));
        int prev = a, cur = b;
        while (nb[cur].length == 2) {
            int next = nb[cur][0] != prev ? nb[cur][0] : nb[cur][1];
            if (seen.contains(edge(cur, next))) break;
            seen.add(edge(cur, next));
            seen.add(edge(next, cur));
            if (size == line.length) line = Arrays.copyOf(line, size * 2);
            line[size++] = next;
            prev = cur;
            cur = next;
        }
        return Arrays.copyOf(line, size);
    }

    /** Keeps only the points that shape the line. */
    private static int[] simplify(int[] line, double eps) {
        if (line.length < 3) return line;
        boolean[] keep = new boolean[line.length];
        keep[0] = keep[line.length - 1] = true;
        List<int[]> stack = new ArrayList<>();
        if (line[0] == line[line.length - 1]) {
            int middle = line.length / 2;
            keep[middle] = true;
            stack.add(new int[]{0, middle});
            stack.add(new int[]{middle, line.length - 1});
        } else {
            stack.add(new int[]{0, line.length - 1});
        }
        while (!stack.isEmpty()) {
            int[] span = stack.remove(stack.size() - 1);
            int i = span[0], j = span[1];
            if (j <= i + 1) continue;
            double ay = line[i] / RW, ax = line[i] % RW;
            double dy = line[j] / RW - ay, dx = line[j] % RW - ax;
            double len = Math.hypot(dy, dx);
            double far = -1;
            int at = -1;
            for (int k = i + 1; k < j; k++) {
                double sy = line[k] / RW - ay, sx = line[k] % RW - ax;
                double dist = len > 0 ? Math.abs(sy * dx - sx * dy) / len : Math.hypot(sy, sx);
                if (dist > far) {
                    far = dist;
                    at = k;
                }
            }
            if (far > eps) {
                keep[at] = true;
                stack.add(new int[]{i, at});
                stack.add(new int[]{at, j});
            }
        }
        int size = 0;
        int[] out = new int[line.length];
        for (int i = 0; i < line.length; i++) {
            if (keep[i]) out[size++] = line[i];
        }
        return Arrays.copyOf(out, size);
    }

    /** Traces a thin mask at ray size, then redraws straight strokes at page size. dashOn 0 means a solid line. */
    private static boolean[] strokes(boolean[] mask, double eps, int minLength, int dashOn, int dashOff) {
        boolean[] out = new boolean[W * H];
        for (int[] line : trace(mask)) {
            int[] s = simplify(line, eps);
            int[] xs = new int[s.length], ys = new int[s.length];
            int length = 0;
            for (int i = 0; i < s.length; i++) {
                xs[i] = (int) ((s[i] % RW + .5) / SS);
                ys[i] = (int) ((s[i] / RW + .5) / SS);
                if (i > 0) length += Math.max(Math.abs(xs[i] - xs[i - 1]), Math.abs(ys[i] - ys[i - 1]));
            }
            if (length < minLength) continue;
            int count = 0;
            for (int i = 1; i < s.length; i++) count = stroke(out, xs[i - 1], ys[i - 1], xs[i], ys[i], dashOn, dashOff, count);
        }
        return out;
    }

    private static int stroke(boolean[] out, int x0, int y0, int x1, int y1, int dashOn, int dashOff, int count) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x1 > x0 ? 1 : -1, sy = y1 > y0 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            boolean on = dashOn == 0 || count % (dashOn + dashOff) < dashOn;
            if (on && x0 >= 0 && y0 >= 0 && x0 < W && y0 < H) out[y0 * W + x0] = true;
            count++;
            if (x0 == x1 && y0 == y1) return count;
            int e = 2 * err;
            if (e > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static boolean ink(boolean[] m, int x, int y) {
        return x >= 0 && y >= 0 && x < W && y < H && m[y * W + x];
    }

    /** Takes the spare pixel out of a stair-step corner. */
    private static boolean[] noCorners(boolean[] m) {
        boolean[] out = m.clone();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!m[y * W + x]) continue;
                boolean n = ink(m, x, y - 1), s = ink(m, x, y + 1), w = ink(m, x - 1, y), e = ink(m, x + 1, y);
                boolean nw = ink(m, x - 1, y - 1), ne = ink(m, x + 1, y - 1), sw = ink(m, x - 1, y + 1), se = ink(m, x + 1, y + 1);
                if ((n && e && !s && !w && !sw) || (n && w && !s && !e && !se) || (s && e && !n && !w && !nw) || (s && w && !n && !e && !ne)) out[y * W + x] = false;
            }
        }
        return out;
    }

    private static boolean anyIn(boolean[] m, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) {
                if (m[y * W + x]) return true;
            }
        }
        return false;
    }

    private static boolean anyInk(byte[] lv, int x0, int y0, int x1, int y1) {
        for (int y = Math.max(0, y0); y <= Math.min(H - 1, y1); y++) {
            for (int x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) {
                if (lv[y * W + x] != 0) return true;
            }
        }
        return false;
    }

    /** A few marks low in each canopy. */
    private static void drawLeaves(byte[] lv, byte[] fam, float[] t, Random rng) {
        boolean[] leaves = new boolean[W * H];
        for (int i = 0; i < leaves.length; i++) leaves[i] = fam[i] == F_LEAVES && t[i] < 60;
        boolean[] inner = shrink(shrink(leaves));
        List<Integer> candidates = new ArrayList<>();
        for (int y = 4; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (inner[y * W + x] && leaves[(y - 4) * W + x]) candidates.add(y * W + x);
            }
        }
        Collections.shuffle(candidates, rng);
        boolean[] used = new boolean[W * H];
        int marks = 0;
        for (int c : candidates) {
            if (marks >= 12) break;
            int y = c / W, x = c % W;
            if (anyIn(used, x - 8, y - 6, x + 8, y + 6)) continue;
            if (x < 1 || x + 2 >= W || y + 1 >= H || anyInk(lv, x - 1, y, x + 2, y + 1)) continue;
            used[c] = true;
            marks++;
            lv[(y + 1) * W + x - 1] = 2;
            lv[c] = 2;
            lv[c + 1] = 2;
        }
    }

    private static boolean[] shrink(boolean[] m) {
        boolean[] out = new boolean[m.length];
        for (int y = 1; y < H - 1; y++) {
            for (int x = 1; x < W - 1; x++) {
                int i = y * W + x;
                out[i] = m[i] && m[i - 1] && m[i + 1] && m[i - W] && m[i + W];
            }
        }
        return out;
    }

    private static double[] noise(Random rng, double sigma) {
        double[] noise = new double[W * H];
        for (int i = 0; i < noise.length; i++) noise[i] = rng.nextDouble();
        noise = MapDrawer.gaussian(noise, W, H, sigma);
        double mean = 0, var = 0;
        for (double v : noise) mean += v;
        mean /= noise.length;
        for (double v : noise) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / noise.length);
        for (int i = 0; i < noise.length; i++) noise[i] = (noise[i] - mean) / std;
        return noise;
    }

    /** Flowers first, as a stem and a dot, then grass in small clusters. */
    private static void drawPlants(byte[] lv, byte[] fam, float[] plantT, boolean[] flower, Random rng) {
        double[] clusters = noise(rng, 7);
        List<Integer> flowers = new ArrayList<>(), grass = new ArrayList<>();
        for (int i = 0; i < lv.length; i++) {
            if (fam[i] != F_GROUND) continue;
            if (flower[i] && plantT[i] < FLOWER_RANGE) flowers.add(i);
            else if (plantT[i] < 17 && clusters[i] > 0.1) grass.add(i);
        }
        Collections.shuffle(flowers, rng);
        Collections.shuffle(grass, rng);
        boolean[] used = new boolean[W * H];
        int count = 0;
        for (int c : flowers) {
            if (count >= 18) break;
            int y = c / W, x = c % W, gap = plantGap(plantT[c], 5);
            if (x <= 0 || x >= W - 1 || y <= 2 || anyIn(used, x - gap, y - gap * 2 / 3, x + gap, y + gap * 2 / 3) || anyInk(lv, x - 1, y - 2, x + 1, y)) continue;
            used[c] = true;
            if (plantT[c] < 5) {
                stamp(lv, null, BIG_FLOWER, x - 2, y - 8);
            } else {
                lv[c] = 2;
                lv[c - W] = 3;
                if (plantT[c] < 14) {
                    lv[c - 2 * W] = 3;
                    lv[c - W - 1] = 2;
                    lv[c - W + 1] = 2;
                }
            }
            count++;
        }
        count = 0;
        for (int c : grass) {
            if (count >= 14) break;
            int y = c / W, x = c % W, gap = plantGap(plantT[c], 7);
            if (x <= 0 || x >= W - 1 || y <= 1 || anyIn(used, x - gap, y - gap * 4 / 7, x + gap, y + gap * 4 / 7) || anyInk(lv, x - 1, y - 2, x + 1, y)) continue;
            used[c] = true;
            lv[c] = 2;
            lv[c - W - 1] = 2;
            lv[c - W + 1] = 2;
            count++;
        }
    }

    /** One mark for each plant: a near plant covers many pixels, so its marks go farther apart. */
    private static int plantGap(float distance, int least) {
        return (int) Math.max(least, Math.min(40f, BLOCK_ON_PAGE / distance));
    }

    private static final String[] BIG_FLOWER = {
            "..2..",
            ".2.2.",
            "2.3.2",
            ".2.2.",
            "..2..",
            "..2..",
            ".22..",
            "..2..",
            "..2..",
    };

    /** Short dashes in rows, which crowd together toward the far shore as they do on real water. */
    private static void drawWater(byte[] lv, byte[] fam, float[] t, Random rng) {
        int next = 0;
        for (int y = 0; y < H; y++) {
            if (y < next) continue;
            float rowT = Float.POSITIVE_INFINITY;
            for (int x = 0; x < W; x++) {
                if (fam[y * W + x] == F_WATER) rowT = Math.min(rowT, t[y * W + x]);
            }
            if (rowT == Float.POSITIVE_INFINITY) continue;
            int gap = rowT < 7 ? 7 : rowT < 14 ? 5 : rowT < 30 ? 4 : 3;
            int length = rowT < 7 ? 6 : rowT < 14 ? 5 : rowT < 30 ? 3 : 2, space = length * 3 + rng.nextInt(length + 1);
            next = y + gap;
            for (int x = rng.nextInt(space); x < W; x += length + space) {
                if (rng.nextInt(4) == 0) continue;
                boolean clear = true;
                for (int k = -1; k <= length && clear; k++) {
                    int px = x + k;
                    clear = px >= 0 && px < W && fam[y * W + px] == F_WATER && lv[y * W + px] == 0;
                }
                if (!clear) continue;
                for (int k = 0; k < length; k++) lv[y * W + x + k] = 1;
            }
        }
    }

    /** A light outline around each large, high cloud. Clouds keep clear of the rest of the drawing. */
    private static void drawClouds(byte[] lv, SketchRays r) {
        boolean[] edge = new boolean[RW * RH];
        boolean any = false;
        for (int y = 0; y < RH; y++) {
            for (int x = 0; x < RW; x++) {
                int a = y * RW + x;
                if (!r.cloud[a]) continue;
                any = true;
                if ((x + 1 < RW && r.t[a + 1] == SketchRays.MISS && !r.cloud[a + 1]) || (x > 0 && r.t[a - 1] == SketchRays.MISS && !r.cloud[a - 1])
                        || (y + 1 < RH && r.t[a + RW] == SketchRays.MISS && !r.cloud[a + RW]) || (y > 0 && r.t[a - RW] == SketchRays.MISS && !r.cloud[a - RW])) {
                    edge[a] = true;
                }
            }
        }
        if (!any) return;
        boolean[] line = strokes(MapDrawer.thin(edge, RW, RH), 1.0, CLOUD_PIECE, 0, 0);
        byte[] before = lv.clone();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (line[y * W + x] && !anyInk(before, x - 2, y - 2, x + 2, y + 2)) lv[y * W + x] = 1;
            }
        }
    }

    // the vanilla sun is square, so this one is too
    private static final String[] SUN = {
            ".....2.....",
            ".2.......2.",
            "...........",
            "...22222...",
            "...2...2...",
            "2..2...2..2",
            "...2...2...",
            "...22222...",
            "...........",
            ".2.......2.",
            ".....2.....",
    };

    /** The sun, the moon in its real phase, stars at night, and rain or snow. Each only on open sky, except the weather. */
    private static void drawSky(byte[] lv, byte[] fam, SketchRays r, Random rng) {
        if (!Float.isNaN(r.sunX) && !r.night) stamp(lv, fam, SUN, Math.round(r.sunX) - 5, Math.round(r.sunY) - 5);
        if (!Float.isNaN(r.moonX) && r.night) stamp(lv, fam, moon(r.moonPhase), Math.round(r.moonX) - 3, Math.round(r.moonY) - 3);
        if (r.night && !r.rain && !r.snow) {
            boolean[] used = new boolean[W * H];
            int stars = 0;
            for (int tries = 0; tries < 400 && stars < 14; tries++) {
                int x = rng.nextInt(W), y = rng.nextInt(H), c = y * W + x;
                if (fam[c] != F_NONE || lv[c] != 0 || anyIn(used, x - 6, y - 5, x + 6, y + 5) || anyInk(lv, x - 2, y - 2, x + 2, y + 2)) continue;
                used[c] = true;
                lv[c] = (byte) (stars % 3 == 0 ? 2 : 1);
                stars++;
            }
        }
        if (r.rain || r.snow) {
            boolean[] used = new boolean[W * H];
            int marks = 0;
            for (int tries = 0; tries < 1500 && marks < (r.rain ? 46 : 60); tries++) {
                int x = 1 + rng.nextInt(W - 2), y = 1 + rng.nextInt(H - 3), c = y * W + x;
                if (anyIn(used, x - 3, y - 3, x + 3, y + 3) || anyInk(lv, x - 1, y - 1, x + 1, y + 2)) continue;
                used[c] = true;
                lv[c] = 1;
                if (r.rain) {
                    lv[c + W] = 1;
                    lv[c + 2 * W - 1] = 1;
                }
                marks++;
            }
        }
    }

    /** A square moon, as in the game, with its dark side filled in light ink. */
    private static String[] moon(int phase) {
        int dark = switch (phase) {
            case 0 -> 0;
            case 1, 7 -> 2;
            case 2, 6 -> 3;
            case 3, 5 -> 4;
            default -> 5;
        };
        boolean left = phase < 4;
        String[] rows = new String[7];
        for (int y = 0; y < 7; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < 7; x++) {
                boolean edge = x == 0 || y == 0 || x == 6 || y == 6;
                int fromSide = left ? x : 6 - x;
                row.append(edge ? '2' : fromSide <= dark ? '1' : '.');
            }
            rows[y] = row.toString();
        }
        return rows;
    }

    /** With fam, draws only on open sky, and clears what is under it. */
    private static void stamp(byte[] lv, byte[] fam, String[] art, int x0, int y0) {
        for (int y = 0; y < art.length; y++) {
            for (int x = 0; x < art[y].length(); x++) {
                int px = x0 + x, py = y0 + y;
                if (px < 0 || py < 0 || px >= W || py >= H || (fam != null && fam[py * W + px] != F_NONE)) continue;
                char c = art[y].charAt(x);
                if (c != '.') lv[py * W + px] = (byte) (c - '0');
                else if (fam != null) lv[py * W + px] = 0;
            }
        }
    }

    /** The drawing just stops near the frame, with no speckle. */
    private static void stopAtEdge(byte[] lv, Random rng) {
        double[] edge = noise(rng, 6);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double r = Math.max(Math.abs(x + .5 - W / 2.0) / (W / 2.0), Math.abs(y + .5 - H / 2.0) / (H / 2.0));
                if (r + edge[y * W + x] * 0.03 > 0.96) lv[y * W + x] = 0;
            }
        }
    }

    /** 2 bits per pixel, then zipped. */
    public static byte[] pack(byte[] lv) {
        byte[] raw = new byte[(lv.length + 3) / 4];
        for (int i = 0; i < lv.length; i++) raw[i / 4] |= (byte) (lv[i] << (6 - i % 4 * 2));
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    /** Null if this is not a whole sketch. A client sends these, so the server checks them with this too. */
    public static byte[] unpack(byte[] packed) {
        byte[] raw = new byte[(W * H + 3) / 4];
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        int off = 0;
        try {
            while (off < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, off, raw.length - off);
                if (n == 0) break;
                off += n;
            }
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        if (off != raw.length) return null;
        byte[] lv = new byte[W * H];
        for (int i = 0; i < lv.length; i++) lv[i] = (byte) (raw[i / 4] >> (6 - i % 4 * 2) & 3);
        return lv;
    }
}
