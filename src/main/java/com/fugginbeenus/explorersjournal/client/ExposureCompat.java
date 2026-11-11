package com.fugginbeenus.explorersjournal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** The only class that talks to Exposure. It asks by name, so the journal needs no Exposure to build or run. This is not a promised API, so any failure just turns photos off. */
final class ExposureCompat {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static boolean tried, ready;
    private static Class<?> photoItem;
    private static Method renderer, render;

    private ExposureCompat() {}

    private static boolean ready() {
        if (tried) return ready;
        tried = true;
        if (!FabricLoader.getInstance().isModLoaded("exposure")) return false;
        try {
            photoItem = Class.forName("io.github.mortuusars.exposure.world.item.PhotographItem");
            renderer = Class.forName("io.github.mortuusars.exposure.ExposureClient").getMethod("photographRenderer");
            render = renderer.getReturnType().getMethod("render", ItemStack.class, boolean.class, boolean.class, PoseStack.class, MultiBufferSource.class, int.class);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("This Exposure version has no photo call the journal knows. Photos will show as items.", e);
        }
        return ready;
    }

    static boolean isPhoto(ItemStack stack) {
        return ready() && photoItem.isInstance(stack.getItem());
    }

    /** Draws the photo with its paper in a square from 0 to 1. Scale the pose first. */
    static void draw(ItemStack stack, PoseStack pose, MultiBufferSource buffers) {
        try {
            render.invoke(renderer.invoke(null), stack, true, false, pose, buffers, LightTexture.FULL_BRIGHT);
        } catch (ReflectiveOperationException | LinkageError e) {
            ready = false;
            LOG.warn("Exposure could not draw a photo. Photos will show as items.", e);
        }
    }
}
