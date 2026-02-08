# Design Patterns

Design patterns are typical solutions to common problems in software design. Each pattern is like a blueprint that you can customize to solve a particular design problem in your code.

---

## Pattern Categories

Design patterns are categorized into three main types based on their purpose:

### 🏗️ [Creational Patterns](./creational/README.md)
Deal with object creation mechanisms, trying to create objects in a manner suitable to the situation.

**Patterns:** Abstract Factory, Builder, Factory Method, Prototype, Singleton

### 🧩 [Structural Patterns](./structural/README.md)
Explain how to assemble objects and classes into larger structures while keeping these structures flexible and efficient.

**Patterns:** Adapter, Bridge, Composite, Decorator, Facade, Flyweight, Proxy

### 🎭 [Behavioral Patterns](./behavioral/README.md)
Concerned with algorithms and the assignment of responsibilities between objects.

**Patterns:** Chain of Responsibility, Command, Iterator, Mediator, Observer, State, Strategy, Template Method, Visitor

---

## Quick Reference Table

| Pattern | Category | Purpose | Use When |
|---------|----------|---------|----------|
| **Abstract Factory** | Creational | Create families of related objects | Need consistent product families |
| **Builder** | Creational | Construct complex objects step by step | Object has many optional parameters |
| **Factory Method** | Creational | Define interface for creating objects | Subclasses decide which class to instantiate |
| **Prototype** | Creational | Clone existing objects | Object creation is expensive |
| **Singleton** | Creational | Ensure only one instance exists | Need global access point |
| **Adapter** | Structural | Make incompatible interfaces work together | Integrate legacy code |
| **Bridge** | Structural | Separate abstraction from implementation | Both can vary independently |
| **Composite** | Structural | Treat individual and composite objects uniformly | Tree structures needed |
| **Decorator** | Structural | Add responsibilities dynamically | Extend functionality without inheritance |
| **Facade** | Structural | Simplify complex subsystem interface | Reduce coupling to subsystem |
| **Flyweight** | Structural | Share common state to save memory | Many similar objects |
| **Proxy** | Structural | Control access to objects | Lazy initialization, access control |
| **Chain of Responsibility** | Behavioral | Pass request along handler chain | Multiple objects may handle request |
| **Command** | Behavioral | Encapsulate request as object | Queue, log, or undo operations |
| **Iterator** | Behavioral | Access collection elements sequentially | Abstract collection traversal |
| **Mediator** | Behavioral | Centralize complex communications | Reduce object dependencies |
| **Observer** | Behavioral | Notify dependents of state changes | One-to-many dependency |
| **State** | Behavioral | Alter behavior when state changes | Object behavior depends on state |
| **Strategy** | Behavioral | Define family of algorithms | Algorithm should be interchangeable |
| **Template Method** | Behavioral | Define algorithm skeleton | Subclasses override specific steps |
| **Visitor** | Behavioral | Separate algorithm from object structure | Operations on object structure |

---

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

---

## Interview Tips

**Common Questions:**
- Explain the difference between Strategy and State patterns
- When would you use Abstract Factory vs Factory Method?
- How does Decorator differ from inheritance?
- What problems does Singleton solve and what are its drawbacks?

**Be Ready to:**
- Draw UML diagrams
- Write code examples
- Discuss real-world use cases
- Explain trade-offs

---

## Real-World Examples

- **Singleton**: Database connection pool, Logger
- **Factory**: UI component libraries (ButtonFactory)
- **Observer**: Event handling systems, MVC
- **Decorator**: Java I/O streams, UI component wrappers
- **Strategy**: Payment processing, sorting algorithms
- **Adapter**: Legacy system integration
- **Facade**: Simplified API for complex libraries

---

## Anti-Patterns to Avoid

❌ **God Object**: One class does everything  
❌ **Spaghetti Code**: No clear structure  
❌ **Golden Hammer**: Using same pattern everywhere  
❌ **Premature Optimization**: Overengineering simple problems  

---

## Resources

- **Book**: "Design Patterns: Elements of Reusable Object-Oriented Software" (Gang of Four)
- **Website**: [Refactoring.Guru](https://refactoring.guru/design-patterns)
- **Video**: [Christopher Okhravi's YouTube Series](https://www.youtube.com/playlist?list=PLrhzvIcii6GNjpARdnO4ueTUAVR9eMBpc)
