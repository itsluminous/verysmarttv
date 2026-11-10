"""Install the three APKs and make VerySmartTV Home, with the Home guard (DESIGN.md §16.1, `install`).

Order: the two companions first (they define the signature permissions the launcher asks for), then the
launcher. Nothing is granted. Home is handed over only after the launcher has started; the stock launcher is
left enabled unless asked, and then disabled only once VerySmartTV answers HOME, and only when it is one of
the four known stock launchers (or the package the user named). If Home does not land on VerySmartTV the
stock launcher is put back, and the commands that finish the rollback are also written to a file, in case
the connection is what broke.

One exception, only when the user asked for the stock to go (`--disable-stock`, or a typed yes to the question
the guided flow asks at that point): when `set-home-activity` leaves Home on that same recognised stock
launcher, as a stock with a HOME intent-filter priority above ours does (PatchWall on the Mi TV 4A; `probe`
prints the priority), the stock is disabled first, Home is handed over again and re-checked, and if
VerySmartTV still does not answer the stock is re-enabled and put back on Home as in every other rollback.
Without the request the behaviour is unchanged: the rollback, and a hint to run again with `--disable-stock`.
"""
import time
from pathlib import Path

from . import homeguard, paths, probe

APPS = ("traktshelf", "livescore", "launcher")
PACKAGES = {"launcher": homeguard.LAUNCHER, "traktshelf": "com.itsluminous.traktshelf",
            "livescore": "com.itsluminous.livescore"}
MIN_SDK = 26
REMOTE_DIR = "/data/local/tmp"


class InstallError(RuntimeError):
    pass


def find_apks(folder) -> dict:
    """{app: path} for the three APKs in a folder: release names (`verysmarttv-<app>-<v>.apk`) or the build
    outputs' `<app>-debug.apk` / `<app>-release.apk`."""
    folder = Path(folder)
    found = {}
    for app in APPS:
        candidates = sorted(folder.glob(f"verysmarttv-{app}-*.apk")) + sorted(folder.glob(f"{app}-*.apk")) \
            + sorted(folder.glob(f"{app}.apk"))
        if not candidates:
            raise InstallError(f"no APK for {app} in {folder} (expected verysmarttv-{app}-<version>.apk or "
                               f"{app}-debug.apk)")
        found[app] = candidates[0]
    return found


def check_sdk(props: dict):
    sdk = probe.sdk_of(props)
    if sdk is not None and sdk < MIN_SDK:
        raise InstallError(f"this TV runs API {sdk} and the apps need API {MIN_SDK} (Android 8.0) or newer; "
                           "pm install would refuse them with INSTALL_FAILED_OLDER_SDK. Nothing was pushed.")


def install_apk(dev, app: str, path: Path, log=print, progress=None):
    remote = f"{REMOTE_DIR}/verysmarttv-{app}.apk"
    log(f"  pushing {path.name} ({path.stat().st_size // 1024} KB)")
    dev.push_file(path, remote, progress)
    out = dev.shell(f"pm install -r {remote}; rm -f {remote}", 300)
    if "Success" not in out:
        if "INSTALL_FAILED_OLDER_SDK" in out:
            raise InstallError(f"pm install refused {path.name}: the TV's Android is older than the app's minSdk "
                               f"({MIN_SDK}). Nothing else was changed.")
        raise InstallError(f"pm install failed for {path.name}: {out.strip().splitlines()[-1] if out.strip() else 'no output'}")
    log(f"  installed {PACKAGES[app]}")


def restart_launcher(dev):
    """Force-stop the launcher (a replaced Home can come up half-drawn otherwise) and keep its accessibility
    helpers on the enabled list, which a force stop drops."""
    pkg = homeguard.LAUNCHER
    dev.shell("s=$(settings get secure enabled_accessibility_services); a=$(settings get secure accessibility_enabled); "
              f"am force-stop {pkg}; case \"$s\" in *{pkg}*) settings put secure enabled_accessibility_services ''; "
              "settings put secure enabled_accessibility_services \"$s\"; settings put secure accessibility_enabled "
              "\"$a\" ;; esac", 60)


def start_home(dev) -> str:
    return dev.shell(f"am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "
                     f"{homeguard.HOME_ACTIVITY}", 120)


def started_ok(out: str) -> bool:
    return "Status: ok" in out and not any(line.lower().startswith("error") for line in out.splitlines())


def rollback(dev, stock: str, log=print):
    """Put the stock launcher back on Home; write the finishing commands to a file as well."""
    if not stock:
        log("Home is not VerySmartTV and no stock launcher was disabled: nothing to roll back.")
        return
    dev.shell(f"pm enable --user 0 {stock}", 60)
    dev.shell(f"cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a "
              f"android.intent.action.MAIN -c android.intent.category.HOME {stock} | tail -n 1)", 60)
    back = probe.query_home(dev)
    if homeguard.package_of(back) == stock:
        log(f"{stock} owns Home again.")
        return
    note = paths.home_dir() / "handover-unfinished.txt"
    note.write_text(
        f"# VerySmartTV: the launcher handover did not finish. Over adb shell:\n"
        f"pm enable --user 0 {stock}\n"
        f"cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a android.intent.action.MAIN "
        f"-c android.intent.category.HOME {stock} | tail -n 1)\n", encoding="utf-8")
    log(f"recovery is incomplete; Home still resolves to {back or 'nothing'}. The commands that finish it are in {note}")


def set_home(dev):
    dev.shell(f"cmd package set-home-activity --user 0 {homeguard.HOME_ACTIVITY}", 60)
    return probe.query_home(dev)


def run(dev, apks: dict, props: dict, stock_choice: str = None, disable_stock=False, log=print, progress=None,
        ask_disable=None) -> dict:
    """Install, hand Home over, optionally disable the stock launcher. Returns {"home": ..., "stock": ...,
    "stock_disabled": bool}. `ask_disable(home)` is asked once, only when Home stays on the recognised stock after
    the handover and `disable_stock` is not set; True means disable it first. Raises InstallError (nothing further
    changed) or AdbError."""
    check_sdk(props)
    home_before = probe.query_home(dev)
    try:
        stock = homeguard.stock_launcher_for(dev, home_before, stock_choice)
    except ValueError as e:
        raise InstallError(str(e)) from e
    log(f"Home now: {home_before or 'nothing'}; stock launcher: {stock or 'none to disable'}")
    for app in APPS:
        install_apk(dev, app, Path(apks[app]), log, progress)
    restart_launcher(dev)
    out = start_home(dev)
    if not started_ok(out):
        raise InstallError(f"the launcher did not start ({out.strip().splitlines()[-1] if out.strip() else 'no output'}); "
                           f"{stock or 'the stock launcher'} was left as it was.")
    dev.shell(f"cmd package compile -m speed -f {homeguard.LAUNCHER}", 300)
    home = set_home(dev)
    disabled = False
    if homeguard.package_of(home) != homeguard.LAUNCHER:
        log(f"Home resolves to {home or 'nothing'}, not to VerySmartTV.")
        stock_holds = stock is not None and homeguard.package_of(home) == stock
        wanted = stock_holds and (disable_stock or (ask_disable is not None and bool(ask_disable(home))))
        if wanted:
            # The stock answers HOME ahead of ours whatever the default says (a HOME filter priority above 0,
            # as PatchWall's on the Mi TV 4A): with the user's say-so, disable it first and hand Home over again.
            log(f"{stock} answers Home ahead of VerySmartTV: disabling it first, then handing Home over again.")
            dev.shell(f"pm disable-user --user 0 {stock}", 60)
            time.sleep(1)
            home = set_home(dev)
            if homeguard.package_of(home) == homeguard.LAUNCHER:
                disabled = True
            else:
                log(f"with {stock} disabled Home resolves to {home or 'nothing'}, still not VerySmartTV: putting it back.")
                rollback(dev, stock, log)
                raise InstallError(f"Home did not move to VerySmartTV even with {stock} disabled; it was re-enabled and "
                                   "put back on Home.")
        else:
            rollback(dev, stock, log)
            if home and homeguard.package_of(home) == homeguard.SETUPWRAITH:
                log(f"{homeguard.SETUPWRAITH} claims Home on this TV: see docs/SETUP.md, Troubleshooting.")
            hint = (f" {stock} answers Home ahead of VerySmartTV (a HOME priority above ours; `setup probe` shows it); "
                    "run again with --disable-stock to disable it first." if stock_holds else "")
            raise InstallError("Home did not move to VerySmartTV; the stock launcher was put back." + hint)
    log(f"Home = {home}")
    if disabled:
        log(f"{stock} disabled for user 0 (setup restore, or `pm enable {stock}`, brings it back)")
    elif disable_stock and stock:
        dev.shell(f"pm disable-user --user 0 {stock}", 60)
        time.sleep(1)
        home = probe.query_home(dev)
        if homeguard.package_of(home) != homeguard.LAUNCHER:
            log(f"after disabling {stock}, Home resolves to {home or 'nothing'}: putting it back.")
            rollback(dev, stock, log)
            raise InstallError(f"disabling {stock} moved Home away from VerySmartTV; it was re-enabled.")
        disabled = True
        log(f"{stock} disabled for user 0 (setup restore, or `pm enable {stock}`, brings it back)")
    elif stock:
        log(f"{stock} left enabled (disable it later with: setup install --disable-stock)")
    return {"home": home, "stock": stock, "stock_disabled": disabled}
