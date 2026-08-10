> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Open/Closed Principle (OCP) — software entities should be open for extension but closed for modification.
>
> **Key concepts:**
> - The problem: massive `switch` or `if/else` statements. Adding a new case (e.g., a new shape to an area calculator) requires modifying existing, tested code.
> - The risk: modifying existing code risks introducing regressions into already working features.
> - The fix: polymorphism. Define an interface (`Shape` with `area()`). The calculator calls the interface. Add new shapes by creating new classes that implement `Shape`.
> - "Closed for modification": the `AreaCalculator` class never changes when new shapes are added.
> - "Open for extension": the system's behavior is extended by adding new shape classes.
> - Strategy Pattern: OCP is the foundation of the Strategy Pattern (swapping algorithms at runtime without changing the caller).
>
> **Key takeaway:** If you have to open an existing file to add a new feature (like a new payment method or a new notification type), you are violating OCP. Use interfaces and polymorphism instead.

---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---
# Open/Closed Principle (OCP)

**Question**: Your `AreaCalculator` class works perfectly for circles and rectangles. Product adds a triangle. You open the class, add another `else if`. Three months later, product adds a hexagon. You open it again. What is the risk each time you do this?

**Problem without OCP**: Every new shape requires modifying `AreaCalculator`. That class already has working, tested code. Every modification risks introducing a regression. If ten places depend on `AreaCalculator`, they all need to be retested. The class that handles the "calculate total area" logic should never need to know about individual shape formulas.

**Minimal fix**: Make each shape responsible for its own area calculation. `AreaCalculator` calls `shape.area()` — it never knows whether it is calling a circle, rectangle, or hexagon. Add a new shape by creating a new class. `AreaCalculator` never changes.

**Full principle**: OCP — Software entities should be **open for extension** (add new behavior by adding new code), but **closed for modification** (adding new behavior should not require changing existing, working code). The mechanism is polymorphism: abstract the varying part into an interface, and new variations implement that interface.

> **Analogy**: A vending machine. You can add new product slots (extension) without rewiring the internal dispensing mechanism (modification). Add new snacks, don't rewrite the machine.

---

## Topic Mindmap

```
[Open/Closed Principle]
├── Problem It Solves
│   ├── AreaCalculator with instanceof chain — add shape = modify class
│   ├── Every modification risks regression in working, tested code
│   └── Classes that depend on AreaCalculator must all be retested
├── Core Rule
│   ├── Open for extension: add new behavior by adding new code
│   ├── Closed for modification: existing working code is untouched
│   └── Mechanism: polymorphism — abstract the varying part into an interface
├── The Fix Pattern
│   ├── Shape interface with area() method
│   ├── Circle, Rectangle, Triangle, Hexagon each implement Shape
│   ├── AreaCalculator calls shape.area() — never knows the concrete type
│   └── Add Triangle: new class only, AreaCalculator unchanged
├── NotificationSender Example
│   ├── if/else on notification type = OCP violation
│   ├── NotificationSender interface with send()
│   └── EmailNotification, SMSNotification, PushNotification implement it
├── When OCP Applies
│   ├── Behavior varies across types (payment methods, shapes, exporters)
│   ├── The variation point is known and stable
│   └── New variants are expected over time
├── When NOT to Over-Apply
│   ├── Don't abstract until you have two concrete cases (YAGNI)
│   ├── Premature abstraction = wrong interface that blocks real extension
│   └── Bug fixes do require modifying existing code — that is fine
├── Trade-offs
│   ├── More interfaces/classes — more indirection to trace
│   ├── First extension is expensive (create interface + first impl)
│   └── Second and all subsequent extensions are cheap
└── Interview Angles
    ├── How does OCP relate to polymorphism?
    ├── Can you achieve OCP without interfaces? (composition, function objects)
    └── What breaks when you violate OCP at scale?
```

## The Problem

If you have to change existing code every time you add new functionality, you risk introducing bugs into previously working code. The existing logic becomes fragile — every addition is a potential regression.

### Bad Example (Violates OCP)

```java
class AreaCalculator {
    public double calculateArea(Object shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            return r.length * r.width;
        } else if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return Math.PI * c.radius * c.radius;
        }
        // To add Triangle, we MUST MODIFY this class!
        return 0;
    }
}
```

**Problems:**
- Every new shape requires modifying `AreaCalculator`.
- Risk of breaking Rectangle logic when adding Triangle.
- The class is never "done" — it grows forever with if/else chains.

---

### Good Example (Follows OCP)

```java
import java.util.List;

// 1. Define an interface (Contract) — this is the stable abstraction
interface Shape {
    double calculateArea();
}


// 2. Each shape owns its own calculation logic
class Rectangle implements Shape {
    private final double length;
    private final double width;

    public Rectangle(double length, double width) {
        this.length = length;
        this.width = width;
    }

    @Override
    public double calculateArea() {
        return length * width;
    }
}


class Circle implements Shape {
    private final double radius;

    public Circle(double radius) {
        this.radius = radius;
    }

    @Override
    public double calculateArea() {
        return Math.PI * radius * radius;
    }
}


// 3. Adding Triangle = zero changes to AreaCalculator
class Triangle implements Shape {
    private final double base;
    private final double height;

    public Triangle(double base, double height) {
        this.base = base;
        this.height = height;
    }

    @Override
    public double calculateArea() {
        return 0.5 * base * height;
    }
}


// 4. AreaCalculator never needs to change
class AreaCalculator {
    public double calculateTotalArea(List<Shape> shapes) {
        return shapes.stream().mapToDouble(Shape::calculateArea).sum(); // Polymorphism does the work
    }
}
```

**Benefits:**
- Add `Hexagon`, `Polygon`, `Ellipse` without touching `AreaCalculator`.
- `AreaCalculator` is **Closed for Modification**.
- `Shape` hierarchy is **Open for Extension**.

---

## Real-World Example: Notification Service

### Bad

```java
class NotificationSender {
    public void send(String type, String message) {
        if (type.equals("email")) {
            // SMTP logic
        } else if (type.equals("sms")) {
            // Twilio logic
        } else if (type.equals("push")) {
            // FCM logic — had to modify this class to add push!
        }
        // Slack? Webhook? Modify again...
    }
}
```

### Good

```java
interface NotificationChannel {
    void send(String message);
}


class EmailChannel implements NotificationChannel {
    @Override
    public void send(String message) {
        // SMTP logic
    }
}


class SMSChannel implements NotificationChannel {
    @Override
    public void send(String message) {
        // Twilio logic
    }
}


class PushChannel implements NotificationChannel {
    @Override
    public void send(String message) {
        // FCM logic
    }
}


// New channel? Just add a new class. NotificationSender never changes.
class SlackChannel implements NotificationChannel {
    @Override
    public void send(String message) {
        // Slack webhook logic
    }
}


class NotificationSender {
    private final NotificationChannel channel;

    public NotificationSender(NotificationChannel channel) {
        this.channel = channel;
    }

    public void notify(String message) {
        channel.send(message); // Closed for modification
    }
}
```

---

## How to Apply OCP

1. **Use Interfaces / Abstract Classes**: Define a stable contract.
2. **Use Polymorphism**: Let subclasses handle specific behavior.
3. **Dependency Injection**: Inject implementations at runtime.
4. **Design Patterns that naturally enforce OCP**:
   - **Strategy Pattern**: Swap algorithms without modifying the context.
   - **Decorator Pattern**: Add behavior dynamically without modifying the component.
   - **Factory Pattern**: Create objects without coupling to concrete types.

---

## When to Use in Interviews

- When designing a payment system: "We'd define a `PaymentProcessor` interface. Each provider (Stripe, PayPal, Razorpay) implements it. Adding a new provider = new class, no modifications."
- When designing a discount/pricing engine: Don't write `if (type == "SUMMER") ... else if (type == "STUDENT") ...`. Use a `DiscountStrategy` interface.
- Plugin architectures are the ultimate OCP example: VS Code is closed for modification but open for extension via its extensions API.

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Long if/else or switch on type | `if (type == "X") ... else if (type == "Y")` | Use polymorphism + interface |
| Modify existing class to add new feature | PR changes core class for every new case | Extract abstract type, add new implementor |
| Strategy embedded in client | Business rule logic inside the caller | Extract to Strategy interface |

---

## Pros & Cons

**Pros:**
- Stable, tested code stays untouched when adding features
- Reduces regression risk
- Forces clean abstraction design upfront

**Cons:**
- Requires predicting the right abstraction points (hard to get right the first time)
- Can over-abstract too early (YAGNI conflict)
- Can't be 100% closed — if the core interface changes, you must modify code

---

## Interview Tips

**Q: "Can you be 100% closed for modification?"**
- "No. If the core logic or interface changes (e.g., `calculateArea` needs a parameter), you have to modify code. OCP minimizes modification, it doesn't eliminate it."

**Q: "How does OCP relate to plugins?"**
- "Plugins are the ultimate OCP example. An IDE (like VS Code) is closed for modification but open for extension via its extensions API."

---

## Applied In

This concept is used by **22 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Parking Lot](../05-problems/01-core-problems/01-design-parking-lot.md)
- [Design a Rate Limiter](../05-problems/01-core-problems/02-design-rate-limiter.md)
- [Design Tic-Tac-Toe](../05-problems/01-core-problems/03-design-tic-tac-toe.md)
- [Design a Vending Machine](../05-problems/01-core-problems/04-design-vending-machine.md)
- [Design Splitwise](../05-problems/01-core-problems/05-design-splitwise.md)
- [Design Chess](../05-problems/02-frequent-problems/07-design-chess.md)
- [Design Snake and Ladder](../05-problems/02-frequent-problems/08-design-snake-and-ladder.md)
- [Design Elevator System](../05-problems/02-frequent-problems/09-design-elevator-system.md)
- …and 14 more

