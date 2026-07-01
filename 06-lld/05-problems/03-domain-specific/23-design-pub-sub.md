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

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants: no message delivered twice within a group, no message lost across multiple concurrent publishers, and DLQ correctly captures unacknowledged messages.

```python
import threading
import time
import uuid
from dataclasses import dataclass, field
from typing import Any, Optional

# ── Minimal self-contained pub-sub implementation ──

@dataclass
class Message:
    message_id: str
    topic_name: str
    payload: Any
    published_at: float = field(default_factory=time.time)

@dataclass
class InFlight:
    message: Message
    deadline: float
    retries: int = 0

class DLQ:
    def __init__(self):
        self._messages = []
        self._lock = threading.Lock()

    def add(self, message, reason):
        with self._lock:
            self._messages.append((message, reason))

    def size(self):
        with self._lock:
            return len(self._messages)


class ConsumerGroup:
    def __init__(self, group_id, ack_timeout=5.0, max_retries=3):
        self.group_id = group_id
        self.offset = 0
        self.ack_timeout = ack_timeout
        self.max_retries = max_retries
        self._in_flight = {}       # message_id -> InFlight
        self._delivered_ids = set()  # all message_ids ever delivered to this group
        self._lock = threading.Lock()

    def fetch(self, messages, dlq):
        with self._lock:
            # Redeliver expired in-flight messages first
            now = time.time()
            to_redeliver = []
            to_dlq = []
            for mid, inf in list(self._in_flight.items()):
                if now > inf.deadline:
                    if inf.retries >= self.max_retries:
                        to_dlq.append(mid)
                    else:
                        to_redeliver.append(inf.message)
                        inf.retries += 1
                        inf.deadline = now + self.ack_timeout

            for mid in to_dlq:
                dlq.add(self._in_flight.pop(mid).message, "max_retries")

            if to_redeliver:
                msg = to_redeliver[0]
                return msg

            # Fetch next from log
            if self.offset < len(messages):
                msg = messages[self.offset]
                self.offset += 1
                self._in_flight[msg.message_id] = InFlight(msg, now + self.ack_timeout)
                self._delivered_ids.add(msg.message_id)
                return msg

            return None

    def ack(self, message_id):
        with self._lock:
            self._in_flight.pop(message_id, None)

    def nack(self, message_id):
        with self._lock:
            if message_id in self._in_flight:
                inf = self._in_flight[message_id]
                inf.deadline = 0  # force immediate redeliver


class Topic:
    def __init__(self, name):
        self.name = name
        self._messages = []
        self._groups = {}
        self._msg_lock = threading.Lock()
        self._grp_lock = threading.Lock()
        self.dlq = DLQ()

    def publish(self, payload):
        msg = Message(str(uuid.uuid4()), self.name, payload)
        with self._msg_lock:
            self._messages.append(msg)
        return msg

    def subscribe(self, group_id, **kwargs):
        with self._grp_lock:
            if group_id not in self._groups:
                self._groups[group_id] = ConsumerGroup(group_id, **kwargs)
        return self._groups[group_id]

    def consume(self, group_id):
        with self._grp_lock:
            group = self._groups.get(group_id)
        if group is None:
            raise ValueError(f"No group: {group_id}")
        with self._msg_lock:
            msgs = list(self._messages)   # snapshot
        return group.fetch(msgs, self.dlq)


# ─────────────────────────────────────────────────────────────
# TEST 1: No message delivered twice within a consumer group
# 1 publisher sends 200 messages; 5 consumer threads race to consume.
# Each message_id must appear exactly once in the consumed set.
# ─────────────────────────────────────────────────────────────
def test_no_duplicate_delivery():
    topic = Topic("orders")
    topic.subscribe("group-A")

    # Publish 200 messages (single thread — ordering guarantee)
    published_ids = set()
    for i in range(200):
        msg = topic.publish(f"order-{i}")
        published_ids.add(msg.message_id)

    consumed_ids = []
    c_lock = threading.Lock()

    def consumer():
        while True:
            msg = topic.consume("group-A")
            if msg is None:
                break
            with c_lock:
                consumed_ids.append(msg.message_id)
            topic._groups["group-A"].ack(msg.message_id)

    threads = [threading.Thread(target=consumer) for _ in range(5)]
    for t in threads: t.start()
    for t in threads: t.join()

    # Check no duplicates
    assert len(consumed_ids) == len(set(consumed_ids)), \
        f"Duplicate deliveries: {len(consumed_ids) - len(set(consumed_ids))} duplicates"
    # Check all messages were delivered
    assert set(consumed_ids) == published_ids, "Some messages were not delivered"
    print(f"PASS: test_no_duplicate_delivery ({len(consumed_ids)} messages)")


# ─────────────────────────────────────────────────────────────
# TEST 2: Multiple consumer groups each see all messages
# 3 groups subscribe to the same topic.
# Each group must consume all 100 published messages independently.
# ─────────────────────────────────────────────────────────────
def test_each_group_sees_all_messages():
    topic = Topic("events")
    groups = ["group-X", "group-Y", "group-Z"]
    for gid in groups:
        topic.subscribe(gid)

    for i in range(100):
        topic.publish(f"event-{i}")

    per_group_consumed = {gid: [] for gid in groups}

    def consume_all(gid):
        while True:
            msg = topic.consume(gid)
            if msg is None:
                break
            per_group_consumed[gid].append(msg.message_id)
            topic._groups[gid].ack(msg.message_id)

    threads = [threading.Thread(target=consume_all, args=(gid,)) for gid in groups]
    for t in threads: t.start()
    for t in threads: t.join()

    for gid in groups:
        assert len(per_group_consumed[gid]) == 100, \
            f"{gid}: expected 100, got {len(per_group_consumed[gid])}"

    print("PASS: test_each_group_sees_all_messages")


# ─────────────────────────────────────────────────────────────
# TEST 3: Concurrent publishers — no messages lost
# 10 publisher threads each publish 50 messages concurrently.
# Total published: 500. Consuming group must see all 500.
# ─────────────────────────────────────────────────────────────
def test_concurrent_publishers_no_loss():
    topic = Topic("logs")
    topic.subscribe("group-log")

    published = []
    p_lock = threading.Lock()

    def publisher(tid):
        for i in range(50):
            msg = topic.publish(f"log-{tid}-{i}")
            with p_lock:
                published.append(msg.message_id)

    pub_threads = [threading.Thread(target=publisher, args=(i,)) for i in range(10)]
    for t in pub_threads: t.start()
    for t in pub_threads: t.join()

    assert len(published) == 500

    consumed = []
    while True:
        msg = topic.consume("group-log")
        if msg is None:
            break
        consumed.append(msg.message_id)
        topic._groups["group-log"].ack(msg.message_id)

    assert len(consumed) == 500, f"Expected 500 consumed, got {len(consumed)}"
    assert set(consumed) == set(published), "Consumed set != published set"
    print("PASS: test_concurrent_publishers_no_loss")


# ─────────────────────────────────────────────────────────────
# TEST 4: Unacknowledged messages moved to DLQ after max_retries
# Publish 1 message; consumer always nacks it.
# After max_retries, message must appear in DLQ.
# ─────────────────────────────────────────────────────────────
def test_dlq_after_max_retries():
    topic = Topic("payments")
    topic.subscribe("group-pay", ack_timeout=0.05, max_retries=2)

    topic.publish("payment-001")

    # Consume and nack 3 times (max_retries=2 means after 3 deliveries → DLQ)
    for _ in range(3):
        msg = topic.consume("group-pay")
        if msg:
            topic._groups["group-pay"].nack(msg.message_id)
        time.sleep(0.06)  # let ack_timeout expire

    # After max_retries exceeded, message should be in DLQ
    # Trigger the reaper by attempting one more consume
    topic.consume("group-pay")

    assert topic.dlq.size() >= 1, f"Expected message in DLQ, size={topic.dlq.size()}"
    print("PASS: test_dlq_after_max_retries")


if __name__ == "__main__":
    test_no_duplicate_delivery()
    test_each_group_sees_all_messages()
    test_concurrent_publishers_no_loss()
    test_dlq_after_max_retries()
    print("All concurrency tests passed.")
```

**What each test verifies:**
- `test_no_duplicate_delivery`: The group's offset is guarded by `_lock`; two threads cannot both fetch the same offset. Without the lock, two threads could both read `offset=5`, both deliver `messages[5]`, and both advance the counter.
- `test_each_group_sees_all_messages`: Each `ConsumerGroup` maintains its own independent offset; publishing to a topic does not advance any group's offset automatically. All three groups start at offset 0 and consume independently.
- `test_concurrent_publishers_no_loss`: `topic._messages` is a list protected by `_msg_lock`. Without the lock, concurrent `append()` calls from multiple threads can corrupt the list (CPython's GIL makes bare appends safe, but the lock is required for non-CPython runtimes and for compound operations like publish+notify).
- `test_dlq_after_max_retries`: The DLQ path exercises the expiry-based redelivery loop; after `max_retries` attempts, the message must be quarantined rather than silently dropped or re-queued indefinitely.
