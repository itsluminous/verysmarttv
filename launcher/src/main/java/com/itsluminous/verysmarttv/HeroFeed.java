package com.itsluminous.verysmarttv;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.util.LruCache;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * hero_feed: the Trakt shelf app's items ({@link HeroContract#PACKAGE}), read through its content provider. The launcher
 * only queries and opens what the provider offers; no network, no file paths, no dependency. When the provider is
 * missing or refuses, the list is empty and the shelf behaves as without the switch.
 */
final class HeroFeed {
    private static final String TAG = "VerySmartTV.HeroFeed";
    /** The companion's items URI, from the contract both apps compile against (DESIGN.md §12.1). */
    static final Uri ITEMS = HeroContract.ITEMS;
    /**
     * Items beyond this are not read. The companion's Carousel items setting goes up to 20, so this only guards against a
     * provider that answers with far more; backdrops are decoded on demand (an LRU of three), so the count costs no memory.
     */
    static final int MAX_ITEMS = 20;

    /** One carousel entry. */
    static final class Entry {
        long id, traktId;
        String kind = "movie", title = "", synopsis = "", imdb, rt, trakt, meta, kicker;
        int year;
        Uri backdrop;
    }

    interface Listener {
        void onItems(List<Entry> items);
    }

    private final Context context;
    private final Handler main;
    private final Listener listener;
    private final ContentObserver observer;
    private final LruCache<Long, Bitmap> art = new LruCache<>(3);
    /** A right-to-left Home puts the text on the right: the backdrop sits left and fades on its right instead. */
    private final boolean rtl;
    private boolean started;
    private volatile boolean loading;
    private final Runnable reload = this::load;

    HeroFeed(Context context, Handler main, Listener listener) {
        this.context = context.getApplicationContext();
        this.main = main;
        this.listener = listener;
        rtl = this.context.getResources().getBoolean(R.bool.rtl_menu_bar);
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                // A sync notifies once; coalesce anyway, the companion may notify from several places.
                main.removeCallbacks(reload);
                main.postDelayed(reload, 800);
            }
        };
    }

    void start() {
        if (started) return;
        started = true;
        try {
            context.getContentResolver().registerContentObserver(ITEMS, false, observer);
        } catch (RuntimeException e) {
            Log.w(TAG, "observer not registered: " + e.getMessage());
        }
        load();
    }

    void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(reload);
        try {
            context.getContentResolver().unregisterContentObserver(observer);
        } catch (RuntimeException ignored) {
        }
    }

    void release() {
        stop();
        art.evictAll();
    }

    /** Queries the provider off the main thread and hands the listener the items (an empty list when there are none). */
    void load() {
        if (loading) return;
        loading = true;
        new Thread(() -> {
            List<Entry> items = query();
            main.post(() -> {
                loading = false;
                if (started) listener.onItems(items);
            });
        }, "hero-feed").start();
    }

    private List<Entry> query() {
        List<Entry> out = new ArrayList<>();
        ContentResolver cr = context.getContentResolver();
        try (Cursor c = cr.query(ITEMS, null, null, null, null)) {
            if (c == null) return Collections.emptyList();
            int iId = c.getColumnIndex(HeroContract.ID), iKind = c.getColumnIndex(HeroContract.KIND), iTitle = c.getColumnIndex(HeroContract.TITLE),
                    iYear = c.getColumnIndex(HeroContract.YEAR), iSyn = c.getColumnIndex(HeroContract.SYNOPSIS), iImdb = c.getColumnIndex(HeroContract.IMDB_RATING),
                    iRt = c.getColumnIndex(HeroContract.RT_RATING), iTrakt = c.getColumnIndex(HeroContract.TRAKT_RATING), iTraktId = c.getColumnIndex(HeroContract.TRAKT_ID),
                    iBackdrop = c.getColumnIndex(HeroContract.BACKDROP_PATH), iMeta = c.getColumnIndex(HeroContract.META), iNext = c.getColumnIndex(HeroContract.NEXT_EPISODE),
                    iRow = c.getColumnIndex(HeroContract.SOURCE_ROW);
            if (iId < 0 || iTitle < 0 || iBackdrop < 0) return Collections.emptyList();
            while (c.moveToNext() && out.size() < MAX_ITEMS) {
                Entry e = new Entry();
                e.id = c.getLong(iId);
                e.title = text(c, iTitle);
                if (e.title.isEmpty()) continue;
                e.kind = iKind < 0 ? HeroContract.KIND_MOVIE : text(c, iKind);
                e.year = iYear < 0 ? 0 : c.getInt(iYear);
                e.synopsis = text(c, iSyn);
                e.imdb = nullable(c, iImdb);
                e.rt = nullable(c, iRt);
                e.trakt = nullable(c, iTrakt);
                e.traktId = iTraktId < 0 ? 0 : c.getLong(iTraktId);
                e.meta = nullable(c, iMeta);
                String backdrop = nullable(c, iBackdrop);
                if (backdrop != null && HeroContract.isImage(Uri.parse(backdrop))) e.backdrop = Uri.parse(backdrop);
                String next = nullable(c, iNext);
                String row = nullable(c, iRow);
                e.kicker = next != null ? context.getString(R.string.hero_feed_up_next) + " · " + next : kickerFor(row, e.kind);
                out.add(e);
            }
        } catch (RuntimeException e) {
            // No provider, a refusal, a cursor it could not build: the shelf stays as without the switch.
            Log.w(TAG, "provider: " + e.getMessage());
            return Collections.emptyList();
        }
        return out;
    }

    private String kickerFor(String row, String kind) {
        int what = "show".equals(kind) ? R.string.hero_feed_show : R.string.hero_feed_movie;
        if (row == null) return context.getString(what);
        switch (row) {
            case "rec_shows":
            case "rec_movies":
                return context.getString(R.string.hero_feed_recommended) + " · " + context.getString(what);
            case "watchlist":          // the companion before its watchlist split into shows and movies
            case "watchlist_shows":
            case "watchlist_movies":
                return context.getString(R.string.hero_feed_watchlist) + " · " + context.getString(what);
            case "trending":
                return context.getString(R.string.hero_feed_trending) + " · " + context.getString(what);
            default:
                return context.getString(what);
        }
    }

    private static String text(Cursor c, int i) {
        if (i < 0 || c.isNull(i)) return "";
        String s = c.getString(i);
        return s == null ? "" : s;
    }

    private static String nullable(Cursor c, int i) {
        if (i < 0 || c.isNull(i)) return null;
        String s = c.getString(i);
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * The backdrop for the shelf, decoded off the main thread to w x h (power-of-two downsampling, then a centre
     * crop), its left and bottom faded to transparent so it melts into the wallpaper. Three are kept.
     */
    void backdrop(Entry e, int w, int h, Consumer<Bitmap> then) {
        Bitmap cached = art.get(e.id);
        if (cached != null) {
            then.accept(cached);
            return;
        }
        if (e.backdrop == null) {
            then.accept(null);
            return;
        }
        new Thread(() -> {
            Bitmap b = null;
            try {
                b = decodeFaded(e.backdrop, w, h);
            } catch (IOException | RuntimeException | OutOfMemoryError ex) {
                Log.w(TAG, "backdrop " + e.id + ": " + ex.getMessage());
            }
            final Bitmap result = b;
            main.post(() -> {
                if (result != null) art.put(e.id, result);
                then.accept(result);
            });
        }, "hero-feed-art").start();
    }

    private Bitmap decodeFaded(Uri uri, int w, int h) throws IOException {
        ContentResolver cr = context.getContentResolver();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap src;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            src = BitmapFactory.decodeStream(in, null, o);
        }
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        // Centre crop.
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((w - src.getWidth() * scale) / 2f, (h - src.getHeight() * scale) / 2f);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src, m, paint);
        src.recycle();
        // Fade: transparent on the left third (the text's side), along the top and along the bottom, into the wallpaper.
        Paint mask = new Paint();
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mask.setShader(new LinearGradient(rtl ? w : 0, 0, rtl ? 0 : w, 0, new int[]{0x00000000, 0x00000000, 0xFF000000, 0xFF000000},
                new float[]{0f, 0.22f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mask);
        // Top: the top bar sits over the art (its pills about 37 to 80dp down, 11 to 23% of the 349dp view), and a
        // bright backdrop (sky, snow) under frosted white pills makes them unreadable. Transparent through the top 10%,
        // opaque only from 40% down, so the pills see at most a third of the art and the clock none of it.
        mask.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x00000000, 0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                new float[]{0f, 0.10f, 0.40f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, mask);
        return out;
    }
}
