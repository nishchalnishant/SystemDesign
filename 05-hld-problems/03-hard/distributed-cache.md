> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed cache (Redis Cluster / Memcached) — horizontal scaling of in-memory key-value storage with consistent hashing, replication, and eviction.
>
> **Key design decisions:**
> - Consistent hashing: keys distributed across nodes using hash ring; virtual nodes (vnodes) ensure even load; adding/removing node only moves O(K/N) keys
> - Replication: each primary has 1–2 replicas; async replication for performance; replica auto-promotes on primary failure (Sentinel/Cluster)
> - Eviction policies: LRU (evict least recently used), LFU (evict least frequently used); choose based on access pattern; allkeys-lru for pure cache
> - Cache-aside pattern: application checks cache → miss → fetch from DB → populate cache; avoids stale data on write
> - Write-through vs write-behind: write-through (write to cache + DB atomically) vs write-behind (write to cache, async DB flush); write-behind risks data loss
> - Hot key problem: single popular key overwhelms one node; solution: local in-process micro-cache + key sharding (append shard ID suffix)
> - Failure handling: circuit breaker on cache; fall through to DB on cache unavailability; don't crash on cache miss
>
> **Key takeaway:** Consistent hashing with virtual nodes is the foundation — getting the hot key problem right (local micro-cache + key sharding) separates senior candidates.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, distributed-cache, consistent-hashing, eviction, redis, memcached]
---
# Design a Distributed Cache

> **Difficulty**: Hard | **Asked at**: Amazon, Google, Meta, Uber

---

## Problem Statement

Design a distributed in-memory cache system like Redis Cluster or Memcached. The cache stores key-value pairs with optional TTL, distributes data across multiple nodes, handles node failures gracefully, and supports horizontal scaling without rehashing all keys.

---

## Functional Requirements

1. **GET / SET / DELETE**: Basic key-value operations with optional TTL
2. **Consistent hashing**: Distribute keys across nodes; adding/removing nodes rehashes minimal keys
3. **Replication**: Each key is replicated to N nodes for durability
4. **Eviction**: LRU eviction when memory is full
5. **TTL**: Automatic key expiry
6. **Partitioned data**: No single node holds all data (horizontal partitioning)

---

## Non-Functional Requirements

- **Latency**: GET < 1ms P99, SET < 2ms P99
- **Throughput**: 1M operations/sec per node; cluster scales to 100M ops/sec
- **Availability**: Cache continues serving during single-node failures
- **Consistency**: For replicated keys: eventual consistency (acceptable to serve stale data briefly after write)
- **Scale**: Petabytes of cached data across thousands of nodes

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `CacheEntry` | key (bytes), value (bytes), ttl_seconds, created_at, last_accessed |
| `CacheNode` | node_id, ip, port, ring_position[], is_primary, replica_of |
| `ConsistentHashRing` | sorted list of (hash_position, node_id) |

---

## API Design

```
# Client library API (e.g., Python)
cache = DistributedCache(nodes=["node1:6379", "node2:6379", "node3:6379"])

cache.set("user:123", json.dumps(user_data), ttl=3600)
value = cache.get("user:123")  # returns None if expired or not found
cache.delete("user:123")

# Atomic operations
cache.incr("counter:page_views", delta=1)
cache.setnx("lock:resource_id", "locked", ttl=30)  # set if not exists (lock)
```

**Wire protocol** (Redis RESP):
```
Client → Node: *3\r\n$3\r\nSET\r\n$8\r\nuser:123\r\n$45\r\n<value>\r\n
Node → Client: +OK\r\n

Client → Node: *2\r\n$3\r\nGET\r\n$8\r\nuser:123\r\n
Node → Client: $45\r\n<value>\r\n  (or $-1\r\n if not found)
```

---

## High-Level Design

```
Client (with consistent-hash routing library)
  │ hash(key) → determine primary node
  │
  ▼
Primary Cache Node (holds key in memory)
  │ GET: read from memory (hash table)
  │ SET: write to memory → async replicate to N-1 replica nodes
  │ TTL expiry: lazy expiry (check on access) + active sweep (background)
  │
  ▼
Replica Cache Nodes (N-1 replicas for durability)
  │ Serve reads when primary is unavailable
  │ Accept replication writes from primary

Cluster Management:
  Gossip protocol: nodes exchange health info every 1s
  Consistent Hash Ring: maintained by all nodes
  Client library: subscribes to ring updates; reroutes on node failure
```

---

## Deep Dive 1: Consistent Hashing Ring

**Problem**: With N=3 cache nodes, a naïve approach maps `hash(key) % 3` to a node. When a 4th node is added, `hash(key) % 4` remaps almost all keys — the entire cache is invalidated, causing a thundering herd on the database.

**Consistent hashing**:
- Map each node to multiple positions on a 0–2^32 ring using `hash(node_id + virtual_node_index)`
- To find which node owns a key: compute `hash(key)` → find the next node clockwise on the ring
- When a node is added: only the keys between the new node's position and its predecessor are remapped to the new node (1/N of total keys)
- When a node is removed: only its keys migrate to the next node clockwise

**Virtual nodes (vnodes)**: Each physical node gets 150 virtual nodes (positions on the ring). This distributes keys more evenly — without vnodes, unlucky hash placement can give one node 50% of the keys.

```python
class ConsistentHashRing:
    def __init__(self, vnodes=150):
        self.ring = {}   # position → node_id
        self.sorted_keys = []

    def add_node(self, node_id):
        for i in range(self.vnodes):
            key = hash(f"{node_id}:{i}") % (2**32)
            self.ring[key] = node_id
        self.sorted_keys = sorted(self.ring.keys())

    def get_node(self, cache_key: str) -> str:
        key_hash = hash(cache_key) % (2**32)
        idx = bisect.bisect_right(self.sorted_keys, key_hash) % len(self.sorted_keys)
        return self.ring[self.sorted_keys[idx]]
```

---

## Deep Dive 2: Replication and Consistency

**Problem**: With N=3 replicas for each key, how do you handle reads and writes to ensure data durability without sacrificing latency?

**Write replication (async)**:
- Client writes to the primary node
- Primary returns OK immediately (latency = 1 write)
- Primary asynchronously replicates to 2 replica nodes
- Trade-off: if primary crashes before replication, the write is lost — **at-risk window**

**Quorum writes (sync, stronger durability)**:
- `W=2`: Client sends write to primary; primary forwards to replicas; waits for ACK from any 1 replica before returning OK
- `R=2`: Client reads from primary + 1 replica; returns the value with the highest version
- `W + R > N` (2+2>3) → always reads the latest written value
- Trade-off: higher latency (waits for N/2 + 1 confirmations)

**Read repair**: On a quorum read, if the primary and replica disagree, the cache repairs the stale replica asynchronously. No extra latency for the client.

**Primary failure**: Gossip protocol detects primary failure within 1-2 seconds. The replica with the highest replication offset is promoted to primary. Client library reconnects to new primary. Keys written to the old primary but not yet replicated may be lost (RPO > 0 for async replication).

---

## Deep Dive 3: Eviction and Memory Management

**Problem**: The cache is full (64 GB RAM consumed). A new key arrives. Which existing key do you evict?

**Eviction policies**:
- **LRU (Least Recently Used)**: Evict the key not accessed for the longest time. Optimal for access pattern with temporal locality. Implementation: O(1) with doubly-linked list + hash map (classic LRU).
- **LFU (Least Frequently Used)**: Evict the key accessed fewest times. Better for skewed access patterns. More complex — requires frequency counting.
- **allkeys-random**: Evict a random key. Worst performance but O(1) with no bookkeeping.
- **volatile-lru**: Only evict keys that have a TTL set. Keeps non-expiring keys forever. Redis default.

**Approximate LRU (Redis's actual approach)**:
- Maintaining a perfect LRU list requires updating the list on every GET (write on read) — too expensive at 1M ops/sec.
- Redis approximates LRU: each key stores `lru_clock` (last access timestamp in seconds, 3 bytes). On eviction: randomly sample 10 keys, evict the one with the oldest `lru_clock`. This approximates LRU with only 3 extra bytes per key.

**TTL expiry**:
- **Lazy expiry**: Check if a key is expired on each access. Expired keys are deleted on read. Memory is freed gradually.
- **Active expiry sweep**: Background task samples random keys every 100ms. If > 25% of sampled keys are expired, repeat until < 25%. This bounds the amount of expired memory wasted.

---

## Interviewer Questions by Level

**Junior**:
- What is a cache? What's the difference between a cache hit and a cache miss?
- What is LRU eviction? When would a key be evicted?
- What is TTL and why is it important for cached data?

**Mid-level**:
- What problem does consistent hashing solve? Why is `hash(key) % N` problematic?
- Explain virtual nodes. Why do we add virtual nodes to a consistent hash ring?
- Compare LRU vs LFU eviction. When would you choose each?

**Senior**:
- Design the replication strategy for a cache with N=3 replicas. What are the trade-offs between async and quorum replication?
- A cache node fails. How does the system detect the failure, promote a replica, and reroute client requests — with zero data loss?
- How does Redis implement approximate LRU efficiently? Why not track exact LRU?
- Design a cache warming strategy for a 64 GB cache after a cold restart — how do you prevent a thundering herd on the database?
