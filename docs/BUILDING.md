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
| `:testkit` | `verysmarttv.android.lib` | Java | `FakeHeroProvider`, `FakeScoreProvider`, `HeroFixture`, `ScoreFixture`: test-only stand-ins for the companions. |
| `:testkit:hero`, `:testkit:score`, `:testkit:tiles` | `verysmarttv.android.app` | Java | The three apps the launcher's connected tests install: the two companion stand-ins (packages `com.itsluminous.traktshelf` and `com.itsluminous.livescore`, ADR-037) and the seventh TV app. Debug only, never released. |
| `:launcher` | `verysmarttv.android.app` | Java | `com.itsluminous.verysmarttv`, the home screen (carried from the fork in Phase 1; decomposed into the §8 controllers in Phase 2, ADR-038). |
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
| Home | `com.itsluminous.verysmarttv/.home.HomeActivity` |
| Screensaver (`settings put secure screensaver_components …`) | `com.itsluminous.verysmarttv/.clock.ClockDream` |
| Accessibility helpers (`enabled_accessibility_services`) | `com.itsluminous.verysmarttv/.keys.HomeKeyService`, `com.itsluminous.verysmarttv/.keys.InputKeyService` |
| Notification listener (`cmd notification allow_listener`) | `com.itsluminous.verysmarttv/.keys.GuardRevival` |
| Theme and backup providers | `content://com.itsluminous.verysmarttv.theme/`, `content://com.itsluminous.verysmarttv.backup/` (authorities, unchanged) |

A TV that had the fork's helpers enabled under the old flat names keeps a stale entry in those secure settings until
`adb shell settings put secure …` names the new components.

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

The launcher's suite (`./gradlew :launcher:connectedDebugAndroidTest`) first installs three test apps
(DESIGN.md §14.3, ADR-037): `:testkit:hero` and `:testkit:score`, the companion stand-ins, which take the
packages `com.itsluminous.traktshelf` and `com.itsluminous.livescore` and so **replace the traktshelf and
livescore debug builds on that emulator** until `./gradlew :traktshelf:installDebug :livescore:installDebug`
puts them back (their data is kept); and `:testkit:tiles`, "Tile 7", the seventh TV app the focus tests enable
and disable. On one emulator `:livescore:connectedDebugAndroidTest` must run before the launcher's suite, since
each install replaces the other's companion and a connected run uninstalls its app at the end; a whole-project
`./gradlew connectedDebugAndroidTest` (what CI runs) orders them itself (`mustRunAfter` in the launcher's and the
stand-ins' build files, ADR-052), so `org.gradle.parallel` never runs the two suites at once. Tiles 1 to 6 are entries of the launcher's test APK. With
`leaveApksInstalledAfterRun` those tiles stay on Home; `adb uninstall com.itsluminous.verysmarttv.test` and
`adb uninstall com.itsluminous.verysmarttv.testkit.tiles` remove them. Every test exports the launcher's
settings before it runs and restores them after (`support/HomeTestRule`), so the theme and choices on the
emulator survive a run. The 24 tests take about 2 minutes on `HikViewer_TV_1080p` (Apple M-series, headless
`-no-window -no-audio -gpu swiftshader_indirect`), 115 s of it on the device, plus a minute of build on a cold
Gradle daemon.

Two guards keep a connected run from hanging (ADR-052). The runner fails any one test still running after 3 minutes
(`timeout_msec`, `AndroidDefaults.TEST_TIMEOUT_MS`, with a `TestTimedOutException` and the stack of the stuck thread);
every poll loop in the suites has a deadline of 10 s or less. Tests that need the public internet carry
`@RequiresNetwork` (`BwfRecordTest`, which records BWF's Match Centre through the real hidden WebView) and are left out
of a connected run unless you pass `-PincludeNetworkTests`:

```bash
./gradlew :livescore:connectedDebugAndroidTest -PincludeNetworkTests \
  -Pandroid.testInstrumentationRunnerArguments.bwf_record=true     # BwfRecordTest records; without bwf_record it is skipped
tools/dev/record_bwf_fixtures.sh                                   # the same through adb, and it pulls the JSON
```

CI never passes `includeNetworkTests`. A whole-project run takes about 6 minutes on `HikViewer_TV_1GB`: livescore's
11 tests in 32 s, then the stand-in installs, then the launcher's 44 tests in 4 minutes 15 seconds.

When every test that starts Home fails with "waited 8000 ms for the grid to lay out a focused tile" (and
`HomeSmokeTest` with "requestFocus on the Settings pill was refused"), the emulator is in touch mode: a click in
its window, or an `adb shell input tap`, put it there, and in touch mode no view takes D-pad focus. Check with
`adb shell dumpsys input | grep TouchMode` (`TouchMode: 1`) and leave it with one key,
`adb shell input keyevent KEYCODE_DPAD_DOWN`; the suite then passes unchanged. A TV remote never enters it.

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
SHA-256 digest with the one in the release notes: `15d5085a0400d720d4aea65edab4df72c6e5a5313844715c3e56859cec99c21d`
for every release from 0.1.0 on ([SETUP.md](SETUP.md#verifying-a-download)).

## Companion banners

The two companions' TV banners (`android:banner`, 320×180 dp) show the app's icon above its name, in the
launcher's look: dark slate, the icon 90 dp across, the name in Outfit at weight 500, 28 dp, centred. An
Android drawable cannot draw text, so each banner is a layer-list, `res/drawable/banner.xml`: the slate and
the icon as a vector (`banner_art.xml`) and the name as a bitmap, `res/mipmap-xhdpi/banner.png`, 320×180 px
with a transparent background, which the layer-list stretches over the banner so one pixel is one dp.
The PNG is committed and rendered by [`tools/dev/render_banner.py`](../tools/dev/render_banner.py) from the
launcher's own `outfit.ttf` (Pillow: `pip install pillow`):

```bash
python3 tools/dev/render_banner.py --text Trakt     --out traktshelf/src/main/res/mipmap-xhdpi/banner.png
python3 tools/dev/render_banner.py --text LiveScore --out livescore/src/main/res/mipmap-xhdpi/banner.png
python3 tools/dev/render_banner.py --text Trakt     --out traktshelf/src/main/res/mipmap-xhdpi/banner.png --check   # exit 1 when stale
```

Rerun it after a change to the script or the font; the module-card labels (`xml/module_card.xml`,
"Trakt" and "Scores") are strings and are not part of the picture. The launcher's tile rounds the banner's
corners itself, so the artwork is full-bleed. [`tools/dev/screenshot.py`](../tools/dev/screenshot.py) takes a
screenshot over adb (`exec-out screencap -p`, `ANDROID_SERIAL` or `-s`) and drops whatever a TV writes before
the PNG signature, which the Mi TV 4A does; `--fix raw.png out.png` cleans a capture made another way.

## Identifier gate

Every identifier was renamed from the upstream project and from the design's working title (DESIGN.md
§7, ADR-010). The old names may appear only in `LICENSE`, `THIRD_PARTY_NOTICES.md`, the README's
credits, the preset headers (verbatim community text) and `docs/`. The check, which must print nothing:

```bash
git grep -niE 'leantv|gathin|itsluminous\.tvhome|cleanyourtv' -- ':!LICENSE' ':!THIRD_PARTY_NOTICES.md' ':!README.md' ':!docs/' ':!presets/' ':!.github/workflows/ci.yml'
```

`ci.yml` runs the same command in its `verify` job (the workflow file is left out because it holds the
pattern), and `setup/tests/test_bootstrap_and_identifiers.py` runs it as a test.

## Local API keys for the Trakt companion (Phase 1)

`:traktshelf` reads `TRAKT_CLIENT_ID`, `TMDB_API_KEY` and `OMDB_API_KEY` from the environment, then
from `secrets.env` at the repo root (git-ignored, `KEY=value` lines). Without them it builds with empty
keys and its sign-in screen says so. How to get the keys is in the companion's README once it lands.

## The setup tool (`setup/`)

The computer-side tool of [docs/SETUP.md](SETUP.md) is a plain Python package, `setup/verysmarttv_setup`, with
one dependency (`setup/requirements.txt`: `cryptography`, for the RSA key ADB authenticates with). It speaks the ADB
wire protocol itself (`adb/`), so no `adb` binary or server is involved; everything it runs on a TV is a shell
command through that link.

```bash
python3 -m venv setup/.venv && setup/.venv/bin/pip install -r setup/requirements.txt   # dev venv, git-ignored
cd setup && .venv/bin/python -m unittest discover -s tests -t .                        # the Python suite
.venv/bin/python -m verysmarttv_setup --help
python3 tools/dev/build_presets_index.py --check                                      # presets/index.json is fresh
```

The tests run against two fakes: `tests/fake_tv.py` answers the shell commands the tool sends from a small state
(packages, HOME claimants, Wi-Fi owner, `dumpsys package` text) and records what was sent, and `tests/fake_adbd.py`
is an in-process adbd on a real socket for the protocol itself (the token/signature/public-key handshake, `shell:`,
`sync:`, `reboot:`). CI runs the suite on Ubuntu and Windows with Python 3.9 and 3.12 (`setup-tests` in `ci.yml`).

Against the emulator: it exposes adbd on the host at the console port + 1, so `HikViewer_TV_1GB` (console 5556) is
`--host 127.0.0.1:5557`, with no `adb tcpip` needed. The debug APKs of a build go in one folder for `--apk-dir`:

```bash
mkdir -p /tmp/apks && cp */build/outputs/apk/debug/*-debug.apk /tmp/apks/
setup/setup.sh --host 127.0.0.1:5557 --apk-dir /tmp/apks --theme frosted --preset none --yes
```

`VERYSMARTTV_HOME` moves the tool's folder (`~/.verysmarttv/`: the ADB key, records, releases, screenshots); the
tests set it to a temp dir. Never point the tool at the owner's TV from an agent session.
