"""The latest VerySmartTV release from GitHub: the three APKs and SHA256SUMS, verified, cached under
`~/.verysmarttv/releases/<version>/`. Standard library only (urllib)."""
import hashlib
import json
import re
import urllib.error
import urllib.request
from pathlib import Path

from . import paths

REPO = "itsluminous/verysmarttv"
API = "https://api.github.com"
APPS = ("launcher", "traktshelf", "livescore")
USER_AGENT = "verysmarttv-setup"


class ReleaseError(RuntimeError):
    pass


def _get(url: str, timeout: float = 30.0) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/vnd.github+json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()
    except urllib.error.HTTPError as e:
        raise ReleaseError(f"GitHub answered {e.code} for {url}" + (": no release yet" if e.code == 404 else "")) from e
    except (urllib.error.URLError, OSError) as e:
        raise ReleaseError(f"could not reach {url}: {getattr(e, 'reason', e)}") from e


def fetch_release(tag: str = None, prerelease=False, api: str = API, repo: str = REPO) -> dict:
    """The release JSON: the latest (no prereleases), a tag, or with `prerelease` the newest of all."""
    if tag:
        return json.loads(_get(f"{api}/repos/{repo}/releases/tags/{tag}"))
    if prerelease:
        releases = json.loads(_get(f"{api}/repos/{repo}/releases?per_page=5"))
        if not releases:
            raise ReleaseError("no releases published yet")
        return releases[0]
    return json.loads(_get(f"{api}/repos/{repo}/releases/latest"))


def pick_assets(release: dict) -> dict:
    """{app: asset} for the three APKs plus "sums" for SHA256SUMS; a ReleaseError names what is missing."""
    assets = {a["name"]: a for a in release.get("assets", [])}
    picked = {}
    for app in APPS:
        pattern = re.compile(rf"^verysmarttv-{app}-[0-9][\w.-]*\.apk$")
        names = [n for n in assets if pattern.match(n)]
        if len(names) != 1:
            raise ReleaseError(f"release {release.get('tag_name')} has {len(names)} assets for {app}; expected one "
                               f"verysmarttv-{app}-<version>.apk")
        picked[app] = assets[names[0]]
    if "SHA256SUMS" not in assets:
        raise ReleaseError(f"release {release.get('tag_name')} has no SHA256SUMS")
    picked["sums"] = assets["SHA256SUMS"]
    return picked


def parse_sums(text: str) -> dict:
    """`<hex>  <name>` lines into {name: hex}."""
    sums = {}
    for line in text.splitlines():
        m = re.match(r"^([0-9a-fA-F]{64})\s+\*?(\S+)$", line.strip())
        if m:
            sums[m.group(2)] = m.group(1).lower()
    return sums


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(release: dict, folder: Path = None, log=print, api_download=None) -> dict:
    """Download (or reuse) the three APKs, verify each against SHA256SUMS. Returns {app: path}."""
    version = (release.get("tag_name") or "unknown").lstrip("v")
    folder = Path(folder) if folder else paths.releases_dir() / version
    folder.mkdir(parents=True, exist_ok=True)
    picked = pick_assets(release)
    sums = parse_sums(_get(picked["sums"]["browser_download_url"]).decode("utf-8", "replace"))
    out = {}
    for app in APPS:
        asset = picked[app]
        target = folder / asset["name"]
        want = sums.get(asset["name"])
        if not want:
            raise ReleaseError(f"SHA256SUMS has no line for {asset['name']}")
        if not (target.exists() and sha256(target) == want):
            log(f"  downloading {asset['name']} ({int(asset.get('size', 0)) // 1024} KB)")
            data = _get(asset["browser_download_url"], timeout=300)
            target.write_bytes(data)
        got = sha256(target)
        if got != want:
            target.unlink(missing_ok=True)
            raise ReleaseError(f"{asset['name']}: SHA-256 {got} does not match SHA256SUMS ({want}); the download "
                               "was deleted. Nothing was installed.")
        out[app] = target
    out["version"] = version
    return out
