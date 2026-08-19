> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Concurrent LRU Cache — takes the standard LRU cache and asks "how do we make this thread-safe without locking the whole structure and killing performance?"
>
> **Key concepts:**
> - The Problem: Wrapping the whole LRU (HashMap + DoublyLinkedList) in a `synchronized` block makes it thread-safe, but limits throughput to 1 thread at a time.
> - Striped Locking (The Solution): Divide the cache into $N$ separate "segments" (e.g., 16 segments). 
> - Hashing to Segments: Use `hash(key) % N` to determine which segment a key belongs to.
> - Segment Isolation: Each segment has its own independent `HashMap`, `DoublyLinkedList`, and `ReentrantLock`.
> - Concurrency: Thread A accessing Segment 2 and Thread B accessing Segment 5 can proceed entirely in parallel without blocking each other.
>
> **Key takeaway:** This is the exact design of Java's pre-8 `ConcurrentHashMap`. Explain that an LRU requires *both* a map and a linked list to be updated atomically, which is why you can't just use a `ConcurrentHashMap` out of the box (you still need to lock the segment to update the linked list pointers).

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, concurrent-lru-cache, striped-locking, doubly-linked-list, thread-safety]
---
# Design Concurrent LRU Cache

> **Difficulty**: Hard
> **Asked at**: Amazon, Google, Uber
> **Key Patterns**: LRU eviction (DoublyLinkedList + HashMap), Striped locking (segment-level), Thread safety

---

## Understanding the Problem

Design a thread-safe LRU cache with O(1) get/put. Support concurrent access from multiple threads without a single global lock (which would serialize all requests).

---

## Clarifying Questions

**You**: "What are the performance goals — can we use a single global lock?"
**Interviewer**: "Single lock is acceptable for a first pass. Discuss striped locking as an optimization."

**You**: "What should happen on cache miss?"
**Interviewer**: "Return None (or -1 for integer-key caches). Caller handles the miss."

**You**: "Should eviction be LRU strictly?"
**Interviewer**: "Yes — evict the least recently used item when capacity is full."

**You**: "Can keys and values be any type?"
**Interviewer**: "Yes — generic K/V."

**You**: "Thread model — read-heavy, write-heavy, or balanced?"
**Interviewer**: "Read-heavy. Optimize for concurrent reads."

---

## Final Requirements

**In scope:**
1. `get(key)` → value or None; moves key to MRU position
2. `put(key, value)` → insert/update; evict LRU if at capacity
3. Thread-safe under concurrent access
4. O(1) get and put

**Out of scope:**
- TTL / expiry
- Distributed cache
- Persistence
- Cache-aside pattern (loading strategy)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `DLinkedNode` | Doubly linked list node: key, value, prev, next |
| `LRUCache` | HashMap + DLL; single-lock version |
| `StripedLRUCache` | Segments each with own LRUCache + lock; key → segment by hash |
| `CacheSegment` | One LRUCache shard + its own RLock |

The DLL tracks recency: MRU at head, LRU at tail (between sentinel dummy nodes). HashMap gives O(1) access to any node. Moving a node to head is O(1) with prev/next pointers.

---

## Class Design

### DLinkedNode

```
class DLinkedNode<K, V>:
- key: K
- value: V
- prev: DLinkedNode<K, V> or null
- next: DLinkedNode<K, V> or null
```

### LRUCache (single-lock)

```
class LRUCache<K, V>:
- capacity: int
- cache: Map<K, DLinkedNode<K, V>>    # key -> node
- head: DLinkedNode<K, V>             # dummy head (MRU side)
- tail: DLinkedNode<K, V>             # dummy tail (LRU side)
- lock: ReentrantLock

+ get(key: K): V or null
+ put(key: K, value: V): void
+ moveToHead(node: DLinkedNode<K, V>): void
+ removeNode(node: DLinkedNode<K, V>): void
+ addToHead(node: DLinkedNode<K, V>): void
+ popTail(): DLinkedNode<K, V>
```

### StripedLRUCache

```
class StripedLRUCache<K, V>:
- segments: List<LRUCache<K, V>>
- numSegments: int

+ get(key: K): V or null
+ put(key: K, value: V): void
+ segment(key: K): LRUCache<K, V>
```

---

## Implementation

### Single-Lock LRU Cache

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class LRUCache<K, V> {

    private static class DLinkedNode<K, V> {
        K key;
        V value;
        DLinkedNode<K, V> prev;
        DLinkedNode<K, V> next;

        DLinkedNode() {}

        DLinkedNode(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<K, DLinkedNode<K, V>> cache = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    // Sentinel nodes simplify edge cases
    private final DLinkedNode<K, V> head = new DLinkedNode<>();   // MRU sentinel
    private final DLinkedNode<K, V> tail = new DLinkedNode<>();   // LRU sentinel

    public LRUCache(int capacity) {
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        lock.lock();
        try {
            DLinkedNode<K, V> node = cache.get(key);
            if (node == null) {
                return null;
            }
            moveToHead(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            DLinkedNode<K, V> node = cache.get(key);
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                DLinkedNode<K, V> newNode = new DLinkedNode<>(key, value);
                cache.put(key, newNode);
                addToHead(newNode);
                if (cache.size() > capacity) {
                    DLinkedNode<K, V> evicted = popTail();
                    cache.remove(evicted.key);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void addToHead(DLinkedNode<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(DLinkedNode<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(DLinkedNode<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    private DLinkedNode<K, V> popTail() {
        DLinkedNode<K, V> lru = tail.prev;   // the real last node
        removeNode(lru);
        return lru;
    }
}
```

### Striped LRU Cache

Each segment has its own `LRUCache` (with its own lock and its own capacity = total_capacity / num_segments). Key → segment mapping by hash. Only the relevant segment is locked per operation.

```java
import java.util.ArrayList;
import java.util.List;

public class StripedLRUCache<K, V> {

    private final List<LRUCache<K, V>> segments;
    private final int numSegments;

    public StripedLRUCache(int totalCapacity, int numSegments) {
        this.numSegments = numSegments;
        int segmentCapacity = Math.max(1, totalCapacity / numSegments);
        this.segments = new ArrayList<>(numSegments);
        for (int i = 0; i < numSegments; i++) {
            segments.add(new LRUCache<>(segmentCapacity));
        }
    }

    private LRUCache<K, V> segment(K key) {
        int idx = Math.floorMod(key.hashCode(), numSegments);
        return segments.get(idx);
    }

    public V get(K key) {
        return segment(key).get(key);
    }

    public void put(K key, V value) {
        segment(key).put(key, value);
    }
}
```

**Trade-off**: Striped locking reduces lock contention by 1/num_segments. Trade-off: the total effective capacity is `num_segments × segment_capacity`, but items are not redistributed across segments — one segment may be full while another is empty (uneven distribution for skewed key spaces).

### Read-Write Lock variant for read-heavy workloads

For read-heavy caches, use a read-write lock: multiple readers can hold simultaneously, writer holds exclusively. Java ships this directly as `java.util.concurrent.locks.ReentrantReadWriteLock` — no hand-rolled reader-count bookkeeping needed:

```java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ReadWriteLockedCache {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public void acquireRead() {
        rwLock.readLock().lock();
    }

    public void releaseRead() {
        rwLock.readLock().unlock();
    }

    public void acquireWrite() {
        rwLock.writeLock().lock();
    }

    public void releaseWrite() {
        rwLock.writeLock().unlock();
    }
}
```

For educational purposes, here is what `ReentrantReadWriteLock` does internally (a hand-rolled version using an intrinsic monitor + reader count, matching the naive reader-count approach some languages use before reaching for a library primitive):

```java
public class NaiveReadWriteLock {
    private final Object monitor = new Object();
    private int readers = 0;
    private boolean writerActive = false;

    public void acquireRead() {
        synchronized (monitor) {
            while (writerActive) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            readers++;
        }
    }

    public void releaseRead() {
        synchronized (monitor) {
            readers--;
            if (readers == 0) {
                monitor.notifyAll();
            }
        }
    }

    public void acquireWrite() {
        synchronized (monitor) {
            while (writerActive || readers > 0) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            writerActive = true;
        }
    }

    public void releaseWrite() {
        synchronized (monitor) {
            writerActive = false;
            monitor.notifyAll();
        }
    }
}
```

Note: `get` in LRU also writes (moves node to head), so it can't use a pure read lock unless we separate the "lookup" and "recency update" operations.

---

## Verification

```
LRUCache(capacity=3)
Initial: head ↔ tail

put(1, 'A'):
  new node N1(1,'A')
  add to head: head ↔ N1 ↔ tail
  cache = {1: N1}

put(2, 'B'):
  new node N2(2,'B')
  head ↔ N2 ↔ N1 ↔ tail
  cache = {1:N1, 2:N2}

put(3, 'C'):
  new node N3(3,'C')
  head ↔ N3 ↔ N2 ↔ N1 ↔ tail
  cache = {1:N1, 2:N2, 3:N3}  [full]

get(1):    # access key 1 → move to head
  head ↔ N1 ↔ N3 ↔ N2 ↔ tail
  return 'A'

put(4, 'D'):  # capacity=3, evict LRU
  LRU = tail.prev = N2 (key=2)
  pop N2 from tail, del cache[2]
  add N4(4,'D') to head
  head ↔ N4 ↔ N1 ↔ N3 ↔ tail
  cache = {1:N1, 3:N3, 4:N4}

get(2) → None  (evicted)
```

---

## Deep Dive & Extensibility

### 1. "Why doesn't a read-only `get` use a read lock?"

In LRU, `get` mutates state — it moves the accessed node to the MRU position (head). So `get` is a write operation. If we want true reader parallelism, we can separate the lookup (read) from the recency update (write). One approach: only update recency asynchronously (background thread), accepting approximate LRU.

### 2. "How does striped locking improve throughput?"

With 1 lock: all threads serialize. With 16 segments: 16 threads can operate in parallel (assuming uniform key distribution). Contention is reduced 16×. The trade-off: capacity is split across segments — global LRU ordering is not maintained.

### 3. "How would you add TTL (expiry) to the cache?"

Add `expiresAt: Instant` to `DLinkedNode`. On `get`, check if `expiresAt` is before `now` → treat as cache miss, remove node. Run a background "janitor" thread that periodically scans the LRU end (where old items accumulate) and removes expired entries.

```java
public V get(K key) {
    lock.lock();
    try {
        DLinkedNode<K, V> node = cache.get(key);
        if (node == null) {
            return null;
        }
        if (node.expiresAt != null && Instant.now().isAfter(node.expiresAt)) {
            removeNode(node);
            cache.remove(key);
            return null;
        }
        moveToHead(node);
        return node.value;
    } finally {
        lock.unlock();
    }
}
```

### 4. "What's the difference between LRU and LFU eviction?"

**LRU** (Least Recently Used): evicts the item not accessed for the longest time. Tracks recency — good for temporal locality.

**LFU** (Least Frequently Used): evicts the item accessed the fewest times. Tracks frequency — better for stable hot items. Implementation: frequency → doubly linked list of nodes at that frequency. More complex (need min-frequency pointer). Used by Redis with approximation.

---

## Interviewer Questions by Level

**Junior**: LRU cache with `LinkedHashMap` (access-order mode). get/put in O(1). Capacity enforcement with eviction (override `removeEldestEntry`).

**Mid-level**: Doubly linked list + HashMap implementation. Sentinel dummy nodes. Thread-safe with a single RLock. O(1) move-to-head via prev/next pointers.

**Senior**: Striped locking (16 segments) for concurrency. Trade-off: global LRU ordering lost. Read-write lock for read-heavy workloads. TTL support with background janitor. LRU vs LFU comparison.

---

## Common Interview Questions

- **Q**: Why use a doubly linked list + HashMap instead of a simpler structure?
  **A**: HashMap gives O(1) key lookup. DLL gives O(1) node removal and insertion (given a pointer to the node — no search needed). Combined: O(1) get and put. A singly linked list would require O(n) to find the previous node for removal.

- **Q**: What are the sentinel dummy nodes for?
  **A**: Sentinel head and tail eliminate null checks. Add to head always inserts after head.next. Pop from tail always removes tail.prev. No special cases for empty list or single-element list.

- **Q**: Why is a single global lock a problem?
  **A**: All threads serialize — throughput is capped at 1 operation at a time regardless of the number of CPUs. Under high concurrency, threads spend most of their time waiting, not working.

- **Q**: How does striped locking work?
  **A**: Split the cache into N segments (e.g., 16). Each segment has its own lock and its own sub-cache. A key maps to a segment via `hash(key) % N`. Threads accessing different segments never contend. Throughput scales linearly with segments (up to N).

- **Q**: Does `get` need a write lock in LRU?
  **A**: Yes. `get` moves the node to the MRU position, which modifies the DLL (two pointer updates + two pointer updates on neighboring nodes) and conceptually the ordering. This is a write to shared state — needs a write lock, not a read lock.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Interface Segregation](../../02-solid-principles/04-interface-segregation.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Thread-Safe Singleton](../../04-concurrency/thread-safe-singleton.md)

**Practice next**

- [Design an LRU Cache](../02-frequent-problems/13-design-lru-cache.md)
- [Design a Lock-Free Queue](34-design-lock-free-queue.md)

Do the single-threaded LRU first, then add locking.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../01-oop-fundamentals/uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
