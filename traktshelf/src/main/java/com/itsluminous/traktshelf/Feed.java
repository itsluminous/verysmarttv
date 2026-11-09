package com.itsluminous.traktshelf;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The rows Home shows and the provider serves, on disk as files/feed.json, plus files/meta.json: every item
 * ever enriched, keyed by kind:trakt id, so a sync refetches TMDB and OMDb only after seven days.
 */
final class Feed {
    private static final String TAG = "TraktShelf.Feed";
    static final String UP_NEXT = "up_next", REC_SHOWS = "rec_shows", REC_MOVIES = "rec_movies",
            WATCHLIST_SHOWS = "watchlist_shows", WATCHLIST_MOVIES = "watchlist_movies", TRENDING = "trending", HISTORY = "history";
    /** The one watchlist row of earlier builds: an old feed.json or Carousel rows choice still names it. */
    static final String LEGACY_WATCHLIST = "watchlist";
    /** Row order on Home. */
    static final String[] ROW_ORDER = {UP_NEXT, REC_SHOWS, REC_MOVIES, WATCHLIST_SHOWS, WATCHLIST_MOVIES, TRENDING, HISTORY};
    static final long META_TTL_MS = 7L * 86_400_000L;

    static final class Row {
        final String key;
        final List<Item> items = new ArrayList<>();

        Row(String key) {
            this.key = key;
        }

        int titleRes() {
            switch (key) {
                case UP_NEXT:
                    return R.string.row_up_next;
                case REC_SHOWS:
                    return R.string.row_rec_shows;
                case REC_MOVIES:
                    return R.string.row_rec_movies;
                case WATCHLIST_SHOWS:
                    return R.string.row_watchlist_shows;
                case WATCHLIST_MOVIES:
                    return R.string.row_watchlist_movies;
                case HISTORY:
                    return R.string.row_history;
                default:
                    return R.string.row_trending;
            }
        }
    }

    final LinkedHashMap<String, Row> rows = new LinkedHashMap<>();
    long syncedAt;
    /**
     * Every title the account has watched, as Item keys (movie:id, show:id; a show with one episode seen counts), from
     * Trakt's /sync/watched at the last sync. Trending and the recommendations leave these out (hideWatched); Up
     * Next, the watchlist and Recently watched are untouched.
     */
    final Set<String> watched = new HashSet<>();
    /** The rows that hide watched titles. */
    static final String[] HIDE_WATCHED_ROWS = {TRENDING, REC_SHOWS, REC_MOVIES};

    /** The watchlist row a title belongs to, by its kind. */
    static String watchlistRow(String kind) {
        return Item.SHOW.equals(kind) ? WATCHLIST_SHOWS : WATCHLIST_MOVIES;
    }

    static boolean isWatchlistRow(String key) {
        return WATCHLIST_SHOWS.equals(key) || WATCHLIST_MOVIES.equals(key) || LEGACY_WATCHLIST.equals(key);
    }

    /** True when the title is on the watchlist row of its kind. */
    boolean onWatchlist(Item item) {
        Row r = rows.get(watchlistRow(item.kind));
        if (r != null) for (Item it : r.items) if (it.key().equals(item.key())) return true;
        return false;
    }

    /**
     * Puts a title on, or takes it off, its watchlist row, as the Trakt write just did, so Home and the provider show
     * the change before the next sync confirms it. Creates the row when it is missing; the order is applied again.
     */
    void setOnWatchlist(Context c, Item item, boolean on) {
        String key = watchlistRow(item.kind);
        Row r = rows.get(key);
        if (r == null) {
            r = new Row(key);
            rows.put(key, r);
            LinkedHashMap<String, Row> ordered = new LinkedHashMap<>();
            for (String k : ROW_ORDER) if (rows.containsKey(k)) ordered.put(k, rows.get(k));
            rows.clear();
            rows.putAll(ordered);
        }
        for (int i = r.items.size() - 1; i >= 0; i--) if (r.items.get(i).key().equals(item.key())) r.items.remove(i);
        if (on) {
            Item copy = null;
            try {
                copy = Item.fromJson(item.toJson());
            } catch (JSONException ignored) {
            }
            if (copy == null) copy = item;
            copy.sourceRow = key;
            if (copy.listedAt == 0) copy.listedAt = System.currentTimeMillis();
            r.items.add(0, copy);
        }
        orderWatchlist(c);
    }
    String lastError;

    static File file(Context c) {
        return new File(c.getFilesDir(), "feed.json");
    }

    static File metaFile(Context c) {
        return new File(c.getFilesDir(), "meta.json");
    }

    static Feed load(Context c) {
        Feed f = new Feed();
        File file = file(c);
        if (!file.isFile()) return f;
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            f.syncedAt = j.optLong("synced_at");
            f.lastError = Item.opt(j, "last_error");
            JSONArray watched = j.optJSONArray("watched");
            if (watched != null) for (int i = 0; i < watched.length(); i++) f.watched.add(watched.getString(i));
            JSONObject rows = j.optJSONObject("rows");
            if (rows != null) {
                // A feed written before the split has one "watchlist" row: its shows and movies go to the two rows.
                JSONArray legacy = rows.optJSONArray(LEGACY_WATCHLIST);
                for (String key : ROW_ORDER) {
                    JSONArray a = rows.optJSONArray(key);
                    boolean fromLegacy = a == null && legacy != null && isWatchlistRow(key);
                    if (a == null && !fromLegacy) continue;
                    Row r = new Row(key);
                    if (!fromLegacy) {
                        for (int i = 0; i < a.length(); i++) r.items.add(Item.fromJson(a.getJSONObject(i)));
                    } else {
                        for (int i = 0; i < legacy.length(); i++) {
                            Item it = Item.fromJson(legacy.getJSONObject(i));
                            if (!watchlistRow(it.kind).equals(key)) continue;
                            it.sourceRow = key;
                            r.items.add(it);
                        }
                    }
                    f.rows.put(key, r);
                }
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "feed.json unreadable: " + e.getMessage());
        }
        f.orderWatchlist(c);
        f.hideWatched();   // a sync already did; a title marked watched since, and synced, goes too
        return f;
    }

    /**
     * Takes every watched title out of Trending and the two recommendation rows (HIDE_WATCHED_ROWS), for Home and the
     * provider alike; returns the titles that went. Nothing happens without a watched set (not signed in, or an old feed).
     */
    List<String> hideWatched() {
        List<String> gone = new ArrayList<>();
        if (watched.isEmpty()) return gone;
        for (String key : HIDE_WATCHED_ROWS) {
            Row r = rows.get(key);
            if (r == null) continue;
            for (int i = r.items.size() - 1; i >= 0; i--) {
                if (watched.contains(r.items.get(i).key())) gone.add(r.items.remove(i).title);
            }
        }
        return gone;
    }

    /**
     * Both watchlist rows in the order Settings asks for (Prefs.watchlistOrder): by date added, newest first, or by
     * release date, the titles still to come soonest first and then the released ones newest first; items without
     * the date keep Trakt's order at the end. Home and the provider both load the feed, so both see it.
     */
    void orderWatchlist(Context c) {
        boolean release = Prefs.ORDER_RELEASE.equals(Prefs.watchlistOrder(c));
        orderRow(rows.get(WATCHLIST_SHOWS), release);
        orderRow(rows.get(WATCHLIST_MOVIES), release);
    }

    private static void orderRow(Row r, boolean release) {
        if (r == null || r.items.size() < 2) return;
        final long now = System.currentTimeMillis();
        List<Item> dated = new ArrayList<>(), undated = new ArrayList<>();
        for (Item it : r.items) ((release ? it.released : it.listedAt) > 0 ? dated : undated).add(it);
        if (release) {
            Collections.sort(dated, (a, b) -> {
                boolean fa = a.released > now, fb = b.released > now;
                if (fa != fb) return fa ? -1 : 1;                           // upcoming first
                return fa ? Long.compare(a.released, b.released)            // soonest first
                        : Long.compare(b.released, a.released);             // then newest release first
            });
        } else {
            Collections.sort(dated, (a, b) -> Long.compare(b.listedAt, a.listedAt));
        }
        r.items.clear();
        r.items.addAll(dated);
        r.items.addAll(undated);
    }

    void save(Context c) {
        try {
            JSONObject rows = new JSONObject();
            for (Row r : this.rows.values()) {
                JSONArray a = new JSONArray();
                for (Item it : r.items) a.put(it.toJson());
                rows.put(r.key, a);
            }
            JSONArray seen = new JSONArray();
            for (String key : watched) seen.put(key);
            JSONObject j = new JSONObject().put("synced_at", syncedAt).put("last_error", lastError).put("rows", rows).put("watched", seen);
            writeAtomic(file(c), j.toString());
        } catch (IOException | JSONException e) {
            Log.w(TAG, "feed.json not saved: " + e.getMessage());
        }
    }

    /** Every item once, in row order (the provider's order); the first row an item appears in wins. */
    List<Item> flattened() {
        Map<String, Item> seen = new LinkedHashMap<>();
        for (String key : ROW_ORDER) {
            Row r = rows.get(key);
            if (r == null) continue;
            for (Item it : r.items) if (!seen.containsKey(it.key())) seen.put(it.key(), it);
        }
        return new ArrayList<>(seen.values());
    }

    Item find(String kind, long traktId) {
        for (Row r : rows.values()) for (Item it : r.items) if (it.traktId == traktId && it.kind.equals(kind)) return it;
        return null;
    }

    /**
     * Items for the launcher's carousel: the Up Next episode of the show watched most recently leads (Up Next is in
     * last_watched_at order, so its first eligible item), whatever Settings' rows say, and the rest is a true shuffle
     * of the pool the ticked rows (Prefs.heroRows) make, no row grouping. The pool takes every eligible item of each
     * ticked row, capped at ceil(max / 2) a row so no row floods the carousel, each title once; the shuffle is seeded
     * with the sync time (syncedAt), so the order holds between queries and changes with every sync, and the
     * provider's rank is that order. Eligible: a backdrop on disk and not upcoming (Item.upcoming: a future release
     * day, or an Up Next episode that has not aired); Home still shows those, since the watchlist is the viewer's own.
     */
    List<Item> heroItems(Context c, int max) {
        Set<String> enabled = Prefs.heroRows(c);
        List<Item> out = new ArrayList<>();
        if (max <= 0) return out;
        File img = Images.dir(c);
        Set<String> seen = new HashSet<>();
        Row upNext = rows.get(UP_NEXT);
        if (upNext != null) {
            for (Item it : upNext.items) {
                if (!eligible(it, img)) continue;
                seen.add(it.key());
                out.add(it);
                break;
            }
        }
        List<Item> pool = new ArrayList<>();
        int cap = (max + 1) / 2;
        for (String key : ROW_ORDER) {
            Row r = rows.get(key);
            if (r == null || !enabled.contains(key)) continue;
            int taken = 0;
            for (Item it : r.items) {
                if (taken >= cap) break;
                if (seen.contains(it.key()) || !eligible(it, img)) continue;
                seen.add(it.key());
                pool.add(it);
                taken++;
            }
        }
        shuffle(pool, syncedAt);
        for (Item it : pool) {
            if (out.size() >= max) break;
            out.add(it);
        }
        Log.i(TAG, "carousel: " + out.size() + " of " + max + " from " + enabled + ", lead "
                + (out.isEmpty() || !UP_NEXT.equals(out.get(0).sourceRow) ? "none" : out.get(0).title + " " + out.get(0).nextEpisode)
                + ", pool " + pool.size() + ", seed " + syncedAt);
        return out;
    }

    private static boolean eligible(Item it, File img) {
        return !it.upcoming() && it.backdropUrl != null && new File(img, it.backdropFile()).isFile();
    }

    /** Fisher-Yates with java.util.Random on the seed: the same seed, the same order. */
    static <T> void shuffle(List<T> items, long seed) {
        Collections.shuffle(items, new Random(seed));
    }

    // ---- metadata cache ---------------------------------------------------------------------------

    static Map<String, Item> loadMeta(Context c) {
        Map<String, Item> out = new HashMap<>();
        File file = metaFile(c);
        if (!file.isFile()) return out;
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            JSONArray a = j.optJSONArray("items");
            if (a != null) for (int i = 0; i < a.length(); i++) {
                Item it = Item.fromJson(a.getJSONObject(i));
                out.put(it.key(), it);
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "meta.json unreadable: " + e.getMessage());
        }
        return out;
    }

    static void saveMeta(Context c, Map<String, Item> meta) {
        try {
            // Drop entries older than 30 days, so the file tracks what the account still shows.
            long cutoff = System.currentTimeMillis() - 30L * 86_400_000L;
            JSONArray a = new JSONArray();
            for (Item it : meta.values()) if (it.enrichedAt >= cutoff) a.put(it.toJson());
            writeAtomic(metaFile(c), new JSONObject().put("items", a).toString());
        } catch (IOException | JSONException e) {
            Log.w(TAG, "meta.json not saved: " + e.getMessage());
        }
    }

    static void writeAtomic(File file, String text) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) throw new IOException("rename failed");
    }

    static List<Item> none() {
        return Collections.emptyList();
    }
}
