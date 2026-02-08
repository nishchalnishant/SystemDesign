# Solid principles

SOLID principles are a set of five design principles in object-oriented programming that help create scalable, maintainable, and flexible software systems. They were introduced by **Robert C. Martin (Uncle Bob)** and are widely used in software design.

***

#### 1. **S - Single Responsibility Principle (SRP)**

_A class should have only one reason to change._

**Explanation:**\
Each class should have only one job or responsibility. If a class has multiple responsibilities, changes in one part might affect the other.

**Example (Bad Design - Violating SRP)**

```java
class Report {
    void generateReport() {
        System.out.println("Generating report...");
    }

    void printReport() {
        System.out.println("Printing report...");
    }
}
```

Here, the `Report` class has two responsibilities:

1. Generating the report
2. Printing the report

If we need to change the way reports are printed, we also modify the same class, which is against SRP.

**Good Design (Following SRP)**

```java
class ReportGenerator {
    void generateReport() {
        System.out.println("Generating report...");
    }
}

class ReportPrinter {
    void printReport() {
        System.out.println("Printing report...");
    }
}
```

Now, changes in report generation won’t affect report printing.

***

#### 2. **O - Open/Closed Principle (OCP)**

_Software entities should be open for extension but closed for modification._

**Explanation:**\
A class should allow its behavior to be extended without modifying its source code.

**Example (Bad Design - Violating OCP)**

```java
class PaymentProcessor {
    void processPayment(String type) {
        if (type.equals("CreditCard")) {
            System.out.println("Processing credit card payment...");
        } else if (type.equals("PayPal")) {
            System.out.println("Processing PayPal payment...");
        }
    }
}
```

If we add a new payment method (e.g., Bitcoin), we have to modify the existing class.

**Good Design (Following OCP)**

```java
abstract class Payment {
    abstract void pay();
}

class CreditCardPayment extends Payment {
    void pay() {
        System.out.println("Processing credit card payment...");
    }
}

class PayPalPayment extends Payment {
    void pay() {
        System.out.println("Processing PayPal payment...");
    }
}

class PaymentProcessor {
    void processPayment(Payment payment) {
        payment.pay();
    }
}
```

Now, if we need to add Bitcoin payment, we can create a new `BitcoinPayment` class without modifying existing code.

***

#### 3. **L - Liskov Substitution Principle (LSP)**

_Subtypes must be substitutable for their base types._

**Explanation:**\
A subclass should be able to replace its superclass without altering the correctness of the program.

**Example (Bad Design - Violating LSP)**

```java
class Rectangle {
    int width, height;

    void setWidth(int width) {
        this.width = width;
    }

    void setHeight(int height) {
        this.height = height;
    }

    int getArea() {
        return width * height;
    }
}

class Square extends Rectangle {
    void setWidth(int width) {
        this.width = width;
        this.height = width; // Violates LSP
    }

    void setHeight(int height) {
        this.width = height;
        this.height = height; // Violates LSP
    }
}
```

Here, `Square` is a subclass of `Rectangle`, but it breaks LSP because changing the width also changes the height, making it behave differently from a rectangle.

**Good Design (Following LSP)**

```java
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    int width, height;

    Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getArea() {
        return width * height;
    }
}

class Square implements Shape {
    int side;

    Square(int side) {
        this.side = side;
    }

    public int getArea() {
        return side * side;
    }
}
```

Now, both `Rectangle` and `Square` can be used interchangeably without breaking behavior.

***

#### 4. **I - Interface Segregation Principle (ISP)**

_A client should not be forced to depend on methods it does not use._

**Explanation:**\
Instead of having large interfaces, break them into smaller, more specific ones.

**Example (Bad Design - Violating ISP)**

```java
interface Worker {
    void work();
    void eat();
}

class Robot implements Worker {
    public void work() {
        System.out.println("Robot working...");
    }

    public void eat() {  // Robots don't eat, but are forced to implement this method.
        throw new UnsupportedOperationException("Robots don't eat");
    }
}
```

**Good Design (Following ISP)**

```java
interface Workable {
    void work();
}

interface Eatable {
    void eat();
}

class Human implements Workable, Eatable {
    public void work() {
        System.out.println("Human working...");
    }

    public void eat() {
        System.out.println("Human eating...");
    }
}

class Robot implements Workable {
    public void work() {
        System.out.println("Robot working...");
    }
}
```

Now, `Robot` is not forced to implement `eat()`.

***

#### 5. **D - Dependency Inversion Principle (DIP)**

_High-level modules should not depend on low-level modules. Both should depend on abstractions._

**Explanation:**\
Instead of directly depending on concrete implementations, depend on abstractions (interfaces).

**Example (Bad Design - Violating DIP)**

```java
class MySQLDatabase {
    void connect() {
        System.out.println("Connecting to MySQL...");
    }
}

class Application {
    MySQLDatabase database = new MySQLDatabase();

    void start() {
        database.connect();
    }
}
```

Here, `Application` is tightly coupled to `MySQLDatabase`, making it hard to switch to another database.

**Good Design (Following DIP)**

```java
interface Database {
    void connect();
}

class MySQLDatabase implements Database {
    public void connect() {
        System.out.println("Connecting to MySQL...");
    }
}

class PostgreSQLDatabase implements Database {
    public void connect() {
        System.out.println("Connecting to PostgreSQL...");
    }
}

class Application {
    private Database database;

    Application(Database database) {
        this.database = database;
    }

    void start() {
        database.connect();
    }
}
```

Now, `Application` depends on the `Database` interface, making it flexible to use any database.

***

### **Summary of SOLID Principles**

| Principle                     | Description                                                       | Example Issue                                                       |
| ----------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------- |
| **S** - Single Responsibility | A class should have one responsibility.                           | A class handling both report generation and printing.               |
| **O** - Open/Closed           | A class should be open for extension but closed for modification. | Adding new payment methods requiring modification of existing code. |
| **L** - Liskov Substitution   | Subtypes must be substitutable for their base types.              | A `Square` class breaking `Rectangle`’s behavior.                   |
| **I** - Interface Segregation | Clients should not be forced to depend on unused methods.         | A `Robot` being forced to implement an `eat()` method.              |
| **D** - Dependency Inversion  | Depend on abstractions, not concrete classes.                     | An `Application` class directly depending on `MySQLDatabase`.       |

Following **SOLID** principles leads to a well-structured, maintainable, and scalable codebase. 🚀





* **Python**&#x20;

Here are the **SOLID principles** explained with Python examples. 🚀

***

### **1. Single Responsibility Principle (SRP)**

_A class should have only one reason to change._

#### **Bad Design (Violating SRP)**

```python
class Report:
    def generate_report(self):
        print("Generating report...")

    def print_report(self):
        print("Printing report...")
```

👎 **Problem:**\
The `Report` class is responsible for both generating and printing the report. If we need to change the way we print, we have to modify this class.

#### **Good Design (Following SRP)**

```python
class ReportGenerator:
    def generate_report(self):
        print("Generating report...")

class ReportPrinter:
    def print_report(self):
        print("Printing report...")
```

👍 **Fix:**\
Now, each class has a **single responsibility**, making the system more modular and easier to maintain.

***

### **2. Open/Closed Principle (OCP)**

_Software entities should be open for extension but closed for modification._

#### **Bad Design (Violating OCP)**

```python
class PaymentProcessor:
    def process_payment(self, type):
        if type == "CreditCard":
            print("Processing credit card payment...")
        elif type == "PayPal":
            print("Processing PayPal payment...")
```

👎 **Problem:**\
If we add a new payment method (e.g., Bitcoin), we need to modify this class.

#### **Good Design (Following OCP)**

```python
from abc import ABC, abstractmethod

class Payment(ABC):
    @abstractmethod
    def pay(self):
        pass

class CreditCardPayment(Payment):
    def pay(self):
        print("Processing credit card payment...")

class PayPalPayment(Payment):
    def pay(self):
        print("Processing PayPal payment...")

class PaymentProcessor:
    def process_payment(self, payment: Payment):
        payment.pay()
```

👍 **Fix:**\
Now, if we need to add Bitcoin, we just create a new `BitcoinPayment` class **without modifying existing code**.

***

### **3. Liskov Substitution Principle (LSP)**

_Subtypes must be substitutable for their base types._

#### **Bad Design (Violating LSP)**

```python
class Rectangle:
    def __init__(self, width, height):
        self.width = width
        self.height = height

    def set_width(self, width):
        self.width = width

    def set_height(self, height):
        self.height = height

    def get_area(self):
        return self.width * self.height

class Square(Rectangle):
    def set_width(self, width):
        self.width = width
        self.height = width  # Violates LSP

    def set_height(self, height):
        self.width = height
        self.height = height  # Violates LSP
```

👎 **Problem:**\
A `Square` should not behave like a `Rectangle`. Setting width also changes the height, breaking expected behavior.

#### **Good Design (Following LSP)**

```python
from abc import ABC, abstractmethod

class Shape(ABC):
    @abstractmethod
    def get_area(self):
        pass

class Rectangle(Shape):
    def __init__(self, width, height):
        self.width = width
        self.height = height

    def get_area(self):
        return self.width * self.height

class Square(Shape):
    def __init__(self, side):
        self.side = side

    def get_area(self):
        return self.side * self.side
```

👍 **Fix:**\
Now, `Square` and `Rectangle` can be used interchangeably without breaking behavior.

***

### **4. Interface Segregation Principle (ISP)**

_A client should not be forced to depend on methods it does not use._

#### **Bad Design (Violating ISP)**

```python
class Worker:
    def work(self):
        pass

    def eat(self):
        pass

class Robot(Worker):
    def work(self):
        print("Robot working...")

    def eat(self):  # Robots don't eat, but are forced to implement this method.
        raise NotImplementedError("Robots don't eat")
```

👎 **Problem:**\
The `Robot` class is forced to implement `eat()`, even though it doesn’t make sense.

#### **Good Design (Following ISP)**

```python
from abc import ABC, abstractmethod

class Workable(ABC):
    @abstractmethod
    def work(self):
        pass

class Eatable(ABC):
    @abstractmethod
    def eat(self):
        pass

class Human(Workable, Eatable):
    def work(self):
        print("Human working...")

    def eat(self):
        print("Human eating...")

class Robot(Workable):
    def work(self):
        print("Robot working...")
```

👍 **Fix:**\
Now, `Robot` implements only what it needs.

***

### **5. Dependency Inversion Principle (DIP)**

_High-level modules should not depend on low-level modules. Both should depend on abstractions._

#### **Bad Design (Violating DIP)**

```python
class MySQLDatabase:
    def connect(self):
        print("Connecting to MySQL...")

class Application:
    def __init__(self):
        self.database = MySQLDatabase()

    def start(self):
        self.database.connect()
```

👎 **Problem:**\
The `Application` class is tightly coupled to `MySQLDatabase`. Switching to another database requires modifying the application.

#### **Good Design (Following DIP)**

```python
from abc import ABC, abstractmethod

class Database(ABC):
    @abstractmethod
    def connect(self):
        pass

class MySQLDatabase(Database):
    def connect(self):
        print("Connecting to MySQL...")

class PostgreSQLDatabase(Database):
    def connect(self):
        print("Connecting to PostgreSQL...")

class Application:
    def __init__(self, database: Database):
        self.database = database

    def start(self):
        self.database.connect()
```

👍 **Fix:**\
Now, `Application` depends on the `Database` interface, making it easy to switch databases.

***

### **Summary of SOLID Principles in Python**

| Principle                     | Description                                                       | Example Issue                                                       |
| ----------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------------------- |
| **S** - Single Responsibility | A class should have one responsibility.                           | A class handling both report generation and printing.               |
| **O** - Open/Closed           | A class should be open for extension but closed for modification. | Adding new payment methods requiring modification of existing code. |
| **L** - Liskov Substitution   | Subtypes must be substitutable for their base types.              | A `Square` class breaking `Rectangle`’s behavior.                   |
| **I** - Interface Segregation | Clients should not be forced to depend on unused methods.         | A `Robot` being forced to implement an `eat()` method.              |
| **D** - Dependency Inversion  | Depend on abstractions, not concrete classes.                     | An `Application` class directly depending on `MySQLDatabase`.       |

Following **SOLID** principles leads to a well-structured, maintainable, and scalable codebase. 🚀🔥





<figure><img src="../../../.gitbook/assets/image (138).png" alt=""><figcaption></figcaption></figure>
