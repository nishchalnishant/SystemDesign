# Cache Systems Every Developer Should Know

> **Source**: [Cache Systems Every Developer Should Know](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf) — Videos #6, #57
> Also see: [Caching Pitfalls Every Developer Should Know](https://www.youtube.com/playlist?list=PLCRMIe5FDPsd0gVs500xeOewfySTsmEjf)

---

## Cache Layers

```
L1: Browser/Client Cache
L2: CDN Cache (edge)
L3: API Gateway / Reverse Proxy Cache
L4: Application-Level Cache (in-process)
L5: Distributed Cache (Redis/Memcached)
L6: Database Query Cache
L7: CPU Cache (L1/L2/L3)
```

---

## Caching Strategies

### Read Strategies
| Strategy | How It Works | Best For |
|---|---|---|
| **Cache-Aside** | App checks cache, on miss reads DB and populates cache | General purpose, most common |
| **Read-Through** | Cache automatically loads from DB on miss | Simpler application code |
| **Refresh-Ahead** | Cache proactively refreshes before expiry | Hot data with predictable access |

### Write Strategies
| Strategy | How It Works | Best For |
|---|---|---|
| **Write-Through** | Write to cache AND DB synchronously | Strong consistency |
| **Write-Behind** | Write to cache, async write to DB | High write throughput |
| **Write-Around** | Write to DB only, cache populated on read | Write-heavy with rare re-reads |

---

## Eviction Policies

| Policy | Description |
|---|---|
| **LRU** | Evict Least Recently Used — most popular |
| **LFU** | Evict Least Frequently Used |
| **FIFO** | Evict oldest entry |
| **TTL** | Evict after time expires |
| **Random** | Evict random entry |

---

## Caching Pitfalls

### 1. Cache Stampede / Thundering Herd
- Multiple requests hit DB simultaneously when cache expires
- **Fix**: Locking (only one thread refills), stale-while-revalidate, jittered TTL

### 2. Cache Penetration
- Queries for data that **doesn't exist** always hit DB
- **Fix**: Cache null results with short TTL, Bloom filter

### 3. Cache Avalanche
- Many cache entries expire at the same time
- **Fix**: Randomized TTL values (add jitter)

### 4. Inconsistency
- Cache and DB have different data
- **Fix**: Shorter TTL, event-driven invalidation, write-through

### 5. Hot Key Problem
- One cache key gets disproportionate traffic
- **Fix**: Key replication across multiple cache nodes, local caching

---

## Redis vs Memcached

| Feature | Redis | Memcached |
|---|---|---|
| Data Structures | Rich (lists, sets, sorted sets, hashes) | Key-value only |
| Persistence | RDB + AOF | None |
| Replication | Built-in | External |
| Clustering | Redis Cluster | Client-side |
| Pub/Sub | Yes | No |
| Lua Scripting | Yes | No |
| Memory Efficiency | Good | Better for simple k/v |

---

## Top 5 Redis Use Cases

1. **Session Store**: Store user sessions for web apps
2. **Cache**: Application-level caching for DB queries
3. **Rate Limiter**: Sliding window counters
4. **Leaderboard**: Sorted sets for ranking
5. **Pub/Sub**: Real-time messaging between services

---

## Why is Single-Threaded Redis So Fast?

1. **In-memory operations**: No disk I/O for reads
2. **Single-threaded**: No lock contention, no context switching
3. **I/O Multiplexing**: Uses epoll/kqueue for handling many connections
4. **Efficient data structures**: Optimized C implementations
5. **Simple protocol**: RESP (Redis Serialization Protocol)
