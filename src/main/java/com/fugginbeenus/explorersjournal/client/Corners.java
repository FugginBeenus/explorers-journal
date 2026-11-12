package com.fugginbeenus.explorersjournal.client;

import java.util.List;

/**
 * The folded corners of the top pages, at the two most worn stages. Nobody writes on a folded corner, so a title or a line near one starts past it, or stops before it.
 * A left page has its fold at the top left. A right page has its fold at the bottom right. The numbers are page pixels, read off the page art by scripts/corners.py.
 */
final class Corners {
    /** Where an entry's first page lies in the open book, and how worn the book is. The entry's pages after it go right, left, right. */
    record Where(boolean startsLeft, int stage) {
        static final Where FLAT = new Where(true, 0);

        boolean left(int entryPage) {
            return startsLeft == (entryPage % 2 == 0);
        }
    }

    /** Writing stays this far from a fold. */
    static final int CLEAR = 2;
    private static final int TOP_FIRST = -4, BOTTOM_FIRST = 140;
    // for each row, how many pixels the fold covers from the left edge of a left page
    private static final int[] TOP_LEFT_3 = {5, 7, 7, 7, 8, 8, 8, 7, 7, 3, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] TOP_LEFT_4 = {12, 13, 14, 14, 14, 14, 15, 15, 15, 13, 10, 6, 3, 0, 0, 0, 0};
    // for each row, the first pixel of a right page that the fold covers
    private static final int[] BOTTOM_RIGHT_3 = {129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 122, 118, 115, 113, 113, 113, 114, 114, 114};
    private static final int[] BOTTOM_RIGHT_4 = {129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 129, 123, 120, 117, 114, 112, 113, 112, 112, 112, 113, 113, 113};

    private Corners() {}

    /** Where writing on rows y0 to y1 of this page can start, clear of a fold. 0 when no fold is in the way. */
    static int start(boolean leftPage, int stage, int y0, int y1) {
        int[] rows = !leftPage ? null : stage == 3 ? TOP_LEFT_3 : stage >= 4 ? TOP_LEFT_4 : null;
        if (rows == null) return 0;
        int most = 0;
        for (int y = Math.max(y0, TOP_FIRST); y <= y1 && y - TOP_FIRST < rows.length; y++) most = Math.max(most, rows[y - TOP_FIRST]);
        return most == 0 ? 0 : most + CLEAR;
    }

    /** Where writing on rows y0 to y1 of this page has to stop, clear of a fold. The page's width when no fold is in the way. */
    static int stop(boolean leftPage, int stage, int y0, int y1) {
        int[] rows = leftPage ? null : stage == 3 ? BOTTOM_RIGHT_3 : stage >= 4 ? BOTTOM_RIGHT_4 : null;
        int least = EntryLayout.PAGE_W;
        if (rows == null) return least;
        for (int y = Math.max(y0, BOTTOM_FIRST); y <= y1 && y - BOTTOM_FIRST < rows.length; y++) least = Math.min(least, rows[y - BOTTOM_FIRST]);
        return least == EntryLayout.PAGE_W ? least : least - CLEAR;
    }

    /** The fold on this page as one box, x0, y0, x1, y1, with room around it. Notes beside things keep out of it. */
    static List<int[]> boxes(boolean leftPage, int stage) {
        int top = Integer.MAX_VALUE, bottom = Integer.MIN_VALUE;
        for (int y = TOP_FIRST; y < BOTTOM_FIRST + BOTTOM_RIGHT_4.length; y++) {
            if (start(leftPage, stage, y, y) == 0 && stop(leftPage, stage, y, y) == EntryLayout.PAGE_W) continue;
            top = Math.min(top, y);
            bottom = Math.max(bottom, y);
        }
        if (top > bottom) return List.of();
        int x0 = leftPage ? 0 : stop(false, stage, top, bottom), x1 = leftPage ? start(true, stage, top, bottom) : EntryLayout.PAGE_W;
        return List.of(new int[]{x0, top - CLEAR, x1, bottom + 1 + CLEAR});
    }
}
