# Design Vending Machine

> **Difficulty**: Medium
> **Topics**: State Design Pattern, State Machine, Inventory Management
> **Extension**: Maintenance mode, exact-change enforcement, multiple payment types

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

## Phase 5: Key Java Implementation

```java
import java.math.BigDecimal;
import java.util.*;

// ── Currency-safe Product & Inventory ─────────────────────────────────────

class Product {
    final String name;
    final BigDecimal price;
    Product(String name, BigDecimal price) { this.name = name; this.price = price; }
}

class Inventory {
    private final Map<String, Product> products = new HashMap<>();
    private final Map<String, Integer> counts   = new HashMap<>();

    public void addProduct(String code, Product p, int count) {
        products.put(code, p);
        counts.put(code, count);
    }

    public boolean isAvailable(String code) {
        return counts.getOrDefault(code, 0) > 0;
    }

    public BigDecimal getPrice(String code) {
        return products.containsKey(code) ? products.get(code).price : BigDecimal.ZERO;
    }

    public String getName(String code) {
        return products.containsKey(code) ? products.get(code).name : "Unknown";
    }

    public void deduct(String code) {
        if (isAvailable(code)) counts.put(code, counts.get(code) - 1);
    }

    public void restock(String code, int count) {
        counts.merge(code, count, Integer::sum);
    }
}

// ── State Interface ────────────────────────────────────────────────────────

interface State {
    void selectItem(String code);
    void insertMoney(BigDecimal amount);
    void dispense();
    void cancel();
}

// ── Context ────────────────────────────────────────────────────────────────

class VendingMachine {
    private State idleState;
    private State selectedState;
    private State dispensingState;
    private State outOfOrderState;

    private State currentState;
    private final Inventory inventory;
    private BigDecimal balance;
    private String selectedCode;

    public VendingMachine() {
        inventory       = new Inventory();
        balance         = BigDecimal.ZERO;
        idleState       = new IdleState(this);
        selectedState   = new ProductSelectedState(this);
        dispensingState = new DispensingState(this);
        outOfOrderState = new OutOfOrderState(this);
        currentState    = idleState;
    }

    // Delegate user actions to current state
    public void selectItem(String code)        { currentState.selectItem(code);   }
    public void insertMoney(BigDecimal amount) { currentState.insertMoney(amount); }
    public void pressDispense()                { currentState.dispense();          }
    public void pressCancel()                  { currentState.cancel();            }

    // State accessors (used by state classes)
    public void setState(State s)       { this.currentState = s; }
    public State getIdleState()         { return idleState;       }
    public State getSelectedState()     { return selectedState;   }
    public State getDispensingState()   { return dispensingState; }
    public State getOutOfOrderState()   { return outOfOrderState; }
    public Inventory getInventory()     { return inventory;       }

    public void addBalance(BigDecimal amt) { balance = balance.add(amt); }
    public BigDecimal getBalance()         { return balance; }
    public void setSelectedCode(String c)  { selectedCode = c; }
    public String getSelectedCode()        { return selectedCode; }

    public void reset() {
        balance       = BigDecimal.ZERO;
        selectedCode  = null;
    }
}

// ── Concrete States ────────────────────────────────────────────────────────

class IdleState implements State {
    private final VendingMachine vm;
    IdleState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) {
        if (!vm.getInventory().isAvailable(code)) {
            System.out.println("[IDLE] Item " + code + " is out of stock.");
            return;
        }
        vm.setSelectedCode(code);
        vm.setState(vm.getSelectedState());
        System.out.println("[IDLE→SELECTED] Item " + code + " selected. Price: "
                + vm.getInventory().getPrice(code));
    }

    public void insertMoney(BigDecimal amount) { System.out.println("[IDLE] Select an item first."); }
    public void dispense()                     { System.out.println("[IDLE] Select an item first."); }
    public void cancel()                       { System.out.println("[IDLE] Nothing to cancel."); }
}

class ProductSelectedState implements State {
    private final VendingMachine vm;
    ProductSelectedState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) { System.out.println("[SELECTED] Item already selected."); }

    public void insertMoney(BigDecimal amount) {
        vm.addBalance(amount);
        BigDecimal price = vm.getInventory().getPrice(vm.getSelectedCode());
        System.out.println("[SELECTED] Inserted: " + amount + " | Balance: " + vm.getBalance());

        if (vm.getBalance().compareTo(price) >= 0) {
            vm.setState(vm.getDispensingState());
            vm.pressDispense();
        }
    }

    public void dispense() { System.out.println("[SELECTED] Insert more money."); }

    public void cancel() {
        System.out.println("[SELECTED] Cancelled. Refunding: " + vm.getBalance());
        vm.reset();
        vm.setState(vm.getIdleState());
    }
}

class DispensingState implements State {
    private final VendingMachine vm;
    DispensingState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) { System.out.println("[DISPENSING] Please wait..."); }
    public void insertMoney(BigDecimal amount) { System.out.println("[DISPENSING] Please wait..."); }
    public void cancel()               { System.out.println("[DISPENSING] Cannot cancel now."); }

    public void dispense() {
        String code  = vm.getSelectedCode();
        BigDecimal price  = vm.getInventory().getPrice(code);
        BigDecimal change = vm.getBalance().subtract(price);

        vm.getInventory().deduct(code);
        System.out.println("[DISPENSING] Dispensed: " + vm.getInventory().getName(code));
        if (change.compareTo(BigDecimal.ZERO) > 0) {
            System.out.println("[DISPENSING] Change returned: " + change);
        }

        vm.reset();
        vm.setState(vm.getIdleState());
        System.out.println("[DISPENSING→IDLE] Ready.");
    }
}

class OutOfOrderState implements State {
    private final VendingMachine vm;
    OutOfOrderState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code)        { System.out.println("[OUT_OF_ORDER] Machine under maintenance."); }
    public void insertMoney(BigDecimal amount) { System.out.println("[OUT_OF_ORDER] Machine under maintenance."); }
    public void dispense()                     { System.out.println("[OUT_OF_ORDER] Machine under maintenance."); }
    public void cancel()                       { System.out.println("[OUT_OF_ORDER] Machine under maintenance."); }
}

// ── Demo ───────────────────────────────────────────────────────────────────

class VendingMachineDemo {
    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();
        vm.getInventory().addProduct("A1", new Product("Coke",  new BigDecimal("1.50")), 5);
        vm.getInventory().addProduct("B2", new Product("Chips", new BigDecimal("2.00")), 3);

        vm.selectItem("A1");
        vm.insertMoney(new BigDecimal("1.00"));
        vm.insertMoney(new BigDecimal("1.00")); // balance 2.00 >= 1.50 → auto-dispense
        // Output: Dispensed Coke, Change returned: 0.50
    }
}
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
```java
class MaintenanceState implements State {
    // Admin can call restock() and collectCash()
    // All customer actions print "Machine under maintenance"
    // Admin calls done() → setState(idleState)
}
```

**Multiple payment types:**
Extract `PaymentProcessor` interface with `CashProcessor`, `CardProcessor`, `QRCodeProcessor`. `insertMoney()` becomes `processPayment(PaymentRequest)`.

**Exact-change coin tracking (SDE-3 depth):**
```java
// In DispensingState.dispense():
Map<Integer, Integer> coinBox = vm.getCoinBox(); // denomination → count
int changeInCents = balance.subtract(price).multiply(BigDecimal.valueOf(100)).intValue();
// Greedy: 100¢, 25¢, 10¢, 5¢, 1¢
// If cannot make change exactly → refund all, display error
```
