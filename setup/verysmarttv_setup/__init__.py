"""VerySmartTV setup: the computer-side tool that puts the three apps on an Android TV (DESIGN.md §16).

Pure Python 3.9+, one third-party package (`cryptography`, for the RSA key ADB authenticates with). Run it
as `python -m verysmarttv_setup <command>`, or through `setup/setup.sh` and `setup/setup.ps1`, which only
find a Python, make a venv and start this package.
"""
VERSION = "1.0.0-rc2"
