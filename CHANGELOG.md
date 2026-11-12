# Changelog

All notable changes to VerySmartTV. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions: [Semantic Versioning](https://semver.org/). The three apps share one version; a tag `vX.Y.Z` builds
and publishes all three.

## [Unreleased]

### Scores companion
- **Follow badminton countries and players across tournaments, with a discipline filter** (ADR-051): under Badminton the picker gains **Countries** (about forty nations plus any seen this week, A–Z under letter headers; BWF website source only, since SportMicro's feed names no nationality) and **Players** (everyone on this week's matches, by country); OK on one opens a chooser, *All* / *Singles* / *Doubles* / *Mixed doubles*, and the row shows the filter. A match is followed when any follow covers it: India for singles plus two Chinese players for everything lists the union. The follows back up and restore with the rest; no extra request, the day-wide badminton fetch already covers every tournament.
- **Badminton source** row in Settings (ADR-049): *SportMicro (your RapidAPI key)*, the default and unchanged; *BWF website (experimental)*, which reads BWF's own Match Centre through a hidden WebView, no key, live matches only, more memory for a few seconds per refresh and liable to stop when the site changes; *Off*. The key row shows only under SportMicro and the key is asked for only then.
- "BWF site unavailable" row on the companion's Home, and in the picker's Badminton column, with the reason, when the experimental source fails; the other sources and the launcher are untouched.
- `StateNormaliser` reads the Match Centre's words (`In Progress`, `Waiting to start`, `Warm up`, `Completed`); `tools/dev/record_bwf_fixtures.sh` records the page's JSON through the emulator's WebView, and a live day recorded on 2026-11-12 is in the parser's fixtures.
- The BWF page loader runs in a `:bwf` process that exits after each capture (ADR-050): the companion's own process never creates a WebView, and on the 1 GB emulator it reads 61.7 MB thirty seconds after a visit against 66–67 MB before, where the one-process version kept about 40 MB. The client gives up after 45 s and reports "BWF site unavailable"; only a foreground refresh starts the process. The Settings row says so instead of naming a retained figure.
- Review of the BWF source (2026-11-11): the hidden view reaches only `bwfbadminton.com` hosts and Cloudflare's challenge host, with file and content access off; the background job never runs it (the 6 h refresh and the one-off before a start fetch the other sports only); a tournament whose live-match call fails is retried once and then skipped instead of failing the whole visit; the parser reads the players' full display names, a `0` third game while *Waiting to start* as not begun (the interval, not an upcoming match), and a warm-up's start as now rather than the day's start; the Settings row says about 40 MB stays until the app is closed.

## [0.1.0] - 2026-11-10

First release. `com.itsluminous.verysmarttv`, `com.itsluminous.traktshelf`, `com.itsluminous.livescore`,
versionCode 1000100, one release key for all three (signer SHA-256
`15d5085a0400d720d4aea65edab4df72c6e5a5313844715c3e56859cec99c21d`).

### Launcher
- Frosted-glass home screen for Android TV 8.0+, built for 1 GB TVs; no `INTERNET` permission, no background services.
- App grid with uniform rows, a top shelf naming the focused app or showing the Trakt carousel, Inputs / network / Settings pills, clock and two-line date, Clock screensaver.
- RAM and Storage bars under the clock (one toggle).
- Live-score lines under the top-right pills: up to **Live score rows** (1–5, default 2), sport icon per line, Up from the grid lands on them, Up again on Inputs, OK opens the match; hidden when the Scores app is absent.
- Settings panel: TV system settings, Reorder apps, Apps per row, Hero banner height, Trakt recommendations, Clock, System bars, Live scores, Live score rows, Animations, Choose default home, Backup & restore.
- Backup & restore to a JSON file (includes Scores follows; restores a backup made by the upstream launcher this one derives from).
- Themes pushed over ADB without a rebuild: `default`, `frosted`.
- 31 UI languages.

### Trakt companion
- Device-code sign-in (`trakt.tv/activate`), no client secret.
- Rows: Up next, Recommended shows/movies, Watchlist shows/movies, Trending, Recently watched; TMDB artwork, IMDb/RT/Trakt ratings.
- Details with Play (Stremio, CloudStream, NetMirror), Trailer (TizenTube, SmartTube), watchlist and mark-watched.
- Carousel feed for the launcher via a signature-protected provider; unreleased titles and already-watched titles are excluded; shuffled after the latest Up next.
- Sync every 6 hours.

### Scores companion
- Cricket, football, F1 and tennis from ESPN's public feeds, no key.
- Badminton from SportMicro's Badminton Micro API on RapidAPI with your own key, asked when badminton is first followed, changeable in Settings; 5-minute cadence (100 requests/day free plan).
- Follow sports, competitions, teams or single matches with three-state boxes.
- Scoreboard, settings, signature-protected provider for the launcher; refreshes only while a followed match is live and the launcher is reading.

### Setup tool
- `setup.sh` / `setup.ps1` (Python 3.9+, pure-Python ADB): find the TV, install the latest release with verified checksums, set Home behind a Home guard, push a theme.
- Debloat from 434 community lists (credited in `THIRD_PARTY_NOTICES.md`) matched to the TV; record written before any change; `restore` replays it; core Android packages refused; factory/service-menu packages need a typed yes.
- Subcommands: `discover`, `probe`, `install`, `theme`, `debloat`, `restore`, `check`, `screenshot`, `adb-closed`.

### Build
- One Gradle project; `./gradlew ci` runs unit tests, lint, file-length and identifier gates; CI runs the Python suite on Ubuntu and Windows and Espresso on an Android TV emulator; `release.yml` signs on `v*` tags.
- Earlier candidates `v1.0.0-rc1..rc3` and `v0.1.0-rc1..rc2` were published on the same day while the first release was being prepared; their pages remain.

[Unreleased]: https://github.com/itsluminous/verysmarttv/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/itsluminous/verysmarttv/releases/tag/v0.1.0
