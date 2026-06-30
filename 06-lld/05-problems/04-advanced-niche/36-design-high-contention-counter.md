> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a High-Contention Counter — testing advanced concurrency concepts (like Java's `LongAdder`) to optimize metrics counting under massive load.
>
> **Key concepts:**
> - The Problem: `AtomicLong` uses a single CAS loop. If 1000 threads try to increment it simultaneously, 999 fail, spin, and retry, causing massive CPU contention and cache-line invalidation (false sharing).
> - Striped Counters: Instead of one variable, use an array of variables (cells). 
> - The Algorithm: 
>   - When a thread wants to increment, it hashes its own Thread ID to pick a specific cell in the array and increments that cell using CAS.
>   - Since threads map to different cells, contention is drastically reduced.
> - Getting the Total: When you need the actual count, iterate through the array and sum all the cells. (This is slightly slower, but usually reads are rare compared to writes for metrics).
>
> **Key takeaway:** This is a very specific systems question. Knowing the difference between `AtomicLong` (good for low contention) and `LongAdder` (Striped cells, good for high contention) is the key to passing this.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, high-contention-counter, striped-counter, longadder, eventual-consistency]
---
# Design High-Contention Counter

> **Difficulty**: Hard
> **Asked at**: Amazon, Cloudflare, Stripe
> **Key Patterns**: Striped counters (LongAdder pattern), CAS loops, Eventual consistency

---

## Understanding the Problem

Design a counter that supports high-throughput concurrent increment operations without becoming a bottleneck. A naive shared integer with a lock is a sequential bottleneck at high concurrency.

---

## Clarifying Questions

**You**: "What operations do we need — increment only, or also decrement and get?"
**Interviewer**: "Increment, decrement, and get. Increment/decrement are the hot path."

**You**: "Is the read (get) expected to be perfectly accurate in real time?"
**Interviewer**: "Reads can be slightly stale — eventual consistency is acceptable."

**You**: "What's the scale — how many threads concurrently?"
**Interviewer**: "Hundreds of threads. Single-machine, in-process."

**You**: "What's the use case — request counter, like counter, vote counter?"
**Interviewer**: "General purpose — think page view counter or rate limiter base counter."

**You**: "Can we use Python's threading module?"
**Interviewer**: "Yes — simulate the design. Discuss where Python's GIL helps and where it doesn't."

---

## Final Requirements

**In scope:**
1. `increment(delta=1)` — high-throughput, concurrent-safe
2. `decrement(delta=1)` — high-throughput, concurrent-safe
3. `get()` — returns approximate current value (may be slightly stale)
4. `reset()` — reset counter to 0

**Out of scope:**
- Distributed counter (across multiple machines)
- Persistent counter (disk)
- Exactly-once increment semantics

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `NaiveCounter` | Single int + lock; baseline (serializes all increments) |
| `StripedCounter` | N cells; each increment targets a cell by thread ID or random → reduces contention N× |
| `CASCounter` | Single int; CAS retry loop; no lock |
| `LongAdderCounter` | Combines CAS base with striped cells (Java LongAdder pattern) |

`StripedCounter` is the primary recommendation for high concurrency. `CASCounter` demonstrates lock-free techniques. `LongAdderCounter` is the production-grade hybrid.

---

## Class Design

### NaiveCounter (baseline)

```
class NaiveCounter:
- value: int
- lock: threading.Lock

+ increment(delta=1)
+ decrement(delta=1)
+ get() -> int
```

### StripedCounter

```
class StripedCounter:
- cells: list[int]       # N cells, one per stripe
- locks: list[Lock]      # one lock per cell
- num_stripes: int

+ increment(delta=1)
+ decrement(delta=1)
+ get() -> int           # sum of all cells (may be slightly stale)
+ reset()
```

### CASCounter (lock-free simulation)

```
class CASCounter:
- _value: int
- _lock: Lock            # only for CAS simulation; real CAS uses CPU instruction

+ increment(delta=1)
+ get() -> int
```

---

## Implementation

### NaiveCounter (baseline — the bottleneck)

```python
class NaiveCounter:
    def __init__(self):
        self._value = 0
        self._lock = threading.Lock()

    def increment(self, delta=1):
        with self._lock:
            self._value += delta

    def decrement(self, delta=1):
        with self._lock:
            self._value -= delta

    def get(self):
        with self._lock:
            return self._value
```

**Problem**: Every increment acquires the same lock. Under 100 threads, 99 threads wait for the 1 holding the lock. Throughput = 1 increment / lock-acquisition-time, regardless of CPU count.

### StripedCounter (primary recommendation)

Distribute increments across N independent cells. Threads are assigned a cell (by `thread_id % num_stripes` or randomly). Each cell has its own lock — threads on different stripes never contend. `get()` sums all cells.

```python
class StripedCounter:
    def __init__(self, num_stripes=None):
        cpu_count = os.cpu_count() or 4
        self.num_stripes = num_stripes or cpu_count * 4
        self.cells = [0] * self.num_stripes
        self.locks = [threading.Lock() for _ in range(self.num_stripes)]

    def _stripe_index(self):
        # Use thread identity to pick a stripe (consistent per-thread)
        return threading.get_ident() % self.num_stripes

    def increment(self, delta=1):
        idx = self._stripe_index()
        with self.locks[idx]:
            self.cells[idx] += delta

    def decrement(self, delta=1):
        idx = self._stripe_index()
        with self.locks[idx]:
            self.cells[idx] -= delta

    def get(self):
        # Sum without holding all locks (slightly stale but O(stripes))
        return sum(self.cells)

    def get_exact(self):
        # Hold all locks for a consistent snapshot
        for lock in self.locks:
            lock.acquire()
        try:
            return sum(self.cells)
        finally:
            for lock in self.locks:
                lock.release()

    def reset(self):
        for i, lock in enumerate(self.locks):
            with lock:
                self.cells[i] = 0
```

**Throughput**: 100 threads × 1 increment/lock-time, but contention is distributed. Effective throughput scales toward O(num_stripes × 1/lock-time).

### CASCounter (lock-free)

```python
class CASCounter:
    def __init__(self):
        self._value = 0
        self._lock = threading.Lock()  # simulation only

    def _cas(self, expected, new_val):
        with self._lock:
            if self._value == expected:
                self._value = new_val
                return True
            return False

    def increment(self, delta=1):
        while True:
            current = self._value
            if self._cas(current, current + delta):
                return

    def get(self):
        return self._value
```

**Note**: In Python, `+=` on an integer is effectively atomic due to the GIL, but in Java/C++ without a lock, CAS is needed. The CASCounter demonstrates the pattern; in production Python you'd use `threading.Lock` or rely on the GIL.

### LongAdderCounter (Java LongAdder pattern)

The Java `LongAdder` uses a CAS-protected base value plus a dynamic array of `Cell`s. Under low contention: CAS the base. Under contention (CAS failure): hash thread to a cell, increment that cell. `sum()` = base + sum(cells).

```python
class LongAdderCounter:
    def __init__(self):
        self._base = 0
        self._base_lock = threading.Lock()
        self._cells = []
        self._cell_locks = []
        self._cells_initialized = threading.Event()
        self._num_cells = (os.cpu_count() or 4) * 2

    def increment(self, delta=1):
        # Try base first (fast path for low contention)
        if self._base_lock.acquire(blocking=False):
            self._base += delta
            self._base_lock.release()
        else:
            # Contention detected — use striped cells
            self._ensure_cells()
            idx = threading.get_ident() % self._num_cells
            with self._cell_locks[idx]:
                self._cells[idx] += delta

    def _ensure_cells(self):
        if not self._cells:
            self._cells = [0] * self._num_cells
            self._cell_locks = [threading.Lock() for _ in range(self._num_cells)]

    def get(self):
        with self._base_lock:
            total = self._base
        for i, lock in enumerate(self._cell_locks):
            with lock:
                total += self._cells[i]
        return total

    def reset(self):
        with self._base_lock:
            self._base = 0
        for i, lock in enumerate(self._cell_locks):
            with lock:
                self._cells[i] = 0
```

---

## Verification

```
StripedCounter, 4 stripes, 8 threads
Thread assignments by ident % 4:
  T1→stripe0, T2→stripe1, T3→stripe2, T4→stripe3
  T5→stripe0, T6→stripe1, T7→stripe2, T8→stripe3

All 8 threads call increment() simultaneously:
  T1 and T5 contend for locks[0] → one waits (1 wait pair)
  T2 and T6 contend for locks[1] → one waits
  T3 and T7 contend for locks[2] → one waits
  T4 and T8 contend for locks[3] → one waits
  
  4 pairs contend independently (vs NaiveCounter where 7 threads wait)
  All 8 increments complete in ≈ 2 × lock_time (2 rounds, 4 parallel)

NaiveCounter: same 8 threads → 8 × lock_time (sequential)

get():
  cells = [2, 2, 2, 2], sum = 8 ✓
```

---

## Deep Dive & Extensibility

### 1. "How does Python's GIL affect this design?"

The GIL (Global Interpreter Lock) serializes Python bytecode execution — only one thread runs Python at a time. This means `counter += 1` is somewhat protected for simple Python integers. However:
- GIL is released during I/O and C extensions — not reliable for pure correctness
- The GIL doesn't eliminate all races in compound operations
- Performance is still limited by GIL contention — `StripedCounter` reduces GIL acquisition frequency

In Java/C++/Go (no GIL): CAS and striped counters are essential — not just optimization.

### 2. "How would you build a distributed counter (across multiple machines)?"

Three approaches with different consistency trade-offs:

**Eventually consistent** (e.g., CRDT): each node maintains its own counter, periodically gossips with others, computes sum:
```python
# Each node: {node_id: local_count}
# Global count = sum of all nodes' local counts
# Merge: take max of each node's count (for increment-only)
```

**Redis INCR**: single Redis instance, atomic `INCR` command, O(1) and durable. Bottleneck at very high rates → use Redis Cluster or pipeline batched increments.

**Kafka**: each increment is a message. Counter = total messages in a topic. Exact but with latency.

### 3. "What if `get_exact()` is too expensive?"

For approximate counts, skip locking during `get()`:
```python
def get(self):
    return sum(self.cells)   # racy but approximately correct
```

The worst case: one cell is in mid-update. The count is off by at most `delta` for that one operation — acceptable for page view counters, not for financial transactions.

### 4. "How would you implement a rate limiter using this counter?"

```python
class RateLimiter:
    def __init__(self, max_per_second):
        self.counter = StripedCounter()
        self.max = max_per_second
        self._reset_thread = threading.Thread(target=self._reset_loop, daemon=True)
        self._reset_thread.start()

    def allow(self):
        if self.counter.get() >= self.max:
            return False
        self.counter.increment()
        return True

    def _reset_loop(self):
        while True:
            time.sleep(1.0)
            self.counter.reset()
```

---

## Interviewer Questions by Level

**Junior**: `NaiveCounter` with a single lock. Explain why it's a bottleneck. Mention the GIL.

**Mid-level**: `StripedCounter` — N cells, N locks, thread → cell by hash. `get()` sums cells (approximately). Explain contention reduction factor.

**Senior**: LongAdder pattern (fast path CAS on base, fall back to cells on contention). `get_exact()` requiring all locks. Distributed counter (CRDT, Redis INCR, Kafka). GIL implications in Python vs Java. Rate limiter application.

---

## Common Interview Questions

- **Q**: Why is a single lock on a counter a bottleneck?
  **A**: All threads must acquire the same lock serially. With N threads, N-1 always wait. Throughput is bounded by 1/lock-acquisition-time regardless of CPU count — you can't parallelize it.

- **Q**: How does striping reduce contention?
  **A**: Distribute the counter across N cells, each with its own lock. A thread picks its cell (by thread ID hash). Threads on different cells never contend. With N stripes and N threads evenly distributed, throughput scales N× compared to a single lock.

- **Q**: Why is `get()` on a StripedCounter approximate?
  **A**: Reading all cells without holding all locks simultaneously means some cells might be in mid-update. The sum might be off by at most one delta per stripe. For most use cases (page views, metrics), this is acceptable.

- **Q**: What is Java's LongAdder and how does it improve on AtomicLong?
  **A**: `AtomicLong` uses a single CAS — under high contention, many threads repeatedly fail CAS and retry (spin). `LongAdder` uses a CAS base + striped cells: successful CAS updates the base; failed CAS redirects to a cell by thread hash. Under low contention it's as fast as `AtomicLong`; under high contention, throughput scales linearly.

- **Q**: When would you use a high-contention counter over a simple database column?
  **A**: DB column: durable, consistent, but each increment is a network round-trip + disk write (low throughput, high latency). In-memory striped counter: millions of increments/second, no disk, but lost on crash and single-machine only. Use in-memory for hot real-time metrics; periodically flush to DB for durability.
