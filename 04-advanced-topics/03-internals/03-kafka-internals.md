> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Kafka internals — how the distributed streaming platform achieves high throughput, durability, and exactly-once semantics via sequential I/O, zero-copy, and consumer-pull model.
>
> **Key topics:**
> - Architecture: Topics → Partitions (ordered append-only log) → Consumer Groups (each gets its own offset cursor)
> - Write path: producer → leader partition → ISR (in-sync replicas) → ack to producer; acks=all for no data loss
> - Zero-copy: sendfile() syscall delivers data from page cache to network socket without copying to userspace → 2× throughput
> - Consumer offset: Kafka stores offsets in __consumer_offsets topic; consumers commit after processing; enables replay
> - Log compaction: per-key keeps only latest value; enables Kafka as a changelog/compacted state store
> - Exactly-once semantics: idempotent producer (deduplication per session) + transactional API (atomic cross-partition writes)
> - Rebalancing: consumer joins/leaves trigger partition reassignment; Cooperative Sticky assignor minimizes disruption
>
> **Key takeaway:** Kafka's throughput comes from sequential disk I/O (log append) + zero-copy networking + consumer pull model — all three must be understood to explain why it outperforms traditional message queues by 10×.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Kafka Internals

> A distributed streaming platform built on an append-only log. Producers write, consumers read, brokers store; the log is durable, replayable, and partitioned for parallelism.

---

## 1. Why Kafka Exists

**Question**: A traditional message queue (RabbitMQ, ActiveMQ) deletes a message after it's consumed. But you need to (a) replay events from 3 days ago, (b) let three independent services each consume the full stream without interfering, (c) handle 1M events/sec. How?

**Physical constraint**: Traditional MQs are push-based with broker-side ack tracking; the broker holds per-consumer state. Sequential disk I/O is ~150 MB/s on HDD vs ~1 MB/s for random I/O. RabbitMQ throughput tops out at tens of thousands/sec. Kafka exploits sequential I/O on the write path and gives consumers control of their own position — they just store their offset.

**Minimal solution**: An append-only log file on disk. Producers append at the end; consumers track their own position (offset). Any number of consumers can read the same log independently. Sequential writes achieve near-disk-bandwidth throughput.

**Production generalization**: Apache Kafka is a distributed, replicated, partitioned append-only log. Tunable durability (acks), tunable consistency (replication factor vs ISR), and rich ecosystem (Kafka Connect, Kafka Streams, ksqlDB). The 2014 LinkedIn design paper and the original Kafka at LinkedIn blog post remain the canonical references.

---

## 2. Core Concepts

| Concept | Definition |
|---------|------------|
| **Topic** | Logical stream of events (e.g., `payment-events`) — the unit producers publish to and consumers subscribe to |
| **Partition** | Ordered, immutable, append-only log within a topic. Unit of parallelism. |
| **Offset** | 64-bit monotonically increasing ID of a message within a partition. Consumer's "bookmark." |
| **Consumer Group** | Labeled subscriber (e.g., `billing-service`). Each group gets an independent copy of every message. |
| **ISR** | In-Sync Replicas — leader + followers caught up within `replica.lag.time.max.ms` |
| **Leader / Follower** | One broker is the authoritative partition leader; others replicate |
| **Broker** | Single Kafka server hosting some partitions |
| **__consumer_offsets** | Internal compacted topic (50 partitions) that stores consumer group offsets |

**Partition assignment rule** (within a consumer group):
- One partition → at most one consumer in a group at a time
- More partitions than consumers → some consumers handle multiple partitions
- More consumers than partitions → extras sit idle
- Add partitions to scale throughput (cannot reduce without reassignment)

**Ordering guarantee**: strict within a single partition. None across partitions of the same topic. To get total order, set `num.partitions=1` (rare; defeats parallelism).

---

## 3. Why Kafka Is Fast

Four engineering choices explain Kafka's throughput:

### 3.1 Sequential I/O

Kafka appends messages to the end of a log file. HDD sequential throughput is ~150 MB/s; SSD ~500 MB/s. Random I/O is ~100 IOPS (HDD) to 10K IOPS (SSD). B-tree databases do random page writes on every update; Kafka is append-only. On modern disks, sequential append can saturate the device.

### 3.2 Zero-Copy

Naïve network send: `disk → kernel buffer → user buffer (JVM heap) → kernel socket buffer → NIC`. Two copies and four context switches.

Kafka uses the OS `sendfile()` syscall: `disk → kernel buffer → NIC`. One copy, two context switches. Data never enters the JVM heap or user space on the read path. This alone gives ~2× throughput improvement and drastically reduces GC pressure.

### 3.3 Page Cache

Kafka relies on the Linux kernel's page cache instead of an in-process cache. Recently written data is automatically cached in RAM. Consumers reading recent messages are reading from memory, not disk. On restart, the OS re-warms the page cache from the same log files — no separate cache rebuild.

### 3.4 Batching

Producers and consumers group messages into batches. Fewer network round-trips; better compression ratio. `linger.ms` and `batch.size` trade latency for throughput.

**Interview answer**: "Why is Kafka faster than a traditional DB for streaming?" — Sequential I/O (append-only log) vs B-tree random writes, plus zero-copy, plus page-cache exploitation, plus batching.

---

## 4. Write Path

```
Producer sends record
  → ProducerRecord keyed with key (optional) and value
  → Partitioner: hash(key) % numPartitions  (round-robin if no key)
  → Record appended to leader partition's log
  → Leader replicates to all followers in ISR
  → When min.insync.replicas followers ACK → leader sends ACK to producer
  → Producer proceeds (or retries on failure)
```

**Key configs**:

| Config | Effect |
|--------|--------|
| `acks=0` | Fire-and-forget; producer doesn't wait; fastest; may lose data |
| `acks=1` | Leader acks after local write; loses data if leader dies before replication |
| `acks=all` | All ISR acks; strongest durability; requires `min.insync.replicas >= 2` |
| `min.insync.replicas=2` | At least 2 replicas must ACK; prevents silent data loss if only 1 replica is alive |
| `enable.idempotence=true` | Producer attaches PID + sequence number; broker dedupes retries |
| `compression.type=snappy|lz4|zstd` | Compress batches; reduces network and disk I/O |

**Unclean leader election** (`unclean.leader.election.enable`):
- `false` (default): if all ISR are down, the partition becomes unavailable until an ISR replica returns. Consistency > availability.
- `true`: a non-ISR replica may be promoted. Available, but may lose recent writes. Availability > consistency.

---

## 5. Consumer Mechanics

### Offset Commit

Consumers commit their current offset to the internal `__consumer_offsets` topic (50 partitions; hash(group_id) % 50). The Group Coordinator is the leader of that partition.

- On restart: fetch committed offset → resume from there
- `consumer.commitSync()` — synchronous, blocks until ACK
- `consumer.commitAsync()` — fire-and-forget, better throughput
- `enable.auto.commit=true` — periodic background commit; risk of duplicate processing

**Consumer lag** = `latest_offset_in_partition - committed_offset`. Monitor via `kafka-consumer-groups.sh --describe` or JMX `records-lag-max`.

### Partition Assignment & Rebalancing

When a consumer joins or leaves, the Group Coordinator triggers a rebalance.

**Eager rebalancing** (default before 2.4):
1. Coordinator sends `RevokeAll` to all consumers
2. All consumers rejoin via `JoinGroup`
3. Group Leader computes new assignment
4. All consumers receive new assignments via `SyncGroup`
5. Pause duration: typically 3-30 seconds for large groups

**Cooperative (incremental) rebalancing** (2.4+):
1. Coordinator computes the delta (which consumers lose/gain which partitions)
2. Only the affected partitions are revoked and reassigned
3. Consumers that kept their partitions never paused

```
Eager (100 partitions, 10 consumers, 1 added):
  All 100 revoked → all 10 consumers pause → reassigned
  Pause: 100% of throughput lost for 5-30 seconds

Cooperative sticky (same):
  ~9 partitions moved
  Only those 9 pause briefly
  91 partitions: zero interruption
  Throughput loss: ~9%
```

**Recommended**: `partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor`.

**Rebalancing storm**: if consumers crash frequently (JVM GC pause, network hiccup, rolling deploy), rebalances chain together. A consumer that misses heartbeat triggers a rebalance; while rebalancing, another consumer's processing slows, misses its own heartbeat, triggers another rebalance. Mitigation: cooperative sticky + `session.timeout.ms=30s` + `max.poll.interval.ms` tuned to actual processing time.

### Partition Assignment Strategies

| Strategy | Behavior | When to Use |
|----------|----------|-------------|
| **RangeAssignor** (default eager) | Assigns contiguous partition ranges per topic | Rarely optimal |
| **RoundRobinAssignor** | Spreads partitions evenly across consumers | Balanced load |
| **StickyAssignor** (eager) | Preserves previous assignment where possible | Consumers with per-partition state |
| **CooperativeStickyAssignor** (2.4+) | Incremental + sticky | **Production default** |

---

## 6. Delivery Guarantees

| Semantic | Mechanism | Risk |
|----------|-----------|------|
| **At-most-once** | Auto-commit offset before processing | Data loss on crash between commit and process |
| **At-least-once** | Process → then commit | Duplicate processing on crash between process and commit |
| **Exactly-once** | Idempotent producer + transactional API | Increased latency; coordination overhead |

### Exactly-Once (Kafka-to-Kafka)

Three components:

1. **Idempotent producer** (`enable.idempotence=true`): producer attaches Producer ID (PID) + sequence number to each batch. If the broker receives a duplicate sequence, it discards and ACKs anyway. Deduplication is per `(PID, partition)`.

2. **Transactional API**: atomic writes across multiple partitions. Messages are marked "uncommitted" inside the transaction log. `commitTransaction()` marks them visible to read-committed consumers.

3. **Consumer isolation level** (`isolation.level=read_committed`): consumers only see committed messages.

```java
// Idempotent producer (dedupes within a session)
properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
// Transactional producer (atomic across partitions)
properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-service-tx-1");

producer.initTransactions();
try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("orders", orderId, orderJson));
    producer.send(new ProducerRecord<>("audit", orderId, auditJson));
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Important interview note**: exactly-once is only guaranteed *within* the Kafka ecosystem (Topic A → Topic B). If your consumer writes to an external database (Postgres, MySQL), you must handle idempotency at the DB level — e.g., a `UNIQUE` constraint, an `UPSERT`, or a deduplication table.

---

## 7. Retention vs Log Compaction

### Time / Size Retention

Delete data older than `retention.ms` (default 7 days) or when the log exceeds `retention.bytes`. Standard for event streams where only recent data matters (metrics, logs).

### Log Compaction

Keep only the **latest value for each key**. Compactor runs in background; merges segments and drops older values whose keys are still present.

Use cases:
- **Change data capture (CDC)**: every change to a row is published; downstream needs only the latest value to materialize state
- **Compacted state store**: re-read the entire topic on restart to rebuild in-memory state (Kafka Streams uses this for KTables)
- **Configuration**: each service gets the latest config without polling

**Interview tip**: distinguish log compaction (current state per key) from time-based retention (full event history). They solve different problems and can be combined (`retention.ms` deletes whole segments; `cleanup.policy=compact` keeps latest per key within surviving segments).

---

## 8. Advanced Topics

### Hot Partitions (Key Skew)

**Problem**: You partition by `user_id`. One celebrity generates 1000× more events than average. One partition is overwhelmed; one broker is saturated; consumer lag spikes.

**Solutions**:

1. **Salting the key**: `user_id + "_" + random(0,9)` → spreads load across 10 partitions. Trade-off: lose strict ordering for that user; consumer must aggregate across partitions.

2. **Two-stage aggregation**: round-robin in Stage 1 for partial aggregation → single aggregator partition in Stage 2 for the final total. Trade-off: latency and complexity.

3. **Dynamic re-partitioning**: detect hot keys at the producer and route to a dedicated high-traffic topic. Trade-off: complex stateful management.

### Partition Count Trade-offs

- **More partitions** = higher throughput, more consumer parallelism
- **More partitions** = more leader elections, more open file handles, higher rebalance cost
- You can increase partition count after creation, but it **breaks key-based ordering**: `hash(key) % 10` becoming `hash(key) % 20` re-routes every key

**Rule of thumb**: `num.partitions ≈ target_throughput_MB/s / 10 MB/s per partition`.

### System-Wide Ordering

The only way to guarantee ordering across an entire topic is `partitions=1`. State this explicitly in interviews and accept the trade-off.

### Unclean Leader Election

If all ISR are down, should Kafka allow a non-synced follower to become leader? See §4. CAP theorem trade-off: availability vs consistency.

---

## 9. Real-World Usage

| Use case | Topology | Notes |
|----------|----------|-------|
| **Streaming CDC** | Debezium → Kafka → consumer sinks | Log compaction for materialization |
| **Event sourcing** | Service publishes events; aggregates read topic | All state reconstructable from log |
| **Log aggregation** | Fluentd/Vector → Kafka → Elasticsearch / S3 | Decouples producers from sinks |
| **Metrics pipeline** | Producers → Kafka → Kafka Streams → TSDB | Exactly-once to TSDB |
| **Async messaging** | Order service → Kafka → fulfillment, email, analytics | Fan-out via consumer groups |
| **Dead letter handling** | Failed consumer → DLQ topic → manual replay | Use `retries` + `delivery.timeout.ms` first |

**Reference architecture** (LinkedIn 2024): 7+ trillion messages/day, 4M+ topics. The original 2014 design scaled from 10K msgs/sec to 10M+ msgs/sec on commodity hardware.

---

## 10. Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **Throughput** | Millions of events/sec; GB/sec sustained | Operational complexity (brokers, partitions, lag monitoring) |
| **Durability** | Configurable (acks=all + RF=3) | Tunable — wrong config = silent data loss |
| **Replay** | Move offset back in time; full event log retained | Retention is finite; cold storage is your problem |
| **Fan-out** | N independent consumer groups, no interference | Per-group lag is your monitoring problem |
| **Ordering** | Strict within partition | None across partitions; need external sequencing for global order |
| **Ecosystem** | Connect, Streams, Schema Registry, ksqlDB | New operational surface area; KRaft still maturing |

---

## 11. Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| **Broker death** | Leader unavailable for some partitions; producers get `NotLeaderForPartition` | Producers retry; controller re-elects leader from ISR (~few seconds) |
| **ISR shrink** | `acks=all` writes start failing; `min.insync.replicas` not met | Add brokers; check disk and network on the lagging replica |
| **Rebalance storm** | Throughput oscillating; logs full of rebalance events | Cooperative sticky; increase `session.timeout.ms`; reduce GC pauses |
| **Hot partition** | One consumer group stuck; one broker disk I/O saturated | Salt the key; two-stage aggregation; consider splitting the entity |
| **Controller failover** | Brief unavailability of admin operations (topic create) | KRaft mode (3.3+) reduces to ms; ZK mode can take 30s |
| **Consumer never commits** | Lag grows unbounded; consumer appears stuck | Check `max.poll.records`; long processing time → heartbeat missed |
| **Disk fill on broker** | Producer `RecordTooLargeException` or `TimeoutException` | Reduce retention; tiered storage (KIP-405); add brokers |
| **ZooKeeper outage** (pre-KRaft) | All Kafka admin operations fail; cluster effectively read-only | Migrate to KRaft mode; ZK ensemble 5+ nodes across failure domains |

---

## 12. Performance

### Producer Tuning

| Config | Effect | Default |
|--------|--------|---------|
| `linger.ms` | Wait this long to fill a batch | 0 (no waiting) |
| `batch.size` | Max batch size in bytes | 16 KB |
| `compression.type` | `none`, `gzip`, `snappy`, `lz4`, `zstd` | none |
| `acks` | 0 / 1 / all | 1 |
| `buffer.memory` | Total memory for batching | 32 MB |
| `max.in.flight.requests.per.connection` | Pipelined batches; idempotence allows 5 | 5 |
| `delivery.timeout.ms` | Total time before giving up | 120000 |

### Consumer Tuning

| Config | Effect | Default |
|--------|--------|---------|
| `fetch.min.bytes` | Wait for this much data per fetch | 1 |
| `fetch.max.wait.ms` | Max wait for `fetch.min.bytes` | 500 |
| `max.poll.records` | Max records per `poll()` call | 500 |
| `session.timeout.ms` | Heartbeat miss → rebalance | 10000 (10s) |
| `max.poll.interval.ms` | Max time between polls before considered dead | 300000 (5min) |
| `auto.offset.reset` | `earliest` or `latest` when no committed offset | latest |

### Throughput Math

- HDD sequential: 150 MB/s
- A 1 KB message at 100 MB/s effective write → 100K msgs/sec per disk
- 3 brokers × 4 disks × 100K = 1.2M msgs/sec per cluster
- Network is usually the bottleneck before disk

---

## 13. Implementation Patterns

### Java — Idempotent + Transactional Producer

```java
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092,broker2:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-svc-tx-1");
props.put(ProducerConfig.ACKS_CONFIG, "all");
props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
producer.initTransactions();

try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("orders", orderId, orderJson));
    producer.send(new ProducerRecord<>("audit-log", orderId, auditJson));
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
    throw e;
}
```

### Java — Consumer with Manual Commit

```java
Properties props = new Properties();
props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "broker1:9092");
props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-service");
props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
    "org.apache.kafka.clients.consumer.CooperativeStickyAssignor");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(List.of("orders"));

try {
    while (running) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
            processOrder(record);  // may throw
        }
        consumer.commitSync();   // commit only after successful processing
    }
} finally {
    consumer.close();
}
```

### Kafka Streams — KTable from Compacted Topic

```java
StreamsBuilder builder = new StreamsBuilder();

// KTable reads the entire compacted topic to materialize current state
KTable<String, UserProfile> users = builder.table(
    "user-profiles",
    Consumed.with(Serdes.String(), userProfileSerde),
    Materialized.as("user-profiles-store")
);

KStream<String, Order> orders = builder.stream("orders");

// Join stream to table — uses the materialized state
orders.join(users, (order, user) -> enrichOrder(order, user))
      .to("enriched-orders");
```

---

## Quick Revision

- Kafka is a distributed, partitioned, replicated append-only log
- Topics are split into partitions; partitions are the unit of parallelism
- Consumer groups share the work; each group gets its own offset cursor
- Writes are append-only → sequential I/O → near-disk-bandwidth throughput
- `sendfile()` syscall gives zero-copy from disk to NIC (no JVM heap involvement)
- Page cache in Linux kernel caches hot log segments; consumers read from memory
- ISR + acks=all + min.insync.replicas=2 = strong durability
- Cooperative sticky rebalancing minimizes disruption on consumer churn
- Exactly-once requires idempotent producer + transactional API + read_committed consumer
- Log compaction keeps latest value per key; use for CDC, KTables, config
- Hot partitions: salt the key or two-stage aggregation
- KRaft mode (3.3+) replaces ZooKeeper for metadata; ms-level controller failover

---

## See Also

- [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md) — partitioning across brokers
- [04-advanced-topics/internals/consensus-protocols.md](consensus-protocols.md) — KRaft details, ZAB vs Raft
- [04-advanced-topics/internals/index-structures.md](index-structures.md) — log structure as a sequential-write index
- [04-advanced-topics/internals/zookeeper-internals.md](zookeeper-internals.md) — pre-KRaft dependency
- [04-advanced-topics/event-driven-architecture.md](../event-driven-architecture.md) — Kafka as backbone
- [04-advanced-topics/stream-processing.md](../stream-processing.md) — Kafka Streams, ksqlDB

---

## Interview Questions Asked

**Q: How does Kafka achieve high throughput?**

A: Four engineering choices: (1) Sequential I/O — append-only log, no random page writes like B-tree; HDD sequential ~150 MB/s, SSD ~500 MB/s. (2) Zero-copy — `sendfile()` syscall moves data from disk page cache directly to NIC socket, bypassing the JVM heap; ~2× throughput and no GC pressure. (3) Page cache — Linux kernel caches hot log segments in RAM; recent reads served from memory. (4) Batching — `linger.ms` + `batch.size` amortize network round-trips and improve compression ratio.

**Q: How do you achieve exactly-once semantics in Kafka?**

A: Three components. (1) Idempotent producer (`enable.idempotence=true`) attaches PID + sequence number; broker dedupes retries. (2) Transactional API (`initTransactions()` + `beginTransaction()` + `commitTransaction()`) makes writes across multiple partitions atomic. (3) Consumer with `isolation.level=read_committed` only sees committed messages. Important caveat: this guarantees exactly-once *within Kafka* (Topic A → Topic B). If the consumer writes to an external database, you still need idempotency at the DB layer (UNIQUE constraint, UPSERT, dedup table).

**Q: What is a hot partition and how do you fix it?**

A: A hot partition is one partition receiving disproportionate traffic because of a skewed key (e.g., partitioning by `user_id` and one celebrity generates 1000× the average). The owning broker saturates disk, network, or consumer. Fixes: (1) **salt the key** — `user_id + "_" + random(0,9)` spreads across N partitions; trade-off is loss of strict per-user ordering and consumer must aggregate. (2) **Two-stage aggregation** — round-robin in stage 1 for partials, single aggregator partition in stage 2 for the final total. (3) **Dynamic re-partitioning** — detect hot keys at the producer and route to a dedicated topic. The right answer is "salt the key unless you truly need per-entity ordering."

**Q: When do you use Kafka vs RabbitMQ?**

A: Kafka: event streaming, log aggregation, CDC, metrics pipeline, anything that needs replay or fan-out to many independent consumer groups. Sequential writes give millions/sec; retention is configurable. RabbitMQ: complex routing (header/fanout exchanges), per-message acknowledgments, request/response, task queues where you don't need replay. Throughput is tens of thousands/sec. Pick Kafka when you need a durable log; pick RabbitMQ when you need a smart broker with flexible routing.

**Q: Why did Kafka remove ZooKeeper?**

A: At 100K+ partitions, ZK's in-memory metadata model became a bottleneck. Controller restart required re-reading all metadata through ZK and re-pushing it to brokers — minutes of unavailability. KRaft (Kafka 3.3+) stores all metadata in an internal Kafka topic `__cluster_metadata` replicated via Raft. The new controller already has the full log → ms-level failover. Operational win: one system instead of two (no separate ZK JVM, GC tuning, quorum sizing). Migration: dual-write period, then transfer metadata, then decommission ZK.
