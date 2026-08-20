# Chapter 4 — What's a Design Pattern?

> **Design patterns are typical solutions to commonly occurring problems in software design.**
> They are like pre-made blueprints that you can customize to solve a recurring design
> problem in your code.

You **can't just find a pattern and copy it** into your program the way you can with
off-the-shelf functions or libraries. The pattern is not a specific piece of code, but a
**general concept** for solving a particular problem. You follow the pattern details and
implement a solution that suits the realities of your own program.

## 4.1 Pattern vs. Algorithm

Patterns are often confused with algorithms, because both describe typical solutions to
known problems.

| | Algorithm | Pattern |
|---|---|---|
| Definition | A clear set of actions that achieves a goal | A high-level description of a solution |
| Same problem, two programs | Same code | Code **may be different** |
| Analogy | A **cooking recipe** — clear steps to achieve a goal | A **blueprint** — you see the result and its features, but the exact order of implementation is up to you |

## 4.2 What Does a Pattern Consist Of?

Most patterns are described very formally so people can reproduce them in many contexts.
Sections usually present in a pattern description:

- **Intent** — briefly describes both the problem and the solution.
- **Motivation** — further explains the problem and the solution the pattern makes possible.
- **Structure** of classes — shows each part of the pattern and how they are related.
- **Code example** in one of the popular programming languages — makes it easier to grasp
  the idea behind the pattern.

Some pattern catalogs list other useful details, such as **applicability** of the pattern,
**implementation steps** and **relations with other patterns**.

## 4.3 Classification of Patterns

Design patterns differ by their **complexity, level of detail and scale of applicability**
to the entire system being designed.

> **Analogy — road construction:** you can make an intersection safer by either installing
> some traffic lights or building an entire multi-level interchange with underground passages
> for pedestrians.

**By scale:**

| Level | Name | Description |
|---|---|---|
| Lowest | **Idioms** | The most basic and low-level patterns. Usually apply only to a **single programming language**. |
| Middle | **Design patterns** | The subject of this book. |
| Highest | **Architectural patterns** | The most universal and high-level. Implementable in virtually any language. Unlike other patterns, they can be used to design the architecture of an **entire application**. |

**By intent / purpose** — the three main groups covered in this book:

| Group | Purpose |
|---|---|
| **Creational patterns** | Provide object creation mechanisms that increase flexibility and reuse of existing code. |
| **Structural patterns** | Explain how to assemble objects and classes into larger structures, while keeping the structures flexible and efficient. |
| **Behavioral patterns** | Take care of effective communication and the assignment of responsibilities between objects. |

## 4.4 Who Invented Patterns?

That's a good, but not a very accurate, question. Design patterns aren't obscure,
sophisticated concepts — quite the opposite. Patterns are typical solutions to common
problems in object-oriented design. **When a solution gets repeated over and over in various
projects, someone eventually puts a name to it and describes the solution in detail.**
That's basically how a pattern gets *discovered*.

**Timeline:**

1. **Christopher Alexander** first described the concept of patterns in
   *A Pattern Language: Towns, Buildings, Construction*. The book describes a "language" for
   designing the urban environment. The units of this language are patterns: how high windows
   should be, how many levels a building should have, how large green areas in a neighborhood
   should be, and so on.
2. **1994 — the "Gang of Four"**: Erich Gamma, John Vlissides, Ralph Johnson, and Richard Helm
   published *Design Patterns: Elements of Reusable Object-Oriented Software*, applying the
   concept of design patterns to programming. The book featured **23 patterns** solving various
   problems of object-oriented design and became a best-seller very quickly. Due to its lengthy
   name, people started calling it "the book by the gang of four", soon shortened to
   **"the GoF book"**.
3. **Since then**, dozens of other object-oriented patterns have been discovered. The
   "pattern approach" became very popular in other programming fields, so lots of other
   patterns now exist outside of object-oriented design as well.
