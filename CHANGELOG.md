# Changelog

All notable changes to VerySmartTV. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the versions [Semantic Versioning](https://semver.org/) as [DESIGN.md §15.4](docs/design/DESIGN.md#154-versioning)
reads it: a major bump means a provider contract or theme key broke (with a migration note here), a minor one
features, a patch one fixes. The three apps share one version and one release; a tag `vX.Y.Z` builds and
publishes all three, and the tag's message quotes the section below.

## [Unreleased]

## [1.0.0-rc1] - 2026-11-10

The first release candidate: everything Phases 0 to 5 of the build plan delivered, signed with the release
key for the first time so the release pipeline can be proved before `1.0.0`.

### Added

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

### Build and release

- One Gradle project, one version from the tag, one release key for the three apps (`release.yml` refuses
  to publish anything not signed with it). `./gradlew ci` runs the unit tests, lint, the file-length gate
  and the identifier gate; CI also runs the Python suite on Ubuntu and Windows and Espresso on an Android TV
  emulator.
- Signer certificate SHA-256: recorded here once the first signed release has been verified.

[Unreleased]: https://github.com/itsluminous/verysmarttv/compare/v1.0.0-rc1...HEAD
[1.0.0-rc1]: https://github.com/itsluminous/verysmarttv/releases/tag/v1.0.0-rc1
