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
| Line items | items: list[OrderItem] |
| Lifecycle | status, timestamps per transition |
| Shipping | tracking_number (set on ship) |

```
class Order:
- order_id: str
- user_id: str
- idempotency_key: str
- items: list[OrderItem]
- status: OrderStatus
- created_at: datetime
- tracking_number: Optional[str]
+ total() -> float
+ can_cancel() -> bool
```

### Inventory

| Requirement | What Inventory must track |
|-------------|---------------------------|
| Stock | available: int, reserved: int per product |

```
class Inventory:
- stock: dict[str, dict]   # product_id -> {available, reserved}
+ reserve(product_id, qty) -> bool
+ release(product_id, qty)
+ deduct(product_id, qty)
+ get_available(product_id) -> int
```

### OrderService

```
class OrderService:
- orders: dict[str, Order]
- idempotency_map: dict[str, Order]
- inventory: Inventory
- payment_service: PaymentService
- shipping_service: ShippingService
- observers: list[OrderObserver]
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

```python
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum, auto
from typing import Optional
import uuid


class OrderStatus(Enum):
    PLACED    = auto()
    CONFIRMED = auto()
    SHIPPED   = auto()
    DELIVERED = auto()
    CANCELLED = auto()
    FAILED    = auto()


@dataclass
class OrderItem:
    product_id: str
    quantity: int
    unit_price: float

    def subtotal(self) -> float:
        return self.quantity * self.unit_price


@dataclass
class Order:
    order_id: str
    user_id: str
    idempotency_key: str
    items: list[OrderItem]
    status: OrderStatus = OrderStatus.PLACED
    created_at: datetime = field(default_factory=datetime.utcnow)
    tracking_number: Optional[str] = None

    def total(self) -> float:
        return sum(item.subtotal() for item in self.items)

    def can_cancel(self) -> bool:
        return self.status in (OrderStatus.PLACED, OrderStatus.CONFIRMED)


class Inventory:
    def __init__(self):
        self._stock: dict[str, dict] = {}  # {available: int, reserved: int}

    def add_product(self, product_id: str, quantity: int):
        self._stock[product_id] = {"available": quantity, "reserved": 0}

    def reserve(self, product_id: str, qty: int) -> bool:
        s = self._stock.get(product_id)
        if not s or s["available"] < qty:
            return False
        s["available"] -= qty
        s["reserved"] += qty
        return True

    def release(self, product_id: str, qty: int):
        s = self._stock[product_id]
        s["reserved"] -= qty
        s["available"] += qty

    def deduct(self, product_id: str, qty: int):
        s = self._stock[product_id]
        s["reserved"] -= qty  # already moved out of available

    def get_available(self, product_id: str) -> int:
        return self._stock.get(product_id, {}).get("available", 0)


class PaymentService:
    def charge(self, user_id: str, amount: float) -> bool:
        print(f"Charging {user_id} ${amount:.2f}")
        return True  # stub


class ShippingService:
    def create_shipment(self, order_id: str) -> str:
        return f"TRACK-{order_id[:8].upper()}"


class OrderObserver:
    def on_status_change(self, order: Order):
        pass


class EmailObserver(OrderObserver):
    def on_status_change(self, order: Order):
        print(f"Email: order {order.order_id} is now {order.status.name}")


class OrderService:
    def __init__(self):
        self.orders: dict[str, Order] = {}
        self.idempotency_map: dict[str, Order] = {}
        self.inventory = Inventory()
        self.payment_service = PaymentService()
        self.shipping_service = ShippingService()
        self.observers: list[OrderObserver] = []

    def add_observer(self, obs: OrderObserver):
        self.observers.append(obs)

    def _notify(self, order: Order):
        for obs in self.observers:
            obs.on_status_change(order)

    def place_order(self, user_id: str, items: list[OrderItem],
                    idempotency_key: str) -> Order:
        if idempotency_key in self.idempotency_map:
            return self.idempotency_map[idempotency_key]

        if not items:
            raise ValueError("Order must have at least one item")

        reserved = []
        try:
            for item in items:
                if not self.inventory.reserve(item.product_id, item.quantity):
                    raise ValueError(f"Insufficient stock for {item.product_id}")
                reserved.append(item)
        except ValueError:
            for item in reserved:
                self.inventory.release(item.product_id, item.quantity)
            raise

        order = Order(
            order_id=str(uuid.uuid4()),
            user_id=user_id,
            idempotency_key=idempotency_key,
            items=items,
        )
        self.orders[order.order_id] = order
        self.idempotency_map[idempotency_key] = order
        self._notify(order)
        return order

    def confirm_order(self, order_id: str) -> Order:
        order = self.orders[order_id]
        if order.status != OrderStatus.PLACED:
            raise ValueError("Can only confirm a PLACED order")

        charged = self.payment_service.charge(order.user_id, order.total())
        if not charged:
            for item in order.items:
                self.inventory.release(item.product_id, item.quantity)
            order.status = OrderStatus.FAILED
            self._notify(order)
            raise ValueError("Payment failed")

        for item in order.items:
            self.inventory.deduct(item.product_id, item.quantity)

        order.status = OrderStatus.CONFIRMED
        self._notify(order)
        return order

    def ship_order(self, order_id: str, tracking: Optional[str] = None) -> Order:
        order = self.orders[order_id]
        if order.status != OrderStatus.CONFIRMED:
            raise ValueError("Can only ship a CONFIRMED order")
        order.tracking_number = tracking or self.shipping_service.create_shipment(order_id)
        order.status = OrderStatus.SHIPPED
        self._notify(order)
        return order

    def deliver_order(self, order_id: str) -> Order:
        order = self.orders[order_id]
        if order.status != OrderStatus.SHIPPED:
            raise ValueError("Can only deliver a SHIPPED order")
        order.status = OrderStatus.DELIVERED
        self._notify(order)
        return order

    def cancel_order(self, order_id: str) -> Order:
        order = self.orders[order_id]
        if not order.can_cancel():
            raise ValueError("Cannot cancel order in status: " + order.status.name)
        for item in order.items:
            self.inventory.release(item.product_id, item.quantity)
        order.status = OrderStatus.CANCELLED
        self._notify(order)
        return order
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

```python
# Timeout-based release: a background job
def release_expired_placements(service: OrderService, ttl_minutes: int = 15):
    cutoff = datetime.utcnow() - timedelta(minutes=ttl_minutes)
    for order in list(service.orders.values()):
        if order.status == OrderStatus.PLACED and order.created_at < cutoff:
            service.cancel_order(order.order_id)
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

```python
if idempotency_key in self.idempotency_map:
    return self.idempotency_map[idempotency_key]
```

### 5. "How would you model returns and refunds?"

Add a `Return` entity linked to an Order. A return can only be initiated for DELIVERED orders within a window (e.g., 30 days). The return flow mirrors the order saga in reverse: create_return → approve_return → refund_payment → restock_inventory.

```python
class ReturnStatus(Enum):
    REQUESTED = auto()
    APPROVED  = auto()
    REFUNDED  = auto()
    REJECTED  = auto()

@dataclass
class Return:
    return_id: str
    order: Order
    items: list[OrderItem]   # subset of original items
    reason: str
    status: ReturnStatus = ReturnStatus.REQUESTED
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

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
