# Third-party notices

VerySmartTV is MIT-licensed (see [LICENSE](LICENSE)). This file names the works it drew on, each with
its licence. When a phase carries new material into the tree (fonts, icons, Python libraries, the data
services the companions call), its notice is added here in the same commit.

## Repositories

### CleanYourTV, by Gathin and the CleanYourTV community

https://github.com/Gathin23/cleanyourtv, MIT License, Copyright (c) 2026 Gathin.

VerySmartTV is a derived work: the launcher, the Trakt companion, the computer-side setup tooling
and the debloat presets started as this repository's code and community TV lists, renamed and
reworked here. The 434 lists under `presets/lists/` are those community lists carried verbatim (only the
Apply/Undo command lines in their headers name this tool; `presets/index.json` records the source commit),
and the lists' headers name the people who tested each TV. The setup tool's ADB client, Home guard, core
package deny-list, recovery record, drift check and base64 transfer follow that repository's `tools/`,
rewritten here in one Python package. CleanYourTV's licence text is
reproduced in full in [LICENSE](LICENSE); its permission notice applies to the portions derived from it.

### tv-town-launcher, by Naresh Jagriya

https://github.com/NareshJagriya/tv-town-launcher, GNU General Public License v3.0.

Consulted for the approach to live scores: which public ESPN site feeds carry cricket, football, F1
and tennis scoreboards, and how a launcher polls them only while the score is on screen. **No code
from this repository is copied into VerySmartTV**: the live-score companion is written from scratch
under MIT, against those feeds, with its own parsers. The GPL applies to tv-town-launcher's own
code; its notice reads:

> This program is free software: you can redistribute it and/or modify it under the terms of the
> GNU General Public License as published by the Free Software Foundation, either version 3 of the
> License, or (at your option) any later version. This program is distributed in the hope that it
> will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
> FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details:
> https://www.gnu.org/licenses/gpl-3.0.html

### talkies, by TechPP

https://github.com/rajupp/talkies, MIT License, Copyright (c) 2026 TechPP.

Consulted for the shape of this notices file and of the README's disclaimer and references
sections; no code copied.

> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
> associated documentation files (the "Software"), to deal in the Software without restriction,
> including without limitation the rights to use, copy, modify, merge, publish, distribute,
> sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
> furnished to do so, subject to the following conditions: The above copyright notice and this
> permission notice shall be included in all copies or substantial portions of the Software. THE
> SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
> LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
> IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
> LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
> CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

### HikViewer, by itsluminous

https://github.com/itsluminous/HikViewer, MIT License, Copyright (c) 2026 itsluminous.

The CI and release workflow shape (quality gates, the Android TV emulator job, tag-driven signed
releases with a verify-or-fail signature check), the Gradle version pins and the build-logic helpers
(`AppVersion`, `ReleaseSigning`) follow this repository's. Same author, same licence as VerySmartTV;
the MIT notice above applies.

## Data services

### ESPN site feeds

The live-score companion (`:livescore`) reads scores from ESPN's public, undocumented site feeds
(`site.api.espn.com`, `site.web.api.espn.com`), the same endpoints the ESPN website and apps read, with no
account and no key. **VerySmartTV is not affiliated with, endorsed by or sponsored by ESPN**; ESPN, the feed
contents and the team and competition names are the property of their owners, shown here for the personal,
non-commercial display of scores on the owner's own TV. ESPN publishes no terms for these feeds and may
change or stop them without notice; the companion is built to fail soft when that happens (the last
scores stay, marked stale, and the status line shows when they were last updated) and the adapter boundary
`source/ScoreSource` is where a replacement goes (DESIGN.md §13.1, ADR-012). Nothing is fetched while no
screen shows a score.

### SportMicro "Badminton Micro" API, through RapidAPI

The live-score companion reads badminton scores from SportMicro's Badminton Micro API as listed on RapidAPI
(`badminton-micro.p.rapidapi.com`, `rapidapi.com/sportmicro-sportmicro-default/api/badminton-micro`), only with a key
the TV's owner creates in their own RapidAPI account and enters in the companion's Settings; no key ships in the APK,
the repository, a build variable or a CI secret, and nothing is fetched without one. **VerySmartTV is not affiliated
with, endorsed by or sponsored by SportMicro or RapidAPI (Nokia)**; the data, the tournament and player names are the
property of their owners and of the Badminton World Federation, shown for the personal, non-commercial display of
scores on the owner's own TV under the plan the owner subscribed to. The free plan is 100 requests a day (measured
2026-11-10), so the companion fetches badminton at most every 5 minutes; past the free plan the owner's key may cost
them money under RapidAPI's billing, which is between the owner and RapidAPI. The vendor may change or stop the API,
and the adapter boundary `source/ScoreSource` is where a replacement goes (DESIGN.md §13.1, ADR-044). The fixtures
under `livescore/src/test/resources/fixtures/badminton/` are five matches of one public day's list recorded on
2026-11-10 with image hashes removed, plus hand-made edge cases in the same shape; nothing in them is personal.

### Badminton World Federation (BWF) website, Match Centre (experimental, off by default)

When the TV's owner chooses **Settings → Badminton source → BWF website (experimental)**, the live-score companion
loads BWF's own public Match Centre page (`match-centre.bwfbadminton.com`) in a hidden browser view on the TV, the way
a browser would, and reads the live-score JSON the page itself requests from `extranet-lv.bwfbadminton.com`
(ADR-049). No key, no account, no login, no bypass of any protection: the page is public and the browser engine is the
TV's own Android System WebView; the companion asks for it at most every 5 minutes while a badminton follow is live, only
while a screen shows a score (never from its background job), lets the hidden view reach BWF's own hosts and Cloudflare's
challenge host and nothing else, and keeps nothing of the visit (cache, cookies and web storage are deleted after each). **VerySmartTV is not affiliated with,
endorsed by or sponsored by the Badminton World Federation**; the site's content, scores, tournament and player names
are © BWF and the property of their owners, shown for the personal, non-commercial display of scores on the owner's
own TV. This is an experimental read of a public web page: the site may change, challenge or stop it at any time, and
then the companion shows "BWF site unavailable" and nothing else happens. SportMicro's keyed API stays the default
source. The fixtures under `livescore/src/test/resources/fixtures/bwf/` are the public tournament lists the page
received on 2026-11-10 and 2026-11-12, an empty live-match list from the first visit, the live-match lists of two
tournaments from the second (public scores and players' names as the site shows them, at most five matches each), and a
hand-made live-match list in the page's own field names; nothing in them is personal.

## Artwork

### "Get it on GitHub" and "Get it on Obtainium" badges

`docs/badges/get-it-on-github.png` is the badge from [Kunzisoft/Github-badge](https://github.com/Kunzisoft/Github-badge),
created by @flocke as part of the [F-Droid artwork](https://gitlab.com/fdroid/artwork/-/tree/master/badge/src),
CC-BY-SA 3.0. `docs/badges/get-it-on-obtainium.png` is `assets/graphics/badge_obtainium.png` from
[Obtainium](https://github.com/ImranR98/Obtainium), GPL-3.0, vendored because Obtainium asks projects not to
hot-link it. Both are linked from the README only and are in no APK; [docs/badges/README.md](docs/badges/README.md)
repeats this beside the files.

## Libraries

### Kotlin standard library

`org.jetbrains.kotlin:kotlin-stdlib`, Apache License 2.0, Copyright 2010-2026 JetBrains s.r.o. and Kotlin
Programming Language contributors. The one runtime library of `:livescore`, added by AGP's built-in Kotlin
support with the compiler's own version. https://www.apache.org/licenses/LICENSE-2.0

### Outfit typeface

Outfit, by Rodrigo Fuenzalida, SIL Open Font License 1.1 (`launcher/FONT-LICENSE-OFL.txt`). The launcher
and both companions ship it in `res/font/outfit.ttf`. https://fonts.google.com/specimen/Outfit

### AndroidX Annotation

`androidx.annotation:annotation`, Apache License 2.0, Copyright The Android Open Source Project.
Used by `:core` for `@NonNull` / `@Nullable` on its public API; compile-time only.
https://www.apache.org/licenses/LICENSE-2.0

### cryptography

`cryptography` (https://cryptography.io), Apache License 2.0 or BSD-3-Clause, Copyright The cryptography
developers. The one third-party package of the setup tool (`setup/requirements.txt`): the RSA key pair ADB
authenticates this computer with, and the PKCS#1 v1.5 signature of adbd's token. Installed into the user's
venv by the bootstraps; not in the tree. https://github.com/pyca/cryptography/blob/main/LICENSE

### Gradle wrapper

`gradle/wrapper/gradle-wrapper.jar`, Apache License 2.0, Copyright the original author or authors
(Gradle Inc.). https://www.apache.org/licenses/LICENSE-2.0
