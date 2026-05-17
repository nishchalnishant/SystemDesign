# System Design — SDE-3 Interview Preparation

Consolidated system design guide for senior/staff engineer interviews. No fluff, no duplicates. Covers HLD, LLD, distributed systems, and interview execution.

---

## What's in This Repo

| Category | Count | Location |
|----------|-------|----------|
| HLD Problems (Easy) | 9 | `05-hld-problems/01-easy/` |
| HLD Problems (Medium) | 6 | `05-hld-problems/02-medium/` |
| HLD Problems (Hard) | 12 | `05-hld-problems/03-hard/` |
| LLD Problems | 23 | `06-lld/05-problems/` |
| Design Patterns | 16 | `06-lld/03-design-patterns/` |
| System Design Patterns | 5 | `09-patterns/` |
| Interview Templates | 4 | `07-interview-templates/` |
| Building Blocks | 13 | `02-building-blocks/` |
| Advanced Topics | 6+ | `04-advanced-topics/` |
| Reference / Cheat Sheets | 5 | `08-reference/` |

---

## Repository Structure

```
SystemDesign/
├── SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md  # Interview execution: clarify → estimate → HLD → trade-offs → deep dive
│
├── 01-foundations/
│   ├── fundamentals.md                   # Networking, protocols, caching, availability, scalability
│   ├── databases.md                      # SQL vs NoSQL, ACID, CAP theorem, replication, sharding
│   ├── caching-cdn.md
│   ├── networking.md
│   └── security.md
│
├── 02-building-blocks/                      # Deep dives on individual components
│   ├── load-balancers.md
│   ├── reverse-proxy.md
│   ├── cdn.md
│   ├── caching-layer.md
│   ├── message-brokers.md
│   ├── service-discovery.md
│   ├── api-gateway.md
│   ├── distributed-locks.md
│   ├── rate-limiting.md
│   ├── sharding.md
│   ├── replication.md
│   ├── bloom-filter.md
│   └── architecture-composition.md
│
├── 03-scaling/
│   └── scaling-strategies.md            # Horizontal/vertical, DB scaling, queues, async
│
├── 04-advanced-topics/
│   ├── distributed-systems.md           # Consistency models, Lamport/Vector clocks, CRDTs
│   ├── distributed-concepts.md          # Idempotency, retry, backpressure
│   ├── microservices.md                 # Microservices patterns, trade-offs, Kubernetes
│   ├── event-driven-architecture.md     # EDA, Kafka, event sourcing, CQRS, outbox pattern
│   ├── observability.md                 # Metrics, distributed tracing, SLI/SLO/SLA
│   └── chaos-engineering.md
│
├── 05-hld-problems/
│   ├── 01-easy/                         # URL Shortener, Pastebin, Rate Limiter, Key-Value Store, Web Crawler, Autocomplete, Unique ID Generator, Booking System, Leaderboard
│   ├── 02-medium/                       # Twitter, Instagram, YouTube, WhatsApp, Notification Service, E-Commerce Platform
│   └── 03-hard/                         # Chat System, Distributed Cache, Kafka, Payment, Ride-Sharing, Google Drive, Search, Ad Click Aggregator, Google Maps, LLM Chat, RAG System, Stock Exchange
│
├── 06-lld/
│   ├── 01-oop-fundamentals/
│   ├── 02-solid-principles/
│   ├── 03-design-patterns/              # 01-creational, 02-structural, 03-behavioral (16 patterns)
│   ├── 04-concurrency/
│   └── 05-problems/                     # 23 LLD problems (Parking Lot → Version Control)
│
├── 07-interview-templates/
│   ├── hld-template.md                  # 45-60 min HLD interview guide (7 phases)
│   ├── lld-template.md
│   ├── capacity-estimation.md           # QPS, storage, bandwidth formulas
│   └── trade-offs-cheat-sheet.md        # SQL vs NoSQL, sync vs async, etc.
│
└── 08-reference/
    ├── numbers-to-know.md               # Latency, throughput, cost estimates
    ├── ml-system-design.md              # Feature stores, training pipelines, model monitoring
    └── book-summaries/                  # DDIA, Head First Java, Head First OOA&D
```

---

## Recommended Study Order

This repo has two parallel tracks — HLD and LLD — that build on each other. Follow the order within each track; the tracks can overlap in time.

---

### HLD Track

**Phase 1 — Mental models (read once, then reference constantly)**
1. `SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md` — the 7-phase interview structure; know this cold
2. `01-foundations/fundamentals.md` — scalability, availability, consistency: the four axes every system is measured on
3. `08-reference/numbers-to-know.md` — memorize these; rattling off latency numbers builds credibility instantly
4. `07-interview-templates/capacity-estimation.md` — capacity math becomes muscle memory with 3-4 practice runs

**Phase 2 — Foundations (each builds on the previous)**
5. `01-foundations/networking.md` — TCP, HTTP, DNS; every system lives on a network
6. `01-foundations/databases.md` — SQL vs. NoSQL, ACID, CAP; every system stores data
7. `01-foundations/caching-cdn.md` — when to cache, when not to, what breaks when you do
8. `01-foundations/security.md` — auth, encryption, OWASP top 10

**Phase 3 — Building blocks (the Lego pieces you assemble into every system)**
9. `02-building-blocks/load-balancers.md`
10. `02-building-blocks/caching-layer.md` — Redis internals, eviction, cache-aside vs. write-through
11. `02-building-blocks/message-brokers.md` — Kafka, SQS, pub-sub; required for any async problem
12. `02-building-blocks/sharding.md` + `02-building-blocks/replication.md` — read these together
13. `02-building-blocks/rate-limiting.md` — token bucket, sliding window
14. `02-building-blocks/api-gateway.md` + `02-building-blocks/service-discovery.md`
15. `03-scaling/scaling-strategies.md` — ties everything together

**Phase 4 — HLD Problems (easy → medium → hard; timed at 45 min each)**

| Order | Problem | Why do it here |
|-------|---------|----------------|
| 1 | `05-hld-problems/01-easy/url-shortener.md` | Introduces hashing, caching, DB sharding in isolation |
| 2 | `05-hld-problems/01-easy/unique-id-generator.md` | Short but teaches Snowflake — referenced in every other problem |
| 3 | `05-hld-problems/01-easy/rate-limiter.md` | Token bucket + Redis; applies directly to real interviews |
| 4 | `05-hld-problems/01-easy/pastebin.md` | Object storage + CDN + TTL |
| 5 | `05-hld-problems/01-easy/key-value-store.md` | Storage engine internals, replication |
| 6 | `05-hld-problems/01-easy/autocomplete.md` | Trie, prefix caching, ranking |
| 7 | `05-hld-problems/01-easy/web-crawler.md` | Distributed queues, dedup, politeness |
| 8 | `05-hld-problems/02-medium/notification-service.md` | First async/fan-out problem |
| 9 | `05-hld-problems/02-medium/instagram.md` | Photo storage, CDN, feed generation |
| 10 | `05-hld-problems/02-medium/youtube.md` | Video encoding pipeline, chunked upload |
| 11 | `05-hld-problems/02-medium/whatsapp.md` | WebSocket, presence, message ordering |
| 12 | `05-hld-problems/02-medium/twitter-news-feed.md` | Fan-out at scale; push vs. pull |
| 13 | `05-hld-problems/03-hard/distributed-cache.md` | Consistent hashing, eviction, cluster topology |
| 14 | `05-hld-problems/03-hard/chat-system.md` | Real-time messaging, storage, offline delivery |
| 15 | `05-hld-problems/03-hard/search-system.md` | Inverted index, ranking, crawl pipeline |
| 16 | `05-hld-problems/03-hard/payment-system.md` | Idempotency, exactly-once, ledger design |
| 17 | `05-hld-problems/03-hard/ride-sharing.md` | Geo-indexing, matching, real-time dispatch |
| 18 | `05-hld-problems/03-hard/google-drive.md` | Chunking, dedup, sync protocol |
| 19 | `05-hld-problems/03-hard/distributed-message-queue.md` | Kafka internals applied |
| 20 | `05-hld-problems/03-hard/ad-click-aggregator.md` | High-volume ingestion, Lambda vs Kappa, dedup |
| 21 | `05-hld-problems/03-hard/google-maps.md` | Geo-indexing, graph routing, ETA at scale |
| 22 | `05-hld-problems/03-hard/stock-exchange.md` | Matching engine, order book, low-latency |
| 23 | `05-hld-problems/03-hard/llm-chat-system.md` | Streaming inference, context window, cost control |
| 24 | `05-hld-problems/03-hard/rag-system.md` | Vector embeddings, semantic search, retrieval grounding |

**Phase 5 — Advanced (SDE-3 / Staff level differentiators)**
20. `04-advanced-topics/distributed-systems.md` — consistency models, linearizability, Raft/Paxos
21. `04-advanced-topics/distributed-concepts.md` — idempotency, retry strategies, backpressure
22. `04-advanced-topics/event-driven-architecture.md` — CQRS, event sourcing, outbox pattern
23. `04-advanced-topics/microservices.md` — service mesh, sagas, operational complexity
24. `04-advanced-topics/observability.md` — SLI/SLO/SLA, distributed tracing, on-call readiness
25. `04-advanced-topics/chaos-engineering.md` — game days, failure injection

**Internals (study alongside the problem that uses the technology)**
- `04-advanced-topics/internals/kafka-internals.md` — alongside distributed-message-queue.md
- `04-advanced-topics/internals/redis-internals.md` — alongside distributed-cache.md
- `04-advanced-topics/internals/cassandra-internals.md` — alongside any write-heavy problem
- `04-advanced-topics/internals/postgresql-internals.md` — alongside any RDBMS-heavy problem

---

### LLD Track

The LLD track has a strict dependency order. Each layer depends on the previous one.

**Layer 1 — OOP fundamentals (2–3 days)**
- `06-lld/01-oop-fundamentals/four-pillars.md` — encapsulation, inheritance, polymorphism, abstraction; start here
- `06-lld/01-oop-fundamentals/introduction.md` — what OOP is and why it exists
- `06-lld/01-oop-fundamentals/principles.md` — IS-A vs. HAS-A, composition vs. inheritance

**Layer 2 — SOLID principles (1 week)**
Read these in order — each principle solves a problem introduced by ignoring the previous one.
- Single Responsibility → Open/Closed → Liskov Substitution → Interface Segregation → Dependency Inversion

**Layer 3 — Design patterns (1–2 weeks)**
Don't read all 16 at once. Group by what you're about to build:
- Creational: Singleton (before Parking Lot), Factory (before any multi-type system), Builder (before complex object construction)
- Behavioral: Observer (before any event-driven problem), Strategy (before any algorithm-swap problem), State (before Vending Machine, Elevator)
- Structural: Decorator (before Logger, Rate Limiter), Composite (before file systems, coupon chains)

**Layer 4 — LLD Problems (Tier 1 → Tier 2 → Tier 3)**

| Tier | Problems | Why this order |
|------|----------|----------------|
| Tier 1 (must solve 3+ times) | Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise | Cover Singleton, Strategy, State, Factory |
| Tier 2 (understand class diagram + key pattern) | Elevator, Snake & Ladder, Hotel Mgmt, LRU Cache, Comment System | Cover scheduling, game loops, date concurrency, doubly-linked list |
| Tier 3 (read for specific algorithm) | Locker Service, S3, Search Engine, Version Control, Text Editor | Geo-hash, Composite, Trie, DAG, Gap Buffer |

**Concurrency (study before Tier 2)**
- `06-lld/04-concurrency/producer-consumer.md`
- `06-lld/04-concurrency/thread-safe-singleton.md`

---

### Interview templates (use from Day 1, not Day N)

- `07-interview-templates/hld-template.md` — your 45-60 min interview script; use it on every problem you practice
- `07-interview-templates/lld-template.md` — requirements → use cases → class diagram → patterns
- `07-interview-templates/trade-offs-cheat-sheet.md` — decision matrices for every fork: SQL vs. NoSQL, sync vs. async, push vs. pull

---

## Week-by-Week Schedule

### SDE-3 Interview Prep (8 weeks)

Run HLD and LLD tracks in parallel. HLD requires more time; LLD can be done in shorter focused sessions.

**Week 1 — Mental models + interview framework**
- `SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md`
- `07-interview-templates/hld-template.md` + `07-interview-templates/capacity-estimation.md`
- `01-foundations/fundamentals.md` + `08-reference/numbers-to-know.md`
- LLD: `06-lld/01-oop-fundamentals/four-pillars.md` + `principles.md`

**Week 2 — Foundations deep**
- `01-foundations/databases.md` + `01-foundations/networking.md`
- `01-foundations/caching-cdn.md` + `01-foundations/security.md`
- All of `02-building-blocks/` (read as a block — they reference each other)
- LLD: All 5 SOLID principles in order

**Week 3 — Easy HLD (all 7, timed at 45 min each)**
- URL Shortener → Unique ID Generator → Rate Limiter → Pastebin → Key-Value Store → Autocomplete → Web Crawler
- LLD: Singleton, Factory, Strategy patterns + solve Parking Lot 3 times from scratch

**Week 4 — Medium HLD**
- Notification Service → Instagram → YouTube → WhatsApp → Twitter News Feed
- LLD: State, Observer patterns + solve Vending Machine + Tic-Tac-Toe

**Week 5 — Hard HLD (Part 1)**
- Distributed Cache + Chat System + Search System
- `04-advanced-topics/distributed-systems.md`
- LLD: Decorator, Composite, Chain of Responsibility + solve Rate Limiter LLD + LRU Cache

**Week 6 — Hard HLD (Part 2) + Distributed Systems**
- Payment System + Ride Sharing + Google Drive + Distributed Message Queue
- `04-advanced-topics/distributed-concepts.md` (idempotency, retry — critical for Payment System)
- `04-advanced-topics/event-driven-architecture.md`
- LLD: solve Splitwise + Elevator System

**Week 7 — Advanced Topics + Internals**
- `04-advanced-topics/microservices.md` + `04-advanced-topics/observability.md`
- `04-advanced-topics/internals/kafka-internals.md` + `04-advanced-topics/internals/redis-internals.md`
- LLD: solve 3-4 Tier 2 problems (Hotel, Comment System, Locker Service)

**Week 8 — Advanced hard problems + mock interviews**
- `05-hld-problems/03-hard/ad-click-aggregator.md` + `05-hld-problems/03-hard/stock-exchange.md` — data pipeline and low-latency systems
- `05-hld-problems/03-hard/google-maps.md` — geo-indexing at scale
- `05-hld-problems/03-hard/llm-chat-system.md` + `05-hld-problems/03-hard/rag-system.md` — AI system design (increasingly common at SDE-3)
- `04-advanced-topics/chaos-engineering.md`
- LLD: attempt 1-2 Tier 3 problems (S3, Version Control)
- Final review: `07-interview-templates/trade-offs-cheat-sheet.md`
- Re-solve 4 HLD problems end-to-end without notes (pick weakest ones)

---

## Interview Execution Checklist

### Before the interview
- [ ] Know the 7 HLD phases from `07-interview-templates/hld-template.md`
- [ ] Memorize key numbers: 1M req/day ≈ 12 QPS, L1 cache 0.5ns, RAM 100ns, SSD 150μs
- [ ] Know 99.9% = 8.76 hrs downtime/year, 99.99% = 52 min/year

### During the interview

**Phase 1 (0-10 min): Requirements**
- [ ] Clarify functional requirements (top 3-5 features only)
- [ ] Define non-functional requirements: PASS-R (Performance, Availability, Scalability, Security, Reliability)
- [ ] Agree on scale — DAU, QPS, storage order of magnitude

**Phase 2 (10-15 min): Capacity Estimation**
- [ ] Writes QPS, reads QPS (estimate read:write ratio)
- [ ] Storage: bytes per record × total records × replication factor
- [ ] Bandwidth: QPS × avg response size

**Phase 3 (15-25 min): API + Schema + HLD**
- [ ] 2-3 REST endpoints
- [ ] Core DB schema (3-4 tables/collections)
- [ ] High-level component diagram

**Phase 4 (25-55 min): Deep Dives**
- [ ] Scale the bottleneck (DB, service, cache)
- [ ] Caching strategy (what, where, eviction policy)
- [ ] Failure scenarios (what if DB is down, network partition)
- [ ] Trade-offs — explain every choice with "because X, at the cost of Y"

**Phase 5 (55-60 min): Wrap-up**
- [ ] Summarize design in 3 sentences
- [ ] Mention one enhancement you'd add with more time

---

## Key Principles

### 1. Trade-offs over answers

Don't say: "I'd use Redis."
Say: "Redis gives sub-millisecond latency and rich data structures like sorted sets for leaderboards. The trade-off is limited storage — we'd use LRU eviction and keep the DB as source of truth."

### 2. Scale progressively

"At 1K users a single server works. At 1M users we need horizontal scaling + read replicas. At 10M we need sharding."

### 3. Production mindset

Every design decision needs: monitoring ("how do we know it's working?"), failure handling ("what if X goes down?"), and operational complexity ("how hard is this to maintain?").

### 4. Own the conversation

Drive through the phases. Don't wait for the interviewer to ask — ask yourself: "Should I go deeper on the DB choice or the caching layer?" Then pick one and explain why.

---

## Key Files for SDE-3 Interviews

| File | Purpose |
|------|---------|
| `07-interview-templates/hld-template.md` | Master this — it's your interview script |
| `07-interview-templates/capacity-estimation.md` | Practice until estimation is automatic |
| `07-interview-templates/trade-offs-cheat-sheet.md` | Decision matrices for SQL vs NoSQL, sync vs async |
| `08-reference/numbers-to-know.md` | Latency numbers — memorize the orders of magnitude |
| `04-advanced-topics/distributed-systems.md` | Consistency models, consensus — SDE-3 differentiators |
| `04-advanced-topics/microservices.md` | When to use microservices and the operational cost |
| `04-advanced-topics/event-driven-architecture.md` | Kafka, event sourcing, CQRS — appears in many hard problems |

---

## Resources

- "Designing Data-Intensive Applications" — Martin Kleppmann (read chapters 1, 5, 7, 9)
- "System Design Interview" Vol 1 & 2 — Alex Xu
- [High Scalability Blog](http://highscalability.com/)
- [AWS Architecture Center](https://aws.amazon.com/architecture/)
