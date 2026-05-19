# Caching & CDN — Concepts and Decision Framework

> **The conceptual layer: why caching exists, when to use it, and how to think about it.**
> For implementation details (Redis config, Java code, eviction tuning): [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md)
> For CDN deep dive (origin offload math, edge caching, invalidation): [02-building-blocks/cdn.md](../02-building-blocks/cdn.md)

---

## File Mindmap

```
Caching & CDN
├── Why It Exists
│   ├── Problem → 100k req/hr all hitting DB for same user profile; 500k ms DB compute wasted
│   └── Forces → RAM ~100ns vs DB query ~1–20ms (10,000–200,000× difference); reads >> writes
├── Core Concepts
│   ├── Cache hit → data found in cache; served immediately; DB not touched
│   ├── Cache miss → data absent; must fetch from DB; populate cache for next request
│   ├── TTL (Time-to-Live) → auto-expiry; freshness vs staleness trade-off
│   └── Hit ratio → % of requests served from cache; target >90% for meaningful DB offload
├── Read Strategies
│   ├── Cache-aside (lazy loading) → app checks cache; on miss: load DB, populate cache
│   │   └── Pro: only hot data cached; Con: first request always slow (cold start)
│   ├── Read-through → cache layer fetches from DB on miss; app sees only cache
│   │   └── Pro: simpler app code; Con: first-request latency; less control
│   └── Refresh-ahead → pre-load cache before TTL expires; Pro: no miss latency; Con: may prefetch stale
├── Write Strategies
│   ├── Write-through → write DB + cache together; always consistent; doubles write latency
│   ├── Write-behind (write-back) → write cache first; flush to DB async; fast writes; risk data loss
│   └── Write-around → write DB only; skip cache; next read will miss; good for write-once data
├── Cache Invalidation
│   ├── TTL expiry → simple; always slightly stale; tunable
│   ├── Event-driven invalidation → on write, explicitly delete/update cache key; complex but fresh
│   └── Cache-aside + short TTL → most common hybrid; acceptable staleness window
├── CDN Summary
│   ├── Pull CDN → edge fetches from origin on first miss; best for large asset catalogs
│   ├── Push CDN → you upload to CDN ahead of time; best for small known asset sets
│   └── Cache-Control headers → max-age, stale-while-revalidate, no-cache, s-maxage
├── Failure Modes
│   ├── Cache stampede (thundering herd) → TTL expires; all instances miss simultaneously → DB crushed
│   │   └── Mitigations: mutex/lock on miss; probabilistic early expiry; background refresh
│   └── Stale reads → user writes data; reads old value from cache → use write-through or invalidation
├── Real-World Usage
│   ├── Netflix → EVCache (Memcached-based); caches catalog; 99% hit rate; global replication
│   └── Facebook → Memcached at massive scale; TAO for social graph with explicit invalidation
└── Interview Angles
    ├── "What is cache invalidation and why is it hard?" → distributed systems; clocks differ; order of writes
    ├── "How do you prevent thundering herd?" → mutex on first miss; probabilistic early refresh
    └── Follow-up: "Write-through vs write-behind — when to use each?" → consistency vs throughput trade-off
```

---

## Table of Contents

1. [Why Caching Exists](#why-caching-exists)
2. [The Caching Decision Framework](#the-caching-decision-framework)
3. [Caching Strategies — Summary](#caching-strategies--summary)
4. [Cache Invalidation — The Hard Part](#cache-invalidation--the-hard-part)
5. [CDN Summary](#cdn-summary)
6. [Caching at Each System Tier](#caching-at-each-system-tier)
7. [Quick Revision](#quick-revision)

---

## Why Caching Exists

**Question**: Your homepage loads user profile data on every page render. The profile changes once a week on average. At 100,000 page views per hour, you run 100,000 identical `SELECT * FROM users WHERE id = ?` queries per hour for the same set of popular users. Each query takes 5ms. That's 500,000ms of database compute per hour — just for reads that return the same result 99.9% of the time. What's the minimal change that eliminates most of that work?

**Physical constraint**: A relational database query takes 1–20ms (network + query parsing + disk I/O + result serialization). RAM access takes ~100ns. The same data retrieved from RAM is 10,000–200,000× faster than from a database over the network. The only reason to go to the database is if you don't have the data already, or if it may have changed since you last fetched it. The question is always: how often does this data change relative to how often it's read?

**Minimal solution**: Store the query result in a local hash map in your application process, keyed by user ID. On a hit, return from RAM in microseconds. On a miss, query the DB and populate the map. Invalidate the entry when the user updates their profile. This works until: multiple app server instances each have their own map (cache is not shared), and the map grows unboundedly until the process runs out of memory.

**Production generalization**: A shared distributed cache (Redis) solves both problems — all app instances read from one cache, and you set TTLs to bound memory growth. The caching decision framework, invalidation strategies, and failure modes derive from this same fundamental tradeoff: the faster store has a copy, the copy may become stale, and you must decide how to keep it fresh.

The fundamental insight behind every cache ever built is simple: **retrieving the same data repeatedly from the original source is wasteful when that data hasn't changed.**

Think about how your brain works. You don't re-read the dictionary every time you want to use a word — you hold a working vocabulary in memory. When someone asks for your phone number, you don't look it up in a contacts app; it's in working memory. When you need an obscure fact, you might actually open a book. That three-tier model — working memory (fast, small), long-term memory (slower, larger), reference books (slowest, complete) — maps almost exactly to CPU registers / RAM / disk in a computer, and to in-process cache / Redis / database in a distributed system.

**The locality of reference principle** is what makes caching work in practice. Two forms:

- **Temporal locality**: data you accessed recently is likely to be accessed again soon. You check the same five websites every morning. You re-read the same configuration file on every request.
- **Spatial locality**: data near what you just accessed is likely to be needed next. When you load a user's profile, you probably also need their settings. When you scan one row in a database page, you will likely scan the next row too.

Real systems bear this out at scale. On YouTube, roughly 20% of videos account for more than 80% of total watch time. On Twitter, a small fraction of accounts generate the majority of timeline reads. On any e-commerce site, a handful of product pages get thousands of visits per minute while the long tail gets one visit per month. Cache the hot 20%, serve the cold 80% directly from the database on the rare occasions someone actually asks for them.

**The speed gap that makes caching matter:**

| Layer | Typical Latency |
|-------|----------------|
| CPU L1 cache | ~0.5 ns |
| RAM (in-process cache) | ~100 ns |
| Redis (network, same datacenter) | ~0.5 ms |
| PostgreSQL (simple query, warm) | ~5–20 ms |
| PostgreSQL (complex join) | ~100–500 ms |
| Cross-region database call | ~100–300 ms |

A cache hit at the Redis layer is 10–100× faster than a database query. At the in-process level, the gap is even larger. The question is never "is caching faster?" — it always is. The question is whether the freshness tradeoff is acceptable.

---

## The Caching Decision Framework

### When to Add a Cache

Add a cache when **all three** of the following are true:

1. **Read:write ratio is high (roughly > 5:1).** If data is written once and read a thousand times, caching that data pays for itself immediately. If data is written constantly, the cache is perpetually stale or perpetually invalidated — neither is useful.

2. **Reads are expensive.** Expensive means: complex multi-table database joins, external API calls, computed aggregations, or any operation that takes more than a few milliseconds. If your read is a primary-key lookup that returns in 1 ms, caching it may not be worth the complexity.

3. **Staleness is tolerable.** Your system can serve data that is seconds, minutes, or hours old without causing incorrect behavior or harm. The exact tolerance depends on your domain.

**Good fit — Twitter user profile:**
- Read:write ratio is extremely high. Millions read a given profile; the user updates it rarely.
- The read requires joining multiple tables (user, follower counts, bio, avatar URL).
- A 15-minute-old profile is completely fine. Nobody notices or cares.
- Result: cache with 15-minute TTL, invalidate on profile update.

### When NOT to Add a Cache

**Do not add a cache** when any of the following apply:

- **Write-heavy workload**: If data changes on every request (event logs, sensor readings, real-time counters), your cache hit rate will be near zero. You add complexity with no benefit.

- **Freshness is critical**: Bank account balance, stock price, inventory count, seat availability on a flight. Serving a stale value is not just inaccurate — it can be harmful (overselling inventory, showing wrong prices). Event-driven invalidation can help here, but it adds significant complexity. Ask whether the cache is actually buying you anything.

- **Every user gets unique data**: If your query is `SELECT * FROM recommendations WHERE user_id = ?` and the result is different for every user, cache hit rate is low and you're burning memory to store data that will never be reused.

- **Data volume is enormous and access is uniform**: If you have 500 million products and each gets exactly one visit per year, there is no hot working set to cache. Your cache will thrash constantly and help no one.

**Poor fit — bank account balance:**
- Written on every transaction.
- Read:write ratio is low during active banking hours.
- Serving a stale balance could cause overdraft decisions based on wrong data.
- Freshness is critical. TTL-based caching is dangerous here.

---

## Caching Strategies — Summary

Each strategy answers the same question differently: who is responsible for loading data into the cache, and when does the write reach the database?

**Cache-Aside (Lazy Loading)**: The application checks the cache first. On a miss, the application fetches from the database and populates the cache itself. The cache only ever contains data that has actually been requested. Think of it as a personal notepad: you only write down things you had to look up. The notepad stays relevant because you never carry information you haven't needed. Use this for general-purpose read-heavy data where cache misses on first access are acceptable.

→ Implementation details and code: [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md)

**Read-Through**: Same flow as cache-aside, but the cache library handles the database fetch automatically on a miss — the application only ever talks to the cache interface. Think of a "smart wallet" that goes to the ATM on your behalf when it detects it's empty. The application code is cleaner; the tradeoff is that your caching layer must understand your database schema. Use this when you want to centralize loading logic and ensure all code paths populate the cache consistently.

→ Implementation details and code: [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md)

**Write-Through**: Every write goes to both the cache and the database synchronously before the operation returns. Think of a bank teller who writes your deposit in both the paper ledger and the computer system before handing you your receipt — both records are in sync the moment you walk out the door. Higher write latency (two sequential writes), but reads from cache are always fresh. Use this when data is written infrequently but read many times, and you need strong consistency between cache and database.

→ Implementation details and code: [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md)

**Write-Behind (Write-Back)**: Writes go to the cache immediately and return. The database is updated asynchronously in the background, often in batches. Think of a notebook you scribble transactions in all day, then upload to the accounting system at end of day — your workflow is fast, but if you lose the notebook before the upload, those entries are gone. Very low write latency; use this for high-throughput write workloads (analytics counters, ad impression logging) where some data loss is acceptable. Never use this for financial records or user-generated content.

→ Implementation details and code: [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md)

---

## Cache Invalidation — The Hard Part

> "There are only two hard things in computer science: cache invalidation and naming things." — Phil Karlton

The quote is funny because it's true. Why is invalidation hard? Because a cache is a copy of data that lives somewhere else. The moment the original changes, the copy is wrong. And in a distributed system with multiple services writing to the same underlying data, any of them could change data that another service has cached — often without knowing the cache exists.

### TTL-Based Invalidation

Set a time limit on every cache entry. After the TTL expires, the next reader fetches fresh data from the database and re-populates the cache. Simple, automatic, no extra code on writes.

The tradeoff: you may serve stale data for the entire TTL window. A one-hour TTL means a user's profile change might not be visible to some readers for up to an hour. For most profile data, that's fine. For a price update on a flash sale item, it is not.

Choose TTL based on how much staleness your domain can tolerate: user profiles → minutes to hours; product prices → seconds to minutes; real-time scores → never (don't cache or use event-driven invalidation).

### Event-Driven Invalidation

When the source data changes, immediately delete or update the corresponding cache key. The next reader gets a cache miss, fetches fresh data, and re-populates. No stale window.

The tradeoff: more code and more coupling. You must track which cache keys are affected by each type of write. If a user update affects keys in five different services, all five must be invalidated. Missing one means stale data survives. In a microservices architecture, this often means publishing invalidation events to a message bus and having each service consume and act on them.

### Cache Stampede (Thundering Herd)

This is one of the most dangerous failure modes in caching. Imagine 10,000 users are watching the same live sports match. Your cache holds the current score with a 60-second TTL. At exactly second 60, the TTL expires. All 10,000 users' next requests hit the cache simultaneously — and all 10,000 get a miss. All 10,000 simultaneously query the database for the same score. Your database, which normally handles 100 queries per second, receives 10,000 simultaneous queries in under one second. It falls over.

The name "thundering herd" describes exactly this: a silent cache that suddenly expires, followed by a thunderous wave of requests hitting the database at once.

**How to prevent it:**

- **Probabilistic early expiration**: Instead of expiring all at once at TTL=0, each process randomly decides to refresh slightly before the TTL expires. As the TTL approaches zero, the probability of refreshing increases. This smears the refresh across time so no single moment triggers a flood.

- **Mutex / single-flight pattern**: When a cache miss occurs, only one request is allowed to fetch from the database. All other concurrent requests for the same key wait for that single fetch to complete and then read from the refreshed cache. Prevents N simultaneous database queries for the same key, but adds a small wait for the requestors that lost the mutex race.

- **Background refresh**: For known hot keys, refresh proactively in a background process before they expire. The key is always warm; no request ever experiences a miss.

---

## CDN Summary

A Content Delivery Network (CDN) is a geographically distributed set of caching servers (called edge nodes or points of presence) that serve content from the location closest to the user. When a user in Mumbai requests a video, they get it from an edge server in Mumbai — not from your origin server in Virginia. The round-trip is 10 ms instead of 200 ms.

**How it works**: On the first request for a given piece of content, the edge node fetches it from the origin, caches it locally, and serves it. Every subsequent request for that content from nearby users is served from the edge — the origin is not involved. CDNs are most effective for static and semi-static content: images, videos, JavaScript bundles, CSS, font files, and API responses with reasonable TTLs.

**The origin offload math**: If you have 1 million users per day requesting the same hero image, and your CDN has a 99% cache hit rate, your origin receives 10,000 image requests instead of 1,000,000. Origin bandwidth and compute drop by 99%. This is why companies like Netflix and YouTube would be economically impossible without CDNs — they cannot possibly serve all their video traffic from a handful of origin data centers.

**When to use a CDN**: Any time you have globally distributed users accessing shared static content. If all your users are in one city and your servers are in the same city, a CDN adds complexity without meaningful latency benefit.

→ Deep dive (edge caching, push vs pull, invalidation, Cache-Control headers): [02-building-blocks/cdn.md](../02-building-blocks/cdn.md)

---

## Caching at Each System Tier

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

Each layer has different characteristics: the browser cache is private to one user; the CDN cache is shared across all users; Redis sits between your application and your database; the database buffer pool is managed automatically by the database engine. Latency increases and capacity grows as you move down the table. Cache misses fall through to the next layer.

---

## Quick Revision

**Why cache?** Data access follows the 80/20 rule — a small hot working set accounts for most reads. Serving that working set from memory (sub-millisecond) instead of disk (tens to hundreds of milliseconds) reduces latency and database load dramatically.

**When to cache?** High read:write ratio + expensive reads + staleness tolerance. Avoid for write-heavy workloads, freshness-critical data (stock prices, bank balances), or data that is unique per user with no sharing.

**Strategies:**
- Cache-aside: app loads on miss — most common, resilient, lazy
- Read-through: cache loads on miss — cleaner code, tighter coupling
- Write-through: write DB + cache synchronously — consistent, higher write latency
- Write-behind: write cache async to DB — fast writes, risk of data loss

**Invalidation:**
- TTL: simple, automatic, possible stale window
- Event-driven: immediate freshness, more code and coupling
- Cache stampede: mass simultaneous misses → DB overload. Prevent with probabilistic early expiration or mutex/single-flight.

**CDN:** Distributed edge caches that serve static content from the location nearest the user. 99%+ origin offload is realistic for popular static assets. Use versioned URLs for safe aggressive caching.

**Tiers:** Caches exist at every layer — browser, CDN, load balancer, application, Redis, database buffer pool. A request may pass through several cache layers before reaching the origin. Staleness can accumulate across layers.

**Common mistakes:**
- Caching without TTL or invalidation — stale data lives forever
- Treating cache as source of truth — it's a performance layer, not a database
- No fallback when cache is down — always degrade gracefully to the DB
- Ignoring cache stampede on popular key expiry

---

## Interview Questions Asked

### Conceptual
1. **"What is cache stampede and how do you prevent it?"** → When a popular key expires, many requests simultaneously miss and hammer the DB. Prevent with: mutex/single-flight (only one thread fetches, rest wait), probabilistic early expiration (PER — recompute slightly before TTL expires), or background refresh. Testing: production cache failure modes.
2. **"What happens when your cache goes down?"** → All traffic falls through to the DB — which may not handle the full load. Mitigate: circuit breaker to shed load, read replicas to absorb, staggered TTLs to prevent mass simultaneous expiry. Always design the DB to handle cache-cold traffic, even if slowly. Testing: resilience thinking.
3. **"When would you NOT use a cache?"** → When data is write-heavy (cache constantly invalidated), uniqueness per user with no sharing, strong freshness requirements (financial balances, inventory), or when the dataset fits in DB buffer pool. Testing: knowing when caching adds complexity without benefit.

### Comparison / Trade-off
1. **"Write-through vs write-back vs cache-aside — when to use each?"** → Cache-aside: lazy loading, resilient, most common. Write-through: write DB + cache together — consistent but adds write latency. Write-back (write-behind): write cache first, async flush to DB — fastest writes, risk of data loss on cache crash. Use write-back only when you can tolerate data loss (e.g., analytics counters).
2. **"How do you invalidate cache entries across a distributed cache?"** → TTL (simple, stale window), event-driven invalidation (publish to pub/sub on write — all nodes delete the key), or versioned keys (change key on update — old key naturally expires). Event-driven is most consistent but adds coupling.

### Scenario / Design
1. **"How does a CDN decide where to cache content?"** → On first request to an edge PoP, the CDN fetches from origin and caches based on `Cache-Control`/`Expires` headers. Subsequent requests from nearby users are served locally. CDN uses Anycast routing to direct users to the nearest PoP. Popular content propagates to more edges over time. Testing: understanding CDN as a tiered cache, not magic.
2. **"What is a thundering herd and how do you prevent it?"** → Same as stampede at larger scale: a cache flush or node restart causes all clients to simultaneously query the backend. Prevent with: jittered TTLs (randomize expiry within a window), request coalescing (only one upstream request per key), and pre-warming the cache before bringing a node live.
