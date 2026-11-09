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
| `content://com.itsluminous.livescore.scores/pill` | Exactly one row with the columns below: the text the launcher's score pill shows. MIME type `vnd.android.cursor.item/vnd.com.itsluminous.livescore.pill`. Always answerable from the companion's cache, never blocks on a fetch; `state` is `none` when nothing followed is in the window. A query is also the companion's signal that a screen is looking. Observers are notified after every refresh and after the follow list changes. |
| `content://com.itsluminous.livescore.scores/events`, `…/events/<event_id>` | The followed events in the window, one row each (`ScoreContract.EVENT_COLUMNS`); the launcher does not read them. |
| `content://com.itsluminous.livescore.scores/backup` | Exactly one row, column `json`: the companion's follow list as the JSON it stores, opaque to the launcher. |

Columns of `/pill`, in cursor order (`ScoreContract.PILL_COLUMNS`):

| Column | Type | What |
|---|---|---|
| `state` | text | `live`, `break`, `upcoming`, `final`, or `none`. |
| `kicker` | text | The short text before the line, in the state colour: `LIVE`, the break's word (`STUMPS`, `HT`), a start time (`19:30`), `FT`. May be empty. |
| `line` | text | The one-line text, at most 48 characters, already shortened by the companion: `T20I · AFG 19/4 (6) v ZIM`. |
| `event_id` | text | The feed's event id, or null when `state` is `none`. |
| `sport` | text | `cricket`, `football`, `f1`, `tennis`, `basketball`. |
| `competition` | text | Competition name as shown (`IPL`). |
| `updated_ms` | long | Epoch milliseconds of the fetch the row comes from. |
| `stale` | int | 1 when `updated_ms` is older than the refresh interval for the state. |
| `color` | text | `#RRGGBB` for the kicker. |

How the launcher reads it (`feed.ScoreFeed`, with `score_pill` on): a `ContentObserver` on `/pill`, re-queried on
change and coalesced to 800 ms; the query runs off the main thread and the row is handed to the pill on it. While the
last row said `live` or `break` and Home is resumed the launcher also re-queries every 60 seconds, which is how the
companion knows a screen is watching; nothing is read while Home is paused, while the setting is off, or after a
`SecurityException` (another signing key), which is logged once. The launcher renders the row as it comes
(`topbar.ScoreLine`, `topbar.ScorePill`, ADR-015): it adds only the state dot, the colours, a cut at 48 characters
with an ellipsis, and a dim while `stale` is 1. A row with `state` `none`, an absent provider and a refusal all hide
the pill.

Opening the match: OK on the pill starts `com.itsluminous.livescore/.ScoreboardActivity`
(`ScoreContract.SCOREBOARD_ACTIVITY`) with the extra `event_id` (`ScoreContract.EXTRA_EVENT_ID`), or the app's
leanback launch intent when the row has no event; a long press starts `com.itsluminous.livescore/.FollowActivity`
(`ScoreContract.FOLLOW_ACTIVITY`).

Backup (`backup.Companions`, ADR-032): the launcher's Backup document carries the `/backup` row under
`companions.livescore.json`, verbatim; Restore hands it back with
`call("restore", null, Bundle{"json": …})` on the same authority, which answers `Bundle{"ok": true}` or
`{"ok": false, "error": …}`. When the companion is not installed the section is left out of the export and skipped on
restore, with one log line; the launcher's own sections are unaffected.
