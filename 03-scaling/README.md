> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Index for the scaling section — strategies and deep dives for handling traffic, data, and complexity growth.
>
> **Key topics:**
> - Scaling Strategies: horizontal vs vertical, read/write DB scaling, replication, partitioning, caching, queues
> - Database Scaling Deep Dive: WAL internals, MVCC, connection pooling (PgBouncer), OLTP vs OLAP separation
> - Global Distribution: multi-region, active-active vs active-passive, CRDT conflict resolution, data sovereignty
> - Database Internals: B-tree, LSM, WAL, MVCC, isolation levels for deep-dive interview follow-ups
> - LSM vs B-Tree: the fundamental trade-off between write throughput and read performance
>
> **Key takeaway:** Read Scaling Strategies first, then use the deep dives when an interviewer pushes harder on database or global architecture specifics.

---

# Scaling

> **Strategies and concepts for scaling systems to handle growth in traffic, data, and complexity.**

## Contents

- **[Scaling Strategies](01-scaling-fundamentals.md)** — Horizontal vs vertical, database scaling (read/write), replication, partitioning, caching, queues, async processing. Use this as the main reference for “how do we scale?” in interviews.
- **[Database Scaling Deep Dive](03-database-scaling.md)** — Read replicas, sharding strategies (range, hash, directory), connection pooling, query optimization, OLTP vs OLAP separation. Use this when the bottleneck in an interview is the database tier.
- **[Global Distribution](04-global-distribution.md)** — Multi-region architectures, data residency, CDN topologies, latency-based routing, active-active vs active-passive, CRDTs for conflict resolution. Use this for geographically distributed systems.
- **[Database Internals](02-database-internals.md)** — B-tree indexes, WAL, MVCC, transaction isolation levels. Use this when interview follow-ups go deeper than "use Postgres" — indexes, replication mechanics, concurrency.

## Related

- **Building blocks**: [02-building-blocks/](../02-building-blocks/) — Sharding, replication, caching layer, message brokers.
- **Core concepts**: [01-foundations/databases.md](../01-foundations/03-database-foundations/01-databases.md) — Replication and sharding in depth.
- **Distributed concepts**: [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/01-distributed-architecture/02-distributed-concepts.md) — Idempotency, retry, backpressure for robust scaling.
