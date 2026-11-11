package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.map.MapDrawer;
import com.fugginbeenus.explorersjournal.net.EJPayloads;
import com.fugginbeenus.explorersjournal.net.client.EJNetClient;
import com.fugginbeenus.explorersjournal.sketch.PixelCanvas;
import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import com.fugginbeenus.explorersjournal.util.EJId;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Maps and sketches arrive from the server when a page first needs them, then stay as textures until you leave the world. */
public final class MapImages {
    private static final Map<String, ResourceLocation> READY = new HashMap<>();
    private static final Set<String> ASKED = new HashSet<>();
    private static final Set<String> SKETCHES = new HashSet<>(), DRAWINGS = new HashSet<>();
    private static final Map<String, byte[]> DRAWING_PIXELS = new HashMap<>(), MAP_PIXELS = new HashMap<>();
    private static final ResourceLocation LIVE_ID = EJId.of("drawing/live");
    private static DynamicTexture live;
    private static PixelCanvas liveCanvas;
    private static int liveRevision;

    private MapImages() {}

    public static ResourceLocation get(String journalId, String mapId) {
        ResourceLocation id = READY.get(mapId);
        if (id == null && ASKED.add(mapId)) {
            EJNetClient.send(EJPayloads.REQUEST_MAP, new EJPayloads.RequestMap(journalId, mapId));
        }
        return id;
    }

    public static ResourceLocation getSketch(String journalId, String imageId) {
        SKETCHES.add(imageId);
        return get(journalId, imageId);
    }

    /** A sketch you just drew is here already, with no need to ask for it. */
    static void putSketch(String imageId, byte[] lv) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SketchDrawer.W, SketchDrawer.H, true);
        for (int y = 0; y < SketchDrawer.H; y++) {
            for (int x = 0; x < SketchDrawer.W; x++) {
                int k = lv[y * SketchDrawer.W + x];
                image.setPixelRGBA(x, y, k == 0 ? 0 : abgr(SketchDrawer.INK[k]));
            }
        }
        ResourceLocation id = EJId.of("sketch/" + imageId);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        READY.put(imageId, id);
        SKETCHES.add(imageId);
    }

    public static ResourceLocation getDrawing(String journalId, String imageId) {
        DRAWINGS.add(imageId);
        return get(journalId, imageId);
    }

    /** Null until the picture has come from the server. */
    public static byte[] drawingPixels(String imageId) {
        return DRAWING_PIXELS.get(imageId);
    }

    /** A map's palette numbers, or null until it has come from the server. */
    static byte[] mapPixels(String mapId) {
        return MAP_PIXELS.get(mapId);
    }

    static void putDrawing(String imageId, byte[] pixels) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, PixelCanvas.SIZE, PixelCanvas.SIZE, true);
        paint(image, pixels);
        ResourceLocation id = EJId.of("drawing/" + imageId);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        READY.put(imageId, id);
        DRAWINGS.add(imageId);
        DRAWING_PIXELS.put(imageId, pixels.clone());
    }

    /** The drawing you are working on. One texture, written again only when the picture changes. */
    static ResourceLocation live(PixelCanvas canvas) {
        if (live == null) {
            live = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, PixelCanvas.SIZE, PixelCanvas.SIZE, true));
            Minecraft.getInstance().getTextureManager().register(LIVE_ID, live);
            liveCanvas = null;
        }
        if (liveCanvas != canvas || liveRevision != canvas.revision()) {
            paint(live.getPixels(), canvas.pixels());
            live.upload();
            liveCanvas = canvas;
            liveRevision = canvas.revision();
        }
        return LIVE_ID;
    }

    private static void paint(NativeImage image, byte[] pixels) {
        for (int y = 0; y < PixelCanvas.SIZE; y++) {
            for (int x = 0; x < PixelCanvas.SIZE; x++) {
                int k = pixels[y * PixelCanvas.SIZE + x];
                image.setPixelRGBA(x, y, k == 0 ? 0 : abgr(PixelCanvas.INK[k]));
            }
        }
    }

    static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 0xFF;
    }

    public static void receive(String mapId, byte[] packed) {
        if (DRAWINGS.contains(mapId)) {
            byte[] pixels = PixelCanvas.unpack(packed);
            if (pixels != null) putDrawing(mapId, pixels);
            return;
        }
        if (SKETCHES.contains(mapId)) {
            byte[] lv = SketchDrawer.unpack(packed);
            if (lv != null) putSketch(mapId, lv);
            return;
        }
        byte[] img = MapDrawer.unpack(packed);
        MAP_PIXELS.put(mapId, img);
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, MapDrawer.W, MapDrawer.H, true);
        for (int y = 0; y < MapDrawer.H; y++) {
            for (int x = 0; x < MapDrawer.W; x++) {
                int k = img[y * MapDrawer.W + x];
                if (k == 0 || k >= MapDrawer.PALETTE.length) {
                    image.setPixelRGBA(x, y, 0);
                } else {
                    image.setPixelRGBA(x, y, abgr(MapDrawer.PALETTE[k]));
                }
            }
        }
        ResourceLocation id = EJId.of("map/" + mapId);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        READY.put(mapId, id);
    }

    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        READY.values().forEach(textures::release);
        READY.clear();
        ASKED.clear();
        SKETCHES.clear();
        DRAWINGS.clear();
        DRAWING_PIXELS.clear();
        MAP_PIXELS.clear();
        if (live != null) textures.release(LIVE_ID);
        live = null;
        liveCanvas = null;
    }
}
