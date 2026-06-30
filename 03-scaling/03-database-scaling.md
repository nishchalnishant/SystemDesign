> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A deep dive into database scaling from a single PostgreSQL node to a globally distributed data tier — WAL internals, MVCC, connection pooling, and sharding at scale.
>
> **Key topics:**
> - PostgreSQL WAL: sequential write-first → checkpoint → crash recovery; WAL is also the replication stream
> - MVCC: xmin/xmax row versions → readers never block writers; dead tuples need VACUUM
> - Connection pooling with PgBouncer: session vs transaction vs statement modes; pool_size tuning
> - Index strategies: composite index column order, covering indexes, partial indexes, index-only scans
> - Read vs write scaling path: add replicas → connection pooling → caching → query optimization → shard
> - Sharding at scale: virtual shards, cross-shard scatter-gather, shard key selection anti-patterns
> - OLTP vs OLAP separation: CDC to pipeline writes into a columnar store (Redshift, BigQuery, ClickHouse) for analytics
>
> **Key takeaway:** Before sharding, exhaust all single-node options: PgBouncer connection pooling + read replicas + covering indexes can handle 10× more load than a naive single node.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, scaling]
---
# Database Scaling

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

## Connection Pooling

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

## Index Strategies for Scale

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

## Read Scaling Patterns

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

## Write Scaling: Sharding

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

## Time-Series Data Scaling

**Question**: You collect 100,000 metrics data points per second. After 1 year that's ~3 trillion rows. A standard Postgres table at that scale has an index that no longer fits in RAM. Every query does index page I/Os. INSERT performance degrades because index pages must be fetched, modified, and written. How do you store append-only time-series data without killing insert throughput or query performance?

**Physical constraint**: A B-tree index on a table with 3 trillion rows is ~20 levels deep. Each index write touches ~20 pages. At 100,000 inserts/sec that's 2,000,000 index page writes/sec — far exceeding NVMe throughput for random writes. The index simply cannot keep up.

**Minimal solution**: Range partitioning by time. Each partition (e.g. one month) is a separate physical table with its own smaller index. Recent queries only scan the recent partition. Old partitions can be moved to cold storage. Works until: partition management is manual and error-prone, or write throughput exceeds what even a single-partition index can handle.

**Generalize**: TimescaleDB (a Postgres extension) automates time-based partitioning (hypertables), pre-computes continuous aggregates, and compresses old data 90–95% using dictionary + delta encoding. For even higher write throughput, ClickHouse (columnar, 100K+ inserts/sec) or Cassandra (time-series with partition key by sensor/time).

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

## Auto-Scaling Database

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
