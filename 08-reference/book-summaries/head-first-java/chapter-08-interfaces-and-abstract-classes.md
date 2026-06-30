---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The power of abstraction in Java, using `abstract` classes and `interface`s.
>
> **Key concepts:**
> - Abstract Classes: Used when a class is so general that it shouldn't be instantiated (e.g., `new Animal()` doesn't make sense). Use the `abstract` keyword.
> - Abstract Methods: Methods with no body. If a class has an abstract method, the class *must* be abstract. The first concrete subclass *must* implement all abstract methods.
> - Interfaces: A 100% pure abstract class. They define a contract (what a class can do) without specifying how. Use the `implements` keyword.
> - Multiple Inheritance: Java doesn't allow a class to `extend` multiple classes, but it *does* allow a class to `implements` multiple interfaces.
>
> **Key takeaway:** Use an abstract class for a shared base implementation among closely related classes (IS-A). Use an interface to define a role that any class can play, regardless of where it is in the inheritance tree (e.g., `Pet`, `Serializable`).

# Ch 08: Interfaces and Abstract Classes

**Source**: Head First Java, Second Edition | **Pages**: 231-268

## 🎯 Learning Objectives

Abstraction and multiple inheritance

## 📚 Key Concepts

* Abstract classes
* Abstract methods
* Interfaces
* Implementing interfaces
* Multiple inheritance via interfaces
* When to use abstract vs interface
* Object class and polymorphism

***

## 📖 Detailed Notes

### 1. Abstract classes

_Essential concept for mastering Java and OOP._

**Example**:

```java
The class structure isn’t too bad. We’ve designed 
```

### 2. Abstract methods

_Essential concept for mastering Java and OOP._

**Example**:

```java
Wolf aWolf = new Wolf();
```

### 3. Interfaces

_Essential concept for mastering Java and OOP._

**Example**:

```java
Animal aHippo = new Hippo();
```

### 4. Implementing interfaces

_Essential concept for mastering Java and OOP._

**Example**:

```java
Animal anim = new Animal();
```

### 5. Multiple inheritance via interfaces

_Essential concept for mastering Java and OOP._

**Example**:

```java
abstract subclasses of class Animal, not Animal itself. 
```

### 6. When to use abstract vs interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
Fortunately, there’s a simple way to prevent a class 
```

### 7. Object class and polymorphism

_Essential concept for mastering Java and OOP._

**Example**:

```java
the class as abstract, the compiler will stop any 
```

***

## 💡 Important Points to Remember

* methods in
* stuff
* than that...
* is
* that an abstract class can have both abstract and non-abstract

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Abstract classes
* [ ] Abstract methods
* [ ] Interfaces
* [ ] Implementing interfaces
* [ ] Multiple inheritance via interfaces
* [ ] When to use abstract vs interface
* [ ] Object class and polymorphism

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 231-268._



## Chapter 8: Serious Polymorphism — Study Notes

This chapter explores how to exploit polymorphism fully by using abstract classes and interfaces. It focuses on designing flexible architectures where you can write code that works on generic types (like `Animal`) but executes specific behaviors at runtime (like `Dog` or `Cat`).

***

### 1. Abstract Classes

Sometimes a class is too generic to be instantiated. For example, in an animal simulation, you need `Dog` and `Cat` objects, but a generic `Animal` object doesn't make sense.

*   Definition: An abstract class is a class that cannot be instantiated (you cannot say `new Animal()`). It exists solely to be extended.

    <a class="button secondary"></a>
* Purpose: It serves as a template for subclasses, enforcing a common protocol (a set of methods) that all subclasses must have.
*   Syntax: Use the `abstract` keyword.

    Java

    ```
    abstract class Animal {
        // methods and variables
    }
    ```

***

### 2. Abstract Methods

An abstract class can contain abstract methods. These are methods that _must_ be overridden by the first concrete (non-abstract) subclass.

*   No Body: Abstract methods have no body (no curly braces), just a semicolon.

    Java

    ```
    public abstract void eat();
    ```
*   The Rule: If a class contains even one abstract method, the class _must_ be marked abstract.

    <a class="button secondary"></a>
*   Enforcement: This forces all concrete subclasses to implement the method, ensuring they all have that specific behavior (e.g., every `Animal` _must_ implement `eat()`, but a `Dog` eats differently than a `Lion`).

    <a class="button secondary"></a>

***

### 3. The Ultimate Superclass: `Object`

In Java, every class extends `Object`, either directly or indirectly.

* Universal Methods: Since every class inherits from `Object`, every object has certain methods, such as:
  *   `equals(Object o)`: Decides if two objects are "equal".

      <a class="button secondary"></a>
  *   `getClass()`: Returns the class type of the object.

      <a class="button secondary"></a>
  *   `hashCode()`: Returns a unique ID for the object (used in collections).

      <a class="button secondary"></a>
  *   `toString()`: Returns a string representation of the object (often overridden to provide meaningful output).

      <a class="button secondary"></a>
*   Polymorphism with Object: You can use `Object` as a "universal type" to pass any object around (e.g., `ArrayList<Object>`), but this comes with a price: you lose the specific type information.

    <a class="button secondary"></a>

***

### 4. Casting and Type Safety

When you store a `Dog` in an `Object` reference (or `ArrayList<Object>`), the compiler only sees it as an `Object`. You cannot call `Dog`-specific methods (like `bark()`) on it unless you convert it back.

*   Downcasting: Converting a general reference back to a specific type.

    Java

    ```
    Object o = new Dog();
    Dog d = (Dog) o; // Cast the Object back to a Dog
    d.bark();
    ```
*   Risk: If you cast an object to the wrong type (e.g., casting a `Cat` to a `Dog`), Java will throw a `ClassCastException` at runtime.

    <a class="button secondary"></a>
*   `instanceof` Operator: Use this to check an object's type safely before casting.

    Java

    ```
    if (o instanceof Dog) {
        Dog d = (Dog) o;
    }
    ```

***

### 5. Interfaces

Java does not support "multiple inheritance" (a class cannot extend two parents like `Dog extends Animal, Pet`). To solve this, Java uses interfaces.

* Definition: An interface is a 100% abstract class. All methods in an interface are abstract by default.
* Contract: An interface defines a "role" that a class can play (e.g., `Pet`, `Serializable`). It says, "If you implement this interface, you promise to define these methods."
* Syntax:
  * Define: `public interface Pet { ... }`
  * Implement: `public class Dog extends Animal implements Pet { ... }`
*   Multiple Implementation: A class can extend only one parent class, but it can implement many interfaces.

    Java

    ```
    class Dog extends Animal implements Pet, Saveable, Runnable { ... }
    ```

***

### 6. Abstract Class vs. Interface

| **Feature** | **Abstract Class**                                      | **Interface**                                            |
| ----------- | ------------------------------------------------------- | -------------------------------------------------------- |
| Methods     | Can have both abstract and concrete methods.            | All methods are implicitly `public` and `abstract`.      |
| Inheritance | A class can extend only one abstract class.             | A class can implement multiple interfaces.               |
| Usage       | Use when classes share a common "core" identity (IS-A). | Use to define a "role" or capability (e.g., `Playable`). |

***

### 7. Revision Checklist

*   `abstract` vs `concrete`: Can you explain why you can't say `new Animal()` if Animal is abstract?

    <a class="button secondary"></a>
*   Polymorphism: Do you understand that `List<Animal>` can hold `Dog`, `Cat`, and `Lion` objects?

    <a class="button secondary"></a>
* Interface Implementation: Remember that when you implement an interface, you _must_ write the code for _every_ method defined in that interface.
*   Super: Use `super.methodName()` to call the superclass version of a method from within a subclass override.

    <a class="button secondary">+1</a>
