# Learning Path

Three tracks for interview prep, based on where you are. Each track is a sequence — earlier topics give you vocabulary for later ones. The estimated hours assume active reading with note-taking, not passive skimming.

---

## Track 1 — Beginner (0–6 months experience)

Goal: understand what distributed systems are, why they're hard, and be able to discuss a simple system end-to-end.

**Start here:**
1. `START-HERE.md` — orientation and how to navigate the repo
2. `01-foundations/01-system-design-basics/01-fundamentals.md` — the four axes: scalability, availability, consistency, performance
3. `01-foundations/02-hardware-and-networking/02-networking.md` — DNS, TCP, HTTP: how a URL becomes a response
4. `01-foundations/02-hardware-and-networking/01-storage-fundamentals.md` — disk, memory, and the latency hierarchy
5. `01-foundations/03-database-foundations/01-databases.md` — relational vs NoSQL, when to use each
6. `02-building-blocks/01-networking/01-load-balancers.md` — why you need more than one server
7. `02-building-blocks/02-performance/01-caching-layer.md` — why caches exist
8. `02-building-blocks/01-networking/05-cdn.md` — why CDNs exist

**First HLD problems (Easy tier — `05-hld-problems/01-easy/`):**
- `url-shortener.md` — classic starter: hashing, DB, redirection
- `pastebin.md` — similar to URL shortener, adds storage considerations
- `rate-limiter.md` — applies caching + token bucket theory
- `unique-id-generator.md` — introduces distributed ID generation

**Estimated time:** 20–30 hours

---

## Track 2 — Intermediate (6 months – 2 years)

Goal: handle end-to-end system design for medium-complexity systems. Understand consistency trade-offs, basic scaling, and the components of a real production system.

**Prerequisites:** Track 1 complete, or equivalent experience.

**Core building blocks:**
1. `01-foundations/01-system-design-basics/01-fundamentals.md` — CAP and PACELC: the fundamental trade-off
2. `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md` — eventual vs strong consistency
3. `01-foundations/04-security/01-security.md` — TLS, encryption, authentication
4. `01-foundations/04-security/02-oauth-jwt.md` — auth in production
5. `02-building-blocks/03-data-partitioning/01-sharding.md` — partitioning data across nodes
6. `02-building-blocks/04-coordination/01-message-brokers.md` — Kafka, SQS, async patterns
7. `03-scaling/01-scaling-fundamentals.md` — stateless services and horizontal scale
8. `03-scaling/03-database-scaling.md` — replication, sharding, read replicas
9. `02-building-blocks/01-networking/03-api-gateway.md` — gateway patterns

**Medium HLD problems (`05-hld-problems/02-medium/`):**
- `notification-service.md` — applies message brokers, fan-out
- `twitter-news-feed.md` — introduces feed generation, fan-out on write/read trade-offs
- `whatsapp.md` — real-time messaging, presence, delivery guarantees
- `youtube.md` — video storage, CDN, transcoding pipeline
- `instagram.md` — social graph, media storage, feed generation

**Estimated time:** 40–60 hours

---

## Track 3 — SDE-3 / Staff (2+ years, targeting FAANG-tier)

Goal: demonstrate production depth in system design interviews — not just "what" to use but "why," "what goes wrong," and "how to operate it." This is the level expected for L5/E5/SDE-3 and above.

**Prerequisites:** Track 2 complete, or equivalent production experience.

**Advanced distributed theory:**
1. `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md` — linearizability, CRDTs, vector clocks, PACELC
2. `03-scaling/02-database-internals.md` — storage engine internals
3. `04-advanced-topics/03-internals/03-kafka-internals.md` — acks, ISR, exactly-once, leader election
4. `01-foundations/05-advanced-distributed-theory/02-consensus-algorithms.md` — Raft / Paxos, when consensus matters
5. `01-foundations/05-advanced-distributed-theory/03-change-data-capture.md` — CDC, Debezium, outbox pattern

**Reliability and observability:**
1. `04-advanced-topics/02-system-reliability/01-observability.md` — RED/USE, distributed tracing, SLO/error budgets
2. `07-interview-templates/03-pitfalls-and-recovery/02-failure-recovery-playbook.md` — incident response, chaos engineering
3. `07-interview-templates/01-frameworks/04-monitoring-slo-template.md` — SLO design

**Distributed architecture:**
1. `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md` — MVCC, isolation levels
2. `02-building-blocks/02-performance/01-caching-layer.md` — eviction policies, stampede, cache warm-up
3. `04-advanced-topics/01-distributed-architecture/03-microservices.md` — service mesh, circuit breakers, Saga
4. `09-patterns/03-migration-and-pitfalls/01-strangler-fig.md` — monolith decomposition

**Hard HLD problems (`05-hld-problems/03-hard/`):**
Work through all of these. The ones with the highest signal for SDE-3 interviews:
- `distributed-cache.md` — consistent hashing, eviction, replication, hot keys
- `distributed-message-queue.md` — durability, ordering, at-least-once vs exactly-once
- `search-system.md` — inverted index, ranking, distributed index sharding
- `payment-system.md` — idempotency, distributed transactions, reconciliation
- `stock-exchange.md` — order matching, sequence numbers, ultra-low latency
- `ad-click-aggregator.md` — time-series aggregation, at-most-once vs at-least-once trade-offs
- `ride-sharing.md` — geospatial indexing, real-time matching, surge pricing
- `google-maps.md` — graph shortest path, map tile serving, ETA

**LLD problems (`06-lld/05-problems/`):**
Focus on concurrency-heavy problems:
- `25-design-minesweeper.md`
- `26-design-concurrent-lru-cache.md` (or equivalent)
- `24-design-lock-free-queue.md`
- Any parking lot / elevator / rate limiter LLD — practice the OOP decomposition

**Estimated time:** 60–100 hours

---

## Interview Prep Sequence (Last 2 Weeks Before Interview)

Regardless of track, this is the sequence for final prep:

**Week -2:**
- Re-read all advanced distributed theory pages (consistency, Kafka, Raft)
- Work through 3–4 Hard HLD problems from scratch (whiteboard, then compare)
- Review all senior Q&As at the bottom of each building block file

**Week -1:**
- Mock interviews focused on the components you're weakest on
- Read `04-advanced-topics/02-system-reliability/` — observability and SLOs signal seniority
- Practice estimation: storage, bandwidth, QPS — know orders of magnitude cold

**Reference cards to memorize:**
- CAP theorem: when to pick consistency vs availability
- Database selection: when to use SQL vs NoSQL vs time-series vs graph
- Numbers every engineer should know: disk read ~1ms, SSD read ~0.1ms, RAM ~100ns, network round trip ~1ms (same DC), ~100ms (cross-continent)

---

## Quick Reference: Which File For What

| Topic | File |
|---|---|
| Consistent hashing | `02-building-blocks/01-networking/01-load-balancers.md` |
| Message delivery guarantees | `02-building-blocks/04-coordination/01-message-brokers.md` |
| Database sharding strategies | `03-scaling/03-database-scaling.md` |
| SLO / error budgets | `04-advanced-topics/02-system-reliability/01-observability.md` |
| JWT / OAuth 2.0 | `01-foundations/04-security/02-oauth-jwt.md` |
| mTLS / zero-trust / secrets | `01-foundations/04-security/01-security.md` |
| CRDT / linearizability / vector clocks | `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md` |
| Kafka acks / ISR / exactly-once | `04-advanced-topics/03-internals/03-kafka-internals.md` |
| Service mesh / circuit breakers / Saga | `04-advanced-topics/01-distributed-architecture/03-microservices.md` |
| API gateway / BFF / rate limiting | `02-building-blocks/01-networking/03-api-gateway.md` |
