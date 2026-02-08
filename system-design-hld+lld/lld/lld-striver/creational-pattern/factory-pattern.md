# Factory Pattern

* provides an interface for creating objects but allows subclasses to alter the type of objects that will be created.
* **In simpler terms**:\
  Rather than calling a constructor directly to create an object, we use a factory method to create that object based on some input or condition.
*   **When Should You Use It?**

    We can use the Factory Pattern when:

    * The client code needs to work with multiple types of objects.
    * The decision of which class to instantiate must be made at runtime.
    * The instantiation process is complex or needs to be controlled.
* Ex: Ordering Pizza&#x20;
  * Imagine you walk into a pizza shop and say, “I'd like a pizza.” The shop doesn't ask you to go into the kitchen and make it yourself.&#x20;
  * Instead, it asks, “Which type? Margherita? Pepperoni? Veggie?” Based on your choice, the kitchen (factory) creates the specific pizza for you and hands it over.
  * This is exactly what the Factory Pattern does in code: it creates an object based on some input without exposing the instantiation logic to the client.
* Basic Structure
  * The Factory Pattern typically consists of the following components:
    * **Product:** It is an _interface_ or _abstract class_ that defines the methods the product must implement.
    * **Concrete Products:** The _concrete classes_ that implement the Product interface.
    * **Factory:** A class with a method that returns different concrete products based on input.

```python
# Interface 
from abc import ABC, abstractmethod

class Shape(ABC):
    @abstractmethod
    def draw(self):
        pass

# Class implementing the Shape Interface
class Circle(Shape):
    def draw(self):
        print("Drawing Circle")

# Class implementing the Shape Interface
class Square(Shape):
    def draw(self):
        print("Drawing Square")

# Factory Class
class ShapeFactory:
    # Method that takes the type of shape as input 
    # and returns the corresponding object
    def get_shape(self, shape_type):
        if shape_type.lower() == "circle":
            return Circle()
        elif shape_type.lower() == "square":
            return Square()
        return None

# Driver code
if __name__ == "__main__":
    # Object of ShapeFactory is initialized
    shape_factory = ShapeFactory()

    # Get a Circle object and call its draw method
    shape1 = shape_factory.get_shape("CIRCLE")
    if shape1: shape1.draw()

    # Get a Square object and call its draw method
    shape2 = shape_factory.get_shape("SQUARE")
    if shape2: shape2.draw()

```



* Real life product example : ligistic service&#x20;
  * We are buiding a service which handles different types of transport service, by road, by air etc.

Bad practice: by not follwoing factory&#x20;

```
# Logistics Interface 
from abc import ABC, abstractmethod

class Logistics(ABC):
    @abstractmethod
    def send(self):
        pass

# Class implementing the Logistics Interface
class Road(Logistics):
    def send(self):
        print("Sending by road logic")

# Class implementing the Logistics Interface
class Air(Logistics):
    def send(self):
        print("Sending by air logic")

# Class implementing Logistics Service
class LogisticsService:
    def send(self, mode):
        if mode.lower() == "air":
            logistics = Air()
            logistics.send()
        elif mode.lower() == "road":
            logistics = Road()
            logistics.send()

# Driver code
if __name__ == "__main__":
    service = LogisticsService()
    service.send("Air")
    service.send("Road")

```

*   **Understanding the Issue:**

    In the LogisticsService class:

    * The object of `Air` or `Road` is directly instantiated **based on string comparison**.
    * The **object creation logic is embedded** inside the business logic (`send` method).
    * This violates the **Open/Closed Principle** — if you want to add a new mode (e.g., `Ship`), you have to **modify** the `send` method.

    **Problems:**

    * **Tight Coupling:** LogisticsService depends directly on Air and Road classes.
    * **Hard to Extend:** Adding a new mode (e.g., Drone, Ship) requires modifying existing code.
    * **No Separation of Concerns:** Object creation and business logic are mixed.
    * **Code Duplication:** Repeated instantiation and send() logic.
    * **Testing & Maintenance Nightmare:** Hard to test independently or mock logistics.

    <br>

Good practice&#x20;

```
# Logistics Interface 
from abc import ABC, abstractmethod

class Logistics(ABC):
    @abstractmethod
    def send(self):
        pass

# Class implementing the Logistics Interface
class Road(Logistics):
    def send(self):
        print("Sending by road logic")

# Class implementing the Logistics Interface
class Air(Logistics):
    def send(self):
        print("Sending by air logic")

# Factory Class taking care of Logistics
class LogisticsFactory:
    @staticmethod
    def get_logistics(mode):
        if mode.lower() == "air":
            return Air()
        elif mode.lower() == "road":
            return Road()
        else:
            raise ValueError(f"Unknown logistics mode: {mode}")

# Class implementing the Logistics Services
class LogisticsService:
    def send(self, mode):
        # Using the Logistics Factory to get the desired object based on the mode
        logistics = LogisticsFactory.get_logistics(mode)
        logistics.send()

# Driver Code
if __name__ == "__main__":
    service = LogisticsService()
    service.send("Air")
    service.send("Road")

```

*   **Understanding the Improvement:**

    In this refactored code:

    * The object creation logic is moved to the `LogisticsFactory`.
    * The `LogisticsService` class now only focuses on business logic.
    * Adding a new mode (e.g., Ship) only requires modifying the factory, not the service.

    **Benefits:**

    * **Loose Coupling:** The service is decoupled from specific logistics classes.
    * **Open/Closed Principle:** New modes can be added without modifying existing code.
    * **Separation of Concerns:** Object creation and business logic are separated.
    * **No Code Duplication:** Instantiation logic is centralized in the factory.
    * **Easier Testing & Maintenance:** Each component can be tested independently.
*   #### Pros of Factory Pattern

    * **Promotes Loose Coupling:**
      * The client code is decoupled from the actual instantiation of classes.
      * You work with interfaces rather than concrete classes.
    * **Enhances Extensibility (OCP - Open/Closed Principle):**
      * You can introduce new classes (e.g., new types of logistics like `Ship`) without modifying existing client code.
      * The system becomes easier to scale and extend.
    * **Centralizes Object Creation (SRP - Single Responsibility Principle):**
      * The responsibility of object creation is moved to a dedicated factory class.
      * Business logic stays clean and focused only on "what to do" with the object.
    * **Increases Flexibility:**
      * The decision of "which object to create" can be deferred to runtime based on input, config, or logic.
      * Makes your system adaptable to dynamic requirements.
    * **Improves Code Reusability:**
      * Common instantiation logic can be reused from a single factory.
      * Avoids code duplication when creating similar objects in different parts of the system.

    #### Cons of Factory Pattern

    * **Increased Complexity:** Introduces additional layers (factory classes/interfaces) which might be overkill for very small programs.
    * **More Code Overhead:** Requires writing extra code like factory classes and interfaces, which might look unnecessary in simpler use-cases.

    <br>

    <figure><img src="../../../../.gitbook/assets/image (15).png" alt=""><figcaption></figcaption></figure>
