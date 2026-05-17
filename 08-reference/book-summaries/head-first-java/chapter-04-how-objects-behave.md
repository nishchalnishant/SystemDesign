# Ch 04: How Objects Behave

**Source**: Head First Java, Second Edition | **Pages**: 105-128

## 🎯 Learning Objectives

Methods, parameters, and encapsulation

## 📚 Key Concepts

* Method parameters and arguments
* Return types
* Getters and setters
* Encapsulation basics
* Instance variables vs local variables
* Comparing objects
* \== vs equals()

***

## 📖 Detailed Notes

### 1. Method parameters and arguments

_Essential concept for mastering Java and OOP._

**Example**:

```java
And if the Dog class has a method makeNoise(), well, don’t you think a 70-pound dog barks a 
```

### 2. Return types

_Essential concept for mastering Java and OOP._

**Example**:

```java
A class is the blueprint for an object. When you 
```

### 3. Getters and setters

_Essential concept for mastering Java and OOP._

**Example**:

```java
Every instance of a particular class has the same 
```

### 4. Encapsulation basics

_Essential concept for mastering Java and OOP._

**Example**:

```java
The Song class has two instance variables, title 
```

### 5. Instance variables vs local variables

_Essential concept for mastering Java and OOP._

**Example**:

```java
void play() {
    soundPlayer.playSound(title);
}
```

### 6. Comparing objects

_Essential concept for mastering Java and OOP._

**Example**:

```java
Remember: a class describes what an 
```

### 7. == vs equals()

_Essential concept for mastering Java and OOP._

**Example**:

```java
t2.play();
```

***

## 💡 Important Points to Remember

* part: If a method takes a parameter, you must pass
* Java
* moment, we’ve
* a class describes what an
* Java is pass by value, (which

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] Method parameters and arguments
* [ ] Return types
* [ ] Getters and setters
* [ ] Encapsulation basics
* [ ] Instance variables vs local variables
* [ ] Comparing objects
* [ ] \== vs equals()

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 105-128._

## Chapter 4: How Objects Behave — Study Notes

This chapter focuses on the relationship between an object's state (instance variables) and its behavior (methods). You will learn how to pass data to methods, get data back, and how to protect that data through encapsulation.

<a class="button secondary">+3</a>

***

### 1. State Affects Behavior

Every instance of a class has its own values for instance variables. Because methods use these variables, objects of the same type can behave differently based on their state.

<a class="button secondary">+2</a>

*   Instance Variables: Represent what an object knows (its state).

    <a class="button secondary">+2</a>
*   Methods: Represent what an object does (its behavior).

    <a class="button secondary">+2</a>
*   Example: A `Dog` object with a `size` of 70 might have a `bark()` method that prints "Woof! Woof!", while a `Dog` with a `size` of 8 prints "Yip! Yip!".

    <a class="button secondary"></a>

***

### 2. Sending Things to a Method (Parameters)

You can pass values into a method to tell it more about what you want it to do.

<a class="button secondary"></a>

*   Parameters: The variables defined in the method signature that receive values (e.g., `void bark(int numOfBarks)`).

    <a class="button secondary"></a>
*   Arguments: The actual values you pass into the method when calling it (e.g., `d.bark(3)`).

    <a class="button secondary"></a>
*   Type Safety: You must pass the correct type of value that the method expects. If a method takes an `int`, you cannot pass it a `String`.

    <a class="button secondary">+1</a>

***

### 3. Getting Things Back from a Method (Return Values)

Methods can return a value back to the caller.

<a class="button secondary"></a>

*   Return Type: Every method must declare what it returns. If it returns nothing, it is marked `void`.

    <a class="button secondary"></a>
*   Return Statement: If a method has a return type other than `void`, it must use the `return` keyword followed by a value of that type.

    <a class="button secondary">+2</a>

Java

```
int giveSecretNumber() {
    return 42; 
}
```

***

### 4. Java is "Pass-by-Value"

In Java, when you pass an argument to a method, you are passing a copy of the bits inside the variable.

<a class="button secondary">+2</a>

*   Primitives: The method gets a copy of the actual value. If the method changes that value, the original variable outside the method remains unchanged.

    <a class="button secondary">+2</a>
*   References: The method gets a copy of the reference (the "remote control"). This means the method and the caller both point to the same object on the heap. If the method changes the object's state, those changes are visible to the caller.

    <a class="button secondary">+4</a>

***

### 5. Encapsulation (Data Hiding)

To keep your code safe and flexible, you should hide an object's instance variables and provide access only through methods.

<a class="button secondary">+1</a>

*   The Rule: Mark your instance variables as `private` and provide `public` getters and setters.

    <a class="button secondary">+1</a>
* Why it Matters:
  1.  Validation: You can prevent invalid data (e.g., setting a `Dog`'s size to a negative number).

      <a class="button secondary">+1</a>
  2.  Flexibility: You can change how data is stored or calculated internally without breaking other people's code that uses your class.

      <a class="button secondary">+3</a>

Java

```
public class GoodDog {
    private int size; // Private variable

    public int getSize() { // Getter
        return size;
    }

    public void setSize(int s) { // Setter with validation
        if (s > 0) {
            size = s;
        }
    }
}
```

***

### 6. Local vs. Instance Variables

*   Instance Variables: Declared inside a class but outside any method. They have default values (0 for numbers, `null` for references) and live as long as the object lives.

    <a class="button secondary">+3</a>
*   Local Variables: Declared inside a method. They do not have default values and must be initialized before use. They live only as long as the method is on the stack.

    <a class="button secondary"></a>

***

### 7. Comparing Variables

*   `==` Operator: Used to compare the "bits" inside two variables.

    <a class="button secondary">+1</a>

    *   For primitives, it checks if the values are the same (e.g., `5 == 5`).

        <a class="button secondary">+1</a>
    *   For references, it checks if two reference variables point to the exact same object on the heap.

        <a class="button secondary">+1</a>
*   `.equals()` Method: Used to check if two different objects are meaningfully equivalent (e.g., two different `String` objects containing the same characters).

    <a class="button secondary"></a>
