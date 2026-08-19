> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 3 of the LLD process — for every pair of related classes from Step 2, deciding the relationship type (association / aggregation / composition / inheritance) and cardinality, then drawing the actual UML class diagram.
>
> **Key ideas:**
> - Four relationship questions, asked in order, for every class pair: (1) Is it IS-A or HAS-A? (2) If HAS-A, does the part's lifecycle depend on the whole? (3) Can the part be shared across multiple wholes? (4) What's the cardinality on each side?
> - Composition (filled diamond) = part dies with whole, not shared. Aggregation (hollow diamond) = part outlives whole, can be shared. Association = a plain "uses/knows about" link, often via a method parameter, no ownership implied.
> - Default to composition or association; reach for inheritance only when LSP holds (see [Liskov Substitution](../02-solid-principles/03-liskov-substitution.md)) — prefer composition over inheritance when in doubt.
> - Draw the diagram as boxes with a 3-part layout (name / fields / methods) connected by typed arrows — the notation itself is documented in [uml-diagrams.md](../01-oop-fundamentals/uml-diagrams.md).
>
> **Key takeaway:** The relationship type isn't a UML trivia question — it dictates real code (does the constructor take the object or create it? does deleting the parent cascade-delete the child?). Get the relationship right and the code follows almost mechanically.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, uml, relationships, class-diagram]
---
# Step 3 — Relationships & UML

You have a list of classified classes from [02-nouns-to-classes.md](02-nouns-to-classes.md). Now you connect them. This step is where "I know what a composition arrow looks like" (declarative knowledge, covered in [uml-diagrams.md](../01-oop-fundamentals/uml-diagrams.md)) turns into "I know which arrow this specific pair needs" (the actual interview skill).

---

## The 4 relationship questions

Run every plausible pair of classes through this in order. Stop at the first "yes."

```
[Relationship Decision Tree — for classes A and B]
├── Q1: Is B fundamentally a specialized type of A — will this remain
│       true for the life of the system, and can B be used anywhere
│       an A is expected (Liskov)?
│   ├── YES → Inheritance (B extends A) or Interface implementation
│   │         Example: Car IS-A Vehicle; CreditCardPayment IS-A PaymentMethod
│   └── NO → continue to Q2
├── Q2: Does A "have" B such that B has no meaning or reason to exist
│       once A is gone — B is created and destroyed with A?
│   ├── YES → Composition (filled diamond, A *-- B)
│   │         Example: ParkingFloor *-- ParkingSpot (a spot doesn't exist
│   │         outside its floor); Order *-- OrderLineItem
│   └── NO → continue to Q3
├── Q3: Does A "have" B, but B has independent lifecycle/meaning and
│       could be shared by multiple As or outlive A?
│   ├── YES → Aggregation (hollow diamond, A o-- B)
│   │         Example: Driver o-- Vehicle (vehicle exists before/after
│   │         this driver relationship; could reassign); Department o-- Employee
│   └── NO → continue to Q4
└── Q4: Does A merely use, reference, or call B — e.g. as a method
        parameter or return type — without owning it at all?
    ├── YES → Association (plain line, or dashed for a transient "uses")
    │         Example: TripMatcher uses Location to compute distance;
    │         PaymentService depends on PaymentGateway
    └── NO → There's probably no direct relationship — don't draw one
```

Full notation reference (arrow styles, multiplicity syntax) lives in [uml-diagrams.md](../01-oop-fundamentals/uml-diagrams.md) — this doc is about *deciding which one applies*, not the drawing syntax itself.

### Why Q2 vs Q3 trips people up

The test isn't "is A's field type B" — both composition and aggregation look identical in code as `private List<B> bs`. The test is **lifecycle and shareability**:

- `ParkingFloor` and `ParkingSpot`: if you delete the floor, the spots are meaningless — they were built into that floor. **Composition.**
- `Driver` and `Vehicle`: a vehicle can exist in the system before a driver is assigned to it, and could be reassigned to a different driver later. **Aggregation.**

If you're unsure, ask: "if I delete the parent object right now, should the child be deleted too?" Yes → composition. No → aggregation.

### Default to composition/association over inheritance

Per [principles.md](../01-oop-fundamentals/principles.md), inheritance is the highest-commitment relationship — it couples subclass to superclass implementation details and is hard to change later. Use it only when Q1's IS-A + substitutability test genuinely holds. When in doubt, prefer composition: give `A` a `B` field and delegate, rather than having `A extends B`.

A concrete smell: if you're about to write `class ElectricCar extends Car` just to override `refuel()` into `recharge()`, and the two don't share meaningful behavior otherwise, you likely want a `FuelStrategy`/`EnergySource` interface (composition) instead of inheritance — see [Strategy pattern](../03-design-patterns/03-behavioral/strategy-pattern.md).

---

## Cardinality

For every relationship, state both sides' multiplicity: `1`, `0..1`, `1..*`, or `0..*`.

- `ParkingFloor "1" *-- "0..*" ParkingSpot` — one floor has zero-or-more spots; a spot belongs to exactly one floor.
- `Rider "1" -- "0..*" Trip` — a rider can have many trips over time; each trip has exactly one rider.
- `Driver "1" -- "0..1" Trip` (active trip only) — a driver has at most one active trip at a time; this constraint is worth calling out verbally since it's a business rule, not just a UML fact.

Cardinality often reveals a missing class: if `Rider` and `Trip` are many-to-many and the relationship itself needs attributes (e.g., a `Booking` needs a `bookedAt` timestamp separate from either side), that's a sign you need an explicit join entity, same as a many-to-many table in a DB schema.

---

## Worked mini-example: ride-sharing diagram

Applying the tree to the entities from [02-nouns-to-classes.md](02-nouns-to-classes.md):

- `Trip` **association** `Rider` (1) and `Trip` **association** `Driver` (0..1) — a trip references both, but doesn't own their lifecycle (riders/drivers exist independently of any one trip).
- `Trip` **composition** `Fare` — a fare is computed for and belongs entirely to one trip; it has no meaning detached from that trip.
- `Trip` **association** `TripStatus` (just a typed field, not really a UML relationship worth drawing as an arrow).
- `Driver` **association** `Location` — driver has a current location value object, replaced over time, not "owned" in a deletion-cascade sense.
- `FareCalculator` **interface**, with `FlatFareCalculator` and `SurgeFareCalculator` **implementing** it — this is inheritance-of-interface (Q1: IS-A "a way of calculating fare"), and it's what Step 5 will name as Strategy.

```
        Rider "1"────────"0..*" Trip "0..1"────────"1" Driver
                            |                            |
                    composition (owns)              association
                            |                    (current location)
                            v                            v
                          Fare                        Location

                     Trip --> uses --> FareCalculator (interface)
                                          ^         ^
                                          |         |
                              FlatFareCalculator  SurgeFareCalculator
```

---

## Common mistakes

- **Drawing inheritance for what's really a role.** "A `Driver` is also a `Rider` sometimes" is not IS-A — it's two independent roles a `User` can hold. Model as composition/association from `User`, not as `Driver extends Rider`.
- **Forgetting the join entity.** Many-to-many relationships with their own data (timestamps, status, amount) need their own class, not a `List<>` on either side.
- **Composition where aggregation belongs.** If deleting the diagram's obvious "owner" class would incorrectly cascade-delete something that should survive (e.g., deleting a `Trip` shouldn't delete the `Driver`), you've mis-picked composition over association/aggregation.

---

## Interview Angles

- Draw the diagram incrementally on the whiteboard/doc as you reason through each pair out loud — don't disappear and present a finished diagram. The reasoning is the signal.
- If asked "why aggregation and not composition here," answer with the lifecycle test verbatim: "because a Vehicle can exist and be reassigned independent of any one Driver."
- Cardinality gaps are a common interviewer probe ("can a driver have two active trips?") — decide and state this during Step 1 clarifying questions if it's ambiguous, not on the fly here.

**Next:** [04-verbs-to-methods-and-interfaces.md](04-verbs-to-methods-and-interfaces.md) — put behavior on these boxes.
