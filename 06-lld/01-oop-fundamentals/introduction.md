# OOP Introduction

Think of software as a city. Early programs were like a single room: everything piled in one place. As systems grew, that room became unmanageable — changing one thing broke something unrelated, and no one could find anything.

Object-oriented programming is city planning for code. Instead of one room, you build **buildings** — each with a clear purpose, its own internal organization, and well-defined entrances and exits. A hospital does not need to know how the power grid works. A bank does not need to know how the hospital schedules surgeries. They communicate through interfaces: "send electricity here," "process this payment." The internals stay hidden.

Each building is a **class**. Each tenant inside — a specific bank branch, a specific hospital — is an **object**. Objects have their own data (state) and their own behavior (methods). They collaborate through contracts, not by reaching into each other's walls and rewiring things directly.

This model gives you three things that matter at scale:

- **Containment**: a change inside one building does not collapse the others.
- **Reuse**: if you have a good hospital design, you build more hospitals from the same blueprint.
- **Extension**: you can add a new wing to the hospital without tearing down and rebuilding it from scratch.

OOP is not magic — it is a discipline. A badly designed city of tangled, interdependent buildings is worse than a small, well-organized room. The four pillars (Encapsulation, Abstraction, Inheritance, Polymorphism) are the zoning rules that keep the city functional as it grows.

---

For full coverage — pillars, IS-A vs HAS-A, DRY/KISS/YAGNI, Law of Demeter, code examples in Java and Python — see [four-pillars.md](four-pillars.md).
