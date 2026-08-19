> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A curated collection of 44 Low-Level Design (LLD) interview problems, complete with requirements, class diagrams, code (Python for 1-24, Java for 25-44), and concurrency handling.
>
> **Prioritization:**
> - Very High Priority (1-5): The "Big 5" — Parking Lot, Rate Limiter, Tic-Tac-Toe, Vending Machine, Splitwise. Master these first; they appear in 80% of LLD rounds.
> - High Priority (6-17): Frequent variations — BookMyShow (concurrency), Chess (rules engine), Elevator (State pattern), LRU Cache.
> - Medium Priority (18-24): Domain-specific designs — Logger Library, Pub-Sub, Inventory.
> - Advanced (25-36): Senior-level deep dives — Concurrent Data Structures, S3 Object Storage, Lock-free queues.
>
> **Key takeaway:** LLD interviews test three things: 1) Extracting entities from requirements, 2) Applying OOP principles (Composition over Inheritance) and Design Patterns (Strategy, Factory, State, Observer), 3) Handling concurrency (Thread safety, locks). Treat each problem as practice for these three skills.

---

# LLD Interview Problems

A curated collection of **44** Low-Level Design (LLD) interview problems. Problems 01–24 have Python implementations. Problems 25–44 are advanced/niche and additional-gap topics with Java implementations.

> **Legend**:
> - **Very High** (80–100%): Must prepare. Asked in almost every LLD round.
> - **High** (60–80%): Frequently asked.
> - **Medium** (20–60%): Domain-specific or less common.
> - **Advanced**: Niche or senior-level problems.

---

## Problem List

### Very High Priority (Must Prepare)
1. [Design Parking Lot](01-core-problems/01-design-parking-lot.md) — Singleton, Factory, Strategy, Concurrency
2. [Design Rate Limiter](01-core-problems/02-design-rate-limiter.md) — Token Bucket, Thread-Safety, Decorator
3. [Design Tic-Tac-Toe](01-core-problems/03-design-tic-tac-toe.md) — Game Loop, 2D Arrays, Validation Logic
4. [Design Vending Machine](01-core-problems/04-design-vending-machine.md) — State Pattern, State Machine, Money Handling
5. [Design Splitwise](01-core-problems/05-design-splitwise.md) — Graph Simplification, Strategy (Splits), User/Group

### High Priority (Frequently Asked)
6. [Design BookMyShow](02-frequent-problems/06-design-bookmyshow.md) — Seat Locking, Concurrency, Booking Flow
7. [Design Chess](02-frequent-problems/07-design-chess.md) — Piece Hierarchy, Move Validation, Turn Management
8. [Design Snake & Ladder](02-frequent-problems/08-design-snake-and-ladder.md) — Game Entity, Observer, Strategy (Dice)
9. [Design Elevator System](02-frequent-problems/09-design-elevator-system.md) — State Pattern, Scheduling (SCAN/LOOK)
10. [Design Comment System](02-frequent-problems/10-design-comment-system.md) — Materialized Path (Trees), Recursion
11. [Design Hotel Management](02-frequent-problems/11-design-hotel-management.md) — Booking Factory, Date Concurrency
12. [Design ATM](02-frequent-problems/12-design-atm.md) — State Pattern, Cash Dispensing, Transaction Safety
13. [Design LRU/LFU Cache](02-frequent-problems/13-design-lru-cache.md) — Doubly Linked List + HashMap, Generics
14. [Design Food Delivery](02-frequent-problems/14-design-food-delivery.md) — Order Lifecycle, Observer, Strategy (Dispatch)
15. [Design Locker Service](02-frequent-problems/15-design-locker-service.md) — Geo-hashing, Locker Size Matching, Inheritance
16. [Design Notification System](02-frequent-problems/16-design-notification-system.md) — Observer, Strategy (Channel), Retry
17. [Design Coupon System](02-frequent-problems/17-design-coupon-system.md) — Composite Pattern, Chain of Responsibility

### Medium Priority (Domain-Specific)
18. [Design Mentorship Platform](03-domain-specific/18-design-mentorship-platform.md) — Booking availability, Conflict resolution
19. [Design Logger Library](03-domain-specific/19-design-logger-library.md) — Chain of Responsibility, Singleton, Sink Strategy
20. [Design Library Management](03-domain-specific/20-design-library-management.md) — Catalog, Borrowing Rules, Fine Calculation
21. [Design Order Management](03-domain-specific/21-design-order-management.md) — Order State Machine, Saga, Inventory
22. [Design Ride Sharing](03-domain-specific/22-design-ride-sharing.md) — Matching, Geospatial, Ride Lifecycle
23. [Design Pub-Sub System](03-domain-specific/23-design-pub-sub.md) — Topic/Subscription, Push vs Pull, Delivery Guarantees
24. [Design Inventory Management](03-domain-specific/24-design-inventory-management.md) — Stock Tracking, Alerts, Concurrent Updates

### Advanced / Niche
25. [Design Minesweeper](04-advanced-niche/25-design-minesweeper.md) — Flood Fill (DFS/BFS), Recursion
26. [Design File System / S3](04-advanced-niche/26-design-s3-object-storage.md) — Composite Pattern, Metadata vs Data, Permissions
27. [Design Search Engine](04-advanced-niche/27-design-search-engine.md) — Inverted Index, Tries, Tokenization
28. [Design Tetris](04-advanced-niche/28-design-tetris.md) — Matrix Rotation, Factory Pattern, Game Loop
29. [Design Version Control](04-advanced-niche/29-design-version-control.md) — Graph (DAG), Hashing (SHA-1), Merkle Tree
30. [Design Tunneling Service](04-advanced-niche/30-design-tunneling-service.md) — Reverse Proxy, Socket Programming
31. [Design Text Editor](04-advanced-niche/31-design-text-editor.md) — Gap Buffer/Rope, Command Pattern, Undo/Redo
32. [Design Download Manager](04-advanced-niche/32-design-download-manager.md) — Multi-threading, HTTP Ranges, File Merging
33. [Design Unlock Pattern](04-advanced-niche/33-design-unlock-pattern.md) — DFS/Backtracking, Validation Logic
34. [Design Lock-Free Queue](04-advanced-niche/34-design-lock-free-queue.md) — CAS, Wait-Free Algorithms
35. [Design Concurrent LRU Cache](04-advanced-niche/35-design-concurrent-lru-cache.md) — Thread-safe cache, Striped locking
36. [Design High-Contention Counter](04-advanced-niche/36-design-high-contention-counter.md) — LongAdder, Striped counters

### Additional Problems (Common Gaps)
37. [Design Stack Overflow](06-additional-problems/37-design-stack-overflow.md) — Voting, Reputation, Strategy (Ranking), Observer
38. [Design a Social Network (LinkedIn / Facebook)](06-additional-problems/38-design-social-network.md) — Connection State Machine, Feed Fan-out, Strategy (Ranking)
39. [Design a Stock Brokerage System](06-additional-problems/39-design-stock-brokerage.md) — Strategy (Order Execution), Observer (Price Triggers), Concurrency
40. [Design a Digital Wallet Service](06-additional-problems/40-design-digital-wallet.md) — Double-Entry Ledger, Idempotency, Deadlock-Free Locking
41. [Design a Car Rental System](06-additional-problems/41-design-car-rental.md) — Interval Scheduling, Strategy (Pricing), Concurrency
42. [Design a Restaurant Management System](06-additional-problems/42-design-restaurant-management.md) — State Pattern (Order Lifecycle), Observer (Kitchen Display)
43. [Design an Airline Management System](06-additional-problems/43-design-airline-management.md) — Seat Holding, Strategy (Fare Pricing), Concurrency
44. [Design a Task Management System](06-additional-problems/44-design-task-management.md) — Observer (Activity Log), Command (Undo/Redo), Optimistic Locking

---

## Preparation Strategy

**Tier 1 — Implement from scratch 3×** (bread and butter):
Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise, BookMyShow

**Tier 2 — Understand class diagram + key pattern** (read + trace):
Elevator, Snake & Ladder, Chess, Comment System, Hotel, LRU Cache, ATM, Notification

**Tier 3 — Read for specific algorithms** (one pass):
Locker (geohash), Search (inverted index), Version Control (DAG/Merkle), Text Editor (rope/gap buffer)

**Skip if short on time**: 25–36 (advanced/niche, rarely asked at SDE-2 level)
