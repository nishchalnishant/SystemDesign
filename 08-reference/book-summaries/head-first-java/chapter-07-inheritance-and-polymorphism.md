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
> **What this covers:** Core OOP concepts: Inheritance (the IS-A relationship) and Polymorphism.
>
> **Key concepts:**
> - Inheritance: Using the `extends` keyword, a subclass inherits all public and protected instance variables and methods from its superclass.
> - Overriding: A subclass can redefine a method inherited from a superclass to provide its own specific behavior.
> - Polymorphism: You can declare a reference variable of a superclass type, but assign it a subclass object. `Animal a = new Dog();`.
> - IS-A vs HAS-A: Use inheritance only when the subclass "IS-A" type of the superclass (e.g., A Dog IS-A Animal). If a class "HAS-A" something else, use composition (e.g., a Bathroom HAS-A Tub).
>
> **Key takeaway:** Polymorphism allows you to write flexible, extensible code. You can write a method that takes an `Animal` array and calls `.makeNoise()` on all of them, and it works perfectly whether the array contains `Dog`, `Cat`, or `Lion` objects.

# Ch 07: Inheritance and Polymorphism

**Source**: Head First Java, Second Edition | **Pages**: 199-230

## 🎯 Learning Objectives

IS-A relationship and method overriding

## 📚 Key Concepts

* Inheritance concept
* Superclass and subclass
* IS-A relationship
* HAS-A vs IS-A
* Method overriding
* Polymorphism explained
* super keyword
* Preventing overriding and inheritance

***

## 📖 Detailed Notes

### 1. Inheritance concept

_Essential concept for mastering Java and OOP._

**Example**:

```java
class design, the 3 tricks to polymorphism, the 8 ways to make flexible code, and if you act 
```

### 2. Superclass and subclass

_Essential concept for mastering Java and OOP._

**Example**:

```java
new class called Shape.
```

### 3. IS-A relationship

_Essential concept for mastering Java and OOP._

**Example**:

```java
The Shape class is called the superclass of the other four 
```

### 4. HAS-A vs IS-A

_Essential concept for mastering Java and OOP._

**Example**:

```java
words, if the Shape class has the functionality, then the 
```

### 5. Method overriding

_Essential concept for mastering Java and OOP._

**Example**:

```java
BRAD: That’s the last step. The Amoeba class overrides the 
```

### 6. Polymorphism explained

_Essential concept for mastering Java and OOP._

**Example**:

```java
I made the Amoeba class override 
```

### 7. super keyword

_Essential concept for mastering Java and OOP._

**Example**:

```java
superclass Shape.
```

### 8. Preventing overriding and inheritance

_Essential concept for mastering Java and OOP._

**Example**:

```java
subclass redefines one of its 
```

***

## 💡 Important Points to Remember

* part of our cultural
* design feature. Think of the
* point is that the
* way back in chapter 2, when Larry (procedural guy)
* that if a class

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Inheritance concept
* [ ] Superclass and subclass
* [ ] IS-A relationship
* [ ] HAS-A vs IS-A
* [ ] Method overriding
* [ ] Polymorphism explained
* [ ] super keyword
* [ ] Preventing overriding and inheritance

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 199-230._



## Chapter 7: Better Living in Objectville — Study Notes

This chapter explores the power of Inheritance and Polymorphism. It focuses on how to design flexible, extensible programs by planning for the future and exploiting the relationships between classes.

<a class="button secondary">+1</a>

***

### 1. The Basics of Inheritance

Inheritance allows one class (the subclass) to inherit the state (instance variables) and behavior (methods) of another class (the superclass).

<a class="button secondary"></a>

*   Superclass (Parent): The more abstract class containing common features.

    <a class="button secondary"></a>
*   Subclass (Child): The more specific class that "extends" the superclass. In Java, this is done using the `extends` keyword.

    <a class="button secondary"></a>
*   Relationship: We describe inheritance as an IS-A relationship. If class `Dog` extends `Animal`, then `Dog` IS-A `Animal`.

    <a class="button secondary"></a>

***

### 2. Overriding Methods

A subclass can redefine an inherited method to change or extend its behavior. This is called overriding.

<a class="button secondary"></a>

*   Why Override?: When a subclass needs specific behavior that differs from the generic implementation in the superclass (e.g., an `Amoeba` rotates differently than a standard `Shape`).

    <a class="button secondary"></a>
*   The "Lowest" Wins: When you call a method on an object, the JVM starts looking for the method in the specific object's class. If it's not there, it moves up the inheritance tree until it finds a match.

    <a class="button secondary"></a>

***

### 3. Designing for Inheritance (5-Step Plan)

To build a solid class hierarchy, follow these steps:

<a class="button secondary"></a>

1.  Look for commonality: Identify objects that share attributes and behaviors.

    <a class="button secondary"></a>
2.  Abstract out the common features: Create a superclass to hold these shared traits.

    <a class="button secondary"></a>
3.  Decide on specific behaviors: Determine which methods need to be overridden in individual subclasses.

    <a class="button secondary"></a>
4.  Find further abstraction: Group similar subclasses into intermediate levels (e.g., grouping `Dog` and `Wolf` under a `Canine` class).

    <a class="button secondary"></a>
5.  Finish the hierarchy: Connect all classes through their inheritance relationships.

    <a class="button secondary"></a>

***

### 4. IS-A vs. HAS-A

Understanding the difference between inheritance and composition is crucial for good design.

<a class="button secondary"></a>

*   IS-A (Inheritance): Represents a relationship where one type is a specialized version of another. (e.g., "A Surgeon IS-A Doctor").

    <a class="button secondary"></a>
*   HAS-A (Composition): Represents a relationship where one class contains a reference to another. (e.g., "A Bathroom HAS-A Tub"). In this case, `Bathroom` has a `Tub` instance variable but does not inherit from it.

    <a class="button secondary"></a>

***

### 5. Introduction to Polymorphism

Polymorphism allows you to treat different objects in a uniform way through their common supertype.

<a class="button secondary"></a>

*   Polymorphic References: You can declare a reference variable of a superclass type and point it to a subclass object.

    <a class="button secondary"></a>

    *   _Example_: `Animal myDog = new Dog();`

        <a class="button secondary"></a>
*   Polymorphic Arguments/Return Types: You can write methods that take a superclass as a parameter, allowing it to accept any subclass of that type.

    <a class="button secondary"></a>

***

### 6. Revision Summary Checklist

*   Keywords: Use `extends` to create an inheritance link.

    <a class="button secondary"></a>
*   Instance Variables: These are not overridden; they are simply inherited and can be assigned values in the subclass.

    <a class="button secondary"></a>
*   The IS-A Test: Always ask "Does \[Subclass] IS-A \[Superclass]?" to verify your design.

    <a class="button secondary"></a>
*   Inheritance Limit: A class can extend only one superclass (no multiple inheritance of classes in Java).

    <a class="button secondary"></a>

