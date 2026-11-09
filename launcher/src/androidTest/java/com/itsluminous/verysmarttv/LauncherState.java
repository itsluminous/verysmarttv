package com.itsluminous.verysmarttv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import com.itsluminous.verysmarttv.backup.Backup;
import com.itsluminous.verysmarttv.theme.Config;

/**
 * The launcher's package-private state, opened to the test helpers in {@code support/} (another package, so
 * they cannot call {@link Backup} and {@link Config} themselves). Nothing here has logic of its own: each method
 * is one call into the launcher, and the test that relies on it says what the launcher must do.
 */
public final class LauncherState {
    private LauncherState() {}

    /** The theme file name the {@code .theme} provider accepts for the config. */
    public static final String CONFIG_FILE = Config.FILE_NAME;

    /** The wallpaper file name the {@code .theme} provider accepts. */
    public static final String WALLPAPER_FILE = Config.WALLPAPER_NAME;

    /** The whole launcher state (preferences, state files, theme) as the Backup JSON. */
    public static JSONObject export(Context ctx) throws JSONException {
        return Backup.export(ctx);
    }

    /** Puts a {@link #export} back: preferences, state files and theme, and bumps the settings revision. */
    public static void restore(Context ctx, JSONObject snapshot) throws JSONException {
        Backup.restore(ctx, snapshot);
    }

    /** The folder "Back up settings" writes into, under the theme directory. */
    public static File backupDir(Context ctx) {
        return Backup.backupDir(ctx);
    }

    /** The backup files the Restore row lists, newest first. */
    public static List<File> backups(Context ctx) {
        return Backup.backups(ctx);
    }

    /** What "Back up settings" writes, as a file, so a test can stage a pushed backup the same way. */
    public static File saveBackup(Context ctx) throws IOException, JSONException {
        return Backup.saveToFile(ctx);
    }

    /** The on-TV choices Launcher settings saves (Config.SETTINGS_NAME). */
    public static SharedPreferences choices(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    /** Launcher settings' change count; Home rebuilds on resume when it moved (Config.SETTINGS_REVISION_KEY). */
    public static long settingsRevision(Context ctx) {
        return choices(ctx).getLong(Config.SETTINGS_REVISION_KEY, 0);
    }

    /** The Animations switch as Home would read it now: the saved choice over the theme's key. */
    public static boolean animations(Context ctx) {
        return Config.load(ctx).animations;
    }

    /** The columns Home would lay out now: the saved choice over the theme's key. */
    public static int columns(Context ctx) {
        return Config.load(ctx).columns;
    }
}
