"""The Home guard: the rules that keep a TV with something to start when Home is pressed.

Shared by `install` (which hands Home to the launcher and may disable the stock one afterwards), `debloat`
(a list may name the launcher that owns Home) and `check --fix`. The rules, in one place:

- Only one of KNOWN_STOCK_LAUNCHERS is ever disabled by the install, and only after VerySmartTV answers
  HOME. Anything else that owns Home (another third-party launcher, Android's chooser, the setup wizard) is
  reported and left alone unless the user names the package.
- A package that answers HOME without being a launcher does not count as a Home to fall back on: the setup
  wizards and settings apps (on Google TV setupwraith's RecoveryActivity takes Home and system_server
  crash-loops on it), a vendor FallbackHome placeholder, a factory-test app.
- A list that holds the Home owner is applied without it while no other launcher is installed and staying
  enabled, or while setupwraith stays enabled and answers HOME ahead of them.
- setupwraith itself is never disabled while the saved Wi-Fi network belongs to it (`dumpsys wifi` cname=),
  or while that cannot be read: disabling it deletes the network and takes the TV off Wi-Fi and ADB.
- Factory and test apps are never started, not even to look.
"""
import re

from . import probe

LAUNCHER = "com.itsluminous.verysmarttv"
HOME_ACTIVITY = f"{LAUNCHER}/.home.HomeActivity"
KNOWN_STOCK_LAUNCHERS = ("com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
                         "com.google.android.leanbacklauncher", "com.mitv.tvhome")
SETUPWRAITH = "com.google.android.tungsten.setupwraith"
NOT_LAUNCHERS = {SETUPWRAITH, "com.android.tv.settings", "com.android.settings", "com.android.provision"}
FALLBACK_HOME_RE = re.compile(r"/\S*FallbackHome$")
FACTORY_RE = re.compile(r"factorytest|factory_test|factorymode|hkcfac|factorymenu", re.I)


def package_of(component):
    return component.split("/", 1)[0] if component else None


def is_chooser(home) -> bool:
    """Android's chooser answers HOME when Home has no default (android/...ResolverActivity); "android" is
    no package name, which is the test."""
    return bool(home) and not probe.PACKAGE_RE.fullmatch(home.split("/", 1)[0])


def not_a_launcher(component) -> bool:
    """Answers HOME without being a launcher to hand Home to or fall back on."""
    if not component:
        return False
    package = package_of(component)
    return (package in NOT_LAUNCHERS or bool(FALLBACK_HOME_RE.search(component))
            or bool(FACTORY_RE.search(package)))


def is_factory(package: str) -> bool:
    return bool(FACTORY_RE.search(package or ""))


def launchers_in(claims) -> list:
    """The packages among HOME claimants that are launchers, each once, in order."""
    out = []
    for component in claims:
        package = package_of(component)
        if not not_a_launcher(component) and package not in out:
            out.append(package)
    return out


def setupwraith_owns_wifi(dev):
    """True when the saved Wi-Fi networks belong to setupwraith, False when they belong to something else,
    None when the TV could not say (then the caller keeps setupwraith on: a wrong guess is the TV off
    the network)."""
    owners = re.findall(r"cname=(\S*)", dev.shell("dumpsys wifi", 60))
    if not owners:
        return None
    return any("setupwraith" in owner for owner in owners)


def guard_list(dev, packages: list, force_home=False, force_setupwraith=False):
    """The packages of a debloat list that may be disabled now, and (package, reason) for each one kept.

    This is tools/disable.sh's Home guard and setupwraith rule in one function. `packages` is the list in
    order; what comes back is in the same order minus the kept ones.
    """
    kept = []
    todo = list(packages)

    def keep(package, why):
        if package in todo:
            todo.remove(package)
            kept.append((package, why))

    if SETUPWRAITH in todo and not force_setupwraith:
        owns = setupwraith_owns_wifi(dev)
        if owns is None:
            keep(SETUPWRAITH, "could not read which app owns the saved Wi-Fi networks (dumpsys wifi showed "
                              "none); on Ethernet, pass --force-setupwraith")
        elif owns:
            keep(SETUPWRAITH, "the saved Wi-Fi network belongs to it: disabling it would delete that network "
                              "and take the TV off Wi-Fi. Forget the network and join it again in Settings > "
                              "Network & Internet first, or pass --force-setupwraith on Ethernet")

    if force_home or not todo:
        return todo, kept

    home = probe.query_home(dev)
    chooser = is_chooser(home)
    home_pkg = None if (home is None or chooser) else package_of(home)
    if home_pkg is None:
        home_pkg = home_process(dev)
    claims = probe.home_claims(dev)
    launchers = launchers_in(claims)
    setup_claims = any(package_of(c) == SETUPWRAITH for c in claims) and SETUPWRAITH not in todo

    if home_pkg and home_pkg in todo:
        others = [p for p in launchers if p != home_pkg and p not in todo]
        if not others:
            keep(home_pkg, "it owns Home and no other launcher is installed and staying enabled; install "
                           "VerySmartTV and make it Home first (setup install), or pass --force-home")
        elif setup_claims:
            keep(home_pkg, f"it owns Home and {SETUPWRAITH} stays enabled and answers Home ahead of the other "
                           "launchers, so Home would land on its RecoveryActivity (a black screen that can "
                           "crash-loop system_server); make VerySmartTV Home first, or disable setupwraith in "
                           "the same run")
    elif not home_pkg:
        # No single owner (the chooser, or neither query named one): judge by the claimants, and when the
        # TV cannot list those either, keep every known stock launcher the list names.
        if launchers:
            listed = [p for p in launchers if p in todo]
            others = [p for p in launchers if p not in todo]
            if listed and not others:
                for p in listed:
                    keep(p, "Home has no default, and once the list is applied no other app could answer "
                            "Home; make VerySmartTV Home first, or pass --force-home")
        else:
            for p in KNOWN_STOCK_LAUNCHERS:
                if p in todo:
                    keep(p, "the TV could not say which apps answer Home, and this is a stock launcher; run "
                            "again once it answers, or pass --force-home")
    return todo, kept


def home_process(dev):
    """The package behind `mHomeProcess` in `dumpsys activity`, for firmware without `cmd` (Android < 7)."""
    out = dev.shell("dumpsys activity", 120)
    m = re.search(r"mHomeProcess:.*?[: ]([A-Za-z0-9_.]+)/[A-Za-z0-9_.]+", out)
    if m and probe.PACKAGE_RE.fullmatch(m.group(1)):
        return m.group(1)
    return None


def stock_launcher_for(dev, home, preferred=None):
    """Which stock launcher the install may disable afterwards: the Home owner when it is one of the four
    known ones, the one known stock among the claimants when Home is the chooser, None when Home is already
    ours, or a ValueError naming what owns Home otherwise (the user can pass the package)."""
    if preferred:
        if preferred == "none":
            return None
        if not probe.PACKAGE_RE.fullmatch(preferred):
            raise ValueError(f"not a package name: {preferred!r}")
        return preferred
    owner = package_of(home)
    if owner in KNOWN_STOCK_LAUNCHERS:
        return owner
    if owner == LAUNCHER:
        return None
    if is_chooser(home):
        found = [p for p in launchers_in(probe.home_claims(dev)) if p in KNOWN_STOCK_LAUNCHERS]
        if len(found) == 1:
            return found[0]
        raise ValueError(f"Home is Android's chooser and {len(found)} known stock launchers claim Home; pick "
                         f"a default on the TV first, or pass --stock-launcher <package> or none")
    if owner is None:
        raise ValueError("could not ask the TV which launcher owns Home; pass --stock-launcher <package> or none")
    if owner == SETUPWRAITH:
        raise ValueError(f"Home resolves to {home}, Google TV's setup wizard; see docs/SETUP.md, "
                         "Troubleshooting. Pass --stock-launcher <package> or none to go on")
    raise ValueError(f"Home resolves to {home}, which is not a stock launcher this tool knows (another "
                     "launcher is the default). Pass --stock-launcher <package> to disable it, or none to "
                     "leave it")
