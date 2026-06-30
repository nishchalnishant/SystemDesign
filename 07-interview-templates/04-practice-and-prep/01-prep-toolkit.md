> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A comprehensive study guide, including a 4-week spaced repetition schedule, self-assessment rubrics, and company-specific calibration.
>
> **Key concepts:**
> - Study Schedule: A week-by-week breakdown of what topics to review and when to do mock interviews.
> - Self-Assessment Rubric: A grading scale (1-4) across dimensions like Requirements Gathering, Technical Depth, and Communication to evaluate your mock interviews.
> - Company Calibration: How Amazon interviews (leadership principles focus) differ from Meta (speed and scale focus) or Google (algorithmic depth focus).
>
> **Key takeaway:** Use the 4-week schedule to avoid cramming. System design requires synthesizing many different concepts, which requires time for the brain to consolidate.

---
module: 07-interview-templates
topic: Prep Toolkit
status: unread
tags: [07-interview-templates, interview-prep, study-guide]
---
# Prep Toolkit

Consolidated reference covering: study schedule, self-assessment rubric, concept learning order, company-specific calibration, and security/compliance interview answers.

---

## Spaced Repetition Review Schedule

> What to review and when. Spaced repetition works by reviewing material just before you would forget it. The schedule below is calibrated for a 4-week interview prep period.

### The Core Principle

Don't re-read everything every day. Review each concept at increasing intervals:

- New concept → review after **1 day**
- After 1st review → review after **3 days**
- After 2nd review → review after **7 days**
- After 3rd review → review after **14 days**
- Stable → review **once before interview**

If you fail to recall a concept during review, reset its interval to 1 day.

### How to Use This Schedule

1. Fill in your interview date at the top.
2. On each day, complete the **new material** column (first-time read).
3. Complete the **review** column (active recall — close the file, write what you remember, then check).
4. Score each review: ✓ (recalled correctly) or ✗ (forgot/wrong — reset interval).
5. Track ✗ items in the "weak list" at the bottom — review those daily until stable.

**Interview date:** _______________
**Start date:** _______________

### 4-Week Schedule

#### Week 1 — Foundations + Building Blocks

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

#### Week 2 — Async Systems + Patterns

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

#### Week 3 — Internals + Advanced Topics

| Day | New Material | Review (from prior days) |
|---|---|---|
| 15 | Tier 4: cassandra-internals.md (gossip, Merkle, SSTable, compaction) | Day 9: Raft; Day 14: circuit breaker algorithm |
| 16 | Tier 4: redis-internals.md (data structures, persistence, cluster) | Day 12: CDC; Day 15: Cassandra write path |
| 17 | Tier 4: elasticsearch-internals.md (inverted index, BKD, sharding) | Day 13: event sourcing; Day 16: Redis sorted set |
| 18 | Tier 4: distributed-locks.md (Redlock, fencing tokens) | Day 14: sliding window rate limiter; Day 17: ES inverted index |
| 19 | Tier 4: global-distribution.md, microservices.md (Istio, canary) | Day 15: Cassandra gossip; Day 18: fencing tokens |
| 20 | Tier 5: stream-processing.md (Flink windows, watermarks, exactly-once) | Day 16: Redis cluster; Day 19: mTLS, circuit breaker |
| 21 | Security & Compliance (OAuth 2.0 flows, JWT, zero-trust — see Security section below) | Day 17: BKD trees; Day 20: Flink window types |

**Week 3 active recall prompts:**
- Explain Cassandra's write path: memtable → SSTable → compaction
- What is a fencing token and why does Redlock need it?
- Describe Flink tumbling vs session windows; when do you use each?
- Explain OAuth 2.0 Auth Code + PKCE: why PKCE exists, the steps

#### Week 4 — LLD + Mock Interviews

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

### Pre-Interview Week (Final 7 Days)

Use this regardless of how long you've been preparing.

| Day | Activity | Focus |
|---|---|---|
| -7 | Full mock: 1 HLD + 1 LLD, scored with rubric | Identify remaining weak dimensions |
| -6 | Review weakest 3 dimensions from rubric; re-read relevant files | Targeted gap closing |
| -5 | Full mock: company-specific problem (see Company-Specific Guide below) | Company calibration |
| -4 | Review anti-patterns list; read through all 50 | Failure mode prevention |
| -3 | Full mock: 1 HLD + 1 LLD; focus on communication only (narrate every decision) | Communication polish |
| -2 | Review company-specific guide for target company; internalize signal table | Final company calibration |
| -1 | Light review: question bank (5 random questions per topic), latency numbers, no new material | Consolidation, no new load |
| Interview day | 30 min: read requirements framing section of hld-template.md; review your weak list | Priming, not learning |

### Concept Review Cards

Use these for active recall during review sessions. Read the question, answer out loud, then check.

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

**Q: When does the outbox pattern beat direct Kafka publish?**
A: When you need transactional guarantees: insert the DB record AND publish the event atomically. Direct publish can fail after DB commit (event lost) or succeed before DB commit (event with no record). Outbox: write event to `outbox` table in the same DB transaction; CDC or poller publishes to Kafka separately.

**Q: What is the key difference between choreography and orchestration sagas?**
A: Choreography: each service reacts to events and publishes events — no central coordinator. Orchestration: a saga orchestrator explicitly commands each step and handles failures — easier to reason about but the orchestrator is a central dependency. Orchestration is easier to debug; choreography is more decoupled.

**Q: Why is `synchronized` insufficient for a read-heavy cache?**
A: `synchronized` on a method means only one thread can execute it at a time — even two simultaneous reads block each other. Use `ReentrantReadWriteLock`: multiple threads can hold the read lock simultaneously; the write lock is exclusive.

**Q: What problem does `LongAdder` solve that `AtomicLong` doesn't?**
A: Under high contention, `AtomicLong.incrementAndGet()` causes many threads to spin-retry their CAS. `LongAdder` maintains per-CPU-core cells; each thread increments its local cell. Trade-off: `sum()` is not a precise point-in-time snapshot.

**Q: What is false sharing?**
A: Two threads write to different variables that share the same 64-byte CPU cache line. Each write invalidates the other thread's cache line, forcing a reload. Fix with `@Contended` annotation (Java 9+) to pad variables to separate cache lines.

### Weak Concept Tracker

```
Concept                           | Date added | Review count | Stable?
----------------------------------|------------|--------------|--------
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
                                  |            |              |
```

### Schedule Compression

If you have less than 4 weeks, cut in this priority order:
1. Skip DB internals (4.5–4.9) — rely on conceptual understanding
2. Skip Tier 5 chaos engineering and strangler fig deep dive
3. Skip LLD design patterns (L2) — focus on concurrency (L3) which is higher signal
4. Reduce mock problems — do 1 per day instead of building up to it
5. Never skip: Tier 1, Tier 2 (2.1–2.6), Tier 3 (3.2–3.5), LLD concurrency (L3.1–L3.5)

---

## Self-Assessment Rubric

> Score yourself after every practice session. Honest scoring is the only way to identify real gaps. Don't average away your 1s.

### How to Use

1. Complete a timed practice session (45 min for HLD, 45 min for LLD)
2. Immediately after, score each dimension 1–4 before reviewing any notes
3. Add brief evidence for any score of 1 or 2
4. Track scores across sessions — look for dimensions stuck below 3

**Scoring scale:**
- **4 — Strong hire:** Did this without prompting, clearly, with depth
- **3 — Hire:** Did this adequately; interviewer wouldn't flag it
- **2 — Borderline:** Did this partially or only after being prompted
- **1 — No hire:** Missed this entirely or got it clearly wrong

### Part A: HLD Rubric

#### A1 — Requirements & Scoping (10 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A1.1 | Asked exactly 3 targeted clarifying questions | Asked none or 5+ | Asked 1–2 or generic ones | Asked 3, most were useful | Asked 3 that each changed the design | |
| A1.2 | Stated assumptions explicitly before designing | No assumptions stated | Stated some, missed obvious ones | Stated the main ones | Stated all key ones including non-obvious | |
| A1.3 | Scoped the problem to what's coverable in 45 min | No scoping | Implicitly scoped by running out of time | Mentioned scoping verbally | Explicitly scoped upfront and committed to a focus area | |
| A1.4 | Completed back-of-envelope before drawing HLD | No estimation | Did estimation after being asked | Did rough estimation before HLD | Did quantified estimation that drove architectural decisions | |
| A1.5 | Numbers were anchored to the design | No numbers used | Numbers mentioned but not connected to decisions | Numbers present, loosely connected | Every major decision cited a specific number | |

**A1 Total: ___ / 20**

#### A2 — High-Level Design (20 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A2.1 | Covered both write path and read path | Covered only one | Covered both but one was superficial | Both paths covered adequately | Both paths covered with comparable depth | |
| A2.2 | Every connection between components has a protocol and payload | Boxes only, no connections | Some connections labeled | Most connections labeled | Every connection has protocol, payload type, and consistency expectation | |
| A2.3 | State management is explicit | State never mentioned | Some stateful components identified | Most state was located | All state explicitly located with ownership and access model | |
| A2.4 | Load balancing and redundancy at every stateless tier | No LB/redundancy mentioned | Mentioned "we'd add redundancy" without detail | Load balancers drawn at main tiers | LBs drawn with type (L4/L7) and health check behavior stated | |
| A2.5 | Caching strategy: what to cache, key, TTL, invalidation | No caching discussed | "Add a cache" with no detail | Cache present with key and TTL | Cache with key design, TTL justification, and invalidation strategy | |
| A2.6 | Database selection was justified against access patterns | DB named without reason | DB named with partial reason | DB named with access pattern justification | DB named with access pattern, schema sketch, and alternative considered | |
| A2.7 | Async vs sync decision was explicit for inter-service calls | No distinction made | Some calls labeled | Most calls have sync/async label | All calls labeled with justification | |
| A2.8 | Failure behavior stated for every I/O call | No failure handling | Happy path only | Main failure paths covered | Every I/O call has explicit failure mode and handling | |
| A2.9 | Monitoring and alerting mentioned before being asked | Never mentioned | Mentioned only when prompted | Mentioned at end | Mentioned proactively with specific metrics and alert thresholds | |
| A2.10 | Design was anchored to requirements throughout | No callbacks to requirements | One or two callbacks | Regular callbacks | Explicitly verified design against each requirement at end | |

**A2 Total: ___ / 40**

#### A3 — Deep Dive Quality (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A3.1 | Consistency model was named precisely | No consistency discussion | "Eventually consistent" with no elaboration | Named model with user-visible implications | Named model + implications + why alternatives were rejected | |
| A3.2 | Sharding strategy addressed access distribution / hot keys | No sharding discussed | Sharding mentioned without key analysis | Sharding strategy with key design | Strategy + hot-key analysis + mitigation for skewed distribution | |
| A3.3 | Handled fan-out at scale | Fan-out not considered | "Push to all followers" with no scale analysis | Identified the fan-out problem, proposed solution | Pull vs push decision with threshold, hybrid approach | |
| A3.4 | Went deeper than one level when asked | Couldn't go deeper | Went 1 level deeper then stopped | Went 2 levels deep | Went 3+ levels deep; could reason from first principles | |
| A3.5 | Explicitly stated v1 vs v2 scope | No v1/v2 distinction | Vague deferral | Named what to defer with brief reason | Named what to defer, why, and what trigger would cause the upgrade | |

**A3 Total: ___ / 20**

#### A4 — Communication & Leadership (10 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| A4.1 | Every decision was framed as: decision + alternative + reason + cost | No framing | Some decisions framed | Most decisions framed | All decisions framed with all 4 elements | |
| A4.2 | Thought out loud (no silences > 30 seconds) | Multiple long silences | 1–2 long silences | Mostly narrated, brief pauses | Continuous narration | |
| A4.3 | Checked in with the interviewer every 5–7 minutes | Never checked in | Checked in once at end | Checked in 2x | Checked in regularly, incorporated feedback | |
| A4.4 | Led the structure; didn't wait for the interviewer to drive | Interviewer drove entire session | Interviewer prompted major sections | Self-directed most of the time | Fully self-directed | |
| A4.5 | Maintained position when challenged; updated when shown new info | Immediately agreed with all challenges | Inconsistently defended | Defended position with reasons | Defended with evidence; updated on new data, not just new opinion | |

**A4 Total: ___ / 20**

**HLD Total Score: ___ / 100**

| Range | Signal |
|---|---|
| 85–100 | Strong SDE-3 signal — ready to interview |
| 70–84 | Hire range — some dimensions need sharpening |
| 55–69 | Borderline — specific weaknesses, 2–3 weeks of targeted practice |
| < 55 | SDE-2 range — fundamental gaps, systematic practice needed |

### Part B: LLD Rubric

#### B1 — Object Model & Design (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B1.1 | Identified the core entities and their relationships before coding | Started coding immediately | Some entities identified | Main entities identified with relationships | All entities, relationships, and cardinality stated before writing code | |
| B1.2 | Class responsibilities are well-defined (SRP) | One god class | Multiple responsibilities per class | Named responsibilities per class | Responsibilities explicitly stated | |
| B1.3 | Interfaces are used where substitutability is needed | No interfaces | Interfaces added but not leveraged | Interfaces at main extension points | Interfaces everywhere behavior might vary | |
| B1.4 | Encapsulation: internal state is not leaked | Public fields throughout | Some private fields | Most state private | All state private; public methods expose behavior, not data | |
| B1.5 | Inheritance vs composition decision was conscious | Inheritance used everywhere | Composition sometimes used | Default to composition; inheritance for true is-a | Composition default; inheritance only for behavioral polymorphism with justification | |

**B1 Total: ___ / 20**

#### B2 — Concurrency (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B2.1 | Identified all shared mutable state | No shared state identified | Some identified | Most identified | All shared state explicitly flagged with ownership | |
| B2.2 | Used the right synchronization primitive for the access pattern | No synchronization | `synchronized` everywhere | Correct primitives for most cases | Correct primitives for every case with justification | |
| B2.3 | Thread pool is bounded and sized with reasoning | No thread pool | Unbounded pool | Bounded pool | Bounded pool with formula: N_cpu × (1 + wait/compute) for I/O-bound | |
| B2.4 | No obvious race conditions in the design | Race conditions present, not identified | Some race conditions addressed | Main race conditions addressed | All shared-state operations are atomic or locked | |
| B2.5 | Deadlock prevention was considered | No mention | Mentioned "we'd need to be careful" | Lock ordering policy stated | Lock ordering defined; or lock-free approach used with justification | |

**B2 Total: ___ / 20**

#### B3 — Code Quality (15 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B3.1 | Method naming clearly expresses intent | Cryptic names | Some clarity | Clear names for most methods | All names are self-documenting | |
| B3.2 | Methods are focused (single level of abstraction) | Long methods doing many things | Some long methods | Most methods are focused | All methods < 20 lines; no method mixes abstraction levels | |
| B3.3 | Edge cases handled: null, empty, overflow, concurrent modification | No edge cases | Some edge cases mentioned | Main edge cases handled | All edge cases walked through | |
| B3.4 | Error handling is appropriate: exceptions vs return values | Exceptions swallowed or missing | Inconsistent handling | Mostly appropriate | Appropriate strategy throughout | |
| B3.5 | Memory footprint was considered | No memory consideration | Mentioned "it could use memory" | Bounded collections | All caches bounded; eviction strategy stated; memory estimate given | |

**B3 Total: ___ / 20**

#### B4 — Extensibility & Scalability (5 points)

| # | Dimension | 1 | 2 | 3 | 4 | Score |
|---|---|---|---|---|---|---|
| B4.1 | Extension points are explicit (OCP) | Design is closed to extension | Some extension points | Main variation axes have extension points | All places where behavior should vary are open for extension | |
| B4.2 | Single-node design is complete; distributed version sketched | No distributed consideration | "This doesn't scale" acknowledged | Distributed version described verbally | Single-node fully working; distributed version described with specific changes | |

**B4 Total: ___ / 8**

**LLD Total Score: ___ / 68** — Normalize to /100: multiply by 1.47

| Normalized Range | Signal |
|---|---|
| 85–100 | Strong SDE-3 LLD signal |
| 70–84 | Hire range |
| 55–69 | Borderline |
| < 55 | SDE-2 range |

### Session Log Template

```
Date: ____________
Problem: ____________
Type: [ ] HLD   [ ] LLD
Time taken: ___ / 45 min

HLD Score: ___ / 100    LLD Score: ___ (normalized) / 100

Lowest-scoring dimensions this session:
1. ____________ (score: ___)  Evidence: ______________________________
2. ____________ (score: ___)  Evidence: ______________________________
3. ____________ (score: ___)  Evidence: ______________________________

What I'll do differently next session:
1. ____________
2. ____________
```

### Progress Tracker

| Session | Date | Problem | HLD | LLD | Weakest Dimension |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |

**Target:** Three consecutive sessions at 85+ on both HLD and LLD before scheduling a real interview.

### Dimension Priority Guide

| Priority | Dimension | Why it matters most |
|---|---|---|
| 1 | A2.8 — Failure behavior for every I/O | Missing this is the #1 reason for SDE-2 downlevel |
| 2 | A2.1 — Write path depth | Most candidates over-index on read path |
| 3 | A3.1 — Precise consistency model | "Eventual consistency" without specification is a red flag |
| 4 | B2.2 — Right synchronization primitive | `synchronized` everywhere signals LLD shallow thinking |
| 5 | A4.1 — Decision framing | Trade-off articulation is the core SDE-3 communication signal |
| 6 | A1.4 — Estimation before design | Skipping this is immediately visible |
| 7 | A2.9 — Monitoring unprompted | Amazon and Meta explicitly score this |
| 8 | B2.3 — Bounded + sized thread pool | Unbounded pools = immediate LLD red flag |

### Calibration Examples

**Score 4 on A2.8 (Failure behavior)**
> "If the payment service is down, the checkout call returns a 503. We do not create an order. The client should retry with the same idempotency key. If payment is down for > 30 seconds, we'd fail the checkout flow and show the user an error rather than leaving them in an uncertain state. Separately, the payment service's circuit breaker will open after 5 failures in 10 seconds and return fast failures until the half-open probe succeeds."

**Score 2 on A2.8 (Failure behavior)**
> "We'd add retry logic to handle failures."

**Score 4 on A3.1 (Consistency model)**
> "I'd use read-your-own-writes consistency here. After a user updates their profile, they must see the updated version immediately — but other users can tolerate seeing the old version for up to 5 seconds. I'd achieve this by routing the update author's reads to the primary replica with a session token, and other users' reads to the read replicas with 5-second replication lag."

**Score 2 on A3.1 (Consistency model)**
> "We'd use eventual consistency for this."

**Score 4 on B2.2 (Synchronization primitive)**
> "The `SearchIndex` has many concurrent readers and occasional bulk index updates. `synchronized` would serialize all reads — unacceptable. I'll use `ReentrantReadWriteLock`: readers acquire `readLock()` which allows concurrent access; the indexer acquires `writeLock()` which is exclusive."

**Score 2 on B2.2 (Synchronization primitive)**
> "I'll mark the methods as synchronized to prevent race conditions."

---

## Concept Dependency Map

> Every concept in this repo has prerequisites. Learn them in dependency order or you'll memorize facts without understanding. Numbers show recommended study order within a tier. Mark concepts as you learn them.

**How to read:** → means "is a prerequisite for"

### Tier 0 — Absolute Prerequisites (no dependencies)

| # | Concept | File | Test yourself |
|---|---|---|---|
| 0.1 | OSI model, TCP/IP, HTTP/HTTPS, DNS | `01-foundations/networking.md` | Explain what happens between typing a URL and seeing a page |
| 0.2 | Latency numbers: memory, SSD, network | `01-foundations/fundamentals.md` | Quote L1 cache, RAM, SSD, 1Gbps network latency without looking |
| 0.3 | CPU, memory, disk I/O — what limits what | `01-foundations/fundamentals.md` | Given a workload description, name the likely bottleneck |
| 0.4 | SQL basics: SELECT, JOIN, INDEX, EXPLAIN | `01-foundations/databases.md` | Write a query with a covering index for a given access pattern |
| 0.5 | Big-O notation | (general knowledge) | Know O(1), O(log N), O(N), O(N log N), O(N²) and when each appears |

### Tier 1 — Core Distributed Systems

```
0.1 (networking) → 1.1 (client-server) → 1.2 (horizontal scaling) → 1.3 (load balancing)
0.3 (bottleneck intuition) → 1.4 (caching) → 1.5 (cache eviction)
0.4 (SQL) → 1.6 (replication) → 1.7 (sharding)
1.6 + 1.7 → 1.8 (consistent hashing)
1.4 + 1.6 → 1.9 (CAP theorem)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| 1.1 | Client-server model, stateless vs stateful | `01-foundations/fundamentals.md` | 0.1 |
| 1.2 | Vertical vs horizontal scaling | `03-scaling/scaling-strategies.md` | 0.3 |
| 1.3 | Load balancing: round-robin, least-conn, consistent hash | `02-building-blocks/load-balancers.md` | 1.2 |
| 1.4 | Caching: where to cache (client, CDN, app, DB) | `01-foundations/caching-cdn.md`, `02-building-blocks/caching-layer.md` | 0.3 |
| 1.5 | Cache eviction: LRU, LFU, ARC, TinyLFU | `02-building-blocks/cache-eviction.md` | 1.4 |
| 1.6 | DB replication: primary-replica, sync vs async | `02-building-blocks/replication.md` | 0.4 |
| 1.7 | DB sharding: horizontal partition strategies | `02-building-blocks/sharding.md` | 1.6 |
| 1.8 | Consistent hashing + virtual nodes | `02-building-blocks/consistent-hashing.md` | 1.7 |
| 1.9 | CAP theorem, PACELC | `01-foundations/fundamentals.md` | 1.4, 1.6 |
| 1.10 | CDN: edge caching, cache-control, origin pull | `02-building-blocks/cdn.md` | 1.4 |

### Tier 2 — Consistency & Databases

```
1.9 (CAP) → 2.1 (consistency models) → 2.2 (consistency in practice)
1.6 (replication) → 2.3 (MVCC) → 2.4 (transactions)
2.4 → 2.5 (isolation levels) → 2.6 (distributed transactions)
2.1 + 2.3 → 2.7 (CRDT/conflict resolution)
1.7 (sharding) → 2.8 (database selection)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| 2.1 | Consistency models: linearizable, sequential, causal, eventual | `01-foundations/consistency-and-conflicts.md` | 1.9 |
| 2.2 | Read-your-own-writes, monotonic reads, bounded staleness | `01-foundations/consistency-and-conflicts.md` | 2.1 |
| 2.3 | MVCC: xmin/xmax, snapshot isolation | `01-foundations/databases.md`, `04-advanced-topics/internals/postgresql-internals.md` | 1.6 |
| 2.4 | ACID transactions: atomicity, isolation, durability | `01-foundations/databases.md` | 2.3 |
| 2.5 | Isolation levels: READ COMMITTED, REPEATABLE READ, SERIALIZABLE | `01-foundations/databases.md` | 2.4 |
| 2.6 | Distributed transactions: 2PC, saga | `09-patterns/two-phase-commit.md`, `09-patterns/saga-pattern.md` | 2.4, 2.1 |
| 2.7 | Vector clocks, CRDTs, conflict resolution | `01-foundations/consistency-and-conflicts.md` | 2.1 |
| 2.8 | DB selection: SQL vs NoSQL vs graph vs time-series | `08-reference/database-selection-tree.md` | 1.7, 2.5 |
| 2.9 | LSM tree vs B-tree: write/read amplification | `03-scaling/lsm-vs-btree.md` | 2.4 |
| 2.10 | Change Data Capture (CDC) | `01-foundations/change-data-capture.md` | 1.6, 2.4 |

### Tier 3 — Async Systems & Patterns

```
1.3 (load balancing) → 3.1 (API gateway)
2.6 (distributed transactions) → 3.2 (saga) → 3.3 (outbox)
3.1 + 3.2 → 3.4 (message brokers)
3.4 → 3.5 (Kafka internals)
3.3 + 3.4 → 3.6 (event-driven arch)
3.4 → 3.7 (stream processing)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| 3.1 | API gateway: routing, auth, rate limiting | `02-building-blocks/api-gateway.md` | 1.3 |
| 3.2 | Saga pattern: choreography vs orchestration, compensation | `09-patterns/saga-pattern.md` | 2.6 |
| 3.3 | Outbox pattern: transactional event publishing | `09-patterns/outbox-pattern.md` | 3.2, 2.10 |
| 3.4 | Message brokers: Kafka vs SQS vs RabbitMQ | `02-building-blocks/message-brokers.md` | 1.2 |
| 3.5 | Kafka internals: partitions, ISR, rebalancing, exactly-once | `04-advanced-topics/internals/kafka-internals.md` | 3.4 |
| 3.6 | Event-driven architecture, CQRS, event sourcing | `04-advanced-topics/event-driven-architecture.md`, `09-patterns/cqrs-event-sourcing.md` | 3.3, 3.4 |
| 3.7 | Stream processing: Flink windows, watermarks, exactly-once | `04-advanced-topics/stream-processing.md` | 3.5 |
| 3.8 | Rate limiting algorithms | `02-building-blocks/rate-limiting.md` | 3.1 |
| 3.9 | Circuit breaker, bulkhead, retry | `02-building-blocks/circuit-breaker.md`, `09-patterns/bulkhead-pattern.md` | 3.4 |

### Tier 4 — Advanced Distributed Systems

```
2.1 (consistency models) → 4.1 (consensus algorithms)
4.1 → 4.2 (distributed locks)
4.1 → 4.3 (leader election)
1.8 (consistent hashing) + 4.1 → 4.4 (distributed storage)
2.9 (LSM/B-tree) → 4.5 (Cassandra internals)
3.5 (Kafka) + 3.7 (stream) → 4.6 (Flink/Spark comparison)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| 4.1 | Consensus: Paxos overview, Raft in depth | `01-foundations/consensus-algorithms.md` | 2.1 |
| 4.2 | Distributed locks: Redlock, fencing tokens | `02-building-blocks/distributed-locks.md` | 4.1 |
| 4.3 | Leader election via Raft/ZooKeeper | `04-advanced-topics/internals/zookeeper-internals.md` | 4.1 |
| 4.4 | Distributed storage internals (replication factor, quorum reads/writes) | `02-building-blocks/replication.md`, `04-advanced-topics/internals/cassandra-internals.md` | 1.8, 4.1 |
| 4.5 | Cassandra: gossip, Merkle trees, anti-entropy | `04-advanced-topics/internals/cassandra-internals.md` | 4.4, 2.9 |
| 4.6 | DynamoDB internals: consistent hashing, B-tree, GSI | `04-advanced-topics/internals/dynamodb-internals.md` | 1.8, 2.9 |
| 4.7 | Elasticsearch: inverted index, BKD trees, sharding | `04-advanced-topics/internals/elasticsearch-internals.md` | 2.9 |
| 4.8 | Redis internals: data structures, persistence, cluster | `04-advanced-topics/internals/redis-internals.md` | 1.5, 1.8 |
| 4.9 | MySQL/PostgreSQL internals: WAL, MVCC, index types | `04-advanced-topics/internals/mysql-internals.md`, `04-advanced-topics/internals/postgresql-internals.md` | 2.3, 2.9 |
| 4.10 | Global distribution: multi-region, latency, data residency | `03-scaling/global-distribution.md` | 4.1, 2.1 |

### Tier 5 — Microservices & Operations

```
3.1 (API gateway) + 3.9 (resilience) → 5.1 (microservices patterns)
3.6 (CQRS) + 5.1 → 5.2 (strangler fig migration)
4.10 (global) + 5.1 → 5.3 (service mesh)
5.1 → 5.4 (observability)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| 5.1 | Microservices patterns: service decomposition, API contracts | `04-advanced-topics/microservices.md` | 3.1, 3.9 |
| 5.2 | Strangler fig: DB decomposition, dual-write, traffic cutover | `09-patterns/strangler-fig.md` | 3.6, 5.1 |
| 5.3 | Service mesh: Istio, mTLS, traffic management, canary | `04-advanced-topics/microservices.md` | 5.1 |
| 5.4 | Observability: metrics, traces, logs, SLOs, SLAs | `04-advanced-topics/observability.md` | 5.1 |
| 5.5 | Security architecture: OAuth 2.0, JWT, zero-trust | `01-foundations/security.md` | 5.1 |
| 5.6 | Chaos engineering | `04-advanced-topics/chaos-engineering.md` | 5.4 |
| 5.7 | Architecture by scale: monolith → services → global | `08-reference/architecture-by-scale.md` | 5.1, 4.10 |

### LLD Dependency Chain

```
L0 (Java/OOP) → L1 (SOLID) → L2 (design patterns) → L3 (concurrency) → L4 (LLD problems)
```

| # | Concept | File | Depends on |
|---|---|---|---|
| L0.1 | OOP: encapsulation, inheritance, polymorphism, abstraction | `06-lld/01-oop-fundamentals/four-pillars.md` | — |
| L0.2 | Java specifics: generics, interfaces, abstract classes | `06-lld/01-oop-fundamentals/java-oops.md` | L0.1 |
| L1.1 | SRP — Single Responsibility | `06-lld/02-solid-principles/1. single-responsibility-principle.md` | L0.1 |
| L1.2 | OCP — Open/Closed | `06-lld/02-solid-principles/2. open-closed-principle.md` | L1.1 |
| L1.3 | LSP — Liskov Substitution | `06-lld/02-solid-principles/3. liskov-substitution-principle.md` | L0.1 |
| L1.4 | ISP — Interface Segregation | `06-lld/02-solid-principles/4. interface-segregation-principle.md` | L1.3 |
| L1.5 | DIP — Dependency Inversion | `06-lld/02-solid-principles/5. dependency-inversion-principle.md` | L1.2, L1.4 |
| L2.1 | Creational patterns: Singleton, Factory, Builder | `06-lld/03-design-patterns/` | L1.5 |
| L2.2 | Structural patterns: Adapter, Decorator, Facade, Proxy | `06-lld/03-design-patterns/` | L1.5 |
| L2.3 | Behavioral patterns: Strategy, Observer, Command, Iterator | `06-lld/03-design-patterns/` | L1.5 |
| L3.1 | Java Memory Model: happens-before, visibility, volatile | `06-lld/04-concurrency/concurrency-patterns.md` | L0.2 |
| L3.2 | Synchronization primitives: synchronized, ReentrantLock, RWLock | `06-lld/04-concurrency/concurrency-patterns.md` | L3.1 |
| L3.3 | Atomic classes, CAS, LongAdder | `06-lld/04-concurrency/concurrency-patterns.md` | L3.2 |
| L3.4 | Thread pools: ThreadPoolExecutor, sizing formulas | `06-lld/04-concurrency/concurrency-patterns.md` | L3.2 |
| L3.5 | Semaphore, CountDownLatch, CyclicBarrier | `06-lld/04-concurrency/concurrency-patterns.md` | L3.2 |
| L3.6 | Producer-consumer, BlockingQueue | `06-lld/04-concurrency/producer-consumer.md` | L3.5 |
| L3.7 | Futures, CompletableFuture, async patterns | `06-lld/04-concurrency/futures-async-patterns.md` | L3.4 |
| L4.x | LLD problems 1–26 | `06-lld/05-problems/` | L2.x + L3.x |

### HLD Problem Prerequisites

| Problem | Must Know Before Attempting |
|---|---|
| URL Shortener | 1.4 (cache), 1.8 (consistent hashing), 2.8 (DB selection) |
| Notification System | 3.4 (message brokers), 3.2 (saga), 3.8 (rate limiting) |
| Twitter Feed | 1.4, 1.8, 3.4, fan-out pattern (3.6) |
| Ride Sharing | 1.4, 4.8 (Redis geo), 2.4 (transactions) |
| Distributed Cache | 1.8, 4.8, 1.5 (eviction) |
| Rate Limiter | 3.8, 4.8, 1.8 |
| YouTube | 3.4, 3.7 (stream), 1.10 (CDN), 2.8 |
| Hotel Booking | 2.4, 2.5, 3.2 (saga), 3.3 (outbox) |
| Search Autocomplete | 4.7 (Elasticsearch), 4.8 (Redis), 3.7 (stream) |
| Leaderboard | 4.8 (Redis sorted set), 1.8 |
| GitHub Code Repo | 4.4 (distributed storage), 3.4, 3.5, 4.7 |
| Payment System | 2.4, 2.6, 3.2, 3.3 |
| E-Commerce | 2.4, 3.2, 3.3, 1.4, 1.8 |
| Stock Exchange | 4.1, 4.2, 3.5, 2.5 |

### Study Path by Time Available

**2 weeks (crunch)**
```
Day 1–2:   Tier 0 + Tier 1 (all)
Day 3–4:   Tier 2 (2.1–2.6 only)
Day 5–6:   Tier 3 (3.1–3.6 only)
Day 7:     LLD L0–L3.4
Day 8–9:   Mock: Problems 1, 5, 6 (HLD) + Problem 11 (LLD)
Day 10–11: Tier 4 (4.1–4.5 only)
Day 12–13: Mock: Problems 3, 8 (HLD) + Problem 13, 14 (LLD)
Day 14:    Review anti-patterns + company-specific guide
```

**4 weeks (solid prep)**
```
Week 1: Tier 0 + Tier 1 + Tier 2 (complete) + LLD L0–L2
Week 2: Tier 3 (complete) + Tier 4 (4.1–4.6) + LLD L3
Week 3: Tier 4 (complete) + Tier 5 + LLD problems 1–13
Week 4: Mock interviews daily (1 HLD + 1 LLD per day)
         Self-score with rubric; review weakest dimensions
         Review company-specific guide for target company
```

**8 weeks (thorough)**
```
Week 1: Tier 0 + Tier 1 (+ read all building block files in depth)
Week 2: Tier 2 complete + PostgreSQL/MySQL internals
Week 3: Tier 3 complete + Kafka internals deep dive
Week 4: Tier 4 complete (all DB internals)
Week 5: Tier 5 complete + LLD L0–L3
Week 6: LLD problems 1–26 (2 per day)
Week 7: Mock interviews (1 HLD + 1 LLD daily) + score with rubric
Week 8: Review all concepts scoring < 3; target company guide; question bank
```

### Concept Clusters (for Review Sessions)

| Cluster | Concepts | Why they cluster |
|---|---|---|
| **Consistency cluster** | CAP, PACELC, 2.1–2.7, consensus | All about agreement under failure |
| **Data storage cluster** | B-tree, LSM, MVCC, WAL, isolation levels | All about how DBs store and read data |
| **Async cluster** | Kafka, saga, outbox, CQRS, stream processing | All about decoupling and async workflows |
| **Caching cluster** | Caching-layer, eviction, Redis internals, CDN | All about reducing latency via stored copies |
| **Scaling cluster** | Sharding, consistent hashing, replication, global distribution | All about growing beyond one machine |
| **Resilience cluster** | Circuit breaker, bulkhead, retry, chaos engineering | All about surviving partial failures |
| **Concurrency cluster (LLD)** | JMM, RWLock, CAS, thread pools, Semaphore, producer-consumer | All about safe shared state |
| **Patterns cluster** | Saga, outbox, strangler fig, CQRS, 2PC | All about distributed coordination patterns |

---

## Company-Specific Guide

> Same technical content, different signal-gathering. Each company's interviewers are trained to extract different signals. Calibrate your delivery, not your knowledge.

Before an onsite: read the company section top to bottom, internalize the "what they're really testing" column, review the "avoid" list.

### Meta (Facebook)

**Bar:** SDE-3 at Meta = "works independently on large projects, drives technical decisions across teams, and unblocks others." Bar is explicitly higher than FAANG average.

**Structure:** 1 round, 45 min. Single problem (news feed, notifications, ads, messenger). Interviewer pushes deep on 2–3 specific areas.

| Area | Surface question | Real signal they want |
|---|---|---|
| Scale intuition | "Design News Feed" | Anchor every decision to numbers. Meta = billions of users. |
| Product sense | Any problem | Do questions reveal you understand the product, not just the engineering? |
| Trade-off articulation | "Why not just use X?" | Can you say "X costs Y" without hedging? |
| Operational mindset | "How does this fail?" | Monitoring, alerting, graceful degradation. Missing = SDE-2 signal. |
| Data model depth | "Walk me through your schema" | Reason about denormalization at scale — joins are often impossible at Meta's scale. |

**Patterns to know cold:**
- Fan-out on write vs fan-out on read: push vs pull vs hybrid (~1M followers threshold)
- Ranked feed: score posts (engagement signals, recency decay), serve top-K
- Notification coalescing: "3 people liked your post" is not 3 separate notifications
- Ads auction: second-price auction, pCTR × bid ranking, budget pacing, frequency capping
- TAO: Meta's graph cache layered over MySQL for social graph data

**Interview phases:**
```
0–5 min:   Requirements — drive this; ask 3 sharp questions
5–10 min:  Scale estimation — do it explicitly; they will stop you if you skip
10–25 min: HLD — draw the diagram, narrate every component choice
25–40 min: Deep dive — 1-2 areas; go very deep, not broad
40–45 min: Failure modes + monitoring — explicitly mention alerts, dashboards
```

**SDE-3 phrases:**
- "At Meta's scale, X would bottleneck because Y — so I'd use Z instead."
- "I'd instrument this with P99 latency and error rate metrics; alert if P99 > Xms."
- "The trade-off here is write amplification vs read latency — I'd choose write amplification because reads are 100× more frequent."

**Avoid:**
- Jumping to microservices without justifying scale
- "Eventual consistency" without explaining what the user experiences
- Skipping monitoring (documented signal in Meta's rubric)
- Over-indexing on databases (Meta's problems are often about application-level data structures)

---

### Google

**Bar:** SDE-3 at Google (L5) = "significant scope and impact, recognized technical leader." Highest pure technical depth bar of MAANG. PhD-heavy interviewers comfortable going very deep.

**Structure:** 1–2 rounds. Problems often have Google-internal flavor: search, maps, YouTube, ads, distributed infrastructure. Interviewers often have a specific deep dive prepared.

| Area | Surface question | Real signal they want |
|---|---|---|
| First-principles reasoning | "How would you design distributed storage?" | Reason from scratch, not from AWS service names. |
| Algorithmic depth | Any geo or search problem | Derive the data structure, not just name it. |
| Consistency model mastery | "What consistency does your system need?" | Name the specific model and justify it. |
| Correctness under failure | "What happens when a node crashes mid-write?" | Walk through step by step; "we'd retry" is not enough. |
| Spanner/Bigtable awareness | Cloud storage problems | Know their use cases (Spanner: global transactions; Bigtable: time-series/wide-column). |

**Patterns to know cold:**
- MapReduce / Dataflow model (map → shuffle → reduce, bounded vs unbounded, Apache Beam)
- Consistent hashing + virtual nodes: know the math, why they reduce hotspots
- Spanner's TrueTime: GPS/atomic clock-backed; commit-wait for external consistency
- GFS / Colossus: chunk servers + distributed metadata (single-master metadata is a bottleneck)
- LSM at Google scale: Bigtable and LevelDB both use LSM; know why

**Interview phases:**
```
0–8 min:   Requirements — Google often underspecifies to test if you ask the RIGHT questions
8–15 min:  Scale + constraints — explicit numbers, latency SLOs, durability requirements
15–30 min: HLD — expect "why not X?" interruptions; answer confidently
30–50 min: Deep dive — expect "and what if that fails?", "what's the complexity?", "10× scale?"
50–60 min: Extensions — often ends with "now make it globally distributed"
```

**SDE-3 phrases:**
- "This requires linearizability because clients must read their own writes."
- "The time complexity of this lookup is O(log N) on the consistent hash ring; with virtual nodes it becomes O(log V×N) but hot-spot variance drops by √V."
- "I'd use an LSM-based store here because writes are 10× more frequent than reads."

**Avoid:**
- Managed service name-dropping without explaining the data model
- Stopping at the first answer — Google deliberately probes deeper
- Handwavy consistency: "eventually consistent" without defining what eventual means
- Ignoring correctness: simple but wrong under concurrent writes will be called out

---

### Amazon

**Bar:** SDE-3 at Amazon (SDE III) = "solves ambiguous problems, mentors SDE-IIs, drives multi-team technical decisions." Amazon's interviews are uniquely bifurcated: Leadership Principles matter as much as technical depth.

**Structure:** 1–2 rounds. Amazon/AWS-flavored problems: order processing, fulfillment, DynamoDB-adjacent, S3-adjacent. Interviewers explicitly map answers to LP signals.

| Area | Surface question | Real signal they want |
|---|---|---|
| Customer obsession | "What are the requirements?" | Do first questions focus on user/customer experience? |
| Ownership | "How do you handle failures?" | Design for operational excellence: runbooks, alarms, capacity planning. |
| Dive deep | Any problem | Will you go one level deeper without being prompted? |
| Invent and simplify | "How would you scale this?" | Simplest design that solves the problem. Over-engineering is penalized. |
| Are Right, A Lot | Trade-off questions | Commit to a position and defend it with data. |

**Patterns to know cold:**
- DynamoDB: partition key + sort key design, GSI vs LSI, single-table design, hot partition problem
- SQS + SNS fan-out: SNS topic → multiple SQS queues → consumers; when vs Kafka
- Idempotency + exactly-once: idempotency key pattern, dedup within a time window
- Two-pizza team principle: each service independently deployable with its own DB
- Operational excellence: CloudWatch alarms, runbooks, capacity planning, on-call rotation impact

**Interview phases:**
```
0–5 min:   Requirements — anchor first questions on customer impact
5–10 min:  Scale estimation — order-of-magnitude reasoning
10–25 min: HLD — draw standard AWS architecture; use AWS service names (unlike Google, fine here)
25–40 min: Deep dive — failure handling and operational concerns
40–50 min: LP signals — "tell me about a time you had to make a trade-off like this"
50–60 min: Extensions / scale
```

**SDE-3 phrases:**
- "From a customer perspective, the most critical thing is that orders are never lost — so I'd prioritize durability over latency here."
- "I'd design this so each service owns its own data store — no shared DBs — so teams can deploy independently."
- "For idempotency: the client sends an idempotency key; the server stores key → result in DynamoDB with TTL 24h; duplicate requests return the cached result."
- "I'd add a CloudWatch alarm on queue depth and P99 processing latency; if it fires, the runbook says to scale up the consumer fleet."

**Avoid:**
- Not framing decisions in LP language
- Over-engineering (Amazon's "Invent and Simplify" LP explicitly penalizes this)
- Assuming AWS — ask "are we designing for AWS or cloud-agnostic?" first
- No operational story: designs without alarms, dashboards, and failure runbooks signal SDE-2

---

### Apple

**Bar:** SDE-3 at Apple = "deep technical expert, works cross-functionally, designs for privacy and reliability." Known for: privacy-first thinking, hardware/software co-design awareness.

**Structure:** 1–2 rounds, sometimes split HLD + deep-dive on separate days. Problems reflect Apple product surfaces: iCloud sync, App Store, Apple Pay, Siri, Maps, Health.

| Area | Surface question | Real signal they want |
|---|---|---|
| Privacy by design | Any problem involving user data | Ask "what data do we actually need?" and propose minimization before building. |
| Reliability | "How does this fail?" | Design for graceful degradation and offline-first. |
| Simplicity | Design a feature | Apple's design philosophy extends to engineering. |
| Cross-functional awareness | Anything iCloud/device | Know where computation should happen: on device vs server. |
| Security depth | Auth, sync, payments | Know E2E encryption, Secure Enclave, certificate pinning, and why they matter. |

**Patterns to know cold:**
- iCloud sync model — CloudKit: change tokens, `recordChangeTag`, optimistic concurrency
- E2E encryption: asymmetric key exchange (ECDH), per-message symmetric (AES-256), iCloud Keychain key escrow
- On-device ML: CoreML, Neural Engine; tradeoff vs server inference
- Apple Pay / Secure Enclave: PAN → device account number tokenization, NFC contactless

**Interview phases:**
```
0–8 min:   Requirements — ask "What data does this system need to retain?" early
8–15 min:  Constraints — on-device vs server, offline support
15–30 min: HLD — draw both device + server halves
30–45 min: Deep dive — sync protocol, conflict resolution, or security model
45–60 min: Edge cases — "What if the user has 14 devices and changes on 3 simultaneously?"
```

**SDE-3 phrases:**
- "Before deciding where to store this data, I want to understand what the minimum necessary data set is."
- "For sync conflict resolution: last-write-wins is simple but creates data loss; I'd use a CRDT or server-authoritative merge with user-visible conflict UI for high-value data."
- "This should be E2E encrypted — Apple's server should hold only ciphertext; decryption happens on-device."
- "I'd make this work offline-first — the device is the source of truth; sync to server when connectivity is available."

**Avoid:**
- Privacy afterthought: adding "and of course we'd encrypt the data" at the end
- Server-first thinking when on-device is feasible
- Ignoring conflict resolution for sync problems
- Handwavy security: "we'll use TLS and OAuth" is not sufficient

---

### Cross-Company Comparison

| Dimension | Meta | Google | Amazon | Apple |
|---|---|---|---|---|
| Primary signal | Scale reasoning + product sense | First-principles + algorithmic depth | Ownership + LP alignment + simplicity | Privacy-first + reliability + sync |
| Tone | Fast-paced, push back expected | Academic, depth > breadth | Structured, LP framing throughout | Deliberate, detail-oriented |
| Managed services | Fine to use | Reason from principles first | AWS services expected | Apple platforms expected |
| Key differentiator topic | Fan-out at 1B users | Consistency models + distributed correctness | Idempotency + operational excellence | E2E encryption + offline-first sync |
| Biggest mistake | Skip monitoring / vague trade-offs | Stop at first answer / handwavy consistency | Over-engineer / ignore LPs | Privacy afterthought / server-first |
| What makes you SDE-3 vs SDE-2 | Monitoring + operational story | Multi-layer depth + correctness proofs | Cross-team design + LP anchoring | Privacy architecture + conflict resolution |

### Universal SDE-3 Signals (All Companies)

```
1. You drive the structure — you don't wait to be asked "what about caching?"
2. You anchor every choice in numbers — "I'd shard because we'll exceed single-node write capacity at 10K writes/sec"
3. You name trade-offs explicitly — not just "I chose X" but "X over Y because Z, at the cost of W"
4. You address failure modes unprompted — at least 2: what if the DB is down, what if the cache is poisoned
5. You mention operational concerns — monitoring, alerting, on-call impact
6. You distinguish between what to build now vs later — "in v1 I'd use X; at 10× scale we'd need Y"
7. You know when to stop going deeper — SDE-3 spends time on the 20% of decisions that matter, not perfect coverage
```

### Quick Reference: Problem → Company Likelihood

| Problem | Most likely at | Key angle to prepare |
|---|---|---|
| News Feed / Social Graph | Meta | Fan-out at scale, feed ranking, TAO-like graph cache |
| Ads System | Meta, Google | Auction mechanics, budget pacing, click aggregation |
| Distributed Storage | Google, Amazon | LSM vs B-tree, consistency model, replication |
| Search | Google | Inverted index, ranking, crawl pipeline, freshness |
| Payment System | Amazon, Apple | Idempotency, exactly-once, ledger, reconciliation |
| Maps / Geo | Google, Apple | Geo-indexing, routing, ETA, tile serving |
| Chat / Messaging | Meta, Apple | WebSocket, message ordering, E2E encryption |
| Cloud Sync | Apple, Google | Conflict resolution, change tokens, offline-first |
| Recommendation | Meta, Amazon | Collaborative filtering, embedding retrieval, feedback loop |
| Job Scheduler | Amazon, Google | Distributed coordination, priority queues, fault tolerance |

---

## Security & Compliance Checklist

> Interview focus: architecture-level decisions, not code-level vulnerability fixes. When asked "how would you secure this system?", answer in terms of perimeter, identity, data, and audit.

### Quick Checklist (8 Items)

```
□ 1. AuthN/AuthZ:  who can call this service? (OAuth 2.0 / API key / mTLS for service-to-service)
□ 2. Token lifecycle: how are tokens issued, refreshed, and revoked?
□ 3. Data in transit: TLS everywhere, mTLS for internal service calls
□ 4. Data at rest: encryption for PII / secrets; KMS for key management
□ 5. Secret management: no secrets in env vars or code; use Vault/AWS Secrets Manager
□ 6. Zero-trust network: no implicit trust on the internal network; verify every call
□ 7. Audit logging: immutable log of who did what to which resource and when
□ 8. Compliance hooks: data residency, PII retention limits, right-to-erasure
```

### Authentication & Authorization

**Protocol Selection by Use Case**

| Caller | Protocol | Reason |
|---|---|---|
| Browser/mobile (user-facing) | OAuth 2.0 + OIDC | Federated identity, user consent, standard token format |
| Service-to-service (internal) | mTLS or JWT with short TTL | Machine identity via cert or signed token |
| Third-party API integration | OAuth 2.0 Client Credentials | Scoped access without sharing credentials |
| External developer (API key) | API key + HMAC signing | Simple; suitable for webhook receivers and SDKs |

**OAuth 2.0 Flow Selection**

```
Authorization Code + PKCE  → web/mobile apps where user grants consent
  1. App redirects to Auth Server with code_challenge
  2. User logs in, grants consent
  3. Auth Server returns authorization code
  4. App exchanges code + code_verifier for access_token + refresh_token
  5. Use access_token (short TTL: 15 min) for API calls
  6. Use refresh_token (long TTL: 30 days) to get new access_tokens

Client Credentials → service-to-service (no user)
  1. Service POSTs client_id + client_secret to token endpoint
  2. Gets access_token with requested scope
  Note: client_secret stored in Vault, not env var

Device Code → TV apps, IoT where keyboard input is hard
Implicit → DEPRECATED (replaced by Auth Code + PKCE)
```

**JWT Structure and Security**

```
JWT = base64url(header) . base64url(payload) . signature

Header: { "alg": "RS256", "typ": "JWT" }
Payload: {
  "sub": "user_id_123",
  "iss": "https://auth.company.com",
  "aud": "https://api.company.com",
  "exp": 1718000000,
  "iat": 1717999000,
  "scope": "read:orders write:orders",
  "jti": "unique-token-id"
}
```

Critical JWT fields: validate `exp`, `aud`, `iss`. Short TTL (15 min for access tokens). Revoke via `jti` blocklist in Redis with TTL = token TTL.

**Token Revocation**

```
Option 1: Short TTL (15 min) + refresh token rotation
  - Compromised token valid for ≤ 15 min; on logout, invalidate refresh token

Option 2: JWT token blocklist
  - On logout: store jti in Redis with TTL = remaining token lifetime
  - Every validation checks Redis: O(1) lookup; Redis becomes critical dependency
  - Use when: compliance requires immediate revocation

Option 3: Opaque tokens (reference tokens)
  - Auth server issues random string; API gateway calls introspection on every request
  - Revocation is trivial; cost: network hop per request (mitigate: cache 30s)
  - Use when: maximum revocation control required
```

**RBAC vs ABAC**

```
RBAC: User → has roles → roles have permissions. Simple; use for most systems.

ABAC: Policy engine evaluates user attributes + resource attributes + environment.
      Flexible but complex; use for fine-grained multi-tenant access (OPA / Cedar).

Interview shortcut:
  "RBAC at the service boundary (can this user access Orders Service?)
   and ABAC or ownership checks within the service (can this user edit THIS specific order?)"
```

### Data in Transit

```
External traffic: TLS 1.2 minimum, TLS 1.3 preferred
  - Terminate TLS at load balancer / API gateway
  - Use HSTS; use managed certs (AWS ACM) for auto-rotation

Internal (service-to-service):
  Option 1: mTLS — both sides present certificates; mutual identity
  Option 2: JWT Bearer token — signed JWT to each downstream service
  Recommendation: mTLS via service mesh (Istio) — transparent to app code
```

**mTLS for Service Identity**

Each service has a certificate issued by internal CA (SPIFFE/SPIRE or Istio CA). Certificate contains workload identity (SPIFFE URI). On each connection both sides present and validate certs. Lateral movement attacks blocked — compromised service A can't impersonate service B.

### Data at Rest

**Encryption Strategy**

```
Layer 1: Disk/storage encryption
  S3 SSE-KMS, EBS encryption, RDS encryption at rest. Protects against physical disk theft.

Layer 2: Application-level encryption (field-level)
  Encrypt specific sensitive fields (SSN, CC number, PII) before writing to DB.
  Use for: PCI DSS, HIPAA.

Layer 3: Client-side encryption
  Data encrypted on client before leaving device. Server never sees plaintext.
  Use for: E2E encrypted messaging, zero-knowledge architectures.
```

**Key Management**

```
NEVER store encryption keys in: application code, environment variables, DB alongside data.

DO:
  - Use managed KMS (AWS KMS, GCP Cloud KMS, HashiCorp Vault)
  - Envelope encryption: data key encrypts data; master key encrypts data key.
    Master key never leaves KMS.
  - Key rotation: rotate data keys annually; envelope encryption makes this cheap
    (re-encrypt only data keys, not data).

Application secrets: store in Vault / AWS Secrets Manager. Inject ephemerally.
Never: git-committed secrets, plaintext in Kubernetes ConfigMaps, hardcoded in Docker images.
```

### Zero-Trust Network Model

```
Traditional perimeter: "inside the firewall = trusted" — compromised VM can reach any service.

Zero-trust: "never trust, always verify"
  - Every request authenticated and authorized regardless of source IP
  - Least-privilege access per service

Implementation:
  1. Service identity (mTLS / SPIFFE): every service has a cryptographic identity
  2. Per-call authorization: OPA sidecar / Istio AuthorizationPolicy
  3. Network segmentation:
     - Public-facing tier: API gateway, web servers
     - Application tier: business logic (no direct external access)
     - Data tier: databases, caches (application tier only)
  4. Egress control: restrict outbound connections per service
```

### Audit Logging

**What to Log**

```
Log:
  - Authentication events: login, logout, failed login, token refresh
  - Authorization decisions: access granted, access denied
  - Data mutations: who created/updated/deleted which record and when
  - Admin actions: role assignments, config changes, key rotations

Don't log:
  - Passwords, secrets, tokens (use reference IDs)
  - Full PII in plaintext (log user_id, not email + SSN)
```

**Audit Log Architecture**

```
Requirements: immutable, tamper-evident, durable, queryable.

Application → structured JSON events → Kafka topic "audit.events"
Kafka consumer → S3 (immutable, versioned bucket + S3 Object Lock COMPLIANCE mode)
              → Elasticsearch (queryable)

S3 Object Lock COMPLIANCE: even root account cannot delete logs during retention period.
Retention: 7 years (financial), 6 years (HIPAA).

Tamper evidence:
  Option 1: hash chaining (each entry includes hash of previous)
  Option 2: append-only ledger (AWS QLDB)
```

### Compliance Frameworks

**Data Residency**

```
Requirement: data for EU users must not leave EU (GDPR Article 44).

Architecture:
  1. GeoDNS: route EU users to EU deployment
  2. Separate data stores per region: EU Postgres cluster, US Postgres cluster
  3. Backups: S3 buckets in same region; no cross-region replication for PII
  4. Tag PII fields with data_residency_zone for automated policy enforcement
```

**PII Handling**

```
PII categories (GDPR): name, email, phone, IP, location, cookies, health, financial, biometrics.

Minimize: don't collect what you don't need.
Pseudonymization: replace identifiers with user_id in analytics; map stored separately.
Encryption at field level: SSN, payment info, health data before storing.
Retention limits: define max per data type; automated nightly deletion job.
```

**Right to Erasure (GDPR Article 17)**

```
Implementation:
  1. User Deletion Service: publishes "user.deletion.requested" to Kafka;
     each service has a consumer for its own data.
  2. Soft delete first: mark deleted_at; background job hard-deletes after 30-day recovery window.
  3. Anonymization for records with legal retention: replace user_id with "DELETED_USER",
     clear PII fields (order records with 7-year legal hold retain financial data).
  4. Data warehouse: re-export partitions with deleted user filtered, or maintain deletion list
     filtered at query time.
  5. Backups: don't restore without re-applying deletion list.
```

### OWASP Top-10 at Architecture Level

| OWASP Category | Architecture-Level Mitigation |
|---|---|
| A01 Broken Access Control | RBAC at API gateway; per-resource ownership check at service; deny by default |
| A02 Cryptographic Failures | TLS everywhere; KMS for keys; no plaintext PII in logs or S3 |
| A03 Injection | Parameterized queries (code-level); WAF for SQL/NoSQL injection patterns |
| A04 Insecure Design | Threat modeling during architecture review; principle of least privilege |
| A05 Security Misconfiguration | IaC for all infra; CIS benchmark scanning (Checkov) |
| A06 Vulnerable Components | Dependency scanning in CI (Snyk/Dependabot); container scanning (Trivy) |
| A07 AuthN/AuthZ Failures | MFA for admin; short-lived tokens; session revocation; no shared credentials |
| A08 Data Integrity Failures | Signed artifacts in CI/CD pipeline; SRI hashes for CDN assets |
| A09 Logging Failures | Centralized immutable audit log; alerting on auth failures |
| A10 SSRF | Egress allowlist per service; block metadata endpoint (169.254.169.254) |

### Interview Answer Framework

When asked "How would you secure system X?":

```
1. Perimeter: What can reach the system from outside?
   → API gateway terminates external TLS; WAF for injection/DDoS
   → Public endpoints explicitly listed; everything else private

2. Identity: Who is calling each service?
   → Users: OAuth 2.0 + JWT (short TTL, validate aud/iss/exp)
   → Services: mTLS with SPIFFE identity
   → Least privilege: each service has only the permissions it needs

3. Data: What sensitive data exists?
   → PII / secrets: encrypted at rest with KMS; field-level for high sensitivity
   → In transit: TLS 1.3; mTLS internal
   → Secrets: Vault dynamic secrets; never in env vars or code

4. Audit: How do you know when something goes wrong?
   → Immutable audit log: who accessed what, when
   → Alerting: anomaly detection on auth failures
   → Incident response: 1 year queryable, 7 years archived

5. Compliance: Any regulatory constraints?
   → Data residency: GeoDNS + region-isolated storage
   → Right to erasure: User Deletion Service coordinates across all services
   → Retention limits: automated deletion jobs per data type
```

### Quick Revision

- **OAuth 2.0**: Authorization Code + PKCE for user-facing; Client Credentials for service-to-service
- **JWT**: validate `exp`, `aud`, `iss`; short TTL (15 min); revoke via `jti` blocklist in Redis
- **mTLS**: both sides present certs; service identity is cryptographic; required for zero-trust
- **Zero-trust**: never trust by network location; always verify; least-privilege egress policies
- **Audit logs**: immutable (S3 Object Lock COMPLIANCE), structured JSON, no PII in plaintext
- **Right to erasure**: User Deletion Service + Kafka fan-out; anonymize where deletion is impossible
- **Key management**: envelope encryption; keys in KMS, not env vars; rotate via re-encrypting data keys
