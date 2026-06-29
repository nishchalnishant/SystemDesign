---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Caching Layer

> **In-memory store (e.g. Redis, Memcached) used to serve hot data with low latency and reduce load on the primary store.**
> Covers: why caching exists, cache patterns, eviction policies, Redis, CDN as a cache layer, and when to use or avoid caching.

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
│   ├── ARC → adaptive; tracks both recency and frequency; used in ZFS, Oracle
│   ├── W-TinyLFU → near-optimal; used in Caffeine; Count-Min Sketch for frequency estimation
│   ├── SLRU → segmented; probationary + protected; used in Memcached
│   ├── Clock (Second Chance) → circular buffer + reference bit; used in OS page cache
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
├── CDN as Cache Layer
│   ├── Edge nodes cache static/semi-static content geographically near users
│   ├── Pull CDN → edge fetches from origin on first miss; best for large asset catalogs
│   ├── Push CDN → you upload to CDN ahead of time; best for small known asset sets
│   └── Cache-Control headers → max-age, stale-while-revalidate, no-cache, s-maxage
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

## Table of Contents

1. [Why Caching Exists](#1-why-caching-exists)
2. [The Caching Decision Framework](#2-the-caching-decision-framework)
3. [Cache Patterns](#3-cache-patterns)
4. [Cache Invalidation](#4-cache-invalidation)
5. [Eviction Policies](#5-eviction-policies)
6. [Redis as the Implementation](#6-redis-as-the-implementation)
7. [CDN as Distributed Cache Layer](#7-cdn-as-distributed-cache-layer)
8. [Caching at Each System Tier](#8-caching-at-each-system-tier)
9. [Failure Scenarios](#9-failure-scenarios)
10. [Performance Considerations](#10-performance-considerations)
11. [Implementation Patterns](#11-implementation-patterns)
12. [Trade-offs and When to Cache](#12-trade-offs-and-when-to-cache)
13. [Quick Revision](#quick-revision)
14. [Interview Questions](#interview-questions)

---

## 1. Why Caching Exists

**Question**: Your homepage loads user profile data on every page render. The profile changes once a week on average. At 100,000 page views per hour, you run 100,000 identical `SELECT * FROM users WHERE id = ?` queries per hour for the same set of popular users. Each query takes 5ms. That's 500,000ms of database compute per hour — just for reads that return the same result 99.9% of the time. What's the minimal change that eliminates most of that work?

**Physical constraint**: RAM access is ~100ns. A database query involves parsing, planning, disk I/O, and network — often 1–20ms. The same data retrieved from RAM is 10,000–200,000× faster than from a database over the network. Serving 100k req/s at 5ms average means 500 concurrent queries — well beyond a single DB node's capacity for non-trivial workloads. There is no vertical scaling path that makes a disk as fast as RAM.

**Minimal solution**: Store the query result in a local hash map in your application process, keyed by user ID. On a hit, return from RAM in microseconds. On a miss, query the DB and populate the map. Works until: the map grows to exhaust JVM heap, deploys wipe it cold (thundering herd on restart), multiple app instances have different cached states, and you can never serve fresh data after an update.

**Production generalization**: A shared external cache (Redis) sits between all app instances and the database. It has a bounded size with an eviction policy, TTL-based expiry for freshness control, and explicit invalidation on writes. When Redis is unavailable, the app falls back to the database — degraded performance, not total failure.

**The speed gap that makes caching matter:**

| Layer | Typical Latency |
|-------|----------------|
| CPU L1 cache | ~0.5 ns |
| RAM (in-process cache) | ~100 ns |
| Redis (network, same datacenter) | ~0.5 ms |
| PostgreSQL (simple query, warm) | ~5–20 ms |
| PostgreSQL (complex join) | ~100–500 ms |
| Cross-region database call | ~100–300 ms |

**The locality of reference principle** is what makes caching work in practice:

- **Temporal locality**: data you accessed recently is likely to be accessed again soon.
- **Spatial locality**: data near what you just accessed is likely to be needed next.

On YouTube, roughly 20% of videos account for more than 80% of total watch time. Cache the hot 20%, serve the cold 80% directly from the database.

---

## 2. The Caching Decision Framework

### When to Add a Cache

Add a cache when **all three** of the following are true:

1. **Read:write ratio is high (roughly > 5:1).** If data is written once and read a thousand times, caching pays for itself immediately. If data is written constantly, the cache is perpetually stale or perpetually invalidated.

2. **Reads are expensive.** Complex multi-table joins, external API calls, computed aggregations, or any operation that takes more than a few milliseconds.

3. **Staleness is tolerable.** Your system can serve data that is seconds, minutes, or hours old without incorrect behavior or harm.

**Good fit — Twitter user profile**: Read:write ratio extremely high. Multi-table join. A 15-minute-old profile is completely fine. → Cache with 15-minute TTL, invalidate on profile update.

### When NOT to Add a Cache

- **Write-heavy workload**: Cache hit rate near zero. Complexity with no benefit.
- **Freshness is critical**: Bank account balance, stock price, inventory count, seat availability. Serving a stale value can cause real harm.
- **Every user gets unique data**: Low hit rate, burning memory to store data that will never be reused.
- **Data volume enormous and access uniform**: No hot working set to cache. Cache thrashes constantly.

**Poor fit — bank account balance**: Written on every transaction. Serving a stale balance could cause overdraft decisions based on wrong data.

---

## 3. Cache Patterns

Each strategy answers: who is responsible for loading data into the cache, and when does the write reach the database?

### Cache-Aside (Lazy Loading)

The application checks the cache first. On a miss, the application fetches from the database and populates the cache itself. The cache only ever contains data that has actually been requested.

**Analogy**: The chef personally checks the prep bowl. If it's empty, they go to the walk-in, grab ingredients, and restock the bowl. The chef is the app — it controls the bowl directly. Most common pattern: degrades gracefully if cache is down.

**Use when**: General-purpose read-heavy data where cache misses on first access are acceptable.

### Read-Through

Same flow as cache-aside, but the cache library handles the database fetch automatically on a miss. The application only ever talks to the cache interface.

**Analogy**: A dedicated prep cook manages the bowl. The chef always asks the prep cook for onions. If the prep cook is out, they go get more without the chef worrying about it. Simpler app code; the cache vendor handles the DB fallback.

**Use when**: You want to centralize loading logic and ensure all code paths populate the cache consistently.

### Write-Through

Every write goes to both the cache and the database synchronously before the operation returns. Reads from cache are always fresh. Higher write latency (two sequential writes).

**Analogy**: Whenever new produce arrives, the chef updates both the walk-in inventory log (DB) and restocks the prep bowl simultaneously. The bowl is always in sync. The cost: every write is a double operation, and the cache fills with data that may never be read.

**Use when**: Data is written infrequently but read many times, and you need strong consistency.

### Write-Behind (Write-Back)

Writes go to the cache immediately and return. The database is updated asynchronously in the background, often in batches. Very low write latency; risk of data loss if the cache node dies before flushing.

**Analogy**: The chef updates the prep bowl immediately and writes to the inventory log later in a batch. If a fire breaks out before the log is updated, you've lost the inventory record.

**Use when**: High-throughput write workloads (analytics counters, ad impression logging) where some data loss is acceptable. Never use for financial records or user-generated content.

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

### Architecture

```
  App ──▶ Cache (Redis) ──▶ HIT → return
                │
                │ MISS
                ▼
            Database
```

---

## 4. Cache Invalidation

> "There are only two hard things in computer science: cache invalidation and naming things." — Phil Karlton

Why is invalidation hard? A cache is a copy of data that lives somewhere else. The moment the original changes, the copy is wrong. In a distributed system with multiple services writing to the same underlying data, any of them could change data that another service has cached — often without knowing the cache exists.

### TTL-Based Invalidation

Set a time limit on every cache entry. After the TTL expires, the next reader fetches fresh data and re-populates the cache. Simple, automatic, no extra code on writes.

The tradeoff: you may serve stale data for the entire TTL window. Choose TTL based on staleness tolerance: user profiles → minutes to hours; product prices → seconds to minutes; real-time scores → never (don't cache or use event-driven invalidation).

### Event-Driven Invalidation

When the source data changes, immediately delete or update the corresponding cache key. No stale window. The tradeoff: more code and more coupling. You must track which cache keys are affected by each type of write. In a microservices architecture, this often means publishing invalidation events to a message bus.

### Cache Stampede (Thundering Herd)

A popular key's TTL expires. All concurrent requests simultaneously get a cache miss and all simultaneously query the database. The database, which normally handles 100 queries per second, receives 10,000 simultaneous queries. It falls over.

**How to prevent it:**

- **Probabilistic early expiration**: Each process randomly decides to refresh slightly before the TTL expires. Smears the refresh across time.
- **Mutex / single-flight pattern**: When a cache miss occurs, only one request fetches from the database. All other concurrent requests for the same key wait. Prevents N simultaneous database queries for the same key.
- **Background refresh**: For known hot keys, refresh proactively before they expire. The key is always warm; no request ever experiences a miss.
- **TTL jitter**: Add randomness to TTL (`3600 ± rand(300)` seconds) so not all items expire simultaneously.

```java
// Caffeine local L1 cache for hot keys
LoadingCache<String, UserProfile> localCache = Caffeine.newBuilder()
    .maximumSize(1_000)
    .expireAfterWrite(5, TimeUnit.SECONDS)
    .build(key -> redisClient.getProfile(key));  // L1 miss → L2 Redis
```

---

## 5. Eviction Policies

A cache has finite memory. When it's full and a new item arrives, something must be evicted. The policy determines which item to remove — and the wrong choice can collapse your hit rate.

**Why the choice matters**: At a 95% hit rate, a 5% improvement to 96% halves the DB load. At 50M requests/day, that's 500K extra DB queries avoided daily.

**Eviction analogy**: Your desk has space for 10 folders. The eviction policy determines which folder you move back to the filing cabinet when a new one arrives.

### Baseline: FIFO and Random

**FIFO (First In, First Out)**: Evict the oldest inserted item. Ignores access patterns entirely. Poor hit rate on any workload with temporal locality.

**Random**: Evict a uniformly random item. Surprisingly competitive against FIFO, but both are dominated by recency/frequency-aware policies.

### LRU (Least Recently Used)

**Policy**: Evict the item that was accessed least recently.

**Rationale**: Temporal locality — recently accessed items are likely to be accessed again soon.

**Implementation**: HashMap + Doubly Linked List. The list maintains access order. `head` = most recently used, `tail` = least recently used.

```java
class LRUCache {
    int capacity;
    Map<Integer, Node> map;   // key → node
    Node head, tail;           // dummy sentinels

    // get: O(1) — lookup in map, move node to head
    // put: O(1) — insert at head, evict tail if over capacity
}
```

Java: `LinkedHashMap(capacity, 0.75f, true)` implements LRU with `removeEldestEntry()`.

**LRU performs poorly when:**
- **Scan/sweep attacks**: A full table scan reads millions of sequential records, each accessed once, evicting your entire working set. Called "cache pollution."
- **Frequency matters more than recency**: A rare but recently accessed item evicts a frequently used item.

### LFU (Least Frequently Used)

**Policy**: Evict the item with the lowest access count.

**Rationale**: Frequency locality — frequently accessed items are more valuable than recently accessed ones.

**Implementation**: O(1) with frequency buckets (naive heap is O(log n)).

```
freq=1: [key_a, key_b]
freq=2: [key_c]
freq=5: [key_d, key_e]
min_freq = 1
```

On eviction, remove any item from the `min_freq` bucket (LRU within the bucket for tie-breaking).

**LFU performs poorly when:**
- **Frequency aging**: A formerly popular item retains a high count and never gets evicted even though it's now irrelevant.
- **New item problem**: A brand-new hot item starts at frequency=1 and gets immediately evicted.

### ARC (Adaptive Replacement Cache)

Invented at IBM (2003). Used in ZFS, Oracle DB, IBM storage systems.

**Key insight**: Real workloads switch between frequency-dominant and recency-dominant phases. ARC adapts automatically by tracking **ghost entries** (metadata of recently evicted items — key only, no data).

**Structure**: 4 internal lists:
```
T1: recency cache   — items seen once recently
T2: frequency cache — items seen 2+ times recently
B1: ghost list for T1 (evicted T1 items, key only, no data)
B2: ghost list for T2 (evicted T2 items, key only, no data)
```

**Access logic**:
```
Cache hit in T1 or T2:  move to T2 (promote to frequency cache)

Cache miss:
  Hit in B1 (ghost): increase p (grow T1 target size); fetch data; insert into T2
  Hit in B2 (ghost): decrease p (grow T2 target size); fetch data; insert into T2
  Total miss:         insert into T1
```

**Why ARC beats LRU and LFU**: Ghost lists let ARC learn from mistakes — self-tuning with no manual configuration. Scan-resistant: a one-time sequential scan populates T1, but since B1 hits don't increase `p`, the scan doesn't pollute T2 (the frequency cache).

**Downside**: Patented by IBM. Linux kernel uses a custom variant. Redis and Memcached use approximated LRU instead.

### TinyLFU and Window TinyLFU (Caffeine)

**Caffeine** is the default Java in-process cache library (Spring Boot, successor to Guava Cache). Its eviction policy is **Window TinyLFU (W-TinyLFU)** — the state of the art for in-process caches.

**Problem**: Maintaining exact access counts for millions of keys requires O(n) memory. TinyLFU uses a **Count-Min Sketch** — a probabilistic data structure that estimates frequency with bounded error using O(1) space.

**Count-Min Sketch**:
```
4 hash functions, each maps key → bucket in a row:

Row 0: [ 0 | 3 | 0 | 7 | 2 | ... ]
Row 1: [ 0 | 0 | 5 | 2 | 0 | ... ]
Row 2: [ 4 | 0 | 0 | 1 | 3 | ... ]
Row 3: [ 0 | 2 | 0 | 6 | 0 | ... ]

Estimated frequency of key X = min(row[0][h0(X)], row[1][h1(X)], ...)
```
Can only overestimate (hash collisions inflate, never deflate). Periodic aging: every N accesses, all counters are halved — prevents stale high-frequency items from dominating (solves LFU's frequency aging problem).

**Window TinyLFU Architecture**:
```
┌─────────────────────────────────────────────────────┐
│  Window Cache (1% of capacity, pure LRU)            │
│  → new items go here first                          │
└────────────────────┬────────────────────────────────┘
                     │ eviction candidate
                     ▼
         TinyLFU Admission Filter
         (compare candidate frequency vs victim frequency)
              │              │
         admit              reject
              ▼
┌─────────────────────────────────────────────────────┐
│  Protected Cache (80% of capacity, LRU)             │
│  → items accessed 2+ times                         │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│  Probationary Cache (20% of capacity, LRU)          │
└─────────────────────────────────────────────────────┘
```

The window cache solves the "new item problem": a brand-new item starts at frequency=0 and needs a grace period before competing with established high-frequency items.

**Performance**: Benchmarks show W-TinyLFU achieving 10-30% higher hit rates than LRU on real-world traces (Wikipedia, Web search, DB traces). Near-optimal — within 5% of the theoretical optimal offline algorithm (Bélády's algorithm).

### SLRU (Segmented LRU)

Used by Memcached, Nginx proxy cache.

**Structure**: Two LRU segments — **probationary** and **protected**.
- New items enter probationary segment
- Items accessed again move to protected segment
- Eviction targets probationary segment first

**Why**: Scan resistance — a full scan pollutes probationary but not protected.

### Clock Algorithm (Second Chance)

Used by OS page cache (Linux), disk caches.

**Structure**: Circular buffer of pages + 1 reference bit per page. A "clock hand" sweeps.

```
[ Page A (bit=1) ] ← [ Page B (bit=0) ] ← [ Page C (bit=1) ] ← ...
                                ▲
                           clock hand
```
- On access: set reference bit = 1
- On eviction: if bit = 1, clear to 0 and advance. If bit = 0, evict.

O(1) operations, O(1) extra space — ideal for OS-level page replacement where overhead must be minimal.

### Eviction Policy Comparison

| Algorithm | Hit Rate | Scan Resist | New Item | Memory Overhead | Used In |
|---|---|---|---|---|---|
| LRU | Good | ❌ | ✅ | Low (linked list) | Redis, Memcached |
| LFU | Good for stable | ✅ | ❌ | Low-Medium | Custom, some DBs |
| ARC | Excellent | ✅ | ✅ | Medium (ghost lists) | ZFS, Oracle |
| W-TinyLFU | Near-optimal | ✅ | ✅ | Low (sketch) | Caffeine |
| SLRU | Good | ✅ | ✅ | Low | Memcached |
| Clock | Fair | ✅ | ✅ | Very Low | OS page cache |

### Miss Rate Curves and Cache Sizing

The **miss rate curve** (MRC) shows how miss rate decreases as cache size increases.

```
Miss Rate
  100% ─┐
        │\
        │ \
        │  \─────────────────────
   ~5%  │            "knee"
  ──────┴─────────────────────────
        0        Cache Size
```

The "knee" is where adding more cache gives diminishing returns. **Practical sizing**: profile production traffic for working set size, then size cache to be 2x-3x the working set to stay comfortably above the knee.

---

## 6. Redis as the Implementation

### Redis vs Memcached

- **Redis**: Rich structures (strings, hashes, sets, sorted sets, streams); persistence; replication; pub/sub; Lua scripts; used for cache, session, rate limit, leaderboards.
- **Memcached**: Simple key-value; multi-threaded; no persistence; pure cache.
- **Choose Redis**: If you need more than simple get/set — almost always Redis in production.

### Redis Eviction Policies

Redis supports 8 eviction policies (set via `maxmemory-policy`):

| Policy | Description |
|---|---|
| `noeviction` | Return error when full |
| `allkeys-lru` | Approximate LRU across all keys |
| `volatile-lru` | Approximate LRU, only keys with TTL |
| `allkeys-lfu` | Approximate LFU across all keys (Redis 4+) |
| `volatile-lfu` | Approximate LFU, only keys with TTL |
| `allkeys-random` | Random eviction |
| `volatile-random` | Random eviction, only keys with TTL |
| `volatile-ttl` | Evict keys with shortest remaining TTL |

**Redis approximates LRU** — it does not maintain a true LRU linked list (too much memory overhead). Instead, it samples `maxmemory-samples` keys (default: 5) and evicts the one with the oldest LRU clock timestamp.

For most production caches: `allkeys-lru` or `allkeys-lfu` depending on whether your access pattern is recency or frequency dominated.

### Cache Metrics

- **Hit rate** = hits / (hits + misses); target > 90% for performance benefit
- **Eviction rate**: rising eviction = cache too small or TTL too short
- **Miss penalty**: time spent on DB fetch per cache miss; bound by DB latency

**Hit rate is the north star metric**: Low hit rate = wrong keys cached, TTL too short, or access pattern too random. Profile your query patterns before adding a cache.

---

## 7. CDN as Distributed Cache Layer

A Content Delivery Network (CDN) is a geographically distributed set of caching servers (edge nodes or points of presence) that serve content from the location closest to the user. When a user in Mumbai requests a video, they get it from an edge server in Mumbai — not from your origin server in Virginia.

**How it works**: On the first request for a given piece of content, the edge node fetches it from the origin, caches it locally, and serves it. Every subsequent request from nearby users is served from the edge — the origin is not involved.

**The origin offload math**: If 1 million users per day request the same hero image, and your CDN has a 99% cache hit rate, your origin receives 10,000 image requests instead of 1,000,000. Origin bandwidth and compute drop by 99%. This is why Netflix and YouTube cannot exist without CDNs.

**CDN strategies**:
- **Pull CDN**: Edge fetches from origin on first miss; best for large asset catalogs.
- **Push CDN**: You upload to CDN ahead of time; best for small known asset sets.

**Cache-Control headers**: `max-age`, `stale-while-revalidate`, `no-cache`, `s-maxage`

**When to use a CDN**: Any time you have globally distributed users accessing shared static content. CDNs are most effective for static and semi-static content: images, videos, JavaScript bundles, CSS, font files, and API responses with reasonable TTLs.

→ Deep dive (edge caching, push vs pull, invalidation): [02-building-blocks/cdn.md](cdn.md)

---

## 8. Caching at Each System Tier

Caching is not a single thing that lives in one place. Every layer of a production system has its own cache. Understanding where caches exist helps you reason about where staleness can hide and where latency can be eliminated.

| Layer | Cache Type | What's Cached | Real Example |
|-------|-----------|---------------|--------------|
| **CPU** | L1/L2/L3 cache | Machine instructions, hot memory addresses | Automatically managed by hardware |
| **Browser** | HTTP cache | HTML, JS, CSS, images | `Cache-Control: max-age=31536000` on static assets |
| **CDN** | Edge cache | Static assets, API responses, video segments | CloudFront, Akamai, Fastly edge nodes |
| **Load balancer** | Response cache | Repeated identical HTTP responses | NGINX `proxy_cache`, HAProxy cache |
| **Application** | In-process cache | Frequently used objects, config, computed results | Guava Cache, Caffeine, Python `functools.lru_cache` |
| **Distributed cache** | External cache (Redis / Memcached) | User sessions, hot DB rows, computed aggregations | Redis storing user profiles, feed items, rate limit counters |
| **Database** | Query cache / buffer pool | Result sets, hot data pages, index pages | PostgreSQL shared_buffers, MySQL InnoDB buffer pool |

Cache misses fall through to the next layer. Staleness can accumulate across layers.

---

## 9. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Cache down | Fall back to DB; accept higher latency; optional stale cache from replica |
| Stampede (many requests on same miss) | Single-flighter or lock; TTL jitter; prewarm hot keys |
| Stale data | TTL; invalidate on write (delete or update cache); version in key |
| Memory full | Eviction policy (LRU/LFU); scale cache size or shard |
| Hot key | Replicate hot key to multiple cache nodes; local in-process L1 cache |

**Hot key problem**: A single cache key receives orders-of-magnitude more traffic than others. A single Redis node handling `user:profile:celebrity123` at 100k reads/second will bottleneck.

Solutions:
1. **Key replication**: Pre-shard the hot key across multiple Redis nodes: `user:profile:celebrity123:shard0`, `:shard1`, `:shard2`. App randomly picks a shard to read from.
2. **Local L1 cache**: Keep a tiny in-process Caffeine cache for the hottest keys with a 1–5s TTL. Reads never leave the application server for those keys.

**Cache down — graceful degradation**: If Redis is down, the kitchen doesn't stop — requests go directly to the DB every single time. Service continues, just slower. Cache failure should degrade performance, not cause total system failure.

---

## 10. Performance Considerations

- **Latency**: Sub-millisecond for cache hit; single-digit milliseconds for cache miss + DB query + backfill.
- **Throughput**: A single Redis node handles 100k–1M ops/second. A single DB node handles far less at comparable latency.
- **Hit rate**: A 90% hit rate means 90% of reads never touch the DB. A 50% hit rate barely helps — the DB still sees half your read load.

---

## 11. Implementation Patterns

- **Single cache**: One Redis instance; simple; single point of failure. Good for dev.
- **Replicated cache**: Primary + replicas; read from replicas for horizontal read scaling; failover to replica on primary loss.
- **Distributed cache (sharded)**: Redis Cluster; consistent hashing across nodes; see [05-hld-problems/03-hard/distributed-cache.md](../05-hld-problems/03-hard/distributed-cache.md).
- **Multi-level cache (L1/L2)**: In-process Caffeine (L1, ~µs, tiny) → Redis (L2, sub-ms, large) → DB. Useful for extreme hot key scenarios.

---

## 12. Trade-offs and When to Cache

| Aspect | Pros | Cons |
|--------|------|------|
| **Cache-aside** | App controls logic; cache failure → fallback to DB | Stale possible; cache stampede on miss |
| **Write-through** | Consistent reads | Higher write latency; cache pollution |
| **Write-behind** | Very fast writes | Data loss if cache dies before DB write |
| **In-memory** | Very low latency (sub-ms) | Cost; size limit; volatile unless persisted |

**When to use**: Read-heavy workload; latency-sensitive; can tolerate staleness or invalidate on write.

**When not to use**: Write-heavy with strong consistency (every write invalidates the cache immediately); or data doesn't have locality (random access pattern → low hit rate, cache wastes memory).

**Common mistakes**:
- Caching without TTL or invalidation — stale data lives forever
- Treating cache as source of truth — it's a performance layer, not a database
- No fallback when cache is down — always degrade gracefully to the DB
- Ignoring cache stampede on popular key expiry

---

## Quick Revision

- **Purpose**: Low latency and reduced DB load by keeping hot data in RAM.
- **When to cache**: High read:write ratio + expensive reads + staleness tolerance. Avoid for write-heavy workloads, freshness-critical data (stock prices, bank balances), or data unique per user.
- **Cache-aside**: App checks cache, loads DB on miss, fills cache. Invalidate on write. Most common, resilient, lazy.
- **Write-through**: Write DB + cache together — consistent, higher write latency.
- **Write-behind**: Write cache async to DB — fast writes, risk of data loss.
- **Eviction**: LRU (recency), LFU (frequency), ARC (adaptive), W-TinyLFU (near-optimal, Caffeine), TTL (time-based). Redis uses approximated LRU.
- **Stampede**: Single-flighter or TTL jitter to prevent mass simultaneous DB hits on same key expiry.
- **Hot key**: Shard the key or use an L1 in-process cache to avoid hammering one Redis node.
- **CDN**: Distributed edge caches serving static content from nearest user location. 99%+ origin offload realistic for popular static assets.
- **Tiers**: Caches exist at every layer — browser, CDN, load balancer, application, Redis, database buffer pool. Staleness can accumulate across layers.
- **Interview**: "We use Redis as a cache-aside layer with a 1-hour TTL for user profiles; on miss we hit the DB and backfill. We invalidate on update. If Redis is down we fall back to the DB and accept higher latency. For hot keys like celebrity profiles we add a 5-second local Caffeine cache per app node."

---

## See Also

- **Redis internals** (how Redis stores data, persistence, clustering): [04-advanced-topics/internals/redis-internals.md](../04-advanced-topics/internals/redis-internals.md)
- **CDN deep dive** (edge caching, push vs pull, invalidation, Cache-Control headers): [02-building-blocks/cdn.md](cdn.md)
- **HLD problems that deeply use caching**: [URL Shortener](../05-hld-problems/01-easy/url-shortener.md) (redirect hot-path), [Twitter News Feed](../05-hld-problems/02-medium/twitter-news-feed.md) (timeline cache), [Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md) (caching as the product)
- **LLD problem**: [06-lld/05-problems/10-design-lru-cache.md](../06-lld/05-problems/10-design-lru-cache.md) — implement LRU from scratch with doubly linked list + HashMap

---

## Interview Questions

### Conceptual

1. **"What is cache stampede and how do you prevent it?"** → When a popular key expires, many requests simultaneously miss and hammer the DB. Prevent with: mutex/single-flight (only one thread fetches, rest wait), probabilistic early expiration (PER — recompute slightly before TTL expires), or background refresh.

2. **"What happens when your cache goes down?"** → All traffic falls through to the DB — which may not handle the full load. Mitigate: circuit breaker to shed load, read replicas to absorb, staggered TTLs to prevent mass simultaneous expiry. Always design the DB to handle cache-cold traffic, even if slowly.

3. **"When would you NOT use a cache?"** → When data is write-heavy (cache constantly invalidated), unique per user with no sharing, strong freshness requirements (financial balances, inventory), or when the dataset fits in DB buffer pool.

4. **"What Redis data structure would you use for a leaderboard?"** → Sorted Set (`ZSET`): members with scores, O(log N) add/update, O(log N + K) range query. `ZADD leaderboard 1500 user:42` then `ZREVRANGE leaderboard 0 9 WITHSCORES` for top 10.

5. **"How does Redis Cluster work?"** → Data is split across 16,384 hash slots. Each primary node owns a slot range; replicas shadow the primary. Clients use a cluster-aware driver that routes to the correct node. On node failure, replica is promoted automatically.

6. **"What is the difference between Redis Pub/Sub and Redis Streams?"** → Pub/Sub: fire-and-forget, no persistence, messages lost if subscriber is offline. Streams: persistent log with consumer groups, replay, acknowledgement — like a lightweight Kafka. Use Streams when you need delivery guarantees or replay.

### Comparison / Trade-off

1. **"Write-through vs write-back vs cache-aside — when to use each?"** → Cache-aside: lazy loading, resilient, most common. Write-through: write DB + cache together — consistent but adds write latency. Write-back: write cache first, async flush to DB — fastest writes, risk of data loss. Use write-back only when you can tolerate data loss (e.g., analytics counters).

2. **"How do you invalidate cache entries across a distributed cache?"** → TTL (simple, stale window), event-driven invalidation (publish to pub/sub on write — all nodes delete the key), or versioned keys (change key on update — old key naturally expires).

3. **"LRU vs LFU eviction — when to use each?"** → LRU: evicts least recently accessed — good for temporal locality (recent = relevant). LFU: evicts least frequently accessed — better for stable hot sets where some items are always popular. LFU has higher overhead. Use LRU as default; LFU for workloads with a stable hot set.

4. **"How does a CDN decide where to cache content?"** → On first request to an edge PoP, the CDN fetches from origin and caches based on `Cache-Control`/`Expires` headers. Subsequent requests from nearby users are served locally. CDN uses Anycast routing to direct users to the nearest PoP.

### Scenario / Design

1. **"How do you handle cache warming after a cold start?"** → Pre-populate by: replaying recent read traffic against the DB on startup, running a background job that loads top-N keys by frequency from DB before going live, or using a warm standby that mirrors production cache. Without warming, a fresh node causes a thundering herd on the DB.

2. **"How do you implement distributed rate limiting with Redis?"** → Sliding window with a ZSET: add current timestamp as member, remove members older than window, count remaining. Or token bucket: store token count + last refill timestamp in a string, use `EVAL` (Lua) for atomicity. All app nodes share one Redis key per client — consistent rate limiting across instances.

3. **"Why does Caffeine use W-TinyLFU instead of LRU, and what problem does the window cache solve?"** → W-TinyLFU uses frequency estimation to make better eviction decisions — it admits an incoming item only if it's more valuable (higher estimated frequency) than the item it would evict. The window cache (1% of capacity) solves the "new item problem": a brand-new item has frequency=0 and would always lose to established items in a pure LFU. The window cache gives new items a brief grace period in pure LRU mode.

4. **"Describe an attack on an LRU cache and how to mitigate it."** → A sequential full-table scan reads millions of unique keys, each accessed exactly once. LRU moves each into the cache and evicts the previous hot item. After the scan, the entire cache is filled with cold data that will never be accessed again. Hit rate collapses. Mitigations: (1) use a scan-resistant policy (ARC, W-TinyLFU, SLRU); (2) route analytical queries to a separate cache or bypass the cache entirely.
