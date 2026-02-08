---
description: A collection of design patterns and idioms in python
---

# Design Patterns

Each pattern has its own tradeoffs and needs us to pay attention more to why we are choosing a certain pattern than how to implement it.

<figure><img src="../../../.gitbook/assets/image (105).png" alt=""><figcaption></figcaption></figure>

<figure><img src="../../../.gitbook/assets/image (142).png" alt=""><figcaption></figcaption></figure>

<mark style="color:purple;">**Creational Patterns:**</mark> <mark style="color:purple;"></mark><mark style="color:purple;">provide various object creation mechanism which increases flexibiity and reuse of existing code</mark>

* **Singleton:**&#x4F;ne instance, globally accessible. Think of it as the "only one" of something.
* **Factory:**&#x43;reates objects without specifying the exact class. Imagine it as a "factory" that produces different types of items.
* **Abstract Factory:**&#x43;reates families of related objects. Like a "factory" that produces products with different variations.
* **Builder:**&#x43;onstructs complex objects step-by-step. Think of it as building a house with different components.
* **Prototype:**&#x43;reates new objects by copying an existing object. Like a "copy machine" for objects.&#x20;

<mark style="color:purple;">**Structural Patterns:**</mark>

* **Adapter:**&#x43;onverts the interface of a class into another interface clients expect. Think of it as an "adapter" that allows different plugs to fit into a socket.
* **Composite:**&#x43;omposes objects into tree structures to represent part-whole hierarchies. Think of it as a "folder structure" in a file system.
* **Decorator:**&#x44;ynamically adds responsibilities to objects. Think of it as "decorating" an object with additional features.
* **Facade:**&#x50;rovides a simplified interface to a complex subsystem. Think of it as a "front desk" simplifying access to different departments in a company.
* **Flyweight:**&#x53;hares objects to support large numbers of fine-grained objects. Think of it as using the same "template" multiple times.
* **Proxy:**&#x50;rovides a surrogate or placeholder for another object. Think of it as a "remote control" for another object.
* **Bridge:**&#x53;eparates abstraction from implementation. Think of it as decoupling the "remote control" from the specific device.&#x20;

<mark style="color:purple;">**Behavioural Patterns:**</mark>

* **Observer:** Defines a one-to-many dependency between objects. Think of it as a "news subscription" where many subscribers are notified of new content.
* **Strategy:** Defines a family of algorithms and makes them interchangeable. Think of it as choosing different "strategies" for solving a problem.
* **Command:** Encapsulates a request as an object. Think of it as a "command" that can be executed and reversed.
* **Iterator:** Provides a way to access the elements of an aggregate object sequentially. Think of it as a "iterator" for going through a list one item at a time.
* **State:** Allows an object to alter its behavior when its internal state changes. Think of it as a "traffic light" changing its behavior based on its state (red, yellow, green).
* **Memento:** Captures and externalizes an object's internal state. Think of it as creating "checkpoints" to restore an object to a previous state.
* **Template Method:** Defines the skeleton of an algorithm in a method, deferring some steps to subclasses. Think of it as a "template" for cooking a dish with some steps that can be customized.
* **Chain of Responsibility**: Avoids coupling the sender of a request to its receiver by giving multiple objects a chance to handle the request.
* **Interpreter**: Defines a grammatical representation for a language and provides an interpreter to deal with this grammar.
* **Mediator**: Defines an object that encapsulates how a set of objects interact.
* **Visitor**: Represents an operation to be performed on the elements of an object structure.&#x20;



## Others

Apart from these we also have following patterns:

### 1. **Scope**

* **Class Patterns**: These focus on relationships between classes and their subclasses. They tend to use inheritance to achieve their goals. Examples include patterns like Template Method and Factory Method.
* **Object Patterns**: These deal with object relationships and are often more flexible than class patterns, as they rely on composition rather than inheritance. Examples include Singleton, Observer, and State.

### 2. **Purpose**

* **Instantiation Patterns**: These specifically address the creation of instances. They often overlap with Creational patterns, such as Singleton and Factory.
* **Structural Composition Patterns**: These patterns describe how classes and objects are structured to form larger structures. Examples are Composite and Decorator.
* **Behavioral Patterns**: These involve how classes or objects communicate. This aligns with the classic Behavioral category but can also include specific sub-patterns like those for synchronization (e.g., Semaphore patterns).

### 3. **Level of Granularity and Abstraction**

* **Architectural Patterns**: These are larger-scale patterns that typically encompass entire system designs, such as Model-View-Controller (MVC), Microservices, and Layered Architecture. They tend to work at a broader level than the classic design patterns.
* **Design-Level Patterns**: These deal with smaller, specific parts of the system and often refer to individual components or interactions between them, such as Adapter or Proxy.
* **Idioms**: These are language-specific patterns, like RAII in C++, which don’t apply universally but are best practices within a particular language or paradigm.

### 4. **Usage in Modern Paradigms**

* **Concurrency Patterns**: These patterns help manage multi-threaded programming, focusing on the synchronization and safe sharing of resources (e.g., Active Object, Thread Pool).
* **Distributed Patterns**: These are useful for designing systems that operate across multiple machines, such as Broker, Event-Driven Architecture, and Leader Election.
* **Reactive Patterns**: Patterns like Publisher-Subscriber, Reactor, and Circuit Breaker are used in reactive programming, where the system is event-driven and responsive to changes.

### 5. **Domain-Specific Patterns**

* In specific domains like **UI design**, patterns like Model-View-Controller (MVC), Model-View-Presenter (MVP), and Model-View-ViewModel (MVVM) are common.
* In **enterprise applications**, patterns like Data Access Object (DAO), Repository, and Service Locator are useful for managing data and service interactions.

### 6. **Evolution and Versioning of Patterns**

* Some patterns address how to manage **evolving systems**, like Versioned State, which is relevant for systems that need backward compatibility with older versions of software.

## **Importance of choosing the right design patterns**

* **Scalability:** Ensures the architecture can accommodate growth without excessive restructuring.
* **Flexibility:** Enables easy adaptation to changing requirements and future enhancements.
* **Maintainability:** Facilitates code readability and comprehension, easing maintenance tasks.
* **Reusability:** Promotes reuse of proven solutions, saving development time and effort.
* **Performance:** Optimizes code execution and resource utilization for efficient operation.
* **Reducing Errors:** Helps avoid common pitfalls and design flaws, leading to fewer bugs and issues.

## **Creational Patterns**:

* When to use creation design pattern?
  * Creational design pattern is used when object creation is complex, involves multiple steps or requires specific initialization.
  * We must use them when the problem is related to object creation.
  * These are useful for promoting reusability, encapsulating creation logic and decoupling client code from classes it instantiates.
    * it enhances flexibility, making it easier to change or extend object creation methods at runtime.
    * Common patterns include singleton, Factory method, Abstract Factory, Builder and prototype.
    * We use them to improve maintainability, readability and scalability or the codebase,
  *

      <figure><img src="../../../.gitbook/assets/image (106).png" alt="" width="375"><figcaption></figcaption></figure>

<table><thead><tr><th width="123" align="center">Pattern</th><th>Description</th></tr></thead><tbody><tr><td align="center">Singleton</td><td><ul><li>Ensures a class has only one instance and provides a global point of access to it.</li><li>Useful when a single instance of a class is needed to coordinate actions across the system (e.g., database connections, configuration settings).</li></ul></td></tr><tr><td align="center">Factory Method</td><td><ul><li>Defines an interface for creating objects but allows subclasses to alter the type of objects created.</li><li>Commonly used when a class can't anticipate the type of object it needs to create, or when the class wants its subclasses to specify the objects it creates.</li></ul></td></tr><tr><td align="center">Abstract Factory</td><td><p></p><ul><li>Provides an interface for creating families of related or dependent objects without specifying their concrete classes.</li><li>Often used when the system needs to be independent of how its objects are created, composed, or represented, and when related objects need to work together.</li></ul></td></tr><tr><td align="center">Builder</td><td><p></p><ul><li>Separates the construction of a complex object from its representation, allowing the same construction process to create different representations.</li><li>Useful when creating objects with multiple optional or required attributes, especially when the creation process is complex (e.g., constructing a car with customizable features).</li></ul></td></tr><tr><td align="center">Prototype</td><td><ul><li>Creates new objects by copying an existing object, or "prototype."</li><li>Handy when creating objects is costly or complex, as it allows you to duplicate objects without going through the initialization process. It also enables dynamic and runtime object creation.</li></ul></td></tr><tr><td align="center">Object Pool</td><td><p></p><ul><li>Manages a pool of reusable objects to improve performance, especially in scenarios where object creation is costly.</li><li>It’s often used in resource-constrained environments or with frequently reused objects like database connections or threads</li></ul></td></tr></tbody></table>

### <mark style="color:red;">**Singleton**</mark> <mark style="color:red;"></mark><mark style="color:red;">pattern</mark>&#x20;

* ensures that a class has only one instance and provides a global access point to that instance.&#x20;
* This is useful when exactly one object is needed to coordinate actions across the system, such as in cases where a single point of control or shared resource (e.g., database connection, configuration settings) is required.

#### Key Characteristics of Singleton

1. **Single Instance**: Only one instance of the class is created and shared.
2. **Global Access**: The instance can be accessed globally via a static method.
3. **Controlled Instantiation**: The class itself controls the instantiation by restricting direct creation of objects.

#### Implementation Steps

1. **Private Constructor**: Make the constructor private to prevent other classes from creating new instances.
2. **Static Instance Method**: Provide a static method to get the single instance.
3. **Lazy Initialization (optional)**: Delay the creation of the instance until it's requested (lazy loading).
4. **Thread Safety (optional)**: Ensure thread safety if the Singleton will be used in a multi-threaded environment.

#### Singleton Code Example (Python)

Here’s a basic example of a thread-safe Singleton in Python using lazy initialization:

```python
import threading

class Singleton:
    _instance = None  # This is the singleton instance
    _lock = threading.Lock()  # Lock for thread safety

    def __new__(cls):
        # Double-checked locking to avoid race condition
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:  # Check again within the lock
                    cls._instance = super(Singleton, cls).__new__(cls)
        return cls._instance

    def __init__(self):
        # Additional initialization can go here, but will only run once.
        self.value = 0  # Example variable for demonstration

# Usage of Singleton
singleton1 = Singleton()
singleton2 = Singleton()

# Test: Both instances should point to the same object
print(singleton1 is singleton2)  # Output: True
singleton1.value = 42
print(singleton2.value)          # Output: 42 (singleton2 sees the same value set by singleton1)
```

#### Explanation

1. **Private Static Variable** (`_instance`): Stores the unique instance of the class.
2. **Lock for Thread Safety** (`_lock`): Ensures only one thread can initialize the instance at a time.
3. **Double-Checked Locking**: The `__new__` method checks if `_instance` is `None` twice (once outside the lock and once inside), reducing unnecessary locking after the instance is created.
4. **Shared State**: Both `singleton1` and `singleton2` are references to the same object, as verified by `singleton1 is singleton2`.

#### Thread Safety in Singleton

The double-checked locking with `threading.Lock` ensures that the Singleton pattern remains thread-safe, meaning multiple threads won’t create separate instances of the Singleton.

#### Pros and Cons of the Singleton Pattern

**Pros:**

* Controlled access to a single instance.
* Reduces memory footprint as only one instance is created.
* Useful for shared resources or services (e.g., logging, configuration).

**Cons:**

* Can make unit testing more difficult due to the single shared instance.
* Introduces global state, which can lead to tighter coupling and hinder modularity.
* Can introduce unexpected dependencies if overused or misused.

The Singleton pattern is widely used, especially for managing shared resources, but should be applied thoughtfully to avoid excessive coupling in your design.



### <mark style="color:red;">**Factory Method**</mark>&#x20;

* pattern defines an interface for creating objects but lets subclasses decide which class to instantiate.&#x20;
* Rather than calling a constructor directly, the client calls a factory method, which returns an instance of the appropriate subclass. This pattern is useful when:
  * A class cannot anticipate the type of objects it needs to create.
  * A class wants its subclasses to specify the objects it creates.
  * There is a need to manage or simplify object creation based on conditions.

#### Key Characteristics of Factory Method Pattern

1. **Single Interface**: The pattern defines a common interface for creating objects, but defers the instantiation to subclasses.
2. **Encapsulation of Creation Logic**: Creation of objects is encapsulated within factory methods, making it easier to modify or extend.
3. **Promotion of Loose Coupling**: The client code depends on an interface, not a concrete class, making it easy to change or add product types.

#### Implementation Structure

1. **Creator (Abstract)**: Declares the factory method that returns new products. May define a default implementation or be fully abstract.
2. **Concrete Creator**: Implements the factory method to return instances of specific products.
3. **Product (Abstract)**: Declares the interface for objects the factory method creates.
4. **Concrete Product**: Implements the product interface.

#### Factory Method Code Example (Python)

Let's create an example of a **Transport Factory** that can produce various types of transport (like cars and bikes) depending on the situation.

**Step 1: Define the Product Interface**

```python
from abc import ABC, abstractmethod

class Transport(ABC):
    @abstractmethod
    def deliver(self):
        pass
```

**Step 2: Create Concrete Products**

```python
class Truck(Transport):
    def deliver(self):
        return "Delivering by land in a truck."

class Ship(Transport):
    def deliver(self):
        return "Delivering by sea in a ship."
```

**Step 3: Define the Creator Interface (Abstract Factory)**

```python
class Logistics(ABC):
    @abstractmethod
    def create_transport(self) -> Transport:
        pass

    def plan_delivery(self):
        # Call the factory method to create a Transport instance
        transport = self.create_transport()
        # Use the transport's deliver method
        return transport.deliver()
```

**Step 4: Create Concrete Creators**

```python
class RoadLogistics(Logistics):
    def create_transport(self) -> Transport:
        return Truck()

class SeaLogistics(Logistics):
    def create_transport(self) -> Transport:
        return Ship()
```

**Step 5: Using the Factory Method**

```python
def client_code(logistics: Logistics):
    print(logistics.plan_delivery())

# Instantiate a concrete creator (factory)
road_logistics = RoadLogistics()
client_code(road_logistics)  # Output: "Delivering by land in a truck."

sea_logistics = SeaLogistics()
client_code(sea_logistics)  # Output: "Delivering by sea in a ship."
```

#### Explanation

1. **`Transport` Interface**: Declares a `deliver` method that all product types (Truck, Ship) must implement.
2. **Concrete Products (`Truck` and `Ship`)**: Implement the `Transport` interface and provide specific delivery methods.
3. **`Logistics` Abstract Class**: The abstract creator class declares the factory method `create_transport`, which subclasses must implement.
4. **Concrete Creators (`RoadLogistics` and `SeaLogistics`)**: Implement the `create_transport` method to return an instance of `Truck` or `Ship`, respectively.

#### Advantages of Factory Method

1. **Loose Coupling**: Clients use an interface to interact with products rather than concrete classes, making it easy to change or extend product types.
2. **Single Responsibility**: Each creator class is responsible only for creating products, simplifying object creation and testing.
3. **Scalability**: New product types can be added without altering existing code, adhering to the Open/Closed Principle.

#### Disadvantages of Factory Method

1. **Increased Complexity**: Introduces additional subclasses, which can complicate the design if overused.
2. **May Overhead**: For simple object creation, this pattern can add unnecessary complexity.

The Factory Method is ideal when the system needs to choose between multiple types of related objects without needing to modify the client code, thus enhancing maintainability and scalability.



### &#x20;<mark style="color:red;">**Abstract Factory**</mark>&#x20;

* provides an interface for creating families of related or dependent objects without specifying their concrete classes.&#x20;
* This pattern is particularly useful when a system must be independent of how its objects are created, composed, or represented, and when products need to work together in specific combinations.

#### Key Characteristics of Abstract Factory Pattern

1. **Family of Products**: It produces families of related products rather than a single product.
2. **Consistency of Products**: Ensures that products are compatible with each other within a family.
3. **Encapsulation of Object Creation**: Creation of products is encapsulated in "factories," making it easy to switch between different families by changing the factory.

#### Implementation Structure

1. **Abstract Factory Interface**: Declares the factory methods to create abstract products.
2. **Concrete Factories**: Implement the abstract factory interface, providing methods to create specific products.
3. **Abstract Product Interfaces**: Define interfaces for each product type.
4. **Concrete Products**: Implement the product interfaces and represent individual product types belonging to families.
5. **Client**: Uses the abstract factory and product interfaces, allowing for flexible product family changes.

#### Example Scenario

Let’s implement an example of an **Abstract Factory** to create two families of products: **Victorian Furniture** and **Modern Furniture**. Each family will contain two types of products: **Chair** and **Sofa**.

#### Code Example (Python)

**Step 1: Define Abstract Product Interfaces**

```python
from abc import ABC, abstractmethod

class Chair(ABC):
    @abstractmethod
    def sit_on(self) -> str:
        pass

class Sofa(ABC):
    @abstractmethod
    def lie_on(self) -> str:
        pass
```

**Step 2: Create Concrete Products for Each Family**

```python
# Victorian Chair and Sofa
class VictorianChair(Chair):
    def sit_on(self) -> str:
        return "Sitting on a Victorian chair."

class VictorianSofa(Sofa):
    def lie_on(self) -> str:
        return "Lying on a Victorian sofa."

# Modern Chair and Sofa
class ModernChair(Chair):
    def sit_on(self) -> str:
        return "Sitting on a Modern chair."

class ModernSofa(Sofa):
    def lie_on(self) -> str:
        return "Lying on a Modern sofa."
```

**Step 3: Define the Abstract Factory Interface**

```python
class FurnitureFactory(ABC):
    @abstractmethod
    def create_chair(self) -> Chair:
        pass

    @abstractmethod
    def create_sofa(self) -> Sofa:
        pass
```

**Step 4: Implement Concrete Factories for Each Family**

```python
class VictorianFurnitureFactory(FurnitureFactory):
    def create_chair(self) -> Chair:
        return VictorianChair()

    def create_sofa(self) -> Sofa:
        return VictorianSofa()

class ModernFurnitureFactory(FurnitureFactory):
    def create_chair(self) -> Chair:
        return ModernChair()

    def create_sofa(self) -> Sofa:
        return ModernSofa()
```

**Step 5: Client Code to Use Abstract Factory**

```python
def client_code(factory: FurnitureFactory):
    chair = factory.create_chair()
    sofa = factory.create_sofa()

    print(chair.sit_on())
    print(sofa.lie_on())

# Instantiate concrete factories
victorian_factory = VictorianFurnitureFactory()
modern_factory = ModernFurnitureFactory()

print("Victorian Furniture:")
client_code(victorian_factory)

print("\nModern Furniture:")
client_code(modern_factory)
```

#### Explanation

1. **Product Interfaces (`Chair` and `Sofa`)**: Define abstract methods for chair and sofa products.
2. **Concrete Products**: Each product family (Victorian and Modern) has its own specific implementations of `Chair` and `Sofa`.
3. **Abstract Factory Interface (`FurnitureFactory`)**: Declares methods for creating each type of product (in this case, `create_chair` and `create_sofa`).
4. **Concrete Factories**: Implement the `FurnitureFactory` to create specific products within the family, ensuring product compatibility.
5. **Client Code**: Uses the factory interface to obtain and interact with products, without knowing the concrete factory type.

#### Output

```
Victorian Furniture:
Sitting on a Victorian chair.
Lying on a Victorian sofa.

Modern Furniture:
Sitting on a Modern chair.
Lying on a Modern sofa.
```

#### Advantages of Abstract Factory

1. **Consistency Among Products**: Ensures that products created within a family are compatible, which is useful when objects need to work together seamlessly.
2. **Encapsulation of Product Creation**: Centralizes object creation, simplifying maintenance and making it easy to swap out or extend product families.
3. **Scalability**: New product families can be added without modifying existing client code, adhering to the Open/Closed Principle.

#### Disadvantages of Abstract Factory

1. **Increased Complexity**: Introduces additional interfaces and classes, which may lead to higher complexity for simpler problems.
2. **Rigid Structure**: Adding new product types may require changes to all existing factories, which could be restrictive if the types vary often.

The Abstract Factory pattern is ideal for systems requiring multiple, interchangeable families of related products, providing flexibility and a modular design for creating compatible objects.



### <mark style="color:red;">**Builder**</mark>&#x20;

* separates the construction of a complex object from its representation. The Builder pattern allows you to create complex objects step-by-step, enabling different configurations of an object without changing its structure.&#x20;
* This pattern is particularly useful for creating objects with multiple optional parameters or configurations.

#### Key Characteristics of the Builder Pattern

1. **Separation of Construction and Representation**: The pattern isolates complex construction logic from the representation, allowing you to focus on constructing the object rather than understanding its details.
2. **Incremental Construction**: Step-by-step methods are used to configure the object, which is useful for creating various configurations.
3. **Flexibility in Construction**: Allows the same construction process to create different representations or configurations of an object.

#### Implementation Structure

1. **Product**: The complex object that is being built.
2. **Builder Interface**: Defines methods for creating parts of the Product.
3. **Concrete Builder**: Implements the Builder interface to construct and assemble the parts of the Product.
4. **Director** (optional): Directs the construction sequence using a Builder. This class is optional and is used when you want to standardize the construction process.

#### Example Scenario

Consider creating a **House** object, which can have different configurations like the number of rooms, the presence of a garage, a garden, and a swimming pool. Each configuration step will be handled by the builder, while the director will manage the order of construction.

#### Code Example (Python)

**Step 1: Define the Product**

```python
class House:
    def __init__(self):
        self.rooms = 0
        self.has_garage = False
        self.has_garden = False
        self.has_swimming_pool = False

    def __str__(self):
        return (f"House with {self.rooms} rooms, "
                f"{'a garage' if self.has_garage else 'no garage'}, "
                f"{'a garden' if self.has_garden else 'no garden'}, "
                f"{'a swimming pool' if self.has_swimming_pool else 'no swimming pool'}.")
```

**Step 2: Create the Builder Interface**

```python
from abc import ABC, abstractmethod

class HouseBuilder(ABC):
    @abstractmethod
    def build_rooms(self, number: int):
        pass

    @abstractmethod
    def build_garage(self):
        pass

    @abstractmethod
    def build_garden(self):
        pass

    @abstractmethod
    def build_swimming_pool(self):
        pass

    @abstractmethod
    def get_house(self) -> House:
        pass
```

**Step 3: Implement the Concrete Builder**

```python
class ConcreteHouseBuilder(HouseBuilder):
    def __init__(self):
        self.house = House()

    def build_rooms(self, number: int):
        self.house.rooms = number

    def build_garage(self):
        self.house.has_garage = True

    def build_garden(self):
        self.house.has_garden = True

    def build_swimming_pool(self):
        self.house.has_swimming_pool = True

    def get_house(self) -> House:
        return self.house
```

**Step 4: Create the Director (Optional)**

```python
class Director:
    def __init__(self, builder: HouseBuilder):
        self.builder = builder

    def construct_minimal_house(self):
        self.builder.build_rooms(1)

    def construct_luxury_house(self):
        self.builder.build_rooms(5)
        self.builder.build_garage()
        self.builder.build_garden()
        self.builder.build_swimming_pool()
```

**Step 5: Using the Builder**

```python
# Create a builder instance
builder = ConcreteHouseBuilder()
director = Director(builder)

# Construct a minimal house
director.construct_minimal_house()
minimal_house = builder.get_house()
print("Minimal House:", minimal_house)  # Output: House with 1 room, no garage, no garden, no swimming pool.

# Construct a luxury house
builder = ConcreteHouseBuilder()  # Reset the builder for a new house
director = Director(builder)
director.construct_luxury_house()
luxury_house = builder.get_house()
print("Luxury House:", luxury_house)  # Output: House with 5 rooms, a garage, a garden, a swimming pool.
```

#### Explanation

1. **House**: The product class that represents the object being constructed.
2. **Builder Interface (`HouseBuilder`)**: Declares the building steps that are required to construct a house.
3. **Concrete Builder (`ConcreteHouseBuilder`)**: Implements the builder interface methods to configure various parts of the house.
4. **Director**: Orchestrates the construction steps to create different configurations of the product. In this example, the `Director` creates both a minimal and a luxury house by calling specific methods on the builder.
5. **Client Code**: The client interacts with the `Director` and `Builder` to get different configurations of the `House` object.

#### Advantages of Builder Pattern

1. **Greater Control**: Allows finer control over the construction process, useful for complex objects.
2. **Flexible Configurations**: Supports creating different configurations of an object without changing the client code.
3. **Improved Readability and Maintenance**: Separates the construction process from the representation, which simplifies code readability and maintenance.

#### Disadvantages of Builder Pattern

1. **Increased Complexity**: Introduces more classes and interfaces, which may not be necessary for simpler objects.
2. **Potential Overhead**: May add unnecessary overhead for objects with simple construction.

The Builder pattern is particularly useful when constructing objects that have several optional parts or configurations. It makes it easy to create complex products step-by-step while keeping the construction process separated from the product’s representation.

### <mark style="color:red;">**Prototype**</mark>

* allows cloning of objects, even complex ones, without depending on their concrete classes. It relies on a prototype (or template) object that can be copied to create new instances, making it useful when object creation is costly or complex. The Prototype pattern is particularly useful when:
  * You want to avoid creating an object from scratch.
  * Object creation is resource-intensive.
  * Different configurations of the object are needed frequently.

#### Key Characteristics of Prototype Pattern

1. **Cloning Instead of Instantiation**: Objects are created by copying (cloning) an existing object, not by using `new` or constructors.
2. **Flexibility**: Allows for creating complex objects dynamically at runtime with a flexible prototype interface.
3. **Avoiding Subclassing**: Supports object creation without subclassing, which can help reduce the overhead of additional classes.

#### Implementation Structure

1. **Prototype Interface**: Declares a `clone` method for cloning itself.
2. **Concrete Prototype**: Implements the `clone` method to perform a deep or shallow copy.
3. **Client**: Uses the `clone` method to create new objects by copying the prototype.

#### Types of Cloning

* **Shallow Copy**: Creates a new object, but nested objects are shared between the original and the clone.
* **Deep Copy**: Creates a new object along with all nested objects, resulting in a fully independent clone.

#### Prototype Code Example (Python)

Let’s create an example where we have a `Shape` prototype with two concrete prototypes: `Circle` and `Rectangle`. The prototype objects can be cloned to create identical shapes.

**Step 1: Define the Prototype Interface**

```python
from abc import ABC, abstractmethod
import copy

class Shape(ABC):
    def __init__(self, color):
        self.color = color

    @abstractmethod
    def clone(self):
        pass

    def __str__(self):
        return f"{self.__class__.__name__} with color {self.color}"
```

**Step 2: Create Concrete Prototypes**

```python
class Circle(Shape):
    def __init__(self, radius, color):
        super().__init__(color)
        self.radius = radius

    def clone(self):
        # Deep copy of the Circle instance
        return copy.deepcopy(self)

    def __str__(self):
        return f"Circle with radius {self.radius} and color {self.color}"

class Rectangle(Shape):
    def __init__(self, width, height, color):
        super().__init__(color)
        self.width = width
        self.height = height

    def clone(self):
        # Deep copy of the Rectangle instance
        return copy.deepcopy(self)

    def __str__(self):
        return f"Rectangle with width {self.width}, height {self.height}, and color {self.color}"
```

**Step 3: Using the Prototype Pattern**

```python
# Create original instances (prototypes)
circle_prototype = Circle(5, "red")
rectangle_prototype = Rectangle(10, 20, "blue")

# Clone the prototypes
circle_clone = circle_prototype.clone()
rectangle_clone = rectangle_prototype.clone()

# Change properties of the cloned instances
circle_clone.color = "green"  # Change color of the clone
rectangle_clone.width = 15     # Change width of the clone

# Print original and cloned objects
print("Original Circle:", circle_prototype)    # Output: Circle with radius 5 and color red
print("Cloned Circle:", circle_clone)          # Output: Circle with radius 5 and color green

print("Original Rectangle:", rectangle_prototype)  # Output: Rectangle with width 10, height 20, and color blue
print("Cloned Rectangle:", rectangle_clone)        # Output: Rectangle with width 15, height 20, and color blue
```

#### Explanation

1. **Prototype Interface (`Shape`)**: Declares the `clone` method for creating copies of objects. This interface has a `color` attribute shared by all shapes.
2. **Concrete Prototypes (`Circle` and `Rectangle`)**: Each shape class implements the `clone` method to return a deep copy of itself.
3. **Cloning Process**: In `clone`, we use `copy.deepcopy` to create a completely independent copy of the object, including any nested data structures.
4. **Client Code**: Clones the prototypes and modifies the properties of the clones without affecting the original prototypes.

#### Output

```
Original Circle: Circle with radius 5 and color red
Cloned Circle: Circle with radius 5 and color green

Original Rectangle: Rectangle with width 10, height 20, and color blue
Cloned Rectangle: Rectangle with width 15, height 20, and color blue
```

#### Advantages of Prototype Pattern

1. **Reduces Resource Cost**: Cloning an existing object is often cheaper than creating a new one, especially if the creation process is expensive.
2. **Dynamic and Flexible**: Makes it easy to add or change object configurations at runtime.
3. **Decoupling from Concrete Classes**: Avoids tight coupling to specific classes, promoting interface-based design.

#### Disadvantages of Prototype Pattern

1. **Complexity with Deep Copying**: Ensuring deep copies for complex structures can introduce additional complexity.
2. **Hidden Dependencies**: Can lead to unexpected dependencies if clones share references unintentionally in shallow copies.
3. **Limited Control over Cloning Process**: If the objects contain unique identifiers or references that shouldn’t be copied, the cloning process may require additional customization.

The Prototype pattern is ideal when object creation is expensive or involves complex initialization, allowing for efficient and flexible object creation through cloning. It provides an efficient way to create new instances by copying a prototype, with the flexibility to adjust the copies without impacting the original.

### <mark style="color:red;">**Object Pool**</mark>&#x20;

* used to manage a pool of reusable objects. It’s particularly useful when object creation is expensive in terms of resources or time.&#x20;
* Instead of creating and destroying objects repeatedly, the Object Pool pattern reuses existing objects, improving performance in scenarios where a large number of objects with a similar life cycle are needed.

#### Key Characteristics of Object Pool Pattern

1. **Object Reusability**: Enables the reuse of objects that are expensive to create, such as database connections, network connections, or large data buffers.
2. **Efficient Resource Management**: Reduces overhead from frequent creation and deletion of resources.
3. **Pooling**: Manages a set of initialized and reusable objects, allowing clients to acquire and release objects as needed.

#### Typical Use Cases for Object Pool Pattern

* **Database Connection Pooling**: Reuses established database connections rather than creating new ones for each request.
* **Thread Pooling**: Reuses a limited number of threads to handle a large number of tasks.
* **Network Resource Pooling**: Manages a limited number of network resources, such as HTTP connections, in web servers.

#### Implementation Structure

1. **Pool Manager (Object Pool)**: Manages the available and in-use objects. Provides methods to acquire and release objects.
2. **Reusable Object**: Represents the object that will be reused, such as a database connection or buffer.

#### Code Example: Object Pool for Database Connections (Python)

In this example, we’ll create a simplified **DatabaseConnection** class that represents a connection to a database. The **DatabaseConnectionPool** will manage a pool of these connections, allowing clients to acquire and release connections as needed.

**Step 1: Define the Reusable Object**

```python
import time

class DatabaseConnection:
    def __init__(self, connection_id):
        self.connection_id = connection_id
        self.in_use = False

    def connect(self):
        print(f"Connecting to database with connection {self.connection_id}")
        self.in_use = True

    def disconnect(self):
        print(f"Disconnecting from database with connection {self.connection_id}")
        self.in_use = False
```

**Step 2: Create the Object Pool**

```python
class DatabaseConnectionPool:
    def __init__(self, max_connections):
        self.max_connections = max_connections
        self.available_connections = []
        self.in_use_connections = set()
        self._initialize_connections()

    def _initialize_connections(self):
        for i in range(self.max_connections):
            connection = DatabaseConnection(i)
            self.available_connections.append(connection)

    def acquire_connection(self):
        if self.available_connections:
            connection = self.available_connections.pop()
            connection.connect()
            self.in_use_connections.add(connection)
            return connection
        else:
            print("No available connections. Please wait or release a connection.")
            return None

    def release_connection(self, connection):
        if connection in self.in_use_connections:
            connection.disconnect()
            self.in_use_connections.remove(connection)
            self.available_connections.append(connection)
        else:
            print("Connection is not part of the pool or is already released.")
```

**Step 3: Using the Object Pool**

```python
# Create a pool with a maximum of 3 database connections
connection_pool = DatabaseConnectionPool(max_connections=3)

# Acquire connections
conn1 = connection_pool.acquire_connection()
conn2 = connection_pool.acquire_connection()
conn3 = connection_pool.acquire_connection()

# Attempt to acquire another connection (exceeds max pool size)
conn4 = connection_pool.acquire_connection()  # No available connections

# Release one connection and try acquiring again
connection_pool.release_connection(conn1)
conn4 = connection_pool.acquire_connection()  # Now it should succeed

# Release all connections
connection_pool.release_connection(conn2)
connection_pool.release_connection(conn3)
connection_pool.release_connection(conn4)
```

#### Explanation

1. **DatabaseConnection**: Represents a simple reusable connection object. It has a `connect` method to indicate it’s in use and a `disconnect` method to release it.
2. **DatabaseConnectionPool**: Manages the pool of connections. It keeps a list of available connections and a set of in-use connections. When a client requests a connection, it removes one from the available pool, marks it as in use, and returns it. When the client releases a connection, it’s returned to the available pool.
3. **Client Code**: Demonstrates acquiring connections from the pool and releasing them. Attempting to acquire a connection when the pool is exhausted results in a message prompting the client to release a connection first.

#### Output

```
Connecting to database with connection 0
Connecting to database with connection 1
Connecting to database with connection 2
No available connections. Please wait or release a connection.
Disconnecting from database with connection 0
Connecting to database with connection 0
Disconnecting from database with connection 1
Disconnecting from database with connection 2
Disconnecting from database with connection 0
```

#### Advantages of Object Pool Pattern

1. **Performance Improvement**: Reduces the overhead of creating and destroying objects repeatedly, improving performance in resource-intensive scenarios.
2. **Efficient Resource Utilization**: Manages limited resources (e.g., database connections) efficiently by reusing them.
3. **Improved Scalability**: Enables scaling with a limited resource pool, essential in high-load systems.

#### Disadvantages of Object Pool Pattern

1. **Complexity in Management**: Requires careful management of resources, especially in systems with dynamic load or unpredictable demands.
2. **Potential for Resource Leaks**: Improperly released objects may lead to resource leaks if not managed correctly.
3. **Limited Flexibility**: May not be suitable for objects that require frequent reinitialization or have unique lifecycle requirements.

The Object Pool pattern is valuable in situations where creating and destroying resources repeatedly is costly. By reusing resources, the pattern reduces resource strain and improves system efficiency. It is particularly effective for managing a pool of limited resources in multi-threaded environments.



### <mark style="color:red;">Builder Pattern</mark>&#x20;

* is a design pattern in object-oriented programming, part of the "Gang of Four" patterns, that helps construct complex objects step by step.&#x20;
* The main idea behind the Builder Pattern is to separate the construction of an object from its representation, allowing the same construction process to create different representations.
* This pattern is especially useful when:
  * You need to create an object with many fields or optional parameters.
  * You want to make the object immutable.
  * You want to avoid having a long list of constructor parameters, often referred to as the "telescoping constructor anti-pattern."

#### Structure of the Builder Pattern

1. **Builder**: Defines methods for setting different parts of an object.
2. **ConcreteBuilder**: Implements the builder interface and constructs the object.
3. **Director** (Optional): Oversees the construction process but is optional.
4. **Product**: The final object that’s being constructed.

#### Example Code in Java

Let’s create an example of the Builder Pattern to build a `Computer` object.

```java
// Product class
public class Computer {
    // Required parameters
    private String HDD;
    private String RAM;

    // Optional parameters
    private boolean isGraphicsCardEnabled;
    private boolean isBluetoothEnabled;

    // Private constructor
    private Computer(ComputerBuilder builder) {
        this.HDD = builder.HDD;
        this.RAM = builder.RAM;
        this.isGraphicsCardEnabled = builder.isGraphicsCardEnabled;
        this.isBluetoothEnabled = builder.isBluetoothEnabled;
    }

    // Getters
    public String getHDD() {
        return HDD;
    }

    public String getRAM() {
        return RAM;
    }

    public boolean isGraphicsCardEnabled() {
        return isGraphicsCardEnabled;
    }

    public boolean isBluetoothEnabled() {
        return isBluetoothEnabled;
    }

    // Static nested Builder class
    public static class ComputerBuilder {
        // Required parameters
        private String HDD;
        private String RAM;

        // Optional parameters
        private boolean isGraphicsCardEnabled;
        private boolean isBluetoothEnabled;

        // Constructor for required fields
        public ComputerBuilder(String hdd, String ram) {
            this.HDD = hdd;
            this.RAM = ram;
        }

        // Setter for optional parameter
        public ComputerBuilder setGraphicsCardEnabled(boolean isGraphicsCardEnabled) {
            this.isGraphicsCardEnabled = isGraphicsCardEnabled;
            return this;
        }

        // Setter for optional parameter
        public ComputerBuilder setBluetoothEnabled(boolean isBluetoothEnabled) {
            this.isBluetoothEnabled = isBluetoothEnabled;
            return this;
        }

        // Method to build the object
        public Computer build() {
            return new Computer(this);
        }
    }
}
```

#### Using the Builder Pattern

Here’s how to create a `Computer` object using the `ComputerBuilder`:

```java
public class Main {
    public static void main(String[] args) {
        // Create Computer object using Builder pattern
        Computer computer = new Computer.ComputerBuilder("1TB", "16GB")
                .setGraphicsCardEnabled(true)
                .setBluetoothEnabled(true)
                .build();

        System.out.println("Computer Config: HDD = " + computer.getHDD()
                + ", RAM = " + computer.getRAM()
                + ", Graphics Card Enabled = " + computer.isGraphicsCardEnabled()
                + ", Bluetooth Enabled = " + computer.isBluetoothEnabled());
    }
}
```

#### Explanation

1. **Constructor Privacy**: The constructor for `Computer` is private and only accessible to `ComputerBuilder`.
2. **Chaining**: The builder provides methods that return the builder itself (`this`), allowing for method chaining.
3. **Immutability**: Once created, `Computer` objects are immutable.
4. **Code Readability**: Creating an object with the builder pattern improves readability, as we don’t need to remember the order of parameters and only specify the ones we need.



## <mark style="color:yellow;">**Structural Patterns**</mark><mark style="color:yellow;">:</mark>

* When to use structural design patterns
  * When we need to compose objects and classes into larger structures while keeping them flexible and efficient.
  * We must choose structural design patterns when the problem is related to object assembly.
  *   These patterns are useful for clarifying relationships between classes, managing object hierarchies and altering interfaces without affecting clients.

      * it promotes code reuse, simplify system design and enhance scalability.
      * They are beneficial when dealing with complex systems, integration of new components or refactoring existing codebases.



      <figure><img src="../../../.gitbook/assets/image (107).png" alt=""><figcaption></figcaption></figure>



Of course. Here is the revised table for structural Low-Level Design (LLD) patterns, with the purpose, use case, and example combined into a single "Basic Description" column for each pattern.

<table><thead><tr><th width="107.3333740234375">Pattern Name</th><th>Basic Description</th></tr></thead><tbody><tr><td><strong>Adapter</strong></td><td><strong>Purpose:</strong> To allow objects with incompatible interfaces to collaborate. It acts as a bridge between two interfaces, making them compatible so they can work together seamlessly.<br><br><strong>Use Case:</strong> When you need to integrate a new class into an existing system that has an incompatible interface. This is common when working with legacy code or third-party libraries.<br><br><strong>Example:</strong> A power adapter that allows you to plug a device with one type of plug into an outlet with a different configuration. In software, an adapter could convert data from a third-party API's XML format to the JSON format used by your application.</td></tr><tr><td><strong>Bridge</strong></td><td><strong>Purpose:</strong> To decouple an abstraction from its implementation so that the two can vary independently. This pattern is particularly useful when you need to avoid a permanent binding between an abstraction and its implementation.<br><br><strong>Use Case:</strong> When you need to extend a class in several independent dimensions. For instance, managing a graphical user interface (GUI) across multiple platforms where the UI controls (abstraction) need to be separated from their platform-specific renderings (implementation).<br><br><strong>Example:</strong> A household light switch is a good analogy; the switch (abstraction) can be a simple toggle or a dimmer, while the light fixture it controls (implementation) can be a ceiling light or a lamp. In software, this could be a media player with different user interfaces (abstractions) controlling various audio/video decoders (implementations).</td></tr><tr><td><strong>Composite</strong></td><td><strong>Purpose:</strong> To compose objects into tree structures to represent part-whole hierarchies. This allows clients to treat individual objects and compositions of objects uniformly.<br><br><strong>Use Case:</strong> When your application's core model can be represented as a tree structure. This is useful in graphics applications for grouping shapes or in file systems for representing directories and files.<br><br><strong>Example:</strong> A file system where a directory can contain files and other directories. You can perform operations like 'get size' on both a single file and an entire directory, and the directory will recursively calculate the size of its contents.</td></tr><tr><td><strong>Decorator</strong></td><td><strong>Purpose:</strong> To attach additional responsibilities or behaviors to an object dynamically. This provides a flexible alternative to subclassing for extending functionality.<br><br><strong>Use Case:</strong> When you need to add features to an object at runtime without altering its class. This is useful for avoiding a "class explosion" where you would otherwise need numerous subclasses to handle all possible combinations of features.<br><br><strong>Example:</strong> Ordering a coffee where you start with a base coffee and then "decorate" it with additions like milk, sugar, or whipped cream. Each addition modifies the final product's cost and description.</td></tr><tr><td><strong>Facade</strong></td><td><strong>Purpose:</strong> To provide a simplified, high-level interface to a complex subsystem of classes. It hides the complexities of the system and provides a unified interface to the client.<br><br><strong>Use Case:</strong> When you need to make a complex system easier to use by providing a simple interface to a set of interfaces in that subsystem. This is useful for interacting with sophisticated libraries or frameworks where you only need a subset of the functionality.<br><br><strong>Example:</strong> A customer placing a food order at a restaurant interacts with a waiter (the facade), who then handles all the complex interactions with the kitchen, billing, etc., shielding the customer from those details.</td></tr><tr><td><strong>Flyweight</strong></td><td><strong>Purpose:</strong> To minimize memory usage or computational expenses by sharing as much as possible with other similar objects. It's used when you have a large number of objects that share common state.<br><br><strong>Use Case:</strong> When an application creates a large number of similar objects and memory consumption is a concern. The pattern is effective when much of the object's state can be made extrinsic (context-dependent) and shared.<br><br><strong>Example:</strong> In a text editor, instead of creating a separate object for every character, the flyweight pattern allows for sharing character objects. For example, all instances of the letter 'A' with the same font and size can share a single object.</td></tr><tr><td><strong>Proxy</strong></td><td><strong>Purpose:</strong> To provide a surrogate or placeholder for another object to control access to it. This allows you to perform actions either before or after the request gets to the original object.<br><br><strong>Use Case:</strong> When you need to control and manage access to objects, such as for lazy loading, security control, logging, or caching.<br><br><strong>Example:</strong> A credit card acts as a proxy for your bank account. It provides a way to access your funds without carrying around cash and adds a layer of security. In software, a proxy can be used to load large images only when they are about to be displayed.</td></tr></tbody></table>

### <mark style="color:red;">**Adapter Pattern**</mark>&#x20;

* allows objects with incompatible interfaces to work together by providing a "middle layer" or adapter.&#x20;
* This pattern involves a class or object that wraps one interface to make it compatible with another, allowing systems to interact seamlessly without modifying the existing code.

The Adapter Pattern can be implemented in two main ways:

1. **Class Adapter**: Uses inheritance to adapt one interface to another.
2. **Object Adapter**: Uses composition to adapt an interface by containing an instance of the incompatible class.

#### When to Use the Adapter Pattern

* **When you need to use a legacy class** with a different or incompatible interface in a new system.
* **When you want to make a class reusable** in multiple situations by adapting its interface.
* **When classes have similar functionality** but different interfaces, and you want them to work together.

#### Example Scenario

Imagine you’re building an audio player that supports only MP3 files, but now you want to add support for other formats like MP4 or VLC. Instead of modifying the existing player class, you can create adapters for each new format, allowing them to work with the existing player without changing its code.

#### Example Code for Adapter Pattern (Python)

We’ll create an example with:

1. **MediaPlayer**: An existing interface that supports only MP3 files.
2. **AdvancedMediaPlayer**: A class that can play MP4 and VLC files.
3. **MediaAdapter**: An adapter that enables `MediaPlayer` to play both MP4 and VLC files.

**Step 1: Define the MediaPlayer Interface and Its Implementation**

```python
class MediaPlayer:
    def play(self, filename):
        print(f"Playing {filename} in MP3 format.")
```

**Step 2: Define the AdvancedMediaPlayer Interface and Implementations**

```python
class AdvancedMediaPlayer:
    def play_mp4(self, filename):
        print(f"Playing {filename} in MP4 format.")

    def play_vlc(self, filename):
        print(f"Playing {filename} in VLC format.")
```

**Step 3: Create the MediaAdapter to Bridge MediaPlayer and AdvancedMediaPlayer**

The `MediaAdapter` will implement `MediaPlayer` while also containing an instance of `AdvancedMediaPlayer`, allowing it to translate calls from `MediaPlayer` to `AdvancedMediaPlayer`.

```python
class MediaAdapter(MediaPlayer):
    def __init__(self, media_type):
        self.advanced_player = AdvancedMediaPlayer()
        self.media_type = media_type

    def play(self, filename):
        if self.media_type == "mp4":
            self.advanced_player.play_mp4(filename)
        elif self.media_type == "vlc":
            self.advanced_player.play_vlc(filename)
```

**Step 4: Use the Adapter in a Media Player**

```python
class AudioPlayer(MediaPlayer):
    def play(self, filename, media_type="mp3"):
        if media_type == "mp3":
            super().play(filename)
        elif media_type in ["mp4", "vlc"]:
            adapter = MediaAdapter(media_type)
            adapter.play(filename)
        else:
            print(f"Media type {media_type} is not supported.")

# Client code
audio_player = AudioPlayer()
audio_player.play("song.mp3")               # Playing song.mp3 in MP3 format.
audio_player.play("movie.mp4", "mp4")       # Playing movie.mp4 in MP4 format.
audio_player.play("series.vlc", "vlc")      # Playing series.vlc in VLC format.
audio_player.play("unknown.wma", "wma")     # Media type wma is not supported.
```

#### Explanation of the Adapter Pattern Implementation

1. **MediaPlayer (Target Interface)**: This is the target interface that the client code expects to use, designed initially for playing MP3 files.
2. **AdvancedMediaPlayer (Adaptee)**: This class has the existing incompatible methods for playing MP4 and VLC files.
3. **MediaAdapter (Adapter)**: The adapter class implements `MediaPlayer` but uses `AdvancedMediaPlayer` internally. When asked to play an MP4 or VLC file, it uses the appropriate method from `AdvancedMediaPlayer`.
4. **AudioPlayer (Client)**: This client uses `MediaAdapter` to support additional media types without altering its structure.

#### Output

```
Playing song.mp3 in MP3 format.
Playing movie.mp4 in MP4 format.
Playing series.vlc in VLC format.
Media type wma is not supported.
```

#### Advantages of the Adapter Pattern

1. **Single Responsibility Principle**: You can separate the interface or logic of two incompatible classes.
2. **Increased Reusability**: Allows the reuse of existing classes even if they have incompatible interfaces.
3. **Flexibility**: You can add adapters for additional incompatible classes without changing the original code.

#### Disadvantages of the Adapter Pattern

1. **Increased Complexity**: Adding extra classes to handle adaptation can increase code complexity, especially with multiple adapters.
2. **Overuse**: Using the adapter pattern for classes that could be refactored or merged can lead to redundant code.

#### Class Adapter vs. Object Adapter

* **Class Adapter**: Uses inheritance. This approach may be simpler but is limited to languages that support multiple inheritance.
* **Object Adapter**: Uses composition (as shown in the example above). This approach is more flexible and works with languages that do not support multiple inheritance.

#### Summary

The Adapter Pattern is ideal when:

* You need to make an existing class compatible with another interface.
* You want to reuse a class that doesn’t match the current system’s interface.

By creating an adapter, you enable smooth integration without changing the code of either the client or the incompatible class, keeping your code more modular and flexible.



### <mark style="color:red;">Bridge Pattern</mark>&#x20;

* decouples an abstraction from its implementation, allowing them to vary independently.&#x20;
* This pattern is useful when a class has multiple dimensions of variation, and it's important to keep these variations separate.

#### Key Concepts of the Bridge Pattern

1. **Abstraction**: The interface for the higher-level control logic. It typically maintains a reference to the implementation part.
2. **Refined Abstraction**: A subclass of the Abstraction that extends its behavior.
3. **Implementor**: An interface for the lower-level implementation.
4. **Concrete Implementor**: Implements the Implementor interface.

The goal of the Bridge Pattern is to separate the abstraction (high-level control) from the implementation (low-level control) so they can be developed and maintained independently.

#### Use Case Example

Consider a scenario where we want to create different devices (like TVs and Radios) with two different types of remotes (BasicRemote and AdvancedRemote). Using the Bridge Pattern allows us to separate the device (abstraction) from the remote controls (implementation).

#### Example Code in Java

```java
// Implementor interface
interface Device {
    void turnOn();
    void turnOff();
    void setVolume(int volume);
    boolean isEnabled();
    int getVolume();
}

// Concrete Implementor - TV
class TV implements Device {
    private boolean on = false;
    private int volume = 30;

    @Override
    public void turnOn() {
        on = true;
        System.out.println("TV is turned on.");
    }

    @Override
    public void turnOff() {
        on = false;
        System.out.println("TV is turned off.");
    }

    @Override
    public void setVolume(int volume) {
        if (volume >= 0 && volume <= 100) {
            this.volume = volume;
            System.out.println("TV volume set to " + this.volume);
        }
    }

    @Override
    public boolean isEnabled() {
        return on;
    }

    @Override
    public int getVolume() {
        return volume;
    }
}

// Concrete Implementor - Radio
class Radio implements Device {
    private boolean on = false;
    private int volume = 50;

    @Override
    public void turnOn() {
        on = true;
        System.out.println("Radio is turned on.");
    }

    @Override
    public void turnOff() {
        on = false;
        System.out.println("Radio is turned off.");
    }

    @Override
    public void setVolume(int volume) {
        if (volume >= 0 && volume <= 100) {
            this.volume = volume;
            System.out.println("Radio volume set to " + this.volume);
        }
    }

    @Override
    public boolean isEnabled() {
        return on;
    }

    @Override
    public int getVolume() {
        return volume;
    }
}

// Abstraction - Remote Control
abstract class RemoteControl {
    protected Device device;

    protected RemoteControl(Device device) {
        this.device = device;
    }

    public void togglePower() {
        if (device.isEnabled()) {
            device.turnOff();
        } else {
            device.turnOn();
        }
    }

    public void volumeUp() {
        device.setVolume(device.getVolume() + 10);
    }

    public void volumeDown() {
        device.setVolume(device.getVolume() - 10);
    }
}

// Refined Abstraction - Advanced Remote Control
class AdvancedRemoteControl extends RemoteControl {

    public AdvancedRemoteControl(Device device) {
        super(device);
    }

    public void mute() {
        device.setVolume(0);
        System.out.println("Device muted.");
    }
}
```

#### Using the Bridge Pattern

```java
public class Main {
    public static void main(String[] args) {
        // Use Basic Remote with TV
        System.out.println("Basic Remote controlling TV:");
        Device tv = new TV();
        RemoteControl basicRemote = new RemoteControl(tv) {};
        basicRemote.togglePower();
        basicRemote.volumeUp();
        basicRemote.togglePower();

        System.out.println("\nAdvanced Remote controlling Radio:");
        // Use Advanced Remote with Radio
        Device radio = new Radio();
        AdvancedRemoteControl advancedRemote = new AdvancedRemoteControl(radio);
        advancedRemote.togglePower();
        advancedRemote.volumeUp();
        advancedRemote.mute();
        advancedRemote.togglePower();
    }
}
```

#### Explanation

1. **Decoupling Abstraction and Implementation**: The `Device` interface represents the abstraction that can vary independently (TV, Radio), and the `RemoteControl` interface represents the remote’s functionality.
2. **Enhanced Flexibility**: By using composition, we can mix and match `Device` implementations with different `RemoteControl` implementations.
3. **Scalability**: It’s easy to add new device types or new remote control types without affecting existing code, making this a scalable and flexible solution.

This pattern enables you to change the class structure more easily, as the bridge allows the two parts to evolve separately.



### <mark style="color:red;">Composite Pattern</mark>&#x20;

* allows you to compose objects into tree-like structures to represent part-whole hierarchies.&#x20;
* This pattern treats individual objects and compositions of objects uniformly, making it easier to work with complex structures of objects as if they were single objects.

#### Key Concepts of the Composite Pattern

1. **Component**: Defines the interface for all objects in the composition (both leaf and composite nodes).
2. **Leaf**: Represents the basic building block objects that don’t have any children.
3. **Composite**: A component that has children and can hold other components. It implements methods to add, remove, or get children.

The Composite Pattern is particularly useful for building hierarchical structures like a tree of folders and files, organizational hierarchies, or graphical shapes.

#### Example Use Case: Files and Folders

In this example, we'll represent a file system where folders can contain both files and other folders. Each file and folder can be treated uniformly.

#### Example Code in Java

```java
import java.util.ArrayList;
import java.util.List;

// Component interface
interface FileSystemComponent {
    void showDetails();
}

// Leaf class
class File implements FileSystemComponent {
    private String name;
    private long size;

    public File(String name, long size) {
        this.name = name;
        this.size = size;
    }

    @Override
    public void showDetails() {
        System.out.println("File: " + name + " (" + size + " KB)");
    }
}

// Composite class
class Folder implements FileSystemComponent {
    private String name;
    private List<FileSystemComponent> components = new ArrayList<>();

    public Folder(String name) {
        this.name = name;
    }

    public void addComponent(FileSystemComponent component) {
        components.add(component);
    }

    public void removeComponent(FileSystemComponent component) {
        components.remove(component);
    }

    @Override
    public void showDetails() {
        System.out.println("Folder: " + name);
        for (FileSystemComponent component : components) {
            component.showDetails();
        }
    }
}
```

#### Using the Composite Pattern

Here’s how to create a folder structure using the Composite Pattern:

```java
public class Main {
    public static void main(String[] args) {
        // Leaf nodes
        File file1 = new File("file1.txt", 120);
        File file2 = new File("file2.txt", 80);
        File file3 = new File("file3.txt", 150);

        // Composite nodes
        Folder rootFolder = new Folder("root");
        Folder subFolder1 = new Folder("subFolder1");
        Folder subFolder2 = new Folder("subFolder2");

        // Constructing the hierarchy
        rootFolder.addComponent(file1);
        rootFolder.addComponent(subFolder1);

        subFolder1.addComponent(file2);
        subFolder1.addComponent(subFolder2);

        subFolder2.addComponent(file3);

        // Display details of all files and folders
        rootFolder.showDetails();
    }
}
```

#### Output

```
Folder: root
File: file1.txt (120 KB)
Folder: subFolder1
File: file2.txt (80 KB)
Folder: subFolder2
File: file3.txt (150 KB)
```

#### Explanation

1. **Component Interface**: `FileSystemComponent` is the common interface for both `File` and `Folder`. This allows treating files and folders in the same way.
2. **Leaf**: `File` is the leaf node. It has no children, and it implements the `showDetails()` method to display its own details.
3. **Composite**: `Folder` is the composite node. It contains a list of `FileSystemComponent` objects, allowing it to contain both files and folders.
4. **Hierarchy Management**: The `Folder` class provides methods to add and remove child components. This allows building a recursive structure of folders and files.
5. **Uniform Treatment**: The client code treats `File` and `Folder` uniformly through the `FileSystemComponent` interface, displaying the details of files and folders recursively.

#### Benefits of the Composite Pattern

* **Simplifies client code** by allowing it to treat individual objects and compositions uniformly.
* **Supports complex hierarchies** by enabling the creation of nested structures.
* **Extensibility**: New component types (e.g., new types of files or folders) can be added without altering existing code, as long as they implement the `FileSystemComponent` interface.

The Composite Pattern is ideal for cases where part-whole hierarchies need to be managed and treated as single units.

### <mark style="color:red;">Decorator Pattern</mark>&#x20;

* allows you to dynamically add new behaviors or functionalities to objects without altering their structure.&#x20;
* This is achieved by wrapping the object with a series of decorator classes, each adding its own behavior.&#x20;
* The pattern is useful when you want to extend the functionality of classes in a flexible and reusable way.

#### Key Concepts of the Decorator Pattern

1. **Component**: The common interface for both the original object and its decorators. This interface defines methods that can be enhanced by the decorators.
2. **Concrete Component**: The original class whose functionality needs to be extended.
3. **Decorator**: An abstract class implementing the `Component` interface, containing a reference to a `Component` object. Decorators delegate tasks to the component and can add extra behaviors.
4. **Concrete Decorators**: These classes extend the `Decorator` class and add specific functionalities to the `Component`.

The Decorator Pattern is commonly used in I/O streams in Java (e.g., `BufferedInputStream` decorating a `FileInputStream`) and in graphical user interfaces where visual elements may be decorated with borders, colors, or shadows.

#### Example Use Case: Coffee Shop

Let’s say we have a coffee shop, and we want to allow customers to add various ingredients to their coffee (like milk, sugar, and mocha). Using the Decorator Pattern, we can add these ingredients without changing the `Coffee` class itself.

#### Example Code in Java

```java
// Component interface
interface Coffee {
    String getDescription();
    double getCost();
}

// Concrete Component - Simple Coffee
class SimpleCoffee implements Coffee {
    @Override
    public String getDescription() {
        return "Simple Coffee";
    }

    @Override
    public double getCost() {
        return 2.0;
    }
}

// Base Decorator - CoffeeDecorator
abstract class CoffeeDecorator implements Coffee {
    protected Coffee decoratedCoffee;

    public CoffeeDecorator(Coffee coffee) {
        this.decoratedCoffee = coffee;
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription();
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost();
    }
}

// Concrete Decorators - Adding various ingredients
class MilkDecorator extends CoffeeDecorator {
    public MilkDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Milk";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.5;
    }
}

class SugarDecorator extends CoffeeDecorator {
    public SugarDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Sugar";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 0.2;
    }
}

class MochaDecorator extends CoffeeDecorator {
    public MochaDecorator(Coffee coffee) {
        super(coffee);
    }

    @Override
    public String getDescription() {
        return decoratedCoffee.getDescription() + ", Mocha";
    }

    @Override
    public double getCost() {
        return decoratedCoffee.getCost() + 1.0;
    }
}
```

#### Using the Decorator Pattern

Here's how to use the decorators to create different coffee combinations:

```java
public class Main {
    public static void main(String[] args) {
        // Create a simple coffee
        Coffee coffee = new SimpleCoffee();
        System.out.println(coffee.getDescription() + " - $" + coffee.getCost());

        // Add milk to coffee
        coffee = new MilkDecorator(coffee);
        System.out.println(coffee.getDescription() + " - $" + coffee.getCost());

        // Add sugar to coffee
        coffee = new SugarDecorator(coffee);
        System.out.println(coffee.getDescription() + " - $" + coffee.getCost());

        // Add mocha to coffee
        coffee = new MochaDecorator(coffee);
        System.out.println(coffee.getDescription() + " - $" + coffee.getCost());
    }
}
```

#### Output

```
Simple Coffee - $2.0
Simple Coffee, Milk - $2.5
Simple Coffee, Milk, Sugar - $2.7
Simple Coffee, Milk, Sugar, Mocha - $3.7
```

#### Explanation

1. **Component Interface**: `Coffee` defines the methods `getDescription` and `getCost` that each coffee object and decorator must implement.
2. **Concrete Component**: `SimpleCoffee` is the base type of coffee, and its behavior can be enhanced by decorators.
3. **Base Decorator**: `CoffeeDecorator` holds a reference to a `Coffee` object and implements the `Coffee` interface, forwarding calls to the decorated coffee object.
4. **Concrete Decorators**: `MilkDecorator`, `SugarDecorator`, and `MochaDecorator` add their own functionalities by extending `CoffeeDecorator`. Each decorator modifies the description and cost based on the ingredient added.
5. **Flexible Combinations**: The coffee object can be wrapped in any number of decorators, each adding a specific ingredient, allowing for flexible combinations.

#### Benefits of the Decorator Pattern

* **Flexibility in extending functionality**: The pattern allows adding features to an object without modifying its structure.
* **Adheres to the Open/Closed Principle**: New functionality can be added by creating new decorators, without changing existing code.
* **Combines behaviors dynamically**: You can add or remove decorators at runtime, giving dynamic control over the object’s behavior.

The Decorator Pattern is ideal for cases where multiple features or behaviors need to be added to an object in various combinations, without using inheritance.



### <mark style="color:red;">Facade Pattern</mark>&#x20;

* provides a simplified interface to a complex subsystem. It hides the complexity of multiple classes and interactions by providing a single interface that clients can use to perform complex operations.
* This pattern is often used to make a library, framework, or set of classes easier to use.

#### Key Concepts of the Facade Pattern

1. **Facade**: The main class that simplifies the interaction with the complex subsystem. It provides a single interface through which the client interacts with the system.
2. **Subsystem Classes**: These are the classes that perform the actual work but are hidden behind the facade. Each class in the subsystem has its own responsibilities and functions independently of the facade.

The Facade Pattern is useful when you want to provide a simple interface to a large, complex subsystem that the client does not need to understand in detail.

#### Example Use Case: Home Theater System

Imagine you have a home theater system with various components like an amplifier, projector, lights, and a DVD player. Turning on the home theater for a movie night requires multiple steps across each component. With the Facade Pattern, we can provide a single `HomeTheaterFacade` class to handle the steps automatically.

#### Example Code in Java

```java
// Subsystem classes
class Amplifier {
    public void on() {
        System.out.println("Amplifier is on.");
    }

    public void setVolume(int level) {
        System.out.println("Amplifier volume set to " + level);
    }
}

class Projector {
    public void on() {
        System.out.println("Projector is on.");
    }

    public void wideScreenMode() {
        System.out.println("Projector in widescreen mode.");
    }
}

class Lights {
    public void dim(int level) {
        System.out.println("Lights dimmed to " + level + "%.");
    }
}

class DVDPlayer {
    public void on() {
        System.out.println("DVD player is on.");
    }

    public void play(String movie) {
        System.out.println("Playing movie: " + movie);
    }
}

// Facade class
class HomeTheaterFacade {
    private Amplifier amp;
    private Projector projector;
    private Lights lights;
    private DVDPlayer dvd;

    public HomeTheaterFacade(Amplifier amp, Projector projector, Lights lights, DVDPlayer dvd) {
        this.amp = amp;
        this.projector = projector;
        this.lights = lights;
        this.dvd = dvd;
    }

    public void watchMovie(String movie) {
        System.out.println("Get ready to watch a movie...");
        lights.dim(10);
        amp.on();
        amp.setVolume(5);
        projector.on();
        projector.wideScreenMode();
        dvd.on();
        dvd.play(movie);
    }

    public void endMovie() {
        System.out.println("Shutting down the home theater...");
        lights.dim(100);
        amp.on();
        dvd.on();
        projector.on();
    }
}
```

#### Using the Facade Pattern

```java
public class Main {
    public static void main(String[] args) {
        Amplifier amp = new Amplifier();
        Projector projector = new Projector();
        Lights lights = new Lights();
        DVDPlayer dvd = new DVDPlayer();

        HomeTheaterFacade homeTheater = new HomeTheaterFacade(amp, projector, lights, dvd);
        homeTheater.watchMovie("Inception");
        homeTheater.endMovie();
    }
}
```

#### Output

```
Get ready to watch a movie...
Lights dimmed to 10%.
Amplifier is on.
Amplifier volume set to 5.
Projector is on.
Projector in widescreen mode.
DVD player is on.
Playing movie: Inception
Shutting down the home theater...
Lights dimmed to 100%.
Amplifier is on.
DVD player is on.
Projector is on.
```

#### Explanation

1. **Subsystem Classes**: `Amplifier`, `Projector`, `Lights`, and `DVDPlayer` are individual components in the home theater system. Each class provides its own specific functionality.
2. **Facade**: `HomeTheaterFacade` provides a simplified interface to control all the components of the home theater system. It encapsulates the complex interactions between components in methods like `watchMovie` and `endMovie`.
3. **Single Entry Point**: The client only interacts with `HomeTheaterFacade`, simplifying the process of setting up and shutting down the home theater.

#### Benefits of the Facade Pattern

* **Simplifies Interface**: Reduces the complexity of the system by hiding complex interactions behind a single, simplified interface.
* **Promotes Loose Coupling**: The facade decouples the client from the subsystem, allowing the subsystem to evolve without impacting the client code.
* **Improves Code Readability**: Facade code often represents a high-level overview of the system’s main tasks, making the code more readable and organized.

#### When to Use the Facade Pattern

* When you have a complex subsystem that needs to be simplified for easier use.
* When you want to decouple a system from its clients to reduce dependencies.
* When you have several tightly coupled classes in a subsystem that would benefit from a single entry point for interaction.

The Facade Pattern is ideal when you need to simplify complex systems and create a single entry point that can manage multiple interactions behind the scenes.

### <mark style="color:red;">Flyweight Pattern</mark>&#x20;

* allows you to reuse objects efficiently by sharing them across multiple contexts, instead of creating new instances for each context.&#x20;
* This is especially useful when there are a large number of similar objects, and creating a new object for each use would be costly in terms of memory.
* The pattern minimizes memory usage by storing shared parts of objects in a central place and referencing them in different contexts. It distinguishes between _intrinsic_ (shared) and _extrinsic_ (unique per instance) states:
  * **Intrinsic State**: The shared state that does not change across instances and can be stored in the flyweight object.
  * **Extrinsic State**: The context-specific state that varies for each instance and is passed to the flyweight object when used.
* The Flyweight Pattern is often used in applications that involve large numbers of similar objects, like rendering text characters or graphics.

#### Key Concepts of the Flyweight Pattern

1. **Flyweight Interface**: Defines the interface for flyweight objects. It provides methods to receive the extrinsic state (data unique to each instance).
2. **Concrete Flyweight**: Implements the flyweight interface and stores the intrinsic state. It uses extrinsic data provided by the client to perform operations.
3. **Flyweight Factory**: Manages and shares flyweight instances. It ensures that identical flyweights are shared instead of creating new ones.
4. **Client**: The client is responsible for storing or calculating extrinsic data and passing it to the flyweight.

#### Example Use Case: Text Character Rendering

Let’s say we are rendering a document containing millions of characters. Each character can be rendered with different font sizes, colors, and other properties. Rather than creating an object for each character, we can use the Flyweight Pattern to store a shared object for each unique character type, reducing memory usage.

#### Example Code in Java

```java
import java.util.HashMap;
import java.util.Map;

// Flyweight interface
interface Character {
    void display(int fontSize);
}

// Concrete Flyweight class
class ConcreteCharacter implements Character {
    private char symbol; // Intrinsic state, the character symbol itself

    public ConcreteCharacter(char symbol) {
        this.symbol = symbol;
    }

    @Override
    public void display(int fontSize) {
        System.out.println("Character: " + symbol + " displayed at font size " + fontSize);
    }
}

// Flyweight Factory
class CharacterFactory {
    private Map<Character, ConcreteCharacter> characters = new HashMap<>();

    public Character getCharacter(char symbol) {
        // Check if the character already exists
        ConcreteCharacter character = characters.get(symbol);
        if (character == null) {
            // If not, create a new one and add it to the pool
            character = new ConcreteCharacter(symbol);
            characters.put(symbol, character);
            System.out.println("Creating new character: " + symbol);
        } else {
            System.out.println("Reusing existing character: " + symbol);
        }
        return character;
    }
}

// Client
public class Main {
    public static void main(String[] args) {
        CharacterFactory factory = new CharacterFactory();

        // Creating characters with different font sizes
        Character characterA1 = factory.getCharacter('A');
        characterA1.display(12);

        Character characterA2 = factory.getCharacter('A');
        characterA2.display(14);

        Character characterB1 = factory.getCharacter('B');
        characterB1.display(12);

        Character characterB2 = factory.getCharacter('B');
        characterB2.display(18);
    }
}
```

#### Output

```
Creating new character: A
Character: A displayed at font size 12
Reusing existing character: A
Character: A displayed at font size 14
Creating new character: B
Character: B displayed at font size 12
Reusing existing character: B
Character: B displayed at font size 18
```

#### Explanation

1. **Flyweight Interface**: The `Character` interface defines the `display` method, which accepts the font size (extrinsic state) as a parameter.
2. **Concrete Flyweight**: `ConcreteCharacter` implements the `Character` interface and stores the intrinsic state (the symbol itself).
3. **Flyweight Factory**: `CharacterFactory` is responsible for managing and reusing `ConcreteCharacter` instances. It stores previously created instances in a `Map` and returns an existing instance if it already exists.
4. **Client Code**: The `Main` class uses the `CharacterFactory` to create and display characters. The font size is passed as an extrinsic state, allowing the same character object to be reused with different font sizes.

#### Benefits of the Flyweight Pattern

* **Memory Efficiency**: The pattern reduces the number of objects created, leading to lower memory usage.
* **Performance Boost**: Fewer objects mean fewer memory allocations, which can improve performance.
* **Reuse of Objects**: Allows reusing objects with shared properties, which helps manage resources effectively.

#### When to Use the Flyweight Pattern

* When your application creates a large number of objects, and this causes memory or performance issues.
* When most object states can be made extrinsic and passed in from the client, making objects sharable.
* When the application requires a large number of similar objects that can benefit from a centralized management approach.

The Flyweight Pattern is ideal when working with repetitive, memory-intensive data, such as rendering characters, graphics, or large datasets with limited variations.

### <mark style="color:red;">Proxy Pattern</mark>

* provides a placeholder or surrogate object to control access to another object.&#x20;
* It is often used when accessing an object directly could be undesirable, such as when the object is resource-intensive to create, is located remotely, or requires additional control before access.
* The Proxy Pattern allows you to add a layer of control, making it useful for scenarios like lazy initialization, access control, logging, caching, and monitoring.

#### Types of Proxies

1. **Virtual Proxy**: Controls access to a resource that may be expensive to create. It creates the object only when needed (e.g., lazy initialization).
2. **Protection Proxy**: Manages access to an object based on user permissions or access levels.
3. **Remote Proxy**: Represents an object that resides in a different address space, such as a remote server.
4. **Smart Proxy**: Adds additional actions when an object is accessed, such as reference counting, logging, or caching.

#### Example Use Case: Image Loading with Virtual Proxy

Imagine a situation where you have an application that displays images. Loading all images at once can be resource-intensive. Using a virtual proxy, we can create a placeholder for each image, loading it only when it is accessed for the first time.

#### Example Code in Java

```java
// Subject interface
interface Image {
    void display();
}

// Real Subject - Actual Image class
class RealImage implements Image {
    private String filename;

    public RealImage(String filename) {
        this.filename = filename;
        loadImage();
    }

    private void loadImage() {
        System.out.println("Loading image from disk: " + filename);
    }

    @Override
    public void display() {
        System.out.println("Displaying image: " + filename);
    }
}

// Proxy class - Image Proxy
class ImageProxy implements Image {
    private RealImage realImage;
    private String filename;

    public ImageProxy(String filename) {
        this.filename = filename;
    }

    @Override
    public void display() {
        // Lazy initialization: load the image only when it’s needed
        if (realImage == null) {
            realImage = new RealImage(filename);
        }
        realImage.display();
    }
}

// Client code
public class Main {
    public static void main(String[] args) {
        Image image1 = new ImageProxy("photo1.jpg");
        Image image2 = new ImageProxy("photo2.jpg");

        // Image is loaded only when display is called
        System.out.println("Accessing image1 for the first time:");
        image1.display(); // Loads and displays the image
        System.out.println("\nAccessing image1 again:");
        image1.display(); // Only displays the image, no loading

        System.out.println("\nAccessing image2 for the first time:");
        image2.display(); // Loads and displays the image
    }
}
```

#### Output

```
Accessing image1 for the first time:
Loading image from disk: photo1.jpg
Displaying image: photo1.jpg

Accessing image1 again:
Displaying image: photo1.jpg

Accessing image2 for the first time:
Loading image from disk: photo2.jpg
Displaying image: photo2.jpg
```

#### Explanation

1. **Subject Interface**: `Image` defines the `display` method, which both the real image and proxy implement.
2. **Real Subject**: `RealImage` is the actual image class that loads an image from the disk and displays it.
3. **Proxy**: `ImageProxy` is a virtual proxy that holds a reference to `RealImage`. The `display` method in the proxy checks if the `RealImage` instance is null and creates it only if necessary. This lazy initialization avoids loading the image until it’s actually needed.
4. **Client**: The client uses `ImageProxy` instances for `image1` and `image2`. The first call to `display` on each proxy triggers the loading of the actual image, but subsequent calls just display it without reloading.

#### Benefits of the Proxy Pattern

* **Control Access**: Can manage access to the real object based on permissions, conditions, or other constraints.
* **Lazy Initialization**: Useful for delaying the instantiation of resource-intensive objects until they are needed.
* **Additional Functionality**: Proxies can add functionality such as logging, caching, and counting references.
* **Remote Access**: Enables remote access to objects as if they were local.

#### Common Uses of the Proxy Pattern

1. **Virtual Proxy**: For delaying expensive resource creation or loading, such as images, files, or connections.
2. **Protection Proxy**: For access control based on user roles, commonly used in systems with sensitive data.
3. **Remote Proxy**: In distributed systems where objects are located in remote servers (e.g., RMI in Java).
4. **Smart Proxy**: For logging, caching, and monitoring purposes.

#### When to Use the Proxy Pattern

* When accessing an object directly is expensive and can be delayed.
* When additional actions are required before or after accessing an object.
* When you want to control access based on certain conditions, such as permissions or availability.
* When working with remote or distributed objects that need to appear as local.

The Proxy Pattern provides flexibility and control over object access, making it a useful design pattern for many situations requiring indirect control or deferred initialization.

## <mark style="color:yellow;">**Behavioral Patterns**</mark><mark style="color:yellow;">:</mark>

* We choose behavioral design pattern when we need to manage algorithms, communication or responsibilities between objects.
* They are useful for encapsulating behavior that varies and promoting loose coupling between objects.
*   We choose behavior design pattern when the problem is related to object interations.

    * These facilitate code reuse, flexibility and maintainability by defining how objects interact and communicate.
    * Use them to address scenarios like handling complex workflows, managing state transitions or implementing communication between objects.



    <figure><img src="../../../.gitbook/assets/image (108).png" alt=""><figcaption></figcaption></figure>

<table><thead><tr><th width="107.3333740234375" align="center">Pattern</th><th>Description</th></tr></thead><tbody><tr><td align="center">Chain of Responsibility</td><td><ul><li><strong>Purpose</strong>: Allows passing a request along a chain of handlers. Each handler decides whether to process the request or pass it to the next handler.</li><li><strong>Use Case</strong>: Logging systems, user request processing pipelines.</li><li><strong>Example</strong>: Middleware chain in web applications where each middleware can decide to process or pass the request.</li></ul></td></tr><tr><td align="center">Command</td><td><ul><li><strong>Purpose</strong>: Encapsulates a request as an object, allowing parameterization of requests and supporting undoable operations.</li><li><strong>Use Case</strong>: Implementing undo/redo functionality, queuing tasks, and logging changes.</li><li><strong>Example</strong>: Remote control operations where each button press is encapsulated as a command.</li></ul></td></tr><tr><td align="center">Interpreter</td><td><ul><li><strong>Purpose</strong>: Defines a language's grammar and uses an interpreter to interpret sentences in the language.</li><li><strong>Use Case</strong>: Situations that involve language processing, such as SQL or mathematical expressions.</li><li><strong>Example</strong>: Parsing and evaluating expressions in a calculator or SQL-like language interpreter.</li></ul></td></tr><tr><td align="center">Iterator</td><td><ul><li><strong>Purpose</strong>: Provides a way to access elements of an aggregate object sequentially without exposing its underlying structure.</li><li><strong>Use Case</strong>: Iterating over complex data structures without exposing their internal representation.</li><li><strong>Example</strong>: <code>Iterator</code> classes in collections (e.g., <code>ArrayList.iterator()</code> in Java).</li></ul></td></tr><tr><td align="center">Mediator</td><td><ul><li><strong>Purpose</strong>: Defines an object that encapsulates how a set of objects interact, promoting loose coupling between components.</li><li><strong>Use Case</strong>: Reducing dependencies in systems with many interacting objects.</li><li><strong>Example</strong>: Chatroom mediator where users communicate through a central mediator.</li></ul></td></tr><tr><td align="center">Memento</td><td><ul><li><strong>Purpose</strong>: Captures an object’s state to restore it later without exposing its details.</li><li><strong>Use Case</strong>: Undo or rollback functionality.</li><li><strong>Example</strong>: Text editor with "undo" functionality using snapshots of text states.</li></ul></td></tr><tr><td align="center">Observer</td><td><ul><li><strong>Purpose</strong>: Establishes a one-to-many dependency where when one object changes, its dependents are notified and updated automatically.</li><li><strong>Use Case</strong>: Event handling systems, real-time data feeds.</li><li><strong>Example</strong>: GUI components where changes in the model reflect on the view (e.g., MVC pattern).</li></ul></td></tr><tr><td align="center">State</td><td><ul><li><strong>Purpose</strong>: Allows an object to alter its behavior when its internal state changes, appearing as though it changed its class.</li><li><strong>Use Case</strong>: Systems where objects' behavior depends on their state.</li><li><strong>Example</strong>: Traffic lights that change behavior based on light color.</li></ul></td></tr><tr><td align="center">Strategy</td><td><ul><li><strong>Purpose</strong>: Defines a family of algorithms, encapsulates each one, and makes them interchangeable.</li><li><strong>Use Case</strong>: When you need to switch algorithms or behaviors at runtime.</li><li><strong>Example</strong>: Sorting strategies like quicksort, mergesort, or bubble sort in a context where the algorithm can vary.</li></ul></td></tr><tr><td align="center">Template Method</td><td><ul><li><strong>Purpose</strong>: Defines the skeleton of an algorithm, letting subclasses redefine certain steps without changing the algorithm’s structure.</li><li><strong>Use Case</strong>: When a method’s structure is fixed but certain steps vary.</li><li><strong>Example</strong>: Abstract classes in frameworks where the framework provides the skeleton, and the user provides specific implementations.</li></ul></td></tr><tr><td align="center">Visitor</td><td><ul><li><strong>Purpose</strong>: Separates an algorithm from the objects on which it operates, allowing new operations to be added without modifying object classes.</li><li><strong>Use Case</strong>: When you need to perform operations across a complex object structure without changing classes.</li><li><strong>Example</strong>: File system traversal where you want to perform different actions on files and directories</li></ul></td></tr></tbody></table>

### <mark style="color:red;">Chain of Responsibility</mark>&#x20;

* allows a request to pass through a series of handlers, where each handler can either process the request or pass it to the next handler in the chain.&#x20;
* This pattern promotes loose coupling, as the sender of a request doesn’t need to know which component will handle it, or how many handlers there are.

#### Key Concepts of the Chain of Responsibility Pattern

1. **Handler Interface**: Defines an interface for handling requests. It typically has a method to set the next handler in the chain and a method to handle the request.
2. **Concrete Handlers**: Implement the handler interface and either process the request or pass it to the next handler.
3. **Client**: Creates a chain of handlers and submits a request.

This pattern is particularly useful for situations where multiple handlers can handle the same type of request, and the request should be passed along the chain until it’s handled.

#### Example Use Case: Request Logging and Processing

Imagine a system where each request goes through several stages, such as logging, authorization, and validation, before reaching the main processing stage. Each of these stages can be handled by a separate handler in a chain, and if one handler cannot handle the request, it passes it to the next handler.

#### Example Code in Java

```java
// Handler interface
abstract class RequestHandler {
    protected RequestHandler nextHandler;

    // Sets the next handler in the chain
    public void setNextHandler(RequestHandler nextHandler) {
        this.nextHandler = nextHandler;
    }

    // Handles the request, either processing it or passing it on
    public abstract void handleRequest(String request);
}

// Concrete Handler for logging
class LoggingHandler extends RequestHandler {
    @Override
    public void handleRequest(String request) {
        System.out.println("Logging request: " + request);
        if (nextHandler != null) {
            nextHandler.handleRequest(request);
        }
    }
}

// Concrete Handler for authorization
class AuthorizationHandler extends RequestHandler {
    @Override
    public void handleRequest(String request) {
        if (request.contains("authorized")) {
            System.out.println("Authorization successful for request: " + request);
            if (nextHandler != null) {
                nextHandler.handleRequest(request);
            }
        } else {
            System.out.println("Authorization failed for request: " + request);
        }
    }
}

// Concrete Handler for validation
class ValidationHandler extends RequestHandler {
    @Override
    public void handleRequest(String request) {
        if (request.matches("^[a-zA-Z0-9_]+$")) { // Simple alphanumeric validation
            System.out.println("Validation successful for request: " + request);
            if (nextHandler != null) {
                nextHandler.handleRequest(request);
            }
        } else {
            System.out.println("Validation failed for request: " + request);
        }
    }
}

// Client code
public class Main {
    public static void main(String[] args) {
        // Create handlers
        RequestHandler loggingHandler = new LoggingHandler();
        RequestHandler authorizationHandler = new AuthorizationHandler();
        RequestHandler validationHandler = new ValidationHandler();

        // Set up the chain
        loggingHandler.setNextHandler(authorizationHandler);
        authorizationHandler.setNextHandler(validationHandler);

        // Submit requests
        System.out.println("Processing request 1:");
        loggingHandler.handleRequest("authorized_request1");

        System.out.println("\nProcessing request 2:");
        loggingHandler.handleRequest("unauthorized_request2");

        System.out.println("\nProcessing request 3:");
        loggingHandler.handleRequest("authorized_invalid-request3");
    }
}
```

#### Output

```
Processing request 1:
Logging request: authorized_request1
Authorization successful for request: authorized_request1
Validation successful for request: authorized_request1

Processing request 2:
Logging request: unauthorized_request2
Authorization failed for request: unauthorized_request2

Processing request 3:
Logging request: authorized_invalid-request3
Authorization successful for request: authorized_invalid-request3
Validation failed for request: authorized_invalid-request3
```

#### Explanation

1. **Handler Interface**: `RequestHandler` is an abstract class that defines a `handleRequest` method and a `setNextHandler` method to chain handlers together.
2. **Concrete Handlers**:
   * `LoggingHandler` logs the request and passes it along the chain.
   * `AuthorizationHandler` checks if the request is authorized. If it is, the request is passed along; otherwise, it stops.
   * `ValidationHandler` validates the request format. If valid, it passes it along; if invalid, it stops.
3. **Client**: The `Main` class creates each handler, sets up the chain, and submits requests. Each request passes from handler to handler until it’s either processed or rejected.

#### Benefits of the Chain of Responsibility Pattern

* **Decoupling of Sender and Receiver**: The client doesn’t need to know which handler will process the request.
* **Flexible Chains**: Handlers can be added, removed, or reordered without modifying the client code.
* **Single Responsibility**: Each handler has one specific responsibility, promoting cleaner, more maintainable code.

#### Drawbacks

* **Performance Overhead**: Passing requests along a long chain can impact performance.
* **Difficult Debugging**: When many handlers are in the chain, it may become difficult to trace the request’s flow.

#### Common Use Cases

* **Event Handling Systems**: In GUI applications, different components handle events such as mouse clicks, with each component deciding to process the event or pass it on.
* **Customer Support System**: Requests are escalated through different support levels until they’re resolved.
* **Logging Frameworks**: Different loggers handle messages based on priority, with lower priority messages passed up the chain if they’re not handled.

The Chain of Responsibility Pattern is useful when multiple handlers might handle requests in specific scenarios or in a specific order, allowing flexibility and reusability.



### <mark style="color:red;">Command Pattern</mark>&#x20;

* turns a request into a standalone object. This object contains all the information about the request, such as the operation to be performed, the receiver of the request, and any parameters required.&#x20;
* By doing so, the pattern decouples the sender of the request from the receiver, allowing for operations to be parameterized, queued, logged, and even undone or redone.

#### Key Concepts of the Command Pattern

1. **Command Interface**: Declares an abstract method for executing commands.
2. **Concrete Command**: Implements the command interface and defines a binding between an action and a receiver.
3. **Receiver**: The actual component that performs the action or business logic.
4. **Invoker**: Initiates the command. It knows how to execute a command but doesn’t know the details of what the command does.
5. **Client**: Creates concrete commands and sets the receivers.

#### Example Use Case: Remote Control for Home Appliances

Imagine you have a remote control that can turn on and off different devices, like a light or a fan. Instead of having a direct dependency on each device, we can create command objects for each action, making the remote flexible and extensible.

#### Example Code in Java

```java
// Command Interface
interface Command {
    void execute();
    void undo(); // Optional undo functionality
}

// Receiver Class - Light
class Light {
    public void turnOn() {
        System.out.println("The light is on.");
    }

    public void turnOff() {
        System.out.println("The light is off.");
    }
}

// Concrete Command for turning the light on
class LightOnCommand implements Command {
    private Light light;

    public LightOnCommand(Light light) {
        this.light = light;
    }

    @Override
    public void execute() {
        light.turnOn();
    }

    @Override
    public void undo() {
        light.turnOff();
    }
}

// Concrete Command for turning the light off
class LightOffCommand implements Command {
    private Light light;

    public LightOffCommand(Light light) {
        this.light = light;
    }

    @Override
    public void execute() {
        light.turnOff();
    }

    @Override
    public void undo() {
        light.turnOn();
    }
}

// Invoker Class - Remote Control
class RemoteControl {
    private Command command;

    // Set the command to be executed
    public void setCommand(Command command) {
        this.command = command;
    }

    // Execute the command
    public void pressButton() {
        command.execute();
    }

    // Undo the last command
    public void pressUndo() {
        command.undo();
    }
}

// Client Code
public class Main {
    public static void main(String[] args) {
        // Receiver
        Light light = new Light();

        // Concrete Commands
        Command lightOn = new LightOnCommand(light);
        Command lightOff = new LightOffCommand(light);

        // Invoker
        RemoteControl remote = new RemoteControl();

        // Turn the light on
        System.out.println("Turning the light on:");
        remote.setCommand(lightOn);
        remote.pressButton();

        // Turn the light off
        System.out.println("\nTurning the light off:");
        remote.setCommand(lightOff);
        remote.pressButton();

        // Undo the last operation (turns light on again)
        System.out.println("\nUndoing the last operation:");
        remote.pressUndo();
    }
}
```

#### Output

```
Turning the light on:
The light is on.

Turning the light off:
The light is off.

Undoing the last operation:
The light is on.
```

#### Explanation

1. **Command Interface**: `Command` declares the `execute` and `undo` methods. These methods encapsulate the behavior that will be triggered by the `Invoker`.
2. **Concrete Commands**:
   * `LightOnCommand` encapsulates the action of turning the light on, with the `execute` method invoking `light.turnOn()` and `undo` turning the light off.
   * `LightOffCommand` encapsulates turning the light off, with `execute` calling `light.turnOff()` and `undo` turning the light on.
3. **Receiver**: `Light` class has methods to turn the light on and off.
4. **Invoker**: `RemoteControl` holds a reference to a command and triggers its `execute` and `undo` methods, depending on which button is pressed.
5. **Client**: The `Main` class creates `LightOnCommand` and `LightOffCommand` objects, sets them on the `RemoteControl`, and executes them.

This setup allows for commands to be added, removed, or changed without altering the `RemoteControl` or `Light` classes, adhering to the **Open/Closed Principle**.

#### Benefits of the Command Pattern

* **Decoupling**: Separates the object that invokes the command from the one that knows how to execute it.
* **Extensibility**: New commands can be added without modifying existing code.
* **Undo/Redo Support**: Supports operations to be undone or redone by storing state in the command.
* **Queuing and Logging**: Commands can be queued for delayed execution or logged for audit purposes.

#### Drawbacks

* **Complexity**: Adding commands for every operation can lead to more classes, which might add complexity.
* **Memory Overhead**: If using undo functionality, commands may need to store the state, which could increase memory usage.

#### Common Use Cases

1. **Undo/Redo Operations**: Text editors, drawing applications, and IDEs.
2. **Action Queueing**: Scheduling operations for later execution.
3. **Macro Recording**: Grouping commands into a sequence that can be executed as a unit (like a macro).
4. **Remote Control Applications**: Any control mechanism that needs to trigger commands on different devices (e.g., smart home systems).

The Command Pattern is a powerful way to encapsulate requests and add flexibility in controlling actions, making it highly suitable for applications needing dynamic and extensible command processing.



### <mark style="color:red;">**Interpreter Pattern**</mark>

* used to evaluate and interpret sentences or expressions in a given language.&#x20;
* This pattern is useful for designing simple language interpreters or implementing rules and grammars, making it ideal for parsing expressions in a specific problem domain, such as mathematical expressions, log filters, or parsing configuration files.

#### Key Concepts of the Interpreter Pattern

1. **Context**: Provides the environment or context in which expressions are interpreted. It holds data values, variable assignments, and other relevant information needed for evaluation.
2. **Abstract Expression**: Defines an interface for interpreting expressions. Each specific expression type will implement this interface.
3. **Terminal Expression**: Represents specific objects in the language (e.g., numbers, constants) and evaluates directly. These are the "leaf" nodes in the pattern structure.
4. **Non-Terminal Expression**: Combines terminal and other expressions. For example, addition or subtraction expressions in an arithmetic interpreter.

#### Example: Arithmetic Expression Interpreter

In this example, we'll build a simple interpreter for arithmetic expressions involving addition and subtraction.

**Step-by-Step Code**

1.  **Define the Expression Interface**: This interface will be implemented by all expressions in the language.

    ```python
    from abc import ABC, abstractmethod

    class Expression(ABC):
        @abstractmethod
        def interpret(self, context):
            pass
    ```
2.  **Implement Terminal Expressions**: The `Number` class here represents a number in the arithmetic expression.

    ```python
    class Number(Expression):
        def __init__(self, value):
            self.value = value
        
        def interpret(self, context=None):
            return self.value
    ```
3.  **Implement Non-Terminal Expressions**: We’ll create two non-terminal expressions: `Add` and `Subtract`.

    ```python
    class Add(Expression):
        def __init__(self, left, right):
            self.left = left
            self.right = right
        
        def interpret(self, context=None):
            return self.left.interpret(context) + self.right.interpret(context)

    class Subtract(Expression):
        def __init__(self, left, right):
            self.left = left
            self.right = right
        
        def interpret(self, context=None):
            return self.left.interpret(context) - self.right.interpret(context)
    ```
4.  **Create a Context and Client Code**: We can now use these classes to interpret a basic arithmetic expression, such as `(5 + 10) - 3`.

    ```python
    if __name__ == "__main__":
        # Represent the expression (5 + 10) - 3
        expression = Subtract(
            Add(Number(5), Number(10)),
            Number(3)
        )

        # Interpret the expression
        result = expression.interpret()
        print("Result:", result)  # Output should be 12
    ```

#### Explanation of the Code

* **Number**: This is a terminal expression that simply returns the number itself.
* **Add and Subtract**: These are non-terminal expressions that interpret expressions by evaluating the left and right sub-expressions.
* **expression**: We construct an arithmetic expression `(5 + 10) - 3` by composing `Add` and `Subtract` objects, demonstrating how the interpreter pattern can build complex expressions by combining smaller expressions.

#### When to Use the Interpreter Pattern

* When you have a language to interpret, and you can represent statements in this language as an abstract syntax tree (AST).
* When the grammar of the language is relatively simple and doesn’t need heavy optimization.
* In applications that require configurable rule-based logic, like converting user commands, translating protocols, or evaluating mathematical expressions.

#### Pros and Cons

**Pros**:

* Simple, easy-to-extend implementation for small languages.
* Flexible, as new expressions can be added without changing the existing codebase.

**Cons**:

* Can become complex and inefficient if the language or grammar is large.
* Inefficient for performance-sensitive applications due to deep recursive calls.

### <mark style="color:red;">**Iterator Pattern**</mark>

* provides a way to access the elements of a collection (such as an array, list, or tree) sequentially without exposing its underlying representation.&#x20;
* This pattern is particularly useful for traversing complex data structures in a standardized way, promoting encapsulation and separation of concerns.

#### Key Concepts of the Iterator Pattern

1. **Iterator Interface**: Defines methods to traverse elements in a collection (such as `next`, `hasNext`, etc.).
2. **Concrete Iterator**: Implements the iterator interface for a specific collection, holding a reference to the collection and a cursor (or index) to keep track of the current position.
3. **Aggregate (Collection)**: The collection or aggregate object that provides an `iterator()` method to return an iterator object.
4. **Client**: Uses the iterator to access elements in the collection without needing to know the details of the underlying data structure.

#### Example: Custom List Iterator

In this example, we will build a custom list iterator for a `NameCollection` class.

**Step-by-Step Code**

1.  **Define the Iterator Interface**: This interface defines the methods for traversal.

    ```python
    from abc import ABC, abstractmethod

    class Iterator(ABC):
        @abstractmethod
        def has_next(self):
            pass

        @abstractmethod
        def next(self):
            pass
    ```
2.  **Implement the Collection (Aggregate) Interface**: This interface provides a method to get an iterator.

    ```python
    class IterableCollection(ABC):
        @abstractmethod
        def get_iterator(self):
            pass
    ```
3.  **Implement the Concrete Collection**: We’ll create a `NameCollection` class that stores names and returns an iterator.

    ```python
    class NameCollection(IterableCollection):
        def __init__(self, names):
            self.names = names
        
        def get_iterator(self):
            return NameIterator(self.names)
    ```
4.  **Implement the Concrete Iterator**: The `NameIterator` class will implement the `Iterator` interface to traverse the `NameCollection`.

    ```python
    class NameIterator(Iterator):
        def __init__(self, names):
            self.names = names
            self.index = 0
        
        def has_next(self):
            return self.index < len(self.names)
        
        def next(self):
            if self.has_next():
                name = self.names[self.index]
                self.index += 1
                return name
            else:
                raise StopIteration("No more elements")
    ```
5.  **Client Code to Use the Iterator**: Now we can use the `NameIterator` to iterate through `NameCollection`.

    ```python
    if __name__ == "__main__":
        names = ["Alice", "Bob", "Charlie", "Diana"]
        name_collection = NameCollection(names)
        iterator = name_collection.get_iterator()

        while iterator.has_next():
            print(iterator.next())
    ```

    Output:

    ```
    Alice
    Bob
    Charlie
    Diana
    ```

#### Explanation of the Code

* **NameCollection**: This class represents the collection and provides a `get_iterator` method to return an iterator for its elements.
* **NameIterator**: Implements `Iterator` to allow sequential access to the elements in `NameCollection`. It maintains an index to keep track of the current position.
* **Client Code**: Uses the `NameIterator` to traverse the `NameCollection` without directly accessing its internal structure.

#### When to Use the Iterator Pattern

* When you need to traverse a complex data structure without exposing its details.
* When you have multiple ways of traversing a collection, and each traversal can be implemented as a separate iterator.
* When you want to provide a uniform traversal interface across different types of collections (e.g., lists, trees, graphs).

#### Pros and Cons

**Pros**:

* Promotes encapsulation by hiding the collection’s internal structure.
* Provides a uniform way to traverse different types of collections.
* Supports multiple traversal strategies by creating different iterator implementations.

**Cons**:

* May add extra complexity when used with simple collections (such as lists).
* Increases the memory footprint, as iterators need to maintain state for tracking traversal.

### <mark style="color:red;">**Mediator Pattern**</mark>

* facilitates communication between objects (known as "colleagues") by centralizing complex communication logic in a separate mediator object.&#x20;
* This pattern promotes loose coupling, as the colleagues don't need to directly reference or interact with each other; instead, they communicate through the mediator.

#### Key Concepts of the Mediator Pattern

1. **Mediator Interface**: Defines the interface for communication between colleagues.
2. **Concrete Mediator**: Implements the mediator interface and coordinates communication between colleague objects, managing dependencies and interactions.
3. **Colleague Classes**: Represent components or classes that communicate with each other through the mediator. Each colleague holds a reference to the mediator and uses it to send messages to other colleagues.
4. **Client**: Instantiates the colleagues and the mediator, and triggers communication through the mediator.

#### Example: Chat Room Mediator

In this example, we’ll create a simple chat room where different users (colleagues) can send messages to each other through a mediator.

**Step-by-Step Code**

1.  **Define the Mediator Interface**: This interface provides a method for colleagues to send messages.

    ```python
    from abc import ABC, abstractmethod

    class ChatMediator(ABC):
        @abstractmethod
        def send_message(self, message, user):
            pass
    ```
2.  **Implement the Concrete Mediator**: The `ChatRoom` class implements the mediator interface to manage communication between users.

    ```python
    class ChatRoom(ChatMediator):
        def __init__(self):
            self.users = []

        def add_user(self, user):
            self.users.append(user)

        def send_message(self, message, user):
            for u in self.users:
                if u != user:  # Don't send the message to the sender
                    u.receive_message(message, user)
    ```
3.  **Define the Colleague Class**: This `User` class will represent the participants in the chat room. Each user has a reference to the chat mediator.

    ```python
    class User:
        def __init__(self, name, mediator):
            self.name = name
            self.mediator = mediator

        def send_message(self, message):
            print(f"{self.name} sends message: {message}")
            self.mediator.send_message(message, self)

        def receive_message(self, message, sender):
            print(f"{self.name} received message from {sender.name}: {message}")
    ```
4.  **Client Code to Use the Mediator**: We can now create the `ChatRoom` mediator and add users to it.

    ```python
    if __name__ == "__main__":
        chat_room = ChatRoom()

        user1 = User("Alice", chat_room)
        user2 = User("Bob", chat_room)
        user3 = User("Charlie", chat_room)

        chat_room.add_user(user1)
        chat_room.add_user(user2)
        chat_room.add_user(user3)

        user1.send_message("Hello everyone!")
        user2.send_message("Hi Alice!")
    ```

    Output:

    ```
    Alice sends message: Hello everyone!
    Bob received message from Alice: Hello everyone!
    Charlie received message from Alice: Hello everyone!
    Bob sends message: Hi Alice!
    Alice received message from Bob: Hi Alice!
    Charlie received message from Bob: Hi Alice!
    ```

#### Explanation of the Code

* **ChatRoom (Mediator)**: Manages communication between `User` objects, sending each message to all users except the sender.
* **User (Colleague)**: Represents a chat user who can send and receive messages. Each user has a reference to the `ChatRoom` mediator.
* **Client Code**: Sets up the mediator and colleagues, adds users to the chat room, and triggers messages to demonstrate interaction.

#### When to Use the Mediator Pattern

* When you have a complex web of communication between multiple objects, and you want to simplify and centralize this communication.
* When you want to promote loose coupling and reduce dependencies between objects.
* When you need to add or change interaction logic without affecting the communicating classes directly.

#### Pros and Cons

**Pros**:

* Reduces coupling between colleague classes by centralizing interactions.
* Simplifies object communication and can make the code more readable.
* Makes it easier to modify communication logic without changing individual colleague classes.

**Cons**:

* The mediator can become a complex "god object" if too much logic is placed in it, which can lead to high maintenance costs.
* Can make the code harder to follow if the mediator becomes too complex, obscuring how interactions work.

This example shows how the mediator pattern simplifies the communication between users, keeping each user independent of others and delegating the communication logic to a central mediator.

### <mark style="color:red;">**Memento Pattern**</mark>

* allows an object to save and restore its previous state without violating encapsulation.&#x20;
* The pattern is commonly used in scenarios where you need to implement undo/redo functionality, as it enables an object to return to a previous state without exposing the details of its internal structure.

#### Key Concepts of the Memento Pattern

1. **Originator**: The object whose state needs to be saved and restored. It creates a memento containing a snapshot of its state and can restore itself to a state stored in a memento.
2. **Memento**: The memento object represents a snapshot of the `Originator`'s state. It’s typically an immutable object that only the `Originator` can modify.
3. **Caretaker**: Manages the memento's lifecycle by requesting new mementos and storing them. It does not modify or access the contents of the memento directly, maintaining encapsulation.

#### Example: Text Editor with Undo Feature

In this example, we’ll create a simple text editor where the `Editor` class (Originator) can save and restore its state through a memento object.

**Step-by-Step Code**

1.  **Define the Memento Class**: This class will store the state of the `Editor` at a given point.

    ```python
    class Memento:
        def __init__(self, state):
            self._state = state

        def get_state(self):
            return self._state
    ```
2.  **Implement the Originator Class**: The `Editor` class is our originator. It can create and restore mementos of its state.

    ```python
    class Editor:
        def __init__(self):
            self._content = ""

        def write(self, text):
            self._content += text

        def save(self):
            return Memento(self._content)

        def restore(self, memento):
            self._content = memento.get_state()

        def get_content(self):
            return self._content
    ```
3.  **Implement the Caretaker**: The `History` class acts as the caretaker. It stores the `Editor`’s mementos, enabling undo functionality.

    ```python
    class History:
        def __init__(self):
            self._mementos = []

        def save(self, memento):
            self._mementos.append(memento)

        def undo(self):
            if not self._mementos:
                return None
            return self._mementos.pop()
    ```
4.  **Client Code to Use the Memento Pattern**: This code demonstrates saving and restoring the editor’s state.

    ```python
    if __name__ == "__main__":
        editor = Editor()
        history = History()

        # Writing some text and saving state
        editor.write("Hello, ")
        history.save(editor.save())  # Save the state

        editor.write("world!")
        history.save(editor.save())  # Save another state

        editor.write(" This is a test.")
        print("Current content:", editor.get_content())  # Output: "Hello, world! This is a test."

        # Undo to previous state
        editor.restore(history.undo())
        print("After undo:", editor.get_content())  # Output: "Hello, world!"

        # Undo again to the initial state
        editor.restore(history.undo())
        print("After second undo:", editor.get_content())  # Output: "Hello, "
    ```

#### Explanation of the Code

* **Memento**: Represents the `Editor`'s state at a specific moment, holding a copy of the text content.
* **Editor (Originator)**: Manages its own state (`_content`) and provides methods to save its state to a memento or restore its state from a memento.
* **History (Caretaker)**: Stores the history of `Editor`'s mementos, allowing the editor to revert to previous states as needed. It doesn’t access the content of mementos directly.

#### When to Use the Memento Pattern

* When you need to implement undo/redo functionality.
* When you want to take snapshots of an object’s state at various points without exposing or breaking its internal structure.
* When the internal state of an object changes frequently, and you need to retain previous states for restoration.

#### Pros and Cons

**Pros**:

* Provides an easy way to implement undo/redo functionality.
* Protects the encapsulation of an object’s internal state, as only the originator can modify its own mementos.

**Cons**:

* Can use a lot of memory if the originator's state is large or if many mementos are stored.
* Can increase complexity, as the caretaker must manage mementos' lifecycle.

This example demonstrates how the Memento pattern allows an `Editor` object to restore its state through a simple, encapsulated mechanism. The pattern provides a way to manage the state without exposing it, enabling features like undo/redo without compromising data integrity or encapsulation.

### <mark style="color:red;">**Observer Pattern**</mark>

* defines a subscription mechanism for notifying multiple objects about any events that happen to a particular object, the "subject."&#x20;
* This pattern is commonly used to implement distributed event-handling systems, allowing objects to stay in sync with changes in another object without being tightly coupled to it.

#### Key Concepts of the Observer Pattern

1. **Subject (Observable)**: The object being observed. It maintains a list of observers, notifies them of state changes, and allows them to subscribe or unsubscribe.
2. **Observer**: An interface or abstract class for objects that want to be notified about changes in the subject. Observers implement an `update` method to receive updates.
3. **Concrete Observers**: These are the classes that implement the `Observer` interface and define how they respond to updates from the subject.
4. **Client**: Sets up the relationship between the subject and observers.

#### Example: Weather Station

In this example, we’ll create a simple weather station where different displays (observers) update their data based on changes in temperature, humidity, etc., from a weather station (subject).

**Step-by-Step Code**

1.  **Define the Observer Interface**: This interface declares the `update` method for observers.

    ```python
    from abc import ABC, abstractmethod

    class Observer(ABC):
        @abstractmethod
        def update(self, temperature, humidity, pressure):
            pass
    ```
2.  **Define the Subject (Observable) Interface**: This interface declares methods for adding, removing, and notifying observers.

    ```python
    class Subject(ABC):
        @abstractmethod
        def add_observer(self, observer):
            pass

        @abstractmethod
        def remove_observer(self, observer):
            pass

        @abstractmethod
        def notify_observers(self):
            pass
    ```
3.  **Implement the Concrete Subject**: The `WeatherStation` class stores weather data and notifies observers when data changes.

    ```python
    class WeatherStation(Subject):
        def __init__(self):
            self._observers = []
            self._temperature = None
            self._humidity = None
            self._pressure = None

        def add_observer(self, observer):
            self._observers.append(observer)

        def remove_observer(self, observer):
            self._observers.remove(observer)

        def set_measurements(self, temperature, humidity, pressure):
            self._temperature = temperature
            self._humidity = humidity
            self._pressure = pressure
            self.notify_observers()

        def notify_observers(self):
            for observer in self._observers:
                observer.update(self._temperature, self._humidity, self._pressure)
    ```
4.  **Implement Concrete Observers**: Define specific observer classes, such as a display for current conditions and another for forecasting.

    ```python
    class CurrentConditionsDisplay(Observer):
        def update(self, temperature, humidity, pressure):
            print(f"Current conditions: {temperature}°C, {humidity}% humidity, {pressure} hPa pressure")

    class ForecastDisplay(Observer):
        def update(self, temperature, humidity, pressure):
            if pressure < 1000:
                forecast = "Rain likely"
            else:
                forecast = "Clear skies"
            print(f"Forecast: {forecast}")
    ```
5.  **Client Code to Use the Observer Pattern**: Now we can create the `WeatherStation` and attach observers to it.

    ```python
    if __name__ == "__main__":
        # Create the subject
        weather_station = WeatherStation()

        # Create observers
        current_display = CurrentConditionsDisplay()
        forecast_display = ForecastDisplay()

        # Register observers with the subject
        weather_station.add_observer(current_display)
        weather_station.add_observer(forecast_display)

        # Update weather data and notify observers
        weather_station.set_measurements(25, 65, 1013)
        weather_station.set_measurements(22, 70, 998)
    ```

    Output:

    ```
    Current conditions: 25°C, 65% humidity, 1013 hPa pressure
    Forecast: Clear skies
    Current conditions: 22°C, 70% humidity, 998 hPa pressure
    Forecast: Rain likely
    ```

#### Explanation of the Code

* **WeatherStation (Subject)**: Maintains a list of observers and notifies them when weather data changes. The `set_measurements` method updates weather data and then calls `notify_observers` to push the update to each observer.
* **CurrentConditionsDisplay and ForecastDisplay (Observers)**: These classes implement the `Observer` interface and define how they respond to weather data updates.
* **Client Code**: Sets up the observers and attaches them to the `WeatherStation`. When weather data is updated, both displays receive and handle the update.

#### When to Use the Observer Pattern

* When an object needs to notify multiple other objects about changes in its state.
* When you want to reduce the coupling between the subject and its observers.
* When changes in one object’s state should automatically update other objects, such as in MVC architectures or event-handling systems.

#### Pros and Cons

**Pros**:

* Promotes loose coupling between subject and observers.
* Allows easy addition of new observers without changing the subject’s code.
* Supports broadcasting of state changes to multiple observers simultaneously.

**Cons**:

* Can lead to unexpected updates, especially if the subject frequently changes.
* Observer management (e.g., adding and removing observers) can become complex if many observers are involved.
* Can cause performance issues if there are a large number of observers and frequent updates.

This example demonstrates how the Observer pattern allows a weather station to notify multiple displays of changes in weather data, achieving a loosely coupled relationship between the weather station and its displays.

### <mark style="color:red;">**State Pattern**</mark>&#x20;

* allows an object to change its behavior when its internal state changes.&#x20;
* This pattern is especially useful when an object has different states that influence how it should respond to certain events.&#x20;
* It’s a way to simplify complex conditional logic, promoting cleaner and more maintainable code.

#### Key Concepts of the State Pattern

1. **State Interface**: Declares the methods that concrete states must implement. This ensures a uniform interface for all states.
2. **Concrete States**: Implement the specific behavior associated with each state of the context.
3. **Context**: The main object whose behavior changes based on its internal state. It has a reference to the current state and delegates state-specific behavior to it.
4. **Client**: Interacts with the context, triggering state transitions indirectly.

#### Example: Audio Player with Different States (Playing, Paused, Stopped)

In this example, we’ll create a simple audio player that can be in one of three states: _Playing_, _Paused_, or _Stopped_. Each state will have its own behavior for actions like play, pause, and stop.

**Step-by-Step Code**

1.  **Define the State Interface**: This interface declares the methods that each state should implement.

    ```python
    from abc import ABC, abstractmethod

    class State(ABC):
        @abstractmethod
        def play(self, player):
            pass

        @abstractmethod
        def pause(self, player):
            pass

        @abstractmethod
        def stop(self, player):
            pass
    ```
2.  **Implement Concrete States**: Define the behavior for each state (`PlayingState`, `PausedState`, `StoppedState`).

    ```python
    class PlayingState(State):
        def play(self, player):
            print("Already playing.")

        def pause(self, player):
            print("Pausing the player.")
            player.set_state(PausedState())

        def stop(self, player):
            print("Stopping the player.")
            player.set_state(StoppedState())

    class PausedState(State):
        def play(self, player):
            print("Resuming playback.")
            player.set_state(PlayingState())

        def pause(self, player):
            print("Already paused.")

        def stop(self, player):
            print("Stopping the player from pause.")
            player.set_state(StoppedState())

    class StoppedState(State):
        def play(self, player):
            print("Starting playback.")
            player.set_state(PlayingState())

        def pause(self, player):
            print("Cannot pause. The player is stopped.")

        def stop(self, player):
            print("Already stopped.")
    ```
3.  **Implement the Context Class**: The `AudioPlayer` class will maintain a reference to the current state and delegate actions to it.

    ```python
    class AudioPlayer:
        def __init__(self):
            self._state = StoppedState()  # Start in the stopped state

        def set_state(self, state):
            self._state = state

        def play(self):
            self._state.play(self)

        def pause(self):
            self._state.pause(self)

        def stop(self):
            self._state.stop(self)
    ```
4.  **Client Code to Use the State Pattern**: Now we can create the `AudioPlayer` and perform state-specific actions.

    ```python
    if __name__ == "__main__":
        player = AudioPlayer()

        player.play()    # Output: Starting playback.
        player.pause()   # Output: Pausing the player.
        player.play()    # Output: Resuming playback.
        player.stop()    # Output: Stopping the player.
        player.pause()   # Output: Cannot pause. The player is stopped.
    ```

    Output:

    ```
    Starting playback.
    Pausing the player.
    Resuming playback.
    Stopping the player.
    Cannot pause. The player is stopped.
    ```

#### Explanation of the Code

* **PlayingState, PausedState, StoppedState (Concrete States)**: Implement the specific behaviors for each state, defining how the player should respond to `play`, `pause`, and `stop` actions.
* **AudioPlayer (Context)**: Maintains a reference to the current state and delegates actions to the state. It changes its state based on the current behavior.
* **Client Code**: Creates an instance of `AudioPlayer` and interacts with it, demonstrating the state transitions.

#### When to Use the State Pattern

* When an object’s behavior changes based on its state.
* When you want to simplify complex conditional logic based on an object’s state.
* When you want to make the object more flexible by allowing behavior changes dynamically.

#### Pros and Cons

**Pros**:

* Simplifies complex conditional logic, replacing it with state-specific classes.
* Promotes the Open/Closed Principle by allowing new states to be added without modifying existing code.
* Improves code readability and maintainability by separating state-specific behavior.

**Cons**:

* Increases the number of classes, as each state requires its own class.
* Can add complexity to simple systems where only a few states are needed.

This example demonstrates how the State pattern allows an `AudioPlayer` to handle actions differently depending on its current state, providing a cleaner and more maintainable approach to managing state-dependent behavior.

### <mark style="color:red;">**Strategy Pattern**</mark>

* allows you to define a family of algorithms or behaviors, encapsulate each one as a separate class, and make them interchangeable.&#x20;
* This pattern enables an object to choose the algorithm it uses at runtime, promoting flexibility and avoiding conditional logic.&#x20;
* By encapsulating different behaviors into different classes, the Strategy Pattern provides a way to select the behavior of an object dynamically.

#### Key Concepts of the Strategy Pattern

1. **Strategy Interface**: Defines a common interface for all algorithms. Each strategy implements this interface to ensure they’re interchangeable.
2. **Concrete Strategies**: Implement the algorithm defined in the Strategy Interface. Each concrete strategy represents a different way to perform a task.
3. **Context**: The main object that needs to perform some task but delegates the actual implementation to one of the strategies. The context maintains a reference to a strategy object, and clients can set or change the strategy dynamically.

#### Example: Payment System with Different Payment Methods

In this example, we’ll create a payment system where a user can pay using different payment methods, such as a credit card or PayPal. Each payment method represents a different strategy.

**Step-by-Step Code**

1.  **Define the Strategy Interface**: This interface declares the `pay` method that all payment strategies must implement.

    ```python
    from abc import ABC, abstractmethod

    class PaymentStrategy(ABC):
        @abstractmethod
        def pay(self, amount):
            pass
    ```
2.  **Implement Concrete Strategies**: Each payment method (e.g., CreditCardPayment, PayPalPayment) implements the `pay` method defined in `PaymentStrategy`.

    ```python
    class CreditCardPayment(PaymentStrategy):
        def __init__(self, name, card_number, cvv, expiry_date):
            self.name = name
            self.card_number = card_number
            self.cvv = cvv
            self.expiry_date = expiry_date

        def pay(self, amount):
            print(f"Paying ${amount} using Credit Card for {self.name}")

    class PayPalPayment(PaymentStrategy):
        def __init__(self, email):
            self.email = email

        def pay(self, amount):
            print(f"Paying ${amount} using PayPal account {self.email}")
    ```
3.  **Implement the Context Class**: The `ShoppingCart` class (context) will use one of the payment strategies to process a payment.

    ```python
    class ShoppingCart:
        def __init__(self):
            self._items = []
            self._total_amount = 0

        def add_item(self, item, price):
            self._items.append((item, price))
            self._total_amount += price

        def set_payment_strategy(self, strategy):
            self._payment_strategy = strategy

        def checkout(self):
            if hasattr(self, '_payment_strategy'):
                self._payment_strategy.pay(self._total_amount)
            else:
                print("Payment method not selected.")
    ```
4.  **Client Code to Use the Strategy Pattern**: We can now create different payment strategies and use them with the shopping cart.

    ```python
    if __name__ == "__main__":
        # Create a shopping cart and add items
        cart = ShoppingCart()
        cart.add_item("Book", 10)
        cart.add_item("Pen", 2)
        cart.add_item("Notebook", 5)

        # Choose payment method
        credit_card = CreditCardPayment("John Doe", "1234567890123456", "123", "12/23")
        paypal = PayPalPayment("johndoe@example.com")

        # Set payment strategy to credit card and checkout
        cart.set_payment_strategy(credit_card)
        cart.checkout()  # Output: Paying $17 using Credit Card for John Doe

        # Set payment strategy to PayPal and checkout
        cart.set_payment_strategy(paypal)
        cart.checkout()  # Output: Paying $17 using PayPal account johndoe@example.com
    ```

    Output:

    ```
    Paying $17 using Credit Card for John Doe
    Paying $17 using PayPal account johndoe@example.com
    ```

#### Explanation of the Code

* **PaymentStrategy (Strategy Interface)**: Defines the `pay` method that all payment strategies must implement.
* **CreditCardPayment and PayPalPayment (Concrete Strategies)**: Each class represents a different payment method, implementing the `pay` method in its own way.
* **ShoppingCart (Context)**: Manages the items and total cost, and uses a payment strategy to process the payment.
* **Client Code**: Creates different payment strategies and sets the strategy in the `ShoppingCart` context before calling `checkout`.

#### When to Use the Strategy Pattern

* When you have multiple ways to perform a task, and you want to make the method easily interchangeable.
* When you want to avoid complex conditional statements to select different behaviors.
* When the behavior varies based on specific criteria or situations.

#### Pros and Cons

**Pros**:

* Promotes Open/Closed Principle by allowing new strategies to be added without changing the context.
* Provides flexibility to switch between different algorithms or behaviors at runtime.
* Reduces conditional logic in the context by delegating tasks to strategy classes.

**Cons**:

* Can lead to an increase in the number of classes, as each algorithm requires its own class.
* Adds complexity if there are only a few behaviors, as using separate classes might seem unnecessary.
* The context must be aware of the various strategies, and clients must know which strategy to use.

This example demonstrates how the Strategy pattern allows a `ShoppingCart` to handle payments in multiple ways by switching between different payment strategies dynamically, providing a flexible and maintainable solution for handling different payment methods.

### <mark style="color:red;">**Template Pattern**</mark>&#x20;

* defines the skeleton of an algorithm in a method, called a **template method**, which defers some steps to subclasses.&#x20;
* By doing so, it allows subclasses to redefine certain steps of the algorithm without changing its structure.&#x20;
* This pattern is particularly useful when you have a general algorithm that needs to be shared across multiple classes, but you want to allow subclasses to implement specific parts of the algorithm differently.

#### Key Concepts of the Template Pattern

1. **Template Method**: The template method defines the skeleton of the algorithm. It calls other methods that can either be implemented by the subclass or already provided in the parent class.
2. **Abstract Class**: The abstract class provides a template method that outlines the workflow and contains methods that can be either concrete or abstract.
3. **Concrete Methods**: These are methods implemented in the abstract class that are common for all subclasses.
4. **Abstract Methods**: These are methods that must be implemented by the subclasses to provide custom functionality. These methods represent the parts of the algorithm that may vary.
5. **Hooks**: Optional methods that can be overridden by subclasses. They may have a default implementation in the abstract class, or be empty methods, allowing subclasses to customize or extend behavior when needed.

#### Example: Cooking Recipe

Let’s consider a cooking recipe as an example. All recipes follow a basic structure: gather ingredients, cook the ingredients, and serve. However, the cooking method varies depending on the recipe. This is a classic example where the Template Pattern can be applied.

**Step-by-Step Code**

1.  **Define the Abstract Class with the Template Method**: The abstract class `Recipe` defines the template method, `prepare_recipe`, which outlines the steps of the cooking process. The `prepare_recipe` method calls other methods, some of which are concrete (like `gather_ingredients` and `serve`), and some are abstract (like `cook`).

    ```python
    from abc import ABC, abstractmethod

    class Recipe(ABC):
        def prepare_recipe(self):
            self.gather_ingredients()
            self.cook()
            self.serve()

        def gather_ingredients(self):
            print("Gathering ingredients.")

        @abstractmethod
        def cook(self):
            pass

        def serve(self):
            print("Serving the dish.")
    ```
2.  **Implement Concrete Subclasses**: Define subclasses for specific recipes, such as `PastaRecipe` and `SoupRecipe`. Each subclass provides its own implementation for the `cook` method to describe how it cooks the ingredients.

    ```python
    class PastaRecipe(Recipe):
        def cook(self):
            print("Boiling pasta and adding sauce.")

    class SoupRecipe(Recipe):
        def cook(self):
            print("Boiling vegetables and adding seasoning.")
    ```
3.  **Client Code to Use the Template Pattern**: Now we can create instances of `PastaRecipe` and `SoupRecipe` and use the `prepare_recipe` method to prepare the dishes.

    ```python
    if __name__ == "__main__":
        pasta = PastaRecipe()
        soup = SoupRecipe()

        print("Preparing Pasta Recipe:")
        pasta.prepare_recipe()

        print("\nPreparing Soup Recipe:")
        soup.prepare_recipe()
    ```

    Output:

    ```
    Preparing Pasta Recipe:
    Gathering ingredients.
    Boiling pasta and adding sauce.
    Serving the dish.

    Preparing Soup Recipe:
    Gathering ingredients.
    Boiling vegetables and adding seasoning.
    Serving the dish.
    ```

#### Explanation of the Code

* **Recipe (Abstract Class)**: The `prepare_recipe` method defines the template for the cooking process, ensuring that all recipes follow the same steps. It calls `gather_ingredients`, `cook`, and `serve` in order. The `cook` method is abstract, meaning that subclasses must implement it.
  * `gather_ingredients` and `serve` are concrete methods that are common to all recipes.
  * `cook` is an abstract method, as cooking differs between recipes.
* **PastaRecipe and SoupRecipe (Concrete Subclasses)**: These classes provide specific implementations for the `cook` method. Each recipe has its own way of cooking, but the steps for gathering ingredients and serving remain the same.
* **Client Code**: The client creates instances of the `PastaRecipe` and `SoupRecipe` classes and calls `prepare_recipe`, which executes the template method and follows the steps defined in the base class.

#### When to Use the Template Pattern

* When you have multiple classes that share a common algorithm but differ in certain steps.
* When you want to define the skeleton of an algorithm in a base class, but allow subclasses to provide specific implementations of some steps.
* When you want to promote code reuse while allowing flexibility in certain parts of the algorithm.

#### Pros and Cons

**Pros**:

* **Code Reusability**: Common parts of the algorithm are implemented once in the base class, avoiding code duplication.
* **Flexibility**: Subclasses can implement their own behavior while reusing common behavior.
* **Enforces Consistency**: The template method ensures that the algorithm is followed in the same order, reducing errors.

**Cons**:

* **Increased Coupling**: Subclasses are tightly coupled to the template method, meaning that changes to the template method can affect all subclasses.
* **Complexity**: The pattern can lead to a large number of classes if the algorithm has many variations.
* **Difficulty in Changing the Algorithm's Structure**: Once the structure of the algorithm is defined in the template method, it can be difficult to change without affecting all subclasses.

#### Example Use Cases for the Template Pattern

* **Workflow management**: In systems where multiple workflows share a common structure but differ in specific tasks (e.g., processing different types of documents).
* **Game development**: For game engines that define a general flow (initialize, update, render) but allow specific games to define unique update and rendering logic.
* **Data processing pipelines**: Where multiple types of data processing follow a similar structure, but each data type requires a different processing strategy.

This example demonstrates how the Template Pattern allows a base class to define the overall structure of a process, while subclasses can provide specific implementations for the variable parts of that process. It ensures consistency while allowing flexibility for customization.



### <mark style="color:red;">**Visitor Pattern**</mark>&#x20;

* &#x20;allows you to separate an algorithm from the objects it operates on.&#x20;
* It lets you add further operations to objects without modifying their classes. Essentially, the Visitor Pattern enables you to define a new operation (the "visitor") without changing the classes of the elements on which it operates.
* The pattern is typically used when you need to perform different operations on a set of objects with different types, but you want to avoid using multiple `if-else` or `switch` statements to handle each case.
* Key Concepts of the Visitor Pattern
  * **Visitor Interface**: The visitor interface defines a `visit` method for each concrete element class. This method allows the visitor to perform an operation on the element.
  * **Concrete Visitor**: The concrete visitor implements the visitor interface and provides the specific behavior for each element type.
  * **Element Interface**: The element interface defines an `accept` method that allows the visitor to "visit" the element. Each concrete element class implements this interface.
  * **Concrete Element**: The concrete element implements the `accept` method, which calls the appropriate `visit` method on the visitor.
  * **Object Structure**: The object structure contains a collection of elements and provides a way to traverse them.
* Example: Shape Drawing System
  * Let’s implement a drawing system where we have different shapes (e.g., Circle, Rectangle), and we want to perform various operations (e.g., calculating the area or printing the shape). Instead of modifying the shape classes, we will use the Visitor Pattern to add new operations to these shapes.
  * **Step-by-Step Code**

**Define the Visitor Interface**: The visitor interface will define a `visit` method for each type of shape.

```python
from abc import ABC, abstractmethod

class ShapeVisitor(ABC):
    @abstractmethod
    def visit_circle(self, circle):
        pass

    @abstractmethod
    def visit_rectangle(self, rectangle):
        pass
```

1.  **Define the Element Interface**: The element interface will have an `accept` method that accepts a visitor.

    ```python
    class Shape(ABC):
        @abstractmethod
        def accept(self, visitor: ShapeVisitor):
            pass
    ```
2.  **Define Concrete Element Classes**: These are the concrete shape classes that implement the `Shape` interface and the `accept` method.

    ```python
    class Circle(Shape):
        def __init__(self, radius):
            self.radius = radius

        def accept(self, visitor: ShapeVisitor):
            visitor.visit_circle(self)

    class Rectangle(Shape):
        def __init__(self, width, height):
            self.width = width
            self.height = height

        def accept(self, visitor: ShapeVisitor):
            visitor.visit_rectangle(self)
    ```
3.  **Define Concrete Visitors**: These classes define specific operations to be performed on the shapes. For example, one visitor calculates the area, and another prints the shape’s details.

    ```python
    class AreaCalculator(ShapeVisitor):
        def visit_circle(self, circle):
            area = 3.14 * circle.radius ** 2
            print(f"Area of Circle: {area}")

        def visit_rectangle(self, rectangle):
            area = rectangle.width * rectangle.height
            print(f"Area of Rectangle: {area}")

    class ShapePrinter(ShapeVisitor):
        def visit_circle(self, circle):
            print(f"Circle with radius: {circle.radius}")

        def visit_rectangle(self, rectangle):
            print(f"Rectangle with width: {rectangle.width} and height: {rectangle.height}")
    ```
4.  **Client Code to Use the Visitor Pattern**: We can now create a collection of shapes, apply the visitors, and print or calculate areas.

    ```python
    if __name__ == "__main__":
        # Create some shapes
        circle = Circle(5)
        rectangle = Rectangle(4, 6)

        # Create visitors
        area_calculator = AreaCalculator()
        shape_printer = ShapePrinter()

        # Use visitors to perform operations
        print("Calculating Areas:")
        circle.accept(area_calculator)
        rectangle.accept(area_calculator)

        print("\nPrinting Shapes:")
        circle.accept(shape_printer)
        rectangle.accept(shape_printer)
    ```

    Output:

    ```
    Calculating Areas:
    Area of Circle: 78.5
    Area of Rectangle: 24

    Printing Shapes:
    Circle with radius: 5
    Rectangle with width: 4 and height: 6
    ```

#### Explanation of the Code

* **ShapeVisitor (Visitor Interface)**: Defines `visit_circle` and `visit_rectangle` methods for visiting different shape types. Concrete visitors will implement these methods with the specific logic.
* **Shape (Element Interface)**: This interface requires concrete shape classes (`Circle`, `Rectangle`) to implement the `accept` method, which will accept a visitor.
* **Circle and Rectangle (Concrete Element Classes)**: These classes represent different shapes and implement the `accept` method. The `accept` method allows the object to "accept" a visitor and pass itself to the visitor's corresponding `visit_*` method.
* **AreaCalculator and ShapePrinter (Concrete Visitors)**: These visitors implement the logic for performing specific operations (calculating areas or printing shape details). The `visit_*` methods in the visitors handle the operation for each concrete element type.
* **Client Code**: Creates instances of shapes and visitors, then uses the `accept` method to apply the visitors' operations to the shapes.

#### When to Use the Visitor Pattern

* **When you need to perform operations on objects of different classes**: If you have a set of different types of objects and want to perform some operation on all of them without modifying the objects themselves, the Visitor Pattern is ideal.
* **When new operations need to be added to an existing object structure**: If you need to add new operations (such as calculating a new property) to an existing set of objects, the Visitor Pattern allows you to do so without modifying the classes themselves.
* **When you want to avoid adding a lot of conditional logic**: The Visitor Pattern allows you to remove complex `if-else` or `switch` statements in favor of polymorphic method dispatch.

#### Pros and Cons

**Pros**:

* **Open for Extension, Closed for Modification**: The pattern follows the open/closed principle. New operations can be added without modifying the existing classes (elements).
* **Separation of Concerns**: The operations are separated from the elements, making it easier to maintain and extend the system.
* **Flexibility**: Allows adding new operations to a structure of objects without modifying the objects themselves.

**Cons**:

* **Increased Complexity**: The pattern introduces additional classes (visitors), which may increase complexity if there are many types of elements or visitors.
* **Difficult to add new element types**: If you want to add a new type of element, you need to modify all visitor classes to handle the new element type, violating the open/closed principle.
* **Tight Coupling between Visitors and Elements**: Visitors are tightly coupled to the element classes. Any change in the element class can require changes in the visitor class.

#### Example Use Cases for the Visitor Pattern

* **Compilers/Interpreters**: Where different operations need to be performed on various nodes of an abstract syntax tree (e.g., evaluating an expression, printing, optimizing).
* **GUI Frameworks**: When you need to apply different operations to various types of components (e.g., buttons, text fields).
* **Document Processing**: When different operations are required for processing different parts of a document (e.g., images, paragraphs, tables).

In this example, the Visitor Pattern allows us to keep the shape classes (`Circle`, `Rectangle`) simple, while allowing us to add new operations (such as area calculation or shape printing) without modifying the shape classes.

## **Design for Testability Patterns**:

|                                                    Pattern                                                    | Description                        |
| :-----------------------------------------------------------------------------------------------------------: | ---------------------------------- |
| [dependency\_injection](https://github.com/faif/python-patterns/blob/master/patterns/dependency_injection.py) | 3 variants of dependency injection |

## **Fundamental Patterns**:

|                                                        Pattern                                                        | Description                                                                 |
| :-------------------------------------------------------------------------------------------------------------------: | --------------------------------------------------------------------------- |
| [delegation\_pattern](https://github.com/faif/python-patterns/blob/master/patterns/fundamental/delegation_pattern.py) | an object handles a request by delegating to a second object (the delegate) |

## **Others**

|                                               Pattern                                               | Description                                                                                                              |
| :-------------------------------------------------------------------------------------------------: | ------------------------------------------------------------------------------------------------------------------------ |
|    [blackboard](https://github.com/faif/python-patterns/blob/master/patterns/other/blackboard.py)   | architectural model, assemble different sub-system knowledge to build a solution, AI approach - non gang of four pattern |
| [graph\_search](https://github.com/faif/python-patterns/blob/master/patterns/other/graph_search.py) | graphing algorithms - non gang of four pattern                                                                           |
|         [hsm](https://github.com/faif/python-patterns/blob/master/patterns/other/hsm/hsm.py)        | hierarchical state machine - non gang of four pattern                                                                    |

