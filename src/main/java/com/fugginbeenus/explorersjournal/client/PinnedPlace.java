package com.fugginbeenus.explorersjournal.client;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/** The place pinned in a journal you carry. Location Tooltip shows it on a pill: its name, how far, and vanilla's compass pointing the way. */
public final class PinnedPlace {
    private static final double HERE = 8;
    private static String name = "", dim = "";
    private static int x, z;

    private PinnedPlace() {}

    public static void set(String name, String dim, int x, int z) {
        PinnedPlace.name = name;
        PinnedPlace.dim = dim;
        PinnedPlace.x = x;
        PinnedPlace.z = z;
    }

    public static boolean isPinned() {
        return !name.isEmpty();
    }

    /** The pill's words, or null for no pill: away in another dimension, or nothing pinned. */
    public static String label() {
        double[] way = way();
        if (way == null) return null;
        if (way[0] < HERE) return name + ", here";
        return name + " " + (way[0] < 1000 ? Math.round(way[0]) + "m" : String.format(Locale.ROOT, "%.1fkm", way[0] / 1000));
    }

    /** Vanilla's own compass, its needle turned to the place. Null when you are there. */
    public static String icon() {
        double[] way = way();
        if (way == null || way[0] < HERE) return null;
        // compass_16 points straight up, and each frame after it turns a 32nd of the way around to the right
        int frame = Math.floorMod(16 + (int) Math.round(way[1] / 11.25), 32);
        return String.format(Locale.ROOT, "minecraft:textures/item/compass_%02d.png", frame);
    }

    /** How far the place is, and how far to turn right to face it, in degrees. */
    private static double[] way() {
        Minecraft mc = Minecraft.getInstance();
        if (name.isEmpty() || mc.player == null || mc.level == null) return null;
        if (!dim.equals(mc.level.dimension().location().toString())) return null;
        double dx = x + 0.5 - mc.player.getX(), dz = z + 0.5 - mc.player.getZ();
        return new double[]{Math.hypot(dx, dz), Math.toDegrees(Math.atan2(-dx, dz)) - mc.player.getYRot()};
    }
}
