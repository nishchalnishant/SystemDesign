---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals]
---
# OOP Principles

A comprehensive reference covering all key OOP concepts with Java examples, real-world analogies, and the major software design principles (DRY, KISS, YAGNI, Law of Demeter).

---

## Topic Mindmap

```
[OOP Principles — Java Reference]
├── Class and Object
│   ├── Class: blueprint defining fields + methods
│   ├── Object: runtime instance of a class
│   └── new keyword allocates memory and calls constructor
├── Encapsulation
│   ├── private fields + public getters/setters
│   ├── Only expose what external callers need
│   └── Analogy: ATM hides cash mechanics behind deposit/withdraw
├── Abstraction
│   ├── Abstract class: partial implementation, forces subclass to complete
│   ├── Interface: pure contract, no state
│   └── Analogy: TV remote — you press buttons, not circuit logic
├── Inheritance
│   ├── Single: one parent (Animal → Dog)
│   ├── Multilevel: chain (Animal → Mammal → Dog)
│   ├── Hierarchical: one parent, many children (Shape → Circle, Square)
│   └── Java has no multiple class inheritance — use interfaces instead
├── Polymorphism
│   ├── Overriding: subclass redefines parent method (runtime dispatch)
│   ├── Overloading: same name, different parameter signatures (compile-time)
│   └── Dynamic dispatch enables open/closed extension
├── IS-A vs HAS-A
│   ├── IS-A: inheritance — Dog IS-A Animal
│   ├── HAS-A: composition — Car HAS-A Engine
│   └── Prefer HAS-A for flexibility and testability
├── Design Principles
│   ├── DRY: extract repeated logic — one change propagates everywhere
│   ├── KISS: simplest solution that works; complexity is a liability
│   ├── YAGNI: don't add abstractions for hypothetical future use
│   └── Law of Demeter: a.b.c.doX() is a violation — only talk to neighbors
└── Static vs Instance
    ├── static: belongs to the class, shared across all instances
    ├── instance: belongs to an object, per-object state
    └── static methods cannot access instance fields
```

## Key OOP Concepts Covered

1. Class and Object
2. Encapsulation (Data Hiding)
3. Abstraction (Hiding Implementation Details)
4. Inheritance (Single, Multiple, Multilevel, Hierarchical)
5. Polymorphism (Method Overriding and Method Overloading)
6. IS-A Relationship (Inheritance-based)
7. HAS-A Relationship (Composition)
8. Static Methods
9. Magic/Constructor Methods

---

## Java Code with Detailed Comments

```java
// ========================================================
// 1. CLASS & OBJECT
// ========================================================
// A class is a blueprint for creating objects.
// An object is an instance of a class with its own attributes and methods.
// Example: An Animal class with name and species attributes.
class Animal {
    protected String name;    // protected: accessible by subclasses
    protected String species;

    public Animal(String name, String species) {
        this.name = name;
        this.species = species;
    }

    public String makeSound() {
        return "Some generic sound";
    }

    @Override
    public String toString() {
        return this.name + " is a " + this.species;
    }
}

// ========================================================
// 2. ENCAPSULATION (DATA HIDING)
// ========================================================
// Encapsulation restricts direct access to data,
// allowing controlled access through methods (getters/setters).
// Example: A BankAccount where balance is private.
class BankAccount {
    private String accountHolder;
    private double balance;  // Private — cannot be set from outside directly

    public BankAccount(String accountHolder, double balance) {
        this.accountHolder = accountHolder;
        this.balance = balance;
    }

    public String deposit(double amount) {
        if (amount > 0) {
            this.balance += amount;
            return "Deposited " + amount + ", New Balance: " + this.balance;
        }
        return "Invalid deposit amount";
    }

    public String withdraw(double amount) {
        if (0 < amount && amount <= this.balance) {
            this.balance -= amount;
            return "Withdrew " + amount + ", Remaining Balance: " + this.balance;
        }
        return "Insufficient funds";
    }

    public double getBalance() {
        return this.balance;
    }
}

// ========================================================
// 3. ABSTRACTION (HIDING IMPLEMENTATION DETAILS)
// ========================================================
// Abstraction hides complex details and exposes only necessary functionality.
// Abstract classes cannot be instantiated and force subclasses to implement required methods.
abstract class Vehicle {
    public abstract String startEngine();
    public abstract String stopEngine();
}

class Car extends Vehicle {
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
// 4. INHERITANCE (IS-A RELATIONSHIP)
// ========================================================
// Inheritance allows a class (child) to acquire properties and behaviors
// of another class (parent). Promotes code reuse. Dog IS-A Animal.
class Dog extends Animal {
    private String breed;

    public Dog(String name, String breed) {
        super(name, "Dog");
        this.breed = breed;
    }

    @Override
    public String makeSound() {
        return "Bark!";
    }
}

// ========================================================
// 5. MULTIPLE INHERITANCE (via interfaces in Java)
// ========================================================
// A class can implement multiple interfaces.
// Bird IS-A Animal and also CAN Fly.
interface Flying {
    String fly();
}

class Bird extends Animal implements Flying {
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
// 6. MULTILEVEL INHERITANCE
// ========================================================
// A class is derived from another derived class.
// Puppy -> Dog -> Animal
class Puppy extends Dog {
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
// 7. POLYMORPHISM (METHOD OVERRIDING & OVERLOADING)
// ========================================================
// Polymorphism: same method name, different behavior per class.
class Cat extends Animal {
    public Cat(String name) {
        super(name, "Cat");
    }

    @Override
    public String makeSound() {
        return "Meow!";
    }
}

class Lion extends Animal {
    public Lion(String name) {
        super(name, "Lion");
    }

    @Override
    public String makeSound() {
        return "Roar!";
    }
}

// Polymorphism in action: one method, any Animal subtype
class PolymorphismDemo {
    public static String animalSound(Animal animal) {
        return animal.makeSound();
    }
}

// ========================================================
// 8. COMPOSITION (HAS-A RELATIONSHIP)
// ========================================================
// Instead of inheritance, a class contains another class as a field.
// Car HAS-A Engine.
class Engine {
    public String start() {
        return "Engine started";
    }

    public String stop() {
        return "Engine stopped";
    }
}

class CarWithEngine {
    private String model;
    private Engine engine;  // Composition: Car owns an Engine

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
// 9. STATIC METHODS
// ========================================================
// Static methods don't use instance attributes; called without an instance.
class Utility {
    public static String greet() {
        return "Hello, welcome to OOP in Java!";
    }

    public static String describeClass() {
        return "This is the Utility class.";
    }
}

// Method Overloading (compile-time polymorphism)
class MathOperations {
    public int add(int a, int b) {
        return a + b;
    }

    public int add(int a, int b, int c) {
        return a + b + c;
    }
}

// ========================================================
// 10. DEMONSTRATION
// ========================================================
public class Main {
    public static void main(String[] args) {
        Dog dog = new Dog("Buddy", "Golden Retriever");
        Cat cat = new Cat("Whiskers");
        Lion lion = new Lion("Simba");

        // Polymorphism
        System.out.println(PolymorphismDemo.animalSound(dog));   // Bark!
        System.out.println(PolymorphismDemo.animalSound(cat));   // Meow!
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

        // Multiple Inheritance via interface
        Bird bird = new Bird("Eagle", "Bird of Prey");
        System.out.println(bird.fly());

        // Static Methods
        System.out.println(Utility.greet());
        System.out.println(Utility.describeClass());

        // Multilevel Inheritance
        Puppy puppy = new Puppy("Max", "Labrador", 3);
        System.out.println(puppy.isCute());

        // Method Overloading
        MathOperations mathOps = new MathOperations();
        System.out.println(mathOps.add(5, 10));       // 15
        System.out.println(mathOps.add(5, 10, 20));   // 35
    }
}
```

---

## Summary of OOP Concepts

| Concept | Example |
|---|---|
| **Class & Object** | `Animal`, `Dog`, `Car` |
| **Encapsulation** | `BankAccount` with private `balance` |
| **Abstraction** | `Vehicle` abstract class |
| **Inheritance** | `Dog IS-A Animal`, `Bird IS-A Animal` |
| **Polymorphism** | `makeSound()` overridden in `Dog`, `Cat`, `Lion` |
| **Multiple Inheritance** | `Bird` implements `Animal` & `Flying` |
| **Multilevel Inheritance** | `Puppy → Dog → Animal` |
| **Hierarchical Inheritance** | `Dog`, `Cat`, `Lion` all from `Animal` |
| **Method Overloading** | `MathOperations.add()` with different signatures |
| **Static Methods** | `Utility.greet()` |
| **Composition (HAS-A)** | `CarWithEngine HAS-A Engine` |

---

## Software Design Principles

These principles guide how to write maintainable, readable, and scalable object-oriented code. They apply on top of the four pillars.

---

### DRY — Don't Repeat Yourself

#### Analogy: Your Mailing Address

Imagine you write your home address on 10 different government forms. You move to a new city. Now you have to find all 10 forms and update each one. You will probably miss at least one. That out-of-date form causes a problem six months later.

Better approach: one authoritative record of your address. Every form references it. When you move, you update **one place**, and everything stays consistent.

**DRY in code:** Every piece of knowledge should have a **single, authoritative representation** in the system. If you find yourself copying and pasting logic, you are violating DRY.

**Bad — Repeated Logic:**

```java
class OrderService {
    public double calculateTax(double amount) {
        return amount * 0.18;  // Tax logic here
    }
}

class InvoiceService {
    public double calculateTax(double amount) {
        return amount * 0.18;  // Same tax logic duplicated
    }
}
```

If the tax rate changes, you need to find and update every copy. You will miss one.

**Good — Single Source of Truth:**

```java
class TaxCalculator {
    private static final double TAX_RATE = 0.18;

    public static double calculate(double amount) {
        return amount * TAX_RATE;
    }
}

class OrderService {
    public double calculateTax(double amount) {
        return TaxCalculator.calculate(amount);  // One reference
    }
}

class InvoiceService {
    public double calculateTax(double amount) {
        return TaxCalculator.calculate(amount);  // Same reference
    }
}
```

Now, when the tax rate changes, you update exactly one constant in one file.

---

### KISS — Keep It Simple, Stupid

#### Analogy: A Swiss Army Knife vs. a Fixed Blade

A Swiss Army knife is clever. It has a blade, scissors, screwdriver, bottle opener, toothpick, and tweezers all in one. But if you need to cut a thick rope quickly, a simple, fixed-blade knife is better. Fewer moving parts, full grip, full blade length. The Swiss Army knife is over-engineered for this task.

**KISS in code:** Do not add complexity you do not need. The simplest solution that correctly solves the problem is usually the best solution.

**Over-engineered:**

```java
// A "flexible" calculator using Strategy pattern, reflection, and factory
// for the sole purpose of adding two numbers
interface Operation {
    double execute(double a, double b);
}

class AddOperation implements Operation {
    @Override
    public double execute(double a, double b) {
        return a + b;
    }
}

class CalculatorFactory {
    public static Operation getOperation(String type) {
        if (type.equals("ADD")) return new AddOperation();
        throw new IllegalArgumentException("Unknown operation");
    }
}

// Usage: CalculatorFactory.getOperation("ADD").execute(2, 3);
```

**KISS — just add the numbers:**

```java
public double add(double a, double b) {
    return a + b;
}
```

Apply patterns only when the problem genuinely requires them. Premature abstraction creates complexity without value.

---

### YAGNI — You Aren't Gonna Need It

#### Analogy: Don't Build the Garage Yet

You do not own a car. You might buy one in five years. Building a garage right now — spending money, space, and time — for a car you do not have is wasteful. Build the garage when you buy the car.

**YAGNI in code:** Do not implement features until they are actually needed. Speculative generality — building things "just in case" — adds code that must be maintained, tested, and understood, but delivers no current value.

**Violating YAGNI:**

```java
class UserService {
    // Required now
    public User createUser(String name, String email) { ... }

    // "Maybe we'll need multi-tenancy someday"
    public User createUserInTenant(String name, String email, String tenantId) { ... }

    // "Could be useful for enterprise clients"
    public User createUserWithSSOToken(String name, String email, String ssoToken) { ... }

    // "Might need LDAP integration"
    public User createUserFromLDAP(LDAPEntry entry) { ... }
}
```

**YAGNI — implement what is needed:**

```java
class UserService {
    public User createUser(String name, String email) {
        // This is what the system needs right now
        return new User(name, email);
    }
}
```

Add the LDAP integration when an enterprise customer actually requires it. Until then, it is dead code.

---

### Law of Demeter — Don't Talk to Strangers

#### Analogy: Customer Paying at a Store

A customer walks into a store. The cashier says: "Open your wallet, take out the compartment with cards, find the Visa card, and give it to me." That is wrong. The cashier is reaching into the customer's wallet through multiple layers of indirection.

The correct interaction: "That will be $45." The customer handles their own wallet. The cashier does not need to know what is inside it.

**In code:** An object should only call methods on:
1. Itself
2. Objects passed in as parameters
3. Objects it created
4. Its own direct fields

It should **not** call methods on objects returned by other method calls ("chaining through strangers").

**Violation — reaching into strangers:**

```java
// Wrong: cashier is going customer → wallet → money → deduct
public void processPayment(Customer customer, double amount) {
    customer.getWallet().getMoney().deduct(amount);
}
```

This tightly couples `processPayment` to the internal structure of `Customer`, `Wallet`, and `Money`. If `Wallet` is refactored, this breaks.

**Law of Demeter — talk only to your direct friend:**

```java
// Right: tell the customer to pay; the customer handles their own wallet
public void processPayment(Customer customer, double amount) {
    customer.pay(amount);
}

class Customer {
    private Wallet wallet;

    public void pay(double amount) {
        // Customer manages their own wallet
        this.wallet.deduct(amount);
    }
}
```

Now `processPayment` knows nothing about `Wallet`. If the internal structure of `Customer` changes, only `Customer` needs to change.

**Another Example:**

```java
// Violation: chaining through multiple objects
String city = order.getCustomer().getAddress().getCity();

// Better: let Order expose what callers need
String city = order.getCustomerCity();

class Order {
    private Customer customer;

    public String getCustomerCity() {
        return customer.getCity();  // One level of delegation
    }
}
```

---

## Combined Quick Reference

| Principle | Analogy | Rule |
|---|---|---|
| **DRY** | Update your address in 10 forms vs. 1 | Every piece of knowledge has one authoritative representation |
| **KISS** | Fixed blade vs. Swiss Army knife | Simplest solution that works is best; avoid speculative complexity |
| **YAGNI** | Don't build a garage for a car you don't have | Only implement what you need right now |
| **Law of Demeter** | Customer pays — cashier doesn't touch the wallet | Talk to direct friends only; don't chain through strangers |

---

## When Principles Conflict

These principles are guidelines, not absolute rules. There are situations where tension arises:

- **DRY vs. KISS**: Sometimes removing duplication requires introducing an abstraction (a new class or method) that adds complexity. If the abstraction is harder to understand than the duplication, keep the duplication.
- **DRY vs. YAGNI**: Extracting a reusable component "just in case" it is needed elsewhere violates YAGNI, even if it satisfies DRY. Extract when you actually have the second use case, not before.
- **Law of Demeter vs. Fluent APIs**: Builder patterns and fluent APIs intentionally chain calls (e.g., `StringBuilder.append("a").append("b")`). This is acceptable because you are calling methods on the same object returned by each step — not traversing into separate, unrelated objects.

The goal is always **maintainability**: code that a reasonable engineer can understand, change, and test with confidence six months later.
