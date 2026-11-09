# Contributing

Thanks for helping. VerySmartTV is one Gradle project that builds three Android TV apps; the design
is in [docs/design/DESIGN.md](docs/design/DESIGN.md) and every decision has a line in
[docs/design/DECISIONS.md](docs/design/DECISIONS.md). If you work with a coding agent, point it at
[AGENTS.md](AGENTS.md), which has the same rules in its form.

## Before you start

- A bug: open an issue with the TV model, Android version, the app and version (Launcher settings
  shows it), and what you pressed. A logcat excerpt helps; blur Wi-Fi names and anything personal.
- A feature or a new dependency: open an issue first. The launcher stays small and offline (1 GB TVs,
  no network); a feature that needs the internet belongs in a companion app.
- A debloat preset fix: the lists are community data from the upstream project named first in
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Send the fix there too, so every project that
  reads those lists gets it.

## Making a change

1. Build and test locally: `./gradlew ci` (JDK 17, Android SDK 36; [docs/BUILDING.md](docs/BUILDING.md)).
2. Tests come with the change, in the same commit: a JVM test for logic, Robolectric for anything that
   needs a `Context`, Espresso for what needs a device. A bug fix starts with the failing test.
3. User-facing text goes in `res/values/strings.xml` (English). The 30 other languages are
   community-maintained; filling a gap in one is welcome.
4. Keep files under 1,200 lines (`./gradlew fileLength`). Keep lint clean; it fails on warnings.
   No dead code: nothing lands that no shipped theme, setting, test or companion reaches, and no
   commented-out code or `TODO` placeholders.
5. Never commit a keystore, `release.properties`, `secrets.env`, an API key, an IP address, a Wi-Fi
   name or a serial number. `.gitignore` covers the usual files; check before adding a new type.
6. Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `feat(launcher): …`,
   `fix(livescore): …`, `docs: …`. Say what changed and why.
7. Open a pull request against `main`. Say which TV or emulator you tested on. A visual change gets a
   screenshot from the TV or the emulator in the description, not in the tree.

CI runs the same `./gradlew ci`, builds every APK, and on code changes runs the Espresso suites on an
Android TV emulator. A pull request merges when it is green and reviewed.

## Labels for release notes

Release notes are generated from pull-request labels when a tag is not annotated: `feat`, `fix`,
`docs`, `area:launcher`, `area:traktshelf`, `area:livescore`, `area:setup` ([.github/release.yml](.github/release.yml)).

## Licence

Contributions are released under the repository's MIT licence ([LICENSE](LICENSE)). The parts derived
from the upstream project keep its notice; [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) lists
everything the project drew on. Do not copy code from a repository under a different licence.
