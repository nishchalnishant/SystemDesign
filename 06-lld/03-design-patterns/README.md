> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design Patterns — 23 standard solutions to common software design problems, organized into Creational, Structural, and Behavioral categories.
>
> **Categories:**
> - Creational (5): How objects are created (Singleton, Factory Method, Abstract Factory, Builder, Prototype). They abstract the instantiation process.
> - Structural (7): How classes and objects are composed to form larger structures (Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy).
> - Behavioral (11): How objects communicate and assign responsibilities (Strategy, Observer, Command, State, Chain of Responsibility, etc.).
>
> **Most critical for LLD interviews:**
> - Creational: Factory Method, Builder (for complex objects), Singleton (for shared state, though it's an anti-pattern in large systems).
> - Structural: Decorator (add behavior without inheritance), Adapter (connect incompatible interfaces), Composite (tree structures like file systems).
> - Behavioral: Strategy (swap algorithms), Observer (event systems like Pub/Sub), State (state machines like Vending Machines).
>
> **Key takeaway:** Do not memorize all 23 patterns. Focus on the core 8–10 that appear constantly in LLD interviews. Apply a pattern only when the specific problem it solves arises (e.g., use Strategy when you see multiple algorithms for the same task).

---

# Design Patterns

Design patterns are typical solutions to common problems in software design. Each pattern is like a blueprint that you can customize to solve a particular design problem in your code.

***

## Pattern Categories

Design patterns are categorised into three main types based on their purpose:

### &#x20;[Creational Patterns](01-creational/)

Deal with object creation mechanisms, trying to create objects in a manner suitable to the situation.

**Patterns:** Abstract Factory, Builder, Factory Method, Prototype, Singleton

### &#x20;[Structural Patterns](02-structural/)

Explain how to assemble objects and classes into larger structures while keeping these structures flexible and efficient.

**Patterns:** Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy

### &#x20;[Behavioral Patterns](03-behavioral/)

Concerned with algorithms and the assignment of responsibilities between objects.

**Patterns:** Chain of Responsibility, Command, Iterator, Mediator, Observer, State, Strategy, Template Method, Visitor

***

## Quick Reference Table

One line on what it solves, a real system that uses it, and how it's actually wired up in code — enough to recognize the shape when it shows up in an interview prompt.

| Pattern | Purpose | Real-World Example | How It's Implemented |
|---|---|---|---|
| **Singleton** | Ensure a class has only one instance with a global access point | Database connection pool, Logger | A private constructor + a static `getInstance()` method that lazily creates and caches the one instance |
| **Factory Method** | Create objects without the caller specifying the exact class | `DocumentFactory.create("pdf")` in an office suite | A creator class defines an abstract `create()` method; subclasses override it to return a specific product type |
| **Abstract Factory** | Create families of related objects without specifying concrete classes | Cross-platform UI toolkit (`WinFactory` vs `MacFactory` producing matching Button/Checkbox) | An interface with one factory method per product; a concrete factory per family implements all of them consistently |
| **Builder** | Separate construction of a complex object from its representation | `StringBuilder`, HTTP request builders, SQL query builders | A separate `Builder` class accumulates optional fields via chained setters, then a `build()` call assembles the final immutable object |
| **Prototype** | Clone existing objects instead of building from scratch | Cloning a pre-configured game character/enemy template | A `clone()` method on the object performs a deep copy of itself instead of the caller re-running the full constructor logic |
| **Adapter** | Let incompatible interfaces collaborate by wrapping one in the other | Plugging a legacy XML parser into code expecting JSON | A wrapper class implements the target interface and internally translates calls to the wrapped (adaptee) object |
| **Bridge** | Decouple an abstraction from its implementation so both vary independently | Remote control (abstraction) working across TV brands (implementation) | The abstraction class holds a reference to an implementor interface instead of extending a concrete implementation |
| **Composite** | Compose objects into tree structures representing part-whole hierarchies | File system (files and folders treated uniformly) | A common `Component` interface is implemented by both `Leaf` and `Composite` (which holds a list of children and delegates to them) |
| **Decorator** | Add behavior to an object dynamically without subclassing | Java I/O streams (`BufferedInputStream(new FileInputStream(...))`) | A decorator class implements the same interface as the wrapped object, holds a reference to it, and adds behavior before/after delegating |
| **Flyweight** | Minimize memory by sharing data common to many similar objects | Character glyphs in a text editor, tree instances in a game world | A factory returns cached shared instances keyed by intrinsic state; extrinsic (per-use) state is passed in at call time, not stored |
| **Facade** | Provide a simplified interface to a complex subsystem | `OrderService.placeOrder()` hiding inventory/payment/shipping calls | One class exposes a few high-level methods that internally coordinate calls across the subsystem's many classes |
| **Proxy** | Control access to another object (lazy load, access control, logging) | Hibernate lazy-loaded entities, Spring `@Transactional` proxies | A proxy class implements the same interface as the real object, holds a reference to it, and adds a check/delay before delegating |
| **Command** | Encapsulate a request as an object for queuing, logging, or undo | Redo/undo stack in a text editor, remote-control button bindings | Each action becomes a class implementing an `execute()` (and often `undo()`) method, invoked polymorphically instead of calling methods directly |
| **State** | Let an object alter its behavior when its internal state changes | Vending machine, TCP connection states, order lifecycle | Each state is a class implementing a common interface; the context object delegates to its current state object and swaps it on transition |
| **Observer** | Notify many dependents automatically when one object's state changes | Pub/Sub systems, UI event listeners, MVC view updates | Subscribers register with a subject via `subscribe()`; the subject loops over and calls `notify()`/`update()` on all of them on change |
| **Strategy** | Define a family of interchangeable algorithms | Payment processing (credit card vs. PayPal vs. wallet), sort comparators | The context holds a reference to a strategy interface, injected at construction/runtime, and delegates the algorithm call to it |
| **Visitor** | Add new operations to a class hierarchy without modifying the classes | AST processing in compilers (type-checking, code-gen as separate visitors) | Each element accepts a visitor via `accept(visitor)`, which calls back `visitor.visit(this)` — double dispatch picks the right overload |
| **Mediator** | Centralize complex communication between objects into one object | Air traffic control tower coordinating planes, chat room relaying messages | Colleague objects only talk to the mediator, never each other directly; the mediator holds references to all colleagues and routes messages |
| **Iterator** | Access elements of a collection sequentially without exposing internals | Java's `Iterator`/`Iterable` powering the for-each loop | The collection returns an iterator object exposing `hasNext()`/`next()`, hiding whether it's backed by an array, list, or tree |
| **Chain of Responsibility** | Let a request pass along a chain until some handler processes it | Middleware chains, support-ticket escalation tiers | Each handler holds a reference to the next handler; it either processes the request or forwards it down the chain |
| **Template Method** | Define an algorithm's skeleton, letting subclasses override specific steps | `AbstractList`, framework lifecycle hooks (`onCreate`/`onStart`) | A base class defines a `final` method that calls a fixed sequence of steps; subclasses override the individual step methods, not the sequence |

***

## Learning Path

### Beginner

Start with these fundamental patterns:

1. **Singleton** - Simplest creational pattern
2. **Factory Method** - Foundation for other patterns
3. **Strategy** - Easy to understand behavioral pattern
4. **Observer** - Common in event-driven systems

### Intermediate

5. **Decorator** - Flexible alternative to inheritance
6. **Adapter** - Common in integration scenarios
7. **Template Method** - Good for frameworks
8. **Command** - Important for undo/redo

### Advanced

9. **Abstract Factory** - Complex but powerful
10. **Builder** - Essential for fluent APIs
11. **Composite** - Tree structures
12. **Visitor** - Most complex, but very useful

***

## Interview Tips

**Common Questions:**

* Explain the difference between Strategy and State patterns
* When would you use Abstract Factory vs Factory Method?
* How does Decorator differ from inheritance?
* What problems does Singleton solve and what are its drawbacks?

**Be Ready to:**

* Draw UML diagrams
* Write code examples
* Discuss real-world use cases
* Explain trade-offs

***

## Anti-Patterns to Avoid

❌ **God Object**: One class does everything\
❌ **Spaghetti Code**: No clear structure\
❌ **Golden Hammer**: Using same pattern everywhere\
❌ **Premature Optimization**: Overengineering simple problems

***

## Resources

* **Book**: "Design Patterns: Elements of Reusable Object-Oriented Software" (Gang of Four)
* **Website**: [Refactoring.Guru](https://refactoring.guru/design-patterns)
* **Video**: [Christopher Okhravi's YouTube Series](https://www.youtube.com/playlist?list=PLrhzvIcii6GNjpARdnO4ueTUAVR9eMBpc)
