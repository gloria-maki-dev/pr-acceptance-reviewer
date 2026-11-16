#!/usr/bin/env python3
"""Tests for the skill's scripts. Run: python3 -m unittest discover -s scripts -v"""
from __future__ import annotations

import unittest

from annotate_diff import InvalidDiff, annotate, parse_diff
from verify_citations import review

CSV_DIFF = """diff --git a/src/orders/exportCsv.ts b/src/orders/exportCsv.ts
--- a/src/orders/exportCsv.ts
+++ b/src/orders/exportCsv.ts
@@ -1,5 +1,5 @@
-export const exportButtonLabel = 'Export';
-const separator = ';';
+export const exportButtonLabel = 'Download CSV';
+const separator = ',';

 export function exportCsv(orders: Order[]): string {
   const header = ['id', 'customer', 'total'].join(separator);
"""

CRITERIA = [
    "The exportButtonLabel constant must have the value “Download CSV”.",
    "The CSV separator must still be the semicolon.",
    "The export endpoint must require authentication.",
]


def cite(path, side, number, snippet):
    return {"file": path, "side": side, "lineNumber": number, "snippet": snippet}


class AnnotateDiffTest(unittest.TestCase):
    def test_numbers_each_side_against_its_own_file(self):
        files = parse_diff(CSV_DIFF)
        lines = files["src/orders/exportCsv.ts"]
        added = [(l.number, l.content) for l in lines if l.side == "ADDED"]
        removed = [(l.number, l.content) for l in lines if l.side == "REMOVED"]
        self.assertEqual([1, 2], [n for n, _ in added])
        self.assertEqual([1, 2], [n for n, _ in removed])
        self.assertIn("Download CSV", added[0][1])
        self.assertEqual("const separator = ',';", added[1][1])

    def test_content_that_looks_like_a_header_does_not_truncate_the_hunk(self):
        diff = (
            "--- a/schema.sql\n+++ b/schema.sql\n@@ -1,4 +1,4 @@\n"
            " CREATE TABLE t (\n--- order number\n+-- order id\n   id INT\n"
        )
        lines = parse_diff(diff)["schema.sql"]
        self.assertEqual(4, len(lines))
        self.assertEqual("-- order number", next(l.content for l in lines if l.side == "REMOVED"))

    def test_deleted_file_after_another_file_is_not_merged_into_it(self):
        diff = (
            "--- a/one.txt\n+++ b/one.txt\n@@ -1,1 +1,1 @@\n-a\n+b\n"
            "--- a/gone.txt\n+++ /dev/null\n@@ -1,2 +0,0 @@\n-x\n-y\n"
        )
        files = parse_diff(diff)
        self.assertEqual(["one.txt", "gone.txt"], list(files))
        self.assertEqual(2, len(files["gone.txt"]))

    def test_same_path_twice_is_merged(self):
        diff = (
            "diff --git a/x.ts b/x.ts\n--- a/x.ts\n+++ b/x.ts\n@@ -1,1 +1,1 @@\n-a\n+b\n"
            "diff --git a/x.ts b/x.ts\n--- a/x.ts\n+++ b/x.ts\n@@ -50,1 +50,1 @@\n-tea\n+water\n"
        )
        files = parse_diff(diff)
        self.assertEqual(1, len(files))
        self.assertEqual([1, 50], [l.number for l in files["x.ts"] if l.side == "ADDED"])

    def test_quoted_non_ascii_path(self):
        diff = (
            'diff --git "a/caf\\303\\251.txt" "b/caf\\303\\251.txt"\n'
            '--- "a/caf\\303\\251.txt"\n+++ "b/caf\\303\\251.txt"\n@@ -1,1 +1,1 @@\n-one\n+two\n'
        )
        self.assertEqual(["café.txt"], list(parse_diff(diff)))

    def test_rejects_text_that_is_not_a_diff(self):
        with self.assertRaises(InvalidDiff):
            parse_diff("just some prose\nwith no diff in it")

    def test_annotated_output_carries_file_side_and_number(self):
        out = annotate(CSV_DIFF)
        self.assertIn("FILE: src/orders/exportCsv.ts", out)
        self.assertIn("[ADDED 2] const separator = ',';", out)
        self.assertIn("[REMOVED 2] const separator = ';';", out)


class VerifyCitationsTest(unittest.TestCase):
    def test_csv_example_returns_the_three_verdicts(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MET", "explanation": "Label updated.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 1, "exportButtonLabel = 'Download CSV'")},
            {"criterionId": "C2", "status": "NOT_MET", "explanation": "Separator became a comma.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 2, "const separator = ','")},
            {"criterionId": "C3", "status": "NOT_VERIFIED",
             "explanation": "The endpoint is not in the diff.", "evidence": None},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA)

        self.assertEqual(["MET", "NOT_MET", "NOT_VERIFIED"], [r["status"] for r in checked])
        self.assertEqual(2, checked[1]["evidence"]["lineNumber"])
        self.assertIsNone(checked[1]["verificationNote"])
        self.assertIsNone(checked[2]["evidence"])

    def test_invented_line_number_is_downgraded_and_the_citation_dropped(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MET", "explanation": "Label updated.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 417, "Download CSV")},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA[:1])

        self.assertEqual("NOT_VERIFIED", checked[0]["status"])
        self.assertIsNone(checked[0]["evidence"])
        self.assertIn("417", checked[0]["verificationNote"])

    def test_citation_to_a_file_outside_the_diff_is_rejected(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MET", "explanation": "From another ticket.",
             "evidence": cite("src/components/CartSummary.tsx", "ADDED", 20, "Estimated total")},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA[:1])

        self.assertEqual("NOT_VERIFIED", checked[0]["status"])
        self.assertIn("CartSummary.tsx", checked[0]["verificationNote"])

    def test_a_one_character_snippet_proves_nothing(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MET", "explanation": "Sure.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 2, ";")},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA[:1])

        self.assertEqual("NOT_VERIFIED", checked[0]["status"])
        self.assertIn("too short", checked[0]["verificationNote"])

    def test_missing_verdict_becomes_not_verified_without_blocking_the_rest(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MET", "explanation": "Label updated.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 1, "Download CSV")},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA)

        self.assertEqual(3, len(checked))
        self.assertEqual("MET", checked[0]["status"])
        self.assertEqual("NOT_VERIFIED", checked[1]["status"])
        self.assertIn("did not return a verdict", checked[1]["verificationNote"])

    def test_unknown_status_only_sinks_its_own_criterion(self):
        verdicts = {"results": [
            {"criterionId": "C1", "status": "MAYBE", "explanation": "Who knows."},
            {"criterionId": "C2", "status": "NOT_MET", "explanation": "Separator changed.",
             "evidence": cite("src/orders/exportCsv.ts", "ADDED", 2, "const separator = ','")},
        ]}

        checked = review(CSV_DIFF, verdicts, CRITERIA[:2])

        self.assertEqual("NOT_VERIFIED", checked[0]["status"])
        self.assertIn("MAYBE", checked[0]["verificationNote"])
        self.assertEqual("NOT_MET", checked[1]["status"])

    def test_snippet_may_carry_the_annotation_or_the_diff_marker(self):
        for snippet in ("[ADDED 2] const separator = ','", "+const separator = ','"):
            verdicts = {"results": [
                {"criterionId": "C1", "status": "NOT_MET", "explanation": "Changed.",
                 "evidence": cite("src/orders/exportCsv.ts", "ADDED", 2, snippet)},
            ]}
            checked = review(CSV_DIFF, verdicts, CRITERIA[:1])
            self.assertEqual("NOT_MET", checked[0]["status"], snippet)


if __name__ == "__main__":
    unittest.main()
