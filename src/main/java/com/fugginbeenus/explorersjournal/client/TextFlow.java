package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/** Lays writing out on an entry's pages, by the entry's layout. A row is cut only where something taped down, or its note, really is, so the writing hugs its shape on both sides. */
final class TextFlow {
    /** Letters begin to end go on this row of this page of the entry, x pixels from the page's left edge. A row can hold more than one line. */
    record Line(int begin, int end, int page, int row, int x) {}

    private record Slot(int page, int row, int x, int width, boolean wholeRow) {}

    // writing keeps further from a note than from a thing, so the two never read as one line
    private static final int MARGIN = 2, NOTE_MARGIN = 6, MIN_WIDTH = 20, TEXT_HEIGHT = 9, MOST_LINES = 64;

    private TextFlow() {}

    static List<Line> layOut(Font font, String text, Entry entry) {
        EntryLayout at = EntryLayout.of(entry);
        int seed = entry.id.hashCode();
        List<Line> lines = new ArrayList<>();
        int start = 0;
        boolean more = true;
        for (int page = 0; more; page++) {
            for (Slot slot : slots(font, entry, at, page)) {
                int[] piece = fit(font, text, start, slot.width(), slot.wholeRow(), seed, lines.size());
                if (piece == null) continue;
                lines.add(new Line(start, piece[0], slot.page(), slot.row(), slot.x()));
                start = piece[1];
                // text that ends in a line break or a wrapped space has one more, empty line for the caret
                more = start < text.length() || piece[0] < piece[1];
                if (!more) break;
            }
        }
        return lines;
    }

    /** A short note in a box of its own: rows of one width, from x 0. */
    static List<Line> wrap(Font font, String text, int width, int seed) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        boolean more = true;
        while (more && lines.size() < MOST_LINES) {
            int[] piece = fit(font, text, start, width, true, seed, lines.size());
            lines.add(new Line(start, piece[0], 0, lines.size(), 0));
            start = piece[1];
            more = start < text.length() || piece[0] < piece[1];
        }
        return lines;
    }

    /** The free pieces of each row on this page, left to right. */
    private static List<Slot> slots(Font font, Entry entry, EntryLayout at, int page) {
        boolean first = page == 0;
        int count = at.lines(first), top = at.top(first);
        List<Captions.Box> notes = Captions.place(font, entry, page);
        List<Slot> slots = new ArrayList<>();
        for (int row = 0; row < count; row++) {
            int y = top + row * PageEditor.LINE_H;
            int from = at.bodyX + (first && row == 0 ? at.indent : 0), to = at.bodyX + at.bodyW;
            boolean[] taken = new boolean[EntryLayout.PAGE_W + 1];
            for (Keepsake keepsake : entry.keepsakes) {
                if (keepsake.page == page) take(taken, KeepsakeArt.covered(keepsake, y - 1, y + TEXT_HEIGHT + 1), MARGIN);
            }
            for (Captions.Box note : notes) take(taken, note.covered(y - 1, y + TEXT_HEIGHT + 1), NOTE_MARGIN);
            int run = -1;
            for (int x = from; x <= to; x++) {
                boolean free = x < to && !taken[x];
                if (free && run < 0) run = x;
                if (!free && run >= 0) {
                    if (x - run >= MIN_WIDTH) slots.add(new Slot(page, row, run, x - run, run == from && x == to));
                    run = -1;
                }
            }
        }
        return slots;
    }

    private static void take(boolean[] taken, int[] covered, int margin) {
        if (covered == null) return;
        int stop = Math.min(taken.length, covered[1] + margin);
        for (int x = Math.max(0, covered[0] - margin); x < stop; x++) taken[x] = true;
    }

    /** How much text from start fits in this width, by vanilla's line rule, counting the hand drift. Returns the line's end and where the next line starts, or null if the next word cannot fit this short piece of a row. */
    private static int[] fit(Font font, String text, int start, int width, boolean wholeRow, int seed, int line) {
        int used = 0, word = 0, lastSpace = -1;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') return new int[]{i, i + 1};
            used += font.width(String.valueOf(c));
            if (c == ' ') {
                lastSpace = i;
                used += Drift.gap(seed, line, word++);
            }
            if (used > width && i > start) {
                if (lastSpace > start || (lastSpace == start && wholeRow)) return new int[]{lastSpace, lastSpace + 1};
                return wholeRow ? new int[]{i, i} : null;
            }
        }
        return new int[]{text.length(), text.length()};
    }
}
