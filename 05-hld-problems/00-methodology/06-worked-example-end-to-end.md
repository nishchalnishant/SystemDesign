> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** All 5 methodology steps run back-to-back on one prompt — "Design a read-it-later bookmarking service" (Pocket/Instapaper-like) — a problem not solved elsewhere in `05-hld-problems/`, so the reasoning can be followed without recalling a memorized answer.
>
> **Key ideas:**
> - This doc collects the same worked example threaded through Steps 1-5 into one continuous read — it's not new content, it's proof the process composes into a full design.
> - Every artifact shown (requirements list, capacity numbers, API/schema, architecture diagram, deep dive) is the literal deliverable each step doc says to produce.
> - Cover the final diagram and try to redo Steps 2-5 yourself from just the Step 1 requirements list before reading further — that's the actual practice rep.
>
> **Key takeaway:** Nothing in this design was pattern-matched from memory. Every decision traces back to one line in the Step 1 requirements list — that traceability is what makes the process work on a prompt you've genuinely never seen.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, worked-example, architecture]
---
# Step 6 — Worked Example, End to End

**Prompt:** "Design a Pocket/Instapaper-like service — users save article URLs to read later, with offline access."

Try Steps 2-5 yourself from the requirements list below before reading further.

---

## Step 1 — Requirements & Scope

1. User saves a URL; system fetches and stores a cleaned, readable copy of the content.
2. User lists their saved articles (paginated, most-recent-first).
3. User marks an article as read/unread; state syncs across the user's devices within a few seconds (eventual consistency acceptable).
4. User can view article content offline-first (from the stored copy, not a live fetch of the original site).
5. Scale: 10M users, ~50M saves/day, ~200 KB average stored size per article.
6. Out of scope: sharing, comments, full-text search across articles (v1).
7. Multi-region: not specified — assume single-region for v1, note the extension in the deep dive.

Full reasoning: [01-requirements-and-scope.md](01-requirements-and-scope.md).

---

## Step 2 — Capacity Estimation

- **Write QPS:** 50M saves/day ÷ 100,000 sec/day ≈ 500 writes/sec average, ~1,500 peak.
- **Read QPS:** ~3x write volume (browsing + read-state updates) ≈ 1,500 avg, ~4,500 peak.
- **Storage:** 50M saves/day × 200 KB ≈ 10 TB/day of article content — large enough that object storage, not a relational blob column, is required.
- **Bandwidth:** ~100 MB/sec sustained ingest, ~300 MB/sec sustained read.
- **Tier reading:** Load-balanced horizontally-scaled app tier + object storage + cache/CDN for reads — not yet at a scale requiring sharding of the metadata DB or a queue for the metadata write path itself (though the fetch-and-clean step is a different story — see Step 4).

Full reasoning: [02-capacity-estimation.md](02-capacity-estimation.md).

---

## Step 3 — API & Data Model

| Requirement | Endpoint |
|---|---|
| 1 | `POST /articles {url}` → `202 {articleId, status: "processing"}` |
| 2 | `GET /articles?cursor=&limit=20` → `[{id, title, url, savedAt, readState}]` |
| 3 | `PATCH /articles/{id} {readState}` |
| 4 | `GET /articles/{id}/content` → cleaned HTML/text |

Data model: `User {id, email}`, `Article {id, userId, url, title, savedAt, readState, contentRef}`, `ArticleContent` in object storage keyed by `contentRef`. `POST` returning `202` is the first signal that fetch-and-clean is async work, not inline request handling.

Full reasoning: [03-api-and-data-model.md](03-api-and-data-model.md).

---

## Step 4 — Deriving the Architecture

| Symptom | Component |
|---|---|
| ~4,500 QPS peak | Load balancer + horizontally scaled app tier |
| Reads >> writes, content immutable once cleaned | Cache in front of metadata DB; CDN for serving cleaned content |
| 10 TB/day of content | Object storage for `ArticleContent` |
| Fetch-and-clean is slow/unreliable, shouldn't block the API response | Message queue + worker pool |
| Read-state sync tolerates eventual consistency | No synchronous cross-replica consistency machinery needed |
| Metadata volume doesn't yet demand it | No DB sharding yet — explicit decision, not an oversight |

```
Client → CDN (cached article content)
       → Load Balancer → App/API tier
              │                 │
              ▼                 ▼
       [Cache] ⇄ [Primary DB + read replicas]   (Article/User metadata)
              │
              ▼
       [Message Queue] → [Worker Pool] → [Object Storage] (cleaned content)
                                       → [DB] (status update: processing → ready)
```

Full reasoning: [04-deriving-the-architecture.md](04-deriving-the-architecture.md).

---

## Step 5 — Identifying the Bottleneck, Go Deep

**Target chosen:** the fetch-and-clean worker pool — the least obviously-safe component, since it calls arbitrary, sometimes-slow or hostile external URLs.

> "The risk isn't throughput, it's that a hung origin server can tie up a worker indefinitely. I'd put a hard per-fetch timeout (~10s), retry with backoff up to 3 attempts, then dead-letter. I'd also cap concurrent fetches per origin domain — otherwise a viral link saved by thousands of users at once turns into a self-inflicted DoS against that site and risks our fetcher's IP getting banned, which is worse than a slow job. The tradeoff: per-domain caps mean bursty saves for one popular site queue up rather than parallelize, which is acceptable because Step 1 only requires eventual offline availability, not real-time save confirmation — I'd revisit this if 'instant save confirmation' became a hard requirement."

This is the [🎯 Staff signal](../../07-interview-templates/01-frameworks/05-staff-signal-convention.md) shape: mechanism (timeout + backoff + DLQ + per-domain cap), failure mode (hung worker, self-DoS), quantified tradeoff (queuing delay for one domain), and an explicit tie back to a Step 1 requirement.

Full reasoning: [05-identifying-the-bottleneck.md](05-identifying-the-bottleneck.md).

---

## What made this work

Every box, every field, every deep-dive sentence traces to something written down in Step 1 or Step 2 — none of it required having seen a "bookmarking service" HLD solution before. That's the whole point: the process is what transfers to an unfamiliar prompt, not the memorized shape of this particular answer.

**Next:** [07-interview-playbook.md](07-interview-playbook.md) — running this under a 45-60 minute clock.
