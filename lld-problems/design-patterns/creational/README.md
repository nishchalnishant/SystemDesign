# Creational Design Patterns

Creational patterns provide various object creation mechanisms, which increase flexibility and reuse of existing code.

## Patterns in this Category

### [Abstract Factory](./abstract-factory-pattern.md)
Provides an interface for creating families of related objects without specifying concrete classes.
* **Interview Examples:**
  * Cross-platform UI toolkit (Windows/Mac button and checkbox creation).
  * Car manufacturing system producing a family of parts (Engine, Tires) for different car models (Sports, SUV).
* **Java Implementation:**
  * `interface` for the Abstract Factory predicting family creation methods.
  * `interface` for each Abstract Product family.

### [Builder](./builder-pattern.md)
Constructs complex objects step by step, allowing different representations using the same construction process.
* **Interview Examples:**
  * Constructing an HTTP Request object or a SQL Query builder.
  * Meal ordering in a restaurant where a meal consists of various configurable parts (Burger, Drink, Toy).
  * Designing a customizable computer (CPU, RAM, Storage).
* **Java Implementation:**
  * `class` or `interface` for the Builder with chainable methods (returning `this`).
  * Often implemented as a `public static class` nested within the Target Product class.

### [Factory Method](./factory-pattern.md)
Defines an interface for creating objects but lets subclasses decide which class to instantiate.
* **Interview Examples:**
  * Logistics management where a `Transport` factory creates `Truck` or `Ship`.
  * Database connection driver factory treating MySQL, PostgreSQL, or Oracle instances interchangeably.
  * Document creator where `WordConverter` creates `.docx` and `PDFConverter` creates `.pdf`.
* **Java Implementation:**
  * `interface` or `abstract class` for the Creator declaring an abstract `create()` method.
  * `class` for Concrete Creators implementing the factory method.

### [Prototype](./prototype-pattern.md)
Creates new objects by cloning existing objects, avoiding expensive initialization.
* **Interview Examples:**
  * Copy-pasting elements in a graphic editor.
  * Spawning enemy NPCs in a game based on a base template.
  * Caching frequently used complex configuration objects to avoid repeated database hits.
* **Java Implementation:**
  * `interface` extending Java's `Cloneable` or providing a custom `clone()` method.

### [Singleton](./singleton.md)
Ensures a class has only one instance and provides global access to it.
* **Interview Examples:**
  * Database Connection Pool manager.
  * Application Configuration Manager (e.g., reading `application.properties`).
  * Logger service, caching service instance shared across the application.
* **Java Implementation:**
  * `class` with a `private` constructor and a `public static` global access method (with double-checked locking).
  * Alternatively, robustly implemented using an `enum`.

---

## When to Use Creational Patterns

- When object creation is complex or resource-intensive
- When you need to decouple object creation from usage
- When you want to control object instantiation
- When you need families of related objects
