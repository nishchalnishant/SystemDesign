# Database Scaling Deep Dive

> **From a single Postgres to a globally distributed data tier: WAL internals, MVCC, connection pooling, index strategies, and sharding at scale.**

---

## 1. PostgreSQL Internals That Matter for Scaling

### Write-Ahead Log (WAL)

> **Analogy**: Before a surgeon makes an incision, they document the procedure in the patient's chart. If anything goes wrong mid-surgery, the chart allows recovery to a known-good state. The WAL is PostgreSQL's chart — every change is documented before it happens.

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

> **Analogy**: A busy restaurant where each customer (app thread) needs their own waiter (database connection). Hiring 1,000 waiters is expensive (memory, context switching). A pool of 50 waiters serves 1,000 customers by sharing connections — each transaction uses a waiter briefly, then returns them to the pool.

### Why Connection Limits Matter

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
  Spring Boot DataSource routing:
```

```java
@Transactional(readOnly = true)
public List<Order> getUserOrders(String userId) {
    // Spring routes this to read replica due to readOnly=true
    return orderRepository.findByUserId(userId);
}

@Transactional
public Order createOrder(OrderRequest req) {
    // Routes to primary (write transaction)
    return orderRepository.save(new Order(req));
}
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

## 6. Time-Series Data Scaling

```
Problem: append-only time-series data (metrics, events, logs) grows unboundedly.
         Standard table: billion rows, degraded performance.

PostgreSQL table partitioning:
  Partition by time range — each partition is a separate physical table
  Queries on recent data only scan recent partition (fast)
  Old partitions can be moved to slower storage or dropped

Example:
CREATE TABLE metrics (
    metric_name VARCHAR(100),
    value       DOUBLE PRECISION,
    timestamp   TIMESTAMPTZ,
    tags        JSONB
) PARTITION BY RANGE (timestamp);

CREATE TABLE metrics_2026_05 PARTITION OF metrics
  FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE TABLE metrics_2026_06 PARTITION OF metrics
  FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- Automate with pg_partman extension
-- Old partitions: detach → archive to S3 → drop

TimescaleDB (Postgres extension):
  Automatic time-based partitioning (hypertables)
  Continuous aggregates: pre-compute hourly/daily rollups automatically
  Compression: 90-95% reduction for time-series data (dictionary + delta encoding)
  Recommended for metrics, IoT, financial tick data

Alternative for very high write throughput:
  ClickHouse: columnar, 100K+ inserts/sec, sub-second OLAP queries
  Apache Cassandra: excellent for time-series (partition by sensor_id, cluster by timestamp)
```

---

## 7. Auto-Scaling Database

### Read Replica Auto-Scaling (AWS Aurora)

```
Aurora Auto Scaling:
  Monitors: CPU utilization, connection count, replica lag
  Scale out: Add read replica when avg CPU > 70% for 5 minutes
  Scale in: Remove replica when avg CPU < 30% for 15 minutes
  Warmup: ~5-10 minutes to provision new Aurora replica
  
Challenge: Read traffic redirected before replica is fully warmed up
Solution: Application-level health check on replica before routing traffic
          Aurora Proxy handles this automatically

Predictive scaling:
  Historical patterns: Monday 9am always spikes
  Pre-scale at 8:45am proactively (before spike, not in response to it)
  AWS: Scheduled scaling actions for predictable traffic patterns
```

### Write Scaling: Vitess (MySQL at YouTube/GitHub scale)

```
Vitess: Kubernetes-native sharding middleware for MySQL

Architecture:
  VTGate: Query router (stateless, scales horizontally)
  VTTablet: Per-shard MySQL proxy (manages connections, query rewriting)
  Topology Service (etcd/ZooKeeper): Shard map, routing metadata

Automatic resharding:
  Split shard: copy data → redirect traffic → drain old shard
  Minimal manual intervention
  Powers YouTube, GitHub, Slack, HubSpot
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
- **Time-series**: Table partitioning or TimescaleDB. ClickHouse for OLAP.
- **Interview**: "I scale reads first with replicas and a Redis cache layer (targeting 90%+ hit rate). Writes go vertical (bigger instance + PgBouncer for connections) until QPS > 10K or table > 500GB, at which point I evaluate sharding — choosing shard key carefully to avoid cross-shard queries and hotspots."
