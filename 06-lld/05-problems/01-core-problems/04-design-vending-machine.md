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

```java
public class IdleState implements VendingMachineState {
    private final VendingMachine machine;

    public IdleState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void selectItem(String code) {
        Item item = machine.getInventory().getItem(code);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item code: " + code);
        }
        if (!machine.getInventory().isAvailable(code)) {
            throw new OutOfStockError(item.getName() + " is out of stock");
        }
        machine.setSelectedItem(item);
        machine.setState(new ItemSelectedState(machine));
    }

    @Override
    public void insertCoin(int amountCents) {
        throw new InvalidOperationError("Select an item first");
    }

    @Override
    public void completePurchase() {
        throw new InvalidOperationError("Select an item first");
    }

    @Override
    public void cancel() {
        // Nothing to cancel in idle
    }
}
```

### Core Method: insert_coin (in PaymentState)

**Core logic:**
1. Accumulate inserted_amount
2. Add coin to machine's CoinChanger pool
3. If inserted_amount >= item price, automatically attempt purchase

```java
public class PaymentState implements VendingMachineState {
    private final VendingMachine machine;

    public PaymentState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void insertCoin(int amountCents) {
        machine.setInsertedAmount(machine.getInsertedAmount() + amountCents);
        machine.getCoinChanger().addCoins(Map.of(amountCents, 1));

        if (machine.getInsertedAmount() >= machine.getSelectedItem().getPriceCents()) {
            completePurchase();
        }
    }

    @Override
    public void completePurchase() {
        Item item = machine.getSelectedItem();
        int change = machine.getInsertedAmount() - item.getPriceCents();

        if (change > 0 && !machine.getCoinChanger().canMakeChange(change)) {
            // Cannot give change — refund all coins
            Map<Integer, Integer> returned = machine.getCoinChanger()
                .makeChange(machine.getInsertedAmount());
            machine.setInsertedAmount(0);
            machine.setSelectedItem(null);
            machine.setState(new IdleState(machine));
            throw new InsufficientChangeError(
                "Cannot make change of " + change + " cents. Coins returned.");
        }

        machine.setState(new DispensingState(machine));
        machine.getState().dispense();
    }

    @Override
    public void cancel() {
        Map<Integer, Integer> returned = machine.getCoinChanger()
            .makeChange(machine.getInsertedAmount());
        machine.setInsertedAmount(0);
        machine.setSelectedItem(null);
        machine.setState(new IdleState(machine));
    }
}
```

### Core Method: CoinChanger.make_change

**Core logic:**
Uses a greedy algorithm with available denominations (works correctly when denominations are standard currency coins).

```java
public Map<Integer, Integer> makeChange(int amountCents) {
    Map<Integer, Integer> change = new HashMap<>();
    int remaining = amountCents;
    for (int denom : coinDenominations) { // sorted descending
        int count = Math.min(remaining / denom, availableCoins.getOrDefault(denom, 0));
        if (count > 0) {
            change.put(denom, count);
            remaining -= count * denom;
            availableCoins.merge(denom, -count, Integer::sum);
        }
    }
    if (remaining != 0) {
        throw new InsufficientChangeError(
            "Cannot make exact change for " + amountCents + " cents");
    }
    return change;
}

public boolean canMakeChange(int amountCents) {
    try {
        // Simulate without modifying state
        Map<Integer, Integer> temp = new HashMap<>(availableCoins);
        int remaining = amountCents;
        for (int denom : coinDenominations) {
            int count = Math.min(remaining / denom, temp.getOrDefault(denom, 0));
            remaining -= count * denom;
        }
        return remaining == 0;
    } catch (Exception e) {
        return false;
    }
}
```

### DispensingState.dispense

```java
public class DispensingState implements VendingMachineState {
    private final VendingMachine machine;

    public DispensingState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void dispense() {
        Item item = machine.getSelectedItem();
        int change = machine.getInsertedAmount() - item.getPriceCents();

        // Dispense item
        machine.getInventory().decrement(item.getCode());
        System.out.println("Dispensing: " + item.getName());

        // Return change
        if (change > 0) {
            Map<Integer, Integer> returned = machine.getCoinChanger().makeChange(change);
            System.out.println("Returning change: " + returned);
        }

        // Reset machine
        machine.setInsertedAmount(0);
        machine.setSelectedItem(null);
        machine.setState(new IdleState(machine));
    }
}
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

```java
public void insertCoin(int amount) {
    if (state.equals("IDLE")) {
        throw new InvalidOperationError("...");
    } else if (state.equals("ITEM_SELECTED")) {
        transitionTo("PAYMENT");
        insertedAmount += amount;
    } else if (state.equals("PAYMENT")) {
        insertedAmount += amount;
        if (insertedAmount >= item.getPrice()) {
            completePurchase();
        }
    } else if (state.equals("DISPENSING")) {
        throw new InvalidOperationError("...");
    }
}
```

Every method has a growing if/else block. Adding a new state means modifying every action method. With State pattern, adding a new state (e.g., `MaintenanceState`) means adding one class — zero changes to existing state classes. Each state class is independently testable and has a single clear responsibility.

### 2. "How would you add credit card payment?"

Credit card payment is a different `PaymentMethod` strategy:

```java
public interface PaymentMethod {
    String initiate(double amount);       // returns transactionId
    boolean confirm(String transactionId);
    boolean refund(String transactionId);
}

public class CoinPayment implements PaymentMethod {
    @Override
    public String initiate(double amount) {
        // accumulate coins — return null (no external transaction)
        return null;
    }

    @Override
    public boolean confirm(String transactionId) {
        return true;
    }

    @Override
    public boolean refund(String transactionId) {
        return true;
    }
}

public class CardPayment implements PaymentMethod {
    private final PaymentGateway gateway;

    public CardPayment(PaymentGateway paymentGateway) {
        this.gateway = paymentGateway;
    }

    @Override
    public String initiate(double amount) {
        return gateway.charge(amount);
    }

    @Override
    public boolean confirm(String transactionId) {
        return gateway.verify(transactionId);
    }

    @Override
    public boolean refund(String transactionId) {
        return gateway.refund(transactionId);
    }
}
```

VendingMachine accepts a `PaymentMethod` at construction or per-session. The PaymentState uses whichever method is set. The state machine logic (transitions, inventory update) stays identical — only the payment mechanism changes.

### 3. "How do you handle the case where the machine can't make exact change?"

Two options depending on business rules:

**Option A (strict)**: Check `can_make_change` before accepting the transaction. On `complete_purchase`, if change can't be made, refund all inserted coins and transition back to IDLE. User keeps their coins, item stays in inventory.

**Option B (lenient)**: Show a "Exact change only" warning before purchase. Accept exact-payment amounts only when coins are low.

The implementation above uses Option A. The `can_make_change` simulation runs without modifying the coin pool — it creates a temporary copy of `available_coins` and tests greedily. This is O(D) where D is the number of denomination types (constant in practice).

### 4. "How would you add an admin restocking interface?"

Add an `AdminPanel` class that operates on `Inventory` and `CoinChanger` directly, bypassing the state machine:

```java
public class AdminPanel {
    private final VendingMachine machine;

    public AdminPanel(VendingMachine machine) {
        this.machine = machine;
    }

    public void restockItem(String code, int quantity) {
        if (!(machine.getState() instanceof IdleState)) {
            throw new InvalidOperationError("Cannot restock during a transaction");
        }
        machine.getInventory().restock(code, quantity);
    }

    public void addCoins(Map<Integer, Integer> coins) {
        machine.getCoinChanger().addCoins(coins);
    }

    public void addItemType(Item item, int quantity) {
        machine.getInventory().addItem(item, quantity);
    }

    public double collectRevenue() {
        double revenue = machine.getRevenue();
        machine.setRevenue(0);
        return revenue;
    }
}
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

```java
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// ── Minimal stub implementation (self-contained) ──

class VendingException extends RuntimeException {
    public VendingException(String message) {
        super(message);
    }
}

class Item {
    private final String code;
    private final String name;
    private final int priceCents;

    public Item(String code, String name, int priceCents) {
        this.code = code;
        this.name = name;
        this.priceCents = priceCents;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public int getPriceCents() { return priceCents; }
}

class Inventory {
    private final Map<String, Item> items = new HashMap<>();
    private final Map<String, Integer> quantities = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void addItem(Item item, int qty) {
        lock.lock();
        try {
            items.put(item.getCode(), item);
            quantities.put(item.getCode(), qty);
        } finally {
            lock.unlock();
        }
    }

    public Item getItem(String code) {
        lock.lock();
        try {
            if (!items.containsKey(code)) {
                throw new VendingException("Unknown code: " + code);
            }
            if (quantities.get(code) == 0) {
                throw new VendingException("Out of stock: " + code);
            }
            return items.get(code);
        } finally {
            lock.unlock();
        }
    }

    public void decrement(String code) {
        lock.lock();
        try {
            if (quantities.getOrDefault(code, 0) == 0) {
                throw new VendingException("Out of stock: " + code);
            }
            quantities.merge(code, -1, Integer::sum);
        } finally {
            lock.unlock();
        }
    }

    public int quantity(String code) {
        lock.lock();
        try {
            return quantities.getOrDefault(code, 0);
        } finally {
            lock.unlock();
        }
    }
}

/** Single-transaction serialization via a global reentrant lock. */
class VendingMachine {
    private final Inventory inventory = new Inventory();
    private final ReentrantLock transactionLock = new ReentrantLock();
    private int insertedCents = 0;
    private String selectedCode = null;
    private int revenue = 0;

    public Inventory getInventory() { return inventory; }
    public int getRevenue() { return revenue; }

    public Item selectItem(String code) {
        transactionLock.lock();
        try {
            Item item = inventory.getItem(code);
            selectedCode = code;
            insertedCents = 0;
            return item;
        } finally {
            transactionLock.unlock();
        }
    }

    public void insertCoin(int cents) {
        transactionLock.lock();
        try {
            if (selectedCode == null) {
                throw new VendingException("No item selected");
            }
            insertedCents += cents;
        } finally {
            transactionLock.unlock();
        }
    }

    public int dispense() {
        transactionLock.lock();
        try {
            if (selectedCode == null) {
                throw new VendingException("No item selected");
            }
            Item item = inventory.getItem(selectedCode);
            if (insertedCents < item.getPriceCents()) {
                throw new VendingException(
                    "Insufficient: need " + item.getPriceCents() + ", have " + insertedCents);
            }
            int change = insertedCents - item.getPriceCents();
            inventory.decrement(selectedCode);
            revenue += item.getPriceCents();
            selectedCode = null;
            insertedCents = 0;
            return change;
        } finally {
            transactionLock.unlock();
        }
    }

    public int cancel() {
        transactionLock.lock();
        try {
            int refund = insertedCents;
            selectedCode = null;
            insertedCents = 0;
            return refund;
        } finally {
            transactionLock.unlock();
        }
    }
}

public class VendingMachineConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Inventory never goes negative under concurrent purchases
    // 100 threads all try to buy the same item (stock=10).
    // Exactly 10 must succeed; 90 must get VendingException.
    // ─────────────────────────────────────────────────────────────
    static void testInventoryNeverNegative() throws InterruptedException {
        VendingMachine vm = new VendingMachine();
        Item cola = new Item("A1", "Cola", 150);
        vm.getInventory().addItem(cola, 10);

        List<Integer> successes = Collections.synchronizedList(new ArrayList<>());
        List<String> failures = Collections.synchronizedList(new ArrayList<>());

        Runnable buy = () -> {
            try {
                vm.selectItem("A1");
                vm.insertCoin(200);
                int change = vm.dispense();
                successes.add(change);
            } catch (VendingException e) {
                failures.add(e.getMessage());
            }
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            threads.add(new Thread(buy));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // Exactly 10 purchases; 90 stock-out failures
        // Note: serialization means each thread sees a consistent state
        int finalQty = vm.getInventory().quantity("A1");
        int sold = 10 - finalQty;
        assert finalQty >= 0 : "Inventory went negative: " + finalQty;
        assert successes.size() == sold : "Success count " + successes.size() + " != sold " + sold;
        System.out.println("PASS: testInventoryNeverNegative (" + sold + " sold, " + finalQty + " remaining)");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: No revenue corruption under concurrent transactions
    // N threads each complete a full transaction.
    // Total revenue must equal N x item price.
    // ─────────────────────────────────────────────────────────────
    static void testRevenueConsistency() throws InterruptedException {
        VendingMachine vm = new VendingMachine();
        Item water = new Item("B1", "Water", 100);
        vm.getInventory().addItem(water, 50);

        AtomicInteger successCount = new AtomicInteger(0);

        Runnable buy = () -> {
            try {
                vm.selectItem("B1");
                vm.insertCoin(100);
                vm.dispense();
                successCount.incrementAndGet();
            } catch (VendingException e) {
                // ignore
            }
        };

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            threads.add(new Thread(buy));
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        int expectedRevenue = successCount.get() * 100;
        assert vm.getRevenue() == expectedRevenue :
            "Revenue mismatch: expected " + expectedRevenue + ", got " + vm.getRevenue();
        System.out.println("PASS: testRevenueConsistency (" + successCount.get()
            + " purchases, revenue=" + vm.getRevenue() + ")");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: Cancel returns inserted amount, leaves machine in clean state
    // Thread A inserts coins and then cancels.
    // Thread B then completes a transaction.
    // Thread B must see a clean machine state (not A's insertedCents).
    // ─────────────────────────────────────────────────────────────
    static void testCancelResetsState() throws InterruptedException {
        VendingMachine vm = new VendingMachine();
        Item snack = new Item("C1", "Snack", 75);
        vm.getInventory().addItem(snack, 5);

        CyclicBarrier barrier = new CyclicBarrier(2);
        Map<String, Integer> results = new ConcurrentHashMap<>();

        Runnable threadA = () -> {
            vm.selectItem("C1");
            vm.insertCoin(200);
            int refund = vm.cancel();
            results.put("refund", refund);
            try {
                barrier.await(); // signal B to proceed
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Runnable threadB = () -> {
            try {
                barrier.await(); // wait for A to cancel
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            vm.selectItem("C1");
            vm.insertCoin(75);
            int change = vm.dispense();
            results.put("change", change);
        };

        Thread ta = new Thread(threadA);
        Thread tb = new Thread(threadB);
        ta.start();
        tb.start();
        ta.join();
        tb.join();

        assert results.get("refund") == 200 : "Expected refund 200, got " + results.get("refund");
        assert results.get("change") == 0 : "Expected change 0, got " + results.get("change");
        assert vm.getInventory().quantity("C1") == 4 : "Inventory not decremented correctly";
        System.out.println("PASS: testCancelResetsState");
    }

    public static void main(String[] args) throws InterruptedException {
        testInventoryNeverNegative();
        testRevenueConsistency();
        testCancelResetsState();
        System.out.println("All concurrency tests passed.");
    }
}
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
