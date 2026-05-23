---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 09: Life and Death of an Object

**Source**: Head First Java, Second Edition | **Pages**: 269-306

## 🎯 Learning Objectives

Constructors and object lifecycle

## 📚 Key Concepts

* Constructors explained
* Constructor overloading
* Default constructor
* super() in constructors
* this() for constructor chaining
* Object initialization
* Garbage collection
* finalize() method

***

## 📖 Detailed Notes

### 1. Constructors explained

_Essential concept for mastering Java and OOP._

**Example**:

```java
Instance variables are declared inside a class but not 
```

### 2. Constructor overloading

_Essential concept for mastering Java and OOP._

**Example**:

```java
public class Duck {
   int size;
}
```

### 3. Default constructor

_Essential concept for mastering Java and OOP._

**Example**:

```java
public void foo(int x) {
   int i = x + 3;
   boolean b = true; 
}
```

### 4. super() in constructors

_Essential concept for mastering Java and OOP._

**Example**:

```java
  public void doStuff() {
     boolean b = true;
     go(4);
  }
  public void go(int x) {
     int z = x + 24;
     crazy();
```

### 5. this() for constructor chaining

_Essential concept for mastering Java and OOP._

**Example**:

```java
  }
  public void crazy() {
     char c = ‘a’;
  }
```

### 6. Object initialization

_Essential concept for mastering Java and OOP._

**Example**:

```java
class calls doStuff(), 
```

### 7. Garbage collection

_Essential concept for mastering Java and OOP._

**Example**:

```java
class looks like) with three methods. The first method (doStuff()) calls 
```

### 8. finalize() method

_Essential concept for mastering Java and OOP._

**Example**:

```java
public class StackRef {
   public void foof() {
      barf();
   }
   public void barf() {
      Duck d = new Duck(24);
   }
}
```

***

## 💡 Important Points to Remember

* instance variables.
* Duck state\*
* that those inherited things be finished. No
* that the values of an object’s instance
* that a reference

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Constructors explained
* [ ] Constructor overloading
* [ ] Default constructor
* [ ] super() in constructors
* [ ] this() for constructor chaining
* [ ] Object initialization
* [ ] Garbage collection
* [ ] finalize() method

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 269-306._



## Chapter 9: Life and Death of an Object — Study Notes

This chapter covers the entire lifecycle of an object: how it is created, where it lives, and how it is removed from memory when it is no longer needed.

### 1. The Stack and The Heap

Understanding memory is crucial for mastering Java scope and object creation.

*   The Heap: The area of memory where ALL objects live.

    <a class="button secondary"></a>

    *   Even if an object is assigned to a local variable, the object itself is on the Heap; the local variable (reference) is on the Stack.

        <a class="button secondary"></a>
*   The Stack: The area where method invocations and local variables live.

    <a class="button secondary"></a>

    *   When a method is called, a new stack frame is pushed onto the stack to hold the method's state and local variables.

        <a class="button secondary"></a>
    *   When the method finishes (hits the closing curly brace), the frame is popped off, and the local variables are lost.

        <a class="button secondary"></a>

### 2. Constructors

A constructor is a special method that runs when you instantiate an object. It is used to initialize the state of an object.

<a class="button secondary"></a>

#### Key Rules

*   Invocation: Constructors are called when you use the `new` keyword (e.g., `new Duck()`).

    <a class="button secondary"></a>
*   Name: A constructor must have the exact same name as the class.

    <a class="button secondary"></a>
*   Return Type: Constructors have no return type, not even `void`.

    <a class="button secondary"></a>
* Default Constructor: If you don't write _any_ constructor, the compiler puts one in for you (a no-argument constructor).
*   Overloading: You can have multiple constructors as long as they have different parameter lists.

    <a class="button secondary"></a>

#### The "Super" Constructor

*   Chaining: Every constructor must call a constructor of its superclass (its parent).

    <a class="button secondary"></a>
* Implicit Call: If you don't explicitly call `super()`, the compiler inserts a call to the no-arg super constructor as the very first line.
* Object Class: This chaining continues up to the `Object` class, ensuring every class in the hierarchy gets a chance to initialize itself.

### 3. The Lifecycle of an Object

1. Creation: An object is created on the Heap when `new` is called.
2.  Life: The object remains alive as long as there is at least one "live" reference pointing to it.

    <a class="button secondary"></a>
3. Death (Abandonment): An object becomes eligible for garbage collection when it is no longer reachable. This happens when:
   * The reference variable goes out of scope (the method ends).
   * The reference is assigned to another object.
   *   The reference is explicitly set to `null`.

       <a class="button secondary"></a>

### 4. Garbage Collection (GC)

*   The GC: Java's garbage collector runs automatically to reclaim memory occupied by abandoned objects.

    <a class="button secondary"></a>
*   Memory Management: You cannot force the GC to run; you can only suggest it. Java manages memory for you, preventing common errors like memory leaks found in C++.

    <a class="button secondary"></a>
*   The Goal: The purpose of GC is to free up heap space so you don't run out of RAM.

    <a class="button secondary"></a>

### 5. Revision Checklist

*   Stack vs. Heap: Do you know that local variables (even references to objects) are on the stack, while the objects they point to are on the heap?

    <a class="button secondary"></a>
* Constructor Chaining: Remember that `super()` must be the first statement in a constructor.
* Scope: Variables only exist within the block `{}` they are defined in. A local variable dies when its method completes.
*   Null Reference: Setting a reference to `null` breaks the link to the object, potentially making the object eligible for GC.

    <a class="button secondary"></a>
