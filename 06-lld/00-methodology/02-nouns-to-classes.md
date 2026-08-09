> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 2 of the LLD process — mechanically converting your requirements list into candidate classes by underlining nouns, then filtering each one through a 4-way classification.
>
> **Key ideas:**
> - Underline every noun in your requirements list. Each one is a *candidate* — not automatically a class.
> - Classify each candidate as: Entity (has identity + lifecycle), Value Object (immutable, defined by its values), Enum (closed, small set of options), or Attribute (belongs inside another class, isn't its own class).
> - The identity test is the single most useful filter: "do I need to tell two instances apart even if all their fields are equal?" Yes → Entity. No → Value Object.
> - Under-modeling (missing a class) gets caught in Step 3 when a relationship doesn't make sense. Over-modeling (a class for everything) is the more common interview failure — resist it.
>
> **Key takeaway:** Every noun is a suspect, not a verdict. The classification step is what separates a clean 6-8 class diagram from a bloated 20-class one that buries the interview in ceremony.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, uml, class-design]
---
# Step 2 — Nouns to Classes

This is the mechanical trick that makes "imagining the classes" stop feeling like guessing. You're not inventing entities from nothing — you're filtering a list you already produced in Step 1.

---

## The mechanical pass

1. Take your Step 1 requirements list.
2. Underline every noun and noun phrase.
3. Deduplicate synonyms (e.g., "spot" and "space" are the same candidate).
4. Run each surviving candidate through the classification tree below.

```
[Noun Classification]
├── Does it need a unique identity that persists across state changes?
│   ├── YES → Entity (its own class, usually with an ID field)
│   │   Examples: Vehicle, Ticket, Booking, User, Driver, Trip
│   └── NO → continue
├── Is it fully defined by its values, immutable, and interchangeable with
│   any other instance holding the same values?
│   ├── YES → Value Object (own class, no ID, equals() by value)
│   │   Examples: Money, Address, TimeRange, GeoCoordinate
│   └── NO → continue
├── Is it a small, closed, fixed set of named options?
│   ├── YES → Enum
│   │   Examples: VehicleType {CAR, TRUCK, BIKE}, TripStatus {REQUESTED, MATCHED, ...}
│   └── NO → continue
└── Does it only ever appear as a field inside exactly one other class,
    with no independent behavior or lifecycle?
    ├── YES → Attribute, not a class
    │   Examples: "license plate" (a String field on Vehicle), "spot number" (an int on ParkingSpot)
    └── Reconsider — you likely missed a relationship; revisit in Step 3
```

### Entity vs. Value Object — the identity test

Ask: *if I create two instances with identical field values, are they the same thing or two different things?*

- Two `Ticket`s with the same entry time and spot — still two different tickets (different cars used them). → **Entity**.
- Two `Money` objects both representing $50 — completely interchangeable, no reason to distinguish them. → **Value Object**.

Getting this right matters because Entities need an `id` field and identity-based `equals()`; Value Objects should be immutable and compared by value. Interviewers notice when candidates give a `Money` class a database-style ID, or forget an ID on `Booking`.

### Enum vs. class — don't over-build

A common over-engineering trap: turning `VehicleType` into a class hierarchy (`Vehicle` → `Car`, `Truck`, `Bike` subclasses) when a simple `enum VehicleType { CAR, TRUCK, BIKE }` field on `Vehicle` is enough.

Reach for a subclass hierarchy only when the types have **different behavior**, not just a different label. If `Car.calculateFee()` and `Truck.calculateFee()` genuinely differ in logic, subclassing (or Strategy — see [05](05-spotting-the-pattern.md)) earns its keep. If the only difference is "which enum value gets looked up in a rate table," an enum + a map is simpler and is the correct answer, not a shortcut.

---

## Worked mini-example: continuing the ride-sharing requirements

From [01-problem-decomposition.md](01-problem-decomposition.md)'s requirements list, the underlined nouns are: *rider, driver, request, trip, fare, location, status*.

| Candidate | Classification | Why |
|---|---|---|
| Rider | Entity | Has identity across many trips |
| Driver | Entity | Has identity, has a lifecycle (available/busy) |
| Trip | Entity | Needs identity — two trips with identical rider/driver/fare are still distinct trips |
| Fare | Value Object | Fully defined by amount + currency; no identity needed |
| Location | Value Object | Defined by (lat, lng); two identical coordinates are interchangeable |
| TripStatus | Enum | Small closed set: REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED |
| "request" | Not a separate class | It's really `Trip` in the REQUESTED state — don't create a redundant `RideRequest` class unless the interviewer says requests outlive/differ from trips |

Note the last row: the mechanical pass will sometimes surface a noun that turns out to be a state of an existing entity rather than a new class. That's a normal, expected outcome of the filter — not a mistake.

---

## Common mistakes

- **A class per noun, no filtering.** If every underlined word becomes a class, you'll end up with 15+ boxes for a problem that needs 6. Apply the tree.
- **Skipping value objects.** Passing raw `double` for money or raw `(double, double)` for coordinates works but throws away type safety and a natural home for related logic (e.g., `Money.add()`, `Location.distanceTo()`). Small value objects are cheap and read as more senior.
- **Modeling attributes as classes "to be safe."** A `LicensePlate` class is only justified if it has its own validation logic or is shared/compared independently. Otherwise it's a `String` field on `Vehicle` — say so and move on.

---

## Interview Angles

- Say the classification out loud as you go: "Trip needs identity, so that's an entity; Fare is just an amount and currency, so I'll make it a value object." This narrates judgment, which is what's being graded.
- If the interviewer challenges a classification ("why is that an enum and not a class?"), the identity/behavior test above is your answer: "because there's no behavior that differs per type, just a label."
- Aim for 5-8 core entities for a medium problem. More than ~10 by the time you reach Step 3 is a signal to fold some into value objects or attributes.

**Next:** [03-relationships-and-uml.md](03-relationships-and-uml.md) — connect these classes into a diagram.
