# Caching Layer

> **In-memory store (e.g. Redis, Memcached) used to serve hot data with low latency and reduce load on the primary store.**

---

## 1. Concept Overview

A **caching layer** sits between the application and the primary data store (e.g. database). It holds a subset of data in fast storage (RAM) so that repeated reads are served without hitting the DB.

**Why it exists**: Databases are slower and more expensive per operation than memory. Caching hot data reduces latency and DB load, enabling higher throughput and better user experience.

---

## 2. Core Principles

### Patterns

- **Cache-aside**: App checks cache; on miss, loads from DB and populates cache. App owns logic.
- **Read-through**: Cache layer loads from DB on miss; app only talks to cache.
- **Write-through**: Writes go to DB and cache together; cache always consistent with DB.
- **Write-behind**: Writes go to cache first; DB updated asynchronously (higher performance, risk of loss).

### Eviction

- **LRU** (Least Recently Used): Evict least recently accessed (common default).
- **LFU** (Least Frequently Used): Evict least frequently accessed.
- **TTL**: Expire after a fixed time; good for time-sensitive data.

### Architecture

```
  App ──▶ Cache (Redis) ──▶ HIT → return
                │
                │ MISS
                ▼
            Database
```

---

## 3. Real-World Usage

- **Redis**: Rich structures (strings, hashes, sets, sorted sets); persistence; replication; used for cache, session, rate limit, leaderboards.
- **Memcached**: Simple key-value; multi-threaded; often used for pure cache.
- **ElastiCache, Azure Cache**: Managed Redis/Memcached.

---

## 4. Trade-offs

| Aspect | Pros | Cons |
|--------|------|------|
| **Cache-aside** | App controls logic; cache failure → fallback to DB | Stale possible; cache stampede on miss |
| **Write-through** | Consistent reads | Higher write latency; cache pollution |
| **Write-behind** | Very fast writes | Data loss if cache dies before DB write |
| **In-memory** | Very low latency | Cost; size limit; volatile unless persisted |

**When to use**: Read-heavy workload; latency-sensitive; can tolerate staleness or invalidate on write.  
**When not**: Write-heavy with strong consistency; or data doesn’t have locality (low hit rate).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Cache down | Fall back to DB; accept higher latency; optional stale cache from replica |
| Stampede (many requests on same miss) | Single-flighter or lock; TTL; prewarm hot keys |
| Stale data | TTL; invalidate on write (delete or update cache); version in key |
| Memory full | Eviction policy (LRU/LFU); scale cache size or shard |

---

## 6. Performance Considerations

- **Latency**: Sub-millisecond for cache hit; avoid heavy serialization or large values.
- **Throughput**: In-memory cache can handle hundreds of thousands of ops/s per node.
- **Hit rate**: Design keys and TTL so hot data stays in cache; monitor hit ratio.

---

## 7. Implementation Patterns

- **Single cache**: One Redis/Memcached instance; simple; single point of failure.
- **Replicated cache**: Primary + replicas; read from replica for scaling; failover to replica.
- **Distributed cache**: Sharded (e.g. Redis Cluster); consistent hashing; see [hld-problems/hard/distributed-cache.md](../hld-problems/hard/distributed-cache.md).

---

## Quick Revision

- **Purpose**: Low latency and reduced DB load by keeping hot data in memory.
- **Cache-aside**: App checks cache, loads DB on miss, fills cache. **Write-through**: Write DB + cache.
- **Eviction**: LRU common; TTL for freshness.
- **Failure**: Cache down → DB fallback; stampede → single-flighter or TTL.
- **Interview**: “We use Redis as a cache-aside layer with a 1-hour TTL for user profiles; on miss we hit the DB and backfill. We invalidate on update. If Redis is down we fall back to the DB and accept higher latency.”

**For full caching strategies, invalidation, and CDN**, see [core-concepts/caching-cdn.md](../core-concepts/caching-cdn.md).
