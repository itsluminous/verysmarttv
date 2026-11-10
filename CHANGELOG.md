# Changelog

All notable changes to VerySmartTV. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the versions [Semantic Versioning](https://semver.org/) as [DESIGN.md §15.4](docs/design/DESIGN.md#154-versioning)
reads it: a major bump means a provider contract or theme key broke (with a migration note here), a minor one
features, a patch one fixes. The three apps share one version and one release; a tag `vX.Y.Z` builds and
publishes all three, and the tag's message quotes the section below.

## [Unreleased]

## [0.1.0-rc1] - 2026-11-10

The first public release candidate, numbered **0.1.0**: the owner chose a 0.x line for the first release, so
`1.0.0` is reserved for the point where the provider contracts and theme keys are promised stable. Everything
below is what the three apps and the setup tool do at this tag.

**Renumbering note.** Three candidates were published before this one as `v1.0.0-rc1`, `v1.0.0-rc2` and
`v1.0.0-rc3` (all on 2026-11-10, all with Android `versionCode` 10000); their release pages stay as they are,
and their contents are the sections marked *(was 1.0.0-rcN)* below. So that a TV with one of them installed
upgrades to this and every later release, the `versionCode` scheme gained an epoch (ADR-047): `versionCode =
1000000 + X·10000 + Y·100 + Z`, so `0.1.0-rc1` is 1000100 (> 10000) and the order of versions is the order of
codes from here on. A release candidate shares its final version's code and the final is installed over it.

### Added

- **Scores: the badminton key is asked for the moment badminton is enabled** (ADR-044 amended). Ticking the
  Badminton sport, a tournament, a player or a match in the Follow picker with no key stored opens the key
  dialog first: a text field for the TV keyboard, and the words that it is your own RapidAPI key for
  SportMicro's Badminton Micro API, kept on this TV, 100 requests a day on the free plan and possibly billed
  beyond it. OK (or the keyboard's Done key) saves the key and the follow lands; Cancel leaves badminton
  unfollowed. Scores → Settings → **Badminton data key** shows "Not set" or the key's last four characters and
  changes or removes it; removing it keeps your follows, and the Scores home and the picker then say
  "Badminton scores need a data key" (OK on that row opens the dialog) while nothing is fetched.

### Fixed

- **Launcher: the score stack never moves or overlaps the top shelf** (ADR-046). The stacked score lines and
  the shelf are independent layers: the shelf is placed from the dock line whatever the stack shows (its top is
  the same with 0, 1, 3 and 5 rows), and where the two would cross, the shelf's title, meta line and synopsis
  end 16dp before the stack's left edge and ellipsize instead of running under it. The Trakt carousel's
  backdrop fades out under the stack (a soft veil, no edge), so the lines read over any sky. One row above the
  shelf costs the title nothing.

### Changed *(was 1.0.0-rc3)*

- **Scores: badminton comes from SportMicro's "Badminton Micro" API on RapidAPI** (`badminton-micro.p.rapidapi.com`;
  ADR-044 amended). rc2 named the same listing "SportDevs' Badminton Devs"; the source, its settings and the
  docs now carry the vendor's current name, and the Settings row reads **Badminton data key (RapidAPI · Badminton
  Micro)**. The key is still **your own**, entered on the TV and never built into the APK, a GitHub secret or a
  CI variable, and it may cost you money beyond the free tier. The free plan is **100 requests a day** (read from
  the API's `x-ratelimit-requests-limit` header on 2026-11-10), so a live badminton match is now fetched **at most
  every 5 minutes** whatever screen shows it (the other sports keep 60 s), the open scoreboard's 30 s refresh reads
  the cache inside that gap, a badminton row counts as stale after 10 minutes, and the Settings row says all this.
- **Scores: the badminton parser reads the feed as recorded.** The first real recording (2026-11-10, Kumamoto
  Masters Japan and Kaohsiung Masters) showed the day list's flat shape: `status` as a word with a separate
  `status_reason`, the games won as a number and the points as `*_team_period_N_score`, a `specific_start_time`
  for the real first serve, surnames before dotless initials (`Pong L K K`, `He Z-W`), and no round. The parser
  takes that and the nested `/matches` shape, prefers the real start time, drops a league the tournament name
  already carries, and reads a retirement the feed reports as plain `Ended` (second game 19-8, games 1-0) from the
  games won. The fixtures under `livescore/src/test/resources/fixtures/badminton/` are now recordings plus
  hand-made edge cases in the recorded shape; `tools/dev/record_fixtures.sh --badminton` records both shapes.
- **Scores (from the rc2 review, dd7e8a5)**: an ending or pause the feed gives only as the status type
  (walkover, retired, suspended) is kept as the detail, so the pill reads `w/o` and `ret.`; a finished match
  with no games-won number takes its winner from the points; the picker's hint names the two days the feed is
  asked for.
- **Setup: a stock launcher that keeps Home is disabled first when you ask.** Some stock launchers give their
  HOME activity a priority above ours (`setup probe` now prints `home priority = …`; Google's TV launcher has 2,
  the Mi TV 4A's PatchWall keeps Home the same way), so the handover says Success and Home stays with them. With
  `--disable-stock`, or a yes to the question the guided flow asks at that point, the recognised stock (or the
  package you named) is disabled first, Home is handed over again and checked; if VerySmartTV still does not
  answer, the stock is re-enabled and put back on Home. Without the request nothing changes: the rollback, and a
  hint to run again with `--disable-stock`. Verified on the Android TV 14 emulator with the stock re-enabled.

### Added *(was 1.0.0-rc2)*

- **Scores: badminton** (ADR-044). ESPN has no badminton feed, so badminton comes from SportDevs' Badminton
  API on RapidAPI with **your own key**: create a free RapidAPI account, subscribe to the API's free plan and
  enter the key under Scores → Settings → **Badminton data key** (kept in the app's preferences, never in the
  APK or a backup; `docs/providers.md` has the steps). Without a key badminton is listed but not fetched, and
  the Follow picker says so. The picker's Badminton column lists this week's tournaments and, under one, its
  players and matches; "All Badminton" follows everything. The pill reads `Japan Masters · Sindhu 21-18 11-9
  Axelsen`, `INTERVAL` between games, `… Antonsen w/o Christie` for a walkover, `… ret.` for a retirement. The
  free plan has a daily request quota: a live match costs one request a minute while a screen shows it.
- **Scores: three-state boxes in the Follow picker** (ADR-045). A sport or a competition shows an empty box, a
  bar when some of what is under it is followed, or a tick when it is followed itself or every team and match
  under it is. OK on a section selects everything under it; on a fully ticked one it clears everything (for a
  sport, every follow of that sport).

### Changed *(was 1.0.0-rc2)*

- **Scores**: a break's kicker is the pause itself (`SUSPENDED`, not the round name) when the feed's detail
  carries both.

### Added *(was 1.0.0-rc1)*

- **Launcher** (`com.itsluminous.verysmarttv`): the home screen carried from the upstream project (credited in the README and `THIRD_PARTY_NOTICES.md`) and renamed.
  A dock and a grid of your apps, a top shelf that names the focused app or shows the Trakt carousel, three
  frosted buttons (Inputs, network, Settings), the clock, the date and a greeting, the Clock screensaver,
  Backup & restore (a fork backup restores, including one made under the old package name), themes pushed
  without a rebuild (`default` and `frosted`), and the settings panel. No `INTERNET` permission, no background
  services; built for 1 GB TVs.
- **System bars**: a RAM and a Storage bar under the clock, each with its free amount (replaces the fork's
  ring gauges; the `widgets` theme key became a yes/no and old values migrate).
- **Score pill**: one line under the top-right buttons with a live match from the Scores companion, with a
  sport icon; up to `score_rows` events stacked; Up from the dock focuses it, OK opens the match in Scores.
  Hidden when the companion is not installed or shows nothing.
- **Trakt companion** (`com.itsluminous.traktshelf`): sign in with a code at trakt.tv/activate; rows of posters
  (Up next, recommendations, watchlists, trending, recently watched) with ratings from TMDB, OMDb and Trakt;
  Play through Stremio, CloudStream or NetMirror, trailers through TizenTube or SmartTube; the launcher's
  carousel reads it through a provider only an app signed with the same key may open.
- **Scores companion** (`com.itsluminous.livescore`): cricket, football, F1 and tennis from ESPN's public
  site feeds, no key. Follow sports, competitions, teams or a single match; defaults on first run are India
  internationals and the IPL, the ISL, the Premier League and the Champions League, and F1. A scoreboard,
  a follow picker, settings, and the pill line the launcher shows. Refreshes only while a followed match is
  live and the launcher is reading, with backoff; follows are part of the launcher's backup.
- **Setup tool** (`setup/`): `setup.sh` and `setup.ps1` for macOS, Linux and Windows on Python 3.9+, with a
  pure-Python ADB client (no `adb` binary). The guided flow finds the TV on the Wi-Fi, installs the three APKs
  from the latest release with checksums verified, makes the launcher Home behind a Home guard (the stock
  launcher stays enabled unless you say so), pushes a theme and offers the community clean-up list for your
  model. `discover`, `probe`, `install`, `theme`, `debloat`, `restore`, `check`, `screenshot` and `adb-closed`
  run on their own too.
- **Debloat presets**: the 434 community lists carried verbatim with their testers' notes, matched to the TV
  by model, device, board and brand; a record of the TV is written before the first change and `setup
  restore` replays it. Core Android packages are refused outright; the software under the HDMI inputs and the
  apps that open the inputs and Settings are kept unless asked for.
- **Clean-up safety (Phase 6 hardening)**: every way a list is chosen shows its header and asks the same
  yes/no; a list that disables factory or service-menu packages prints what stops working and needs a typed
  yes even with `--yes`; a list for another brand that happens to share your TV's model name is listed last
  and never pre-selected; `e` at the question deselects packages by number and keeps your copy of the list
  for `check` and `restore`.

### Build and release *(was 1.0.0-rc1)*

- One Gradle project, one version from the tag, one release key for the three apps (`release.yml` refuses
  to publish anything not signed with it). `./gradlew ci` runs the unit tests, lint, the file-length gate
  and the identifier gate; CI also runs the Python suite on Ubuntu and Windows and Espresso on an Android TV
  emulator.
- Signer certificate SHA-256: `15d5085a0400d720d4aea65edab4df72c6e5a5313844715c3e56859cec99c21d` (verified with
  `apksigner verify --print-certs` on every APK of v1.0.0-rc1, v1.0.0-rc2 and v1.0.0-rc3).

[Unreleased]: https://github.com/itsluminous/verysmarttv/compare/v0.1.0-rc1...HEAD
[0.1.0-rc1]: https://github.com/itsluminous/verysmarttv/releases/tag/v0.1.0-rc1
