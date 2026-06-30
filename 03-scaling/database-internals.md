> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Database internals that matter for system design interviews — storage engines, indexes, WAL, MVCC, and transaction isolation — so you can answer follow-up questions confidently.
>
> **Key topics:**
> - Storage engines: B-Tree (read-fast, in-place updates) vs LSM Tree (write-fast, append-only + compaction)
> - Index types: primary (clustered), secondary (pointer to row), composite (leftmost prefix rule), covering (no row lookup)
> - WAL (Write-Ahead Log): every write goes to sequential log before modifying data pages; enables crash recovery and replication
> - MVCC: each transaction sees a snapshot; old row versions kept until VACUUM; readers never block writers
> - Transaction isolation levels: Read Uncommitted → Read Committed → Repeatable Read → Serializable (and their anomalies)
> - Query optimization: EXPLAIN/ANALYZE to find seq scans; covering indexes to avoid heap access; query planner statistics
> - Vacuum and bloat: PostgreSQL dead tuples from MVCC; AUTOVACUUM reclaims space; bloat slows range scans
>
> **Key takeaway:** When an interviewer asks "what happens under high write load?" — explain WAL sequential writes, connection limits, and why MVCC dead tuples require VACUUM.

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

**B-Tree vs B+Tree (the distinction interviewers probe)**: Real databases use **B+Trees**, not classic B-Trees. In a B+Tree, only leaf nodes store data (or row pointers); internal nodes store keys for navigation only. Leaf nodes are linked in a doubly-linked list, so range scans (`WHERE created_at BETWEEN x AND y`) walk leaves sequentially without re-traversing the tree. This is why B+Trees dominate: range queries are cheap.

**Concrete numbers**: Page = 8KB (Postgres) or 16KB (InnoDB). Branching factor ~100–500. A 3-level tree indexes ~100M rows; a 4-level tree indexes ~10B+. Upper levels stay hot in buffer cache, so a point lookup on a billion-row table is typically **1 disk read** (the leaf), not 4.

**Use when**: mixed read/write OLTP workloads, queries by range or primary key.

### LSM Tree (Cassandra, RocksDB, LevelDB, HBase)

Writes go to an in-memory buffer (MemTable, ~64MB), then are flushed to disk as sorted files (SSTables). Reads must check the MemTable plus multiple on-disk levels; Bloom filters skip SSTables that can't contain the key. Compaction merges files in the background, which is the source of write amplification (10–30x for leveled compaction).

**Use when**: write-heavy workloads, time-series, event logs. Read amplification and compaction I/O are the trade-offs.

> Full LSM internals (compaction strategies, tombstones, write/read/space amplification, RocksDB tuning) are in `lsm-vs-btree.md`.

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

### Index Internals Interviewers Probe

- **Index-only scan vs heap fetch**: a secondary-index lookup normally fetches the row from the heap (one extra random I/O). A covering index avoids this. In Postgres the row must also be **visible** (MVCC) — Postgres checks the *visibility map*; if the page isn't all-visible, it still does a heap fetch even for a covering index. MySQL InnoDB doesn't have this caveat because the clustered index *is* the table.
- **InnoDB secondary index = double lookup**: secondary indexes store the **primary key**, not a physical pointer. So a secondary lookup is: traverse secondary index → get PK → traverse clustered index. Two B+Tree walks. This is why a fat primary key (e.g., a UUID) bloats every secondary index.
- **Clustered index on random UUID = page-split hell**: InnoDB inserts rows in PK order. A random UUIDv4 PK causes inserts into random leaf pages → constant page splits, ~2x write amplification, fragmentation. Use a monotonic key (auto-increment, UUIDv7, or Snowflake ID) for the clustered key. This is a classic "why are our inserts slow?" interview scenario.
- **fillfactor**: leaving free space in each page (Postgres default 90%, lower it for update-heavy tables) lets updates stay on the same page (HOT updates), avoiding index churn.
- **HOT updates (Postgres)**: if an `UPDATE` doesn't change any indexed column and the page has free space, Postgres writes the new tuple version on the same page and skips updating every index — huge win for update-heavy tables. Set `fillfactor=70–85` to enable it.

---

## Write-Ahead Log (WAL)

Before any data page is modified, the change is written to an append-only log on disk. If the server crashes mid-write, the DB replays the WAL on startup to reach a consistent state.

**Why this matters for system design**:
- **Durability**: `fsync()` on WAL = durable write; data pages can be in memory
- **Replication**: streaming replication sends WAL records to replicas; replica applies them in order
- **Point-in-time recovery**: archive WAL segments → restore to any moment

**The fsync bottleneck and group commit**: a durable commit needs an `fsync()` on the WAL, which forces data through the OS cache to physical storage — ~0.5–2ms on NVMe, ~5–10ms on a network-attached EBS volume. That fsync, not CPU, often caps commit throughput. **Group commit** amortizes it: many concurrent transactions' WAL records are flushed in one fsync. Postgres `commit_delay` / `synchronous_commit` and MySQL `innodb_flush_log_at_trx_commit` tune this:
- `synchronous_commit=off` (Postgres) / `innodb_flush_log_at_trx_commit=2`: ack before fsync → ~10x more commit throughput, but you can lose the last few hundred ms of committed transactions on a crash. Acceptable for analytics/event ingestion, never for a payment ledger.
- **Full-page writes**: after a checkpoint, Postgres writes the entire 8KB page to WAL on first modification (protects against torn pages on a crash). This is why WAL volume spikes right after a checkpoint and why checkpoint tuning matters.

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

### The Anomaly the Table Hides: Write Skew

The standard anomaly table is incomplete. **Repeatable Read / Snapshot Isolation does NOT prevent write skew**, and write skew is the anomaly that actually bites real systems.

Classic example: two on-call doctors, rule is "at least one must remain on call."
```
Both transactions read: "2 doctors on call" → both decide it's safe to go off call
Both write: set themselves off call
Result: 0 doctors on call — invariant violated
```
Each transaction read a consistent snapshot and wrote a *different* row, so there's no read-write conflict that SI detects. Only **Serializable** prevents this.

- **Postgres** implements Serializable via **SSI (Serializable Snapshot Isolation)** — optimistic. It runs at snapshot-isolation speed but tracks read/write dependencies; at commit it aborts one transaction in a dangerous cycle with a `could not serialize access` error. You **must** retry on `40001`. Low overhead unless contention is high.
- **MySQL InnoDB** implements Serializable via **2PL with next-key (gap) locks** — pessimistic. Reads take shared locks; this blocks rather than aborts, and can deadlock.
- **Cost framing**: SSI = cheap reads, possible abort+retry under contention. 2PL = blocking, deadlocks, but no retry logic needed. Know which your DB uses.

### Locking, Deadlocks, and Lost Updates

- **Lost update**: two transactions read balance=100, both add 10, both write 110 → one increment lost. Read Committed and Repeatable Read both allow this with a read-then-write pattern. Fixes: `SELECT ... FOR UPDATE` (pessimistic row lock), atomic `UPDATE balance = balance + 10` (let the DB serialize), or optimistic concurrency (`WHERE version = N`, retry on 0 rows).
- **Deadlock**: TX-A locks row 1 then waits on row 2; TX-B locks row 2 then waits on row 1. The DB's deadlock detector (runs ~every 1s in Postgres, immediate cycle detection in InnoDB) kills one victim with a deadlock error. **Mitigation**: always acquire locks in a consistent order (e.g., sort row IDs before locking), keep transactions short, and add retry-on-deadlock logic.
- **`SELECT FOR UPDATE` vs `FOR UPDATE SKIP LOCKED`**: `SKIP LOCKED` is the standard pattern for building a queue/job-dispatcher on a relational DB — workers grab unlocked rows without blocking each other.
- **Lock granularity**: row locks (cheap, high concurrency) vs table locks (DDL, `LOCK TABLE`). Postgres `ALTER TABLE ... ADD COLUMN` with a default used to rewrite + exclusive-lock the whole table; modern Postgres adds nullable/constant-default columns instantly. Schema migrations on hot tables are a real interview gotcha — use `CREATE INDEX CONCURRENTLY` to avoid blocking writes.

### Isolation Defaults by Engine (don't get this wrong)

| Engine | Default isolation | Serializable mechanism |
|---|---|---|
| PostgreSQL | Read Committed | SSI (optimistic) |
| MySQL InnoDB | Repeatable Read | 2PL + next-key locks (pessimistic) |
| Oracle | Read Committed | Snapshot ("Serializable" = SI, not true serializable) |
| SQL Server | Read Committed (lock-based) | 2PL; optional RCSI for MVCC reads |

Note MySQL's RR is *stronger* than the SQL-standard RR — next-key locks block most phantoms. Oracle's "Serializable" is actually snapshot isolation and permits write skew.

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
