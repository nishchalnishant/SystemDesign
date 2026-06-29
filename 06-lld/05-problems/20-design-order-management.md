---
module: 06-lld
topic: Problems
status: interview-ready
tags: [06-lld, system-design, problems]
---
# Design Order Management System

> **Difficulty**: Medium
> **Topics**: State Pattern, Observer Pattern, Strategy Pattern, Saga
> **Extension**: Return/refund flow, partial fulfilment, saga rollback on payment failure

---

## What Breaks Without This Design?

```python
class Order:
    def __init__(self, order_id, items):
        self.order_id = order_id
        self.items = items
        self.status = "CART"  # string flag

    def place(self):
        if self.status != "CART":
            raise Exception("Can only place from CART")
        self.status = "PLACED"
        self._charge_payment()
        self._reserve_inventory()
        self._send_confirmation_email()   # all in one method

    def ship(self):
        if self.status not in ("PLACED", "CONFIRMED"):
            raise Exception("Can only ship from PLACED/CONFIRMED")
        self.status = "SHIPPED"
        self._send_shipping_email()
```

**Concrete failures**:
1. **String status is not type-safe**: Typo `"PLACEED"` compiles; equality check fails silently.
2. **Invalid transitions not structurally prevented**: Nothing stops calling `ship()` on a `CANCELLED` order except a runtime string check — every new developer must remember to add it.
3. **God method**: `place()` does payment, inventory reservation, and email in one method. A payment failure leaves inventory already reserved and the email already sent — no rollback.
4. **Adding a new state (`PARTIALLY_SHIPPED`) requires editing every method** that references the current status string.
5. **Notifications tightly coupled**: `_send_confirmation_email()` is hardcoded — you cannot swap in SMS or push without editing `Order`.

---

## Derive the Class Structure

**Force 1 — State-dependent behaviour must be grouped**: `place()`, `confirm()`, `ship()`, `deliver()`, `cancel()`, `return_order()` all behave differently per state. Extract `OrderState` interface. Each concrete state implements only valid transitions; others throw `InvalidTransitionException`.

**Force 2 — Notification must be decoupled**: Multiple observers (EmailService, SMSService, InventoryService, AnalyticsService) react to order events. Extract `OrderObserver` interface; `Order` notifies all registered observers on state change.

**Force 3 — Pricing/discount is swappable**: Prime discount, coupon, bulk pricing — extract `PricingStrategy` interface.

**Force 4 — Payment + inventory + notification is a distributed transaction (Saga)**: Each step must be independently reversible. If payment succeeds but inventory reservation fails, payment must be refunded. Store a rollback action per step.

```
God class → Order (items, currentState: OrderState, observers: list[OrderObserver])
          → OrderState (interface: place, confirm, ship, deliver, cancel, return_order)
             → CartState, PlacedState, ConfirmedState, ShippedState,
               DeliveredState, CancelledState, ReturnRequestedState
          → OrderObserver (interface: on_order_event(event: OrderEvent))
             → EmailNotifier, SMSNotifier, InventoryReserver, AnalyticsTracker
          → PricingStrategy (interface: calculate_total(items) → Decimal)
             → StandardPricing, PrimePricing, CouponPricing
          → OrderItem (product_id, name, quantity, unit_price)
          → OrderEvent (order_id, from_state, to_state, timestamp)
```

---

## Opening Analogy

Think of an Amazon order. You add items to cart — nothing is charged. You place the order — payment is authorized. Amazon confirms — inventory is reserved. It ships — you get a tracking number. It is delivered — the authorization is captured (charged). You request a return — refund is initiated. At every step, the order is in exactly one state and only certain actions are valid. Pressing "cancel" after delivery goes to "return request" — not back to "placed". That is a state machine. The email and SMS you receive at each step are observers reacting to the transition — the Order itself does not know about email.

---

## Phase 1: Requirements

### Functional
- Customer adds items to cart, places order, tracks status.
- System validates payment, reserves inventory, triggers fulfilment.
- Order transitions: `CART → PLACED → CONFIRMED → SHIPPED → DELIVERED`.
- Cancellation allowed from `PLACED` or `CONFIRMED` (not after shipping).
- Return/refund flow: `DELIVERED → RETURN_REQUESTED → RETURN_APPROVED → REFUNDED`.
- All state change observers notified synchronously (or via event queue for async).

### Non-Functional
- Invalid state transitions must throw, not silently no-op.
- Currency must use `Decimal` — no `float` for money.
- Observers must not block the order state transition (fire-and-forget or async queue).
- Adding a new observer (e.g., `LoyaltyPointsService`) must not touch `Order`.

---

## Phase 2: Use Cases

### Actors
- **Customer** — places, tracks, cancels, initiates return.
- **Warehouse** — confirms, ships.
- **Payment Gateway** — authorizes and captures.
- **System** — drives state machine, notifies observers.

### UC1: Place Order (Happy Path)
1. Customer calls `order.place()` from `CartState`.
2. System validates items are in stock and price is correct.
3. System authorizes payment (hold on card).
4. State transitions: `CART → PLACED`.
5. Observers notified: InventoryReserver reserves stock; EmailNotifier sends confirmation.

### UC2: Ship Order
1. Warehouse calls `order.ship()` from `CONFIRMED`.
2. State: `CONFIRMED → SHIPPED`.
3. Observers notified: EmailNotifier sends tracking number; SMSNotifier sends SMS.

### UC3: Cancel Order
1. Customer calls `order.cancel()` from `PLACED` or `CONFIRMED`.
2. State: `PLACED/CONFIRMED → CANCELLED`.
3. Rollback: payment authorization released; inventory reservation released.
4. If attempted from `SHIPPED` or later: `InvalidTransitionException`.

### UC4: Return Order
1. Customer calls `order.request_return()` from `DELIVERED`.
2. State: `DELIVERED → RETURN_REQUESTED`.
3. Warehouse approves: `RETURN_REQUESTED → RETURN_APPROVED`.
4. Refund issued: `RETURN_APPROVED → REFUNDED`.

---

## Phase 3: Class Diagram

```
┌─────────────────────────────────────────┐
│                  Order                  │  <<Context>>
│─────────────────────────────────────────│
│ - order_id: str                         │
│ - items: list[OrderItem]                │
│ - current_state: OrderState             │
│ - observers: list[OrderObserver]        │
│ - total_price: Decimal                  │
│─────────────────────────────────────────│
│ + place() → void                        │
│ + confirm() → void                      │
│ + ship() → void                         │
│ + deliver() → void                      │
│ + cancel() → void                       │
│ + request_return() → void               │
│ + add_observer(o: OrderObserver) → void │
│ + set_state(s: OrderState) → void       │
└────────────────────┬────────────────────┘
                     │ delegates to
           ┌─────────▼──────────┐
           │    OrderState      │  <<interface>>
           │────────────────────│
           │ + place(order)     │
           │ + confirm(order)   │
           │ + ship(order)      │
           │ + deliver(order)   │
           │ + cancel(order)    │
           │ + request_return() │
           └─────────┬──────────┘
      ┌──────────────┼──────────────────────────┐
      ▼              ▼                          ▼
  CartState    PlacedState            ShippedState
               ConfirmedState         DeliveredState
               CancelledState         ReturnRequestedState

┌──────────────────────────────┐
│        OrderObserver         │  <<interface>>
│──────────────────────────────│
│ + on_order_event(e: Event)   │
└──────────────────────────────┘
     ▲             ▲
EmailNotifier   InventoryReserver
```

**State transition diagram:**
```
CART ──place()──► PLACED ──confirm()──► CONFIRMED ──ship()──► SHIPPED ──deliver()──► DELIVERED
                    │           │                                                         │
                 cancel()    cancel()                                              request_return()
                    ▼           ▼                                                         ▼
                CANCELLED   CANCELLED                                          RETURN_REQUESTED
                                                                                         │
                                                                                   approve_return()
                                                                                         ▼
                                                                                 RETURN_APPROVED
                                                                                         │
                                                                                   issue_refund()
                                                                                         ▼
                                                                                      REFUNDED
```

---

## Phase 4: Design Patterns Applied

### 1. State Pattern — Core architecture
**Why:** Without it, every method on `Order` is a cascade of `if status == "CART"` branches. 7 states × 6 actions = 42 branches in one class. State pattern moves each state's behaviour into its own class. Adding `PARTIALLY_SHIPPED` state = new file, zero changes to existing states.

**How:** `Order` is the Context. It holds `current_state: OrderState` and delegates every action to it. Each concrete state handles valid transitions and raises `InvalidTransitionException` for invalid ones.

### 2. Observer Pattern — Notifications and side effects
**Why:** EmailNotifier, SMSNotifier, InventoryReserver, and AnalyticsTracker all need to react to state changes, but `Order` must not know about any of them (violates SRP and OCP). Observer decouples: `Order` calls `notify_observers(event)` after each transition; each observer reacts independently.

**How:** `Order.add_observer(o)` registers. On each transition, `Order._notify(event)` iterates observers and calls `on_order_event(event)`.

### 3. Strategy Pattern — Pricing
**Why:** Standard, Prime, coupon, and bulk pricing produce different totals from the same item list. Strategy keeps pricing logic out of `Order`.

**How:** `Order.__init__` takes a `PricingStrategy`. `total_price = strategy.calculate_total(items)` called at placement time.

---

## Phase 5: Key Python Implementation

```python
from abc import ABC, abstractmethod
from decimal import Decimal
from datetime import datetime
from enum import Enum, auto

# ── Domain types ──────────────────────────────────────────────────────────────

class OrderStatus(Enum):
    CART              = auto()
    PLACED            = auto()
    CONFIRMED         = auto()
    SHIPPED           = auto()
    DELIVERED         = auto()
    CANCELLED         = auto()
    RETURN_REQUESTED  = auto()
    RETURN_APPROVED   = auto()
    REFUNDED          = auto()

class OrderItem:
    def __init__(self, product_id, name, quantity, unit_price):
        self.product_id = product_id
        self.name = name
        self.quantity = quantity
        self.unit_price = unit_price

class OrderEvent:
    def __init__(self, order_id, from_status, to_status):
        self.order_id = order_id
        self.from_status = from_status
        self.to_status = to_status
        self.timestamp = datetime.now()

# ── Observer interface ────────────────────────────────────────────────────────

class OrderObserver(ABC):
    @abstractmethod
    def on_order_event(self, event): ...

class EmailNotifier(OrderObserver):
    def on_order_event(self, event):
        print(f"[EMAIL] Order {event.order_id}: {event.from_status.name} → {event.to_status.name}")

class InventoryReserver(OrderObserver):
    def on_order_event(self, event):
        if event.to_status == OrderStatus.PLACED:
            print(f"[INVENTORY] Reserving stock for order {event.order_id}")
        elif event.to_status == OrderStatus.CANCELLED:
            print(f"[INVENTORY] Releasing reservation for order {event.order_id}")

# ── Pricing Strategy ──────────────────────────────────────────────────────────

class PricingStrategy(ABC):
    @abstractmethod
    def calculate_total(self, items): ...

class StandardPricing(PricingStrategy):
    def calculate_total(self, items):
        return sum(item.unit_price * item.quantity for item in items)

class PrimePricing(PricingStrategy):
    DISCOUNT = Decimal("0.10")
    def calculate_total(self, items):
        subtotal = sum(item.unit_price * item.quantity for item in items)
        return subtotal * (1 - self.DISCOUNT)

# ── State interface ───────────────────────────────────────────────────────────

class InvalidTransitionException(Exception): ...

class OrderState(ABC):
    @abstractmethod
    def place(self, order): ...
    @abstractmethod
    def confirm(self, order): ...
    @abstractmethod
    def ship(self, order): ...
    @abstractmethod
    def deliver(self, order): ...
    @abstractmethod
    def cancel(self, order): ...
    @abstractmethod
    def request_return(self, order): ...

    def _reject(self, action, state):
        raise InvalidTransitionException(f"Cannot '{action}' from state '{state}'")

# ── Context ───────────────────────────────────────────────────────────────────

class Order:
    def __init__(self, order_id, items, pricing=None):
        self.order_id = order_id
        self.total = (pricing or StandardPricing()).calculate_total(items)
        self.status = OrderStatus.CART
        self._items = items
        self._observers = []
        self._current_state = CartState()

    def add_observer(self, observer):
        self._observers.append(observer)

    def set_state(self, state, new_status):
        event = OrderEvent(self.order_id, self.status, new_status)
        self.status = new_status
        self._current_state = state
        self._notify(event)

    def _notify(self, event):
        for obs in self._observers:
            obs.on_order_event(event)

    # Delegate all actions to current state
    def place(self):          self._current_state.place(self)
    def confirm(self):        self._current_state.confirm(self)
    def ship(self):           self._current_state.ship(self)
    def deliver(self):        self._current_state.deliver(self)
    def cancel(self):         self._current_state.cancel(self)
    def request_return(self): self._current_state.request_return(self)

# ── Concrete States ───────────────────────────────────────────────────────────

class CartState(OrderState):
    def place(self, order):
        order.set_state(PlacedState(), OrderStatus.PLACED)
        print(f"Order {order.order_id} placed. Total: {order.total}")

    def confirm(self, o):  self._reject("confirm", "CART")
    def ship(self, o):     self._reject("ship", "CART")
    def deliver(self, o):  self._reject("deliver", "CART")
    def cancel(self, o):   self._reject("cancel", "CART")
    def request_return(self, o): self._reject("return", "CART")

class PlacedState(OrderState):
    def place(self, o):    self._reject("place", "PLACED")
    def confirm(self, order):
        order.set_state(ConfirmedState(), OrderStatus.CONFIRMED)
    def ship(self, o):     self._reject("ship", "PLACED")
    def deliver(self, o):  self._reject("deliver", "PLACED")
    def cancel(self, order):
        order.set_state(CancelledState(), OrderStatus.CANCELLED)
        print(f"Order {order.order_id} cancelled. Refunding payment authorization.")
    def request_return(self, o): self._reject("return", "PLACED")

class ConfirmedState(OrderState):
    def place(self, o):    self._reject("place", "CONFIRMED")
    def confirm(self, o):  self._reject("confirm", "CONFIRMED")
    def ship(self, order):
        order.set_state(ShippedState(), OrderStatus.SHIPPED)
    def deliver(self, o):  self._reject("deliver", "CONFIRMED")
    def cancel(self, order):
        order.set_state(CancelledState(), OrderStatus.CANCELLED)
    def request_return(self, o): self._reject("return", "CONFIRMED")

class ShippedState(OrderState):
    def place(self, o):    self._reject("place", "SHIPPED")
    def confirm(self, o):  self._reject("confirm", "SHIPPED")
    def ship(self, o):     self._reject("ship", "SHIPPED")
    def deliver(self, order):
        order.set_state(DeliveredState(), OrderStatus.DELIVERED)
    def cancel(self, o):   self._reject("cancel", "SHIPPED")
    def request_return(self, o): self._reject("return", "SHIPPED")

class DeliveredState(OrderState):
    def place(self, o):    self._reject("place", "DELIVERED")
    def confirm(self, o):  self._reject("confirm", "DELIVERED")
    def ship(self, o):     self._reject("ship", "DELIVERED")
    def deliver(self, o):  self._reject("deliver", "DELIVERED")
    def cancel(self, o):   self._reject("cancel", "DELIVERED")
    def request_return(self, order):
        order.set_state(ReturnRequestedState(), OrderStatus.RETURN_REQUESTED)

class CancelledState(OrderState):
    def place(self, o):    self._reject("place", "CANCELLED")
    def confirm(self, o):  self._reject("confirm", "CANCELLED")
    def ship(self, o):     self._reject("ship", "CANCELLED")
    def deliver(self, o):  self._reject("deliver", "CANCELLED")
    def cancel(self, o):   self._reject("cancel", "CANCELLED")
    def request_return(self, o): self._reject("return", "CANCELLED")

class ReturnRequestedState(OrderState):
    def place(self, o):    self._reject("place", "RETURN_REQUESTED")
    def confirm(self, o):  self._reject("confirm", "RETURN_REQUESTED")
    def ship(self, o):     self._reject("ship", "RETURN_REQUESTED")
    def deliver(self, o):  self._reject("deliver", "RETURN_REQUESTED")
    def cancel(self, o):   self._reject("cancel", "RETURN_REQUESTED")
    def request_return(self, o): self._reject("return", "RETURN_REQUESTED")

# ── Demo ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    items = [
        OrderItem("P1", "Kindle", 1, Decimal("7999")),
        OrderItem("P2", "Case",   1, Decimal("499")),
    ]
    order = Order("ORD-001", items, PrimePricing())
    order.add_observer(EmailNotifier())
    order.add_observer(InventoryReserver())

    order.place()      # CART → PLACED, notifies email + inventory
    order.confirm()    # PLACED → CONFIRMED
    order.ship()       # CONFIRMED → SHIPPED
    order.deliver()    # SHIPPED → DELIVERED
    order.request_return()  # DELIVERED → RETURN_REQUESTED
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| State per class | Separate class per state | `VALID_TRANSITIONS` dict | Per-class puts behaviour and transition logic together; dict is lighter but couples all transitions in one place |
| Observer sync | Synchronous notify loop | Async event queue (Kafka) | Sync is simpler; async needed if observers are slow or remote |
| Pricing at order creation | Calculate total at `place()` | Recalculate on every read | Price must be locked in at placement; later price changes must not affect placed orders |
| Cancellation window | Allowed until SHIPPED | Allowed until CONFIRMED only | After SHIPPED, warehouse has already packed — cancellation cost is high |

### Extensions

**Saga rollback on payment failure:**
```python
class PlacedState(OrderState):
    def place(self, order: Order) -> None:
        try:
            payment_service.authorize(order.order_id, order.total)
        except PaymentException:
            # Do NOT transition — stay in CART, return error
            raise
        try:
            inventory_service.reserve(order.items)
        except InsufficientStockException:
            payment_service.release_auth(order.order_id)  # compensate
            raise
        order.set_state(PlacedState(), OrderStatus.PLACED)
```

**Partial fulfilment:**
Split one `Order` into multiple `Shipment` objects. `Order` transitions to `PARTIALLY_SHIPPED` when some items ship, `SHIPPED` when all ship.

---

## Interviewer Follow-Up Questions

- "Walk me through the state machine for an Amazon order." → States: `CART` (no payment), `PLACED` (payment authorized, inventory reserved), `CONFIRMED` (warehouse accepted), `SHIPPED` (in transit), `DELIVERED` (received). Cancel allowed from `PLACED`/`CONFIRMED` — releases auth and inventory. After shipping: no cancel, only return. Return flow: `DELIVERED → RETURN_REQUESTED → RETURN_APPROVED → REFUNDED`. State pattern enforces this — each state class rejects invalid transitions with an exception, not a silent return.
- "What happens if payment succeeds but inventory reservation fails?" → Saga compensating transaction: step 1 = authorize payment, step 2 = reserve inventory. If step 2 fails: compensate step 1 by calling `release_auth()`. The order stays in `CART` state — nothing persisted. This is the Saga pattern: each step has a compensating action. At SDE-2: mention the problem and the fix. At SDE-3: describe the idempotency of each step and the retry strategy.
- "How do you add a loyalty points observer without touching Order?" → Implement `LoyaltyPointsObserver(OrderObserver)` with `on_order_event()` that awards points on `DELIVERED`. Register it: `order.add_observer(LoyaltyPointsObserver())`. Zero changes to `Order`, `OrderState`, or any existing observer. This is OCP: open for extension (new observer), closed for modification.
- "Why use Decimal and not float for order totals?" → `float` uses IEEE 754 binary floating point — `0.1 + 0.2 == 0.30000000000000004` in Python. For financial amounts: `Decimal("7999") + Decimal("499") == Decimal("8498")` — exact. Always use `Decimal` for money. Set a fixed scale (`Decimal.quantize(Decimal("0.01"))`) to enforce two decimal places and avoid rounding surprises.
- "How do you enforce that two services don't simultaneously transition the same order?" → Optimistic locking: add a `version: int` field to the `Order` DB record. On update: `UPDATE orders SET status=X, version=version+1 WHERE order_id=Y AND version=N`. If `rows_affected=0`, another process already updated it — retry or reject. This is correct and avoids distributed locks. For SDE-2: mention the problem ("two warehouse workers confirming the same order"). For the fix: optimistic locking via version column.
