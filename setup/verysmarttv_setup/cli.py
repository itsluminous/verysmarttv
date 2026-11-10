"""The command line: `python -m verysmarttv_setup <command>`; `setup` is the guided flow of DESIGN.md §16.2."""
import argparse
import errno
import os
import re
import socket
import sys
import time
from pathlib import Path

from . import VERSION, check, debloat, discover, github, homeguard, install, lists, presets, probe, restore, theme
from . import adb, paths

ENV_HOST = "VERYSMARTTV_HOST"


class Quit(Exception):
    """Stop with a message and an exit status (2 = nothing reached the TV)."""

    def __init__(self, message, code=1):
        super().__init__(message)
        self.code = code


# -- helpers ----------------------------------------------------------------------------------------------

def say(text=""):
    print(text, flush=True)


def ask(prompt: str, default: str = "", yes=False) -> str:
    if yes:
        return default
    if not sys.stdin or not sys.stdin.isatty():
        return default
    try:
        answer = input(f"{prompt} ").strip()
    except EOFError:
        return default
    return answer or default


def confirm(prompt: str, default=False, yes=False) -> bool:
    if yes:
        return True
    answer = ask(f"{prompt} [{'Y/n' if default else 'y/N'}]", "", yes)
    if not answer:
        return default
    return answer.lower().startswith("y")


def resolve_host(args) -> str:
    raw = getattr(args, "host", None) or os.environ.get(ENV_HOST)
    if not raw:
        last = discover.last_tv()
        if last:
            raw = last["address"]
    if not raw:
        raise Quit("no TV address: pass --host <ip>, or run `setup discover` first", 2)
    problem = adb.address_problem(raw)
    if problem:
        raise Quit(f"--host {raw!r}: {problem}", 2)
    return raw.strip()


def open_device(args, wait_for_prompt=True):
    """Connect, or stop with the worded reason (exit 2: nothing ran on the TV)."""
    address = resolve_host(args)
    host, port = adb.parse_address(address)
    say(f"connecting to {host}:{port} ...")
    try:
        dev = adb.AdbDevice(host, port, timeout=10.0, auth_timeout=120.0 if wait_for_prompt else 10.0).connect()
    except Exception as e:
        raise Quit(adb.connect_error(e, host, port), 2) from e
    props = probe.read_props(dev)
    discover.remember(address, props)
    say(f"connected: {probe.describe(props)}")
    return dev, props


def list_path_for(args, presets_all=None) -> Path:
    if getattr(args, "list", None):
        return Path(args.list)
    slug = getattr(args, "preset", None)
    if not slug:
        raise Quit("name the list: --preset <slug> (see presets/lists/) or --list <file>", 2)
    preset = presets.by_slug(presets_all or presets.load_index(), slug)
    if preset is None:
        raise Quit(f"no preset named {slug!r}; see presets/README.md for the folder names", 2)
    edited = paths.lists_dir() / f"{slug}-edited.txt"
    return edited if edited.exists() else preset.path


def debloat_options(args) -> debloat.Options:
    return debloat.Options(force_home=getattr(args, "force_home", False),
                           force_setupwraith=getattr(args, "force_setupwraith", False),
                           allow_input_drivers=getattr(args, "allow_input_drivers", False),
                           allow_handlers=getattr(args, "allow_handlers", False),
                           dry_run=getattr(args, "dry_run", False),
                           screenshots=not getattr(args, "no_screenshots", False))


def show_list(text: str, log=None):
    """The header, then every package numbered, with its note; a deselected one is marked off."""
    log = log or say
    notes = lists.notes(text)
    log(lists.header(text))
    log("")
    for i, (_, name, active) in enumerate(lists.entries(text), 1):
        note = notes.get(name, "")
        log(f"  {i:3d}  {'' if active else 'off  '}{name}" + (f"   # {note}" if note else ""))


def read_list_text(path):
    """(text, names) of a list file; raises lists.ListError."""
    try:
        text = Path(path).read_text(encoding="utf-8-sig", errors="replace")
    except OSError as e:
        raise lists.ListError(f"could not read {path} ({e.strerror or e})") from e
    return text, lists.parse(text, str(path))


def factory_warning(factory: list, log=None):
    """Printed before the question when the list disables factory or service-menu packages."""
    log = log or say
    log("")
    log("WARNING: this list disables factory and service-menu packages:")
    for name in factory:
        log(f"  {name}")
    log("These are the TV maker's factory-test, service-menu and board self-test apps. With them disabled the")
    log("TV's factory or service menu (the one a repair shop opens with a key sequence) and some board")
    log("self-tests stop working until they are enabled again; nothing else on the TV uses them, and some")
    log("lists disable one because it claims Home. `setup restore` re-enables them with the rest of the list.")
    log("This needs a typed yes, even with --yes.")


def confirm_list(path, text, names, yes=False, full=False, log=None, slug=None):
    """The one question every clean-up path asks before a list is applied: the list's header (the whole
    list with its notes when `full`), then yes/no, No by default. `--yes` answers it for a list the user
    named on the command line, unless the list disables factory packages: that always needs a typed yes.
    For a preset (`slug`), `e` deselects packages by number first (`deselect`). Returns the path to apply,
    which is the edited copy after an `e`, or None."""
    log = log or say
    while True:
        if full:
            show_list(text, log)
        else:
            log(lists.header(text))
        log(f"{len(names)} packages from {path}")
        factory = lists.factory_packages(names)
        if factory:
            factory_warning(factory, log)
        if yes and not factory:
            return path
        hint = ", e to deselect packages" if slug else ""
        answer = ask(f"disable these {len(names)} packages for user 0 (nothing is uninstalled; `setup restore` "
                     f"undoes it)? [y/N{hint}]", "", False).lower()
        if answer.startswith("e") and slug:
            path, text, names = deselect(slug, path, text, names, log)
            full = True
            continue
        if answer.startswith("y"):
            return path
        if factory and (not sys.stdin or not sys.stdin.isatty()):
            log("not applied: a list with factory packages needs a typed yes, so run this in a terminal (without "
                "--yes), or deselect those packages with e")
        return None


def deselect(slug, path, text, names, log):
    """Toggle packages off (or back on) by their numbers in `show_list`, and keep the result as the local copy
    `~/.verysmarttv/lists/<slug>-edited.txt`, which `debloat`, `check` and `restore` all read for that slug
    from then on. Returns the (path, text, names) to go on with; unchanged when nothing was toggled."""
    show_list(text, log)
    raw = ask("numbers of the packages to turn off, or back on (space-separated; Enter for none):", "", False)
    positions = [int(x) for x in re.findall(r"\d+", raw)]
    if not positions:
        return path, text, names
    try:
        new_text = lists.toggle(text, positions)
    except lists.ListError as e:
        log(str(e))
        return path, text, names
    if not any(active for _, _, active in lists.entries(new_text)):
        log("every package would be off; nothing to apply, so the list is left as it was")
        return path, text, names
    edited = paths.lists_dir() / f"{slug}-edited.txt"
    edited.write_text(new_text, encoding="utf-8")
    log(f"your copy of the list is {edited}; `setup check` and `setup restore --preset {slug}` use it too")
    return edited, new_text, lists.parse(new_text, str(edited))


# -- commands ---------------------------------------------------------------------------------------------

def cmd_discover(args) -> int:
    found = discover.mdns(args.mdns_seconds)
    say(f"mDNS: {len(found)} answer(s)")
    if not found or args.scan:
        say(f"scanning port {discover.ADB_PORT} on {args.subnet or 'this /24'} ...")
        for entry in discover.scan(args.subnet):
            if entry not in found:
                found.append(entry)
    for i, entry in enumerate(found, 1):
        say(f"  {i}. {entry.address}" + (f"  ({entry.name}, {entry.source})" if entry.name else f"  ({entry.source})"))
    if not found:
        say("no TV answered. On the TV:")
        for step in discover.hints(args.brand or ""):
            say(f"  - {step}")
        say("then run this again, or pass --host <ip> (the TV's address is under Settings > Network).")
        return 1
    return 0


def cmd_probe(args) -> int:
    dev, props = open_device(args)
    try:
        for key in probe.PROPS:
            if key != "ro.serialno":
                say(f"{key} = {props[key]}")
        say(f"home = {probe.query_home(dev) or 'nothing'}")
        say(f"home claimants = {', '.join(probe.home_claims(dev)) or 'none'}")
        matches = presets.match(props, presets.load_index())
        best = presets.suggestion(matches)
        say(f"preset: {best.preset.slug + ' (' + best.text + ')' if best else 'no exact match'}; "
            f"{len(matches)} candidate(s) in all")
    finally:
        dev.close()
    return 0


def obtain_apks(args, log=say) -> dict:
    if getattr(args, "apk_dir", None):
        apks = install.find_apks(args.apk_dir)
        log(f"APKs from {args.apk_dir}: " + ", ".join(Path(apks[a]).name for a in install.APPS))
        return apks
    log("asking GitHub for the latest release ...")
    release = github.fetch_release(getattr(args, "tag", None), getattr(args, "prerelease", False))
    out = github.download(release, log=log)
    log(f"release {out['version']}: SHA-256 verified for the three APKs")
    return out


def cmd_install(args) -> int:
    try:
        apks = obtain_apks(args)
    except (github.ReleaseError, install.InstallError) as e:
        raise Quit(str(e), 2) from e
    dev, props = open_device(args)
    try:
        result = install.run(dev, apks, props, args.stock_launcher, args.disable_stock, say, progress_line)
    except install.InstallError as e:
        raise Quit(str(e), 1) from e
    finally:
        dev.close()
    say(f"installed. Press Home on the remote to see it. (Home = {result['home']})")
    return 0


def progress_line(i, n):
    print(f"\r    chunk {i}/{n}", end="" if i < n else "\n", flush=True)


def cmd_theme(args) -> int:
    dev, _ = open_device(args)
    try:
        if args.reset:
            theme.reset(dev, say)
        else:
            theme.push(dev, args.theme, say)
    finally:
        dev.close()
    return 0


def cmd_debloat(args) -> int:
    all_presets = presets.load_index()
    dev, props = open_device(args)
    try:
        if not args.preset and not args.list:
            matches = presets.match(props, all_presets)
            best = presets.suggestion(matches)
            if not best:
                say("no exact community list for this TV; the candidates by brand:")
                for m in matches[:8]:
                    say(f"  {m.preset.slug}: {m.text} ({m.preset.packages} packages)")
                raise Quit("pass --preset <slug> to apply one, or --list <file> for your own", 2)
            say(f"suggested preset: {best.preset.slug} ({best.text}, {best.preset.packages} packages), by the "
                "community (MIT; presets/README.md names the source)")
            args.preset = best.preset.slug
        path = list_path_for(args, all_presets)
        text, names = read_list_text(path)
        if args.dry_run:
            show_list(text)
            if lists.factory_packages(names):
                factory_warning(lists.factory_packages(names))
        else:
            path = confirm_list(path, text, names, args.yes, full=args.show or not args.yes, slug=args.preset)
            if not path:
                say("nothing was changed on the TV")
                return 0
        return debloat.apply(dev, path, props, debloat_options(args), say)
    except lists.ListError as e:
        raise Quit(str(e), 2) from e
    finally:
        dev.close()


def cmd_restore(args) -> int:
    dev, props = open_device(args)
    try:
        if not args.preset and not args.list:
            records = restore.find_records(props)
            if len(records) == 1:
                import json
                args.list = json.loads(records[0].read_text(encoding="utf-8")).get("list")
                say(f"one record for this TV: {records[0].name} (list {args.list})")
            elif records:
                raise Quit("this TV has several records; name the list with --preset or --list:\n  "
                           + "\n  ".join(r.name for r in records), 2)
            else:
                raise Quit("no recovery record for this TV; name the list that was applied with --preset or --list "
                           "(without a record every listed package is enabled)", 2)
        path = list_path_for(args)
        return restore.run(dev, path, props, args.stock, args.dry_run, say)
    except (lists.ListError, ValueError) as e:
        raise Quit(str(e), 2) from e
    finally:
        dev.close()


def cmd_check(args) -> int:
    dev, props = open_device(args)
    try:
        return check.run(dev, list_path_for(args), props, args.fix, debloat_options(args), say)
    except lists.ListError as e:
        raise Quit(str(e), 2) from e
    finally:
        dev.close()


def cmd_screenshot(args) -> int:
    dev, _ = open_device(args)
    try:
        if args.keys:
            dev.press(*args.keys)
        if args.wait:
            time.sleep(args.wait)
        size = dev.screenshot(args.out)
    except (adb.AdbError, ValueError) as e:
        raise Quit(str(e), 1) from e
    finally:
        dev.close()
    say(f"screenshot {args.out} ({size} bytes)")
    return 0


def cmd_adb_closed(args) -> int:
    """0 the TV refused the connection (closed); 1 still open; 2 nothing could be learned."""
    try:
        host, port = adb.parse_address(args.host or resolve_host(args))
    except ValueError as e:
        raise Quit(str(e), 2) from e
    try:
        with socket.create_connection((host, port), timeout=3.0):
            pass
    except ConnectionRefusedError:
        say(f"{host}:{port} is closed: the TV refused the connection.")
        return 0
    except socket.timeout:
        say(f"could not check {host}:{port}: no answer in 3 s, which proves nothing (off, asleep, rebooting, or "
            "another address). Only a refused connection shows the port closed.")
        return 2
    except OSError as e:
        note = ""
        if sys.platform == "darwin" and e.errno == errno.EHOSTUNREACH:
            note = (" On macOS this is also what Local Network privacy looks like (System Settings > Privacy & "
                    "Security > Local Network).")
        say(f"could not check {host}:{port} ({e}). Is the TV on and on this network?{note}")
        return 2
    say(f"{host}:{port} is still open: network ADB is on. On the TV, turn USB debugging, Network/ADB debugging "
        "and Wireless debugging off, reboot it, and check again.")
    return 1


def cmd_setup(args) -> int:
    """The guided flow: find, connect, get the apps, install, theme, clean up, companions, close ADB."""
    say(f"VerySmartTV setup {VERSION}")
    yes = args.yes
    # 1 Find
    if args.host or os.environ.get(ENV_HOST):
        say(f"1  Find your TV: {args.host or os.environ.get(ENV_HOST)} (given)")
    else:
        say("1  Find your TV")
        found = discover.mdns(2.0)
        if not found:
            say("   scanning this network for port 5555 ...")
            found = discover.scan()
        last = discover.last_tv()
        if found:
            for i, entry in enumerate(found, 1):
                say(f"   {i}. {entry.address}" + (f" ({entry.name})" if entry.name else ""))
            choice = ask(f"   which one? [1-{len(found)}, or an IP]", "1", yes)
            args.host = found[int(choice) - 1].address if choice.isdigit() and 0 < int(choice) <= len(found) else choice
        elif last:
            args.host = ask(f"   no TV answered; the last one was {last['address']}. Use it, or type an IP:", last["address"], yes)
        else:
            say("2  Enable USB debugging (nothing answered). On the TV:")
            for step in discover.hints(args.brand or ""):
                say(f"   - {step}")
            args.host = ask("   then type the TV's IP (Settings > Network):", "", yes)
            if not args.host:
                raise Quit("no TV to set up", 2)
    say("2  Connect (accept \"Allow USB debugging?\" on the TV if it asks)")
    dev, props = open_device(args)
    try:
        # 3 Apps
        say("3  Get the apps")
        try:
            apks = obtain_apks(args, lambda t: say("   " + t))
        except (github.ReleaseError, install.InstallError) as e:
            raise Quit(str(e), 2) from e
        # 4 Install
        say("4  Install")
        try:
            result = install.run(dev, apks, props, args.stock_launcher, False, lambda t: say("   " + t), progress_line)
        except install.InstallError as e:
            raise Quit(str(e), 1) from e
        # `--yes` takes the question's default, which is No (§16.2 step 4): only `--disable-stock` or a typed yes
        # disables the stock launcher.
        if result["stock"] and (args.disable_stock or (not yes and confirm(
                f"   disable the stock launcher {result['stock']} now (it stays installed; restore brings it back)?",
                False))):
            try:
                disable_stock_now(dev, result["stock"])
            except install.InstallError as e:
                say(f"   {e}")
        # 5 Theme
        say("5  Theme")
        choice = args.theme or ask(f"   theme: {', '.join(theme.available())} or skip", "frosted", yes)
        if choice and choice != "skip":
            theme.push(dev, choice, lambda t: say("   " + t))
        # 6 Clean up
        say("6  Clean up (optional)")
        if args.preset != "none":
            cleanup_step(dev, props, args, yes, lambda t: say("   " + t))
        else:
            say("   skipped (--preset none)")
        # 7 Companions
        say("7  Companions")
        say("   Open Trakt on the TV and sign in at trakt.tv/activate on your phone.")
        say("   Open Scores and pick what to follow.")
    finally:
        dev.close()
    # 8 Close ADB
    say("8  Close ADB?")
    say("   Port 5555 gives a shell to any device on your network. When you are done: turn USB debugging, "
        "Network/ADB debugging and Wireless debugging off on the TV, reboot it, then run `setup adb-closed`.")
    return 0


def cleanup_step(dev, props, args, yes, log):
    """Step 6 of the guided flow. Whichever way a list is chosen (the suggestion, a slug typed at the prompt,
    `--preset` on the command line) it goes through `confirm_list`: the header, then the same yes/no."""
    all_presets = presets.load_index()
    matches = presets.match(props, all_presets)
    best = presets.suggestion(matches)
    slug, full = args.preset, False
    if not slug and best:
        log(f"suggested preset: {best.preset.slug} ({best.text}, {best.preset.packages} packages) by the "
            "community (MIT; presets/README.md names the source)")
        answer = ask("   apply it / show the list first / pick another slug / skip? [apply/show/<slug>/skip]", "skip", yes)
        if answer == "show":
            answer, full = "apply", True
        slug = best.preset.slug if answer == "apply" else (None if answer == "skip" else answer)
    elif not slug:
        nearest = ", ".join(f"{m.preset.slug} ({m.text})" for m in matches[:5])
        log("no exact community list for this TV" + (f"; nearest: {nearest}" if nearest else ""))
        slug = ask("   a preset slug to apply, or skip:", "skip", yes)
        slug = None if slug == "skip" else slug
    if not slug:
        return
    args.preset = slug
    try:
        path = list_path_for(args, all_presets)
        text, names = read_list_text(path)
        path = confirm_list(path, text, names, yes, full, log, slug=slug)
        if not path:
            log("nothing was changed on the TV")
            return
        debloat.apply(dev, path, props, debloat_options(args), log)
    except (lists.ListError, Quit) as e:
        log(str(e))


def disable_stock_now(dev, stock: str):
    dev.shell(f"pm disable-user --user 0 {stock}", 60)
    time.sleep(1)
    home = probe.query_home(dev)
    if homeguard.package_of(home) != homeguard.LAUNCHER:
        install.rollback(dev, stock, say)
        raise install.InstallError(f"disabling {stock} moved Home to {home or 'nothing'}; it was re-enabled")
    say(f"   {stock} disabled for user 0")


# -- parser -----------------------------------------------------------------------------------------------

def add_host(p):
    p.add_argument("--host", help=f"the TV's IP (or IP:port); default ${ENV_HOST} or the last TV used")


def add_debloat_flags(p):
    p.add_argument("--preset", help="a community list by folder name (presets/lists/<slug>), or none")
    p.add_argument("--list", help="your own debloat list file")
    p.add_argument("--dry-run", action="store_true", help="print the commands instead of sending them")
    p.add_argument("--yes", "-y", action="store_true", help="answer every question with its default/yes")
    p.add_argument("--no-screenshots", action="store_true")
    p.add_argument("--force-home", action="store_true", help="disable the Home owner even with no other launcher")
    p.add_argument("--force-setupwraith", action="store_true", help="disable setupwraith even when it owns the Wi-Fi")
    p.add_argument("--allow-input-drivers", action="store_true", help="disable packages under the HDMI/AV inputs")
    p.add_argument("--allow-handlers", action="store_true", help="disable the apps that open inputs and Settings")


def add_install_flags(p):
    p.add_argument("--apk-dir", help="install these APKs instead of the latest GitHub release")
    p.add_argument("--tag", help="a release tag (v1.2.3) instead of the latest")
    p.add_argument("--prerelease", action="store_true", help="take the newest release even when it is a prerelease")
    p.add_argument("--stock-launcher", help="the stock launcher package, or none, when the TV's Home is unknown")
    p.add_argument("--disable-stock", action="store_true", help="disable the stock launcher once ours is Home")


def build_parser():
    p = argparse.ArgumentParser(prog="setup", description="VerySmartTV: install the launcher and its companions on an "
                                "Android TV over Wi-Fi, and optionally clean the TV up.")
    p.add_argument("--version", action="version", version=VERSION)
    sub = p.add_subparsers(dest="command", required=True)

    s = sub.add_parser("setup", help="the guided flow: find, install, theme, clean up")
    add_host(s); add_install_flags(s); add_debloat_flags(s)
    s.add_argument("--theme", help="theme folder or name to push (default: ask; frosted)")
    s.add_argument("--brand", help="your TV's brand, for the debugging steps when nothing answers")
    s.set_defaults(func=cmd_setup)

    s = sub.add_parser("discover", help="find TVs with ADB on this network")
    s.add_argument("--subnet", help="a /24 to scan, e.g. 192.168.1.0/24 (default: this computer's)")
    s.add_argument("--scan", action="store_true", help="scan even when mDNS answered")
    s.add_argument("--mdns-seconds", type=float, default=2.0)
    s.add_argument("--brand")
    s.set_defaults(func=cmd_discover)

    s = sub.add_parser("probe", help="model, Android version, Home owner, matching preset")
    add_host(s); s.set_defaults(func=cmd_probe)

    s = sub.add_parser("install", help="install the three apps and make VerySmartTV Home")
    add_host(s); add_install_flags(s); s.set_defaults(func=cmd_install)

    s = sub.add_parser("theme", help="push a theme folder, or --reset")
    add_host(s)
    s.add_argument("theme", nargs="?", help="a folder with config.json, or a name under themes/")
    s.add_argument("--reset", action="store_true")
    s.set_defaults(func=cmd_theme)

    s = sub.add_parser("debloat", help="disable a community list's packages, in batches, with a record first")
    add_host(s); add_debloat_flags(s)
    s.add_argument("--show", action="store_true", help="print the list with its notes before asking")
    s.set_defaults(func=cmd_debloat)

    s = sub.add_parser("restore", help="put the recorded state back")
    add_host(s)
    s.add_argument("--preset"); s.add_argument("--list")
    s.add_argument("--stock", help="the stock launcher to fall back on when no Home was recorded")
    s.add_argument("--dry-run", action="store_true")
    s.set_defaults(func=cmd_restore)

    s = sub.add_parser("check", help="what a firmware update undid; --fix re-applies the list")
    add_host(s); add_debloat_flags(s)
    s.add_argument("--fix", action="store_true")
    s.set_defaults(func=cmd_check)

    s = sub.add_parser("screenshot", help="capture the TV screen, after pressing keys")
    add_host(s)
    s.add_argument("out", help="the PNG to write")
    s.add_argument("keys", nargs="*", help="remote keys to press first (HOME, DPAD_DOWN, ...)")
    s.add_argument("--wait", type=float, default=0.0)
    s.set_defaults(func=cmd_screenshot)

    s = sub.add_parser("adb-closed", help="check that the TV's ADB port refuses connections")
    add_host(s); s.set_defaults(func=cmd_adb_closed)
    return p


def main(argv=None) -> int:
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except (AttributeError, ValueError):
            pass
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except Quit as e:
        print(str(e), file=sys.stderr)
        return e.code
    except adb.AdbError as e:
        print(f"ADB: {e}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\ninterrupted", file=sys.stderr)
        return 130
