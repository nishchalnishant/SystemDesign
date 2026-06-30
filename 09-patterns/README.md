---
module: 09-patterns
topic: System Design Patterns
status: unread
tags: [09-patterns, system-design, distributed-patterns]
---
# System Design Patterns

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** An index of common cross-cutting distributed system patterns and anti-patterns.
>
> **Key concepts:**
> - Patterns vs Building Blocks: While a load balancer is a building block, the way you use it to route traffic during a monolith-to-microservice migration is a *pattern* (Strangler Fig).
> - Distributed Transactions: Handling state changes across multiple services (Saga, 2PC, Outbox).
> - Resiliency: Preventing total system collapse when one component fails (Bulkhead, Anti-patterns).
>
> **Key takeaway:** In system design interviews, being able to name-drop a pattern (e.g., "To avoid the dual-write problem, we'll use the Outbox pattern here") immediately signals seniority and deep distributed systems knowledge.

These are cross-cutting distributed system patterns — each one solves a specific class of problem that appears across many different systems. Unlike building blocks (which are components), these are *protocols and approaches* that govern how components interact.

---

## Pattern Map

| Pattern | Solves | Use When |
|---------|--------|----------|
| [Saga](01-data-consistency/03-saga-pattern.md) | Distributed transactions without 2PC | Microservices, order fulfillment, payment flows |
| [CQRS + Event Sourcing](02-architecture-and-scaling/01-cqrs-event-sourcing.md) | Read/write model separation + full audit trail | High read:write ratio, audit requirements, undo/replay |
| [Outbox](01-data-consistency/01-outbox-pattern.md) | Guaranteed event delivery without dual-write | Any service that must write to DB AND publish event atomically |
| [Bulkhead](02-architecture-and-scaling/02-bulkhead-pattern.md) | Fault isolation between subsystems | Prevent one slow dependency from collapsing the whole service |
| [Strangler Fig](03-migration-and-pitfalls/01-strangler-fig.md) | Incremental legacy migration | Replacing a monolith without a big-bang rewrite |
| [Two-Phase Commit](01-data-consistency/02-two-phase-commit.md) | Atomic commit across multiple databases | Distributed transactions requiring strong consistency |
| [Anti-Patterns](03-migration-and-pitfalls/02-anti-patterns.md) | Learn what NOT to do | Before designing any system — know the failure modes |

---

## When to Reach for Each Pattern

### You need distributed transactions
- **Saga** — if you can tolerate eventual consistency and can write compensating transactions (refund, cancel, rollback)
- **Two-Phase Commit** — if you need atomic commits and can accept blocking risk; use only within a single org's systems
- **Outbox** — if you just need "write to DB and publish event" to be atomic; simpler than Saga for single-service scenarios

### You need to separate reads from writes
- **CQRS** — read model and write model are different shapes; query performance suffers when using the same model for both

### You need to handle failures gracefully
- **Bulkhead** — one dependency (payment service, recommendations) is slow; you don't want it to exhaust your thread pool and take down checkout
- **Circuit Breaker** (see `02-building-blocks/circuit-breaker.md`) — open the circuit when error rate exceeds threshold; fail fast instead of waiting

### You need to migrate incrementally
- **Strangler Fig** — route traffic gradually from old system to new; no cutover risk

---

## Which HLD Problems Use These Patterns

| HLD Problem | Patterns Used |
|-------------|--------------|
| Payment System | Saga, Outbox, Idempotency |
| E-Commerce Platform | Saga (order flow), CQRS (order history vs live cart), Bulkhead |
| Notification Service | Outbox (guaranteed delivery), Bulkhead (email vs SMS isolation) |
| Distributed Message Queue | CQRS (producer model ≠ consumer model) |
| Ride Sharing | Saga (ride lifecycle: match → confirm → complete → pay) |
| GitHub Code Repo | Event Sourcing (every commit is an event; full history is the log) |

---

## Study Order

Read these in dependency order — later patterns assume you know the earlier ones:

1. **Anti-Patterns** — know what to avoid before learning what to do
2. **Outbox** — simplest; understand atomic write + publish first
3. **Saga** — builds on outbox; adds multi-step compensation
4. **Two-Phase Commit** — understand why Saga exists by seeing 2PC's downsides first
5. **CQRS + Event Sourcing** — advanced; requires understanding event-driven architecture
6. **Bulkhead** — read alongside `02-building-blocks/circuit-breaker.md`
7. **Strangler Fig** — operational pattern; read last, alongside `04-advanced-topics/microservices.md`
