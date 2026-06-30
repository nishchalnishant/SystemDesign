> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Java OOP syntax reference for LLD interviews — classes, objects, access modifiers, interfaces, abstract classes, generics, and Java-specific patterns.
>
> **Key topics:**
> - Class structure: fields, constructors, methods; `public`/`private`/`protected`/package-private; `static` vs instance members; `final` for immutability
> - Interfaces: `interface` = pure contract (all methods abstract by default); `implements`; from Java 8: `default` methods allow interface evolution without breaking implementors
> - Abstract classes: `abstract class` = partial implementation; cannot instantiate; `extends`; use when subclasses share code; interface = behavior contract, abstract class = shared code
> - Key Java-specific: `@Override` annotation; `equals()`/`hashCode()` contract; `Comparable` vs `Comparator`; generics (`List<T>`, bounded wildcards `<T extends Comparable<T>>`)
> - Enums: use for fixed set of constants (Order.Status: PENDING, SHIPPED, DELIVERED); enums in Java are classes and can have methods
> - Common patterns in LLD: `ParkingLot implements Singleton`; `VehicleFactory.create(type)`; `PaymentStrategy` interface with `CreditCard`, `UPI` implementations
>
> **Key takeaway:** For LLD interviews in Java — interfaces for behavior contracts, abstract classes for shared implementation, enums for state machines; master the Builder pattern via telescoping constructor problem.

---
module: 06-lld
topic: Oop Fundamentals
status: unread
tags: [06-lld, system-design, oop-fundamentals]
---
# JAVA OOPs

Here is a Java OOPs Revision Guide: Part 1 focused on the absolute fundamentals: Syntax, Classes, and Objects.

Since you are new to Java, think of this as your "Cheat Sheet" to understand how a Java file is structured and how to bring your code to life.

---

## Topic Mindmap

```
[Java OOPs — Syntax Reference]
├── Part 1: Class and Object
│   ├── Class: blueprint (recipe); Object: instance (cake)
│   ├── new keyword: allocates heap memory + calls constructor
│   ├── this keyword: refers to current object instance
│   └── static members: shared across all instances (no this)
├── Part 2: Arrays and Strings
│   ├── Arrays: fixed-size, zero-indexed, int[] arr = new int[5]
│   ├── String: immutable; == compares reference, .equals() compares value
│   └── StringBuilder: mutable, use for concatenation in loops
├── Part 3: Methods and Control Flow
│   ├── Method signature: return type + name + parameters
│   ├── void: no return value
│   ├── Overloading: same name, different parameter types/count
│   └── Control flow: if/else, for, while, switch
├── Part 4: Constructors Deep Dive
│   ├── Default constructor: provided by compiler if none defined
│   ├── Parameterized constructor: initializes fields on creation
│   ├── Constructor chaining: this() calls another constructor
│   └── Cannot be called after object creation — use a factory method
├── Access Modifiers
│   ├── private: class only
│   ├── protected: class + subclasses + same package
│   ├── public: everywhere
│   └── default (package-private): same package only
├── Static vs Instance
│   ├── static field: one copy per class (counter, constants)
│   ├── instance field: one copy per object (name, balance)
│   ├── static method: no access to this or instance fields
│   └── Use static for utility methods (Math.abs, Collections.sort)
├── Key Java Mechanics
│   ├── Primitive types: int, double, boolean, char (stack allocated)
│   ├── Reference types: objects, arrays (heap allocated, null default)
│   └── Pass-by-value: primitives copy value; objects copy reference
└── Interview Angles
    ├── What is the difference between == and .equals()?
    ├── Why are Strings immutable in Java?
    ├── When does a default constructor disappear?
    └── What is the output of static initializer blocks?
```

## Part 1

***

### 1. The Core Concept: Class vs. Object

Before writing code, you must understand the mental model.

* Class (The Blueprint): A logical template. It defines _what_ data and actions an entity will have, but it doesn't occupy memory for data yet.
* Object (The House): A physical instance of the class. It occupies memory and holds actual data. You can build thousands of objects from one class.

Analogy:

* Class: The recipe for a Chocolate Cake.
* Object: The actual cake sitting on your table.

***

### 2. The Anatomy of a Java Class

Every Java class follows a specific structure. Here is the skeleton syntax you need to memorize.

```python
# 1. Module-level imports (bringing in other tools)
# import something

# 2. Class Declaration (PascalCase)
class Student:

    # 3. Fields / Attributes (State) -> snake_case
    # Defined in __init__

    # 4. Methods (Behavior) -> snake_case
    def __init__(self, name: str, age: int):
        self.name = name
        self.age = age

    def study(self):
        print(f"{self.name} is studying.")
```

***

### 3. Constructors: The "Setup" Method

When you create an object, you often want to set it up immediately (e.g., giving a Student a name). This is done using a Constructor.

* Rule 1: Must have the exact same name as the class.
* Rule 2: Must not have a return type (not even `void`).
* Rule 3: It runs automatically when you use the `new` keyword.

```python
class Student:
    # Default / parameterized constructor using optional argument
    def __init__(self, input_name: str = "Unknown"):
        self.name = input_name
```

***

### 4. The `this` Keyword

This is a reference variable that points to the current object. It is mostly used to resolve naming conflicts when your parameter name is the same as your field name.

```python
class Car:
    def __init__(self, model: str):
        # In Python, 'self.model' refers to the instance attribute
        # 'model' refers to the parameter — no ambiguity issue
        self.model = model
```

***

### 5. Creating Objects (The `new` Keyword)

To use a class, you must instantiate it in a `main` method.

Syntax:

`ClassName objectName = new Constructor();`

* `ClassName`: The type of variable.
* `objectName`: The reference variable name (like a remote control).
* `=`: Assignment operator.
* `new`: The magic keyword that allocates memory in the heap.
* `Constructor()`: Initializes the object.

***

### 6. Static vs. Non-Static (Instance)

This is often the most confusing part for beginners.

* Instance (Non-Static): Belongs to the Object. Every object has its own copy.
  * _Example:_ `eyeColor`. Your eye color is unique to you.
* Static: Belongs to the Class. All objects share one single copy.
  * _Example:_ `populationCount`. If a baby is born, the count goes up for _everyone_.

```python
class Human:
    population = 0  # Class variable (Shared by all Humans)

    def __init__(self, name: str):
        self.name = name        # Instance variable (Unique per person)
        Human.population += 1  # Increasing the shared counter
```

***

### 7. Putting It All Together: A "SmartPhone" Example

Here is a complete, runnable code block combining everything above.

```python
# 1. The Class Definition
class SmartPhone:
    os_type = "Android"  # Class variable (Shared by all phones of this class)

    def __init__(self, brand: str, battery_level: int):
        self.brand = brand
        self.battery_level = battery_level

    def call(self, number: str):
        print(f"{self.brand} is calling {number}")

    def show_stats(self):
        print(f"Phone: {self.brand} | OS: {SmartPhone.os_type}")


# 2. Main Execution
if __name__ == "__main__":
    p1 = SmartPhone("Samsung", 85)
    p2 = SmartPhone("Pixel", 90)

    p1.call("555-0199")
    p2.show_stats()  # Output: Phone: Pixel | OS: Android

    # Changing a class variable affects EVERYONE
    SmartPhone.os_type = "Android 14"

    print("After Update:")
    p1.show_stats()  # Output: Phone: Samsung | OS: Android 14
    p2.show_stats()  # Output: Phone: Pixel   | OS: Android 14
```

#### Quick Syntax Cheat Sheet

| **Action**    | **Syntax**                                |
| ------------- | ----------------------------------------- |
| Define Class  | `class Name { ... }`                      |
| Create Object | `Name obj = new Name();`                  |
| Constructor   | `public Name() { ... }` (No return type!) |
| Access Method | `obj.methodName();`                       |
| Access Field  | `obj.fieldName;`                          |
| Refer to self | `this.variableName`                       |
| Shared Data   | `static int variableName`                 |



## Part 2

These are often where beginners face their first "gotchas" (like index errors or string comparison issues), so I will highlight those specifically.

***

### 1. Java Arrays

"The Fixed Container"

An Array is a container object that holds a fixed number of values of a single type. Once you create an array with a specific size (e.g., 5 slots), you cannot change its size.

* Key Characteristic: Fixed Size.
* Indexing: Starts at 0 (not 1). The last index is `length - 1`.
* Performance: Very fast access if you know the index.

**A. Syntax & Creation**

There are two main ways to create an array.

Method 1: Pre-allocated list (Empty)

Use this when you know _how many_ items you need, but not _what_ they are yet.

```python
# Create 5 slots initialized to 0
scores = [0] * 5  # [0, 0, 0, 0, 0]

# Setting values
scores[0] = 95
scores[1] = 88
```

Method 2: Initialization (Filled)

Use this when you already know the values.

```python
names = ["Alice", "Bob", "Charlie"]
```

**B. Iterating (Looping)**

You will almost always use a loop to go through an array.

```python
numbers = [10, 20, 30, 40, 50]

# 1. Index-based loop (Good if you need the index)
for i in range(len(numbers)):
    print(f"Index {i}: {numbers[i]}")

# 2. For-each loop (Good for just reading values)
# Read as: "For each 'num' in 'numbers'..."
for num in numbers:
    print(f"Value: {num}")
```

**C. The "Gotcha": ArrayIndexOutOfBoundsException**

This is the most common error. It happens if you try to access a slot that doesn't exist.

```python
arr = [0] * 3  # Indices are 0, 1, 2
# print(arr[3])  # IndexError! Index 3 is the 4th slot.
```

***

### 2. Java Strings

"The Immutable Text"

In Java, a `String` is an Object, not a primitive type (like `int` or `char`). This means it has methods you can call.

* Key Characteristic: Immutability. Once a String object is created, it cannot be changed. If you "modify" a string, Java actually creates a generic _new_ string in memory and discards the old one.

**A. Creation: Literal vs. New**

How you create a string matters for memory.

1.  String Literal (standard in Python — all strings are interned by default for small strings):

    ```python
    s1 = "Hello"
    ```
2.  Explicit construction (rarely needed in Python):

    ```python
    s2 = str("Hello")
    ```

**B. The Comparison Trap (`is` vs `==`)**

In Python strings are compared with `==` for value equality. `is` checks identity (same object).

* `is`: Checks identity (same object in memory). Do not use for string value comparison.
* `==`: Compares content (values). Always use this for strings.

```python
s1 = "Java"
s2 = "Java"
s3 = "".join(["J", "a", "v", "a"])  # Constructed string, may be a different object

print(s1 == s2)   # True  (same content)
print(s1 == s3)   # True  (same content) — USE THIS
print(s1 is s2)   # True  (interned literals, same object — do not rely on this)
print(s1 is s3)   # False (different object)
```

**C. Essential String Methods**

You don't need to memorize all of them, but these 5 are essential:

1. `length()`: Returns the count of characters.
2. `charAt(index)`: Returns the character at a specific position.
3. `substring(start, end)`: Extracts a portion of the text.
4. `toLowerCase()` / `toUpperCase()`: Changes case.
5. `trim()`: Removes whitespace from start and end.

***

### 3. Practical Revision: A "Student Database" Code

Here is a single program combining Arrays and Strings to simulate a mini-database.

```python
if __name__ == "__main__":
    # 1. List of strings
    students = ["Alice", "Bob", "Charlie", "David"]

    # 2. List of integers (parallel list for grades)
    grades = [85, 42, 90, 76]

    print("--- Class Report ---")

    # 3. Loop through the lists
    for name, score in zip(students, grades):
        # String manipulation: convert to uppercase for display
        display_name = name.upper()

        # Conditional logic
        result = "PASSED" if score >= 50 else "FAILED"

        print(f"{display_name} : {score} -> {result}")

    # 4. Demonstrate immutability
    original = "   Python   "
    cleaned = original.strip()  # original is NOT changed

    print(f"\nOriginal: '{original}'")
    print(f"Cleaned:  '{cleaned}'")
```

#### Quick Syntax Cheat Sheet

| **Action**      | **Syntax/Method**       | **Note**                                       |
| --------------- | ----------------------- | ---------------------------------------------- |
| Create List     | `arr = [0] * 5`         | Dynamic size; pre-fill with 0s.                |
| Get List Size   | `len(arr)`              | Built-in function.                             |
| Get String Size | `len(s)`                | Same built-in function for strings.            |
| Compare Strings | `s1 == s2`              | `==` compares content; use `is` for identity.  |
| Get Char        | `s[0]`                  | Gets the first character.                      |
| Get Substring   | `s[0:3]`                | Slice: index 0, 1, 2 (3 is exclusive).         |

## Part 3

Here is Java OOPs Revision Guide: Part 3, covering Methods (the behavior) and Control Flow (the logic).

This is where your static code starts to actually _do_ things.

***

### 1. Java Methods

"The Verbs"

A method is a block of code that runs only when it is called. You use methods to break a complex problem into small, manageable chunks (like "login", "calculateTotal", "printReceipt").

**A. The Anatomy of a Method**

You need to memorize this signature structure:

`AccessModifier ReturnType MethodName(Parameters) { Body }`

* Access Modifier: `public` (everyone can see), `private` (only this class), etc.
* Return Type: The data type the method gives back (e.g., `int`, `String`). Use `void` if it gives back nothing.
* Method Name: camelCase (e.g., `calculateTax`).
* Parameters: Inputs inside the parentheses `(type name)`.

**B. Return Types vs. Void**

This is a common point of confusion.

* `void`: Performs an action but returns no value. (e.g., Printing to console, saving to DB).
* Data Type (`int`, `String`, etc.): Calculates and returns a value to the caller.

```python
class Calculator:

    # 1. None-returning method (Action only)
    def print_welcome(self) -> None:
        print("--- Welcome to Calculator ---")
        # No return statement needed

    # 2. int-returning method
    def add(self, a: int, b: int) -> int:
        total = a + b
        return total
```

**C. Method Overloading**

This is a core OOP concept (Compile-time Polymorphism). You can have multiple methods with the same name as long as their parameters are different.

```python
# Python uses duck typing — no overloading by signature needed
class Printer:
    def print_value(self, *args):
        if len(args) == 1:
            print(args[0])
        elif len(args) == 2:
            print(f"{args[0]} & {args[1]}")
```

***

### 2. Control Flow statements

"The Logic / The Brain"

Control flow dictates the order in which statements are executed. Without this, your code just reads from top to bottom like a book.

**A. Conditional Logic (If / Else / Switch)**

Use If/Else for ranges or complex conditions. Use Switch for specific fixed values.

```python
battery = 15

# IF-ELIF-ELSE LADDER
if battery > 50:
    print("Green Light")
elif battery > 20:
    print("Yellow Light")
else:
    print("Red Light")

# MATCH STATEMENT (Python 3.10+, equivalent to switch)
day = 3
match day:
    case 1:
        print("Monday")
    case 2:
        print("Tuesday")
    case _:
        print("Midweek")
```

**B. Loops (For, While, Do-While)**

Loops allow you to repeat code.

* `for` loop: Use when you know exactly how many times to loop (e.g., "Run 10 times").
* `while` loop: Use when you don't know the number of iterations (e.g., "Run until user types 'exit'").
* `do-while` loop: Guaranteed to run at least once (e.g., "Show menu, then ask to continue").

```python
# 1. FOR loop (fixed range)
for i in range(1, 6):
    print(i, end=" ")  # Output: 1 2 3 4 5
print()

# 2. WHILE loop (condition based)
count = 5
while count > 0:
    print(count, end=" ")  # Output: 5 4 3 2 1
    count -= 1
```

***

### 3. Combined Practice: A "Mini ATM" Class

Here is how Methods and Control Flow work together in a real object.

Java

```python
class ATM:
    def __init__(self, initial_balance: float):
        self.__balance = initial_balance  # State (Data)
        self.__pin = 1234

    # Method 1: Boolean return type (Logic)
    def verify_pin(self, input_pin: int) -> bool:
        return input_pin == self.__pin

    # Method 2: None-returning method with control flow
    def withdraw(self, amount: int) -> None:
        # Validation Logic
        if amount <= 0:
            print("Error: Amount must be positive.")
        elif amount > self.__balance:
            print("Error: Insufficient Funds.")
        else:
            # Success Logic
            self.__balance -= amount
            print(f"Success! Please take your ${amount}")

    # Method 3: Getter
    def get_balance(self) -> float:
        return self.__balance


# Main Execution
if __name__ == "__main__":
    my_atm = ATM(1000.0)

    # Using Logic (if/else) with method calls
    if my_atm.verify_pin(1234):
        print("PIN Accepted.")
        my_atm.withdraw(500)   # Valid
        my_atm.withdraw(5000)  # Invalid (Insufficient funds)
    else:
        print("Wrong PIN!")
```

#### Quick Syntax Cheat Sheet

| **Keyword**    | **Purpose**                    | **Example**                      |
| -------------- | ------------------------------ | -------------------------------- |
| `-> None`      | Method returns nothing         | `def run(self) -> None: ...`     |
| `return`       | Exits method & sends data back | `return a + b`                   |
| `break`        | Exits a loop                   | `break`                          |
| `continue`     | Skips current loop iteration   | `continue`                       |
| `if x == y:`   | Checks equality                | `if age == 18: ...`              |
| `!=`           | Checks "Not Equal"             | `if age != 0: ...`               |
| `and` / `or`   | Logical AND / OR               | `if age > 18 and has_id: ...`    |

***

## Part 4

***

### 1. Constructors

"The Birth of an Object"

A constructor is a special method that is called automatically when you create an object (using `new`). Its main job is to initialize the object's fields.

* Rule: It must have the exact same name as the class and no return type.

**A. Default vs. Parameterized**

* Default Constructor: Has no arguments. Java provides an invisible one if you don't write _any_ constructor.
* Parameterized Constructor: Takes arguments to set specific values during creation.

```python
class User:
    # Use default parameter to handle both default and parameterized cases
    def __init__(self, name: str = "Guest"):
        self.name = name
```

**B. Constructor Overloading**

Python uses default parameter values to handle flexible construction.

```python
u1 = User()         # name = "Guest"
u2 = User("Alice")  # name = "Alice"
```

***

### 2. Static vs. Instance

"Shared vs. Unique"

This is the most critical memory concept in Java.

* Instance (Non-Static): Belongs to the Object. Each object has its own copy.
  * _Analogy:_ Your Toothbrush. Everyone has their own.
* Static: Belongs to the Class. There is only one copy shared by all objects.
  * _Analogy:_ The Bathroom Light. Everyone shares the same one.

**A. The `static` Keyword**

Use `static` for properties that should be common to all objects (like a counter or a constant).

```python
class Employee:
    company = "Google"  # Class variable: Shared by ALL employees

    def __init__(self, name: str):
        self.name = name  # Instance variable: Unique to each employee

# Usage
e1 = Employee("John")
e2 = Employee("Jane")

# e1.company is "Google"
# e2.company is "Google"

# Change it via the Class — affects all instances
Employee.company = "Alphabet"

# Now e1.company AND e2.company are both "Alphabet"!
```

**B. Static Methods**

Static methods can be called without creating an object. They are utility functions (like `Math.sqrt()`).

* _Restriction:_ A static method cannot access instance variables (because it doesn't know _which_ object you are talking about).

***

### 3. Access Modifiers

"The Security Guards"

Access modifiers determine which other classes can see and use your variables and methods.

| **Modifier** | **Keyword**    | **Visibility**               | **Analogy**                          |
| ------------ | -------------- | ---------------------------- | ------------------------------------ |
| Public       | `public`       | Everywhere (Global)          | Public Park (Anyone can enter)       |
| Private      | `private`      | Only inside the Same Class   | Your Diary (Only you can read)       |
| Protected    | `protected`    | Same Package + Subclasses    | Family Money (Family + Kids inherit) |
| Default      | _(no keyword)_ | Only inside the Same Package | Office Water Cooler (Only coworkers) |

**Best Practice: Encapsulation**

Always make your fields `private` and your methods `public` (unless they are internal helper methods).

```python
class BankAccount:
    def __init__(self):
        self.__balance: float = 0.0  # Private: no one can touch the money directly

    def deposit(self, amount: float) -> None:  # Public: everyone can use the bank services
        if amount > 0:
            self.__balance += amount  # Safe internal access
```

***

### 4. Combined Revision Code

Here is a `School` system combining Constructors, Static logic, and Modifiers.

```python
class Student:
    # Class variable — shared counter (equivalent to private static)
    _total_students: int = 0

    # Class constant
    SCHOOL_NAME: str = "Lincoln High"

    def __init__(self, name: str):
        self.__name: str = name                   # Private field
        Student._total_students += 1
        self.__id: int = Student._total_students  # Auto-assigned ID

    @staticmethod
    def get_total_students() -> int:              # Static method: class-level data only
        return Student._total_students

    def introduce(self) -> None:                  # Instance method: uses instance data
        print(f"Hi, I am {self.__name} (ID: {self.__id})")
        print(f"I go to {Student.SCHOOL_NAME}")


if __name__ == "__main__":
    print(f"School: {Student.SCHOOL_NAME}")

    s1 = Student("Alice")
    s2 = Student("Bob")

    s1.introduce()  # ID: 1
    s2.introduce()  # ID: 2

    print(f"Total Enrolled: {Student.get_total_students()}")  # 2
```

#### Quick Syntax Cheat Sheet

| **Feature**     | **Syntax**                        | **When to use?**                                |
| --------------- | --------------------------------- | ----------------------------------------------- |
| Constructor     | `def __init__(self): ...`         | To set up default values on object creation.    |
| Class Variable  | `count = 0` (top of class body)   | For shared data (counters, constants).          |
| Static Method   | `@staticmethod\ndef run(): ...`   | For utility tools (math helpers, converters).   |
| Private Field   | `self.__age = 0`                  | ALWAYS for internal class fields (attributes).  |
| Public Method   | `def get_age(self): ...`          | For methods meant for the outside world.        |
| Access Static   | `ClassName.variable`              | Don't access via instance (e.g., `s1.count`).  |

