# Design Distributed Cache (Redis Cluster)

> **Difficulty**: Hard
> **Topics**: Consistent Hashing, Replication, Failover, Cache Invalidation
> **Time**: 60 minutes
> **Companies**: Google, Meta, Amazon (ElastiCache), Redis Labs

---

## Problem Statement

Design a distributed, in-memory cache like Redis Cluster that:
- Scales horizontally (add nodes to increase capacity)
- Provides high availability (automatic failover)
- Supports common cache operations (GET, SET, DELETE)
- Handles node failures gracefully
- Maintains data consistency across replicas

---

## Analogy

A librarian who memorizes the most frequently asked book locations so they don't have to check the catalog every time. A patron asks: "Where is the Python book?" The librarian answers from memory in 2ms instead of scanning 100,000 catalog entries.

Now scale to 100 libraries across a city. You need to decide: which librarian holds which memories? What happens when a librarian retires (node failure)? And what if two librarians memorize different answers for the same book after a catalog update (consistency)?

The "city" is your distributed cache. The key insight: you don't want to move every librarian's memories when you add a new library — that's the consistent hashing problem.

---

## Why This Is Hard

1. **Key redistribution on resize**: A naive `hash(key) % N` approach breaks when you add a node — every key maps to a different server, causing a thundering herd to the database as the cache cold-starts. Consistent hashing solves this by moving only ~1/N keys.
2. **Failover without data loss**: Replication is async by default. If a master crashes before replicating a write, that data is gone. Synchronous replication fixes this but adds latency.
3. **Cache invalidation**: Phil Karlton famously said there are only two hard things in CS. Knowing when to evict or invalidate a cached value — especially when the source of truth has changed — is genuinely hard in distributed systems.
4. **Hot keys**: A single celebrity tweet can cause millions of requests for one key/second, overwhelming the single node that owns it. No amount of sharding helps unless you explicitly replicate hot keys.
5. **Split brain**: A network partition can create two masters for the same slot, each accepting conflicting writes. The minority partition must go read-only or accept lost updates.

---

## Requirements

### Functional
- SET(key, value, TTL): Store key-value with optional expiration
- GET(key): Retrieve value
- DELETE(key): Remove key
- Automatic data distribution across nodes
- Replication for availability

### Non-Functional
- **Latency**: P99 < 5ms
- **Throughput**: 1M ops/sec per node
- **Availability**: 99.99% uptime
- **Scalability**: Add/remove nodes without downtime
- **Durability**: Optional (in-memory by default, can persist to disk)

---

## Scale Estimation

```
Assumptions:
- 10 billion keys
- Average value size: 1KB
- 10:1 read:write ratio
- Target: 10M ops/sec

Storage:
10B keys × 1KB = 10 TB
With replication (3×): 30 TB
Nodes needed: 30 TB ÷ 256 GB/node = 120 nodes

QPS per node:
10M ops/sec ÷ 120 nodes = 83K ops/sec (well within Redis capability)
```

---

## Core Concepts

### 1. Consistent Hashing

**Problem**: Simple hash % N breaks when N changes

```
Simple Hashing:
hash("user_123") % 3 = Node 0
Add 4th node → hash("user_123") % 4 = Node 3  ← DIFFERENT!
→ All keys need to move (expensive!)
```

**Consistent Hashing Solution:**
```
Hash ring (0 to 2^64-1):

         Node A (hash = 100)
              ↓
    ┌─────────────────┐
   /                   \
  │   Node C           │  Node B
  │  (hash = 300)      │  (hash = 200)
   \                   /
    └─────────────────┘

Key placement:
hash("user_123") = 150 → walks clockwise → Node B
hash("user_456") = 250 → walks clockwise → Node C

Add Node D (hash = 350):
Only keys between 300-350 move to Node D (minimal redistribution!)
```

**Virtual Nodes (for balance):**
```
Each physical node → 128 virtual nodes on ring
Node A: positions [10, 45, 87, 123, ...]
Node B: positions [23, 56, 91, 145, ...]

Benefit: More even distribution, smoother rebalancing
```

**Why virtual nodes?** Without them, a node added to the ring only captures keys from the one neighbor to its left. With 128 virtual nodes, the new node captures 1/N of each existing node's load — much more balanced.

---

### 2. Redis Cluster Hash Slots

**Redis Approach: 16,384 hash slots**

```
Hash slot = CRC16(key) mod 16384

Cluster (3 nodes):
├─ Node A: slots 0-5460 (5,461 slots)
├─ Node B: slots 5461-10922 (5,461 slots)
└─ Node C: slots 10923-16383 (5,462 slots)

Key "user_123":
CRC16("user_123") = 12345
12345 mod 16384 = 12345 → Node C
```

**Adding Node D:**
```
Move slots to Node D:
├─ Node A: 0-4095 (give 1,366 slots to D)
├─ Node B: 5461-9556 (give 1,366 slots to D)
├─ Node C: 10923-15018 (give 1,366 slots to D)
└─ Node D: 4096-5460, 9557-10922, 15019-16383 (4,098 slots)

Result: Each node has ~4,096 slots (even distribution)
```

---

### 3. Replication

```
Master-Replica Architecture:

Master A → Replica A1 (async replication)
Master B → Replica B1
Master C → Replica C1

If Master A fails:
1. Replicas vote
2. Replica A1 promoted to master
3. Clients redirect to new master
```

**Replication Flow:**
```
Client → SET key=foo, value=bar
         ↓
    Master A (writes)
         ↓
    Replication log → Replica A1 (async)
                   → Replica A2

Client → GET key=foo
         ↓
    Master A OR Replica A1 (read from any)
```

**Async vs Sync replication trade-off:**
- Async (default): Master acks write before replica confirms. Throughput is high, latency is low. Risk: data loss on master crash.
- Sync (WAIT command): Master waits for N replicas to confirm. Durability guaranteed. Cost: latency increases by round-trip time to replica.

---

## Architecture

```
┌─────────────────────────────────────────┐
│          Redis Cluster                   │
│                                          │
│  ┌──────────┐  ┌──────────┐  ┌─────────┐│
│  │ Master A │  │ Master B │  │Master C ││
│  │slots     │  │slots     │  │slots    ││
│  │0-5460    │  │5461-10922│  │10923-   ││
│  └────┬─────┘  └────┬─────┘  └────┬────┘│
│       │             │              │     │
│       ▼             ▼              ▼     │
│  ┌──────────┐  ┌──────────┐  ┌─────────┐│
│  │Replica A1│  │Replica B1│  │ReplicaC1││
│  └──────────┘  └──────────┘  └─────────┘│
└─────────────────────────────────────────┘
           ↑     Gossip Protocol
           └──── (node discovery, health checks)

Client (Smart Client):
├─ Maintains slot→node mapping
├─ Routes requests to correct node
└─ Handles redirects (MOVED, ASK)
```

---

## Data Model

```
In-Memory Store (each node):

Hash Table:
key → {
  value: "data",
  ttl: 1675843200,  // Unix timestamp
  type: "string"    // string, list, hash, set, zset
}

Example:
"user:123" → {value: "{\"name\": \"Alice\"}", ttl: null, type: "string"}
"session:abc" → {value: "xyz", ttl: 1675843200, type: "string"}
```

---

## API Design

### Smart Client (Cluster-Aware)

```java
// Client knows cluster topology
RedisCluster cluster = new RedisCluster(["node1:6379", "node2:6379", "node3:6379"]);

// SET operation
cluster.set("user:123", "{\"name\": \"Alice\"}");
// Client calculates: slot = CRC16("user:123") % 16384 = 5000
// Routes to Node A (owns slots 0-5460)

// GET operation
String value = cluster.get("user:123");
// Routes to Node A (or Replica A1 if read from replica enabled)

// DELETE operation
cluster.del("user:123");
```

### Redirection (MOVED)

```
Client requests: GET user:456 → Node A
Node A: "user:456" is in slot 8000 (owned by Node B)
Response: -MOVED 8000 nodeB:6379

Client updates slot mapping, retries → Node B
Node B: Returns value
```

### Resharding (ASK)

```
During resharding (slot 5000 moving from Node A to Node D):

Client → GET key_in_slot_5000 → Node A
Node A: Slot partially migrated
Response: -ASK 5000 nodeD:6379

Client sends: ASKING, then GET key_in_slot_5000 → Node D
Node D: Returns value (temporary redirect, does NOT update client mapping)
```

**MOVED vs ASK distinction:**
- `MOVED`: Slot permanently lives on the new node. Client updates its map.
- `ASK`: Slot is mid-migration. Client uses new node for this request only; does not update its map (the migration might roll back).

---

## Scaling Operations

### Adding a Node

```
1. Add Node D to cluster:
   CLUSTER MEET nodeD:6379

2. Assign slots to Node D:
   - Move slots 0-1365 from Node A
   - Move slots 5461-6826 from Node B
   - Move slots 10923-12288 from Node C

3. Resharding (for each slot):
   a. Mark slot as "MIGRATING" on source node
   b. Mark slot as "IMPORTING" on target node
   c. Migrate keys one by one (MIGRATE command)
   d. Update slot ownership

4. Clients automatically discover new topology via gossip
```

**Zero Downtime**: Resharding happens while cluster serves requests (ASK redirects)

### Removing a Node

```
1. Reshard all slots from Node D to other nodes
2. Wait for data migration to complete
3. CLUSTER FORGET nodeD:6379 (remove from cluster)
4. Node D can be decommissioned
```

---

## Replication & Failover

### Automatic Failover

```
Scenario: Master A crashes

1. Detection (via gossip):
   - Replica A1 detects Master A unreachable
   - Starts failover timer (typically 1 second)

2. Replica Promotion:
   - Replica A1 broadcasts CLAIM message
   - Other masters vote (majority needed)
   - Replica A1 promoted to Master

3. Cluster Update:
   - Master B, C update topology: "Node A1 is now master for slots 0-5460"
   - Clients receive MOVED redirects, update mapping

4. Duration: 1-2 seconds (brief unavailability for writes to those slots)
```

**Quorum**: Majority of masters must agree (3 masters → need 2 votes)

---

## Consistency Trade-offs

### Eventual Consistency (Async Replication)

```
Scenario:
1. Client → SET key=foo → Master A (returns OK)
2. Master A crashes BEFORE replicating to Replica A1
3. Replica A1 promoted (missing "foo")
4. Client → GET key=foo → returns nil (data lost!)

Risk: Small window of data loss (milliseconds)
```

**Solution: WAIT command**
```
SET key=foo
WAIT 1 1000  # Wait for 1 replica to ack within 1000ms
→ Slower, but safer (synchronous replication)
```

---

## Cache Eviction

### Eviction Policies

```
maxmemory 256gb
maxmemory-policy allkeys-lru

Policies:
- allkeys-lru: Evict least recently used (any key)
- volatile-lru: Evict LRU among keys with TTL
- allkeys-random: Evict random key
- volatile-ttl: Evict keys with shortest TTL
- noeviction: Return error when full
```

**LRU Implementation (Approximate):**
```
Redis uses sampling (5 keys) instead of perfect LRU (faster).
Evict key with oldest access time among the sample.
```

**Choosing a policy:**
- Use `allkeys-lru` when cache can be fully reconstructed from DB (pure cache use).
- Use `volatile-lru` when some keys are critical and should never be evicted (give them no TTL).
- Use `noeviction` for session stores where you can't afford silent key loss.

---

## Failure Scenarios

### Master Failure
```
Impact: Writes to affected slots unavailable for 1-2 sec
Mitigation: Automatic replica promotion
Data loss: Possible (unacknowledged writes in async replication)
Recovery: New master continues; original master can rejoin as replica
```

### Network Partition (Split Brain)
```
Scenario: 3 masters, partition splits 2:1

Partition 1 (2 masters): Continues operating (has majority)
Partition 2 (1 master): Goes read-only (minority, can't form quorum)

When healed: Minority partition syncs from majority (minority writes lost)
```

### Hot Key Problem
```
Scenario: 10M requests/sec hitting "trending_topic:123" on Node B

Effect: Node B overwhelmed; P99 latency spikes; other keys on B also slow

Mitigations:
- Local in-process cache (L1): Cache hot key in application memory for 1 second
- Key replication: Write "trending:123:{replica_id}" to N nodes;
  read from random replica
- Read replicas: Route reads to replicas (enable READONLY on replica nodes)
```

### All Replicas Fail
```
Impact: Master continues, but no failover if master fails next
Mitigation: Monitor replica health alerts (P0), add new replicas immediately
```

---

## Monitoring

```
Key Metrics:
- Hit rate: cache_hits / (cache_hits + cache_misses) > 95%
- Latency: P99 < 5ms
- Memory usage: < 80% of max
- Evictions per second (spikes indicate memory pressure)
- Replica lag: < 1 second

Alerts:
P0: Master unreachable (no replica available) — manual intervention
P1: Hit rate < 90% for 10 min (cache not effective, check access patterns)
P2: Memory > 90% (add nodes or tighten TTLs)
```

---

## Interview Talking Points

**Q: "Why consistent hashing over simple hash?"**
- A: "Adding/removing nodes with simple hash requires rehashing ALL keys — the entire cache cold-starts. Consistent hashing only moves ~1/N keys, keeping the cache warm during scale operations."

**Q: "How does Redis Cluster handle failures?"**
- A: "Automatic replica promotion via voting. Majority of masters must agree. Failover takes 1-2 seconds. During that window, writes to the affected slots return errors."

**Q: "What if you can't tolerate data loss?"**
- A: "Use WAIT command for synchronous replication to N replicas before acknowledging the write. This trades latency (one extra round trip) for durability. Alternatively, use AOF persistence with `fsync=always`, but this dramatically reduces throughput."

**Q: "Redis vs Memcached?"**
| Feature | Redis | Memcached |
|---------|-------|-----------|
| Data structures | Rich (lists, sets, hashes, sorted sets) | Key-value only |
| Persistence | Yes (RDB/AOF) | No |
| Replication | Yes (built-in) | No |
| Clustering | Built-in | External (libmemcached) |
| Use case | Cache + data store + pub/sub | Pure cache |

**Q: "How do you handle cache invalidation for write-heavy workloads?"**
- A: "Three strategies: (1) TTL expiry — simplest, accepts stale reads; (2) write-through — update cache and DB in same request (consistent, slower writes); (3) cache-aside with explicit DELETE on DB update — most common, brief inconsistency window between DELETE and next cache population."

**Trade-offs:**
- Availability vs Consistency: Redis prioritizes availability (asynchronous replication by default)
- Memory vs Durability: In-memory (fast) but must persist to disk for durability (slower)
- Simplicity vs Features: Memcached simpler, Redis more powerful

---

## Interview Questions Asked

### Google
1. **"Design Memcache at Google scale."** → Tests cluster topology and thundering herd awareness; key answer: consistent hashing ring with virtual nodes, lease-based thundering herd protection (only one client recomputes on miss), regional replication for latency.

### Amazon
1. **"Design an ElastiCache cluster for a high-traffic service."** → Tests replication and failover planning; key answer: Redis Cluster with M primaries + 1 replica each, Sentinel-based auto-failover, VPC-internal access, read replicas for read-heavy workloads.

### Common Follow-ups
1. **"How does a consistent hashing ring with virtual nodes work?"** → Tests hash ring mechanics; each physical node owns V virtual positions on the ring (e.g., V=150); key maps to the next clockwise node; adding a node only migrates ~1/N keys; virtual nodes smooth uneven load distribution.
2. **"How do you handle cache eviction under memory pressure?"** → Tests eviction policy selection; LRU is default but LFU (least-frequently-used) is better for skewed access patterns; `allkeys-lfu` in Redis avoids evicting hot keys; monitor `evicted_keys` metric — sustained evictions signal under-provisioning.
3. **"How do you solve the thundering herd on cold start?"** → Tests cache population strategies; on cache miss, use Redis `SETNX` as a mutex — only one worker recomputes while others wait; alternatively, probabilistic early expiration (PER) recomputes slightly before TTL expires to pre-warm; staggered TTL jitter prevents simultaneous mass expiry.
4. **"Write-through vs. write-behind — when do you choose each?"** → Tests consistency vs. performance trade-off; write-through: cache and DB updated synchronously — consistent but adds write latency; write-behind: write to cache immediately, flush to DB asynchronously — lower latency but risk of data loss if cache crashes before flush.
