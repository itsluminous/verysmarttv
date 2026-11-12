# Changelog

All notable changes to VerySmartTV. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions: [Semantic Versioning](https://semver.org/). The three apps share one version; a tag `vX.Y.Z` builds
and publishes all three.

## [Unreleased]

### Build and CI
- The Espresso job cannot hang: the launcher's and livescore's connected suites run one after the other (they share the emulator and the `com.itsluminous.livescore` package), the emulator's stock launcher stays disabled for the whole run instead of coming back between tests with a dialog over the screen, the runner fails any test after 3 minutes, Gradle runs under `timeout 40m` with `--continue`, and a push to `main` no longer cancels the previous commit's run (ADR-052).
- Tests that need the internet carry `@RequiresNetwork` and run only with `-PincludeNetworkTests`.

## [0.1.2] - 2026-11-12

versionCode 1000102, same release key.

### Scores companion
- Follow badminton **countries** and **players** across all tournaments; follows are a union (India + two Chinese players = every Indian player's matches plus those players' matches).
- A discipline filter per country/player follow: All, Singles, Doubles, Mixed doubles.
- Picker under Badminton gains Countries (A–Z, ~60 nations) and Players (grouped by country, from current tournaments).
- **Badminton source** row in Settings: SportMicro with your RapidAPI key (default) or **BWF website (experimental)**, which reads BWF's own Match Centre through a hidden browser view; no key. Country follows need the BWF source (SportMicro carries no nationality).
- The BWF loader runs in a separate `:bwf` process that exits after each capture, so the companion's memory returns (≈61 MB after vs 68 MB before on a 1 GB emulator); it never runs from the background job or while the screen is off.
- BWF source hardened: reaches only `bwfbadminton.com` and Cloudflare's challenge host, file/content access off, cache and cookies wiped, 45 s timeout; per-tournament fetch with one retry when the API answers without CORS headers.
- "BWF site unavailable" row with the reason when the experimental source fails; other sports unaffected.
- Backup/restore carries the new follow kinds; older follows still load.

### Fixed
- Discipline lists in the picker read "Doubles, Mixed doubles" (separator was trimmed).

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

[Unreleased]: https://github.com/itsluminous/verysmarttv/compare/v0.1.2...HEAD
[0.1.2]: https://github.com/itsluminous/verysmarttv/releases/tag/v0.1.2
[0.1.0]: https://github.com/itsluminous/verysmarttv/releases/tag/v0.1.0
