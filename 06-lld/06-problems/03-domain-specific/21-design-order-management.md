> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an Order Management System (OMS) — tests your ability to handle complex, multi-step distributed workflows (like reserving inventory, charging payment, then confirming).
>
> **Key concepts:**
> - Core Entities: `Order`, `OrderLineItem`, `InventoryManager`, `PaymentProcessor`, `Warehouse`.
> - State Pattern: `Order` moves through `CREATED`, `PENDING_PAYMENT`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `CANCELLED`.
> - Saga Pattern (LLD variation): The orchestrator calls Inventory (reserve items), then Payment (charge card). If Payment fails, it must call Inventory (release items) to rollback the transaction.
> - Observer Pattern: Notifications (email, SMS) triggered by state transitions.
>
> **Key takeaway:** The main challenge is the rollback mechanism if a later step fails. Clearly define the `OrderOrchestrator` class that handles the try-catch block and invokes the compensating transactions (un-reserve inventory, refund payment) if necessary.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, order-management, state-pattern, saga, observer]
---
# Design an Order Management System

> **Difficulty**: Medium-Hard  
> **Asked at**: Amazon, Flipkart  
> **Key Patterns**: State (order status), Saga (multi-step order flow), Observer (status notifications)

---

## Understanding the Problem

Design an order management system that handles placing orders, moving them through a lifecycle (placed → confirmed → shipped → delivered), deducting inventory, and supporting cancellations.

---

## Clarifying Questions

**You**: "Does inventory deduction happen at order placement or at confirmation?"  
**Interviewer**: "Reserve at placement; actually deduct at confirmation."

**You**: "What happens if payment fails after we've reserved inventory?"  
**Interviewer**: "Release the reservation and mark the order FAILED."

**You**: "Can orders contain multiple products?"  
**Interviewer**: "Yes — an order has one or more OrderItems."

**You**: "Can a partially shipped order be cancelled?"  
**Interviewer**: "No — once any item is shipped, cancellation is blocked."

**You**: "Do we need idempotency for order placement?"  
**Interviewer**: "Yes — clients may retry; use an idempotency key."

**You**: "Do we need returns/refunds?"  
**Interviewer**: "Model it as a deep dive; out of scope for core design."

---

## Final Requirements

**In scope:**
1. Place orders with multiple items; reserve inventory at placement
2. Confirm order (deduct inventory, charge payment)
3. Ship order with a tracking number
4. Mark order delivered
5. Cancel order only if not yet shipped; release reserved inventory
6. Idempotency for order placement via client-supplied key
7. Status change notifications via Observer

**Out of scope:**
- Returns and refunds
- Multi-seller orders
- Partial fulfilment

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|----------------|
| Order | Aggregate root; tracks status, items, user, timestamps |
| OrderStatus | Enum: PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, FAILED |
| OrderItem | Line item: product_id, quantity, unit_price |
| Product | Catalog entry with price |
| Inventory | Tracks reserved and available stock per product |
| PaymentService | Charges the customer (stubbed) |
| ShippingService | Creates shipment, returns tracking number (stubbed) |
| OrderService | Orchestrates order lifecycle; holds all state transitions |
| OrderObserver | Abstract; notified on status changes (email, push) |

---

## Class Design

### OrderStatus

```
enum OrderStatus:
- PLACED
- CONFIRMED
- SHIPPED
- DELIVERED
- CANCELLED
- FAILED
```

### OrderItem

| Requirement | What OrderItem must track |
|-------------|---------------------------|
| Line detail | product_id, quantity, unit_price |

```
class OrderItem:
- product_id: str
- quantity: int
- unit_price: float
+ subtotal() -> float
```

### Order

| Requirement | What Order must track |
|-------------|------------------------|
| Identity | order_id, user_id, idempotency_key |
| Line items | items: List<OrderItem> |
| Lifecycle | status, timestamps per transition |
| Shipping | tracking_number (set on ship) |

```
class Order:
- order_id: str
- user_id: str
- idempotency_key: str
- items: List<OrderItem>
- status: OrderStatus
- created_at: datetime
- tracking_number: String (nullable)
+ total() -> float
+ can_cancel() -> bool
```

### Inventory

| Requirement | What Inventory must track |
|-------------|---------------------------|
| Stock | available: int, reserved: int per product |

```
class Inventory:
- stock: Map<String, Stock>   # product_id -> {available, reserved}
+ reserve(product_id, qty) -> bool
+ release(product_id, qty)
+ deduct(product_id, qty)
+ get_available(product_id) -> int
```

### OrderService

```
class OrderService:
- orders: Map<String, Order>
- idempotency_map: Map<String, Order>
- inventory: Inventory
- payment_service: PaymentService
- shipping_service: ShippingService
- observers: List<OrderObserver>
+ place_order(user_id, items, idempotency_key) -> Order
+ confirm_order(order_id) -> Order
+ ship_order(order_id, tracking) -> Order
+ deliver_order(order_id) -> Order
+ cancel_order(order_id) -> Order
+ get_order(order_id) -> Order
+ add_observer(observer: OrderObserver)
```

---

## Implementation

### Core Method: `place_order`

**Core logic:**
1. Check `idempotency_map`; if key exists, return the existing Order (idempotent response).
2. Validate all items have sufficient inventory via `inventory.reserve()`.
3. Create Order with status PLACED.
4. Store in `orders` and `idempotency_map`.
5. Notify observers.

**Edge cases:**
- Partial reservation failure: if product B fails after product A reserved, release A before raising.
- Empty items list — raise immediately.
- Negative quantity — validate before touching inventory.

```java
import java.time.Instant;
import java.util.*;

enum OrderStatus {
    PLACED, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, FAILED
}

class OrderItem {
    private final String productId;
    private final int quantity;
    private final double unitPrice;

    public OrderItem(String productId, int quantity, double unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }

    public double subtotal() { return quantity * unitPrice; }
}

class Order {
    private final String orderId;
    private final String userId;
    private final String idempotencyKey;
    private final List<OrderItem> items;
    private OrderStatus status = OrderStatus.PLACED;
    private final Instant createdAt = Instant.now();
    private String trackingNumber;

    public Order(String orderId, String userId, String idempotencyKey, List<OrderItem> items) {
        this.orderId = orderId;
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.items = items;
    }

    public String getOrderId() { return orderId; }
    public String getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public double total() {
        return items.stream().mapToDouble(OrderItem::subtotal).sum();
    }

    public boolean canCancel() {
        return status == OrderStatus.PLACED || status == OrderStatus.CONFIRMED;
    }
}

class Inventory {
    private static class Stock {
        int available;
        int reserved;
        Stock(int available, int reserved) { this.available = available; this.reserved = reserved; }
    }

    private final Map<String, Stock> stock = new HashMap<>();

    public void addProduct(String productId, int quantity) {
        stock.put(productId, new Stock(quantity, 0));
    }

    public boolean reserve(String productId, int qty) {
        Stock s = stock.get(productId);
        if (s == null || s.available < qty) {
            return false;
        }
        s.available -= qty;
        s.reserved += qty;
        return true;
    }

    public void release(String productId, int qty) {
        Stock s = stock.get(productId);
        s.reserved -= qty;
        s.available += qty;
    }

    public void deduct(String productId, int qty) {
        Stock s = stock.get(productId);
        s.reserved -= qty;  // already moved out of available
    }

    public int getAvailable(String productId) {
        Stock s = stock.get(productId);
        return s == null ? 0 : s.available;
    }
}

class PaymentService {
    public boolean charge(String userId, double amount) {
        System.out.printf("Charging %s $%.2f%n", userId, amount);
        return true;  // stub
    }
}

class ShippingService {
    public String createShipment(String orderId) {
        return "TRACK-" + orderId.substring(0, 8).toUpperCase();
    }
}

interface OrderObserver {
    void onStatusChange(Order order);
}

class EmailObserver implements OrderObserver {
    @Override
    public void onStatusChange(Order order) {
        System.out.printf("Email: order %s is now %s%n", order.getOrderId(), order.getStatus());
    }
}

class OrderService {
    private final Map<String, Order> orders = new HashMap<>();
    private final Map<String, Order> idempotencyMap = new HashMap<>();
    private final Inventory inventory = new Inventory();
    private final PaymentService paymentService = new PaymentService();
    private final ShippingService shippingService = new ShippingService();
    private final List<OrderObserver> observers = new ArrayList<>();

    public Map<String, Order> getOrders() {
        return orders;
    }

    public void addObserver(OrderObserver obs) {
        observers.add(obs);
    }

    private void notifyObservers(Order order) {
        for (OrderObserver obs : observers) {
            obs.onStatusChange(order);
        }
    }

    public Order placeOrder(String userId, List<OrderItem> items, String idempotencyKey) {
        if (idempotencyMap.containsKey(idempotencyKey)) {
            return idempotencyMap.get(idempotencyKey);
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }

        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : items) {
                if (!inventory.reserve(item.getProductId(), item.getQuantity())) {
                    throw new IllegalStateException("Insufficient stock for " + item.getProductId());
                }
                reserved.add(item);
            }
        } catch (IllegalStateException e) {
            for (OrderItem item : reserved) {
                inventory.release(item.getProductId(), item.getQuantity());
            }
            throw e;
        }

        Order order = new Order(UUID.randomUUID().toString(), userId, idempotencyKey, items);
        orders.put(order.getOrderId(), order);
        idempotencyMap.put(idempotencyKey, order);
        notifyObservers(order);
        return order;
    }

    public Order confirmOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new IllegalStateException("Can only confirm a PLACED order");
        }

        boolean charged = paymentService.charge(order.getUserId(), order.total());
        if (!charged) {
            for (OrderItem item : order.getItems()) {
                inventory.release(item.getProductId(), item.getQuantity());
            }
            order.setStatus(OrderStatus.FAILED);
            notifyObservers(order);
            throw new IllegalStateException("Payment failed");
        }

        for (OrderItem item : order.getItems()) {
            inventory.deduct(item.getProductId(), item.getQuantity());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        notifyObservers(order);
        return order;
    }

    public Order shipOrder(String orderId, String tracking) {
        Order order = orders.get(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Can only ship a CONFIRMED order");
        }
        order.setTrackingNumber(tracking != null ? tracking : shippingService.createShipment(orderId));
        order.setStatus(OrderStatus.SHIPPED);
        notifyObservers(order);
        return order;
    }

    public Order deliverOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order.getStatus() != OrderStatus.SHIPPED) {
            throw new IllegalStateException("Can only deliver a SHIPPED order");
        }
        order.setStatus(OrderStatus.DELIVERED);
        notifyObservers(order);
        return order;
    }

    public Order cancelOrder(String orderId) {
        Order order = orders.get(orderId);
        if (!order.canCancel()) {
            throw new IllegalStateException("Cannot cancel order in status: " + order.getStatus());
        }
        for (OrderItem item : order.getItems()) {
            inventory.release(item.getProductId(), item.getQuantity());
        }
        order.setStatus(OrderStatus.CANCELLED);
        notifyObservers(order);
        return order;
    }
}
```

---

## Verification

Scenario: User places an order for 2 units of Product A (stock=5), then cancels.

1. `place_order(user_id, [item(A,2)], key="k1")` → reserves 2 units (available drops to 3), status=PLACED.
2. Retry with same key `"k1"` → returns same Order (idempotent).
3. `cancel_order(order_id)` → releases 2 units (available back to 5), status=CANCELLED, observers notified.
4. `cancel_order(order_id)` again → `can_cancel()` returns False, raises ValueError.

---

## Deep Dive & Extensibility

### 1. "When should inventory be reserved vs deducted?"

Reserve at order placement so the customer sees accurate availability. Deduct only at payment confirmation to avoid holding stock for unpaid orders indefinitely. Release on payment failure, cancellation, or timeout.

```java
import java.time.Duration;

// Timeout-based release: a background job
void releaseExpiredPlacements(OrderService service, int ttlMinutes) {
    Instant cutoff = Instant.now().minus(Duration.ofMinutes(ttlMinutes));
    for (Order order : new ArrayList<>(service.getOrders().values())) {
        if (order.getStatus() == OrderStatus.PLACED && order.getCreatedAt().isBefore(cutoff)) {
            service.cancelOrder(order.getOrderId());
        }
    }
}
```

### 2. "What if payment fails after inventory is reserved?"

The flow is: reserve → charge → deduct. If charge fails, release reservation and set status=FAILED. This is the compensating transaction in a Saga. The Saga is local here (single service), but in a microservices context each step publishes an event and the next service responds.

### 3. "Explain the Saga pattern for order flow"

Saga breaks a distributed transaction into a sequence of local transactions, each with a compensating action if later steps fail.

```
Step 1: reserve_inventory   | compensate: release_inventory
Step 2: charge_payment      | compensate: refund_payment
Step 3: create_shipment     | compensate: cancel_shipment
```

If Step 3 fails, the Saga runs compensations 2 and 1 in reverse. Unlike 2PC, no distributed lock is held — eventual consistency.

### 4. "How do you implement idempotency for order placement?"

Client generates a UUID `idempotency_key` and sends it with every attempt. The server stores `idempotency_key → Order` in a map. On duplicate request, return the stored Order unchanged. Key must be stored durably (DB) for crash safety in production.

```java
if (idempotencyMap.containsKey(idempotencyKey)) {
    return idempotencyMap.get(idempotencyKey);
}
```

### 5. "How would you model returns and refunds?"

Add a `Return` entity linked to an Order. A return can only be initiated for DELIVERED orders within a window (e.g., 30 days). The return flow mirrors the order saga in reverse: create_return → approve_return → refund_payment → restock_inventory.

```java
enum ReturnStatus {
    REQUESTED, APPROVED, REFUNDED, REJECTED
}

class Return {
    private final String returnId;
    private final Order order;
    private final List<OrderItem> items;   // subset of original items
    private final String reason;
    private ReturnStatus status = ReturnStatus.REQUESTED;

    public Return(String returnId, Order order, List<OrderItem> items, String reason) {
        this.returnId = returnId;
        this.order = order;
        this.items = items;
        this.reason = reason;
    }

    public String getReturnId() { return returnId; }
    public Order getOrder() { return order; }
    public List<OrderItem> getItems() { return items; }
    public String getReason() { return reason; }
    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus status) { this.status = status; }
}
```

---

## Interviewer Questions by Level

**Junior**: What is the purpose of OrderStatus and what transitions are valid?  
**Mid-level**: Why reserve inventory at placement rather than at confirmation?  
**Senior**: Explain how the Saga pattern handles payment failure in a microservices order flow.

---

## Common Interview Questions

- **Q: When should inventory be deducted?** A: Reserve at placement (blocks others from buying the same stock); deduct only at payment confirmation so unconfirmed orders don't permanently remove inventory.
- **Q: What if payment fails after inventory is reserved?** A: Run the compensating transaction: release the reservation, mark order FAILED, notify the user.
- **Q: Saga vs 2PC for order flow?** A: Saga uses async compensating transactions — higher availability, no distributed lock; 2PC requires a coordinator and holds locks across services, which hurts availability. Saga is preferred for microservices.
- **Q: How does the idempotency key prevent duplicate orders?** A: Client generates a unique key per logical request. Server checks the key before processing; if found, return the prior result. Safe to retry on network timeout.
- **Q: Can a partially shipped order be cancelled?** A: No — once status is SHIPPED or later, `can_cancel()` returns False.
- **Q: How would you handle order timeout (user places but never pays)?** A: Background job scans PLACED orders older than TTL and runs `cancel_order` to release inventory.
- **Q: How do observers get notified?** A: `OrderService._notify(order)` iterates all registered `OrderObserver` instances and calls `on_status_change(order)` after every valid state transition.

---

## Related

**Patterns applied here**

- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Inventory Management](24-design-inventory-management.md)
- [Design Food Delivery](../02-frequent-problems/14-design-food-delivery.md)

Order state and stock levels move together.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
