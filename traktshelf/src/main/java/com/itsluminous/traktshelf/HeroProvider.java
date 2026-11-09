package com.itsluminous.traktshelf;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Read-only, exported: what the launcher's hero carousel reads.
 *
 * content://com.itsluminous.traktshelf.hero/items                    the items, ranked (HeroContract.COLUMNS)
 * content://com.itsluminous.traktshelf.hero/image/backdrop/<_id>     the backdrop JPEG, through openFile
 * content://com.itsluminous.traktshelf.hero/image/poster/<_id>       the poster JPEG
 *
 * Guarded by HeroContract.PERMISSION_READ, a signature permission this app declares and the launcher holds (§12.3).
 *
 * poster_path and backdrop_path hold those image URIs, so a reader opens them with ContentResolver.openInputStream
 * and never needs a file permission. Observers of /items are notified after every sync and after a Settings change
 * (the rows, the count and the watchlist order come from Prefs on every query).
 */
public final class HeroProvider extends ContentProvider {
    private static final String TAG = "TraktShelf.Provider";
    /** The contract the launcher reads this provider by (DESIGN.md §12.1); the authority, URIs and columns live there. */
    public static final String AUTHORITY = HeroContract.AUTHORITY;
    public static final Uri ITEMS = HeroContract.ITEMS;
    static final String[] COLUMNS = HeroContract.COLUMNS;
    private static final int M_ITEMS = 1, M_BACKDROP = 2, M_POSTER = 3;
    private static final UriMatcher MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        MATCHER.addURI(AUTHORITY, HeroContract.PATH_ITEMS, M_ITEMS);
        MATCHER.addURI(AUTHORITY, HeroContract.PATH_IMAGE_BACKDROP + "/#", M_BACKDROP);
        MATCHER.addURI(AUTHORITY, HeroContract.PATH_IMAGE_POSTER + "/#", M_POSTER);
    }

    @Override
    public boolean onCreate() {
        // The launcher queries this on every Home: that is when the periodic sync is re-registered after a reboot.
        if (getContext() != null) Sync.schedule(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (MATCHER.match(uri) != M_ITEMS) return null;
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        Feed feed = Feed.load(getContext());
        List<Item> items = feed.heroItems(getContext(), Prefs.heroItems(getContext()));
        int rank = 0;
        for (Item it : items) {
            long id = it.stableId();
            cursor.addRow(new Object[]{id, it.kind, it.title, it.year, it.overview, it.imdbRating, it.rtRating, it.traktPercent(),
                    it.imdbId, it.traktId,
                    HeroContract.poster(id).toString(),
                    HeroContract.backdrop(id).toString(),
                    it.sourceRow, rank++, it.metaLine(), it.nextEpisode});
        }
        cursor.setNotificationUri(getContext().getContentResolver(), ITEMS);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int m = MATCHER.match(uri);
        if (m != M_BACKDROP && m != M_POSTER) throw new FileNotFoundException(uri.toString());
        if (!"r".equals(mode)) throw new FileNotFoundException("read only");
        long id;
        try {
            id = Long.parseLong(uri.getLastPathSegment());
        } catch (NumberFormatException e) {
            throw new FileNotFoundException(uri.toString());
        }
        String kind = (id & (1L << 40)) != 0 ? Item.SHOW : Item.MOVIE;
        long traktId = id & ((1L << 40) - 1);
        String name = kind + "_" + traktId + (m == M_BACKDROP ? "_b.jpg" : "_p.jpg");
        File f = Images.file(getContext(), name);
        if (f == null) throw new FileNotFoundException(name);
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        switch (MATCHER.match(uri)) {
            case M_ITEMS:
                return HeroContract.TYPE_ITEMS;
            case M_BACKDROP:
            case M_POSTER:
                return "image/jpeg";
            default:
                return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.w(TAG, "insert refused");
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
