---
module: 05-hld-problems
topic: Easy
status: interview-ready
tags: [05-hld-problems, system-design, easy]
---
# Design a Key-Value Store

> **Difficulty**: Easy/Medium
> **Topics**: Consistent Hashing, LSM-Tree, Quorum, Replication
> **Time**: 45 min
> **Companies**: Google, Amazon, Meta

---

## Clarifying Questions

1. "What's the data size — are values small (< 1KB) or large blobs (MB+)?"
2. "What read/write ratio should we optimize for — 10:1 reads, or write-heavy?"
3. "Do we need strong consistency or eventual consistency? Any linearizability requirements?"
4. "What's the target availability SLA — 99.9% or 99.99%?"
5. "Do we need TTL support, range queries, or point lookups only?"
6. "Single region or multi-region? If multi-region, is cross-DC replication required?"

---

## Back-of-Envelope

```
Scale:
  10B keys, 1KB avg value = 10TB total data
  500K reads/sec, 50K writes/sec (10:1 ratio)

Sharding:
  10TB / 200GB per node = ~50 nodes minimum
  Add replication factor 3 → 150 nodes total

Memory (Bloom filter per SSTable):
  1B keys at 1% FPR = 10 bits/key = ~1.25 GB per node
  At 50 nodes: 62 GB total for filters (fits in RAM)

Throughput:
  500K reads/sec / 50 nodes = 10K reads/sec per node (achievable with SSDs)
```

---

## APIs

```
// Core operations
put(key: String, value: Bytes, ttl?: seconds) → void
get(key: String) → Bytes | null           // null on miss or expired
delete(key: String) → void                // logical tombstone

// Batch (reduces network RTTs)
mget(keys: String[]) → Map<String, Bytes>
```

Key decisions inline: `get` must be O(1) average; `delete` writes a tombstone (not immediate removal — compaction cleans it up).

---

## Architecture

```
Client
  │
  ▼
Coordinator Node (any node, routes request)
  │
  ├── Consistent Hashing Ring (virtual nodes)
  │     - Each physical node owns multiple virtual nodes (150 vnodes/node)
  │     - Key → hash(key) → find nearest vnodes clockwise on ring
  │
  ├── Replication (RF=3, next 3 nodes clockwise on ring)
  │     - Write to all 3, wait for W=2 ACKs (quorum write)
  │     - Read from 2 nodes, return newest (quorum read)
  │
  └── Storage Node (per node)
        ├── MemTable (in-memory sorted write buffer)
        ├── WAL (write-ahead log for durability)
        └── SSTables (immutable sorted disk files, L0→L6)
```

```
Write path:
  1. Write to WAL (sync to disk)
  2. Write to MemTable
  3. MemTable full (64MB) → flush to L0 SSTable
  4. Background compaction: merge SSTables L0→L1→L2

Read path:
  1. Check MemTable (O(log N))
  2. Check Bloom filter for each SSTable (skip if key absent)
  3. Binary search inside SSTable if Bloom says "maybe"
```

---

## Data Model

```sql
-- Not a relational model — this shows the logical SSTable structure

-- Each SSTable file on disk:
--   Header: min_key, max_key, bloom_filter (10 bits/key, 1% FPR)
--   Data blocks: sorted (key, value, timestamp, tombstone_flag) pairs
--   Index block: sparse index for binary search within data blocks

-- Metadata store (for node membership and key ranges)
CREATE TABLE node_registry (
    node_id      VARCHAR(50) PRIMARY KEY,
    host         VARCHAR(100),
    port         INT,
    token_range  BIGINT[],  -- list of virtual node positions on ring
    status       ENUM('ACTIVE', 'LEAVING', 'JOINING')
);
```

---

## Key Design Decisions

**1. Consistent Hashing with Virtual Nodes**
Simple consistent hashing: each physical node owns one arc. Adding a node only steals from one neighbor → highly uneven distribution. Virtual nodes (150 vnodes/physical node): each physical node owns 150 random points on the ring. Adding a node steals proportionally from all existing nodes → even load distribution. Rebalancing moves ~1/N of total data (not all of it).

**2. LSM-Tree for Write-Optimized Storage**
B-tree on SSDs: random writes cause many small I/Os. LSM converts random writes to sequential: buffer in MemTable → flush as sequential SSTable → compaction merges files sequentially. Write throughput 5-10× better than B-tree on SSDs. Trade-off: reads may need to check multiple SSTable levels (mitigated by Bloom filters).

**3. Quorum Reads and Writes (R=2, W=2, N=3)**
With RF=3: W=2 means 2/3 replicas must ACK before write returns. R=2 means read from 2 replicas, take the version with the latest timestamp. W + R > N = 2 + 2 > 3 → guaranteed overlap: at least one replica has the latest write. This gives strong eventual consistency. If you need strictly strong: W=3 or always read from the leader.

**4. Vector Clocks for Conflict Resolution**
Two concurrent writes to the same key from different clients: which wins? Last-write-wins (LWW) using wall clock timestamps loses data if clocks drift. Vector clocks track causality: `{node1: 3, node2: 1}` means node1 wrote 3 times, node2 once. If vectors can't be ordered (concurrent), expose the conflict to the application (like DynamoDB's `ConditionalCheckFailed`) or use a merge function (CRDTs for counters/sets).

---

## Deep Dives

**Hinted Handoff and Read Repair**
Node B is temporarily down. Write goes to nodes A and C (plus A stores a "hint" that it has B's data). When B recovers, A delivers the hint. This is sloppy quorum — writes always succeed even with node failures, at the cost of slightly stale reads post-recovery.

Read Repair: during a quorum read, if node 1 returns version 5 and node 2 returns version 3, the coordinator returns version 5 to the client AND asynchronously writes version 5 back to node 2. Self-healing without background job.

**Compaction Strategies**
- Size-tiered (Cassandra default): merge SSTables of similar size. Fast writes, but can cause large temporary space amplification during major compaction.
- Leveled (LevelDB/RocksDB): each level has a size cap; L1 files are 10× larger than L0. Better read performance (fewer SSTables to check), better space efficiency. More write amplification.

For a cache-like KV store (many short-lived keys with TTL): leveled compaction drops expired tombstones faster.

---

## Failure Scenarios

| Failure | Impact | Mitigation |
|---------|--------|------------|
| Node failure (1 of 3 replicas) | Reads/writes degrade to 2 replicas | Hinted handoff buffers writes; Read Repair heals on recovery |
| Network partition | AP mode: sloppy quorum continues; data may diverge | Anti-entropy with Merkle trees reconciles diverged data post-partition |
| Hot key (100K reads/sec to one key) | Single node overwhelmed | Read replicas + client-side round-robin; local in-process cache |
| Compaction spike | Read latency increases as compaction saturates I/O | Rate-limit compaction; separate compaction I/O path; monitor pending compactions |
| Bloom filter false positive | Unnecessary disk read | FPR 1% is acceptable; can tune to 0.1% at 3× memory cost |

---

## Interview Questions Asked

### Google
1. **"Design a distributed key-value store like Bigtable — what would you change from a simple DHT approach?"** → Bigtable uses a sorted key space (not consistent hashing) to enable range scans; a master assigns tablet ranges to tablet servers; SSTable files on GFS provide durability. The interviewer is probing whether you know sorted partitioning enables range queries that hash-based DHTs cannot support.
2. **"How does LSM-tree compaction work and when does it cause latency spikes?"** → Minor compaction flushes memtable to L0 SSTables (fast). Major compaction merges L0→L1→L2 SSTables (expensive I/O). Spikes occur during major compaction because the same disks serve both compaction I/O and read I/O. Mitigation: rate-limit compaction, use separate disks for compaction, or use leveled compaction (LevelDB/RocksDB) which limits L0 file count.

### Amazon
1. **"How does DynamoDB's consistent hashing work internally — and what is a virtual node?"** → Physical nodes own token ranges on the ring; each physical node owns multiple virtual nodes (tokens) to distribute load evenly. When a node is added, it steals a fraction of each existing node's range rather than one large contiguous range, achieving better load balance. The interviewer wants to hear about the token assignment strategy and how it minimizes data movement on rebalance.
2. **"DynamoDB offers both eventual and strong consistency reads — how does it implement strong consistency?"** → Strong consistency reads are routed to the partition leader (primary replica). Eventual reads can go to any replica. Strong reads cost 2× read capacity units because they bypass replica caching. The interviewer is testing whether you understand quorum reads vs. leader reads.

### Common Follow-ups
1. **"How do you handle hot keys — e.g., a counter that gets 100K writes/second?"** → Shard the hot key: `counter:shard_0` through `counter:shard_N`, write to a random shard, sum all shards on read. This distributes write load across N partitions. For read-heavy hot keys: add read replicas or use client-side caching with short TTL.
2. **"What are the CAP trade-offs in your design, and when would you flip from AP to CP?"** → Default AP (sloppy quorum, always available during partition, may return stale). Flip to CP when the use case requires strict consistency: financial ledger, distributed locks, leader election. Implement by requiring W + R > N (e.g., W=2, R=2, N=3) — forces quorum overlap at the cost of availability during node failure.
3. **"How does Bloom filter help LSM-tree reads, and what is its false positive rate?"** → On a point read, check the Bloom filter for each SSTable level before doing a disk seek. A false positive causes an unnecessary disk read; a false negative is impossible (Bloom filters never miss true members). At 1% FP rate (10 bits/element), a 1B-key store needs ~1.25 GB for the filter — worth it to avoid disk I/O.
4. **"How would you implement TTL (time-to-live) expiry in an LSM-based KV store?"** → Store expiry timestamp alongside the value. On read: check timestamp, return tombstone if expired. On compaction: drop entries whose TTL has passed. This is exactly how Redis (in-memory) and Cassandra (LSM) both implement TTL — lazy expiry on read + eager cleanup at compaction.

---

## Interviewer Follow-Up Questions

**On data structure and storage:**
- "Your KV store needs to support range queries like 'all keys between A and Z' — how does that change your design?" → Hash maps don't support range queries (keys are unordered after hashing). Switch to a B-tree (or skiplist for in-memory) for the storage engine. LSM trees support range queries via sorted SSTables. Alternatively: keep a hash map for O(1) point reads and a separate sorted index for range queries — double write cost, but both operations are fast.
- "How do you handle large values (e.g., a 10MB value stored in your KV store)?" → Split into chunks: store as multiple keys (`key:part:0`, `key:part:1`, ...) with a metadata key listing chunk count and sizes. Or enforce a hard limit (Redis does 512MB/value but practical limit is much lower for network efficiency). Large values cause network saturation on reads and slow GETs. Often the right answer is: store the large object in S3 and store the S3 key in the KV store.
- "What data structure does Redis use for sorted sets and why?" → Skip list (in-memory) + hash map. Skip list provides O(log N) ordered operations (ZADD, ZRANGE, ZRANK). Hash map provides O(1) point access by member. The dual structure costs extra memory but makes both ordered range queries and point lookups fast. For small sets (< 128 members), Redis compresses to a ziplist/listpack for memory efficiency.

**On consistency and replication:**
- "A write succeeds on the primary, then the primary crashes before replication. The new primary doesn't have the write. What does the client see?" → The client got a success response but the data is lost — this is the async replication durability gap. Prevention: `WAIT 1 0` in Redis forces synchronous replication to at least one replica before returning success. Trade-off: higher write latency. For durable writes (financial data): sync replication or write to a WAL that's synced before ACK.
- "How do you implement linearizable reads in a system with replicas?" → Option 1: always read from the leader (loses the point of read replicas). Option 2: read from a replica only if its replication offset is >= the offset returned by the last write (read-your-writes). Option 3: quorum reads (R + W > N) — majority of replicas must agree on the value. Each option trades latency for consistency level.
- "Sloppy quorum vs. strict quorum — explain the difference." → Strict quorum: writes/reads must involve exactly the N nodes responsible for the key. If any are unavailable, the operation fails. Sloppy quorum: during a partition, writes go to any N available nodes (including non-home nodes as hints), stored as "hinted handoff." On recovery, hinted writes are forwarded to the home node. Higher availability, but violates quorum guarantees — reads may miss recent writes.

**On eviction and memory:**
- "You have 10GB of RAM and 50GB of data. How do you decide which keys to evict?" → LRU (Least Recently Used) is the default — evict keys not accessed recently. LFU (Least Frequently Used) is better for workloads where some keys are accessed rarely but must not be evicted (config keys accessed once at startup). Redis supports: `allkeys-lru`, `allkeys-lfu`, `volatile-lru` (only keys with TTL). For cache use cases: `allkeys-lru`. For mixed (cache + persistent): `volatile-lru`.
- "How does a hot key problem manifest and how do you fix it?" → One key gets 100K reads/sec — that node's CPU and network become bottlenecks while others are idle. Detection: monitor per-key access frequency (`redis-cli --hotkeys`). Fix: read replicas + client-side round-robin across replicas for hot keys; local in-process cache with short TTL in each application server (reduces Redis traffic entirely for the hottest keys); shard the hot key by appending a random suffix and aggregating on read.

**On operations:**
- "How do you upgrade the KV store without downtime?" → Rolling upgrade: upgrade one node at a time, ensure protocol backward compatibility between old and new versions. For a single-node store: write-ahead log ensures durability across restarts. For a cluster: take one node out of rotation, upgrade, verify, re-add. Never upgrade the leader first — upgrade followers, promote a follower, then upgrade the old leader.
