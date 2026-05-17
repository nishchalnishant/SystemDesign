# System Design Patterns

> **Cross-cutting architectural patterns for building robust distributed systems. These complement the building blocks — they are the "how to combine them" layer.**

---

## Patterns in This Module

| Pattern | Problem It Solves | Key Concept |
|---------|-------------------|-------------|
| [Outbox Pattern](outbox-pattern.md) | Atomic DB write + event publish | Transactional outbox + CDC |
| [Saga Pattern](saga-pattern.md) | Distributed transactions without 2PC | Choreography vs Orchestration |
| [CQRS + Event Sourcing](cqrs-event-sourcing.md) | Read/write model separation + audit trail | Command/Query split + event log |
| [Strangler Fig](strangler-fig.md) | Migrate monolith to microservices | Incremental extraction |
| [Anti-Patterns](anti-patterns.md) | What NOT to build | Distributed monolith, shared DB |

---

## When to Use These Patterns

```
Outbox Pattern → Any time you write to DB + publish an event atomically
Saga Pattern → Distributed transaction spanning multiple services
CQRS → Read-heavy system where read/write models diverge
Event Sourcing → Need full audit trail or time-travel queries
Strangler Fig → Migrating legacy system without big-bang rewrite
Anti-patterns → Know these to avoid them in interviews
```
