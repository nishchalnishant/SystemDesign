# Design Distributed Message Queue (Kafka)

> **Difficulty**: Hard
> **Topics**: Partitioning, Replication, Consumer Groups, Exactly-Once Semantics
> **Time**: 75 minutes
> **Companies**: LinkedIn, Uber, Netflix, Confluent

---

## Problem Statement

Design a distributed message queue like Apache Kafka that:
- Handles millions of messages per second
- Guarantees message ordering within a partition
- Provides at-least-once, at-most-once, and exactly-once delivery semantics
- Scales horizontally by adding brokers
- Supports multiple consumer groups

---

## Analogy

A conveyor belt in a factory. Producers place items on the belt at one end; consumers pick them up at their own pace at the other end.

The belt has a position counter (offset). A worker can step away and come back — the belt remembers where they left off. You can have multiple belts (partitions) running in parallel. Multiple teams (consumer groups) can independently read the same belt without interfering with each other. If you need to replay last Tuesday's items, you rewind the belt — the items are still there for 7 days.

The hard part: when you have 100 belts, 50 factories reading them, and a worker is added or removed mid-shift, how do you reassign belts without dropping items on the floor?

---

## What Breaks Without This System?

Without a message queue, every producer calls consumers directly over synchronous HTTP. When a downstream consumer is slow, the producer's thread pool exhausts waiting for responses — and the producer goes down too. One slow service cascades into a full outage. There is also no replay: if a consumer crashes mid-batch, the events it hadn't processed are gone forever with no way to reprocess them.

---

## Derive the Architecture

**1 server, in-memory queue**: Producer pushes jobs to an in-memory list; consumer pops and processes. Works at ~1,000 msg/sec. Breaks when: the server restarts — all unprocessed messages vanish. Fix: acknowledge only after writing to disk.

**Disk-backed single node**: Append each message to a log file before ACKing the producer. Survives restarts; consumer reads from last checkpointed offset. Works to ~10,000 msg/sec. Breaks when: a single rotating disk saturates at ~100 MB/s I/O, and a single 1 KB message stream at 10K/sec = 10 MB/s leaves no headroom for replicas or bursty traffic. Fix: partition the log across multiple disks and nodes so writes fan out in parallel.

**Partitioned log, N brokers**: Each partition is an independent ordered log on one broker. 10 partitions across 10 brokers = 10× throughput. Handles 100K–1M msg/sec. Breaks when: the broker owning a partition dies — that partition is unavailable until manual recovery. Fix: replicate each partition to 2 additional brokers (leader + 2 followers = replication factor 3). Writes require acknowledgment from the in-sync replica (ISR) set before the offset advances.

**Replicated partitions, ISR**: With RF=3, one broker failure is tolerated without message loss. Handles 1M msg/sec across 6+ brokers, 1 GB/sec ingress. Breaks when: multiple consumers in the same group need to process at different speeds — one slow consumer blocks the others from advancing the group's committed offset. Fix: assign each partition to exactly one consumer per group; add consumers up to partition count.

**Consumer groups + coordinator**: A group coordinator broker tracks partition assignment and heartbeats. When a consumer joins or dies, it triggers a rebalance. Handles arbitrary consumer fleet scaling. Breaks when: the cluster metadata store (Zookeeper) becomes a single point of failure and a bottleneck at >50K partition operations. Fix: replace Zookeeper with KRaft (Kafka's built-in Raft consensus), eliminating the external dependency and dropping metadata latency from ~100ms to ~10ms.

---

## Why This Is Hard

1. **Delivery semantics**: "At-least-once" is easy — retry until the consumer ACKs. "Exactly-once" is fundamentally hard in distributed systems: the producer might retry a successful send (network timeout), and the consumer might process before crashing before committing its offset. Each scenario requires a different fix at a different layer.
2. **Consumer rebalancing**: Every time a consumer joins or leaves a group, all consumers must pause, renegotiate partition assignments, and resume from their last committed offset. During this pause, no messages are consumed. With many consumers cycling, the system can thrash.
3. **Ordering guarantees**: Kafka only guarantees order within a partition. If you need global ordering across all events for a user, you must route all that user's events to the same partition — which creates a hotspot problem.
4. **ISR and durability**: If you use `acks=all`, the producer blocks until all In-Sync Replicas confirm. An ISR with a lagging follower can slow down every producer on that partition.
5. **Retention and disk management**: Kafka retains messages on disk for days. At 1M msg/sec, that's petabytes. Managing storage, compaction, and the LSM-style segment merge without impacting read/write latency requires careful tuning.

---

## Requirements

### Functional
- **Publish**: Producers send messages to topics
- **Subscribe**: Consumers receive messages from topics
- **Partitioning**: Messages distributed across partitions for parallelism
- **Consumer Groups**: Multiple consumers share topic load
- **Message Retention**: Messages stored for configurable time (e.g., 7 days)

### Non-Functional
- **Throughput**: 1M messages/sec per broker
- **Latency**: P99 < 10ms for writes
- **Durability**: No message loss (replicated to 3 brokers)
- **Ordering**: Messages in same partition maintain order
- **Availability**: 99.99% uptime

---

## Scale Estimation

```
Assumptions:
- 1M messages/sec (peak)
- Average message size: 1KB
- Retention: 7 days
- Replication factor: 3

Throughput:
1M msg/sec × 1KB = 1 GB/sec
Peak (3×): 3 GB/sec

Storage (7 days):
1M msg/sec × 1KB × 86,400 sec/day × 7 days = 604 TB
With replication (3×): 1.8 PB

Bandwidth:
Write: 3 GB/sec (ingress)
Read: 3 GB/sec × num_consumer_groups (egress)

Brokers needed (rough):
1 GB/sec ingress; broker NIC = 10 Gbps → single broker can handle ~1 GB/sec write
With replication overhead: ~3-5 brokers for throughput
For durability + HA: minimum 6 brokers (3 for writes, 3 replicas on different racks)
```

---

## Core Concepts

### 1. Topic & Partition

```
Topic: "user-events"
├── Partition 0: [msg1, msg3, msg5, ...]
├── Partition 1: [msg2, msg6, msg10, ...]
├── Partition 2: [msg4, msg7, msg11, ...]
└── Partition 3: [msg8, msg9, msg12, ...]

Producer chooses partition by:
- Hash(key) % num_partitions (if key provided → same key always same partition)
- Round-robin (if no key → spread load evenly)
```

**Why Partitions?**
- Parallelism: Multiple consumers read different partitions simultaneously
- Ordering within partition guaranteed (not across partitions)
- Scalability: Add more partitions to increase throughput

**Partition count trade-off:**
- More partitions → more parallelism, but also more overhead for leader election and file handles.
- Rule of thumb: `max(throughput_target / throughput_per_partition, num_consumers)`.

### 2. Consumer Groups

```
Topic: "orders" (3 partitions)

Consumer Group "payment-service":
├── Consumer 1 → reads Partition 0
├── Consumer 2 → reads Partition 1
└── Consumer 3 → reads Partition 2

Consumer Group "analytics-service":
├── Consumer 1 → reads Partition 0, 1
└── Consumer 2 → reads Partition 2

Each group gets ALL messages independently!
```

**Key**: Each partition is assigned to exactly ONE consumer per group. You cannot have more consumers in a group than partitions — extra consumers sit idle.

### 3. Replication

```
Topic: "orders", Partition 0, Replication Factor = 3

Broker 1 (Leader): [msg1, msg2, msg3] ← All writes go here
Broker 2 (Follower): [msg1, msg2, msg3] ← Async replication
Broker 3 (Follower): [msg1, msg2, msg3] ← Async replication

If Broker 1 fails → Broker 2 elected as new leader (via Zookeeper/KRaft)
```

---

## Architecture

```
┌─────────────┐
│  Producers  │
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────┐
│     Kafka Cluster (Brokers)      │
│                                   │
│  ┌─────────┐  ┌─────────┐       │
│  │ Broker1 │  │ Broker2 │  ...  │
│  │ (Leader)│  │(Follower)│       │
│  └─────────┘  └─────────┘       │
│                                   │
│  Topic: "events"                 │
│  ├─ Partition 0 (Leader: B1, Replicas: B2, B3)
│  ├─ Partition 1 (Leader: B2, Replicas: B1, B3)
│  └─ Partition 2 (Leader: B3, Replicas: B1, B2)
└──────────────┬───────────────────┘
               │
               ▼
┌──────────────────────────────────┐
│      Consumer Groups             │
│                                   │
│  Group "analytics":              │
│  ├─ Consumer 1 → Partition 0    │
│  └─ Consumer 2 → Partition 1, 2  │
└──────────────────────────────────┘

┌──────────────┐
│  Zookeeper   │  (Cluster coordination, leader election)
│  / KRaft     │  (KRaft = Kafka Raft, removes Zookeeper dep)
└──────────────┘
```

---

## Data Model

### Message Structure

```
Message:
├─ Key: "user_123" (optional, for partitioning)
├─ Value: {"event": "purchase", "amount": 99.99}
├─ Timestamp: 1675843200000
├─ Headers: {"source": "mobile-app"}
├─ Partition: 0
└─ Offset: 12345 (unique within partition, monotonically increasing)
```

### Offset Management

```
Topic: "orders", Partition 0

Offset
0      [msg: "order_1"]
1      [msg: "order_2"]
2      [msg: "order_3"]  ← Consumer currently at offset 2
3      [msg: "order_4"]
...

Consumer commits offset to Kafka:
Group "payment-service", Partition 0, Offset: 3
→ Next read starts from offset 3

Committed offsets stored in internal topic: "__consumer_offsets"
```

---

## API Design

### Producer API

```java
Producer<String, String> producer = new KafkaProducer<>(props);

ProducerRecord<String, String> record = new ProducerRecord<>(
    "user-events",             // topic
    "user_123",                // key (determines partition)
    "{\"action\": \"click\"}"  // value
);

// Async send (high throughput)
producer.send(record, (metadata, exception) -> {
    if (exception == null) {
        System.out.println("Sent to partition " + metadata.partition() +
                          ", offset " + metadata.offset());
    }
});

// Sync send (wait for ack — lower throughput, guaranteed delivery)
RecordMetadata metadata = producer.send(record).get();
```

### Consumer API

```java
Consumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("user-events"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

    for (ConsumerRecord<String, String> record : records) {
        processMessage(record.value());
    }

    // Commit AFTER processing (at-least-once delivery)
    consumer.commitSync();
}
```

---

## Deep Dive Topics

### 1. Delivery Semantics

#### At-Most-Once (May Lose Messages)
```
1. Consumer reads message
2. Consumer commits offset   ← offset committed
3. Consumer processes message ← crash here → message LOST (offset already moved forward)

Use case: Metrics, logs where occasional loss is acceptable.
```

#### At-Least-Once (May Duplicate)
```
1. Consumer reads message
2. Consumer processes message
3. Consumer commits offset    ← crash here → reprocess on restart (duplicate)

Most Common: At-least-once + idempotent processing (check for duplicate before acting)
```

#### Exactly-Once (Complex)
```
Requirements:
1. Idempotent producer: retry doesn't create duplicates (enable.idempotence=true)
2. Transactional writes: atomic commit of output + offset
3. Consumer reads ONLY committed transactions (isolation.level=read_committed)

Implementation:
producer.initTransactions();
producer.beginTransaction();
    producer.send(outputRecord);
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);
producer.commitTransaction();  // atomic: either all or nothing
```

---

### 2. Replication & Leader Election

**In-Sync Replicas (ISR):**
```
Partition 0:
├─ Leader (Broker 1): Offset 100
├─ Follower (Broker 2): Offset 100 ← In-sync (within replica.lag.time.max.ms)
└─ Follower (Broker 3): Offset 95  ← Out-of-sync (lagging, removed from ISR)

ISR = [Broker 1, Broker 2]
```

**Leader Election:**
```
If Leader fails:
1. Zookeeper / KRaft detects failure
2. New leader elected from ISR (e.g., Broker 2)
3. Clients automatically reconnect to new leader

Producer acks settings:
acks=all: Wait for ALL ISR replicas to ack (slower, safest — no data loss if ISR >= 2)
acks=1:   Wait for leader only (faster, less durable)
acks=0:   No wait (fastest, may lose data on leader crash)
```

---

### 3. Consumer Rebalancing

**Scenario**: Consumer added/removed from group

```
Before:
Group "analytics" (2 consumers):
├─ Consumer 1 → Partition 0, 1
└─ Consumer 2 → Partition 2, 3

Consumer 3 joins:
1. Rebalancing triggered (stop-the-world for the group)
2. Partitions reassigned:
   ├─ Consumer 1 → Partition 0, 1
   ├─ Consumer 2 → Partition 2
   └─ Consumer 3 → Partition 3

During rebalancing: ALL consumption pauses (brief gap)
```

**Rebalance Protocol:**
1. Group coordinator (a Kafka broker) detects change via heartbeat timeout
2. Issues `JoinGroup` request to all consumers
3. Elected group leader computes new assignment
4. Coordinator distributes assignment via `SyncGroup`
5. Consumers resume from last committed offset

**Cooperative Rebalancing (Kafka 2.4+):** Only revoke and reassign affected partitions. No full stop-the-world pause.

---

### 4. Message Ordering Guarantees

**Within Partition**: Guaranteed (FIFO)
```
Producer → Partition 0: [msg1, msg2, msg3]
Consumer reads: [msg1, msg2, msg3] (same order, always)
```

**Across Partitions**: NOT guaranteed
```
Producer:
├─ msg1 (Partition 0, offset 10)
├─ msg2 (Partition 1, offset 5)
└─ msg3 (Partition 0, offset 11)

Consumer may see: msg2, msg1, msg3 (different order)
```

**Solution for per-entity ordering**: Partition by entity ID.
```
producer.send(new ProducerRecord<>("orders", userId, orderPayload));
// All orders for user_123 always go to same partition → ordered
```

**Solution for Global Ordering**: Use 1 partition (limits throughput to single-threaded consumption).

---

### 5. Compaction (Log Compaction)

**Use Case**: Keep only latest value per key (e.g., user profile updates, CDC)

```
Before Compaction:
Offset  Key      Value
0       user_1   {"name": "Alice"}
1       user_2   {"name": "Bob"}
2       user_1   {"name": "Alice Smith"}  ← Latest for user_1
3       user_3   {"name": "Charlie"}

After Compaction:
Offset  Key      Value
2       user_1   {"name": "Alice Smith"}  ← Kept (latest)
1       user_2   {"name": "Bob"}           ← Kept
3       user_3   {"name": "Charlie"}       ← Kept
```

**Config**: `cleanup.policy=compact`
**Use case**: Change Data Capture (CDC), event sourcing state snapshots.

---

## Scaling Strategies

### Horizontal Scaling (Add Brokers)
```
3 Brokers → 6 Brokers:
1. Add 3 new brokers
2. Rebalance partition leadership across all 6 brokers
3. Throughput increases (each broker handles fewer partitions)
```

### Partition Scaling
```
Topic "events" (3 partitions) → (6 partitions):
1. Create new partitions
2. New messages hash to new partition distribution
3. Old messages remain in original partitions (not moved)

Limitation: Cannot DECREASE partition count without recreating the topic.
Warning: Increasing partitions breaks hash-based key ordering guarantees.
```

---

## Failure Scenarios

### Broker Failure
```
Impact: Partitions with leader on failed broker are unavailable for writes.
Duration: Time for new leader election (typically 5-30 seconds with Zookeeper,
          faster with KRaft).
Mitigation:
- Replication: ISR follower is promoted to leader automatically.
- acks=all: No data loss if at least one replica was in-sync.
- Auto-recovery: Failed broker can rejoin cluster as follower.
```

### Consumer Failure
```
Impact: Partitions assigned to dead consumer are not consumed.
Duration: Time for heartbeat timeout + rebalance (default: 10-30 seconds).
Mitigation:
- Rebalancing reassigns partitions to surviving consumers.
- Resume from last committed offset (no message loss; possible reprocessing).
- session.timeout.ms tuning: Lower = faster detection but more false positives.
```

### Network Partition (Split Brain)
```
Problem: Cluster splits into two halves; both halves try to elect leaders.
Mitigation:
- Zookeeper quorum: Majority must agree on leader. Minority partition cannot elect.
- If no quorum, minority brokers refuse writes (protect against split-brain).
- KRaft: Uses Raft protocol — only majority partition continues; minority is read-only.
```

### Consumer Lag Explosion
```
Scenario: Producer speed > consumer processing speed.
Effect: Lag grows; messages take longer and longer to process; eventually
        messages expire from retention and are LOST before being consumed.
Mitigation:
- Scale consumers (add instances up to partition count).
- Optimize consumer processing (batching, async I/O).
- Increase retention period or watch lag alert (lag > N hours * consumption_rate).
```

---

## Monitoring

**Key Metrics:**
```
- Under-replicated partitions (ISR < replication factor) → data loss risk
- Consumer lag (offset difference between HW and consumer offset) → processing delay
- Broker disk usage (>80% = add storage or reduce retention)
- Request latency P99 (< 10ms for writes)
- Throughput (messages/sec, MB/sec per broker)
```

**Alerts:**
```
P0: Under-replicated partitions > 0 (data loss risk — page immediately)
P1: Consumer lag > 1M messages (processing falling behind — scale consumers)
P2: Disk usage > 80% (add brokers or adjust retention.ms)
```

---

## Interview Talking Points

**Q: "How does Kafka achieve high throughput?"**
- A: "Four mechanisms: (1) Sequential disk I/O — Kafka appends to log files sequentially, which is as fast as RAM on modern SSDs; (2) Zero-copy — uses `sendfile` syscall to transfer data from disk to network without copying through user space; (3) Batching — producers batch messages before sending, amortizing per-message overhead; (4) Compression — GZIP/Snappy applied to batches, reducing both storage and network."

**Q: "What happens if consumer crashes before committing offset?"**
- A: "On restart, consumer re-reads from last committed offset (at-least-once delivery). Messages between last commit and crash are reprocessed. To handle this safely, make consumer processing idempotent — check a dedup key before acting."

**Q: "How do you ensure exactly-once semantics?"**
- A: "Three layers: (1) Idempotent producer with `enable.idempotence=true` — broker deduplicates retries using sequence numbers; (2) Kafka transactions — atomic commit of both output messages and consumer offset; (3) Consumer with `isolation.level=read_committed` — reads only messages in completed transactions."

**Q: "Kafka vs RabbitMQ?"**
| Feature | Kafka | RabbitMQ |
|---------|-------|----------|
| Throughput | Very high (1M+/sec) | Medium (10K-100K/sec) |
| Message Ordering | Per partition | Per queue |
| Message Retention | Configurable (days) | Deleted after consumption |
| Replay | Yes (by rewinding offset) | No (consumed = gone) |
| Use Case | Event streaming, logs, CDC | Task queues, RPC |

**Key Trade-offs:**
- Ordering vs Parallelism: 1 partition = ordered but single-threaded; many partitions = parallel but unordered across partitions
- Durability vs Latency: `acks=all` = no data loss but slower; `acks=0` = max speed but fire-and-forget
- Consumer lag vs Throughput: Commit frequently = more overhead; commit rarely = more reprocessing risk on crash

---

## Interview Questions Asked

### Amazon
1. **"Design Amazon SQS"** → Probe: at-least-once delivery, visibility timeout, dead-letter queues, horizontal scalability. Hint: messages stored redundantly across AZs; consumer holds visibility lock for configurable timeout — if not deleted, message becomes visible again for retry.

### LinkedIn
1. **"Walk me through Kafka internals — how does it achieve 1M messages/sec?"** → Probe: sequential I/O, zero-copy, batching, compression. Hint: `sendfile` syscall bypasses user-space copy; producers batch and compress; log-structured storage means all writes are sequential appends.

### Common Follow-ups
1. **"How do you guarantee exactly-once delivery in Kafka?"** → Three layers: idempotent producer (`enable.idempotence=true`) deduplicates retries via sequence numbers; Kafka transactions atomically commit output messages + consumer offset; consumer reads with `isolation.level=read_committed`.
2. **"How does Kafka handle consumer group rebalancing?"** → Group coordinator (a broker) triggers rebalance when consumer joins/leaves/crashes; all consumers in the group pause, revoke partitions, then re-assign via partition assignor (range or round-robin); cooperative rebalancing (incremental) reduces stop-the-world pauses by only reassigning moved partitions.
3. **"How do you choose a partition key for ordering guarantees?"** → Partition by the entity that needs ordering (e.g., `user_id` for user events, `order_id` for order lifecycle); all events for that key land on one partition, consumed by one thread in order; avoid high-cardinality random keys which break ordering.
4. **"What are compacted topics and when do you use them?"** → Log compaction retains only the latest message per key (older values garbage-collected); used for CDC (change data capture) where downstream systems need current state, not full history; consumers can rebuild current state by reading compacted topic from beginning.
5. **"How do you handle poison messages that crash the consumer repeatedly?"** → After N retries, route to a dead-letter queue (DLQ) topic; consumer continues processing other messages; ops team inspects DLQ, fixes bug, then replays; SQS has native DLQ with `maxReceiveCount`; Kafka requires application-level DLQ routing.
