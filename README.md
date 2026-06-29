# Amazon SDE-2 Interview Preparation

Focused system design prep for Amazon SDE-2 HLD and LLD interviews. The repo is organized around practical interview execution: concepts, reusable building blocks, problem practice, templates, and reference material.

---

## Start Here

1. Read [SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md](SYSTEM_DESIGN_INTERVIEW_FRAMEWORK.md).
2. For HLD, use [hld-template.md](07-interview-templates/hld-template.md) and [hld-cheat-sheet.md](07-interview-templates/hld-cheat-sheet.md).
3. For LLD, use [lld-template.md](07-interview-templates/lld-template.md) and [lld-cheat-sheet.md](07-interview-templates/lld-cheat-sheet.md).
4. Practice problems from [05-hld-problems](05-hld-problems/README.md) and [06-lld](06-lld/README.md).
5. Use [last-week-prep.md](07-interview-templates/last-week-prep.md) for final revision.

---

## Amazon SDE-2 Scope

### HLD

Amazon SDE-2 HLD usually tests medium-scale systems, clear trade-offs, operational thinking, and failure handling. Be ready to discuss:

- DynamoDB-style access-pattern modeling;
- SQS/SNS fan-out, retries, DLQs, and visibility timeout;
- idempotency for payment, booking, order, and job workflows;
- cache strategy and invalidation;
- alarms, dashboards, runbooks, and graceful degradation.

### LLD

Amazon SDE-2 LLD usually tests clean object modeling and the ability to code a correctness-critical flow. Be ready to discuss:

- OOP fundamentals and SOLID;
- Strategy, State, Factory, Builder, Observer, Decorator, Composite, Chain of Responsibility;
- concurrency around small critical sections;
- class diagrams and extensibility;
- practical edge cases, not just pattern names.

---

## Repository Map

### Foundations

- [01-foundations](01-foundations/README.md) — fundamentals and networking.

### Building Blocks

- [02-building-blocks](02-building-blocks/README.md) — load balancers, caching, CDN, API gateway, reverse proxy, message brokers, rate limiting, replication, sharding, consistent hashing, SQL fundamentals.

### Scaling

- [03-scaling](03-scaling/README.md) — scaling strategies and database scaling.

### HLD Problems

- [05-hld-problems](05-hld-problems/README.md) — 21 HLD problems across easy, medium, and hard practice.
- Amazon priority: Rate Limiter, Unique ID Generator, E-Commerce, Notification Service, Booking System, Web Crawler, Distributed Job Scheduler, Distributed Cache, Payment System, Ticketmaster.

### LLD

- [06-lld](06-lld/README.md) — OOP, SOLID, design patterns, concurrency, and 18 LLD problems.
- Tier 1: Parking Lot, Rate Limiter, Tic-Tac-Toe, Vending Machine, Splitwise, BookMyShow, LRU Cache, ATM.

### Interview Templates

- [07-interview-templates](07-interview-templates/README.md) — HLD/LLD templates, cheat sheets, capacity estimation, API design, database selection, mock problems, Amazon HLD guide, and last-week prep.

### Reference

- [08-reference](08-reference/README.md) — numbers to know and supporting reference notes.

---

## How to Practice

For every HLD problem:

1. Clarify requirements.
2. Estimate QPS/storage/bandwidth.
3. Define APIs and access patterns.
4. Draw the architecture.
5. Deep-dive the hardest bottleneck.
6. Cover failures, retries, idempotency, and alarms.

For every LLD problem:

1. Clarify actors and use cases.
2. Identify entities, services, strategies, and repositories.
3. Draw the class diagram.
4. Code the critical path.
5. Explain concurrency and edge cases.

---

## Amazon LP Alignment

Tie design decisions to Leadership Principles naturally:

- **Customer Obsession**: latency, availability, clear failure behavior.
- **Ownership**: alarms, runbooks, operational recovery.
- **Dive Deep**: explain hot partitions, stale cache, retries, duplicate events.
- **Deliver Results**: scope the design to what fits in the interview.
- **Bias for Action**: start simple, then evolve at the next scale threshold.
