# Third-party notices

VerySmartTV is MIT-licensed (see [LICENSE](LICENSE)). This file names the works it drew on, each with
its licence. When a phase carries new material into the tree (fonts, icons, Python libraries, the data
services the companions call), its notice is added here in the same commit.

## Repositories

### CleanYourTV, by Gathin and the CleanYourTV community

https://github.com/Gathin23/cleanyourtv, MIT License, Copyright (c) 2026 Gathin.

VerySmartTV is a derived work: the launcher, the Trakt companion, the computer-side setup tooling
and the debloat presets started as this repository's code and community TV lists, renamed and
reworked here. The lists' headers name the people who tested each TV. CleanYourTV's licence text is
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

## Libraries

### AndroidX Annotation

`androidx.annotation:annotation`, Apache License 2.0, Copyright The Android Open Source Project.
Used by `:core` for `@NonNull` / `@Nullable` on its public API; compile-time only.
https://www.apache.org/licenses/LICENSE-2.0

### Gradle wrapper

`gradle/wrapper/gradle-wrapper.jar`, Apache License 2.0, Copyright the original author or authors
(Gradle Inc.). https://www.apache.org/licenses/LICENSE-2.0
