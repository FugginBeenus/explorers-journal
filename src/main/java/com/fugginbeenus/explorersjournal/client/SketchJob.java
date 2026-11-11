package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.mixin.LevelRendererAccessor;
import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import com.fugginbeenus.explorersjournal.sketch.SketchRays;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** One sketch. The rays are cast a few at a time on the game thread, each frame, so nothing lags. Then the lines are drawn on a background thread. */
public final class SketchJob {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static final double MAX_DISTANCE = 140, SMALL_RANGE = 32, FARTHEST_CLOUD = 340;

    private final Level level;
    private final double ex, ey, ez;
    private final double fx, fy, fz, rx, rz, ux, uy, uz, halfW, halfH;
    private final long seed;
    private final int minY, maxY;
    private final SketchRays rays = new SketchRays();
    private final SketchThings things;
    private final Map<BlockState, SketchBlocks.Info> infos = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private int next;
    private int frames;
    private boolean skyRead;
    private boolean[] clouds;
    private double cloudY, cloudDrift;
    private long castNanos, longestNanos, startedDrawing;
    private CompletableFuture<byte[]> drawing;
    private byte[] image;

    public SketchJob(Level level, double eyeX, double eyeY, double eyeZ, float yaw, float pitch, double verticalFov, long seed) {
        this.level = level;
        this.ex = eyeX;
        this.ey = eyeY;
        this.ez = eyeZ;
        this.seed = seed;
        this.minY = level.getMinBuildHeight();
        this.maxY = level.getMaxBuildHeight();
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        fx = -Math.sin(y) * Math.cos(p);
        fy = -Math.sin(p);
        fz = Math.cos(y) * Math.cos(p);
        rx = -Math.cos(y);
        rz = -Math.sin(y);
        // right cross forward
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        halfH = Math.tan(Math.toRadians(verticalFov) / 2);
        halfW = halfH * SketchRays.W / SketchRays.H;
        things = new SketchThings(level, eyeX, eyeY, eyeZ, fx, fy, fz);
    }

    /** The sun, moon, and weather as they are right now, in a dimension that has them. */
    private void readSky() {
        Minecraft mc = Minecraft.getInstance();
        if (!(level instanceof ClientLevel client) || client.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return;
        BlockPos at = BlockPos.containing(ex, ey, ez);
        float angle = (float) (level.getTimeOfDay(1f) * Math.PI * 2);
        double sunX = -Math.sin(angle), sunY = Math.cos(angle);
        rays.night = sunY < -0.1;
        boolean wet = level.getRainLevel(1f) > 0.5f;
        if (wet) {
            Biome.Precipitation fall = level.getBiome(at).value().getPrecipitationAt(at);
            rays.rain = fall == Biome.Precipitation.RAIN;
            rays.snow = fall == Biome.Precipitation.SNOW;
        } else {
            float[] sun = toPage(sunX, sunY, 0), moon = toPage(-sunX, -sunY, 0);
            if (sun != null) {
                rays.sunX = sun[0];
                rays.sunY = sun[1];
            }
            if (moon != null) {
                rays.moonX = moon[0];
                rays.moonY = moon[1];
            }
            rays.moonPhase = level.getMoonPhase();
        }
        if (mc.options.getCloudsType() == CloudStatus.OFF) return;
        cloudY = client.effects().getCloudHeight();
        cloudDrift = ((LevelRendererAccessor) mc.levelRenderer).explorersjournal$ticks() * 0.03f;
        clouds = loadClouds();
    }

    /** Null if that way is behind you. */
    private float[] toPage(double x, double y, double z) {
        double ahead = x * fx + y * fy + z * fz;
        if (ahead <= 0) return null;
        double across = (x * rx + z * rz) / ahead / halfW, up = (x * ux + y * uy + z * uz) / ahead / halfH;
        return new float[]{(float) ((across + 1) / 2 * SketchDrawer.W), (float) ((1 - up) / 2 * SketchDrawer.H)};
    }

    private static boolean[] loadClouds() {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.tryParse("minecraft:textures/environment/clouds.png"));
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() != 256 || image.getHeight() != 256) return null;
            boolean[] cells = new boolean[256 * 256];
            for (int z = 0; z < 256; z++) {
                for (int x = 0; x < 256; x++) cells[z * 256 + x] = image.getPixelRGBA(x, z) >>> 24 > 0;
            }
            return cells;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean cloudAt(double dx, double dy, double dz) {
        if (dy <= 0) return false;
        double t = (cloudY - ey) / dy;
        if (t <= 0 || t > FARTHEST_CLOUD) return false;
        int cellX = (int) Math.floor((ex + dx * t + cloudDrift) / 12) & 255, cellZ = (int) Math.floor((ez + dz * t) / 12 + 0.33) & 255;
        return clouds[cellZ * 256 + cellX];
    }

    public boolean done() {
        return image != null;
    }

    public byte[] image() {
        return image;
    }

    public SketchRays rays() {
        return rays;
    }

    /** The slowest moment on the game thread, in milliseconds. */
    public float longestMillis() {
        return longestNanos / 1_000_000f;
    }

    public float progress() {
        return (float) next / (SketchRays.W * SketchRays.H);
    }

    /** Casts rays until the budget for this frame is used up. */
    public void work(long budgetNanos) {
        if (image != null) return;
        if (drawing != null) {
            if (drawing.isDone()) finish();
            return;
        }
        int total = SketchRays.W * SketchRays.H;
        long start = Util.getNanos(), used;
        if (!skyRead) {
            skyRead = true;
            readSky();
        }
        if (!things.readUntil(start + budgetNanos)) {
            noteFrame(Util.getNanos() - start);
            return;
        }
        do {
            // the clock is slow next to a ray, so look at it now and then
            for (int i = 0; i < 64 && next < total; i++) cast(next++);
            used = Util.getNanos() - start;
        } while (next < total && used < budgetNanos);
        noteFrame(used);
        if (next >= total) {
            startedDrawing = Util.getMillis();
            drawing = CompletableFuture.supplyAsync(() -> SketchDrawer.draw(rays, seed), Util.backgroundExecutor());
        }
    }

    private void noteFrame(long used) {
        frames++;
        castNanos += used;
        longestNanos = Math.max(longestNanos, used);
    }

    private void finish() {
        image = drawing.join();
        LOG.info("Sketch: {} rays and {} things, {} ms in all over {} frames, longest frame {} ms, drew {} ms off the game thread, {} bytes",
                SketchRays.W * SketchRays.H, things.count(), castNanos / 1_000_000, frames, longestNanos / 1_000_000, Util.getMillis() - startedDrawing, SketchDrawer.pack(image).length);
    }

    /** Steps through the blocks along one ray, the same way the game's own clip does, with no allocation for each ray. */
    private void cast(int i) {
        int row = i / SketchRays.W, col = i % SketchRays.W;
        double gx = (col + .5) / SketchRays.W * 2 - 1, gy = 1 - (row + .5) / SketchRays.H * 2;
        double dx = fx + gx * halfW * rx + gy * halfH * ux;
        double dy = fy + gy * halfH * uy;
        double dz = fz + gx * halfW * rz + gy * halfH * uz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len;
        dy /= len;
        dz /= len;
        castBlocks(i, dx, dy, dz);
        if (things.any()) things.cast(rays, i, (float) dx, (float) dy, (float) dz);
        if (clouds != null && rays.t[i] == SketchRays.MISS) rays.cloud[i] = cloudAt(dx, dy, dz);
    }

    private void castBlocks(int i, double dx, double dy, double dz) {
        int ix = (int) Math.floor(ex), iy = (int) Math.floor(ey), iz = (int) Math.floor(ez);
        int sx = dx >= 0 ? 1 : -1, sy = dy >= 0 ? 1 : -1, sz = dz >= 0 ? 1 : -1;
        double tdx = dx != 0 ? Math.abs(1 / dx) : 1e30, tdy = dy != 0 ? Math.abs(1 / dy) : 1e30, tdz = dz != 0 ? Math.abs(1 / dz) : 1e30;
        double tmx = dx != 0 ? (ix + (sx > 0 ? 1 : 0) - ex) / dx : 1e30;
        double tmy = dy != 0 ? (iy + (sy > 0 ? 1 : 0) - ey) / dy : 1e30;
        double tmz = dz != 0 ? (iz + (sz > 0 ? 1 : 0) - ez) / dz : 1e30;
        double t = 0;
        while (t < MAX_DISTANCE) {
            if (iy >= minY && iy < maxY) {
                pos.set(ix, iy, iz);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && hitsBlock(state, i, ix, iy, iz, dx, dy, dz, t)) return;
            } else if (iy >= maxY ? dy >= 0 : dy <= 0) {
                return;
            }
            if (tmx <= tmy && tmx <= tmz) {
                t = tmx;
                ix += sx;
                tmx += tdx;
            } else if (tmy <= tmz) {
                t = tmy;
                iy += sy;
                tmy += tdy;
            } else {
                t = tmz;
                iz += sz;
                tmz += tdz;
            }
        }
    }

    private boolean hitsBlock(BlockState state, int i, int ix, int iy, int iz, double dx, double dy, double dz, double tNow) {
        SketchBlocks.Info info = infos.get(state);
        if (info == null) {
            info = SketchBlocks.read(state, level, pos);
            infos.put(state, info);
        }
        if (info.plant() && rays.plantT[i] == SketchRays.MISS) {
            rays.plantT[i] = (float) tNow;
            rays.plantFlower[i] = info.flower();
        }
        if (info.small() && tNow > SMALL_RANGE) return false;
        double ox = ex - ix, oy = ey - iy, oz = ez - iz;
        double best = Double.POSITIVE_INFINITY;
        int normal = -1;
        for (float[] b : info.boxes()) {
            long found = rayBox(ox, oy, oz, dx, dy, dz, b[0], b[1], b[2], b[3], b[4], b[5], tNow, best);
            if (found >= 0) {
                best = lastHit;
                normal = (int) found;
            }
        }
        byte family = info.family();
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            long found = rayBox(ox, oy, oz, dx, dy, dz, 0, 0, 0, 1, fluid.getHeight(level, pos), 1, tNow, best);
            if (found >= 0) {
                best = lastHit;
                normal = (int) found;
                family = SketchDrawer.F_WATER;
            }
        }
        if (normal < 0) return false;

        rays.t[i] = (float) best;
        rays.family[i] = family;
        rays.normal[i] = (byte) normal;
        rays.ground[i] = info.ground();
        rays.upper[i] = info.upper();
        rays.blockY[i] = iy;
        rays.blockHash[i] = hash(ix, iy, iz);
        double px = ex + dx * (best + 1e-3), py = ey + dy * (best + 1e-3), pz = ez + dz * (best + 1e-3);
        double fx0 = px - Math.floor(px), fy0 = py - Math.floor(py), fz0 = pz - Math.floor(pz);
        int axis = normal / 2;
        rays.u[i] = (float) (axis == 0 ? fz0 : fx0);
        rays.v[i] = (float) (axis == 1 ? fz0 : fy0);
        return true;
    }

    private double lastHit;

    /** The ray against one box in the block. Returns the face it entered, or -1. The distance goes in lastHit. */
    private long rayBox(double ox, double oy, double oz, double dx, double dy, double dz, float x0, float y0, float z0, float x1, float y1, float z1, double tNow, double best) {
        double near = Double.NEGATIVE_INFINITY, far = Double.POSITIVE_INFINITY;
        int axis = 0;
        if (dx != 0) {
            double a = (x0 - ox) / dx, b = (x1 - ox) / dx;
            near = Math.min(a, b);
            far = Math.max(a, b);
        } else if (ox < x0 || ox > x1) {
            return -1;
        }
        if (dy != 0) {
            double a = (y0 - oy) / dy, b = (y1 - oy) / dy;
            double n = Math.min(a, b);
            if (n > near) {
                near = n;
                axis = 1;
            }
            far = Math.min(far, Math.max(a, b));
        } else if (oy < y0 || oy > y1) {
            return -1;
        }
        if (dz != 0) {
            double a = (z0 - oz) / dz, b = (z1 - oz) / dz;
            double n = Math.min(a, b);
            if (n > near) {
                near = n;
                axis = 2;
            }
            far = Math.min(far, Math.max(a, b));
        } else if (oz < z0 || oz > z1) {
            return -1;
        }
        if (near > far || far < 0 || near < tNow - 1e-6 || near >= best) return -1;
        lastHit = near;
        boolean along = axis == 0 ? dx > 0 : axis == 1 ? dy > 0 : dz > 0;
        return axis * 2L + (along ? 1 : 0);
    }

    private static int hash(int x, int y, int z) {
        long h = ((long) x * 73856093) ^ ((long) y * 19349663) ^ ((long) z * 83492791);
        h = (h ^ (h >> 13)) * 1274126177;
        return (int) ((h ^ (h >> 16)) & 0x7fffffff);
    }
}
