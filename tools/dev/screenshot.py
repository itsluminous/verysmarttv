#!/usr/bin/env python3
"""Take a screenshot over adb, or clean one up, dropping whatever precedes the PNG signature.

Some TVs (the Mi TV 4A among them) write a few bytes of console noise before the image when asked for
`screencap -p`, and `adb shell` on Windows turns `\\n` into `\\r\\n`; both leave a file that image viewers and
`tools/screendiff`-style tools refuse. This keeps everything from the eight-byte PNG signature on.

    python3 tools/dev/screenshot.py shots/home.png                        # screencap on the one attached device
    ANDROID_SERIAL=emulator-5556 python3 tools/dev/screenshot.py /tmp/h.png
    python3 tools/dev/screenshot.py -s 192.168.1.50:5555 shots/home.png   # a named device
    python3 tools/dev/screenshot.py --fix raw.png shots/home.png          # clean a capture made some other way

Uses `adb exec-out screencap -p` (binary-safe) and honours ANDROID_SERIAL; `-s` names the device as adb does.
Exit 1 when no PNG signature is found in what came back.
"""
import argparse
import os
import subprocess
import sys
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def strip_prefix(data: bytes) -> bytes:
    """Everything from the PNG signature on; raises ValueError when there is none."""
    at = data.find(PNG_SIGNATURE)
    if at < 0:
        raise ValueError("no PNG signature in the capture")
    return data[at:]


def capture(serial) -> bytes:
    adb = os.environ.get("ADB", "adb")
    cmd = [adb] + (["-s", serial] if serial else []) + ["exec-out", "screencap", "-p"]
    return subprocess.run(cmd, check=True, capture_output=True).stdout


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("-s", "--serial", help="the adb device; default ANDROID_SERIAL or the one attached device")
    p.add_argument("--fix", type=Path, metavar="RAW", help="clean this file instead of capturing")
    p.add_argument("out", type=Path, help="the PNG to write")
    args = p.parse_args()
    try:
        data = args.fix.read_bytes() if args.fix else capture(args.serial or os.environ.get("ANDROID_SERIAL"))
        png = strip_prefix(data)
    except (ValueError, subprocess.CalledProcessError, OSError) as e:
        print(f"screenshot: {e}", file=sys.stderr)
        return 1
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(png)
    dropped = len(data) - len(png)
    print(f"wrote {args.out} ({len(png)} bytes" + (f", {dropped} bytes of prefix dropped)" if dropped else ")"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
