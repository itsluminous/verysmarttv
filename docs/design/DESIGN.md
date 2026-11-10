# VerySmartTV — design

Status: **accepted** (owner decisions folded in on 2026-11-09; project renamed VerySmartTV). Every
decision below names its reason and the alternative it rejected; the one-line record of each is in
[DECISIONS.md](DECISIONS.md) (ADR-001 …). The items the first draft marked **[CONFIRM]** are resolved:
ADR-008 accepted, ADR-011 and ADR-032 rejected, ADR-029 changed, ADR-035 added.

Source studied: `/Users/kupraki/repo/cleanyourtv` at `e31f5eb0` (branch `main`): `launcher/` (83 Java
files, 18,285 lines, `MainActivity.java` 4,973 of them), `addons/traktshelf/` (18 files, 3,097 lines),
`tools/` (`tv.py` 746 lines, `recovery.py` 680, `check.py` 484, `disable.sh` 452), `examples/` (435
folders, 434 with a `tv.json`), `tests/` (≈300 Python test files). References: tv-town-launcher
(`Cricket.java`, `Scores.java`, `release.yml`, `THIRD_PARTY_NOTICES.md`), talkies (`README.md`,
`THIRD_PARTY_NOTICES.md`, `release-apk.sh`), HikViewer (`README.md`, `.github/workflows/ci.yml`,
`release.yml`, `app/build.gradle.kts`), and the ESPN feeds queried live on 2026-11-09.

---

## 1. Goals

1. One independent repository, `itsluminous/verysmarttv`, that builds three Android TV apps from one Gradle
   project: the launcher **`com.itsluminous.verysmarttv`**, the Trakt companion **`com.itsluminous.traktshelf`**
   and a new live-score companion **`com.itsluminous.livescore`**.
2. The launcher and traktshelf behave as they do in the fork today (every switch in
   `themes/mooq1-frosted/config.json` keeps working), with two visible changes: the ring gauges are
   replaced by a RAM/Storage two-bar block, and a live-score pill appears under the top-right pills.
3. Every identifier moves into the new namespaces. `grep -ri 'gathin\|tvhome\|cleanyourtv'` over the
   code tree returns only `LICENSE` and the README credits.
4. A test pyramid that lets agents change the launcher without a TV in front of them: JVM unit tests,
   Robolectric view tests, Espresso on the TV emulator, Python tests against a fake TV.
5. GitHub Actions: tests, lint and emulator tests on every push; signed APKs of all three apps with
   SHA-256 on every `v*` tag, one keystore, one version.
6. A setup script for macOS and Windows that finds the TV, installs the three apps from the latest
   release, sets Home, pushes a theme and offers a community debloat preset, with undo.

## 2. Non-goals

- A themes gallery, add-on gallery, press list or showcase. Two themes ship: `default` and `frosted`.
- Compatibility with cleanyourtv's module-card ecosystem under the old contract strings. Third-party
  cards written for `com.gathin.tvhome.category.DOCK_CARD` will not appear; the new contract is
  `com.itsluminous.verysmarttv.category.CARD` (section 7). Nobody outside this repo depends on it today.
- Upstreaming to Gathin23/cleanyourtv. The APK size gate (576 KB) and its PR rules no longer apply.
- A Play Store listing. Distribution is GitHub Releases (and Obtainium, which reads them).
- Phones. All three apps declare `android.software.leanback`; the livescore UI is D-pad only.
- Live-score coverage beyond what one free feed carries. The adapter boundary exists so a second
  source can be added, but v1 ships one.

---

## 3. What is carried, what is dropped

Requirement 7 ("no dead code, everything carried must be reachable") conflicts with requirement 2
("keep current behaviour and features") wherever a feature is reachable only through a theme key no
shipped theme sets. I resolved it by keeping everything the two shipped themes, the Mi TV 4A, and
**other people's TVs** need (device-compatibility code stays: the setup script targets 435 TV models),
and dropping decorative or superseded features. Every "drop" row is accepted (ADR-008).

| Area | Files (cleanyourtv `launcher/app/src/main/java/com/gathin/tvhome/`) | Decision | Why |
|---|---|---|---|
| Home, grid, dock, uniform rows, shelf, carousel, glass, ambient glow | `MainActivity`, `AppAdapter`, `Config`, `GlassView`, `DockDecoration`, `EdgeAlignDecoration`, `DockSlots`, `UniformRows`, `ShelfScale`, `HeroFeed`, `HeroArt`, `TopFade`, `Order`, `GridNavigation`, `Motion`, `TileRefresh`, `LastOpened`, `UiFont` | **Keep** (decomposed, section 8) | The product. |
| Settings page + side panel, backup/restore, theme provider | `LauncherSettingsActivity`, `SettingsPanel`, `HomeSettingsActivity`, `QuickSettingsActivity`, `Backup`, `BackupProvider`, `ThemeProvider`, `ThemeFiles` | **Keep**, minus the import row | Owner uses all of it. The **Import from another launcher** row and `Backup.KNOWN_PACKAGES` go (ADR-011): the launcher never queries another launcher's provider. Back up / Restore from file is the only path; the fork's backup JSON restores (§7.4), and the setup tool moves the file over ADB (§19). |
| App library | `AppLibraryDialog`, `LibraryApps`, `LibraryLoads` | **Keep** | In the frosted theme. |
| Module cards | `Cards`, `CardFeature` | **Keep**, renamed contract | traktshelf and livescore declare cards. |
| Remote keys, Home key, input key, guard, service repair | `RemoteKeys`, `HomeKeyService`, `InputKeyService`, `GuardKeep`, `GuardRevival`, `GuardConfig`, `ServiceRepair`, `VolumeKeys`, `GamepadPassthrough`, `InputsRequestActivity`, `AppLaunchActivity`, `ProbeActivity`, `OpenOnStart`, `LaunchActions`, `SystemSettings`, `InputNames`, `InputSources`, `InputFold`, `CoocaaSources` | **Keep** | Device compatibility for the TVs the presets cover (Xiaomi ALL_APPS key, TCL force-stop revival, Coocaa inputs). Small, reached by config keys. |
| Clock screensaver | `ClockDream`, `ClockActivity`, `ClockScene`, `ClockRefresh`, `ClockFormat` | **Keep** (minus wallpaper rotation hooks) | Owner's TV uses `ClockDream`. |
| Network/VPN pills | `VpnBadge` | **Keep** | 141 lines, a config key, harmless. |
| Memory | `MemInfo`, `MemoryTrimPolicy`, `StatsText` | **Keep** (`MemInfo` feeds the RAM bar) | |
| Locale | `LauncherLocale` | **Keep** | `locale` key. |
| **Widgets** | `GaugeView`, `CpuLoad`, the `show_stats` line (`startStats/readStats/statsLine`, `cpuPercent`, `cpuFromProcStat`, `cpuFromIdle`, `cpuClockLoad`) | **Drop; replace** with `ResourceBars` (section 9.1) | Requirement 2: only RAM and Storage, as two thin bars; no CPU anywhere. `show_stats` is the only other CPU consumer, so it goes with `CpuLoad`. |
| Festival / seasonal skins | `DiyaDrawable`, `MarigoldDrawable`, `BellDrawable`, `JackOLanternDrawable`, `SpookyIconDrawable`, `PetalShowerView`, `BatFlightView`, `Festival`, `FestiveLamp`, `applyFestiveSkin`, `playPetalShower` | **Drop** | ≈1,800 lines reachable only through `festival`/`seasonal_skin` keys of themes we do not ship (`dussehra`, `halloween`, `christmas`). |
| F1 weekend panel, game-day panel | `RaceWeekend`, `GameDay`, `tools/race_calendar.py`, the `game_day` provider contract | **Drop** | Superseded by the livescore companion (F1 is one of its sports). |
| Wallpaper rotation / scenes / moods / captions, time-of-day sky | `WallpaperPlayer`, `WallpaperRotation`, `WallpaperMood`, `WallpaperCaptions`, `WallpaperSize`, `Sky`, `SkyView`, `Sun`, `tools/scenes_wallpapers.py`, `tools/blocky_wallpapers.py` | **Drop** | The frosted theme is one static pre-blurred JPEG. `WallpaperSize` (34 lines, decodes the single wallpaper at screen size) stays. |
| Lunar / Shamsi calendars | `Lunar`, `Shamsi`, `res/values/lunar.xml` | **Drop** | Owner cannot verify either; `date_two_lines` already excludes them. |
| Hebrew mirrored layout | `res/layout-iw/activity_main.xml` (477 lines), `rtl_menu_bar`, `rtlAppGrid` branches | **Drop the duplicate layout**, keep `values-iw/strings.xml`, set `supportsRtl=true` | A second 477-line copy of the main layout must be edited for every top-bar change (the bars and the score pill both touch it). Android's own mirroring is "best effort, untested" in the README. |
| Translations | 30 `values-*/strings.xml` (69–186 strings each, English 187) | **Keep as pure string files** | They cost nothing at runtime (fall back to English for missing keys) and ~1–3 KB each in the APK. New strings are added in English only; lint's `MissingTranslation` is disabled for the launcher. Contributors may fill them. |
| Launcher docs | `docs/switches.md` (screenshot rule), `docs/modules.md`, `docs/specs/card.md`, `themes/README.md` Keys table | **Carry the Keys table and the card spec**, rewritten for the new names, into `docs/` | Needed by theme authors and card authors. The screenshot-per-key rule is dropped (it needs a real-TV capture per key, which only the owner can take). |
| traktshelf | all 18 files | **Keep**, package renamed | Requirement 2. |
| tools | `tv.py`, `b64push.py`, `recovery.py`, `check.py`, `theme_restart.py`, the guard logic of `disable.sh`, `adb-closed.py` | **Carry into the Python setup package** (section 16) | `bench.py`, `trace*.py`, `flamechart.py`, `screendiff.py`, `privacy.py`, `philips-api.py`, `manage.py`, `relay.py`, `gen_tables.py`, `readme_*.py`, `docker-build.*`, all `.ps1` twins: **drop** (perf tooling and site generators the owner will not run; the PowerShell twins are replaced by one Python implementation that runs on both OSes). |
| examples | 434 `debloat.txt` + `tv.json` | **Carry as `presets/`** (section 17) | Requirement 6. |
| tests | the fake-TV pattern of `tests/test_recovery_record.py`, list-format checks of `tests/test_examples.py`, `tests/test_core_packages.py`, `tests/test_home_guard.py` | **Carry the pattern and the rules**, rewrite for the new layout | ~290 other test files check cleanyourtv's docs, press counts, showcase, addons gallery: not carried. |

Lines of launcher Java carried: ≈15,000 of 18,285 before decomposition.

---

## 4. Architecture

```mermaid
flowchart LR
  subgraph TV["Android TV (1 GB class)"]
    subgraph L["com.itsluminous.verysmarttv (launcher, no INTERNET)"]
      HA[HomeActivity<br/>thin host]
      TB[TopBarController<br/>clock · date · pills · ResourceBars · ScorePill]
      SH[ShelfController<br/>app name / carousel]
      GR[GridController<br/>dock · rows · reorder]
      FR[FocusRouter<br/>pure Java rules]
      ST[Settings<br/>page + side panel]
      TP[(ThemeProvider<br/>.theme)]
      BP[(BackupProvider<br/>.backup)]
      HF[HeroFeed reader]
      SF[ScoreFeed reader]
      HA --> TB & SH & GR & ST
      TB --> FR
      GR --> FR
      SH --> HF
      TB --> SF
    end
    subgraph T["com.itsluminous.traktshelf"]
      HP[(HeroProvider<br/>.hero)]
      TS[Sync · JobScheduler 6 h]
      TU[Rows · Details · Sign-in]
    end
    subgraph S["com.itsluminous.livescore"]
      SP[(ScoreProvider<br/>.scores)]
      SR[Refresher<br/>stale-on-read + JobScheduler]
      SU[Follow picker · Scoreboard]
      AD[ScoreSource adapter<br/>EspnSource]
    end
    HF -- "query /items, openFile image/*<br/>signature permission" --> HP
    SF -- "query /pill, /events<br/>signature permission" --> SP
    SP --> SR --> AD
  end
  subgraph Net["Internet (companions only)"]
    TR[Trakt · TMDB · OMDb]
    ES[site.api.espn.com<br/>site.web.api.espn.com]
  end
  TS --> TR
  AD --> ES
  subgraph PC["Computer (macOS / Windows)"]
    SU2[setup.sh / setup.ps1 → verysmarttv_setup (Python)]
    ADB[pure-Python ADB<br/>tv.py carried]
    PR[presets/ index + 434 lists]
    GH[GitHub Releases<br/>signed APKs + SHA256SUMS]
    SU2 --> ADB --> TV
    SU2 --> PR
    SU2 --> GH
  end
```

Trust boundaries: the launcher never opens a socket. Both companions hold `INTERNET`. The launcher
reads the companions through content providers guarded by **signature-level permissions** (section
12.3), which works because all three APKs are signed with the one release key (debug builds share the
machine's debug key, so the emulator suite also passes).

---

## 5. Repository and module layout

```
verysmarttv/
├── settings.gradle.kts              # includes :core, :launcher, :traktshelf, :livescore, :testkit; includeBuild("build-logic")
├── build.gradle.kts
├── gradle/libs.versions.toml        # one version catalog for every module
├── gradle/wrapper/                  # Gradle 9.x wrapper (jar committed, as every Android repo does)
├── build-logic/                     # convention plugins (Kotlin DSL)
│   └── src/main/kotlin/
│       ├── verysmarttv.android.app.gradle.kts    # compileSdk/minSdk/targetSdk, Java 17, R8, packaging excludes,
│       │                                    # shared release signing, version from -PversionName, lint config
│       └── verysmarttv.android.lib.gradle.kts
├── core/                            # :core — com.android.library, Java. Provider CONTRACTS + tiny shared helpers.
│   └── src/main/java/com/itsluminous/verysmarttv/core/
│       ├── contract/HeroContract.java       # authority, URIs, columns, permission name (traktshelf → launcher)
│       ├── contract/ScoreContract.java      # authority, URIs, columns, permission name (livescore → launcher)
│       ├── contract/CardContract.java       # module-card category, meta-data name, spec validation constants
│       ├── ui/Glass.java                    # the frosted-glass drawable factory (pills, chips) shared by all three UIs
│       ├── ui/DpadKeys.java                 # isOk/isBack/direction helpers
│       └── text/Gb.java                     # "N GB free" formatting (one place, one test)
├── launcher/                        # :launcher — com.itsluminous.verysmarttv, Java (carried + decomposed)
│   ├── build.gradle.kts
│   └── src/{main,test,androidTest}/java/com/itsluminous/verysmarttv/...
├── traktshelf/                      # :traktshelf — com.itsluminous.traktshelf, Java (carried)
│   └── src/{main,test,androidTest}/java/com/itsluminous/traktshelf/...
├── livescore/                       # :livescore — com.itsluminous.livescore, Kotlin (new)
│   └── src/{main,test,androidTest}/kotlin/com/itsluminous/livescore/...
├── testkit/                         # :testkit — com.android.library, test-only: fake providers, D-pad Espresso helpers,
│   └── ...                          #   recorded JSON fixtures loader. Consumed as testImplementation/androidTestImplementation only.
├── themes/
│   ├── README.md                    # the Keys table (carried, renamed keys)
│   ├── default/config.json
│   └── frosted/{config.json,wallpaper.jpg,screenshot.jpg}     # mooq1-frosted, renamed; widgets key changed
├── presets/                         # debloat presets (section 17)
│   ├── README.md                    # the CleanYourTV MIT notice + list format
│   ├── index.json                   # generated by setup/tools/build_index.py; committed
│   └── lists/<slug>/{debloat.txt,tv.json}   # 434 folders, verbatim text (header paths rewritten)
├── setup/                           # the computer-side tool (section 16)
│   ├── setup.sh                     # bootstrap: find python3, venv, pip, run `python -m verysmarttv_setup`
│   ├── setup.ps1
│   ├── requirements.txt             # adb-shell, rsa, cryptography (what tv.py needs), zeroconf
│   ├── verysmarttv_setup/                # Python package
│   │   ├── __main__.py              # the guided flow
│   │   ├── adb.py                   # tv.py's client (connect, shell, push/pull, screenshot, batch)
│   │   ├── discover.py              # mDNS + /24 port-5555 scan + manual entry
│   │   ├── guide.py                 # USB-debugging walkthrough text, per brand
│   │   ├── release.py               # GitHub Releases API: latest tag, assets, SHA256SUMS verify
│   │   ├── install.py               # b64 push, pm install, grants, set-home, Home guard
│   │   ├── theme.py                 # push themes/<name> through the .theme provider
│   │   ├── presets.py               # match, show, apply, record, restore, check (recovery.py + disable.sh guard + check.py)
│   │   └── core_packages.py         # CORE_RE / CORE_OK: the never-disable list
│   └── tools/build_index.py         # regenerates presets/index.json from presets/lists
├── tests/                           # Python unittest: setup package against a fake TV, preset format, index freshness
├── docs/
│   ├── design/{DESIGN.md,DECISIONS.md}
│   ├── BUILDING.md                  # JDK/SDK/emulator, build, tests, signing (HikViewer style)
│   ├── themes.md → ../themes/README.md
│   ├── cards.md                     # module-card contract (carried spec, renamed)
│   ├── providers.md                 # the two companion contracts for other launchers/apps
│   └── badges/{get-it-on-github.png,get-it-on-obtainium.png}
├── .github/
│   ├── workflows/{ci.yml,release.yml}
│   ├── release.yml                  # release-notes categories
│   └── ISSUE_TEMPLATE/
├── README.md · LICENSE · THIRD_PARTY_NOTICES.md · CONTRIBUTING.md · CHANGELOG.md · AGENTS.md
└── .gitignore                       # local.properties, *.jks, release.properties, secrets.env, work/, shots/, backup/
```

### 5.1 Toolchain

| Choice | Decision | Reason | Rejected |
|---|---|---|---|
| Gradle layout | **One Gradle project, five modules** | One version catalog, one `gradlew ci`, one emulator run that installs all three debug APKs with the same debug key (the signature permissions need that), one release job. | Three projects (today's cleanyourtv layout): three wrappers, three catalogs, duplicated signing code, no way to share `:core` without publishing it. |
| AGP / Gradle | **AGP 9.x, Gradle 9.x** (the versions HikViewer pins today: AGP 9.5.1, Gradle 9.8.2; take the current stable at Phase 0) | Owner already maintains that toolchain in HikViewer; AGP 9 compiles Kotlin itself (no KGP for `:livescore`); local SDK has build-tools 37 and platform 37. | AGP 8.5.2 / Gradle 8.9 (cleanyourtv): works only with JDK 17–22, and two toolchains to keep in the owner's head. |
| JDK | **17** (Corretto 17 locally, Temurin 17 in CI) | AGP 9 minimum; cleanyourtv code is Java 17 source already. | 21: no benefit for Android targets. |
| compileSdk | **36** | Locally installed; AndroidX versions we need compile against it; no behaviour change from compileSdk alone. | 37: HikViewer uses it because of androidx.core 1.19; the launcher needs no such library. |
| targetSdk | **34** for all three (shared constant), re-evaluated after a real-TV pass in Phase 6 | Behaviour parity with the carried code (`launcher/app/build.gradle` line 24 `targetSdk 34`). 35 enforces edge-to-edge and tightens foreground-service and intent rules; a launcher with accessibility and notification-listener services is exactly where that bites. Not on Play, so no store rule forces it. | 35/36 now: untested behaviour on the one Android 14 TV we could try. |
| minSdk | **26** | `launcher/app/build.gradle` line 22; presets cover Android 8.0 sets (`skyworth-7s60-a5`). | 29 (the owner's TV): shuts out TVs the presets serve. |
| Language | **Java** in `:core`, `:launcher`, `:traktshelf` (carried, 101 files); **Kotlin** in `:livescore`; tests in the module's language | Converting 18k lines changes nothing visible and risks regressions in code that has no tests yet; `:core` must stay Java so the launcher pulls no Kotlin stdlib (it has none today, `packagingOptions` excludes `kotlin/**`). New networking/parsing code is shorter and null-safe in Kotlin, and the owner writes Kotlin daily (HikViewer). Mixed per module is allowed by requirement 9. | All-Java (one language, but new code in the less maintainable one); all-Kotlin (a multi-week conversion before any feature; stdlib in the launcher). |
| Kotlin extras in `:livescore` | **stdlib only; no coroutines, no kotlinx-serialization** | `HttpURLConnection` + `android.util.JsonReader` streaming parse + `Executors` match traktshelf's style and keep the APK small; the tennis scoreboard is 365 KB per fetch, which a tree parser would hold twice on a 1 GB TV. | coroutines (+~200 KB after R8, a second concurrency model next to traktshelf's threads), Retrofit/Moshi/OkHttp (three libraries for four URLs). |
| UI toolkit | **Views + RecyclerView** everywhere | Carried code is Views; Leanback's `BrowseFragment` is heavy (traktshelf already avoids it); Compose for TV adds ~2 MB. | Compose for TV. |
| Lint | Android Lint, `warningsAsErrors` in `:core`/`:livescore`, baseline file for the carried modules | The carried code has lint warnings we do not want to fix in the rename commit; a baseline freezes them so new ones fail. | Fix all up front (blocks Phase 1 on cosmetics). |

---

## 6. Launcher behaviour kept, and the two changes

Everything the fork README lists under "Switches added in this fork" and "Backup & restore" stays
with the same semantics and key names except these:

| Key / UI | Before | After |
|---|---|---|
| `widgets` (array of `memory`, `cpu`, `storage`) | three ring gauges | **`widgets` is a boolean**: the RAM + Storage two-bar block on or off (section 9.1). An array in an old theme is read as `true` if non-empty (migration). Settings row **Widgets** becomes one toggle "System bars (RAM, Storage)". |
| `show_stats` | a CPU/mem/storage text line under the clock | removed |
| `festival`, `seasonal_skin`, `game_day`, `race_weekend*`, `sky_intensity`, `latitude`, `longitude`, `wallpaper_rotation_minutes`, `wallpaper_moods`, `wallpaper_captions`, `shamsi_date`, `lunar_date` | features | keys ignored with one `Log.i("VerySmartTV.Config", "ignored key …")` each |
| (new) `score_pill` | – | boolean, default **false**; the live-score pill (section 9.2). Settings row **Live scores** (On / Off; the row says when the livescore app is not installed, as the Trakt row does today). |

---

## 7. Naming map (old → new)

Every string a grep for `gathin`, `tvhome`, `cleanyourtv`, `TvHome` finds in code, with its new value.
The rename is done by a scripted pass in Phase 1 whose test is exactly that grep.

### 7.1 Packages, application ids, namespaces

| Old | New |
|---|---|
| Java package `com.gathin.tvhome` (launcher) | `com.itsluminous.verysmarttv` (sub-packages after decomposition: `.home`, `.shelf`, `.grid`, `.topbar`, `.settings`, `.theme`, `.cards`, `.keys`, `.clock`, `.backup`, `.feed`) |
| applicationId `com.itsluminous.tvhome` / `com.gathin.tvhome` (via `LAUNCHER_PACKAGE`) | `com.itsluminous.verysmarttv`, fixed; the `launcherApplicationId` Gradle property and `LAUNCHER_PACKAGE` env var go |
| Gradle `namespace 'com.gathin.tvhome'` | `com.itsluminous.verysmarttv` |
| `rootProject.name = "tvhome"` / `"traktshelf"` | `verysmarttv` |
| Java package `com.itsluminous.traktshelf` | unchanged (already in the namespace) |
| Theme style `Theme.TvHome`, `Theme.TvHome.Settings` | `Theme.VerySmartTV`, `Theme.VerySmartTV.Settings` |
| Log tags `TvHome*` (`TvHomeHeroFeed`, `TvHomeCards`, `TraktShelf.Provider` …) | `VerySmartTV.*` (`VerySmartTV.HeroFeed`, `VerySmartTV.Cards`), `TraktShelf.*` unchanged, `LiveScore.*` |

### 7.2 Provider authorities

| Old | New | Permission |
|---|---|---|
| `${applicationId}.theme` (= `com.itsluminous.tvhome.theme`) | `com.itsluminous.verysmarttv.theme` | `android.permission.WRITE_SECURE_SETTINGS` (adb shell only), unchanged |
| `${applicationId}.backup` | `com.itsluminous.verysmarttv.backup` | none (read-only; `adb shell content read` must keep working), unchanged |
| `com.itsluminous.traktshelf.hero` | unchanged | **new:** `readPermission="com.itsluminous.traktshelf.permission.READ_HERO"`, `protectionLevel="signature"` |
| – | `com.itsluminous.livescore.scores` | `readPermission="com.itsluminous.livescore.permission.READ_SCORES"`, `signature` |
| `com.gathin.tvhome.backup`, `com.itsluminous.tvhome.backup` in `<queries>` and `Backup.KNOWN_PACKAGES` | removed with the import row (ADR-011); no `tvhome` authority anywhere in the tree | |
| – | `com.itsluminous.livescore.scores/backup` (read) and `call("restore")` on the same provider | `READ_SCORES` / `WRITE_SCORES`, both `signature` (§12.2, ADR-032) |

### 7.3 Intent actions, categories, extras, meta-data

| Old | New |
|---|---|
| `com.gathin.tvhome.category.DOCK_CARD` (module-card category) | `com.itsluminous.verysmarttv.category.CARD` |
| `com.gathin.tvhome.card` (module-card meta-data) | `com.itsluminous.verysmarttv.card` |
| `com.gathin.tvhome.games` (game_day meta-data) | removed |
| `com.gathin.tvhome.action.LAUNCHER_SETTINGS` | `com.itsluminous.verysmarttv.action.LAUNCHER_SETTINGS` |
| `com.gathin.tvhome.action.APP_LIBRARY` | `com.itsluminous.verysmarttv.action.APP_LIBRARY` |
| `com.gathin.tvhome.extra.REORDER_FROM_SETTINGS` | `com.itsluminous.verysmarttv.extra.REORDER_FROM_SETTINGS` |
| `com.gathin.tvhome.GUARD` (GuardConfig broadcast) | `com.itsluminous.verysmarttv.action.GUARD` |
| `Cards` label blocklist entry `"tvvibehack", "cleanyourtv"` (`Cards.java:82`) | `"verysmarttv"` |

### 7.4 Files, prefs, formats

| Old | New |
|---|---|
| `Order.MAGIC = "tvhome-order 1"` (`Order.java:35`) | `"verysmarttv-order 1"`; the reader also accepts `tvhome-order 1` so a Backup JSON from the fork restores (`Backup.java:48` embeds it) |
| Backup file `tvhome-<stamp>.json` (`Backup.java:242`) | `verysmarttv-<stamp>.json`; Restore lists both patterns |
| Backup JSON `"package": "com.gathin.tvhome"` | `"package": "com.itsluminous.verysmarttv"`; restore accepts any of the three known packages |
| SharedPreferences names (`Config.SETTINGS_NAME`, `"boot"`, `"wallpaper_rotation"`, `GuardKeep.PREFS`, `LastOpened.PREFS`, `ServiceRepair.PREFS`) | unchanged names (they are app-private; renaming gains nothing and breaks Import) except `"wallpaper_rotation"`, which goes with the feature |
| Screensaver component `…/com.gathin.tvhome.ClockDream` | `com.itsluminous.verysmarttv/.clock.ClockDream` |
| Accessibility services `…/com.gathin.tvhome.HomeKeyService`, `.InputKeyService` | `com.itsluminous.verysmarttv/.keys.HomeKeyService`, `.keys.InputKeyService` |
| Config dir `Android/data/<pkg>/files/` (`Config.dir`) | same path under the new package |
| Theme folder `themes/mooq1-frosted` | `themes/frosted` |
| `HeroFeed.PACKAGE = "com.itsluminous.traktshelf"`, `DETAILS_ACTIVITY` | moved to `core/contract/HeroContract` |
| `~/.cleanyourtv/recovery-*.json` (recovery records) | `~/.verysmarttv/recovery-<tv-id>.json`, keyed by **`ro.serialno` hash + model**, not by IP (section 17.4) |
| `tools/venv`, `TV_HOST` | `setup/.venv`; `TV_HOST` still honoured as an override, otherwise the discovered/entered address is saved in `~/.verysmarttv/last-tv.json` |

### 7.5 Resources

| Old | New |
|---|---|
| `<com.gathin.tvhome.SkyView>`, `<com.gathin.tvhome.GlassView>` in `activity_main.xml` | `SkyView` removed; `<com.itsluminous.verysmarttv.home.GlassView>` |
| `@string/app_name` "Clean Your TV" (fork: launcher label) | "VerySmartTV" |
| `@string/home_key_name`, `@string/dream_name` | texts say VerySmartTV |
| `R.id.*`, drawable names | unchanged where not branded; `ic_tvhome_*` → `ic_verysmarttv_*` |
| `res/xml/locales_config.xml` | unchanged (31 locales) |

### 7.6 Docs and scripts

| Old | New |
|---|---|
| `launcher/build-and-install.sh` (+`.ps1`), `tools/theme.sh` (+`.ps1`), `tools/disable.sh`, `tools/restore.sh` | `setup/verysmarttv_setup/{install,theme,presets}.py`; shell left is only the two bootstraps |
| `PROMPT.md`, `AGENTS.md` (cleanyourtv's) | `AGENTS.md` rewritten for this repo: build, test, rename invariants, the 1 GB rule, "never print secrets" |

---

## 8. Launcher decomposition

`MainActivity.java` is 4,973 lines with 180 methods (listing taken 2026-11-09). It is one class because
upstream optimises for a 576 KB APK and one-file readability; we optimise for testability. The split
below groups the methods by the state they share, so each piece owns its fields and the activity only
wires them. Line numbers refer to the cleanyourtv file.

| New class (package `com.itsluminous.verysmarttv.…`) | Carries from `MainActivity` | Pure JVM-testable? |
|---|---|---|
| `home.HomeActivity` (~400 lines) | `attachBaseContext` 102, `onCreate` 504 (wiring only), `onNewIntent` 1161, `onStart/onResume/onPause/onStop/onDestroy`, `onTrimMemory` 1476, `onWindowFocusChanged` 1494, `onActivityResult` 1276, `onBackPressed` 1891, `onUserInteraction` 1415 | No (Robolectric smoke) |
| `home.HomeHost` (interface) | what controllers need from the activity: `findViewById`, `getConfig()`, `open(Intent)`, `post(Runnable)`, `density()`, `isRtl()` | – |
| `home.HomeState` | the booleans/ints that today are activity fields read by many methods: `singleRow`, `uniformRows`, `shelfEveryRow`, `heroOwned`, `pillsOverArt`, `COLUMNS`, reorder/dock-edit mode | Yes |
| `keys.KeyDispatcher` | `dispatchKeyEvent` 1555, `handleSettingsKey` 1530, `holdOk/cancelHoldOk/fireHoldOk` 4642–4706, `isOkKey` 4636; delegates to the controller that owns focus | Yes (takes `KeyEvent` → verdict; Robolectric for `KeyEvent` construction) |
| `grid.GridController` | `reloadApps*` 1303–1375, `restoreFocus` 1399, `focusTile` 3512, `cancelPendingFocusScroll` 3504, `refocusAfterLayout` 1873, `keepScroll` 1853, `onTileFocused` 1930, `onTileLongPress` 4706, `onTileVerticalKey` 3277, `packagesChangedSinceRebuild` 1360 | Partly: it calls `grid.FocusRules` for every decision |
| `grid.FocusRules` (**new, pure**) | the arithmetic now inline in `onTileVerticalKey` 3277–3324 + `GridNavigation`: given (position, count, columns, dockFilled, rtl, singleRow, uniformRows, keyCode) → `Move{target}` / `LeaveUp` / `Swallow` / `FocusLibrary` | **Yes**: the heart of the Espresso-free tests |
| `grid.ReorderSession` | `toggleReorder` 1629 … `moveBy` 1823 (reorder, pick up, drop, dim) | Yes for the order maths (uses `Order`) |
| `grid.DockEditSession` | `startDockEdit` 1656 … `swapIntoDock` 1742 | Yes |
| `shelf.ShelfController` | `showHero` 2042, `fillHero` 2120, `heroOwned/dockTileFocused/shelfTileFocused` 2168–2187, `applyTileNames` 2187, `showOwnedShelf` 2199, `shelfTitleStyle` 2391, `fitCarouselTitle/unfitCarouselTitle/oneLinePx/shelfTitleSp/shelfGapPx/gridTopInRoot` 2409–2474, `hideHero` 2866, `setUpCinematicShelf` 2628, `scrimDrawable` 2649, `showHeroArt/fadeHeroArt/updateHeroMeta/updateHeroDots/drawDots` 2668–2761 | Layout maths (`ShelfScale`) yes; views via Robolectric |
| `shelf.CarouselController` | `setUpHeroFeed` 2130, `showCarousel/showCarouselItem/addFeedChip/placeCarouselShelf` 2253–2391, `fitFeedArt/setPillsOverArt/exitCarousel/handleCarouselKey/openFeedItem` 2474–2628, `topMargin/shelfMargin` | Rotation timer and item index: yes |
| `shelf.AmbientGlow` | `setAmbient/transitionAmbient/stopAmbientAnimation/showGlowOnly` 2761–2866, `refitGlass/hideGlass` 2019, 2884 | Colour maths yes |
| `topbar.TopBarController` | `updateClockVisibility` 3559, `updateGreeting` 3570, `updatePanel` 3631, `showPager` 3684, `refreshPillsAfterNextDraw` 3753, `updateWifiLabel` 3773, `makeIconOnly/labelOnFocus/addFocusAction` 3897–3983, `openNetworkPill/vpnAppIntent/openVpnPill/updateVpnPill` 4018–4083, `updateInputsButton` 4088, `addTopBarApps` 3423, `firstTopButton` 3402, `dateOnTwoLines` 3101, `moveDateBelowClock/centerCompactHeader/alignDateBelowIconsWithClock` 3334–3402, `focusLibrary` 3466 | Pill order (`topbar.PillOrder`, pure) yes |
| `topbar.ResourceBars` (**new view**) + `topbar.ResourceSampler` | replaces `setUpWidgets/widgetPercents/showWidgetValues` 3025–3100 and the stats loop 2899–3025; sampler reads `MemInfo` + `StatFs("/data")` every 5 s while Home is resumed | Formatting (`core.text.Gb`) yes; sampler with injected readers yes |
| `topbar.ScorePill` (**new view**) + `feed.ScoreFeed` (reader, mirrors `HeroFeed`) | new | Line formatting yes; reader against a fake `ContentResolver` (Robolectric) |
| `topbar.TopBarFocus` (**new, pure**) | the rule "Up from grid → score pill if shown, else Inputs; Up from score pill → Inputs; Left/Right along the bar; Down → grid", section 9.3 | **Yes** |
| `inputs.InputsMenu` | `showInputs` 4276 (209 lines), `fold` 4485, `addSeparator/addInputRow` 4514–4553, `openInput/mappedSource/inputActionIntent/openSourcePanel/liveTvIntent` 4150–4276, `onOpenInput` 4614, `applyViewInputsHandler` 1088, `openInputsIfAsked` 1103 | Row model (`inputs.InputRows`: which HDMI rows, states, labels) yes |
| `menus.GlassPopups` | `glassPopup/dropIn` 4490–4514, `showTopMenu` 4553, `showSettingsMenu` 4582, `addSettingsMenuRow` 4755 | No (Robolectric smoke) |
| `cards.CardPrompts` | `showCardPrompt/showCardPromptNow/decideCard/showModuleCards/showModuleCardsNow/cardFor/updateCardStates/stateText` 4789–4973 | Decision model yes (`Cards` already has JVM tests) |
| `home.StartupRouter` | `openLiveTvAfterBoot/openStartAppOnWake/openWhenReady` 4112–4150, `openLibraryIfAsked/openLauncherSettingsIfAsked` 1138–1161, `startPendingSettingsReorder` 1295, `settingsRevision` 1267 | Yes with injected clock/prefs |
| `clock.IdleClock` | `restartIdleClock` 1420 and the idle timer | Yes |

Rules for the split (so Phase 2 does not drift into a rewrite):

1. Move methods verbatim first (same bodies, fields become controller fields), commit, run the Espresso
   smoke; only then refactor a controller internally. Each controller's constructor takes `HomeHost`
   and the views it owns; no controller reaches into another's views.
2. Any `if` that decides *where focus goes* or *which rows show* moves into a pure class
   (`FocusRules`, `TopBarFocus`, `PillOrder`, `InputRows`, `ShelfScale`) returning a value; the
   controller only executes it. That is what makes the Up→score→Inputs rule unit-testable.
3. `HomeActivity` keeps under 500 lines; CI fails a PR that pushes any file in `:launcher` over 1,200
   lines (a small Gradle task; `AppAdapter` 1,135 and `Config` 1,128 are at the limit and get split
   into `Config` + `ThemeKeys` + `SettingsChoices`, and `AppAdapter` + `TileBinder`).
4. All user-facing text lives in string resources (ADR-035): no literal shown to the user in Java or
   Kotlin, in every module. Lint's `HardcodedText` and `SetTextI18n` stay errors; a string the launcher
   builds from a companion row (the pill line, the carousel meta) is data, not UI text, and is exempt.

---

## 9. Top bar: bars, score pill, focus

### 9.1 ResourceBars (replaces the gauges)

Target layout, from `/private/tmp/live_score.jpg` (1280×720 frame): top-left a small caps weather line
("23° PARTLY CLOUDY"), under it a large thin clock "8:42 PM", a vertical hairline, then "THURSDAY 29
OCTOBER" in tracked caps. Top-right, left of the pills: a compact block about 150 px wide and two rows
tall; each row is a label at the left ("RAM", "Storage") and a right-aligned value ("1.1 GB free",
"21.6 GB free") on one baseline, with a thin track bar underneath spanning the block, the filled part
light and the rest dark. Right of it, four round frosted pills (sliders, display, Wi-Fi, gear). Under the
pills, right-aligned to the last pill: a red dot, "LIVE" in red caps, then "T20I · AFG 19/4 (6) v ZIM"
in white, one line, small. The rest of the image (content rows, a dock of app banners) is another
launcher and not a target.

We take the RAM/Storage block and the score line; the weather line is out of scope (no network in the
launcher, and no companion for it).

`ResourceBars` is one custom `View` (not two `ProgressBar`s and four `TextView`s) that draws:

- two rows, 12 sp label left, 12 sp value right (`core.text.Gb.free(bytes)` → "1.1 GB free", one decimal
  under 10 GB, none above, `MB` under 1 GB), 2 dp track at 30 % white, fill at 85 % white, rounded ends;
  block width 150 dp, row pitch 22 dp; over the carousel's art the track darkens like the pills do
  (`setOverArt`, carried behaviour from `GaugeView`).
- RAM = `MemInfo` (`/proc/meminfo` MemAvailable), Storage = `StatFs(Environment.getDataDirectory())`.
  No CPU. Sampled every 5 s by `ResourceSampler` only while Home is resumed (today's `startStats/stopStats`
  lifecycle), read on a background thread, applied on main.
- Placement: in the header row between the date column and `topButtons`, right-aligned to the pills'
  left edge minus 16 dp, vertically centred on the pills. In `date_two_lines` the header height is
  unchanged (two 12 sp rows + 2 dp bars fit the 48 dp pill height).
- Setting: `widgets` boolean (theme) / Settings row **System bars** (On/Off, picker marks the value in
  force, theme's until chosen; same pattern as Trakt recommendations).

### 9.2 ScorePill

A `TextView`-based pill under the right end of `topButtons`, right-aligned with the last pill, 8 dp
below the pill row, one line, 13 sp: a leading state glyph and kicker in the state colour, then the
line in white. *Follow-up (ADR-041):* the pill stacks up to `score_rows` such lines (1 to 5, default 3), one per
followed event the companion ranks, right-aligned under one another, each on its own 10 % shade; the stack is one focus
stop whose lines Up and Down walk (§9.3 rule 8).

| Provider `state` | Kicker | Example line |
|---|---|---|
| `live` | red dot + "LIVE" (red `#E53935`) | `T20I · AFG 19/4 (6) v ZIM` |
| `break` | amber dot + the feed's word ("STUMPS", "HT", "RAIN") | `Test · BAN 386 & 49/2 v WI` |
| `upcoming` | grey "19:30" (local start time, or "TOMORROW 19:30") | `ISL · Bengaluru v Mumbai City` |
| `final` | grey "FT" / "RESULT" | `T20I · AFG won by 23 runs` (shown for 6 h after the end) |
| `none` / provider missing / permission denied | pill hidden (`GONE`); the layout above does not move | |

The text comes ready-made from the provider (`/pill` row, section 12.2): the launcher does no sports
logic, exactly as `HeroFeed` takes the companion's `meta` line. The pill is focusable only when visible;
it draws the same frosted focus ring as the round pills (`core.ui.Glass`). OK opens
`com.itsluminous.livescore/.ScoreboardActivity` with `event_id` (or the app's main activity when the
row has no event). Long-press OK opens the livescore app's follow picker.

Reader `feed.ScoreFeed`: registers a `ContentObserver` on `/pill`, re-queries on change (coalesced
800 ms like `HeroFeed`), and additionally re-queries every 60 s while Home is resumed **and** the last
row said `live` or `break` — that read is what tells the companion a screen is watching (section 13.5).
When the provider throws `SecurityException` (different signing key) the pill hides and one warning is
logged; no retry loop.

### 9.3 Focus and navigation rules (`topbar.TopBarFocus`, pure)

Inputs are the current focus, the key, and the bar model `(scorePillShown, pills in bar order)`.

1. **Up from any tile in the first row of the grid** (dock, or row 0 under `uniform_rows`):
   - if the score pill is shown → focus **the score pill**;
   - else → focus **Inputs** (today `firstTopButton()` 3402 returns the first visible pill after
     Inputs is excluded; the new rule names Inputs explicitly, falling back to the first visible pill
     when `show_inputs_pill` is false).
   The last-focused pill is **never** restored by Up; the "remembered focus" behaviour of
   `RecyclerView`'s default focus search is overridden by `GridController` returning `true` from
   `onTileVerticalKey` for Up and calling `TopBarFocus.fromGridUp()`.
2. **Up from the score pill** → **Inputs** (same fallback). **Up from a round pill** → stays (swallowed).
3. **Down from the score pill or any pill** → the grid tile that was focused last (`GridController`
   remembers it; today `restoreFocus` 1399), else dock slot 0.
4. **Left/Right on the score pill** → swallowed (it is alone on its row). Left/Right on the round pills
   → along the bar, ends swallowed (carried).
5. **Carousel focus** (hero shelf owns focus after Up from the dock when `hero_feed` has items): the
   carousel sits between the grid and the bar. Order becomes grid → carousel → score pill → Inputs. Up
   from the carousel lands on the score pill when shown, else Inputs; Down from the score pill lands on
   the carousel when it is showing, else the grid. This keeps today's carousel keys (`handleCarouselKey`
   2558) intact and inserts the pill as one more stop.
6. **Back** anywhere in the bar → the grid (carried `onBackPressed` semantics).
7. **RTL**: bar order is mirrored by the framework (`supportsRtl`); the rules are expressed in bar order,
   not screen side, so they need no RTL branch.

8. **The stacked pill is one stop** (ADR-041): Up from the grid or the carousel lands on its **bottom** line, the
   one nearest the grid; inside it Up and Down move one line at a time; Up from the top line is rule 2 (Inputs), Down
   from the bottom line is rule 3 (the carousel while it shows, else the grid); Left/Right, Back and OK are the same on
   every line, OK opening that line's event.

Every rule above is a JVM test case in `TopBarFocusTest`; the five that involve real views (Up from a
dock tile with/without the pill, Up from the pill, Down back, Back) are also Espresso cases on the TV
emulator (section 14.3), and rule 8 for 1, 3 and 5 lines against the stand-in's five-row fixture.

---

## 10. Settings model

Carried as is: `Config` reads the theme `config.json` (keys table in `themes/README.md`), then overlays
the on-TV choices in `SharedPreferences` `Config.SETTINGS_NAME` (`Config.java:777`); a theme push or
Restore clears the choices (fork README, "Settings" row). Rows and their stored keys after this design:

| Row (panel / page) | Stored choice key | Values | Theme key it overrides |
|---|---|---|---|
| TV system settings (first when `tv_settings_first`) | – | opens `android.settings.SETTINGS` | |
| Reorder apps, Add to dock, Edit dock (hidden under `shelf_every_row`) | order file | | `dock_apps` seed |
| Apps per row | `columns` | 4–7 (8 with `uniform_rows`) | `columns` |
| Rows below dock (hidden under `uniform_rows`) | `rows_below_dock` | | `rows_below_dock` |
| Dock band opacity (hidden under `uniform_rows`) | `dock_opacity` | | `dock_opacity` |
| Trakt recommendations | `hero_feed` | On/Off | `hero_feed` |
| Hero banner height | `hero_height` | 60/80/100/120 (+ theme's own) | `hero_height` |
| **System bars** (was Widgets) | `widgets` | On/Off | `widgets` |
| **Live scores** (new) | `score_pill` | On/Off; row subtitle "LiveScore app not installed" when `ScoreContract` resolves to nothing | `score_pill` |
| **Live score rows** (ADR-041) | `score_rows` | 1–5 | `score_rows` |
| Animations | `animations` | On/Off | `animations` (+ `advanced_effects`) |
| Module cards | per-card decision prefs | Shown/Hidden | |
| Backup & restore: Back up, Restore from file | files (`companions` section through each companion's `/backup`, §12.2) | | |
| App library rows (Add/Remove) | library prefs | | `library_apps` |

Persistence tests: a JVM test per row round-trips the choice through `Config` with a Robolectric
`SharedPreferences`; an Espresso test changes **Live scores** and **System bars** in the panel, presses
Home, and asserts the views' visibility.

---

## 11. Theme model

Unchanged mechanism: a folder with `config.json` (+ optional `wallpaper.jpg`) pushed to
`Android/data/com.itsluminous.verysmarttv/files/` through `content://com.itsluminous.verysmarttv.theme/<file>`
(adb shell holds `WRITE_SECURE_SETTINGS`; `ThemeProvider` carried). `theme_revision` bumps so Home
reloads (`ThemeFiles.java:20`).

Shipped themes:

- `themes/default/config.json`: the launcher's built-in values, black background, dock row, 5 columns.
  Pushing it is how "reset" works (`setup theme --reset` deletes the folder instead, as `theme.sh --reset`).
- `themes/frosted/`: `mooq1-frosted` with `"widgets": true` and `"score_pill": true`; `wallpaper.jpg`
  carried (owner's own picture, 1920×1080 pre-blurred); `screenshot.jpg` retaken in Phase 6.

Keys table: `themes/README.md` carries the current Keys table minus the dropped keys (section 3) and
plus `score_pill`; `widgets` re-typed. A JVM test (`ThemeKeysTest`, carried idea from
`tests/test_theme_keys_config.py`) asserts every key in the table is read by `Config` and every key
`Config` reads is in the table, so the doc cannot drift.

---

## 12. Companion provider contracts

Both contracts live in `:core` as Java constants (`HeroContract`, `ScoreContract`) that the companion's
provider and the launcher's reader both compile against: a column renamed in one place fails the other's
build. `docs/providers.md` is generated from the Javadoc of those two classes (a Gradle task) so other
launchers can read them.

### 12.1 HeroContract (traktshelf → launcher) — carried

Authority `com.itsluminous.traktshelf.hero`. Carried verbatim from `HeroProvider.java:28-41`:

| URI | Returns |
|---|---|
| `content://…hero/items` | `MatrixCursor` columns `_id, kind, title, year, synopsis, imdb_rating, rt_rating, trakt_rating, imdb_id, trakt_id, poster_path, backdrop_path, source_row, rank, meta, next_episode`; ranked; notifies observers after sync and settings change |
| `content://…hero/image/backdrop/<_id>`, `…/image/poster/<_id>` | JPEG via `openFile` (`image/jpeg`) |

Change: `readPermission` `com.itsluminous.traktshelf.permission.READ_HERO` (`signature`). The launcher
declares `<uses-permission>` for it. `insert/update/delete` keep returning nothing.

### 12.2 ScoreContract (livescore → launcher) — new

Authority `com.itsluminous.livescore.scores`; `readPermission` `com.itsluminous.livescore.permission.READ_SCORES` (`signature`).

| URI | Rows | Columns |
|---|---|---|
| `content://…scores/pill` | exactly one | `state` (`live`,`break`,`upcoming`,`final`,`none`), `kicker` (text, may be empty), `line` (text, ≤ 48 chars, already shortened for one line), `event_id` (text, nullable), `sport` (`cricket`,`football`,`f1`,`tennis`,`basketball`), `competition` (text), `updated_ms` (int64, when the data was fetched), `stale` (int, 1 when `updated_ms` is older than the refresh interval for that state), `color` (`#RRGGBB` for the kicker) |
| `content://…scores/events` | followed events from yesterday 00:00 to tomorrow 23:59 local, live first then by start | `_id`, `event_id`, `sport`, `competition`, `format` (T20I/ODI/Test/…; empty for other sports), `state`, `status_detail` (feed's words: "Stumps", "Day 5", "Lunch", "HT", "Q3"), `start_ms`, `end_ms` (nullable), `home_name`, `home_abbr`, `home_score`, `away_name`, `away_abbr`, `away_score`, `summary` (feed's sentence: "Bangladesh require 320 runs"), `followed_reason` (`team`,`competition`,`event`), `updated_ms` |
| `content://…scores/events/<event_id>` | one | same columns |
| `content://…scores/backup` | exactly one | `json` (text): the companion's follow list as the JSON it stores on disk (§13.2), so the launcher's Backup file carries it under `companions.livescore` without parsing it |

Restore goes the other way through `ContentProvider.call()`: `call("restore", null, Bundle{"json": …})`
replaces the follow list and notifies `/pill`; it returns `Bundle{"ok": true}` or `{"ok": false,
"error": …}`. `call` is guarded by the provider's `writePermission`
`com.itsluminous.livescore.permission.WRITE_SCORES` (`signature`), so only the launcher restores.
HeroContract has no backup path: traktshelf's state is OAuth tokens, which are never backed up (ADR-032).

Semantics the launcher relies on:

- `/pill` is **always answerable** without network: it is computed from the on-disk cache; when there is
  no followed event in the window it returns `state=none`. A query never blocks on a fetch.
- A query of `/pill` or `/events` is also the companion's signal that a screen is looking (section 13.5).
- Observers of `/pill` are notified after every successful refresh and after the follow list changes.
- `getType` returns `vnd.android.cursor.item/vnd.com.itsluminous.livescore.pill` and
  `vnd.android.cursor.dir/vnd.com.itsluminous.livescore.event`.
- Images (team logos) are **not** in v1 (the pill has no room; the scoreboard UI loads them itself).
  The `image/team/<id>` path is reserved in the contract doc, not implemented.

Pill selection (in the companion, `PillChooser`, pure Kotlin, fully unit-tested): among followed events in
the window pick the first by: live > break > upcoming within 12 h > final within 6 h; ties by the follow
reason (`event` > `team` > `competition`), then earliest start. Several live → the one followed by team
wins; still tied → alternate every 20 s between them (the companion notifies; the launcher re-reads), so
two simultaneous cricket matches both get shown.

Line format per sport (pure, fixture-tested): cricket `"<format> · <bat_abbr> <runs>/<wkts> (<overs>) v <other_abbr>"`
for limited overs; Tests `"<format> · <abbr> <inn1> & <runs>/<wkts> v <abbr>"` (ESPN's `score` string
already reads `386 & 49/2 (14 ov, target 369)`; we strip the parenthesis to overs only, as
tv-town's `OVERS_PART` does). Football `"<comp> · <home_abbr> <h>–<a> <away_abbr> <minute>'"`. F1
`"F1 · <GP short name> · <session>"` and `"LEADER <driver>"` when the feed gives a leader. Tennis
`"<tournament> · <player A> <sets> <player B>"` with the set score of the current set.

### 12.3 Permissions

| Provider | Protection | Why |
|---|---|---|
| `…traktshelf.hero`, `…livescore.scores` | `signature` custom permission declared by the companion (`<permission android:protectionLevel="signature">`), held by the launcher via `<uses-permission>`; `…livescore.scores` adds a `signature` `writePermission` for `call("restore")` | Requirement 5. Only an app signed with our key reads a user's Trakt rows or followed teams. Works for debug builds on one machine (same `debug.keystore`) and in CI (all three debug APKs built in one Gradle invocation). Documented consequence: a launcher built by someone else from source cannot read a companion from our Releases; they build all three. |
| `…verysmarttv.theme` | `WRITE_SECURE_SETTINGS` (adb shell) | carried |
| `…verysmarttv.backup` | none, read-only | carried: `adb shell content read` must work for the setup tool's backup step; the data is the app order and hidden apps |

`<queries>`: the launcher lists both companion authorities (package visibility on Android 11+), carried
pattern from the manifest lines 72–76.

---

## 13. The livescore companion

### 13.1 Data-source evaluation

Criteria from requirement 3: no key or a free key the user makes; ToS allows personal use; covers
cricket (IPL + internationals) + football + F1 + tennis; stable; rate limits fit 60 s polling during a
live match (1 fetch/min per followed competition, ≈ 60–300 requests per match-day per competition).

| Source | Key | Cricket | Football | F1 | Tennis | Rate limit vs 60 s polling | ToS for personal use | Stability | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| **ESPN site feeds** `site.api.espn.com/apis/site/v2/sports/<sport>/<league>/scoreboard`, `site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=YYYYMMDD` | none | **Yes**: scorepanel lists every match of the day across leagues (IPL = league `8048`, internationals, domestic); per-innings runs/wickets/overs, status words (Live/Stumps/Lunch), summary sentence | **Yes**: `soccer/eng.1`, `ind.1` (ISL), `uefa.champions`, `esp.1`, `fifa.world`, … | **Yes**: `racing/f1` | **Yes**: `tennis/atp`, `tennis/wta` | No published limit; tv-town-launcher has polled it at 60 s while on, 30 min quiet since 2026 (`Cricket.java:33` `WHILE_ON = 60_000L`) | **Undocumented**: ESPN publishes no API terms; ESPN's site terms govern. Personal, non-commercial display of scores with attribution is what tv-town ships and credits ("from ESPN's public site feeds… no affiliation"). Risk: it can change or stop without notice. | Used by many open-source scoreboards for years; verified 2026-11-09: all five endpoints HTTP 200, cricket scorepanel 110 KB in 1.5 s, soccer 66 KB, F1 44 KB, tennis **365 KB** | **Primary** |
| CricAPI / cricketdata.org | free key | Yes (IPL, intl) | – | – | – | **100 hits/day** on the free plan → 1 request every 14 min; 60 s polling exhausts it in 100 minutes | Allowed with key | Stable, commercial | Rejected (limit, cricket-only, key) |
| TheSportsDB v1/v2 | free key `123` (v1) | Schedules yes, **livescores Premium only** ("Premium… extra stuff like livescores", docs) | same | same | same | 30 req/min free | Allowed | Stable | Rejected for live; candidate for team logos later |
| football-data.org | free key | – | 12 competitions, no ISL | – | – | 10 calls/min free; live match detail is paid | Allowed with key | Stable | Rejected (football-only, limit, no India league) |
| API-Football (api-sports.io) | free key | – (separate paid APIs per sport) | Yes | – | – | 100 req/day free | Allowed with key | Stable | Rejected (limit) |
| Sofascore / Cricbuzz / ESPNcricinfo hidden endpoints | none | Yes | Yes | Yes | Yes | unknown | Unofficial, actively blocked at times (Cloudflare challenges) | Fragile | Rejected |
| Jolpica (F1 only, Ergast successor) | none | – | – | Yes (results, no live) | – | 200 req/h | Allowed | OK | Not needed; ESPN has F1 sessions |

**Decision: ESPN's site feeds are the primary and only v1 source** (ADR-012). They are the only option
that meets all four sports without a key and without a daily cap, and they are what the reference
launcher proved in production. The adapter boundary (13.3) is where a keyed source goes if ESPN closes
the feed; the README's Disclaimer says so in the same words tv-town uses.

Payload-size handling for a 1 GB TV: parse with `android.util.JsonReader` streaming, skipping
sub-trees we do not read (`odds`, `broadcasts`, `highlights`, `venue`, athletes' headshots); never hold
the raw text (`Http.stream()` returns the `InputStream`). Tennis is fetched only when a tennis follow
exists. Measured upper bound to design to: 365 KB of JSON per minute = negligible bandwidth, but a tree
parse would allocate ~3 MB of `JSONObject`s per fetch, which `JsonReader` avoids.

### 13.2 Follow model

The user follows **sports → competitions → teams/players**, and may pin single **events**:

```
Follow { kind: SPORT | COMPETITION | TEAM | EVENT, sport, competitionId?, teamId?, eventId?, label }
```

- Following a *sport* means every competition of that sport the adapter knows (cricket: the whole
  scorepanel; football: the adapter's league list). Following a *competition* means all its events.
  Following a *team* means its events in any competition. An *event* follow expires at `end_ms + 6 h`.
- Stored as JSON in the app's files dir (`follows.json`, ≤ 100 entries), mirrored to
  `SharedPreferences` nothing. Part of the launcher's Backup from v1 (ADR-032): the launcher reads
  `content://…scores/backup` into the `companions.livescore` section of its Backup JSON and restores it
  with `call("restore")` (§12.2); a backup taken while the companion is absent has no section, and a
  restore on a TV without the companion skips it with one log line.
- Defaults on first run (so the pill shows something before the user picks): cricket → India
  internationals + IPL; football → ISL + Premier League + Champions League; F1; tennis off.

### 13.3 Adapter interface (`livescore/source/`)

```kotlin
interface ScoreSource {
    val id: String                                   // "espn"
    fun sports(): List<Sport>                        // what this source can follow
    fun competitions(sport: Sport): List<Competition> // static catalogue, no network (fixtures for tests)
    @Throws(IOException::class)
    fun scoreboard(sport: Sport, competition: Competition?, day: LocalDate): List<Event>
    @Throws(IOException::class)
    fun event(id: EventId): Event?                   // the fuller scoreboard (innings, batters, laps)
}
```

`Event` is the source-neutral model the provider and the UI read (`Event(id, sport, competition,
format, state, statusDetail, startMs, endMs, home: Side, away: Side, summary, updatedMs)`; `Side(id,
name, abbr, score, innings: List<Innings>?, isBatting)`). `EspnSource` is the one implementation; its
parsers are pure functions `parseScorepanel(JsonReader): List<Event>`, `parseScoreboard(sport,
JsonReader)`, each tested against recorded fixtures in `livescore/src/test/resources/espn/*.json`
(captured 2026-11-09 and re-captured by a script when the feed changes). A `FakeSource` in `:testkit`
returns scripted events for the launcher's and the provider's tests. Adding a source is: implement the
interface, register it in `Sources`, add fixtures; nothing above the interface changes.

### 13.4 State normalisation

ESPN's `status.type.state` is `pre | in | post`, but `in` covers Stumps, Lunch, Tea, Rain, and even
"Starts at 10:00 local time" (seen in the 2026-11-09 capture for `Quaid-e-Azam Trophy`, `period 0`).
Normalisation (pure, fixture-tested):

| Feed | Our `state` |
|---|---|
| `pre`, or `in` with `period == 0` and a "Starts at" summary | `upcoming` |
| `in` and `type.description` ∈ {Live, In Progress, 1st/2nd Half, Q1–4, Set n, Lap n} | `live` |
| `in` and `type.description` ∈ {Stumps, Lunch, Tea, Dinner, Drinks, Innings Break, Delayed, Rain, Halftime, Suspended, Bad light, Wet outfield} (tv-town's `NOT_PLAYING` list) | `break` |
| `post` | `final` |

### 13.5 Refresh policy (1 GB TV)

No foreground service, no wake locks, nothing runs while nothing is displayed.

| Situation | Who drives | Interval | Mechanism |
|---|---|---|---|
| Launcher Home on screen, a followed event `live`/`break` | the launcher's `ScoreFeed` queries `/pill` every 60 s | 60 s | **Refresh-on-read**: `ScoreProvider.query()` answers from cache at once; if the cache is older than 55 s *and* any followed event is `live`/`break` *and* no fetch is running, it posts one fetch to a single-thread executor; on success it writes the cache and `notifyChange(/pill)`, the launcher re-reads. One fetch per competition with a live followed event, not per event. |
| livescore UI open (`ScoreboardActivity`) | the activity | 30 s | same executor, `Handler.postDelayed` while resumed |
| Nothing on screen, some followed event within the next 24 h | `JobScheduler` | one-off job at `start_ms − 2 min` (`setMinimumLatency`) + a periodic job every **6 h** (same cadence as traktshelf's `Sync`) to refresh the day's fixture list | fetches fixtures (`pre` events) only; no live polling |
| Nothing followed is live and nothing on screen | nothing | – | zero network |
| Fetch fails | exponential backoff 1, 2, 4, 8 min; cache marked `stale=1`; the pill keeps the last line (the launcher dims it at `stale=1`) | | tv-town's `AFTER_FAILING = 300_000L` is the same idea |
| Device offline (`ConnectivityManager` says no network) | no fetch, cache served | | |

Memory budget: the companion process holds the parsed `Event` list for the window (tens of objects),
a `JsonReader` while parsing, and nothing else; no bitmaps in v1. Target: < 12 MB PSS while idle in the
background, < 25 MB with the scoreboard open (measured in Phase 5 on `HikViewer_TV_1GB`).

### 13.6 UI (Views, D-pad)

- `MainActivity` (LEANBACK_LAUNCHER + module card "Scores"): today's followed events as rows per sport,
  a "Follow" pill, a "Settings" pill (refresh now, defaults, clear follows).
- `FollowActivity`: three-column drill-down, sport → competition → team, each a vertical list; OK toggles
  a tick; the current day's events appear as "Follow this match" entries under their competition. Search
  is not needed (lists are < 40 items).
- `ScoreboardActivity(event_id)`: cricket: innings table, current batters/bowler when the feed has them
  (`summary` endpoint, carried idea from tv-town `Scores.java:354`); football: scorers from `details`;
  F1: session results; tennis: set table. Refreshes every 30 s while resumed.
- Module card: `<card spec="1" label="Scores" caption="Live scores" accent="#E53935"/>`.

---

## 14. Test strategy

### 14.1 Pyramid

```
            ┌──────────────────────────┐   Espresso on the TV emulator (CI: every push to main,
            │ instrumentation (~40)    │   PRs touching code). Focus paths, provider handshakes,
            │                          │   settings persistence, install/Home smoke.
          ┌─┴──────────────────────────┴─┐
          │ Robolectric (~60)             │ Views that need a Context: ResourceBars drawing,
          │                               │ ScorePill text, SettingsPanel rows, Config overlay.
        ┌─┴───────────────────────────────┴─┐
        │ JVM unit (~250)                    │ FocusRules, TopBarFocus, PillOrder, Order, Cards,
        │                                    │ ShelfScale, Gb, parsers against fixtures, PillChooser,
        │                                    │ line formatters, refresh scheduler with a fake clock.
      ┌─┴────────────────────────────────────┴─┐
      │ Python unittest (~80)                   │ setup package against a fake TV, preset format,
      └─────────────────────────────────────────┘ index freshness, discovery parsing, release verify.
```

Frameworks: **JUnit 4** (the carried tests are JUnit 4; one framework across Java and Kotlin modules),
**Robolectric 4.x** for view/`Context` tests (`@Config(sdk = 34)`), **AndroidX Test + Espresso +
UiAutomator** for instrumentation (UiAutomator only for the install-and-Home smoke that spans apps),
**Python `unittest`** (carried; no pytest dependency), **Kover** for Kotlin coverage and **JaCoCo** for
Java with floors: `:core` 90 %, `:livescore` 80 %, `:launcher` 50 % (carried code; rises as the
decomposition lands), `:traktshelf` 40 %. No Mockito: inject interfaces and use the fakes in `:testkit`
(`FakeContentResolver`, `FakeSource`, `FakeClock`, `FakeReaders` for `/proc/meminfo`).

### 14.2 Per-module test lists

`:core` (JVM)
- `GbTest`: 1.1 GB free / 21.6 GB free / 512 MB free / 0 B; rounding at 9.95 GB; locale decimal separator.
- `HeroContractTest`, `ScoreContractTest`: URI builders (incl. `/backup` and the `restore` method name), column arrays have no duplicates, permission names match the manifests (reads the merged manifests from the build dir).
- `CardContractTest`: label/caption/accent validation (carried `CardsTest` rules).
- `DpadKeysTest`.

`:launcher` (JVM, carried 30 tests renamed + new)
- carried: `VolumeKeysTest, TileFitTest, DockOpacityTest, MotionTest, RowGapNameTest, LibraryAppsTest, EdgeAlignTest, LaunchActionsTest, CardFeatureTest, InputNamesTest, HomeKeyServiceTest, OpenOnStartTest, GridNavigationTest, GlassStyleTest, MemInfoTest, CardsTest, RemoteKeysTest, DockCornersTest, OrderTest, LauncherLocaleTest, InputFoldTest, InputSourcesTest` (dropped with their features: `SkyTest, RaceWeekendTest, FestivalTest, LunarTest, GaugeTest, WidgetsTest, WallpaperRotationTest`).
- new `FocusRulesTest`: every branch of today's `onTileVerticalKey` as a table: single row, dock row Up, mid-grid Up/Down, last row Down → library, horizontal edges with dock partially filled, `uniform_rows` 7 columns, RTL mirrored.
- new `TopBarFocusTest`: the seven rules of 9.3, each with pill shown/hidden, carousel showing/not.
- new `PillOrderTest`: which pills show for each combination of `show_inputs_pill`, `show_network_pill`, `show_library_pill`, `vpn_pill`, `top_bar_apps`, `score_pill`.
- new `ResourceSamplerTest`: fake `/proc/meminfo` and `StatFs` → percentages and free bytes; unreadable → row hidden.
- new `ScoreLineTest`: pill text from a provider row for each `state`; 48-char truncation with ellipsis; `stale` dimming flag.
- new `ConfigMigrationTest`: `widgets: ["memory","cpu","storage"]` → `true`; `[]` → `false`; dropped keys logged and ignored; `advanced_effects` choice dropped on load (carried rule).
- new `ThemeKeysTest`: `themes/README.md` Keys table ⇔ `Config` reads (both directions).
- new `OrderMagicTest`: reads both `verysmarttv-order 1` and `tvhome-order 1`.
- new `BackupCompatTest`: a fork backup JSON (`package: com.itsluminous.tvhome`) restores.
- new `FileLengthTest` (Gradle check task): no source file > 1,200 lines.

`:launcher` (Robolectric)
- `ResourceBarsTest`: measures 150 dp × 2 rows; draws two tracks; `setOverArt` changes track alpha; text equals `Gb.free`.
- `ScorePillTest`: GONE for `none`/null cursor/`SecurityException`; kicker colour per state; one line.
- `SettingsPanelTest`: rows present/absent under `uniform_rows`, `shelf_every_row`; "Live scores" subtitle when the companion resolves to nothing; choice round-trip for every row of section 10.
- `HomeActivityTest` (smoke): `onCreate` with the default and the frosted theme does not throw; controllers wired; `onTrimMemory` releases art.
- `HeroFeedTest` / `ScoreFeedTest`: against `FakeContentResolver`; observer coalescing; `MAX_ITEMS`.

`:traktshelf` (JVM + Robolectric)
- `ItemTest` (stableId bit layout, metaLine), `FeedTest` (hero ordering: Up next first, shuffle seeded by sync id, watched exclusion), `PrefsTest` (rows, count, watchlist order), `TraktParseTest` against recorded responses (device code, token, sync/watched, recommendations), `MetadataTest` (TMDB/OMDb parse), `PlayersTest` (Stremio/CloudStream/NetMirror intent building, chooser rules), `HeroProviderTest` (Robolectric: cursor columns match `HeroContract`, `openFile` refuses `w`, unknown URI → null).

`:livescore` (JVM, Kotlin)
- `EspnScorepanelParserTest`: the 2026-11-09 fixture → 8 live events; BAN/WI Test innings `386 & 49/2`; AFG19 `41/1 (9/50 ov)`; `Starts at 10:00` → `upcoming`; Stumps → `break`.
- `EspnScoreboardParserTest` for soccer (`eng.1` fixture: 5 events, FT), F1 (`racing/f1`: session names), tennis (`atp`: set scores), basketball.
- `StateNormaliserTest` (table of 13.4).
- `LineFormatterTest`: every example line of 12.2; abbreviation fallback when the feed has none; ≤ 48 chars.
- `PillChooserTest`: priority order; alternation every 20 s; follow-reason tie-break; empty → `none`.
- `FollowStoreTest`: JSON round-trip; expiry of event follows; 100-entry cap; corrupt file → defaults.
- `RefreshPolicyTest` with `FakeClock` + `FakeSource`: refresh-on-read only when stale and live; one fetch per competition; backoff 1-2-4-8; no fetch when offline; job scheduling times.
- `EspnSourceUrlTest`: URL per sport/competition/day; tennis fetched only when followed.
- `ScoreProviderTest` (Robolectric): `/pill` columns, `getType`, notify on refresh, `SecurityException` for a caller without the permission (`ShadowContextWrapper.grantPermissions` toggled).

Python `tests/` (carried pattern: a fake `tv.py` answering from a JSON state file, `tests/test_recovery_record.py:1-80`)
- `test_discover.py`: parses mDNS answers and scan results; manual IP validation (`address_problem` carried from `tv.py:144`).
- `test_release.py`: picks the three assets by name from a recorded Releases JSON; SHA256SUMS mismatch aborts; prerelease skipped unless asked.
- `test_install.py`: Home guard (never disable the Home owner before the launcher answers HOME; the four `KNOWN_STOCK_LAUNCHERS`; setupwraith/Wi-Fi rule) — carried rules from `tests/test_home_guard.py`, `test_setupwraith_guard.py`, `test_launcher_handover.py`.
- `test_presets_format.py`: every `presets/lists/*/debloat.txt` parses; `tv.json` has `tv, os, brand, notes`; no core package listed (carried `CORE_RE`/`CORE_OK` from `disable.sh:68-69` via `test_core_packages.py`); commented-out-but-active rule (`test_examples.py:141-156`).
- `test_presets_match.py`: `getprop` samples (Mi TV 4A MOOQ1, a TCL G08, a Sony BRAVIA_ATV3, an unknown) → expected ranking and confidence tiers.
- `test_presets_apply.py`: record-before-change, batches, restore replays recorded states, retry does not overwrite the record (carried from `test_recovery_record.py`, `test_check_fix_record.py`).
- `test_index_fresh.py`: `presets/index.json` equals what `build_index.py` generates.
- `test_no_old_identifiers.py`: `grep` for `gathin|tvhome|cleanyourtv|TvHome` over the tree returns only `LICENSE`, `THIRD_PARTY_NOTICES.md`, `README.md` credits, `presets/**` (verbatim community headers), and this design folder.
- `test_bootstrap.py`: `setup.sh`/`setup.ps1` parse (bash -n; PowerShell parsed with `pwsh -NoProfile -Command` when present, else skipped).

### 14.3 Instrumentation (androidTest on the TV emulator)

Runs on `android-tv` API 34 x86 in CI (the only x86 TV image Google ships, per HikViewer's `ci.yml`
note), on `HikViewer_TV_1080p`/`HikViewer_TV_1GB` (arm64) locally. One Gradle invocation installs the
debug APKs of all three apps (`connectedDebugAndroidTest` on each module; the launcher's suite depends on
the companions being installed, enforced by a Gradle `dependsOn(":traktshelf:installDebug",
":livescore:installDebug")`).

`:launcher`
- `HomeFocusTest`: launch Home with a test theme (`score_pill: true`, `widgets: true`, pushed through the `.theme` provider in `@Before` using `UiAutomation.executeShellCommand`); Up from dock slot 0 → score pill focused; Up → Inputs focused; Up again → Inputs still; Down → dock slot 0; focus slot 3, Up → score pill, Down → slot 3; Back from Inputs → grid.
- `HomeFocusNoPillTest`: `score_pill: false` → Up from dock → Inputs; Left/Right along the bar; last-focused pill not restored by a second Up.
- `CarouselFocusTest` (companion fake installed): Up from dock → carousel; Up → score pill; Up → Inputs; Down → score pill; Down → carousel; Back → grid.
- `ResourceBarsVisibleTest`: bars present with the key, absent without; text matches `N GB free` regex.
- `SettingsPersistTest`: open the panel, toggle Live scores off, press Home twice → pill gone; toggle on → back; survives `am force-stop` + relaunch.
- `ProviderHandshakeTest`: with the real debug `livescore` installed and its `FakeSource` armed via a test-only broadcast, the pill shows the scripted `LIVE T20I · AFG 19/4 (6) v ZIM`; OK opens `ScoreboardActivity` with that `event_id` (Intents).
- `HeroHandshakeTest`: traktshelf's provider seeded through a test-only intent → carousel shows the title.
- `ThemePushTest`: `content write` of `frosted/config.json` → Home reloads with 6 columns.
- `BackupRestoreTest`: back up, change a setting, restore, assert.
- `HomeSmokeTest`: `set-home-activity`, HOME keyevent, `mFocusedApp` is `HomeActivity` (UiAutomator).

`:livescore`
- `FollowFlowTest`: follow IPL, assert `/pill` row via `ContentResolver` from the test (test app shares the debug key).
- `ScoreboardTest`: `FakeSource` cricket Test match → innings table rows.
- `ProviderPermissionTest`: a second instrumentation APK signed with another key gets `SecurityException` (the `android-emulator-runner` job builds it with a throwaway keystore).

`:traktshelf`
- `HeroProviderInstrumentedTest`: `openFile` of a seeded poster returns bytes; `/items` columns.
- `DetailsIntentTest`: `trakt_id` + `kind` extras open the right title.

Real-TV checks (owner, Phase 6 gate; cannot be automated): remote buttons through accessibility helpers,
`HomeKeyService` on the Xiaomi ALL_APPS key, screensaver component, 1 GB memory readings.

---

## 15. CI/CD, signing, versioning, release notes

### 15.1 `ci.yml` (every push and PR)

Mirrors HikViewer's shape (`verify → build → changes → instrumented`):

| Job | Runs | Does |
|---|---|---|
| `verify` | every push, every PR | `./gradlew ci`: unit tests all modules, Android Lint (baseline for carried modules, warnings-as-errors for `:core`/`:livescore`), Kover/JaCoCo floors, `FileLengthTest`, the identifier grep; `python -m unittest discover -s tests` with Python 3.12; uploads reports |
| `build` | after verify | `assembleDebug assembleRelease` (release debug-signed here); uploads the six APKs as artifacts; prints sizes and fails if the launcher exceeds **900 KB** (today 615 KB; the headroom is for the decomposition's extra classes, not a licence to grow) |
| `changes` | PRs | `dorny/paths-filter`: code vs docs |
| `instrumented` | push to `main`, `workflow_dispatch`, PRs that touched code | `reactivecircus/android-emulator-runner@v2`, `api-level: 34`, `target: android-tv`, `arch: x86`, `profile: tv_1080p`, `ram-size: 1536M`, KVM enabled, HikViewer's settle script (animations off, `hide_error_dialogs`, 60 s wait), then `connectedDebugAndroidTest` for the three modules; ~30–40 min |

Feasibility within free minutes: the repo is **public**, and GitHub Actions is free without a minutes cap
for public repositories, so the emulator job runs on every push to `main` and on code PRs rather than
nightly (ADR-022). Concurrency group per ref with cancel-in-progress keeps queue time down.

### 15.2 `release.yml` (tag `v*`)

1. Checkout with `fetch-depth: 0` (annotated tag body = release notes, HikViewer's approach).
2. `version=${GITHUB_REF_NAME#v}`; fail unless it matches `X.Y.Z(-suffix)?` with each part < 100.
3. `./gradlew ci` (same gates as a push, debug-signed: the signing environment is not set yet).
4. Decode `KEYSTORE_BASE64` → `$RUNNER_TEMP/release.jks`; export `KEYSTORE_FILE`. After `ci`, not
   before: the convention plugin stops a build whose environment names a keystore without its
   passwords (§15.3), which is what `KEYSTORE_FILE` alone would be.
5. `./gradlew assembleRelease -PversionName=$version` with `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
   `KEY_PASSWORD`, `TRAKT_CLIENT_ID`, `TMDB_API_KEY`, `OMDB_API_KEY` in the environment (the
   convention plugin reads env first, then `secrets.env`, then `local.properties`; a missing key builds
   with an empty string, as traktshelf does today, `addons/traktshelf/app/build.gradle:8-17`).
6. Collect `dist/verysmarttv-launcher-$version.apk`, `dist/verysmarttv-traktshelf-$version.apk`,
   `dist/verysmarttv-livescore-$version.apk`; `sha256sum > dist/SHA256SUMS`.
7. **Verify** each APK with `apksigner verify --print-certs`: the SHA-256 digest must equal the
   keystore's certificate digest (`keytool -list -v`); mismatch fails the job (HikViewer's rule: a
   mis-wired secret must never ship a debug-signed release). Also assert all three digests are equal.
8. `softprops/action-gh-release@v2`: name `VerySmartTV $version`, body = tag message if annotated else
   `generate_release_notes: true` with `.github/release.yml` categories (`feat`, `fix`, `area:launcher`,
   `area:traktshelf`, `area:livescore`, `area:setup`, `docs` from PR labels); `prerelease` when the tag has a `-`;
   files = the three APKs + `SHA256SUMS`.
9. A final step appends to the release body the certificate digest and
   `Install with: setup/setup.sh` (so the Obtainium/GitHub badges have something to point at).

### 15.3 Signing

- **One release keystore** for the three apps (owner's decision): alias `verysmarttv`, RSA 4096, validity
  30 years, created once by the owner with `keytool` and kept **off the repo** (`*.jks` and
  `release.properties` git-ignored; `.gitignore` is in the Phase 0 commit before any build file).
- GitHub secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
  `TRAKT_CLIENT_ID`, `TMDB_API_KEY`, `OMDB_API_KEY`.
- Why one key: the signature-level provider permissions need it; the module-card approval is pinned to
  the signer certificate (`docs/modules.md` §4), so one key means one approval; one secret set to rotate.
- Local release builds: `release.properties` next to `settings.gradle.kts` (same keys as the env vars);
  the convention plugin uses it when present, else env vars, else debug signing with a console warning.
- The certificate SHA-256 is published in the README and in every release body so a user can
  `apksigner verify --print-certs` a download (talkies' and modules.md's practice).

### 15.4 Versioning

One version for the three apps (requirement 5), derived from the tag: `versionName = X.Y.Z`,
`versionCode = X·10000 + Y·100 + Z` (HikViewer's `AppVersion`, `app/build.gradle.kts:163-189`); a build
without `-PversionName` is `0.0.0-dev` with code 1 so a dev build never outranks an install. Every tag
rebuilds and publishes all three even when only one changed: the setup script and the user install a
matching set, and the provider contracts are versioned by the one number. Semver meaning: **major** =
a provider contract or theme-key break (migration note in CHANGELOG), **minor** = features, **patch** =
fixes. `CHANGELOG.md` is kept by hand (Keep a Changelog); the tag message quotes its section.

---

## 16. Setup scripts (computer side)

### 16.1 Architecture

One Python package, `setup/verysmarttv_setup`, run through two thin bootstraps:

- `setup.sh` (macOS/Linux): finds `python3` ≥ 3.9 (Homebrew, Xcode CLT, or `python3` on PATH), creates
  `setup/.venv`, `pip install -r requirements.txt` (`adb-shell`, `rsa`, `cryptography` — what `tv.py` imports,
  `tools/requirements.txt` — plus `zeroconf` for mDNS), then `exec python -I -m verysmarttv_setup "$@"`.
- `setup.ps1` (Windows PowerShell 5.1+): finds `py -3`/`python`, same venv, same module. If no Python,
  prints the python.org link and stops (no winget automation).

No stock `adb` binary: `adb.py` is `tools/tv.py` carried (the pure-Python client on `adb-shell`, the
RSA key in `~/.verysmarttv/adbkey` with `0600`, `restrict()` carried from `tv.py:191`), plus `b64push.py`'s
chunked base64 upload for APKs (`tools/b64push.py`). The optional paired-Wireless-debugging backend
(`NativeAdbDevice`, `tv.py:253`) is carried too, for Chromecast-class devices with no port 5555.

Subcommands (`python -m verysmarttv_setup <cmd>`), the guided flow runs them in order:

| Command | Does | Carried from |
|---|---|---|
| `find` | mDNS browse for `_adb._tcp` and `_adb-tls-connect._tcp` (2 s), then a threaded connect scan of port 5555 across the computer's /24 (1 s per host, 64 threads, ~4 s), then manual entry; remembers the last TV in `~/.verysmarttv/last-tv.json` | new; `address_problem` from `tv.py:144` |
| `guide` | prints the USB-debugging steps for the detected brand (Xiaomi, TCL, Sony, Google TV, Fire TV…) and waits for the "Allow USB debugging?" accept; `probe` shows model/Android/firmware | `--probe` from `tv.py` |
| `probe` | `getprop` of `ro.product.model/brand/manufacturer/device/board/name`, `ro.build.version.release/sdk/incremental/fingerprint`, Home owner, enabled launchers | `tv.py --probe`, `build-and-install.sh:80-96` |
| `fetch` | GitHub Releases API (`/repos/itsluminous/verysmarttv/releases/latest`, or `--tag`), downloads the three APKs + `SHA256SUMS` into `~/.verysmarttv/releases/<version>/`, verifies SHA-256, prints the signer digest if `apksigner` happens to exist (optional) | new |
| `install` | pushes each APK (`b64push`), `pm install -r`, grants (`ACCESS_FINE_LOCATION`, `WRITE_SECURE_SETTINGS` when a key helper is on, `cmd package compile -m speed`), `set-home-activity`, verifies `mFocusedApp` after HOME; **Home guard**: the stock launcher is disabled only after VerySmartTV answers HOME, only one of the four `KNOWN_STOCK_LAUNCHERS`, with the setupwraith/Wi-Fi check on Google TV | `build-and-install.sh:78-192`, `disable.sh` guard |
| `theme <name|--reset>` | pushes `themes/<name>` through `content://com.itsluminous.verysmarttv.theme/…`, bumps `theme_revision`, restarts Home | `theme.sh`, `theme_restart.py` |
| `debloat` | preset matching (17.3), shows the suggested preset with its header and the package count, lets the user pick another or "manual" (a list of every third-party/vendor package with the checklist questions from `PROMPT.md`: streaming apps, Assistant, Chromecast, updater), **records first** (`~/.verysmarttv/recovery-<id>.json`), applies in batches with a Home check between, screenshots to `~/.verysmarttv/shots/` | `recovery.py`, `disable.sh`, `PROMPT.md` checklist |
| `restore` | replays the recovery record (a package the owner had disabled stays disabled; Home goes back to the recorded owner) | `restore.sh`, `recovery.py --commands` |
| `check [--fix]` | drift after a firmware update | `check.py` |
| `backup` | `content read --uri content://com.itsluminous.verysmarttv.backup/settings` → `~/.verysmarttv/backups/` | fork README |
| `close-adb` | tells the user which toggles to flip, reboots, confirms port 5555 refuses (`adb-closed.py` logic) | `adb-closed.py` |

### 16.2 UX flow (`setup` with no arguments)

```
VerySmartTV setup 1.0.0
1  Find your TV            → mDNS … 1 found: "MiTV-MOOQ1" at <ip> (Android TV 10)   [Enter] / other IP
2  Enable USB debugging    → steps for Xiaomi; waiting for the TV to accept this computer … ✓
3  Get the apps            → v1.0.0: launcher 0.6 MB, traktshelf 1.1 MB, livescore 0.9 MB; SHA-256 ✓
4  Install                 → installed 3 apps; Home = VerySmartTV ✓ (stock launcher left enabled; disable? [y/N])
5  Theme                   → default / frosted (owner's) / skip
6  Clean up (optional)     → Suggested preset: xiaomi-mi-tv-4a-mooq1 (exact model match, 27 packages)
                             by the CleanYourTV community (MIT). Show list / Pick another / Manual / Skip
                             Recording current state → ~/.verysmarttv/recovery-….json ✓
                             Batch 1/5 … Home still VerySmartTV ✓ … done. Undo any time: setup restore
7  Companions              → Open Trakt on the TV and sign in at trakt.tv/activate.
                             Open Scores and pick what to follow (defaults: India, IPL, ISL, F1).
8  Close ADB?              → [y/N]  (explains port 5555)
```

Every step is idempotent and re-runnable; `setup --dry-run` prints the shell commands it would send.
Secrets never pass through it (there are none: the companions carry their API keys in the APK).

---

## 17. Debloat presets

### 17.1 Carry

`presets/lists/<slug>/debloat.txt` and `tv.json` are the 434 `examples/*/` folders **verbatim** (the
only edits: the `Apply:`/`Undo:` command lines in headers become `setup debloat --preset <slug>` /
`setup restore`; relative links to `examples/README.md` are dropped). The folder `README.md`/`config.json`
of each example are not carried (they describe cleanyourtv themes and docs). The 435th folder has no
`tv.json` and is checked in Phase 0; if it is a list it gets a generated `tv.json`, otherwise it is skipped.

### 17.2 Data model

`presets/index.json` (generated, committed, test-checked):

```json
{
  "generated_from": {"repo": "https://github.com/Gathin23/cleanyourtv", "commit": "e31f5eb0…", "fork": "https://github.com/itsluminous/cleanyourtv"},
  "presets": [
    {
      "slug": "xiaomi-mi-tv-4a-mooq1",
      "tv": "Xiaomi Mi TV 4A (MiTV-MOOQ1), …",      // tv.json
      "brand": "Xiaomi", "os": "Android TV 10",      // tv.json
      "header": "# Xiaomi Mi TV 4A (MiTV-MOOQ1, codename tarzan), Android TV 10, …",   // first header line
      "tokens": ["mitv-mooq1", "tarzan", "mi tv 4a", "qtg3"],   // normalised identifiers mined from header + slug + tv
      "sdk": 29,                                       // parsed from "SDK 29" / "API 29" when present
      "packages": 27,
      "path": "presets/lists/xiaomi-mi-tv-4a-mooq1/debloat.txt",
      "source": "examples/xiaomi-mi-tv-4a-mooq1/debloat.txt"
    }
  ]
}
```

The header lines are free text (sampled: `# Sony BRAVIA KD-43X8000G (APAC: device BRAVIA_ATV3_4K,
product BRAVIA 4K GB ATV3). Android TV 9 (API 28).`, `# TCL 55Q6CX1 (… device G08, product G08_4K_GB,
model "Smart TV Pro")`), so `build_index.py` mines tokens with a small grammar: quoted strings, words
after `device`/`product`/`board`/`model`/`codename`, parenthesised capitalised codes containing a digit
(`MiTV-MOOQ1`, `G08`, `BRAVIA_ATV3_4K`), the slug's parts, and the brand. Tokens are lower-cased,
`[^a-z0-9]` collapsed to `-`.

### 17.3 Matching rules (`presets.match(props) → ranked list`)

From `getprop`: `model = ro.product.model`, `device = ro.product.device`, `board = ro.product.board`,
`product = ro.build.product | ro.product.name`, `brand = ro.product.brand | ro.product.manufacturer`,
`sdk = ro.build.version.sdk`, `incremental = ro.build.version.incremental`. Each normalised like tokens.

| Tier | Condition | Shown as |
|---|---|---|
| **exact** | `model` or `device` equals a preset token | "exact model match" (auto-suggested; still needs a Yes) |
| **board** | `board` or `product` equals a token, brand equals | "same board, different model name" |
| **brand+os** | brand equals and `sdk` within ±1 | "same brand and Android version — check the list" (never auto-suggested; listed as candidates) |
| **brand** | brand equals | candidates, sorted by `sdk` distance then package count |
| none | – | "no community list for this TV: Manual" |

Ties in a tier are broken by SDK distance, then by newest `source` commit. The suggested preset's header
is shown in full before anything is sent; the user can open the list, deselect lines (writes a local copy
to `~/.verysmarttv/lists/<slug>-edited.txt`, which `check` uses afterwards), or go Manual.

Safety carried unchanged into `presets.py`: `CORE_RE`/`CORE_OK` refusal (`disable.sh:68-69`), one
`pm disable-user --user 0` per shell call, the Home-owner skip until another launcher answers HOME
(`disable.sh:127-139` `home_claims/launchers_in/home_launchers`), the setupwraith + `cname=` Wi-Fi check,
never touching `*factory*`/`*hkcfac*` packages (listed or not: refusal with the reason), and the record
written before the first change. Record key: `sha256(ro.serialno + ro.product.model)[:12]` instead of the
IP (`recovery.py` names records by address and warns on address change, `recovery.py:19-23`; DHCP moves
the owner's TV, `work/README-mooq1.md` says so).

### 17.4 Attribution

- `presets/README.md`: "These lists were written by the CleanYourTV community
  (https://github.com/Gathin23/cleanyourtv, MIT, © 2026 Gathin and contributors) and are carried
  verbatim; each file's header names its tester." Plus the MIT text.
- No per-contributor list and no generated `ATTRIBUTION.md` (ADR-029, changed by the owner): each
  list's header already names its tester, and `index.json` keeps the source path and commit per preset.
- `THIRD_PARTY_NOTICES.md` at the root names the **repositories** this project drew on, each with its
  licence text: Gathin23/cleanyourtv and its community lists (launcher, traktshelf, tools, presets),
  NareshJagriya/tv-town-launcher (the ESPN feed approach), rajupp/talkies (notices layout),
  itsluminous/HikViewer (CI and release shape); then the Outfit font (OFL, `FONT-LICENSE-OFL.txt`
  carried), Fluent Emoji icons (`ICONS-LICENSE-FLUENT-EMOJI.txt` carried), ESPN ("from ESPN's public
  site feeds; no affiliation"), Trakt, TMDB ("not endorsed or certified by TMDB"), OMDb, `adb-shell`,
  `cryptography`, `rsa`, `zeroconf`.

---

## 18. README outline (HikViewer's structure)

```
# VerySmartTV
[CI badge] [Latest release] [License: MIT] [Platform: Android TV 8.0+]
One paragraph: a small launcher for Android TV plus two companions (Trakt, live scores); no ads, no
network in the launcher, 1 GB TVs.
[Get it on GitHub] [Get it on Obtainium]  (docs/badges/, Obtainium deep link for com.itsluminous.verysmarttv)
## Screenshots           (Home with the frosted theme and the score pill; settings panel; Scores app; Trakt details) — 4 images from the TV
## Features              (launcher bullets; companions bullets; what the bars and the pill show)
## How it works          (three apps, providers, signature permissions, refresh policy, the setup tool; link docs/providers.md)
## Install               (setup.sh / setup.ps1 one-liners; manual: download the three APKs, adb install, set-home; verify SHA256SUMS + signer digest)
## Usage tips            (D-pad map incl. Up→score→Inputs; Trakt sign-in; following teams; themes; backup/restore; after a firmware update; closing ADB)
## Building              (→ docs/BUILDING.md: JDK 17, SDK, `./gradlew ci`, emulators, release.properties)
## Contributing          (CONTRIBUTING.md: tests with every change, conventional commits, never commit keys/IPs/Wi-Fi names, presets are community-owned → send list fixes upstream to cleanyourtv too)
## References            (CleanYourTV: launcher and lists; tv-town-launcher: ESPN feed approach; talkies: notices layout; HikViewer: CI/release shape; Trakt/TMDB/OMDb/ESPN)
## Disclaimer            (unofficial; ESPN/Trakt/TMDB not affiliated; undocumented feeds may stop; debloating is at your own risk; nothing uninstalled; restore)
## License               (MIT © 2026 itsluminous; derived from CleanYourTV, MIT © 2026 Gathin — see LICENSE and THIRD_PARTY_NOTICES.md)
```

### 18.1 Licence plan

`LICENSE`: the MIT text with `Copyright (c) 2026 itsluminous`, followed by a second block:
"This project is a derived work of CleanYourTV (https://github.com/Gathin23/cleanyourtv),
Copyright (c) 2026 Gathin, MIT License; its notice is reproduced below and applies to the portions
derived from it." then the original MIT text (as the source `LICENSE` has it). The debloat presets'
notice is in `presets/README.md` (same licence, same copyright line). Fonts/icons: OFL and Fluent
Emoji files carried beside the launcher module.

---

## 19. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| **1 GB RAM**: a third companion process (livescore) plus traktshelf plus the launcher | Android kills the companions when memory is short; the launcher's provider reads then cold-start a process (~300 ms) | Companions are tiny (no bitmaps in livescore v1; traktshelf's image LRU is 3); providers answer from disk cache; `MemoryTrimPolicy` carried in the launcher; Phase 5 measures PSS on `HikViewer_TV_1GB` and on the Mi TV 4A; budget in 13.5 |
| **ESPN feed ToS / availability**: undocumented, no agreement | Scores stop; nothing else breaks | Fail-soft everywhere (pill hides, cache served, backoff); Disclaimer; adapter boundary for a keyed source; no scraping of HTML; polite cadence (60 s, only while displayed) |
| **Feed shape drift** | Parser returns nothing | Fixtures recorded with a script (`livescore/tools/record_fixtures.sh`); a weekly scheduled CI job re-fetches live endpoints and runs the parsers against them (non-blocking, opens an issue on failure) |
| **Signature permissions and third-party builds** | Someone installs our launcher with a self-built companion → `SecurityException`, pill/carousel silently absent | Logged once with the reason; README "Building" says to build all three; the setting rows say "not installed" only when the authority is absent and "blocked (different signing key)" when the query is refused |
| **Emulator vs real TV** | Accessibility helpers, CEC inputs, Xiaomi ALL_APPS key, firmware HOME allowlists are untestable on the emulator | Phase 6 has an explicit real-TV checklist the owner runs; CI covers focus, providers, settings; the repo's AGENTS.md says which claims need a TV |
| **Rename regressions** | A missed string breaks a card, a provider or the backup import | The identifier grep test; `BackupCompatTest`; `OrderMagicTest`; the Espresso handshakes |
| **Decomposition regressions** (4,973 lines moved) | Subtle focus/animation differences | Move-verbatim-then-refactor rule; the Espresso focus suite is written **before** the split (Phase 2 step 1) and must stay green through it; `bench.py`'s idea (key latency, janky frames) is replaced by a simple `dumpsys gfxinfo` check in `HomeSmokeTest` |
| **CI emulator flakiness** | Red main for environmental reasons | HikViewer's settle script; `fail-fast: false`; one retry of the job via `nick-fields/retry`; the suite is also runnable locally on the arm64 AVDs |
| **Preset mismatch** disables something a TV needs | A broken TV feature | Exact-tier only is auto-suggested; the full header shown; record-first; batches with Home checks; `CORE_RE` refusal; restore in one command |
| **Translations rot** | Untranslated new strings show English in 30 locales | Accepted and documented; a lint check only for malformed format arguments |
| **Owner's existing TV migration** | Settings lost moving from `com.itsluminous.tvhome` | No import row (ADR-011). The setup tool's `backup` reads the old launcher's `.backup/settings` over ADB (`content read`, no permission needed) and pushes the file into the new launcher's backup folder; **Restore from file** reads it (`BackupCompatTest`, `OrderMagicTest`). The launcher itself never names the old package |
| **Keystore loss** | No more updates installable over existing ones | The owner keeps the `.jks` + passwords in a password manager; README says a lost key means uninstall/reinstall |

---

## 20. Phased build plan

Agent roles per phase: **coder** (implements), **reviewer** (reads the diff against this document and
the ADRs, runs the tests, blocks on scope creep), **QA** (writes/extends tests first where the phase
says so, runs the emulator suite, takes screenshots). The owner approves each phase's gate.

| Phase | Scope | Coder delivers | Reviewer checks | QA | Acceptance | Owner provides |
|---|---|---|---|---|---|---|
| **0 Skeleton** (½ day) | Repo, Gradle, CI shell | `settings.gradle.kts`, `build-logic` convention plugins, version catalog, empty `:core`/`:testkit`, `.gitignore`, `LICENSE`, `THIRD_PARTY_NOTICES.md`, `AGENTS.md`, `CONTRIBUTING.md`, `ci.yml`, `release.yml`, `.github/release.yml`, `docs/BUILDING.md` | Toolchain versions match §5.1; secrets never in tree; `.gitignore` first | `./gradlew ci` green on an empty project; `release.yml` dry run on a `v0.0.1-rc1` tag fails at its first step after the version check, **Check the secrets**, as designed (a release is never debug-signed; once the secrets exist it fails at collecting the APKs until Phase 1 adds the modules) | CI green; a tag builds; nothing to install yet | GitHub repo `itsluminous/verysmarttv` created, Actions enabled |
| **1 Carry + rename** (2 days) | `:launcher`, `:traktshelf` moved and renamed; `:core` contracts | Scripted move of 101 Java files + resources into the new packages; naming map §7 applied; dropped features removed (§3); `widgets` boolean + migration; `Config`/`AppAdapter` split; lint baselines; `HeroContract` extracted; signature permission on `.hero` | Grep test passes; no behaviour change beyond §3/§6; dropped-key logging | Carried 22 JVM tests pass; `HomeActivityTest` smoke; `BackupCompatTest`, `OrderMagicTest`, `ConfigMigrationTest` written and green; manual run on `HikViewer_TV_1080p` with the frosted theme: Home renders, carousel works with traktshelf debug build | Frosted theme on the emulator looks like the fork's screenshot minus gauges; APK < 900 KB | – (the §3 and §7.2 rows are decided) |
| **2 Decompose** (3 days) | §8 | Step 1 (QA first): `HomeFocusNoPillTest`, `CarouselFocusTest` (pre-pill rules), `ThemePushTest`, `BackupRestoreTest`, `HomeSmokeTest` green on the undecomposed code. Then move methods into the 17 classes verbatim (one commit per controller), then extract `FocusRules`, `TopBarFocus` (without the pill), `PillOrder`, `InputRows`, `ShelfScale` as pure classes | Each commit compiles and keeps Espresso green; no logic changes while moving; `HomeActivity` < 500 lines; file-length task on | `FocusRulesTest` (full table), `PillOrderTest`, `ShelfScaleTest`; coverage floor 50 % on `:launcher` | All Phase-2 suites green on the emulator; `dumpsys gfxinfo` janky-frame share within 2 pp of Phase 1 on the same AVD | – |
| **3 Bars + pill in the launcher** (2 days) | §9, §10 rows | `ResourceBars`, `ResourceSampler`, `ScorePill`, `ScoreFeed`, `TopBarFocus` rules 1–7, settings rows System bars / Live scores, `score_pill` theme key, `themes/frosted` updated, Keys table | Pill is `GONE` without the provider; no network; 60 s read only while live; focus rules exactly §9.3 | `TopBarFocusTest`, `ResourceSamplerTest`, `ScoreLineTest`, Robolectric `ResourceBarsTest`/`ScorePillTest`/`SettingsPanelTest`; Espresso `HomeFocusTest`, `ResourceBarsVisibleTest`, `SettingsPersistTest` with a `FakeScoreProvider` from `:testkit` | The reference-image layout reproduced on `HikViewer_TV_1080p` screenshot (bars block, pill line under the pills); every focus rule green | Reviews the screenshot |
| **4 livescore app** (4 days) | §13, §12.2 | `:livescore`: `ScoreSource`, `EspnSource` + parsers, `StateNormaliser`, `LineFormatter`, `PillChooser`, `FollowStore`, `RefreshPolicy` + `ScoreProvider` + jobs, `MainActivity`/`FollowActivity`/`ScoreboardActivity`, module card, fixtures + `record_fixtures.sh` | Streaming parse, no raw payload held; refresh only on read/when live; permission `signature`; no coroutines; PSS budget | All §14.2 `:livescore` JVM tests; Robolectric `ScoreProviderTest`; Espresso `FollowFlowTest`, `ScoreboardTest`, `ProviderPermissionTest`; launcher `ProviderHandshakeTest` with the real debug companion | Follow IPL on the emulator → pill shows a scripted live line; real feed shows today's live match; PSS < 12 MB idle / < 25 MB open on `HikViewer_TV_1GB` | Confirms default follows (§13.2); no key needed |
| **5 Setup tool + presets** (3 days) | §16, §17 | `setup/` package, both bootstraps, `presets/lists` import + `build_index.py`, Python tests with the fake TV | Home guard and `CORE_RE` byte-identical in intent to cleanyourtv's; record-before-change; no IPs in repo | `tests/` suite (§14.2 Python) green on macOS and in CI (ubuntu + windows runners for the bootstraps) | `setup.sh` from a clean machine: finds the emulator (`--host 127.0.0.1:5555` path), installs a `v0.x` release's three APKs, sets Home, pushes `frosted`, matches the Mi TV 4A props sample to `xiaomi-mi-tv-4a-mooq1` at tier exact, dry-run shows the batches; `restore` replays | – (attribution form decided, §17.4) |
| **6 Real TV, README, v1.0.0** (2 days + owner time) | Docs, screenshots, release | README per §18; `docs/providers.md` generated; CHANGELOG; screenshots from the Mi TV 4A; `v1.0.0-rc1` then `v1.0.0` | README links resolve; badges point at real workflows; Disclaimer text | Real-TV checklist: Home after reboot, ALL_APPS key, Button Mapper still bound, carousel, pill with a live match, bars values plausible vs `dumpsys meminfo`, settings panel rows, settings moved from `com.itsluminous.tvhome` with `setup backup` + Restore from file, `setup debloat` matches the TV, `setup restore` works; PSS of the three processes | Owner installs `v1.0.0` on the Mi TV 4A via `setup.sh`; everything in the checklist passes | Keystore + the 7 secrets in GitHub; Trakt/TMDB/OMDb keys (exist in the fork's `work/secrets.env`, copied by the owner into the secrets, never by an agent); runs the real-TV checklist |

Total: about 16 agent-days of work plus owner gates. Phases 3 and 4 can run in parallel after Phase 2
(the pill is tested with `FakeScoreProvider` until the real companion lands).

### 20.1 What the owner must provide, when

| Item | Phase | Note |
|---|---|---|
| GitHub repo `itsluminous/verysmarttv`, Actions on | 0 | public |
| Answers to every **[CONFIRM]** | done (2026-11-09) | §3 drops accepted; §7.2 old backup authority rejected; §13.2 follows are backed up; §17.4 repositories named, no per-contributor list |
| Release keystore (`keytool -genkeypair -alias verysmarttv -keyalg RSA -keysize 4096 -validity 10950`) and its two passwords, as the four GitHub secrets | before the first `v*` tag (Phase 6; a `-rc` tag pushed earlier fails at **Check the secrets** on purpose) | keep the `.jks` in a password manager |
| `TRAKT_CLIENT_ID`, `TMDB_API_KEY`, `OMDB_API_KEY` as GitHub secrets and in local `secrets.env` | 1 (traktshelf builds with empty keys otherwise) | copied by the owner from the fork's `work/secrets.env`; agents never read or print that file |
| livescore key | none | ESPN needs none |
| The Mi TV 4A, ADB on, for the Phase 6 checklist | 6 | and the four README screenshots |
| The frosted wallpaper (already in `themes/mooq1-frosted/wallpaper.jpg`) | 1 | carried |
