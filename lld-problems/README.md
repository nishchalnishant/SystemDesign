# LLD Interview Problems Catalog

> **Low-Level Design patterns and problems for object-oriented design interviews**

## Structure

This folder contains organized resources for Low-Level Design (LLD) interviews:

```
4. LLD Interview Problems/
├── design-patterns/       → GOF patterns with examples
├── SOLID-principles/      → SOLID with real code examples
├── concurrency/           → Thread-safe design, locks, async patterns
└── problems/              → Classic LLD interview problems
```

---

## Common LLD Interview Problems

### **Easy** (Object-Oriented Basics)

| Problem | Key Concepts | Estimated Time |
|---------|--------------|----------------|
| **Library Management** | Classes, inheritance, composition | 45 min |
| **Parking Lot** | Enums, polymorphism, design patterns (see template) | 60 min |
| **Vending Machine** | State pattern, finite state machine | 45 min |
| **ATM Design** | State pattern, transaction handling | 45 min |
| **Elevator System** | Strategy pattern, scheduling algorithms | 60 min |

### **Medium** (Design Patterns & Concurrency)

| Problem | Key Concepts | Estimated Time |
|---------|--------------|----------------|
| **Online Shopping Cart** | Observer, Singleton, Factory | 60 min |
| **Chess Game** | Command pattern, game state management | 60 min |
| **Hotel Booking System** | Concurrency (double booking), distributed locking | 60 min |
| **Ride-Sharing (Uber Driver Matching)** | Strategy, Observer, geospatial matching | 75 min |
| **Snake & Ladder** | Game loop, event handling | 45 min |
| **Splitwise (Expense Sharing)** | Graph algorithms, debt simplification | 60 min |

### **Hard** (Complex Systems)

| Problem | Key Concepts** | Estimated Time |
|---------|--------------|----------------|
| **LRU Cache** | HashMap + Doubly Linked List, O(1) operations | 45 min |
| **In-Memory File System** | Tree structure, path parsing, composite pattern | 60 min |
| **Design Pattern: Implement Kafka Consumer** | Iterator, concurrency, backpressure | 75 min |
| **Rate Limiter (Token Bucket)** | Thread-safe counters, sliding window | 60 min |
| **Pub-Sub System** | Observer, topic management, thread pools | 75 min |

---

## Design Patterns Coverage

### Creational Patterns

**1. Singleton**
```
Problem: Only one instance of a class (Database connection pool)
Use in: Parking Lot, Logger, Configuration Manager
```

**2. Factory**
```
Problem: Create objects without specifying exact class
Use in: Vehicle creation (Car, Bike, Truck), Payment methods
```

**3. Builder**
```
Problem: Complex object construction
Use in: Order Builder, Query Builder
```

### Structural Patterns

**4. Adapter**
```
Problem: Incompatible interfaces
Use in: Legacy system integration, payment gateways
```

**5. Decorator**
```
Problem: Add responsibilities dynamically
Use in: Pizza toppings, coffee customization
```

**6. Composite**
```
Problem: Tree structures (part-whole hierarchy)
Use in: File system, organizational chart
```

### Behavioral Patterns

**7. Strategy**
```
Problem: Interchangeable algorithms
Use in: Pricing strategies, sorting algorithms, payment methods
```

**8. Observer**
```
Problem: One-to-many dependency (event notification)
Use in: Stock price updates, news feed subscriptions
```

**9. State**
```
Problem: Object behavior changes based on state
Use in: Vending machine, ATM, order status
```

**10. Command**
```
Problem: Encapsulate request as object
Use in: Undo/Redo, transaction history, remote control
```

---

## SOLID Principles

### S - Single Responsibility Principle
```
A class should have one, and only one, reason to change.

Bad: class User { login(), sendEmail(), generateReport() }
Good: class UserAuth { login() }
      class EmailService { sendEmail() }
      class ReportGenerator { generateReport() }
```

### O - Open/Closed Principle
```
Classes should be open for extension, closed for modification.

Bad: Adding new payment method requires modifying PaymentProcessor
Good: Create new class implementing PaymentMethod interface
```

### L - Liskov Substitution Principle
```
Subtypes must be substitutable for their base types.

Bad: class Penguin extends Bird { fly() { throw Exception } }
Good: class Bird { }
      class FlyingBird extends Bird { fly() }
      class Penguin extends Bird { swim() }
```

### I - Interface Segregation Principle
```
Clients should not depend on interfaces they don't use.

Bad: interface Animal { fly(), swim(), walk() }
     class Dog implements Animal { fly() { /* not applicable */ } }
Good: interface Flyable { fly() }
      interface Swimmable { swim() }
      class Bird implements Flyable { }
      class Fish implements Swimmable { }
```

### D - Dependency Inversion Principle
```
Depend on abstractions, not concretions.

Bad: class PaymentProcessor {
       StripePayment stripe = new StripePayment();
     }
Good: class PaymentProcessor {
       PaymentGateway gateway;  // Interface
       PaymentProcessor(PaymentGateway g) { this.gateway = g; }
     }
```

---

## Concurrency Patterns

### 1. Thread-Safe Singleton
```java
class ThreadSafeSingleton {
    private static volatile ThreadSafeSingleton instance;
    
    private ThreadSafeSingleton() {}
    
    public static ThreadSafeSingleton getInstance() {
        if (instance == null) {
            synchronized (ThreadSafeSingleton.class) {
                if (instance == null) {
                    instance = new ThreadSafeSingleton();
                }
            }
        }
        return instance;
    }
}
```

### 2. Producer-Consumer (Blocking Queue)
```java
class ProducerConsumer {
    BlockingQueue<Task> queue = new ArrayBlockingQueue<>(100);
    
    class Producer implements Runnable {
        public void run() {
            while (true) {
                Task task = createTask();
                queue.put(task);  // Blocks if queue full
            }
        }
    }
    
    class Consumer implements Runnable {
        public void run() {
            while (true) {
                Task task = queue.take();  // Blocks if queue empty
                process(task);
            }
        }
    }
}
```

### 3. Read-Write Lock
```java
class DataStore {
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private Map<String, String> data = new HashMap<>();
    
    public String read(String key) {
        rwLock.readLock().lock();
        try {
            return data.get(key);
        } finally {
            rwLock.readLock().unlock();
        }
    }
    
    public void write(String key, String value) {
        rwLock.writeLock().lock();
        try {
            data.put(key, value);
        } finally {
            rwLock.writeLock().unlock();
        }
    }
}
```

---

## Interview Approach (Use LLD Template!)

Follow the [`lld-template.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/lld-template.md) for structured interviews:

1. **Requirements** (5 min): Clarify actors, features
2. **Use Cases** (5 min): Define primary user flows
3. **Class Diagram** (15 min): Identify classes, attributes, relationships
4. **Design Patterns** (10 min): Apply appropriate patterns
5. **Code** (20 min): Implement 2-3 key methods
6. **Discussion** (5 min): Trade-offs, extensions, concurrency

---

## Practice Roadmap

### Week 1: SOLID + Easy Problems
- [ ] Review SOLID principles
- [ ] Parking Lot (use template)
- [ ] ATM Design
- [ ] Library Management

### Week 2: Design Patterns + Medium Problems
- [ ] Study: Singleton, Factory, Strategy, Observer
- [ ] Shopping Cart
- [ ] Chess Game
- [ ] Hotel Booking

### Week 3: Concurrency + Hard Problems
- [ ] Thread-safe Singleton
- [ ] LRU Cache
- [ ] Rate Limiter
- [ ] Pub-Sub System

---

## Resources

- **LLD Template**: [`/5. Interview Templates/lld-template.md`](file:///Users/nishchalnishant/Documents/GitHub/SystemDesign/5.%20Interview%20Templates/lld-template.md)  
- **Design Patterns**: `design-patterns/` folder (to be populated)  
- **SOLID Examples**: `SOLID-principles/` folder (to be populated)  
- **Concurrency**: `concurrency/` folder (to be populated)

---

## Interview Tips

**Do's:**
- ✅ Ask clarifying questions (actors, core features, constraints)
- ✅ Draw class diagram before coding
- ✅ Explain design pattern choices ("I use Strategy because...")
- ✅ Write clean, readable code with meaningful names
- ✅ Discuss trade-offs (Singleton vs Dependency Injection)

**Don'ts:**
- ❌ Jump into coding without design
- ❌ Use too many patterns (KISS - Keep It Simple)
- ❌ Forget edge cases (null checks, concurrency)
- ❌ Create God classes (violates Single Responsibility)

**Time Allocation:**
- Design (class diagram): 40% of time
- Coding (key methods): 40% of time
- Discussion (trade-offs, extensions): 20% of time

Good luck! 🚀
