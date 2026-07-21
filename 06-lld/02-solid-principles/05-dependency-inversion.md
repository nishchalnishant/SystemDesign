> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Dependency Inversion Principle (DIP) — high-level modules should not depend on low-level modules; both should depend on abstractions.
>
> **Key concepts:**
> - The problem: high-level business logic (`OrderService`) instantiating low-level infrastructure (`MySQLDatabase`). If you change to PostgreSQL, you have to rewrite `OrderService`.
> - The symptom: `new` keywords scattered throughout business logic classes for infrastructure or external services. Impossibility of unit testing without real databases/network.
> - The fix: inversion. Define an abstraction (`OrderRepository` interface). `OrderService` depends on the interface. `MySQLDatabase` implements the interface.
> - Dependency Injection (DI): how DIP is implemented. Instead of creating its own dependencies, `OrderService` receives them via its constructor (e.g., from Spring).
> - Testing: DIP makes mocking trivial. Inject a `MockOrderRepository` into `OrderService` to test business logic in isolation.
>
> **Key takeaway:** "Depend on abstractions, not concretions." You should never use `new` inside a high-level class to create a low-level dependency. Pass it in via the constructor as an interface.

---
module: 06-lld
topic: Solid Principles
status: unread
tags: [06-lld, system-design, solid-principles]
---
# Dependency Inversion Principle (DIP)

**Question**: Your `OrderService` calls `new GmailEmailSender()` inside a method to send confirmation emails. The company switches to SendGrid. How many places in `OrderService` do you have to touch? Can you write a unit test for `OrderService` without actually sending emails?

**Problem without DIP**: `OrderService` is hardwired to `GmailEmailSender`. Switching email providers requires modifying `OrderService` — a class that should only care about order business logic. Testing `OrderService` in isolation is impossible because constructing it triggers email-sending infrastructure. The high-level module (`OrderService`) directly depends on the low-level module (`GmailEmailSender`).

**Minimal fix**: Introduce an `EmailSender` interface. `OrderService` takes an `EmailSender` in its constructor. `GmailEmailSender` implements it. For tests, inject a `MockEmailSender`. Switching to SendGrid means creating `SendGridEmailSender` — `OrderService` does not change.

**Full principle**: DIP — High-level modules should not depend on low-level modules. Both should depend on abstractions. This is the principle behind Dependency Injection: you do not `new` your dependencies inside high-level classes. You accept them from outside, typed as interfaces.

> **Analogy**: A power socket standard. Your laptop (high-level module) depends on the AC adapter interface (abstraction), not on the specific wall voltage (low-level detail). Swap countries/voltages without changing the laptop.

---

## Topic Mindmap

```
[Dependency Inversion Principle]
├── Problem It Solves
│   ├── OrderService calls new GmailEmailSender() internally
│   ├── Switch to SendGrid → must modify OrderService (wrong layer)
│   └── Unit test triggers real email infrastructure — untestable
├── Core Rule
│   ├── High-level modules must NOT depend on low-level modules
│   ├── Both must depend on abstractions (interfaces/abstract classes)
│   └── Abstractions must NOT depend on details — details depend on abstractions
├── The Fix Pattern
│   ├── EmailSender interface: send(to, subject, body)
│   ├── GmailEmailSender implements EmailSender
│   ├── OrderService takes EmailSender via constructor injection
│   └── Test: inject MockEmailSender — no real email, no network
├── Application → Database Example
│   ├── Application hardwired to MySQLDatabase = violation
│   ├── Database interface with query/save
│   ├── MySQLDatabase, PostgresDatabase implement Database
│   └── Application constructor takes Database — swap without code change
├── DIP vs Dependency Injection (DI)
│   ├── DIP is the principle (depend on abstractions)
│   ├── DI is the technique (inject dependencies from outside)
│   └── DI frameworks (Spring, Guice) automate DIP wiring
├── Where to Apply
│   ├── Any high-level class that calls new <LowLevelClass>()
│   ├── Services calling specific DB drivers, email clients, HTTP clients
│   └── Anywhere you want to swap implementations or write unit tests
├── Trade-offs
│   ├── More interfaces and wiring code upfront
│   ├── DI container can hide dependencies (magic wiring)
│   └── Constructor injection preferred — dependencies explicit, testable
└── Interview Angles
    ├── What is the difference between DIP and Dependency Injection?
    ├── How does DIP enable unit testing?
    └── What is an inversion of control (IoC) container?
```

## The Core Idea

When high-level business logic is directly wired to low-level implementation details (specific database, specific email provider, specific file system), every change to the details breaks the business logic. DIP inverts this: both depend on an interface in the middle.

**Simple test**: Does your service class contain `new MySQLDatabase()` or `new GmailService()` inside it? That's DIP violation — you've wired the high-level module to a specific low-level detail.

---

## Bad Design (Violates DIP)

```python
class MySQLDatabase:
    def connect(self):
        print("Connecting to MySQL...")

    def save(self, data: str):
        print(f"Saving to MySQL: {data}")


class Application:
    def __init__(self):
        self._database = MySQLDatabase()  # Hard dependency on concrete class

    def start(self):
        self._database.connect()

    def save_data(self, data: str):
        self._database.save(data)  # Can't swap to PostgreSQL without changing Application
```

**Problems:**
- `Application` is tightly coupled to `MySQLDatabase`.
- To switch to PostgreSQL, MongoDB, or an in-memory DB for testing — you must modify `Application`.
- Unit testing `Application` requires a real MySQL database.
- The high-level business logic (Application) knows about the low-level detail (MySQL).

---

## Good Design (Follows DIP)

```python
from abc import ABC, abstractmethod


# 1. Define the abstraction (the "socket standard")
class Database(ABC):
    @abstractmethod
    def connect(self): pass

    @abstractmethod
    def save(self, data: str): pass


# 2. Low-level modules depend on the abstraction
class MySQLDatabase(Database):
    def connect(self):
        print("Connecting to MySQL...")

    def save(self, data: str):
        print(f"Saving to MySQL: {data}")


class PostgreSQLDatabase(Database):
    def connect(self):
        print("Connecting to PostgreSQL...")

    def save(self, data: str):
        print(f"Saving to PostgreSQL: {data}")


# For tests: in-memory implementation
class InMemoryDatabase(Database):
    def __init__(self):
        self._storage: list[str] = []

    def connect(self):
        pass  # No-op for in-memory

    def save(self, data: str):
        self._storage.append(data)
        print(f"Saved to memory: {data}")


# 3. High-level module depends only on the abstraction
class Application:
    # Dependency is INJECTED — not created internally
    def __init__(self, database: Database):
        self._database = database

    def start(self):
        self._database.connect()

    def save_data(self, data: str):
        self._database.save(data)


# 4. Wiring happens at the composition root (main, DI container, etc.)
if __name__ == "__main__":
    # Production: use MySQL
    app = Application(MySQLDatabase())
    app.start()

    # Test: use in-memory
    test_app = Application(InMemoryDatabase())
    test_app.save_data("test-record")
```

---

## Real-World Example: Notification Service

```python
from abc import ABC, abstractmethod


# Bad: Hardwired to Gmail
class OrderServiceBad:
    def __init__(self):
        self._email_sender = GmailEmailSender()

    def place_order(self, order):
        # Process order...
        self._email_sender.send(order.email, "Order confirmed")
        # Now we're stuck on Gmail forever


# Good: Depends on abstraction
class EmailSender(ABC):
    @abstractmethod
    def send(self, to: str, message: str): pass


class GmailEmailSender(EmailSender):
    def send(self, to: str, message: str):
        pass  # Gmail SMTP


class SendGridEmailSender(EmailSender):
    def send(self, to: str, message: str):
        pass  # SendGrid API


class OrderService:
    def __init__(self, email_sender: EmailSender):
        self._email_sender = email_sender

    def place_order(self, order):
        # Process order...
        self._email_sender.send(order.email, "Order confirmed")
        # Switch to SendGrid? No change here. Just inject a different implementation.
```

---

## DIP vs Dependency Injection

These are related but distinct:
- **DIP** is a principle: depend on abstractions, not concretions.
- **Dependency Injection** is a technique to implement DIP: pass dependencies from outside rather than creating them inside.

You can violate DIP even while using DI (if you inject concrete classes instead of interfaces).

```python
# DI without DIP — still wrong
class Application:
    def __init__(self, database: MySQLDatabase):  # Injecting concrete class
        self._database = database

# DI with DIP — correct
class Application:
    def __init__(self, database: Database):  # Injecting abstraction
        self._database = database
```

---

## When to Use in Interviews

- When designing any service that talks to external systems: "I'd define an interface for the database, email sender, payment gateway, etc. The service depends on the interface. Implementations are injected."
- When discussing testability: "With DIP, I can inject a mock/fake in tests without touching the production code."
- When designing frameworks: "The framework defines interfaces. The user provides implementations. Spring, for example, is built entirely around this idea."

---

## Common Violations

| Violation | Symptom | Fix |
|---|---|---|
| `new ConcreteClass()` inside service | Hard dependency created internally | Inject via constructor |
| Importing specific vendor class in business logic | `import com.mysql.MySQLDriver` in OrderService | Depend on `Database` interface |
| Static method calls to infrastructure | `EmailService.send(...)` static call | Define `EmailSender` interface; inject instance |
| Config hardcoded in business class | Database URL or API key in service constructor | Extract to config; inject dependencies |

---

## Pros of DIP

- High-level business logic is isolated from infrastructure details
- Easy to swap implementations (DB, email, payment) without touching business logic
- Unit testing is simple — inject a mock/fake
- Follows Open/Closed Principle at the module level

---

## Interview Tips

**Q: "Explain DIP with an example"**
- "An `OrderService` should not directly instantiate `MySQLDatabase`. Instead, it should depend on a `Database` interface. The concrete implementation is injected from outside — usually via constructor injection. This lets me swap MySQL for PostgreSQL or an in-memory DB for tests without touching `OrderService`."

**Q: "What's the relationship between DIP and Spring Framework?"**
- "Spring's IoC container implements DIP at the framework level. You define interfaces; Spring injects the correct implementation at runtime based on configuration. Your beans never call `new` on their dependencies."

---

## Applied In

This concept is used by **12 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Rate Limiter](../05-problems/01-core-problems/02-design-rate-limiter.md)
- [Design BookMyShow](../05-problems/02-frequent-problems/06-design-bookmyshow.md)
- [Design a Food Delivery System](../05-problems/02-frequent-problems/14-design-food-delivery.md)
- [Design Notification System](../05-problems/02-frequent-problems/16-design-notification-system.md)
- [Design Mentorship Platform](../05-problems/03-domain-specific/18-design-mentorship-platform.md)
- [Design a Logger Library](../05-problems/03-domain-specific/19-design-logger-library.md)
- [Design a Ride Sharing System](../05-problems/03-domain-specific/22-design-ride-sharing.md)
- [Design a Pub-Sub Messaging System](../05-problems/03-domain-specific/23-design-pub-sub.md)
- …and 4 more

