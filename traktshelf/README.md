# Trakt shelf (`:traktshelf`, `com.itsluminous.traktshelf`)

The Trakt companion of VerySmartTV, carried from the owner's CleanYourTV fork (see
[THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)). Your Trakt account on the TV, in the launcher's language: a slate home with **Up next**, **Recommended
shows**, **Recommended movies**, **Watchlist · Shows**, **Watchlist · Movies**, **Trending** and **Recently
watched** as rows of posters, the
focused title named in large type over its backdrop with IMDb, Rotten Tomatoes and Trakt ratings, and a details
page whose **Play** goes to Stremio, CloudStream or NetMirror, **Trailer** to TizenTube or SmartTube.

It also feeds the launcher: with `"hero_feed": true` in a theme, the launcher's top shelf cycles through these
titles while the dock is at rest, read from this app's content provider (no network in the launcher).

## What it does

| Part | How |
|---|---|
| Sign in | Trakt's device flow on the TV: a code and `trakt.tv/activate`; approve on your phone. No browser on the TV, no client secret anywhere. Tokens refresh on their own; a refused refresh asks you to sign in again. |
| Sync | Every 6 hours while the TV is online (JobScheduler), after sign-in, and from Settings → Sync now. Trending needs no account. Up next, recommendations, watchlist and history need one. Each sync also reads what the account has watched (`/sync/watched/shows` and `/sync/watched/movies`, no extended data) and takes those titles out of **Trending**, **Recommended shows** and **Recommended movies**, on Home and in the carousel alike; a show with one episode seen counts as seen. Up next, the watchlist and Recently watched are untouched. The ids are kept in `feed.json`, and the log says how many went (`excluded 2 watched from trending/recommended: [...]`). |
| Details | Posters (TMDB w342), backdrops (w780), synopsis, genres, runtime, top-five cast and a trailer from TMDB; IMDb rating and Rotten Tomatoes from OMDb; Trakt's own rating. Cached seven days per title. The title on the hero and the details page stays on one line: it shrinks (40sp to 24sp on the hero, 46sp to 28sp on details) and then ends in an ellipsis, so a long one never pushes the synopsis down. |
| Watchlist rows | Two rows, **Watchlist · Shows** and **Watchlist · Movies**, each the 20 titles Trakt lists first in the **Watchlist order** (its `added` or `released` sort, newest first), so a list longer than 20 keeps the newest. A feed or a Carousel rows choice from before the split (one `watchlist` row) is read as both rows. |
| Cache | `files/feed.json`, `files/meta.json` and `files/img/`, trimmed to 140 MB by age. |
| Play | By IMDb id: Stremio's deep link (`stremio:///detail/movie/tt…/tt…`, and the next episode for a show), CloudStream's search (`cloudstreamsearch://title`), or NetMirror's home screen. A chooser when more than one is installed; Settings → Default player skips it. |
| Trailer | A YouTube watch URL to TizenTube when it is installed, SmartTube otherwise, with no chooser; when neither is there and more than one other app takes YouTube links, a chooser among those. |
| Settings | Account, Sync now, Default player (Ask each time or one of the installed players), **Carousel rows** (a tick list: Up next, Recommended shows, Recommended movies, Watchlist · Shows, Watchlist · Movies, Trending, Recently watched; all but Recently watched by default; a saved `watchlist` tick from before the split stands for both watchlist rows), **Carousel items** (6, 9, 12, 15 or 20; 12 by default), **Watchlist order** (Date added, newest first, or Release date, the titles still to come soonest first and then the released ones newest first; the Home rows and the carousel's watchlist items both follow it, and a change fetches the rows again), Sign out. The rows scroll, so Sign out is reachable on a 540dp screen. Kept in SharedPreferences; a change notifies the provider's observers, so the launcher's shelf follows at once. |
| Writes | Add to / remove from watchlist, mark watched (a show's next episode, or the movie). Off the main thread: the pill reads **Adding…**, **Removing…** or **Marking…** at once, then the new state (**Remove from watchlist**, **Watched ✓**), or its old label with Trakt's answer in a toast. A watchlist change goes into the feed and to the provider's observers before the sync that follows confirms it. |
| Provider | `content://com.itsluminous.traktshelf.hero/items`, read-only, exported, readable only by an app signed with this app's key (`com.itsluminous.traktshelf.permission.READ_HERO`, `protectionLevel="signature"`; the launcher holds it). The authority, URIs, columns and extras are `HeroContract` in `:core`, which both apps compile against ([docs/providers.md](../docs/providers.md)). Columns: `_id`, `kind`, `title`, `year`, `synopsis`, `imdb_rating`, `rt_rating`, `trakt_rating`, `imdb_id`, `trakt_id`, `poster_path`, `backdrop_path` (both `content://…/image/{poster,backdrop}/<_id>`, opened with `ContentResolver.openInputStream`), `source_row` (`up_next`, `rec_shows`, `rec_movies`, `watchlist_shows`, `watchlist_movies`, `trending`, `history`), `rank`, `meta`, `next_episode`. As many items as Settings → Carousel items asks (12 by default). Rank 0 is the Up next episode of the show watched most recently (whatever rows are ticked); the rest is a shuffle of the rows ticked in Carousel rows, no row grouping, every eligible title of each row in the pool (each row capped at half the count, each title once, titles not out yet and titles without a backdrop left out), `java.util.Random` seeded with the sync time, so the order holds between queries and changes with every sync. Observers are notified after every sync and after a Settings change. |
| Card | Label "Trakt", caption "Up next and picks", accent `#ED1C24`. |

## Keys

Register a Trakt API app at [trakt.tv/oauth/applications](https://trakt.tv/oauth/applications): give it a name
and a redirect URI (`http://localhost` or `urn:ietf:wg:oauth:2.0:oob`; the device flow never uses it), and copy
only the **Client ID**, since this is a public (PKCE) app and the secret is never needed. Then a
[TMDB](https://www.themoviedb.org/settings/api) key (the free v3 API key) and an
[OMDb](https://www.omdbapi.com/apikey.aspx) key (the free tier, activated from its e-mail), and give them to
the build either as environment variables or in `secrets.env` at the repository root (next to
`settings.gradle.kts`; git-ignored), `KEY=value` lines:

```
TRAKT_CLIENT_ID=…
TMDB_API_KEY=…
OMDB_API_KEY=…
```

The environment wins over the file. Without either the app builds with empty keys and the sign-in screen says
so; the build log names the keys it found, never their values. `release.yml` passes the three GitHub secrets of
the same names.

## Build and install

```bash
./gradlew :traktshelf:assembleDebug          # traktshelf/build/outputs/apk/debug/traktshelf-debug.apk
adb install -r traktshelf/build/outputs/apk/debug/traktshelf-debug.apk
```

Then open **Trakt** on Home, press **Sign in**, and enter the code at `trakt.tv/activate`. The provider is
signature-guarded, so `adb shell content query` is refused; read it from the launcher's log instead
(`logcat -s VerySmartTV.HeroFeed`). Release APKs come from `./gradlew assembleRelease` (debug-signed unless a
keystore is configured, [docs/BUILDING.md](../docs/BUILDING.md)).

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | api.trakt.tv, api.themoviedb.org, image.tmdb.org, www.omdbapi.com. Nothing else. |
| `ACCESS_NETWORK_STATE` | JobScheduler's "only while online" constraint for the six-hourly sync. |

No analytics, no background service: the sync is a JobScheduler job and a thread while the app is open.

## Tested on

Xiaomi Mi TV 4A (MiTV-MOOQ1), Android TV 10, 1 GB class: a full account sync of 82 titles takes about a
minute and the app sits at 55 to 60 MB PSS with the rows on screen.

## Dependencies

`androidx.recyclerview` for the poster rows. Networking is `HttpURLConnection`, JSON is `org.json`, the
periodic sync is JobScheduler; the Outfit typeface is under the SIL Open Font License (`FONT-LICENSE-OFL.txt`).
This project uses the TMDB API but is not endorsed or certified by TMDB.
