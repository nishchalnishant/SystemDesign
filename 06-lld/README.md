# Low-Level Design (LLD) Interview Prep

Focused LLD prep for Amazon SDE-2. The goal is not to memorize every pattern; it is to model clean classes, explain trade-offs, and code the correctness-critical flow.

## Interview Approach

Start with [00-interview-strategy.md](00-interview-strategy.md), then use [../07-interview-templates/lld-template.md](../07-interview-templates/lld-template.md) and [../07-interview-templates/lld-cheat-sheet.md](../07-interview-templates/lld-cheat-sheet.md) for timed practice.

---

## Learning Path

### Step 1 — OOP Fundamentals

- [OOP Introduction](01-oop-fundamentals/introduction.md)
- [Four Pillars of OOP](01-oop-fundamentals/four-pillars.md)
- [Python OOPs](01-oop-fundamentals/python-oops.md)

Focus on encapsulation, abstraction, inheritance vs composition, and polymorphism. For SDE-2 LLD, composition and clear ownership matter more than deep inheritance trees.

### Step 2 — SOLID Principles

Read in order:

1. [Single Responsibility](02-solid-principles/1.%20single-responsibility-principle.md)
2. [Open/Closed](02-solid-principles/2.%20open-closed-principle.md)
3. [Liskov Substitution](02-solid-principles/3.%20liskov-substitution-principle.md)
4. [Interface Segregation](02-solid-principles/4.%20interface-segregation-principle.md)
5. [Dependency Inversion](02-solid-principles/5.%20dependency-inversion-principle.md)

### Step 3 — Design Patterns

Read patterns only when they connect to a problem.

| Pattern | Read Before | Why |
|---------|-------------|-----|
| [Singleton](03-design-patterns/01-creational/singleton.md) | Parking Lot, Logger | shared coordinator or logger |
| [Factory](03-design-patterns/01-creational/factory-pattern.md) | Parking Lot, Hotel | create different domain types |
| [Builder](03-design-patterns/01-creational/builder-pattern.md) | Coupon System | complex object construction |
| [Strategy](03-design-patterns/03-behavioral/strategy-pattern.md) | Parking Lot, Splitwise, Food Delivery | swappable algorithms |
| [State](03-design-patterns/03-behavioral/state-pattern.md) | Vending Machine, Elevator, ATM | lifecycle-specific behavior |
| [Observer](03-design-patterns/03-behavioral/observer-pattern.md) | Notification, Food Delivery | event fan-out |
| [Decorator](03-design-patterns/02-structural/decorator-pattern.md) | Rate Limiter, Logger | wrapping behavior |
| [Composite](03-design-patterns/02-structural/composite-pattern.md) | Comment System, Coupon System | trees and nested rules |
| [Command](03-design-patterns/03-behavioral/command-pattern.md) | Tic-Tac-Toe | actions as objects |
| [Chain of Responsibility](03-design-patterns/03-behavioral/chain-of-responsibility-pattern.md) | Logger, Coupon System, ATM | ordered handler pipeline |

### Step 4 — Concurrency

- [Locks and Semaphores](04-concurrency/locks-semaphores.md)
- [Producer-Consumer](04-concurrency/producer-consumer.md)
- [Thread-Safe Singleton](04-concurrency/thread-safe-singleton.md)

Know how to protect small critical sections: seat booking, parking spot allocation, LRU cache map/list updates, and logger queues.

### Step 5 — Problems

Practice Tier 1 from scratch. For Tier 2 and Tier 3, be able to draw the class diagram and explain the tricky part.

---

## Tier 1 — Implement From Scratch

| Rank | Problem | Key Patterns | The Tricky Part |
|------|---------|--------------|-----------------|
| 1 | [Design Parking Lot](05-problems/1-design-parking-lot.md) | Singleton, Factory, Strategy | thread-safe spot allocation |
| 2 | [Design Rate Limiter](05-problems/2-design-rate-limiter.md) | Token Bucket, Decorator | atomic counter update |
| 3 | [Design Tic-Tac-Toe](05-problems/3-design-tic-tac-toe.md) | Command, Game Loop | O(1) win detection |
| 4 | [Design Vending Machine](05-problems/4-design-vending-machine.md) | State | exhaustive state transitions |
| 5 | [Design Splitwise](05-problems/5-design-splitwise.md) | Strategy, Graph | balance simplification |
| 6 | [Design BookMyShow](05-problems/13-design-bookmyshow.md) | Factory, Strategy, Locks | seat locking and expiry |
| 7 | [Design LRU Cache](05-problems/10-design-lru-cache.md) | DLL + HashMap | O(1) get and put |
| 8 | [Design ATM](05-problems/18-design-atm.md) | State, Chain of Responsibility | cash withdrawal pipeline |

## Tier 2 — Know Class Diagram + Core Flow

| Rank | Problem | Key Patterns | The Tricky Part |
|------|---------|--------------|-----------------|
| 9 | [Design Snake & Ladder](05-problems/6-design-snake-and-ladder.md) | Strategy, Observer | board as graph |
| 10 | [Design Elevator System](05-problems/7-design-elevator-system.md) | State, Strategy | request scheduling |
| 11 | [Design Hotel Management](05-problems/9-design-hotel-management.md) | Factory, Strategy | date overlap and room locking |
| 12 | [Design Library Management](05-problems/15-design-library-management.md) | Strategy | inventory, reservations, fines |
| 13 | [Design Food Delivery](05-problems/16-design-food-delivery.md) | Strategy, Observer, State | order lifecycle and assignment |
| 14 | [Design Notification System](05-problems/17-design-notification-system.md) | Observer, Strategy, Decorator | channel routing and rate limits |
| 15 | [Design Logger Library](05-problems/14-design-logger-library.md) | Chain, Singleton, Queue | async logging without blocking callers |

## Tier 3 — Read for Specific Techniques

| Rank | Problem | Technique |
|------|---------|-----------|
| 16 | [Design Comment System](05-problems/8-design-comment-system.md) | nested trees, materialized path |
| 17 | [Design Locker Service](05-problems/11-design-locker-service.md) | size matching, nearest locker lookup |
| 18 | [Design Coupon System](05-problems/12-design-coupon-system.md) | Composite rules and validation chain |

---

## Cross-References

- Rate Limiter LLD -> [rate-limiting.md](../02-building-blocks/rate-limiting.md) -> [HLD Rate Limiter](../05-hld-problems/01-easy/rate-limiter.md)
- LRU Cache LLD -> [caching-layer.md](../02-building-blocks/caching-layer.md) -> [HLD Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md)
- BookMyShow LLD -> [HLD Ticketmaster Seat Booking](../05-hld-problems/03-hard/ticketmaster-seat-booking.md)
- Logger/Notification LLD -> [message-brokers.md](../02-building-blocks/message-brokers.md) -> [HLD Notification Service](../05-hld-problems/02-medium/notification-service.md)
