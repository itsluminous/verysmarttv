package com.itsluminous.verysmarttv.support;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.IOException;

/**
 * The TV apps a test counts: Tile 1 to Tile 6 are leanback aliases of the instrumentation APK (its
 * AndroidManifest.xml), always installed with it; Tile 7 is the {@code :testkit:tiles} package, switched on and off
 * with {@code pm enable} / {@code pm disable-user} as the owner would over adb (Android 14's shell may switch whole
 * packages only, which is why the seventh is a package of its own). The launcher's leanback query sees the change at
 * its next rebuild, so a test enables the count it wants before Home is created.
 */
public final class DummyTiles {
    private DummyTiles() {}

    /** Tile 7's package. */
    public static final String SEVENTH = "com.itsluminous.verysmarttv.testkit.tiles";

    /** How many there are with Tile 7 on. */
    public static final int MAX = 7;

    /** The instrumentation APK's package, the one Tile 1 to Tile 6 belong to. */
    public static String packageName() {
        return InstrumentationRegistry.getInstrumentation().getContext().getPackageName();
    }

    /** The label of tile {@code n} (1-based), as the manifests spell it and the grid shows it. */
    public static String label(int n) {
        return "Tile " + n;
    }

    /** Six apps (Tile 7 disabled) or seven (Tile 7 enabled). */
    public static void enable(int n) throws IOException {
        if (n != MAX - 1 && n != MAX) throw new IllegalArgumentException("6 or 7 tiles, not " + n);
        String out = Shell.run("pm " + (n == MAX ? "enable" : "disable-user") + " --user 0 " + SEVENTH);
        if (!out.contains("new state")) {
            throw new IOException("pm could not switch " + SEVENTH + " (is :testkit:tiles installed? ./gradlew :testkit:tiles:installDebug): " + out.trim());
        }
    }

    /** All seven on: the state the packages install with, and the one every test leaves behind. */
    public static void enableAll() throws IOException {
        enable(MAX);
    }
}
