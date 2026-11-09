package com.itsluminous.verysmarttv.backup;

import android.content.Context;
import android.content.SharedPreferences;

import org.robolectric.RuntimeEnvironment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.grid.Order;

/**
 * A backup written by the fork's launcher restores here (DESIGN.md §7.4, §19 "Owner's existing TV migration"):
 * the setup tool moves the file over ADB and Restore from file reads it. What differs in such a file is the
 * {@code package} field, which restore never checks, the order file's header, which Order reads by pattern, and
 * the Widgets tick list, which Config migrates to the System bars toggle.
 */
@RunWith(RobolectricTestRunner.class)
public class BackupCompatTest {
    /** A backup as the fork's launcher wrote it, under a different package name, with its own order header. */
    private static JSONObject forkBackup() throws Exception {
        JSONObject settings = new JSONObject()
                .put("columns", new JSONObject().put("type", "int").put("value", 6))
                .put("hero_feed", new JSONObject().put("type", "int").put("value", 1))
                .put("widgets", new JSONObject().put("type", "set")
                        .put("value", new JSONArray(Arrays.asList("memory", "cpu", "storage"))))
                .put(Config.ADVANCED_EFFECTS_LEGACY_KEY, new JSONObject().put("type", "boolean").put("value", false))
                .put(Config.SETTINGS_REVISION_KEY, new JSONObject().put("type", "long").put("value", 40));
        JSONObject files = new JSONObject().put(Order.FILE_NAME, new JSONObject()
                .put("text", "previous-order 1\ndock com.netflix.ninja,com.stremio.one\ndocked 2\n"
                        + "com.netflix.ninja/.MainActivity\ncom.stremio.one/.MainActivity\norg.smarttube.stable/.Main\n"));
        JSONObject theme = new JSONObject().put(Config.FILE_NAME, new JSONObject()
                .put("text", "{\"columns\": 6, \"uniform_rows\": true, \"widgets\": [\"memory\", \"storage\"], \"show_stats\": true}"));
        return new JSONObject()
                .put("version", 1)
                .put("package", "com.example.previouslauncher")
                .put("exported_at", "2026-11-02T14:44:00Z")
                .put("prefs", new JSONObject().put(Config.SETTINGS_NAME, settings))
                .put("files", files)
                .put("theme", theme);
    }

    @Test
    public void aForkBackupRestoresWhateverItsPackageField() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        Backup.restore(ctx, forkBackup());

        SharedPreferences settings = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        assertEquals(6, settings.getInt("columns", -1));
        assertEquals(1, settings.getInt(Config.HERO_FEED_KEY, -1));
        assertFalse("the Advanced effects choice is dropped on restore", settings.contains(Config.ADVANCED_EFFECTS_LEGACY_KEY));
        assertTrue("the revision moved past the backup's, so Home rebuilds", settings.getLong(Config.SETTINGS_REVISION_KEY, 0) > 40);

        // The restored Widgets tick list is still a string set until Config reads it once; then it is the toggle.
        assertEquals(new HashSet<>(Arrays.asList("memory", "cpu", "storage")), settings.getStringSet(Config.WIDGETS_KEY, null));
        Config cfg = Config.load(ctx);
        assertTrue(cfg.widgets);
        assertEquals(1, settings.getInt(Config.WIDGETS_KEY, -1));
        assertEquals(6, cfg.columns);
        assertTrue("the theme's old chip list reads as on", cfg.themeWidgets);

        File order = new File(ctx.getFilesDir(), Order.FILE_NAME);
        assertTrue(order.isFile());
        Order decoded = Order.decode(new String(Files.readAllBytes(order.toPath()), StandardCharsets.UTF_8));
        assertNotNull("the fork's order header decodes", decoded);
        assertEquals(2, decoded.docked);
        assertEquals(3, decoded.keys.size());
    }

    @Test
    public void anotherVersionOrNoPrefsIsRefusedBeforeAnythingChanges() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences settings = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        settings.edit().clear().putInt("columns", 5).commit();
        for (JSONObject bad : new JSONObject[]{forkBackup().put("version", 2), new JSONObject().put("version", 1)}) {
            try {
                Backup.restore(ctx, bad);
                throw new AssertionError("restored " + bad);
            } catch (org.json.JSONException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("not a VerySmartTV settings backup"));
            }
        }
        assertEquals(5, settings.getInt("columns", -1));
    }

    @Test
    public void exportNamesThisPackageAndEveryStateFileRoundTrips() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        Backup.restore(ctx, forkBackup());
        JSONObject exported = Backup.export(ctx);
        assertEquals(ctx.getPackageName(), exported.getString("package"));
        assertEquals(1, exported.getInt("version"));
        assertTrue(exported.getJSONObject("files").has(Order.FILE_NAME));
        assertTrue(exported.getJSONObject("prefs").getJSONObject(Config.SETTINGS_NAME).has("columns"));
    }
}
