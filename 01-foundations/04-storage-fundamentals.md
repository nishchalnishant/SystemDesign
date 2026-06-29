---
module: 01-foundations
topic: Storage Fundamentals
status: unread
tags: [01-foundations, system-design, storage, lsm-tree, b-tree, indexes]
---

# Storage Fundamentals

Your write-heavy service is hitting 50K writes/sec and Postgres is falling behind. Your read-heavy service needs sub-10ms lookups. Are these the same database problem? No — and the difference is in how each storage engine is built.

```
[Storage Fundamentals]
├── Physical Reality
│   ├── HDD: sequential read ~200MB/s; random read ~0.5MB/s (seek time ~10ms)
│   ├── SSD: sequential read ~500MB/s; random read ~100MB/s; random write ~80MB/s
│   └── RAM: ~50GB/s; no seek cost; volatile
├── B-Tree (Read-Optimized)
│   ├── On-disk balanced tree; nodes = disk pages (4KB–16KB)
│   ├── In-place updates: find page → modify → write back
│   ├── Read: O(log N) page fetches; fast random reads
│   ├── Write: random writes (update any page); good up to ~10K writes/sec
│   └── Used by: PostgreSQL, MySQL, SQLite, most RDBMS
├── LSM Tree (Write-Optimized)
│   ├── Writes go to in-memory MemTable → flushed to immutable SSTables on disk
│   ├── All writes are sequential (append-only) → saturates disk throughput
│   ├── Compaction merges SSTables → removes tombstones, merges duplicates
│   ├── Read: check MemTable → check Bloom filter → read SSTables (slower reads)
│   └── Used by: Cassandra, RocksDB, LevelDB, DynamoDB, HBase
├── Indexes
│   ├── B-Tree index: fast range queries and point lookups; costly on write-heavy tables
│   ├── Hash index: O(1) point lookups; no range queries
│   ├── Composite index: (col_a, col_b) — usable for queries on col_a alone, NOT col_b alone
│   └── Covering index: index includes all columns the query needs — avoids table fetch
├── Write Amplification
│   ├── B-Tree: write once to WAL, once to page → amplification ~2×
│   ├── LSM: compaction rewrites data repeatedly → amplification 10–30× (disk writes > logical writes)
│   └── Trade-off: LSM wins on raw write throughput; B-tree wins on read latency
└── Interview Decision Rule
    ├── High write throughput (>10K/s), time-series, logs → LSM (Cassandra, DynamoDB)
    └── Complex queries, strong consistency, moderate write load → B-Tree (Postgres, MySQL)
```

## Why physical storage matters

Random I/O on HDD is 400× slower than sequential (0.5MB/s vs 200MB/s). SSDs close that gap but random writes still cost more than sequential. Every database design decision — B-tree vs LSM, index choice, partition size — is ultimately about minimizing random disk seeks. The engine that wins on writes and the engine that wins on reads are built differently because of this physical constraint.

## B-Tree: how it works

Nodes are fixed-size disk pages (typically 4KB–16KB). An update finds the target page — anywhere on disk — modifies it in place, and writes it back. That's a random write. Reads follow tree pointers, touching O(log N) pages, which is fast for point lookups and range scans. B-trees are the right choice for read-heavy workloads with complex queries. The write bottleneck is real: random writes saturate HDDs around 200 IOPS (~200 writes/sec per spindle), and even on SSD you cap out well below LSM throughput.

## LSM Tree: how it works

```
Write path:
  1. Write to in-memory MemTable (also written to WAL for durability)
  2. When MemTable hits size limit (~64MB), flush to disk as immutable SSTable
  3. Background compaction merges SSTables: removes deleted keys (tombstones), deduplicates
  4. Result: all disk writes are sequential → 10–100× higher write throughput than B-tree

Read path:
  1. Check MemTable (in-memory, fast)
  2. Check Bloom filter for each SSTable (avoids disk read if key definitely absent)
  3. Binary search within SSTable
  Penalty: a read may touch multiple SSTables before finding the latest value
```

```python
# Conceptual LSM write path
class LSMTree:
    def __init__(self, memtable_limit=64 * 1024 * 1024):  # 64MB
        self.memtable = {}          # in-memory sorted dict
        self.wal = open("wal.log", "ab")
        self.sstables = []          # list of on-disk SSTables
        self.memtable_size = 0
        self.limit = memtable_limit

    def write(self, key, value):
        # 1. Append to WAL (sequential write — durable)
        self.wal.write(f"{key}={value}\n".encode())
        self.wal.flush()
        # 2. Write to MemTable (in-memory)
        self.memtable[key] = value
        self.memtable_size += len(key) + len(value)
        # 3. Flush if MemTable is full
        if self.memtable_size >= self.limit:
            self._flush_to_sstable()

    def _flush_to_sstable(self):
        # Writes are sequential — sorted keys written in one pass
        path = f"sstable_{len(self.sstables)}.sst"
        with open(path, "wb") as f:
            for key in sorted(self.memtable):
                f.write(f"{key}={self.memtable[key]}\n".encode())
        self.sstables.append(path)
        self.memtable.clear()
        self.memtable_size = 0
```

## Index types — decision table

| Index Type | When to use | Watch out |
|---|---|---|
| B-Tree index | Point lookups, range queries, ORDER BY | Slows writes; each index = extra write per row |
| Hash index | Exact-match only (no range) | Redis uses this; most RDBMS don't expose it |
| Composite `(a, b)` | Queries filter on `a` or `(a, b)` | Useless if query filters only on `b` |
| Covering index | Query needs cols a, b, c — include all in index | Avoids table heap fetch; dramatic speedup |
| Partial index | `WHERE status = 'pending'` — index only pending rows | Much smaller; fast for filtered queries |

## Write amplification

LSM compaction rewrites data multiple times across levels (L0→L1→L2→L3). A single logical write may cause 10–30 physical disk writes. This is why SSDs are preferred for LSM-based databases: SSDs have higher random write endurance and IOPS than HDDs. B-trees amplify less (~2×: one WAL write + one page write), but that one page write is random, which is the real cost on spinning disk.

## Interview application

- **Time-series metrics, event logs, write-heavy IoT** → Cassandra (LSM, tunable consistency, wide-column)
- **Transactional data, complex JOINs, strong consistency** → PostgreSQL (B-tree, MVCC, ACID)
- **High write throughput + point lookups** → DynamoDB (LSM underneath, hash+range key)
- **Caching / pure point lookups** → Redis (hash index in RAM)

## Common follow-ups

**"What is a WAL and why does every database have one?"**
Write-Ahead Log: write the change to an append-only log before applying it to the data structure. On crash, replay the log to recover. Sequential write, durable, cheap. Both B-tree and LSM use it.

**"Why does adding indexes slow down writes?"**
Every INSERT/UPDATE must update all indexes on the table. 5 indexes = 5 extra B-tree writes per row mutation. On a write-heavy table, drop unused indexes.

**"What is MVCC?"**
Multi-Version Concurrency Control: instead of locking rows for reads, keep multiple versions of each row. Readers see a consistent snapshot; writers create a new version. Postgres uses this — reads never block writes. The cost is periodic vacuum to clean up old row versions.
