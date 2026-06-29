---
module: 07-interview-templates
topic: LLD Interview Question Bank
tags: [lld, interview, oop, solid, design-patterns, concurrency]
---
# LLD Interview Question Bank

> Questions an Amazon SDE-2 interviewer actually asks during a low-level design session.
> Format: **question → answer you should give out loud in ≤ 60 seconds.**
> Each answer follows: **What it is → Why it matters → Concrete example → Trade-off or Amazon angle.**

---

## Quick Reference Index

| Topic | Questions |
|---|---|
| OOP Fundamentals | Q1–Q6 |
| SOLID Principles | Q7–Q11 |
| Design Patterns | Q12–Q20 |
| Encapsulation & Abstraction | Q21–Q24 |
| Concurrency | Q25–Q30 |
| Class & API Design | Q31–Q34 |
| LLD Problem Follow-ups | Q35–Q40 |
| Python-Specific LLD | Q41–Q44 |
| Error Handling & Defensive Design | Q45–Q50 |
| Testing in LLD | Q51–Q55 |
| Refactoring & Code Smells | Q56–Q59 |
| Data Structures in LLD | Q60–Q64 |
| Object Design Walkthroughs | Q65–Q68 |
| Structural & Behavioral Patterns | Q69–Q73 |
| Amazon-Specific LLD Problems | Q74–Q78 |
| Concurrency Deep-Dives | Q79–Q82 |

---

## 1. OOP Fundamentals

---

**Q1: What are the four pillars of OOP and give a one-line use case for each?**

- **Encapsulation**: `BankAccount` keeps `_balance` private and exposes `debit(amount)` — the method enforces the no-negative-balance invariant so callers can't corrupt the object.
- **Abstraction**: `PaymentGateway` interface hides whether you're calling Stripe or Braintree — callers write `gateway.charge(amount)` without knowing the vendor.
- **Inheritance**: `Animal → Dog` shares locomotion logic so subclasses don't duplicate code.
- **Polymorphism**: `shape.area()` calls the right formula whether shape is a `Circle` or `Rectangle` — the caller never needs to know.

---

**Q2: What is the difference between method overloading and method overriding?**

- **Overloading**: same method name, different parameter signatures, resolved at *compile time*. Python doesn't support it natively — use default arguments or `*args` instead.
- **Overriding**: a subclass redefines a parent method, resolved at *runtime* via dynamic dispatch. This is the mechanism that makes polymorphism work.

One is a compile-time trick for convenience; the other is a runtime mechanism for correctness.

---

**Q3: When does inheritance break and what do you do?**

Inheritance breaks when:
1. The subclass must suppress or throw on a parent method (`Penguin` can't `fly()` — LSP violated).
2. The hierarchy is 4+ levels deep — the fragile base class problem: changes to the base ripple unpredictably.
3. You need to change behavior at runtime — inheritance is static.

**Fix**: flatten with composition, use Strategy/Decorator, or split into smaller interfaces. Inheritance couples you to the parent's internals; composition couples you only to an interface.

---

**Q4: What is duck typing and how does Python use it?**

If it walks like a duck and quacks like a duck, it's a duck. Python checks for the presence of methods at runtime, not the declared type. `for x in obj` works on anything with `__iter__`, regardless of class hierarchy.

This gives you polymorphism without inheritance — just implement the right methods. **Downside**: errors surface at runtime, not at class definition. Use `Protocol` (typing module) for static duck typing checks.

---

**Q5: What is the difference between `__str__` and `__repr__` in Python?**

- `__repr__`: unambiguous string for developers — used in the REPL and `repr()`. Should be valid Python to recreate the object: `Point(x=1, y=2)`.
- `__str__`: human-readable string for end users — used in `print()` and `str()`. If only `__repr__` is defined, `str()` falls back to it.

**Rule**: always define `__repr__`; define `__str__` only if you want a different user-facing format.

---

**Q6: What is method resolution order (MRO) in Python?**

The order Python searches classes when looking up a method in multiple inheritance. Computed using C3 linearization: left-to-right, depth-first, each class appears once. Check it: `ClassName.__mro__`.

**Matters when**: two parent classes define the same method — MRO determines which one is called. `super()` follows MRO, so cooperative multiple inheritance works correctly as long as all classes also use `super()`.

---

## 2. SOLID Principles

---

**Q7: Give a concrete example of a Single Responsibility violation.**

`UserService` that: validates email format, hashes passwords, sends a welcome email, writes to the DB, and logs the event. That's five reasons to change — email format rules change, the email provider changes, the logging format changes, etc.

**Fix**: `UserValidator`, `PasswordHasher`, `EmailSender`, `UserRepository`. SRP doesn't mean one method per class; it means **one reason to change**.

---

**Q8: How do you apply OCP without rewriting existing code when adding a new payment method?**

Define `PaymentProcessor` as an abstract base class with `charge(amount) -> Receipt`. Each payment method is a concrete subclass: `StripeProcessor`, `PayPalProcessor`. Adding `ApplePayProcessor` = one new class, zero modification to existing code.

The `if/elif payment_type == "stripe":` block is the OCP violation — replace it with polymorphic dispatch via a registry or factory.

---

**Q9: What is the Interface Segregation Principle and when does violating it hurt?**

Clients should not depend on interfaces they don't use.

**Violation**: a `Worker` interface with `work()`, `eat()`, `sleep()` — a `RobotWorker` must implement `eat()` and `sleep()` even though robots don't eat. Fix: split into `Workable` and `Eatable`.

**Why it hurts**: violating ISP forces classes to implement meaningless no-ops, and a change to `eat()` forces a recompile and retest of all `Worker` implementations — including the ones that don't eat.

---

**Q10: Explain the Dependency Inversion Principle with a concrete example.**

`OrderService` should not instantiate `MySQLOrderRepository` — it should depend on an `OrderRepository` *interface*, with the concrete `MySQLOrderRepository` injected at construction time.

High-level policy (`OrderService`) depends on abstraction. Low-level detail (`MySQLOrderRepository`) also depends on abstraction. **Result**: you can swap MySQL for Postgres, or inject a `FakeRepository` in tests, without touching `OrderService`.

---

**Q11: You have a 5-level class hierarchy. Which SOLID principle is most at risk?**

**LSP (Liskov Substitution)**. Deep hierarchies accumulate overrides, and a subclass 4 levels down can violate a contract established in the base class — throwing unexpected exceptions, weakening postconditions, or strengthening preconditions. Also risks SRP — deep hierarchies tend to mix concerns.

**Red flag**: if you see `isinstance` checks to handle different subclasses differently, LSP is already broken.

---

## 3. Design Patterns

---

**Q12: When do you use Factory Method vs Abstract Factory?**

- **Factory Method**: one product, subclasses decide which concrete class to create. `Dialog.create_button()` — `WindowsDialog` returns `WindowsButton`, `MacDialog` returns `MacButton`.
- **Abstract Factory**: a family of *related* products that must be used together consistently. `GUIFactory` creates `Button` + `Checkbox` + `TextInput` that all match the same theme.

Use Abstract Factory when products must be consistent with each other.

---

**Q13: What problem does the Builder pattern solve and when is it overkill?**

Solves the **telescoping constructor** problem: `User(name, email, age, phone, address, role, avatar, ...)` — too many parameters, impossible to call correctly in the right order.

**Builder**: `UserBuilder().name("Alice").email("a@b.com").role(ADMIN).build()` — explicit, readable, optional fields have sensible defaults.

**Overkill when**: the object has 2–3 fields (just use a constructor or dataclass), or all fields are required (named keyword arguments are fine). Use Builder for objects with many optional fields or complex construction order.

---

**Q14: Explain the Observer pattern and its trade-offs.**

Subject maintains a list of observers; on state change, calls `notify()` on all of them.

**Example**: `OrderService` notifies `InventoryService`, `EmailService`, `AnalyticsService` on order creation.

**Trade-offs**: observers are decoupled from the subject (good), but you lose explicit control flow — a bug in one observer silently affects all others, notification order is implicit, and memory leaks occur if observers are never unregistered. **Prefer an event bus** for large fan-out; it handles failure isolation and async delivery.

---

**Q15: What is the Decorator pattern? Give an example.**

Wraps an object to add behavior *without modifying its class*.

**Example**: `Coffee → MilkDecorator(coffee) → SugarDecorator(milk_coffee)` — each wrapper adds to `cost()` and `description()`. Compare to inheritance: you'd need `MilkCoffee`, `SugarCoffee`, `MilkSugarCoffee` as separate subclasses.

Decorator composes at runtime; inheritance is static. Python's `@functools.wraps` and HTTP middleware stacks are Decorator in practice.

---

**Q16: Singleton pattern — when is it appropriate and when is it a problem?**

**Appropriate**: logger, config, thread pool, DB connection pool — things that should have exactly one instance and are expensive to create.

**Problem**: it's global mutable state — makes tests non-deterministic (one test modifies the singleton, affects the next), creates hidden coupling (callers don't declare the dependency), hard to swap implementations. In Python, module-level objects are effectively singletons — prefer those over the `__new__` trick.

---

**Q17: What is the Strategy pattern and how does it differ from using if/elif?**

Strategy: extract the varying algorithm into a separate class, inject it.

```python
class Sorter:
    def __init__(self, strategy): self._strategy = strategy
    def sort(self, data): return self._strategy.sort(data)

sorter = Sorter(strategy=QuickSort())
```

**vs if/elif**: adding a new sort requires modifying existing code (OCP violation), logic is harder to test in isolation, and runtime swapping requires `if/elif` everywhere. Strategy adds a new class instead of modifying existing ones, and enables runtime algorithm swapping.

---

**Q18: What is the difference between Strategy and Template Method?**

- **Template Method**: base class defines the skeleton of an algorithm; subclasses fill in specific steps via override. Structure is fixed in base; behavior varies in subclasses. Uses *inheritance*.
- **Strategy**: the entire algorithm is extracted into an injectable object. Both structure and behavior vary. Uses *composition*.

If you need to vary only a few steps, Template Method is simpler. If you need to vary the whole algorithm or swap at runtime, use Strategy.

---

**Q19: When do you use the Command pattern?**

When you need any of these four capabilities:
1. **Undo/redo**: each command stores state to reverse itself.
2. **Queueing**: commands are objects that can be stored and replayed.
3. **Audit logging**: log every command that mutated state.
4. **Macro recording**: replay a sequence of commands.

Classic example: text editor — each keystroke is a `Command` with `execute()` and `undo()`. Adds indirection and complexity; not worth it unless you need one of those four.

---

**Q20: What is the Proxy pattern and what are its three common uses?**

A proxy sits in front of a real object and intercepts calls.

1. **Virtual Proxy**: lazy initialization — don't load a heavy resource until first access.
2. **Protection Proxy**: access control — check permissions before forwarding.
3. **Remote Proxy**: make a remote object look local — a gRPC stub is a Remote Proxy.

**Proxy vs Decorator**: Proxy controls *access* to an object; Decorator *adds behavior* to an object.

---

## 4. Encapsulation & Abstraction

---

**Q21: What is the "Tell, Don't Ask" principle? Show a before/after.**

- **Ask (bad)**: `if account.get_balance() >= amount: account.set_balance(account.get_balance() - amount)` — leaks internal state, not thread-safe, scatters business rules across callers.
- **Tell (good)**: `account.debit(amount)` — the `BankAccount` enforces its own invariant internally.

Any time you call a getter and then conditionally call a setter, you're violating this principle. Move the decision inside the object.

---

**Q22: What is the Law of Demeter?**

A method should only call methods on: itself, its direct fields, objects it creates, objects passed as arguments. "Don't talk to strangers."

- **Bad**: `order.getCustomer().getAddress().getCity()` — three levels of traversal, tight coupling across the entire chain.
- **Good**: `order.getCustomerCity()` — `Order` encapsulates the traversal.

**Why it matters**: if `Customer` changes to store addresses differently, every caller of the three-level chain breaks. The single-level call is insulated.

---

**Q23: How do you design a class so that it's impossible to construct in an invalid state?**

1. Validate all required fields in the constructor; throw if invalid.
2. Make required fields private/final — no public setters for invariants.
3. Use factory methods for complex invariants: `EmailAddress.of("user@example.com")` — returns a valid object or throws.
4. Use a Builder for objects with many optional fields.

**Never** allow a no-arg constructor that leaves required fields as `None`.

---

**Q24: What is immutability and when do you enforce it?**

An immutable object cannot change after construction — all fields set in `__init__`, no setters.

**Benefits**: inherently thread-safe (no synchronization needed), safe to share and cache, no defensive copies needed.

**Enforce for**: value objects (`Money`, `EmailAddress`, `DateRange`), dict/set keys, objects passed across thread boundaries.

In Python: `@dataclass(frozen=True)` or `namedtuple`. **Cost**: creating a new object for every mutation — not appropriate for frequently mutated state.

---

## 5. Concurrency in LLD

---

**Q25: You have a shared counter incremented by 100 threads. Write thread-safe Python.**

```python
import threading
counter = 0
lock = threading.Lock()

def increment():
    global counter
    with lock:
        counter += 1
```

**Why the lock is necessary**: `counter += 1` compiles to three bytecodes — LOAD, ADD, STORE. Another thread can interleave between LOAD and STORE, causing lost updates. The GIL does not protect compound operations.

---

**Q26: What is a deadlock and what are the four conditions required for it?**

Two or more threads waiting for each other's locks indefinitely. Coffman's four necessary conditions:

1. **Mutual exclusion**: only one thread holds a resource at a time.
2. **Hold and wait**: a thread holds a lock while waiting for another.
3. **No preemption**: locks can't be forcibly taken.
4. **Circular wait**: thread A waits for B, B waits for A.

Break *any one* condition to prevent deadlock. **Simplest fix**: always acquire locks in the same global order.

---

**Q27: What is a thread-safe singleton in Python?**

```python
import threading
_instance = None
_lock = threading.Lock()

def get_instance():
    global _instance
    if _instance is None:          # fast path — no lock needed once initialized
        with _lock:
            if _instance is None:  # double-checked: recheck after acquiring lock
                _instance = Config()
    return _instance
```

The double-check avoids acquiring the lock on every call after initialization. The outer check is the fast path; the inner check prevents a race between two threads that both saw `None` before the lock.

---

**Q28: When do you use `threading` vs `multiprocessing` in Python?**

| Workload | Module | Why |
|---|---|---|
| I/O-bound (network, DB, file) | `threading` | Threads release GIL during I/O — true parallelism |
| CPU-bound (hashing, ML, encoding) | `multiprocessing` | Each process has its own GIL — all cores used |
| I/O-bound, very high concurrency (10K+ connections) | `asyncio` | Single thread, cooperative multitasking, no context-switch overhead |

---

**Q29: What is a race condition in the context of a cache?**

Check-then-act:
```python
if key not in cache:
    cache[key] = db.query(key)  # two threads can both reach here
```

Two threads both see the key absent, both query the DB, both write — the second write silently overwrites the first (expensive), or worse, one reads a partially-written value.

**Fix**: `cache.setdefault(key, value)` is atomic in CPython for dicts, or use a lock around the check-and-set, or a distributed lock for cross-process scenarios.

---

**Q30: What is the producer-consumer pattern and how do you implement it in Python?**

```python
import queue, threading

q = queue.Queue(maxsize=100)   # bounded — backpressure when full

def producer():
    while True:
        item = fetch_next_item()
        q.put(item)            # blocks if queue is full (backpressure)

def consumer():
    while True:
        item = q.get()
        process(item)
        q.task_done()
```

`queue.Queue` is thread-safe. `maxsize` provides backpressure — producers slow down automatically when consumers fall behind. `task_done()` + `q.join()` lets the main thread wait for all items to be processed before exiting.

---

## 6. Class & API Design

---

**Q31: How do you design an API that is hard to misuse?**

1. **Use types to encode constraints**: `PositiveInt` instead of `int`, `EmailAddress` instead of `str` — invalid values can't be constructed.
2. **Avoid boolean flags**: `send_email(urgent=True)` → `send_urgent_email()` — intent is unambiguous.
3. **Require mandatory fields in the constructor** — impossible to construct in an incomplete state.
4. **Name methods after what they do to the *caller***, not internal mechanics.
5. **Use method chaining for optional config**: `builder.timeout(30).retries(3).build()`.

---

**Q32: What is the difference between a value object and an entity?**

- **Entity**: has identity — two `User` objects with the same name are *different* if they have different IDs. Equality by ID. Usually mutable (a user's email can change).
- **Value object**: has no identity — two `Money(100, USD)` objects are *equal* if they have the same amount and currency. Equality by value. Should be immutable.

Examples of value objects: `Money`, `Address`, `EmailAddress`, `DateRange`. They're excellent for domain modeling because immutability makes them safe to share.

---

**Q33: How do you decide whether to use a method or a property?**

- **Property** (`@property`): conceptually *a value the object has* — `user.full_name`, `circle.area`. Should have no observable side effects and be cheap to compute.
- **Method**: when computation is expensive, has side effects, takes arguments, or represents *an action the object performs*.

**Bad**: `user.send_email` as a property — properties must not send emails. **Rule**: if calling it twice gives the same result with no side effects, it can be a property.

---

**Q34: Your class has grown to 500 lines. How do you decide what to extract?**

Look for:
1. **Private helpers that only relate to one sub-concern** → extract to a collaborator class.
2. **Data that clusters together** → extract to a value object or dataclass.
3. **Conditional logic on type** (`isinstance` checks) → extract to polymorphism.
4. **Repeated patterns across multiple methods** → extract to a strategy or utility.

Each extracted class should have a name that says *what it is*, not what it contains — `EmailValidator`, not `EmailUtils`.

---

## 7. LLD Design Problems — Common Follow-ups

---

**Q35: You designed a parking lot. Interviewer asks: how do you handle concurrent booking of the same spot?**

Two approaches:
- **Optimistic locking**: load spot with a `version` field, update with `WHERE id = ? AND version = ? AND status = 'free'`. Zero rows updated = someone else took it — retry with a different spot.
- **Pessimistic locking**: `SELECT FOR UPDATE` on the spot row — use if contention is high and retries are expensive.

For distributed systems: `SET spot:{id} booked NX EX 30` in Redis — atomic, only one caller succeeds, TTL releases the lock automatically on timeout.

---

**Q36: You designed an elevator. Interviewer asks: how does SCAN (disk scheduling) apply?**

SCAN algorithm: elevator moves in one direction, services all pending requests in that direction, then reverses. Implementation: maintain a sorted set of pending floors for the current direction, service them in order before reversing.

**Better than SSTF** (nearest floor): SSTF can starve floors far from the current position. SCAN guarantees maximum wait time is proportional to twice the number of floors — no floor waits forever.

---

**Q37: You designed a rate limiter. Interviewer asks: how do you handle distributed rate limiting?**

Per-server token bucket only limits per-server, not globally. Options:

1. **Centralized Redis**: `INCR ratelimit:{user}:{window}` with `EXPIRE`. Atomic Lua script for check-and-increment. Downside: Redis on every hot-path request.
2. **Local counter + async sync**: each instance maintains a local counter, syncs to Redis every 100ms. Allows brief overages in exchange for no per-request Redis call — acceptable for most APIs.
3. **Token bucket in Redis**: store `(tokens, last_refill_time)` as a hash, refill lazily on each request — supports burst semantics.

---

**Q38: You designed a logging framework. Interviewer asks: what design patterns did you use?**

- **Chain of Responsibility**: each handler (console, file, remote) decides whether to process and whether to pass on.
- **Decorator**: add handlers dynamically without modifying the Logger class.
- **Observer**: Logger notifies all registered handlers on each log event.
- **Singleton**: one Logger instance per name (like Python's `logging.getLogger(__name__)`).
- **Strategy**: log formatting is a strategy — JSON formatter vs plain text formatter.

---

**Q39: You designed a LRU cache. Interviewer asks: why a doubly linked list and not a singly linked list?**

On cache hit, we need to move the accessed node to the head. To remove a node from a singly linked list, you need its predecessor — O(N) traversal. With a doubly linked list, each node has `prev` and `next` — removal is O(1) because you can update the pointers directly without traversal.

Combined with a `HashMap(key → node)`, both `get` and `put` are O(1). Python's `collections.OrderedDict` implements this internally — `cache.move_to_end(key)` for access, `cache.popitem(last=False)` for eviction.

---

**Q40: You designed a notification system. Interviewer asks: how do you handle a slow notification channel (e.g., SMS gateway times out)?**

Decouple send from acknowledgment:
1. API call receives the notification trigger → puts it on an internal queue (`queue.Queue` or SQS).
2. A dedicated sender worker pulls from the queue with exponential backoff retry (max 3 attempts).
3. On final failure: move to dead-letter queue for manual inspection.
4. The original API call returns immediately — it doesn't wait for SMS delivery.

This is the Fan-out pattern with async delivery. The queue is the decoupling point; the worker fleet is independently scalable.

---

## 8. Python-Specific LLD

---

**Q41: What is `__slots__` and when do you use it?**

`__slots__` prevents creation of `__dict__` on instances — saves memory when you have millions of small objects.

```python
class Point:
    __slots__ = ['x', 'y']
```

Without slots: each instance has a dict (~200 bytes overhead). With slots: only the declared attributes (~56 bytes). **Use when**: creating huge numbers of small objects — graph nodes, matrix cells, event records.

**Trade-off**: can't add arbitrary attributes at runtime, can't use weak references without adding `'__weakref__'` to slots.

---

**Q42: What is `@dataclass` and how does it differ from a regular class?**

Auto-generates `__init__`, `__repr__`, `__eq__` from annotated fields.

- `@dataclass(frozen=True)`: also generates `__hash__`, makes object immutable.
- `@dataclass(order=True)`: generates `<`, `>`, `<=`, `>=` comparison methods.

**Use for**: DTOs, value objects, config structs. Unlike `namedtuple`, dataclasses support default values, inheritance, and mutable state. Use a regular class when you need custom `__init__` logic beyond simple assignment.

---

**Q43: Explain `@property` with getter, setter, and deleter.**

```python
class Temperature:
    def __init__(self, celsius):
        self._celsius = celsius

    @property
    def celsius(self):
        return self._celsius

    @celsius.setter
    def celsius(self, value):
        if value < -273.15:
            raise ValueError("Below absolute zero")
        self._celsius = value

    @celsius.deleter
    def celsius(self):
        del self._celsius
```

Callers use `t.celsius = 100` — looks like a field assignment, behaves like a method call. The invariant is enforced at every write, not just construction.

---

**Q44: What is the difference between `@classmethod` and `@staticmethod`?**

- `@classmethod`: receives the class (`cls`) as the first argument. Used for **alternative constructors** (`Date.from_string("2024-01-01")`) or when subclasses need to override creation behavior.
- `@staticmethod`: receives nothing — a plain function namespaced to the class for organizational purposes. No access to class or instance.

**Rule**: use `@classmethod` when the method needs to create or inspect the class; use `@staticmethod` for pure utility functions that logically belong to the class but don't need `self` or `cls`.

---

## 9. Error Handling & Defensive Design

---

**Q45: What is the difference between an exception and a return code for error signaling?**

- **Return codes** (`None`, `-1`, `False`): easy to ignore — the caller can forget to check.
- **Exceptions**: force the caller to handle or propagate — the error cannot be silently ignored.

**Rule**: use exceptions for unexpected failures that break a contract (file not found, network timeout, invalid argument). Use return values for expected outcomes that are part of normal flow (`None` for "user not found" in a search). Never use exceptions for control flow (catching `StopIteration` manually — use `for` instead).

---

**Q46: When do you catch a broad `Exception` vs a specific one?**

Always catch specific exceptions — broad `Exception` hides bugs (you'll swallow a `KeyError` you didn't expect and mask the root cause).

**Catch `Exception` broadly only at the top level** of a service — request handler, main event loop — to log and return 500 rather than crashing the process. And always log the full traceback. **Never `except Exception: pass`** — silent failure is worse than a crash.

---

**Q47: What is a custom exception and when do you define one?**

Define one when callers need to distinguish your failure from a generic built-in exception.

```python
class PaymentError(Exception): pass          # base for the module
class InsufficientFundsError(PaymentError): pass  # specific variant
```

Callers can now `except PaymentError` to catch all payment errors, or `except InsufficientFundsError` for the specific case. This is more informative than raising `ValueError` and hoping callers can parse the message.

---

**Q48: How do you design a method that can fail in multiple ways?**

Three options:

1. **Raise specific exceptions** for each failure mode — clean, but callers need `try/except`.
2. **Return a result type** — `@dataclass` with `success: bool`, `value`, `error_code`. Explicit, no exception handling, readable branching.
3. **Return `Optional`** (value or `None`) — simple but loses error information.

**Choose based on caller usage**: if callers need to branch on the *type* of failure, use exceptions or a result type. If failures are rare and all handled the same way, `Optional` is fine.

---

**Q49: What is "fail fast" and how does it apply to class design?**

Detect invalid state as early as possible — at construction time, not at call time.

**Bad**: `User(name=None)` constructs successfully, crashes 10 method calls later when `name.upper()` is called — the error is far from the bug.

**Good**: `if not name: raise ValueError("name required")` in `__init__` — the crash is immediate and points directly at the caller that passed `None`.

Fail fast also applies to service startup — validate all required config at boot, not on the first request.

---

**Q50: How do you handle errors in a chain of method calls without deeply nested try/except?**

Let exceptions propagate naturally — only catch where you can meaningfully handle or translate.

**Exception translation**:
```python
try:
    db.save(user)
except psycopg2.IntegrityError as e:
    raise DuplicateUserError(user.email) from e
```

The `from e` preserves the original traceback (exception chaining). Use context managers for resource cleanup (`with open(...) as f`) so cleanup happens even when exceptions propagate. Avoid wrapping every call in its own `try/except` — that's noise, not error handling.

---

## 10. Testing in LLD

---

**Q51: How do you unit test a class that depends on a database?**

Inject the dependency — `UserService(repo: UserRepository)`. In tests, pass a `FakeUserRepository` that stores users in a dict. The service never touches the DB. Tests run in milliseconds with no DB setup.

The fake must implement the same interface as the real repo. Test the real repo separately against a test DB. **Never mock a DB client inside the class under test** — that tests the mock, not the code.

---

**Q52: What is the difference between a mock, a stub, and a fake?**

| Type | Description | When to use |
|---|---|---|
| **Stub** | Returns hardcoded values (`get_user()` always returns `User("Alice")`) | State-based tests where you just need a return value |
| **Fake** | Working lightweight implementation (`InMemoryUserRepo`) | When correctness of behavior matters (not just calls) |
| **Mock** | Records calls, allows assertions (`assert send.called_once_with(...)`) | Interaction-based tests — did the system call X? |

Over-mocking produces brittle tests. Prefer fakes and stubs; use mocks only for side-effectful dependencies.

---

**Q53: What makes a test brittle and how do you fix it?**

**Brittle tests** break when implementation changes but *behavior* doesn't.

Causes:
- Testing private methods directly (they can be freely refactored).
- Mocking internal calls (ties tests to implementation details).
- Asserting on exact method call order when order doesn't matter.

**Fix**: test through the public interface only, assert on observable outcomes (return value, state after call, side effects through injected fakes) — not on how the method achieves the outcome.

---

**Q54: How do you test concurrent code?**

Unit tests don't reliably catch race conditions — they're non-deterministic. Strategies:

1. Use `threading.Barrier` to synchronize threads to the exact moment of contention.
2. Run the concurrent test 1000 times and check for inconsistency.
3. Use well-tested concurrency primitives (`queue.Queue`, `threading.Lock`) rather than custom synchronization — the primitives are already battle-tested.

**Best test**: code that doesn't need synchronization at all (immutable shared state, no shared state).

---

**Q55: How do you test a method that has a side effect (sends an email, writes to a file)?**

Inject the side-effectful dependency behind an interface:

- **Email**: inject `FakeEmailSender` that appends to a list. Assert the list contains the right email.
- **File**: inject `io.StringIO` instead of a real file.
- **Time**: inject a clock — `def now(): return datetime.utcnow()` by default; inject a frozen clock in tests.

**Rule**: if you have a `finally` block in client code for cleanup, you should probably have a context manager instead.

---

## 11. Refactoring & Code Smells

---

**Q56: What are code smells and name five common ones?**

Code smells are signals of poor design — not bugs, but indicators of deeper structural problems.

1. **Long Method**: does too much, impossible to name accurately → extract into smaller named methods.
2. **Large Class**: too many responsibilities, violates SRP → split into cohesive classes.
3. **Feature Envy**: a method uses another class's data more than its own → move the method to that class.
4. **Primitive Obsession**: using `str` for email, `int` for money → extract into value objects.
5. **Switch/if-elif chains on type**: branching on a type string to choose behavior → replace with polymorphism.

---

**Q57: How do you refactor a 300-line method without breaking behavior?**

Never refactor without tests. Steps:
1. **Write characterization tests** — call the method with representative inputs, record actual outputs as expected values.
2. **Identify cohesive sub-operations** inside the method, extract each into a private method with a meaningful name.
3. **Verify tests still pass** after each extraction.
4. The 300-line method is now a readable sequence of named steps.

The extraction itself doesn't change behavior — it's mechanical. Tests prove it.

---

**Q58: What is the difference between refactoring and rewriting?**

- **Refactoring**: small, behavior-preserving transformations, one at a time, tests green after each step. Safe and incremental.
- **Rewriting**: discard existing code, start from scratch. High risk — you lose accumulated bug fixes and edge-case handling.

**Prefer refactoring unless**: the code is untestable (no seams for injection), the design is so wrong that every change is constrained by it, or it's truly throwaway prototype code. Strangler Fig pattern for gradual replacement: wrap old system, incrementally replace pieces, retire old code last.

---

**Q59: Interviewer shows you this code: `if user.role == "admin": ... elif user.role == "editor": ...`. What do you say?**

This is a **type-code smell** — branching on a string to choose behavior. Every new role requires modifying this method (OCP violation), and the same `if/elif` likely appears in multiple places.

**Fix**: `Role` base class (or Strategy pattern) with subclasses `AdminRole`, `EditorRole`, each implementing `can_edit()`, `can_delete()`, etc. The `if/elif` becomes `user.role.can_edit()`. Adding a new role = new class, zero existing code changes.

---

## 12. Data Structures in LLD

---

**Q60: Which data structure backs an LRU cache and why?**

**HashMap + doubly linked list**.

- HashMap gives O(1) lookup: key → node pointer.
- Doubly linked list maintains access order: on access, move the node to the head in O(1) (need `prev` pointer for O(1) removal — singly linked list would be O(N)).
- Eviction: remove from the tail (least recently accessed) in O(1).

Python's `collections.OrderedDict` implements this — `cache.move_to_end(key)` for access, `cache.popitem(last=False)` for eviction.

---

**Q61: How do you implement a stack that also returns the minimum in O(1)?**

Maintain a second `min_stack` alongside the main stack:
- **Push**: push to main; if `min_stack` is empty or value ≤ `min_stack.top()`, push to `min_stack` too.
- **Pop**: pop from main; if popped value == `min_stack.top()`, pop from `min_stack` too.
- **get_min()**: return `min_stack.top()`.

Both stacks stay synchronized for the minimum. O(1) push, pop, get_min. O(N) space worst case (all elements decreasing — all pushed to min_stack).

---

**Q62: When would you use a heap in a design problem?**

When you repeatedly need the minimum or maximum of a *dynamic* set.

Common LLD scenarios:
- **Task scheduler**: always pick the highest-priority task → min-heap on priority.
- **Merge K sorted lists**: heap of `(value, list_index)`, pop min, advance that list.
- **Sliding window median**: two heaps — max-heap for the left half, min-heap for the right half.
- **Top-K elements in a stream**: min-heap of size K — new element replaces root if larger.

Python: `heapq` module — min-heap by default; negate values for max-heap.

---

**Q63: When do you use a deque vs a list in Python?**

- **`deque`**: O(1) append and pop from *both* ends. Use for: sliding window (append right, pop left), BFS queue (`popleft()` instead of O(N) `pop(0)`), bounded history buffer (`deque(maxlen=100)` auto-evicts oldest).
- **`list`**: O(1) append to the right, O(N) insert/pop from the left. Use for: random access by index (deque is O(N) for random access), sorting, slicing.

Key rule: if you're doing `list.pop(0)` or `list.insert(0, x)`, switch to `deque`.

---

**Q64: How do you choose between a set and a dict for a "seen items" tracker?**

- **Set**: when you only need membership testing (`if item in seen`). O(1) average lookup, minimal memory — no value storage.
- **Dict**: when you need associated data (`seen[item] = timestamp`, `seen[item] = count`).

Use a set if you only need `in` checks — it communicates intent clearly. **Common mistake**: using a list for membership testing — O(N) per lookup vs O(1) for set.

---

## 13. Object Design Walkthroughs

---

**Q65: Design a `Money` class. What methods and invariants does it need?**

```python
from decimal import Decimal
from dataclasses import dataclass

@dataclass(frozen=True)
class Money:
    amount: Decimal
    currency: str

    def __post_init__(self):
        if self.amount < 0:
            raise ValueError("amount must be non-negative")

    def add(self, other: 'Money') -> 'Money':
        if self.currency != other.currency:
            raise ValueError("currency mismatch")
        return Money(self.amount + other.amount, self.currency)

    def multiply(self, factor: Decimal) -> 'Money':
        return Money(self.amount * factor, self.currency)
```

- **Invariants**: non-negative amount, valid ISO currency code.
- **Immutable**: every operation returns a new `Money`.
- **Never use `float`** — floating-point precision errors compound in financial math. Always `Decimal`.

---

**Q66: Design a `Config` class that reads from a file and supports hot reload.**

```python
import json, threading

class Config:
    def __init__(self, path):
        self._path = path
        self._data = {}
        self._lock = threading.RLock()
        self._load()

    def _load(self):
        with open(self._path) as f:
            with self._lock:
                self._data = json.load(f)

    def get(self, key, default=None):
        with self._lock:
            return self._data.get(key, default)

    def reload(self):
        self._load()   # called by SIGHUP handler or file-watcher thread
```

`RLock` (reentrant lock) because `reload()` calls `_load()` which also acquires the lock. Hot reload is triggered by `SIGHUP` signal or a background file-watcher thread. Readers hold the lock only during the dict lookup — brief and contention-free.

---

**Q67: Design a simple event bus in Python.**

```python
from collections import defaultdict
import threading

class EventBus:
    def __init__(self):
        self._handlers = defaultdict(list)
        self._lock = threading.Lock()

    def subscribe(self, event_type, handler):
        with self._lock:
            self._handlers[event_type].append(handler)

    def unsubscribe(self, event_type, handler):
        with self._lock:
            self._handlers[event_type].remove(handler)

    def publish(self, event_type, payload=None):
        with self._lock:
            handlers = list(self._handlers[event_type])  # snapshot
        for handler in handlers:
            try:
                handler(payload)
            except Exception:
                logging.exception("handler %s raised", handler)
```

**Key details**: copy the handler list before iterating (avoids modification-during-iteration bugs), catch per-handler exceptions (one bad handler must not prevent others from running). For async handlers: run each in a `ThreadPoolExecutor`.

---

**Q68: Design a `ConnectionPool` class.**

```python
import queue, contextlib

class ConnectionPool:
    def __init__(self, factory, size=10):
        self._pool = queue.Queue(maxsize=size)
        for _ in range(size):
            self._pool.put(factory())

    @contextlib.contextmanager
    def acquire(self, timeout=5):
        conn = self._pool.get(timeout=timeout)   # blocks until available; raises queue.Empty on timeout
        try:
            yield conn
        finally:
            self._pool.put(conn)                  # always return, even if caller raises
```

- `queue.Queue` provides thread-safe blocking acquire with no explicit locking.
- Context manager guarantees the connection is always returned.
- Timeout prevents deadlock if the pool is exhausted.
- **Follow-up**: "How do you handle a broken connection?" — validate the connection on acquire (ping or check `conn.closed`), replace with `factory()` if invalid before yielding.

---

## 14. Structural & Behavioral Patterns

---

**Q69: What is the State pattern and how does it replace if/elif on status fields?**

**What it is**: extract each state into its own class implementing a common interface. The context object holds a reference to the current state and delegates all behavior to it. State transitions happen inside state methods.

**Why it matters**: the `if/elif self.state == "idle":` version requires you to touch every method when you add a new state. With State pattern, adding a new state = one new class, zero changes to existing states.

**Example — Vending Machine**:
```python
class VendingMachineState(ABC):
    @abstractmethod
    def insert_coin(self, amount): ...
    @abstractmethod
    def select_item(self, item_id): ...
    @abstractmethod
    def cancel(self): ...

class IdleState(VendingMachineState):
    def insert_coin(self, amount):
        self.context.balance += amount
        self.context.set_state(CoinInsertedState())
    def select_item(self, item_id):
        raise InvalidStateError("Insert coin first")
    def cancel(self): pass   # no-op, nothing to return

class CoinInsertedState(VendingMachineState):
    def select_item(self, item_id):
        if self.context.inventory[item_id] == 0:
            self.context.set_state(OutOfStockState())
        elif self.context.balance >= self.context.prices[item_id]:
            self.context.set_state(DispensingState(item_id))
```

**Amazon angle**: Order status machine — `PLACED`, `CONFIRMED`, `SHIPPED`, `DELIVERED`, `RETURNED` each as state objects. Illegal transitions (e.g., `DELIVERED → SHIPPED`) simply raise in the state's method rather than requiring `if/elif` guard blocks everywhere.

---

**Q70: What is the Adapter pattern and when does Amazon need it?**

**What it is**: wraps an incompatible interface so it matches the interface your code expects. Your code is unchanged; only the adapter translates.

**When to use**: integrating a new third-party vendor without changing existing code. The adapter is the seam between your abstraction and theirs.

**Example**:
```python
class PaymentProcessor(ABC):       # your interface
    @abstractmethod
    def charge(self, amount_cents: int) -> Receipt: ...

class StripeAdapter(PaymentProcessor):   # adapts Stripe's SDK
    def __init__(self, stripe_client):
        self._client = stripe_client

    def charge(self, amount_cents: int) -> Receipt:
        response = self._client.payment_intents.create(
            amount=amount_cents,
            currency="usd",
        )
        return Receipt(transaction_id=response.id)

class SquareAdapter(PaymentProcessor):   # adapts Square's SDK
    def charge(self, amount_cents: int) -> Receipt:
        result = self._square.payments.create_payment(
            amount_money={"amount": amount_cents, "currency": "USD"}
        )
        return Receipt(transaction_id=result.payment.id)
```

**Amazon uses this constantly**: each shipping carrier (UPS, FedEx, USPS) exposes a different API. Adapters make them all look like `Carrier.ship(package)` to the fulfillment service.

**Adapter vs Facade**: Adapter translates an interface you *don't own*; Facade simplifies an interface you *do own*.

---

**Q71: What is the Facade pattern and when do you reach for it?**

**What it is**: provides a simple, high-level interface to a complex subsystem. The facade knows how to orchestrate the subsystem; callers don't need to.

**When to use**: when callers repeatedly perform the same sequence of subsystem calls. The facade captures that sequence once.

**Example**:
```python
class OrderFacade:
    def __init__(self, inventory, payment, shipping, notification):
        self._inventory = inventory
        self._payment = payment
        self._shipping = shipping
        self._notification = notification

    def place_order(self, cart, payment_method, user):
        self._inventory.reserve(cart)          # step 1
        receipt = self._payment.charge(cart.total, payment_method)  # step 2
        shipment = self._shipping.schedule(cart, user.address)      # step 3
        self._notification.send_confirmation(user, receipt, shipment)  # step 4
        return Order(receipt, shipment)
```

Callers call `facade.place_order(...)` and know nothing about the four services or their interaction order.

**Facade vs Proxy**: Proxy controls access to *one* object; Facade simplifies access to *many* objects.
**Facade vs Adapter**: Facade simplifies your own subsystem; Adapter translates a third-party interface.

---

**Q72: What is the Composite pattern? Design a discount rule engine using it.**

**What it is**: lets you treat individual objects and compositions of objects uniformly. Both leaf nodes and composite nodes implement the same interface — callers don't need to know whether they're dealing with a single rule or a tree of rules.

**When to use**: when you have a hierarchy of objects that should be operated on the same way — file systems (file and directory both have `size()`), UI components (button and panel both have `render()`), rule engines.

**Example — Discount engine**:
```python
from abc import ABC, abstractmethod
from decimal import Decimal

class DiscountRule(ABC):
    @abstractmethod
    def apply(self, cart) -> Decimal: ...

# Leaf nodes
class PercentageDiscount(DiscountRule):
    def __init__(self, pct: Decimal): self._pct = pct
    def apply(self, cart) -> Decimal: return cart.total * self._pct

class FlatDiscount(DiscountRule):
    def __init__(self, amount: Decimal): self._amount = amount
    def apply(self, cart) -> Decimal: return min(self._amount, cart.total)

# Composite nodes — treat groups of rules as a single rule
class AndDiscount(DiscountRule):    # all rules must apply; sum their discounts
    def __init__(self, *rules): self._rules = rules
    def apply(self, cart) -> Decimal: return sum(r.apply(cart) for r in self._rules)

class OrDiscount(DiscountRule):     # best rule wins
    def __init__(self, *rules): self._rules = rules
    def apply(self, cart) -> Decimal: return max(r.apply(cart) for r in self._rules)
```

**Usage — "10% off + free shipping if cart > $50, OR 15% off if cart > $100"**:
```python
rule = OrDiscount(
    AndDiscount(PercentageDiscount(Decimal("0.10")), FreeShipping()),
    PercentageDiscount(Decimal("0.15")),
)
discount = rule.apply(cart)
```

Adding a new rule type (e.g., `BuyXGetYDiscount`) = one new leaf class. The `OrDiscount` and `AndDiscount` composites work with it immediately.

---

**Q73: What is the Chain of Responsibility pattern? Give an Amazon example.**

**What it is**: a request is passed through a chain of handlers; each handler decides to process it, pass it on, or stop it. Handlers are independent and interchangeable.

**When to use**: when you have a sequential pipeline of processing steps where each step is independent (doesn't need to know about others), and steps need to be reordered or inserted without modifying existing code.

**Example — Order fulfillment pipeline**:
```python
class Handler(ABC):
    def __init__(self): self._next: Handler = None

    def set_next(self, handler: 'Handler') -> 'Handler':
        self._next = handler
        return handler   # enables chaining: fraud.set_next(inventory).set_next(payment)

    def handle(self, order):
        if self._next:
            return self._next.handle(order)

class FraudCheckHandler(Handler):
    def handle(self, order):
        if is_fraudulent(order):
            raise FraudException(order.id)
        return super().handle(order)    # pass to next

class InventoryHandler(Handler):
    def handle(self, order):
        if not inventory.reserve(order):
            raise OutOfStockException(order.item_id)
        return super().handle(order)

class PaymentHandler(Handler):
    def handle(self, order):
        receipt = payment.charge(order.total, order.payment_method)
        return super().handle(order)

# Wire the chain
fraud = FraudCheckHandler()
fraud.set_next(InventoryHandler()).set_next(PaymentHandler()).set_next(ShippingHandler())
fraud.handle(order)
```

**Adding age verification**: new `AgeVerificationHandler` class, insert anywhere in the chain. Zero changes to existing handlers.

**CoR vs Observer**:
- CoR passes one request through handlers *sequentially*, stopping when handled or on error.
- Observer notifies *all* subscribers *simultaneously* (fire-and-forget).

---

## 15. Amazon-Specific LLD Problems

---

**Q74: Design a Vending Machine class hierarchy. Walk through the state transitions.**

**States**: `IdleState`, `CoinInsertedState`, `DispensingState`, `OutOfStockState`.
**Events**: `insert_coin(amount)`, `select_item(item_id)`, `cancel()`, `dispense_complete()`.

**Transitions**:
- `Idle + insert_coin(amount)` → `CoinInserted` (accumulate balance)
- `CoinInserted + select_item(id)` where `balance >= price AND qty > 0` → `Dispensing`
- `CoinInserted + select_item(id)` where `qty == 0` → `OutOfStock` (return coins, → Idle)
- `CoinInserted + cancel` → `Idle` (return coins)
- `Dispensing + dispense_complete` → `Idle` (deduct inventory, return change)

**Key classes**:
- `VendingMachine` (context — holds `current_state`, `balance`, `inventory`)
- `Item(name, price, quantity)`
- Each `*State` class (implements `insert_coin`, `select_item`, `cancel`)

**Invariants**:
1. Never dispense without atomically decrementing inventory.
2. Always return change before transitioning to Idle.

**Concurrency**: `Lock` around `select_item` transitions — two concurrent callers must not both dispense the last item. Use optimistic approach: `inventory[item_id] -= 1` inside the lock; if `< 0`, reverse and raise.

---

**Q75: Design an ATM. What are the states and what concurrent risk exists?**

**States**: `CardInsertedState → PinVerifiedState → SelectingTransactionState → ProcessingState → EjectingCardState`.

**The crash problem**: withdrawal is two steps — debit the DB account, then dispense cash. If the machine crashes after the DB debit but before dispensing: money is gone, no cash given.

**Design with Saga + transaction log**:
```
1. INSERT withdrawal_log(id, account_id, amount, status='PENDING')
2. UPDATE accounts SET balance = balance - amount WHERE id = X AND balance >= amount
3. Dispense cash (physical)
4. UPDATE withdrawal_log SET status='COMPLETED'
```

On startup: scan for `status='PENDING'` rows. If balance was debited but cash not dispensed: refund or alert ops. If balance not debited: mark abandoned.

**Concurrent risk**: two sessions for the same account (two ATMs, or one ATM + online banking). Fix: `SELECT FOR UPDATE` on the account row during withdrawal — holds a DB row lock for the duration of the transaction, serializing concurrent withdrawals for the same account.

---

**Q76: Design an Amazon Locker service.**

**Core classes**:
- `Locker(id, size: LockerSize, status: LockerStatus, location_id)`
- `Package(id, size: PackageSize, delivery_address)`
- `LockerBank(location_id, address, lockers: List[Locker])`
- `Delivery(id, package_id, locker_id, pickup_code, expires_at, status)`

**Assignment algorithm**: find the smallest available locker that fits the package — smallest-fit to preserve larger lockers for larger packages.

```sql
SELECT id FROM lockers
WHERE location_id = :location
  AND size >= :package_size
  AND status = 'AVAILABLE'
ORDER BY size ASC
LIMIT 1
```

**Concurrency — two deliveries assigned the same locker simultaneously**:
```sql
UPDATE lockers
SET status = 'OCCUPIED', delivery_id = :delivery_id
WHERE id = :locker_id AND status = 'AVAILABLE'
-- rows_affected = 0 means another delivery won; retry with next available
```

**Pickup flow**: carrier deposits package → system generates 6-digit code (random, stored hashed), sends to recipient → recipient enters code → system verifies → locker opens → delivery marked PICKED_UP.

**Expiry**: background job scans `deliveries WHERE expires_at < NOW() AND status = 'DEPOSITED'` → sends return-to-sender event → marks locker AVAILABLE.

---

**Q77: Design an Order state machine. What transitions are legal and how do you enforce them?**

**States**: `PLACED → PAYMENT_PENDING → PAYMENT_FAILED | CONFIRMED → SHIPPED → OUT_FOR_DELIVERY → DELIVERED → RETURN_REQUESTED → RETURNED`. Also: `CANCELLED` (reachable from PLACED, CONFIRMED — not from DELIVERED).

**Enforcement** — define valid transitions as data, not code:
```python
from enum import Enum

class OrderStatus(Enum):
    PLACED = "PLACED"
    CONFIRMED = "CONFIRMED"
    SHIPPED = "SHIPPED"
    DELIVERED = "DELIVERED"
    CANCELLED = "CANCELLED"
    RETURN_REQUESTED = "RETURN_REQUESTED"
    RETURNED = "RETURNED"

VALID_TRANSITIONS: dict[OrderStatus, set[OrderStatus]] = {
    OrderStatus.PLACED:            {OrderStatus.CONFIRMED, OrderStatus.CANCELLED},
    OrderStatus.CONFIRMED:         {OrderStatus.SHIPPED, OrderStatus.CANCELLED},
    OrderStatus.SHIPPED:           {OrderStatus.DELIVERED},
    OrderStatus.DELIVERED:         {OrderStatus.RETURN_REQUESTED},
    OrderStatus.RETURN_REQUESTED:  {OrderStatus.RETURNED},
    OrderStatus.CANCELLED:         set(),
    OrderStatus.RETURNED:          set(),
}

class Order:
    def transition_to(self, new_status: OrderStatus):
        if new_status not in VALID_TRANSITIONS[self.status]:
            raise InvalidTransitionError(f"{self.status} → {new_status} is not allowed")
        self.status = new_status
        self._publish_event(OrderStatusChangedEvent(self.id, new_status))
```

Never allow direct field assignment (`order.status = "CANCELLED"`) — force through `transition_to()`. Each transition publishes an event; handlers respond asynchronously (Observer pattern) — confirmation email on CONFIRMED, tracking record on SHIPPED.

---

**Q78: Design a Coupon/Discount Engine. How do you handle stacking and exclusivity?**

**Classes**:
- `Coupon(code, type: CouponType, value, conditions: CouponConditions, exclusive: bool, max_uses: int, expiry: datetime)`
- `CouponConditions(min_cart_value, eligible_categories, first_order_only: bool)`
- `CouponType`: `PERCENTAGE`, `FLAT_AMOUNT`, `FREE_SHIPPING`, `BUY_X_GET_Y`

**Validation and application algorithm**:
```python
def apply_coupons(cart, coupon_codes: list[str], user) -> Decimal:
    valid = []
    for code in coupon_codes:
        coupon = coupon_repo.get(code)
        if not coupon or coupon.is_expired() or not coupon.conditions_met(cart, user):
            raise InvalidCouponError(code)
        valid.append(coupon)

    # Exclusivity: if any coupon is exclusive, apply only that one
    exclusive = [c for c in valid if c.exclusive]
    if exclusive:
        if len(exclusive) > 1:
            raise ConflictingCouponsError("only one exclusive coupon allowed")
        return exclusive[0].apply(cart)

    # Stacking: apply in order — percentage → flat → shipping
    total_discount = Decimal(0)
    for coupon in sorted(valid, key=lambda c: c.type.priority):
        total_discount += coupon.apply(cart)
    return min(total_discount, cart.total)   # can't discount more than cart total
```

**`max_uses` race condition** — two users redeem the last available use simultaneously:
```python
# Redis atomic increment — check before granting
uses = redis.incr(f"coupon:{code}:uses")
if uses > coupon.max_uses:
    redis.decr(f"coupon:{code}:uses")   # rollback
    raise CouponExhaustedError(code)
```

Or at the DB level: `UPDATE coupons SET uses_count = uses_count + 1 WHERE code = :code AND uses_count < max_uses` — check `rows_affected = 0` means exhausted.

---

## 16. Concurrency Deep-Dives

---

**Q79: What is a Semaphore and how does it differ from a Lock?**

**What it is**: a counting primitive — allows N threads to hold it simultaneously (vs Lock which allows only 1).

**When to use**: bound concurrent access to a resource pool — connection pool, API call throttling, bounded parallel downloads.

```python
import threading

# Limit to 10 concurrent DB queries
db_semaphore = threading.Semaphore(10)

def query_db(sql):
    with db_semaphore:          # blocks when 10 threads are already inside
        return db.execute(sql)
```

- **Lock** = `Semaphore(1)` — mutual exclusion.
- **Semaphore(N)** = "at most N concurrent" — resource pool control.

**Never use a Lock when you mean "at most N concurrent"** — a Lock forces serial access, wasting all the concurrency you could allow.

---

**Q80: What is a read-write lock and when do you need one?**

**What it is**: multiple readers can hold it simultaneously; a writer needs exclusive access (no readers or other writers).

**When to use**: reads are frequent and cheap, writes are rare and must be exclusive. Example: an in-memory cache read by 100 threads/sec, refreshed once per minute.

Python's `threading` module has no built-in RWLock. Implement with two locks:

```python
import threading

class RWLock:
    def __init__(self):
        self._readers = 0
        self._count_lock = threading.Lock()      # protects _readers counter
        self._write_lock = threading.Lock()      # held exclusively by one writer

    def read_acquire(self):
        with self._count_lock:
            self._readers += 1
            if self._readers == 1:               # first reader blocks writers
                self._write_lock.acquire()

    def read_release(self):
        with self._count_lock:
            self._readers -= 1
            if self._readers == 0:               # last reader unblocks writers
                self._write_lock.release()

    def write_acquire(self):
        self._write_lock.acquire()               # blocks until no readers or writers

    def write_release(self):
        self._write_lock.release()
```

**Trade-off**: using a plain `Lock` for a read-heavy workload serializes all reads unnecessarily — 100 reads/sec become sequential. RWLock allows all 100 reads to proceed concurrently.

---

**Q81: What is `threading.Event` and what problem does it solve?**

**What it is**: a signaling primitive — one thread sets it, all waiting threads are unblocked simultaneously. Unlike a lock, it doesn't transfer ownership; it broadcasts a signal.

```python
import threading

ready = threading.Event()

def producer():
    prepare_data()
    ready.set()            # wake ALL threads waiting on ready.wait()

def consumer():
    ready.wait()           # blocks until set; returns immediately if already set
    process_data()

def shutdown_worker(stop_event):
    while not stop_event.is_set():
        process_next_item()
    # clean shutdown when stop_event.set() is called
```

**Use for**:
1. **Start signals**: don't begin until initialization is complete.
2. **Graceful shutdown**: workers loop on `stop_event.is_set()` — set it to stop all workers.
3. **One-shot notifications**: signal that some condition has been achieved.

**Event vs Condition**: `Event` is a stateless one-shot signal (set/wait). `Condition` pairs a lock with a predicate — lets threads wait until a *specific condition* becomes true (e.g., `queue is not empty`), and re-check on each `notify()`. Use `Condition` when multiple threads wait on different predicates; use `Event` for simple broadcast signals.

---

**Q82: How do you design a context manager and when is it the right abstraction?**

**What it is**: an object that defines `__enter__` (setup) and `__exit__` (cleanup). Used with `with` statement to guarantee cleanup regardless of success or failure — no `finally` block needed in client code.

**Two implementation styles**:

```python
# Style 1: class-based — use when you need to reuse the context manager or subclass it
class Timer:
    def __enter__(self):
        self.start = time.time()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.elapsed = time.time() - self.start
        return False    # False = propagate exceptions; True = suppress them

with Timer() as t:
    do_work()
print(f"took {t.elapsed:.2f}s")

# Style 2: generator-based — simpler for one-off use
import contextlib

@contextlib.contextmanager
def db_transaction(conn):
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise          # re-raise so the caller sees the exception
```

**`__exit__` receives exception info** — `(exc_type, exc_val, exc_tb)` are `None` if no exception occurred. Return `True` to suppress the exception; return `False` (or `None`) to propagate it.

**When to use**: any resource that requires cleanup — file handles, DB connections, locks, timers, temporary directories. **Interview signal**: if you write `finally: conn.close()` in client code repeatedly, you should have a context manager instead.

