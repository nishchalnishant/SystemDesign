# Apache Kafka Deep Dive

> **Source**: Videos #21, #25, #41, #59, #67, #77, #84, #99 from the playlist
> - System Design: Why is Kafka fast?
> - System Design: Why is single-threaded Redis so fast?
> - System Design: Apache Kafka In 3 Minutes
> - System Design: Why is Kafka so Popular?
> - Kafka vs. RabbitMQ vs. Messaging Middleware vs. Pulsar
> - Top Kafka Use Cases You Should Know
> - Apache Kafka Fundamentals You Should Know

---

## What is Kafka?

Apache Kafka is a **distributed event streaming platform** designed for high-throughput, fault-tolerant, real-time data pipelines.

---

## Core Concepts

```
Producer → Topic (Partition 0, 1, 2...) → Consumer Group
                                           ├── Consumer 1 (Partition 0)
                                           ├── Consumer 2 (Partition 1)
                                           └── Consumer 3 (Partition 2)
```

| Concept | Description |
|---|---|
| **Producer** | Publishes messages to topics |
| **Consumer** | Reads messages from topics |
| **Topic** | Named category/feed of messages |
| **Partition** | Ordered, immutable sequence within a topic |
| **Offset** | Position of a message within a partition |
| **Broker** | Kafka server that stores data |
| **Consumer Group** | Set of consumers that share the workload |
| **ZooKeeper/KRaft** | Cluster management and leader election |

---

## Why is Kafka Fast?

1. **Sequential I/O**: Append-only writes to disk (sequential = fast)
2. **Zero-copy**: Data sent directly from page cache to network socket
3. **Batching**: Messages batched for network efficiency
4. **Compression**: Batch compression reduces network overhead
5. **Partitioning**: Parallelism across partitions
6. **Page cache**: Leverages OS page cache instead of JVM heap
7. **Sendfile system call**: Avoids copying data between kernel and user space

---

## Why is Kafka Popular?

1. **High throughput**: Millions of messages per second
2. **Durability**: Messages persisted to disk with replication
3. **Scalability**: Add brokers and partitions horizontally
4. **Fault tolerance**: Replication across brokers
5. **Replay**: Consumers can re-read from any offset
6. **Ecosystem**: Kafka Streams, ksqlDB, Kafka Connect
7. **Decoupling**: Producers and consumers are independent

---

## Top Kafka Use Cases

| Use Case | Description |
|---|---|
| **Event Streaming** | Real-time event processing |
| **Log Aggregation** | Collect logs from multiple services |
| **Metrics Collection** | System/application metrics pipeline |
| **Activity Tracking** | User clicks, views, searches |
| **Stream Processing** | Real-time data transformations |
| **Event Sourcing** | Store state changes as events |
| **CDC (Change Data Capture)** | Replicate DB changes to other systems |
| **Message Queue** | Async communication between services |

---

## Kafka vs RabbitMQ vs Pulsar

| Feature | Kafka | RabbitMQ | Pulsar |
|---|---|---|---|
| **Model** | Distributed log | Message broker | Distributed log + messaging |
| **Throughput** | Very high | Moderate | Very high |
| **Message Retention** | Configurable (days/forever) | Until consumed | Configurable |
| **Replay** | Yes (offset-based) | No | Yes |
| **Ordering** | Per-partition | Per-queue | Per-partition |
| **Protocol** | Binary | AMQP | Binary |
| **Multi-tenancy** | Limited | vhosts | Native |
| **Geo-replication** | MirrorMaker | Federation | Built-in |
| **Best For** | Event streaming | Task queues | Both streaming + queueing |

---

## Kafka Guarantees

| Guarantee | How |
|---|---|
| **Ordering** | Within a partition (not across partitions) |
| **Durability** | Replication factor (e.g., 3 replicas) |
| **At-least-once** | Default consumer behavior |
| **Exactly-once** | Idempotent producer + transactional consumer |
