> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Redis internals — how a single-threaded in-memory store delivers microsecond latency while supporting 6 data structures, persistence, and clustering.
>
> **Key topics:**
> - Single-threaded event loop: every command is atomic — no locks needed; I/O multiplexing (epoll) handles thousands of connections
> - Data structures: String (SDS), Hash (ziplist → hashtable), List (quicklist), Set (intset → hashtable), Sorted Set (ziplist → skiplist + hashtable), HyperLogLog, Geospatial
> - Encoding optimization: small collections use compact encodings (ziplist/intset) → automatically promoted on size threshold
> - Persistence: RDB (periodic snapshot, smaller files, faster restart) vs AOF (append-only log, up to 1-sec durability, larger files) vs Hybrid
> - Eviction policies: noeviction, allkeys-lru, allkeys-lfu, volatile-lru, volatile-ttl — choose based on cache vs durable store usage
> - Replication: async primary-replica; Sentinel for auto-failover; Cluster mode for horizontal sharding (16384 hash slots)
> - Pipelining and Lua: batch commands with PIPELINE; Lua scripts for multi-command atomicity without MULTI/EXEC overhead
>
> **Key takeaway:** Redis is single-threaded by design — the event loop + in-memory data model is why it's 100× faster than PostgreSQL for point lookups; use Cluster mode only when data exceeds single-node RAM.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Redis Internals

> An in-memory data structure store with sub-millisecond latency, atomic single-command execution, and rich data types beyond simple strings.

---

## 1. Why Redis Exists

**Question**: A web app's database handles 100K requests/sec. 95% of queries are point lookups on the same handful of hot keys (user sessions, product info, rate-limit counters). Each round trip to the database takes 5-20 ms. How do you cut response time to <1 ms?

**Physical constraint**: RAM access is ~100 ns. SSD is ~100 µs (1000× slower). Spinning disk is ~10 ms (100,000× slower). A well-tuned database still pays 1-5 ms of round-trip, lock acquisition, and query parsing. For 100K lookups/sec, the gap is enormous.

**Minimal solution**: Keep hot data in RAM. Skip the query parser. Use simple data structures optimized for the access pattern. Single-threaded command processing means no lock overhead per operation.

**Production generalization**: Redis (REmote DIctionary Server) is the standard in-memory data structure store. It supports strings, hashes, lists, sets, sorted sets, streams, HyperLogLog, and geospatial indexes. Single-threaded for command processing (Redis 6+ added I/O threads for network). Persistence is optional (RDB snapshots, AOF log, or both).

---

## 2. Core Data Structures

| Type | Internal encoding (small → large) | Best use case | O-complexity |
|------|----------------------------------|---------------|--------------|
| **String** | int / embstr / raw (SDS) | Cache, counter, idempotency key, lock | O(1) |
| **Hash** | listpack (was ziplist) → hashtable | Object storage (user profile, config) | O(1) per field |
| **List** | listpack → quicklist (linked listpack of listpacks) | Queue, recent-items feed | O(1) push/pop |
| **Set** | intset (small integers) → hashtable | Unique visitors, tags, membership | O(1) add/check |
| **Sorted Set** | listpack → skiplist + hashtable | Leaderboard, sliding-window rate limit, time series | O(log N) |
| **Stream** | radix tree + listpack | Durable message log with consumer groups | O(1) append, O(N) read |
| **HyperLogLog** | sparse (12 KB) / dense (12 KB) | Cardinality estimation (unique visitors at scale) | O(1) add, ~0.81% error |
| **Geospatial** | sorted set (with geohash as score) | "Find within 5 km" queries | O(log N + M) |

**Encoding optimization**: small collections use memory-efficient encodings (listpack, intset) stored contiguously. When a collection crosses the threshold (`hash-max-listpack-entries`, etc.), it's transparently promoted to a hashtable. Always enable — saves memory for small collections.

### Strings

```java
SET user:123:name "Alice"
GET user:123:name
INCR user:123:login_count
EXPIRE user:123:session 3600
SETNX lock:order:42 "owner-id" EX 30      // distributed lock primitive
```

`INCR` is atomic — perfect for rate-limit counters and unique-ID generation.

### Hashes

```java
HSET user:123 name "Alice" age 30 email "alice@example.com"
HGET user:123 name
HINCRBY user:123 login_count 1
HMGET user:123 name email                  // multi-get in one RTT
```

Update individual fields without read-modify-write of the whole object. Good for object storage.

### Lists

```java
LPUSH recent:user:123 "page-home"
LPUSH recent:user:123 "page-cart"
LRANGE recent:user:123 0 9                 // last 10 items
LTRIM recent:user:123 0 99                 // cap at 100 items
```

Doubly-linked list of listpacks. O(1) push/pop from either end. Cap with `LTRIM` to bound memory (recent-activity feed pattern).

### Sets

```java
SADD unique:visitors:2024-05-12 "user:123"
SISMEMBER unique:visitors:2024-05-12 "user:123"   // 1
SCARD unique:visitors:2024-05-12                  // count
SINTER tags:python tags:redis                      // intersection
```

Unique-string collection. O(1) `SADD`/`SISMEMBER`. Use for membership tests and set algebra (`SINTER`, `SUNION`, `SDIFF`).

### Sorted Sets

Every member has a numeric score. Sorted by score. Internally a skip list + hash map (for O(1) score lookup by member).

```java
ZADD leaderboard 9500 "alice"
ZADD leaderboard 8200 "bob" 9800 "carol"
ZREVRANGE leaderboard 0 9 WITHSCORES        // top 10
ZREVRANK leaderboard "alice"               // rank (0-indexed)
ZINCRBY leaderboard 300 "alice"            // score += 300
```

**Sliding-window rate limiter**:

```java
ZADD rate:user:123 1715510400 "req:uuid-1"
ZREMRANGEBYSCORE rate:user:123 0 (now - 60_000)
int count = ZCARD rate:user:123;
if (count > LIMIT) throw new RateLimitException();
```

**Why skiplist?** O(log N) insert + range query. Compare to balanced BST: simpler to implement concurrent-friendly, no rotations, supports range queries by rank. Used as the secondary index inside Sorted Set.

---

## 3. Single-Threaded Event Loop

Redis processes commands on a **single thread**. This is the central design choice.

### Why Single-Threaded Works

- **No locks needed** per command — atomic by construction
- **No context switches** between commands
- **In-memory data** access is ~100 ns; CPU is rarely the bottleneck
- **Network I/O** is the actual bottleneck; Redis 6+ added I/O threads for `read`/`write` syscall offload

### What This Means for Users

- **Avoid blocking commands**: `KEYS *`, `SMEMBERS` on a huge set, large `LRANGE`, `SORT` without `LIMIT`. They freeze the server for their duration.
- **Use `SCAN` instead of `KEYS`**: cursor-based, non-blocking iteration.
- **Per-command atomicity is free**: a `HINCRBY` doesn't need a transaction.

### I/O Multiplexing

Linux `epoll` (or `kqueue` on BSD, `IOCP` on Windows). One thread handles thousands of connections, dispatching commands as they arrive. This is how Redis achieves 100K+ ops/sec on a single core.

---

## 4. Persistence

| Aspect | RDB | AOF |
|--------|-----|-----|
| **What** | Point-in-time binary snapshot | Append-only log of every write command |
| **Trigger** | Periodic (`save` rules) or explicit `BGSAVE` | Every command (with `fsync` policy) |
| **Durability** | Minutes of data loss possible | Up to 1 s with `appendfsync everysec`; no loss with `always` |
| **File size** | Compact | Larger (grows until rewrite) |
| **Restart speed** | Fast (load one file) | Slow (replay all commands) |
| **I/O overhead** | Low (fork + write) | Slight per-write overhead |
| **Best for** | Backups, fast restarts, read-heavy | Maximum durability |

### RDB

A `fork()` creates a child process that writes the snapshot to a temp file, then atomically renames. Main process is unaffected. Configured by `save N M` rules: "save if M keys changed in N seconds."

### AOF

Every write command is appended. Three `fsync` policies:
- `always`: fsync after every command → no loss; ~hundreds of writes/sec ceiling
- `everysec` (default): fsync every second → at most 1 s loss; ~100K writes/sec
- `no`: let OS decide → best performance, up to 30 s loss

### AOF Rewrite

Background process scans the dataset and writes a minimal AOF that recreates current state. Removes redundant intermediate writes. Configured by `auto-aof-rewrite-percentage` and `auto-aof-rewrite-min-size`.

### Hybrid (Redis 7+)

RDB snapshot prefix + AOF tail. Fast restart from RDB; durability from AOF.

**Production recommendation**: enable both (`save ""` to disable RDB only if you don't need backups). Set `appendfsync everysec`. Run AOF rewrite during off-peak.

---

## 5. Eviction Policies

When `maxmemory` is reached, Redis must evict keys. The policy determines which:

| Policy | Evicts |
|--------|--------|
| `noeviction` | Nothing — writes return error |
| `allkeys-lru` | Least recently used (any key) |
| `allkeys-lfu` | Least frequently used (any key) |
| `volatile-lru` | LRU among keys with TTL set |
| `volatile-lfu` | LFU among keys with TTL set |
| `volatile-ttl` | Shortest TTL first |
| `volatile-random` | Random among keys with TTL |
| `allkeys-random` | Random (any key) |

**Choice guide**:
- **Pure cache** (no durability required): `allkeys-lru` or `allkeys-lfu`
- **Cache + persistent store** (some keys must survive eviction): `volatile-lru` (only evict cache keys, never the source-of-truth keys)
- **LFU vs LRU**: LFU (Redis 4+) better for skewed access patterns (a key accessed 10K times in the last hour is "hot" even if not accessed in the last 5 min)

---

## 6. Replication & Sentinel

### Async Primary-Replica

- Primary handles writes; replicas asynchronously replicate
- Replica lag is unbounded by default (typically <1 s; can grow on heavy primary load)
- Replicas can serve reads (eventual consistency) — set `read-only` on the replica side, point reads at the replica
- Use a replica for read scaling, never for write scaling

### Sentinel

A separate process for HA:
- Monitors primaries and replicas
- Automatic failover: if the primary is down, Sentinel promotes a replica
- Quorum-based decision (don't split-brain)
- Clients connect via Sentinel to discover the current primary address

### Cluster Mode

For horizontal scaling beyond a single node's RAM:
- 16,384 hash slots; `CRC16(key) % 16384` determines the slot
- Each node owns a subset of slots
- Multi-key operations require keys to share a hash tag (e.g., `{userId}:profile`, `{userId}:settings`)

```
Slot 0-5460    → Node A (primary) + A-replica
Slot 5461-10922 → Node B (primary) + B-replica
Slot 10923-16383 → Node C (primary) + C-replica
```

**MOVED redirection**: client sent to wrong node → redirect to correct shard (client updates its slot map and retries).
**ASK redirection**: temporary, during slot migration between nodes.

**Cluster gossip**: nodes exchange PING/PONG every 100 ms with random peers. Failure detected after `cluster-node-timeout` (default 15 s). PFAIL → FAIL (majority agrees) → failover.

**Hot key in cluster**: one slot is overloaded. Solutions:
- Add replicas of that slot (`redis-cli --cluster replicate`)
- Use hash tags to spread related keys across slots (intentional)
- Client-side sharding (split the key into N sub-keys)
- Cache locally in the application

---

## 7. Pipelining & Lua

### Pipelining: RTT Amortization

Every command is a network round-trip. At 1 ms RTT, single-command throughput is 1000 ops/sec. Pipelining sends N commands without waiting, then reads N responses:

```
Without pipeline (10 commands, 1ms RTT):  10ms
With pipeline (10 commands in batch):     1ms  (10× speedup)
```

```java
// Jedis pipelining
Pipeline p = jedis.pipelined();
for (String userId : userIds) {
    p.get("user:" + userId);
}
List<Object> responses = p.syncAndReturnAll();
```

**Not atomic**: other commands can interleave. Use `MULTI/EXEC` for atomicity, Lua for atomicity + complex logic.

### MULTI/EXEC Transactions

```java
Transaction t = jedis.multi();
t.set("balance:A", "100");
t.set("balance:B", "200");
t.exec();  // atomic; no rollback on individual command failure
```

Server queues commands; `EXEC` runs them as a single block. No rollback — if a command fails, the others still run. Use `WATCH` for optimistic locking.

### Lua Scripts: Atomic + Logic

```lua
-- Atomic rate limiter in a single round trip
local count = redis.call('INCR', KEYS[1])
if count > tonumber(ARGV[1]) then
    redis.call('DECR', KEYS[1])
    return -1
end
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
return count
```

```java
jedis.eval(script, Collections.singletonList("rate:user:123"),
           "100", "60");
```

Atomic (single-threaded execution), one RTT, arbitrary logic. Use for any operation that needs atomicity across multiple commands.

---

## 8. Pub/Sub vs Streams

| Aspect | Pub/Sub | Streams |
|--------|---------|---------|
| **Persistence** | None | Durable on disk |
| **Delivery** | Fire-and-forget | At-least-once via consumer groups + XACK |
| **Replay** | No | Yes (any historical entry) |
| **Consumer groups** | No (all subscribers get all messages) | Yes (work distributed, like Kafka) |
| **Use case** | Live notifications, chat presence | Durable event log, job queue, CDC |

**Pub/Sub**: subscribers must be online; messages are lost if no subscriber is connected.

**Streams**:
```java
// Producer
XADD jobs:email * to "user@example.com" subject "Welcome";

// Consumer
XREADGROUP GROUP workers consumer1 COUNT 10 BLOCK 5000 STREAMS jobs:email >;
XACK jobs:email workers <message-id>;

// Reclaim stuck messages
XCLAIM jobs:email workers consumer2 3600000 <message-id>;
```

### Redis Streams vs Kafka

| Aspect | Redis Streams | Kafka |
|--------|---------------|-------|
| Storage | In-memory (with optional disk persistence) | Disk (sequential log) |
| Latency | Sub-ms | 5-50 ms |
| Throughput | Tens of thousands/sec | Millions/sec |
| Retention | Hours/days (RAM-bound) | Years (disk) |
| Replay | Within retention | Full historical |
| Use case | Low-latency in-app messaging | Cross-system event backbone |

---

## 9. Real-World Usage

| Use case | Data type | Notes |
|----------|-----------|-------|
| **Session store** | String or Hash with TTL | Sticky sessions; auto-expiry |
| **Cache** | String with TTL | Cache-aside pattern; `EX` for TTL |
| **Rate limiter** | Sorted Set (sliding) or String + INCR (fixed) | Sliding more accurate; fixed cheaper |
| **Leaderboard** | Sorted Set | `ZADD` + `ZREVRANGE` |
| **Distributed lock** | String + `SET NX PX` | Add fencing token for safety |
| **Recent activity feed** | List + LTRIM | Cap at N items |
| **Unique visitor count** | Set (exact) or HyperLogLog (approximate) | HyperLogLog saves massive memory at scale |
| **Pub/Sub for live UI** | Pub/Sub | Live chat presence; no replay needed |
| **Job queue** | Stream with consumer group | XACK + XCLAIM for failed jobs |

---

## 10. Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **Latency** | Sub-ms; ~100K ops/sec on commodity hardware | Limited by single-thread |
| **Data structures** | Purpose-built types (Sorted Set, HyperLogLog) | Each type has its own complexity |
| **Atomicity** | Free per command; Lua for complex | No multi-key transactions in cluster |
| **Persistence** | Optional RDB/AOF | Not a system of record; restart from RDB is best-effort |
| **Memory** | Predictable; bounded | RAM cost > SSD cost; eviction tuning is operationally important |
| **Cluster** | 16,384 slots; horizontal scale | Multi-key operations complicated; client must handle MOVED/ASK |

---

## 11. Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| **Hot key** | Single key gets 10K+ qps; single-thread blocked | Split key (write sharding); local cache in app; replica for reads |
| **Big key** | `HGETALL` on 1 MB hash blocks event loop | Split into multiple keys; `HSCAN` instead of `HGETALL` |
| **`KEYS *` in production** | Server freezes for seconds; timeouts cascade | `SCAN` with cursor; never `KEYS` in hot path |
| **Memory full** | Writes return `OOM command not allowed` | Raise `maxmemory` (if RAM available); set eviction policy; remove unused keys |
| **Replication lag** | Reads from replica return stale data | Monitor `master_repl_offset`; check network; reduce primary write load |
| **Persistence overhead** | AOF fsync stalls write path | Use `appendfsync everysec`; SSD; AOF rewrite during off-peak |
| **Cluster slot imbalance** | Some nodes hot, others idle | Use hashtag carefully; reshard; rebalance during off-peak |
| **Sentinel split-brain** | Two primaries accepting writes | Ensure odd number of Sentinels; proper quorum |
| **Client library bug** | Connection leak; pool exhaustion | Use proven library (Jedis, Lettuce, Redisson); monitor pool stats |

---

## 12. Performance

### Memory Sizing

- Estimate dataset size with `INFO memory` after populating representative data
- Add 50% headroom for forks (RDB/AOF rewrite), fragmentation, growth
- For RDB fork: `INFO memory` `used_memory_rss` should be ≤ 2× `used_memory` (overhead from fork copy-on-write)

### Encoding Selection

Small collections use compact encodings:
- `hash-max-listpack-entries 128`
- `hash-max-listpack-value 64`
- `list-max-listpack-size -2` (8 KB per node)
- `zset-max-listpack-entries 128`
- `set-max-intset-entries 512`

Cross thresholds trigger promotion to hashtable/skiplist. Tune based on `INFO memory` per-type stats.

### Slow Log

```bash
CONFIG SET slowlog-log-slower-than 10000   # log commands > 10 ms
SLOWLOG GET 10                              # last 10 slow commands
```

`CONFIG SET slowlog-max-len 128`.

### Avoiding Latency Spikes

- Disable transparent huge pages (`THP`) on Linux
- Use `taskset` to pin Redis process to one CPU (avoids cross-core cache thrash)
- Monitor `INFO stats` `instantaneous_ops_per_sec`, `instantaneous_input_kbps`, `instantaneous_output_kbps`

---

## 13. Implementation Patterns

### Java — Jedis Pool + Pipeline + Lua

```java
JedisPool pool = new JedisPool(new JedisPoolConfig(), "redis-cluster-host", 6379);

try (Jedis jedis = pool.getResource()) {
    // Single command
    jedis.setex("user:123", 3600, userJson);

    // Pipeline
    Pipeline p = jedis.pipelined();
    Response<String> name = p.get("user:123:name");
    Response<String> email = p.get("user:123:email");
    p.sync();
    System.out.println(name.get() + " " + email.get());

    // Lua for atomic rate limit
    String script =
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count > tonumber(ARGV[1]) then " +
        "  redis.call('DECR', KEYS[1]) " +
        "  return -1 " +
        "end " +
        "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) " +
        "return count";

    Object result = jedis.eval(script,
        Collections.singletonList("rate:user:123"),
        "100", "60");
    if ((Long) result == -1) throw new RateLimitException();
}
```

### Java — Redisson Distributed Lock with Fencing

```java
RedissonClient redisson = Redisson.create(config);

RLock lock = redisson.getLock("order:42");
try {
    // Wait up to 5s for the lock; lease 30s
    boolean acquired = lock.tryLock(5, 30, TimeUnit.SECONDS);
    if (!acquired) throw new LockUnavailableException();
    processOrder(order);
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

For stronger safety (preventing stale lock holder from writing after lease expiry), pass a `fencingToken` to downstream resources.

### Java — Lettuce (Reactive, Thread-Safe)

```java
RedisClient client = RedisClient.create("redis://localhost:6379");
StatefulRedisConnection<String, String> conn = client.connect();
RedisAsyncCommands<String, String> async = conn.async();

CompletableFuture<String> f1 = async.get("user:1:name");
CompletableFuture<String> f2 = async.get("user:2:name");
CompletableFuture.allOf(f1, f2).join();
```

Lettuce uses Netty; one connection is thread-safe and shared across the app.

---

## Quick Revision

- Redis is single-threaded for command processing; multi-threaded I/O since 6.0
- All operations are atomic per command; use Lua or MULTI/EXEC for compound atomicity
- 6 core data types: String, Hash, List, Set, Sorted Set, Stream
- Sorted Set uses skiplist + hashmap; O(log N) insert, O(1) score lookup
- Sorted Set powers sliding-window rate limiters and leaderboards
- RDB: snapshot, fast restart, possible data loss. AOF: log, durable, larger
- Eviction policies: `allkeys-lru` for pure cache, `volatile-lru` if some keys are source of truth
- Replication: async primary-replica; Sentinel for HA; Cluster for horizontal scale
- Cluster: 16,384 hash slots; MOVED/ASK redirects; multi-key ops need hash tags
- Pipelining amortizes RTT; Lua gives atomicity + complex logic in one round trip
- Pub/Sub = fire-and-forget; Streams = durable + consumer groups (Kafka-lite)

---

## See Also

- [02-building-blocks/caching-layer.md](../../02-building-blocks/caching-layer.md) — cache patterns, eviction
- [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md) — cluster slot distribution
- [04-advanced-topics/internals/kafka-internals.md](kafka-internals.md) — Streams vs Kafka comparison
- [04-advanced-topics/internals/index-structures.md](index-structures.md) — skiplist internals
- [04-advanced-topics/event-driven-architecture.md](../event-driven-architecture.md) — when to use Redis vs Kafka

---

## Interview Questions Asked

**Q: Design a rate limiter using Redis.**

A: Two options. **Fixed window** (cheaper, less accurate): key per minute bucket, `INCR` + `EXPIRE`; burst at minute boundary is possible. **Sliding window** (more accurate, slightly more expensive): Sorted Set, score = timestamp, member = unique request ID. On each request: `ZADD` with current timestamp; `ZREMRANGEBYSCORE` to drop entries older than 60 s; `ZCARD` to count; reject if > limit. For atomicity, wrap the three commands in a Lua script (one RTT). Alternative: `SET key value NX PX 60000` for "once per N seconds per user" semantics.

**Q: Cache-aside vs Write-through vs Write-behind?**

A: **Cache-aside** (lazy): app checks cache, on miss reads DB and populates cache. App code owns the cache. Risk: stale data, thundering herd (use `SETNX` lock or request coalescing). **Write-through**: app writes to cache and DB in the same operation; cache is always consistent. Cost: double the writes; slower. **Write-behind**: app writes to cache; cache asynchronously flushes to DB. Fastest, but data loss on cache crash unless persisted. Cache-aside is the most common pattern; write-through is preferred for read-heavy with strong consistency; write-behind is rare and requires careful durability planning.

**Q: How does Redis persist data, and what should I use in production?**

A: Two mechanisms. **RDB** (snapshot): periodic `BGSAVE` writes the entire dataset to a binary file using fork. Fast restart, compact, but up to N minutes of data loss on crash. **AOF** (append-only file): every write command is appended; `fsync` policy determines durability (`everysec` = at most 1 s loss, default). AOF rewrite compacts the log. **Production**: enable both. RDB for backups and fast restarts. AOF with `appendfsync everysec` for durability. Redis 7+ hybrid uses RDB prefix + AOF tail for best of both.

**Q: How does Redis Cluster handle slot migration?**

A: 16,384 hash slots distributed across nodes. `CRC16(key) % 16384` determines the owning slot. To move a slot from A to B: B imports the slot's data; during migration, A forwards writes for that slot to B with an `ASK` redirect (B handles them but doesn't yet claim the slot in its slot table). After migration completes, A sends `MOVED` to all clients for the migrated slot. Clients update their slot map. Temporary inconsistency during migration is bounded by the time to copy data, not the cluster's lifetime.

**Q: Redis vs Memcached?**

A: Both in-memory, both single-threaded historically. Differences: Redis has 8 data types (String, Hash, List, Set, Sorted Set, Stream, HyperLogLog, Geo); Memcached has only String. Redis has persistence (RDB, AOF); Memcached does not. Redis has transactions, Lua, pub/sub, Streams; Memcached does not. Redis Cluster provides sharding; Memcached uses consistent hashing client-side. Use Redis for most cases (richer features); Memcached only when you need a simple string-only cache with multi-threaded I/O and that's it.
