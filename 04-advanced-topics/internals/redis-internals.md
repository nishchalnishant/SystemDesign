# Redis Internals

## The Big Picture: An Index Card Box at the Library Reference Desk

Think of Redis as a **super-fast index card box at the reference desk of a library**.

- Every lookup is O(1) — you know exactly which card to pull.
- The card box lives **in memory** (RAM), so retrieval is measured in microseconds, not milliseconds.
- Persistence options let you **save the card box to disk** periodically (RDB) or **journal every transaction** (AOF), so the box survives a power outage.
- The cards come in different **formats** for different use cases — plain cards, numbered lists, unique label sets, sorted score boards, and maps of fields.

Redis stands for **RE**mote **DI**ctionary **S**erver. It is an open-source, in-memory key-value data store. Its single-threaded event loop means every operation is atomic — no race conditions on individual keys.

---

## File Mindmap

```
Redis Internals
├── Why It Exists
│   ├── Problem → database reads for hot data are too slow (milliseconds); need microsecond response
│   └── Physical limit → RAM access ~100ns vs SSD ~100μs; Redis exploits this for sub-millisecond lookups
├── Core Data Structures
│   ├── String → GET/SET/INCR; cache, counter, idempotency key; O(1)
│   ├── Hash → HGET/HSET; user session object, partial field updates; O(1) per field
│   ├── List → LPUSH/RPOP/LRANGE; task queue, recent items; O(1) push/pop
│   ├── Set → SADD/SMEMBERS/SINTER; unique visitors, tag systems; O(1) add/check
│   ├── Sorted Set → ZADD/ZRANGE/ZRANGEBYSCORE; leaderboards, sliding window rate limiter; O(log N)
│   │   └── Internals: Skip List (probabilistic linked list) for O(log N) rank queries
│   └── Streams → XADD/XREADGROUP/XACK/XCLAIM; durable message log with consumer groups
├── Rate Limiting Patterns
│   ├── Fixed Window → INCR counter per minute key; EXPIRE TTL=60s; fast but burst at boundary
│   └── Sliding Window → ZADD timestamp as score; ZREMRANGEBYSCORE removes old; ZCARD = current count
├── Caching Patterns
│   ├── Cache-aside (lazy loading) → miss: fetch DB → SET with TTL → return; cache only hot data
│   ├── Write-through → write to cache and DB simultaneously; consistent, double write overhead
│   └── Write-behind → write cache; async flush to DB; fastest writes, risk data loss on crash
├── Pub/Sub vs Streams
│   ├── Pub/Sub → fire-and-forget; no persistence; offline subscriber misses messages; use for live notifications
│   └── Streams → persistent log; XREADGROUP = consumer groups; XACK = ack; XCLAIM = reassign failed; use for reliable messaging
├── Persistence
│   ├── RDB (Redis Database Backup)
│   │   ├── Periodic fork → child process writes snapshot to disk; low I/O overhead
│   │   └── Risk: data loss between snapshots (minutes); faster restart than AOF
│   └── AOF (Append-Only File)
│       ├── Log every write command; fsync options: always / everysec / no
│       └── everysec → lose at most 1 second of data; recommended for durability
├── Redis Cluster
│   ├── 16,384 hash slots → CRC16(key) % 16384 → assign slot to node
│   ├── MOVED redirection → client sent to wrong node → redirect to correct shard
│   ├── Minimum 3 primaries (for quorum); each primary has 1+ replicas
│   └── Hot key problem → one slot overloaded; solution: key hash tags {userId}.suffix for co-location or client-side sharding
├── Single-Threaded Event Loop
│   ├── All commands execute serially → atomicity guaranteed for individual operations
│   ├── MULTI/EXEC → transaction block; no rollback on error; WATCH for optimistic locking
│   └── I/O threads (Redis 6+) → multi-threaded I/O, single-threaded command processing
├── Trade-offs
│   ├── Pro: sub-millisecond latency; rich data structures; atomic operations
│   ├── Con: data must fit in RAM; persistence adds write latency
│   └── Con: Redis Cluster complicates multi-key operations (keys must share hash slot)
└── Interview Angles
    ├── "Design a rate limiter" → sliding window with Sorted Set (ZADD + ZREMRANGEBYSCORE + ZCARD)
    ├── "How does Redis persist data?" → RDB for snapshots, AOF for durability; use both in production
    └── Follow-up: Redis vs Kafka for messaging → Redis Streams for low-latency; Kafka for replay + fan-out at scale
```

## 1. Core Data Structures

The index cards come in different formats. Choosing the right format is the most important Redis decision you make in a system design interview.

| Data Structure | Index Card Analogy | Best Use Case | Key Commands |
|---|---|---|---|
| **Strings** | A plain card with one value | Caching, session tokens, atomic counters | `SET`, `GET`, `INCR` |
| **Hashes** | A card with labeled fields (Name: Alice, Age: 30) | Storing objects (user profile) | `HSET`, `HGET`, `HMGET` |
| **Lists** | A numbered, ordered stack of cards | Queues, recent items feeds (FIFO/LIFO) | `LPUSH`, `RPOP`, `LRANGE` |
| **Sets** | A pile of cards — each label unique, no duplicates | Unique visitors, tags, membership checks | `SADD`, `SISMEMBER`, `SMEMBERS` |
| **Sorted Sets** | Cards ranked on a leaderboard by score | Leaderboards, sliding window rate limiters | `ZADD`, `ZRANGE`, `ZRANK` |
| **Streams** | A numbered ledger with consumer receipts | Durable message bus, async job queues | `XADD`, `XREAD`, `XREADGROUP` |

### Strings — The Plain Card

The most primitive structure. Can hold a string, integer, or binary blob (up to 512MB). `INCR` is atomic — useful for counters, rate limiting fixed windows, and generating unique IDs.

```java
// Pseudocode: Caching a user profile
SET user:123:name "Alice"         // store
GET user:123:name                 // retrieve -> "Alice"
INCR user:123:login_count         // atomic increment
EXPIRE user:123:session 3600      // TTL: expire in 1 hour
```

### Hashes — The Field-Map Card

Store an object without serialization overhead. Instead of storing a JSON blob (entire card) and deserializing the whole thing to change one field, Hashes let you update individual fields atomically.

```java
HSET user:123 name "Alice" age 30 email "alice@example.com"
HGET user:123 name         // "Alice"
HGET user:123 age          // "30"
HINCRBY user:123 age 1     // Increment age atomically
```

### Lists — The Ordered Stack

A doubly linked list. O(1) push/pop from either end. Good for queues (push to right, pop from left) or recent-activity feeds (push to left, trim to last 100).

```java
LPUSH recent:user:123 "page-home"    // Add to front
LPUSH recent:user:123 "page-cart"
LRANGE recent:user:123 0 9           // Get last 10 items
```

### Sets — The Unique Label Pile

Unordered collection of unique strings. Useful for "has this user already voted?" checks, unique visitor counts, or tag systems. Supports O(1) membership checks.

```java
SADD unique:visitors:2024-05-12 "user:123"
SADD unique:visitors:2024-05-12 "user:456"
SISMEMBER unique:visitors:2024-05-12 "user:123"   // 1 (true)
SCARD unique:visitors:2024-05-12                  // 2
```

### Sorted Sets — The Arcade Leaderboard

Every member has a **score**. Members are sorted by score in ascending order. You can instantly find a player's rank, retrieve the top-N players, or get all players within a score range.

**Analogy**: An arcade game leaderboard on the wall. Players (members) have scores. The board is always sorted. You can instantly answer: "What is Alice's rank?", "Who are the top 10?", "Who is within 100 points of Bob?"

Implemented internally using a **Skip List** — O(log N) for inserts, updates, and rank lookups.

```java
// Leaderboard
ZADD leaderboard 9500 "alice"
ZADD leaderboard 8200 "bob"
ZADD leaderboard 9800 "carol"

ZRANK leaderboard "alice"          // 1 (0-indexed, ascending)
ZREVRANK leaderboard "alice"       // 1 (descending - higher = better)
ZRANGE leaderboard 0 2 WITHSCORES // ["bob","8200","alice","9500","carol","9800"]
ZREVRANGE leaderboard 0 9          // Top 10 players (descending)
ZINCRBY leaderboard 300 "alice"    // Alice scores 300 more points
```

#### Sliding Window Rate Limiter using Sorted Sets

```java
// Key: rate:user:123, Score: timestamp, Member: unique request ID
ZADD rate:user:123 1715510400 "req:uuid-1"

// Remove entries older than the window (last 60 seconds)
ZREMRANGEBYSCORE rate:user:123 0 (now - 60000)

// Count requests in current window
ZCARD rate:user:123   // If > limit, block the request
```

---

## 2. Key System Design Patterns

### Cache-Aside Pattern

The standard way to use Redis as a database cache:

1. Check Redis. If data exists (cache **hit**) → return it immediately.
2. If data does not exist (cache **miss**) → query the database.
3. Write the result back to Redis with a TTL for future requests.

```java
public User getUser(String userId) {
    String cached = redis.get("user:" + userId);
    if (cached != null) {
        return deserialize(cached);          // Cache hit: O(1) from memory
    }
    User user = database.findById(userId);   // Cache miss: query DB
    redis.setex("user:" + userId, 3600, serialize(user));  // Cache for 1 hour
    return user;
}
```

**TTL** is critical — without it, stale data stays in the cache forever.

### Rate Limiting

**Fixed Window** — using String INCR:
```java
String key = "rate:user:123:min:" + currentMinute;
long count = redis.incr(key);
redis.expire(key, 60);  // Auto-expire after the minute
if (count > LIMIT) {
    throw new RateLimitException();
}
```

**Sliding Window** — using Sorted Set (more accurate, no edge-case boundary issues):
```java
String key = "rate:user:123";
long now = System.currentTimeMillis();
redis.zadd(key, now, UUID.randomUUID().toString());
redis.zremrangebyscore(key, 0, now - 60_000);  // Remove old entries
long requestCount = redis.zcard(key);
if (requestCount > LIMIT) {
    throw new RateLimitException();
}
```

---

## 3. Pub/Sub — The Sports Bar TV

### Analogy

A sports bar has the game on TV. Everyone in the bar watching that channel gets the same broadcast simultaneously. If you leave the bar and come back 20 minutes later, you missed those 20 minutes — there is no rewind, no history, no record of what was broadcast while you were away.

Redis Pub/Sub works the same way:
- Subscribers receive messages published to a channel **in real time**.
- If a subscriber is offline when a message is published, **the message is gone**.
- No persistence, no replay, no consumer groups.

```java
// Publisher
PUBLISH sports:scores "Team A: 3, Team B: 1"

// Subscriber (must be connected and listening)
SUBSCRIBE sports:scores
// Receives: "Team A: 3, Team B: 1"
```

**Use Pub/Sub for**: real-time notifications, chat presence indicators, live dashboards where missing a message is acceptable.

**Do NOT use Pub/Sub for**: job queues, reliable event delivery, or anything where you need delivery guarantees. Use **Redis Streams** instead.

### Redis Streams — Pub/Sub with Memory

Streams are a durable, persistent Pub/Sub. Messages are stored on the server. Consumer groups track which messages have been delivered and acknowledged (via `XACK`). If a worker crashes, another worker can claim the pending message with `XCLAIM`. This is the Redis equivalent of Kafka partitions, at smaller scale.

```java
// Producer
XADD jobs:email * to "user@example.com" subject "Welcome"

// Consumer group: distribute messages among workers
XREADGROUP GROUP workers consumer1 COUNT 10 STREAMS jobs:email >

// Acknowledge processed message
XACK jobs:email workers <message-id>

// Claim stuck messages (if consumer1 crashed)
XCLAIM jobs:email workers consumer2 3600000 <stuck-message-id>
```

---

## 4. Persistence: AOF vs. RDB

Redis is in-memory. To survive a restart, you must persist to disk.

### RDB — The Snapshot Photo

**Analogy**: At 3:00 AM every night, a photographer takes a picture of the entire card box. If the card box burns down at 3:59 AM, you can restore the 3:00 AM photo — but you lose the last 59 minutes of cards added since the photo was taken.

RDB takes a **point-in-time snapshot** of the entire dataset, written to a `dump.rdb` file using a fork-based process (so it does not block the main thread).

- **Pros**: Fast restart (load one file), compact file size, minimal performance overhead during normal operation.
- **Cons**: Data loss of up to the snapshot interval on crash (default: every 1–15 minutes depending on change thresholds).

### AOF — The Transaction Journal

**Analogy**: Every single transaction — every card added, every card updated, every card removed — is written in a journal in real time. If the card box burns down, you reconstruct it by replaying the journal from the beginning. Nothing is lost (or at most, 1 second of data with `fsync=everysec`).

AOF writes every **write operation** to an append-only log file (`appendonly.aof`).

- **Pros**: Much more durable — `fsync=always` means no data loss; `fsync=everysec` means at most 1 second of data loss.
- **Cons**: Larger file size; slower restart (must replay entire log); slightly higher write overhead.

### AOF Rewrite

Over time, the AOF log grows huge (it contains every individual write, including redundant updates). Redis compresses it periodically via an **AOF Rewrite** — creating a compact, optimized log that produces the current state directly, discarding intermediate steps.

| Feature | RDB | AOF |
|---|---|---|
| Durability | Low–Medium (snapshot interval) | High (per-second or per-write) |
| Recovery Speed | Fast (load one binary file) | Slow (replay all commands) |
| File Size | Compact | Large (grows over time) |
| Performance Impact | Minimal (fork-based) | Slight overhead on writes |
| Best For | Snapshots, backups, fast restarts | Maximum data safety |

**Production recommendation**: Use **both**. RDB for fast restarts and backups. AOF for durability. On startup, Redis loads the AOF (more complete) if enabled.

---

## 5. Redis Cluster — Splitting the Card Box

### Analogy

The library's card box has grown too large for one desk. Split it into sections by first letter: A–H at Desk 1, I–P at Desk 2, Q–Z at Desk 3. Each desk section (shard) is managed by one librarian (primary node), with backup librarians (replicas) at each desk who take over if the primary is sick.

Redis Cluster shards data across multiple nodes using **16,384 hash slots**. Each node owns a subset of slots.

```
hash_slot = CRC16(key) % 16384

Slot 0-5460    → Node A (primary) + Node A-replica
Slot 5461-10922 → Node B (primary) + Node B-replica
Slot 10923-16383 → Node C (primary) + Node C-replica
```

### MOVED Redirection

If a client sends a command to the wrong node (wrong hash slot), the node responds with a `MOVED` error containing the correct node's address. The client updates its internal slot map and retries. This is transparent to the application after the first redirect.

### Replication in the Cluster

Each primary node has one or more replicas. If the primary fails, the cluster promotes a replica to primary automatically. The cluster can tolerate up to one primary failure per shard (with RF=2) without data loss.

### Cluster vs. Single Redis Instance

| Feature | Single Instance | Redis Cluster |
|---|---|---|
| Max Data | Limited by single machine RAM | Horizontally scalable |
| Availability | Single point of failure | High availability with failover |
| Complexity | Simple | Requires slot-aware clients |
| Multi-key Operations | Works on any keys | Keys must be in same slot (use hash tags) |

---

## 6. Redis Streams vs. Apache Kafka

| Feature | Redis Streams | Apache Kafka |
|---|---|---|
| Storage | In-Memory (RAM) | Disk-based (Sequential Log) |
| Latency | Sub-millisecond | Low (5–50ms typical) |
| Throughput | High (tens of thousands/sec) | Extreme (millions/sec) |
| Durability | Best-effort (via RDB/AOF) | Very high (replicated commit logs) |
| Retention | Limited by RAM (hours/days, use MAXLEN) | Massive (years of data on disk) |
| Complexity | Simple (single binary) | High (brokers, KRaft, tuning) |
| Replay | Limited (within retention) | Full historical replay |

### When to Use Redis Streams

- You already run Redis and want to avoid new infrastructure.
- You need sub-millisecond latency (high-frequency trading, real-time gaming).
- Data volume is moderate (< 1 TB/day).
- Short message history is sufficient (last N events with MAXLEN).

### When to Use Kafka

- Mission-critical pipelines where data loss is unacceptable.
- Frequent replay or audit of historical data.
- Massive ingestion scale (logs from thousands of microservices).
- Big Data integrations (Hadoop, Spark, Snowflake via Kafka Connect).

---

## 7. Important Trade-offs

### Memory Cost

RAM is significantly more expensive than SSD/HDD. Do not store large, rarely accessed binary blobs in Redis. Use Redis for hot, frequently accessed data. Cold data belongs in the primary database.

### Not a Source of Truth

Redis with persistence is still primarily an in-memory store. Use it as a cache, session store, or real-time data layer — not as the authoritative, durable record for mission-critical data. Keep the primary database (Postgres, MySQL, Cassandra) as the source of truth.

### Single-Threaded Event Loop

Redis is single-threaded for command processing. This means:
- No race conditions on individual keys — every command is atomic.
- Avoid long-blocking commands (`KEYS *`, `SMEMBERS` on huge sets) — they block all other requests for their duration.
- Use `SCAN` instead of `KEYS *` for iterating over large datasets.

---

## Interview Quick Reference

| Pattern | Redis Structure | Why |
|---|---|---|
| Caching | String | O(1) get/set, TTL support |
| Session Store | Hash or String | Fast lookup by session ID |
| Leaderboard | Sorted Set | O(log N) rank updates, instant range queries |
| Rate Limiting (fixed window) | String + INCR | Atomic counter per time bucket |
| Rate Limiting (sliding window) | Sorted Set | Timestamp-scored entries, ZREMRANGEBYSCORE |
| Unique Visitors | Set | O(1) membership check, SCARD for count |
| Job Queue | List or Stream | LPUSH/RPOP queue; Streams for durability + consumer groups |
| Pub/Sub Notifications | Pub/Sub | Real-time broadcast; no persistence needed |
| Durable Event Log | Stream | Kafka-lite with XACK and XCLAIM |
| Distributed Lock | String + SET NX PX | Atomic compare-and-set with TTL |
