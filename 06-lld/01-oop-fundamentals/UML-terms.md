> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The five UML class-diagram relationships — Association, Aggregation, Composition, Inheritance, Realization — and how to draw and code each one.
>
> **Key ideas:**
> - Aggregation and Composition are stricter subtypes of Association — think of them as a spectrum of ownership commitment, not five unrelated concepts
> - Association ("knows-a"): objects interact but neither owns the other — no permanent reference
> - Aggregation ("has-a", weak): parent holds a reference to a child that can outlive it — child is passed in from outside
> - Composition ("part-of", strong): parent creates and owns the child's lifecycle — child dies when parent dies
> - Inheritance ("is-a"): child class extends a parent's state and behavior — a strict, permanent hierarchy
> - Realization ("fulfills-contract"): a class implements an interface — no shared logic, only a forced method contract
>
> **Key takeaway:** In an interview, the diamond/arrow you draw signals how deeply you understand ownership and lifecycle — get Aggregation vs. Composition right, since mixing them up is the most common LLD diagramming mistake.

---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals, uml]
---

# UML Relationships

The five relationships used on every class diagram, ordered from weakest to strongest coupling. Aggregation and Composition are both special cases of Association — the difference is ownership and lifecycle, not the presence of a reference.

---

## Reference Mindmap

```
[UML Relationships]
├── Connection relationships (linking separate objects)
│   ├── Association   — "knows-a"   — no ownership, temporary interaction
│   ├── Aggregation    — "has-a"     — weak ownership, child outlives parent
│   └── Composition    — "part-of"   — strong ownership, child dies with parent
└── Type relationships (hierarchies and contracts)
    ├── Inheritance    — "is-a"              — extends state + behavior
    └── Realization    — "fulfills-contract" — implements an interface, no shared logic, implemented through an interface
```

---

## The Ownership Spectrum

| Relationship | Commitment | Example |
|---|---|---|
| Association | "We know each other." | `Driver` uses a `Car` |
| Aggregation | "I have you, but you can leave." | `Employee` has an `OfficeDesk` |
| Composition | "I am made of you. We die together." | `House` has a `Room` |

---

## Connection Relationships

### 1. Association ("Knows-A" / "Uses-A")

Two objects interact, but neither owns the other. Usually one object is passed into a method of the other temporarily and not stored.

**Concept**: A `Driver` uses a `Car` to drive. The driver doesn't own the car and doesn't hold it as a permanent attribute.

**UML diagram**: A solid line, sometimes with an open arrow `----->` pointing to the object being used.

```java
class Driver {
    // The car is passed into a specific method, used, and forgotten.
    void driveToWork(Car car) {
        car.startEngine();
    }
}
```

---

### 2. Aggregation ("Has-A" — Weak Ownership)

One object holds a reference to another as a permanent attribute, but the child can exist independently of the parent.

**Concept**: An `Employee` has an `OfficeDesk`. The employee keeps the desk while they work there, but if the employee is fired, the desk remains in the building.

**UML diagram**: A solid line with an empty diamond `◇----` at the parent (`Employee`) end.

```java
class Employee {
    private final String name;
    private final OfficeDesk desk; // stored, but not created by Employee

    Employee(String name, OfficeDesk desk) {
        this.name = name;
        this.desk = desk; // passed in from the outside
    }
}
```

---

### 3. Composition ("Part-Of" — Strong Ownership)

One object is made up of another, with a strict lifecycle dependency. If the parent is destroyed, the child is destroyed with it.

**Concept**: A `House` has a `Room`. If you demolish the house, the room ceases to exist.

**UML diagram**: A solid line with a filled/black diamond `◆----` at the parent end.

```java
class House {
    private final Room livingRoom;

    House() {
        // House creates the Room. If House is deleted, Room is deleted with it.
        this.livingRoom = new Room("Living Room");
    }
}
```

---

## Type Relationships

### 4. Inheritance ("Is-A")

A child class inherits the state and behavior of a parent class — a strict, permanent hierarchy that reuses code.

**Concept**: A `Manager` is an `Employee`. It does everything an employee does, plus extras.

**UML diagram**: A solid line with an empty, closed triangle `◁----` pointing to the parent.

```java
abstract class Employee {
    abstract double getSalary();
}

class Manager extends Employee { // Manager IS-A Employee
    @Override
    double getSalary() { return 0; }

    void approveLeaves() { }
}
```

---

### 5. Realization / Implementation ("Fulfills-Contract")

A class implements an interface (or abstract base). The parent carries no logic — it only forces the child to implement specific methods.

**Concept**: `PineconeStore` realizes the `VectorDatabase` interface.

**UML diagram**: A dashed line with an empty, closed triangle `◁- - - -` pointing to the interface.

```java
interface VectorDatabase {
    void saveVector(double[] data);
}

class PineconeStore implements VectorDatabase {
    @Override
    public void saveVector(double[] data) {
        System.out.println("Saving to Pinecone...");
    }
}
```

---

## Self-Check

Match each pair to the correct relationship — Association, Aggregation, Composition, or Inheritance.

| # | Pair | Question | Answer |
|---|---|---|---|
| 1 | `ShoppingCart` and the `Item`s inside it | If you delete the cart, do the items vanish from the store's inventory? | Aggregation — items outlive the cart |
| 2 | `CreditCard` and `PaymentMethod` | A credit card *is a* type of payment method. | Inheritance |
| 3 | `File` and `FileSystem` | If you destroy the file system, what happens to the files? | Composition — files die with the file system |
| 4 | `Customer` and `CustomerSupportAgent` | They talk on a call, but neither owns the other. | Association |

---

**Related:** [four-pillars.md](four-pillars.md) — encapsulation, inheritance, polymorphism, abstraction · [principles.md](principles.md) — IS-A vs. HAS-A, composition vs. inheritance as a design decision · [glossary.md](../glossary.md) — UML relationships entry with the same terms in quick-lookup form
