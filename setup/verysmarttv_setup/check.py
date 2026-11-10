"""What a firmware update undid, compared with the list that was applied; `--fix` applies it again.

An update re-enables listed packages, hands Home back to the stock launcher and adds new preloads. The TV is
compared with the list, not the record: the record holds the TV *before* cleanup, which is what restore needs,
while the target here is the list. `--fix` never writes a record of its own (a record of the TV after cleanup
would make undo leave the whole list disabled); when this TV and list have one, it adds what a second debloat
run would, so undo still means undo. The same safety checks run before anything is disabled.
"""
from . import debloat, homeguard, lists, probe, record


def inspect(dev, listed: list, expected: str = homeguard.LAUNCHER):
    installed, disabled = probe.query_packages(dev)
    home = probe.query_home(dev)
    back_on = [n for n in listed if n in installed and n not in disabled]
    gone = [n for n in listed if n not in installed]
    want = expected if expected in installed else None
    home_pkg = homeguard.package_of(home)
    home_drifted = (home is None or home_pkg in set(listed) or (want is not None and home_pkg != want))
    return installed, disabled, home, back_on, gone, home_drifted, want


def report(listed, home, back_on, gone, home_drifted, expected, log=print) -> int:
    if back_on:
        log(f"  back on ({len(back_on)}): {', '.join(back_on)}")
    if home_drifted:
        why = "" if not home else (", which this list disables" if homeguard.package_of(home) in set(listed)
                                   else f", not {expected}")
        log(f"  Home is {home or 'nothing (no component answers HOME)'}{why}")
    if gone:
        log(f"  not installed any more ({len(gone)}): {', '.join(gone)}")
    drift = len(back_on) + (1 if home_drifted else 0)
    if not drift:
        log(f"nothing drifted: {len(listed) - len(gone)} listed packages are still disabled and Home is {home}")
        return 0
    log(f"drift: {drift} of {len(listed)}; re-apply with `setup check --fix`")
    return 1


def launcher_to_use(dev, listed, preferred=None):
    """The HOME activity --fix switches to: the preferred package's, or the one launcher the list keeps."""
    claims = probe.home_claims(dev)
    if preferred:
        found = [c for c in claims if homeguard.package_of(c) == preferred and not homeguard.FALLBACK_HOME_RE.search(c)]
        return found[0] if found else None
    candidates = [c for c in claims if homeguard.package_of(c) not in listed and not homeguard.not_a_launcher(c)]
    own = [c for c in candidates if homeguard.package_of(c) == homeguard.LAUNCHER]
    return (own or candidates or [None])[0]


def fix(dev, listed, back_on, home_drifted, props, opts: debloat.Options, log=print) -> int:
    if home_drifted:
        target = launcher_to_use(dev, listed, homeguard.LAUNCHER if homeguard.LAUNCHER in
                                 {homeguard.package_of(c) for c in probe.home_claims(dev)} else None)
        if not target:
            log("Home: nothing to switch to: VerySmartTV is not installed, or nothing but the packages this list "
                "disables answers HOME. Run `setup install`, then check again; nothing was changed on the TV.")
            return 1
        dev.shell(f"cmd package set-home-activity --user 0 {target}", 60)
        home = probe.query_home(dev)
        if home != target:
            log(f"Home: asked for {target} but the TV still resolves {home or 'nothing'}; nothing was disabled.")
            return 1
        log(f"Home: set to {target}")
    if not back_on:
        return 0
    todo, kept = homeguard.guard_list(dev, back_on, opts.force_home, opts.force_setupwraith)
    kept += debloat.safety_checks(dev, todo, opts, probe.sdk_of(props), log)
    kept_names = {k for k, _ in kept}
    todo = [n for n in todo if n not in kept_names]
    for name, why in kept:
        log(f"leaving {name} on: {why}")
    for name in todo:
        dev.shell(f"pm disable-user --user 0 {name}", 60)
    _, disabled = probe.query_packages(dev)
    still_on = [n for n in todo if n not in disabled]
    if still_on:
        log(f"still enabled after --fix: {', '.join(still_on)} (the firmware may pin them)")
        return 1
    if todo:
        log(f"disabled {len(todo)} package(s) the update had turned back on")
    if kept:
        log(f"left {len(kept)} package(s) on that this list disables; the TV is not fully back to the list")
        return 1
    return 0


def run(dev, list_path, props: dict, wanted_fix=False, opts: debloat.Options = None, log=print) -> int:
    """0 in sync (or fixed), 1 drift or an incomplete fix. Raises for a list that cannot be used."""
    listed = lists.read_list(list_path)
    core = lists.core_packages(listed)
    if core:
        raise lists.ListError(f"refusing {list_path}: it disables core packages: {', '.join(core)}")
    installed, disabled, home, back_on, gone, home_drifted, expected = inspect(dev, listed)
    log(f"checking {list_path} ({len(listed)} packages)")
    code = report(listed, home, back_on, gone, home_drifted, expected, log)
    if code and wanted_fix:
        path = record.record_path(list_path, probe.tv_id(props))
        if path.exists():
            record.extend(path, record.load(path), listed, installed, disabled)
        code = fix(dev, listed, back_on, home_drifted, props, opts or debloat.Options(), log)
    return code
