package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;
import com.fugginbeenus.explorersjournal.journal.Journal;
import com.fugginbeenus.explorersjournal.journal.Keepsake;
import com.fugginbeenus.explorersjournal.map.MapDrawer;
import com.fugginbeenus.explorersjournal.util.EJId;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Papers slipped between the pages. Each one shows only when the book holds something like it, and a worn book holds more. Each journal keeps its own spots. The pages, drawn next, hide their inner ends. */
final class LoosePapers {
    private record Paper(ResourceLocation texture, int w, int h) {}

    // nearly black at alpha 100, as ABGR
    private static final int SHADOW = 0x64000F1C;
    private static final int MAP_W = 66, MAP_H = 40, MAP_INSET = 3, PHOTO = 46;
    private static final ResourceLocation MAP_PAPER = EJId.of("minecraft", "textures/map/map_background.png");
    private static final Map<String, Paper> MADE = new HashMap<>();
    private static int nextTexture;
    private static String lastDrawn = "";

    private LoosePapers() {}

    /** What the last frame showed, for the test script. */
    static String lastDrawn() {
        return lastDrawn;
    }

    static void draw(GuiGraphics ctx, Journal journal, int stage, int bookX, int bookY) {
        lastDrawn = "";
        if (stage < 2) return;
        int seed = journal.id.hashCode();
        int notes = 0;
        Entry place = null;
        ItemStack photo = null;
        for (Entry entry : journal.entries) {
            if (Entry.NOTE.equals(entry.type)) notes++;
            if (place == null && entry.isLocation() && entry.map != null) place = entry;
            for (Keepsake keepsake : entry.keepsakes) {
                if (photo != null) break;
                ItemStack stack = KeepsakeArt.stack(keepsake);
                if (ExposureCompat.isPhoto(stack)) photo = stack;
            }
        }
        if (notes >= 1) put(ctx, "letter", letter(0, 2), bookX + 30 + pick(seed, 1, 28), bookY - 5 - pick(seed, 2, 2));
        if (stage >= 3 && place != null) put(ctx, "map", mapSlip(journal.id, place.map), bookX + 200 + pick(seed, 3, 22), bookY - 7);
        if (stage >= 4 && notes >= 2) put(ctx, "letter", letter(1, 87), bookX - 7, bookY + 26 + pick(seed, 4, 14));
        if (stage >= 4 && photo != null) {
            drawPhoto(ctx, photo, bookX - 7, bookY + 110 + pick(seed, 5, 14));
            lastDrawn += "photo ";
        }
        lastDrawn = lastDrawn.strip();
    }

    private static int pick(int seed, int salt, int range) {
        return EntryLayout.pick(seed, salt + 100, range);
    }

    private static void put(GuiGraphics ctx, String name, Paper paper, int x, int y) {
        if (paper == null) return;
        EJGui.image(ctx, paper.texture(), x, y, paper.w(), paper.h());
        lastDrawn += name + " ";
    }

    private static Paper letter(int which, int degrees) {
        return MADE.computeIfAbsent("letter" + which + "|" + degrees, key -> {
            int[] size = new int[2];
            int[] pixels = read(EJId.of("textures/gui/book/letter" + which + ".png"), size);
            return pixels == null ? null : make(pixels, size[0], size[1], degrees);
        });
    }

    /** One of your own maps on vanilla's map paper. The part with the most water along its top edge goes there, so the edge that shows reads as a map. */
    private static Paper mapSlip(String journalId, String mapId) {
        Paper made = MADE.get("map|" + mapId);
        if (made != null) return made;
        MapImages.get(journalId, mapId);
        byte[] map = MapImages.mapPixels(mapId);
        int[] size = new int[2];
        int[] paper = map == null ? null : read(MAP_PAPER, size);
        if (paper == null || size[0] < 17 || size[1] < 17) return null;
        int[] card = nineSlice(paper, size[0], size[1], MAP_W, MAP_H);
        int iw = MAP_W - MAP_INSET * 2, ih = MAP_H - MAP_INSET * 2;
        int best = -1, bx = 0, by = 0;
        for (int cy = 0; cy + ih <= MapDrawer.H; cy += 2) {
            for (int cx = 0; cx + iw <= MapDrawer.W; cx += 2) {
                int water = 0;
                for (int y = cy; y < cy + 8; y++) {
                    for (int x = cx; x < cx + iw; x++) {
                        if (isWater(map[y * MapDrawer.W + x])) water++;
                    }
                }
                // some land in it as well
                if (water > iw * 8 * 45 / 100) water = 0;
                if (water > best) {
                    best = water;
                    bx = cx;
                    by = cy;
                }
            }
        }
        for (int y = 0; y < ih; y++) {
            for (int x = 0; x < iw; x++) {
                int k = map[(by + y) * MapDrawer.W + bx + x];
                if (k > 0 && k < MapDrawer.PALETTE.length) card[(y + MAP_INSET) * MAP_W + x + MAP_INSET] = MapImages.abgr(MapDrawer.PALETTE[k]);
            }
        }
        made = make(card, MAP_W, MAP_H, -3);
        MADE.put("map|" + mapId, made);
        return made;
    }

    private static boolean isWater(int k) {
        return k >= 4 && k <= 6 || k == 10;
    }

    /** Vanilla's map paper, stretched the way vanilla's own map frames are: corners kept, edges and middle repeated. */
    private static int[] nineSlice(int[] src, int sw, int sh, int w, int h) {
        int c = 8;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int sy = y < c ? y : y >= h - c ? sh - (h - y) : c + (y - c) % (sh - c * 2);
            for (int x = 0; x < w; x++) {
                int sx = x < c ? x : x >= w - c ? sw - (w - x) : c + (x - c) % (sw - c * 2);
                out[y * w + x] = src[sy * sw + sx];
            }
        }
        return out;
    }

    /** Turns the paper and puts its shadow under it, one pixel down and right. */
    private static Paper make(int[] pixels, int w, int h, int degrees) {
        RotSprite.Sprite turned = RotSprite.tilt(pixels, w, h, degrees);
        int tw = turned.w() + 1, th = turned.h() + 1;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, tw, th, true);
        for (int y = 0; y < th; y++) {
            for (int x = 0; x < tw; x++) {
                int here = x < turned.w() && y < turned.h() ? turned.pixels()[y * turned.w() + x] : 0;
                int under = x > 0 && y > 0 ? turned.pixels()[(y - 1) * turned.w() + x - 1] : 0;
                image.setPixelRGBA(x, y, here >>> 24 > 0 ? here : under >>> 24 > 0 ? SHADOW : 0);
            }
        }
        ResourceLocation id = EJId.of("loose/" + nextTexture++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return new Paper(id, tw, th);
    }

    /** A texture's pixels as ABGR, from the resource packs, so a pack can change it. */
    private static int[] read(ResourceLocation texture, int[] size) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            size[0] = image.getWidth();
            size[1] = image.getHeight();
            int[] pixels = new int[size[0] * size[1]];
            for (int y = 0; y < size[1]; y++) {
                for (int x = 0; x < size[0]; x++) pixels[y * size[0] + x] = image.getPixelRGBA(x, y);
            }
            return pixels;
        } catch (IOException e) {
            return null;
        }
    }

    /** A real photo from the book, drawn by Exposure. Exposure draws in depth, so the depth is cleared after it and the pages still go on top. */
    private static void drawPhoto(GuiGraphics ctx, ItemStack photo, int x, int y) {
        PoseStack pose = ctx.pose();
        int half = PHOTO / 2;
        ctx.flush();
        pose.pushPose();
        pose.translate(x + half + 2, y + half + 2, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(-6));
        pose.translate(-half, -half, 0);
        pose.scale(PHOTO, PHOTO, PHOTO);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        ExposureCompat.draw(photo, pose, buffers);
        buffers.endBatch();
        pose.popPose();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        MADE.values().forEach(paper -> {
            if (paper != null) textures.release(paper.texture());
        });
        MADE.clear();
    }
}
