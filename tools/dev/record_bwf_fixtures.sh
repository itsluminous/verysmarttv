#!/usr/bin/env bash
# Records BWF's Match Centre JSON for the :livescore BWF parser tests (ADR-049) through the real hidden WebView, on an
# emulator or TV with ADB and internet: the site sits behind a Cloudflare JavaScript challenge, so curl cannot. Run from
# the repository root with the debug APKs built; pass the serial as ANDROID_SERIAL when more than one device is attached.
#
#   tools/dev/record_bwf_fixtures.sh              # fixtures/bwf/current-live-<day>.json and live-matches-<tmtId>-<day>.json
#
# What it does: installs livescore's debug and androidTest APKs, runs BwfRecordTest with `-e bwf_record true` (the test
# is skipped without it), pulls what the page handed back from the app's files/bwf-record/, keeps the first 5 entries of
# every `results` array with a `_note` saying so, and prints the logcat lines that say whether the challenge was passed.
# Nothing personal is in these payloads: they are the public live scores. Never point it at a TV you may not use.
set -euo pipefail
cd "$(dirname "$0")/../.."
FIX=livescore/src/test/resources/fixtures/bwf
DAY="$(date -u +%Y%m%d)"
ADB="${ADB:-adb}"
PKG=com.itsluminous.livescore
MAX=5

[ -f livescore/build/outputs/apk/debug/livescore-debug.apk ] || { echo "build first: ./gradlew :livescore:assembleDebug :livescore:assembleDebugAndroidTest" >&2; exit 2; }
"$ADB" install -r -t livescore/build/outputs/apk/debug/livescore-debug.apk >/dev/null
"$ADB" install -r -t livescore/build/outputs/apk/androidTest/debug/livescore-debug-androidTest.apk >/dev/null
"$ADB" logcat -c
"$ADB" shell am instrument -w -e bwf_record true -e class $PKG.BwfRecordTest $PKG.test/androidx.test.runner.AndroidJUnitRunner | tail -3
"$ADB" logcat -d -s LiveScore.BwfRecord LiveScore.BwfWeb LiveScore.Bwf LiveScore.BwfProc | cut -c1-400

mkdir -p "$FIX"
for f in $("$ADB" shell run-as $PKG ls files/bwf-record 2>/dev/null); do
  case "$f" in
    current-live.json) out="$FIX/current-live-$DAY.json" ;;
    live-matches-*.json) out="$FIX/${f%.json}-$DAY.json" ;;
    failed.txt) echo "the visit failed: $("$ADB" shell run-as $PKG cat files/bwf-record/failed.txt)" >&2; continue ;;
    *) continue ;;
  esac
  "$ADB" shell run-as $PKG cat "files/bwf-record/$f" > "$out"
  python3 - "$out" "$MAX" "$DAY" <<'PY'
import json, sys
path, keep, day = sys.argv[1], int(sys.argv[2]), sys.argv[3]
d = json.load(open(path))
cut = 0
if isinstance(d, dict) and isinstance(d.get("results"), list) and len(d["results"]) > keep:
    cut = len(d["results"]) - keep
    d["results"] = d["results"][:keep]
if isinstance(d, dict):
    d["_note"] = f"Recorded {day[:4]}-{day[4:6]}-{day[6:]} through the hidden WebView (tools/dev/record_bwf_fixtures.sh, BwfRecordTest, ADR-049)" + (f"; results[] truncated to its first {keep} entries ({cut} dropped)." if cut else ".")
json.dump(d, open(path, "w"), separators=(",", ":"), ensure_ascii=False)
PY
  echo "$out $(wc -c < "$out") bytes"
done
