package com.itsluminous.verysmarttv.backup;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * backup_restore: the launcher's settings, read-only, for anything on the TV that asks. Authority
 * {@code com.itsluminous.verysmarttv.backup}; the setup tool reads it over adb to move a backup between TVs.
 *
 * <pre>
 *   adb shell content read --uri content://com.itsluminous.verysmarttv.backup/settings   # the Backup.export JSON
 *   adb shell content read --uri content://com.itsluminous.verysmarttv.backup/backups    # the files in files/backup/
 * </pre>
 *
 * No permission guards it on purpose: it holds the app order, the hidden-apps list, the dock, the module-card
 * approvals and the theme, nothing secret, and `adb shell content read` must keep working. It writes
 * nothing: every mutating method answers "no", so a stray app on the LAN cannot reset the TV through it. The
 * export is written to the cache dir and handed over as a read-only descriptor (`content read` wants a file).
 */
public class BackupProvider extends ContentProvider {
    /** The fixed authority the manifest declares (DESIGN.md §7.2). */
    static final String AUTHORITY = "com.itsluminous.verysmarttv.backup";
    static final String SETTINGS = "settings";
    static final String BACKUPS = "backups";

    @Override
    public @NonNull ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (mode == null || mode.contains("w") || mode.contains("t")) throw new FileNotFoundException("read-only");
        List<String> path = uri.getPathSegments();
        if (path.size() != 1) throw new FileNotFoundException(String.valueOf(uri));
        String body;
        try {
            if (SETTINGS.equals(path.get(0))) body = Backup.export(getContext()).toString(2);
            else if (BACKUPS.equals(path.get(0))) body = backups().toString(2);
            else throw new FileNotFoundException(String.valueOf(uri));
        } catch (org.json.JSONException e) {
            throw new FileNotFoundException("export failed: " + e.getMessage());
        }
        File out = new File(getContext().getCacheDir(), "backup-" + path.get(0) + ".json");
        try {
            Backup.writeBytes(out, body.getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("cannot write " + out + ": " + e.getMessage());
        }
        return ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** The backup folder's files with whether this app can read each (a file adb pushed there, for instance). */
    private JSONObject backups() throws org.json.JSONException {
        JSONObject root = new JSONObject();
        File dir = Backup.backupDir(getContext());
        root.put("dir", dir == null ? JSONObject.NULL : dir.getPath());
        JSONArray files = new JSONArray();
        File[] listed = dir == null ? null : dir.listFiles();
        if (listed != null) {
            java.util.Arrays.sort(listed);
            for (File f : listed) {
                files.put(new JSONObject().put("name", f.getName()).put("size", f.length())
                        .put("readable", f.canRead()).put("modified", f.lastModified()));
            }
        }
        root.put("files", files);
        return root;
    }

    @Override public boolean onCreate() { return true; }

    @Override public @NonNull String getType(@NonNull Uri uri) { return "application/json"; }

    /** One row naming the package, for a caller that only wants to know the provider is there. */
    @Override
    public @NonNull Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] args, @Nullable String order) {
        MatrixCursor c = new MatrixCursor(new String[]{"package", "version"});
        c.addRow(new Object[]{getContext().getPackageName(), Backup.VERSION});
        return c;
    }

    @Override public @Nullable Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] args) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] args) { return 0; }
}
