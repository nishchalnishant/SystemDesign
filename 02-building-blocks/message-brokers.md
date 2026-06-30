---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Message Brokers

> **Middleware that enables asynchronous messaging between services via queues or pub/sub.**

---

## File Mindmap

```
Message Brokers
├── Why It Exists
│   ├── Problem → order service calls notification synchronously; email saturates; orders fail
│   └── Forces → synchronous coupling means producer speed bounded by slowest consumer; no buffering
├── Core Models
│   ├── Queue (Point-to-Point)
│   │   ├── Message consumed by exactly one consumer
│   │   ├── Use case → work distribution; task queues; order processing
│   │   └── Examples → RabbitMQ, SQS, ActiveMQ
│   └── Pub/Sub (Publish-Subscribe)
│       ├── Message broadcast to all subscribed consumers
│       ├── Use case → event fan-out; audit log; cache invalidation
│       └── Examples → Kafka topics, Google Pub/Sub, SNS
├── Kafka Architecture
│   ├── Topics → named streams; logically partitioned
│   ├── Partitions → ordered, immutable log; key → consistent partition
│   │   └── Partition count → max parallelism for consumers
│   ├── Consumer Groups → each group gets all messages; each partition read by one consumer/group
│   ├── Offsets → consumer tracks position; broker doesn't push — consumer pulls
│   └── Retention → messages kept N days regardless of consumption (replay capability)
├── Delivery Guarantees
│   ├── At-most-once → fire and forget; possible message loss; use for metrics/logs
│   ├── At-least-once → retry until ack; possible duplicate; use for payments (idempotent consumer)
│   └── Exactly-once → Kafka transactions + idempotent producer; highest cost; use for financial
├── Dead Letter Queue (DLQ)
│   ├── Messages that fail N retries → moved to DLQ
│   ├── Prevents poison pill blocking queue indefinitely
│   └── Ops workflow → alert on DLQ depth; manual inspect/replay
├── Outbox Pattern (at-least-once across DB + broker)
│   ├── Write event to outbox table in same DB transaction as business data
│   ├── Separate relay process polls outbox and publishes to broker
│   └── Ensures no message lost if broker down at write time
├── Broker Comparison
│   ├── Kafka → high throughput, durable, replay, partitioned; complex ops; best for streaming
│   ├── RabbitMQ → flexible routing (exchanges); AMQP; easy to run; best for task queues
│   └── SQS → managed AWS; auto-scaling; FIFO variant for ordering; no replay
├── Trade-offs
│   ├── Pros → decouple producer/consumer speeds; absorb bursts; enable async workflows
│   └── Cons → at-least-once requires idempotent consumers; added latency; operational complexity
├── Failure Scenarios
│   ├── Broker down → producers buffer or fail fast; consumers pause; replication for HA
│   ├── Consumer lag → add consumers; increase partitions; back-pressure to producer
│   └── Poison pill → DLQ; schema validation at publish time
└── Interview Angles
    ├── "Queue vs pub/sub?" → queue: one consumer gets it; pub/sub: all subscribers get it
    ├── "How do you ensure exactly-once?" → idempotent consumer + deduplication key; or Kafka transactions
    ├── "What is the outbox pattern and why?" → atomic write to DB + broker without 2PC
    └── Follow-up: "How do you handle consumer lag in Kafka?" → add consumers up to partition count; then repartition
```

---

## 1. Why Message Brokers Exist

**Question**: Your order service calls the notification service synchronously. Black Friday hits: 50,000 orders/minute arrive, but the notification service can only handle 10,000 emails/minute. What happens to the orders while the email service is saturated?

**Physical constraint**: Network RTT within a datacenter is ~1ms. Each synchronous call holds a thread for the full duration of the downstream operation. A thread pool of 200 threads × 1 call = 200 concurrent in-flight requests. Beyond that, the caller blocks and your latency climbs instantly. Disk I/O and CPU are finite — two services with mismatched throughput have no buffer between them under synchronous coupling.

**Minimal solution**: Write pending notifications to a database table. A cron job polls the table every second and sends emails at a capped rate. Works until: the cron interval adds latency, polling hammers the DB, the cron node crashes and leaves a gap, and you can't scale the consumer without duplicating cron jobs.

**Production generalization**: A dedicated message broker replaces the polling loop. It persists messages durably, delivers them to consumers at their own pace, handles redelivery on crash without manual intervention, and lets you scale consumers independently of producers. The broker is the buffer — it absorbs the mismatch between producer and consumer rates.

---

## 2. Core Principles

### Queue vs Pub/Sub

A **queue** delivers each message to exactly one consumer (competing consumers). A **pub/sub** topic delivers each message to all subscribers.

| Model | Delivery | Use case |
|-------|----------|----------|
| **Queue** | Each message to one consumer (competing consumers) | Task queues, job processing |
| **Pub/Sub** | Each message to all subscribers | Events, fan-out (e.g. order created → inventory, email, analytics) |

The restaurant pass window analogy helps here too. A **queue** is the grill section — every ticket goes to one cook (competing consumers). A **pub/sub** topic is an announcement over the kitchen intercom — every station (salad, dessert, fry) hears the same message and acts on it.

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

- **Throughput**: Kafka and similar can do millions of msg/s with partitioning and batching. A single broker sustains ~100–500 MB/s; throughput scales with partitions and brokers.
- **Latency**: Trade-off between batching (higher throughput, higher latency) and immediate send (lower latency, lower throughput). `linger.ms` (how long the producer waits to fill a batch) and `batch.size` are the two knobs: `linger.ms=0` minimizes latency; `linger.ms=5–100` maximizes throughput by amortizing network overhead.
- **Persistence**: Disk vs memory; replication factor; affects durability and cost. Kafka achieves high disk throughput via sequential appends + OS page cache + zero-copy (`sendfile`) — the broker never deserializes the message, so disk-based Kafka often outperforms memory-based brokers.

### Producer Durability Knobs (Kafka)

| `acks` | Meaning | Durability | Latency |
|--------|---------|------------|---------|
| `acks=0` | Fire and forget; don't wait for broker | Can lose on broker failure | Lowest |
| `acks=1` | Wait for leader to write | Lost if leader dies before replication | Medium |
| `acks=all` (+ `min.insync.replicas=2`) | Wait for ISR quorum | No loss while one ISR survives | Highest |

`acks=all` with `min.insync.replicas=2` and replication factor 3 is the standard "no data loss" config. If too few replicas are in-sync, the producer blocks rather than silently dropping durability.

### Consumer Lag — the Key Operational Metric

**Consumer lag** = (latest offset produced) − (last offset committed by the group), per partition. It is the single most important health signal for a streaming pipeline — rising lag means consumers can't keep up. Monitor it (Burrow, Kafka Lag Exporter, `kafka-consumer-groups --describe`) and alert before lag becomes unacceptable end-to-end latency. Reduce lag by adding consumers (up to partition count), increasing per-consumer parallelism, or increasing partitions.

### Rebalancing — the Hidden Cost of Scaling Consumers

When a consumer joins or leaves a group, Kafka triggers a **rebalance**: partition ownership is reshuffled. In a classic stop-the-world rebalance, all consumers in the group pause — a latency spike. Mitigations an interviewer probes for:
- **Cooperative/incremental rebalancing** (`CooperativeStickyAssignor`, Kafka 2.4+): only the moved partitions pause, not the whole group.
- **Static membership** (`group.instance.id`): a consumer that briefly disconnects (deploy, GC) keeps its partitions instead of forcing a full rebalance, as long as it returns within `session.timeout.ms`.
- Tune `max.poll.interval.ms` so a slow consumer isn't wrongly evicted mid-batch (a common cause of rebalance storms).

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
1. **"How does Kafka guarantee ordering?"** → Ordering is guaranteed per partition, not across partitions. Produce all related messages to the same partition (by the same key) to ensure order. Gotcha: you cannot freely increase partition count later — keys are hashed `hash(key) % partition_count`, so adding partitions reshuffles the key→partition mapping and breaks per-key ordering for existing keys. Plan partition count up front (over-provision) or use a stable custom partitioner. Also: with a default async producer, retries can reorder messages on a single partition unless you set `max.in.flight.requests.per.connection=1` or enable the idempotent producer (which preserves order even with retries). Testing: do you know ordering is scoped to partition, that scaling partitions breaks both global order and per-key order, and that producer retries can reorder.
2. **"What is a consumer group and how does partition assignment work?"** → A consumer group is a set of consumers sharing a topic's partitions — each partition is assigned to exactly one consumer in the group. Adding consumers scales throughput up to the partition count; beyond that, consumers are idle. Testing: understanding of Kafka's horizontal scaling model.
3. **"Explain exactly-once semantics in Kafka"** → Requires idempotent producer (dedup by sequence number) + transactional API (atomic write across partitions + offset commit). Expensive — most systems use at-least-once + idempotent consumers instead. Testing: do you know exactly-once exists but understand the cost.
4. **"What is a dead letter queue and when would you use one?"** → A separate queue where messages are routed after N failed processing attempts. Prevents poison messages from blocking the main queue indefinitely. Use when you can't discard failed messages — inspect, alert, and retry later. Testing: failure handling design.
5. **"What is log compaction and when would you use it?"** → Kafka retains only the latest value per key per partition, discarding older versions. Useful for changelog topics (e.g., user profile updates) where you only care about current state, not full history. Testing: Kafka internals beyond basic pub/sub.

### Comparison / Trade-off
1. **"Kafka vs RabbitMQ — when to use each?"** → Kafka: high-throughput event streaming, replay needed, multiple independent consumers, event sourcing. RabbitMQ: flexible routing (topic/fanout/direct exchanges), task queues, per-message TTL/priority, simpler ops. Key differentiator: Kafka is a log (durable, replayable); RabbitMQ is a queue (messages deleted after ack).

### Scenario / Design
1. **"How do you handle backpressure in a pub/sub system?"** → Producer side: slow down publishing (rate limit, block, or drop) when the broker's queue depth exceeds a threshold. Consumer side: scale out consumers, increase parallelism. In Kafka: monitor consumer lag — if lag grows, add consumers (up to partition count) or increase processing throughput. Circuit break upstream if lag is critical.
