# Design a Key-Value Store

> **Difficulty**: Easy
> **Topics**: Consistent Hashing, Replication, CAP Theorem, LSM-Trees
> **Time**: 60 minutes
> **Companies**: Amazon (DynamoDB), Meta, Google, Microsoft

---

## Problem Mindmap

```
Distributed Key-Value Store
├── Problem Constraints
│   ├── Scale → 10M QPS (9:1 read:write), 1B keys, 1.32TB with RF=3 replication
│   ├── Latency target → < 10ms p99 reads, eventual consistency acceptable
│   └── Core hardness → partition tolerance + consistent hashing for even load + anti-entropy for replica divergence
├── Architecture Derivation
│   ├── Step 1 → Single server HashMap → works to ~1M keys; no fault tolerance, no horizontal scale
│   ├── Step 2 → Hash(key) % N shards → works until a node is added/removed; all keys remap (avalanche)
│   ├── Step 3 → Consistent hashing ring → only K/N keys remap on node change; add 150 virtual nodes/server for evenness
│   └── Step 4 → RF=3 with quorum (R=2, W=2, R+W>N=3) → tolerate 1 node failure without data loss
├── Core Components
│   ├── Consistent hash ring → CRC32(key) on 0..2^32 ring; 150 vnodes/server; next 3 nodes = replicas
│   ├── LSM-tree storage → MemTable (write) + WAL (durability) + SSTables (disk); Bloom filter skips cold reads
│   ├── Quorum coordinator → routes GET/PUT to RF=3 replicas; returns after R or W acks
│   ├── Vector clocks → per-key version tracking across replicas; detect concurrent writes → last-write-wins or app-level merge
│   └── Merkle trees → per-replica tree for each key range; anti-entropy compares root hashes to find diverged subtrees
├── Data Model
│   ├── SSTable → immutable sorted file; key → (value, timestamp, tombstone flag); indexed by Bloom filter per level
│   └── WAL → append-only log: (seq_no, key, value, op_type); replayed on crash recovery before MemTable rebuild
├── APIs
│   ├── PUT /kv/{key} body:{value} → 200 OK or 503 (quorum not met)
│   ├── GET /kv/{key} → {value, version} or 404
│   └── DELETE /kv/{key} → tombstone write (logical delete); physical delete on compaction
├── Critical Trade-offs
│   ├── CP vs AP → AP chosen (like Dynamo); prefer availability over strong consistency; tunable quorum
│   ├── LSM vs B-Tree → LSM chosen; write-optimized (sequential disk writes); B-Tree better for read-heavy
│   └── Last-write-wins vs CRDT → LWW default; vector clocks exposed to client for conflict resolution at app layer
├── Failure Scenarios
│   ├── Node failure → coordinator reroutes to next replica on ring; hinted handoff queues writes for recovery
│   ├── Network partition → quorum reads/writes may fail; client retries; stale reads possible with R=1
│   └── Compaction I/O spike → tiered compaction limits concurrent merges; read path unaffected (Bloom filter + index)
└── Interview Angles
    ├── Amazon DynamoDB → "Design DynamoDB" → consistent hashing + quorum + vector clocks = core answer
    ├── Deep-dive → "How does Bloom filter help?" → avoids disk reads for missing keys; 1% FPR = 10 bits/key
    └── Follow-up → "What is hinted handoff?" → coordinator stores write locally for unavailable replica, replays on recovery
```

---

## What Breaks Without This System

Every microservice in your stack relies on a single-node key-value store: session tokens, feature flags, distributed locks, and rate-limit counters all live there. Traffic is 1M writes/sec on a node running PostgreSQL with a B-tree index. By month 3, disk I/O wait climbs to 80%: every write requires a random seek to the right B-tree page, and at 1M writes/sec that's 1M random disk seeks per second — mechanical disks max out at ~200 and even NVMe saturates around 600K IOPS under mixed workload. Latency spikes from 2ms to 400ms. Distributed locks start timing out. Feature flag reads fail. Half your services start returning 503s, not because of a code bug, but because the storage layer can't absorb sequential writes.

Six months later you've solved I/O with an LSM-tree, but the dataset has grown to 1 billion keys at ~220 bytes each — 220 GB, saturating a single machine's RAM and spilling onto disk. Adding a second node with `hash(key) % 2` remaps 50% of all keys instantly: every cache is cold, the database gets hammered by a thundering herd, and your on-call engineer is manually redirecting traffic at 2 AM.

That is why this system needs to be designed carefully from the start.

---

## Derive the Architecture

**Start with 1 node + B-tree storage** — a single server with an in-memory hash map flushed to a B-tree on disk. Works fine at 10K writes/sec.

**What breaks at 1M writes/sec?** B-tree writes are random I/O: inserting a key means finding its sorted position on disk and writing in-place. NVMe at mixed read/write saturates around 600K IOPS; at 1M writes/sec plus 9M reads/sec, write latency climbs to 200–400ms. **Fix: LSM-tree.** All writes go to an in-memory MemTable (sorted), flushed to immutable SSTables sequentially. Write latency drops to <1ms because sequential writes are 10–100× faster than random writes. Read amplification is handled by Bloom filters per SSTable level (1% false positive rate, ~10 bits/key, costs 1.2 GB for 1B keys).

**What breaks when you have 1 billion keys?** At 220 bytes/key, that's 220 GB — beyond a single machine's RAM and fast-disk budget. You need to shard. Simple `hash(key) % N` means adding one node remaps `(N-1)/N ≈ 50–67%` of all keys simultaneously, causing a cache-miss thundering herd. **Fix: consistent hashing with virtual nodes.** Each physical node owns 150 virtual nodes on a hash ring. Adding a node moves ~1/N of keys (not 50%). Each node knows the ring via gossip protocol — no central coordinator.

**What breaks when a storage node crashes?** That node's key range is gone. Any GET that lands on its virtual nodes returns a miss or error. **Fix: replication factor RF=3.** Each key is stored on the 3 clockwise successor nodes on the ring. Coordinator node writes to all 3; with quorum W=2, R=2 (R+W > N=3), you can tolerate 1 node failure while maintaining consistency.

**What breaks when two replicas accept writes to the same key during a partition?** Node A updates `user:99 → {"balance": 100}` and Node B updates the same key to `{"balance": 150}` while they're partitioned. When the partition heals, both values exist — last-write-wins (LWW) silently discards one. **Fix: vector clocks** track causal history per key: `[NodeA:2, NodeB:1]` vs `[NodeA:1, NodeB:2]`. If clocks diverge (true conflict), surface it to the client for semantic resolution. DynamoDB uses LWW by default with the option for vector clocks; Riak defaults to vector clocks.

**What breaks when a node is temporarily down during a write?** With strict quorum, W=2 out of 3 replicas must acknowledge — if 2 replicas are up, the write succeeds. But the 3rd replica is now stale. **Fix: sloppy quorum + hinted handoff.** Accept the write on any available node, store a hint that it should be forwarded to the original replica once it recovers. This trades strong consistency for availability during transient failures (AP choice on the CAP spectrum).

**Resulting architecture:**

```
Client → Request Router (consistent hashing ring, gossip-maintained)
       → Coordinator Node
       → Storage Nodes (LSM-tree: MemTable + WAL + SSTables + Bloom filters)
                      × RF=3 replicas, quorum R=2/W=2
                      + hinted handoff for transient failures
                      + vector clocks for conflict detection
```

---

## Why This Is Hard

1. **CAP theorem forces a choice**: During a network partition (two buildings lose their connection), you must choose: do the rooms that can still be reached keep accepting coats (Availability), or do they refuse until contact is restored (Consistency)? You can't have both.
2. **Consistent hashing complexity**: Simple `hash(key) % N` means adding one new node remaps almost all keys, causing a massive cache miss storm. You need consistent hashing to minimize key movement.
3. **Conflict resolution**: If the network split and two clients both updated the same key on different replicas, which value wins when the partition heals? Last-write-wins loses data. Vector clocks are correct but complex.
4. **Write performance at scale**: Random disk writes are slow. Writing to a B-tree involves seeking to the right position. At 1M writes/sec, you need sequential writes — enter LSM-trees.
5. **Read amplification**: LSM-trees are write-optimized but read-pessimized. A GET may need to check the MemTable, then Level-0 SSTables, then Level-1, then Level-2... Adding Bloom filters per level reduces this dramatically.

---

## Requirements

### Functional Requirements
1. **PUT(key, value)**: Store key-value pair
2. **GET(key)**: Retrieve value for a key
3. **DELETE(key)**: Remove a key-value pair
4. Support for configurable TTL (Time-To-Live)
5. Support for atomic compare-and-swap (CAS) operations

### Non-Functional Requirements
1. **High availability**: 99.99% uptime
2. **Partition tolerance**: Continue operating during network partitions
3. **Low latency**: < 10ms for GET, < 50ms for PUT
4. **Scalability**: Store petabytes of data, handle millions of QPS
5. **Tunable consistency**: Support both strong and eventual consistency

---

## Capacity Estimation

### Traffic Estimates
- **Peak QPS**: 10 million requests/sec
- **Read:Write ratio**: 9:1 (90% reads, 10% writes)
- **Reads**: 9M/sec
- **Writes**: 1M/sec

### Storage Estimates
- **Average key size**: 20 bytes
- **Average value size**: 200 bytes
- **Total entries**: 1 billion keys
- **Storage needed**: 1B × (20 + 200 bytes) = **220 GB**
- **With replication (3×)**: 660 GB
- **With overhead (2×)**: **1.32 TB**

### Memory Estimates
- **Cache 20% hot data**: 220 GB × 0.2 = **44 GB per node**
- **For 10 nodes**: 440 GB total cache

---

## API Design

### 1. Basic Operations
```http
PUT /v1/kv/{key}
Content-Type: application/json
{
  "value": "data",
  "ttl": 3600  // seconds, optional
}
Response: 200 OK {"version": 5}

GET /v1/kv/{key}
Response: 200 OK {"value": "data", "version": 5}

DELETE /v1/kv/{key}
Response: 204 No Content
```

### 2. Atomic Compare-And-Swap
```http
POST /v1/kv/{key}/cas
{
  "expectedVersion": 5,
  "newValue": "updated_data"
}
Response: 200 OK (success) or 409 Conflict (version mismatch)
```

### 3. Batch Operations
```http
POST /v1/kv/batch
{
  "operations": [
    {"op": "PUT", "key": "k1", "value": "v1"},
    {"op": "GET", "key": "k2"},
    {"op": "DELETE", "key": "k3"}
  ]
}
```

---

## High-Level Architecture

```
┌──────────┐   ┌──────────┐
│ Client 1 │   │ Client 2 │
└─────┬────┘   └────┬─────┘
      │              │
      └──────┬───────┘
             ▼
┌────────────────────────────┐
│   Request Router           │  (Consistent Hashing Ring)
│   hash(key) → Node         │
└──────┬─────────────────────┘
       │
  ┌────┼─────────────┐
  ▼    ▼             ▼
┌────┐ ┌────┐    ┌────┐
│ N1 │ │ N2 │    │ N3 │   Physical Storage Nodes
└──┬─┘ └──┬─┘    └──┬─┘
   │      │          │
   │   Replicate     │
   │   (RF=3)        │
   ▼      ▼          ▼
┌────┐ ┌────┐    ┌────┐
│R1-1│ │R2-1│    │R3-1│   Replica Sets
│R1-2│ │R2-2│    │R3-2│
└────┘ └────┘    └────┘

┌──────────────────────────┐
│ Metadata Service         │  (ZooKeeper/etcd: cluster membership, ring state)
└──────────────────────────┘
```

---

## Core Components

### 1. Consistent Hashing & Virtual Nodes

**The problem with naive hashing:**
```
3 nodes: hash(key) % 3 → Node 0, 1, or 2
Add 4th node: hash(key) % 4 → Almost all keys remap to new nodes
Result: Massive cache miss storm, entire dataset rebalances
```

**Consistent hashing solution:**
```java
public PhysicalNode getNode(String key) {
    int hashValue = hash(key) % RING_SIZE;  // 0-999

    // Find next virtual node on ring (clockwise)
    for (VirtualNode vnode : sortedVnodes) {
        if (vnode.getPosition() >= hashValue) {
            return vnode.getPhysicalNode();
        }
    }

    // Wrap around to first vnode
    return sortedVnodes.get(0).getPhysicalNode();
}
```

**Why Virtual Nodes?**
- Each physical node owns multiple virtual nodes (256 vnodes is typical)
- When a node is added, it takes over only its vnodes' ranges — minimal data movement
- Without vnodes, removing a node puts all its load on the next node (hot spot)
- With vnodes, the load distributes across all remaining nodes

```
Ring (conceptual):
 N_A vnode1 ── N_B vnode1 ── N_C vnode1 ── N_A vnode2 ── N_B vnode2 ── ...
 
Adding Node D: It gets assigned N random positions on the ring.
Only keys in those ranges move to D. All other keys stay put.
```

### 2. Replication Strategy

**Replication Factor (RF) = 3**: Primary node + 2 replicas on the next two clockwise nodes.

```
Key: user123 → hash → position 150 on ring
Primary:   Node B (position 150)
Replica 1: Node C (position 300, next clockwise)
Replica 2: Node A (position 450, next clockwise from C)
```

**Three replication modes:**
1. **Synchronous (W=ALL)**: Wait for all 3 replicas to ACK → Strong consistency, higher latency
2. **Asynchronous (W=1)**: Write to primary only, replicate in background → Eventual consistency, lowest latency
3. **Quorum (W=2, R=2)**: Write to 2, read from 2 → Balanced (most common)

### 3. Tunable Consistency (Quorum)

The formula: **R + W > RF** guarantees at least one node in every read set overlaps with every write set — ensuring you always read the latest write.

| Level | R | W | R+W | Use Case |
|-------|---|---|-----|----------|
| **Strong** | 3 | 3 | 6 > 3 ✓ | Banking, inventory |
| **Quorum** | 2 | 2 | 4 > 3 ✓ | Balanced default |
| **Eventual** | 1 | 1 | 2 < 3 | Maximum throughput |

```
RF=3 cluster:

Strong consistency:  R=3, W=3 → Always read all 3, write all 3
                     Cost: Latency = slowest of 3 nodes

Quorum:              R=2, W=2 → Overlap guaranteed
                     Cost: Must wait for 2 of 3 nodes

Eventual:            R=1, W=1 → No overlap guarantee
                     Cost: May read stale data
```

---

## Detailed Workflows

### Write Path (PUT)

```
1. Client → PUT(key, value)
2. Router: hash(key) → Node B (Primary) + Replicas: Node C, Node A
3. Coordinator (Node B):
   a. Write to in-memory MemTable (fast, ~1μs)
   b. Append to Write-Ahead Log (WAL) for durability
   c. Propagate to RF-1 replicas (async or sync based on consistency level)
4. If W=2 (quorum):
   - Wait for ACK from 1 replica (primary + 1 = 2 total)
   - Return success to client
5. Third replica ACKs asynchronously

Optimization: Hinted Handoff
If Replica 1 is down:
   → Store a "hint" on Replica 2: "I have writes for Replica 1"
   → When Replica 1 recovers, Replica 2 replays the missed writes
```

### Read Path (GET)

```
1. Client → GET(key)
2. Router: hash(key) → Replicas: Node B, Node C, Node A
3. If R=2 (quorum):
   a. Send parallel reads to Node B and Node C
   b. Both respond: {value: "v1", version: 5}
   c. Versions match → Return "v1" to client

Version mismatch (Read Repair):
   a. Node B: {value: "v1", version: 5}
   b. Node C: {value: "v2", version: 6}  ← more recent
   c. Return "v2" to client (latest version)
   d. Asynchronously: Update Node B to version 6
   This is Read Repair — it heals stale replicas lazily during reads
```

**Read Repair explanation:** Replicas diverge silently during network hiccups. Without repair, stale data lingers forever. Read Repair fixes divergence lazily — on the next read of that key. Anti-entropy (Merkle trees) handles keys that are never read.

---

## Conflict Resolution

### Vector Clocks

When two clients write to the same key concurrently on different replicas (during a partition), you get conflicting versions. Timestamps alone can't resolve this because clocks drift.

```
D1: Node A writes → vector clock: [A:1]
D2: Node A writes again → vector clock: [A:2]
D3: Node B writes (descends from D2) → vector clock: [A:2, B:1]
D4: Node C writes (descends from D2, concurrent with D3) → vector clock: [A:2, C:1]

D3 and D4 are CONCURRENT (neither is an ancestor of the other)
→ System returns both to client, client must resolve

D5: Client merges D3+D4, writes → vector clock: [A:3, B:1, C:1]
```

**Resolution Strategies:**
1. **Last Write Wins (LWW)**: Use timestamp — simple, loses data on concurrent writes
2. **Client-side merge**: Return conflicts, let application decide (Amazon cart: merge item sets)
3. **CRDTs**: Conflict-free Replicated Data Types — mathematically correct merges without coordination (e.g., distributed counters, sets)

**Comparison rule for vector clocks:**
- Clock X is an ancestor of Y (no conflict) if: for every node `i`, `X[i] <= Y[i]`
- Conflict detected: X has some elements greater than Y AND Y has some elements greater than X

---

## Failure Handling

### 1. Node Failure

**Detection:** Gossip protocol — each node pings neighbors every 1 second. Three consecutive missed heartbeats = node marked down.

**Recovery path:**
```
Node fails → Gossip marks it as suspect → Confirm failure → Mark dead
↓
Hinted Handoff: Temporary replicas take writes meant for failed node
↓
Node recovers → Temporary replicas replay missed writes (from hints)
↓
Anti-entropy repair: Merkle tree comparison catches any remaining gaps
```

### 2. Network Partition

During a partition, the Availability vs. Consistency trade-off is forced:

**Sloppy Quorum (favor Availability):**
- If the "preferred" nodes for a key are unreachable, write to the next N healthy nodes
- These are "temporary replicas"
- When partition heals, temporary replicas transfer their writes to the original nodes
- Used by: Amazon DynamoDB, Cassandra

**Strict Quorum (favor Consistency):**
- If you can't reach W/R required nodes, reject the request (return error)
- Used by: etcd, ZooKeeper, Google Spanner

### 3. Data Corruption

- **Checksum verification**: Every value stored with CRC32 checksum; verified on every read
- **Merkle trees for anti-entropy**: Background process comparing replica contents

---

## Storage Engine: LSM-Tree

### Why Not B-Tree?

B-tree requires random writes (updating the tree structure on disk for each insert). At 1M writes/sec:
- Random I/O: HDD ~100 IOPS = terrible. Even SSD ~50K IOPS gets saturated.
- LSM-tree converts random writes into sequential writes — orders of magnitude faster.

### LSM-Tree Write Path

```
Write Request
     ↓
MemTable (In-Memory Red-Black Tree, ~256MB)
     ↓ [When full]
Flush to immutable SSTable on disk (Level 0)
     ↓ [Background compaction]
Merge SSTables → Level 1 (sorted, no overlaps)
     ↓
Level 2 (larger, sorted)
     ...

SSTable: Sorted String Table — immutable, binary-searchable
WAL: Write-Ahead Log — append-only, for crash recovery
```

### LSM-Tree Read Path

```
GET(key):
1. Check MemTable (most recent — in memory, fast)
2. If not found, check each SSTable level (L0 → L1 → L2 → ...)
3. Each SSTable check:
   a. Check Bloom filter: "Does this SSTable definitely NOT have key?"
      → If no, skip this SSTable entirely
   b. If maybe yes, binary search index block
   c. Fetch data block

Bloom filter: Probabilistic structure. False positives possible (check SSTable unnecessarily).
             False negatives impossible (never skip an SSTable that has the key).
```

**Why Bloom filters are critical:** Without them, a GET for a non-existent key would scan every SSTable at every level. With them, each level scan is reduced to ~1% false positives — most levels are skipped entirely.

---

## Advanced Features

### 1. TTL (Time-To-Live)

```java
public class Entry {
    private String key;
    private String value;
    private Timestamp expiresAt;

    public Entry(String key, String value, long ttl) {
        this.key = key;
        this.value = value;
        this.expiresAt = new Timestamp(System.currentTimeMillis() + ttl * 1000);
    }
}

// Lazy deletion on read
if (entry.getExpiresAt() != null &&
    entry.getExpiresAt().before(new Timestamp(System.currentTimeMillis()))) {
    return null;  // Key is expired
}

// Active deletion: expired entries removed during SSTable compaction
// The compaction process checks expiresAt and skips expired entries
```

### 2. Compare-And-Swap (CAS)

```java
public boolean cas(String key, int expectedVersion, String newValue) {
    // This must be atomic — use a distributed lock or Paxos round
    Entry current = db.get(key);

    if (current.getVersion() == expectedVersion) {
        db.put(key, newValue, current.getVersion() + 1);
        return true;
    } else {
        return false;  // Concurrent update happened — retry
    }
}
```

**Use case:** Distributed locks, leader election, optimistic concurrency control

---

## Scalability

### Horizontal Scaling

When a new node joins:
```
Before (3 nodes, consistent hash ring):
N1: positions [100, 400, 700]
N2: positions [200, 500, 800]
N3: positions [300, 600, 900]

After (N4 joins, takes 1/4 of each node's vnodes):
N4: positions [150, 350, 650, 850]
Keys in ranges [100-150], [300-350], [600-650], [800-850] migrate to N4
All other keys stay put (only ~25% of data moves)
```

### Partitioning Strategies

1. **Hash-based (consistent hashing)**: Uniform distribution, no range queries possible
2. **Range-based**: Keys sorted and split into ranges — supports range scans (e.g., DynamoDB sort keys), but risk of hot partitions
3. **Hybrid**: Partition by hash(user_id), sort by timestamp within partition — supports `WHERE user_id = X ORDER BY time`

---

## Monitoring & Operations

### Key Metrics
- **Latency**: p50, p99, p999 for GET/PUT
- **Throughput**: QPS per node
- **Storage**: Disk usage, compaction lag (if compaction falls behind writes, reads slow down)
- **Replication lag**: Time delta between primary and replicas

### Anti-Entropy with Merkle Trees

Background process comparing replicas to catch inconsistencies missed by Hinted Handoff or Read Repair:

```java
public MerkleTree buildMerkleTree(List<Entry> partitionData) {
    // Hash each key-value pair → leaf node
    // Hash pairs of leaf nodes → parent node
    // Continue until root
    List<String> leaves = partitionData.stream()
        .map(entry -> hash(entry.getKey() + entry.getValue()))
        .collect(Collectors.toList());
    return buildTree(leaves);
}

// Compare with replica
if (!localMerkleRoot.equals(replicaMerkleRoot)) {
    // Root differs → traverse tree to find diverging subtrees
    // Only transfer hashes (not data) until you find the differing leaves
    // Then transfer just the differing keys — not the entire dataset
    syncDiff(localTree, replicaTree);
}
```

**Why Merkle trees?** Without them, detecting divergence requires transferring the entire dataset across the network for comparison — impractical for petabyte-scale systems. A Merkle tree lets you locate exactly which keys differ by transferring only O(log N) hashes, then O(diverged keys) data.

---

## Trade-offs

| Aspect | Choice | Trade-off |
|--------|--------|-----------|
| **Consistency** | Tunable (Quorum) | Latency increases with higher consistency (W=ALL is slow) |
| **Replication** | Async by default | Speed vs. durability (crash between write and replication = data loss) |
| **Storage Engine** | LSM-tree | Write-optimized → Read amplification (mitigated by Bloom filters) |
| **Partitioning** | Consistent hashing | Uniform distribution but no range queries |
| **Conflict Resolution** | Last-Write-Wins default | Simplicity vs. occasional data loss on concurrent writes |
| **Partition handling** | Sloppy quorum (AP) | Always available vs. may serve stale data |

---

## Real-World Implementations

| System | Consistency | Architecture | Notes |
|--------|-------------|--------------|-------|
| **Amazon DynamoDB** | Tunable (Eventual default) | Multi-master, sloppy quorum | AP system |
| **Apache Cassandra** | Tunable | Wide-column, gossip | AP by default, CP configurable |
| **etcd** | Strong (Linearizable) | Raft consensus | CP system, used for K8s config |
| **Redis Cluster** | Eventual | Hash slots, master-replica | CP if using WAIT command |

---

## Interview Discussion Points

**Q: CAP Theorem — What does your design prioritize?**
- **Default: AP** (Availability + Partition tolerance) — sloppy quorum, eventual consistency
- **Configurable: CP** with `R=ALL, W=ALL` — sacrifices availability during partitions
- Real answer: "CA" is impossible in a distributed system. You always face CA vs. CP during partitions.

**Q: How to handle hot keys?**
- **Read hotspots**: Aggressive caching, add read replicas, client-side caching with TTL
- **Write hotspots**: Shard the hot key: `counter:global:shard_0` through `counter:global:shard_N`, sum on read

**Q: Why LSM-tree over B-tree?**
- **Writes**: LSM converts random writes to sequential (10× faster on HDD, 2× on SSD)
- **Reads**: B-tree is faster (direct tree lookup vs. multi-level SSTable scan)
- **Choice**: Write-heavy workloads → LSM. Read-heavy → B-tree. KV stores are typically write-heavy.

**Q: How does Read Repair work at scale?**
- Only repairs keys that are actively read — cold keys may stay stale
- Anti-entropy (Merkle tree) is the backstop for keys that are never read
- Together they guarantee eventual consistency without requiring synchronous replication

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
