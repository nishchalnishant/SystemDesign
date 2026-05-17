# Ch 05: Good Design = Flexible Software

**Source**: Head First Object-Oriented Analysis & Design | **Pages**: 232-313

## 🎯 Learning Objectives

Master object-oriented analysis and design principles for building flexible, maintainable software.

## 📚 Key Concepts

* Encapsulation
* Code reuse
* Delegation
* Composition vs Inheritance
* Design patterns intro

***

## 📖 Detailed Notes

_This chapter focuses on practical OOA\&D principles and techniques for real-world software development._

### 1. Encapsulation

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 2. Code reuse

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 3. Delegation

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 4. Composition vs Inheritance

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

### 5. Design patterns intro

_Essential for mastering object-oriented analysis and design._

**Key Principles**:

* Focus on creating flexible, maintainable software
* Apply OO thinking to real-world problems
* Use proper analysis before implementation

***

## 💡 Three Key Takeaways

1. **Encapsulation** - Core principle for this chapter
2. **Code reuse** - Applying concepts in practice
3. **Delegation** - Industry standards and approaches

***

## ✅ Self-Check Questions

1. What are the main OOA\&D concepts covered in this chapter?
2. How do these principles improve software design?
3. Can you apply these concepts to a real project?
4. What are the trade-offs of different design approaches?

## 🔄 Quick Revision Checklist

* [ ] Encapsulation
* [ ] Code reuse
* [ ] Delegation
* [ ] Composition vs Inheritance
* [ ] Design patterns intro

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

_For complete details, case studies, and examples, refer to Head First OOA\&D, pages 232-313._



## Chapter 5: Nothing Ever Stays the Same

**"Good Design = Flexible Software"**

### Overview

This chapter marks a transition from analysis back to design. It revisits the "Rick's Guitars" application from Chapter 1. The business is expanding to sell Mandolins (and eventually Banjos), but the current design is rigid. The chapter focuses on using **Abstract Classes**, **Interfaces**, and **UML** to create a flexible application that can handle change without breaking.

***

### Case Study: Rick's Guitars (Expansion)

#### 1. The Problem: Adding Mandolins

Rick decides to start selling Mandolins.

* **The Quick Fix (Bad Design):** Copy the `Guitar` code, rename it `Mandolin`, and add it to the system.
* **Why it fails:**
  * **Duplicate Code:** `Guitar` and `Mandolin` share fields like `price`, `serialNumber`, and `builder`. If you fix a bug in one, you have to remember to fix it in the other.
  * **Maintenance Nightmare:** The `Inventory` class now needs separate lists for guitars and mandolins, and separate search methods (`search(GuitarSpec)` vs `search(MandolinSpec)`).

#### 2. The Solution: Abstraction

To solve the duplicate code problem, we lift common behavior and attributes into a parent class.

* **Abstract Base Class (`Instrument`):**
  * Created to hold shared properties: `serialNumber`, `price`, `InstrumentSpec`.
  * **Why Abstract?** You never have just an "Instrument" in the store; it's always a specific _type_ (Guitar, Mandolin). Therefore, `Instrument` should not be instantiated directly.
* **Abstract Base Class (`InstrumentSpec`):**
  * Created to hold shared spec properties: `builder`, `model`, `type`, `backWood`, `topWood`.

#### 3. The Design Flaw: Rigid Subclasses

Even with the `Instrument` class, the design is still brittle.

* **Scenario:** Rick wants to sell Banjos.
* **Problem:** Banjos don't have a "back wood" or "top wood" in the same way guitars do. If we force `Banjo` to inherit from `InstrumentSpec`, we might inherit properties that don't make sense.
* **The "Ripple Effect":** Every time a new instrument type is added, we have to create new subclasses and potentially modify the `Inventory` search logic.

***

### Key Concepts & Principles

#### 1. Abstract Classes vs. Interfaces

* **Abstract Class:** Use when you have shared _code_ (behavior/state) that subclasses should inherit.
  * _Example:_ `Instrument` (all instruments have a price and serial number).
* **Interface:** Use when you have shared _roles_ or _capabilities_ that valid classes must fulfill, but they don't share implementation code.
  * _Concept:_ Defines _what_ a class can do, not _how_ it does it.

#### 2. Encapsulate What Varies (Revisited)

The properties of instruments vary wildly (Guitars have strings, Banjos have heads, Drums have skins). Hardcoding these into the class structure (e.g., `getNumStrings()`) is bad design.

* **Dynamic Properties:** Instead of hardcoded fields, use a flexible data structure like a `Map` (Dictionary) to store properties.
  * _Implementation:_ `Map properties = new HashMap();`
  * _Benefit:_ You can add "numStrings" for a Guitar and "skinType" for a Drum without changing the `InstrumentSpec` class code.

#### 3. Cohesion & Single Responsibility

* **Cohesion:** A measure of how closely related the functionality of a class is.
  * **High Cohesion:** The class does one thing well (e.g., `InstrumentSpec` only stores instrument specifications).
  * **Low Cohesion:** The class tries to do everything (e.g., `Inventory` storing items, searching, and managing sales).
* **Single Responsibility Principle:** Every class should have only one reason to change.

***

### The "Search" Refactoring

The most critical improvement in this chapter is the generic search method.

**Before (Rigid):**

```java
public List search(GuitarSpec searchSpec) { ... }
public List search(MandolinSpec searchSpec) { ... }
```
