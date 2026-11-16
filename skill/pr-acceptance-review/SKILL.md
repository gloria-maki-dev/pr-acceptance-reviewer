---
name: pr-acceptance-review
description: Use when deciding whether a code change satisfies a ticket's acceptance criteria - reviewing a pull request, a git diff, or a branch against a list of requirements, or answering "does this PR do what the ticket asked?". Returns Met / Not met / Not verified per criterion, each backed by a citation checked against the diff.
---

# PR acceptance review

Decide whether a diff satisfies each acceptance criterion of a ticket, and back every verdict
with a line of the diff that someone else can check in a second.

## Why the citation matters

Asking a model "does this diff meet the ticket?" is easy. Believing the answer is the hard part:
a confident paragraph about a file that is not even in the diff reads exactly like a correct one.

So a verdict here is a **proposal, not a result**. You cite file, side, line number and snippet,
and `verify_citations.py` looks that line up in the diff. If it is not there, the verdict drops
to **Not verified** and says why. Do not skip the script and hand-check: that is the whole point
of the skill.

## Verdicts

| Verdict | Meaning |
|---|---|
| `MET` | The diff satisfies the criterion, and you can point at the line that proves it. |
| `NOT_MET` | The diff violates the criterion, and you can point at the line that proves it. |
| `NOT_VERIFIED` | The diff does not give enough context to decide, or the citation did not hold up. |

## Steps

### 1. Get the diff and the criteria

Write the acceptance criteria to a file, one per line. Then capture the diff:

```bash
git diff main...HEAD > /tmp/review.diff          # a branch against its base
git diff --staged > /tmp/review.diff             # what is about to be committed
gh pr diff 42 > /tmp/review.diff                 # a GitHub pull request
```

Use `main...HEAD` (three dots) so you see what the branch added, not what `main` moved on to.
Replace `main` with the real base branch.

### 2. Annotate the diff

```bash
python3 scripts/annotate_diff.py /tmp/review.diff
```

Every line comes back tagged with its file, side and line number:

```
FILE: src/orders/exportCsv.ts
[REMOVED 1] export const exportButtonLabel = 'Export';
[ADDED 1] export const exportButtonLabel = 'Download CSV';
[CONTEXT 4] export function exportCsv(orders: Order[]): string {
```

`ADDED` and `CONTEXT` are numbered against the new file, `REMOVED` against the old one. Read the
annotated output, not the raw diff: the numbers you cite must be these.

### 3. Judge each criterion

Write one verdict per criterion to a JSON file:

```json
{"results": [
  {"criterionId": "C1", "status": "MET",
   "explanation": "exportButtonLabel now reads 'Download CSV'.",
   "evidence": {"file": "src/orders/exportCsv.ts", "side": "ADDED", "lineNumber": 1,
                "snippet": "export const exportButtonLabel = 'Download CSV';"}},
  {"criterionId": "C2", "status": "NOT_VERIFIED",
   "explanation": "The export endpoint is not in the diff.", "evidence": null}
]}
```

Rules, in order of importance:

1. **One verdict per criterion**, in order: `C1`, `C2`, `C3`… Never skip one. If you have nothing
   to say about it, that is `NOT_VERIFIED`, not a missing entry.
2. **`MET` and `NOT_MET` require a citation.** No line to point at means `NOT_VERIFIED`.
3. **Copy citations from the annotated output; never reconstruct them.** `snippet` is text copied
   literally from that line. A citation that does not match is thrown out, so when in doubt use
   `NOT_VERIFIED` rather than guessing.
4. **The diff is all that changed.** Do not assume anything about the rest of a file or about
   files that do not appear. If a criterion says something must *not* change and it is nowhere in
   the diff, it is met.
5. **`explanation` is one concrete sentence** about what you saw. Not a restatement of the
   criterion, and not about yourself.

### 4. Verify, then report

```bash
python3 scripts/verify_citations.py \
  --diff /tmp/review.diff \
  --verdicts /tmp/verdicts.json \
  --criteria /tmp/criteria.txt \
  --format markdown
```

Report what the script returns, not what you wrote in step 3. If it downgraded a verdict, say so
and show its reason — **do not show a citation it rejected, and do not keep the explanation that
rested on it.** An explanation that sounds right next to a citation that does not exist is worse
than saying nothing.

## Worked example

Two criteria, and a diff that meets one and breaks the other:

```
Criterion C1: The cart summary must read "Estimated total" instead of "Total".
Criterion C2: Only the cart summary changes. Order history still reads "Total".
```

```diff
--- a/src/components/CartSummary.tsx
+++ b/src/components/CartSummary.tsx
@@ -18,3 +18,3 @@
-        <span className="cart-summary__label">Total</span>
+        <span className="cart-summary__label">Estimated total</span>
         <span className="cart-summary__amount">{formatPrice(total)}</span>
--- a/src/components/OrderHistory.tsx
+++ b/src/components/OrderHistory.tsx
@@ -31,3 +31,3 @@
-          <td>Total: {formatPrice(order.total)}</td>
+          <td>Estimated total: {formatPrice(order.total)}</td>
           <td>{order.status}</td>
```

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | Cart summary reads "Estimated total" | **Met** | `src/components/CartSummary.tsx:18` (added) |
| 2 | Order history still reads "Total" | **Not met** | `src/components/OrderHistory.tsx:31` (added) |

## Notes

- The scripts need Python 3.9 or newer and nothing else. Paths are relative to this file's
  directory.
- `annotate_diff.py` reads stdin, so `git diff main...HEAD | python3 scripts/annotate_diff.py`
  works without a temporary file.
- Unified diffs that are not plain `git diff` output are handled too: renames, new and deleted
  files, `\ No newline at end of file`, CRLF, and quoted non-ASCII paths.
