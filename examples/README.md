# Demo examples

Fictional ticket **SHOP-412 — Clarify the cart amount**, from an online store.

These three files are the single source of the examples: the web demo imports them with `?raw`
and the backend tests read them from disk, so the screen and the tests cannot drift apart.

| File | What it holds |
|---|---|
| `acceptance-criteria.txt` | The ticket's two acceptance criteria, one per line. |
| `diff-with-bug.diff` | Changes "Total" in `CartSummary.tsx` **and also** in `OrderHistory.tsx`. Expected result: criterion 1 **Met**, criterion 2 **Not met**, citing `OrderHistory.tsx`. |
| `diff-fixed.diff` | Changes "Total" in `CartSummary.tsx` only. Expected result: both criteria **Met**. |
