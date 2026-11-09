package com.itsluminous.verysmarttv.feed;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsluminous.verysmarttv.core.contract.ScoreContract;

/**
 * score_pill: the live-score companion's one {@code /pill} row ({@link ScoreContract#PILL}), read through its content
 * provider the way {@code HeroFeed} reads the Trakt shelf (DESIGN.md §9.2, ADR-015). The launcher queries and renders;
 * no network, no sports logic. A {@link ContentObserver} re-reads on change, coalesced to {@link #COALESCE_MS}; while
 * the last row said live or break and Home is resumed it also re-reads every {@link #LIVE_PERIOD_MS}, which is what
 * tells the companion a screen is watching (§13.5). When the provider is missing, answers nothing or refuses
 * ({@code SecurityException}: another signing key) the listener gets {@code null} and the pill hides; a refusal is
 * logged once and ends the reads until the next {@link #start()}, so there is no retry loop.
 */
public final class ScoreFeed {
    private static final String TAG = "VerySmartTV.ScoreFeed";
    /** The companion's pill URI, from the contract both apps compile against. */
    public static final Uri PILL = ScoreContract.PILL;
    static final long COALESCE_MS = 800L;
    static final long LIVE_PERIOD_MS = 60_000L;

    /** The one row, as the pill needs it. */
    public static final class Row {
        @NonNull
        public final String state, kicker, line, color;
        @Nullable
        public final String eventId;
        public final boolean stale;

        public Row(@NonNull String state, @NonNull String kicker, @NonNull String line, @Nullable String eventId, @NonNull String color, boolean stale) {
            this.state = state;
            this.kicker = kicker;
            this.line = line;
            this.eventId = eventId;
            this.color = color;
            this.stale = stale;
        }

        /** Live or in a break: the companion is being watched and the read repeats. */
        public boolean inPlay() {
            return ScoreContract.STATE_LIVE.equals(state) || ScoreContract.STATE_BREAK.equals(state);
        }

        @Override
        @NonNull
        public String toString() {
            return state + " [" + kicker + "] " + line + (stale ? " stale" : "");
        }
    }

    public interface Listener {
        /** The row, or null when there is none to show (state none, no provider, refused, or an unreadable cursor). */
        void onPill(@Nullable Row row);
    }

    private final Context context;
    final Handler main;
    final Listener listener;
    private final ContentObserver observer;
    boolean started;
    volatile boolean loading;
    /** A SecurityException was seen: the companion is signed with another key; nothing is read again this session. */
    boolean denied;
    final Runnable reload = this::load;   // read by the observer below (lint: SyntheticAccessor)
    final Runnable liveTick = this::load;
    /** The 60 s re-read is posted (a live or break row came back and Home is resumed). */
    boolean liveArmed;

    public ScoreFeed(@NonNull Context context, @NonNull Handler main, @NonNull Listener listener) {
        this.context = context.getApplicationContext();
        this.main = main;
        this.listener = listener;
        observer = new ContentObserver(main) {
            @Override
            public void onChange(boolean selfChange) {
                main.removeCallbacks(reload);
                main.postDelayed(reload, COALESCE_MS);
            }
        };
    }

    /** onResume: observe and read now. */
    public void start() {
        if (started) return;
        started = true;
        denied = false;
        try {
            context.getContentResolver().registerContentObserver(PILL, false, observer);
        } catch (RuntimeException e) {
            Log.w(TAG, "observer not registered: " + e.getMessage());
        }
        load();
    }

    /** onPause: no reads, no timer, while Home is off screen. */
    public void stop() {
        if (!started) return;
        started = false;
        main.removeCallbacks(reload);
        main.removeCallbacks(liveTick);
        liveArmed = false;
        try {
            context.getContentResolver().unregisterContentObserver(observer);
        } catch (RuntimeException ignored) {
        }
    }

    public boolean started() {
        return started;
    }

    /** Queries off the main thread, hands the row to the listener on it, and re-arms the live timer when the row is in play. */
    void load() {
        if (!started || loading || denied) return;
        loading = true;
        new Thread(() -> {
            Row row;
            boolean refused = false;
            try {
                row = query();
            } catch (SecurityException e) {
                row = null;
                refused = true;
                Log.w(TAG, "provider refused (another signing key?): " + e.getMessage());
            }
            final Row result = row;
            final boolean deny = refused;
            main.post(() -> {
                loading = false;
                if (deny) denied = true;
                if (!started) return;
                listener.onPill(result);
                main.removeCallbacks(liveTick);
                liveArmed = result != null && result.inPlay() && !denied;
                if (liveArmed) main.postDelayed(liveTick, LIVE_PERIOD_MS);
            });
        }, "score-feed").start();
    }

    /** Whether the 60 s re-read is armed: only while a live or break row was returned and Home is resumed. */
    public boolean liveReadArmed() {
        return liveArmed;
    }

    @Nullable
    private Row query() {
        ContentResolver cr = context.getContentResolver();
        try (Cursor c = cr.query(PILL, null, null, null, null)) {
            if (c == null || !c.moveToFirst()) return null;
            int iState = c.getColumnIndex(ScoreContract.STATE), iKicker = c.getColumnIndex(ScoreContract.KICKER),
                    iLine = c.getColumnIndex(ScoreContract.LINE), iEvent = c.getColumnIndex(ScoreContract.EVENT_ID),
                    iStale = c.getColumnIndex(ScoreContract.STALE), iColor = c.getColumnIndex(ScoreContract.COLOR);
            if (iState < 0 || iLine < 0) return null;
            String state = text(c, iState);
            // none: nothing followed in the window; the same as no row to the pill, and nothing to re-read for.
            if (state.isEmpty() || ScoreContract.STATE_NONE.equals(state)) return null;
            return new Row(state, text(c, iKicker), text(c, iLine), nullable(c, iEvent), text(c, iColor), iStale >= 0 && !c.isNull(iStale) && c.getInt(iStale) == 1);
        } catch (SecurityException e) {
            throw e;
        } catch (RuntimeException e) {
            // No provider, or a cursor it could not build: the pill stays hidden.
            Log.w(TAG, "provider: " + e.getMessage());
            return null;
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
}
