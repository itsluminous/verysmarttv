package com.itsluminous.traktshelf;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.itsluminous.verysmarttv.core.contract.HeroContract;

import java.util.ArrayList;
import java.util.List;

/** One movie or show, as the feed stores it: Trakt ids and text, TMDB art and cast, OMDb ratings. */
final class Item {
    static final String MOVIE = HeroContract.KIND_MOVIE, SHOW = HeroContract.KIND_SHOW;

    String kind = MOVIE;
    String title = "";
    int year;
    long traktId;
    String imdbId;      // tt...
    long tmdbId;
    String slug;
    String overview = "";
    List<String> genres = new ArrayList<>();
    int runtime;        // minutes (an episode's for a show)
    float traktRating;  // 0..10
    int traktVotes;
    String imdbRating;  // "8.7" or null
    String rtRating;    // "97%" or null
    String trailerYoutubeId;
    String posterUrl, backdropUrl;   // TMDB (or Trakt fallback) image URLs
    List<String> cast = new ArrayList<>();
    String tagline;
    String status;      // show: "returning series" etc.
    String certification;
    /** Up Next only: "S02 E04 · Title". */
    String nextEpisode;
    int nextSeason, nextNumber;
    /** Up Next: completed / aired episodes, for a progress bar. */
    int aired, completed;
    /** When the metadata (TMDB, OMDb) was last fetched, for the 7-day refresh. */
    long enrichedAt;
    /** The Trakt row the item first came from; the provider reports it. */
    String sourceRow;
    /** Watchlist: when it was added (Trakt listed_at, epoch ms), for the "Date added" order. 0 when unknown. */
    long listedAt;
    /** Release day (a movie's released, a show's first_aired), epoch ms, for the "Release date" order. 0 when unknown. */
    long released;
    /** Up Next only: when the next episode first airs (Trakt first_aired), epoch ms. 0 when unknown. */
    long nextAired;

    /** Trakt show statuses that mean no episode has aired yet; a show with one and no first_aired is upcoming. */
    private static final List<String> UNAIRED_SHOW = java.util.Arrays.asList("in production", "planned", "upcoming");

    /**
     * True when the title has not come out yet: its release day, or for Up Next its next episode's air day, is after
     * today on this device's calendar (today counts as out), or a show with no first_aired whose Trakt status says it
     * has not aired. A title with no date and no such status is taken as out. The carousel leaves these out; Home
     * keeps them with a "Coming" kicker.
     */
    boolean upcoming() {
        if (nextAired > 0 && isAfterToday(nextAired)) return true;
        if (released > 0) return isAfterToday(released);
        return SHOW.equals(kind) && status != null && UNAIRED_SHOW.contains(status.toLowerCase(java.util.Locale.ROOT));
    }

    /** The day an upcoming title comes out, epoch ms, for the kicker: the next episode's air day when known. 0 when unknown. */
    long comingAt() {
        if (nextAired > 0 && isAfterToday(nextAired)) return nextAired;
        return released > 0 && isAfterToday(released) ? released : 0;
    }

    static boolean isAfterToday(long epochMs) {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        return java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().isAfter(java.time.LocalDate.now(zone));
    }

    /** A stable id for the provider: kind and Trakt id. */
    long stableId() {
        return (SHOW.equals(kind) ? 1L << 40 : 0L) | traktId;
    }

    String key() {
        return kind + ":" + traktId;
    }

    String posterFile() {
        return kind + "_" + traktId + "_p.jpg";
    }

    String backdropFile() {
        return kind + "_" + traktId + "_b.jpg";
    }

    String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (year > 0) sb.append(year);
        if (!genres.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            for (int i = 0; i < Math.min(2, genres.size()); i++) {
                if (i > 0) sb.append(", ");
                String g = genres.get(i);
                sb.append(Character.toUpperCase(g.charAt(0))).append(g.substring(1));
            }
        }
        if (runtime > 0) {
            if (sb.length() > 0) sb.append(" · ");
            if (runtime >= 60) {
                sb.append(runtime / 60).append(" h");
                if (runtime % 60 != 0) sb.append(" ").append(runtime % 60).append(" min");
            }
            else sb.append(runtime).append(" min");
        }
        return sb.toString();
    }

    String traktPercent() {
        return traktRating > 0 ? Math.round(traktRating * 10) + "%" : null;
    }

    JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("kind", kind).put("title", title).put("year", year).put("trakt", traktId).put("imdb", imdbId)
                .put("tmdb", tmdbId).put("slug", slug).put("overview", overview).put("genres", new JSONArray(genres))
                .put("runtime", runtime).put("trakt_rating", traktRating).put("trakt_votes", traktVotes)
                .put("imdb_rating", imdbRating).put("rt_rating", rtRating).put("trailer", trailerYoutubeId)
                .put("poster", posterUrl).put("backdrop", backdropUrl).put("cast", new JSONArray(cast))
                .put("tagline", tagline).put("status", status).put("certification", certification)
                .put("next_episode", nextEpisode).put("next_season", nextSeason).put("next_number", nextNumber)
                .put("aired", aired).put("completed", completed).put("enriched_at", enrichedAt).put("source_row", sourceRow)
                .put("listed_at", listedAt).put("released", released).put("next_aired", nextAired);
        return j;
    }

    static Item fromJson(JSONObject j) {
        Item it = new Item();
        it.kind = j.optString("kind", MOVIE);
        it.title = j.optString("title", "");
        it.year = j.optInt("year");
        it.traktId = j.optLong("trakt");
        it.imdbId = opt(j, "imdb");
        it.tmdbId = j.optLong("tmdb");
        it.slug = opt(j, "slug");
        it.overview = j.optString("overview", "");
        it.genres = strings(j.optJSONArray("genres"));
        it.runtime = j.optInt("runtime");
        it.traktRating = (float) j.optDouble("trakt_rating", 0);
        it.traktVotes = j.optInt("trakt_votes");
        it.imdbRating = opt(j, "imdb_rating");
        it.rtRating = opt(j, "rt_rating");
        it.trailerYoutubeId = opt(j, "trailer");
        it.posterUrl = opt(j, "poster");
        it.backdropUrl = opt(j, "backdrop");
        it.cast = strings(j.optJSONArray("cast"));
        it.tagline = opt(j, "tagline");
        it.status = opt(j, "status");
        it.certification = opt(j, "certification");
        it.nextEpisode = opt(j, "next_episode");
        it.nextSeason = j.optInt("next_season");
        it.nextNumber = j.optInt("next_number");
        it.aired = j.optInt("aired");
        it.completed = j.optInt("completed");
        it.enrichedAt = j.optLong("enriched_at");
        it.sourceRow = opt(j, "source_row");
        it.listedAt = j.optLong("listed_at");
        it.released = j.optLong("released");
        it.nextAired = j.optLong("next_aired");
        return it;
    }

    /** "2024-05-17" or "2024-05-17T01:00:00.000Z" (Trakt's dates) to epoch ms; 0 when absent or unreadable. */
    static long date(String iso) {
        if (iso == null || iso.length() < 10) return 0;
        try {
            if (iso.length() == 10) return java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (java.time.format.DateTimeParseException e) {
            return 0;
        }
    }

    /** Copies the metadata (not the row-specific Up Next fields) from a previously enriched copy. */
    void takeMetadataFrom(Item o) {
        if (o == null) return;
        if (imdbId == null) imdbId = o.imdbId;
        if (tmdbId == 0) tmdbId = o.tmdbId;
        if (overview.isEmpty()) overview = o.overview;
        if (genres.isEmpty()) genres = o.genres;
        if (runtime == 0) runtime = o.runtime;
        if (traktRating == 0) traktRating = o.traktRating;
        imdbRating = o.imdbRating;
        rtRating = o.rtRating;
        if (trailerYoutubeId == null) trailerYoutubeId = o.trailerYoutubeId;
        posterUrl = o.posterUrl;
        backdropUrl = o.backdropUrl;
        cast = o.cast;
        if (tagline == null) tagline = o.tagline;
        if (certification == null) certification = o.certification;
        enrichedAt = o.enrichedAt;
    }

    static String opt(JSONObject j, String key) {
        if (!j.has(key) || j.isNull(key)) return null;
        String s = j.optString(key, null);
        return s == null || s.isEmpty() || "null".equals(s) ? null : s;
    }

    static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, null);
            if (s != null && !s.isEmpty()) out.add(s);
        }
        return out;
    }

    /** Reads the Trakt ids/title block (a "movie" or "show" object with extended=full fields when present). */
    static Item fromTrakt(JSONObject media, String kind) {
        Item it = new Item();
        it.kind = kind;
        it.title = media.optString("title", "");
        it.year = media.optInt("year");
        JSONObject ids = media.optJSONObject("ids");
        if (ids != null) {
            it.traktId = ids.optLong("trakt");
            it.imdbId = opt(ids, "imdb");
            it.tmdbId = ids.optLong("tmdb");
            it.slug = opt(ids, "slug");
        }
        it.overview = media.optString("overview", "");
        it.genres = strings(media.optJSONArray("genres"));
        it.runtime = media.optInt("runtime");
        it.traktRating = (float) media.optDouble("rating", 0);
        it.traktVotes = media.optInt("votes");
        it.tagline = opt(media, "tagline");
        it.status = opt(media, "status");
        it.certification = opt(media, "certification");
        String trailer = opt(media, "trailer");
        if (trailer != null) it.trailerYoutubeId = youtubeId(trailer);
        it.released = date(opt(media, SHOW.equals(kind) ? "first_aired" : "released"));
        return it;
    }

    static String youtubeId(String url) {
        if (url == null) return null;
        int at = url.indexOf("v=");
        if (at >= 0) {
            String id = url.substring(at + 2);
            int amp = id.indexOf('&');
            return amp > 0 ? id.substring(0, amp) : id;
        }
        at = url.indexOf("youtu.be/");
        if (at >= 0) return url.substring(at + 9).split("[?&]")[0];
        return null;
    }
}
