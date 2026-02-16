# Chapter 11: Risky Behavior

**Source**: Head First Java, Second Edition | **Pages**: 349-386

## 🎯 Learning Objectives

Exception handling

## 📚 Key Concepts

- try-catch blocks
- Multiple catch blocks
- finally block
- Throwing exceptions
- Ducking (declaring) exceptions
- Checked vs unchecked exceptions
- Making your own exceptions
- Exception hierarchy

---

## 📖 Detailed Notes

### 1. try-catch blocks

*Essential concept for mastering Java and OOP.*

**Example**:
```java
part of the standard J2SE class library. JavaSound is split into two 
```


### 2. Multiple catch blocks

*Essential concept for mastering Java and OOP.*

**Example**:
```java
a CD-player on your stereo, but with a few added features. The Sequencer class 
```


### 3. finally block

*Essential concept for mastering Java and OOP.*

**Example**:
```java
import javax.sound.midi.*;
public class MusicTest1 {  
```


### 4. Throwing exceptions

*Essential concept for mastering Java and OOP.*

**Example**:
```java
    public void play() {
        Sequencer sequencer = MidiSystem.getSequencer();       
```


### 5. Ducking (declaring) exceptions

*Essential concept for mastering Java and OOP.*

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

*Essential concept for mastering Java and OOP.*

**Example**:
```java
    Sequencer sequencer = MidiSystem.getSequencer();   
```


### 7. Making your own exceptions

*Essential concept for mastering Java and OOP.*

**Example**:
```java
class that you didn’t 
```


### 8. Exception hierarchy

*Essential concept for mastering Java and OOP.*

**Example**:
```java
(probably in a class you didn’t write) is risky?
```


---

## 💡 Important Points to Remember

- cleanup code­ in one place instead of 
- if exceptions were of type Broccoli. 
- from your polymorphism chapters that 
- from the polymorphism chapters means the object is from a 
- on a piano! (OK, maybe not someone, but something.) 

---

## ✅ Self-Check Questions

Test your understanding:

1. Can you explain the main concepts covered in this chapter?
2. Can you write code examples demonstrating these concepts?
3. Do you understand when and why to use these features?
4. Can you explain the benefits and tradeoffs?

## 🔄 Quick Revision Points

- [ ] try-catch blocks
- [ ] Multiple catch blocks
- [ ] finally block
- [ ] Throwing exceptions
- [ ] Ducking (declaring) exceptions
- [ ] Checked vs unchecked exceptions
- [ ] Making your own exceptions
- [ ] Exception hierarchy

---

## 📝 Practice Exercises

1. Write your own code examples for each key concept
2. Modify existing examples to test edge cases
3. Explain concepts to someone else
4. Create a small project using these concepts

## 🔗 Related Chapters

Review related concepts from other chapters to build comprehensive understanding.

---

*For complete details, diagrams, and all examples, refer to Head First Java Second Edition, pages 349-386.*
