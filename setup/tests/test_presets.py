"""The presets: every list parses and follows the format, the index is what the builder writes, and the
matching ranks a Mi TV 4A, a TCL G08, a Sony BRAVIA and an unknown TV as DESIGN.md §17.3 says."""
import json
import re
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "setup"))
sys.path.insert(0, str(ROOT / "tools" / "dev"))

import build_presets_index  # noqa: E402
from verysmarttv_setup import lists, presets  # noqa: E402

LISTS = ROOT / "presets" / "lists"
# Addresses and e-mails never belong in a community list (the one placeholder, 192.168.1.50, is allowed).
IP_RE = re.compile(r"\b(?!192\.168\.1\.50\b)(?:10|172|192)\.\d{1,3}\.\d{1,3}\.\d{1,3}\b")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[a-z]{2,}")

MI_TV_4A = {"ro.product.model": "MiTV-MOOQ1", "ro.product.device": "tarzan", "ro.product.brand": "Xiaomi",
            "ro.product.manufacturer": "Xiaomi", "ro.product.board": "tarzan", "ro.build.product": "tarzan",
            "ro.build.version.sdk": "29", "ro.build.version.release": "10", "ro.build.version.incremental": "QTG3.200305.006.2591"}
TCL_G08 = {"ro.product.model": "Smart TV Pro", "ro.product.device": "G08", "ro.product.brand": "TCL",
           "ro.product.manufacturer": "TCL", "ro.product.board": "G08", "ro.build.product": "G08_4K_GB",
           "ro.build.version.sdk": "31"}
SONY = {"ro.product.model": "BRAVIA 4K GB ATV3", "ro.product.device": "BRAVIA_ATV3_4K", "ro.product.brand": "Sony",
        "ro.product.manufacturer": "Sony", "ro.product.board": "", "ro.build.product": "BRAVIA_ATV3_4K",
        "ro.build.version.sdk": "28"}
UNKNOWN = {"ro.product.model": "X9", "ro.product.device": "zz1", "ro.product.brand": "NoSuchBrand",
           "ro.product.manufacturer": "NoSuchBrand", "ro.build.version.sdk": "30"}


class FormatTest(unittest.TestCase):
    def folders(self):
        return sorted(p for p in LISTS.iterdir() if p.is_dir())

    def test_there_are_many_lists_each_with_both_files(self):
        folders = self.folders()
        self.assertGreater(len(folders), 400)
        for folder in folders:
            with self.subTest(folder=folder.name):
                self.assertTrue((folder / "debloat.txt").is_file())
                self.assertTrue((folder / "tv.json").is_file())
                self.assertEqual(sorted(p.name for p in folder.iterdir()), ["debloat.txt", "tv.json"],
                                 "only the list and its tv.json are carried")

    def test_list_format(self):
        problems = []
        for folder in self.folders():
            path = folder / "debloat.txt"
            text = path.read_text(encoding="utf-8")
            own = folder.name
            lines = text.split("\n")
            if not lines[0].startswith("#"):
                problems.append(f"{own}: first line should be a comment naming the TV")
            try:
                names = lists.parse(text, own)
            except lists.ListError as e:
                problems.append(str(e))
                continue
            seen = [body.split()[0] for body in (line.split("#", 1)[0].strip() for line in lines) if body]
            dupes = sorted({n for n in seen if seen.count(n) > 1})
            if dupes:
                problems.append(f"{own}: listed twice: {dupes}")
            core = lists.core_packages(names)
            if core:
                problems.append(f"{own}: core packages: {core}")
            kept = lists.commented_out_but_listed(lines)
            if kept:
                problems.append(f"{own}: commented out to keep, but still active: {kept}")
            if IP_RE.search(text) or EMAIL_RE.search(text):
                problems.append(f"{own}: holds an address or e-mail")
        self.assertEqual([], problems, "\n".join(problems))

    def test_tv_json_fields(self):
        for folder in self.folders():
            with self.subTest(folder=folder.name):
                meta = json.loads((folder / "tv.json").read_text(encoding="utf-8"))
                for key in ("tv", "os", "brand", "notes"):
                    self.assertIn(key, meta)
                    self.assertIsInstance(meta[key], str)
                self.assertFalse(IP_RE.search(json.dumps(meta)) or EMAIL_RE.search(json.dumps(meta)))

    def test_apply_and_undo_lines_name_this_tool(self):
        """The one edit the carry made (DESIGN.md §17.1): the Apply/Undo lines point at `setup`."""
        for folder in self.folders():
            text = (folder / "debloat.txt").read_text(encoding="utf-8")
            for line in text.splitlines():
                if re.match(r"^# (Apply|Undo): ", line):
                    with self.subTest(folder=folder.name, line=line):
                        self.assertNotRegex(line, r"tools/(disable|restore)\.sh examples/")
                        m = re.search(r"setup (debloat|restore) --preset (\S+)", line)
                        if m:
                            self.assertEqual(m.group(2), folder.name)


class IndexTest(unittest.TestCase):
    def test_index_is_fresh(self):
        current = (ROOT / "presets" / "index.json").read_text(encoding="utf-8")
        self.assertEqual(current, build_presets_index.render(build_presets_index.build(ROOT)),
                         "presets/index.json is stale: run tools/dev/build_presets_index.py")

    def test_check_flag(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools" / "dev" / "build_presets_index.py"), "--check"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_every_preset_has_tokens_and_an_sdk(self):
        for preset in presets.load_index(ROOT):
            with self.subTest(slug=preset.slug):
                self.assertTrue(preset.tokens)
                self.assertIsNotNone(preset.sdk)
                self.assertGreater(preset.packages, 0)
                self.assertTrue(preset.path.is_file())


class MatchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.presets = presets.load_index(ROOT)

    def test_mi_tv_4a_is_an_exact_match_for_its_own_list(self):
        matches = presets.match(MI_TV_4A, self.presets)
        self.assertEqual(matches[0].preset.slug, "xiaomi-mi-tv-4a-mooq1")
        self.assertEqual(matches[0].tier, "exact")
        self.assertEqual(presets.suggestion(matches).preset.slug, "xiaomi-mi-tv-4a-mooq1")
        self.assertIn("xiaomi-mi-tv-4a", [m.preset.slug for m in matches if m.tier == "exact"])
        self.assertTrue(all(m.preset.brand == "Xiaomi" or m.tier == "exact" for m in matches))

    def test_tcl_g08_and_sony_bravia_match_exactly(self):
        tcl = presets.match(TCL_G08, self.presets)
        self.assertEqual(tcl[0].tier, "exact")
        self.assertIn("tcl-55q6cx1", [m.preset.slug for m in tcl if m.tier == "exact"])
        sony = presets.match(SONY, self.presets)
        self.assertEqual(sony[0].tier, "exact")
        self.assertIn("sony-kd-43x8000g", [m.preset.slug for m in sony if m.tier == "exact"])

    def test_an_unknown_tv_has_no_match_and_no_suggestion(self):
        self.assertEqual(presets.match(UNKNOWN, self.presets), [])
        self.assertIsNone(presets.suggestion([]))

    def test_brand_tiers_are_candidates_not_suggestions(self):
        props = dict(UNKNOWN, **{"ro.product.brand": "Sony", "ro.product.manufacturer": "Sony", "ro.build.version.sdk": "28"})
        matches = presets.match(props, self.presets)
        self.assertTrue(matches)
        self.assertEqual({m.tier for m in matches} - {"brand+os", "brand"}, set())
        self.assertEqual(matches[0].tier, "brand+os")
        self.assertIsNone(presets.suggestion(matches))
        far = presets.match(dict(props, **{"ro.build.version.sdk": "21"}), self.presets)
        self.assertEqual(far[0].tier, "brand", "no Sony list within one SDK level of 21")

    def test_board_tier_needs_the_brand(self):
        same_board = dict(UNKNOWN, **{"ro.product.board": "G08", "ro.product.brand": "TCL", "ro.product.manufacturer": "TCL",
                                      "ro.build.version.sdk": "31"})
        self.assertEqual(presets.match(same_board, self.presets)[0].tier, "board")
        other_brand = dict(same_board, **{"ro.product.brand": "Acme", "ro.product.manufacturer": "Acme"})
        self.assertEqual(presets.match(other_brand, self.presets), [])

    def test_token_mining(self):
        tokens = presets.mine_tokens('# Sony BRAVIA KD-43X8000G (APAC: device BRAVIA_ATV3_4K, product BRAVIA 4K GB ATV3). '
                                     'Android TV 9 (API 28).', "sony-kd-43x8000g", "Sony BRAVIA KD-43X8000G")
        for token in ("kd-43x8000g", "bravia-atv3-4k", "sony-kd-43x8000g"):
            self.assertIn(token, tokens)
        self.assertNotIn("api-28", tokens)
        self.assertNotIn("tv", tokens)
        self.assertEqual(presets.sdk_from_text("# X, Android TV 9 (API 28)", "Android TV 9"), 28)
        self.assertEqual(presets.sdk_from_text("# X", "Google TV 12"), 31)
        self.assertEqual(presets.sdk_from_text("# X, Fire OS 7 (Android 9)", "Fire OS 7"), 28)


if __name__ == "__main__":
    unittest.main()
