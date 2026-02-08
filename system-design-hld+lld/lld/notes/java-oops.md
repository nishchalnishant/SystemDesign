# JAVA OOPs

Here is a Java OOPs Revision Guide: Part 1 focused on the absolute fundamentals: Syntax, Classes, and Objects.

Since you are new to Java, think of this as your "Cheat Sheet" to understand how a Java file is structured and how to bring your code to life.

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

Java

```java
// 1. Package Declaration (Optional but recommended)
package com.myapp.basics; 

// 2. Imports (Bringing in other tools)
import java.util.Scanner; 

// 3. Class Declaration (PascalCase)
public class Student { 

    // 4. Fields / Attributes (State) -> camelCase
    String name;  
    int age;      

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

Java

```java
public class Student {
    String name;

    // Default Constructor (No arguments)
    public Student() {
        name = "Unknown";
    }

    // Parameterized Constructor (With arguments)
    public Student(String inputName) {
        name = inputName;
    }
}
```

***

### 4. The `this` Keyword

This is a reference variable that points to the current object. It is mostly used to resolve naming conflicts when your parameter name is the same as your field name.

Java

```java
public class Car {
    String model; // Field (belongs to object)

    // Constructor
    public Car(String model) { // Parameter (local variable)
        // model = model; // AMBIGUITY! Computer is confused.
        
        this.model = model; 
        // "this.model" refers to the Class Field.
        // "model" refers to the Parameter passed in.
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

Java

```java
public class Human {
    String name;            // Instance variable (Unique per person)
    static int population;  // Static variable (Shared by all Humans)

    public Human(String name) {
        this.name = name;
        population++; // Increasing the shared counter
    }
}
```

***

### 7. Putting It All Together: A "SmartPhone" Example

Here is a complete, runnable code block combining everything above.

Java

```java
// 1. The Class Definition
class SmartPhone {
    // Fields (State)
    String brand;
    int batteryLevel;
    static String osType = "Android"; // Shared by all phones of this class

    // Constructor
    public SmartPhone(String brand, int batteryLevel) {
        this.brand = brand;
        this.batteryLevel = batteryLevel;
    }

    // Method (Behavior)
    public void call(String number) {
        System.out.println(this.brand + " is calling " + number);
    }
    
    // Method to show static vs instance data
    public void showStats() {
        System.out.println("Phone: " + brand + " | OS: " + osType);
    }
}

// 2. The Main Execution Class
public class Main {
    public static void main(String[] args) {
        // Creating Object 1
        SmartPhone p1 = new SmartPhone("Samsung", 85);
        
        // Creating Object 2
        SmartPhone p2 = new SmartPhone("Pixel", 90);

        // Calling methods
        p1.call("555-0199");
        p2.showStats(); // Output: Phone: Pixel | OS: Android

        // Changing a static variable affects EVERYONE
        SmartPhone.osType = "Android 14";
        
        System.out.println("After Update:");
        p1.showStats(); // Output: Phone: Samsung | OS: Android 14
        p2.showStats(); // Output: Phone: Pixel   | OS: Android 14
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

Method 1: Declaration + Memory Allocation (Empty)

Use this when you know _how many_ items you need, but not _what_ they are yet.

Java

```java
// Syntax: type[] name = new type[size];
int[] scores = new int[5]; // Creates 5 slots: [0, 0, 0, 0, 0]

// Setting values
scores[0] = 95;
scores[1] = 88;
```

Method 2: Initialization (Filled)

Use this when you already know the values.

Java

```java
// Syntax: type[] name = {values};
String[] names = {"Alice", "Bob", "Charlie"};
```

**B. Iterating (Looping)**

You will almost always use a loop to go through an array.

Java

```java
int[] numbers = {10, 20, 30, 40, 50};

// 1. Classic For Loop (Good if you need the Index)
for (int i = 0; i < numbers.length; i++) {
    System.out.println("Index " + i + ": " + numbers[i]);
}

// 2. Enhanced For-Each Loop (Good for just reading values)
// Read as: "For each 'num' in 'numbers'..."
for (int num : numbers) {
    System.out.println("Value: " + num);
}
```

**C. The "Gotcha": ArrayIndexOutOfBoundsException**

This is the most common error. It happens if you try to access a slot that doesn't exist.

Java

```java
int[] arr = new int[3]; // Indices are 0, 1, 2
// System.out.println(arr[3]); // ERROR! Index 3 is the 4th slot.
```

***

### 2. Java Strings

"The Immutable Text"

In Java, a `String` is an Object, not a primitive type (like `int` or `char`). This means it has methods you can call.

* Key Characteristic: Immutability. Once a String object is created, it cannot be changed. If you "modify" a string, Java actually creates a generic _new_ string in memory and discards the old one.

**A. Creation: Literal vs. New**

How you create a string matters for memory.

1.  String Literal (Recommended): Uses the "String Constant Pool" to save memory. If you write "Hello" twice, Java reuses the same object.

    Java

    ```java
    String s1 = "Hello";
    ```
2.  New Keyword: Forces a new object in Heap memory every time.

    Java

    ```java
    String s2 = new String("Hello");
    ```

**B. The Comparison Trap (`==` vs `.equals()`)**

This is the #1 interview question for beginners.

* `==`: Compares references (memory addresses). "Are these the exact same object?"
* `.equals()`: Compares content (values). "Do these hold the same text?"

Java

```java
String s1 = "Java";
String s2 = "Java";           // Reuses s1 from Pool
String s3 = new String("Java"); // Forces new object

System.out.println(s1 == s2);       // true (Same memory address)
System.out.println(s1 == s3);       // false (Different memory addresses)
System.out.println(s1.equals(s3));  // true (Content is identical) -> USE THIS!
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

Java

```java
public class StudentDatabase {
    public static void main(String[] args) {
        // 1. Array of Strings
        String[] students = {"Alice", "Bob", "Charlie", "David"};
        
        // 2. Array of Integers (Parallel array for grades)
        int[] grades = {85, 42, 90, 76};

        System.out.println("--- Class Report ---");

        // 3. Loop through the array
        for (int i = 0; i < students.length; i++) {
            String name = students[i];
            int score = grades[i];

            // String Manipulation: Convert to uppercase for display
            String displayName = name.toUpperCase();

            // Conditional Logic
            String result;
            if (score >= 50) {
                result = "PASSED";
            } else {
                result = "FAILED";
            }

            System.out.println(displayName + " : " + score + " -> " + result);
        }

        // 4. Demonstrate Immutability
        String original = "   Java   ";
        String cleaned = original.trim(); // original is NOT changed
        
        System.out.println("\nOriginal: '" + original + "'");
        System.out.println("Cleaned:  '" + cleaned + "'");
    }
}
```

#### Quick Syntax Cheat Sheet

| **Action**      | **Syntax/Method**         | **Note**                                 |
| --------------- | ------------------------- | ---------------------------------------- |
| Create Array    | `int[] arr = new int[5];` | Size is fixed at 5.                      |
| Get Array Size  | `arr.length`              | It's a property (no parenthesis).        |
| Get String Size | `str.length()`            | It's a method (needs parenthesis).       |
| Compare Strings | `str1.equals(str2)`       | NEVER use `==` for text content.         |
| Get Char        | `str.charAt(0)`           | Gets the first letter.                   |
| Get Substring   | `str.substring(0, 3)`     | Gets index 0, 1, and 2 (3 is exclusive). |

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

Java

```java
public class Calculator {
    
    // 1. VOID Method (Action only)
    public void printWelcome() {
        System.out.println("--- Welcome to Calculator ---");
        // No 'return' statement needed
    }

    // 2. INT Method (Must return an int)
    public int add(int a, int b) {
        int sum = a + b;
        return sum; // MUST match the 'int' declared above
    }
}
```

**C. Method Overloading**

This is a core OOP concept (Compile-time Polymorphism). You can have multiple methods with the same name as long as their parameters are different.

Java

```
public class Printer {
    // Prints a String
    public void print(String text) {
        System.out.println(text);
    }

    // Prints an Integer (Same name, different input type)
    public void print(int number) {
        System.out.println("Number: " + number);
    }
    
    // Prints two Strings (Same name, different number of inputs)
    public void print(String text1, String text2) {
        System.out.println(text1 + " & " + text2);
    }
}
```

***

### 2. Control Flow statements

"The Logic / The Brain"

Control flow dictates the order in which statements are executed. Without this, your code just reads from top to bottom like a book.

**A. Conditional Logic (If / Else / Switch)**

Use If/Else for ranges or complex conditions. Use Switch for specific fixed values.

Java

```
int battery = 15;

// IF-ELSE LADDER
if (battery > 50) {
    System.out.println("Green Light");
} else if (battery > 20) {
    System.out.println("Yellow Light");
} else {
    System.out.println("Red Light");
}

// SWITCH STATEMENT (Clean for specific cases)
int day = 3;
switch (day) {
    case 1:
        System.out.println("Monday");
        break; // Don't forget break, or it falls through!
    case 2:
        System.out.println("Tuesday");
        break;
    default:
        System.out.println("Midweek");
}
```

**B. Loops (For, While, Do-While)**

Loops allow you to repeat code.

* `for` loop: Use when you know exactly how many times to loop (e.g., "Run 10 times").
* `while` loop: Use when you don't know the number of iterations (e.g., "Run until user types 'exit'").
* `do-while` loop: Guaranteed to run at least once (e.g., "Show menu, then ask to continue").

Java

```
// 1. FOR Loop (Fixed)
for (int i = 1; i <= 5; i++) {
    System.out.print(i + " "); // Output: 1 2 3 4 5
}

// 2. WHILE Loop (Condition based)
int count = 5;
while (count > 0) {
    System.out.print(count + " "); // Output: 5 4 3 2 1
    count--;
}
```

***

### 3. Combined Practice: A "Mini ATM" Class

Here is how Methods and Control Flow work together in a real object.

Java

```java
public class ATM {
    // State (Data)
    private double balance;
    private int pin = 1234;

    // Constructor
    public ATM(double initialBalance) {
        this.balance = initialBalance;
    }

    // Method 1: Boolean Return Type (Logic)
    public boolean verifyPin(int inputPin) {
        if (inputPin == this.pin) {
            return true;
        } else {
            return false;
        }
    }

    // Method 2: Void Method with Control Flow
    public void withdraw(int amount) {
        // Validation Logic
        if (amount <= 0) {
            System.out.println("Error: Amount must be positive.");
        } else if (amount > balance) {
            System.out.println("Error: Insufficient Funds.");
        } else {
            // Success Logic
            balance = balance - amount;
            System.out.println("Success! Please take your $" + amount);
        }
    }
    
    // Method 3: Getter
    public double getBalance() {
        return balance;
    }
}

// Main Execution
public class Main {
    public static void main(String[] args) {
        ATM myAtm = new ATM(1000.0);
        
        // Using Logic (If/Else) with Method Calls
        if (myAtm.verifyPin(1234)) {
            System.out.println("PIN Accepted.");
            myAtm.withdraw(500); // Valid
            myAtm.withdraw(5000); // Invalid (Insufficient funds)
        } else {
            System.out.println("Wrong PIN!");
        }
    }
}
```

#### Quick Syntax Cheat Sheet

| **Keyword**   | **Purpose**                    | **Example**                 |
| ------------- | ------------------------------ | --------------------------- |
| `void`        | Method returns nothing         | `public void run() { ... }` |
| `return`      | Exits method & sends data back | `return a + b;`             |
| `break`       | Exits a loop or switch case    | `break;`                    |
| `continue`    | Skips current loop iteration   | `continue;`                 |
| `if (x == y)` | Checks equality                | `if (age == 18) { ... }`    |
| `!=`          | Checks "Not Equal"             | `if (age != 0) { ... }`     |
| `&&` / `\|\|` | Logical AND / OR               | `if (age > 18 && hasID)`    |

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

Java

```java
public class User {
    String name;

    // 1. No-Args Constructor (Default values)
    public User() {
        this.name = "Guest";
    }

    // 2. Parameterized Constructor (Custom values)
    public User(String name) {
        this.name = name;
    }
}
```

**B. Constructor Overloading**

Just like methods, you can have multiple constructors as long as their parameters are different. This gives users flexibility in how they create objects.

Java

```java
User u1 = new User();        // name = "Guest"
User u2 = new User("Alice"); // name = "Alice"
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

Java

```java
public class Employee {
    String name;          // Instance: Unique to each employee
    static String company = "Google"; // Static: Shared by ALL employees

    public Employee(String name) {
        this.name = name;
    }
}

// Usage
Employee e1 = new Employee("John");
Employee e2 = new Employee("Jane");

// e1.company is "Google"
// e2.company is "Google"

// If we change it strictly using the Class Name:
Employee.company = "Alphabet"; 

// Now e1.company AND e2.company are both "Alphabet"!
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

Java

```java
public class BankAccount {
    // 1. PRIVATE: No one can touch the money directly
    private double balance; 

    // 2. PUBLIC: Everyone can use the bank services
    public void deposit(double amount) {
        if (amount > 0) {
            this.balance += amount; // Safe internal access
        }
    }
}
```

***

### 4. Combined Revision Code

Here is a `School` system combining Constructors, Static logic, and Modifiers.

Java

```java
public class Student {
    // 1. Private Fields (Encapsulation)
    private String name;
    private int id;
    
    // 2. Static Field (Shared Counter)
    private static int totalStudents = 0;
    
    // 3. Constant (Final + Static)
    public static final String SCHOOL_NAME = "Lincoln High";

    // 4. Constructor
    public Student(String name) {
        this.name = name;
        // Auto-increment the shared ID counter
        totalStudents++; 
        this.id = totalStudents;
    }

    // 5. Static Method (Accessing static data only)
    public static int getTotalStudents() {
        return totalStudents;
    }

    // 6. Instance Method (Accessing instance data)
    public void introduce() {
        System.out.println("Hi, I am " + name + " (ID: " + id + ")");
        System.out.println("I go to " + SCHOOL_NAME);
    }
}

public class Main {
    public static void main(String[] args) {
        System.out.println("School: " + Student.SCHOOL_NAME);
        
        Student s1 = new Student("Alice");
        Student s2 = new Student("Bob");
        
        s1.introduce(); // ID: 1
        s2.introduce(); // ID: 2
        
        // Accessing static method directly from Class
        System.out.println("Total Enrolled: " + Student.getTotalStudents()); // Output: 2
    }
}
```

#### Quick Syntax Cheat Sheet

| **Feature**     | **Syntax**               | **When to use?**                           |
| --------------- | ------------------------ | ------------------------------------------ |
| Constructor     | `public ClassName() { }` | To setup default values.                   |
| Static Variable | `static int count;`      | For shared data (counters, constants).     |
| Static Method   | `static void run()`      | For utility tools (Math, Converters).      |
| Private         | `private int age;`       | ALWAYS for class fields (attributes).      |
| Public          | `public void getAge()`   | For methods meant for the outside world.   |
| Access Static   | `ClassName.variable`     | Don't use object names (e.g., `s1.count`). |

