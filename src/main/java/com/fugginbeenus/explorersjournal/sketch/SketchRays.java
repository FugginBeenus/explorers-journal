package com.fugginbeenus.explorersjournal.sketch;

import java.util.Arrays;

/** What each ray saw, at twice the page size in each direction. Plain arrays: filled on the game thread, read on a background thread. */
public final class SketchRays {
    public static final int W = SketchDrawer.W * SketchDrawer.SS, H = SketchDrawer.H * SketchDrawer.SS;
    public static final float MISS = Float.POSITIVE_INFINITY;

    public final float[] t = new float[W * H];
    public final byte[] family = new byte[W * H];
    /** Which way the face looks: faces that look the same way share a number. -1 for a miss. */
    public final byte[] normal = new byte[W * H];
    /** Which kind of ground: path, sand, field, or bare stone. */
    public final byte[] ground = new byte[W * H];
    public final int[] blockHash = new int[W * H];
    public final int[] blockY = new int[W * H];
    /** Where the ray hit on the block's face, from 0 to 1. */
    public final float[] u = new float[W * H], v = new float[W * H];
    /** The upper half of a door. */
    public final boolean[] upper = new boolean[W * H];
    /** The distance to the first plant the ray passed through. */
    public final float[] plantT = new float[W * H];
    public final boolean[] plantFlower = new boolean[W * H];
    /** Which cube of a mob or other thing it hit, or 0. */
    public final short[] part = new short[W * H];
    /** A dark spot on a mob's face. */
    public final boolean[] dot = new boolean[W * H];
    /** A cloud in the sky behind this ray. */
    public final boolean[] cloud = new boolean[W * H];
    /** Where the sun and moon are on the page, or NaN. */
    public float sunX = Float.NaN, sunY = Float.NaN, moonX = Float.NaN, moonY = Float.NaN;
    public int moonPhase;
    public boolean night, rain, snow;

    public SketchRays() {
        Arrays.fill(t, MISS);
        Arrays.fill(plantT, MISS);
        Arrays.fill(normal, (byte) -1);
    }
}
