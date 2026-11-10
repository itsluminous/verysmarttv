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

## ADR-039 Where the `:livescore` companion departed from §13
- Context: §12.2, §13 and §14.2 fixed the companion's behaviour before any ESPN payload had been parsed in code; the Phase 4 coder (2026-11-09) kept every rule but a few needed a decision the design did not make.
- Decisions, as landed:
  - **No RecyclerView.** §5.1 says "Views + RecyclerView everywhere"; every list on the companion's screens is under 40 rows (ADR-034), so they are `LinearLayout`s in a `ScrollView`, and the module has no library but `:core` and the stdlib AGP adds. The APK stays smaller and the D-pad focus order is the layout order, with nothing recycled under the focus.
  - **Football is fetched per day.** The bare `soccer/<league>/scoreboard` URL answers ESPN's own current day (2026-11-08 when asked on the evening of the 9th, IST) and refuses a date range; the companion asks for `?dates=<today>` on a live refresh and today plus tomorrow on a full one. F1 and tennis accept a range and get yesterday to tomorrow in one fetch.
  - **A final's `end_ms` is when the cache first saw it final.** ESPN's cricket `endDate` is the fixture's last scheduled day at 23:59 and its F1 `endDate` the race start; neither is the end. `EventCache.merge` keeps the first fetch time that found the event final (never later than now), so the pill's "final within 6 h" counts from the real end, and a match that was already over when the companion first fetched shows for 6 h from that fetch.
  - **The feed has no lap total.** The F1 scoreboard gives the current lap as `status.period` and no race length, so the live line is `F1 · São Paulo GP · Lap 32 · VER leads`, not `Lap 32/57`.
  - **`FakeSource` lives in `livescore/src/test`, not `:testkit`.** `:testkit` is a Java library the launcher's tests consume; the `Event` model is Kotlin inside the app module, which a library cannot depend on. The launcher's tests use `FakeScoreProvider` (already in `:testkit`), which speaks the contract and needs no model.
  - **Fixtures live under `livescore/src/test/resources/fixtures/<sport>/`** (the owner's Phase 4 instruction), recorded by `tools/dev/record_fixtures.sh` with every `events[]` cut to its first three entries and a `_note` saying so; the catalogue the picker offers (`res/raw/espn_catalogue.json`) is written by the same script from ESPN's `teams` endpoints plus a hand-kept cricket, F1 and tennis section.
  - **Settings** are Refresh now, Live refresh (on: the 60 s read-triggered fetch; off: only the 6 h job), Sports shown, Restore default follows and Clear follows: the owner's instruction named refresh behaviour and sports shown, which §13.6 did not list. A sport that is not shown is left out of Home, the picker, the pill and the fetch plan.
  - **No extra in-process timer while the launcher reads.** The launcher's own 60 s read of `/pill` while live is the timer (ADR-014); the companion only decides, on each read, whether that read fetches. Its own screens tick themselves (Home every 60 s through the same path, the scoreboard every 30 s).
  - **`ScoreContract.ACTION_OPEN`** was added to `:core` (a constant only): the action form of opening the companion at an event, for a launcher that prefers an action to a component name; `MainActivity` answers it and forwards to the scoreboard when an `event_id` is given.
  - **Memory on `HikViewer_TV_1GB`** reads 45 MB PSS with Home open and 47 MB cached in the background (Java heap 6 MB, native 3 to 12 MB, code 4 MB, shared system 15 MB, the rest swap-counted on a zram device); on the same AVD the launcher idles at 47 MB and traktshelf opens at 73 MB. The §13.5 targets (12 / 25 MB) were written for the real TV and are re-measured there in Phase 6; the companion holds the parsed events, a `JsonReader` while parsing and no bitmap, as §13.5 asks.
  - **Re-measured by own data (2026-11-10, same AVD, `dumpsys meminfo`, Private Dirty + swapped-out dirty, the companion's own pages; TOTAL PSS also counts mapped code and the shared system pages every process shows).** Idle in the background right after the launcher read `/pill`: 1.4 MB Private Dirty resident + 13.4 MB dirty in zram = **14.8 MB own data**; Java heap 2.8 MB allocated of a 5.5 MB heap; TOTAL PSS 19.2 MB. `ScoreboardActivity` open on a live match for 12 s: 16.9 MB Private Dirty + 6.6 MB swapped = **23.5 MB own data**; Java heap 2.8 MB allocated (unchanged: nothing is retained between the two states, no bitmap); native heap 12.3 MB private dirty, which on this AVD is the software renderer's; TOTAL PSS 65.9 MB, 28.2 MB of it Private Clean (code and `.so`/`.jar` maps the system shares). The §13.5 targets (12 MB idle, 25 MB open) are read against own data: open is within; idle is over by the runtime's own swapped pages (`.art`, `Dalvik Other`), not by anything the companion holds, and the real-TV pass decides whether 12 MB was the right figure for an ART process at all. No code change: the heap is flat between idle and open.
- Rejected: fetching football without a date (shows yesterday's results until ESPN's day turns); taking the feed's `endDate` as the end (a Test would show as "final" in the pill for days); a `FakeSource` in `:testkit` written against a duplicate Java model (two models to keep in step).
- Consequences: the JVM suite covers the parsers against the recorded and the hand-made fixtures (rain delay, super over, abandoned, tie, walkover, red flag); the provider's permission refusal is checked on the emulator with the shell user as the foreign caller; the §13.5 memory numbers are reported against the launcher's own on the same AVD until the real-TV pass.


## ADR-040 Where the Phase 3 launcher work departed from §9 and §10
- Context: §9 and §10 fixed the bars, the pill and the settings row before the views existed; the Phase 3 coder (2026-11-09) kept every rule but five details needed a decision the design did not make, and the Phase 3–4 review (same day) recorded them.
- Decisions, as landed:
  - **The score pill is a child of the root `FrameLayout`, not of the header row.** §9.2 says the layout above must not move when the pill hides; as a header child it would also have moved the grid under it by 24 dp every time a match ended. `ScorePillController` places it after every layout of `topButtons`: end-aligned with the last pill, 8 dp below the row, mirrored for RTL. Nothing above or below it is laid out against it.
  - **`Gb` keeps one decimal up to 100 GB**, whole from 100 GB ("1.1 GB free", "21.6 GB free", "10.0 GB free" for 9.95 GB). §9.1 says "one decimal under 10 GB, none above" and in the same paragraph names the reference image's "21.6 GB free"; the image wins, as it is what the owner asked to see. MB and B are whole.
  - **`Gb` lives in `launcher.topbar`, not `core.text`** (§5, §14.2 placed it in `:core`). Phases 3 and 4 ran in parallel on `:core` with a contract-constants-only rule; the one caller is the launcher's `ResourceBars`, so the class and `GbTest` sit beside it. It moves to `:core` the day a second module needs it.
  - **Back on a round pill returns to the grid** (rule 6). Phase 2 let Back on a pill fall through to the activity, where `back_opens_clock` could fire; with the bar consuming Back, that key opens the clock from the grid only.
  - **The Live scores subtitle reads "Scores app not installed"**: the companion's label is "Scores" (its module card), and the Trakt row names its app the same way ("Trakt shelf app not installed"); §10's "LiveScore app" was the working name.
  - **`ScoreProvider.call()` checks `WRITE_SCORES` itself.** The framework enforces a provider's read or write permission on `query`, `insert`, `update` and `delete`, but `call()` is reached by any caller holding either permission; §12.2 wants restore limited to the holder of the write permission, so the provider throws `SecurityException` when `checkCallingOrSelfPermission(WRITE_SCORES)` is not granted. A Robolectric case covers the refusal; on the device the shell user is refused before the call reaches the provider (it holds neither permission).
- Rejected: a header-row pill that is `INVISIBLE` rather than `GONE` (keeps the grid still but leaves a 24 dp hole whenever there is no match, which is most of the time); "22 GB free" to satisfy the §9.1 words (loses the figure the image shows); moving `Gb` to `:core` mid-phase (a `:core` change while the companion's coder was building against it).
- Consequences: `ScorePill`'s position is code, not XML (`ScorePillController.place`), and `HomeFocusTest` checks it end-aligns with the bar; `GbTest` is a launcher JVM test; the clock no longer opens from a focused pill with Back; a self-built companion signed with another key refuses the launcher's restore the same way it refuses its reads.

## ADR-041 The score pill stacks up to `score_rows` events, and the companion publishes every candidate
- Context: §9.2 gave the pill one line, the companion's `PillChooser.choose`, which turns two live matches every 20 s. The owner asked (2026-11-10) for up to a configurable number of followed events stacked under the top-right pills, each on its own shade, the stack one focus stop; and for the two companions' banners to carry their names.
- Decisions:
  - **`/pill` returns one row per eligible event, ranked; the launcher cuts.** `PillChooser.ranked` is `choose()` first (so a one-line pill, and row 0 of any stack, behave exactly as before, alternation included) then every other eligible event by the existing rule (live > break > upcoming within 12 h > final within 6 h; follow reason, then earliest start). The contract gains `ScoreContract.MAX_ROWS` (5) and no `limit` parameter: the companion never needs to know a launcher's row count, and a launcher that reads one row reads row 0 (§12.2 "exactly one" becomes "one or more; exactly one `none` row when nothing is in the window"). The owner's "most recently changed first" among live events is not implemented: one fetch per competition gives every event of a competition one `updated_ms`, so the order would be fetch order, not change order; the existing reason-then-start rule stands.
  - **The launcher keeps the lines in place while the companion only turns its first row.** `ScoreLine.stack(previous, fresh, rows)` cuts to `score_rows` (held to 1..5) and, when the kept rows are the same events in the same states as the lines shown, keeps the previous order; any real change (an event gone, arrived, or in another state) takes the companion's order again. Without this, two live matches in a three-line stack would swap places every 20 s; with it a one-line pill still alternates, because one line is never "the same two events".
  - **`score_rows` is a theme key and a settings row.** Theme key `score_rows` (1 to 5, default 3, `config.xml`), the **Live score rows** row right after **Live scores** in the panel (picker 1 to 5; the row shows the count in force, the theme's until a choice is saved), stored under `SettingsChoices.SCORE_ROWS_KEY`, cleared by a theme push and by Restore like every other choice, carried by Backup with the rest of `launcher_settings`.
  - **One stop, `row` inside it.** `TopBarFocus.Bar` carries `scoreRows` (0 while hidden; `scorePillShown` is derived), `Verdict` a `row`, and `onScorePillKey(key, row, bar, carousel)` walks the lines (rule 8, §9.3): Up from the grid or the carousel lands on the bottom line, Up from the top line reaches Inputs, Down from the bottom line leaves. `ScorePill` is a vertical `LinearLayout` of `ScoreLineView`s (the former `ScorePill` `TextView`, inflated from `score_line.xml`), each focusable with the glass ring of `score_line_bg`; at rest a line sits on a 10 % white rounded shade, and over the carousel's backdrop on the same shade over the round pills' dark base (`score_line_bg_over_art`, through `TopBarController.setPillsOverArt`). OK on a line opens that line's event; the long press to the follow picker stays on every line, as §9.2 asks.
  - **The flaky OK test was the key injection, not the pill.** Espresso's `pressKey` injects ACTION_DOWN and ACTION_UP as two events with the main looper idled between them; on the 1 GB emulator under a connected run that gap passed the 500 ms long-press timeout now and then, and OK on the pill opened the follow picker (one intent, the wrong component). `HomeTestRule.tap` dispatches both events to the activity in one main-thread turn, so no posted long-press check can run between them; the test asserts one intent and waits a long-press timeout for a second. The long-press listener stays because §9.2 requires it.
  - **Banners are a layer-list of a vector and a text bitmap.** Android drawables cannot draw text, so `res/drawable/banner.xml` layers `banner_art.xml` (the slate, the diagonal, the 90 dp disc raised to leave room) and `res/mipmap-xhdpi/banner.png` (320×180 px, transparent, the name in Outfit 500 at 28 px, rendered by the committed `tools/dev/render_banner.py` from the launcher's `outfit.ttf`; `--check` fails when the PNG is stale). The artwork is full-bleed: the launcher's tile rounds every banner's corners itself, and transparent corners would read as black holes in other launchers. `icon.xml` and the module-card labels are unchanged.
- Rejected: a `limit` query parameter on `/pill` (the contract would carry a launcher concern, and every reader would have to pass it); alternation turned off whenever the companion has two rows to give (a one-line launcher would then show one of two live matches for good); removing the long press from the pill (§9.2 names it); `UiDevice.pressKeyCode` for the OK test (UiAutomator's injection goes through the system and is no more bounded than Espresso's under load); the whole banner as one PNG (the icon would then live in two places).
- Consequences: `ScoreFeed.Listener.onPill` takes a `List<Row>` (empty for none), `TopBarFocusTest`'s bar takes a row count, `ScorePillTest` covers the stack and the line, `ScoreLineTest` the cut and the hold, `HomeFocusTest` the 1-, 3- and 5-line stacks against `ScoreFixture.STACK` (five events, two of them live, in the `:testkit:score` stand-in); `PillChooserTest` and `ScoreProviderTest` cover `ranked` and the multi-row cursor. The frosted and default themes carry `"score_rows": 3`.

## ADR-042 A sport icon on every score line, from the contract's `sport` value set
- Context: with the stack (ADR-041) a Home can show five lines from several sports at once, and only the kicker and the companion's text tell a cricket Test from a football match. The owner asked (2026-11-10) for a small icon per line, bundled in the launcher, chosen by the row's `sport` column.
- Decisions:
  - **`ScoreContract.SPORTS` is the value set.** `:core` gains `SPORT_BADMINTON` and `SPORT_HOCKEY` beside the five it had, and `SPORTS`, the array a reader iterates. The companion publishes the four it has a source for (`Sport` in `:livescore`, each token checked against `SPORTS` by `SportTest`); a value outside the set is not an error, it gets the generic icon, so a companion may add a sport before the launcher learns it.
  - **Eight hand-drawn vectors, one map.** `ic_sport_cricket` (bat and ball), `_football` (pentagon and seams), `_f1` (chequered flag), `_tennis` (racket and ball), `_badminton` (shuttlecock), `_basketball` (seams), `_hockey` (stick and ball), `_other` (a trophy): 24 dp outlines, stroke 1.8, white at 85 % in the path itself (`#D9FFFFFF`), each under 800 bytes, original work, no icon set copied. `topbar.SportIcons.forSport(String)` is a pure switch to a `@DrawableRes`, `nameOf` the matching `@StringRes` (`sport_cricket` … `sport_other`); `SportIconsTest` checks every `SPORTS` value maps to its own icon and name, never the fallback, and that null, empty, another case or an unknown sport gets the trophy.
  - **The icon is a view at the line's end, inside the shade.** `ScoreLineView` was a `TextView`; it is now a horizontal `LinearLayout` (the focus stop, the shade, the ring, the content description) holding the `TextView` (dot, kicker, line, 500 dp at most) and a 16 dp `ImageView` 7 dp after it, inflated from `score_line_content.xml` with `<merge>` so `new ScoreLineView(ctx)` and the layout inflation build the same row. The icon's tag is the drawable's entry name (`"ic_sport_cricket"`), so a device test can say which icon a line shows without reading pixels; both children are `importantForAccessibility="no"` and the row's description is `score_line_description`: kicker, line, a full stop, the sport's name ("LIVE T20I · AFG 19/4 (6) v ZIM. Cricket").
  - **The sport rides through the launcher as data.** `ScoreFeed.Row.sport` (empty when the column is missing), `ScoreLine.Text.sport` (trimmed, empty for null; `of` takes it after the event id), nothing else changes; the launcher still does no sports logic.
  - **The stand-in's stack carries three sports.** `ScoreFixture.STACK` row 1 is now a tennis match (`ATP Finals · Alcaraz 6–4 3–2 Sinner`), so cricket, tennis and football are all on screen in `HomeFocusTest.rule8_fiveRows`, which asserts each line's icon is visible, last in the row, inside the shade, tagged for its row's sport (`STACK_SPORTS`), and that the description ends in the sport.
- Rejected: a compound drawable on the `TextView` (no icon view to assert, and the owner asked for one); tinting in the `ImageView` (`android:tint` wants AppCompat's `app:tint` under lint; the colour belongs to the vector anyway, it is used nowhere else); a `switch` on the companion's `Sport` enum (the launcher cannot see Kotlin in `:livescore`, and the contract is the one place both sides read); adding `BASKETBALL`, `BADMINTON` and `HOCKEY` to the companion's enum (nothing fetches them; the contract documents the set, the companion emits what it has).
- Consequences: `ScorePillTest` reads the text child for the span, dot and colour and the icon child for the tag; `HomeTestRule.scoreLines` and `HomeFocusTest` read `ScoreLineView.text()`; a sport the companion adds later needs one vector, one string and two `case`s in the launcher, with `SportIconsTest` failing until it has them.
