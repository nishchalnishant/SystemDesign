---
module: 05-hld-problems
topic: Hard
status: interview-ready
tags: [05-hld-problems, system-design, hard]
---
# Design a Distributed Cache

> **Difficulty**: Hard
> **Topics**: Consistent Hashing, Eviction, Replication, Hot Keys
> **Time**: 60 min
> **Companies**: Amazon, Meta, Common

---

## Clarifying Questions

1. "Is this a general-purpose cache (like Memcached/Redis), or application-specific?"
2. "What's the data size — 100M keys at 1KB average = 100 GB total?"
3. "What read/write ratio — 100:1 reads vs writes?"
4. "What consistency model — read-your-own-writes, or eventual?"
5. "Do we need persistence, or is cache data purely ephemeral?"
6. "What's the expected throughput — 1M ops/sec across the cluster?"

---

## Back-of-Envelope

```
100M keys, 1 KB average value = 100 GB raw data

Replication factor = 2 → 200 GB total storage needed

Node sizing:
  32 GB RAM per node → 6-7 nodes needed (200 GB / 32 GB = 6.25)
  With headroom: 12-16 nodes

Throughput:
  Each node: ~100K ops/sec (Redis single-threaded per slot)
  12 nodes: 1.2M ops/sec cluster capacity

Network:
  1M ops/sec × 1KB/op = 1 GB/sec → need 10 Gbps NICs on cache nodes

Hot keys:
  If top 1K keys get 10% of traffic: 100K ops/sec on 1K keys
  → one node gets 10% of cluster traffic → hot key problem
```

---

## APIs

```
// Client library interface (not HTTP — in-memory or TCP socket)
get(key: String) -> Optional<Value>
set(key: String, value: Value, ttl_sec: Int) -> OK
delete(key: String) -> OK

// Multi-key (batched, single round-trip)
mget(keys: List<String>) -> Map<String, Optional<Value>>
mset(pairs: Map<String, Value>, ttl_sec: Int) -> OK

// Atomic operations
incr(key: String, delta: Int = 1) -> Int         // atomic counter increment
setnx(key: String, value: Value, ttl_sec: Int) -> Bool  // set if not exists (distributed lock)
```

---

## Architecture

```
Client Application
  |
  +-- Client-side Routing (consistent hash ring, no extra hop)
  |     +-- Hash key -> node_id
  |     +-- Send request directly to primary node
  |     +-- Client holds membership list (updated via gossip or ZooKeeper)
  |
  v
Cache Node (Primary)
  +-- In-memory hash table (key -> value, TTL metadata)
  +-- LRU/LFU eviction (background thread)
  +-- Write-ahead log (WAL) for replication
  |
  +-- Async replication -> Replica Node(s)
        +-- Replica is read-only (failover only)
        +-- Replication lag: <5ms for intra-AZ

Membership / Coordination:
  - ZooKeeper or etcd: node join/leave events
  - Each node broadcasts heartbeat every 1s
  - Coordinator detects failure after 3 missed heartbeats (3s)
  - On failure: promote replica to primary, redistribute key ownership

Read path:
  Client -> primary -> return value (1 RTT)
  On primary failure: client -> replica (automatic fallback in client library)

Write path:
  Client -> primary -> ACK (synchronous)
  Primary -> replica (asynchronous, fire-and-forget for performance)
```

---

## Data Model

```
Per cache node (in-memory):
  hash_table: HashMap<String, CacheEntry>

CacheEntry {
  value:       bytes
  created_at:  unix_ms
  expires_at:  unix_ms  (0 = no TTL)
  access_at:   unix_ms  (updated on each read, for LRU)
  access_count: int     (incremented on each read, for LFU)
  size_bytes:  int
}

Consistent hash ring:
  Ring positions: 0 to 2^32 - 1
  Virtual nodes: 150 vnodes per physical node
  Key placement: SHA256(key) mod 2^32 -> walk ring clockwise -> land on node
  Replication: key stored on primary node + next N-1 nodes clockwise

ZooKeeper node:
  /cache/nodes/{node_id} -> { ip, port, vnodes[], joined_at }
  Ephemeral node: auto-deleted when node dies
```

---

## Key Design Decisions

**1. Consistent hashing with virtual nodes**
Simple modulo hashing (`node = hash(key) % N`): adding/removing a node rehashes all keys → cache miss storm. Consistent hashing: key lands on the nearest clockwise node on the ring. When a node is added/removed: only `1/N` of keys migrate. Virtual nodes (150 vnodes per physical node) smooth the distribution — without vnodes, 8 physical nodes create 8 large arcs (uneven distribution); with 150 vnodes each, arcs are small and distribution is nearly uniform. Key migration is proportional and gradual.

**2. Client-side routing vs. proxy routing**
Two architectures: (a) Client library holds the ring, routes directly to the node (Redis Cluster style). (b) Proxy node (like Twemproxy) sits between client and cache — client connects to proxy, proxy routes. Client-side: one fewer network hop, lower latency (1 RTT instead of 2). Proxy-side: dumb clients (any language without a cluster-aware client), simpler ops. Chosen: client-side for performance-sensitive applications; proxy for polyglot environments where maintaining smart clients in 5 languages isn't feasible.

**3. Async replication: performance vs. durability trade-off**
Synchronous replication: primary waits for replica to ACK before responding to client → +5ms per write → too slow for a cache. Asynchronous: primary responds immediately, replication happens in background → replica may be 1-5ms behind. On primary failure before replica catches up: the latest few writes are lost. For a cache, this is acceptable — cache is a performance optimization, not a source of truth. The DB remains authoritative; a cache miss on failover just causes a DB read (correct, just slower).

**4. LFU eviction over LRU for scan-heavy workloads**
LRU evicts the least recently used key. Problem: a sequential scan (scan 1M keys once, never again) poisons the LRU list — it evicts all hot keys and replaces them with scan keys. LFU tracks access frequency: a scan key gets count=1 (low frequency); a hot key gets count=100K (high frequency). LFU retains the genuinely hot keys. Trade-off: LFU requires 8 bytes of metadata per key (access counter) vs. 8 bytes for LRU pointer. Memory overhead is equivalent. For general workloads: LRU is simpler and sufficient. For scan-heavy (analytics, report generation hitting the same cache cluster): LFU is clearly better.

---

## Deep Dives

**Hot key problem**
A viral post's like count, a sale's inventory number, or a popular user's profile: one key gets 10% of cluster traffic → one node becomes the bottleneck. Solutions, in order of complexity:
1. Local L1 cache in the client process: cache hot key values in-process for 1s. One node absorbs 100K ops/sec; with L1 cache, that node sees ~100 ops/sec. Trade-off: 1-second stale reads.
2. Read replicas for the hot key: configure specific keys to have 5 replicas instead of 2. Reads are distributed across 5 nodes.
3. Key sharding with random suffix: for a write-heavy hot key (like a counter), use 10 shards (`likes_post_123_0` through `likes_post_123_9`). Reads sum all 10 shards. This distributes writes but requires aggregation on reads.

**Cluster rebalancing on node join**
New node joins the ring with 150 vnodes → claims 1/N of the key space. Keys that now belong to the new node must migrate from their previous owners. Migration flow: coordinator identifies affected key ranges → tells old node to stream keys to new node → once complete, update ring membership. During migration: old node still serves requests for migrated keys (no disruption). After migration complete: new node serves directly. Key migration throughput: 100 MB/sec per node = 100 GB of keys migrated in ~17 minutes — acceptable for most deployments.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Primary node dies | Keys on that node become unavailable | Promote replica; client library detects via heartbeat within 3s |
| Replica lag at failover | Recent writes (last 1-5ms) lost | Acceptable for cache; re-fetch from DB on cache miss |
| Network partition splits cluster | Split-brain: two primaries for same key | ZooKeeper quorum: minority partition rejects writes |
| Hot key overloads one node | Tail latency spike | L1 client cache; key sharding with random suffix |
| Node join rehashes too fast | Cache miss storm during migration | Rate-limit migration; keep old node serving during transition |

---

## Interview Follow-ups

**How do you support cron?**
Store a schedule expression. When a run succeeds or fails terminally, compute the next `run_at` and insert the next job instance.

**How do you cancel a job?**
If status is `SCHEDULED` or `READY`, conditionally set `CANCELLED`. If already `RUNNING`, mark cancellation requested and let cooperative workers stop safely.

**What metric matters most?**
Oldest ready job age. It shows whether the system is falling behind even if average latency looks fine.

---

## Interviewer Follow-Up Questions

**On consistency:**
- "You wrote to the cache (set key=X). Another read request goes to the replica and sees the old value. Is this a problem?" → Depends on the use case. For most cache use cases (session data, feed results, product info): a 1-5ms stale read from a replica is acceptable. The cache is an optimization, not the source of truth. For read-your-own-writes consistency: route writes and the subsequent reads from the same user to the primary node. In the client library: maintain an affinity map (user_id → primary node) for the duration of a session.
- "Cache-aside pattern: application checks cache → miss → queries DB → writes to cache. What race condition exists?" → Classic race: (1) Request A misses cache, queries DB, gets value v1. (2) Meanwhile, a write updates DB to v2 and invalidates cache. (3) Request A writes stale v1 to cache. Cache now has v1; DB has v2 → stale cache until TTL expires. Mitigations: short TTL (accept stale data for TTL duration); compare-and-swap on write (`SET key v1 IF current_value IS NULL`); Leases: DB-miss issues a lease token; write invalidation revokes the lease; only the lease holder can populate the cache.

**On consistent hashing:**
- "A node fails and 1/N of your keys are unavailable. How quickly does your system recover?" → Detection: heartbeat timeout at 3s (3 missed 1s heartbeats). Promotion: coordinator tells replica to become primary → 1-3s. Ring update: broadcast new membership to all clients → client libraries update within 5s. Total: 5-10s of elevated miss rate for 1/N of keys during which reads fall through to DB. With in-process L1 cache: some hot keys served from L1 during the gap. With DNS-based routing: DNS TTL adds another 30-60s. For sub-5s failover: client libraries must use ZooKeeper watches for instant membership updates.
- "How does adding a node during a traffic spike work without a cache miss storm?" → Gradual ring migration: new node is added to the ring with its 150 vnodes, but migration is rate-limited. Old nodes continue serving all requests during migration. As keys migrate to the new node, the old node continues as a fallback (forward missed requests). Cache hit rate dips temporarily (new node's keys not yet populated) → more DB reads → increase DB connection pool before the migration. Schedule node additions during off-peak hours.

**On eviction:**
- "LRU vs LFU — which is better for a YouTube recommendation cache?" → YouTube has a Zipfian distribution: top 1% of videos get 80% of views. LRU works fine for temporal locality (recently watched items are likely to be watched again). LFU works better when the popular set is stable and large scan operations exist (e.g., batch jobs fetching 1M videos for ML training). For a recommendation cache: LRU is typically fine because recent user sessions drive the cache working set. If batch ML jobs share the same cache cluster: use LFU to prevent scan poisoning.
- "Your cache is 100GB. You get a new requirement to cache 150GB of data. You can't add nodes. What do you do?" → Trade-off options: (1) Reduce TTLs — force more eviction to fit higher priority data in 100GB. (2) Compress values — zstd or snappy can achieve 3-5× compression for JSON/text values; 100GB of compressed data = 300-500GB logical. (3) Two-tier: keep only the hottest 20% of keys (100GB) in the fast cache; the next 30% in a cheaper store (disk-based Redis with RDB). (4) Add nodes (the real answer if the requirement is real). Compression is usually the first step — often free throughput for a small CPU overhead.

**On write-through failure:**
- "You do a write-through cache update: you write to cache AND to DB. The DB write fails but cache write succeeds. What now?" → Inconsistency: cache has the new value, DB has the old value. Anyone who reads from cache sees v2; DB reads see v1. Fix: write to DB first, then update cache. If DB write fails: cache is not updated → both DB and cache have v1 (consistent). If cache update fails after DB write succeeds: cache has stale v1, DB has v2 → next cache miss will re-populate from DB (self-healing). The invariant: DB is always >= cache in terms of recency. Never write to cache first when DB consistency matters.
