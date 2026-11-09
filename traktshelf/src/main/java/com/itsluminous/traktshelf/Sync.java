package com.itsluminous.traktshelf;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One sync: Trakt rows, then TMDB and OMDb for every item not enriched in the last week, then the images, then
 * feed.json and a notifyChange on the provider. Runs on its own thread; a second request while one runs is
 * folded into it. Scheduled every six hours by JobScheduler (SyncJob), on sign-in, and from Settings.
 */
final class Sync {
    private static final String TAG = "TraktShelf.Sync";
    static final String ACTION_SYNCED = "com.itsluminous.traktshelf.SYNCED";
    static final String EXTRA_ERROR = "error";
    private static final int JOB_ID = 1;
    private static final long PERIOD_MS = 6L * 3600_000L;

    private static final AtomicBoolean running = new AtomicBoolean();
    private static volatile String progress;
    private static final List<Runnable> waiters = Collections.synchronizedList(new ArrayList<>());

    private Sync() {
    }

    static boolean isRunning() {
        return running.get();
    }

    static String progress() {
        return progress;
    }

    /**
     * Registers the six-hourly job once; a no-op while it is pending. Not persisted (this TV's JobScheduler refused a
     * persisted job even with RECEIVE_BOOT_COMPLETED granted), so after a reboot the job comes back the first time
     * Home or the launcher's carousel wakes the app: both call this.
     */
    static void schedule(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js == null) return;
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, SyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .build();
        try {
            js.schedule(job);
            Log.i(TAG, "periodic sync scheduled every " + PERIOD_MS / 3600_000 + " h");
        } catch (RuntimeException e) {
            Log.w(TAG, "periodic sync not scheduled: " + e.getMessage());
        }
    }

    /** Starts a sync unless one runs; onDone (main thread) runs when this or the running one finishes. */
    static void runNow(Context context, Runnable onDone) {
        final Context c = context.getApplicationContext();
        if (onDone != null) waiters.add(onDone);
        if (!running.compareAndSet(false, true)) return;
        Thread t = new Thread(() -> {
            String error = null;
            try {
                error = run(c);
            } catch (Throwable e) {
                Log.e(TAG, "sync crashed", e);
                error = e.toString();
            } finally {
                running.set(false);
                progress = null;
                final String err = error;
                new Handler(Looper.getMainLooper()).post(() -> {
                    c.sendBroadcast(new Intent(ACTION_SYNCED).setPackage(c.getPackageName()).putExtra(EXTRA_ERROR, err));
                    List<Runnable> done;
                    synchronized (waiters) {
                        done = new ArrayList<>(waiters);
                        waiters.clear();
                    }
                    for (Runnable r : done) r.run();
                });
            }
        }, "traktshelf-sync");
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    /** The sync itself. Returns null, or a short error for the UI; partial results are still saved. */
    private static String run(Context c) {
        long t0 = System.currentTimeMillis();
        Auth auth = new Auth(c);
        Trakt trakt = new Trakt(auth);
        Feed old = Feed.load(c);
        Feed feed = new Feed();
        String error = null;
        if (!Auth.configured()) return c.getString(R.string.err_no_client_id);

        boolean signedIn = auth.refreshIfNeeded();
        // Rows that need no account.
        progress = c.getString(R.string.sync_trending);
        try {
            List<Item> movies = trakt.trending(Item.MOVIE, 10);
            List<Item> shows = trakt.trending(Item.SHOW, 10);
            Feed.Row trending = new Feed.Row(Feed.TRENDING);
            for (int i = 0; i < Math.max(movies.size(), shows.size()); i++) {
                if (i < shows.size()) trending.items.add(shows.get(i));
                if (i < movies.size()) trending.items.add(movies.get(i));
            }
            feed.rows.put(Feed.TRENDING, trending);
        } catch (IOException | JSONException e) {
            Log.w(TAG, "trending: " + e.getMessage());
            error = c.getString(R.string.err_trakt, e.getMessage());
            Feed.Row kept = old.rows.get(Feed.TRENDING);
            if (kept != null) feed.rows.put(Feed.TRENDING, kept);
        }

        if (signedIn) {
            try {
                if (auth.username() == null) {
                    String name = trakt.username();
                    if (name != null) auth.setUsername(name);
                }
                progress = c.getString(R.string.sync_up_next);
                // The watched shows serve Up Next and, with the watched movies, the set Trending and the
                // recommendations leave out (Feed.hideWatched): one episode seen counts the show as seen.
                List<org.json.JSONObject> watchedShows = trakt.watchedShows();
                feed.watched.clear();
                feed.watched.addAll(Trakt.watchedShowKeys(watchedShows));
                feed.watched.addAll(trakt.watchedMovieKeys());
                Feed.Row upNext = new Feed.Row(Feed.UP_NEXT);
                upNext.items.addAll(trakt.upNext(watchedShows, 14, 10));
                feed.rows.put(Feed.UP_NEXT, upNext);
                progress = c.getString(R.string.sync_recommendations);
                Feed.Row recShows = new Feed.Row(Feed.REC_SHOWS);
                recShows.items.addAll(trakt.recommendations(Item.SHOW, 15));
                feed.rows.put(Feed.REC_SHOWS, recShows);
                Feed.Row recMovies = new Feed.Row(Feed.REC_MOVIES);
                recMovies.items.addAll(trakt.recommendations(Item.MOVIE, 15));
                feed.rows.put(Feed.REC_MOVIES, recMovies);
                progress = c.getString(R.string.sync_watchlist);
                String sort = Prefs.ORDER_RELEASE.equals(Prefs.watchlistOrder(c)) ? "released" : "added";
                Feed.Row watchlistShows = new Feed.Row(Feed.WATCHLIST_SHOWS);
                watchlistShows.items.addAll(trakt.watchlist(Item.SHOW, sort, 20));
                feed.rows.put(Feed.WATCHLIST_SHOWS, watchlistShows);
                Feed.Row watchlistMovies = new Feed.Row(Feed.WATCHLIST_MOVIES);
                watchlistMovies.items.addAll(trakt.watchlist(Item.MOVIE, sort, 20));
                feed.rows.put(Feed.WATCHLIST_MOVIES, watchlistMovies);
                Feed.Row history = new Feed.Row(Feed.HISTORY);
                history.items.addAll(trakt.history(12));
                feed.rows.put(Feed.HISTORY, history);
            } catch (Trakt.Unauthorized e) {
                Log.w(TAG, "account rows: token refused, signing out");
                auth.signOut();
                error = c.getString(R.string.err_signed_out);
            } catch (IOException | JSONException e) {
                Log.w(TAG, "account rows: " + e.getMessage());
                error = c.getString(R.string.err_trakt, e.getMessage());
                // Keep what the last sync had for the rows this one could not fetch.
                for (String key : new String[]{Feed.UP_NEXT, Feed.REC_SHOWS, Feed.REC_MOVIES, Feed.WATCHLIST_SHOWS, Feed.WATCHLIST_MOVIES, Feed.HISTORY}) {
                    if (!feed.rows.containsKey(key) && old.rows.containsKey(key)) feed.rows.put(key, old.rows.get(key));
                }
                if (feed.watched.isEmpty()) feed.watched.addAll(old.watched);
            }
        }
        // Titles already seen leave Trending and the recommendations before they cost a TMDB or OMDb call.
        List<String> hidden = feed.hideWatched();
        if (!hidden.isEmpty()) Log.i(TAG, "excluded " + hidden.size() + " watched from trending/recommended: " + hidden);
        // Row order as Home shows it.
        java.util.LinkedHashMap<String, Feed.Row> ordered = new java.util.LinkedHashMap<>();
        for (String key : Feed.ROW_ORDER) if (feed.rows.containsKey(key)) ordered.put(key, feed.rows.get(key));
        feed.rows.clear();
        feed.rows.putAll(ordered);
        for (Feed.Row r : feed.rows.values()) for (Item it : r.items) if (it.sourceRow == null) it.sourceRow = r.key;

        // Metadata: once per unique item, cached a week.
        Map<String, Item> meta = Feed.loadMeta(c);
        Set<String> done = new LinkedHashSet<>();
        List<Item> unique = feed.flattened();
        int n = 0;
        long now = System.currentTimeMillis();
        for (Item it : unique) {
            if (Thread.currentThread().isInterrupted()) break;
            progress = c.getString(R.string.sync_details, ++n, unique.size());
            Item cached = meta.get(it.key());
            if (cached != null && now - cached.enrichedAt < Feed.META_TTL_MS) {
                it.takeMetadataFrom(cached);
            } else {
                Metadata.enrichTmdb(it);
                Metadata.enrichOmdb(it);
                it.enrichedAt = now;
                Item copy = Item.fromJson(safeJson(it));
                if (copy != null) meta.put(it.key(), copy);
            }
            done.add(it.key());
        }
        // The same item in two rows: both copies carry the metadata.
        for (Feed.Row r : feed.rows.values()) for (Item it : r.items) {
            Item m = meta.get(it.key());
            if (m != null && it.enrichedAt == 0) it.takeMetadataFrom(m);
        }
        Feed.saveMeta(c, meta);

        // Images: posters for every item, backdrops too (the hero and the launcher's carousel use them).
        n = 0;
        for (Item it : unique) {
            if (Thread.currentThread().isInterrupted()) break;
            progress = c.getString(R.string.sync_images, ++n, unique.size());
            Images.fetch(c, it.posterUrl, it.posterFile());
            Images.fetch(c, it.backdropUrl, it.backdropFile());
        }
        Images.trim(c);

        feed.syncedAt = System.currentTimeMillis();
        feed.lastError = error;
        feed.save(c);
        Images.clearMemory();
        c.getContentResolver().notifyChange(HeroProvider.ITEMS, null);
        Log.i(TAG, "sync done in " + (feed.syncedAt - t0) / 1000 + " s: " + unique.size() + " items in " + feed.rows.size()
                + " rows" + (error == null ? "" : ", error: " + error));
        return error;
    }

    private static org.json.JSONObject safeJson(Item it) {
        try {
            return it.toJson();
        } catch (JSONException e) {
            return new org.json.JSONObject();
        }
    }
}
