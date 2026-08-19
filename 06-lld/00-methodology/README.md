> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The missing layer between "I've read every design pattern" and "I can solve a fresh LLD problem in 45 minutes." A repeatable, mechanical process for turning a plain-English prompt into entities, relationships, a UML class diagram, and a pattern choice — without needing to have seen the problem before.
>
> **The process, in one line:** Nouns → Classes. Verbs → Methods. Constraints → Relationships. Variability → Patterns. Then draw it.
>
> **Learning path (follow this order):**
> - [01 — Problem Decomposition](01-problem-decomposition.md): turn a vague prompt into a requirements list via clarifying questions
> - [02 — Nouns to Classes](02-nouns-to-classes.md): extract entities, value objects, and enums from the requirements
> - [03 — Relationships & UML](03-relationships-and-uml.md): decide association/aggregation/composition/inheritance and draw the diagram
> - [04 — Verbs to Methods & Interfaces](04-verbs-to-methods-and-interfaces.md): assign behavior to classes, spot where an interface is needed
> - [05 — Spotting the Pattern](05-spotting-the-pattern.md): a signal table mapping requirement phrasing to the design pattern that resolves it
> - [06 — Worked Example, End to End](06-worked-example-end-to-end.md): the full process run on a fresh, unseen problem
> - [07 — Interview Playbook](07-interview-playbook.md): time-boxing, what to say out loud, common follow-up questions
> - [08 — Practice Drills](08-practice-drills.md): timed prompts to run the process on yourself, with self-check pointers
>
> **Key takeaway:** You don't need to recognize "this is a Parking Lot problem." You need a process that produces the same class diagram whether or not you've seen the problem before. That process is what this folder teaches.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, uml, design-patterns, tutorial]
---
# LLD Methodology — Learning How to Think, Not What to Memorize

You already have the reference material in this repo: [OOP fundamentals](../01-oop-fundamentals/), [SOLID](../02-solid-principles/), [23 design patterns](../03-design-patterns/), [concurrency patterns](../04-concurrency/), and [36 fully solved problems](../06-problems/). That material answers "what is the Strategy pattern" and "how was Parking Lot solved." It does not answer the harder question:

**Given a problem you've never seen, how do you produce the class diagram yourself?**

That's what this folder is for. It is a process, not a reference — five mechanical steps you run, in order, on any prompt, that reliably produce entities, relationships, a UML diagram, and a pattern choice. The steps don't require creativity or having seen the problem before. They require discipline in applying a checklist.

---

## Why memorizing 36 solutions doesn't transfer

If you've read the 36 problems in `06-problems/` and still freeze on a new prompt, it's because you memorized outputs (Parking Lot uses Singleton + Strategy) instead of the process that generated them. The interviewer's problem will never be exactly Parking Lot. It will be *shaped* like Parking Lot — shared inventory, allocation under constraints, pluggable pricing — and shape is what the process below extracts, regardless of the surface domain (parking spots vs. hotel rooms vs. warehouse bins).

---

## The process (one page version)

```
[LLD Problem → Class Diagram, in 5 steps]
├── Step 1: Decompose the problem
│   ├── Restate scope in your own words
│   ├── Ask 5-7 clarifying questions (see 01-problem-decomposition.md)
│   └── Output → a short written list of functional + non-functional requirements
├── Step 2: Nouns → Classes
│   ├── Underline every noun in the requirements
│   ├── Classify each: Entity (has identity+lifecycle) / Value Object (immutable, no identity) / Enum (closed set) / Not a class (an attribute)
│   └── Output → a list of candidate classes with a one-line responsibility each
├── Step 3: Relationships → UML
│   ├── For every pair of related classes, ask the 4 relationship questions (see 03)
│   ├── Decide: association, aggregation, composition, or inheritance
│   ├── Add cardinality (1, 0..1, 1..*, 0..*)
│   └── Output → a class diagram (boxes + typed arrows)
├── Step 4: Verbs → Methods & Interfaces
│   ├── Underline every verb/action in the requirements
│   ├── Assign each verb to the class that owns the data it needs (Tell, Don't Ask)
│   ├── If a verb has 2+ interchangeable implementations → it wants an interface
│   └── Output → method signatures on each class box
└── Step 5: Spot the pattern
    ├── Match requirement phrasing against the signal table (see 05)
    ├── Confirm the pattern earns its complexity — don't force-fit
    └── Output → 1-3 named patterns with a one-sentence justification each
```

Steps 2 and 3 interleave in practice — you'll often revise your noun list once relationships expose a missing entity (e.g., realizing `Booking` needs to exist once you notice `User` and `Show` have a many-to-many relationship with extra attributes). That's expected; the process is iterative, not strictly linear.

---

## How this folder relates to the rest of `06-lld`

| You are... | Read... |
|---|---|
| Learning OOP vocabulary from scratch | [01-oop-fundamentals](../01-oop-fundamentals/) first, then come back here |
| Comfortable with OOP but can't produce a diagram from a prompt | Start here, at [01-problem-decomposition.md](01-problem-decomposition.md) |
| Mid-design and unsure which pattern fits | Jump straight to [05-spotting-the-pattern.md](05-spotting-the-pattern.md) |
| About to practice a new problem | [08-practice-drills.md](08-practice-drills.md), then self-check against the matching entry in [06-problems](../06-problems/) |
| About to walk into an interview | [07-interview-playbook.md](07-interview-playbook.md) |

Every step doc links out to the specific SOLID principle, pattern doc, or UML notation it depends on — you don't need to pre-read those folders cover to cover. Follow the links when you hit them.

---

## Interview Angles

- Interviewers grade the *process* as much as the output — narrating "I'm underlining nouns to find entities" is itself a signal of seniority.
- The most common failure mode isn't picking the wrong pattern — it's skipping Step 1 and jumping straight to code, then having to redesign mid-interview when a missed constraint surfaces.
- If you can't name why a class exists in one sentence (Step 2's "one-line responsibility"), it's a sign the class is either two classes merged together (SRP violation) or shouldn't exist yet (YAGNI).
