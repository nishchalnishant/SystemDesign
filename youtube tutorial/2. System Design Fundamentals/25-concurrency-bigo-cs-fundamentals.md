# Concurrency, Big-O & CS Fundamentals

> **Source**: Videos #45, #69, #82, #89 from the playlist
> - Python Vs C++ Vs Java!
> - Concurrency Vs Parallelism!
> - Big-O Notation in 3 Minutes
> - How the Garbage Collector Works in Java, Python, and Go!

---

## Concurrency vs Parallelism

### Definitions
- **Concurrency**: Dealing with multiple tasks at once (interleaving)
- **Parallelism**: Doing multiple tasks at once (simultaneous)

```
Concurrency (single core):
Task A: ████░░████░░████
Task B: ░░████░░████░░████

Parallelism (multi-core):
Core 1 - Task A: ████████████
Core 2 - Task B: ████████████
```

### Key Concepts

| Concept | Description |
|---|---|
| **Thread** | Lightweight execution unit within a process; shared memory |
| **Process** | Independent execution with its own memory space |
| **Mutex** | Mutual exclusion lock — only one thread enters critical section |
| **Semaphore** | Counter-based lock — allows N threads |
| **Deadlock** | Two threads each waiting for the other's lock |
| **Race Condition** | Output depends on timing of thread execution |

### Concurrency Models
| Model | Language | How |
|---|---|---|
| **Threads** | Java, C++ | OS-level threads, shared memory |
| **Async/Await** | Python, JS, Rust | Event loop, non-blocking I/O |
| **Goroutines** | Go | Lightweight green threads, channels |
| **Actors** | Erlang, Akka | Message passing, no shared state |
| **CSP** | Go | Communicating Sequential Processes (channels) |

---

## Big-O Notation

### Common Complexities (Best → Worst)

| Big-O | Name | Example |
|---|---|---|
| O(1) | Constant | Hash table lookup |
| O(log n) | Logarithmic | Binary search |
| O(n) | Linear | Linear search |
| O(n log n) | Linearithmic | Merge sort |
| O(n²) | Quadratic | Bubble sort |
| O(2ⁿ) | Exponential | Recursive Fibonacci |
| O(n!) | Factorial | Permutations |

### In System Design Context
- **Hash table**: O(1) lookups → Use for caches, indexes
- **B-Tree**: O(log n) → Database indexes
- **Sequential scan**: O(n) → Avoid on large datasets
- Focus on **which data structure** gives the right complexity for your access pattern

---

## Python vs C++ vs Java

| Feature | Python | Java | C++ |
|---|---|---|---|
| **Type** | Interpreted | Compiled (JVM) | Compiled (native) |
| **Speed** | Slow | Fast | Fastest |
| **Memory** | GC (ref counting + cycle) | GC (generational) | Manual (or smart pointers) |
| **Concurrency** | GIL limits threads | True multithreading | True multithreading |
| **Use Case** | ML, scripting, web | Enterprise, Android | Systems, games, embedded |
| **Learning Curve** | Easy | Medium | Hard |

---

## Garbage Collection

### Java (Generational GC)
```
Young Generation → Minor GC (fast, frequent)
  Eden Space → Survivor 1 → Survivor 2
Old Generation  → Major GC (slow, infrequent)
```
- Objects start in Young Gen, promoted to Old Gen if they survive
- GC algorithms: G1 (default), ZGC (low-latency), Shenandoah

### Python (Reference Counting + Cycle Detection)
- **Reference counting**: Free when count reaches 0
- **Cycle detector**: Handles circular references (generational)
- GIL (Global Interpreter Lock) limits true parallelism

### Go (Concurrent Mark-and-Sweep)
- **Tri-color marking**: White (unreachable), Gray (processing), Black (reachable)
- **Concurrent**: GC runs alongside application
- Very low pause times (< 1ms target)
- No generational collection (simpler design)
