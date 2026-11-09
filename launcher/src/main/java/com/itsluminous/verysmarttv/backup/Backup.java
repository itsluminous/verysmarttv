package com.itsluminous.verysmarttv.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import com.itsluminous.verysmarttv.cards.Cards;
import com.itsluminous.verysmarttv.theme.Config;
import com.itsluminous.verysmarttv.grid.Order;
import com.itsluminous.verysmarttv.theme.ThemeFiles;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.itsluminous.verysmarttv.settings.SettingsChoices;
import com.itsluminous.verysmarttv.theme.ThemeKeys;

/**
 * backup_restore: everything the owner set on this TV, as one JSON document, and the way back from it.
 *
 * The document carries every SharedPreferences file of the app (name, then key to typed value), the state files in
 * the private files dir (order.txt with the tile order, cards.json with the module-card approvals, the app library's
 * two lists), and the theme in {@link Config#dir} (config.json, wallpaper.jpg, theme_revision). Text is inline; a picture is base64 when it is under {@link #MAX_INLINE_BYTES}, else it is listed by
 * name with a note and left out. Shape:
 *
 * <pre>
 * {"version": 1, "package": "com.itsluminous.verysmarttv", "exported_at": "2026-11-02T14:44:00Z",
 *  "prefs": {"launcher_settings": {"columns": {"type": "int", "value": 6}}},
 *  "files": {"order.txt": {"text": "verysmarttv-order 1\n..."}},
 *  "theme": {"config.json": {"text": "{...}"}, "wallpaper.jpg": {"base64": "..."}}}
 * </pre>
 *
 * {@link #restore} checks only {@code version} and {@code prefs}, never {@code package}: a backup the fork's launcher
 * wrote restores here (its order.txt header is read by Order, its Widgets tick list migrated by Config). It
 * replaces each of the three sections whole: a preferences file is cleared before its keys are put
 * back with their types, a state or theme file the backup does not have is removed (one the export skipped as too
 * large is kept), and the ones it has are written. The theme signature is then taken over so the restored layout
 * choices survive the file change, and the settings revision moves so Home rebuilds when it resumes.
 *
 * {@link BackupProvider} serves {@link #export} read-only at content://com.itsluminous.verysmarttv.backup/settings, which is
 * how `adb shell content read` reads it.
 */
public final class Backup {
    private static final String TAG = "VerySmartTV.Backup";
    static final int VERSION = 1;
    /** A binary over this size is named in the backup but not carried: a JSON of many megabytes helps nobody. */
    static final int MAX_INLINE_BYTES = 2 * 1024 * 1024;
    /** A state file over this size is not one this launcher wrote (Order and Cards both stop at 64 KB). */
    private static final int MAX_STATE_BYTES = 64 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    /** The private files dir's own copy of the theme (ThemeKeys.readOverlay); it is derived from the theme section. */
    private static final String THEME_COPY = Config.FILE_NAME;
    public static final String BACKUP_DIR = "backup";

    private Backup() {}

    // ---- export ----------------------------------------------------------------------------------

    public static @NonNull JSONObject export(@NonNull Context ctx) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        root.put("package", ctx.getPackageName());
        root.put("version_name", versionName(ctx));
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        root.put("exported_at", iso.format(new Date()));
        root.put("prefs", exportPrefs(ctx));
        root.put("files", exportFiles(ctx.getFilesDir(), stateFiles(ctx.getFilesDir())));
        root.put("theme", exportFiles(Config.dir(ctx), themeFiles(Config.dir(ctx))));
        return root;
    }

    private static String versionName(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    /** Every shared_prefs/*.xml, by name, so a preferences file added later is carried without a change here. */
    static List<String> prefsNames(Context ctx) {
        List<String> names = new ArrayList<>();
        File[] files = new File(ctx.getDataDir(), "shared_prefs").listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (f.isFile() && n.endsWith(".xml") && SAFE_NAME.matcher(n).matches()) names.add(n.substring(0, n.length() - 4));
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    private static JSONObject exportPrefs(Context ctx) throws JSONException {
        JSONObject prefs = new JSONObject();
        for (String name : prefsNames(ctx)) {
            Map<String, ?> all = new TreeMap<>(ctx.getSharedPreferences(name, Context.MODE_PRIVATE).getAll());
            JSONObject file = new JSONObject();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                JSONObject typed = typed(e.getValue());
                if (typed != null) file.put(e.getKey(), typed);
            }
            prefs.put(name, file);
        }
        return prefs;
    }

    private static JSONObject typed(Object v) throws JSONException {
        JSONObject t = new JSONObject();
        if (v instanceof String) return t.put("type", "string").put("value", v);
        if (v instanceof Integer) return t.put("type", "int").put("value", v);
        if (v instanceof Long) return t.put("type", "long").put("value", v);
        if (v instanceof Float) return t.put("type", "float").put("value", ((Float) v).doubleValue());
        if (v instanceof Boolean) return t.put("type", "boolean").put("value", v);
        if (v instanceof Set) {
            JSONArray a = new JSONArray();
            for (Object o : new TreeSet<>((Set<?>) v)) a.put(String.valueOf(o));
            return t.put("type", "set").put("value", a);
        }
        return null;
    }

    /**
     * The state files in the private files dir: plain names, no AtomicFile leftovers (.bak, .new), no quarantined
     * cards.json.bad, and not the theme copy, which Config remakes from the theme section.
     */
    static List<String> stateFiles(File dir) {
        List<String> names = new ArrayList<>();
        File[] files = dir == null ? null : dir.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (!f.isFile() || !SAFE_NAME.matcher(n).matches() || n.equals(THEME_COPY)
                        || n.endsWith(".bak") || n.endsWith(".new") || n.endsWith(".bad") || f.length() > MAX_STATE_BYTES) continue;
                names.add(n);
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    /** The theme files present, as the paths ThemeFiles allows: config.json, wallpaper.jpg, theme_revision. */
    static List<String> themeFiles(File dir) {
        List<String> names = new ArrayList<>();
        if (dir == null) return names;
        for (String n : new String[]{Config.FILE_NAME, Config.WALLPAPER_NAME, "theme_revision"}) {
            if (new File(dir, n).isFile()) names.add(n);
        }
        return names;
    }

    private static JSONObject exportFiles(File dir, List<String> names) throws JSONException {
        JSONObject out = new JSONObject();
        for (String name : names) {
            File f = new File(dir, name);
            JSONObject entry = new JSONObject();
            if (f.length() > MAX_INLINE_BYTES) {
                entry.put("skipped", "larger than " + (MAX_INLINE_BYTES / (1024 * 1024)) + " MB; push the theme again");
                entry.put("size", f.length());
            } else {
                byte[] bytes;
                try {
                    bytes = readAll(f);
                } catch (IOException e) {
                    entry.put("skipped", "could not be read: " + e.getMessage());
                    out.put(name, entry);
                    continue;
                }
                String text = asText(bytes);
                if (text != null) entry.put("text", text);
                else entry.put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP));
            }
            out.put(name, entry);
        }
        return out;
    }

    /** The bytes as a string when they are UTF-8 text with no NUL (a config, an order, a caption), else null. */
    private static String asText(byte[] bytes) {
        for (byte b : bytes) if (b == 0) return null;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) bytes.write(buf, 0, n);
            return bytes.toByteArray();
        }
    }

    // ---- restore ---------------------------------------------------------------------------------

    /** The folder "Back up settings" writes into; adb can push a file there and the launcher reads it. */
    public static @Nullable File backupDir(@NonNull Context ctx) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(dir, BACKUP_DIR);
    }

    /** Writes the export into {@link #backupDir} as verysmarttv-&lt;date&gt;.json and returns the file. */
    public static @NonNull File saveToFile(@NonNull Context ctx) throws IOException, JSONException {
        File dir = backupDir(ctx);
        if (dir == null || (!dir.isDirectory() && !dir.mkdirs())) throw new IOException("no external files dir");
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date());
        File out = new File(dir, "verysmarttv-" + stamp + ".json");
        for (int i = 2; out.exists(); i++) out = new File(dir, "verysmarttv-" + stamp + "-" + i + ".json");
        writeBytes(out, export(ctx).toString(2).getBytes(StandardCharsets.UTF_8));
        return out;
    }

    /** The backups in {@link #backupDir}, newest first; a file adb pushed there counts when the app can read it. */
    public static @Nullable List<File> backups(@NonNull Context ctx) {
        List<File> files = new ArrayList<>();
        File dir = backupDir(ctx);
        File[] listed = dir == null ? null : dir.listFiles();
        if (listed != null) for (File f : listed) if (f.isFile() && f.getName().endsWith(".json") && f.canRead()) files.add(f);
        files.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    public static @NonNull JSONObject read(@NonNull File f) throws IOException, JSONException {
        if (f.length() > 16L * 1024 * 1024) throw new IOException(f.getName() + " is larger than 16 MB");
        return new JSONObject(new String(readAll(f), StandardCharsets.UTF_8));
    }

    /**
     * Puts the document back, all three sections, then arranges for Home to rebuild on its next resume. Throws on
     * a document this launcher does not understand before anything is changed; a file that cannot be written is
     * logged and skipped (the preferences, which carry the layout, are already in).
     */
    public static void restore(@NonNull Context ctx, @NonNull JSONObject doc) throws JSONException {
        if (doc.optInt("version", -1) != VERSION || !doc.has("prefs")) {
            throw new JSONException("not a VerySmartTV settings backup (version " + doc.opt("version") + ")");
        }
        SharedPreferences settings = ctx.getSharedPreferences(SettingsChoices.SETTINGS_NAME, Context.MODE_PRIVATE);
        long revisionBefore = settings.getLong(SettingsChoices.SETTINGS_REVISION_KEY, 0);

        JSONObject prefs = doc.getJSONObject("prefs");
        for (Iterator<String> it = prefs.keys(); it.hasNext(); ) {
            String name = it.next();
            if (!SAFE_NAME.matcher(name).matches()) continue;
            restorePrefs(ctx.getSharedPreferences(name, Context.MODE_PRIVATE), prefs.getJSONObject(name));
        }
        // A backup from a launcher that still had an Advanced effects row carries its choice; it means nothing now.
        SettingsChoices.dropLegacyChoices(settings);

        File filesDir = ctx.getFilesDir();
        restoreFiles(filesDir, stateFiles(filesDir), doc.optJSONObject("files"), false);
        File themeDir = Config.dir(ctx);
        restoreFiles(themeDir, themeFiles(themeDir), doc.optJSONObject("theme"), true);
        // The private copy Config reads before external storage is mounted follows the theme.
        File copy = new File(filesDir, THEME_COPY);
        File theme = themeDir == null ? null : new File(themeDir, Config.FILE_NAME);
        try {
            if (theme != null && theme.isFile()) writeBytes(copy, readAll(theme));
            else copy.delete();
        } catch (IOException e) {
            Log.w(TAG, "theme copy: " + e.getMessage());
        }
        // The theme files were just written, so their stamp is new: adopt it, or Config would read the write as a
        // theme push and clear the layout choices that came back with the preferences.
        Config.adoptThemeFiles(ctx);
        // Home rebuilds in onResume when the revision moved; past both what it saw and what the backup held.
        long restored = settings.getLong(SettingsChoices.SETTINGS_REVISION_KEY, 0);
        settings.edit().putLong(SettingsChoices.SETTINGS_REVISION_KEY, Math.max(revisionBefore, restored) + 1).commit();
    }

    private static void restorePrefs(SharedPreferences prefs, JSONObject keys) throws JSONException {
        SharedPreferences.Editor e = prefs.edit().clear();
        for (Iterator<String> it = keys.keys(); it.hasNext(); ) {
            String key = it.next();
            JSONObject t = keys.getJSONObject(key);
            String type = t.getString("type");
            switch (type) {
                case "string": e.putString(key, t.getString("value")); break;
                case "int": e.putInt(key, t.getInt("value")); break;
                case "long": e.putLong(key, t.getLong("value")); break;
                case "float": e.putFloat(key, (float) t.getDouble("value")); break;
                case "boolean": e.putBoolean(key, t.getBoolean("value")); break;
                case "set": {
                    JSONArray a = t.getJSONArray("value");
                    Set<String> set = new HashSet<>();
                    for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                    e.putStringSet(key, set);
                    break;
                }
                default: throw new JSONException("unknown preference type " + type + " for " + key);
            }
        }
        e.commit();
    }

    /**
     * Makes {@code dir} hold exactly the section's files: the present ones not in it go (unless the export skipped
     * them, so they are still wanted), the ones in it are written. {@code theme} restricts names to ThemeFiles'.
     */
    private static void restoreFiles(File dir, List<String> present, JSONObject section, boolean theme) {
        if (dir == null) return;
        if (section == null) section = new JSONObject();
        for (String name : present) {
            JSONObject entry = section.optJSONObject(name);
            if (entry != null && entry.has("skipped")) continue;
            if (entry == null) remove(new File(dir, name));
        }
        for (Iterator<String> it = section.keys(); it.hasNext(); ) {
            String name = it.next();
            String path = theme ? ThemeFiles.path(java.util.Arrays.asList(name.split("/")))
                    : SAFE_NAME.matcher(name).matches() && !name.equals(THEME_COPY) ? name : null;
            if (path == null) { Log.w(TAG, "refusing to write " + name); continue; }
            JSONObject entry = section.optJSONObject(name);
            if (entry == null || entry.has("skipped")) continue;
            byte[] bytes;
            if (entry.has("text")) bytes = entry.optString("text").getBytes(StandardCharsets.UTF_8);
            else if (entry.has("base64")) {
                try { bytes = Base64.decode(entry.optString("base64"), Base64.DEFAULT); }
                catch (IllegalArgumentException e) { Log.w(TAG, name + ": bad base64"); continue; }
            } else continue;
            File f = new File(dir, path);
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) { Log.w(TAG, "cannot make " + parent); continue; }
            try {
                remove(f);
                writeBytes(f, bytes);
            } catch (IOException e) {
                Log.w(TAG, "cannot write " + f + ": " + e.getMessage());
            }
        }
    }

    /** The file and the AtomicFile siblings that would shadow it (.bak is read in preference to the base file). */
    private static void remove(File f) {
        f.delete();
        new File(f.getPath() + ".bak").delete();
        new File(f.getPath() + ".new").delete();
    }

    static void writeBytes(File f, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
    }
}
