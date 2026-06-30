> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Index for SDE-3 level advanced topics — distributed systems theory, observability, chaos engineering, event-driven architecture, microservices, and database internals.
>
> **Key topics:**
> - Distributed Systems: consistency models, Raft/Paxos, distributed transactions, Lamport clocks, conflict resolution
> - Distributed Concepts: idempotency, retry strategies (exponential backoff + jitter), backpressure
> - Observability: metrics, logs, distributed traces, SLI/SLO/SLA, Golden Signals, alerting
> - Chaos Engineering: fault injection types, resilience patterns, Game Days
> - Internals sub-directory: Kafka, Redis, Cassandra, PostgreSQL, Elasticsearch, ZooKeeper deep dives
>
> **Key takeaway:** These topics separate SDE-3 candidates from SDE-2 — expect deep follow-ups on distributed consistency, observability design, and failure handling.

---

# Advanced Topics

SDE-3 level deep dives: distributed systems, observability, resilience.

## Contents

| Topic | File | Description |
|-------|------|-------------|
| **Distributed Systems** | [distributed-systems.md](distributed-systems.md) | Consistency models, Raft/Paxos, distributed transactions, time & ordering, conflict resolution, ZooKeeper |
| **Distributed Concepts** | [distributed-concepts.md](distributed-concepts.md) | Idempotency, retry strategies, backpressure |
| **Observability** | [observability.md](observability.md) | Metrics, logs, traces, SLI/SLO/SLA, Golden Signals, alerting |
| **Chaos Engineering** | [chaos-engineering.md](chaos-engineering.md) | Fault injection, resilience patterns, Game Days |
| **Internals** | [internals/](internals/) | Kafka, Redis, Cassandra, PostgreSQL, Elasticsearch, ZooKeeper |

Consistency models and distributed transactions are also linked from [../distributed-systems/](../distributed-systems/) (pointers to content in distributed-systems.md).
