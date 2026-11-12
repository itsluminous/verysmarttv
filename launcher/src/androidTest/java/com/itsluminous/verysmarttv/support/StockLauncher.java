package com.itsluminous.verysmarttv.support;

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The emulator's stock launcher, disabled for the length of one instrumentation run and enabled again when the run
 * ends (ADR-052). {@code HomeSmokeTest} disables it when {@code cmd package set-home-activity} cannot take Home from
 * it (the API 34 {@code google_atv} and {@code android-tv} images, which carry {@code com.google.android.tvlauncher});
 * {@link RunEndListener} enables it again after the last test. Enabling it between tests is what hung the CI runs:
 * once enabled it is Home again (the preferred Home set by the test is dropped as soon as a second Home appears), so
 * every finished {@code HomeActivity} brought it up, and on a fresh image it answers with a dialog
 * ({@code ShowDialogsActivity}) under which the next {@code ActivityScenario.launch} waited forever.
 *
 * <p>Gradle uninstalls the launcher after a connected run, and a TV with its stock launcher disabled and no other Home
 * would be left on FallbackHome (the state the Home guard exists to prevent), which is why it must come back. All
 * tests of a run share this process, so a static holds what was disabled.
 */
public final class StockLauncher {
    private static final String TAG = "VerySmartTV.StockLauncher";

    /** The stock launchers the install flow may disable once this launcher is installed (DESIGN.md §16, carried KNOWN_STOCK_LAUNCHERS). */
    public static final List<String> PACKAGES = Arrays.asList("com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx", "com.google.android.leanbacklauncher", "com.mitv.tvhome");

    @Nullable
    private static String disabled;

    private StockLauncher() {}

    /** Whether {@code pkg} is one of the stock launchers the install flow knows. */
    public static boolean isStock(String pkg) {
        return PACKAGES.contains(pkg);
    }

    /** Disables {@code pkg} for the rest of the run, the way the install flow does; a second call for the same package does nothing. */
    public static void disableForTheRun(String pkg) throws IOException {
        if (pkg.equals(disabled)) return;
        Log.i(TAG, "disabling " + pkg + " for the rest of the run, as the install flow does");
        Shell.run("pm disable-user --user 0 " + pkg);
        disabled = pkg;
    }

    /** Enables the stock launcher again, if one was disabled; called once, when the run ends. */
    public static void enableAgain() throws IOException {
        if (disabled == null) return;
        Log.i(TAG, "enabling " + disabled + " again: the run is over");
        Shell.run("pm enable --user 0 " + disabled);
        disabled = null;
    }
}
