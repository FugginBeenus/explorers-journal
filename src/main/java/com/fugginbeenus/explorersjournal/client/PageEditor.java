package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Vanilla's text field does the editing keys, with its own wrapping turned off. This class lays out the lines, so the writing sits right on the paper, runs over several pages, and goes around what is taped down. Titles and the find line use it too, on one line. */
final class PageEditor extends MultilineTextField {
    static final int LINE_H = 10;
    static final int TITLE_LINE = 1000;
    private static final int NO_WRAP = 100000;
    private static final int BLINK_MS = 500, DOUBLE_CLICK_MS = 250, MAX_UNDO = 200;
    private static final int EDIT_TYPE = 0, EDIT_DELETE = 1, EDIT_OTHER = 2;

    private record Snapshot(String text, int cursor) {}

    private final Font font;
    private final int seed, firstLine, boxLines, limitWidth;
    private final boolean bold, oneLine;
    private final Deque<Snapshot> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private int maxWidth;
    private Entry entry;
    private Corners.Where where = Corners.Where.FLAT;
    private List<TextFlow.Line> lines = List.of(new TextFlow.Line(0, 0, 0, 0, 0));
    private int lastEdit = EDIT_OTHER;
    private int lastClickIndex = -1;
    private long lastClick, lastInput;

    private PageEditor(Font font, String text, int maxChars, int seed, int firstLine, boolean bold, boolean oneLine, int maxWidth, int boxLines, int limitWidth, Entry entry) {
        super(font, NO_WRAP);
        this.font = font;
        this.seed = seed;
        this.firstLine = firstLine;
        this.bold = bold;
        this.oneLine = oneLine;
        this.maxWidth = maxWidth;
        this.boxLines = boxLines;
        this.limitWidth = limitWidth;
        this.entry = entry;
        setCharacterLimit(maxChars);
        setValue(text);
        layOut();
    }

    static PageEditor body(Font font, String text, int maxChars, int seed, Entry entry, Corners.Where where) {
        PageEditor body = new PageEditor(font, text, maxChars, seed, 0, false, false, 0, 0, 0, entry);
        body.flowOn(entry, where);
        return body;
    }

    /** A heading is bold. A title written under a picture is plain. */
    static PageEditor title(Font font, String text, int maxChars, int seed, int maxWidth, boolean bold) {
        return new PageEditor(font, text, maxChars, seed, TITLE_LINE, bold, true, maxWidth, 0, 0, null);
    }

    static PageEditor findLine(Font font, String text, int maxChars, int maxWidth) {
        return new PageEditor(font, text, maxChars, 0, 0, false, true, maxWidth, 0, 0, null);
    }

    /** A short note beside a thing. It wraps to its box. How much it holds does not depend on the box: it stops when it would fill this many lines of limitWidth, so where it sits never cuts what you write. */
    static PageEditor note(Font font, String text, int maxChars, int seed, int width, int lines, int limitWidth) {
        return new PageEditor(font, text, maxChars, seed, 0, false, false, width, lines, limitWidth, null);
    }

    /** Lays the writing out again, when something on its page changed. */
    void flowOn(Entry entry) {
        flowOn(entry, where);
    }

    /** Lays the writing out again, where its pages now are in the book. */
    void flowOn(Entry entry, Corners.Where where) {
        this.entry = entry;
        this.where = where;
        layOut();
    }

    Corners.Where where() {
        return where;
    }

    /** A note's box can change when its thing moves. */
    void boxWidth(int width) {
        if (width == maxWidth) return;
        maxWidth = width;
        layOut();
    }

    private void layOut() {
        String text = value();
        if (entry != null) lines = TextFlow.layOut(font, text, entry, where);
        else if (boxLines > 0) lines = TextFlow.wrap(font, text, maxWidth, seed);
        else lines = List.of(new TextFlow.Line(0, text.length(), 0, 0, 0));
    }

    List<TextFlow.Line> lines() {
        return lines;
    }

    void toEnd() {
        setSelecting(false);
        seekCursor(Whence.END, 0);
    }

    int caretLine() {
        for (int i = 0; i < lines.size(); i++) {
            TextFlow.Line line = lines.get(i);
            if (cursor() >= line.begin() && cursor() <= line.end()) return i;
        }
        return lines.size() - 1;
    }

    @Override
    public void seekCursor(Whence whence, int position) {
        lastEdit = EDIT_OTHER;
        lastInput = Util.getMillis();
        super.seekCursor(whence, position);
    }

    /** Up and Down go to the nearest line on the next row, in the same place across the page. */
    @Override
    public void seekCursorLine(int offset) {
        if (offset == 0) return;
        int from = caretLine();
        TextFlow.Line now = lines.get(from);
        int across = now.x() + xIn(from, cursor());
        int step = offset < 0 ? -1 : 1;
        int i = from + step;
        while (i >= 0 && i < lines.size() && sameRow(lines.get(i), now)) i += step;
        if (i < 0 || i >= lines.size()) return;
        TextFlow.Line row = lines.get(i);
        int best = i, bestGap = Integer.MAX_VALUE;
        for (; i >= 0 && i < lines.size() && sameRow(lines.get(i), row); i += step) {
            int gap = Math.abs(lines.get(i).x() + xIn(i, Math.min(lines.get(i).end(), lines.get(i).begin() + 1)) - across);
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        seekCursor(Whence.ABSOLUTE, indexAt(best, across));
    }

    private static boolean sameRow(TextFlow.Line a, TextFlow.Line b) {
        return a.page() == b.page() && a.row() == b.row();
    }

    private String textOf(int i) {
        TextFlow.Line line = lines.get(i);
        return value().substring(line.begin(), line.end());
    }

    private int xIn(int i, int index) {
        return Drift.xAt(font, textOf(i), index - lines.get(i).begin(), seed, firstLine + i, bold);
    }

    @Override
    public void insertText(String text) {
        if (oneLine) text = fitOnLine(text.replace("\r\n", " ").replace('\n', ' '));
        if (boxLines > 0) text = fitInBox(text.replace("\r\n", " ").replace('\n', ' '));
        if (text.isEmpty() && !hasSelection()) return;
        remember(text);
        lastInput = Util.getMillis();
        super.insertText(text);
        layOut();
    }

    private String fitOnLine(String text) {
        StringView selected = getSelected();
        String kept = value().substring(0, selected.beginIndex()) + value().substring(selected.endIndex());
        while (!text.isEmpty() && Drift.width(font, kept + text, bold) > maxWidth) text = text.substring(0, text.length() - 1);
        return text;
    }

    private String fitInBox(String text) {
        StringView selected = getSelected();
        String before = value().substring(0, selected.beginIndex()), after = value().substring(selected.endIndex());
        while (!text.isEmpty() && TextFlow.wrap(font, before + text + after, limitWidth, seed).size() > boxLines) text = text.substring(0, text.length() - 1);
        return text;
    }

    /** Letters typed in a row undo together, and so do letters deleted in a row. */
    private void remember(String text) {
        StringView selected = getSelected();
        int length = selected.endIndex() - selected.beginIndex();
        boolean oneLetter = text.length() == 1 && length == 0;
        boolean gap = oneLetter && Character.isWhitespace(text.charAt(0));
        int kind = oneLetter && !gap ? EDIT_TYPE : text.isEmpty() && length == 1 ? EDIT_DELETE : EDIT_OTHER;
        if (kind == EDIT_OTHER || kind != lastEdit) {
            undo.push(new Snapshot(value(), cursor()));
            if (undo.size() > MAX_UNDO) undo.removeLast();
        }
        redo.clear();
        lastEdit = gap ? EDIT_TYPE : kind;
    }

    private void step(Deque<Snapshot> from, Deque<Snapshot> to) {
        if (from.isEmpty()) return;
        to.push(new Snapshot(value(), cursor()));
        Snapshot back = from.pop();
        setValue(back.text());
        layOut();
        setSelecting(false);
        seekCursor(Whence.ABSOLUTE, back.cursor());
    }

    @Override
    public boolean keyPressed(int key) {
        lastInput = Util.getMillis();
        if (Screen.hasControlDown() && !Screen.hasAltDown()) {
            if (key == GLFW.GLFW_KEY_Z) {
                if (Screen.hasShiftDown()) step(redo, undo);
                else step(undo, redo);
                return true;
            }
            if (key == GLFW.GLFW_KEY_Y) {
                step(redo, undo);
                return true;
            }
        }
        if ((oneLine || boxLines > 0) && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) return false;
        if ((key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) && !Screen.hasControlDown()) {
            TextFlow.Line line = lines.get(caretLine());
            setSelecting(Screen.hasShiftDown());
            seekCursor(Whence.ABSOLUTE, key == GLFW.GLFW_KEY_HOME ? line.begin() : line.end());
            return true;
        }
        return super.keyPressed(key);
    }

    /** dx is from the start of the writing area. */
    private int indexAt(int i, double dx) {
        i = Math.max(0, Math.min(i, lines.size() - 1));
        TextFlow.Line line = lines.get(i);
        int drift = firstLine + i;
        return line.begin() + Drift.indexAt(font, textOf(i), dx - line.x() - Drift.shift(seed, drift), seed, drift, bold);
    }

    /** One click puts the caret there, and shift stretches the selection. A second quick click takes the word, a third takes it all. Returns true if dragging from here should select. */
    boolean click(int line, double dx) {
        int index = indexAt(line, dx);
        long now = Util.getMillis();
        boolean again = index == lastClickIndex && now - lastClick < DOUBLE_CLICK_MS;
        lastClick = now;
        lastClickIndex = index;
        if (again && !Screen.hasShiftDown()) {
            if (hasSelection()) select(0, value().length());
            else selectWord(index);
            return false;
        }
        setSelecting(Screen.hasShiftDown());
        seekCursor(Whence.ABSOLUTE, index);
        setSelecting(false);
        return true;
    }

    void dragTo(int line, double dx) {
        setSelecting(true);
        seekCursor(Whence.ABSOLUTE, indexAt(line, dx));
        setSelecting(false);
    }

    private void selectWord(int index) {
        String s = value();
        int a = index, b = index;
        while (a > 0 && !Character.isWhitespace(s.charAt(a - 1))) a--;
        while (b < s.length() && !Character.isWhitespace(s.charAt(b))) b++;
        select(a, b);
    }

    private void select(int a, int b) {
        setSelecting(false);
        seekCursor(Whence.ABSOLUTE, a);
        setSelecting(true);
        seekCursor(Whence.ABSOLUTE, b);
        setSelecting(false);
    }

    /** Draws these lines. Each page of a long note draws its own part. */
    void drawLines(GuiGraphics ctx, int x, int y, int first, int count, int colour, boolean focused) {
        int caretLine = caretLine();
        boolean showCaret = focused && (Util.getMillis() - lastInput) / BLINK_MS % 2 == 0;
        StringView sel = getSelected();
        for (int i = first; i < Math.min(first + count, lines.size()); i++) {
            TextFlow.Line line = lines.get(i);
            int a = line.begin(), b = line.end();
            String s = textOf(i);
            int drift = firstLine + i;
            int lx = x + line.x() + Drift.shift(seed, drift), ly = y + line.row() * LINE_H;
            if (focused && hasSelection() && sel.beginIndex() < b && sel.endIndex() > a) {
                int x0 = Drift.xAt(font, s, Math.max(a, sel.beginIndex()) - a, seed, drift, bold);
                int x1 = Drift.xAt(font, s, Math.min(b, sel.endIndex()) - a, seed, drift, bold);
                ctx.fill(lx + x0, ly - 1, lx + x1, ly + 9, EJGui.SELECTION);
            }
            Drift.draw(ctx, font, s, lx, ly, colour, seed, drift, bold);
            if (showCaret && i == caretLine) {
                int cx = Drift.xAt(font, s, cursor() - a, seed, drift, bold);
                int cy = ly + Drift.yAt(cx, seed, drift);
                ctx.fill(lx + cx, cy - 1, lx + cx + 1, cy + 9, colour);
            }
        }
    }
}
