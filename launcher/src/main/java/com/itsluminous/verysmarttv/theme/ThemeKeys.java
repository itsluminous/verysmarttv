package com.itsluminous.verysmarttv.theme;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.settings.SettingsChoices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * How a theme's config.json is read (DESIGN.md §8 rule 3, split from Config): the overlay file and the copy kept
 * for boots before storage mounts, the typed key readers with their fallbacks and ranges, the allowed values of
 * glow_style and glass_style, the dropped keys that are logged and ignored (ADR-008), and the file stamps the
 * settings layer compares to tell a theme push from an upgrade.
 */
public final class ThemeKeys {
    private ThemeKeys() {
    }

    private static final String TAG = "VerySmartTV.Config";

    /**
     * Keys of features this launcher dropped (ADR-008). A theme that still carries one loads, and the key is
     * ignored with one {@code Log.i} line, so an older config.json never fails and nobody wonders where the feature went.
     */
    public static final List<String> IGNORED_KEYS = Collections.unmodifiableList(Arrays.asList(
            "festival", "festival_until", "seasonal_skin", "greeting", "accent_color", "game_day", "race_calendar",
            "sky_intensity", "latitude", "longitude", "wallpaper_rotation_minutes", "wallpaper_order",
            "wallpaper_moods", "wallpaper_captions", "shamsi_date", "lunar_date", "lunar_utc_offset", "show_stats"));

    public static final String GLOW_RADIAL = "radial", GLOW_HORIZON = "horizon";

    public static final String GLASS_DEFAULT = "glass", GLASS_RIM = "rim", GLASS_HALO = "halo";

    /** A known glow_style, or radial (the default) for anything else. */
    public static @NonNull String glowStyle(@Nullable String value) {
        return value != null && GLOW_HORIZON.equalsIgnoreCase(value.trim()) ? GLOW_HORIZON : GLOW_RADIAL;
    }

    /** A known glass_style, or glass (the default) for anything else. */
    public static @NonNull String glassStyle(@Nullable String value) {
        if (value == null) return GLASS_DEFAULT;
        String v = value.trim();
        if (GLASS_RIM.equalsIgnoreCase(v)) return GLASS_RIM;
        return GLASS_HALO.equalsIgnoreCase(v) ? GLASS_HALO : GLASS_DEFAULT;
    }

    /**
     * A theme's {@code widgets}: a boolean as the Keys table has it, or the array of chip names older themes wrote
     * ({@code ["memory", "cpu", "storage"]}), which reads as on when it names anything and off when empty; anything
     * else keeps {@code fallback}.
     */
    public static boolean widgetsKey(@NonNull JSONObject o, boolean fallback) {
        Object v = o.opt("widgets");
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof JSONArray) {
            boolean on = ((JSONArray) v).length() > 0;
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "widgets is a boolean now; the chip list reads as " + (on ? "true" : "false"));
            return on;
        }
        return fallback;
    }

    /** One {@code Log.i} per {@link #IGNORED_KEYS} entry the theme still carries (ADR-008). */
    public static @NonNull List<String> ignoredKeys(@NonNull JSONObject o) {
        List<String> found = new ArrayList<>();
        for (String key : IGNORED_KEYS) if (o.has(key)) found.add(key);
        return found;
    }

    public static void logIgnoredKeys(@NonNull JSONObject o) {
        for (String key : ignoredKeys(o)) {
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "ignored key " + key + ": that feature is not in this launcher");
        }
    }

    /** A theme's hero_height value: 0 (auto) or 50 to 130; anything else keeps {@code fallback}. */
    public static int heroHeightKey(int value, int fallback) {
        return value == SettingsChoices.HERO_HEIGHT_AUTO || (value >= SettingsChoices.HERO_HEIGHT_MIN && value <= SettingsChoices.HERO_HEIGHT_MAX) ? value : fallback;
    }

    public static @NonNull String fileStamp(@Nullable File file) {
        return file != null && file.isFile() ? file.length() + "@" + file.lastModified() : "absent";
    }

    public static @NonNull String revisionToken(@Nullable File file) {
        if (file == null || !file.isFile() || file.length() > 64) return "absent";
        try (InputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int n = in.read(bytes);
            return n > 0 ? new String(bytes, 0, n, StandardCharsets.US_ASCII) : "absent";
        } catch (Exception ignored) {
            return "absent";
        }
    }

    /**
     * The overlay, or before external storage is mounted, the copy kept from the last time it was read. Home can
     * start before the card is mounted (seen at every boot on a Xiaomi MiTV-MTEQ0), and without the copy it comes
     * up with the built-in dock order. A missing file on mounted storage still means no overlay.
     */
    public static @Nullable JSONObject readOverlay(@NonNull Context ctx, @NonNull File file, boolean mounted) {
        File copy = new File(ctx.getFilesDir(), Config.FILE_NAME);
        if (!mounted) return read(copy);
        JSONObject json = read(file);
        if (json == null) {
            copy.delete();
        } else {
            try (FileOutputStream out = new FileOutputStream(copy)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Log.w(TAG, "could not keep a copy of " + file + ": " + e.getMessage());
            }
        }
        return json;
    }

    public static @Nullable JSONObject read(@Nullable File file) {
        if (file == null || !file.isFile()) return null;
        if (file.length() > 64 * 1024) {
            Log.w(TAG, "ignoring " + file + ": larger than 64 KB");
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[(int) file.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String text = new String(buf, 0, n, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);   // Windows editors add a BOM
            return new JSONObject(text);
        } catch (Exception e) {
            Log.w(TAG, "ignoring " + file + ": " + e.getMessage());
            return null;
        }
    }

    public static @NonNull String string(@NonNull JSONObject o, @NonNull String key, @NonNull String fallback) {
        Object v = o.opt(key);
        return v instanceof String ? ((String) v).trim() : fallback;
    }

    public static boolean bool(@NonNull JSONObject o, @NonNull String key, boolean fallback) {
        Object v = o.opt(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    public static int integer(@NonNull JSONObject o, @NonNull String key, int fallback, int min, int max) {
        Object v = o.opt(key);
        if (!(v instanceof Number)) return fallback;
        int i = ((Number) v).intValue();
        return i < min || i > max ? fallback : i;
    }

    public static int color(@NonNull JSONObject o, @NonNull String key, int fallback) {
        Object v = o.opt(key);
        if (!(v instanceof String)) return fallback;
        try {
            int c = Color.parseColor(((String) v).trim());
            return c | 0xFF000000;   // always opaque: the window behind is black anyway
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Positive keycodes only (vendor remotes use codes in the thousands); a list with anything else in it is ignored as a whole. */
    @NonNull
    public static int[] intArray(@NonNull JSONObject o, @NonNull String key, @NonNull int[] fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        int[] out = new int[a.length()];
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (!(item instanceof Number)) return fallback;
            out[i] = ((Number) item).intValue();
            if (out[i] < 1) return fallback;
        }
        return out;
    }

    /** "package=value" entries; an entry without both halves is skipped, and a later entry for a package wins. */
    static Map<String, String> packageMap(List<String> entries) {
        Map<String, String> out = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            String pkg = entry.substring(0, eq).trim(), value = entry.substring(eq + 1).trim();
            if (!pkg.isEmpty() && !value.isEmpty()) out.put(pkg, value);
        }
        return out;
    }

    public static @NonNull List<String> stringList(@NonNull JSONObject o, @NonNull String key, @NonNull List<String> fallback) {
        Object v = o.opt(key);
        if (!(v instanceof JSONArray)) return fallback;
        JSONArray a = (JSONArray) v;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            Object item = a.opt(i);
            if (item instanceof String && !((String) item).trim().isEmpty()) out.add(((String) item).trim());
        }
        return out;
    }
}
