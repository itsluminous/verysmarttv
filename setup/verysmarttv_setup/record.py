"""The recovery record: the state a debloat list is about to change, written before the first change, so that
undo means undo.

One file per TV and list under `~/.verysmarttv/records/`, named `recovery-<tv id>-<list digest>.json`; the TV
id is sha256(serial + model)[:12] (ADR-028), so the record follows the TV across DHCP leases. The first record
wins: a retry after a half-applied list must not record the broken state as the original. A later run only
adds names the list gained since, with their state now, and marks a package that was absent then and is
installed and enabled now as enabled.
"""
import hashlib
import json
import os
import re
import time
from pathlib import Path

from . import homeguard, paths, probe
from .lists import read_list

VERSION = 1
SETTINGS = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
SCALE_RE = re.compile(r"-?\d+(\.\d+)?")


class RecordError(RuntimeError):
    pass


def list_digest(list_path) -> str:
    return hashlib.sha1(str(Path(list_path).resolve()).encode()).hexdigest()[:8]


def record_path(list_path, tv_id: str) -> Path:
    return paths.records_dir() / f"recovery-{tv_id}-{list_digest(list_path)}.json"


def records_for(tv_id: str) -> list:
    return sorted(paths.records_dir().glob(f"recovery-{tv_id}-*.json"))


def load(path: Path) -> dict:
    try:
        record = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise RecordError(f"{path}: not a usable recovery record ({e}). It is left where it is: move it aside "
                          "deliberately, a retry must not replace the state recorded before the first change.")
    if record.get("version") != VERSION or not isinstance(record.get("packages"), dict):
        raise RecordError(f"{path}: unknown recovery record format; move it aside deliberately.")
    for name, state in record["packages"].items():
        if not probe.PACKAGE_RE.fullmatch(name):
            raise RecordError(f"{path}: not a package name in the record: {name!r}; move it aside deliberately.")
        if state not in ("absent", "disabled", "enabled"):
            raise RecordError(f"{path}: unknown state for {name}: {state!r}; move it aside deliberately.")
    home = record.get("home")
    if home is not None and not (isinstance(home, str) and probe.HOME_RE.fullmatch(home)):
        raise RecordError(f"{path}: not a Home component in the record: {home!r}; move it aside deliberately.")
    if home and (homeguard.is_chooser(home) or homeguard.not_a_launcher(home)):
        record["home"] = None
    if not isinstance(record.get("settings", {}), dict):
        raise RecordError(f"{path}: settings is not a mapping; move it aside deliberately.")
    return record


def write(path: Path, record: dict):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(record, f, indent=2, sort_keys=True)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def package_state(name, installed, disabled):
    return "absent" if name not in installed else "disabled" if name in disabled else "enabled"


def extend(path: Path, record: dict, names, installed, disabled):
    """Add what a second run would: names the list gained (with their state now) and packages recorded as
    absent that are installed and enabled now. Entries already there are never overwritten. Returns
    (added, arrived); the file is written only when something changed."""
    states = record["packages"]
    arrived = [n for n in names if states.get(n) == "absent" and package_state(n, installed, disabled) == "enabled"]
    added = [n for n in names if n not in states]
    for n in added:
        states[n] = package_state(n, installed, disabled)
    for n in arrived:
        states[n] = "enabled"
    if added or arrived:
        write(path, record)
    return added, arrived


def save(dev, list_path, props: dict, log=print) -> Path:
    """Read the TV and write the record for this TV and list, or extend the one it has. Returns its path."""
    names = read_list(list_path)
    path = record_path(list_path, probe.tv_id(props))
    if path.exists():
        record = load(path)
        log(f"keeping the recovery record from {record.get('created')}: {path}")
        if all(record["packages"].get(n, "absent") != "absent" for n in names):
            return path
        installed, disabled = probe.query_packages(dev)
        added, arrived = extend(path, record, names, installed, disabled)
        if added:
            log(f"  added {len(added)} names the list gained since: {', '.join(added)}")
        if arrived:
            log(f"  recorded {len(arrived)} as enabled, installed since the record was taken: {', '.join(arrived)}")
        return path

    installed, disabled = probe.query_packages(dev)
    home = probe.query_home(dev)
    scales = {key: dev.shell(f"settings get global {key}", 30).strip() for key in SETTINGS}
    if homeguard.is_chooser(home) or homeguard.not_a_launcher(home):
        home = None
    home_package = homeguard.package_of(home)
    targets = list(names)
    if home_package and home_package not in targets:
        targets.append(home_package)
    targets += [n for n in homeguard.KNOWN_STOCK_LAUNCHERS if n in installed and n not in targets]
    record = {
        "version": VERSION,
        "created": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "tv": {"id": probe.tv_id(props), "model": props.get("ro.product.model", ""),
               "brand": props.get("ro.product.brand", ""), "sdk": props.get("ro.build.version.sdk", "")},
        "list": str(Path(list_path).resolve()),
        "home": home,
        "settings": scales,
        "packages": {n: package_state(n, installed, disabled) for n in targets},
    }
    write(path, record)
    already = sum(1 for s in record["packages"].values() if s == "disabled")
    log(f"recorded {len(record['packages'])} packages ({already} already disabled, left alone by undo) -> {path}")
    log(f"  Home before: {home or 'no launcher component answered'}")
    return path


def restore_commands(record: dict, names: list, stock: str = None, log=print) -> list:
    """The shell commands that put the recorded states back, one per entry."""
    states = record["packages"]
    commands = [f"pm enable {n}" for n, s in states.items() if s == "enabled"]
    untouched = [(n, s) for n, s in states.items() if s != "enabled"]
    for n, s in untouched:
        log(f"  leaving {n} alone ({s} when the record was taken)")
    fallback = stock or "com.google.android.tvlauncher"
    fallback_state = states.get(fallback)
    home = record.get("home")
    resolve = (f"cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a "
               f"android.intent.action.MAIN -c android.intent.category.HOME {fallback} | tail -n 1)")
    if home and homeguard.package_of(home) != homeguard.LAUNCHER:
        commands.append(f"cmd package set-home-activity --user 0 {home}")
    elif home and fallback_state != "enabled":
        commands.append(f"cmd package set-home-activity --user 0 {home}")
        log(f"  Home goes back to {home}, which owned it when the record was taken; {fallback} was "
            f"{fallback_state or 'not in the record'} then, so it stays as it is")
    elif not home and fallback_state in ("disabled", "absent"):
        log(f"  leaving Home alone: none was recorded, and {fallback} was {fallback_state} then")
    else:
        commands.append(resolve)
    for key in SETTINGS:
        value = str(record.get("settings", {}).get(key, "")).strip()
        if value == "null":
            commands.append(f"settings delete global {key}")
        elif SCALE_RE.fullmatch(value):
            commands.append(f"settings put global {key} {value}")
        elif value:
            log(f"  skipping {key}: the record holds {value!r}, not a number")
    edited = [n for n in names if n not in states]
    if edited:
        log(f"  not in the record, leaving alone: {', '.join(edited)}")
    return commands


def no_record_commands(names: list, stock: str = None) -> list:
    """Without a record: enable every listed package (and the stock launcher), reset Home and the scales."""
    fallback = stock or "com.google.android.tvlauncher"
    commands = [f"pm enable {n}" for n in names]
    commands.append(f"pm enable {fallback}")
    commands.append(f"cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a "
                    f"android.intent.action.MAIN -c android.intent.category.HOME {fallback} | tail -n 1)")
    commands.extend(f"settings put global {key} 1.0" for key in SETTINGS)
    return commands
