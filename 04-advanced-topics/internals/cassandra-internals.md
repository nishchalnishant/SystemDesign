# Cassandra Internals

## The Big Picture: A Global Amazon Warehouse Network

Think of Apache Cassandra as a **global Amazon warehouse network**.

- There is **no single master warehouse**. Every warehouse can accept incoming shipments (writes). Every warehouse can fulfill customer orders (reads).
- Packages (data) are **distributed across warehouses** based on postal code (partition key hash). When you send a package destined for zip code 10001, it goes to the warehouse responsible for that postal code range.
- If one warehouse burns down, the **neighboring warehouses have backup copies** of the same packages (replication). Operations continue uninterrupted.
- You can adjust the **confirmation policy**: ship from any warehouse without confirmation (ANY), confirm receipt at 2 of 3 warehouses (QUORUM), or require all 3 warehouses to confirm (ALL). Speed vs. reliability is your call.

Apache Cassandra is a highly scalable, distributed NoSQL database designed for handling massive amounts of data across multiple nodes with **no single point of failure**.

---

## File Mindmap

```
Cassandra Internals
├── Why It Exists
│   ├── Problem → need petabyte-scale writes with no single point of failure; relational DBs can't scale writes horizontally
│   └── Physical limit → single-node write throughput bounded by disk I/O; distributed writes require no master coordination
├── Ring Architecture
│   ├── No master node → every node is equal (peer-to-peer); any node accepts reads and writes
│   ├── Consistent hashing → partition key hashed → token assigned to node on ring
│   ├── Virtual nodes (vnodes=256) → each physical node owns 256 ring positions; even distribution
│   └── Gossip protocol → nodes exchange state every second; detect failures within seconds
├── Data Model
│   ├── Keyspace → namespace with replication config (like a database)
│   ├── Table → has primary key = partition key + clustering columns
│   ├── Partition key → determines which node(s) own the data (hash of this key)
│   ├── Clustering columns → ordering within a partition; range queries use these
│   └── Model-by-query principle → design tables for specific query patterns; denormalize; no joins
├── Write Path (Why Cassandra Writes Are Fast)
│   ├── Step 1: Write to Commit Log (sequential disk write; crash durability)
│   ├── Step 2: Write to MemTable (in-memory sorted structure)
│   ├── Step 3: Return success to client
│   ├── Step 4: MemTable flushes to SSTable when full (async)
│   └── Key insight: no read-before-write; no locking; sequential writes only
├── Read Path
│   ├── Step 1: Bloom Filter → probabilistic check: "is this key definitely NOT in this SSTable?" → skip if absent
│   ├── Step 2: Partition Summary → in-memory sparse index → narrow byte range in SSTable
│   ├── Step 3: Partition Index → binary search within SSTable for exact offset
│   ├── Step 4: Key Cache → cache recent partition offsets; skip steps 2-3 on hit
│   └── Step 5: Merge results from MemTable + all SSTables (resolve with highest timestamp)
├── Compaction Strategies
│   ├── STCS (Size-Tiered) → merge SSTables of similar size; good for write-heavy
│   ├── LCS (Leveled) → maintain levels; better read performance; more I/O during compaction
│   └── TWCS (Time-Window) → group SSTables by time window; ideal for time-series (TTL data)
├── Tunable Consistency (R + W > RF = strong consistency)
│   ├── Replication Factor (RF) → how many copies of each partition
│   ├── ONE → fastest; any one replica responds; eventual consistency
│   ├── QUORUM → (RF/2)+1 replicas must respond; balances speed and consistency
│   ├── ALL → all replicas must respond; strongest; not partition-tolerant
│   └── LOCAL_QUORUM → quorum within local DC; used in multi-region deployments
├── Fault Tolerance Mechanisms
│   ├── Hinted Handoff → if target node down, coordinator stores hint; delivers when node recovers (3-hour window)
│   ├── Read Repair → during read, coordinator checks replicas; repairs inconsistency in background
│   └── Anti-Entropy (Merkle Trees) → periodic full repair; detect and fix diverged data across replicas
├── Tombstones & Deletes
│   ├── Deletes write a tombstone marker (not actual deletion)
│   ├── gc_grace_seconds=10 days → tombstone persists 10 days to ensure all replicas receive it
│   └── Zombie problem → node down during delete misses tombstone; returns deleted data on recovery; repair fixes this
├── Trade-offs
│   ├── Pro: linear write scale, high availability, no SPOF, tunable consistency
│   ├── Con: no JOINs, no cross-partition transactions, no secondary index efficiency
│   └── Con: tombstones accumulate → slow reads if gc_grace not managed
└── Interview Angles
    ├── "Why choose Cassandra over Postgres?" → write-heavy, high availability, multi-region, no single master
    ├── "How does Cassandra achieve high write throughput?" → commit log + MemTable (sequential, no read-before-write)
    └── Follow-up: consistency vs availability trade-off → QUORUM for balanced, LOCAL_QUORUM for multi-region
```

## Architecture

### Ring Architecture & Consistent Hashing

**Analogy**: Warehouses arranged in a circle, each responsible for a range of postal codes. When a new warehouse opens, it takes over some postal codes from its clockwise neighbor — not every warehouse needs to move inventory.

- **Virtual Nodes (vnodes)**: Each physical node owns multiple virtual nodes (typically 256). This ensures even distribution of data, and when a node is added or removed, only its assigned token ranges move — not the entire ring.
- **Token Ranges**: Data is distributed around the ring based on the hash of the partition key.
- **No Master Node**: Fully peer-to-peer. Any node can serve any read or write request. There is no single point of failure.
- **Gossip Protocol**: Nodes gossip cluster state to each other every second — who is up, who is down, what token ranges they own.

```
Warehouse A (tokens: 0-255)     → receives shipments hashing to 0-255
Warehouse B (tokens: 256-511)   → receives shipments hashing to 256-511
Warehouse C (tokens: 512-767)   → receives shipments hashing to 512-767

Data with hash 100 → Warehouse A
Data with hash 400 → Warehouse B
```

---

## Data Model

### Key Concepts

- **Keyspace**: Top-level namespace (like a database in RDBMS). Sets replication strategy and factor.
- **Table (Column Family)**: Collection of rows.
- **Partition Key**: Determines which node(s) store the data. All rows with the same partition key are stored together on the same node(s).
- **Clustering Key**: Determines the sort order of rows within a partition.
- **Primary Key**: Partition Key + Clustering Key.

### Design Principle: Model by Query

In Cassandra, you design tables for specific queries — not to normalize data. There are no JOINs. Denormalization and duplication are acceptable (and expected) trade-offs for query performance.

```sql
CREATE TABLE user_events (
    user_id UUID,           -- Partition Key: all events for a user go to same node
    event_time TIMESTAMP,   -- Clustering Key: events sorted by time within partition
    event_type TEXT,
    PRIMARY KEY (user_id, event_time)
) WITH CLUSTERING ORDER BY (event_time DESC);
```

**Data Distribution**:
- All events for `user_id=123` are stored together on the same node(s) — one partition.
- Within the partition, rows are sorted by `event_time` descending.
- Enables fast range queries: "Give me the last 100 events for user 123."

---

## Write Path — Why Cassandra is Write-Optimized

### Analogy: The Warehouse Intake Process

Items arrive at the loading dock (MemTable). When the dock is full, workers move items to permanent warehouse shelves (SSTable flush). Periodically, workers re-organize and consolidate shelves (compaction).

### 1. Commit Log (Append-Only WAL)

Before anything else, the write is appended to the **Commit Log** on disk.

- Sequential disk writes — extremely fast, no random I/O.
- Ensures **durability**: if the node crashes before the MemTable is flushed, the Commit Log allows recovery.
- The Commit Log is the insurance policy. Everything is logged here first.

### 2. MemTable (In-Memory)

After the Commit Log, the write goes to the **MemTable** — an in-memory, sorted data structure (per table).

- Writes in memory are fast.
- The MemTable accumulates writes until it reaches a threshold (~128MB by default).

### 3. SSTable Flush

When the MemTable is full, it is flushed to disk as an **SSTable (Sorted String Table)**.

- SSTables are **immutable** — once written, they are never modified. New writes create new SSTables.
- Sorted by partition key, then clustering key.

### Complete Write Flow

```
Client Write
    ↓
Commit Log (disk, sequential — durability guarantee)
    ↓
MemTable (in-memory — speed)
    ↓ (when MemTable reaches threshold)
SSTable (disk, immutable — persistent)
```

**Why is this fast?**
- Sequential writes to the Commit Log (no disk seeks).
- In-memory writes to MemTable.
- No read-before-write (unlike B-trees, which must read a page before modifying it).

---

## Read Path — Optimizations

### The Challenge

Data can exist in multiple places simultaneously:
- MemTable (most recent, in memory)
- Multiple SSTables on disk (older data)
- These must be merged and deduplicated on reads.

### Optimization 1: Bloom Filters

Before touching disk, each SSTable has a **Bloom Filter** — a probabilistic data structure that answers:

- "**Definitely does not contain** this key" → skip this SSTable (saves I/O).
- "**Might contain** this key" → check the SSTable.

False positives are possible (it says "maybe" when the key is not there — extra disk read wasted). False negatives are impossible (it never says "no" when the key exists).

```
Query: user_id=123
SSTable1 Bloom Filter: "Maybe" → check it
SSTable2 Bloom Filter: "Definitely not" → skip (save I/O)
SSTable3 Bloom Filter: "Maybe" → check it
```

### Optimization 2: Partition Summary & Index

- **Partition Summary**: In-memory, sparse index of partition key positions. Narrows the disk seek to a small range.
- **Partition Index**: On-disk, full index of partition keys → byte offsets within the SSTable.

### Optimization 3: Key Cache

Caches the mapping of `partition key → SSTable byte offset`. Eliminates index lookups on repeated access to the same partition.

### Complete Read Flow

```
1. Check MemTable (most recent data, in memory)
2. Check Bloom Filters for each SSTable (skip definite misses)
3. Check Key Cache (skip index lookup if cached)
4. Read SSTables via Partition Index (seek to offset)
5. Merge results from all sources (MemTable + multiple SSTables)
6. Return the most recent version (based on timestamp/tombstone)
```

---

## Compaction — Warehouse Shelf Reorganization

### The Problem

Over time, writes accumulate many small SSTables. Deletes create tombstones (not immediate removals). Reads must check more and more SSTables, getting slower. Disk space fills with redundant and deleted data.

### The Solution: Compaction

Compaction merges multiple SSTables into fewer, larger ones. It:
- Removes deleted data (tombstones past their grace period).
- Deduplicates overwrites (keeps only the latest version of each key).
- Reduces the number of SSTables reads must check.

Think of it as the warehouse workers reorganizing shelves on a Sunday — consolidating partially filled shelves into full ones, throwing out damaged inventory.

### Compaction Strategies

#### 1. Size-Tiered Compaction (STCS) — Default

Merges SSTables of similar size together.

- **Pros**: Good for write-heavy workloads and time-series.
- **Cons**: Reads can be slow (many SSTables exist between compactions); space amplification (needs 2x space during merge).

#### 2. Leveled Compaction (LCS)

SSTables organized into levels. Level 0 contains small SSTables. Level 1+ contains fixed-size (160MB), non-overlapping SSTables.

- **Pros**: Better read performance (fewer SSTables to check); predictable space usage.
- **Cons**: Higher I/O overhead (more frequent compactions).

#### 3. Time-Window Compaction (TWCS)

Groups SSTables by time window. An entire window expires together when the TTL passes.

- **Pros**: Efficient for time-series data with TTL; tombstones not needed (whole window drops).
- **Cons**: Only appropriate for time-series data where TTL is set.

---

## Replication & Consistency

### Replication Factor (RF)

```
RF = 3: Each partition is stored on 3 different nodes
```

When data is written, the coordinator node routes the write to the RF nodes responsible for that partition key's token range. Replication is synchronous (writes go to all RF nodes before responding, depending on consistency level).

### Tunable Consistency — The Speed vs. Reliability Trade-off

**Analogy**: Receiving confirmation for a shipment.

- **ANY**: Ship it out — we'll confirm later. Fastest, least reliable.
- **ONE**: One warehouse confirms receipt. Fast, but that warehouse might be out of sync.
- **QUORUM**: 2 of 3 warehouses confirm. Balanced — strong consistency without requiring all three.
- **ALL**: All 3 warehouses confirm. Slowest, most reliable — unavailable if any node is down.

| Level | Reads | Writes | Use Case |
|-------|-------|--------|----------|
| **ONE** | 1 node | 1 node | Lowest latency; eventual consistency |
| **QUORUM** | RF/2 + 1 | RF/2 + 1 | Strong consistency (most common) |
| **ALL** | All nodes | All nodes | Strongest; unavailable if any node down |
| **LOCAL_QUORUM** | Quorum in local DC | Quorum in local DC | Multi-DC strong consistency |

### Formula for Strong Consistency

```
R + W > RF

Where:
  R = number of read replicas
  W = number of write replicas
  RF = Replication Factor

QUORUM + QUORUM > RF  →  (2 + 2) > 3  → Strong consistency
ONE + ONE > RF        →  (1 + 1) = 2 = 3  → NOT strong consistency
```

If `R + W > RF`, at least one node in the read set is guaranteed to have the latest write (Pigeonhole Principle).

---

## Hinted Handoff & Read Repair

### Hinted Handoff

**Scenario**: Warehouse B is temporarily offline during a write. The coordinator (Warehouse A) stores a **hint** — a local record saying "when Warehouse B comes back, deliver this data to it."

When Warehouse B recovers, Warehouse A replays the hint, restoring consistency.

- Default hint window: 3 hours. If the node is down longer, the hint expires and a manual repair is needed.
- Prevents permanent data divergence from short-lived outages.

### Read Repair

During a read at QUORUM, the coordinator contacts 2 of 3 replicas. If they return different values (replica divergence), the coordinator:
1. Returns the most recent value to the client (based on timestamp).
2. Asynchronously sends the latest value to the stale replica to bring it up to date.

This is automatic, background self-healing.

---

## Anti-Entropy Repair

For nodes that have been down long enough to miss Hinted Handoff, or for tables with low read traffic (no Read Repair triggered), run `nodetool repair`.

- Compares **Merkle Trees** (hash trees of data ranges) between replicas.
- Instead of sending entire datasets, only the rows that differ are synced.
- Should run weekly for tables without frequent reads.

---

## Tombstones & Deletions

### The Problem: Cassandra is Append-Only

Cassandra's SSTables are immutable. You cannot "remove" a row from an existing SSTable. And with multiple replicas, a deleted row could reappear from a replica that missed the delete.

### The Solution: Tombstones

**Analogy**: When you "delete" an item from the warehouse, Cassandra puts a bright red sticker (tombstone) on the shelf location saying "THIS WAS DELETED on [timestamp]." The actual shelf space is reclaimed later during compaction.

- Deletes create **tombstones** — markers in SSTables that indicate the row was deleted at a specific timestamp.
- During reads, tombstones suppress any older versions of the same data.
- Compaction removes tombstones after `gc_grace_seconds` (default: 10 days).

### Why 10 Days?

The grace period allows time for downed replicas to recover and receive the tombstone before it is permanently removed. Without the tombstone, a replica that missed the delete would "resurrect" the deleted data when it comes back online.

**Zombie problem**: If a replica is down for more than `gc_grace_seconds`, it may resurrect deleted data when it returns. Always run `nodetool repair` on nodes that have been down for extended periods.

### Tombstone Overload

Too many accumulated tombstones slow reads significantly — the read path must scan and skip all tombstones.

- Monitor with `nodetool cfstats`.
- Mitigation: use TTL on data when possible (TTL expiration is more efficient than explicit tombstones in TWCS).

---

## Performance Tuning

### Data Modeling Best Practices

- **Denormalize**: No joins. Duplicate data across tables to serve different query patterns.
- **Model by query**: Design tables around your access patterns, not around normalization.
- **Partition size**: Keep partitions under 100MB. Large partitions slow reads and cause GC pressure.
- **Avoid unbounded growth**: Use TTL or bucketing strategies (e.g., `user_id + month` as partition key for time-series).

### Hardware

- **SSDs**: Essential for good SSTable performance (random reads during compaction).
- **RAM**: More RAM = larger MemTable + larger caches (Bloom Filters, key cache).
- **Network**: Low-latency network between nodes for replication.

### Configuration

```
concurrent_reads: 32 (default)
concurrent_writes: 32 (default)
memtable_flush_writers: 2 per disk
```

---

## Common Pitfalls

### SELECT * with Large Partitions

Can timeout or cause Out-Of-Memory errors on the coordinator. Use `LIMIT` or paging state for large partitions.

### Allowing Unbounded Partition Growth

If new rows are continuously added to a partition without TTL or bucketing, the partition grows without bound. Wide partitions (millions of rows) cause slow reads and compaction issues.

### Not Running Repairs

Data drifts across replicas over time (missed writes, node restarts). Run `nodetool repair` on a weekly schedule for tables that are not frequently read (no automatic Read Repair).

### Secondary Indexes

Secondary indexes in Cassandra have poor distributed performance. They create a "partition per unique value" internally, requiring scatter-gather reads across all nodes. Instead:
- Create a separate lookup table (materialized view pattern).
- Denormalize the data into a query-specific table.
- Use Cassandra Materialized Views (with caution — additional write overhead).

---

## Interview Questions

**Q: Why is Cassandra fast for writes?**
- Sequential, append-only writes to the Commit Log (no random I/O, no seeks).
- In-memory MemTable accumulates writes.
- No read-before-write (unlike B-trees that must read a page to modify it).
- LSM-tree architecture: writes are optimized, reads pay the complexity cost.

**Q: How does Cassandra achieve high availability?**
- Replication Factor (typically RF=3) across nodes or data centers.
- No single master — peer-to-peer, every node can accept writes.
- Tunable consistency — can tolerate node failures while still serving requests.
- Hinted Handoff for short-lived node outages.

**Q: Explain quorum consistency.**
- QUORUM = RF/2 + 1. For RF=3, QUORUM = 2.
- Write at QUORUM: 2 of 3 replicas acknowledge before success.
- Read at QUORUM: 2 of 3 replicas respond before returning the latest value.
- Formula: R + W > RF → 2 + 2 > 3 → strong consistency guaranteed.
- Prevents split-brain: majority must agree.

**Q: What are the trade-offs of using Cassandra?**

| Pros | Cons |
|---|---|
| Write-optimized (LSM-tree, sequential I/O) | Eventual consistency unless using QUORUM |
| Linear horizontal scalability | No JOINs — data modeling is query-driven |
| No single point of failure | Tombstone accumulation slows reads |
| Tunable consistency | Data modeling complexity (denormalization) |
| Multi-datacenter replication built-in | Rebalancing cost when adding nodes |

**Q: Cassandra vs. DynamoDB?**
- Both are wide-column NoSQL databases with similar write paths (LSM-tree).
- Cassandra: self-managed, open-source, flexible consistency tuning.
- DynamoDB: fully managed, pay-per-use, AWS-native, less operational burden.
- Choose Cassandra when you need multi-cloud or on-premise control; DynamoDB when you are AWS-native and prefer managed infrastructure.
