> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Pub-Sub Messaging System (like Kafka or RabbitMQ) — an advanced systems problem disguised as LLD, testing deep understanding of concurrency, queues, and message delivery guarantees.
>
> **Key concepts:**
> - Core Entities: `Topic`, `Message`, `Publisher`, `Subscriber`, `Queue`/`Broker`.
> - Observer Pattern: The foundational pattern. Subscribers observe Topics.
> - Delivery Strategies: `AtMostOnce`, `AtLeastOnce`, `ExactlyOnce`.
> - Push vs Pull: Does the broker push messages to subscribers (RabbitMQ style, easy for LLD), or do subscribers poll the broker (Kafka style, better for scale)?
> - Concurrency: Thread pools for workers consuming messages, thread-safe queues (`ConcurrentLinkedQueue`), and handling slow consumers without blocking the publisher.
>
> **Key takeaway:** Keep it simple initially: implement an in-memory "Push" based system. If asked for high throughput, introduce a `BlockingQueue` per topic, where a worker thread reads from the queue and pushes to the subscribers asynchronously.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, pub-sub, messaging, observer, strategy]
---
# Design a Pub-Sub Messaging System

> **Difficulty**: Hard  
> **Asked at**: Amazon, Google, Meta  
> **Key Patterns**: Observer, Strategy (delivery guarantee), Command (message processing)

---

## Understanding the Problem

Design a publish-subscribe messaging system where publishers send messages to named topics, subscribers consume messages, and the system provides configurable delivery guarantees and consumer group semantics.

---

## Clarifying Questions

**You**: "Should messages be delivered in order within a topic?"  
**Interviewer**: "Yes — ordered per topic partition is a good starting point."

**You**: "What delivery guarantee do we target?"  
**Interviewer**: "At-least-once by default; exactly-once is a deep dive."

**You**: "Do we need consumer groups — where multiple consumers share load?"  
**Interviewer**: "Yes — one consumer per group processes each message; all groups get every message."

**You**: "Should unacknowledged messages be redelivered?"  
**Interviewer**: "Yes — after an ack timeout, redeliver the message."

**You**: "Do we need push or pull delivery?"  
**Interviewer**: "Model pull; push is a stretch goal."

**You**: "Do we need a dead letter queue for repeatedly failing messages?"  
**Interviewer**: "Yes — after max retries, move to DLQ."

---

## Final Requirements

**In scope:**
1. Publishers publish messages to named topics
2. Subscribers subscribe to topics; pull messages from their subscription
3. Consumer groups: each group sees every message; only one member in the group processes it
4. At-least-once delivery: unacknowledged messages redelivered after timeout
5. Dead letter queue after `max_retries` failed deliveries
6. Message ordering preserved per topic

**Out of scope:**
- Persistence to disk / durable storage
- Cross-datacenter replication
- Topic partitioning beyond single ordered queue

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| PubSubSystem | Top-level facade; manages topics and subscriptions |
| Topic | Named channel; holds ordered message log |
| Message | Immutable payload with id, topic, data, timestamp |
| Publisher | Produces messages to a topic |
| Subscription | Consumer's view into a topic; tracks read offset |
| ConsumerGroup | Set of subscriptions sharing a group_id; load-balance delivery |
| InFlightMessage | Tracks messages delivered but not yet acked (for redelivery) |
| DeadLetterQueue | Receives messages that exceeded max retries |
| DeliveryPolicy | Config: ack_timeout_sec, max_retries |

---

## Class Design

### Message

| Requirement | What Message must track |
|-------------|-------------------------|
| Identity | message_id, topic_name, payload, published_at |

```
class Message:
- message_id: str
- topic_name: str
- payload: Any
- published_at: datetime
```

### Topic

| Requirement | What Topic must track |
|-------------|------------------------|
| Message log | ordered list of Messages |
| Consumer groups | dict of group_id -> ConsumerGroup |

```
class Topic:
- name: str
- messages: list[Message]
- groups: dict[str, ConsumerGroup]
+ publish(message: Message)
+ subscribe(group_id: str, subscriber_id: str) -> Subscription
+ consume(group_id: str, subscriber_id: str) -> Optional[Message]
```

### Subscription

| Requirement | What Subscription must track |
|-------------|------------------------------|
| Position | offset into topic.messages |
| In-flight | dict of message_id -> InFlightMessage |

```
class Subscription:
- subscription_id: str
- subscriber_id: str
- group_id: str
- topic: Topic
- offset: int
- in_flight: dict[str, InFlightMessage]
+ fetch() -> Optional[Message]
+ ack(message_id: str)
+ nack(message_id: str)
```

### ConsumerGroup

| Requirement | What ConsumerGroup must track |
|-------------|-------------------------------|
| Shared offset | one offset per group across all members |
| Load balance | which subscriber gets next message |
| Members | list of active subscription ids |

```
class ConsumerGroup:
- group_id: str
- topic: Topic
- offset: int
- members: list[str]          # subscriber_ids
- _round_robin_idx: int
+ add_member(subscriber_id: str)
+ next_member() -> str        # round-robin
```

---

## Implementation

### Core Method: `consume` (pull delivery)

**Core logic:**
1. Look up the ConsumerGroup for the given group_id.
2. Verify `subscriber_id` is the next member in round-robin rotation.
3. If the group offset is within the topic message list, fetch the message.
4. Create an `InFlightMessage` record with deadline = now + ack_timeout.
5. Advance group offset by 1.
6. Return the message to the caller (who must ack or nack).

**Edge cases:**
- No messages at current offset — return None.
- Subscriber not in group — raise ValueError.
- Message already in-flight for another subscriber in the group — skip (already being processed).

```python
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Any, Optional
import uuid, threading


@dataclass
class Message:
    message_id: str
    topic_name: str
    payload: Any
    published_at: datetime = field(default_factory=datetime.utcnow)


@dataclass
class InFlightMessage:
    message: Message
    subscriber_id: str
    delivered_at: datetime
    retry_count: int = 0
    deadline: datetime = field(init=False)

    def __post_init__(self):
        self.deadline = self.delivered_at + timedelta(seconds=30)

    def is_expired(self) -> bool:
        return datetime.utcnow() > self.deadline


@dataclass
class DeadLetterQueue:
    messages: list[tuple[Message, str]] = field(default_factory=list)  # (msg, reason)

    def add(self, message: Message, reason: str):
        self.messages.append((message, reason))
        print(f"DLQ: message {message.message_id} moved — {reason}")


class ConsumerGroup:
    def __init__(self, group_id: str):
        self.group_id = group_id
        self.offset: int = 0
        self.members: list[str] = []
        self._rr_idx: int = 0
        self.in_flight: dict[str, InFlightMessage] = {}
        self._lock = threading.Lock()

    def add_member(self, subscriber_id: str):
        if subscriber_id not in self.members:
            self.members.append(subscriber_id)

    def next_member(self) -> Optional[str]:
        if not self.members:
            return None
        m = self.members[self._rr_idx % len(self.members)]
        self._rr_idx += 1
        return m

    def ack(self, message_id: str):
        with self._lock:
            self.in_flight.pop(message_id, None)

    def nack(self, message_id: str):
        with self._lock:
            if message_id in self.in_flight:
                self.in_flight[message_id].retry_count += 1

    def expired_messages(self, max_retries: int) -> tuple[list[Message], list[Message]]:
        """Returns (to_redeliver, to_dlq)"""
        now = datetime.utcnow()
        redeliver, dlq = [], []
        with self._lock:
            for mid, inflight in list(self.in_flight.items()):
                if inflight.is_expired():
                    if inflight.retry_count >= max_retries:
                        dlq.append(inflight.message)
                        del self.in_flight[mid]
                    else:
                        redeliver.append(inflight.message)
                        del self.in_flight[mid]
                        self.offset -= 1  # simplified: requeue by backing offset
        return redeliver, dlq


class Topic:
    MAX_RETRIES = 3

    def __init__(self, name: str):
        self.name = name
        self.messages: list[Message] = []
        self.groups: dict[str, ConsumerGroup] = {}
        self.dlq = DeadLetterQueue()
        self._lock = threading.Lock()

    def publish(self, payload: Any) -> Message:
        msg = Message(message_id=str(uuid.uuid4()),
                      topic_name=self.name,
                      payload=payload)
        with self._lock:
            self.messages.append(msg)
        return msg

    def create_group(self, group_id: str) -> ConsumerGroup:
        if group_id not in self.groups:
            self.groups[group_id] = ConsumerGroup(group_id)
        return self.groups[group_id]

    def subscribe(self, group_id: str, subscriber_id: str):
        group = self.create_group(group_id)
        group.add_member(subscriber_id)

    def consume(self, group_id: str, subscriber_id: str) -> Optional[Message]:
        group = self.groups.get(group_id)
        if not group:
            raise ValueError(f"Group {group_id} not subscribed to {self.name}")
        if subscriber_id not in group.members:
            raise ValueError(f"Subscriber {subscriber_id} not in group {group_id}")

        expected = group.next_member()
        if expected != subscriber_id:
            return None  # not this subscriber's turn

        with self._lock:
            if group.offset >= len(self.messages):
                return None
            msg = self.messages[group.offset]
            group.offset += 1

        inflight = InFlightMessage(message=msg, subscriber_id=subscriber_id,
                                   delivered_at=datetime.utcnow())
        group.in_flight[msg.message_id] = inflight
        return msg

    def ack(self, group_id: str, message_id: str):
        self.groups[group_id].ack(message_id)

    def nack(self, group_id: str, message_id: str):
        self.groups[group_id].nack(message_id)

    def reap_expired(self):
        for group in self.groups.values():
            redeliver, to_dlq = group.expired_messages(self.MAX_RETRIES)
            for msg in to_dlq:
                self.dlq.add(msg, f"exceeded {self.MAX_RETRIES} retries")


class PubSubSystem:
    def __init__(self):
        self.topics: dict[str, Topic] = {}

    def create_topic(self, name: str) -> Topic:
        if name not in self.topics:
            self.topics[name] = Topic(name)
        return self.topics[name]

    def publish(self, topic_name: str, payload: Any) -> Message:
        topic = self.topics.get(topic_name)
        if not topic:
            raise ValueError(f"Topic {topic_name} does not exist")
        return topic.publish(payload)

    def subscribe(self, topic_name: str, group_id: str, subscriber_id: str):
        topic = self.topics[topic_name]
        topic.subscribe(group_id, subscriber_id)

    def consume(self, topic_name: str, group_id: str,
                subscriber_id: str) -> Optional[Message]:
        return self.topics[topic_name].consume(group_id, subscriber_id)

    def ack(self, topic_name: str, group_id: str, message_id: str):
        self.topics[topic_name].ack(group_id, message_id)

    def nack(self, topic_name: str, group_id: str, message_id: str):
        self.topics[topic_name].nack(group_id, message_id)
```

---

## Verification

Scenario: Topic "orders"; group "billing" with two members (B1, B2); publisher sends 3 messages.

1. `publish("orders", "order-1")` → appended to topic.messages[0].
2. `publish("orders", "order-2")` → topic.messages[1].
3. `publish("orders", "order-3")` → topic.messages[2].
4. B1 calls `consume("orders", "billing", "B1")` → round-robin says B1's turn → returns "order-1", offset=1, in_flight["msg1"] set.
5. B2 calls `consume` → returns "order-2", offset=2.
6. B1 acks "msg1" → in_flight entry removed.
7. B1 calls consume again → returns "order-3", offset=3.
8. B2 never acks "msg2" → `reap_expired()` detects timeout → redeliver or DLQ.

---

## Deep Dive & Extensibility

### 1. "At-least-once vs exactly-once — trade-offs"

**At-least-once**: redeliver on ack timeout. Consumer may see duplicates; consumer must be idempotent. Simple to implement.

**At-most-once**: deliver and forget; no redelivery. Can lose messages. Good for metrics/logging.

**Exactly-once**: requires idempotent producers (message_id dedup) + transactional consumers (commit offset atomically with processing). Expensive — needs a distributed transaction or an idempotency store.

```python
# Idempotency store for exactly-once consumer side
class IdempotentConsumer:
    def __init__(self):
        self._processed: set[str] = set()

    def process(self, message: Message):
        if message.message_id in self._processed:
            return  # duplicate — skip
        self._handle(message)
        self._processed.add(message.message_id)
```

### 2. "How do consumer groups work?"

All groups subscribed to a topic have their own independent offset. Publishing one message means every group eventually processes it. Within a group, messages are load-balanced across members (round-robin). Member B1 and B2 in group "billing" each process roughly half the messages; "audit" group processes all of them independently.

```
Topic messages: [m1, m2, m3, m4]

Group "billing"  (B1, B2):  B1 gets m1, m2; B2 gets m2, m4  (load balanced)
Group "audit"   (A1):       A1 gets m1, m2, m3, m4 (all)
```

### 3. "How do you guarantee message ordering?"

Within a single partition (the topic's message list), ordering is preserved because messages are appended sequentially and the offset advances monotonically. Multiple partitions improve throughput but require a partition key to route related messages to the same partition (e.g., order_id % num_partitions).

### 4. "How does the dead letter queue work?"

After `max_retries` failed deliveries (ack timeout + nack), the message is moved to a per-topic DLQ. Operations teams can inspect and replay DLQ messages after fixing the root cause.

```python
def reap_expired(self):
    for group in self.groups.values():
        redeliver, to_dlq = group.expired_messages(self.MAX_RETRIES)
        for msg in to_dlq:
            self.dlq.add(msg, f"exceeded {self.MAX_RETRIES} retries in group {group.group_id}")
        for msg in redeliver:
            # Re-insert at current offset position for redelivery
            self.messages.insert(group.offset, msg)
```

### 5. "Push vs pull delivery model"

**Pull**: consumer calls consume() when ready; controls its own pace; natural backpressure. Used by Kafka.

**Push**: system calls consumer's callback when a message arrives; lower latency; consumer can be overwhelmed. Used by SQS (push notification) and traditional message brokers.

```python
# Push model addition
class PushSubscription:
    def __init__(self, callback):
        self.callback = callback

class Topic:
    def publish(self, payload):
        msg = Message(...)
        self.messages.append(msg)
        for sub in self._push_subscriptions:
            threading.Thread(target=sub.callback, args=(msg,), daemon=True).start()
```

---

## Interviewer Questions by Level

**Junior**: What is the difference between a topic and a subscription?  
**Mid-level**: How does consumer group load balancing work, and why does each group maintain its own offset?  
**Senior**: Explain how you'd achieve exactly-once delivery semantics end-to-end.

---

## Common Interview Questions

- **Q: At-least-once vs exactly-once trade-offs?** A: At-least-once is simple — redeliver on timeout, consumer deduplicates. Exactly-once requires idempotent producers + transactional offset commits, adding significant complexity.
- **Q: How do consumer groups work?** A: Each group has an independent offset into the topic. Publishing one message means every group eventually processes it; within a group, members share the load via round-robin.
- **Q: How is message ordering maintained?** A: Append-only message log + monotonically advancing offset per group. All groups read in the same order; ordering is only guaranteed within a partition.
- **Q: What happens to unacknowledged messages?** A: A reaper thread runs periodically, finds in-flight messages past their ack deadline, and either redelivers them or moves them to the DLQ after max_retries.
- **Q: Push vs pull — which is better?** A: Pull is better for high-throughput systems (consumer controls pace, natural backpressure); push is better for low-latency event-driven use cases.
- **Q: How does the DLQ help operations?** A: It quarantines poison messages that repeatedly fail, letting the main queue continue processing while giving operators time to inspect and replay.
- **Q: How would you handle a slow consumer in a pull system?** A: The consumer simply doesn't call consume() fast enough; messages accumulate at the offset. Implement lag monitoring (current_offset - consumer_offset) and alert when lag exceeds a threshold.
