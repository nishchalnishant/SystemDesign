> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** OOP fundamentals for LLD interviews — the conceptual foundations you must know before tackling design patterns or LLD problems.
>
> **Files:**
> - introduction.md: Why OOP exists — the "5000-line program" problem; how encapsulation, modularity, and abstraction let you change one thing without breaking everything else
> - four-pillars.md: Encapsulation (hide internal state), Inheritance (IS-A relationship, share code), Polymorphism (same interface, different behavior), Abstraction (hide complexity); restaurant kitchen analogy throughout
> - principles.md: IS-A vs HAS-A, composition vs inheritance decision framework, DRY/KISS/YAGNI/Law of Demeter; when to subclass vs. delegate
> - java-oops.md: Java-specific OOP syntax — classes, objects, access modifiers, constructors, static vs instance, interfaces, abstract classes, generics
> - python-oops.md: Python-specific OOP — __init__, @property, @classmethod, multiple inheritance, dunder methods, dataclasses
>
> **Key takeaway:** Prefer composition over inheritance (HAS-A > IS-A) whenever the relationship isn't strictly "is a type of" — over-reliance on inheritance creates tight coupling; delegation/composition keeps classes flexible.

---

# OOP Concepts

Object-oriented programming concepts for low-level design and coding interviews.

## Contents

| Topic | File |
|-------|------|
| **Introduction** | [introduction.md](introduction.md) |
| **Four Pillars** | [four-pillars.md](four-pillars.md) |
| **Principles** | [principles.md](principles.md) |
| **Java OOPs** | [java-oops.md](java-oops.md) |
| **Python OOPs** | [python-oops.md](python-oops.md) |

See also [../SOLID-principles/](../02-solid-principles/) and [../design-patterns/](../03-design-patterns/).
