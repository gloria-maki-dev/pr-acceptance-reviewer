#!/usr/bin/env python3
"""Check every citation in a set of verdicts against the diff it claims to come from.

A model can state, with total confidence, that something changed in a file that is not even in
the diff. This script is the part that does not take its word for it. For each verdict it looks
up the cited file, side and line number in the diff and checks that the line really contains the
quoted snippet. A citation that does not hold up turns its verdict into NOT_VERIFIED, and the
reason is reported.

Verdicts are JSON on stdin or in a file:

    {"results": [
      {"criterionId": "C1", "status": "MET", "explanation": "...",
       "evidence": {"file": "src/a.ts", "side": "ADDED", "lineNumber": 2, "snippet": "..."}}
    ]}

Usage:
    python3 verify_citations.py --diff changes.diff --verdicts verdicts.json
    python3 verify_citations.py --diff changes.diff --criteria criteria.txt --format markdown
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata

# Works even when Python is run isolated (-I), which drops the script's own directory.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from annotate_diff import InvalidDiff, normalise_path, parse_diff  # noqa: E402

STATUSES = ("MET", "NOT_MET", "NOT_VERIFIED")
SIDES = ("ADDED", "REMOVED", "CONTEXT")
LABELS = {"MET": "Met", "NOT_MET": "Not met", "NOT_VERIFIED": "Not verified"}

# A snippet shorter than this proves nothing: citing ";" would support any verdict at all.
MIN_SNIPPET_LENGTH = 4

ANNOTATION = re.compile(r"^\[(?:ADDED|REMOVED|CONTEXT) \d+]\s?")


def _normalise(text: str) -> str:
    """Collapse every kind of whitespace, including the no-break space used in JSX."""
    spaced = "".join(
        " " if unicodedata.category(c) == "Zs" or c.isspace() else c for c in text
    )
    return re.sub(r"\s+", " ", spaced).strip()


def _strip_markers(snippet: str) -> str:
    """Drop a leading "[ADDED 20] " annotation or a "+"/"-" diff marker."""
    trimmed = ANNOTATION.sub("", snippet.strip()).strip()
    return trimmed[1:] if trimmed[:1] in ("+", "-") else trimmed


def verify_evidence(evidence: dict | None, files: dict) -> str | None:
    """Return None when the citation holds up, or the reason it does not."""
    if evidence is None:
        return "the model concluded without citing any line of the diff"

    for field in ("file", "side", "lineNumber", "snippet"):
        if field not in evidence:
            return f"the citation is missing `{field}`"

    side = str(evidence["side"]).upper()
    if side not in SIDES:
        return f"`{evidence['side']}` is not a side of the diff (ADDED, REMOVED, CONTEXT)"

    path = normalise_path(str(evidence["file"]))
    if path not in files:
        return f"the diff does not contain the file `{evidence['file']}`"

    try:
        number = int(evidence["lineNumber"])
    except (TypeError, ValueError):
        return f"`{evidence['lineNumber']}` is not a line number"

    candidates = [line for line in files[path] if line.side == side and line.number == number]
    if not candidates:
        return f"`{path}` has no {side} line {number} in this diff"

    snippet = _normalise(_strip_markers(str(evidence["snippet"])))
    if len(snippet) < MIN_SNIPPET_LENGTH:
        return f"the snippet (`{evidence['snippet']}`) is too short to prove anything"

    if not any(snippet in _normalise(line.content) for line in candidates):
        return (
            f"the snippet does not match line {number} of `{path}`, "
            f"which reads: {candidates[0].content.strip()}"
        )
    return None


def review(diff_text: str, verdicts: dict, criteria: list[str] | None = None) -> list[dict]:
    files = parse_diff(diff_text)
    results = verdicts.get("results")
    if not isinstance(results, list):
        raise ValueError("The verdicts JSON has no `results` list.")

    by_id: dict[str, dict] = {}
    for item in results:
        key = str(item.get("criterionId") or item.get("criterionIndex") or "").upper()
        if key and not key.startswith("C"):
            key = "C" + key
        if key:
            by_id[key] = item

    ids = [f"C{i + 1}" for i in range(len(criteria))] if criteria else list(by_id)
    checked = []
    for index, key in enumerate(ids):
        item = by_id.get(key)
        criterion = criteria[index] if criteria else key
        if item is None:
            checked.append({
                "criterionId": key, "criterion": criterion, "status": "NOT_VERIFIED",
                "explanation": None, "evidence": None,
                "verificationNote": "The model did not return a verdict for this criterion.",
            })
            continue

        status = str(item.get("status", "")).upper()
        if status not in STATUSES:
            checked.append({
                "criterionId": key, "criterion": criterion, "status": "NOT_VERIFIED",
                "explanation": None, "evidence": item.get("evidence"),
                "verificationNote": f"`{item.get('status')}` is not a valid status.",
            })
            continue

        evidence = item.get("evidence")
        if status == "NOT_VERIFIED" and evidence is None:
            problem = None
        else:
            problem = verify_evidence(evidence, files)

        checked.append({
            "criterionId": key,
            "criterion": criterion,
            "status": "NOT_VERIFIED" if problem else status,
            "explanation": item.get("explanation"),
            "evidence": None if problem else evidence,
            "verificationNote": problem[0].upper() + problem[1:] + "." if problem else None,
        })
    return checked


def to_markdown(checked: list[dict]) -> str:
    out = ["| # | Criterion | Verdict | Evidence |", "|---|---|---|---|"]
    for index, row in enumerate(checked, start=1):
        evidence = row["evidence"]
        cite = (
            f"`{evidence['file']}:{evidence['lineNumber']}` ({evidence['side'].lower()})"
            if evidence
            else "—"
        )
        out.append(f"| {index} | {row['criterion']} | **{LABELS[row['status']]}** | {cite} |")
    out.append("")
    for index, row in enumerate(checked, start=1):
        if row["verificationNote"]:
            out.append(f"{index}. Not verified. {row['verificationNote']}")
        elif row["explanation"]:
            out.append(f"{index}. {row['explanation']}")
    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--diff", required=True, help="The diff the citations must match.")
    parser.add_argument("--verdicts", help="Verdicts JSON. Reads stdin when omitted.")
    parser.add_argument("--criteria", help="Acceptance criteria, one per line.")
    parser.add_argument("--format", choices=("json", "markdown"), default="json")
    args = parser.parse_args()

    diff_text = open(args.diff, encoding="utf-8").read()
    raw = open(args.verdicts, encoding="utf-8").read() if args.verdicts else sys.stdin.read()
    criteria = None
    if args.criteria:
        criteria = [
            line.strip()
            for line in open(args.criteria, encoding="utf-8").read().split("\n")
            if line.strip()
        ]

    try:
        checked = review(diff_text, json.loads(raw), criteria)
    except (InvalidDiff, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1

    if args.format == "markdown":
        print(to_markdown(checked))
    else:
        print(json.dumps({"results": checked}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
