# Setting up a TV

`setup/` is the computer-side tool: it finds your Android TV on the Wi-Fi, installs the three VerySmartTV
apps over ADB, makes the launcher Home, pushes a theme and, if you want, turns off the TV maker's
preloaded apps from a community list. Pure Python, no `adb` binary needed; it runs on macOS, Linux and
Windows with Python 3.9 or newer.

Nothing is uninstalled, rooted or flashed. Everything the clean-up does is undone by `setup restore`.

## One-liners

```bash
git clone https://github.com/itsluminous/verysmarttv && cd verysmarttv
setup/setup.sh                       # macOS, Linux
```

```powershell
git clone https://github.com/itsluminous/verysmarttv; cd verysmarttv
powershell -ExecutionPolicy Bypass -File setup\setup.ps1     # Windows
```

The bootstrap finds a Python 3.9+, makes a venv under `~/.verysmarttv/venv` with the one package the tool
needs (`cryptography`, for the ADB key), and runs the guided flow. Without Python it says where to get it.
Any subcommand after the script name runs that command instead: `setup/setup.sh install --apk-dir dist/`.

## Before you start: debugging on the TV

Settings → Device Preferences (or System) → About → press OK on **Build** seven times → back → Developer
options → **USB debugging** on. If there is a **Network debugging** or **ADB debugging** switch, turn that on
too. Keep the TV on. The flow prints the steps for your brand when nothing answers (`--brand xiaomi`).

A Chromecast with Google TV or a Google TV Streamer has only Wireless debugging, which this tool does not
speak: pair it once with Google's adb (`adb pair`, `adb connect`), run `adb tcpip 5555`, then use the IP.

## What the flow asks

```
VerySmartTV setup 0.1.0
1  Find your TV            mDNS, then a scan of port 5555 on your network; pick one or type an IP
2  Connect                 accept "Allow USB debugging?" on the TV (tick "Always allow")
3  Get the apps            the latest GitHub release: three APKs + SHA256SUMS, checksums verified
4  Install                 companions first, then the launcher; Home = VerySmartTV; the stock launcher is
                           left enabled unless you say yes to disabling it (it stays installed either way).
                           A stock that keeps Home ahead of ours (a HOME priority, PatchWall) is disabled
                           first when you say yes, and Home handed over again
5  Theme                   frosted (the owner's), default, or skip
6  Clean up (optional)     the community list that matches your TV exactly, if there is one; apply it, show
                           it first, pick another slug, or skip. Whichever list you pick, its header is
                           printed and the same question is asked: disable these N packages? [y/N, e to
                           deselect packages]. The record is written before the first change
7  Companions              open Trakt on the TV and sign in at trakt.tv/activate; open Scores and follow
8  Close ADB?              what to switch off, and `setup adb-closed` to confirm
```

Every step is re-runnable. `--yes` takes every default (so it never disables the stock launcher and never
applies a clean-up list you did not name); `--dry-run` prints what the clean-up would send.

**Keeping some of a list.** Answer `e` at the question and the list is printed numbered; type the numbers of
the packages to keep and they are turned off (or back on, the next time). The result is your copy of the list,
`~/.verysmarttv/lists/<slug>-edited.txt`, with each kept line as `# off: <package>`; from then on `setup
debloat`, `setup check` and `setup restore --preset <slug>` all read that copy for the slug, so the record and
the undo follow what you applied. Delete the file to go back to the community list as published. With your own
`--list` file there is no `e`: edit the file.

**Factory and service-menu packages.** Some lists disable a package whose name holds `factory` or `hkcfac`
(`com.duokan.factorytest`, `mediatek.factorymenu.ui`, `com.cvte.tv.hkcfac`): the TV maker's factory-test,
service-menu and board self-test apps. One is sometimes disabled because it claims Home (the Mi TV 4A's list
does). Nothing you use day to day needs them, but with them disabled the TV's factory or service menu, the one
a repair shop opens with a key sequence, and some board self-tests stop working until they are enabled again.
When a list holds such a package the tool prints a warning block naming each one before the question, and that
question needs a typed yes even with `--yes`; `setup restore` re-enables them with the rest of the list. To
apply a list without them, comment those lines out of a copy and pass it with `--list`. The tool never starts
a factory package, not even to look.

| Option | Does |
|---|---|
| `--host 192.168.1.50` | skip discovery; `ip:port` for a TV on another port. Remembered for the next run |
| `--apk-dir dist/` | install local APKs (a build's `*-debug.apk`, or release names) instead of the GitHub release |
| `--tag v1.2.3`, `--prerelease` | a particular release, or the newest even when it is a prerelease |
| `--theme frosted`, `--theme skip` | the theme without asking |
| `--preset <slug>`, `--preset none` | a list from `presets/lists/` by folder name, or no clean-up |
| `--stock-launcher <package>` / `none` | when the TV's Home is a launcher the tool does not know |
| `--disable-stock` | disable the stock launcher once VerySmartTV answers HOME, without asking; and first, when it keeps Home ahead of ours (below) |

## Verifying a download

Step 3 of the flow downloads the release's three APKs and its `SHA256SUMS` and refuses an APK whose checksum
does not match. To check a download yourself, or APKs you install by hand:

```bash
sha256sum -c SHA256SUMS                                        # macOS: shasum -a 256 -c SHA256SUMS
apksigner verify --print-certs verysmarttv-launcher-<version>.apk   # from the Android SDK build-tools
```

Every APK of every release from 0.1.0 on is signed with the one release key, whose certificate SHA-256 digest is

```
15d5085a0400d720d4aea65edab4df72c6e5a5313844715c3e56859cec99c21d
```

`release.yml` checks each APK against it before publishing and prints it at the end of every release's notes. A
different digest is not a build of this repository's release key; its companions will not talk to this launcher
(their providers are signature-guarded), and Android refuses to install it over an existing release.

## The commands one by one

| Command | Does |
|---|---|
| `setup discover` | TVs answering `_adb._tcp` / `_adb-tls-connect._tcp` over mDNS, then every host on your /24 with port 5555 open (`--subnet 192.168.1.0/24`) |
| `setup probe --host <ip>` | model, Android version, firmware, who owns Home and its HOME intent-filter priority, and the preset that matches |
| `setup install --host <ip> [--apk-dir d] [--disable-stock]` | the three APKs, Home handover with the Home guard (below) |
| `setup theme --host <ip> frosted` / `--reset` | push a theme folder through the launcher's provider and restart Home; `--reset` removes it |
| `setup debloat --host <ip> [--preset s \| --list f] [--dry-run] [--show]` | match, show the list (its header, or the whole list with its notes: `--show`, or whenever you did not pass `--yes`), ask, record, apply in batches with a screenshot each (`~/.verysmarttv/shots/`). A list for another brand that happens to share your TV's model or device name is listed last among the candidates and never pre-selected |
| `setup restore --host <ip> [--preset s \| --list f] [--stock p]` | replay the record: what was disabled before stays disabled, Home goes back to its owner. `--preset` reads your edited copy of the list when there is one |
| `setup check --host <ip> --preset s [--fix]` | what a firmware update undid (exit 1 on drift); `--fix` re-applies it with the same safety checks |
| `setup screenshot --host <ip> out.png [HOME DPAD_DOWN] [--wait 2]` | press keys, capture, clean the PNG |
| `setup adb-closed --host <ip>` | exit 0 only when the TV refuses the connection |

## The Home guard

The install never leaves a TV without something to start when Home is pressed:

- The launcher is pushed, installed and **started** (`am start -W`, "Status: ok") before Home is handed
  over, and `set-home-activity` is checked afterwards: if Home does not resolve to VerySmartTV the stock
  launcher is put back and the finishing commands are also written to `~/.verysmarttv/handover-unfinished.txt`.
- The stock launcher is **left enabled** by default. Say yes (or `--disable-stock`) and it is disabled only
  once VerySmartTV answers HOME; if Home then moves away, it is re-enabled at once. Only the four known stock
  launchers (`com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`,
  `com.google.android.leanbacklauncher`, `com.mitv.tvhome`) or a package you name are ever disabled.
- **A stock that keeps Home.** Some stock launchers give their HOME activity an intent-filter priority above 0
  (`setup probe` prints `home priority = 2` for Google's TV launcher, and the Mi TV 4A's PatchWall keeps Home the
  same way), so `set-home-activity` says Success and Home still resolves to them. Without your say-so the install
  rolls back as above and says to run again with `--disable-stock`. With `--disable-stock`, or a yes to the
  question the guided flow asks at that point, that same recognised stock (or the package you named) is disabled
  **first**, Home is handed over again and checked; if VerySmartTV still does not answer, the stock is re-enabled
  and put back on Home like any other rollback. Nothing else is ever disabled this way.
- A clean-up list that names the app owning Home is applied without it while nothing else can answer Home,
  or while Google TV's setup wizard (`setupwraith`) still answers it ahead of the other launchers; the wizard
  itself is never disabled while it owns the saved Wi-Fi network.
- Core Android packages are refused outright; the software under the HDMI inputs and the apps that open
  the inputs and Settings are kept unless asked for. The full list: [presets/README.md](../presets/README.md).

## Undo

```bash
setup/setup.sh restore --host <ip>            # one record for this TV: replayed
setup/setup.sh restore --host <ip> --preset <slug>
```

The record (`~/.verysmarttv/records/recovery-<tv>-<list>.json`) is written before the first change and
never overwritten by a retry, so a half-applied run still restores to the state from before the first one.
Without a record, restore enables every listed package and says so. The launcher stays installed either
way; remove it from the TV's Settings → Apps if you want it gone, after making another launcher Home.

## After a firmware update

An update re-enables packages, hands Home back to the stock launcher and adds preloads. `setup check
--preset <slug>` reports it (exit 1); `--fix` puts the list back and Home on VerySmartTV, with the same
checks as the first run. It never writes a record of the cleaned TV, so undo still means undo.

## Closing ADB

Port 5555 is plaintext, and on some firmware any device on the LAN gets a shell through it. When you are
done: on the TV turn **USB debugging**, **Network/ADB debugging** and **Wireless debugging** off (whichever it
has) and revoke the USB debugging authorisations; reboot; then `setup adb-closed --host <ip>` must say the
port is closed (a timeout proves nothing). The launcher keeps working without debugging.

## Troubleshooting

- **"refused the connection"**: debugging is off or on another port; see the steps above.
- **"took the connection but did not finish the handshake"**: accept "Allow USB debugging?" on the TV.
- **"is a Wireless debugging port"**: pair once with Google's adb and `adb tcpip 5555`.
- **macOS "could not reach"** while the TV is on: Local Network privacy; allow Terminal (or your editor)
  under System Settings → Privacy & Security → Local Network and reopen it.
- **Home resolves to the setup wizard** on Google TV: its RecoveryActivity claims HOME ahead of an installed
  launcher. Apply a list that disables `com.google.android.tungsten.setupwraith` (after forgetting and
  re-joining the Wi-Fi network in Settings if the wizard owns it), then install again.
- **"Home did not move to VerySmartTV … run again with --disable-stock"**: the stock launcher answers Home
  ahead of ours (its HOME priority, see `setup probe`); run `setup install --disable-stock` (or say yes in the
  guided flow) and it is disabled first, then Home handed over. `setup restore`, or `pm enable --user 0 <package>`
  over adb, brings it back.
- **A TV refuses `pm disable-user` for every package**: that firmware does not allow it; stop there. The
  launcher half still works.
- **A CVTE board** (the TV lists `com.cvte.*` packages; many Haier, JVC, Artel and store-brand sets): leave its
  stock launcher (`com.cvte.tv.launcher.*`) enabled, which is what the install does unless you name it with
  `--stock-launcher`. Reboot the TV once after the install and check that Home and ADB come back before any
  clean-up: a JVC set hung on its boot animation when its launcher and a key helper were changed in one go.
