# Sharding

> **Partitioning data across multiple databases or nodes to scale writes and storage beyond a single machine.**

---

## 1. Concept Overview

**Sharding** splits a dataset into shards (e.g. by user_id or key range), each stored on a different node. It enables horizontal scaling of storage and write throughput.

**Why it exists**: A single DB has limits on disk, memory, and write capacity. Sharding spreads data and load across many nodes.

---

## 2. Core Principles

### Shard key choice

- **Critical**: Queries that don’t include the shard key require scatter-gather (all shards) or a separate index.
- **Balance**: Key should distribute data and load evenly (avoid hot shards).
- **Growth**: Prefer keys that don’t create hotspots (e.g. avoid “last N” always in one shard).

### Strategies

| Strategy | How | Pros | Cons |
|----------|-----|------|------|
| **Hash-based** | `shard = hash(key) % N` | Even distribution | Resharding moves many keys; use consistent hash to reduce |
| **Range-based** | Shard 1: A–M, Shard 2: N–Z | Range queries on shard key | Hotspots (e.g. recent data in one shard) |
| **Directory-based** | Lookup table: key → shard | Flexible; move individual keys | Lookup table is bottleneck and SPOF |
| **Consistent hash** | Ring; key → next node clockwise | Adding/removing node moves ~1/N keys | Implementation complexity |

### Architecture

```
  App ──▶ Router (shard key → shard id)
              │
              ├──▶ Shard 1 (DB instance 1)
              ├──▶ Shard 2 (DB instance 2)
              └──▶ Shard 3 (DB instance 3)
```

---

## 3. Real-World Usage

- **PostgreSQL / MySQL**: Application-level sharding (app routes by key); or Citus, Vitess.
- **MongoDB**: Sharding with shard key; config servers hold metadata.
- **DynamoDB**: Partition key (required); optional sort key; automatic sharding.
- **Kafka**: Partitions are shards; key determines partition.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Hash** | Even distribution | No range queries across shards; resharding is costly |
| **Range** | Range queries on shard key | Risk of hot shards |
| **Directory** | Flexible placement | Lookup table scalability and HA |
| **Cross-shard operations** | N/A | Joins and transactions across shards are hard; prefer denormalization or application-level join |

**When to use**: Write or storage exceeds single-node capacity; you can design access patterns around shard key.  
**When not**: Single node suffices; or you need frequent cross-shard transactions (consider alternatives first).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| One shard down | Replicate each shard (primary + replica); failover per shard |
| Hot shard | Choose different shard key; split hot shard (range); or add more replicas for reads |
| Resharding | Double-write during migration; background data move; consistent hashing to minimize moves |
| Cross-shard query | Avoid or limit; use caching/denormalization; or accept scatter-gather cost |

---

## 6. Performance Considerations

- **Latency**: Single-shard queries are fast; scatter-gather increases latency and load.
- **Throughput**: Total write throughput scales with number of shards (if balanced).
- **Resharding**: Expensive; plan for growth so resharding is rare (e.g. consistent hashing with many virtual nodes).

---

## 7. Implementation Patterns

- **Application-managed**: App has shard map; routes queries by shard key; uses connection pool per shard.
- **Proxy**: Proxy (e.g. Vitess, ProxySQL) does routing; app sends same query.
- **Managed (DynamoDB, etc.)**: Choose partition key; system shards automatically.

---

## Quick Revision

- **Purpose**: Scale writes and storage by partitioning data across nodes.
- **Shard key**: Must be in most queries; should distribute evenly; consider growth.
- **Strategies**: Hash (even), range (range queries, hotspot risk), directory (flexible, lookup cost), consistent hash (minimal resharding).
- **Cross-shard**: Avoid joins/transactions; denormalize or application join.
- **Interview**: “We shard by user_id so each user’s data is on one shard and we can scale by adding shards; we use consistent hashing so adding a node only moves about 1/N of the data.”
