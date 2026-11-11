package com.fugginbeenus.explorersjournal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The settings in config/explorersjournal.json. The game writes the file with these defaults.
 * textDrift is for your own screen. The rest are rules, and on a server only the server's file counts.
 */
public final class EJConfig {
    private static final Logger LOG = LoggerFactory.getLogger("explorersjournal");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("explorersjournal.json");
    private static volatile EJConfig instance;

    public boolean textDrift = true;
    public boolean othersCanRead = true;
    public boolean pasteCostsPaper = false;
    public int maxEntries = 500;
    // how many entries a journal has held when it reaches wear stages 1 to 4
    public int[] wearAt = {10, 30, 75, 150};

    private EJConfig() {}

    public static EJConfig get() {
        EJConfig cfg = instance;
        if (cfg != null) return cfg;
        synchronized (EJConfig.class) {
            if (instance == null) instance = load();
            return instance;
        }
    }

    /** Reads the file again. A broken file keeps the settings you had and says why in the log. */
    public static synchronized String reload() {
        EJConfig fresh = read();
        if (fresh == null) return "The config file has a mistake, so nothing changed. The log says where.";
        instance = fresh;
        return null;
    }

    private static EJConfig load() {
        EJConfig cfg = read();
        if (cfg == null) cfg = new EJConfig();
        if (!Files.exists(PATH)) cfg.save();
        return cfg;
    }

    private static EJConfig read() {
        if (!Files.exists(PATH)) return new EJConfig();
        try (Reader r = Files.newBufferedReader(PATH)) {
            EJConfig cfg = GSON.fromJson(r, EJConfig.class);
            if (cfg == null) cfg = new EJConfig();
            cfg.clamp();
            return cfg;
        } catch (IOException | RuntimeException e) {
            LOG.error("Could not read {}", PATH, e);
            return null;
        }
    }

    private void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            LOG.error("Could not write {}", PATH, e);
        }
    }

    private void clamp() {
        maxEntries = Math.max(1, Math.min(2000, maxEntries));
        int[] given = wearAt == null ? new int[0] : wearAt;
        int[] steps = {10, 30, 75, 150};
        for (int i = 0; i < steps.length; i++) {
            int at = i < given.length ? given[i] : steps[i];
            // each stage needs more entries than the one before
            steps[i] = Math.max(i == 0 ? 1 : steps[i - 1] + 1, at);
        }
        wearAt = steps;
    }
}
