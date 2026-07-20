---
id: contention-reduction
tags: [lld, concurrency, java, performance]
confidence: 3
last-rehearsed: 2026-07-21
source: 6. Scalability — "Shared mutable state is the serial fraction" + "Avoid coordination"
---
# Contention reduction

**Claim in one sentence.** Every `synchronized` block on a shared object is Amdahl's serial fraction in code form — and the senior move is to avoid coordination rather than optimize it.

## The escalation

```java
// Every increment serializes on one lock
private long count;
public synchronized void increment() { count++; }

// CAS — better, still one contended cache line
private final AtomicLong count = new AtomicLong();

// Striped — threads touch different cells, contention collapses
private final LongAdder count = new LongAdder();
```

`AtomicLong` removes the lock but not the contention: every thread still CASes the *same* cache line, so under high write load they invalidate each other's caches continuously and the retry loop spins. The bottleneck moved from the lock to the memory subsystem.

`LongAdder` maintains per-thread cells summed on read. It trades read cost for write scalability — dramatically faster under high write contention, and the direct LLD expression of "reduce the serial fraction."

## Avoid coordination, don't optimize it

USL's β term in code — prefer designs where threads don't talk:

- **Thread-local accumulation, merged at the end** beats a shared structure with a lock
- **Immutable objects** need no synchronization at all — the strongest form of the idea
- **Copy-on-write** suits extreme read/write ratios (as in the [backend registry](../load-balancing/backend-registry-cow.md))
- **Sharded locks** beat one global lock — the same insight as database sharding, one level down

The pattern across all four: change the data layout so the coordination isn't needed, rather than making the coordination faster. A faster lock is still a serial section.

## What you say in an interview

> "A synchronized counter is the serial fraction in code — it caps throughput regardless of core count. `AtomicLong` removes the lock but every thread still contends on one cache line, so under heavy writes it spins. `LongAdder` stripes across per-thread cells and sums on read, which is the right trade when writes dominate. More generally I'd rather remove the coordination than speed it up — thread-local accumulation, immutability, sharded locks."

## When LongAdder is wrong

Reads are O(number of cells) and the sum is not atomic — you get a value that was never simultaneously true across all cells. Fine for metrics and counters. Wrong when you need an exact instantaneous value to make a decision on, which is why the [connection counter](../load-balancing/backend-registry-cow.md) uses `AtomicInteger`: it's read on every routing decision and must be exact.

## Probes you should survive

- *"When is `synchronized` fine?"* → Low contention — an uncontended lock is cheap, roughly a CAS on the object header, and the JIT can elide it entirely when escape analysis proves the object is thread-local. Optimize when you've measured contention, not before. **Don't credit biased locking** for this: it was disabled by default in JDK 15 ([JEP 374](https://openjdk.org/jeps/374)), obsoleted in 18, and the flag is unrecognized in current JDKs. Citing it as live behavior dates you.
- *"Why is one cache line a bottleneck?"* → Every CAS invalidates that line in every other core's cache. Cores spend their time on coherence traffic rather than work — false-sharing-like behavior on a genuinely shared value.
- *"When would you not use `LongAdder`?"* → When reads are frequent or must be exact. Its sum is O(cells) and not a consistent snapshot.
- *"Sharded locks — how many shards?"* → Roughly the concurrency level; more shards, less contention, more memory. Same shape as choosing a partition count.

## Related

[amdahl-and-usl](./amdahl-and-usl.md) · [backend-registry-cow](../load-balancing/backend-registry-cow.md) · [concurrency-in-contract](../api-design/concurrency-in-contract.md) · [hot-keys](../caching/hot-keys.md)
