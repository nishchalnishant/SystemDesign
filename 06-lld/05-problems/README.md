# LLD Interview Problems

A curated collection of **36** Low-Level Design (LLD) interview problems. Problems 01–24 have Python implementations. Problems 25–36 are advanced/niche topics with Java implementations.

> **Legend**:
> - **Very High** (80–100%): Must prepare. Asked in almost every LLD round.
> - **High** (60–80%): Frequently asked.
> - **Medium** (20–60%): Domain-specific or less common.
> - **Advanced**: Niche or senior-level problems.

---

## Problem List

### Very High Priority (Must Prepare)
1. [Design Parking Lot](01-design-parking-lot.md) — Singleton, Factory, Strategy, Concurrency
2. [Design Rate Limiter](02-design-rate-limiter.md) — Token Bucket, Thread-Safety, Decorator
3. [Design Tic-Tac-Toe](03-design-tic-tac-toe.md) — Game Loop, 2D Arrays, Validation Logic
4. [Design Vending Machine](04-design-vending-machine.md) — State Pattern, State Machine, Money Handling
5. [Design Splitwise](05-design-splitwise.md) — Graph Simplification, Strategy (Splits), User/Group

### High Priority (Frequently Asked)
6. [Design BookMyShow](06-design-bookmyshow.md) — Seat Locking, Concurrency, Booking Flow
7. [Design Chess](07-design-chess.md) — Piece Hierarchy, Move Validation, Turn Management
8. [Design Snake & Ladder](08-design-snake-and-ladder.md) — Game Entity, Observer, Strategy (Dice)
9. [Design Elevator System](09-design-elevator-system.md) — State Pattern, Scheduling (SCAN/LOOK)
10. [Design Comment System](10-design-comment-system.md) — Materialized Path (Trees), Recursion
11. [Design Hotel Management](11-design-hotel-management.md) — Booking Factory, Date Concurrency
12. [Design ATM](12-design-atm.md) — State Pattern, Cash Dispensing, Transaction Safety
13. [Design LRU/LFU Cache](13-design-lru-cache.md) — Doubly Linked List + HashMap, Generics
14. [Design Food Delivery](14-design-food-delivery.md) — Order Lifecycle, Observer, Strategy (Dispatch)
15. [Design Locker Service](15-design-locker-service.md) — Geo-hashing, Locker Size Matching, Inheritance
16. [Design Notification System](16-design-notification-system.md) — Observer, Strategy (Channel), Retry
17. [Design Coupon System](17-design-coupon-system.md) — Composite Pattern, Chain of Responsibility

### Medium Priority (Domain-Specific)
18. [Design Mentorship Platform](18-design-mentorship-platform.md) — Booking availability, Conflict resolution
19. [Design Logger Library](19-design-logger-library.md) — Chain of Responsibility, Singleton, Sink Strategy
20. [Design Library Management](20-design-library-management.md) — Catalog, Borrowing Rules, Fine Calculation
21. [Design Order Management](21-design-order-management.md) — Order State Machine, Saga, Inventory
22. [Design Ride Sharing](22-design-ride-sharing.md) — Matching, Geospatial, Ride Lifecycle
23. [Design Pub-Sub System](23-design-pub-sub.md) — Topic/Subscription, Push vs Pull, Delivery Guarantees
24. [Design Inventory Management](24-design-inventory-management.md) — Stock Tracking, Alerts, Concurrent Updates

### Advanced / Niche
25. [Design Minesweeper](25-design-minesweeper.md) — Flood Fill (DFS/BFS), Recursion
26. [Design File System / S3](26-design-s3-object-storage.md) — Composite Pattern, Metadata vs Data, Permissions
27. [Design Search Engine](27-design-search-engine.md) — Inverted Index, Tries, Tokenization
28. [Design Tetris](28-design-tetris.md) — Matrix Rotation, Factory Pattern, Game Loop
29. [Design Version Control](29-design-version-control.md) — Graph (DAG), Hashing (SHA-1), Merkle Tree
30. [Design Tunneling Service](30-design-tunneling-service.md) — Reverse Proxy, Socket Programming
31. [Design Text Editor](31-design-text-editor.md) — Gap Buffer/Rope, Command Pattern, Undo/Redo
32. [Design Download Manager](32-design-download-manager.md) — Multi-threading, HTTP Ranges, File Merging
33. [Design Unlock Pattern](33-design-unlock-pattern.md) — DFS/Backtracking, Validation Logic
34. [Design Lock-Free Queue](34-design-lock-free-queue.md) — CAS, Wait-Free Algorithms
35. [Design Concurrent LRU Cache](35-design-concurrent-lru-cache.md) — Thread-safe cache, Striped locking
36. [Design High-Contention Counter](36-design-high-contention-counter.md) — LongAdder, Striped counters

---

## Preparation Strategy

**Tier 1 — Implement from scratch 3×** (bread and butter):
Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise, BookMyShow

**Tier 2 — Understand class diagram + key pattern** (read + trace):
Elevator, Snake & Ladder, Chess, Comment System, Hotel, LRU Cache, ATM, Notification

**Tier 3 — Read for specific algorithms** (one pass):
Locker (geohash), Search (inverted index), Version Control (DAG/Merkle), Text Editor (rope/gap buffer)

**Skip if short on time**: 25–36 (advanced/niche, rarely asked at SDE-2 level)
