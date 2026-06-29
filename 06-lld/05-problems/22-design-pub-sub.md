---
module: 06-lld
topic: Problems
status: interview-ready
tags: [06-lld, system-design, problems]
---
# Design Publisher-Subscriber (Event Bus)

> **Difficulty**: Medium
> **Topics**: Observer Pattern, Factory, Strategy, Decorator
> **Extension**: Dead letter queue, message replay, topic filtering by predicate, wildcard subscriptions

---

## What Breaks Without This Design?

```python
class OrderService:
    def __init__(self):
        self.email_svc = EmailService()         # hardcoded
        self.inventory_svc = InventoryService() # hardcoded
        self.analytics = AnalyticsService()     # hardcoded

    def complete_order(self, order_id: str) -> None:
        self.status = "COMPLETED"
        self.email_svc.send_confirmation(order_id)     # coupled
        self.inventory_svc.update_stock(order_id)      # coupled
        self.analytics.track_completion(order_id)      # coupled
        # Adding LoyaltyService requires editing this method
```

**Concrete failures**:
1. **Every new consumer requires editing the producer**: Adding `LoyaltyService.award_points()` means opening `OrderService.complete_order()` — violates OCP.
2. **One slow consumer blocks all others**: `send_confirmation()` takes 2 seconds because SMTP is slow — `update_stock()` waits behind it.
3. **Producer and consumer compiled together**: `OrderService` directly imports `EmailService` — changing the email provider requires redeploying `OrderService`.
4. **No retry on consumer failure**: If `analytics.track_completion()` throws, the exception propagates to `OrderService` — the order state is corrupted by an analytics bug.
5. **No message history**: Consumer that joins after the event fires misses it permanently — there is no replay.

---

## Derive the Class Structure

**Force 1 — Publisher must not know consumers**: Publisher knows only a `topic_name` (string). Extract `EventBus` as the mediator. Publisher calls `bus.publish(topic, event)`. Zero import of any consumer.

**Force 2 — Consumers subscribe independently**: `EmailService`, `InventoryService`, `AnalyticsService` each call `bus.subscribe(topic, handler)`. The event bus stores a `topic → list[Subscriber]` mapping.

**Force 3 — Delivery semantics vary**: Sync delivery (call subscriber in publisher thread), async delivery (put event in queue, worker threads call subscribers), filtered delivery (subscriber only receives events matching a predicate). Extract `DeliveryStrategy`.

**Force 4 — Failed deliveries must not be silently dropped**: Subscriber throwing an exception should not skip other subscribers. Failed messages go to a `DeadLetterQueue` for retry or inspection.

```
God class → EventBus (topics: dict[str, list[Subscriber]])
          → Subscriber (interface: on_event(event: Event))
             → EmailHandler, InventoryHandler, AnalyticsHandler
          → Topic (name: str, subscribers: list[Subscriber], dlq: DeadLetterQueue)
          → Event (topic: str, payload: dict, event_id: str, timestamp)
          → DeliveryStrategy (interface: deliver(event, subscribers))
             → SyncDelivery, AsyncDelivery
          → DeadLetterQueue (events: deque, max_retries: int)
          → EventFilter (interface: matches(event) → bool)
             → TopicFilter, PayloadPredicateFilter
```

---

## Opening Analogy

Think of a newspaper publisher. They print a "Sports" edition and a "Business" edition. They don't know who the readers are — readers subscribe to whichever edition they want. When the sports edition is printed, it is delivered to all sports subscribers. A new reader subscribing to sports starts receiving future editions — they don't get past papers unless the publisher has an archive. The editor (publisher) doesn't need to know the names of every subscriber before printing. That decoupling is exactly what a pub-sub system provides: publisher → topic → subscriber.

---

## Phase 1: Requirements

### Functional
- Publishers publish events to named topics.
- Subscribers register a handler function/object against a topic.
- On publish: all subscribers for the topic are invoked.
- Subscriber can unsubscribe at any time.
- Failed subscriber delivery retried up to `max_retries`; then sent to DLQ.
- Optional: subscriber can provide a filter predicate (only receive events matching condition).

### Non-Functional
- Publisher must not block on slow subscribers (async delivery option).
- One subscriber crash must not affect delivery to other subscribers.
- Event ordering per topic maintained in sync mode; best-effort in async mode.
- Thread-safe subscription registration and delivery.

---

## Phase 2: Use Cases

### Actors
- **Publisher** (e.g., `OrderService`) — produces events.
- **Subscriber** (e.g., `EmailService`, `InventoryService`) — consumes events.
- **EventBus** — routes events from topics to subscribers.

### UC1: Subscribe and Publish (Happy Path)
1. `email_handler = EmailHandler()`.
2. `bus.subscribe("order.completed", email_handler)`.
3. `bus.subscribe("order.completed", inventory_handler)`.
4. `order_service.complete_order()` → calls `bus.publish("order.completed", event)`.
5. Bus delivers event to `email_handler` and `inventory_handler` in sequence (sync) or parallel (async).

### UC2: Subscriber Failure with DLQ
1. `analytics_handler.on_event(event)` raises `ConnectionError`.
2. Bus catches exception; does NOT propagate to publisher.
3. Bus retries `analytics_handler` up to `max_retries=3` with exponential backoff.
4. After 3 failures: event enqueued to `DeadLetterQueue`.
5. Other subscribers (`email_handler`, `inventory_handler`) receive the event normally.

### UC3: Filtered Subscription
1. `discount_handler` subscribes to `"order.completed"` with filter: `payload['amount'] > 5000`.
2. An event for an order of ₹3,000 published.
3. Bus checks filter: `3000 > 5000 → False` — `discount_handler` not invoked.
4. An event for ₹7,000 published → filter passes → `discount_handler` invoked.

### UC4: Unsubscribe
1. A/B test ends; `ExperimentHandler` unsubscribes from `"user.signup"`.
2. `bus.unsubscribe("user.signup", experiment_handler)`.
3. Future events on `"user.signup"` no longer reach `ExperimentHandler`.

---

## Phase 3: Class Diagram

```
┌────────────────────────────────────────────┐
│                  EventBus                  │  <<Mediator>>
│────────────────────────────────────────────│
│ - topics: dict[str, Topic]                 │
│ - delivery: DeliveryStrategy               │
│────────────────────────────────────────────│
│ + subscribe(topic, handler, filter?) → sub │
│ + unsubscribe(topic, handler) → void       │
│ + publish(topic, payload) → void           │
│ + get_dlq(topic) → DeadLetterQueue         │
└────────────────────────────────────────────┘
         │ contains
         ▼
┌──────────────────────────────────────┐
│                Topic                 │
│──────────────────────────────────────│
│ - name: str                          │
│ - subscriptions: list[Subscription]  │
│ - dlq: DeadLetterQueue               │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐
│            Subscription              │
│──────────────────────────────────────│
│ - handler: Subscriber                │
│ - event_filter: EventFilter | None   │
│ - max_retries: int                   │
│ - retry_count: int                   │
└──────────────────────────────────────┘

┌──────────────────────────────────────┐   ┌──────────────────────────────┐
│            Subscriber                │   │         EventFilter          │
│  <<interface>>                       │   │  <<interface>>               │
│──────────────────────────────────────│   │──────────────────────────────│
│ + on_event(event: Event) → void      │   │ + matches(event) → bool      │
└──────────────────────────────────────┘   └──────────────────────────────┘
▲        ▲           ▲                      ▲             ▲
Email  Inventory  Analytics            TopicFilter  PayloadPredicate

┌──────────────────────────────────────┐
│          DeliveryStrategy            │
│  <<interface>>                       │
│──────────────────────────────────────│
│ + deliver(event, subs) → void        │
└──────────────────────────────────────┘
▲                  ▲
SyncDelivery    AsyncDelivery (thread pool)
```

---

## Phase 4: Design Patterns Applied

### 1. Observer Pattern — Core architecture
**Why:** Publisher is the Subject; Subscribers are Observers. The EventBus is the registry that decouples them. Publisher never imports consumers; consumers never import each other.

**How:** `EventBus` holds `topic → list[Subscription]`. `publish()` iterates subscribers for the topic and calls `on_event()` on each handler. Each `Subscription` wraps a `Subscriber` with optional filter and retry state.

### 2. Strategy Pattern — Delivery
**Why:** Sync delivery (call subscriber in caller thread, propagate exceptions) vs. async delivery (submit to thread pool, publisher returns immediately) are interchangeable delivery behaviours with the same interface.

**How:** `EventBus` takes `DeliveryStrategy` in its constructor. `SyncDelivery` calls handlers one by one. `AsyncDelivery` submits each handler call to a `ThreadPoolExecutor`.

### 3. Decorator Pattern — Retry and Dead Letter
**Why:** Retry logic and DLQ forwarding should not be inside the `Subscriber` implementation. Wrap each subscriber invocation in a retry decorator — handler sees only the clean `on_event()` interface.

**How:** `Subscription` wraps the `Subscriber`. Delivery code catches exceptions per subscription, retries up to `max_retries`, and pushes to DLQ after exhaustion — the handler and all other subscribers are unaffected.

---

## Phase 5: Key Python Implementation

```python
from abc import ABC, abstractmethod
from datetime import datetime
from collections import deque
from concurrent.futures import ThreadPoolExecutor
import uuid, time

# ── Domain types ──────────────────────────────────────────────────────────────

class Event:
    def __init__(self, topic, payload):
        self.topic = topic
        self.payload = payload
        self.event_id = str(uuid.uuid4())
        self.published_at = datetime.now()

# ── Subscriber interface ──────────────────────────────────────────────────────

class Subscriber(ABC):
    @abstractmethod
    def on_event(self, event): ...

# ── Event Filter ──────────────────────────────────────────────────────────────

class EventFilter(ABC):
    @abstractmethod
    def matches(self, event): ...

class PayloadPredicateFilter(EventFilter):
    def __init__(self, predicate):
        self._predicate = predicate

    def matches(self, event):
        return self._predicate(event.payload)

# ── Dead Letter Queue ─────────────────────────────────────────────────────────

class FailedDelivery:
    def __init__(self, event, subscriber_name, error):
        self.event = event
        self.subscriber_name = subscriber_name
        self.error = error
        self.failed_at = datetime.now()

class DeadLetterQueue:
    def __init__(self, max_size=1000):
        self._queue = deque(maxlen=max_size)

    def enqueue(self, failure):
        self._queue.append(failure)
        print(f"[DLQ] Enqueued: event {failure.event.event_id} for {failure.subscriber_name}: {failure.error}")

    def drain(self):
        items = list(self._queue)
        self._queue.clear()
        return items

# ── Subscription ──────────────────────────────────────────────────────────────

class Subscription:
    def __init__(self, handler, event_filter=None, max_retries=3):
        self.handler = handler
        self.filter = event_filter
        self.max_retries = max_retries

    def should_deliver(self, event):
        return self.filter is None or self.filter.matches(event)

    def deliver(self, event, dlq):
        if not self.should_deliver(event):
            return
        for attempt in range(self.max_retries + 1):
            try:
                self.handler.on_event(event)
                return
            except Exception as e:
                if attempt < self.max_retries:
                    time.sleep(0.1 * (2 ** attempt))  # exponential backoff
                else:
                    dlq.enqueue(FailedDelivery(
                        event,
                        type(self.handler).__name__,
                        str(e),
                    ))

# ── Topic ─────────────────────────────────────────────────────────────────────

class Topic:
    def __init__(self, name):
        self.name = name
        self._subscriptions = []
        self.dlq = DeadLetterQueue()

    def add_subscription(self, sub):
        self._subscriptions.append(sub)

    def remove_subscription(self, handler):
        self._subscriptions = [s for s in self._subscriptions if s.handler is not handler]

    def deliver_all(self, event):
        for sub in list(self._subscriptions):
            sub.deliver(event, self.dlq)

# ── Delivery Strategy ─────────────────────────────────────────────────────────

class DeliveryStrategy(ABC):
    @abstractmethod
    def deliver(self, event, topic): ...

class SyncDelivery(DeliveryStrategy):
    def deliver(self, event, topic):
        topic.deliver_all(event)

class AsyncDelivery(DeliveryStrategy):
    def __init__(self, max_workers=4):
        self._pool = ThreadPoolExecutor(max_workers=max_workers)

    def deliver(self, event, topic):
        self._pool.submit(topic.deliver_all, event)

# ── EventBus ──────────────────────────────────────────────────────────────────

class EventBus:
    def __init__(self, delivery=None):
        self._topics = {}
        self._delivery = delivery or SyncDelivery()

    def _get_or_create_topic(self, topic_name):
        if topic_name not in self._topics:
            self._topics[topic_name] = Topic(topic_name)
        return self._topics[topic_name]

    def subscribe(self, topic_name, handler, event_filter=None, max_retries=3):
        topic = self._get_or_create_topic(topic_name)
        topic.add_subscription(Subscription(handler, event_filter, max_retries))
        print(f"[BUS] {type(handler).__name__} subscribed to '{topic_name}'")

    def unsubscribe(self, topic_name, handler):
        if topic_name in self._topics:
            self._topics[topic_name].remove_subscription(handler)

    def publish(self, topic_name, payload):
        event = Event(topic=topic_name, payload=payload)
        print(f"[BUS] Published event {event.event_id} to '{topic_name}'")
        if topic_name in self._topics:
            self._delivery.deliver(event, self._topics[topic_name])

    def get_dlq(self, topic_name):
        return self._topics[topic_name].dlq if topic_name in self._topics else None

# ── Concrete Subscribers ──────────────────────────────────────────────────────

class EmailHandler(Subscriber):
    def on_event(self, event):
        print(f"[EMAIL] Sending confirmation for order {event.payload.get('order_id')}")

class InventoryHandler(Subscriber):
    def on_event(self, event):
        print(f"[INVENTORY] Updating stock for order {event.payload.get('order_id')}")

class FlakyAnalyticsHandler(Subscriber):
    def __init__(self):
        self._call_count = 0
    def on_event(self, event):
        self._call_count += 1
        if self._call_count <= 2:
            raise ConnectionError("Analytics DB down")
        print(f"[ANALYTICS] Tracked event {event.event_id}")

# ── Demo ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    bus = EventBus(delivery=SyncDelivery())

    email = EmailHandler()
    inventory = InventoryHandler()
    analytics = FlakyAnalyticsHandler()

    bus.subscribe("order.completed", email)
    bus.subscribe("order.completed", inventory)
    bus.subscribe("order.completed", analytics, max_retries=3)

    # Filtered subscription: only large orders
    class LoyaltyHandler(Subscriber):
        def on_event(self, event):
            print(f"[LOYALTY] Awarding points for ₹{event.payload['amount']} order")

    bus.subscribe(
        "order.completed",
        LoyaltyHandler(),
        event_filter=PayloadPredicateFilter(lambda p: p.get("amount", 0) > 5000),
    )

    print("\n--- Publishing small order (₹3000) ---")
    bus.publish("order.completed", {"order_id": "ORD-001", "amount": 3000})

    print("\n--- Publishing large order (₹7500) ---")
    bus.publish("order.completed", {"order_id": "ORD-002", "amount": 7500})

    # Inspect DLQ
    dlq = bus.get_dlq("order.completed")
    if dlq:
        failures = dlq.drain()
        print(f"\n[DLQ] {len(failures)} failed deliveries")
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Delivery mode | Sync by default | Async (ThreadPoolExecutor) | Sync is simple, predictable, easy to test. Async needed when subscribers are slow (network I/O) |
| Per-subscription retry | Retry per subscription with backoff | Global retry queue | Per-subscription retry is simpler; global queue is more durable but adds infrastructure |
| DLQ in-process | `deque` in memory | Separate DLQ service (SQS) | In-process is for LLD scope; production: SQS Dead Letter Queue with visibility timeout |
| Wildcard topics | Not implemented | Trie-based topic matching (`order.*`) | Adds complexity; implement with a prefix-tree router if wildcards are required |

### Extensions

**Message replay (event sourcing integration):**
```python
class EventStore:
    def __init__(self) -> None:
        self._log: list[Event] = []
    def append(self, event: Event) -> None:
        self._log.append(event)
    def replay(self, topic: str, bus: EventBus) -> None:
        for event in self._log:
            if event.topic == topic:
                bus.publish(event.topic, event.payload)
```

**Wildcard topic routing:**
```python
class WildcardEventBus(EventBus):
    def publish(self, topic_name: str, payload: dict) -> None:
        event = Event(topic=topic_name, payload=payload)
        for registered_topic, topic_obj in self._topics.items():
            # match 'order.*' to 'order.completed', 'order.cancelled'
            pattern = registered_topic.replace("*", "")
            if topic_name.startswith(pattern) or registered_topic == topic_name:
                self._delivery.deliver(event, topic_obj)
```

---

## Interviewer Follow-Up Questions

- "How is pub-sub different from a simple Observer pattern?" → Observer pattern couples the Subject to its Observers — the Subject holds a reference to each Observer and calls them directly. Pub-sub adds a third party (the EventBus) so Publisher and Subscriber are completely decoupled — they don't import each other; they only know the EventBus and the topic name. Observer is synchronous and usually in-process. Pub-sub can be async, distributed, or durable. The EventBus adds routing, filtering, delivery guarantees, and DLQ capabilities that a plain Observer registry doesn't have.
- "What happens if the EventBus process crashes — are messages lost?" → In the LLD in-process implementation: yes, in-flight messages are lost. For durability: write events to a persistent log (Kafka topic, SQS queue, or DB `events` table) before delivery. On restart: replay from the log. This is event sourcing — the event log is the source of truth. For Amazon SDE-2: say "the in-memory bus is suitable for LLD scope; production would use Kafka or SQS for durability."
- "How do you guarantee exactly-once delivery?" → Exactly-once is impossible in a distributed system without coordination. In practice: guarantee at-least-once delivery (retry on failure) + idempotent consumers (consumers handle the same event twice without side effects). Idempotency key: each `Event` has a UUID `event_id`. Consumer stores processed event IDs in a `processed_events` set; on duplicate delivery, it skips processing. This is the standard contract across all production pub-sub systems (Kafka, SQS).
- "How would you add wildcard subscriptions — subscribe to `order.*` and receive `order.completed`, `order.cancelled`?" → Replace the exact-match `dict[str, Topic]` lookup with a routing table that supports prefix matching. Use a Trie data structure where each node is a topic segment (split by `.`). On `publish("order.completed", ...)`: walk the Trie for `["order", "completed"]` and collect all handlers registered at `order`, `order.*`, and `order.completed`. Amazon's EventBridge uses this pattern — exact match plus `prefix`, `suffix`, and `anything-but` filtering.
- "Two threads publish to the same topic simultaneously. Is your implementation thread-safe?" → The `Topic` class uses a `threading.Lock` for both `add_subscription()` and `deliver_all()`. The list of subscriptions is copied under the lock before iterating, so a concurrent unsubscribe during delivery does not cause a `list modified during iteration` error. The `EventBus._topics` dict is also protected by a lock for topic creation. Thread safety is at the data structure level; subscriber handlers are called without holding the lock (to avoid holding locks during I/O).
