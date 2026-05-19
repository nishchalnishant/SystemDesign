# MySQL / InnoDB Internals

> InnoDB stores data in a B+ tree clustered index, uses a buffer pool for I/O reduction, MVCC for non-blocking reads, and WAL (redo log) for crash recovery — understanding these is the foundation of database performance tuning.

---

## File Mindmap

```
MySQL / InnoDB Internals
├── Why It Exists
│   ├── Problem → Naive disk I/O for every query is too slow
│   └── Forces → Disk seek = 10 ms; RAM access = 100 ns; 5 orders of magnitude difference
├── Core Concepts
│   ├── B+ tree → all data in leaves; O(log N) lookup; great for range scans
│   ├── Clustered index → data rows ARE the B+ tree leaf pages; ordered by PK
│   ├── Buffer pool → LRU cache of 16 KB pages; target 80-90% of available RAM
│   ├── MVCC → readers don't block writers; undo log stores old versions
│   └── WAL/redo log → write-ahead log; crash recovery without full flush
├── Strategies / Types
│   ├── Clustered index scan → sequential I/O → fast for range
│   └── Secondary index + index lookup → 2 B+ trees; covering index avoids 2nd lookup
├── Trade-offs
│   ├── Pro: B+ trees support both point lookup and range scan
│   └── Con: Write amplification; index maintenance on insert/update
├── Failure Modes
│   └── Deadlock → InnoDB detects cycle; rolls back youngest transaction
└── Interview Angles
    └── EXPLAIN output → what each row means; index selection
```

---

## 1. Why MySQL / InnoDB Internals Matters

**Question**: Your query `SELECT * FROM orders WHERE customer_id = 12345` takes 2 seconds on a table with 50 million rows. How do you diagnose and fix it?

**Physical constraint**: A spinning disk does 100-200 random I/Os per second (seek time ~10 ms). A 50M-row table at 200 bytes/row = 10 GB. Scanning the full table requires reading 10 GB / 16 KB per page = 625,000 pages. At 100 I/Os/s that is 6,250 seconds. Even on SSD (10,000 random I/Os/s) it's 62 seconds. Random I/O at scale is unusable without indexing.

**Minimal solution**: Add an index on `customer_id`. The B+ tree index narrows the search from 625,000 pages to `log₂(625,000) ≈ 20` page reads to find the leaf level, plus a small number of pages for the matching rows.

**Production generalization**: Understanding why the index works — and when the optimizer won't use it — requires knowing the B+ tree structure, how the clustered index relates to secondary indexes, how the buffer pool caches pages, and how MVCC affects what version of data you see.

---

## 2. Core Concepts / How It Works

### B+ Tree Structure

```
                    [Root]
                   /      \
            [Internal]    [Internal]
           /         \        \
        [Leaf]    [Leaf] ← → [Leaf] ← → [Leaf]
        (data)    (data)     (data)     (data)

Leaf nodes are doubly linked → efficient range scan (no backtracking to root)
Internal nodes store separator keys only (no data) → more keys per page → shallower tree
```

Properties:
- Tree height for 50M rows at 16 KB page, ~100 keys/internal page: `log₁₀₀(50,000,000) ≈ 3.4` → height 4
- 4 page reads to find any row (root usually cached in buffer pool → effectively 2-3 physical reads)
- Range scan walks leaf linked list sequentially → I/O is sequential, much faster than random

### Clustered Index

InnoDB stores table data in a B+ tree ordered by the primary key. The leaf pages contain the actual row data.

```
PRIMARY KEY (order_id)

Clustered B+ tree:
  Leaf page 1: orders 1-100      (rows stored here, ordered by order_id)
  Leaf page 2: orders 101-200
  ...
```

**Implication**: Rows are physically ordered by PK. A range scan on PK is sequential I/O. Inserting rows with non-monotonic PKs (e.g., UUID) causes random page writes — "page fragmentation" → slower inserts and reduced buffer pool efficiency.

**UUID vs AUTO_INCREMENT**:
| Key Type     | Insert pattern | Fragmentation | Index size |
|--------------|----------------|---------------|------------|
| AUTO_INCREMENT| Sequential     | Minimal        | Small      |
| Random UUID  | Random         | High           | Larger (16 vs 4 bytes, plus secondary index bloat) |
| UUIDv7 (time-ordered) | Sequential | Minimal  | Larger, but no fragmentation |

### Secondary Index

A secondary index is a separate B+ tree. Leaf nodes contain the indexed column value + the primary key value (not the full row).

```
INDEX (customer_id)

Secondary B+ tree leaf:
  (customer_id=100, order_id=500)
  (customer_id=101, order_id=12)
  (customer_id=101, order_id=999)

To get full row: look up order_id in clustered index → second B+ tree traversal ("index lookup")
```

This two-step process is called a **bookmark lookup** or **back-to-table lookup**. It means each secondary index lookup is actually 2 × O(log N) operations.

**Covering Index**: If the query only needs columns present in the index, InnoDB doesn't need to go back to the clustered index.

```sql
-- Query: SELECT order_id, status FROM orders WHERE customer_id = 101
-- Covering index: INDEX (customer_id, order_id, status)
-- InnoDB reads only the secondary B+ tree — no back-to-table lookup
```

### Buffer Pool

The buffer pool is InnoDB's in-memory page cache. Default size: 128 MB. Production: 70-80% of available RAM.

```
┌──────────────────── Buffer Pool ────────────────────┐
│  New Sublist (37%)         Old Sublist (63%)         │
│  [hot pages]      ←midpoint→  [cool pages → evict]  │
└─────────────────────────────────────────────────────┘
```

InnoDB uses a modified LRU: newly read pages enter the "old" sublist midpoint. If accessed again within 1 second (e.g., full table scan touching every page once), they don't promote to "new" sublist. This prevents large table scans from evicting hot working-set pages.

**Key metrics**:
- Buffer pool hit rate: target > 99%. Below 95% → I/O bound, add RAM.
- `innodb_buffer_pool_size`: 70-80% of available RAM.
- Multiple buffer pool instances (`innodb_buffer_pool_instances`): 1 per 1 GB to reduce mutex contention.

### WAL / Redo Log

Write-Ahead Logging (WAL) enables crash recovery and makes writes fast.

```
Write flow:
  1. Write change to redo log (sequential write, ~1 ms)  ← FAST
  2. Mark buffer pool page as dirty
  3. Acknowledge write to client                         ← Client sees commit
  [async] Checkpoint: flush dirty pages to data files    ← happens later
```

The redo log is a circular ring buffer on disk (`ib_logfile0`, `ib_logfile1`). If MySQL crashes after step 1 but before the async flush (step 3), on restart InnoDB replays the redo log to recover committed changes.

**redo log vs binlog**:
| Log type  | Purpose                              | Format         | Who reads it         |
|-----------|--------------------------------------|----------------|----------------------|
| Redo log  | Crash recovery (InnoDB internal)     | Physical (page changes) | InnoDB only    |
| Binlog    | Replication; point-in-time recovery  | Logical (SQL or row)  | Replicas, mysqlbinlog |

### MVCC (Multi-Version Concurrency Control)

MVCC allows reads to proceed without blocking writes and vice versa.

```
Transaction T1 (starts at timestamp 100) reads row R
Transaction T2 (starts at timestamp 105) updates row R, commits

T1 reads the version of R from before timestamp 105 (from undo log)
T1 never blocks waiting for T2
T2 never blocks waiting for T1
```

Each row has hidden columns: `DB_TRX_ID` (last modifying transaction ID) and `DB_ROLL_PTR` (pointer to previous version in undo log).

**Read views**: At the start of a repeatable-read transaction, InnoDB takes a snapshot of active transaction IDs. Any row version created by a transaction in that snapshot (or after) is invisible to this transaction.

**Undo log growth**: Long-running transactions prevent undo log cleanup → undo log grows → performance degrades. Avoid transactions that hold open for minutes.

### Row Locking and Deadlocks

InnoDB locks at the row level. Lock types:

| Lock type    | Compatibility | Use case                           |
|--------------|---------------|------------------------------------|
| Shared (S)   | S+S ok, S+X no| `SELECT ... FOR SHARE`             |
| Exclusive (X)| X blocks all  | `SELECT ... FOR UPDATE`, `UPDATE`, `DELETE` |
| Intention (IS/IX) | Intent at table level | Allows table-level structure changes to detect row locks |
| Gap lock     | Prevents phantom reads | Lock on a range, not a specific row |
| Next-key lock| Row + gap above| Default in REPEATABLE READ; prevents phantoms |

**Deadlock detection**: InnoDB builds a wait-for graph. On detecting a cycle, it rolls back the transaction with the least undo log (youngest transaction, by default). The rolled-back transaction receives `ERROR 1213: Deadlock found`.

**Deadlock avoidance**:
1. Always access tables/rows in the same order across transactions
2. Keep transactions short
3. Use `SELECT ... FOR UPDATE` only when you will actually update

---

## 3. Real-World Usage

| Scenario                          | Relevant internal                               |
|-----------------------------------|-------------------------------------------------|
| Slow `ORDER BY` on un-indexed col | Full filesort; add index on ORDER BY column     |
| High insert rate with UUID PK     | B+ tree page splits; switch to AUTO_INCREMENT or UUIDv7 |
| Read-heavy with low hit rate      | Buffer pool too small; increase `innodb_buffer_pool_size` |
| Replica lag                       | Binlog-based replication; switch to row-based for faster replay |
| `SELECT COUNT(*)` is slow         | Full index scan; maintain separate counter table |
| MVCC undo log bloat               | Long-running transaction; commit/rollback sooner |

**GitHub's MySQL usage**: GitHub runs MySQL at scale using Vitess (horizontal sharding). They keep InnoDB buffer pool at 75% of RAM per shard host. They use AUTO_INCREMENT PKs on all tables specifically to avoid B+ tree fragmentation.

---

## 4. Trade-offs

| Dimension        | Pro                                                | Con                                                      |
|------------------|----------------------------------------------------|----------------------------------------------------------|
| B+ tree reads    | O(log N) for point lookup; O(K) for range scan     | Write amplification: each insert may split pages         |
| Clustered index  | Range scans on PK are sequential → fast            | Secondary indexes store PK → larger secondary index size |
| Buffer pool      | Eliminates I/O for hot pages; 99%+ hit rate        | Cold start: empty buffer pool → high I/O until warm      |
| MVCC             | Reads don't block writes; writers don't block reads| Undo log growth with long transactions                   |
| Row locking      | Fine-grained concurrency                           | Deadlocks possible; gap locks can cause unexpected blocks|
| WAL              | Fast writes (sequential log write vs random flush) | Redo log replay on restart adds recovery time            |

---

## 5. Failure Scenarios

| Scenario                       | Symptom                                             | Mitigation                                             |
|--------------------------------|-----------------------------------------------------|--------------------------------------------------------|
| Buffer pool thrashing          | I/O wait > 50%; low hit rate                        | Increase `innodb_buffer_pool_size`; add RAM            |
| B+ tree fragmentation          | INSERT slows over time; index size grows            | Use AUTO_INCREMENT PK; run `OPTIMIZE TABLE` (offline)  |
| Long-running transactions      | Undo log fills; replicas lag; `SHOW ENGINE INNODB STATUS` shows long trx | Set `wait_timeout`; use explicit COMMIT  |
| Deadlock spike                 | Application gets 1213 errors                        | Retry with backoff; fix transaction ordering           |
| Replication lag                | Replica falls behind primary                        | Parallel replication (`slave_parallel_workers`); add replicas |
| Disk full (data file)          | InnoDB stops; queries fail                          | Monitor disk usage; use `innodb_file_per_table` for easier management |
| Redo log too small             | High write workload causes checkpoint stall         | Increase `innodb_log_file_size` (requires restart)     |

---

## 6. Performance Considerations

**Page read cost**: SSD NVMe: ~0.1 ms/random read. HDD: ~10 ms. Buffer pool hit: ~100 ns. A query needing 4 page reads with 99% hit rate: 0.04 page reads from disk on average = ~4 µs of disk I/O.

**Index selectivity**: An index is useful when it reduces the row set significantly. MySQL's optimizer uses index statistics. Low-selectivity index (e.g., boolean column) is often ignored by the optimizer — full scan is cheaper than many bookmark lookups.

**Join performance**: MySQL supports hash join (8.0+) and nested-loop join. For nested-loop: outer table row count × inner index lookup cost. Rule: put the smaller table on the outer loop; ensure inner table has an index on the join column.

**Sort performance**: `ORDER BY` without an index triggers a filesort (in-memory sort buffer or disk temp file). `innodb_sort_buffer_size` controls the in-memory sort buffer. For large result sets, always add an index on the ORDER BY column if it's queried frequently.

**`innodb_flush_log_at_trx_commit`**:
| Value | Behavior                                            | Durability | Performance |
|-------|-----------------------------------------------------|------------|-------------|
| 0     | Flush redo log every second (not per commit)        | Risk 1 s of data loss on crash | Fastest |
| 1     | Flush and sync on every commit (default)            | Fully durable | Slowest |
| 2     | Write to OS buffer on commit; sync every second     | Risk 1 s on OS crash | Middle |

Production: use `1` for financial data, `2` for analytics workloads that can tolerate 1 s data loss.

---

## 7. Implementation Patterns

### Reading EXPLAIN Output

```sql
EXPLAIN SELECT o.order_id, o.total, c.name
FROM orders o
JOIN customers c ON o.customer_id = c.id
WHERE o.status = 'PENDING'
  AND o.created_at > '2024-01-01'
ORDER BY o.created_at DESC
LIMIT 100;
```

```
+----+-------------+-------+--------+-----------------------------+------------------+---------+------------------+------+-----------------------------+
| id | select_type | table | type   | possible_keys               | key              | key_len | ref              | rows | Extra                       |
+----+-------------+-------+--------+-----------------------------+------------------+---------+------------------+------+-----------------------------+
|  1 | SIMPLE      | o     | range  | idx_status_date,idx_date    | idx_status_date  | 10      | NULL             | 5000 | Using index condition; Using filesort |
|  1 | SIMPLE      | c     | eq_ref | PRIMARY                     | PRIMARY          | 4       | o.customer_id    |    1 | NULL                        |
+----+-------------+-------+--------+-----------------------------+------------------+---------+------------------+------+-----------------------------+
```

**Key columns**:
| Column        | What it means                                                          |
|---------------|------------------------------------------------------------------------|
| `type`        | Access method: `const` > `eq_ref` > `ref` > `range` > `index` > `ALL`|
| `key`         | Index actually chosen                                                   |
| `rows`        | Estimated rows examined — multiply across joined tables for total cost  |
| `Extra`       | `Using filesort` = no index for ORDER BY; `Using temporary` = temp table|
| `key_len`     | Bytes of index used; shorter than index definition = partial index use  |

**Problems in this EXPLAIN**: `Using filesort` on orders — the ORDER BY can't use the index because status is a prefix and created_at is scanned then sorted. Fix:

```sql
-- Composite index that supports both WHERE and ORDER BY
CREATE INDEX idx_status_created ON orders(status, created_at DESC);
-- Now: type=range, Using index condition, no filesort
```

### Index Design Best Practices

```sql
-- Rule: Equality conditions first, then range, then ORDER BY columns
-- WHERE status = 'PENDING' AND created_at > X ORDER BY created_at
CREATE INDEX idx_status_created ON orders(status, created_at);

-- Covering index: include SELECT columns to avoid back-to-table lookup
CREATE INDEX idx_status_created_covering
ON orders(status, created_at, order_id, total);

-- Prefix index for long strings (saves space, reduces I/O)
CREATE INDEX idx_email_prefix ON users(email(20));
-- Trade-off: can't use for exact equality on full email

-- Partial index (MySQL 8.0+): only index rows matching condition
CREATE INDEX idx_pending_orders ON orders(customer_id)
WHERE status = 'PENDING';  -- smaller index; only useful for PENDING queries
```

### Java — Detecting and Handling Deadlocks

```java
import java.sql.*;

public class OrderService {

    private static final int MAX_DEADLOCK_RETRIES = 3;
    private static final int DEADLOCK_ERROR_CODE = 1213;

    public void transferFunds(long fromAccountId, long toAccountId, BigDecimal amount)
            throws SQLException {
        int attempts = 0;
        while (attempts < MAX_DEADLOCK_RETRIES) {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Always lock in consistent order to prevent deadlocks
                    long firstId = Math.min(fromAccountId, toAccountId);
                    long secondId = Math.max(fromAccountId, toAccountId);

                    lockAccount(conn, firstId);
                    lockAccount(conn, secondId);

                    debit(conn, fromAccountId, amount);
                    credit(conn, toAccountId, amount);

                    conn.commit();
                    return;
                } catch (SQLException e) {
                    conn.rollback();
                    if (e.getErrorCode() == DEADLOCK_ERROR_CODE && attempts < MAX_DEADLOCK_RETRIES - 1) {
                        attempts++;
                        Thread.sleep(10 * (long) Math.pow(2, attempts)); // exponential backoff
                    } else {
                        throw e;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new SQLException("Interrupted during deadlock retry", ie);
            }
        }
    }

    private void lockAccount(Connection conn, long accountId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, accountId);
            ps.executeQuery();
        }
    }
}
```

### Monitoring Query Performance

```sql
-- Enable slow query log
SET GLOBAL slow_query_log = ON;
SET GLOBAL long_query_time = 0.1;  -- log queries > 100 ms
SET GLOBAL log_queries_not_using_indexes = ON;

-- Find top slow queries (requires performance_schema)
SELECT digest_text, count_star, avg_timer_wait/1e9 AS avg_sec,
       sum_timer_wait/1e9 AS total_sec
FROM performance_schema.events_statements_summary_by_digest
ORDER BY sum_timer_wait DESC
LIMIT 10;

-- Buffer pool hit rate
SELECT (1 - (Innodb_buffer_pool_reads / Innodb_buffer_pool_read_requests)) * 100
    AS hit_rate_pct
FROM information_schema.global_status
WHERE Variable_name IN ('Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests');

-- Check for long-running transactions
SELECT trx_id, trx_started, trx_query, trx_rows_locked
FROM information_schema.innodb_trx
WHERE trx_started < NOW() - INTERVAL 30 SECOND;
```

---

## Quick Revision

- InnoDB stores data in a B+ tree clustered by PK; leaf pages contain actual rows
- Secondary indexes store indexed columns + PK; accessing non-indexed columns requires a second B+ tree lookup (bookmark lookup)
- Covering index: include all SELECT columns in index; avoids back-to-table lookup
- Buffer pool: target 70-80% of RAM; hit rate > 99%; uses modified LRU to protect from scan pollution
- WAL (redo log): changes written sequentially first; dirty pages flushed asynchronously; crash recovery replays redo log
- MVCC: each row has version history in undo log; readers see a consistent snapshot without blocking writers
- Row-level locking: S/X/gap/next-key locks; deadlock detection rolls back youngest transaction
- EXPLAIN: look at `type` (avoid ALL), `rows` (minimize), `Extra` (avoid `filesort`, `temporary`)
- Index design rule: equality columns first, range column last, include ORDER BY column if possible
- UUID PKs cause B+ tree fragmentation; prefer AUTO_INCREMENT or time-ordered UUIDv7

---

## See Also

- [04-advanced-topics/internals/postgresql-internals.md](postgresql-internals.md) — compare MVCC, WAL implementations
- [02-building-blocks/caching-layer.md](../../02-building-blocks/caching-layer.md) — Redis as query cache
- [02-building-blocks/replication.md](../../02-building-blocks/replication.md) — MySQL binlog replication
- [04-advanced-topics/distributed-systems.md](../distributed-systems.md) — ACID vs BASE
- [01-foundations/databases.md](../../01-foundations/databases.md) — database fundamentals

---

## Interview Questions Asked

### Conceptual

**Q1: How does InnoDB's MVCC allow reads and writes to proceed concurrently without locking?**

A: Each row has two hidden fields: `DB_TRX_ID` (the transaction ID of the last write) and `DB_ROLL_PTR` (pointer to the previous version in the undo log). When a transaction reads a row, InnoDB compares the row's `DB_TRX_ID` against the transaction's read view (a snapshot of which transactions were active at the start of this transaction). If `DB_TRX_ID` belongs to a concurrent or future transaction (invisible), InnoDB follows `DB_ROLL_PTR` to get the older version from the undo log. This provides a consistent point-in-time snapshot. Readers never need to acquire shared locks, so they never block writers. Writers take exclusive row locks only for the specific rows they modify, not the entire table.

**Q2: What is the difference between redo log and binlog in MySQL? Why does MySQL have both?**

A: The redo log is an InnoDB-internal write-ahead log that records physical page changes. Its purpose is crash recovery — if MySQL crashes, the redo log is replayed on restart to restore committed changes that hadn't been flushed to data files. It is circular (fixed size) and automatically managed. The binlog is a server-level logical log that records SQL statements or row changes for all committed transactions. Its purposes are: (1) replication — replicas read the binlog and re-execute changes, (2) point-in-time recovery — restore from backup and replay binlog to a specific timestamp. MySQL has both because they serve different layers: redo log is about storage engine crash safety; binlog is about the broader MySQL server's replication and recovery features. The two-phase commit (`XA` internally) ensures they stay in sync.

**Q3: Why can adding an index sometimes make a query slower?**

A: Several reasons: (1) Low selectivity: if the index returns 30% of rows, the optimizer calculates that each row requires a back-to-table bookmark lookup — it may be cheaper to full-scan the clustered index sequentially than to do hundreds of thousands of random lookups via the secondary index. The optimizer picks the full scan. (2) Stale statistics: after a large data load, `ANALYZE TABLE` hasn't been run, so the optimizer has inaccurate row counts and picks the wrong plan. (3) Covering index not complete: if you add an index on `(customer_id)` but the query selects 10 columns, each row still needs a bookmark lookup — adding more columns to the index would eliminate those. (4) Index on a function: `WHERE YEAR(created_at) = 2024` won't use an index on `created_at`; you need a function-based index or a range rewrite `WHERE created_at BETWEEN '2024-01-01' AND '2024-12-31'`.

### Comparison / Trade-off

**Q: Compare InnoDB and MyISAM. Why is InnoDB the default since MySQL 5.5?**

A: MyISAM uses table-level locking — any write locks the entire table, blocking all reads and writes. InnoDB uses row-level locking. MyISAM has no MVCC, no transactions, no foreign keys, and no crash recovery (relies on table repair). InnoDB has all of these. MyISAM's only advantages were slightly faster reads for single-table queries on read-only workloads (no MVCC overhead), and smaller storage for certain index-heavy tables. In practice, InnoDB's row locking and crash safety outweigh MyISAM's minimal read advantages for any production workload with concurrent writes. MySQL 5.5 made InnoDB the default because MyISAM's table-level locking made it unusable for most web applications.

### Scenario / Design

**Q: A query is taking 5 seconds. EXPLAIN shows `type=ALL`, `rows=50000000`. Walk through your optimization process.**

A: Step 1: Confirm with `EXPLAIN` that no index is being used and identify the WHERE/JOIN/ORDER BY conditions. Step 2: Check if a suitable index exists: `SHOW INDEX FROM table_name`. If no index on the WHERE column, create one. Step 3: If an index exists but isn't used, check selectivity (`SELECT COUNT(DISTINCT col) / COUNT(*)`). If < 5%, the optimizer may prefer a full scan — consider a composite index that is more selective. Step 4: Run `ANALYZE TABLE` to refresh statistics if the index exists and should be selective but isn't chosen. Step 5: Check if the WHERE clause prevents index use: `WHERE LOWER(email) = ?` can't use an index on `email` — rewrite or add a function-based index. Step 6: If the query needs columns not in the index, add them to create a covering index. Step 7: Check if the buffer pool is cold — run the query twice and compare; if the second run is fast, the issue is buffer pool warmup. Step 8: If the query is inherently slow (e.g., aggregation over 50M rows), consider adding a summary/rollup table or moving to a columnar store (ClickHouse, BigQuery) for analytics queries.
