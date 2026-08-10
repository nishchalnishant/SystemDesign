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
> - Concurrency: Thread pools for workers consuming messages, thread-safe queues (`ConcurrentLinkedQueue`), and handling slow consumers without blocking the publisher. Use `CopyOnWriteArrayList` for subscriber/member lists and `ConcurrentHashMap` for topic/group registries.
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

```java
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

final class Message {
    private final String messageId;
    private final String topicName;
    private final Object payload;
    private final Instant publishedAt;

    Message(String messageId, String topicName, Object payload) {
        this.messageId = messageId;
        this.topicName = topicName;
        this.payload = payload;
        this.publishedAt = Instant.now();
    }

    String getMessageId() { return messageId; }
    String getTopicName() { return topicName; }
    Object getPayload() { return payload; }
    Instant getPublishedAt() { return publishedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        return messageId.equals(((Message) o).messageId);
    }

    @Override
    public int hashCode() { return Objects.hash(messageId); }

    @Override
    public String toString() {
        return String.format("Message{id=%s, topic=%s, payload=%s}", messageId, topicName, payload);
    }
}

final class InFlightMessage {
    private final Message message;
    private final String subscriberId;
    private final Instant deliveredAt;
    private int retryCount = 0;
    private Instant deadline;

    InFlightMessage(Message message, String subscriberId, Instant deliveredAt) {
        this.message = message;
        this.subscriberId = subscriberId;
        this.deliveredAt = deliveredAt;
        this.deadline = deliveredAt.plusSeconds(30);
    }

    Message getMessage() { return message; }
    String getSubscriberId() { return subscriberId; }
    int getRetryCount() { return retryCount; }
    void incrementRetryCount() { retryCount++; }

    boolean isExpired() {
        return Instant.now().isAfter(deadline);
    }
}

final class DeadLetterQueue {
    // (message, reason) pairs
    private final List<Map.Entry<Message, String>> messages = new CopyOnWriteArrayList<>();

    void add(Message message, String reason) {
        messages.add(Map.entry(message, reason));
        System.out.println(String.format("DLQ: message %s moved — %s", message.getMessageId(), reason));
    }

    List<Map.Entry<Message, String>> getMessages() { return messages; }
}

class ConsumerGroup {
    private final String groupId;
    private int offset = 0;
    private final List<String> members = new CopyOnWriteArrayList<>();
    private int rrIdx = 0;
    private final Map<String, InFlightMessage> inFlight = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    ConsumerGroup(String groupId) {
        this.groupId = groupId;
    }

    String getGroupId() { return groupId; }
    int getOffset() { return offset; }
    List<String> getMembers() { return members; }

    void addMember(String subscriberId) {
        if (!members.contains(subscriberId)) {
            members.add(subscriberId);
        }
    }

    Optional<String> nextMember() {
        if (members.isEmpty()) {
            return Optional.empty();
        }
        String m = members.get(rrIdx % members.size());
        rrIdx++;
        return Optional.of(m);
    }

    void ack(String messageId) {
        inFlight.remove(messageId);
    }

    void nack(String messageId) {
        InFlightMessage inf = inFlight.get(messageId);
        if (inf != null) {
            inf.incrementRetryCount();
        }
    }

    // Returns [toRedeliver, toDlq]
    ExpiredResult expiredMessages(int maxRetries) {
        List<Message> redeliver = new ArrayList<>();
        List<Message> dlq = new ArrayList<>();
        lock.lock();
        try {
            for (Iterator<Map.Entry<String, InFlightMessage>> it = inFlight.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, InFlightMessage> entry = it.next();
                InFlightMessage inflight = entry.getValue();
                if (inflight.isExpired()) {
                    if (inflight.getRetryCount() >= maxRetries) {
                        dlq.add(inflight.getMessage());
                        it.remove();
                    } else {
                        redeliver.add(inflight.getMessage());
                        it.remove();
                        offset -= 1; // simplified: requeue by backing offset
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return new ExpiredResult(redeliver, dlq);
    }

    void advanceOffset() { offset += 1; }

    boolean isBelowOffset(int size) { return offset < size; }

    void putInFlight(Message msg, InFlightMessage inflight) {
        inFlight.put(msg.getMessageId(), inflight);
    }

    static final class ExpiredResult {
        final List<Message> toRedeliver;
        final List<Message> toDlq;

        ExpiredResult(List<Message> toRedeliver, List<Message> toDlq) {
            this.toRedeliver = toRedeliver;
            this.toDlq = toDlq;
        }
    }
}

class Topic {
    static final int MAX_RETRIES = 3;

    private final String name;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final Map<String, ConsumerGroup> groups = new ConcurrentHashMap<>();
    private final DeadLetterQueue dlq = new DeadLetterQueue();
    private final ReentrantLock lock = new ReentrantLock();

    Topic(String name) {
        this.name = name;
    }

    String getName() { return name; }
    DeadLetterQueue getDlq() { return dlq; }

    Message publish(Object payload) {
        Message msg = new Message(UUID.randomUUID().toString(), name, payload);
        lock.lock();
        try {
            messages.add(msg);
        } finally {
            lock.unlock();
        }
        return msg;
    }

    ConsumerGroup createGroup(String groupId) {
        return groups.computeIfAbsent(groupId, ConsumerGroup::new);
    }

    void subscribe(String groupId, String subscriberId) {
        ConsumerGroup group = createGroup(groupId);
        group.addMember(subscriberId);
    }

    Optional<Message> consume(String groupId, String subscriberId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) {
            throw new IllegalArgumentException(String.format("Group %s not subscribed to %s", groupId, name));
        }
        if (!group.getMembers().contains(subscriberId)) {
            throw new IllegalArgumentException(String.format("Subscriber %s not in group %s", subscriberId, groupId));
        }

        Optional<String> expected = group.nextMember();
        if (expected.isEmpty() || !expected.get().equals(subscriberId)) {
            return Optional.empty(); // not this subscriber's turn
        }

        Message msg;
        lock.lock();
        try {
            if (!group.isBelowOffset(messages.size())) {
                return Optional.empty();
            }
            msg = messages.get(group.getOffset());
            group.advanceOffset();
        } finally {
            lock.unlock();
        }

        InFlightMessage inflight = new InFlightMessage(msg, subscriberId, Instant.now());
        group.putInFlight(msg, inflight);
        return Optional.of(msg);
    }

    void ack(String groupId, String messageId) {
        groups.get(groupId).ack(messageId);
    }

    void nack(String groupId, String messageId) {
        groups.get(groupId).nack(messageId);
    }

    void reapExpired() {
        for (ConsumerGroup group : groups.values()) {
            ConsumerGroup.ExpiredResult result = group.expiredMessages(MAX_RETRIES);
            for (Message msg : result.toDlq) {
                dlq.add(msg, String.format("exceeded %d retries", MAX_RETRIES));
            }
        }
    }
}

class PubSubSystem {
    private final Map<String, Topic> topics = new ConcurrentHashMap<>();

    Topic createTopic(String name) {
        return topics.computeIfAbsent(name, Topic::new);
    }

    Message publish(String topicName, Object payload) {
        Topic topic = topics.get(topicName);
        if (topic == null) {
            throw new IllegalArgumentException(String.format("Topic %s does not exist", topicName));
        }
        return topic.publish(payload);
    }

    void subscribe(String topicName, String groupId, String subscriberId) {
        Topic topic = topics.get(topicName);
        topic.subscribe(groupId, subscriberId);
    }

    Optional<Message> consume(String topicName, String groupId, String subscriberId) {
        return topics.get(topicName).consume(groupId, subscriberId);
    }

    void ack(String topicName, String groupId, String messageId) {
        topics.get(topicName).ack(groupId, messageId);
    }

    void nack(String topicName, String groupId, String messageId) {
        topics.get(topicName).nack(groupId, messageId);
    }
}
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

```java
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Idempotency store for exactly-once consumer side
class IdempotentConsumer {
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    void process(Message message) {
        if (processed.contains(message.getMessageId())) {
            return; // duplicate — skip
        }
        handle(message);
        processed.add(message.getMessageId());
    }

    private void handle(Message message) {
        // business logic here
    }
}
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

```java
void reapExpired() {
    for (ConsumerGroup group : groups.values()) {
        ConsumerGroup.ExpiredResult result = group.expiredMessages(MAX_RETRIES);
        for (Message msg : result.toDlq) {
            dlq.add(msg, String.format("exceeded %d retries in group %s", MAX_RETRIES, group.getGroupId()));
        }
        for (Message msg : result.toRedeliver) {
            // Re-insert at current offset position for redelivery
            messages.add(group.getOffset(), msg);
        }
    }
}
```

### 5. "Push vs pull delivery model"

**Pull**: consumer calls consume() when ready; controls its own pace; natural backpressure. Used by Kafka.

**Push**: system calls consumer's callback when a message arrives; lower latency; consumer can be overwhelmed. Used by SQS (push notification) and traditional message brokers.

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// Push model addition
class PushSubscription {
    private final Consumer<Message> callback;

    PushSubscription(Consumer<Message> callback) {
        this.callback = callback;
    }

    Consumer<Message> getCallback() { return callback; }
}

class Topic {
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final List<PushSubscription> pushSubscriptions = new CopyOnWriteArrayList<>();

    void publish(Object payload) {
        Message msg = new Message(java.util.UUID.randomUUID().toString(), "topic", payload);
        messages.add(msg);
        for (PushSubscription sub : pushSubscriptions) {
            Thread t = new Thread(() -> sub.getCallback().accept(msg));
            t.setDaemon(true);
            t.start();
        }
    }
}
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

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

// ── Minimal self-contained pub-sub implementation ──

final class Message {
    private final String messageId;
    private final String topicName;
    private final Object payload;
    private final long publishedAt;

    Message(String messageId, String topicName, Object payload) {
        this.messageId = messageId;
        this.topicName = topicName;
        this.payload = payload;
        this.publishedAt = System.nanoTime();
    }

    String getMessageId() { return messageId; }
    Object getPayload() { return payload; }
}

final class InFlight {
    final Message message;
    volatile long deadlineNanos;
    volatile int retries = 0;

    InFlight(Message message, long deadlineNanos) {
        this.message = message;
        this.deadlineNanos = deadlineNanos;
    }
}

class DLQ {
    private final List<Map.Entry<Message, String>> messages = new CopyOnWriteArrayList<>();

    void add(Message message, String reason) {
        messages.add(Map.entry(message, reason));
    }

    int size() {
        return messages.size();
    }
}

class ConsumerGroup {
    private final String groupId;
    private volatile int offset = 0;
    private final double ackTimeoutSeconds;
    private final int maxRetries;
    private final Map<String, InFlight> inFlight = new ConcurrentHashMap<>();
    private final Set<String> deliveredIds = ConcurrentHashMap.newKeySet();
    private final ReentrantLockHolder lock = new ReentrantLockHolder();

    ConsumerGroup(String groupId) {
        this(groupId, 5.0, 3);
    }

    ConsumerGroup(String groupId, double ackTimeoutSeconds, int maxRetries) {
        this.groupId = groupId;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
        this.maxRetries = maxRetries;
    }

    Message fetch(List<Message> messages, DLQ dlq) {
        lock.lock();
        try {
            // Redeliver expired in-flight messages first
            long now = System.nanoTime();
            List<Message> toRedeliver = new ArrayList<>();
            List<String> toDlq = new ArrayList<>();
            for (Map.Entry<String, InFlight> entry : inFlight.entrySet()) {
                InFlight inf = entry.getValue();
                if (now > inf.deadlineNanos) {
                    if (inf.retries >= maxRetries) {
                        toDlq.add(entry.getKey());
                    } else {
                        toRedeliver.add(inf.message);
                        inf.retries += 1;
                        inf.deadlineNanos = now + (long) (ackTimeoutSeconds * 1_000_000_000L);
                    }
                }
            }

            for (String mid : toDlq) {
                InFlight removed = inFlight.remove(mid);
                dlq.add(removed.message, "max_retries");
            }

            if (!toRedeliver.isEmpty()) {
                return toRedeliver.get(0);
            }

            // Fetch next from log
            if (offset < messages.size()) {
                Message msg = messages.get(offset);
                offset += 1;
                inFlight.put(msg.getMessageId(), new InFlight(msg, now + (long) (ackTimeoutSeconds * 1_000_000_000L)));
                deliveredIds.add(msg.getMessageId());
                return msg;
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    void ack(String messageId) {
        inFlight.remove(messageId);
    }

    void nack(String messageId) {
        InFlight inf = inFlight.get(messageId);
        if (inf != null) {
            inf.deadlineNanos = 0; // force immediate redeliver
        }
    }

    // Small wrapper so ConsumerGroup keeps a single lock member without importing
    // java.util.concurrent.locks.ReentrantLock at the top of every nested class.
    private static final class ReentrantLockHolder {
        private final java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
        void lock() { lock.lock(); }
        void unlock() { lock.unlock(); }
    }
}

class Topic {
    private final String name;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private final Map<String, ConsumerGroup> groups = new ConcurrentHashMap<>();
    final DLQ dlq = new DLQ();

    Topic(String name) {
        this.name = name;
    }

    Message publish(Object payload) {
        Message msg = new Message(UUID.randomUUID().toString(), name, payload);
        messages.add(msg);
        return msg;
    }

    ConsumerGroup subscribe(String groupId) {
        return subscribe(groupId, 5.0, 3);
    }

    ConsumerGroup subscribe(String groupId, double ackTimeout, int maxRetries) {
        return groups.computeIfAbsent(groupId, id -> new ConsumerGroup(id, ackTimeout, maxRetries));
    }

    Message consume(String groupId) {
        ConsumerGroup group = groups.get(groupId);
        if (group == null) {
            throw new IllegalArgumentException("No group: " + groupId);
        }
        List<Message> snapshot = new ArrayList<>(messages); // snapshot
        return group.fetch(snapshot, dlq);
    }

    ConsumerGroup getGroup(String groupId) {
        return groups.get(groupId);
    }
}


// ─────────────────────────────────────────────────────────────
// TEST 1: No message delivered twice within a consumer group
// 1 publisher sends 200 messages; 5 consumer threads race to consume.
// Each message_id must appear exactly once in the consumed set.
// ─────────────────────────────────────────────────────────────
public class PubSubConcurrencyTest {

    static void testNoDuplicateDelivery() throws InterruptedException {
        Topic topic = new Topic("orders");
        topic.subscribe("group-A");

        // Publish 200 messages (single thread — ordering guarantee)
        Set<String> publishedIds = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < 200; i++) {
            Message msg = topic.publish("order-" + i);
            publishedIds.add(msg.getMessageId());
        }

        List<String> consumedIds = new CopyOnWriteArrayList<>();

        Runnable consumer = () -> {
            while (true) {
                Message msg = topic.consume("group-A");
                if (msg == null) {
                    break;
                }
                consumedIds.add(msg.getMessageId());
                topic.getGroup("group-A").ack(msg.getMessageId());
            }
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Thread t = new Thread(consumer);
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();

        // Check no duplicates
        Set<String> uniqueConsumed = new HashSet<>(consumedIds);
        if (consumedIds.size() != uniqueConsumed.size()) {
            throw new AssertionError("Duplicate deliveries: "
                    + (consumedIds.size() - uniqueConsumed.size()) + " duplicates");
        }
        // Check all messages were delivered
        if (!uniqueConsumed.equals(publishedIds)) {
            throw new AssertionError("Some messages were not delivered");
        }
        System.out.println(String.format("PASS: testNoDuplicateDelivery (%d messages)", consumedIds.size()));
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Multiple consumer groups each see all messages
    // 3 groups subscribe to the same topic.
    // Each group must consume all 100 published messages independently.
    // ─────────────────────────────────────────────────────────────
    static void testEachGroupSeesAllMessages() throws InterruptedException {
        Topic topic = new Topic("events");
        List<String> groups = List.of("group-X", "group-Y", "group-Z");
        for (String gid : groups) {
            topic.subscribe(gid);
        }

        for (int i = 0; i < 100; i++) {
            topic.publish("event-" + i);
        }

        Map<String, List<String>> perGroupConsumed = new ConcurrentHashMap<>();
        for (String gid : groups) {
            perGroupConsumed.put(gid, new CopyOnWriteArrayList<>());
        }

        List<Thread> threads = new ArrayList<>();
        for (String gid : groups) {
            Thread t = new Thread(() -> {
                while (true) {
                    Message msg = topic.consume(gid);
                    if (msg == null) {
                        break;
                    }
                    perGroupConsumed.get(gid).add(msg.getMessageId());
                    topic.getGroup(gid).ack(msg.getMessageId());
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();

        for (String gid : groups) {
            int size = perGroupConsumed.get(gid).size();
            if (size != 100) {
                throw new AssertionError(String.format("%s: expected 100, got %d", gid, size));
            }
        }

        System.out.println("PASS: testEachGroupSeesAllMessages");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Concurrent publishers — no messages lost
    // 10 publisher threads each publish 50 messages concurrently.
    // Total published: 500. Consuming group must see all 500.
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentPublishersNoLoss() throws InterruptedException {
        Topic topic = new Topic("logs");
        topic.subscribe("group-log");

        List<String> published = new CopyOnWriteArrayList<>();

        List<Thread> pubThreads = new ArrayList<>();
        for (int t = 0; t < 10; t++) {
            final int tid = t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < 50; i++) {
                    Message msg = topic.publish(String.format("log-%d-%d", tid, i));
                    published.add(msg.getMessageId());
                }
            });
            pubThreads.add(thread);
            thread.start();
        }
        for (Thread thread : pubThreads) thread.join();

        if (published.size() != 500) {
            throw new AssertionError("Expected 500 published, got " + published.size());
        }

        List<String> consumed = new ArrayList<>();
        while (true) {
            Message msg = topic.consume("group-log");
            if (msg == null) {
                break;
            }
            consumed.add(msg.getMessageId());
            topic.getGroup("group-log").ack(msg.getMessageId());
        }

        if (consumed.size() != 500) {
            throw new AssertionError("Expected 500 consumed, got " + consumed.size());
        }
        if (!new HashSet<>(consumed).equals(new HashSet<>(published))) {
            throw new AssertionError("Consumed set != published set");
        }
        System.out.println("PASS: testConcurrentPublishersNoLoss");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 4: Unacknowledged messages moved to DLQ after max_retries
    // Publish 1 message; consumer always nacks it.
    // After max_retries, message must appear in DLQ.
    // ─────────────────────────────────────────────────────────────
    static void testDlqAfterMaxRetries() throws InterruptedException {
        Topic topic = new Topic("payments");
        topic.subscribe("group-pay", 0.05, 2);

        topic.publish("payment-001");

        // Consume and nack 3 times (max_retries=2 means after 3 deliveries → DLQ)
        for (int i = 0; i < 3; i++) {
            Message msg = topic.consume("group-pay");
            if (msg != null) {
                topic.getGroup("group-pay").nack(msg.getMessageId());
            }
            Thread.sleep(60); // let ack_timeout expire
        }

        // After max_retries exceeded, message should be in DLQ
        // Trigger the reaper by attempting one more consume
        topic.consume("group-pay");

        if (topic.dlq.size() < 1) {
            throw new AssertionError("Expected message in DLQ, size=" + topic.dlq.size());
        }
        System.out.println("PASS: testDlqAfterMaxRetries");
    }

    public static void main(String[] args) throws InterruptedException {
        testNoDuplicateDelivery();
        testEachGroupSeesAllMessages();
        testConcurrentPublishersNoLoss();
        testDlqAfterMaxRetries();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testNoDuplicateDelivery`: The group's `fetch` is guarded by a `ReentrantLock`; two threads cannot both fetch the same offset. Without the lock, two threads could both read `offset=5`, both deliver `messages[5]`, and both advance the counter.
- `testEachGroupSeesAllMessages`: Each `ConsumerGroup` maintains its own independent offset; publishing to a topic does not advance any group's offset automatically. All three groups start at offset 0 and consume independently.
- `testConcurrentPublishersNoLoss`: `Topic.messages` is a `CopyOnWriteArrayList`. Unlike a plain `ArrayList`, concurrent `add()` calls from multiple publisher threads are safe without external locking — each write copies the underlying array, so readers never see a partially-updated structure.
- `testDlqAfterMaxRetries`: The DLQ path exercises the expiry-based redelivery loop; after `max_retries` attempts, the message must be quarantined rather than silently dropped or re-queued indefinitely.

---

## Related

**Patterns applied here**

- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Interface Segregation](../../02-solid-principles/04-interface-segregation.md) · [Dependency Inversion](../../02-solid-principles/05-dependency-inversion.md)

**Concurrency**: [Producer-Consumer](../../04-concurrency/producer-consumer.md) · [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design Notification System](../02-frequent-problems/16-design-notification-system.md)
- [Design Comment System](../02-frequent-problems/10-design-comment-system.md)

Notifications and comment feeds are consumers of this.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
