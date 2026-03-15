# Scaling Strategies

> **How to scale systems: horizontal vs vertical, database scaling, replication, partitioning, caching, queues, and async processing.**

---

## 1. Concept Overview

**Scaling** is increasing a system’s capacity to handle more load (traffic, data, or both). Strategies differ by **what** you scale (compute, storage, reads, writes) and **how** (vertical vs horizontal, replication vs sharding, sync vs async).

**Why it matters**: At SDE-3 level you are expected to choose and justify scaling strategies and explain trade-offs (cost, complexity, consistency, operations).

---

## 2. Horizontal vs Vertical Scaling

### Vertical scaling (scale up)

- **What**: Add more CPU, RAM, or disk to the same machine.
- **Pros**: Simple; no distributed systems; no application changes.
- **Cons**: Hard limits (max instance size); single point of failure; often more expensive per unit capacity at high end.
- **When**: Early stage; quick fix; or components that are hard to distribute (e.g. legacy DB).

### Horizontal scaling (scale out)

- **What**: Add more machines (nodes); distribute load and data across them.
- **Pros**: No single ceiling; can use cheaper hardware; fault tolerance (multiple nodes).
- **Cons**: Complexity (distribution, consistency, coordination); operational overhead.
- **When**: Growth beyond one machine; need for HA and elasticity.

### Comparison

| Aspect | Vertical | Horizontal |
|--------|----------|------------|
| **Complexity** | Low | High |
| **Limit** | Single machine max | Theoretically unlimited |
| **Failure** | Single point of failure | N-1 resilience |
| **Cost** | Often nonlinear at high end | Linear with nodes |
| **Application** | Usually no change | May need stateless design, sharding, etc. |

---

## 3. Database Scaling

### Read scaling

- **Read replicas**: Primary takes writes; replicas replicate (async or sync); reads go to replicas.
- **Caching**: Cache hot data in front of DB (Redis, Memcached); reduces DB read load.
- **CDN**: For static or cacheable content; offload entirely from DB.

**Trade-off**: Replicas can lag; cache can be stale. Use “read-your-writes” (route user’s reads to primary or same replica) when needed.

### Write scaling

- **Sharding**: Partition data by key across multiple DB instances; each shard takes a fraction of writes. See [building-blocks/sharding.md](../building-blocks/sharding.md).
- **Async writes**: Accept write in API, persist to queue, workers write to DB (write-behind); increases write throughput and smooths spikes.
- **Batching**: Group many small writes into fewer large writes (e.g. time or count threshold).

**Trade-off**: Sharding adds complexity (routing, resharding, cross-shard queries); async writes add eventual consistency and operational complexity.

### Storage scaling

- **Sharding**: More shards → more total storage.
- **Archival**: Move old data to cold storage (e.g. S3, Glacier); keep hot data in primary DB.
- **Compression and encoding**: Reduce size per row; more rows per node.

---

## 4. Replication Strategies

- **Leader–follower**: One primary, N replicas; simple; read scaling and HA. See [building-blocks/replication.md](../building-blocks/replication.md).
- **Multi-leader**: Multiple primaries (e.g. per region); conflict resolution required.
- **Leaderless (quorum)**: W + R > N for consistency; tunable W, R for latency vs durability.

**When**: Replication for HA and read scaling; multi-leader only when you need writes in multiple regions and can handle conflicts.

---

## 5. Partitioning (Sharding) Strategies

- **Hash-based**: `shard = hash(key) % N`; even distribution; resharding costly (use consistent hashing to reduce moves).
- **Range-based**: Ranges of key (e.g. A–M, N–Z); good for range queries; risk of hotspots.
- **Directory-based**: Lookup table key → shard; flexible but lookup can be bottleneck.

**When**: Write or storage exceeds single node; design access patterns around shard key to avoid cross-shard queries.

---

## 6. Caching Strategies

- **Cache-aside**: App loads DB on miss and fills cache; good for read-heavy, variable access.
- **Write-through**: Write DB + cache; consistent but higher write cost.
- **Write-behind**: Write cache, async to DB; high write throughput; risk of loss.
- **TTL and invalidation**: Balance freshness vs hit rate; invalidate on write for critical data.

**When**: Read-heavy; latency-sensitive; can tolerate staleness or explicit invalidation. See [core-concepts/caching-cdn.md](../core-concepts/caching-cdn.md) and [building-blocks/caching-layer.md](../building-blocks/caching-layer.md).

---

## 7. Queue-Based Architectures

- **Decouple producers and consumers**: API responds quickly; workers process asynchronously (emails, notifications, analytics).
- **Load leveling**: Spike in requests → queue absorbs; workers drain at steady rate.
- **Backpressure**: When queue or downstream is overloaded, slow or reject producers (e.g. 503, or backpressure in streams).

**When**: Async processing acceptable; need to absorb spikes or integrate with external systems. See [building-blocks/message-brokers.md](../building-blocks/message-brokers.md).

---

## 8. Asynchronous Processing

- **Async I/O**: Non-blocking calls; one thread can handle many requests (e.g. Node.js, async/await).
- **Async workflows**: Request returns immediately; long-running work in queue + workers; notify when done (webhook, polling, or SSE).
- **Event-driven**: Services emit events; other services react; eventual consistency.

**Trade-off**: Simplicity and strong consistency (sync) vs scalability and resilience (async). Async requires idempotency and retry handling. See [advanced-topics/distributed-concepts.md](../advanced-topics/distributed-concepts.md) (idempotency, retry, backpressure).

---

## 9. Decision Summary

| Goal | Strategy | Trade-off |
|------|----------|-----------|
| More read capacity | Read replicas, cache | Staleness, replication lag |
| More write capacity | Sharding, async writes | Complexity, eventual consistency |
| More storage | Sharding, archival | Resharding cost, query limits |
| High availability | Replication, multi-AZ | Consistency vs availability (CAP) |
| Lower latency | Cache, CDN, closer regions | Staleness, cost |
| Absorb spikes | Queues, async | Operational complexity, eventual consistency |

---

## Quick Revision

- **Vertical**: Bigger machine; simple, limited. **Horizontal**: More machines; scalable, more complex.
- **Reads**: Replicas + cache. **Writes**: Sharding + optional async.
- **Replication**: Leader–follower common; sync vs async trade-off.
- **Sharding**: Hash/range/directory; design around shard key.
- **Caching**: Cache-aside common; TTL and invalidation for freshness.
- **Queues**: Decouple and level load; design for at-least-once and idempotency.
- **Interview**: “We scale reads with read replicas and Redis cache; we scale writes by sharding by user_id when we outgrow one DB. We use queues for notifications and analytics so the API stays fast and we absorb traffic spikes.”
