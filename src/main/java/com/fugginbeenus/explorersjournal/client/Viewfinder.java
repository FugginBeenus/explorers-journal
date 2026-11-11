package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** The book steps aside and a frame shows what the sketch will hold. You can still walk and look around. A click takes the sketch. */
public final class Viewfinder {
    private static final float FRAME_HEIGHT = 0.8f;
    private static final int DIM = 0x70100C08, PAPER = 0xFFF3E8CC, EDGE = 0xFF6E5A42;

    private static JournalScreen returnTo;
    private static int heldSlot;

    private Viewfinder() {}

    public static boolean isOpen() {
        return returnTo != null;
    }

    static void open(JournalScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        returnTo = screen;
        heldSlot = mc.player.getInventory().selected;
        mc.gameRenderer.setRenderHand(false);
        mc.setScreen(null);
    }

    /** Runs before the game reads its keys, so the click does not also hit a block. */
    public static void tick(Minecraft mc) {
        if (returnTo == null) return;
        if (mc.player == null) {
            close(mc);
            return;
        }
        if (mc.screen instanceof PauseScreen) {
            // Esc means never mind, so back to the book
            JournalScreen screen = returnTo;
            close(mc);
            mc.setScreen(screen);
            return;
        }
        if (mc.screen != null) {
            close(mc);
            return;
        }
        // the journal must stay in your hand, or the server will not take the sketch
        mc.player.getInventory().selected = heldSlot;
        for (KeyMapping slot : mc.options.keyHotbarSlots) {
            while (slot.consumeClick()) {
                // no changing hands here
            }
        }
        while (mc.options.keyDrop.consumeClick()) {
            // and no dropping it
        }
        boolean shot = false;
        while (mc.options.keyAttack.consumeClick()) shot = true;
        while (mc.options.keyUse.consumeClick()) shot = true;
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        if (shot) shoot(mc);
    }

    private static void shoot(Minecraft mc) {
        Player player = mc.player;
        Vec3 eye = player.getEyePosition();
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        int frameHeight = frame(mc.getWindow().getGuiScaledWidth(), guiHeight)[1];
        double half = Math.tan(Math.toRadians(mc.options.fov().get()) / 2) * frameHeight / guiHeight;
        SketchJob job = new SketchJob(mc.level, eye.x, eye.y, eye.z, player.getYRot(), player.getXRot(), Math.toDegrees(Math.atan(half)) * 2, player.getRandom().nextLong());
        JournalScreen screen = returnTo;
        close(mc);
        EJGui.pageTurn();
        mc.setScreen(screen);
        screen.sketchTaken(job);
    }

    private static void close(Minecraft mc) {
        returnTo = null;
        mc.gameRenderer.setRenderHand(true);
    }

    /** The frame's width and height, the shape of the page's sketch. */
    private static int[] frame(int width, int height) {
        int h = Math.round(height * FRAME_HEIGHT);
        int w = h * SketchDrawer.W / SketchDrawer.H;
        if (w > width * 0.96f) {
            w = Math.round(width * 0.96f);
            h = w * SketchDrawer.H / SketchDrawer.W;
        }
        return new int[]{w, h};
    }

    public static void draw(GuiGraphics ctx) {
        if (returnTo == null) return;
        Minecraft mc = Minecraft.getInstance();
        int width = ctx.guiWidth(), height = ctx.guiHeight();
        int[] size = frame(width, height);
        int x0 = (width - size[0]) / 2, x1 = x0 + size[0], y0 = (height - size[1]) / 2, y1 = y0 + size[1];
        // the hotbar draws in front of plain drawing, so this goes in front of it
        ctx.pose().pushPose();
        ctx.pose().translate(0, 0, 1000);
        ctx.fill(0, 0, width, y0 - 2, DIM);
        ctx.fill(0, y1 + 2, width, height, DIM);
        ctx.fill(0, y0 - 2, x0 - 2, y1 + 2, DIM);
        ctx.fill(x1 + 2, y0 - 2, width, y1 + 2, DIM);
        ctx.fill(x0 - 2, y0 - 2, x1 + 2, y0, PAPER);
        ctx.fill(x0 - 2, y1, x1 + 2, y1 + 2, PAPER);
        ctx.fill(x0 - 2, y0, x0, y1, PAPER);
        ctx.fill(x1, y0, x1 + 2, y1, PAPER);
        ctx.fill(x0, y0, x1, y0 + 1, EDGE);
        ctx.fill(x0, y1 - 1, x1, y1, EDGE);
        ctx.fill(x0, y0 + 1, x0 + 1, y1 - 1, EDGE);
        ctx.fill(x1 - 1, y0 + 1, x1, y1 - 1, EDGE);
        ctx.drawCenteredString(mc.font, Component.literal("Click to sketch. Esc: never mind."), width / 2, Math.max(2, y0 - 13), 0xFFFFFF);
        ctx.pose().popPose();
    }
}
