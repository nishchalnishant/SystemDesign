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

**You**: "Can we use Java's `java.util.concurrent` primitives directly?"
**Interviewer**: "Yes — use real `AtomicLong`/CAS. Discuss how this compares to Java's built-in `LongAdder`."

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
- value: long
- lock: Object (synchronized monitor)

+ increment(delta: long = 1): void
+ decrement(delta: long = 1): void
+ get(): long
```

### StripedCounter

```
class StripedCounter:
- cells: long[]                 # N cells, one per stripe
- locks: ReentrantLock[]        # one lock per cell
- numStripes: int

+ increment(delta: long = 1): void
+ decrement(delta: long = 1): void
+ get(): long                   # sum of all cells (may be slightly stale)
+ reset(): void
```

### CASCounter (real CAS via AtomicLong)

```
class CASCounter:
- value: AtomicLong             # real hardware CAS, no lock needed

+ increment(delta: long = 1): void
+ get(): long
```

---

## Implementation

### NaiveCounter (baseline — the bottleneck)

```java
public class NaiveCounter {
    private long value = 0;
    private final Object lock = new Object();

    public void increment(long delta) {
        synchronized (lock) {
            value += delta;
        }
    }

    public void decrement(long delta) {
        synchronized (lock) {
            value -= delta;
        }
    }

    public long get() {
        synchronized (lock) {
            return value;
        }
    }
}
```

**Problem**: Every increment acquires the same lock. Under 100 threads, 99 threads wait for the 1 holding the lock. Throughput = 1 increment / lock-acquisition-time, regardless of CPU count.

### StripedCounter (primary recommendation)

Distribute increments across N independent cells. Threads are assigned a cell (by `thread_id % num_stripes` or randomly). Each cell has its own lock — threads on different stripes never contend. `get()` sums all cells.

```java
import java.util.concurrent.locks.ReentrantLock;

public class StripedCounter {
    private final int numStripes;
    private final long[] cells;
    private final ReentrantLock[] locks;

    public StripedCounter(int numStripes) {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        this.numStripes = numStripes > 0 ? numStripes : cpuCount * 4;
        this.cells = new long[this.numStripes];
        this.locks = new ReentrantLock[this.numStripes];
        for (int i = 0; i < this.numStripes; i++) {
            locks[i] = new ReentrantLock();
        }
    }

    private int stripeIndex() {
        // Use thread identity to pick a stripe (consistent per-thread)
        long tid = Thread.currentThread().threadId();
        return (int) (tid % numStripes);
    }

    public void increment(long delta) {
        int idx = stripeIndex();
        locks[idx].lock();
        try {
            cells[idx] += delta;
        } finally {
            locks[idx].unlock();
        }
    }

    public void decrement(long delta) {
        int idx = stripeIndex();
        locks[idx].lock();
        try {
            cells[idx] -= delta;
        } finally {
            locks[idx].unlock();
        }
    }

    public long get() {
        // Sum without holding all locks (slightly stale but O(stripes))
        long total = 0;
        for (long cell : cells) {
            total += cell;
        }
        return total;
    }

    public long getExact() {
        // Hold all locks for a consistent snapshot
        for (ReentrantLock lock : locks) {
            lock.lock();
        }
        try {
            long total = 0;
            for (long cell : cells) {
                total += cell;
            }
            return total;
        } finally {
            for (ReentrantLock lock : locks) {
                lock.unlock();
            }
        }
    }

    public void reset() {
        for (int i = 0; i < numStripes; i++) {
            locks[i].lock();
            try {
                cells[i] = 0;
            } finally {
                locks[i].unlock();
            }
        }
    }
}
```

**Throughput**: 100 threads × 1 increment/lock-time, but contention is distributed. Effective throughput scales toward O(num_stripes × 1/lock-time).

### CASCounter (lock-free)

```java
import java.util.concurrent.atomic.AtomicLong;

public class CASCounter {
    // AtomicLong.compareAndSet is a real hardware CAS (LOCK CMPXCHG on x86) —
    // no lock is used to simulate it, unlike a GIL-protected `+=` in Python.
    private final AtomicLong value = new AtomicLong(0);

    public void increment(long delta) {
        while (true) {
            long current = value.get();
            if (value.compareAndSet(current, current + delta)) {
                return;
            }
            // CAS failed — another thread updated concurrently; retry (spin)
        }
    }

    public long get() {
        return value.get();
    }
}
```

**Note**: In production Java you would simply call `value.addAndGet(delta)` (or `getAndAdd`), which performs the same CAS-retry loop internally via `Unsafe`/`VarHandle` intrinsics. The explicit `while` loop above exists to make the CAS pattern visible for the interview — it is not something you'd hand-write in real code. Java has no GIL: without `AtomicLong` or a lock, concurrent `value += delta` on a plain `long` field is a genuine, unguarded data race (lost updates), unlike Python where the GIL happens to serialize the bytecode for a bare `+=`.

### LongAdderCounter (Java LongAdder pattern)

The Java `LongAdder` uses a CAS-protected base value plus a dynamic array of `Cell`s. Under low contention: CAS the base. Under contention (CAS failure): hash thread to a cell, increment that cell. `sum()` = base + sum(cells).

```java
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicBoolean;

public class LongAdderCounter {
    private final AtomicLong base = new AtomicLong(0);
    private volatile AtomicLongArray cells;   // lazily initialized, visible across threads
    private final AtomicBoolean cellsInitialized = new AtomicBoolean(false);
    private final int numCells;

    public LongAdderCounter() {
        this.numCells = Runtime.getRuntime().availableProcessors() * 2;
    }

    public void increment(long delta) {
        // Fast path: try a lock-free CAS on the base (cheap under low contention)
        long current = base.get();
        if (base.compareAndSet(current, current + delta)) {
            return;
        }
        // Contention detected — fall back to striped cells
        ensureCells();
        int idx = (int) (Thread.currentThread().threadId() % numCells);
        cells.getAndAdd(idx, delta);
    }

    private void ensureCells() {
        if (cellsInitialized.compareAndSet(false, true)) {
            cells = new AtomicLongArray(numCells);
        }
        // Busy-wait for the initializing thread to finish, in the rare race
        // where a second thread reaches ensureCells() before `cells` is assigned.
        while (cells == null) {
            Thread.onSpinWait();
        }
    }

    public long get() {
        long total = base.get();
        AtomicLongArray snapshot = cells;
        if (snapshot != null) {
            for (int i = 0; i < snapshot.length(); i++) {
                total += snapshot.get(i);
            }
        }
        return total;
    }

    public void reset() {
        base.set(0);
        AtomicLongArray snapshot = cells;
        if (snapshot != null) {
            for (int i = 0; i < snapshot.length(); i++) {
                snapshot.set(i, 0);
            }
        }
    }
}
```

**Judgment call**: the Python original's fast path uses `lock.acquire(blocking=False)` (a non-blocking mutex try-lock) to detect contention. The idiomatic Java analog isn't a `tryLock()` on a `ReentrantLock` — it's a failed `compareAndSet`, which is exactly what the real `java.util.concurrent.atomic.LongAdder` does internally (`Striped64.longAccumulate`). The port above follows Java's own `LongAdder` design rather than translating the Python mutex-based heuristic literally, since a failed CAS is the more precise and idiomatic "contention detected" signal on the JVM. In production you would simply use `java.util.concurrent.atomic.LongAdder` directly rather than hand-rolling this.

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

### 1. "Does Java need any of this, or can threads just share a `long` field?"

Java has no GIL — the JVM runs threads with genuine parallelism across cores, so there is no accidental serialization to lean on. A bare `counter += 1` on a shared `long` (or even `volatile long`) from multiple threads is a real, unguarded data race: the read-modify-write is not atomic, and concurrent increments will silently lose updates. This is why `AtomicLong`, striped locks/cells, or `LongAdder` are not optional optimizations in Java the way they might appear to be a stylistic choice in a GIL'd interpreter — they are required for correctness under concurrent writers. `StripedCounter` and the CAS-based designs above exist specifically to reduce contention on the CAS/lock while still guaranteeing correctness.

### 2. "How would you build a distributed counter (across multiple machines)?"

Three approaches with different consistency trade-offs:

**Eventually consistent** (e.g., CRDT): each node maintains its own counter, periodically gossips with others, computes sum:
```java
// Each node: Map<String, Long> nodeIdToLocalCount
// Global count = sum of all nodes' local counts
// Merge: take max of each node's count (for increment-only)
```

**Redis INCR**: single Redis instance, atomic `INCR` command, O(1) and durable. Bottleneck at very high rates → use Redis Cluster or pipeline batched increments.

**Kafka**: each increment is a message. Counter = total messages in a topic. Exact but with latency.

### 3. "What if `getExact()` is too expensive?"

For approximate counts, skip locking during `get()`:
```java
public long get() {
    long total = 0;
    for (long cell : cells) {
        total += cell;   // racy but approximately correct
    }
    return total;
}
```

The worst case: one cell is in mid-update. The count is off by at most `delta` for that one operation — acceptable for page view counters, not for financial transactions.

### 4. "How would you implement a rate limiter using this counter?"

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RateLimiter {
    private final StripedCounter counter;
    private final long max;
    private final ScheduledExecutorService resetScheduler;

    public RateLimiter(long maxPerSecond) {
        this.counter = new StripedCounter(0);   // 0 => default stripe count
        this.max = maxPerSecond;
        this.resetScheduler = Executors.newSingleThreadScheduledExecutor(
            r -> {
                Thread t = new Thread(r, "rate-limiter-reset");
                t.setDaemon(true);
                return t;
            }
        );
        resetScheduler.scheduleAtFixedRate(counter::reset, 1, 1, TimeUnit.SECONDS);
    }

    public boolean allow() {
        if (counter.get() >= max) {
            return false;
        }
        counter.increment(1);
        return true;
    }
}
```

---

## Interviewer Questions by Level

**Junior**: `NaiveCounter` with a single lock. Explain why it's a bottleneck. Explain why Java needs explicit synchronization here (no GIL to fall back on).

**Mid-level**: `StripedCounter` — N cells, N locks, thread → cell by hash. `get()` sums cells (approximately). Explain contention reduction factor.

**Senior**: LongAdder pattern (fast path CAS on base, fall back to cells on contention). `getExact()` requiring all locks. Distributed counter (CRDT, Redis INCR, Kafka). Why Java's real `LongAdder` exists and how it compares to hand-rolled CAS. Rate limiter application.

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

---

## Related

**SOLID focus**: [Interface Segregation](../../02-solid-principles/04-interface-segregation.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design a Lock-Free Queue](34-design-lock-free-queue.md)
- [Design Rate Limiter](../01-core-problems/02-design-rate-limiter.md)

Contention on a shared counter is the rate limiter core.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
