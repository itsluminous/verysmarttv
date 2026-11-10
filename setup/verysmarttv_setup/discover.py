"""Find the TV on the network: an mDNS query for the ADB services, then a scan of port 5555 across this
computer's /24, then a typed address. And the steps to turn debugging on, per brand, when nothing answers.

The mDNS part is a plain UDP query to 224.0.0.251:5353 for `_adb-tls-connect._tcp.local` (Wireless debugging,
Android 11+) and `_adb._tcp.local` (adbd in TCP mode), with the answers parsed here: no zeroconf dependency.
"""
import concurrent.futures
import ipaddress
import json
import socket
import struct
import time

from . import paths

MDNS_GROUP, MDNS_PORT = "224.0.0.251", 5353
SERVICES = ("_adb-tls-connect._tcp.local", "_adb._tcp.local")
ADB_PORT = 5555
SCAN_THREADS = 64


class Found:
    """One TV candidate: where it answered, and what the answer said about it."""

    def __init__(self, host: str, port: int, name: str = "", source: str = ""):
        self.host, self.port, self.name, self.source = host, port, name, source

    @property
    def address(self):
        return f"{self.host}:{self.port}" if self.port != ADB_PORT else self.host

    def __repr__(self):
        return f"<Found {self.address} {self.name!r} via {self.source}>"

    def __eq__(self, other):
        return isinstance(other, Found) and (self.host, self.port) == (other.host, other.port)

    def __hash__(self):
        return hash((self.host, self.port))


# -- mDNS -------------------------------------------------------------------------------------------------

def encode_name(name: str) -> bytes:
    out = b""
    for label in name.strip(".").split("."):
        out += bytes([len(label)]) + label.encode()
    return out + b"\0"


def build_query(services=SERVICES) -> bytes:
    header = struct.pack("!HHHHHH", 0, 0, len(services), 0, 0, 0)
    body = b"".join(encode_name(s) + struct.pack("!HH", 12, 0x8001) for s in services)   # PTR, QU + IN
    return header + body


def read_name(data: bytes, offset: int):
    labels, jumped, end = [], False, None
    for _ in range(128):
        if offset >= len(data):
            raise ValueError("name runs past the packet")
        length = data[offset]
        if length == 0:
            offset += 1
            break
        if length & 0xC0 == 0xC0:
            pointer = struct.unpack("!H", data[offset:offset + 2])[0] & 0x3FFF
            if not jumped:
                end = offset + 2
            jumped = True
            offset = pointer
            continue
        labels.append(data[offset + 1:offset + 1 + length].decode("utf-8", "replace"))
        offset += 1 + length
    return ".".join(labels), (end if end is not None else offset)


def parse_response(data: bytes) -> list:
    """Found entries from one mDNS answer packet: PTR -> instance, SRV -> port + host, A -> address."""
    try:
        _id, _flags, qd, an, ns, ar = struct.unpack("!HHHHHH", data[:12])
        offset = 12
        for _ in range(qd):
            _, offset = read_name(data, offset)
            offset += 4
        records = []
        for _ in range(an + ns + ar):
            name, offset = read_name(data, offset)
            rtype, _cls, _ttl, rdlen = struct.unpack("!HHIH", data[offset:offset + 10])
            offset += 10
            rdata = data[offset:offset + rdlen]
            records.append((name, rtype, rdata, offset))
            offset += rdlen
    except (struct.error, ValueError, IndexError):
        return []
    instances, srv, addresses = {}, {}, {}
    for name, rtype, rdata, at in records:
        if rtype == 12 and name.lower() in SERVICES:
            instance, _ = read_name(data, at)
            instances[instance] = name.lower()
        elif rtype == 33:
            port = struct.unpack("!H", rdata[4:6])[0]
            target, _ = read_name(data, at + 6)
            srv[name] = (port, target)
        elif rtype == 1 and len(rdata) == 4:
            addresses.setdefault(name, socket.inet_ntoa(rdata))
    found = []
    for instance, service in instances.items():
        port, target = srv.get(instance, (None, None))
        host = addresses.get(target) if target else None
        if host is None and len(addresses) == 1:
            host = next(iter(addresses.values()))
        if host and port:
            label = instance.split(".")[0]
            found.append(Found(host, port, label, service.split(".")[0].lstrip("_")))
    return found


def mdns(timeout: float = 2.0) -> list:
    """TVs that answered the ADB service queries within `timeout` seconds."""
    found = []
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.settimeout(0.3)
        sock.bind(("", 0))
        sock.sendto(build_query(), (MDNS_GROUP, MDNS_PORT))
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                data, (host, _) = sock.recvfrom(9000)
            except socket.timeout:
                continue
            for entry in parse_response(data):
                if entry.host in ("0.0.0.0",):
                    entry.host = host
                if entry not in found:
                    found.append(entry)
        sock.close()
    except OSError:
        pass
    return found


# -- port scan --------------------------------------------------------------------------------------------

def local_ip():
    """This computer's address on the LAN (the one a packet to the Internet would leave from); None offline."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(("10.255.255.255", 1))
        ip = sock.getsockname()[0]
        sock.close()
        return ip
    except OSError:
        return None


def port_open(host: str, port: int = ADB_PORT, timeout: float = 1.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def scan(subnet: str = None, port: int = ADB_PORT, timeout: float = 1.0) -> list:
    """Hosts with `port` open across a /24 (this computer's by default), as Found entries."""
    if subnet is None:
        ip = local_ip()
        if not ip:
            return []
        subnet = str(ipaddress.ip_network(f"{ip}/24", strict=False))
    network = ipaddress.ip_network(subnet, strict=False)
    me = local_ip()
    hosts = [str(h) for h in network.hosts() if str(h) != me]
    found = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=SCAN_THREADS) as pool:
        for host, ok in zip(hosts, pool.map(lambda h: port_open(h, port, timeout), hosts)):
            if ok:
                found.append(Found(host, port, "", "scan"))
    return found


# -- memory of the last TV ---------------------------------------------------------------------------------

def remember(address: str, props: dict = None):
    data = {"address": address, "model": (props or {}).get("ro.product.model", ""), "when": int(time.time())}
    paths.last_tv_file().write_text(json.dumps(data), encoding="utf-8")


def last_tv():
    try:
        return json.loads(paths.last_tv_file().read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None


# -- enabling debugging ------------------------------------------------------------------------------------

GENERIC_STEPS = [
    "Settings > Device Preferences (or System) > About > press OK on \"Build\" seven times",
    "back to Device Preferences > Developer options > turn USB debugging on",
    "if there is a Network debugging or ADB debugging switch, turn that on too",
    "keep the TV on; when this computer connects, accept \"Allow USB debugging?\" on the TV",
]
BRAND_STEPS = {
    "xiaomi": ["Settings > Device Preferences > About > press OK on \"Build\" (Android TV build) seven times",
               "Settings > Device Preferences > Developer options > USB debugging on",
               "PatchWall sets: also Settings > Device Preferences > Developer options > ADB debugging (Network) on",
               "accept \"Allow USB debugging?\" on the TV when this computer connects"],
    "tcl": ["Settings > Device Preferences > About > press OK on \"Build\" seven times",
            "Developer options > USB debugging on; TCL also shows Network debugging: turn it on",
            "accept \"Allow USB debugging?\" on the TV when this computer connects"],
    "sony": ["Settings > Device Preferences > About > Build: press OK seven times",
             "Settings > Device Preferences > Developer options > USB debugging on, and Network debugging on",
             "accept the prompt on the TV when this computer connects"],
    "google": ["Settings > System > About > Android TV OS build: press OK seven times",
               "Settings > System > Developer options > Wireless debugging on, then pair once with Google's adb "
               "(`adb pair`) and run `adb tcpip 5555`: Chromecasts never open 5555 by themselves"],
    "amazon": ["Settings > My Fire TV > About > press Select on the device name seven times",
               "Settings > My Fire TV > Developer options > ADB debugging on",
               "Fire OS keeps its own launcher as Home; only the clean-up half applies"],
    "hisense": ["Settings > Device Preferences > About > Build: press OK seven times",
                "Developer options > USB debugging on, and Network debugging on where it is listed"],
    "philips": ["Settings > Android settings > Device Preferences > About > Build: press OK seven times",
                "Developer options > USB debugging on, then Network debugging on"],
}


def hints(brand: str = "") -> list:
    """The steps to turn on debugging, for the brand when it is one we know, else the generic ones."""
    key = (brand or "").strip().lower().split(" ")[0]
    return BRAND_STEPS.get(key, GENERIC_STEPS)
