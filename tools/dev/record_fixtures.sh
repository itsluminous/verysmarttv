#!/usr/bin/env bash
# Records the ESPN payloads the :livescore parser tests run against, and rebuilds the static catalogue the
# follow picker offers. Run from the repository root; needs curl and python3 (the catalogue step).
#
#   tools/dev/record_fixtures.sh                 # today's scoreboards into livescore/src/test/resources/fixtures/<sport>/
#   tools/dev/record_fixtures.sh 20261109        # a given day (cricket takes the date; the others are "today" at ESPN)
#   tools/dev/record_fixtures.sh --catalogue     # livescore/src/main/res/raw/espn_catalogue.json from the teams endpoints
#
# Event arrays are kept whole when a day has 3 events or fewer; otherwise the recording keeps the first 3
# events of every list (and the first 3 matches of a tennis grouping) so the fixtures stay small; the
# `_note` field at the end of such a file says how many were dropped. Nothing in
# these payloads is personal: they are the public scoreboards. Fixtures are re-recorded by hand when the
# feed changes shape; the tests then say which field moved (DESIGN.md §13.3).
set -euo pipefail
cd "$(dirname "$0")/../.."
FIX=livescore/src/test/resources/fixtures
DAY="${1:-$(date -u +%Y%m%d)}"
MAX_EVENTS=3

trim() { # $1 file: keep the first $MAX_EVENTS entries of every "events" array, and say so
  python3 - "$1" "$MAX_EVENTS" <<'PY'
import json, sys
path, keep = sys.argv[1], int(sys.argv[2])
d = json.load(open(path))
cut = 0
def walk(o):
    global cut
    if isinstance(o, dict):
        for k, v in o.items():
            # events[] everywhere; competitions[] only where it is a long match list (tennis), never the
            # 5 sessions of an F1 weekend or the one competition of a cricket or football match
            if k == "events" and isinstance(v, list) and len(v) > keep or k == "competitions" and isinstance(v, list) and len(v) > 6:
                cut += len(v) - keep; o[k] = v[:keep]
            walk(o[k])
    elif isinstance(o, list):
        for i in o: walk(i)
walk(d)
if cut:
    d["_note"] = f"Recorded by tools/dev/record_fixtures.sh; events[] (and tennis match lists) truncated to their first {keep} entries ({cut} dropped)."
json.dump(d, open(path, "w"), separators=(",", ":"), ensure_ascii=False)
PY
}

record() { # $1 sport dir, $2 name, $3 url
  mkdir -p "$FIX/$1"
  curl -fsS -H 'Accept: application/json' "$3" -o "$FIX/$1/$2.json"
  trim "$FIX/$1/$2.json"
  echo "$FIX/$1/$2.json $(wc -c < "$FIX/$1/$2.json") bytes"
}

if [ "${1:-}" = "--catalogue" ]; then
  OUT=livescore/src/main/res/raw/espn_catalogue.json
  python3 - "$OUT" <<'PY'
import json, sys, urllib.request
out = sys.argv[1]
football = [("ind.1", "Indian Super League", "ISL"), ("eng.1", "Premier League", "Premier League"),
            ("uefa.champions", "Champions League", "UCL"), ("esp.1", "La Liga", "La Liga"), ("ita.1", "Serie A", "Serie A"),
            ("ger.1", "Bundesliga", "Bundesliga"), ("fra.1", "Ligue 1", "Ligue 1"), ("uefa.europa", "Europa League", "UEL"),
            ("fifa.world", "FIFA World Cup", "World Cup")]
def teams(slug):
    url = f"https://site.api.espn.com/apis/site/v2/sports/soccer/{slug}/teams"
    d = json.load(urllib.request.urlopen(url, timeout=30))
    rows = []
    for t in d["sports"][0]["leagues"][0]["teams"]:
        t = t["team"]
        rows.append({"id": t["id"], "name": t["displayName"], "abbr": t.get("abbreviation") or t["displayName"][:3].upper()})
    return sorted(rows, key=lambda r: r["name"])
# Cricket: the scorepanel covers every league in one fetch, so a competition is an ESPN league id; the
# "international" entry is the pseudo-competition for every match between nations (Competition.INTERNATIONAL).
# Team ids are Cricinfo's, read from the scorepanel on 2026-11-09 and 2026-05-01.
nations = [("6", "India", "IND"), ("2", "Australia", "AUS"), ("1", "England", "ENG"), ("7", "Pakistan", "PAK"),
           ("3", "South Africa", "SA"), ("5", "New Zealand", "NZ"), ("8", "Sri Lanka", "SL"), ("4", "West Indies", "WI"),
           ("25", "Bangladesh", "BAN"), ("40", "Afghanistan", "AFG"), ("9", "Zimbabwe", "ZIM"), ("29", "Ireland", "IRE")]
cricket = [
    {"id": "international", "name": "International matches", "short": "Internationals",
     "teams": [{"id": i, "name": n, "abbr": a} for i, n, a in nations]},
    {"id": "8048", "name": "Indian Premier League", "short": "IPL", "teams": []},
    {"id": "8679", "name": "Pakistan Super League", "short": "PSL", "teams": []},
    {"id": "8623", "name": "Caribbean Premier League", "short": "CPL", "teams": []},
    {"id": "21266", "name": "Major League Cricket", "short": "MLC", "teams": []},
    {"id": "8053", "name": "Vitality Blast", "short": "Blast", "teams": []},
    {"id": "8634", "name": "ICC Women's T20 World Cup", "short": "Women's T20 WC", "teams": []},
]
cat = {
    "cricket": cricket,
    "football": [{"id": s, "name": n, "short": sh, "teams": teams(s)} for s, n, sh in football],
    "f1": [{"id": "f1", "name": "Formula 1", "short": "F1", "teams": []}],
    "tennis": [{"id": "atp", "name": "ATP Tour", "short": "ATP", "teams": []}, {"id": "wta", "name": "WTA Tour", "short": "WTA", "teams": []}],
}
json.dump(cat, open(out, "w"), indent=0, ensure_ascii=False, separators=(",", ":"))
print(out, sum(len(c["teams"]) for c in cat["football"]), "football teams")
PY
  exit 0
fi

record cricket "scorepanel-$DAY" "https://site.web.api.espn.com/apis/site/v2/sports/cricket/scorepanel?dates=$DAY"
record football "eng1-$DAY" "https://site.api.espn.com/apis/site/v2/sports/soccer/eng.1/scoreboard"
record f1 "f1-$DAY" "https://site.api.espn.com/apis/site/v2/sports/racing/f1/scoreboard"
record tennis "atp-$DAY" "https://site.api.espn.com/apis/site/v2/sports/tennis/atp/scoreboard"
