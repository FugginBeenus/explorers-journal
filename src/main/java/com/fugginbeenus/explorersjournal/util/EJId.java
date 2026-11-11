package com.fugginbeenus.explorersjournal.util;

import net.minecraft.resources.ResourceLocation;

public final class EJId {
    public static final String MOD = "explorersjournal";

    private EJId() {}

    public static ResourceLocation of(String path) {
        return of(MOD, path);
    }

    public static ResourceLocation of(String namespace, String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }
}
