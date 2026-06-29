---
module: 07-interview-templates
status: unread
tags: [07-interview-templates, system-design, lld]
---
# LLD Interview Framework (45-60 min)

Use this when the prompt is "design Parking Lot", "design Splitwise", "design BookMyShow", or any class-level design problem. The goal is to show clean object modeling, extensibility, and correctness under edge cases.

---

## Timeline

| Phase | Time | Output |
|-------|------|--------|
| Clarify scope | 0-5 min | Actors, core use cases, explicit exclusions |
| Identify entities | 5-10 min | Nouns, responsibilities, ownership boundaries |
| Class diagram | 10-20 min | Classes, interfaces, relationships, patterns |
| Core flows | 20-30 min | Main sequence: create, update, query, cancel |
| Code critical path | 30-50 min | Working code for the hardest flow |
| Edge cases | 50-60 min | Concurrency, invalid states, extensibility |

---

## 1. Clarify Scope

Ask 3-5 questions. Keep them tied to design decisions.

```
Who are the actors?
What are the 3 core operations?
Is this single-machine LLD or distributed HLD?
Do we need concurrency safety?
What is out of scope for this round?
```

Example:

```
For BookMyShow, I will support movie search, show selection, seat locking, booking confirmation, and cancellation.
I will keep payment as a strategy interface and focus on seat consistency.
```

---

## 2. Identify Entities

Start from nouns in the use cases, then assign ownership.

| Prompt noun | Candidate class | Owns |
|-------------|----------------|------|
| User | `User` | id, profile, permissions |
| Booking | `Booking` | status, items, timestamps |
| Seat | `Seat` | seat id, row, type |
| Payment | `PaymentStrategy` | payment behavior |
| Inventory | `InventoryService` | availability and locking |

Rules:
- Entities own state.
- Services orchestrate flows.
- Strategies own swappable algorithms.
- Repositories own persistence-like lookup.
- State objects own lifecycle rules.

---

## 3. Draw the Class Diagram

Use this structure:

```
Entities:
  User, Booking, Seat, Show

Enums:
  BookingStatus, SeatStatus, PaymentStatus

Interfaces:
  PaymentStrategy, PricingStrategy, AllocationStrategy

Services:
  BookingService, PaymentService, NotificationService

Repositories:
  ShowRepository, BookingRepository
```

Then state relationships:

```
Booking has many SeatLock objects.
Show has many Seat objects.
BookingService depends on PaymentStrategy, SeatLockManager, BookingRepository.
```

---

## 4. Pick Patterns Deliberately

| Situation | Pattern |
|-----------|---------|
| Different pricing, matching, split, allocation algorithms | Strategy |
| Object lifecycle changes behavior | State |
| Create object by type | Factory |
| Complex object with optional fields | Builder |
| Notify multiple downstream consumers | Observer |
| Add behavior without subclass explosion | Decorator |
| Tree hierarchy | Composite |
| Pipeline of handlers | Chain of Responsibility |
| One shared coordinator | Singleton, but discuss drawbacks |

Do not name patterns without tying them to a failure mode.

---

## 5. Code the Critical Path

Write code for the part where correctness matters most:

| Problem | Critical code |
|---------|---------------|
| Parking Lot | `parkVehicle()` and `unparkVehicle()` |
| Rate Limiter | `allowRequest()` |
| BookMyShow | `lockSeats()` and `confirmBooking()` |
| LRU Cache | `get()` and `put()` |
| Vending Machine | state transitions |
| Splitwise | expense split and balance update |
| Elevator | dispatcher strategy |

Prefer a small complete flow over many incomplete classes.

---

## 6. Concurrency Checklist

Always ask whether concurrent calls are possible.

| Race | Fix |
|------|-----|
| Two users book same seat | Lock per show/seat group; re-check inside lock |
| Two cars take same spot | Lock spot allocation or level inventory |
| Two cache updates corrupt list | Single mutex around map + DLL |
| Producer outruns consumer | Bounded blocking queue |
| Singleton double creation | Double-checked locking or language-level singleton |

Say the invariant clearly:

```
At most one confirmed booking can own a seat for a show.
I enforce that by checking availability and marking locked inside the same critical section.
```

---

## 7. Close Strong

End with:

```
The core design is:
- Entities own state.
- Services orchestrate flows.
- Strategies isolate variable algorithms.
- Locks protect the small critical section where shared state changes.

If we had more time, I would add persistence, retries, metrics, and exhaustive tests.
```

