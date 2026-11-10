"""Push a theme folder (`config.json`, optional `wallpaper.jpg`) to the launcher and restart Home.

Each file goes through the launcher's theme provider, `content://com.itsluminous.verysmarttv.theme/<name>`,
which the adb shell may write (it holds WRITE_SECURE_SETTINGS), with a plain copy into Android/data as the
fallback on firmware whose `content write` is missing (Android 8 and 9). `theme_revision` is then bumped, so
Config drops the choices made on the TV, and Home is restarted. The provider accepts nothing but those three
names.
"""
import tempfile
import time
from pathlib import Path

from . import homeguard, presets
from .adb import AdbError

AUTHORITY = homeguard.LAUNCHER + ".theme"
FILES = ("config.json", "wallpaper.jpg")
TMP = "/data/local/tmp/verysmarttv-theme"
EXTERNAL = f"/sdcard/Android/data/{homeguard.LAUNCHER}/files"


def themes_dir() -> Path:
    return presets.repo_root() / "themes"


def available() -> list:
    folder = themes_dir()
    return sorted(p.name for p in folder.iterdir() if p.is_dir() and (p / "config.json").is_file()) if folder.is_dir() else []


def resolve(name_or_path) -> Path:
    path = Path(name_or_path)
    if (path / "config.json").is_file():
        return path
    candidate = themes_dir() / str(name_or_path)
    if (candidate / "config.json").is_file():
        return candidate
    raise AdbError(f"{name_or_path} is not a theme folder (no config.json); themes shipped: {', '.join(available())}")


def put(dev, name: str, local: Path, log=print):
    staged = f"{TMP}-{name}"
    dev.push_file(local, staged)
    out = dev.shell(f"content write --uri content://{AUTHORITY}/{name} < {staged} 2>&1 || echo PROVIDER_FAILED", 60)
    if any(mark in out for mark in ("Unsupported operation", "Error while accessing provider", "PROVIDER_FAILED",
                                    "unknown command", "No such file")):
        dev.shell(f"mkdir -p {EXTERNAL} 2>/dev/null; cp {staged} {EXTERNAL}/{name}", 60)
        how = "copied into Android/data"
    else:
        how = "written through the provider"
    dev.shell(f"rm -f {staged}", 30)
    log(f"  {name}: {how}")


def delete(dev, name: str):
    dev.shell(f"rm -f {EXTERNAL}/{name} 2>/dev/null; content delete --uri content://{AUTHORITY}/{name} >/dev/null 2>&1; true", 60)


def restart_home(dev):
    from .install import restart_launcher, start_home
    restart_launcher(dev)
    time.sleep(1)
    start_home(dev)


def push(dev, theme, log=print):
    folder = resolve(theme)
    log(f"pushing {folder.name} to {homeguard.LAUNCHER}")
    put(dev, "config.json", folder / "config.json", log)
    wallpaper = folder / "wallpaper.jpg"
    if wallpaper.is_file():
        put(dev, "wallpaper.jpg", wallpaper, log)
    else:
        delete(dev, "wallpaper.jpg")
    bump_revision(dev, log)
    restart_home(dev)
    log("Home restarted")


def reset(dev, log=print):
    for name in FILES:
        delete(dev, name)
    bump_revision(dev, log)
    restart_home(dev)
    log("theme removed; Home restarted")


def bump_revision(dev, log=print):
    revision = str(int(time.time()))
    with tempfile.NamedTemporaryFile("w", delete=False, suffix="-theme_revision") as f:
        f.write(revision)
        local = Path(f.name)
    try:
        put(dev, "theme_revision", local, log)
    finally:
        local.unlink(missing_ok=True)
