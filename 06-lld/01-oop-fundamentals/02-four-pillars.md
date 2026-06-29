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

```python
# The cookie cutter — no memory allocated yet
class Car:
    def __init__(self, make, model, year):
        self.make = make
        self.model = model
        self.year = year

    def start_engine(self):
        print(f"{self.make} {self.model} engine starting.")

# Two cookies from the same cutter — same shape, different state
civic = Car("Honda", "Civic", 2022)
model3 = Car("Tesla", "Model 3", 2023)
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

### Python Example

```python
class Car:
    # _speed is private — callers cannot set it to -200 km/h directly.
    # __fuel uses name mangling for stricter protection.
    def __init__(self, make, model, fuel_litres):
        self.make = make
        self.model = model
        self.__fuel = fuel_litres   # private — enforce invariant here
        self._speed = 0             # private — only accelerate/brake can change it

    def accelerate(self, kmh):
        if kmh <= 0:
            return "Speed increment must be positive"
        if self.__fuel <= 0:
            return "Out of fuel — cannot accelerate"
        self._speed += kmh
        self.__fuel -= kmh * 0.05   # consume fuel
        return f"{self.make} {self.model} now at {self._speed} km/h"

    def brake(self, kmh):
        self._speed = max(0, self._speed - kmh)
        return f"Slowed to {self._speed} km/h"

    def get_speed(self):
        return self._speed          # read-only view; no direct write

    def get_fuel(self):
        return round(self.__fuel, 2)
```

**Invariant enforced**: speed can never go negative (clamped to 0 in `brake()`); fuel can never be consumed below empty; callers cannot set `__fuel = 9999` to cheat.

### When to use in interviews

Whenever you design a class with data that has invariants (speed can't be negative, fuel can't exceed tank capacity, odometer can only increase), use encapsulation: make the field private and enforce the rule in the method. Interviewers look for this in LLD rounds — a `Car` class with a public `speed` field that any caller can set to `-500` is a red flag.

---

## 2. Abstraction

**Question**: You have a `PetrolCar` that can `start()` and `refuel()`. Now you need to support `ElectricCar` (which charges instead of refuelling) and `HybridCar`. The fleet manager loops over all vehicles and calls start. Does the loop need to know which type it is talking to?

**Problem without abstraction**: The fleet manager calls `petrolCar.startPetrolEngine()` and `electricCar.startElectricMotor()` — it must import and know every concrete type. Add `HybridCar` and the fleet manager changes again.

**Minimal fix**: Define an abstract `Vehicle` with `start()` and `refuel_or_charge()`. The fleet manager depends only on `Vehicle`. Each concrete type implements the contract its own way. The fleet manager never changes when a new vehicle type is added.

**Full pattern**: This is Abstraction — expose what an object does (the interface), hide how it does it (the implementation). The fleet manager reasons about "what": start the vehicle, refuel it. The concrete car owns the "how": inject petrol vs. plug in charger.

### Real-life analogy

You drive a rental car in a foreign country. You know how to: insert key, start engine, accelerate, brake. You do not know whether it is front-wheel drive or rear-wheel drive, whether it has a timing belt or chain, or which country made the engine. The rental company exposed to you exactly the controls you need. Everything else — the drivetrain, the fuel injection system, the ABS calibration — is hidden behind the steering wheel and pedals.

Abstraction is about what you expose, not what you hide. The contract says: "these pedals and this wheel will drive the car." How is none of your business.

### Definition

Abstraction is the process of exposing only the essential interface of a system and hiding the complex implementation details behind it. It lets callers reason about **what** an object does without needing to understand **how** it does it.

**Key distinction from Encapsulation:**
- Encapsulation is about *protecting* internal state (hiding data).
- Abstraction is about *simplifying* a system by hiding implementation (hiding complexity behind a clean interface).

A car both encapsulates its engine internals (you cannot reach in and adjust the pistons) and abstracts the combustion cycle (you press accelerate — you don't ignite fuel yourself).

### Python Example

```python
from abc import ABC, abstractmethod

# Abstract Vehicle defines the contract — WHAT every vehicle must do
class Vehicle(ABC):
    def __init__(self, make, model):
        self.make = make
        self.model = model

    @abstractmethod
    def start(self): ...

    @abstractmethod
    def refuel_or_charge(self, amount): ...

    def describe(self):
        return f"{self.make} {self.model}"

# PetrolCar knows HOW to start a combustion engine
class PetrolCar(Vehicle):
    def __init__(self, make, model, fuel_litres):
        super().__init__(make, model)
        self.__fuel = fuel_litres

    def start(self):
        return f"{self.describe()} ignites combustion engine — vroom!"

    def refuel_or_charge(self, litres):
        self.__fuel += litres
        return f"Refuelled {litres}L. Tank: {self.__fuel}L"

# ElectricCar knows HOW to start an electric motor
class ElectricCar(Vehicle):
    def __init__(self, make, model, battery_kwh):
        super().__init__(make, model)
        self.__battery = battery_kwh

    def start(self):
        return f"{self.describe()} powers up electric motor — silent launch."

    def refuel_or_charge(self, kwh):
        self.__battery += kwh
        return f"Charged {kwh} kWh. Battery: {self.__battery} kWh"

# Fleet manager works with Vehicle — never imports PetrolCar or ElectricCar
def start_fleet(vehicles):  # list of Vehicle
    for v in vehicles:
        print(v.start())   # same call, different behaviour — abstraction in action

fleet = [
    PetrolCar("Toyota", "Camry", 50),
    ElectricCar("Tesla", "Model 3", 75),
]
start_fleet(fleet)
```

### When to use in interviews

Use abstract classes when you are defining a contract that multiple concrete types must fulfill — `Vehicle → PetrolCar / ElectricCar / HybridCar`, payment methods (`PaymentMethod → CreditCard / UPI / Wallet`), storage backends (`Storage → S3 / LocalDisk`). The caller depends on the abstraction, not the concrete implementation. This is what makes the system extensible.

---

## 3. Inheritance

**Question**: You have `PetrolCar`, `ElectricCar`, and `Truck` classes. All three have `make`, `model`, `get_speed()`, `accelerate()`, and `brake()`. You are copying those fields and methods into each class. The safety team changes the braking logic. How many places do you update?

**Problem without inheritance**: Three. And if you miss one, `Truck` still uses the old brake logic while `PetrolCar` uses the new one. The duplication is a maintenance liability — every change must be made N times.

**Minimal fix**: Extract the shared code into a parent class `Vehicle`. `PetrolCar`, `ElectricCar`, and `Truck` extend it. The braking logic lives in one place. Change it once, all vehicle types pick it up.

**Full pattern**: This is Inheritance — the parent captures shared state and behavior; children specialize it. Use it only for genuine IS-A relationships. Overuse creates fragile hierarchies.

### Real-life analogy

A car manufacturer makes sedans, SUVs, and trucks. All of them are vehicles: they all have a make, a model, an engine, and they all accelerate and brake the same way. But an SUV also has four-wheel drive controls. A truck has a payload capacity and a trailer hitch. A sports car has a launch control mode.

Each type *inherits* the general vehicle behaviour (accelerate, brake, speed tracking) and *extends* it with type-specific behaviour. If the safety team changes the ABS braking algorithm, every vehicle type picks it up automatically — you change it once in `Vehicle`, not once in each subclass.

This is inheritance: the parent captures shared behaviour, the children specialize.

### Definition

Inheritance is a mechanism where a new class (subclass/child) derives from an existing class (superclass/parent). The subclass inherits the fields and methods of the parent, enabling code reuse, and establishes a hierarchical **IS-A relationship**: `PetrolCar IS-A Vehicle`, `ElectricCar IS-A Vehicle`, `Truck IS-A Vehicle`.

### Python Example

```python
# Parent: shared state and behaviour for ALL vehicle types
class Vehicle:
    def __init__(self, make, model):
        self.make = make
        self.model = model
        self._speed = 0

    def brake(self, kmh):           # shared — one place to change
        self._speed = max(0, self._speed - kmh)
        return f"{self.make} slowed to {self._speed} km/h"

    def get_speed(self):
        return self._speed

    def start(self):                # overridden by children
        return f"{self.make} {self.model} starting."

# PetrolCar IS-A Vehicle — inherits brake(), get_speed(), specializes start()
class PetrolCar(Vehicle):
    def __init__(self, make, model, fuel):
        super().__init__(make, model)
        self.__fuel = fuel

    def start(self):
        return f"{self.make} {self.model} ignites combustion engine."

    def accelerate(self, kmh):
        self._speed += kmh
        self.__fuel -= kmh * 0.05
        return f"Accelerated to {self._speed} km/h (fuel: {self.__fuel:.1f}L)"

# ElectricCar IS-A Vehicle — inherits brake(), adds charge_battery()
class ElectricCar(Vehicle):
    def __init__(self, make, model, battery_kwh):
        super().__init__(make, model)
        self.__battery = battery_kwh

    def start(self):
        return f"{self.make} {self.model} powers up silently."

    def accelerate(self, kmh):
        self._speed += kmh
        self.__battery -= kmh * 0.02
        return f"Accelerated to {self._speed} km/h (battery: {self.__battery:.1f} kWh)"

    def charge_battery(self, kwh):       # ElectricCar-specific
        self.__battery += kwh
        return f"Charged {kwh} kWh. Battery: {self.__battery} kWh"

# Truck IS-A Vehicle — adds payload_kg
class Truck(Vehicle):
    def __init__(self, make, model, payload_kg):
        super().__init__(make, model)
        self.payload_kg = payload_kg

    def start(self):
        return f"{self.make} {self.model} diesel engine rumbles to life."

    def accelerate(self, kmh):
        penalty = kmh // 2 if self.payload_kg > 5000 else kmh   # heavy load = slower
        self._speed += penalty
        return f"Truck accelerated to {self._speed} km/h"
```

### When to use in interviews

Use inheritance when there is a genuine IS-A relationship and the child truly is a specialized version of the parent. Avoid using it just to reuse code — that is what composition is for (see below). `ElectricCar IS-A Vehicle` ✓. `Car extends Engine` ✗ — a car is not an engine, it has one.

---

## 4. Polymorphism

**Question**: You have a fleet — `PetrolCar`, `ElectricCar`, `Truck` — and the fleet manager needs to start all of them. Do you need an `if isinstance(v, PetrolCar)` chain, or can the loop stay clean?

**Problem without polymorphism**: You write:
```python
for v in fleet:
    if isinstance(v, PetrolCar):
        v.ignite_combustion()
    elif isinstance(v, ElectricCar):
        v.power_up_motor()
    elif isinstance(v, Truck):
        v.start_diesel()
```
Add `HybridCar` and you add another `elif`. Every loop that processes vehicles grows. A new vehicle type means hunting down every `isinstance` chain in the codebase.

**Minimal fix**: Every vehicle type overrides `start()` from `Vehicle`. The loop calls `v.start()`. It does not know what type `v` is. Add `HybridCar` by implementing `start()` — the loop does not change.

**Full pattern**: This is Runtime Polymorphism — Python resolves the right `start()` at runtime based on the actual object's type. The fleet manager writes to the `Vehicle` interface; each concrete type handles itself.

### Real-life analogy

A traffic light turns green. Every car at the intersection responds to "green means go" — but each vehicle does it differently. The petrol car ignites its engine and accelerates. The electric car silently surges forward. The truck slowly builds momentum under its load. The traffic light sent one signal. Each vehicle type interpreted it in its own way. That is polymorphism: one interface (`go`), many implementations depending on what is actually at the intersection.

### Definition

Polymorphism means "many forms." It is the ability of different objects to respond to the same method call in different ways. Two types:

- **Compile-time polymorphism** (Method Overloading): same method name, different parameter signatures — resolved at compile time.
- **Runtime polymorphism** (Method Overriding): subclass provides its own implementation of a parent's method — resolved at runtime based on the actual object type.

### Python Example

```python
# All three classes inherit from Vehicle (from the Inheritance section above)
# and override start() and accelerate() differently.

fleet = [  # list of Vehicle
    PetrolCar("Toyota", "Camry", 50),
    ElectricCar("Tesla", "Model 3", 75),
    Truck("Tata", "Prima", payload_kg=8000),
]

# One loop — no isinstance, no if/elif — polymorphism dispatches correctly
for vehicle in fleet:
    print(vehicle.start())           # calls the right start() for each type
    print(vehicle.accelerate(30))    # calls the right accelerate() for each type
    print(vehicle.brake(10))         # calls brake() from Vehicle parent — shared

# Output:
# Toyota Camry ignites combustion engine.
# Accelerated to 30 km/h (fuel: 48.5L)
# Toyota Camry slowed to 20 km/h
# Tesla Model 3 powers up silently.
# Accelerated to 30 km/h (battery: 74.4 kWh)
# Tesla Model 3 slowed to 20 km/h
# ...
```

**Method overloading (compile-time) — Python uses default args:**
```python
class Car:
    def refuel(self, litres=40.0, grade="91"):
        return f"Added {litres}L of grade-{grade} fuel"

c = Car()
c.refuel()           # default: 40L, grade 91
c.refuel(20, "95")   # override both
```

### When to use in interviews

Polymorphism is the mechanism that makes extensibility real. When an interviewer asks "how would you add a new vehicle type?", the answer is: "I add a new class that extends `Vehicle` and implements `start()` and `accelerate()`. The fleet manager loop already calls `v.start()` — it picks up the new type automatically. No changes to existing code." That is the Open/Closed Principle in action, enabled by polymorphism.

---

## IS-A vs HAS-A: Inheritance vs Composition

### IS-A (Inheritance)

A `Dog` IS-A `Animal`. A `SavingsAccount` IS-A `BankAccount`. The child is a specialized version of the parent. Use inheritance here.

### HAS-A (Composition)

A `Car` HAS-A `Engine`. A `House` HAS-A `Kitchen`. An `Order` HAS-A `Customer`. One object *contains* another — it is not a specialized version of it. Use composition here.

```python
# Inheritance: Car IS-A Vehicle  ✓
class Car(Vehicle):
    pass

# Composition: Car HAS-A Engine  ✓
class Car:
    def __init__(self, model):
        self.model = model
        self.engine = Engine()  # Car owns an Engine — does not inherit from it

    def start(self):
        return self.engine.start()  # Delegate to the Engine

class Engine:
    def start(self):
        return "Engine running."

    def stop(self):
        return "Engine off."
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

```python
# Violation: tax logic duplicated in two services
class OrderService:
    def calculate_tax(self, amount):
        return amount * 0.18

class InvoiceService:
    def calculate_tax(self, amount):
        return amount * 0.18


# DRY: one authoritative source
class TaxCalculator:
    TAX_RATE = 0.18

    @staticmethod
    def calculate(amount):
        return amount * TaxCalculator.TAX_RATE

class OrderService:
    def calculate_tax(self, amount):
        return TaxCalculator.calculate(amount)

class InvoiceService:
    def calculate_tax(self, amount):
        return TaxCalculator.calculate(amount)
```

When the tax rate changes, you update one constant. Not five files.

---

### KISS — Keep It Simple, Stupid

A Swiss Army knife has a blade, scissors, screwdriver, bottle opener, and toothpick. If you need to cut a rope, a single fixed-blade knife is better. Fewer parts, full grip, full blade. The Swiss Army knife is over-engineered for this task.

**KISS in code:** the simplest solution that correctly solves the problem is usually the best solution. Do not reach for patterns, abstractions, and frameworks until the problem genuinely requires them.

```python
# Over-engineered: Strategy pattern + Factory just to add two numbers
from abc import ABC, abstractmethod

class Operation(ABC):
    @abstractmethod
    def execute(self, a, b):
        pass

class AddOperation(Operation):
    def execute(self, a, b):
        return a + b

class CalculatorFactory:
    @staticmethod
    def get_operation(op_type):
        if op_type == "ADD":
            return AddOperation()
        raise ValueError("Unknown operation")

# KISS:
def add(a, b):
    return a + b
```

Apply patterns when the problem requires extensibility or multiple implementations. Not before.

---

### YAGNI — You Aren't Gonna Need It

You do not own a car. You might buy one in five years. Building a garage now — spending money, time, and space — for a car you do not yet have is wasteful. Build the garage when you buy the car.

**YAGNI in code:** do not implement features until they are actually needed. Speculative code — built "just in case" — must still be maintained, tested, and understood by every engineer who reads it, while delivering zero current value.

```python
# Violation: implementing features for requirements that do not exist yet
class UserService:
    def create_user(self, name, email):
        # needed now
        pass

    def create_user_in_tenant(self, name, email, tenant_id):
        # maybe someday
        pass

    def create_user_from_ldap(self, entry):
        # hypothetical enterprise need
        pass

# YAGNI:
class UserService:
    def create_user(self, name, email):
        return User(name, email)
```

Add the LDAP integration when an enterprise customer actually requires it. Until then, it is dead code.

---

### Law of Demeter — Don't Talk to Strangers

A customer walks to the checkout. The cashier says: "Open your wallet, find the card compartment, take out the Visa card, and hand it to me." That is wrong. The cashier is reaching through the customer into the wallet into the card compartment. That is three levels of indirection the cashier has no business knowing about.

The correct interaction: "That will be $45." The customer handles their own wallet. The cashier does not need to know what is inside it.

**Law of Demeter in code:** an object should only call methods on (1) itself, (2) objects passed in as parameters, (3) objects it created, (4) its own direct fields. It should not chain through returned objects — that traverses "strangers."

```python
# Violation: cashier reaching into customer → wallet → money
def process_payment(customer, amount):
    customer.get_wallet().get_money().deduct(amount)
# If Wallet is refactored, this breaks — and this code had no right to know about Wallet.

# Law of Demeter: talk to your direct friend only
def process_payment(customer, amount):
    customer.pay(amount)  # Tell the customer to pay — they handle their own wallet

class Customer:
    def __init__(self, wallet):
        self._wallet = wallet

    def pay(self, amount):
        self._wallet.deduct(amount)
```

Another common violation:

```python
# Violation
city = order.get_customer().get_address().get_city()

# Better: Order exposes what callers need, hides its internal structure
city = order.get_customer_city()

class Order:
    def __init__(self, customer):
        self._customer = customer

    def get_customer_city(self):
        return self._customer.get_city()
```

---

## Quick Revision Table

| Concept | Real-world Analogy | Core Rule |
|---|---|---|
| **Class** | Cookie cutter | Blueprint — no memory until instantiated |
| **Object** | A cookie | Instance with its own state |
| **Encapsulation** | Car hides `__fuel` — you accelerate, not reach into the tank | Private fields + public controlled interface |
| **Abstraction** | Rental car — you press accelerate, not understand the engine | Expose what, hide how |
| **Inheritance** | PetrolCar / ElectricCar / Truck all IS-A Vehicle | Child reuses and specializes parent; IS-A |
| **Polymorphism** | Traffic light turns green — each vehicle responds its own way | One interface, many implementations |
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

> For Python-specific syntax, access modifiers, and advanced patterns: see [python-oops.md](python-oops.md)

---

## Interviewer Follow-Up Questions

**On encapsulation:**
- "I see you have private fields with public getters and setters. What's the point — the field is effectively public?" → A setter can validate: `setAge(age)` throws if `age < 0`. A getter can compute: `getFullName()` concatenates `firstName + lastName`. The internal representation can change without breaking callers: if you change from storing `firstName` + `lastName` to a single `name` field, callers using `getFullName()` don't notice. Setters also let you add logging, caching, or notification later. Raw public fields give you none of this.

**On inheritance:**
- "When does inheritance break down? Give a concrete example." → The Square-Rectangle problem: `Square extends Rectangle`. `Rectangle.setWidth(5); rectangle.setHeight(10); assertThat(rectangle.area()).isEqualTo(50)` — fails for Square (setting width also changes height). Square violates the Liskov Substitution Principle — you can't substitute a Square where a Rectangle is expected. This happens whenever a subclass has a stricter invariant than the parent. Fix: prefer composition (`Square` has-a `Side`), or flatten the hierarchy.
- "Java doesn't support multiple inheritance of classes. Why? What problem does it prevent?" → Diamond problem: if C inherits from both A and B, and both A and B override a method from a common parent, which version does C get? Java's solution: classes can only have one parent (single inheritance). Interfaces (which have no state) can be multiply-implemented without the diamond problem. Java 8+ default interface methods re-introduce a limited form of the diamond problem but require explicit resolution in the implementing class.

**On polymorphism:**
- "What's the difference between method overloading and method overriding?" → Overloading: same method name, different parameter signatures, resolved at compile time (static dispatch). `add(int, int)` vs `add(double, double)`. Overriding: same signature in subclass, resolved at runtime based on actual object type (dynamic dispatch). Overriding enables polymorphism — the same `animal.speak()` call produces different output for Dog vs Cat. Overloading is a convenience for callers; overriding is the mechanism for runtime behavior variation.

**On abstraction:**
- "How does abstraction differ from encapsulation? Aren't they the same?" → Encapsulation: hiding *how* something is implemented (private fields, controlled access). Abstraction: hiding *what* the implementation is — defining an interface without specifying the concrete type. `List<T>` is abstraction — callers know it's a list but not whether it's an `ArrayList` or `LinkedList`. `ArrayList` encapsulates its internal array — callers can't directly modify the array. You can have abstraction without encapsulation (a public abstract class with public fields) and encapsulation without abstraction (a concrete class with private fields and no interface).
