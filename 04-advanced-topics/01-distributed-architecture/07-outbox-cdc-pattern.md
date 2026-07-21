---
module: 04-advanced-topics
topic: distributed-architecture
status: unread
tags: [04-advanced-topics, distributed-systems, outbox, cdc, debezium, event-driven]
---
# Transactional Outbox and Change Data Capture (CDC)

These two patterns solve the same problem from different directions: how do you reliably publish an event to a message broker at the same time as you write to a database, without introducing dual-write bugs?

---

## The Dual-Write Problem

**Naive approach**: When processing an order, write to PostgreSQL and then publish to Kafka.

```java
// WRONG — classic dual-write anti-pattern
db.execute("INSERT INTO orders VALUES (...)")
kafka.publish("order-created", event)  // if this fails, event is lost
```

**What goes wrong:**
1. DB write succeeds, Kafka publish fails → downstream services never hear about the order
2. DB write succeeds, Kafka publish succeeds, process crashes before ack → event published twice (unless Kafka exactly-once is configured end-to-end)
3. DB write fails, but Kafka publish already sent → event published for a non-existent order

There's no way to make both operations atomic across two separate systems without distributed transactions. XA/2PC across DB and Kafka is possible but adds latency, reduces throughput, and most Kafka clients don't support it correctly.

---

## Pattern 1: Transactional Outbox

**Idea**: Instead of publishing to Kafka directly, write the event to an `outbox` table in the same DB transaction as the business write. A separate relay process reads from the outbox and publishes to Kafka.

### Schema

```sql
-- Business table
CREATE TABLE orders (
  order_id  UUID PRIMARY KEY,
  user_id   UUID NOT NULL,
  total     NUMERIC(10,2),
  status    VARCHAR(20),
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Outbox table (same database)
CREATE TABLE outbox (
  id           BIGSERIAL PRIMARY KEY,
  aggregate_id UUID NOT NULL,          -- order_id, user_id, etc.
  event_type   VARCHAR(100) NOT NULL,  -- "order.created", "payment.completed"
  payload      JSONB NOT NULL,
  published    BOOLEAN DEFAULT FALSE,
  created_at   TIMESTAMPTZ DEFAULT now()
);
```

### Application Code

```java
// CORRECT — atomic outbox write
@Transactional
public void createOrder(Order order) {
    orderRepo.save(order);
    outboxRepo.save(OutboxEvent.builder()
        .aggregateId(order.getOrderId())
        .eventType("order.created")
        .payload(toJson(order))
        .build());
    // Both writes commit together or both roll back
}
```

### Relay Process (Message Relay)

```java
// Runs as a background thread or separate service
@Scheduled(fixedDelay = 100) // every 100ms
public void publishPendingEvents() {
    List<OutboxEvent> pending = outboxRepo.findByPublishedFalse(limit = 100);
    for (OutboxEvent event : pending) {
        try {
            kafka.publish(event.getEventType(), event.getPayload());
            outboxRepo.markPublished(event.getId());
        } catch (KafkaException e) {
            // retry on next poll — event stays in outbox
        }
    }
}
```

**At-least-once guarantee**: If the relay crashes between publish and `markPublished`, the event is published again on restart. Consumers must be idempotent (use `event_id` for deduplication).

### Outbox Cleanup

Without cleanup, the outbox table grows forever:

```sql
-- Delete events published more than 7 days ago
DELETE FROM outbox WHERE published = TRUE AND created_at < now() - INTERVAL '7 days';
```

Run as a nightly job or pg_cron. Keep 7 days for debugging/audit purposes.

### Advantages

- Simple to implement with any DB that supports transactions
- No new infrastructure beyond what you already have (Postgres + Kafka)
- Works with any ORM/framework

### Disadvantages

- Relay process adds latency: events published ~100ms–1s after commit (not sub-millisecond)
- Polling the outbox at high frequency adds DB load
- Doesn't scale elegantly: many services polling their own outbox tables can add up

---

## Pattern 2: Change Data Capture (CDC) with Debezium

**Idea**: Instead of application code writing to the outbox, read the database's replication log (WAL in PostgreSQL, binlog in MySQL) and convert row changes into events.

Every write to the DB is already recorded in the transaction log for replication purposes. CDC taps this stream directly.

### Architecture

```
PostgreSQL WAL (Write-Ahead Log)
        │
        ▼
Debezium Connector (runs in Kafka Connect)
        │  reads logical replication slot
        │  converts INSERT/UPDATE/DELETE to events
        ▼
Kafka Topic: dbserver1.public.orders
{
  "op": "c",  // c=create, u=update, d=delete
  "before": null,
  "after": {
    "order_id": "abc-123",
    "user_id": "u-456",
    "total": 99.99,
    "status": "created"
  },
  "source": { "ts_ms": 1735689600000, "db": "shop", "table": "orders" }
}
        │
        ▼
Downstream consumers (inventory service, email service, analytics)
```

### PostgreSQL Setup for CDC

```sql
-- Enable logical replication (requires postgresql.conf: wal_level = logical)
CREATE PUBLICATION debezium_pub FOR TABLE orders, payments, users;

-- Debezium creates a replication slot automatically:
-- SELECT * FROM pg_replication_slots;
-- slot_name: debezium, plugin: pgoutput
```

### Debezium Connector Configuration

```json
{
  "name": "orders-connector",
  "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
  "database.hostname": "postgres",
  "database.port": "5432",
  "database.user": "debezium",
  "database.password": "dbz",
  "database.dbname": "shop",
  "slot.name": "debezium_orders",
  "plugin.name": "pgoutput",
  "table.include.list": "public.orders",
  "topic.prefix": "dbserver1",
  "transforms": "unwrap",
  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false"
}
```

### Why CDC is Powerful

**No application code changes**: Legacy systems emit events without modification. You're reading the same WAL that PostgreSQL streaming replication already uses.

**Capture all changes including non-application writes**: DBA runs `UPDATE orders SET status = 'fraud' WHERE ...` directly in SQL? CDC captures it. The outbox pattern would miss this entirely.

**Sub-second latency**: Debezium reads the WAL as it's written. Events arrive in Kafka within milliseconds of the commit, not 100ms–1s polling interval.

**Exactly-once on read**: Since the WAL is an ordered log with LSN (Log Sequence Number), Debezium tracks its position (stored in Kafka's offset system). On restart, it resumes from exactly where it left off — no events skipped, no events duplicated.

---

## Outbox + CDC Combined

The most robust pattern combines both:

1. Application writes to `orders` + `outbox` in one transaction (outbox pattern)
2. Debezium CDC reads the `outbox` table from the WAL and publishes to Kafka

This gives you:
- **Atomicity**: application code writes both atomically (no dual-write)
- **Low latency**: CDC is sub-second, not 100ms polling
- **No polling DB load**: no relay process querying the DB
- **Structured events**: outbox payload is explicit (not raw row diff)

```
Application  ─── transaction ──▶  orders table  +  outbox table
                                                        │
                                         Debezium CDC reads WAL
                                                        │
                                                        ▼
                                                  Kafka Topic
```

The `outbox.published` column is no longer needed. Debezium reads the outbox rows on INSERT and publishes them. Cleanup is still needed to avoid infinite table growth.

---

## Comparison

| | Dual Write | Outbox (Polling Relay) | CDC (Debezium) | Outbox + CDC |
|---|---|---|---|---|
| **Atomicity** | None | Yes | N/A (reads existing tables) | Yes |
| **Latency** | Sub-ms | 100ms–1s (polling) | Sub-second | Sub-second |
| **App code changes** | Minimal | Yes (write to outbox) | None | Yes (write to outbox) |
| **DB load** | None | Polling adds load | WAL read (negligible) | WAL read only |
| **Infrastructure** | None | Relay process | Kafka Connect + Debezium | Kafka Connect + Debezium |
| **Exactly-once** | No | At-least-once | At-least-once | At-least-once |
| **Failure handling** | Broken | Robust | Robust | Robust |

---

## When to Use Each Pattern

**Outbox (polling relay)**: Small teams, existing Postgres, moderate event volume (< 10K/sec), team doesn't want Kafka Connect infrastructure. Simple to reason about, easy to debug.

**Pure CDC**: Reading existing tables you don't control (third-party app, legacy system). Need to react to all DB changes including those not made through the application. DBA-initiated changes must be captured.

**Outbox + CDC**: High event volume (> 10K/sec), sub-second latency required, Kafka Connect already in infrastructure. Best-of-both-worlds for greenfield systems.

---

## Pitfalls and Production Concerns

### PostgreSQL Replication Slot Lag

Debezium holds a replication slot. If Debezium falls behind (Kafka Connect is slow, consumer is down), PostgreSQL **cannot reclaim WAL segments** that Debezium hasn't read yet. The WAL grows unboundedly until disk fills.

```sql
-- Monitor replication slot lag
SELECT slot_name, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes
FROM pg_replication_slots;
```

Set `max_slot_wal_keep_size` in `postgresql.conf` to auto-invalidate a stuck slot before disk fills. Operational runbook: alert if lag exceeds 10 GB; page if it exceeds 50 GB.

### Schema Evolution

Debezium includes the schema in each event envelope (Avro with Schema Registry, or JSON). If you rename a column, downstream consumers that parse the event by field name break. Use Schema Registry's compatibility checks (BACKWARD or FULL compatibility) to enforce that schema changes don't break consumers.

### Tombstone Events (Deletes)

When a row is deleted, Debezium emits two Kafka messages: the delete event, then a `null` payload "tombstone." The tombstone is used for Kafka log compaction. Consumers must handle `null` payloads without crashing.

### Outbox Ordering

If multiple rows in the outbox table belong to the same aggregate and are processed by concurrent relay threads, events may publish out of order. Fix: route events by `aggregate_id` to the same Kafka partition (partitioned by aggregate_id). Order is then guaranteed within a partition.

---

## Interview Questions to Practice

1. **"Your service must write to PostgreSQL and publish an event to Kafka. How do you ensure both happen atomically?"**
   *Use the transactional outbox pattern: write the event to an `outbox` table in the same DB transaction. A relay process (or Debezium CDC) publishes from the outbox to Kafka separately. This gives atomicity via the DB transaction — the event is either in both the DB and Kafka, or in neither. The trade-off is at-least-once delivery: if the relay crashes after publishing but before deleting/marking the outbox row, the event publishes again. Consumers must be idempotent using an event_id for deduplication.*

2. **"What is CDC and how does Debezium work?"**
   *CDC is the process of capturing every INSERT/UPDATE/DELETE from a database's transaction log and publishing them as events. Debezium is a Kafka Connect connector that reads PostgreSQL's Write-Ahead Log (WAL) via a logical replication slot. It converts WAL records into structured Kafka messages. Benefits: no application code changes, sub-second latency, captures all writes including DBA-initiated ones. The main operational risk is replication slot lag — if Debezium falls behind, PostgreSQL can't reclaim WAL disk space.*

3. **"Why is dual write dangerous even if you retry the Kafka publish?"**
   *Retrying only solves transient failures. If the DB write succeeds but the application process crashes before the Kafka publish, no retry ever happens — the event is permanently lost. If you retry after a timeout but the first publish did go through (slow network, not a real failure), you send a duplicate event. Without a distributed transaction, there's no way to know whether the publish succeeded or not. The outbox pattern avoids this by making "publish intent" part of the DB transaction — you can always know whether the event needs to be published by checking the outbox.*

---

## Applied In

This concept is used by **1 problem** in this repo:

**High-Level Design**

- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md)

