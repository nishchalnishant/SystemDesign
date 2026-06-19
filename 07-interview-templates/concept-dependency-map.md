---
module: 07-interview-templates
topic: Concept Dependency Map
status: unread
tags: [07-interview-templates, learning-path, prerequisites, dependency-map]
---
# Concept Dependency Map

> Every concept in this repo has prerequisites. Learn them in dependency order or you'll memorize facts without understanding. This map shows what to learn before what — and which repo files contain each concept.

---

## How to Read This Map

- **→** means "is a prerequisite for"
- Numbers in parentheses show recommended study order within a tier
- File paths are relative to repo root
- Mark concepts as you learn them — gaps in earlier tiers cause confusion in later ones

---

## Tier 0 — Absolute Prerequisites (no dependencies)

Must know these before anything else. If any are fuzzy, stop and review.

| # | Concept | File | Test yourself |
|---|---|---|---|
| 0.1 | OSI model, TCP/IP, HTTP/HTTPS, DNS | `01-foundations/networking.md` | Explain what happens between typing a URL and seeing a page |
| 0.2 | Latency numbers: memory, SSD, network | `01-foundations/fundamentals.md` | Quote L1 cache, RAM, SSD, 1Gbps network latency without looking |
| 0.3 | CPU, memory, disk I/O — what limits what | `01-foundations/fundamentals.md` | Given a workload description, name the likely bottleneck |
| 0.4 | SQL basics: SELECT, JOIN, INDEX, EXPLAIN | `01-foundations/databases.md` | Write a query with a covering index for a given access pattern |
| 0.5 | Big-O notation | (general knowledge) | Know O(1), O(log N), O(N), O(N log N), O(N²) and when each appears |

---

## Tier 1 — Core Distributed Systems

Learn these in the order listed. Each depends on the previous.

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

---

## Tier 2 — Consistency & Databases

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

---

## Tier 3 — Async Systems & Patterns

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

---

## Tier 4 — Advanced Distributed Systems

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

---

## Tier 5 — Microservices & Operations

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
| 5.5 | Security architecture: OAuth 2.0, JWT, zero-trust | `01-foundations/security.md`, `07-interview-templates/security-compliance-checklist.md` | 5.1 |
| 5.6 | Chaos engineering | `04-advanced-topics/chaos-engineering.md` | 5.4 |
| 5.7 | Architecture by scale: monolith → services → global | `08-reference/architecture-by-scale.md` | 5.1, 4.10 |

---

## LLD Dependency Chain

LLD has its own prerequisite chain, independent of HLD.

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

---

## HLD Problem Prerequisites

Before attempting each HLD problem, you should have mastered these concepts:

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

---

## Study Path by Time Available

### 2 weeks (crunch — focus on signal, not breadth)
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

### 4 weeks (solid prep)
```
Week 1: Tier 0 + Tier 1 + Tier 2 (complete) + LLD L0–L2
Week 2: Tier 3 (complete) + Tier 4 (4.1–4.6) + LLD L3
Week 3: Tier 4 (complete) + Tier 5 + LLD problems 1–13
Week 4: Mock interviews daily (1 HLD + 1 LLD per day)
         Self-score with rubric; review weakest dimensions
         Review company-specific guide for target company
```

### 8 weeks (thorough — all internals)
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

---

## Concept Clusters (for Review Sessions)

Group these for 60-minute review blocks:

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
