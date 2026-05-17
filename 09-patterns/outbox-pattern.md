# The Outbox Pattern

> **Solving the dual-write problem: atomically writing to a database AND publishing an event.**

---

## The Problem

You need to do two things:
1. Write a record to your database (e.g. save an Order)
2. Publish an event to a message broker (e.g. publish `OrderCreated` to Kafka)

**These cannot be made atomic in a single transaction — the database and Kafka are separate systems.**

### Why This Fails Without Outbox

```
Scenario A: Write first, then publish
  1. INSERT INTO orders (...) ← success
  2. kafka.produce("order_created") ← CRASH

Result: Order saved in DB. Kafka never gets the event.
Downstream services (notification, inventory, analytics) never know about the order.

Scenario B: Publish first, then write
  1. kafka.produce("order_created") ← success
  2. INSERT INTO orders (...) ← CRASH

Result: Kafka gets the event. DB has no order.
Consumers process an order that doesn't exist in the DB.

Both scenarios leave the system in an inconsistent state.
```

---

## The Solution: Transactional Outbox

**Core idea**: Write the event to a table in the **same database** as your business data. Both writes happen in one ACID transaction. A separate relay process reads from that table and publishes to Kafka.

```
┌─────────────────────────────────────────────────┐
│           SINGLE DATABASE TRANSACTION            │
│                                                  │
│  INSERT INTO orders (order_id, user_id, ...)    │
│  INSERT INTO outbox  (event_type, payload, ...)  │
│                                                  │
│  Either both commit, or both rollback            │
└─────────────────────────────────────────────────┘
                        │
                        │ (separate process)
                        ▼
         ┌─────────────────────────────┐
         │  Outbox Relay / CDC         │
         │  (Debezium / custom poller) │
         │                             │
         │  READ unprocessed outbox    │
         │  PUBLISH to Kafka           │
         │  MARK outbox row as sent    │
         └─────────────────────────────┘
                        │
                        ▼
                    KAFKA TOPIC
```

---

## Implementation

### Database Schema

```sql
-- Business table
CREATE TABLE orders (
    order_id    UUID PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    status      VARCHAR(20),
    total       DECIMAL(10, 2),
    created_at  TIMESTAMP
);

-- Outbox table (same database)
CREATE TABLE outbox_events (
    event_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type  VARCHAR(100) NOT NULL,      -- 'OrderCreated', 'PaymentProcessed'
    aggregate_id VARCHAR(64) NOT NULL,      -- order_id, user_id, etc.
    payload     JSONB NOT NULL,             -- Full event data
    status      VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    created_at  TIMESTAMP DEFAULT NOW(),
    published_at TIMESTAMP,
    INDEX idx_outbox_pending (status, created_at) WHERE status = 'PENDING'
);
```

### Application Code (Spring/Java)

```java
@Service
@Transactional
public class OrderService {

    public Order createOrder(CreateOrderRequest request) {
        // Step 1: Save order
        Order order = new Order(request);
        orderRepository.save(order);

        // Step 2: Write event to outbox (SAME TRANSACTION)
        OutboxEvent event = OutboxEvent.builder()
            .eventType("OrderCreated")
            .aggregateId(order.getOrderId())
            .payload(objectMapper.writeValueAsString(new OrderCreatedEvent(order)))
            .build();
        outboxRepository.save(event);

        // Both committed together or neither — atomic!
        return order;
        // DO NOT call kafka.produce() here
    }
}
```

### Outbox Relay (Polling)

```java
@Scheduled(fixedDelay = 100)  // Run every 100ms
public void publishPendingEvents() {
    List<OutboxEvent> pending = outboxRepository.findPendingEvents(limit = 100);
    for (OutboxEvent event : pending) {
        try {
            kafkaTemplate.send(
                topicFor(event.getEventType()),
                event.getAggregateId(),   // Partition key (ensures ordering per entity)
                event.getPayload()
            );
            outboxRepository.markPublished(event.getEventId());
        } catch (Exception e) {
            outboxRepository.markFailed(event.getEventId());
            log.error("Failed to publish event {}", event.getEventId(), e);
        }
    }
}
```

---

## CDC-Based Relay (Production Preferred)

Polling the outbox table adds load and latency. Production systems use **Change Data Capture (CDC)** instead.

**Debezium** reads the database's WAL (transaction log) and streams changes to Kafka without polling:

```yaml
# Debezium PostgreSQL Connector config
{
  "name": "outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres-primary",
    "database.dbname": "production",
    "table.include.list": "public.outbox_events",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.route.by.field": "aggregate_id"
  }
}
```

CDC advantages:
- No polling → lower DB load, lower latency (sub-second)
- Reads WAL before it's discarded → works even if app is down during event creation
- Debezium handles exactly-once publishing (Kafka topic per outbox row)

---

## At-Least-Once Delivery

The outbox relay guarantees **at-least-once** delivery — if the relay crashes after publishing but before marking the event as `PUBLISHED`, it will re-publish on restart.

**Consumers MUST be idempotent:**

```java
@KafkaListener(topics = "order-created")
public void handleOrderCreated(OrderCreatedEvent event) {
    String idempotencyKey = event.getEventId();  // UUID from outbox

    // Skip if already processed
    if (processedEventRepository.existsById(idempotencyKey)) {
        return;  // Duplicate — safe to skip
    }

    // Process
    inventoryService.reserve(event.getOrderId(), event.getItems());

    // Mark as processed
    processedEventRepository.save(new ProcessedEvent(idempotencyKey));
}
```

---

## Outbox Cleanup

```sql
-- Archive published events older than 7 days
DELETE FROM outbox_events
WHERE status = 'PUBLISHED' AND published_at < NOW() - INTERVAL '7 days';

-- Run daily or use pg_partman to partition by day + drop old partitions
```

---

## Trade-offs

| Approach | Pros | Cons |
|----------|------|------|
| **Dual-write (no outbox)** | Simple code | Non-atomic → data loss risk |
| **Outbox + polling** | Atomic, no special infra | Added DB load, slight latency |
| **Outbox + CDC (Debezium)** | Atomic, low latency, low load | Extra infrastructure (Debezium) |
| **2-Phase Commit (XA)** | Distributed atomic | Not supported by Kafka, slow |

**Recommendation**: Use Outbox + Debezium CDC in production. Use Outbox + polling for simpler setups.

---

## Interview Talking Points

**Q: "How do you ensure a payment event is published to Kafka exactly once?"**
> "The outbox pattern. When we process a payment, we write the payment record AND a payment_processed event to the outbox table in the same database transaction — atomic by ACID. A Debezium CDC connector tails the database WAL and streams outbox rows to Kafka. If the publisher crashes mid-publish, it re-reads and re-publishes. Consumers deduplicate by event_id (UUID in the outbox). This gives us at-least-once delivery with idempotent consumers — effectively exactly-once semantics end-to-end."

**Q: "Why not just use Kafka transactions?"**
> "Kafka transactions can't span a database commit and a Kafka produce atomically. You'd need XA (distributed 2PC), which Kafka doesn't support. The outbox pattern is the standard solution in the industry precisely because it avoids any cross-system distributed transaction."
