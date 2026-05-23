---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Change Data Capture (CDC)

## What Is CDC?

**Change Data Capture** is the process of tracking every insert, update, and delete in a database and making those changes available as a stream of events to downstream consumers.

Real-world analogy: Instead of asking "what does this spreadsheet look like right now?", CDC gives you the **audit log** — every edit, who made it, and when. Downstream systems can replay the audit log to reconstruct any state.

**Why not just query the database?**

- Polling (`SELECT * WHERE updated_at > last_checked`) misses deletes and has race conditions
- High-frequency polling hammers the source database
- You can't detect which specific columns changed
- You miss intermediate states (a row updated twice between polls)

CDC solves all of these by tapping into the database's internal replication log.

---

## How Databases Record Changes

Every production-grade database maintains a **Write-Ahead Log (WAL)** or equivalent for crash recovery and replication.

| Database | Log Name | CDC Access Method |
|---|---|---|
| PostgreSQL | WAL (pg_wal) | Logical Replication / `pg_logical` |
| MySQL | Binary Log (binlog) | `binlog` format = ROW |
| MongoDB | Oplog | Change Streams |
| SQL Server | Transaction Log | CDC feature (built-in) |
| Oracle | Redo Log | LogMiner or GoldenGate |

The CDC tool reads this log as a replication client — from the database's perspective, it looks like a replica.

---

## Log-Based CDC vs Query-Based CDC

### Log-Based CDC

**How it works**: Connect to the database's replication log as a replica. Read change events directly from the binary/WAL log.

```
Database WAL/Binlog
       │
       ▼
  CDC Connector (Debezium)
       │
       ▼
  Kafka Topic (change events)
       │
   ┌───┴────────────┐
   ▼                ▼
 Search Index    Data Warehouse
 (Elasticsearch) (Snowflake)
```

**Advantages**:
- Captures all changes including deletes
- Zero additional load on the source database (reads the log file, not live tables)
- Low latency (milliseconds after commit)
- Detects every intermediate state
- Does not require schema changes to the source table

**Disadvantages**:
- Tightly coupled to database internals (log format changes between DB versions)
- Requires replication privileges on the source DB
- Log retention must be configured (PostgreSQL WAL can fill disk if consumers lag)
- Schema evolution in the change events requires careful handling (Avro + Schema Registry)

### Query-Based CDC (Polling)

**How it works**: Periodically run `SELECT * FROM table WHERE updated_at > :last_watermark`.

**Advantages**:
- Simple to implement
- No special database privileges needed
- Works with any database or API

**Disadvantages**:
- Misses hard deletes (deleted rows have no `updated_at`)
- Misses intermediate updates (if a row is updated twice between polls, you see only the final state)
- Polling interval = maximum latency for downstream systems
- Adds read load to the source DB
- Requires an `updated_at` column on every table (schema change required)
- Clock skew between application and DB can cause missed events

### Trigger-Based CDC

**How it works**: Add database triggers that write to an audit/outbox table on every change.

**Advantages**: Captures all changes including deletes.

**Disadvantages**:
- Every write to the source table now does 2 writes (source + audit) — 2x write amplification
- Triggers fire synchronously in the transaction — increases transaction latency
- Audit table becomes a bottleneck under high write load
- Trigger logic must be maintained alongside schema migrations

**Verdict**: Log-based CDC is almost always the right choice for production systems. Query-based is acceptable for low-volume, low-latency-requirement batch jobs.

---

## Debezium: The Standard Log-Based CDC Tool

Debezium is an open-source log-based CDC platform built on Kafka Connect. It has connectors for PostgreSQL, MySQL, MongoDB, SQL Server, Oracle, and more.

### Architecture

```
Source DB (PostgreSQL)
  WAL (logical replication slot)
       │
  Debezium Postgres Connector
  (runs in Kafka Connect worker)
       │
  Kafka Topics (one per table: e.g. mydb.public.orders)
       │
  Consumers (Elasticsearch Sink, JDBC Sink, custom consumers)
```

### Kafka Connect Worker

Debezium runs as a **Kafka Connect source connector**. The Connect framework handles:
- Offset tracking (which WAL position has been consumed)
- Fault tolerance (offsets stored in Kafka topic `connect-offsets`)
- Parallelism (multiple partitions, multiple workers)

### Change Event Format

Each Debezium event contains:

```json
{
  "op": "u",
  "ts_ms": 1716400000000,
  "before": { "id": 1, "status": "pending", "amount": 100 },
  "after":  { "id": 1, "status": "shipped", "amount": 100 },
  "source": {
    "db": "mydb",
    "table": "orders",
    "lsn": 12345678,
    "txId": 499
  }
}
```

- `op`: `c` (create/insert), `u` (update), `d` (delete), `r` (read/snapshot), `t` (truncate)
- `before`: row state before the change (null for inserts)
- `after`: row state after the change (null for deletes)
- `lsn`: Log Sequence Number — the WAL position (used as offset)

### PostgreSQL-Specific Setup

PostgreSQL requires a **logical replication slot**:

```sql
-- Enable logical replication in postgresql.conf
wal_level = logical
max_replication_slots = 4
max_wal_senders = 4

-- Create replication slot (Debezium does this automatically)
SELECT pg_create_logical_replication_slot('debezium', 'pgoutput');
```

**Critical operational concern**: A replication slot retains WAL until the consumer has read and acknowledged it. If the Debezium connector is down for hours/days, WAL accumulates on the PostgreSQL server and **can fill the disk**. Always monitor `pg_replication_slots` for `confirmed_flush_lsn` lag.

```sql
SELECT slot_name, confirmed_flush_lsn, pg_current_wal_lsn(),
       pg_current_wal_lsn() - confirmed_flush_lsn AS lag
FROM pg_replication_slots;
```

---

## CDC Patterns and Use Cases

### Pattern 1: Database to Search Index Sync

Keep Elasticsearch in sync with PostgreSQL without dual writes:

```
Orders Table (PostgreSQL)
  → Debezium CDC
  → Kafka topic: mydb.public.orders
  → Elasticsearch Kafka Connect Sink Connector
  → orders index (Elasticsearch)
```

**Why CDC beats dual writes**:
- Dual write: if the app writes to PostgreSQL and Elasticsearch crashes, they diverge
- CDC: Elasticsearch is a derived view of PostgreSQL — if it falls behind, it can catch up from Kafka; if it corrupts, replay from beginning

### Pattern 2: Cache Invalidation

```
Product Table (MySQL)
  → Debezium
  → Kafka: mydb.products
  → Cache Invalidation Service
  → Redis DEL product:{id}
```

The cache invalidation service consumes change events and deletes/updates the affected cache keys immediately after the DB commits.

### Pattern 3: Event Sourcing Bootstrap

An event-sourced system needs to bootstrap from an existing relational DB:

1. Take a snapshot of the source table (Debezium initial snapshot mode)
2. Publish snapshot records as `r` (read) events
3. Switch to streaming mode — publish ongoing changes as `c/u/d` events
4. Downstream consumers replay all events to build their read model

### Pattern 4: Outbox Pattern (CDC-based)

See `09-patterns/outbox-pattern.md` for full detail. The outbox pattern uses CDC to guarantee transactional publishing to message brokers:

1. Application writes to both business table + outbox table in one transaction
2. Debezium reads from outbox table via CDC
3. Publishes to Kafka with exactly-once guarantees

**Key advantage over polling-based outbox**: Debezium detects the outbox row insertion at WAL level — no polling delay, no extra DB load.

### Pattern 5: CQRS Read Model Population

```
Write Model (PostgreSQL)
  → Debezium CDC
  → Kafka
  → Projection Builder (consumes events, builds read model)
  → Read Model (e.g., DynamoDB, Redis, Elasticsearch)
```

The read model is always a derived, eventually consistent view of the write model. If the read model needs to be rebuilt, the projection builder replays the Kafka topic from offset 0.

---

## Schema Evolution

When the source table schema changes, CDC events change shape. Two strategies:

### 1. Avro + Schema Registry (recommended for production)

- Debezium publishes events as Avro with schema ID embedded
- Confluent Schema Registry stores versioned schemas
- Consumers use the schema ID to deserialize correctly
- Schema Registry enforces compatibility rules (backward, forward, full)

**Backward compatible change** (old consumers can read new data):
- Adding a nullable column (default null)

**Breaking change** (requires consumer coordination):
- Renaming a column, changing a column type, removing a non-nullable column

### 2. Event Upcasting (see `04-advanced-topics/event-driven-architecture.md`)

For event-sourced systems: transform old event shapes to new shapes at read time using version-aware upcasters.

---

## Operational Concerns

| Concern | Detail |
|---|---|
| **WAL disk fill** | Monitor replication slot lag. Set `max_slot_wal_keep_size` to cap WAL retention |
| **Initial snapshot** | Debezium snapshots large tables under `REPEATABLE READ` — holds locks briefly |
| **At-least-once delivery** | Debezium guarantees at-least-once; consumers must be idempotent |
| **Connector restart** | Offsets stored in Kafka; restart resumes from last committed offset |
| **Topic compaction** | For CDC topics used as change log: use `cleanup.policy=delete` (not compact) |
| **Tombstone events** | Debezium emits `null` value for deleted keys — required for Kafka log compaction |

---

## CDC vs Dual Write vs Outbox

| Approach | Consistency | Complexity | DB Load | Latency |
|---|---|---|---|---|
| **Dual Write** | ❌ No atomicity | Low | Low | Low |
| **Polling (query-based)** | ⚠️ Misses deletes | Low | High | High (poll interval) |
| **Outbox (polling)** | ✅ Atomic | Medium | Medium | Medium |
| **CDC (log-based)** | ✅ Atomic | High | Very Low | Very Low (~ms) |
| **Trigger-based** | ✅ Atomic | Medium | High | Low |

---

## Interview Deep-Dive Questions

1. **How does Debezium guarantee at-least-once delivery? Why not exactly-once?**
   Debezium commits its WAL offset (LSN) to Kafka `connect-offsets` after writing events to Kafka. If the Kafka write succeeds but the offset commit fails, on restart it will re-read and re-publish those events. Exactly-once requires Kafka transactions plus idempotent producers — Debezium supports this in newer versions with `exactly.once.support = enabled`, but it requires Kafka 2.6+ and adds overhead. In practice, consumers are designed to be idempotent (upsert by primary key) rather than relying on exactly-once at the transport layer.

2. **A PostgreSQL replication slot has 50GB of retained WAL because the CDC consumer was down for 6 hours. How do you recover?**
   First, determine if you need the retained history:
   - If yes: restart the consumer and let it drain (monitor disk usage, ensure enough headroom)
   - If no (e.g., consumer will start fresh): drop and recreate the replication slot
   
   The immediate danger is disk exhaustion. Set `max_slot_wal_keep_size = 10GB` in `postgresql.conf` to cap retention — PostgreSQL will drop the slot (and the retained WAL) if it would exceed this. Monitor with `SELECT slot_name, pg_size_pretty(pg_current_wal_lsn() - confirmed_flush_lsn) FROM pg_replication_slots`.

3. **Why is log-based CDC preferred over polling for an event sourcing system?**
   Event sourcing requires capturing every change to reconstruct state by replaying events. Polling misses intermediate states (two updates between polls produce only one event). Log-based CDC reads from the WAL where every committed write is recorded individually — no events are dropped. Additionally, CDC captures true ordering (via LSN), while polling queries can have ordering ambiguity with concurrent writes.

---

## See Also

- `09-patterns/outbox-pattern.md` — CDC-based outbox pattern vs polling
- `04-advanced-topics/event-driven-architecture.md` — CQRS, Event Sourcing, event upcasting
- `01-foundations/databases.md` — WAL internals, MVCC, PostgreSQL replication
- `04-advanced-topics/internals/kafka-internals.md` — Kafka as CDC event backbone
