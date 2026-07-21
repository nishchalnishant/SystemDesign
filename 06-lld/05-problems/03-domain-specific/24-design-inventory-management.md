> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an Inventory Management System — tests handling of concurrent stock updates and alerting mechanisms when stock runs low.
>
> **Key concepts:**
> - Core Entities: `Warehouse`, `Product`, `InventoryItem`, `StockAlert`.
> - Concurrency (The core challenge): Two users buying the last item. 
>   - Use a `ConcurrentHashMap` for `product_id -> quantity`.
>   - Use `synchronized` methods or `AtomicInteger.compareAndSet` for decrementing stock.
> - Reservation Strategy: When a user adds to cart, "reserve" the item (decrement available, increment reserved). If checkout fails/times out, revert it.
> - Observer Pattern: When stock drops below a threshold, trigger a `LowStockEvent` to notify suppliers.
>
> **Key takeaway:** The interviewer will hammer you on the exact moment the stock count is updated. Differentiate between "Available Quantity" (can be added to cart), "Reserved Quantity" (in carts), and "Purchased Quantity".

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, inventory-management, observer, strategy, concurrency]
---
# Design Inventory Management System

> **Difficulty**: Medium
> **Asked at**: Amazon, Walmart, Shopify
> **Key Patterns**: Observer (stock alerts), Strategy (reservation), Optimistic locking (concurrent updates)

---

## Understanding the Problem

Design an inventory management system that tracks stock levels for products across multiple warehouses, handles reservations (soft holds during checkout), and fires low-stock alerts.

---

## Clarifying Questions

**You**: "Do we track inventory per warehouse or globally?"
**Interviewer**: "Per warehouse."

**You**: "What's the difference between reserve and deduct?"
**Interviewer**: "Reserve = soft hold during checkout. Deduct = permanent reduction when order is confirmed."

**You**: "How do we handle concurrent reservations for the last item?"
**Interviewer**: "Only one should succeed — race conditions must be prevented."

**You**: "When should low-stock alerts fire?"
**Interviewer**: "When quantity drops below a configured threshold per product."

**You**: "Do we need to track stock movement history?"
**Interviewer**: "Yes — reason and quantity for each change."

---

## Final Requirements

**In scope:**
1. Track stock per product per warehouse
2. Reserve stock (soft hold, time-bounded); release if not confirmed
3. Deduct stock permanently on order confirmation
4. Restock (add inventory)
5. Low-stock alerts via Observer when quantity falls below threshold
6. Stock movement history

**Out of scope:**
- Multi-warehouse routing for orders (follow-up)
- Supplier integration / auto-reorder
- Expiry date tracking (for perishables)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Inventory` | Tracks available + reserved quantity per product per warehouse |
| `Product` | Product ID, low-stock threshold |
| `Warehouse` | Warehouse ID, location |
| `Reservation` | Soft hold: product, quantity, expiry, order_id |
| `StockMovement` | Audit log: product, warehouse, delta, reason, timestamp |
| `InventoryService` | Orchestrates reserve, deduct, restock, expiry |
| `StockAlertObserver` | Notified when stock falls below threshold |

---

## Class Design

### Inventory

| Requirement | What Inventory must track |
|-------------|--------------------------|
| "Available vs reserved" | available_qty, reserved_qty |
| "Concurrent update safety" | version (optimistic lock) |

```
class Inventory:
- product_id: str
- warehouse_id: str
- available_qty: int
- reserved_qty: int
- version: int          # optimistic lock

+ total_qty() -> int    # available + reserved
+ can_reserve(qty) -> bool
```

### Reservation

```
class Reservation:
- id: str
- product_id: str
- warehouse_id: str
- order_id: str
- quantity: int
- created_at: datetime
- expires_at: datetime
- status: ReservationStatus   # ACTIVE, CONFIRMED, RELEASED
```

### InventoryService

```
class InventoryService:
- inventory_repo: InventoryRepository
- reservation_repo: ReservationRepository
- movement_repo: StockMovementRepository
- observers: list[StockAlertObserver]

+ reserve(product_id, warehouse_id, order_id, qty, ttl_minutes) -> Reservation
+ confirm_reservation(reservation_id) -> bool
+ release_reservation(reservation_id) -> bool
+ restock(product_id, warehouse_id, qty, reason) -> Inventory
+ expire_stale_reservations()
+ add_observer(observer: StockAlertObserver)
```

---

## Implementation

### Core Method: `reserve`

**Core logic:**
1. Fetch inventory with optimistic lock (version)
2. Check available_qty >= requested qty
3. Decrement available_qty, increment reserved_qty
4. CAS update (WHERE version = current_version)
5. Create Reservation record
6. Check low-stock threshold → notify observers

**Edge cases:**
- Insufficient stock → raise InsufficientStockError
- CAS failure (concurrent modification) → retry up to 3 times
- TTL = 0 or negative → raise ValueError

```python
def reserve(self, product_id, warehouse_id, order_id, qty, ttl_minutes=15):
    if qty <= 0:
        raise ValueError("Quantity must be positive")

    for attempt in range(3):
        inventory = self.inventory_repo.get(product_id, warehouse_id)
        if not inventory:
            raise ProductNotFoundError()
        if inventory.available_qty < qty:
            raise InsufficientStockError(
                f"Only {inventory.available_qty} available"
            )

        updated = self.inventory_repo.cas_update(
            product_id=product_id,
            warehouse_id=warehouse_id,
            expected_version=inventory.version,
            available_delta=-qty,
            reserved_delta=+qty
        )
        if updated:
            break
        if attempt == 2:
            raise ConcurrencyError("Failed to reserve after retries")

    reservation = Reservation(
        id=generate_id(),
        product_id=product_id,
        warehouse_id=warehouse_id,
        order_id=order_id,
        quantity=qty,
        created_at=datetime.utcnow(),
        expires_at=datetime.utcnow() + timedelta(minutes=ttl_minutes),
        status=ReservationStatus.ACTIVE
    )
    self.reservation_repo.save(reservation)
    self._log_movement(product_id, warehouse_id, -qty, f"Reserved for order {order_id}")
    self._check_low_stock(product_id, warehouse_id)
    return reservation
```

### CAS update in the repository

```python
def cas_update(self, product_id, warehouse_id, expected_version, available_delta, reserved_delta):
    # SQL equivalent:
    # UPDATE inventory
    # SET available_qty = available_qty + ?,
    #     reserved_qty = reserved_qty + ?,
    #     version = version + 1
    # WHERE product_id = ? AND warehouse_id = ? AND version = ?
    # Returns rows affected
    rows_affected = self.db.execute(
        "UPDATE inventory SET available_qty=available_qty+?, "
        "reserved_qty=reserved_qty+?, version=version+1 "
        "WHERE product_id=? AND warehouse_id=? AND version=?",
        [available_delta, reserved_delta, product_id, warehouse_id, expected_version]
    )
    return rows_affected > 0
```

### Observer: low-stock alert

```python
class StockAlertObserver:
    def on_stock_changed(self, product_id, warehouse_id, new_qty, threshold):
        pass

class EmailAlertObserver(StockAlertObserver):
    def on_stock_changed(self, product_id, warehouse_id, new_qty, threshold):
        if new_qty < threshold:
            send_email(
                to="ops@company.com",
                subject=f"Low stock: {product_id} at {warehouse_id}",
                body=f"Only {new_qty} units remaining (threshold: {threshold})"
            )

def _check_low_stock(self, product_id, warehouse_id):
    inventory = self.inventory_repo.get(product_id, warehouse_id)
    product = self.product_repo.get(product_id)
    if inventory.available_qty < product.low_stock_threshold:
        for observer in self.observers:
            observer.on_stock_changed(
                product_id, warehouse_id,
                inventory.available_qty,
                product.low_stock_threshold
            )
```

### Confirmation and release

```python
def confirm_reservation(self, reservation_id):
    res = self.reservation_repo.get(reservation_id)
    if res.status != ReservationStatus.ACTIVE:
        raise InvalidReservationError()
    # Move from reserved to permanently deducted (reserved_qty - qty)
    self.inventory_repo.cas_update(
        res.product_id, res.warehouse_id,
        expected_version=self._get_version(res.product_id, res.warehouse_id),
        available_delta=0,
        reserved_delta=-res.quantity
    )
    res.status = ReservationStatus.CONFIRMED
    self.reservation_repo.save(res)
    self._log_movement(res.product_id, res.warehouse_id, -res.quantity, "Order confirmed")

def release_reservation(self, reservation_id):
    res = self.reservation_repo.get(reservation_id)
    # Move reserved qty back to available
    self.inventory_repo.cas_update(
        res.product_id, res.warehouse_id,
        expected_version=self._get_version(res.product_id, res.warehouse_id),
        available_delta=+res.quantity,
        reserved_delta=-res.quantity
    )
    res.status = ReservationStatus.RELEASED
    self.reservation_repo.save(res)
    self._log_movement(res.product_id, res.warehouse_id, +res.quantity, "Reservation released")
```

---

## Verification

```
Inventory: product_id="P1", warehouse_id="W1"
  available_qty=5, reserved_qty=0, version=1

reserve("P1", "W1", "ORD-1", qty=3, ttl=15):
  available=5 >= 3 ✓
  CAS: UPDATE ... WHERE version=1 → rows=1 (success)
  available_qty=2, reserved_qty=3, version=2
  Reservation R1 created (expires in 15 min)
  _check_low_stock: available=2 < threshold=3 → EmailAlertObserver fires

confirm_reservation(R1):
  R1 status=ACTIVE ✓
  CAS: reserved_qty=3-3=0 → permanently deducted
  R1.status = CONFIRMED

Concurrent attempt: reserve("P1", "W1", "ORD-2", qty=3):
  available=2 < 3 → raise InsufficientStockError
```

---

## Deep Dive & Extensibility

### 1. "How would you handle expired reservations automatically?"

Run a background job (cron or scheduled task):

```python
def expire_stale_reservations(self):
    now = datetime.utcnow()
    expired = self.reservation_repo.find_expired(now)
    for res in expired:
        if res.status == ReservationStatus.ACTIVE:
            self.release_reservation(res.id)
```

For high throughput, process in batches and use `SKIP LOCKED` in SQL to avoid contention with concurrent workers.

### 2. "How would you support multi-warehouse fulfillment?"

When a single warehouse can't fulfill an order, split across warehouses:

```python
def reserve_across_warehouses(self, product_id, order_id, total_qty):
    warehouses = self.warehouse_repo.get_with_stock(product_id)
    remaining = total_qty
    reservations = []
    for warehouse in warehouses:
        available = warehouse.get_available(product_id)
        to_reserve = min(available, remaining)
        if to_reserve > 0:
            res = self.reserve(product_id, warehouse.id, order_id, to_reserve)
            reservations.append(res)
            remaining -= to_reserve
        if remaining == 0:
            break
    if remaining > 0:
        # Roll back all reservations
        for res in reservations:
            self.release_reservation(res.id)
        raise InsufficientStockError("Insufficient stock across all warehouses")
    return reservations
```

### 3. "What if the system needs real-time stock dashboard?"

Use event sourcing: every stock change publishes a `StockChangedEvent`. A read model (separate DB) consumes events and maintains a materialized view of current stock. Dashboard reads from the read model — no load on the write path.

### 4. "How would you add auto-reorder?"

Add `reorder_point: int` and `reorder_quantity: int` to Product. When `_check_low_stock` fires and stock falls below `reorder_point`, publish a `ReorderRequiredEvent`. A `ProcurementService` consumes it and creates a purchase order.

---

## Interviewer Questions by Level

**Junior**: Inventory entity with available/reserved quantities. reserve() and deduct(). Basic low-stock check.

**Mid-level**: Optimistic locking via CAS to prevent race conditions. Reservation TTL + expiry job. Observer for low-stock alerts. Stock movement audit log.

**Senior**: Multi-warehouse fulfillment with saga-style rollback. Event sourcing for real-time dashboard. Auto-reorder via event-driven procurement. Performance optimization (SKIP LOCKED for expiry job).

---

## Common Interview Questions

- **Q**: Why use optimistic locking instead of pessimistic (SELECT FOR UPDATE)?
  **A**: Optimistic locking (CAS/version) has no lock held between read and write — better for high read, low contention scenarios. Pessimistic locking holds a row lock for the whole transaction — better when contention is high and retries are expensive.

- **Q**: What's the difference between reserve and deduct?
  **A**: Reserve = soft hold. Available qty decreases, reserved qty increases. The stock is committed but not gone — if the order cancels, it's released back. Deduct = permanent. Reserved qty decreases (the stock is gone).

- **Q**: How do you prevent overselling?
  **A**: CAS update: `UPDATE ... WHERE version = expected AND available_qty >= qty`. If rows affected == 0, retry or fail. Only one concurrent reservation can succeed for the last unit.

- **Q**: What does the stock movement log contain?
  **A**: product_id, warehouse_id, quantity delta (positive = added, negative = removed), reason (reserved/confirmed/released/restocked), order_id (if applicable), timestamp. Supports audit trails and debugging.

- **Q**: How would you scale this to millions of SKUs?
  **A**: Shard inventory table by product_id. Cache frequently queried stock levels in Redis with write-through. Use async event processing for low-stock notifications (Kafka consumer). Batch expiry jobs with pagination.

---

## Related

**Patterns applied here**

- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)
- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Order Management](21-design-order-management.md)
- [Design Library Management](20-design-library-management.md)

Stock reservation is the same hold-then-commit flow.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
