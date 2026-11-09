package com.itsluminous.verysmarttv.core.contract;

import android.content.ComponentName;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What the Trakt companion publishes and the launcher's carousel reads (DESIGN.md §12.1).
 *
 * <p>The provider at {@link #AUTHORITY} is read-only and guarded by {@link #PERMISSION_READ}, a
 * {@code signature}-level permission the companion declares and the launcher holds, so only an app
 * signed with the same key reads a user's Trakt rows (§12.3). It answers:
 *
 * <ul>
 *   <li>{@link #ITEMS}: the ranked titles, one row per title with {@link #COLUMNS}; observers are
 *       notified after every sync and after a Settings change in the companion.
 *   <li>{@code image/backdrop/<_id>} and {@code image/poster/<_id>}: JPEG bytes through
 *       {@code openFile}, mode {@code r} only. The {@link #POSTER_PATH} and {@link #BACKDROP_PATH}
 *       columns already hold those URIs, so a reader opens them with
 *       {@code ContentResolver.openInputStream} and never needs a file permission.
 * </ul>
 *
 * <p>There is no backup path: the companion's state is OAuth tokens, which are never backed up
 * (ADR-032). {@code insert}, {@code update} and {@code delete} return nothing.
 */
public final class HeroContract {
    private HeroContract() {}

    /** The companion's package; {@link #DETAILS_ACTIVITY} and the provider live in it. */
    public static final String PACKAGE = "com.itsluminous.traktshelf";

    /** Content authority of the hero provider. */
    public static final String AUTHORITY = PACKAGE + ".hero";

    /** Read permission on the provider; {@code protectionLevel="signature"}. */
    public static final String PERMISSION_READ = PACKAGE + ".permission.READ_HERO";

    /** Path of the ranked titles. */
    public static final String PATH_ITEMS = "items";

    /** Path prefix of a backdrop JPEG; append the row's {@code _id}. */
    public static final String PATH_IMAGE_BACKDROP = "image/backdrop";

    /** Path prefix of a poster JPEG; append the row's {@code _id}. */
    public static final String PATH_IMAGE_POSTER = "image/poster";

    /** {@code content://com.itsluminous.traktshelf.hero/items}. */
    public static final Uri ITEMS = Uri.parse("content://" + AUTHORITY + "/" + PATH_ITEMS);

    /** MIME type of the {@link #ITEMS} cursor. */
    public static final String TYPE_ITEMS = "vnd.android.cursor.dir/vnd." + AUTHORITY + ".item";

    /** The activity the launcher opens for a title, with {@link #EXTRA_TRAKT_ID} and {@link #EXTRA_KIND}. */
    public static final ComponentName DETAILS_ACTIVITY = new ComponentName(PACKAGE, PACKAGE + ".DetailsActivity");

    /** {@code long} extra: the title's Trakt id. */
    public static final String EXTRA_TRAKT_ID = "trakt_id";

    /** {@code String} extra: {@link #KIND_MOVIE} or {@link #KIND_SHOW}. */
    public static final String EXTRA_KIND = "kind";

    /** Value of {@link #KIND} for a movie. */
    public static final String KIND_MOVIE = "movie";

    /** Value of {@link #KIND} for a show. */
    public static final String KIND_SHOW = "show";

    // Columns of /items, in cursor order. Stable: the carried HeroProvider's, not renamed.

    /** Row id, stable across syncs: the Trakt id with bit 40 set for a show. */
    public static final String ID = "_id";

    /** {@link #KIND_MOVIE} or {@link #KIND_SHOW}. */
    public static final String KIND = "kind";

    /** Title. */
    public static final String TITLE = "title";

    /** Release year, or 0. */
    public static final String YEAR = "year";

    /** TMDB overview. */
    public static final String SYNOPSIS = "synopsis";

    /** IMDb rating text as OMDb gives it ({@code "8.1"}), or empty. */
    public static final String IMDB_RATING = "imdb_rating";

    /** Rotten Tomatoes score text ({@code "94%"}), or empty. */
    public static final String RT_RATING = "rt_rating";

    /** Trakt rating as a percent, 0 to 100. */
    public static final String TRAKT_RATING = "trakt_rating";

    /** IMDb id ({@code "tt0111161"}), or empty. */
    public static final String IMDB_ID = "imdb_id";

    /** Trakt id. */
    public static final String TRAKT_ID = "trakt_id";

    /** {@code content://} URI of the poster JPEG. */
    public static final String POSTER_PATH = "poster_path";

    /** {@code content://} URI of the backdrop JPEG. */
    public static final String BACKDROP_PATH = "backdrop_path";

    /** Which companion row the title came from (Up next, Recommended shows, ...). */
    public static final String SOURCE_ROW = "source_row";

    /** Position in the carousel order, 0 first. */
    public static final String RANK = "rank";

    /** The companion's ready-made one-line meta text; the launcher renders it, never rebuilds it. */
    public static final String META = "meta";

    /** Next episode to watch ({@code "S2 E4"}), or empty. */
    public static final String NEXT_EPISODE = "next_episode";

    /** Every column of {@link #ITEMS}, in cursor order. */
    public static final String[] COLUMNS = {
        ID, KIND, TITLE, YEAR, SYNOPSIS, IMDB_RATING, RT_RATING, TRAKT_RATING,
        IMDB_ID, TRAKT_ID, POSTER_PATH, BACKDROP_PATH, SOURCE_ROW, RANK, META, NEXT_EPISODE,
    };

    /** {@code content://…hero/image/backdrop/<id>}. */
    @NonNull
    public static Uri backdrop(long id) {
        return Uri.parse("content://" + AUTHORITY + "/" + PATH_IMAGE_BACKDROP + "/" + id);
    }

    /** {@code content://…hero/image/poster/<id>}. */
    @NonNull
    public static Uri poster(long id) {
        return Uri.parse("content://" + AUTHORITY + "/" + PATH_IMAGE_POSTER + "/" + id);
    }

    /** Whether {@code uri} is one of this provider's image URIs, which a reader may open as a stream. */
    public static boolean isImage(@Nullable Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())) return false;
        String path = uri.getPath();
        return path != null
                && (path.startsWith("/" + PATH_IMAGE_BACKDROP + "/") || path.startsWith("/" + PATH_IMAGE_POSTER + "/"));
    }
}
