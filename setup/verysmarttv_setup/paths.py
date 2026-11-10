"""Where the tool keeps its files on this computer: `~/.verysmarttv/`.

The ADB key, the recovery records, the downloaded releases, screenshots and the last TV's address all live
there, never in the repository. `VERYSMARTTV_HOME` moves the folder (the tests point it at a temp dir).
"""
import os
import sys
from pathlib import Path


def home_dir() -> Path:
    override = os.environ.get("VERYSMARTTV_HOME")
    return Path(override) if override else Path.home() / ".verysmarttv"


def ensure(path: Path, mode: int = 0o700) -> Path:
    """The folder, created with `mode` when missing."""
    path.mkdir(parents=True, exist_ok=True, mode=mode)
    return path


def restrict(path, mode=0o600):
    """Keep a private key, or its folder, to this account. Reported, never fatal: the key still works
    either way. Windows has no POSIX mode bits and ignores this."""
    try:
        os.chmod(path, mode)
    except OSError as e:
        print(f"warning: could not restrict permissions on {path}: {e}", file=sys.stderr)


def records_dir() -> Path:
    return ensure(home_dir() / "records")


def releases_dir() -> Path:
    return ensure(home_dir() / "releases")


def shots_dir() -> Path:
    return ensure(home_dir() / "shots")


def lists_dir() -> Path:
    return ensure(home_dir() / "lists")


def last_tv_file() -> Path:
    return ensure(home_dir()) / "last-tv.json"
