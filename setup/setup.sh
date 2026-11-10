#!/usr/bin/env bash
# VerySmartTV setup for macOS and Linux: finds a Python 3.9+, makes a venv under ~/.verysmarttv/venv with the one
# package the tool needs (setup/requirements.txt), and runs the Python package with your arguments.
#
#   setup/setup.sh                                  # the guided flow: find the TV, install, theme, clean up
#   setup/setup.sh --host 192.168.1.50              # same, for a TV you know the address of
#   setup/setup.sh install --apk-dir dist/          # any subcommand: install, theme, debloat, restore, check ...
#   setup/setup.sh --help
#
# Nothing here needs sudo or Homebrew. The venv lives outside the repository so a `git pull` never touches it;
# delete ~/.verysmarttv/venv to start over.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
VENV="${VERYSMARTTV_HOME:-$HOME/.verysmarttv}/venv"

find_python() {
  # The first python3 that is 3.9 or newer: on PATH, Homebrew's, Xcode's, python.org's, the Linux distro's.
  local candidate
  for candidate in python3 python3.13 python3.12 python3.11 python3.10 python3.9 \
      /opt/homebrew/bin/python3 /usr/local/bin/python3 /usr/bin/python3 \
      /Library/Frameworks/Python.framework/Versions/Current/bin/python3; do
    command -v "$candidate" >/dev/null 2>&1 || [ -x "$candidate" ] || continue
    if "$candidate" -c 'import sys; sys.exit(0 if sys.version_info >= (3, 9) else 1)' 2>/dev/null; then
      command -v "$candidate" 2>/dev/null || printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

if ! PY="$(find_python)"; then
  echo "VerySmartTV setup needs Python 3.9 or newer and found none." >&2
  echo "macOS: run 'xcode-select --install' (Apple's Python 3), or 'brew install python', or install it from" >&2
  echo "https://www.python.org/downloads/ ; Linux: your package manager's python3 (and python3-venv on Debian/Ubuntu)." >&2
  exit 2
fi

if [ ! -x "$VENV/bin/python" ]; then
  echo "creating $VENV with $("$PY" --version 2>&1) ..."
  mkdir -p "$(dirname "$VENV")"
  "$PY" -m venv "$VENV" || {
    echo "could not create a venv. On Debian/Ubuntu: sudo apt install python3-venv, then run this again." >&2
    exit 2
  }
fi
# Install (or upgrade) the requirement only when the file changed since the last time: the stamp is its hash.
STAMP="$VENV/.requirements-stamp"
HASH="$(cksum "$HERE/requirements.txt" | cut -d' ' -f1-2)"
if [ ! -f "$STAMP" ] || [ "$(cat "$STAMP")" != "$HASH" ]; then
  "$VENV/bin/python" -m pip install --quiet --disable-pip-version-check -r "$HERE/requirements.txt"
  printf '%s' "$HASH" > "$STAMP"
fi

# No subcommand given (or only options such as --host): run the guided flow.
case "${1:-}" in
  setup|discover|probe|install|theme|debloat|restore|check|screenshot|adb-closed|-h|--help|--version) ;;
  *) set -- setup "$@" ;;
esac
# The package is found through PYTHONPATH, not by changing directory: `--apk-dir dist/` and `--list my.txt` are
# relative to where the user typed them.
PYTHONPATH="$HERE${PYTHONPATH:+:$PYTHONPATH}" exec "$VENV/bin/python" -m verysmarttv_setup "$@"
