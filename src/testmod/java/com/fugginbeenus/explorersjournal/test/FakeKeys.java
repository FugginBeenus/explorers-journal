package com.fugginbeenus.explorersjournal.test;

/** The game asks the operating system if shift or control is down. A script cannot press them, so it sets these instead. */
public final class FakeKeys {
    public static volatile boolean shift, control;

    private FakeKeys() {}
}
