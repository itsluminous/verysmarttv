# VerySmartTV

[![CI](https://github.com/itsluminous/verysmarttv/actions/workflows/ci.yml/badge.svg)](https://github.com/itsluminous/verysmarttv/actions/workflows/ci.yml)
[![Release](https://github.com/itsluminous/verysmarttv/actions/workflows/release.yml/badge.svg)](https://github.com/itsluminous/verysmarttv/actions/workflows/release.yml)
[![Latest release](https://img.shields.io/github/v/release/itsluminous/verysmarttv?include_prereleases)](https://github.com/itsluminous/verysmarttv/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Platform: Android TV 8.0+](https://img.shields.io/badge/Android%20TV-8.0%2B-3DDC84)

A small launcher for Android TV plus two companions: a Trakt shelf and live scores. No ads, no network
in the launcher, built for 1 GB TVs. Three apps from one Gradle project, one release key, one version.

**Status: under construction.** Phases 0 and 1 of the [build plan](docs/design/DESIGN.md#20-phased-build-plan)
are in: the Gradle skeleton, the provider contracts, CI and the release workflow, and the launcher and the Trakt
companion carried from the fork under their new names (`./gradlew assembleDebug` builds both; the ring gauges are
gone and the RAM/Storage bars arrive in Phase 3). The sections below are filled in as the phases land; the design
they follow is [docs/design/DESIGN.md](docs/design/DESIGN.md).

## Screenshots

Taken from the TV in Phase 6.

## Features

- **Launcher** (`com.itsluminous.verysmarttv`): the home screen; no `INTERNET` permission.
- **Trakt companion** (`com.itsluminous.traktshelf`): your Trakt account as rows of posters, and the
  launcher's top-shelf carousel.
- **Live scores** (`com.itsluminous.livescore`): follow teams and competitions; a one-line score pill
  under the launcher's top-right buttons.

## How it works

Three apps; the launcher reads the companions through content providers guarded by signature-level
permissions, so only apps signed with the same key can read your Trakt rows or followed teams.
Contracts: [`core/.../HeroContract.java`](core/src/main/java/com/itsluminous/verysmarttv/core/contract/HeroContract.java),
[`core/.../ScoreContract.java`](core/src/main/java/com/itsluminous/verysmarttv/core/contract/ScoreContract.java).

## Install

```bash
git clone https://github.com/itsluminous/verysmarttv && cd verysmarttv
setup/setup.sh                                                   # macOS, Linux
powershell -ExecutionPolicy Bypass -File setup\setup.ps1         # Windows
```

The guided flow finds the TV, installs the three apps from the latest release (checksums verified), makes
the launcher Home, pushes a theme and offers the community clean-up list for your model, undoable with
`setup/setup.sh restore`. Everything it asks and every subcommand: [docs/SETUP.md](docs/SETUP.md). The first
release is Phase 6; until it exists, build from source and pass `--apk-dir` ([docs/BUILDING.md](docs/BUILDING.md)).

## Usage tips

Written with Phase 6.

## Building

JDK 17, Android SDK 36, `./gradlew ci`. Everything in [docs/BUILDING.md](docs/BUILDING.md).

## Contributing

[CONTRIBUTING.md](CONTRIBUTING.md): tests with every change, Conventional Commits, never commit keys,
IP addresses or Wi-Fi names. Coding agents: [AGENTS.md](AGENTS.md).

## References

- [CleanYourTV](https://github.com/Gathin23/cleanyourtv): the launcher, the Trakt companion, the setup
  tooling and the community debloat lists this project derives from.
- [tv-town-launcher](https://github.com/NareshJagriya/tv-town-launcher): the ESPN feed approach to
  live scores.
- [talkies](https://github.com/rajupp/talkies): the layout of the notices and disclaimer.

## Disclaimer

Unofficial; not affiliated with ESPN, Trakt, TMDB or OMDb. Written with Phase 6.

## License

MIT © 2026 itsluminous. Derived from CleanYourTV, MIT © 2026 Gathin: see [LICENSE](LICENSE) and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
