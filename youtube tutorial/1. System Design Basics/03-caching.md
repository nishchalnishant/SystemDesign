# Caching in System Design Interviews

> **Source**: [Caching in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=3)

---

## What is Caching?

Caching is storing copies of frequently accessed data in a **faster storage layer** to reduce latency and load on the primary data source.

---

## Why Cache?

- **Reduce latency**: Memory access (~100ns) vs Disk (~10ms) = 100,000x faster
- **Reduce database load**: Fewer queries hitting the database
- **Improve throughput**: Serve more requests with same resources
- **Cost reduction**: Fewer database replicas needed

---

## Cache Layers in a System

```
Client → CDN → Application Cache → Database Cache → Database
         ↓           ↓                   ↓
      Browser     Redis/Memcached    Query Cache
      Cache       In-memory cache    Buffer Pool
```

### 1. Client-Side Cache
- **Browser cache**: HTTP cache headers (Cache-Control, ETag, Expires)
- **Mobile app cache**: Local storage, SQLite

### 2. CDN (Content Delivery Network)
- Caches static assets (images, CSS, JS) at edge locations
- Reduces latency for geographically distributed users
- Examples: CloudFront, Akamai, Fastly

### 3. Application-Level Cache
- **In-process cache**: Within the application memory (e.g., Guava Cache, Caffeine)
- **Distributed cache**: Shared across multiple servers (e.g., Redis, Memcached)

### 4. Database Cache
- **Query cache**: Database caches results of frequent queries
- **Buffer pool**: Database caches pages in memory

---

## Cache Strategies (Read)

### 1. Cache-Aside (Lazy Loading)
```
Read: App checks cache → miss → read from DB → write to cache → return
```
- **Most common pattern**
- Application manages the cache explicitly
- Cache is populated on-demand (lazy)
- **Pros**: Only requested data is cached; cache failure doesn't break the system
- **Cons**: Cache miss = 3 trips (cache + DB + write to cache); stale data possible

### 2. Read-Through
```
Read: App reads from cache → cache reads from DB on miss → returns data
```
- Cache sits between app and DB
- Cache library/provider handles DB reads
- **Pros**: Simpler app code; cache always consistent with pattern
- **Cons**: Data model must match between cache and DB

### 3. Refresh-Ahead
```
Cache proactively refreshes entries before they expire
```
- Reduces latency on frequently accessed items
- Requires prediction of which items will be accessed
- **Pros**: Low latency for hot data
- **Cons**: Wasted resources if predictions are wrong

---

## Cache Strategies (Write)

### 1. Write-Through
```
Write: App writes to cache → cache writes to DB → return
```
- Every write goes through cache to DB synchronously
- **Pros**: Cache always consistent with DB
- **Cons**: Higher write latency (two writes); unused data may fill cache

### 2. Write-Behind (Write-Back)
```
Write: App writes to cache → returns immediately → cache async writes to DB
```
- Cache buffers writes and flushes to DB asynchronously
- **Pros**: Low write latency; can batch DB writes
- **Cons**: Risk of data loss if cache crashes before flush

### 3. Write-Around
```
Write: App writes directly to DB → cache NOT updated
```
- Cache only populated on reads (cache-aside for reads)
- **Pros**: Cache not flooded with write data
- **Cons**: Cache miss on recently written data

---

## Cache Eviction Policies

| Policy | Description | Use Case |
|---|---|---|
| **LRU** (Least Recently Used) | Evict the least recently accessed item | General purpose, most common |
| **LFU** (Least Frequently Used) | Evict the least frequently accessed item | When access frequency matters |
| **FIFO** (First In, First Out) | Evict the oldest item | Simple, predictable |
| **TTL** (Time to Live) | Evict after a fixed time period | Data with known staleness tolerance |
| **Random** | Evict a random item | Simple, low overhead |

### Best Practice
- Use **LRU + TTL** combination
- Set TTL based on how stale the data can be
- LRU handles memory pressure

---

## Cache Invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." — Phil Karlton

### Strategies
1. **TTL-based**: Set expiration time; data auto-expires
2. **Event-driven**: Invalidate on write/update events
3. **Versioning**: Include version in cache key; new version = cache miss
4. **Active invalidation**: Explicitly delete/update cache entry on data change

### Common Problems
- **Stale data**: Cache serves outdated data
- **Cache stampede**: Multiple requests hit DB simultaneously when cache expires
- **Thundering herd**: Many concurrent cache misses for same key

### Solutions for Cache Stampede
- **Locking**: Only one request fetches from DB; others wait
- **Early expiration**: Refresh before actual expiry (jitter)
- **Stale-while-revalidate**: Serve stale data while refreshing in background

---

## Distributed Cache Considerations

### Redis vs Memcached

| Feature | Redis | Memcached |
|---|---|---|
| **Data Structures** | Rich (strings, lists, sets, sorted sets, hashes) | Simple key-value only |
| **Persistence** | RDB snapshots + AOF | No persistence |
| **Replication** | Built-in primary-replica | No native replication |
| **Cluster Mode** | Redis Cluster (sharding) | Client-side sharding |
| **Pub/Sub** | Built-in | Not supported |
| **Memory Efficiency** | Good | Better for simple k/v |

### Cache Consistency in Distributed Systems
- **Read-your-writes consistency**: After writing, subsequent reads see the write
- **Eventual consistency**: Cache will eventually be consistent (acceptable for most use cases)
- Use **cache-aside + TTL** for eventual consistency
- Use **write-through** for stronger consistency

---

## Cache Design Patterns

### 1. Cache Key Design
```
{entity_type}:{entity_id}:{field}
user:12345:profile
post:67890:comments:page:1
```
- Use **namespacing** to avoid key collisions
- Include **version** for schema changes: `v2:user:12345:profile`

### 2. Cache Warming
- Pre-populate cache on startup or deployment
- Prevents cold-start performance degradation
- Use for **predictable hot data**

### 3. Multi-Level Caching
```
L1: In-process (fastest, smallest)
L2: Distributed (Redis - fast, larger)
L3: CDN (edge locations)
```

---

## Interview Tips

1. **Always consider caching** when discussing read-heavy systems
2. Mention the **cache strategy** (cache-aside is the safe default)
3. Discuss **eviction policies** and **TTL values**
4. Address **cache invalidation** — this is the hard part
5. Mention **cache stampede** and how to prevent it
6. Discuss **consistency trade-offs** between cache and DB
7. Know when NOT to cache: write-heavy, highly dynamic data, small datasets
