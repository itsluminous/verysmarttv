# AGENTS.md

Rules for coding agents working in this repository. People start with [README.md](README.md) and
[CONTRIBUTING.md](CONTRIBUTING.md); this file states the same rules in the form an agent needs. The
design is [docs/design/DESIGN.md](docs/design/DESIGN.md) and every decision has an entry in
[docs/design/DECISIONS.md](docs/design/DECISIONS.md); read the sections the task names before writing
anything, and cite the ADR when a change follows one.

## What this is

VerySmartTV builds three Android TV apps from one Gradle project: the launcher
`com.itsluminous.verysmarttv` (module `:launcher`, Java, no network), the Trakt companion
`com.itsluminous.traktshelf` (`:traktshelf`, Java) and the live-score companion
`com.itsluminous.livescore` (`:livescore`, Kotlin). `:core` holds the provider contracts they share,
`:testkit` the fakes their tests share, `build-logic/` the convention plugins. The computer-side
setup tool and the debloat presets live in `setup/` and `presets/` (from Phase 5). Phases and what each
delivers: DESIGN.md §20.

## Build and verify

```bash
export JAVA_HOME=/path/to/jdk-17          # Corretto or Temurin 17; AGP 9 needs 17, the code is Java 17
./gradlew ci                              # what CI runs: assembleDebug, testDebugUnitTest, lintDebug per module, build-logic tests, fileLength
./gradlew fileLength                      # the 1,200-line gate alone
./gradlew :core:testDebugUnitTest         # one module's JVM tests
```

Android SDK platform 36 and build-tools 36 (`local.properties` with `sdk.dir`, git-ignored). Details and
the emulator setup: [docs/BUILDING.md](docs/BUILDING.md). A change is not done until `./gradlew ci` is
green locally.

## Hard rules

- **Tests with every change.** New logic comes with a JVM test in the same commit; a focus or layout
  decision is written as a pure class with a table test (DESIGN.md §8). A fix for a bug starts with the
  test that reproduces it. Robolectric for anything that needs a `Context`; Espresso for what needs a
  device; no Mockito, use the fakes in `:testkit` (ADR-019).
- **No dead code.** Nothing is carried or added that no shipped theme, setting, test or companion
  reaches (DESIGN.md §3). No commented-out code, no `TODO` placeholders, no "for later" classes.
  Features are complete in the phase that lands them or they are not in the tree.
- **All user-facing text lives in string resources** (ADR-035), in every module. Lint's
  `HardcodedText` and `SetTextI18n` are errors and may not be baselined. Text a companion hands the
  launcher as data (the pill line, the carousel meta) is exempt.
- **Naming.** Packages `com.itsluminous.verysmarttv.*` (launcher, core, testkit),
  `com.itsluminous.traktshelf`, `com.itsluminous.livescore`. Provider authorities, intent actions,
  categories and meta-data all start with the owning app's package (DESIGN.md §7). Log tags
  `VerySmartTV.*`, `TraktShelf.*`, `LiveScore.*`. The upstream project's identifiers and the
  design's working title may appear only in `LICENSE`, `THIRD_PARTY_NOTICES.md`, README credits,
  preset headers and `docs/design/`; the exact pattern and command are the identifier gate in
  [docs/BUILDING.md](docs/BUILDING.md#identifier-gate), and it must print nothing.
- **File length.** No source file over 1,200 lines (`./gradlew fileLength`, part of `ci`).
  `HomeActivity` stays under 500. Split, do not raise the gate.
- **Never commit secrets or identifiers.** No keystores, `release.properties`, `secrets.env`, API keys,
  IP addresses, Wi-Fi names, serial numbers, account e-mails. `.gitignore` lists the local files; read
  it before adding a file type. Never read or print the owner's `secrets.env` or the fork's
  `work/secrets.env`; the owner copies keys into GitHub secrets themselves (DESIGN.md §20.1).
- **The launcher has no network.** No `INTERNET` permission, no background services, no polling. It
  reads the companions through content providers guarded by signature permissions (§12.3) and nothing
  else. Companions use `HttpURLConnection`, no HTTP, JSON or coroutine libraries (ADR-005).
- **One version, one key.** Version and signing come from build-logic; a module never sets its own
  `versionCode`, `versionName` or `signingConfig`. New dependencies go through
  `gradle/libs.versions.toml` and are discussed in an issue first; nothing joins the catalog before
  the phase that uses it.
- **Lint is a hard gate.** `warningsAsErrors` everywhere. A carried module may commit a
  `lint-baseline.xml` once, in the carry commit; new findings are fixed, not baselined.
- **1 GB TVs.** No blur, no video, no bitmaps held longer than needed; measure PSS when a phase says so.
- **Don't edit `.github/workflows/`**, the size gate, the signing or the Home-guard logic in the setup
  tool without the owner's say-so.
- **Real-TV claims need a real TV.** Accessibility helpers, remote buttons, the Xiaomi ALL_APPS key, CEC
  inputs and firmware Home allowlists cannot be verified on the emulator; say so rather than claim it.

## Commits

Conventional Commits, imperative mood, subject under 72 characters, a body that says what and why:
`feat(launcher): score pill under the top-right pills (ADR-017)`, `build: Gradle skeleton, convention
plugins, CI and release workflows`, `docs: owner decisions, project renamed VerySmartTV`. One topic per
commit. Build before committing. Never force-push, never rewrite pushed history.

## When the design and the code disagree

The design wins until the owner changes it. A change that the design does not cover gets a new ADR in
`DECISIONS.md` in the same commit, with context, decision, rejected alternatives and consequences.
