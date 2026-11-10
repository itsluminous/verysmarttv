package com.itsluminous.verysmarttv.testkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An in-process stand-in for the live-score companion's provider, speaking {@link ScoreContract}.
 *
 * <p>Tests script it through the static setters, then register it under
 * {@link ScoreContract#AUTHORITY}. Until seeded, {@code /pill} answers one {@link ScoreContract#STATE_NONE} row,
 * {@code /events} is empty and {@code /backup} holds {@code {}}: the state of a fresh companion with
 * nothing followed in the window. {@link #METHOD_RESTORE restore} stores the JSON it is given and
 * notifies {@code /pill}, as the real provider does. Call {@link #reset()} in {@code @After}. Open for the on-device
 * stand-in ({@code :testkit:score}), which seeds it on create and adds the fixture-switching calls.
 */
public class FakeScoreProvider extends ContentProvider {
    private static final List<Object[]> PILL = new ArrayList<>(Collections.singletonList(none()));
    private static final List<Object[]> EVENTS = new ArrayList<>();
    private static String backupJson = "{}";
    private static final List<String> RESTORED = new ArrayList<>();
    private static int pillQueries;

    /** The one {@code /pill} row, following {@link ScoreContract#PILL_COLUMNS}. */
    public static void seedPill(@NonNull Object[] row) {
        seedPills(Collections.singletonList(row));
    }

    /** The {@code /pill} rows in cursor order, each following {@link ScoreContract#PILL_COLUMNS}; the companion's stacked answer. */
    public static void seedPills(@NonNull List<Object[]> rows) {
        for (Object[] row : rows) {
            if (row.length != ScoreContract.PILL_COLUMNS.length) {
                throw new IllegalArgumentException("row has " + row.length + " values, contract has " + ScoreContract.PILL_COLUMNS.length);
            }
        }
        PILL.clear();
        PILL.addAll(rows);
    }

    /** A {@code /pill} row in the given state with the given kicker and line, {@code event_id} and sport filled in, fresh. */
    @NonNull
    public static Object[] pill(@NonNull String state, @NonNull String kicker, @NonNull String line, @Nullable String eventId,
            @NonNull String sport, long updatedMs) {
        return new Object[] {state, kicker, line, eventId, sport, "", updatedMs, 0, "#E53935"};
    }

    /** Replaces the rows {@code /events} returns; each array follows {@link ScoreContract#EVENT_COLUMNS}. */
    public static void seedEvents(@NonNull List<Object[]> rows) {
        for (Object[] row : rows) {
            if (row.length != ScoreContract.EVENT_COLUMNS.length) {
                throw new IllegalArgumentException("row has " + row.length + " values, contract has " + ScoreContract.EVENT_COLUMNS.length);
            }
        }
        EVENTS.clear();
        EVENTS.addAll(rows);
    }

    /** What {@code /backup} returns. */
    public static void seedBackup(@NonNull String json) {
        backupJson = json;
    }

    /** Every JSON a {@code restore} call delivered, oldest first. */
    @NonNull
    public static List<String> restored() {
        return new ArrayList<>(RESTORED);
    }

    /** How many {@code /pill} queries have been answered since the last {@link #reset()}. */
    public static int pillQueryCount() {
        return pillQueries;
    }

    /** Back to the fresh-companion state. */
    public static void reset() {
        PILL.clear();
        PILL.add(none());
        EVENTS.clear();
        backupJson = "{}";
        RESTORED.clear();
        pillQueries = 0;
    }

    private static Object[] none() {
        return new Object[] {ScoreContract.STATE_NONE, "", "", null, "", "", 0L, 0, "#9E9E9E"};
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        MatrixCursor cursor;
        if (ScoreContract.PILL.equals(uri)) {
            pillQueries++;
            cursor = new MatrixCursor(ScoreContract.PILL_COLUMNS);
            for (Object[] row : PILL) cursor.addRow(row);
        } else if (ScoreContract.EVENTS.equals(uri)) {
            cursor = new MatrixCursor(ScoreContract.EVENT_COLUMNS);
            for (Object[] row : EVENTS) cursor.addRow(row);
        } else if (isEventUri(uri)) {
            cursor = new MatrixCursor(ScoreContract.EVENT_COLUMNS);
            String id = uri.getLastPathSegment();
            for (Object[] row : EVENTS) {
                if (id != null && id.equals(String.valueOf(row[1]))) cursor.addRow(row);
            }
        } else if (ScoreContract.BACKUP.equals(uri)) {
            cursor = new MatrixCursor(ScoreContract.BACKUP_COLUMNS);
            cursor.addRow(new Object[] {backupJson});
        } else {
            return null;
        }
        Context context = getContext();
        if (context != null) cursor.setNotificationUri(context.getContentResolver(), ScoreContract.PILL);
        return cursor;
    }

    @Override
    @Nullable
    public String getType(@NonNull Uri uri) {
        if (ScoreContract.PILL.equals(uri)) return ScoreContract.TYPE_PILL;
        if (ScoreContract.EVENTS.equals(uri)) return ScoreContract.TYPE_EVENTS;
        if (isEventUri(uri)) return ScoreContract.TYPE_EVENT;
        if (ScoreContract.BACKUP.equals(uri)) return ScoreContract.TYPE_BACKUP;
        return null;
    }

    @Override
    @NonNull
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        Bundle result = new Bundle();
        if (!ScoreContract.METHOD_RESTORE.equals(method)) {
            result.putBoolean(ScoreContract.RESULT_OK, false);
            result.putString(ScoreContract.RESULT_ERROR, "unknown method " + method);
            return result;
        }
        String json = extras == null ? null : extras.getString(ScoreContract.EXTRA_JSON);
        if (json == null) {
            result.putBoolean(ScoreContract.RESULT_OK, false);
            result.putString(ScoreContract.RESULT_ERROR, "missing " + ScoreContract.EXTRA_JSON);
            return result;
        }
        RESTORED.add(json);
        backupJson = json;
        Context context = getContext();
        if (context != null) context.getContentResolver().notifyChange(ScoreContract.PILL, null);
        result.putBoolean(ScoreContract.RESULT_OK, true);
        return result;
    }

    private static boolean isEventUri(@Nullable Uri uri) {
        return uri != null
                && ScoreContract.AUTHORITY.equals(uri.getAuthority())
                && uri.getPathSegments().size() == 2
                && ScoreContract.PATH_EVENTS.equals(uri.getPathSegments().get(0));
    }

    @Override
    @Nullable
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
