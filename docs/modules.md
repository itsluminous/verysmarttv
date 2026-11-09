# Module cards

A module is a normal Android TV app that declares a card for the launcher. Your repo, your licence, your signing
key, released as an APK wherever you like. The TV owner sideloads it like any other app. The launcher finds the
card and asks the owner once; if they answer Show, your app appears as a card from then on: your label, an
optional caption, and your colour in the ambient glow, placed ahead of the alphabetical grid. The two companions
in this repository ([`:traktshelf`](../traktshelf/README.md), and `:livescore` from Phase 4) declare cards exactly
this way.

The contract strings are the launcher's (DESIGN.md §7.3): category `com.itsluminous.verysmarttv.category.CARD`,
meta-data `com.itsluminous.verysmarttv.card`. Cards written for CleanYourTV's `DOCK_CARD` category are not read
(ADR-010); the rules below are otherwise its spec 1, unchanged.

## 1. Declare the card

On the activity that already has the `LEANBACK_LAUNCHER` filter, add a second filter and a `meta-data` entry:

```xml
<activity android:name=".MainActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="com.itsluminous.verysmarttv.category.CARD" />
    </intent-filter>
    <meta-data android:name="com.itsluminous.verysmarttv.card" android:resource="@xml/module_card" />
</activity>
```

Then create `res/xml/module_card.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<card spec="1" label="Trakt" caption="Up next and picks" accent="#ED1C24" />
```

Rules for the declaration:

- The card filter and the `meta-data` sit on an activity that also has `MAIN` + `LEANBACK_LAUNCHER`, is
  `exported` and is enabled. A card on any other activity is dropped.
- The `meta-data` must use `android:resource` pointing at an XML resource. `android:value` is not read.
- One card per package. If a package declares more than one, only the first the system lists is read.
- The root element is `<card>` with attributes only, no XML namespace. Child elements are ignored. Unknown
  attributes are ignored, up to 16 attributes in total.

## 2. Fields

Values are literal text written in the XML file. References (`@string/name`, `?attr/name`) are not resolved in
spec 1 and make the card invalid, so a card is not localisable in spec 1.

| Attribute | Required | Rule |
| :- | :- | :- |
| `spec` | yes | Decimal integer. This launcher reads spec `1`. Any other value: the card is ignored, with one log line. |
| `label` | yes | 1 to 24 characters from `A-Z a-z 0-9` space `. & + ' -`, no leading or trailing space, at least one letter or digit. See the reserved names below. Changing the label in an update makes the owner decide again, so pick it once. |
| `caption` | no | Up to 32 characters from the same set. Shown after the label while the card is focused in the grid. The name line under the tile is one line; label and caption together show about 26 characters on a 4 column grid, so keep the caption to a few words. A character outside the set drops the whole card rather than showing it without the caption. |
| `accent` | no | `#RRGGBB`. The ambient glow colour while the card is focused in the dock. |

The resource compiler stores some attribute values in a typed form; the launcher accepts the forms it hands back
for the values above (`spec="1"` as an integer, `accent="#E8A33D"` as a colour, which comes back as `#ffe8a33d`).
Keep labels and captions as words: a value that reads as a number with a unit or as a colour is stored in that
typed form and will not match what you wrote.

### Reserved names

Comparisons use the folded form of the label: lower case, with everything except ASCII letters and digits
removed. "Live TV", "live-tv" and "LIVETV" are the same name.

A label is rejected when:

- it contains `verified` or `official` anywhere: words that imply endorsement are reserved, and nothing a module
  declares is ever presented as endorsed (the same two words are refused in the caption);
- it is one of the names the launcher's own UI uses, in English or in the TV's language: what its pills and
  dialogs say (`Home`, `Settings`, `Inputs`, `Wi-Fi`, `Live TV`, `App library`, `Choose default home`), the name
  of the clock screensaver in the TV's screensaver picker (`Clock`), `Default home` word for word, and the
  project name `verysmarttv`;
- it names an input the Inputs menu shows: `HDMI` and anything starting with it, `AV`;
- while the prompt is due (a new card, or one whose signing key or label changed): it equals the label of another
  app installed on this TV (the names on the home screen), or the app's own label equals another installed app's
  label. The module's own app label is fine: a card named "Trakt" from an app named "Trakt shelf" is the normal
  case. Once the owner has answered, the name stays with that card: an app installed afterwards with the same
  label does not displace it. The answer is for that name: a card that changes its label on an update is pending
  again and goes through these checks anew;
- two cards share a label: the one the owner already decided on keeps the name (the oldest decision wins), the
  other is dropped; when neither has a decision, both are dropped and nobody is asked about a contested name.

## 3. Limits

The launcher never trusts a module's input to be small or well formed. Everything is bounded and any violation
drops that one card, with a log line and no other effect.

| What | Limit |
| :- | :- |
| Cards read per package list refresh | 32, the rest are ignored |
| Cards shown as tiles | 8, by label; the rest are listed as over the limit in the Module cards list and keep their ordinary tile |
| Decisions remembered in `cards.json` | 128, the oldest are dropped beyond that |
| XML events walked per card | 64 |
| XML nesting depth | 2 (`<card>` and its direct children) |
| Attributes on `<card>` | 16 |
| Label and caption length | 24 and 32 characters, checked before anything else looks at the value |
| Signers | exactly one signing certificate; a package with several is rejected |

## 4. Launcher behaviour

1. Discovery. Every time the home screen comes to the front, the launcher asks the package manager for activities
   with `MAIN` + `com.itsluminous.verysmarttv.category.CARD` (a package manager query, no polling, no services, no
   network). For each one it checks the activity, reads the XML through the package manager, applies the rules
   above and computes the signer digest. Every card is read inside its own error boundary.
2. Signer digest. SHA-256 of the DER encoded signing certificate of the package's single signer, as `apksigner
   verify --print-certs` prints it ("Signer #1 certificate SHA-256 digest"). On Android 9 and newer the launcher
   reads the current signer; on Android 8 it reads the package's signature. A package signed with more than one
   certificate is rejected.
3. Decision record. The owner's decision is stored per package in the launcher's private storage
   (`files/cards.json`, format version 2): package, signer digest, the card's folded label, decision (`show` or
   `hide`), first seen, decided at. The file is written atomically and validated strictly when read; a file that
   does not validate is moved to `cards.json.bad` and the launcher continues as if no decision had been made. The
   record is part of the launcher's **Back up settings** file and of nothing else.
4. Prompt. A card with no record, or whose signer digest or folded label differs from its record, is not shown.
   Once the grid is laid out and the home screen has focus, with no menu open, the launcher shows one prompt. It
   names the app by its own label (control and formatting characters removed, at most 40 characters) and package
   and the key by the first twelve hex characters of the digest; when the card label differs from the app label it
   says both names. The buttons are "Keep hidden" (focused first) and "Show card"; Back means Keep hidden. Left and
   Right move between the buttons; Up and Down do nothing. After an answer, the next card with a pending prompt
   follows at once.
5. Signer change. A package whose signer digest differs from its record is treated as new: the card is not shown,
   and the prompt says the app was updated with a different signing key. The record is replaced only by the
   owner's answer, never by the detection alone. A card whose folded label differs from its record is treated the
   same way: a decision is for one name under one key.
6. Placement. A shown card stands in for every tile of its package: the ordinary tile is not shown next to it. A
   package in `hidden_apps` shows neither a tile nor a card. A package in `dock_apps` gets its card in that dock
   slot. Other cards sort right after the configured dock apps and before the alphabetical grid, by label. In the
   dock, the top shelf shows the label and the glow uses the accent; in the grid, the name line under the focused
   tile shows the label and the caption, as far as they fit on one line. A card uses the app's own banner (or
   icon) for its artwork.
7. Hide. A hidden card leaves the app's ordinary tile exactly as before: hiding a card never removes an app, and
   showing one never installs anything. A card is hidden or shown again from the Module cards list: hold OK on the
   Settings pill. The list shows every discovered card with its app label, package, full signer digest and state;
   OK toggles Shown and Hidden.
8. Uninstall. Removing the module removes the card. Its record stays, so a reinstall with the same signing key
   needs no new prompt.
9. Launch. A card launches the declaring activity by explicit component with no extras and no data, the same way
   every tile is launched. No field of a card can name an intent, a URI, a class or a package. Holding OK on a
   card opens the same tile menu a tile opens (`tile_menu`, on by default: Move, Remove from Home with
   `app_library`, and App info where the firmware has that page).
10. Off switch. `module_cards` in `config.xml` or a theme (default `true`). When `false` the launcher never looks
    for cards, never asks, and the Module cards list only says that none are installed.

## 5. Security

- The launcher never installs, downloads, enables or disables anything, binds no service, loads no code, views or
  resources from a module beyond the one XML file and the label and artwork every tile already uses, and has no
  network permission.
- Nothing is shown until the owner says so, and the answer is pinned to the signing key. The owner judges a
  package name and an app label; the launcher judges the digest. The three apps of this repository share one key
  (DESIGN.md §15.3), so one answer covers an update of any of them.
- A card can only make its own activity look different; it cannot start anything else. Launching it is the same
  as launching the app's ordinary tile.
- Label rules are ASCII only so that look-alike letters cannot imitate another name, and labels that match
  another installed app or the launcher's own UI are refused.
- The card code can never take the home screen down: if any part of it fails on a TV, cards switch off for that
  session with one log line and Home behaves as with `module_cards` false.
- Residual risk: an app that holds the "draw over other apps" permission could draw over the prompt text. It
  cannot press the prompt's buttons from a remote. The prompt is only shown while the home screen has window
  focus and is dismissed when the home screen leaves the front.

## 6. Testing a card

Install over ADB, press Home, and the prompt appears:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell input keyevent KEYCODE_HOME
adb shell pm dump <package> | grep -B2 -A6 category.CARD     # did the filter and meta-data land on the right activity?
adb logcat -s VerySmartTV.Cards                              # one line per rejected card, with the reason
```

Then: answer Keep hidden and check the app keeps its ordinary tile; hold OK on Settings, open the Module cards
list, set it to Shown, and check the card takes the tile's place; press OK on the card and check your app opens;
uninstall and check the card is gone.

## 7. Release and keep your key

The owner's answer is pinned to your signing certificate. Publish the certificate digest with every release so
people can check what they install (`apksigner verify --print-certs your.apk | grep SHA-256`), sign every update
with the same key (a different key makes every TV ask again), sign with one certificate, and keep the keystore out
of your repo.
