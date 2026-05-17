# Message Brokers

> **Middleware that enables asynchronous messaging between services via queues or pub/sub.**

---

## The Restaurant Pass Window Analogy

Picture a busy restaurant on a Saturday night. The kitchen pass window is the single place where waiters drop order tickets and cooks pick them up. A waiter (producer) writes the order, clips it to the pass, and immediately goes back to serve the next table. The kitchen (consumer) picks up tickets when capacity allows. The restaurant handles 50 tables with 2 cooks because orders queue up at the pass — no waiter stands idle waiting at the window. That pass window is a message broker.

**Why it exists**: Without the pass, every waiter would walk into the kitchen to place an order and wait there until the cook finished. One slow meal would block an entire section. Systems face the same problem: a message broker decouples producers from consumers in time and topology, absorbs load spikes, and enables async processing without blocking the request path.

---

## 1. Concept Overview

A **message broker** accepts messages from producers and delivers them to consumers. It provides:
- **Decoupling**: Producers don't know which consumers exist.
- **Buffering**: Consumers process at their own pace; spikes don't overwhelm them.
- **Persistence**: Messages survive consumer restarts.
- **Delivery guarantees**: At-most-once, at-least-once, or exactly-once.

---

## 2. Core Principles

### Queue vs Pub/Sub

The restaurant analogy helps here too. A **queue** is the grill section — every ticket goes to one cook (competing consumers). A **pub/sub** topic is an announcement over the kitchen intercom — every station (salad, dessert, fry) hears the same message and acts on it.

| Model | Delivery | Use case |
|-------|----------|----------|
| **Queue** | Each message to one consumer (competing consumers) | Task queues, job processing |
| **Pub/Sub** | Each message to all subscribers | Events, fan-out (e.g. order created → inventory, email, analytics) |

### Topics and Partitions

Kafka topics work like different sections of the kitchen — grill, salad, dessert. Each section (partition) handles its own queue of orders. Multiple cooks (consumer instances) share a section via a **consumer group**: each cook picks up the next available ticket so no two cooks duplicate work on the same order. Add more sections (partitions) to increase parallelism.

### Delivery Guarantees

- **At-least-once**: A waiter accidentally duplicates a ticket. The kitchen may cook two steaks. Solution: make the kitchen idempotent — if it sees the same order ID twice, it only cooks once.
- **Exactly-once**: A kitchen system that detects duplicate ticket IDs and silently discards them. Requires both broker support and idempotent consumers.

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

A **dead-letter queue (DLQ)** is like a shelf on the kitchen pass for tickets the cook couldn't process after 3 attempts — an expeditor reviews them rather than letting them block the line forever.

---

## 6. Performance Considerations

- **Throughput**: Kafka and similar can do millions of msg/s with partitioning and batching.
- **Latency**: Trade-off between batching (higher throughput, higher latency) and immediate send (lower latency, lower throughput).
- **Persistence**: Disk vs memory; replication factor; affects durability and cost.

---

## 7. Implementation Patterns

### Task queue (Java example)

```java
// Producer: place an order ticket on the pass
kafkaProducer.send(new ProducerRecord<>("order-tasks", orderId, orderJson));

// Consumer: idempotent handler (at-least-once safe)
@KafkaListener(topics = "order-tasks", groupId = "kitchen-group")
public void processOrder(String orderJson) {
    Order order = parse(orderJson);
    if (orderRepo.isProcessed(order.getId())) return; // dedup
    kitchenService.cook(order);
    orderRepo.markProcessed(order.getId());
}
```

### Outbox Pattern

Write to the DB and an outbox table in the same transaction. A separate relay process publishes to the broker. If the service crashes between the DB commit and the broker send, no message is lost — the relay retries from the outbox on restart.

```java
// In one DB transaction:
orderRepo.save(order);                          // main record
outboxRepo.save(new OutboxEvent("order-created", orderId, payload)); // outbox

// Relay process (runs separately):
List<OutboxEvent> pending = outboxRepo.findUnpublished();
pending.forEach(e -> {
    producer.send(e.getTopic(), e.getPayload());
    outboxRepo.markPublished(e.getId());
});
```

- **Task queue**: One queue, N workers; at-least-once; idempotent handlers.
- **Event streaming**: Kafka-style; multiple consumers; replay; partition by key for ordering.
- **Outbox pattern**: Avoids losing messages when producer crashes after DB commit but before broker send.

---

## Quick Revision

- **Queue**: One consumer per message. **Pub/Sub**: fan-out to many subscribers.
- **At-least-once**: Common; requires idempotent consumers. **Exactly-once**: Idempotency + dedup or transactional outbox.
- **Kafka**: Log, partitions, replay, high throughput. **RabbitMQ**: Flexible routing, task queues. **SQS**: Simple, managed.
- **Failure**: Replication, DLQ for poison messages, scale consumers for backlog.
- **Interview**: "We use a message queue so the API can respond immediately and workers process notifications asynchronously; we design consumers to be idempotent for at-least-once delivery."

---

## See Also

- **Kafka internals** (log segments, ISR, consumer group rebalancing): [04-advanced-topics/internals/kafka-internals.md](../04-advanced-topics/internals/kafka-internals.md)
- **Event-driven architecture** (CQRS, event sourcing, outbox pattern): [04-advanced-topics/event-driven-architecture.md](../04-advanced-topics/event-driven-architecture.md)
- **HLD problems that use message brokers centrally**: [Notification Service](../05-hld-problems/02-medium/notification-service.md) (fan-out), [YouTube](../05-hld-problems/02-medium/youtube.md) (encoding pipeline), [Distributed Message Queue](../05-hld-problems/03-hard/distributed-message-queue.md) (Kafka as the product), [Web Crawler](../05-hld-problems/01-easy/web-crawler.md) (URL frontier queue)
- **Idempotency for at-least-once delivery**: [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md)

---

## Interview Questions Asked

### Conceptual
1. **"How does Kafka guarantee ordering?"** → Ordering is guaranteed per partition, not across partitions. Produce all related messages to the same partition (by the same key) to ensure order. Testing: do you know ordering is scoped to partition, and that scaling partitions breaks global order.
2. **"What is a consumer group and how does partition assignment work?"** → A consumer group is a set of consumers sharing a topic's partitions — each partition is assigned to exactly one consumer in the group. Adding consumers scales throughput up to the partition count; beyond that, consumers are idle. Testing: understanding of Kafka's horizontal scaling model.
3. **"Explain exactly-once semantics in Kafka"** → Requires idempotent producer (dedup by sequence number) + transactional API (atomic write across partitions + offset commit). Expensive — most systems use at-least-once + idempotent consumers instead. Testing: do you know exactly-once exists but understand the cost.
4. **"What is a dead letter queue and when would you use one?"** → A separate queue where messages are routed after N failed processing attempts. Prevents poison messages from blocking the main queue indefinitely. Use when you can't discard failed messages — inspect, alert, and retry later. Testing: failure handling design.
5. **"What is log compaction and when would you use it?"** → Kafka retains only the latest value per key per partition, discarding older versions. Useful for changelog topics (e.g., user profile updates) where you only care about current state, not full history. Testing: Kafka internals beyond basic pub/sub.

### Comparison / Trade-off
1. **"Kafka vs RabbitMQ — when to use each?"** → Kafka: high-throughput event streaming, replay needed, multiple independent consumers, event sourcing. RabbitMQ: flexible routing (topic/fanout/direct exchanges), task queues, per-message TTL/priority, simpler ops. Key differentiator: Kafka is a log (durable, replayable); RabbitMQ is a queue (messages deleted after ack).

### Scenario / Design
1. **"How do you handle backpressure in a pub/sub system?"** → Producer side: slow down publishing (rate limit, block, or drop) when the broker's queue depth exceeds a threshold. Consumer side: scale out consumers, increase parallelism. In Kafka: monitor consumer lag — if lag grows, add consumers (up to partition count) or increase processing throughput. Circuit break upstream if lag is critical.
