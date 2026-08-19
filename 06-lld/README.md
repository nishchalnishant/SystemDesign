> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Complete Low-Level Design (LLD) interview preparation — OOP fundamentals, SOLID principles, 16 design patterns, concurrency, and 44 LLD problems with full class diagrams and code.
>
> **Learning path (follow this order):**
> - Step 0 — Methodology (start here if you know the reference material but can't originate a design): the process for turning a fresh prompt into classes, UML, and pattern choices
> - Step 1 — OOP Fundamentals (2–3 days): four-pillars.md, principles.md, introduction.md; choose Java or Python for code examples
> - Step 2 — SOLID Principles (1 week): SRP → OCP → LSP → ISP → DIP in order; each builds on the previous
> - Step 3 — Design Patterns (pair with problems): 16 patterns across Creational, Structural, Behavioral; read pattern when problem needs it
> - Step 4 — Concurrency Patterns: thread-safe singleton, producer-consumer, futures; needed for concurrent LLD problems
> - Step 5 — Architecture & Data: clean architecture, API design, database schema design for LLD
> - Step 6 — LLD Problems (44 problems): Parking Lot → Rate Limiter → Tic-Tac-Toe → Vending Machine → Splitwise → BookMyShow etc.
>
> **Key takeaway:** Don't read all patterns upfront — pair each pattern with the problem that needs it; the README maps patterns to problems; the LLD interview is about translating requirements into clean class hierarchies.

---

# Low-Level Design (LLD) Interview Problems

23 LLD problems ranked by interview frequency. But before you jump to problems, the learning path below matters — design patterns only click when you understand what problem they solve.

## Learning Path (follow this order)

**Step 0 — Methodology** (read first if you already know the reference material but can't originate a design from scratch)
- [00-methodology/README.md](00-methodology/README.md) — the 5-step process (decompose → nouns to classes → relationships/UML → verbs to methods/interfaces → spot the pattern), a full worked example not found elsewhere in this repo, a 45-minute interview playbook, and timed practice drills

**Step 1 — OOP fundamentals** (2–3 days, read before anything else)
- [four-pillars.md](01-oop-fundamentals/four-pillars.md) — Encapsulation, Inheritance, Polymorphism, Abstraction with the restaurant kitchen analogy
- [principles.md](01-oop-fundamentals/principles.md) — IS-A vs. HAS-A, composition vs. inheritance; this determines when to subclass vs. delegate
- [introduction.md](01-oop-fundamentals/introduction.md) — Why OOP exists and the problems it solves

**Step 2 — SOLID Principles** (1 week, read in order — each builds on the previous)
- [02-solid-principles/README.md](02-solid-principles/README.md) — SRP → OCP → LSP → ISP → DIP, each with the rule, the code smell, and the fix

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

**Step 5 — Architecture and Data** (Bridging LLD and HLD)
- [Clean Architecture](05-architecture-and-data/01-clean-architecture.md)
- [API Design for LLD](05-architecture-and-data/02-api-design-for-lld.md)
- [Database Schema Design for LLD](05-architecture-and-data/03-database-schema-design.md)

**Step 6 — Problems** (Tier 1 → Tier 2 → Tier 3)

> **Legend**:
> - **Tier 1** (80-100%): Must implement from scratch 3+ times. These appear in almost every LLD round.
> - **Tier 2** (50-80%): Understand the class diagram and key pattern. Know the tricky part cold.
> - **Tier 3** (10-50%): Read for the specific algorithm. Don't memorize — understand the approach.

---

## Tier 1 — Implement from scratch (3+ times each)

| Rank | Problem | Freq | Key Patterns | The Tricky Part |
|------|---------|------|--------------|-----------------|
| 1 | [Design Parking Lot](06-problems/01-core-problems/01-design-parking-lot.md) | 95% | Singleton, Factory, Strategy | Thread-safe spot allocation across floors |
| 2 | [Design Rate Limiter](06-problems/01-core-problems/02-design-rate-limiter.md) | 90% | Token Bucket, Decorator | Thread-safe counter without synchronization bottleneck |
| 3 | [Design Tic-Tac-Toe](06-problems/01-core-problems/03-design-tic-tac-toe.md) | 85% | Game Loop | Win detection in O(1) using row/col/diag counters |
| 4 | [Design Vending Machine](06-problems/01-core-problems/04-design-vending-machine.md) | 85% | State Pattern | State transitions are exhaustive — draw the FSM first |
| 5 | [Design Splitwise](06-problems/01-core-problems/05-design-splitwise.md) | 80% | Strategy, Graph | Debt simplification: greedy min-cash-flow algorithm |

---

## Tier 2 — Understand class diagram + key pattern cold

| Rank | Problem | Freq | Key Patterns | The Tricky Part |
|------|---------|------|--------------|-----------------|
| 6 | [Design Snake & Ladder](06-problems/02-frequent-problems/08-design-snake-and-ladder.md) | 75% | Observer, Strategy | Board is a graph; snakes/ladders are edges |
| 7 | [Design Elevator System](06-problems/02-frequent-problems/09-design-elevator-system.md) | 75% | State Pattern | SCAN/LOOK scheduling; request batching by direction |
| 8 | [Design Comment System](06-problems/02-frequent-problems/10-design-comment-system.md) | 70% | Composite | Nested comments as a tree; materialized path for DB |
| 9 | [Design Hotel Management](06-problems/02-frequent-problems/11-design-hotel-management.md) | 70% | Factory, Singleton | Concurrency on booking: optimistic lock on room row |
| 10 | [Design LRU Cache](06-problems/02-frequent-problems/13-design-lru-cache.md) | 65% | LinkedHashMap / DLL+HashMap | O(1) get AND put requires doubly-linked list + HashMap together |
| 11 | [Design Locker Service](06-problems/02-frequent-problems/15-design-locker-service.md) | 65% | Inheritance | Locker size matching; geohash for nearest-locker lookup |
| 12 | [Design Coupon System](06-problems/02-frequent-problems/17-design-coupon-system.md) | 60% | Composite, Chain of Responsibility | Stacking rules: which coupons combine, which are exclusive |
| 13 | [Design Mentorship Platform](06-problems/03-domain-specific/18-design-mentorship-platform.md) | 55% | Factory | Booking conflict detection: interval overlap check |
| 14 | [Design Logger Library](06-problems/03-domain-specific/19-design-logger-library.md) | 50% | Chain of Responsibility, Singleton | Log levels as a chain; each handler decides pass-through |
| 15 | [Design Minesweeper](06-problems/04-advanced-niche/25-design-minesweeper.md) | 45% | Flood Fill | BFS from revealed cell; stop at cells adjacent to mines |

---

## Tier 3 — Read for the specific algorithm

| Rank | Problem | Freq | Key Algorithm | Why It Matters |
|------|---------|------|---------------|----------------|
| 16 | [Design S3 Object Storage](06-problems/04-advanced-niche/26-design-s3-object-storage.md) | 40% | Composite Pattern | File system as tree; metadata vs. data separation |
| 17 | [Design Search Engine](06-problems/04-advanced-niche/27-design-search-engine.md) | 35% | Inverted Index, Trie | Tokenization pipeline; prefix search vs. full-text search |
| 18 | [Design Tetris](06-problems/04-advanced-niche/28-design-tetris.md) | 30% | Matrix rotation, Factory | Rotation as 90° transpose + reverse; piece factory |
| 19 | [Design Version Control](06-problems/04-advanced-niche/29-design-version-control.md) | 25% | DAG, SHA-1 hashing | Commits as a DAG; content-addressed storage via hash |
| 20 | [Design Tunneling Service](06-problems/04-advanced-niche/30-design-tunneling-service.md) | 20% | Reverse Proxy, Sockets | Bidirectional socket relay; port multiplexing |
| 21 | [Design Text Editor](06-problems/04-advanced-niche/31-design-text-editor.md) | 20% | Gap Buffer, Command | Gap Buffer for O(1) insert/delete at cursor; Command for undo |
| 22 | [Design Download Manager](06-problems/04-advanced-niche/32-design-download-manager.md) | 15% | HTTP Range, multi-threading | Parallel chunk download; merge and verify checksum |
| 23 | [Design Unlock Pattern](06-problems/04-advanced-niche/33-design-unlock-pattern.md) | 10% | DFS/Backtracking | Validate knight-move jumps; required intermediate points |

---

## Cross-References

- Rate Limiter LLD ↔ [02-building-blocks/rate-limiting.md](../02-building-blocks/02-performance/02-rate-limiting.md) ↔ [HLD Rate Limiter](../05-hld-problems/01-easy/rate-limiter.md)
- LRU Cache LLD ↔ [02-building-blocks/caching-layer.md](../02-building-blocks/02-performance/01-caching-layer.md) ↔ [HLD Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md)
- S3 LLD ↔ [HLD Google Drive](../05-hld-problems/03-hard/google-drive.md) (chunking, dedup)
- Search Engine LLD ↔ [HLD Search System](../05-hld-problems/03-hard/search-system.md) (inverted index at scale)
