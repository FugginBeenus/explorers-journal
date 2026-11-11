package com.fugginbeenus.explorersjournal.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Results go to a file in the run folder, so a script can be checked after the game has closed. */
final class Report {
    private Report() {}

    static void check(String what, boolean ok) {
        line((ok ? "PASS " : "FAIL ") + what);
    }

    static void line(String s) {
        System.out.println("[ejtest] " + s);
        try {
            Files.writeString(Path.of("ejtest.log"), s + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
