# Structural Design Patterns

Structural patterns explain how to assemble objects and classes into larger structures while keeping these structures flexible and efficient.

## Patterns in this Category

### [Adapter](./adapter-pattern.md)
Allows incompatible interfaces to work together by wrapping an object with an adapter.
* **Interview Examples:**
  * Integrating a legacy XML payment gateway into a modern JSON-based system.
  * Using a third-party analytics library that expects a different object format.
  * Power plug adapters converting European to US plugs.
* **Java Implementation:**
  * `interface` for the Target interface the client expects.
  * `class` for the Adapter that implements the Target and composes (or inherits) the Adaptee.

### [Bridge](./bridge-pattern.md)
Separates abstraction from implementation so they can vary independently.
* **Interview Examples:**
  * Universal remote controls (Abstraction) mapping to different Device types (Implementation like TV, AC).
  * Cross-platform GUI frameworks where visual elements (Buttons, Menus) are separated from OS rendering (Windows, macOS API).
* **Java Implementation:**
  * `interface` for the Implementor defining primitive operations.
  * `abstract class` for the Abstraction holding a reference to the Implementor.

### [Composite](./composite-pattern.md)
Composes objects into tree structures to represent part-whole hierarchies.
* **Interview Examples:**
  * File System structure (Directories containing both Files and other Directories).
  * Organization chart computing total salary (Manager calculating their own salary + sum of subordinates' salaries).
  * UI components (Panels containing Buttons or other Panels).
* **Java Implementation:**
  * `interface` or `abstract class` for the Component defining common operations.
  * `class` for Composite maintaining a robust List of Components alongside `class` for Leaf items.

### [Decorator](./decorator-pattern.md)
Adds new functionality to objects dynamically without altering their structure.
* **Interview Examples:**
  * Ordering a Coffee with add-ons (Milk, Sugar, Caramel) dynamically calculating the cost at runtime.
  * Wrapping a data stream with Encryption and Compression decorators.
  * Adding UI borders or scrollbars to legacy window components.
* **Java Implementation:**
  * `interface` for the Component.
  * `abstract class` for the Decorator implementing Component and maintaining a reference to a wrapped Component.

### [Facade](./facade-pattern.md)
Provides a simplified interface to a complex subsystem.
* **Interview Examples:**
  * E-Commerce checkout service (a simple `placeOrder()` method hiding inventory checks, payment processing, and shipping subsystems).
  * Smart Home app (a "Good Morning" button opening blinds, turning on AC, and brewing coffee).
  * Video conversion library wrapper.
* **Java Implementation:**
  * `class` providing high-level methods that delegate to various complex subsystem classes.

### [Flyweight](./flyweight-pattern.md)
Reduces memory usage by sharing common state among multiple objects.
* **Interview Examples:**
  * Text editors rendering thousands of characters (sharing font and glyph data).
  * Massive multiplayer game environments sharing models (Trees, Buildings) across millions of instances.
  * Caching frequently requested immutable String objects.
* **Java Implementation:**
  * `class` for the Flyweight containing intrinsic (shared) state.
  * `class` for a Factory that maintains a cache (e.g., a `HashMap`) of existing Flyweight instances.

### [Proxy](./proxy-pattern.md)
Provides a surrogate or placeholder to control access to an object.
* **Interview Examples:**
  * Virtual proxy for lazy-loading heavy images on a webpage.
  * Protection proxy for Access Control Lists (ACL) checking user permissions before allowing database modifications.
  * Remote proxy for gRPC/REST API calls masking network communication details.
* **Java Implementation:**
  * `interface` for the Subject.
  * `class` for the Proxy implementing the Subject interface and maintaining a reference to the Real Subject.

---

## When to Use Structural Patterns

- When you need to simplify complex relationships between objects
- When you want to add functionality without modifying existing code
- When you need to optimize memory usage
- When you want to control access to objects
