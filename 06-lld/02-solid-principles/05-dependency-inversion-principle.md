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

    def save(self, data):
        print(f"Saving to MySQL: {data}")

class Application:
    def __init__(self):
        self._database = MySQLDatabase()  # Hard dependency on concrete class

    def start(self):
        self._database.connect()

    def save_data(self, data):
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
    def connect(self): ...

    @abstractmethod
    def save(self, data): ...

# 2. Low-level modules depend on the abstraction
class MySQLDatabase(Database):
    def connect(self):
        print("Connecting to MySQL...")

    def save(self, data):
        print(f"Saving to MySQL: {data}")

class PostgreSQLDatabase(Database):
    def connect(self):
        print("Connecting to PostgreSQL...")

    def save(self, data):
        print(f"Saving to PostgreSQL: {data}")

# For tests: in-memory implementation
class InMemoryDatabase(Database):
    def __init__(self):
        self._storage = []  # list of str

    def connect(self):
        pass  # No-op for in-memory

    def save(self, data):
        self._storage.append(data)
        print(f"Saved to memory: {data}")

# 3. High-level module depends only on the abstraction
class Application:
    # Dependency is INJECTED — not created internally
    def __init__(self, database):
        self._database = database

    def start(self):
        self._database.connect()

    def save_data(self, data):
        self._database.save(data)

# 4. Wiring happens at the composition root
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
class OrderService:
    def __init__(self):
        self._email_sender = GmailEmailSender()

    def place_order(self, order):
        # Process order...
        self._email_sender.send(order.get_email(), "Order confirmed")
        # Now we're stuck on Gmail forever

# Good: Depends on abstraction
class EmailSender(ABC):
    @abstractmethod
    def send(self, to, message): ...

class GmailEmailSender(EmailSender):
    def send(self, to, message): ...  # Gmail SMTP

class SendGridEmailSender(EmailSender):
    def send(self, to, message): ...  # SendGrid API

class OrderService:
    def __init__(self, email_sender):
        self._email_sender = email_sender

    def place_order(self, order):
        # Process order...
        self._email_sender.send(order.get_email(), "Order confirmed")
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
    def __init__(self, database):  # injecting concrete MySQLDatabase — still wrong
        self._database = database

# DI with DIP — correct
class Application:
    def __init__(self, database):  # injecting Database abstraction
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

## Interviewer Follow-Up Questions

- "What does DIP say? Explain both levels." → (1) High-level modules should not depend on low-level modules. Both should depend on abstractions. (2) Abstractions should not depend on details. Details should depend on abstractions. Intuition: your `OrderService` (high-level business logic) should not import `MySQLOrderRepository` directly. Both should depend on `OrderRepository` (interface). If you swap MySQL for Postgres, `OrderService` is untouched.
- "How does DIP enable unit testing?" → Without DIP: `OrderService` instantiates `MySQLOrderRepository` internally — you can't test `OrderService` without a live MySQL DB. With DIP: `OrderService.__init__(self, repo: OrderRepository)` — in tests, inject a `FakeOrderRepository` (in-memory dict). The service is fully testable without any external dependencies. DIP is the prerequisite for dependency injection, which is the prerequisite for unit testability.
- "What's the difference between DIP and Dependency Injection?" → DIP is a design principle (the rule). Dependency Injection is the pattern that implements it (the mechanism). DIP says "depend on abstractions." DI says "pass dependencies from the outside rather than creating them inside." You can violate DIP while using a DI framework (injecting a concrete class instead of an interface). You can follow DIP without a DI framework (manually passing constructor arguments).
- "Show me how you'd refactor code that violates DIP." → Before: `class OrderService: def __init__(self): self.repo = MySQLOrderRepository(); self.mailer = SMTPMailer()`. After: `class OrderService: def __init__(self, repo: OrderRepository, mailer: Mailer): self.repo = repo; self.mailer = mailer`. The service no longer knows which DB or email service is used — the caller decides. For production: inject real implementations. For tests: inject fakes. The class is now decoupled from infrastructure.
- "What's the risk of applying DIP everywhere?" → Over-abstraction: every string becomes a `StringProvider`, every boolean an `Enabled` interface. The codebase becomes full of single-implementation interfaces that add indirection without benefit. Rule of thumb: add the interface abstraction when there's (1) a second implementation (test fake counts), or (2) a predictable variation point. Don't abstract until you need to.
