# Ch 11: Risky Behavior

**Source**: Head First Java, Second Edition | **Pages**: 349-386

## 🎯 Learning Objectives

Exception handling

## 📚 Key Concepts

* try-catch blocks
* Multiple catch blocks
* finally block
* Throwing exceptions
* Ducking (declaring) exceptions
* Checked vs unchecked exceptions
* Making your own exceptions
* Exception hierarchy

***

## 📖 Detailed Notes

### 1. try-catch blocks

_Essential concept for mastering Java and OOP._

**Example**:

```java
part of the standard J2SE class library. JavaSound is split into two 
```

### 2. Multiple catch blocks

_Essential concept for mastering Java and OOP._

**Example**:

```java
a CD-player on your stereo, but with a few added features. The Sequencer class 
```

### 3. finally block

_Essential concept for mastering Java and OOP._

**Example**:

```java
import javax.sound.midi.*;
public class MusicTest1 {  
```

### 4. Throwing exceptions

_Essential concept for mastering Java and OOP._

**Example**:

```java
    public void play() {
        Sequencer sequencer = MidiSystem.getSequencer();       
```

### 5. Ducking (declaring) exceptions

_Essential concept for mastering Java and OOP._

**Example**:

```java
        System.out.println(“We got a sequencer”);     
    } // close play
    public static void main(String[] args) {
        MusicTest1 mt = new MusicTest1();
        mt.play();
    } // close main
} // close class
```

### 6. Checked vs unchecked exceptions

_Essential concept for mastering Java and OOP._

**Example**:

```java
    Sequencer sequencer = MidiSystem.getSequencer();   
```

### 7. Making your own exceptions

_Essential concept for mastering Java and OOP._

**Example**:

```java
class that you didn’t 
```

### 8. Exception hierarchy

_Essential concept for mastering Java and OOP._

**Example**:

```java
(probably in a class you didn’t write) is risky?
```

***

## 💡 Important Points to Remember

* cleanup code­ in one place instead of
* if exceptions were of type Broccoli.
* from your polymorphism chapters that
* from the polymorphism chapters means the object is from a
* on a piano! (OK, maybe not someone, but something.)

***

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

* [ ] try-catch blocks
* [ ] Multiple catch blocks
* [ ] finally block
* [ ] Throwing exceptions
* [ ] Ducking (declaring) exceptions
* [ ] Checked vs unchecked exceptions
* [ ] Making your own exceptions
* [ ] Exception hierarchy

***

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

***

_For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 349-386._



## Chapter 11: Risky Behavior — Study Notes

This chapter introduces Exception Handling in Java. It teaches how to write code that deals with errors gracefully, ensuring that your program doesn't crash when something unexpected happens (like a file being missing or a server being down).

### 1. The Risky Method

Some methods in Java are considered "risky" because they interact with things outside your program's control (e.g., files, network connections).

* The Problem: If you call a risky method, it might fail at runtime.
*   The Solution: You must acknowledge the risk. Java enforces this by requiring you to wrap the risky call in a `try/catch` block or declare that your method "ducks" the exception.

    <a class="button secondary"></a>

### 2. Try/Catch Blocks

The `try/catch` block is how you handle exceptions. You put the risky code in the `try` block and the recovery code in the `catch` block.

<a class="button secondary"></a>Java

```
try {
    // Risky code goes here
    Sequencer sequencer = MidiSystem.getSequencer();
    System.out.println("We got a sequencer");
} catch (MidiUnavailableException ex) {
    // Recovery code goes here
    System.out.println("Bummer, no sequencer");
}
```

* Flow Control:
  * If the code in `try` succeeds, the `catch` block is skipped.
  *   If the code in `try` fails (throws an exception), the rest of the `try` block is skipped, and execution jumps immediately to the `catch` block.

      <a class="button secondary"></a>

### 3. Checked vs. Unchecked Exceptions

Not all exceptions are treated equally by the compiler.

<a class="button secondary"></a>

* Checked Exceptions: The compiler _forces_ you to handle these. They typically represent failures you cannot predict or prevent (e.g., `FileNotFoundException`).
*   Unchecked Exceptions (RuntimeExceptions): The compiler does _not_ check these. They usually represent logic errors in your code (e.g., `NullPointerException`, `ArrayIndexOutOfBoundsException`). You _can_ catch them, but you are not required to.

    <a class="button secondary"></a>

### 4. The `finally` Block

A `finally` block is used for code that must run no matter what—whether the exception happens or not.

* Usage: cleaning up resources like closing a file or network connection.

Java

```
try {
    turnOvenOn();
    bakeCake();
} catch (BakingException ex) {
    ex.printStackTrace();
} finally {
    turnOvenOff(); // This runs regardless of success or failure
}
```

### 5. Ducking Exceptions

If you don't want to handle an exception in your method, you can "duck" it by declaring that your method throws it. This passes the responsibility to the method that called you.

<a class="button secondary"></a>Java

```
public void takeRisk() throws BadException {
    // risky code that might throw BadException
}
```

### 6. Exception Polymorphism

*   Hierarchy: All exceptions inherit from `Throwable`. You can catch multiple specific exceptions or catch a general `Exception` superclass.

    <a class="button secondary"></a>
*   Order Matters: If you have multiple catch blocks, put the most specific exceptions (subclasses) first and the most generic (superclasses) last. If you catch `Exception` first, it will catch everything, and the specific blocks below it will never run.

    <a class="button secondary"></a>

### 7. Revision Checklist

* Syntax: Do you have your `try`, `catch`, and optional `finally` blocks in the correct order?
* Declaration: If you call a method that throws a checked exception, have you either caught it or declared that you throw it?
*   Scope: Remember that variables declared inside a `try` block are local to that block and cannot be accessed in the `catch` or `finally` blocks.

    <a class="button secondary"></a>
*   `printStackTrace()`: Use this method on the exception object in your catch block to see a trace of where the error occurred.

    <a class="button secondary"></a>
