package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import com.fugginbeenus.explorersjournal.sketch.SketchRays;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Whatever stands in the world and is not a block, as the rays see it. The game's own renderer draws each thing into a listener, which keeps the faces.
 * So armor, held items, wool, boats, and modded mobs all work the same way. All positions are from the eye.
 */
final class SketchThings {
    private static final double RANGE = 64;
    private static final int MOST_THINGS = 64, MOST_FACES = 8000, MOST_CUBES_TO_JOIN = 400;
    // how near a cube is before its own lines are worth drawing, as a multiple of its size
    private static final float CORNER_RANGE = 13f, LINE_RANGE = 26f;
    private static final Pattern TEXTURE_NAME = Pattern.compile("\\[([a-z0-9_.-]+:[a-z0-9_./-]+)]?\\(blur=");

    private interface Skin {
        boolean solid(float u, float v);
    }

    private record Texture(int w, int h, int[] pixels) implements Skin {
        int pixel(float u, float v) {
            return pixels[Math.max(0, Math.min(h - 1, (int) (v * h))) * w + Math.max(0, Math.min(w - 1, (int) (u * w)))];
        }

        @Override
        public boolean solid(float u, float v) {
            return pixel(u, v) >>> 24 > 25;
        }
    }

    /** An item or block model, painted from the game's big sheet of sprites. */
    private record Sprite(SpriteContents contents, float u0, float v0, float du, float dv) implements Skin {
        Sprite(TextureAtlasSprite sprite) {
            this(sprite.contents(), sprite.getU0(), sprite.getV0(), sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0());
        }

        @Override
        public boolean solid(float u, float v) {
            int w = contents.width(), h = contents.height();
            int x = Math.max(0, Math.min(w - 1, (int) ((u - u0) / du * w))), y = Math.max(0, Math.min(h - 1, (int) ((v - v0) / dv * h)));
            return !contents.isTransparent(0, x, y);
        }
    }

    /** Faces that share corners. A far cube draws as one with the larger cube it touches. */
    private static final class Cube {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        short id;
        float size, cornerRange, lineRange;
        Cube parent;

        void add(Face f) {
            minX = Math.min(minX, f.box[0]);
            minY = Math.min(minY, f.box[1]);
            minZ = Math.min(minZ, f.box[2]);
            maxX = Math.max(maxX, f.box[3]);
            maxY = Math.max(maxY, f.box[4]);
            maxZ = Math.max(maxZ, f.box[5]);
        }

        boolean touches(Cube o) {
            float gap = 0.02f;
            return minX <= o.maxX + gap && o.minX <= maxX + gap && minY <= o.maxY + gap && o.minY <= maxY + gap && minZ <= o.maxZ + gap && o.minZ <= maxZ + gap;
        }
    }

    /** One flat face with square corners. The ray finds where it crosses the face, then asks the skin if that spot is painted. */
    private static final class Face {
        final float ax, ay, az, ex, ey, ez, ee, fx, fy, fz, ff, nx, ny, nz, nd;
        final float u, v, ue, ve, uf, vf;
        final float outX, outY, outZ;
        final Skin skin;
        final byte look;
        final long[] corners = new long[4];
        final float[] box;
        int[] region;
        int darkBelow;
        Cube cube;

        Face(float[] xs, float[] ys, float[] zs, float[] us, float[] vs, float outX, float outY, float outZ, Skin skin) {
            ax = xs[0];
            ay = ys[0];
            az = zs[0];
            float e0 = xs[1] - ax, e1 = ys[1] - ay, e2 = zs[1] - az, f0 = xs[3] - ax, f1 = ys[3] - ay, f2 = zs[3] - az;
            ex = e0;
            ey = e1;
            ez = e2;
            fx = f0;
            fy = f1;
            fz = f2;
            ee = 1f / (e0 * e0 + e1 * e1 + e2 * e2);
            ff = 1f / (f0 * f0 + f1 * f1 + f2 * f2);
            float a = e1 * f2 - e2 * f1, b = e2 * f0 - e0 * f2, c = e0 * f1 - e1 * f0;
            float len = (float) Math.sqrt(a * a + b * b + c * c);
            nx = a / len;
            ny = b / len;
            nz = c / len;
            nd = nx * ax + ny * ay + nz * az;
            u = us[0];
            v = vs[0];
            ue = us[1] - u;
            ve = vs[1] - v;
            uf = us[3] - u;
            vf = vs[3] - v;
            this.outX = outX;
            this.outY = outY;
            this.outZ = outZ;
            this.skin = skin;
            look = (byte) (1 + Math.floorMod(Math.round(nx * 3) * 49 + Math.round(ny * 3) * 7 + Math.round(nz * 3), 120));
            float[] b6 = {xs[0], ys[0], zs[0], xs[0], ys[0], zs[0]};
            for (int i = 0; i < 4; i++) {
                corners[i] = ((long) Float.floatToRawIntBits(xs[i]) * 0x9E3779B1L) ^ ((long) Float.floatToRawIntBits(ys[i]) * 0x85EBCA77L << 1) ^ Float.floatToRawIntBits(zs[i]);
                b6[0] = Math.min(b6[0], xs[i]);
                b6[1] = Math.min(b6[1], ys[i]);
                b6[2] = Math.min(b6[2], zs[i]);
                b6[3] = Math.max(b6[3], xs[i]);
                b6[4] = Math.max(b6[4], ys[i]);
                b6[5] = Math.max(b6[5], zs[i]);
            }
            box = b6;
        }

        boolean flat() {
            return !(ee > 0 && ff > 0 && Float.isFinite(ee) && Float.isFinite(ff) && Float.isFinite(nx));
        }
    }

    /** One thing, with a box around all of it, so most rays skip it quickly. */
    private record Thing(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Face[] faces) {}

    /** Stands in for one of the game's vertex buffers, and keeps the faces. */
    private final class Recorder implements VertexConsumer {
        private final boolean keep;
        private final Skin fileSkin;
        private Skin skin;
        private final float[] xs = new float[4], ys = new float[4], zs = new float[4], us = new float[4], vs = new float[4];
        private float x, y, z, u, v, nx, ny, nz;
        private boolean pending;
        private int count;

        Recorder(boolean keep, Skin fileSkin) {
            this.keep = keep;
            this.fileSkin = fileSkin;
            this.skin = fileSkin;
        }

        private void begin(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            pending = true;
        }

        void commit() {
            if (!pending) return;
            pending = false;
            xs[count] = x;
            ys[count] = y;
            zs[count] = z;
            us[count] = u;
            vs[count] = v;
            if (++count == 4) {
                count = 0;
                if (keep && faces.size() < MOST_FACES + 1) {
                    Face face = new Face(xs, ys, zs, us, vs, nx, ny, nz, skin);
                    if (!face.flat()) faces.add(face);
                }
            }
        }

        void finish() {
            commit();
            count = 0;
        }

        //? if >=1.21 {
        /*@Override
        public VertexConsumer addVertex(float x, float y, float z) {
            commit();
            begin(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, float a, int[] light, int overlay, boolean readAlpha) {
            skin = new Sprite(quad.getSprite());
            VertexConsumer.super.putBulkData(pose, quad, brightness, r, g, b, a, light, overlay, readAlpha);
            commit();
            skin = fileSkin;
        }
        *///?} else {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            begin((float) x, (float) y, (float) z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override
        public void endVertex() {
            commit();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness, float r, float g, float b, int[] light, int overlay, boolean readAlpha) {
            skin = new Sprite(quad.getSprite());
            VertexConsumer.super.putBulkData(pose, quad, brightness, r, g, b, light, overlay, readAlpha);
            skin = fileSkin;
        }
        //?}
    }

    private final List<Thing> things = new ArrayList<>();
    private final Map<ResourceLocation, Optional<Texture>> textures = new HashMap<>();
    private final Map<RenderType, Recorder> recorders = new HashMap<>();
    private List<Face> faces = new ArrayList<>();
    private final ArrayDeque<Entity> waiting = new ArrayDeque<>();
    private final double ex, ey, ez;
    private short nextPart = 1;

    /** The last three numbers are the way you look. */
    SketchThings(Level level, double ex, double ey, double ez, double fx, double fy, double fz) {
        this.ex = ex;
        this.ey = ey;
        this.ez = ez;
        Minecraft mc = Minecraft.getInstance();
        AABB around = new AABB(ex - RANGE, ey - RANGE, ez - RANGE, ex + RANGE, ey + RANGE, ez + RANGE);
        List<Entity> nearby = new ArrayList<>(level.getEntities(mc.player, around, e -> worthDrawing(e) && !behind(e, ex, ey, ez, fx, fy, fz)));
        nearby.sort(Comparator.comparingDouble(e -> e.distanceToSqr(ex, ey, ez)));
        waiting.addAll(nearby.subList(0, Math.min(MOST_THINGS, nearby.size())));
    }

    /** Reads things until this time, so a crowd does not stall one frame. True when all are read. */
    boolean readUntil(long nanos) {
        while (!waiting.isEmpty() && Util.getNanos() < nanos) read(waiting.poll());
        return waiting.isEmpty();
    }

    private void read(Entity entity) {
        faces = new ArrayList<>();
        boolean failed = false;
        try {
            draw(entity, ex, ey, ez, this::recorder);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
        }
        recorders.values().forEach(Recorder::finish);
        if (failed || faces.size() > MOST_FACES) faces.clear();
        if (faces.isEmpty() && entity instanceof LivingEntity) addHitbox(entity, ex, ey, ez);
        if (!faces.isEmpty()) keep(entity, ey);
    }

    private static boolean worthDrawing(Entity e) {
        return !e.isInvisible() && !(e instanceof ItemEntity) && !(e instanceof ExperienceOrb) && !(e instanceof Projectile);
    }

    /** A thing behind you must not use up the limit. */
    private static boolean behind(Entity e, double ex, double ey, double ez, double fx, double fy, double fz) {
        Vec3 middle = e.getBoundingBox().getCenter();
        return (middle.x - ex) * fx + (middle.y - ey) * fy + (middle.z - ez) * fz < -(e.getBbWidth() + 3);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void draw(Entity entity, double ex, double ey, double ez, MultiBufferSource listener) {
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        Vec3 offset = renderer.getRenderOffset(entity, 1f);
        PoseStack pose = new PoseStack();
        pose.translate((float) (entity.getX() - ex + offset.x), (float) (entity.getY() - ey + offset.y), (float) (entity.getZ() - ez + offset.z));
        renderer.render(entity, entity.getYRot(), 1f, pose, listener, LightTexture.FULL_BRIGHT);
    }

    private VertexConsumer recorder(RenderType type) {
        return recorders.computeIfAbsent(type, t -> {
            boolean keep = t.mode() == VertexFormat.Mode.QUADS && (t.format() == DefaultVertexFormat.NEW_ENTITY || t.format() == DefaultVertexFormat.BLOCK);
            return new Recorder(keep, keep ? skinOf(t) : null);
        });
    }

    private Skin skinOf(RenderType type) {
        Matcher name = TEXTURE_NAME.matcher(type.toString());
        return name.find() ? textures.computeIfAbsent(ResourceLocation.tryParse(name.group(1)), SketchThings::load).orElse(null) : null;
    }

    private static Optional<Texture> load(ResourceLocation location) {
        if (location == null) return Optional.empty();
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return Optional.empty();
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int w = image.getWidth(), h = image.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) pixels[y * w + x] = image.getPixelRGBA(x, y);
            }
            return Optional.of(new Texture(w, h, pixels));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void addHitbox(Entity entity, double ex, double ey, double ez) {
        AABB b = entity.getBoundingBox().move(-ex, -ey, -ez);
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        addSide(new float[]{x0, x1, x1, x0}, new float[]{y0, y0, y1, y1}, new float[]{z0, z0, z0, z0});
        addSide(new float[]{x0, x1, x1, x0}, new float[]{y0, y0, y1, y1}, new float[]{z1, z1, z1, z1});
        addSide(new float[]{x0, x0, x0, x0}, new float[]{y0, y1, y1, y0}, new float[]{z0, z0, z1, z1});
        addSide(new float[]{x1, x1, x1, x1}, new float[]{y0, y1, y1, y0}, new float[]{z0, z0, z1, z1});
        addSide(new float[]{x0, x1, x1, x0}, new float[]{y0, y0, y0, y0}, new float[]{z0, z0, z1, z1});
        addSide(new float[]{x0, x1, x1, x0}, new float[]{y1, y1, y1, y1}, new float[]{z0, z0, z1, z1});
    }

    private void addSide(float[] xs, float[] ys, float[] zs) {
        faces.add(new Face(xs, ys, zs, new float[4], new float[4], 0, 0, 0, null));
    }

    /** Sorts the faces into cubes, finds the eyes, and stores the thing. */
    private void keep(Entity entity, double ey) {
        List<Cube> cubes = joinIntoCubes();
        cubes.sort(Comparator.comparingDouble((Cube c) -> -c.size));
        if (cubes.size() <= MOST_CUBES_TO_JOIN) {
            for (int i = 1; i < cubes.size(); i++) {
                for (int j = 0; j < i; j++) {
                    if (cubes.get(i).touches(cubes.get(j))) {
                        cubes.get(i).parent = cubes.get(j);
                        break;
                    }
                }
            }
        }
        if (entity instanceof LivingEntity mob) findEyes(mob, ey);
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (Cube c : cubes) {
            minX = Math.min(minX, c.minX);
            minY = Math.min(minY, c.minY);
            minZ = Math.min(minZ, c.minZ);
            maxX = Math.max(maxX, c.maxX);
            maxY = Math.max(maxY, c.maxY);
            maxZ = Math.max(maxZ, c.maxZ);
        }
        things.add(new Thing(minX, minY, minZ, maxX, maxY, maxZ, faces.toArray(new Face[0])));
    }

    private List<Cube> joinIntoCubes() {
        int[] root = new int[faces.size()];
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i < root.length; i++) root[i] = i;
        for (int i = 0; i < root.length; i++) {
            for (long corner : faces.get(i).corners) {
                Integer other = seen.putIfAbsent(corner, i);
                if (other != null) root[find(root, i)] = find(root, other);
            }
        }
        Map<Integer, Cube> byRoot = new HashMap<>();
        for (int i = 0; i < root.length; i++) {
            Face face = faces.get(i);
            face.cube = byRoot.computeIfAbsent(find(root, i), k -> new Cube());
            face.cube.add(face);
        }
        List<Cube> cubes = new ArrayList<>(byRoot.values());
        for (Cube c : cubes) {
            c.id = nextPart++;
            float a = c.maxX - c.minX, b = c.maxY - c.minY, d = c.maxZ - c.minZ;
            // the middle side tells a real block of a mob from a thin limb
            c.size = Math.max(Math.min(a, b), Math.min(Math.max(a, b), d));
            c.cornerRange = c.size * CORNER_RANGE;
            c.lineRange = c.size * LINE_RANGE;
        }
        return cubes;
    }

    private static int find(int[] root, int i) {
        while (root[i] != i) {
            root[i] = root[root[i]];
            i = root[i];
        }
        return i;
    }

    /** Faces that look the way the mob looks, at the height of its eyes. Their darkest pixels become eyes. */
    private void findEyes(LivingEntity mob, double ey) {
        Vec3 look = mob.getViewVector(1f);
        float eyeY = (float) (mob.getEyeY() - ey), window = Math.max(0.12f, mob.getBbHeight() * 0.25f);
        for (Face f : faces) {
            if (!(f.skin instanceof Texture texture)) continue;
            if (f.outX * look.x + f.outY * look.y + f.outZ * look.z < 0.7) continue;
            if (Math.abs((f.box[1] + f.box[4]) / 2 - eyeY) > window) continue;
            f.darkBelow = darkLimit(texture, f);
        }
    }

    private static int luma(int abgr) {
        return ((abgr & 255) * 3 + (abgr >> 8 & 255) * 6 + (abgr >> 16 & 255)) / 10;
    }

    /** How dark a pixel must be to count, or 0. A face that is mostly dark has no eyes to find. */
    private static int darkLimit(Texture texture, Face f) {
        float u0 = Math.min(f.u, Math.min(f.u + f.ue, f.u + f.uf)), u1 = Math.max(f.u, Math.max(f.u + f.ue, f.u + f.uf));
        float v0 = Math.min(f.v, Math.min(f.v + f.ve, f.v + f.vf)), v1 = Math.max(f.v, Math.max(f.v + f.ve, f.v + f.vf));
        int x0 = Math.max(0, Math.round(u0 * texture.w())), x1 = Math.min(texture.w(), Math.round(u1 * texture.w()));
        int y0 = Math.max(0, Math.round(v0 * texture.h())), y1 = Math.min(texture.h(), Math.round(v1 * texture.h()));
        if (x1 <= x0 || y1 <= y0) return 0;
        int[] all = new int[(x1 - x0) * (y1 - y0)];
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int p = texture.pixels()[y * texture.w() + x];
                if (p >>> 24 > 128) all[count++] = luma(p);
            }
        }
        if (count == 0) return 0;
        Arrays.sort(all, 0, count);
        int limit = Math.min(90, all[count / 2] / 2), found = 0;
        for (int i = 0; i < count && all[i] < limit; i++) found++;
        return found == 0 || found * 3 > count ? 0 : limit;
    }

    /** Tests one ray from the eye against every thing. A nearer hit than the blocks wins. */
    void cast(SketchRays rays, int i, float dx, float dy, float dz) {
        for (Thing thing : things) {
            if (!hitsBounds(thing, dx, dy, dz, rays.t[i])) continue;
            for (Face face : thing.faces()) test(face, rays, i, dx, dy, dz);
        }
    }

    private static boolean hitsBounds(Thing m, float dx, float dy, float dz, float limit) {
        float near = 0, far = limit;
        float a = m.minX() / dx, b = m.maxX() / dx;
        near = Math.max(near, Math.min(a, b));
        far = Math.min(far, Math.max(a, b));
        a = m.minY() / dy;
        b = m.maxY() / dy;
        near = Math.max(near, Math.min(a, b));
        far = Math.min(far, Math.max(a, b));
        a = m.minZ() / dz;
        b = m.maxZ() / dz;
        near = Math.max(near, Math.min(a, b));
        far = Math.min(far, Math.max(a, b));
        return near <= far;
    }

    private static void test(Face f, SketchRays rays, int i, float dx, float dy, float dz) {
        if (f.outX * dx + f.outY * dy + f.outZ * dz > 0) return;
        float t = f.nd / (f.nx * dx + f.ny * dy + f.nz * dz);
        if (!(t > 0) || t >= rays.t[i]) return;
        float px = dx * t - f.ax, py = dy * t - f.ay, pz = dz * t - f.az;
        float s = (px * f.ex + py * f.ey + pz * f.ez) * f.ee;
        if (s < 0 || s > 1) return;
        float w = (px * f.fx + py * f.fy + pz * f.fz) * f.ff;
        if (w < 0 || w > 1) return;
        float u = f.u + s * f.ue + w * f.uf, v = f.v + s * f.ve + w * f.vf;
        if (f.skin != null && !f.skin.solid(u, v)) return;
        rays.t[i] = t;
        rays.family[i] = SketchDrawer.F_THING;
        Cube cube = f.cube;
        while (t >= cube.lineRange && cube.parent != null) cube = cube.parent;
        rays.part[i] = cube.id;
        rays.normal[i] = t < f.cube.cornerRange ? f.look : 0;
        rays.dot[i] = f.darkBelow > 0 && luma(((Texture) f.skin).pixel(u, v)) < f.darkBelow;
    }

    boolean any() {
        return !things.isEmpty();
    }

    int count() {
        return things.size();
    }
}
