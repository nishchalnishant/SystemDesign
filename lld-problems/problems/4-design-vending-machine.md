# Design Vending Machine

> **Difficulty**: Medium
> **Topics**: State Design Pattern, State Machine
> **Key Concepts**: Managing state transitions, inventory, handling money.

## Phase 1: Requirements Gathering

### Goals
- Design a Vending Machine that dispenses products based on user selection and payment.
- Identify states and transitions.
- Handle edge cases like insufficient funds or out-of-stock items.

### 1. Who are the actors?
- **User**: Selects items, inserts money, collects product and change.
- **Admin**: Restocks items and collects money.
- **System**: Manages inventory, state, and transactions.

### 2. What are the must-have features? (Core)
- **Product Selection**: User selects an item by code (e.g., A1).
- **Payment Processing**: User inserts coins/notes.
- **Dispensing**: Machine dispenses item if balance >= price.
- **Change Return**: Machine returns excess balance.
- **State Management**: Handle Idle, Selection, Dispensing, OutOfOrder states.

### 3. What are the constraints?
- **Concurrency**: One user at a time (physical machine).
- **Hardware Interface**: Methods to control motors/display (abstracted here).

---

## Phase 2: Use Cases

### UC1: Purchase Item
**Actor**: User
**Flow**:
1. User selects an item (State: Idle -> Selection).
2. System checks inventory.
3. User inserts money (State: Selection).
4. If balance >= price, change state to Dispensing.
5. System dispenses item and returns change.
6. System resets to Idle state.

### UC2: Cancel Request
**Actor**: User
**Flow**:
1. User presses cancel during selection.
2. System refunds inserted money.
3. System resets to Idle state.

---

## Phase 3: Class Diagram

### Step 1: Core Entities
- **VendingMachine**: Context class holding state and inventory.
- **State** (Interface): Defines behavior for states.
- **Inventory**: Manages products and counts.
- **Product**: Item details.
- **Coin/Note**: Money entities (simplified as double for this design).

### UML Diagram

```mermaid
classDiagram
    class VendingMachine {
        -State state
        -Inventory inventory
        -double balance
        +setState(State)
        +selectItem(code)
        +insertMoney(amount)
        +dispense()
    }

    class State {
        <<interface>>
        +selectItem(code)
        +insertMoney(amount)
        +dispense()
    }

    class IdleState { ... }
    class SelectionState { ... }
    class DispenseState { ... }

    class Inventory {
        -Map~String, Product~ products
        +deduct(code)
        +isAvailable(code)
    }

    class Product {
        -String name
        -double price
    }

    VendingMachine --> State
    State <|.. IdleState
    State <|.. SelectionState
    State <|.. DispenseState
    VendingMachine --> Inventory
```

---

## Phase 4: Design Patterns

### 1. State Pattern
- **Description**: Allows an object to alter its behavior when its internal state changes. The object will appear to change its class.
- **Why used**: The Vending Machine has distinct behaviors depending on its state (e.g., `Idle`, `Selection`, `Dispensing`). The pattern eliminates complex `if-else` or `switch` statements by delegating behavior to state objects.

---

## Phase 5: Code Key Methods

### Java Implementation

```java
import java.util.*;

// 1. Core Entities
class Product {
    String name;
    double price;
    public Product(String name, double price) { this.name = name; this.price = price; }
}

class Inventory {
    private Map<String, Product> products = new HashMap<>(); // Code -> Product
    private Map<String, Integer> counts = new HashMap<>();   // Code -> Count

    public void addProduct(String code, Product p, int count) {
        products.put(code, p);
        counts.put(code, count);
    }

    public double getPrice(String code) {
        return products.containsKey(code) ? products.get(code).price : 0;
    }

    public boolean isAvailable(String code) {
        return counts.getOrDefault(code, 0) > 0;
    }

    public void deduct(String code) {
        if (isAvailable(code)) {
            counts.put(code, counts.get(code) - 1);
        }
    }
    
    public Product getProduct(String code) { return products.get(code); }
}

// 2. State Interface
interface State {
    void selectItem(String code);
    void insertMoney(double amount);
    void dispense();
}

// 3. Vending Machine Context
class VendingMachine {
    private State idleState;
    private State selectionState;
    private State dispenseState;
    
    private State currentState;
    private Inventory inventory;
    private double currentBalance;
    private String selectedCode;

    public VendingMachine() {
        idleState = new IdleState(this);
        selectionState = new SelectionState(this);
        dispenseState = new DispenseState(this);
        
        inventory = new Inventory();
        currentState = idleState; // Initial State
        currentBalance = 0;
    }

    public void setState(State state) { this.currentState = state; }
    public State getIdleState() { return idleState; }
    public State getSelectionState() { return selectionState; }
    public State getDispenseState() { return dispenseState; }
    
    public Inventory getInventory() { return inventory; }
    public void addBalance(double amount) { this.currentBalance += amount; }
    public double getBalance() { return currentBalance; }
    public void setSelectedCode(String code) { this.selectedCode = code; }
    public String getSelectedCode() { return selectedCode; }
    public void reset() { currentBalance = 0; selectedCode = null; }

    // Actions delegation
    public void selectItem(String code) { currentState.selectItem(code); }
    public void insertMoney(double amount) { currentState.insertMoney(amount); }
    public void dispense() { currentState.dispense(); }
}

// 4. Concrete States
class IdleState implements State {
    VendingMachine vm;
    public IdleState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) {
        if (vm.getInventory().isAvailable(code)) {
            vm.setSelectedCode(code);
            vm.setState(vm.getSelectionState());
            System.out.println("Item " + code + " selected. Price: " + vm.getInventory().getPrice(code));
        } else {
            System.out.println("Item unavailable.");
        }
    }
    public void insertMoney(double amount) { System.out.println("Select item first."); }
    public void dispense() { System.out.println("Select item first."); }
}

class SelectionState implements State {
    VendingMachine vm;
    public SelectionState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) { System.out.println("Item already selected."); }
    public void insertMoney(double amount) {
        vm.addBalance(amount);
        double price = vm.getInventory().getPrice(vm.getSelectedCode());
        System.out.println("Inserted: " + amount + " Total: " + vm.getBalance());
        
        if (vm.getBalance() >= price) {
            vm.setState(vm.getDispenseState());
            vm.dispense();
        }
    }
    public void dispense() { System.out.println("Insufficient funds."); }
}

class DispenseState implements State {
    VendingMachine vm;
    public DispenseState(VendingMachine vm) { this.vm = vm; }

    public void selectItem(String code) { System.out.println("Dispensing..."); }
    public void insertMoney(double amount) { System.out.println("Dispensing..."); }
    public void dispense() {
        String code = vm.getSelectedCode();
        double price = vm.getInventory().getPrice(code);
        double change = vm.getBalance() - price;
        
        vm.getInventory().deduct(code);
        System.out.println("Dispensing " + vm.getInventory().getProduct(code).name);
        if (change > 0) System.out.println("Returning change: " + change);
        
        vm.reset();
        vm.setState(vm.getIdleState());
    }
}

// 5. Client
public class VendingMachineDemo {
    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();
        vm.getInventory().addProduct("A1", new Product("Coke", 1.5), 5);
        
        vm.selectItem("A1");
        vm.insertMoney(1.0);
        vm.insertMoney(1.0); // Total 2.0 -> Dispense + 0.5 Change
    }
}
```

---

## Phase 6: Discussion

### Concurrency
**Q: How to handle concurrency (two users selecting same last item)?**
- A: "In `Inventory.deduct()`, implement a double-check lock or use `AtomicInteger` for counts. If using database, use optimistic locking."

### Extension: Maintenance Mode
**Q: How to add new states (e.g., Maintenance)?**
- A: "Create `MaintenanceState` class implementing `State` interface. Add transition logic (only Admin can trigger). In Maintenance, all actions like `insertMoney` would throw exceptions or return error messages."

### Hardware Integration
**Q: How to handle exact change only?**
- A: "The machine needs to track its own internal cash inventory. In `DispenseState`, check if internal cash can provide the change using a Greedy algorithm (DP for optimal)."

---

## SOLID Principles Checklist

- **S (Single Responsibility)**: `Inventory` manages stock, `States` manage transitions, `VendingMachine` acts as context.
- **O (Open/Closed)**: New states can be added without modifying existing states significantly.
- **L (Liskov Substitution)**: All states implement `State` interface correctly.
- **I (Interface Segregation)**: `State` interface covers all necessary actions.
- **D (Dependency Inversion)**: `VendingMachine` depends on `State` interface, not concrete states.
