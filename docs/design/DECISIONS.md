# VerySmartTV — decisions (ADR log)

One entry per decision in [DESIGN.md](DESIGN.md). Format: context → decision → alternatives rejected →
consequences. Every entry is **accepted** by the owner (2026-11-09) unless its heading says
**rejected** (the decision was not taken; the entry records why) or **changed** (the decision as it now
stands). The project was named VerySmartTV at the same time; the launcher's applicationId and namespace
are `com.itsluminous.verysmarttv`, the Gradle module stays `:launcher`.

## ADR-001 One Gradle project with five modules
- Context: three apps that share provider contracts, glass drawables and D-pad helpers, and must be signed with one key.
- Decision: `:core` (Java library), `:launcher`, `:traktshelf`, `:livescore`, `:testkit`; `build-logic` convention plugins; one version catalog.
- Rejected: three Gradle projects as in cleanyourtv (`launcher/`, `addons/traktshelf/` each with its own wrapper): duplicated signing/version code, no shared module without publishing, three CI matrices.
- Consequences: one `./gradlew ci`; the emulator job installs all three debug APKs from one build; a change to a contract fails the other side's compile.

## ADR-002 AGP 9.x / Gradle 9.x / JDK 17
- Context: cleanyourtv pins AGP 8.5.2 + Gradle 8.9 (JDK 17–22 only); HikViewer (the owner's other repo) pins AGP 9.5.1 + Gradle 9.8.2; the local SDK has build-tools/platform 37.
- Decision: take the current stable AGP 9.x and Gradle 9.x at Phase 0; JDK 17 everywhere.
- Rejected: staying on AGP 8.5.2 to match the carried build files (two toolchains for the owner; JDK ceiling at 22).
- Consequences: AGP 9's built-in Kotlin compiles `:livescore` without KGP; build files are rewritten in Kotlin DSL during the carry.

## ADR-003 compileSdk 36, targetSdk 34, minSdk 26
- Context: carried `launcher/app/build.gradle` has `minSdk 26`, `targetSdk 34`; presets cover Android 8.0 TVs; no Play Store requirement.
- Decision: compileSdk 36 (installed), targetSdk 34 shared by the three apps, minSdk 26.
- Rejected: targetSdk 35/36 now (edge-to-edge and service/intent tightening untested on a TV with accessibility + notification-listener services); minSdk 29 (would exclude TVs the presets serve).
- Consequences: a Phase 6 follow-up re-evaluates targetSdk on a real Android 14 TV.

## ADR-004 Java for carried code and `:core`, Kotlin for `:livescore`
- Context: 101 carried Java files with no tests of their own; the launcher ships no Kotlin stdlib (`packagingOptions` excludes `kotlin/**`); new code is a networking/parsing app; the owner writes Kotlin daily.
- Decision: keep Java in `:launcher`, `:traktshelf`, `:core`; write `:livescore` in Kotlin (stdlib only).
- Rejected: all-Java (new code in the language with weaker null-safety and more boilerplate); all-Kotlin (a multi-week conversion with no visible result, stdlib in the launcher, regressions in untested code).
- Consequences: two languages in one repo, isolated per module; `:core` must stay Java so the launcher pulls no stdlib.

## ADR-005 No coroutines, no serialization/HTTP libraries in `:livescore`
- Context: four URLs, payloads up to 365 KB, a 1 GB TV, traktshelf already uses `HttpURLConnection` + `org.json` + threads.
- Decision: `HttpURLConnection`, `android.util.JsonReader` streaming parse, a single-thread `Executor`, `Handler`.
- Rejected: coroutines (+~200 KB after R8, a second concurrency idiom), OkHttp/Retrofit/Moshi/kotlinx-serialization (dependency weight for four endpoints), `org.json` tree parsing (holds ~3 MB of objects for the tennis payload).
- Consequences: parsers are hand-written against fixtures; adding a field means editing a parser and a fixture.

## ADR-006 Views + RecyclerView, no Leanback browse fragments, no Compose
- Context: carried UI is Views; traktshelf avoided Leanback; Compose for TV is ~2 MB.
- Decision: Views everywhere, shared glass drawables in `:core`.
- Rejected: Compose for TV (size, 1 GB TVs), Leanback `BrowseSupportFragment` (heavy, opinionated).

## ADR-007 Decompose `MainActivity` into 17 classes with pure rule objects
- Context: 4,973 lines, 180 methods; focus and layout decisions are inline `if`s, untestable without a device.
- Decision: §8 table; "move verbatim first, refactor second"; every focus/row decision becomes a pure class returning a value; a Gradle check fails files over 1,200 lines.
- Rejected: leaving it (no unit tests possible for the Up→score→Inputs rule); a rewrite (loses years of device-specific fixes encoded in that file).
- Consequences: Phase 2 is the riskiest phase; the Espresso focus suite is written before the split and gates every commit of it.

## ADR-008 Drop decorative and superseded launcher features (accepted)
- Context: requirement 7 vs requirement 2.
- Decision: drop festival/seasonal skins, F1 weekend and game-day panels, wallpaper rotation/scenes/moods/captions, time-of-day sky, lunar/Shamsi dates, `show_stats`/`CpuLoad`, the ring gauges, the Hebrew duplicate layout; keep all device-compatibility code and the 31 string translations.
- Rejected: carrying everything (≈3,300 lines reachable only through themes we do not ship); dropping device-compat code (the presets target 435 TV models, so the launcher will meet those TVs).
- Consequences: dropped theme keys are logged and ignored; RTL becomes framework mirroring, documented as untested.

## ADR-009 Keep 30 translations as pure string files, English-only for new strings
- Context: `values-*/strings.xml` files of 69–186 strings; the owner cannot maintain them.
- Decision: keep them; disable `MissingTranslation` lint; new strings fall back to English; contributors may fill gaps.
- Rejected: deleting them (free to keep, and they serve the TVs the presets cover); maintaining them (impossible for one owner).

## ADR-010 Rename every identifier; the grep is a test
- Context: requirement 1.
- Decision: the naming map in §7; `tests/test_no_old_identifiers.py` allows old names only in `LICENSE`, `THIRD_PARTY_NOTICES.md`, README credits, `presets/**` headers, and `docs/design/`.
- Rejected: keeping the Java package `com.gathin.tvhome` under a new applicationId (what the fork does to ease upstream merges; no longer a goal).
- Consequences: third-party module cards written for the old category do not appear; the fork's backup JSON and order file are still readable (compat shims with tests).

## ADR-011 Keep the old `.backup` authority in `<queries>` for one migration (**rejected**)
- Context: the owner's TV runs `com.itsluminous.tvhome`; the fork's "Import from another launcher" row reads the other package's provider.
- Proposed: `Backup.KNOWN_PACKAGES = {"com.itsluminous.tvhome"}` for import; drop `com.gathin.tvhome`.
- Owner's decision: **no `tvhome` authority anywhere** in the tree, and the import row is removed from the design. Backup / Restore from file is the only path.
- Consequences: `Backup.KNOWN_PACKAGES` and the `<queries>` entries go in Phase 1; the migration is done by the setup tool over ADB (read the old `.backup/settings`, push the file, Restore from file), covered by `BackupCompatTest` and `OrderMagicTest`; the identifier grep allows `tvhome` only in `LICENSE`, `THIRD_PARTY_NOTICES.md`, README credits, preset headers and `docs/design/`.

## ADR-012 ESPN site feeds as the single v1 score source
- Context: §13.1 table; verified live on 2026-11-09; tv-town-launcher's production use since 2026 with 60 s polling while on screen.
- Decision: `site.api.espn.com` scoreboards per league + `site.web.api.espn.com … cricket/scorepanel?dates=` for cricket; no key.
- Rejected: CricAPI (100 hits/day, cricket only), TheSportsDB (livescores Premium only), football-data.org (football only, no ISL, 10/min), API-Football (100/day), Sofascore/Cricbuzz (unofficial, blocked at times), Jolpica (F1 results only).
- Consequences: undocumented feed; fail-soft design; Disclaimer; adapter boundary (ADR-013) for a replacement; weekly CI fixture re-check.

## ADR-013 `ScoreSource` adapter boundary with a source-neutral `Event` model
- Context: one source today, likely a second later.
- Decision: §13.3 interface; `EspnSource` the only implementation; `FakeSource` in `:testkit`.
- Rejected: parsing ESPN JSON straight into provider rows (ties the contract to the feed shape).

## ADR-014 Refresh-on-read plus JobScheduler, no services
- Context: 1 GB TV; a 60 s interval is below JobScheduler's 15-minute periodic floor; nothing should run while nothing displays.
- Decision: §13.5: the launcher's 60 s provider read (only while a followed event is live and Home is resumed) triggers a stale-check fetch; the scoreboard UI polls at 30 s while resumed; a one-off job 2 min before the next followed start and a 6 h periodic fixture refresh; backoff 1-2-4-8 min.
- Rejected: a foreground service with a notification (the TV shows it, and it costs memory all day); `AlarmManager` at 60 s (wakes the device with nothing on screen); polling from the launcher itself (it has no INTERNET and must not).
- Consequences: the pill's freshness depends on a screen looking at it, which is the point.

## ADR-015 Pill text computed in the companion, not the launcher
- Context: `HeroFeed` already takes the companion's ready-made `meta` line; the launcher has no sports knowledge.
- Decision: `/pill` returns `state, kicker, line, color`; the launcher renders only.
- Rejected: shipping `Event` columns and formatting in the launcher (duplicates sport logic in Java, untestable without the companion).

## ADR-016 Signature-level permissions on both companion providers
- Context: requirement 5; one keystore; the Backup provider must stay readable from `adb shell`.
- Decision: `READ_HERO` and `READ_SCORES` custom permissions, `protectionLevel="signature"`; `.backup` stays open read-only; `.theme` stays `WRITE_SECURE_SETTINGS`.
- Rejected: no permission on the companions (any app could read the user's Trakt rows and followed teams); `signatureOrSystem`/`dangerous` (no better fit).
- Consequences: self-built launchers cannot read released companions; documented.

## ADR-017 Up from the grid always goes to the score pill (if shown) then Inputs
- Context: requirement 2; today `firstTopButton()` picks the first visible pill and RecyclerView may restore the last-focused pill.
- Decision: §9.3 rules in a pure `TopBarFocus`; the carousel becomes a stop between grid and pill.
- Rejected: restoring the last-focused pill (the owner explicitly rejected it); putting the pill in the pill row (changes the reference layout and the Left/Right bar walk).

## ADR-018 `ResourceBars` as one custom view; `widgets` becomes a boolean
- Context: requirement 2; the reference image's two-row block.
- Decision: one `View` draws both rows; `widgets: true|false`; old arrays migrate to `true` when non-empty; the settings row is one toggle "System bars".
- Rejected: six stock widgets (`TextView`×4 + `ProgressBar`×2: more layout passes on a 1 GB TV, harder to align); keeping the array type with only two allowed values (a type nobody needs).

## ADR-019 JUnit 4 + Robolectric + Espresso/UiAutomator + Python unittest; fakes, no Mockito
- Context: carried tests are JUnit 4; cross-app handshakes need real installs; the setup tool is Python.
- Decision: §14.1; coverage floors per module; `:testkit` holds the fakes.
- Rejected: JUnit 5 (two runners in one repo for no gain), Mockito (encourages mocking Android classes instead of extracting pure logic), pytest (a dependency the carried tests never needed).

## ADR-020 Espresso focus suite before the decomposition
- Context: ADR-007 risk.
- Decision: Phase 2 step 1 writes `HomeFocusNoPillTest`, `CarouselFocusTest`, `ThemePushTest`, `BackupRestoreTest`, `HomeSmokeTest` against the undecomposed code; every decomposition commit keeps them green.
- Rejected: writing tests after the split (would test the new shape, not preserve the old behaviour).

## ADR-021 One release keystore, four signing secrets, verify-or-fail
- Context: owner's decision; HikViewer's `release.yml` pattern.
- Decision: §15.3; keystore alias `verysmarttv`; `apksigner` digest must equal the keystore's; all three digests equal; digest published in README and release body.
- Rejected: a key per app (three secret sets, three card approvals, no signature permissions across apps); signing locally and uploading by hand (talkies' `release-apk.sh`: not reproducible by CI).

## ADR-022 Emulator tests on every push to main and code PRs, not nightly
- Context: requirement 5 asked "if feasible within free minutes"; the repo is public and GitHub Actions has no minutes cap for public repositories.
- Decision: run `instrumented` on push to `main`, `workflow_dispatch`, and PRs that touch code (`dorny/paths-filter`); ~30–40 min per run.
- Rejected: nightly only (a broken focus rule would be found a day late).

## ADR-023 Shared version from the tag; all three APKs rebuilt per tag
- Context: requirement 5 "versioning scheme shared across the three apps".
- Decision: `vX.Y.Z` → `versionName X.Y.Z`, `versionCode X·10000+Y·100+Z`; dev builds `0.0.0-dev`/1; every tag publishes all three plus `SHA256SUMS`.
- Rejected: per-app versions (users would mix incompatible contract versions; the setup tool would need a compatibility table).

## ADR-024 Release notes from the annotated tag, else GitHub-generated with label categories
- Context: HikViewer's approach; requirement 5 "generate release notes".
- Decision: tag body wins; otherwise `generate_release_notes` with `.github/release.yml` categories; CHANGELOG.md by hand.
- Rejected: a commit-message changelog generator (another tool to maintain).

## ADR-025 One Python setup package with thin bash/PowerShell bootstraps
- Context: requirement 6; cleanyourtv has `.sh` scripts each with a `.ps1` twin (two implementations of the Home guard to keep in step, `tests/test_core_packages.py`).
- Decision: `setup/verysmarttv_setup` (Python ≥ 3.9) carrying `tv.py`, `b64push.py`, `recovery.py`, `check.py`, the `disable.sh` guard logic; `setup.sh`/`setup.ps1` only find Python and make a venv.
- Rejected: shell + PowerShell twins (double logic); a compiled binary (nothing to reproduce from source); requiring the stock `adb` (blocked on some machines, and the requirement forbids it).

## ADR-026 TV discovery: mDNS, then /24 port-5555 scan, then manual
- Context: the owner's TV moves on DHCP (`work/README-mooq1.md`); a `nc` loop is what they used.
- Decision: `zeroconf` browse for `_adb._tcp`/`_adb-tls-connect._tcp` (2 s), threaded connect scan (≈4 s), manual entry with `address_problem` validation; remember the last TV.
- Rejected: manual IP only (the brief asks for discovery); ARP/vendor-OUI scans (platform-specific).

## ADR-027 Presets carried verbatim; tokens mined from headers; exact-tier auto-suggestion only
- Context: `tv.json` holds only `tv, os, brand, notes` (checked across 434 files); headers are free text but name device/product/board codes.
- Decision: §17.2 index with mined tokens; §17.3 tiers; only an exact `ro.product.model`/`ro.product.device` match is pre-selected, and still shown in full before anything is sent.
- Rejected: hand-adding a `models` field to 434 files (a week of editing community data); fuzzy brand-only auto-apply (unsafe).

## ADR-028 Recovery records keyed by serial+model hash, not IP
- Context: `recovery.py` keys records by address and warns when the TV moves (`recovery.py:19-23`).
- Decision: `sha256(ro.serialno + ro.product.model)[:12]` under `~/.verysmarttv/`.
- Rejected: IP keying (DHCP breaks it); `ANDROID_ID` (changes on factory reset, which is when you most want the record found — but a reset also clears the disables, so either works; serial is simpler to read).

## ADR-029 Attribution: MIT notice in LICENSE + THIRD_PARTY_NOTICES.md naming the repositories (**changed**)
- Context: requirement 6 "keep attribution"; 732 distinct git authors over `examples/`.
- Proposed: `presets/ATTRIBUTION.md` generated with slug, source path, last commit and author names.
- Owner's decision: the MIT derived-work notice in `LICENSE`, plus `THIRD_PARTY_NOTICES.md` naming the repositories that helped, each with its licence text: Gathin23/cleanyourtv and its community lists, NareshJagriya/tv-town-launcher, rajupp/talkies, itsluminous/HikViewer. **No per-contributor list, no `ATTRIBUTION.md` generator.** `presets/README.md` keeps the CleanYourTV MIT notice; each list's header still names its tester.
- Rejected: per-author names (a 732-name file to regenerate on every list update, and names are personal data the lists' headers already carry where the tester chose to); a single collective line only (thinner than MIT's notice requirement).

## ADR-030 Licence: MIT with the CleanYourTV derived-work notice in `LICENSE`
- Context: requirement 8.
- Decision: §18.1; `THIRD_PARTY_NOTICES.md` for fonts, icons, Python libraries and data services.
- Rejected: a separate `NOTICE` file only (GitHub's licence detector reads `LICENSE`).

## ADR-031 Launcher APK size gate at 900 KB
- Context: upstream's 576 KB gate; the fork is at 615 KB; the decomposition adds classes.
- Decision: CI fails above 900 KB.
- Rejected: no gate (1 GB TVs still matter); 700 KB (would fight the decomposition).

## ADR-032 Follows are not part of the launcher backup in v1 (**rejected**)
- Context: the launcher's Backup covers its own settings; follows live in the companion.
- Proposed: `follows.json` stays in the companion; re-entered in a minute.
- Owner's decision: follows **are** part of the launcher backup from v1. The launcher's Backup JSON gets a `companions` section, read and written through each companion's provider: `ScoreContract` gains a `/backup` read URI (one row, `json`) under `READ_SCORES` and a `call("restore", …)` under a `signature` `writePermission` `WRITE_SCORES`. `HeroContract` gets neither: traktshelf's state is OAuth tokens, which are never backed up.
- Consequences: `ScoreContract` carries the two names from Phase 0; `FakeScoreProvider` answers both; `BackupRestoreTest` (Espresso) covers the round trip once `:livescore` exists; a restore on a TV without the companion skips the section with one log line.

## ADR-033 Default follows on first run
- Context: the pill should show something before the picker is used; the owner is in India.
- Decision: cricket India internationals + IPL; football ISL + Premier League + Champions League; F1; tennis off.
- Rejected: nothing followed by default (an empty pill on first Home).

## ADR-034 Companion UI without Leanback search; three-column follow picker
- Context: catalogues under 40 items per level.
- Decision: sport → competition → team columns, OK toggles.
- Rejected: a search box (keyboard on a TV for lists this short).

## ADR-035 All user-facing text lives in string resources
- Context: owner's rule, added with the ADR-008 answer; the carried code has a few literals (`"LIVE"`-style kickers, settings subtitles) and the decomposition will touch every one of them.
- Decision: no literal that a user can read is written in Java or Kotlin, in any module. Everything goes through `R.string` (with `%1$s` placeholders where the text is composed). Lint's `HardcodedText` and `SetTextI18n` are errors; a module may not baseline them.
- Exempt: text that is data rather than UI (a companion's `line`/`meta` row the launcher renders verbatim, log messages, exception messages, JSON keys).
- Rejected: enforcing it only in the launcher (the companions have the same 30-locale fallback problem) or by review alone (lint catches it for free).

## ADR-036 Compatibility with the fork's files is by shape, not by name
- Context: §7.4 says the order reader also accepts the fork's `tvhome-order 1` header and a backup whose `package` is one of the three known names, and §14.2 asks for `OrderMagicTest` and `BackupCompatTest`. ADR-010/011 say the old identifiers may appear nowhere in code or tests: the identifier gate in docs/BUILDING.md prints nothing. The two cannot both hold for a literal.
- Decision (Phase 1 coder, 2026-11-09): `Order.decode` accepts any header matching `[a-z]+-order 1` (the format is unchanged; only the product name in the header moved), and `Backup.restore` checks `version` and `prefs` and never reads `package`, as it already did. The tests use stand-in names (`previous-order 1`, `com.example.previouslauncher`) that exercise the same code path. The fork's files restore without the old names being written anywhere in this tree.
- Rejected: spelling the old literal in code or a test fixture (fails the gate, which is the project's one defence against a half-done rename); building the literal from pieces (`"tv" + "home"`) to slip past the gate (hides exactly what the gate is for).
- Consequences: a backup from any launcher that writes this format, under any name, restores here; the gate stays absolute.

## ADR-037 The companion stand-in installs under the companion's own package
- Context: §14.3 asks for `CarouselFocusTest` with "the companion fake installed", and §5 puts the fakes in `:testkit` as a library. On a device a fake must be an installed app. The launcher reads the carousel at the fixed authority `HeroContract.AUTHORITY` and opens a title through the fixed component `HeroContract.DETAILS_ACTIVITY`, both inside `com.itsluminous.traktshelf`; Android lets one installed package own an authority, and a component name always names its own package. A fake under another id (`com.itsluminous.testkit.hero`) could neither be installed beside the real companion nor be the target of OK on the shelf.
- Decision (Phase 2 QA, 2026-11-09): `:testkit:hero` is an application module with `applicationId com.itsluminous.traktshelf` and namespace `com.itsluminous.verysmarttv.testkit.hero`. It holds one component, `FixtureHeroProvider` (`FakeHeroProvider` seeded with `HeroFixture` on create), declares `READ_HERO` at `signature` level exactly as the companion does, and is debug-only. `:launcher:connectedDebugAndroidTest` depends on its `installDebug`: on a test emulator it replaces the companion's debug build (same debug key, so `adb install -r` keeps the companion's data) until `./gradlew :traktshelf:installDebug` puts the real one back. The OK intent is checked in-process with an `Instrumentation.ActivityMonitor` (`support/IntentCapture`), which records and blocks the start, so the stand-in needs no activity and the test adds no library. The seventh TV app the focus tests switch on and off is `:testkit:tiles` (its own package: Android 14's shell can enable or disable whole packages only, not one alias of the test APK, and the launcher's uid may not touch another package's components); Tiles 1 to 6 are leanback aliases of the launcher's test APK.
- Rejected: a fake under its own package (cannot satisfy either fixed name); Espresso-Intents (a new catalog entry for what the framework's monitor already does); `pm uninstall` of the real companion before each run (loses nothing with `-k`, but the stand-in under the real id needs no uninstall at all); one dummy module with product flavours for several tile packages (`ci` names `lintDebug` per module, which flavours remove).
- Consequences: `HeroHandshakeTest` (§14.3, the real companion) and `CarouselFocusTest` cannot run in one install; a later phase runs them in two Gradle invocations or seeds the real companion through a test-only intent and drops the stand-in. A test emulator shows "Trakt shelf (test stand-in)" under Settings > Apps while it holds the stand-in.

## ADR-038 Where the §8 decomposition departed from the table
- Context: §8 lists the controllers and the methods each carries, with four rules; the move (Phase 2 coder, 2026-11-09) kept every body but could not keep every row of the table and still obey rule 1 ("no controller reaches into another's views") or compile between commits.
- Decision, as landed:
  - The sub-package move (§5) went first, before any controller, with the activity still whole: the members the packages now share across each other became public once, instead of twice. Every newly public signature carries `@NonNull`/`@Nullable` (lint's `UnknownNullness`, 187 findings fixed, none baselined); three `LambdaLast` findings on Java-only signatures are suppressed in place with the reason.
  - `HomeHost` names the controllers the others call (`grid()`, `shelf()`, `topBar()`, ...; `AmbientGlow`, `KeyDispatcher` and `IdleClock` are the activity's alone and have no accessor) besides the activity, state, handler, density, RTL, `open` and `findViewById`; controllers take the interface and reach the Context through `activity()`. Popups anchored to a pill ask `TopBarController.settingsPill()`/`inputsAnchor()` rather than finding the bar's views by id (rule 1). `HomeState` also holds the lifecycle flags (`started`, `stopped`, `resumed`, `tilesReady`), which as many methods read as the theme flags.
  - `refitGlass`, `hideGlass`, `applyTileNames`, `gridTopInRoot` and `focusLibrary` live in `grid.GridController`, not in `AmbientGlow`, `ShelfController` and `TopBarController` as the table says: each acts on the glass or the grid's RecyclerView, which the grid owns. `showGlowOnly` is split at the same seam: the shelf's half in `ShelfController`, the glow's in `AmbientGlow`, which exposes one-line fade, alpha and dip-and-swap methods so the shelf never touches the ambient view.
  - `onTileLongPress`'s body is `menus.GlassPopups.showTileMenu` (it builds a popup); the activity's `TileListener` method delegates to it, as the others delegate to the grid.
  - `ShelfController` and `CarouselController` landed in one commit: they are two faces of the one hero group in `activity_main.xml` and read each other's views, so they share the `shelf` package and package-private fields. `GlassPopups` builds the top menu from `InputsMenu.addInputRow`, as the table places it.
  - Fields read by anonymous listeners and receivers inside a controller are package-private, which removes the `SyntheticAccessor` warnings the fork had baselined for them (baseline 411 → 289 over the phase, nothing added; 2 `ApplySharedPref` entries moved with their `commit()` calls into `SettingsChoices`).
  - `grid.FocusRules` takes `(position, keyCode, Grid{count, columns, dockFilled, lastFocusable, libraryAtEnd, rtl, singleRow})` and returns `PASS`/`SWALLOW`/`LIBRARY`/`MOVE(target)`; `uniformRows` is not an input because today's rule never reads it. `topbar.TopBarFocus` carries the pre-pill rules with `Stop` an enum (`STAY`, `FRAMEWORK`, `GRID`, `CAROUSEL`, `PILL`) that Phase 3 extends by `SCORE_PILL`. `inputs.InputRows` takes a list of `Input`s built from `TvInputInfo` and a state lookup, so `TvInputManager.getInputState` is still asked only for the rows that show a state.
- Rejected: controllers typed on `HomeActivity` (a cycle between every package and `home`, and no fake host for JVM tests later); keeping the glass methods in `AmbientGlow` (two owners for one view); a `grid.GlassHighlight` class (one more class than the table, for a split the grid did not need).
- Consequences: `HomeActivity` is 465 lines of wiring; every file in `:launcher` is under 1,000 lines and the `fileLength` allowlist is gone; the 24 Espresso cases of ADR-020 stayed green through every commit of the split.
