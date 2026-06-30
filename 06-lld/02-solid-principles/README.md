> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The SOLID principles — five design principles that make software designs more understandable, flexible, and maintainable. This is the foundation of Low-Level Design (LLD).
>
> **The 5 Principles:**
> - SRP (Single Responsibility): A class should have one, and only one, reason to change (e.g., separate DB logic from business logic).
> - OCP (Open/Closed): Open for extension, closed for modification. Add features by adding new classes, not editing existing ones (via interfaces).
> - LSP (Liskov Substitution): Subclasses must be substitutable for their base classes without breaking correctness (no `UnsupportedOperationException`).
> - ISP (Interface Segregation): Don't force clients to depend on methods they don't use. Split fat interfaces into smaller, specific ones.
> - DIP (Dependency Inversion): High-level modules shouldn't depend on low-level modules; both should depend on abstractions (interfaces). Inject dependencies.
>
> **Key takeaway:** They build on each other. SRP tells you when a class is too big. OCP tells you how to extend it. LSP ensures inheritance is sound. ISP ensures interfaces are clean. DIP wires it all together safely.

---

# SOLID Principles

