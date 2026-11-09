package com.itsluminous.verysmarttv.theme;

import android.content.Context;
import android.content.SharedPreferences;

import org.robolectric.RuntimeEnvironment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.itsluminous.verysmarttv.settings.SettingsChoices;

/**
 * DESIGN.md §6: {@code widgets} became a boolean, the keys of dropped features are ignored with a log line, and the
 * Advanced effects choice an older launcher saved is dropped on load and on restore.
 */
@RunWith(RobolectricTestRunner.class)
public class ConfigMigrationTest {
    private SharedPreferences choices;

    @Before
    public void prefs() {
        Context ctx = RuntimeEnvironment.getApplication();
        choices = ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE);
        choices.edit().clear().commit();
    }

    @Test
    public void aThemesWidgetsIsABoolean() throws Exception {
        assertTrue(ThemeKeys.widgetsKey(new JSONObject("{\"widgets\": true}"), false));
        assertFalse(ThemeKeys.widgetsKey(new JSONObject("{\"widgets\": false}"), true));
        assertTrue(ThemeKeys.widgetsKey(new JSONObject("{}"), true));          // missing keeps the fallback
        assertFalse(ThemeKeys.widgetsKey(new JSONObject("{\"widgets\": \"yes\"}"), false));   // a wrong type too
    }

    @Test
    public void theOldChipListReadsAsOnWhenItNamesAnything() throws Exception {
        JSONObject all = new JSONObject().put("widgets", new JSONArray(Arrays.asList("memory", "cpu", "storage")));
        assertTrue(ThemeKeys.widgetsKey(all, false));
        JSONObject one = new JSONObject().put("widgets", new JSONArray(Collections.singletonList("storage")));
        assertTrue(ThemeKeys.widgetsKey(one, false));
        JSONObject none = new JSONObject().put("widgets", new JSONArray());
        assertFalse(ThemeKeys.widgetsKey(none, true));
    }

    @Test
    public void theSystemBarsToggleOverridesTheTheme() {
        assertTrue(SettingsChoices.chosenWidgets(1, false));
        assertFalse(SettingsChoices.chosenWidgets(0, true));
        assertTrue(SettingsChoices.chosenWidgets(-1, true));
        assertFalse(SettingsChoices.chosenWidgets(-1, false));
        assertFalse(SettingsChoices.chosenWidgets(choices, false));
        choices.edit().putInt(SettingsChoices.WIDGETS_KEY, 1).commit();
        assertTrue(SettingsChoices.chosenWidgets(choices, false));
    }

    @Test
    public void aSavedTickListMigratesToTheToggleOnce() {
        choices.edit().putStringSet(SettingsChoices.WIDGETS_KEY, new HashSet<>(Arrays.asList("memory", "storage"))).commit();
        assertTrue(SettingsChoices.chosenWidgets(choices, false));
        assertEquals(1, choices.getInt(SettingsChoices.WIDGETS_KEY, -1));   // written back as the int; the set is gone

        choices.edit().putStringSet(SettingsChoices.WIDGETS_KEY, new HashSet<>()).commit();   // a saved "none"
        assertFalse(SettingsChoices.chosenWidgets(choices, true));
        assertEquals(0, choices.getInt(SettingsChoices.WIDGETS_KEY, -1));
    }

    @Test
    public void droppedKeysAreNamedAndNothingElse() throws Exception {
        JSONObject theme = new JSONObject("{\"columns\": 6, \"festival\": \"diwali\", \"show_stats\": true, "
                + "\"lunar_date\": true, \"wallpaper_moods\": {}, \"race_calendar\": [], \"sky_intensity\": 60}");
        List<String> ignored = ThemeKeys.ignoredKeys(theme);
        assertEquals(Arrays.asList("festival", "race_calendar", "sky_intensity", "wallpaper_moods", "lunar_date", "show_stats"), ignored);
        assertTrue(ThemeKeys.ignoredKeys(new JSONObject("{\"columns\": 6, \"widgets\": true}")).isEmpty());
        for (String key : ThemeKeys.IGNORED_KEYS) assertFalse(key, key.equals("widgets") || key.equals("wallpaper") || key.equals("wallpaper_dim"));
    }

    @Test
    public void theAdvancedEffectsChoiceIsDropped() {
        choices.edit().putBoolean(SettingsChoices.ADVANCED_EFFECTS_LEGACY_KEY, false).putBoolean("animations", true).commit();
        SettingsChoices.dropLegacyChoices(choices);
        assertFalse(choices.contains(SettingsChoices.ADVANCED_EFFECTS_LEGACY_KEY));
        assertTrue(choices.getBoolean("animations", false));
    }

    @Test
    public void loadReadsTheDefaultsWithoutATheme() {
        Context ctx = RuntimeEnvironment.getApplication();
        Config cfg = Config.load(ctx);
        assertFalse(cfg.overlayApplied);
        assertFalse(cfg.widgets);
        assertEquals(cfg.themeWidgets, cfg.widgets);
        choices.edit().putInt(SettingsChoices.WIDGETS_KEY, 1).commit();
        assertTrue(Config.load(ctx).widgets);
    }
}
