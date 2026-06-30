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
> **What this covers:** How to extract the real requirements from a customer, using a "Dog Door" application as an example.
>
> **Key concepts:**
> - Requirements: What the system *must* do to be successful.
> - Use Cases: A specific scenario describing what a system does to achieve a particular goal for a particular user (Actor).
> - Main Success Scenario (Happy Path): The steps where everything goes right.
> - Alternate Paths: What happens when things go wrong (e.g., the dog gets stuck outside).
>
> **Key takeaway:** Customers rarely know exactly what they want. It is your job as an analyst to write down use cases, find the edge cases (alternate paths), and ensure the system accounts for them *before* you write the code.

# Ch 02: Gathering Requirements

**Source**: Head First Object-Oriented Analysis & Design | **Pages**: 90-145

## 🎯 Learning Objectives

Master object-oriented analysis and design principles for building flexible, maintainable software.

## 📚 Key Concepts

* Requirements gathering
* Use cases
* User stories
* Customer needs

***

## 📖 Detailed Notes

_This chapter focuses on practical OOA\&D principles and techniques for real-world software development._

### 1. Requirements gathering

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 2. Use cases

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 3. User stories

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 4. Customer needs

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

***

## 💡 Three Key Takeaways

1. **Requirements gathering** - Core principle for this chapter
2. **Use cases** - Applying concepts in practice
3. **User stories** - Industry standards and approaches

***

## ✅ Self-Check Questions

1. What are the main OOA\&D concepts covered in this chapter?
2. How do these principles improve software design?
3. Can you apply these concepts to a real project?
4. What are the trade-offs of different design approaches?

## 🔄 Quick Revision Checklist

* [ ] Requirements gathering
* [ ] Use cases
* [ ] User stories
* [ ] Customer needs

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

_For complete details, case studies, and examples, refer to Head First OOA\&D, pages 90-145._



## Chapter 2: Give Them What They Want

**"Everybody loves a satisfied customer"**

### Overview

This chapter focuses on the first step of building great software: **Gathering Requirements**. It uses the example of "Doug's Dog Doors" to demonstrate how to understand what the customer really needs, how to handle "happy paths" vs. "alternate paths," and how to use **Use Cases** to document requirements effectively.

***

### Case Study: Doug's Dog Doors

#### 1. The Initial Request

Todd and Gina want a high-tech dog door for their dog, Fido. They are tired of getting out of bed to let him out.

* **Initial Solution:** A simple door system with a remote control.
* **The Code:**
  * \[cite\_start]`DogDoor` class: Has `open()`, `close()`, and `isOpen()` methods\[cite: 3].
  * \[cite\_start]`Remote` class: Has a `pressButton()` method that toggles the door open/closed\[cite: 3].
  * **The Bug:** Fido goes out, does his business, but before he can come back in, the door stays open (letting in rabbits/rats) or closes and locks him out. \[cite\_start]Todd and Gina have to manually close it, which they forget to do\[cite: 3].

#### 2. The Requirement Analysis (Step 1 Revisited)

The initial failure happened because the developers didn't fully understand the "System" or how the customer would use it.

* **The Real Problem:** Todd and Gina don't want to _operate_ the door; they want the door to handle Fido's entry/exit automatically so they can sleep.
* \[cite\_start]**Updated Requirement:** The door must close automatically after a few seconds\[cite: 3].

#### 3. Requirements Checklist

\[cite\_start]A **Requirement** is a singular need detailing what a particular product or service should be or do\[cite: 3].

* **List for Dog Door 2.0:**
  1. Door opening must be at least 12" tall.
  2. Remote button toggles the door (opens if closed, closes if open).
  3. \[cite\_start]**Automatic Closing:** Once opened, the door should close automatically if not already closed\[cite: 3].

***

### Key Concepts

#### 1. The "System"

* **Definition:** The system is everything needed to meet a customer's goals.
* \[cite\_start]**Scope:** It includes the software, the hardware (door, remote), and how it interacts with external actors (Todd, Gina, Fido)\[cite: 3].
* **Role of Developer:** You must understand the system better than the customer does to anticipate problems they haven't thought of.

#### 2. Use Cases

\[cite\_start]A **Use Case** describes what your system does to accomplish a particular customer goal\[cite: 3]. It is a technique for capturing potential requirements.

* **Structure of a Use Case:**
  1. **Clear Value:** It must help the customer achieve their goal.
  2. **Start and Stop:** It must have a definite starting point (trigger) and stopping point (completion).
  3. \[cite\_start]**External Initiator:** It is started by something _outside_ the system (e.g., Fido barking)\[cite: 3].
* \[cite\_start]**Example Use Case: Fido goes outside** \[cite: 3]
  1. Fido barks to be let out (**Start/External Initiator**).
  2. Todd/Gina hears Fido.
  3. Todd/Gina presses the remote button.
  4. The dog door opens.
  5. Fido goes outside.
  6. Fido does his business.
  7. Fido goes back inside.
  8. The door shuts automatically (**Stop/Goal Achieved**).

#### 3. Alternate Paths (Handling Failure)

The "Main Path" (or Happy Path) is the scenario where everything goes right. \[cite\_start]**Alternate Paths** handle what happens when things go wrong\[cite: 3].

* **Scenario:** What if the door closes before Fido gets back inside?
* **Updated Use Case Steps (Alternate Path):**
  * 6.1 The door shuts automatically.
  * 6.2 Fido barks to be let back inside.
  * 6.3 Todd/Gina presses the button again.
  * 6.4 The door opens again.
  * 6.5 Fido returns inside.

***

### The Process for Great Software (Updated)

1. **Gather Requirements:** Listen to the customer and understand what they want the system to do.
2. **Describe the Use Case:** Write down the steps the system takes to get the user to their goal.
3. **Check for Alternate Paths:** Ask "What could go wrong?" (e.g., Door jams? Fido stays out too long?) \[cite\_start]and update the use case to handle these scenarios\[cite: 3].
4. **Code:** Only after understanding the Happy Path and Alternate Paths do you write the code.

### Chapter Summary (Bullet Points)

* **Requirements are about the "What", not the "How".** Focus on what the system needs to do before figuring out how to implement it.
* **A Use Case is a story.** It tells a story about how a user interacts with your system to achieve a specific goal.
* \[cite\_start]**One Use Case per Goal.** If your system does three distinct things (e.g., "Let dog out", "Lock door for vacation", "Test battery"), you need three separate use cases\[cite: 3].
* **The Customer isn't always right.** They often don't know what they truly need until you help them uncover the "Alternate Paths" where things might go wrong.
* \[cite\_start]**External Initiators start the flow.** A use case is always triggered by something outside the system (a user, a timer, another program, or a dog)\[cite: 3].
