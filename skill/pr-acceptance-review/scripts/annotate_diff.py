#!/usr/bin/env python3
"""Annotate a unified diff so every changed line can be cited unambiguously.

Each line is printed with the file it belongs to, which side of the diff it is on, and its
line number:

    FILE: src/orders/exportCsv.ts
    [ADDED 1] export const exportButtonLabel = 'Download CSV';
    [REMOVED 1] export const exportButtonLabel = 'Export';
    [CONTEXT 4] export function exportCsv(orders: Order[]): string {

ADDED and CONTEXT lines are numbered against the new file, REMOVED lines against the old one.

Usage:
    python3 annotate_diff.py changes.diff
    git diff main...HEAD | python3 annotate_diff.py
"""
from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass

HUNK_HEADER = re.compile(r"^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@")
OCTAL = re.compile(r"^[0-7]{3}$")


class InvalidDiff(Exception):
    """The text is not a unified diff we can read."""


@dataclass(frozen=True)
class DiffLine:
    side: str  # ADDED | REMOVED | CONTEXT
    number: int
    content: str


def normalise_path(path: str) -> str:
    cleaned = (path or "").strip().replace("\\", "/")
    if cleaned.startswith(("a/", "b/")):
        cleaned = cleaned[2:]
    while cleaned.startswith("./"):
        cleaned = cleaned[2:]
    return cleaned


def _unquote(quoted: str) -> str:
    """Undo the octal escaping git uses for non-ASCII paths when core.quotePath is on."""
    out = bytearray()
    i = 0
    while i < len(quoted):
        char = quoted[i]
        if char == "\\" and i + 3 < len(quoted) and OCTAL.match(quoted[i + 1 : i + 4]):
            out.append(int(quoted[i + 1 : i + 4], 8))
            i += 4
        elif char == "\\" and i + 1 < len(quoted):
            out.extend(quoted[i + 1].encode("utf-8"))
            i += 2
        else:
            out.extend(char.encode("utf-8"))
            i += 1
    return out.decode("utf-8", errors="replace")


def _strip_path_prefix(raw: str) -> str:
    path = raw.strip().split("\t")[0].strip()
    if len(path) >= 2 and path.startswith('"') and path.endswith('"'):
        path = _unquote(path[1:-1])
    if path.startswith(("a/", "b/")):
        path = path[2:]
    return path


def _path_from_git_header(line: str) -> str | None:
    rest = line[len("diff --git ") :].strip()
    split = rest.rfind(" b/")
    return _strip_path_prefix(rest[split + 1 :]) if split > 0 else None


def parse_diff(text: str) -> dict[str, list[DiffLine]]:
    """Return {file path: [DiffLine, ...]} for a unified diff.

    The hunk header declares how many lines each side contributes, and we count them down. That
    matters: inside a hunk, a removed line whose content starts with "-- " (a SQL comment, say)
    is written "--- ..." and looks exactly like a file header. Without the countdown the rest of
    the hunk would be dropped in silence.
    """
    if not text or not text.strip():
        raise InvalidDiff("The diff is empty.")

    files: dict[str, list[DiffLine]] = {}
    path: str | None = None
    pending: list[DiffLine] = []
    old_line = new_line = 0
    old_left = new_left = 0

    def flush() -> None:
        nonlocal pending
        if path is not None and pending:
            files.setdefault(normalise_path(path), []).extend(pending)
        pending = []

    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines.pop()

    for raw in lines:
        line = raw[:-1] if raw.endswith("\r") else raw
        inside_hunk = old_left > 0 or new_left > 0

        if not inside_hunk:
            if line.startswith("diff --git "):
                flush()
                path = _path_from_git_header(line)
            elif line.startswith("--- "):
                flush()
                old_path = _strip_path_prefix(line[4:])
                path = None if old_path in ("/dev/null", "dev/null") else old_path
            elif line.startswith("+++ "):
                new_path = _strip_path_prefix(line[4:])
                if new_path not in ("/dev/null", "dev/null"):
                    path = new_path
            else:
                header = HUNK_HEADER.match(line)
                if header:
                    old_line = int(header.group(1))
                    old_left = int(header.group(2) or 1)
                    new_line = int(header.group(3))
                    new_left = int(header.group(4) or 1)
            continue

        if line.startswith("\\"):
            continue  # "\ No newline at end of file" is not a line of the file.

        if line.startswith("+"):
            if path:
                pending.append(DiffLine("ADDED", new_line, line[1:]))
            new_line += 1
            new_left = max(0, new_left - 1)
        elif line.startswith("-"):
            if path:
                pending.append(DiffLine("REMOVED", old_line, line[1:]))
            old_line += 1
            old_left = max(0, old_left - 1)
        elif line.startswith(" ") or line == "":
            if path:
                pending.append(DiffLine("CONTEXT", new_line, line[1:] if line else ""))
            old_line += 1
            new_line += 1
            old_left = max(0, old_left - 1)
            new_left = max(0, new_left - 1)
        else:
            old_left = new_left = 0  # Junk where a hunk line should be: close the hunk.

    flush()

    if not files:
        raise InvalidDiff(
            "No changed files found. Pass the output of `git diff`, including its "
            "`--- a/file`, `+++ b/file` and `@@` headers."
        )
    return files


def annotate(text: str) -> str:
    out: list[str] = []
    for path, lines in parse_diff(text).items():
        out.append(f"FILE: {path}")
        out.extend(f"[{line.side} {line.number}] {line.content}" for line in lines)
        out.append("")
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("diff", nargs="?", help="Diff file. Reads stdin when omitted.")
    args = parser.parse_args()

    text = open(args.diff, encoding="utf-8").read() if args.diff else sys.stdin.read()
    try:
        sys.stdout.write(annotate(text))
    except InvalidDiff as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
