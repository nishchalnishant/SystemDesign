> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A staff-level rapid-recall deck — the questions a senior interviewer fires *after* you've drawn the boxes, to test whether you understand the machinery. Unlike the beginner ["gotcha" bank](./02-interview-question-bank.md), every answer here quantifies a trade-off instead of just naming a component.
>
> **How to use it:**
> - Cover the **A:** line. Say your answer out loud in ≤ 60 seconds. Then reveal and compare.
> - The bar is not "correct" — it's "correct *and* quantified *and* names the failure mode."
> - Drill the whole deck once a day for the 5 days before an on-site.
>
> **Key takeaway:** SDE-3 signal = you name the number, the tradeoff, and when the default breaks. "Use a cache" is SDE-2. "Cache-aside with a 30s TTL + jitter, single-flight on miss, accept a stampede window ≤ one DB call per key per TTL" is SDE-3.

---
module: 07-interview-templates
topic: SDE-3 Rapid-Recall Question Bank
status: unread
tags: [07-interview-templates, interview, sde-3, flashcards, rapid-recall]
---
# SDE-3 Rapid-Recall Bank

> **Format:** Q → 60-second spoken answer. The 🎯 line is the *staff signal* — the specific phrase that separates E5 from E6. If your answer didn't contain that idea, you answered at SDE-2.

---

## 1. Databases & Storage

**Q: SQL vs NoSQL — give me a decision rule, not a preference.**
**A:** "Start relational. Move a table off SQL only when one of three things breaks: (1) write throughput exceeds what a single primary + vertical scaling can absorb (~tens of k writes/s), (2) the access pattern is a pure key lookup with no cross-entity joins, or (3) the data is schemaless/append-heavy (events, logs). Absent those, Postgres with read replicas and partitioning goes surprisingly far."
> 🎯 Staff signal: you gave a *threshold* that triggers the switch, not "NoSQL scales better."

**Q: You have replication lag of 2s. How do you give a user read-your-own-writes?**
**A:** "Three options, cheapest first: (1) route that user's reads to the primary for a short window after they write, (2) sticky-read from the replica but track a per-user write timestamp/LSN and only serve replicas caught up past it, (3) read from cache you updated on write. Pick (1) for simplicity; (2) when primary read load is the bottleneck."
> 🎯 Staff signal: you named the LSN/version-token approach, not just "read from primary."

**Q: Pick a shard key for a messaging app. Defend it.**
**A:** "`conversation_id`, not `user_id`. It co-locates all messages of a thread on one shard (single-shard reads for the hot path), spreads load evenly since conversations are numerous, and is immutable. `user_id` would make a group chat's messages span shards and would hot-spot on power users."
> 🎯 Staff signal: you evaluated the key against cardinality + immutability + query locality, and named the anti-pattern.

**Q: How do you reshard a live sharded database with zero downtime?**
**A:** "Double-write + backfill. Stand up the new shard map, write to both old and new topology, backfill historical data, verify parity with a checksum job, then flip reads to the new map and stop the old writes. Consistent hashing minimizes the keys that move in the first place."
> 🎯 Staff signal: you described the *migration mechanism*, not just "add more shards."

**Q: LSM-tree vs B-tree — when does each win?**
**A:** "LSM (Cassandra, RocksDB): write-heavy, sequential-write-optimized, no read-before-write, but reads pay a bloom-filter + multi-SSTable + compaction tax. B-tree (Postgres, MySQL): read-heavy, in-place updates, predictable read latency, but random writes hurt. Choose LSM when writes ≫ reads and you can tolerate read amplification."
> 🎯 Staff signal: you named *write amplification vs read amplification* as the axis.

---

## 2. Caching

**Q: Design the full cache read path for a hot key. Name every failure mode.**
**A:** "Cache-aside: check Redis, on miss fetch DB, write back with TTL. Failure modes and fixes: **stampede** on expiry → single-flight/mutex so one request refills; **stale reads** → short TTL or write-through invalidation; **synchronized expiry** → add TTL jitter; **hot key overwhelming one Redis node** → client-side local cache or key replication across nodes."
> 🎯 Staff signal: you enumerated stampede + jitter + hot-node, not just "use Redis."

**Q: Write-through vs write-back vs write-around — pick one for a like-counter.**
**A:** "Write-back (write to cache, async flush to DB). A like counter is high-write, loss-tolerant for a few seconds, and read-hot. Write-through would double every write's latency; write-around would tank the read hit rate right after a write. Batch the flush to collapse many increments into one DB write."
> 🎯 Staff signal: you tied the policy to the write/read ratio and loss tolerance.

**Q: Your cache and DB disagree. How did that happen and how do you prevent it?**
**A:** "Classic race: reader gets a miss, DB returns v1, meanwhile a writer sets v2 and invalidates the (empty) cache, then the slow reader writes v1 back — cache now holds stale v1 forever. Fixes: write-through with versioned writes, or delete-on-write + short TTL as a backstop, or use a CDC stream to invalidate."
> 🎯 Staff signal: you described the *specific interleaving*, not "cache invalidation is hard."

---

## 3. Messaging & Async

**Q: Exactly-once delivery — does it exist? How do you fake it?**
**A:** "Not end-to-end in general; the network can always redeliver. You get *effectively-once* by combining at-least-once delivery + idempotent consumers. Three idempotency patterns: (1) natural idempotency (SET not INCR), (2) a dedup table keyed on message-id, (3) transactional outbox so producing the event and committing state are atomic."
> 🎯 Staff signal: you said "at-least-once + idempotent = effectively-once" and named the outbox.

**Q: A consumer is falling behind — lag is growing. Walk me through the diagnosis.**
**A:** "Is it a throughput or a poison-pill problem? Check: consumer CPU/IO saturated → scale consumers up to the partition count (can't exceed it — repartition if needed). One partition lagging → hot key, need a better partition key. Lag on all partitions after a deploy → slow processing regression. A single message retrying forever → route to DLQ after N attempts."
> 🎯 Staff signal: you separated scale-out (bounded by partitions) from poison-pill (DLQ).

**Q: Why can't you just add consumers to make Kafka faster?**
**A:** "Parallelism is capped at the partition count — a partition is consumed by at most one consumer in a group. Beyond that, adding consumers leaves them idle. To go faster you repartition (which reshuffles keys and breaks in-flight ordering) or make each consumer faster."
> 🎯 Staff signal: you named the partition = unit-of-parallelism constraint.

---

## 4. Consistency & Coordination

**Q: CAP is a bumper sticker. Give me PACELC for a real system.**
**A:** "PACELC: on Partition, choose A or C; Else (normal ops), choose Latency or Consistency. DynamoDB default = PA/EL (available + low latency, eventually consistent). A bank ledger = PC/EC (consistent even if it costs availability and latency). The 'Else' clause is the one people forget — most of the time there's no partition, and you're still trading latency for consistency on every quorum read."
> 🎯 Staff signal: you used the EL/EC 'else' clause, not just CA/CP.

**Q: You need a distributed lock. What's wrong with `SETNX` in Redis?**
**A:** "A single-node lock isn't fault-tolerant, and even Redlock has a correctness gap: a GC pause or clock skew can let a client hold a lock it thinks is valid past expiry, so two clients act at once. The fix is a **fencing token** — a monotonic number handed out with the lock that the protected resource checks and rejects if stale. The lock alone is never sufficient."
> 🎯 Staff signal: you named fencing tokens as the real safety mechanism.

**Q: Two-phase commit — why do we avoid it and what do we use instead?**
**A:** "2PC blocks: if the coordinator crashes after prepare, participants hold locks indefinitely — it's a synchronous availability killer across services. We use the **Saga** pattern instead: a sequence of local transactions with compensating actions on failure, coordinated by choreography (events) or orchestration (a central saga). We trade atomicity for availability and get eventual consistency."
> 🎯 Staff signal: you named the coordinator-crash blocking failure and Saga + compensation.

---

## 5. Scale & Performance Math

**Q: Estimate QPS and storage for a 100M-DAU photo app. Show the arithmetic.**
**A:** "100M DAU, say each posts 0.2 photos/day → 20M writes/day ÷ 86,400s ≈ **230 writes/s average, ~5× peak ≈ 1,150/s**. Reads at ~50:1 → ~12k reads/s peak. Storage: 20M photos/day × 1.5MB ≈ **30 TB/day** raw; with thumbnails + replication ×3 ≈ 100 TB/day; that's ~36 PB/year — so we tier cold photos to object storage and only keep hot ones on SSD."
> 🎯 Staff signal: you carried peak multiplier + replication factor + a tiering conclusion.

**Q: A request fans out to 100 services. Each has p99 = 10ms. What's the overall p99?**
**A:** "Much worse than 10ms — with 100 parallel calls, the slowest almost certainly hits a tail. P(all under p99) = 0.99^100 ≈ 37%, so ~63% of requests hit at least one 10ms+ tail. This is **tail-latency amplification**. Fixes: hedged requests (send a duplicate after p95), reduce fan-out, or make the tail tighter, not the median."
> 🎯 Staff signal: you computed 0.99^100 and named hedged requests.

**Q: How many requests/sec can one server handle? Reason it out.**
**A:** "Little's Law: concurrency = throughput × latency. If each request holds a thread for 50ms and I have 200 threads, throughput = 200 / 0.05 = **4,000 req/s** — if CPU/IO isn't the limit first. The real cap is usually the downstream (DB connections) or CPU, so I'd load-test to find the utilization knee (~70–80%) rather than trust the arithmetic alone."
> 🎯 Staff signal: you invoked Little's Law and the utilization knee.

---

## 6. Reliability

**Q: Design the retry policy for a flaky downstream. What breaks a naive retry?**
**A:** "Naive fixed retries cause **retry storms** — the downstream hiccups, everyone retries in lockstep, and the synchronized load keeps it down. Fix: exponential backoff **with jitter**, a **retry budget** (cap retries to e.g. 10% of requests so you never more-than-double load), and a **circuit breaker** that fails fast when the downstream is clearly down. Only retry idempotent operations."
> 🎯 Staff signal: you named jitter + retry budget + circuit breaker + idempotency-gating.

**Q: What's your load-shedding strategy when the system is overloaded?**
**A:** "Shed *before* you fall over, by priority. Ladder: reject non-critical/batch traffic first, then degrade features (serve stale cache, drop personalization), then throttle by tier, and only then reject at the edge with 429 + Retry-After. Bounded queues so latency doesn't grow unboundedly — a full queue sheds immediately rather than queuing to death."
> 🎯 Staff signal: you described a priority ladder + bounded queues, not just "return 503."

**Q: How do you prevent a single failure from cascading?**
**A:** "Bulkheads + circuit breakers + timeouts. Isolate resource pools per dependency (a slow service can't exhaust all threads), trip a breaker when a dependency is failing, and set aggressive timeouts so a hung call doesn't pin a thread. Without timeouts, one slow dependency silently consumes your whole thread pool — the most common cascade."
> 🎯 Staff signal: you named the thread-pool-exhaustion cascade and bulkheads.

---

## 7. The "why" behind your own design

These are the interrupts that catch people who memorized a diagram.

**Q: You put a queue there. What does it actually buy you, and what does it cost?**
**A:** "Buys: decoupling (producer doesn't wait), buffering (absorbs spikes), and retry/durability. Costs: eventual consistency (the work isn't done when the API returns), ordering complexity, and now I own a DLQ and lag monitoring. If the operation must be synchronous for the user, the queue is the wrong tool."

**Q: You said 'add a cache.' What's your cache invalidation strategy — specifically?**
**A:** "TTL as a backstop, plus event-driven invalidation on write. Pure TTL means staleness up to the TTL; pure event-driven means a missed event = permanent staleness. Combining them bounds worst-case staleness to the TTL even if an invalidation event is lost."

**Q: Where's the single point of failure in what you just drew?**
**A:** "[Name it before they do.] The [coordinator/primary/gateway]. I'd make it HA with a standby + automated failover, health-checked, and I'd confirm failover is *tested* — an untested failover is a SPOF you haven't discovered yet."
> 🎯 Staff signal across all three: you volunteer the weakness and its cost before being pushed.

---

## Applied In

Drill these against the real problems:

- [HLD Problems](../../../05-hld-problems/) — pick one per tier, answer its by-level interviewer questions cold
- [Mock Interview Problems](./03-mock-interview-problems.md) — timed practice
- [45-Minute Walkthrough](./06-45-minute-walkthrough.md) — pacing rubric
- [The "Gotcha" Question Bank](./02-interview-question-bank.md) — the SDE-2 foundation layer
