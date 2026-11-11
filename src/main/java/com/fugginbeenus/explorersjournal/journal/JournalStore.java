package com.fugginbeenus.explorersjournal.journal;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Journals live in the world folder, one folder per journal. The item only holds the ID. */
public final class JournalStore {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static final Map<String, Journal> OPEN = new HashMap<>();
    private static final Map<String, Journal> WAITING = new HashMap<>();

    private JournalStore() {}

    public static Path folder(MinecraftServer server, String id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/explorers_journal").resolve(id);
    }

    public static Journal create(MinecraftServer server, UUID owner, String ownerName) {
        Journal journal = new Journal();
        journal.id = UUID.randomUUID().toString();
        journal.owner = owner.toString();
        journal.ownerName = ownerName;
        OPEN.put(journal.id, journal);
        save(server, journal);
        return journal;
    }

    /** The ID comes from item data, which a player can edit, so it must really be a UUID before it touches a path. */
    public static Journal get(MinecraftServer server, String id) {
        String clean;
        try {
            clean = UUID.fromString(id).toString();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        Journal cached = OPEN.get(clean);
        if (cached != null) return cached;
        Path file = folder(server, clean).resolve("journal.json");
        if (!Files.exists(file)) return null;
        try {
            Journal journal = Journal.DISK.fromJson(Files.readString(file), Journal.class);
            OPEN.put(clean, journal);
            return journal;
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not read journal {}", clean, e);
            return null;
        }
    }

    /** Saves now, unless an item moved. Then the journal waits for the world. */
    public static void save(MinecraftServer server, Journal journal) {
        if (!WAITING.containsKey(journal.id)) write(server, journal);
    }

    /** An item moved. The game saves inventories only with the world, so the journal waits for the same moment. A crash then undoes both, and the item is never in two places. */
    public static void saveWithWorld(Journal journal) {
        WAITING.put(journal.id, journal);
    }

    public static void saveWaiting(MinecraftServer server) {
        WAITING.values().forEach(journal -> write(server, journal));
        WAITING.clear();
    }

    private static void write(MinecraftServer server, Journal journal) {
        Path dir = folder(server, journal.id);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("journal.json.tmp");
            Files.writeString(tmp, Journal.DISK.toJson(journal));
            Files.move(tmp, dir.resolve("journal.json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.error("Could not save journal {}", journal.id, e);
        }
    }

    public static boolean saveMap(MinecraftServer server, String journalId, String mapId, byte[] packed) {
        try {
            Path dir = folder(server, journalId).resolve("img");
            Files.createDirectories(dir);
            Files.write(dir.resolve(mapId + ".bin"), packed);
            return true;
        } catch (IOException e) {
            LOG.error("Could not save map {}", mapId, e);
            return false;
        }
    }

    public static byte[] loadMap(MinecraftServer server, String journalId, String mapId) {
        try {
            return Files.readAllBytes(folder(server, journalId).resolve("img").resolve(mapId + ".bin"));
        } catch (IOException e) {
            LOG.error("Could not read map {}", mapId, e);
            return null;
        }
    }

    public static void deleteMap(MinecraftServer server, String journalId, String mapId) {
        try {
            Files.deleteIfExists(folder(server, journalId).resolve("img").resolve(mapId + ".bin"));
        } catch (IOException e) {
            LOG.error("Could not delete map {}", mapId, e);
        }
    }

    public static void clear() {
        OPEN.clear();
        WAITING.clear();
    }
}
