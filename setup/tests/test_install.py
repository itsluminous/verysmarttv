"""The install against the fake TV: companions before the launcher, the stock launcher left alone until
VerySmartTV answers HOME, the rollback when Home does not move, the SDK refusal."""
import os
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fake_tv import FakeTv  # noqa: E402
from verysmarttv_setup import homeguard, install  # noqa: E402

STOCK = "com.google.android.tvlauncher"
STOCK_HOME = STOCK + "/.MainActivity"
OURS = homeguard.LAUNCHER
OURS_HOME = homeguard.HOME_ACTIVITY
PROPS = {"ro.product.model": "FakeTV", "ro.serialno": "S1", "ro.build.version.sdk": "30"}


def quiet(*_):
    pass


class InstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        os.environ["VERYSMARTTV_HOME"] = self.tmp.name
        self.addCleanup(self.tmp.cleanup)
        self.addCleanup(os.environ.pop, "VERYSMARTTV_HOME", None)
        folder = Path(self.tmp.name) / "apks"
        folder.mkdir()
        for app in install.APPS:
            (folder / f"verysmarttv-{app}-1.0.0.apk").write_bytes(b"apk" * 10)
        self.apks = install.find_apks(folder)

    def fresh_tv(self, **kw):
        base = dict(installed={STOCK, "com.android.tv.settings"}, claims=[STOCK_HOME, "com.android.tv.settings/.system.FallbackHome"],
                    home=STOCK_HOME, props=PROPS)
        base.update(kw)
        dev = FakeTv(**base)
        # `pm install -r` of the launcher makes it claim HOME, as the real one does.
        original = dev._answer

        def answer(command):
            if command.startswith("pm install -r") and "launcher" in command:
                dev.installed.add(OURS)
                if OURS_HOME not in dev.claims:
                    dev.claims.append(OURS_HOME)
            elif command.startswith("pm install -r"):
                dev.installed.add("com.itsluminous." + ("traktshelf" if "traktshelf" in command else "livescore"))
            return original(command)
        dev._answer = answer
        return dev

    def test_find_apks_accepts_release_and_debug_names(self):
        folder = Path(self.tmp.name) / "debug"
        folder.mkdir()
        for app in install.APPS:
            (folder / f"{app}-debug.apk").write_bytes(b"x")
        self.assertEqual(sorted(p.name for p in install.find_apks(folder).values()),
                         ["launcher-debug.apk", "livescore-debug.apk", "traktshelf-debug.apk"])
        (folder / "launcher-debug.apk").unlink()
        with self.assertRaises(install.InstallError):
            install.find_apks(folder)

    def test_companions_first_then_home_handover_with_the_stock_left_enabled(self):
        dev = self.fresh_tv()
        result = install.run(dev, self.apks, PROPS, log=quiet)
        installs = [c.split()[3].rstrip(";") for c in dev.commands if c.startswith("pm install -r")]
        self.assertEqual(installs, ["/data/local/tmp/verysmarttv-traktshelf.apk", "/data/local/tmp/verysmarttv-livescore.apk",
                                    "/data/local/tmp/verysmarttv-launcher.apk"])
        self.assertEqual(result["home"], OURS_HOME)
        self.assertEqual(result["stock"], STOCK)
        self.assertNotIn(STOCK, dev.disabled, "left enabled unless asked")
        self.assertFalse(any(c.startswith("pm grant") for c in dev.commands), "nothing is granted")
        start = next(i for i, c in enumerate(dev.commands) if c.startswith("am start"))
        sethome = next(i for i, c in enumerate(dev.commands) if c.startswith("cmd package set-home-activity"))
        self.assertLess(start, sethome, "the launcher is started before Home is handed over")

    def test_the_stock_launcher_is_disabled_only_after_ours_answers_home(self):
        dev = self.fresh_tv()
        install.run(dev, self.apks, PROPS, disable_stock=True, log=quiet)
        disable = next(i for i, c in enumerate(dev.commands) if c.startswith("pm disable-user"))
        sethome = next(i for i, c in enumerate(dev.commands) if c.startswith("cmd package set-home-activity"))
        self.assertLess(sethome, disable)
        self.assertIn(STOCK, dev.disabled)
        self.assertEqual(dev.home, OURS_HOME)

    def test_a_launcher_that_does_not_start_leaves_the_stock_alone(self):
        dev = self.fresh_tv(start_fails=True)
        with self.assertRaises(install.InstallError):
            install.run(dev, self.apks, PROPS, disable_stock=True, log=quiet)
        self.assertNotIn(STOCK, dev.disabled)
        self.assertEqual(dev.home, STOCK_HOME)

    def test_home_that_does_not_move_rolls_back(self):
        dev = self.fresh_tv()
        original = dev._answer
        dev._answer = lambda c: "Error\n" if c.startswith("cmd package set-home-activity") and OURS in c else original(c)
        with self.assertRaises(install.InstallError):
            install.run(dev, self.apks, PROPS, disable_stock=True, log=quiet)
        self.assertNotIn(STOCK, dev.disabled)
        self.assertEqual(dev.home, STOCK_HOME)

    def test_disabling_the_stock_that_takes_home_away_is_undone(self):
        dev = self.fresh_tv(fallback_wins=True)
        logged = []
        with self.assertRaises(install.InstallError):
            install.run(dev, self.apks, PROPS, disable_stock=True, log=logged.append)
        self.assertNotIn(STOCK, dev.disabled, "re-enabled")
        self.assertEqual(homeguard.package_of(dev.home), STOCK)

    def test_an_unknown_home_owner_needs_the_package_named(self):
        dev = self.fresh_tv(installed={"com.other.launcher"}, claims=["com.other.launcher/.Home"], home="com.other.launcher/.Home")
        with self.assertRaises(install.InstallError):
            install.run(dev, self.apks, PROPS, log=quiet)
        self.assertEqual([c for c in dev.commands if c.startswith("pm install")], [], "nothing was pushed")
        result = install.run(dev, self.apks, PROPS, stock_choice="none", log=quiet)
        self.assertIsNone(result["stock"])
        self.assertEqual(result["home"], OURS_HOME)

    def test_an_old_tv_is_refused_before_anything_is_pushed(self):
        dev = self.fresh_tv()
        with self.assertRaises(install.InstallError) as caught:
            install.run(dev, self.apks, dict(PROPS, **{"ro.build.version.sdk": "25"}), log=quiet)
        self.assertIn("INSTALL_FAILED_OLDER_SDK", str(caught.exception))
        self.assertEqual(dev.files, {})

    def guided(self, dev, *argv):
        """`setup setup <argv>` against the fake TV: the device and the APK folder are given, nothing is asked."""
        from verysmarttv_setup import cli
        saved = cli.open_device, cli.obtain_apks, cli.say
        cli.open_device = lambda args, wait_for_prompt=True: (dev, PROPS)
        cli.obtain_apks = lambda args, log=None: self.apks
        cli.say = quiet
        try:
            return cli.main(["setup", "--host", "127.0.0.1", "--theme", "skip", "--preset", "none", "--yes", *argv])
        finally:
            cli.open_device, cli.obtain_apks, cli.say = saved

    def test_the_guided_flow_with_yes_leaves_the_stock_launcher_enabled(self):
        """§16.2 step 4 defaults to No: `--yes` takes that default, and only --disable-stock disables the stock."""
        dev = self.fresh_tv()
        self.assertEqual(self.guided(dev), 0)
        self.assertEqual(dev.home, OURS_HOME)
        self.assertNotIn(STOCK, dev.disabled)
        dev = self.fresh_tv()
        self.assertEqual(self.guided(dev, "--disable-stock"), 0)
        self.assertEqual(dev.home, OURS_HOME)
        self.assertIn(STOCK, dev.disabled)
        disabled_at = next(i for i, c in enumerate(dev.commands) if c.startswith(f"pm disable-user --user 0 {STOCK}"))
        home_set_at = next(i for i, c in enumerate(dev.commands) if c.startswith("cmd package set-home-activity"))
        self.assertGreater(disabled_at, home_set_at, "the stock goes only after ours is Home")


if __name__ == "__main__":
    unittest.main()
