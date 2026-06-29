---
module: 03-scaling
topic: Database Internals
status: unread
tags: [03-scaling, system-design, databases, internals]
---
# Database Internals — What Matters for System Design

> **Why this file exists**: Every HLD answer involving a database will get follow-up questions: "Why not use an index here?", "What happens under high write load?", "How do you handle concurrent reads and writes?" This file gives you the internal model to answer those confidently.

---

## File Mindmap

```
Database Internals
├── Storage Engines
│   ├── B-Tree → reads fast, writes update in-place; good for OLTP
│   └── LSM Tree → writes fast (append-only); reads slower; good for write-heavy (Cassandra, RocksDB)
├── Indexes
│   ├── Primary index → clustered; data stored in index order; one per table
│   ├── Secondary index → non-clustered; pointer to row; multiple per table
│   ├── Composite index → (col_a, col_b); only useful if query uses leftmost prefix
│   └── Covering index → index contains all columns the query needs; no row lookup
├── Write-Ahead Log (WAL)
│   ├── What → every write goes to append-only log BEFORE modifying data pages
│   ├── Why → crash recovery; replay log to restore state; enables replication
│   └── Used by → PostgreSQL, MySQL InnoDB, SQLite
├── MVCC (Multi-Version Concurrency Control)
│   ├── What → each transaction sees a snapshot; old versions kept until no longer needed
│   ├── Why → readers don't block writers; writers don't block readers
│   └── Cost → dead tuples accumulate; VACUUM in Postgres cleans them up
├── Transaction Isolation Levels
│   ├── Read Uncommitted → sees dirty writes; almost never used
│   ├── Read Committed → sees only committed data; default in Postgres
│   ├── Repeatable Read → same query returns same result within transaction
│   └── Serializable → transactions appear to run one-at-a-time; safest, slowest
└── Interview Angles
    ├── "Why use a covering index?" → eliminates heap fetch; critical for hot read paths
    ├── "What breaks under Read Committed?" → non-repeatable reads; phantom reads
    └── "How does WAL enable replication?" → standby replays primary's WAL stream
```

---

## Storage Engines

### B-Tree (PostgreSQL, MySQL InnoDB, SQLite)

Data is stored in sorted pages in a balanced tree. Reads are O(log n). Writes update pages in-place — fast for random reads, slower for write-heavy workloads (random I/O).

**Use when**: mixed read/write OLTP workloads, queries by range or primary key.

### LSM Tree (Cassandra, RocksDB, LevelDB, HBase)

Writes go to an in-memory buffer (MemTable), then are flushed to disk as sorted files (SSTables). Reads must check multiple levels. Compaction merges files in the background.

**Use when**: write-heavy workloads, time-series, event logs. Read amplification is the trade-off.

| | B-Tree | LSM Tree |
|---|---|---|
| Write speed | Moderate (random I/O) | Fast (sequential I/O) |
| Read speed | Fast | Slower (multiple levels) |
| Space overhead | Low | Higher (until compaction) |
| Best for | OLTP, mixed workloads | Write-heavy, append-dominant |

---

## Indexes

### How B-Tree indexes work

Each internal node stores key ranges. Leaf nodes store the actual data (clustered) or pointers to rows (non-clustered). A lookup traverses ~3-4 levels for a million-row table.

### Primary vs Secondary Index

**Primary (clustered)**: rows are physically stored in index order. Only one per table. In InnoDB, this is always the primary key.

**Secondary (non-clustered)**: a separate structure that stores (indexed_column → primary_key). A lookup hits the secondary index, then follows the pointer to the actual row (one extra I/O).

### Composite Index

`CREATE INDEX idx ON orders(user_id, created_at)` — useful for `WHERE user_id = ? AND created_at > ?` but **not** for `WHERE created_at > ?` alone (leftmost prefix rule).

### Covering Index

If all columns in a `SELECT` are in the index, the DB never touches the main table. Critical for hot read paths.

```sql
-- This query is fully covered by the index below
SELECT user_id, created_at FROM orders WHERE user_id = 42;
CREATE INDEX idx_cover ON orders(user_id, created_at);
```

### When Indexes Hurt

- High write tables: every insert/update must update all indexes
- Low cardinality columns (e.g., `is_active BOOLEAN`): not selective enough
- Table scans are sometimes faster than index + row lookups for large result sets

---

## Write-Ahead Log (WAL)

Before any data page is modified, the change is written to an append-only log on disk. If the server crashes mid-write, the DB replays the WAL on startup to reach a consistent state.

**Why this matters for system design**:
- **Durability**: `fsync()` on WAL = durable write; data pages can be in memory
- **Replication**: streaming replication sends WAL records to replicas; replica applies them in order
- **Point-in-time recovery**: archive WAL segments → restore to any moment

---

## MVCC — Multi-Version Concurrency Control

Instead of locking rows, the DB keeps multiple versions of each row. Each transaction gets a snapshot of the database at the moment it started.

```
Transaction A (started at T=100): sees row version from T=100
Transaction B (started at T=105): sees row version from T=105
Transaction A updates row → creates new version at T=110
Transaction B still sees T=100 version — no lock needed
```

**Result**: readers never block writers; writers never block readers. High concurrency.

**Cost**: old versions (dead tuples in Postgres) accumulate and must be vacuumed. Long-running transactions prevent cleanup → table bloat.

---

## Transaction Isolation Levels

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | Typical Use |
|---|---|---|---|---|
| Read Uncommitted | ✓ possible | ✓ possible | ✓ possible | Almost never |
| Read Committed | ✗ prevented | ✓ possible | ✓ possible | Default (Postgres, Oracle) |
| Repeatable Read | ✗ | ✗ prevented | ✓ possible | MySQL InnoDB default |
| Serializable | ✗ | ✗ | ✗ prevented | Financial systems, audits |

**What the anomalies mean**:
- **Dirty read**: you see a write that was later rolled back
- **Non-repeatable read**: you read the same row twice, get different values (another TX committed between reads)
- **Phantom read**: you run the same query twice, get different rows (another TX inserted/deleted between runs)

**Interview framing**: "For the payment ledger, I'd use Serializable isolation on the balance update — we cannot tolerate phantom reads where two concurrent withdrawals both see the same balance and both succeed."

---

## Query Execution — What Happens Inside

```
SQL query → Parser → Query Planner (optimizer) → Executor → Buffer Pool → Disk
```

**Buffer Pool**: in-memory cache of data pages. Hot pages stay in RAM; cold pages evicted (LRU). This is why `shared_buffers` in Postgres matters — bigger pool = fewer disk reads.

**Query Planner**: decides index scan vs sequential scan based on table statistics (row count, cardinality). `ANALYZE` updates statistics. Stale stats → bad plans → slow queries.

**EXPLAIN / EXPLAIN ANALYZE**: shows the plan the planner chose. Essential for debugging slow queries.

---

## Interview Application

| Scenario | What to say |
|---|---|
| "Why PostgreSQL for your payments DB?" | "ACID guarantees, Serializable isolation for balance updates, WAL-based replication for HA" |
| "Your user profile reads are slow" | "Add covering index on (user_id, name, email) — eliminates heap fetch; profile reads are read-heavy and benefit immediately" |
| "How does your read replica stay in sync?" | "PostgreSQL streaming replication via WAL — replica applies log records in order; typically < 100ms lag" |
| "Cassandra for user sessions — why?" | "LSM-tree writes are fast for session upserts; we don't need strong consistency; Cassandra's eventual consistency with QUORUM reads is acceptable" |
| "What breaks at high write load?" | "Index maintenance bottleneck — each write updates all indexes. Solution: drop low-value indexes, use partial indexes, or switch to LSM-tree storage" |
