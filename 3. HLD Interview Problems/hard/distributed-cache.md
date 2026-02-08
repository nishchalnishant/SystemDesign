# Design Distributed Cache (Redis Cluster)

> **Difficulty**: Hard  
> **Topics**: Consistent Hashing, Replication, Failover, Cache Invalidation  
> **Time**: 60 minutes  
> **Companies**: Google, Meta, Amazon (ElastiCache), Redis Labs

## Problem Statement

Design a distributed, in-memory cache like Redis Cluster that:
- Scales horizontally (add nodes to increase capacity)
- Provides high availability (automatic failover)
- Supports common cache operations (GET, SET, DELETE)
- Handles node failures gracefully
- Maintains data consistency across replicas

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

## Core Concepts

### 1. Consistent Hashing

**Problem**: Simple hash % N breaks when N changes

```
Simple Hashing:
hash("user_123") % 3 = Node 0
Add 4th node → hash("user_123") % 4 = Node 3 ← DIFFERENT!
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
Node D: Returns value (temporary redirect, doesn't update mapping)
```

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
Redis uses sampling (5 keys) instead of perfect LRU (faster)
Evict key with oldest access time among sample
```

---

## Failure Scenarios

### Master Failure
```
Impact: Writes to affected slots unavailable for 1-2 sec
Mitigation: Automatic replica promotion
Data loss: Possible (unacknowledged writes in async replication)
```

### Network Partition (Split Brain)
```
Scenario: 3 masters, partition splits 2:1

Partition 1 (2 masters): Continues operating (has majority)
Partition 2 (1 master): Goes read-only (minority, can't form quorum)

When healed: Minority partition syncs from majority
```

### All Replicas Fail
```
Impact: Master continues, but no failover if master fails
Mitigation: Monitor replica health, add new replicas
```

---

## Monitoring

```
Key Metrics:
- Hit rate: cache_hits / (cache_hits + cache_misses) > 95%
- Latency: P99 < 5ms
- Memory usage: < 80% of max
- Evictions per second
- Replica lag: < 1 second

Alerts:
P0: Master unreachable (no replica available)
P1: Hit rate < 90% for 10 min (cache not effective)
P2: Memory > 90% (add nodes or eviction)
```

---

## Interview Tips

**Q: "Why consistent hashing over simple hash?"**
- A: "Adding/removing nodes with simple hash requires rehashing ALL keys. Consistent hashing only moves ~1/N keys."

**Q: "How does Redis Cluster handle failures?"**
- A: "Automatic replica promotion via voting. Majority of masters must agree. Failover takes 1-2 seconds."

**Q: "What if you can't tolerate data loss?"**
- A: "Use WAIT command for synchronous replication (slower), or persistence (RDB/AOF snapshots)."

**Q: "Redis vs Memcached?"**
| Feature | Redis | Memcached |
|---------|-------|-----------|
| Data structures | Rich (lists, sets, hashes) | Key-value only |
| Persistence | Yes (RDB/AOF) | No |
| Replication | Yes | No |
| Clustering | Built-in | External (libmemcached) |
| Use case | Cache + data store | Pure cache |

**Trade-offs:**
- Availability vs Consistency: Redis prioritizes availability (asynchronous replication)
- Memory vs Durability: In-memory (fast) but must persist to disk for durability (slower)
- Simplicity vs Features: Memcached simpler, Redis more powerful
