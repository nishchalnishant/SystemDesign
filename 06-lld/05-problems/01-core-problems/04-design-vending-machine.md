> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Vending Machine — the textbook example for demonstrating the State Design Pattern.
>
> **Key concepts:**
> - Core Entities: `VendingMachine` (context), `State` (interface), `Item`, `Inventory` (manages stock), `Coin`/`Note` (Enums).
> - States: `IdleState`, `HasMoneyState`, `DispensingState`, `OutOfStockState`.
> - State Pattern implementation: Each state implements methods like `insertCoin()`, `selectProduct()`, `dispense()`, and `cancel()`. If an action is invalid for a state (e.g., `dispense()` while in `IdleState`), it throws an exception.
> - Workflow: User inserts coin -> transitions to `HasMoneyState`. User selects product -> validates stock -> transitions to `DispensingState`. Dispenses item, returns change -> transitions back to `IdleState`.
>
> **Key takeaway:** DO NOT use if-else blocks for state management in a Vending Machine interview. The interviewer specifically wants to see a clean, polymorphic State Pattern implementation.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, vending-machine, state-pattern, strategy, command]
---
# Design a Vending Machine

> **Difficulty**: Medium  
> **Asked at**: Amazon, Google, Microsoft  
> **Key Patterns**: State pattern (machine states), Strategy (payment), Command (dispense)

---

## Understanding the Problem

Design a vending machine that accepts item selection and coin/card payments, dispenses items and change, manages inventory, and handles the full transaction lifecycle through distinct states.

---

## Clarifying Questions

**You**: "What payment methods should we support?"  
**Interviewer**: "Coins for now. Make it extensible to credit card."

**You**: "Should the machine give change?"  
**Interviewer**: "Yes. If the user inserts more than the item price, return the difference."

**You**: "What happens if the machine can't make exact change?"  
**Interviewer**: "Reject the transaction and return the inserted coins — don't dispense the item without giving correct change."

**You**: "Do we need to handle multiple items in one transaction?"  
**Interviewer**: "No — one item per transaction."

**You**: "What happens if a user selects a sold-out item?"  
**Interviewer**: "Display an error, stay in idle state — don't accept payment."

**You**: "Should we handle admin operations like restocking?"  
**Interviewer**: "That's a follow-up. Focus on the user-facing flow first."

---

## Final Requirements

**In scope:**
1. Item selection with inventory check
2. Coin insertion and accumulation within a transaction
3. Purchase completion — dispense item + return change
4. Transaction cancellation — return all inserted coins
5. State machine: IDLE → ITEM_SELECTED → PAYMENT → DISPENSING → IDLE
6. Inventory management (decrement on successful purchase)
7. Change calculation using coin denominations

**Out of scope:**
- Credit card payment (mentioned as extensibility)
- Multi-item transactions
- Admin restocking (follow-up only)
- Receipt printing

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| VendingMachine | Context in State pattern; delegates all actions to current state |
| VendingMachineState | Abstract state — defines allowed operations |
| IdleState | Accepts item selection only |
| ItemSelectedState | Accepts coin insertion or cancellation |
| PaymentState | Accepts more coins or triggers purchase if sufficient |
| DispensingState | Dispenses item and change, resets to idle |
| Item | Name, price, item code |
| Inventory | Maps item code to Item and quantity |
| CoinChanger | Computes change using available coin denominations |

VendingMachine holds a reference to the current state object. State transitions are triggered by user actions. Each state object has a reference back to the machine to read/write machine context (selected item, inserted amount).

---

## Class Design

### Item

```
class Item:
- code: str
- name: str
- price: float
```

### Inventory

```
class Inventory:
- items: dict[str, Item]
- quantities: dict[str, int]

+ get_item(code: str) -> Item | None
+ is_available(code: str) -> bool
+ decrement(code: str) -> None
+ restock(code: str, quantity: int) -> None
```

### CoinChanger

```
class CoinChanger:
- available_coins: dict[int, int]  # denomination(cents) -> count
- coin_denominations: list[int]    # sorted descending: [100, 50, 25, 10, 5, 1]

+ can_make_change(amount_cents: int) -> bool
+ make_change(amount_cents: int) -> dict[int, int]  # denomination -> count returned
+ add_coins(coins: dict[int, int]) -> None
```

### VendingMachineState (abstract)

```
class VendingMachineState:           # abstract
+ select_item(code: str) -> None
+ insert_coin(amount_cents: int) -> None
+ complete_purchase() -> None
+ cancel() -> None
```

### Concrete States

```
class IdleState(VendingMachineState):
- machine: VendingMachine
# Only select_item is valid; all others raise InvalidOperationError

class ItemSelectedState(VendingMachineState):
- machine: VendingMachine
# insert_coin and cancel are valid; select_item raises error

class PaymentState(VendingMachineState):
- machine: VendingMachine
# insert_coin (accumulate), complete_purchase if sufficient, cancel

class DispensingState(VendingMachineState):
- machine: VendingMachine
# Automatic — dispenses and transitions back to idle
```

### VendingMachine (Context)

```
class VendingMachine:
- inventory: Inventory
- coin_changer: CoinChanger
- state: VendingMachineState
- selected_item: Item | None
- inserted_amount: int             # in cents

+ select_item(code: str) -> None
+ insert_coin(amount_cents: int) -> None
+ complete_purchase() -> None
+ cancel() -> None
+ set_state(state: VendingMachineState) -> None
```

---

## Implementation

### Core Method: select_item (in IdleState)

**Core logic:**
1. Validate item code exists in inventory
2. Validate item is in stock
3. Store selected item on machine context
4. Transition machine to ItemSelectedState

**Edge cases:**
- Invalid item code — raise error, stay in IDLE
- Item out of stock — raise error, stay in IDLE

```python
class IdleState(VendingMachineState):
    def select_item(self, code: str) -> None:
        item = self.machine.inventory.get_item(code)
        if item is None:
            raise ValueError(f"Unknown item code: {code}")
        if not self.machine.inventory.is_available(code):
            raise OutOfStockError(f"{item.name} is out of stock")
        self.machine.selected_item = item
        self.machine.set_state(ItemSelectedState(self.machine))

    def insert_coin(self, amount_cents: int) -> None:
        raise InvalidOperationError("Select an item first")

    def complete_purchase(self) -> None:
        raise InvalidOperationError("Select an item first")

    def cancel(self) -> None:
        pass  # Nothing to cancel in idle
```

### Core Method: insert_coin (in PaymentState)

**Core logic:**
1. Accumulate inserted_amount
2. Add coin to machine's CoinChanger pool
3. If inserted_amount >= item price, automatically attempt purchase

```python
class PaymentState(VendingMachineState):
    def insert_coin(self, amount_cents: int) -> None:
        self.machine.inserted_amount += amount_cents
        self.machine.coin_changer.add_coins({amount_cents: 1})

        if self.machine.inserted_amount >= self.machine.selected_item.price_cents:
            self.complete_purchase()

    def complete_purchase(self) -> None:
        item = self.machine.selected_item
        change = self.machine.inserted_amount - item.price_cents

        if change > 0 and not self.machine.coin_changer.can_make_change(change):
            # Cannot give change — refund all coins
            returned = self.machine.coin_changer.make_change(
                self.machine.inserted_amount
            )
            self.machine.inserted_amount = 0
            self.machine.selected_item = None
            self.machine.set_state(IdleState(self.machine))
            raise InsufficientChangeError(f"Cannot make change of {change} cents. Coins returned.")

        self.machine.set_state(DispensingState(self.machine))
        self.machine.state.dispense()

    def cancel(self) -> None:
        returned = self.machine.coin_changer.make_change(self.machine.inserted_amount)
        self.machine.inserted_amount = 0
        self.machine.selected_item = None
        self.machine.set_state(IdleState(self.machine))
```

### Core Method: CoinChanger.make_change

**Core logic:**
Uses a greedy algorithm with available denominations (works correctly when denominations are standard currency coins).

```python
def make_change(self, amount_cents: int) -> dict[int, int]:
    change = {}
    remaining = amount_cents
    for denom in self.coin_denominations:  # sorted descending
        count = min(remaining // denom, self.available_coins.get(denom, 0))
        if count > 0:
            change[denom] = count
            remaining -= count * denom
            self.available_coins[denom] -= count
    if remaining != 0:
        raise InsufficientChangeError(f"Cannot make exact change for {amount_cents} cents")
    return change

def can_make_change(self, amount_cents: int) -> bool:
    try:
        # Simulate without modifying state
        temp = dict(self.available_coins)
        remaining = amount_cents
        for denom in self.coin_denominations:
            count = min(remaining // denom, temp.get(denom, 0))
            remaining -= count * denom
        return remaining == 0
    except Exception:
        return False
```

### DispensingState.dispense

```python
class DispensingState(VendingMachineState):
    def dispense(self) -> None:
        item = self.machine.selected_item
        change = self.machine.inserted_amount - item.price_cents

        # Dispense item
        self.machine.inventory.decrement(item.code)
        print(f"Dispensing: {item.name}")

        # Return change
        if change > 0:
            returned = self.machine.coin_changer.make_change(change)
            print(f"Returning change: {returned}")

        # Reset machine
        self.machine.inserted_amount = 0
        self.machine.selected_item = None
        self.machine.set_state(IdleState(self.machine))
```

---

## Verification

**Scenario**: User selects "Cola" ($1.50), inserts $1.00 then $1.00, receives Cola and $0.50 change.

1. `select_item("COLA")` in IdleState → Cola exists, qty=5 → `selected_item=Cola`, → ItemSelectedState
2. `insert_coin(100)` in ItemSelectedState → transitions to PaymentState, then calls `insert_coin(100)`: `inserted_amount=100`, 100 < 150, stay in PaymentState
3. `insert_coin(100)` in PaymentState: `inserted_amount=200`, 200 >= 150 → `complete_purchase()`
4. `change = 200 - 150 = 50 cents`. `can_make_change(50)` checks coin pool → True (has quarters)
5. Transition to DispensingState → `dispense()`
6. `inventory.decrement("COLA")` → qty=4
7. `make_change(50)` → `{25: 2}` returned (two quarters)
8. `inserted_amount=0`, `selected_item=None` → IdleState

---

## Deep Dive & Extensibility

### 1. "Why use the State pattern instead of if/else chains?"

Without State pattern:

```python
def insert_coin(self, amount):
    if self.state == "IDLE":
        raise InvalidOperationError(...)
    elif self.state == "ITEM_SELECTED":
        self.transition_to("PAYMENT")
        self.inserted_amount += amount
    elif self.state == "PAYMENT":
        self.inserted_amount += amount
        if self.inserted_amount >= self.item.price:
            self.complete_purchase()
    elif self.state == "DISPENSING":
        raise InvalidOperationError(...)
```

Every method has a growing if/else block. Adding a new state means modifying every action method. With State pattern, adding a new state (e.g., `MaintenanceState`) means adding one class — zero changes to existing state classes. Each state class is independently testable and has a single clear responsibility.

### 2. "How would you add credit card payment?"

Credit card payment is a different `PaymentMethod` strategy:

```python
class PaymentMethod:                    # abstract
+ initiate(amount: float) -> str        # returns transaction_id
+ confirm(transaction_id: str) -> bool
+ refund(transaction_id: str) -> bool

class CoinPayment(PaymentMethod):
    def initiate(self, amount):
        # accumulate coins — return None (no external transaction)
        ...

class CardPayment(PaymentMethod):
    def __init__(self, payment_gateway):
        self.gateway = payment_gateway

    def initiate(self, amount):
        return self.gateway.charge(amount)

    def confirm(self, transaction_id):
        return self.gateway.verify(transaction_id)
```

VendingMachine accepts a `PaymentMethod` at construction or per-session. The PaymentState uses whichever method is set. The state machine logic (transitions, inventory update) stays identical — only the payment mechanism changes.

### 3. "How do you handle the case where the machine can't make exact change?"

Two options depending on business rules:

**Option A (strict)**: Check `can_make_change` before accepting the transaction. On `complete_purchase`, if change can't be made, refund all inserted coins and transition back to IDLE. User keeps their coins, item stays in inventory.

**Option B (lenient)**: Show a "Exact change only" warning before purchase. Accept exact-payment amounts only when coins are low.

The implementation above uses Option A. The `can_make_change` simulation runs without modifying the coin pool — it creates a temporary copy of `available_coins` and tests greedily. This is O(D) where D is the number of denomination types (constant in practice).

### 4. "How would you add an admin restocking interface?"

Add an `AdminPanel` class that operates on `Inventory` and `CoinChanger` directly, bypassing the state machine:

```python
class AdminPanel:
    def __init__(self, machine: VendingMachine):
        self.machine = machine

    def restock_item(self, code: str, quantity: int) -> None:
        if self.machine.state.__class__ != IdleState:
            raise InvalidOperationError("Cannot restock during a transaction")
        self.machine.inventory.restock(code, quantity)

    def add_coins(self, coins: dict[int, int]) -> None:
        self.machine.coin_changer.add_coins(coins)

    def add_item_type(self, item: Item, quantity: int) -> None:
        self.machine.inventory.add_item(item, quantity)

    def collect_revenue(self) -> float:
        revenue = self.machine.revenue
        self.machine.revenue = 0
        return revenue
```

The machine tracks `revenue: float` incrementing on each successful purchase. Admin can only restock when the machine is in IdleState to avoid race conditions mid-transaction.

---

## Interviewer Questions by Level

**Junior**: Draw the state transition diagram. Identify what actions are valid in each state. Explain why the machine has a "selected_item" field.

**Mid-level**: Implement `select_item` and `insert_coin` in their respective state classes. Implement `CoinChanger.make_change` using greedy algorithm. Explain why the State pattern makes testing easier.

**Senior**: Explain why `can_make_change` must simulate without modifying coin state. Design the credit card payment extension. Discuss thread safety — what happens if two users interact with the machine concurrently and how to prevent it.

---

## Common Interview Questions

- Q: What happens if power cuts mid-transaction? A: Without persistence, all in-flight state is lost. Inserted coins can't be tracked. In a real system, store transaction state to non-volatile memory (EEPROM or a small DB). On restart, check for incomplete transactions and dispense coins or refund.
- Q: Draw the state transition diagram. A: IDLE →(select_item)→ ITEM_SELECTED →(insert_coin)→ PAYMENT →(sufficient funds)→ DISPENSING →(auto)→ IDLE. Cancel from any state returns to IDLE. Out-of-stock in IDLE stays in IDLE.
- Q: Why does ItemSelectedState transition to PaymentState on first coin insert? A: To model that payment has begun. Once coins are inserted, different rules apply (cancel must refund). Separating "item chosen, no money yet" from "money inserted" makes each state's valid operations clearer.
- Q: How do you test each state in isolation? A: Construct a VendingMachine with its state manually set to the target state. Mock the machine context. Call the state's methods and assert transitions and outputs. No need to drive through the full flow each time.
- Q: What if the user inserts the exact amount — what change is returned? A: `change = inserted - price = 0`. Skip the `make_change` call entirely. Dispense the item. No coins returned.
- Q: How is partial payment handled? A: Payment accumulates in `inserted_amount`. Each `insert_coin` call adds to it. The state stays in PaymentState until `inserted_amount >= item.price_cents`. This allows multiple coin insertions before reaching the required amount.

---

## Concurrency Test Harness

A vending machine is a single physical device — transactions must be strictly sequential. These tests verify that concurrent access is correctly serialized and invariants hold under race conditions.

```python
import threading
import time
from enum import Enum, auto
from dataclasses import dataclass, field

# ── Minimal stub implementation (self-contained) ──

class VendingException(Exception):
    pass

@dataclass
class Item:
    code: str
    name: str
    price_cents: int

class Inventory:
    def __init__(self):
        self._items = {}
        self._quantities = {}
        self._lock = threading.Lock()

    def add_item(self, item, qty):
        with self._lock:
            self._items[item.code] = item
            self._quantities[item.code] = qty

    def get_item(self, code):
        with self._lock:
            if code not in self._items:
                raise VendingException(f"Unknown code: {code}")
            if self._quantities[code] == 0:
                raise VendingException(f"Out of stock: {code}")
            return self._items[code]

    def decrement(self, code):
        with self._lock:
            if self._quantities.get(code, 0) == 0:
                raise VendingException(f"Out of stock: {code}")
            self._quantities[code] -= 1

    def quantity(self, code):
        with self._lock:
            return self._quantities.get(code, 0)


class VendingMachine:
    """Single-transaction serialization via a global reentrant lock."""

    def __init__(self):
        self.inventory = Inventory()
        self._transaction_lock = threading.Lock()
        self._inserted_cents = 0
        self._selected_code = None
        self._revenue = 0

    def select_item(self, code):
        with self._transaction_lock:
            item = self.inventory.get_item(code)
            self._selected_code = code
            self._inserted_cents = 0
            return item

    def insert_coin(self, cents):
        with self._transaction_lock:
            if self._selected_code is None:
                raise VendingException("No item selected")
            self._inserted_cents += cents

    def dispense(self):
        with self._transaction_lock:
            if self._selected_code is None:
                raise VendingException("No item selected")
            item = self.inventory.get_item(self._selected_code)
            if self._inserted_cents < item.price_cents:
                raise VendingException(
                    f"Insufficient: need {item.price_cents}, have {self._inserted_cents}"
                )
            change = self._inserted_cents - item.price_cents
            self.inventory.decrement(self._selected_code)
            self._revenue += item.price_cents
            code = self._selected_code
            self._selected_code = None
            self._inserted_cents = 0
            return change

    def cancel(self):
        with self._transaction_lock:
            refund = self._inserted_cents
            self._selected_code = None
            self._inserted_cents = 0
            return refund


# ─────────────────────────────────────────────────────────────
# TEST 1: Inventory never goes negative under concurrent purchases
# 100 threads all try to buy the same item (stock=10).
# Exactly 10 must succeed; 90 must get VendingException.
# ─────────────────────────────────────────────────────────────
def test_inventory_never_negative():
    vm = VendingMachine()
    cola = Item("A1", "Cola", 150)
    vm.inventory.add_item(cola, 10)

    successes = []
    failures = []
    lock = threading.Lock()

    def buy():
        try:
            vm.select_item("A1")
            vm.insert_coin(200)
            change = vm.dispense()
            with lock:
                successes.append(change)
        except VendingException as e:
            with lock:
                failures.append(str(e))

    threads = [threading.Thread(target=buy) for _ in range(100)]
    for t in threads: t.start()
    for t in threads: t.join()

    # Exactly 10 purchases; 90 stock-out failures
    # Note: serialization means each thread sees a consistent state
    final_qty = vm.inventory.quantity("A1")
    sold = 10 - final_qty
    assert final_qty >= 0, f"Inventory went negative: {final_qty}"
    assert len(successes) == sold, f"Success count {len(successes)} != sold {sold}"
    print(f"PASS: test_inventory_never_negative ({sold} sold, {final_qty} remaining)")


# ─────────────────────────────────────────────────────────────
# TEST 2: No revenue corruption under concurrent transactions
# N threads each complete a full transaction.
# Total revenue must equal N × item price.
# ─────────────────────────────────────────────────────────────
def test_revenue_consistency():
    vm = VendingMachine()
    water = Item("B1", "Water", 100)
    vm.inventory.add_item(water, 50)

    success_count = 0
    lock = threading.Lock()

    def buy():
        nonlocal success_count
        try:
            vm.select_item("B1")
            vm.insert_coin(100)
            vm.dispense()
            with lock:
                success_count += 1
        except VendingException:
            pass

    threads = [threading.Thread(target=buy) for _ in range(50)]
    for t in threads: t.start()
    for t in threads: t.join()

    expected_revenue = success_count * 100
    assert vm._revenue == expected_revenue, \
        f"Revenue mismatch: expected {expected_revenue}, got {vm._revenue}"
    print(f"PASS: test_revenue_consistency ({success_count} purchases, revenue={vm._revenue})")


# ─────────────────────────────────────────────────────────────
# TEST 3: Cancel returns inserted amount, leaves machine in clean state
# Thread A inserts coins and then cancels.
# Thread B then completes a transaction.
# Thread B must see a clean machine state (not A's inserted_cents).
# ─────────────────────────────────────────────────────────────
def test_cancel_resets_state():
    vm = VendingMachine()
    snack = Item("C1", "Snack", 75)
    vm.inventory.add_item(snack, 5)

    barrier = threading.Barrier(2)
    results = {}

    def thread_a():
        vm.select_item("C1")
        vm.insert_coin(200)
        refund = vm.cancel()
        results["refund"] = refund
        barrier.wait()  # signal B to proceed

    def thread_b():
        barrier.wait()  # wait for A to cancel
        vm.select_item("C1")
        vm.insert_coin(75)
        change = vm.dispense()
        results["change"] = change

    ta = threading.Thread(target=thread_a)
    tb = threading.Thread(target=thread_b)
    ta.start(); tb.start()
    ta.join(); tb.join()

    assert results["refund"] == 200, f"Expected refund 200, got {results['refund']}"
    assert results["change"] == 0, f"Expected change 0, got {results['change']}"
    assert vm.inventory.quantity("C1") == 4, "Inventory not decremented correctly"
    print("PASS: test_cancel_resets_state")


if __name__ == "__main__":
    test_inventory_never_negative()
    test_revenue_consistency()
    test_cancel_resets_state()
    print("All concurrency tests passed.")
```

**What each test verifies:**
- `test_inventory_never_negative`: The `_transaction_lock` serializes select→insert→dispense triples. Without it, two threads can both pass the "quantity > 0" check and both decrement, driving stock negative.
- `test_revenue_consistency`: Revenue is incremented inside the same lock as inventory decrement; they update atomically per transaction with no partial writes visible between threads.
- `test_cancel_resets_state`: A barrier coordinates A's cancel before B's transaction to confirm that cancel fully resets `_selected_code` and `_inserted_cents`, leaving the machine in `IdleState` for the next user.

---

## Related

**Patterns applied here**

- [Command Pattern](../../03-design-patterns/03-behavioral/command-pattern.md)
- [State Pattern](../../03-design-patterns/03-behavioral/state-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Open/Closed](../../02-solid-principles/02-open-closed.md)

**Practice next**

- [Design an ATM](../02-frequent-problems/12-design-atm.md)
- [Design Parking Lot](01-design-parking-lot.md)

The ATM is the same state machine with money and auth.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
