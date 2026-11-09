# Companion providers

The launcher has no network. What it shows from the two companions it reads through content providers whose
contracts live in `:core` as Java constants that both sides compile against (DESIGN.md §12): a column renamed in
one place fails the other's build. This page is the human-readable form for another launcher or app that wants to
read the same providers. The source of truth is the class; when the two differ, the class wins.

| Contract | Companion | Class | Status |
|---|---|---|---|
| HeroContract | `com.itsluminous.traktshelf` | [`core/.../contract/HeroContract.java`](../core/src/main/java/com/itsluminous/verysmarttv/core/contract/HeroContract.java) | carried, Phase 1 |
| ScoreContract | `com.itsluminous.livescore` | [`core/.../contract/ScoreContract.java`](../core/src/main/java/com/itsluminous/verysmarttv/core/contract/ScoreContract.java) | the class exists; the section here lands with the companion in Phase 4 |

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

Authority `com.itsluminous.livescore.scores`, permissions `com.itsluminous.livescore.permission.READ_SCORES` and
`WRITE_SCORES` (the latter for `call("restore")`). The companion and this section land in Phase 4; until then the
class in `:core` and DESIGN.md §12.2 are the reference.
