---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Database Scaling Deep Dive

> **From a single Postgres to a globally distributed data tier: WAL internals, MVCC, connection pooling, index strategies, and sharding at scale.**

---

## File Mindmap

```
Database Scaling Deep Dive
├── Why It Exists
│   ├── Problem → single Postgres node becomes bottleneck at scale: write throughput, connection limits, query latency
│   └── Physical limit → disk I/O ceiling, max ~500 active connections per node (context-switch cost)
├── PostgreSQL WAL Internals
│   ├── Sequential write to WAL first → flush → ack client → async data page write
│   ├── Checkpoint → dirty pages flushed to disk; crash recovery replays WAL since last checkpoint
│   └── Scaling insight: WAL is the replication stream for read replicas
├── MVCC
│   ├── Each row has xmin (created by txn) + xmax (deleted by txn)
│   ├── Readers never block writers → snapshot isolation
│   └── Cost: dead tuples accumulate → VACUUM reclaims space
├── Connection Pooling (PgBouncer)
│   ├── Problem → 10,000 app instances × 1 connection = Postgres OOM
│   ├── PgBouncer modes: session (safe) / transaction (efficient) / statement
│   └── pool_size in pgbouncer.ini → typically 20-100 per DB
├── Index Strategies
│   ├── B-tree → default, O(log N), equality + range queries
│   ├── Partial index → WHERE clause reduces index size (active users only)
│   ├── Expression index → index on lower(email) for case-insensitive lookups
│   ├── GIN → inverted index for arrays, JSONB, full-text search
│   └── Covering index → INCLUDE columns to avoid heap fetch
├── Read Replicas
│   ├── Streaming WAL replication → async by default, sync available
│   ├── Use for: reporting queries, analytics, geographic distribution
│   └── Risk: replication lag → stale reads; route time-sensitive reads to primary
├── Write Sharding
│   ├── Shard key selection → high cardinality, even distribution, no hot spots
│   ├── Hash sharding → ConsistentHashRouter; resharding moves ~1/N data
│   ├── Range sharding → time-series workloads; risk: monotonic key creates hot shard
│   └── Resharding process: double-write → backfill → verify checksums → cut over
├── Time-Series at Scale
│   ├── TimescaleDB → hypertables auto-partition by time, compression, continuous aggregates
│   └── ClickHouse → columnar, MergeTree engine, millions of rows/sec insert
├── Managed Scale-Out
│   ├── Aurora → auto-scaling storage (10GB→128TB), up to 15 read replicas, auto-failover <30s
│   └── Vitess → MySQL sharding middleware; used by YouTube, Slack
├── Trade-offs
│   ├── Pro: read replicas scale read throughput linearly
│   ├── Con: cross-shard queries require scatter-gather or denormalization
│   └── Con: resharding is operationally complex and slow
└── Interview Angles
    ├── "Our DB is slow" → diagnose: reads vs writes; add replicas vs shard vs index
    ├── "How do you shard a user table?" → hash on user_id, virtual nodes for rebalance
    └── Follow-up: hot key problem → salting or directory-based sharding
```

## 1. PostgreSQL Internals That Matter for Scaling

### Write-Ahead Log (WAL)

**Question**: You write a row to Postgres. The kernel buffers the write in RAM. Before it flushes to disk, the machine crashes. Is the row there when you restart? If Postgres said "committed", it must be — but how can it guarantee that if disk writes are buffered? What mechanism makes "committed" mean something durable?

**Physical constraint**: Random writes to disk are slow (~4ms per seek for HDD, ~0.1ms for NVMe). A typical transaction touches multiple pages scattered across the file. Flushing all dirty pages synchronously on every commit would make each commit take tens of milliseconds. At 1,000 writes/sec that's 10 seconds of disk time per second — impossible.

**Minimal solution**: Write sequentially to a log file before touching data pages. Sequential writes are 100× faster than random writes because there's no seek — you just append. The log is the source of truth. If the machine crashes, replay the log on restart. Data pages are just a cache of the log's state.

**Generalize**: The WAL is also the replication mechanism. Standbys stream the WAL from the primary and apply it. Physical replication streams raw WAL bytes (exact replica). Logical replication streams decoded logical changes (works across versions, more flexible). Every scaling capability in Postgres — replication, PITR, crash recovery — derives from the WAL.

```
Every write operation (INSERT/UPDATE/DELETE) in PostgreSQL:
  1. Write change to WAL (sequential disk write — fast: ~1ms)
  2. Modify in-memory buffer pages
  3. Return success to client

Checkpoint (every 5 min or 1GB WAL):
  Flush dirty buffer pages to data files (random writes — slower)

Why WAL first?
  Sequential writes are 100× faster than random writes
  If crash happens between step 1 and 3: replay WAL on restart → no data loss
  If crash happens during checkpoint: WAL contains everything needed to recover

WAL and replication:
  Standby servers stream WAL from primary and apply it
  Physical replication: stream exact WAL bytes (works for identical Postgres versions)
  Logical replication: stream logical changes (works across versions, more flexible)
```

### MVCC (Multi-Version Concurrency Control)

**Question**: Reader and writer hit the same row simultaneously. With a lock, one blocks the other. Your dashboard query holds a read lock while a write is waiting. At 10,000 concurrent users, lock contention becomes the bottleneck — not disk, not CPU. How do you let readers and writers proceed simultaneously without seeing inconsistent data?

**Physical constraint**: A lock is a flag in shared memory. Acquiring it requires a memory fence (a CPU instruction that stops out-of-order execution). At high concurrency, the lock itself becomes a bottleneck — thousands of threads queuing up for the same flag. More threads means more contention, not more throughput.

**Minimal solution**: Keep multiple versions of each row. Readers see the version that was current when their transaction started. Writers create a new version without touching the old one. Readers never block writers; writers never block readers.

**Generalize**: MVCC is Postgres's implementation. Every row has `xmin` (the transaction that created it) and `xmax` (the transaction that deleted it). A reader at transaction ID T sees a row if `xmin <= T` and `(xmax is NULL OR xmax > T)`. The cost: dead tuples accumulate. Autovacuum reclaims them. Without vacuum, tables bloat. Transaction ID wraparound is a critical failure mode — monitor `age(datfrozenxid)` and alert before it reaches 2 billion.

```
Problem without MVCC: Read acquires a lock → blocks writer. Writer blocks readers.
Solution: Store multiple versions of rows (old + new). Readers see consistent snapshots.

Transaction T1 starts at timestamp 100:
  Reads row R → sees version valid at T=100 (the "snapshot")
  Even if T2 updates R at T=101, T1 still sees old version until T1 commits

Implementation:
  Each row has: xmin (transaction that created it), xmax (transaction that deleted it)
  T1 sees row if: xmin <= T1.start_time AND (xmax is NULL OR xmax > T1.start_time)

Vacuum (autovacuum daemon):
  Old versions accumulate (every UPDATE creates a new version + marks old as dead)
  Vacuum reclaims dead tuples (frees space)
  Without vacuum: table bloat, degraded query performance
  Monitor: pg_stat_user_tables.n_dead_tup → alert if > 10% of live tuples

Transaction ID wraparound (critical!):
  PostgreSQL uses 32-bit transaction IDs → wraps after ~2 billion transactions
  If wraparound occurs: all old data looks "in the future" → data loss
  Solution: autovacuum FREEZE prevents wraparound; monitor with:
    SELECT datname, age(datfrozenxid) FROM pg_database ORDER BY age DESC;
    Alert if age > 1.5 billion
```

---

## 2. Connection Pooling

**Question**: You have 500 app server threads, each needing a DB connection. Postgres allocates a backend process (~10MB RAM) per connection. 500 connections = 5GB of RAM just for connection overhead, before a single query runs. At 1,000 threads, Postgres crashes with "too many connections." Why does each connection require a dedicated process, and how do you serve thousands of app threads without thousands of DB connections?

**Physical constraint**: Postgres uses a process-per-connection model (not threads). Each OS process has its own virtual memory space (~10MB minimum), its own stack, its own TLB entries. Context-switching between 1,000 processes is expensive. The OS scheduler, not Postgres, is the bottleneck at high connection counts.

**Minimal solution**: Connection pool at the application level. Each app instance keeps a pool of 10 connections and queues requests. Works until: you have 50 app instances × 10 connections = 500 connections, which is still too many for a large Postgres deployment.

**Generalize**: PgBouncer as a proxy. App threads connect to PgBouncer (cheap — just a socket). PgBouncer holds a small pool of real Postgres connections. In transaction mode, a real connection is borrowed only for the duration of a transaction (typically <10ms), then returned to the pool. 50 real connections can serve 5,000 app threads because most threads are waiting on network or computation, not holding a DB transaction.

```
Each PostgreSQL connection:
  ~10MB of memory (backend process)
  max_connections default: 100

At scale: 1,000 app servers × 10 threads each = 10,000 connections needed
→ Postgres crashes with "too many connections"

Solutions:
  1. PgBouncer (connection pooler)
  2. RDS Proxy (managed, for AWS)
  3. Pgpool-II (more feature-rich, also does replication)
```

### PgBouncer Configuration

```ini
[databases]
mydb = host=primary.rds.amazonaws.com port=5432 dbname=production

[pgbouncer]
pool_mode = transaction          # Return connection to pool after each transaction
max_client_conn = 10000          # How many app connections PgBouncer accepts
default_pool_size = 50           # How many real DB connections per database

# Transaction mode:
#   Each statement/transaction borrows a connection from pool
#   After transaction commits: connection returned to pool
#   One real connection serves many app connections sequentially
#   Best for most OLTP workloads

# Session mode:
#   Connection dedicated to client for entire session
#   Less multiplexing; use when app uses session-level features (SET, temp tables)

# Statement mode:
#   Extreme multiplexing; each statement gets its own connection
#   Cannot use transactions across statements — rarely practical
```

```
Effective multiplexing:
  100 DB connections via PgBouncer → handles 5,000 concurrent app threads
  Why? Most app threads spend 99% of time NOT in a database transaction
  (network I/O, computation, waiting for responses from other services)
```

---

## 3. Index Strategies for Scale

### B-Tree (Default) — When and Why

**Question**: A table has 100M rows. `SELECT * FROM orders WHERE user_id = 12345 ORDER BY created_at DESC LIMIT 20`. Without an index, Postgres scans all 100M rows. With an index on `(user_id)` alone it finds the rows but then sorts them. What index structure makes this query instant regardless of table size?

**Physical constraint**: A B-tree lookup is O(log N) I/Os. For 100M rows that's ~27 levels — but in practice ~3–4 disk reads because upper levels stay hot in buffer cache. A sequential scan is O(N) — proportional to table size. The deeper the tree, the more pages you need in buffer cache to avoid disk I/Os. Column order in a composite index determines which prefix of the index is usable for a given query.

**Minimal solution**: Index on `(user_id)`. Finds rows fast but still requires a sort on `created_at`. Alternatively, index on `(created_at)` alone enables range scans but not the user filter.

**Generalize**: Composite index `(user_id, created_at DESC)`. The index is ordered by `user_id` first, then `created_at` descending within each user. A query filtering `user_id = X ORDER BY created_at DESC` can walk the index in order and return the first 20 rows without a sort. This is an index-ordered scan — zero additional work after the index lookup.

```sql
-- Default index: good for equality and range queries
CREATE INDEX idx_orders_user_created ON orders (user_id, created_at DESC);

-- EXPLAIN ANALYZE reveals if index is used:
EXPLAIN ANALYZE
SELECT * FROM orders WHERE user_id = 12345 ORDER BY created_at DESC LIMIT 20;

-- Ideal: "Index Scan Backward using idx_orders_user_created"
-- Bad: "Seq Scan" — indicates missing index or optimizer chose full scan

-- Composite index column order matters:
-- (user_id, created_at) can serve:
--   WHERE user_id = X
--   WHERE user_id = X AND created_at > Y
-- Cannot efficiently serve:
--   WHERE created_at > Y (user_id not in WHERE clause)
```

### Partial Indexes — For Sparse Conditions

```sql
-- Index only active orders (90% of rows are completed — waste to index them)
CREATE INDEX idx_active_orders ON orders (user_id)
WHERE status = 'ACTIVE';

-- Index only unprocessed jobs
CREATE INDEX idx_pending_jobs ON background_jobs (priority DESC, created_at)
WHERE status = 'PENDING';

-- Result: tiny index (10% of full), very fast for the hot path queries
-- Can only be used if WHERE clause matches index predicate
```

### Expression Indexes

```sql
-- Index on lower(email) for case-insensitive email lookup
CREATE INDEX idx_users_email_lower ON users (lower(email));

-- Now this query uses the index:
SELECT * FROM users WHERE lower(email) = lower('User@Example.com');

-- Index on extracted JSON field
CREATE INDEX idx_metadata_user_type ON events ((metadata->>'user_type'));
SELECT * FROM events WHERE metadata->>'user_type' = 'premium';
```

### GIN Indexes for Full-Text and JSONB

```sql
-- Full-text search index
CREATE INDEX idx_products_search ON products
  USING GIN (to_tsvector('english', title || ' ' || description));

SELECT * FROM products
WHERE to_tsvector('english', title || ' ' || description)
  @@ plainto_tsquery('english', 'wireless headphones');

-- JSONB containment index (find records where JSON contains a value)
CREATE INDEX idx_user_preferences ON users USING GIN (preferences jsonb_path_ops);
SELECT * FROM users WHERE preferences @> '{"theme": "dark"}';
```

### Covering Indexes (Index-Only Scan)

```sql
-- Index includes all columns needed by the query (avoids heap fetch)
CREATE INDEX idx_orders_covering ON orders (user_id, created_at DESC)
  INCLUDE (total_amount, status);

-- This query does "Index Only Scan" — never touches the main table:
SELECT created_at, total_amount, status
FROM orders
WHERE user_id = 12345
ORDER BY created_at DESC
LIMIT 10;
```

---

## 4. Read Scaling Patterns

### Read Replicas

**Question**: Your primary handles 500 write QPS and 5,000 read QPS. Reads are overwhelming the primary — it's at 80% CPU and reads are adding latency to writes. You can't move writes off the primary. How do you serve 5,000 reads/sec without touching the write path?

**Physical constraint**: CPU time is shared between read queries and write processing (WAL, index maintenance, checkpoint). A read-heavy workload that saturates CPU delays write acknowledgment — which directly increases write latency. Reads and writes compete for the same buffer cache, the same CPU, and the same I/O bandwidth.

**Minimal solution**: One read replica. The primary replicates its WAL to the replica; the replica applies it and accepts read queries. The primary is no longer responsible for read CPU. Works until: one replica can't absorb all reads, or replication lag causes stale reads on a time-sensitive path.

**Generalize**: Multiple read replicas with a read load balancer. Route `readOnly=true` transactions to replicas. Monitor replication lag — alert if it exceeds your staleness tolerance. Route critical reads (post-write, auth) to the primary. Auto-scale replicas on read load.

```
Architecture:
  Primary: accepts all writes + critical reads (balance checks, auth)
  Replica 1, 2, ..., N: accepts read-only queries (reports, analytics, feeds)

Replication lag monitoring:
  SELECT now() - pg_last_xact_replay_timestamp() AS replication_lag;
  Alert if lag > 5 seconds (for user-facing reads requiring freshness)

Routing reads to replicas:
  Application: route read queries to replica connection pool
  ProxySQL / pgBouncer for MySQL / Postgres — can route by query type

```python
import psycopg2

primary = psycopg2.connect(PRIMARY_DSN)
replica = psycopg2.connect(REPLICA_DSN)

def get_user_orders(user_id: str) -> list:
    with replica.cursor() as cur:   # reads → replica
        cur.execute("SELECT * FROM orders WHERE user_id = %s", (user_id,))
        return cur.fetchall()

def create_order(req: dict) -> dict:
    with primary.cursor() as cur:   # writes → primary
        cur.execute("INSERT INTO orders (...) VALUES (...) RETURNING id", (...,))
        primary.commit()
        return cur.fetchone()
```

### Caching Layer in Front of DB

```
Read path with cache:
  1. Check Redis cache (hit rate target: 90%+)
  2. Cache miss: query replica, populate cache (TTL: 60-300 seconds)
  3. Return cached result

Write path (cache invalidation):
  Option A: Invalidate on write (most common)
    cache.delete("product:" + productId)  // Forces re-fetch on next read
  
  Option B: Write-through (update cache on write)
    cache.set("product:" + productId, updatedProduct, TTL)
    Pros: No cold miss after write. Cons: Cache and DB writes must be atomic.
    Use transactional outbox pattern for atomicity.

  Option C: TTL-based (no explicit invalidation)
    Accept stale data for TTL duration (fine for product catalog, news feeds)
    Simple. Works well when staleness ≤ seconds is acceptable.
```

---

## 5. Write Scaling: Sharding

### When to Shard

**Question**: Your primary write QPS is 8,000/sec and growing. Postgres can handle ~50,000 simple writes/sec, but your writes are complex — 3 index updates per row. Effective throughput is ~15,000 writes/sec before index maintenance starts slowing down. You're at 50% of that ceiling today. At what point do you shard, and why is that decision so expensive to reverse?

**Physical constraint**: Each secondary index update is a random write to a B-tree page. At high write rates, index pages are constantly being written, evicting other pages from buffer cache, increasing the chance that the next write hits a cold page. Write amplification (one logical write → multiple physical writes for indexes) grows with the number of indexes and the size of each index.

**Minimal solution**: Vertical scale + PgBouncer + async writes (write to Kafka, batch-write to DB). This can buy significant headroom. Sharding is a last resort — it adds enormous operational complexity: routing logic in application code, cross-shard queries become impossible, resharding under live traffic is weeks of careful work.

**Generalize**: When you must shard, choose the shard key carefully. Properties: high cardinality, even write distribution (no hotspots), queries almost always include the shard key, key is stable after write. Use consistent hashing to minimize data movement on resize. Plan for resharding from day one — even if you never need it, the plan forces you to pick the right key.

```
Signal 1: Primary write QPS > 10K/sec (Postgres max ~50K simple writes/sec)
Signal 2: Table > 500GB with frequent writes (index maintenance slows writes)
Signal 3: Single-shard IOPS limited by storage throughput

Before sharding, try:
  - Vertical scaling (bigger instance)
  - Connection pooling (PgBouncer)
  - Async writes (write to Kafka, batch-write to DB)
  - Archival (move old data to cold storage, shrink hot table)
  
Sharding is a last resort — adds enormous operational complexity.
```

### Shard Key Selection (Critical Decision)

```
Good shard key properties:
  1. High cardinality (many distinct values → even distribution)
  2. Evenly distributed writes (no hotspots)
  3. Queries usually include the shard key (avoid cross-shard queries)
  4. Stable (shard key doesn't change after write)

Examples:
  Orders: shard by user_id (user's orders stay together, no cross-shard joins)
  Messages: shard by conversation_id (all messages in a chat on same shard)
  Metrics: shard by (tenant_id, time) — time ensures even write distribution

Anti-pattern: shard by timestamp (all writes go to the same "latest" shard — hotspot!)
Anti-pattern: shard by country (USA shard gets 50× traffic of Ghana shard — uneven)
```

### Resharding (the painful part)

```
Problem: You start with 4 shards, then outgrow them. Need 8 shards.
         Moving data while serving live traffic is extremely difficult.

Strategy: Consistent hashing (reduces data movement on resize)
  4 shards → 8 shards: only ~50% of data needs to move (vs 100% with modulo hashing)

Process:
  1. Add new shard nodes
  2. Start dual-writing: write to old shards + new shards
  3. Backfill: copy historical data from old → new shard assignments
  4. Once backfill complete: stop dual-write, route all reads/writes to new shards
  5. Decommission old shards

Duration: Weeks for large datasets. Never underestimate resharding cost.
```

---

## Quick Revision

- **WAL**: Every write to WAL before data files. Sequential writes → fast. Enables crash recovery + replication.
- **MVCC**: Readers see consistent snapshots. Writers never block readers. Dead tuples need VACUUM.
- **PgBouncer**: Transaction-mode pooling. 50 real connections → 5,000 app connections.
- **Index strategy**: Composite (ordered by selectivity), partial (sparse conditions), covering (include extra columns), GIN (full-text/JSONB).
- **Read scaling**: Read replicas + cache. Route `readOnly=true` queries to replicas.
- **Shard key**: High cardinality, even distribution, query-aligned, stable.
- **Resharding**: Consistent hashing reduces moves. Dual-write during migration.
- **Interview**: "I scale reads first with replicas and a Redis cache layer (targeting 90%+ hit rate). Writes go vertical (bigger instance + PgBouncer for connections) until QPS > 10K or table > 500GB, at which point I evaluate sharding — choosing shard key carefully to avoid cross-shard queries and hotspots."
