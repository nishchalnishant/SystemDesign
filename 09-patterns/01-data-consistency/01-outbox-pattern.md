---
module: 09-patterns
status: unread
tags: [09-patterns, system-design, patterns]
---
# The Outbox Pattern

> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to reliably send messages to other parts of your system immediately after updating your own database, solving the "Dual-Write Problem."
>
> **Key concepts:**
> - **The Dual-Write Problem:** If you update your database and then send a message, the messaging system might be down. If you send the message first, your database might crash. You get stuck in an inconsistent state.
> - **The Outbox Table:** An extra table (or "folder") in the *same* database as your main data.
> - **Transactional Guarantee:** You update your main data AND drop a message into the Outbox table in the *exact same database save*. It is all-or-nothing (atomic).
> - **Message Relay:** A separate background worker (like Debezium) constantly checks the Outbox table and safely forwards those messages to the rest of the system.
>
> **Key takeaway:** The Outbox Pattern is the gold standard for making sure a microservice reliably tells the rest of the system what it just did, without ever dropping a message.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are building a payment system. A user clicks "Pay". 
1. Your code saves a `PaymentCompleted` record to your PostgreSQL database.
2. Then, your code sends a `payment_success` message to Kafka, so the Order Service knows to ship the item.

But what if, on a Tuesday morning, the Kafka system is briefly offline for 5 seconds?
Your database saves the payment. The user's credit card is charged. But the message to Kafka fails. The Order Service never hears about it. The user's order is stuck in "PENDING" forever. You have taken their money but not shipped their item.

If you flip the order (send the message first, then save to the database), it's even worse. The message fires, the Order Service ships the item, but then your database crashes. You just shipped a free laptop to a user and have no record of their payment.

This is called the **Dual-Write Problem**, and without understanding the Outbox Pattern, your microservices will constantly lose data.

---

## ✉️ The Mailroom Analogy

To understand the Outbox Pattern, imagine you work in an office and need to do two things:
1. Put a signed contract in your filing cabinet.
2. Mail a copy to your client.

If you mail it first and then trip and drop the original in a shredder, the client has it but you don't. If you file it first but the post office is closed, you have it but the client doesn't. 

**The Outbox Solution:**
Instead of doing two separate things, you open your filing cabinet. You put the original contract in the main folder. Then, you put the copy in a special folder called the **"Outbox."** Finally, you lock the cabinet. 
Because both went into the cabinet at the same time, it's impossible to lose one and keep the other. 

Later, a dedicated mailroom worker (the Relay Process) walks by, opens the Outbox folder, takes the copy, and mails it. If the post office is closed, the mailroom worker just holds onto the copy and tries again tomorrow. 

---

## Pattern Mindmap

```
Outbox Pattern
├── Core Problem
│   └── Dual-write: DB commit and message publish cannot be atomic across two systems
├── Key Components
│   ├── Outbox Table → same DB as business record; stores pending events transactionally
│   ├── Local ACID Transaction → writes business record + outbox row atomically
│   ├── Relay Process → reads outbox, publishes to broker (Kafka/SQS/RabbitMQ)
│   └── Idempotent Consumer → handles duplicate events on retry without side effects
├── Two Relay Approaches
│   ├── CDC (Debezium) → tails DB write-ahead log, low latency, no DB polling load
│   └── Polling Relay → SELECT unpublished rows, publish, mark done — simpler but adds DB load
├── When to Use
│   ├── ✓ Service must write to DB AND publish event in same logical operation
│   ├── ✓ At-least-once delivery guarantee required for downstream consumers
│   └── ✓ Cannot afford to lose events on broker downtime or network partition
├── When NOT to Use
│   ├── ✗ At-most-once delivery acceptable (fire-and-forget notifications)
│   └── ✗ DB does not support transactions (some NoSQL stores)
├── Trade-offs
│   ├── Pro: Exactly-one write + durable event; no lost messages on broker failure
│   ├── Pro: Retry is safe — event persisted in DB before any publish attempt
│   ├── Con: At-least-once delivery — consumers must be idempotent
│   └── Con: Additional outbox table + relay process to operate
├── Real-World Usage
│   ├── Debezium + Kafka → standard CDC-based outbox for Postgres/MySQL microservices
│   ├── Eventuate Tram → framework wrapping outbox pattern for Java services
│   └── Stripe → transactional event log ensures webhook delivery after payment write
└── Interview Angles
    ├── "How do you guarantee event delivery?" → outbox + relay, describe CDC vs polling
    ├── "What if relay crashes mid-publish?" → idempotent consumer handles duplicates
    └── "Why not wrap DB + Kafka in one transaction?" → no shared coordinator; 2PC problem
```

---

## The Solution: Transactional Outbox

**Core idea**: Write the event to a table in the **same database** as your business data. Both writes happen in one database save (an ACID transaction). A separate relay process reads from that table and publishes to Kafka.

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

Polling the outbox table constantly is like asking "Is there mail? Is there mail?" every second. It slows down the database. Production systems use **Change Data Capture (CDC)** instead.

**Debezium** is a popular CDC tool. It secretly reads the database's internal transaction log (WAL) and streams changes directly to Kafka without ever asking the database for data.

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
- Reads the internal log before it's thrown away → works even if your app crashes during event creation
- Debezium handles the complex math of publishing exactly-once.

---

## The Catch: "At-Least-Once" Delivery

The outbox relay guarantees **at-least-once** delivery. This means if the mailroom worker takes the mail to the post office, but gets amnesia before checking it off the list, they might send the same mail twice. 

Because of this, the systems receiving your messages **MUST be idempotent** (meaning they can receive the same message twice without doing the action twice).

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

Don't let your outbox grow forever! You need a script to throw away the mail that has already been sent.

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
| **Dual-write (no outbox)** | Simple code | Non-atomic → high data loss risk |
| **Outbox + polling** | Atomic, no special infrastructure | Added database load, slight delay |
| **Outbox + CDC (Debezium)** | Atomic, fast, low database load | Extra infrastructure to maintain (Debezium) |
| **2-Phase Commit (XA)** | Distributed atomic guarantee | Very slow, not supported by modern systems like Kafka |

**Recommendation**: Use Outbox + Debezium CDC in production. Use Outbox + polling for simpler setups or startups.

---

## 🎤 Interview Talking Points

**Q: "How do you ensure a payment event is published to Kafka exactly once?"**
> "I would use the Outbox Pattern. When we save a payment, we save a `payment_processed` event to an Outbox table in the exact same database transaction. This makes it impossible to lose. Then, a tool like Debezium reads the database logs and streams that outbox row to Kafka. If Debezium crashes mid-publish, it will try again, which gives us at-least-once delivery. We then make sure the downstream consumer checks the event's UUID to deduplicate it, giving us exactly-once semantics end-to-end."

**Q: "Why not just use Kafka transactions?"**
> "Kafka transactions can't span across a database and Kafka atomically. You would need a distributed coordinator (like Two-Phase Commit), which is notoriously slow and brittle, and Kafka doesn't support it for external databases anyway. The Outbox Pattern is the industry standard because it avoids cross-system transactions entirely."

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design an E-Commerce Platform (Amazon)](../../05-hld-problems/02-medium/e-commerce-platform.md)
- [Design a Notification Service](../../05-hld-problems/02-medium/notification-service.md)
- [Design a Payment System](../../05-hld-problems/03-hard/payment-system.md)

