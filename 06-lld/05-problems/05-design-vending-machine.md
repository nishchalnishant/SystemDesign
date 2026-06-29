---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# Design Vending Machine

> **Difficulty**: Medium
> **Topics**: State Design Pattern, State Machine, Inventory Management
> **Extension**: Maintenance mode, exact-change enforcement, multiple payment types

---

## What Breaks Without This Design?

```python
from decimal import Decimal

class VendingMachine:
    def __init__(self):
        self._state = "IDLE"  # "IDLE", "SELECTED", "DISPENSING"
        self._selected_item = None  # str | None
        self._balance = Decimal("0")
        self._inventory = {}  # dict[str, int]
        self._prices = {}     # dict[str, Decimal]

    def insert_coin(self, amount):
        if self._state in ("IDLE", "SELECTED"):
            self._balance += amount
            if self._state == "SELECTED" and self._balance >= self._prices[self._selected_item]:
                self._state = "DISPENSING"
                self._dispense()
        else:
            print("Cannot insert coin now")

    def select_item(self, item):
        if self._state == "IDLE":
            if self._inventory.get(item, 0) > 0:
                self._selected_item = item
                self._state = "SELECTED"
        else:
            print("Select item only in IDLE state")

    def _dispense(self):
        self._inventory[self._selected_item] -= 1
        change = self._balance - self._prices[self._selected_item]
        self._balance = Decimal("0")
        self._selected_item = None
        self._state = "IDLE"
        print(f"Dispensed. Change: ${change}")
```

**Concrete failures**:
1. **O(S × A) branching**: Every method has `if (state.equals("X"))` branches. 4 states × 5 actions = 20 branches across 5 methods. Adding `MAINTENANCE` state requires editing every method.
2. **String-based state is not type-safe**: Typo `"DISPENCING"` compiles and fails silently at runtime. No compiler catches invalid state names.
3. **Illegal transitions are not structurally prevented**: Nothing stops calling `dispense()` directly while in `IDLE`. Only a runtime string check catches it — and only if the developer remembered to add it.
4. **State logic is scattered**: The behavior for `SELECTED` state is split between `insertCoin()`, `selectItem()`, and `dispense()`. To understand how `SELECTED` behaves, you must read all methods.
5. **No `BigDecimal` for currency**: `double balance += amount` introduces floating-point errors. `0.1 + 0.2 != 0.3` — critical for money.

---

## Derive the Class Structure

**Force 1 — State-dependent behavior must be grouped, not scattered**: Every action (insertCoin, selectItem, dispense, cancel) behaves differently per state. Extract `VendingMachineState` interface with all action methods. Each state class implements only its valid behavior; invalid actions print an error or throw. The machine holds `currentState` and delegates every call to it.

**Force 2 — State transitions must be explicit**: `IdleState.selectItem()` calls `machine.setState(machine.getSelectedState())`. The transition is visible in the state class that owns it — not buried in an `if/else` chain in the main class.

**Force 3 — Inventory and pricing belong together per slot**: A slot code `A1` maps to a product, a price, and a count. Extract `Slot` (or `Product`) with `name`, `price` (BigDecimal), `quantity`. `VendingMachine` holds `Map<String, Slot>`.

**Force 4 — `BigDecimal` for all money**: `double` is wrong for money. `balance` and `price` are `BigDecimal`. `insertCoin` accepts `BigDecimal amount`.

**Force 5 — Adding `MAINTENANCE` state must not touch existing states**: A new `MaintenanceState` implements `VendingMachineState` — all action methods reject requests with an appropriate message. Existing `IdleState`, `SelectedState`, `DispensingState` are untouched.

**Result** — the class split these forces produce:
```
God class → VendingMachine (holds currentState, inventory Map<slotCode, Slot>, balance)
          → VendingMachineState (interface: insertCoin, selectItem, dispense, cancel)
             → IdleState, ProductSelectedState, DispensingState, OutOfOrderState
          → Slot / Product (name, price: BigDecimal, quantity)
          → ChangeCalculator (computes exact change, handles edge cases)
```

---

## Opening Analogy

Stand in front of a real vending machine. Before you touch it, it is idle — buttons do nothing except item selection. After you pick an item, it waits for money. While it is dispensing, pressing any button is ignored. If it runs out of a product, it shows "sold out" for that slot. The machine behaves *completely differently* depending on which mode it is in. That is the core insight: the vending machine is not a bag of if/else branches — it is a State Machine. Each state owns its own behavior, and illegal transitions are rejected by the state itself, not by a central controller.

---

## Phase 1: Requirements

### Functional
- User selects a product by slot code (e.g., `A1`).
- User inserts coins or notes (represented as a dollar amount).
- Machine dispenses the product when `balance >= price`.
- Machine returns exact change.
- Admin can restock items and collect cash.
- States: `IDLE`, `PRODUCT_SELECTED`, `DISPENSING`, `OUT_OF_ORDER`.

### Non-Functional
- State transitions must be explicit and exhaustive — no silent fallthrough.
- Currency must use `BigDecimal` to avoid floating-point precision errors.
- Thread-safe for a single physical machine (one user at a time enforced by hardware, but code should handle `synchronized` for software emulation).
- Adding a new state (e.g., `MAINTENANCE`) must not require changes to existing state classes.

---

## Phase 2: Use Cases

### Actors
- **Customer** — selects item, inserts money, collects product and change.
- **Admin** — restocks inventory, empties cash box, triggers maintenance mode.
- **Machine (System)** — validates transitions, dispenses, returns change.

### UC1: Purchase Item (Happy Path)
1. Customer presses slot code `A1` (State: `IDLE` → `PRODUCT_SELECTED`).
2. System displays price.
3. Customer inserts money in one or more insertions.
4. When `balance >= price`, system auto-transitions to `DISPENSING`.
5. System deducts item count, calculates change, dispenses product, returns change.
6. System resets to `IDLE`.

### UC2: Cancel During Selection
1. Customer presses Cancel while in `PRODUCT_SELECTED`.
2. System refunds entire balance.
3. System returns to `IDLE`.

### UC3: Out-of-Stock Selection
1. Customer presses `A1`.
2. Inventory shows count = 0.
3. System displays "Item unavailable" and stays in `IDLE`.

### UC4: Admin Restock
1. Admin enters maintenance credentials.
2. System transitions to `OUT_OF_ORDER`.
3. Admin adds product and cash.
4. System transitions back to `IDLE`.

---

## Phase 3: Class Diagram

```
┌──────────────────────────────────────┐
│           VendingMachine             │   <<Context>>
│──────────────────────────────────────│
│ - currentState: State                │
│ - inventory: Inventory               │
│ - balance: BigDecimal                │
│ - selectedCode: String               │
│──────────────────────────────────────│
│ + selectItem(code: String): void     │
│ + insertMoney(amount: BigDecimal)    │
│ + pressDispense(): void              │
│ + pressCancel(): void                │
│ + setState(s: State): void           │
└───────────────────┬──────────────────┘
                    │ delegates to
          ┌─────────▼──────────┐
          │     State          │  <<interface>>
          │────────────────────│
          │ selectItem(code)   │
          │ insertMoney(amount)│
          │ dispense()         │
          │ cancel()           │
          └─────────┬──────────┘
     ┌──────────────┼──────────────────────┐
     ▼              ▼                      ▼
 IdleState  ProductSelectedState    DispensingState
                                    OutOfOrderState

┌─────────────────────────────┐
│          Inventory           │
│─────────────────────────────│
│ - products: Map<code,Product>│
│ - counts:   Map<code,Integer>│
│─────────────────────────────│
│ + isAvailable(code): boolean│
│ + getPrice(code): BigDecimal│
│ + deduct(code): void        │
│ + restock(code, n): void    │
└─────────────────────────────┘

┌─────────────────────────────┐
│           Product            │
│─────────────────────────────│
│ - name: String              │
│ - price: BigDecimal         │
└─────────────────────────────┘
```

**State transition diagram:**
```
IDLE ──selectItem(valid)──► PRODUCT_SELECTED
IDLE ──selectItem(OOS)───► IDLE (stays)

PRODUCT_SELECTED ──insertMoney(balance>=price)──► DISPENSING
PRODUCT_SELECTED ──cancel()──────────────────────► IDLE

DISPENSING ──dispense() completes──► IDLE

Any state ──admin trigger──► OUT_OF_ORDER
OUT_OF_ORDER ──admin done──► IDLE
```

---

## Phase 4: Design Patterns Applied

### 1. State Pattern — Core architecture
**Why:** Without it, every method on `VendingMachine` would be a chain of `if (state == IDLE) { ... } else if (state == DISPENSING) { ... }`. That is O(states × methods) branches, all in one class. State pattern moves each state's behavior into its own class. Adding a new state (e.g., `MaintenanceState`) requires creating one new file with no changes to existing states.

**How it works here:** `VendingMachine` is the Context. It holds a reference to the current `State` and delegates every user action (`selectItem`, `insertMoney`, `dispense`, `cancel`) to it. Each concrete state implements only the actions that make sense for it, and throws or prints an error for invalid ones.

### 2. Strategy Pattern — Pricing (extension)
**Why:** Price should vary by time of day, membership tier, or promotional period. A `PricingStrategy` interface with implementations like `FlatPricing`, `PeakHourPricing`, or `MembershipPricing` keeps this logic out of `Inventory` and makes it swappable at runtime.

---

## Phase 5: Key Python Implementation

```python
from abc import ABC, abstractmethod
from decimal import Decimal

# ── Currency-safe Product & Inventory ─────────────────────────────────────

class Product:
    def __init__(self, name, price):
        self.name = name
        self.price = price

class Inventory:
    def __init__(self):
        self._products = {}  # dict[str, Product]
        self._counts = {}    # dict[str, int]

    def add_product(self, code, product, count):
        self._products[code] = product
        self._counts[code] = count

    def is_available(self, code):
        return self._counts.get(code, 0) > 0

    def get_price(self, code):
        return self._products[code].price if code in self._products else Decimal("0")

    def get_name(self, code):
        return self._products[code].name if code in self._products else "Unknown"

    def deduct(self, code):
        if self.is_available(code):
            self._counts[code] -= 1

    def restock(self, code, count):
        self._counts[code] = self._counts.get(code, 0) + count

# ── State Interface ────────────────────────────────────────────────────────

class State(ABC):
    @abstractmethod
    def select_item(self, code): ...
    @abstractmethod
    def insert_money(self, amount): ...
    @abstractmethod
    def dispense(self): ...
    @abstractmethod
    def cancel(self): ...

# ── Context ────────────────────────────────────────────────────────────────

class VendingMachine:
    def __init__(self):
        self._inventory = Inventory()
        self._balance = Decimal("0")
        self._selected_code = None  # str | None

        self._idle_state       = IdleState(self)
        self._selected_state   = ProductSelectedState(self)
        self._dispensing_state = DispensingState(self)
        self._out_of_order_state = OutOfOrderState(self)
        self._current_state = self._idle_state

    # Delegate user actions to current state
    def select_item(self, code):        self._current_state.select_item(code)
    def insert_money(self, amount):     self._current_state.insert_money(amount)
    def press_dispense(self):           self._current_state.dispense()
    def press_cancel(self):             self._current_state.cancel()

    # State accessors (used by state classes)
    def set_state(self, state):           self._current_state = state
    def get_idle_state(self):             return self._idle_state
    def get_selected_state(self):         return self._selected_state
    def get_dispensing_state(self):       return self._dispensing_state
    def get_out_of_order_state(self):     return self._out_of_order_state
    def get_inventory(self):              return self._inventory

    def add_balance(self, amt):           self._balance += amt
    def get_balance(self):                return self._balance
    def set_selected_code(self, code):    self._selected_code = code
    def get_selected_code(self):          return self._selected_code

    def reset(self):
        self._balance = Decimal("0")
        self._selected_code = None

# ── Concrete States ────────────────────────────────────────────────────────

class IdleState(State):
    def __init__(self, vm):
        self._vm = vm

    def select_item(self, code):
        if not self._vm.get_inventory().is_available(code):
            print(f"[IDLE] Item {code} is out of stock.")
            return
        self._vm.set_selected_code(code)
        self._vm.set_state(self._vm.get_selected_state())
        print(f"[IDLE→SELECTED] Item {code} selected. Price: {self._vm.get_inventory().get_price(code)}")

    def insert_money(self, amount): print("[IDLE] Select an item first.")
    def dispense(self):             print("[IDLE] Select an item first.")
    def cancel(self):               print("[IDLE] Nothing to cancel.")

class ProductSelectedState(State):
    def __init__(self, vm):
        self._vm = vm

    def select_item(self, code): print("[SELECTED] Item already selected.")

    def insert_money(self, amount):
        self._vm.add_balance(amount)
        price = self._vm.get_inventory().get_price(self._vm.get_selected_code())
        print(f"[SELECTED] Inserted: {amount} | Balance: {self._vm.get_balance()}")
        if self._vm.get_balance() >= price:
            self._vm.set_state(self._vm.get_dispensing_state())
            self._vm.press_dispense()

    def dispense(self): print("[SELECTED] Insert more money.")

    def cancel(self):
        print(f"[SELECTED] Cancelled. Refunding: {self._vm.get_balance()}")
        self._vm.reset()
        self._vm.set_state(self._vm.get_idle_state())

class DispensingState(State):
    def __init__(self, vm):
        self._vm = vm

    def select_item(self, code):    print("[DISPENSING] Please wait...")
    def insert_money(self, amount): print("[DISPENSING] Please wait...")
    def cancel(self):               print("[DISPENSING] Cannot cancel now.")

    def dispense(self):
        code = self._vm.get_selected_code()
        price = self._vm.get_inventory().get_price(code)
        change = self._vm.get_balance() - price
        self._vm.get_inventory().deduct(code)
        print(f"[DISPENSING] Dispensed: {self._vm.get_inventory().get_name(code)}")
        if change > Decimal("0"):
            print(f"[DISPENSING] Change returned: {change}")
        self._vm.reset()
        self._vm.set_state(self._vm.get_idle_state())
        print("[DISPENSING→IDLE] Ready.")

class OutOfOrderState(State):
    def __init__(self, vm):
        self._vm = vm

    def select_item(self, code):    print("[OUT_OF_ORDER] Machine under maintenance.")
    def insert_money(self, amount): print("[OUT_OF_ORDER] Machine under maintenance.")
    def dispense(self):             print("[OUT_OF_ORDER] Machine under maintenance.")
    def cancel(self):               print("[OUT_OF_ORDER] Machine under maintenance.")

# ── Demo ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    vm = VendingMachine()
    vm.get_inventory().add_product("A1", Product("Coke",  Decimal("1.50")), 5)
    vm.get_inventory().add_product("B2", Product("Chips", Decimal("2.00")), 3)

    vm.select_item("A1")
    vm.insert_money(Decimal("1.00"))
    vm.insert_money(Decimal("1.00"))  # balance 2.00 >= 1.50 → auto-dispense
    # Output: Dispensed Coke, Change returned: 0.50
```

---

## Phase 6: Trade-offs and Extensions

### Trade-offs

| Decision | Choice | Alternative | Reason |
|---|---|---|---|
| Currency type | `BigDecimal` | `double` | Floating-point errors are unacceptable for money (0.1+0.2 ≠ 0.3) |
| Change return | Subtract from balance | Track coin denominations | Simplified; real machine needs greedy/DP coin-change algorithm |
| Thread safety | `synchronized` at method level | Full `ReentrantLock` | Single physical machine; method-level sync is sufficient |
| State transitions | Auto-trigger dispense when balance sufficient | Require explicit dispense button | More user-friendly; matches real vending machine UX |

### Extensions

**Exact-change enforcement:**
Track internal coin inventory (`Map<CoinDenomination, Integer>`). In `DispensingState.dispense()`, run a greedy algorithm to make change. If impossible, transition to `CannotMakeChangeState` and refund in full.

**Maintenance mode:**
```python
class MaintenanceState(State):
    def __init__(self, vm):
        self._vm = vm

    # Admin can call restock() and collect_cash()
    # All customer actions print "Machine under maintenance"
    # Admin calls done() → set_state(idle_state)

    def select_item(self, code):    print("[MAINTENANCE] Machine under maintenance.")
    def insert_money(self, amount): print("[MAINTENANCE] Machine under maintenance.")
    def dispense(self):             print("[MAINTENANCE] Machine under maintenance.")
    def cancel(self):               print("[MAINTENANCE] Machine under maintenance.")

    def done(self):
        self._vm.set_state(self._vm.get_idle_state())
```

**Multiple payment types:**
Extract `PaymentProcessor` interface with `CashProcessor`, `CardProcessor`, `QRCodeProcessor`. `insertMoney()` becomes `processPayment(PaymentRequest)`.

**Exact-change coin tracking (SDE-3 depth):**
```python
# In DispensingState.dispense():
# coin_box: dict[int, int]  denomination (cents) → count
from decimal import Decimal

def make_change(coin_box, balance, price):  # coin_box: dict[denomination_cents, count]
    change_cents = int((balance - price) * 100)
    # Greedy: 100¢, 25¢, 10¢, 5¢, 1¢
    for denom in sorted(coin_box, reverse=True):
        while change_cents >= denom and coin_box[denom] > 0:
            change_cents -= denom
            coin_box[denom] -= 1
    return change_cents == 0  # False → refund all, display error
```

---

## Interviewer Follow-Up Questions

- "What design pattern naturally models a vending machine?" → State pattern. The machine has distinct states: `IDLE`, `HAS_MONEY`, `DISPENSING`, `OUT_OF_STOCK`. Each state handles events (insert coin, select product, cancel) differently. In `IDLE`: insert coin → transition to `HAS_MONEY`. In `HAS_MONEY`: select product (if sufficient funds) → `DISPENSING`; insert more coins → stay in `HAS_MONEY` (accumulate); cancel → return money, back to `IDLE`. Each state is a class — no large switch statement.
- "How do you model the inventory?" → `Map<Product, Integer>` (product → quantity). On dispense: `inventory.put(product, inventory.get(product) - 1)`. Check availability before dispensing: `inventory.getOrDefault(product, 0) > 0`. If quantity hits 0 after dispensing: potentially transition to `OUT_OF_STOCK` state (or per-product unavailability — the machine may still sell other products). Restock: update the map, transition back to `IDLE` if previously out of stock.
- "How do you handle change calculation?" → Greedy algorithm with available coin denominations: `make_change(amount, available_coins)`. Sort coin denominations descending. For each denomination: use as many as possible without exceeding the amount. If you can't make exact change: return money and reject the sale. Available coins are a resource (like inventory) — decrement on use. Edge case: machine must track its own coin inventory, not just the inserted coins.
- "Two people insert coins simultaneously — is that realistic and how do you model it?" → Real vending machines have one coin slot — physically single-threaded. In software, if modeling a shared resource (like a network endpoint accepting concurrent requests): acquire a mutex before any state transition. The entire `insertCoin → selectProduct → dispense` sequence for one customer must be atomic. Simulate the physical constraint in software with a `ReentrantLock` on the machine.
