# Amazon SDE-2 Interview Preparation

Focused system design prep for Amazon SDE-2. Everything unnecessary for this target has been removed. Start with the [concept-dependency-map](07-interview-templates/concept-dependency-map.md) and follow the [spaced-repetition-schedule](07-interview-templates/spaced-repetition-schedule.md).

---

## Amazon SDE-2 Scope

Amazon SDE-2 HLD: medium-scale systems (10M–100M users), DynamoDB access patterns, SQS/SNS fan-out, idempotency, operational excellence. LP alignment is expected in every design answer.

Amazon SDE-2 LLD: OOP fundamentals, SOLID, core design patterns, concurrency basics through `ReentrantReadWriteLock` + `Semaphore`. Problems 1–18 in `06-lld/05-problems/`.

---

## What's Here

### Foundations
- `01-foundations/` — networking, databases (MVCC, isolation), consistency models, caching, security, storage, CDC, change-data-capture

### Building Blocks
- `02-building-blocks/` — load balancers, caching layer, CDN, circuit breaker, consistent hashing, message brokers, rate limiting, replication, reverse proxy, service discovery, sharding, API gateway, architecture composition

### Scaling
- `03-scaling/scaling-strategies.md` — horizontal/vertical, read replicas, sharding
- `03-scaling/database-scaling-deep-dive.md` — partitioning, hot key mitigation, DynamoDB at scale

### Advanced Topics (curated for SDE-2)
- `04-advanced-topics/event-driven-architecture.md` — SQS, SNS, event-driven patterns
- `04-advanced-topics/microservices.md` — service decomposition, service mesh basics
- `04-advanced-topics/observability.md` — CloudWatch, metrics, logs, traces, alarms
- `04-advanced-topics/distributed-systems.md` — distributed system fundamentals

### HLD Problems
- `05-hld-problems/01-easy/` — URL shortener, pastebin, rate limiter, key-value store, unique ID, autocomplete, leaderboard, booking, web crawler
- `05-hld-problems/02-medium/` — notification service, e-commerce platform, WhatsApp, YouTube, Instagram, Twitter news feed, typeahead search
- `05-hld-problems/03-hard/` — hotel booking, payment system, distributed cache, distributed job scheduler, dropbox sync, Google Drive, Google Maps, ride sharing, chat system, ticketmaster

### LLD
- `06-lld/01-oop-fundamentals/` — four pillars, Java OOP, principles
- `06-lld/02-solid-principles/` — all 5 principles
- `06-lld/03-design-patterns/` — creational, structural, behavioral (all 23 GoF patterns)
- `06-lld/04-concurrency/` — concurrency patterns, producer-consumer, futures, thread-safe singleton
- `06-lld/05-problems/` — problems 1–18 (parking lot → hotel management → LRU cache → download manager)

### Patterns (Amazon-relevant)
- `09-patterns/saga-pattern.md` — compensation, DLQ, choreography vs orchestration
- `09-patterns/outbox-pattern.md` — transactional event publishing
- `09-patterns/two-phase-commit.md` — why Amazon avoids it (and uses saga instead)
- `09-patterns/anti-patterns.md`, `bulkhead-pattern.md`

### Interview Templates
- `07-interview-templates/hld-template.md` — 45-min HLD framework
- `07-interview-templates/lld-template.md` — 45-min LLD framework
- `07-interview-templates/company-specific-guide.md` — **Amazon section**: LP alignment, DynamoDB patterns, SQS+SNS, idempotency, operational excellence signals
- `07-interview-templates/trade-offs-cheat-sheet.md` — quick reference for common trade-offs
- `07-interview-templates/mock-interview-problems.md` — 10 HLD + 5 LLD problems with scope, key decisions, mistakes
- `07-interview-templates/self-assessment-rubric.md` — 1–4 scoring rubric for self-evaluation
- `07-interview-templates/interview-anti-patterns.md` — 50 failure modes to avoid
- `07-interview-templates/spaced-repetition-schedule.md` — 4-week day-by-day schedule
- `07-interview-templates/concept-dependency-map.md` — what to learn in what order
- `07-interview-templates/interview-question-bank.md` — 90 follow-up Q&A across 17 topics
- `07-interview-templates/capacity-estimation.md` — back-of-envelope templates
- `07-interview-templates/database-selection-tree.md` — when to use DynamoDB vs RDS vs Redis
- `07-interview-templates/api-design-template.md` — REST/gRPC versioning, idempotency keys
- `07-interview-templates/architecture-by-scale.md` — what architecture fits what scale

### Reference
- `08-reference/numbers-to-know.md` — latency numbers, throughput estimates
- `08-reference/system-design-glossary.md` — key term definitions
- `08-reference/cloud-services-cheat-sheet.md` — AWS services relevant to system design

---

## Start Here (Amazon SDE-2)

1. Read `07-interview-templates/concept-dependency-map.md` — understand what depends on what
2. Follow `07-interview-templates/spaced-repetition-schedule.md` Weeks 1–2 (skip Week 3 internals section)
3. Read `07-interview-templates/company-specific-guide.md` Amazon section
4. Do mock problems from `07-interview-templates/mock-interview-problems.md` (easy → medium)
5. Self-score with `07-interview-templates/self-assessment-rubric.md`
6. Review `07-interview-templates/interview-anti-patterns.md` before each mock

---

## Amazon LP Alignment in Design Answers

Every system design answer at Amazon should map to at least one LP:
- **Customer Obsession** — prioritize availability + latency for user-facing systems
- **Ownership** — propose runbooks, CloudWatch alarms, on-call procedures
- **Dive Deep** — be ready to go from HLD → implementation detail on any component
- **Deliver Results** — scope the design to what's achievable, not the "perfect" 10-year system
- **Bias for Action** — pick a reasonable design and defend it; don't hedge everything

Say things like: "I'd start with this simpler design and iterate based on load testing" (Bias for Action) or "I'd add CloudWatch alarms on queue depth and p99 latency with a runbook" (Ownership).
