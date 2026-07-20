# System Design Repo — Comprehensive Audit
*Date: 2026-07-01 | Auditor: Staff/Principal Engineer perspective*

> **RESOLVED — 2026-07-21.** All 20 ranked improvements and both integrity bugs below have been executed. The unchecked boxes and "27 HLD / 11 hard" counts in this document describe the pre-fix state and are kept only as a record of what was addressed. Current tree: 36 HLD (easy 9 / medium 7 / hard 20), 36 LLD; START-HERE counts match; parking-lot `to_vehicle_type()` removed. Delivered work includes expanded `04-advanced-topics/03-internals/03-kafka-internals.md`, a full consistency ladder in `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md`, cache-stampede coverage in `02-building-blocks/02-performance/01-caching-layer.md`, delivery guarantees in `02-building-blocks/04-coordination/01-message-brokers.md`, sharding/replication depth in `03-scaling/03-database-scaling.md`, and new Observability (`04-advanced-topics/02-system-reliability/01-observability.md`), Security (`01-foundations/04-security/01-security.md`), and `LEARNING-PATH.md` pages. Read this file as history, not a to-do list.

This audit is based on a full read of a 20-file representative sample spanning foundations, building blocks, scaling, advanced internals, HLD problems (easy/medium/hard), LLD problems, patterns, and reference material. The tone is deliberately critical: the goal is not to praise what exists but to expose what will get a candidate rejected at an SDE-3/Staff loop.

**Headline verdict:** The repo is a strong *intermediate* study aid with excellent HLD problem write-ups and a genuinely good interview framework, dragged down by shallow foundational/internals pages, a metadata/counting integrity problem, and gaps in the exact "senior signal" topics (delivery guarantees, consistency models, sharding mechanics, observability) that separate a mid-level pass from a Staff-level hire.

---

## 1. Coverage Completeness

| Area | Coverage | Gap Severity | What to do |
|------|----------|--------------|------------|
| Fundamentals (CAP, latency, building blocks) | Good | Low | Add PACELC, tail latency (p99/p999) |
| Consistency models | **Shallow** | **High** | Add linearizability, causal, read-your-writes, monotonic reads, CRDTs |
| Load balancing | Good | Medium | Add consistent hashing, GSLB/DNS, connection draining, LB HA |
| Caching | Good | Medium | Add stampede/thundering herd depth, TTL jitter, negative caching, invalidation |
| Message brokers / Kafka | Partial | **High** | Add delivery guarantees, ISR, acks, log compaction, exactly-once |
| DB scaling | Good | Medium | Add replication lag, sharding strategies, resharding, hot partitions |
| Microservices | Good | Medium | Add API gateway, service mesh, distributed tracing, saga cross-link |
| HLD problems (easy/med/hard) | **Excellent** | Low | Keep; add per-problem back-of-envelope reconciliation |
| LLD problems | **Excellent** | Low | Add concurrency test harness sections consistently |
| Patterns (saga etc.) | Strong | Low | Add outbox, CDC, 2PC-vs-saga decision table |
| Observability / SRE | **Missing** | **High** | Add metrics/logs/traces, SLO/SLA/error budgets, on-call |
| Security | **Missing** | **High** | Add authN/authZ, OAuth/JWT, rate-limit-as-security, encryption at rest/in transit |
| Data-intensive (stream processing, OLAP) | Thin | Medium | Add batch vs stream, Lambda/Kappa, columnar stores |

---

## 2. Interview Readiness per Topic

1. **CAP / fundamentals** — Ready for mid-level. For Staff, the page stops at "pick 2 of 3," which is the naive framing. **Add PACELC and the fact that partitions are rare so the real daily trade-off is latency-vs-consistency.**
2. **Consistency & conflicts** — **Not ready.** Filed under "advanced distributed theory" but only covers strong/eventual, LWW, vector clocks, split-brain/quorum. A Staff interviewer will ask "linearizable vs serializable?" and the reader has nothing. **Add the full consistency ladder.**
3. **Load balancing** — Ready for mid-level; **not ready** for "how does the LB itself not become a SPOF / how do you scale it." Add consistent hashing and LB HA (active-passive, anycast).
4. **Caching** — Mostly ready; the summary name-drops cache stampede but the body never teaches the fix (locking, request coalescing, probabilistic early expiry). **Close that gap.**
5. **Message brokers / Kafka internals** — **Not ready for senior.** Kafka page is a great ELI5 but omits acks (0/1/all), ISR, replication factor, leader election, log compaction, and exactly-once. These are the *most-asked* Kafka follow-ups. **This is the single highest-priority content fix.**
6. **DB scaling** — Ready for the "staircase" question; **not ready** for "how do you choose a shard key" or "what happens on replication lag / read-after-write." Add those.
7. **Microservices** — Ready conceptually; missing the operational depth (gateway, mesh, tracing) that Staff candidates must speak to.
8. **HLD problems** — **Ready and strong** (URL shortener, Twitter feed, payments, Ticketmaster). These are interview-grade.
9. **LLD problems** — **Ready and strong** (parking lot, LRU). Correct patterns, working code, concurrency reasoning, tiered questions.
10. **Saga / consistency patterns** — Ready and strong.

---

## 3. Clarity & Engagement Issues

The analogy-driven ELI5 voice is the repo's biggest strength and, in the foundational pages, its biggest liability — it substitutes vividness for precision.

- **Over-cute at the cost of correctness.** Kafka: "it is incredibly dumb." Rewrite: *"Kafka is fast because it exploits sequential disk I/O and an append-only log — it trades random-access flexibility for raw write throughput."*
- **Absolute claims that an interviewer will punish.** DB-scaling: "If your first answer is 'I will shard,' you will fail the interview." Rewrite: *"Reach for sharding last; interviewers want to see you exhaust indexing, caching, and replication first."*
- **Analogy left un-cashed-out.** Caching mentions the stampede in the summary but the body never returns to it. Rewrite: add one line — *"When the sticky note expires and 10,000 users ask at once, they all hit the DB simultaneously (cache stampede); fix it with a mutex lock or probabilistic early recompute."*
- **"Golden rule" inflation.** Multiple pages each declare a different "Golden Rule." Rewrite: reserve the phrase for one genuinely universal principle per module, or the emphasis stops meaning anything.
- **Emoji headers hurt scannability at senior level.** Keep them in easy content; strip them from the reference/advanced pages so they read as authoritative.

---

## 4. Missing Industry Knowledge

Specific sub-topics absent from the sample that a Staff loop expects:

- **Delivery semantics:** at-most / at-least / exactly-once, idempotent consumers, dedup with the outbox pattern.
- **Kafka internals:** acks, ISR, min.insync.replicas, leader election, log compaction, tiered storage, zero-copy.
- **Consistency ladder:** linearizability vs serializability, causal consistency, read-your-writes, monotonic reads/writes, CRDTs.
- **Sharding mechanics:** range vs hash vs geo vs directory-based, shard-key selection, hot partitions, resharding / consistent hashing rebalancing.
- **Replication:** sync vs async, replication lag, read-after-write consistency, failover / leader election (Raft/Paxos at a conceptual level).
- **Observability:** the three pillars (metrics/logs/traces), RED/USE methods, SLO/SLI/error budgets, distributed tracing.
- **Security:** OAuth2/OIDC, JWT, mTLS, encryption at rest/in transit, secrets management, WAF, DDoS.
- **Networking depth:** consistent hashing, GSLB/anycast, TCP vs QUIC, gRPC vs REST vs GraphQL trade-offs.
- **Data platform:** batch vs stream, Lambda/Kappa, CDC, OLAP/columnar, data lake vs warehouse.
- **Cost/capacity:** cost-per-request reasoning tied back to the good numbers page.

---

## 5. HLD Problem Quality

| Problem | Requirements | Estimation | HLD depth | Deep dives | Trade-offs | Score /10 |
|---------|-------------|------------|-----------|------------|------------|-----------|
| URL Shortener (easy) | Clear | Present | Base62 + 3 ID schemes | 3 real deep dives | Redis INCR vs ticket vs Snowflake | **9** |
| Twitter News Feed (medium) | Clear | Present | Hybrid fan-out | Celebrity, sharding, trending | Fan-out write vs read | **9** |
| Payment System (hard) | Clear | Present | Ledger + idempotency | Reconciliation, PCI DSS, SQL/Python | Strong-consistency justification | **9** |
| Ticketmaster (hard) | Clear | Present | Seat lock + waiting room | SETNX, FOR UPDATE SKIP LOCKED, waitlist | Pessimistic vs optimistic vs Redis | **9** |

These four are the crown jewels. The only systematic gap: **estimation numbers are stated but rarely reconciled back into the design** (e.g., "we need X QPS → therefore Y shards → therefore this instance type"). Add that closing loop to push each from a 9 to a 10.

---

## 6. LLD Quality Assessment

**Parking Lot** — Excellent. Correct pattern selection (Singleton/Strategy/Factory), explicit clarifying-question dialogue, in-scope/out-of-scope, per-floor locking that correctly identifies and fixes the global-lock bottleneck, and four realistic extensibility deep dives (EV, reservations, dynamic pricing). Tiered junior/mid/senior questions are exactly right. Minor nit: `spot_type.to_vehicle_type()` is referenced but never defined — **fix the dangling method or note it as pseudocode.**

**LRU Cache** — Excellent. Correct HashMap + doubly-linked-list with sentinels, clean O(1) reasoning, honest treatment of why singly-linked fails, thread-safety options (coarse lock → RW lock caveat → sharding → CAS), LRU-vs-LFU table, distributed and TTL extensions, and a correct OrderedDict note. This is genuinely interview-grade.

**Systematic strengths:** both follow an identical rigorous skeleton (Understand → Clarify → Requirements → Entities → Class Design → Implementation → Verification → Deep Dive → Tiered Questions). **Recommendation:** enforce this skeleton across all LLD problems and add a *concurrency test harness* section (the parking-lot "100 threads, 50 spots" idea) to every applicable problem.

---

## 7. Learning Flow Review

The repo is well-numbered but has no explicit *path*. Recommended sequences:

- **Beginner:** Framework → 01 Fundamentals → Scaling Fundamentals → DB Scaling → Load Balancers → Caching → one easy HLD (URL Shortener) → one LLD (LRU).
- **Intermediate:** + Message Brokers → Microservices → Saga → medium HLD (Twitter) → parking-lot LLD → numbers-to-know.
- **SDE-3/Staff:** + (once written) Consistency Ladder → Kafka Internals (expanded) → Sharding/Replication deep dive → Observability → Security → hard HLDs (Payments, Ticketmaster) → design a novel system unaided against the framework's 7 phases.

**Add a `LEARNING-PATH.md`** with these three tracks and estimated hours; right now a learner has to invent their own order.

---

## 8. Note Quality Scorecard

| # | File | Score /10 | Note |
|---|------|-----------|------|
| 1 | START-HERE.md | 6 | Good index; **count inconsistencies** (see below) |
| 2 | SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md | 9 | Strong 7-phase framework |
| 3 | 01-fundamentals.md | 8 | Solid; add PACELC, tail latency |
| 4 | 05-advanced/01-consistency-and-conflicts.md | 5 | Too shallow for "advanced" |
| 5 | 01-load-balancers.md | 7 | Missing consistent hashing, GSLB, LB HA |
| 6 | 01-caching-layer.md | 7 | Stampede named but not taught |
| 7 | 01-message-brokers.md | 6 | Missing delivery guarantees, ordering, backpressure |
| 8 | 01-scaling-fundamentals.md | 8 | Clear; stateless golden rule good |
| 9 | 03-database-scaling.md | 7 | Missing replication lag, shard strategies |
| 10 | 03-microservices.md | 7 | Missing gateway/mesh/tracing/saga link |
| 11 | 03-kafka-internals.md | 6 | Great ELI5, missing all the senior follow-ups |
| 12 | url-shortener.md | 9 | Interview-grade |
| 13 | twitter-news-feed.md | 9 | Interview-grade |
| 14 | payment-system.md | 9 | Interview-grade, has code |
| 15 | ticketmaster-seat-booking.md | 9 | Interview-grade, has code |
| 16 | saga-pattern.md | 9 | Strong, mindmap + talking points |
| 17 | numbers-to-know.md | 9 | Thorough reference |
| 18 | 07-interview-templates/README.md | 8 | Good index |
| 19 | 01-design-parking-lot.md | 9 | Excellent LLD |
| 20 | 13-design-lru-cache.md | 9 | Excellent LLD |

**Integrity finding:** START-HERE.md's stated counts (27 HLD / 11 hard / 26 LLD / 21 patterns) do not match the actual tree (19 hard, 36 LLD per the repo structure). **Audit and auto-generate these counts in CI so the index never lies.** A wrong self-count is the first thing a detail-oriented reviewer notices.

---

## 9. Improvement Roadmap

**Critical Fixes (do first)**
1. Expand Kafka internals: acks, ISR, replication factor, leader election, log compaction, exactly-once.
2. Rewrite consistency-and-conflicts into a real consistency ladder.
3. Fix START-HERE.md counts and add CI count-verification.
4. Teach cache stampede fix in the caching body, not just the summary.

**High Impact**
5. Add delivery-guarantee coverage to message-brokers.
6. Add sharding strategies + replication lag + read-after-write to DB scaling.
7. Add a new Observability module (metrics/logs/traces, SLO/error budgets).
8. Add a new Security module (OAuth/JWT/mTLS/encryption).

**Nice to Have**
9. Add consistent hashing + GSLB + LB HA to load balancers.
10. Add API gateway / service mesh / tracing to microservices.
11. Reconcile estimation numbers into each HLD design.
12. Add `LEARNING-PATH.md` with three tracks.

**Missing Topics (net-new pages)**
Observability/SRE • Security • Stream vs batch processing • CDC/outbox • gRPC/GraphQL/REST trade-offs • Rate limiting as a first-class topic • Consensus (Raft/Paxos conceptual).

**Sections to Rewrite**
consistency-and-conflicts • kafka-internals (expand) • caching (finish the stampede thread) • START-HERE counts.

**Sections to Expand**
message-brokers • database-scaling • microservices • load-balancers • fundamentals (PACELC/tail latency).

---

## 10. Final Deliverable

**Overall repo score: 78/100.** Elite HLD/LLD problem library and a genuinely good framework, held back by shallow foundational/internals pages, missing observability/security modules, and a self-reporting integrity bug.

**Interview readiness: 72/100.** A candidate who studies only this repo is well-prepared for mid-level and for HLD/LLD *problems*, but exposed on the senior-signal follow-ups (delivery guarantees, consistency models, sharding mechanics, observability, security).

**Missing-topics checklist**
- [ ] Kafka acks/ISR/log compaction/exactly-once
- [ ] Consistency ladder (linearizability, causal, RYW, CRDTs)
- [ ] Delivery semantics + outbox/idempotent consumers
- [ ] Sharding strategies + shard-key selection + resharding
- [ ] Replication lag + read-after-write + failover
- [ ] Observability (metrics/logs/traces, SLO/error budgets)
- [ ] Security (OAuth/JWT/mTLS/encryption)
- [ ] Consistent hashing + GSLB + LB HA
- [ ] API gateway / service mesh / distributed tracing
- [ ] Stream vs batch, CDC, OLAP/columnar
- [ ] gRPC vs REST vs GraphQL
- [ ] Consensus (Raft/Paxos conceptual)

**Top 20 improvements, ranked**
1. Expand Kafka internals. 2. Rewrite consistency ladder. 3. Fix + CI-verify counts. 4. Teach cache stampede fix. 5. Add delivery guarantees. 6. Add sharding strategies. 7. Add replication lag / RYW. 8. New Observability module. 9. New Security module. 10. Consistent hashing + LB HA. 11. API gateway / mesh / tracing. 12. Reconcile estimation into HLD designs. 13. `LEARNING-PATH.md`. 14. Add concurrency test-harness sections to LLD. 15. PACELC + tail latency in fundamentals. 16. Outbox/CDC pattern page. 17. gRPC/REST/GraphQL comparison. 18. Consensus page. 19. Strip emoji from advanced/reference pages. 20. Fix parking-lot `to_vehicle_type()` dangling method.

**World-class chapter structure**
1. Framework & Estimation → 2. Fundamentals (CAP/PACELC, latency, tail) → 3. Networking (LB, consistent hashing, protocols) → 4. Storage (SQL/NoSQL, indexing, replication, sharding) → 5. Caching → 6. Messaging & Streaming (brokers, delivery, Kafka internals) → 7. Consistency & Consensus (ladder, Raft/Paxos, CRDTs) → 8. Microservices & Patterns (saga, outbox, CDC, gateway, mesh) → 9. Observability & Reliability (SLO, tracing, on-call) → 10. Security → 11. HLD Problem Library → 12. LLD Problem Library → 13. Numbers & Cheatsheets.

**Prioritized action plan**
- **Week 1:** Kafka expansion, consistency ladder, count fix + CI, cache stampede. (Closes the highest-severity senior gaps.)
- **Week 2:** Delivery guarantees, sharding/replication depth, Observability module.
- **Week 3:** Security module, consistent hashing/LB HA, gateway/mesh/tracing, LEARNING-PATH.md.
- **Week 4:** Reconcile HLD estimation loops, LLD test-harness sections, net-new pages (outbox/CDC, protocols, consensus), tone/emoji cleanup on advanced pages.

The foundation is better than most public repos. Close the senior-signal gaps and fix the integrity bug, and this moves from a solid 78 to a genuine 90+.
