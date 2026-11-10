"""The release download against a local HTTP stub standing in for GitHub: the three assets are picked by name,
SHA256SUMS is checked, a mismatch deletes the file and aborts, a prerelease is taken only when asked."""
import hashlib
import http.server
import json
import os
import sys
import tempfile
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from verysmarttv_setup import github  # noqa: E402


class Stub(http.server.BaseHTTPRequestHandler):
    routes = {}

    def do_GET(self):
        body = self.routes.get(self.path)
        if body is None:
            self.send_response(404)
            self.end_headers()
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class GithubTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = http.server.HTTPServer(("127.0.0.1", 0), Stub)
        cls.base = f"http://127.0.0.1:{cls.server.server_port}"
        threading.Thread(target=cls.server.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        os.environ["VERYSMARTTV_HOME"] = self.tmp.name
        self.addCleanup(self.tmp.cleanup)
        self.addCleanup(os.environ.pop, "VERYSMARTTV_HOME", None)
        self.apks = {app: f"bytes of {app}".encode() * 100 for app in github.APPS}
        sums = "".join(f"{hashlib.sha256(data).hexdigest()}  verysmarttv-{app}-1.2.3.apk\n" for app, data in self.apks.items())
        Stub.routes = {f"/dl/verysmarttv-{app}-1.2.3.apk": data for app, data in self.apks.items()}
        Stub.routes["/dl/SHA256SUMS"] = sums.encode()
        assets = [{"name": f"verysmarttv-{app}-1.2.3.apk", "browser_download_url": f"{self.base}/dl/verysmarttv-{app}-1.2.3.apk",
                   "size": len(self.apks[app])} for app in github.APPS]
        assets.append({"name": "SHA256SUMS", "browser_download_url": f"{self.base}/dl/SHA256SUMS", "size": len(sums)})
        self.release = {"tag_name": "v1.2.3", "prerelease": False, "assets": assets}
        pre = dict(self.release, tag_name="v1.3.0-rc1", prerelease=True)
        Stub.routes["/repos/itsluminous/verysmarttv/releases/latest"] = json.dumps(self.release).encode()
        Stub.routes["/repos/itsluminous/verysmarttv/releases/tags/v1.2.3"] = json.dumps(self.release).encode()
        Stub.routes["/repos/itsluminous/verysmarttv/releases?per_page=5"] = json.dumps([pre, self.release]).encode()

    def test_latest_release_is_downloaded_and_verified(self):
        release = github.fetch_release(api=self.base)
        self.assertEqual(release["tag_name"], "v1.2.3")
        out = github.download(release, log=lambda *_: None)
        self.assertEqual(out["version"], "1.2.3")
        for app in github.APPS:
            self.assertEqual(Path(out[app]).read_bytes(), self.apks[app])
            self.assertEqual(Path(out[app]).parent, Path(self.tmp.name) / "releases" / "1.2.3")
        # A second run reuses the cache: no download line.
        logged = []
        github.download(release, log=logged.append)
        self.assertEqual(logged, [])

    def test_a_checksum_mismatch_deletes_the_file_and_aborts(self):
        Stub.routes["/dl/verysmarttv-livescore-1.2.3.apk"] = b"tampered"
        with self.assertRaises(github.ReleaseError) as caught:
            github.download(self.release, log=lambda *_: None)
        self.assertIn("SHA-256", str(caught.exception))
        self.assertFalse((Path(self.tmp.name) / "releases" / "1.2.3" / "verysmarttv-livescore-1.2.3.apk").exists())

    def test_missing_assets_are_named(self):
        release = dict(self.release, assets=[a for a in self.release["assets"] if "launcher" not in a["name"]])
        with self.assertRaises(github.ReleaseError) as caught:
            github.pick_assets(release)
        self.assertIn("launcher", str(caught.exception))
        release = dict(self.release, assets=[a for a in self.release["assets"] if a["name"] != "SHA256SUMS"])
        with self.assertRaises(github.ReleaseError):
            github.pick_assets(release)

    def test_prerelease_only_when_asked_and_tags(self):
        self.assertEqual(github.fetch_release(api=self.base)["tag_name"], "v1.2.3")
        self.assertEqual(github.fetch_release(prerelease=True, api=self.base)["tag_name"], "v1.3.0-rc1")
        self.assertEqual(github.fetch_release(tag="v1.2.3", api=self.base)["tag_name"], "v1.2.3")
        with self.assertRaises(github.ReleaseError) as caught:
            github.fetch_release(tag="v9.9.9", api=self.base)
        self.assertIn("404", str(caught.exception))

    def test_parse_sums(self):
        sums = github.parse_sums("ABCDEF" + "0" * 58 + "  a.apk\n" + "1" * 64 + " *b.apk\nnot a line\n")
        self.assertEqual(sums, {"a.apk": "abcdef" + "0" * 58, "b.apk": "1" * 64})


if __name__ == "__main__":
    unittest.main()
