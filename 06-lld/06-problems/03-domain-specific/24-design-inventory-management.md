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
- productId: String
- warehouseId: String
- availableQty: int
- reservedQty: int
- version: int          // optimistic lock

+ totalQty() -> int     // available + reserved
+ canReserve(qty) -> boolean
```

### Reservation

```
class Reservation:
- id: String
- productId: String
- warehouseId: String
- orderId: String
- quantity: int
- createdAt: Instant
- expiresAt: Instant
- status: ReservationStatus   // ACTIVE, CONFIRMED, RELEASED
```

### InventoryService

```
class InventoryService:
- inventoryRepo: InventoryRepository
- reservationRepo: ReservationRepository
- movementRepo: StockMovementRepository
- observers: List<StockAlertObserver>

+ reserve(productId, warehouseId, orderId, qty, ttlMinutes) -> Reservation
+ confirmReservation(reservationId) -> boolean
+ releaseReservation(reservationId) -> boolean
+ restock(productId, warehouseId, qty, reason) -> Inventory
+ expireStaleReservations()
+ addObserver(observer: StockAlertObserver)
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

```java
public Reservation reserve(String productId, String warehouseId, String orderId,
                            int qty, int ttlMinutes) {
    if (qty <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    boolean updated = false;
    for (int attempt = 0; attempt < 3; attempt++) {
        Inventory inventory = inventoryRepo.get(productId, warehouseId);
        if (inventory == null) {
            throw new ProductNotFoundError();
        }
        if (inventory.getAvailableQty() < qty) {
            throw new InsufficientStockError(
                String.format("Only %d available", inventory.getAvailableQty())
            );
        }

        updated = inventoryRepo.casUpdate(
            productId,
            warehouseId,
            inventory.getVersion(),
            -qty,
            +qty
        );
        if (updated) {
            break;
        }
        if (attempt == 2) {
            throw new ConcurrencyError("Failed to reserve after retries");
        }
    }

    Reservation reservation = new Reservation(
        generateId(),
        productId,
        warehouseId,
        orderId,
        qty,
        Instant.now(),
        Instant.now().plus(Duration.ofMinutes(ttlMinutes)),
        ReservationStatus.ACTIVE
    );
    reservationRepo.save(reservation);
    logMovement(productId, warehouseId, -qty, String.format("Reserved for order %s", orderId));
    checkLowStock(productId, warehouseId);
    return reservation;
}
```

### CAS update in the repository

```java
public boolean casUpdate(String productId, String warehouseId, int expectedVersion,
                          int availableDelta, int reservedDelta) {
    // SQL equivalent:
    // UPDATE inventory
    // SET available_qty = available_qty + ?,
    //     reserved_qty = reserved_qty + ?,
    //     version = version + 1
    // WHERE product_id = ? AND warehouse_id = ? AND version = ?
    // Returns rows affected
    int rowsAffected = db.execute(
        "UPDATE inventory SET available_qty=available_qty+?, " +
        "reserved_qty=reserved_qty+?, version=version+1 " +
        "WHERE product_id=? AND warehouse_id=? AND version=?",
        availableDelta, reservedDelta, productId, warehouseId, expectedVersion
    );
    return rowsAffected > 0;
}
```

### Observer: low-stock alert

```java
public interface StockAlertObserver {
    void onStockChanged(String productId, String warehouseId, int newQty, int threshold);
}

public class EmailAlertObserver implements StockAlertObserver {
    @Override
    public void onStockChanged(String productId, String warehouseId, int newQty, int threshold) {
        if (newQty < threshold) {
            EmailService.sendEmail(
                "ops@company.com",
                String.format("Low stock: %s at %s", productId, warehouseId),
                String.format("Only %d units remaining (threshold: %d)", newQty, threshold)
            );
        }
    }
}

private void checkLowStock(String productId, String warehouseId) {
    Inventory inventory = inventoryRepo.get(productId, warehouseId);
    Product product = productRepo.get(productId);
    if (inventory.getAvailableQty() < product.getLowStockThreshold()) {
        for (StockAlertObserver observer : observers) {
            observer.onStockChanged(
                productId, warehouseId,
                inventory.getAvailableQty(),
                product.getLowStockThreshold()
            );
        }
    }
}
```

### Confirmation and release

```java
public boolean confirmReservation(String reservationId) {
    Reservation res = reservationRepo.get(reservationId);
    if (res.getStatus() != ReservationStatus.ACTIVE) {
        throw new InvalidReservationError();
    }
    // Move from reserved to permanently deducted (reserved_qty - qty)
    inventoryRepo.casUpdate(
        res.getProductId(), res.getWarehouseId(),
        getVersion(res.getProductId(), res.getWarehouseId()),
        0,
        -res.getQuantity()
    );
    res.setStatus(ReservationStatus.CONFIRMED);
    reservationRepo.save(res);
    logMovement(res.getProductId(), res.getWarehouseId(), -res.getQuantity(), "Order confirmed");
    return true;
}

public boolean releaseReservation(String reservationId) {
    Reservation res = reservationRepo.get(reservationId);
    // Move reserved qty back to available
    inventoryRepo.casUpdate(
        res.getProductId(), res.getWarehouseId(),
        getVersion(res.getProductId(), res.getWarehouseId()),
        +res.getQuantity(),
        -res.getQuantity()
    );
    res.setStatus(ReservationStatus.RELEASED);
    reservationRepo.save(res);
    logMovement(res.getProductId(), res.getWarehouseId(), +res.getQuantity(), "Reservation released");
    return true;
}
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

```java
public void expireStaleReservations() {
    Instant now = Instant.now();
    List<Reservation> expired = reservationRepo.findExpired(now);
    for (Reservation res : expired) {
        if (res.getStatus() == ReservationStatus.ACTIVE) {
            releaseReservation(res.getId());
        }
    }
}
```

For high throughput, process in batches and use `SKIP LOCKED` in SQL to avoid contention with concurrent workers.

### 2. "How would you support multi-warehouse fulfillment?"

When a single warehouse can't fulfill an order, split across warehouses:

```java
public List<Reservation> reserveAcrossWarehouses(String productId, String orderId, int totalQty) {
    List<Warehouse> warehouses = warehouseRepo.getWithStock(productId);
    int remaining = totalQty;
    List<Reservation> reservations = new ArrayList<>();
    for (Warehouse warehouse : warehouses) {
        int available = warehouse.getAvailable(productId);
        int toReserve = Math.min(available, remaining);
        if (toReserve > 0) {
            Reservation res = reserve(productId, warehouse.getId(), orderId, toReserve, DEFAULT_TTL_MINUTES);
            reservations.add(res);
            remaining -= toReserve;
        }
        if (remaining == 0) {
            break;
        }
    }
    if (remaining > 0) {
        // Roll back all reservations
        for (Reservation res : reservations) {
            releaseReservation(res.getId());
        }
        throw new InsufficientStockError("Insufficient stock across all warehouses");
    }
    return reservations;
}
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

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
