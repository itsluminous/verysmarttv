"""What the TV says about itself: the `getprop` values matching, records and the user-facing summary use."""
import hashlib
import re

PROPS = ("ro.product.model", "ro.product.brand", "ro.product.manufacturer", "ro.product.device",
         "ro.product.board", "ro.product.name", "ro.build.product", "ro.build.version.release",
         "ro.build.version.sdk", "ro.build.version.incremental", "ro.build.display.id",
         "ro.build.fingerprint", "ro.serialno", "ro.adb.secure", "service.adb.tcp.port")

HOME_QUERY = ("cmd package resolve-activity --brief -a android.intent.action.MAIN "
              "-c android.intent.category.HOME")
CLAIMS_QUERY = ("cmd package query-activities --brief -a android.intent.action.MAIN "
                "-c android.intent.category.HOME")
HOME_RE = re.compile(r"[A-Za-z_][\w.]*/[\w.$]+")
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")


def read_props(dev) -> dict:
    """Every PROPS value in one round trip, as a dict (missing ones are empty strings)."""
    script = "; ".join(f"echo {p}=$(getprop {p})" for p in PROPS)
    props = {p: "" for p in PROPS}
    for line in dev.shell(script, 60).splitlines():
        if "=" in line:
            key, _, value = line.partition("=")
            if key.strip() in props:
                props[key.strip()] = value.strip()
    return props


def sdk_of(props: dict):
    value = props.get("ro.build.version.sdk", "")
    return int(value) if value.isdigit() else None


def tv_id(props: dict) -> str:
    """The record key: sha256(serial + model)[:12], so a TV keeps its records across DHCP leases (ADR-028)."""
    raw = (props.get("ro.serialno", "") + props.get("ro.product.model", "")).encode()
    return hashlib.sha256(raw).hexdigest()[:12]


def describe(props: dict) -> str:
    model = props.get("ro.product.model") or "unknown model"
    brand = props.get("ro.product.brand") or props.get("ro.product.manufacturer") or ""
    release = props.get("ro.build.version.release") or "?"
    sdk = props.get("ro.build.version.sdk") or "?"
    build = props.get("ro.build.version.incremental") or props.get("ro.build.display.id") or ""
    head = f"{brand} {model}".strip()
    return f"{head}, Android {release} (API {sdk}), firmware {build}".rstrip(", firmware ")


def package_names(answer: str) -> set:
    return {line.split(":", 1)[1].strip() for line in answer.splitlines() if line.startswith("package:")}


def query_packages(dev):
    """(installed, disabled) for user 0: the user every command here acts on. Android 14 merges every
    user's answer without --user, so a second profile's state would otherwise leak into the record."""
    scope = " --user 0"
    installed = package_names(dev.shell("pm list packages --user 0"))
    if not installed:
        scope = ""
        installed = package_names(dev.shell("pm list packages"))
    if not installed:
        raise RuntimeError("the TV returned no packages; check the connection and try again")
    disabled = package_names(dev.shell(f"pm list packages -d{scope}"))
    return installed, disabled


def query_home(dev):
    """The component Home resolves to, or None when nothing (or nothing readable) answers HOME. Android's
    chooser (android/...ResolverActivity) does pass: callers tell it apart with homeguard.is_chooser."""
    answer = dev.shell(HOME_QUERY, 60).strip()
    candidate = answer.splitlines()[-1].strip() if answer else ""
    return candidate if HOME_RE.fullmatch(candidate) else None


def home_claims(dev) -> list:
    """Every HOME component the TV can start now, as package/activity, in the TV's order."""
    found = []
    for line in dev.shell(CLAIMS_QUERY, 60).splitlines():
        m = re.match(r"\s*([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)", line)
        if m and m.group(1) not in found:
            found.append(m.group(1))
    return found


def home_priority(dev, component):
    """The HOME intent filter's priority of `component` (`package/.Activity`), from `dumpsys package <pkg>`:
    the `mPriority=` line of the filter block that carries `Category: "android.intent.category.HOME"`, 0 when
    the block has none (Android prints the line only for a priority other than 0), the highest when the
    activity has several such filters, None when the dump names no HOME filter for it. A stock launcher with a
    priority above ours answers HOME whatever default `set-home-activity` sets (PatchWall on the Mi TV 4A), which
    is what `install --disable-stock` disables first."""
    if not component or "/" not in component:
        return None
    package, activity = component.split("/", 1)
    if activity.startswith("."):
        activity = package + activity
    out = dev.shell(f"dumpsys package {package}", 120)
    best = None
    block = None
    indent = 0

    def flush(current):
        nonlocal best
        if current is not None and current["home"]:
            best = current["priority"] if best is None else max(best, current["priority"])

    for line in out.splitlines():
        head = re.match(r"^(\s*)[0-9a-f]+ (\S+) filter [0-9a-f]+", line)
        if head:
            flush(block)
            name = head.group(2)
            if name.startswith("."):
                name = package + name
            short = name.replace(package + "/" + package + ".", package + "/.")
            mine = name in (component, package + "/" + activity) or short == component
            block = {"home": False, "priority": 0} if mine else None
            indent = len(head.group(1))
            continue
        if block is None:
            continue
        if line.strip() and len(line) - len(line.lstrip()) <= indent:
            flush(block)
            block = None
            continue
        if 'Category: "android.intent.category.HOME"' in line:
            block["home"] = True
        m = re.search(r"mPriority=(-?\d+)", line)
        if m:
            block["priority"] = int(m.group(1))
    flush(block)
    return best
