---
module: 06-lld
topic: Problems
status: interview-ready
tags: [06-lld, system-design, problems]
---
# Design Inventory Management System

> **Difficulty**: Medium
> **Topics**: Observer Pattern, Strategy Pattern, Template Method, Concurrency
> **Extension**: Multi-warehouse allocation, FIFO/LIFO fulfilment, batch replenishment, expiry tracking

---

## What Breaks Without This Design?

```python
class Warehouse:
    def __init__(self) -> None:
        self.items = {}     # product_id → quantity
        self.threshold = 10                 # global threshold — wrong

    def sell(self, product_id: str, qty: int) -> None:
        if self.items.get(product_id, 0) < qty:
            raise Exception("Insufficient stock")
        self.items[product_id] -= qty
        if self.items[product_id] < self.threshold:
            # hardcoded — emails the same address for everything
            send_email("warehouse@company.com", f"Low stock: {product_id}")

    def restock(self, product_id: str, qty: int) -> None:
        self.items[product_id] = self.items.get(product_id, 0) + qty
```

**Concrete failures**:
1. **Integer stock with no race-condition guard**: Two concurrent `sell()` calls both read `quantity=5`, both check `5 >= 5`, both decrement — result: `quantity=-5`. Items oversold.
2. **Global threshold for all products**: A TV (low-stock threshold = 2) and a pen (threshold = 500) can't have different reorder thresholds without a separate system.
3. **Notification hardcoded in sell()**: The alert email address is baked in. Adding a Slack alert or PO system integration requires editing `sell()`.
4. **No concept of "reserved" stock**: Stock can be committed to an order and still sold again to a second customer before the first order ships — double-fulfilment.
5. **No audit trail**: No record of who sold what, when, or why stock changed — impossible to reconcile discrepancies.

---

## Derive the Class Structure

**Force 1 — Stock has two states (available vs. reserved)**: When an order is placed, stock is reserved (committed) but not yet dispatched. Available stock = total − reserved. If Amazon's warehouse oversells, it cancels orders — the correct fix is to model `available_qty` and `reserved_qty` separately.

**Force 2 — Reorder threshold and alert behaviour vary per product**: A perishable item (yoghurt, 2-day shelf life) and a durable item (TV) need different threshold values and different replenishment strategies. Extract `Product` with `reorder_threshold` and inject `ReplenishmentStrategy`.

**Force 3 — Low-stock alert recipients vary**: Email, SMS, automated PO system — all react to a low-stock event. Extract `StockObserver` interface.

**Force 4 — Stock mutation must be atomic**: Concurrent `sell()` calls must be serialized per product. Use a per-product lock, not a global warehouse lock (global lock is a bottleneck; unrelated products should not contend).

```
God class → Inventory (products: dict[str, InventoryItem])
          → InventoryItem (product: Product, total_qty, reserved_qty, lock: RLock)
             → available_qty = total_qty − reserved_qty
          → Product (product_id, name, reorder_threshold)
          → StockObserver (interface: on_low_stock(product, qty))
             → EmailAlerter, SlackAlerter, AutoPOSystem
          → ReplenishmentStrategy (interface: calculate_reorder_qty(item) → int)
             → FixedQtyReplenishment, EconomicOrderQuantity
          → StockTransaction (product_id, delta, reason, timestamp) — audit log
```

---

## Opening Analogy

Think of Amazon's fulfilment centre. Every product has a bin. The bin label shows: **Total** (all units in the bin), **Reserved** (units committed to placed orders awaiting shipping), **Available** (total − reserved — what can be sold right now). When you add an item to your cart and place an order, units move from "Available" to "Reserved" — not gone yet but can't be sold to others. When the order ships, Reserved is decremented (the units leave the building). When a restock truck arrives, Total increases. An alert fires when Available drops below the reorder point — not when Total does, because reserved units are already spoken for.

---

## Phase 1: Requirements

### Functional
- Add products to inventory with initial quantity and reorder threshold.
- Reserve stock when an order is placed (available → reserved).
- Confirm stock when an order ships (reserved decremented, total decremented).
- Release stock when an order is cancelled (reserved → available).
- Alert all observers when available stock drops below reorder threshold.
- Restock: increase total quantity.
- Query current available, reserved, and total quantities per product.

### Non-Functional
- Concurrent `reserve()` calls for the same product must not oversell.
- Per-product locking (not a global lock) for throughput.
- `Decimal` or `int` for quantities (integers for physical stock; `Decimal` for weight-based).
- Observer notification must not block stock reservation (fire after transaction completes).
- Full audit trail: every stock change logged with reason and timestamp.

---

## Phase 2: Use Cases

### Actors
- **Order Service** — reserves stock on order placement; releases on cancel; confirms on ship.
- **Warehouse Manager** — restocks inventory; sets reorder thresholds.
- **Alert System** — receives low-stock events (observer).

### UC1: Reserve Stock for Order (Concurrency-Safe)
1. Order placed for 3 units of product "P001".
2. `inventory.reserve("P001", 3, "ORDER-42")`.
3. Lock acquired for P001.
4. Check: `available_qty >= 3`. If not: raise `InsufficientStockException`.
5. `reserved_qty += 3`.
6. Release lock.
7. If `available_qty < reorder_threshold`: notify observers.

### UC2: Confirm Stock on Shipment
1. Order "ORDER-42" ships.
2. `inventory.confirm("P001", 3, "ORDER-42")`.
3. Lock acquired for P001.
4. `reserved_qty -= 3`; `total_qty -= 3` (units left the warehouse).
5. Transaction logged: `("P001", -3, "SHIPPED:ORDER-42", now)`.

### UC3: Release Stock on Cancel
1. Customer cancels "ORDER-42" before shipment.
2. `inventory.release("P001", 3, "ORDER-42")`.
3. `reserved_qty -= 3` (units back to available pool).
4. No change to `total_qty` (units still in warehouse).

### UC4: Restock and Reorder Calculation
1. Manager triggers restock for P001.
2. `ReplenishmentStrategy.calculate_reorder_qty(item)` returns 100.
3. `inventory.restock("P001", 100, "PO-789")`.
4. `total_qty += 100`; transaction logged.

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────────────┐
│                  Inventory                   │
│──────────────────────────────────────────────│
│ - items: dict[str, InventoryItem]            │
│ - observers: list[StockObserver]             │
│──────────────────────────────────────────────│
│ + add_product(product, initial_qty) → void   │
│ + reserve(product_id, qty, ref) → void       │
│ + confirm(product_id, qty, ref) → void       │
│ + release(product_id, qty, ref) → void       │
│ + restock(product_id, qty, ref) → void       │
│ + get_status(product_id) → StockStatus       │
│ + add_observer(obs: StockObserver) → void    │
└──────────────────────────────────────────────┘
         │ contains
         ▼
┌──────────────────────────────────────────────┐
│               InventoryItem                  │
│──────────────────────────────────────────────│
│ - product: Product                           │
│ - total_qty: int                             │
│ - reserved_qty: int                          │
│ - _lock: RLock                               │
│ - _transactions: list[StockTransaction]      │
│──────────────────────────────────────────────│
│ + available_qty → int (property)             │
│ + reserve(qty, ref) → void                   │
│ + confirm(qty, ref) → void                   │
│ + release(qty, ref) → void                   │
│ + restock(qty, ref) → void                   │
└──────────────────────────────────────────────┘

┌───────────────────────────────┐  ┌─────────────────────────────────┐
│        StockObserver          │  │      ReplenishmentStrategy      │
│  <<interface>>                │  │  <<interface>>                  │
│───────────────────────────────│  │─────────────────────────────────│
│ + on_low_stock(product, qty)  │  │ + calculate_reorder_qty(item)   │
└───────────────────────────────┘  │   → int                         │
▲           ▲                      └─────────────────────────────────┘
Email    AutoPOSystem              ▲                    ▲
Alerter  Observer               FixedQty             EconomicOrder
                                Replenishment        Quantity
```

---

## Phase 4: Design Patterns Applied

### 1. Observer Pattern — Low-stock alerts
**Why:** `EmailAlerter`, `SlackAlerter`, and `AutoPOSystem` all need to react to low-stock events, but `InventoryItem` must not import any of them. Observer decouples: `InventoryItem` calls `notify(product, available_qty)` after each reservation; each observer reacts independently.

**How:** `Inventory.add_observer(obs)` registers. After every reservation that crosses the threshold, `_notify_low_stock(product, qty)` iterates observers.

### 2. Strategy Pattern — Replenishment calculation
**Why:** Fixed order quantity (order 100 units whenever stock is low), Economic Order Quantity (EOQ, a formula balancing order cost vs. holding cost), and seasonal replenishment are interchangeable algorithms. Strategy lets the manager configure replenishment per product category without touching `InventoryItem`.

**How:** `Product` holds a `ReplenishmentStrategy`. `inventory.restock()` can query `item.product.replenishment.calculate_reorder_qty(item)` to know how many to order.

### 3. Template Method Pattern — Stock mutation workflow
**Why:** Every stock operation follows the same skeleton: acquire lock → validate preconditions → mutate quantities → log transaction → release lock → notify observers. The specific mutation differs (reserve vs. release vs. restock). Template Method defines the skeleton in `InventoryItem._mutate()` and lets subclasses/lambdas fill in the specific step.

**How:** `InventoryItem._apply_transaction(delta, reason, validate_fn)` acquires the lock, calls `validate_fn()` (raises if invalid), mutates, logs. All public methods delegate to this.

---

## Phase 5: Key Python Implementation

```python
from abc import ABC, abstractmethod
from datetime import datetime

# ── Domain types ──────────────────────────────────────────────────────────────

class StockTransaction:
    def __init__(self, product_id, delta, reason):
        self.product_id = product_id
        self.delta = delta          # positive = added, negative = removed
        self.reason = reason
        self.timestamp = datetime.now()

class StockStatus:
    def __init__(self, product_id, total_qty, reserved_qty, available_qty):
        self.product_id = product_id
        self.total_qty = total_qty
        self.reserved_qty = reserved_qty
        self.available_qty = available_qty

# ── Replenishment Strategy ────────────────────────────────────────────────────

class ReplenishmentStrategy(ABC):
    @abstractmethod
    def calculate_reorder_qty(self, item): ...

class FixedQtyReplenishment(ReplenishmentStrategy):
    def __init__(self, fixed_qty):
        self._qty = fixed_qty
    def calculate_reorder_qty(self, item):
        return self._qty

class EOQReplenishment(ReplenishmentStrategy):
    def __init__(self, annual_demand, order_cost, holding_cost):
        import math
        self._eoq = int(math.sqrt((2 * annual_demand * order_cost) / holding_cost))
    def calculate_reorder_qty(self, item):
        return self._eoq

# ── Product ───────────────────────────────────────────────────────────────────

class Product:
    def __init__(self, product_id, name, reorder_threshold, replenishment=None):
        self.product_id = product_id
        self.name = name
        self.reorder_threshold = reorder_threshold
        self.replenishment = replenishment if replenishment is not None else FixedQtyReplenishment(100)

# ── Observer ──────────────────────────────────────────────────────────────────

class StockObserver(ABC):
    @abstractmethod
    def on_low_stock(self, product, available_qty): ...

class EmailAlerter(StockObserver):
    def on_low_stock(self, product, available_qty):
        print(f"[EMAIL] LOW STOCK: {product.name} has {available_qty} units left")

class AutoPOSystem(StockObserver):
    def __init__(self):
        self.pending_pos = []
    def on_low_stock(self, product, available_qty):
        reorder_qty = product.replenishment.calculate_reorder_qty(None)  # type: ignore
        self.pending_pos.append({"product_id": product.product_id, "qty": reorder_qty})
        print(f"[PO] Auto-raising PO for {reorder_qty} units of {product.name}")

# ── InventoryItem (concurrency-safe) ─────────────────────────────────────────

class InsufficientStockException(Exception): ...

class InventoryItem:
    def __init__(self, product, initial_qty):
        self.product = product
        self.total_qty = initial_qty
        self.reserved_qty = 0
        self._transactions = []

    def available_qty(self):
        return self.total_qty - self.reserved_qty

    def _log(self, delta, reason):
        self._transactions.append(StockTransaction(self.product.product_id, delta, reason))

    def reserve(self, qty, order_ref):
        if self.available_qty() < qty:
            raise InsufficientStockException(
                f"Only {self.available_qty()} available for {self.product.product_id}, requested {qty}"
            )
        self.reserved_qty += qty
        self._log(-qty, f"RESERVED:{order_ref}")

    def confirm(self, qty, order_ref):
        if self.reserved_qty < qty:
            raise ValueError(f"Reserved qty {self.reserved_qty} < confirm qty {qty}")
        self.reserved_qty -= qty
        self.total_qty -= qty
        self._log(-qty, f"SHIPPED:{order_ref}")

    def release(self, qty, order_ref):
        if self.reserved_qty < qty:
            raise ValueError("Cannot release more than reserved")
        self.reserved_qty -= qty
        self._log(qty, f"RELEASED:{order_ref}")

    def restock(self, qty, po_ref):
        self.total_qty += qty
        self._log(qty, f"RESTOCKED:{po_ref}")

    def status(self):
        return StockStatus(
            self.product.product_id,
            self.total_qty,
            self.reserved_qty,
            self.available_qty(),
        )

    def get_transactions(self):
        return list(self._transactions)

# ── Inventory ─────────────────────────────────────────────────────────────────

class Inventory:
    def __init__(self):
        self._items = {}
        self._observers = []

    def add_observer(self, obs):
        self._observers.append(obs)

    def add_product(self, product, initial_qty):
        if product.product_id in self._items:
            raise ValueError(f"Product {product.product_id} already exists")
        self._items[product.product_id] = InventoryItem(product, initial_qty)

    def _get(self, product_id):
        if product_id not in self._items:
            raise KeyError(f"Product {product_id} not found")
        return self._items[product_id]

    def _maybe_alert(self, item):
        if item.available_qty() < item.product.reorder_threshold:
            for obs in self._observers:
                obs.on_low_stock(item.product, item.available_qty())

    def reserve(self, product_id, qty, order_ref):
        item = self._get(product_id)
        item.reserve(qty, order_ref)
        self._maybe_alert(item)

    def confirm(self, product_id, qty, order_ref):
        self._get(product_id).confirm(qty, order_ref)

    def release(self, product_id, qty, order_ref):
        self._get(product_id).release(qty, order_ref)

    def restock(self, product_id, qty, po_ref):
        self._get(product_id).restock(qty, po_ref)

    def get_status(self, product_id):
        return self._get(product_id).status()

# ── Demo ──────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    inv = Inventory()
    inv.add_observer(EmailAlerter())
    inv.add_observer(AutoPOSystem())

    kindle = Product("P001", "Kindle", reorder_threshold=5,
                     replenishment=FixedQtyReplenishment(50))
    inv.add_product(kindle, initial_qty=10)

    print("--- Place two orders ---")
    inv.reserve("P001", 3, "ORDER-001")   # available: 10-3=7
    inv.reserve("P001", 4, "ORDER-002")   # available: 7-4=3 → below threshold=5 → alert

    print("\n--- Ship first order ---")
    inv.confirm("P001", 3, "ORDER-001")   # total: 10-3=7, reserved: 4-0=4

    print("\n--- Cancel second order ---")
    inv.release("P001", 4, "ORDER-002")   # reserved: 4-4=0, total stays at 7

    print("\n--- Restock ---")
    inv.restock("P001", 50, "PO-789")

    status = inv.get_status("P001")
    print(f"\nFinal status: total={status.total_qty}, reserved={status.reserved_qty}, available={status.available_qty}")
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Reserved + Total model | Track `reserved_qty` and `total_qty` separately | Single `available_qty` counter | Allows "available = total − reserved" to be computed; enables partial cancellation and order tracking |
| Per-product RLock | One `RLock` per `InventoryItem` | Single global lock on `Inventory` | Global lock serializes all products — kills throughput; per-product lock only serializes concurrent access to the same SKU |
| Alert after transaction | Notify observers after lock is released | Notify inside lock | Observers may do slow I/O (HTTP); holding the lock during I/O starves other threads |
| Int for quantity | `int` | `Decimal` | Physical inventory is always whole units; `Decimal` is for weight- or volume-based stock (e.g., 2.5 kg flour) |

### Extensions

**Multi-warehouse allocation:**
```python
class WarehouseAllocator:
    def __init__(self, warehouses):
        self._warehouses = warehouses

    def reserve(self, product_id, qty, order_ref):
        for wh in self._warehouses:
            try:
                wh.reserve(product_id, qty, order_ref)
                return
            except InsufficientStockException:
                continue
        raise InsufficientStockException(f"No warehouse has {qty} units of {product_id}")
```

**Expiry tracking (FIFO for perishables):**
```python
@dataclass(order=True)
class StockBatch:
    expiry_date: datetime       # sorted ascending — oldest first
    qty: int
    batch_id: str = field(compare=False)
```
Use `heapq` to maintain batches sorted by expiry; `reserve()` always picks the soonest-to-expire batch first (FEFO — First Expired First Out).

---

## Interviewer Follow-Up Questions

- "Two concurrent requests try to buy the last 3 units when only 3 are left. How do you prevent overselling?" → Per-product `RLock` (or DB row lock in production). Both threads acquire the lock for product "P001"; one proceeds. Inside the lock: check `available_qty >= 3`. First thread: check passes (3 >= 3), `reserved_qty += 3`, `available_qty = 0`. Second thread: check fails (0 >= 3 → False), raises `InsufficientStockException`. The customer sees "out of stock." The RLock serializes both threads — only one proceeds with the reservation. Global lock would work too but is a bottleneck; per-product lock allows TVs and pens to be sold concurrently without contention.
- "What's the difference between 'reserve' and 'confirm' in your model?" → Reserve: moves stock from "available" to "committed" — the customer has placed the order but the warehouse hasn't shipped yet. Available qty decreases, total qty stays the same. Confirm (ship): units physically leave the warehouse — total qty decreases, reserved qty decreases. Release (cancel): the commitment is undone — reserved qty decreases, total qty stays the same (units back in the available pool). The three-state model (total, reserved, available) is how Amazon's actual inventory system works — "available to promise" (ATP) is `total − reserved`.
- "How would you handle an Amazon Flash Sale where 100K customers try to buy the same product simultaneously?" → Per-product RLock on a single server serializes 100K threads — that's a single-point bottleneck. For flash sales: (1) Pre-allocate stock into a Redis counter using `DECRBY`. Redis is single-threaded, so atomic `DECRBY` is safe without application-level locking. If the result >= 0: reservation succeeds. If negative: reverse the decrement, return "sold out." (2) Queue requests via SQS FIFO — process reservations one at a time per product. (3) Shard the product across virtual bins (each bin holds 1,000 units; distribute 100K requests across shards). Mention the tradeoff: Redis is fast and correct; queue adds latency but is more durable.
- "How do you audit stock discrepancies — if total_qty doesn't match physical count?" → Every stock mutation logged as a `StockTransaction` with delta, reason, and timestamp. Physical count = Σ(all transaction deltas starting from initial). On discrepancy: walk the transaction log to find the last consistent state and which operation created the gap. In practice: warehouse management systems do periodic cycle counts and reconcile against the transaction log. The audit trail is non-negotiable — without it, there's no way to determine if the discrepancy is theft, receiving error, or a system bug.
- "How do you handle a product that's sold by weight — 2.5 kg of flour?" → Replace `int` quantities with `Decimal`. `reserve("flour", Decimal("2.500"), "ORDER-42")`. All arithmetic uses `Decimal` to avoid float precision errors. Weight-based products may also need unit conversion (grams vs. kilograms) — encapsulate in a `Quantity(amount: Decimal, unit: Unit)` value object with a `convert_to(unit)` method.
