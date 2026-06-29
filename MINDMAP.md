---
module: root
status: unread
tags: [root, system-design, mindmap]
---
# System Design Repository — Master Mindmap & Study Framework

> Last updated: May 2026 | Use this as your navigation anchor across the entire repo.

---

## REPO ARCHITECTURE

```
SystemDesign/
│
├── 01-foundations/          [6 files — core theory]        ← start here Week 1
├── 02-building-blocks/      [13 files — system components] ← Week 1-2
├── 03-scaling/              [4 files — scale strategies]   ← Week 2
├── 04-advanced-topics/      [7 files + 6 internals]        ← Week 3
├── 05-hld-problems/         [27 problems: 9E + 6M + 12H]   ← Week 4-8
├── 06-lld/                  [4+5+16+3+23 = 51 files]       ← Week 2-6 parallel
├── 07-interview-templates/  [4 files — interview scaffolds]← use from Day 1
├── 08-reference/            [5 files + book summaries]     ← ongoing
└── 09-patterns/             [5 files — distributed patterns]← Week 3
```

**Total files:** ~120 | **Study window:** 8 weeks full-time, 12 weeks part-time

---

## 01 — FOUNDATIONS  [theory base; no code]

```
01-foundations/
│
├── fundamentals.md          [CAP, PACELC, consistency models, latency numbers]
├── networking.md            [TCP/UDP, HTTP/2/3, TLS, DNS, CDN mechanics]
├── databases.md             [SQL vs NoSQL, ACID, B-tree, LSM, indexing]
└── security.md              [AuthN/AuthZ, OAuth2, JWT, mTLS, rate-limit abuse]
```

**Key concepts to nail:** CAP theorem trade-offs · P99 latency budgets · B-tree vs LSM write amplification

---

## 02 — BUILDING BLOCKS  [reusable system components]

```
02-building-blocks/
│
├── load-balancers.md        [L4 vs L7, round-robin, least-conn, consistent hash]
├── reverse-proxy.md         [Nginx, TLS termination, connection pooling]
├── api-gateway.md           [Auth, rate limiting, protocol translation, fan-out]
├── caching-layer.md         [Redis, Memcached, eviction (LRU/LFU), cache stampede]
├── cdn.md                   [Edge PoPs, origin pull vs push, invalidation]
├── message-brokers.md       [Kafka, RabbitMQ, pub-sub, fan-out, dead-letter]
├── sharding.md              [Range, hash, directory sharding; hotspot prevention]
├── replication.md           [Leader-follower, multi-leader, leaderless (quorum)]
├── rate-limiting.md         [Token bucket, sliding window, fixed window, Redis Lua]
├── distributed-locks.md     [Redlock, ZooKeeper, fencing tokens]
├── service-discovery.md     [Consul, Eureka, client-side vs server-side discovery]
├── bloom-filter.md          [Probabilistic, false positives only, web crawlers/cache]
└── architecture-composition.md  [How blocks combine: read-heavy, write-heavy, hybrid]
```

**Cross-reference:** These 13 blocks are the raw material for every HLD problem in `05-hld-problems/`.

---

## 03 — SCALING  [when single-server breaks]

```
03-scaling/
│
├── scaling-strategies.md        [Vertical vs horizontal, stateless design, auto-scaling]
├── database-scaling-deep-dive.md [Read replicas, range/hash/directory sharding, OLAP separation]
└── global-distribution.md        [Multi-region, active-active vs active-passive, CRDTs, data residency]
```

**Decision tree:** Single DB → Read replicas → Sharding → Multi-region → Active-active

---

## 04 — ADVANCED TOPICS  [SDE-3 depth layer]

```
04-advanced-topics/
│
├── distributed-systems.md        [Consensus, Paxos, Raft, leader election, split-brain]
├── distributed-concepts.md       [Idempotency, exactly-once, vector clocks, tombstones]
├── microservices.md              [Service mesh, Sidecar, circuit breaker, bulkhead]
├── event-driven-architecture.md  [Event sourcing, CQRS, choreography vs orchestration]
├── observability.md              [Metrics, logs, traces; RED method, SLI/SLO/SLA]
├── chaos-engineering.md          [GameDay, fault injection, blast radius, steady state]
│
└── internals/                    [Internals of 6 major systems — deep-dive reference]
    ├── cassandra-internals.md    [SSTable, LSM, consistent hashing ring, gossip]
    ├── kafka-internals.md        [Log segments, ISR, controller election, exactly-once]
    ├── redis-internals.md        [Skip list (sorted set), dict rehash, AOF vs RDB]
    ├── postgresql-internals.md   [MVCC, WAL, B-tree, VACUUM, index types]
    ├── elasticsearch-internals.md [Inverted index, shards, relevance scoring (BM25)]
    └── zookeeper-internals.md    [ZAB protocol, znodes, watches, leader election]
```

**Study tip:** Read internals only after the corresponding HLD problem (e.g., kafka-internals after distributed-message-queue.md).

---

## 05 — HLD PROBLEMS  [27 interview problems]

```
05-hld-problems/
│
├── 01-easy/   [9 problems — one concept each, 30-45 min]
│   ├── url-shortener.md          [Hash + Base62 + DB sharding]
│   ├── unique-id-generator.md    [Snowflake — no coordination]
│   ├── rate-limiter.md           [Token bucket + Redis atomic ops]
│   ├── pastebin.md               [Object storage + CDN + TTL]
│   ├── key-value-store.md        [LSM tree + replication]
│   ├── autocomplete.md           [Trie + prefix cache + ranking]
│   ├── web-crawler.md            [Distributed queue + Bloom filter]
│   ├── booking-system.md         [Inventory lock + idempotency]
│   └── leaderboard.md            [Redis sorted sets + windowed ranking]
│
├── 02-medium/ [6 problems — 3-5 building blocks, 60 min]
│   ├── notification-service.md   [Fan-out + async + multi-channel]
│   ├── instagram.md              [Photo storage + CDN + feed gen]
│   ├── youtube.md                [Encoding pipeline + chunked upload + ABR]
│   ├── whatsapp.md               [WebSocket + ordering + offline delivery]
│   ├── twitter-news-feed.md      [Fan-out push vs pull + Redis sorted sets]
│   └── e-commerce-platform.md   [Inventory + cart + checkout + order lifecycle]
│
└── 03-hard/   [12 problems — multi-region/consensus/latency, 75 min]
    ├── distributed-cache.md      [Consistent hashing + eviction + topology]
    ├── chat-system.md            [Real-time + storage + offline delivery]
    ├── search-system.md          [Inverted index + ranking + crawl pipeline]
    ├── payment-system.md         [Idempotency + exactly-once + double-entry ledger]
    ├── ride-sharing.md           [Geo-indexing + real-time matching + dispatch]
    ├── google-drive.md           [Chunking + dedup + sync + conflict resolution]
    ├── distributed-message-queue.md [Partitioning + replication + Kafka internals]
    ├── ad-click-aggregator.md    [High-volume ingestion + real-time vs batch + dedup]
    ├── google-maps.md            [Geo-indexing + routing graph + tile serving + ETA]
    ├── llm-chat-system.md        [Streaming inference + context management + token cost]
    ├── rag-system.md             [Vector embeddings + semantic search + retrieval pipeline]
    └── stock-exchange.md         [Order book + matching engine + low-latency + market data]
```

---

## 06 — LLD  [Low-Level Design + coding patterns]

```
06-lld/
│
├── 01-oop-fundamentals/         [4 files]
│   ├── introduction.md          [What is OOP, why it matters]
│   ├── four-pillars.md          [Encapsulation, Abstraction, Inheritance, Polymorphism]
│   ├── java-oops.md             [Java-specific: access modifiers, interfaces, abstract]
│   └── python-oops.md           [Python: dunder methods, MRO, dataclasses]
│   (+ principles.md)
│
├── 02-solid-principles/         [5 files — one per principle]
│   ├── 1. single-responsibility-principle.md  [One class, one reason to change]
│   ├── 2. open-closed-principle.md            [Open for extension, closed for modification]
│   ├── 3. liskov-substitution-principle.md    [Subtype must be substitutable]
│   ├── 4. interface-segregation-principle.md  [Many specific interfaces > one fat interface]
│   └── 5. dependency-inversion-principle.md   [Depend on abstractions, not concretions]
│
├── 03-design-patterns/          [16 patterns across 3 categories]
│   │
│   ├── 01-creational/           [5 patterns — object creation]
│   │   ├── singleton.md         [Single instance; double-checked locking in Java]
│   │   ├── factory-pattern.md   [Decouple creation from use]
│   │   ├── abstract-factory-pattern.md [Families of related objects]
│   │   ├── builder-pattern.md   [Complex object step-by-step; fluent API]
│   │   └── prototype-pattern.md [Clone to avoid expensive init]
│   │
│   ├── 02-structural/           [7 patterns — composition]
│   │   ├── adapter-pattern.md   [Convert interface to expected interface]
│   │   ├── bridge-pattern.md    [Decouple abstraction from implementation]
│   │   ├── composite-pattern.md [Tree structure; leaf + composite uniform API]
│   │   ├── decorator-pattern.md [Wrap to add behavior without subclassing]
│   │   ├── facade-pattern.md    [Simple interface over complex subsystem]
│   │   ├── flyweight-pattern.md [Share intrinsic state; reduce memory]
│   │   └── proxy-pattern.md     [Placeholder for another object (lazy, access, cache)]
│   │
│   └── 03-behavioral/           [9 patterns — communication]
│       ├── observer-pattern.md  [Subject notifies observers; event systems]
│       ├── strategy-pattern.md  [Swap algorithms at runtime]
│       ├── command-pattern.md   [Encapsulate request; undo/redo support]
│       ├── state-pattern.md     [Object changes behavior as state changes]
│       ├── template-method-pattern.md [Skeleton in base class; steps in subclass]
│       ├── chain-of-responsibility.md [Pass request along handler chain]
│       ├── iterator-pattern.md  [Sequential access without exposing internals]
│       ├── mediator-pattern.md  [Centralize object communication; reduce coupling]
│       └── visitor-pattern.md   [Add operations without changing classes]
│
├── 04-concurrency/              [4 files — Java thread patterns]
│   ├── producer-consumer.md       [BlockingQueue, wait/notify, bounded buffer]
│   ├── thread-safe-singleton.md   [Double-checked locking, volatile, enum singleton]
│   ├── concurrency-patterns.md    [RWLock, Semaphore, Latch, Barrier, ThreadPool, Atomic, volatile]
│   └── futures-async-patterns.md  [Future, CompletableFuture chains, allOf/anyOf, timeout, promise bridge]
│
└── 05-problems/                 [23 LLD problems — Tier 1 to Tier 3]
    ├── Tier 1 (classic):        parking-lot, rate-limiter, tic-tac-toe, vending-machine,
    │                            splitwise, snake-and-ladder, elevator-system, comment-system
    ├── Tier 2 (complex):        hotel-management, lru-cache, locker-service, coupon-system,
    │                            mentorship-platform, logger-library, minesweeper, s3-object-storage
    └── Tier 3 (hard):           search-engine, tetris, version-control, tunneling-service,
                                 text-editor, download-manager, unlock-pattern
```

---

## SUPPORTING MATERIALS  [high-value; use alongside study phases]

```
Root/
│
├── SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md  [7-phase execution guide for live interviews]
│                                          clarify → estimate → HLD → bottlenecks → trade-offs → deep dive → scaling
├── MINDMAP.md                             [this file — repo navigation anchor]
├── FLOWCHARTS.md                          [per-topic recall flowcharts for every section]
└── SUMMARY.md                             [GitBook table of contents]
```

**When to use:**
- `SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md` — read once in Phase 1, re-read before every mock
- `FLOWCHARTS.md` — glance before studying a topic; redraw from memory after

---

## 06b — LLD SUPPORTING REFERENCE

```
06-lld/
│
└── uml-diagrams.md    [UML class diagram notation: visibility, relationships, multiplicity]
                        use when drawing entity models in LLD interviews
```

---

## 07 — INTERVIEW TEMPLATES  [use from Day 1]

```
07-interview-templates/
│
├── hld-template.md          [6-step framework: clarify → estimate → API → data → design → deep-dive]
├── lld-template.md          [5-step: requirements → entities → design → code → edge cases]
├── capacity-estimation.md   [QPS, storage, bandwidth math with worked examples]
└── trade-offs-cheat-sheet.md [SQL vs NoSQL, push vs pull, sync vs async, CP vs AP]
```

**Rule:** Open hld-template.md before every HLD problem session. Internalize the 6 steps.

---

## 08 — REFERENCE  [quick lookup during prep]

```
08-reference/
│
├── numbers-to-know.md           [Latency numbers, storage units, QPS benchmarks]
├── system-design-glossary.md    [250+ terms defined]
├── cloud-services-cheat-sheet.md [AWS/GCP/Azure service equivalents]
├── ml-system-design.md          [Feature store, model serving, data pipeline patterns]
│
└── book-summaries/
    ├── ddia.md                  [Designing Data-Intensive Applications — key chapters]
    └── head-first-*/            [Head First Java, OO&A summaries]
```

---

## 09 — PATTERNS  [distributed system design patterns]

```
09-patterns/
│
├── outbox-pattern.md            [Transactional outbox for reliable event publishing]
├── saga-pattern.md              [Distributed transactions: choreography vs orchestration]
├── cqrs-event-sourcing.md       [Separate read/write models; store events not state]
├── strangler-fig.md             [Migrate monolith to microservices incrementally]
└── anti-patterns.md             [What NOT to do: distributed monolith, big ball of mud]
```

---

## STUDY SEQUENCE

```
STUDY SEQUENCE
│
├── PHASE 1 — THEORY BASE  [Week 1]
│   ├── 01-foundations/ (all 5 files)
│   ├── 07-interview-templates/hld-template.md
│   └── 07-interview-templates/capacity-estimation.md
│
├── PHASE 2 — BUILDING BLOCKS  [Week 2]
│   ├── 02-building-blocks/ (all 13 files — read, don't memorize)
│   ├── 03-scaling/scaling-strategies.md
│   └── 07-interview-templates/trade-offs-cheat-sheet.md
│
├── PHASE 3 — LLD TRACK  [Week 2-4, parallel with HLD]
│   ├── 06-lld/01-oop-fundamentals/ (all 4)
│   ├── 06-lld/02-solid-principles/ (all 5)
│   ├── 06-lld/03-design-patterns/ (creational → structural → behavioral)
│   ├── 06-lld/04-concurrency/ (all 3 files)
│   └── 06-lld/05-problems/ (Tier 1 first, then Tier 2, then Tier 3)
│
├── PHASE 4 — HLD EASY  [Week 3-4]
│   ├── Problems 1-5: url-shortener, unique-id, rate-limiter, pastebin, key-value
│   └── Problems 6-9: autocomplete, web-crawler, booking-system, leaderboard
│
├── PHASE 5 — HLD MEDIUM  [Week 5]
│   └── Problems 10-15: notification, instagram, youtube, whatsapp, twitter, e-commerce
│
├── PHASE 6 — ADVANCED TOPICS  [Week 5-6]
│   ├── 04-advanced-topics/distributed-systems.md
│   ├── 04-advanced-topics/distributed-concepts.md
│   ├── 04-advanced-topics/microservices.md
│   ├── 09-patterns/ (all 5 files)
│   └── 03-scaling/database-scaling-deep-dive.md + global-distribution.md
│
└── PHASE 7 — HLD HARD  [Week 6-8]
    ├── Problems 16-20: distributed-cache, chat, search, payment, ride-sharing
    ├── Problems 21-24: google-drive, dist-message-queue, ad-click, google-maps
    ├── Problems 25-27: llm-chat, rag-system, stock-exchange
    └── 04-advanced-topics/internals/ (match each to the relevant problem)
```

---

## BUILDING BLOCK × HLD PROBLEM MATRIX

| Building Block | Easy | Medium | Hard |
|----------------|------|--------|------|
| Consistent Hashing | URL Shortener | — | Distributed Cache, Chat, Dist. Queue |
| Redis Sorted Sets | Leaderboard | Twitter, Instagram | — |
| Bloom Filter | Web Crawler | — | Distributed Cache |
| Message Broker / Kafka | — | Notification, YouTube | Dist. Queue, Ad Click, Ride Sharing |
| Sharding | URL Shortener, Key-Value | — | All hard problems |
| Read Replicas | — | Instagram, YouTube | Search, Google Drive |
| Idempotency Keys | Booking System | Notification | Payment, WhatsApp |
| WebSocket | — | WhatsApp | Chat, Stock Exchange |
| Geo-Indexing | — | — | Ride Sharing, Google Maps |
| Vector DB | — | — | RAG System |
| Object Storage | Pastebin | YouTube, Instagram | Google Drive, S3 LLD |
| Rate Limiting | Rate Limiter | — | API Gateway in all hard problems |

---

## LLD PATTERN × PROBLEM MATRIX

| Pattern | Classic Usage | LLD Problem |
|---------|--------------|-------------|
| Singleton | Config, Logger | logger-library, lru-cache |
| Observer | Event bus, notifications | comment-system, mentorship-platform |
| Strategy | Payment methods, sort algos | vending-machine, coupon-system |
| State | Order lifecycle, elevator | elevator-system, vending-machine |
| Command | Undo/redo, job queue | text-editor, download-manager |
| Builder | Query DSL, request objects | s3-object-storage |
| Decorator | Middleware stack, logging | logger-library |
| Factory | DB connection, notification channel | coupon-system |
| Composite | File system tree | version-control |
| Proxy | Lazy loading, access control | lru-cache, locker-service |

---

## REPO HEALTH STATUS

| Section | Files | Coverage | Status |
|---------|-------|----------|--------|
| 01-foundations | 5 | Core theory complete | ✅ |
| 02-building-blocks | 13 | All blocks documented | ✅ |
| 03-scaling | 4 | Deep-dive files added | ✅ |
| 04-advanced-topics | 6 + 6 internals | Complete | ✅ |
| 05-hld-problems | 27 | All catalogued, paths verified | ✅ |
| 06-lld/oop | 4 | Both Java + Python | ✅ |
| 06-lld/solid | 5 | One file per principle | ✅ |
| 06-lld/patterns | 16 | All 3 categories | ✅ |
| 06-lld/concurrency | 4 | RWLock, Semaphore, Latch, CompletableFuture added | ✅ |
| 06-lld/problems | 23 | All tiers covered | ✅ |
| 07-interview-templates | 4 | Both HLD + LLD templates | ✅ |
| 08-reference | 5+ | Glossary, numbers, cloud cheatsheet | ✅ |
| 09-patterns | 5 | Outbox, Saga, CQRS, Strangler, Anti | ✅ |

---

## COMPANY TARGETING

```
COMPANY TARGETING
│
├── Google    → Web Crawler, Autocomplete, Search System, Distributed Cache,
│               Google Maps, Ad Click Aggregator  [+ internals of Bigtable/Spanner concepts]
│
├── Meta      → Twitter News Feed, Instagram, WhatsApp, Notification Service
│               [fan-out is the dominant pattern — master push vs pull]
│
├── Amazon    → Rate Limiter, Distributed Message Queue, Unique ID Generator,
│               E-Commerce Platform  [exactly-once semantics matter here]
│
├── Uber      → Ride Sharing, Distributed Message Queue, Booking System
│               [geo-indexing + real-time dispatch]
│
├── Stripe    → Payment System  [double-entry ledger + idempotency key + reconciliation]
│
├── Robinhood → Stock Exchange  [order book + matching engine + sub-ms latency]
│
└── OpenAI /
    Anthropic → LLM Chat System, RAG System
                [token budgeting + streaming + vector retrieval pipeline]
```
