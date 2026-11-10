"""Debloat lists: the format, the names it must never hold, and the batches a list is applied in.

A list is one package name per line, `#` comments allowed, a trailing `# note` allowed. Every name ends up
inside a command run by the TV's shell, so anything that is not a bare package name stops the run before the
TV is touched. A package the user deselected before applying is kept in the list's local copy
(`~/.verysmarttv/lists/<slug>-edited.txt`, DESIGN.md §17.3) as `# off: <name>`, so the line can be toggled
back on and the copy still reads as the community list it came from.
"""
import re
from pathlib import Path

from .probe import PACKAGE_RE

# Core Android packages: with one of these disabled, Android itself can stop booting. A Mi TV Stick never
# came back after a list took the permission controller, package installer, ext services, module metadata
# and the mainline overlays. No switch overrides this. The content providers in CORE_OK are the exception:
# tested lists turn them off and no TV feature uses them. The TV middleware is guarded the same way
# (MediaTek's MAL, TV service, agent and input service: a Haier set restarted in a loop with com.cltv.mal
# off), the SDK sandbox, and com.android.location.fused (a TCL boot-looped into recovery with only it off).
CORE_RE = re.compile(r"^(android|android\.ext\.(services|shared)|com\.android\.sdksandbox|com\.android\.location\.fused"
                     r"|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings"
                     r"|com\.android\.tv\.settings"
                     r"|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)"
                     r"|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*"
                     r"|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$")
CORE_OK = re.compile(r"^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$")
# Factory, service-menu and board self-test packages (com.duokan.factorytest, mediatek.factorymenu.ui,
# com.cvte.tv.hkcfac, com.tcl.FactoryMode ...). A list may disable one, and some must (the Mi TV 4A's
# factorytest claims HOME), but the TV's service menu and the self-tests behind it then stop working until
# `setup restore` re-enables them, so the plan says so and needs a typed yes (ADR-043). None is ever started.
FACTORY_RE = re.compile(r"factory|hkcfac", re.I)
BATCH_MARK = re.compile(r"^\s*#\s*-*\s*Batch\b", re.I)
OFF_MARK = "# off: "
DEFAULT_BATCH = 8


class ListError(ValueError):
    """The list cannot be used; the message names the line."""


def core_packages(names) -> list:
    """The names a list holds that must never be disabled, in list order."""
    return [n for n in names if CORE_RE.match(n) and not CORE_OK.match(n)]


def factory_packages(names) -> list:
    """The factory and service-menu packages a list holds, in list order."""
    return [n for n in names if FACTORY_RE.search(n)]


def parse(text: str, name: str = "list") -> list:
    """Package names from a list's text, in order; a BOM and CRLF are dropped. Raises ListError."""
    if text.startswith("\ufeff"):
        text = text[1:]
    names = []
    for number, line in enumerate(text.splitlines(), 1):
        body = line.split("#", 1)[0].strip()
        if not body:
            continue
        word = body.split()[0]
        if not PACKAGE_RE.fullmatch(word):
            raise ListError(f"{name}:{number}: not a package name: {word!r}")
        if word not in names:
            names.append(word)
    if not names:
        raise ListError(f"{name}: no package names in it (every line is a comment or blank)")
    return names


def read_list(path) -> list:
    try:
        text = Path(path).read_text(encoding="utf-8-sig", errors="replace")
    except OSError as e:
        raise ListError(f"could not read {path} ({e.strerror or e})") from e
    return parse(text, str(path))


def entries(text: str) -> list:
    """Every package line, active or deselected, as (line number, name, active), in order. The numbers are
    the ones the user toggles with."""
    found = []
    for number, line in enumerate(text.lstrip("\ufeff").splitlines(), 1):
        active = True
        if line.startswith(OFF_MARK):
            line, active = line[len(OFF_MARK):], False
        body = line.split("#", 1)[0].strip()
        if body and PACKAGE_RE.fullmatch(body.split()[0]):
            found.append((number, body.split()[0], active))
    return found


def toggle(text: str, positions) -> str:
    """The text with the entries at the given 1-based positions (as `entries` numbers them) flipped between
    active and `# off: `. Raises ListError for a position the list does not have."""
    all_entries = entries(text)
    lines = text.lstrip("\ufeff").split("\n")
    for position in positions:
        if not 1 <= position <= len(all_entries):
            raise ListError(f"no package number {position}: the list has {len(all_entries)}")
        number, _, active = all_entries[position - 1]
        line = lines[number - 1]
        lines[number - 1] = OFF_MARK + line if active else line[len(OFF_MARK):]
    return "\n".join(lines)


def header(text: str) -> str:
    """The comment block at the top of a list, as plain text without the `#`."""
    lines = []
    for line in text.lstrip("\ufeff").splitlines():
        if BATCH_MARK.match(line) or line.startswith(OFF_MARK):
            break
        if line.startswith("#"):
            lines.append(line[1:].strip())
        elif line.strip():
            break
        elif lines:
            lines.append("")
    return "\n".join(lines).strip()


def notes(text: str) -> dict:
    """The trailing `# note` of each package line, by package."""
    found = {}
    for line in text.lstrip("\ufeff").splitlines():
        body, _, note = line.partition("#")
        body = body.strip()
        if body and PACKAGE_RE.fullmatch(body.split()[0]):
            found[body.split()[0]] = note.strip()
    return found


def batches(text: str, names: list, size: int = DEFAULT_BATCH) -> list:
    """The list's packages in the batches they are applied in: the list's own `# ---- Batch` sections when it
    has them, else groups of `size` in order. Only names in `names` (the ones a guard let through) appear."""
    wanted = set(names)
    if any(BATCH_MARK.match(line) for line in text.splitlines()):
        groups, current = [], []
        for line in text.lstrip("\ufeff").splitlines():
            if BATCH_MARK.match(line):
                if current:
                    groups.append(current)
                current = []
                continue
            body = line.split("#", 1)[0].strip()
            if body and body.split()[0] in wanted:
                current.append(body.split()[0])
        if current:
            groups.append(current)
        groups = [g for g in groups if g]
        if groups:
            return groups
    ordered = [n for n in names]
    return [ordered[i:i + size] for i in range(0, len(ordered), size)]


def commented_out_but_listed(lines) -> list:
    """(line number, package) for each package commented out below the first package line that an active
    line still names: the active line wins, so the comment's intent is lost."""
    comment_entry = re.compile(r"^\s*#\s*([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)\s*(?:#.*)?$")
    active, first = set(), None
    for n, line in enumerate(lines, 1):
        body = line.split("#", 1)[0].strip()
        if body:
            active.add(body.split()[0])
            first = first or n
    if first is None:
        return []
    found = []
    for n, line in enumerate(lines[first:], first + 1):
        m = comment_entry.match(line)
        if m and m.group(1) in active:
            found.append((n, m.group(1)))
    return found
