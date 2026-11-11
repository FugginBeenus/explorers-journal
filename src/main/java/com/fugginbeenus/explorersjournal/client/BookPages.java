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
    int placesPage, blankPage;

    /** flows keeps the lines of entries that did not change, so typing only lays out the note you are writing. */
    BookPages(Journal journal, Font font, Entry editing, List<TextFlow.Line> editingLines, Map<Entry, List<TextFlow.Line>> flows, String query, double px, double pz, String dim) {
        List<Entry> entries = new ArrayList<>(journal.entries);
        if (editing != null && !entries.contains(editing)) entries.add(editing);

        // entry pages first, so the lists can give page numbers
        List<Page> entryPages = new ArrayList<>();
        int printed = 1;
        for (Entry entry : entries) {
            boolean writing = entry == editing && editingLines != null;
            List<TextFlow.Line> flow = writing ? editingLines : flows.computeIfAbsent(entry, e -> TextFlow.layOut(font, e.text, e));
            lines.put(entry, flow);
            int lastPage = writing || !entry.text.isEmpty() ? flow.get(flow.size() - 1).page() : 0;
            for (Keepsake keepsake : entry.keepsakes) lastPage = Math.max(lastPage, keepsake.page);
            number.put(entry, printed);
            int line = 0;
            for (int at = 0; at <= lastPage; at++) {
                Page page = new Page(Kind.ENTRY);
                page.entry = entry;
                page.firstOfEntry = at == 0;
                page.entryPage = at;
                page.firstLine = line;
                while (line < flow.size() && flow.get(line).page() == at) line++;
                page.lineCount = line - page.firstLine;
                page.number = printed++;
                entryPages.add(page);
            }
        }

        addContents(entries);
        placesPage = pages.size();
        addPlaces(journal, entries, query, px, pz, dim);
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

    private void addContents(List<Entry> entries) {
        List<Row> rows = new ArrayList<>();
        for (Section section : Section.values()) {
            boolean heading = false;
            for (Entry entry : entries) {
                if (Section.of(entry) != section) continue;
                if (!heading) {
                    rows.add(new Row("", section.title, Integer.toString(number.get(entry)), 0, entry));
                    heading = true;
                }
                rows.add(new Row("", displayTitle(entry), Integer.toString(number.get(entry)), 1, entry));
            }
        }
        boolean first = true;
        int i = 0;
        do {
            Page page = new Page(Kind.CONTENTS);
            page.firstOfKind = first;
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
            page.rows = part;
            pages.add(page);
            first = false;
        } while (i < rows.size());
    }

    private void addPlaces(Journal journal, List<Entry> entries, String query, double px, double pz, String dim) {
        List<Row> rows = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            for (Entry entry : entries) {
                if (matches(entry, q)) {
                    rows.add(new Row(Long.toString(entry.day), displayTitle(entry), "p." + number.get(entry), 1, entry));
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
        boolean first = true;
        int i = 0;
        do {
            Page page = new Page(Kind.PLACES);
            page.firstOfKind = first;
            page.rows = new ArrayList<>(rows.subList(i, Math.min(i + PLACE_ROWS, rows.size())));
            pages.add(page);
            i += PLACE_ROWS;
            first = false;
        } while (i < rows.size());
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

    int pageOf(Entry entry) {
        Integer page = firstPage.get(entry);
        return page == null ? -1 : page;
    }
}
