package com.fugginbeenus.explorersjournal.map;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Draws the hand-drawn map. Plain Java: arrays in, arrays out, no game classes, so it runs on a background thread.
 * Water and coast follow vanilla's explorer map rules, with light blue water. Input is at block resolution, twice the map size.
 */
public final class MapDrawer {
    public static final int W = 128, H = 104;
    public static final int BLOCKS_W = W * 2, BLOCKS_H = H * 2;

    public static final int WATER = 1, LEAVES = 2, CONIFER = 4, BUILT = 8, PATH = 16, FARM = 32, LOADED = 64;

    private static final byte B_DARK = 1, B_MID = 2, B_SOFT = 3, O_LOW = 4, O_NORM = 5, O_HIGH = 6, G_DARK = 7, G_MID = 8, G_SOFT = 9, W_WASH = 10;

    /** Palette as 0xRRGGBB. Index 0 is bare paper. Browns and greens are MapColor at vanilla's map brightness. */
    public static final int[] PALETTE = {0,
            0x36281B, 0x483524, 0x58412C,
            0x6699D8, 0x7AA6DA, 0x86B0DE,
            0x36431B, 0x485924, 0x586D2C,
            0xACC8D8};

    private static final String[] TREE = {" ddd ", "dgggd", "dgmgd", " ddd ", "  t  "};
    private static final String[] PINE = {"  d  ", " dgd ", " dgd ", "dgggd", "  t  "};
    private static final String[] PEAK = {"   d   ", "  d s  ", " d   s ", "d     s"};

    private MapDrawer() {}

    public static byte[] draw(byte[] flags, short[] ground, long seed) {
        Random rng = new Random(seed);
        byte[] img = new byte[W * H];
        boolean[] loaded = atLeast(flags, LOADED, 2);

        boolean[] wat = smoothWater(atLeast(flags, WATER, 2));
        drawWater(img, wat);
        drawRoads(img, wat, atLeast(flags, PATH, 1));
        drawFields(img, wat, atLeast(flags, FARM, 2));
        drawPeaks(img, wat, flags, ground, rng);
        drawTrees(img, wat, flags, rng);
        drawBuildings(img, wat, atLeast(flags, BUILT, 2));

        for (int i = 0; i < img.length; i++) {
            if (!loaded[i]) img[i] = 0;
        }
        drawnEdge(img, new Random(seed ^ 0x5DEECE66DL));
        return img;
    }

    /** How many of each 2 x 2 block group have the flag. */
    private static boolean[] atLeast(byte[] flags, int bit, int min) {
        boolean[] out = new boolean[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int a = y * 2 * BLOCKS_W + x * 2;
                int n = (flags[a] & bit) != 0 ? 1 : 0;
                if ((flags[a + 1] & bit) != 0) n++;
                if ((flags[a + BLOCKS_W] & bit) != 0) n++;
                if ((flags[a + BLOCKS_W + 1] & bit) != 0) n++;
                out[y * W + x] = n >= min;
            }
        }
        return out;
    }

    private static boolean[] dilate(boolean[] a, int w, int h) {
        boolean[] out = new boolean[a.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                out[i] = a[i] || (x > 0 && a[i - 1]) || (x < w - 1 && a[i + 1]) || (y > 0 && a[i - w]) || (y < h - 1 && a[i + w]);
            }
        }
        return out;
    }

    private static boolean[] erode(boolean[] a, int w, int h) {
        boolean[] out = new boolean[a.length];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                out[i] = a[i] && a[i - 1] && a[i + 1] && a[i - w] && a[i + w];
            }
        }
        return out;
    }

    private static boolean[] close(boolean[] a, int w, int h) {
        return erode(dilate(a, w, h), w, h);
    }

    private static boolean[] open(boolean[] a, int w, int h) {
        return dilate(erode(a, w, h), w, h);
    }

    /** Smooth with the border padded, so water runs off the edge cleanly. */
    private static boolean[] smoothWater(boolean[] wat) {
        int p = 3, w = W + p * 2, h = H + p * 2;
        boolean[] padded = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            int sy = Math.max(0, Math.min(H - 1, y - p));
            for (int x = 0; x < w; x++) {
                int sx = Math.max(0, Math.min(W - 1, x - p));
                padded[y * w + x] = wat[sy * W + sx];
            }
        }
        padded = open(close(padded, w, h), w, h);
        boolean[] out = new boolean[W * H];
        for (int y = 0; y < H; y++) {
            System.arraycopy(padded, (y + p) * w + p, out, y * W, W);
        }
        return out;
    }

    private static void drawWater(byte[] img, boolean[] wat) {
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int c = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int ny = Math.max(0, Math.min(H - 1, y + dy)), nx = Math.max(0, Math.min(W - 1, x + dx));
                        if (wat[ny * W + nx]) c++;
                    }
                }
                int i = y * W + x;
                if (wat[i]) {
                    if (c > 7 && y % 2 == 0) {
                        int k = Math.floorMod(Math.floorDiv(x + (int) (Math.sin(y) * 7.0), 8), 5);
                        img[i] = k == 0 || k == 4 ? O_LOW : k == 1 || k == 3 ? O_NORM : O_HIGH;
                    } else if (c > 7) {
                        img[i] = W_WASH;
                    } else if (c > 5) {
                        img[i] = O_NORM;
                    } else {
                        img[i] = O_LOW;
                    }
                } else if (c > 0) {
                    img[i] = c > 3 ? B_SOFT : B_DARK;
                }
            }
        }
    }

    private static void drawRoads(byte[] img, boolean[] wat, boolean[] path) {
        boolean[] line = thin(close(path, W, H), W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (line[i] && !wat[i] && (x + y) % 3 != 2) img[i] = B_MID;
            }
        }
    }

    /** Zhang-Suen thinning: keeps lines one pixel wide. */
    public static boolean[] thin(boolean[] mask, int w, int h) {
        boolean[] m = mask.clone();
        boolean changed = true;
        int[] nb = new int[8];
        while (changed) {
            changed = false;
            for (int phase = 0; phase < 2; phase++) {
                List<Integer> clear = new ArrayList<>();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        if (!m[i]) continue;
                        nb[0] = at(m, x, y - 1, w, h); nb[1] = at(m, x + 1, y - 1, w, h);
                        nb[2] = at(m, x + 1, y, w, h); nb[3] = at(m, x + 1, y + 1, w, h);
                        nb[4] = at(m, x, y + 1, w, h); nb[5] = at(m, x - 1, y + 1, w, h);
                        nb[6] = at(m, x - 1, y, w, h); nb[7] = at(m, x - 1, y - 1, w, h);
                        int b = 0, a = 0;
                        for (int k = 0; k < 8; k++) {
                            b += nb[k];
                            if (nb[k] == 0 && nb[(k + 1) % 8] == 1) a++;
                        }
                        boolean keep = phase == 0
                                ? nb[0] * nb[2] * nb[4] == 0 && nb[2] * nb[4] * nb[6] == 0
                                : nb[0] * nb[2] * nb[6] == 0 && nb[0] * nb[4] * nb[6] == 0;
                        if (a == 1 && b >= 2 && b <= 6 && keep) clear.add(i);
                    }
                }
                for (int i : clear) m[i] = false;
                changed |= !clear.isEmpty();
            }
        }
        return m;
    }

    private static int at(boolean[] m, int x, int y, int w, int h) {
        return x >= 0 && y >= 0 && x < w && y < h && m[y * w + x] ? 1 : 0;
    }

    private static void drawFields(byte[] img, boolean[] wat, boolean[] farm) {
        boolean[] field = open(farm, W, H);
        for (int y = 0; y < H; y += 2) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (field[i] && !wat[i]) img[i] = G_SOFT;
            }
        }
    }

    /** Small peaks only where the ground really climbs, 18 blocks or more. */
    private static void drawPeaks(byte[] img, boolean[] wat, byte[] flags, short[] ground, Random rng) {
        short median = medianLoaded(flags, ground);
        int[] gs = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int a = y * 2 * BLOCKS_W + x * 2, sum = 0;
                for (int k : new int[]{a, a + 1, a + BLOCKS_W, a + BLOCKS_W + 1}) {
                    sum += (flags[k] & LOADED) != 0 ? ground[k] : median;
                }
                gs[y * W + x] = sum / 4;
            }
        }
        List<Integer> cand = new ArrayList<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (wat[y * W + x]) continue;
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int j = Math.max(0, y - 4); j <= Math.min(H - 1, y + 4); j++) {
                    for (int i = Math.max(0, x - 4); i <= Math.min(W - 1, x + 4); i++) {
                        int v = gs[j * W + i];
                        if (v < lo) lo = v;
                        if (v > hi) hi = v;
                    }
                }
                if (hi - lo >= 18) cand.add(y * W + x);
            }
        }
        Collections.shuffle(cand, rng);
        boolean[] used = new boolean[W * H];
        for (int c : cand) {
            int y = c / W, x = c % W;
            if (anyUsed(used, x, y, 10, 7)) continue;
            used[c] = true;
            stamp(img, PEAK, x - 3, y - 2, 'd', B_DARK, 's', B_SOFT, ' ', (byte) 0, ' ', (byte) 0);
        }
    }

    private static short medianLoaded(byte[] flags, short[] ground) {
        short[] v = new short[ground.length];
        int n = 0;
        for (int i = 0; i < ground.length; i++) {
            if ((flags[i] & LOADED) != 0) v[n++] = ground[i];
        }
        if (n == 0) return 64;
        Arrays.sort(v, 0, n);
        return v[n / 2];
    }

    /** One mark per clump. Thick forest gets fewer marks, a lone tree still gets one. */
    private static void drawTrees(byte[] img, boolean[] wat, byte[] flags, Random rng) {
        boolean[] leaves = atLeast(flags, LEAVES, 2);
        boolean[] conifer = atLeast(flags, CONIFER, 1);
        int[] sum = new int[(W + 1) * (H + 1)];
        List<Integer> cand = new ArrayList<>();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean can = leaves[y * W + x] && !wat[y * W + x];
                if (can) cand.add(y * W + x);
                sum[(y + 1) * (W + 1) + x + 1] = (can ? 1 : 0) + sum[y * (W + 1) + x + 1] + sum[(y + 1) * (W + 1) + x] - sum[y * (W + 1) + x];
            }
        }
        Collections.shuffle(cand, rng);
        boolean[] used = new boolean[W * H];
        List<Integer> marks = new ArrayList<>();
        for (int c : cand) {
            int y = c / W, x = c % W;
            int x0 = Math.max(0, x - 7), x1 = Math.min(W, x + 8), y0 = Math.max(0, y - 7), y1 = Math.min(H, y + 8);
            double near = (sum[y1 * (W + 1) + x1] - sum[y0 * (W + 1) + x1] - sum[y1 * (W + 1) + x0] + sum[y0 * (W + 1) + x0]) / 225.0;
            boolean thick = near > 0.45;
            if (anyUsed(used, x, y, thick ? 9 : 6, thick ? 7 : 5)) continue;
            used[c] = true;
            marks.add(c);
        }
        Collections.sort(marks);
        for (int c : marks) {
            int y = c / W, x = c % W;
            stamp(img, conifer[c] ? PINE : TREE, x - 2, y - 2, 'd', G_DARK, 'g', G_SOFT, 'm', G_MID, 't', B_DARK);
        }
    }

    private static boolean anyUsed(boolean[] used, int x, int y, int gapX, int gapY) {
        for (int j = Math.max(0, y - gapY); j <= Math.min(H - 1, y + gapY); j++) {
            for (int i = Math.max(0, x - gapX); i <= Math.min(W - 1, x + gapX); i++) {
                if (used[j * W + i]) return true;
            }
        }
        return false;
    }

    private static void stamp(byte[] img, String[] sprite, int x, int y, char k1, byte v1, char k2, byte v2, char k3, byte v3, char k4, byte v4) {
        for (int j = 0; j < sprite.length; j++) {
            for (int i = 0; i < sprite[j].length(); i++) {
                char ch = sprite[j].charAt(i);
                if (ch == ' ') continue;
                int yy = y + j, xx = x + i;
                if (yy < 0 || xx < 0 || yy >= H || xx >= W) continue;
                img[yy * W + xx] = ch == k1 ? v1 : ch == k2 ? v2 : ch == k3 ? v3 : v4;
            }
        }
    }

    /** Buildings last, so they stay readable: real footprints, kept simple. */
    private static void drawBuildings(byte[] img, boolean[] wat, boolean[] built) {
        boolean[] closed = close(built, W, H);
        boolean[] bld = new boolean[W * H];
        for (int i = 0; i < bld.length; i++) bld[i] = closed[i] && !wat[i];
        int[] label = new int[W * H];
        int[] stack = new int[W * H];
        int next = 0;
        for (int start = 0; start < bld.length; start++) {
            if (!bld[start] || label[start] != 0) continue;
            next++;
            List<Integer> cells = new ArrayList<>();
            int top = 0, x0 = W, x1 = 0, y0 = H, y1 = 0;
            stack[top++] = start;
            label[start] = next;
            while (top > 0) {
                int c = stack[--top];
                cells.add(c);
                int y = c / W, x = c % W;
                x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
                if (x > 0 && bld[c - 1] && label[c - 1] == 0) { label[c - 1] = next; stack[top++] = c - 1; }
                if (x < W - 1 && bld[c + 1] && label[c + 1] == 0) { label[c + 1] = next; stack[top++] = c + 1; }
                if (y > 0 && bld[c - W] && label[c - W] == 0) { label[c - W] = next; stack[top++] = c - W; }
                if (y < H - 1 && bld[c + W] && label[c + W] == 0) { label[c + W] = next; stack[top++] = c + W; }
            }
            if (cells.size() < 3) continue;
            for (int c : cells) {
                int y = c / W, x = c % W;
                boolean edge = x == x0 || x == x1 || y == y0 || y == y1
                        || label[c - 1] != next || label[c + 1] != next || label[c - W] != next || label[c + W] != next;
                img[c] = edge ? B_DARK : B_SOFT;
            }
        }
    }

    /** The map just ends, the way a drawing does. Each row and column stops at its own spot. */
    private static void drawnEdge(byte[] img, Random rng) {
        double[] noise = new double[W * H];
        for (int i = 0; i < noise.length; i++) noise[i] = rng.nextDouble();
        noise = gaussian(noise, W, H, 5);
        double mean = 0, var = 0;
        for (double v : noise) mean += v;
        mean /= noise.length;
        for (double v : noise) var += (v - mean) * (v - mean);
        double std = Math.sqrt(var / noise.length);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double nx = (x + .5 - W / 2.0) / (W / 2.0), ny = (y + .5 - H / 2.0) / (H / 2.0);
                double d = Math.pow(nx * nx * nx * nx + ny * ny * ny * ny, .25);
                if (d + (noise[y * W + x] - mean) / std * 0.03 > 0.95) img[y * W + x] = 0;
            }
        }
    }

    public static double[] gaussian(double[] a, int w, int h, double sigma) {
        int r = (int) (sigma * 3);
        double[] k = new double[r * 2 + 1];
        double total = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = Math.exp(-i * i / (2 * sigma * sigma));
            total += k[i + r];
        }
        for (int i = 0; i < k.length; i++) k[i] /= total;
        double[] b = new double[a.length], c = new double[a.length];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -r; i <= r; i++) s += k[i + r] * a[y * w + Math.max(0, Math.min(w - 1, x + i))];
                b[y * w + x] = s;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -r; i <= r; i++) s += k[i + r] * b[Math.max(0, Math.min(h - 1, y + i)) * w + x];
                c[y * w + x] = s;
            }
        }
        return c;
    }

    /** 4 bits per pixel, then zipped. */
    public static byte[] pack(byte[] img) {
        byte[] raw = new byte[(img.length + 1) / 2];
        for (int i = 0; i < img.length; i++) {
            raw[i / 2] |= (byte) (i % 2 == 0 ? img[i] << 4 : img[i]);
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    public static byte[] unpack(byte[] packed) {
        byte[] raw = new byte[(W * H + 1) / 2];
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        try {
            int off = 0;
            while (off < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, off, raw.length - off);
                if (n == 0) break;
                off += n;
            }
        } catch (DataFormatException e) {
            return new byte[W * H];
        } finally {
            inflater.end();
        }
        byte[] img = new byte[W * H];
        for (int i = 0; i < img.length; i++) {
            img[i] = (byte) (i % 2 == 0 ? (raw[i / 2] >> 4) & 15 : raw[i / 2] & 15);
        }
        return img;
    }
}
