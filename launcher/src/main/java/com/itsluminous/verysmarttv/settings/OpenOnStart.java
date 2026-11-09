package com.itsluminous.verysmarttv.settings;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import com.itsluminous.verysmarttv.theme.Config;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** open_on_start_choice: the app Home opens once after each boot, picked in Launcher settings. */
public final class OpenOnStart {
    /** The picked package in SettingsChoices.SETTINGS_NAME; absent or empty means Off. */
    public static final String KEY = "open_on_start";
    /** The boot whose first Home has been handled, shared with boot_to_live_tv. */
    public static final String BOOT_PREFS = "boot";
    public static final String BOOT_KEY = "live_tv_boot";

    private OpenOnStart() {
    }

    /** Whether the first Home of this boot opens {@code pkg}: a known boot not handled yet, and an app that is not
     *  the launcher itself (which would only reopen Home). */
    public static boolean due(int boot, int handledBoot, @Nullable String pkg, @NonNull String self) {
        return boot >= 0 && boot != handledBoot && pkg != null && !pkg.isEmpty() && !pkg.equals(self);
    }

    /** open_on_start_on_wake needs the Open on start row, which is where the app is picked. */
    public static boolean onWake(boolean choice, boolean onWake) {
        return choice && onWake;
    }

    public static int boot(@NonNull Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    /** A pick takes effect from the next boot: the Home that follows the pick stays Home. */
    static void skipThisBoot(Context ctx) {
        ctx.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE).edit().putInt(BOOT_KEY, boot(ctx)).commit();
    }

    /** open_on_start_network_wait: the app opens once the network is validated, or when the wait is over. */
    public static boolean opensNow(boolean online, long now, long deadline) {
        return online || now >= deadline;
    }

    /** Android has reached the internet through the active network (ACCESS_NETWORK_STATE). */
    public static boolean online(@NonNull Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        Network n = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = n != null ? cm.getNetworkCapabilities(n) : null;
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /** The app's TV entry point, or its phone one; null when it is uninstalled or disabled, so Home stays. */
    public static @NonNull Intent intent(@NonNull PackageManager pm, @NonNull String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }
}
