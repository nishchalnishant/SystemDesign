> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** PostgreSQL internals — MVCC, WAL, vacuum, buffer pool, and index types; everything needed to answer deep interview follow-ups on PostgreSQL performance and correctness.
>
> **Key topics:**
> - MVCC: xmin (row created by txn) + xmax (row deleted by txn); readers see snapshot at txn start; readers never block writers
> - WAL (Write-Ahead Log): every change written to WAL before data pages; sequential I/O; enables crash recovery + streaming replication
> - VACUUM: reclaims dead tuple storage (from MVCC old versions); updates visibility map; prevents transaction ID wraparound
> - Buffer pool (shared_buffers): 8KB pages cached in RAM; dirty pages flushed at checkpoint; default 128MB, tune to 25% of RAM
> - Index types: B-tree (range, equality), Hash (equality only), GIN (full-text, JSONB arrays), BRIN (time-series, physically ordered data)
> - Table partitioning: declarative (RANGE, LIST, HASH) — each partition is a separate physical table; constraint exclusion prunes irrelevant partitions
> - Transaction isolation: Read Committed (default), Repeatable Read (snapshot), Serializable (SSI); each adds protection vs anomalies
>
> **Key takeaway:** PostgreSQL's MVCC is what makes it competitive for concurrent OLTP — but VACUUM must be tuned to keep dead tuple bloat from degrading performance over time.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# PostgreSQL Internals

## Overview

PostgreSQL is an advanced open-source relational database known for ACID compliance, complex queries, and extensibility. Understanding its internals is crucial for SDE-3 level system design and database interviews.

---

## File Mindmap

```
PostgreSQL Internals
├── Why It Exists
│   ├── Problem → need ACID compliance, complex queries, and high concurrency without reader-writer blocking
│   └── Physical limit → 8KB page size; large values require TOAST; random I/O (4.0 cost) vs sequential (1.0 cost)
├── MVCC (Multi-Version Concurrency Control)
│   ├── Every row has xmin (created by txn ID) and xmax (deleted by txn ID)
│   ├── Readers see snapshot of DB at their txn start → readers never block writers
│   ├── Snapshot visibility → row visible if xmin committed before snapshot AND (xmax null OR xmax not committed)
│   └── Cost: dead tuples accumulate from old row versions → VACUUM reclaims space
├── WAL (Write-Ahead Log)
│   ├── Sequential WAL write → flush WAL → ack client → async dirty page write to data files
│   ├── Checkpoint → flush dirty pages from shared_buffers to disk; crash recovery replays WAL from last checkpoint
│   ├── Streaming replication → WAL shipped to replicas (async by default, sync possible)
│   └── Key insight: WAL is sequential → fast; data page updates are random → async to avoid bottleneck
├── Storage Layout
│   ├── Pages → 8KB fixed-size pages; header + item pointers + actual tuples
│   ├── TOAST → "The Oversized-Attribute Storage Technique"; values > ~2KB compressed/chunked into separate TOAST table
│   └── Shared buffers → in-memory page cache; size: typically 25% of RAM
├── Index Types
│   ├── B-tree → default; O(log N); equality, range, ORDER BY; works for most cases
│   ├── GIN (Generalized Inverted Index) → arrays, JSONB, full-text search; expensive to build/update
│   ├── GiST → spatial data, geometric types; PostGIS uses GiST
│   └── BRIN (Block Range Index) → time-series / monotonically increasing columns; tiny index size; not for random access
├── Index Strategies
│   ├── Partial index → WHERE clause reduces size (CREATE INDEX ON orders(status) WHERE status='pending')
│   ├── Expression index → index on expression (CREATE INDEX ON users(lower(email)))
│   └── Covering index → INCLUDE columns to avoid heap fetch (index-only scan)
├── Query Execution
│   ├── Pipeline → parse → rewrite → plan → execute
│   ├── Cost estimation → seq_page_cost=1.0, random_page_cost=4.0, cpu_tuple_cost=0.01
│   ├── EXPLAIN ANALYZE → actual vs estimated rows; Seq Scan vs Index Scan vs Bitmap Heap Scan
│   └── N+1 problem → watch for Nested Loop with sequential scans in explain output
├── VACUUM
│   ├── Standard VACUUM → reclaims dead tuples; does NOT return space to OS; safe for production
│   ├── VACUUM FULL → rewrites table; returns space to OS; locks table; avoid in production
│   ├── Autovacuum → background daemon; triggered by dead tuple threshold
│   └── XID Wraparound → critical: 32-bit transaction ID wraps at 2 billion; autovacuum must run to freeze old XIDs; failure = DB refuses writes
├── Replication
│   ├── Physical (streaming) → byte-for-byte WAL copy; same Postgres version required; used for HA standby
│   └── Logical → SELECT-level replication; cross-version, selective tables; used for zero-downtime upgrades
├── Transaction Isolation Levels
│   ├── Read Committed (default) → sees committed data at statement start; phantom reads possible
│   ├── Repeatable Read → snapshot at txn start; no phantom reads in Postgres (MVCC)
│   └── Serializable → full serializability via predicate locks; highest isolation, most contention
├── Locking
│   ├── Row-level locks → SELECT FOR UPDATE; FOR SHARE; FOR NO KEY UPDATE
│   ├── Table-level locks → DDL (ALTER TABLE) takes AccessExclusiveLock; blocks all reads + writes
│   └── Deadlock detection → background process detects cycles; aborts one txn; rare with consistent lock ordering
├── Trade-offs
│   ├── Pro: ACID, rich indexing, MVCC for concurrency, extensible types
│   ├── Con: VACUUM overhead; dead tuple bloat if autovacuum misconfigured
│   └── Con: write scaling requires sharding (no built-in horizontal write scale)
└── Interview Angles
    ├── "Why is my query slow?" → EXPLAIN ANALYZE → missing index vs bad statistics vs N+1
    ├── "How does Postgres handle concurrent reads/writes?" → MVCC; readers never block writers
    └── Follow-up: XID wraparound emergency → VACUUM FREEZE on affected tables before ID limit
```

## MVCC (Multi-Version Concurrency Control)

### Analogy: A Library Book Checkout System

Imagine a library with a single copy of a book. In a naive locking system, when you check out the book, nobody else can read it until you return it. The library is blocked.

PostgreSQL's MVCC works differently. When you sit down to read the book (start a transaction), a librarian hands you a **photocopy of the book as it existed at that moment**. While you are reading your copy:

- Someone else can check out the book.
- A librarian can stamp a new edition (another transaction updates the data).
- You still see **your original copy** — your snapshot — unaffected by anything happening around you.

When you finish reading (your transaction ends), your photocopy is discarded. No blocking. No waiting. Readers never block writers; writers never block readers.

### Core Concept

Instead of locking rows, PostgreSQL creates **multiple versions** of each row to allow concurrent reads and writes.

### Transaction IDs (XID)

- Each transaction gets a unique, monotonically increasing integer ID.
- Every row tuple stores:
  - `xmin`: the transaction ID that **created** this row version.
  - `xmax`: the transaction ID that **deleted** (or updated) this row version. NULL means the row is still current.

### Tuple Visibility Example

```
Transaction 100: INSERT INTO users VALUES (1, 'Alice');
  → Row: (id=1, name='Alice', xmin=100, xmax=NULL)
        ↑ "Created by txn 100; nobody has deleted it yet"

Transaction 101: UPDATE users SET name='Bob' WHERE id=1;
  → Old Row: (id=1, name='Alice', xmin=100, xmax=101)
             ↑ "Created by txn 100; deleted by txn 101"
  → New Row: (id=1, name='Bob',  xmin=101, xmax=NULL)
             ↑ "Created by txn 101; still current"

Transaction 102 (started BEFORE txn 101 committed):
  → Sees 'Alice' — its snapshot predates txn 101

Transaction 103 (started AFTER txn 101 committed):
  → Sees 'Bob' — its snapshot includes txn 101
```

Each transaction carries a **snapshot**: a list of committed transaction IDs as of when it started. It only sees rows whose `xmin` is in its snapshot.

### Visibility Rules

A tuple is visible to transaction T if:
1. `xmin` is committed AND `xmin < T.snapshot` (the row was created by a transaction T can see)
2. `xmax` is NULL OR `xmax` is not committed OR `xmax > T.snapshot` (the row has not been deleted from T's perspective)

### MVCC Trade-off: Dead Tuples

Every UPDATE creates a new row version and marks the old one as dead (not deleted from disk). Every DELETE marks the old version as dead. Dead tuples accumulate over time and must be cleaned up by VACUUM. This is the price you pay for lock-free concurrency.

---

## Write-Ahead Logging (WAL)

### Analogy: A Chef's Order Ticket Rail

In a busy restaurant, before the kitchen starts cooking (writing data to disk), the order must first be clipped to the **ticket rail** — the long metal strip above the pass where all orders are recorded in sequence.

If the kitchen burns down (server crash), the chef can reconstruct every unfinished order by reading the tickets from the rail. Nothing is lost because the record was made *before* the cooking started.

PostgreSQL's WAL is that ticket rail. Every change is logged to WAL **before** the actual data page is modified on disk.

### Purpose

- **Durability**: Ensure transactions survive crashes.
- **Replication**: Ship WAL to standby replicas.
- **Point-in-Time Recovery (PITR)**: Replay WAL to restore the database to any past moment.

### How WAL Works

```
Transaction commits
    ↓
Write change to WAL buffer (memory)
    ↓
Flush WAL buffer to WAL file on disk (sequential write)
    ↓
Acknowledge commit to client  ← Client sees "success" here
    ↓ (asynchronously, later)
Write actual data pages to disk (the heap files)
```

**Key insight**: The WAL is written **sequentially** to disk — extremely fast. The actual data pages are written **later**, potentially in random order (page modifications are batched and written during checkpoints). Sequential WAL writes are the reason PostgreSQL can sustain high write throughput.

### WAL Segments

- Fixed-size files, default 16MB each.
- Named with a monotonically increasing sequence: `000000010000000000000001`, `000000010000000000000002`, etc.
- Old WAL segments are recycled (or archived for PITR) once they are no longer needed for recovery.

### Checkpoints

A **checkpoint** is a moment when PostgreSQL guarantees that all dirty data pages (in memory but not yet written to disk) are flushed to the heap files.

- Without checkpoints, crash recovery would require replaying the entire WAL history.
- With checkpoints, recovery only needs to replay WAL since the last checkpoint — much faster.
- Controlled by `checkpoint_timeout` (default: 5 min) and `max_wal_size`.

### Crash Recovery

1. Identify the last completed checkpoint.
2. Replay all WAL records from that checkpoint forward.
3. **Redo** committed transactions (apply their changes to data pages).
4. **Undo** uncommitted transactions (roll them back).
5. Database is now consistent.

---

## Storage: Pages & TOAST

### Page Structure

PostgreSQL stores all data in **8KB pages**. Every table, every index is a collection of 8KB pages.

```
+------------------+
| Page Header      |  24 bytes — LSN, checksum, free space info
+------------------+
| Item Pointers    |  4 bytes each — pointers to tuple locations in this page
+------------------+
| Free Space       |  Available for new tuples
+------------------+
| Tuples           |  The actual row data (contains xmin, xmax, columns)
+------------------+
| Special Space    |  Index-specific metadata
+------------------+
```

Each 8KB page is the unit of I/O — when PostgreSQL needs one row, it reads the entire 8KB page into `shared_buffers` (the buffer pool in RAM).

### TOAST (The Oversized Attribute Storage Technique)

**Problem**: A row with a 50MB JSON field cannot fit in an 8KB page.

**TOAST solution**: Large values are compressed and/or stored out-of-line in a separate TOAST table, chunked into 2KB pieces. The main table row stores a pointer to the TOAST chunks. This is transparent to queries — you still `SELECT large_column FROM table` and get the full value.

| Strategy | Compression | Out-of-Line Storage |
|---|---|---|
| `PLAIN` | No | No (fails if row too large) |
| `EXTENDED` | Yes (try first) | Yes (if still too large) — default |
| `EXTERNAL` | No | Yes |
| `MAIN` | Yes | Avoid if possible |

---

## Indexes

### B-Tree — The Dewey Decimal System

**Analogy**: A library organized using the Dewey Decimal System. To find a book numbered "515.3", you walk to the 500s section, then to 515, then to 515.3. Each step halves your search space. With millions of books, you find it in just a few steps — O(log N).

PostgreSQL's B-tree index works identically: a balanced tree of sorted keys. Finding a value takes O(log N) page reads down the tree.

```
Root Page (contains ranges: ≤100, 101-200, 201-300...)
  ├─ Internal Page 1 (≤100)
  │   ├─ Leaf Page: rows 1-50
  │   └─ Leaf Page: rows 51-100
  └─ Internal Page 2 (101-200)
      ├─ Leaf Page: rows 101-150
      └─ Leaf Page: rows 151-200
```

- Supports: `<`, `<=`, `=`, `>=`, `>`, `BETWEEN`, `IN`, `LIKE 'prefix%'`
- Tree height is typically 3–4 levels for tens of millions of rows.
- Default index type. Use it for most columns in `WHERE`, `JOIN`, `ORDER BY`.

```sql
CREATE INDEX idx_users_email ON users(email);
-- Now: SELECT * FROM users WHERE email = 'alice@example.com'
-- Uses the index: O(log N) instead of O(N) full table scan
```

### GIN — Generalized Inverted Index

For multi-valued columns: arrays, JSONB, full-text search vectors.

GIN builds an **inverted index**: instead of mapping row → values, it maps value → list of rows containing it.

```sql
CREATE INDEX idx_articles_tags ON articles USING GIN(tags);

-- tags is an array column: tags = ['postgres', 'database']
-- GIN index maps:
'postgres' → [row1, row3, row5]
'database' → [row1, row2, row4]

-- Query: find all articles tagged 'postgres'
SELECT * FROM articles WHERE tags @> ARRAY['postgres'];
-- Uses GIN: directly jump to 'postgres' → [row1, row3, row5]
```

### GiST — Generalized Search Tree

For spatial and geometric data, used by PostGIS for proximity and bounding-box queries.

### BRIN — Block Range Index

For very large tables with natural physical ordering (e.g., time-series tables where `created_at` increases monotonically).

BRIN stores only the **min and max values per block range** — a tiny index for a huge table.

```
Block range 1-128:   min_created_at=2024-01-01, max=2024-01-31
Block range 129-256: min_created_at=2024-02-01, max=2024-02-29
```

A query for January data skips February's block ranges immediately. The index is tiny (kilobytes) even for billion-row tables, because it only stores one entry per block range.

---

## Query Execution

### Analogy: A GPS Choosing the Fastest Route

When you type a destination into GPS, it considers multiple routes: fastest (highway), shortest distance (back roads), avoiding tolls. It picks the route with the lowest estimated travel time based on current conditions.

PostgreSQL's **query planner** does the same thing. For `SELECT * FROM orders WHERE user_id = 123`, it considers:
- **Index scan** on `user_id` — fast if few rows match.
- **Sequential scan** — faster if 90% of rows match (cheaper to scan the whole table than bounce around the index).

The planner uses **statistics** (from `ANALYZE`) to estimate how many rows match. It picks the plan with the lowest estimated cost.

### Query Lifecycle

#### 1. Parse SQL → Abstract Syntax Tree (AST)

```
SELECT * FROM users WHERE age > 25
  ↓
Parse Tree (AST)
```

#### 2. Rewrite (Apply Rules)

View expansion, rule system transformations.

#### 3. Plan (Choose Execution Strategy)

- Sequential Scan vs. Index Scan
- Join algorithms: Nested Loop, Hash Join, Merge Join
- Cost-based optimization using statistics

#### 4. Execute

Walk the plan tree and produce rows.

### Cost Estimation

PostgreSQL estimates the cost of each plan using:

```
seq_page_cost = 1.0         (cost of reading one page sequentially)
random_page_cost = 4.0      (cost of reading one page randomly — ~4x slower)
cpu_tuple_cost = 0.01       (CPU cost per row processed)
effective_cache_size = 75% of RAM  (hint: data likely in OS page cache)
```

```
Seq Scan cost   = (pages × seq_page_cost) + (rows × cpu_tuple_cost)
Index Scan cost = (matched rows × random_page_cost) + (rows × cpu_tuple_cost)
```

If 5% of rows match, Index Scan wins. If 90% of rows match, Sequential Scan wins — reading the whole table sequentially is cheaper than 90% random page reads.

### Table Statistics

```sql
ANALYZE users;   -- Collect statistics
-- Stored in pg_stats: histograms, most common values, null fraction, row estimates
```

Stale statistics cause the planner to choose wrong plans. Run `ANALYZE` after bulk loads. Autovacuum runs it automatically.

### EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123;

-- Output includes:
-- Seq Scan / Index Scan decision
-- Estimated rows vs actual rows
-- Execution time (actual)
-- Buffer hits vs disk reads
```

High "actual rows" vs "estimated rows" divergence means stale statistics — run `ANALYZE`.

---

## Vacuum & Autovacuum

### Analogy: Library Shelf Re-Organization

MVCC leaves dead row versions on the shelves — old "Alice" versions that nobody needs anymore because everyone sees "Bob" now. The library is filling with phantom books taking up space.

**VACUUM** is the periodic shelf re-organization. A librarian walks through and removes all the phantom books (dead tuples), reclaims shelf space, and updates the card catalog (statistics) so the query planner knows what is actually there.

### Why Dead Tuples Exist

Every UPDATE creates a new row version and leaves the old one dead. Every DELETE marks a row as dead. MVCC requires these dead versions to exist until no active transaction can see them anymore. Then they become garbage.

### VACUUM (Standard)

- Marks dead tuples as reusable space.
- Does **not** shrink the physical table file — the space is available for future inserts, but the file on disk stays the same size.
- Non-blocking: runs concurrently with reads and writes.

### VACUUM FULL

- Rewrites the entire table into a new, compact file.
- **Reclaims disk space** (shrinks the file).
- Requires an **exclusive lock** on the table — disruptive in production. Use during maintenance windows only.

### Autovacuum

Background daemon that automatically triggers VACUUM when a table accumulates enough dead tuples.

Default trigger threshold:
```
dead_tuples > 50 + (0.2 × live_tuples)
```

For a table with 1 million live rows, autovacuum triggers after 200,050 dead tuples accumulate.

### Tuning Autovacuum for High-Write Tables

```sql
-- More aggressive for high-write tables
ALTER TABLE orders SET (
    autovacuum_vacuum_scale_factor = 0.05,  -- Trigger at 5% dead (not 20%)
    autovacuum_vacuum_cost_delay = 10       -- Less throttling
);
```

### Transaction ID Wraparound — The Critical Risk

PostgreSQL transaction IDs are 32-bit integers — approximately 4 billion values. When all 4 billion are used, the counter wraps around. Rows with old XIDs become invisible to new transactions ("frozen" or "missing").

VACUUM prevents this by **freezing** old row XIDs — replacing them with a special "frozen" marker that is always visible. This is why autovacuum must never be disabled on a healthy production database.

---

## Replication

### Physical Replication (Streaming)

**WAL shipping**: The primary sends a continuous stream of WAL records to standbys. Standbys replay the WAL, maintaining a byte-for-byte copy of the primary.

```
Primary (read/write)
    ↓ WAL stream (continuous)
Standby 1 (read-only hot standby)
Standby 2 (read-only hot standby)
```

**Synchronous replication**: Primary waits for standby to acknowledge WAL receipt before confirming commit to client. Zero data loss on failover. Higher latency.

**Asynchronous replication**: Primary does not wait. Lower latency, but a small window of data loss on primary crash (data in transit to standby).

### Logical Replication

Replicates specific **tables** rather than the entire database at the byte level.

- Supports different PostgreSQL versions between publisher and subscriber.
- Enables selective replication (replicate only `orders` table to the analytics replica).
- **Use cases**: Upgrading PostgreSQL versions with minimal downtime, cross-region partial replication, data warehousing.

---

## Transaction Isolation Levels

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|------------|---------------------|--------------|
| **Read Uncommitted** | (Postgres treats as RC) | Yes | Yes |
| **Read Committed** | No | Yes | Yes |
| **Repeatable Read** | No | No | No (Postgres, unlike SQL standard) |
| **Serializable** | No | No | No |

### Read Committed (PostgreSQL Default)

- Sees only committed data.
- Snapshot taken **per query** within the transaction.
- Non-repeatable reads possible: two queries in the same transaction can see different data if a concurrent transaction commits between them.

### Repeatable Read

- Snapshot taken **per transaction** (not per query).
- Consistent view throughout the entire transaction.
- No phantom reads in PostgreSQL (unlike the SQL standard — PostgreSQL's MVCC naturally prevents them at this level).

### Serializable (SSI — Serializable Snapshot Isolation)

- Transactions execute as if they ran one at a time in some serial order.
- If a serialization conflict is detected (two transactions would produce a result inconsistent with any serial ordering), one is aborted with a serialization failure error.
- Application must retry aborted transactions.

---

## Locks

### Row-Level Locks

```sql
SELECT * FROM orders WHERE id = 123 FOR UPDATE;   -- Exclusive lock on this row
SELECT * FROM orders WHERE id = 123 FOR SHARE;    -- Shared lock (allows concurrent reads)
```

### Table-Level Locks

- `ACCESS SHARE`: Acquired by `SELECT`. Compatible with everything except `ACCESS EXCLUSIVE`.
- `ROW EXCLUSIVE`: Acquired by `INSERT`, `UPDATE`, `DELETE`. Compatible with reads.
- `EXCLUSIVE`: Blocks all concurrent access except `ACCESS SHARE`.
- `ACCESS EXCLUSIVE`: Acquired by `ALTER TABLE`, `DROP TABLE`, `VACUUM FULL`. Blocks everything.

### Deadlock Detection

PostgreSQL checks for deadlocks every `deadlock_timeout` (default: 1 second). When detected, it aborts one of the transactions involved in the cycle. The aborted transaction receives an error and must retry.

---

## Performance Tuning

### Key Memory Parameters

```
shared_buffers = 25% of RAM        # PostgreSQL's own buffer pool (data cache)
work_mem = 4MB per sort/hash op    # Per-operation memory for sorts, hash joins
maintenance_work_mem = 1GB         # For VACUUM, index creation, pg_restore
effective_cache_size = 75% of RAM  # Hint to the planner: "this much data is likely cached"
```

`shared_buffers` is the most impactful parameter. On a 32GB machine: `shared_buffers = 8GB`. The OS page cache handles the remaining ~16–24GB, which PostgreSQL also benefits from.

### Checkpoint Parameters

```
checkpoint_timeout = 15min   # How often checkpoints occur (default: 5min)
max_wal_size = 2GB           # Also triggers checkpoints (default: 1GB)
```

More frequent checkpoints = faster crash recovery (less WAL to replay) but higher I/O overhead during normal operation.

### Connection Parameters

```
max_connections = 100    # PostgreSQL spawns one process per connection (~10MB RAM each)
```

PostgreSQL connections are expensive. Use **PgBouncer** (connection pooler) to multiplex many application connections over a small pool of actual PostgreSQL connections. This is nearly always required at scale.

### Index Best Practices

```sql
-- Index columns used in WHERE, JOIN ON, ORDER BY
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Multi-column index: most selective column first
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- Covering index (index-only scan — never touches heap)
CREATE INDEX idx_orders_covering ON orders(user_id) INCLUDE (total, created_at);

-- Partial index: only index rows matching a condition
CREATE INDEX idx_orders_pending ON orders(created_at) WHERE status = 'PENDING';
```

---

## Common Pitfalls

### Not Running VACUUM

Dead tuples accumulate → bloated tables → slower sequential scans and index scans → eventually, transaction ID wraparound risk. Never disable autovacuum. Tune it aggressively for high-write tables.

### Missing Indexes

Sequential scans on million-row tables. Use `EXPLAIN ANALYZE` to identify. Look for `Seq Scan` on large tables with a low estimated row count.

### N+1 Query Problem

Fetching 1 order, then 1 query per order line item = N+1 queries. Use JOINs, `IN` clauses, or CTEs to batch. Use `WITH` (CTEs) for complex multi-step queries.

### No Connection Pooling

Each PostgreSQL connection uses ~10MB of RAM and spawns a process. At 1000 concurrent connections, that is 10GB of RAM just for connection overhead. Use PgBouncer in transaction-mode pooling.

### Unoptimized LIKE Queries

```sql
-- Uses index (prefix match)
WHERE email LIKE 'alice%'

-- Cannot use B-tree index (leading wildcard)
WHERE email LIKE '%alice%'
-- Solution: use pg_trgm extension + GIN index for substring search
```

---

## Interview Questions

**Q: Explain MVCC in PostgreSQL.**
- Creates multiple versions of rows using `xmin`/`xmax` transaction IDs.
- Each transaction sees a snapshot of committed data as of its start time.
- No read locks — readers never block writers, writers never block readers.
- Trade-off: Dead tuples accumulate and require periodic VACUUM to reclaim space.

**Q: What is WAL and why is it needed?**
- Write-Ahead Log: every change is logged sequentially before the data page is modified.
- Sequential WAL writes are fast (disk is fast for sequential I/O).
- Used for crash recovery (replay from last checkpoint), replication (ship WAL to standbys), and point-in-time recovery (replay to any past moment).

**Q: Index Scan vs. Sequential Scan — when does each happen?**
- Query planner chooses based on cost estimation using table statistics.
- **Index Scan**: When fetching a small fraction of rows (~5-10%). Random page reads are expensive but fewer of them.
- **Sequential Scan**: When fetching a large fraction of rows. Reading the whole table sequentially is cheaper than many random page accesses.
- Affected by `random_page_cost` vs `seq_page_cost` ratio and table row count.

**Q: How does PostgreSQL handle high write load?**
- WAL batching: multiple transactions share a single WAL flush, reducing I/O.
- Asynchronous commit: `synchronous_commit=off` — acknowledge before WAL flush (small data loss risk, much lower latency).
- Connection pooling with PgBouncer to handle many clients.
- Table partitioning to distribute I/O across multiple physical files.
- Tune `shared_buffers`, `checkpoint_timeout`, `max_wal_size`, `work_mem`.

**Q: What happens when VACUUM doesn't run?**
- Dead tuples accumulate → table bloat → slower scans.
- Index bloat from dead index entries.
- Eventually, transaction ID wraparound: all ~4 billion XIDs exhausted → database goes into emergency freeze mode, refusing writes until manual intervention.
- This is one of the most catastrophic PostgreSQL failures. Never disable autovacuum.
