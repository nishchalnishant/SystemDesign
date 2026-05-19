# OOP Introduction

**Question**: You have a 5,000-line program in one file. You need to add a feature. Where do you start? How do you make sure your addition doesn't break the ten things it touches?

That question — "how do I change one thing without breaking everything else?" — is the reason OOP exists.

---

## The Problem Without Structure

Early programs were a single room: everything piled in one place. Global variables, functions calling other functions in arbitrary order, no clear ownership of data. This works until the program grows. Then:

- Changing a variable in function A breaks function B, which you forgot also reads it.
- Adding a feature requires understanding the entire program — no piece can be changed in isolation.
- Testing one part requires running all parts.

The core constraint is **tight coupling**: everything depends on everything. The cost of changing anything grows with program size.

## The Minimal Fix

Group related data and behavior together. Put the data a function needs right next to that function. Now a change to "bank account balance logic" only touches the bank account code — not the logging code, not the UI code.

This grouping is a **class**. An instantiated class is an **object**.

## The Full Structure

Object-oriented programming extends this minimal fix into a discipline:

- **Containment**: a change inside one object does not propagate to others unless the object's public contract changes.
- **Reuse**: a good class design can be instantiated many times, or subclassed to share structure.
- **Extension**: add a new type (a new payment method, a new shape) by adding a class — without touching existing classes.

Think of software as a city. Each **building** (class) has a clear purpose, its own internals, and well-defined entrances. A hospital does not need to know how the power grid works. They communicate through interfaces: "send electricity here." The internals stay hidden.

OOP is not magic — it is a discipline. A badly designed city of tangled, interdependent buildings is worse than a small, well-organized room. The four pillars — Encapsulation, Abstraction, Inheritance, Polymorphism — are the zoning rules that keep the city functional as it grows.

---

For full coverage — pillars, IS-A vs HAS-A, DRY/KISS/YAGNI, Law of Demeter, code examples in Java and Python — see [four-pillars.md](four-pillars.md).
