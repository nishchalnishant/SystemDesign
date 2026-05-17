# Ch 08: Design Principles

**Source**: Head First Object-Oriented Analysis & Design | **Pages**: 410-457

## 🎯 Learning Objectives

Master object-oriented analysis and design principles for building flexible, maintainable software.

## 📚 Key Concepts

* OO principles
* SOLID principles
* DRY
* Open-Closed Principle
* Liskov Substitution

***

## 📖 Detailed Notes

_This chapter focuses on practical OOA\&D principles and techniques for real-world software development._

### 1. OO principles

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 2. SOLID principles

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 3. DRY

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 4. Open-Closed Principle

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 5. Liskov Substitution

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

***

## 💡 Three Key Takeaways

1. **OO principles** - Core principle for this chapter
2. **SOLID principles** - Applying concepts in practice
3. **DRY** - Industry standards and approaches

***

## ✅ Self-Check Questions

1. What are the main OOA\&D concepts covered in this chapter?
2. How do these principles improve software design?
3. Can you apply these concepts to a real project?
4. What are the trade-offs of different design approaches?

## 🔄 Quick Revision Checklist

* [ ] OO principles
* [ ] SOLID principles
* [ ] DRY
* [ ] Open-Closed Principle
* [ ] Liskov Substitution

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

_For complete details, case studies, and examples, refer to Head First OOA\&D, pages 410-457._



## Chapter 8: Originality is Overrated

**"Design Principles"**

### Overview

This chapter shifts focus from "inventing" solutions to "reusing" proven ones. It introduces **Design Principles**—basic tools and techniques that make code more maintainable, flexible, and extensible. The core message is that you don't need to be original to be a great designer; you just need to know when to apply established principles like the **Open-Closed Principle (OCP)** and **Don't Repeat Yourself (DRY)**.

***

### Key Design Principles

#### 1. The Open-Closed Principle (OCP)

This is the centerpiece of the chapter.

* \[cite\_start]**Definition:** Classes should be **open for extension** but **closed for modification**. \[cite: 12]
  * **Closed for modification:** Once a class is working and verified, you shouldn't change its code. \[cite\_start]This prevents new bugs from breaking existing, working logic. \[cite: 12]
  * \[cite\_start]**Open for extension:** You should be able to extend the behavior of the class (usually through inheritance or composition) to add new features or handle new situations. \[cite: 12]
* **Example from the Book:** `InstrumentSpec`.
  * The base `matches()` method in `InstrumentSpec` is generic and works for most instruments. \[cite\_start]It is _closed_ for modification. \[cite: 12]
  * When we need specific behavior for a Guitar (e.g., matching string count), we create `GuitarSpec` which extends `InstrumentSpec`. We override `matches()` to add the new logic. \[cite\_start]This _extends_ the behavior without touching the original `InstrumentSpec` code. \[cite: 12]

#### 2. Don't Repeat Yourself (DRY)

* \[cite\_start]**Definition:** Avoid duplicate code by abstracting out things that are common and placing them in a single location. \[cite: 1]
* **Why it matters:**
  * **Maintenance:** If you have the same logic in three places, and requirements change, you have to fix it in three places. If you forget one, you have a bug.
  * **Risk:** Every time you touch code, you risk breaking it. DRY reduces the surface area you have to touch when changes happen.
* \[cite\_start]**Application:** We extracted common properties like `price` and `serialNumber` into the parent `Instrument` class so we didn't have to repeat them in `Guitar`, `Mandolin`, and `Banjo`. \[cite: 9]

#### 3. Single Responsibility Principle (SRP)

* \[cite\_start]**Definition:** Every object should have a single responsibility, and all its services should be focused on carrying out that single responsibility. \[cite: 10]
* **The Litmus Test:** Ask, "Does this class have only **one reason to change**?"
  * _Example:_ An `Automobile` class shouldn't also handle `wash()` or `changeTires()`. Those are different responsibilities (Mechanic/CarWash). \[cite\_start]If the washing instructions change, the `Automobile` class shouldn't have to be recompiled. \[cite: 10]
* **Cohesion:** SRP leads to **High Cohesion**. A highly cohesive class does one thing well. \[cite\_start]Low cohesion classes are "Monoliths" that try to do everything. \[cite: 10]

#### 4. Liskov Substitution Principle (LSP)

* **Definition:** Subtypes must be substitutable for their base types.
* **Meaning:** If you have code that uses an `Instrument` object, it should work correctly whether you pass it a `Guitar`, `Mandolin`, or `Banjo`. The code shouldn't have to check `if (instrument is Guitar)` to function.
* **Violation:** If a subclass changes the behavior of the parent so drastically that it breaks the code relying on the parent, it violates LSP.

***

### Composition vs. Inheritance vs. Aggregation

The chapter clarifies the relationships between objects, which is crucial for applying OCP and DRY correctly.

| Relationship    | Concept                                                                      | Phrase    | Example                                                                                                                          |
| --------------- | ---------------------------------------------------------------------------- | --------- | -------------------------------------------------------------------------------------------------------------------------------- |
| **Inheritance** | One class inherits behavior/state from another.                              | **IS-A**  | \[cite\_start]A `Guitar` **IS-A** `Instrument`. \[cite: 9]                                                                       |
| **Composition** | One class is made up of other objects. If the container dies, the parts die. | **HAS-A** | A `Unit` **HAS-A** `Map` of properties. (If Unit is deleted, the properties are gone)\[cite\_start]. \[cite: 11]                 |
| **Aggregation** | One class uses another, but they can exist independently.                    | **HAS-A** | An `Inventory` **HAS-A** list of `Instrument`s. (If Inventory is deleted, the Instruments still exist)\[cite\_start]. \[cite: 8] |

**Key Guideline:** Favor **Composition** (or Aggregation) over Inheritance. Composition is often more flexible because you can change behavior at runtime (by swapping the object you rely on), whereas inheritance is fixed at compile time.

***

### Practical Application: The Unit Class (Strategy Game)

The chapter applies these principles to the "Gary's Games" case study (from Chapter 6 & 7).

* **The Problem:** We need a `Unit` class that works for Tanks, Soldiers, and Wizards.
* **Bad Approach:** Create `Tank`, `Soldier`, and `Wizard` subclasses. (Violates DRY/OCP if behaviors overlap or explode in complexity)\[cite\_start]. \[cite: 11]
* **Good Approach (Property Pattern):**
  * Create a single `Unit` class.
  * \[cite\_start]Use a `Map<String, Object>` to store properties (`"hitPoints"`, `"mana"`, `"armor"`). \[cite: 11]
  * **Result:** We can create _any_ unit type without creating a new class. We just configure the properties differently.
  * **OCP Compliant:** We don't need to modify `Unit.java` to add a new unit type. \[cite\_start]We just change the configuration (data) we feed it. \[cite: 11]

***

### Chapter Summary (Bullet Points)

* **Design Principles are tools, not rules.** They are proven solutions to common problems. \[cite\_start]Use them to make your life easier. \[cite: 12]
* **OCP (Open-Closed Principle):** Keep your working code closed to modification. Extend it to add new behavior. \[cite\_start]This keeps your software robust. \[cite: 12]
* **DRY (Don't Repeat Yourself):** Extract commonality into a single place. \[cite\_start]If you change it, you change it everywhere. \[cite: 1]
* **SRP (Single Responsibility Principle):** One class, one reason to change. \[cite\_start]This leads to high cohesion and loose coupling. \[cite: 10]
* **Delegate to Reuse.** Instead of putting all logic in one place, delegate tasks to other objects (like `Inventory` delegating to `InstrumentSpec`). \[cite\_start]This makes your code more modular. \[cite: 5]
* \[cite\_start]**Composition/Aggregation is powerful.** It allows you to build complex objects from smaller, reusable parts, often offering more flexibility than deep inheritance hierarchies. \[cite: 8]
