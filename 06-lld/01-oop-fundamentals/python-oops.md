> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Python OOP syntax reference for LLD interviews — classes, dataclasses, ABC, properties, dunder methods, and Python-specific patterns.
>
> **Key topics:**
> - Class structure: `__init__` constructor; `self` is explicit; `_private` convention (not enforced); `__private` name mangling (double underscore)
> - Properties: `@property` for getters; `@prop.setter` for setters with validation; Pythonic alternative to Java getters/setters
> - Class methods / static: `@classmethod` (receives cls, factory methods); `@staticmethod` (no cls/self, utility functions); useful for alternative constructors
> - ABC (Abstract Base Class): `from abc import ABC, abstractmethod`; `class Shape(ABC): @abstractmethod def area()`; enforces interface implementation
> - Dunder methods: `__str__`/`__repr__` (string representation), `__eq__`/`__hash__` (equality), `__lt__` (comparison/sorting), `__len__`, `__iter__` (iteration protocol)
> - Dataclasses: `@dataclass` auto-generates `__init__`, `__repr__`, `__eq__`; `frozen=True` for immutability; great for value objects
> - Multiple inheritance: Python supports it; MRO (Method Resolution Order) via C3 linearization; prefer mixins over complex hierarchies
>
> **Key takeaway:** Python's ABC + `@abstractmethod` replaces Java interfaces for LLD; use dataclasses for value objects and DTOs; `@property` gives Java-style encapsulation without explicit getter/setter boilerplate.

---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals]
---
# Python OOPs

Here is a Python OOPs Revision Guide focused on the absolute fundamentals: Syntax, Classes, and Objects. Use this as your cheat sheet to understand how a Python OOP file is structured and how to bring your LLD code to life.

---

## Topic Mindmap

```
[Python OOPs — Syntax Reference]
├── Part 1: Class and Object
│   ├── Class: blueprint; Object: instance
│   ├── __init__ method: constructor, initializes state
│   ├── self parameter: refers to current object instance
│   └── class vs static members: classmethod, staticmethod, or instancemethod
├── Part 2: Collections and Strings
│   ├── Lists: dynamic arrays, list comprehensions
│   ├── Dictionaries: key-value hash maps, fast O(1) lookups
│   ├── Sets: unique collections, O(1) membership check
│   └── Strings: immutable; formatting with f-strings
├── Part 3: Methods and Control Flow
│   ├── Method signature: def name(self, param: type) -> return_type
│   ├── Variable arguments: *args, **kwargs
│   └── Control flow: if/elif/else, for/while, dict lookups (switch alternative)
├── Part 4: Access Modifiers & Key Mechanics
│   ├── public / _protected / __private (name mangling)
│   ├── Pass-by-object-reference: mutable vs immutable
│   └── Magic methods: __str__, __repr__, __len__
└── Part 5: Inheritance & Relationships
    ├── IS-A (inheritance): subclass extends parent
    ├── HAS-A — two forms:
    │   ├── Composition (strong): owner creates the part; part dies with the owner
    │   └── Aggregation (weak): owner receives the part; part lives independently
    ├── Abstract class + abstract method (abc.ABC, @abstractmethod)
    ├── Method overriding: subclass replaces parent's implementation
    └── Method overloading: Python does it via default args / *args / @overload
```

## Part 1: Class and Object

### 1. The Core Concept: Class vs. Object

* **Class (The Blueprint)**: A logical template defining fields and actions.
* **Object (The Instance)**: A physical instantiation of the class occupying memory and containing state.

```python
class Student:
    # Class attribute (shared by all instances)
    school_name = "Tech University"

    # Constructor (Initializer)
    def __init__(self, name, age):
        self.name = name  # Instance attribute (unique to each instance)
        self.age = age

    # Instance method
    def study(self):
        print(f"{self.name} is studying.")

# Creating objects (instances)
student1 = Student("Alice", 20)
student2 = Student("Bob", 22)

student1.study()  # Alice is studying.
```

***

### 2. The `self` Keyword

The `self` parameter represents the specific instance of the class you are calling. In Python, you must explicitly include `self` as the first argument of any instance method, although Python passes it automatically when the method is invoked.

```python
class Car:
    def __init__(self, model):
        self.model = model  # self.model binds the value to this instance

    def get_model(self):
        return self.model
```

***

### 3. Static and Class Methods

* **Instance Methods**: Access and modify instance state via `self`.
* **Class Methods (`@classmethod`)**: Access and modify class state via `cls`. Often used for factory methods.
* **Static Methods (`@staticmethod`)**: Access neither instance nor class state. behave like normal utility functions inside the class namespace.

```python
class Calculator:
    name = "Standard Calculator"

    def __init__(self, brand):
        self.brand = brand

    # Instance method
    def get_brand(self):
        return self.brand

    # Class method
    @classmethod
    def get_calculator_name(cls):
        return cls.name

    # Static method
    @staticmethod
    def add(a, b):
        return a + b
```

---

## Part 2: Collections and Strings

Python offers clean, built-in dynamic collections instead of separate array objects.

### 1. Lists, Dictionaries, and Sets

```python
# Lists (Dynamic Arrays)
fruits = ["apple", "banana"]
fruits.append("cherry")

# Dictionaries (Hash Maps)
user_ages = {"alice": 25, "bob": 30}
user_ages["charlie"] = 35

# Sets (Hash Sets, Unique items)
unique_ids = {101, 102, 103}
unique_ids.add(101)  # Duplicate, ignored
```

### 2. String Manipulation and Formatting

Python strings are immutable. For text building, f-strings are preferred over concatenation.

```python
name = "Alice"
greeting = f"Hello, {name}!"  # Preferred f-string interpolation
```

---

## Part 3: Methods and Control Flow

### 1. Flexible Method Arguments

Python handles variable arguments and default parameters naturally.

```python
class Logger:
    # Default parameters
    def log(self, message, level="INFO"):
        print(f"[{level}] {message}")

    # *args (variable positional arguments) and **kwargs (variable keyword arguments)
    def log_multiple(self, *messages, **metadata):
        for msg in messages:
            print(f"{msg} | Metadata: {metadata}")
```

### 2. Control Flow and Switch Alternatives

Python supports standard loops and dictionary-based lookups (the common pythonic alternative to switch statements).

```python
def get_http_status(status_code):
    # Dictionary lookup is a common pythonic alternative to switch/match-case
    statuses = {
        200: "OK",
        404: "Not Found",
        500: "Internal Server Error"
    }
    return statuses.get(status_code, "Unknown Status")
```

---

## Part 4: Access Modifiers & Key Mechanics

### 1. Access Modifiers (Convention & Mangling)

* **Public**: accessible from anywhere.
* **Protected** (prefixed with `_`): acts as a warning to callers that the member is private, but is still physically accessible.
* **Private** (prefixed with `__`): invokes Python's name mangling mechanism, changing `__variable` to `_ClassName__variable` to prevent accidental access/overrides.

```python
class Account:
    def __init__(self, holder, balance):
        self.holder = holder      # Public
        self._id = "ACC123"       # Protected
        self.__balance = balance  # Private

    def get_balance(self):
        return self.__balance
```

### 2. Pass-by-Object-Reference

In Python, all arguments are passed by object reference.
* If you pass a **mutable** object (like a list or dict), modifications inside the method affect the caller.
* If you pass an **immutable** object (like a string, tuple, or integer), rebinding the variable inside the method does not affect the caller.

```python
def modify(lst, val):
    lst.append(val)  # Mutates the original list

my_list = [1, 2]
modify(my_list, 3)
print(my_list)  # [1, 2, 3]
```

### 3. Magic (Dunder) Methods

Python uses double underscore (dunder) methods to override built-in operations.

```python
class Book:
    def __init__(self, title, pages):
        self.title = title
        self.pages = pages

    # String representation (analogous to toString() in Java)
    def __str__(self):
        return f"'{self.title}' ({self.pages} pages)"

    # Length operator override
    def __len__(self):
        return self.pages
```

---

## Part 5: Inheritance & Relationships

All examples in this section use the same `Vehicle / PetrolCar / ElectricCar / Truck` hierarchy so the concepts chain together.

---

### 1. IS-A Relationship (Inheritance)

`PetrolCar IS-A Vehicle`. The child class `extends` the parent — it inherits all fields and methods, and can add or override behaviour.

```python
class Vehicle:
    def __init__(self, make, model):
        self.make = make
        self.model = model
        self._speed = 0          # _speed: protected, accessible in subclasses

    def brake(self, kmh):
        self._speed = max(0, self._speed - kmh)
        return f"{self.make} {self.model} slowed to {self._speed} km/h"

    def get_speed(self):
        return self._speed


class PetrolCar(Vehicle):                # PetrolCar IS-A Vehicle
    def __init__(self, make, model, fuel):
        super().__init__(make, model)    # delegate to Vehicle.__init__
        self.__fuel = fuel               # __fuel: private, only PetrolCar can touch it

    def accelerate(self, kmh):
        if self.__fuel <= 0:
            return "Out of fuel"
        self._speed += kmh
        self.__fuel -= kmh * 0.05
        return f"{self.make} {self.model} now at {self._speed} km/h"


class ElectricCar(Vehicle):             # ElectricCar IS-A Vehicle
    def __init__(self, make, model, battery_kwh):
        super().__init__(make, model)
        self.__battery = battery_kwh

    def accelerate(self, kmh):
        if self.__battery <= 0:
            return "Battery dead"
        self._speed += kmh
        self.__battery -= kmh * 0.02    # EVs are more efficient
        return f"{self.make} {self.model} now at {self._speed} km/h (silent)"


# isinstance() confirms IS-A at runtime
tesla = ElectricCar("Tesla", "Model 3", 75)
print(isinstance(tesla, ElectricCar))   # True
print(isinstance(tesla, Vehicle))       # True  — IS-A relationship confirmed
```

**Rule of thumb**: use `isinstance(obj, ParentClass)` to verify an IS-A relationship at runtime. `issubclass(ElectricCar, Vehicle)` checks at the class level.

---

### 2. HAS-A Relationship — Composition vs Aggregation

HAS-A has two forms that differ on **who owns the lifetime** of the inner object.

| | Composition (strong HAS-A) | Aggregation (weak HAS-A) |
|---|---|---|
| Part created by | The owner (`self` creates it in `__init__`) | An external caller (passed in via constructor) |
| Part lifetime | Dies when the owner dies | Lives independently; can be shared |
| Dependency direction | Owner is fully responsible | Owner uses but does not own |
| Python signal | `self.__engine = Engine(...)` inside `__init__` | `self.__driver = driver` where `driver` is a parameter |

---

#### 2a. Composition (Strong HAS-A) — owner creates and owns the part

A `Car` creates its own `Engine` internally. The `Engine` has no meaning outside that `Car` — when the `Car` is gone, so is the `Engine`.

```python
class Engine:
    def __init__(self, horsepower, fuel_type):
        self.horsepower = horsepower
        self.fuel_type = fuel_type

    def start(self):
        return f"{self.fuel_type} engine ({self.horsepower} hp) started"

    def stop(self):
        return f"{self.fuel_type} engine stopped"


class Car:                              # Car COMPOSES Engine (strong HAS-A)
    def __init__(self, make, model, horsepower, fuel_type):
        self.make = make
        self.model = model
        self.__engine = Engine(horsepower, fuel_type)   # Car creates Engine; caller never sees it

    def start(self):
        return f"{self.make} {self.model}: {self.__engine.start()}"

    def stop(self):
        return f"{self.make} {self.model}: {self.__engine.stop()}"


camry = Car("Toyota", "Camry", horsepower=150, fuel_type="petrol")
print(camry.start())   # Toyota Camry: petrol engine (150 hp) started
# The Engine object is hidden inside Car — no external reference to it exists.
# When `camry` goes out of scope, the Engine is also garbage-collected.
```

**Lifetime rule**: destroy the `Car` → its `Engine` is also destroyed. The `Engine` cannot exist without its owning `Car`.

---

#### 2b. Aggregation (Weak HAS-A) — owner uses the part, but doesn't own it

A `Fleet` aggregates `Driver` objects. Drivers exist independently — the same driver can be in multiple fleets, and drivers outlive any single fleet.

```python
class Driver:
    def __init__(self, name, license_id):
        self.name = name
        self.license_id = license_id

    def details(self):
        return f"Driver({self.name}, license={self.license_id})"


class Fleet:                            # Fleet AGGREGATES Drivers (weak HAS-A)
    def __init__(self, fleet_name):
        self.fleet_name = fleet_name
        self.__drivers = []   # list of Driver; holds references, not ownership

    def add_driver(self, driver):
        self.__drivers.append(driver)       # Driver passed in from outside

    def remove_driver(self, driver):
        self.__drivers.remove(driver)

    def list_drivers(self):
        return [d.details() for d in self.__drivers]


# Drivers exist independently
alice = Driver("Alice", "DL-001")
bob   = Driver("Bob",   "DL-002")

amazon_fleet = Fleet("Amazon Delivery")
amazon_fleet.add_driver(alice)
amazon_fleet.add_driver(bob)

uber_fleet = Fleet("Uber")
uber_fleet.add_driver(alice)            # Alice is in BOTH fleets simultaneously

print(amazon_fleet.list_drivers())
# ['Driver(Alice, license=DL-001)', 'Driver(Bob, license=DL-002)']

# Dissolving the fleet does NOT delete the drivers
del amazon_fleet
print(alice.details())                  # Driver(Alice, license=DL-001) — still alive
```

**Lifetime rule**: destroy the `Fleet` → the `Driver` objects continue to exist. Drivers can be shared across multiple fleets.

---

**IS-A vs HAS-A quick test**:
- "Is a Car an Engine?" → No → HAS-A. Does the Car create the Engine internally? → Composition. Is it passed in from outside? → Aggregation.
- "Is a PetrolCar a Vehicle?" → Yes → IS-A (inheritance).

---

### 3. Abstract Class and Abstract Method

An abstract class defines a **contract** — it lists what subclasses must implement, but provides no concrete implementation for those methods. You cannot instantiate an abstract class directly.

```python
from abc import ABC, abstractmethod


class Vehicle(ABC):                     # ABC = Abstract Base Class
    def __init__(self, make, model):
        self.make = make
        self.model = model

    @abstractmethod
    def start(self):                    # subclass MUST implement this
        ...

    @abstractmethod
    def refuel_or_charge(self, amount):  # subclass MUST implement this
        ...

    def describe(self):                 # concrete method — inherited as-is
        return f"{self.make} {self.model}"


class PetrolCar(Vehicle):
    def __init__(self, make, model, fuel):
        super().__init__(make, model)
        self.__fuel = fuel

    def start(self):                    # fulfils abstract contract
        return f"{self.describe()} ignites combustion engine — vroom!"

    def refuel_or_charge(self, litres):
        self.__fuel += litres
        return f"Refuelled {litres}L. Tank: {self.__fuel}L"


class ElectricCar(Vehicle):
    def __init__(self, make, model, battery_kwh):
        super().__init__(make, model)
        self.__battery = battery_kwh

    def start(self):
        return f"{self.describe()} powers up electric motor — silent launch."

    def refuel_or_charge(self, kwh):
        self.__battery += kwh
        return f"Charged {kwh} kWh. Battery: {self.__battery} kWh"


# Vehicle()                # TypeError: Can't instantiate abstract class Vehicle
pc = PetrolCar("Toyota", "Camry", 50)
print(pc.start())          # Toyota Camry ignites combustion engine — vroom!
```

**Interview rule**: declare `@abstractmethod` on every method that varies per subclass. Leave shared logic (like `describe()`) as a concrete method on the base. If a subclass misses even one `@abstractmethod`, Python raises `TypeError` at instantiation.

---

### 4. Method Overriding

A subclass **replaces** the parent's implementation of a method with the same signature. Python resolves to the subclass version at runtime (dynamic dispatch).

```python
class Vehicle:
    def start(self):
        return "Generic vehicle starting..."         # default behaviour

    def describe(self):
        return f"Vehicle({self.__class__.__name__})"


class PetrolCar(Vehicle):
    def start(self):                                 # overrides Vehicle.start
        return "PetrolCar ignites combustion engine — vroom!"


class ElectricCar(Vehicle):
    def start(self):                                 # overrides Vehicle.start
        return "ElectricCar powers up silently."


class Truck(Vehicle):
    def start(self):                                 # overrides Vehicle.start
        return "Truck rumbles to life — heavy diesel ignition."

    def start_with_checklist(self):
        parent_result = super().start()              # call parent's version if needed
        return f"[Pre-check done] {parent_result}"


# Runtime dispatch — same call, different behaviour based on actual type
fleet = [PetrolCar(), ElectricCar(), Truck()]  # list of Vehicle
for v in fleet:
    print(v.start())
# PetrolCar ignites combustion engine — vroom!
# ElectricCar powers up silently.
# Truck rumbles to life — heavy diesel ignition.
```

**Key point**: `super().start()` lets you extend the parent's behaviour rather than replace it entirely — useful when you want to add to the parent, not overwrite it.

---

### 5. Method Overloading (Python-style)

Python does **not** support true method overloading (same name, different parameter types) like Java. The last definition wins. Instead, use:

- **Default arguments** — same method, parameter is optional
- **`*args` / `**kwargs`** — accept any number of arguments
- **`@overload` from `typing`** — type-hint different signatures for IDE support (no runtime effect)

```python
from typing import overload


class FuelStation:

    # --- Approach 1: default argument ---
    def refuel(self, litres, grade="91"):
        return f"Dispensed {litres}L of grade-{grade} fuel"

    # Usage:
    # station.refuel(40)          -> "Dispensed 40L of grade-91 fuel"
    # station.refuel(40, "95")    -> "Dispensed 40L of grade-95 fuel"

    # --- Approach 2: *args for variable positional arguments ---
    def log_vehicles(self, *vehicle_ids):
        return f"Serviced: {', '.join(vehicle_ids)}"

    # Usage:
    # station.log_vehicles("V1")             -> "Serviced: V1"
    # station.log_vehicles("V1", "V2", "V3") -> "Serviced: V1, V2, V3"

    # --- Approach 3: @overload for type-safe signatures (IDE hint only) ---
    @overload
    def calculate_cost(self, litres): ...
    @overload
    def calculate_cost(self, litres, discount_pct): ...

    def calculate_cost(self, litres, discount_pct=0.0):
        base = litres * 1.5                  # Rs 1.5 per litre
        return base * (1 - discount_pct / 100)

    # Usage:
    # station.calculate_cost(40)        -> 60.0
    # station.calculate_cost(40, 10)    -> 54.0   (10% discount)
```

**Interview answer**: "Python doesn't support overloading natively — the last definition of a function with the same name replaces all prior ones. We achieve overload-like behaviour via default parameters or `*args`. `typing.overload` is a decorator that lets type checkers understand multiple signatures, but at runtime only one function body exists."

---

### 6. Quick Reference — IS-A vs HAS-A vs Abstract

| Concept | Python syntax | Lifetime of the part | Example |
|---|---|---|---|
| IS-A (inheritance) | `class PetrolCar(Vehicle)` | n/a | `PetrolCar IS-A Vehicle` |
| Composition (strong HAS-A) | `self.__engine = Engine(...)` inside `__init__` | Part dies with owner | `Car` creates its own `Engine` |
| Aggregation (weak HAS-A) | `self.__drivers: list[Driver] = []`, drivers passed in | Part outlives owner; can be shared | `Fleet` holds `Driver` refs; drivers exist independently |
| Abstract class | `class Vehicle(ABC)` | n/a | Cannot instantiate; defines contract |
| Abstract method | `@abstractmethod def start()` | n/a | Subclass must implement or `TypeError` at init |
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
