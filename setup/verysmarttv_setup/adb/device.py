"""A pure-Python ADB client for one TV over TCP: connect and authenticate, run shell commands, move files,
take screenshots, reboot. No adb binary, no adb server.

The link is one TCP socket to the TV's port 5555; each command opens one ADB stream on it (`shell:`,
`sync:`, `reboot:`), so a run of commands shares the connection and the one "Allow USB debugging?" accept.
"""
import base64
import hashlib
import os
import re
import socket
import time
from pathlib import Path

from . import auth
from .protocol import (A_AUTH, A_CLSE, A_CNXN, A_OKAY, A_OPEN, A_STLS, A_WRTE, AUTH_RSAPUBLICKEY,
                       AUTH_SIGNATURE, AUTH_TOKEN, MAX_DATA, VERSION, AdbConnectionLost, AdbError,
                       AdbRefused, Message, read_message, send_message)

DEFAULT_PORT = 5555
SYNC_CHUNK = 64 * 1024
# The remote path is pasted into the TV's shell: absolute, plain characters, no "..".
REMOTE_RE = re.compile(r"/[A-Za-z0-9._/-]+")
PNG = b"\x89PNG\r\n\x1a\n"
PNG_END = b"IEND\xaeB`\x82"
SHOT_REMOTE = "/data/local/tmp/verysmarttv-shot.png"
KEY_NAME = re.compile(r"^[A-Z0-9_]+$")


def split_host_port(endpoint: str):
    """host:port or [ipv6]:port into (host, port or None)."""
    if endpoint.startswith("["):
        end = endpoint.find("]")
        if end != -1:
            rest = endpoint[end + 1:]
            return endpoint[1:end], (rest[1:] if rest.startswith(":") else None)
    elif endpoint.count(":") == 1:
        return tuple(endpoint.split(":", 1))
    return endpoint, None


def address_problem(raw: str):
    """What makes this word something other than the TV's address, as a sentence, or None.

    An address is pasted out of a browser often enough that the two shapes it is never in are worth
    naming: `http://192.168.1.50`, or `192.168.1.50/` from the tail of a URL.
    """
    word = (raw or "").strip()
    if not word:
        return "no address given"
    if word.startswith("-"):
        return "that is a flag, not the TV's address"
    if "://" in word:
        return "that is a URL, not the TV's address: give the IP alone, e.g. 192.168.1.50"
    if "/" in word:
        return "that is a path, not the TV's address: give the IP alone, e.g. 192.168.1.50"
    host, port = split_host_port(word)
    if not host:
        return "no host in the address"
    if port is not None:
        if not port.isdigit() or not 1 <= int(port) <= 65535:
            return f"not a port number: {port!r}"
    return None


def parse_address(raw: str):
    """(host, port) for `ip`, `ip:port` or `[ipv6]:port`; a ValueError names what is wrong."""
    problem = address_problem(raw)
    if problem:
        raise ValueError(problem)
    host, port = split_host_port(raw.strip())
    return host, int(port) if port else DEFAULT_PORT


def connect_error(e: Exception, host: str, port: int) -> str:
    """One paragraph for the user about why connect() failed, with the way past it."""
    target = f"{host}:{port}"
    pair = ("pair the TV once with Google's adb (`adb pair`, then `adb connect` to the port its Wireless "
            "debugging screen shows) and run `adb tcpip 5555`; port 5555 usually closes again at the next "
            "reboot")
    if isinstance(e, AdbError) and "STLS" in str(e):
        return (f"{target} is a Wireless debugging port: it asks for TLS, which this tool does not speak. "
                f"To use port 5555 instead, {pair}, then give the TV's IP alone.")
    if isinstance(e, ConnectionRefusedError):
        return (f"{target} refused the connection: nothing listens for ADB there, so network ADB is off or "
                f"on another port. Turn on USB debugging (and Network/ADB debugging where the TV has it) "
                f"under Developer options. If the TV only has Wireless debugging, {pair}. Not an Android "
                f"TV (Samsung, LG, Roku)? There is no ADB to connect to.")
    if isinstance(e, socket.timeout):
        return (f"{target} took the connection but did not finish the ADB handshake in time. If the TV "
                f"shows \"Allow USB debugging?\", accept it (tick \"Always allow\") and run this again; "
                f"otherwise check that this is the TV's address.")
    if isinstance(e, OSError) and not isinstance(e, ConnectionError):
        extra = ""
        if os.name == "posix" and getattr(e, "errno", None) == 65 and os.uname().sysname == "Darwin":
            extra = (" On macOS this is also what Local Network privacy looks like: allow the app that "
                     "runs this tool (Terminal, iTerm2, VS Code) under System Settings > Privacy & "
                     "Security > Local Network, then quit and reopen it.")
        return f"could not reach {target} ({e}). Is the TV on and on this network?{extra}"
    return f"could not connect to {target}: {type(e).__name__}: {e}"


class AdbDevice:
    """One authenticated ADB connection. Use `connect()` below, or `with AdbDevice(host, port) as dev`."""

    def __init__(self, host: str, port: int = DEFAULT_PORT, timeout: float = 10.0, auth_timeout: float = 120.0,
                 keys=None):
        self.host, self.port = host, port
        self.timeout, self.auth_timeout = timeout, auth_timeout
        self._keys = keys
        self._sock = None
        self._next_id = 1
        self._early = []
        self._stream_closed = False
        self.max_data = MAX_DATA
        self.banner = ""

    # -- connection ---------------------------------------------------------------------------------

    def connect(self) -> "AdbDevice":
        keys = self._keys if self._keys is not None else auth.keys_to_offer()
        sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        sock.settimeout(self.timeout)
        self._sock = sock
        send_message(sock, Message(A_CNXN, VERSION, MAX_DATA, b"host::\0"))
        offered_public = False
        tries = 0
        while True:
            msg = read_message(sock)
            if msg.command == A_CNXN:
                self.max_data = min(MAX_DATA, msg.arg1) if msg.arg1 else MAX_DATA
                self.banner = msg.data.rstrip(b"\0").decode("utf-8", "replace")
                return self
            if msg.command == A_STLS:
                self.close()
                raise AdbError("the TV asked for STLS: this is a Wireless debugging (TLS) port")
            if msg.command != A_AUTH or msg.arg0 != AUTH_TOKEN:
                self.close()
                raise AdbError(f"unexpected {msg.name} during the handshake")
            if tries < len(keys):
                send_message(sock, Message(A_AUTH, AUTH_SIGNATURE, 0, keys[tries].sign(msg.data)))
                tries += 1
                continue
            if offered_public or not keys:
                self.close()
                raise AdbError("the TV rejected every key and the public key offer")
            # Every key was refused: offer our own public key, which puts the "Allow USB debugging?"
            # prompt on the TV. The answer takes as long as the person needs.
            send_message(sock, Message(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys[0].public_payload()))
            offered_public = True
            sock.settimeout(self.auth_timeout)

    def close(self):
        if self._sock is not None:
            try:
                self._sock.close()
            finally:
                self._sock = None

    def __enter__(self):
        return self if self._sock else self.connect()

    def __exit__(self, *exc):
        self.close()

    # -- streams ------------------------------------------------------------------------------------

    def _open(self, service: str, timeout: float):
        if self._sock is None:
            raise AdbConnectionLost("not connected")
        sock = self._sock
        sock.settimeout(timeout)
        local = self._next_id
        self._next_id += 1
        payload = service.encode() + b"\0"
        if len(payload) > self.max_data:
            raise AdbError(f"the command is longer than this TV's ADB payload limit ({self.max_data} bytes)")
        send_message(sock, Message(A_OPEN, local, 0, payload))
        while True:
            msg = read_message(sock)
            if msg.command == A_OKAY and msg.arg1 == local:
                return local, msg.arg0
            if msg.command == A_CLSE and msg.arg1 == local:
                raise AdbRefused(f"the TV refused to open {service.split(':', 1)[0]}:")
            if msg.command == A_WRTE:
                # A stale stream of a previous command: acknowledge and ignore.
                send_message(sock, Message(A_OKAY, msg.arg1, msg.arg0))

    def _read_stream(self, local: int, remote: int, on_data, timeout: float):
        """WRTE payloads for this stream until the TV closes it; each is acknowledged."""
        sock = self._sock
        sock.settimeout(timeout)
        while True:
            msg = read_message(sock)
            if msg.command == A_WRTE and msg.arg1 == local:
                send_message(sock, Message(A_OKAY, local, remote))
                on_data(msg.data)
            elif msg.command == A_CLSE and msg.arg1 == local:
                send_message(sock, Message(A_CLSE, local, remote))
                return
            elif msg.command == A_OKAY:
                continue

    def _write_stream(self, local: int, remote: int, data: bytes, timeout: float):
        sock = self._sock
        sock.settimeout(timeout)
        for i in range(0, len(data), self.max_data):
            send_message(sock, Message(A_WRTE, local, remote, data[i:i + self.max_data]))
            while True:
                msg = read_message(sock)
                if msg.command == A_OKAY and msg.arg1 == local:
                    break
                if msg.command == A_CLSE and msg.arg1 == local:
                    # adbd answers a sync request it refuses with FAIL and closes the stream; the connection
                    # itself is fine, which is what lets push_file fall back to base64 chunks.
                    self._stream_closed = True
                    failed = next((e for e in self._early if e.startswith(b"FAIL")), None)
                    why = failed[8:].decode("utf-8", "replace") if failed else "the TV closed the stream"
                    self._early = []
                    raise AdbRefused(f"the TV refused the transfer: {why}")
                if msg.command == A_WRTE and msg.arg1 == local:
                    # Early data (a FAIL from sync): keep it for the reader.
                    send_message(sock, Message(A_OKAY, local, remote))
                    self._early.append(msg.data)

    # -- shell --------------------------------------------------------------------------------------

    def shell(self, command: str, timeout: float = 120.0) -> str:
        """Run one shell command; its stdout and stderr as text (the exit status is not carried, so a
        caller that needs it appends `; echo rc=$?`)."""
        local, remote = self._open(f"shell:{command}", timeout)
        chunks = []
        self._read_stream(local, remote, chunks.append, timeout)
        return b"".join(chunks).decode("utf-8", "replace").replace("\r\n", "\n")

    def shell_rc(self, command: str, timeout: float = 120.0):
        """(output, exit status) using a marker line; status None when the marker did not come back."""
        out = self.shell(f"{command}; echo __VST_RC__$?", timeout)
        m = re.search(r"__VST_RC__(\d+)\s*$", out)
        if not m:
            return out, None
        return out[:m.start()], int(m.group(1))

    def getprop(self, name: str) -> str:
        return self.shell(f"getprop {name}", 30).strip()

    def reboot(self):
        """Ask for a reboot; returns once the request is accepted (the TV then goes away)."""
        try:
            self._open("reboot:", 30)
        except AdbConnectionLost:
            pass
        self.close()

    # -- sync (push / pull) -------------------------------------------------------------------------

    def _sync(self, timeout: float):
        self._early = []
        self._stream_closed = False
        return self._open("sync:", timeout)

    def _sync_send(self, ids, cmd: bytes, payload: bytes, timeout: float):
        local, remote = ids
        self._write_stream(local, remote, cmd + len(payload).to_bytes(4, "little") + payload, timeout)

    def _sync_reader(self, ids, timeout: float):
        """A function returning the next sync reply (id, payload); reads WRTEs as needed."""
        local, remote = ids
        sock = self._sock
        buf = bytearray(b"".join(self._early))
        self._early = []

        def fill():
            sock.settimeout(timeout)
            while True:
                msg = read_message(sock)
                if msg.command == A_WRTE and msg.arg1 == local:
                    send_message(sock, Message(A_OKAY, local, remote))
                    buf.extend(msg.data)
                    return
                if msg.command == A_CLSE and msg.arg1 == local:
                    self._stream_closed = True
                    raise AdbRefused("the TV closed the sync stream")

        def next_reply():
            while len(buf) < 8:
                fill()
            rid, length = bytes(buf[:4]), int.from_bytes(buf[4:8], "little")
            if rid in (b"DATA", b"FAIL"):
                while len(buf) < 8 + length:
                    fill()
                payload = bytes(buf[8:8 + length])
                del buf[:8 + length]
            else:
                payload = bytes(buf[4:8])
                del buf[:8]
            return rid, payload
        return next_reply

    def _sync_quit(self, ids, timeout):
        if self._stream_closed:
            return
        local, remote = ids
        try:
            self._write_stream(local, remote, b"QUIT" + b"\0\0\0\0", min(timeout, 10.0))
            send_message(self._sock, Message(A_CLSE, local, remote))
            self._sock.settimeout(2)
            for _ in range(4):
                msg = read_message(self._sock)
                if msg.command == A_CLSE and msg.arg1 == local:
                    break
        except (AdbError, socket.timeout, OSError):
            pass

    def push(self, local_path, remote: str, timeout: float = 300.0, mode: int = 0o644):
        """Copy a local file to the TV with the sync service (what `adb push` does)."""
        if not safe_remote(remote):
            raise ValueError(f"refusing remote path {remote!r}: use an absolute path of letters, digits, "
                             "'.', '_', '-' and '/', without '..'")
        data = Path(local_path).read_bytes()
        ids = self._sync(timeout)
        try:
            reply = self._sync_reader(ids, timeout)
            self._sync_send(ids, b"SEND", f"{remote},{mode}".encode(), timeout)
            for i in range(0, len(data), SYNC_CHUNK):
                self._sync_send(ids, b"DATA", data[i:i + SYNC_CHUNK], timeout)
            self._sync_send(ids, b"DONE", b"", timeout)
            # DONE carries the mtime in the length field; the reply is OKAY or FAIL + message.
            rid, payload = reply()
            if rid == b"FAIL":
                raise AdbRefused(f"the TV refused the push to {remote}: {payload.decode('utf-8', 'replace')}")
            if rid != b"OKAY":
                raise AdbError(f"unexpected sync reply {rid!r}")
        finally:
            self._sync_quit(ids, timeout)

    def pull(self, remote: str, local_path, timeout: float = 300.0):
        """Copy a file from the TV; the local file is replaced only once the whole file has come back."""
        local_path = Path(local_path)
        part = local_path.with_name(f".{local_path.name}.{os.getpid()}.part")
        ids = self._sync(timeout)
        try:
            reply = self._sync_reader(ids, timeout)
            self._sync_send(ids, b"RECV", remote.encode(), timeout)
            with open(part, "wb") as f:
                while True:
                    rid, payload = reply()
                    if rid == b"DATA":
                        f.write(payload)
                    elif rid == b"DONE":
                        break
                    elif rid == b"FAIL":
                        raise AdbRefused(f"the TV did not send {remote}: {payload.decode('utf-8', 'replace')}")
                    else:
                        raise AdbError(f"unexpected sync reply {rid!r}")
            os.replace(part, local_path)
        except BaseException:
            try:
                os.remove(part)
            except OSError:
                pass
            raise
        finally:
            self._sync_quit(ids, timeout)

    # -- base64 push (the fallback for firmware whose sync service refuses) -------------------------

    def push_b64(self, local_path, remote: str, progress=None, timeout: float = 60.0):
        """Send a file as base64 chunks through shell commands, assembled next to the target and checked
        with md5sum; only a matching copy is moved over the target."""
        if not safe_remote(remote):
            raise ValueError(f"refusing remote path {remote!r}")
        data = Path(local_path).read_bytes()
        want = hashlib.md5(data).hexdigest()
        b64, part = remote + ".b64.part", remote + ".part"
        chunk = min(48 * 1024, (self.max_data - 85 - len(b64)) * 3 // 4)
        chunk -= chunk % 3
        if chunk < 256:
            raise ValueError(f"the path {remote!r} leaves under 256 bytes per chunk on this TV")
        q = lambda p: "'" + p + "'"
        try:
            self.shell(f"rm -f {q(b64)} {q(part)} && : > {q(b64)}", timeout)
            n = (len(data) + chunk - 1) // chunk
            for i in range(n):
                piece = base64.b64encode(data[i * chunk:(i + 1) * chunk]).decode()
                out = self.shell(f"echo '{piece}' >> {q(b64)} && echo ok{i}", timeout)
                if f"ok{i}" not in out:
                    raise AdbError(f"chunk {i} of {n} was not written: {out.strip() or 'no output'}")
                if progress:
                    progress(i + 1, n)
            out = self.shell(f"base64 -d < {q(b64)} > {q(part)} && md5sum {q(part)}; rm -f {q(b64)}", 120)
            got = out.split()[0] if out.strip() else ""
            if got != want:
                raise AdbError(f"md5 mismatch after the transfer: TV {got or 'none'}, local {want}")
            out = self.shell(f"mv -f {q(part)} {q(remote)} && echo moved", timeout)
            if "moved" not in out:
                # Some firmware refuses rename(2) in /data/local/tmp while cp works there.
                out = self.shell(f"cp -f {q(part)} {q(remote)} && md5sum {q(remote)}", 120)
                if (out.split() or [""])[0] != want:
                    raise AdbError(f"could not put the verified copy at {remote}: {out.strip()}")
        finally:
            try:
                self.shell(f"rm -f {q(b64)} {q(part)}", 30)
            except AdbError:
                pass

    def push_file(self, local_path, remote: str, progress=None):
        """Push with sync, falling back to base64 chunks when the TV's sync service refuses."""
        try:
            self.push(local_path, remote)
        except AdbRefused:
            self.push_b64(local_path, remote, progress)

    # -- screenshot ---------------------------------------------------------------------------------

    def press(self, *keys: str, gap: float = 0.6):
        for i, key in enumerate(keys):
            if not KEY_NAME.match(key):
                raise ValueError(f"not a key name: {key!r}")
            if i:
                time.sleep(gap)
            self.shell(f"input keyevent {keyevent(key)}", 30)

    def screenshot(self, local_path) -> int:
        """`screencap -p` on the TV, pulled and cleaned: everything from the PNG signature on. Returns the
        byte count; raises AdbError when the capture is empty or not a whole PNG."""
        local_path = Path(local_path)
        local_path.parent.mkdir(parents=True, exist_ok=True)
        out, rc = self.shell_rc(f"rm -f {SHOT_REMOTE}; screencap -p {SHOT_REMOTE}", 120)
        if rc not in (None, 0, 1):
            self.shell(f"rm -f {SHOT_REMOTE}", 30)
            raise AdbError(f"screencap failed on the TV (exit status {rc}): {out.strip() or 'no output'}")
        self.pull(SHOT_REMOTE, local_path)
        self.shell(f"rm -f {SHOT_REMOTE}", 30)
        data = local_path.read_bytes()
        if not data:
            raise AdbError("the capture came back empty: Live TV, HDMI and some streaming apps give an "
                           "empty screenshot on most TVs; check what is on screen")
        png = strip_png_prefix(data)
        if not png.endswith(PNG_END):
            raise AdbError(f"{local_path} is {len(png)} bytes of a PNG that stops before its IEND chunk; "
                           "try again")
        if len(png) != len(data):
            local_path.write_bytes(png)
        return len(png)


def strip_png_prefix(data: bytes) -> bytes:
    """Everything from the PNG signature on (some TVs print console noise before the image)."""
    at = data.find(PNG)
    if at < 0:
        raise AdbError("no PNG signature in the capture")
    return data[at:]


def keyevent(name: str) -> str:
    return name if name.isdigit() or name.startswith("KEYCODE_") else "KEYCODE_" + name


def safe_remote(path: str) -> bool:
    return bool(REMOTE_RE.fullmatch(path)) and ".." not in path and not path.endswith("/")


def connect(address: str, timeout: float = 10.0, auth_timeout: float = 120.0) -> AdbDevice:
    """An authenticated device for `ip` or `ip:port`; raises ValueError for a bad address and the
    socket's or AdbError for a connection that failed (word it with `connect_error`)."""
    host, port = parse_address(address)
    return AdbDevice(host, port, timeout, auth_timeout).connect()
