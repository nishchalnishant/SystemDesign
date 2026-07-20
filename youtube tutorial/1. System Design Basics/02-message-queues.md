# Message Queues in System Design Interviews

> **Source**: [Message Queues in System Design Interviews w/ Meta Staff Engineer](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=2)

---

## What is a Message Queue?

A **message queue** is a form of asynchronous service-to-service communication. Messages are stored in a queue until they are processed and deleted. Each message is processed only once, by a single consumer.

---

## Why Use Message Queues?

### Core Benefits
- **Decoupling**: Producer and consumer don't need to know about each other
- **Asynchronous Processing**: Producer doesn't wait for consumer to process
- **Load Leveling**: Absorbs traffic spikes; consumers process at their own pace
- **Reliability**: Messages persist even if consumer is temporarily down
- **Scalability**: Add more consumers to increase throughput

### Real-World Analogy
- Think of a **restaurant order system**: waiter (producer) puts order on the counter (queue), chef (consumer) picks it up when ready. They work independently.

---

## Core Concepts

### Producer
- Creates and sends messages to the queue
- Fire-and-forget after message is acknowledged by the queue

### Consumer
- Reads and processes messages from the queue
- Sends acknowledgment (ACK) after successful processing
- Can NACK (negative acknowledge) for retry

### Message
- The unit of data being transmitted
- Contains a **body** (payload) and optional **metadata/headers**
- Can have **TTL** (Time to Live), **priority**, **routing key**

### Queue
- FIFO data structure that stores messages
- Durable (persisted to disk) or transient (in-memory only)

---

## Message Delivery Semantics

| Semantic | Description | Trade-off |
|---|---|---|
| **At-most-once** | Message delivered 0 or 1 times | May lose messages, but no duplicates |
| **At-least-once** | Message delivered 1+ times | No message loss, but possible duplicates |
| **Exactly-once** | Message delivered exactly 1 time | Hardest to achieve; requires idempotency |

### Best Practice
- Design for **at-least-once** delivery with **idempotent consumers**
- Use a **deduplication ID** or **idempotency key** to handle duplicates

---

## Common Patterns

### 1. Point-to-Point (Work Queue)
```
Producer → Queue → Consumer
                 → Consumer  (competing consumers)
                 → Consumer
```
- Multiple consumers compete for messages
- Each message processed by only ONE consumer
- Use case: Background job processing, email sending

### 2. Publish/Subscribe (Fan-out)
```
Producer → Topic/Exchange → Queue A → Consumer A
                          → Queue B → Consumer B
                          → Queue C → Consumer C
```
- Message delivered to ALL subscribers
- Use case: Event notifications, real-time updates

### 3. Request/Reply
```
Client → Request Queue → Server
Client ← Reply Queue   ← Server
```
- Synchronous-like pattern over async infrastructure
- Use case: RPC over message queues

---

## Dead Letter Queue (DLQ)

- Messages that **fail processing** after N retries go to a DLQ
- Prevents poison messages from blocking the queue
- Engineers can inspect and manually reprocess DLQ messages
- Critical for **observability and debugging**

```
Main Queue → Consumer (fails) → Retry Queue → Consumer (fails again)
                                             → Dead Letter Queue
```

---

## Backpressure Handling

- When producers are faster than consumers, the queue grows
- Strategies:
  - **Scale consumers** horizontally
  - **Rate limit** producers
  - **Drop messages** (if acceptable, e.g., metrics)
  - **Apply backpressure** signals to producers

---

## Message Ordering

- **FIFO Queues**: Guarantee strict ordering (e.g., SQS FIFO)
- **Standard Queues**: Best-effort ordering, higher throughput
- **Partitioned ordering**: Order guaranteed within a partition/shard (Kafka)
- In interviews, clarify if ordering matters for the use case

---

## When to Use Message Queues in System Design

| Scenario | Example |
|---|---|
| **Async processing** | Send email after user signup |
| **Decoupling services** | Order service → Payment service |
| **Rate limiting** | API gateway → rate-limited processing |
| **Event-driven architecture** | User action triggers multiple downstream services |
| **Batch processing** | Collect events, process in bulk |
| **Retry mechanism** | Failed operations queued for retry |

---

## Popular Message Queue Systems

| System | Key Strength |
|---|---|
| **Apache Kafka** | High throughput, event streaming |
| **RabbitMQ** | Flexible routing, AMQP support |
| **Amazon SQS** | Fully managed, easy to use |
| **Amazon SNS** | Pub/sub, fan-out notifications |
| **Google Pub/Sub** | Global, fully managed |
| **Redis Streams** | Lightweight, low latency |

---

## Interview Tips

1. **Always mention message queues** when you see async processing needs
2. Discuss **delivery semantics** (at-least-once + idempotency is the sweet spot)
3. Mention **DLQ** for error handling — shows production experience
4. Discuss **scaling consumers** independently of producers
5. Address **ordering guarantees** — do you need strict FIFO or is best-effort OK?
6. Consider **message size limits** — large payloads should use a reference pattern (store in S3, send URL in message)
