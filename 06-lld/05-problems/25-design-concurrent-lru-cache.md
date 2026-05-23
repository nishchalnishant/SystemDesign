---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# LLD: Design a Concurrent LRU Cache

> **Implement a thread-safe LRU cache that supports concurrent reads and writes with O(1) get and put, minimizing lock contention for high-throughput scenarios.**

---

## Problem Statement

Design an LRU (Least Recently Used) cache that:
- `get(key)`: returns value if present, -1 otherwise. Marks key as most recently used.
- `put(key, value)`: inserts key-value. If at capacity, evicts least recently used entry.
- Thread-safe: multiple threads call get/put concurrently
- Target: high throughput (10K+ ops/s per thread)

---

## Naive Solution: Single Lock

The simplest thread-safe LRU wraps the entire cache in a `ReentrantLock`.

```java
public class SimpleLRUCache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> cache;
    private final ReentrantLock lock = new ReentrantLock();

    public SimpleLRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {  // accessOrder=true
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        lock.lock();
        try {
            return cache.getOrDefault(key, null);
        } finally {
            lock.unlock();
        }
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            cache.put(key, value);
        } finally {
            lock.unlock();
        }
    }
}
```

**Problem**: Every operation blocks every other operation. With 8 threads, 7 are always waiting. Throughput scales to ~1 thread's worth of work. This is unacceptable for high-concurrency scenarios.

---

## Production Solution: Segment-Based Locking

**Key insight**: Divide the cache into N independent segments. Each segment is a complete, independently-locked LRU. A key maps to exactly one segment via `key.hashCode() % N`. Threads operating on different segments don't block each other.

```
capacity = 1000, segments = 16
segment_capacity = 1000 / 16 = 62 per segment

Thread A: get("user:123")  → segment 7   → acquires lock 7
Thread B: get("user:456")  → segment 11  → acquires lock 11
                                            (both proceed concurrently!)

Thread C: get("user:789")  → segment 7   → blocks on lock 7
                                            (same segment as Thread A)
```

### Full Implementation

```java
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentLRUCache<K, V> {

    private final int segments;
    private final LRUSegment<K, V>[] segmentArray;

    @SuppressWarnings("unchecked")
    public ConcurrentLRUCache(int capacity, int segments) {
        this.segments = segments;
        this.segmentArray = new LRUSegment[segments];
        int segCapacity = Math.max(1, capacity / segments);
        for (int i = 0; i < segments; i++) {
            segmentArray[i] = new LRUSegment<>(segCapacity);
        }
    }

    public ConcurrentLRUCache(int capacity) {
        this(capacity, 16);  // default: 16 segments
    }

    private LRUSegment<K, V> segmentFor(K key) {
        int hash = key.hashCode();
        // Spread hash to avoid clustering
        hash ^= (hash >>> 16);
        return segmentArray[Math.abs(hash % segments)];
    }

    public V get(K key) {
        return segmentFor(key).get(key);
    }

    public void put(K key, V value) {
        segmentFor(key).put(key, value);
    }

    public boolean containsKey(K key) {
        return segmentFor(key).containsKey(key);
    }

    public void remove(K key) {
        segmentFor(key).remove(key);
    }

    // ──────────────────────────────────────────────
    // Individual segment: a complete LRU with its own lock
    // ──────────────────────────────────────────────
    private static class LRUSegment<K, V> {
        private final int capacity;
        private final HashMap<K, Node<K, V>> map;
        private final Node<K, V> head;  // sentinel (MRU side)
        private final Node<K, V> tail;  // sentinel (LRU side)
        private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

        LRUSegment(int capacity) {
            this.capacity = capacity;
            this.map = new HashMap<>(capacity * 2);  // initial capacity avoids rehash
            this.head = new Node<>(null, null);
            this.tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        V get(K key) {
            // Try read lock first for cache hit path (no structural change needed)
            // NOTE: LRU requires moving node to front — must upgrade to write lock
            // Can't upgrade ReentrantReadWriteLock (would deadlock)
            // So use write lock for get() too — access order must be updated
            rwLock.writeLock().lock();
            try {
                Node<K, V> node = map.get(key);
                if (node == null) return null;
                moveToFront(node);
                return node.value;
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        void put(K key, V value) {
            rwLock.writeLock().lock();
            try {
                Node<K, V> existing = map.get(key);
                if (existing != null) {
                    existing.value = value;
                    moveToFront(existing);
                } else {
                    if (map.size() >= capacity) {
                        evictLRU();
                    }
                    Node<K, V> node = new Node<>(key, value);
                    map.put(key, node);
                    addToFront(node);
                }
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        boolean containsKey(K key) {
            rwLock.readLock().lock();
            try {
                return map.containsKey(key);
            } finally {
                rwLock.readLock().unlock();
            }
        }

        void remove(K key) {
            rwLock.writeLock().lock();
            try {
                Node<K, V> node = map.remove(key);
                if (node != null) removeNode(node);
            } finally {
                rwLock.writeLock().unlock();
            }
        }

        private void moveToFront(Node<K, V> node) {
            removeNode(node);
            addToFront(node);
        }

        private void addToFront(Node<K, V> node) {
            node.next = head.next;
            node.prev = head;
            head.next.prev = node;
            head.next = node;
        }

        private void removeNode(Node<K, V> node) {
            node.prev.next = node.next;
            node.next.prev = node.prev;
        }

        private void evictLRU() {
            Node<K, V> lru = tail.prev;
            if (lru == head) return;  // empty cache
            removeNode(lru);
            map.remove(lru.key);
        }

        int size() {
            rwLock.readLock().lock();
            try {
                return map.size();
            } finally {
                rwLock.readLock().unlock();
            }
        }
    }

    // Doubly-linked list node
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
```

---

## Why Read Lock Still Needs Write Lock for `get()`

A common interview mistake: "I'll use read lock for `get()` since I'm only reading."

**Wrong.** LRU `get()` must update the access order (move the node to MRU position). This is a structural write to the linked list. Using a read lock on a write operation causes data corruption with concurrent access.

Two solutions:
1. **Write lock for both get and put** (implemented above): simple, correct, reduced read parallelism
2. **Lazy promotion** (advanced): delay LRU order updates, accept approximate LRU — used by Caffeine

---

## Advanced: Approximate LRU with ConcurrentHashMap

`ConcurrentHashMap` itself uses segment-based locking internally. For a cache that tolerates approximate LRU (not exact recency order), use `ConcurrentHashMap` directly:

```java
public class ApproximateLRUCache<K, V> {
    private final int capacity;
    private final ConcurrentHashMap<K, V> map;

    public ApproximateLRUCache(int capacity) {
        this.capacity = capacity;
        // Initial capacity * 1.5 to avoid resizing
        this.map = new ConcurrentHashMap<>(capacity + capacity / 2);
    }

    public V get(K key) {
        return map.get(key);  // no lock! ConcurrentHashMap handles concurrency
    }

    public void put(K key, V value) {
        if (map.size() >= capacity) {
            // Approximate eviction: pick a random key
            // This is NOT true LRU — but is O(1) lock-free
            K evictKey = map.keys().nextElement();
            map.remove(evictKey);
        }
        map.put(key, value);
    }
}
```

**Trade-off**: No locking → very high throughput. But eviction is random, not LRU. For caches where exact recency doesn't matter (CDN edge cache), acceptable. For caches where hot data must stay in (rate limit counters), not acceptable.

**Caffeine's approach**: Uses a ring buffer to record accesses. A background thread drains the ring buffer and updates LRU order asynchronously. Reads are lock-free (CAS on ring buffer), LRU updates are eventual (processed in batch). This achieves near-LRU behavior with near-lock-free read performance.

---

## Contention Analysis

```
1 segment (naive lock):
  Threads = 8, lock acquisition rate: 1 thread proceeds at a time
  Throughput = 1× baseline

16 segments:
  Each segment handles 1/16 of keys
  Probability two threads hit same segment = 1/16 = 6.25%
  Throughput ≈ 8-12× baseline (contention drops dramatically)
  Diminishing returns beyond √(threads) segments

Rule of thumb: segments = 4× expected peak concurrent threads
  8 threads → 32 segments
  32 threads → 128 segments
```

---

## Thread Safety Test

```java
@Test
public void concurrentGetPutTest() throws InterruptedException {
    ConcurrentLRUCache<Integer, Integer> cache = new ConcurrentLRUCache<>(100);
    int threads = 16;
    int opsPerThread = 10_000;
    CountDownLatch latch = new CountDownLatch(threads);
    
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    
    for (int t = 0; t < threads; t++) {
        final int threadId = t;
        pool.submit(() -> {
            Random rand = new Random(threadId);
            for (int i = 0; i < opsPerThread; i++) {
                int key = rand.nextInt(200);  // key space 2× capacity → 50% hit rate
                if (rand.nextBoolean()) {
                    cache.put(key, key * 2);
                } else {
                    cache.get(key);
                }
            }
            latch.countDown();
        });
    }
    
    latch.await(30, TimeUnit.SECONDS);
    pool.shutdown();
    
    // Invariant: size never exceeds capacity
    // (check each segment)
    // No exception thrown = no race condition
}
```

---

## Interview Extensions

**Q: How would you add TTL (time-to-expiration) per entry?**

Add `long expiresAt` to `Node`. On `get()`, check `System.currentTimeMillis() > node.expiresAt` — if expired, remove and return null. Lazy expiration (check on access) is simpler than eager expiration (background thread). For eager: add a `DelayQueue` or scheduled cleanup per segment.

```java
static class Node<K, V> {
    K key;
    V value;
    long expiresAt;  // System.currentTimeMillis() + ttlMs; -1 = no expiry
    Node<K, V> prev, next;
}
```

**Q: How would you make this distributed (shared cache across JVMs)?**

Switch to Redis. Redis is single-threaded so all commands are serialized — no locking needed by callers. Use Redis `GET`/`SET` with `EX` for TTL. For LRU: Redis supports `maxmemory-policy allkeys-lru` natively. For consistent hashing across Redis Cluster nodes: client library handles shard routing.

**Q: How does `ConcurrentHashMap` work internally (Java 8+)?**

Before Java 8: segment-based locking (similar to our approach, 16 segments default).  
Java 8+: lock striping at individual bucket level. Each bucket (linked list or tree) has its own lock via `synchronized(bucket)`. Reads are lock-free using `volatile` node references. Writes lock only the head of the affected bucket. `size()` uses a `LongAdder` (striped counter) to avoid a single bottleneck counter.

---

## Complexity Summary

| Operation | Time | Space |
|-----------|------|-------|
| get(key) | O(1) amortized | O(capacity) |
| put(key, value) | O(1) amortized | O(capacity) |
| Eviction | O(1) | — |
| Lock contention | O(1/segments) probability | — |

**Space**: O(capacity) for map entries + O(capacity) for linked list nodes = O(capacity) total.

---

## See Also

- **LRU eviction in cache policies**: [02-building-blocks/cache-eviction.md](../../02-building-blocks/cache-eviction.md)
- **Java Memory Model + CAS**: [06-lld/04-concurrency/concurrency-patterns.md](../04-concurrency/concurrency-patterns.md)
- **Lock-free queue (Michael-Scott)**: [06-lld/05-problems/24-design-lock-free-queue.md](24-design-lock-free-queue.md)
