#!/usr/bin/env python3
"""Push a theme folder to the launcher on a TV or emulator with plain adb, and restart Home.

A Phase 1 stand-in for the setup tool's `theme` command (DESIGN.md §16; Phase 5 replaces this file). Mirrors the
fork's tools/theme.sh: each file goes through the launcher's theme provider,
content://com.itsluminous.verysmarttv.theme/<name>, which the adb shell may write (it holds WRITE_SECURE_SETTINGS),
with a plain copy into Android/data/<package>/files/ as the fallback on firmware whose `content write` is missing
(Android 8 and 9). Then theme_revision is bumped so Config drops the on-TV choices, and Home is restarted.

    tools/dev/push_theme.py themes/frosted              # push config.json (+ wallpaper.jpg) and restart Home
    tools/dev/push_theme.py --reset                     # remove the theme: back to config.xml
    tools/dev/push_theme.py -s emulator-5554 themes/default

Only config.json, wallpaper.jpg and theme_revision are pushed (ThemeFiles accepts nothing else). Nothing here
touches the network, and nothing is printed but adb's own output and the steps taken.
"""
import argparse
import os
import subprocess
import sys
import time

PACKAGE = "com.itsluminous.verysmarttv"
AUTHORITY = PACKAGE + ".theme"
FILES = ("config.json", "wallpaper.jpg")
TMP = "/data/local/tmp/verysmarttv-theme"
EXTERNAL = f"/sdcard/Android/data/{PACKAGE}/files"


def adb(serial, *args, check=True):
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    result = subprocess.run(cmd, capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit(f"{' '.join(cmd)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result.stdout


def shell(serial, command):
    return adb(serial, "shell", command)


def put(serial, name, local):
    """Writes `local` as the theme file `name`: through the provider, else as a plain copy into Android/data."""
    staged = f"{TMP}-{name}"
    adb(serial, "push", local, staged)
    out = shell(serial, f"content write --uri content://{AUTHORITY}/{name} < {staged} 2>&1 || echo PROVIDER_FAILED")
    if "Unsupported operation" in out or "Error while accessing provider" in out or "PROVIDER_FAILED" in out:
        shell(serial, f"mkdir -p {EXTERNAL} 2>/dev/null; cp {staged} {EXTERNAL}/{name}")
        how = "copied into Android/data"
    else:
        how = "written through the provider"
    shell(serial, f"rm -f {staged}")
    print(f"  {name}: {how}")


def delete(serial, name):
    shell(serial, f"rm -f {EXTERNAL}/{name} 2>/dev/null; content delete --uri content://{AUTHORITY}/{name} >/dev/null 2>&1; true")


def restart_home(serial):
    shell(serial, f"am force-stop {PACKAGE}")
    time.sleep(1)
    shell(serial, "input keyevent KEYCODE_HOME")


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("theme", nargs="?", help="theme folder with a config.json (and optionally wallpaper.jpg)")
    p.add_argument("--reset", action="store_true", help="remove the theme from the TV instead of pushing one")
    p.add_argument("-s", "--serial", help="adb device serial, when more than one device is attached")
    args = p.parse_args(argv)
    if bool(args.theme) == args.reset:
        p.error("give a theme folder, or --reset")

    revision = str(int(time.time()))
    if args.reset:
        for name in FILES:
            delete(args.serial, name)
        print("theme removed")
    else:
        folder = args.theme
        config = os.path.join(folder, "config.json")
        if not os.path.isfile(config):
            sys.exit(f"{folder} has no config.json")
        print(f"pushing {folder} to {PACKAGE}")
        put(args.serial, "config.json", config)
        wallpaper = os.path.join(folder, "wallpaper.jpg")
        if os.path.isfile(wallpaper):
            put(args.serial, "wallpaper.jpg", wallpaper)
        else:
            delete(args.serial, "wallpaper.jpg")
    # The revision file is what Config compares: a new value clears the choices made on the TV (DESIGN.md §10).
    local = f"/tmp/verysmarttv-theme_revision-{revision}"
    with open(local, "w", encoding="ascii") as f:
        f.write(revision)
    try:
        put(args.serial, "theme_revision", local)
    finally:
        os.remove(local)
    restart_home(args.serial)
    print("Home restarted")


if __name__ == "__main__":
    main()
