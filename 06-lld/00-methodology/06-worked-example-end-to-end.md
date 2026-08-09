> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** All 5 methodology steps run back-to-back on one fresh prompt — "Design a Gym Membership & Class Booking System" — a problem not already solved elsewhere in this repo, so you can follow the reasoning without recalling a memorized answer.
>
> **Key ideas:**
> - This doc is meant to be read *after* Steps 1-5, as proof the process produces a real design mechanically, not as a shortcut to skip the individual step docs.
> - Every artifact shown (requirements list, class table, diagram, method table, pattern list) is the literal deliverable each step doc says to produce.
> - Cover the final diagram and try to redo Steps 2-5 yourself from just the Step 1 requirements list before reading further — that's the actual practice rep.
>
> **Key takeaway:** Nothing in this design was pattern-matched from memory. Every decision traces back to one line in the Step 1 requirements list — that traceability is what makes the process work on a problem you've genuinely never seen.

---
module: 06-lld
topic: Methodology
status: unread
tags: [06-lld, methodology, worked-example, uml]
---
# Step 6 — Worked Example, End to End

**Prompt:** "Design a gym membership and class booking system. Members can hold a membership, book into scheduled group classes (yoga, spin, etc.) up to capacity, and cancel a booking. The gym wants to experiment with different cancellation-fee policies over time."

Try Steps 2–5 yourself from the requirements list below before reading past it.

---

## Step 1 — Problem Decomposition

Clarifying questions and answers:

- Q: "One gym location or a chain?" → A: "One location for now."
- Q: "What membership tiers exist?" → A: "Basic and Premium; Premium gets priority booking when a class is full."
- Q: "What happens when a class is at capacity?" → A: "Basic members go on a waitlist; Premium members can bump the last waitlisted Basic member... actually, keep it simple: just waitlist everyone, FIFO, no bumping."
- Q: "How does cancellation-fee policy vary?" → A: "Free if cancelled >24h before class; a flat fee otherwise. But we want to swap this logic later without touching booking code."
- Q: "Who triggers waitlist promotion when a spot opens?" → A: "The system automatically promotes the next waitlisted member when someone cancels."
- Q: "Concurrency — can two members book the last spot simultaneously?" → A: "Yes, assume concurrent booking attempts on the same class."
- Q: "Out of scope?" → A: "Payments processing itself, just fee *calculation*. No trainer scheduling."

**Requirements list:**
1. Member holds exactly one Membership (Basic or Premium tier).
2. GymClass has a schedule (time slot) and a fixed capacity.
3. Member books into a GymClass; booking fails/waitlists if at capacity.
4. If at capacity, member is placed on a FIFO waitlist, regardless of tier (simplified).
5. Member can cancel a booking.
6. On cancellation, the next waitlisted member is automatically promoted to a confirmed booking.
7. Cancellation fee is calculated by a policy that must be swappable without modifying booking code.
8. Concurrent booking attempts on the same class must not overbook capacity.
9. Out of scope: payment processing, trainer scheduling.

---

## Step 2 — Nouns to Classes

Underlined nouns: *member, membership, tier, class (GymClass), schedule/time slot, capacity, booking, waitlist, cancellation fee, policy*.

| Candidate | Classification | Why |
|---|---|---|
| Member | Entity | Identity persists across many bookings |
| Membership | Value Object (arguably Entity — see note) | Defined by tier + validity dates; treated as Entity here since it has its own lifecycle (renew, expire) independent of being "just data" |
| MembershipTier | Enum | Closed set: BASIC, PREMIUM |
| GymClass | Entity | Identity — two classes at the same time slot with the same name are still distinct sessions |
| TimeSlot | Value Object | Defined fully by start/end time; interchangeable if equal |
| Booking | Entity | Needs identity — join entity between Member and GymClass, carries its own status and timestamp (per the Step 3 many-to-many rule) |
| BookingStatus | Enum | CONFIRMED, WAITLISTED, CANCELLED |
| CancellationFeePolicy | Interface (behavior varies — flagged in Step 4) | "must be swappable" is the direct signal |
| Waitlist | Not a separate class | It's a `Queue<Booking>` owned by `GymClass`, not an independent entity with its own identity |

---

## Step 3 — Relationships & UML

- `Member` **association** `Membership` (1) — a member has one membership; membership could arguably be swapped/renewed independent of the member record, so association rather than tight composition.
- `Booking` **composition** relationship from both `Member` and `GymClass` conceptually, but structurally: `Booking` **association** `Member` (many bookings reference one member) and `Booking` **association** `GymClass` (many bookings reference one class) — this is the join-entity pattern flagged in Step 3's methodology (many-to-many with its own attributes: status, timestamp).
- `GymClass` **composition** `TimeSlot` — a time slot has no meaning outside its class session.
- `GymClass` **aggregation** `Booking` (0..capacity confirmed + 0..* waitlisted) — bookings reference the class but the class doesn't "own" them in a strict deletion-cascade sense; cancelling a booking shouldn't be structurally impossible if implemented as aggregation vs composition — either is defensible, state your reasoning.
- `Booking` **association** `CancellationFeePolicy` — used at cancellation time, not owned.

```
Member "1"────"1" Membership "1"────"1" MembershipTier(enum)
  |
  | "1"
  |
Booking "0..*"────"1" GymClass "1"────"1" TimeSlot
  |
  | uses (association)
  v
CancellationFeePolicy (interface)
      ^                    ^
      |                    |
FreeWithinWindowPolicy   FlatFeePolicy
```

---

## Step 4 — Verbs to Methods & Interfaces

| Verb | Owning class | Method | Interface? |
|---|---|---|---|
| "member books into a class" | Neither alone — needs GymClass's capacity/waitlist state and creates a Booking | `GymClass.book(Member): Booking` (GymClass owns capacity, so it decides confirmed vs. waitlisted) | No |
| "booking fails/waitlists if at capacity" | GymClass (owns capacity + waitlist) | same `book()` method, internal branch | No |
| "member cancels a booking" | Booking (owns its own status) | `Booking.cancel(CancellationFeePolicy): Money` | No |
| "next waitlisted member is automatically promoted" | GymClass (owns the waitlist queue) | `GymClass.promoteNextWaitlisted()`, called from within `Booking.cancel()` or by GymClass after removing a confirmed booking | No |
| "cancellation fee calculated by a policy, must be swappable" | Delegated to policy | `CancellationFeePolicy.calculateFee(Booking): Money` | **Yes** — flagged directly by requirement 7 |
| "concurrent booking must not overbook" | GymClass (guards its own capacity mutation) | `book()` synchronizes on the class's internal spot counter / uses a concurrent-safe structure | No (concurrency mechanism, not a new interface — see [concurrency-patterns.md](../04-concurrency/concurrency-patterns.md)) |

---

## Step 5 — Spotting the Pattern

Running the signal table from [05-spotting-the-pattern.md](05-spotting-the-pattern.md):

- **Strategy** on `CancellationFeePolicy` — direct match: "must be swappable without modifying booking code" is the canonical Strategy signal. Justification traces to requirement 7.
- **Factory**, considered for creating `Booking` objects — **rejected** by the justification check: there's only one kind of `Booking`, construction is trivial, no varying sub-type. Adding a `BookingFactory` here would fail the "can you point to the requirement that needs it" test.
- **Observer**, considered for waitlist promotion ("automatically promoted") — **borderline**. If promotion is just one direct method call (`GymClass.promoteNextWaitlisted()` invoked from `cancel()`), a plain method call is simpler and sufficient. Observer would be justified only if *multiple, independent* parts of the system (e.g., a notification service AND a billing service AND an analytics logger) all need to react to the same cancellation event without `Booking` knowing about any of them. Since requirement 6 only describes one reaction, skip Observer — note it as "would upgrade to Observer if more listeners are added."
- **Thread-safety mechanism** (not a GoF pattern, but the concurrency layer from requirement 8) — a synchronized method or `AtomicInteger` spot counter on `GymClass`, following [concurrency-patterns.md](../04-concurrency/concurrency-patterns.md) and the same shared-counter problem discussed in [thread-safe-singleton.md](../04-concurrency/thread-safe-singleton.md).

**Final pattern list:** Strategy (confirmed, requirement 7) + a concurrency-safe capacity guard (requirement 8). Factory and Observer both explicitly considered and rejected with reasons — that rejection is worth stating out loud in an interview, it demonstrates the same judgment as picking a pattern correctly.

---

## What this demonstrates

Six classes, one interface, one pattern, one concurrency mechanism — produced entirely by running the requirements list through four mechanical filters. Nothing here required having seen a "gym booking" problem before; it required underlining nouns and verbs and asking the same four relationship questions and one pattern-justification check every time.

**Next:** [07-interview-playbook.md](07-interview-playbook.md) — how to run this process live, under a clock, while narrating it.
