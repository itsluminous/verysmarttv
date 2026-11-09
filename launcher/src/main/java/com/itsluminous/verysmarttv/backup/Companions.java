package com.itsluminous.verysmarttv.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * The {@code companions} section of the Backup document (DESIGN.md §12.2, ADR-032): what each companion keeps on the
 * TV that the owner would not want to re-enter, carried through its provider so the launcher never parses it.
 *
 * <pre>
 * "companions": {"livescore": {"json": "{...the follow list as the companion stores it...}"}}
 * </pre>
 *
 * The live-score companion answers {@link ScoreContract#BACKUP} with one {@code json} row and takes it back through
 * {@code call(}{@link ScoreContract#METHOD_RESTORE}{@code )}, which its {@code signature} write permission limits
 * to the launcher. Trakt has no section: its state is OAuth tokens, never backed up. When the companion is not
 * installed, or refuses (another signing key), the section is left out of the export and skipped on restore, with one
 * log line each; the launcher's own sections are unaffected either way.
 */
public final class Companions {
    private static final String TAG = "VerySmartTV.Backup";
    /** The section's key in the document. */
    public static final String KEY = "companions";
    /** The live-score companion's entry under {@link #KEY}. */
    public static final String LIVESCORE = "livescore";

    private Companions() {
    }

    /** The section for the export, or null when no companion is installed. */
    @Nullable
    public static JSONObject export(@NonNull Context ctx) throws JSONException {
        String scores = readScores(ctx.getContentResolver());
        if (scores == null) return null;
        JSONObject section = new JSONObject();
        section.put(LIVESCORE, new JSONObject().put(ScoreContract.JSON, scores));
        return section;
    }

    /** The companion's follow list as the JSON it stores, or null when its provider is absent, refuses or answers nothing. */
    @Nullable
    static String readScores(@NonNull ContentResolver cr) {
        try (Cursor c = cr.query(ScoreContract.BACKUP, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) {
                if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "livescore: no provider; the companions section is left out");
                return null;
            }
            int i = c.getColumnIndex(ScoreContract.JSON);
            if (i < 0 || c.isNull(i)) return null;
            return c.getString(i);
        } catch (RuntimeException e) {
            // SecurityException (another signing key) or a provider that failed: the section is left out.
            Log.w(TAG, "livescore backup not read: " + e.getMessage());
            return null;
        }
    }

    /**
     * Hands each companion its entry from {@code section} (the document's {@link #KEY}, may be null). Returns true when
     * the live-score companion took its follow list, false when the section has none for it, the companion is absent,
     * or it refused.
     */
    public static boolean restore(@NonNull Context ctx, @Nullable JSONObject section) {
        JSONObject livescore = section == null ? null : section.optJSONObject(LIVESCORE);
        String json = livescore == null ? null : livescore.optString(ScoreContract.JSON, null);
        if (json == null) return false;
        return restoreScores(ctx.getContentResolver(), json);
    }

    static boolean restoreScores(@NonNull ContentResolver cr, @NonNull String json) {
        Bundle extras = new Bundle();
        extras.putString(ScoreContract.EXTRA_JSON, json);
        try {
            Bundle result = cr.call(ScoreContract.BACKUP, ScoreContract.METHOD_RESTORE, null, extras);
            if (result != null && result.getBoolean(ScoreContract.RESULT_OK, false)) return true;
            Log.w(TAG, "livescore restore refused: " + (result == null ? "no answer" : result.getString(ScoreContract.RESULT_ERROR)));
        } catch (RuntimeException e) {
            // IllegalArgumentException when no provider answers the authority; SecurityException for another key.
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "livescore not restored: " + e.getMessage());
        }
        return false;
    }
}
