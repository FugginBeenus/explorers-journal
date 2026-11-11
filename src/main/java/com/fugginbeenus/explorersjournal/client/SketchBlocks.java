package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.sketch.SketchDrawer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.regex.Pattern;

/** What a block means to the sketch: which kind of line it gets, and which boxes a ray can hit. Decided once for each block state. */
final class SketchBlocks {
    /** A small thing is only drawn up close. */
    record Info(byte family, byte ground, boolean plant, boolean flower, boolean upper, boolean small, float[][] boxes) {}

    private static final float[][] NO_BOXES = new float[0][];
    // rays go through these. Anything with no collision goes through too, unless it is a small thing worth drawing.
    private static final Pattern PASS = Pattern.compile("^(.*_carpet|snow|barrier|light|structure_void)$");
    private static final Pattern SMALL = Pattern.compile("^(.*torch|lantern|soul_lantern|end_rod|candle|.*_candle|bell|flower_pot|potted_.*|.*_sign|.*_banner|lever|.*_button|tripwire_hook|ladder|lily_pad)$");
    private static final Pattern PLANT = Pattern.compile("^(short_grass|grass|tall_grass|fern|large_fern|dead_bush|.*_sapling|red_mushroom|brown_mushroom|crimson_fungus|warped_fungus|crimson_roots|warped_roots|nether_sprouts|dandelion|poppy|blue_orchid|allium|azure_bluet|.*_tulip|oxeye_daisy|cornflower|lily_of_the_valley|sunflower|lilac|rose_bush|peony|torchflower|sweet_berry_bush|wheat|carrots|potatoes|beetroots)$");
    private static final Pattern GROUND = Pattern.compile("^(grass_block|dirt|coarse_dirt|podzol|mycelium|rooted_dirt|dirt_path|farmland|mud|muddy_mangrove_roots|moss_block|snow_block|powder_snow|sand|red_sand|suspicious_sand|gravel|suspicious_gravel|clay|stone|andesite|diorite|granite|deepslate|tuff|calcite|sandstone|red_sandstone|terracotta|.*_terracotta|.*_ore|infested_.*|packed_ice|ice|blue_ice|bedrock|dripstone_block|netherrack|crimson_nylium|warped_nylium|magma_block|end_stone|soul_sand|soul_soil|basalt|smooth_basalt|blackstone|sculk|amethyst_block|budding_amethyst)$");
    private static final Pattern LEAFY = Pattern.compile("^(nether_wart_block|warped_wart_block|shroomlight|azalea|flowering_azalea|red_mushroom_block|brown_mushroom_block)$");
    private static final Pattern LOG = Pattern.compile(".*(_log|_wood|_stem|_hyphae)$|^stripped_.*|^mangrove_roots$");
    private static final Pattern MASONRY = Pattern.compile(".*(cobble|brick|smooth_stone|polished_|stone_stairs|stone_slab|andesite_|diorite_|granite_|sandstone_|_wall$|prismarine|quartz|mossy).*|^stone_.*");
    private static final Pattern PLANKS = Pattern.compile(".*(planks|oak_|spruce_|birch_|jungle_|acacia_|dark_oak_|mangrove_|cherry_|bamboo_|crimson_|warped_).*");

    private SketchBlocks() {}

    static Info read(BlockState state, BlockGetter level, BlockPos pos) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        boolean small = SMALL.matcher(name).matches();
        boolean pass = !small && (PASS.matcher(name).matches() || state.getCollisionShape(level, pos).isEmpty());
        boolean flower = state.is(BlockTags.FLOWERS);
        boolean plant = flower || PLANT.matcher(name).matches() || state.is(BlockTags.CROPS);
        boolean upper = state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
        return new Info(family(state, name), ground(name), plant, flower, upper, small, pass ? NO_BOXES : boxes(state, level, pos));
    }

    private static float[][] boxes(BlockState state, BlockGetter level, BlockPos pos) {
        List<AABB> shape = state.getShape(level, pos).toAabbs();
        float[][] boxes = new float[shape.size()][];
        for (int i = 0; i < boxes.length; i++) {
            AABB b = shape.get(i);
            boxes[i] = new float[]{(float) b.minX, (float) b.minY, (float) b.minZ, (float) b.maxX, (float) b.maxY, (float) b.maxZ};
        }
        return boxes;
    }

    private static byte family(BlockState state, String name) {
        if (name.equals("water") || name.equals("bubble_column")) return SketchDrawer.F_WATER;
        if (state.is(BlockTags.LEAVES) || name.endsWith("_leaves") || LEAFY.matcher(name).matches()) return SketchDrawer.F_LEAVES;
        if (state.is(BlockTags.LOGS) || LOG.matcher(name).matches()) return SketchDrawer.F_LOG;
        if (name.contains("door") && !name.contains("trapdoor")) return SketchDrawer.F_DOOR;
        if (name.contains("glass")) return SketchDrawer.F_GLASS;
        if (GROUND.matcher(name).matches()) return SketchDrawer.F_GROUND;
        if (MASONRY.matcher(name).matches()) return SketchDrawer.F_MASONRY;
        if (state.is(BlockTags.PLANKS) || PLANKS.matcher(name).matches()) return SketchDrawer.F_PLANKS;
        return SketchDrawer.F_OTHER;
    }

    private static byte ground(String name) {
        return switch (name) {
            case "dirt_path" -> 1;
            case "sand", "red_sand", "gravel" -> 2;
            case "farmland" -> 3;
            case "stone", "andesite", "diorite", "granite", "tuff", "deepslate" -> 4;
            default -> 0;
        };
    }
}
