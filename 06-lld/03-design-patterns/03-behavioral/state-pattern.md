# State Pattern

## Question

You are designing a `VendingMachine`. It has four states: `IDLE`, `HAS_COIN`, `DISPENSING`, `OUT_OF_STOCK`. Write `insertCoin()`, `selectProduct()`, and `dispense()` methods for the machine.

Try it before reading on.

---

## Pattern Mindmap

```
[State Pattern]
├── Problem It Solves
│   ├── VendingMachine: 4 states × 3 actions = 12 if/else branches
│   ├── Add new state: must touch every method to add new branch
│   └── Complexity grows as O(S × A) — unmanageable
├── Core Structure
│   ├── State interface: insertCoin(), selectProduct(), dispense()
│   ├── Concrete states: IdleState, HasCoinState, DispensingState, OutOfStockState
│   ├── Context: VendingMachine holds current State reference
│   ├── Context delegates: insertCoin() calls currentState.insertCoin()
│   └── States transition context: state.insertCoin() calls context.setState(new HasCoinState())
├── Transition Ownership
│   ├── State objects own their own transitions
│   ├── IdleState.insertCoin() → context.setState(new HasCoinState())
│   ├── HasCoinState.dispense() → context.setState(new IdleState())
│   └── Each state only handles its own valid transitions — invalid actions throw
├── Analogy
│   ├── Traffic light: each color knows when to transition to the next color
│   └── The light (context) delegates behavior to the current signal (state)
├── When to Use
│   ├── Object behavior depends heavily on its current state
│   ├── Large switch/if-else in methods based on state field
│   └── State transitions are complex or need to be self-contained
├── State vs Strategy
│   ├── Strategy: algorithms are interchangeable; client chooses
│   ├── State: transitions are internal — state decides next state
│   └── State pattern: context changes state itself; Strategy: client swaps strategy
├── Trade-offs
│   ├── More classes (one per state), but each is small and focused
│   ├── Adding a new state: one new class + update transitions in neighboring states
│   └── State explosion if states are too fine-grained
└── Interview Angles
    ├── How is State different from Strategy?
    ├── Who owns the transition logic — context or state?
    └── How do you model hierarchical states (nested states)?
```

## Problem Without the Pattern

```java
class VendingMachine {
    private String state = "IDLE";

    public void insertCoin() {
        if (state.equals("IDLE")) {
            state = "HAS_COIN";
            System.out.println("Coin accepted");
        } else if (state.equals("HAS_COIN")) {
            System.out.println("Coin already inserted");
        } else if (state.equals("DISPENSING")) {
            System.out.println("Please wait, dispensing");
        } else if (state.equals("OUT_OF_STOCK")) {
            System.out.println("Machine out of stock");
        }
    }

    public void selectProduct() {
        if (state.equals("IDLE")) {
            System.out.println("Please insert coin first");
        } else if (state.equals("HAS_COIN")) {
            state = "DISPENSING";
        }
        // ... more branches
    }
    // dispense() — another full set of branches
}
```

**What breaks**:
1. **O(S × A) branching**: S states × A actions = branches that grow quadratically. 4 states × 4 actions = 16 blocks, all in one class.
2. **OCP violation**: Adding a new state (e.g., `MAINTENANCE`) requires editing every method.
3. **Logic scatter**: The behavior for a given state is split across all methods, not grouped in one place.
4. **Invalid transitions compile silently**: Nothing in the type system prevents calling `dispense()` in `IDLE` — only a runtime string check catches it.

---

## Derive the Minimal Fix

The constraint: **each state should own its own behavior, and the machine delegates to the current state**.

Step 1 — extract a `State` interface where each action is a method:
```java
interface State {
    void insertCoin();
    void selectProduct();
    void dispense();
}
```

Step 2 — each state is its own class that owns the logic for that state:
```java
class IdleState implements State {
    private VendingMachine machine;
    public void insertCoin()    { machine.setState(machine.getHasCoinState()); }
    public void selectProduct() { System.out.println("Insert coin first"); }
    public void dispense()      { System.out.println("Insert coin first"); }
}

class HasCoinState implements State {
    private VendingMachine machine;
    public void insertCoin()    { System.out.println("Coin already inserted"); }
    public void selectProduct() { machine.setState(machine.getDispensingState()); }
    public void dispense()      { System.out.println("Select product first"); }
}
```

Step 3 — the machine holds a reference to the current state and delegates:
```java
class VendingMachine {
    private State currentState;

    public void insertCoin()    { currentState.insertCoin(); }
    public void selectProduct() { currentState.selectProduct(); }
    public void dispense()      { currentState.dispense(); }

    public void setState(State s) { currentState = s; }
}
```

Adding `MAINTENANCE` is now one new `MaintenanceState` class. The existing states are untouched.

---

> **Category**: Behavioral Pattern
> **Purpose**: Allows an object to alter its behavior when its internal state changes. The object will appear to change its class.

## Real-Life Analogy

**A traffic light.**

A traffic light has three states: Green, Yellow, Red. Each behaves completely differently:
- **Green**: Cars go. If you "request next state", it transitions to Yellow.
- **Yellow**: Slow down. If you "request next state", it transitions to Red.
- **Red**: Stop. If you "request next state", it transitions to Green.

The traffic light object is the same object throughout. But its behavior changes entirely based on its current state. The transition rules are built into each state — Green knows it should go to Yellow, not Red. You don't need a massive `if state == GREEN ... else if state == YELLOW ...` block.

**The key insight**: The object *delegates* its behavior to the current state object. Swapping the state object changes how the object behaves — no `if-else`, no giant switch.

---

## When to Use

- An object's behavior depends on its **internal state** and must change at runtime.
- You have **large conditional blocks** (`if/switch`) based on state that affect multiple methods.
- **State transitions** have rules that should be encoded in the states themselves, not the context class.
- Adding a new state should require only a new class, not changes to every method in the context.

---

## Understanding the Problem

Bad code — state as a string, logic spread everywhere:

```java
public void insertCoin() {
    if (state.equals("Idle")) accept();
    else if (state.equals("HasCoin")) reject();
    else if (state.equals("OutOfStock")) reject();
}

public void pressButton() {
    if (state.equals("Idle")) System.out.println("Insert coin first");
    else if (state.equals("HasCoin")) dispense(); // + transition to Idle
    else if (state.equals("OutOfStock")) System.out.println("Out of stock");
}
```

**Problems**: Adding a new state (`Dispensing`, `Refunding`) requires modifying every method. The state transition logic is spread across all methods. Violates OCP.

---

## Solution: State Pattern

```java
// 1. State Interface
interface State {
    void insertCoin();
    void pressButton();
    void dispense();
}

// 2. Context (The Machine)
class VendingMachine {
    private State idleState;
    private State hasCoinState;
    private State currentState;
    
    public VendingMachine() {
        this.idleState = new IdleState(this);
        this.hasCoinState = new HasCoinState(this);
        this.currentState = this.idleState;
    }
    
    public void setState(State state) {
        this.currentState = state;
    }
    
    public void insertCoin() {
        currentState.insertCoin();
    }
    
    public void pressButton() {
        currentState.pressButton();
    }
   
    public State getIdleState() { return idleState; }
    public State getHasCoinState() { return hasCoinState; }
}

// 3. Concrete States — each state knows its own rules and transitions
class IdleState implements State {
    private VendingMachine machine;
    
    public IdleState(VendingMachine machine) {
        this.machine = machine;
    }
    
    @Override
    public void insertCoin() {
        System.out.println("Coin inserted");
        machine.setState(machine.getHasCoinState());  // Transition built into the state
    }
    
    @Override
    public void pressButton() {
        System.out.println("Insert coin first");
    }
    
    @Override
    public void dispense() {
        System.out.println("Insert coin first");
    }
}

class HasCoinState implements State {
    private VendingMachine machine;
    
    public HasCoinState(VendingMachine machine) {
        this.machine = machine;
    }
    
    @Override
    public void insertCoin() {
        System.out.println("Coin already inserted");
    }
    
    @Override
    public void pressButton() {
        System.out.println("Button pressed — dispensing...");
        machine.setState(machine.getIdleState());  // Transition back to Idle
    }
    
    @Override
    public void dispense() {
        System.out.println("Dispensing...");
    }
}

// Client
public class Main {
    public static void main(String[] args) {
        VendingMachine vm = new VendingMachine();
        vm.insertCoin();   // State changes to HasCoin
        vm.pressButton();  // Action allowed, state reverts to Idle
        vm.pressButton();  // "Insert coin first" — correct behavior for Idle state
    }
}
```

### Class Diagram

```mermaid
classDiagram
    class State {
        <<interface>>
        +insertCoin()
        +pressButton()
        +dispense()
    }

    class VendingMachine {
        -State idleState
        -State hasCoinState
        -State currentState
        +setState(State state)
        +insertCoin()
        +pressButton()
        +getIdleState() State
        +getHasCoinState() State
    }

    class IdleState {
        -VendingMachine machine
        +insertCoin()
        +pressButton()
        +dispense()
    }

    class HasCoinState {
        -VendingMachine machine
        +insertCoin()
        +pressButton()
        +dispense()
    }

    class Main {
        +main(String[] args)
    }

    State <|.. IdleState
    State <|.. HasCoinState
    VendingMachine o-- State
    IdleState o-- VendingMachine
    HasCoinState o-- VendingMachine
    Main ..> VendingMachine : uses
```

---

## How State Pattern Resolves the Issues

| Issue | Solution |
|---|---|
| **Giant if-else across every method** | Each state class contains only its own behavior. No conditionals anywhere. |
| **Adding a new state modifies all methods** | Add a new state class. Context methods (`insertCoin`, `pressButton`) never change. |
| **Transition logic scattered** | Each state decides its own transitions: `IdleState.insertCoin()` transitions to `HasCoinState`. The context just delegates. |
| **Violates OCP** | New state = new class. Zero changes to existing code. |

---

## State vs. Strategy

| Aspect | State | Strategy |
|---|---|---|
| **Who switches?** | The object switches its own state internally as side effect of actions. | The client typically chooses and injects the strategy. |
| **Do states know each other?** | Yes — `IdleState` knows to transition to `HasCoinState`. | No — strategies are independent algorithms. |
| **Purpose** | Model state machines with transition rules. | Swap interchangeable algorithms. |

---

## When NOT to Use

- If the object has only 2 states and the transitions are trivial, a simple boolean flag is clearer.
- If states don't have distinct behavior — state pattern adds classes without simplifying logic.

---

## Pros & Cons

**Pros**
- Eliminates large conditional chains based on state.
- Each state class is small and focused (Single Responsibility).
- Adding new states doesn't break existing ones (Open/Closed Principle).
- Transition logic is localized to each state — easy to audit.

**Cons**
- Increases class count — one class per state.
- States are coupled to the context class (need a reference to transition back).
- Can be overkill for objects with only 2-3 trivial states.
