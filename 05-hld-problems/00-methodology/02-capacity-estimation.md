> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 2 — converting the raw numbers from Step 1 into QPS, storage, and bandwidth figures, and reading those figures to decide which architecture tier you're in *before* you draw anything.
>
> **Key ideas:**
> - Capacity estimation isn't a ritual to perform and forget — it's the input to Step 4. "We need 12 TB" is the sentence that forces "single Postgres instance" off the table, not a preference.
> - Use round numbers (1 day ≈ 100K seconds, not 86,400) — precision doesn't matter, order of magnitude does. See [capacity-estimation.md](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) for the full magic-number reference.
> - Always compute both average and peak (peak is usually 2-3x average for consumer traffic, higher for flash-sale/event-driven traffic) — architecture decisions should be sized to peak, not average.
> - Map the final numbers to a tier using [architecture-by-scale.md](../../07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md) — this tells you what NOT to propose as much as what to propose.
>
> **Key takeaway:** If you can't point to which number in your capacity estimate justifies a given component (sharding, caching, a queue), you added that component from memory of a similar problem, not from this problem's actual load.

---
module: 05-hld-problems
topic: Methodology
status: unread
tags: [05-hld-problems, methodology, capacity-estimation, scale]
---
# Step 2 — Capacity Estimation

Requirements ([Step 1](01-requirements-and-scope.md)) tell you *what* to build. This step tells you *how big* — and the size determines which architectures are even legal answers. A design that would be correct at 10K users can be actively wrong at 100M users, and vice versa (over-engineered, unjustifiable complexity for a small system).

---

## The 4 numbers to compute, every time

```
[Capacity Estimation — compute in this order]
├── 1. QPS (queries per second)
│   ├── Read QPS = daily reads / 100,000 (seconds/day, rounded)
│   ├── Write QPS = daily writes / 100,000
│   └── Peak QPS = average QPS × 2-3 (or ask: is traffic spiky?
│       e.g., ticket sales, flash sales → peak could be 10-50x)
├── 2. Storage
│   ├── Per-item size × items/day × retention period
│   └── Always state the retention assumption explicitly
│       ("assuming 5-year retention...")
├── 3. Bandwidth
│   └── QPS × average payload size (separately for read and write paths —
│       they're usually very different, e.g., a 200-byte write vs.
│       a 2 MB image read)
└── 4. Memory (only if caching is in play)
    └── Working-set estimate: what % of data is "hot"? Apply the
        80/20 rule as a default, then size cache to hold that 20%.
```

Compute these from the Step 1 numbers directly — don't invent new numbers here. If Step 1 didn't produce a number you need, that's a sign to go back and ask, not to guess silently.

---

## Reading the numbers into a tier

Once you have QPS/storage/bandwidth, the actual point of this step is deciding what tier of architecture is justified — see the full ladder in [architecture-by-scale.md](../../07-interview-templates/02-cheat-sheets/04-architecture-by-scale.md). As a compressed version:

| If your numbers land here... | ...then this is the ceiling of what's justified |
|---|---|
| < 1K QPS, < 10 GB storage | Single server, single DB — no sharding, no queue, no CDN needed yet |
| ~1K-10K QPS, tens of GB | Add a cache and read replicas; DB still a single logical instance |
| ~10K-100K QPS, hundreds of GB–low TB | Load balancer + horizontally scaled app tier + CDN for static/media |
| 100K+ QPS, multi-TB+ | Sharding/partitioning, message queue for write smoothing, likely multi-region |

The mistake in both directions is common: proposing Kafka and 12 shards for a 500-QPS internal tool is over-engineering exactly as much as proposing a single Postgres box for a system whose own numbers say 200K writes/sec.

---

## Worked mini-example: continuing the read-it-later service

From [Step 1](01-requirements-and-scope.md)'s numbers: 10M users, ~50M saves/day, ~200 KB average stored size.

- **Write QPS:** 50M saves/day ÷ 100,000 sec/day ≈ **500 writes/sec average**. Assume reading (article-list views + "mark as read" actions) happens ~3x as often as saving → **~1,500 reads/sec average**. Peak (evening reading hours cluster traffic) ≈ 2-3x average → **~1,500 writes/sec, ~4,500 reads/sec peak**.
- **Storage:** 50M articles/day × 200 KB = 10 TB/day *(this is clearly too high — sanity check: 50M saves/day across 10M users is 5 saves/user/day, matches Step 1; but 10 TB/day × 365 = 3.65 PB/year is a large-company-scale number, worth flagging out loud: "that feels high for a bookmarking app — let me double check the daily-save assumption" — catching your own unrealistic number is itself a signal)*. Recomputed sanity: 10M users × 5 saves/day × 200 KB = 10 TB/day is consistent with the given numbers — so the honest move is to state it and note object storage (S3-class), not a relational DB, is required at this size.
- **Bandwidth:** Write path: 500/sec × 200 KB ≈ 100 MB/sec sustained ingest. Read path (serving stored articles): 1,500/sec × 200 KB ≈ 300 MB/sec.
- **Memory/cache:** Hot set = recently-saved, not-yet-read articles, roughly the last few days per active user — cache sized to hold a few days' worth of the write volume (~30-50 GB), not the full multi-PB corpus.

**Tier reading:** ~1,500-4,500 QPS and multi-TB/day storage puts this solidly in the "load balancer + horizontally scaled app tier + object storage + CDN for article content + cache for hot reads" tier — not single-server, but not yet requiring a message queue for the write path itself (500 writes/sec is well within a single write-optimized path's capacity, though a queue may still be justified for the *fetch-and-clean* step, which is Step 4's job to identify, not Step 2's).

---

## Common mistakes

- **Skipping straight to "we'll use Kafka and shard by user_id" without computing anything.** That's naming components from pattern-matching, not deriving them — an interviewer who asks "what number told you that you need sharding" will catch this immediately.
- **Not sanity-checking absurd results.** If your math produces "3.65 PB/year" for what feels like a lightweight app, say so out loud and re-derive — silently accepting an implausible number reads worse than catching and correcting it.
- **Only computing average, not peak.** Architecture must survive peak, not average — a system sized to average QPS falls over during exactly the traffic spike an interviewer will ask about next ("what happens during a flash sale?").
- **Treating this as a box-checking ritual disconnected from Step 4.** The numbers exist to be *used* — every "why do we need X" answer in Step 4 should be able to cite a number from this step.

---

## Interview Angles

- State assumptions explicitly and move on — "I'll assume average payload is 2 KB, revise me if that's off" is stronger than stalling to get an exact number from the interviewer.
- When your own math produces a surprising number, narrate the surprise and the sanity check — this is a visible signal of rigor, not a mistake to hide.
- Connect every number to the decision it will justify later: "this is multi-TB, so we're object storage, not a relational blob column" is worth saying now, even though the full component list comes in Step 4.

**Next:** [03-api-and-data-model.md](03-api-and-data-model.md) — define the contract and schema before drawing any boxes.
