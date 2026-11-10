"""Apply a debloat list to the TV: every safety check, the record, then `pm disable-user --user 0` one package
per shell call, in batches with a Home check and a screenshot between them. Nothing is ever uninstalled.

The checks, in order, each a package left enabled with its reason rather than a stop:
core packages (a refusal of the whole list, no override), setupwraith and the Wi-Fi network, the Home guard,
the software under the HDMI/AV inputs (TV_INPUT_HARDWARE), the apps that open the inputs and Settings.
"""
import re
import time

from . import homeguard, lists, probe, record
from .paths import shots_dir

INPUT_PERMISSION = "android.permission.TV_INPUT_HARDWARE"
REQUESTED_RE = re.compile(r"requested permissions:(.*?)(?:\n\s*(?:install|runtime) permissions:|\n\s*User \d+:|$)", re.S)
GRANTED_RE = re.compile(r"grantedPermissions:(.*?)(?:\n\s*(?:install|runtime) permissions:|\n\s*User \d+:|$)", re.S)
HUNG = "hung"


class Options:
    def __init__(self, force_home=False, force_setupwraith=False, allow_input_drivers=False, allow_handlers=False,
                 dry_run=False, screenshots=True, batch_size=lists.DEFAULT_BATCH):
        self.force_home = force_home
        self.force_setupwraith = force_setupwraith
        self.allow_input_drivers = allow_input_drivers
        self.allow_handlers = allow_handlers
        self.dry_run = dry_run
        self.screenshots = screenshots
        self.batch_size = batch_size


def asks_for_input_hardware(dev, name: str, sdk):
    """True/False from a dump that can be read; None when it shows neither permission block (Android 5.x
    prints neither, so the check cannot run); HUNG when the watchdog has switched dumps off."""
    dump = dev.shell(f"dumpsys package {name}", 60)
    block = REQUESTED_RE.search(dump)
    if block:
        return INPUT_PERMISSION in block.group(1)
    granted = GRANTED_RE.search(dump)
    if granted:
        return INPUT_PERMISSION in granted.group(1)
    if "Service Resolver Table:" in dump:
        return "android.media.tv.TvInputService" in dump
    if "hung system process" in dump:
        return HUNG
    if "Unable to find" in dump:
        return False
    if sdk is not None and sdk >= 23 and f"Package [{name}]" in dump:
        return False
    return None


def handler_apps(dev):
    """(apps that open the HDMI/AV inputs, apps that open Settings), by package."""
    q = "cmd package query-activities --brief"
    inputs = dev.shell(f"{q} -a android.intent.action.VIEW -t vnd.android.cursor.item/channel", 60)
    settings = dev.shell(f"{q} -a android.settings.SETTINGS", 60)
    names = lambda out: set(re.findall(r"^\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)/", out, re.M))
    return names(inputs), names(settings)


def safety_checks(dev, names: list, opts: Options, sdk, log=print):
    """The names a run leaves enabled, as (name, reason), after the Home guard: the input-driver and the
    inputs/Settings handler checks."""
    skipped = []
    if not opts.allow_input_drivers:
        for name in names:
            hw = asks_for_input_hardware(dev, name, sdk)
            if hw is HUNG:
                skipped.append((name, "the TV answers dumpsys with \"Service dumps disabled due to hung system "
                                      "process\", so the input-driver check cannot read it: reboot the TV and run "
                                      "again"))
            elif hw is None:
                skipped.append((name, "the TV's dumpsys shows neither permission block for it (Android 5.x prints "
                                      "neither), so it cannot be checked for the HDMI/AV inputs; it stays enabled "
                                      "(--allow-input-drivers once every input works after a reboot)"))
            elif hw:
                skipped.append((name, f"it asks for {INPUT_PERMISSION}, so it is the software under the HDMI/AV/tuner "
                                      "inputs: with it disabled those ports stay black, and only a full restore and "
                                      "a cold boot bring them back (--allow-input-drivers to disable it anyway)"))
    if not opts.allow_handlers:
        left = [n for n in names if n not in {k for k, _ in skipped}]
        if left:
            inputs, settings = handler_apps(dev)
            if not settings and not inputs:
                log("  could not check which packages open the TV's inputs and Settings (the query answered "
                    "nothing; this firmware may have no `cmd`): open an HDMI input and Settings afterwards")
            for name in left:
                if name in inputs:
                    skipped.append((name, "the TV opens its HDMI/AV inputs and channels with it; with it disabled "
                                          "the launcher's input tiles do nothing (--allow-handlers to disable it)"))
                elif name in settings:
                    skipped.append((name, "the TV opens Settings with it; with it disabled Settings does not open, "
                                          "and with it the way back to Developer options (--allow-handlers)"))
    return skipped


def plan(dev, list_path, props: dict, opts: Options, log=print):
    """Everything before the first change: the list, the refusals, the guards. Returns (text, todo, kept)."""
    try:
        with open(list_path, encoding="utf-8-sig", errors="replace") as f:
            text = f.read()
    except OSError as e:
        raise lists.ListError(f"could not read {list_path} ({e.strerror or e})") from e
    names = lists.parse(text, str(list_path))
    core = lists.core_packages(names)
    if core:
        raise lists.ListError(f"refusing {list_path}: it disables core Android or TV middleware packages, and "
                              f"without them the TV may not boot again: {', '.join(core)}. Take these lines out "
                              "(or comment them) and run again. Nothing was changed on the TV.")
    todo, kept = homeguard.guard_list(dev, names, opts.force_home, opts.force_setupwraith)
    if todo:
        kept += safety_checks(dev, todo, opts, probe.sdk_of(props), log)
        kept_names = {k for k, _ in kept}
        todo = [n for n in todo if n not in kept_names]
    return text, todo, kept


def still_enabled(dev, names: list):
    enabled = probe.package_names(dev.shell("pm list packages -e --user 0", 60))
    if not enabled:
        return None
    return [n for n in names if n in enabled]


def home_still_a_launcher(dev):
    home = probe.query_home(dev)
    return bool(home) and not homeguard.is_chooser(home) and not homeguard.not_a_launcher(home), home


def apply(dev, list_path, props: dict, opts: Options, log=print, screenshot_name=None) -> int:
    """Plan, record, apply in batches. Returns 0 when every package the plan held is disabled, 1 otherwise."""
    text, todo, kept = plan(dev, list_path, props, opts, log)
    for name, why in kept:
        log(f"keeping {name}: {why}")
    if not todo:
        log("nothing to disable: every listed package is kept by a check above")
        return 0
    groups = lists.batches(text, todo, opts.batch_size)
    if opts.dry_run:
        log(f"dry run: {len(todo)} packages in {len(groups)} batches; the commands that would be sent:")
        for i, group in enumerate(groups, 1):
            log(f"# batch {i}/{len(groups)}")
            for name in group:
                log(f"pm disable-user --user 0 {name}")
        return 0
    path = record.save(dev, list_path, props, log)
    log(f"undo any time: setup restore (the record is {path.name})")
    stem = screenshot_name or time.strftime("%Y%m%d-%H%M%S")
    for i, group in enumerate(groups, 1):
        log(f"batch {i}/{len(groups)}: {', '.join(group)}")
        for name in group:
            out = dev.shell(f"pm disable-user --user 0 {name}", 60).strip()
            if out and "new state" not in out.lower():
                log(f"  {name}: {out.splitlines()[0]}")
        ok, home = home_still_a_launcher(dev)
        if not ok:
            log(f"stopping: after batch {i} Home resolves to {home or 'nothing'}, which is no launcher. The "
                f"batches after it were not sent. Run `setup restore` to undo, or `setup install` to make "
                "VerySmartTV Home.")
            return 1
        if opts.screenshots:
            try:
                shot = shots_dir() / f"{stem}-batch{i}.png"
                dev.screenshot(shot)
                log(f"  Home is {home}; screenshot {shot}")
            except Exception as e:  # a capture that fails is not a reason to stop the list
                log(f"  Home is {home}; screenshot skipped ({e})")
        else:
            log(f"  Home is {home}")
    left = still_enabled(dev, todo)
    if left is None:
        log("could not read back which packages are enabled; compare `pm list packages -d --user 0` by hand")
        return 0
    if left:
        log(f"still enabled after this run, although the list disables them: {', '.join(left)}. The TV did not "
            "take pm disable-user for these; run the list once more after a reboot, and if every package stays "
            "enabled this firmware refuses pm disable-user.")
        return 1
    disabled = probe.package_names(dev.shell("pm list packages -d --user 0", 60))
    absent = len(todo) - len(left) - len([n for n in todo if n in disabled])
    log(f"done: {len([n for n in todo if n in disabled])} packages disabled for user 0, nothing uninstalled"
        + (f"; {absent} not on this TV" if absent > 0 else ""))
    secure = props.get("ro.adb.secure", "")
    if secure in ("0", ""):
        log("note: this TV reports ro.adb.secure=0 (or none), so port 5555 gives a shell to any device on your "
            "network without the \"Allow\" prompt; turn debugging off when you are done (setup adb-closed).")
    return 0
