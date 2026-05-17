# Behavioral Design Patterns

Behavioral patterns are concerned with algorithms and the assignment of responsibilities between objects.

## Patterns in this Category

### 

### [Observer](./observer-pattern.md)
Defines a subscription mechanism to notify multiple objects about events.
* **Interview Examples:**
  * Stock market ticker where multiple dashboards update when prices change.
  * Event-driven systems (Pub/Sub like Kafka/RabbitMQ concepts).
  * Model-View-Controller (MVC) where View observes Model changes.
* **Java Implementation:**
  * `interface` for the Observer (with an `update()` method).
  * `interface` or `class` for the Subject maintaining a List of Observers.

### [Strategy](./strategy-pattern.md)
Defines a family of algorithms and makes them interchangeable.
* **Interview Examples:**
  * Payment Gateway (Credit Card, PayPal, UPI strategies calculating different fees/processes).
  * Navigation map routing (Driving, Walking, Transit strategies).
  * Sorting algorithms (QuickSort, MergeSort based on data size or type).
* **Java Implementation:**
  * `interface` for the Strategy algorithm.
  * `class` for the Context holding a reference to a Strategy and delegating work to it.

### [Command](./command-pattern.md)
Encapsulates a request as an object, allowing parameterization and queuing of requests.
* **Interview Examples:**
  * Implementing Undo/Redo functionality in text editors.
  * GUI buttons where each button triggers a specific command object.
  * Job Queue processing where tasks are executed asynchronously.
* **Java Implementation:**
  * `interface` for the Command (with an `execute()` method).
  * `class` for Concrete Commands storing a reference to the Receiver.

### [State](./state-pattern.md)
Allows an object to alter its behavior when its internal state changes.
* **Interview Examples:**
  * Vending Machine state transitions (HasCoin, NoCoin, Dispensing).
  * Media Player (Play, Pause, Stop states).
  * Document workflows (Draft, In-Review, Published).
* **Java Implementation:**
  * `interface` for the State defining behaviors.
  * `class` for the Context holding a reference to the current State.


### [Visitor](./visitor-pattern.md)
Separates algorithms from the objects they operate on.
* **Interview Examples:**
  * Abstract Syntax Tree (AST) parsing in compilers.
  * Exporting data from a complex object hierarchy (e.g., returning JSON/XML from a document structure).
  * Processing tax calculations on different types of grocery items.
* **Java Implementation:**
  * `interface` for the Visitor (with overloaded `visit()` methods for each element type).
  * `interface` for the Element (with an `accept(Visitor)` method).

### [Iterator](./iterator-pattern.md)
Provides a way to access elements of a collection sequentially without exposing underlying representation.
* **Interview Examples:**
  * Custom collection classes in Java/C++ (like Trees, Graphs) providing a unified way to iterate over nodes.
  * Paginating results from a database.
* **Java Implementation:**
  * `interface` for the Iterator (with `hasNext()` and `next()`).
  * `interface` for the Iterable Collection (with a method returning the Iterator).

### [Mediator](./mediator-pattern.md)
Reduces coupling by making objects communicate through a mediator rather than directly.
* **Interview Examples:**
  * Chat Room application where users don't talk directly but through a central server.
  * Air Traffic Control system coordinating planes.
  * UI Dialog boxes coordinating complex interactions between various UI components.
* **Java Implementation:**
  * `interface` for the Mediator.
  * `abstract class` or `class` for Colleagues holding a reference to the Mediator.

[Chain of Responsibility](./chain-of-responsibility.md)
Passes requests along a chain of handlers, where each handler decides to process or pass it.
* **Interview Examples:**
  * Middleware in web frameworks (e.g., Express.js, Spring Security filters).
  * Logging frameworks where a log goes through multiple filters before being written.
  * Request approval workflows (Manager -> Director -> VP).
* **Java Implementation:**
  * `abstract class` for the Handler (holds a reference to the `nextHandler`).
  * `class` for each Concrete Handler implementing the processing logic.
  
### [Template Method](./template-method-pattern.md)
Defines the skeleton of an algorithm, letting subclasses override specific steps.
* **Interview Examples:**
  * Data parsing framework where steps are (Open, Parse, Close) and subclasses implement `Parse` (CSV, XML, JSON).
  * Game AI behavior skeletons.
  * UI lifecycle methods (React/Android component lifecycles).
* **Java Implementation:**
  * `abstract class` with a `final` template method and `abstract` primitive operation steps.


---

## When to Use Behavioral Patterns

- When you need flexible communication between objects
- When you want to define algorithms independently
- When object behavior depends on state
- When you need to decouple sender and receiver
