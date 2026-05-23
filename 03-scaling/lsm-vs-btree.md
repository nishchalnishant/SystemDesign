---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# LSM Trees vs B-Trees

## The Core Trade-off

**B-Tree**: optimized for reads. Random I/O on writes (update in place).
**LSM Tree**: optimized for writes. Sequential I/O on writes; read amplification on queries.

Neither is universally better. The choice depends on your read/write ratio and latency requirements.

Real-world analogy:
- **B-Tree** = a sorted filing cabinet. Finding a record is fast (go to the right drawer), but inserting a new document in the middle means shifting everything around.
- **LSM Tree** = a stack of sticky notes + occasional reorganization. You always write to a new note at the top (fast), but reading requires checking all the notes to find the latest version (slow without Bloom filters).

---

## B-Tree Internals

### Structure

A B-Tree is a balanced tree where each node is a **disk page** (typically 4KB or 16KB).

```
                    [30 | 70]
                   /    |    \
          [10|20]   [40|50|60]   [80|90]
```

- **Internal nodes**: contain keys + pointers to children
- **Leaf nodes**: contain keys + actual data (or pointers to row data)
- Branching factor: typically 100–500 children per node
- A tree of depth 3-4 can index billions of rows

### Write Path (Update In-Place)

1. Find the correct leaf page (tree traversal: O(log n) page reads)
2. Write the new value into that page
3. If page is full: **page split** — create a new page, redistribute entries, update parent

**The problem**: writing to a random page deep in the tree means the disk head must seek to that location. On HDD: 5-10ms seek time. Even on SSD, random 4KB writes are ~10x slower than sequential writes.

### Write-Ahead Log (WAL)

Before modifying any page, databases write the change to a **WAL** (append-only log). This ensures crash recovery: if the DB crashes mid-write, replay the WAL.

But WAL doesn't eliminate the random write — it adds a sequential write AND a random write (2x I/O per update in the worst case).

### Read Path

1. Start at root, follow child pointers (each level = 1 page read from disk)
2. Binary search within leaf page for exact key
3. Total cost: O(log n) page reads = 3-4 disk reads for a billion-row table

**InnoDB (MySQL) B+Tree specifics:**
- All data stored in leaf nodes of the clustered index
- Secondary indexes store the primary key, not a pointer — secondary index lookup = 2 traversals
- Page size: 16KB by default

---

## LSM Tree Internals

Used by: RocksDB (embedded), Cassandra, HBase, LevelDB, ScyllaDB, InfluxDB.

### Structure

An LSM Tree has two components:

1. **MemTable** (in-memory, mutable): a sorted data structure (red-black tree or skip list)
2. **SSTables** (on-disk, immutable): Sorted String Tables — sorted, immutable files

```
Write → MemTable (in RAM)
          │
          │  (flush when full, ~64MB)
          ▼
       L0 SSTables (may have overlapping key ranges)
          │
          │  (compaction)
          ▼
       L1 SSTables (non-overlapping key ranges, ~10MB each)
          │
          │  (compaction)
          ▼
       L2 SSTables (non-overlapping, ~100MB each)
          ...
```

### Write Path

1. Write to WAL (for crash recovery): O(1), sequential append
2. Write to MemTable: O(log n) in-memory sorted insert
3. When MemTable hits size limit (~64MB): **flush** to a new L0 SSTable (sequential write, ~GB/s)

**No random writes at all** — every disk write is a sequential append or flush. This is why LSM Trees dominate write-heavy workloads.

### Read Path

1. Check MemTable (latest writes)
2. Check L0 SSTables (newest first — these may overlap in key range)
3. Check L1, L2, L3... SSTables

Without optimization: O(number of SSTables) reads in the worst case. In practice, mitigated by:

- **Bloom Filters**: probabilistic filter per SSTable. 99%+ chance of eliminating an SSTable that doesn't contain the key. False positive rate ~1%.
- **Sparse Index**: each SSTable has a sparse index (every ~16th key). Binary search to narrow range.
- **Block Cache**: recently read SSTable blocks cached in RAM.

### Compaction

Compaction is the background process that merges SSTables and removes stale/deleted data. It is the source of both **write amplification** and **space amplification**.

#### Leveled Compaction (LCS — used by Cassandra, RocksDB default)

- L0: small SSTables, may overlap
- L1+: each level has non-overlapping key ranges; total size increases 10x per level
- Compaction picks one SSTable from Lk and merges it with the overlapping SSTables in Lk+1

```
L0: [a-z] [a-m] [n-z]        ← 3 overlapping SSTables
L1: [a-c] [d-h] [i-m] [n-r] [s-z]   ← non-overlapping, ~10MB each
L2: [a-b] ... [y-z]          ← non-overlapping, ~100MB each
```

- **Read amplification**: ~5-10 SSTable reads in worst case (one per level)
- **Write amplification**: 10-30x (each byte written to DB may be rewritten 10-30 times during compaction)
- **Space amplification**: ~1.1x (only ~10% temporary overhead)

#### Size-Tiered Compaction (STCS — used by early Cassandra)

- Group SSTables by size; merge groups of similarly-sized files
- Fewer compaction I/Os, but temporarily doubles space during merge

```
4 x 10MB SSTables → merge → 1 x 40MB SSTable → group with others...
```

- **Read amplification**: high (many overlapping SSTables, must check all)
- **Write amplification**: 5-10x (lower than leveled)
- **Space amplification**: up to 2x during compaction

#### Time-Window Compaction (TWCS — used by Cassandra for time-series)

- Group SSTables by time window (e.g., 1 hour)
- Never merge SSTables from different time windows
- When a window closes, compact it once, then never touch it again

Designed for workloads where old data is never updated — append-only time-series.

---

## Write Amplification, Read Amplification, Space Amplification

The fundamental three-way trade-off. You can optimize for at most two.

**Write Amplification (WA)**: how many bytes are actually written to disk per byte of user data.

```
WA = total_bytes_written_to_disk / bytes_written_by_application
```

- B-Tree: WA ≈ 2 (WAL + random page write), but page splits can spike it higher
- LSM Leveled: WA ≈ 10-30 (data rewritten at each level during compaction)
- LSM Tiered: WA ≈ 5-10

**Read Amplification (RA)**: how many disk reads per user query.

```
RA = disk_reads_per_query
```

- B-Tree: RA ≈ 3-4 (depth of tree, usually 3-4 levels)
- LSM Leveled: RA ≈ 5-10 (one read per level + Bloom filter checks)
- LSM Tiered: RA ≈ 10-50 (many overlapping SSTables)

**Space Amplification (SA)**: how much disk space is used per byte of live data.

```
SA = total_disk_used / bytes_of_live_data
```

- B-Tree: SA ≈ 1.3-2x (fragmentation, partially filled pages)
- LSM Leveled: SA ≈ 1.1x (only ~10% of data is being compacted at any time)
- LSM Tiered: SA ≈ 1.5-2x (temporary doubled space during merge)

| Strategy | Write Amp | Read Amp | Space Amp | Best For |
|---|---|---|---|---|
| B-Tree | Low (2-5x) | Very Low (3-4) | Medium (1.3-2x) | Read-heavy, OLTP |
| LSM Leveled | High (10-30x) | Low (5-10) | Very Low (1.1x) | Write-heavy, bounded reads |
| LSM Tiered | Medium (5-10x) | High (10-50) | High (1.5-2x) | Write-heavy, scan workloads |
| LSM TWCS | Low (1-2x) | Medium | Low | Time-series, append-only |

---

## Tombstones and Deletes

In a B-Tree: delete the entry, compact/free the page.

In an LSM Tree: **you cannot delete data in place** (SSTables are immutable). Instead:

1. Write a **tombstone** marker: a special entry for the key with a "deleted" flag
2. Tombstone sits in MemTable, gets flushed to SSTable
3. During compaction, if a tombstone meets the original entry, both are discarded

**Problem**: tombstones accumulate. If you delete data but compaction hasn't merged the tombstone with the original data, **both exist on disk simultaneously** (space amplification spike). Cassandra's `gc_grace_seconds` (default 10 days) is the window during which tombstones must be preserved to prevent resurrection of deleted data across nodes.

---

## Crash Recovery

**B-Tree recovery**: Replay WAL from last checkpoint. Pages that were partially written are fixed by WAL. O(WAL entries since last checkpoint).

**LSM Tree recovery**:
1. MemTable contents are lost on crash
2. Replay WAL to reconstruct MemTable state
3. SSTables on disk are already durable (each flush was an atomic file rename)
4. No partial page writes to worry about — SSTables are immutable once written

---

## When to Use Each

### Use B-Tree (PostgreSQL, MySQL, SQLite) when:

- Read/write ratio is high (>80% reads)
- You need secondary indexes with low read latency
- Workload is OLTP with mixed reads and writes to the same rows
- You need strong MVCC / isolation level guarantees
- Small working set that fits in buffer pool

### Use LSM Tree (Cassandra, RocksDB, HBase) when:

- Write throughput is the bottleneck (>50% writes)
- Data is largely append-only or write-once (time-series, logs, events)
- You can tolerate background compaction I/O spikes
- You don't need complex secondary indexes or joins
- Wide columns or sparse schema (Cassandra's design sweet spot)

---

## RocksDB Internals (Practical Reference)

RocksDB is the most widely embedded LSM engine (used by Kafka log compaction, TiKV, MyRocks, CockroachDB's storage layer).

- MemTable: skip list by default (configurable to hash-skip-list, vector)
- Block size: 4KB per block within SSTable
- Bloom filter: 10 bits per key → ~1% false positive rate
- Block cache: LRU cache for recently accessed SSTable blocks
- Write batch: atomic write of multiple keys in one WAL entry
- Column families: logical separation of key spaces, each with its own MemTable/SSTable tree

**Tuning levers for interview discussion:**

| Parameter | Effect |
|---|---|
| `write_buffer_size` | MemTable size before flush; larger = fewer L0 files = less read amplification |
| `max_write_buffer_number` | Max MemTables before writes stall (back-pressure mechanism) |
| `level0_slowdown_writes_trigger` | L0 file count that triggers write slowdown (compaction is behind) |
| `compression_type` | Snappy (fast), ZSTD (better ratio) — trades CPU for I/O |
| `bloom_filter_bits_per_key` | Higher = lower false positive rate, more memory |

---

## Interview Deep-Dive Questions

1. **Why does Cassandra use LSM Trees instead of B-Trees?**
   Cassandra was designed for write-heavy, eventually consistent workloads with no single master. LSM Trees provide write throughput that scales linearly with nodes. Cassandra's leaderless architecture means multiple nodes can accept writes simultaneously — B-Tree's random I/O would bottleneck at disk IOPS, while LSM's sequential writes can use the full disk bandwidth.

2. **A RocksDB instance has 50 L0 SSTables. What does this indicate and what are the consequences?**
   Compaction is falling behind write throughput. L0 SSTables can have overlapping key ranges, so reads must check all 50. RocksDB will begin rate-limiting writes (`level0_slowdown_writes_trigger` is typically 20). If it reaches `level0_stop_writes_trigger` (typically 36), writes will stall completely until compaction catches up.

3. **How does a Bloom filter help LSM Tree reads? What is the false positive problem?**
   A Bloom filter for an SSTable can answer "does key X exist in this SSTable?" in O(1). If the answer is "no," skip the SSTable. If "yes" (possibly a false positive), do the actual disk read. At 10 bits/key, ~1% of "yes" answers are false positives. This means 1% of SSTable lookups are wasted disk reads. Still much better than checking every SSTable.

4. **When does write amplification hurt SSD lifespan?**
   SSDs have a rated TBW (Total Bytes Written). A 10x write amplification on a workload writing 1 TB/day means the SSD sees 10 TB/day of actual writes. A consumer SSD rated for 300 TBW would fail in 30 days. Production deployments must account for write amplification when calculating expected SSD lifespan and choosing between MLC vs TLC vs SLC NAND.

---

## See Also

- `01-foundations/storage-fundamentals.md` — IOPS, disk types, sequential vs random I/O
- `01-foundations/databases.md` — MVCC, B-Tree indexes in PostgreSQL/MySQL
- `04-advanced-topics/internals/cassandra-internals.md` — Cassandra's compaction strategies in detail
- `04-advanced-topics/internals/kafka-internals.md` — Kafka log segments (LSM-inspired append-only design)
