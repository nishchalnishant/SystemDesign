> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Restaurant Management System — table reservation, order taking, kitchen ticket routing, and billing in one cohesive model.
>
> **Key concepts:**
> - Core Entities: `RestaurantTable`, `Reservation`, `MenuItem` (with `MenuCategory`), `Order`, `OrderItem`, `KitchenTicket`, `Bill`.
> - The problem: assigning tables without double-booking, taking orders that fan out into kitchen tickets, and enforcing a valid order-item status lifecycle all the way to a (possibly split) bill.
> - Patterns:
>   - State: `OrderItem` status (`PLACED → IN_KITCHEN → READY → SERVED → PAID`) as a state machine with legal-transition enforcement, not a bag of booleans.
>   - Observer: `KitchenDisplaySystem` subscribes to new-order events so it updates in real time without `Order` knowing about the UI.
>   - Strategy (extension): split-bill strategies (equal split vs. per-item assignment).
> - Concurrency: Table reservation for the same table/time slot is a classic race — must be atomic (lock or CAS) so exactly one request wins. Order-item status updates must validate the transition inside the same critical section that mutates state.
>
> **Key takeaway:** The core insight is decoupling three independent lifecycles — table occupancy, order-item kitchen progress, and billing — that only *reference* each other through IDs. Model each as its own small state machine instead of one giant `Order` object with flags for everything.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, restaurant-management, state-pattern, observer-pattern, concurrency]
---
# Design a Restaurant Management System

> **Difficulty**: Medium  
> **Asked at**: OpenTable, Toast, Swiggy, DoorDash  
> **Key Patterns**: State (order-item lifecycle), Observer (kitchen display notifications), Strategy (bill splitting)

---

## Understanding the Problem

Design a restaurant management system that handles dine-in table assignment/reservation, order taking against a categorized menu, routing order items to the kitchen as tickets, tracking each item through preparation to served, and generating a bill at the end of the meal — including splitting the bill across multiple guests.

---

## Clarifying Questions

**You**: "Should this cover table reservations, order management, or both?"  
**Interviewer**: "Both. A host assigns or reserves tables, and a server takes orders against an assigned table. Model the full flow from seating to payment."

**You**: "How is the menu structured — flat list or categories?"  
**Interviewer**: "Categories — appetizers, mains, desserts, beverages. Each item has a price and an availability flag."

**You**: "What's the lifecycle of an order item once it's placed?"  
**Interviewer**: "PLACED when the server submits it, IN_KITCHEN once the kitchen starts prepping, READY when it's plated, SERVED once it reaches the table, and PAID after the bill is settled. Enforce that order — no skipping ahead."

**You**: "Do we need to support splitting the bill?"  
**Interviewer**: "Yes — split evenly across N guests, and also support assigning specific items to specific guests for itemized splits."

**You**: "Does the kitchen need a live view of incoming orders, or is polling fine?"  
**Interviewer**: "Assume a kitchen display system (KDS) that should update in real time — model it as something that reacts to new orders and status changes, not something that polls the order table."

**You**: "Can a table be reserved for a future time slot, or is it walk-in-only?"  
**Interviewer**: "Both — support advance reservations for a time window, and immediate walk-in seating. Prevent double-booking the same table for overlapping windows."

**You**: "What about menu items going out of stock mid-service?"  
**Interviewer**: "Good question — handle that too. If an item is marked unavailable, it shouldn't be orderable, and there should be a sane way to handle it if it's already mid-order."

---

## Final Requirements

**In scope:**
1. Assign a walk-in table or reserve a table for a future time window; prevent overlapping reservations for the same table
2. Browse a categorized menu with per-item availability
3. Place an order for a table — creates order items, each independently tracked
4. Kitchen ticket routing: new orders notify a Kitchen Display System via Observer
5. Order-item status lifecycle enforced as a state machine: `PLACED → IN_KITCHEN → READY → SERVED → PAID`
6. Generate a bill with tax; support even split across guests and itemized split by guest assignment
7. Mark menu items unavailable and handle in-flight orders for items that go out of stock
8. Thread-safe table reservation and order-item status transitions

**Out of scope:**
- Payment gateway integration (assume `Bill.markPaid()` is called after external payment succeeds)
- Inventory/ingredient-level stock tracking (menu item availability is a single boolean)
- Delivery/takeout logistics
- Staff scheduling and shift management

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| RestaurantTable | Physical table; tracks current occupancy state and capacity |
| Reservation | Binds a table to a time window and party; used to prevent overlap |
| MenuCategory | Groups MenuItems (Appetizers, Mains, Desserts, Beverages) |
| MenuItem | Name, price, category, availability flag |
| Order | Belongs to one table; owns a list of OrderItems |
| OrderItem | One menu item + quantity within an order; owns its own status state machine |
| KitchenTicket | Kitchen-facing view of an order's items, created when an order is placed |
| KitchenDisplaySystem | Observer that reacts to new orders / status changes |
| Bill | Computed from an order's items; supports even or itemized split |

A `RestaurantTable` is reserved via `Reservation` (future) or assigned directly (walk-in). An `Order` is created against an occupied table and owns `OrderItem`s, each independently progressing through its state machine. Placing an order publishes an event that `KitchenDisplaySystem` (an `OrderObserver`) consumes to render a `KitchenTicket`. `Bill` is derived from the `Order`'s items at settlement time and is not itself observed.

---

## Class Design

### RestaurantTable / Reservation

| Requirement | What must be tracked |
|-------------|----------------------|
| Prevent double-booking | reservations: sorted list of time windows per table |
| Current occupancy | status: AVAILABLE / RESERVED / OCCUPIED |
| Party size fit | capacity: int |

```
class RestaurantTable:
- table_id: str
- capacity: int
- status: TableStatus          # AVAILABLE, RESERVED, OCCUPIED
- reservations: list[Reservation]
- _lock: threading.Lock

+ has_conflict(start: datetime, end: datetime) -> bool
+ reserve(reservation: Reservation) -> None
+ seat_walk_in(party_size: int) -> None
+ free() -> None

class Reservation:
- reservation_id: str
- table: RestaurantTable
- guest_name: str
- party_size: int
- start_time: datetime
- end_time: datetime
- status: ReservationStatus     # PENDING, CONFIRMED, CANCELLED, COMPLETED
```

### Menu

```
class MenuCategory(Enum):
    APPETIZER, MAIN, DESSERT, BEVERAGE

class MenuItem:
- item_id: str
- name: str
- price: BigDecimal
- category: MenuCategory
- is_available: bool

+ mark_unavailable() -> None
+ mark_available() -> None
```

### OrderItem — State pattern

```
interface OrderItemState:
+ next(context: OrderItem) -> None
+ name() -> OrderItemStatus

class OrderItem:
- order_item_id: str
- menu_item: MenuItem
- quantity: int
- state: OrderItemState          # current state object, starts PlacedState
- guest_assignment: str | None   # for itemized split

+ advance() -> None               # delegates to state.next(this)
+ transition_to(target: OrderItemStatus) -> None  # validated jump, used by kitchen/server actions
+ status() -> OrderItemStatus
```

States: `PlacedState → InKitchenState → ReadyState → ServedState → PaidState`. Each state object knows only the *next* legal state and rejects out-of-order calls (e.g., `ServedState` cannot be reached from `PlacedState` directly).

### Order

```
class Order:
- order_id: str
- table: RestaurantTable
- items: list[OrderItem]
- observers: list[OrderObserver]   # Observer subject
- created_at: datetime

+ add_observer(observer: OrderObserver) -> None
+ place_item(menu_item: MenuItem, quantity: int, guest: str | None) -> OrderItem
+ notify_new_order() -> None
+ update_item_status(order_item_id: str, target: OrderItemStatus) -> None
```

### KitchenTicket / KitchenDisplaySystem — Observer

```
interface OrderObserver:
+ on_new_order(order: Order) -> None
+ on_item_status_changed(order_item: OrderItem) -> None

class KitchenTicket:
- ticket_id: str
- order_id: str
- table_id: str
- items: list[OrderItem]
- created_at: datetime

class KitchenDisplaySystem(OrderObserver):
- open_tickets: dict[str, KitchenTicket]   # order_id -> ticket

+ on_new_order(order: Order) -> None        # creates a KitchenTicket, renders it
+ on_item_status_changed(order_item: OrderItem) -> None  # updates ticket view
```

### Bill

```
class Bill:
- bill_id: str
- order: Order
- tax_rate: BigDecimal
- subtotal: BigDecimal
- tax: BigDecimal
- total: BigDecimal
- is_paid: bool

+ split_evenly(num_guests: int) -> list[BigDecimal]
+ split_by_guest() -> dict[str, BigDecimal]
+ mark_paid() -> None
```

---

## Implementation

### Core Method: reserveTable / assignTable (availability check)

**Core logic:**
1. Lock the table (per-table lock avoids a global bottleneck across many tables)
2. For a reservation: scan existing reservations for overlap with `[start, end)`; reject if any active reservation conflicts
3. For a walk-in: reject unless `status == AVAILABLE`
4. Mutate state and release lock atomically

**Edge cases:**
- Two reservation requests for the same table/window racing — only one may win
- Party size exceeds table capacity — reject before touching reservation state
- Walk-in seating a table that has a reservation starting soon — allowed only if requirements say so; here we simply reject if `status != AVAILABLE`

```java
public class RestaurantTable {
    private final String tableId;
    private final int capacity;
    private TableStatus status = TableStatus.AVAILABLE;
    private final List<Reservation> reservations = new ArrayList<>();
    private final Object lock = new Object();

    public RestaurantTable(String tableId, int capacity) {
        this.tableId = tableId;
        this.capacity = capacity;
    }

    public boolean hasConflict(LocalDateTime start, LocalDateTime end) {
        for (Reservation r : reservations) {
            if (r.getStatus() == ReservationStatus.CANCELLED
                    || r.getStatus() == ReservationStatus.COMPLETED) {
                continue;
            }
            boolean disjoint = !end.isAfter(r.getStartTime()) || !start.isBefore(r.getEndTime());
            if (!disjoint) {
                return true;
            }
        }
        return false;
    }

    public Reservation reserve(String guestName, int partySize, LocalDateTime start, LocalDateTime end) {
        if (partySize > capacity) {
            throw new IllegalArgumentException("Party of " + partySize + " exceeds capacity " + capacity);
        }
        synchronized (lock) {
            if (hasConflict(start, end)) {
                throw new IllegalStateException("Table " + tableId + " already booked for that window");
            }
            Reservation reservation = new Reservation(
                UUID.randomUUID().toString(), this, guestName, partySize, start, end);
            reservations.add(reservation);
            return reservation;
        }
    }

    public void seatWalkIn(int partySize) {
        if (partySize > capacity) {
            throw new IllegalArgumentException("Party of " + partySize + " exceeds capacity " + capacity);
        }
        synchronized (lock) {
            if (status != TableStatus.AVAILABLE) {
                throw new IllegalStateException("Table " + tableId + " is not available");
            }
            status = TableStatus.OCCUPIED;
        }
    }

    public void free() {
        synchronized (lock) {
            status = TableStatus.AVAILABLE;
        }
    }

    public String getTableId() { return tableId; }
    public int getCapacity() { return capacity; }
    public TableStatus getStatus() { return status; }
    public List<Reservation> getReservations() { return reservations; }
}
```

### Core Method: placeOrder (creates OrderItems, notifies kitchen)

**Core logic:**
1. Reject items whose `menu_item.is_available == false`
2. Create an `OrderItem` in `PLACED` state for each requested line
3. Append to `Order.items`
4. Call `notify_new_order()` — fans out to every registered `OrderObserver` (e.g., the KDS)

**Edge cases:**
- Ordering an unavailable item — reject that line, don't fail the whole order (configurable; we choose per-line rejection with a returned error list)
- Empty order — reject with no observer notification

```java
public class Order {
    private final String orderId;
    private final RestaurantTable table;
    private final List<OrderItem> items = new ArrayList<>();
    private final List<OrderObserver> observers = new ArrayList<>();
    private final LocalDateTime createdAt = LocalDateTime.now();
    private final Object lock = new Object();

    public Order(String orderId, RestaurantTable table) {
        this.orderId = orderId;
        this.table = table;
    }

    public void addObserver(OrderObserver observer) {
        observers.add(observer);
    }

    public OrderItem placeItem(MenuItem menuItem, int quantity, String guest) {
        if (!menuItem.isAvailable()) {
            throw new IllegalStateException(menuItem.getName() + " is currently unavailable");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        OrderItem item = new OrderItem(UUID.randomUUID().toString(), menuItem, quantity, guest);
        synchronized (lock) {
            items.add(item);
        }
        notifyNewOrder();
        return item;
    }

    public void notifyNewOrder() {
        for (OrderObserver observer : observers) {
            observer.onNewOrder(this);
        }
    }

    public void updateItemStatus(String orderItemId, OrderItemStatus target) {
        OrderItem item = findItem(orderItemId);
        item.transitionTo(target);
        for (OrderObserver observer : observers) {
            observer.onItemStatusChanged(item);
        }
    }

    private OrderItem findItem(String orderItemId) {
        synchronized (lock) {
            for (OrderItem item : items) {
                if (item.getOrderItemId().equals(orderItemId)) {
                    return item;
                }
            }
        }
        throw new NoSuchElementException("No such order item: " + orderItemId);
    }

    public String getOrderId() { return orderId; }
    public RestaurantTable getTable() { return table; }
    public List<OrderItem> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

### Core Method: updateOrderItemStatus (state machine transition validation)

**Core logic:**
1. Each `OrderItem` holds a `state: OrderItemState` object (State pattern), not a raw enum with ad-hoc `if` checks
2. `transitionTo(target)` locks the item, asks the current state whether `target` is the legal next status, and swaps state objects atomically
3. Illegal transitions (e.g., `SERVED` requested while still `PLACED`) throw before any mutation

**Edge cases:**
- Requesting the same status twice — reject as illegal (no self-loop)
- Skipping a state (`PLACED → SERVED`) — reject
- Two threads racing to advance the same item — lock makes the check-and-set atomic

```java
public enum OrderItemStatus { PLACED, IN_KITCHEN, READY, SERVED, PAID }

public interface OrderItemState {
    OrderItemStatus name();
    OrderItemState nextState(OrderItemStatus target);
}

public class PlacedState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.PLACED; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.IN_KITCHEN) return new InKitchenState();
        throw new IllegalStateException("Cannot go from PLACED to " + target);
    }
}

public class InKitchenState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.IN_KITCHEN; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.READY) return new ReadyState();
        throw new IllegalStateException("Cannot go from IN_KITCHEN to " + target);
    }
}

public class ReadyState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.READY; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.SERVED) return new ServedState();
        throw new IllegalStateException("Cannot go from READY to " + target);
    }
}

public class ServedState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.SERVED; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.PAID) return new PaidState();
        throw new IllegalStateException("Cannot go from SERVED to " + target);
    }
}

public class PaidState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.PAID; }
    public OrderItemState nextState(OrderItemStatus target) {
        throw new IllegalStateException("PAID is terminal, cannot transition to " + target);
    }
}

public class OrderItem {
    private final String orderItemId;
    private final MenuItem menuItem;
    private final int quantity;
    private final String guestAssignment;
    private volatile OrderItemState state = new PlacedState();
    private final Object lock = new Object();

    public OrderItem(String orderItemId, MenuItem menuItem, int quantity, String guestAssignment) {
        this.orderItemId = orderItemId;
        this.menuItem = menuItem;
        this.quantity = quantity;
        this.guestAssignment = guestAssignment;
    }

    public void transitionTo(OrderItemStatus target) {
        synchronized (lock) {
            state = state.nextState(target);
        }
    }

    public OrderItemStatus status() {
        return state.name();
    }

    public String getOrderItemId() { return orderItemId; }
    public MenuItem getMenuItem() { return menuItem; }
    public int getQuantity() { return quantity; }
    public String getGuestAssignment() { return guestAssignment; }
}
```

### Core Method: generateBill (sum with tax, optional split)

**Core logic:**
1. Sum `menu_item.price * quantity` across all order items that are not already `PAID`
2. Apply `tax_rate` to get `total`
3. `split_evenly(n)` divides `total` into `n` shares, pushing any rounding remainder onto the last share so shares sum exactly to `total`
4. `split_by_guest()` groups items by `guest_assignment`, taxes each group's subtotal proportionally

```java
public class Bill {
    private final String billId;
    private final Order order;
    private final BigDecimal taxRate;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private boolean paid = false;

    public Bill(String billId, Order order, BigDecimal taxRate) {
        this.billId = billId;
        this.order = order;
        this.taxRate = taxRate;
        computeTotals();
    }

    private void computeTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal lineTotal = item.getMenuItem().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
            sum = sum.add(lineTotal);
        }
        subtotal = sum.setScale(2, RoundingMode.HALF_UP);
        tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        total = subtotal.add(tax);
    }

    public List<BigDecimal> splitEvenly(int numGuests) {
        if (numGuests <= 0) {
            throw new IllegalArgumentException("numGuests must be positive");
        }
        BigDecimal share = total.divide(BigDecimal.valueOf(numGuests), 2, RoundingMode.DOWN);
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int i = 0; i < numGuests - 1; i++) {
            shares.add(share);
            running = running.add(share);
        }
        shares.add(total.subtract(running));
        return shares;
    }

    public Map<String, BigDecimal> splitByGuest() {
        Map<String, BigDecimal> guestSubtotals = new LinkedHashMap<>();
        for (OrderItem item : order.getItems()) {
            String guest = item.getGuestAssignment() != null ? item.getGuestAssignment() : "UNASSIGNED";
            BigDecimal lineTotal = item.getMenuItem().getPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
            guestSubtotals.merge(guest, lineTotal, BigDecimal::add);
        }
        Map<String, BigDecimal> guestTotals = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : guestSubtotals.entrySet()) {
            BigDecimal guestSubtotal = entry.getValue();
            BigDecimal guestTax = guestSubtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            guestTotals.put(entry.getKey(), guestSubtotal.setScale(2, RoundingMode.HALF_UP).add(guestTax));
        }
        return guestTotals;
    }

    public void markPaid() {
        paid = true;
        for (OrderItem item : order.getItems()) {
            if (item.status() == OrderItemStatus.SERVED) {
                item.transitionTo(OrderItemStatus.PAID);
            }
        }
    }

    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public boolean isPaid() { return paid; }
}
```

---

## Verification

**Scenario**: Table T5 (capacity 4) seats a walk-in party of 3. Server orders 2x Margherita Pizza ($14.00 each) and 1x Iced Tea ($3.50). Tax rate is 8%.

1. `table.seatWalkIn(3)` — capacity 4 >= 3, status `AVAILABLE → OCCUPIED`
2. `order.placeItem(pizza, 2, null)` — pizza is available, `OrderItem(PLACED)` created, `notifyNewOrder()` fires → KDS creates `KitchenTicket K001` with this line
3. `order.placeItem(icedTea, 1, null)` — same flow, appended to the order (KDS ticket now shows both lines since it re-renders from `order.getItems()`)
4. Kitchen begins prep: `order.updateItemStatus(pizzaItemId, IN_KITCHEN)` — `PlacedState.nextState(IN_KITCHEN)` succeeds → state becomes `InKitchenState`
5. Kitchen finishes: `order.updateItemStatus(pizzaItemId, READY)` — succeeds; `order.updateItemStatus(pizzaItemId, SERVED)` — succeeds once delivered to table
6. Attempting `order.updateItemStatus(icedTeaItemId, SERVED)` while iced tea is still `PLACED` — throws `IllegalStateException("Cannot go from PLACED to SERVED")`, correctly rejected
7. All items reach `SERVED`. `Bill` computed: subtotal = 2×14.00 + 3.50 = **$31.50**; tax = 31.50 × 0.08 = **$2.52**; total = **$34.02**
8. `bill.splitEvenly(3)` → share = 34.02 / 3 = 11.34 each → `[$11.34, $11.34, $11.34]` (sums exactly to $34.02)
9. `bill.markPaid()` — every `SERVED` item transitions to `PAID`; table can now be freed with `table.free()`

---

## Deep Dive & Extensibility

### 1. "How would you handle split billing among multiple guests at a table?"

Two modes are common: even split (divide total by N, handling rounding remainder) and itemized split (each `OrderItem` carries an optional `guest_assignment`). The even-split remainder problem — `$34.02 / 3` doesn't divide evenly to the cent — is solved by giving every guest the floor share and dumping the leftover cents on the last guest, which is exactly what `Bill.splitEvenly` above does. For itemized splits, unassigned items (shared appetizers) need a policy — the simplest is to bucket them under `"UNASSIGNED"` and let the app layer decide whether to divide that bucket evenly across the table:

```java
public Map<String, BigDecimal> splitByGuestWithSharedItems(List<String> allGuests) {
    Map<String, BigDecimal> perGuest = splitByGuest();
    BigDecimal shared = perGuest.remove("UNASSIGNED");
    if (shared != null && !allGuests.isEmpty()) {
        BigDecimal sharePerGuest = shared.divide(
            BigDecimal.valueOf(allGuests.size()), 2, RoundingMode.HALF_UP);
        for (String guest : allGuests) {
            perGuest.merge(guest, sharePerGuest, BigDecimal::add);
        }
    }
    return perGuest;
}
```

This keeps `Bill` focused on arithmetic while the caller decides the shared-item policy — a Strategy could formalize this (`SplitStrategy.EVEN`, `SplitStrategy.ITEMIZED`, `SplitStrategy.ITEMIZED_WITH_SHARED_SPLIT`) if the interviewer wants pluggability.

### 2. "How would you support real-time kitchen display updates (push notification pattern)?"

This is the Observer pattern already baked into `Order`: `KitchenDisplaySystem implements OrderObserver` and is registered via `order.addObserver(kds)`. `Order` never imports or references the KDS class directly — it just iterates its `List<OrderObserver>` on `placeItem` and `updateItemStatus`. This means adding a second consumer (e.g., a customer-facing order-status screen) requires zero changes to `Order`:

```java
public class KitchenDisplaySystem implements OrderObserver {
    private final Map<String, KitchenTicket> openTickets = new ConcurrentHashMap<>();

    @Override
    public void onNewOrder(Order order) {
        KitchenTicket ticket = openTickets.computeIfAbsent(order.getOrderId(),
            id -> new KitchenTicket(UUID.randomUUID().toString(), order.getOrderId(),
                                     order.getTable().getTableId()));
        ticket.refresh(order.getItems());
        render(ticket);
    }

    @Override
    public void onItemStatusChanged(OrderItem item) {
        System.out.println("[KDS] " + item.getMenuItem().getName() + " -> " + item.status());
    }

    private void render(KitchenTicket ticket) {
        System.out.println("[KDS] Ticket " + ticket.getTicketId() + " for table "
            + ticket.getTableId() + " has " + ticket.getItems().size() + " item(s)");
    }
}

public class CustomerStatusScreen implements OrderObserver {
    @Override
    public void onNewOrder(Order order) { /* show "order received" */ }
    @Override
    public void onItemStatusChanged(OrderItem item) { /* update customer-facing progress bar */ }
}
```

For true push (not just in-process callback) to a physical KDS device, wrap `render()` in a WebSocket broadcast or a message-queue publish — the Observer's `on*` methods become the trigger point for that I/O, keeping `Order` unaware of the transport.

### 3. "How would you handle out-of-stock menu items during an active order?"

`MenuItem.markUnavailable()` blocks *new* lines (`placeItem` throws immediately, per the Implementation section). The harder case is items already `PLACED`/`IN_KITCHEN` when the kitchen runs out mid-service. Add an explicit `OUT_OF_STOCK` terminal branch the kitchen can trigger instead of `READY`, decoupled from the happy-path chain so it doesn't corrupt the main state graph:

```java
public class OutOfStockState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.OUT_OF_STOCK; }
    public OrderItemState nextState(OrderItemStatus target) {
        throw new IllegalStateException("OUT_OF_STOCK is terminal, cannot transition to " + target);
    }
}

// InKitchenState gains an escape hatch alongside its happy-path transition:
public class InKitchenState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.IN_KITCHEN; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.READY) return new ReadyState();
        if (target == OrderItemStatus.OUT_OF_STOCK) return new OutOfStockState();
        throw new IllegalStateException("Cannot go from IN_KITCHEN to " + target);
    }
}
```

`Order.updateItemStatus(itemId, OUT_OF_STOCK)` then fires the usual `onItemStatusChanged` notification, letting the KDS flag it and the server layer prompt the guest for a substitution — typically implemented as canceling that `OrderItem` and calling `placeItem` again for the replacement, rather than trying to resurrect the original line.

---

## Interviewer Questions by Level

**Junior**: Define `OrderItem` and its fields. Explain the five statuses in the order-item lifecycle and why `SERVED` can't be reached directly from `PLACED`. Sketch how `Order`, `OrderItem`, and `MenuItem` relate.

**Mid-level**: Implement `placeOrder` and `updateOrderItemStatus` with correct locking. Explain why the State pattern is preferable to an enum with `if/else` transition checks scattered across the codebase. Implement even bill-splitting with correct rounding. Explain how `Reservation` overlap detection works.

**Senior**: Identify the single-lock-per-table bottleneck versus a single restaurant-wide lock and justify the tradeoff. Design the Observer wiring so a new consumer (analytics, customer app) requires zero changes to `Order`. Discuss the `OUT_OF_STOCK` escape hatch and why it's modeled as a separate terminal branch rather than reusing existing states. Discuss idempotency: what happens if the kitchen sends `IN_KITCHEN` twice for the same item?

---

## Common Interview Questions

- Q: Why model order-item status as a State pattern instead of an enum with validation in `Order`? A: Each state owns its own legal transitions, so adding a new status (e.g., `OUT_OF_STOCK`) means adding one class, not hunting through `Order` for every `if status == X` check. It also makes illegal transitions a compile-time-visible concern per state rather than a runtime `switch` default case.
- Q: Why does `Order` use Observer instead of the KDS polling the order list? A: Polling wastes cycles and adds latency; Observer pushes exactly the events consumers care about (`onNewOrder`, `onItemStatusChanged`) and lets multiple independent consumers (KDS, customer screen, analytics) subscribe without `Order` knowing they exist.
- Q: How do you prevent two reservations for the same table/time slot? A: A per-table lock guards `hasConflict()` + `reservations.add()` as one atomic block; without it, two threads can both pass the overlap check before either inserts, producing a double-booking.
- Q: How is bill-split rounding handled so the shares sum exactly to the total? A: Compute each guest's floor share, then assign the remainder (total minus the sum of floor shares) to the last guest, so cents are never lost or duplicated.
- Q: Can an `OrderItem` skip from `PLACED` straight to `PAID`? A: No — `PlacedState.nextState()` only accepts `IN_KITCHEN` as a legal target; any other target throws before the state is mutated.
- Q: Why is `Bill` separate from `Order` rather than fields on `Order`? A: `Order` is about what was requested and its kitchen progress; `Bill` is a derived snapshot for settlement. Keeping them separate lets you regenerate a `Bill` (e.g., after a correction) without mutating order history.
- Q: How would you test that two guests can't both grab the last available table for overlapping windows? A: Spin up N threads all calling `reserve()` with the same or overlapping windows on one table; assert exactly one succeeds and the rest raise `IllegalStateException`, and that `reservations` has no two overlapping entries afterward.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety invariants. No external deps — uses stdlib `java.util.concurrent` primitives only.

```java
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

// --- Minimal stubs to make the harness self-contained ---

enum TableStatus { AVAILABLE, RESERVED, OCCUPIED }
enum ReservationStatus { PENDING, CONFIRMED, CANCELLED, COMPLETED }
enum OrderItemStatus { PLACED, IN_KITCHEN, READY, SERVED, PAID, OUT_OF_STOCK }
enum MenuCategory { APPETIZER, MAIN, DESSERT, BEVERAGE }

class MenuItem {
    private final String itemId;
    private final String name;
    private final BigDecimal price;
    private final MenuCategory category;
    private volatile boolean available = true;

    public MenuItem(String itemId, String name, BigDecimal price, MenuCategory category) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.category = category;
    }

    public boolean isAvailable() { return available; }
    public void markUnavailable() { available = false; }
    public void markAvailable() { available = true; }
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public MenuCategory getCategory() { return category; }
}

interface OrderItemState {
    OrderItemStatus name();
    OrderItemState nextState(OrderItemStatus target);
}

class PlacedState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.PLACED; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.IN_KITCHEN) return new InKitchenState();
        throw new IllegalStateException("Cannot go from PLACED to " + target);
    }
}

class InKitchenState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.IN_KITCHEN; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.READY) return new ReadyState();
        if (target == OrderItemStatus.OUT_OF_STOCK) return new OutOfStockState();
        throw new IllegalStateException("Cannot go from IN_KITCHEN to " + target);
    }
}

class ReadyState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.READY; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.SERVED) return new ServedState();
        throw new IllegalStateException("Cannot go from READY to " + target);
    }
}

class ServedState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.SERVED; }
    public OrderItemState nextState(OrderItemStatus target) {
        if (target == OrderItemStatus.PAID) return new PaidState();
        throw new IllegalStateException("Cannot go from SERVED to " + target);
    }
}

class PaidState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.PAID; }
    public OrderItemState nextState(OrderItemStatus target) {
        throw new IllegalStateException("PAID is terminal, cannot transition to " + target);
    }
}

class OutOfStockState implements OrderItemState {
    public OrderItemStatus name() { return OrderItemStatus.OUT_OF_STOCK; }
    public OrderItemState nextState(OrderItemStatus target) {
        throw new IllegalStateException("OUT_OF_STOCK is terminal, cannot transition to " + target);
    }
}

class OrderItem {
    private final String orderItemId;
    private final MenuItem menuItem;
    private final int quantity;
    private volatile OrderItemState state = new PlacedState();
    private final Object lock = new Object();

    public OrderItem(String orderItemId, MenuItem menuItem, int quantity) {
        this.orderItemId = orderItemId;
        this.menuItem = menuItem;
        this.quantity = quantity;
    }

    public void transitionTo(OrderItemStatus target) {
        synchronized (lock) {
            state = state.nextState(target);
        }
    }

    public OrderItemStatus status() { return state.name(); }
    public String getOrderItemId() { return orderItemId; }
    public MenuItem getMenuItem() { return menuItem; }
    public int getQuantity() { return quantity; }
}

interface OrderObserver {
    void onNewOrder(Order order);
    void onItemStatusChanged(OrderItem item);
}

class Order {
    private final String orderId;
    private final List<OrderItem> items = new ArrayList<>();
    private final List<OrderObserver> observers = new ArrayList<>();
    private final Object lock = new Object();

    public Order(String orderId) { this.orderId = orderId; }

    public void addObserver(OrderObserver observer) { observers.add(observer); }

    public OrderItem placeItem(MenuItem menuItem, int quantity) {
        if (!menuItem.isAvailable()) {
            throw new IllegalStateException(menuItem.getName() + " is unavailable");
        }
        OrderItem item = new OrderItem(UUID.randomUUID().toString(), menuItem, quantity);
        synchronized (lock) {
            items.add(item);
        }
        for (OrderObserver o : observers) o.onNewOrder(this);
        return item;
    }

    public void updateItemStatus(String orderItemId, OrderItemStatus target) {
        OrderItem item = find(orderItemId);
        item.transitionTo(target);
        for (OrderObserver o : observers) o.onItemStatusChanged(item);
    }

    private OrderItem find(String orderItemId) {
        synchronized (lock) {
            for (OrderItem item : items) {
                if (item.getOrderItemId().equals(orderItemId)) return item;
            }
        }
        throw new NoSuchElementException("No such item: " + orderItemId);
    }

    public String getOrderId() { return orderId; }
    public List<OrderItem> getItems() { return items; }
}

class RestaurantTable {
    private final String tableId;
    private final int capacity;
    private TableStatus status = TableStatus.AVAILABLE;
    private final List<Reservation> reservations = new ArrayList<>();
    private final Object lock = new Object();

    public RestaurantTable(String tableId, int capacity) {
        this.tableId = tableId;
        this.capacity = capacity;
    }

    public boolean hasConflict(LocalDateTime start, LocalDateTime end) {
        for (Reservation r : reservations) {
            if (r.getStatus() == ReservationStatus.CANCELLED) continue;
            boolean disjoint = !end.isAfter(r.getStartTime()) || !start.isBefore(r.getEndTime());
            if (!disjoint) return true;
        }
        return false;
    }

    public Reservation reserve(String guestName, int partySize, LocalDateTime start, LocalDateTime end) {
        if (partySize > capacity) {
            throw new IllegalArgumentException("Party exceeds capacity");
        }
        synchronized (lock) {
            if (hasConflict(start, end)) {
                throw new IllegalStateException("Table " + tableId + " already booked for that window");
            }
            Reservation reservation = new Reservation(
                UUID.randomUUID().toString(), tableId, guestName, partySize, start, end);
            reservations.add(reservation);
            return reservation;
        }
    }

    public String getTableId() { return tableId; }
    public List<Reservation> getReservations() { return reservations; }
}

class Reservation {
    private final String reservationId;
    private final String tableId;
    private final String guestName;
    private final int partySize;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    public Reservation(String reservationId, String tableId, String guestName, int partySize,
                        LocalDateTime startTime, LocalDateTime endTime) {
        this.reservationId = reservationId;
        this.tableId = tableId;
        this.guestName = guestName;
        this.partySize = partySize;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getReservationId() { return reservationId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public ReservationStatus getStatus() { return status; }
}

class NoopKds implements OrderObserver {
    public void onNewOrder(Order order) { }
    public void onItemStatusChanged(OrderItem item) { }
}

// --- Tests ---

class RestaurantConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Concurrent reservation requests for the same table/time slot
    // 50 threads race to reserve the same table for the same window.
    // Exactly one must succeed; the rest must get IllegalStateException.
    // ─────────────────────────────────────────────────────────────
    static void testNoDoubleBookingSameSlot() throws InterruptedException {
        RestaurantTable table = new RestaurantTable("T1", 4);
        LocalDateTime start = LocalDateTime.of(2026, 8, 14, 19, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 14, 20, 0);

        int numThreads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> results = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < numThreads; i++) {
            final int idx = i;
            results.add(pool.submit(() -> {
                startGate.await();
                try {
                    table.reserve("Guest-" + idx, 2, start, end);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        startGate.countDown();

        int successCount = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) successCount++;
        }
        pool.shutdown();

        if (successCount != 1) {
            throw new AssertionError("Expected exactly 1 successful reservation, got " + successCount);
        }
        if (table.getReservations().size() != 1) {
            throw new AssertionError("Expected exactly 1 stored reservation, got " + table.getReservations().size());
        }
        System.out.println("PASS: testNoDoubleBookingSameSlot");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: Concurrent order-item status updates cannot skip states
    // Many threads simultaneously try to push the same item straight to
    // SERVED while it is still PLACED. None may succeed until the item
    // has legally passed through IN_KITCHEN and READY first.
    // ─────────────────────────────────────────────────────────────
    static void testNoInvalidStateSkip() throws InterruptedException {
        MenuItem burger = new MenuItem("m1", "Burger", new BigDecimal("12.00"), MenuCategory.MAIN);
        Order order = new Order("O1");
        order.addObserver(new NoopKds());
        OrderItem item = order.placeItem(burger, 1);

        int numThreads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> results = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < numThreads; i++) {
            results.add(pool.submit(() -> {
                startGate.await();
                try {
                    order.updateItemStatus(item.getOrderItemId(), OrderItemStatus.SERVED);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        startGate.countDown();

        int successCount = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) successCount++;
        }
        pool.shutdown();

        if (successCount != 0) {
            throw new AssertionError("Expected 0 threads to skip straight to SERVED, got " + successCount);
        }
        if (item.status() != OrderItemStatus.PLACED) {
            throw new AssertionError("Item status should remain PLACED, was " + item.status());
        }

        // Now walk it through legally and confirm exactly-once semantics under race
        order.updateItemStatus(item.getOrderItemId(), OrderItemStatus.IN_KITCHEN);
        order.updateItemStatus(item.getOrderItemId(), OrderItemStatus.READY);

        ExecutorService pool2 = Executors.newFixedThreadPool(numThreads);
        List<Future<Boolean>> servedResults = new ArrayList<>();
        CountDownLatch gate2 = new CountDownLatch(1);
        for (int i = 0; i < numThreads; i++) {
            servedResults.add(pool2.submit(() -> {
                gate2.await();
                try {
                    order.updateItemStatus(item.getOrderItemId(), OrderItemStatus.SERVED);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                }
            }));
        }
        gate2.countDown();
        int servedSuccessCount = 0;
        for (Future<Boolean> f : servedResults) {
            if (f.get()) servedSuccessCount++;
        }
        pool2.shutdown();

        if (servedSuccessCount != 1) {
            throw new AssertionError("Expected exactly 1 thread to win the READY->SERVED race, got " + servedSuccessCount);
        }
        if (item.status() != OrderItemStatus.SERVED) {
            throw new AssertionError("Item should be SERVED, was " + item.status());
        }
        System.out.println("PASS: testNoInvalidStateSkip");
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        testNoDoubleBookingSameSlot();
        testNoInvalidStateSkip();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `testNoDoubleBookingSameSlot`: The per-table lock makes `hasConflict()` + `reservations.add()` atomic. Without it, many threads could all pass the overlap check before any of them inserts, double-booking the table.
- `testNoInvalidStateSkip`: `OrderItem.transitionTo` locks the check-and-swap of the state object, so (a) no thread can jump straight from `PLACED` to `SERVED`, and (b) once the item is legally at `READY`, exactly one of many racing `SERVED` requests wins — the rest see a state object that has already moved on and correctly fail.

---

## Related

**Patterns applied here**

- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)
- [Observer Pattern](../../03-design-patterns/03-behavioral/observer-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design Hotel Management](../02-frequent-problems/11-design-hotel-management.md)
- [Design BookMyShow](../02-frequent-problems/06-design-bookmyshow.md)

Both reuse the time-window-reservation and status-lifecycle shape.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
