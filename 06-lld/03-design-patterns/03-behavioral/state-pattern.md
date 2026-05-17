# State Pattern

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
