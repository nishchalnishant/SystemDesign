> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Interface Segregation Principle (ISP) — clients should not be forced to depend on methods they do not use.
>
> **Key concepts:**
> - The problem: "Fat" interfaces. A `Worker` interface with `work()`, `eat()`, and `sleep()`. A `Robot` class implements `Worker` but has to leave `eat()` and `sleep()` empty or throw exceptions.
> - The symptom: classes implementing interfaces with dummy methods or returning `null` just to satisfy the compiler. This leads directly to LSP violations.
> - The fix: split the fat interface into smaller, highly cohesive, role-specific interfaces (`Workable`, `Eatable`, `Sleepable`).
> - The result: `Human` implements all three. `Robot` implements only `Workable`. Neither class is forced to implement methods it doesn't need.
> - Client-centric design: interfaces should be designed based on what the *caller* needs, not what the *implementer* happens to do.
>
> **Key takeaway:** ISP is SRP for interfaces. Instead of one massive `MultiFunctionPrinter` interface, create `Printer`, `Scanner`, and `Fax` interfaces. Classes can implement multiple interfaces if they support multiple roles.

---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---
# Interface Segregation Principle (ISP)

**Question**: You create a `Worker` interface with `work()`, `eat()`, and `sleep()`. A `Robot` class implements `Worker`. What does `robot.eat()` do?

**Problem without ISP**: `Robot` is forced to implement `eat()` and `sleep()` even though robots neither eat nor sleep. The only options are: throw `UnsupportedOperationException` (breaking LSP), or return silently (a lie — the method looks callable but does nothing). Any class that holds a `Worker` reference now cannot trust that `eat()` actually works.

**Minimal fix**: Split `Worker` into `Workable`, `Eatable`, and `Sleepable`. `Robot` implements `Workable` only. `HumanEmployee` implements all three. Each class depends only on what it uses.

**Full principle**: ISP — a client should not be forced to depend on methods it does not use. Fat interfaces force implementers to stub out methods they cannot honor, producing the same kind of contract violation as LSP. Prefer many small, focused interfaces over one large general one.

> **Analogy**: A TV remote with 200 buttons. You only use 10. Why do you have to implement 190 methods that throw `UnsupportedOperationException`? Give people only the buttons they need.

---

## Topic Mindmap

```
[Interface Segregation Principle]
├── Problem It Solves
│   ├── Worker interface has work() + eat() + sleep()
│   ├── Robot must implement eat() and sleep() — can't meaningfully do so
│   └── Options: throw UnsupportedOperationException (breaks LSP) or lie silently
├── Core Rule
│   ├── Client should not be forced to depend on methods it does not use
│   ├── Prefer many small focused interfaces over one large fat interface
│   └── If implementing a method requires throwing — interface is too fat
├── The Fix Pattern
│   ├── Workable: work() — Robot + HumanEmployee implement
│   ├── Eatable: eat() — HumanEmployee only
│   ├── Sleepable: sleep() — HumanEmployee only
│   └── Robot implements Workable only — no stubs, no lies
├── Printer Example
│   ├── MultiFunctionDevice: print() + scan() + fax()
│   ├── BasicPrinter cannot scan or fax — forced to stub
│   ├── Fix: Printable, Scannable, Faxable interfaces
│   └── BasicPrinter: Printable only; EnterprisePrinter: all three
├── Identifying Violations
│   ├── Implementation throws UnsupportedOperationException
│   ├── Implementation returns null/empty/no-op silently
│   └── Callers must instanceof-check before calling interface methods
├── ISP vs SRP
│   ├── SRP: one class has one reason to change
│   ├── ISP: one interface serves one client's need
│   └── Both reduce coupling, but at different levels
├── Trade-offs
│   ├── Too many tiny interfaces: hard to discover and compose
│   ├── Interface proliferation: compose interfaces with extends when needed
│   └── Balance: split when two implementers diverge in what they can do
└── Interview Angles
    ├── How does ISP prevent LSP violations?
    ├── What is a "fat" interface and how do you detect one?
    └── How do you avoid interface explosion while applying ISP?
```

## The Core Idea

Instead of one large, "fat" interface, break it into smaller, focused interfaces. Classes implement only what they actually need. This prevents classes from being burdened with methods they cannot meaningfully support.

**Simple test**: Does any class implement an interface method only to throw `UnsupportedOperationException` or leave it empty? That's ISP violation.

---

## Bad Design (Violates ISP)

```java
interface Worker {
    void work();
    void eat();
    void sleep();
}


class HumanWorker implements Worker {
    @Override
    public void work() { System.out.println("Human working..."); }
    @Override
    public void eat() { System.out.println("Human eating..."); }
    @Override
    public void sleep() { System.out.println("Human sleeping..."); }
}


class Robot implements Worker {
    @Override
    public void work() {
        System.out.println("Robot working...");
    }

    @Override
    public void eat() {
        throw new UnsupportedOperationException("Robots don't eat");
        // Forced to implement something that doesn't apply
    }

    @Override
    public void sleep() {
        throw new UnsupportedOperationException("Robots don't sleep");
        // Forced again
    }
}
```

**Problems:**
- `Robot` is forced to implement `eat()` and `sleep()` even though robots do neither.
- The interface is a lie — it claims all workers eat and sleep, but that's false.
- Any code calling `worker.eat()` on a Robot gets a runtime exception — a silent contract breach.

---

## Good Design (Follows ISP)

```java
// Segregated interfaces — each interface does ONE thing
interface Workable {
    void work();
}


interface Eatable {
    void eat();
}


interface Sleepable {
    void sleep();
}


// Human needs all three
class HumanWorker implements Workable, Eatable, Sleepable {
    @Override
    public void work() { System.out.println("Human working..."); }
    @Override
    public void eat() { System.out.println("Human eating..."); }
    @Override
    public void sleep() { System.out.println("Human sleeping..."); }
}


// Robot only implements what applies
class Robot implements Workable {
    @Override
    public void work() {
        System.out.println("Robot working...");
    }
    // No eat(), no sleep() — and that's correct
}
```

Now `Robot` implements only what it actually supports. No fake methods, no runtime exceptions.

---

## Real-World Example: Printer Interface

```java
// Bad: Fat interface
interface MultiFunctionPrinter {
    void print(Document document);
    void scan(Document document);
    void fax(Document document);
    void staple(Document document);
}


// A basic home printer has to implement fax and staple?
class BasicPrinter implements MultiFunctionPrinter {
    @Override
    public void print(Document document) { /* OK */ }
    @Override
    public void scan(Document document) { /* OK */ }
    @Override
    public void fax(Document document) { throw new UnsupportedOperationException("No fax"); }
    @Override
    public void staple(Document document) { throw new UnsupportedOperationException("No stapler"); }
}
```

```java
// Good: Segregated interfaces
interface Printable {
    void print(Document document);
}

interface Scannable {
    void scan(Document document);
}

interface Faxable {
    void fax(Document document);
}

interface Stapleable {
    void staple(Document document);
}


// Basic printer implements only what it supports
class BasicPrinter implements Printable, Scannable {
    @Override
    public void print(Document document) { /* Print logic */ }
    @Override
    public void scan(Document document) { /* Scan logic */ }
}


// Enterprise printer supports everything
class EnterprisePrinter implements Printable, Scannable, Faxable, Stapleable {
    @Override
    public void print(Document document) { }
    @Override
    public void scan(Document document) { }
    @Override
    public void fax(Document document) { }
    @Override
    public void staple(Document document) { }
}
```

---

## Real-World Example: Order Service

```java
import java.util.List;

// Bad: One giant interface
interface OrderService {
    void placeOrder(Order order);
    void cancelOrder(String orderId);
    void trackOrder(String orderId);
    void generateInvoice(String orderId);
    void applyDiscount(String orderId, double percent);
    void exportToCsv(List<Order> orders);        // Report concern
    void sendConfirmationEmail(Order order);      // Notification concern
}


// Good: Segregated by concern
interface OrderPlacementService {
    void placeOrder(Order order);
}

interface OrderCancellationService {
    void cancelOrder(String orderId);
}

interface OrderTrackingService {
    void trackOrder(String orderId);
}

interface InvoiceService {
    void generateInvoice(String orderId);
}

interface NotificationService {
    void sendConfirmationEmail(Order order);
}

interface ReportService {
    void exportToCsv(List<Order> orders);
}
```

Each service class implements only the interface(s) relevant to it.

---

## When to Use in Interviews

- When designing service interfaces: "Rather than one `UserService` with 15 methods, I'd split by read vs write, or by domain concern — `UserQueryService`, `UserCommandService`, `UserNotificationService`."
- When building plugin or extension systems: "I'd define narrow interfaces so each plugin only depends on the capabilities it actually needs."
- When reviewing legacy code: "If I see `UnsupportedOperationException` in any interface implementation, that's an ISP violation I'd flag immediately."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| Fat interface | One interface with 10+ methods from different concerns | Split by responsibility |
| `UnsupportedOperationException` | Implementing class throws for some methods | Segregate the interface |
| Forced empty implementations | Methods with empty bodies that do nothing | Split; implement only relevant interface |
| God interface in frameworks | One interface every service must implement | Use multiple focused interfaces or abstract base classes |

---

## ISP vs SRP

These feel similar but operate at different levels:
- **SRP** is about classes — a class should have one reason to change.
- **ISP** is about interfaces — a client should not be forced to depend on methods it doesn't use.

They complement each other: ISP keeps interfaces lean, SRP keeps classes focused.

---

## Interview Tips

**Q: "Give an example of ISP violation"**
- "A `Worker` interface with `work()`, `eat()`, and `sleep()`. A `Robot` class forced to implement `eat()` and `sleep()` with `UnsupportedOperationException`. The fix is to split into `Workable`, `Eatable`, `Sleepable` interfaces."

**Q: "How does ISP relate to the Dependency Inversion Principle?"**
- "DIP says depend on abstractions. ISP says those abstractions should be narrow. Together, they keep your dependencies minimal — you depend only on the exact capabilities you need."

---

## Applied In

This concept is used by **8 problems** in this repo:

**Low-Level Design**

- [Design an LRU Cache](../06-problems/02-frequent-problems/13-design-lru-cache.md)
- [Design a Pub-Sub Messaging System](../06-problems/03-domain-specific/23-design-pub-sub.md)
- [Design S3 Object Storage / File System](../06-problems/04-advanced-niche/26-design-s3-object-storage.md)
- [Design Search Engine (Inverted Index)](../06-problems/04-advanced-niche/27-design-search-engine.md)
- [Design HTTP Tunneling Service](../06-problems/04-advanced-niche/30-design-tunneling-service.md)
- [Design Lock-Free Queue](../06-problems/04-advanced-niche/34-design-lock-free-queue.md)
- [Design Concurrent LRU Cache](../06-problems/04-advanced-niche/35-design-concurrent-lru-cache.md)
- [Design High-Contention Counter](../06-problems/04-advanced-niche/36-design-high-contention-counter.md)

