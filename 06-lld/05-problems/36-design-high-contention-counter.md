---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems, concurrency]
---
# LLD: Design a High-Contention Counter

> **Implement a counter that supports millions of concurrent increments per second with minimal contention, correct final reads, and optional approximate-read semantics.**

---

## Problem Statement

Design a counter that:
- `void increment()` — increment the counter by 1, called from many threads simultaneously
- `long get()` — return the current count (exact or approximate depending on variant)
- Thread-safe: 100+ threads increment concurrently
- Target: 10M+ increments/second on an 8-core machine

---

## Why This Is Hard

A naive `long count++` is not atomic — it compiles to read-modify-write: three instructions, not one. Two threads reading the same value both increment it and write back, losing one update.

```
Thread A: read count=5
Thread B: read count=5
Thread A: write count=6
Thread B: write count=6   ← lost increment
```

**High contention** means every attempted fix creates a new bottleneck:
- `synchronized`: serializes all threads → throughput collapses to single-thread speed
- `AtomicLong.incrementAndGet()`: uses CAS (Compare-And-Swap); under extreme contention, CAS loops spin-retry → CPU waste
- Single shared variable: all threads fight over the same cache line → cache line ping-pong across CPU cores

---

## Solution 1: AtomicLong (Baseline)

Use `java.util.concurrent.atomic.AtomicLong` with CAS.

```java
public class AtomicCounter {
    private final AtomicLong count = new AtomicLong(0);

    public void increment() {
        count.incrementAndGet();
    }

    public long get() {
        return count.get();
    }
}
```

**How CAS works:**
```
compareAndSet(expected, expected + 1):
  if (current == expected) { current = expected + 1; return true; }
  else { return false; }  // retry
```

**Problem under high contention:** Many threads read value=5, all try CAS(5, 6). Only one succeeds. All others retry. Under 100 threads, each increment may require O(N) retries → throughput degrades from O(1) to O(N) per increment.

**Verdict**: Good up to ~20–50 threads. Breaks down beyond that.

---

## Solution 2: LongAdder — Striped Counter (Production Choice)

`java.util.concurrent.atomic.LongAdder` (Java 8+) solves high-contention CAS retries via **counter striping**: instead of one shared value, maintain an array of cells, each on a separate cache line. Each thread increments a cell based on its thread ID (with fallback hashing). `sum()` adds all cells.

```java
public class LongAdderCounter {
    private final LongAdder count = new LongAdder();

    public void increment() {
        count.increment();           // adds to thread-local cell
    }

    public long get() {
        return count.sum();          // sums all cells — not a point-in-time snapshot
    }

    public long getAndReset() {
        return count.sumThenReset(); // atomically sum + zero all cells
    }
}
```

**Internal structure:**
```
LongAdder
├── base: long          ← uncontended path; CAS here first
└── cells: Cell[]       ← allocated on first contention
    ├── Cell[0]: value=1201  ← Thread group 0 increments here
    ├── Cell[1]: value=988   ← Thread group 1 increments here
    ├── Cell[2]: value=1055  ← Thread group 2 increments here
    └── Cell[3]: value=756   ← Thread group 3 increments here
sum() = base + cells[0] + cells[1] + cells[2] + cells[3] = 4000
```

**Cache line padding:** Each `Cell` is annotated `@Contended` (JVM flag: `-XX:-RestrictContended`), which pads the cell to 128 bytes (2 cache lines). Prevents false sharing — updating Cell[0] doesn't invalidate Cell[1]'s cache line on another CPU.

**Thread-to-cell mapping:** Uses `Thread.probe` (a per-thread random hash) to pick a cell. On collision, rehashes. Cell array grows (doubles) on contention, up to `Runtime.getRuntime().availableProcessors()`.

**Throughput:** Near-linear scaling to CPU core count. 8 cores → ~8× throughput vs single AtomicLong.

**Trade-off:** `sum()` is **not atomic** — a thread can increment between two `get()` calls within `sum()`. Use LongAdder when you need throughput and can tolerate brief read inconsistency (metrics, rate counters). Use AtomicLong when you need atomic read-modify-write semantics.

---

## Solution 3: Manual Striped Counter (Custom Variant)

When LongAdder's cell growth heuristic isn't optimal for your workload, implement striping explicitly.

```java
public class StripedCounter {
    private static final int STRIPES = 64;  // power of 2; tune to CPU count
    private final AtomicLong[] cells;
    private static final int MASK = STRIPES - 1;

    public StripedCounter() {
        cells = new AtomicLong[STRIPES];
        for (int i = 0; i < STRIPES; i++) {
            cells[i] = new AtomicLong(0);
        }
    }

    public void increment() {
        // Thread.currentThread().getId() spreads threads across stripes
        int idx = (int)(Thread.currentThread().getId() & MASK);
        cells[idx].incrementAndGet();
    }

    public long get() {
        long sum = 0;
        for (AtomicLong cell : cells) {
            sum += cell.get();
        }
        return sum;
    }
}
```

**Problem:** Without `@Contended` padding, `cells[]` array elements are adjacent in memory. Multiple cells share a cache line → false sharing. Use Guava's `Striped` or pad manually (see below).

**Manual padding:**
```java
// Pad each long to occupy a full cache line (64 bytes)
@jdk.internal.vm.annotation.Contended
static final class PaddedLong {
    volatile long value = 0;
}
```

---

## Solution 4: Guava Striped (Library Approach)

Guava's `com.google.common.util.concurrent.AtomicLongMap` provides a striped concurrent map where each key has independent contention. Useful for per-key counters (e.g., per-user request counts).

```java
AtomicLongMap<String> counters = AtomicLongMap.create();

// Thread-safe increment per user
counters.incrementAndGet("user:123");

// Thread-safe read
long count = counters.get("user:123");
```

Internally uses a `ConcurrentHashMap<K, AtomicLong>` — each key bucket is independent. For a single counter, use `LongAdder` instead.

---

## Solution 5: Distributed Counter (HLD extension)

When the counter spans multiple nodes (e.g., global request count across 100 API servers):

### Option A: Redis INCR
```
INCR global:request_count   → atomic, single-node
INCRBY global:request_count 100  → batched increment
```
- Atomic at Redis level
- Single Redis node is a bottleneck for extreme rates (>500K ops/s)
- Use Redis Cluster with key hashing: `INCR counter:{shard_id}` + periodic merge

### Option B: Count-Min Sketch / HyperLogLog (approximate)
For **distinct** counts (unique users, unique IPs):
```
PFADD unique_visitors "user:123"   → HyperLogLog add
PFCOUNT unique_visitors            → approximate distinct count (±0.81% error)
PFMERGE total unique:day1 unique:day2  → merge across shards
```
- Memory: 12KB per HyperLogLog regardless of cardinality
- Trade-off: 0.81% error vs exact count requiring O(N) memory

### Option C: Local buffer + periodic flush
Each API server maintains a `LongAdder` locally. A background thread flushes to Redis every 1 second:
```
INCRBY global:count localAdder.sumThenReset()
```
- Reduces Redis write rate by 1000× (1 Redis call per second vs 1M per second)
- Trade-off: up to 1 second lag in global count visibility

---

## Comparison Table

| Approach | Throughput | Exact Read | Use Case |
|---|---|---|---|
| `synchronized` | Low | Yes | Simple, low concurrency |
| `AtomicLong` | Medium | Yes | < 50 threads |
| `LongAdder` | High | Approx (sum) | Metrics, rate counters |
| Manual `StripedCounter` | High | Approx | Custom stripe count needed |
| Redis `INCR` | Medium (network) | Yes | Cross-process, single node |
| Redis + local buffer | High | Eventual | High-rate distributed |
| HyperLogLog | Very high | ±0.81% | Distinct count only |

---

## When to Use Which

```
Single JVM, < 50 threads → AtomicLong
Single JVM, 50+ threads, writes >> reads → LongAdder
Single JVM, need per-key counters → AtomicLongMap (Guava)
Cross-process, moderate rate → Redis INCR
Cross-process, high rate (>100K/s) → local LongAdder + Redis flush
Distinct count (unique users) → HyperLogLog
Need exact distributed count with strong consistency → distributed transaction (expensive, avoid)
```

---

## Interview Deep-Dives

**Q: Why does LongAdder outperform AtomicLong under contention?**
A: AtomicLong uses a single CAS variable. Under 100-thread contention, threads spin-retry on the same cache line. LongAdder spreads writes across `N` cells (one per CPU). CAS failures on Cell[0] don't affect Cell[1] — each thread mostly succeeds on first try. The cost shifts from retry loops to a slightly more expensive `sum()`.

**Q: When is `sum()` not safe to use?**
A: When you need a consistent snapshot at a specific point in time. `sum()` iterates the cell array; increments can happen between reading Cell[0] and Cell[3]. For billing, use `AtomicLong` or a database transaction. For metrics dashboards, `sum()` is fine.

**Q: How do you implement a rate limiter using these primitives?**
A: Use a `LongAdder` per time window. On each request: `count.increment()`. In a background thread, every second: `long windowCount = count.sumThenReset()`. Compare `windowCount` to rate limit. `sumThenReset()` is a single CAS on the base + individual cell resets — not perfectly atomic but close enough for rate limiting.

**Q: What is false sharing and how does `@Contended` fix it?**
A: CPU cache operates on 64-byte cache lines. If Cell[0] and Cell[1] share a cache line, Thread A updating Cell[0] invalidates Thread B's cached copy of Cell[1], even though they're different logical variables. Thread B must reload the cache line from L3/RAM. `@Contended` adds padding so each cell occupies its own cache line.

---

## Quick Revision

- **Problem**: `count++` is not atomic; `AtomicLong` CAS spins under high contention
- **Solution**: `LongAdder` — stripe across cells, one per CPU; CAS failures become independent
- **Key trade-off**: LongAdder `sum()` is not a point-in-time snapshot → don't use for billing
- **Distributed**: local `LongAdder` + periodic flush to Redis; HyperLogLog for distinct counts
- **False sharing**: pad each cell to 128 bytes with `@Contended` to keep cells on separate cache lines

---

## See Also

- `06-lld/05-problems/24-design-lock-free-queue.md` — CAS mechanics, ABA problem
- `06-lld/05-problems/25-design-concurrent-lru-cache.md` — lock striping, ConcurrentHashMap internals
- `06-lld/04-concurrency/concurrency-patterns.md` — Java Memory Model, happens-before
- `02-building-blocks/rate-limiting.md` — applying counters to token bucket / sliding window
