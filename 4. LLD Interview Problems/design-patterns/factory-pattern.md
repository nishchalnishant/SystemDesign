# Factory Patterns

> **Category**: Creational Pattern  
> **Purpose**: Create objects without specifying the exact class of object that will be created

## 1. Simple Factory (Static Factory)

Not a "real" GoF pattern, but commonly used. A static method creates objects based on input.

### Example: Vehicle Factory

```java
public class VehicleFactory {
    public static Vehicle createVehicle(String type) {
        if (type.equalsIgnoreCase("car")) {
            return new Car();
        } else if (type.equalsIgnoreCase("bike")) {
            return new Bike();
        } else if (type.equalsIgnoreCase("truck")) {
            return new Truck();
        } else {
            throw new IllegalArgumentException("Unknown vehicle type");
        }
    }
}

// Usage
Vehicle car = VehicleFactory.createVehicle("car");
```

**Pros:**  
✅ Simple to implement  
✅ Client decoupled from concrete classes  

**Cons:**  
❌ Violates Open/Closed Principle (must modify factory to add new type)

---

## 2. Factory Method Pattern

Define an interface for creating an object, but let subclasses decide which class to instantiate.

### Structure

```
Creator (abstract) -> declares factoryMethod()
   Example: Logistics (planDelivery, createTransport)

ConcreteCreator -> implements factoryMethod()
   Example: RoadLogistics (returns Truck), SeaLogistics (returns Ship)
```

### Example: Logistics System

```java
// Product
interface Transport {
    void deliver();
}

class Truck implements Transport {
    public void deliver() { System.out.println("Deliver by land in a box"); }
}

class Ship implements Transport {
    public void deliver() { System.out.println("Deliver by sea in a container"); }
}

// Creator
abstract class Logistics {
    // Core business logic uses the product
    public void planDelivery() {
        Transport t = createTransport();
        t.deliver();
    }
    
    // Abstract factory method
    protected abstract Transport createTransport();
}

// Concrete Creators
class RoadLogistics extends Logistics {
    @Override
    protected Transport createTransport() {
        return new Truck();
    }
}

class SeaLogistics extends Logistics {
    @Override
    protected Transport createTransport() {
        return new Ship();
    }
}

// Usage
Logistics logistics = new RoadLogistics();
logistics.planDelivery(); // "Deliver by land..."
```

**Pros:**  
✅ Follows Open/Closed Principle (add `AirLogistics` without changing existing code)  
✅ Single Responsibility (creation code separate)

---

## 3. Abstract Factory Pattern

Produce families of related objects without specifying their concrete classes.

### Example: GUI Toolkit (Windows vs Mac)

```java
// Abstract Products
interface Button { void paint(); }
interface Checkbox { void paint(); }

// Concrete Products
class WinButton implements Button { 
    public void paint() { System.out.println("Render Windows Button"); } 
}
class MacButton implements Button { 
    public void paint() { System.out.println("Render Mac Button"); } 
}

// Abstract Factory
interface GUIFactory {
    Button createButton();
    Checkbox createCheckbox();
}

// Concrete Factories
class WinFactory implements GUIFactory {
    public Button createButton() { return new WinButton(); }
    public Checkbox createCheckbox() { return new WinCheckbox(); }
}

class MacFactory implements GUIFactory {
    public Button createButton() { return new MacButton(); }
    public Checkbox createCheckbox() { return new MacCheckbox(); }
}

// Client
class Application {
    private Button button;
    
    public Application(GUIFactory factory) {
        button = factory.createButton();
    }
    
    public void paint() {
        button.paint();
    }
}
```

**Pros:**  
✅ Ensure compatibility (Windows button always with Windows checkbox)  
✅ Strong decoupling

---

## Interview Questions

**Q: Factory Method vs Abstract Factory?**
- **Factory Method**: Creates ONE product. Uses inheritance (subclass decides).
- **Abstract Factory**: Creates FAMILY of related products. Uses composition (factory object passed to client).

**Q: When to use Factory?**
- When you don't know exact types/dependencies of objects beforehand.
- To provide a library/framework where users can extend components.
