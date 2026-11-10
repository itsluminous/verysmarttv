"""Put the TV back the way it was before `setup debloat`, from the recovery record: packages already disabled
then stay disabled, Home goes back to the component that owned it, the animation scales to their values."""
from . import homeguard, probe, record
from .lists import read_list


def find_records(props: dict) -> list:
    return record.records_for(probe.tv_id(props))


def run(dev, list_path, props: dict, stock: str = None, dry_run=False, log=print) -> int:
    """Replay the record for this TV and list (or, without one, enable the whole list). 0 when the recorded
    states are back, 1 when the TV left something enabled or Home elsewhere."""
    names = read_list(list_path)
    if stock and not probe.PACKAGE_RE.fullmatch(stock):
        raise ValueError(f"not a package name: {stock!r}")
    path = record.record_path(list_path, probe.tv_id(props))
    if path.exists():
        rec = record.load(path)
        log(f"restoring the state recorded on {rec.get('created')} ({path.name})")
        commands = record.restore_commands(rec, names, stock, log)
    else:
        log(f"no recovery record for this TV and list ({path.name}): enabling every listed package and resetting "
            "Home and the animation scales to their defaults. Packages that were disabled before cleanup are "
            "enabled too.")
        commands = record.no_record_commands(names, stock)
    if dry_run:
        for c in commands:
            log(c)
        return 0
    for c in commands:
        out = dev.shell(c, 60).strip()
        if out and not any(w in out for w in ("new state", "Success", "Deleted")):
            log(f"  {c.split()[0]} {c.split()[1] if len(c.split()) > 1 else ''}: {out.splitlines()[0]}")
    _, disabled = probe.query_packages(dev)
    wanted_on = [n for n in names if (path.exists() and record.load(path)["packages"].get(n) == "enabled")
                 or not path.exists()]
    left = [n for n in wanted_on if n in disabled]
    home = probe.query_home(dev)
    log(f"Home is now {home or 'nothing'}")
    if left:
        log(f"still disabled although the record holds them as enabled: {', '.join(left)}")
        return 1
    if home is None or homeguard.not_a_launcher(home):
        log("Home resolves to no launcher: pick one on the TV, or run `setup install` to make VerySmartTV Home")
        return 1
    log("restored")
    return 0
