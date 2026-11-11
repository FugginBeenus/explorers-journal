package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Keepsake;
import com.fugginbeenus.explorersjournal.journal.Keepsakes;
import com.fugginbeenus.explorersjournal.util.EJId;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/** How a thing looks on the page. Flat items are shown at 2x, turned on the grid, and held with tape. Blocks and shiny items stay straight. Photos are drawn by Exposure. */
final class KeepsakeArt {
    private static final int ICON = 16, ITEM_SIZE = 32, PHOTO_SIZE = 92, PHOTO_PAD = 4, TAPE_HEIGHT = 8;
    private static final int[] TILTS = {-18, -14, -10, 10, 12, 14, 18};
    private static final int[] PHOTO_TILTS = {-5, -4, -3, 3, 4, 5};
    // nearly the paper, one pixel down and right, so it lifts off the page (alpha, blue, green, red)
    private static final int SHADE = 0xFF8DADC2;

    // rowLeft and rowRight hold where the real pixels are on each row of the texture
    private record Art(ResourceLocation texture, int x, int y, int w, int h, int holdX, int holdY, int[] rowLeft, int[] rowRight) {}

    private static final Map<String, ItemStack> STACKS = new HashMap<>();
    private static final Map<String, Art> ARTS = new HashMap<>();
    private static final Map<String, int[]> BOUNDS = new HashMap<>();
    private static int nextTexture;
    private static boolean newArt;

    private KeepsakeArt() {}

    /** What this thing covers between two heights on its page, from the page's left edge, or null if nothing. It follows the real shape and the tape once the art exists, and the plain box before that. */
    static int[] covered(Keepsake keepsake, int top, int bottom) {
        int seed = keepsake.id.hashCode();
        Art art = ARTS.get(keepsake.item + "|" + tilt(seed));
        if (art == null) {
            ItemStack stack = stack(keepsake);
            int size = size(stack), pad = ExposureCompat.isPhoto(stack) ? PHOTO_PAD : 0;
            if (bottom <= keepsake.y - pad || top >= keepsake.y + size + pad) return null;
            return new int[]{keepsake.x - pad, keepsake.x + size + pad};
        }
        int left = Integer.MAX_VALUE, right = Integer.MIN_VALUE;
        int ax = keepsake.x + art.x(), ay = keepsake.y + art.y();
        for (int row = Math.max(0, top - ay); row < Math.min(art.h(), bottom - ay); row++) {
            if (art.rowLeft()[row] <= art.rowRight()[row]) {
                left = Math.min(left, ax + art.rowLeft()[row]);
                right = Math.max(right, ax + art.rowRight()[row] + 1);
            }
        }
        int length = tapeLength(seed);
        int tapeX = ax + art.holdX() - length / 2, tapeY = ay + art.holdY() - 2;
        if (bottom > tapeY - 1 && top < tapeY + TAPE_HEIGHT) {
            left = Math.min(left, tapeX);
            right = Math.max(right, tapeX + length);
        }
        return left <= right ? new int[]{left, right} : null;
    }

    /** The box this thing and its tape cover on its page: x0, y0, x1, y1, from the page's top left. Kept once its art exists, since only its spot changes after that. */
    static int[] bounds(Keepsake keepsake) {
        String key = keepsake.item + "|" + tilt(keepsake.id.hashCode()) + "|" + tapeLength(keepsake.id.hashCode());
        boolean hasArt = ARTS.containsKey(keepsake.item + "|" + tilt(keepsake.id.hashCode()));
        int[] rel = hasArt ? BOUNDS.get(key) : null;
        if (rel == null) {
            int size = size(keepsake);
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            for (int y = keepsake.y - 12; y < keepsake.y + size + 12; y++) {
                int[] span = covered(keepsake, y, y + 1);
                if (span == null) continue;
                x0 = Math.min(x0, span[0]);
                x1 = Math.max(x1, span[1]);
                y0 = Math.min(y0, y);
                y1 = y + 1;
            }
            rel = x0 <= x1 ? new int[]{x0 - keepsake.x, y0 - keepsake.y, x1 - keepsake.x, y1 - keepsake.y} : new int[]{0, 0, size, size};
            if (hasArt) BOUNDS.put(key, rel);
        }
        return new int[]{keepsake.x + rel[0], keepsake.y + rel[1], keepsake.x + rel[2], keepsake.y + rel[3]};
    }

    /** True once after new art was made, because the writing can then hug its real shape. */
    static boolean takeNewArt() {
        boolean was = newArt;
        newArt = false;
        return was;
    }

    private static int tilt(int seed) {
        return TILTS[Math.floorMod(seed, TILTS.length)];
    }

    private static int tapeLength(int seed) {
        return 16 + Math.floorMod(seed >> 3, 3);
    }

    static ItemStack stack(Keepsake keepsake) {
        return STACKS.computeIfAbsent(keepsake.item, data -> Keepsakes.read(data, Minecraft.getInstance().level.registryAccess()));
    }

    static String name(Keepsake keepsake) {
        ItemStack stack = stack(keepsake);
        return stack.isEmpty() ? "Keepsake" : stack.getHoverName().getString();
    }

    static int size(ItemStack stack) {
        return ExposureCompat.isPhoto(stack) ? PHOTO_SIZE : ITEM_SIZE;
    }

    static int size(Keepsake keepsake) {
        return size(stack(keepsake));
    }

    /** The seed keeps its tilt and tape the same every time. alpha below 1 is for a thing still on the mouse. */
    static void draw(GuiGraphics ctx, String data, ItemStack stack, int seed, int x, int y, float alpha) {
        if (stack.isEmpty()) {
            drawScrap(ctx, seed, x, y);
        } else if (ExposureCompat.isPhoto(stack)) {
            drawPhoto(ctx, stack, seed, x, y);
        } else if (isFlat(stack)) {
            drawFlat(ctx, data, stack, seed, x, y, alpha);
        } else {
            drawSolid(ctx, stack, seed, x, y, alpha);
        }
    }

    private static boolean isFlat(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        return !stack.hasFoil() && !mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0).isGui3d();
    }

    private static void tape(GuiGraphics ctx, int seed, int x, int y, int length, int height, int rise, int crease) {
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 200);
        EJGui.tape(ctx, x, y, length, rise, height, seed >> 7, seed >> 9, crease);
        ctx.pose().popPose();
    }

    private static void itemTape(GuiGraphics ctx, int seed, int holdX, int holdY) {
        int length = tapeLength(seed);
        int rise = Math.floorMod(seed >> 5, 3) - 1;
        int crease = Math.floorMod(seed >> 11, 3) == 0 ? 6 + Math.floorMod(seed >> 13, 3) : -1;
        tape(ctx, seed, holdX - length / 2, holdY - 2, length, 5, rise, crease);
    }

    private static void drawFlat(GuiGraphics ctx, String data, ItemStack stack, int seed, int x, int y, float alpha) {
        int tilt = tilt(seed);
        String key = data + "|" + tilt;
        Art art = ARTS.get(key);
        if (art == null) {
            art = build(ctx, stack, tilt);
            ARTS.put(key, art);
            newArt = true;
        }
        ctx.setColor(1f, 1f, 1f, alpha);
        EJGui.image(ctx, art.texture(), x + art.x(), y + art.y(), art.w(), art.h());
        ctx.setColor(1f, 1f, 1f, 1f);
        itemTape(ctx, seed, x + art.x() + art.holdX(), y + art.y() + art.holdY());
    }

    private static void drawSolid(GuiGraphics ctx, ItemStack stack, int seed, int x, int y, float alpha) {
        PoseStack pose = ctx.pose();
        ctx.setColor(1f, 1f, 1f, alpha);
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(2, 2, 1);
        ctx.renderItem(stack, 0, 0);
        pose.popPose();
        ctx.setColor(1f, 1f, 1f, 1f);
        itemTape(ctx, seed, x + ITEM_SIZE / 2, y + ITEM_SIZE * 2 / 3);
    }

    private static void drawPhoto(GuiGraphics ctx, ItemStack stack, int seed, int x, int y) {
        PoseStack pose = ctx.pose();
        int half = PHOTO_SIZE / 2;
        pose.pushPose();
        pose.translate(x + half, y + half, 1);
        pose.mulPose(Axis.ZP.rotationDegrees(PHOTO_TILTS[Math.floorMod(seed, PHOTO_TILTS.length)]));
        pose.translate(-half, -half, 0);
        pose.scale(PHOTO_SIZE, PHOTO_SIZE, PHOTO_SIZE);
        ctx.flush();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        ExposureCompat.draw(stack, pose, buffers);
        buffers.endBatch();
        pose.popPose();
        tape(ctx, seed, x + half - 14, y - 2, 28, 6, -2 + Math.floorMod(seed >> 5, 5), 11);
    }

    /** The item's mod is gone. A torn bit of paper holds its place until the mod comes back. */
    private static void drawScrap(GuiGraphics ctx, int seed, int x, int y) {
        ctx.fill(x + 5, y + 8, x + 28, y + 28, 0xFFE6D9B2);
        ctx.fill(x + 7, y + 28, x + 26, y + 29, 0xFFC8B88C);
        ctx.fill(x + 3, y + 11, x + 5, y + 25, 0xFFE6D9B2);
        EJGui.write(ctx, Minecraft.getInstance().font, "?", x + 14, y + 14, EJGui.INK_SOFT);
        itemTape(ctx, seed, x + ITEM_SIZE / 2, y + 8);
    }

    private static Art build(GuiGraphics ctx, ItemStack stack, int tilt) {
        ctx.flush();
        int[] icon = capture(stack);
        int[] doubled = new int[ITEM_SIZE * ITEM_SIZE];
        for (int y = 0; y < ITEM_SIZE; y++) {
            for (int x = 0; x < ITEM_SIZE; x++) doubled[y * ITEM_SIZE + x] = icon[y / 2 * ICON + x / 2];
        }
        RotSprite.Sprite turned = RotSprite.turn(doubled, ITEM_SIZE, ITEM_SIZE, tilt);
        int w = turned.w() + 1, h = turned.h() + 1;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, true);
        int[] rowLeft = new int[h], rowRight = new int[h];
        for (int y = 0; y < h; y++) {
            rowLeft[y] = w;
            rowRight[y] = -1;
            for (int x = 0; x < w; x++) {
                int here = x < turned.w() && y < turned.h() ? turned.pixels()[y * turned.w() + x] : 0;
                int under = x > 0 && y > 0 ? turned.pixels()[(y - 1) * turned.w() + x - 1] : 0;
                int colour = here >>> 24 > 0 ? here : under >>> 24 > 0 ? SHADE : 0;
                image.setPixelRGBA(x, y, colour);
                if (colour != 0) {
                    rowLeft[y] = Math.min(rowLeft[y], x);
                    rowRight[y] = x;
                }
            }
        }
        ResourceLocation id = EJId.of("keepsake/" + nextTexture++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        int[] hold = holdPoint(turned);
        return new Art(id, (ITEM_SIZE - turned.w()) / 2, (ITEM_SIZE - turned.h()) / 2, w, h, hold[0], hold[1], rowLeft, rowRight);
    }

    /** Draws the item's own icon on a small hidden sheet and reads the pixels back, so any item works, with its tints. */
    private static int[] capture(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        TextureTarget target = new TextureTarget(ICON, ICON, true, Minecraft.ON_OSX);
        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, ICON, ICON, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
        GuiGraphics sheet = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        sheet.renderItem(stack, 0, 0);
        sheet.flush();
        RenderSystem.restoreProjectionMatrix();
        mc.getMainRenderTarget().bindWrite(true);

        int[] pixels = new int[ICON * ICON];
        try (NativeImage image = new NativeImage(ICON, ICON, false)) {
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            for (int y = 0; y < ICON; y++) {
                for (int x = 0; x < ICON; x++) pixels[y * ICON + x] = straightAlpha(image.getPixelRGBA(x, y));
            }
        }
        target.destroyBuffers();
        return pixels;
    }

    /** Blending onto a clear sheet darkens see-through pixels. This undoes it. */
    private static int straightAlpha(int v) {
        int a = v >>> 24;
        if (a == 0) return 0;
        if (a == 255) return v;
        int r = Math.min(255, (v & 255) * 255 / a), g = Math.min(255, (v >> 8 & 255) * 255 / a), b = Math.min(255, (v >> 16 & 255) * 255 / a);
        return a << 24 | b << 16 | g << 8 | r;
    }

    /** Where the tape goes: low on a stem, if the thing has one, or else a little below the middle of its long side. */
    private static int[] holdPoint(RotSprite.Sprite sprite) {
        int w = sprite.w(), h = sprite.h();
        int green = 0;
        for (int v : sprite.pixels()) {
            if (isGreen(v)) green++;
        }
        boolean stem = green > 8;
        double n = 0, cx = 0, cy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = sprite.pixels()[y * w + x];
                if (stem ? isGreen(v) : v >>> 24 > 0) {
                    n++;
                    cx += x;
                    cy += y;
                }
            }
        }
        if (n == 0) return new int[]{w / 2, h / 2};
        cx /= n;
        cy /= n;
        double xx = 0, yy = 0, xy = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = sprite.pixels()[y * w + x];
                if (stem ? isGreen(v) : v >>> 24 > 0) {
                    xx += (x - cx) * (x - cx);
                    yy += (y - cy) * (y - cy);
                    xy += (x - cx) * (y - cy);
                }
            }
        }
        double angle = 0.5 * Math.atan2(2 * xy, xx - yy);
        double ax = Math.cos(angle), ay = Math.sin(angle);
        if (ay < 0) {
            ax = -ax;
            ay = -ay;
        }
        double spread = Math.sqrt(Math.max(0, (xx + yy) / 2 + Math.hypot((xx - yy) / 2, xy)) / n);
        double along = stem ? 5 : spread * 0.3;
        return new int[]{(int) (cx + ax * along), (int) (cy + ay * along)};
    }

    private static boolean isGreen(int v) {
        return v >>> 24 > 0 && (v >> 8 & 255) > (v & 255) + 12;
    }

    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        ARTS.values().forEach(art -> textures.release(art.texture()));
        ARTS.clear();
        STACKS.clear();
        BOUNDS.clear();
    }
}
