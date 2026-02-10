# Low-Level Design (LLD) Interview Problems 📚

A curated list of **20** common LLD interview problems, ranked by **Probability of being asked** in SDE 2 interviews.

> **Legend**:
> - 🔥 **Very High** (80-100%): Must prepare. Asked in almost every LLD round.
> - 🟢 **High** (60-80%): Frequently asked.
> - 🟡 **Medium** (20-60%): Domain-specific or less common.

---

## 🏆 The Master List (Ranked)

| Rank | Problem | Probability | Difficulty | Key Concepts |
| :--- | :--- | :--- | :--- | :--- |
| **1** | **[Design Parking Lot](problems/1-design-parking-lot.md)** 🅿️ | 🔥 **95%** | Medium | Singleton, Factory, Strategy, Concurrency |
| **2** | **[Design Rate Limiter](problems/2-design-rate-limiter.md)** 🚦 | 🔥 **90%** | Medium | Token Bucket, Thread-Safety, Decorator |
| **3** | **[Design Tic-Tac-Toe](problems/3-design-tic-tac-toe.md)** ❌⭕ | 🔥 **85%** | Easy | Game Loop, 2D Arrays, Validation Logic |
| **4** | **[Design Vending Machine](problems/4-design-vending-machine.md)** 🍫 | 🔥 **85%** | Medium | State Pattern, State Machine, Money Handling |
| **5** | **[Design Splitwise](problems/5-design-splitwise.md)** 💸 | 🔥 **80%** | Medium | Graph Simplification, Strategy (Splits), User/Group |
| **6** | **[Design Snake & Ladder](problems/6-design-snake-and-ladder.md)** 🎲 | 🟢 **75%** | Medium | Game Entity, Observer, Strategy (Dice) |
| **7** | **[Design Elevator System](problems/7-design-elevator-system.md)** 🛗 | 🟢 **75%** | Medium | State Pattern, Scheduling Algorithms (SCAN/LOOK) |
| **8** | **[Design Comment System](problems/8-design-comment-system.md)** 💬 | 🟢 **70%** | Medium | Materialized Path (Trees), Recursion, Database Design |
| **9** | **[Design Hotel Management](problems/9-design-hotel-management.md)** 🏨 | 🟢 **70%** | Medium | Booking Factory, Date Concurrency, Singleton |
| **10** | **[Design Cache (LRU/LFU)](problems/10-design-lru-cache.md)** ⚡️ | 🟢 **65%** | Hard | Doubly Linked List + HashMap, Generics |
| **11** | **[Design Locker Service (Amazon)](problems/11-design-locker-service.md)** 📦 | 🟢 **65%** | Medium | Geo-hashing, Locker Size Matching, Inheritance |
| **12** | **[Design Coupon System](problems/12-design-coupon-system.md)** 🏷️ | 🟡 **60%** | Medium | Composite Pattern, Chain of Responsibility |
| **13** | **[Design Mentorship Platform](problems/13-design-mentorship-platform.md)** 👨‍🏫 | 🟡 **55%** | Medium | Booking availability, Conflict resolution |
| **14** | **[Design Logger Library](problems/14-design-logger-library.md)** 📜 | 🟡 **50%** | Easy | Chain of Responsibility, Singleton, Sink Strategy |
| **15** | **[Design Minesweeper](problems/15-design-minesweeper.md)** 💣 | 🟡 **45%** | Medium | Flood Fill (DFS/BFS), Recursion |
| **16** | **[Design File System / S3](problems/16-design-s3-object-storage.md)** ☁️ | 🟡 **40%** | Hard | Composite Pattern, Metadata vs Data, Permissions |
| **17** | **[Design Search Engine](problems/17-design-search-engine.md)** 🔍 | 🟡 **35%** | Hard | Inverted Index, Tries, Tokenization |
| **18** | **[Design Tetris](problems/18-design-tetris.md)** 🧱 | 🟡 **30%** | Hard | Matrix Rotation, Factory Pattern, Game Loop |
| **19** | **[Design Version Control (Git)](problems/19-design-version-control.md)** 🌿 | 🟡 **25%** | Hard | Graph (DAG), Hashing (SHA-1), Merkle Tree |
| **20** | **[Design Tunneling Service](problems/20-design-tunneling-service.md)** 🚇 | 🟡 **20%** | Hard | Reverse Proxy, Socket Programming |
| **21** | **[Design Text Editor](problems/21-design-text-editor.md)** 📝 | 🟡 **20%** | Hard | Gap Buffer/Rope, Command Pattern, Undo/Redo |
| **22** | **[Design Download Manager](problems/22-design-download-manager.md)** ⬇️ | 🟡 **15%** | Hard | Multi-threading, HTTP Ranges, File Merging |
| **23** | **[Design Unlock Pattern](problems/23-design-unlock-pattern.md)** 🔓 | 🟡 **10%** | Medium | DFS/Backtracking, Validation Logic |

---

## 🛠 Preparation Strategy

1.  **Tier 1 (Fire 🔥)**: Implement these **from scratch** 3 times. These are your bread and butter. 
    *   *Parking Lot, Rate Limiter, Vending Machine, Tic-Tac-Toe, Splitwise*.
2.  **Tier 2 (Green 🟢)**: Understand the **Class Diagram** and **Key Design Pattern**.
    *   *Elevator, Snake & Ladder, Comments, Hotel, LRU Cache*.
3.  **Tier 3 (Yellow 🟡)**: Read the code to understand **specific algorithms** (e.g. QuadTree for Locker, Tries for Search).

---

> *Note: Problems marked with links are completed.*
