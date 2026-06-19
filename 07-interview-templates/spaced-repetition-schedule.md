---
module: 07-interview-templates
topic: Spaced Repetition Review Schedule
status: unread
tags: [07-interview-templates, spaced-repetition, review-schedule, interview-prep]
---
# Spaced Repetition Review Schedule

> What to review and when. Spaced repetition works by reviewing material just before you would forget it. The schedule below is calibrated for a 4-week interview prep period.

---

## The Core Principle

Don't re-read everything every day. Review each concept at increasing intervals:

- New concept → review after **1 day**
- After 1st review → review after **3 days**
- After 2nd review → review after **7 days**
- After 3rd review → review after **14 days**
- Stable → review **once before interview**

If you fail to recall a concept during review, reset its interval to 1 day.

---

## How to Use This Schedule

1. Fill in your interview date at the top.
2. On each day, complete the **new material** column (first-time read).
3. Complete the **review** column (active recall — close the file, write what you remember, then check).
4. Score each review: ✓ (recalled correctly) or ✗ (forgot/wrong — reset interval).
5. Track ✗ items in the "weak list" at the bottom — review those daily until stable.

**Interview date:** _______________
**Start date:** _______________

---

## 4-Week Schedule

### Week 1 — Foundations + Building Blocks

| Day | New Material | Review (from prior days) |
|---|---|---|
| 1 | Tier 0: networking.md, fundamentals.md (latency numbers, bottleneck intuition) | — |
| 2 | Tier 1: scaling-strategies.md, load-balancers.md | Day 1: latency numbers, OSI model |
| 3 | Tier 1: caching-layer.md, cache-eviction.md, cdn.md | Day 1: latency numbers; Day 2: LB algorithms |
| 4 | Tier 1: replication.md, sharding.md | Day 1: bottleneck; Day 3: LRU vs LFU |
| 5 | Tier 1: consistent-hashing.md, fundamentals.md (CAP/PACELC) | Day 2: horizontal vs vertical scale; Day 4: sharding strategies |
| 6 | Tier 2: consistency-and-conflicts.md (models: linear/causal/eventual) | Day 3: cache invalidation; Day 5: consistent hashing + virtual nodes |
| 7 | Tier 2: databases.md (MVCC, isolation levels) | Day 4: primary-replica replication; Day 5: CAP theorem; Day 1: latency |

**Week 1 active recall prompts:**
- Quote 5 latency numbers from memory
- Explain consistent hashing + virtual nodes to someone unfamiliar
- Name the 4 isolation levels and one anomaly each prevents
- Distinguish linearizable vs eventual consistency with a user-visible example

---

### Week 2 — Async Systems + Patterns

| Day | New Material | Review (from prior days) |
|---|---|---|
| 8 | Tier 2: databases.md (B-tree, WAL), lsm-vs-btree.md | Day 5: consistent hashing; Day 7: MVCC |
| 9 | Tier 2: consensus-algorithms.md (Raft: election, replication, safety) | Day 6: consistency models; Day 8: LSM write amplification |
| 10 | Tier 3: message-brokers.md, kafka-internals.md (ISR, exactly-once) | Day 7: isolation levels; Day 9: Raft leader election |
| 11 | Tier 3: saga-pattern.md, two-phase-commit.md | Day 8: LSM vs B-tree; Day 10: Kafka partition ordering |
| 12 | Tier 3: outbox-pattern.md, change-data-capture.md | Day 9: Raft safety property; Day 11: saga compensation |
| 13 | Tier 3: event-driven-architecture.md, cqrs-event-sourcing.md | Day 10: exactly-once Kafka; Day 12: outbox vs polling |
| 14 | Tier 3: rate-limiting.md, circuit-breaker.md, bulkhead-pattern.md | Day 11: 2PC coordinator crash; Day 13: CQRS read model |

**Week 2 active recall prompts:**
- Explain Raft leader election from memory (steps, quorum requirement)
- Why does saga not use 2PC? What does it lose?
- Explain the outbox pattern: what problem it solves, how it works, its limitation
- Describe token bucket vs sliding window rate limiting; which prevents burst?

---

### Week 3 — Internals + Advanced Topics

| Day | New Material | Review (from prior days) |
|---|---|---|
| 15 | Tier 4: cassandra-internals.md (gossip, Merkle, SSTable, compaction) | Day 9: Raft; Day 14: circuit breaker algorithm |
| 16 | Tier 4: redis-internals.md (data structures, persistence, cluster) | Day 12: CDC; Day 15: Cassandra write path |
| 17 | Tier 4: elasticsearch-internals.md (inverted index, BKD, sharding) | Day 13: event sourcing; Day 16: Redis sorted set |
| 18 | Tier 4: distributed-locks.md (Redlock, fencing tokens) | Day 14: sliding window rate limiter; Day 17: ES inverted index |
| 19 | Tier 4: global-distribution.md, microservices.md (Istio, canary) | Day 15: Cassandra gossip; Day 18: fencing tokens |
| 20 | Tier 5: stream-processing.md (Flink windows, watermarks, exactly-once) | Day 16: Redis cluster; Day 19: mTLS, circuit breaker |
| 21 | Tier 5: security-compliance-checklist.md (OAuth 2.0 flows, JWT, zero-trust) | Day 17: BKD trees; Day 20: Flink window types |

**Week 3 active recall prompts:**
- Explain Cassandra's write path: memtable → SSTable → compaction
- What is a fencing token and why does Redlock need it?
- Describe Flink tumbling vs session windows; when do you use each?
- Explain OAuth 2.0 Auth Code + PKCE: why PKCE exists, the steps

---

### Week 4 — LLD + Mock Interviews

| Day | New Material | Review (from prior days) |
|---|---|---|
| 22 | LLD: concurrency-patterns.md (JMM, volatile, happens-before) | Day 18: distributed locks; Day 21: JWT validation |
| 23 | LLD: concurrency-patterns.md (RWLock, CAS, LongAdder, Semaphore) | Day 20: Flink exactly-once; Day 22: happens-before |
| 24 | LLD: producer-consumer.md, futures-async-patterns.md | Day 19: Istio VirtualService; Day 23: RWLock vs synchronized |
| 25 | LLD: SOLID principles quick review (DIP + ISP are most tested) | Day 21: OAuth flows; Day 24: CompletableFuture |
| 26 | Mock HLD: Problem 3 (Twitter feed) — 45 min timed | All of Week 2 key concepts |
| 27 | Mock HLD: Problem 8 (Hotel booking) — 45 min timed | All of Week 3 key concepts |
| 28 | Mock LLD: Problem 11 (LRU cache) + Problem 13 (Connection pool) | All of Week 4 key concepts |

**Week 4 active recall prompts:**
- Thread pool sizing formula for I/O-bound workload — derive it
- Difference between CAS and synchronized: when does CAS fail? What do you do?
- Walk through Semaphore borrow/return pattern for connection pool
- Describe happens-before: what provides it in Java? What breaks it?

---

## Pre-Interview Week (Final 7 Days)

Use this regardless of how long you've been preparing. Do this in the 7 days before the interview.

| Day | Activity | Focus |
|---|---|---|
| -7 | Full mock: 1 HLD + 1 LLD, scored with rubric | Identify remaining weak dimensions |
| -6 | Review weakest 3 dimensions from rubric; re-read relevant files | Targeted gap closing |
| -5 | Full mock: company-specific problem (see company-specific-guide.md) | Company calibration |
| -4 | Review anti-patterns list; read through all 50 | Failure mode prevention |
| -3 | Full mock: 1 HLD + 1 LLD; focus on communication only (narrate every decision) | Communication polish |
| -2 | Review company-specific guide for target company; internalize signal table | Final company calibration |
| -1 | Light review: question bank (5 random questions per topic), latency numbers, no new material | Consolidation, no new load |
| Interview day | 30 min: read requirements framing section of hld-template.md; review your weak list | Priming, not learning |

---

## Concept Review Cards

Use these for active recall during review sessions. Read the question, answer out loud, then check.

### Distributed Systems

**Q: What does "happens-before" mean in distributed systems (not JMM)?**
A: If event A happens-before event B, then all effects of A are visible to B. Established by: message passing (send before receive), process order. Lamport clocks encode this relationship. Without it, different nodes may observe events in different orders.

**Q: State the Raft safety property.**
A: Only one leader per term. A leader has all committed entries from all previous terms. A node can only become leader if its log is at least as up-to-date as the majority.

**Q: What is the difference between a partition and a replica in Kafka?**
A: A partition is the unit of parallelism and ordering — messages within a partition are ordered. A replica is a copy of a partition on a different broker for fault tolerance. Each partition has one leader replica (handles reads/writes) and N-1 follower replicas (ISR).

**Q: How does Cassandra achieve eventual consistency?**
A: Each write goes to N replicas determined by the partition key's position on the token ring. With `QUORUM` writes, the majority must acknowledge. Offline nodes receive writes via hinted handoff. Anti-entropy via Merkle tree comparison reconciles diverged replicas asynchronously.

**Q: Name 3 ways to prevent a cache stampede.**
A: (1) Mutex lock: only one thread fetches from DB, others wait. (2) Probabilistic early expiry: begin refreshing before TTL expires, probability increases as TTL → 0. (3) Background refresh: async worker refreshes proactively before expiry.

---

### Patterns

**Q: When does the outbox pattern beat direct Kafka publish?**
A: When you need transactional guarantees: insert the DB record AND publish the event atomically, with no risk of one succeeding and the other failing. Direct publish can fail after DB commit (event lost) or succeed before DB commit (event with no record). Outbox: write event to `outbox` table in the same DB transaction; CDC or poller publishes to Kafka separately.

**Q: What is the key difference between choreography and orchestration sagas?**
A: Choreography: each service reacts to events and publishes events — no central coordinator; hard to visualize the full flow. Orchestration: a saga orchestrator explicitly commands each step and handles failures — easier to reason about but the orchestrator is a central dependency. Orchestration is easier to debug; choreography is more decoupled.

**Q: What does the strangler fig pattern do and what's the hardest part?**
A: Incrementally replaces a monolith by building new functionality in the new system and routing traffic to it, while the old system still handles existing paths. The hardest part is DB decomposition: the monolith and new service may share a DB; you can't just split them without a dual-write + migration phase.

---

### Java Concurrency

**Q: Why is `synchronized` insufficient for a read-heavy cache?**
A: `synchronized` on a method means only one thread can execute it at a time — even two simultaneous reads block each other. For a read-heavy cache, use `ReentrantReadWriteLock`: multiple threads can hold the read lock simultaneously; the write lock is exclusive. This allows concurrent reads without serialization.

**Q: What problem does `LongAdder` solve that `AtomicLong` doesn't?**
A: Under high contention, `AtomicLong.incrementAndGet()` causes many threads to spin-retry their CAS, which adds CPU overhead and contention. `LongAdder` maintains per-CPU-core cells; each thread increments its local cell, reducing contention. `sum()` adds all cells. Trade-off: `sum()` is not a precise point-in-time snapshot — acceptable for counters like request counts.

**Q: What is false sharing?**
A: Two threads write to different variables that share the same 64-byte CPU cache line. Each write by thread A invalidates thread B's cache line, forcing it to reload — serializing what should be independent parallel work. Fix with `@Contended` annotation (Java 9+) to pad variables to separate cache lines.

---

## Weak Concept Tracker

Track concepts you couldn't recall during review. Review these daily until stable.

```
Concept                           | Date added | Review count | Stable?
----------------------------------|------------|--------------|--------
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
```

---

## Schedule Compression

If you have less than 4 weeks, cut in this priority order:
1. Skip DB internals (4.5–4.9) — rely on conceptual understanding
2. Skip Tier 5 chaos engineering and strangler fig deep dive
3. Skip LLD design patterns (L2) — focus on concurrency (L3) which is higher signal
4. Reduce mock problems — do 1 per day instead of building up to it
5. Never skip: Tier 1, Tier 2 (2.1–2.6), Tier 3 (3.2–3.5), LLD concurrency (L3.1–L3.5)
