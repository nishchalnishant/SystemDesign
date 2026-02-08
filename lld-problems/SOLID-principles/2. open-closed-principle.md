# Open/Closed Principle (OCP)

> **Definition**: Software entities (classes, modules, functions) should be **Open for Extension**, but **Closed for Modification**.

## The Problem

If you have to change existing code to add new functionality, you risk introducing bugs into previously working code.

### ❌ Bad Example (Violates OCP)

```java
public class AreaCalculator {
    public double calculateArea(Object shape) {
        if (shape instanceof Rectangle) {
            Rectangle r = (Rectangle) shape;
            return r.length * r.width;
        } else if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return Math.PI * c.radius * c.radius;
        }
        return 0;
    }
}

// Usage
Rectangle r = new Rectangle(5, 10);
Circle c = new Circle(7);
// To add Triangle, we must MODIFY AreaCalculator!
```

**Problems:**
- Every new shape requires modifying `AreaCalculator` class.
- Violates OCP because it's not closed for modification.
- Risk of breaking "Rectangle" logic when adding "Triangle".

---

### ✅ Good Example (Follows OCP)

```java
// 1. Define an interface (Contract)
interface Shape {
    double calculateArea();
}

// 2. Implement concrete classes (Extension)
class Rectangle implements Shape {
    double length;
    double width;
    
    public Rectangle(double length, double width) {
        this.length = length;
        this.width = width;
    }
    
    @Override
    public double calculateArea() {
        return length * width;
    }
}

class Circle implements Shape {
    double radius;
    
    public Circle(double radius) {
        this.radius = radius;
    }
    
    @Override
    public double calculateArea() {
        return Math.PI * radius * radius;
    }
}

// 3. New functionality (Extension without modification)
class Triangle implements Shape {
    double base;
    double height;
    
    public Triangle(double base, double height) {
        this.base = base;
        this.height = height;
    }
    
    @Override
    public double calculateArea() {
        return 0.5 * base * height;
    }
}

// 4. Client code remains unchanged!
public class AreaCalculator {
    public double calculateTotalArea(List<Shape> shapes) {
        double total = 0;
        for (Shape shape : shapes) {
            total += shape.calculateArea(); // Polymorphism magic!
        }
        return total;
    }
}
```

**Benefits:**
- Add `Triangle`, `Hexagon`, `Polygon` without touching `AreaCalculator`.
- `AreaCalculator` is **Closed for Modification**.
- `Shape` hierarchy is **Open for Extension**.

---

## Real-World Example: Notification Service

### ❌ Bad

```java
public class NotificationSender {
    public void send(String type, String message) {
        if (type.equals("email")) {
            // Send SMTP
        } else if (type.equals("sms")) {
            // Send Twilio
        } else if (type.equals("push")) { // Modified to add new type!
            // Send FCM
        }
    }
}
```

### ✅ Good

```java
interface NotificationChannel {
    void send(String message);
}

class EmailChannel implements NotificationChannel {
    public void send(String message) { /* SMTP logic */ }
}

class SMSChannel implements NotificationChannel {
    public void send(String message) { /* Twilio logic */ }
}

class PushChannel implements NotificationChannel {
    public void send(String message) { /* FCM logic */ }
}

public class NotificationSender {
    private NotificationChannel channel;
    
    public NotificationSender(NotificationChannel channel) {
        this.channel = channel;
    }
    
    public void notify(String message) {
        channel.send(message);
    }
}
```

---

## How to Apply OCP

1. **Use Interfaces / Abstract Classes**: Define a contract.
2. **Use Polymorphism**: Let subclasses handle specific behavior.
3. **Dependency Injection**: Inject implementations at runtime.
4. **Design Patterns**:
   - **Strategy Pattern**: Swap algorithms.
   - **Decorator Pattern**: Add behavior dynamically.
   - **Factory Pattern**: Create objects without coupling.

## Interview Tips

**Q: "Can you be 100% closed for modification?"**
- A: "No. If the core logic or interface changes (e.g., `calculateArea` needs a parameter), you have to modify code. OCP minimizes modification, doesn't eliminate it."

**Q: "How does OCP relate to plugins?"**
- A: "Plugins are the ultimate OCP example. An IDE (like VS Code) is closed for modification but open for extension via extensions API."
