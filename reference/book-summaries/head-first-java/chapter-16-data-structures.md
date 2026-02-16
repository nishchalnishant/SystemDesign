# Ch 16: Data Structures

**Source**: Head First Java, Second Edition | **Pages**: 563-614

## 🎯 Learning Objectives

Collections and Generics

## 📚 Key Concepts

* Collections Framework
* Generics basics
* ArrayList
* HashSet
* TreeSet
* HashMap
* Comparable interface
* Comparator interface
* equals() and hashCode()
* Wildcards in generics

***

## 📖 Detailed Notes

### 1. Collections Framework

_Essential concept for mastering Java and OOP._

**Example**:

```java
import java.util.*;
import java.io.*;
public class Jukebox1 {
```

### 2. Generics basics

_Essential concept for mastering Java and OOP._

**Example**:

```java
ArrayList<String> songList = new ArrayList<String>();
```

### 3. ArrayList

_Essential concept for mastering Java and OOP._

**Example**:

```java
public static void main(String[] args) {
```

### 4. HashSet

_Essential concept for mastering Java and OOP._

**Example**:

```java
new Jukebox1().go();
```

### 5. TreeSet

_Essential concept for mastering Java and OOP._

**Example**:

```java
}
```

### 6. HashMap

_Essential concept for mastering Java and OOP._

**Example**:

```java
public void go() {	
```

### 7. Comparable interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
getSongs();	
```

### 8. Comparator interface

_Essential concept for mastering Java and OOP._

**Example**:

```java
System.out.println(songList);	
```

***

## 💡 Important Points to Remember

* difference between Song and String? What’s the
* we think it is.)
* part! Whatever “E” is
* the order in
* the order in which elements were last

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Collections Framework
* [ ] Generics basics
* [ ] ArrayList
* [ ] HashSet
* [ ] TreeSet
* [ ] HashMap
* [ ] Comparable interface
* [ ] Comparator interface
* [ ] equals() and hashCode()
* [ ] Wildcards in generics

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 563-614._



## Chapter 16: Data Structures — Study Notes

This chapter dives into the Java Collections Framework, a powerful set of data structures that goes far beyond simple arrays. It covers how to store, sort, and organize objects using Lists, Sets, and Maps, and introduces Generics for type safety.

***

### 1. Generics (Type Safety)

Before Java 5.0, collections like `ArrayList` held only objects of type `Object`. This meant you could accidentally put a `Dog` into a list of `Cat` objects, and you wouldn't know until runtime when the program crashed.

* The Syntax: `ArrayList<String>`
  * The type in the angle brackets `< >` tells the compiler exactly what kind of objects this list can hold.
*   The Benefit: The compiler can now catch errors at compile-time. If you try to add a `Dog` to `ArrayList<String>`, the code won't compile.

    <a class="button secondary"></a>
*   Generic Classes: You can write your own classes that take a type parameter.

    Java

    ```
    public class Box<T> {
        private T content;
        public void add(T t) { content = t; }
    }
    ```

***

### 2. The Collections API

The Java API provides three main interfaces for collecting objects, each with a different purpose.

#### A. List (Sequence)

* Behavior: Knows about index position. It keeps things in the order you inserted them (unless you sort them).
* Duplicates: Allowed.
*   Key Class: `ArrayList` (the standard, growable array), `LinkedList` (better for inserting/deleting in the middle).

    <a class="button secondary"></a>

#### B. Set (Uniqueness)

* Behavior: Prevents duplicates. It does not necessarily keep things in order.
* Duplicates: NOT allowed. If you try to add an object that is already in the set, the add method fails (returns `false`) and the set remains unchanged.
* Key Classes:
  * `HashSet`: Fast access, no ordering.
  *   `TreeSet`: Keeps elements sorted and prevents duplicates.

      <a class="button secondary"></a>

#### C. Map (Key/Value Pairs)

* Behavior: Stores pairs of objects (keys and values). You use the key to find the value (like a dictionary).
* Duplicates: Keys must be unique; values can be duplicates.
* Key Classes:
  * `HashMap`: Fast access, no ordering.
  * `TreeMap`: Keeps keys sorted.

***

### 3. Sorting with `Collections.sort()`

The `ArrayList` class does not have a sort method. Instead, you use the static helper method `Collections.sort()`.

* Sorting Strings: `Collections.sort(myStringList)` works automatically because `String` implements the rules for alphabetizing.
*   Sorting Objects: If you try `Collections.sort(myDogList)`, it won't compile because Java doesn't know how to order `Dog` objects (by size? name? cuteness?).

    <a class="button secondary">+1</a>

#### The `Comparable` Interface

To make your objects sortable, your class must implement `Comparable<T>`.

* Contract: You must implement the `compareTo(T other)` method.
* Logic:
  * Return a negative number if `this` object is smaller than `other`.
  * Return a positive number if `this` object is larger than `other`.
  * Return zero if they are equal.

Java

```
class Song implements Comparable<Song> {
    String title;
    public int compareTo(Song s) {
        return title.compareTo(s.title); // Delegate to String's logic
    }
}
```

#### The `Comparator` Interface

What if you want to sort songs by _Artist_ instead of _Title_? You can't change the `compareTo()` method in the class because it's already used for Title.

* Solution: Create a separate class that implements `Comparator<Song>`.
* Usage: Pass this comparator to the sort method: `Collections.sort(songList, new ArtistCompare())`.

***

### 4. Object Equality (`hashCode` and `equals`)

For a `Set` to prevent duplicates (or a `Map` to find keys), it must be able to determine if two objects are "the same".

* Reference Equality (`==`): Checks if two references point to the exact same object on the heap.
* Object Equality (`equals()`): Checks if two objects are meaningfully equivalent (e.g., two `Song` objects with the same title and artist).
* `hashCode()`: Returns an integer "bucket number" for the object.

#### The Rules

1. If two objects are equal, they MUST have the same hash code.
2. If two objects have the same hash code, they are NOT necessarily equal (collisions can happen).
3.  Override Both: If you override `equals()`, you must override `hashCode()`. If you don't, collections like `HashSet` and `HashMap` will not work correctly, and you might find "duplicate" objects in your Set.

    <a class="button secondary">+1</a>

***

### 5. Polymorphism and Generics

* The Gotcha: `List<Dog>` is NOT a subclass of `List<Animal>`. You cannot pass an `ArrayList<Dog>` to a method that expects `ArrayList<Animal>`.
  * _Why?_ If Java allowed this, the method could add a `Cat` to your `dogList`, breaking type safety.
* Wildcards: To create a method that accepts a list of _any_ Animal subtype, use the wildcard `?`.
  * `void takeAnimals(ArrayList<? extends Animal> animals)`
  *   Restriction: When using `? extends`, you can read from the list, but you cannot add to it (because the compiler can't guarantee what specific subtype the list holds).

      <a class="button secondary"></a>

***

### 6. Revision Checklist

* Diamond Syntax: Do you use `<>` on both sides of the assignment? (e.g., `new ArrayList<String>()`).
* Sort vs. Shuffle: Use `Collections.sort()` to order a list and `Collections.shuffle()` to randomize it.
* Map Keys: Remember that keys in a `HashMap` must override `hashCode()` and `equals()` to be retrieved correctly.
*   TreeSet Requirement: Elements in a `TreeSet` must be `Comparable`, or you must provide a `Comparator` at construction time. Otherwise, it will crash at runtime.

    <a class="button secondary"></a>
