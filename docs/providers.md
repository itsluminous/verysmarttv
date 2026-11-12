# Companion providers

The launcher has no network. What it shows from the two companions it reads through content providers whose
contracts live in `:core` as Java constants that both sides compile against (DESIGN.md §12): a column renamed in
one place fails the other's build. This page is the human-readable form for another launcher or app that wants to
read the same providers. The source of truth is the class; when the two differ, the class wins.

| Contract | Companion | Class | Status |
|---|---|---|---|
| HeroContract | `com.itsluminous.traktshelf` | [`core/.../contract/HeroContract.java`](../core/src/main/java/com/itsluminous/verysmarttv/core/contract/HeroContract.java) | carried, Phase 1 |
| ScoreContract | `com.itsluminous.livescore` | [`core/.../contract/ScoreContract.java`](../core/src/main/java/com/itsluminous/verysmarttv/core/contract/ScoreContract.java) | the launcher's reader and backup side, Phase 3; the companion's provider, Phase 4 |

## Permissions (both contracts)

Each provider is guarded by a custom permission the companion declares with `protectionLevel="signature"` and the
launcher requests with `<uses-permission>` (§12.3, ADR-016). Only an app signed with the companion's key reads a
user's Trakt rows or followed teams. The three apps of this repository are signed with one key, so the launcher
built here reads the companions built here; a launcher built from source by someone else does not read a companion
from our Releases, and must build all three (docs/BUILDING.md). A refused query throws `SecurityException` on the
reader's side; the launcher logs it once and behaves as if the companion were absent. `adb shell content query` is
refused for the same reason.

## HeroContract (traktshelf → launcher)

Authority `com.itsluminous.traktshelf.hero`, read permission `com.itsluminous.traktshelf.permission.READ_HERO`.
Read-only: `insert`, `update` and `delete` return nothing. There is no backup path: the companion's state is OAuth
tokens, which are never backed up (ADR-032).

| URI | Returns |
|---|---|
| `content://com.itsluminous.traktshelf.hero/items` | The ranked titles for the launcher's carousel, one row per title with the columns below. MIME type `vnd.android.cursor.dir/vnd.com.itsluminous.traktshelf.hero.item`. Observers are notified after every sync and after a Settings change in the companion. As many rows as the companion's **Carousel items** setting (12 by default, up to 20); the launcher reads at most 20. |
| `content://com.itsluminous.traktshelf.hero/image/backdrop/<_id>` | The backdrop JPEG through `openFile`, mode `r` only; MIME `image/jpeg`. |
| `content://com.itsluminous.traktshelf.hero/image/poster/<_id>` | The poster JPEG, the same way. |

The `poster_path` and `backdrop_path` columns already hold those image URIs, so a reader opens them with
`ContentResolver.openInputStream` and never needs a file path or a file permission.

Columns of `/items`, in cursor order:

| Column | Type | What |
|---|---|---|
| `_id` | long | Row id, stable across syncs: the Trakt id with bit 40 set for a show. |
| `kind` | text | `movie` or `show`. |
| `title` | text | Title. |
| `year` | int | Release year, or 0. |
| `synopsis` | text | TMDB overview. |
| `imdb_rating` | text | IMDb rating as OMDb gives it (`8.1`), or empty. |
| `rt_rating` | text | Rotten Tomatoes score (`94%`), or empty. |
| `trakt_rating` | int | Trakt rating as a percent, 0 to 100. |
| `imdb_id` | text | `tt0111161`, or empty. |
| `trakt_id` | long | Trakt id. |
| `poster_path` | text | `content://` URI of the poster JPEG (above). |
| `backdrop_path` | text | `content://` URI of the backdrop JPEG (above). |
| `source_row` | text | Which companion row the title came from: `up_next`, `rec_shows`, `rec_movies`, `watchlist_shows`, `watchlist_movies`, `trending`, `history`. |
| `rank` | int | Position in the carousel order, 0 first. |
| `meta` | text | The companion's ready-made one-line meta text. The launcher renders it; it never rebuilds it (ADR-015). |
| `next_episode` | text | Next episode to watch (`S2 E4`), or empty. |

Order: rank 0 is the Up next episode of the show watched most recently; the rest is a shuffle of the rows ticked
in the companion's **Carousel rows**, each title once, titles not released yet and titles without a backdrop left
out, seeded with the sync time so the order holds between queries and changes with every sync.

Opening a title: the launcher starts `com.itsluminous.traktshelf/.DetailsActivity` (`HeroContract.DETAILS_ACTIVITY`)
with the extras `trakt_id` (long) and `kind` (`movie` or `show`), `HeroContract.EXTRA_TRAKT_ID` and
`HeroContract.EXTRA_KIND`.

How the launcher reads it (`HeroFeed`): a `ContentObserver` on `/items`, re-queried on change, coalesced to 800 ms;
backdrops are decoded on demand into an LRU of three; the reader is started when Home is resumed with `hero_feed`
on and released when Home stops or memory is trimmed.

## ScoreContract (livescore → launcher)

Authority `com.itsluminous.livescore.scores`, read permission `com.itsluminous.livescore.permission.READ_SCORES`,
write permission `com.itsluminous.livescore.permission.WRITE_SCORES` (for `call("restore")` only). Both
`protectionLevel="signature"`. This section is what the launcher reads and sends; the companion's own side (what
it fetches, how often, the `/events` rows its scoreboard uses) is documented with it.

| URI | Returns |
|---|---|
| `content://com.itsluminous.livescore.scores/pill` | One row per event the pill can show, the columns below, in the companion's order: the pill's own first (`PillChooser.choose`: live, then a break, then upcoming within 12 h, then final within 6 h; with two live matches of one follow reason it turns every 20 s), then every other eligible event in that same rank order (`PillChooser.ranked`). The launcher shows the first `score_rows` (1 to 5, `ScoreContract.MAX_ROWS`) of them, so the companion never needs to know how many lines a launcher has; a one-line launcher reads row 0 as before. MIME type `vnd.android.cursor.item/vnd.com.itsluminous.livescore.pill`. Always answerable from the companion's cache, never blocks on a fetch; exactly one row with `state` `none` when nothing followed is in the window. A query is also the companion's signal that a screen is looking. Observers are notified after every refresh and after the follow list changes. |
| `content://com.itsluminous.livescore.scores/events`, `…/events/<event_id>` | The followed events in the window, one row each (`ScoreContract.EVENT_COLUMNS`); the launcher does not read them. |
| `content://com.itsluminous.livescore.scores/backup` | Exactly one row, column `json`: the companion's follow list as the JSON it stores, opaque to the launcher. |

Columns of `/pill`, in cursor order (`ScoreContract.PILL_COLUMNS`):

| Column | Type | What |
|---|---|---|
| `state` | text | `live`, `break`, `upcoming`, `final`, or `none`. |
| `kicker` | text | The short text before the line, in the state colour: `LIVE`, the break's word (`STUMPS`, `HT`), a start time (`19:30`), `FT`. May be empty. |
| `line` | text | The one-line text, at most 48 characters, already shortened by the companion: `T20I · AFG 19/4 (6) v ZIM`. |
| `event_id` | text | The feed's event id, or null when `state` is `none`. |
| `sport` | text | One of `ScoreContract.SPORTS`: `cricket`, `football`, `f1`, `tennis`, `badminton`, `basketball`, `hockey`. The companion publishes the five it has a source for (cricket, football, f1, tennis from ESPN; badminton from SportMicro's Badminton Micro API on RapidAPI, ADR-044); the launcher picks each line's icon by this value and shows a generic one for anything outside the set, so a companion may add a sport before the launcher learns it. |
| `competition` | text | Competition name as shown (`IPL`). |
| `updated_ms` | long | Epoch milliseconds of the fetch the row comes from. |
| `stale` | int | 1 when `updated_ms` is older than the refresh interval for the state. |
| `color` | text | `#RRGGBB` for the kicker. |

How the launcher reads it (`feed.ScoreFeed`, with `score_pill` on): a `ContentObserver` on `/pill`, re-queried on
change and coalesced to 800 ms; the query runs off the main thread and the rows (at most `MAX_ROWS`, `none` rows
left out) are handed to the pill on it. While any row said `live` or `break` and Home is resumed the launcher also
re-queries every 60 seconds, which is how the companion knows a screen is watching; nothing is read while Home is
paused, while the setting is off, or after a `SecurityException` (another signing key), which is logged once. The
launcher renders each row as it comes (`topbar.ScoreLine`, `topbar.ScoreLineView`, ADR-015): it adds only the state
dot, the colours, a cut at 48 characters with an ellipsis, a dim while `stale` is 1, and the row's sport icon at the
end of the line (`topbar.SportIcons`, ADR-042: `ic_sport_<sport>` from `sport`, `ic_sport_other` for a value it does
not know; the line's content description ends in the sport's name); `ScoreLine.stack` cuts the list to `score_rows`
and keeps the lines in place while the companion only turns its first row (ADR-041). A list of `none` rows, an absent
provider and a refusal all hide the pill.

Opening the match: OK on a line starts `com.itsluminous.livescore/.ScoreboardActivity`
(`ScoreContract.SCOREBOARD_ACTIVITY`) with that line's `event_id` (`ScoreContract.EXTRA_EVENT_ID`), or the app's
leanback launch intent when the row has no event; a long press starts `com.itsluminous.livescore/.FollowActivity`
(`ScoreContract.FOLLOW_ACTIVITY`).

Backup (`backup.Companions`, ADR-032): the launcher's Backup document carries the `/backup` row under
`companions.livescore.json`, verbatim; Restore hands it back with
`call("restore", null, Bundle{"json": …})` on the same authority, which answers `Bundle{"ok": true}` or
`{"ok": false, "error": …}`. When the companion is not installed the section is left out of the export and skipped on
restore, with one log line; the launcher's own sections are unaffected.

### The companion's side (`:livescore`, Phase 4)

What answers the provider is `ScoreProvider`, backed by `ScoreRepo`: the follow list (`files/follows.json`), the
parsed events (`files/events.json`), the sources behind one `SourceRegistry` (`source/espn/EspnSource`, ESPN's public
site feeds, ADR-012, for cricket, football, F1 and tennis; `source/sportmicro/SportMicroSource`, SportMicro's Badminton Micro
API through RapidAPI with the user's own key, ADR-044; `source/bwf/BwfWebViewSource`, BWF's own Match Centre through a hidden
WebView, experimental and off by default, ADR-049; **Settings → Badminton source** picks which of the two answers badminton, or
neither) and the refresh decisions (`RefreshPolicy`, DESIGN.md §13.5, ADR-014).

- **Where the rows come from.** `/pill` is `PillChooser` over the followed events in the window (yesterday 00:00 to
  tomorrow 23:59 local): live, then break, then upcoming within 12 h, then final within 6 h; ties by follow reason
  (`event` > `team` > `competition`), then earliest start; two live matches followed for the same reason take 20 s
  turns, the companion notifying between them for as long as something read the pill in the last 90 s. The `line`
  and `kicker` are `LineFormatter`'s: cricket `T20I · AFG 19/4 (6) v ZIM` / `Test · BAN 386 & 49/2 v WI` /
  `T20I · AFG won by 23 runs`; football `Premier League · ARS 2–1 CHE 67'`, upcoming `ISL · Bengaluru v Mumbai City`;
  F1 `F1 · São Paulo GP · Lap 32 · VER leads`, `F1 · São Paulo GP · Race · Norris wins`; tennis
  `Stockholm Open · Gojo 7-5 4-6 7-6 Brunold`; badminton `Japan Masters · Sindhu 21-18 11-9 Axelsen` (a pair by
  surnames, `Chang / Yang`, or by abbreviations, `CHA/YAN`, when the surnames would not fit; a walkover
  `… Antonsen w/o Christie`, a retirement `… Christie 21-18 5-3 Antonsen ret.`). Kickers: `LIVE` (`#E53935`), the feed's break word in capitals
  (`STUMPS`, `RAIN`, `HT`, `RED FLAG`, badminton's `INTERVAL` between games, `#FFB300`), the local start time (`19:30`, `TOMORROW 19:30`, `SAT 19:30`),
  `FT` / `RESULT` / `FINAL` (`#9E9E9E`). `stale` is 1 when the row is older than twice its refresh interval
  (60 s live or break, 15 min for an upcoming event within a day, 6 h otherwise).
- **`/events` columns** come straight from the source-neutral `Event`: `home_score`/`away_score` are the feed's own
  strings (`386 & 184/4 (59 ov, target 369)`), `format` the cricket format, `status_detail` the feed's words
  (`Stumps`, `FT`, `Lap 32`, `Round 2 · Set 3`), `summary` its sentence. `end_ms` of a final is when the companion first
  saw it final, not the feed's scheduled last day (ESPN's cricket `endDate` is the fixture's end), so "shown for 6 h
  after the end" counts from the real end.
- **What a read triggers.** The query is answered from the cache at once. Then, if Live refresh is on, the companion
  fetches every competition with a live or break followed event whose last fetch is older than 55 s, and every
  competition with a followed event starting within 15 min (or started up to 3 h ago while the cache still says
  upcoming) whose fetch is older than 15 min; one fetch per competition, cricket's scorepanel and badminton's day list
  once for all. Nothing is
  fetched offline, while a fetch runs, or inside a failure's backoff (1, 2, 4, 8 min). With no screen reading, a
  one-off JobScheduler job runs 2 min before the next followed start and 3 min after it, and a periodic job every 6 h
  refreshes the fixture list; both need a network.
- **Fetches.** Cricket: `site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=yyyyMMdd` (today).
  Football: `site.api.espn.com/apis/site/v2/sports/soccer/<league>/scoreboard?dates=yyyyMMdd`, today on a live refresh,
  today and tomorrow on a full one (the bare URL answers ESPN's own current day, a range is refused). F1
  `…/racing/f1/scoreboard?dates=<yesterday>-<tomorrow>`, tennis `…/tennis/<atp|wta>/scoreboard?dates=…`, tennis
  only while a tennis follow exists. Badminton: `badminton-micro.p.rapidapi.com/matches-by-date?date=eq.yyyy-MM-dd`
  (today on a live refresh, today and tomorrow on a full one), with the headers `x-rapidapi-key` (the key from
  **Settings → Badminton data key (RapidAPI · Badminton Micro)**, stored in the app's preferences, never in the APK, a
  backup, a build variable or a CI secret) and `x-rapidapi-host`; without a key badminton is not fetched at all, and
  the picker's Badminton column says so. Badminton is fetched **at most every 5 minutes** whatever screen shows it
  (the other sports refresh at 60 s while live): the free plan is 100 requests a day. The
  open scoreboard also reads the cricket `summary` endpoint for the active batters and bowlers. Every body is parsed
  as a stream (`android.util.JsonReader`); the raw text is never held.
- **Badminton's key.** SportMicro's Badminton Micro API is listed on RapidAPI
  (`rapidapi.com/sportmicro-sportmicro-default/api/badminton-micro`): sign in to RapidAPI, subscribe to the API's free
  plan, and copy the key the playground shows as `x-rapidapi-key` into the companion: it asks for the key the moment
  you follow anything badminton (Cancel leaves badminton unfollowed), and **Settings → Badminton data key** shows its
  last four characters, changes or removes it (removing keeps your follows; they read "need a data key" until a key
  is back). **Enter your own key;
  it may cost money beyond the free tier.** The free plan is 100 requests a day (`x-ratelimit-requests-limit: 100`,
  measured 2026-11-10); a live badminton match costs one request every 5 minutes while a screen shows it and a full
  refresh two (today and tomorrow), so a long match day fits. If the quota does run out the feed answers 429 and the
  companion backs off (1, 2, 4, 8 min) with the last scores marked stale; whether requests past the quota are refused or
  billed depends on the plan you subscribed to on RapidAPI, which the companion cannot see. The key is only ever
  entered on the TV: it is not a GitHub secret, a `BuildConfig` field or a CI variable of this repository, and the
  release APKs carry none. Badminton has no static catalogue: the picker's Badminton column lists the tournaments of
  today and tomorrow from the feed (fetched once when the column is first opened with a key; one row per discipline,
  `Kumamoto Masters Japan, WS, Qualifying`) and, under a tournament, its players and matches; "All Badminton" follows
  everything.
- **Badminton from BWF's website (experimental).** **Settings → Badminton source → BWF website (experimental)** replaces
  SportMicro with `BwfWebViewSource` (ADR-049): no key. On each badminton fetch the companion creates one hidden
  `WebView` on the main thread, loads `https://match-centre.bwfbadminton.com/` (BWF's Match Centre; the plain
  `HttpURLConnection` path meets a Cloudflare JavaScript challenge there, the TV's browser engine does not), waits for
  the page (not Cloudflare's "Just a moment…" interstitial), and runs one script that calls the page's own `fetch` for
  `extranet-lv.bwfbadminton.com/api/match-center/vue-current-live` (the tournaments live this week) and
  `vue-live-matches?tmtId=<id>&tmtType=0` for up to four of them, one at a time (a tournament whose call fails is
  retried once with a cache-busting parameter, then skipped), handing the raw JSON to a `@JavascriptInterface` bridge;
  30 s timeout; then the WebView is destroyed, its cache, cookies and web storage deleted, and Chromium told to trim its
  memory. The view can reach only `bwfbadminton.com` hosts and Cloudflare's challenge host: every other request the page
  makes (fonts, icon font, analytics, tag manager) is answered empty on the TV, file and content URLs are off. The page
  lists the **matches on court** (warming up, playing, waiting between games, just finished), not the day's order of
  play or results, so this source yields live, break and just-finished matches, and an upcoming one only for a match on
  court that has not begun. Badminton keeps its 5-minute live cadence under this source (a Chromium page load a minute
  is too much for a 1 GB TV), the two days of a full refresh share one visit, and the rest of the refresh policy is
  unchanged, except that the **background job never runs this source**: the 6 h refresh and the one-off before a start
  fetch the other sports and leave badminton to the next screen that shows a score, so no hidden browser runs while the
  TV may be asleep. Any failure (challenge not cleared, timeout, no network, an HTTP error, a changed shape) is logged,
  backed off and shown as **"BWF site unavailable"** with the reason on the companion's Home and in the picker's
  Badminton column; the launcher's pill simply has no badminton line. Memory on `HikViewer_TV_1GB` (2026-11-11,
  `dumpsys meminfo`, TOTAL PSS of the companion with its Home open): 73 MB before the visit, a peak of 115 MB while the
  page loads, 114 MB once the WebView is destroyed, and that stays for the life of the process (26 MB of it private
  dirty; the rest is the WebView provider's code mapped into the process, clean pages the kernel can drop under
  pressure); the process is a background one the system may kill between fetches. **Off** stops badminton being
  fetched or shown at all. Re-record the fixtures with `tools/dev/record_bwf_fixtures.sh` (an emulator with internet).
- **Follows** (`FollowStore`, §13.2): `{kind: sport|competition|team|event, sport, competition, team, event, label,
  expires}` rows under `{"version": 1, "follows": [...]}`, at most 100; the first run writes the defaults (India, IPL,
  ISL, Premier League, Champions League, F1; ADR-033). `/backup` returns that file verbatim; `restore` replaces it,
  refuses anything that is not a version-1 follow list, and drops rows with an unknown kind or sport.
- **Opening.** Besides the two components above, the companion answers the action
  `com.itsluminous.livescore.action.OPEN` (`ScoreContract.ACTION_OPEN`): with `event_id` it shows that scoreboard,
  without one its Home.
