package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import com.fugginbeenus.explorersjournal.journal.Keepsakes;
import com.fugginbeenus.explorersjournal.journal.MapMark;
import com.fugginbeenus.explorersjournal.compat.LocationTooltipCompat;
import com.fugginbeenus.explorersjournal.map.MapDrawer;
import com.fugginbeenus.explorersjournal.net.EJPayloads;
import com.fugginbeenus.explorersjournal.net.client.EJNetClient;
import com.fugginbeenus.explorersjournal.sketch.PixelCanvas;
import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The open journal. You are reading or you are writing. Page arrows, tabs, and ribbons move you around. Paper slips show while you write, and tools show while you draw. */
public final class JournalScreen extends EJScreen {
    private static final int PAGE_W = 129;
    private static final int TITLE_W = PAGE_W - 8;
    private static final int ARROW_W = 23, ARROW_H = 13, ARROW_Y = 173;
    // by wear stage: the forward arrow sits just clear of the right page's bottom corner, which curls and then folds
    private static final int[] FORWARD_ARROW_X = {270, 270, 264, 255, 254};
    private static final int FEATHER_Y = 171, FEATHER_SIZE = 16;
    private static final int DONE_X = 204, DONE_W = 36, DELETE_X = 246, DELETE_W = 44, PASTE_X = 66, PASTE_W = 40;
    private static final int FIND_Y = 163, FIND_CHARS = 24;
    private static final int TURN_GAP_MS = 250;
    private static final int PICK_COLS = 7, PICK_CELL = 18;
    // where a thing can sit, from the top of the writing area: below the title, above the page arrows, with room for its tilt
    private static final int STICK_TOP = 15, STICK_TOP_MORE = 2, STICK_BOTTOM = 151, STICK_BOTTOM_MAP = 108;
    private static final String FIND_LABEL = "find: ";
    private static final String[] CHOICES = {"Mark this place", "Sketch this view", "Draw something", "Write a note", "Paste something in"};
    private static final int SKETCH_BUDGET_NANOS = 5_000_000, REVEAL_MS = 700;
    private static final int GRAB_SLOP = 3;
    private static final Map<String, Integer> LAST_PAGE = new HashMap<>();
    private static final ItemStack FEATHER = new ItemStack(Items.FEATHER);

    private record Link(int x, int y, int w, int h, Entry target) {}
    private record PinSpot(int x, int y, int w, int h, Entry entry) {}
    /** A place's name on a map: its box on screen, or null when it fits nowhere, and its icon's box. */
    public record MarkName(String name, int[] box, int[] icon) {}
    // a small pin, drawn in red ink beside a pinned place's way back
    private static final String[] PIN = {".XX.", "XXXX", ".XX.", "..X.", "..X.", ".X.."};
    private static final int NAME_X = 8, NAME_W = 52, MARK_NAME_W = 60, MARK_GAP = 3;
    private record Carried(int slot, ItemStack stack, String data, String id) {}
    private record Stuck(Entry entry, Keepsake keepsake, int page) {}
    private record NoteAt(Entry entry, Captions.Box box, int page) {}

    /** A thing taped down that you pressed on. It moves once the mouse goes a few pixels. A plain click writes its note instead. */
    private static final class Grab {
        final Entry entry;
        final Keepsake keepsake;
        final int offX, offY;
        final double startX, startY;
        boolean moving;

        Grab(Entry entry, Keepsake keepsake, int offX, int offY, double startX, double startY) {
            this.entry = entry;
            this.keepsake = keepsake;
            this.offX = offX;
            this.offY = offY;
            this.startX = startX;
            this.startY = startY;
        }
    }

    private Journal journal;
    private boolean canWrite;
    // the server's rules for this book
    private int wear;
    private boolean paperCost;
    private int maxEntries = Integer.MAX_VALUE;
    private BookPages book;
    private int spread;
    private int bookX, bookY, leftX, rightX, pageTop, pageBottom;
    private boolean drawingMap;
    private final List<Link> links = new ArrayList<>();
    private final List<PinSpot> pinSpots = new ArrayList<>();
    private final List<MarkName> markNames = new ArrayList<>();
    private final Map<TextFlow.Key, List<TextFlow.Line>> flows = new java.util.HashMap<>();
    private List<Component> tooltip = List.of();
    private Optional<TooltipComponent> tooltipImage = Optional.empty();
    private List<Integer> pickable;
    private Carried carried;
    private SketchJob sketching;
    private DrawingDesk desk;
    private long revealStart;
    private boolean toViewfinder;
    private double scrollSum;
    private long lastScrollTurn;

    private Entry editEntry;
    private boolean isNew, deleteArmed;
    private String editStartTitle = "", editStartText = "";
    private PageEditor title, body, find, focus, dragging;
    private String query;
    // the note beside a thing, while you write it
    private PageEditor caption;
    private String captionId, captionStart = "";
    private Grab grab;

    public JournalScreen(Journal journal, EJPayloads.OpenJournal rules) {
        super(Component.translatable("item.explorersjournal.journal"));
        this.journal = journal;
        takeRules(rules);
    }

    private void takeRules(EJPayloads.OpenJournal rules) {
        canWrite = rules.canWrite();
        wear = rules.wear();
        paperCost = rules.paperCost();
        maxEntries = rules.maxEntries();
    }

    /** The server takes no more entries in this book. */
    private boolean full() {
        return journal.entries.size() >= maxEntries;
    }

    public Journal journal() {
        return journal;
    }

    public static void clearArt() {
        KeepsakeArt.clear();
        LoosePapers.clear();
    }

    public void receive(Journal journal, EJPayloads.OpenJournal rules) {
        boolean grew = journal.entries.size() > this.journal.entries.size();
        boolean madeMap = drawingMap && grew;
        if (grew) drawingMap = false;
        this.journal = journal;
        takeRules(rules);
        if (editEntry != null) {
            Entry same = journal.entry(editEntry.id);
            if (same != null) {
                editEntry = same;
                isNew = false;
            } else {
                // the server removed it, when the last thing was peeled off, so it is a new draft again
                editEntry.keepsakes.clear();
                isNew = true;
            }
            if (caption != null) {
                // the note you are writing stays as you have it, unless its thing was peeled off
                Keepsake still = editEntry.keepsake(captionId);
                if (still != null) still.note = caption.value();
                else dropCaption();
            }
            body.flowOn(editEntry);
        }
        flows.clear();
        rebuildHere();
        if (madeMap) showPage(book.pageOf(journal.entries.get(journal.entries.size() - 1)));
    }

    @Override
    protected void init() {
        toViewfinder = false;
        bookX = (this.width - EJGui.BOOK_W) / 2;
        bookY = Math.max(8, (this.height - EJGui.BOOK_H - 24) / 2);
        leftX = bookX + 17;
        rightX = bookX + 166;
        pageTop = bookY + 16;
        pageBottom = bookY + EJGui.BOOK_H - 16;
        if (book == null) {
            rebuild();
            showPage(LAST_PAGE.getOrDefault(journal.id, 0));
        }
    }

    private void rebuild() {
        var player = this.minecraft.player;
        if (player == null || this.minecraft.level == null) return;
        String dim = this.minecraft.level.dimension().location().toString();
        String q = find != null ? find.value() : query;
        book = new BookPages(journal, this.font, editEntry, body == null ? null : body.lines(), flows, q, player.getX(), player.getZ(), dim, wear);
        // the note you write moved to the other side of the book, or the book wore: lay it out again for its corners
        if (editEntry != null && body != null && !book.whereOf(editEntry).equals(body.where())) {
            body.flowOn(editEntry, book.whereOf(editEntry));
            book = new BookPages(journal, this.font, editEntry, body.lines(), flows, q, player.getX(), player.getZ(), dim, wear);
        }
        if (editEntry != null && title != null) title.boxWidth(titleRoom(editEntry));
        spread = Math.max(0, Math.min(spread, lastSpread()));
    }

    /** Which side of the book an entry starts on. A new entry starts on the blank page. */
    private Corners.Where whereOf(Entry entry) {
        if (book == null) return Corners.Where.FLAT;
        int page = book.pageOf(entry);
        return page >= 0 ? book.whereOf(entry) : new Corners.Where(book.blankPage % 2 == 0, wear);
    }

    /** Where a title starts, from the page's left edge: by its layout, and past a folded corner. */
    private int titleStart(Entry entry, int width) {
        EntryLayout at = EntryLayout.of(entry);
        Corners.Where where = whereOf(entry);
        return Math.max(at.titleX(width), Corners.start(where.startsLeft(), where.stage(), at.titleY - 1, at.titleY + 10));
    }

    /** How wide a title can be, so it stays on the page and off a folded corner. A centered title that meets a fold starts past it. */
    private int titleRoom(Entry entry) {
        EntryLayout at = EntryLayout.of(entry);
        Corners.Where where = whereOf(entry);
        int fold = Corners.start(where.startsLeft(), where.stage(), at.titleY - 1, at.titleY + 10);
        return Math.min(at.titleRoom(), PAGE_W - 4 - Math.max(fold, at.centered ? 0 : at.titleX));
    }

    /** Stays on the same writing, even if the lists in front grew or shrank. */
    private void rebuildHere() {
        Entry anchor = book == null ? null : book.pages.get(spread * 2).entry;
        int offset = anchor == null ? 0 : spread * 2 - book.pageOf(anchor);
        rebuild();
        Entry keep = anchor == null ? null : journal.entry(anchor.id);
        if (keep != null) showPage(book.pageOf(keep) + offset);
    }

    private int lastSpread() {
        return (book.pages.size() - 1) / 2;
    }

    private void showPage(int page) {
        if (page >= 0) spread = Math.max(0, Math.min(page / 2, lastSpread()));
    }

    private int pageX(int page) {
        return page % 2 == 0 ? leftX : rightX;
    }

    private int pageUnder(double mx) {
        return spread * 2 + (mx >= bookX + EJGui.BOOK_W / 2.0 ? 1 : 0);
    }

    /** What the title shows: while you write it, what you typed. */
    private String titleShown(Entry entry) {
        return entry == editEntry && title != null ? title.value() : fitTitle(entry, entry.title);
    }

    private int titleX(Entry entry, int x) {
        return x + titleStart(entry, titleWidth(entry, titleShown(entry)));
    }

    private int titleY(Entry entry) {
        return pageTop + EntryLayout.of(entry).titleY;
    }

    /** A heading is the row at the top. A title under a picture is its own row. */
    private boolean overTitle(Entry entry, double my) {
        int y = titleY(entry);
        return (!EntryLayout.of(entry).caption || my >= y - 2) && my < y + 12;
    }

    private int bodyTop(BookPages.Page page) {
        return pageTop + EntryLayout.of(page.entry).top(page.firstOfEntry);
    }

    /** The line nearest the mouse. A row can hold a line on each side of something taped down. */
    private int lineAt(BookPages.Page page, int x, double mx, double my) {
        int row = (int) Math.floor((my - bodyTop(page)) / PageEditor.LINE_H);
        double dx = mx - x;
        List<TextFlow.Line> lines = body.lines();
        int best = page.firstLine, bestCost = Integer.MAX_VALUE;
        for (int i = page.firstLine; i < page.firstLine + page.lineCount; i++) {
            TextFlow.Line line = lines.get(i);
            int width = this.font.width(body.value().substring(line.begin(), line.end()));
            int gap = dx < line.x() ? (int) (line.x() - dx) : dx > line.x() + width ? (int) (dx - line.x() - width) : 0;
            int cost = Math.abs(line.row() - row) * 1000 + gap;
            if (cost < bestCost) {
                bestCost = cost;
                best = i;
            }
        }
        return best;
    }

    private static int seed(Entry entry) {
        return entry.id.hashCode();
    }

    private Entry firstEntryOnSpread() {
        if (book == null) return null;
        for (int i = spread * 2; i <= spread * 2 + 1 && i < book.pages.size(); i++) {
            Entry entry = book.pages.get(i).entry;
            if (entry != null) return entry;
        }
        return null;
    }

    private boolean shows(int spreadIndex, Entry entry) {
        for (int i = spreadIndex * 2; i <= spreadIndex * 2 + 1 && i < book.pages.size(); i++) {
            if (book.pages.get(i).entry == entry) return true;
        }
        return false;
    }

    // ---- drawing

    @Override
    protected void ejDraw(GuiGraphics ctx, int mouseX, int mouseY) {
        links.clear();
        pinSpots.clear();
        markNames.clear();
        tooltip = List.of();
        tooltipImage = Optional.empty();
        workOnSketch();
        // any way of leaving a note you were writing keeps it
        if (caption != null && focus != caption) commitCaption();
        int stage = wear;
        EJGui.backdrop(ctx, this.width, this.height);
        EJGui.cover(ctx, stage, bookX, bookY);
        drawRibbons(ctx, mouseX, mouseY);
        for (Section section : Section.values()) {
            if (hasTab(section)) EJGui.tab(ctx, this.font, section, bookX, bookY, overTab(section, mouseX, mouseY));
        }
        if (editEntry != null) drawSlips(ctx, mouseX, mouseY);
        EJGui.askew(ctx, stage, bookX, bookY);
        LoosePapers.draw(ctx, journal, stage, bookX, bookY);
        EJGui.pages(ctx, stage, bookX, bookY);
        int picker = pickable == null ? -1 : pickerPage();
        int menu = menuShown() ? canvasPage() ^ 1 : -1;
        for (int i = spread * 2; i <= spread * 2 + 1; i++) {
            if (i == picker) drawPicker(ctx, pageX(i), mouseX, mouseY);
            else if (i == menu) desk.drawMenu(ctx, this.font, pageX(i), pageTop, mouseX, mouseY, headingSpot(i % 2 == 0));
            else drawPage(ctx, i, mouseX, mouseY);
        }
        if (pickable == null) drawArrows(ctx, mouseX, mouseY);
        if (carried != null) drawCarried(ctx, mouseX, mouseY);
        if (grab != null && grab.moving) drawGrabbed(ctx, mouseX, mouseY);
        int feather = featherPage(mouseX, mouseY);
        if (feather >= 0) {
            // vanilla's feather is nearly white, so it gets dipped in ink to show on the paper
            ctx.setColor(0.55f, 0.42f, 0.3f, 1f);
            ctx.renderItem(FEATHER, featherX(feather), bookY + FEATHER_Y);
            ctx.setColor(1f, 1f, 1f, 1f);
        }
        hoverHints(mouseX, mouseY);
        if (KeepsakeArt.takeNewArt()) {
            flows.clear();
            if (body != null) body.flowOn(editEntry);
            rebuildHere();
        }
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (!tooltip.isEmpty()) ctx.renderTooltip(this.font, tooltip, tooltipImage, mouseX, mouseY);
    }

    private void drawRibbons(GuiGraphics ctx, int mx, int my) {
        boolean marked = !journal.bookmark.isEmpty();
        if (marked || canWrite) {
            // it hangs out when it marks a page, and stays tucked in when it does not
            EJGui.blueRibbon(ctx, bookX + 116, bookY + (marked ? 188 : 176), overBlueRibbon(mx, my) ? 2 : 0);
        }
        EJGui.redRibbon(ctx, bookX + 166, bookY + 188, overRedRibbon(mx, my) ? 2 : 0);
    }

    /** A village nobody has named, with a new name typed in its title: Location Tooltip can give it that name, for everyone. */
    private boolean canNameHere() {
        if (editEntry == null || title == null || !editEntry.isLocation() || !editEntry.nameable || editEntry.place == null) return false;
        String typed = title.value().strip();
        return !typed.isEmpty() && !typed.equals(editEntry.placeName);
    }

    private boolean overNameIt(double mx, double my) {
        return EJGui.over(mx, my, bookX + NAME_X, bookY + 188, NAME_W, EJGui.SLIP_BOTTOM - 186);
    }

    private void drawSlips(GuiGraphics ctx, int mx, int my) {
        Section done = Section.DRAWINGS, delete = Section.SCRAPBOOK;
        if (canNameHere()) {
            Section place = Section.LOCATIONS;
            EJGui.slip(ctx, this.font, "Name it", bookX + NAME_X, bookY, NAME_W, place.paper, place.light, place.dark, overNameIt(mx, my));
        }
        EJGui.slip(ctx, this.font, "Done", bookX + DONE_X, bookY, DONE_W, done.paper, done.light, done.dark, overDone(mx, my));
        EJGui.slip(ctx, this.font, deleteArmed ? "Sure?" : "Delete", bookX + DELETE_X, bookY, DELETE_W, delete.paper, delete.light, delete.dark, overDelete(mx, my));
        EJGui.slip(ctx, this.font, retrying() ? "Retry" : "Paste", bookX + PASTE_X, bookY, PASTE_W, EJGui.TAPE_PAPER, EJGui.TAPE_LIGHT, EJGui.TAPE_DARK, overPaste(mx, my));
    }

    /** A new sketch that is not kept yet can be taken again. */
    private boolean retrying() {
        return isNew && editEntry != null && editEntry.isSketch();
    }

    private int pickerPage() {
        return book.pages.get(spread * 2).entry == editEntry ? spread * 2 + 1 : spread * 2;
    }

    private int canvasPage() {
        return book.pageOf(editEntry);
    }

    private boolean canvasShown() {
        return desk != null && canvasPage() >= 0 && canvasPage() / 2 == spread;
    }

    /** The tools step aside while your note runs onto the page they sit on. */
    private boolean menuShown() {
        return canvasShown() && !(focus == body && caretPage() == (canvasPage() ^ 1));
    }

    private int canvasY() {
        return pageTop + EntryLayout.of(editEntry).pictureY;
    }

    private int cellX(int x, int i) {
        return x + 1 + i % PICK_COLS * PICK_CELL;
    }

    private int cellY(int i) {
        return pageTop + 14 + i / PICK_COLS * PICK_CELL;
    }

    /** The page across from your writing becomes a plain menu of what you carry. */
    private void drawPicker(GuiGraphics ctx, int x, int mx, int my) {
        heading(ctx, "Paste what?", x);
        Inventory inventory = this.minecraft.player.getInventory();
        for (int i = 0; i < pickable.size(); i++) {
            ItemStack stack = inventory.getItem(pickable.get(i));
            int cx = cellX(x, i), cy = cellY(i);
            ctx.renderItem(stack, cx, cy);
            ctx.renderItemDecorations(this.font, stack, cx, cy);
            if (EJGui.over(mx, my, cx, cy, 16, 17)) EJGui.line(ctx, cx, cy + 16, cx + 15, cy + 16, EJGui.INK_RED);
        }
        int y = pageTop + 128;
        String[] hint = pickable.isEmpty() ? new String[]{"You carry nothing", "to paste."}
                : paperCost && paperCount() == 0 ? new String[]{"The tape needs", "1 Paper, and you", "have none."}
                : paperCost ? new String[]{"Pick one, then click", "your page. The tape", "costs 1 Paper."}
                : new String[]{"Pick one, then click", "your page."};
        for (String line : hint) {
            EJGui.write(ctx, this.font, line, x + 2, y, EJGui.INK_FAINT);
            y += PageEditor.LINE_H;
        }
    }

    private void drawCarried(GuiGraphics ctx, int mx, int my) {
        int size = KeepsakeArt.size(carried.stack());
        int target = stickTarget(mx, my);
        int seed = carried.id().hashCode();
        if (target >= 0) {
            int[] at = stickSpot(target, size, mx, my);
            KeepsakeArt.draw(ctx, carried.data(), carried.stack(), seed, pageX(target) + at[0], pageTop + at[1], 1f);
        } else {
            KeepsakeArt.draw(ctx, carried.data(), carried.stack(), seed, mx - size / 2, my - size / 2, 0.5f);
        }
    }

    /** A thing you are moving shows where it would land. Off your pages, it shows faint, and it goes back if you let go there. */
    private void drawGrabbed(GuiGraphics ctx, int mx, int my) {
        Keepsake keepsake = grab.keepsake;
        ItemStack stack = KeepsakeArt.stack(keepsake);
        int size = KeepsakeArt.size(keepsake), seed = keepsake.id.hashCode();
        int cx = mx - grab.offX + size / 2, cy = my - grab.offY + size / 2;
        int target = stickTarget(cx, cy);
        if (target >= 0) {
            int[] at = stickSpot(target, size, cx, cy);
            KeepsakeArt.draw(ctx, keepsake.item, stack, seed, pageX(target) + at[0], pageTop + at[1], 1f);
        } else {
            KeepsakeArt.draw(ctx, keepsake.item, stack, seed, mx - grab.offX, my - grab.offY, 0.5f);
        }
    }

    private boolean isGrabbed(Keepsake keepsake) {
        return grab != null && grab.moving && grab.keepsake.id.equals(keepsake.id);
    }

    /** Your own page, or the blank page right after it if this is your newest entry. */
    private int stickTarget(double mx, double my) {
        int index = pageUnder(mx);
        if (index >= book.pages.size() || !EJGui.over(mx, my, pageX(index) - 4, pageTop - 8, PAGE_W + 8, 176)) return -1;
        BookPages.Page page = book.pages.get(index);
        if (page.entry == editEntry) return index;
        return page.kind == BookPages.Kind.BLANK && book.pages.get(index - 1).entry == editEntry ? index : -1;
    }

    /** Stays on the page and clear of the page arrows. On a place's first page it stays on the map, away from the writing. A title written under a picture stays clear too. mx and my are where its middle would go. */
    private int[] stickSpot(int index, int size, double mx, double my) {
        BookPages.Page page = book.pages.get(index);
        boolean first = page.kind == BookPages.Kind.ENTRY && page.firstOfEntry;
        EntryLayout at = first ? EntryLayout.of(page.entry) : null;
        boolean underPicture = at != null && at.caption;
        int top = first && !underPicture ? STICK_TOP : STICK_TOP_MORE;
        int bottom = first && page.entry.isLocation() ? STICK_BOTTOM_MAP : STICK_BOTTOM;
        int x = (int) mx - pageX(index) - size / 2, y = (int) my - pageTop - size / 2;
        y = Math.max(top, Math.min(y, bottom - size));
        if (underPicture) {
            int t0 = at.titleY - 2, t1 = at.titleY + 11;
            if (y < t1 && y + size > t0) y = y + size / 2 < (t0 + t1) / 2 ? t0 - size : t1;
            y = Math.max(top, Math.min(y, bottom - size));
        }
        return new int[]{Math.max(0, Math.min(x, PAGE_W - size)), y};
    }

    private void drawKeepsakes(GuiGraphics ctx, BookPages.Page page, int x) {
        for (Keepsake keepsake : page.entry.keepsakes) {
            if (keepsake.page == page.entryPage && !isGrabbed(keepsake)) {
                KeepsakeArt.draw(ctx, keepsake.item, KeepsakeArt.stack(keepsake), keepsake.id.hashCode(), x + keepsake.x, pageTop + keepsake.y, 1f);
            }
        }
        drawCaptions(ctx, page, x);
    }

    /** The short note beside each thing, in lighter ink than the writing. Under or over a photo, a small arrow points at it. */
    private void drawCaptions(GuiGraphics ctx, BookPages.Page page, int x) {
        for (Captions.Box box : Captions.place(this.font, page.entry, page.entryPage, book.whereOf(page.entry))) {
            Keepsake keepsake = box.keepsake();
            if (isGrabbed(keepsake)) continue;
            int bx = x + box.x(), by = pageTop + box.y(), seed = Captions.seed(keepsake);
            if (caption != null && page.entry == editEntry && keepsake.id.equals(captionId)) {
                caption.boxWidth(box.width());
                if (caption.value().isEmpty()) EJGui.write(ctx, this.font, Captions.HINT, bx, by, EJGui.INK_FAINT);
                caption.drawLines(ctx, bx, by, 0, caption.lines().size(), EJGui.INK_SOFT, focus == caption);
            } else {
                String text = box.shown();
                for (int i = 0; i < box.lines().size(); i++) {
                    TextFlow.Line line = box.lines().get(i);
                    Drift.draw(ctx, this.font, text.substring(line.begin(), line.end()), bx + Drift.shift(seed, i), by + i * PageEditor.LINE_H, EJGui.INK_SOFT, seed, i, false);
                }
            }
            int[] arrow = box.arrow();
            if (arrow != null) EJGui.arrow(ctx, x + arrow[0], pageTop + arrow[1], x + arrow[2], pageTop + arrow[3], EJGui.INK_SOFT);
        }
    }

    /** A page that shows as itself, not covered by the drawing tools or the paste menu. */
    private boolean showsItself(int index) {
        if (index >= book.pages.size()) return false;
        if (menuShown() && index == (canvasPage() ^ 1)) return false;
        return pickable == null || index != pickerPage();
    }

    /** The thing under the mouse, where it really is and not its empty corners, so a note close by still takes its own clicks. The newest one is on top. */
    private Stuck stuckAt(double mx, double my) {
        for (int i = spread * 2 + 1; i >= spread * 2; i--) {
            if (!showsItself(i)) continue;
            BookPages.Page page = book.pages.get(i);
            if (page.entry == null) continue;
            List<Keepsake> list = page.entry.keepsakes;
            for (int n = list.size() - 1; n >= 0; n--) {
                Keepsake keepsake = list.get(n);
                if (keepsake.page != page.entryPage || isGrabbed(keepsake)) continue;
                int[] b = KeepsakeArt.bounds(keepsake);
                if (EJGui.over(mx, my, pageX(i) + b[0] - 1, pageTop + b[1] - 1, b[2] - b[0] + 2, b[3] - b[1] + 2)) {
                    return new Stuck(page.entry, keepsake, i);
                }
            }
        }
        return null;
    }

    /** The note under the mouse, if there is one. */
    private NoteAt noteAt(double mx, double my) {
        for (int i = spread * 2; i <= spread * 2 + 1; i++) {
            if (!showsItself(i)) continue;
            BookPages.Page page = book.pages.get(i);
            if (page.entry == null) continue;
            for (Captions.Box box : Captions.place(this.font, page.entry, page.entryPage, book.whereOf(page.entry))) {
                int w = Math.max(box.blockWidth(), 24), h = box.widths().length * PageEditor.LINE_H;
                if (EJGui.over(mx, my, pageX(i) + box.x() - 1, pageTop + box.y() - 2, w + 2, h + 2)) return new NoteAt(page.entry, box, i);
            }
        }
        return null;
    }

    private void itemTooltip(ItemStack stack, List<Component> hints) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(this.minecraft, stack));
        lines.addAll(hints);
        tooltip = lines;
        tooltipImage = stack.getTooltipImage();
    }

    private void drawArrows(GuiGraphics ctx, int mx, int my) {
        if (canTurn(-1)) EJGui.pageArrow(ctx, arrowX(false), bookY + ARROW_Y, false, overArrow(mx, my, false));
        if (canTurn(1)) EJGui.pageArrow(ctx, arrowX(true), bookY + ARROW_Y, true, overArrow(mx, my, true));
    }

    private boolean hasEntries(Section section) {
        for (Entry entry : journal.entries) {
            if (Section.of(entry) == section) return true;
        }
        return false;
    }

    private boolean hasTab(Section section) {
        return section == Section.CONTENTS || hasEntries(section);
    }

    private void hoverHints(int mx, int my) {
        if (pickable != null) {
            int x = pageX(pickerPage());
            for (int i = 0; i < pickable.size(); i++) {
                if (EJGui.over(mx, my, cellX(x, i), cellY(i), 16, 17)) itemTooltip(this.minecraft.player.getInventory().getItem(pickable.get(i)), List.of());
            }
            return;
        }
        if (carried != null) {
            if (stickTarget(mx, my) < 0) tooltip = List.of(Component.literal("Click your page to stick it"), Component.literal("Right-click: never mind").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (Section section : Section.values()) {
            if (hasTab(section) && overTab(section, mx, my)) tooltip = List.of(Component.literal(section.title));
        }
        if (menuShown()) {
            List<Component> hint = desk.menuHint(mx, my, pageX(canvasPage() ^ 1), pageTop);
            if (hint != null) {
                tooltip = hint;
                return;
            }
        }
        if (grab != null && grab.moving) return;
        Stuck stuck = stuckAt(mx, my);
        if (stuck != null) {
            ItemStack stack = KeepsakeArt.stack(stuck.keepsake());
            List<Component> hints = new ArrayList<>();
            // a note cut short for room shows whole here
            for (Captions.Box box : Captions.place(this.font, stuck.entry(), stuck.keepsake().page, book.whereOf(stuck.entry()))) {
                if (box.keepsake().id.equals(stuck.keepsake().id) && box.cut()) hints.add(Component.literal("\"" + Captions.text(stuck.keepsake()) + "\"").withStyle(ChatFormatting.ITALIC));
            }
            if (canWrite) {
                hints.add(Component.literal("Drag: move it").withStyle(ChatFormatting.GRAY));
                hints.add(Component.literal("Click: write a note beside it").withStyle(ChatFormatting.GRAY));
                hints.add(Component.literal("Right-click: peel it off").withStyle(ChatFormatting.GRAY));
            }
            if (stack.isEmpty()) tooltip = List.of(Component.literal("A keepsake from a mod that is gone"));
            else itemTooltip(stack, hints);
            return;
        }
        for (PinSpot spot : pinSpots) {
            if (EJGui.over(mx, my, spot.x(), spot.y(), spot.w(), spot.h())) {
                boolean pinned = spot.entry().id.equals(journal.pinned);
                tooltip = List.of(Component.literal(pinned ? "Take the pin out" : "Pin this place to your screen"),
                        Component.literal("Location Tooltip shows it on a pill").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        if (canNameHere() && overNameIt(mx, my)) {
            tooltip = List.of(Component.literal("Name this village for everyone"), Component.literal("Location Tooltip's own rules decide").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (MarkName mark : markNames) {
            int[] b = mark.icon();
            if (EJGui.over(mx, my, b[0], b[1], b[2] - b[0], b[3] - b[1])) {
                tooltip = List.of(Component.literal(mark.name()));
                return;
            }
        }
        if (overRedRibbon(mx, my)) {
            tooltip = List.of(Component.literal("Next blank page"));
        } else if (overBlueRibbon(mx, my)) {
            tooltip = List.of(Component.literal(blueRibbonHint()));
        } else if (editEntry != null && overDone(mx, my)) {
            tooltip = stillSketching() ? List.of(Component.literal("Still sketching"))
                    : List.of(Component.literal(desk != null ? "Keep this drawing" : "Stop writing and keep it"), Component.literal("Esc does the same").withStyle(ChatFormatting.GRAY));
        } else if (editEntry != null && overDelete(mx, my)) {
            tooltip = List.of(Component.literal(deleteArmed ? "Click again to delete this entry" : "Delete this entry"));
            if (!editEntry.keepsakes.isEmpty()) tooltip = List.of(tooltip.get(0), Component.literal("What is taped in comes back to you").withStyle(ChatFormatting.GRAY));
        } else if (editEntry != null && overPaste(mx, my)) {
            tooltip = List.of(Component.literal(retrying() ? "Sketch it again" : "Paste an item or photo on this page"));
        } else {
            int page = featherPage(mx, my);
            if (page >= 0 && overFeather(page, mx, my)) tooltip = List.of(Component.literal("Write on this page"));
        }
    }

    private String blueRibbonHint() {
        Entry marked = journal.entry(journal.bookmark);
        if (!canWrite) return "Go to the marked page";
        if (marked == null) return firstEntryOnSpread() == null ? "Turn to an entry to mark it" : "Mark this page";
        return shows(spread, marked) ? "Take the mark out" : "Go to your marked page";
    }

    private void drawPage(GuiGraphics ctx, int index, int mx, int my) {
        if (index >= book.pages.size()) return;
        BookPages.Page page = book.pages.get(index);
        int x = pageX(index);
        switch (page.kind) {
            case CONTENTS -> drawContents(ctx, page, x, mx, my);
            case PLACES -> drawPlaces(ctx, page, x, mx, my);
            case ENTRY -> drawEntry(ctx, page, x, mx, my);
            case BLANK -> {
                if (canWrite && editEntry == null) {
                    if (full()) drawFull(ctx, x);
                    else drawChoices(ctx, x, mx, my);
                }
            }
            default -> {
            }
        }
    }

    private void heading(GuiGraphics ctx, String text, int x) {
        int[] at = headingSpot(x == leftX);
        EJGui.writeBold(ctx, this.font, text, x + at[0], pageTop - 2, EJGui.INK);
        EJGui.line(ctx, x + at[1], pageTop + 8, x + PAGE_W - 2, pageTop + 9, EJGui.INK);
    }

    /** Where a page's heading starts, and where its underline starts, past a folded corner. */
    private int[] headingSpot(boolean leftPage) {
        return new int[]{Math.max(2, Corners.start(leftPage, wear, -3, 8)), Corners.start(leftPage, wear, 8, 9)};
    }

    private void drawContents(GuiGraphics ctx, BookPages.Page page, int x, int mx, int my) {
        heading(ctx, "Contents", x);
        int y = pageTop + 13;
        boolean started = false;
        for (BookPages.Row row : page.rows) {
            if (row.level() == 0 && started) y += 2;
            started = true;
            int rx = x + 2 + row.level() * 8;
            boolean hover = EJGui.over(mx, my, x, y - 1, PAGE_W, 11);
            int colour = hover ? EJGui.INK_RED : row.level() == 0 ? EJGui.INK : EJGui.INK_SOFT;
            String num = row.far();
            int numX = x + PAGE_W - 1 - this.font.width(num);
            String name = fit(row.name(), numX - rx - 4);
            EJGui.write(ctx, this.font, name, rx, y, colour);
            int nameEnd = rx + this.font.width(name);
            EJGui.dots(ctx, nameEnd + 2, numX - 2, y + 7, EJGui.INK_SOFT);
            EJGui.write(ctx, this.font, num, numX, y, colour);
            if (hover) EJGui.line(ctx, rx, y + 9, nameEnd - 2, y + 9, EJGui.INK_RED);
            y += 11;
        }
        if (page.firstOfKind) {
            int count = journal.entries.size();
            if (count == 0) {
                EJGui.write(ctx, this.font, "Nothing here yet.", x + 2, pageTop + 13, EJGui.INK_SOFT);
                int ty = pageTop + 31;
                for (String line : new String[]{"The red ribbon finds", "your next blank page.", "", "The arrows turn pages.", "", "Click your own writing", "to change it."}) {
                    EJGui.write(ctx, this.font, line, x + 2, ty, EJGui.INK_FAINT);
                    ty += 10;
                }
            } else {
                EJGui.write(ctx, this.font, count + (count == 1 ? " entry so far" : " entries so far"), x + 2, pageBottom - 10, EJGui.INK_FAINT);
            }
        }
    }

    private String fit(String s, int width) {
        if (this.font.width(s) <= width) return s;
        return this.font.plainSubstrByWidth(s, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private void drawPlaces(GuiGraphics ctx, BookPages.Page page, int x, int mx, int my) {
        boolean searching = find != null || query != null;
        heading(ctx, searching ? "Found" : "Places I've been", x);
        // with Location Tooltip, each place has vanilla's map icon for its kind. The names all move over, so the column stays straight.
        boolean icons = false;
        for (BookPages.Row row : page.rows) icons |= row.target() != null && row.target().marker != null;
        int nameX = x + (icons ? 31 : 22);
        int y = pageTop + 13;
        EJGui.write(ctx, this.font, "day", x + 17 - this.font.width("day"), y, EJGui.INK_SOFT);
        EJGui.write(ctx, this.font, "where", nameX, y, EJGui.INK_SOFT);
        String farHead = searching ? "page" : "far";
        EJGui.write(ctx, this.font, farHead, x + PAGE_W - 1 - this.font.width(farHead), y, EJGui.INK_SOFT);
        EJGui.line(ctx, x + 1, y + 10, x + PAGE_W - 3, y + 10, EJGui.INK_FAINT);
        y += 13;
        for (BookPages.Row row : page.rows) {
            boolean hover = EJGui.over(mx, my, x, y - 2, PAGE_W, 13);
            EJGui.write(ctx, this.font, row.day(), x + 17 - this.font.width(row.day()), y, EJGui.INK_SOFT);
            if (icons && row.target() != null && row.target().marker != null) EJGui.mapIcon(ctx, row.target().marker, x + 21, y);
            int farX = x + PAGE_W - 1 - this.font.width(row.far());
            String name = fit(row.name(), farX - nameX - 3);
            EJGui.write(ctx, this.font, name, nameX, y, hover ? EJGui.INK_RED : EJGui.INK);
            EJGui.write(ctx, this.font, row.far(), farX, y, EJGui.INK_SOFT);
            if (hover) EJGui.line(ctx, nameX, y + 9, nameX - 2 + this.font.width(name), y + 9, EJGui.INK_RED);
            y += 13;
        }
        if (page.firstOfKind) drawFind(ctx, x, mx, my);
    }

    private void drawFind(GuiGraphics ctx, int x, int mx, int my) {
        int y = bookY + FIND_Y;
        if (find != null) {
            EJGui.write(ctx, this.font, FIND_LABEL, x + 2, y, EJGui.INK);
            find.drawLines(ctx, findTextX(x), y, 0, 1, EJGui.INK, focus == find);
        } else if (query != null) {
            EJGui.write(ctx, this.font, FIND_LABEL + query, x + 2, y, EJGui.INK_SOFT);
        } else {
            boolean hover = overFind(x, mx, my);
            EJGui.write(ctx, this.font, "find...", x + 2, y, hover ? EJGui.INK : EJGui.INK_FAINT);
            if (hover) EJGui.line(ctx, x + 2, y + 9, x + this.font.width("find..."), y + 9, EJGui.INK);
        }
    }

    private int findTextX(int x) {
        return x + 2 + this.font.width(FIND_LABEL);
    }

    private boolean overFind(int x, double mx, double my) {
        return EJGui.over(mx, my, x, bookY + FIND_Y - 2, 60, 12);
    }

    private void drawEntry(GuiGraphics ctx, BookPages.Page page, int x, int mx, int my) {
        Entry entry = page.entry;
        boolean editing = entry == editEntry;
        if (page.firstOfEntry) {
            if (entry.isLocation()) drawLocation(ctx, entry, x);
            if (entry.isSketch()) drawSketch(ctx, entry, x);
            if (entry.isDrawing()) drawDrawing(ctx, entry, x, mx, my);
            drawTitle(ctx, entry, x, editing);
            drawDay(ctx, entry, x, editing);
        }
        int top = bodyTop(page);
        if (editing) {
            if (page.firstOfEntry && body.value().isEmpty() && page.lineCount > 0) {
                TextFlow.Line first = body.lines().get(page.firstLine);
                EJGui.write(ctx, this.font, "Write here...", x + first.x(), top + first.row() * PageEditor.LINE_H, EJGui.INK_FAINT);
            }
            body.drawLines(ctx, x, top, page.firstLine, page.lineCount, EJGui.INK, focus == body);
        } else if (page.firstOfEntry && entry.isLocation() && entry.text.isEmpty() && canWrite) {
            EJGui.write(ctx, this.font, "add a note...", x + EntryLayout.of(entry).bodyX, top, EJGui.INK_FAINT);
        } else {
            drawLines(ctx, page, x, top);
        }
        String num = Integer.toString(page.number);
        EJGui.write(ctx, this.font, num, x + (PAGE_W - this.font.width(num)) / 2, bookY + 179, EJGui.INK_FAINT);
        drawKeepsakes(ctx, page, x);
    }

    private void drawLines(GuiGraphics ctx, BookPages.Page page, int x, int y) {
        List<TextFlow.Line> lines = book.linesOf(page.entry);
        int seed = seed(page.entry);
        for (int i = page.firstLine; i < page.firstLine + page.lineCount; i++) {
            TextFlow.Line at = lines.get(i);
            String line = page.entry.text.substring(at.begin(), at.end());
            int lx = x + at.x() + Drift.shift(seed, i), ly = y + at.row() * PageEditor.LINE_H;
            Entry target = linkTarget(line);
            if (target != null) {
                drawLink(ctx, line, lx, ly, target);
            } else {
                Drift.draw(ctx, this.font, line, lx, ly, EJGui.INK, seed, i, false);
            }
        }
    }

    /** A line that says "see: River camp" links to that entry, if there is one. */
    private Entry linkTarget(String line) {
        String lower = line.strip().toLowerCase(Locale.ROOT);
        if (!lower.startsWith("see: ") && !lower.startsWith("see ")) return null;
        String name = lower.substring(lower.indexOf(' ') + 1).strip();
        if (name.isEmpty()) return null;
        for (Entry entry : journal.entries) {
            if (entry.title.toLowerCase(Locale.ROOT).equals(name)) return entry;
        }
        return null;
    }

    private void drawLink(GuiGraphics ctx, String line, int x, int y, Entry target) {
        int w = this.font.width(line);
        EJGui.write(ctx, this.font, line, x, y, EJGui.INK_RED);
        EJGui.handLine(ctx, x, y + 9, x + w - 2, y + 10, 0, EJGui.INK_RED);
        arrowRight(ctx, x + w + 4, y + 4);
        links.add(new Link(x, y - 1, w + 16, 11, target));
    }

    private static void arrowRight(GuiGraphics ctx, int x, int y) {
        EJGui.line(ctx, x, y, x + 10, y, EJGui.INK_RED);
        EJGui.line(ctx, x + 10, y, x + 7, y - 3, EJGui.INK_RED);
        EJGui.line(ctx, x + 10, y, x + 7, y + 3, EJGui.INK_RED);
    }

    /** A heading is bold. A title under a picture is plain. */
    private int titleWidth(Entry entry, String text) {
        return Drift.width(this.font, text, !EntryLayout.of(entry).caption);
    }

    /** A long place name loses whole words from its end, so it stays on the page. The full name stays in the journal. */
    private String fitTitle(Entry entry, String text) {
        String s = text;
        int room = titleRoom(entry);
        while (titleWidth(entry, s) > room) {
            int space = s.lastIndexOf(' ');
            s = space > 0 ? s.substring(0, space) : s.substring(0, s.length() - 1);
        }
        return s;
    }

    private void drawTitle(GuiGraphics ctx, Entry entry, int x, boolean editing) {
        EntryLayout at = EntryLayout.of(entry);
        int seed = seed(entry);
        String text = editing ? title.value() : fitTitle(entry, entry.title);
        int w = titleWidth(entry, text.isEmpty() ? "Title" : text);
        int tx = x + titleStart(entry, w), ty = pageTop + at.titleY;
        int ink = at.caption ? EJGui.INK_SOFT : EJGui.INK;
        if (editing) {
            if (text.isEmpty()) EJGui.write(ctx, this.font, "Title", tx, ty, EJGui.INK_FAINT);
            title.drawLines(ctx, tx, ty, 0, 1, ink, focus == title);
        } else {
            if (text.isEmpty()) return;
            Drift.draw(ctx, this.font, text, tx + Drift.shift(seed, PageEditor.TITLE_LINE), ty, ink, seed, PageEditor.TITLE_LINE, !at.caption);
        }
        if (at.caption) return;
        w = titleWidth(entry, text);
        int end = tx + (at.centered ? w + 2 : Math.max(36, w + 2));
        EJGui.handLine(ctx, tx - 1, ty + 10, end, ty + 9, Math.floorMod(seed, 3) == 0 ? 1 : 0, EJGui.INK);
        if (!editing && entry.renamedSince()) {
            // the old name stays, crossed out by hand, and the new one is written beside it, or under it
            EJGui.handLine(ctx, tx - 1, ty + 5, tx + w + 1, ty + 3, 0, EJGui.INK);
            int nx = tx + w + 5, nw = this.font.width(entry.renamed);
            if (nx + nw <= x + PAGE_W - 2) EJGui.write(ctx, this.font, entry.renamed, nx, ty + 1, EJGui.INK_SOFT);
            else EJGui.writeOver(ctx, this.font, entry.renamed, tx, ty + 12, EJGui.INK_SOFT);
        }
    }

    private void drawDay(GuiGraphics ctx, Entry entry, int x, boolean editing) {
        int[] at = daySpot(entry, x, editing);
        if (at != null) EJGui.write(ctx, this.font, "day " + entry.day, at[0], at[1], entry.isLocation() ? EJGui.INK_SOFT : EJGui.INK_FAINT);
    }

    /** The day goes at the right of the title's row, or right after the title, as if written then. Null when the title leaves no room. */
    private int[] daySpot(Entry entry, int x, boolean editing) {
        // a new name written on the title's row takes the day's room
        if (!editing && entry.renamedSince()) return null;
        EntryLayout at = EntryLayout.of(entry);
        String day = "day " + entry.day;
        String shown = editing ? title.value() : fitTitle(entry, entry.title);
        if (editing && shown.isEmpty()) shown = "Title";
        int w = titleWidth(entry, shown);
        int tx = x + titleStart(entry, w), ty = pageTop + at.titleY;
        if (at.day == EntryLayout.Day.RIGHT) {
            int dayX = x + PAGE_W - 2 - this.font.width(day);
            if (tx + Math.max(36, w + 2) + 6 >= dayX) return null;
            return new int[]{dayX, entry.isLocation() ? pageTop + 1 : pageTop};
        }
        if (shown.isEmpty() && !at.caption) return null;
        int dayX = tx + w + (at.caption ? 5 : 6);
        if (dayX + this.font.width(day) > x + PAGE_W - 2) return null;
        return new int[]{dayX, ty + 1 + Drift.yAt(w, seed(entry), PageEditor.TITLE_LINE)};
    }

    private void drawLocation(GuiGraphics ctx, Entry entry, int x) {
        int mapY = pageTop + EntryLayout.of(entry).pictureY;
        ResourceLocation map = entry.map == null ? null : MapImages.get(journal.id, entry.map);
        if (map != null) EJGui.image(ctx, map, x, mapY, MapDrawer.W, MapDrawer.H);

        // the red X on the spot, and a small north mark where the map leaves room for it
        int cx = x + MapDrawer.W / 2, cy = mapY + MapDrawer.H / 2;
        for (int s = 0; s < 2; s++) {
            EJGui.line(ctx, cx - 4 + s, cy - 4, cx + 4 + s, cy + 4, EJGui.INK_RED);
            EJGui.line(ctx, cx + 4 + s, cy - 4, cx - 4 + s, cy + 4, EJGui.INK_RED);
        }
        int nx = x + (northLeft(entry) ? 10 : 118), ny = pageTop + 18;
        EJGui.write(ctx, this.font, "N", nx - 2, ny - 8, EJGui.INK);
        EJGui.line(ctx, nx, ny + 14, nx, ny + 2, EJGui.INK);
        EJGui.line(ctx, nx - 2, ny + 4, nx, ny + 2, EJGui.INK);
        EJGui.line(ctx, nx + 2, ny + 4, nx, ny + 2, EJGui.INK);
        drawMarks(ctx, entry, x, mapY, new int[]{nx - 4, ny - 9, nx + 5, ny + 15});

        String at = entry.x + ", " + entry.y + ", " + entry.z;
        EJGui.write(ctx, this.font, at, x + (PAGE_W - this.font.width(at)) / 2, pageTop + 116, EJGui.INK);
        drawWayBack(ctx, entry, x + 4, pageTop + 127);
    }

    /** Other places on the map, from Location Tooltip: vanilla's own map icon, and the name written beside it on the clearest side. A name keeps clear of the other icons and names, so it reads as its own. A name that fits nowhere is left off, and its icon shows it on hover. */
    private void drawMarks(GuiGraphics ctx, Entry entry, int x, int mapY, int[] north) {
        if (entry.marks == null) return;
        byte[] map = MapImages.mapPixels(entry.map);
        List<int[]> taken = new ArrayList<>(), icons = new ArrayList<>();
        int cx = x + MapDrawer.W / 2, cy = mapY + MapDrawer.H / 2;
        taken.add(new int[]{cx - 6, cy - 6, cx + 7, cy + 7});
        taken.add(north);
        for (MapMark mark : entry.marks) {
            int ix = x + mark.x - 4, iy = mapY + mark.y - 4;
            EJGui.mapIcon(ctx, mark.marker, ix, iy);
            icons.add(new int[]{ix, iy, ix + 8, iy + 8});
        }
        for (int m = 0; m < entry.marks.size(); m++) {
            String name = shortName(entry.marks.get(m).name, MARK_NAME_W);
            int[] icon = icons.get(m);
            int w = this.font.width(name), ix = icon[0], iy = icon[1];
            List<int[]> keepClear = new ArrayList<>(taken);
            for (int[] other : icons) keepClear.add(other == icon ? other : grow(other, MARK_GAP, MARK_GAP));
            int[] best = clearest(map, x, mapY, w, keepClear, new int[][]{{ix + 10, iy}, {ix - 2 - w, iy}, {ix + 4 - w / 2, iy + 10}, {ix + 4 - w / 2, iy - 10}});
            if (best == null) best = clearest(map, x, mapY, w, keepClear, new int[][]{{ix + 10, iy - 6}, {ix + 10, iy + 6}, {ix - 2 - w, iy - 6}, {ix - 2 - w, iy + 6}});
            int[] box = best == null ? null : new int[]{best[0] - 1, best[1] - 1, best[0] + w + 1, best[1] + 9};
            markNames.add(new MarkName(entry.marks.get(m).name, box, icon));
            if (box == null) continue;
            EJGui.writeOver(ctx, this.font, name, best[0], best[1], EJGui.INK);
            taken.add(grow(box, MARK_GAP, 1));
        }
    }

    private static int[] grow(int[] box, int dx, int dy) {
        return new int[]{box[0] - dx, box[1] - dy, box[2] + dx, box[3] + dy};
    }

    /** Of these spots for a name, the one on the map that covers nothing, with the least ink under it. Null if none fits. */
    private static int[] clearest(byte[] map, int x, int mapY, int w, List<int[]> keepClear, int[][] spots) {
        int[] best = null;
        int bestInk = Integer.MAX_VALUE;
        for (int[] spot : spots) {
            int[] box = {spot[0] - 1, spot[1] - 1, spot[0] + w + 1, spot[1] + 9};
            if (box[0] < x || box[2] > x + MapDrawer.W || box[1] < mapY || box[3] > mapY + MapDrawer.H || overlapsAny(box, keepClear)) continue;
            int ink = inkUnder(map, box[0] - x, box[1] - mapY, box[2] - x, box[3] - mapY);
            if (ink < bestInk) {
                bestInk = ink;
                best = spot;
            }
        }
        return best;
    }

    /** How much of the map is drawn on in this box, in map pixels. */
    private static int inkUnder(byte[] map, int x0, int y0, int x1, int y1) {
        if (map == null) return 0;
        int ink = 0;
        for (int y = Math.max(0, y0); y < Math.min(MapDrawer.H, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(MapDrawer.W, x1); x++) {
                if (map[y * MapDrawer.W + x] != 0) ink++;
            }
        }
        return ink;
    }

    /** A long name is cut short on a small map, at a word where it can be. */
    private String shortName(String name, int room) {
        if (this.font.width(name) <= room) return name;
        String s = name.strip();
        while (s.contains(" ") && this.font.width(s + "...") > room) s = s.substring(0, s.lastIndexOf(' ')).strip();
        while (s.length() > 1 && this.font.width(s + "...") > room) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static boolean overlapsAny(int[] box, List<int[]> boxes) {
        for (int[] b : boxes) {
            if (box[0] < b[2] && b[0] < box[2] && box[1] < b[3] && b[1] < box[3]) return true;
        }
        return false;
    }

    /** A person draws the north mark in the top corner with less on it. Until the map comes, the entry's seed picks. */
    private static boolean northLeft(Entry entry) {
        byte[] map = entry.map == null ? null : MapImages.mapPixels(entry.map);
        if (map == null) return EntryLayout.pick(entry.id.hashCode(), 9, 2) == 0;
        int left = 0, right = 0;
        for (int y = 0; y < 22; y++) {
            for (int x = 2; x < 18; x++) {
                if (map[y * MapDrawer.W + x] != 0) left++;
                if (map[y * MapDrawer.W + MapDrawer.W - 1 - x] != 0) right++;
            }
        }
        return left < right;
    }

    private void drawSketch(GuiGraphics ctx, Entry entry, int x) {
        int y = pageTop + EntryLayout.of(entry).pictureY;
        if (entry == editEntry && sketching != null && !sketching.done()) {
            String dots = ".".repeat((int) (Util.getMillis() / 300 % 4));
            EJGui.write(ctx, this.font, "Sketching" + dots, x + 40, y + 40, EJGui.INK_FAINT);
            return;
        }
        ResourceLocation image = entry.map == null ? null : MapImages.getSketch(journal.id, entry.map);
        if (image == null) return;
        int shown = SketchDrawer.H;
        if (entry == editEntry && revealStart > 0) {
            // the lines come down the page, the way they would be drawn
            shown = (int) Math.min(SketchDrawer.H, (Util.getMillis() - revealStart) * SketchDrawer.H / REVEAL_MS);
        }
        RenderSystem.enableBlend();
        ctx.blit(image, x, y, 0, 0, SketchDrawer.W, shown, SketchDrawer.W, SketchDrawer.H);
        RenderSystem.disableBlend();
    }

    private void drawDrawing(GuiGraphics ctx, Entry entry, int x, int mx, int my) {
        if (entry == editEntry && desk != null) {
            desk.drawCanvas(ctx, x, canvasY(), mx, my, pickable == null && carried == null && stuckAt(mx, my) == null);
            return;
        }
        ResourceLocation image = entry.map == null ? null : MapImages.getDrawing(journal.id, entry.map);
        if (image != null) EJGui.imageTo(ctx, image, x, pageTop + EntryLayout.of(entry).pictureY, DrawingDesk.SHOWN, DrawingDesk.SHOWN, PixelCanvas.SIZE);
    }

    /** The viewfinder took a shot. A new sketch starts a draft. A retry keeps what you already wrote. */
    public void sketchTaken(SketchJob job) {
        toViewfinder = false;
        sketching = job;
        revealStart = 0;
        if (editEntry == null) startDraft(Entry.SKETCH);
        else editEntry.map = null;
        rebuild();
        showPage(book.pageOf(editEntry));
    }

    private void workOnSketch() {
        if (sketching == null || editEntry == null || editEntry.map != null) return;
        sketching.work(SKETCH_BUDGET_NANOS);
        if (sketching.done()) {
            editEntry.map = UUID.randomUUID().toString();
            MapImages.putSketch(editEntry.map, sketching.image());
            revealStart = Util.getMillis();
        }
    }

    private boolean stillSketching() {
        return sketching != null && !sketching.done();
    }

    private void drawWayBack(GuiGraphics ctx, Entry entry, int x, int y) {
        var player = this.minecraft.player;
        if (player == null) return;
        String dim = this.minecraft.level.dimension().location().toString();
        String far = BookPages.far(entry, player.getX(), player.getZ(), dim);
        int end;
        if (!dim.equals(entry.dim)) {
            String text = "in the " + far;
            EJGui.write(ctx, this.font, text, x, y, EJGui.INK_SOFT);
            end = x + this.font.width(text);
        } else if (far.equals("here")) {
            EJGui.write(ctx, this.font, "you are here", x, y, EJGui.INK_SOFT);
            end = x + this.font.width("you are here");
        } else {
            String text = far + " that way";
            EJGui.write(ctx, this.font, text, x, y, EJGui.INK_SOFT);
            drawArrow(ctx, entry.x + 0.5 - player.getX(), entry.z + 0.5 - player.getZ(), player.getYRot(), x + this.font.width(text) + 10, y + 3);
            end = x + this.font.width(text) + 17;
        }
        // with Location Tooltip, the way back can be pinned to your screen. Without it, a pin means nothing, so it hides.
        if (!LocationTooltipCompat.linked()) return;
        if (canWrite) pinSpots.add(new PinSpot(x - 1, y - 2, end - x + 12, 12, entry));
        if (entry.id.equals(journal.pinned)) EJGui.art(ctx, PIN, end + 3, y, EJGui.INK_RED);
    }

    /** Up on the page is the way you are facing. */
    private static void drawArrow(GuiGraphics ctx, double dx, double dz, float yaw, int cx, int cy) {
        double a = Math.toRadians(yaw);
        double forward = dx * -Math.sin(a) + dz * Math.cos(a);
        double right = dx * -Math.cos(a) + dz * -Math.sin(a);
        double n = Math.max(0.001, Math.hypot(forward, right));
        double ux = right / n, uy = -forward / n;
        int tipX = cx + (int) Math.round(ux * 6), tipY = cy + (int) Math.round(uy * 6);
        EJGui.line(ctx, cx - (int) Math.round(ux * 5), cy - (int) Math.round(uy * 5), tipX, tipY, EJGui.INK_RED);
        for (int s = -1; s <= 1; s += 2) {
            EJGui.line(ctx, tipX, tipY, tipX - (int) Math.round(ux * 4 - uy * 3 * s), tipY - (int) Math.round(uy * 4 + ux * 3 * s), EJGui.INK_RED);
        }
    }

    private void drawChoices(GuiGraphics ctx, int x, int mx, int my) {
        long today = this.minecraft.level.getDayTime() / 24000L + 1;
        EJGui.write(ctx, this.font, "Day " + today, x + Math.max(3, Corners.start(x == leftX, wear, -3, 8)), pageTop - 2, EJGui.INK_FAINT);
        for (int i = 0; i < CHOICES.length; i++) {
            int y = choiceY(i);
            boolean ready = i != 0 || !drawingMap;
            String label = i == 0 && drawingMap ? "Drawing the map..." : CHOICES[i];
            boolean hover = ready && overChoice(x, y, label, mx, my);
            EJGui.write(ctx, this.font, label, x + 26, y, hover ? EJGui.INK : EJGui.INK_FAINT);
            if (hover) EJGui.line(ctx, x + 26, y + 9, x + 26 + this.font.width(label) - 2, y + 9, EJGui.INK);
        }
    }

    private void drawFull(GuiGraphics ctx, int x) {
        EJGui.write(ctx, this.font, "This journal is full.", x + 26, choiceY(0), EJGui.INK_FAINT);
        EJGui.write(ctx, this.font, "Start a new one", x + 26, choiceY(1), EJGui.INK_FAINT);
        EJGui.write(ctx, this.font, "to keep going.", x + 26, choiceY(1) + PageEditor.LINE_H, EJGui.INK_FAINT);
    }

    private int choiceY(int index) {
        return pageTop + 40 + index * 16;
    }

    private boolean overChoice(int x, int y, String label, double mx, double my) {
        return EJGui.over(mx, my, x + 24, y - 2, this.font.width(label) + 4, 12);
    }

    // ---- places you can click

    private boolean overTab(Section section, double mx, double my) {
        return EJGui.over(mx, my, bookX + 299, bookY + section.y, 23, 16);
    }

    private boolean overRedRibbon(double mx, double my) {
        return EJGui.over(mx, my, bookX + 166, bookY + 189, EJGui.RIBBON_W, 27);
    }

    private boolean overBlueRibbon(double mx, double my) {
        boolean marked = !journal.bookmark.isEmpty();
        if (!marked && !canWrite) return false;
        return EJGui.over(mx, my, bookX + 116, bookY + 189, EJGui.RIBBON_W, marked ? 21 : 11);
    }

    private boolean overDone(double mx, double my) {
        return EJGui.over(mx, my, bookX + DONE_X, bookY + 188, DONE_W, EJGui.SLIP_BOTTOM - 186);
    }

    private boolean overDelete(double mx, double my) {
        return EJGui.over(mx, my, bookX + DELETE_X, bookY + 188, DELETE_W, EJGui.SLIP_BOTTOM - 186);
    }

    private boolean overPaste(double mx, double my) {
        return EJGui.over(mx, my, bookX + PASTE_X, bookY + 188, PASTE_W, EJGui.SLIP_BOTTOM - 186);
    }

    private int arrowX(boolean next) {
        return next ? bookX + FORWARD_ARROW_X[Math.min(wear, FORWARD_ARROW_X.length - 1)] : bookX + 19;
    }

    private boolean overArrow(double mx, double my, boolean next) {
        return EJGui.over(mx, my, arrowX(next), bookY + ARROW_Y, ARROW_W, ARROW_H);
    }

    /** The page you could start writing on, while the mouse is over it. The feather shows there. */
    private int featherPage(double mx, double my) {
        if (!canWrite || editEntry != null || find != null) return -1;
        if (!EJGui.over(mx, my, bookX, bookY, EJGui.BOOK_W, EJGui.BOOK_H)) return -1;
        int index = pageUnder(mx);
        return index < book.pages.size() && book.pages.get(index).kind == BookPages.Kind.ENTRY ? index : -1;
    }

    private int featherX(int page) {
        return page % 2 == 0 ? leftX + PAGE_W - FEATHER_SIZE - 2 : rightX + 2;
    }

    private boolean overFeather(int page, double mx, double my) {
        return EJGui.over(mx, my, featherX(page), bookY + FEATHER_Y, FEATHER_SIZE, FEATHER_SIZE);
    }

    @Override
    protected boolean ejClick(double mx, double my, int button) {
        if (pickable != null) return clickPicker(mx, my, button);
        if (carried != null) return clickCarrying(mx, my, button);
        if (canvasShown()) {
            if (button == 0 && menuShown() && desk.clickMenu(mx, my, pageX(canvasPage() ^ 1), pageTop)) {
                deleteArmed = false;
                return true;
            }
            if (stuckAt(mx, my) == null && desk.press(mx, my, button, pageX(canvasPage()), canvasY())) {
                focus = null;
                dragging = null;
                deleteArmed = false;
                return true;
            }
        }
        if (button == 1) return rightClick(mx, my);
        if (button != 0) return false;

        if (canNameHere() && overNameIt(mx, my)) {
            EJGui.click();
            deleteArmed = false;
            EJNetClient.send(EJPayloads.NAME_VILLAGE, new EJPayloads.NameVillage(journal.id, editEntry.id, title.value().strip()));
            return true;
        }
        if (editEntry != null && overPaste(mx, my)) {
            EJGui.click();
            deleteArmed = false;
            if (retrying()) goToViewfinder();
            else openPicker();
            return true;
        }
        if (editEntry != null && overDone(mx, my)) {
            if (stillSketching()) return true;
            EJGui.click();
            finishEditing();
            return true;
        }
        if (editEntry != null && overDelete(mx, my)) {
            EJGui.click();
            if (deleteArmed) deleteEntry();
            else deleteArmed = true;
            return true;
        }
        deleteArmed = false;

        if (overRedRibbon(mx, my)) {
            finishEditing();
            jump(book.blankPage);
            return true;
        }
        if (overBlueRibbon(mx, my)) {
            finishEditing();
            clickBlueRibbon();
            return true;
        }
        for (Section section : Section.values()) {
            if (hasTab(section) && overTab(section, mx, my)) {
                finishEditing();
                nextInSection(section);
                return true;
            }
        }

        // the first click away only stops what you were doing
        boolean busy = editEntry != null || find != null;
        if (!EJGui.over(mx, my, bookX - 4, bookY - 4, EJGui.BOOK_W + 8, EJGui.BOOK_H + 8)) {
            if (busy) finishEditing();
            else onClose();
            return true;
        }
        if (overArrow(mx, my, true) && canTurn(1)) {
            turn(1);
            return true;
        }
        if (overArrow(mx, my, false) && canTurn(-1)) {
            turn(-1);
            return true;
        }

        // press on something taped down to move it, or click it to write the note beside it. A right-click peels it off.
        Stuck stuck = stuckAt(mx, my);
        if (stuck != null) {
            if (!canWrite) return true;
            if (busy && stuck.entry() != editEntry) {
                finishEditing();
                return true;
            }
            if (!canOpen(stuck.entry())) return true;
            Keepsake keepsake = stuck.keepsake();
            grab = new Grab(stuck.entry(), keepsake, (int) Math.floor(mx) - (pageX(stuck.page()) + keepsake.x), (int) Math.floor(my) - (pageTop + keepsake.y), mx, my);
            return true;
        }
        NoteAt note = canWrite ? noteAt(mx, my) : null;
        if (note != null) {
            if (busy && note.entry() != editEntry) {
                finishEditing();
                return true;
            }
            if (!canOpen(note.entry())) return true;
            if (editEntry == null) startEditing(note.entry());
            Keepsake keepsake = editEntry.keepsake(note.box().keepsake().id);
            if (keepsake != null) {
                openCaption(keepsake);
                int line = (int) Math.floor((my - (pageTop + note.box().y())) / PageEditor.LINE_H);
                dragging = caption.click(Math.max(0, line), mx - (pageX(note.page()) + note.box().x())) ? caption : null;
            }
            return true;
        }

        for (PinSpot spot : pinSpots) {
            if (EJGui.over(mx, my, spot.x(), spot.y(), spot.w(), spot.h())) {
                EJGui.click();
                journal.pinned = spot.entry().id.equals(journal.pinned) ? "" : spot.entry().id;
                EJNetClient.send(EJPayloads.SET_PIN, new EJPayloads.SetPin(journal.id, journal.pinned));
                return true;
            }
        }

        int index = pageUnder(mx);
        if (menuShown() && index == (canvasPage() ^ 1)) return true;
        if (index >= book.pages.size()) return true;
        BookPages.Page page = book.pages.get(index);
        int x = pageX(index);

        if (editEntry != null && page.entry == editEntry) {
            placeCursor(page, x, mx, my);
            return true;
        }
        if (find != null && page.kind == BookPages.Kind.PLACES && page.firstOfKind && EJGui.over(mx, my, x, bookY + FIND_Y - 2, PAGE_W, 12)) {
            focus = find;
            dragging = find.click(0, mx - findTextX(x)) ? find : null;
            return true;
        }
        for (Link link : links) {
            if (EJGui.over(mx, my, link.x(), link.y(), link.w(), link.h())) {
                finishEditing();
                jump(book.pageOf(link.target()));
                return true;
            }
        }
        finishEditing();

        switch (page.kind) {
            case CONTENTS -> clickContents(page, x, mx, my);
            case PLACES -> clickPlaces(page, x, mx, my);
            case ENTRY -> {
                if (canWrite && !busy) clickEntry(page, index, x, mx, my);
            }
            case BLANK -> {
                if (canWrite && !busy && !full()) clickChoices(x, mx, my);
            }
            default -> {
            }
        }
        return true;
    }

    private boolean rightClick(double mx, double my) {
        Stuck stuck = canWrite ? stuckAt(mx, my) : null;
        if (stuck == null) return false;
        EJGui.peelOff();
        EJNetClient.send(EJPayloads.PEEL_KEEPSAKE, new EJPayloads.PeelKeepsake(journal.id, stuck.entry().id, stuck.keepsake().id));
        return true;
    }

    private void openPicker() {
        pickable = new ArrayList<>();
        Inventory inventory = this.minecraft.player.getInventory();
        var registries = this.minecraft.level.registryAccess();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (Keepsakes.canPaste(inventory.getItem(slot), registries)) pickable.add(slot);
        }
        if (Keepsakes.canPaste(inventory.getItem(Inventory.SLOT_OFFHAND), registries)) pickable.add(Inventory.SLOT_OFFHAND);
    }

    private int paperCount() {
        Inventory inventory = this.minecraft.player.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.PAPER)) count += inventory.getItem(slot).getCount();
        }
        return count;
    }

    /** Pick one and it goes on the mouse. Any other click means never mind. */
    private boolean clickPicker(double mx, double my, int button) {
        int x = pageX(pickerPage());
        List<Integer> slots = pickable;
        pickable = null;
        if (button != 0) return true;
        for (int i = 0; i < slots.size(); i++) {
            if (!EJGui.over(mx, my, cellX(x, i), cellY(i), 16, 17)) continue;
            int slot = slots.get(i);
            ItemStack stack = this.minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty()) return true;
            if (paperCost && paperCount() < (stack.is(Items.PAPER) ? 2 : 1)) {
                // no paper is left for the tape, so the menu stays
                pickable = slots;
                return true;
            }
            ItemStack one = stack.copyWithCount(1);
            EJGui.click();
            carried = new Carried(slot, one, Keepsakes.write(one, this.minecraft.level.registryAccess()), UUID.randomUUID().toString());
            return true;
        }
        return true;
    }

    private boolean clickCarrying(double mx, double my, int button) {
        if (button != 0) {
            carried = null;
            return true;
        }
        if (overArrow(mx, my, true) && canTurn(1)) {
            turn(1);
            return true;
        }
        if (overArrow(mx, my, false) && canTurn(-1)) {
            turn(-1);
            return true;
        }
        int target = stickTarget(mx, my);
        if (target < 0) return true;
        int[] at = stickSpot(target, KeepsakeArt.size(carried.stack()), mx, my);
        String key = BuiltInRegistries.ITEM.getKey(carried.stack().getItem()).toString();
        int entryPage = target - book.pageOf(editEntry);
        EJGui.stickDown();
        EJNetClient.send(EJPayloads.PASTE_KEEPSAKE, new EJPayloads.PasteKeepsake(journal.id, editEntry.id, editEntry.type, carried.id(), carried.slot(), key, entryPage, at[0], at[1]));
        carried = null;
        return true;
    }

    /** A drawing opens only once its picture has come from the server, so its picture is never lost. */
    private static boolean canOpen(Entry entry) {
        return !(entry.isDrawing() && entry.map != null && MapImages.drawingPixels(entry.map) == null);
    }

    private void clickEntry(BookPages.Page page, int index, int x, double mx, double my) {
        Entry entry = page.entry;
        if (!canOpen(entry)) return;
        if (overFeather(index, mx, my)) {
            EJGui.click();
            startEditing(entry);
            followCaret();
        } else if (overWriting(page, x, my)) {
            startEditing(entry);
            placeCursor(page, x, mx, my);
        } else if (entry.isDrawing()) {
            EJGui.click();
            startEditing(entry);
            focus = null;
        }
    }

    private boolean overWriting(BookPages.Page page, int x, double my) {
        if (!page.firstOfEntry) return true;
        return overTitle(page.entry, my) || my >= bodyTop(page) - 2;
    }

    private void placeCursor(BookPages.Page page, int x, double mx, double my) {
        if (page.firstOfEntry) {
            if (overTitle(page.entry, my)) {
                focus = title;
                dragging = title.click(0, mx - titleX(page.entry, x)) ? title : null;
                return;
            }
            if (my < bodyTop(page) - 2) return;
        }
        focus = body;
        dragging = body.click(lineAt(page, x, mx, my), mx - x) ? body : null;
    }

    @Override
    protected boolean ejDrag(double mx, double my, int button) {
        if (desk != null && desk.stroking()) {
            if (canvasShown()) desk.drag(mx, my, pageX(canvasPage()), canvasY());
            return true;
        }
        if (button != 0) return false;
        if (grab != null) {
            if (!grab.moving && Math.hypot(mx - grab.startX, my - grab.startY) >= GRAB_SLOP) {
                if (editEntry != grab.entry) startEditing(grab.entry);
                grab.moving = true;
            }
            return true;
        }
        if (dragging == null) return false;
        if (dragging == find) {
            find.dragTo(0, mx - findTextX(pageX(book.placesPage)));
        } else if (dragging == title) {
            title.dragTo(0, mx - titleX(editEntry, pageX(book.pageOf(editEntry))));
        } else if (dragging == caption) {
            NoteAt note = captionBox();
            if (note != null) {
                int line = (int) Math.floor((my - (pageTop + note.box().y())) / PageEditor.LINE_H);
                caption.dragTo(Math.max(0, line), mx - (pageX(note.page()) + note.box().x()));
            }
        } else {
            int index = pageUnder(mx);
            if (index >= book.pages.size() || book.pages.get(index).entry != editEntry) index ^= 1;
            if (index >= book.pages.size() || book.pages.get(index).entry != editEntry) return true;
            int x = pageX(index);
            body.dragTo(lineAt(book.pages.get(index), x, mx, my), mx - x);
        }
        return true;
    }

    @Override
    protected void ejRelease(double mx, double my, int button) {
        if (desk != null) desk.release();
        if (button == 0 && grab != null) {
            Grab let = grab;
            grab = null;
            if (let.moving) drop(let, mx, my);
            else writeNoteOf(let);
        }
        if (button == 0) dragging = null;
    }

    /** A plain click on a thing: write the note beside it. */
    private void writeNoteOf(Grab let) {
        if (editEntry != let.entry) startEditing(let.entry);
        Keepsake keepsake = editEntry.keepsake(let.keepsake.id);
        if (keepsake != null) openCaption(keepsake);
    }

    /** Lets go of a thing you moved. On your own pages it sticks there. Anywhere else it goes back. */
    private void drop(Grab let, double mx, double my) {
        Keepsake keepsake = editEntry == null ? null : editEntry.keepsake(let.keepsake.id);
        if (keepsake == null) return;
        int size = KeepsakeArt.size(keepsake);
        double cx = mx - let.offX + size / 2.0, cy = my - let.offY + size / 2.0;
        int target = stickTarget(cx, cy);
        if (target < 0) return;
        int[] at = stickSpot(target, size, cx, cy);
        if (keepsake.id.equals(captionId)) commitCaption();
        keepsake.page = target - book.pageOf(editEntry);
        keepsake.x = at[0];
        keepsake.y = at[1];
        EJGui.stickDown();
        EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(journal.id, editEntry.id, keepsake.id, keepsake.page, keepsake.x, keepsake.y, Captions.text(keepsake)));
        reflow();
    }

    /** Where the note you are writing is right now. It can move as you write. */
    private NoteAt captionBox() {
        if (caption == null || editEntry == null) return null;
        Keepsake keepsake = editEntry.keepsake(captionId);
        int first = book.pageOf(editEntry);
        if (keepsake == null || first < 0) return null;
        for (Captions.Box box : Captions.place(this.font, editEntry, keepsake.page, book.whereOf(editEntry))) {
            if (box.keepsake().id.equals(captionId)) return new NoteAt(editEntry, box, first + keepsake.page);
        }
        return null;
    }

    /** Starts writing the note beside this thing. The writing around it makes room as you go. */
    private void openCaption(Keepsake keepsake) {
        if (caption != null && keepsake.id.equals(captionId)) {
            focus = caption;
            return;
        }
        commitCaption();
        captionId = keepsake.id;
        captionStart = Captions.text(keepsake);
        Captions.startWriting(this.font, editEntry, keepsake, book.whereOf(editEntry));
        caption = PageEditor.note(this.font, captionStart, Captions.MAX_CHARS, Captions.seed(keepsake), Captions.LIMIT_W, Captions.MAX_LINES, Captions.LIMIT_W);
        NoteAt at = captionBox();
        if (at != null) caption.boxWidth(at.box().width());
        caption.toEnd();
        focus = caption;
        dragging = null;
        deleteArmed = false;
        reflow();
    }

    /** Keeps the note you were writing. It goes to the server only if it changed. */
    private void commitCaption() {
        if (caption == null) return;
        String text = caption.value().strip();
        Keepsake keepsake = editEntry == null ? null : editEntry.keepsake(captionId);
        boolean changed = !text.equals(captionStart);
        dropCaption();
        if (keepsake == null) return;
        keepsake.note = text.isEmpty() ? null : text;
        if (changed) {
            EJNetClient.send(EJPayloads.EDIT_KEEPSAKE, new EJPayloads.EditKeepsake(journal.id, editEntry.id, keepsake.id, keepsake.page, keepsake.x, keepsake.y, text));
        }
        reflow();
    }

    private void dropCaption() {
        if (focus == caption) focus = null;
        if (dragging == caption) dragging = null;
        caption = null;
        captionId = null;
        captionStart = "";
        Captions.stopWriting();
    }

    private void reflow() {
        flows.clear();
        if (body != null && editEntry != null) body.flowOn(editEntry);
        rebuild();
    }

    private void clickContents(BookPages.Page page, int x, double mx, double my) {
        int y = pageTop + 13;
        boolean started = false;
        for (BookPages.Row row : page.rows) {
            if (row.level() == 0 && started) y += 2;
            started = true;
            if (EJGui.over(mx, my, x, y - 1, PAGE_W, 11)) {
                jump(book.pageOf(row.target()));
                return;
            }
            y += 11;
        }
    }

    private void clickPlaces(BookPages.Page page, int x, double mx, double my) {
        int y = pageTop + 26;
        for (BookPages.Row row : page.rows) {
            if (EJGui.over(mx, my, x, y - 2, PAGE_W, 13)) {
                jump(book.pageOf(row.target()));
                return;
            }
            y += 13;
        }
        if (page.firstOfKind && overFind(x, mx, my)) {
            EJGui.click();
            find = PageEditor.findLine(this.font, query == null ? "" : query, FIND_CHARS, TITLE_W - this.font.width(FIND_LABEL));
            find.toEnd();
            focus = find;
            query = null;
            rebuild();
        }
    }

    private void clickChoices(int x, double mx, double my) {
        if (overChoice(x, choiceY(0), CHOICES[0], mx, my) && !drawingMap) {
            EJGui.click();
            drawingMap = true;
            EJNetClient.send(EJPayloads.MARK_PLACE, new EJPayloads.MarkPlace(journal.id));
        } else if (overChoice(x, choiceY(1), CHOICES[1], mx, my)) {
            EJGui.click();
            goToViewfinder();
        } else if (overChoice(x, choiceY(2), CHOICES[2], mx, my)) {
            EJGui.click();
            startDraft(Entry.DRAWING);
        } else if (overChoice(x, choiceY(3), CHOICES[3], mx, my)) {
            EJGui.click();
            startDraft(Entry.NOTE);
        } else if (overChoice(x, choiceY(4), CHOICES[4], mx, my)) {
            EJGui.click();
            startDraft(Entry.SCRAPBOOK);
            openPicker();
        }
    }

    private void goToViewfinder() {
        toViewfinder = true;
        Viewfinder.open(this);
    }

    private void startDraft(String type) {
        Entry draft = new Entry();
        draft.id = UUID.randomUUID().toString();
        draft.type = type;
        draft.day = this.minecraft.level.getDayTime() / 24000L + 1;
        isNew = true;
        startEditing(draft);
        focus = title;
    }

    /** With no mark, it marks the page. With a mark, it goes there. On the marked page, it comes back out. */
    private void clickBlueRibbon() {
        Entry marked = journal.entry(journal.bookmark);
        if (marked != null && !shows(spread, marked)) {
            jump(book.pageOf(marked));
            return;
        }
        if (!canWrite) return;
        Entry here = marked == null ? firstEntryOnSpread() : null;
        if (marked == null && here == null) return;
        EJGui.click();
        journal.bookmark = here == null ? "" : here.id;
        EJNetClient.send(EJPayloads.SET_BOOKMARK, new EJPayloads.SetBookmark(journal.id, journal.bookmark));
    }

    private void jump(int page) {
        int to = Math.max(0, Math.min(page / 2, lastSpread()));
        if (page >= 0 && to != spread) {
            spread = to;
            EJGui.pageTurn();
        }
    }

    /** The first click goes to the start of the section. Clicking again moves on through it. */
    private void nextInSection(Section section) {
        if (section == Section.CONTENTS) {
            jump(0);
            return;
        }
        List<Integer> stops = new ArrayList<>();
        if (section == Section.LOCATIONS) stops.add(book.placesPage);
        for (Entry entry : journal.entries) {
            if (Section.of(entry) == section) stops.add(book.pageOf(entry));
        }
        if (stops.isEmpty()) return;
        int next = stops.get(0);
        if (showing(section)) {
            for (int stop : stops) {
                if (stop / 2 > spread) {
                    next = stop;
                    break;
                }
            }
        }
        jump(next);
    }

    private boolean showing(Section section) {
        for (int i = spread * 2; i <= spread * 2 + 1 && i < book.pages.size(); i++) {
            BookPages.Page page = book.pages.get(i);
            if (page.kind == BookPages.Kind.PLACES && section == Section.LOCATIONS) return true;
            if (page.entry != null && Section.of(page.entry) == section) return true;
        }
        return false;
    }

    /** While you write, the book stays on your note. With a thing on the mouse, it can also go to the blank page after your newest entry. */
    private boolean canTurn(int by) {
        int to = spread + by;
        if (to < 0 || to > lastSpread()) return false;
        if (editEntry == null || shows(to, editEntry)) return true;
        return carried != null && book.blankPage / 2 == to && book.pages.get(book.blankPage - 1).entry == editEntry;
    }

    private void turn(int by) {
        if (!canTurn(by)) return;
        if (find != null) closeFind(true);
        spread = Math.max(0, Math.min(lastSpread(), spread + by));
        EJGui.pageTurn();
    }

    // ---- writing

    private void startEditing(Entry entry) {
        editEntry = entry;
        editStartTitle = entry.title;
        editStartText = entry.text;
        EntryLayout at = EntryLayout.of(entry);
        title = PageEditor.title(this.font, entry.title, EJPayloads.MAX_TITLE, seed(entry), titleRoom(entry), !at.caption);
        body = PageEditor.body(this.font, entry.text, EJPayloads.MAX_TEXT, seed(entry), entry, whereOf(entry));
        title.toEnd();
        body.toEnd();
        focus = body;
        desk = entry.isDrawing() ? new DrawingDesk(new PixelCanvas(entry.map == null ? null : MapImages.drawingPixels(entry.map))) : null;
        rebuild();
        if (isNew) showPage(book.pageOf(entry));
    }

    private void stopWriting() {
        dropCaption();
        grab = null;
        editEntry = null;
        title = body = focus = dragging = null;
        isNew = deleteArmed = false;
        pickable = null;
        carried = null;
        sketching = null;
        revealStart = 0;
        desk = null;
    }

    /** Called whenever you click or turn away. Also lets go of the find line, so keys work on the book again. */
    private void finishEditing() {
        if (find != null) closeFind(true);
        if (editEntry == null) return;
        commitCaption();
        String t = title.value().strip(), b = body.value().stripTrailing();
        boolean changed = !t.equals(editStartTitle) || !b.equals(editStartText);
        boolean empty = t.isEmpty() && b.isEmpty();
        Entry entry = editEntry;
        boolean draft = isNew;
        byte[] sketch = sketching != null ? sketching.image() : null;
        PixelCanvas drawn = desk != null ? desk.canvas() : null;
        if (desk != null) desk.release();
        stopWriting();
        if (drawn != null) {
            finishDrawing(entry, draft, drawn, t, b, changed);
        } else if (draft && entry.isSketch()) {
            if (sketch != null && entry.map != null) {
                entry.title = t;
                entry.text = b;
                journal.entries.add(entry);
                EJNetClient.send(EJPayloads.SAVE_SKETCH, new EJPayloads.SaveImage(journal.id, entry.id, entry.map, t, b, SketchDrawer.pack(sketch)));
            }
        } else if (draft) {
            if (!empty) {
                entry.title = t;
                entry.text = b;
                journal.entries.add(entry);
                EJNetClient.send(EJPayloads.SAVE_ENTRY, new EJPayloads.SaveEntry(journal.id, entry.id, t, b));
            }
        } else if (changed) {
            entry.title = t;
            entry.text = b;
            if (entry.isEmpty()) journal.entries.remove(entry);
            EJNetClient.send(EJPayloads.SAVE_ENTRY, new EJPayloads.SaveEntry(journal.id, entry.id, t, b));
        }
        flows.clear();
        rebuildHere();
    }

    /** A new picture gets a new file. With no ink and no words and nothing taped in, the drawing is gone, like an emptied note. */
    private void finishDrawing(Entry entry, boolean draft, PixelCanvas drawn, String t, String b, boolean wordsChanged) {
        if (drawn.isBlank() && t.isEmpty() && b.isEmpty() && entry.keepsakes.isEmpty()) {
            if (!draft) {
                journal.entries.remove(entry);
                if (entry.id.equals(journal.bookmark)) journal.bookmark = "";
                EJNetClient.send(EJPayloads.DELETE_ENTRY, new EJPayloads.DeleteEntry(journal.id, entry.id));
            }
            return;
        }
        entry.title = t;
        entry.text = b;
        if (draft || drawn.changed()) {
            entry.map = UUID.randomUUID().toString();
            MapImages.putDrawing(entry.map, drawn.pixels());
            if (draft) journal.entries.add(entry);
            EJNetClient.send(EJPayloads.SAVE_DRAWING, new EJPayloads.SaveImage(journal.id, entry.id, entry.map, t, b, drawn.pack()));
        } else if (wordsChanged) {
            EJNetClient.send(EJPayloads.SAVE_ENTRY, new EJPayloads.SaveEntry(journal.id, entry.id, t, b));
        }
    }

    private void deleteEntry() {
        Entry entry = editEntry;
        boolean draft = isNew;
        stopWriting();
        if (!draft) {
            journal.entries.remove(entry);
            if (entry.id.equals(journal.bookmark)) journal.bookmark = "";
            EJNetClient.send(EJPayloads.DELETE_ENTRY, new EJPayloads.DeleteEntry(journal.id, entry.id));
        }
        flows.clear();
        rebuild();
    }

    /** The note may have grown onto another page, and the caret may have moved there. A note beside a thing pushes the writing around it as it grows. */
    private void afterTyping() {
        if (caption != null && focus == caption) {
            Keepsake keepsake = editEntry.keepsake(captionId);
            if (keepsake != null) keepsake.note = caption.value();
            body.flowOn(editEntry);
        }
        if (focus != title) rebuild();
        if (focus != find && focus != caption) followCaret();
    }

    private void followCaret() {
        if (focus == title) {
            jump(book.pageOf(editEntry));
        } else if (focus == body) {
            int line = body.caretLine();
            for (int i = 0; i < book.pages.size(); i++) {
                BookPages.Page page = book.pages.get(i);
                if (page.entry == editEntry && line >= page.firstLine && line < page.firstLine + page.lineCount) {
                    jump(i);
                    return;
                }
            }
        }
    }

    private void writeIn(PageEditor editor) {
        focus = editor;
        followCaret();
    }

    private void closeFind(boolean keepResults) {
        String text = find.value().strip();
        find = null;
        focus = null;
        dragging = null;
        query = keepResults && !text.isEmpty() ? text : null;
        rebuild();
    }

    private void clearQuery() {
        query = null;
        rebuildHere();
    }

    @Override
    public void removed() {
        // the viewfinder comes back to this page, with this draft
        if (toViewfinder) {
            super.removed();
            return;
        }
        finishEditing();
        if (query != null) clearQuery();
        LAST_PAGE.put(journal.id, spread * 2);
        super.removed();
    }

    /** A trackpad sends many small scrolls, and they keep coming after a flick. They add up to one page, with a gap between turns. */
    @Override
    protected boolean ejScroll(double mx, double my, double amount) {
        if (amount == 0) return true;
        if (scrollSum * amount < 0) scrollSum = 0;
        scrollSum += amount;
        if (Math.abs(scrollSum) < 1) return true;
        int by = scrollSum < 0 ? 1 : -1;
        scrollSum = 0;
        long now = Util.getMillis();
        if (now - lastScrollTurn < TURN_GAP_MS) return true;
        lastScrollTurn = now;
        turn(by);
        return true;
    }

    private static boolean isEnter(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
    }

    @Override
    protected boolean ejKey(int keyCode) {
        if (pickable != null || carried != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                pickable = null;
                carried = null;
            }
            return true;
        }
        if (find != null && focus == find) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeFind(false);
            } else if (isEnter(keyCode)) {
                closeFind(true);
            } else {
                focus.keyPressed(keyCode);
                afterTyping();
            }
            return true;
        }
        if (focus != null) {
            deleteArmed = false;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishEditing();
                return true;
            }
            if (focus == title && (isEnter(keyCode) || keyCode == GLFW.GLFW_KEY_TAB || keyCode == GLFW.GLFW_KEY_DOWN)) {
                writeIn(body);
                return true;
            }
            if (focus == caption && (isEnter(keyCode) || keyCode == GLFW.GLFW_KEY_TAB)) {
                writeIn(body);
                return true;
            }
            if (focus == body && keyCode == GLFW.GLFW_KEY_TAB) {
                writeIn(title);
                return true;
            }
            if (focus == body && keyCode == GLFW.GLFW_KEY_UP && body.caretLine() == 0 && !Screen.hasShiftDown()) {
                writeIn(title);
                return true;
            }
            focus.keyPressed(keyCode);
            afterTyping();
            return true;
        }
        if (desk != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finishEditing();
                return true;
            }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
                if (Screen.hasShiftDown()) desk.redo();
                else desk.undo();
                return true;
            }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
                desk.redo();
                return true;
            }
        }
        if (editEntry != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finishEditing();
            return true;
        }
        if (query != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            clearQuery();
            return true;
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_RIGHT -> {
                turn(1);
                yield true;
            }
            case GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_LEFT -> {
                turn(-1);
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (pickable != null || carried != null) return true;
        if (focus == null || c < 32 || c == 127 || c == 167) return false;
        deleteArmed = false;
        focus.insertText(String.valueOf(c));
        afterTyping();
        return true;
    }

    // ---- for the test script

    public int spread() {
        return spread;
    }

    public int blankPage() {
        return book.blankPage;
    }

    public int pageOfEntry(String id) {
        Entry entry = journal.entry(id);
        return entry == null ? -1 : book.pageOf(entry);
    }

    public int pageCount() {
        return book.pages.size();
    }

    public String pageKind(int page) {
        return page < book.pages.size() ? book.pages.get(page).kind.name() : "";
    }

    public String entryOnPage(int page) {
        Entry entry = page < book.pages.size() ? book.pages.get(page).entry : null;
        return entry == null ? "" : entry.id;
    }

    public String titleText() {
        return title == null ? "" : title.value();
    }

    public String bodyText() {
        return body == null ? "" : body.value();
    }

    public String findText() {
        return find == null ? "" : find.value();
    }

    public String focusName() {
        return focus == null ? "" : focus == title ? "title" : focus == body ? "body" : focus == caption ? "caption" : "find";
    }

    public int caret() {
        return focus == null ? -1 : focus.cursor();
    }

    public int caretLine() {
        return focus == null ? -1 : focus.caretLine();
    }

    public String selectedText() {
        return focus == null ? "" : focus.getSelectedText();
    }

    public int editingPage() {
        return editEntry == null ? -1 : book.pageOf(editEntry);
    }

    public String editingId() {
        return editEntry == null ? "" : editEntry.id;
    }

    /** Where the forward page arrow is, from the book's left edge. */
    public int forwardArrowX() {
        return arrowX(true) - bookX;
    }

    /** The wear stage the server says this book shows. */
    public int wearShown() {
        return wear;
    }

    public boolean isFull() {
        return full();
    }

    public int caretPage() {
        if (editEntry == null || body == null) return -1;
        int line = body.caretLine();
        for (int i = 0; i < book.pages.size(); i++) {
            BookPages.Page page = book.pages.get(i);
            if (page.entry == editEntry && line >= page.firstLine && line < page.firstLine + page.lineCount) return i;
        }
        return -1;
    }

    public boolean deleteArmed() {
        return deleteArmed;
    }

    public boolean pickerOpen() {
        return pickable != null;
    }

    public List<Integer> pickerSlots() {
        return pickable == null ? List.of() : List.copyOf(pickable);
    }

    public int pickerCellX(int i) {
        return cellX(pageX(pickerPage()), i);
    }

    public int pickerCellY(int i) {
        return cellY(i);
    }

    public boolean carrying() {
        return carried != null;
    }

    public DrawingDesk desk() {
        return desk;
    }

    /** The screen position of a cell of the drawing you are working on. */
    public int canvasCellX(int cell) {
        return pageX(canvasPage()) + cell * DrawingDesk.SCALE + 1;
    }

    public int canvasCellY(int cell) {
        return canvasY() + cell * DrawingDesk.SCALE + 1;
    }

    public int menuX() {
        return pageX(canvasPage() ^ 1);
    }

    public int menuTop() {
        return pageTop;
    }

    public boolean sketchInProgress() {
        return stillSketching();
    }

    public boolean sketchReady() {
        return editEntry != null && editEntry.isSketch() && sketching != null && sketching.done();
    }

    public int keepsakesOnWriting() {
        return editEntry == null ? -1 : editEntry.keepsakes.size();
    }

    public int linesOnPage(int page) {
        return page < book.pages.size() ? book.pages.get(page).lineCount : 0;
    }

    public int lineRowOnPage(int page, int n) {
        BookPages.Page at = book.pages.get(page);
        return book.linesOf(at.entry).get(at.firstLine + n).row();
    }

    public String lineTextOnPage(int page, int n) {
        BookPages.Page at = book.pages.get(page);
        TextFlow.Line line = book.linesOf(at.entry).get(at.firstLine + n);
        String text = at.entry == editEntry ? body.value() : at.entry.text;
        return text.substring(line.begin(), line.end());
    }

    /** How many lines of writing on this page touch something taped down. */
    public int linesUnderKeepsakes(int page) {
        BookPages.Page at = book.pages.get(page);
        int seed = seed(at.entry), top = EntryLayout.of(at.entry).top(at.firstOfEntry), hits = 0;
        List<TextFlow.Line> lines = book.linesOf(at.entry);
        for (int i = at.firstLine; i < at.firstLine + at.lineCount; i++) {
            TextFlow.Line line = lines.get(i);
            String s = lineTextOnPage(page, i - at.firstLine);
            if (s.isBlank()) continue;
            int left = line.x() + Drift.shift(seed, i), right = left + Drift.xAt(this.font, s, s.length(), seed, i, false);
            int y = top + line.row() * PageEditor.LINE_H;
            for (Keepsake keepsake : at.entry.keepsakes) {
                int[] covered = keepsake.page == at.entryPage ? KeepsakeArt.covered(keepsake, y, y + 9) : null;
                if (covered != null && left < covered[1] && right > covered[0]) hits++;
            }
        }
        return hits;
    }

    /** Everything on a page that must not touch: what is written, and what is taped down. Each box is x0, y0, x1, y1 from the page's top left, then a name. */
    private List<Object[]> pieces(int page) {
        List<Object[]> out = new ArrayList<>();
        BookPages.Page at = book.pages.get(page);
        String head = at.kind == BookPages.Kind.CONTENTS ? "Contents" : at.kind == BookPages.Kind.PLACES ? (find != null || query != null ? "Found" : "Places I've been") : null;
        if (head != null) {
            int hx = headingSpot(page % 2 == 0)[0];
            out.add(new Object[]{new int[]{hx, -3, hx + Drift.width(this.font, head, true), 7}, "heading"});
        }
        if (at.entry == null) return out;
        Entry entry = at.entry;
        EntryLayout layout = EntryLayout.of(entry);
        boolean editing = entry == editEntry;
        int x = pageX(page);
        if (at.firstOfEntry) {
            String shown = editing ? title.value() : fitTitle(entry, entry.title);
            if (!shown.isEmpty()) {
                int w = titleWidth(entry, shown), tx = titleStart(entry, w);
                out.add(new Object[]{new int[]{tx, layout.titleY - 1, tx + w, layout.titleY + 9}, "title"});
            }
            int[] day = daySpot(entry, x, editing);
            if (day != null) out.add(new Object[]{new int[]{day[0] - x, day[1] - pageTop - 1, day[0] - x + this.font.width("day " + entry.day), day[1] - pageTop + 9}, "day"});
        }
        int seed = seed(entry), top = layout.top(at.firstOfEntry);
        List<TextFlow.Line> lines = book.linesOf(entry);
        String text = editing ? body.value() : entry.text;
        for (int i = at.firstLine; i < at.firstLine + at.lineCount; i++) {
            TextFlow.Line line = lines.get(i);
            String s = text.substring(line.begin(), line.end());
            if (s.isBlank()) continue;
            int left = line.x() + Drift.shift(seed, i), y = top + line.row() * PageEditor.LINE_H;
            out.add(new Object[]{new int[]{left, y - 1, left + Drift.xAt(this.font, s, s.length(), seed, i, false), y + 9}, "line " + i});
        }
        for (Keepsake keepsake : entry.keepsakes) {
            if (keepsake.page == at.entryPage) out.add(new Object[]{KeepsakeArt.bounds(keepsake), "thing " + KeepsakeArt.name(keepsake), keepsake});
        }
        for (Captions.Box box : Captions.place(this.font, entry, at.entryPage, book.whereOf(entry))) {
            for (int i = 0; i < box.widths().length; i++) {
                int y = box.y() + i * PageEditor.LINE_H;
                out.add(new Object[]{new int[]{box.x(), y - 1, box.x() + box.widths()[i], y + 9}, "note of " + KeepsakeArt.name(box.keepsake())});
            }
        }
        return out;
    }

    /** The other places on the maps shown in the last frame: each name with its box on screen, and its icon's box. */
    public List<MarkName> markNames() {
        return List.copyOf(markNames);
    }

    /** What is wrong with how a page is laid out: things that touch, or that leave the page. Empty if nothing. Writing is checked against a thing's real shape, row by row, because it hugs that shape. */
    public List<String> layoutProblems(int page) {
        List<String> problems = new ArrayList<>();
        List<Object[]> pieces = pieces(page);
        boolean left = page % 2 == 0;
        for (int i = 0; i < pieces.size(); i++) {
            int[] a = (int[]) pieces.get(i)[0];
            String an = (String) pieces.get(i)[1];
            if (!an.startsWith("thing")) {
                // the fold itself starts CLEAR pixels past where writing may start. A pixel of hand drift stays inside that gap.
                int reach = Corners.start(left, wear, a[1], a[3] - 1) - Corners.CLEAR, from = Corners.stop(left, wear, a[1], a[3] - 1) + Corners.CLEAR;
                if (reach > 0 && a[0] < reach || from < EntryLayout.PAGE_W && a[2] > from) problems.add(an + " lies on the folded corner at " + Arrays.toString(a));
            }
            // a thing may reach onto the paper past the writing area. Writing may not.
            int edge = an.startsWith("thing") ? 8 : 0;
            if (a[0] < -edge || a[2] > PAGE_W + edge || a[1] < -6 || a[3] > EntryLayout.BOTTOM + 3 + edge) problems.add(an + " leaves the page at " + Arrays.toString(a));
            for (int j = i + 1; j < pieces.size(); j++) {
                int[] b = (int[]) pieces.get(j)[0];
                String bn = (String) pieces.get(j)[1];
                boolean aThing = an.startsWith("thing"), bThing = bn.startsWith("thing");
                if (aThing && bThing) continue;
                boolean writing = !an.startsWith("note") && !bn.startsWith("note") && (aThing || bThing);
                boolean touch;
                if (writing) {
                    int[] text = aThing ? b : a;
                    int[] span = KeepsakeArt.covered((Keepsake) (aThing ? pieces.get(i) : pieces.get(j))[2], text[1], text[3]);
                    touch = span != null && text[0] < span[1] && span[0] < text[2];
                } else {
                    touch = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
                }
                if (touch) problems.add(an + " touches " + bn);
            }
        }
        return problems;
    }

    /** Which layout this entry has, for the test sheet. */
    public String layoutOf(String entryId) {
        Entry entry = journal.entry(entryId);
        if (entry == null && editEntry != null && editEntry.id.equals(entryId)) entry = editEntry;
        if (entry == null) return "";
        EntryLayout at = EntryLayout.of(entry);
        return (at.caption ? "title under the picture" : at.centered ? "centered title" : "title at " + at.titleX) + ", day " + at.day.name().toLowerCase(Locale.ROOT) + ", writing at " + at.bodyX + (at.indent > 0 ? ", first line in" : "");
    }

    /** Spots for the script to click, from the book's top left: the title's middle, and the start of the writing. */
    public int[] spotsOf(String entryId) {
        Entry entry = journal.entry(entryId);
        if (entry == null && editEntry != null && editEntry.id.equals(entryId)) entry = editEntry;
        int page = entry == null ? -1 : book.pageOf(entry);
        if (page < 0) return new int[]{0, 0, 0, 0};
        EntryLayout at = EntryLayout.of(entry);
        int x = pageX(page) - bookX;
        return new int[]{titleX(entry, pageX(page)) - bookX + 4, pageTop - bookY + at.titleY + 4, x + at.bodyX + at.indent + 2, pageTop - bookY + at.bodyTop + 4};
    }

    /** The note beside the thing at this place in the entry, and where it sits from the book's top left: x, y. */
    public String noteOf(String entryId, int keepsake) {
        Entry entry = entryId.equals(editEntry == null ? "" : editEntry.id) ? editEntry : journal.entry(entryId);
        return entry == null || keepsake >= entry.keepsakes.size() ? "" : Captions.text(entry.keepsakes.get(keepsake));
    }

    public int[] noteSpot(String entryId, int keepsake) {
        Entry entry = entryId.equals(editEntry == null ? "" : editEntry.id) ? editEntry : journal.entry(entryId);
        if (entry == null || keepsake >= entry.keepsakes.size()) return null;
        Keepsake k = entry.keepsakes.get(keepsake);
        int page = book.pageOf(entry) + k.page;
        for (Captions.Box box : Captions.place(this.font, entry, k.page, book.whereOf(entry))) {
            if (box.keepsake().id.equals(k.id)) return new int[]{pageX(page) - bookX + box.x(), pageTop - bookY + box.y(), box.side().ordinal()};
        }
        return null;
    }

    /** Where a thing sits: its page in the entry, x, y on the page, and its middle from the book's top left. */
    public int[] keepsakeSpot(String entryId, int keepsake) {
        Entry entry = entryId.equals(editEntry == null ? "" : editEntry.id) ? editEntry : journal.entry(entryId);
        if (entry == null || keepsake >= entry.keepsakes.size()) return null;
        Keepsake k = entry.keepsakes.get(keepsake);
        int page = book.pageOf(entry) + k.page, half = KeepsakeArt.size(k) / 2;
        return new int[]{k.page, k.x, k.y, pageX(page) - bookX + k.x + half, pageTop - bookY + k.y + half};
    }

    /** How many notes on this page were cut short because the page had no room for them all. */
    public int notesCutOnPage(int page) {
        BookPages.Page at = book.pages.get(page);
        if (at.entry == null) return 0;
        int cut = 0;
        for (Captions.Box box : Captions.place(this.font, at.entry, at.entryPage, book.whereOf(at.entry))) {
            if (box.cut()) cut++;
        }
        return cut;
    }

    public int rowsOnPage(int page) {
        return page < book.pages.size() ? book.pages.get(page).rows.size() : 0;
    }

    public boolean grabbing() {
        return grab != null && grab.moving;
    }

    public String loosePapers() {
        return LoosePapers.lastDrawn();
    }

    public int pagesOfEntry(String id) {
        Entry entry = journal.entry(id);
        int count = 0;
        for (BookPages.Page page : book.pages) {
            if (entry != null && page.entry == entry) count++;
        }
        return count;
    }
}
