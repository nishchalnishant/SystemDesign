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

<table><thead><tr><th width="111.87109375">Pattern</th><th width="487.5703125">Purpose</th><th>Use When</th><th data-hidden>Category</th></tr></thead><tbody><tr><td><mark style="color:$danger;"><strong>Singleton</strong></mark></td><td>Ensure a class has only one instance and provide a global access point to it</td><td>Need global access point</td><td>Creational</td></tr><tr><td><mark style="color:red;"><strong>Factory Method</strong></mark></td><td>Create objects without specifying the exact class of object that will be created</td><td>Subclasses decide which class to instantiate</td><td>Creational</td></tr><tr><td><mark style="color:green;"><strong>Abstract Factory</strong></mark></td><td>provides an interface for creating <strong>families of related or dependent objects</strong> without specifying their concrete classes.</td><td>Need consistent product families</td><td>Creational</td></tr><tr><td><mark style="color:green;"><strong>Builder</strong></mark></td><td>separates the construction of a complex object from its representation</td><td>Object has many optional parameters</td><td>Creational</td></tr><tr><td><strong>Prototype</strong></td><td>used to clone existing objects instead of constructing them from scratch.</td><td>Object creation is expensive</td><td>Creational</td></tr><tr><td><mark style="color:yellow;"><strong>Adapter</strong></mark></td><td>Allows objects with incompatible interfaces to collaborate. Acts as a wrapper/translator.</td><td>Integrate legacy code</td><td>Structural</td></tr><tr><td><strong>Bridge</strong></td><td>used to <strong>decouple an abstraction from its implementation</strong> so that the two can vary independently.</td><td>Both can vary independently</td><td>Structural</td></tr><tr><td><mark style="color:green;"><strong>Composite</strong></mark></td><td>allows you to <strong>compose objects into tree structures</strong> to represent part-whole hierarchies.</td><td>Tree structures needed</td><td>Structural</td></tr><tr><td><mark style="color:yellow;"><strong>Decorator</strong></mark></td><td>Dynamically adds behavior to an object without altering its structure. Avoids "Class Explosion".</td><td>Extend functionality without inheritance</td><td>Structural</td></tr><tr><td><strong>Flyweight</strong></td><td>minimize memory usage by sharing as much data as possible with similar objects.</td><td>Many similar objects</td><td>Structural</td></tr><tr><td><strong>Facade</strong></td><td>Provides a simplified interface to a complex subsystem</td><td>Reduce coupling to subsystem</td><td>Structural</td></tr><tr><td><strong>Proxy</strong></td><td>Provides a placeholder for another object to control access to it (Lazy loading, Access control, Logging).</td><td>Lazy initialization, access control</td><td>Structural</td></tr><tr><td><mark style="color:yellow;"><strong>Command</strong></mark></td><td>encapsulates a request as an object, allowing for more flexible and dynamic command handling. In the upcoming sections, </td><td>Queue, log, or undo operations</td><td>Behavioral</td></tr><tr><td><strong>State</strong></td><td>Allows an object to alter its behavior when its internal state changes. The object will appear to change its class.</td><td>Object behavior depends on state</td><td>Behavioral</td></tr><tr><td><mark style="color:red;"><strong>Observer</strong></mark></td><td>Define a one-to-many dependency so that when one object changes state, all its dependents are notified and updated automatically.</td><td>One-to-many dependency</td><td>Behavioral</td></tr><tr><td><mark style="color:red;"><strong>Strategy</strong></mark></td><td>Define a family of algorithms, encapsulate each one, and make them interchangeable. Strategy lets the algorithm vary independently from clients that use it.</td><td>Algorithm should be interchangeable</td><td>Behavioral</td></tr><tr><td><mark style="color:green;"><strong>Visitor</strong></mark></td><td>lets you add new operations to existing class hierarchies without modifying the classes themselves.</td><td>Operations on object structure</td><td>Behavioral</td></tr><tr><td><strong>Mediator</strong></td><td><p></p><p>centralizes complex communication between objects into a single mediation object.</p></td><td>Reduce object dependencies</td><td>Behavioral</td></tr><tr><td><strong>Iterator</strong></td><td><p></p><p>provides a way to access the elements of a collection sequentially without exposing the underlying representation.</p></td><td>Abstract collection traversal</td><td>Behavioral</td></tr><tr><td><strong>Chain of Responsibility</strong></td><td>sets up a chain where each team can either process the request or pass it to the next team in the chain.</td><td>Multiple objects may handle request</td><td>Behavioral</td></tr><tr><td><mark style="color:yellow;"><strong>Template Method</strong></mark></td><td>provides a blueprint for executing an algorithm. It allows subclasses to override specific steps of the algorithm, but the overall structure remains the same.</td><td>Subclasses override specific steps</td><td>Behavioral</td></tr></tbody></table>

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

## Real-World Examples

* **Singleton**: Database connection pool, Logger
* **Factory**: UI component libraries (ButtonFactory)
* **Observer**: Event handling systems, MVC
* **Decorator**: Java I/O streams, UI component wrappers
* **Strategy**: Payment processing, sorting algorithms
* **Adapter**: Legacy system integration
* **Facade**: Simplified API for complex libraries

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
