> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Step 4 of the LLD process — extracting verbs from the requirements list and assigning each to the class that should own it, then deciding when a verb needs an interface instead of a concrete method.
>
> **Key ideas:**
> - Underline every verb/action in the requirements. Each verb becomes a candidate method on exactly one class.
> - Ownership rule: a method belongs to the class that owns the data it primarily reads/mutates ("Tell, Don't Ask" / high cohesion). If a method needs data from two classes equally, it's a sign a third coordinating class (a Service/Manager) is missing.
> - A verb wants an **interface** when the requirements say or imply "this should be swappable/pluggable/vary by type" — that phrasing is the same signal Step 5 uses to pick Strategy, Factory, etc.
> - Keep methods on entities behavioral, not just getters/setters — anemic domain models (all data, no behavior) are a common interview criticism.
>
> **Key takeaway:** Assigning a verb to a class is a cohesion decision, not a naming exercise — the question is always "which object's internal state does this action primarily change?"

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, methods, interfaces, solid]
---
# Step 4 — Verbs to Methods & Interfaces

Nouns gave you boxes ([Step 2](02-nouns-to-classes.md)). Relationships gave you arrows ([Step 3](03-relationships-and-uml.md)). Now the boxes need behavior, or you've drawn a data schema, not an object model.

---

## The mechanical pass

1. Return to your Step 1 requirements list.
2. Underline every verb / action phrase: "assign a spot," "calculate fee," "notify subscribers," "reject a request."
3. For each verb, ask the ownership question below.
4. Write the method as a signature on the owning class: `assignSpot(Vehicle v): ParkingSpot`.

```
[Method Ownership Test — for verb V]
├── Which class's fields does V primarily read or mutate?
│   → Put the method there. (High cohesion: data and the behavior
│     that uses it live together.)
├── Does V need roughly equal data from two unrelated classes,
│   with neither being a natural "owner"?
│   → V belongs on neither. Introduce a coordinating class
│     (a *Service, *Manager, or the pattern's own coordinator —
│     e.g. Mediator, see 05-spotting-the-pattern.md).
│     Example: "match rider to driver" isn't Rider's job or
│     Driver's job — it's a RideMatcher's job.
└── Does V just delegate to a sub-object one level down?
    → Put a thin method on the parent that forwards to the child.
      Example: ParkingLot.parkVehicle() finds a floor and calls
      floor.assignSpot() — ParkingLot doesn't reach into spots directly.
```

This is the same instinct as [Single Responsibility](../02-solid-principles/01-single-responsibility.md) applied at method-placement granularity, and it's what "Tell, Don't Ask" means in practice: `trip.complete()` (tell the Trip to complete itself, it knows its own state) beats `if (trip.getStatus() == IN_PROGRESS) { trip.setStatus(COMPLETED); ... }` written externally.

---

## Avoiding the anemic domain model

A common interview anti-pattern: every class ends up with only getters/setters, and all the actual logic lives in one giant `*Service` or `*Manager` class that pokes at everyone else's internals. This fails SRP at the system level (one class does everything) even if each individual class looks small.

Fix: push behavior down to the entity that owns the relevant state.

- Bad: `ParkingLotService.calculateFee(ticket)` reaches into `ticket.getEntryTime()`, `ticket.getSpot().getType()`, externally.
- Better: `ticket.calculateFee(pricingStrategy)` — the `Ticket` owns entry time and spot reference, so it's the natural owner of the calculation, delegating the *rate* lookup to an injected strategy.

Some coordination logic legitimately belongs in a service (see the second branch of the tree above — cross-entity operations like matching or booking). The failure mode is doing this by default for *everything*, not using it when genuinely warranted.

---

## When a verb wants an interface

A verb signals "give me an interface" when the requirements contain (explicitly or implicitly) phrasing like:

- "different types should calculate/behave differently" → the verb varies by sub-type
- "should be pluggable / configurable / swappable" → the verb varies by runtime choice, not just sub-type
- "we might add more X later" → the verb needs to be extensible without modifying existing code ([Open/Closed](../02-solid-principles/02-open-closed.md))

When you see this, don't write the verb as a concrete method with an `if/else` or `switch` on type — extract an interface with one method, and let each variant implement it. This is Step 5's job to *name* the pattern (usually Strategy or Factory); Step 4's job is just to notice the seam exists.

Example from the ride-sharing requirements: "fare calculation must be pluggable (flat rate now, surge pricing later)" →

```java
interface FareCalculator {
    Money calculate(Trip trip);
}
class FlatFareCalculator implements FareCalculator { ... }
class SurgeFareCalculator implements FareCalculator { ... }
```

`Trip.completeAndCalculateFare(FareCalculator calculator)` takes the interface, not a concrete class — this is [Dependency Inversion](../02-solid-principles/05-dependency-inversion.md) in action: `Trip` depends on the abstraction, not on `FlatFareCalculator` directly.

---

## Worked mini-example: ride-sharing methods

| Verb (from requirements) | Owning class | Method | Interface needed? |
|---|---|---|---|
| "system matches rider to nearest driver" | Neither Rider nor Driver alone — needs both | `RideMatcher.match(Rider, List<Driver>): Driver` | No — matching algorithm is fixed for now per clarifying answers |
| "driver can accept or reject" | Driver (mutates its own availability) | `Driver.accept(Trip)`, `Driver.reject(Trip)` | No |
| "system retries with next-nearest driver" | RideMatcher (coordinates) | `RideMatcher.retry(Trip)` | No |
| "fare calculation must be pluggable" | Trip delegates; FareCalculator computes | `Trip.completeAndCalculateFare(FareCalculator)` | **Yes** — Strategy, see [05](05-spotting-the-pattern.md) |
| "trip has a lifecycle" | Trip (owns its own status) | `Trip.transitionTo(TripStatus)` | Not yet — only if transition *logic* (side effects per state) gets complex; otherwise a plain enum + guard clauses suffice |

---

## Common mistakes

- **God-object services.** A single `RideSharingService` with 20 methods poking every class's internals. Push behavior to the entity that owns the data; reserve service classes for genuine cross-entity coordination.
- **Interface-per-verb overkill.** Not every verb needs an interface — only the ones the requirements flagged as varying. Adding a `Strategy` for a calculation that will never have a second implementation is YAGNI.
- **Getters/setters as the only methods.** If every class is just data + accessors, behavior has leaked into an external service by default rather than by decision — revisit the ownership test.

---

## Interview Angles

- When you place a method, say the ownership reasoning out loud: "fee calculation reads the ticket's entry time and the spot type, so I'll put a `calculateFee` method on Ticket, taking the pricing strategy as a parameter."
- If the interviewer asks "why is this an interface and not just an if/else," point to the specific requirement that said "pluggable" or "may add more later" — that's your evidence, not a stylistic preference.
- Don't extract interfaces preemptively for hypothetical future variation the interviewer never mentioned — over-abstracting reads as over-engineering, not foresight.

**Next:** [05-spotting-the-pattern.md](05-spotting-the-pattern.md) — name the patterns these seams point to.
