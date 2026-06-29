---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, hld, cheat-sheet]
---
# HLD Cheat Sheet

Use this as the last-page review before a high-level design interview.

---

## 45-Minute Flow

| Time | What to do | Output |
|------|------------|--------|
| 0-5 | Clarify scope | 3-5 functional requirements, exclusions |
| 5-10 | Non-functional requirements | latency, availability, consistency, scale |
| 10-15 | Capacity estimate | QPS, storage, bandwidth, cache size |
| 15-20 | APIs | endpoints, request/response, idempotency |
| 20-25 | Data model | entities, keys, indexes, access patterns |
| 25-35 | Architecture | clients, LB, services, DB, cache, queue |
| 35-45 | Deep dives | bottleneck, failure handling, trade-offs |

---

## Core Questions

Ask these early:

```
What are the top 3 features?
What is the read/write ratio?
What scale should I design for?
What consistency is required?
Can we tolerate eventual consistency anywhere?
What should I explicitly keep out of scope?
```

---

## Numbers to Know

```
1 day = 86,400 seconds ~= 100K seconds
1M requests/day ~= 12 QPS
100M requests/day ~= 1.2K QPS
1B requests/day ~= 12K QPS

1 KB * 1B = 1 TB
1 MB * 1M = 1 TB

Memory read ~= 100 ns
Network same DC ~= 0.5 ms
SSD read ~= 100 us - 1 ms
Cross-region ~= 50-150 ms
```

---

## Default Architecture

```
Client
  -> CDN / Edge Cache
  -> Load Balancer
  -> API Gateway
  -> Stateless Services
  -> Cache
  -> Primary Database
  -> Message Queue
  -> Workers
  -> Object Storage / Search / Analytics
```

Use this as the baseline, then remove components you do not need.

---

## Database Choice

### Quick Decision Table

| Scenario | Choose | Why not the others |
|---|---|---|
| User accounts, orders, payments, inventory | PostgreSQL / MySQL | Needs JOINs, transactions, foreign keys |
| User sessions, caching, leaderboards, counters | Redis | Sub-ms reads; data fits in RAM |
| Product catalog, user profiles, flexible schema | DynamoDB | Known access pattern, horizontal scale, no JOINs needed |
| Time-series metrics, IoT events, logs | Cassandra / InfluxDB | High write throughput, time-range queries |
| Social graph, fraud detection, recommendations | Neo4j / Neptune | Relationship traversal; SQL JOINs across 5+ hops are prohibitive |
| Full-text product / document search | Elasticsearch | Inverted index; SQL LIKE is full-table scan |
| Event stream, audit log, replay | Kafka | Durable, replayable, ordered per partition |
| Simple async job queue | SQS | At-least-once delivery, DLQ, no replay needed |
| Files, images, video, backups | S3 | Unlimited scale, CDN-friendly, lifecycle policies |
| Analytics, OLAP, large aggregations | Redshift / BigQuery / Snowflake | Columnar storage; not for transactional workloads |

---

### SQL (PostgreSQL / MySQL)

**Use when**: financial transactions, user data with relational integrity, complex reporting queries, any time you need ACID across multiple tables.

| Pros | Cons |
|---|---|
| ACID transactions across multiple tables | Vertical scaling hits ceiling (~10K writes/sec on single primary) |
| Flexible queries — JOINs, GROUP BY, subqueries | Schema changes on large tables are slow (locks) |
| Mature tooling, indexes, constraints | Horizontal write scaling requires sharding (complex) |
| Strong consistency by default | Bad for unstructured / highly variable schemas |

**Amazon interview phrasing**: "I'd use PostgreSQL here because the payment flow requires atomicity across the `orders`, `payments`, and `inventory` tables — a failed payment must roll back all three."

---

### DynamoDB (Key-Value / Document)

**Use when**: known, simple access patterns (get by user_id, list by user_id + timestamp), need to scale horizontally without ops overhead, high read/write throughput.

| Pros | Cons |
|---|---|
| Horizontal scale to any throughput — no ops | No JOINs — all access patterns must be pre-modeled |
| Single-digit ms reads at any scale | Queries outside partition/sort key require full scan or GSI |
| Fully managed, TTL, streams built-in | Strong consistent reads cost 2× read units |
| GSI (Global Secondary Index) for alternate access patterns | 400KB item size limit; no aggregations |

**Design rule**: model access patterns first, then choose partition key + sort key. Bad partition key = hot partition = throttling.

**Amazon interview phrasing**: "I'd use DynamoDB with partition key `user_id` and sort key `created_at` — this gives me O(1) writes and efficient range scans for a user's history without JOINs."

---

### Redis (In-Memory Key-Value)

**Use when**: caching database query results, session storage, rate limiting counters, leaderboards, pub/sub fanout, distributed locks.

| Pros | Cons |
|---|---|
| Sub-millisecond reads and writes | Data must fit in RAM (expensive at scale) |
| Rich data structures: sorted sets, lists, hashes, streams | Persistence is optional — volatile by default |
| Atomic operations (INCR, ZADD) — no race conditions on counters | Not a primary store — cache invalidation is your problem |
| Built-in TTL per key | Single-threaded command execution (though fast) |

**Common patterns**:
- Rate limiting: `INCR` + `EXPIRE` on `ratelimit:{user_id}:{window}`
- Leaderboard: `ZADD scores {score} {user_id}` + `ZREVRANGE`
- Distributed lock: `SET lock:resource uuid NX EX 30`
- Session store: `HSET session:{token} user_id ... EX 3600`

---

### Cassandra (Wide-Column)

**Use when**: write-heavy workloads, time-series data, data that maps naturally to (partition key → sorted rows), need multi-region active-active.

| Pros | Cons |
|---|---|
| Extremely high write throughput (LSM tree, sequential writes) | No JOINs, no transactions across partitions |
| Linear horizontal scale — add nodes, throughput scales | Read latency higher than Redis; may touch multiple SSTables |
| Tunable consistency (ONE / QUORUM / ALL per query) | Schema must be designed around queries — not flexible |
| Multi-region active-active replication built-in | Deletes are expensive (tombstones linger until compaction) |

**Use for**: message history (WhatsApp), time-series metrics, activity feeds, IoT sensor data.

---

### Elasticsearch (Search)

**Use when**: full-text search, faceted filtering (filter by price + category + rating simultaneously), log analytics.

| Pros | Cons |
|---|---|
| Inverted index — fast full-text and fuzzy search | Not a source of truth — sync lag from primary DB (CDC) |
| Bool queries combine text match + range filters | Expensive at scale; complex cluster tuning |
| Aggregations for analytics (avg price by category) | Eventual consistency with source DB |
| Fuzzy matching, autocomplete, scoring built-in | Schema mapping changes require reindex |

**Write path**: primary DB → CDC (Debezium) → Kafka → Elasticsearch indexer. Acceptable lag: 1–2s.

---

### Kafka (Event Stream)

**Use when**: event sourcing, audit log, fan-out to multiple consumers, replay, decoupling services, ordered-per-entity event processing.

| Pros | Cons |
|---|---|
| Durable, replayable log — consumers can re-read from offset 0 | Operationally complex (partitions, consumer groups, offsets) |
| High throughput — millions of events/sec | Not a queue — no per-message deletion; retention-based cleanup |
| Fan-out: many consumer groups read same topic independently | At-least-once delivery — consumers must be idempotent |
| Ordering guaranteed within a partition | No native request-reply pattern |

**Partition key rule**: partition by the entity that must be ordered (e.g., `order_id` so all events for one order go to the same partition → processed in order).

---

### S3 / Object Storage

**Use when**: user-uploaded files, images, video, ML training data, backups, static assets.

| Pros | Cons |
|---|---|
| Unlimited storage, pay per GB | Not queryable — must know the key |
| Strongly consistent (since 2020) | No partial reads without byte-range requests |
| Lifecycle policies — auto-transition to Glacier, auto-expire | Latency ~50–200ms per object fetch (use CDN for hot content) |
| Presigned URLs — client uploads directly (bypasses your server) | No atomic multi-object transactions |

---

### When to Use Multiple Databases Together

Most real systems use 3–4 databases. Common combos:

| System | Primary DB | Cache | Search | Event Stream |
|---|---|---|---|---|
| E-commerce | PostgreSQL (orders) | Redis (sessions, cart) | Elasticsearch (product search) | Kafka (order events) |
| Social media | DynamoDB (posts, follows) | Redis (feed cache, counters) | Elasticsearch (user/post search) | Kafka (activity events) |
| Messaging | Cassandra (message history) | Redis (online presence) | — | Kafka (message delivery) |
| Ride-sharing | PostgreSQL (trips, payments) | Redis (driver locations) | — | Kafka (location events) |

Amazon interview phrasing:

```
I'd use PostgreSQL as the source of truth for order data,
Redis to cache user sessions and rate limit API calls,
and Elasticsearch to power the product search — synced
via CDC from Postgres with ~1s lag, which is acceptable
for search freshness.
```

---

## Cache Patterns

| Pattern | Use when | Risk |
|---------|----------|------|
| Cache-aside | app controls cache fill | stale reads |
| Write-through | cache and DB updated together | write latency |
| Write-back | high write volume | data loss on cache failure |
| Read-through | cache abstracts DB reads | vendor coupling |

Invalidation options:
- TTL
- explicit delete on write
- versioned keys
- event-driven invalidation

---

## Queue Patterns

Use a queue when work can happen asynchronously.

| Requirement | Pattern |
|-------------|---------|
| fan out one event to many consumers | SNS -> SQS |
| retry failed tasks | queue with DLQ |
| avoid duplicate side effects | idempotency key |
| long-running work | workers + status table |
| strict per-user ordering | partition by user id |

Amazon terms to mention:
- visibility timeout
- dead-letter queue
- exponential backoff
- idempotent consumer
- CloudWatch alarm on queue depth

---

## Failure Checklist

For every design, cover:

```
What if the cache is down?
What if the database primary fails?
What if the queue grows faster than workers consume?
What if the same request is retried?
What if a downstream service times out?
What data can be stale?
What data cannot be lost?
```

---

## Common Trade-offs

| Decision | Trade-off |
|----------|-----------|
| push vs pull feed | low read latency vs expensive writes |
| SQL vs NoSQL | transactions/joins vs horizontal scale |
| sync vs async | immediate correctness vs lower latency |
| strong vs eventual consistency | correctness vs availability/latency |
| global vs regional service | consistent global state vs low latency |
| shard by user vs object | easy user queries vs hot celebrity keys |

---

## Strong Closing

End with:

```
The main bottleneck is X.
The main correctness risk is Y.
I handle failures with retries, idempotency, DLQs, and alarms.
At 10x scale, I would shard/cache/partition this component first.
```

