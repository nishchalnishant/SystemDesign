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

> An advanced open-source RDBMS with ACID compliance, MVCC for concurrent reads/writes, and a rich extensibility model.

---

## 1. Why PostgreSQL Exists

**Question**: A web app has 100 concurrent users reading and writing the same tables. Naïve locking blocks everyone. The team needs full ACID transactions, complex queries (joins, subqueries, window functions), and the ability to add custom types and operators. How?

**Physical constraint**: Lock-based concurrency scales poorly — readers block writers and vice versa. A single write leader caps at ~10K-50K writes/sec. Complex queries need a sophisticated optimizer. The CAP theorem forces trade-offs; SQL's demand for strong consistency pushes us to CP.

**Minimal solution**: Multi-Version Concurrency Control (MVCC) — every row update creates a new version; old versions are kept until no transaction can see them. Readers see a consistent snapshot; writers don't block readers. This is what makes PostgreSQL viable for concurrent OLTP.

**Production generalization**: PostgreSQL has been the most advanced open-source RDBMS for 25+ years. MVCC via xmin/xmax, a cost-based optimizer, a wide index type catalog (B-tree, hash, GIN, GiST, BRIN, SP-GiST), logical replication, JSONB, PostGIS for geo, and an extension ecosystem. Default for new systems when you don't need NoSQL scale.

---

## 2. MVCC (Multi-Version Concurrency Control)

Every row tuple carries two system columns: `xmin` (transaction that created it) and `xmax` (transaction that deleted or updated it; NULL means still current). Every transaction has a monotonically increasing ID. When a transaction starts, it takes a snapshot of which XIDs are committed and which are in flight.

```
Transaction 100: INSERT INTO users VALUES (1, 'Alice');
  → Row: (id=1, name='Alice', xmin=100, xmax=NULL)

Transaction 101: UPDATE users SET name='Bob' WHERE id=1;
  → Old Row: (id=1, name='Alice', xmin=100, xmax=101)  ← "dead tuple"
  → New Row: (id=1, name='Bob',  xmin=101, xmax=NULL)

Transaction 102 (started BEFORE txn 101 committed):
  → Sees 'Alice' — its snapshot predates txn 101

Transaction 103 (started AFTER txn 101 committed):
  → Sees 'Bob' — its snapshot includes txn 101
```

### Visibility Rules

A tuple is visible to transaction T if:
1. `xmin` is committed AND `xmin < T.snapshot` (creator is visible to T)
2. `xmax` is NULL OR `xmax` is not committed OR `xmax > T.snapshot` (deleter is not visible to T)

### Why It Works

- **Readers never block writers**: a reader sees its snapshot, regardless of concurrent writes
- **Writers never block readers**: a writer creates a new version; old readers continue seeing the old version
- **Lock-free reads**: readers take no shared locks on rows

### Cost: Dead Tuples

Every UPDATE creates a new row version and marks the old one as dead. Every DELETE marks the row as dead. Dead tuples accumulate on disk and in indexes until VACUUM reclaims them. This is the price of MVCC.

---

## 3. WAL (Write-Ahead Log)

Every change is appended to the WAL **before** the corresponding data page is modified. WAL is sequential, fast, and durable.

```
Transaction commits
    ↓
Write change to WAL buffer (in memory)
    ↓
Flush WAL buffer to WAL file on disk (sequential, fsync)
    ↓
ACK to client                                              ← Client sees "success"
    ↓ (asynchronously, later)
Write actual data pages to disk (the heap files) — at checkpoint
```

**Key insight**: the WAL is sequential → fast. Data page writes are random → deferred to checkpoint. Sequential WAL writes are why PostgreSQL can sustain high write throughput without sacrificing durability.

### WAL Segments

- Fixed-size files, default 16 MB each
- Named with a monotonic sequence: `000000010000000000000001`, `000000010000000000000002`, ...
- Old segments recycled (or archived for PITR) once no longer needed for recovery

### Checkpoint

A moment when PostgreSQL flushes all dirty data pages to disk.

- Without checkpoints, crash recovery would replay the entire WAL history
- With checkpoints, recovery only replays WAL since the last checkpoint
- Configured by `checkpoint_timeout` (default 5 min) and `max_wal_size` (default 1 GB)

### Crash Recovery

1. Identify last completed checkpoint
2. Replay all WAL records forward from that checkpoint
3. Redo committed transactions; undo uncommitted
4. Database is consistent

### Replication

WAL is the source of truth for replicas:
- **Physical (streaming)**: byte-for-byte WAL stream to standby; same PG version required; HA standby
- **Logical**: SELECT-level replication; cross-version; selective tables; zero-downtime upgrades

---

## 4. Storage: Pages & TOAST

### Page Structure

PostgreSQL stores all data in **8 KB pages**. Every table, every index is a collection of 8 KB pages.

```
+------------------+
| Page Header      |  24 bytes — LSN, checksum, free space info
+------------------+
| Item Pointers    |  4 bytes each — pointers to tuple locations
+------------------+
| Free Space       |
+------------------+
| Tuples           |  Actual row data (xmin, xmax, columns)
+------------------+
| Special Space    |  Index-specific metadata
+------------------+
```

### TOAST (The Oversized-Attribute Storage Technique)

A 50 MB JSONB field cannot fit in 8 KB. TOAST compresses and/or stores large values out-of-line in a separate TOAST table, chunked into ~2 KB pieces. The main table row holds a pointer. Transparent to queries.

| Strategy | Compression | Out-of-Line | Behavior |
|----------|-------------|-------------|----------|
| `PLAIN` | No | No | Fails if row > 2 KB |
| `EXTENDED` | Yes (try first) | Yes (if still too large) | Default |
| `EXTERNAL` | No | Yes | Faster extraction; no compression savings |
| `MAIN` | Yes | Avoid if possible | Try to keep in-row |

---

## 5. VACUUM & Autovacuum

MVCC leaves dead tuples on the heap. VACUUM reclaims the space and updates the visibility map (which pages have only visible tuples — can skip in index-only scans).

### VACUUM vs VACUUM FULL

| | Standard VACUUM | VACUUM FULL |
|---|---|---|
| What it does | Marks dead tuples as reusable | Rewrites the entire table into a new compact file |
| Space to OS | No (file stays the same size) | Yes (file shrinks) |
| Lock | None | AccessExclusiveLock (blocks everything) |
| Use case | Routine; safe in production | Maintenance windows; rare |

### Autovacuum

Background daemon that triggers VACUUM (and ANALYZE) automatically when a table accumulates enough dead tuples.

Default trigger: `dead_tuples > 50 + (0.2 × live_tuples)`. For 1M live rows → triggers after 200,050 dead tuples.

### Tuning for High-Write Tables

```sql
ALTER TABLE orders SET (
    autovacuum_vacuum_scale_factor = 0.05,  -- trigger at 5% dead (not 20%)
    autovacuum_vacuum_cost_delay = 10       -- less throttling
);
```

### Transaction ID Wraparound — The Critical Risk

XIDs are 32-bit integers — ~4 billion values. When all 4 billion are used, the counter wraps around. Rows with old XIDs become invisible to new transactions.

VACUUM prevents this by **freezing** old row XIDs (replaces them with a special "frozen" marker that is always visible). Autovacuum must never be disabled on a healthy production database. If it falls behind, the database goes into emergency mode and refuses writes until manual `VACUUM FREEZE` is run.

---

## 6. Indexes

| Type | Use case | Notes |
|------|----------|-------|
| **B-tree** (default) | Equality, range, ORDER BY, LIKE 'prefix%' | O(log N); most common |
| **Hash** | Equality only | Since PG 10; rarely beats B-tree |
| **GIN** (Generalized Inverted) | Arrays, JSONB, full-text `tsvector` | Expensive to build; slow updates |
| **GiST** (Generalized Search Tree) | Spatial, geometric, full-text | PostGIS uses GiST |
| **BRIN** (Block Range) | Time-series, physically ordered data | Tiny index; range scans per block |
| **SP-GiST** | Space-partitioned: quadtree, kd-tree, radix tree | Specialized |

For deep dive on tree internals, see [index-structures.md](index-structures.md).

### Index Strategies

```sql
-- Partial index: only index rows matching a predicate
CREATE INDEX idx_pending ON orders(created_at) WHERE status = 'PENDING';

-- Expression index: index on a function
CREATE INDEX idx_lower_email ON users(lower(email));

-- Covering index (PostgreSQL 11+): INCLUDE columns for index-only scan
CREATE INDEX idx_covering ON orders(user_id, status) INCLUDE (total, created_at);

-- Composite index column order: equality, range, sort
CREATE INDEX idx_status_date ON orders(status, created_at);
-- WHERE status = 'PENDING' AND created_at > '2024-01-01' ORDER BY created_at
-- Uses both columns; no sort needed
```

---

## 7. Query Execution

```
SQL text
  → Parse (lex + parse)          → raw parse tree
  → Analyze (semantic)            → query tree
  → Rewrite (apply rules)         → rewritten tree (views, RLS)
  → Plan (optimizer)              → plan tree
  → Execute                      → rows
```

### Cost-Based Optimizer

PostgreSQL uses statistics (from `ANALYZE`) to estimate the cost of each plan:

```
seq_page_cost = 1.0          (one sequential page read)
random_page_cost = 4.0       (one random page read — ~4× more expensive)
cpu_tuple_cost = 0.01        (CPU per row)
effective_cache_size = 75% of RAM  (hint: data likely in OS page cache)
```

If 5% of rows match, Index Scan wins. If 90% match, Sequential Scan wins.

### EXPLAIN ANALYZE

```sql
EXPLAIN ANALYZE SELECT * FROM orders WHERE user_id = 123;
-- Output: plan tree with estimated vs actual row counts, buffer hits, timing
```

Look for: high divergence between estimated and actual rows (stale stats), `Seq Scan` on a large table (missing index), `Using filesort` (no index for ORDER BY), `Using temporary` (hash join spilling to disk).

---

## 8. Transaction Isolation

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Notes |
|-------|------------|---------------------|--------------|-------|
| **Read Uncommitted** | (PG treats as RC) | Yes | Yes | Effectively unused |
| **Read Committed** (default) | No | Yes | Yes | Snapshot per statement |
| **Repeatable Read** | No | No | No (PG's MVCC) | Snapshot per transaction |
| **Serializable (SSI)** | No | No | No | Predicate locks; conflict detection |

### Read Committed (default)

Snapshot taken **per statement**. Within one transaction, two queries may see different data if a concurrent commit lands between them.

### Repeatable Read

Snapshot taken **per transaction**. Consistent view throughout. In PostgreSQL, no phantom reads (unlike SQL standard) because MVCC handles it.

### Serializable (SSI)

SSI (Serializable Snapshot Isolation) adds predicate locks. Detects serialization conflicts; one transaction is aborted with `serialization_failure`. Application must retry.

---

## 9. Locking

### Row-Level Locks

```sql
SELECT * FROM orders WHERE id = 123 FOR UPDATE;     -- exclusive
SELECT * FROM orders WHERE id = 123 FOR SHARE;      -- shared
SELECT * FROM orders WHERE id = 123 FOR NO KEY UPDATE;  -- weaker exclusive
```

### Table-Level Locks

| Mode | Blocks |
|------|--------|
| `ACCESS SHARE` | `ACCESS EXCLUSIVE` only (taken by `SELECT`) |
| `ROW EXCLUSIVE` | `SHARE`, `EXCLUSIVE`, `ACCESS EXCLUSIVE` (taken by DML) |
| `EXCLUSIVE` | Everything except `ACCESS SHARE` |
| `ACCESS EXCLUSIVE` | Everything (taken by `ALTER TABLE`, `DROP TABLE`, `VACUUM FULL`) |

### Deadlock Detection

Background process scans `pg_locks` and wait-for graph every `deadlock_timeout` (default 1s). On cycle detected, aborts one transaction with error 40P01. Application must retry. Mitigation: consistent lock ordering across all transactions.

---

## 10. Real-World Usage

| Use case | Notes |
|----------|-------|
| **OLTP web apps** | Default; transactions, complex queries, JSONB |
| **Time-series** | Partitioning by time + BRIN; pg_partman extension |
| **Full-text search** | GIN on `tsvector`; ranking |
| **Geo** | PostGIS on top of GiST; OpenStreetMap queries |
| **JSONB document store** | Rich indexing, `$` operators, GIN on JSON paths |
| **Analytics** | Window functions, CTEs, materialized views; not optimized for OLAP at scale |
| **Multi-tenant SaaS** | Row-level security; schema-per-tenant; partition per tenant |

---

## 11. Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **ACID** | Strong consistency, transactions | No horizontal write scale built-in |
| **MVCC** | Lock-free reads, snapshot isolation | Dead tuple bloat; VACUUM overhead |
| **Indexing** | Rich catalog (B-tree, GIN, GiST, BRIN, ...) | Each index slows writes |
| **Extensibility** | Custom types, operators, functions, languages | More surface area to learn |
| **SQL compliance** | Most standards-compliant open-source DB | Cost: complex optimizer; harder to tune |
| **Replication** | Physical + logical, sync + async | Logical has row-filter limits |

---

## 12. Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| **VACUUM not running** | Dead tuple bloat; queries slow | Never disable autovacuum; tune for high-write tables |
| **XID wraparound** | Database refuses writes; "database is in emergency mode" | `VACUUM FREEZE` on affected tables; restore autovacuum |
| **Missing index** | Sequential scan on million-row table; `EXPLAIN` shows `Seq Scan` | Add index on WHERE/JOIN/ORDER BY columns |
| **N+1 query problem** | ORM fetches 1 + N queries | Use JOINs, eager loading, `IN`, or batch endpoints |
| **No connection pooling** | 10 GB RAM for 1000 connections; context switching | PgBouncer in transaction mode |
| **Long transaction** | VACUUM can't clean; replicas lag | Commit/rollback sooner; `idle_in_transaction_session_timeout` |
| **Unoptimized LIKE** | `LIKE '%foo%'` does sequential scan | `pg_trgm` extension + GIN index |
| **Replication lag** | Standby falls behind; read-after-write fails | Check network; reduce long transactions; tune `wal_compression` |

---

## 13. Performance Tuning

### Memory Parameters

```
shared_buffers           = 25% of RAM       # PostgreSQL's own buffer pool
work_mem                 = 4MB per sort/op  # Per-operation memory
maintenance_work_mem     = 1GB              # For VACUUM, index build, pg_restore
effective_cache_size     = 75% of RAM       # Hint: data likely in OS page cache
huge_pages               = try              # Use 2 MB pages if available
```

### WAL & Checkpoint

```
wal_buffers               = 16MB
checkpoint_timeout        = 15min
max_wal_size              = 2GB
min_wal_size              = 512MB
wal_compression           = on
```

### Connections

```
max_connections           = 100    # Spawns one process per connection (~10 MB)
```

Use **PgBouncer** (transaction-mode pooler) to multiplex thousands of client connections over a small pool. Almost always required at scale.

### Index Best Practices

```sql
-- Index columns used in WHERE, JOIN, ORDER BY
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- Composite: most selective equality column first
CREATE INDEX idx_orders_user_status ON orders(user_id, status);

-- Covering (index-only scan)
CREATE INDEX idx_orders_covering ON orders(user_id) INCLUDE (total, created_at);

-- Partial
CREATE INDEX idx_pending ON orders(created_at) WHERE status = 'PENDING';
```

---

## 14. Implementation Patterns

### Java/JDBC — Connection Pool + Prepared Statement

```java
// HikariCP configuration tuned for OLTP
HikariConfig config = new HikariConfig();
config.setJdbcUrl("jdbc:postgresql://db1,db2,db3/app?targetServerType=primary");
config.setUsername("app");
config.setMaximumPoolSize(20);
config.setMinimumIdle(5);
config.setConnectionTimeout(30_000);
config.setIdleTimeout(600_000);
config.setMaxLifetime(1_800_000);
config.addDataSourceProperty("reWriteBatchedInserts", "true");
HikariDataSource ds = new HikariDataSource(config);

// Batch insert — reWriteBatchedInserts=true turns this into one multi-row INSERT
try (Connection conn = ds.getConnection();
     PreparedStatement ps = conn.prepareStatement(
         "INSERT INTO orders (user_id, total) VALUES (?, ?)")) {
    for (Order o : orders) {
        ps.setLong(1, o.userId());
        ps.setBigDecimal(2, o.total());
        ps.addBatch();
    }
    ps.executeBatch();
}
```

### SQL — Window Function for Pagination Without Offset

```sql
-- Bad: OFFSET 100000 skips 100K rows each time
SELECT * FROM orders ORDER BY id LIMIT 20 OFFSET 100000;

-- Good: seek pagination (constant cost regardless of depth)
SELECT * FROM orders
WHERE id > :last_seen_id
ORDER BY id
LIMIT 20;
```

### SQL — Retry on Serialization Failure

```java
int maxRetries = 3;
for (int attempt = 1; attempt <= maxRetries; attempt++) {
    try (Connection conn = ds.getConnection()) {
        conn.setAutoCommit(false);
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        try {
            // ... do work ...
            conn.commit();
            return;
        } catch (SQLException e) {
            conn.rollback();
            if ("40001".equals(e.getSQLState()) && attempt < maxRetries) {
                Thread.sleep(10L * (1L << attempt));  // exponential backoff
            } else {
                throw e;
            }
        }
    } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        throw new SQLException("Interrupted", ie);
    }
}
```

---

## Quick Revision

- MVCC: every row has `xmin` (creator) and `xmax` (deleter); readers see a snapshot
- WAL: every change logged sequentially before data pages; enables recovery + replication
- Checkpoint: flush dirty pages; bounds WAL replay on crash
- Pages: 8 KB; TOAST for large values
- VACUUM reclaims dead tuples from MVCC; `VACUUM FULL` shrinks files (locks table)
- XID wraparound emergency: autovacuum must run or DB refuses writes
- Indexes: B-tree (default), Hash, GIN (JSONB/arrays), GiST (spatial), BRIN (time-series)
- Covering index (`INCLUDE`) avoids heap fetch
- Isolation: Read Committed → Repeatable Read → Serializable SSI
- Row-level locks: `FOR UPDATE`, `FOR SHARE`, `FOR NO KEY UPDATE`
- Connection pooling: PgBouncer; ~10 MB per connection
- `EXPLAIN ANALYZE` for query diagnosis

---

## See Also

- [04-advanced-topics/internals/index-structures.md](index-structures.md) — B-tree, GIN, GiST, BRIN internals
- [04-advanced-topics/internals/mysql-internals.md](mysql-internals.md) — compare MVCC, WAL implementations
- [01-foundations/databases.md](../../01-foundations/databases.md) — SQL fundamentals
- [04-advanced-topics/distributed-concepts.md](../distributed-concepts.md) — idempotency, transactions
- [04-advanced-topics/observability.md](../observability.md) — query monitoring

---

## Interview Questions Asked

**Q: Explain MVCC in PostgreSQL.**

A: Every row has two system columns: `xmin` (transaction that created the row) and `xmax` (transaction that deleted or updated it). Each transaction takes a snapshot at start time. On read, PostgreSQL checks visibility: the row is visible if its xmin is committed and predates the snapshot AND its xmax is either NULL, uncommitted, or from a transaction not yet in the snapshot. Readers never block writers; writers never block readers. The cost: every UPDATE creates a new row version, leaving the old one as a "dead tuple" until VACUUM reclaims it.

**Q: What is WAL and why is it needed?**

A: Write-Ahead Log. Every change is written to the WAL sequentially **before** the data page is modified. Sequential disk I/O is fast. The client is acknowledged after the WAL is flushed. The data pages are written later, potentially in random order, during checkpoints. WAL is used for: (1) crash recovery — on restart, replay WAL from last checkpoint; (2) streaming replication — ship WAL to standbys; (3) point-in-time recovery — replay WAL to any timestamp.

**Q: Index Scan vs Sequential Scan — when does each happen?**

A: The cost-based optimizer estimates both options. **Index Scan** is chosen when a small fraction of rows matches (~5-10%): random page reads are expensive but few of them. **Sequential Scan** is chosen when many rows match (~30%+): reading the whole table sequentially is cheaper than hundreds of thousands of random page reads. The break-even point depends on `random_page_cost` (4.0 default) vs `seq_page_cost` (1.0) and the table's physical clustering.

**Q: What happens when VACUUM doesn't run?**

A: Three failures, in order. (1) Dead tuples accumulate → table bloat → slower sequential scans and index scans → higher I/O. (2) Indexes bloat (dead entries aren't reclaimed). (3) **XID wraparound**: PostgreSQL's 32-bit transaction IDs run out. Rows with old XIDs become invisible to new transactions. The database eventually goes into emergency mode, refuses writes, and demands manual `VACUUM FREEZE`. This is one of the most catastrophic PostgreSQL failures. Never disable autovacuum.

**Q: How does PostgreSQL handle high write load?**

A: Five levers. (1) **WAL batching**: multiple transactions share a single WAL flush. (2) **Asynchronous commit** (`synchronous_commit=off`): acknowledge before WAL flush; up to ~0.5 s of data loss on crash, much lower latency. (3) **Connection pooling with PgBouncer** to handle many clients with few backend processes. (4) **Table partitioning** distributes I/O across multiple physical files. (5) **Tune memory**: `shared_buffers`, `work_mem`, `wal_buffers`. For horizontal write scale, partition across multiple PostgreSQL instances (Citus, native partitioning) or move to a different system.
