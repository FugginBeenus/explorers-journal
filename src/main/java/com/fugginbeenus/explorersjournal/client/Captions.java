package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The short note beside each thing taped down. The book places it for you, the way a person would: beside the thing where there is room, or else under or over it, narrower if it must squeeze in.
 * On a crowded page it goes in the nearest clear gap, with a small arrow to its thing. It keeps a little clear of the title, the other things, and their notes, so two notes never read as one.
 */
final class Captions {
    /** A note holds what fits in 3 lines this wide, wherever it sits. */
    static final int MAX_LINES = 3, MAX_CHARS = 80, LIMIT_W = 80;
    static final String HINT = "a note...";
    private static final int GAP = 4, PHOTO_GAP = 11, SIDE_W = LIMIT_W, MIN_SIDE = 34, UNDER_W = 100, CLEAR = 3, FAR = 64, STEP = 2, LINE_H = PageEditor.LINE_H;
    private static final int HARD = 1000, NO_FIT = 400, REMEMBER = 64;

    enum Side { RIGHT, LEFT, BELOW, ABOVE, AWAY }

    /**
     * One note, from the page's top left. Its lines start at x, y and wrap to width. zone is the note and the space between it and its thing, x0, y0, x1, y1, so no writing goes between them.
     * arrow, if there is one, runs from the note to its thing: x0, y0, x1, y1, with the head at the end. shown is what is written, which is cut short when the page has no room for it all.
     */
    record Box(Keepsake keepsake, Side side, int x, int y, int width, List<TextFlow.Line> lines, int[] widths, int[] zone, int[] arrow, String shown) {
        boolean cut() {
            return !shown.equals(text(keepsake));
        }

        int blockWidth() {
            return max(widths);
        }

        /** What the note, its gap, and its arrow cover between two heights, from the page's left edge, or null. */
        int[] covered(int top, int bottom) {
            int[] span = null;
            if (bottom > zone[1] && top < zone[3]) span = new int[]{zone[0], zone[2]};
            if (arrow != null) {
                int ax0 = Math.min(arrow[0], arrow[2]) - 1, ax1 = Math.max(arrow[0], arrow[2]) + 2;
                int ay0 = Math.min(arrow[1], arrow[3]) - 1, ay1 = Math.max(arrow[1], arrow[3]) + 2;
                if (bottom > ay0 && top < ay1) span = span == null ? new int[]{ax0, ax1} : new int[]{Math.min(span[0], ax0), Math.max(span[1], ax1)};
            }
            return span;
        }
    }

    /** Where a note you are writing stays put: its side, the corner it grows from, and its width. */
    private record Pin(String id, Side side, int x, int y, int width) {}

    /** The keepsake whose note you are writing. Its note keeps a place even while it is empty. */
    static String writing;
    private static Pin pin;
    private static final Map<String, List<Box>> KNOWN = new LinkedHashMap<>(REMEMBER, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Box>> eldest) {
            return size() > REMEMBER;
        }
    };

    private Captions() {}

    static String text(Keepsake keepsake) {
        return keepsake.note == null ? "" : keepsake.note;
    }

    static int seed(Keepsake keepsake) {
        return keepsake.id.hashCode() * 31 + 5;
    }

    /**
     * Starts writing a note. It stays where it is now, if it shows. A new note gets its spot once, then stays there while you write.
     * It grows from the side nearest its thing: beside it, from its middle. Under it, from its top. Over it, from its bottom.
     */
    static void startWriting(Font font, Entry entry, Keepsake keepsake) {
        Box now = find(place(font, entry, keepsake.page), keepsake);
        writing = keepsake.id;
        pin = null;
        if (now == null) now = find(place(font, entry, keepsake.page), keepsake);
        if (now != null) pinTo(keepsake, now);
    }

    private static void pinTo(Keepsake keepsake, Box now) {
        int tall = now.widths().length * LINE_H, block = now.blockWidth();
        int x = switch (now.side()) {
            case LEFT -> now.x() + block;
            case BELOW, ABOVE -> now.x() + block / 2;
            default -> now.x();
        };
        int y = switch (now.side()) {
            case RIGHT, LEFT -> now.y() + tall / 2;
            case ABOVE -> now.y() + tall;
            default -> now.y();
        };
        pin = new Pin(keepsake.id, now.side(), x, y, now.width());
    }

    static void stopWriting() {
        writing = null;
        pin = null;
    }

    private static Box find(List<Box> boxes, Keepsake keepsake) {
        for (Box box : boxes) {
            if (box.keepsake().id.equals(keepsake.id)) return box;
        }
        return null;
    }

    /** Every note on this page of the entry, in the order the things were taped down. An earlier note keeps its place when a later one comes. */
    static List<Box> place(Font font, Entry entry, int page) {
        StringBuilder key = new StringBuilder(entry.id).append('|').append(entry.type).append('|').append(page).append('|').append(writing).append('|').append(pin);
        List<Keepsake> here = new ArrayList<>();
        List<int[]> items = new ArrayList<>();
        for (Keepsake keepsake : entry.keepsakes) {
            if (keepsake.page != page) continue;
            int[] b = KeepsakeArt.bounds(keepsake);
            here.add(keepsake);
            items.add(b);
            key.append('|').append(keepsake.id).append(',').append(b[0]).append(',').append(b[1]).append(',').append(b[2]).append(',').append(b[3]).append(',').append(text(keepsake));
        }
        List<Box> known = KNOWN.get(key.toString());
        if (known != null) return known;
        List<Box> out = place(font, entry, page, here, items);
        KNOWN.put(key.toString(), out);
        return out;
    }

    private static List<Box> place(Font font, Entry entry, int page, List<Keepsake> here, List<int[]> items) {
        EntryLayout at = EntryLayout.of(entry);
        List<int[]> hard = new ArrayList<>(), soft = new ArrayList<>();
        // a note keeps clear of the title's row and the day on it
        int top = page > 0 ? EntryLayout.MORE_TOP : at.caption ? 0 : at.titleY + 12 + CLEAR;
        if (page == 0) {
            hard.add(new int[]{0, at.titleY - 1, EntryLayout.PAGE_W, at.titleY + 11});
            // the coordinates and the way back on a place's first page
            if (entry.isLocation()) hard.add(new int[]{0, 114, EntryLayout.PAGE_W, 138});
            if (at.hasPicture(entry)) soft.add(at.picture(entry));
        }
        List<Box> out = new ArrayList<>();
        for (int k = 0; k < here.size(); k++) {
            Keepsake keepsake = here.get(k);
            String text = text(keepsake);
            boolean writingThis = keepsake.id.equals(writing);
            if (text.isEmpty() && !writingThis) continue;
            int[] item = items.get(k);
            Box best;
            if (writingThis) {
                // the note you write stays put while it fits there. If it outgrows its spot, it moves once to one that fits.
                best = pin != null && pin.id().equals(keepsake.id) ? pinned(font, keepsake, text, item) : null;
                if (best == null || clashes(best, hard, items, k)) {
                    best = clear(font, keepsake, text, item, top, k, hard, items, soft);
                    if (best != null) pinTo(keepsake, best);
                }
            } else {
                best = clear(font, keepsake, text, item, top, k, hard, items, soft);
                // with no room for it all, a person writes less: words come off the end until it fits
                String[] words = text.split(" ");
                for (int keep = words.length - 1; keep >= 1 && clashes(best, hard, items, k); keep--) {
                    Box shorter = clear(font, keepsake, String.join(" ", java.util.Arrays.copyOf(words, keep)) + "...", item, top, k, hard, items, soft);
                    if (shorter != null && !clashes(shorter, hard, items, k)) best = shorter;
                }
            }
            if (best == null) continue;
            out.add(best);
            hard.add(best.zone());
            if (best.arrow() != null) hard.add(arrowBox(best.arrow()));
        }
        return out;
    }

    /** The best spot beside the thing, or else the nearest clear gap with an arrow. */
    private static Box clear(Font font, Keepsake keepsake, String text, int[] item, int top, int k, List<int[]> hard, List<int[]> items, List<int[]> soft) {
        Box best = beside(font, keepsake, text, item, top, k, hard, items, soft);
        if (best != null && !clashes(best, hard, items, k)) return best;
        Box away = away(font, keepsake, text, item, top, k, hard, items, soft);
        return away != null ? away : best;
    }

    private static boolean clashes(Box box, List<int[]> hard, List<int[]> items, int k) {
        if (box == null) return true;
        int[] spot = {box.x(), box.y(), box.x() + box.blockWidth(), box.y() + box.widths().length * LINE_H};
        for (int[] b : hard) {
            if (clash(spot, b) > 0) return true;
        }
        for (int i = 0; i < items.size(); i++) {
            if (i != k && clash(spot, items.get(i)) > 0) return true;
        }
        return false;
    }

    /** Beside, under, or over the thing: each side, a few widths, and lined up three ways. The cheapest spot wins. */
    private static Box beside(Font font, Keepsake keepsake, String text, int[] item, int top, int k, List<int[]> hard, List<int[]> items, List<int[]> soft) {
        Box best = null;
        int bestCost = Integer.MAX_VALUE;
        for (Side side : new Side[]{Side.RIGHT, Side.LEFT, Side.BELOW, Side.ABOVE}) {
            int most = side == Side.RIGHT ? Math.min(SIDE_W, EntryLayout.PAGE_W - 2 - (item[2] + GAP))
                    : side == Side.LEFT ? Math.min(SIDE_W, item[0] - GAP - 2) : Math.min(UNDER_W, EntryLayout.PAGE_W - 4);
            // a crowded page gets a narrower note, the way a person squeezes one in
            for (int narrow = 0; narrow < 3; narrow++) {
                int width = most * (4 - narrow) / 4;
                if (width < MIN_SIDE) break;
                List<TextFlow.Line> lines = TextFlow.wrap(font, text, width, seed(keepsake));
                int[] widths = widths(font, text, lines, seed(keepsake));
                int n = widths.length, block = max(widths);
                for (int align = 0; align < 3; align++) {
                    int[] spot = spot(side, align, item, keepsake, top, block, n);
                    if (spot == null) continue;
                    int cost = side.ordinal() * 6 + narrow * 2 + align;
                    for (int[] box : hard) cost += clash(spot, box);
                    for (int i = 0; i < items.size(); i++) {
                        if (i != k) cost += clash(spot, items.get(i));
                    }
                    for (int[] box : soft) cost += area(spot, box) / 8;
                    if (lines.size() > MAX_LINES) cost += NO_FIT;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = box(keepsake, side, spot, item, width, lines, widths, text);
                    }
                }
            }
        }
        return best;
    }

    /** The nearest clear gap on the page, at most FAR pixels from the thing, with a short arrow that crosses nothing. Null if there is none. */
    private static Box away(Font font, Keepsake keepsake, String text, int[] item, int top, int k, List<int[]> hard, List<int[]> items, List<int[]> soft) {
        Box best = null;
        int bestCost = Integer.MAX_VALUE;
        for (int narrow = 0; narrow < 3; narrow++) {
            int width = LIMIT_W * (4 - narrow) / 4;
            List<TextFlow.Line> lines = TextFlow.wrap(font, text, width, seed(keepsake));
            int[] widths = widths(font, text, lines, seed(keepsake));
            int block = max(widths), tall = widths.length * LINE_H;
            for (int y = top; y + tall <= EntryLayout.BOTTOM; y += STEP) {
                for (int x = 2; x + block <= EntryLayout.PAGE_W - 2; x += STEP) {
                    int[] spot = {x, y, x + block, y + tall};
                    int far = gap(spot, item);
                    if (far > FAR || far < CLEAR) continue;
                    int cost = far * 4 + narrow * 6 + (lines.size() > MAX_LINES ? NO_FIT : 0);
                    if (cost >= bestCost || hits(spot, hard, items, -1)) continue;
                    for (int[] box : soft) cost += area(spot, box) / 8;
                    int[] arrow = arrow(spot, item);
                    if (arrow != null && crosses(arrow, hard, items, k)) continue;
                    if (cost < bestCost) {
                        bestCost = cost;
                        best = new Box(keepsake, Side.AWAY, x, y, width, lines, widths, spot, arrow, text);
                    }
                }
            }
        }
        return best;
    }

    private static boolean hits(int[] spot, List<int[]> hard, List<int[]> items, int skip) {
        for (int[] b : hard) {
            if (clash(spot, b) > 0) return true;
        }
        for (int i = 0; i < items.size(); i++) {
            if (i != skip && clash(spot, items.get(i)) > 0) return true;
        }
        return false;
    }

    /** From the note's edge to the thing's edge, a pixel short at each end. Null when they are too close for an arrow. */
    private static int[] arrow(int[] note, int[] item) {
        int icx = (item[0] + item[2]) / 2, icy = (item[1] + item[3]) / 2, ncx = (note[0] + note[2]) / 2, ncy = (note[1] + note[3]) / 2;
        int sx = clamp(icx, note[0], note[2] - 1), sy = clamp(icy, note[1], note[3] - 1);
        int ex = clamp(ncx, item[0], item[2] - 1), ey = clamp(ncy, item[1], item[3] - 1);
        double len = Math.hypot(ex - sx, ey - sy);
        if (len < 8) return null;
        double ux = (ex - sx) / len, uy = (ey - sy) / len;
        return new int[]{(int) Math.round(sx + ux * 2), (int) Math.round(sy + uy * 2), (int) Math.round(ex - ux * 2), (int) Math.round(ey - uy * 2)};
    }

    /** True if the arrow runs over another thing, or over something written. */
    private static boolean crosses(int[] arrow, List<int[]> hard, List<int[]> items, int own) {
        int steps = Math.max(Math.abs(arrow[2] - arrow[0]), Math.abs(arrow[3] - arrow[1]));
        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0 : (double) s / steps;
            int px = (int) Math.round(arrow[0] + (arrow[2] - arrow[0]) * t), py = (int) Math.round(arrow[1] + (arrow[3] - arrow[1]) * t);
            for (int[] b : hard) {
                if (px >= b[0] && px < b[2] && py >= b[1] && py < b[3]) return true;
            }
            for (int i = 0; i < items.size(); i++) {
                int[] b = items.get(i);
                if (i != own && px >= b[0] && px < b[2] && py >= b[1] && py < b[3]) return true;
            }
        }
        return false;
    }

    private static int[] arrowBox(int[] arrow) {
        return new int[]{Math.min(arrow[0], arrow[2]) - 1, Math.min(arrow[1], arrow[3]) - 1, Math.max(arrow[0], arrow[2]) + 2, Math.max(arrow[1], arrow[3]) + 2};
    }

    /** A note you are writing grows from its pinned corner, so it never jumps while you type. */
    private static Box pinned(Font font, Keepsake keepsake, String text, int[] item) {
        List<TextFlow.Line> lines = TextFlow.wrap(font, text, pin.width(), seed(keepsake));
        int[] widths = widths(font, text, lines, seed(keepsake));
        int block = max(widths), tall = widths.length * LINE_H;
        int x = switch (pin.side()) {
            case LEFT -> pin.x() - block;
            case BELOW, ABOVE -> clamp(pin.x() - block / 2, 2, EntryLayout.PAGE_W - 2 - block);
            default -> pin.x();
        };
        int y = switch (pin.side()) {
            case RIGHT, LEFT -> pin.y() - tall / 2;
            case ABOVE -> pin.y() - tall;
            default -> pin.y();
        };
        int[] spot = {x, y, x + block, y + tall};
        if (pin.side() == Side.AWAY) return new Box(keepsake, Side.AWAY, x, y, pin.width(), lines, widths, spot, arrow(spot, item), text);
        return box(keepsake, pin.side(), spot, item, pin.width(), lines, widths, text);
    }

    private static int max(int[] values) {
        int m = 0;
        for (int v : values) m = Math.max(m, v);
        return m;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(v, hi));
    }

    private static int[] widths(Font font, String text, List<TextFlow.Line> lines, int seed) {
        if (text.isEmpty()) return new int[]{font.width(HINT)};
        int[] widths = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            String s = text.substring(lines.get(i).begin(), lines.get(i).end());
            widths[i] = Drift.xAt(font, s, s.length(), seed, i, false);
        }
        return widths;
    }

    /** Where a note of this size goes on this side of the thing: x0, y0, x1, y1 for its lines. Beside it, it is centered on the thing, or lined up with its top or bottom. Under or over it, it is centered, or lined up with its left or right. Null if it cannot go there. */
    private static int[] spot(Side side, int align, int[] item, Keepsake keepsake, int top, int block, int lines) {
        int tall = lines * LINE_H, s0, s1, x0;
        boolean beside = side == Side.RIGHT || side == Side.LEFT;
        if (beside) {
            x0 = side == Side.RIGHT ? item[2] + GAP : item[0] - GAP - block;
            s0 = align == 0 ? (item[1] + item[3]) / 2 - tall / 2 : align == 1 ? item[1] + 2 : item[3] - 2 - tall;
        } else {
            int gap = isPhoto(keepsake) ? PHOTO_GAP : GAP;
            x0 = align == 0 ? (item[0] + item[2]) / 2 - block / 2 : align == 1 ? item[0] : item[2] - block;
            x0 = Math.max(2, Math.min(x0, EntryLayout.PAGE_W - 2 - block));
            s0 = side == Side.BELOW ? item[3] + gap : item[1] - gap - tall;
        }
        s1 = s0 + tall;
        // slide it onto the page, top to bottom
        if (s0 < top) {
            s1 += top - s0;
            s0 = top;
        }
        if (s1 > EntryLayout.BOTTOM) {
            s0 -= s1 - EntryLayout.BOTTOM;
            s1 = EntryLayout.BOTTOM;
        }
        if (s0 < top || x0 < 1 || x0 + block > EntryLayout.PAGE_W - 1) return null;
        // sliding must not push a note under or over onto the thing itself
        if (side == Side.BELOW && s0 < item[3]) return null;
        if (side == Side.ABOVE && s1 > item[1]) return null;
        return new int[]{x0, s0, x0 + block, s1};
    }

    private static boolean isPhoto(Keepsake keepsake) {
        return ExposureCompat.isPhoto(KeepsakeArt.stack(keepsake));
    }

    /** The lines sit in their spot on the side nearest the thing, so a spot kept for a note still being written does not leave a gap. */
    private static Box box(Keepsake keepsake, Side side, int[] spot, int[] item, int width, List<TextFlow.Line> lines, int[] widths, String shown) {
        int tall = widths.length * LINE_H, block = max(widths);
        int x = switch (side) {
            case LEFT -> spot[2] - block;
            case BELOW, ABOVE -> clamp((item[0] + item[2]) / 2 - block / 2, spot[0], Math.max(spot[0], spot[2] - block));
            default -> spot[0];
        };
        int y = switch (side) {
            case RIGHT, LEFT -> (spot[1] + spot[3]) / 2 - tall / 2;
            case ABOVE -> spot[3] - tall;
            default -> spot[1];
        };
        int[] zone = switch (side) {
            case RIGHT -> new int[]{item[2], spot[1] - 1, spot[2], spot[3]};
            case LEFT -> new int[]{spot[0], spot[1] - 1, item[0], spot[3]};
            case BELOW -> new int[]{spot[0], item[3], spot[2], spot[3]};
            default -> new int[]{spot[0], spot[1] - 1, spot[2], item[1]};
        };
        int[] arrow = null;
        if ((side == Side.BELOW || side == Side.ABOVE) && isPhoto(keepsake)) {
            // the arrow stands where the note and the photo overlap, so it touches both
            int lo = Math.max(item[0] + 6, x + 4), hi = Math.min(item[2] - 6, x + max(widths) - 4);
            int ax = lo <= hi ? (lo + hi) / 2 : (item[0] + item[2]) / 2;
            arrow = side == Side.BELOW ? new int[]{ax, spot[1] - 2, ax, item[3] + 2} : new int[]{ax, spot[3], ax, item[1] - 2};
        }
        return new Box(keepsake, side, x, y, width, lines, widths, zone, arrow, shown);
    }

    /** How badly a note's spot runs into something it must stay clear of. */
    private static int clash(int[] spot, int[] box) {
        int[] wide = {box[0] - CLEAR, box[1] - CLEAR, box[2] + CLEAR, box[3] + CLEAR};
        int area = area(spot, wide);
        return area > 0 ? HARD + area : 0;
    }

    /** The empty space between two boxes, the larger of across and down. */
    private static int gap(int[] a, int[] b) {
        int dx = Math.max(0, Math.max(a[0] - b[2], b[0] - a[2])), dy = Math.max(0, Math.max(a[1] - b[3], b[1] - a[3]));
        return Math.max(dx, dy);
    }

    private static int area(int[] a, int[] b) {
        int w = Math.min(a[2], b[2]) - Math.max(a[0], b[0]), h = Math.min(a[3], b[3]) - Math.max(a[1], b[1]);
        return w > 0 && h > 0 ? w * h : 0;
    }
}
