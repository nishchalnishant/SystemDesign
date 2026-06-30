---
module: 08-reference
topic: Book Summaries
subtopic: Head First Ooand
status: unread
tags: [08-reference, system-design, book-summaries]
---
> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Dealing with the inevitable truth of software development: requirements will always change mid-project.
>
> **Key concepts:**
> - Updating Use Cases: When the customer asks for a new feature (like a bark recognizer for the Dog Door), you must first update the use case, not the code.
> - Code Flexibility: If your code is tightly coupled, a requirement change will require a massive rewrite.
> - Delegation: Separating responsibilities. Instead of the Dog Door handling the bark recognition, delegate that to a `BarkRecognizer` object.
>
> **Key takeaway:** The single constant in software engineering is change. By encapsulating what varies and delegating responsibilities, you create a system that can absorb new requirements without breaking existing functionality.

# Ch 03: Requirements Change

**Source**: Head First Object-Oriented Analysis & Design | **Pages**: 146-179

## 🎯 Learning Objectives

Master object-oriented analysis and design principles for building flexible, maintainable software.

## 📚 Key Concepts

* Change management
* Flexible design
* Anticipating change
* Refactoring

***

## 📖 Detailed Notes

_This chapter focuses on practical OOA\&D principles and techniques for real-world software development._

### 1. Change management

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 2. Flexible design

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 3. Anticipating change

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 4. Refactoring

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

***

## 💡 Three Key Takeaways

1. **Change management** - Core principle for this chapter
2. **Flexible design** - Applying concepts in practice
3. **Anticipating change** - Industry standards and approaches

***

## ✅ Self-Check Questions

1. What are the main OOA\&D concepts covered in this chapter?
2. How do these principles improve software design?
3. Can you apply these concepts to a real project?
4. What are the trade-offs of different design approaches?

## 🔄 Quick Revision Checklist

* [ ] Change management
* [ ] Flexible design
* [ ] Anticipating change
* [ ] Refactoring

***

## 📝 Practice Exercises

1. Apply the chapter concepts to your current project
2. Create diagrams and models using the techniques learned
3. Review existing code and identify improvement opportunities
4. Discuss design decisions with your team

## 🔗 Related Topics

* Software architecture patterns
* Design principles and best practices
* UML diagrams and modeling
* Agile development practices

***

_For complete details, case studies, and examples, refer to Head First OOA\&D, pages 146-179._



## Chapter 3: I Love You, You're Perfect... Now Change

**"Requirements Change"**

### Overview

This chapter tackles the inevitable reality of software development: **requirements always change**. It continues the "Doug's Dog Doors" case study, showing how to handle changes without breaking the existing system. The chapter introduces the "Code-Test-Iterate" cycle and emphasizes that a system is never truly "finished"—it just evolves.

***

### Case Study: Dog Door 3.0

#### 1. The New Requirement (The Change)

Just when the team thought the dog door was perfect, Todd and Gina call with a new problem.

* **The Scenario:** Fido barks, the door opens, but before he can go out, he gets distracted (e.g., chasing a bug).
* **The Problem:** The door closes automatically after 5 seconds. Fido is now stuck inside. He barks again, but the remote is back in the bedroom with Todd and Gina.
* **The Failure:** The current system assumes Fido _always_ goes out immediately. It doesn't handle the "alternate path" where he stalls.

#### 2. The Solution: Hardware Recognition

Doug (the boss) suggests a hardware fix: a **Bark Recognizer**.

* **Concept:** A device on Fido's collar that "listens" for a bark. When Fido barks, the door opens automatically. No need for Todd and Gina to press the remote!
* **Impact on Code:** We need to update the software to handle this new input (the bark recognizer) while keeping the old input (the remote) working.

***

### Key Concepts & Processes

#### 1. The One Constant in Software Analysis & Design

**"Change."** No matter how well you gathered requirements in Chapter 2, they will change.

* **Why?** Customers see the software working and realize they want something else, or new scenarios arise (like Fido getting distracted).
* **Your Job:** Design software that is _flexible_ enough to handle change without breaking.

#### 2. Updating the Use Case

Before writing code, we must update the Use Case to reflect the new reality.

**Updated Use Case: Fido goes outside (Version 3.0)**

1. Fido barks to be let out.
2. **The Bark Recognizer "hears" a bark.** (New Step)
3. **The Bark Recognizer sends a signal to the dog door.** (New Step)
4. The dog door opens.
5. Fido goes outside.
6. Fido does his business.
7. Fido goes back inside.
8. The door shuts automatically.

* **Alternate Paths:** We still need to handle scenarios where Fido gets stuck outside or the door jams.

#### 3. The "Code-Test-Iterate" Cycle

This chapter formalizes the development loop. You don't just "write code" once.

1. **Code:** Update the classes to handle the new requirement.
   * _Action:_ Created `BarkRecognizer` class.
   * _Action:_ Updated `DogDoor` to handle requests from the recognizer.
2. **Test:** Run the simulator to see if it works.
   * _Observation:_ Fido barks, the door opens. Success?
3. **Iterate (Fixing Bugs):**
   * _The New Bug:_ The door opens for _any_ sound (a car horn, another dog).
   * _The Fix:_ We need to verify _which_ dog is barking. The `BarkRecognizer` needs to check if the bark matches Fido's stored bark signature.
   * _Refactoring:_ Instead of just `String` comparisons (e.g., "Woof"), we might need more robust matching (hinting at future object encapsulation).

***

### New Classes & Implementation Details

#### `BarkRecognizer` Class

A new class added to the system to handle the hardware "listener."

```java
public class BarkRecognizer {
    private DogDoor door;

    public BarkRecognizer(DogDoor door) {
        this.door = door;
    }

    public void recognize(String bark) {
        System.out.println("BarkRecognizer: Heard a '" + bark + "'");
        door.open();
    }
}
```
