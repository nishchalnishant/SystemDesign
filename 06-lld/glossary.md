> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A comprehensive glossary of OOP, SOLID, design-pattern (all 23 GoF), concurrency, and UML terminology used in LLD interviews.
>
> **Key concepts:**
> - OOP core: Encapsulation, Inheritance, Polymorphism, Abstraction, Composition vs. Inheritance.
> - SOLID: SRP, OCP, LSP, ISP, DIP — one line each on what failure mode it prevents.
> - Design patterns: all 23 GoF patterns grouped Creational / Structural / Behavioral, each with intent + a one-line trigger phrase.
> - Concurrency: race condition, deadlock, mutex, semaphore, thread-safe singleton, producer-consumer.
> - UML: class diagram relationship types (association, aggregation, composition, inheritance, realization).
>
> **Key takeaway:** In an LLD interview, naming the pattern by its correct name *and* its trigger condition ("this is the Strategy pattern because the algorithm varies independently of the client") signals seniority the same way precise distributed-systems vocabulary does in HLD. Use this file to refine that vocabulary, not to learn the patterns for the first time — see [03-design-patterns/](03-design-patterns/) for full treatments.

---
module: 06-lld
status: unread
tags: [06-lld, glossary, reference, design-patterns, oop, solid]
---
# LLD Glossary

> **Essential vocabulary for SDE-3 level LLD interviews — OOP, SOLID, all 23 GoF patterns, concurrency, and UML.**

---

## Reference Mindmap

```
LLD Glossary
├── Core Problem
│   └── Recognizing a pattern in a diagram is not the same as reaching for it during a fresh prompt
├── OOP Pillars
│   ├── Encapsulation → hide internal state behind a controlled interface
│   ├── Abstraction → expose what an object does, hide how
│   ├── Inheritance → IS-A relationship, shared contract + behavior reuse
│   └── Polymorphism → same interface, different runtime behavior
├── SOLID
│   ├── SRP → one class, one reason to change
│   ├── OCP → open for extension, closed for modification
│   ├── LSP → subtypes must be substitutable for their base type
│   ├── ISP → many small interfaces beat one fat one
│   └── DIP → depend on abstractions, not concretions
├── GoF Patterns (23)
│   ├── Creational (5) → Singleton, Factory Method, Abstract Factory, Builder, Prototype
│   ├── Structural (7) → Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy
│   └── Behavioral (11) → Strategy, Observer, State, Command, Chain of Responsibility,
│       Template Method, Iterator, Mediator, Memento, Visitor, Interpreter
├── Concurrency
│   ├── Race condition → outcome depends on thread interleaving timing
│   ├── Deadlock → circular wait on locks, no thread can proceed
│   ├── Mutex vs Semaphore → exclusive lock (1) vs counted permits (N)
│   └── Thread-safe singleton → double-checked locking or eager init to avoid two instances
├── UML Relationships
│   ├── Association → "uses" — loosest coupling, no ownership implied
│   ├── Aggregation → "has-a," shared ownership, part outlives the whole
│   ├── Composition → "owns-a," lifecycle-bound, part dies with the whole
│   ├── Inheritance → "is-a," compile-time, implementation reuse
│   └── Realization → "implements," interface contract with no shared implementation
└── Interview Angles
    ├── "Why this pattern and not X?" → know the closest confusable pattern and the deciding difference
    ├── "What happens if two threads call this simultaneously?" → know your class's thread-safety story
    └── "Composition or inheritance here?" → default to composition; justify inheritance explicitly
```

---

## OOP Fundamentals

### Encapsulation

Bundling data and the methods that operate on it into a single unit, and restricting direct access to internal state.

**Key property**: External code interacts only through a defined interface (public methods), never by reaching into fields directly.

**Real example**: A `BankAccount` class exposes `deposit()`/`withdraw()` but keeps `balance` private — no caller can set balance directly and skip validation.

**Interview angle**: "Why is this field private with a getter instead of public?" — the answer should name the invariant the getter/setter protects, not just "it's good practice."

---

### Abstraction

Exposing only the essential behavior of an object while hiding implementation detail behind an interface.

**Key property**: The caller depends on *what* an object does, not *how*.

**Real example**: `List<T>` in Java — callers code against the interface; whether it's backed by an array or a linked list is hidden.

**Distinguish from encapsulation**: Encapsulation hides *state*; abstraction hides *implementation complexity* behind a simpler contract. They're often applied together but answer different questions.

---

### Inheritance

An IS-A relationship where a subclass acquires the fields and methods of a superclass, and can override behavior.

**Key property**: Compile-time binding of a shared contract; enables code reuse but tightly couples subclass to superclass implementation.

**Real example**: `SavingsAccount extends Account` — reuses deposit/withdraw logic, overrides interest calculation.

**Cost**: Fragile base class problem — changing the superclass can silently break every subclass. This is the core reason [composition-over-inheritance](02-solid-principles/README.md) is the default recommendation.

---

### Polymorphism

The ability for objects of different types to be treated through a common interface, with the actual behavior determined at runtime (dynamic/runtime polymorphism) or compile time (static/method overloading).

**Key property**: The caller invokes one method signature; the JVM/interpreter dispatches to the correct override based on the object's actual runtime type.

**Real example**: `List<Shape> shapes; for (Shape s : shapes) s.draw();` — each shape draws itself differently without the caller knowing which subclass it holds.

**Interview angle**: This is the mechanism that makes the Strategy, State, and Template Method patterns work — if you can't explain polymorphism precisely, you can't explain why those patterns avoid `if/else` chains on type.

---

### Composition vs. Inheritance

Composition (HAS-A, an object holds a reference to another) vs. inheritance (IS-A, a class extends another).

**Key property**: Composition is looser coupling — behavior can be swapped at runtime by substituting the held object; inheritance is fixed at compile time.

**Real example**: A `Car` HAS-A `Engine` (composition, swappable) vs. a `SportsCar` IS-A `Car` (inheritance, fixed).

**Rule of thumb**: "Favor composition over inheritance" — default to composition; justify inheritance only when the IS-A relationship is genuinely stable and you need polymorphic substitution, not just code reuse.

---

## SOLID Principles

### Single Responsibility Principle (SRP)

A class should have only one reason to change.

**Failure mode it prevents**: God classes that mix unrelated concerns (e.g., a class that both computes tax and formats an invoice PDF) — a change to formatting risks breaking tax logic.

**Reference**: [02-solid-principles/01-single-responsibility.md](02-solid-principles/01-single-responsibility.md)

---

### Open/Closed Principle (OCP)

Software entities should be open for extension but closed for modification.

**Failure mode it prevents**: Adding a new case requires editing existing, already-tested code (e.g., a growing `if/else` chain in a `calculateDiscount()` method) instead of adding a new class.

**Mechanism**: Usually achieved via Strategy or polymorphism — new behavior is a new class implementing an existing interface, not a new branch.

**Reference**: [02-solid-principles/02-open-closed.md](02-solid-principles/02-open-closed.md)

---

### Liskov Substitution Principle (LSP)

Subtypes must be substitutable for their base type without altering the correctness of the program.

**Failure mode it prevents**: A subclass that overrides a method to throw `UnsupportedOperationException` or silently changes the contract (e.g., `Square extends Rectangle` breaking `setWidth`/`setHeight` independence).

**Reference**: [02-solid-principles/03-liskov-substitution.md](02-solid-principles/03-liskov-substitution.md)

---

### Interface Segregation Principle (ISP)

Clients should not be forced to depend on methods they don't use — prefer many small, role-specific interfaces over one fat interface.

**Failure mode it prevents**: A `Worker` interface with `work()` and `eat()` forces a `RobotWorker` to implement a meaningless `eat()`.

**Reference**: [02-solid-principles/04-interface-segregation.md](02-solid-principles/04-interface-segregation.md)

---

### Dependency Inversion Principle (DIP)

High-level modules should not depend on low-level modules; both should depend on abstractions.

**Failure mode it prevents**: A `NotificationService` that directly instantiates `EmailSender` can't be tested or extended without editing its source — depend on a `Sender` interface instead, inject the implementation.

**Reference**: [02-solid-principles/05-dependency-inversion.md](02-solid-principles/05-dependency-inversion.md)

---

## Design Patterns — Creational

Patterns concerned with object creation, decoupling "what gets created" from "how and when it gets created."

### Singleton

Ensures a class has only one instance and provides a global access point to it.

**Trigger phrase**: "exactly one shared instance across the whole application" (e.g., a config manager, a connection pool).

**Interview trap**: Naive lazy init isn't thread-safe — know double-checked locking, the initialization-on-demand holder idiom, or an `enum` singleton in Java.

**Reference**: [thread-safe-singleton.md](01-creational/singleton.md)

---

### Factory Method

Defines an interface for creating an object, but lets subclasses decide which class to instantiate.

**Trigger phrase**: "the exact subclass to create depends on some condition, and I want that decision isolated from the caller."

**Distinguish from Abstract Factory**: Factory Method creates one product via subclassing/overriding; Abstract Factory creates families of related products via composition.

**Reference**: [factory-pattern.md](01-creational/factory-pattern.md)

---

### Abstract Factory

Provides an interface for creating families of related objects without specifying their concrete classes.

**Trigger phrase**: "I need to create a set of related objects that must stay consistent with each other" (e.g., a UI toolkit producing matched Button/Checkbox/Scrollbar for a given OS theme).

**Reference**: [abstract-factory-pattern.md](01-creational/abstract-factory-pattern.md)

---

### Builder

Separates the construction of a complex object from its representation, allowing the same construction process to build different representations, typically via a fluent chained API.

**Trigger phrase**: "this object has many optional constructor parameters" (telescoping constructor problem).

**Reference**: [builder-pattern.md](01-creational/builder-pattern.md)

---

### Prototype

Creates new objects by copying an existing object (a prototype) rather than instantiating a class directly.

**Trigger phrase**: "creating this object from scratch is expensive, but I already have a similar instance to clone."

**Reference**: [prototype-pattern.md](01-creational/prototype-pattern.md)

---

## Design Patterns — Structural

Patterns concerned with how classes and objects are composed to form larger structures.

### Adapter

Converts the interface of a class into another interface clients expect, letting incompatible interfaces work together.

**Trigger phrase**: "I need to plug in a third-party/legacy class whose interface doesn't match what my code expects."

**Reference**: [adapter-pattern.md](02-structural/adapter-pattern.md)

---

### Bridge

Decouples an abstraction from its implementation so the two can vary independently.

**Trigger phrase**: "I have two dimensions of variation (e.g., shape type × rendering API) and inheritance would force a combinatorial explosion of subclasses."

**Reference**: [bridge-pattern.md](02-structural/bridge-pattern.md)

---

### Composite

Composes objects into tree structures to represent part-whole hierarchies, letting clients treat individual objects and compositions uniformly.

**Trigger phrase**: "I have a tree of objects (files/folders, UI components) and I want to call the same method on a leaf or an entire subtree without checking which one it is."

**Reference**: [composite-pattern.md](02-structural/composite-pattern.md)

---

### Decorator

Attaches additional responsibilities to an object dynamically, as a flexible alternative to subclassing.

**Trigger phrase**: "I need to add behavior in combinable layers at runtime" (e.g., `BufferedInputStream(new FileInputStream(...))`, coffee with add-ons).

**Distinguish from Proxy**: Decorator adds *new* behavior/responsibility; Proxy controls *access* to the same behavior.

**Reference**: [decorator-pattern.md](02-structural/decorator-pattern.md)

---

### Facade

Provides a unified, simplified interface to a set of interfaces in a subsystem.

**Trigger phrase**: "callers shouldn't need to know about the 6 subsystems they'd otherwise have to orchestrate themselves."

**Reference**: [facade-pattern.md](02-structural/facade-pattern.md)

---

### Flyweight

Uses sharing to support large numbers of fine-grained objects efficiently by factoring out shared (intrinsic) state from unique (extrinsic) state.

**Trigger phrase**: "I need millions of small objects and most of their state is identical/shareable" (e.g., character glyphs in a text editor, tree instances in a forest renderer).

**Reference**: [flyweight-pattern.md](02-structural/flyweight-pattern.md)

---

### Proxy

Provides a surrogate or placeholder for another object to control access to it (lazy loading, access control, remote invocation, logging).

**Trigger phrase**: "I need to control/mediate access to an object without changing its interface" (virtual proxy for lazy load, protection proxy for auth checks).

**Reference**: [proxy-pattern.md](02-structural/proxy-pattern.md)

---

## Design Patterns — Behavioral

Patterns concerned with algorithms and the assignment of responsibility/communication between objects.

### Strategy

Defines a family of interchangeable algorithms, encapsulates each one, and makes them swappable at runtime.

**Trigger phrase**: "the algorithm/behavior varies independently of the client that uses it" (e.g., payment method, sorting comparator, compression algorithm).

**Reference**: [strategy-pattern.md](03-behavioral/strategy-pattern.md)

---

### Observer

Defines a one-to-many dependency so that when one object (subject) changes state, all its dependents (observers) are notified automatically.

**Trigger phrase**: "multiple parts of the system need to react when this object's state changes, and I don't want the subject to know their concrete types" (event listeners, pub-sub within a process).

**Reference**: [observer-pattern.md](03-behavioral/observer-pattern.md)

---

### State

Allows an object to alter its behavior when its internal state changes, appearing to change its class.

**Trigger phrase**: "this object's allowed operations depend on which state it's in, and I have a growing `if/else`/`switch` on a state enum" (e.g., order lifecycle, TCP connection states, vending machine).

**Distinguish from Strategy**: Structurally identical (both delegate to an interchangeable object), but State transitions *itself* (state objects trigger the next state), while Strategy is chosen and set by the client.

**Reference**: [state-pattern.md](03-behavioral/state-pattern.md)

---

### Command

Encapsulates a request as an object, letting you parameterize clients with queues, requests, and operations, and supporting undo/redo.

**Trigger phrase**: "I need to queue, log, or undo an operation" (e.g., GUI button actions, transactional operations, job queues).

**Reference**: [command-pattern.md](03-behavioral/command-pattern.md)

---

### Chain of Responsibility

Passes a request along a chain of handlers until one handles it.

**Trigger phrase**: "multiple objects might handle a request, and I don't want the sender to know which one will" (e.g., middleware pipelines, approval workflows, exception handler chains).

**Reference**: [chain-of-responsibility.md](03-behavioral/chain-of-responsibility.md)

---

### Template Method

Defines the skeleton of an algorithm in a base class method, deferring specific steps to subclasses.

**Trigger phrase**: "several subclasses share the same overall algorithm structure but differ in a few specific steps" (e.g., a data-processing pipeline with a fixed read → process → write shape).

**Distinguish from Strategy**: Template Method uses inheritance (subclass overrides steps); Strategy uses composition (client injects a whole algorithm object).

**Reference**: [template-method-pattern.md](03-behavioral/template-method-pattern.md)

---

### Iterator

Provides a way to access elements of a collection sequentially without exposing its underlying representation.

**Trigger phrase**: "callers need to traverse this collection without knowing if it's an array, tree, or linked list underneath."

**Reference**: [iterator-pattern.md](03-behavioral/iterator-pattern.md)

---

### Mediator

Defines an object that encapsulates how a set of objects interact, avoiding direct references between them.

**Trigger phrase**: "these objects all need to talk to each other, and direct references would create an N² web of dependencies" (e.g., a chat room mediating between users, an air traffic control tower).

**Reference**: [mediator-pattern.md](03-behavioral/mediator-pattern.md)

---

### Memento

Captures and externalizes an object's internal state without violating encapsulation, so it can be restored later.

**Trigger phrase**: "I need undo/checkpoint/rollback functionality without exposing the object's internal fields to the code that manages the history."

**Reference**: [memento-pattern.md](03-behavioral/memento-pattern.md)

---

### Visitor

Represents an operation to be performed on the elements of an object structure, letting you add new operations without changing the classes of the elements.

**Trigger phrase**: "I need to add a new operation across a fixed set of unrelated classes (e.g., an AST) without modifying each class" — the classic OCP-vs-visitor tradeoff: adding a new *operation* is easy, adding a new *element type* requires touching every visitor.

**Reference**: [visitor-pattern.md](03-behavioral/visitor-pattern.md)

---

### Interpreter

Given a language, defines a representation for its grammar along with an interpreter that uses the representation to interpret sentences in the language.

**Trigger phrase**: "I need to parse and evaluate a simple, well-defined grammar" (e.g., a rules engine, a basic expression evaluator) — rarely reached for outside of DSL/parsing-flavored interview prompts.

**Reference**: [interpreter-pattern.md](03-behavioral/interpreter-pattern.md)

---

## Concurrency

### Race Condition

A bug where the correctness of the result depends on the relative timing/interleaving of multiple threads.

**Real example**: Two threads both read `count = 5`, both increment locally, both write `6` — one increment is lost.

**Fix**: Mutual exclusion (lock/synchronized block), atomic operations (`AtomicInteger`), or immutable state.

---

### Deadlock

A state where two or more threads are each waiting for a resource held by the other, so none can proceed.

**Classic cause**: Thread A locks resource 1 then waits for resource 2; Thread B locks resource 2 then waits for resource 1.

**Fix**: Consistent lock ordering across all threads, lock timeouts, or avoiding nested locks entirely.

**Reference**: [concurrency-patterns.md](04-concurrency/concurrency-patterns.md)

---

### Mutex vs. Semaphore

A mutex is a binary lock granting exclusive access to one thread at a time; a semaphore holds a count of N permits, allowing up to N threads concurrent access.

**Key distinction**: A mutex has ownership (only the thread that locked it can unlock it); a semaphore does not — any thread can release a permit.

**Real example**: A mutex protects a single shared counter; a semaphore with N=10 limits concurrent connections to a resource pool of 10.

---

### Thread-Safe Singleton

A Singleton implementation that guarantees only one instance is created even under concurrent first-access from multiple threads.

**Mechanisms**: Double-checked locking with a `volatile` field, the initialization-on-demand holder idiom (lazy, no locking overhead after class load), or an `enum`-based singleton (JVM guarantees this).

**Reference**: [thread-safe-singleton.md](04-concurrency/thread-safe-singleton.md)

---

### Producer-Consumer

A concurrency pattern where producer threads generate data onto a shared, bounded queue and consumer threads process it, decoupling the two at different rates via the queue's blocking behavior.

**Real example**: `BlockingQueue` in Java — producers block on `put()` when full, consumers block on `take()` when empty.

**Reference**: [producer-consumer.md](04-concurrency/producer-consumer.md)

---

## UML Relationships

### Association

A general "uses" relationship between two classes with no ownership implied — the loosest form of coupling in a class diagram.

**Real example**: A `Teacher` and a `Student` — a teacher teaches many students, but neither owns the other's lifecycle.

---

### Aggregation

A "has-a," whole-part relationship where the part can exist independently of the whole (shared ownership, hollow-diamond arrow in UML).

**Real example**: A `Department` has `Professor`s — if the department is dissolved, the professors still exist.

---

### Composition

A "owns-a," whole-part relationship where the part's lifecycle is bound to the whole (filled-diamond arrow in UML) — the part is destroyed when the whole is.

**Real example**: A `House` is composed of `Room`s — delete the house, the rooms cease to exist as separate entities.

**Interview angle**: This is the same "composition" invoked in "favor composition over inheritance," but that phrase actually covers both aggregation and composition loosely — in UML terms specifically, composition implies the stronger lifecycle binding.

---

### Inheritance (Generalization)

An "is-a" relationship shown as a solid line with a hollow triangle arrow pointing to the parent class.

**Real example**: `SavingsAccount` → `Account`.

---

### Realization

An "implements" relationship between a class and an interface, shown as a dashed line with a hollow triangle arrow — the class commits to a contract with no shared implementation inherited.

**Real example**: `ArrayList` realizes `List`.

---

## Idiomatic Interview Phrasings

- "I'm using **composition over inheritance** here because behavior needs to be swappable at runtime, not fixed at compile time."
- "This is the **Strategy pattern** because the algorithm varies independently of the client that consumes it."
- "This violates **OCP** — adding a new payment type means editing this existing method instead of adding a new class."
- "I'd make this a **thread-safe Singleton** using the holder idiom to avoid synchronization overhead on every access after initialization."
- "These two classes have a **composition** relationship, not aggregation — the child has no meaning outside the parent's lifecycle."

---

**Related:** [08-reference/system-design-glossary.md](../08-reference/system-design-glossary.md) — the HLD/distributed-systems counterpart to this glossary. Start at [06-lld/README.md](README.md) or [00-methodology/README.md](00-methodology/README.md) if you haven't yet.
