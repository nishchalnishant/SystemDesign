# Four pillers of OOPs

## <mark style="color:purple;">Class and objects --</mark>&#x20;

* A class is a blueprint or template that defines the properties and behavior of an object.
* An object is an instance of a class created using the class defination.

```java
public class Car {
    private String make;
    private String model;
    private int year;
    
    public Car(String make, String model, int year) {
        this.make = make;
        this.model = model;
        this.year = year;
    }
    
    public void startEngine() {
        System.out.println("The " + this.make + " " + this.model + "'s engine is starting");
    }
}
```

## <mark style="color:purple;">Following are the four pilers of OOPs:</mark>

1. <mark style="color:green;">**Encapsulation**</mark><mark style="color:green;">:</mark>&#x20;
   1. Encapsulation refers to the bundling of data (attributes) and methods (functions) that operate on the data into a single unit called a class. It keeps the data safe from outside interference and misuse.
   2. concept of hiding the implementation details of an object from the outside world and only exposing the necessary information through public methods.
   3. helps protect the object's internal state from external interference and misuse.
2. <mark style="color:green;">**Abstraction**</mark><mark style="color:green;">:</mark>&#x20;
   1. Abstraction is the process of hiding the complex implementation details and showing only the essential features of the object. It allows the programmer to focus on what the object does rather than how it does it.
   2. Abstraction is the concept of showing only the necessary information to the outside world while hiding unnecessary details.
   3. helps to simplify complex systems and focus on the essential features
3. <mark style="color:green;">**Inheritance**</mark><mark style="color:green;">:</mark>&#x20;
   1. Inheritance is a mechanism where a new class (derived class or subclass) is derived from an existing class (base class or superclass).&#x20;
   2. The derived class inherits the features (properties and methods) of the existing class, enabling code reuse and establishing a hierarchical relationship between classes.
   3. **Inheritance** is a mechanism that allows a class to inherit properties and methods from another class, called the **superclass** or **parent class**.
   4. The class that inherits is called the **subclass** or **child class**.
4. <mark style="color:green;">**Polymorphism**</mark><mark style="color:green;">:</mark>&#x20;
   1. Polymorphism means the ability to take multiple forms. In OOP, it refers to the ability of different objects to respond to the same message (method call) in different ways.&#x20;
   2. There are two types of polymorphism: compile-time (method overloading) and runtime (method overriding).
   3. Polymorphism is the ability of an object to take on multiple forms.
   4. A common way to achieve polymorphism is **method overriding.**
   5. Method overriding is when a subclass provides a specific implementation of a method that is already defined in its parent class.<br>

## Example:

Lets use an example of vehicle class to explain the four pilers of the OOPs:

#### <mark style="color:green;">1. Encapsulation:</mark>

* Encapsulation involves bundling data (attributes) and methods (functions) that operate on the data into a single unit (class).&#x20;
* in python we can achieve encaplsulation using private attributes and methods denoted by a double underscore.
* Here the \_\_make and \_\_model are private meaning it can't be accessed  directly from outside the class.
* Here's how encapsulation can be applied to a `Vehicle` class:

```java
public class Vehicle {
    private String make;  // Encapsulated attribute
    private String model; // Encapsulated attribute
    
    public Vehicle(String make, String model) {
        this.make = make;
        this.model = model;
    }
    
    public void displayInfo() {
        System.out.println("Vehicle: " + this.make + " " + this.model);
    }
    
    public void start() {
        System.out.println("Vehicle started.");
    }
    
    public void stop() {
        System.out.println("Vehicle stopped.");
    }
}
```

In this example:

* `__make` and `__model` are encapsulated attributes of the `Vehicle` class.
* `display_info`, `start`, and `stop` are encapsulated methods that operate on the attributes.

#### <mark style="color:green;">2. Abstraction:</mark>

* Abstraction involves hiding the complex implementation details and showing only the essential features of an object.&#x20;
* helps to simplify complex systems and focus on essential features.
* In python we can achieve this using abstract basic classes(ABC) and abstract methods
* Here's how abstraction is implemented in our `Vehicle` class:

```java
// Abstract class representing a Shape
abstract class Shape {
    public abstract double area();
}

// Rectangle class extending Shape
class Rectangle extends Shape {
    private double width;
    private double height;
    
    public Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    @Override
    public double area() {
        return this.width * this.height;
    }
}

// Circle class extending Shape
class Circle extends Shape {
    private double radius;
    
    public Circle(double radius) {
        this.radius = radius;
    }
    
    @Override
    public double area() {
        return 3.14 * this.radius * this.radius;
    }
}
```

In this example:

* Let’s say we have an abstract base class called `Shape`. The `Shape` class is marked as an abstract class by inheriting from the `ABC` class (Abstract Base Class).
* Inside the `Shape` class, we define an abstract method called `area()` using the `@abstractmethod` decorator.
*   The `Rectangle` and `Circle` classes inherit from the `Shape` class.


* They provide their own implementations of the `area()` method specific to their shapes.&#x20;
* Note that the implementation details are hidden from the outside world, and only the interface defined by the abstract class is exposed.

#### <mark style="color:green;">3. Inheritance:</mark>

* Inheritance allows one class (subclass) to inherit the attributes and methods of another class (superclass).&#x20;
* The child class inherits all the fields and methods of the parent class and can also add new fields and methods or override the ones inherited from the parent class.
* Inheritance promotes **code reuse** and helps create a **hierarchical** structure.

\
Here's how inheritance can be used to extend our `Vehicle` class:



```java
// Define a parent class called "Vehicle"
class Vehicle {
    protected String color;
    
    public Vehicle(String color) {
        this.color = color;
    }
    
    public void honk() {
        System.out.println("Honk honk");
    }
}

// Define a child class called "Car" that inherits from Vehicle
class Car extends Vehicle {
    private int speed;
    
    public Car(String color, int speed) {
        super(color);  // Call parent constructor
        this.speed = speed;
    }
    
    public void accelerate() {
        this.speed += 10;
    }
}

// Create an object (instance of the Car class)
public class Main {
    public static void main(String[] args) {
        Car myCar = new Car("red", 60);
        myCar.honk();
    }
}
```

In this example:

* The `Car` class inherits the `color` attribute and the `honk` method from the `Vehicle` class promoting code reuse.&#x20;
* The `Car` class also adds its own attributes and methods, such as `speed` and `accelerate`.

#### <mark style="color:green;">4. Polymorphism:</mark>

* Polymorphism allows objects to be treated as instances of their superclass, enabling flexibility and reusability in code.&#x20;
* It enables you to write generic code that can work with objects of multiple types, as long as they share a common interface.
* Here's how polymorphism is demonstrated using our `Vehicle` and `Car` classes:

```java
// Abstract Document class
abstract class Document {
    public abstract String show();
}

// PDF class implementing Document
class PDF extends Document {
    @Override
    public String show() {
        return "Show PDF Content";
    }
}

// Word class implementing Document
class Word extends Document {
    @Override
    public String show() {
        return "Show Word content";
    }
}

// Client code demonstrating polymorphism
public class Main {
    public static void main(String[] args) {
        Document[] docs = {new PDF(), new Word()};
        
        for (Document doc : docs) {
            System.out.println(doc.show());
        }
    }
}
```

In this example:

* Each subclass (`Pdf`, `Word`) of `Document` implement the `show` method differently (method overriding), but the interface remains consistent giving the ability to iterate over both the classes using a single for loop.

