package com.itsluminous.traktshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Where Play and Trailer go. Play routes by IMDb id: Stremio's deep link (stremio:///detail/movie/tt../tt.., checked on
 * this TV: it opens the title's streams), CloudStream's search scheme (cloudstreamsearch://title), or NetMirror's main
 * screen (it has no search intent). Trailers go to TizenTube or SmartTube as a YouTube watch URL (both declare the
 * youtube.com host); the stock YouTube app is used only when neither is installed.
 */
final class Players {
    static final String PREFS = "settings";
    static final String KEY_PLAYER = "player";   // ask | stremio | cloudstream | netmirror
    static final String STREMIO = "com.stremio.one", CLOUDSTREAM = "com.lagradost.cloudstream3", NETMIRROR = "app.netmirror.newtv";
    /** Trailers go to the first of these that is installed: TizenTube, then SmartTube's builds. */
    static final String TIZENTUBE = "io.gh.reisxd.tizentube.cobalt";
    static final String[] TRAILER_PREFERRED = {TIZENTUBE, "org.smarttube.stable", "org.smarttube.beta", "com.teamsmart.videomanager.tv"};

    private Players() {
    }

    static final class Player {
        final String id, label, pkg;

        Player(String id, String label, String pkg) {
            this.id = id;
            this.label = label;
            this.pkg = pkg;
        }
    }

    static boolean installed(Context c, String pkg) {
        try {
            c.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    static List<Player> installedPlayers(Context c) {
        List<Player> out = new ArrayList<>();
        if (installed(c, STREMIO)) out.add(new Player("stremio", "Stremio", STREMIO));
        if (installed(c, CLOUDSTREAM)) out.add(new Player("cloudstream", "CloudStream", CLOUDSTREAM));
        if (installed(c, NETMIRROR)) out.add(new Player("netmirror", "NetMirror", NETMIRROR));
        return out;
    }

    static String defaultPlayer(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLAYER, "ask");
    }

    static void setDefaultPlayer(Context c, String id) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLAYER, id).apply();
    }

    static Intent playIntent(Context c, Player p, Item it) {
        switch (p.id) {
            case "stremio": {
                String type = Item.SHOW.equals(it.kind) ? "series" : "movie";
                String id = it.imdbId != null ? it.imdbId : "tmdb:" + it.tmdbId;
                String video = id;
                if (Item.SHOW.equals(it.kind)) video = it.nextSeason > 0 ? id + ":" + it.nextSeason + ":" + it.nextNumber : null;
                String url = "stremio:///detail/" + type + "/" + id + (video != null ? "/" + video : "");
                return new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(p.pkg);
            }
            case "cloudstream":
                return new Intent(Intent.ACTION_VIEW, Uri.parse("cloudstreamsearch://" + Uri.encode(it.title))).setPackage(p.pkg);
            default: {
                Intent launch = c.getPackageManager().getLeanbackLaunchIntentForPackage(p.pkg);
                if (launch == null) launch = c.getPackageManager().getLaunchIntentForPackage(p.pkg);
                return launch;
            }
        }
    }

    static void play(Activity a, Item it) {
        List<Player> players = installedPlayers(a);
        if (players.isEmpty()) {
            Toast.makeText(a, R.string.no_player, Toast.LENGTH_LONG).show();
            return;
        }
        String def = defaultPlayer(a);
        for (Player p : players) if (p.id.equals(def)) {
            start(a, playIntent(a, p, it));
            return;
        }
        if (players.size() == 1) {
            start(a, playIntent(a, players.get(0), it));
            return;
        }
        String[] names = new String[players.size()];
        for (int i = 0; i < names.length; i++) names[i] = players.get(i).label;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                .setTitle(a.getString(R.string.play_with))
                .setItems(names, (d, which) -> start(a, playIntent(a, players.get(which), it)))
                .show();
    }

    static void trailer(Activity a, Item it) {
        if (it.trailerYoutubeId == null) {
            Toast.makeText(a, R.string.no_trailer, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=" + it.trailerYoutubeId));
        List<ResolveInfo> handlers = a.getPackageManager().queryIntentActivities(view, 0);
        // TizenTube when installed, SmartTube otherwise: straight there, no chooser.
        for (String pkg : TRAILER_PREFERRED) for (ResolveInfo r : handlers) if (pkg.equals(r.activityInfo.packageName)) {
            Intent target = new Intent(view).setPackage(pkg);
            if (TIZENTUBE.equals(pkg)) ColdStartResend.arm(target);
            start(a, target);
            return;
        }
        if (handlers.size() == 1) {
            start(a, new Intent(view).setPackage(handlers.get(0).activityInfo.packageName));
        } else if (handlers.size() > 1) {
            // Neither is installed and several other apps take YouTube links: ask.
            String[] names = new String[handlers.size()];
            for (int i = 0; i < names.length; i++) names[i] = String.valueOf(handlers.get(i).loadLabel(a.getPackageManager()));
            new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_NoActionBar)
                    .setTitle(a.getString(R.string.trailer_with))
                    .setItems(names, (d, which) -> start(a, new Intent(view).setPackage(handlers.get(which).activityInfo.packageName)))
                    .show();
        } else {
            start(a, new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + it.trailerYoutubeId)));
        }
    }

    private static void start(Activity a, Intent i) {
        if (i == null) {
            Toast.makeText(a, R.string.no_player, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.no_player, Toast.LENGTH_SHORT).show();
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * TizenTube (Cobalt) loses a YouTube link it is started with cold. Starboard keeps the intent's URL as the
     * "initial deep link", the first document that reads it (TizenTube's own user script) consumes it, and the
     * YouTube TV app, which reads it next, gets an empty string and opens its home page. Warm, the same intent
     * reaches the already loaded page as a deep-link event and the video plays. Nothing in the app's intent
     * filter or extras avoids this (every URL form and an explicit component behave the same cold; the Cobalt
     * "args" extra only carries switches), and other processes cannot be queried on Android 10, so the companion
     * tells cold from warm by timing: the system stops the sending activity once TizenTube has drawn, which takes
     * about 0.55 s warm and 1.1 s or more cold (process start plus Cobalt's libraries) on the Mi TV 4A. After a
     * cold start the same intent is sent again once the page is up, about 6 s after the first; the activity is
     * singleTask, so it arrives as onNewIntent and plays the video. A re-send onto a video already playing would
     * restart it, which is why warm starts are left alone. Hooks: DetailsActivity's onPause, onStop and onStart.
     */
    static final class ColdStartResend {
        /** The companion's onPause-to-onStop gap above which TizenTube is taken to have started cold. */
        static final long COLD_GAP_MS = 850;
        /** When the re-sent intent goes out, counted from the first one: the YouTube TV page is listening by then. */
        static final long RESEND_AFTER_MS = 6000;
        /** A stop more than this long after the trailer was started is unrelated to it. */
        static final long WINDOW_MS = 4000;
        private static final Handler HANDLER = new Handler(Looper.getMainLooper());
        private static Intent armed;
        private static long startedAt, pausedAt;
        private static Runnable pending;

        private ColdStartResend() {
        }

        static void arm(Intent i) {
            cancel();
            armed = new Intent(i);
            startedAt = SystemClock.uptimeMillis();
            pausedAt = 0;
        }

        static void onPause() {
            if (armed != null && pausedAt == 0) pausedAt = SystemClock.uptimeMillis();
        }

        static void onStop(Context c) {
            if (armed == null) return;
            long now = SystemClock.uptimeMillis();
            boolean inWindow = now - startedAt <= WINDOW_MS;
            boolean cold = pausedAt > 0 && now - pausedAt >= COLD_GAP_MS;
            Intent again = armed;
            armed = null;
            if (!inWindow || !cold) return;
            final Context app = c.getApplicationContext();
            pending = () -> {
                pending = null;
                try {
                    app.startActivity(again.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException | SecurityException ignored) {
                }
            };
            HANDLER.postAtTime(pending, startedAt + RESEND_AFTER_MS);
        }

        /** The companion is back in front: the person left TizenTube, so nothing is sent to it. */
        static void cancel() {
            armed = null;
            if (pending != null) HANDLER.removeCallbacks(pending);
            pending = null;
        }
    }
}
