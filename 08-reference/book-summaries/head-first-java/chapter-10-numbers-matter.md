---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 10: Numbers Matter

**Source**: Head First Java, Second Edition | **Pages**: 307-348

## 🎯 Learning Objectives

Static members and number handling

## 📚 Key Concepts

* Static methods
* Static variables
* Math class
* Wrapper classes
* Autoboxing and unboxing
* Number formatting
* Static initialization
* Constants with static final

***

## 📖 Detailed Notes

### 1. Static methods

_Essential concept for mastering Java and OOP._

**Example**:

```java
Methods in the Math class 
```

### 2. Static variables

_Essential concept for mastering Java and OOP._

**Example**:

```java
time. If you had 10,000 instances of class Math, and ran 
```

### 3. Math class

_Essential concept for mastering Java and OOP._

**Example**:

```java
to make an instance of class Math simply to run the 
```

### 4. Wrapper classes

_Essential concept for mastering Java and OOP._

**Example**:

```java
Math class doesn’t have any instance variables. So there’s 
nothing to be gained by making an instance of class 
```

### 5. Autoboxing and unboxing

_Essential concept for mastering Java and OOP._

**Example**:

```java
Math class to make a new Math object.
```

### 6. Number formatting

_Essential concept for mastering Java and OOP._

**Example**:

```java
TestMath.java:3: Math() has private 
```

### 7. Static initialization

_Essential concept for mastering Java and OOP._

**Example**:

```java
     Math mathObject = new Math();
```

### 8. Constants with static final

_Essential concept for mastering Java and OOP._

**Example**:

```java
Math mathObject = new Math();
```

***

## 💡 Important Points to Remember

* things
* one! And what about the Color
* the ‘+’ operator is overloaded
* that if you do put
* the

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Static methods
* [ ] Static variables
* [ ] Math class
* [ ] Wrapper classes
* [ ] Autoboxing and unboxing
* [ ] Number formatting
* [ ] Static initialization
* [ ] Constants with static final

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 307-348._



## Chapter 10: Numbers Matter — Study Notes

This chapter focuses on static variables and methods, which exist independently of any object instance. It also covers essential number formatting, parsing, and manipulation using the Java API.

### 1. Static Methods

A `static` method is a method that runs without an instance of the class. It is called on the class itself, not on an object.

* Behavior: Static methods cannot access instance variables or non-static methods because they don't "know" about any specific object on the heap.
* Usage: Use `static` methods for utility functions that don't depend on an object's state (e.g., `Math.round()`, `Integer.parseInt()`).
* Syntax: `ClassName.methodName()` (e.g., `Math.random()`).
* Restriction: A static method cannot use the `this` keyword.

### 2. Static Variables

A `static` variable is shared by all instances of a class. There is only one copy of the variable, regardless of how many objects are created.

* Initialization: Static variables are initialized when the class is first loaded, before any objects are created.
* Shared State: If one object changes a static variable, it changes for all other objects of that class (e.g., a `count` variable to track how many `Duck` objects exist).
* Constants: `static final` variables are used as constants.
  * Naming Convention: Constants should be in `ALL_CAPS_WITH_UNDERSCORES` (e.g., `public static final double PI = 3.14159;`).
  * Final: The `final` keyword ensures the value cannot change once assigned.

### 3. The Math Class

The `java.lang.Math` class is full of static methods for common mathematical operations.

* `Math.random()`: Returns a double between 0.0 and 1.0 (not inclusive of 1.0).
* `Math.abs()`: Returns the absolute (positive) value of a number.
* `Math.round()`: Rounds a floating-point number to the nearest integer.
* `Math.min()` / `Math.max()`: Returns the smaller or larger of two numbers.

### 4. Wrappers

Wrapper classes allow you to treat primitive data types as objects. This is often necessary when working with collections like `ArrayList`, which can only hold objects.

* Mapping:
  * `int` → `Integer`
  * `double` → `Double`
  * `boolean` → `Boolean`
* Autoboxing / Unboxing: Since Java 5.0, the compiler automatically converts between primitives and their wrapper objects.
  * _Autoboxing_: `Integer i = 10;` (wraps `int` 10 into an `Integer` object).
  * _Unboxing_: `int x = i;` (extracts the `int` value from the `Integer` object).
* Parsing: Wrappers provide static utility methods to convert Strings to primitives (e.g., `Integer.parseInt("42")`).

### 5. Number Formatting

Java provides strong support for formatting numbers and dates.

* `String.format()`: A static method that works like `printf` in C. It returns a formatted String.
  * `String s = String.format("%,d", 1000000);` // returns "1,000,000"
* Format Specifiers:
  * `%d`: Decimal integer
  * `%f`: Floating point
  * `%x`: Hexadecimal
  * `%c`: Character
  * `%,d`: Insert commas
  * `%.2f`: Limit to two decimal places
* Date Formatting: Using `java.util.Date` and `java.util.Calendar` (or newer APIs) to represent and manipulate time.

### 6. Revision Checklist

* Static vs. Non-Static: Do you understand why `Math.random()` is static but `String.substring()` is not? (One performs a global utility; the other operates on specific data).
* Wrapper utility: Can you use `Integer.parseInt()` to turn user text input into a number for math?
* Constants: Are you declaring your global constants as `public static final`?
* Final variables: Remember that a `final` variable cannot be changed. A `final` method cannot be overridden. A `final` class cannot be extended.
