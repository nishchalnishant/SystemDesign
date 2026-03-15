# Replication

> **Copying data across multiple nodes for availability, read scaling, and durability.**

---

## 1. Concept Overview

**Replication** keeps multiple copies of data (replicas) in sync. It provides:
- **High availability**: If one node fails, others can serve traffic.
- **Read scaling**: Distribute reads across replicas.
- **Durability**: Data survives single-node (or single-datacenter) failure.

**Why it exists**: A single copy is a single point of failure and a read bottleneck. Replication addresses both.

---

## 2. Core Principles

### Topologies

| Topology | How | Use case |
|----------|-----|----------|
| **Leader–follower (primary–replica)** | One leader for writes; followers replicate; reads can go to followers | Most RDBMS, MongoDB |
| **Multi-leader** | Multiple nodes accept writes; replicate to each other | Multi-datacenter; offline-first |
| **Leaderless** | No single leader; quorum writes and reads (e.g. W=2, R=2, N=3) | Cassandra, DynamoDB |

### Sync vs async replication

| Mode | How | Pros | Cons |
|------|-----|------|------|
| **Synchronous** | Leader waits for replica(s) to ack before confirming write | No data loss on leader failover | Higher latency; availability tied to replica |
| **Asynchronous** | Leader acks immediately; replicas updated in background | Low latency; leader not blocked by replica | Replica can lag; possible data loss if leader fails before replication |

### Architecture (leader–follower)

```
  Writes ──▶ Leader ──▶ Replication stream ──▶ Follower 1
                │                              Follower 2
  Reads ──▶ Follower 1 / Follower 2 (optional)
```

---

## 3. Real-World Usage

- **PostgreSQL / MySQL**: Primary + read replicas; async or semi-sync; failover via promotion.
- **MongoDB**: Replica set; one primary, secondaries replicate; automatic failover.
- **Cassandra / DynamoDB**: Leaderless; quorum (W, R, N); tunable consistency.
- **Kafka**: Partition replicas; in-sync replicas (ISR); leader handles writes.

---

## 4. Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| **Sync replication** | No loss on failover | Latency; if replica is down, writes can block or fail |
| **Async replication** | Low latency; leader not blocked | Replication lag; possible loss on leader failure |
| **Read from replica** | Scale reads | Stale reads (lag); need to handle consistency (e.g. read-your-writes) |
| **Multi-leader** | Write locally in multiple DCs | Conflict resolution; complexity |

**When to use**: Need HA or read scaling; can tolerate eventual consistency for reads from replicas.  
**When not**: Single-node acceptable; or strong consistency with no lag (then sync and read from primary only).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Leader fails | Promote replica to leader (manual or automatic); clients reconnect to new leader |
| Replica lag** | Monitor lag; route critical reads to leader; increase replica capacity or reduce write load |
| Split brain (multi-leader) | Conflict resolution (LWW, vector clocks, CRDTs); or avoid multi-leader |
| Replication loop (multi-leader) | Use topology that avoids cycles; or use conflict-free structures |

---

## 6. Performance Considerations

- **Write latency**: Sync replication adds round-trip(s) to replica(s); async does not.
- **Read scaling**: More replicas → more read capacity; balance with replication load and storage cost.
- **Replication lag**: Depends on write volume and replica capacity; can be seconds under load.

---

## 7. Implementation Patterns

- **Single leader + N replicas**: Standard for RDBMS; async or semi-sync; read replicas for reporting and read scaling.
- **Leaderless quorum**: W + R > N for strong consistency; tune W, R for latency vs durability.
- **Cross-datacenter**: Async replica in second DC for DR; or multi-leader if needed for local writes.

---

## Quick Revision

- **Purpose**: HA, read scaling, durability.
- **Leader–follower**: One writer; replicas copy; reads can go to replicas (stale possible).
- **Sync vs async**: Sync = no loss, higher latency; async = low latency, possible loss.
- **Leaderless**: Quorum (W, R, N); no single leader; tunable consistency.
- **Interview**: “We use a primary and two async read replicas so writes are fast and we scale reads; we accept replication lag and route read-your-writes to the primary when needed.”
