> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How message brokers decouple services, the delivery guarantee models that separate a junior answer from a senior one, and how to reason about ordering, backpressure, and failure modes.
>
> **Key topics:**
> - **Async decoupling** — producers and consumers scale independently
> - **Point-to-Point vs Pub/Sub** — task queues vs event fans
> - **Delivery guarantees** — at-most-once, at-least-once, exactly-once
> - **Idempotent consumers** — how to handle duplicates safely
> - **Ordering** — when it's guaranteed and when it's not
> - **Backpressure** — what happens when consumers can't keep up
> - **Dead Letter Queue** — handling poison pill messages
> - **Kafka vs RabbitMQ vs SQS** — when to use what
>
> **Key takeaway:** Every message broker system you design must answer three questions: what delivery guarantee do you need, how do you handle duplicates, and how do you handle consumer lag? These are the senior-signal follow-ups.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, coordination]
---
# Message Brokers

---

## Why Message Brokers Exist

When Service A calls Service B directly (synchronous RPC), the latency of A includes the latency of B. If B is slow, A is slow. If B crashes, A fails. If traffic spikes, both must scale in lockstep.

A message broker breaks this coupling. A writes to the broker and returns immediately. B reads at its own pace. They can scale, deploy, and fail independently.

**When to use a broker vs a direct call:**

| Use direct call | Use a broker |
|---|---|
| Response needed before returning to user | Response not needed immediately |
| Sub-10ms latency required | Can tolerate seconds of delay |
| Single downstream consumer | Multiple consumers, or fan-out |
| Simple request/reply | Durable event record needed |

---

## Two Messaging Patterns

### Point-to-Point (Task Queue)

One message → consumed by exactly one consumer → deleted after ack.

```
Producer → [Queue] → Consumer A
                   ↛ Consumer B  (only one wins)
```

Use for: payment processing, email sending, PDF generation — work that must happen exactly once.

**Tools:** RabbitMQ, AWS SQS, ActiveMQ.

### Publish/Subscribe (Event Stream)

One message → fan-out to all subscribers independently.

```
Producer → [Topic: user.signup]
              ├─ Email Service (Consumer Group A)
              ├─ Analytics Service (Consumer Group B)
              └─ Profile Service (Consumer Group C)
```

Each consumer group gets its own copy of every message. Adding a new consumer group doesn't affect existing ones. The broker retains messages for the configured retention window.

Use for: event-driven architecture, audit logs, change data capture, real-time analytics.

**Tools:** Apache Kafka, AWS SNS+SQS (fan-out pattern), Google Pub/Sub.

---

## Delivery Guarantees

This is the section most candidates gloss over. Every broker makes a trade-off between performance and safety.

### At-Most-Once

Message is sent once. If the consumer crashes before processing it, the message is gone.

- **How:** Producer sends and forgets (no acks). Consumer auto-acks on receive, before processing.
- **Risk:** Data loss on crash.
- **Use for:** Metrics, telemetry, log streaming — losing a few data points is acceptable.

### At-Least-Once

Message is delivered one or more times. The consumer must ack after successful processing; if it crashes before acking, the broker redelivers.

- **How:** Producer retries until broker acks. Consumer manually acks after processing.
- **Risk:** Duplicates if the consumer processes successfully but crashes before acking.
- **Use for:** The safe default for most systems. Handle duplicates with idempotent consumers.

### Exactly-Once

Message is processed exactly once, even across producer retries and consumer crashes.

- **How:** Requires coordination at both ends:
  - **Producer side:** Idempotent producer (Kafka: `enable.idempotence=true`) deduplicates retries using (producer_id, sequence_number).
  - **Consumer side:** Transactional writes — atomically commit the processed output AND the consumer offset in one transaction.
- **Cost:** Higher latency, coordination overhead.
- **Use for:** Financial transactions, inventory updates, any write where duplicates cause real harm.

| Guarantee | Data loss | Duplicates | Complexity |
|---|---|---|---|
| At-most-once | Yes | No | Low |
| At-least-once | No | Yes | Medium |
| Exactly-once | No | No | High |

---

## Idempotent Consumers

At-least-once is the most practical guarantee for most systems, but it requires consumers to handle duplicates. An **idempotent consumer** produces the same result whether it processes a message once or ten times.

**Pattern 1 — Natural idempotency:** Use `INSERT ... ON CONFLICT DO NOTHING` with a unique constraint on the message/event ID.

```sql
-- payments table has UNIQUE(idempotency_key)
INSERT INTO payments (id, amount, idempotency_key)
VALUES ($1, $2, $3)
ON CONFLICT (idempotency_key) DO NOTHING;
```

**Pattern 2 — Dedup table:** Maintain a `processed_events(event_id, processed_at)` table. Check before processing; skip if already seen.

```python
def process(event):
    if db.exists("SELECT 1 FROM processed_events WHERE event_id = %s", event.id):
        return  # already handled
    with db.transaction():
        apply_business_logic(event)
        db.execute("INSERT INTO processed_events VALUES (%s, NOW())", event.id)
```

**Pattern 3 — Outbox pattern:** Write the event and the business state change atomically in one DB transaction (outbox table). A separate relay process reads the outbox and publishes to the broker. Guarantees the event is published if and only if the DB write succeeded.

```
[Service DB Transaction]
  INSERT INTO orders (...)           ← business write
  INSERT INTO outbox (event, ...)    ← event record
  COMMIT

[Outbox Relay Process]
  SELECT * FROM outbox WHERE status = 'pending'
  → publish to broker
  → UPDATE outbox SET status = 'sent'
```

---

## Ordering

**Kafka:** Ordering is guaranteed within a single partition. Messages with the same key always go to the same partition (via `hash(key) % num_partitions`), so per-entity ordering is maintained. Cross-partition ordering is not guaranteed.

**RabbitMQ:** FIFO within a single queue. If you have competing consumers on one queue, ordering is maintained per consumer but not globally.

**SQS Standard:** No ordering guarantee (best-effort). Use **SQS FIFO** for strict ordering (limited to 3,000 msg/sec per queue with batching).

**Interview answer for "how do you guarantee order":** Partition/route by the entity key (user_id, order_id). All events for that entity land on the same partition/queue → processed sequentially by one consumer at a time.

---

## Backpressure and Consumer Lag

**Consumer lag** = number of messages in the broker that haven't been consumed yet. High lag means consumers can't keep up with producers.

**What to do:**
1. **Scale consumers** — add more consumer instances (up to the number of partitions in Kafka).
2. **Optimize consumer logic** — batch DB writes, use connection pools.
3. **Apply backpressure upstream** — slow the producer rate or reject new work (circuit breaker).
4. **Alert on lag** — set alerts when consumer lag exceeds N minutes of production rate.

**Key metric to monitor:** `consumer_group_lag` — exposed by Kafka's JMX metrics and tools like Burrow or Confluent Control Center.

---

## Dead Letter Queue (DLQ)

If a consumer fails to process a message after N retries (e.g., 3–5), the broker moves it to a **Dead Letter Queue** instead of blocking the main queue.

- Prevents a "poison pill" message (malformed, depends on deleted data, triggers a bug) from halting all processing.
- DLQ contents should alert on-call engineers.
- DLQ messages should be inspectable and replayable once the root cause is fixed.

```
Main Queue → Consumer (fails 3x) → DLQ → Alert → Engineer inspects → Replay
```

---

## Kafka vs RabbitMQ vs SQS

| | Kafka | RabbitMQ | SQS |
|---|---|---|---|
| Model | Log-based (consumers read at own pace) | Push-based (broker pushes to consumers) | Pull-based managed queue |
| Retention | Days/weeks (configurable) | Until acked + deleted | 4 days default, 14 days max |
| Throughput | Millions/sec | Tens of thousands/sec | ~3,000/sec per queue standard |
| Ordering | Per-partition | Per-queue | No (FIFO queue: yes) |
| Replay | Yes (seek to any offset) | No (messages deleted on ack) | No |
| Exactly-once | Yes (transactional API) | Plugin-based | No (at-least-once standard) |
| Best for | Event streaming, audit logs, CDC, real-time pipelines | Task queues, RPC patterns, routing/fanout | Serverless, simple async tasks on AWS |

**Rule of thumb:** If you need replay, long retention, or high throughput → Kafka. If you need flexible routing, priority queues, or simple task delegation → RabbitMQ. If you're already on AWS and want managed simplicity → SQS.

---

## Interview Questions to Practice

1. **"You need to process payments. Which delivery guarantee do you choose and why?"**
   *Exactly-once, or at-least-once with idempotent consumers. Exactly-once via Kafka's transactional API is the cleanest, but adds latency. Practically: at-least-once + unique constraint on payment_id in the DB is simpler and equally safe. The key is the consumer must be idempotent — charging a customer twice is a P0 incident.*

2. **"A consumer processes a message and writes to the DB, but crashes before acking. What happens?"**
   *The broker redelivers the message (at-least-once). If the consumer is idempotent (e.g., uses `INSERT ... ON CONFLICT DO NOTHING`), the redelivery is a no-op. If it's not idempotent, you'll get a duplicate — double charge, double email, etc. This is why idempotency is mandatory when using at-least-once delivery.*

3. **"How do you guarantee ordered processing of events for a specific user?"**
   *In Kafka: use user_id as the message key. Kafka routes all messages with the same key to the same partition. One consumer per partition processes messages sequentially. Ordering is maintained per user, across the whole topic.*

4. **"Your consumer lag keeps growing. What do you do?"**
   *First, instrument: is lag growing on all partitions or one? If one, that consumer instance may be stuck on a poison pill — check DLQ. If all partitions: scale consumers (add instances up to partition count), optimize consumer processing (batch writes, reduce DB round trips), and if still insufficient, increase partition count (requires careful rebalancing).*

5. **"What is the outbox pattern and when do you need it?"**
   *The outbox pattern solves the dual-write problem: you can't atomically write to a DB and publish to a broker in one transaction. Solution: write both the business event and a record in an `outbox` table in one DB transaction, then have a relay process publish the outbox records to the broker and mark them sent. Guarantees no event is lost even if the broker is temporarily unavailable.*
