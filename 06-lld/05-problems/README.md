# LLD Interview Problems

A curated list of **19** LLD problems ranked by SDE-2 interview frequency.

> **Legend**:
> - **Very High** (80-100%): Must prepare. Asked in almost every LLD round.
> - **High** (60-80%): Frequently asked.
> - **Medium** (20-60%): Domain-specific or less common.

---

## Problem List

### Very High Priority — Implement from scratch 3×

1. [Design Parking Lot](1-design-parking-lot.md) — Singleton, Factory, Strategy, Concurrency
2. [Design Rate Limiter](2-design-rate-limiter.md) — Token Bucket, Thread-Safety, Decorator
3. [Design Tic-Tac-Toe](3-design-tic-tac-toe.md) — Game Loop, 2D Arrays, Validation
4. [Design Vending Machine](4-design-vending-machine.md) — State Pattern, State Machine, Money Handling
5. [Design Splitwise](5-design-splitwise.md) — Graph Simplification, Strategy (Splits), User/Group
6. [Design BookMyShow](13-design-bookmyshow.md) — Booking, Seat Locking, Concurrency, Factory
7. [Design LRU Cache](10-design-lru-cache.md) — Doubly Linked List + HashMap, Generics
8. [Design ATM](18-design-atm.md) — State Pattern, Chain of Responsibility, Transaction Safety

### High Priority — Know the class diagram and key pattern

9. [Design Snake & Ladder](6-design-snake-and-ladder.md) — Observer, Strategy (Dice), Game Entity
10. [Design Elevator System](7-design-elevator-system.md) — State Pattern, Scheduling (SCAN/LOOK)
11. [Design Hotel Management](9-design-hotel-management.md) — Booking Factory, Date Concurrency
12. [Design Library Management](15-design-library-management.md) — Inventory, Reservation Queue, Fines
13. [Design Food Delivery](16-design-food-delivery.md) — Strategy (Agent Assignment), Observer, State
14. [Design Notification System](17-design-notification-system.md) — Observer, Strategy, Decorator, Rate Limiting
15. [Design Logger Library](14-design-logger-library.md) — Chain of Responsibility, Singleton, Sink Strategy

### Medium Priority — Read for specific algorithm/pattern

16. [Design Comment System](8-design-comment-system.md) — Materialized Path (Trees), Recursion
17. [Design Locker Service](11-design-locker-service.md) — Geo-hashing, Locker Size Matching, Inheritance
18. [Design Coupon System](12-design-coupon-system.md) — Composite Pattern, Chain of Responsibility

---

## Preparation Strategy

**Tier 1 — Fire (implement from scratch 3×)**
Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise, BookMyShow, LRU Cache, ATM

**Tier 2 — Green (know class diagram + key pattern)**
Elevator, Snake & Ladder, Hotel, Library, Food Delivery, Notification System, Logger

**Tier 3 — Yellow (read for specific algorithm)**
Comment System, Locker Service, Coupon System
