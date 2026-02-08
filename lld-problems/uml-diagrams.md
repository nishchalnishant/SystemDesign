# UML digrams

Unified modeling language, there are multiple places where this is used.

Can be divided into 2 types --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20231222121300/UML-Diagrams.jpg" alt=""><figcaption></figcaption></figure>

Out of all, we mostly use the class digram for LLD.

Class diagrams provide a high-level overview of a system's design, helping to communicate and document the structure of the software.

Class digram --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20240118123645/Class-Notation.webp" alt=""><figcaption></figcaption></figure>



* class Name
* attributes
* Metods/operations
* Visibility notations&#x20;
  * Visibility notations indicate the access level of attributes and methods. Common visibility notations include:
    * `+` for public (visible to all classes)
    * `-` for private (visible only within the class)
    * `#` for protected (visible to subclasses)
    * `~` for package or default visibility (visible to classes in the same package)



Now if there are multiple classes we can interlink them to make a suitable working scenario. to do that we use notations --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20240209094815/relationship.webp" alt=""><figcaption></figcaption></figure>

We have following relation ships --

*   **Composition** --

    * Composition is a stronger form of aggregation, indicating a more significant ownership or dependency relationship.&#x20;
    * In composition, the part class cannot exist independently of the whole class.
    * ex — Imagine a digital contact book application. The contact book is the whole, and each contact entry is a part. Each contact entry is fully owned and managed by the contact book. If the contact book is deleted or destroyed, all associated contact entries are also removed.


*   **Directed** **Association** --

    * A directed association in a UML class diagram represents a relationship between two classes where the association has a direction, indicating that one class is associated with another in a specific way.
    * ex — Consider a scenario where a "Teacher" class is associated with a "Course" class in a university system. The directed association arrow may point from the "Teacher" class to the "Course" class, indicating that a teacher is associated with or teaches a specific course.


*   **Usage**(**Dependency**) **Relationship**

    * A usage dependency relationship in a UML class diagram indicates that one class (the client) utilizes or depends on another class (the supplier) to perform certain tasks or access certain functionality.&#x20;
    * The client class relies on the services provided by the supplier class but does not own or create instances of it.
    * ex — Consider a scenario where a "Car" class depends on a "FuelTank" class to manage fuel consumption.


*   **Generalisation (Inheritance)**

    * Inheritance represents an "is-a" relationship between classes, where one class (the subclass or child) inherits the properties and behaviours of another class (the superclass or parent).
    * ex — In the example of bank accounts, we can use generalisation to represent different types of accounts such as current accounts, savings accounts, and credit accounts.


*   **Aggregation**

    * Aggregation is a specialised form of association that represents a "whole-part" relationship.&#x20;
    * It denotes a stronger relationship where one class (the whole) contains or is composed of another class (the part).&#x20;
    * Aggregation is represented by a diamond shape on the side of the whole class. In this kind of relationship, the child class can exist independently of its parent class.
    * ex — The company can be considered as the whole, while the employees are the parts.
    * Employees belong to the company, and the company can have multiple employees. However, if the company ceases to exist, the employees can still exist independently


*   **Association**

    * An association represents a bi-directional relationship between two classes. It indicates that instances of one class are connected to instances of another class.&#x20;
    * Associations are typically depicted as a solid line connecting the classes, with optional arrows indicating the direction of the relationship.
    * ex — Let's consider a simple system for managing a library. In this system, we have two main entities: `Book` and `Library`.&#x20;
    * Each `Library` contains multiple `Books`, and each `Book` belongs to a specific `Library`. This relationship between `Library` and `Book` represents an association.


* **Dependency** **Relationship**
  * A dependency exists between two classes when one class relies on another, but the relationship is not as strong as association or inheritance.&#x20;
  * It represents a more loosely coupled connection between classes.
  *   Let's consider a scenario where a Person depends on a Book.

      * **Person Class:** Represents an individual who reads a book. The Person class depends on the Book class to access and read the content.
      * **Book Class:** Represents a book that contains content to be read by a person. The Book class is independent and can exist without the Person class.



      > The Person class depends on the Book class because it requires access to a book to read its content. However, the Book class does not depend on the Person class; it can exist independently and does not rely on the Person class for its functionality.


* **Realisation (Interface Implementation)**
  * Realisation indicates that a class implements the features of an interface. It is often used in cases where a class realizes the operations defined by an interface.
  *   Let's consider the scenario where a "Person" and a "Corporation" both realizing an "Owner" interface.

      * **Owner Interface:** This interface now includes methods such as "acquire(property)" and "dispose(property)" to represent actions related to acquiring and disposing of property.
      * **Person Class (Realisation):** The Person class implements the Owner interface, providing concrete implementations for the "acquire(property)" and "dispose(property)" methods. For instance, a person can acquire ownership of a house or dispose of a car.
      * **Corporation Class (Realization):** Similarly, the Corporation class also implements the Owner interface, offering specific implementations for the "acquire(property)" and "dispose(property)" methods. For example, a corporation can acquire ownership of real estate properties or dispose of company vehicles.



      > Both the Person and Corporation classes realize the Owner interface, meaning they provide concrete implementations for the "acquire(property)" and "dispose(property)" methods defined in the interface.



***

## UML Digrams

Here’s a comprehensive UML Class Diagram Cheat Sheet with definitions, symbols, and examples for every concept.

***

### 1. Association (Uses-A)

"Links-To" Relationship

Association is a structural relationship where objects of one class are connected to objects of another class. It indicates that one object knows about or navigates to another. It can be uni-directional (A knows B) or bi-directional (A and B know each other).

* Strength: Weak to Medium.
* Lifecycle: The objects have their own independent lifecycles. There is no ownership involved.
* UML Representation: A solid line connecting two classes. An open arrow (`->`) is used to show the direction of navigation.

Java Implementation:

* Mechanism: Define a field (instance variable) in the class.
* Best Practice: Use an Interface for the field type to keep the classes loosely coupled.

Java

```java
// 1. Target Class
class IDCard {
    private String number;
    public IDCard(String n) { this.number = n; }
    public String getNumber() { return number; }
}

// 2. Source Class
class Employee {
    private String name;
    
    // ASSOCIATION: Employee "has" a link to an IDCard
    // The IDCard exists independently; the Employee just refers to it.
    private IDCard idCard; 

    public Employee(String name, IDCard idCard) {
        this.name = name;
        this.idCard = idCard;
    }

    public void showID() {
        System.out.println(name + " has ID: " + idCard.getNumber());
    }
}
```

***

### 2. Aggregation (HAS-A Weak)

"Has-A" (Weak) Relationship

Aggregation is a specialised form of Association. It represents a "Whole-Part" relationship where the part can exist independently of the whole. If the container (parent) is destroyed, the parts (children) are _not_ destroyed.

* Strength: Medium.
* Lifecycle: Independent. The Child is created outside and passed into the Parent.
* UML Representation: A solid line with an empty diamond (`<>`) on the side of the "Whole" (Container).

Java Implementation:

* Mechanism: Constructor Injection. The external object is passed as a parameter to the constructor (or a setter method) and stored in a field.
* Key Logic: Do not use `new` inside the constructor to create the part.

Java

```java
// The Part (Independent)
class Player {
    String name;
    public Player(String name) { this.name = name; }
}

// The Whole (Container)
class FootballTeam {
    String teamName;
    List<Player> players; // Aggregation

    // We pass the list of players IN. The Team does not create them.
    public FootballTeam(String teamName, List<Player> players) {
        this.teamName = teamName;
        this.players = players;
    }
}

public class Main {
    public static void main(String[] args) {
        List<Player> players = new ArrayList<>();
        players.add(new Player("Ronaldo"));

        // If 'juventus' is set to null, 'Ronaldo' still exists in memory.
        FootballTeam juventus = new FootballTeam("Juventus", players);
    }
}
```

***

### 3. Composition (HAS-A Strong)

"Has-A" (Strong) Relationship

Composition is a restricted form of Aggregation. It represents a "Whole-Part" relationship where the part CANNOT exist without the whole. If the container is destroyed, the parts are destroyed with it.

* Strength: Strong.
* Lifecycle: Dependent. The Child's lifecycle is managed strictly by the Parent.
* UML Representation: A solid line with a filled diamond (`♦`) on the side of the "Whole" (Container).

Java Implementation:

* Mechanism: Instantiation inside the Constructor. The parent class uses the `new` keyword to create the child object.
* Best Practice: Make the field `final` to enforce that the relationship cannot be changed.

Java

```java
// The Part
class Engine {
    String type;
    public Engine(String type) { this.type = type; }
}

// The Whole
class Car {
    // COMPOSITION: The Car owns this Engine.
    private final Engine engine;

    public Car(String engineType) {
        // The Engine is created INSIDE the Car.
        // If the Car object is garbage collected, the Engine goes with it.
        this.engine = new Engine(engineType);
    }
}
```

***

### 4. Inheritance (IS-A)

"Is-A" Relationship

Inheritance (or Generalization) allows a child class to acquire the properties and methods of a parent class. It is the strongest form of coupling between classes.

* Strength: Very Strong (Static/Compile-time).
* Lifecycle: The Child _is_ the Parent; they share the same lifecycle.
* UML Representation: A solid line with a closed, empty triangle (`△`) pointing to the Parent class.

Java Implementation:

* Mechanism: The `extends` keyword.
* Best Practice: Use Abstract Classes for the parent if you want to provide a common template but force specific behavior implementation in children.

Java

```java
// Abstract Parent
abstract class BankAccount {
    protected double balance;
    
    // Concrete method (Code reuse)
    public void deposit(double amount) {
        balance += amount;
    }
    
    // Abstract method (Forcing implementation)
    public abstract void calculateInterest();
}

// Concrete Child
class SavingsAccount extends BankAccount {
    @Override
    public void calculateInterest() {
        balance += (balance * 0.05); // Specific logic for Savings
    }
}
```

***

### 5. Realisation (Implementation)

"Can-Do" Relationship

Realisation is the relationship between a class and an interface. The class "realizes" (makes real) the behavior defined by the interface. It is about capability, not state.

* Strength: Strong (Contractual).
* Lifecycle: N/A (Interfaces have no state).
* UML Representation: A dashed line with a closed, empty triangle (`- - △`) pointing to the Interface.

Java Implementation:

* Mechanism: The `implements` keyword.
* Best Practice: Use Interfaces to define capabilities (e.g., `Runnable`, `Serializable`, `Flyable`) that unrelated classes can share.

Java

```java
// The Contract
interface Payable {
    void processPayment();
}

// Implementation A
class Invoice implements Payable {
    public void processPayment() {
        System.out.println("Processing Invoice Payment...");
    }
}

// Implementation B (Unrelated class)
class EmployeeSalary implements Payable {
    public void processPayment() {
        System.out.println("Processing Salary Transfer...");
    }
}
```

***

### 6. Dependency (Uses- A)

"Uses-A" Relationship

Dependency is the weakest relationship. It exists when one class temporarily uses another class to perform a specific task. The dependent class does not store the other class as a field.

* Strength: Very Weak (Temporary).
* Lifecycle: The object is usually created and destroyed within the scope of a single method.
* UML Representation: A dashed line with an open arrow (`- - ->`) pointing to the used class.

Java Implementation:

* Mechanism: Method Parameter. Pass the object into the method where it is needed.
* Best Practice: Pass an Interface type as the parameter to allow the method to "use" any implementation of that dependency.

Java

```java
class Logger {
    public void log(String msg) {
        System.out.println("Log: " + msg);
    }
}

class Calculator {
    // DEPENDENCY: Calculator uses Logger temporarily.
    // Logger is NOT a field of Calculator. 
    // It is just passed in to do a job and then forgotten.
    public int add(int a, int b, Logger logger) {
        int result = a + b;
        logger.log("Added " + a + " and " + b);
        return result;
    }
}
```

***

### Summary Table

| **Relationship** | **Keyword/Concept** | **Coupling** | **Implementation Pattern**         |
| ---------------- | ------------------- | ------------ | ---------------------------------- |
| 1. Association   | Links-To            | Weak         | Instance Field (Interface)         |
| 2. Aggregation   | Has-A (Shared)      | Medium       | Constructor Injection (Pass it in) |
| 3. Composition   | Has-A (Owned)       | Strong       | `new` inside Constructor           |
| 4. Inheritance   | Is-A                | Very Strong  | `extends`                          |
| 5. Realization   | Can-Do              | Strong       | `implements`                       |
| 6. Dependency    | Uses-A              | Very Weak    | Method Parameter                   |

***

## Four pillar of OOPs

***

### 1. Encapsulation (The Shield)

"The Shield"

Encapsulation is the practice of bundling data (variables) and methods (functions) into a single unit (class) and restricting direct access to some of an object's components. It is often called "Data Hiding."

* Goal: Protect data from unauthorized or invalid modification.
* UML Representation:
  * Private (`-`): Variables are marked with a minus sign.
  * Public (`+`): Methods are marked with a plus sign.

Java Implementation:

* Keywords: `private`, `public`, `protected`.
* Mechanism:
  1. Mark class variables as `private`.
  2. Provide `public` Getter and Setter methods to access and update the value.

Java

```java
class BankAccount {
    // 1. DATA HIDING: Variables are private
    private double balance;

    public BankAccount(double initialBalance) {
        if (initialBalance > 0) {
            this.balance = initialBalance;
        }
    }

    // 2. CONTROLLED ACCESS: Public Setter with validation logic
    public void deposit(double amount) {
        if (amount > 0) {
            balance += amount;
            System.out.println("Deposited: $" + amount);
        } else {
            System.out.println("Error: Invalid amount.");
        }
    }

    // 3. READ-ONLY ACCESS: Public Getter
    public double getBalance() {
        return balance;
    }
}
```

***

### 2. Inheritance (Blueprint Copy)

Inheritance allows a new class (Subclass/Child) to acquire the properties and behaviors of an existing class (Superclass/Parent). It promotes code reusability and establishes an "Is-A" relationship.

* Goal: Stop rewriting code that already exists.
* UML Representation: A solid line with a closed, empty triangle (`△`) pointing to the Parent class.

Java Implementation:

* Keyword: `extends`.
* Mechanism: The child class automatically gets all `public` and `protected` fields/methods of the parent. It can also add its own.

Java

```java
// Parent Class (The General Concept)
class Vehicle {
    protected String brand = "Ford"; // accessible to subclasses
    
    public void honk() {
        System.out.println("Tuut, tuut!");
    }
}

// Child Class (The Specific Version)
class Mustang extends Vehicle {
    private String modelName = "GT";
    
    public void showSpecs() {
        // Accessing parent variable 'brand' directly
        System.out.println(brand + " " + modelName);
    }
}

public class Main {
    public static void main(String[] args) {
        Mustang myCar = new Mustang();
        myCar.honk();      // Inherited method
        myCar.showSpecs(); // Child's own method
    }
}
```

***

### 3. Polymorphism (Many Forms)

Polymorphism allows objects to be treated as instances of their parent class rather than their actual class. It enables a single interface to control different underlying forms (data types).

* Goal: Flexibility. One method call can behave differently depending on the object it is called on.
* UML Representation: Often depicted via Inheritance (`extends`) or Realization (`implements`) arrows, showing multiple children overriding a parent method.

Java Implementation:

There are two types of Polymorphism in Java:

**A. Compile-Time Polymorphism (Method Overloading)**

Same method name, different parameters (arguments).

Java

```java
class Calculator {
    // Form 1: Two integers
    int add(int a, int b) { return a + b; }
    
    // Form 2: Three integers
    int add(int a, int b, int c) { return a + b + c; }
    
    // Form 3: Doubles
    double add(double a, double b) { return a + b; }
}
```

**B. Runtime Polymorphism (Method Overriding)**

Same method name and parameters, but the Child class changes the logic.

Java

```java
class Animal {
    public void makeSound() {
        System.out.println("Some generic noise");
    }
}

class Dog extends Animal {
    @Override
    public void makeSound() {
        System.out.println("Woof");
    }
}

class Cat extends Animal {
    @Override
    public void makeSound() {
        System.out.println("Meow");
    }
}

public class Main {
    public static void main(String[] args) {
        // Parent Reference = Child Object
        Animal myPet = new Dog();
        myPet.makeSound(); // Output: "Woof" (Decided at Runtime)
        
        myPet = new Cat();
        myPet.makeSound(); // Output: "Meow"
    }
}
```

***

### 4. Abstraction (The Concept)

Abstraction involves hiding the implementation details and showing only the functionality to the user. It lets you focus on _what_ an object does instead of _how_ it does it.

* Goal: Reduce complexity and isolate impact of changes.
* UML Representation:
  * Abstract Class: Name is often italicized or labeled `{abstract}`.
  * Interface: Labeled `<<interface>>`.

Java Implementation:

You can achieve abstraction using Abstract Classes (0-100% abstraction) or Interfaces (100% abstraction).

#### **Method 1: Abstract Class (Partial Abstraction)**

Use when classes share some common code but must implement specific behaviors differently.

Java

```java
abstract class Shape {
    String color;
    
    // Concrete method (Shared logic)
    public void setColor(String c) { this.color = c; }
    
    // Abstract method (No body - Child MUST implement this)
    abstract double calculateArea();
}

class Circle extends Shape {
    double radius;
    public Circle(double r) { this.radius = r; }

    @Override
    double calculateArea() {
        return Math.PI * radius * radius;
    }
}
```

#### **Method 2: Interface (Total Abstraction)**

Use when unrelated classes share a capability (a contract).

Java

```java
interface RemoteControl {
    void powerOn();  // Automatically public and abstract
    void powerOff();
}

class TV implements RemoteControl {
    public void powerOn() { System.out.println("TV turning on..."); }
    public void powerOff() { System.out.println("TV turning off..."); }
}
```

***

#### Summary Table

| **Concept**   | **Keyword(s)**                        | **Key Implementation**        | **Focus**                 |
| ------------- | ------------------------------------- | ----------------------------- | ------------------------- |
| Encapsulation | `private`, `getters/setters`          | Restrict variable access      | Security & Data Integrity |
| Inheritance   | `extends`                             | Parent-Child relationship     | Code Reusability          |
| Polymorphism  | `Override`, `Overload`                | Same name, different behavior | Flexibility               |
| Abstraction   | `abstract`, `interface`, `implements` | Hide internal details         | Reducing Complexity       |

***
