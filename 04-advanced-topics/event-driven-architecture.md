# Event-Driven Architecture

## What Is EDA?

Event-Driven Architecture (EDA) is a design paradigm where components communicate by producing and consuming events rather than calling each other directly.

**Analogy:** A sports stadium scoreboard. Every time a goal is scored (an event), the scoreboard system broadcasts it. Different consumers react differently to the same event: the live score display updates, the analytics system records possession stats, the replay system queues the clip, the mobile app sends push notifications. None of these consumers talk to each other. They all independently react to the same broadcast event. Adding a new consumer (e.g., a betting odds service) doesn't require changing the scorer or any other consumer.

**Contrast with request/response:** In REST/RPC, Service A calls Service B directly. A knows B exists, knows B's address, and waits for B's response. In EDA, A publishes an event to a broker. A doesn't know who cares — or if anyone does. B, C, and D independently subscribe and react.

---

## Event Types

### Domain Events

Something meaningful that happened within a bounded context.

- `OrderPlaced`, `PaymentCompleted`, `UserRegistered`, `InventoryDepleted`
- Described in past tense — they record facts, not intentions
- Owned by the service that produced them
- Consumed by other services that care

### Integration Events

Domain events that cross bounded context boundaries. They're the contract between services.

- Serialization format must be stable (versioned Avro/Protobuf schemas)
- A change to an integration event is a breaking change for consumers
- Treat them like a public API

### Commands

A request to do something. Different from events — a command has one intended recipient and expects an outcome.

- `ProcessPayment`, `SendEmail`, `ReserveInventory`
- Commands can fail; events are facts that already happened
- Use commands for task-queue style work (RabbitMQ fits well here)
- Use events for broadcasting facts (Kafka fits well here)

---

## Core Patterns

### Event Sourcing

Instead of storing current state in a DB row, store the sequence of events that led to that state.

```
Traditional: orders table row { id: 123, status: "shipped", total: 99.00 }

Event Sourcing: events store
  { orderId: 123, event: "OrderPlaced",   amount: 99.00, at: T1 }
  { orderId: 123, event: "PaymentTaken",  amount: 99.00, at: T2 }
  { orderId: 123, event: "OrderShipped",  trackingId: "X", at: T3 }
```

Current state is derived by replaying events. You get a complete audit log for free.

**Benefits:**
- Full audit trail — know exactly what happened and when
- Replay events to rebuild state or populate new projections
- Temporal queries: "what was the state of order 123 at 2pm yesterday?"
- Natural fit with CQRS

**Costs:**
- Querying current state requires replaying events (mitigated with snapshots)
- Event schema changes are hard — old events must still be interpretable
- Increased complexity — needs an event store (EventStoreDB, Kafka, or custom on Postgres)

**Snapshot optimization:** after N events, store a snapshot of current state. Replay only events since the last snapshot.

### CQRS (Command Query Responsibility Segregation)

Separate the write model (commands) from the read model (queries).

```
Write side:   HTTP POST /orders → Command Handler → Events → Event Store
                                                            ↓
Read side:    Event Processor reads events → updates Read DB (denormalized, optimized for queries)
              HTTP GET /orders → Query Handler → Read DB
```

**Why:** the shape of data you write is often different from what you read. An order is created from many fields, but the order list view needs joined data from users, products, and inventory. Maintaining a separate read model lets you optimize each independently.

**CQRS + Event Sourcing:** natural combination. Events flow from the write side to build the read model. The read model is a derived projection, always rebuildable from the event log.

**When to use CQRS:**
- Complex domains with heavy read optimization needs
- Read and write load are asymmetric (read-heavy systems)
- Multiple read models needed (mobile vs. web vs. analytics)

**When NOT to use:** simple CRUD — CQRS adds complexity without benefit.

### Outbox Pattern

How do you atomically update the DB and publish an event? Two-phase commit? No.

**Problem:**
```
BEGIN TRANSACTION
  UPDATE orders SET status = 'placed'
  publish to Kafka  ← this can fail after DB commits, or DB can fail after Kafka publishes
COMMIT
```

**Solution — Outbox:**
```
BEGIN TRANSACTION
  UPDATE orders SET status = 'placed'
  INSERT INTO outbox (event_type, payload) VALUES ('OrderPlaced', '{...}')
COMMIT
-- separate process (CDC or polling relay) reads outbox table and publishes to Kafka
-- marks outbox records as published
```

The outbox table lives in the same DB as your domain data. The transaction is local (no distributed transaction). A relay process (Debezium CDC or polling relay) reads the outbox and publishes to the broker.

**At-least-once delivery:** the relay might publish the same event twice (if it crashes mid-delivery). Consumers must be idempotent (see below).

---

## Message Brokers: Kafka vs RabbitMQ vs SQS

| Feature | Kafka | RabbitMQ | AWS SQS |
|---------|-------|----------|---------|
| Model | Log-based, pull | Queue-based, push | Queue-based, pull |
| Retention | Configurable (days/weeks) | Until consumed | 4 days (default), up to 14 |
| Consumer groups | Multiple independent groups, each reads full log | Competing consumers, each message consumed once | Competing consumers |
| Ordering | Ordered within partition | Ordered within queue (single consumer) | Best-effort (FIFO queue for strict ordering) |
| Throughput | Very high (millions/sec) | High (tens of thousands/sec) | High (auto-scales) |
| Replay | Yes — seek to any offset | No — messages gone after consume | No |
| Complexity | High — brokers, partitions, consumer lag | Moderate | Low — fully managed |
| Use case | Event streaming, audit log, fan-out | Task queues, routing, RPC | AWS-native task queues |

**Decision guide:**
- Need replay, fan-out, or event sourcing → **Kafka**
- Need complex routing, priorities, dead-letter queues → **RabbitMQ**
- AWS shop, need managed simplicity → **SQS**

---

## At-Least-Once Delivery and Idempotency

Most brokers guarantee at-least-once delivery — a message might be delivered more than once (broker restarts, consumer crashes before ACK).

**Consequence:** your consumer must be idempotent — processing the same message twice must produce the same result as processing it once.

**Strategies:**

**1. Idempotency key in the database**
```sql
INSERT INTO payments (idempotency_key, order_id, amount, status)
VALUES ('pay_abc123', 456, 99.00, 'completed')
ON CONFLICT (idempotency_key) DO NOTHING;
```

**2. Natural idempotency**
Some operations are inherently idempotent: `SET status = 'shipped'` — doing it twice doesn't change anything.
Non-idempotent: `INSERT INTO charges (amount) VALUES (99.00)` — each call adds a new charge.

**3. Deduplication table**
```
consumer processes event → checks dedup table for event_id
  → if already processed: skip
  → if not: process + insert event_id + commit (in one transaction)
```

**4. Exactly-once semantics (Kafka)**
Kafka transactions + idempotent producers can achieve exactly-once within a Kafka-to-Kafka pipeline. Does not extend to external systems (DB, APIs) — you still need idempotency there.

---

## Event Schema Evolution

Integration events are contracts. They change. Manage this carefully.

**Backward compatibility:** new schema can read old messages.
- Add new optional fields with defaults — old producers that don't send the field still work.

**Forward compatibility:** old schema can read new messages.
- Old consumers that don't understand new fields ignore them.
- Never remove or rename fields in a published schema.

**Schema registry (Confluent Schema Registry with Avro/Protobuf):**
```
Producer → Schema Registry (register/validate schema) → Kafka
Consumer → Schema Registry (fetch schema by ID) → deserialize
```

The schema registry stores versioned schemas. Compatibility checks happen on schema registration, not at runtime.

**Versioning strategies:**
- **Additive only** — never break, just add fields (simplest)
- **Version in topic name** — `orders.v1`, `orders.v2` (run old + new consumers in parallel during migration)
- **Version field in payload** — consumers branch on version

**Rule:** changing an integration event schema is as significant as changing a public API. Treat it accordingly.

---

## Trade-offs

### Eventual Consistency

EDA is inherently eventually consistent. After an event is published, consumers may process it milliseconds or seconds later. During that window, different services have different views of reality.

- **Accept it:** most business operations can tolerate a few seconds of inconsistency
- **Communicate it:** UI shows "your order is being processed" not "order confirmed" until confirmed
- **Detect violations:** set up alerts when consumers fall too far behind (consumer lag monitoring in Kafka)

### Debugging Complexity

In REST: request comes in → trace the call stack → find the bug.

In EDA: event published → 5 different consumers → one fails 30 seconds later → which one? What was the event payload? Was the event even published?

**Mitigations:**
- Distributed tracing with trace IDs propagated through events (include `traceId` in event payload)
- Centralized log aggregation (every consumer logs event ID, event type, outcome)
- Dead letter queues (DLQ) — failed events land here for inspection and replay
- Event replay capability — reprocess events from a time range to reproduce bugs

### Event Ordering

Kafka guarantees ordering within a partition. Events for the same entity (e.g., same order ID) must go to the same partition.

```java
// Use order ID as partition key → same order's events always go to same partition
producer.send(new ProducerRecord<>("orders", orderId, eventPayload));
```

Cross-partition ordering is not guaranteed. Design your consumers to handle out-of-order events (sequence numbers, timestamps, idempotency).

### Versioning and Schema Management

The broker retains events for days/weeks. Old and new consumers may coexist. Every schema change must be backward and forward compatible. This discipline is easy to neglect and expensive to fix after the fact.

### Consumer Lag

Consumers falling behind is a silent failure mode. Monitor `consumer_lag` metrics in Kafka. Set alerts when lag exceeds acceptable thresholds.

---

## When to Use EDA

**Good fit:**
- Fan-out: one event → many independent consumers
- Decoupling services that shouldn't know about each other
- Audit trails: every state change is recorded as an event
- Async workflows where the caller doesn't need immediate confirmation
- Handling traffic spikes by buffering (Kafka absorbs bursts, consumers process at their pace)

**Poor fit:**
- Real-time request/response where the caller needs an immediate result
- Simple CRUD with no downstream reactions
- Small systems where the operational overhead isn't justified

---

## Interview Talking Points

**"How would you ensure exactly-once processing in your system?"**
- Kafka exactly-once semantics handle Kafka-to-Kafka, but for Kafka-to-DB you need idempotent consumers
- Use an idempotency key (event ID) stored alongside the processed data in the same transaction
- Natural idempotency where possible (upserts, set operations)

**"How do you handle a consumer that's failing?"**
- Dead letter queue: after N retries, move the message to DLQ for inspection
- Alert on DLQ depth
- Fix the bug, replay from DLQ (or replay from Kafka offset for Kafka consumers)

**"What's the difference between event sourcing and EDA?"**
- EDA is about communication between services via events
- Event sourcing is about persistence — storing state as a log of events
- You can use EDA without event sourcing and vice versa; they're complementary but independent

**"How do you maintain consistency across services?"**
- Accept eventual consistency — design UIs and business processes around it
- Saga pattern for multi-step workflows with compensating transactions
- Outbox pattern for atomic DB + event publish

**"How do you debug a production issue in an event-driven system?"**
- Trace ID propagated through event headers — query your tracing system (Jaeger) by trace ID
- Query the event log (Kafka topic or event store) for the event in question
- Check DLQ for failed processing attempts
- Replay the event against a debug environment
