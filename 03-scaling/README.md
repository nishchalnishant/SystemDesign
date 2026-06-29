# Scaling

> **Strategies and concepts for scaling systems to handle growth in traffic, data, and complexity.**

## Contents

- **[Scaling Strategies](scaling-strategies.md)** — Horizontal vs vertical, database scaling (read/write), replication, partitioning, caching, queues, async processing. Use this as the main reference for “how do we scale?” in interviews.
- **[Database Scaling Deep Dive](database-scaling-deep-dive.md)** — Read replicas, sharding strategies (range, hash, directory), connection pooling, query optimization, OLTP vs OLAP separation. Use this when the bottleneck in an interview is the database tier.
- **[Global Distribution](global-distribution.md)** — Multi-region architectures, data residency, CDN topologies, latency-based routing, active-active vs active-passive, CRDTs for conflict resolution. Use this for geographically distributed systems.
- **[Database Internals](database-internals.md)** — B-tree indexes, WAL, MVCC, transaction isolation levels. Use this when interview follow-ups go deeper than "use Postgres" — indexes, replication mechanics, concurrency.

## Related

- **Building blocks**: [02-building-blocks/](../02-building-blocks/) — Sharding, replication, caching layer, message brokers.
- **Core concepts**: [01-foundations/databases.md](../01-foundations/databases.md) — Replication and sharding in depth.
- **Distributed concepts**: [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md) — Idempotency, retry, backpressure for robust scaling.
