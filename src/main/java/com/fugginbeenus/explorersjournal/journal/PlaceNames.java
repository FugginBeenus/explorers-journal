package com.fugginbeenus.explorersjournal.journal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** The first title of a location entry. Same rules as Location Tooltip, so a village has the same name in both mods. */
public final class PlaceNames {
    private static final List<String> SUFFIXES = List.of(
            "dell", "brook", "ford", "hollow", "wick", "stead", "mere", "fell",
            "holt", "gate", "wood", "field", "bury", "ton", "march", "crest");
    private static final List<String> NOUNS = List.of(
            "Shire", "Hamlet", "Crossing", "Rest", "Watch", "Hollow", "Reach",
            "Landing", "Wells", "Row", "Green", "End", "Barrow", "Court", "Vale");
    private static final List<String> GENERIC = List.of(
            "Iron", "Old", "Still", "Raven", "Stone", "Elder", "Silver", "Black",
            "High", "Red", "Grey", "Long", "Bell", "Fox", "Crow", "Hearth");

    private record Palette(List<String> heads, List<String> suffixes, List<String> nouns) {
        Palette(List<String> heads) { this(heads, SUFFIXES, NOUNS); }
    }

    private static final Palette SNOWY = new Palette(List.of("Frost", "Winter", "Rime", "Snow", "Pale", "North", "Hoar", "Chill", "White"));
    private static final Palette DESERT = new Palette(List.of("Dune", "Sun", "Amber", "Dust", "Sand", "Dry", "Copper", "Scorch", "Bone"));
    private static final Palette BADLANDS = new Palette(List.of("Clay", "Rust", "Ochre", "Mesa", "Ember", "Kiln", "Sienna", "Gulch"));
    private static final Palette TAIGA = new Palette(List.of("Pine", "Spruce", "Fern", "Timber", "Elk", "Moss", "Bear", "Cedar"));
    private static final Palette FOREST = new Palette(List.of("Oak", "Birch", "Bramble", "Thorn", "Bloom", "Glade", "Aspen", "Ivy"));
    private static final Palette SAVANNA = new Palette(List.of("Acacia", "Gold", "Wide", "Dry", "Grass", "Amber", "Sun", "Wind"));
    private static final Palette PLAINS = new Palette(List.of("Green", "Meadow", "Wheat", "Hill", "Fair", "Bright", "Willow", "Apple"));
    private static final Palette JUNGLE = new Palette(List.of("Vine", "Emerald", "Canopy", "Cocoa", "Parrot", "Mist", "Bamboo", "Fern"));
    private static final Palette SWAMP = new Palette(
            List.of("Bog", "Murk", "Reed", "Willow", "Marsh", "Mire", "Peat", "Toad"),
            List.of("mire", "fen", "marsh", "bog", "hollow", "mere", "brook", "holt"),
            List.of("Fen", "Mire", "Bog", "Hollow", "Rest", "Crossing", "Reach"));
    private static final Palette OCEAN = new Palette(
            List.of("Salt", "Tide", "Coral", "Pearl", "Wave", "Kelp", "Drift", "Gull", "Brine", "Foam"),
            List.of("haven", "port", "cove", "reef", "shoal", "wharf", "strand", "mere"),
            List.of("Harbour", "Cove", "Landing", "Wharf", "Anchorage", "Bay", "Shoals", "Reach"));
    private static final Palette MUSHROOM = new Palette(List.of("Spore", "Cap", "Violet", "Shroud", "Glow", "Gill", "Ruby"));
    private static final Palette NETHER = new Palette(
            List.of("Ash", "Ember", "Soul", "Cinder", "Scorch", "Char", "Gloom", "Blaze", "Basalt"),
            List.of("forge", "pyre", "hollow", "gate", "reach", "fell", "crest", "march"),
            List.of("Forge", "Pyre", "Hollow", "Watch", "Gate", "Reach", "End", "Rest"));

    private static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("pillager_outpost", "Pillager Outpost"), Map.entry("mansion", "Woodland Mansion"),
            Map.entry("monument", "Ocean Monument"), Map.entry("ancient_city", "Ancient City"),
            Map.entry("fortress", "Nether Fortress"), Map.entry("bastion_remnant", "Bastion Remnant"),
            Map.entry("end_city", "End City"), Map.entry("stronghold", "Stronghold"),
            Map.entry("desert_pyramid", "Desert Pyramid"), Map.entry("jungle_pyramid", "Jungle Temple"),
            Map.entry("swamp_hut", "Witch Hut"), Map.entry("igloo", "Igloo"), Map.entry("trail_ruins", "Trail Ruins"),
            Map.entry("shipwreck", "Shipwreck"), Map.entry("shipwreck_beached", "Beached Shipwreck"),
            Map.entry("ocean_ruin_cold", "Cold Ocean Ruins"), Map.entry("ocean_ruin_warm", "Warm Ocean Ruins"),
            Map.entry("buried_treasure", "Buried Treasure"), Map.entry("mineshaft", "Mineshaft"),
            Map.entry("mineshaft_mesa", "Badlands Mineshaft"), Map.entry("nether_fossil", "Nether Fossil"),
            Map.entry("ruined_portal", "Ruined Portal"), Map.entry("ruined_portal_desert", "Ruined Portal"),
            Map.entry("ruined_portal_jungle", "Ruined Portal"), Map.entry("ruined_portal_swamp", "Ruined Portal"),
            Map.entry("ruined_portal_mountain", "Ruined Portal"), Map.entry("ruined_portal_ocean", "Ruined Portal"),
            Map.entry("ruined_portal_nether", "Ruined Portal"));

    private PlaceNames() {}

    public static String at(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceLocation dim = level.dimension().location();
        String best = null;
        long bestVolume = Long.MAX_VALUE;
        for (Structure structure : chunk.getAllReferences().keySet()) {
            StructureStart start = level.structureManager().getStructureAt(pos, structure);
            ResourceLocation id = registry.getKey(structure);
            if (!start.isValid() || id == null) continue;
            BoundingBox box = start.getBoundingBox();
            long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
            if (volume < bestVolume) {
                bestVolume = volume;
                best = structureName(level, dim, id, box);
            }
        }
        if (best != null) return best;
        return level.getBiome(pos).unwrapKey().map(key -> pretty(key.location().getPath())).orElse("Somewhere");
    }

    private static String structureName(ServerLevel level, ResourceLocation dim, ResourceLocation id, BoundingBox box) {
        String path = id.getPath();
        if (path.contains("village")) {
            if (has(path, "trader", "camp")) return pretty(path.substring(path.lastIndexOf('/') + 1).replace("village_", ""));
            long seed = dim.toString().hashCode();
            seed = seed * 31L + box.minX();
            seed = seed * 31L + box.minZ();
            return villageName(new Random(seed), palette(level, id, box));
        }
        String nice = STRUCTURES.get(path);
        return nice != null ? nice : pretty(path);
    }

    private static Palette palette(ServerLevel level, ResourceLocation id, BoundingBox box) {
        Palette fromId = match(id.getPath());
        if (fromId != null) return fromId;
        int x = (box.minX() + box.maxX()) / 2, y = (box.minY() + box.maxY()) / 2, z = (box.minZ() + box.maxZ()) / 2;
        return match(level.getUncachedNoiseBiome(x >> 2, y >> 2, z >> 2).unwrapKey().map(key -> key.location().getPath()).orElse(null));
    }

    private static Palette match(String path) {
        if (path == null || path.isEmpty()) return null;
        if (has(path, "snow", "frozen", "ice", "grove", "slopes")) return SNOWY;
        if (has(path, "desert")) return DESERT;
        if (has(path, "badlands", "mesa")) return BADLANDS;
        if (has(path, "ocean", "beach", "shore", "coast", "reef", "tidal")) return OCEAN;
        if (has(path, "jungle", "bamboo")) return JUNGLE;
        if (has(path, "swamp", "mangrove")) return SWAMP;
        if (has(path, "mushroom", "fungus")) return MUSHROOM;
        if (has(path, "nether", "crimson", "warped", "soul", "basalt", "piglin")) return NETHER;
        if (has(path, "savanna")) return SAVANNA;
        if (has(path, "plains", "meadow", "sunflower")) return PLAINS;
        if (has(path, "taiga", "spruce", "pine")) return TAIGA;
        if (has(path, "forest", "birch", "wooded", "cherry", "flower")) return FOREST;
        return null;
    }

    private static boolean has(String path, String... keys) {
        for (String key : keys) {
            if (path.contains(key)) return true;
        }
        return false;
    }

    private static String villageName(Random random, Palette flavour) {
        List<String> heads = flavour == null ? GENERIC : flavour.heads();
        List<String> suffixes = flavour == null ? SUFFIXES : flavour.suffixes();
        List<String> nouns = flavour == null ? NOUNS : flavour.nouns();
        List<String> pool = random.nextInt(10) < 7 ? heads : GENERIC;
        String head = pool.get(random.nextInt(pool.size()));
        if (random.nextBoolean()) return head + suffixes.get(random.nextInt(suffixes.size()));
        return head + " " + nouns.get(random.nextInt(nouns.size()));
    }

    private static String pretty(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.replace('/', ' ').replace('_', ' ').trim().split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? "Somewhere" : out.toString();
    }
}
