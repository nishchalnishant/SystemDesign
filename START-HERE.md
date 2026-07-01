> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Quick-start guide for the repo — where to begin, fastest paths to interview readiness, and key decision files.
>
> **Key topics:**
> - Section-by-section breakdown of what each directory covers in one line
> - Two study tracks: 2-week fast-track (minimum viable prep) and 4-week full track
> - Prioritized 2-week plan: HLD template → capacity estimation → numbers to know → building blocks → HLD problems → SOLID + top 5 patterns → LLD Tier 1
> - LLD problem tiers (Tier 1: must solve, Tier 2: understand, Tier 3: read only)
> - Decision files: SQL vs NoSQL, which pattern to use, trade-off any choice
>
> **Key takeaway:** If you're short on time, follow the 2-week fast track — master the HLD template, building blocks, and Tier 1 LLD problems first.

---

# Start Here

This repo covers system design end-to-end — HLD, LLD, design patterns, and interview frameworks. It is language-agnostic (Python examples in LLD, Java discussed in OOP).

## What's in the Repo

| Section | What It Covers |
|---------|---------------|
| `01-foundations/` | Networking, databases, consistency, security, CDC, consensus |
| `02-building-blocks/` | Load balancers, caching, sharding, message brokers, rate limiting, CDN, API gateway |
| `03-scaling/` | Scaling strategies, DB scaling deep dive, LSM vs B-Tree, global distribution |
| `04-advanced-topics/` | Distributed systems, observability, event-driven architecture, microservices, chaos engineering, internals (Kafka, Redis, DynamoDB, Cassandra, Postgres, MySQL, Elasticsearch, Zookeeper) |
| `05-hld-problems/` | 36 HLD problems — easy (9), medium (7), hard (20) |
| `06-lld/` | OOP fundamentals, SOLID principles, 21 design patterns, concurrency, 36 LLD problems |
| `07-interview-templates/` | HLD/LLD frameworks, capacity estimation, trade-off cheat sheets, anti-patterns, question bank |
| `08-reference/` | Numbers to know, cloud services, glossary, book summaries |
| `09-patterns/` | Saga, CQRS/event sourcing, outbox, bulkhead, strangler fig, two-phase commit |

## Fastest Path to Interview-Ready

**If you have 2 weeks:**
1. `07-interview-templates/hld-template.md` — learn this framework cold
2. `07-interview-templates/capacity-estimation.md` — practice until automatic
3. `08-reference/numbers-to-know.md` — memorize latency numbers
4. All of `02-building-blocks/` — the Lego pieces for every HLD answer
5. HLD easy tier (all 9), then medium (all 7), then 3-4 hard problems
6. `06-lld/02-solid-principles/` + top 5 design patterns: Singleton, Factory, Strategy, Observer, Decorator
7. LLD Tier 1: Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe

**If you have 4+ weeks:** Follow the full study order in `README.md`.

## Key Decision Files

| Decision | File |
|----------|------|
| SQL vs NoSQL | `07-interview-templates/database-selection-tree.md` |
| Which pattern to use | `06-lld/03-design-patterns/README.md` |
| Trade-off any choice | `07-interview-templates/trade-offs-cheat-sheet.md` |
| Capacity math | `07-interview-templates/capacity-estimation.md` |
| Failure & recovery | `07-interview-templates/failure-recovery-playbook.md` |

## LLD Problem Tiers

| Tier | Problems | Patterns Covered |
|------|----------|-----------------|
| Tier 1 — Must solve | Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise | Singleton, Strategy, State, Factory |
| Tier 2 — Understand | Elevator, Snake & Ladder, Hotel Mgmt, LRU Cache, Comment System | Scheduling, game loops, concurrency |
| Tier 3 — Read only | Locker Service, S3, Search Engine, Version Control, Text Editor | Geo-hash, Composite, Trie, DAG, Gap Buffer |
