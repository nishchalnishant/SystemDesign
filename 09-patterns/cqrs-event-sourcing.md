# CQRS + Event Sourcing

> **Two patterns that work well independently and are transformative when combined.**

---

## Pattern Mindmap

```
CQRS + Event Sourcing
├── Core Problem
│   └── Single model can't be optimal for both writes (normalized) and reads (denormalized)
├── Key Components
│   ├── Command Side → validates, enforces business rules, writes to event store
│   ├── Query Side → read-optimized projections, can use different DB per read model
│   ├── Event Store → append-only log; source of truth for all state changes
│   └── Projections → derived read models rebuilt by replaying events
├── CQRS Alone
│   ├── Separate read/write models at application layer
│   ├── Write DB stays normalized; read DB is denormalized per query pattern
│   └── Read model updated async from write events (eventual consistency)
├── Event Sourcing Alone
│   ├── Store events, not current state — reconstruct state by replaying event log
│   ├── Full audit trail: every state transition is captured and replayable
│   └── Snapshots avoid replaying entire history on large aggregates
├── When to Use
│   ├── ✓ Read/write ratio heavily skewed (50K reads vs 5K writes/sec)
│   ├── ✓ Need complete audit trail (finance, healthcare, compliance)
│   └── ✓ Multiple read projections with different shapes from one write model
├── When NOT to Use
│   ├── ✗ Simple CRUD apps — overhead of separate models exceeds benefit
│   └── ✗ Strong read-after-write consistency required — async sync adds lag
├── Trade-offs
│   ├── Pro: Independent scaling of read and write paths
│   ├── Pro: Event log enables time-travel debugging and replay
│   ├── Con: Eventual consistency between write and read models
│   └── Con: Event schema evolution is hard — old events must still be replayable
├── Real-World Usage
│   ├── Axon Framework → Java CQRS/ES framework used in banking systems
│   ├── Microsoft → Azure Event Grid + Cosmos DB projections for read models
│   └── LinkedIn → feed as projection rebuilt from engagement events
└── Interview Angles
    ├── "How do reads stay consistent?" → eventual consistency, explain async projection
    ├── "What if projection breaks?" → replay events to rebuild from event store
    └── "When NOT to use CQRS?" → simple domains, strong consistency needs
```

---

## Part 1: CQRS (Command Query Responsibility Segregation)

### What Breaks Without CQRS?

An order management system has a single `Order` model backed by a normalized relational schema. Writes need strict validation and ACID consistency. Reads need denormalized data: the order list page joins `orders`, `users`, `order_items`, `products`, and `shipping` — a 5-table join running against the same database that's processing writes.

At 50K reads/sec and 5K writes/sec, adding a read replica helps, but the joins are still expensive. To speed up reads you denormalize — but now your write code must maintain that denormalized state, adding complexity and consistency bugs. To add a new read projection (say, a dashboard), you either add another expensive query or another denormalized table, both of which tangle into the write path.

**Why the naive fix fails**

A single model tries to be optimal for two contradictory goals simultaneously:
- Writes want normalized, validated, consistent data. Changing structure is dangerous.
- Reads want precomputed, denormalized, flexibly shaped data. Changing structure is free.

Indexing and caching can buy time but can't resolve the fundamental tension: the same code path handles both concerns, so every optimization for one degrades the other.

**The pattern as the minimal fix**

Separate the write path (Commands) from the read path (Queries) at the application layer. The Command side validates and persists to the write model. The read model is a separate projection, optimized purely for query patterns — denormalized, possibly in a different database, updated asynchronously from write events. You can evolve each independently.

---

### The Problem CQRS Solves

A single model trying to be optimal for both reads and writes is optimized for neither.

**Write model** wants:
- Normalized data (no redundancy)
- ACID transactions
- Business logic validation
- Consistent state

**Read model** wants:
- Denormalized data (everything in one query)
- Fast lookups (precomputed joins)
- Flexible projections (different clients need different shapes)
- High read throughput

> **Analogy**: A government forms two offices. The "Registration Office" (command side) handles all paperwork — strict validation, cross-referenced forms, everything must be accurate. The "Information Kiosk" (query side) provides fast answers — pre-printed brochures, pre-calculated summaries, optimized for how citizens ask questions. They serve different purposes and are designed differently.

---

### CQRS Architecture

```
         COMMAND SIDE                          QUERY SIDE
    (Write — normalized, consistent)    (Read — denormalized, fast)

  ┌─────────────────────┐              ┌─────────────────────┐
  │   Command Handler   │              │    Query Handler    │
  │                     │              │                     │
  │ PlaceOrder          │              │ GetUserOrderHistory  │
  │ CancelOrder         │              │ GetOrderSummary     │
  │ ProcessPayment      │              │ GetDashboardMetrics │
  └──────────┬──────────┘              └──────────┬──────────┘
             │ writes to                           │ reads from
             ▼                                    ▼
  ┌─────────────────────┐              ┌─────────────────────┐
  │  Write Store        │              │  Read Store(s)       │
  │  (PostgreSQL        │──events──►  │  (Elasticsearch,     │
  │   normalized)       │              │   Redis,             │
  └─────────────────────┘              │   MongoDB views)     │
                                       └─────────────────────┘
```

The event bus (Kafka/Debezium) propagates changes from write store → read store asynchronously. The read store is a **projection** of the write store, shaped for specific query patterns.

---

### Implementation

#### Command Side

```java
// Command: Intent to change state
public record PlaceOrderCommand(
    String userId,
    List<OrderItem> items,
    String paymentMethodId
) {}

// Command Handler: validates + executes
@Service
@Transactional
public class OrderCommandHandler {

    public OrderId handle(PlaceOrderCommand cmd) {
        // Validate business rules
        User user = userRepository.findById(cmd.userId())
            .orElseThrow(() -> new UserNotFoundException(cmd.userId()));

        validateInventory(cmd.items());
        validatePaymentMethod(cmd.paymentMethodId());

        // Create order (write to normalized DB)
        Order order = Order.create(cmd.userId(), cmd.items());
        orderRepository.save(order);

        // Publish event (via Outbox Pattern)
        outboxRepository.save(new OrderPlacedEvent(order.getId(), order));

        return order.getId();
    }
}
```

#### Query Side (Projection)

```java
// Event consumer: maintains read model
@EventHandler
public class OrderProjection {

    @KafkaListener(topics = "order-placed")
    public void on(OrderPlacedEvent event) {
        // Build denormalized read model for "user order history"
        OrderSummaryView summary = OrderSummaryView.builder()
            .orderId(event.getOrderId())
            .userId(event.getUserId())
            .itemCount(event.getItems().size())
            .totalAmount(event.getTotalAmount())
            .productNames(event.getItems().stream().map(Item::getName).collect(toList()))
            .status("PENDING")
            .placedAt(event.getTimestamp())
            .build();

        // Write to read store (could be Elasticsearch, Redis, MongoDB)
        orderSummaryRepository.save(summary);
    }

    @KafkaListener(topics = "order-shipped")
    public void on(OrderShippedEvent event) {
        // Update read model when status changes
        orderSummaryRepository.updateStatus(event.getOrderId(), "SHIPPED",
            event.getTrackingNumber());
    }
}

// Query Handler: reads from read model
@Service
public class OrderQueryHandler {

    // One query, one fast read — no JOINs, no computation
    public List<OrderSummaryView> getUserOrderHistory(String userId, int limit) {
        return orderSummaryRepository.findByUserId(userId,
            PageRequest.of(0, limit, Sort.by("placedAt").descending()));
    }

    public DashboardMetrics getDashboardMetrics() {
        // Pre-aggregated read model — returns in < 1ms
        return dashboardRepository.getLatestMetrics();
    }
}
```

---

### When to Use CQRS

✅ **Use CQRS when:**
- Read and write load profiles are dramatically different (read-heavy system)
- Multiple clients need different data shapes (mobile vs desktop vs analytics)
- The read model involves complex aggregations that would slow writes
- Different teams own the read vs write paths

❌ **Don't use CQRS when:**
- Simple CRUD with no complex business logic
- Small team (operational overhead not worth it)
- Read and write models are naturally the same shape

---

## Part 2: Event Sourcing

### What Breaks Without Event Sourcing?

A fraud detection team investigates a disputed transaction. The `orders` table says `status=REFUNDED, total=0`. What was the original total? When did it change? Who changed it? The database has no answer — it only stores current state. The audit trail was an afterthought, added as a separate `order_history` table that devs sometimes forget to write to. It's incomplete.

The payments team wants to replay all events from the past 30 days through a new fraud model. Impossible — the raw events were never stored; only derived state remains.

A bug introduced on May 5 incorrectly applied discounts. Which orders were affected? You'd need to look at application logs and cross-reference the DB, hoping logs weren't rotated. You can't re-derive the correct state from what you have.

**Why the naive fix fails**

Adding audit tables, change data capture, or update timestamps treats the symptom. The root problem is that current-state storage actively destroys the information needed to reconstruct history. You can add logging, but it's separate from the data model — it can be bypassed, dropped, or diverge from actual DB state. You can never rewind the DB to an arbitrary past point.

**The pattern as the minimal fix**

Never update or delete records. Append only: every state change is a new event record — `OrderPlaced`, `PaymentProcessed`, `OrderShipped`, `OrderRefunded`. Current state is derived by replaying events from the beginning (or from a snapshot). The event log is the source of truth; the current-state view is a projection, always re-derivable. Debugging, auditing, and temporal queries become trivial because the full history is always present.

---

### The Problem Event Sourcing Solves

Traditional databases store **current state**. Event Sourcing stores **all state changes as events**.

> **Analogy**: A bank's ledger (traditional accounting) vs the bank's transaction journal (event sourcing). The ledger shows your current balance. The journal shows every deposit and withdrawal that resulted in that balance. With the journal, you can reconstruct the balance at any point in time, audit every change, and even "undo" a transaction. The ledger only tells you where you are now.

```
Traditional:
  orders table:
  order_id | status   | total | updated_at
  123      | SHIPPED  | 99.00 | 2026-05-13

Event Sourced:
  order_events table:
  order_id | seq | event_type         | data                      | timestamp
  123      | 1   | OrderPlaced        | {items: [...], total: 99} | 2026-05-01
  123      | 2   | PaymentProcessed   | {payment_id: "px_123"}    | 2026-05-01
  123      | 3   | OrderShipped       | {tracking: "FX12345"}     | 2026-05-13
  
  Current state = replay all events in sequence order
```

---

### Implementation

#### Aggregate with Event Sourcing

```java
public class Order {
    private String orderId;
    private String status;
    private List<OrderItem> items;
    private String paymentId;
    private List<DomainEvent> pendingEvents = new ArrayList<>();

    // Reconstruct from events (replay)
    public static Order reconstitute(List<DomainEvent> events) {
        Order order = new Order();
        events.forEach(order::apply);
        return order;
    }

    // Command: place a new order
    public void placeOrder(String userId, List<OrderItem> items) {
        // Validate
        if (this.status != null) throw new IllegalStateException("Order already exists");

        // Raise event (don't modify state directly)
        raiseEvent(new OrderPlacedEvent(UUID.randomUUID(), userId, items));
    }

    // Command: mark as shipped
    public void markShipped(String trackingNumber) {
        if (!"PAID".equals(this.status)) throw new InvalidStateException("Must be PAID to ship");
        raiseEvent(new OrderShippedEvent(this.orderId, trackingNumber));
    }

    private void raiseEvent(DomainEvent event) {
        apply(event);                  // Update in-memory state
        pendingEvents.add(event);     // Queue for persistence
    }

    // Event handlers (update state from event)
    private void apply(DomainEvent event) {
        switch (event) {
            case OrderPlacedEvent e -> {
                this.orderId = e.orderId();
                this.items = e.items();
                this.status = "PENDING";
            }
            case PaymentProcessedEvent e -> {
                this.paymentId = e.paymentId();
                this.status = "PAID";
            }
            case OrderShippedEvent e -> {
                this.status = "SHIPPED";
            }
        }
    }
}
```

#### Event Store

```sql
CREATE TABLE event_store (
    aggregate_id   VARCHAR(64),
    aggregate_type VARCHAR(100),      -- 'Order', 'Account', 'Product'
    sequence_num   BIGINT,            -- Monotonically increasing per aggregate
    event_type     VARCHAR(100),
    event_data     JSONB,
    metadata       JSONB,             -- Correlation ID, causation ID, user who triggered
    occurred_at    TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (aggregate_id, sequence_num),
    INDEX idx_aggregate_type_time (aggregate_type, occurred_at)
);
```

#### Optimistic Concurrency in Event Store

```java
public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
    // expectedVersion = last sequence_num we read
    // If another process wrote in the meantime: conflict!
    int currentVersion = eventStore.getLatestVersion(aggregateId);
    if (currentVersion != expectedVersion) {
        throw new OptimisticConcurrencyException(
            "Aggregate " + aggregateId + " modified concurrently");
    }

    for (int i = 0; i < events.size(); i++) {
        eventStore.append(aggregateId, expectedVersion + i + 1, events.get(i));
    }
}
```

---

### Snapshots (Performance Optimization)

Replaying 10,000 events to reconstruct an Order is too slow. **Snapshots** capture state periodically:

```java
// Take snapshot every N events
if (order.getVersion() % 50 == 0) {
    snapshotStore.save(new Snapshot(order.getId(), order.getVersion(),
        objectMapper.writeValueAsString(order)));
}

// Load: start from latest snapshot, replay only events after it
Order reconstitute(String orderId) {
    Optional<Snapshot> snapshot = snapshotStore.findLatest(orderId);
    Order order = snapshot
        .map(s -> objectMapper.readValue(s.getData(), Order.class))
        .orElse(new Order());

    int fromVersion = snapshot.map(Snapshot::getVersion).orElse(0);
    List<DomainEvent> recentEvents = eventStore.getEventsAfter(orderId, fromVersion);
    recentEvents.forEach(order::apply);
    return order;
}
```

---

### CQRS + Event Sourcing Together

The event store becomes the **single source of truth**. Events flow from the event store to build multiple read projections:

```
Command → Order.placeOrder() → OrderPlacedEvent
                                      │
                              EVENT STORE (append)
                                      │
                    ┌─────────────────┼──────────────────┐
                    ▼                 ▼                  ▼
           ┌─────────────┐  ┌──────────────┐  ┌─────────────────┐
           │ Order List  │  │ Analytics    │  │ Inventory       │
           │ Projection  │  │ Projection   │  │ Projection      │
           │ (MongoDB)   │  │ (ClickHouse) │  │ (Redis counter) │
           └─────────────┘  └──────────────┘  └─────────────────┘
```

**Replay = superpower**: If you add a new projection (e.g. "fraud detection model needs order history"), replay all historical events to build it. No data migration needed.

---

### Trade-offs

| | Traditional DB | Event Sourcing |
|--|----------------|----------------|
| **Query current state** | Simple SELECT | Replay events (or snapshot) |
| **Audit trail** | Need extra logging | Built-in (every event persisted) |
| **Time travel** | Not possible | Replay up to any point in time |
| **Schema evolution** | Migrations | Event versioning (upcasters) |
| **Storage** | Only current state | All events forever (grows unbounded) |
| **Complexity** | Simple CRUD | Significant paradigm shift |

---

### When to Use Event Sourcing

✅ **Use Event Sourcing when:**
- Audit trail is a hard requirement (finance, healthcare, legal)
- Need to replay events for debugging or testing
- Complex business state machines where event log adds clarity
- Multiple projections need to be derived from the same state changes

❌ **Avoid Event Sourcing when:**
- Simple CRUD applications with no audit requirements
- Team is unfamiliar with the pattern (steep learning curve)
- Simple reporting needs don't justify the complexity

---

## Interview Talking Points

**Q: "Explain CQRS and when you'd use it."**
> "CQRS separates the write model (commands) from the read model (queries). Commands go through business validation and write to a normalized store. Events from those writes propagate asynchronously to build denormalized read models optimized for each query pattern. I'd use it when read and write profiles diverge significantly — for example, a trading platform where orders are written one at a time but dashboards need pre-aggregated summaries. The trade-off is eventual consistency between read and write models."

**Q: "What's the advantage of Event Sourcing over just storing current state?"**
> "Three superpowers: (1) Audit trail — every state change is recorded immutably with who did it and when. (2) Time travel — reconstruct system state at any past timestamp by replaying events up to that point. (3) New projections — add a new read model at any time by replaying historical events; no need to re-query or migrate old data. The cost is complexity: event versioning as schemas evolve, snapshot management for performance, and a mental model shift from 'state' to 'events.'"
