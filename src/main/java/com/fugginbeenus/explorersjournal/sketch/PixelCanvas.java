package com.fugginbeenus.explorersjournal.sketch;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** A drawing on the page: 64 x 64, one ink for each pixel, and 0 is bare paper. Plain Java, so the server can check a drawing with it too. */
public final class PixelCanvas {
    public static final int SIZE = 64;
    /** Vanilla's dye colours as 0xRRGGBB, in dye order, after bare paper. */
    public static final int[] INK = {0, 0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52, 0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21};
    private static final int MOST_UNDO = 64;

    private final byte[] start;
    private final ArrayDeque<byte[]> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private byte[] pixels;
    private byte[] beforeStroke;
    private int revision;

    /** Null starts on bare paper. */
    public PixelCanvas(byte[] from) {
        pixels = from == null ? new byte[SIZE * SIZE] : from.clone();
        start = pixels.clone();
    }

    public int at(int x, int y) {
        return pixels[y * SIZE + x];
    }

    public byte[] pixels() {
        return pixels;
    }

    /** Goes up each time the picture changes, so the page knows to redraw. */
    public int revision() {
        return revision;
    }

    public boolean isBlank() {
        for (byte b : pixels) {
            if (b != 0) return false;
        }
        return true;
    }

    public boolean changed() {
        return !Arrays.equals(start, pixels);
    }

    public void beginStroke() {
        if (beforeStroke == null) beforeStroke = pixels.clone();
    }

    public void endStroke() {
        if (beforeStroke == null) return;
        if (!Arrays.equals(beforeStroke, pixels)) {
            undo.push(beforeStroke);
            if (undo.size() > MOST_UNDO) undo.removeLast();
            redo.clear();
        }
        beforeStroke = null;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        endStroke();
        if (undo.isEmpty()) return;
        redo.push(pixels);
        pixels = undo.pop();
        revision++;
    }

    public void redo() {
        endStroke();
        if (redo.isEmpty()) return;
        undo.push(pixels);
        pixels = redo.pop();
        revision++;
    }

    /** The cells of a straight line, as x, y pairs. */
    public static int[] lineCells(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0), sx = x1 > x0 ? 1 : -1, sy = y1 > y0 ? 1 : -1, err = dx - dy;
        int[] cells = new int[(Math.max(dx, dy) + 1) * 2];
        int n = 0;
        while (true) {
            cells[n++] = x0;
            cells[n++] = y0;
            if (x0 == x1 && y0 == y1) return Arrays.copyOf(cells, n);
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

    public void line(int x0, int y0, int x1, int y1, int ink) {
        int[] cells = lineCells(x0, y0, x1, y1);
        for (int i = 0; i < cells.length; i += 2) pixels[cells[i + 1] * SIZE + cells[i]] = (byte) ink;
        revision++;
    }

    /** Fills the patch of the same ink around this cell. Corners do not join, so a line can hold the ink in. */
    public void fill(int x, int y, int ink) {
        byte from = pixels[y * SIZE + x], to = (byte) ink;
        if (from == to) return;
        int[] stack = new int[SIZE * SIZE * 4];
        int size = 0;
        stack[size++] = y * SIZE + x;
        pixels[y * SIZE + x] = to;
        while (size > 0) {
            int p = stack[--size], px = p % SIZE, py = p / SIZE;
            if (px > 0 && pixels[p - 1] == from) {
                pixels[p - 1] = to;
                stack[size++] = p - 1;
            }
            if (px < SIZE - 1 && pixels[p + 1] == from) {
                pixels[p + 1] = to;
                stack[size++] = p + 1;
            }
            if (py > 0 && pixels[p - SIZE] == from) {
                pixels[p - SIZE] = to;
                stack[size++] = p - SIZE;
            }
            if (py < SIZE - 1 && pixels[p + SIZE] == from) {
                pixels[p + SIZE] = to;
                stack[size++] = p + SIZE;
            }
        }
        revision++;
    }

    public byte[] pack() {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(pixels);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf));
        deflater.end();
        return out.toByteArray();
    }

    /** Null if this is not a whole drawing. A client sends these, so the server checks them with this too. */
    public static byte[] unpack(byte[] packed) {
        byte[] pixels = new byte[SIZE * SIZE];
        byte[] extra = new byte[1];
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        int off = 0;
        try {
            while (off < pixels.length && !inflater.finished()) {
                int n = inflater.inflate(pixels, off, pixels.length - off);
                if (n == 0) break;
                off += n;
            }
            if (off != pixels.length || inflater.inflate(extra) != 0) return null;
        } catch (DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
        for (byte b : pixels) {
            if (b < 0 || b >= INK.length) return null;
        }
        return pixels;
    }
}
