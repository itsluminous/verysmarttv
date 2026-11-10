"""The pure-Python ADB client against an in-process adbd: the handshake with the "Allow USB debugging?" offer,
a trusted key, shell output, sync push and pull, the base64 fallback, reboot, and the worded failures."""
import os
import socket
import stat
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from fake_adbd import FakeAdbd  # noqa: E402
from verysmarttv_setup.adb import (AdbDevice, AdbError, AdbRefused, address_problem, auth, connect_error,  # noqa: E402
                                   parse_address, strip_png_prefix)
from verysmarttv_setup.adb.protocol import AdbConnectionLost  # noqa: E402


class HomeDir(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        os.environ["VERYSMARTTV_HOME"] = self.tmp.name
        self.addCleanup(self.tmp.cleanup)
        self.addCleanup(os.environ.pop, "VERYSMARTTV_HOME", None)


class AuthTest(HomeDir):
    def test_own_key_is_created_private_and_reused(self):
        key = auth.own_key()
        path = Path(self.tmp.name) / "adbkey"
        self.assertTrue(path.exists())
        self.assertTrue((Path(self.tmp.name) / "adbkey.pub").exists())
        if os.name == "posix":
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(Path(self.tmp.name).stat().st_mode), 0o700)
        again = auth.own_key()
        self.assertEqual(key.public_payload(), again.public_payload())

    def test_public_payload_is_androids_struct(self):
        key = auth.AdbKey.generate("tester@host")
        payload = key.public_payload()
        self.assertTrue(payload.endswith(b" tester@host\0"))
        from fake_adbd import public_key_from_payload
        public = public_key_from_payload(payload)
        self.assertEqual(public.public_numbers().n, key.private_key.public_key().public_numbers().n)

    def test_signature_is_pkcs1_over_the_raw_token(self):
        key = auth.AdbKey.generate("t@h")
        token = os.urandom(20)
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.asymmetric import padding
        from cryptography.hazmat.primitives.asymmetric.utils import Prehashed
        key.private_key.public_key().verify(key.sign(token), token, padding.PKCS1v15(), Prehashed(hashes.SHA1()))


class HandshakeTest(HomeDir):
    def connect(self, server, **kw):
        return AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5, **kw).connect()

    def test_first_connection_offers_the_key_and_the_second_is_trusted(self):
        with FakeAdbd() as server:
            dev = self.connect(server)
            self.assertEqual(server.prompts, 1)
            self.assertIn("FakeTV", dev.banner)
            dev.close()
            dev = self.connect(server)
            self.assertEqual(server.prompts, 1, "a trusted key signs without a second prompt")
            dev.close()

    def test_a_refused_prompt_is_an_error_not_a_hang(self):
        with FakeAdbd(accept_prompt=False) as server:
            with self.assertRaises((AdbError, AdbConnectionLost, OSError)):
                self.connect(server)

    def test_the_stock_key_is_offered_after_ours(self):
        own = auth.own_key()
        stock = auth.AdbKey.generate("stock@host")
        with FakeAdbd(trusted=[stock.private_key.public_key()]) as server:
            dev = self.connect(server, keys=[own, stock])
            self.assertEqual(server.prompts, 0, "the stock key was accepted, so no prompt")
            dev.close()

    def test_stls_is_worded_as_a_wireless_debugging_port(self):
        with FakeAdbd(stls=True) as server:
            try:
                self.connect(server)
            except Exception as e:
                words = connect_error(e, "127.0.0.1", server.port)
            else:
                self.fail("connected through STLS")
        self.assertIn("Wireless debugging", words)

    def test_a_refused_port_is_worded(self):
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
        sock.close()
        try:
            AdbDevice("127.0.0.1", port, timeout=2).connect()
        except Exception as e:
            words = connect_error(e, "127.0.0.1", port)
        else:
            self.fail("connected to a closed port")
        # Windows can report a closed loopback port as a timeout rather than a refusal; both wordings say what to do.
        self.assertTrue("refused" in words or "did not finish" in words, words)
        self.assertIn("USB debugging", words)


class StreamsTest(HomeDir):
    def test_shell_returns_the_output(self):
        with FakeAdbd(shell=lambda c: f"[{c}]\r\nline2\n".encode()) as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                self.assertEqual(dev.shell("getprop x"), "[getprop x]\nline2\n")
                out, rc = dev.shell_rc("true")
                self.assertEqual(rc, None, "the fake does not run the marker, so no status comes back")
                self.assertEqual(server.opened[-1], "shell:true; echo __VST_RC__$?")

    def test_long_output_arrives_in_several_frames(self):
        big = b"x" * (150 * 1024)
        with FakeAdbd(shell=lambda c: big, max_data=4096) as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                self.assertEqual(dev.max_data, 4096)
                self.assertEqual(len(dev.shell("cat big")), len(big))

    def test_push_and_pull_roundtrip(self):
        data = os.urandom(200 * 1024 + 17)
        local = Path(self.tmp.name) / "in.bin"
        local.write_bytes(data)
        with FakeAdbd() as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                dev.push(local, "/data/local/tmp/x.bin")
                self.assertEqual(server.files["/data/local/tmp/x.bin"], data)
                back = Path(self.tmp.name) / "out.bin"
                dev.pull("/data/local/tmp/x.bin", back)
                self.assertEqual(back.read_bytes(), data)
                with self.assertRaises(AdbRefused):
                    dev.pull("/nowhere", Path(self.tmp.name) / "none.bin")
                self.assertFalse((Path(self.tmp.name) / "none.bin").exists(), "a failed pull writes no file")

    def test_a_sync_push_is_read_back_with_md5sum(self):
        """push_file checks the copy on the TV against the local file; a damaged copy is removed and named, and
        a shell without md5sum (no digest in the answer) is let through on the sync acknowledgements."""
        import hashlib
        import re
        data = os.urandom(70 * 1024)
        local = Path(self.tmp.name) / "a.apk"
        local.write_bytes(data)
        damage = {"on": False}
        server_ref = {}

        def shell(cmd):
            m = re.match(r"md5sum '([^']+)'", cmd)
            if m:
                body = server_ref["s"].files.get(m.group(1), b"")
                digest = hashlib.md5(body + (b"x" if damage["on"] else b"")).hexdigest()
                return f"{digest}  {m.group(1)}\n".encode()
            if cmd.startswith("rm -f"):
                server_ref["s"].files.pop(re.findall(r"'([^']+)'", cmd)[0], None)
                return b""
            return b"/system/bin/sh: md5sum: not found\n"
        with FakeAdbd(shell=shell) as server:
            server_ref["s"] = server
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                dev.push_file(local, "/data/local/tmp/a.apk")
                self.assertEqual(server.files["/data/local/tmp/a.apk"], data)
                damage["on"] = True
                with self.assertRaises(AdbError) as caught:
                    dev.push_file(local, "/data/local/tmp/b.apk")
                self.assertIn("arrived damaged", str(caught.exception))
                self.assertNotIn("/data/local/tmp/b.apk", server.files, "the damaged copy is removed")
        with FakeAdbd(shell=lambda cmd: b"/system/bin/sh: md5sum: not found\n") as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                dev.push_file(local, "/data/local/tmp/c.apk")
                self.assertEqual(server.files["/data/local/tmp/c.apk"], data)

    def test_push_falls_back_to_base64_when_sync_refuses(self):
        data = b"apk bytes " * 1000
        local = Path(self.tmp.name) / "a.apk"
        local.write_bytes(data)
        store = {}

        def shell(cmd):
            import base64 as b64
            import hashlib
            import re
            m = re.match(r"echo '([^']*)' >> '([^']+)' && echo (ok\d+)", cmd)
            if m:
                store[m.group(2)] = store.get(m.group(2), b"") + b64.b64decode(m.group(1))
                return (m.group(3) + "\n").encode()
            m = re.match(r"base64 -d < '([^']+)' > '([^']+)' && md5sum", cmd)
            if m:
                store[m.group(2)] = store.pop(m.group(1), b"")
                return (hashlib.md5(store[m.group(2)]).hexdigest() + "  x\n").encode()
            if cmd.startswith("mv -f"):
                parts = re.findall(r"'([^']+)'", cmd)
                store[parts[1]] = store.pop(parts[0])
                return b"moved\n"
            return b""
        with FakeAdbd(shell=shell, max_data=4096) as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                dev.push_file(local, "/refused/a.apk")
        self.assertEqual(store.get("/refused/a.apk"), data)

    def test_reboot_and_a_dropped_connection(self):
        with FakeAdbd() as server:
            dev = AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5).connect()
            dev.reboot()
            self.assertTrue(server.rebooted)
            with self.assertRaises(AdbConnectionLost):
                dev.shell("echo after")

    def test_remote_paths_are_checked_before_anything_is_sent(self):
        with FakeAdbd() as server:
            with AdbDevice("127.0.0.1", server.port, timeout=5, auth_timeout=5) as dev:
                with self.assertRaises(ValueError):
                    dev.push(__file__, "/data/local/tmp/../x")
                with self.assertRaises(ValueError):
                    dev.push_b64(__file__, "relative/path")
        self.assertEqual(server.opened, [])


class AddressTest(unittest.TestCase):
    def test_address_problems(self):
        self.assertIsNone(address_problem("192.168.1.50"))
        self.assertIsNone(address_problem("192.168.1.50:5555"))
        self.assertIsNone(address_problem("[fe80::1]:5555"))
        self.assertIn("URL", address_problem("http://192.168.1.50"))
        self.assertIn("path", address_problem("192.168.1.50/"))
        self.assertIn("flag", address_problem("--host"))
        self.assertIn("port", address_problem("192.168.1.50:99999"))
        self.assertEqual(parse_address("192.168.1.50"), ("192.168.1.50", 5555))
        self.assertEqual(parse_address("[::1]:5557"), ("::1", 5557))

    def test_png_prefix_strip(self):
        png = b"\x89PNG\r\n\x1a\n" + b"rest"
        self.assertEqual(strip_png_prefix(b"noise\r\n" + png), png)
        with self.assertRaises(AdbError):
            strip_png_prefix(b"no image")


if __name__ == "__main__":
    unittest.main()
