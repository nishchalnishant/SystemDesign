> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Master index for a comprehensive system design interview preparation repo targeting SDE-2/SDE-3 roles.
>
> **Key topics:**
> - Repo structure overview: 9 directories covering HLD, LLD, patterns, templates, and reference material
> - Content inventory: 36 HLD problems (9 easy, 7 medium, 20 hard), 36 LLD problems, 21 design patterns, 16 building blocks
> - Recommended 4–8 week study plan with weekly milestones
> - Quick-reference table linking each concept to its file location
> - Study order: Foundations → Building Blocks → Scaling → Advanced Topics → HLD Problems → LLD Problems → Interview Templates
>
> **Key takeaway:** This is the navigation hub — use it to find any topic and follow the structured study order to become interview-ready.

---

# System Design — Interview Preparation (SDE-2 / SDE-3)

Consolidated system design guide for senior/staff engineer interviews. No fluff, no duplicates. Covers HLD, LLD, distributed systems, and interview execution.

---

## What's in This Repo

| Category | Count | Location |
|----------|-------|----------|
| HLD Problems (Easy) | 9 | `05-hld-problems/01-easy/` |
| HLD Problems (Medium) | 7 | `05-hld-problems/02-medium/` |
| HLD Problems (Hard) | 20 | `05-hld-problems/03-hard/` |
| LLD Problems | 36 | `06-lld/05-problems/` |
| Design Patterns | 21 | `06-lld/03-design-patterns/` |
| System Design Patterns | 7 | `09-patterns/` |
| Interview Templates | 17 | `07-interview-templates/` |
| Building Blocks | 16 | `02-building-blocks/` |
| Advanced Topics | 7 + internals | `04-advanced-topics/` |
| Reference / Cheat Sheets | 4 + book-summaries | `08-reference/` |

---

## Repository Structure

```
SystemDesign/
├── README.md                             # This file — master index
├── START-HERE.md                         # Quick-start: fastest path to interview-ready
├── LEARNING-PATH.md                      # Structured week-by-week path
├── FLOWCHARTS.md · MINDMAP.md · SUMMARY.md
├── SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md  # Interview execution: clarify → estimate → HLD → trade-offs → deep dive
│
├── 01-foundations/
│   ├── 01-system-design-basics/          # 01-fundamentals.md — scalability, availability, consistency, CAP
│   ├── 02-hardware-and-networking/       # 01-storage-fundamentals.md, 02-networking.md
│   ├── 03-database-foundations/          # 01-databases.md — SQL vs NoSQL, ACID, indexing
│   ├── 04-security/                      # 01-security.md, 02-oauth-jwt.md
│   └── 05-advanced-distributed-theory/   # consistency-and-conflicts, consensus-algorithms, change-data-capture
│
├── 02-building-blocks/                   # 16 component deep dives
│   ├── 01-networking/                    # load-balancers, reverse-proxy, api-gateway, service-discovery, cdn, websockets-sse
│   ├── 02-performance/                   # caching-layer, rate-limiting, circuit-breaker, bloom-filter
│   ├── 03-data-partitioning/             # sharding, replication, consistent-hashing
│   ├── 04-coordination/                  # message-brokers, distributed-locks
│   └── 05-composition/                   # architecture-composition
│
├── 03-scaling/
│   ├── 01-scaling-fundamentals.md        # Horizontal/vertical, queues, async
│   ├── 02-database-internals.md          # B-tree, LSM, WAL, MVCC, isolation levels
│   ├── 03-database-scaling.md            # Read replicas, shard keys, resharding, hot partitions
│   └── 04-global-distribution.md         # Multi-region, active-active, CRDTs, data residency
│
├── 04-advanced-topics/
│   ├── 01-distributed-architecture/      # distributed-systems, distributed-concepts, microservices,
│   │                                     #   event-driven-architecture, stream-processing, kubernetes,
│   │                                     #   outbox-cdc-pattern, grpc-rest-graphql
│   ├── 02-system-reliability/            # observability, chaos-engineering, telemetry-tracing
│   └── 03-internals/                     # index-structures, consensus-protocols, kafka, redis, cassandra,
│                                         #   postgresql, mysql, dynamodb, elasticsearch, zookeeper,
│                                         #   raft-paxos-conceptual, stream-vs-batch
│
├── 05-hld-problems/                      # 36 problems
│   ├── 01-easy/                          # 9: URL Shortener, Pastebin, Rate Limiter, Key-Value Store, Web Crawler,
│   │                                     #    Autocomplete, Unique ID Generator, Booking System, Leaderboard
│   ├── 02-medium/                        # 7: Twitter, Instagram, YouTube, WhatsApp, Notification Service,
│   │                                     #    E-Commerce, Typeahead Search
│   └── 03-hard/                          # 20: Chat, Distributed Cache, Message Queue, Payment, Ride-Sharing,
│                                         #    Google Drive, Search, Ad Click Aggregator, Google Maps, LLM Chat,
│                                         #    RAG, Stock Exchange, CDN, Job Scheduler, Dropbox, GitHub,
│                                         #    Hotel Booking, Metrics Monitoring, Gaming Leaderboard, Ticketmaster
│
├── 06-lld/
│   ├── 01-oop-fundamentals/              # 5 files
│   ├── 02-solid-principles/              # 5 files (01-single-responsibility → 05-dependency-inversion)
│   ├── 03-design-patterns/               # 21 patterns: 01-creational (5), 02-structural (7), 03-behavioral (9)
│   ├── 04-concurrency/                   # 4 files
│   ├── 05-problems/                      # 36 LLD problems
│   │   ├── 01-core-problems/             # 5 (Tier 1): parking-lot, rate-limiter, tic-tac-toe, vending-machine, splitwise
│   │   ├── 02-frequent-problems/         # 12 (Tier 2): bookmyshow, chess, elevator, LRU cache, ATM, ...
│   │   ├── 03-domain-specific/           # 7: mentorship, logger, library, order-mgmt, ride-sharing, pub-sub, inventory
│   │   └── 04-advanced-niche/            # 12: minesweeper, S3, search engine, version control, lock-free queue, ...
│   ├── uml-diagrams.md
│   └── glossary.md                       # LLD/OOP/SOLID/GoF-pattern/UML term reference
│
├── 07-interview-templates/               # 15 files
│   ├── 01-frameworks/                    # hld-template, lld-template, api-design-template, monitoring-slo-template
│   ├── 02-cheat-sheets/                  # trade-offs, capacity-estimation, database-selection-tree,
│   │                                     #   architecture-by-scale, concept-to-problem-map
│   ├── 03-pitfalls-and-recovery/         # interview-anti-patterns, failure-recovery-playbook
│   └── 04-practice-and-prep/             # prep-toolkit, question-bank, mock-interview-problems, worked-examples
│
├── 08-reference/
│   ├── numbers-to-know.md                # Latency, throughput, cost estimates
│   ├── ml-system-design.md               # Feature stores, training pipelines, model monitoring
│   ├── cloud-services-cheat-sheet.md
│   ├── system-design-glossary.md         # HLD/distributed-systems term reference
│   └── book-summaries/                   # ddia.md, Head First Java, Head First OOA&D
│
├── 09-patterns/                          # 7 cross-cutting patterns
│   ├── 01-data-consistency/              # outbox, two-phase-commit, saga
│   ├── 02-architecture-and-scaling/      # cqrs-event-sourcing, bulkhead
│   └── 03-migration-and-pitfalls/        # strangler-fig, anti-patterns
│
└── youtube tutorial/                     # Companion video notes
    ├── 01-concepts/                      # Atomized deep-dive notes: api-design, caching,
    │                                     #   load-balancing, rate-limiting, scaling
    └── 1..7/                             # Per-playlist walkthrough notes
```

---

## Recommended Study Order

This repo has two parallel tracks — HLD and LLD — that build on each other. Follow the order within each track; the tracks can overlap in time.

---

### HLD Track

**Phase 0 — Methodology (start here if you know the material but can't originate a design from a fresh prompt)**
- `05-hld-problems/00-methodology/README.md` — the 5-step derivation process (requirements & scope → capacity estimation → API & data model → deriving the architecture → identifying the bottleneck), a full worked example, a 45-60 min interview playbook, and timed practice drills
- `08-reference/system-design-glossary.md` — HLD/distributed-systems term reference; use it to quickly look up any term you recognize but can't yet define crisply

**Phase 1 — Mental models (read once, then reference constantly)**
1. `SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md` — the 7-phase interview structure; know this cold
2. `01-foundations/01-system-design-basics/01-fundamentals.md` — scalability, availability, consistency: the four axes every system is measured on
3. `08-reference/numbers-to-know.md` — memorize these; rattling off latency numbers builds credibility instantly
4. `07-interview-templates/02-cheat-sheets/02-capacity-estimation.md` — capacity math becomes muscle memory with 3-4 practice runs

**Phase 2 — Foundations (each builds on the previous)**
5. `01-foundations/02-hardware-and-networking/02-networking.md` — TCP, HTTP, DNS; every system lives on a network
6. `01-foundations/03-database-foundations/01-databases.md` — SQL vs. NoSQL, ACID, CAP; every system stores data
   See also: `03-scaling/02-database-internals.md` — B-tree indexes, WAL, MVCC, isolation levels
7. `02-building-blocks/02-performance/01-caching-layer.md` — when to cache, when not to, cache patterns, eviction policies, Redis, CDN layer (consolidated from former caching-cdn.md and cache-eviction.md)
8. `01-foundations/04-security/01-security.md` — auth, encryption, OWASP top 10

**Phase 3 — Building blocks (the Lego pieces you assemble into every system)**
9. `02-building-blocks/01-networking/01-load-balancers.md`
10. `02-building-blocks/02-performance/01-caching-layer.md` — Redis internals, eviction, cache-aside vs. write-through
11. `02-building-blocks/04-coordination/01-message-brokers.md` — Kafka, SQS, pub-sub; required for any async problem
12. `02-building-blocks/03-data-partitioning/01-sharding.md` + `02-building-blocks/03-data-partitioning/02-replication.md` — read these together
13. `02-building-blocks/02-performance/02-rate-limiting.md` — token bucket, sliding window
14. `02-building-blocks/01-networking/03-api-gateway.md` + `02-building-blocks/01-networking/04-service-discovery.md`
15. `03-scaling/01-scaling-fundamentals.md` — ties everything together

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

**Phase 5 — Advanced (SDE-2/3 differentiators)**
20. `04-advanced-topics/01-distributed-architecture/01-distributed-systems.md` — consistency models, linearizability, Raft/Paxos
21. `04-advanced-topics/01-distributed-architecture/02-distributed-concepts.md` — idempotency, retry strategies, backpressure
22. `04-advanced-topics/01-distributed-architecture/04-event-driven-architecture.md` — CQRS, event sourcing, outbox pattern
23. `04-advanced-topics/01-distributed-architecture/03-microservices.md` — service mesh, sagas, operational complexity
24. `04-advanced-topics/02-system-reliability/01-observability.md` — SLI/SLO/SLA, distributed tracing, on-call readiness
25. `04-advanced-topics/02-system-reliability/02-chaos-engineering.md` — game days, failure injection

**Internals (study alongside the problem that uses the technology)**
- `04-advanced-topics/03-internals/03-kafka-internals.md` — alongside distributed-message-queue.md
- `04-advanced-topics/03-internals/04-redis-internals.md` — alongside distributed-cache.md
- `04-advanced-topics/03-internals/05-cassandra-internals.md` — alongside any write-heavy problem
- `04-advanced-topics/03-internals/06-postgresql-internals.md` — alongside any RDBMS-heavy problem

---

### LLD Track

The LLD track has a strict dependency order. Each layer depends on the previous one.

**Layer 0 — Methodology (start here if you know the material but can't originate a design from a fresh prompt)**
- `06-lld/00-methodology/README.md` — the 5-step derivation process (decompose → nouns to classes → relationships/UML → verbs to methods/interfaces → spot the pattern), a full worked example, a 45-min interview playbook, and timed practice drills
- `06-lld/glossary.md` — OOP/SOLID/GoF-pattern/UML/concurrency term reference; use it to quickly look up any term you recognize but can't yet define crisply

**Layer 1 — OOP fundamentals (2–3 days)**
- `06-lld/01-oop-fundamentals/four-pillars.md` — encapsulation, inheritance, polymorphism, abstraction; start here
- `06-lld/01-oop-fundamentals/introduction.md` — what OOP is and why it exists
- `06-lld/01-oop-fundamentals/principles.md` — IS-A vs. HAS-A, composition vs. inheritance

**Layer 2 — SOLID principles (1 week)**
Read these in order — each principle solves a problem introduced by ignoring the previous one.
- Single Responsibility → Open/Closed → Liskov Substitution → Interface Segregation → Dependency Inversion

**Layer 3 — Design patterns (1–2 weeks)**
Don't read all 21 at once. Group by what you're about to build:
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

- `07-interview-templates/01-frameworks/01-hld-template.md` — your 45-60 min interview script; use it on every problem you practice
- `07-interview-templates/01-frameworks/02-lld-template.md` — requirements → use cases → class diagram → patterns
- `07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md` — decision matrices for every fork: SQL vs. NoSQL, sync vs. async, push vs. pull

---

## Week-by-Week Schedule

### Interview Prep (8 weeks)

Run HLD and LLD tracks in parallel. HLD requires more time; LLD can be done in shorter focused sessions.

**Week 1 — Mental models + interview framework**
- `SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md`
- `07-interview-templates/01-frameworks/01-hld-template.md` + `07-interview-templates/02-cheat-sheets/02-capacity-estimation.md`
- `01-foundations/01-system-design-basics/01-fundamentals.md` + `08-reference/numbers-to-know.md`
- LLD: `06-lld/01-oop-fundamentals/four-pillars.md` + `principles.md`

**Week 2 — Foundations deep**
- `01-foundations/03-database-foundations/01-databases.md` + `01-foundations/02-hardware-and-networking/02-networking.md`
- `02-building-blocks/02-performance/01-caching-layer.md` + `01-foundations/04-security/01-security.md`
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
- `04-advanced-topics/01-distributed-architecture/01-distributed-systems.md`
- LLD: Decorator, Composite, Chain of Responsibility + solve Rate Limiter LLD + LRU Cache

**Week 6 — Hard HLD (Part 2) + Distributed Systems**
- Payment System + Ride Sharing + Google Drive + Distributed Message Queue
- `04-advanced-topics/01-distributed-architecture/02-distributed-concepts.md` (idempotency, retry — critical for Payment System)
- `04-advanced-topics/01-distributed-architecture/04-event-driven-architecture.md`
- LLD: solve Splitwise + Elevator System

**Week 7 — Advanced Topics + Internals**
- `04-advanced-topics/01-distributed-architecture/03-microservices.md` + `04-advanced-topics/02-system-reliability/01-observability.md`
- `04-advanced-topics/03-internals/03-kafka-internals.md` + `04-advanced-topics/03-internals/04-redis-internals.md`
- LLD: solve 3-4 Tier 2 problems (Hotel, Comment System, Locker Service)

**Week 8 — Advanced hard problems + mock interviews**
- `05-hld-problems/03-hard/ad-click-aggregator.md` + `05-hld-problems/03-hard/stock-exchange.md` — data pipeline and low-latency systems
- `05-hld-problems/03-hard/google-maps.md` — geo-indexing at scale
- `05-hld-problems/03-hard/llm-chat-system.md` + `05-hld-problems/03-hard/rag-system.md` — AI system design (increasingly common at SDE-3)
- `04-advanced-topics/02-system-reliability/02-chaos-engineering.md`
- LLD: attempt 1-2 Tier 3 problems (S3, Version Control)
- Final review: `07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md`
- Re-solve 4 HLD problems end-to-end without notes (pick weakest ones)

---

## Interview Execution Checklist

### Before the interview
- [ ] Know the 7 HLD phases from `07-interview-templates/01-frameworks/01-hld-template.md`
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

## Key Files for SDE-2/3 Interviews

| File | Purpose |
|------|---------|
| `07-interview-templates/01-frameworks/01-hld-template.md` | Master this — it's your interview script |
| `07-interview-templates/02-cheat-sheets/02-capacity-estimation.md` | Practice until estimation is automatic |
| `07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md` | Decision matrices for SQL vs NoSQL, sync vs async |
| `08-reference/numbers-to-know.md` | Latency numbers — memorize the orders of magnitude |
| `08-reference/system-design-glossary.md` | HLD term reference — look up any term you recognize but can't define crisply |
| `06-lld/glossary.md` | LLD/OOP/pattern term reference — the LLD-track counterpart |
| `04-advanced-topics/01-distributed-architecture/01-distributed-systems.md` | Consistency models, consensus — senior-level differentiators |
| `04-advanced-topics/01-distributed-architecture/03-microservices.md` | When to use microservices and the operational cost |
| `04-advanced-topics/01-distributed-architecture/04-event-driven-architecture.md` | Kafka, event sourcing, CQRS — appears in many hard problems |

---

## Resources

- "Designing Data-Intensive Applications" — Martin Kleppmann (read chapters 1, 5, 7, 9)
- "System Design Interview" Vol 1 & 2 — Alex Xu
- [High Scalability Blog](http://highscalability.com/)
- [AWS Architecture Center](https://aws.amazon.com/architecture/)
