package com.itsluminous.verysmarttv.testkit;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-process stand-in for the Trakt companion's hero provider, speaking {@link HeroContract}.
 *
 * <p>Tests script it through the static {@link #seed} and {@link #seedImage}, then register it under
 * {@link HeroContract#AUTHORITY} ({@code Robolectric.setupContentProvider}, or a {@code <provider>} in
 * an androidTest manifest). State is static because the system instantiates the provider; call
 * {@link #reset()} in {@code @After}. Like the real provider it is read-only: {@code insert},
 * {@code update} and {@code delete} do nothing and {@code openFile} refuses any mode but {@code r}.
 */
public final class FakeHeroProvider extends ContentProvider {
    private static final List<Object[]> ROWS = new ArrayList<>();
    private static final Map<Long, byte[]> IMAGES = new HashMap<>();
    private static int queries;
    private static final int RANK_INDEX = java.util.Arrays.asList(HeroContract.COLUMNS).indexOf(HeroContract.RANK);

    /**
     * Replaces the rows {@code /items} returns; each array follows {@link HeroContract#COLUMNS}. The
     * {@code rank} column is set from the position, as the real provider does at query time.
     */
    public static void seed(@NonNull List<Object[]> rows) {
        for (Object[] row : rows) {
            if (row.length != HeroContract.COLUMNS.length) {
                throw new IllegalArgumentException("row has " + row.length + " values, contract has " + HeroContract.COLUMNS.length);
            }
        }
        ROWS.clear();
        for (Object[] row : rows) {
            Object[] copy = row.clone();
            copy[RANK_INDEX] = ROWS.size();
            ROWS.add(copy);
        }
    }

    /**
     * One {@code /items} row with the given id, kind, title and meta line and empty text elsewhere;
     * its poster and backdrop columns point back at this provider, as the real one's do. The rank is
     * filled in by {@link #seed}.
     */
    @NonNull
    public static Object[] row(long id, @NonNull String kind, @NonNull String title, @NonNull String meta) {
        return new Object[] {
            id, kind, title, 0, "", "", "", 0, "", id & ((1L << 40) - 1),
            HeroContract.poster(id).toString(), HeroContract.backdrop(id).toString(), "", 0, meta, "",
        };
    }

    /** The bytes {@code openFile} serves for both the poster and the backdrop of {@code id}. */
    public static void seedImage(long id, @NonNull byte[] jpeg) {
        IMAGES.put(id, jpeg);
    }

    /** How many {@code /items} queries have been answered since the last {@link #reset()}. */
    public static int queryCount() {
        return queries;
    }

    /** Forgets every seeded row, image and count. */
    public static void reset() {
        ROWS.clear();
        IMAGES.clear();
        queries = 0;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    @Nullable
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        if (!HeroContract.ITEMS.equals(uri)) return null;
        queries++;
        MatrixCursor cursor = new MatrixCursor(HeroContract.COLUMNS);
        for (Object[] row : ROWS) cursor.addRow(row);
        Context context = getContext();
        if (context != null) cursor.setNotificationUri(context.getContentResolver(), HeroContract.ITEMS);
        return cursor;
    }

    @Override
    @Nullable
    public String getType(@NonNull Uri uri) {
        return HeroContract.ITEMS.equals(uri) ? HeroContract.TYPE_ITEMS : null;
    }

    @Override
    @NonNull
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        if (!HeroContract.isImage(uri)) throw new FileNotFoundException(uri.toString());
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        long id;
        try {
            id = Long.parseLong(uri.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new FileNotFoundException(uri.toString());
        }
        byte[] bytes = IMAGES.get(id);
        Context context = getContext();
        if (bytes == null || context == null) throw new FileNotFoundException(uri.toString());
        try {
            File file = File.createTempFile("fake-hero-", ".jpg", context.getCacheDir());
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
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
