# Kafka vs RabbitMQ

> **Source**: [Kafka vs RabbitMQ](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=1)

---

## Overview

Both Kafka and RabbitMQ are popular message broker systems, but they serve different purposes and have fundamentally different architectures.

---

## Key Differences at a Glance

| Feature | Kafka | RabbitMQ |
|---|---|---|
| **Model** | Distributed log / Event streaming | Traditional message broker |
| **Message Retention** | Retains messages (configurable TTL) | Deletes after consumption |
| **Consumer Model** | Pull-based | Push-based |
| **Ordering** | Per-partition ordering guaranteed | Per-queue ordering |
| **Throughput** | Very high (millions/sec) | Moderate (thousands/sec) |
| **Replay** | Supports replay from any offset | No native replay |
| **Protocol** | Custom binary protocol | AMQP, MQTT, STOMP |
| **Use Case** | Event streaming, log aggregation | Task queues, request/reply |

---

## Apache Kafka

### Architecture
- **Topics**: Logical category/feed name for messages
- **Partitions**: Topics are split into partitions for parallelism
- **Brokers**: Kafka servers that store data and serve clients
- **Consumer Groups**: Multiple consumers can form a group; each partition is consumed by only one consumer in a group
- **ZooKeeper/KRaft**: Manages cluster metadata (KRaft is the newer replacement for ZooKeeper)

### Key Characteristics
- **Append-only log**: Messages are written sequentially and immutably
- **Offset tracking**: Consumers track their position (offset) in the log
- **Replication**: Partitions are replicated across brokers for fault tolerance
- **High throughput**: Designed for handling millions of events per second
- **Durability**: Messages persisted to disk with configurable retention

### When to Use Kafka
- Event sourcing and event-driven architectures
- Real-time data pipelines and streaming
- Log aggregation across services
- Activity tracking (clicks, views, user actions)
- Metrics collection and monitoring
- Stream processing (with Kafka Streams or ksqlDB)
- When you need **message replay** capability
- When ordering within a partition matters

### Kafka Pros
- Extremely high throughput
- Message replay / reprocessing
- Excellent for event sourcing
- Strong durability guarantees
- Built-in partitioning for horizontal scaling

### Kafka Cons
- More complex to set up and operate
- Not ideal for complex routing patterns
- Higher latency for individual messages
- Overkill for simple task queues

---

## RabbitMQ

### Architecture
- **Exchanges**: Receive messages and route them to queues
  - **Direct exchange**: Routes by exact routing key match
  - **Fanout exchange**: Broadcasts to all bound queues
  - **Topic exchange**: Routes by pattern matching on routing keys
  - **Headers exchange**: Routes by message header attributes
- **Queues**: Store messages until consumed
- **Bindings**: Rules that connect exchanges to queues
- **Virtual Hosts (vhosts)**: Logical grouping for multi-tenancy

### Key Characteristics
- **Smart broker / dumb consumer**: Broker handles routing logic
- **Push model**: Broker pushes messages to consumers
- **Acknowledgments**: Consumers ack messages; unacked messages can be re-delivered
- **Message TTL**: Messages can expire
- **Dead Letter Queue**: Failed messages routed to DLQ for handling
- **Priority queues**: Support for message priorities

### When to Use RabbitMQ
- Task/work queues (background job processing)
- Request/reply patterns (RPC)
- Complex routing requirements
- When you need flexible acknowledgment/retry
- Pub/sub with complex routing rules
- When message ordering per queue is sufficient
- Lightweight and simpler setup needed

### RabbitMQ Pros
- Rich routing capabilities (exchanges + bindings)
- Built-in retry and dead letter queue support
- Lower latency for individual messages
- Simpler to set up for basic use cases
- Multiple protocol support (AMQP, MQTT, STOMP)

### RabbitMQ Cons
- Lower throughput compared to Kafka
- No native message replay
- Messages deleted after consumption (by default)
- Less suitable for event streaming

---

## Decision Framework

```
Need event streaming / replay?          → Kafka
Need high throughput (millions/sec)?    → Kafka
Need complex routing patterns?          → RabbitMQ
Need task/work queues?                  → RabbitMQ
Need request/reply (RPC)?              → RabbitMQ
Need log aggregation?                  → Kafka
Need message priority?                 → RabbitMQ
Building microservices event bus?      → Kafka
Simple background job processing?      → RabbitMQ
```

---

## Interview Tips

1. **Clarify the use case** before choosing — there's no universally "better" option
2. **Kafka** = think "event log" — immutable, replayable, high throughput
3. **RabbitMQ** = think "task queue" — smart routing, ack/nack, dead letter queues
4. Mention that **Kafka can replace RabbitMQ** in many scenarios but adds operational complexity
5. Both can be used together in a system — Kafka for event streaming, RabbitMQ for task distribution
