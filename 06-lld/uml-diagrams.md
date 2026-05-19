# UML Diagrams

Unified modeling language, there are multiple places where this is used.

## Topic Mindmap

```
[UML Diagrams for LLD]
├── Core Concept
│   ├── What → Standardized visual notation for software design
│   └── Why → Communicate class relationships before writing code; lingua franca in interviews
├── Two Categories
│   ├── Structural → what the system IS (static view)
│   │   ├── Class Diagram → classes, attributes, methods, relationships
│   │   ├── Object Diagram → snapshot of instances at runtime
│   │   └── Component/Package Diagrams → module-level structure
│   └── Behavioral → what the system DOES (dynamic view)
│       ├── Sequence Diagram → object interactions over time (message flow)
│       ├── Activity Diagram → workflow/flowchart of steps
│       ├── State Machine → states + transitions (vending machine, order lifecycle)
│       └── Use Case Diagram → actor-system interactions
├── Class Diagram Notation
│   ├── Visibility → + public / - private / # protected / ~ package
│   ├── Association → solid line; A uses B
│   ├── Aggregation → hollow diamond; whole HAS-A part (can exist independently)
│   ├── Composition → filled diamond; whole OWNS part (part dies with whole)
│   ├── Inheritance → hollow triangle arrow; IS-A relationship
│   └── Dependency → dashed arrow; A depends on B (method parameter or return type)
├── Sequence Diagram Notation
│   ├── Lifeline → vertical dashed line per object
│   ├── Activation bar → rectangle on lifeline when object is active
│   ├── Synchronous call → solid arrow with filled head
│   ├── Return → dashed arrow
│   └── Self-call → arrow loops back to own lifeline
├── When to Use
│   ├── ✓ Class diagram for LLD design rounds — show relationships before coding
│   └── ✓ Sequence diagram to explain API call flows or event ordering
├── Real-World Examples
│   ├── BookMyShow LLD → Class diagram: Show, Screen, Seat, Booking, User, Payment
│   └── ATM flow → Sequence diagram: User → ATM → Bank → Account
└── Interview Angles
    ├── Always start with class diagram → enumerate entities, then add relationships
    ├── Composition vs Aggregation → Order HAS-MANY OrderItems (composition); Employee works-in Department (aggregation)
    └── Code challenge: draw class diagram for Parking Lot with inheritance and relationships
```

---

Can be divided into 2 types --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20231222121300/UML-Diagrams.jpg" alt=""><figcaption></figcaption></figure>

Out of all, we mostly use the class digram for LLD.

Class diagrams provide a high-level overview of a system's design, helping to communicate and document the structure of the software.

Class digram --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20240118123645/Class-Notation.webp" alt=""><figcaption></figcaption></figure>

* class Name
* attributes
* Metods/operations
* Visibility notations
  * Visibility notations indicate the access level of attributes and methods. Common visibility notations include:
    * `+` for public (visible to all classes)
    * `-` for private (visible only within the class)
    * `#` for protected (visible to subclasses)
    * `~` for package or default visibility (visible to classes in the same package)

Now if there are multiple classes we can interlink them to make a suitable working scenario. to do that we use notations --

<figure><img src="https://media.geeksforgeeks.org/wp-content/uploads/20240209094815/relationship.webp" alt=""><figcaption></figcaption></figure>

We have following relation ships --

* <mark style="color:$danger;">**Composition**</mark> <mark style="color:$danger;"></mark><mark style="color:$danger;">--</mark>
  * Composition is a stronger form of aggregation, indicating a more significant ownership or dependency relationship.
  * In composition, the part class cannot exist independently of the whole class.
  * ex — Imagine a digital contact book application. The contact book is the whole, and each contact entry is a part. Each contact entry is fully owned and managed by the contact book. If the contact book is deleted or destroyed, all associated contact entries are also removed.
* **Directed** **Association** --
  * A directed association in a UML class diagram represents a relationship between two classes where the association has a direction, indicating that one class is associated with another in a specific way.
  * ex — Consider a scenario where a "Teacher" class is associated with a "Course" class in a university system. The directed association arrow may point from the "Teacher" class to the "Course" class, indicating that a teacher is associated with or teaches a specific course.
* **Usage**(**Dependency**) **Relationship**
  * A usage dependency relationship in a UML class diagram indicates that one class (the client) utilizes or depends on another class (the supplier) to perform certain tasks or access certain functionality.
  * The client class relies on the services provided by the supplier class but does not own or create instances of it.
  * ex — Consider a scenario where a "Car" class depends on a "FuelTank" class to manage fuel consumption.
* **Generalisation (Inheritance)**
  * Inheritance represents an "is-a" relationship between classes, where one class (the subclass or child) inherits the properties and behaviours of another class (the superclass or parent).
  * ex — In the example of bank accounts, we can use generalisation to represent different types of accounts such as current accounts, savings accounts, and credit accounts.
* <mark style="color:$danger;">**Aggregation**</mark>
  * Aggregation is a specialised form of association that represents a "whole-part" relationship.
  * It denotes a stronger relationship where one class (the whole) contains or is composed of another class (the part).
  * Aggregation is represented by a diamond shape on the side of the whole class. In this kind of relationship, the child class can exist independently of its parent class.
  * ex — The company can be considered as the whole, while the employees are the parts.
  * Employees belong to the company, and the company can have multiple employees. However, if the company ceases to exist, the employees can still exist independently
* <mark style="color:$danger;">**Association**</mark>
  * An association represents a bi-directional relationship between two classes. It indicates that instances of one class are connected to instances of another class.
  * Associations are typically depicted as a solid line connecting the classes, with optional arrows indicating the direction of the relationship.
  * ex — Let's consider a simple system for managing a library. In this system, we have two main entities: `Book` and `Library`.
  * Each `Library` contains multiple `Books`, and each `Book` belongs to a specific `Library`. This relationship between `Library` and `Book` represents an association.
* <mark style="color:$danger;">**Dependency**</mark> <mark style="color:$danger;">**Relationship**</mark>
  * A dependency exists between two classes when one class relies on another, but the relationship is not as strong as association or inheritance.
  * It represents a more loosely coupled connection between classes.
  *   Let's consider a scenario where a Person depends on a Book.

      * **Person Class:** Represents an individual who reads a book. The Person class depends on the Book class to access and read the content.
      * **Book Class:** Represents a book that contains content to be read by a person. The Book class is independent and can exist without the Person class.

      > The Person class depends on the Book class because it requires access to a book to read its content. However, the Book class does not depend on the Person class; it can exist independently and does not rely on the Person class for its functionality.
* <mark style="color:$danger;">**Realisation (Interface Implementation)**</mark>
  * Realisation indicates that a class implements the features of an interface. It is often used in cases where a class realizes the operations defined by an interface.
  *   Let's consider the scenario where a "Person" and a "Corporation" both realizing an "Owner" interface.

      * **Owner Interface:** This interface now includes methods such as "acquire(property)" and "dispose(property)" to represent actions related to acquiring and disposing of property.
      * **Person Class (Realisation):** The Person class implements the Owner interface, providing concrete implementations for the "acquire(property)" and "dispose(property)" methods. For instance, a person can acquire ownership of a house or dispose of a car.
      * **Corporation Class (Realization):** Similarly, the Corporation class also implements the Owner interface, offering specific implementations for the "acquire(property)" and "dispose(property)" methods. For example, a corporation can acquire ownership of real estate properties or dispose of company vehicles.

      > Both the Person and Corporation classes realize the Owner interface, meaning they provide concrete implementations for the "acquire(property)" and "dispose(property)" methods defined in the interface.

***

## <mark style="color:$danger;">UML Digrams</mark>

Here’s a comprehensive UML Class Diagram Cheat Sheet with definitions, symbols, and examples for every concept.

***

### <mark style="color:$danger;">1. Association (Uses-A)</mark>

"Links-To" Relationship

* Association is a structural relationship where <mark style="color:$success;">objects of one class are connected to objects of another class.</mark>&#x20;
* It <mark style="color:$success;">indicates that one object knows about or navigates to another.</mark> It can be uni-directional (A knows B) or bi-directional (A and B know each other).
  * Strength: Weak to Medium.
  * Lifecycle: The objects have their own independent lifecycles. There is no ownership involved.
  * UML Representation: A solid line connecting two classes. An open arrow (`------->`) is used to show the direction of navigation.
* Java Implementation:
  * Mechanism: <mark style="color:$success;">Define a field (instance variable) in the class.</mark>
  * Best Practice: <mark style="color:$success;">Use an Interface for the field type to keep the classes loosely coupled.</mark>

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

### <mark style="color:$danger;">2. Aggregation (HAS-A Weak)</mark>

<mark style="color:$success;">"Has-A" (Weak) Relationship</mark>

* Aggregation is a specialised form of Association.&#x20;
* It <mark style="color:$success;">r</mark><mark style="color:$success;">epresents a "Whole-Part" relationship where the part can exist independently of the whole.</mark>&#x20;
* If the <mark style="color:$success;">container (parent) is destroyed, the parts (children) are</mark> <mark style="color:$success;"></mark>_<mark style="color:$success;">not</mark>_ <mark style="color:$success;"></mark><mark style="color:$success;">destroyed.</mark>
  * Strength: Medium.
  * Lifecycle: Independent. The <mark style="color:$success;">Child is created outside and passed into the Parent.</mark>
  * UML Representation: A solid line with an empty diamond (`<>`) on the side of the "Whole" (Container).
* Java Implementation:
  * Mechanism: <mark style="color:$success;">Constructor Injection.</mark> The <mark style="color:$success;">external object is passed as a parameter to the constructor (or a setter method) and stored in a field.</mark>
  * Key Logic: <mark style="color:$success;">Do not use</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`new`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">inside the constructor to create the part</mark><mark style="color:$success;">.</mark>

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

### <mark style="color:$danger;">3. Composition (HAS-A Strong)</mark>

"Has-A" (Strong) Relationship

* Composition is a <mark style="color:blue;">restricted form of Aggregation.</mark> It r<mark style="color:blue;">epresents a "Whole-Part" relationship where the part CANNOT exist without the whole. If the container is destroyed, the parts are destroyed with it.</mark>
  * Strength: Strong.
  * Lifecycle: Dependent. <mark style="color:$success;">The Child's lifecycle is managed strictly by the Parent.</mark>
  * UML Representation: A solid line with a filled diamond (`♦`) on the side of the "Whole" (Container).
* Java Implementation:
  * Mechanism:&#x20;
    * <mark style="color:$success;">Instantiation inside the Constructor.</mark>&#x20;
    * The <mark style="color:$success;">parent class uses the</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`new`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">keyword to create the child object.</mark>
  * Best Practice:&#x20;
    * <mark style="color:$success;">Make the field</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`final`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">to enforce that the relationship</mark> <mark style="color:$success;">cannot be changed.</mark>

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

### <mark style="color:$danger;">4. Inheritance (IS-A)</mark>

"Is-A" Relationship

* Inheritance (or Generalization) <mark style="color:$success;">allows a child class to acquire the properties and methods of a parent class.</mark> It is the <mark style="color:$success;">strongest form of coupling between classes.</mark>
  * Strength: Very Strong (Static/Compile-time).
  * Lifecycle: The Child _is_ the Parent; they share the same lifecycle.
  * UML Representation: A solid line with a closed, empty triangle (`△`) pointing to the Parent class.
* Java Implementation:
  * Mechanism<mark style="color:blue;">:</mark> <mark style="color:$success;">The</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`extends`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">keyword.</mark>
  * Best Practice: <mark style="color:$success;">Use Abstract Classes for the parent if you want to provide a common template but force specific behavior implementation in children.</mark>

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

### <mark style="color:$danger;">5. Realisation (Implementation)</mark>

"Can-Do" Relationship

* Realisation is the <mark style="color:$success;">relationship between a class and an interface.</mark>&#x20;
* The <mark style="color:$success;">class "realizes" (makes real) the behavior defined by the interface.</mark>&#x20;
* It is about capability, not state.
  * Strength: Strong (Contractual).
  * Lifecycle: N/A (Interfaces have no state).
  * UML Representation: A dashed line with a closed, empty triangle (`- - △`) pointing to the Interface.
* Java Implementation:
  * Mechanism: <mark style="color:$success;">The</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`implements`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">keyword.</mark>
  * Best Practice: <mark style="color:$success;">Use Interfaces to define capabilities</mark> (e.g., `Runnable`, `Serializable`, `Flyable`) that unrelated classes can share.

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

### <mark style="color:$danger;">6. Dependency (Uses- A)</mark>

"Uses-A" Relationship

* Dependency is the <mark style="color:blue;">weakest relationship.</mark> It exists when one class temporarily uses another class to perform a specific task. The dependent class does not store the other class as a field.
  * Strength: V<mark style="color:$success;">ery Weak (Temporary).</mark>
  * Lifecycle: The object is <mark style="color:$success;">usually created and destroyed within the scope of a single method.</mark>
  * UML Representation: A dashed line with an open arrow (`- - ->`) pointing to the used class.
* Java Implementation:
  * Mechanism: Method Parameter. <mark style="color:$success;">Pass the object into the method where it is needed.</mark>
  * Best Practice: <mark style="color:$success;">Pass an Interface type as the parameter to allow the method to "use"</mark> any implementation of that dependency.

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

<table data-header-hidden><thead><tr><th width="139.078125"></th><th width="171.76171875"></th><th width="135.09375"></th><th></th></tr></thead><tbody><tr><td><strong>Relationship</strong></td><td><strong>Keyword/Concept</strong></td><td><strong>Coupling</strong></td><td><strong>Implementation Pattern</strong></td></tr><tr><td>Association</td><td>Links-To</td><td>Weak</td><td>Instance Field (Interface)</td></tr><tr><td>Aggregation</td><td>Has-A (Shared)</td><td>Medium</td><td>Constructor Injection (Pass it in)</td></tr><tr><td>Composition</td><td>Has-A (Owned)</td><td>Strong</td><td><code>new</code> inside Constructor</td></tr><tr><td>Inheritance</td><td>Is-A</td><td>Very Strong</td><td><code>extends</code></td></tr><tr><td>Realisation</td><td>Can-Do</td><td>Strong</td><td><code>implements</code></td></tr><tr><td>Dependency</td><td>Uses-A</td><td>Very Weak</td><td>Method Parameter</td></tr></tbody></table>

***

## <mark style="color:purple;">Four pillar of OOPs</mark>

***

### <mark style="color:red;">1. Abstraction (The Concept)</mark>

* Abstraction involves <mark style="color:$success;">hiding the implementation details and showing only the functionality to the user.</mark>&#x20;
* It lets you focus on _what_ an object does instead of _how_ it does it.
* Goal: <mark style="color:$success;">Reduce complexity and isolate impact of changes.</mark>
* UML Representation:
  * <mark style="color:$success;">Abstract Class:</mark> Name is often italicized or labeled `{abstract}`.
  * <mark style="color:$success;">Interface</mark>: Labeled `<<interface>>`.
* Java Implementation:
  * You can achieve abstraction <mark style="color:$success;">using Abstract Classes (0-100% abstraction)</mark> <mark style="color:blue;">or</mark> <mark style="color:$success;">Interfaces (100% abstraction).</mark>

#### <mark style="color:yellow;">**Method 1: Abstract Class (Partial Abstraction)**</mark>

Use when classes share some common code but must implement specific behaviours differently.

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

#### <mark style="color:yellow;">**Method 2: Interface (Total Abstraction)**</mark>

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

### <mark style="color:red;">2. Encapsulation (The Shield)</mark>

"The Shield"

* Encapsulation is the <mark style="color:$success;">practice of bundling data</mark> <mark style="color:$success;">(variables) and methods (functions) into a single unit (class)</mark> and restricting direct access to some of an object's components.&#x20;
* It is <mark style="color:$success;">often called "Data Hiding."</mark>
* Goal: Protect data from unauthorised or invalid modification.
* UML Representation:
  * <mark style="color:$success;">Private (</mark><mark style="color:$success;">`-`</mark><mark style="color:$success;">):</mark> Variables are marked with a minus sign.
  * <mark style="color:$success;">Public (</mark><mark style="color:$success;">`+`</mark><mark style="color:$success;">):</mark> Methods are marked with a plus sign.
* Java Implementation:
  * Keywords: <mark style="color:$success;">`private`</mark><mark style="color:$success;">,</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`public`</mark><mark style="color:$success;">,</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`protected`</mark><mark style="color:$success;">.</mark>
  * Mechanism:
    1. Mark <mark style="color:$success;">class variables as</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`private`</mark><mark style="color:$success;">.</mark>
    2. Provide <mark style="color:$success;">`public`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">Getter and Setter methods to access and update the value.</mark>

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

### <mark style="color:red;">3. Inheritance (Blueprint Copy)</mark>

* Inheritance <mark style="color:$success;">a</mark><mark style="color:$success;">llows a new class (Subclass/Child) to acquire the properties and behaviours of an existing class (Superclass/Parent).</mark>&#x20;

It promotes code reusability and <mark style="color:$success;">establishes an "Is-A" relationship.</mark>

* Goal: <mark style="color:$success;">Stop rewriting code that already exists.</mark>
* UML Representation: A solid line with a closed, <mark style="color:$success;">empty triangle (</mark><mark style="color:$success;">`△`</mark><mark style="color:$success;">) pointing to the Parent class.</mark>

Java Implementation:

* Keyword<mark style="color:$success;">:</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`extends`</mark><mark style="color:$success;">.</mark>
* Mechanism: The <mark style="color:$success;">child class automatically gets all</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`public`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">and</mark> <mark style="color:$success;"></mark><mark style="color:$success;">`protected`</mark> <mark style="color:$success;"></mark><mark style="color:$success;">fields/methods of the parent.</mark>&#x20;
* It can also add its own.

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

### <mark style="color:$danger;">4. Polymorphism (Many Forms)</mark>

* Polymorphism <mark style="color:$success;">allows objects to be treated as instances of their parent class rather than their actual class.</mark>&#x20;
* It enables a single interface to control different underlying forms <mark style="color:blue;">(</mark>data types).
  * Goal: Flexibility. One method call can behave differently depending on the object it is called on.
  * UML Representation: Often depicted via Inheritance (`extends`) or Realization (`implements`) arrows, showing multiple children overriding a parent method.

Java Implementation:

* There are two types of Polymorphism in Java:

<mark style="color:yellow;">**A. Compile-Time Polymorphism (Method Overloading)**</mark>

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

<mark style="color:yellow;">**B. Runtime Polymorphism (Method Overriding)**</mark>

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

#### Summary Table

<table data-header-hidden><thead><tr><th width="139.1796875"></th><th width="191.05078125"></th><th></th><th></th></tr></thead><tbody><tr><td><strong>Concept</strong></td><td><strong>Keyword(s)</strong></td><td><strong>Key Implementation</strong></td><td><strong>Focus</strong></td></tr><tr><td>Encapsulation</td><td><code>private</code>, <code>getters/setters</code></td><td>Restrict variable access</td><td>Security &#x26; Data Integrity</td></tr><tr><td>Inheritance</td><td><code>extends</code></td><td>Parent-Child relationship</td><td>Code Reusability</td></tr><tr><td>Polymorphism</td><td><code>Override</code>, <code>Overload</code></td><td>Same name, different behavior</td><td>Flexibility</td></tr><tr><td>Abstraction</td><td><code>abstract</code>, <code>interface</code>, <code>implements</code></td><td>Hide internal details</td><td>Reducing Complexity</td></tr></tbody></table>

***
