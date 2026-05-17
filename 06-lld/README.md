# Low-Level Design (LLD) Interview Problems

23 LLD problems ranked by interview frequency. But before you jump to problems, the learning path below matters — design patterns only click when you understand what problem they solve.

## Learning Path (follow this order)

**Step 1 — OOP fundamentals** (2–3 days, read before anything else)
- [four-pillars.md](01-oop-fundamentals/four-pillars.md) — Encapsulation, Inheritance, Polymorphism, Abstraction with the restaurant kitchen analogy
- [principles.md](01-oop-fundamentals/principles.md) — IS-A vs. HAS-A, composition vs. inheritance; this determines when to subclass vs. delegate
- [introduction.md](01-oop-fundamentals/introduction.md) — Why OOP exists and the problems it solves

**Step 2 — SOLID Principles** (1 week, read in order — each builds on the previous)
- [Single Responsibility](02-solid-principles/1.%20single-responsibility-principle.md) → [Open/Closed](02-solid-principles/2.%20open-closed-principle.md) → [Liskov Substitution](02-solid-principles/3.%20liskov-substitution-principle.md) → [Interface Segregation](02-solid-principles/4.%20interface-segregation-principle.md) → [Dependency Inversion](02-solid-principles/5.%20dependency-inversion-principle.md)

SRP tells you when a class is doing too much. OCP tells you how to extend it without breaking it. LSP tells you how inheritance must behave. ISP tells you how to split interfaces. DIP tells you how to wire them together. Each one solves a failure mode the previous one doesn't address.

**Step 3 — Design Patterns** (don't read all 16 at once — pair with problems)

| Pattern | Read Before | Problem |
|---------|-------------|---------|
| [Singleton](03-design-patterns/01-creational/singleton.md) | Parking Lot, Logger | Shared state (ParkingLot instance, Logger instance) |
| [Factory](03-design-patterns/01-creational/factory-pattern.md) | Parking Lot, Hotel Mgmt | Creating different Vehicle/Room types |
| [Builder](03-design-patterns/01-creational/builder-pattern.md) | Coupon System | Complex object construction |
| [Strategy](03-design-patterns/03-behavioral/strategy-pattern.md) | Parking Lot, Splitwise | Swappable algorithms (pricing, split calculation) |
| [State](03-design-patterns/03-behavioral/state-pattern.md) | Vending Machine, Elevator | Object behavior changes with state |
| [Observer](03-design-patterns/03-behavioral/observer-pattern.md) | Snake & Ladder, Notification | Event-driven updates |
| [Decorator](03-design-patterns/02-structural/decorator-pattern.md) | Rate Limiter, Logger | Wrapping behavior dynamically |
| [Composite](03-design-patterns/02-structural/composite-pattern.md) | S3, Coupon System | Tree structures, recursive operations |
| [Command](03-design-patterns/03-behavioral/command-pattern.md) | Text Editor | Encapsulate operations, enable undo |
| [Chain of Responsibility](03-design-patterns/03-behavioral/chain-of-responsibility.md) | Logger, Coupon System | Sequential handler chain |

**Step 4 — Concurrency** (before Tier 2 problems)
- [producer-consumer.md](04-concurrency/producer-consumer.md)
- [thread-safe-singleton.md](04-concurrency/thread-safe-singleton.md)

**Step 5 — Problems** (Tier 1 → Tier 2 → Tier 3)

> **Legend**:
> - **Tier 1** (80-100%): Must implement from scratch 3+ times. These appear in almost every LLD round.
> - **Tier 2** (50-80%): Understand the class diagram and key pattern. Know the tricky part cold.
> - **Tier 3** (10-50%): Read for the specific algorithm. Don't memorize — understand the approach.

---

## Tier 1 — Implement from scratch (3+ times each)

| Rank | Problem | Freq | Key Patterns | The Tricky Part |
|------|---------|------|--------------|-----------------|
| 1 | [Design Parking Lot](05-problems/1-design-parking-lot.md) | 95% | Singleton, Factory, Strategy | Thread-safe spot allocation across floors |
| 2 | [Design Rate Limiter](05-problems/2-design-rate-limiter.md) | 90% | Token Bucket, Decorator | Thread-safe counter without synchronization bottleneck |
| 3 | [Design Tic-Tac-Toe](05-problems/3-design-tic-tac-toe.md) | 85% | Game Loop | Win detection in O(1) using row/col/diag counters |
| 4 | [Design Vending Machine](05-problems/4-design-vending-machine.md) | 85% | State Pattern | State transitions are exhaustive — draw the FSM first |
| 5 | [Design Splitwise](05-problems/5-design-splitwise.md) | 80% | Strategy, Graph | Debt simplification: greedy min-cash-flow algorithm |

---

## Tier 2 — Understand class diagram + key pattern cold

| Rank | Problem | Freq | Key Patterns | The Tricky Part |
|------|---------|------|--------------|-----------------|
| 6 | [Design Snake & Ladder](05-problems/6-design-snake-and-ladder.md) | 75% | Observer, Strategy | Board is a graph; snakes/ladders are edges |
| 7 | [Design Elevator System](05-problems/7-design-elevator-system.md) | 75% | State Pattern | SCAN/LOOK scheduling; request batching by direction |
| 8 | [Design Comment System](05-problems/8-design-comment-system.md) | 70% | Composite | Nested comments as a tree; materialized path for DB |
| 9 | [Design Hotel Management](05-problems/9-design-hotel-management.md) | 70% | Factory, Singleton | Concurrency on booking: optimistic lock on room row |
| 10 | [Design LRU Cache](05-problems/10-design-lru-cache.md) | 65% | LinkedHashMap / DLL+HashMap | O(1) get AND put requires doubly-linked list + HashMap together |
| 11 | [Design Locker Service](05-problems/11-design-locker-service.md) | 65% | Inheritance | Locker size matching; geohash for nearest-locker lookup |
| 12 | [Design Coupon System](05-problems/12-design-coupon-system.md) | 60% | Composite, Chain of Responsibility | Stacking rules: which coupons combine, which are exclusive |
| 13 | [Design Mentorship Platform](05-problems/13-design-mentorship-platform.md) | 55% | Factory | Booking conflict detection: interval overlap check |
| 14 | [Design Logger Library](05-problems/14-design-logger-library.md) | 50% | Chain of Responsibility, Singleton | Log levels as a chain; each handler decides pass-through |
| 15 | [Design Minesweeper](05-problems/15-design-minesweeper.md) | 45% | Flood Fill | BFS from revealed cell; stop at cells adjacent to mines |

---

## Tier 3 — Read for the specific algorithm

| Rank | Problem | Freq | Key Algorithm | Why It Matters |
|------|---------|------|---------------|----------------|
| 16 | [Design S3 Object Storage](05-problems/16-design-s3-object-storage.md) | 40% | Composite Pattern | File system as tree; metadata vs. data separation |
| 17 | [Design Search Engine](05-problems/17-design-search-engine.md) | 35% | Inverted Index, Trie | Tokenization pipeline; prefix search vs. full-text search |
| 18 | [Design Tetris](05-problems/18-design-tetris.md) | 30% | Matrix rotation, Factory | Rotation as 90° transpose + reverse; piece factory |
| 19 | [Design Version Control](05-problems/19-design-version-control.md) | 25% | DAG, SHA-1 hashing | Commits as a DAG; content-addressed storage via hash |
| 20 | [Design Tunneling Service](05-problems/20-design-tunneling-service.md) | 20% | Reverse Proxy, Sockets | Bidirectional socket relay; port multiplexing |
| 21 | [Design Text Editor](05-problems/21-design-text-editor.md) | 20% | Gap Buffer, Command | Gap Buffer for O(1) insert/delete at cursor; Command for undo |
| 22 | [Design Download Manager](05-problems/22-design-download-manager.md) | 15% | HTTP Range, multi-threading | Parallel chunk download; merge and verify checksum |
| 23 | [Design Unlock Pattern](05-problems/23-design-unlock-pattern.md) | 10% | DFS/Backtracking | Validate knight-move jumps; required intermediate points |

---

## Cross-References

- Rate Limiter LLD ↔ [02-building-blocks/rate-limiting.md](../02-building-blocks/rate-limiting.md) ↔ [HLD Rate Limiter](../05-hld-problems/01-easy/rate-limiter.md)
- LRU Cache LLD ↔ [02-building-blocks/caching-layer.md](../02-building-blocks/caching-layer.md) ↔ [HLD Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md)
- S3 LLD ↔ [HLD Google Drive](../05-hld-problems/03-hard/google-drive.md) (chunking, dedup)
- Search Engine LLD ↔ [HLD Search System](../05-hld-problems/03-hard/search-system.md) (inverted index at scale)
