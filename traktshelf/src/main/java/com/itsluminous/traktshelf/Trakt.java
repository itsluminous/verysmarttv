package com.itsluminous.traktshelf;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trakt API v2 reads and the three writes (watchlist add/remove, mark watched). */
final class Trakt {
    private static final String TAG = "TraktShelf.Trakt";
    private final Auth auth;

    Trakt(Auth auth) {
        this.auth = auth;
    }

    private Map<String, String> headers(boolean withUser) {
        Map<String, String> h = new HashMap<>();
        h.put("trakt-api-version", "2");
        h.put("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID);
        if (withUser && auth.accessToken() != null) h.put("Authorization", "Bearer " + auth.accessToken());
        return h;
    }

    private JSONArray getArray(String path, boolean withUser) throws IOException, JSONException {
        Http.Response r = Http.get(Auth.API + path, headers(withUser));
        if (r.code == 401 && withUser) throw new Unauthorized();
        if (!r.ok()) throw new IOException("GET " + path + " answered " + r.code);
        return new JSONArray(r.body);
    }

    private JSONObject getObject(String path, boolean withUser) throws IOException, JSONException {
        Http.Response r = Http.get(Auth.API + path, headers(withUser));
        if (r.code == 401 && withUser) throw new Unauthorized();
        if (!r.ok()) throw new IOException("GET " + path + " answered " + r.code);
        return new JSONObject(r.body);
    }

    static final class Unauthorized extends IOException {
        Unauthorized() {
            super("Trakt answered 401");
        }
    }

    String username() throws IOException, JSONException {
        JSONObject s = getObject("/users/settings", true);
        JSONObject user = s.optJSONObject("user");
        return user == null ? null : user.optString("username", null);
    }

    /** Trending movies or shows, no account needed. */
    List<Item> trending(String kind, int limit) throws IOException, JSONException {
        JSONArray a = getArray("/" + kind + "s/trending?limit=" + limit + "&extended=full", false);
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject media = a.getJSONObject(i).optJSONObject(kind);
            if (media != null) out.add(Item.fromTrakt(media, kind));
        }
        return out;
    }

    /** Personalised recommendations; needs the account. */
    List<Item> recommendations(String kind, int limit) throws IOException, JSONException {
        JSONArray a = getArray("/recommendations/" + kind + "s?limit=" + limit + "&ignore_collected=false&ignore_watchlisted=true&extended=full", true);
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(Item.fromTrakt(a.getJSONObject(i), kind));
        return out;
    }

    /**
     * The watchlist's shows or movies (kind): the first limit in Trakt's "added" or "released" order, newest first,
     * so a list longer than limit keeps the titles the Watchlist order setting puts first (Trakt's own default is
     * the list's rank, where a re-added title comes last and would drop off the row).
     */
    List<Item> watchlist(String kind, String sort, int limit) throws IOException, JSONException {
        JSONArray a = getArray("/sync/watchlist/" + kind + "s/" + sort + "/desc?extended=full&page=1&limit=" + limit, true);
        return mixed(a);
    }

    /** Recently watched: movies and the shows of recent episodes, each show once. */
    List<Item> history(int limit) throws IOException, JSONException {
        JSONArray a = getArray("/sync/history?extended=full&page=1&limit=" + (limit * 2), true);
        List<Item> items = mixed(a);
        List<Item> out = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (Item it : items) {
            if (seen.containsKey(it.key())) continue;
            seen.put(it.key(), Boolean.TRUE);
            out.add(it);
            if (out.size() >= limit) break;
        }
        return out;
    }

    private static List<Item> mixed(JSONArray a) throws JSONException {
        List<Item> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.getJSONObject(i);
            String type = e.optString("type", "");
            Item it = null;
            if ("movie".equals(type) && e.has("movie")) it = Item.fromTrakt(e.getJSONObject("movie"), Item.MOVIE);
            else if (e.has("show")) it = Item.fromTrakt(e.getJSONObject("show"), Item.SHOW);
            if (it == null) continue;
            it.listedAt = Item.date(Item.opt(e, "listed_at"));   // the watchlist's; history entries carry watched_at
            out.add(it);
        }
        return out;
    }

    /**
     * Every show the account has watched an episode of, newest last_watched_at first: Up Next walks it, and its
     * ids are the shows hidden from Trending and the recommendations (watchedKeys).
     */
    List<JSONObject> watchedShows() throws IOException, JSONException {
        JSONArray watched = getArray("/sync/watched/shows?extended=full,noseasons", true);
        List<JSONObject> entries = new ArrayList<>();
        for (int i = 0; i < watched.length(); i++) entries.add(watched.getJSONObject(i));
        entries.sort((x, y) -> y.optString("last_watched_at", "").compareTo(x.optString("last_watched_at", "")));
        return entries;
    }

    /** Every movie the account has watched, as Item keys (movie:trakt id); no extended data, so the call is cheap. */
    Set<String> watchedMovieKeys() throws IOException, JSONException {
        JSONArray watched = getArray("/sync/watched/movies", true);
        Set<String> out = new HashSet<>();
        for (int i = 0; i < watched.length(); i++) {
            JSONObject movie = watched.getJSONObject(i).optJSONObject("movie");
            long id = movie == null || movie.optJSONObject("ids") == null ? 0 : movie.optJSONObject("ids").optLong("trakt");
            if (id > 0) out.add(Item.MOVIE + ":" + id);
        }
        return out;
    }

    /** The keys (show:trakt id) of watchedShows' entries. */
    static Set<String> watchedShowKeys(List<JSONObject> entries) {
        Set<String> out = new HashSet<>();
        for (JSONObject e : entries) {
            JSONObject show = e.optJSONObject("show");
            long id = show == null || show.optJSONObject("ids") == null ? 0 : show.optJSONObject("ids").optLong("trakt");
            if (id > 0) out.add(Item.SHOW + ":" + id);
        }
        return out;
    }

    /**
     * Up Next: the shows watched most recently (watchedShows' order) that still have an aired, unwatched episode.
     * One progress call per show, so the list is capped: maxShows watched shows are looked at, limit are kept.
     */
    List<Item> upNext(List<JSONObject> entries, int maxShows, int limit) throws IOException, JSONException {
        List<Item> out = new ArrayList<>();
        int looked = 0;
        for (JSONObject e : entries) {
            if (looked++ >= maxShows || out.size() >= limit) break;
            JSONObject show = e.optJSONObject("show");
            if (show == null) continue;
            Item it = Item.fromTrakt(show, Item.SHOW);
            if (it.traktId == 0) continue;
            try {
                // extended=full puts first_aired on next_episode, so the carousel can skip an episode still to come.
                JSONObject p = getObject("/shows/" + it.traktId + "/progress/watched?hidden=false&specials=false&count_specials=false&extended=full", true);
                JSONObject next = p.optJSONObject("next_episode");
                if (next == null) continue;
                it.nextAired = Item.date(Item.opt(next, "first_aired"));
                it.aired = p.optInt("aired");
                it.completed = p.optInt("completed");
                it.nextSeason = next.optInt("season");
                it.nextNumber = next.optInt("number");
                String epTitle = Item.opt(next, "title");
                it.nextEpisode = String.format("S%02d E%02d", it.nextSeason, it.nextNumber) + (epTitle != null ? " · " + epTitle : "");
                out.add(it);
            } catch (IOException ex) {
                if (ex instanceof Unauthorized) throw ex;
                Log.w(TAG, "progress for " + it.title + ": " + ex.getMessage());
            }
        }
        return out;
    }

    // ---- writes ---------------------------------------------------------------------------------------
    // Each returns null when Trakt took the change, or a short reason for the UI ("answered 429", the IOException).

    private String post(String path, Item it) {
        try {
            JSONObject ids = new JSONObject().put("trakt", it.traktId);
            if (it.imdbId != null) ids.put("imdb", it.imdbId);
            JSONObject media = new JSONObject().put("ids", ids);
            JSONObject body = new JSONObject().put(Item.SHOW.equals(it.kind) ? "shows" : "movies", new JSONArray().put(media));
            return send(path, body);
        } catch (JSONException e) {
            return e.getMessage();
        }
    }

    private String send(String path, JSONObject body) {
        try {
            Http.Response r = Http.postJson(Auth.API + path, headers(true), body);
            if (r.ok()) return null;
            Log.w(TAG, "POST " + path + " answered " + r.code + ": " + r.body);
            return r.code == 401 ? "signed out" : "answered " + r.code;
        } catch (IOException e) {
            Log.w(TAG, "POST " + path + ": " + e.getMessage());
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    String addToWatchlist(Item it) {
        return post("/sync/watchlist", it);
    }

    String removeFromWatchlist(Item it) {
        return post("/sync/watchlist/remove", it);
    }

    /** A movie: watched now. A show with a next episode: that episode; otherwise the whole show. */
    String markWatched(Item it) {
        if (Item.SHOW.equals(it.kind) && it.nextSeason > 0) {
            try {
                JSONObject ep = new JSONObject().put("number", it.nextNumber);
                JSONObject season = new JSONObject().put("number", it.nextSeason).put("episodes", new JSONArray().put(ep));
                JSONObject show = new JSONObject().put("ids", new JSONObject().put("trakt", it.traktId))
                        .put("seasons", new JSONArray().put(season));
                return send("/sync/history", new JSONObject().put("shows", new JSONArray().put(show)));
            } catch (JSONException e) {
                return e.getMessage();
            }
        }
        return post("/sync/history", it);
    }
}
