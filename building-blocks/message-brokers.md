# Message Brokers

> **Middleware that enables asynchronous messaging between services via queues or pub/sub.**

---

## 1. Concept Overview

A **message broker** accepts messages from producers and delivers them to consumers. It decouples producers from consumers (in time and in topology) and can provide persistence, ordering, and at-least-once or exactly-once semantics.

**Why it exists**: Systems need to decouple components, absorb load spikes, and process work asynchronously (e.g. sending emails, updating caches, analytics) without blocking the request path.

---

## 2. Core Principles

### Queue vs Pub/Sub

| Model | Delivery | Use case |
|-------|----------|----------|
| **Queue** | Each message to one consumer (competing consumers) | Task queues, job processing |
| **Pub/Sub** | Each message to all subscribers | Events, fan-out (e.g. order created → inventory, email, analytics) |

### Delivery guarantees

| Guarantee | Meaning | How |
|-----------|---------|-----|
| **At-most-once** | May lose messages | Fire and forget; no ack |
| **At-least-once** | No loss; may duplicate | Producer retries; consumer acks after process; replay on crash |
| **Exactly-once** | No loss, no duplicate | Idempotent consumers + dedup or transactional outbox + broker support |

### Architecture (simplified)

```
  Producers ─────▶ Message Broker (Kafka / RabbitMQ / SQS)
                        │
                        ├──▶ Consumer Group A (queue semantics)
                        └──▶ Consumer Group B (another subscription)
```

---

## 3. Real-World Usage

- **Kafka**: High-throughput log; topics and partitions; replay; used for event streaming, logs, metrics.
- **RabbitMQ**: Queues, exchanges, flexible routing; used for task queues, RPC patterns.
- **AWS SQS**: Managed queue; at-least-once; simple; good for decoupling AWS services.
- **Google Pub/Sub**: Managed pub/sub; at-least-once; global.

---

## 4. Trade-offs

| Broker | Ordering | Throughput | Complexity | Best for |
|--------|----------|------------|------------|----------|
| **Kafka** | Per partition | Very high | Higher | Event streaming, log aggregation, high volume |
| **RabbitMQ** | Per queue | High | Medium | Task queues, complex routing |
| **SQS** | Standard: best-effort; FIFO: per group | High | Low | Decoupling, serverless, AWS-native |

**When to use**: Async processing, decoupling, load leveling, event-driven architecture.  
**When not**: Synchronous request-response only; or when you need strong consistency in one request (prefer DB or sync call).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Broker down | Replication; multi-AZ; failover; producers/consumers retry |
| Consumer crash before ack | At-least-once: redelivery; make consumer idempotent |
| Poison message | DLQ after N failures; alert; fix and replay or discard |
| Backlog growth | Scale consumers; backpressure; prioritize critical topics |
| Partition imbalance (Kafka) | Key choice; rebalance; more partitions |

---

## 6. Performance Considerations

- **Throughput**: Kafka and similar can do millions of msg/s with partitioning and batching.
- **Latency**: Trade-off between batching (higher throughput, higher latency) and immediate send (lower latency, lower throughput).
- **Persistence**: Disk vs memory; replication factor; affects durability and cost.

---

## 7. Implementation Patterns

- **Task queue**: One queue, N workers; at-least-once; idempotent handlers.
- **Event streaming**: Kafka-style; multiple consumers; replay; partition by key for ordering.
- **Outbox pattern**: Write to DB + outbox table in same transaction; separate process publishes to broker (avoids losing messages when producer crashes after send but before DB commit).

---

## Quick Revision

- **Queue**: One consumer per message; **Pub/Sub**: fan-out to many subscribers.
- **At-least-once**: Common; requires idempotent consumers. **Exactly-once**: Idempotency + dedup or transactional outbox.
- **Kafka**: Log, partitions, replay, high throughput. **RabbitMQ**: Flexible routing, task queues. **SQS**: Simple, managed.
- **Failure**: Replication, DLQ for poison messages, scale consumers for backlog.
- **Interview**: “We use a message queue so the API can respond immediately and workers process notifications asynchronously; we design consumers to be idempotent for at-least-once delivery.”
