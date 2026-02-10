# Low-Level Design (LLD) Interview Problems 📚

A curated list of common LLD interview problems, ranked by popularity and difficulty. Each problem includes **Java Code**, **Class Diagrams**, and **Flow Charts** designed for SDE 2 interviews.

---

## 🏆 Tier 1: Must Know (High Probability)
*These are the most frequently asked LLD problems. Master these first.*

| Problem | Difficulty | Key Concepts |
| :--- | :--- | :--- |
| **[Design Parking Lot](problems/parking-lot.md)** 🅿️ | Medium | Singleton, Pricing Strategy, Factory |
| **[Design Rate Limiter](problems/rate-limiter-lld.md)** 🚦 | Medium | Thread-Safety, Token Bucket, Strategy |
| **[Design Vending Machine](problems/vending-machine.md)** 🍫 | Medium | State Pattern, Enum, State Machine |
| **[Design Tic-Tac-Toe](problems/tic-tac-toe.md)** ❌⭕ | Easy | Game Loop, 2D Array, Extensibility |
| **[Design Snake & Ladder](problems/snake-and-ladder.md)** 🎲 | Medium | Game Loop, Observer, Strategy (Dice) |

---

## 🥈 Tier 2: Highly Probable
*Commonly asked in companies like Uber, Grab, GoJek, Amazon.*

| Problem | Difficulty | Key Concepts |
| :--- | :--- | :--- |
| **[Design Splitwise](problems/splitwise.md)** 💸 | Medium | Expense Sharing, Graph Simplification, Strategy |
| **[Design Elevator System](problems/elevator-system.md)** 🛗 | Medium | State Pattern, Scheduling Algorithms (SCAN/LOOK) |
| **[Design Comment System](problems/comment-system.md)** 💬 | Medium | Materialized Path (Trees), Recursion |
| **[Design Mentorship Platform](problems/mentorship-platform.md)** 👨‍🏫 | Medium | Booking System, Concurrency, Locking |
| **[Design Coupon System](problems/coupon-system.md)** 🏷️ | Medium | Composite Pattern, Chain of Responsibility |
| **[Design Hotel Management](problems/hotel-management.md)** 🏨 | Medium | Booking, Room Allocation, Concurrency |

---

## 🥉 Tier 3: Domain Specific / Hard
*Asked in specific teams (Infrastructure, Gaming, Tools) or for Senior roles.*

| Problem | Difficulty | Key Concepts |
| :--- | :--- | :--- |
| **[Design S3 Object Storage](problems/s3-object-storage.md)** ☁️ | Hard | Metadata Separation, File I/O, Hashing |
| **[Design Search Engine](problems/search-engine.md)** 🔍 | Hard | Inverted Index, TF-IDF, Tries |
| **[Design Tunneling Service (Ngrok)](problems/tunneling-service.md)** 🚇 | Hard | Reverse Proxy, Socket Programming, Async IO |
| **[Design Text Editor](problems/text-editor.md)** 📝 | Hard | Gap Buffer/Rope, Command Pattern, Undo/Redo |
| **[Design Minesweeper](problems/minesweeper.md)** 💣 | Medium | Flood Fill (DFS/BFS), Recursion |
| **[Design Tetris](problems/tetris.md)** 🧱 | Hard | Game Loop, Matrix Rotation, Factory |
| **[Design Download Manager](problems/download-manager.md)** ⬇️ | Hard | Multi-threading, HTTP Ranges, File Merging |
| **[Design Version Control (Git)](problems/version-control.md)** 🌿 | Hard | Graph Theory (DAG), Hashing (SHA-1) |
| **[Design Unlock Pattern](problems/unlock-pattern.md)** 🔓 | Medium | DFS/Backtracking, Validation Logic |

---

## 🛠 Preparation Guide

### 1. The LLD Framework
1.  **Clarify Requirements**: Actors (Admin, User), Use Cases.
2.  **Class Diagram**: Identify Entities, Interfaces, and Relationships.
3.  **Design Patterns**: Apply Strategy, State, Observer, Factory, Singleton.
4.  **Code**: Write modular, thread-safe, and extensible Java code.

### 2. Common Patterns by Problem Category
-   **Games (Turn-Based)**: Game Loop, State Pattern, Factory (Pieces).
-   **Booking Systems**: Locking (Optimistic/Pessimistic), Date Range validation.
-   **Utility Tools (Rate Limiter, Cache)**: Concurrency (Locks, AtomicVars), Double-Linked Lists.
-   **State Machines (Vending, Elevator)**: State Pattern is mandatory.

---

> *Note: Problems marked with links are completed. Others are in progress.*
