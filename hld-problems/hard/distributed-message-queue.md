# Design Distributed Message Queue (Kafka)

> **Difficulty**: Hard  
> **Topics**: Partitioning, Replication, Consumer Groups, Exactly-Once Semantics  
> **Time**: 75 minutes  
> **Companies**: LinkedIn, Uber, Netflix, Confluent

## Problem Statement

Design a distributed message queue like Apache Kafka that:
- Handles millions of messages per second
- Guarantees message ordering within a partition
- Provides at-least-once, at-most-once, and exactly-once delivery semantics
- Scales horizontally by adding brokers
- Supports multiple consumer groups

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
```

## Core Concepts

### 1. Topic & Partition

```
Topic: "user-events"
├── Partition 0: [msg1, msg3, msg5, ...]
├── Partition 1: [msg2, msg6, msg10, ...]
├── Partition 2: [msg4, msg7, msg11, ...]
└── Partition 3: [msg8, msg9, msg12, ...]

Producer chooses partition by:
- Hash(key) % num_partitions (if key provided)
- Round-robin (if no key)
```

**Why Partitions?**
- Parallelism (multiple consumers read different partitions)
- Ordering within partition (not across partitions)
- Scalability (add more partitions)

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

**Key**: Each partition assigned to exactly ONE consumer per group.

### 3. Replication

```
Topic: "orders", Partition 0, Replication Factor = 3

Broker 1 (Leader): [msg1, msg2, msg3] ← Writes go here
Broker 2 (Follower): [msg1, msg2, msg3] ← Async replication
Broker 3 (Follower): [msg1, msg2, msg3] ← Async replication

If Broker 1 fails → Broker 2 elected as new leader (via Zookeeper/Kraft)
```

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
│  └─ Consumer 2 → Partition 1,2  │
└──────────────────────────────────┘

┌──────────────┐
│  Zookeeper   │  (Cluster coordination, leader election)
│  / KRaft     │
└──────────────┘
```

## Data Model

### Message Structure

```
Message:
├─ Key: "user_123" (optional, for partitioning)
├─ Value: {"event": "purchase", "amount": 99.99}
├─ Timestamp: 1675843200000
├─ Headers: {"source": "mobile-app"}
├─ Partition: 0
└─ Offset: 12345 (unique within partition)
```

### Offset Management

```
Topic: "orders", Partition 0

Offset
0      [msg: "order_1"]
1      [msg: "order_2"]
2      [msg: "order_3"]  ← Consumer at offset 2
3      [msg: "order_4"]
...

Consumer commits offset to Kafka:
Group "payment-service", Partition 0, Offset: 3
→ Next read starts from offset 3
```

## API Design

### Producer API

```java
Producer<String, String> producer = new KafkaProducer<>(props);

ProducerRecord<String, String> record = new ProducerRecord<>(
    "user-events",           // topic
    "user_123",              // key (for partitioning)
    "{\"action\": \"click\"}" // value
);

// Async send
producer.send(record, (metadata, exception) -> {
    if (exception == null) {
        System.out.println("Sent to partition " + metadata.partition() + 
                          ", offset " + metadata.offset());
    }
});

// Sync send (wait for ack)
RecordMetadata metadata = producer.send(record).get();
```

### Consumer API

```java
Consumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("user-events"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    
    for (ConsumerRecord<String, String> record : records) {
        System.out.printf("key=%s, value=%s, partition=%d, offset=%d\n",
            record.key(), record.value(), record.partition(), record.offset());
        
        // Process message
        processMessage(record.value());
    }
    
    // Commit offsets (after processing)
    consumer.commitSync();
}
```

## Deep Dive Topics

### 1. Delivery Semantics

#### At-Most-Once (May Lose Messages)
```
1. Consumer reads message
2. Consumer commits offset
3. Consumer processes message ← If crash here, message lost!
```

#### At-Least-Once (May Duplicate)
```
1. Consumer reads message
2. Consumer processes message
3. Consumer commits offset ← If crash here, reprocess on restart
```

**Most Common**: At-least-once + idempotent processing

#### Exactly-Once (Complex)
```
Requirements:
1. Idempotent producer (retry doesn't create duplicates)
2. Transactional writes (atomic commit of offset + output)
3. Consumer reads from committed transactions only

Implementation:
producer.initTransactions();
producer.beginTransaction();
producer.send(record);
producer.sendOffsetsToTransaction(offsets, consumerGroupId);
producer.commitTransaction();
```

---

### 2. Replication & Leader Election

**In-Sync Replicas (ISR):**
```
Partition 0:
├─ Leader (Broker 1): Offset 100
├─ Follower (Broker 2): Offset 100 ← In-sync
└─ Follower (Broker 3): Offset 95 ← Out-of-sync (lagging)

ISR = [Broker 1, Broker 2]
```

**Leader Election:**
```
If Leader fails:
1. Zookeeper detects failure
2. New leader elected from ISR (e.g., Broker 2)
3. Clients automatically reconnect to new leader

acks=all: Wait for ALL ISR replicas to ack (slower, safer)
acks=1: Wait for leader only (faster, less durable)
acks=0: No wait (fastest, may lose data)
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
1. Rebalancing triggered
2. Partitions reassigned:
   ├─ Consumer 1 → Partition 0, 1
   ├─ Consumer 2 → Partition 2
   └─ Consumer 3 → Partition 3

During rebalancing: Messages NOT consumed (brief pause)
```

**Rebalance Protocol:**
1. Group coordinator (Kafka broker) detects change
2. All consumers stop consuming
3. Partition assignment computed
4. Consumers resume from last committed offset

---

### 4. Message Ordering Guarantees

**Within Partition**: Guaranteed (FIFO)
```
Producer → Partition 0: [msg1, msg2, msg3]
Consumer reads: [msg1, msg2, msg3] (same order)
```

**Across Partitions**: NOT guaranteed
```
Producer:
├─ msg1 (Partition 0, offset 10)
├─ msg2 (Partition 1, offset 5)
└─ msg3 (Partition 0, offset 11)

Consumer may see: msg2, msg1, msg3 (different order)
```

**Solution for Global Ordering**: Use 1 partition (limits throughput)

---

### 5. Compaction (Log Compaction)

**Use Case**: Keep only latest value per key (e.g., user profile updates)

```
Before Compaction:
Offset  Key      Value
0       user_1   {"name": "Alice"}
1       user_2   {"name": "Bob"}
2       user_1   {"name": "Alice Smith"}  ← Latest for user_1
3       user_3   {"name": "Charlie"}

After Compaction:
Offset  Key      Value
2       user_1   {"name": "Alice Smith"}  ← Kept
1       user_2   {"name": "Bob"}           ← Kept
3       user_3   {"name": "Charlie"}       ← Kept
```

**Config**: `cleanup.policy=compact`

---

## Scaling Strategies

### Horizontal Scaling (Add Brokers)
```
3 Brokers → 6 Brokers:
1. Add 3 new brokers
2. Rebalance partitions across all 6 brokers
3. Throughput doubles (each broker handles fewer partitions)
```

### Partition Scaling
```
Topic "events" (3 partitions) → (6 partitions):
1. Create new partitions
2. Messages hash to new partitions
3. Old messages remain in old partitions (not moved)

Limitation: Cannot decrease partitions!
```

---

## Failure Scenarios

### Broker Failure
```
Impact: Partitions with leader on failed broker unavailable
Mitigation:
- Replication (followers take over as leader)
- ISR ensures no data loss (if acks=all)
- Auto-recovery via Zookeeper election
```

### Consumer Failure
```
Impact: Assigned partitions not consumed
Mitigation:
- Rebalancing reassigns partitions to other consumers
- Resume from last committed offset (no message loss)
```

### Network Partition (Split Brain)
```
Problem: Cluster splits, two leaders elected
Mitigation:
- Zookeeper quorum (majority must agree)
- If no quorum, cluster becomes read-only
```

---

## Monitoring

**Key Metrics:**
```
- Under-replicated partitions (ISR < replication factor)
- Consumer lag (offset difference between producer and consumer)
- Broker disk usage (>80% = add storage/retention policy)
- Request latency (P99 < 10ms)
- Throughput (messages/sec, MB/sec)
```

**Alerts:**
```
P0: Under-replicated partitions > 0 (data loss risk)
P1: Consumer lag > 1M messages (processing falling behind)
P2: Disk usage > 80%
```

---

## Interview Tips

**Common Questions:**

**Q: "How does Kafka achieve high throughput?"**
- A: Sequential disk I/O (append-only log), zero-copy (sendfile), batching, compression

**Q: "What happens if consumer crashes before committing offset?"**
- A: On restart, consumer re-reads from last committed offset (at-least-once delivery, may duplicate)

**Q: "How do you ensure exactly-once semantics?"**
- A: Idempotent producer + transactional API + consumer reads only committed transactions

**Q: "Kafka vs RabbitMQ?"**
| Feature | Kafka | RabbitMQ |
|---------|-------|----------|
| Throughput | Very high (1M+/sec) | Medium (10K-100K/sec) |
| Message Ordering | Per partition | Per queue |
| Use Case | Event streaming, logs | Task queues, RPC |
| Persistence | Always (log) | Optional |

**Key Trade-offs:**
- Ordering vs Parallelism (1 partition = ordered but slow, many partitions = fast but unordered)
- Durability vs Latency (acks=all = slow but safe, acks=0 = fast but risky)
- Consumer lag vs throughput (commit frequently = slow, commit rarely = risk of reprocessing)
