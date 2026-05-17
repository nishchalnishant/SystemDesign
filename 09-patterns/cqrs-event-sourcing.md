# CQRS + Event Sourcing

> **Two patterns that work well independently and are transformative when combined.**

---

## Part 1: CQRS (Command Query Responsibility Segregation)

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
