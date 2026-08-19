> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 5 of the LLD process — a signal table mapping the exact phrasing interviewers use in requirements to the design pattern that resolves it, plus the discipline to *not* apply a pattern when it isn't earning its complexity.
>
> **Key ideas:**
> - Patterns aren't chosen because they're impressive — they're chosen because a specific requirement phrase creates a specific structural problem, and the pattern is the named solution to that exact problem.
> - Read the table by requirement phrasing, not by pattern name — you'll rarely think "I should use Observer"; you'll notice "multiple parts of the system need to react when X changes" and the table tells you that's Observer.
> - Every pattern application needs a one-sentence justification tied back to a Step 1 requirement. No justification → don't add the pattern.
> - Most solved LLD problems combine 2-4 patterns, not one. See the mapping table already in [06-lld/README.md](../README.md) for which combinations appear in which solved problems.
>
> **Key takeaway:** If you can't point to the sentence in the requirements that demands a pattern, you're adding accidental complexity, and interviewers penalize that as much as missing a needed pattern.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, design-patterns, decision-tree]
---
# Step 5 — Spotting the Pattern

By this point you have classes ([Step 2](02-nouns-to-classes.md)), relationships ([Step 3](03-relationships-and-uml.md)), and methods with some flagged as needing interfaces ([Step 4](04-verbs-to-methods-and-interfaces.md)). Step 5 names the pattern each flagged seam corresponds to. This is a lookup, not a creative leap — the table below is the lookup.

---

## The signal table

| Requirement phrasing / structural symptom | Pattern | Why it fits | Full reference |
|---|---|---|---|
| "pluggable / configurable algorithm," "different types calculate/behave differently but are interchangeable" | **Strategy** | Encapsulates each algorithm behind one interface, swappable at runtime | [strategy-pattern.md](../03-design-patterns/03-behavioral/strategy-pattern.md) |
| "object behaves differently depending on its current status, with transitions between statuses" | **State** | Each status becomes a class owning its own transition/behavior logic instead of a giant switch | [state-pattern.md](../03-design-patterns/03-behavioral/state-pattern.md) |
| "creating different sub-types of an object based on input," "don't want callers to know concrete classes" | **Factory** (or **Abstract Factory** if creating *families* of related objects) | Centralizes object-creation logic; caller depends on interface, not concrete class | [factory-pattern.md](../03-design-patterns/01-creational/factory-pattern.md), [abstract-factory-pattern.md](../03-design-patterns/01-creational/abstract-factory-pattern.md) |
| "only one instance of X should ever exist," shared global resource/config | **Singleton** | Guarantees single instance + global access point | [singleton.md](../03-design-patterns/01-creational/singleton.md) — pair with [thread-safe-singleton.md](../04-concurrency/thread-safe-singleton.md) if concurrent |
| "object has many optional configuration fields," step-by-step construction | **Builder** | Avoids telescoping constructors; validates at `build()` | [builder-pattern.md](../03-design-patterns/01-creational/builder-pattern.md) |
| "multiple parts of the system need to be notified when X changes," pub/sub, event-driven updates | **Observer** | Decouples the subject from the list of things reacting to it | [observer-pattern.md](../03-design-patterns/03-behavioral/observer-pattern.md) |
| "add behavior to an object dynamically without modifying its class," stacking optional features | **Decorator** | Wraps the object, adding behavior layer by layer | [decorator-pattern.md](../03-design-patterns/02-structural/decorator-pattern.md) |
| "tree structure," "nested/recursive containment" (folders, comments, menus) | **Composite** | Treats individual objects and groups of objects uniformly via a shared interface | [composite-pattern.md](../03-design-patterns/02-structural/composite-pattern.md) |
| "incompatible interfaces need to work together," wrapping a legacy/external API | **Adapter** | Translates one interface into another without modifying either side | [adapter-pattern.md](../03-design-patterns/02-structural/adapter-pattern.md) |
| "simplify a complex subsystem behind one entry point" | **Facade** | One simple interface hides orchestration of several subsystems | [facade-pattern.md](../03-design-patterns/02-structural/facade-pattern.md) |
| "control/restrict access to an object" (lazy-load, permission check, remote proxy) | **Proxy** | Same interface as the real object, adds a control layer in front | [proxy-pattern.md](../03-design-patterns/02-structural/proxy-pattern.md) |
| "undo/redo," "queue or log operations to execute later" | **Command** | Turns a request into an object, so it can be queued, logged, or undone | [command-pattern.md](../03-design-patterns/03-behavioral/command-pattern.md) |
| "a request passes through a sequence of handlers, each deciding to process or forward it" | **Chain of Responsibility** | Decouples sender from receiver; handlers are composable and reorderable | [chain-of-responsibility.md](../03-design-patterns/03-behavioral/chain-of-responsibility.md) |
| "many objects coordinate with each other" causing tangled many-to-many references | **Mediator** | Centralizes communication so objects only talk to the mediator, not each other | [mediator-pattern.md](../03-design-patterns/03-behavioral/mediator-pattern.md) |
| "save/restore an object's previous state" (undo, checkpoints, snapshots) | **Memento** | Captures state externally without breaking encapsulation | [memento-pattern.md](../03-design-patterns/03-behavioral/memento-pattern.md) |
| "same overall algorithm, but a few steps vary by sub-type" | **Template Method** | Base class fixes the skeleton, subclasses override specific steps | [template-method-pattern.md](../03-design-patterns/03-behavioral/template-method-pattern.md) |
| "traverse a collection without exposing its internal structure" | **Iterator** | Standard traversal interface independent of the underlying data structure | [iterator-pattern.md](../03-design-patterns/03-behavioral/iterator-pattern.md) |
| "many near-identical objects, memory is a concern" | **Flyweight** | Shares common immutable state across instances instead of duplicating it | [flyweight-pattern.md](../03-design-patterns/02-structural/flyweight-pattern.md) |
| "operation varies by object type, but you don't want to pollute those classes with every operation" | **Visitor** | Moves the operation into a visitor, dispatched via double-dispatch per type | [visitor-pattern.md](../03-design-patterns/03-behavioral/visitor-pattern.md) |
| "decouple abstraction from implementation so both can vary independently" | **Bridge** | Splits a class hierarchy into an abstraction hierarchy and an implementation hierarchy, connected by composition | [bridge-pattern.md](../03-design-patterns/02-structural/bridge-pattern.md) |
| "expensive object creation should be cloned instead of rebuilt" | **Prototype** | Clone an existing configured instance instead of re-running construction logic | [prototype-pattern.md](../03-design-patterns/01-creational/prototype-pattern.md) |
| "parse/evaluate expressions in a mini grammar" (rare in interviews) | **Interpreter** | Represents grammar rules as a class hierarchy that can be evaluated | [interpreter-pattern.md](../03-design-patterns/03-behavioral/interpreter-pattern.md) |

This table is deliberately organized by **symptom you'll notice**, not alphabetically — read down the left column looking for phrasing that matches your Step 1 requirements or a seam you flagged in Step 4.

---

## The discipline half: don't force-fit

Every pattern in the table above has a real cost — an extra interface, an extra layer of indirection, more files to navigate. That cost is worth paying only when a real requirement demands the flexibility. Before finalizing a pattern, run this check:

```
[Pattern Justification Check — for pattern P you're about to add]
├── Can you point to the specific line in your Step 1 requirements
│   list that needs P's flexibility?
│   ├── NO → Don't add P. A concrete class / plain if-else is correct here.
│   └── YES → continue
├── Is there only one implementation today, with the interviewer saying
│   "might add more later" as the only justification?
│   ├── That's still a valid YES — extensibility requirements are real
│   │   requirements. Add the interface, but keep it to exactly one
│   │   method / one seam — don't speculatively generalize further.
│   └── continue
└── Would removing P make the design simpler with no loss of correctness
    against the stated requirements?
    ├── YES → remove it, you were pattern-matching the name, not the need.
    └── NO → keep it, and state the one-sentence justification out loud.
```

A design with zero patterns because the problem genuinely didn't need any is a *correct* outcome for a simple prompt (e.g., a basic Tic-Tac-Toe needs a clean class model more than it needs Strategy). Don't manufacture a pattern to look sophisticated.

---

## Worked mini-example: ride-sharing patterns

Carrying forward from Steps 1–4:

- **Strategy** on `FareCalculator` — justified by requirement #4, "fare calculation must be pluggable."
- **Singleton**, only if there's a single global `RideMatcher`/dispatch service coordinating all requests — justified if the interviewer confirms a single coordinating instance; otherwise skip it, don't add Singleton reflexively to every "manager" class.
- **Observer**, only if the requirements later add "notify the rider's app in real time as driver location updates" — not justified yet by the current requirements list, so it's *not* added at this stage. Note it as a "would add if requirements expand" instead of building it preemptively.
- No Factory needed — there's no varying sub-type of `Driver` or `Rider` being constructed from input; a plain constructor suffices. Resist the urge to add one anyway.

This is 1 confirmed pattern (Strategy) plus 1 conditionally-justified one (Singleton) plus one explicitly deferred (Observer) — a realistic, defensible output. Compare against how solved problems in `06-problems/` combine patterns via the [table in 06-lld/README.md](../README.md).

---

## Interview Angles

- Justify, don't announce: "I'll use Strategy for fare calculation because requirement 4 said pricing must be pluggable" beats "I'll use the Strategy pattern here."
- If you're unsure between two candidate patterns for the same seam (e.g., State vs. Strategy — both use an interface + interchangeable implementations), the distinguishing question is: does the object *transition between* these implementations based on its own history (State), or does the *caller pick* which one to use (Strategy)? A `Trip`'s status progressing REQUESTED → MATCHED → COMPLETED is State; a rider's chosen fare type is Strategy.
- It's fine — often better — to say "I don't think this needs a pattern here, a plain conditional is clearer" when the justification check fails. Naming the absence of a pattern is itself a signal of judgment.

**Next:** [06-worked-example-end-to-end.md](06-worked-example-end-to-end.md) — see all 5 steps run back-to-back on one fresh problem.
