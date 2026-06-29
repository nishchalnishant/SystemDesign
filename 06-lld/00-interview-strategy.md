---
module: 06-lld
topic: Interview Strategy
status: unread
tags: [06-lld, system-design, interview-strategy]
---
# How to Handle an LLD Interview

This is the process to follow from the moment the problem is stated to when you stop coding. Every section maps to a phase the interviewer is silently evaluating.

---

## The 45-Minute Timeline

```
0–5 min   → Clarify requirements (ask, don't assume)
5–10 min  → Define actors and use cases
10–15 min → Sketch the class diagram (talk while drawing)
15–20 min → Name the design patterns and justify them
20–40 min → Write code for the core flows only
40–45 min → Handle edge cases and concurrency follow-ups
```

Do not skip phases. Jumping straight to code is the most common way to fail an LLD round — you end up solving the wrong problem.

---

## Phase 1: Clarify Requirements (0–5 min)

Ask exactly 3–5 questions. More than that signals you can't prioritize; fewer means you'll build the wrong thing.

**Ask about scope:**
- "Is this a single-machine design or distributed?"
- "How many concurrent users are we expecting?" (changes whether you need locks)
- "Are there multiple types of X?" (e.g., vehicle types in parking lot, room types in hotel)

**Ask about the tricky invariants:**
- "Can a booking be cancelled after confirmation?"
- "What happens if payment fails after a seat is locked?"
- "Can a user have multiple active sessions?"

**Don't ask** things you can assume:
- Don't ask about database schema — this is LLD, not HLD.
- Don't ask about deployment — out of scope.
- Don't ask about UI — assume you're designing the service layer.

**After clarifying, summarize aloud:**
> "So I'll design a parking lot that supports multiple floors, three vehicle types, concurrent entry/exit, and hourly pricing that can be swapped. I'll skip payment processing."

This gives the interviewer a chance to correct you before you build.

---

## Phase 2: Actors and Use Cases (5–10 min)

Name the actors. Then list use cases as short sentences — one line each.

**Format:**
```
Actors: Customer, System, Admin

Use Cases:
- Customer parks a vehicle → system assigns nearest available spot → issues ticket
- Customer exits → system calculates fee → releases spot
- Admin changes pricing strategy → system applies to all new exits
```

Keep this under 5 minutes. You're not writing a PRD — you're building a shared mental model with the interviewer so they know you understand the problem.

**Why this matters:** Interviewers look for whether you can separate what the system does from how it does it. Use cases are the "what." Code is the "how."

---

## Phase 3: Class Diagram (10–15 min)

This is the most important phase. A strong class diagram wins the interview even if your code is incomplete.

### Step 1 — Start from nouns in the use cases

Every noun in your use case list is a candidate class. Circle them:
> Customer **parks** a **Vehicle** → system assigns nearest available **ParkingSpot** → issues **Ticket**

Candidates: `Vehicle`, `ParkingSpot`, `Ticket`. Then ask: does this noun have its own data and behavior? If yes, it's a class.

### Step 2 — Find relationships

Ask three questions for each pair of classes:
- **Owns?** → Composition (filled diamond): `ParkingLot` owns `Level`
- **Uses?** → Association (arrow): `Ticket` references `ParkingSpot`
- **Is a?** → Inheritance (hollow triangle): `Car` is a `Vehicle`

Prefer composition over inheritance. If you're using inheritance, make sure the IS-A relationship holds under every scenario (Liskov).

### Step 3 — Add interfaces at decision points

Anywhere behavior needs to be swappable, extract an interface:
- Pricing can change → `PricingStrategy` interface
- Multiple vehicle types → `Vehicle` abstract class
- Multiple notification channels → `NotificationChannel` interface

### What to draw (verbally, no whiteboard needed):

```
ParkingLot (Singleton)
  └── has-many Level
        └── has-many ParkingSpot
              └── holds Vehicle (abstract)
                    ├── Car
                    ├── Truck
                    └── Motorcycle
ParkingLot uses PricingStrategy (interface)
  ├── HourlyPricingStrategy
  └── FlatRatePricingStrategy
Ticket references ParkingSpot
```

**Talk while you draw.** Say: "I'm extracting `PricingStrategy` here because pricing logic will change and I don't want that change to touch `ParkingLot`." This demonstrates you're applying OCP, not just drawing boxes.

---

## Phase 4: Name Design Patterns (15–20 min)

After the class diagram, explicitly state the patterns you used and why. Don't make the interviewer infer it.

**The formula:** "I'm using [pattern] for [class/interface] because [the specific problem it solves here]."

Examples:
- "I'm using **Singleton** for `ParkingLot` because two instances would have separate spot arrays — they'd double-book the same physical spot."
- "I'm using **Strategy** for `PricingStrategy` because pricing rules change (weekend vs. weekday) and I don't want that change to require editing `ParkingLot`."
- "I'm using **State** for `VendingMachine` because the same action (insert coin) does different things depending on current state — a flag-based approach would be a maze of `if` blocks."
- "I'm using **Observer** for order updates because the restaurant, customer, and analytics system all care about the same state change, and hardcoding three notification calls in one method makes adding a fourth impossible without editing that method."

**Anti-pattern to avoid:** Naming patterns without justifying them. "I used Factory here" with no explanation is a red flag — it signals pattern name-dropping, not understanding.

---

## Phase 5: Write Code — Core Flows Only (20–40 min)

You have 20 minutes. Write the 2–3 methods that demonstrate the core design, not boilerplate getters/setters.

### What to code:

| Priority | Code | Skip |
|----------|------|------|
| Must | The method with the core algorithm (e.g., `findNearestSpot`, `dispense`, `lockSeats`) | Constructors with trivial field assignment |
| Must | The interface + one concrete implementation | All concrete implementations (code one, describe the rest) |
| Must | The critical section if concurrency is involved | Logging, error message formatting |
| Should | The state transition method | Input validation for obvious cases |
| Skip | Getters/setters | toString, hashCode, equals |

### Code quality signals interviewers look for:

**1. Meaningful method names**
Bad: `process()`, `handle()`, `doStuff()`
Good: `findNearestAvailableSpot()`, `lockSeatsForCheckout()`, `transitionTo()`

**2. Small methods with one job**
If a method is more than 20 lines, it's probably doing two things. Extract.

**3. Return types carry meaning**
Instead of returning `boolean` from `bookSeat()`, return a `Booking` object — it carries the booking ID, seat, and timestamp that the caller needs.

**4. Checked vs. unchecked exceptions**
Throw domain exceptions, not generic ones:
- `throw new SeatAlreadyLockedException(seatId)` — not `throw new RuntimeException("seat taken")`
- `throw new InsufficientCashException()` — not `return false`

**5. Concurrency: state it before you code it**
Say: "Two threads could both see `seat.isFree() == true` before either marks it occupied — I'll synchronize the check-and-set."
Then write the `synchronized` block. Never silently add synchronization — explain it.

### Patterns to know cold:

```python
import threading
from abc import ABC, abstractmethod
from enum import Enum

# Thread-safe singleton (double-checked locking)
class ParkingLot:
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls) -> "ParkingLot":
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = cls()
        return cls._instance

# Enum-based state machine with valid transitions
class State(Enum):
    IDLE = "IDLE"
    HAS_ITEM = "HAS_ITEM"
    HAS_MONEY = "HAS_MONEY"

TRANSITIONS: dict[State, set[State]] = {
    State.IDLE:     {State.HAS_ITEM},
    State.HAS_ITEM: {State.IDLE, State.HAS_MONEY},
}

def transition(self, next_state: State) -> None:
    if next_state not in TRANSITIONS[self.state]:
        raise InvalidStateTransitionException(self.state, next_state)
    self.state = next_state

# Strategy via ABC
class PricingStrategy(ABC):
    @abstractmethod
    def calculate(self, duration_minutes: int) -> float: ...

class HourlyPricing(PricingStrategy):
    HOURLY_RATE = 10.0

    def calculate(self, duration_minutes: int) -> float:
        return (duration_minutes / 60.0) * self.HOURLY_RATE
```

---

## Phase 6: Edge Cases and Follow-Ups (40–45 min)

Interviewers always probe at least one of these at the end. Have answers ready.

### Concurrency follow-ups

**"What if two users try to book the same seat simultaneously?"**
→ The `lockSeats()` method synchronizes on a per-show mutex. Both threads enter, one acquires the lock, creates the lock records, releases. The second thread then sees an active non-expired lock and throws `SeatAlreadyLockedException`.

**"What if the server crashes after cash is dispensed but before the account is debited?"**
→ Idempotency key on the debit API call. On restart, replay the transaction log — if the debit wasn't confirmed, re-attempt with the same idempotency key (bank ignores duplicate).

### Extensibility follow-ups

**"How would you add a new vehicle type?"**
→ Add a new `VehicleType` enum value and a new `SpotType`. The `SpotFactory` and `VehicleFactory` produce the new types. No change to `ParkingLot` or `Level` — OCP holds.

**"How would you add a new notification channel (WhatsApp)?"**
→ Implement `NotificationChannel` interface, register it in `NotificationRouter`'s channel map. Zero changes to routing logic.

### Scale follow-ups (if asked)

**"How would you scale this to multiple servers?"**
→ The in-memory state (seat locks, spot occupancy) moves to Redis. Locks use `SET NX PX` (set-if-not-exists with TTL). The service layer becomes stateless — any instance can handle any request.

You don't need to implement this. Naming the pattern is enough.

---

## What Interviewers Are Actually Scoring

| Signal | Green | Red |
|--------|-------|-----|
| Requirements | Asks 3–5 targeted questions, summarizes before designing | Assumes everything OR asks 15 questions |
| Class diagram | Nouns become classes, relationships labeled, interfaces at variation points | God class OR over-engineered with 15 classes for a simple problem |
| Pattern justification | "I use X because without it Y breaks" | "I use X because it's a good pattern" |
| Code | Core algorithm + interface + one concrete impl | All getters/setters, no core logic |
| Concurrency | Identifies the race condition, states the fix, writes the synchronized block | Ignores it entirely OR over-locks everything |
| Edge cases | Proactively mentions 2–3; answers follow-ups clearly | Answers only when directly asked; vague answers |

---

## The One Thing That Separates Good from Great

Good candidates define classes and relationships correctly.

Great candidates **justify every decision as a tradeoff**.

"I'm making `ParkingLot` a Singleton — the tradeoff is it makes unit testing harder because you can't inject a mock. I'd mitigate that by exposing the instance via a `ParkingLotProvider` interface so tests can inject a fake."

That sentence shows: you know the pattern, you know its cost, and you know how to work around the cost. That's SDE-2 level thinking.

---

## Quick Pre-Interview Checklist

Before walking in, make sure you can:

- [ ] Draw class diagrams for Parking Lot, Vending Machine, BookMyShow, ATM from memory in under 10 minutes
- [ ] Explain State vs. Strategy vs. Observer in one sentence each with a concrete example
- [ ] Write thread-safe singleton (double-checked locking) without looking it up
- [ ] Write a `synchronized` critical section for a check-and-set operation
- [ ] Name the tricky part of each Tier 1 problem (see `05-problems/README.md`)
- [ ] Answer "how would you add a new X without changing Y" for each Tier 1 problem

---

## Interviewer Follow-Up Questions

**On clarifying the problem:**
- "You jumped straight into designing classes. What would you do first in a real interview?" → Clarify requirements: (1) Scope — what features are in/out? (2) Constraints — multi-user? concurrent access? persistence? (3) Actors — who uses the system and how? (4) Key operations — CRUD on what entities? Only after establishing scope should you name your first class. Interviewers penalize candidates who design before understanding the problem.
- "Your design handles the happy path. What edge cases should you consider before declaring it complete?" → Resource exhaustion (what happens when capacity is full), concurrent access (two threads modifying the same entity), invalid inputs (negative amounts, null values, out-of-range IDs), lifecycle transitions (can a CANCELLED booking become ACTIVE?), boundary conditions (empty collections, single-element cases). Explicitly enumerate edge cases before coding — shows systematic thinking.

**On trade-offs:**
- "You chose composition over inheritance for this design. What would the inheritance version look like, and why is yours better?" → Describe the inheritance alternative: a `Vehicle` base class with `Car`, `Truck`, `Motorcycle` subclasses. The problem: adding a new dimension (e.g., ElectricVehicle vs GasVehicle) creates a hierarchy explosion (`ElectricCar`, `ElectricTruck`, `GasCar`...). Composition: a `Vehicle` has a `DrivetrainType` strategy injected. New drivetrain = one new class, not N new subclasses. Testable in isolation. This is the answer that demonstrates design maturity.
- "How would you extend this design to support a new requirement X without modifying existing classes?" → Show OCP (Open-Closed Principle) in action: identify the extension point, extract an interface if one doesn't exist, add the new implementation as a new class that implements the interface. Existing code depends on the interface, not the concrete class — no changes required. If the design doesn't have this extension point, acknowledge it and propose a refactor.
