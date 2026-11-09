package com.itsluminous.traktshelf;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TMDB (art, cast, genres, runtime, a trailer) and OMDb (IMDb and Rotten Tomatoes ratings) for one item. */
final class Metadata {
    private static final String TAG = "TraktShelf.Meta";
    private static final String TMDB = "https://api.themoviedb.org/3";
    static final String POSTER_BASE = "https://image.tmdb.org/t/p/w342";
    static final String BACKDROP_BASE = "https://image.tmdb.org/t/p/w780";

    private Metadata() {
    }

    static boolean tmdbConfigured() {
        return !BuildConfig.TMDB_API_KEY.isEmpty();
    }

    static boolean omdbConfigured() {
        return !BuildConfig.OMDB_API_KEY.isEmpty();
    }

    /** A v4 read access token (a long JWT) goes in a Bearer header; a v3 key (32 characters) as api_key. */
    private static Http.Response tmdb(String pathAndQuery) throws IOException {
        String key = BuildConfig.TMDB_API_KEY;
        Map<String, String> h = new HashMap<>();
        String url = TMDB + pathAndQuery;
        if (key.length() > 60) h.put("Authorization", "Bearer " + key);
        else url += (url.contains("?") ? "&" : "?") + "api_key=" + key;
        return Http.get(url, h);
    }

    /** Fills art, overview, genres, runtime, cast and trailer from TMDB. Returns false when nothing was found. */
    static boolean enrichTmdb(Item it) {
        if (!tmdbConfigured()) return false;
        try {
            boolean show = Item.SHOW.equals(it.kind);
            if (it.tmdbId == 0 && it.imdbId != null) {
                Http.Response f = tmdb("/find/" + it.imdbId + "?external_source=imdb_id");
                if (f.ok()) {
                    JSONArray res = f.json().optJSONArray(show ? "tv_results" : "movie_results");
                    if (res != null && res.length() > 0) it.tmdbId = res.getJSONObject(0).optLong("id");
                }
            }
            if (it.tmdbId == 0) return false;
            Http.Response r = tmdb("/" + (show ? "tv" : "movie") + "/" + it.tmdbId + "?append_to_response=credits,videos&language=en-US");
            if (!r.ok()) {
                Log.w(TAG, "tmdb " + it.kind + " " + it.tmdbId + " answered " + r.code);
                return false;
            }
            JSONObject j = r.json();
            String poster = Item.opt(j, "poster_path");
            String backdrop = Item.opt(j, "backdrop_path");
            if (poster != null) it.posterUrl = POSTER_BASE + poster;
            if (backdrop != null) it.backdropUrl = BACKDROP_BASE + backdrop;
            String overview = Item.opt(j, "overview");
            if (overview != null && (it.overview.isEmpty() || overview.length() > it.overview.length())) it.overview = overview;
            if (it.tagline == null) it.tagline = Item.opt(j, "tagline");
            JSONArray genres = j.optJSONArray("genres");
            if (genres != null && genres.length() > 0) {
                List<String> g = new ArrayList<>();
                for (int i = 0; i < genres.length(); i++) {
                    String name = genres.getJSONObject(i).optString("name", null);
                    if (name != null) g.add(name);
                }
                if (!g.isEmpty()) it.genres = g;
            }
            if (it.runtime == 0) {
                if (show) {
                    JSONArray rt = j.optJSONArray("episode_run_time");
                    if (rt != null && rt.length() > 0) it.runtime = rt.optInt(0);
                } else it.runtime = j.optInt("runtime");
            }
            if (it.imdbId == null) {
                String imdb = Item.opt(j, "imdb_id");
                if (imdb != null) it.imdbId = imdb;
            }
            JSONObject credits = j.optJSONObject("credits");
            if (credits != null) {
                JSONArray cast = credits.optJSONArray("cast");
                List<String> names = new ArrayList<>();
                if (cast != null) for (int i = 0; i < cast.length() && names.size() < 5; i++) {
                    String name = cast.getJSONObject(i).optString("name", null);
                    if (name != null && !name.isEmpty()) names.add(name);
                }
                if (!names.isEmpty()) it.cast = names;
            }
            if (it.trailerYoutubeId == null) {
                JSONObject videos = j.optJSONObject("videos");
                JSONArray results = videos == null ? null : videos.optJSONArray("results");
                String any = null;
                if (results != null) for (int i = 0; i < results.length(); i++) {
                    JSONObject v = results.getJSONObject(i);
                    if (!"YouTube".equals(v.optString("site"))) continue;
                    String key = v.optString("key", null);
                    if (key == null) continue;
                    if ("Trailer".equals(v.optString("type"))) {
                        it.trailerYoutubeId = key;
                        if (v.optBoolean("official")) break;
                    } else if (any == null) any = key;
                }
                if (it.trailerYoutubeId == null) it.trailerYoutubeId = any;
            }
            return true;
        } catch (IOException | JSONException e) {
            Log.w(TAG, "tmdb " + it.title + ": " + e.getMessage());
            return false;
        }
    }

    /** IMDb rating and Rotten Tomatoes score from OMDb, by IMDb id. */
    static void enrichOmdb(Item it) {
        if (!omdbConfigured() || it.imdbId == null) return;
        try {
            Http.Response r = Http.get("https://www.omdbapi.com/?apikey=" + BuildConfig.OMDB_API_KEY + "&i=" + it.imdbId + "&tomatoes=true", null);
            if (!r.ok()) {
                Log.w(TAG, "omdb " + it.imdbId + " answered " + r.code);
                return;
            }
            JSONObject j = r.json();
            if (!"True".equals(j.optString("Response"))) return;
            String imdb = Item.opt(j, "imdbRating");
            it.imdbRating = imdb == null || "N/A".equals(imdb) ? null : imdb;
            it.rtRating = null;
            JSONArray ratings = j.optJSONArray("Ratings");
            if (ratings != null) for (int i = 0; i < ratings.length(); i++) {
                JSONObject rr = ratings.getJSONObject(i);
                if ("Rotten Tomatoes".equals(rr.optString("Source"))) it.rtRating = Item.opt(rr, "Value");
            }
            if (it.rtRating == null) {
                String tm = Item.opt(j, "tomatoMeter");
                if (tm != null && !"N/A".equals(tm)) it.rtRating = tm + "%";
            }
            if (it.certification == null) {
                String rated = Item.opt(j, "Rated");
                if (rated != null && !"N/A".equals(rated)) it.certification = rated;
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "omdb " + it.imdbId + ": " + e.getMessage());
        }
    }
}
