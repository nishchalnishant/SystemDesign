# Ch 06: Using the Java Library

**Source**: Head First Java, Second Edition | **Pages**: 159-198

## 🎯 Learning Objectives

Java API and using library classes

## 📚 Key Concepts

* Java API overview
* ArrayList in detail
* Java packages
* Import statements
* Boolean expressions
* Using documentation
* Library vs your code

***

## 📖 Detailed Notes

### 1. Java API overview

_Essential concept for mastering Java and OOP._

**Example**:

```java
public String checkYourself(String stringGuess) {
    int guess = Integer.parseInt(stringGuess);
    String result = “miss”;
    for (int cell : locationCells) {
        if (guess == cell) {
           result = “hit”;
           numOfHits++;
           break;
       } // end if
    } // end for
    if (numOfHits == locationCells.length) {
       result = “kill”;
    } // end if
    System.out.println(result);
    return result;
} // end method
```

### 2. ArrayList in detail

_Essential concept for mastering Java and OOP._

**Example**:

```java
A class in the core Java library (the API).
```

### 3. Java packages

_Essential concept for mastering Java and OOP._

**Example**:

```java
ArrayList<Egg> myList = new ArrayList<Egg>();
```

### 4. Import statements

_Essential concept for mastering Java and OOP._

**Example**:

```java
Egg s = new Egg();
myList.add(s);
```

### 5. Boolean expressions

_Essential concept for mastering Java and OOP._

**Example**:

```java
myList.remove(s);
```

### 6. Using documentation

_Essential concept for mastering Java and OOP._

**Example**:

```java
int theSize = myList.size();
```

### 7. Library vs your code

_Essential concept for mastering Java and OOP._

**Example**:

```java
boolean isIn = myList.contains(s);
```

***

## 💡 Important Points to Remember

* for three main reasons. First, they
* ArrayList.
* the add(Object elem) method
* For extra credit, you might
* To do this exercise, you need

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Java API overview
* [ ] ArrayList in detail
* [ ] Java packages
* [ ] Import statements
* [ ] Boolean expressions
* [ ] Using documentation
* [ ] Library vs your code

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 159-198._



## Chapter 6: Using the Java Library — Study Notes

This chapter introduces the Java API, focusing on how to use pre-built classes so you don't have to "reinvent the wheel". It specifically explores the `ArrayList` class as a more flexible alternative to standard arrays.

<a class="button secondary"></a>

***

### 1. The Java API

The Java Library (API) is a vast collection of pre-built classes that you can use as building blocks for your applications.

<a class="button secondary"></a>

*   The Goal: Write only the custom parts of your application and use the API for the rest.

    <a class="button secondary"></a>
*   Packages: API classes are grouped into packages (e.g., `java.util`, `java.io`). You must `import` these classes to use them, except for those in `java.lang`.

    <a class="button secondary">+1</a>

***

### 2. ArrayList: A Better Array

While standard arrays have a fixed size, an `ArrayList` is a dynamic collection that grows or shrinks as needed.

<a class="button secondary">+1</a>

#### Key Differences: Arrays vs. ArrayList

| **Feature** | **Standard Array**                                                                            | **ArrayList**                                                                                                     |
| ----------- | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| Size        | <p>Fixed at creation.</p><p><a class="button secondary"></a></p>                              | <p>Dynamic; expands as items are added.</p><p><a class="button secondary">+1</a></p>                              |
| Syntax      | <p>Special syntax (e.g., <code>myArray[0]</code>).</p><p><a class="button secondary"></a></p> | <p>Standard method calls (e.g., <code>myList.add(o)</code>).</p><p><a class="button secondary">+1</a></p>         |
| Types       | <p>Can hold primitives or objects.</p><p><a class="button secondary"></a></p>                 | <p>Can only hold object references (requires wrappers for primitives).</p><p><a class="button secondary"></a></p> |

#### Common ArrayList Methods

*   `add(Object o)`: Adds an object to the list.

    <a class="button secondary"></a>
*   `remove(int index)` / `remove(Object o)`: Removes an item.

    <a class="button secondary">+1</a>
*   `contains(Object o)`: Checks if an item exists in the list.

    <a class="button secondary">+1</a>
*   `isEmpty()`: Checks if the list has no elements.

    <a class="button secondary">+1</a>
*   `indexOf(Object o)`: Returns the position of an element, or -1 if not found.

    <a class="button secondary">+1</a>
*   `size()`: Returns the number of elements.

    <a class="button secondary">+1</a>

***

### 3. Organizing with Packages

As your programs grow, you should put your classes into packages to prevent naming collisions.

<a class="button secondary"></a>

*   Naming Conflicts: Packages ensure that your class `Foo` doesn't conflict with another developer's class named `Foo`.

    <a class="button secondary"></a>
*   Structure: The package name must match the directory structure where the class file resides.

    <a class="button secondary">+1</a>

***

### 4. Sorting and the Collections Class

While `ArrayList` itself doesn't have a `sort()` method, the `java.util.Collections` class provides tools to manipulate lists.

<a class="button secondary">+1</a>

*   `Collections.sort(myList)`: Sorts a list of objects.

    <a class="button secondary">+2</a>
*   Comparable Interface: To be sortable, a class must implement the `Comparable` interface and its `compareTo()` method to define the "natural" sort order.

    <a class="button secondary"></a>
*   Comparator Interface: Use a `Comparator` when you need to sort objects in different ways (e.g., by title OR by artist).

    <a class="button secondary"></a>

***

### 5. Boolean Logic Revisited

Chapter 6 introduces "Extra-Strength" boolean expressions to handle complex logic.

<a class="button secondary">+1</a>

*   `&&` (AND): True if both conditions are met.

    <a class="button secondary">+1</a>
*   `||` (OR): True if at least one condition is met.

    <a class="button secondary">+1</a>
*   `!` (NOT): Reverses the boolean value.

    <a class="button secondary">+1</a>
*   Short-circuiting: With `&&`, if the first part is false, the second part isn't even checked.

    <a class="button secondary">+1</a>

***

### 6. Revision Checklist

*   Importing: Did you `import java.util.ArrayList;`?

    <a class="button secondary">+1</a>
*   Generics: Are you using angle brackets to specify the type (e.g., `ArrayList<String>`)?

    <a class="button secondary">+1</a>
*   Methods: Remember to use `.size()` for `ArrayList` instead of `.length` which is for arrays.

    <a class="button secondary"></a>
*   API Docs: Use the Java API documentation to find methods for classes like `String`, `Math`, and `ArrayList`.

    <a class="button secondary"></a>
