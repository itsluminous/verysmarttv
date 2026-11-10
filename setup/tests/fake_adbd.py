"""An in-process adbd for the protocol tests: a TCP server that speaks enough of the ADB wire protocol to
authenticate a client (token, signature, public-key offer), run `shell:` services, the `sync:` SEND/RECV
service and `reboot:`. The client under test is the real `verysmarttv_setup.adb` over a real socket.
"""
import base64
import os
import socket
import struct
import threading

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.asymmetric.utils import Prehashed

from verysmarttv_setup.adb import protocol as P


def public_key_from_payload(payload: bytes):
    """The RSAPublicKey struct adb sends (base64, then ' name\\0') back into a cryptography public key."""
    blob = base64.b64decode(payload.split(b" ", 1)[0])
    words = struct.unpack("<II64I64II", blob)
    assert words[0] == 64, "the modulus length in words"
    n_words, exponent = words[2:66], words[130]
    n = sum(w << (32 * i) for i, w in enumerate(n_words))
    return rsa.RSAPublicNumbers(exponent, n).public_key()


class FakeAdbd:
    """Start with `with FakeAdbd(...) as server:`; `server.port` is where the client connects."""

    def __init__(self, trusted=(), accept_prompt=True, shell=None, max_data=64 * 1024, stls=False, secure=True):
        self.trusted = list(trusted)            # public keys that sign without a prompt
        self.accept_prompt = accept_prompt      # whether the "Allow USB debugging?" is accepted
        self.shell_handler = shell or (lambda cmd: f"ran: {cmd}\n".encode())
        self.max_data = max_data
        self.stls = stls
        self.secure = secure
        self.files = {}
        self.prompts = 0
        self.rebooted = False
        self.opened = []
        self._sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self._sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self._sock.bind(("127.0.0.1", 0))
        self._sock.listen(4)
        self.port = self._sock.getsockname()[1]
        self._stop = False
        self._thread = threading.Thread(target=self._serve, daemon=True)

    def __enter__(self):
        self._thread.start()
        return self

    def __exit__(self, *exc):
        self._stop = True
        try:
            self._sock.close()
        except OSError:
            pass

    def _serve(self):
        while not self._stop:
            try:
                conn, _ = self._sock.accept()
            except OSError:
                return
            try:
                self._handle(conn)
            except (P.AdbError, OSError, ConnectionError):
                pass
            finally:
                conn.close()

    def _send(self, conn, command, arg0=0, arg1=0, data=b""):
        P.send_message(conn, P.Message(command, arg0, arg1, data))

    def _handle(self, conn):
        conn.settimeout(10)
        msg = P.read_message(conn)
        assert msg.command == P.A_CNXN
        if self.stls:
            self._send(conn, P.A_STLS, 1, 0)
            return
        if self.secure:
            token = os.urandom(20)
            self._send(conn, P.A_AUTH, P.AUTH_TOKEN, 0, token)
            while True:
                msg = P.read_message(conn)
                if msg.command != P.A_AUTH:
                    return
                if msg.arg0 == P.AUTH_SIGNATURE:
                    if any(self._verify(key, msg.data, token) for key in self.trusted):
                        break
                    token = os.urandom(20)
                    self._send(conn, P.A_AUTH, P.AUTH_TOKEN, 0, token)
                elif msg.arg0 == P.AUTH_RSAPUBLICKEY:
                    self.prompts += 1
                    if not self.accept_prompt:
                        return
                    self.trusted.append(public_key_from_payload(msg.data))
                    break
        self._send(conn, P.A_CNXN, P.VERSION, self.max_data, b"device::ro.product.model=FakeTV;features=cmd\0")
        remote_ids = 100
        while True:
            msg = P.read_message(conn)
            if msg.command != P.A_OPEN:
                continue
            service = msg.data.rstrip(b"\0").decode()
            self.opened.append(service)
            local_id, remote_ids = msg.arg0, remote_ids + 1
            self._send(conn, P.A_OKAY, remote_ids, local_id)
            if service.startswith("shell:"):
                out = self.shell_handler(service[6:])
                for i in range(0, len(out), self.max_data):
                    self._send(conn, P.A_WRTE, remote_ids, local_id, out[i:i + self.max_data])
                    self._expect_okay(conn)
                self._send(conn, P.A_CLSE, remote_ids, local_id)
            elif service == "sync:":
                self._sync(conn, local_id, remote_ids)
            elif service == "reboot:":
                self.rebooted = True
                self._send(conn, P.A_CLSE, remote_ids, local_id)
                return

    @staticmethod
    def _verify(key, signature, token):
        try:
            key.verify(signature, token, padding.PKCS1v15(), Prehashed(hashes.SHA1()))
            return True
        except InvalidSignature:
            return False

    def _expect_okay(self, conn):
        while True:
            msg = P.read_message(conn)
            if msg.command == P.A_OKAY:
                return

    def _sync(self, conn, local_id, remote_id):
        buf = bytearray()
        receiving = None

        def reply(rid, payload=b""):
            data = rid + len(payload).to_bytes(4, "little") + payload
            self._send(conn, P.A_WRTE, remote_id, local_id, data)
            self._expect_okay(conn)

        while True:
            msg = P.read_message(conn)
            if msg.command == P.A_CLSE:
                self._send(conn, P.A_CLSE, remote_id, local_id)
                return
            if msg.command != P.A_WRTE:
                continue
            self._send(conn, P.A_OKAY, remote_id, local_id)
            buf.extend(msg.data)
            while len(buf) >= 8:
                rid, length = bytes(buf[:4]), int.from_bytes(buf[4:8], "little")
                if rid in (b"SEND", b"DATA", b"RECV", b"STAT", b"LIST"):
                    if len(buf) < 8 + length:
                        break
                    payload = bytes(buf[8:8 + length])
                    del buf[:8 + length]
                else:
                    payload = b""
                    del buf[:8]
                if rid == b"SEND":
                    path = payload.decode().rsplit(",", 1)[0]
                    if path.startswith("/refused"):
                        # adbd answers FAIL and closes the sync stream; the connection stays.
                        reply(b"FAIL", b"couldn't create file: Permission denied")
                        self._send(conn, P.A_CLSE, remote_id, local_id)
                        return
                    else:
                        receiving = (path, bytearray())
                elif rid == b"DATA" and receiving:
                    receiving[1].extend(payload)
                elif rid == b"DONE":
                    if receiving:
                        self.files[receiving[0]] = bytes(receiving[1])
                        receiving = None
                        reply(b"OKAY")
                elif rid == b"RECV":
                    path = payload.decode()
                    if path not in self.files:
                        reply(b"FAIL", f"remote object '{path}' does not exist".encode())
                    else:
                        data = self.files[path]
                        for i in range(0, len(data), 1000):
                            reply(b"DATA", data[i:i + 1000])
                        reply(b"DONE")
                elif rid == b"QUIT":
                    pass
