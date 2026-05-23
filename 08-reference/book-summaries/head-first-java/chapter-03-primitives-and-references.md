---
module: 08-reference
topic: Book Summaries
subtopic: Head First Java
status: unread
tags: [08-reference, system-design, book-summaries]
---
# Ch 03: Primitives and References

**Source**: Head First Java, Second Edition | **Pages**: 83-104

## 🎯 Learning Objectives

Understanding variables, memory, and references

## 📚 Key Concepts

* Primitive types (int, boolean, etc.)
* Reference variables
* Object references vs primitives
* Arrays fundamentals
* Heap vs Stack memory
* Garbage collection intro
* null references

***

## 📖 Detailed Notes

### 1. Primitive types (int, boolean, etc.)

_Essential concept for mastering Java and OOP._

**Example**:

```java
Rabbit hopper = new Giraffe();
```

### 2. Reference variables

_Essential concept for mastering Java and OOP._

**Example**:

```java
int count;
```

### 3. Object references vs primitives

_Essential concept for mastering Java and OOP._

**Example**:

```java
of type X”, think of type and class as synonyms.  
```

### 4. Arrays fundamentals

_Essential concept for mastering Java and OOP._

**Example**:

```java
int x;
x = 234;
byte b = 89;
boolean isFun = true;
double d = 3456.98;
char c = ‘f’;
int z = x;
boolean isPunkRock;
isPunkRock = false;
boolean powerOn;
powerOn = isFun;
long big = 3456789;
float f = 32.5f;
```

### 5. Heap vs Stack memory

_Essential concept for mastering Java and OOP._

**Example**:

```java
int x = 24;  
byte b = x;  
```

### 6. Garbage collection intro

_Essential concept for mastering Java and OOP._

**Example**:

```java
int size = 32;	
```

### 7. null references

_Essential concept for mastering Java and OOP._

**Example**:

```java
char initial = ‘j’;	
```

***

## 💡 Important Points to Remember

* that a reference variable
* that the compiler won’t let you
* We have to
* When you see a statement like: “an object
* the ‘f’. Gotta have that

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Primitive types (int, boolean, etc.)
* [ ] Reference variables
* [ ] Object references vs primitives
* [ ] Arrays fundamentals
* [ ] Heap vs Stack memory
* [ ] Garbage collection intro
* [ ] null references

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 83-104._



## Chapter 3: Know Your Variables — Study Notes

This chapter explores how Java manages data using Variables. It covers the two main types of variables (primitives and references), how they are stored in memory, and the rules for declaring them.

<a class="button secondary"></a>

***

### 1. Variable Fundamentals

A variable is essentially a container (think of it as a "cup") that holds something. Every variable in Java must follow two strict rules:

<a class="button secondary"></a>

*   Must have a type: Java is a "strongly-typed" language. You cannot put a "Giraffe" value into a "Rabbit" variable.

    <a class="button secondary"></a>
*   Must have a name: This name allows you to use and track the variable in your code.

    <a class="button secondary"></a>

***

### 2. Primitive Types

Primitives hold fundamental values as simple bit patterns. They come in different "cup sizes" depending on the data they need to store.

<a class="button secondary"></a>

| **Type** | **Bit Depth** | **Value Range**                                                                                        |
| -------- | ------------- | ------------------------------------------------------------------------------------------------------ |
| boolean  | JVM-specific  | <p><code>true</code> or <code>false</code></p><p><a class="button secondary"></a></p>                  |
| char     | 16 bits       | <p>0 to 65535</p><p><a class="button secondary"></a></p>                                               |
| byte     | 8 bits        | <p>-128 to 127</p><p><a class="button secondary"></a></p>                                              |
| short    | 16 bits       | <p>-32,768 to 32,767</p><p><a class="button secondary"></a></p>                                        |
| int      | 32 bits       | <p>-2,147,483,648 to 2,147,483,647</p><p><a class="button secondary"></a></p>                          |
| long     | 64 bits       | <p>Extremely large range</p><p><a class="button secondary"></a></p>                                    |
| float    | 32 bits       | <p>Floating point (use 'f' suffix, e.g., <code>3.2f</code>)</p><p><a class="button secondary"></a></p> |
| double   | 64 bits       | <p>Large floating point (standard default)</p><p><a class="button secondary"></a></p>                  |

***

### 3. Object Reference Variables

There is no such thing as an "object variable." There are only object reference variables.

<a class="button secondary"></a>

*   The Remote Control Analogy: Think of a reference variable as a remote control to an object.

    <a class="button secondary">+1</a>
*   Storage: While the reference variable lives on the Stack (if it's a local variable), the actual object always lives on the Heap.

    <a class="button secondary">+1</a>
*   Reference Value: The "bits" inside a reference variable represent a way to get to a specific object on the heap.

    <a class="button secondary"></a>

#### Example:

Java

```
Dog myDog = new Dog();
```

1. `Dog myDog`: Declares a reference variable (the remote control).
2. `new Dog()`: Creates a new Dog object on the Heap.
3.  `=`: Links the reference variable to the new object.

    <a class="button secondary"></a>

***

### 4. Array Variables

An array is an object, even if it is an array of primitives.

<a class="button secondary"></a>

*   Declaration: `int[] nums;` (declares a reference to an array).

    <a class="button secondary"></a>
*   Creation: `nums = new int[7];` (creates an array object that can hold 7 ints).

    <a class="button secondary"></a>
*   Access: Use the index starting at 0 (e.g., `nums[0] = 6;`).

    <a class="button secondary"></a>

***

### 5. Memory Management: The Heap and The Stack

*   The Stack: Where method calls and local variables live. When a method is called, its "stack frame" is pushed onto the stack. When it finishes, the frame is popped off, and all local variables in that frame die.

    <a class="button secondary"></a>
*   The Heap: Where all objects live.

    <a class="button secondary">+1</a>
*   Garbage Collection (GC): An object is eligible for GC when it has no more "live" references. This happens if:

    <a class="button secondary"></a>

    1.  The reference goes out of scope.

        <a class="button secondary"></a>
    2.  The reference is assigned to another object.

        <a class="button secondary"></a>
    3.  The reference is explicitly set to `null`.

        <a class="button secondary"></a>

***

### 6. Revision Checklist

*   Naming Rules: Variables must start with a letter, underscore (`_`), or dollar sign (`$`); they cannot start with a digit.

    <a class="button secondary"></a>
*   Type Safety: You cannot assign a large type (like `long`) to a smaller type (like `int`) without an explicit cast, as this could cause "spillage".

    <a class="button secondary"></a>
*   Primitive vs. Reference: Remember that primitives hold the actual value, while references hold the address of the object.

    <a class="button secondary"></a>
*   Wrapper Classes: If you need to treat a primitive like an object (e.g., for an `ArrayList`), use its Wrapper class (e.g., `Integer` for `int`).

    <a class="button secondary"></a>
