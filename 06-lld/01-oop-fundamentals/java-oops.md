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

Think of this as your "Cheat Sheet" to understand how a Java file is structured and how to bring your code to life.

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

```java
// 1. Imports (bringing in other tools)
// import java.util.List;

// 2. Class Declaration (PascalCase)
public class Student {

    // 3. Fields / Attributes (State) -> camelCase
    private String name;
    private int age;

    // 4. Constructor
    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // 5. Methods (Behavior) -> camelCase
    public void study() {
        System.out.println(name + " is studying.");
    }
}
```

***

### 3. Constructors: The "Setup" Method

When you create an object, you often want to set it up immediately (e.g., giving a Student a name). This is done using a Constructor.

* Rule 1: Must have the exact same name as the class.
* Rule 2: Must not have a return type (not even `void`).
* Rule 3: It runs automatically when you use the `new` keyword.

```java
public class Student {
    private String name;

    // Parameterized constructor
    public Student(String inputName) {
        this.name = inputName;
    }

    // Overloaded constructor providing a default value
    public Student() {
        this("Unknown");   // constructor chaining via this(...)
    }
}
```

***

### 4. The `this` Keyword

This is a reference variable that points to the current object. It is mostly used to resolve naming conflicts when your parameter name is the same as your field name.

```java
public class Car {
    private String model;

    public Car(String model) {
        // 'this.model' refers to the instance field
        // 'model' (no 'this.') refers to the constructor parameter
        this.model = model;
    }
}
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

```java
public class Human {
    static int population = 0;   // Class variable (shared by all Humans)

    private String name;         // Instance variable (unique per person)

    public Human(String name) {
        this.name = name;
        Human.population += 1;   // Increasing the shared counter
    }
}
```

***

### 7. Putting It All Together: A "SmartPhone" Example

Here is a complete, runnable code block combining everything above.

```java
public class SmartPhone {
    static String osType = "Android";   // Class variable (shared by all phones of this class)

    private String brand;
    private int batteryLevel;

    public SmartPhone(String brand, int batteryLevel) {
        this.brand = brand;
        this.batteryLevel = batteryLevel;
    }

    public void call(String number) {
        System.out.println(brand + " is calling " + number);
    }

    public void showStats() {
        System.out.println("Phone: " + brand + " | OS: " + SmartPhone.osType);
    }

    // 2. Main Execution
    public static void main(String[] args) {
        SmartPhone p1 = new SmartPhone("Samsung", 85);
        SmartPhone p2 = new SmartPhone("Pixel", 90);

        p1.call("555-0199");
        p2.showStats();   // Output: Phone: Pixel | OS: Android

        // Changing a class variable affects EVERYONE
        SmartPhone.osType = "Android 14";

        System.out.println("After Update:");
        p1.showStats();   // Output: Phone: Samsung | OS: Android 14
        p2.showStats();   // Output: Phone: Pixel   | OS: Android 14
    }
}
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

Method 1: Pre-allocated array (Empty)

Use this when you know _how many_ items you need, but not _what_ they are yet.

```java
// Create 5 slots initialized to 0
int[] scores = new int[5];   // [0, 0, 0, 0, 0]

// Setting values
scores[0] = 95;
scores[1] = 88;
```

Method 2: Initialization (Filled)

Use this when you already know the values.

```java
String[] names = {"Alice", "Bob", "Charlie"};
```

**B. Iterating (Looping)**

You will almost always use a loop to go through an array.

```java
int[] numbers = {10, 20, 30, 40, 50};

// 1. Index-based loop (Good if you need the index)
for (int i = 0; i < numbers.length; i++) {
    System.out.println("Index " + i + ": " + numbers[i]);
}

// 2. For-each loop (Good for just reading values)
// Read as: "For each 'num' in 'numbers'..."
for (int num : numbers) {
    System.out.println("Value: " + num);
}
```

**C. The "Gotcha": ArrayIndexOutOfBoundsException**

This is the most common error. It happens if you try to access a slot that doesn't exist.

```java
int[] arr = new int[3];   // Indices are 0, 1, 2
// System.out.println(arr[3]);  // ArrayIndexOutOfBoundsException! Index 3 is the 4th slot.
```

***

### 2. Java Strings

"The Immutable Text"

In Java, a `String` is an Object, not a primitive type (like `int` or `char`). This means it has methods you can call.

* Key Characteristic: Immutability. Once a String object is created, it cannot be changed. If you "modify" a string, Java actually creates a _new_ string in memory and discards the old one.

**A. Creation: Literal vs. New**

How you create a string matters for memory.

1. String Literal (standard, and interned in the String Pool):

    ```java
    String s1 = "Hello";
    ```
2. Explicit construction (creates a new object on the heap, bypassing the pool):

    ```java
    String s2 = new String("Hello");
    ```

**B. The Comparison Trap (`==` vs `.equals()`)**

* `==`: Checks reference/identity (same object in memory). Do not use for string value comparison.
* `.equals()`: Compares content (values). Always use this for strings.

```java
String s1 = "Java";
String s2 = "Java";
String s3 = new String("Java");   // Explicitly constructed — a different object

System.out.println(s1 == s2);          // true  (both interned literals — same object)
System.out.println(s1 == s3);          // false (different object — DO NOT rely on this)
System.out.println(s1.equals(s2));     // true  (same content)
System.out.println(s1.equals(s3));     // true  (same content) — USE THIS
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

```java
public class StudentDatabase {
    public static void main(String[] args) {
        // 1. Array of strings
        String[] students = {"Alice", "Bob", "Charlie", "David"};

        // 2. Array of integers (parallel array for grades)
        int[] grades = {85, 42, 90, 76};

        System.out.println("--- Class Report ---");

        // 3. Loop through the arrays together
        for (int i = 0; i < students.length; i++) {
            // String manipulation: convert to uppercase for display
            String displayName = students[i].toUpperCase();

            // Conditional logic
            String result = grades[i] >= 50 ? "PASSED" : "FAILED";

            System.out.println(displayName + " : " + grades[i] + " -> " + result);
        }

        // 4. Demonstrate immutability
        String original = "   Java   ";
        String cleaned = original.trim();   // original is NOT changed

        System.out.println("\nOriginal: '" + original + "'");
        System.out.println("Cleaned:  '" + cleaned + "'");
    }
}
```

#### Quick Syntax Cheat Sheet

| **Action**      | **Syntax/Method**       | **Note**                                       |
| --------------- | ----------------------- | ----------------------------------------------- |
| Create Array    | `int[] arr = new int[5]`| Fixed size; auto-filled with 0s.               |
| Get Array Size  | `arr.length`            | Property, not a method (no parentheses).        |
| Get String Size | `s.length()`            | Method, not a property (needs parentheses).     |
| Compare Strings | `s1.equals(s2)`         | `.equals()` compares content; `==` is identity. |
| Get Char        | `s.charAt(0)`           | Gets the first character.                       |
| Get Substring   | `s.substring(0, 3)`     | From index 0 up to (but excluding) index 3.     |

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

```java
public class Calculator {

    // 1. void-returning method (Action only)
    public void printWelcome() {
        System.out.println("--- Welcome to Calculator ---");
        // No return statement needed
    }

    // 2. int-returning method
    public int add(int a, int b) {
        int total = a + b;
        return total;
    }
}
```

**C. Method Overloading**

This is a core OOP concept (Compile-time Polymorphism). You can have multiple methods with the same name as long as their parameters are different — the compiler picks the right one based on argument types/count.

```java
public class Printer {

    public void printValue(int value) {
        System.out.println(value);
    }

    public void printValue(int a, int b) {
        System.out.println(a + " & " + b);
    }

    public void printValue(String value) {
        System.out.println(value);
    }
}
```

***

### 2. Control Flow statements

"The Logic / The Brain"

Control flow dictates the order in which statements are executed. Without this, your code just reads from top to bottom like a book.

**A. Conditional Logic (If / Else / Switch)**

Use If/Else for ranges or complex conditions. Use Switch for specific fixed values.

```java
int battery = 15;

// IF-ELSE-IF LADDER
if (battery > 50) {
    System.out.println("Green Light");
} else if (battery > 20) {
    System.out.println("Yellow Light");
} else {
    System.out.println("Red Light");
}

// SWITCH STATEMENT (modern arrow syntax, Java 14+)
int day = 3;
switch (day) {
    case 1 -> System.out.println("Monday");
    case 2 -> System.out.println("Tuesday");
    default -> System.out.println("Midweek");
}
```

**B. Loops (For, While, Do-While)**

Loops allow you to repeat code.

* `for` loop: Use when you know exactly how many times to loop (e.g., "Run 10 times").
* `while` loop: Use when you don't know the number of iterations (e.g., "Run until user types 'exit'").
* `do-while` loop: Guaranteed to run at least once (e.g., "Show menu, then ask to continue").

```java
// 1. FOR loop (fixed range)
for (int i = 1; i <= 5; i++) {
    System.out.print(i + " ");   // Output: 1 2 3 4 5
}
System.out.println();

// 2. WHILE loop (condition based)
int count = 5;
while (count > 0) {
    System.out.print(count + " ");   // Output: 5 4 3 2 1
    count -= 1;
}
```

***

### 3. Combined Practice: A "Mini ATM" Class

Here is how Methods and Control Flow work together in a real object.

```java
public class ATM {
    private double balance;
    private final int pin = 1234;

    public ATM(double initialBalance) {
        this.balance = initialBalance;   // State (Data)
    }

    // Method 1: Boolean return type (Logic)
    public boolean verifyPin(int inputPin) {
        return inputPin == this.pin;
    }

    // Method 2: void-returning method with control flow
    public void withdraw(int amount) {
        // Validation Logic
        if (amount <= 0) {
            System.out.println("Error: Amount must be positive.");
        } else if (amount > balance) {
            System.out.println("Error: Insufficient Funds.");
        } else {
            // Success Logic
            balance -= amount;
            System.out.println("Success! Please take your $" + amount);
        }
    }

    // Method 3: Getter
    public double getBalance() {
        return balance;
    }

    // Main Execution
    public static void main(String[] args) {
        ATM myAtm = new ATM(1000.0);

        // Using Logic (if/else) with method calls
        if (myAtm.verifyPin(1234)) {
            System.out.println("PIN Accepted.");
            myAtm.withdraw(500);    // Valid
            myAtm.withdraw(5000);   // Invalid (Insufficient funds)
        } else {
            System.out.println("Wrong PIN!");
        }
    }
}
```

#### Quick Syntax Cheat Sheet

| **Keyword**    | **Purpose**                    | **Example**                      |
| -------------- | ------------------------------ | --------------------------------- |
| `void`         | Method returns nothing         | `public void run() { ... }`      |
| `return`       | Exits method & sends data back | `return a + b;`                  |
| `break`        | Exits a loop                   | `break;`                         |
| `continue`     | Skips current loop iteration   | `continue;`                      |
| `==`           | Checks equality (primitives)   | `if (age == 18) { ... }`         |
| `!=`           | Checks "Not Equal"             | `if (age != 0) { ... }`          |
| `&&` / `\|\|`  | Logical AND / OR               | `if (age > 18 && hasId) { ... }` |

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

```java
public class User {
    private String name;

    // Parameterized constructor
    public User(String name) {
        this.name = name;
    }
}
```

**B. Constructor Overloading**

Java uses overloaded constructors (or `this(...)` chaining) to handle flexible construction.

```java
public class User {
    private String name;

    public User() {
        this("Guest");     // delegates to the parameterized constructor
    }

    public User(String name) {
        this.name = name;
    }
}

// Usage
User u1 = new User();          // name = "Guest"
User u2 = new User("Alice");   // name = "Alice"
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

```java
public class Employee {
    static String company = "Google";   // Class variable: shared by ALL employees

    private String name;                // Instance variable: unique to each employee

    public Employee(String name) {
        this.name = name;
    }
}

// Usage
Employee e1 = new Employee("John");
Employee e2 = new Employee("Jane");

// e1.company (via Employee.company) is "Google"
// e2.company (via Employee.company) is "Google"

// Change it via the Class — affects all instances
Employee.company = "Alphabet";

// Now Employee.company is "Alphabet" for both e1 and e2!
```

**B. Static Methods**

Static methods can be called without creating an object. They are utility functions (like `Math.sqrt()`).

* _Restriction:_ A static method cannot access instance variables (because it doesn't know _which_ object you are talking about).

***

### 3. Access Modifiers

"The Security Guards"

Access modifiers determine which other classes can see and use your variables and methods.

| **Modifier** | **Keyword**    | **Visibility**               | **Analogy**                          |
| ------------ | -------------- | ----------------------------- | ------------------------------------- |
| Public       | `public`       | Everywhere (Global)          | Public Park (Anyone can enter)       |
| Private      | `private`      | Only inside the Same Class   | Your Diary (Only you can read)       |
| Protected    | `protected`    | Same Package + Subclasses    | Family Money (Family + Kids inherit) |
| Default      | _(no keyword)_ | Only inside the Same Package | Office Water Cooler (Only coworkers) |

**Best Practice: Encapsulation**

Always make your fields `private` and your methods `public` (unless they are internal helper methods).

```java
public class BankAccount {
    private double balance = 0.0;   // Private: no one can touch the money directly

    public void deposit(double amount) {   // Public: everyone can use the bank services
        if (amount > 0) {
            balance += amount;   // Safe internal access
        }
    }
}
```

***

### 4. Combined Revision Code

Here is a `School` system combining Constructors, Static logic, and Modifiers.

```java
public class Student {
    // Class variable — shared counter
    private static int totalStudents = 0;

    // Class constant
    static final String SCHOOL_NAME = "Lincoln High";

    private final String name;   // Private field
    private final int id;        // Auto-assigned ID

    public Student(String name) {
        this.name = name;
        Student.totalStudents += 1;
        this.id = Student.totalStudents;
    }

    public static int getTotalStudents() {   // Static method: class-level data only
        return totalStudents;
    }

    public void introduce() {                // Instance method: uses instance data
        System.out.println("Hi, I am " + name + " (ID: " + id + ")");
        System.out.println("I go to " + Student.SCHOOL_NAME);
    }

    public static void main(String[] args) {
        System.out.println("School: " + Student.SCHOOL_NAME);

        Student s1 = new Student("Alice");
        Student s2 = new Student("Bob");

        s1.introduce();   // ID: 1
        s2.introduce();   // ID: 2

        System.out.println("Total Enrolled: " + Student.getTotalStudents());   // 2
    }
}
```

#### Quick Syntax Cheat Sheet

| **Feature**     | **Syntax**                        | **When to use?**                                |
| ---------------- | --------------------------------- | ------------------------------------------------ |
| Constructor     | `public ClassName() { ... }`      | To set up default values on object creation.    |
| Class Variable  | `static int count = 0;`           | For shared data (counters, constants).          |
| Static Method   | `public static void run() { ... }`| For utility tools (math helpers, converters).   |
| Private Field   | `private int age = 0;`            | ALWAYS for internal class fields (attributes).  |
| Public Method   | `public int getAge() { ... }`     | For methods meant for the outside world.        |
| Access Static   | `ClassName.variable`              | Don't access via instance (e.g., `s1.count`).   |
