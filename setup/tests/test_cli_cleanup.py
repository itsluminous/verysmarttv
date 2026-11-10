"""The clean-up step as the command line runs it, against the fake TV and a fake preset index: every path to a
list (the suggestion, "show" first, another slug typed at the prompt, `--preset` on the command line, `setup
debloat`) shows the list's header and asks the same yes/no before anything is sent; `--yes` answers it only
for a list named on the command line."""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fake_tv import FakeTv  # noqa: E402
from verysmarttv_setup import cli, homeguard, install, presets  # noqa: E402

STOCK = "com.google.android.tvlauncher"
STOCK_HOME = STOCK + "/.MainActivity"
OURS = homeguard.LAUNCHER
OURS_HOME = homeguard.HOME_ACTIVITY
PROPS = {"ro.product.model": "FakeTV", "ro.product.device": "fake1", "ro.product.brand": "Fake",
         "ro.product.manufacturer": "Fake", "ro.serialno": "S1", "ro.build.version.sdk": "30", "ro.adb.secure": "1"}
VENDOR = ["com.vendor.ads", "com.vendor.promo", "com.vendor.telemetry"]
HEADER = "# Fake TV (FakeTV, device fake1), Android TV 11, SDK 30"
OTHER_HEADER = "# Fake TV Lite (FakeTV-L), Android TV 11, SDK 30"


class CleanupCli(unittest.TestCase):
    """`cli.main` with the device, the APKs, the questions and the output all under the test's control."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        os.environ["VERYSMARTTV_HOME"] = str(Path(self.tmp.name) / "home")
        self.addCleanup(os.environ.pop, "VERYSMARTTV_HOME", None)
        self.root = Path(self.tmp.name) / "repo"
        self.write_preset("fake-tv", HEADER, VENDOR, body="# ---- Batch 1\ncom.vendor.ads\ncom.vendor.promo\n"
                                                           "# ---- Batch 2\ncom.vendor.telemetry   # usage upload\n")
        self.write_preset("fake-tv-lite", OTHER_HEADER, ["com.vendor.ads"])
        apks = Path(self.tmp.name) / "apks"
        apks.mkdir()
        for app in install.APPS:
            (apks / f"verysmarttv-{app}-1.0.0.apk").write_bytes(b"apk" * 10)
        self.apks = install.find_apks(apks)
        self.said, self.asked, self.answers = [], [], []
        saved = cli.open_device, cli.obtain_apks, cli.say, cli.ask, presets.load_index
        cli.open_device = lambda args, wait_for_prompt=True: (self.dev, PROPS)
        cli.obtain_apks = lambda args, log=None: self.apks
        cli.say = self.said.append
        cli.ask = self.scripted_ask
        presets.load_index = lambda root=None: self.index

        def restore():
            cli.open_device, cli.obtain_apks, cli.say, cli.ask, presets.load_index = saved
        self.addCleanup(restore)
        self.dev = FakeTv(installed={STOCK, OURS, "com.android.tv.settings", *VENDOR}, claims=[STOCK_HOME, OURS_HOME],
                          home=STOCK_HOME, props=PROPS, wifi_owners=["android.uid.system"])

    def write_preset(self, slug, header, names, body=None):
        folder = self.root / "presets" / "lists" / slug
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "debloat.txt").write_text(header + "\n" + (body or "".join(f"{n}\n" for n in names)), encoding="utf-8")

    @property
    def index(self):
        def entry(slug, header, tokens, count):
            return presets.Preset({"slug": slug, "tv": slug, "brand": "Fake", "header": header, "tokens": tokens,
                                   "sdk": 30, "packages": count, "path": f"presets/lists/{slug}/debloat.txt"}, self.root)
        return [entry("fake-tv", HEADER, ["faketv", "fake1"], 3), entry("fake-tv-lite", OTHER_HEADER, ["faketv-l"], 1)]

    def scripted_ask(self, prompt, default="", yes=False):
        self.asked.append(prompt)
        if yes:
            return default
        if "stock launcher" in prompt:
            return ""  # step 4's question, No by default: not this file's subject
        self.assertTrue(self.answers, f"the tool asked more than the test scripted: {prompt!r}")
        return self.answers.pop(0)

    def run_cli(self, *argv, answers=()):
        self.answers = list(answers)
        code = cli.main(list(argv))
        self.assertEqual(self.answers, [], "every scripted answer was used")
        return code

    def guided(self, *argv, answers=()):
        return self.run_cli("setup", "--host", "127.0.0.1", "--theme", "skip", *argv, answers=answers)

    def disabled(self):
        return [c.split()[-1] for c in self.dev.commands if c.startswith("pm disable-user")]

    def output(self):
        return "\n".join(self.said)

    def question(self):
        return [q for q in self.asked if "disable these" in q]


class DebloatCommandTest(CleanupCli):
    def test_no_leaves_the_tv_alone_after_the_header(self):
        self.assertEqual(self.run_cli("debloat", "--host", "127.0.0.1", "--preset", "fake-tv", answers=["n"]), 0)
        self.assertEqual(self.disabled(), [])
        self.assertIn(HEADER[2:], self.output(), "the header is shown before the question")
        self.assertIn("com.vendor.telemetry   # usage upload", self.output(), "without --yes the whole list is shown")
        self.assertEqual(len(self.question()), 1)
        self.assertIn("nothing was changed on the TV", self.output())

    def test_yes_applies_in_the_lists_batches(self):
        self.assertEqual(self.run_cli("debloat", "--host", "127.0.0.1", "--preset", "fake-tv", "--no-screenshots", answers=["y"]), 0)
        self.assertEqual(self.disabled(), VENDOR)

    def test_the_yes_flag_answers_for_a_list_named_on_the_command_line(self):
        self.assertEqual(self.run_cli("debloat", "--host", "127.0.0.1", "--preset", "fake-tv", "--yes", "--no-screenshots"), 0)
        self.assertEqual(self.disabled(), VENDOR)
        self.assertEqual(self.question(), [], "nothing was asked")
        self.assertIn(HEADER[2:], self.output(), "the header is still printed")

    def test_dry_run_shows_the_commands_and_asks_nothing(self):
        self.assertEqual(self.run_cli("debloat", "--host", "127.0.0.1", "--preset", "fake-tv", "--dry-run"), 0)
        self.assertEqual(self.disabled(), [])
        self.assertEqual(self.asked, [])
        self.assertIn("pm disable-user --user 0 com.vendor.ads", self.output())


class GuidedFlowTest(CleanupCli):
    def test_apply_asks_the_same_yes_no_after_the_header(self):
        self.assertEqual(self.guided(answers=["apply", "n"]), 0)
        self.assertEqual(self.disabled(), [])
        self.assertIn("suggested preset: fake-tv (exact model match, 3 packages)", self.output())
        self.assertIn(HEADER[2:], self.output())
        self.assertEqual(len(self.question()), 1)
        self.assertEqual(self.guided(answers=["apply", "y"]), 0)
        self.assertEqual(self.disabled(), VENDOR)

    def test_show_prints_the_whole_list_then_asks_once(self):
        self.assertEqual(self.guided(answers=["show", "y"]), 0)
        self.assertIn("com.vendor.telemetry   # usage upload", self.output())
        self.assertEqual(len(self.question()), 1)
        self.assertEqual(self.disabled(), VENDOR)

    def test_another_slug_typed_at_the_prompt_gets_its_header_and_the_same_question(self):
        self.assertEqual(self.guided(answers=["fake-tv-lite", "n"]), 0)
        self.assertEqual(self.disabled(), [])
        self.assertIn(OTHER_HEADER[2:], self.output(), "the other list's header is shown")
        self.assertNotIn(HEADER[2:], self.output())
        self.assertEqual(len(self.question()), 1)
        self.assertIn("1 packages from", self.output())
        self.assertEqual(self.guided(answers=["fake-tv-lite", "y"]), 0)
        self.assertEqual(self.disabled(), ["com.vendor.ads"])

    def test_an_unknown_slug_is_reported_and_the_flow_goes_on(self):
        self.assertEqual(self.guided(answers=["no-such-list"]), 0)
        self.assertEqual(self.disabled(), [])
        self.assertIn("no preset named 'no-such-list'", self.output())
        self.assertIn("7  Companions", self.output(), "the steps after the clean-up still run")

    def test_the_yes_flag_skips_a_list_it_did_not_name_and_applies_one_it_did(self):
        self.assertEqual(self.guided("--yes"), 0)
        self.assertEqual(self.disabled(), [], "--yes takes the default, skip")
        self.assertEqual(self.guided("--yes", "--preset", "fake-tv-lite", "--no-screenshots"), 0)
        self.assertEqual(self.disabled(), ["com.vendor.ads"])
        self.assertIn(OTHER_HEADER[2:], self.output())

    def test_skip_and_preset_none(self):
        self.assertEqual(self.guided(answers=["skip"]), 0)
        self.assertEqual(self.disabled(), [])
        self.assertEqual(self.guided("--preset", "none"), 0)
        self.assertIn("skipped (--preset none)", self.output())


if __name__ == "__main__":
    unittest.main()
