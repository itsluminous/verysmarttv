package com.itsluminous.verysmarttv.settings;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.theme.ThemeKeys;

import java.io.File;
import java.util.Set;

/**
 * The choices Launcher settings saves on the TV and how they sit over the theme's keys (DESIGN.md §8 rule 3, §10,
 * split from Config): the preference file and its keys, the change count Home rebuilds on, which saved value wins
 * over which theme key and when a theme push clears them, and the migrations of choices older launchers wrote.
 */
public final class SettingsChoices {
    private static final String TAG = "VerySmartTV.Config";

    private SettingsChoices() {
    }

    public static final String SETTINGS_NAME = "launcher_settings";

    public static final String ROWS_BELOW_DOCK_KEY = "rows_below_dock";

    public static final String DOCK_OPACITY_KEY = "dock_opacity";

    public static final String CLOCK_FORMAT_KEY = "clock_format";

    /** The shelf height chosen in Launcher settings, percent (hero_height); unset means the theme's key decides. */
    public static final String HERO_HEIGHT_KEY = "hero_height";

    public static final int HERO_HEIGHT_MIN = 50, HERO_HEIGHT_MAX = 130;

    /**
     * hero_height 0, the default: the shelf is the layout's own 100 while the carousel is on (hero_feed, after the
     * Launcher settings toggle) and 80 without it, where the app's name alone does not need the room.
     */
    public static final int HERO_HEIGHT_AUTO = 0, HERO_HEIGHT_WITH_FEED = 100, HERO_HEIGHT_WITHOUT_FEED = 80;

    /**
     * The Trakt recommendations toggle in Launcher settings: 1 on, 0 off, unset means the theme's hero_feed decides.
     * Off leaves the launcher exactly as with "hero_feed": false (the shelf names the app, no provider is read).
     */
    public static final String HERO_FEED_KEY = "hero_feed";

    /**
     * The System bars toggle in Launcher settings: 1 on, 0 off, unset means the theme's widgets key decides. The
     * RAM and Storage bars themselves land in Phase 3 (DESIGN.md §9.1); until then the value is read and nothing is drawn.
     */
    public static final String WIDGETS_KEY = "widgets";

    /**
     * A choice older launchers saved from their Advanced effects row. Advanced effects now follow the Animations
     * setting and the theme's advanced_effects key, and this key is dropped wherever it turns up (load, restore):
     * both old Animations rows wrote it false on every toggle, which then outlived turning animations back on.
     */
    public static final String ADVANCED_EFFECTS_LEGACY_KEY = "advanced_effects";

    /**
     * Counts the changes made in Launcher settings. Home remembers the count it was built with and rebuilds on
     * resume when it differs, however the settings were left (see HomeActivity#onResume).
     */
    public static final String SETTINGS_REVISION_KEY = "settings_revision";

    /** True when Launcher settings changed something since Home was built at revision {@code seen}. */
    public static boolean settingsChanged(long seen, long current) {
        return seen != current;
    }

    public static final String THEME_SIGNATURE_KEY = "theme_signature";

    /**
     * Drops the choices no setting writes any more (ADVANCED_EFFECTS_LEGACY_KEY), so a value an older launcher
     * saved cannot turn the theme's effects off behind the Animations row. Backup.restore calls it too, so a
     * backup taken by that launcher restores clean.
     */
    public static void dropLegacyChoices(@NonNull SharedPreferences choices) {
        if (choices.contains(ADVANCED_EFFECTS_LEGACY_KEY)) choices.edit().remove(ADVANCED_EFFECTS_LEGACY_KEY).apply();
    }

    /**
     * The theme's signature: its config.json stamp and its revision token; a change means a theme push or reset and
     * clears the layout choices saved on the TV ({@link #syncThemeChoices}).
     */
    @NonNull
    public static String themeSignature(@Nullable File config, @Nullable File revision) {
        return ThemeKeys.fileStamp(config) + ":" + ThemeKeys.revisionToken(revision);
    }

    /** Config.load: the choices saved on the TV survive an upgrade but not a theme push (the signature moved). */
    public static void syncThemeChoices(@NonNull SharedPreferences choices, @Nullable File config, @Nullable File revision) {
        String token = ThemeKeys.revisionToken(revision);
        String signature = themeSignature(config, revision);
        String seen = choices.getString(THEME_SIGNATURE_KEY, null);
        if (seen == null) {
            // Existing preferences survive an app upgrade, unless the first launch follows an
            // explicit theme push or reset by the updated tools.
            SharedPreferences.Editor editor = choices.edit();
            if (!"absent".equals(token)) editor.remove("columns").remove("animations")
                    .remove(ROWS_BELOW_DOCK_KEY)
                    .remove(DOCK_OPACITY_KEY).remove(CLOCK_FORMAT_KEY).remove(HERO_HEIGHT_KEY).remove(HERO_FEED_KEY).remove(WIDGETS_KEY);
            editor.putString(THEME_SIGNATURE_KEY, signature).commit();
        } else if (!seen.equals(signature)) {
            choices.edit().remove("columns").remove("animations")
                    .remove(ROWS_BELOW_DOCK_KEY).remove(DOCK_OPACITY_KEY)
                    .remove(CLOCK_FORMAT_KEY).remove(HERO_HEIGHT_KEY).remove(HERO_FEED_KEY).remove(WIDGETS_KEY)
                    .putString(THEME_SIGNATURE_KEY, signature).commit();
        }
    }

    /** The dock band opacity chosen in Launcher settings, or the theme's when none (or an out-of-range one) is saved. */
    public static int chosenDockOpacity(int chosen, int theme) {
        return chosen >= 30 && chosen <= 100 ? chosen : theme;
    }

    /**
     * The shelf height: the Launcher settings choice (hero_height) when one in range is saved, else the theme's key
     * when it names a height, else the auto rule for the theme's 0: HERO_HEIGHT_WITH_FEED with the carousel on
     * ({@code feed}, the effective hero_feed after the toggle), HERO_HEIGHT_WITHOUT_FEED without it.
     */
    public static int chosenHeroHeight(int chosen, int theme, boolean feed) {
        if (chosen >= HERO_HEIGHT_MIN && chosen <= HERO_HEIGHT_MAX) return chosen;
        if (theme >= HERO_HEIGHT_MIN && theme <= HERO_HEIGHT_MAX) return theme;
        return autoHeroHeight(feed);
    }

    /** hero_height 0: 100 while the carousel is on, 80 without it. */
    public static int autoHeroHeight(boolean feed) {
        return feed ? HERO_HEIGHT_WITH_FEED : HERO_HEIGHT_WITHOUT_FEED;
    }

    /** The Trakt recommendations toggle (HERO_FEED_KEY): 1 is on, 0 off, anything else leaves the theme's hero_feed. */
    public static boolean chosenHeroFeed(int chosen, boolean theme) {
        return chosen == 1 || (chosen != 0 && theme);
    }

    /**
     * The bars in force: the System bars toggle (WIDGETS_KEY, 1 on, 0 off) when one is saved, else the theme's key.
     * A tick list an older launcher saved under the same key (a string set of chip names) is migrated on the way:
     * non-empty reads as on, empty as off, and the choice is written back as the int so the set is read only once.
     */
    public static boolean chosenWidgets(@NonNull SharedPreferences choices, boolean theme) {
        if (!choices.contains(WIDGETS_KEY)) return theme;
        try {
            return chosenWidgets(choices.getInt(WIDGETS_KEY, -1), theme);
        } catch (ClassCastException legacy) {
            Set<String> ticks = choices.getStringSet(WIDGETS_KEY, null);
            boolean on = ticks != null && !ticks.isEmpty();
            choices.edit().putInt(WIDGETS_KEY, on ? 1 : 0).apply();
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "migrated the widgets tick list to System bars " + (on ? "on" : "off"));
            return on;
        }
    }

    /** The System bars toggle: 1 is on, 0 off, anything else leaves the theme's widgets. */
    public static boolean chosenWidgets(int chosen, boolean theme) {
        return chosen == 1 || (chosen != 0 && theme);
    }
}
