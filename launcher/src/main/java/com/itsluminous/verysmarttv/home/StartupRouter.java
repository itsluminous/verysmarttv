package com.itsluminous.verysmarttv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.settings.LauncherSettingsActivity;
import com.itsluminous.verysmarttv.settings.OpenOnStart;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * What opens instead of, or over, Home when it starts (DESIGN.md §8, moved from the activity verbatim): the Live TV
 * app or the Open on start app once per boot and on wake, with the optional wait for the network; the intents other
 * parts of the launcher send Home (open the library, open Launcher settings); and the Launcher settings result,
 * whose change count decides whether Home rebuilds and whether reorder mode starts.
 */
public final class StartupRouter {
    private static final int LAUNCHER_SETTINGS_REQUEST = 42;

    // Not private: the two anonymous receivers below read them, and a synthetic accessor per read is what lint's
    // SyntheticAccessor flags (the launcher runs on 1 GB TVs; the fork baselined these, this file fixes them).
    final HomeActivity home;
    /** Launcher settings' change count this Home was built with (see onResume). */
    private long settingsRevision;
    /** boot_to_live_tv: open the Live TV app on the first Home of each boot. */
    private final boolean bootToLiveTv;
    /** open_on_start_choice: the app picked in Launcher settings may open on the first Home of each boot. */
    private final boolean openOnStartChoice;
    /** open_on_start_network_wait, in ms; the boot's app while it waits for the network, and until when. */
    private final long openOnStartWaitMs;
    /** open_on_start_on_wake: wakeReceiver is registered for this activity's life. */
    private final boolean openOnStartOnWake;
    private final String liveTvPkg;
    volatile Intent startPending;
    long startDeadline;
    /** Opens the boot's app once the network is validated (networkCallback posts this) or the wait is over. */
    private final Runnable openWhenOnline = new Runnable() {
        @Override
        public void run() {
            if (startPending == null) return;
            if (!OpenOnStart.opensNow(OpenOnStart.online(home), SystemClock.elapsedRealtime(),
                    startDeadline)) return;
            home.handler().removeCallbacks(this);   // the deadline's run, when the network came first
            Intent i = startPending;
            startPending = null;
            home.open(i);
        }
    };
    private final BroadcastReceiver wakeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            openStartAppOnWake();
        }
    };
    private boolean settingsRecreationPending;

    public StartupRouter(@NonNull HomeActivity home, @NonNull Config cfg) {
        this.home = home;
        settingsRevision = settingsRevision();
        bootToLiveTv = cfg.bootToLiveTv;
        openOnStartChoice = cfg.openOnStartChoice;
        openOnStartWaitMs = cfg.openOnStartNetworkWait * 1000L;
        openOnStartOnWake = OpenOnStart.onWake(cfg.openOnStartChoice, cfg.openOnStartOnWake);
        liveTvPkg = cfg.liveTvPackage;
    }

    /** onCreate: the wake receiver, for the activity's whole life. */
    public void onCreate() {
        if (openOnStartOnWake) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                home.registerReceiver(wakeReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED);
            } else {
                home.registerReceiver(wakeReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
            }
        }
    }

    /** onPause: leaving Home before the network came back, the boot's app stays closed. */
    public void onPause() {
        home.handler().removeCallbacks(openWhenOnline);
        startPending = null;
    }

    public void onDestroy() {
        if (openOnStartOnWake) home.unregisterReceiver(wakeReceiver);
    }

    /** open_on_start_network_wait: the boot's app opens on the first validated network (networkCallback). */
    public void onNetworkValidated() {
        if (startPending != null) home.handler().post(openWhenOnline);
    }

    /** onResume: something changed in Launcher settings since this Home was built. */
    public boolean settingsChanged() {
        return Config.settingsChanged(settingsRevision, settingsRevision());
    }

    /** Opens Launcher settings over Home; Home rebuilds when they close with a change (onResume, onActivityResult). */
    public void openLauncherSettings() {
        home.startActivityForResult(new Intent(home, LauncherSettingsActivity.class), LAUNCHER_SETTINGS_REQUEST);
    }

    /** The two intents Home answers besides HOME: Launcher settings, then the App library. */
    public boolean openIfAsked(@Nullable Intent intent) {
        return openLauncherSettingsIfAsked(intent) || openLibraryIfAsked(intent);
    }

    /**
     * ACTION_APP_LIBRARY, sent by the "library" action word: open the App library once Home is on screen. The intent
     * is replaced so a later recreate() does not open it again. With app_library off there is no library, so the
     * press only brings Home forward.
     */
    private boolean openLibraryIfAsked(Intent intent) {
        if (intent == null || !HomeActivity.ACTION_APP_LIBRARY.equals(intent.getAction())) return false;
        home.setIntent(new Intent(Intent.ACTION_MAIN));
        if (!home.state().appLibraryEnabled) return true;
        home.getWindow().getDecorView().post(() -> {
            if (!home.isFinishing() && !home.isDestroyed()) home.onLibraryRequested();
        });
        return true;
    }

    private boolean openLauncherSettingsIfAsked(Intent intent) {
        if (intent == null || !HomeActivity.ACTION_LAUNCHER_SETTINGS.equals(intent.getAction())) return false;
        home.setIntent(new Intent(Intent.ACTION_MAIN));
        openLauncherSettings();
        return true;
    }

    /** Launcher settings' change count (Config.SETTINGS_REVISION_KEY); onResume rebuilds Home when it moved. */
    private long settingsRevision() {
        return home.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getLong(Config.SETTINGS_REVISION_KEY, 0);
    }

    /**
     * Only "Reorder tiles" in Launcher settings still answers with a result: Home rebuilds (the saved choices are
     * reloaded) and then enters reorder mode. Every other change is picked up in onResume through the change count.
     */
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == LAUNCHER_SETTINGS_REQUEST && resultCode == android.app.Activity.RESULT_OK
                && data != null && data.getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) {
            home.getIntent().putExtra(LauncherSettingsActivity.EXTRA_REORDER, true);
            if (!Config.settingsChanged(settingsRevision, settingsRevision())) {
                // Nothing was changed before Reorder was chosen: keep this activity and its tiles, and start the
                // reorder when the window has focus again (onWindowFocusChanged). A recreate() here blanked Home
                // for a moment on every use.
                return;
            }
            // Something changed too: rebuild with it first. The old activity must not consume the pending action,
            // or rebuild a second time in onResume.
            settingsRecreationPending = true;
            settingsRevision = settingsRevision();
            home.recreate();
        }
    }

    public void startPendingSettingsReorder() {
        if (settingsRecreationPending || !home.state().tilesReady || !home.hasWindowFocus() || home.isFinishing()
                || !home.getIntent().getBooleanExtra(LauncherSettingsActivity.EXTRA_REORDER, false)) return;
        home.getIntent().removeExtra(LauncherSettingsActivity.EXTRA_REORDER);
        home.toggleReorder();
    }

    /** boot_to_live_tv, or the Open on start app: the first time Home comes up after the TV starts, open that app
     *  instead. Once per boot, keyed on Settings.Global.BOOT_COUNT and committed to disk before the app starts: on a
     *  low-memory TV the app can push the launcher out of memory, and an apply() still in flight was lost then, so
     *  every Home press opened the app again. */
    public boolean openLiveTvAfterBoot() {
        String app = openOnStartChoice ? home.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE)
                .getString(OpenOnStart.KEY, "") : "";
        if (!bootToLiveTv && app.isEmpty()) return false;
        int boot = OpenOnStart.boot(home);
        SharedPreferences prefs = home.getSharedPreferences(OpenOnStart.BOOT_PREFS, Context.MODE_PRIVATE);
        String target = bootToLiveTv ? liveTvPkg : app;
        if (!OpenOnStart.due(boot, prefs.getInt(OpenOnStart.BOOT_KEY, -1), target, home.getPackageName())) return false;
        if (!prefs.edit().putInt(OpenOnStart.BOOT_KEY, boot).commit()) return false;
        Intent i = bootToLiveTv ? home.inputs().liveTvIntent() : OpenOnStart.intent(home.getPackageManager(), app);
        if (i == null) return false;
        return openWhenReady(i);
    }

    /** open_on_start_on_wake: the remote's power button is a light sleep on some sets, with no boot for
     *  openLiveTvAfterBoot to catch. */
    void openStartAppOnWake() {
        String app = home.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(OpenOnStart.KEY, "");
        if (app.isEmpty() || app.equals(home.getPackageName())) return;
        Intent i = OpenOnStart.intent(home.getPackageManager(), app);
        if (i != null) openWhenReady(i);
    }

    private boolean openWhenReady(Intent i) {
        // open_on_start_network_wait: Home stays on screen until the network is back or the wait is over.
        if (openOnStartWaitMs > 0 && !OpenOnStart.online(home)) {
            startPending = i;
            startDeadline = SystemClock.elapsedRealtime() + openOnStartWaitMs;
            home.handler().postDelayed(openWhenOnline, openOnStartWaitMs);
            // Validated between the check above and startPending: networkCallback may have skipped it.
            if (OpenOnStart.online(home)) home.handler().post(openWhenOnline);
            return false;
        }
        home.open(i);
        return true;
    }
}
