package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.sketch.PixelCanvas;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The drawing you are working on, and the tools for it. The canvas sits on your page at exactly 2x. The tools fill the page across from it, and only while you draw. */
public final class DrawingDesk {
    public enum Tool {
        FEATHER("Feather: draw", "Drag on the drawing.", Items.FEATHER),
        PAPER("Paper: erase", "Drag to erase.", Items.PAPER),
        BUCKET("Water bucket: fill", "Click a patch to fill it.", Items.WATER_BUCKET),
        STRING("String: a straight line", "Drag a straight line.", Items.STRING);

        final String name, hint;
        final ItemStack icon;

        Tool(String name, String hint, Item item) {
            this.name = name;
            this.hint = hint;
            this.icon = new ItemStack(item);
        }
    }

    public static final int SCALE = 2, SHOWN = PixelCanvas.SIZE * SCALE;
    private static final int BOX = 18, CELL = 20, COLUMNS = 6, TOOLS_Y = 12, INKS_HEAD_Y = 35, INKS_Y = 48, HINT_Y = 112, GUIDE_EVERY = 8;
    private static final int UNDO_CELL = 4, REDO_CELL = 5, GUIDE = 0x66A89C86;
    private static final DyeColor[] INK_ORDER = {DyeColor.BLACK, DyeColor.GRAY, DyeColor.LIGHT_GRAY, DyeColor.WHITE, DyeColor.BROWN, DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK};
    private static final String[] UNDO_ART = {
            "...X........",
            "..XX........",
            ".XXXXXXXX...",
            "..XX.....X..",
            "...X......X.",
            "..........X.",
            ".........X..",
            "...XXXXXX...",
    };
    private static final String[] REDO_ART = {
            "........X...",
            "........XX..",
            "...XXXXXXXX.",
            "..X.....XX..",
            ".X......X...",
            ".X..........",
            "..X.........",
            "...XXXXXX...",
    };
    private static Tool lastTool = Tool.FEATHER;
    private static DyeColor lastInk = DyeColor.BLACK;

    private final PixelCanvas canvas;
    private boolean stroking, erasing, straight;
    private int fromX, fromY, lastX, lastY;

    public DrawingDesk(PixelCanvas canvas) {
        this.canvas = canvas;
    }

    public PixelCanvas canvas() {
        return canvas;
    }

    public boolean stroking() {
        return stroking;
    }

    public void undo() {
        release();
        canvas.undo();
    }

    public void redo() {
        release();
        canvas.redo();
    }

    private static int inkOf(DyeColor color) {
        return color.getId() + 1;
    }

    // ---- the canvas. x and y are its top left corner on the screen.

    private static int cell(double from) {
        return Mth.clamp((int) Math.floor(from / SCALE), 0, PixelCanvas.SIZE - 1);
    }

    /** True if the mouse is on the canvas and the press began a stroke. Right drag always erases. */
    boolean press(double mx, double my, int button, int x, int y) {
        if ((button != 0 && button != 1) || !EJGui.over(mx, my, x, y, SHOWN, SHOWN)) return false;
        release();
        int cx = cell(mx - x), cy = cell(my - y);
        EJGui.scribble();
        canvas.beginStroke();
        if (button == 0 && lastTool == Tool.BUCKET) {
            canvas.fill(cx, cy, inkOf(lastInk));
            canvas.endStroke();
            return true;
        }
        stroking = true;
        erasing = button == 1 || lastTool == Tool.PAPER;
        straight = button == 0 && lastTool == Tool.STRING;
        fromX = lastX = cx;
        fromY = lastY = cy;
        if (!straight) canvas.line(cx, cy, cx, cy, strokeInk());
        return true;
    }

    /** A fast mouse skips cells, so each move draws a line from the last one. */
    void drag(double mx, double my, int x, int y) {
        if (!stroking) return;
        int cx = cell(mx - x), cy = cell(my - y);
        if (!straight && (cx != lastX || cy != lastY)) canvas.line(lastX, lastY, cx, cy, strokeInk());
        lastX = cx;
        lastY = cy;
    }

    void release() {
        if (!stroking) return;
        if (straight) canvas.line(fromX, fromY, lastX, lastY, strokeInk());
        stroking = false;
        canvas.endStroke();
    }

    private int strokeInk() {
        return erasing ? 0 : inkOf(lastInk);
    }

    void drawCanvas(GuiGraphics ctx, int x, int y, int mx, int my, boolean ready) {
        for (int cy = 0; cy <= PixelCanvas.SIZE; cy += GUIDE_EVERY) {
            for (int cx = 0; cx <= PixelCanvas.SIZE; cx += GUIDE_EVERY) {
                ctx.fill(x + Math.min(cx * SCALE, SHOWN - 1), y + Math.min(cy * SCALE, SHOWN - 1), x + Math.min(cx * SCALE, SHOWN - 1) + 1, y + Math.min(cy * SCALE, SHOWN - 1) + 1, GUIDE);
            }
        }
        EJGui.imageTo(ctx, MapImages.live(canvas), x, y, SHOWN, SHOWN, PixelCanvas.SIZE);
        if (stroking && straight) {
            int[] cells = PixelCanvas.lineCells(fromX, fromY, lastX, lastY);
            for (int i = 0; i < cells.length; i += 2) pixel(ctx, x, y, cells[i], cells[i + 1], inkColor());
        } else if (ready && !stroking && EJGui.over(mx, my, x, y, SHOWN, SHOWN)) {
            int cx = cell(mx - x), cy = cell(my - y);
            if (lastTool == Tool.PAPER) {
                int px = x + cx * SCALE, py = y + cy * SCALE;
                EJGui.box(ctx, px - 1, py - 1, SCALE + 2, SCALE + 2, EJGui.INK_FAINT);
            } else {
                pixel(ctx, x, y, cx, cy, inkColor());
            }
        }
    }

    private static int inkColor() {
        return 0xFF000000 | PixelCanvas.INK[inkOf(lastInk)];
    }

    private static void pixel(GuiGraphics ctx, int x, int y, int cx, int cy, int color) {
        ctx.fill(x + cx * SCALE, y + cy * SCALE, x + (cx + 1) * SCALE, y + (cy + 1) * SCALE, color);
    }

    // ---- the menu, on the page across. x is that page, and top is the top of its writing area.

    public static int toolX(int x, int i) {
        return x + 1 + i * CELL;
    }

    public static int toolY(int top) {
        return top + TOOLS_Y;
    }

    public static int inkX(int x, int i) {
        return x + 1 + i % COLUMNS * CELL;
    }

    public static int inkY(int top, int i) {
        return top + INKS_Y + i / COLUMNS * CELL;
    }

    private static boolean overBox(double mx, double my, int x, int y) {
        return EJGui.over(mx, my, x, y, BOX, BOX);
    }

    void drawMenu(GuiGraphics ctx, Font font, int x, int top, int mx, int my) {
        heading(ctx, font, "Tools", x, top - 2);
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            int cx = toolX(x, i), cy = toolY(top);
            EJGui.cell(ctx, cx, cy, BOX, tools[i] == lastTool, overBox(mx, my, cx, cy));
            ctx.renderItem(tools[i].icon, cx + 1, cy + 1);
        }
        drawStep(ctx, UNDO_ART, x, top, UNDO_CELL, canvas.canUndo(), mx, my);
        drawStep(ctx, REDO_ART, x, top, REDO_CELL, canvas.canRedo(), mx, my);

        heading(ctx, font, "Inks", x, top + INKS_HEAD_Y);
        for (int i = 0; i < INK_ORDER.length; i++) {
            int cx = inkX(x, i), cy = inkY(top, i);
            EJGui.cell(ctx, cx, cy, BOX, INK_ORDER[i] == lastInk, overBox(mx, my, cx, cy));
            ctx.renderItem(new ItemStack(DyeItem.byColor(INK_ORDER[i])), cx + 1, cy + 1);
        }

        EJGui.write(ctx, font, lastTool.hint, x + 2, top + HINT_Y, EJGui.INK_FAINT);
        EJGui.write(ctx, font, "Right drag erases.", x + 2, top + HINT_Y + PageEditor.LINE_H, EJGui.INK_FAINT);
    }

    private static void heading(GuiGraphics ctx, Font font, String text, int x, int y) {
        EJGui.writeBold(ctx, font, text, x + 2, y, EJGui.INK);
        EJGui.line(ctx, x, y + 10, x + 127, y + 11, EJGui.INK);
    }

    private static void drawStep(GuiGraphics ctx, String[] art, int x, int top, int cell, boolean ready, int mx, int my) {
        int cx = toolX(x, cell), cy = toolY(top);
        EJGui.cell(ctx, cx, cy, BOX, false, ready && overBox(mx, my, cx, cy));
        EJGui.art(ctx, art, cx + 3, cy + 5, ready ? EJGui.INK : EJGui.INK_FAINT);
    }

    /** True if the click was on a tool, a step, or an ink. */
    boolean clickMenu(double mx, double my, int x, int top) {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            if (overBox(mx, my, toolX(x, i), toolY(top))) {
                EJGui.click();
                lastTool = tools[i];
                return true;
            }
        }
        if (overBox(mx, my, toolX(x, UNDO_CELL), toolY(top))) {
            if (canvas.canUndo()) EJGui.click();
            undo();
            return true;
        }
        if (overBox(mx, my, toolX(x, REDO_CELL), toolY(top))) {
            if (canvas.canRedo()) EJGui.click();
            redo();
            return true;
        }
        for (int i = 0; i < INK_ORDER.length; i++) {
            if (overBox(mx, my, inkX(x, i), inkY(top, i))) {
                EJGui.click();
                lastInk = INK_ORDER[i];
                // picking an ink means you want to draw with it
                if (lastTool == Tool.PAPER) lastTool = Tool.FEATHER;
                return true;
            }
        }
        return false;
    }

    /** What the thing under the mouse does, or null. */
    List<Component> menuHint(double mx, double my, int x, int top) {
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            if (overBox(mx, my, toolX(x, i), toolY(top))) return List.of(Component.literal(tools[i].name));
        }
        String key = Minecraft.ON_OSX ? "Command" : "Control";
        if (overBox(mx, my, toolX(x, UNDO_CELL), toolY(top))) return stepHint("Undo", key + " and Z");
        if (overBox(mx, my, toolX(x, REDO_CELL), toolY(top))) return stepHint("Redo", key + " and Y");
        for (int i = 0; i < INK_ORDER.length; i++) {
            if (overBox(mx, my, inkX(x, i), inkY(top, i))) return List.of(new ItemStack(DyeItem.byColor(INK_ORDER[i])).getHoverName());
        }
        return null;
    }

    private static List<Component> stepHint(String name, String keys) {
        return List.of(Component.literal(name), Component.literal(keys).withStyle(ChatFormatting.GRAY));
    }

    // ---- for the test script

    public static Tool tool() {
        return lastTool;
    }

    public static int ink() {
        return inkOf(lastInk);
    }

    public static int inkMenuIndex(DyeColor color) {
        for (int i = 0; i < INK_ORDER.length; i++) {
            if (INK_ORDER[i] == color) return i;
        }
        return -1;
    }
}
