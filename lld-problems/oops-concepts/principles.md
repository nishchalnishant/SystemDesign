# OOPs principles

Here is a Python program that demonstrates all the key Object-Oriented Programming (OOP) concepts, including:

1. **Class and Object**
2. **Encapsulation**
3. **Abstraction**
4. **Inheritance** (Single, Multiple, Multilevel, and Hierarchical)
5. **Polymorphism** (Method Overriding and Method Overloading using default arguments)
6. **Static Methods and Class Methods**
7. **Magic/Dunder Methods** (e.g., `__init__`, `__str__`)
8. **Composition (Has-a Relationship)**
9. **IS-A Relationship (Inheritance-based concept)**

***

#### Code with Detailed Comments:

```java
// Object-Oriented Programming (OOP) Concepts in Java
import java.util.*;

// 1. Class and Object
class Animal {
    // A base class to represent an animal
    protected String name;  // Public Attribute (protected for inheritance)
    protected String species;  // Public Attribute
    
    public Animal(String name, String species) {
        this.name = name;
        this.species = species;
    }
    
    public String makeSound() {
        // A general method to make a sound
        return "Some generic sound";
    }
    
    @Override
    public String toString() {
        // toString Method to return a string representation
        return this.name + " is a " + this.species;
    }
}

// 2. Encapsulation: Protecting data using private attributes
class BankAccount {
    // A class to demonstrate Encapsulation
    private String accountHolder;  // Public attribute
    private double balance;  // Private attribute (Encapsulation)
    
    public BankAccount(String accountHolder, double balance) {
        this.accountHolder = accountHolder;
        this.balance = balance;
    }
    
    public String deposit(double amount) {
        // Public method to deposit money
        if (amount > 0) {
            this.balance += amount;
            return "Deposited " + amount + ", New Balance: " + this.balance;
        }
        return "Invalid deposit amount";
    }
    
    public String withdraw(double amount) {
        // Public method to withdraw money
        if (0 < amount && amount <= this.balance) {
            this.balance -= amount;
            return "Withdrew " + amount + ", Remaining Balance: " + this.balance;
        }
        return "Insufficient funds";
    }
    
    public double getBalance() {
        // Getter method to access private attribute
        return this.balance;
    }
}

// 3. Abstraction: Hiding complex details and exposing only necessary parts
abstract class Vehicle {
    // Abstract class that forces subclasses to implement specific methods
    public abstract String startEngine();
    public abstract String stopEngine();
}

class Car extends Vehicle {
    // Concrete class implementing the abstract methods
    @Override
    public String startEngine() {
        return "Car engine started";
    }
    
    @Override
    public String stopEngine() {
        return "Car engine stopped";
    }
}

// 4. Inheritance (IS-A Relationship): Creating a hierarchy
class Dog extends Animal {
    // Dog IS-A Animal (Inheritance)
    private String breed;  // Additional attribute
    
    public Dog(String name, String breed) {
        super(name, "Dog");  // Calling parent class constructor
        this.breed = breed;
    }
    
    @Override
    public String makeSound() {
        // Method Overriding (Polymorphism)
        return "Bark!";
    }
}

class Cat extends Animal {
    // Cat IS-A Animal
    private String color;
    
    public Cat(String name, String color) {
        super(name, "Cat");
        this.color = color;
    }
    
    @Override
    public String makeSound() {
        return "Meow!";
    }
}

// 5. Multiple Inheritance (using interfaces in Java)
interface Flying {
    // A interface to add flying capability
    String fly();
}

class Bird extends Animal implements Flying {
    // Bird IS-A Animal and it also CAN Fly
    public Bird(String name, String species) {
        super(name, species);
    }
    
    @Override
    public String makeSound() {
        return "Chirp!";
    }
    
    @Override
    public String fly() {
        return "I can fly!";
    }
}

// 6. Multilevel Inheritance
class Puppy extends Dog {
    // Puppy IS-A Dog, which IS-A Animal
    private int age;
    
    public Puppy(String name, String breed, int age) {
        super(name, breed);
        this.age = age;
    }
    
    public String isCute() {
        return this.name + " is a cute " + this.age + "-month-old puppy!";
    }
}

// 7. Hierarchical Inheritance: Multiple child classes from the same base
class Lion extends Animal {
    // Lion IS-A Animal
    public Lion(String name) {
        super(name, "Lion");
    }
    
    @Override
    public String makeSound() {
        return "Roar!";
    }
}

// 8. Polymorphism: Method Overriding
class PolymorphismDemo {
    public static String animalSound(Animal animal) {
        // Function to demonstrate polymorphism
        return animal.makeSound();
    }
}

// 9. Method Overloading
class MathOperations {
    // Demonstrating method overloading
    public int add(int a, int b) {
        return a + b;
    }
    
    public int add(int a, int b, int c) {
        return a + b + c;
    }
}

// 10. Static Methods
class Utility {
    // A class with static methods
    public static String greet() {
        return "Hello, welcome to OOP in Java!";
    }
    
    public static String describeClass() {
        return "This is Utility class.";
    }
}

// 11. Composition (HAS-A Relationship)
class Engine {
    // Engine class
    public String start() {
        return "Engine started";
    }
    
    public String stop() {
        return "Engine stopped";
    }
}

class CarWithEngine {
    // Car HAS-A Engine (Composition)
    private String model;
    private Engine engine;  // Composition
    
    public CarWithEngine(String model) {
        this.model = model;
        this.engine = new Engine();
    }
    
    public String startCar() {
        return this.model + ": " + this.engine.start();
    }
    
    public String stopCar() {
        return this.model + ": " + this.engine.stop();
    }
}

// Demonstration of OOP concepts
public class Main {
    public static void main(String[] args) {
        // Creating Objects
        Dog dog = new Dog("Buddy", "Golden Retriever");
        Cat cat = new Cat("Whiskers", "Black");
        Lion lion = new Lion("Simba");
        
        // Demonstrate Polymorphism
        System.out.println(PolymorphismDemo.animalSound(dog));  // Output: Bark!
        System.out.println(PolymorphismDemo.animalSound(cat));  // Output: Meow!
        System.out.println(PolymorphismDemo.animalSound(lion));  // Output: Roar!
        
        // Demonstrate Encapsulation
        BankAccount account = new BankAccount("Alice", 1000);
        System.out.println(account.deposit(500));
        System.out.println(account.withdraw(300));
        System.out.println("Balance: " + account.getBalance());
        
        // Demonstrate Abstraction
        Car car = new Car();
        System.out.println(car.startEngine());
        System.out.println(car.stopEngine());
        
        // Demonstrate Composition
        CarWithEngine myCar = new CarWithEngine("Tesla Model X");
        System.out.println(myCar.startCar());
        System.out.println(myCar.stopCar());
        
        // Demonstrate Multiple Inheritance (via interfaces)
        Bird bird = new Bird("Eagle", "Bird of Prey");
        System.out.println(bird.fly());  // Output: I can fly!
        
        // Demonstrate Static Methods
        System.out.println(Utility.greet());  // Output: Hello, welcome to OOP in Java!
        System.out.println(Utility.describeClass());  // Output: This is Utility class.
        
        // Demonstrate Multilevel Inheritance
        Puppy puppy = new Puppy("Max", "Labrador", 3);
        System.out.println(puppy.isCute());
        
        // Demonstrate Method Overloading
        MathOperations mathOps = new MathOperations();
        System.out.println(mathOps.add(5, 10));  // Output: 15
        System.out.println(mathOps.add(5, 10, 20));  // Output: 35
    }
}
```

***

#### Summary of OOP Concepts Demonstrated:

✅ **Class & Object** – `Animal`, `Dog`, `Car`, etc.\
✅ **Encapsulation** – `BankAccount` with private attribute `__balance`\
✅ **Abstraction** – `Vehicle` (Abstract Class)\
✅ **Inheritance** – `Dog IS-A Animal`, `Bird IS-A Animal`\
✅ **Polymorphism** – `make_sound()` overridden in `Dog`, `Cat`, `Lion`\
✅ **Multiple Inheritance** – `Bird` inherits from `Animal` & `Flying`\
✅ **Multilevel Inheritance** – `Puppy → Dog → Animal`\
✅ **Hierarchical Inheritance** – `Dog`, `Cat`, `Lion` from `Animal`\
✅ **Method Overloading** – `MathOperations.add()` with default arguments\
✅ **Static & Class Methods** – `Utility.greet()`, `Utility.describe_class()`\
✅ **Composition (HAS-A)** – `CarWithEngine HAS-A Engine`

This code thoroughly explains and demonstrates **all key OOP concepts** in Python. 🚀 Let me know if you need any modifications!





Here's the enhanced Python program with **detailed comments** explaining each OOP concept before the corresponding code. It covers **all major OOP principles** with proper explanations.

***

#### **Key OOP Concepts Covered:**

✅ **Class & Object**\
✅ **Encapsulation** (Data Hiding)\
✅ **Abstraction** (Hiding Implementation Details)\
✅ **Inheritance** (Single, Multiple, Multilevel, Hierarchical)\
✅ **Polymorphism** (Method Overriding & Overloading)\
✅ **IS-A Relationship** (Inheritance-based)\
✅ **HAS-A Relationship** (Composition)\
✅ **Static Methods & Class Methods**\
✅ **Magic Methods (`__init__`, `__str__`)**

***

#### **🔹 Python Code with Detailed Comments:**

```java
// ========================================================
// ✅ 1. CLASS & OBJECT
// ========================================================
// A class is a blueprint for creating objects.
// An object is an instance of a class with its own attributes and methods.
// Example: An Animal class with name and species attributes.
class Animal {
    // A base class to represent an Animal
    protected String name;  // Public Attribute (protected for inheritance)
    protected String species;  // Public Attribute
    
    public Animal(String name, String species) {
        this.name = name;
        this.species = species;
    }
    
    public String makeSound() {
        // A general method to make a sound
        return "Some generic sound";
    }
    
    @Override
    public String toString() {
        // toString Method to return a string representation
        return this.name + " is a " + this.species;
    }
}

// ========================================================
// ✅ 2. ENCAPSULATION (DATA HIDING)
// ========================================================
// Encapsulation restricts direct access to some data,
// allowing controlled access through methods (getters/setters).
// Example: A BankAccount class where balance is private.
class BankAccount {
    // A class demonstrating Encapsulation
    private String accountHolder;  // Public attribute
    private double balance;  // Private attribute (Encapsulation)
    
    public BankAccount(String accountHolder, double balance) {
        this.accountHolder = accountHolder;
        this.balance = balance;
    }
    
    public String deposit(double amount) {
        // Public method to deposit money
        if (amount > 0) {
            this.balance += amount;
            return "Deposited " + amount + ", New Balance: " + this.balance;
        }
        return "Invalid deposit amount";
    }
    
    public String withdraw(double amount) {
        // Public method to withdraw money
        if (0 < amount && amount <= this.balance) {
            this.balance -= amount;
            return "Withdrew " + amount + ", Remaining Balance: " + this.balance;
        }
        return "Insufficient funds";
    }
    
    public double getBalance() {
        // Getter method to access private attribute
        return this.balance;
    }
}

// ========================================================
// ✅ 3. ABSTRACTION (HIDING IMPLEMENTATION DETAILS)
// ========================================================
// Abstraction hides complex details and exposes only necessary functionality.
// Abstract classes cannot be instantiated and force subclasses to implement required methods.
abstract class Vehicle {
    // Abstract class forcing subclasses to implement methods
    public abstract String startEngine();
    public abstract String stopEngine();
}

class Car extends Vehicle {
    // Concrete class implementing abstract methods
    @Override
    public String startEngine() {
        return "Car engine started";
    }
    
    @Override
    public String stopEngine() {
        return "Car engine stopped";
    }
}

// ========================================================
// ✅ 4. INHERITANCE (IS-A RELATIONSHIP)
// ========================================================
// Inheritance allows a class (child) to acquire properties and behaviors of another class (parent).
// This promotes code reuse. Example: Dog IS-A Animal.
class Dog extends Animal {
    // Dog IS-A Animal (Inheritance)
    private String breed;  // Additional attribute
    
    public Dog(String name, String breed) {
        super(name, "Dog");  // Calling parent class constructor
        this.breed = breed;
    }
    
    @Override
    public String makeSound() {
        // Method Overriding (Polymorphism)
        return "Bark!";
    }
}

// ========================================================
// ✅ 5. MULTIPLE INHERITANCE
// ========================================================
// A class can inherit from multiple base classes.
// Example: Bird IS-A Animal and also CAN Fly.
interface Flying {
    // A interface to add flying capability
    String fly();
}

class Bird extends Animal implements Flying {
    // Bird IS-A Animal and it also CAN Fly
    public Bird(String name, String species) {
        super(name, species);
    }
    
    @Override
    public String makeSound() {
        return "Chirp!";
    }
    
    @Override
    public String fly() {
        return "I can fly!";
    }
}

// ========================================================
// ✅ 6. MULTILEVEL INHERITANCE
// ========================================================
// A class is derived from another derived class.
// Example: Puppy → Dog → Animal
class Puppy extends Dog {
    // Puppy IS-A Dog, which IS-A Animal
    private int age;
    
    public Puppy(String name, String breed, int age) {
        super(name, breed);
        this.age = age;
    }
    
    public String isCute() {
        return this.name + " is a cute " + this.age + "-month-old puppy!";
    }
}

// ========================================================
// ✅ 7. POLYMORPHISM (METHOD OVERRIDING & OVERLOADING)
// ========================================================
// Polymorphism allows different classes to have the same method name but different behaviors.
// Example: makeSound() is overridden in Dog, Cat, and Lion.
class Cat extends Animal {
    // Cat IS-A Animal
    public Cat(String name) {
        super(name, "Cat");
    }
    
    @Override
    public String makeSound() {
        return "Meow!";
    }
}

class Lion extends Animal {
    // Lion IS-A Animal
    public Lion(String name) {
        super(name, "Lion");
    }
    
    @Override
    public String makeSound() {
        return "Roar!";
    }
}

// Polymorphism in action
class PolymorphismDemo {
    public static String animalSound(Animal animal) {
        // Function to demonstrate polymorphism
        return animal.makeSound();
    }
}

// ========================================================
// ✅ 8. COMPOSITION (HAS-A RELATIONSHIP)
// ========================================================
// Instead of inheritance, we can use composition where a class contains another class as an attribute.
// Example: A Car HAS-A Engine.
class Engine {
    // Engine class
    public String start() {
        return "Engine started";
    }
    
    public String stop() {
        return "Engine stopped";
    }
}

class CarWithEngine {
    // Car HAS-A Engine (Composition)
    private String model;
    private Engine engine;  // Composition
    
    public CarWithEngine(String model) {
        this.model = model;
        this.engine = new Engine();
    }
    
    public String startCar() {
        return this.model + ": " + this.engine.start();
    }
    
    public String stopCar() {
        return this.model + ": " + this.engine.stop();
    }
}

// ========================================================
// ✅ 9. STATIC METHODS
// ========================================================
// Static methods don't use instance attributes and can be called without an instance.
class Utility {
    // A class with static methods
    public static String greet() {
        return "Hello, welcome to OOP in Java!";
    }
    
    public static String describeClass() {
        return "This is Utility class.";
    }
}

// ========================================================
// ✅ 10. DEMONSTRATION OF ALL CONCEPTS
// ========================================================
public class Main {
    public static void main(String[] args) {
        // Objects
        Dog dog = new Dog("Buddy", "Golden Retriever");
        Cat cat = new Cat("Whiskers");
        Lion lion = new Lion("Simba");
        
        // Polymorphism
        System.out.println(PolymorphismDemo.animalSound(dog));  // Bark!
        System.out.println(PolymorphismDemo.animalSound(cat));  // Meow!
        System.out.println(PolymorphismDemo.animalSound(lion));  // Roar!
        
        // Encapsulation
        BankAccount account = new BankAccount("Alice", 1000);
        System.out.println(account.deposit(500));
        System.out.println(account.withdraw(300));
        System.out.println("Balance: " + account.getBalance());
        
        // Abstraction
        Car car = new Car();
        System.out.println(car.startEngine());
        System.out.println(car.stopEngine());
        
        // Composition
        CarWithEngine myCar = new CarWithEngine("Tesla Model X");
        System.out.println(myCar.startCar());
        System.out.println(myCar.stopCar());
        
        // Static Methods
        System.out.println(Utility.greet());  
        System.out.println(Utility.describeClass());  
        
        // Multilevel Inheritance
        Puppy puppy = new Puppy("Max", "Labrador", 3);
        System.out.println(puppy.isCute());
    }
}
```

***

This code **explains OOP concepts in detail** with **proper examples and comments**. 🚀
Let me know if you need more modifications or explanations! 😊
