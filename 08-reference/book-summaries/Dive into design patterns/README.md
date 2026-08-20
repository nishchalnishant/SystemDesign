# Dive Into Design Patterns — Complete Notes

Chapter-wise notes for **Alexander Shvets, *Dive Into Design Patterns*** (Refactoring.Guru, v2021-2.34).
Every chapter is captured in full: problem narratives, solutions, real-world analogies, structure breakdowns with ASCII UML diagrams, the book's complete pseudocode listings (verbatim, with original line numbering), applicability, implementation steps, pros/cons, and inter-pattern relations.

- [About This Book](00-about-this-book.md) — how to read it, full printed table of contents

---

## 1. Introduction to OOP

| # | Chapter |
|---|---------|
| 1 | [Basics of OOP](01-oop/01-basics-of-oop.md) — objects, classes, hierarchies, UML basics |
| 2 | [Pillars of OOP](01-oop/02-pillars-of-oop.md) — abstraction, encapsulation, inheritance, polymorphism |
| 3 | [Relations Between Objects](01-oop/03-relations-between-objects.md) — dependency, association, aggregation, composition |

## 2. Introduction to Design Patterns

| # | Chapter |
|---|---------|
| 1 | [What's a Design Pattern?](02-patterns-intro/01-what-is-a-design-pattern.md) — history, classification, what's in a pattern |
| 2 | [Why Should I Learn Patterns?](02-patterns-intro/02-why-should-i-learn-patterns.md) |

## 3. Software Design Principles

| # | Chapter |
|---|---------|
| 1 | [Features of Good Design](03-principles/01-features-of-good-design.md) — code reuse, extensibility |
| 2 | [Design Principles](03-principles/02-design-principles.md) — Encapsulate What Varies · Program to an Interface · Favor Composition Over Inheritance |
| 3 | [SOLID Principles](03-principles/03-solid-principles.md) — SRP · OCP · LSP · ISP · DIP |

---

## 4. Creational Patterns

> **Mnemonic:** **FAB PS** (Fabulous PS)  
> **Phrase:** **F**ive **A**rchitects **B**uild **P**erfect **S**ystems  
> *(Factory Method, Abstract Factory, Builder, Prototype, Singleton)*

[Overview](04-creational/00-creational-overview.md)

| # | Pattern | Intent |
|---|---------|--------|
| 1 | [Factory Method](04-creational/01-factory-method.md) | Provides an interface for creating objects in a superclass, but allows subclasses to alter the type of objects that will be created. |
| 2 | [Abstract Factory](04-creational/02-abstract-factory.md) | Lets you produce families of related objects without specifying their concrete classes. |
| 3 | [Builder](04-creational/03-builder.md) | Lets you construct complex objects step by step, producing different types and representations from the same construction code. |
| 4 | [Prototype](04-creational/04-prototype.md) | Lets you copy existing objects without making your code dependent on their classes. |
| 5 | [Singleton](04-creational/05-singleton.md) | Ensures a class has only one instance, while providing a global access point to it. |

## 5. Structural Patterns

> **Mnemonic:** **ABCD FFP**  
> **Phrase:** **A**ll **B**rave **C**ats **D**o **F**ly **F**or **P**rey  
> *(Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy)*

[Overview](05-structural/00-structural-overview.md)

| # | Pattern | Intent |
|---|---------|--------|
| 1 | [Adapter](05-structural/01-adapter.md) | Allows objects with incompatible interfaces to collaborate. |
| 2 | [Bridge](05-structural/02-bridge.md) | Lets you split a large class or set of closely related classes into two separate hierarchies — abstraction and implementation. |
| 3 | [Composite](05-structural/03-composite.md) | Lets you compose objects into tree structures and then work with these structures as if they were individual objects. |
| 4 | [Decorator](05-structural/04-decorator.md) | Lets you attach new behaviors to objects by placing them inside special wrapper objects. |
| 5 | [Facade](05-structural/05-facade.md) | Provides a simplified interface to a library, a framework, or any other complex set of classes. |
| 6 | [Flyweight](05-structural/06-flyweight.md) | Lets you fit more objects into available RAM by sharing common parts of state between multiple objects. |
| 7 | [Proxy](05-structural/07-proxy.md) | Lets you provide a substitute or placeholder for another object. |

## 6. Behavioral Patterns

> **Mnemonic:** **VIM COMICS S** (or **2C 2M 2S I O T V**)  
> **Phrase:** **C**razy **C**oding **I**nterviews **M**ake **M**e **O**bserve **S**tate **S**trategy **T**o **V**ictory!  
> *(Chain of Responsibility, Command, Iterator, Mediator, Memento, Observer, State, Strategy, Template Method, Visitor)*

[Overview](06-behavioral/00-behavioral-overview.md)

| # | Pattern | Intent |
|---|---------|--------|
| 1 | [Chain of Responsibility](06-behavioral/01-chain-of-responsibility.md) | Lets you pass requests along a chain of handlers, each deciding to process it or pass it on. |
| 2 | [Command](06-behavioral/02-command.md) | Turns a request into a stand-alone object containing all information about the request. |
| 3 | [Iterator](06-behavioral/03-iterator.md) | Lets you traverse elements of a collection without exposing its underlying representation. |
| 4 | [Mediator](06-behavioral/04-mediator.md) | Lets you reduce chaotic dependencies between objects by forcing them to collaborate via a mediator. |
| 5 | [Memento](06-behavioral/05-memento.md) | Lets you save and restore the previous state of an object without revealing implementation details. |
| 6 | [Observer](06-behavioral/06-observer.md) | Lets you define a subscription mechanism to notify multiple objects about events. |
| 7 | [State](06-behavioral/07-state.md) | Lets an object alter its behavior when its internal state changes. |
| 8 | [Strategy](06-behavioral/08-strategy.md) | Lets you define a family of algorithms, put each into a separate class, and make their objects interchangeable. |
| 9 | [Template Method](06-behavioral/09-template-method.md) | Defines the skeleton of an algorithm in the superclass but lets subclasses override specific steps. |
| 10 | [Visitor](06-behavioral/10-visitor.md) | Lets you separate algorithms from the objects on which they operate. |

---

- [08-mnemonics-cheatsheet.md](08-mnemonics-cheatsheet.md) — Acronyms and phrases to remember the patterns
- [09-sde3-interview-relevance.md](09-sde3-interview-relevance.md) — Patterns tiered by relevance to SDE-3 LLD/system-design interviews
- [Conclusion & Footnotes](07-conclusion.md)

---

## Quick Reference: Pattern Selection

**"I need to create objects flexibly"** → Creational
- One instance globally → **Singleton**
- Subclass decides the concrete type → **Factory Method**
- Families of related products that must match → **Abstract Factory**
- Many optional construction parameters / different representations → **Builder**
- Copying is cheaper or the class is unknown → **Prototype**

**"I need to compose objects into larger structures"** → Structural
- Incompatible interface → **Adapter**
- Two independent dimensions of variation → **Bridge**
- Tree of part-whole objects treated uniformly → **Composite**
- Add responsibilities at runtime by wrapping → **Decorator**
- Simplify a complex subsystem → **Facade**
- Too many objects, share intrinsic state → **Flyweight**
- Control access / lazy-init / log / cache → **Proxy**

**"I need to manage algorithms and communication"** → Behavioral
- Pipeline of optional handlers → **Chain of Responsibility**
- Requests as objects, undo, queueing → **Command**
- Uniform traversal of collections → **Iterator**
- Many-to-many object communication → **Mediator**
- Snapshots and undo → **Memento**
- Publish/subscribe events → **Observer**
- Behavior varies by internal state, states know each other → **State**
- Interchangeable algorithms, independent of each other → **Strategy**
- Fixed algorithm skeleton, variable steps (inheritance) → **Template Method**
- New operations over a stable class hierarchy → **Visitor**
