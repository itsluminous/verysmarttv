"""The ADB wire protocol: 24-byte headers, the six commands, and a socket that reads whole messages.

Every message is `command, arg0, arg1, data_length, data_checksum, magic` as little-endian u32s, followed by
`data_length` bytes. The checksum is the plain sum of the payload bytes (protocol version 0x01000000; newer
daemons ignore it), and magic is the command XOR 0xFFFFFFFF.
"""
import socket
import struct

HEADER = struct.Struct("<6I")

A_SYNC = 0x434E5953
A_CNXN = 0x4E584E43
A_OPEN = 0x4E45504F
A_OKAY = 0x59414B4F
A_CLSE = 0x45534C43
A_WRTE = 0x45545257
A_AUTH = 0x48545541
A_STLS = 0x534C5453

NAMES = {A_SYNC: "SYNC", A_CNXN: "CNXN", A_OPEN: "OPEN", A_OKAY: "OKAY", A_CLSE: "CLSE",
         A_WRTE: "WRTE", A_AUTH: "AUTH", A_STLS: "STLS"}

VERSION = 0x01000000
MAX_DATA = 256 * 1024          # what we announce; the TV's own limit (its CNXN arg1) caps every payload

AUTH_TOKEN = 1
AUTH_SIGNATURE = 2
AUTH_RSAPUBLICKEY = 3


class AdbError(Exception):
    """A failure the ADB link reported, in words meant for the user."""


class AdbConnectionLost(AdbError):
    """The TV closed or reset the connection in the middle of a command."""


class AdbRefused(AdbError):
    """The TV refused a stream (CLSE in answer to OPEN) or a sync request (FAIL)."""


class Message:
    __slots__ = ("command", "arg0", "arg1", "data")

    def __init__(self, command, arg0=0, arg1=0, data=b""):
        self.command, self.arg0, self.arg1, self.data = command, arg0, arg1, data

    def pack(self) -> bytes:
        data = self.data
        return HEADER.pack(self.command, self.arg0, self.arg1, len(data), sum(data) & 0xFFFFFFFF,
                           self.command ^ 0xFFFFFFFF) + data

    @property
    def name(self):
        return NAMES.get(self.command, f"0x{self.command:08x}")

    def __repr__(self):
        return f"<{self.name} {self.arg0} {self.arg1} {len(self.data)}B>"


def read_exact(sock: socket.socket, n: int) -> bytes:
    chunks = []
    while n:
        try:
            chunk = sock.recv(n)
        except socket.timeout:
            raise
        except OSError as e:
            raise AdbConnectionLost(f"the connection to the TV dropped ({e})") from e
        if not chunk:
            raise AdbConnectionLost("the TV closed the connection")
        chunks.append(chunk)
        n -= len(chunk)
    return b"".join(chunks)


def read_message(sock: socket.socket) -> Message:
    command, arg0, arg1, length, _checksum, magic = HEADER.unpack(read_exact(sock, HEADER.size))
    if magic != command ^ 0xFFFFFFFF:
        raise AdbError("the TV sent a frame that is not ADB (bad magic): is this an ADB port?")
    data = read_exact(sock, length) if length else b""
    return Message(command, arg0, arg1, data)


def send_message(sock: socket.socket, message: Message):
    try:
        sock.sendall(message.pack())
    except OSError as e:
        raise AdbConnectionLost(f"the connection to the TV dropped ({e})") from e
