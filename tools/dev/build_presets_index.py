#!/usr/bin/env python3
"""Generate `presets/index.json` from the lists in `presets/lists/` (DESIGN.md §17.2).

    tools/dev/build_presets_index.py            # write presets/index.json
    tools/dev/build_presets_index.py --check    # exit 1 when the committed index is stale (CI and the tests)

Each `presets/lists/<slug>/` has a `debloat.txt` (the first line is a comment naming the TV) and a `tv.json`
(`tv`, `os`, `brand`, `notes`). The entry carries the identifiers mined from them (`verysmarttv_setup.presets.mine_tokens`), the SDK level the header or the OS text gives, the package count, and the stock launcher the
header's Undo line named, which `setup restore` falls back on when a TV has no record.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "setup"))
from verysmarttv_setup import lists, presets  # noqa: E402

# Where the lists came from (repository, commit, fork): a data file beside the lists, so the carry's source is
# edited there and the upstream project's name stays out of the code (docs/BUILDING.md, Identifier gate).
SOURCE_FILE = "presets/source.json"
UNDO_RE = re.compile(r"^#\s*Undo:.*?--stock\s+([A-Za-z_][\w.]*)", re.M)


def build(root: Path) -> dict:
    folder = root / "presets" / "lists"
    entries = []
    for path in sorted(p for p in folder.iterdir() if p.is_dir()):
        text = (path / "debloat.txt").read_text(encoding="utf-8-sig")
        meta = json.loads((path / "tv.json").read_text(encoding="utf-8"))
        header_block = lists.header(text)
        first = text.splitlines()[0].lstrip("#").strip() if text else ""
        names = lists.parse(text, str(path / "debloat.txt"))
        undo = UNDO_RE.search(text)
        entry = {
            "slug": path.name,
            "tv": meta.get("tv", ""),
            "brand": meta.get("brand", ""),
            "os": meta.get("os", ""),
            "notes": meta.get("notes", ""),
            "header": first,
            "tokens": presets.mine_tokens(header_block, path.name, meta.get("tv", "")),
            "sdk": presets.sdk_from_text(header_block, meta.get("os", "")),
            "packages": len(names),
            "stock_launcher": undo.group(1) if undo else None,
            "path": f"presets/lists/{path.name}/debloat.txt",
            "source": f"examples/{path.name}/debloat.txt",
        }
        entries.append(entry)
    source = json.loads((root / SOURCE_FILE).read_text(encoding="utf-8"))
    return {"generated_from": source, "presets": entries}


def render(index: dict) -> str:
    return json.dumps(index, indent=1, ensure_ascii=False, sort_keys=True) + "\n"


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--check", action="store_true", help="compare with the committed index instead of writing")
    p.add_argument("--root", type=Path, default=ROOT)
    args = p.parse_args(argv)
    out = args.root / "presets" / "index.json"
    text = render(build(args.root))
    if args.check:
        current = out.read_text(encoding="utf-8") if out.exists() else ""
        if current != text:
            print(f"{out} is stale: run tools/dev/build_presets_index.py", file=sys.stderr)
            return 1
        print(f"{out} is up to date ({len(json.loads(text)['presets'])} presets)")
        return 0
    out.write_text(text, encoding="utf-8")
    print(f"wrote {out} ({len(json.loads(text)['presets'])} presets)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
