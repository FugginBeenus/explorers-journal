package com.fugginbeenus.explorersjournal.client;

import com.fugginbeenus.explorersjournal.journal.Entry;

/** The paper tabs. Contents always has one. A section gets one once it has an entry. */
public enum Section {
    CONTENTS("Contents", "C", 12, 0xFFD9CBA3, 0xFFEADFC0, 0xFF8F7F5B),
    LOCATIONS("Locations", "L", 33, 0xFFC68E58, 0xFFDCA874, 0xFF7C5430),
    DRAWINGS("Drawings", "D", 57, 0xFF8E9A5A, 0xFFA8B474, 0xFF566034),
    SCRAPBOOK("Scrapbook", "S", 78, 0xFFBE6254, 0xFFD68072, 0xFF783830),
    NOTES("Notes", "N", 102, 0xFF6E8EA4, 0xFF8CAABE, 0xFF42586C);

    public final String title, letter;
    public final int y, paper, light, dark;

    Section(String title, String letter, int y, int paper, int light, int dark) {
        this.title = title;
        this.letter = letter;
        this.y = y;
        this.paper = paper;
        this.light = light;
        this.dark = dark;
    }

    public static Section of(Entry entry) {
        return entry.isLocation() ? LOCATIONS : entry.isSketch() || entry.isDrawing() ? DRAWINGS : entry.isScrapbook() ? SCRAPBOOK : NOTES;
    }
}
