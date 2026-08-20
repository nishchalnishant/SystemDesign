# Design Pattern Mnemonics Cheatsheet

A quick reference guide for recalling the 22 Gang of Four (GoF) design patterns covered in *Dive into Design Patterns*.

---

## 1. Creational Patterns (5)
*How objects are created.*

**Acronym**: `FAB PS`
**Phrase**: **F**ive **A**rchitects **B**uild **P**erfect **S**ystems

1. **F**actory Method: Subclasses decide the concrete class.
2. **A**bstract Factory: Families of related objects.
3. **B**uilder: Step-by-step construction.
4. **P**rototype: Clone existing objects.
5. **S**ingleton: One instance, global access.

---

## 2. Structural Patterns (7)
*How objects are composed into larger structures.*

**Acronym**: `ABCD FFP`
**Phrase**: **A**ll **B**rave **C**ats **D**o **F**ly **F**or **P**rey

1. **A**dapter: Make incompatible interfaces work together.
2. **B**ridge: Split abstraction from implementation.
3. **C**omposite: Tree structures treated uniformly.
4. **D**ecorator: Add behavior via wrappers.
5. **F**acade: Simple interface for a complex subsystem.
6. **F**lyweight: Share intrinsic state to save RAM.
7. **P**roxy: Placeholder to control access.

---

## 3. Behavioral Patterns (10)
*How objects communicate and assign responsibilities.*

**Acronym**: `VIM COMICS S`
**Phrase**: **C**razy **C**oding **I**nterviews **M**ake **M**e **O**bserve **S**tate **S**trategy **T**o **V**ictory!

1. **C**hain of Responsibility: Pipeline of handlers.
2. **C**ommand: Requests as standalone objects (undoable).
3. **I**terator: Uniform traversal.
4. **M**ediator: Centralized communication (no spaghetti).
5. **M**emento: Save/restore state snapshots.
6. **O**bserver: Publish-subscribe events.
7. **S**tate: Behavior changes based on internal state.
8. **S**trategy: Swappable algorithms.
9. **T**emplate Method: Fixed skeleton, variable steps.
10. **V**isitor: External operations on a class hierarchy.
