# PostgreSQL Internals

## Overview
PostgreSQL is an advanced open-source relational database known for ACID compliance, complex queries, and extensibility. Understanding its internals is crucial for SDE-3 interviews.

---

## MVCC (Multi-Version Concurrency Control)

### Core Concept
Instead of locking rows, PostgreSQL creates **multiple versions** of each row to allow concurrent reads and writes.

### How It Works

#### Transaction IDs (XID)
- Each transaction gets a unique, monotonically increasing ID
- Stored as `xmin` (creating transaction) and `xmax` (deleting transaction) in each tuple

#### Tuple Visibility
```
Transaction 100: INSERT INTO users VALUES (1, 'Alice');
  → Row: (id=1, name='Alice', xmin=100, xmax=NULL)

Transaction 101: UPDATE users SET name='Bob' WHERE id=1;
  → Old Row: (id=1, name='Alice', xmin=100, xmax=101)
  → New Row: (id=1, name='Bob', xmin=101, xmax=NULL)

Transaction 102 (started before 101): Sees 'Alice'
Transaction 103 (started after 101): Sees 'Bob'
```

### Visibility Rules
A tuple is visible to transaction T if:
1. `xmin` is committed and `xmin < T.snapshot`
2. `xmax` is NULL OR (`xmax` not committed OR `xmax > T.snapshot`)

---

## Write-Ahead Logging (WAL)

### Purpose
- **Durability**: Ensure transactions survive crashes
- **Replication**: Ship WAL to replicas
- **Point-in-Time Recovery

**: Replay WAL to restore to specific time

### How It Works

#### 1. WAL Buffers
```
Transaction commits
    ↓
Write to WAL buffer (memory)
    ↓
Flush WAL to disk (sequential write)
    ↓
Acknowledge commit to client
    ↓ (later)
Write actual data pages to disk
```

**Key Insight:** WAL is written **before** data pages (Write-Ahead)

#### 2. WAL Segments
- Fixed size files (default 16MB)
- Named like: `000000010000000000000001`
- Recycled when no longer needed

#### 3. Checkpoints
- Periodic flush of dirty pages to disk
- Reduces recovery time (don't replay entire WAL)
- Controlled by `checkpoint_timeout` (default 5 min)

### Crash Recovery
1. Find last checkpoint
2. Replay WAL from checkpoint onward
3. Redo committed transactions, undo uncommitted ones

---

## Storage: Pages & TOAST

### Page Structure
- PostgreSQL stores data in **8KB pages** (default)
- Each page has:
  - Header (24 bytes)
  - Item pointers (4 bytes each)
  - Free space
  - Tuples (actual data)
  - Special space (for indexes)

```
+------------------+
| Page Header      |
+------------------+
| Item Pointers    |  ← point to tuples
+------------------+
| Free Space       |
+------------------+
| Tuples           |  ← actual rows
+------------------+
| Special Space    |
+------------------+
```

### TOAST (The Oversized Attribute Storage Technique)
- Handles values > ~2KB (like large text, JSON)
- **Compression**: Try compressing first
- **Out-of-line storage**: If still too large, store in separate TOAST table
- **Chunking**: Split into 2KB chunks

**Strategies:**
- `PLAIN`: No compression/out-of-line
- `EXTENDED`: Compress, then out-of-line if needed (default)
- `EXTERNAL`: Out-of-line, no compression
- `MAIN`: Compress, avoid out-of-line

---

## Indexes

### B-Tree (Default)
- Balanced tree structure
- Supports `<`, `<=`, `=`, `>=`, `>`, `BETWEEN`, `IN`
- Height typically 3-4 levels for millions of rows

```
Root Page
  ├─ Internal Page 1
  │   ├─ Leaf (rows 1-100)
  │   └─ Leaf (rows 101-200)
  └─ Internal Page 2
      ├─ Leaf (rows 201-300)
      └─ Leaf (rows 301-400)
```

### GiST (Generalized Search Tree)
- For spatial, full-text, geometric data
- Used by PostGIS

### GIN (Generalized Inverted Index)
- For array, JSONB, full-text search
- Inverted index: maps values → rows

**Example:**
```sql
CREATE INDEX idx_tags ON articles USING GIN(tags);

-- tags column has arrays: ['postgres', 'database']
-- GIN index:
'postgres' → [row1, row3, row5]
'database' → [row1, row2, row4]
```

### BRIN (Block Range Index)
- For very large tables with natural ordering
- Stores min/max per block range
- **Tiny size** compared to B-tree
- Good for time-series data

---

## Query Execution

### Planner/Optimizer

#### 1. Parse SQL
```
SELECT * FROM users WHERE age > 25
  ↓
Parse Tree (AST)
```

#### 2. Rewrite (Apply Rules)
- View expansion
- Rule system

#### 3. Plan (Choose Execution Strategy)
- Seq Scan vs Index Scan
- Join algorithms (Nested Loop, Hash Join, Merge Join)
- Cost-based optimization

#### 4. Execute

### Cost Estimation
PostgreSQL estimates cost using:
- `seq_page_cost = 1.0` (sequential page read cost)
- `random_page_cost = 4.0` (random page read cost)
- `cpu_tuple_cost = 0.01` (cost to process one row)
- Table statistics from `ANALYZE`

**Example:**
```
Seq Scan: cost = (pages × seq_page_cost) + (rows × cpu_tuple_cost)
Index Scan: cost = (random reads × random_page_cost) + ...
```

### Statistics
- `pg_stats`: Histogram, most common values, null fraction
- `ANALYZE`: Updates statistics
- Autovacuum runs ANALYZE automatically

---

## Vacuum & Autovacuum

### Problem: Dead Tuples
- MVCC creates old row versions
- Deleted/updated rows not removed immediately
- **Bloat** reduces performance

### Vacuum
Reclaims space from dead tuples

#### 1. VACUUM (Standard)
- Marks dead tuples as reusable
- **Does not shrink** table file

#### 2. VACUUM FULL
- Rewrites entire table
- **Locks table** (slow, disruptive)
- Reclaims disk space

### Autovacuum
- Background process
- Triggers when:
  ```
  dead tuples > (threshold + scale_factor × live tuples)
  default: 50 + 0.2 × live tuples
  ```

### Tuning Autovacuum
```sql
-- Per table settings
ALTER TABLE users SET (autovacuum_vacuum_scale_factor = 0.05);

-- More aggressive for high-write tables
ALTER TABLE logs SET (autovacuum_vacuum_cost_delay = 10);
```

---

## Replication

### Physical Replication (Streaming)
- **WAL shipping**: Primary sends WAL to standby
- Standby replays WAL
- **Byte-for-byte copy**: Entire database replicated

```
Primary (read/write)
    ↓ (WAL stream)
Standby 1 (read-only)
Standby 2 (read-only)
```

**Types:**
- **Synchronous**: Wait for standby to acknowledge
- **Asynchronous**: Don't wait (faster, but potential data loss)

### Logical Replication
- Replicates specific **tables** (not entire DB)
- Different PostgreSQL versions supported
- Enables selective replication

**Use Cases:**
- Upgrading PostgreSQL versions
- Cross-region partial replication
- Data warehousing (replicate subset)

---

## Transaction Isolation Levels

| Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|-------|------------|---------------------|--------------|
| **Read Uncommitted** | ✅ (Postgres treats as RC) | ✅ | ✅ |
| **Read Committed** | ❌ | ✅ | ✅ |
| **Repeatable Read** | ❌ | ❌ | ❌ (Postgres) |
| **Serializable** | ❌ | ❌ | ❌ |

### Read Committed (Default)
- Sees only committed data
- Snapshot taken **per query**
- Non-repeatable reads possible

### Repeatable Read
- Snapshot taken **per transaction**
- Sees consistent view throughout transaction
- **No phantom reads** in PostgreSQL (unlike SQL standard)

### Serializable
- True serializability
- May cause serialization failures (need retry logic)
- Uses **Serializable Snapshot Isolation (SSI)**

---

## Locks

### Row-Level Locks
- `FOR UPDATE`: Exclusive lock
- `FOR SHARE`: Shared lock

### Table-Level Locks
- `ACCESS SHARE`: Acquired by `SELECT`
- `ROW EXCLUSIVE`: Acquired by `INSERT`, `UPDATE`, `DELETE`
- `EXCLUSIVE`: Blocks all concurrent access

### Deadlock Detection
- Checks every `deadlock_timeout` (default 1s)
- Aborts one transaction to break cycle

---

## Performance Tuning

### Key Parameters

#### Memory
```
shared_buffers = 25% of RAM  (data cache)
work_mem = 4MB per sort/hash operation
maintenance_work_mem = 1GB (for VACUUM, index creation)
effective_cache_size = 75% of RAM (planner hint)
```

#### Checkpoints
```
checkpoint_timeout = 15min
max_wal_size = 2GB
```

#### Connections
```
max_connections = 100 (set conservatively)
```

### Query Optimization

#### Use EXPLAIN ANALYZE
```sql
EXPLAIN ANALYZE SELECT * FROM users WHERE age > 25;

-- Look for:
-- Sequential Scan → consider index
-- High cost → optimize query
```

#### Index Best Practices
- Index columns in `WHERE`, `JOIN`, `ORDER BY`
- Multi-column indexes: most selective column first
- Covering indexes (INCLUDE clause) for index-only scans

---

## Common Pitfalls

### ❌ Not Running VACUUM
- Dead tuples accumulate
- Indexes bloat
- Run `autovacuum` or manual `VACUUM`

### ❌ Missing Indexes
- Sequential scans on large tables
- Use `EXPLAIN` to identify

### ❌ N+1 Query Problem
- Use JOINs or batch queries
- Consider `WITH` (CTEs) or subqueries

### ❌ Connection Pooling
- PostgreSQL expensive per connection (~10MB)
- Use **PgBouncer** or **application-level pooling**

---

## Interview Questions

**Q: Explain MVCC in PostgreSQL**
- Creates multiple versions of rows
- Each transaction sees snapshot based on transaction ID
- No read locks, high concurrency
- Trade-off: Dead tuples need cleanup (VACUUM)

**Q: What is WAL and why is it needed?**
- Write-Ahead Log ensures durability
- Written before data pages (sequential I/O)
- Used for crash recovery and replication
- Checkpoint mechanism reduces recovery time

**Q: Index Scan vs Sequential Scan - when does each happen?**
- Planner chooses based on cost estimation
- Index Scan: When fetching small % of rows (~5-10%)
- Seq Scan: When fetching large % of rows (cheaper to read whole table)
- Affected by `random_page_cost` vs `seq_page_cost`

**Q: How does PostgreSQL handle high write load?**
- WAL batching reduces I/O
- Asynchronous commit for lower latency (trade-off: potential data loss)
- Connection pooling to handle more clients
- Partitioning to distribute I/O
- Tune `shared_buffers`, `checkpoint_timeout`, `max_wal_size`
