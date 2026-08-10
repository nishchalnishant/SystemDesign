> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The SOLID principles — five design principles that make software designs more understandable, flexible, and maintainable. This is the foundation of Low-Level Design (LLD).
>
> **The 5 Principles:**
> - SRP (Single Responsibility): A class should have one, and only one, reason to change (e.g., separate DB logic from business logic).
> - OCP (Open/Closed): Open for extension, closed for modification. Add features by adding new classes, not editing existing ones (via interfaces).
> - LSP (Liskov Substitution): Subclasses must be substitutable for their base classes without breaking correctness (no `UnsupportedOperationException`).
> - ISP (Interface Segregation): Don't force clients to depend on methods they don't use. Split fat interfaces into smaller, specific ones.
> - DIP (Dependency Inversion): High-level modules shouldn't depend on low-level modules; both should depend on abstractions (interfaces). Inject dependencies.
>
> **Key takeaway:** They build on each other. SRP tells you when a class is too big. OCP tells you how to extend it. LSP ensures inheritance is sound. ISP ensures interfaces are clean. DIP wires it all together safely.

---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---

# SOLID Principles

An SDE-3 level cheat sheet for the SOLID principles — the rule, the intuition, the code smell that signals a violation, and the fix, for each.

---

## 1. SRP: Single Responsibility Principle

**The Rule:** A class should have one, and only one, reason to change.
**The Intuition:** Classes are specialized workers. If you have a class doing math, writing to a database, and formatting a UI, it has three reasons to change.

**The "Smell" (When you are violating it):**
- The class is huge (a "God Class").
- You use the word "AND" when describing what it does (e.g., "This class trains the model *and* saves the results").

**The Implementation Technique:** Extract responsibilities into smaller, distinct classes (nouns/roles) and use an Orchestrator to coordinate them.

**Example:**
- **Bad:** `Order` class calculates the total price, charges the credit card, and sends the receipt email.
- **Good:** `Order` holds the items. `PricingCalculator` does the math. `PaymentProcessor` handles the card. `EmailService` sends the receipt.

---

## 2. OCP: Open/Closed Principle

**The Rule:** Software entities should be open for extension, but closed for modification.
**The Intuition:** You should be able to add a brand new feature to your system *without touching existing, tested, working code*.

**The "Smell":**
- Massive `if / elif / else` chains checking for "types".
- Every time a new requirement comes in (e.g., "Add Apple Pay"), you have to modify the core `Checkout` class.

**The Implementation Technique:** Polymorphism and Interfaces. Program to an abstraction, not a concrete implementation.

**Example:**

Bad:
```java
void exportData(Data data, String format) {
    if (format.equals("csv")) { /* Logic */ }
    else if (format.equals("json")) { /* Logic */ }
}
```

Good:
```java
void exportData(Data data, DataExporter exporter) {
    exporter.export(data); // Just pass in a CsvExporter or JsonExporter
}
```

---

## 3. LSP: Liskov Substitution Principle

**The Rule:** Subtypes must be substitutable for their base types without altering the correctness of the program.
**The Intuition:** A child class cannot break the promises made by the parent class. It must behave in a way the client expects.

**The "Smell":**
- A subclass overrides a method to raise `NotImplementedError`.
- A subclass returns a completely different data type than the parent.
- You have to check the type (`isinstance`) of an object before calling its method.

**The Implementation Technique:** Only use inheritance (`extends`) for true "Is-A" relationships where the child can do *everything* the parent does. Otherwise, use Composition or separate Interfaces.

**Example:**
- **Bad:** `Penguin` inherits from `Bird` but overrides `fly()` to throw an error.
- **Good:** `Bird` has no `fly()` method. `FlyingBird` extends `Bird` and adds `fly()`. `Penguin` just extends `Bird`.

---

## 4. ISP: Interface Segregation Principle

**The Rule:** Clients should not be forced to depend on methods they do not use.
**The Intuition:** Don't create massive, bloated contracts ("Fat Interfaces"). Break them down into smaller, role-specific contracts.

**The "Smell":**
- You implement an interface, but leave half the methods empty or throwing `UnsupportedOperationException`.

**The Implementation Technique:** Create smaller, highly focused interfaces. A single class can implement *multiple* small interfaces if it needs to.

**Example:**
- **Bad:** `IMachine` has `print()`, `scan()`, `fax()`. A `BasicPrinter` implements it, but has to leave `scan()` and `fax()` empty.
- **Good:** Create `IPrinter`, `IScanner`, `IFax`. The `BasicPrinter` only implements `IPrinter`. An `AdvancedCopier` implements all three.

---

## 5. DIP: Dependency Inversion Principle

**The Rule:** High-level modules should not depend on low-level modules. Both should depend on abstractions.
**The Intuition:** Your core business logic (High-Level) shouldn't care about the specific database, API, or framework (Low-Level) being used.

**The "Smell":**
- You are using the `new ClassName()` constructor directly inside another class's constructor.

**The Implementation Technique:** Dependency Injection (passing dependencies via constructor arguments) combined with Interfaces.

**Example:**
- **Bad:** `TradingAgent` creates a `new ZerodhaBroker()` directly inside itself.
- **Good:** `TradingAgent` accepts a `BrokerInterface broker` in its constructor. The `Main` class creates the `ZerodhaBroker` and passes it in.

---

## The SDE-3 Caveat: Over-engineering

In a FAANG interview, you must also know when *not* to use these. SOLID principles are **heuristics, not laws**.
If you are writing a 50-line script that will never change, creating 5 abstract classes violates **KISS** (Keep It Simple, Stupid) and **YAGNI** (You Aren't Gonna Need It). A senior engineer balances SOLID with simplicity.

---

## Bridge to Design Patterns: A Motivating Problem

Now that you have the complete map, let's step back into **STAGE 6: Design Patterns**.

Here is the problem from earlier.

We have a `StockTicker`. When the price changes, it needs to alert an Email Service, a Push Notification Service, and a UI Dashboard.

```java
class StockTicker {
    private String ticker;
    private double price;
    private EmailService emailService;
    private PushService pushService;
    private Dashboard dashboard;

    public StockTicker(String ticker, EmailService emailService, PushService pushService, Dashboard dashboard) {
        this.ticker = ticker;
        this.price = 0;
        this.emailService = emailService;
        this.pushService = pushService;
        this.dashboard = dashboard;
    }

    public void updatePrice(double newPrice) {
        this.price = newPrice;
        emailService.sendEmail("New price: " + newPrice);
        pushService.sendPush("New price: " + newPrice);
        dashboard.refresh(newPrice);
    }
}
```

**The Design Pressure:** We want to add SMS notifications tomorrow, an automated Trading Bot next week, and the ability for users to turn off emails at will.

If we keep passing services into the constructor, this class will violate the Open/Closed Principle heavily.

**Your Challenge:**
How do we redesign `StockTicker` so that it doesn't need to know *who* it is alerting? How can it alert 0, 5, or 100 different services dynamically without its code ever changing?

*Hint: Think about YouTube. The YouTuber (the Publisher) maintains a list of Subscribers. When a video is uploaded, it just iterates through the list.*

---

**Related:** [03-design-patterns/README.md](../03-design-patterns/README.md) — the Observer pattern solves the StockTicker challenge above · [../glossary.md](../glossary.md)

