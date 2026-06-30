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

```python
from abc import ABC, abstractmethod


# ========================================================
# 1. CLASS & OBJECT
# ========================================================
# A class is a blueprint for creating objects.
# An object is an instance of a class with its own attributes and methods.
# Example: An Animal class with name and species attributes.
class Animal:
    def __init__(self, name: str, species: str):
        self.name = name        # accessible by subclasses (convention)
        self.species = species

    def make_sound(self) -> str:
        return "Some generic sound"

    def __str__(self) -> str:
        return f"{self.name} is a {self.species}"


# ========================================================
# 2. ENCAPSULATION (DATA HIDING)
# ========================================================
# Encapsulation restricts direct access to data,
# allowing controlled access through methods.
# Example: A BankAccount where balance is private.
class BankAccount:
    def __init__(self, account_holder: str, balance: float):
        self._account_holder = account_holder
        self.__balance = balance  # Private — cannot be set from outside directly

    def deposit(self, amount: float) -> str:
        if amount > 0:
            self.__balance += amount
            return f"Deposited {amount}, New Balance: {self.__balance}"
        return "Invalid deposit amount"

    def withdraw(self, amount: float) -> str:
        if 0 < amount <= self.__balance:
            self.__balance -= amount
            return f"Withdrew {amount}, Remaining Balance: {self.__balance}"
        return "Insufficient funds"

    def get_balance(self) -> float:
        return self.__balance


# ========================================================
# 3. ABSTRACTION (HIDING IMPLEMENTATION DETAILS)
# ========================================================
# Abstraction hides complex details and exposes only necessary functionality.
# Abstract classes cannot be instantiated and force subclasses to implement required methods.
class Vehicle(ABC):
    @abstractmethod
    def start_engine(self) -> str: ...

    @abstractmethod
    def stop_engine(self) -> str: ...


class Car(Vehicle):
    def start_engine(self) -> str:
        return "Car engine started"

    def stop_engine(self) -> str:
        return "Car engine stopped"


# ========================================================
# 4. INHERITANCE (IS-A RELATIONSHIP)
# ========================================================
# Inheritance allows a class (child) to acquire properties and behaviors
# of another class (parent). Promotes code reuse. Dog IS-A Animal.
class Dog(Animal):
    def __init__(self, name: str, breed: str):
        super().__init__(name, "Dog")
        self._breed = breed

    def make_sound(self) -> str:
        return "Bark!"


# ========================================================
# 5. MULTIPLE INHERITANCE (via multiple base classes in Python)
# ========================================================
# A class can inherit from multiple bases.
# Bird IS-A Animal and also CAN Fly.
class Flying(ABC):
    @abstractmethod
    def fly(self) -> str: ...


class Bird(Animal, Flying):
    def __init__(self, name: str, species: str):
        super().__init__(name, species)

    def make_sound(self) -> str:
        return "Chirp!"

    def fly(self) -> str:
        return "I can fly!"


# ========================================================
# 6. MULTILEVEL INHERITANCE
# ========================================================
# A class is derived from another derived class.
# Puppy -> Dog -> Animal
class Puppy(Dog):
    def __init__(self, name: str, breed: str, age: int):
        super().__init__(name, breed)
        self._age = age

    def is_cute(self) -> str:
        return f"{self.name} is a cute {self._age}-month-old puppy!"


# ========================================================
# 7. POLYMORPHISM (METHOD OVERRIDING)
# ========================================================
# Polymorphism: same method name, different behavior per class.
class Cat(Animal):
    def __init__(self, name: str):
        super().__init__(name, "Cat")

    def make_sound(self) -> str:
        return "Meow!"


class Lion(Animal):
    def __init__(self, name: str):
        super().__init__(name, "Lion")

    def make_sound(self) -> str:
        return "Roar!"


# Polymorphism in action: one function, any Animal subtype
def animal_sound(animal: Animal) -> str:
    return animal.make_sound()


# ========================================================
# 8. COMPOSITION (HAS-A RELATIONSHIP)
# ========================================================
# Instead of inheritance, a class contains another class as a field.
# CarWithEngine HAS-A Engine.
class Engine:
    def start(self) -> str:
        return "Engine started"

    def stop(self) -> str:
        return "Engine stopped"


class CarWithEngine:
    def __init__(self, model: str):
        self._model = model
        self._engine = Engine()  # Composition: Car owns an Engine

    def start_car(self) -> str:
        return f"{self._model}: {self._engine.start()}"

    def stop_car(self) -> str:
        return f"{self._model}: {self._engine.stop()}"


# ========================================================
# 9. STATIC METHODS
# ========================================================
# Static methods don't use instance attributes; called without an instance.
class Utility:
    @staticmethod
    def greet() -> str:
        return "Hello, welcome to OOP in Python!"

    @staticmethod
    def describe_class() -> str:
        return "This is the Utility class."


# Method Overloading equivalent (use *args in Python)
def add(*args: int) -> int:
    return sum(args)


# ========================================================
# 10. DEMONSTRATION
# ========================================================
if __name__ == "__main__":
    dog  = Dog("Buddy", "Golden Retriever")
    cat  = Cat("Whiskers")
    lion = Lion("Simba")

    # Polymorphism
    print(animal_sound(dog))   # Bark!
    print(animal_sound(cat))   # Meow!
    print(animal_sound(lion))  # Roar!

    # Encapsulation
    account = BankAccount("Alice", 1000)
    print(account.deposit(500))
    print(account.withdraw(300))
    print(f"Balance: {account.get_balance()}")

    # Abstraction
    car = Car()
    print(car.start_engine())
    print(car.stop_engine())

    # Composition
    my_car = CarWithEngine("Tesla Model X")
    print(my_car.start_car())
    print(my_car.stop_car())

    # Multiple Inheritance
    bird = Bird("Eagle", "Bird of Prey")
    print(bird.fly())

    # Static Methods
    print(Utility.greet())
    print(Utility.describe_class())

    # Multilevel Inheritance
    puppy = Puppy("Max", "Labrador", 3)
    print(puppy.is_cute())

    # add() with different arg counts
    print(add(5, 10))       # 15
    print(add(5, 10, 20))   # 35
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

```python
class OrderService:
    def calculate_tax(self, amount: float) -> float:
        return amount * 0.18  # Tax logic here

class InvoiceService:
    def calculate_tax(self, amount: float) -> float:
        return amount * 0.18  # Same tax logic duplicated
```

If the tax rate changes, you need to find and update every copy. You will miss one.

**Good — Single Source of Truth:**

```python
TAX_RATE = 0.18

def calculate_tax(amount: float) -> float:
    return amount * TAX_RATE

class OrderService:
    def calculate_tax(self, amount: float) -> float:
        return calculate_tax(amount)  # One reference

class InvoiceService:
    def calculate_tax(self, amount: float) -> float:
        return calculate_tax(amount)  # Same reference
```

Now, when the tax rate changes, you update exactly one constant in one file.

---

### KISS — Keep It Simple, Stupid

#### Analogy: A Swiss Army Knife vs. a Fixed Blade

A Swiss Army knife is clever. It has a blade, scissors, screwdriver, bottle opener, toothpick, and tweezers all in one. But if you need to cut a thick rope quickly, a simple, fixed-blade knife is better. Fewer moving parts, full grip, full blade length. The Swiss Army knife is over-engineered for this task.

**KISS in code:** Do not add complexity you do not need. The simplest solution that correctly solves the problem is usually the best solution.

**Over-engineered:**

```python
from abc import ABC, abstractmethod

# A "flexible" calculator using Strategy pattern and factory
# for the sole purpose of adding two numbers
class Operation(ABC):
    @abstractmethod
    def execute(self, a: float, b: float) -> float: ...

class AddOperation(Operation):
    def execute(self, a: float, b: float) -> float:
        return a + b

class CalculatorFactory:
    @staticmethod
    def get_operation(op_type: str) -> Operation:
        if op_type == "ADD":
            return AddOperation()
        raise ValueError(f"Unknown operation: {op_type}")

# Usage: CalculatorFactory.get_operation("ADD").execute(2, 3)
```

**KISS — just add the numbers:**

```python
def add(a: float, b: float) -> float:
    return a + b
```

Apply patterns only when the problem genuinely requires them. Premature abstraction creates complexity without value.

---

### YAGNI — You Aren't Gonna Need It

#### Analogy: Don't Build the Garage Yet

You do not own a car. You might buy one in five years. Building a garage right now — spending money, space, and time — for a car you do not have is wasteful. Build the garage when you buy the car.

**YAGNI in code:** Do not implement features until they are actually needed. Speculative generality — building things "just in case" — adds code that must be maintained, tested, and understood, but delivers no current value.

**Violating YAGNI:**

```python
class UserService:
    # Required now
    def create_user(self, name: str, email: str): ...

    # "Maybe we'll need multi-tenancy someday"
    def create_user_in_tenant(self, name: str, email: str, tenant_id: str): ...

    # "Could be useful for enterprise clients"
    def create_user_with_sso_token(self, name: str, email: str, sso_token: str): ...

    # "Might need LDAP integration"
    def create_user_from_ldap(self, ldap_entry): ...
```

**YAGNI — implement what is needed:**

```python
class UserService:
    def create_user(self, name: str, email: str):
        # This is what the system needs right now
        return User(name, email)
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

```python
# Wrong: cashier is going customer → wallet → money → deduct
def process_payment(customer, amount: float):
    customer.get_wallet().get_money().deduct(amount)
```

This tightly couples `process_payment` to the internal structure of `Customer`, `Wallet`, and `Money`. If `Wallet` is refactored, this breaks.

**Law of Demeter — talk only to your direct friend:**

```python
# Right: tell the customer to pay; the customer handles their own wallet
def process_payment(customer, amount: float):
    customer.pay(amount)

class Customer:
    def __init__(self):
        self._wallet = Wallet()

    def pay(self, amount: float):
        # Customer manages their own wallet
        self._wallet.deduct(amount)
```

Now `process_payment` knows nothing about `Wallet`. If the internal structure of `Customer` changes, only `Customer` needs to change.

**Another Example:**

```python
# Violation: chaining through multiple objects
city = order.get_customer().get_address().get_city()

# Better: let Order expose what callers need
city = order.get_customer_city()

class Order:
    def __init__(self, customer):
        self._customer = customer

    def get_customer_city(self) -> str:
        return self._customer.get_city()  # One level of delegation
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
