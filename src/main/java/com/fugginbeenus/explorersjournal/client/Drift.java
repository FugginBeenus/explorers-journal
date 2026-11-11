package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.config.EJConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Handwriting on free pages. Each word is drawn on its own: the line slopes a little, starts a pixel off, and word gaps change by a pixel. The seed keeps a page the same every time. */
final class Drift {
    private static final double[] SLOPES = {-1 / 90.0, -1 / 170.0, 1 / 170.0, 1 / 200.0, 1 / 140.0, -1 / 120.0};

    private Drift() {}

    private static int hash(int seed, int a, int b) {
        int h = seed * 31 + a;
        h = h * 31 + b;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        return h ^ h >>> 13;
    }

    static int shift(int seed, int line) {
        if (!EJConfig.get().textDrift) return 0;
        return Math.floorMod(hash(seed, line, 7), 3) - 1;
    }

    private static double slope(int seed, int line) {
        if (!EJConfig.get().textDrift) return 0;
        return SLOPES[Math.floorMod(hash(seed, line / 4, 11), SLOPES.length)];
    }

    static int gap(int seed, int line, int word) {
        if (!EJConfig.get().textDrift) return 0;
        return Math.floorMod(hash(seed, line, word), 2);
    }

    static int width(Font font, String s, boolean bold) {
        return bold ? font.width(Component.literal(s).withStyle(ChatFormatting.BOLD)) : font.width(s);
    }

    static void draw(GuiGraphics ctx, Font font, String s, int x, int y, int colour, int seed, int line, boolean bold) {
        double slope = slope(seed, line);
        int cx = x, word = 0, start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i < s.length() && s.charAt(i) != ' ') continue;
            if (i > start) {
                String w = s.substring(start, i);
                int wy = y + (int) Math.round((cx - x) * slope);
                if (bold) EJGui.writeBold(ctx, font, w, cx, wy, colour);
                else EJGui.write(ctx, font, w, cx, wy, colour);
                cx += width(font, w, bold);
            }
            if (i < s.length()) cx += font.width(" ") + gap(seed, line, word++);
            start = i + 1;
        }
    }

    /** Where the caret goes before this character, from the line's start. */
    static int xAt(Font font, String s, int index, int seed, int line, boolean bold) {
        int x = 0, word = 0;
        for (int i = 0; i < index && i < s.length(); i++) {
            char c = s.charAt(i);
            x += width(font, String.valueOf(c), bold);
            if (c == ' ') x += gap(seed, line, word++);
        }
        return x;
    }

    static int yAt(int x, int seed, int line) {
        return (int) Math.round(x * slope(seed, line));
    }

    static int indexAt(Font font, String s, double dx, int seed, int line, boolean bold) {
        for (int i = 0; i < s.length(); i++) {
            int a = xAt(font, s, i, seed, line, bold), b = xAt(font, s, i + 1, seed, line, bold);
            if (dx < (a + b) / 2.0) return i;
        }
        return s.length();
    }
}
