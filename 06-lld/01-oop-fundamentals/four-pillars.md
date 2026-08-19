> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The four pillars of OOP with deep dives, real-world analogies, Java code examples, and design principle implications.
>
> **The four pillars:**
> - Encapsulation: bundle data + methods; private fields, public getters/setters; prevents invalid state; e.g., BankAccount hides `balance`, exposes `deposit()`/`withdraw()`
> - Inheritance: IS-A relationship; child inherits parent's interface and/or implementation; `Dog extends Animal`; avoid deep hierarchies (>2 levels = problem)
> - Polymorphism: same method name, different behavior per class; runtime polymorphism via method overriding; compile-time via overloading; enables `List<Animal>` containing Dogs and Cats
> - Abstraction: hide complexity behind simple interface; abstract classes (partial implementation) and interfaces (pure contract); `PaymentGateway` interface hides Stripe/PayPal details
> - IS-A vs HAS-A: inheritance (IS-A) = tight coupling; composition (HAS-A) = flexible; `Car HAS-A Engine` (not IS-A); prefer composition
> - This file covers: analogies, Java code examples, common mistakes, interview questions
>
> **Key takeaway:** Polymorphism via interfaces is the most interview-relevant pillar — it's how you swap algorithms (Strategy), handle events (Observer), and build extensible systems (OCP); memorize the `Shape.draw()` example.

---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals]
---
# Four Pillars of OOP — Complete Reference

> This is the single authoritative file for OOP concepts: what OOP is, the four pillars, IS-A vs HAS-A, and the software design principles that shape how you apply them.

---

## Topic Mindmap

```
[Four Pillars of OOP]
├── Problem It Solves
│   ├── Global variables readable/writable by 40+ functions — no ownership
│   ├── Changing one thing requires understanding everything
│   └── Fix: group variable + its allowed functions into one bounded unit
├── Encapsulation
│   ├── Hide internal state behind a public interface
│   ├── BankAccount hides balance; only deposit/withdraw can touch it
│   ├── Analogy: hospital patient record — nurse accesses via defined procedure
│   └── Benefit: change internals without breaking callers
├── Abstraction
│   ├── Expose what you need; hide how it works
│   ├── Airline passenger uses book/cancel — not seat-allocation internals
│   ├── Interface/abstract class defines the contract
│   └── Benefit: caller depends on behavior, not implementation
├── Inheritance (IS-A)
│   ├── Subclass inherits fields + methods from superclass
│   ├── Doctor IS-A HospitalStaff — gets scheduleShift(), adds prescribe()
│   ├── Avoid deep hierarchies — prefer composition
│   └── Risk: tight coupling; violating Liskov breaks substitutability
├── Polymorphism
│   ├── One interface, many implementations
│   ├── Waiter.serve(customer) works for DineIn, Takeout, Delivery
│   ├── Compile-time: method overloading (same name, different params)
│   └── Runtime: method overriding + dynamic dispatch
├── IS-A vs HAS-A
│   ├── IS-A → use inheritance (Car IS-A Vehicle)
│   ├── HAS-A → use composition (Car HAS-A Engine)
│   └── Default to HAS-A — more flexible, avoids hierarchy lock-in
├── Design Principles
│   ├── DRY: Don't Repeat Yourself — extract to one place
│   ├── KISS: Keep It Simple — don't over-engineer
│   ├── YAGNI: You Aren't Gonna Need It — don't add unused abstractions
│   └── Law of Demeter: talk to direct collaborators, not their internals
└── Interview Angles
    ├── Why prefer composition over inheritance?
    ├── What breaks when IS-A is misused? (Square/Rectangle, Penguin/Bird)
    ├── Difference between overloading and overriding?
    └── What does encapsulation buy you at the system level?
```

## What is OOP and Why Does It Exist?

**Question**: You are maintaining a 3,000-line program. A bug is reported. The variable causing the bug is read by 40 different functions. Which functions actually write to it? Which should be allowed to? How do you fix the bug without breaking one of the other 39 readers?

**The problem without structure**: Everything is global. Any function can read or write any variable. To understand one piece of code you must understand all of it. There is no boundary. There is no way to reason about what a change affects.

**The minimal fix**: Group a variable with the functions that are allowed to touch it. Put them in a single unit. Hide the variable from the outside. Now "balance" belongs to `BankAccount` — only the methods inside it can modify it. The 3,000-line problem becomes a collection of smaller, bounded problems.

**The full structure**: OOP formalizes this minimal fix into four principles — Encapsulation, Abstraction, Inheritance, Polymorphism — that together make it possible to build, change, and reason about large systems without holding the whole thing in your head at once.

Think of a restaurant kitchen. There is a head chef, a sous-chef, a pastry chef, and a line cook. Each one has a clearly defined role, a defined set of tools, and defined responsibilities. The pastry chef does not need to know how the head chef makes the sauce — she just needs to know that she is responsible for desserts and here are her inputs and outputs. When a new dish is added to the menu, only the relevant station is updated. No one else's workflow breaks.

That is object-oriented programming. Software systems grew too large to manage as a single bag of instructions. OOP gives you a way to divide responsibility, hide complexity, and build systems where one change does not break everything else. You model your system as a collection of **objects** — each with its own data and its own behavior — that collaborate through well-defined interfaces.

---

## Class vs Object

A **class** is a cookie cutter. An **object** is a cookie.

Every cookie made from the same cutter has the same shape — the same methods (behaviors). But each cookie is its own thing: it can have its own chocolate chips, its own icing color, its own level of doneness. That is the state — the data unique to each instance.

The cookie cutter (class) is never a cookie itself. It defines what cookies look like. When you call `new`, you are pressing the cutter into dough and creating an actual, tangible object.

```java
// The cookie cutter — no memory allocated yet
class Car {
    private final String make;
    private final String model;
    private final int year;

    public Car(String make, String model, int year) {
        this.make = make;
        this.model = model;
        this.year = year;
    }

    public void startEngine() {
        System.out.println(make + " " + model + " engine starting.");
    }
}

// Two cookies from the same cutter — same shape, different state
Car civic  = new Car("Honda", "Civic",   2022);
Car model3 = new Car("Tesla", "Model 3", 2023);
```

---

## The Four Pillars

---

## 1. Encapsulation

**Question**: You have a `BankAccount` class with a `balance` field. Ten different places in your code read and update it directly. A bug report says balances are going negative. Which of those ten places is the culprit? How do you prevent it from happening again?

**Problem without encapsulation**: Anyone can do `account.balance -= 500`. There is no single place to enforce "balance can never go negative." The validation logic gets copy-pasted wherever `balance` is modified — and inevitably one copy is missed.

**Minimal fix**: Make `balance` private. Provide a `withdraw()` method. Now there is exactly one place where the "no negative balance" rule lives. Fix it there, it is fixed everywhere.

**Full pattern**: This is Encapsulation — bundle data with the methods that enforce its invariants. Every class with meaningful state should have private fields and public methods that control access.

### Real-life analogy

You walk into a hospital. A nurse checks your blood pressure. She does not hand you the sphygmomanometer and tell you to read the mercury column yourself. She operates the device, reads the result, and tells you the number. The internal mechanism — the rubber bulb, the pressure gauge, the cuff valve — is hidden behind her professional interface.

If you were allowed to directly manipulate the device's internals, you could break it. Or you could get a wrong reading and not know why. Encapsulation prevents this: you interact with the interface, and the internals are protected.

In code: private fields are the internal mechanism. Public methods are the nurse's professional interface.

### Definition

Encapsulation is the bundling of data (fields) and the methods that operate on that data into a single unit (a class), while restricting direct external access to the internal state. Callers interact through a controlled public interface, which enforces invariants and prevents invalid state.

### Example

```java
class BankAccount {
    private final String accountHolder;
    private double balance;  // Private field — the internal mechanism, not directly touchable

    public BankAccount(String accountHolder, double initialBalance) {
        this.accountHolder = accountHolder;
        this.balance = initialBalance;
    }

    // Public interface — the nurse's controlled interaction
    public String deposit(double amount) {
        if (amount > 0) {
            balance += amount;
            return "Deposited " + amount + ". New balance: " + balance;
        }
        return "Invalid deposit amount";
    }

    public String withdraw(double amount) {
        if (amount > 0 && amount <= balance) {
            balance -= amount;
            return "Withdrew " + amount + ". Remaining balance: " + balance;
        }
        return "Insufficient funds";
    }

    public double getBalance() {
        return balance;  // Read access — no write access without validation
    }
}
```

No external code can do `account.balance = -999999` — the field is private. The business rules (no overdraft, no negative deposit) live inside the class, enforced by every path that touches the data.

### When to use in interviews

Whenever you design a class with data that has invariants (balance can't go negative, age must be positive, password must be hashed), use encapsulation: make the field private and enforce the rule in the setter or method. Interviewers look for this in LLD rounds — a `User` class with a public `password` field is a red flag.

---

## 2. Abstraction

**Question**: You have a `NotificationService` that sends emails. Now you need it to also send SMS. Then push notifications. How do you add these without the caller having to change every time a new channel is added?

**Problem without abstraction**: The caller calls `emailService.sendEmail(to, body)`. To add SMS, the caller must now also call `smsService.sendSms(phone, body)`. The caller knows the implementation details of every notification channel. Add a fourth channel and the caller changes again.

**Minimal fix**: Define an interface `NotificationChannel` with a single method `send(recipient, message)`. The caller depends on the interface — not on EmailService or SMSService. Add a new channel by implementing the interface. The caller does not change.

**Full pattern**: This is Abstraction — expose what an object does (the interface), hide how it does it (the implementation). The caller reasons about the "what." The implementer owns the "how."

### Real-life analogy

You fly from New York to London. You book a seat, show up, sit down, and arrive. You did not write the flight plan, calculate fuel load, or configure the autopilot. The airline exposed to you exactly what you needed: a seat, a departure time, an arrival time. Everything else — the hydraulic systems, the air traffic control protocols, the fuel calculations — was abstracted away.

Abstraction is about what you expose, not what you hide. The contract says: "give me a destination, I'll get you there." How is none of your business.

### Definition

Abstraction is the process of exposing only the essential interface of a system and hiding the complex implementation details behind it. It lets callers reason about **what** an object does without needing to understand **how** it does it.

**Key distinction from Encapsulation:**
- Encapsulation is about *protecting* internal state (hiding data).
- Abstraction is about *simplifying* a system by hiding implementation (hiding complexity behind a clean interface).

A TV remote both encapsulates its circuit board (you cannot open it) and abstracts the IR signal protocol (you do not know it exists).

### Example

```java
// The abstract class defines WHAT operations exist — not HOW they work
// This is the airline's booking interface: "give me a destination"
abstract class Shape {
    abstract double area();
    abstract double perimeter();
}

// Each shape knows HOW to fulfill the contract
class Rectangle extends Shape {
    private final double width;
    private final double height;

    Rectangle(double width, double height) {
        this.width = width;
        this.height = height;
    }

    @Override double area()      { return width * height; }
    @Override double perimeter() { return 2 * (width + height); }
}

class Circle extends Shape {
    private final double radius;

    Circle(double radius) {
        this.radius = radius;
    }

    @Override double area()      { return Math.PI * radius * radius; }
    @Override double perimeter() { return 2 * Math.PI * radius; }
}
```

The caller just calls `shape.area()`. It does not know or care about `math.pi` or `width * height`. The abstraction holds.

### When to use in interviews

Use abstract classes when you are defining a contract that multiple concrete types must fulfill — payment methods (CreditCard, UPI, Wallet), notification channels (Email, SMS, Push), storage backends (S3, GCS, LocalDisk). The caller depends on the abstraction, not the concrete implementation. This is what makes the system extensible.

---

## 3. Inheritance

**Question**: You have `Employee`, `Manager`, and `Intern` classes. All three have `name`, `email`, `clockIn()`, and `clockOut()`. You are copying those four fields/methods into each class. The HR system changes the clock-in logic. How many places do you update?

**Problem without inheritance**: Three. And if you miss one, `Intern` still uses the old clock-in logic while `Manager` uses the new one. The duplication is a maintenance liability — every change must be made N times.

**Minimal fix**: Extract the shared code into a parent class `Employee`. `Manager` and `Intern` extend it. The clock-in logic lives in one place. Change it once, all roles pick it up.

**Full pattern**: This is Inheritance — the parent captures shared state and behavior; children specialize it. Use it only for genuine IS-A relationships. Overuse creates fragile hierarchies.

### Real-life analogy

A general hospital employs doctors, nurses, and administrators. All of them are hospital staff: they all have an employee ID, a salary, and they all clock in and clock out. But a doctor also has a medical license number, can prescribe medication, and can order lab tests. A nurse can administer medication and record vitals. An administrator handles billing and scheduling.

Each role *inherits* the general staff behavior (clock in, get paid) and *extends* it with role-specific behavior. If the hospital changes the clock-in procedure, every role gets the update automatically — you change it once in "Staff", not once in "Doctor", once in "Nurse", once in "Administrator."

This is inheritance: the parent captures shared behavior, the children specialize.

### Definition

Inheritance is a mechanism where a new class (subclass/child) derives from an existing class (superclass/parent). The subclass inherits the fields and methods of the parent, enabling code reuse, and establishes a hierarchical **IS-A relationship**: `Dog IS-A Animal`, `Car IS-A Vehicle`, `Doctor IS-A Staff`.

### Example

```java
// The base: all animals share these behaviors
class Animal {
    protected final String name;

    Animal(String name) {
        this.name = name;
    }

    void breathe() {
        System.out.println(name + " is breathing.");
    }

    String makeSound() {
        return "Some generic sound";
    }
}

// Dog IS-A Animal — inherits breathe(), specializes makeSound()
class Dog extends Animal {
    private final String breed;

    Dog(String name, String breed) {
        super(name);
        this.breed = breed;
    }

    @Override
    String makeSound() {
        return "Bark!";
    }
}

// Labrador IS-A Dog IS-A Animal — inherits everything, adds retrieve()
class Labrador extends Dog {
    Labrador(String name) {
        super(name, "Labrador");
    }

    void retrieve() {
        System.out.println(name + " fetches and returns the ball.");
    }
}
```

`myLabrador.breathe()` works even though `Labrador` never defines `breathe()`. It travels up the chain: `Labrador → Dog → Animal`.

### Types of Inheritance

| Type | Description | Example |
|------|-------------|---------|
| **Single** | One parent, one child | `Car extends Vehicle` |
| **Multilevel** | Chain of inheritance | `Labrador → Dog → Animal` |
| **Hierarchical** | One parent, multiple children | `Dog`, `Cat`, `Lion` all extend `Animal` |
| **Multiple** | Multiple parents — via interfaces in Java | `Bird extends Animal implements Flying` |

### When to use in interviews

Use inheritance when there is a genuine IS-A relationship and the child truly is a specialized version of the parent. Avoid using it just to reuse code — that is what composition is for (see below). In interviews: `PaymentMethod → CreditCardPayment`, `Notification → EmailNotification`, `Repository → UserRepository` are reasonable inheritance hierarchies. `Car extends Engine` is not — a car is not an engine.

---

## 4. Polymorphism

**Question**: You have a list of shapes — circles, rectangles, triangles — and you need to compute the total area. You write a loop. Do you need an `if (shape instanceof Circle)` check, or can the loop stay clean?

**Problem without polymorphism**: You need an `if/else` or `switch` block. Add a new shape type? Add another branch. The loop that computes the total now knows about every concrete shape. Adding `Triangle` means modifying the loop — and every other loop that does anything with shapes.

**Minimal fix**: Every shape overrides `area()`. The loop calls `shape.area()`. It does not know what kind of shape it is. Add `Triangle` by implementing `area()` in the new class — the loop does not change.

**Full pattern**: This is Runtime Polymorphism — the JVM resolves the right `area()` method at runtime based on the actual object type. The caller writes to the interface; the implementation takes care of itself.

### Real-life analogy

A waiter at a restaurant takes orders from every table. When he calls out "Table 4, order up!", he does not need to know whether table 4 ordered pasta, steak, or salad. He just delivers the plate. Each dish responds to "serve it" in its own way — pasta gets twirled and plated, steak gets sliced and rested, salad gets tossed. The waiter's action is identical. The result differs based on what is actually on the plate.

One interface (`serve()`), many behaviors depending on the actual type. That is polymorphism.

### Definition

Polymorphism means "many forms." It is the ability of different objects to respond to the same method call in different ways. Two types:

- **Compile-time polymorphism** (Method Overloading): same method name, different parameter signatures — resolved at compile time.
- **Runtime polymorphism** (Method Overriding): subclass provides its own implementation of a parent's method — resolved at runtime based on the actual object type.

### Example — Runtime Polymorphism

```java
abstract class Shape {
    abstract double area();
}

class Circle extends Shape {
    private final double radius;
    Circle(double radius) { this.radius = radius; }
    @Override double area() { return Math.PI * radius * radius; }
}

class Rectangle extends Shape {
    private final double width, height;
    Rectangle(double width, double height) { this.width = width; this.height = height; }
    @Override double area() { return width * height; }
}

class Triangle extends Shape {
    private final double base, height;
    Triangle(double base, double height) { this.base = base; this.height = height; }
    @Override double area() { return 0.5 * base * height; }
}

List<Shape> shapes = List.of(new Circle(5), new Rectangle(4, 6), new Triangle(3, 8));

double total = 0.0;
for (Shape s : shapes) {
    // Same call — s.area() — different behavior per type
    // The JVM resolves the right implementation at runtime (dynamic dispatch)
    total += s.area();
}
System.out.println("Total area: " + total);
```

The loop has no `instanceof` checks. It does not need to know the actual type. It just calls `area()` and trusts each object to do the right thing.

### Example — Compile-time Polymorphism (Overloading)

Java supports true method overloading — multiple methods with the same name and different parameter signatures, resolved at compile time:

```java
class Calculator {
    int add(int a, int b) { return a + b; }
    int add(int a, int b, int c) { return a + b + c; }
    double add(double a, double b) { return a + b; }
}

// calculator.add(5, 10)       → 15
// calculator.add(5, 10, 20)   → 35
// calculator.add(1.5, 2.5)    → 4.0
```

### When to use in interviews

Polymorphism is the mechanism that makes extensibility real. When an interviewer asks "how would you add a new payment method?", the answer is: "I add a new class that implements the `PaymentMethod` interface. The payment processor loop already calls `payment.process()` — it picks up the new type automatically." No changes to existing code. That is the Open/Closed Principle in action, enabled by polymorphism.

---

## IS-A vs HAS-A: Inheritance vs Composition

### IS-A (Inheritance)

A `Dog` IS-A `Animal`. A `SavingsAccount` IS-A `BankAccount`. The child is a specialized version of the parent. Use inheritance here.

### HAS-A (Composition)

A `Car` HAS-A `Engine`. A `House` HAS-A `Kitchen`. An `Order` HAS-A `Customer`. One object *contains* another — it is not a specialized version of it. Use composition here.

```java
// Inheritance: Car IS-A Vehicle  ✓
class Car extends Vehicle { /* ... */ }

// Composition: Car HAS-A Engine  ✓
class Car {
    private final String model;
    private final Engine engine;  // Car owns an Engine — does not inherit from it

    Car(String model) {
        this.model = model;
        this.engine = new Engine();
    }

    String start() {
        return engine.start();  // Delegate to the Engine
    }
}

class Engine {
    String start() { return "Engine running."; }
    String stop()  { return "Engine off."; }
}
```

### When to prefer composition over inheritance

Prefer composition when:

1. **The relationship is "has-a", not "is-a".** A `Logger` is not a `File`. A `Report` is not a `Printer`. Using inheritance here produces fragile, misleading class hierarchies.
2. **You want to swap implementations at runtime.** You cannot swap your parent class at runtime, but you can swap a composed field. A `PaymentService` composed with a `PaymentGateway` interface can switch between Stripe, PayPal, or Razorpay without changing the service.
3. **The parent class is not designed for extension.** Inheriting from a class to reuse a single helper method couples you to the entire class — including future changes to it.
4. **You need multiple unrelated behaviors.** Java does not allow multiple class inheritance. Composition lets you combine behaviors from multiple sources without the diamond-problem risk.

Rule of thumb: **"Favor composition over inheritance"** (GoF, Effective Java). Start with composition. Use inheritance only when the IS-A relationship is genuine and stable.

---

## Software Design Principles

These principles sit on top of the four pillars. They guide how to write OOP code that is maintainable, readable, and easy to change.

---

### DRY — Don't Repeat Yourself

Imagine you move to a new city and update your address on 10 separate government forms. Six months later, one of those forms still has your old address and causes a problem. The fix was obvious: keep your address in one authoritative record and reference it from everywhere. Update once, every reference reflects the change.

**DRY in code:** every piece of knowledge should have a single, authoritative representation. When you copy-paste logic, you create multiple "forms" that will diverge.

```java
// Violation: tax logic duplicated in two services
class OrderService {
    double calculateTax(double amount) { return amount * 0.18; }
}

class InvoiceService {
    double calculateTax(double amount) { return amount * 0.18; }
}

// DRY: one authoritative source
class TaxCalculator {
    static final double TAX_RATE = 0.18;
    static double calculateTax(double amount) { return amount * TAX_RATE; }
}

class OrderService {
    double calculateTax(double amount) { return TaxCalculator.calculateTax(amount); }
}

class InvoiceService {
    double calculateTax(double amount) { return TaxCalculator.calculateTax(amount); }
}
```

When the tax rate changes, you update one constant. Not five files.

---

### KISS — Keep It Simple, Stupid

A Swiss Army knife has a blade, scissors, screwdriver, bottle opener, and toothpick. If you need to cut a rope, a single fixed-blade knife is better. Fewer parts, full grip, full blade. The Swiss Army knife is over-engineered for this task.

**KISS in code:** the simplest solution that correctly solves the problem is usually the best solution. Do not reach for patterns, abstractions, and frameworks until the problem genuinely requires them.

```java
// Over-engineered: Strategy pattern + Factory just to add two numbers
interface Operation {
    double execute(double a, double b);
}

class AddOperation implements Operation {
    @Override public double execute(double a, double b) { return a + b; }
}

class CalculatorFactory {
    static Operation getOperation(String opType) {
        if (opType.equals("ADD")) {
            return new AddOperation();
        }
        throw new IllegalArgumentException("Unknown operation: " + opType);
    }
}

// KISS:
static double add(double a, double b) {
    return a + b;
}
```

Apply patterns when the problem requires extensibility or multiple implementations. Not before.

---

### YAGNI — You Aren't Gonna Need It

You do not own a car. You might buy one in five years. Building a garage now — spending money, time, and space — for a car you do not yet have is wasteful. Build the garage when you buy the car.

**YAGNI in code:** do not implement features until they are actually needed. Speculative code — built "just in case" — must still be maintained, tested, and understood by every engineer who reads it, while delivering zero current value.

```java
// Violation: implementing features for requirements that do not exist yet
class UserService {
    User createUser(String name, String email) { /* ... */ return null; }                     // needed now
    User createUserInTenant(String name, String email, String tenantId) { /* ... */ return null; }  // maybe someday
    User createUserFromLdap(Object ldapEntry) { /* ... */ return null; }                       // hypothetical enterprise need
}

// YAGNI:
class UserService {
    User createUser(String name, String email) {
        return new User(name, email);
    }
}
```

Add the LDAP integration when an enterprise customer actually requires it. Until then, it is dead code.

---

### Law of Demeter — Don't Talk to Strangers

A customer walks to the checkout. The cashier says: "Open your wallet, find the card compartment, take out the Visa card, and hand it to me." That is wrong. The cashier is reaching through the customer into the wallet into the card compartment. That is three levels of indirection the cashier has no business knowing about.

The correct interaction: "That will be $45." The customer handles their own wallet. The cashier does not need to know what is inside it.

**Law of Demeter in code:** an object should only call methods on (1) itself, (2) objects passed in as parameters, (3) objects it created, (4) its own direct fields. It should not chain through returned objects — that traverses "strangers."

```java
// Violation: cashier reaching into customer → wallet → money
void processPayment(Customer customer, double amount) {
    customer.getWallet().getMoney().deduct(amount);
}
// If Wallet is refactored, this breaks — and this code had no right to know about Wallet.

// Law of Demeter: talk to your direct friend only
void processPayment(Customer customer, double amount) {
    customer.pay(amount);  // Tell the customer to pay — they handle their own wallet
}

class Customer {
    private final Wallet wallet = new Wallet();

    void pay(double amount) {
        wallet.deduct(amount);
    }
}
```

Another common violation:

```java
// Violation
String city = order.getCustomer().getAddress().getCity();

// Better: Order exposes what callers need, hides its internal structure
String city = order.getCustomerCity();

class Order {
    private final Customer customer;

    Order(Customer customer) {
        this.customer = customer;
    }

    String getCustomerCity() {
        return customer.getCity();
    }
}
```

---

## Quick Revision Table

| Concept | Real-world Analogy | Core Rule |
|---|---|---|
| **Class** | Cookie cutter | Blueprint — no memory until instantiated |
| **Object** | A cookie | Instance with its own state |
| **Encapsulation** | Hospital nurse operating a device | Private fields + public controlled interface |
| **Abstraction** | Airline passenger — books seat, not the flight plan | Expose what, hide how |
| **Inheritance** | Hospital staff roles (IS-A) | Child reuses and specializes parent; IS-A |
| **Polymorphism** | Waiter calling "serve" to any dish | One interface, many implementations |
| **Composition** | Car HAS-A Engine | Prefer over inheritance for has-a relationships |
| **DRY** | One authoritative address record | Every piece of knowledge in one place |
| **KISS** | Fixed blade over Swiss Army knife | Simplest solution that works |
| **YAGNI** | Don't build the garage yet | Only build what is needed now |
| **Law of Demeter** | Cashier asks customer to pay — not their wallet | Talk to direct friends only |

---

## When These Principles Conflict

These are guidelines, not commandments. Know when tension arises:

- **DRY vs KISS**: removing duplication sometimes requires an abstraction that adds complexity. If the abstraction is harder to understand than the duplication, keep the duplication.
- **DRY vs YAGNI**: extracting a reusable component "just in case" it is needed elsewhere violates YAGNI. Extract when you actually have the second use case — not before.
- **Law of Demeter vs Fluent APIs**: builder patterns and fluent APIs intentionally chain calls (`StringBuilder.append("a").append("b")`). This is acceptable because each call returns the same object — not a different, unrelated object you are traversing into.

The goal is always **maintainability**: code that a reasonable engineer can understand, change, and test with confidence six months later.

---

> For Java-specific syntax, access modifiers, and advanced patterns: see [java-oops.md](java-oops.md)
> For a Java OOP syntax cheat sheet (classes, access modifiers, equals/hashCode, inheritance): see [python-oops.md](python-oops.md)

---

## Applied In

This concept is used by **36 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Parking Lot](../06-problems/01-core-problems/01-design-parking-lot.md)
- [Design a Rate Limiter](../06-problems/01-core-problems/02-design-rate-limiter.md)
- [Design Tic-Tac-Toe](../06-problems/01-core-problems/03-design-tic-tac-toe.md)
- [Design a Vending Machine](../06-problems/01-core-problems/04-design-vending-machine.md)
- [Design Splitwise](../06-problems/01-core-problems/05-design-splitwise.md)
- [Design BookMyShow](../06-problems/02-frequent-problems/06-design-bookmyshow.md)
- [Design Chess](../06-problems/02-frequent-problems/07-design-chess.md)
- [Design Snake and Ladder](../06-problems/02-frequent-problems/08-design-snake-and-ladder.md)
- …and 28 more

