> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Java OOP syntax reference for LLD interviews — classes, records, interfaces, abstract classes, getters/setters, `equals()`/`hashCode()`, and Java-specific patterns.
>
> **Key topics:**
> - Class structure: explicit constructors; `this` refers to the current instance; `private`/`protected`/`public` access modifiers enforced by the compiler
> - Encapsulation: private fields with public getters/setters; validation logic lives in the setter — the Java equivalent of Python's `@property`
> - Static vs instance members: `static` methods/fields belong to the class; instance methods/fields belong to each object; static factory methods as alternative constructors
> - Interfaces & abstract classes: `interface Shape { double area(); }` or `abstract class Shape { abstract double area(); }`; enforce a contract that concrete subclasses must implement
> - Object methods: `toString()` (string representation), `equals()`/`hashCode()` (equality contract), `compareTo()` (ordering via `Comparable`), iteration via `Iterable`/`Iterator`
> - Records: `record Point(int x, int y) {}` auto-generates constructor, accessors, `equals()`, `hashCode()`, `toString()`; great for immutable value objects
> - Multiple inheritance: Java disallows multiple class inheritance; a class can implement multiple interfaces instead; prefer composition over deep hierarchies
>
> **Key takeaway:** Java interfaces + abstract classes define contracts for LLD; use `record` for immutable value objects and DTOs; private fields with public getters/setters give controlled encapsulation without exposing internal state.

---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals]
---
# Java OOPs

Here is a Java OOPs Revision Guide focused on the absolute fundamentals: Syntax, Classes, and Objects. Use this as your cheat sheet to understand how a Java OOP file is structured and how to bring your LLD code to life.

---

## Topic Mindmap

```
[Java OOPs — Syntax Reference]
├── Part 1: Class and Object
│   ├── Class: blueprint; Object: instance
│   ├── Constructor: initializes state, same name as the class
│   ├── this keyword: refers to current object instance
│   └── class vs static members: static, instance, or constructor
├── Part 2: Collections and Strings
│   ├── Lists: ArrayList (dynamic arrays), streams for functional-style transforms
│   ├── Maps: HashMap key-value store, fast O(1) lookups
│   ├── Sets: HashSet unique collections, O(1) membership check
│   └── Strings: immutable; formatting with String.format / text blocks
├── Part 3: Methods and Control Flow
│   ├── Method signature: returnType name(paramType param)
│   ├── Variable arguments: varargs (Type... args)
│   └── Control flow: if/else if/else, for/while, switch expressions
├── Part 4: Access Modifiers & Key Mechanics
│   ├── public / protected / private (compiler-enforced)
│   ├── Pass-by-value: object references are passed by value
│   └── Object methods: toString(), equals(), hashCode()
└── Part 5: Inheritance & Relationships
    ├── IS-A (inheritance): subclass extends parent
    ├── HAS-A — two forms:
    │   ├── Composition (strong): owner creates the part; part dies with the owner
    │   └── Aggregation (weak): owner receives the part; part lives independently
    ├── Abstract class + abstract method / interface
    ├── Method overriding: subclass replaces parent's implementation
    └── Method overloading: same name, different parameter signatures (compile-time)
```

## Part 1: Class and Object

### 1. The Core Concept: Class vs. Object

* **Class (The Blueprint)**: A logical template defining fields and actions.
* **Object (The Instance)**: A physical instantiation of the class occupying memory and containing state.

```java
class Student {
    // Class (static) attribute (shared by all instances)
    static String schoolName = "Tech University";

    // Instance attributes (unique to each instance)
    String name;
    int age;

    // Constructor
    Student(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // Instance method
    void study() {
        System.out.println(name + " is studying.");
    }
}

// Creating objects (instances)
Student student1 = new Student("Alice", 20);
Student student2 = new Student("Bob", 22);

student1.study();  // Alice is studying.
```

***

### 2. The `this` Keyword

The `this` keyword refers to the specific instance of the class you are calling. Java resolves `this` implicitly inside instance methods — you only need it explicitly when a parameter name shadows a field.

```java
class Car {
    private String model;

    Car(String model) {
        this.model = model;  // this.model binds the value to this instance
    }

    String getModel() {
        return this.model;
    }
}
```

***

### 3. Static and Instance Methods

* **Instance Methods**: Access and modify instance state via the implicit `this`.
* **Static Methods**: Access class-level state, called on the class itself without an instance. Often used for factory or utility methods.

```java
class Calculator {
    static String name = "Standard Calculator";

    private String brand;

    Calculator(String brand) {
        this.brand = brand;
    }

    // Instance method
    String getBrand() {
        return brand;
    }

    // Static "class-level" accessor
    static String getCalculatorName() {
        return name;
    }

    // Static method
    static int add(int a, int b) {
        return a + b;
    }
}
```

---

## Part 2: Collections and Strings

Java's Collections Framework provides typed, generic collection classes.

### 1. Lists, Maps, and Sets

```java
// Lists (Dynamic Arrays)
List<String> fruits = new ArrayList<>(List.of("apple", "banana"));
fruits.add("cherry");

// Maps (Hash Maps)
Map<String, Integer> userAges = new HashMap<>();
userAges.put("alice", 25);
userAges.put("bob", 30);
userAges.put("charlie", 35);

// Sets (Hash Sets, Unique items)
Set<Integer> uniqueIds = new HashSet<>(Set.of(101, 102, 103));
uniqueIds.add(101);  // Duplicate, ignored
```

### 2. String Manipulation and Formatting

Java strings are immutable. For building text with variables, `String.format` or text blocks are preferred over concatenation.

```java
String name = "Alice";
String greeting = String.format("Hello, %s!", name);  // Preferred formatted interpolation
```

---

## Part 3: Methods and Control Flow

### 1. Overloaded and Variable Method Arguments

Java has no default parameters — the idiomatic equivalents are method overloading and varargs.

```java
class Logger {
    // Overload to simulate a "default parameter"
    void log(String message) {
        log(message, "INFO");
    }

    void log(String message, String level) {
        System.out.println("[" + level + "] " + message);
    }

    // Varargs (variable number of arguments)
    void logMultiple(String... messages) {
        for (String msg : messages) {
            System.out.println(msg);
        }
    }
}
```

### 2. Control Flow and Switch Expressions

Java supports standard loops and switch expressions (a modern, cleaner alternative to chained if/else).

```java
static String getHttpStatus(int statusCode) {
    // Switch expression — Java's built-in alternative to a lookup map
    return switch (statusCode) {
        case 200 -> "OK";
        case 404 -> "Not Found";
        case 500 -> "Internal Server Error";
        default -> "Unknown Status";
    };
}
```

---

## Part 4: Access Modifiers & Key Mechanics

### 1. Access Modifiers (Compiler-Enforced)

* **Public**: accessible from anywhere.
* **Protected**: accessible within the same package and by subclasses.
* **Private**: accessible only within the declaring class; enforced by the compiler, not just convention.

```java
class Account {
    public String holder;      // Public
    protected String id;       // Protected
    private double balance;    // Private

    Account(String holder, double balance) {
        this.holder = holder;
        this.id = "ACC123";
        this.balance = balance;
    }

    public double getBalance() {
        return balance;
    }
}
```

### 2. Pass-by-Value (of the Reference)

In Java, all arguments are passed by value. For objects, the *value* being passed is the reference itself.
* Modifications made **through** the reference (like mutating a list's contents) affect the caller.
* Reassigning the parameter itself inside the method does not affect the caller's variable.

```java
static void modify(List<Integer> list, int val) {
    list.add(val);  // Mutates the original list's contents
}

List<Integer> myList = new ArrayList<>(List.of(1, 2));
modify(myList, 3);
System.out.println(myList);  // [1, 2, 3]
```

### 3. Object Methods (`toString`, `equals`, `hashCode`)

Java uses well-known methods inherited from `Object` to override built-in operations — the equivalent of Python's dunder methods.

```java
class Book {
    private final String title;
    private final int pages;

    Book(String title, int pages) {
        this.title = title;
        this.pages = pages;
    }

    // String representation (equivalent of Python's __str__)
    @Override
    public String toString() {
        return "'" + title + "' (" + pages + " pages)";
    }

    // "Length" equivalent — Java has no operator overloading, so expose a method
    public int length() {
        return pages;
    }
}
```

---

## Part 5: Inheritance & Relationships

All examples in this section use the same `Vehicle / PetrolCar / ElectricCar / Truck` hierarchy so the concepts chain together.

---

### 1. IS-A Relationship (Inheritance)

`PetrolCar IS-A Vehicle`. The child class `extends` the parent — it inherits all fields and methods, and can add or override behaviour.

```java
class Vehicle {
    protected String make;
    protected String model;
    protected int speed = 0;          // protected: accessible in subclasses

    Vehicle(String make, String model) {
        this.make = make;
        this.model = model;
    }

    String brake(int kmh) {
        speed = Math.max(0, speed - kmh);
        return make + " " + model + " slowed to " + speed + " km/h";
    }

    int getSpeed() {
        return speed;
    }
}


class PetrolCar extends Vehicle {                // PetrolCar IS-A Vehicle
    private double fuel;                         // private: only PetrolCar can touch it

    PetrolCar(String make, String model, double fuel) {
        super(make, model);                      // delegate to Vehicle's constructor
        this.fuel = fuel;
    }

    String accelerate(int kmh) {
        if (fuel <= 0) {
            return "Out of fuel";
        }
        speed += kmh;
        fuel -= kmh * 0.05;
        return make + " " + model + " now at " + speed + " km/h";
    }
}


class ElectricCar extends Vehicle {              // ElectricCar IS-A Vehicle
    private double battery;

    ElectricCar(String make, String model, double batteryKwh) {
        super(make, model);
        this.battery = batteryKwh;
    }

    String accelerate(int kmh) {
        if (battery <= 0) {
            return "Battery dead";
        }
        speed += kmh;
        battery -= kmh * 0.02;    // EVs are more efficient
        return make + " " + model + " now at " + speed + " km/h (silent)";
    }
}


// instanceof confirms IS-A at runtime
ElectricCar tesla = new ElectricCar("Tesla", "Model 3", 75);
System.out.println(tesla instanceof ElectricCar);   // true
System.out.println(tesla instanceof Vehicle);       // true — IS-A relationship confirmed
```

**Rule of thumb**: use `obj instanceof ParentClass` to verify an IS-A relationship at runtime.

---

### 2. HAS-A Relationship — Composition vs Aggregation

HAS-A has two forms that differ on **who owns the lifetime** of the inner object.

| | Composition (strong HAS-A) | Aggregation (weak HAS-A) |
|---|---|---|
| Part created by | The owner (created with `new` inside the constructor) | An external caller (passed in via constructor) |
| Part lifetime | Dies when the owner dies | Lives independently; can be shared |
| Dependency direction | Owner is fully responsible | Owner uses but does not own |
| Java signal | `this.engine = new Engine(...)` inside the constructor | `this.driver = driver` where `driver` is a parameter |

---

#### 2a. Composition (Strong HAS-A) — owner creates and owns the part

A `Car` creates its own `Engine` internally. The `Engine` has no meaning outside that `Car` — when the `Car` is gone, so is the `Engine`.

```java
class Engine {
    private final int horsepower;
    private final String fuelType;

    Engine(int horsepower, String fuelType) {
        this.horsepower = horsepower;
        this.fuelType = fuelType;
    }

    String start() {
        return fuelType + " engine (" + horsepower + " hp) started";
    }

    String stop() {
        return fuelType + " engine stopped";
    }
}


class Car {                              // Car COMPOSES Engine (strong HAS-A)
    private final String make;
    private final String model;
    private final Engine engine;         // Car creates Engine; caller never sees it

    Car(String make, String model, int horsepower, String fuelType) {
        this.make = make;
        this.model = model;
        this.engine = new Engine(horsepower, fuelType);
    }

    String start() {
        return make + " " + model + ": " + engine.start();
    }

    String stop() {
        return make + " " + model + ": " + engine.stop();
    }
}


Car camry = new Car("Toyota", "Camry", 150, "petrol");
System.out.println(camry.start());   // Toyota Camry: petrol engine (150 hp) started
// The Engine object is hidden inside Car — no external reference to it exists.
// When `camry` becomes unreachable, the Engine is also garbage-collected.
```

**Lifetime rule**: destroy the `Car` → its `Engine` is also destroyed. The `Engine` cannot exist without its owning `Car`.

---

#### 2b. Aggregation (Weak HAS-A) — owner uses the part, but doesn't own it

A `Fleet` aggregates `Driver` objects. Drivers exist independently — the same driver can be in multiple fleets, and drivers outlive any single fleet.

```java
class Driver {
    private final String name;
    private final String licenseId;

    public Driver(String name, String licenseId) {
        this.name = name;
        this.licenseId = licenseId;
    }

    public String details() {
        return String.format("Driver(%s, license=%s)", name, licenseId);
    }
}

class Fleet {                                  // Fleet AGGREGATES Drivers (weak HAS-A)
    private final String fleetName;
    private final List<Driver> drivers = new ArrayList<>();   // holds references, not ownership

    public Fleet(String fleetName) {
        this.fleetName = fleetName;
    }

    public void addDriver(Driver driver) {
        drivers.add(driver);                    // Driver passed in from outside
    }

    public void removeDriver(Driver driver) {
        drivers.remove(driver);
    }

    public List<String> listDrivers() {
        List<String> result = new ArrayList<>();
        for (Driver d : drivers) {
            result.add(d.details());
        }
        return result;
    }
}

// Drivers exist independently
Driver alice = new Driver("Alice", "DL-001");
Driver bob   = new Driver("Bob",   "DL-002");

Fleet amazonFleet = new Fleet("Amazon Delivery");
amazonFleet.addDriver(alice);
amazonFleet.addDriver(bob);

Fleet uberFleet = new Fleet("Uber");
uberFleet.addDriver(alice);              // Alice is in BOTH fleets simultaneously

System.out.println(amazonFleet.listDrivers());
// [Driver(Alice, license=DL-001), Driver(Bob, license=DL-002)]

// Dissolving the fleet does NOT delete the drivers
amazonFleet = null;                      // no more references from that fleet
System.out.println(alice.details());     // Driver(Alice, license=DL-001) — still alive
```

**Lifetime rule**: destroy the `Fleet` → the `Driver` objects continue to exist. Drivers can be shared across multiple fleets.

---

**IS-A vs HAS-A quick test**:
- "Is a Car an Engine?" → No → HAS-A. Does the Car create the Engine internally? → Composition. Is it passed in from outside? → Aggregation.
- "Is a PetrolCar a Vehicle?" → Yes → IS-A (inheritance).

---

### 3. Abstract Class and Abstract Method

An abstract class defines a **contract** — it lists what subclasses must implement, but provides no concrete implementation for those methods. You cannot instantiate an abstract class directly.

```java
abstract class Vehicle {                // abstract class = cannot be instantiated
    protected final String make;
    protected final String model;

    public Vehicle(String make, String model) {
        this.make = make;
        this.model = model;
    }

    public abstract String start();                     // subclass MUST implement this
    public abstract String refuelOrCharge(double amount); // subclass MUST implement this

    public String describe() {              // concrete method — inherited as-is
        return make + " " + model;
    }
}

class PetrolCar extends Vehicle {
    private double fuel;

    public PetrolCar(String make, String model, double fuel) {
        super(make, model);
        this.fuel = fuel;
    }

    @Override
    public String start() {                 // fulfils abstract contract
        return describe() + " ignites combustion engine — vroom!";
    }

    @Override
    public String refuelOrCharge(double litres) {
        fuel += litres;
        return String.format("Refuelled %.1fL. Tank: %.1fL", litres, fuel);
    }
}

class ElectricCar extends Vehicle {
    private double battery;

    public ElectricCar(String make, String model, double batteryKwh) {
        super(make, model);
        this.battery = batteryKwh;
    }

    @Override
    public String start() {
        return describe() + " powers up electric motor — silent launch.";
    }

    @Override
    public String refuelOrCharge(double kwh) {
        battery += kwh;
        return String.format("Charged %.1f kWh. Battery: %.1f kWh", kwh, battery);
    }
}

// new Vehicle("x","y")   // compile error: Vehicle is abstract; cannot be instantiated
Vehicle pc = new PetrolCar("Toyota", "Camry", 50);
System.out.println(pc.start());     // Toyota Camry ignites combustion engine — vroom!
```

**Interview rule**: declare every method that varies per subclass as `abstract` on the base class. Leave shared logic (like `describe()`) as a concrete method on the base. If a subclass misses even one abstract method, the compiler refuses to compile it unless the subclass is also declared `abstract`.

---

### 4. Method Overriding

A subclass **replaces** the parent's implementation of a method with the same signature. Java resolves to the subclass version at runtime (dynamic dispatch / virtual method invocation).

```java
class Vehicle {
    public String start() {
        return "Generic vehicle starting...";           // default behaviour
    }

    public String describe() {
        return "Vehicle(" + getClass().getSimpleName() + ")";
    }
}

class PetrolCar extends Vehicle {
    @Override
    public String start() {                              // overrides Vehicle.start
        return "PetrolCar ignites combustion engine — vroom!";
    }
}

class ElectricCar extends Vehicle {
    @Override
    public String start() {                              // overrides Vehicle.start
        return "ElectricCar powers up silently.";
    }
}

class Truck extends Vehicle {
    @Override
    public String start() {                              // overrides Vehicle.start
        return "Truck rumbles to life — heavy diesel ignition.";
    }

    public String startWithChecklist() {
        String parentResult = super.start();              // call parent's version if needed
        return "[Pre-check done] " + parentResult;
    }
}

// Runtime dispatch — same call, different behaviour based on actual type
List<Vehicle> fleet = List.of(new PetrolCar(), new ElectricCar(), new Truck());
for (Vehicle v : fleet) {
    System.out.println(v.start());
}
// PetrolCar ignites combustion engine — vroom!
// ElectricCar powers up silently.
// Truck rumbles to life — heavy diesel ignition.
```

**Key point**: `super.start()` lets you extend the parent's behaviour rather than replace it entirely — useful when you want to add to the parent, not overwrite it.

---

### 5. Method Overloading

Java **does** support true method overloading — multiple methods with the same name but different parameter lists (different count and/or types) can coexist in the same class. The compiler picks the matching overload at compile time based on the argument types (static/compile-time polymorphism).

```java
class FuelStation {

    // --- Approach 1: overload with fewer parameters (acts like a "default" argument) ---
    public String refuel(double litres) {
        return refuel(litres, "91");
    }

    public String refuel(double litres, String grade) {
        return String.format("Dispensed %.0fL of grade-%s fuel", litres, grade);
    }

    // Usage:
    // station.refuel(40)          -> "Dispensed 40L of grade-91 fuel"
    // station.refuel(40, "95")    -> "Dispensed 40L of grade-95 fuel"

    // --- Approach 2: varargs for variable number of arguments ---
    public String logVehicles(String... vehicleIds) {
        return "Serviced: " + String.join(", ", vehicleIds);
    }

    // Usage:
    // station.logVehicles("V1")             -> "Serviced: V1"
    // station.logVehicles("V1", "V2", "V3") -> "Serviced: V1, V2, V3"

    // --- Approach 3: true overloads with different parameter types/counts ---
    public double calculateCost(double litres) {
        return calculateCost(litres, 0.0);
    }

    public double calculateCost(double litres, double discountPct) {
        double base = litres * 1.5;                  // Rs 1.5 per litre
        return base * (1 - discountPct / 100);
    }

    // Usage:
    // station.calculateCost(40)        -> 60.0
    // station.calculateCost(40, 10)    -> 54.0   (10% discount)
}
```

**Interview answer**: "Java resolves overloads at compile time based on the number and types of arguments (static polymorphism) — this is distinct from overriding, which is resolved at runtime based on the actual object type (dynamic polymorphism). When no exact match exists, the compiler applies widening/boxing conversions before failing; ambiguous calls are a compile error, not a runtime one."

---

### 6. Quick Reference — IS-A vs HAS-A vs Abstract

| Concept | Java syntax | Lifetime of the part | Example |
|---|---|---|---|
| IS-A (inheritance) | `class PetrolCar extends Vehicle` | n/a | `PetrolCar IS-A Vehicle` |
| Composition (strong HAS-A) | `private final Engine engine = new Engine(...);` inside constructor | Part dies with owner | `Car` creates its own `Engine` |
| Aggregation (weak HAS-A) | `private List<Driver> drivers = new ArrayList<>();`, drivers passed in | Part outlives owner; can be shared | `Fleet` holds `Driver` refs; drivers exist independently |
| Abstract class | `abstract class Vehicle` | n/a | Cannot instantiate; defines contract |
| Abstract method | `abstract String start();` | n/a | Subclass must implement or fails to compile (unless also `abstract`) |
| Method overriding | Same signature in subclass | n/a | `ElectricCar.start()` replaces `Vehicle.start()` |
| Method overloading | Default args / `*args` / `@overload` | n/a | `calculate_cost(litres)` vs `calculate_cost(litres, discount)` |
| `super()` | `super().__init__(...)` | n/a | Delegate to parent; follows MRO |

---

## Interviewer Follow-Up Questions

- "What is the difference between IS-A and HAS-A?" → IS-A is inheritance: `PetrolCar IS-A Vehicle` — the child extends the parent and inherits its fields and methods. HAS-A is when a class holds another class as a field — it comes in two forms. **Composition (strong HAS-A)**: the owner creates the part internally (`self.__engine = Engine(...)`); the part has no independent existence and is destroyed with the owner. **Aggregation (weak HAS-A)**: the part is passed in from outside (`fleet.add_driver(alice)`); it lives independently, can be shared across owners, and outlives any single owner. Rule of thumb: if the inner object makes no sense without the outer, it's composition. If the inner object can stand alone and be reused elsewhere, it's aggregation.
- "What is the difference between composition and aggregation?" → Both are forms of HAS-A. Composition: owner **creates** the part — `Car.__init__` calls `Engine(...)` directly; caller never gets a reference to the engine. The engine's lifetime is bound to the car. Aggregation: owner **receives** the part — `Fleet.add_driver(driver)` accepts an externally created `Driver`. The driver can belong to multiple fleets and survives after the fleet is deleted. Python signal: if the inner object is instantiated inside `__init__`, it's composition; if it's injected via a parameter, it's aggregation.
- "What happens if a subclass doesn't implement all abstract methods?" → Python raises `TypeError` at instantiation time: `TypeError: Can't instantiate abstract class PetrolCar with abstract method refuel_or_charge`. The abstract contract is enforced by Python's `ABCMeta` metaclass — it checks at class creation that all `@abstractmethod` methods have concrete implementations in the subclass. If even one is missing, instantiation fails. This is Python's compile-time safety net for abstract contracts.
- "Does Python support method overloading?" → Not natively. If you define two methods with the same name, the second replaces the first. Achieve overload-like behaviour with: (1) default arguments — `def start(self, mode: str = "normal")`; (2) `*args` / `**kwargs` — handle variable inputs in one body; (3) `typing.overload` decorator — type hints only, no runtime effect. In interviews: mention `typing.overload` exists but clarify it's for IDE/type-checker support, not actual dispatch.
- "What is the difference between method overriding and method overloading?" → Overriding: same method signature in a subclass replaces the parent's implementation — resolved at **runtime** based on actual object type (dynamic dispatch). Polymorphism is built on overriding. Overloading: same method name with different parameter signatures in the **same class** — resolved at **compile time** in Java/C++. Python doesn't have true overloading (last definition wins). Key: overriding is a runtime concept that enables polymorphism; overloading is a compile-time convenience for callers.
- "When would you use `super()` inside an overridden method?" → When you want to **extend** the parent's behaviour rather than replace it. `super().start()` calls the parent's `start()` first, then you add extra behaviour after. Common pattern in `__init__`: `super().__init__(make, model)` initializes the parent's fields before adding subclass-specific ones. If you don't call `super().__init__()`, the parent's fields are never set — `self.make` would be undefined. In multiple inheritance, `super()` follows the MRO chain, not just the immediate parent.
- "What is `__init__` vs `__new__` in Python?" → `__new__` allocates and returns the new object (rarely overridden — used for immutable types like `int`, `str`, or implementing singletons). `__init__` initializes the already-created object (the common override point). Normal classes only need `__init__`. Override `__new__` when you need to control object creation itself (e.g., return a cached instance for a singleton or flyweight pattern).
- "What does `@staticmethod` vs `@classmethod` vs instance method mean? When do you use each?" → Instance method: receives `self` — accesses instance state. `@classmethod`: receives `cls` — accesses class-level state, used for factory methods (`Date.from_string("2024-01-01")`). `@staticmethod`: receives neither — a pure function namespaced under the class, no access to instance or class state. Use when the function logically belongs to the class but doesn't need any class data.
- "How does Python's MRO (Method Resolution Order) work for multiple inheritance?" → C3 linearization. For `class C(A, B)`, Python builds the MRO as: C → A → B → object (roughly left-to-right, depth-first, but respecting the order). Call `C.__mro__` to inspect. `super()` follows the MRO — it doesn't call the parent directly, it calls the next class in the MRO. This is why cooperative multiple inheritance works in Python but requires all classes in the chain to use `super()`.
- "What is a `@property` and why is it better than a plain getter method?" → `@property` exposes an attribute-like interface while executing a getter under the hood: `obj.name` instead of `obj.get_name()`. The API looks like a plain attribute access (clean), but you can add validation, computation, or lazy-loading behind it later without changing callers. Combine with `@name.setter` for validation on assignment. The value: you can start with a plain public attribute, then add `@property` later to intercept reads/writes — backward-compatible refactoring.
- "What's the difference between `__str__` and `__repr__`?" → `__repr__`: unambiguous representation for developers — should ideally be valid Python to recreate the object: `Point(x=3, y=4)`. Used in the REPL and `repr()`. `__str__`: human-readable representation for display: `"(3, 4)"`. Used by `print()` and `str()`. Fallback: if `__str__` is not defined, Python uses `__repr__`. Always define `__repr__`; define `__str__` only when you need a different display format.
