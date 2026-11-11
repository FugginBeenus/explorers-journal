package com.fugginbeenus.explorersjournal.journal;

import java.util.ArrayList;
import java.util.List;

/** One page of the journal. Saved as JSON, so field names are the file format. */
public final class Entry {
    public static final String NOTE = "note";
    public static final String LOCATION = "location";
    public static final String SCRAPBOOK = "scrapbook";
    public static final String SKETCH = "sketch";
    public static final String DRAWING = "drawing";

    public String id;
    public String type = NOTE;
    public long day;
    public String title = "";
    public String text = "";

    public String dim;
    public int x, y, z;
    public String map;

    // what the place was called when you marked it, and from Location Tooltip: its region, map icon, its name now if that changed, and the other places on the map
    public String place, placeName, marker, renamed;
    public boolean nameable;
    public List<MapMark> marks;

    public List<Keepsake> keepsakes = new ArrayList<>();

    public boolean isLocation() {
        return LOCATION.equals(type);
    }

    /** Location Tooltip renamed this place since you marked it, and you never changed its title yourself. */
    public boolean renamedSince() {
        return renamed != null && title.equals(placeName);
    }

    public boolean isSketch() {
        return SKETCH.equals(type);
    }

    public boolean isDrawing() {
        return DRAWING.equals(type);
    }

    /** A note with nothing left in it is gone. A place or a sketch still holds its picture, and a keepsake still holds its item. */
    public boolean isEmpty() {
        return title.isBlank() && text.isBlank() && keepsakes.isEmpty() && map == null && !isLocation();
    }

    public boolean isScrapbook() {
        return SCRAPBOOK.equals(type);
    }

    public Keepsake keepsake(String keepsakeId) {
        for (Keepsake k : keepsakes) {
            if (k.id.equals(keepsakeId)) return k;
        }
        return null;
    }
}
