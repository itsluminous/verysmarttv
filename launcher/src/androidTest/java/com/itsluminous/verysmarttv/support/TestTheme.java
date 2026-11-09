package com.itsluminous.verysmarttv.support;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.TreeSet;

/**
 * A theme {@code config.json} for one test: the few keys a case varies, over config.xml's defaults for the rest.
 * Every leanback app on the emulator except the dummy tiles (the test APK's six and Tile 7's package) goes into
 * {@code hidden_apps}, so the grid holds the tiles alone (and no module card can prompt: a hidden app's card is never discovered). The dock has the app
 * library's pill after the last row, the shelf names the focused app, and the wallpaper is off (one less bitmap on
 * a 1 GB image; the owner's wallpaper comes back with the HomeTestRule snapshot).
 */
public final class TestTheme {
    private int columns = 4;
    private int rowsBelowDock;
    private boolean uniformRows;
    private boolean heroFeed;
    private boolean settingsPanel;
    private boolean scorePill;
    private boolean widgets;

    public TestTheme columns(int n) {
        columns = n;
        return this;
    }

    /** Grid rows kept visible under the dock (rows_below_dock). */
    public TestTheme rowsBelowDock(int n) {
        rowsBelowDock = n;
        return this;
    }

    /** uniform_rows: no dock, every row scrolls to the shelf's line. */
    public TestTheme uniformRows() {
        uniformRows = true;
        return this;
    }

    /** hero_feed on, with the longest rotation interval so no item advances under a test. */
    public TestTheme heroFeed() {
        heroFeed = true;
        return this;
    }

    /** settings_panel: Launcher settings as the side panel. */
    public TestTheme settingsPanel() {
        settingsPanel = true;
        return this;
    }

    /** score_pill: the live-score pill under the bar (DESIGN.md §9.2). */
    public TestTheme scorePill() {
        scorePill = true;
        return this;
    }

    /** widgets: the RAM and Storage bars beside the date (DESIGN.md §9.1). */
    public TestTheme widgets() {
        widgets = true;
        return this;
    }

    /** The config.json text, for the launcher at {@code target}. */
    public String json(Context target) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("columns", columns);
        json.put("rows_below_dock", rowsBelowDock);
        json.put("uniform_rows", uniformRows);
        json.put("hero_feed", heroFeed);
        json.put("hero_feed_interval", 120);
        json.put("settings_panel", settingsPanel);
        json.put("score_pill", scorePill);
        json.put("widgets", widgets);
        json.put("app_library", true);
        json.put("library_button", true);
        json.put("show_library_pill", true);
        json.put("show_greeting", false);
        json.put("wallpaper", false);
        json.put("dock_apps", new JSONArray().put(DummyTiles.packageName()));
        json.put("hidden_apps", new JSONArray(otherLeanbackPackages(target)));
        return json.toString(2);
    }

    /** Every package with a leanback entry, the launcher and the two dummy-tile packages left out, sorted. */
    private static TreeSet<String> otherLeanbackPackages(Context target) {
        TreeSet<String> hidden = new TreeSet<>();
        Intent leanback = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : target.getPackageManager().queryIntentActivities(leanback, 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (!pkg.equals(target.getPackageName()) && !pkg.equals(DummyTiles.packageName()) && !pkg.equals(DummyTiles.SEVENTH)) hidden.add(pkg);
        }
        return hidden;
    }
}
