package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;

/** Where things go on an entry's pages, from the page's top left. Each kind of entry has a few layouts. The entry's seed picks one and nudges it a pixel or two, so a page always looks the same, and two pages never look stamped from one mold. */
final class EntryLayout {
    static final int PAGE_W = 129, BOTTOM = 154, MORE_TOP = -2, MORE_LINES = 16;
    static final int SKETCH_H = 96, DRAWING_H = 128;
    private static final int RIGHT_EDGE = 125;

    enum Day { RIGHT, AFTER }

    /** The title is a plain line under the picture, not a heading over it. */
    final boolean caption;
    final boolean centered;
    final int titleX, titleY;
    final Day day;
    final int pictureY;
    final int bodyTop, bodyX, bodyW, indent, lines;

    private EntryLayout(boolean caption, boolean centered, int titleX, int titleY, Day day, int pictureY, int bodyTop, int bodyX, int bodyW, int indent, int lines) {
        this.caption = caption;
        this.centered = centered;
        this.titleX = titleX;
        this.titleY = titleY;
        this.day = day;
        this.pictureY = pictureY;
        this.bodyTop = bodyTop;
        this.bodyX = bodyX;
        this.bodyW = bodyW;
        this.indent = indent;
        this.lines = lines;
    }

    /** A number from 0 to range - 1 that comes from the seed alone. Each salt gives a different, unrelated number. */
    static int pick(int seed, int salt, int range) {
        int h = seed ^ salt * 0x9E3779B9;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return Math.floorMod(h, range);
    }

    static EntryLayout of(Entry entry) {
        int seed = entry.id.hashCode();
        int bodyX = 2 + pick(seed, 7, 3), bodyW = RIGHT_EDGE - pick(seed, 8, 3) - bodyX;
        Day day = pick(seed, 2, 2) == 0 ? Day.RIGHT : Day.AFTER;
        if (entry.isLocation()) {
            return new EntryLayout(false, false, 4 + pick(seed, 1, 4), -3, day, 12, 138, bodyX, bodyW, 0, 2);
        }
        if (entry.isSketch() || entry.isDrawing()) {
            boolean sketch = entry.isSketch();
            int bodyTop = sketch ? 112 : 142, lines = sketch ? 4 : 1;
            if (pick(seed, 3, 2) == 0) {
                // the picture at the top, its title written under it
                int titleY = (sketch ? SKETCH_H : DRAWING_H) + (sketch ? 3 : 2);
                return new EntryLayout(true, false, 3 + pick(seed, 1, 9), titleY, Day.AFTER, 0, bodyTop, bodyX, bodyW, 0, lines);
            }
            return new EntryLayout(false, false, 1 + pick(seed, 1, 4), -2, day, 12, bodyTop, bodyX, bodyW, 0, lines);
        }
        boolean centered = entry.isScrapbook() && pick(seed, 4, 2) == 0;
        int indent = Entry.NOTE.equals(entry.type) && pick(seed, 5, 3) == 0 ? 6 : 0;
        return new EntryLayout(false, centered, 1 + pick(seed, 1, 5), -2 - pick(seed, 6, 2), day, 0, 13, bodyX, bodyW, indent, (BOTTOM - 13) / PageEditor.LINE_H);
    }

    /** Where the title starts, for a title this wide. A centered title moves as it grows. */
    int titleX(int width) {
        return centered ? Math.max(1, (PAGE_W - width) / 2) : titleX;
    }

    int titleRoom() {
        return centered ? PAGE_W - 8 : PAGE_W - 4 - titleX;
    }

    boolean hasPicture(Entry entry) {
        return entry.isLocation() || entry.isSketch() || entry.isDrawing();
    }

    /** The picture's box on the first page: x0, y0, x1, y1. */
    int[] picture(Entry entry) {
        int h = entry.isLocation() ? 104 : entry.isSketch() ? SKETCH_H : DRAWING_H;
        return new int[]{0, pictureY, PAGE_W - 1, pictureY + h};
    }

    int top(boolean firstPage) {
        return firstPage ? bodyTop : MORE_TOP;
    }

    int lines(boolean firstPage) {
        return firstPage ? lines : MORE_LINES;
    }
}
