# Caching Layer

> **In-memory store (e.g. Redis, Memcached) used to serve hot data with low latency and reduce load on the primary store.**

---

## File Mindmap

```
Caching Layer
├── Why It Exists
│   ├── Problem → 1M users, 100K req/s, profiles change weekly; PostgreSQL handles ~10K queries/s; math fails
│   └── Forces → RAM access ~100ns; DB over network 1–20ms; 10,000–200,000× speed difference
├── Cache Patterns
│   ├── Cache-Aside (Lazy Loading)
│   │   ├── App checks cache → hit: return; miss: query DB → write to cache → return
│   │   ├── Pros → only cache what's read; cache failure doesn't break app
│   │   └── Cons → cache miss penalty (2 round trips); stale data possible
│   ├── Read-Through
│   │   ├── Cache sits in front of DB; cache fetches from DB on miss automatically
│   │   ├── Pros → cache always populated on read; app code simpler
│   │   └── Cons → first read always slow; cache layer must understand data model
│   ├── Write-Through
│   │   ├── Every write goes to cache AND DB synchronously
│   │   ├── Pros → cache always fresh; no stale reads
│   │   └── Cons → write latency doubled; cache fills with rarely-read data
│   └── Write-Behind (Write-Back)
│       ├── Write → cache only; async flush to DB later
│       ├── Pros → very fast writes; batch DB writes
│       └── Cons → data loss if cache fails before flush; complex recovery
├── Eviction Policies
│   ├── LRU (Least Recently Used) → evict item not accessed longest; good for temporal locality
│   ├── LFU (Least Frequently Used) → evict item accessed fewest times; good for frequency skew
│   └── TTL (Time To Live) → evict after fixed time; simplest; controls staleness
├── Cache Stampede (Thundering Herd)
│   ├── Scenario → popular key expires; N concurrent requests all miss; all query DB simultaneously
│   ├── Fix 1 → Single-flighter (mutex) → one request fetches; others wait on same promise
│   ├── Fix 2 → TTL Jitter → add ±random seconds to TTL; stagger expiry across keys
│   └── Fix 3 → Pre-warming → background job refreshes key before expiry
├── Hot Key Problem
│   ├── Scenario → single key (celebrity tweet) gets millions of req/s; one Redis node saturates
│   ├── Fix 1 → Key replication → store same value under key_0…key_9; read random suffix
│   └── Fix 2 → L1 local cache → Caffeine in-process cache in front of Redis; reduce Redis calls
├── Redis vs Memcached
│   ├── Redis → data structures (hash, sorted set, list, stream); persistence; pub/sub; Lua scripts
│   ├── Memcached → simple key-value; multi-threaded; no persistence; pure cache
│   └── Choose Redis → if you need more than simple get/set; almost always Redis in production
├── Cache Metrics
│   ├── Hit rate = hits / (hits + misses); target > 90% for performance benefit
│   ├── Eviction rate → rising eviction = cache too small or TTL too short
│   └── Miss penalty → time spent on DB fetch per cache miss; bound by DB latency
├── Implementation Patterns
│   ├── Single node → dev; SPOF; not for production
│   ├── Replicated → primary + replica; read scale + HA; leader failover
│   ├── Distributed (Redis Cluster) → sharded across N nodes; each holds subset of keys
│   └── Multi-level → L1 Caffeine (in-process, µs) → L2 Redis (shared, sub-ms) → L3 DB
├── Trade-offs
│   ├── Pros → massive latency reduction; DB load offload; enables read scale
│   └── Cons → stale data; cache invalidation complexity; extra infrastructure; stampede risk
└── Interview Angles
    ├── "Which cache pattern do you use?" → cache-aside for most; write-through if consistency critical
    ├── "How do you handle cache stampede?" → single-flighter + TTL jitter + background refresh
    ├── "What is your cache invalidation strategy?" → TTL + event-driven invalidation on write
    └── Follow-up: "How do you handle hot keys in Redis?" → key replication with random suffix; L1 local cache
```

---

## 1. Why Caching Exists

**Question**: Your user profile API makes a SELECT on every request. Profiles change once a week. You have 1M active users and 100k req/s. Your PostgreSQL can handle ~10k complex queries/second at acceptable latency. The math doesn't work — what do you do?

**Physical constraint**: RAM access is ~100ns. A disk seek (even SSD) is ~100µs — 1,000× slower. A database query involves parsing, planning, disk I/O, and network — often 1–10ms. Serving 100k req/s at 5ms average means 500 concurrent queries — well beyond a single DB node's capacity for non-trivial workloads. There is no vertical scaling path that makes a disk as fast as RAM.

**Minimal solution**: Add an in-process HashMap. Populate it on first request, never expire it. Works until: the map grows to exhaust JVM heap, deploys wipe it cold (thundering herd on restart), multiple app instances have different cached states, and you can never serve fresh data after an update.

**Production generalization**: A shared external cache (Redis) sits between all app instances and the database. It has a bounded size with an eviction policy, TTL-based expiry for freshness control, and explicit invalidation on writes. When Redis is unavailable, the app falls back to the database — degraded performance, not total failure.

---

## 2. Core Principles

### Patterns

- **Cache-aside**: App checks cache; on miss, loads from DB and populates cache. App owns logic.
- **Read-through**: Cache layer loads from DB on miss; app only talks to cache.
- **Write-through**: Writes go to DB and cache together; cache always consistent with DB.
- **Write-behind**: Writes go to cache first; DB updated asynchronously (higher performance, risk of loss).

**Pattern analogies (the chef's mise en place)**:

- **Cache-aside**: The chef personally checks the prep bowl. If it's empty, they go to the walk-in, grab ingredients, and restock the bowl. The chef is the app — it controls the bowl directly. Most common pattern in software because it degrades gracefully: if the cache is down, the chef just goes to the walk-in every time (higher latency but correct).

- **Read-through**: A dedicated prep cook manages the bowl. The chef always asks the prep cook for onions. If the prep cook is out, they go get more without the chef worrying about it. The app never talks to the DB directly — only to the cache layer, which handles misses internally. Simpler app code; the cache vendor handles the DB fallback.

- **Write-through**: Whenever new produce arrives, the chef updates both the walk-in inventory log (DB) and restocks the prep bowl simultaneously. The bowl is always in sync with the walk-in. Reads are always fresh. The cost: every write is a double operation, and the cache fills with data that may never be read (cache pollution from rarely-used items).

- **Write-behind (write-back)**: The chef updates the prep bowl immediately and writes to the inventory log later in a batch. The kitchen moves fast during service. The risk: if a fire breaks out before the log is updated, you've lost the inventory record. In software: high write throughput at the cost of potential data loss if the cache node dies before flushing to the DB.

### Eviction

- **LRU** (Least Recently Used): Evict least recently accessed (common default).
- **LFU** (Least Frequently Used): Evict least frequently accessed.
- **TTL**: Expire after a fixed time; good for time-sensitive data.

**Eviction analogy**: The prep station has limited counter space. When it fills up:
- **LRU**: Throw out the ingredient you haven't touched in the longest time. If you haven't used tarragon in 3 hours, clear it.
- **LFU**: Throw out the ingredient you've used the fewest times during service. Saffron got touched twice; garlic got touched 200 times. Clear the saffron.
- **TTL**: Every item has a sticky note with an expiry time. Chopped herbs lose their flavor after 2 hours — throw them out at the 2-hour mark regardless of how much is left.

### Architecture

```
  App ──▶ Cache (Redis) ──▶ HIT → return
                │
                │ MISS
                ▼
            Database
```

### Java — Cache-Aside with Redis

```java
public UserProfile getUserProfile(String userId) {
    String cacheKey = "user:profile:" + userId;

    // 1. Check cache
    String cached = redisClient.get(cacheKey);
    if (cached != null) {
        return objectMapper.readValue(cached, UserProfile.class);
    }

    // 2. Cache miss — load from DB
    UserProfile profile = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));

    // 3. Populate cache with TTL
    redisClient.setex(cacheKey, 3600, objectMapper.writeValueAsString(profile));

    return profile;
}

public void updateUserProfile(String userId, UserProfile updated) {
    userRepository.save(updated);

    // Invalidate stale cache entry on write
    redisClient.del("user:profile:" + userId);
}
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
| **In-memory** | Very low latency (sub-ms) | Cost; size limit; volatile unless persisted |

**When to use**: Read-heavy workload; latency-sensitive; can tolerate staleness or invalidate on write.  
**When not**: Write-heavy with strong consistency (every write invalidates the cache immediately); or data doesn't have locality (random access pattern → low hit rate, cache is waste of memory).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Cache down | Fall back to DB; accept higher latency; optional stale cache from replica |
| Stampede (many requests on same miss) | Single-flighter or lock; TTL jitter; prewarm hot keys |
| Stale data | TTL; invalidate on write (delete or update cache); version in key |
| Memory full | Eviction policy (LRU/LFU); scale cache size or shard |
| Hot key | Replicate hot key to multiple cache nodes; local in-process L1 cache |

**Cache stampede — the rush on the prep station**: It's 12:05pm and lunch rush hits. The 1-hour TTL on the "daily special" prep bowl expired at noon. Fifty orders arrive simultaneously for the daily special. All 50 cooks check the bowl, find it empty, and all 50 run to the walk-in at once. The walk-in is overwhelmed (DB gets 50 simultaneous queries for the same data). This is the cache stampede, also called the thundering herd.

Solutions:
1. **Single-flighter / mutex lock**: Only one cook is allowed to restock at a time. The other 49 wait, then get served from the freshly-stocked bowl. In code: a distributed lock or `singleflight` pattern ensures only one goroutine/thread queries the DB per key.
2. **TTL jitter**: Add randomness to TTL so not all items expire simultaneously (e.g. `3600 ± rand(300)` seconds). Staggered expirations prevent mass simultaneous misses.
3. **Prewarm**: Before TTL expires, a background job refreshes the cache asynchronously. The bowl never actually goes empty during service.

**Hot key problem — everyone orders the same dish**: Every customer at the restaurant wants the Caesar salad. The Caesar prep station is overwhelmed — one chef can't chop lettuce fast enough. Other stations stand idle.

In Redis, a hot key is a single cache key receiving orders-of-magnitude more traffic than others. A single Redis node handling `user:profile:celebrity123` at 100k reads/second will bottleneck.

Solutions:
1. **Key replication**: Pre-shard the hot key across multiple Redis nodes: `user:profile:celebrity123:shard0`, `:shard1`, `:shard2`. App randomly picks a shard to read from, distributing load.
2. **Local L1 cache**: Keep a tiny in-process cache (e.g. Caffeine in Java) for the hottest keys with a 1–5s TTL. Reads never leave the application server for those keys. The database sees near-zero reads for them.

```java
// Caffeine local L1 cache for hot keys
LoadingCache<String, UserProfile> localCache = Caffeine.newBuilder()
    .maximumSize(1_000)
    .expireAfterWrite(5, TimeUnit.SECONDS)
    .build(key -> redisClient.getProfile(key));  // L1 miss → L2 Redis
```

**Cache down — walk-in locked**: If the prep station is locked (Redis is down), the kitchen doesn't stop — cooks go directly to the walk-in every single order. Dinner service continues, just slower. This is the graceful degradation principle: cache failure should degrade performance, not cause total system failure.

---

## 6. Performance Considerations

- **Latency**: Sub-millisecond for cache hit; single-digit milliseconds for cache miss + DB query + backfill.
- **Throughput**: A single Redis node handles 100k–1M ops/second. A single DB node handles far less at comparable latency.
- **Hit rate**: Design keys and TTL so hot data stays in cache; monitor hit ratio. A 90% hit rate means 90% of reads never touch the DB. A 50% hit rate barely helps — the DB still sees half your read load.

**Hit rate is the north star metric**: Caching infrastructure is only valuable if the hit rate is high. Low hit rate = wrong keys cached, TTL too short, or access pattern is too random. Profile your query patterns before adding a cache.

---

## 7. Implementation Patterns

- **Single cache**: One Redis/Memcached instance; simple; single point of failure. Good for dev.
- **Replicated cache**: Primary + replicas; read from replicas for horizontal read scaling; failover to replica on primary loss.
- **Distributed cache (sharded)**: Redis Cluster; consistent hashing across nodes; see [05-hld-problems/03-hard/distributed-cache.md](../05-hld-problems/03-hard/distributed-cache.md).
- **Multi-level cache (L1/L2)**: In-process Caffeine/Guava cache (L1, ~1ms, tiny) → Redis (L2, ~1ms network, large) → DB. Useful for extreme hot key scenarios.

---

## Quick Revision

- **Purpose**: Low latency and reduced DB load by keeping hot data in RAM.
- **Cache-aside**: App checks cache, loads DB on miss, fills cache. Invalidate on write. **Write-through**: Write DB + cache together.
- **Eviction**: LRU (recency), LFU (frequency), TTL (time-based). Use TTL for freshness-sensitive data.
- **Stampede**: Single-flighter or TTL jitter to prevent mass simultaneous DB hits on same key expiry.
- **Hot key**: Shard the key or use an L1 in-process cache to avoid hammering one Redis node.
- **Mise en place analogy**: Prep bowl = cache; walk-in refrigerator = database; TTL = expiry sticker; out-of-stock + rush = stampede.
- **Interview**: "We use Redis as a cache-aside layer with a 1-hour TTL for user profiles; on miss we hit the DB and backfill. We invalidate on update. If Redis is down we fall back to the DB and accept higher latency. For hot keys like celebrity profiles we add a 5-second local Caffeine cache per app node."

**For full caching strategies, invalidation, and CDN**, see [01-foundations/caching-cdn.md](../01-foundations/caching-cdn.md).

---

## See Also

- **Conceptual layer** (when to cache, invalidation framework): [01-foundations/caching-cdn.md](../01-foundations/caching-cdn.md)
- **Redis internals** (how Redis stores data, persistence, clustering): [04-advanced-topics/internals/redis-internals.md](../04-advanced-topics/internals/redis-internals.md)
- **HLD problems that deeply use caching**: [URL Shortener](../05-hld-problems/01-easy/url-shortener.md) (redirect hot-path), [Twitter News Feed](../05-hld-problems/02-medium/twitter-news-feed.md) (timeline cache), [Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md) (caching as the product)
- **LLD problem**: [06-lld/05-problems/10-design-lru-cache.md](../06-lld/05-problems/10-design-lru-cache.md) — implement LRU from scratch with doubly linked list + HashMap

---

## Interview Questions Asked

### Conceptual
1. **"What Redis data structure would you use for a leaderboard?"** → Sorted Set (`ZSET`): members with scores, O(log N) add/update, O(log N + K) range query. `ZADD leaderboard 1500 user:42` then `ZREVRANGE leaderboard 0 9 WITHSCORES` for top 10. Testing: do you know Redis data structures beyond plain strings.
2. **"How does Redis Cluster work?"** → Data is split across 16,384 hash slots. Each primary node owns a slot range; replicas shadow the primary. Clients use a cluster-aware driver that routes to the correct node. On node failure, replica is promoted automatically. Testing: horizontal scaling mechanics of Redis.
3. **"What is the difference between Redis Pub/Sub and Redis Streams?"** → Pub/Sub: fire-and-forget, no persistence, messages lost if subscriber is offline. Streams: persistent log with consumer groups, replay, acknowledgement — like a lightweight Kafka. Use Streams when you need delivery guarantees or replay; Pub/Sub for ephemeral notifications. Testing: do you know when to pick each.

### Comparison / Trade-off
1. **"LRU vs LFU eviction — when to use each?"** → LRU (Least Recently Used): evicts the item not accessed for the longest time — good for temporal locality (recent = relevant). LFU (Least Frequently Used): evicts the item accessed fewest times — better for stable hot sets where some items are always popular. LFU has higher overhead (frequency counter per key). Use LRU as default; LFU for workloads with a stable hot set.

### Scenario / Design
1. **"How do you handle cache warming after a cold start?"** → Pre-populate by: replaying recent read traffic against the DB on startup, running a background job that loads top-N keys by frequency from DB before going live, or using a warm standby that mirrors production cache. Without warming, a fresh node causes a thundering herd on the DB. Testing: operational cache lifecycle awareness.
2. **"How do you implement distributed rate limiting with Redis?"** → Sliding window with a ZSET: add current timestamp as member, remove members older than window, count remaining. Or token bucket: store token count + last refill timestamp in a string, use `EVAL` (Lua) for atomicity. All app nodes share one Redis key per client — consistent rate limiting across instances. Testing: Redis as a coordination primitive.
