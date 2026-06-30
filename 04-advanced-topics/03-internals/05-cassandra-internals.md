> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Cassandra internals — how a leaderless, ring-based distributed database achieves massive write throughput and tunable consistency using an LSM tree.
>
> **Key topics:**
> - Ring architecture: consistent hashing places nodes on a ring; partition key hash determines owning node; no single master
> - Write path: Commit Log (durability) → MemTable (in-memory) → SSTable (immutable on-disk); all writes sequential = very fast
> - Replication: replicate to N adjacent nodes in ring; reads from R nodes; W+R>N = quorum consistency
> - Consistency levels: ONE (fastest, risk stale), QUORUM (balanced), ALL (strongest, risk availability); tunable per operation
> - Compaction: SSTables accumulate → compaction merges and deduplicates; Size-Tiered vs Leveled strategies
> - Anti-entropy: read repair (fix inconsistency on read), hinted handoff (buffer writes for down nodes), Merkle tree sync (periodic)
> - Data modeling: primary key = partition key + clustering columns; design tables around queries, not entities
>
> **Key takeaway:** Cassandra's write speed comes from sequential disk I/O (MemTable flush) — the trade-off is eventual consistency and no cross-partition transactions; model your data around your read patterns.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Cassandra Internals

> A leaderless, peer-to-peer wide-column store optimized for write-heavy workloads at petabyte scale. Tunable consistency per query.

---

## 1. Why Cassandra Exists

**Question**: A single Oracle instance can write ~10K-50K rows/sec before lock contention dominates. You need 1M writes/sec, multi-region replication, and zero downtime even when an entire data center is offline. How?

**Physical constraint**: Single-node write throughput is bounded by disk I/O and lock contention on the global write-ahead log. Multi-master replication requires conflict resolution. Strong consistency across data centers has fundamental latency limits (speed of light).

**Minimal solution**: Shard the data by partition key across many nodes. Use consistent hashing so adding a node only moves K/N of the data. Accept eventual consistency. No single point of failure means no primary — every node can accept writes.

**Production generalization**: Cassandra (2008, originally Facebook) combined ideas from Dynamo (Amazon, 2007) and BigTable (Google, 2006): Dynamo-style peer-to-peer ring with BigTable-style LSM storage. Tunable consistency, multi-DC replication, and CQL as a SQL-like query language.

---

## 2. Core Concepts

| Concept | Definition |
|---------|------------|
| **Cluster** | Set of nodes that share the same keyspaces |
| **Node** | Single Cassandra instance |
| **Keyspace** | Top-level namespace; defines replication strategy and factor |
| **Table** | Collection of rows; equivalent to a column family in older docs |
| **Partition Key** | Determines which node(s) own the data via `hash(partition_key)` |
| **Clustering Key** | Sort order of rows within a partition; enables range queries |
| **Primary Key** | Partition Key + Clustering Key |
| **vnode (virtual node)** | Each physical node owns 256 ring positions → even distribution on add/remove |
| **Coordinator** | The node a client connects to; routes the request to the owning replicas |

### Ring Architecture

- Nodes are arranged on a consistent hash ring (`0` to `2^127 - 1` for Murmur3)
- Each node owns multiple **vnodes** (256 by default)
- Data with `hash(partition_key)` is owned by the next clockwise node
- Adding a node: it claims some vnode positions; the data migrates only for those positions
- Removing a node: its vnodes are absorbed by their clockwise neighbors

For consistent hashing details, see [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md).

### Data Model Principle: Model by Query

Cassandra has no JOINs. There are no multi-partition transactions. Every query must hit a single partition. Therefore, design tables around the queries, not around the entities:

```sql
CREATE TABLE user_events (
    user_id     UUID,        -- partition key
    event_time  TIMESTAMP,   -- clustering key
    event_type  TEXT,
    payload     TEXT,
    PRIMARY KEY (user_id, event_time)
) WITH CLUSTERING ORDER BY (event_time DESC);

-- "Get last 100 events for user 123" → fast single-partition query
-- "Get events across all users" → IMPOSSIBLE without a separate table
```

**Common pattern**: create one table per access pattern, denormalized. Storage is cheap; queries must be fast.

---

## 3. Write Path

```
Client write
    ↓
1. Append to Commit Log (sequential disk; durability)
    ↓
2. Update MemTable (in-memory sorted structure per table)
    ↓
3. ACK to client
    ↓ (asynchronously, when MemTable fills ~128 MB)
4. Flush MemTable to disk as immutable SSTable
```

**Why this is fast**:
- Sequential writes to Commit Log (no disk seeks)
- In-memory MemTable writes (no disk I/O)
- No read-before-write (unlike B-trees, which must read a page before modifying it)
- No lock on the data path (each partition is independent)

### Commit Log

- One commit log per node, shared by all tables
- Sequential append; oldest segments archived or truncated after all MemTables are flushed
- On crash recovery, replay the Commit Log to rebuild MemTables not yet flushed

### MemTable

- In-memory sorted structure (skiplist or Chuckwagon) per table
- Per-partition: backed by an in-memory index
- Flush threshold: `memtable_total_space_in_mb` (default: 1/4 of heap)

### SSTable (Sorted String Table)

- Immutable, sorted on-disk file
- Contains: data block (sorted by partition key + clustering key), sparse index, Bloom filter
- Once written, never modified (until compaction)

---

## 4. Read Path

Data may live in:
- MemTable (most recent)
- Multiple SSTables (older data, including compacted ones)
- Each replica may have different SSTables

```
1. Check MemTable
2. Check Bloom filter for each SSTable → skip definite misses
3. Check Key Cache (partition key → SSTable offset)
4. Read Partition Summary → narrow byte range
5. Read Partition Index → binary search to exact offset
6. Read Data block (sequential scan within)
7. Merge results from MemTable + all matching SSTables
8. Return most recent version (by timestamp)
```

### Bloom Filter

Probabilistic structure per SSTable:
- "Definitely not in this SSTable" → skip (100% accurate)
- "Maybe in this SSTable" → read it (false positive possible)

Tunable false-positive rate (~1% by default). 1% FPR = 10 bits/key.

### Read Latency

- MemTable hit: ~0.1 ms
- SSTable hit (in page cache): ~1 ms
- SSTable hit (from disk): ~10 ms
- Multiple SSTables to merge → latency = sum of those reads

This is why compaction matters: more SSTables = more reads per query.

---

## 5. Replication & Tunable Consistency

```
Replication Factor (RF) = number of copies of each partition
Coordinator = the node the client connects to (any node)
Replicas    = the RF nodes that own the partition (hash → clockwise)
```

### Consistency Levels

| Level | Reads/Writes | Trade-off |
|-------|--------------|-----------|
| `ONE` | 1 replica responds | Fastest; may be stale |
| `TWO`, `THREE` | N replicas | Linear increase in latency/strength |
| `QUORUM` | `RF/2 + 1` replicas | Strong consistency for RF=3 (2+2>3) |
| `LOCAL_QUORUM` | Quorum in local DC | Multi-DC; avoids cross-DC write latency |
| `EACH_QUORUM` | Quorum in every DC | Strongest multi-DC |
| `ALL` | All RF replicas | Strongest; unavailable if any replica is down |

### The R + W > N Rule

```
N = Replication Factor (total copies)
W = number of replicas that must acknowledge a write
R = number of replicas that must respond to a read

If R + W > N, strong consistency is guaranteed:
  At least one node in the read set has the latest write
  (Pigeonhole principle)

For RF=3:
  W=QUORUM(2) + R=QUORUM(2)  →  2+2>3  → strong
  W=ONE(1) + R=ONE(1)        →  1+1=2<3  → eventual (stale read possible)
```

### Per-Query Consistency

Set the consistency level per query, not per table:

```sql
-- Strong consistency for the order write
INSERT INTO orders (...) VALUES (...) USING TIMESTAMP ?;

-- Consistency level set at driver level
ConsistencyLevel cl = ConsistencyLevel.LOCAL_QUORUM;
```

**Sloppy quorum** (`CL=ANY`): the coordinator may write to a different replica if the natural owner is down, plus a hint for the natural owner. Trades strictness for availability.

---

## 6. Compaction

SSTables accumulate. Reads get slower. Space grows. Compaction merges SSTables, removes tombstones, deduplicates by timestamp.

| Strategy | Behavior | Best For |
|----------|----------|----------|
| **STCS** (Size-Tiered Compaction Strategy) | Merge SSTables of similar size | Write-heavy; time-series |
| **LCS** (Leveled Compaction Strategy) | SSTables organized in levels, fixed size, non-overlapping | Read-heavy; predictable latency |
| **TWCS** (Time-Window Compaction Strategy) | Group SSTables by time window; whole window expires | Time-series with TTL |

**Write amplification**: each logical write may be rewritten 10-50× across compaction rounds. Plan disk capacity accordingly.

**Compaction throughput** (`compaction_throughput_mb_per_sec`): cap compaction I/O so it doesn't starve foreground reads/writes. Default 16 MB/s.

---

## 7. Tombstones & Deletes

Cassandra SSTables are **immutable**. Deletes don't remove rows; they write a **tombstone** marker with a timestamp.

**Tombstone lifecycle**:
1. Delete → write tombstone with current timestamp
2. Reads suppress older versions because of the tombstone
3. After `gc_grace_seconds` (default 10 days), compaction physically removes the tombstone

**Why gc_grace_seconds=10 days**: the grace period ensures all replicas receive the tombstone before it's permanently removed. Otherwise a replica that was down could resurrect the deleted data on recovery.

**Zombie problem**: if a replica is down longer than `gc_grace_seconds`, it may return "deleted" data when it recovers. Mitigation: run `nodetool repair` within the grace period for any node that was down.

**Tombstone overload**: too many accumulated tombstones slow reads (read path must scan and skip each one). Monitor with `nodetool cfstats`. Mitigation: use TTL on data, especially with TWCS where the whole window drops atomically.

---

## 8. Anti-Entropy & Self-Healing

### Hinted Handoff

If the natural replica is down for a write, the coordinator stores a **hint** (locally, in the coordinator's `hints` directory) saying "deliver this to node X when it returns." Delivered when the node comes back (max 3 hours by default; longer requires `nodetool repair`).

### Read Repair

During a read at QUORUM, the coordinator reads from 2 of 3 replicas. If they diverge:
1. Return the most recent value (by timestamp) to the client
2. Asynchronously write the latest value to the stale replica (background repair)

### Anti-Entropy Repair (Merkle Trees)

For nodes that have been down too long for hints, or for tables with low read traffic (no Read Repair triggered), run `nodetool repair` periodically.

```
Build Merkle tree per SSTable:
         [hash(all data 0-1M)]
        /                      \
  [hash(0-500K)]            [hash(500K-1M)]
  /       \                   /          \
[h(0-250K)] [h(250-500K)]  [h(500-750K)] [h(750-1M)]

Compare top-down between Node A and Node B:
  Root hashes match? → identical, done (O(1))
  Root hashes differ? → descend:
    Left subtrees match → skip entire left half
    Right subtrees differ → recurse right half
  Until leaf → identify exact token ranges that differ
  Stream only differing rows
```

**Network savings**: instead of streaming 100 GB to compare, exchange O(depth × 1 KB) hashes, then stream only differing MB.

**Repair modes** (Cassandra 4.0+ adds **incremental repair** — only un-repaired SSTables, marks with `repairedAt`):
- `nodetool repair`: full repair of all owned ranges
- `nodetool repair -pr`: primary ranges only (run across all nodes to avoid duplicate work)
- Incremental: much faster; reduces repair time 90%+

**Critical**: run repair within `gc_grace_seconds`. Otherwise tombstones expire and deletes can resurrect.

---

## 9. Gossip Protocol

Nodes discover each other and propagate state via gossip. Every second, each node gossips with up to 3 other nodes.

```
Round 0: Node A has new information X
Round 1: A gossips to {B, C, D}        → 4 nodes know X
Round 2: B,C,D each gossip to 3 more   → 13 nodes know X
Round 3: 13 nodes gossip to 39 more     → ~34 nodes know X
Convergence: O(log N) rounds
At 1000 nodes: ~6.3 rounds × 1s = ~7s
```

**Gossip message types** (3-way handshake):
1. **GossipDigestSyn** — sender's view of self + sampled peers
2. **GossipDigestAck** — receiver's full state + acknowledgment
3. **GossipDigestAck2** — sender's full state update

**State gossipped**: disk load, schema version, datacenter, rack, vnode tokens, status (NORMAL/LEAVING/JOINING).

### Failure Detection: Phi Accrual

```
φ = -log₁₀(P[node still alive | last heartbeat delay])

φ < threshold (default 8): node UP
φ > threshold:             node DOWN

Adaptive: slow-but-alive nodes have rising φ that stays below threshold
          suddenly-dead nodes have φ spike rapidly past threshold
```

Config: `phi_convict_threshold: 8` in `cassandra.yaml`. Higher = more tolerant of slow nodes.

---

## 10. Real-World Usage

| Company | Use case | Notes |
|---------|----------|-------|
| **Netflix** | 1+ trillion writes/day; user activity, viewing history | Multi-DC; EC2 Multi-Region |
| **Apple** | iMessage, Siri | 100K+ nodes reported |
| **Instagram** | Photo metadata, feeds, direct messages | Cassandra + Redis + Postgres |
| **Discord** | Messages (trillions of msgs) | Migrated from MongoDB at scale |
| **Uber** | Trip events, business metrics | Multi-region, used alongside MySQL/Schemaless |

---

## 11. Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **Write scale** | Linear horizontal scaling; sequential I/O | No transactions across partitions |
| **Availability** | No SPOF; survives (RF-1) node failures | Eventual consistency unless using QUORUM |
| **Multi-DC** | Tunable per-DC consistency (LOCAL_QUORUM) | Network latency between DCs |
| **Data modeling** | Query-driven; predictable performance | No JOINs; denormalization is mandatory |
| **Operations** | Peer-to-peer; no failover orchestration | Repair scheduling; tombstone tuning; compaction tuning |

---

## 12. Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| **Hot partition** | One node's disk I/O saturated; latency spike on that key | Improve partition key; salting; check for skewed access |
| **Wide partition** | Reads timeout; coordinator OOM | `LIMIT` queries; redesign with bucketing; bound partition size to <100 MB |
| **Tombstone overload** | Read latency increases linearly with tombstone count | Use TTL; TWCS for time-series; `nodetool compact` to clear |
| **Replica divergence** | Different reads return different values | Run `nodetool repair`; check for clock skew (NTP) |
| **Long GC pause** | Gossip times out → false DOWN detection → topology churn | Tune JVM heap; G1GC; max 8 GB heap |
| **Zombie resurrection** | "Deleted" data reappears after node comes back | Run repair within `gc_grace_seconds`; never let a node be down >10 days |
| **Compaction falling behind** | Disk fills; read latency grows | Increase `compaction_throughput_mb_per_sec`; switch to LCS or TWCS |
| **Gossip partition** | Nodes can't see each other; cluster splits | Network investigation; check firewall and `rpc_address` |

---

## 13. Performance

### Partition Sizing

- **Sweet spot**: < 100 MB per partition; < 100K rows per partition
- **Too small**: too many partitions to scan for a "give me all of user's events" query
- **Too large**: one query loads too much into memory; GC pressure

### Memory

- `MAX_HEAP_SIZE`: 8-16 GB typical (don't go higher; CMS/G1 don't scale well)
- `memtable_total_space_in_mb`: 1/4 of heap
- `commitlog_total_space_in_mb`: 8-32 GB (rotation target)
- Bloom filters and key cache live off-heap

### Compaction Tuning

- `compaction_throughput_mb_per_sec`: 16-32 MB/s typical
- STCS: `min_threshold=4, max_threshold=32`
- LCS: `sstable_size_in_mb=160` per level
- TWCS: `compaction_window_unit=DAYS, compaction_window_size=1`

### Hardware

- **SSDs essential** for compaction I/O and Bloom filter reads
- **RAM**: more = larger MemTable + caches
- **Network**: low-latency between DCs for LOCAL_QUORUM

---

## 14. Implementation Patterns

### Java — DataStax Driver with Tunable Consistency

```java
CqlSession session = CqlSession.builder()
    .addContactPoint(new InetSocketAddress("node1", 9042))
    .withConfigLoader(DriverConfigLoader.programmaticBuilder()
        .withDuration(DefaultDriverOption.REQUEST_TIMEOUT, Duration.ofSeconds(5))
        .withBoolean(DefaultDriverOption.PROTOCOL_COMPRESSION, true)
        .build())
    .build();

// Per-query consistency
SimpleStatement stmt = SimpleStatement.builder("SELECT * FROM orders WHERE user_id = ?")
    .addPositionalValue(userId)
    .setExecutionProfileName("strong")
    .build();

session.execute(stmt);  // uses profile default: LOCAL_QUORUM
```

### CQL — Last-Write-Wins with USIng TIMESTAMP

```sql
-- Only update if local timestamp is newer than stored
UPDATE user_profiles
SET name = ?, email = ?
WHERE user_id = ?
USING TIMESTAMP ?;     -- application-supplied timestamp (microseconds)

-- Read with explicit timestamp for debugging
SELECT name, email, writetime(name) AS name_ts
FROM user_profiles
WHERE user_id = ?;
```

### Lightweight Transactions (Paxos)

For compare-and-set on a single partition:

```sql
-- INSERT only if not exists
INSERT INTO users (email, name) VALUES (?, ?)
IF NOT EXISTS;

-- UPDATE with version check
UPDATE accounts SET balance = ? WHERE id = ?
IF balance = ?;        -- conditional, atomic
```

Internally uses Paxos: 4 round trips for a single CAS. Use sparingly.

---

## Quick Revision

- Cassandra is a leaderless, peer-to-peer wide-column store on a consistent hash ring
- Write path: Commit Log → MemTable → SSTable (all sequential)
- Read path: MemTable + Bloom filter + partition index + merge across SSTables
- Tunable consistency: ONE, QUORUM, ALL, LOCAL_QUORUM, EACH_QUORUM
- R + W > N → strong consistency (Pigeonhole)
- No JOINs; design tables around queries (denormalize)
- Compaction: STCS (write-heavy), LCS (read-heavy), TWCS (time-series)
- Hinted handoff + read repair + Merkle tree anti-entropy = self-healing
- Tombstones have 10-day gc_grace to prevent zombie resurrection
- Phi accrual failure detector outputs continuous suspicion score
- Hot partitions: redesign key; salting; never use sequential UUIDs

---

## See Also

- [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md) — ring partitioning
- [04-advanced-topics/internals/index-structures.md](index-structures.md) — LSM tree deep dive
- [04-advanced-topics/internals/dynamodb-internals.md](dynamodb-internals.md) — similar architecture, AWS managed
- [04-advanced-topics/internals/consensus-protocols.md](consensus-protocols.md) — lightweight Paxos for LWT
- [01-foundations/databases.md](../../01-foundations/databases.md) — SQL vs NoSQL
- [04-advanced-topics/distributed-concepts.md](../distributed-concepts.md) — eventual consistency

---

## Interview Questions Asked

**Q: Why is Cassandra write-fast?**

A: Three reasons. (1) Sequential I/O — every write is an append to the Commit Log, then a write to the in-memory MemTable. No disk seeks, no random I/O. (2) No read-before-write — unlike B-trees, Cassandra never reads a page to modify it. (3) No lock contention — writes touch only the local MemTable; replication is async (or waits for the configured consistency level). LSM tree is write-optimized; the read cost is paid later (multi-SSTable merges, Bloom filter checks).

**Q: Explain Cassandra's tunable consistency with the R + W > N formula.**

A: For a replication factor of N=3, write consistency W and read consistency R. If R + W > N, then at least one node in the read set has the latest write (Pigeonhole). W=QUORUM(2) + R=QUORUM(2) gives strong consistency for RF=3. W=ONE(1) + R=ONE(1) gives eventual consistency — possible to read stale. The application picks the level per query: strong for "is this user already registered?" (QUORUM); eventually consistent for activity feed reads (ONE).

**Q: How does Cassandra handle a hot partition?**

A: Identify the partition key in the access pattern. If the key is skewed (one celebrity user), the natural solution is to redesign the partition key. If you can't (the user_id is fundamental), use **salting**: append a random suffix to the key on write (`user_123_4`), spread across N partitions, then aggregate on read. Alternative: two-stage aggregation — round-robin partials in stage 1, aggregator partition in stage 2. Worst case: dedicated high-traffic topic with dynamic routing. Cassandra's `WRITETIME` and `USING TIMESTAMP` help with last-write-wins ordering.

**Q: How do tombstones work, and what is the zombie problem?**

A: Cassandra's SSTables are immutable — deletes cannot remove rows in place. A delete writes a **tombstone** marker with a timestamp. Reads suppress any older version. After `gc_grace_seconds` (10 days), the tombstone is removed during compaction. The **zombie problem**: if a replica is down longer than the grace period, it may return the "deleted" row on recovery (because its SSTable still has the original row, and the tombstone has been compacted away on other replicas). Mitigation: run `nodetool repair` on any node that's been down, within the grace window. Repair syncs the data including tombstones.

**Q: Cassandra vs DynamoDB?**

A: Same lineage (Dynamo paper + BigTable), similar write paths (LSM), similar consistency model (tunable per query). Differences: Cassandra is self-managed, open-source, runs anywhere (multi-cloud, on-prem), supports CQL with some query flexibility. DynamoDB is fully managed, AWS-native, single-digit-ms latency at any scale, no operational overhead, but lock-in to AWS and no multi-cloud. Choose Cassandra for multi-cloud or on-prem control with ops capacity; DynamoDB when you're AWS-native and want zero ops.
