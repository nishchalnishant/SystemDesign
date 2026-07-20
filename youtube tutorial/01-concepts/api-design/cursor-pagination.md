---
id: cursor-pagination
tags: [api-design, databases, pagination]
confidence: 3
last-rehearsed: 2026-07-20
source: 3. API Design — "Pagination"
---
# Cursor pagination

**Claim in one sentence.** Offset pagination fails twice — the database scans and discards every skipped row, and concurrent inserts shift the window so users see duplicates or gaps — and a cursor encoding a stable sort key has neither problem.

## Why it happens

`OFFSET 100000 LIMIT 20` does not seek. The database produces 100,020 rows and throws away 100,000 of them, so page cost grows linearly with page number. Deep pages on a large table are slow by construction, not by misconfiguration.

The correctness failure is worse and quieter. Offsets are positions in a result set that is still changing. Insert a row at the head between page 1 and page 2 and everything shifts down one — the reader sees the last item of page 1 again at the top of page 2. Delete instead, and an item is skipped entirely and never rendered.

## The shape

```
GET /v1/feed?limit=20&cursor=eyJpZCI6MTIzfQ==

{ "items": [...], "next_cursor": "eyJpZCI6MTQzfQ==" }
```

The cursor is an opaque encoding of the sort key — `WHERE id < 123 ORDER BY id DESC LIMIT 20`. That's an index seek, so page 5,000 costs what page 1 costs. And because it names a *row*, not a position, inserts elsewhere in the table don't move it.

Keep it opaque (base64) so clients can't construct one and you can change the internal shape later.

## What you say in an interview

> "Cursor, not offset, for anything feed-shaped. Offset makes the database scan and discard everything it skips, so deep pages degrade linearly — and inserts during paging shift the window, so users see duplicates. A cursor encodes the last sort key and becomes an index seek, so page cost is flat and it's stable under concurrent writes."

## Trade-offs

| | Offset | Cursor |
|---|---|---|
| Deep-page cost | O(offset) | O(1) index seek |
| Stable under writes | No | Yes |
| Jump to page N | Yes | **No** — sequential only |
| Total page count | Easy | Needs a separate count |

The cost is real: cursors can't do "jump to page 47." That's fine for feeds and wrong for an admin table with numbered pages, which is the honest place to keep offset.

## Probes you should survive

- *"When is offset actually fine?"* → Small bounded result sets, admin UIs with page numbers, anything where the data isn't changing under the reader.
- *"What goes in the cursor?"* → The sort key of the last row. If the sort isn't unique, a tiebreaker too — `(created_at, id)` — or rows with identical timestamps get skipped at page boundaries.
- *"Why base64 it?"* → To keep it opaque. A client that parses your cursor makes its internals part of your public contract.
- *"How do you show a total count?"* → You usually don't. An approximate count or "load more" is the normal answer; an exact count means the scan you were avoiding.

## Related

[protocol-selection](./protocol-selection.md) · [idempotency-keys](./idempotency-keys.md) · [api-versioning](./api-versioning.md)
