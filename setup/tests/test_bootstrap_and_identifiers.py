"""The two bootstraps parse (bash -n; PowerShell when `pwsh` is installed, else skipped), setup.sh finds a Python
and starts the package, and the old identifiers stay out of the tree (DESIGN.md §14.2)."""
import os
import re
import shutil
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SETUP = ROOT / "setup"


def identifier_gate():
    """The `git grep` command docs/BUILDING.md gives under "Identifier gate", as (pattern, excludes): the doc is the
    one place the pattern lives, so this file does not have to hold it."""
    text = (ROOT / "docs" / "BUILDING.md").read_text(encoding="utf-8")
    block = text.split("## Identifier gate", 1)[1]
    line = next(l for l in block.splitlines() if l.startswith("git grep"))
    m = re.match(r"git grep -niE '([^']+)' -- (.*)$", line)
    return m.group(1), re.findall(r"'(:![^']+)'", m.group(2))


class BootstrapTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("bash") and os.name != "nt", "no bash (Windows' System32 bash is the WSL stub)")
    def test_setup_sh_parses(self):
        result = subprocess.run(["bash", "-n", str(SETUP / "setup.sh")], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(os.access(SETUP / "setup.sh", os.X_OK) or os.name == "nt", "setup.sh is executable")

    @unittest.skipUnless(shutil.which("pwsh"), "no pwsh")
    def test_setup_ps1_parses(self):
        script = (f"$t=$null;$e=$null;[System.Management.Automation.Language.Parser]::ParseFile('{SETUP / 'setup.ps1'}',"
                  "[ref]$t,[ref]$e)|Out-Null; if ($e.Count) { $e | ForEach-Object { Write-Output $_.Message }; exit 1 }")
        result = subprocess.run(["pwsh", "-NoProfile", "-Command", script], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    @unittest.skipUnless(shutil.which("bash") and os.name != "nt", "needs bash and a POSIX venv layout")
    def test_setup_sh_makes_the_venv_and_runs_the_package(self):
        import tempfile
        with tempfile.TemporaryDirectory() as tmp:
            env = dict(os.environ, VERYSMARTTV_HOME=tmp)
            env.pop("VIRTUAL_ENV", None)
            result = subprocess.run(["bash", str(SETUP / "setup.sh"), "--version"], capture_output=True, text=True,
                                    env=env, timeout=600)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue((Path(tmp) / "venv").is_dir())
            self.assertRegex(result.stdout.strip().splitlines()[-1], r"^\d+\.\d+\.\d+")
            # A second run finds the venv and skips pip.
            again = subprocess.run(["bash", str(SETUP / "setup.sh"), "--version"], capture_output=True, text=True,
                                   env=env, timeout=120)
            self.assertNotIn("creating", again.stdout)

    def test_requirements_are_minimal_and_pinned(self):
        lines = [line.strip() for line in (SETUP / "requirements.txt").read_text().splitlines()
                 if line.strip() and not line.startswith("#")]
        self.assertEqual(len(lines), 1)
        self.assertRegex(lines[0], r"^cryptography==\d+\.\d+(\.\d+)?$")


class IdentifiersTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("git") and (ROOT / ".git").exists(), "needs the git checkout")
    def test_no_old_identifiers_outside_the_allowed_files(self):
        pattern, excludes = identifier_gate()
        self.assertTrue(pattern and excludes)
        result = subprocess.run(["git", "grep", "-niE", pattern, "--", *excludes], cwd=ROOT, capture_output=True, text=True)
        self.assertEqual(result.stdout.strip(), "", "old identifiers outside the allowed files:\n" + result.stdout)


if __name__ == "__main__":
    unittest.main()
