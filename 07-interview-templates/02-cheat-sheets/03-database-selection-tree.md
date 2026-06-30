> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A structured decision tree to help you quickly choose the right database for a system design problem and justify it to the interviewer.
>
> **Key concepts:**
> - Relational (SQL): Use for structured data with strict ACID requirements (e.g., financial transactions, billing).
> - Key-Value (Redis/DynamoDB): Use for simple lookups by ID, caching, or session storage.
> - Document (MongoDB/Couchbase): Use for unstructured or evolving data where the schema isn't fixed (e.g., product catalogs, user profiles).
> - Wide-Column (Cassandra/HBase): Use for massive write-heavy time-series data without complex joins (e.g., chat histories, IoT metrics).
> - Graph (Neo4j): Use for highly interconnected data where traversing relationships is the primary query (e.g., social networks, recommendation engines).
>
> **Key takeaway:** "Because it's fast" is not a valid justification. Use this tree to say: "Because our data has fluid schemas and we need high write throughput without complex joins, a Document database like MongoDB fits best."

---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, interview-templates]
---
# Database Selection Decision Tree

> **Choose the database that matches your access patterns, consistency requirements, and operational constraints — not the one you're most familiar with.**

---

## The Decision Tree

```
START: What are your primary access patterns?
│
├─ Structured data with complex relationships + ACID needed?
│   └─► SQL (PostgreSQL, MySQL)
│
├─ Key-value lookups, simple schema, massive write scale?
│   ├─ Need complex queries or aggregations?
│   │   └─► DynamoDB with GSIs  (or Cassandra with careful data modeling)
│   └─ Pure key-value, sub-ms latency?
│       └─► Redis (cache/session) or DynamoDB (durable)
│
├─ Time-series data (metrics, events with timestamps)?
│   └─► InfluxDB, TimescaleDB, or Cassandra TWCS
│
├─ Full-text search, relevance scoring?
│   └─► Elasticsearch / OpenSearch (+ a primary DB for source of truth)
│
├─ Graph relationships (social network, fraud detection)?
│   └─► Neo4j, Amazon Neptune
│
├─ Document store, flexible schema, nested objects?
│   ├─ Need strong consistency + transactions?
│   │   └─► MongoDB (with transactions, 4.0+)
│   └─ Eventual consistency ok?
│       └─► CouchDB, MongoDB (without transactions)
│
└─ Globally distributed, multi-region writes, strong consistency?
    └─► Google Spanner, CockroachDB
```

---

## SQL Databases (PostgreSQL, MySQL)

**Use when**:
- Data is relational — entities with foreign keys, JOINs
- Need ACID transactions across multiple tables
- Complex queries: aggregations, GROUP BY, ORDER BY, window functions
- Schema is relatively stable and well-defined
- Team is familiar with SQL

**Do not use when**:
- Write QPS > 50K (single primary struggles; sharding SQL is painful)
- Schema changes frequently at high velocity (ALTER TABLE on large tables is blocking)
- Data model is hierarchical/graph-like with unlimited depth

**PostgreSQL vs MySQL**:

| Feature | PostgreSQL | MySQL |
|---------|-----------|-------|
| Default isolation | Read Committed | Repeatable Read |
| JSON support | JSONB (indexed) | JSON (less featured) |
| Full-text search | tsvector/tsquery | Basic |
| Logical replication | Built-in (pglogical) | Binlog + tools |
| Extensions | Rich (PostGIS, timescaledb, pg_vector) | Limited |
| Recommendation | **Preferred for new projects** | Strong for existing MySQL shops |

**Capacity**:
- Single primary: 2K–10K write QPS depending on write size
- With read replicas: scales reads horizontally
- With PgBouncer (connection pooler): 10K connections → 100 actual DB connections

---

## DynamoDB

**Use when**:
- Access patterns are known upfront and primarily key-value or single-table
- Need single-digit millisecond latency at any scale
- Serverless/managed preferred — zero ops
- Write QPS is unpredictable or extremely high (millions/s with auto-scaling)
- Global tables needed for multi-region active-active

**Do not use when**:
- Need complex queries (multi-column filters, JOINs, aggregations)
- Access patterns are not known at design time
- Budget is a concern at low QPS (DynamoDB minimum costs add up)

**Key design constraint**: All access patterns must be expressible via:
- Primary key (PK + optional SK): exact lookup
- GSI (Global Secondary Index): lookup by alternate key
- Query on PK + SK range: range queries within a partition

```
Example single-table design for Order service:

PK              | SK                | Data
----------------|-------------------|---------
USER#alice      | PROFILE           | {name, email}
USER#alice      | ORDER#2024-01-15  | {status, total}
ORDER#ord-123   | ITEM#prod-456     | {qty, price}
ORDER#ord-123   | META             | {created_at, user_id}

GSI1: SK → PK (invert the table for reverse lookups)
GSI2: status → created_at (query all PENDING orders by date)
```

**Consistency options**:
- Eventually consistent reads (default, cheaper): may return stale data
- Strongly consistent reads (2x cost): always returns latest committed value
- Transactions (TransactWriteItems): serializable across up to 100 items

---

## Cassandra

**Use when**:
- Extremely high write throughput (millions writes/s)
- Time-series or event log: writes always append, rarely update
- Multi-datacenter replication required (active-active multi-region)
- Predictable low-latency writes regardless of data size
- Data access pattern is: write fast, read by known partition key

**Do not use when**:
- Need ad-hoc queries or complex aggregations
- Need cross-partition transactions
- Data model requires secondary index lookups at high cardinality

**Data modeling rule**: one table per query. No JOINs, no multi-partition queries. Design tables around your exact read patterns.

```sql
-- Good: query by user_id and time range
CREATE TABLE user_events (
    user_id UUID,
    event_time TIMESTAMP,
    event_type TEXT,
    payload TEXT,
    PRIMARY KEY (user_id, event_time)
) WITH CLUSTERING ORDER BY (event_time DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy',
                    'compaction_window_size': 1,
                    'compaction_window_unit': 'DAYS'};

-- Bad: no way to query "all events of type X" efficiently
-- Must create a separate table for that query pattern
```

**Consistency tuning**:
- `CONSISTENCY ONE`: lowest latency, may read stale
- `CONSISTENCY QUORUM`: majority, balanced
- `CONSISTENCY ALL`: all replicas must respond (high latency, low availability)
- **Rule**: `Write(QUORUM) + Read(QUORUM) ≥ ReplicationFactor + 1` → strong consistency

---

## MongoDB

**Use when**:
- Document model fits (nested objects, variable schema)
- Need flexible schema evolution (add fields without migration)
- Complex queries needed but not as complex as relational joins
- Aggregation pipelines for analytics on document collections
- Team prefers JSON-native query language

**Do not use when**:
- Relational integrity is critical (MongoDB lacks foreign key enforcement)
- Write QPS is extremely high (Cassandra better for pure write throughput)
- Strong consistency across multiple collections is required frequently

**Key features**:
- Transactions (since 4.0): ACID across multiple documents/collections
- Change streams: CDC-like event stream for reactive architectures
- Atlas Search: Lucene-based full-text search integrated into MongoDB Atlas
- $lookup: JOIN equivalent (but slower than relational JOINs)

---

## Redis

**Use when**:
- In-memory speed required: sessions, rate limiting counters, real-time leaderboards
- TTL-based expiration (cache, temporary holds)
- Pub/Sub messaging (real-time notifications, not for durable event log)
- Atomic operations on counters, sets, sorted sets
- Distributed lock (SET NX PX)

**Do not use when**:
- Data must survive restarts without replication (pure in-memory mode)
- Dataset exceeds available RAM (Redis is memory-bound)
- Need complex queries or relational integrity

**Common patterns**:

| Pattern | Redis Structure | Command |
|---------|----------------|---------|
| Cache with TTL | String | SET k v EX 300 |
| Distributed lock | String | SET k uuid NX PX 10000 |
| Rate limiting | String | INCR / EXPIRE |
| Leaderboard | Sorted Set | ZADD / ZRANK |
| Session store | Hash | HSET / HGETALL |
| Job queue | List | LPUSH / BRPOP |

---

## Elasticsearch / OpenSearch

**Use when**:
- Full-text search with relevance scoring (BM25, custom scoring)
- Log aggregation and analytics (ELK stack)
- Faceted search (filter by category, price range, rating simultaneously)
- Geo-spatial queries (nearby locations)

**Always combine with a primary DB**:
```
Primary write: PostgreSQL (source of truth)
          ↓
       CDC (Debezium)
          ↓
  Elasticsearch (search index, can be rebuilt from DB)
```

Elasticsearch is eventually consistent. Never make it the only store for data you can't reconstruct.

---

## Google Spanner / CockroachDB

**Use when**:
- Global, multi-region active-active with strong consistency
- ACID transactions across geographic regions
- SQL interface required at global scale
- Regulatory requirement for data in specific regions with global availability

**Trade-offs**:
- Higher latency (cross-region coordination: 50–200ms vs 1–5ms single-region)
- Higher cost (Spanner: ~$0.90/node/hour minimum, plus storage)
- Complexity: TrueTime (Spanner) or Hybrid Logical Clocks (CockroachDB) for ordering

**Spanner TrueTime**:
```
Google's GPS + atomic clock infrastructure gives bounded uncertainty: ε = [now - δ, now + δ]
where δ ≈ 7ms. Spanner waits for this uncertainty interval to pass before committing.
This guarantees external consistency: commit order matches real-world time order.
CockroachDB approximates this with HLC (Hybrid Logical Clocks) + NTP.
```

---

## Quick Selection Guide

| Requirement | First Choice | Second Choice |
|-------------|-------------|---------------|
| ACID + complex queries | PostgreSQL | MySQL |
| High write throughput, simple key access | DynamoDB | Cassandra |
| Time-series / append-heavy | Cassandra (TWCS) | TimescaleDB |
| Full-text search | Elasticsearch | PostgreSQL tsvector |
| In-memory cache + session | Redis | Memcached |
| Graph traversal | Neo4j | Amazon Neptune |
| Flexible document schema | MongoDB | DynamoDB |
| Global active-active, strong | Spanner | CockroachDB |
| Event streaming / log | Kafka | Kinesis |

---

## Interview Pattern: Justify Your Database Choice

Always structure database selection as:
1. **Access pattern**: "The primary read is X, primary write is Y"
2. **Consistency requirement**: "Writes must be [immediately/eventually] consistent"
3. **Scale**: "We need to handle X writes/s, Y reads/s"
4. **Choice**: "Therefore I'd use Z because..."
5. **Trade-off acknowledged**: "The downside is [no joins / higher cost / eventual consistency]"

Example: "For a leaderboard, I'd use Redis Sorted Sets — ZADD is O(log N), ZRANK is O(log N), and the entire leaderboard fits in memory. The trade-off is memory-bound and no persistence guarantee without AOF — acceptable for a leaderboard where slight staleness on restart is fine."

---

## See Also

- **Architecture by scale**: [07-interview-templates/architecture-by-scale.md](architecture-by-scale.md)
- **Cassandra internals**: [04-advanced-topics/internals/cassandra-internals.md](../04-advanced-topics/internals/cassandra-internals.md)
- **MVCC and isolation levels**: [01-foundations/databases.md](../01-foundations/databases.md)
- **PACELC**: [01-foundations/fundamentals.md](../01-foundations/fundamentals.md)
