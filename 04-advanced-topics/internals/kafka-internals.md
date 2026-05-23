---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Kafka Internals

## The Big Picture: A Newspaper Subscription Service at Industrial Scale

Think of Kafka as a **newspaper subscription service at industrial scale**.

- The **newspaper** is a **Topic** — a named stream of related events (e.g., "user-clicks", "payment-events").
- The newspaper has **sections** (Sports, Business, World) — these are **partitions**. Each section is printed and managed independently.
- The **printing press** logs everything permanently in a **commit log** — an ordered, append-only record. You can re-read any past edition.
- **Subscribers** are **consumer groups**. The New York Times subscriber gets their own copy of the paper. The Washington Post subscriber gets their own copy. They don't share or interfere with each other.
- Your **bookmark** in the paper is your **offset** — "I read up to page 47 yesterday; today I start from 47."

Unlike a traditional message queue (where a message disappears after it is read), Kafka keeps every edition of the newspaper in the archive indefinitely, subject to retention policy. Any subscriber can go back and re-read last Tuesday's paper.

---

## File Mindmap

```
Kafka Internals
├── Why It Exists
│   ├── Problem → traditional MQ: message gone after consume; one consumer group starves others; no replay
│   └── Physical limit → sequential disk I/O is 100× faster than random; Kafka exploits this for millions/sec throughput
├── Core Abstractions
│   ├── Topic → logical stream of events (e.g., "payment-events"); like a newspaper title
│   ├── Partition → ordered, immutable, append-only log within a topic; unit of parallelism
│   ├── Offset → consumer's bookmark within a partition; consumer controls when to advance
│   └── Consumer Group → each group gets independent copy of all messages; groups don't interfere
├── Partition Assignment Rule
│   ├── One partition → at most one consumer per group at a time
│   ├── More partitions than consumers → some consumers handle multiple partitions
│   └── More consumers than partitions → extra consumers are idle; scale by adding partitions
├── Delivery Guarantees
│   ├── At-most-once → auto-commit offset before processing; may lose on crash
│   ├── At-least-once → commit offset after processing; may duplicate on crash; most common
│   └── Exactly-once → transactional API (beginTransaction + commitTransaction) + idempotent producer; Kafka-to-Kafka only
├── ISR (In-Sync Replicas)
│   ├── ISR = leader + followers that are caught up (within replica.lag.time.max.ms)
│   ├── acks=0 → fire-and-forget; fastest; may lose data
│   ├── acks=1 → leader acks; lose data if leader dies before follower catches up
│   ├── acks=all → all ISR ack; strongest durability guarantee
│   └── min.insync.replicas=2 → at least 2 replicas must ack; prevents silent data loss
├── Why Kafka Is Fast
│   ├── Sequential I/O → append-only log; HDD sequential = 150 MB/s vs random = 1 MB/s
│   ├── Zero-copy → sendfile() syscall; data goes disk → kernel buffer → NIC; skips user space copy
│   ├── Page cache → OS caches hot log segments in RAM; recent messages served from memory
│   └── Batching → producer batches messages; linger.ms + batch.size trade latency for throughput
├── Retention vs Log Compaction
│   ├── Time/size retention → delete old segments after N days or N GB (event streaming use case)
│   └── Log compaction → keep only latest value per key; useful for change data capture / state rebuild
├── Hot Partition Problem
│   ├── Cause → all events for popular entity (celebrity user) route to same partition
│   ├── Salting → append random suffix to key: userId + "_" + random(0,10) → spreads load
│   └── Two-stage aggregation → aggregate per salt shard first, then merge
├── Consistent Hashing with Virtual Nodes
│   ├── Partition assignment uses murmur2 hash of key mod numPartitions
│   └── Virtual nodes → multiple ring positions per broker; ensures even partition distribution on broker add/remove
├── Trade-offs
│   ├── Pro: millions of events/sec, replay, fan-out to independent consumer groups
│   ├── Con: high operational complexity (brokers, partitions, consumer lag monitoring)
│   └── Con: ordering only within partition; cross-partition ordering requires external sequencing
└── Interview Angles
    ├── "How does Kafka achieve high throughput?" → sequential I/O + zero-copy + batching + page cache
    ├── "How do you handle duplicate messages?" → idempotent consumer with dedup table or upsert
    └── Follow-up: rebalancing storm → sticky partition assignment + incremental cooperative rebalancing
```

## 1. Storage & Scaling: Topics vs. Partitions

### Topic

A Topic is a logical category — like the name of a newspaper. It is the unit you publish to and subscribe from. Topics themselves do not scale; partitions do.

### Partitions — Parallel Printing Presses

A partition is an **ordered, immutable log of messages**. Think of each partition as a separate printing press producing its own sequence of pages. Messages within a single partition are **strictly ordered** — page 1 always comes before page 2 on that press.

However, across partitions, there is **no ordering guarantee**. The Sports section press and the Business section press operate independently. You cannot assume Sports page 5 was printed before Business page 5.

| Feature | Topic | Partition |
|---|---|---|
| Scaling | Logical grouping | Horizontal scaling (lives on different brokers) |
| Ordering | No total ordering across a topic | Strict ordering within a single partition |
| Unit of Parallelism | N/A | One partition = at most one consumer per consumer group |

**Interview tip:** If asked "how do you increase Kafka throughput?", the answer is **increase the number of partitions** — this allows more consumers to read in parallel.

### Offsets — Your Bookmark

Each message in a partition has a unique, monotonically increasing **offset**. If you read up to offset 47 yesterday, today you start from 47. Your position in the log is your own responsibility (stored in `__consumer_offsets` topic). This is why Kafka is called a "dumb broker, smart consumer" model.

---

## 2. Consumer Groups — Multiple Subscribers, Independent Copies

### Analogy

The New York Times subscriber gets their own copy of today's paper. The Washington Post subscriber gets their own independent copy. They read at their own pace and do not affect each other. Neither "uses up" the paper.

In Kafka:
- A **Consumer Group** is a labeled subscriber (e.g., "billing-service", "analytics-service").
- Multiple services can subscribe to the same topic simultaneously. Each group gets its own independent view — their own offset cursor.
- Within a group, partitions are divided among consumers. One consumer per partition maximum.

### Partition Assignment Rule

- **Partitions < Consumers in a group**: Some consumers sit idle (wasted).
- **Partitions > Consumers in a group**: Some consumers read from multiple partitions.
- **Partitions = Consumers**: Perfect parallel utilization.

### Rebalancing

When a consumer joins or leaves a group, the Group Coordinator triggers a **rebalance** — redistributing partitions among active consumers. This causes a **"stop-the-world" pause** in processing. Mention this in interviews the same way you would mention Garbage Collection pauses in JVM systems.

### Eager Rebalancing (Default Before Kafka 2.4)

The original rebalancing protocol:

1. Group Coordinator detects membership change (consumer joins, leaves, or heartbeat times out)
2. Group Coordinator sends **RevokAll** — every consumer revokes **all** its partitions
3. All consumers rejoin the group (send `JoinGroup` request)
4. Group Coordinator collects all `JoinGroup` responses, elects a **Group Leader** (first to respond)
5. Group Leader runs the partition assignment algorithm and sends result back via `SyncGroup`
6. All consumers receive their new partition assignments and resume consuming

**Stop-the-world duration**: steps 2-6 = typically 3-30 seconds for large consumer groups. During this window, **no consumer in the group processes any messages**.

**Rebalancing storm**: if consumers crash frequently (JVM GC pauses, rolling deployment, network hiccups), rebalances chain together. A new consumer joining at step 4 triggers another full rebalance. In extreme cases, a group of 100 consumers can spend more time rebalancing than consuming.

### Cooperative (Incremental) Rebalancing (Kafka 2.4+)

Introduced to eliminate the stop-the-world pause:

1. Group Coordinator detects membership change
2. **Round 1**: Group Leader assigns partitions and marks only the **delta** (changed assignments) as to-be-revoked. Consumers that need to revoke partitions do so; others keep consuming.
3. **Round 2**: Only revoked partitions are reassigned. Consumers that never lost their partitions **never stopped consuming**.

```
Eager (100 partitions, 10 consumers, 1 consumer added):
  All 100 partitions revoked → all consumers pause → 100 partitions reassigned
  Pause: 100% of throughput lost for 5-30 seconds

Cooperative (same scenario):
  ~9 partitions moved from existing consumers to new consumer
  Only those 9 partitions have a brief gap
  91 partitions: zero interruption
  Throughput loss: ~9%
```

**Configuration**:
```properties
partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

### Sticky Assignment

Both eager and cooperative protocols support **sticky assignment** — the assignor tries to keep consumers assigned to the same partitions they had before the rebalance. This minimizes state reload overhead for consumers that maintain per-partition state (e.g., aggregation windows, join buffers).

```properties
# For eager + sticky:
partition.assignment.strategy=org.apache.kafka.clients.consumer.StickyAssignor

# For cooperative + sticky (recommended):
partition.assignment.strategy=org.apache.kafka.clients.consumer.CooperativeStickyAssignor
```

### `__consumer_offsets` — The Offset Commit Topic

Kafka stores committed consumer offsets in an internal compacted topic named `__consumer_offsets` with 50 partitions. Every `consumer.commitSync()` or `consumer.commitAsync()` call writes to this topic.

- Each consumer group's offsets land in a specific partition determined by `hash(group_id) % 50`
- The Group Coordinator for a consumer group is the leader of that `__consumer_offsets` partition
- On consumer restart: fetch committed offset from `__consumer_offsets` → resume from that position

**Consumer lag**: the difference between the latest offset in the partition and the consumer's committed offset. Monitored via `kafka-consumer-groups.sh --describe` or JMX metric `records-lag-max`.

### When to Use Which Rebalancing Protocol

| Scenario | Recommendation |
|---|---|
| Rolling deployments of consumer services | Cooperative sticky — minimize throughput loss |
| Short-lived consumers (batch jobs) | Eager is fine — no long-lived state |
| Consumers with partition-local state (aggregations) | Cooperative sticky — preserve state locality |
| Consumer groups with > 50 consumers | Cooperative sticky — stop-the-world cost is too high |
| Kafka < 2.4 | Eager only (cooperative not available) |

---

## 3. Delivery Guarantees

### At-Least-Once — Newspaper Delivered, Sometimes Twice

The newspaper is delivered to your door. But occasionally, due to a delivery error, you get two copies. You are charged twice. This is **at-least-once**: no messages are lost, but duplicates are possible. Consumer must be **idempotent** to handle this safely.

Implementation: Process message → then commit offset. If the consumer crashes after processing but before committing, it re-reads and reprocesses.

### At-Most-Once — Fast but Lossy

Commit offset first, then process. If the consumer crashes after committing but before processing, the message is lost. Fastest, lowest overhead — acceptable for metrics or non-critical logging.

### Exactly-Once — Guaranteed One Delivery

Exactly one delivery to exactly one consumer, with no duplicates. This requires:
1. **Idempotent Producer**: attaches a Producer ID (PID) + Sequence Number to each batch. If the broker receives a duplicate, it discards it and still sends an ACK.
2. **Transactional API**: Atomic writes across multiple partitions. Messages are marked "Uncommitted" until `commitTransaction()` is called.
3. **Consumer Isolation Level**: `isolation.level=read_committed` — consumers only see committed messages.

**Important interview note:** Exactly-once is only guaranteed *within* the Kafka ecosystem (Topic A → Topic B). If your consumer writes to an external database (Postgres, MySQL), you must handle idempotency at the database level — e.g., a `UNIQUE` constraint or `UPSERT`.

| Semantic | Mechanism | Risk |
|---|---|---|
| At-Most-Once | Commit offset → Process | Data loss if crash between commit and process |
| At-Least-Once | Process → Commit offset | Duplicate processing if crash between process and commit |
| Exactly-Once | Idempotent Producer + Transactional API | Increased latency; coordination overhead |

---

## 4. Pull vs. Push Model

Kafka chose a **Pull-based** model for consumers (consumers pull data at their own pace). This is a deliberate HLD choice.

| Model | Push (e.g., RabbitMQ) | Pull (e.g., Kafka) |
|---|---|---|
| Control | Server controls the rate | Consumer controls the rate |
| Risk | Can overwhelm a slow consumer | No risk; consumer pulls at its own pace |
| Batching | Harder to batch | Excellent for batching (higher throughput) |
| Backpressure | Implicit (server must throttle) | Natural (consumer just pulls slower) |
| Consumer State | Stored by broker | Consumer manages its own offsets |

A slow consumer in Kafka creates **Consumer Lag** — it falls behind — but does not exert direct backpressure on the producer or affect other consumer groups.

---

## 5. Availability & Durability: The ISR Model

### Leader/Follower — The Authoritative Printing Press

For each partition, one broker is the **Leader** — the authoritative printing press. All reads and writes for that partition go through the leader. Other brokers that have replicated that partition's data are **Followers** — they make backup copies.

### In-Sync Replicas (ISR)

The **ISR** is the subset of followers that are "caught up" with the leader. If the leader fails, a new leader is elected from the ISR — ensuring no data loss.

### The `acks` Setting — How Many Presses Must Confirm?

| Setting | Meaning | Latency | Durability |
|---|---|---|---|
| `acks=0` | Fire and forget. No confirmation. | Lowest | Lowest — data loss likely |
| `acks=1` | Only the Leader must acknowledge | Medium | Moderate — loss if leader fails before replication |
| `acks=all` | All ISRs must acknowledge | Highest | Highest — strongest guarantee |

`min.insync.replicas` sets the minimum number of replicas that must be healthy for a write to succeed. If fewer are available, the write fails.

### Unclean Leader Election

If all ISRs are down, should Kafka allow a non-synced follower to become leader?
- **Yes**: System stays available but may lose data (Availability > Consistency).
- **No** (default): System becomes unavailable until a synced replica returns (Consistency > Availability).
This is a CAP theorem trade-off discussion point.

---

## 6. Why is Kafka Fast?

Four performance pillars explain Kafka's throughput:

1. **Sequential I/O**: Kafka appends messages to the end of a log file. Hard drives and SSDs are dramatically faster at sequential writes than random access. Unlike a B-tree database that does random page writes, Kafka is write-append only.

2. **Zero-Copy Optimization**: Kafka uses the OS `sendfile()` syscall to move data directly from the disk page cache to the network buffer, bypassing the application/JVM heap entirely. Data never touches user space on the read path.

3. **Page Cache**: Kafka relies on the Linux kernel's page cache. Recently written data is automatically cached in RAM. Consumers reading recent messages are effectively reading from memory, not disk.

4. **Batching**: Producers and consumers group messages into batches. Fewer network round-trips, more data per trip.

**Interview answer to "Why is Kafka faster than a traditional DB for streaming?"**: Sequential I/O (append-only log) vs. B-tree random writes, plus Zero-Copy, plus Page Cache exploitation.

---

## 7. Retention vs. Log Compaction

### Time-based / Size-based Retention

- Delete data older than X days.
- Delete data once the log exceeds Y gigabytes.

Standard for event streams where you only care about recent data (e.g., last 7 days of metrics).

### Log Compaction — The "Current State" Mode

Log Compaction keeps only the **latest value for each key**. It is like a dictionary that only stores the most recent definition — previous definitions are discarded.

**Use case**: "What is user 123's current account balance?" You do not need the full history of every cent moved. You need the latest write for each key. Log Compaction gives you that: a compacted topic that you can replay to reconstruct current state.

**Interview tip**: Distinguish Log Compaction (current state per key) from time-based retention (full event history). They solve different problems.

---

## 8. Advanced Scenarios

### Hot Partitions (Key Skew)

**Problem**: You partition by `user_id`. One celebrity user generates 1000x more events than average. One partition is overwhelmed; one broker is saturated; Consumer Lag spikes.

**Solutions**:

1. **Salting the Key**: Use `user_id + random_suffix` (e.g., `user123_1`, `user123_2`) to spread load. Trade-off: you lose strict ordering for that user; consumer must aggregate across partitions.

2. **Two-Stage Aggregation**: Round-Robin in Stage 1 for partial aggregation → single "aggregator" partition in Stage 2 for final total. Trade-off: latency and complexity increase.

3. **Dynamic Re-Partitioning**: Detect hot keys at the producer and route to a dedicated high-traffic topic. Trade-off: complex stateful management.

### Partition Count Trade-offs

- **More partitions** = higher throughput, more consumer parallelism.
- **More partitions** = more leader elections, more open file handles, higher rebalance cost.
- You can *increase* partition count after creation, but it **breaks key-based ordering**: if `hash(key) % 10` becomes `hash(key) % 20`, the same key routes to a different partition.

### System-Wide Ordering (Across All Partitions)

The only way to guarantee ordering across an entire topic is to set `partitions = 1`. This is a scaling bottleneck — state it explicitly in interviews and accept the trade-off.

---

## 9. Kafka vs. RabbitMQ

| Feature | Apache Kafka | RabbitMQ |
|---|---|---|
| Model | Pull-based (Dumb Broker, Smart Consumer) | Push-based (Smart Broker, Dumb Consumer) |
| Storage | Durable log — messages stay after reading | Ephemeral queue — messages deleted after ACK |
| Ordering | Guaranteed within a partition | Guaranteed within a queue |
| Throughput | Extreme (millions/sec, GBs/sec) | Moderate (KBs–MBs/sec) |
| Replay | Yes — move offset back in time | No — once consumed, gone |
| Use Case | Event streaming, log aggregation, metrics | Task queues, request/response, complex routing |

**When to choose RabbitMQ**: Complex routing (header/fanout exchanges), individual message acknowledgments per message, or when you have no need to replay historical data.

---

## 10. Consistent Hashing (Distributed Key Distribution)

Kafka uses key-based hashing to route messages to partitions. Distributed systems more broadly use Consistent Hashing to distribute data across nodes without massive reshuffling when the cluster changes.

### Standard Modulo Hashing — The Problem

```
index = hash(key) % n
```

If `n` changes (node added or removed), nearly every key remaps. Result: a "cache miss storm" or massive data migration.

### Consistent Hashing — The Solution

1. Map a range of integers (e.g., 0 to 2^32 - 1) into a circular ring.
2. Hash each server's ID to place it at a position on the ring.
3. Hash each key to a position on the ring.
4. Assign the key to the first server encountered clockwise.

**Adding a node**: It only "steals" keys from its immediate clockwise neighbor. All other keys remain unaffected.

**Removing a node**: Its keys fall to the next clockwise node. Only those keys are remapped.

### Virtual Nodes — Solving Uneven Distribution

Placing each server once on the ring can cause hotspots (one server owns a large arc). **Virtual nodes** place each server multiple times using different hash functions (e.g., Server1_A, Server1_B, Server1_C). Benefit: even distribution; more powerful servers can be given more virtual nodes.

| Feature | Standard Hashing | Consistent Hashing |
|---|---|---|
| Server Changes | Nearly 100% of keys remap | Only K/n keys remap |
| Scalability | Poor | Excellent |
| Load Balancing | Depends on hash quality | Tunable via virtual nodes |
| Lookup Complexity | O(1) | O(log N) with sorted structure |

---

## 11. Consistency Levels in Distributed Systems

Relevant when comparing Kafka's durability model to databases like Cassandra.

### The R + W > N Rule

- **N**: Replication Factor (total copies of data)
- **W**: Number of replicas that must acknowledge a write
- **R**: Number of replicas that must respond to a read

If `R + W > N`, strong consistency is guaranteed (at least one node in the read set has the latest write).

| Configuration (N=3) | Consistency | Trade-off |
|---|---|---|
| W=1, R=1 | Eventual | Fastest; stale reads possible |
| W=QUORUM(2), R=QUORUM(2) | Strong | Best balance for most apps |
| W=ALL(3), R=1 | Strong | Best for write-once, read-many |

### Eventual Consistency Healing Mechanisms

- **Hinted Handoff**: Coordinator stores missed writes for a downed node and replays them when the node recovers.
- **Read Repair**: During a read, coordinator compares replicas and repairs stale nodes.
- **Anti-Entropy (Merkle Trees)**: Background comparison of data hashes to identify and sync diverged rows without sending entire datasets.

---

## Interview Summary Checklist

When answering Kafka HLD questions, weave in these keywords to demonstrate senior-level understanding:

| Term | What it signals |
|---|---|
| **Consumer Lag** | How far behind a consumer group is (the lag metric) |
| **Rebalance Storm** | Consumers constantly joining/leaving, halting processing |
| **Backpressure** | Pull model naturally handles slow consumers without overwhelming them |
| **Fan-out** | One topic consumed by multiple independent consumer groups |
| **ISR** | In-Sync Replicas — the safety net for leader failover |
| **Zero-Copy** | `sendfile()` syscall for disk-to-network without JVM heap involvement |
| **Log Compaction** | Current state per key, not full history |
| **Idempotent Producer** | PID + sequence numbers to prevent duplicate writes |
