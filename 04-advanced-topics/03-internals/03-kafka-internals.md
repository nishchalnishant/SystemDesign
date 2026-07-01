> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How Apache Kafka achieves 10M+ messages/sec throughput and the production-grade reliability guarantees that separate it from a simple queue.
>
> **Key topics:**
> - **Append-only log + sequential I/O** — why Kafka is fast
> - **Partitions, offsets, consumer groups** — how it scales
> - **Replication (ISR), acks, min.insync.replicas** — how it stays durable under failure
> - **Leader election** — how it recovers automatically
> - **Log compaction** — how Kafka acts as a state store
> - **Exactly-once semantics** — idempotent producers + transactional API
> - **Zero-copy + page-cache batching** — why commodity hardware is enough
>
> **Key takeaway:** Kafka is fast because it exploits sequential disk I/O and an append-only log. It is reliable because ISR + acks + min.insync.replicas give you tunable durability. Understanding the full acks/ISR model is the most-tested senior follow-up in any Kafka interview question.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, message-brokers]
---
# Apache Kafka Internals

---

## Why Kafka is Fast: Sequential I/O

Random disk writes (what an RDBMS does when updating an index) cap out at ~150 writes/sec on spinning disk. Sequential writes (appending to the end of a file) hit 100–150 MB/sec on the same hardware.

Kafka exploits this by treating each partition as an **append-only log**: new messages are always written at the end, old messages are never modified. The OS page cache absorbs bursts, and the broker flushes to disk in large sequential batches. Combined with **zero-copy** (`sendfile()` syscall), the broker can deliver messages to consumers directly from the page cache without copying data through user space — this alone doubles throughput.

---

## Topics, Partitions, Offsets

A **Topic** is a logical stream. It is divided into **Partitions** (immutable, ordered logs stored on individual brokers). Messages in a partition are assigned monotonically increasing **Offsets**.

```
Topic: payments
  Partition 0 (Leader: Broker 1) →  [0] [1] [2] [3] ...
  Partition 1 (Leader: Broker 2) →  [0] [1] [2] ...
  Partition 2 (Leader: Broker 3) →  [0] [1] ...
```

**Key routing rule:** If a producer sets a message key, Kafka routes `hash(key) % num_partitions`. All events for the same key land on the same partition → strict per-key ordering.

**Consumer Groups:** Kafka assigns each partition to exactly one consumer in a group. `N` partitions and `N` consumers → perfect parallelism. `N` consumers but only 1 partition → `N-1` sit idle. Consumers commit offsets back to Kafka; on restart they resume from the committed offset.

---

## Replication: ISR + Acks + min.insync.replicas

This is the section most candidates skip, and the section interviewers probe hardest.

### In-Sync Replicas (ISR)

Each partition has one **leader** and zero or more **followers**. Followers continuously fetch from the leader. The **ISR** is the set of replicas that are caught up to the leader within `replica.lag.time.max.ms` (default: 10 seconds).

```
Partition 0:
  Leader  → Broker 1  (always in ISR)
  Replica → Broker 2  (in ISR — lag < 10s)
  Replica → Broker 3  (lagging — removed from ISR)
```

A follower is kicked out of ISR if it falls behind. It is re-added once it catches up. The ISR set is stored in ZooKeeper (or KRaft in newer versions).

### Producer acks

| `acks` value | Meaning | Durability | Throughput |
|---|---|---|---|
| `0` | Fire-and-forget — no acknowledgement | Lowest | Highest |
| `1` | Leader writes to its local log and acks | Medium | Medium |
| `all` (or `-1`) | Leader waits for all **ISR** replicas to confirm | Highest | Lowest |

`acks=all` alone is not enough. If the ISR has shrunk to 1 (the leader itself), `acks=all` gives no more durability than `acks=1`.

### min.insync.replicas

This broker/topic config sets the minimum number of replicas that must acknowledge a write before the leader considers it committed. If the ISR size falls below this threshold and `acks=all`, the producer receives a `NotEnoughReplicasException` — this is intentional. It forces the producer to retry or fail fast rather than silently producing into a cluster that can't guarantee durability.

**Production default pattern:**
```
replication.factor      = 3
min.insync.replicas     = 2
acks                    = all
```

This means: even if one broker dies, writes still succeed (ISR = 2, meets minimum). If two brokers die, writes fail loudly — better to surface the outage than lose data.

---

## Leader Election

When a partition leader dies, Kafka must elect a new leader from the ISR.

**Pre-KRaft (ZooKeeper mode):**
1. Broker detects leader is gone (missed heartbeat to ZK).
2. ZooKeeper triggers a controller election among brokers.
3. The elected controller picks the first ISR member as the new leader and writes it to ZK.
4. All producers and consumers fetch updated metadata and reconnect.

**KRaft mode (Kafka 3.x+, ZK eliminated):**
- A Raft quorum of controllers manages metadata (topic/partition/ISR state).
- Removes ZooKeeper as a dependency, halves failure recovery time (~20s → <5s).

**Unclean leader election (`unclean.leader.election.enable`):**
- Default: `false`. Only ISR members can become leader.
- If `true`: any replica (even lagging ones) can become leader. Risk: messages written since the replica fell behind are lost. Use only when availability is more important than data integrity (e.g., metrics pipelines).

---

## Log Compaction

Retention in Kafka has two modes:

| Mode | Behavior | Use case |
|---|---|---|
| **Time/size-based** (default) | Messages older than `retention.ms` are deleted | Event streams (click events, logs) |
| **Log compaction** | For each key, only the latest message value is retained | State stores, change data capture (CDC) |

Log compaction makes Kafka behave like a key-value changelog. A consumer that reads the compacted topic from offset 0 gets the latest state of every key — useful for rebuilding caches or materializing views after a restart. Tombstones (value = null) signal key deletion.

```
Before compaction:
  [k=user1, v=signup], [k=user2, v=signup], [k=user1, v=updated_email]

After compaction:
  [k=user2, v=signup], [k=user1, v=updated_email]
```

---

## Exactly-Once Semantics

By default, Kafka gives **at-least-once** delivery: if a producer retries after a network timeout, the message may be written twice.

### Idempotent Producer (`enable.idempotence=true`)

Each producer is assigned a **PID** (Producer ID) and a per-partition **sequence number**. The broker deduplicates retries: if the same (PID, partition, sequence) arrives twice, it is written once.

This gives **exactly-once within a single partition session** (not across restarts without a stable `transactional.id`).

### Transactional API

For **exactly-once across multiple partitions** (e.g., read from partition A, transform, write to partition B and commit offset atomically):

```python
producer = KafkaProducer(
    bootstrap_servers="...",
    transactional_id="my-unique-id"   # stable ID = survives restarts
)
producer.init_transactions()

producer.begin_transaction()
try:
    producer.send("output-topic", key=b"k", value=b"v")
    producer.send_offsets_to_transaction(offsets, consumer_group_id)
    producer.commit_transaction()
except Exception:
    producer.abort_transaction()
```

The consumer must set `isolation.level=read_committed` to only read messages from committed transactions. Uncommitted messages are buffered at the consumer until the transaction commits or aborts.

| Delivery guarantee | Config |
|---|---|
| At-most-once | `acks=0`, no retries |
| At-least-once | `acks=all`, retries > 0, no idempotence |
| Exactly-once | `enable.idempotence=true` + transactional API + `read_committed` |

---

## Tiered Storage (Kafka 3.6+)

Older log segments are offloaded to object storage (S3/GCS). Brokers serve recent data from local disk; historical data is fetched on demand from object storage. This decouples storage capacity from broker memory/disk, enabling infinite retention at low cost — useful for audit logs, replay-on-demand architectures.

---

## Interview Questions to Practice

1. **"Kafka producer sets `acks=all`. Is data guaranteed not to be lost?"**
   *No. `acks=all` waits for all in-sync replicas (ISR). If `min.insync.replicas=1` (or not set) and the ISR has shrunk to just the leader, it's functionally equivalent to `acks=1`. You need both `acks=all` AND `min.insync.replicas≥2` for meaningful durability.*

2. **"How does a Kafka consumer ensure it doesn't miss or double-process messages?"**
   *It commits offsets after successful processing (at-least-once) or uses the transactional API to atomically commit the offset and the output write in one transaction (exactly-once). For at-most-once: commit before processing — if it crashes, the message is skipped.*

3. **"A partition leader crashes. How long does it take Kafka to recover, and what are the trade-offs?"**
   *In ZK mode: ~20–30s to elect a new controller and propagate new leader metadata. In KRaft mode: ~5s. Trade-off: if `unclean.leader.election.enable=true`, recovery is faster but you may elect a lagging replica and lose messages. Default is false — only ISR members can lead.*

4. **"You need to process 1M events/sec and guarantee that all events for user X are processed in order. How do you design this in Kafka?"**
   *Partition by user_id (`hash(user_id) % num_partitions`). All user X events land in one partition, so strict ordering is preserved. Scale by increasing partition count; each consumer in the group handles one partition. Ordering is only guaranteed within a partition, not across them.*

5. **"What is log compaction and when would you use it instead of time-based retention?"**
   *Log compaction retains only the latest value per key. Use it for CDC / changelog topics where consumers need to reconstruct current state (e.g., a table snapshot). Time-based retention is for ephemeral streams where historical state doesn't matter.*
