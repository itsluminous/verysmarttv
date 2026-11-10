"""Discovery parsing (an mDNS answer built by hand), the debugging hints, the list helpers, and the theme push
against the fake TV."""
import os
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fake_tv import FakeTv  # noqa: E402
from verysmarttv_setup import discover, lists, theme  # noqa: E402


def record(name: bytes, rtype: int, rdata: bytes) -> bytes:
    return name + struct.pack("!HHIH", rtype, 0x8001, 120, len(rdata)) + rdata


def mdns_answer() -> bytes:
    service = discover.encode_name("_adb-tls-connect._tcp.local")
    instance = discover.encode_name("adb-ABC123-xyz._adb-tls-connect._tcp.local")
    host = discover.encode_name("Android.local")
    header = struct.pack("!HHHHHH", 0, 0x8400, 0, 3, 0, 0)
    ptr = record(service, 12, instance)
    srv = record(instance, 33, struct.pack("!HHH", 0, 0, 41234) + host)
    a = record(host, 1, bytes([192, 168, 1, 50]))
    return header + ptr + srv + a


class DiscoverTest(unittest.TestCase):
    def test_parse_an_mdns_answer(self):
        found = discover.parse_response(mdns_answer())
        self.assertEqual(len(found), 1)
        self.assertEqual((found[0].host, found[0].port, found[0].name), ("192.168.1.50", 41234, "adb-ABC123-xyz"))
        self.assertEqual(found[0].source, "adb-tls-connect")
        self.assertEqual(found[0].address, "192.168.1.50:41234")

    def test_compressed_names_and_garbage(self):
        # A name pointer back to the question's service name (0xC00C) is what real answers use.
        service = discover.encode_name("_adb._tcp.local")
        header = struct.pack("!HHHHHH", 0, 0x8400, 1, 3, 0, 0)
        question = service + struct.pack("!HH", 12, 1)
        instance = b"\x05mitv1" + b"\xc0\x0c"
        ptr = record(b"\xc0\x0c", 12, instance)
        host = discover.encode_name("tv.local")
        srv = record(b"\x05mitv1\xc0\x0c", 33, struct.pack("!HHH", 0, 0, 5555) + host)
        a = record(host, 1, bytes([10, 0, 0, 7]))
        found = discover.parse_response(header + question + ptr + srv + a)
        self.assertEqual([(f.host, f.port, f.name, f.address) for f in found], [("10.0.0.7", 5555, "mitv1", "10.0.0.7")])
        self.assertEqual(discover.parse_response(b"\x00\x01garbage"), [])
        self.assertEqual(discover.parse_response(b""), [])

    def test_query_asks_for_both_services(self):
        query = discover.build_query()
        self.assertIn(discover.encode_name("_adb._tcp.local"), query)
        self.assertIn(discover.encode_name("_adb-tls-connect._tcp.local"), query)

    def test_hints_by_brand(self):
        self.assertIn("PatchWall", " ".join(discover.hints("Xiaomi")))
        self.assertIn("adb pair", " ".join(discover.hints("Google")))
        self.assertEqual(discover.hints("NoSuchBrand"), discover.GENERIC_STEPS)
        self.assertEqual(discover.hints(""), discover.GENERIC_STEPS)

    def test_last_tv_memory(self):
        with tempfile.TemporaryDirectory() as tmp:
            os.environ["VERYSMARTTV_HOME"] = tmp
            try:
                self.assertIsNone(discover.last_tv())
                discover.remember("192.168.1.50", {"ro.product.model": "X"})
                self.assertEqual(discover.last_tv()["address"], "192.168.1.50")
            finally:
                os.environ.pop("VERYSMARTTV_HOME", None)


class ListsTest(unittest.TestCase):
    TEXT = ("\ufeff# Fake TV (CODE1), Android TV 11\r\n# second header line\r\n# ---- Batch 1 ----\r\n"
            "com.a.one   # ads\r\ncom.a.two\r\n\r\n# com.a.kept  # kept on purpose\r\n# ---- Batch 2 ----\r\ncom.a.three\r\n")

    def test_parse_header_notes_batches(self):
        names = lists.parse(self.TEXT)
        self.assertEqual(names, ["com.a.one", "com.a.two", "com.a.three"])
        self.assertEqual(lists.header(self.TEXT), "Fake TV (CODE1), Android TV 11\nsecond header line")
        self.assertEqual(lists.notes(self.TEXT)["com.a.one"], "ads")
        self.assertEqual(lists.batches(self.TEXT, names), [["com.a.one", "com.a.two"], ["com.a.three"]])
        self.assertEqual(lists.batches(self.TEXT, ["com.a.three"]), [["com.a.three"]], "kept packages leave their batch")
        self.assertEqual(lists.batches("# h\n" + "".join(f"com.p.n{i}\n" for i in range(10)), [f"com.p.n{i}" for i in range(10)], 4),
                         [[f"com.p.n{i}" for i in r] for r in (range(0, 4), range(4, 8), range(8, 10))])

    def test_refusals(self):
        with self.assertRaises(lists.ListError):
            lists.parse("# h\ncom.a.b; rm -rf /\n")
        with self.assertRaises(lists.ListError):
            lists.parse("# h\n# com.a.b\n")
        with self.assertRaises(lists.ListError):
            lists.read_list("/no/such/list.txt")

    def test_commented_out_but_listed(self):
        self.assertEqual(lists.commented_out_but_listed(["# TV", "com.a.b", "# com.a.b  # keep"]), [(3, "com.a.b")])
        self.assertEqual(lists.commented_out_but_listed(["# TV", "# com.a.b", "com.a.b"]), [], "a header record is fine")
        self.assertEqual(lists.commented_out_but_listed(["# TV", "com.a.b", "# com.a.b also answers HOME"]), [])


class ThemeTest(unittest.TestCase):
    def test_push_writes_through_the_provider_and_restarts_home(self):
        dev = FakeTv(installed={theme.homeguard.LAUNCHER})
        logged = []
        theme.push(dev, "frosted", logged.append)
        self.assertEqual(set(dev.files), {f"{theme.TMP}-config.json", f"{theme.TMP}-wallpaper.jpg", f"{theme.TMP}-theme_revision"})
        writes = [c for c in dev.commands if c.startswith("content write")]
        self.assertEqual(len(writes), 3)
        self.assertTrue(any("am force-stop" in c for c in dev.commands))
        self.assertTrue(any(c.startswith("am start") for c in dev.commands))
        self.assertIn("  config.json: written through the provider", logged)

    def test_fallback_copy_on_firmware_without_content_write(self):
        dev = FakeTv(installed={theme.homeguard.LAUNCHER}, no_cmd=True)
        theme.push(dev, "default", lambda *_: None)
        copies = [c for c in dev.commands if c.startswith("mkdir -p")]
        self.assertEqual(len(copies), 2, "config.json and theme_revision copied; no wallpaper in default")
        self.assertTrue(any("content delete" in c and "wallpaper.jpg" in c for c in dev.commands))

    def test_unknown_theme(self):
        with self.assertRaises(Exception):
            theme.resolve("no-such-theme")
        self.assertIn("frosted", theme.available())


if __name__ == "__main__":
    unittest.main()
