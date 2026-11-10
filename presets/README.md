# Debloat presets

One folder per TV model under [`lists/`](lists/), each with the two files the setup tool reads:

| File | What |
|---|---|
| `debloat.txt` | One package name per line, `#` comments allowed, a trailing `# note` on a line allowed. The first line is a comment naming the TV, its Android version and firmware; the header that follows says what the TV was found with, what was kept and why, and often the batches it was applied in (`# ---- Batch N ----`, which the tool applies one at a time with a Home check and a screenshot between). Everything a list names is **disabled for user 0** (`pm disable-user --user 0`), never uninstalled. |
| `tv.json` | `tv` (the model as the header names it), `os` (`Android TV 10`, `Google TV 12`, `Fire OS 7`), `brand`, `notes` (a sentence on what the list does and keeps). |

[`index.json`](index.json) is generated from them by `tools/dev/build_presets_index.py` and committed: per
preset the slug, the first header line, the identifiers mined from the header (`tokens`, lower-cased with
non-alphanumerics collapsed to `-`), the SDK level, the package count, the stock launcher the header's Undo
line named, and the source path in the repository the list came from. `setup debloat` matches a TV's
`getprop` values to these tokens (DESIGN.md §17.3): an exact `ro.product.model` or `ro.product.device`
match is the only tier offered as the pre-selected answer, and the whole header is shown before anything
is sent. `build_presets_index.py --check` fails when the committed index is stale; CI and the tests run it.

## Where the lists come from

These lists were written by the **CleanYourTV community** (https://github.com/Gathin23/cleanyourtv, MIT,
© 2026 Gathin and contributors) and are carried here from that repository's `examples/` folders, one list
per folder, at the commit `index.json` names under `generated_from`. Each file's header names the tester
and the TV it was tried on. The only edit the carry made is the `# Apply:` / `# Undo:` command lines, which
now name this tool (`setup debloat --preset <slug>`, `setup restore --preset <slug> [--stock <package>]`);
the rest of each header is the community's text as written, including its mentions of the upstream
scripts. [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) names the repository and
[`LICENSE`](../LICENSE) carries its permission notice. Per the owner's decision (ADR-029) there is no
per-contributor file: the headers and the upstream repository's history hold that.

A fix that makes a list better for every TV belongs upstream at
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) too, so send it there as well.

## Safety rules the tool applies to every list

- **Core packages are refused**, the whole list, with no override: the permission controller, package
  installer, ext services and shared, module metadata, the mainline overlays, `com.android.providers.*`
  (the calendar, contacts, user-dictionary and blocked-number providers excepted), SystemUI, shell, phone,
  the settings apps, the SDK sandbox, fused location, and MediaTek's TV middleware (`lists.CORE_RE`).
  Without them a TV may not boot again.
- **The Home guard**: a list that names the app owning Home is applied without it while no other launcher is
  installed and staying enabled, or while Google TV's setup wizard still answers HOME ahead of them.
- **setupwraith** stays enabled while the saved Wi-Fi network belongs to it (disabling it deletes the
  network), or while the TV cannot say.
- **The software under the HDMI/AV inputs** (`TV_INPUT_HARDWARE`) and **the apps that open the inputs and
  Settings** are kept unless asked for (`--allow-input-drivers`, `--allow-handlers`).
- **The record first**: the state of every listed package, Home and the animation scales is written to
  `~/.verysmarttv/records/` before the first change; `setup restore` replays it, so a package the owner had
  disabled already stays disabled and Home goes back to what owned it.

## Adding or updating a list

1. Make `lists/<brand>-<model>/debloat.txt` and `tv.json` in the format above (the tests in
   `setup/tests/test_presets.py` check the shape, that no core package is named, that no address or
   e-mail is in the file, and that a package commented out to keep it is not also listed active).
2. Run `tools/dev/build_presets_index.py` and commit `index.json` with the list.
3. Say in the header what TV, Android version and firmware you tested on, and what you kept and why.
