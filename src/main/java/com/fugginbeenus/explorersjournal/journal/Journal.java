package com.fugginbeenus.explorersjournal.journal;

import com.fugginbeenus.explorersjournal.config.EJConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

public final class Journal {
    public static final int STAGES = 5;

    private static final Gson NET = new Gson();
    static final Gson DISK = new GsonBuilder().setPrettyPrinting().create();

    public String id;
    public String owner;
    public String ownerName = "";
    public int wearOverride = -1;
    public int mostEntries;
    public String bookmark = "";
    // the place shown on Location Tooltip's pills, if any
    public String pinned = "";
    public List<Entry> entries = new ArrayList<>();

    /** Deleting an entry does not make a used book look new again. */
    public int wear() {
        if (wearOverride >= 0) return Math.min(wearOverride, STAGES - 1);
        int used = Math.max(entries.size(), mostEntries);
        int[] at = EJConfig.get().wearAt;
        int stage = 0;
        for (int i = 0; i < at.length; i++) {
            if (used >= at[i]) stage = i + 1;
        }
        return Math.min(stage, STAGES - 1);
    }

    public Entry entry(String entryId) {
        for (Entry e : entries) {
            if (e.id.equals(entryId)) return e;
        }
        return null;
    }

    public String toNet() {
        return NET.toJson(this);
    }

    public static Journal fromNet(String json) {
        return NET.fromJson(json, Journal.class);
    }
}
