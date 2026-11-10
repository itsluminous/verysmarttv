"""A fake TV for the tests: answers the shell commands the setup tool sends from a small in-memory state, and
records every command. Only the ADB transport is simulated; the real tool logic runs against it.

The state models what the Home guard cares about: which packages are installed and disabled, who answers
HOME and in which order, whether a package is a launcher, which app owns the Wi-Fi network, what `dumpsys
package` says about the input permission. Disabling the package that owns Home moves Home the way Android
does: to the next enabled claimant, a FallbackHome if that is all that is left, or nothing.
"""
import re

PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 16 + b"IEND\xaeB`\x82"
RC = "; echo __VST_RC__$?"


class FakeTv:
    def __init__(self, installed=(), disabled=(), claims=(), home=None, props=None, wifi_owners=None,
                 dumps=None, input_handlers=(), settings_handlers=("com.android.tv.settings",), settings=None,
                 no_cmd=False, home_process=None, start_fails=False, refuse_disable=(), fallback_wins=False):
        self.installed = set(installed)
        self.disabled = set(disabled)
        self.claims = list(claims)             # package/activity in the TV's priority order
        self.home = home                       # the component Home resolves to, or None
        self.props = dict(props or {})
        self.wifi_owners = wifi_owners         # None: dumpsys shows no network; list: the cname= owners
        self.dumps = dict(dumps or {})         # package -> dumpsys text
        self.input_handlers = set(input_handlers)
        self.settings_handlers = set(settings_handlers)
        self.settings = dict(settings or {})
        self.secure = {}
        self.no_cmd = no_cmd                   # firmware without `cmd` (Android < 7)
        self.home_process = home_process
        self.start_fails = start_fails
        self.refuse_disable = set(refuse_disable)
        self.fallback_wins = fallback_wins          # a firmware that hands Home to its FallbackHome placeholder
        self.commands = []
        self.files = {}
        self.installed_apks = []
        self.max_data = 256 * 1024
        self.closed = False

    # -- helpers --------------------------------------------------------------------------------------------

    def enabled(self, package):
        return package in self.installed and package not in self.disabled

    def enabled_claims(self):
        return [c for c in self.claims if self.enabled(c.split("/")[0])]

    def resettle_home(self):
        """What Android does when the set of HOME apps changes: keep a still-enabled default, or with
        `fallback_wins` drop the preference and land on the placeholder."""
        if self.home and self.enabled(self.home.split("/")[0]) and not self.fallback_wins:
            return
        claims = self.enabled_claims()
        real = [c for c in claims if not c.endswith("FallbackHome")]
        fallback = [c for c in claims if c.endswith("FallbackHome")]
        self.home = ((fallback if self.fallback_wins else real) or claims or [None])[0]

    # -- the transport the tool uses ------------------------------------------------------------------------

    def shell(self, command, timeout=None):
        self.commands.append(command)
        rc_marker = command.endswith(RC)
        if rc_marker:
            command = command[: -len(RC)]
        out = self._answer(command)
        if rc_marker:
            out += "__VST_RC__0\n"
        return out

    def shell_rc(self, command, timeout=None):
        return self._answer(command), 0

    def getprop(self, name):
        return self.props.get(name, "")

    def push(self, local, remote, timeout=None, mode=0o644):
        with open(local, "rb") as f:
            self.files[remote] = f.read()

    push_b64 = push

    def push_file(self, local, remote, progress=None):
        self.push(local, remote)

    def pull(self, remote, local, timeout=None):
        with open(local, "wb") as f:
            f.write(self.files.get(remote, b""))

    def press(self, *keys, gap=0.0):
        for key in keys:
            self.commands.append(f"input keyevent {key}")

    def screenshot(self, local):
        self.commands.append("screencap")
        with open(local, "wb") as f:
            f.write(PNG)
        return len(PNG)

    def reboot(self):
        self.commands.append("reboot")

    def close(self):
        self.closed = True

    # -- answers --------------------------------------------------------------------------------------------

    def _answer(self, command):
        if "$(getprop" in command and command.startswith("echo "):
            return "".join(f"{k}={self.props.get(k, '')}\n" for k in re.findall(r"echo (\S+)=\$\(getprop", command))
        m = re.fullmatch(r"getprop (\S+)", command.strip())
        if m:
            return self.props.get(m.group(1), "") + "\n"
        if command.startswith("pm install -r"):
            self.installed_apks.append(command.split()[3])
            return "Success\n"
        if command.startswith("pm list packages"):
            if "-d" in command.split():
                names = sorted(p for p in self.installed if p in self.disabled)
            elif "-e" in command.split():
                names = sorted(p for p in self.installed if p not in self.disabled)
            else:
                names = sorted(self.installed)
            return "".join(f"package:{n}\n" for n in names)
        m = re.fullmatch(r"pm disable-user --user 0 (\S+)", command.strip())
        if m:
            name = m.group(1)
            if name not in self.installed:
                return f"Exception occurred while executing 'disable-user':\njava.lang.IllegalArgumentException: Unknown package: {name}\n"
            if name in self.refuse_disable:
                return "Error: java.lang.SecurityException: Permission Denial\n"
            self.disabled.add(name)
            self.resettle_home()
            return f"Package {name} new state: disabled-user\n"
        m = re.fullmatch(r"pm enable (?:--user 0 )?(\S+)", command.strip())
        if m:
            self.disabled.discard(m.group(1))
            return f"Package {m.group(1)} new state: enabled\n"
        if command.startswith("cmd ") and self.no_cmd:
            return "/system/bin/sh: cmd: not found\n"
        if command.startswith("cmd package resolve-activity"):
            m = re.search(r"category\.HOME\s+(\S+)\s*$", command.strip())
            if m and not m.group(1).startswith("|"):
                pkg = m.group(1)
                found = [c for c in self.enabled_claims() if c.split("/")[0] == pkg]
                return (found[0] + "\n") if found else "No activity found\n"
            m = re.search(r"category\.HOME\s+(\S+)\s*\|", command)
            if m:
                pkg = m.group(1)
                found = [c for c in self.enabled_claims() if c.split("/")[0] == pkg]
                return (found[0] + "\n") if found else "No activity found\n"
            return (self.home or "No activity found") + "\n"
        if command.startswith("cmd package set-home-activity"):
            m = re.search(r"\$\(cmd package resolve-activity[^$]*HOME\s+(\S+)\s*\|", command)
            if m:
                found = [c for c in self.enabled_claims() if c.split("/")[0] == m.group(1)]
                comp = found[0] if found else None
            else:
                comp = command.split()[-1]
            if comp and comp in self.enabled_claims():
                self.home = comp
                return "Success\n"
            return "Error: Could not set home\n"
        if command.startswith("cmd package query-activities") and "category.HOME" in command:
            return "".join(f"  {c}\n" for c in self.enabled_claims())
        if command.startswith("cmd package query-activities") and "cursor.item/channel" in command:
            return "".join(f"  {p}/.Inputs\n" for p in sorted(self.input_handlers) if self.enabled(p))
        if command.startswith("cmd package query-activities") and "settings.SETTINGS" in command:
            return "".join(f"  {p}/.Settings\n" for p in sorted(self.settings_handlers) if self.enabled(p))
        if command.startswith("cmd package compile"):
            return "Success\n"
        if command.startswith("dumpsys wifi"):
            if self.wifi_owners is None:
                return "Wi-Fi is enabled\n"
            return "".join(f"  cname={o} \n" for o in self.wifi_owners)
        m = re.fullmatch(r"dumpsys package (\S+)", command.strip())
        if m:
            name = m.group(1)
            if name in self.dumps:
                return self.dumps[name]
            if name not in self.installed:
                return "Unable to find package: " + name + "\n"
            return f"Package [{name}] (abc):\n    requested permissions:\n      android.permission.INTERNET\n    install permissions:\n"
        if command.startswith("dumpsys activity"):
            return f"  mHomeProcess: ProcessRecord{{1 2:{self.home_process}/u0a1}}\n" if self.home_process else ""
        m = re.fullmatch(r"settings get global (\S+)", command.strip())
        if m:
            return str(self.settings.get(m.group(1), "null")) + "\n"
        m = re.fullmatch(r"settings put global (\S+) (\S+)", command.strip())
        if m:
            self.settings[m.group(1)] = m.group(2)
            return ""
        m = re.fullmatch(r"settings delete global (\S+)", command.strip())
        if m:
            self.settings.pop(m.group(1), None)
            return "Deleted 1 rows\n"
        if command.startswith("am start"):
            return "Error: Activity not started\n" if self.start_fails else "Starting: Intent\nStatus: ok\nComplete\n"
        if "am force-stop" in command:
            return ""
        if command.startswith("content write"):
            return "" if not self.no_cmd else "Error while accessing provider\n"
        if command.startswith("rm -f") and "screencap" in command:
            self.files["/data/local/tmp/verysmarttv-shot.png"] = PNG
            return ""
        return ""
