package com.fugginbeenus.explorersjournal.map;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Reads the blocks around a spot from loaded chunks only, a few chunks per tick, then draws on a background thread. */
public final class MapJob {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static final int CHUNKS_PER_TICK = 16;
    private static final Pattern BUILT = Pattern.compile("planks|cobble|brick|stairs|slab|fence|glass|wool|door|lantern|bell|terracotta|concrete|barrel|smoker|furnace|_bed$|chest|crafting|fletching|smithing|stonecutter|cartography|loom|lectern|grindstone|cauldron|composter|hay_block");
    private static final Map<Block, Byte> KINDS = new IdentityHashMap<>();
    private static final List<MapJob> RUNNING = new ArrayList<>();

    private final MinecraftServer server;
    private final ServerLevel level;
    private final int x0, z0, firstCx, lastCx, lastCz;
    private final long seed;
    private final Consumer<byte[]> done;
    private final byte[] flags = new byte[MapDrawer.BLOCKS_W * MapDrawer.BLOCKS_H];
    private final short[] ground = new short[flags.length];
    private int cx, cz;
    private long readNanos;

    private MapJob(ServerLevel level, BlockPos center, Consumer<byte[]> done) {
        this.server = level.getServer();
        this.level = level;
        this.x0 = center.getX() - MapDrawer.W;
        this.z0 = center.getZ() - MapDrawer.H;
        this.firstCx = x0 >> 4;
        this.lastCx = (x0 + MapDrawer.BLOCKS_W - 1) >> 4;
        this.lastCz = (z0 + MapDrawer.BLOCKS_H - 1) >> 4;
        this.cx = firstCx;
        this.cz = z0 >> 4;
        this.seed = center.getX() * 341873128712L + center.getZ() * 132897987541L;
        this.done = done;
    }

    /** done gets the packed image, back on the server thread. */
    public static void start(ServerLevel level, BlockPos center, Consumer<byte[]> done) {
        RUNNING.add(new MapJob(level, center, done));
    }

    public static void tickAll() {
        for (Iterator<MapJob> it = RUNNING.iterator(); it.hasNext(); ) {
            if (it.next().tick()) it.remove();
        }
    }

    public static void clear() {
        RUNNING.clear();
    }

    private boolean tick() {
        long t0 = System.nanoTime();
        for (int n = 0; n < CHUNKS_PER_TICK; n++) {
            if (cz > lastCz) break;
            readChunk(cx, cz);
            if (++cx > lastCx) {
                cx = firstCx;
                cz++;
            }
        }
        readNanos += System.nanoTime() - t0;
        if (cz <= lastCz) return false;

        Util.backgroundExecutor().execute(() -> {
            long t1 = System.nanoTime();
            byte[] packed = MapDrawer.pack(MapDrawer.draw(flags, ground, seed));
            LOG.info("Map: read {} ms on the server thread, drew {} ms off it, {} bytes",
                    readNanos / 1_000_000, (System.nanoTime() - t1) / 1_000_000, packed.length);
            server.execute(() -> done.accept(packed));
        });
        return true;
    }

    private void readChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lz = 0; lz < 16; lz++) {
            int gy = (chunkZ << 4) + lz - z0;
            if (gy < 0 || gy >= MapDrawer.BLOCKS_H) continue;
            for (int lx = 0; lx < 16; lx++) {
                int gx = (chunkX << 4) + lx - x0;
                if (gx < 0 || gx >= MapDrawer.BLOCKS_W) continue;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
                BlockState state = chunk.getBlockState(pos.set(lx, top, lz));
                int i = gy * MapDrawer.BLOCKS_W + gx;
                flags[i] = (byte) (kind(state) | MapDrawer.LOADED);
                ground[i] = (short) chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
            }
        }
    }

    private static int kind(BlockState state) {
        int water = state.getFluidState().is(FluidTags.WATER) ? MapDrawer.WATER : 0;
        Byte known = KINDS.get(state.getBlock());
        if (known == null) {
            known = classify(state);
            KINDS.put(state.getBlock(), known);
        }
        return known | water;
    }

    private static byte classify(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        int kind = 0;
        if (name.contains("water") || name.equals("ice") || name.equals("packed_ice") || name.equals("blue_ice") || name.equals("kelp")
                || name.equals("kelp_plant") || name.equals("seagrass") || name.equals("tall_seagrass") || name.equals("lily_pad")) kind |= MapDrawer.WATER;
        if (name.endsWith("_leaves") || state.is(BlockTags.LEAVES)) kind |= MapDrawer.LEAVES;
        if (name.equals("spruce_leaves")) kind |= MapDrawer.CONIFER;
        if (BUILT.matcher(name).find()) kind |= MapDrawer.BUILT;
        if (name.equals("dirt_path")) kind |= MapDrawer.PATH;
        if (name.equals("farmland") || name.equals("wheat") || name.equals("carrots") || name.equals("potatoes") || name.equals("beetroots")) kind |= MapDrawer.FARM;
        return (byte) kind;
    }
}
