package com.fugginbeenus.explorersjournal.client;

/** Turns a small sprite and keeps every pixel on the GUI grid: grow it 8 times with Scale2x, turn it, then take every 8th pixel. Plain arrays in and out. */
final class RotSprite {
    record Sprite(int[] pixels, int w, int h) {}

    private static final int GROW = 8;

    private RotSprite() {}

    static Sprite turn(int[] pixels, int w, int h, double degrees) {
        int[] big = pixels;
        int bw = w, bh = h;
        for (int i = 0; i < 3; i++) {
            big = scale2x(big, bw, bh);
            bw *= 2;
            bh *= 2;
        }
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        int ow = (int) Math.ceil(w * Math.abs(c) + h * Math.abs(s));
        int oh = (int) Math.ceil(w * Math.abs(s) + h * Math.abs(c));
        int[] out = new int[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                double dx = (x + 0.5 - ow / 2.0) * GROW, dy = (y + 0.5 - oh / 2.0) * GROW;
                int sx = (int) Math.floor(c * dx - s * dy + bw / 2.0);
                int sy = (int) Math.floor(s * dx + c * dy + bh / 2.0);
                if (sx >= 0 && sx < bw && sy >= 0 && sy < bh) out[y * ow + x] = big[sy * bw + sx];
            }
        }
        return new Sprite(out, ow, oh);
    }

    /** A plain turn with the nearest pixel, for a sheet of paper a few degrees out of line. Positive degrees turn it to the left. */
    static Sprite tilt(int[] pixels, int w, int h, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        int ow = (int) Math.ceil(w * Math.abs(c) + h * Math.abs(s));
        int oh = (int) Math.ceil(w * Math.abs(s) + h * Math.abs(c));
        int[] out = new int[ow * oh];
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                double dx = x + 0.5 - ow / 2.0, dy = y + 0.5 - oh / 2.0;
                int sx = (int) Math.floor(c * dx - s * dy + w / 2.0);
                int sy = (int) Math.floor(s * dx + c * dy + h / 2.0);
                if (sx >= 0 && sx < w && sy >= 0 && sy < h) out[y * ow + x] = pixels[sy * w + sx];
            }
        }
        return new Sprite(out, ow, oh);
    }

    private static int at(int[] p, int w, int h, int x, int y) {
        return p[Math.max(0, Math.min(y, h - 1)) * w + Math.max(0, Math.min(x, w - 1))];
    }

    private static int[] scale2x(int[] p, int w, int h) {
        int[] out = new int[w * 2 * h * 2];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int e = at(p, w, h, x, y);
                int b = at(p, w, h, x, y - 1), low = at(p, w, h, x, y + 1), d = at(p, w, h, x - 1, y), f = at(p, w, h, x + 1, y);
                int e0 = e, e1 = e, e2 = e, e3 = e;
                if (b != low && d != f) {
                    if (d == b) e0 = d;
                    if (b == f) e1 = f;
                    if (d == low) e2 = d;
                    if (low == f) e3 = f;
                }
                int row = y * 2 * w * 2 + x * 2;
                out[row] = e0;
                out[row + 1] = e1;
                out[row + w * 2] = e2;
                out[row + w * 2 + 1] = e3;
            }
        }
        return out;
    }
}
