# LLD Interview Problems 📚

A curated collection of **23** common Low-Level Design (LLD) interview problems, ranked by probability of being asked in SDE 2 interviews.

> **Legend**:
> - 🔥 **Very High** (80-100%): Must prepare. Asked in almost every LLD round.
> - 🟢 **High** (60-80%): Frequently asked.
> - 🟡 **Medium** (20-60%): Domain-specific or less common.

---

## 🏆 The Complete Problem List

This section contains detailed solutions and explanations for all 23 problems:

### 🔥 Very High Priority (Must Prepare)
1. [Design Parking Lot](1-design-parking-lot.md) - Singleton, Factory, Strategy, Concurrency
2. [Design Rate Limiter](2-design-rate-limiter.md) - Token Bucket, Thread-Safety, Decorator
3. [Design Tic-Tac-Toe](3-design-tic-tac-toe.md) - Game Loop, 2D Arrays, Validation Logic
4. [Design Vending Machine](4-design-vending-machine.md) - State Pattern, State Machine, Money Handling
5. [Design Splitwise](5-design-splitwise.md) - Graph Simplification, Strategy (Splits), User/Group

### 🟢 High Priority (Frequently Asked)
6. [Design Snake & Ladder](6-design-snake-and-ladder.md) - Game Entity, Observer, Strategy (Dice)
7. [Design Elevator System](7-design-elevator-system.md) - State Pattern, Scheduling Algorithms (SCAN/LOOK)
8. [Design Comment System](8-design-comment-system.md) - Materialized Path (Trees), Recursion, Database Design
9. [Design Hotel Management](9-design-hotel-management.md) - Booking Factory, Date Concurrency, Singleton
10. [Design Cache (LRU/LFU)](10-design-lru-cache.md) - Doubly Linked List + HashMap, Generics
11. [Design Locker Service](11-design-locker-service.md) - Geo-hashing, Locker Size Matching, Inheritance

### 🟡 Medium Priority (Domain-Specific)
12. [Design Coupon System](12-design-coupon-system.md) - Composite Pattern, Chain of Responsibility
13. [Design Mentorship Platform](13-design-mentorship-platform.md) - Booking availability, Conflict resolution
14. [Design Logger Library](14-design-logger-library.md) - Chain of Responsibility, Singleton, Sink Strategy
15. [Design Minesweeper](15-design-minesweeper.md) - Flood Fill (DFS/BFS), Recursion
16. [Design File System / S3](16-design-s3-object-storage.md) - Composite Pattern, Metadata vs Data, Permissions
17. [Design Search Engine](17-design-search-engine.md) - Inverted Index, Tries, Tokenization
18. [Design Tetris](18-design-tetris.md) - Matrix Rotation, Factory Pattern, Game Loop
19. [Design Version Control](19-design-version-control.md) - Graph (DAG), Hashing (SHA-1), Merkle Tree
20. [Design Tunneling Service](20-design-tunneling-service.md) - Reverse Proxy, Socket Programming
21. [Design Text Editor](21-design-text-editor.md) - Gap Buffer/Rope, Command Pattern, Undo/Redo
22. [Design Download Manager](22-design-download-manager.md) - Multi-threading, HTTP Ranges, File Merging
23. [Design Unlock Pattern](23-design-unlock-pattern.md) - DFS/Backtracking, Validation Logic

---

## 🛠 Preparation Strategy

1. **Tier 1 (Fire 🔥)**: Implement these **from scratch** 3 times. These are your bread and butter.
   - *Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise*.

2. **Tier 2 (Green 🟢)**: Understand the **Class Diagram** and **Key Design Pattern**.
   - *Elevator, Snake & Ladder, Comments, Hotel, LRU Cache*.

3. **Tier 3 (Yellow 🟡)**: Read the code to understand **specific algorithms** (e.g. QuadTree for Locker, Tries for Search).

---

Click on any problem above to view its detailed solution and implementation.
