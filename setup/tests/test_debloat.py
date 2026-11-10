"""The Home guard and the debloat flow against the fake TV: a list may not disable the app that owns Home
while nothing else can answer it; setupwraith stays while it owns the Wi-Fi; core packages are refused with
no override; the record is written before the first change and a retry keeps it; restore replays it."""
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fake_tv import FakeTv  # noqa: E402
from verysmarttv_setup import check, debloat, homeguard, lists, record, restore  # noqa: E402

STOCK = "com.google.android.tvlauncher"
STOCK_HOME = STOCK + "/.MainActivity"
OURS = homeguard.LAUNCHER
OURS_HOME = homeguard.HOME_ACTIVITY
SETUP = homeguard.SETUPWRAITH
PROPS = {"ro.product.model": "FakeTV", "ro.serialno": "ABC123", "ro.build.version.sdk": "30", "ro.adb.secure": "1"}


def quiet(*_):
    pass


def tv(**kw):
    base = dict(installed={STOCK, "com.vendor.ads", "com.vendor.promo", "com.vendor.telemetry", "com.android.tv.settings"},
                claims=[STOCK_HOME, "com.android.tv.settings/.system.FallbackHome"], home=STOCK_HOME, props=PROPS,
                wifi_owners=["android.uid.system"])
    base.update(kw)
    return FakeTv(**base)


class HomeDir(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        os.environ["VERYSMARTTV_HOME"] = self.tmp.name
        self.addCleanup(self.tmp.cleanup)
        self.addCleanup(os.environ.pop, "VERYSMARTTV_HOME", None)

    def write_list(self, *names, header="# Fake TV, Android TV 11, SDK 30\n", body=None):
        path = Path(self.tmp.name) / "list.txt"
        path.write_text(header + (body if body is not None else "".join(f"{n}\n" for n in names)), encoding="utf-8")
        return path


class GuardTest(HomeDir):
    def test_the_home_owner_is_kept_when_nothing_else_answers_home(self):
        dev = tv()
        todo, kept = homeguard.guard_list(dev, ["com.vendor.ads", STOCK])
        self.assertEqual(todo, ["com.vendor.ads"])
        self.assertEqual([k for k, _ in kept], [STOCK])
        self.assertIn("owns Home", kept[0][1])

    def test_a_fallback_home_and_a_factory_app_do_not_count_as_launchers(self):
        dev = tv(installed={STOCK, "com.duokan.factorytest", "com.android.tv.settings"},
                 claims=[STOCK_HOME, "com.duokan.factorytest/.FactoryLauncher", "com.android.tv.settings/.system.FallbackHome"])
        todo, kept = homeguard.guard_list(dev, [STOCK])
        self.assertEqual(todo, [])
        self.assertEqual(kept[0][0], STOCK)

    def test_the_home_owner_goes_once_our_launcher_answers_home(self):
        dev = tv(installed={STOCK, OURS, "com.vendor.ads"}, claims=[STOCK_HOME, OURS_HOME])
        todo, kept = homeguard.guard_list(dev, ["com.vendor.ads", STOCK])
        self.assertEqual(todo, ["com.vendor.ads", STOCK])
        self.assertEqual(kept, [])

    def test_setupwraith_answering_home_blocks_the_handover(self):
        dev = tv(installed={STOCK, OURS, SETUP}, claims=[SETUP + "/.RecoveryActivity", STOCK_HOME, OURS_HOME])
        todo, kept = homeguard.guard_list(dev, [STOCK])
        self.assertEqual(todo, [])
        self.assertIn("RecoveryActivity", kept[0][1])
        # Listed in the same run, with the Wi-Fi owned by the system: both go.
        todo, kept = homeguard.guard_list(dev, [STOCK, SETUP])
        self.assertEqual(todo, [STOCK, SETUP])

    def test_setupwraith_stays_while_it_owns_the_wifi_or_the_tv_cannot_say(self):
        dev = tv(installed={STOCK, SETUP, OURS}, claims=[STOCK_HOME, OURS_HOME], wifi_owners=["setupwraith:1000"])
        todo, kept = homeguard.guard_list(dev, [SETUP, "com.vendor.ads"])
        self.assertEqual(todo, ["com.vendor.ads"])
        self.assertIn("Wi-Fi", kept[0][1])
        dev = tv(installed={STOCK, SETUP, OURS}, claims=[STOCK_HOME, OURS_HOME], wifi_owners=None)
        todo, kept = homeguard.guard_list(dev, [SETUP])
        self.assertEqual(todo, [])
        todo, kept = homeguard.guard_list(dev, [SETUP], force_setupwraith=True)
        self.assertEqual(todo, [SETUP])

    def test_the_chooser_falls_back_on_the_claimants(self):
        dev = tv(installed={STOCK, "com.other.launcher"}, claims=[STOCK_HOME, "com.other.launcher/.Home"],
                 home="android/com.android.internal.app.ResolverActivity")
        todo, kept = homeguard.guard_list(dev, [STOCK, "com.other.launcher"])
        self.assertEqual(todo, [])
        self.assertEqual(len(kept), 2)
        todo, kept = homeguard.guard_list(dev, [STOCK])
        self.assertEqual(todo, [STOCK], "another launcher stays, so the listed one may go")

    def test_a_tv_without_cmd_keeps_the_known_stock_launchers(self):
        dev = tv(no_cmd=True, home=None)
        todo, kept = homeguard.guard_list(dev, [STOCK, "com.vendor.ads"])
        self.assertEqual(todo, ["com.vendor.ads"])
        self.assertIn("could not say", kept[0][1])
        # Older firmware names the Home process in dumpsys activity: the owner is still found.
        dev = tv(no_cmd=True, home=None, home_process=STOCK)
        todo, kept = homeguard.guard_list(dev, [STOCK])
        self.assertEqual(todo, [])

    def test_force_home_overrides(self):
        dev = tv()
        todo, kept = homeguard.guard_list(dev, [STOCK], force_home=True)
        self.assertEqual(todo, [STOCK])

    def test_stock_launcher_for_the_install(self):
        dev = tv()
        self.assertEqual(homeguard.stock_launcher_for(dev, STOCK_HOME), STOCK)
        self.assertIsNone(homeguard.stock_launcher_for(dev, OURS_HOME))
        self.assertEqual(homeguard.stock_launcher_for(dev, "x/y", "com.named.one"), "com.named.one")
        with self.assertRaises(ValueError):
            homeguard.stock_launcher_for(dev, "com.other.launcher/.Home")
        with self.assertRaises(ValueError):
            homeguard.stock_launcher_for(dev, SETUP + "/.RecoveryActivity")
        chooser = tv(home="android/com.android.internal.app.ResolverActivity")
        self.assertEqual(homeguard.stock_launcher_for(chooser, chooser.home), STOCK)


class CoreTest(HomeDir):
    def test_core_packages_are_refused_with_no_override(self):
        path = self.write_list("com.vendor.ads", "com.android.permissioncontroller")
        dev = tv()
        for opts in (debloat.Options(), debloat.Options(force_home=True, force_setupwraith=True, allow_handlers=True,
                                                         allow_input_drivers=True)):
            with self.assertRaises(lists.ListError) as caught:
                debloat.apply(dev, path, PROPS, opts, quiet)
            self.assertIn("com.android.permissioncontroller", str(caught.exception))
        self.assertEqual([c for c in dev.commands if "disable" in c], [], "nothing reached the TV")

    def test_the_core_pattern(self):
        for name in ("android", "android.ext.services", "com.android.sdksandbox", "com.android.location.fused",
                     "com.android.systemui", "com.google.android.permissioncontroller", "com.android.packageinstaller",
                     "com.google.android.overlay.modules.ext.services", "com.android.providers.settings",
                     "com.cltv.mal", "com.mediatek.tis", "com.android.tv.settings"):
            self.assertEqual(lists.core_packages([name]), [name], name)
        for name in ("com.android.providers.calendar", "com.android.providers.contacts", "com.vendor.ads",
                     "com.google.android.tvlauncher", "com.mediatek.wwtv.tvcenter"):
            self.assertEqual(lists.core_packages([name]), [], name)


class ApplyTest(HomeDir):
    def test_record_before_change_and_batches_with_screenshots(self):
        path = self.write_list(body="# ---- Batch 1\ncom.vendor.ads\ncom.vendor.promo\n# ---- Batch 2\ncom.vendor.telemetry\n")
        dev = tv(disabled={"com.vendor.promo"})
        code = debloat.apply(dev, path, PROPS, debloat.Options(), quiet, screenshot_name="t")
        self.assertEqual(code, 0)
        disables = [c for c in dev.commands if c.startswith("pm disable-user")]
        self.assertEqual(disables, ["pm disable-user --user 0 com.vendor.ads", "pm disable-user --user 0 com.vendor.promo",
                                    "pm disable-user --user 0 com.vendor.telemetry"])
        first_disable = dev.commands.index(disables[0])
        self.assertTrue(any("pm list packages" in c for c in dev.commands[:first_disable]), "the record read the TV first")
        records = list((Path(self.tmp.name) / "records").glob("recovery-*.json"))
        self.assertEqual(len(records), 1)
        rec = json.loads(records[0].read_text())
        self.assertEqual(rec["packages"]["com.vendor.promo"], "disabled", "already disabled before: left alone by undo")
        self.assertEqual(rec["packages"]["com.vendor.ads"], "enabled")
        self.assertEqual(rec["packages"][STOCK], "enabled", "the stock launcher's state is recorded, listed or not")
        self.assertEqual(rec["home"], STOCK_HOME)
        self.assertEqual(dev.commands.count("screencap"), 2, "one screenshot per batch")
        self.assertEqual(sorted(p.name for p in (Path(self.tmp.name) / "shots").iterdir()), ["t-batch1.png", "t-batch2.png"])

    def test_dry_run_sends_nothing_and_writes_no_record(self):
        path = self.write_list("com.vendor.ads")
        dev = tv()
        self.assertEqual(debloat.apply(dev, path, PROPS, debloat.Options(dry_run=True), quiet), 0)
        self.assertFalse(any("disable-user" in c for c in dev.commands))
        self.assertEqual(list((Path(self.tmp.name) / "records").glob("*.json")) if (Path(self.tmp.name) / "records").exists() else [], [])

    def test_a_retry_keeps_the_first_record(self):
        path = self.write_list("com.vendor.ads", "com.vendor.promo")
        dev = tv()
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        before = next((Path(self.tmp.name) / "records").glob("*.json")).read_text()
        # Second run: the TV now has both disabled; the record must still say they were enabled.
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        self.assertEqual(next((Path(self.tmp.name) / "records").glob("*.json")).read_text(), before)

    def test_an_edited_list_extends_the_record_without_overwriting(self):
        path = self.write_list("com.vendor.ads")
        dev = tv()
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        path.write_text("# h\ncom.vendor.ads\ncom.vendor.promo\n")
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        rec = json.loads(next((Path(self.tmp.name) / "records").glob("*.json")).read_text())
        self.assertEqual(rec["packages"]["com.vendor.ads"], "enabled")
        self.assertEqual(rec["packages"]["com.vendor.promo"], "enabled")

    def test_input_driver_and_handler_packages_are_kept(self):
        dump = "Package [com.vendor.hdmi] (x):\n    requested permissions:\n      android.permission.TV_INPUT_HARDWARE\n    install permissions:\n"
        path = self.write_list("com.vendor.hdmi", "com.vendor.livetv", "com.vendor.ads")
        dev = tv(installed={STOCK, OURS, "com.vendor.hdmi", "com.vendor.livetv", "com.vendor.ads"},
                 claims=[STOCK_HOME, OURS_HOME], dumps={"com.vendor.hdmi": dump}, input_handlers={"com.vendor.livetv"})
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        disables = [c.split()[-1] for c in dev.commands if c.startswith("pm disable-user")]
        self.assertEqual(disables, ["com.vendor.ads"])
        dev2 = tv(installed={STOCK, OURS, "com.vendor.hdmi", "com.vendor.livetv", "com.vendor.ads"},
                  claims=[STOCK_HOME, OURS_HOME], dumps={"com.vendor.hdmi": dump}, input_handlers={"com.vendor.livetv"})
        debloat.apply(dev2, path, PROPS, debloat.Options(screenshots=False, allow_input_drivers=True, allow_handlers=True), quiet)
        self.assertEqual(len([c for c in dev2.commands if c.startswith("pm disable-user")]), 3)

    def test_a_still_enabled_package_fails_the_run(self):
        path = self.write_list("com.vendor.ads", "com.vendor.promo")
        dev = tv(refuse_disable={"com.vendor.promo"})
        logged = []
        code = debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), logged.append)
        self.assertEqual(code, 1)
        self.assertTrue(any("still enabled" in line and "com.vendor.promo" in line for line in logged))

    def test_home_landing_on_no_launcher_stops_the_batches(self):
        # The guard lets the owner go (another launcher stays), but this firmware hands Home to its placeholder.
        path = self.write_list(body="# ---- Batch 1\n" + STOCK + "\n# ---- Batch 2\ncom.vendor.ads\n")
        dev = tv(installed={STOCK, OURS, "com.vendor.ads", "com.android.tv.settings"}, fallback_wins=True,
                 claims=[STOCK_HOME, OURS_HOME, "com.android.tv.settings/.system.FallbackHome"])
        logged = []
        code = debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), logged.append)
        self.assertEqual(code, 1)
        self.assertTrue(any("stopping" in line for line in logged))
        self.assertFalse(any(c.endswith("com.vendor.ads") for c in dev.commands if c.startswith("pm disable")),
                         "the batch after the failure was not sent")


class RestoreTest(HomeDir):
    def test_restore_replays_the_record(self):
        path = self.write_list("com.vendor.ads", "com.vendor.promo", STOCK)
        dev = tv(installed={STOCK, OURS, "com.vendor.ads", "com.vendor.promo"}, claims=[STOCK_HOME, OURS_HOME],
                 disabled={"com.vendor.promo"}, settings={"window_animation_scale": "0.5"})
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        self.assertEqual(dev.disabled, {"com.vendor.ads", "com.vendor.promo", STOCK})
        self.assertEqual(dev.home, OURS_HOME, "Home moved to the other launcher when the stock one went")
        dev.settings["window_animation_scale"] = "0"
        code = restore.run(dev, path, PROPS, log=quiet)
        self.assertEqual(code, 0)
        self.assertEqual(dev.disabled, {"com.vendor.promo"}, "what was disabled before stays disabled")
        self.assertEqual(dev.home, STOCK_HOME, "Home goes back to the recorded owner")
        self.assertEqual(dev.settings["window_animation_scale"], "0.5")

    def test_without_a_record_the_whole_list_is_enabled_with_a_warning(self):
        path = self.write_list("com.vendor.ads", "com.vendor.promo")
        dev = tv(disabled={"com.vendor.ads", "com.vendor.promo"})
        logged = []
        restore.run(dev, path, PROPS, log=logged.append)
        self.assertTrue(any("no recovery record" in line for line in logged))
        self.assertEqual(dev.disabled, set())

    def test_the_record_refuses_damage(self):
        path = Path(self.tmp.name) / "r.json"
        path.write_text('{"version": 1, "packages": {"com.a.b": "enabled", "rm -rf": "enabled"}}')
        with self.assertRaises(record.RecordError):
            record.load(path)
        path.write_text('{"version": 1, "packages": {"com.a.b": "enabled"}, "home": "android/com.android.internal.app.ResolverActivity"}')
        self.assertIsNone(record.load(path)["home"], "the chooser is read as no Home")


class CheckTest(HomeDir):
    def test_check_reports_drift_and_fix_repairs_it(self):
        path = self.write_list("com.vendor.ads", "com.vendor.promo")
        dev = tv(installed={STOCK, OURS, "com.vendor.ads", "com.vendor.promo"}, claims=[STOCK_HOME, OURS_HOME])
        debloat.apply(dev, path, PROPS, debloat.Options(screenshots=False), quiet)
        dev.home = OURS_HOME
        self.assertEqual(check.run(dev, path, PROPS, log=quiet), 0)
        # A firmware update: a package came back and Home went to the stock launcher.
        dev.disabled.discard("com.vendor.ads")
        dev.home = STOCK_HOME
        logged = []
        self.assertEqual(check.run(dev, path, PROPS, log=logged.append), 1)
        self.assertTrue(any("back on (1): com.vendor.ads" in line for line in logged))
        self.assertEqual(check.run(dev, path, PROPS, wanted_fix=True, opts=debloat.Options(), log=quiet), 0)
        self.assertEqual(dev.home, OURS_HOME)
        self.assertIn("com.vendor.ads", dev.disabled)
        # The fix never wrote a record of the cleaned TV.
        rec = json.loads(next((Path(self.tmp.name) / "records").glob("*.json")).read_text())
        self.assertEqual(rec["packages"]["com.vendor.ads"], "enabled")

    def test_fix_stops_when_no_launcher_can_take_home(self):
        path = self.write_list("com.vendor.ads")
        dev = tv(disabled=set(), home=None, claims=["com.android.tv.settings/.system.FallbackHome"])
        self.assertEqual(check.run(dev, path, PROPS, wanted_fix=True, log=quiet), 1)
        self.assertFalse(any("disable-user" in c for c in dev.commands))


if __name__ == "__main__":
    unittest.main()
