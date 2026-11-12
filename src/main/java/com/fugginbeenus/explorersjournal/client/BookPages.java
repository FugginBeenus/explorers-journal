package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lays the journal out as pages: contents, the place list, every entry in the order it was written, then the next blank page. */
final class BookPages {
    enum Kind { CONTENTS, PLACES, ENTRY, BLANK, EMPTY }

    private static final int TOC_ROWS_HEIGHT = 139, TOC_ROW = 11, PLACE_ROWS = 9;

    /** One line of a list. */
    record Row(String day, String name, String far, int level, Entry target) {}

    static final class Page {
        final Kind kind;
        List<Row> rows = List.of();
        boolean firstOfKind;
        Entry entry;
        boolean firstOfEntry;
        int entryPage;
        int firstLine, lineCount, number;

        Page(Kind kind) {
            this.kind = kind;
        }
    }

    final List<Page> pages = new ArrayList<>();
    private final Map<Entry, Integer> firstPage = new IdentityHashMap<>();
    private final Map<Entry, Integer> number = new IdentityHashMap<>();
    private final Map<Entry, List<TextFlow.Line>> lines = new IdentityHashMap<>();
    private final Map<Entry, Corners.Where> where = new IdentityHashMap<>();
    int placesPage, blankPage;

    /** flows keeps the lines of entries that did not change, so typing only lays out the note you are writing. stage is how worn the book is, for its folded corners. */
    BookPages(Journal journal, Font font, Entry editing, List<TextFlow.Line> editingLines, Map<TextFlow.Key, List<TextFlow.Line>> flows, String query, double px, double pz, String dim, int stage) {
        List<Entry> entries = new ArrayList<>(journal.entries);
        if (editing != null && !entries.contains(editing)) entries.add(editing);

        // the lists come first. How many pages they fill does not depend on the page numbers in them, so each entry knows its side of the book before it is laid out
        List<List<Row>> contents = contentsPages(entries, entry -> "");
        List<List<Row>> places = placesPages(entries, query, px, pz, dim, entry -> "");
        int at = contents.size() + places.size();

        List<Page> entryPages = new ArrayList<>();
        int printed = 1;
        for (Entry entry : entries) {
            Corners.Where side = new Corners.Where(at % 2 == 0, stage);
            where.put(entry, side);
            boolean writing = entry == editing && editingLines != null;
            List<TextFlow.Line> flow = writing ? editingLines : flows.computeIfAbsent(new TextFlow.Key(entry, side), k -> TextFlow.layOut(font, entry.text, entry, side));
            lines.put(entry, flow);
            int lastPage = writing || !entry.text.isEmpty() ? flow.get(flow.size() - 1).page() : 0;
            for (Keepsake keepsake : entry.keepsakes) lastPage = Math.max(lastPage, keepsake.page);
            number.put(entry, printed);
            int line = 0;
            for (int page = 0; page <= lastPage; page++) {
                Page p = new Page(Kind.ENTRY);
                p.entry = entry;
                p.firstOfEntry = page == 0;
                p.entryPage = page;
                p.firstLine = line;
                while (line < flow.size() && flow.get(line).page() == page) line++;
                p.lineCount = line - p.firstLine;
                p.number = printed++;
                entryPages.add(p);
            }
            at += lastPage + 1;
        }

        java.util.function.Function<Entry, String> numberOf = entry -> Integer.toString(number.get(entry));
        addPages(Kind.CONTENTS, contentsPages(entries, numberOf));
        placesPage = pages.size();
        addPages(Kind.PLACES, placesPages(entries, query, px, pz, dim, numberOf));
        int front = pages.size();
        pages.addAll(entryPages);
        for (int i = front; i < pages.size(); i++) {
            Page page = pages.get(i);
            if (page.firstOfEntry) firstPage.put(page.entry, i);
        }
        blankPage = pages.size();
        pages.add(new Page(Kind.BLANK));
        if (pages.size() % 2 == 1) pages.add(new Page(Kind.EMPTY));
    }

    private void addPages(Kind kind, List<List<Row>> parts) {
        boolean first = true;
        for (List<Row> part : parts) {
            Page page = new Page(kind);
            page.firstOfKind = first;
            page.rows = part;
            pages.add(page);
            first = false;
        }
    }

    /** The contents, split into pages. There is always at least one page, even with nothing in it. */
    private static List<List<Row>> contentsPages(List<Entry> entries, java.util.function.Function<Entry, String> numberOf) {
        List<Row> rows = new ArrayList<>();
        for (Section section : Section.values()) {
            boolean heading = false;
            for (Entry entry : entries) {
                if (Section.of(entry) != section) continue;
                if (!heading) {
                    rows.add(new Row("", section.title, numberOf.apply(entry), 0, entry));
                    heading = true;
                }
                rows.add(new Row("", displayTitle(entry), numberOf.apply(entry), 1, entry));
            }
        }
        List<List<Row>> parts = new ArrayList<>();
        int i = 0;
        do {
            List<Row> part = new ArrayList<>();
            int used = 0;
            while (i < rows.size()) {
                Row row = rows.get(i);
                int h = TOC_ROW + (row.level() == 0 && !part.isEmpty() ? 2 : 0);
                if (used + h > TOC_ROWS_HEIGHT) break;
                part.add(row);
                used += h;
                i++;
            }
            parts.add(part);
        } while (i < rows.size());
        return parts;
    }

    /** The place list, or what find found, split into pages. There is always at least one page. */
    private static List<List<Row>> placesPages(List<Entry> entries, String query, double px, double pz, String dim, java.util.function.Function<Entry, String> numberOf) {
        List<Row> rows = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            for (Entry entry : entries) {
                if (matches(entry, q)) {
                    rows.add(new Row(Long.toString(entry.day), displayTitle(entry), "p." + numberOf.apply(entry), 1, entry));
                }
            }
        } else {
            List<Entry> places = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.isLocation()) places.add(entry);
            }
            places.sort(Comparator.comparingDouble(e -> sortDistance(e, px, pz, dim)));
            for (Entry entry : places) {
                rows.add(new Row(Long.toString(entry.day), displayTitle(entry), far(entry, px, pz, dim), 1, entry));
            }
        }
        List<List<Row>> parts = new ArrayList<>();
        int i = 0;
        do {
            parts.add(new ArrayList<>(rows.subList(i, Math.min(i + PLACE_ROWS, rows.size()))));
            i += PLACE_ROWS;
        } while (i < rows.size());
        return parts;
    }

    private static boolean matches(Entry entry, String q) {
        if (entry.title.toLowerCase(Locale.ROOT).contains(q) || entry.text.toLowerCase(Locale.ROOT).contains(q)) return true;
        for (Keepsake keepsake : entry.keepsakes) {
            if (KeepsakeArt.name(keepsake).toLowerCase(Locale.ROOT).contains(q)) return true;
            if (Captions.text(keepsake).toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private static double sortDistance(Entry entry, double px, double pz, String dim) {
        if (!dim.equals(entry.dim)) return Double.MAX_VALUE;
        return Math.hypot(entry.x + 0.5 - px, entry.z + 0.5 - pz);
    }

    static String far(Entry entry, double px, double pz, String dim) {
        if (!dim.equals(entry.dim)) {
            String path = entry.dim == null ? "" : entry.dim.substring(entry.dim.indexOf(':') + 1);
            return switch (path) {
                case "overworld" -> "Overworld";
                case "the_nether" -> "Nether";
                case "the_end" -> "End";
                default -> "away";
            };
        }
        double dist = Math.hypot(entry.x + 0.5 - px, entry.z + 0.5 - pz);
        if (dist < 8) return "here";
        return dist < 1000 ? Math.round(dist) + "m" : String.format(Locale.ROOT, "%.1fkm", dist / 1000);
    }

    static String displayTitle(Entry entry) {
        if (!entry.title.isBlank()) return entry.title;
        String first = entry.text.strip();
        int nl = first.indexOf('\n');
        if (nl >= 0) first = first.substring(0, nl);
        if (first.isEmpty() && !entry.keepsakes.isEmpty()) first = KeepsakeArt.name(entry.keepsakes.get(0));
        if (first.isEmpty()) return entry.isSketch() ? "Sketch" : entry.isDrawing() ? "Drawing" : "Untitled";
        return first.length() <= 16 ? first : first.substring(0, 15).stripTrailing() + "...";
    }

    List<TextFlow.Line> linesOf(Entry entry) {
        return lines.get(entry);
    }

    /** Which side of the book an entry starts on, and how worn the book is. */
    Corners.Where whereOf(Entry entry) {
        return where.getOrDefault(entry, Corners.Where.FLAT);
    }

    int pageOf(Entry entry) {
        Integer page = firstPage.get(entry);
        return page == null ? -1 : page;
    }
}
