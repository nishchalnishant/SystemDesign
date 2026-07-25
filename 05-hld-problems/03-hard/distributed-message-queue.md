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

> 🎯 **Staff signal:** The performance unlock is that Kafka's log is **append-only and sequential**, which turns "durable 1M msg/sec" from a random-write problem (seek-bound, the thing that kills databases) into sequential I/O the OS page cache absorbs — writes hit page cache, recent reads are served from it, and zero-copy `sendfile` ships bytes disk→socket without a userspace copy. Naming *sequential-append + page-cache + zero-copy* as the reason a queue outruns a DB for this workload is the tell. The durability half is understanding **ISR (in-sync replicas) as a tunable, not a boolean**: `acks=all` + `min.insync.replicas=2` means an ACK guarantees the write survives any single broker loss, but the E6 subtlety is the *unclean-leader-election* fork — if all ISR members die, you choose between availability (promote an out-of-sync replica, lose data) and consistency (block until an ISR member returns). State that `acks`/`min.insync` is a per-topic durability-vs-latency dial and that page-cache reliance means an OS crash before flush can lose un-replicated writes — which is *why* replication, not fsync, is the durability story. E5 says "append-only log with replication"; E6 explains the I/O path and treats ISR as a dial with a named failure fork.

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

> 🎯 **Staff signal:** The core insight is that **the partition is the unit of parallelism *and* the unit of ordering, and they're the same knob** — so partition count sets your max consumer parallelism forever (16 partitions ⇒ at most 16 useful consumers; a 17th sits idle), and it must be chosen up front because increasing it later breaks key→partition affinity and reshuffles ordering. That coupling is the tell. On rebalancing, the E6 detail is that classic rebalance is **stop-the-world**: a single consumer's missed heartbeat pauses the *whole group* while partitions are reassigned, so consumer lag spikes during every deploy or crash — which is why *sticky/cooperative* assignment matters (keep partitions on their existing owner to avoid reloading per-partition local state) and why `session.timeout` is a latency-vs-false-positive dial. And name consumer lag as *the* health signal: it's the derivative that tells you throughput can't keep up before the queue overflows. E5 says "consumer groups scale out"; E6 states that partitions cap parallelism, ordering is per-partition, and rebalance is a stop-the-world event to design deploys around.

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

> 🎯 **Staff signal:** The senior clarification is that **Kafka "exactly-once" only holds *inside Kafka***: idempotent-producer (dedup on `producer_id`+`sequence`) plus transactions (atomically commit the produced output *and* the consumer offset via `send_offsets_to_transaction`, read with `read_committed`) give you exactly-once for the read→process→write loop *when every hop is a Kafka topic*. The moment the side effect leaves that boundary — a bank transfer, an external API, a row in another DB — Kafka's guarantee evaporates, because Kafka can't make a third party's `POST /transfer` idempotent. So the real answer is layered: Kafka EOS for the internal pipeline, **plus** downstream idempotency keyed on `message_id` (`ON CONFLICT DO NOTHING`) for the external effect. Stating that boundary explicitly — "exactly-once is a property of the closed Kafka loop; cross-boundary you still need an idempotency key" — is exactly the trap this problem sets, and naming it is the E5→E6 line. E5 says "enable exactly-once"; E6 says where it stops and what pays for the last mile.

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

---

## Back-of-Envelope Estimation

**Scale inputs (given in NFRs):**
- 1M messages/sec write; 10M messages/sec read (fan-out); < 10ms P99 end-to-end

**Ingest throughput:**
- 1M messages/sec × 1 KB average message size = **~1 GB/sec** write throughput
- With replication factor 3: 3 GB/sec actual disk write across the cluster
- NVMe SSD sequential write: ~2 GB/sec per disk → need at least 2 disks per broker for replication writes
- At 10 brokers: 100 MB/sec per broker (ingest) + 200 MB/sec replication = 300 MB/sec per broker — within NVMe capacity

**Fan-out read throughput:**
- 10M messages/sec read = 10× write rate → typical for pub-sub (1 topic, 10 consumer groups)
- 10M × 1 KB = **~10 GB/sec** read throughput from the cluster
- Page cache is the key: NVMe read ~3 GB/sec; Linux page cache hits are memory speed (~50 GB/sec)
- If messages are consumed within seconds of production (hot data), they're in page cache — 10 GB/sec served from RAM, not disk

**Partition sizing:**
- Each partition: one leader broker handles all reads and writes for that partition
- Leader throughput limit: ~100 MB/sec per partition (single-threaded in Kafka)
- 1 GB/sec ingest ÷ 100 MB/sec per partition = **10 partitions minimum** for write path
- For fan-out: 10 GB/sec ÷ 100 MB/sec = **100 partitions minimum** across the cluster
- Production recommendation: 200 partitions with 10 brokers = 20 partitions/broker = 2 GB/sec per broker read capacity

**Storage sizing:**
- 1 GB/sec ingest × 7-day retention × 86,400 sec/day = **~605 TB** raw
- With RF=3: **~1.8 PB** total cluster storage
- With compression (LZ4 on typical JSON payloads, ~3× ratio): **~600 TB** physical
- At 10 brokers: **60 TB per broker** (NVMe-backed storage arrays in production)

**Latency breakdown (< 10ms P99 budget):**
- Producer batch delay: 1ms (linger.ms=1)
- Network to broker leader: 1ms (same DC)
- Broker write to page cache + replicate to 2 followers: 4ms
- Follower ack: 2ms (acks=all)
- Consumer poll + network: 2ms
- Total: **~10ms** — exactly at SLA with no slack; use acks=1 (leader-only) to drop to ~4ms P99 if latency > durability

**Architecture decisions driven by these numbers:**
- **Sequential disk I/O as the design principle**: Kafka's 1 GB/sec write rate is only achievable via sequential appends to a log file (OS batches writes, page cache absorbs bursts). Random I/O at 1 GB/sec would require ~250K IOPS — beyond any single disk. The immutable append-only log is not just a design choice; it's what makes the throughput target achievable.
- **Page cache as the read tier**: 10 GB/sec read from disk is impossible (NVMe max ~3 GB/sec). But if consumers lag by less than the page cache size (~100 GB on a modern server), reads hit RAM at memory bandwidth (~50 GB/sec). This is why Kafka discourages consumer lag — a lagging consumer falls out of page cache and causes disk reads that degrade performance for all partitions on that broker.
- **200 partitions across 10 brokers**: Fewer, larger partitions simplify routing but create hot spots (one slow consumer blocks a large partition). More partitions distribute load but add coordination overhead (ZooKeeper/KRaft metadata per partition). 200 partitions at 10 brokers gives 20 per broker — the sweet spot for this throughput.

---

## Related

**Concepts used in this design**

- [Kafka Internals](../../04-advanced-topics/03-internals/03-kafka-internals.md)
- [Replication](../../02-building-blocks/03-data-partitioning/02-replication.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)
- [Consistent Hashing](../../02-building-blocks/03-data-partitioning/03-consistent-hashing.md)
- [Raft & Paxos](../../04-advanced-topics/03-internals/11-raft-paxos-conceptual.md)

**Practice next**

- [Distributed Job Scheduler](../03-hard/distributed-job-scheduler.md)
- [Chat System](../03-hard/chat-system.md)

The scheduler is a consumer of exactly the guarantees built here.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
