# Building VerySmartTV

One Gradle project builds the launcher and its two companions. This page covers the toolchain, the
everyday commands, the TV emulators, and release signing. The reasoning behind each version is in
[design/DESIGN.md §5.1](design/DESIGN.md#51-toolchain).

## Toolchain

| Need | Version | Notes |
|---|---|---|
| JDK | **17** | Corretto or Temurin. AGP 9 needs 17; the code is Java 17. Set `JAVA_HOME`, or let Gradle's toolchain detection find it. |
| Android SDK | platform **36**, build-tools **36.0.0** | `sdkmanager "platforms;android-36" "build-tools;36.0.0"`. Tell Gradle where it is with `local.properties` at the repo root: `sdk.dir=/path/to/Android/sdk` (git-ignored). |
| Gradle | **9.8.2**, through the wrapper | `./gradlew` downloads it once. Never install Gradle separately. |
| Android Gradle Plugin | **9.5.1** | Pinned in `gradle/libs.versions.toml`; Dependabot proposes bumps. |

SDK levels for every module: `compileSdk 36`, `targetSdk 34`, `minSdk 26`
(`build-logic/src/main/kotlin/AndroidDefaults.kt`).

## Commands

```bash
./gradlew ci                      # everything CI runs: assembleDebug, testDebugUnitTest, lintDebug for every module,
                                  # the build-logic unit tests, the 1,200-line fileLength gate
./gradlew fileLength              # the file-length gate alone
./gradlew :core:testDebugUnitTest # one module's JVM tests
./gradlew assembleDebug           # debug APKs of the apps: <module>/build/outputs/apk/debug/
./gradlew assembleRelease         # release APKs; debug-signed unless signing is configured (below):
                                  # <module>/build/outputs/apk/release/verysmarttv-<module>-0.0.0-dev.apk
./gradlew assembleRelease -PversionName=1.2.3   # what release.yml runs: versionName 1.2.3, versionCode 10203
```

Reports land in `<module>/build/reports/` (tests, `lint-results-debug.html`).

## Modules

| Module | Plugin | Language | What |
|---|---|---|---|
| `:core` | `verysmarttv.android.lib` | Java | The provider contracts (`HeroContract`, `ScoreContract`) and small shared helpers. |
| `:testkit` | `verysmarttv.android.lib` | Java | `FakeHeroProvider`, `FakeScoreProvider`, `HeroFixture`: test-only stand-ins for the companions. |
| `:testkit:hero`, `:testkit:tiles` | `verysmarttv.android.app` | Java | The two apps the launcher's connected tests install: the companion stand-in (package `com.itsluminous.traktshelf`, ADR-037) and the seventh TV app. Debug only, never released. |
| `:launcher` | `verysmarttv.android.app` | Java | `com.itsluminous.verysmarttv`, the home screen (carried from the fork in Phase 1; decomposed in Phase 2). |
| `:traktshelf` | `verysmarttv.android.app` | Java | `com.itsluminous.traktshelf`, the Trakt companion ([traktshelf/README.md](../traktshelf/README.md)). |
| `:livescore` (Phase 4) | `verysmarttv.android.app` | Kotlin | `com.itsluminous.livescore`. |

The two convention plugins live in `build-logic/` and hold everything the modules share: SDK
levels, Java 17, lint as a hard gate (`warningsAsErrors`; a carried module may commit a
`lint-baseline.xml` beside its build file), R8 with resource shrinking, the shared version and the
shared signing. A module's `build.gradle.kts` sets only its namespace, applicationId and dependencies.

### Launcher component names

The launcher's classes live in the sub-packages of [DESIGN.md §5](design/DESIGN.md#5-repository-and-module-layout),
so every component the TV or the setup tool names by class carries its package segment
(`com.itsluminous.verysmarttv/.clock.ClockDream`, not `/.ClockDream`):

| What | Component |
|---|---|
| Home | `com.itsluminous.verysmarttv/.MainActivity` (renamed `.home.HomeActivity` in Phase 2) |
| Screensaver (`settings put secure screensaver_components …`) | `com.itsluminous.verysmarttv/.clock.ClockDream` |
| Accessibility helpers (`enabled_accessibility_services`) | `com.itsluminous.verysmarttv/.keys.HomeKeyService`, `com.itsluminous.verysmarttv/.keys.InputKeyService` |
| Notification listener (`cmd notification allow_listener`) | `com.itsluminous.verysmarttv/.keys.GuardRevival` |
| Theme and backup providers | `content://com.itsluminous.verysmarttv.theme/`, `content://com.itsluminous.verysmarttv.backup/` (authorities, unchanged) |

A TV that had the fork's helpers enabled under the old flat names keeps a stale entry in those secure settings until the
setup tool (Phase 5) or `adb shell settings put secure …` names the new components.

## Emulators

Instrumentation tests run on an Android TV image. CI uses `android-tv`, API 34, x86, profile
`tv_1080p`, 1536 MB (the only x86 TV image Google ships at API 34). Locally, on Apple silicon, create
the arm64 equivalent once:

```bash
sdkmanager "system-images;android-34;android-tv;arm64-v8a"
avdmanager create avd -n VerySmartTV_TV_1080p -k "system-images;android-34;android-tv;arm64-v8a" -d tv_1080p
emulator -avd VerySmartTV_TV_1080p &
./gradlew connectedDebugAndroidTest
```

A second AVD with `hw.ramSize=1024` in its `config.ini` stands in for a 1 GB TV when a phase asks for
memory numbers.

Gradle uninstalls the debug APKs when a connected run ends. Add
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to keep the launcher installed and Home on
the emulator you are also testing by hand. On a Google TV image (`google_atv`) `cmd package set-home-activity`
alone does not take Home from the stock launcher; `HomeSmokeTest` disables it for the test and enables it
again afterwards, the way the setup tool's install flow does for good.

The launcher's suite (`./gradlew :launcher:connectedDebugAndroidTest`) first installs two test apps
(DESIGN.md §14.3, ADR-037): `:testkit:hero`, the companion stand-in, which takes the package
`com.itsluminous.traktshelf` and so **replaces the traktshelf debug build on that emulator** until
`./gradlew :traktshelf:installDebug` puts it back (its sign-in is kept); and `:testkit:tiles`, "Tile 7", the
seventh TV app the focus tests enable and disable. Tiles 1 to 6 are entries of the launcher's test APK. With
`leaveApksInstalledAfterRun` those tiles stay on Home; `adb uninstall com.itsluminous.verysmarttv.test` and
`adb uninstall com.itsluminous.verysmarttv.testkit.tiles` remove them. Every test exports the launcher's
settings before it runs and restores them after (`support/HomeTestRule`), so the theme and choices on the
emulator survive a run. The 24 tests take about 2 minutes on `HikViewer_TV_1080p` (Apple M-series, headless
`-no-window -no-audio -gpu swiftshader_indirect`), 115 s of it on the device, plus a minute of build on a cold
Gradle daemon.

## Signing

Release builds of all three apps are signed with **one** keystore, alias `verysmarttv`
([DESIGN.md §15.3](design/DESIGN.md#153-signing)). The signature-level permissions between the apps
depend on it, so a launcher and a companion from different keys will not talk to each other.

Create the keystore once and keep it out of the tree (`*.jks` is git-ignored):

```bash
keytool -genkeypair -alias verysmarttv -keyalg RSA -keysize 4096 -validity 10950 -keystore release.jks
```

The build looks for signing material in this order:

1. `release.properties` next to `settings.gradle.kts` (git-ignored):
   ```properties
   storeFile=release.jks
   storePassword=…
   keyAlias=verysmarttv
   keyPassword=…
   ```
2. Environment variables `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (what
   `release.yml` exports from the GitHub secrets).
3. Neither: release builds are **debug-signed** and the build prints a warning. Fine for local testing;
   the release workflow refuses to publish such an APK.

A half-filled source (a file or environment with one of the four missing, or a keystore path that is
not a file) stops the build with the names of the missing entries. Passwords are never printed.

### GitHub secrets for `release.yml`

`KEYSTORE_BASE64` (`base64 < release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, plus the
companions' API keys `TRAKT_CLIENT_ID`, `TMDB_API_KEY`, `OMDB_API_KEY`. Pushing a tag `vX.Y.Z` builds the
three APKs, verifies each is signed by that keystore (`apksigner verify --print-certs` digest equal to
the keystore's), writes `SHA256SUMS`, and publishes a GitHub release. Without the four signing secrets
the job fails before building. A tag with a `-` (`v1.0.0-rc1`) is a pre-release.

Verify a downloaded APK: `apksigner verify --print-certs verysmarttv-launcher-X.Y.Z.apk` and compare the
SHA-256 digest with the one in the release notes.

## Identifier gate

Every identifier was renamed from the upstream project and from the design's working title (DESIGN.md
§7, ADR-010). The old names may appear only in `LICENSE`, `THIRD_PARTY_NOTICES.md`, the README's
credits, the preset headers (verbatim community text) and `docs/`. The check, which must print nothing:

```bash
git grep -niE 'leantv|gathin|tvhome|cleanyourtv' -- ':!LICENSE' ':!THIRD_PARTY_NOTICES.md' ':!README.md' ':!docs/' ':!presets/' ':!.github/workflows/ci.yml'
```

`ci.yml` runs the same command in its `verify` job (the workflow file is left out because it holds the
pattern). Phase 5 turns it into `tests/test_no_old_identifiers.py`.

## Local API keys for the Trakt companion (Phase 1)

`:traktshelf` reads `TRAKT_CLIENT_ID`, `TMDB_API_KEY` and `OMDB_API_KEY` from the environment, then
from `secrets.env` at the repo root (git-ignored, `KEY=value` lines). Without them it builds with empty
keys and its sign-in screen says so. How to get the keys is in the companion's README once it lands.
