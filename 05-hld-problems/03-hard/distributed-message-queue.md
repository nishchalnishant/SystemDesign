> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed message queue (Kafka) — durable, ordered, partitioned message storage with consumer groups and exactly-once delivery semantics.
>
> **Key design decisions:**
> - Partitioning: topic split into P partitions; each partition is an ordered append-only log; partition key determines which partition; enables parallelism
> - Durability: messages written to disk + replicated to ISR (in-sync replicas); acks=all ensures no data loss; configurable retention (time or size)
> - Consumer groups: each group gets independent offset cursor per partition; add consumers = parallel consumption up to partition count
> - Offset management: offsets stored in __consumer_offsets topic; auto-commit vs manual commit; commit after processing for at-least-once
> - Backpressure: consumers pull at their own rate; producers never overwhelm consumers; natural backpressure via pull model
> - Exactly-once: idempotent producer (epoch + sequence) eliminates duplicates; transactional API for atomic multi-partition writes + offset commit
> - Compaction: log compaction retains only latest value per key; enables Kafka as a changelog / state store (source of truth)
>
> **Key takeaway:** Kafka's partition-based parallelism + pull model + ISR replication = high throughput + durability; consumer groups make it trivially scalable to add downstream consumers.

---
module: 05-hld-problems
topic: Hard
status: unread
tags: [05-hld-problems, system-design, hard, message-queue, kafka, durability, partitioning, consumer-groups]
---
# Design a Distributed Message Queue (Kafka)

> **Difficulty**: Hard | **Asked at**: LinkedIn, Uber, Confluent, Amazon

---

## Problem Statement

Design a distributed message queue like Apache Kafka. Producers publish messages to topics; consumers read messages in order. The system must provide durable storage, horizontal scalability, at-least-once delivery, and support millions of messages per second.

---

## Functional Requirements

1. **Publish**: Producers send messages to named topics
2. **Subscribe**: Consumer groups read messages from topics, maintaining per-group offset
3. **Ordering**: Messages within a partition are delivered in order
4. **Persistence**: Messages stored durably (configurable retention: 7 days default)
5. **Replay**: Consumers can re-read messages by resetting offset
6. **Partitioning**: Topics divided into partitions for parallelism

---

## Non-Functional Requirements

- **Throughput**: 1M messages/sec write; 10M messages/sec read (fan-out to many consumers)
- **Latency**: End-to-end < 10ms for P99 (produce to consume)
- **Durability**: No message loss (replicated to 3 nodes; persist to disk before ACK)
- **Availability**: 99.99% — survive single broker failures
- **Scale**: Petabytes of storage; thousands of topics; millions of partitions

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `Topic` | topic_name, partition_count, replication_factor, retention_ms |
| `Partition` | topic_name, partition_id, leader_broker_id, replica_broker_ids[], log_end_offset |
| `Message` | partition_id, offset, key (bytes), value (bytes), headers, timestamp |
| `ConsumerGroup` | group_id, topic_name, partition_offsets (partition_id → committed_offset) |
| `Broker` | broker_id, host, port, is_controller |

---

## API Design

**Producer API**:
```python
producer = KafkaProducer(bootstrap_servers=["broker1:9092", "broker2:9092"])

producer.send(
    topic="order-events",
    key=b"order-123",            # determines partition assignment
    value=json.dumps(order).encode(),
    headers=[("source", b"order-service")]
)
producer.flush()  # wait for broker ACK (acks=all for durability)
```

**Consumer API**:
```python
consumer = KafkaConsumer(
    "order-events",
    group_id="fulfillment-service",
    bootstrap_servers=["broker1:9092"],
    auto_offset_reset="earliest"
)
for message in consumer:
    process(message.value)
    consumer.commit()  # commit offset after successful processing
```

**Admin API**:
```http
POST /api/v1/topics
Body: { "name": "order-events", "partitions": 16, "replication_factor": 3 }

GET /api/v1/topics/{topic}/offsets?group_id=fulfillment-service
Response: { "partitions": [{ "partition": 0, "committed": 54321, "end": 54400, "lag": 79 }] }
```

---

## High-Level Design

```
Producers
  │ hash(key) % partition_count → partition assignment
  │ send to leader broker for that partition
  ▼
Broker Cluster (N brokers)
  │ Each partition has one leader broker + N-1 replica brokers
  │ Leader handles reads and writes
  │ Replicas follow: copy from leader's log
  │
  ├── Partition Log (append-only file on disk, indexed by offset)
  │     Write: append to log → fsync → ack producer
  │     Read: seek to offset → read sequential pages (O(1))
  │
  └── ZooKeeper / KRaft (metadata: partition assignments, consumer group offsets)

Consumers
  ├── Consumer Group: partitions divided among consumers in group (1 partition → 1 consumer)
  ├── Consumer reads from partition leader
  ├── Commit offset to Kafka internal topic `__consumer_offsets`
  └── On consumer join/leave: rebalance partition assignments
```

---

## Deep Dive 1: Partition Log and Durability

**Problem**: Kafka must store 1M messages/sec durably without losing data even during broker failures. How does the append-only log achieve this?

**Append-only segment files**: Each partition's log is stored as a series of segment files on disk:
```
/data/order-events-0/
  00000000000000000000.log  # offsets 0 → 1,000,000
  00000000000001000001.log  # offsets 1,000,001 → 2,000,000 (active)
  00000000000000000000.index  # sparse offset → file_position index
```
Writes are sequential appends — optimal for HDD and SSD (no seek overhead). Reading any offset: binary search the `.index` file → seek to file position → read forward.

**Replication protocol (ISR — In-Sync Replicas)**:
1. Producer sends to partition leader
2. Leader appends to its local log
3. Follower brokers pull from leader (not pushed)
4. Leader tracks ISR (list of followers within N messages of leader's log end)
5. With `acks=all`: leader waits until all ISR followers have written the message → returns ACK to producer
6. A follower that falls behind is removed from ISR; rejoins after catching up

**Durability guarantee**: A message ACKed by the leader is guaranteed to survive any single broker failure (because all ISR members have it). With `min.insync.replicas=2` and `replication_factor=3`, even if one replica fails, the write still completes.

**Page cache optimization**: Kafka relies on the OS page cache. Writes go to page cache (not directly to disk) — the OS flushes asynchronously. This makes writes appear fast. Reads also hit the page cache for recent messages. A consumer reading messages produced seconds ago never hits disk.

---

## Deep Dive 2: Consumer Groups and Rebalancing

**Problem**: A consumer group of 4 consumers reads from a topic with 16 partitions. One consumer crashes. How does the system rebalance?

**Partition assignment**: 16 partitions / 4 consumers = 4 partitions each. Assignment is managed by a designated consumer acting as the **Group Coordinator** on a specific broker.

**Rebalance trigger**: Consumer sends heartbeats every 3 seconds. If a heartbeat is missed for `session.timeout.ms` (default 10s), the Group Coordinator triggers rebalance.

**Rebalance protocol**:
1. Coordinator sends `JoinGroup` to all consumers
2. Each consumer responds with its topic subscriptions
3. Coordinator elects a Group Leader (one of the consumers)
4. Group Leader runs the partition assignment algorithm and sends back to Coordinator
5. Coordinator distributes assignments via `SyncGroup` response
6. During rebalance: all consumers stop consuming (stop-the-world) until reassignment completes

**Sticky partition assignment**: New assignment tries to keep partitions with the same consumer they had before. Minimizes state reload (consumers often maintain local state per partition).

**Consumer lag monitoring**: `consumer_lag = partition_end_offset - consumer_committed_offset`. Kafka exposes lag via JMX metrics. Alert when lag > 10,000 messages (consumer is falling behind).

---

## Deep Dive 3: Message Routing and Exactly-Once

**Problem**: A payment service produces a message that causes a bank transfer. If the message is processed twice, $100 is transferred twice. How do you guarantee exactly-once processing?

**Idempotent producer** (`enable.idempotence=true`):
- Producer assigns a `producer_id` and per-partition `sequence_number` to each message
- Broker deduplicates: if it receives a message with the same `(producer_id, sequence_number)`, it discards the duplicate
- Handles network retries: producer retries on timeout → broker deduplicates

**Transactions** (producer + consumer exactly-once):
```python
producer = KafkaProducer(transactional_id="payment-processor-1")
producer.init_transactions()

producer.begin_transaction()
try:
    result = process_payment(message)  # idempotent payment operation
    producer.send("payment-results", result)
    producer.send_offsets_to_transaction(consumer_offsets, "payment-group")
    producer.commit_transaction()
except Exception:
    producer.abort_transaction()
```
`send_offsets_to_transaction` atomically commits the consumer offset AND the produced output message. Either both happen or neither does. Consumer reading with `isolation.level=read_committed` only sees committed messages.

**Downstream idempotency**: Even with Kafka exactly-once, the downstream system (database, API) must be idempotent. Include `message_id` in the payload; DB uses `ON CONFLICT DO NOTHING` on the message_id.

---

## Interviewer Questions by Level

**Junior**:
- What is a Kafka topic and partition? Why do we partition topics?
- What is a consumer group? How do multiple consumers in a group share work?
- What does "commit an offset" mean? Why is it important?

**Mid-level**:
- What is the ISR (In-Sync Replicas) and how does it ensure durability?
- When does a consumer group rebalance? What happens during the rebalance?
- How does Kafka achieve high throughput? Why is sequential disk I/O important?

**Senior**:
- Design the end-to-end exactly-once guarantee for a payment processing pipeline.
- How does Kafka handle a broker failure during a write that has `acks=all`?
- A consumer group has 100 consumers and a topic has 10 partitions. What's the problem and how do you fix it?
- Design a multi-region Kafka deployment for a global payment system — how do you handle cross-region replication and failover?
