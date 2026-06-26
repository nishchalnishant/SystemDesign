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
├── Part 4: Constructors & Inheritance
│   ├── super(): delegates to parent constructors/methods
│   └── Magic methods: __str__, __repr__, __len__, __getitem__
├── Access Modifiers (Convention)
│   ├── public: name (default)
│   ├── protected: _name (convention: treat as private)
│   └── private: __name (name mangled to _ClassName__name)
└── Key Python Mechanics
    ├── Pass-by-object-reference: mutable objects can be modified, immutable cannot
    └── Duck Typing: "If it walks like a duck and quacks like a duck, it is a duck"
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
    def __init__(self, name: str, age: int):
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
    def __init__(self, model: str):
        self.model = model  # self.model binds the value to this instance

    def get_model(self) -> str:
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

    def __init__(self, brand: str):
        self.brand = brand

    # Instance method
    def get_brand(self) -> str:
        return self.brand

    # Class method
    @classmethod
    def get_calculator_name(cls) -> str:
        return cls.name

    # Static method
    @staticmethod
    def add(a: float, b: float) -> float:
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
    def log(self, message: str, level: str = "INFO"):
        print(f"[{level}] {message}")

    # *args (variable positional arguments) and **kwargs (variable keyword arguments)
    def log_multiple(self, *messages, **metadata):
        for msg in messages:
            print(f"{msg} | Metadata: {metadata}")
```

### 2. Control Flow and Switch Alternatives

Python supports standard loops and dictionary-based lookups (the common pythonic alternative to switch statements).

```python
def get_http_status(status_code: int) -> str:
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
    def __init__(self, holder: str, balance: float):
        self.holder = holder      # Public
        self._id = "ACC123"       # Protected
        self.__balance = balance  # Private

    def get_balance(self) -> float:
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
    def __init__(self, title: str, pages: int):
        self.title = title
        self.pages = pages

    # String representation (analogous to toString() in Java)
    def __str__(self) -> str:
        return f"'{self.title}' ({self.pages} pages)"

    # Length operator override
    def __len__(self) -> int:
        return self.pages
```
