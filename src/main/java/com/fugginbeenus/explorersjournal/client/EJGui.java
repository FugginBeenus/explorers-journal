package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.util.EJId;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

public final class EJGui {
    private EJGui() {}

    public static final int BOOK_W = 312;
    public static final int BOOK_H = 196;
    // The textures have room around the book for what pokes out.
    private static final int ART_LEFT = 12, ART_TOP = 12, ART_W = 336, ART_H = 232;
    private static final ResourceLocation[] COVER = new ResourceLocation[5];
    private static final ResourceLocation[] ASKEW = new ResourceLocation[5];
    private static final ResourceLocation[] PAGES = new ResourceLocation[5];
    private static final ResourceLocation RIBBON_RED = EJId.of("textures/gui/book/ribbon_red.png");
    private static final ResourceLocation RIBBON_BLUE = EJId.of("textures/gui/book/ribbon_blue.png");
    public static final int RIBBON_W = 30;
    // the first row below the bend over the cover, where a pulled ribbon gets longer
    private static final int RIBBON_STRETCH_ROW = 11;
    private static final int SCRIBBLE_GAP_MS = 1500;
    private static long lastScribble;

    static {
        for (int i = 0; i < PAGES.length; i++) {
            COVER[i] = EJId.of("textures/gui/book/cover" + i + ".png");
            ASKEW[i] = EJId.of("textures/gui/book/askew" + i + ".png");
            PAGES[i] = EJId.of("textures/gui/book/stage" + i + ".png");
        }
    }

    public static final int INK = 0xFF36281B;
    public static final int INK_SOFT = 0xFF7A6652;
    public static final int INK_FAINT = 0xFFA89C86;
    public static final int INK_RED = 0xFFA11F18;
    public static final int PAPER = 0xFFFFF9EC;
    public static final int SELECTION = 0xFFD8C694;
    private static final int TAB_INK = 0xFF281C12;
    public static final int SLIP_BOTTOM = 208;
    public static final int TAPE_PAPER = 0xFFCEB27E, TAPE_LIGHT = 0xFFE2CEA0, TAPE_DARK = 0xFFA68A5C;
    private static final int TAPE_TOP = 0xD8E2CEA0, TAPE_BODY = 0xD8CEB27E, TAPE_LOW = 0xD8A68A5C, TAPE_SHADOW = 0x2E5A4428;
    // how many pixels each row of a strip loses at a torn end
    private static final int[][] TORN = {{1, 0, 2, 0, 1, 0}, {0, 2, 0, 1, 0, 2}, {2, 0, 1, 2, 0, 1}};

    public static void cover(GuiGraphics ctx, int stage, int x, int y) {
        image(ctx, COVER[stage], x - ART_LEFT, y - ART_TOP, ART_W, ART_H);
    }

    /** Whole pages that came out of line. They lie under the loose papers and the top pages. */
    public static void askew(GuiGraphics ctx, int stage, int x, int y) {
        if (stage > 0) image(ctx, ASKEW[stage], x - ART_LEFT, y - ART_TOP, ART_W, ART_H);
    }

    public static void pages(GuiGraphics ctx, int stage, int x, int y) {
        image(ctx, PAGES[stage], x - ART_LEFT, y - ART_TOP, ART_W, ART_H);
    }

    public static void redRibbon(GuiGraphics ctx, int x, int y, int pull) {
        ribbon(ctx, RIBBON_RED, x, y, 29, pull);
    }

    public static void blueRibbon(GuiGraphics ctx, int x, int y, int pull) {
        ribbon(ctx, RIBBON_BLUE, x, y, 23, pull);
    }

    /** A pulled ribbon slides out from under the pages: its bend over the cover stays put, and the part that hangs gets longer. */
    private static void ribbon(GuiGraphics ctx, ResourceLocation tex, int x, int y, int h, int pull) {
        RenderSystem.enableBlend();
        ctx.blit(tex, x, y, 0, 0, 31, RIBBON_STRETCH_ROW + 1, 31, h);
        for (int i = 1; i <= pull; i++) ctx.blit(tex, x, y + RIBBON_STRETCH_ROW + i, 0, RIBBON_STRETCH_ROW, 31, 1, 31, h);
        ctx.blit(tex, x, y + RIBBON_STRETCH_ROW + 1 + pull, 0, RIBBON_STRETCH_ROW + 1, 31, h - RIBBON_STRETCH_ROW - 1, 31, h);
        RenderSystem.disableBlend();
    }

    /** A cut paper tab, glued to a page edge. Only its end shows past the book, with its two outer corners snipped, like the slips. It slides out a little under the mouse. */
    public static void tab(GuiGraphics ctx, Font font, Section section, int bookX, int bookY, boolean hover) {
        int x0 = bookX + 292, x1 = bookX + BOOK_W + 7 + (hover ? 2 : 0), y0 = bookY + section.y, y1 = y0 + 16;
        ctx.fill(x0 + 1, y0 + 2, x1 + 2, y1, 0x641C0F00);
        ctx.fill(x0 + 1, y1, x1 + 1, y1 + 1, 0x641C0F00);
        ctx.fill(x0, y0, x1, y1, section.dark);
        ctx.fill(x1, y0 + 1, x1 + 1, y1 - 1, section.dark);
        ctx.fill(x0, y0 + 1, x1, y1 - 1, section.paper);
        ctx.fill(x0, y0 + 1, x1 - 1, y0 + 2, section.light);
        write(ctx, font, section.letter, x1 - font.width(section.letter), y0 + 5, TAB_INK);
    }

    /** A paper slip tucked under the bottom of the book, the same paper as the tabs. It shows only while you write. */
    public static void slip(GuiGraphics ctx, Font font, String text, int x, int bookY, int w, int paper, int light, int dark, boolean hover) {
        int y0 = bookY + 186, y1 = bookY + SLIP_BOTTOM + (hover ? 2 : 0), x1 = x + w;
        ctx.fill(x + 1, y0 + 1, x1 + 1, y1 + 1, 0x641C0F00);
        ctx.fill(x, y0, x1, y1 - 1, dark);
        ctx.fill(x + 1, y1 - 1, x1 - 1, y1, dark);
        ctx.fill(x + 1, y0, x1 - 1, y1 - 1, paper);
        ctx.fill(x + 1, y0, x + 2, y1 - 1, light);
        write(ctx, font, text, x + (w - font.width(text) + 1) / 2, y1 - 10, TAB_INK);
    }

    /** A strip of paper tape: darker than the page, a little see-through, torn at both ends, with a thin shadow. rise is how many pixels it climbs or drops over its length. */
    public static void tape(GuiGraphics ctx, int x, int y, int length, int rise, int height, int startTear, int endTear, int crease) {
        int[] start = TORN[Math.floorMod(startTear, TORN.length)], end = TORN[Math.floorMod(endTear, TORN.length)];
        for (int i = 0; i < length; i++) {
            int yy = y + Math.round((float) i * rise / Math.max(1, length - 1));
            for (int j = 0; j < height; j++) {
                if (i < start[j] || i >= length - end[j]) continue;
                int colour = j == 0 ? TAPE_TOP : j == height - 1 ? TAPE_LOW : i == crease ? TAPE_TOP : TAPE_BODY;
                ctx.fill(x + i, yy + j, x + i + 1, yy + j + 1, colour);
            }
            int last = height - 1;
            if (i >= start[last] && i < length - end[last]) ctx.fill(x + i, yy + height, x + i + 1, yy + height + 1, TAPE_SHADOW);
        }
    }

    /** Vanilla's own page arrows, so a resource pack changes them too. */
    public static void pageArrow(GuiGraphics ctx, int x, int y, boolean next, boolean hover) {
        RenderSystem.enableBlend();
        //? if >=1.21 {
        /*String name = (next ? "page_forward" : "page_backward") + (hover ? "_highlighted" : "");
        ctx.blitSprite(EJId.of("minecraft", "widget/" + name), x, y, 23, 13);
        *///?} else {
        ctx.blit(EJId.of("minecraft", "textures/gui/book.png"), x, y, hover ? 23 : 0, next ? 192 : 205, 23, 13);
        //?}
        RenderSystem.disableBlend();
    }

    public static void handLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int wobble, int colour) {
        int mx = (x0 + x1) / 2, my = (y0 + y1) / 2 + wobble;
        line(ctx, x0, y0, mx, my, colour);
        line(ctx, mx, my, x1, y1, colour);
    }

    /** One of vanilla's own 8 x 8 map icons, so a resource pack changes it too. */
    public static void mapIcon(GuiGraphics ctx, String marker, int x, int y) {
        RenderSystem.enableBlend();
        //? if >=1.21 {
        /*ctx.blit(EJId.of("minecraft", "textures/map/decorations/" + marker + ".png"), x, y, 0, 0, 8, 8, 8, 8);
        *///?} else {
        int index = legacyIcon(marker);
        ctx.blit(EJId.of("minecraft", "textures/map/map_icons.png"), x, y, (index % 16) * 8, (index / 16) * 8, 8, 8, 128, 128);
        //?}
        RenderSystem.disableBlend();
    }

    /** Where each icon sits in 1.20.1's single sheet of map icons. */
    private static int legacyIcon(String marker) {
        return switch (marker) {
            case "red_marker" -> 2;
            case "woodland_mansion" -> 8;
            case "ocean_monument" -> 9;
            case "orange_banner" -> 11;
            case "magenta_banner" -> 12;
            case "light_blue_banner" -> 13;
            case "yellow_banner" -> 14;
            case "lime_banner" -> 15;
            case "pink_banner" -> 16;
            case "gray_banner" -> 17;
            case "cyan_banner" -> 19;
            case "purple_banner" -> 20;
            case "blue_banner" -> 21;
            case "brown_banner", "plains_village", "desert_village", "savanna_village", "snowy_village", "taiga_village" -> 22;
            case "green_banner", "swamp_hut", "jungle_temple" -> 23;
            case "red_banner", "trial_chambers" -> 24;
            case "black_banner" -> 25;
            case "red_x" -> 26;
            default -> 2;
        };
    }

    /** A small hand-drawn arrow, with its head at the second point. */
    public static void arrow(GuiGraphics ctx, int x0, int y0, int x1, int y1, int colour) {
        line(ctx, x0, y0, x1, y1, colour);
        double len = Math.max(1, Math.hypot(x1 - x0, y1 - y0)), ux = (x1 - x0) / len, uy = (y1 - y0) / len;
        for (int s = -1; s <= 1; s += 2) {
            double a = Math.toRadians(35) * s, c = Math.cos(a), n = Math.sin(a);
            int hx = (int) Math.round(x1 - 3 * (ux * c - uy * n)), hy = (int) Math.round(y1 - 3 * (ux * n + uy * c));
            line(ctx, x1, y1, hx, hy, colour);
        }
    }

    public static void dots(GuiGraphics ctx, int x0, int x1, int y, int colour) {
        for (int x = x0; x < x1; x += 3) ctx.fill(x, y, x + 1, y + 1, colour);
    }

    public static void image(GuiGraphics ctx, ResourceLocation tex, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        ctx.blit(tex, x, y, 0, 0, w, h, w, h);
        RenderSystem.disableBlend();
    }

    /** A square texture of this many pixels, drawn larger. */
    public static void imageTo(GuiGraphics ctx, ResourceLocation tex, int x, int y, int w, int h, int size) {
        RenderSystem.enableBlend();
        ctx.blit(tex, x, y, w, h, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
    }

    /** One slot of the drawing tools: a lighter square of paper, with a red edge on what you have chosen. */
    public static void cell(GuiGraphics ctx, int x, int y, int size, boolean chosen, boolean hover) {
        ctx.fill(x, y, x + size, y + size, 0xFFFAF2DC);
        box(ctx, x, y, size, size, chosen ? INK_RED : hover ? INK : INK_FAINT);
    }

    public static void box(GuiGraphics ctx, int x, int y, int w, int h, int colour) {
        ctx.fill(x, y, x + w, y + 1, colour);
        ctx.fill(x, y + h - 1, x + w, y + h, colour);
        ctx.fill(x, y + 1, x + 1, y + h - 1, colour);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** A small hand mark, drawn from rows of text. X is ink. */
    public static void art(GuiGraphics ctx, String[] rows, int x, int y, int colour) {
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (rows[r].charAt(c) == 'X') ctx.fill(x + c, y + r, x + c + 1, y + r + 1, colour);
            }
        }
    }

    public static void backdrop(GuiGraphics ctx, int width, int height) {
        ctx.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
    }

    public static void write(GuiGraphics ctx, Font font, String s, int x, int y, int colour) {
        ctx.drawString(font, s, x, y, colour, false);
    }

    /** Words over map ink, with a thin edge of paper around each letter so they still read. */
    public static void writeOver(GuiGraphics ctx, Font font, String s, int x, int y, int colour) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) ctx.drawString(font, s, x + dx, y + dy, PAPER, false);
            }
        }
        ctx.drawString(font, s, x, y, colour, false);
    }

    public static void writeBold(GuiGraphics ctx, Font font, String s, int x, int y, int colour) {
        ctx.drawString(font, net.minecraft.network.chat.Component.literal(s).withStyle(net.minecraft.ChatFormatting.BOLD), x, y, colour, false);
    }

    public static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void line(GuiGraphics ctx, int x0, int y0, int x1, int y1, int colour) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x1 > x0 ? 1 : -1, sy = y1 > y0 ? 1 : -1;
        int err = dx - dy;
        while (true) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, colour);
            if (x0 == x1 && y0 == y1) return;
            int e = 2 * err;
            if (e > -dy) { err -= dy; x0 += sx; }
            if (e < dx) { err += dx; y0 += sy; }
        }
    }

    public static void pageTurn() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0f));
    }

    public static void stickDown() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0f));
    }

    public static void peelOff() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_PICKUP, 1.0f));
    }

    /** A pen on paper. It does not start again on every short stroke. */
    public static void scribble() {
        long now = net.minecraft.Util.getMillis();
        if (now - lastScribble < SCRIBBLE_GAP_MS) return;
        lastScribble = now;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.0f, 0.35f));
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }
}
