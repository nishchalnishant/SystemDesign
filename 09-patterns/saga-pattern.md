# Saga Pattern

> **Distributed transactions without 2PC: coordinating multi-service workflows through a sequence of local transactions with compensating actions.**

---

## Pattern Mindmap

```
Saga Pattern
├── Core Problem
│   └── Distributed transactions across services without 2PC global lock
├── Key Components
│   ├── Local Transaction → each service commits to its own DB independently
│   ├── Compensating Transaction → semantic undo (forward action, not DB ROLLBACK)
│   ├── Choreography → services react to events, no central coordinator
│   └── Orchestrator → single process issues commands, tracks saga state
├── When to Use
│   ├── ✓ Multi-service workflows that must stay consistent (order, payment, inventory)
│   ├── ✓ Long-running business transactions (minutes to hours)
│   └── ✓ Services own separate databases — no shared DB transaction possible
├── When NOT to Use
│   ├── ✗ Single service with one DB (just use a local ACID transaction)
│   └── ✗ Need true isolation — saga intermediate states are visible to other readers
├── Trade-offs
│   ├── Pro: No distributed lock, no coordinator SPOF, scales horizontally
│   ├── Pro: Each service independently deployable and failure-isolated
│   ├── Con: Compensating transactions are complex to design and test
│   └── Con: Eventual consistency — system is temporarily inconsistent during execution
├── Choreography vs Orchestration
│   ├── Choreography → no SPOF, hard to trace flow, good for simple linear sagas
│   └── Orchestration → explicit flow control, easier debugging, coordinator can be SPOF
├── Real-World Usage
│   ├── Amazon → order saga: Order → Reserve Inventory → Charge Payment → Ship
│   ├── Uber → trip saga: Request → Match Driver → Charge Card → Complete Trip
│   └── Netflix → account saga: Signup → Payment → Profile Creation → Welcome Email
└── Interview Angles
    ├── "How do you handle partial failures?" → describe compensating transactions
    ├── "Choreography vs orchestration?" → trade SPOF for debuggability
    └── "How is this different from 2PC?" → no blocking locks, eventual consistency
```

---

## What Breaks Without This Pattern?

An e-commerce order touches three services: Order, Inventory, Payment. Without coordination, this happens:

1. OrderService writes `order_id=789, status=PENDING` to its DB — success.
2. InventoryService reserves 2 units of SKU-42 — success.
3. PaymentService charges the card — the bank rejects it (insufficient funds).

Now you have reserved inventory that will never be used and an order stuck in PENDING forever. No single `ROLLBACK` fixes this — each service committed to its own database independently.

**The naive fix: Two-Phase Commit (2PC)**

2PC adds a coordinator that asks every participant "can you commit?" before anyone does. This violates two properties that distributed systems need:
- **Blocking lock**: All participants hold locks from Phase 1 until the coordinator says go. If the coordinator crashes between phases, every participant is locked indefinitely.
- **Coordinator SPOF**: The coordinator must be highly available and persistent. At scale (hundreds of services, thousands of transactions/sec), this is a bottleneck and a single point of failure.

**The pattern as the minimal fix**

The Saga is the minimum addition that works: instead of one atomic distributed transaction, use a sequence of local transactions. Each service commits independently and publishes an event. If any step fails, each prior step is reversed by a compensating transaction (a new forward transaction that semantically undoes the previous one — not a DB ROLLBACK). No global lock, no coordinator SPOF, no blocked threads.

---

## The Problem: Distributed Transactions

When a business operation spans multiple services (each with their own database), how do you maintain data consistency without a distributed transaction (2PC)?

> **Analogy**: Planning a vacation requires booking a flight, hotel, and rental car. Each is independent — they don't share a single reservation system. If the hotel is unavailable after you've booked the flight, you cancel the flight (compensating action). The "distributed transaction" is implemented as a series of local actions with rollback steps if anything fails. This is exactly the Saga pattern.

---

## What is a Saga?

A Saga is a sequence of local transactions, where each transaction:
1. Updates its own service's database
2. Publishes an event or message to trigger the next step
3. If any step fails, executes compensating transactions in reverse order

```
Order Saga:
  Step 1: OrderService    → Create order (PENDING)
  Step 2: InventoryService → Reserve items
  Step 3: PaymentService   → Charge card
  Step 4: OrderService    → Mark order CONFIRMED + notify user

Compensating transactions (on failure):
  If Step 3 fails: Inventory rollback (release reserved items)
                   Order rollback (mark order FAILED)
  If Step 2 fails: Order rollback only
```

---

## Implementation 1: Choreography

Services react to events autonomously. No central coordinator.

```
┌──────────────┐          ┌─────────────────┐
│ Order Service│          │Inventory Service │
│ Creates order│─OrderCreated──►│ Reserves stock  │
│ (PENDING)    │          │                 │─StockReserved──►┐
└──────────────┘          └─────────────────┘                │
                                                             ▼
                                                  ┌────────────────┐
                                                  │Payment Service │
                                                  │ Charges card   │
                                                  └────────────────┘
                                                         │
                                              PaymentCompleted
                                                         │
                                              ┌──────────▼───────┐
                                              │ Order Service    │
                                              │ Mark CONFIRMED   │
                                              └──────────────────┘

Compensation chain (PaymentFailed):
  PaymentService → publishes PaymentFailed
  InventoryService listens → publishes StockReleased
  OrderService listens → marks order FAILED
```

### Choreography Implementation

```java
// Order Service
@EventHandler
public class OrderSagaHandler {

    @KafkaListener(topics = "payment-completed")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.getOrderId());
        order.confirm();
        orderRepository.save(order);
        notificationService.sendConfirmation(order);
    }

    @KafkaListener(topics = "payment-failed")
    public void onPaymentFailed(PaymentFailedEvent event) {
        Order order = orderRepository.findById(event.getOrderId());
        order.fail("Payment failed: " + event.getReason());
        orderRepository.save(order);
        // No further compensation needed — order was never confirmed
    }

    @KafkaListener(topics = "stock-reserve-failed")
    public void onStockReserveFailed(StockReserveFailedEvent event) {
        Order order = orderRepository.findById(event.getOrderId());
        order.fail("Out of stock");
        orderRepository.save(order);
    }
}

// Inventory Service
@EventHandler
public class InventorySagaHandler {

    @KafkaListener(topics = "order-created")
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            inventoryService.reserve(event.getOrderId(), event.getItems());
            eventPublisher.publish(new StockReservedEvent(event.getOrderId()));
        } catch (InsufficientStockException e) {
            eventPublisher.publish(new StockReserveFailedEvent(event.getOrderId(), e.getMessage()));
        }
    }

    @KafkaListener(topics = "payment-failed")
    public void compensate(PaymentFailedEvent event) {
        // Compensation: release reserved stock
        inventoryService.release(event.getOrderId());
        eventPublisher.publish(new StockReleasedEvent(event.getOrderId()));
    }
}
```

**Choreography: Pros and Cons**
```
✓ Simple: No central coordinator, services are loosely coupled
✓ No single point of failure
✗ Hard to visualize overall flow (distributed across services)
✗ Implicit dependencies: hard to track which service handles which event
✗ Debugging complex: trace across multiple services' logs
✓ Use when: Simple linear flows with 3-4 steps
```

---

## Implementation 2: Orchestration

A central Saga Orchestrator directs each step explicitly.

```
┌─────────────────────────────────────────────────────────┐
│                 ORDER SAGA ORCHESTRATOR                  │
│                                                         │
│  Step 1: reserve_inventory(order_id, items)             │
│           ↓ success                                     │
│  Step 2: charge_payment(order_id, amount)               │
│           ↓ success                                     │
│  Step 3: confirm_order(order_id)                        │
│           ↓ success                                     │
│  Step 4: send_notification(order_id)                    │
│                                                         │
│  On failure at Step N:                                  │
│    Execute compensation for Steps N-1 → 1               │
└─────────────────────────────────────────────────────────┘
        │           │           │           │
        ▼           ▼           ▼           ▼
  Inventory    Payment      Order        Notification
  Service      Service      Service      Service
```

### Orchestration Implementation

```java
@Service
public class OrderSagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final OrderClient orderClient;

    @Transactional
    public void execute(String orderId, List<OrderItem> items, BigDecimal amount) {
        SagaState state = new SagaState(orderId);
        sagaStateRepository.save(state);

        try {
            // Step 1
            state.setStep("RESERVING_INVENTORY");
            sagaStateRepository.save(state);
            inventoryClient.reserve(orderId, items);  // Synchronous or async
            state.setInventoryReserved(true);

            // Step 2
            state.setStep("CHARGING_PAYMENT");
            sagaStateRepository.save(state);
            String paymentId = paymentClient.charge(orderId, amount);
            state.setPaymentId(paymentId);
            state.setPaymentCharged(true);

            // Step 3
            state.setStep("CONFIRMING_ORDER");
            sagaStateRepository.save(state);
            orderClient.confirm(orderId, paymentId);
            state.setStep("COMPLETED");
            sagaStateRepository.save(state);

        } catch (InventoryException e) {
            state.setStep("FAILED");
            state.setFailureReason("Inventory: " + e.getMessage());
            sagaStateRepository.save(state);
            orderClient.fail(orderId, "Out of stock");
            // No compensation needed — inventory was never reserved

        } catch (PaymentException e) {
            state.setStep("COMPENSATING");
            sagaStateRepository.save(state);
            // Compensation: release inventory
            inventoryClient.release(orderId);  // Compensating transaction
            state.setInventoryReserved(false);
            orderClient.fail(orderId, "Payment failed: " + e.getMessage());
            state.setStep("FAILED");
            sagaStateRepository.save(state);
        }
    }
}

// Saga state table (for recovery after crash)
// CREATE TABLE saga_states (
//     saga_id    UUID PRIMARY KEY,
//     order_id   VARCHAR(64),
//     step       VARCHAR(50),
//     payload    JSONB,  -- preserves IDs needed for compensation
//     created_at TIMESTAMP,
//     updated_at TIMESTAMP
// );
```

**Orchestration: Pros and Cons**
```
✓ Explicit flow: easy to see and test the entire workflow
✓ Easy to track: single saga state record per transaction
✓ Easier debugging: one place to look for saga history
✗ Orchestrator = single point of failure (mitigate: run multiple instances)
✗ Tighter coupling between orchestrator and services
✓ Use when: Complex flows with 5+ steps, parallel branches, conditional logic
```

---

## Saga State Machine

```
States for Order Saga:
  STARTED
    → INVENTORY_RESERVING
    → INVENTORY_RESERVED
    → PAYMENT_CHARGING
    → PAYMENT_CHARGED
    → CONFIRMING
    → COMPLETED

  Failure paths:
    → INVENTORY_FAILED → FAILED
    → PAYMENT_FAILED → RELEASING_INVENTORY → COMPENSATION_COMPLETE → FAILED
    → CONFIRM_FAILED → REFUNDING → RELEASING_INVENTORY → COMPENSATION_COMPLETE → FAILED

Implementation: Store current state in DB. On orchestrator restart,
read state from DB and resume from last known-good step.
```

---

## Idempotency in Sagas (Critical)

Every saga step and compensation must be idempotent:

```java
// Inventory reservation (idempotent):
public void reserve(String orderId, List<Item> items) {
    // If already reserved for this orderId: no-op (return success)
    if (reservationRepository.existsByOrderId(orderId)) {
        return;  // Already reserved — idempotent success
    }
    // Proceed with reservation
    doReserve(orderId, items);
}

// Why? Orchestrator might retry failed steps.
// Without idempotency: retry → double reservation → oversold inventory
```

---

## Choreography vs Orchestration: When to Use

| | Choreography | Orchestration |
|--|--------------|---------------|
| **Coupling** | Loose (events) | Tighter (direct calls) |
| **Visibility** | Low (distributed) | High (centralized) |
| **Debugging** | Hard (trace across services) | Easy (saga state machine) |
| **Complex flows** | Hard (multiple event types) | Easy (explicit steps) |
| **Failure handling** | Hard to ensure all compensations run | Easy (orchestrator handles) |
| **Team size** | Works for small teams | Better for large teams |
| **Use when** | 3-4 simple linear steps | 5+ steps, parallel branches, complex logic |

---

## Interview Talking Points

**Q: "Why use Saga instead of 2-Phase Commit (2PC)?"**
> "2PC requires a distributed lock across all participants for the duration of the transaction — if the coordinator fails mid-transaction, all participants are stuck (blocking protocol). At microservices scale, this is catastrophic: one Payment Service slowdown locks the Inventory Service. Saga uses local transactions with compensating actions — each service only holds locks for its own operation. Failure at any step triggers compensations rather than a distributed lock. The trade-off: eventual consistency instead of ACID, and we must design compensating transactions for every step."

**Q: "What if a compensating transaction fails?"**
> "We must make compensating transactions retryable (idempotent). We log every compensation attempt in the saga state. If compensation fails: retry with exponential backoff. For cases where compensation is truly impossible (money already processed by payment provider), we fall back to human intervention + manual refund. This is why saga state records are so critical — the on-call engineer can see exactly what state the saga is in and what needs to be compensated manually."
