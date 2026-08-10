> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design an LRU Cache — arguably the most famous data structure interview question. Tests your ability to combine primitive data structures to achieve O(1) time complexity for complex operations.
>
> **Key concepts:**
> - Core Data Structures: A `HashMap` (for O(1) lookups) + a `DoublyLinkedList` (for O(1) additions and removals).
> - The Nodes: The nodes in the linked list must store BOTH the `key` and the `value` (so when you evict the tail node, you know which key to remove from the HashMap).
> - `get(key)`: If present, return value AND move the node to the front (head) of the list. O(1).
> - `put(key, value)`: If present, update value and move to front. If not present, add to front. If at capacity, remove the tail node (Least Recently Used) from both the list and the map. O(1).
> - Dummy Head/Tail: Using a dummy head and dummy tail node eliminates all null checks when adding/removing nodes, drastically simplifying the code.
>
> **Key takeaway:** Memorize the exact wiring of the `DoublyLinkedList` with dummy head and tail nodes. This question is so common that any hesitation on the pointer wiring is heavily penalized.

---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, lru-cache, doubly-linked-list, hashmap, data-structure]
---
# Design an LRU Cache

> **Difficulty**: Medium  
> **Asked at**: Amazon, Google, Meta, Microsoft  
> **Key Patterns**: Custom data structure (DoublyLinkedList + HashMap)

---

## Understanding the Problem

Design a Least Recently Used (LRU) cache that supports O(1) get and put operations. When the cache reaches capacity, it evicts the least recently used entry before inserting a new one. "Recently used" means accessed (get) or inserted/updated (put).

---

## Clarifying Questions

**You**: "What should get return when a key is not in the cache?"  
**Interviewer**: "Return -1 (or None). Accessing a missing key does not affect recency."

**You**: "On put, if the key already exists, do we update the value and refresh its recency?"  
**Interviewer**: "Yes. Update value and treat it as most recently used."

**You**: "Is this single-threaded or do we need thread safety?"  
**Interviewer**: "Start with single-threaded. Describe how you'd make it thread-safe."

**You**: "Fixed capacity at construction, or dynamically resizable?"  
**Interviewer**: "Fixed capacity set at construction."

**You**: "Any constraints on key/value types?"  
**Interviewer**: "Assume int keys and int values for now."

---

## Final Requirements

**In scope:**
1. `get(key)` → value or -1 if absent; moves key to most-recently-used position
2. `put(key, value)` → insert or update; move to MRU; evict LRU if over capacity
3. O(1) time for both operations
4. Capacity enforced on every put

**Out of scope:**
- TTL / expiry
- Persistence
- Distributed caching
- Generic types (mention in deep dive)

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| Node | Doubly linked list node — holds key, value, prev/next pointers |
| DoublyLinkedList | Maintains order (head = MRU, tail = LRU); O(1) insert/remove with node reference |
| LRUCache | Combines the list with a HashMap for O(1) key lookup |

The key insight: a HashMap gives O(1) access to the node, and the doubly linked list allows O(1) removal and reinsertion of that node at the head. A singly linked list cannot do O(1) removal because you can't reach the previous node.

---

## Class Design

### Node

| Requirement | What Node must track |
|-------------|---------------------|
| Content | key: int, value: int |
| Links | prev: Optional[Node], next: Optional[Node] |

```
class Node:
- key: int
- value: int
- prev: Optional[Node]
- next: Optional[Node]
```

### DoublyLinkedList

```
class DoublyLinkedList:
- head: Node  # sentinel (MRU side)
- tail: Node  # sentinel (LRU side)
+ add_to_front(node: Node) -> None
+ remove(node: Node) -> None
+ remove_last() -> Node  # evict LRU; returns evicted node
```

### LRUCache

| Requirement | What LRUCache must track |
|-------------|--------------------------|
| Fast lookup | cache: Dict[int, Node] |
| Order | list: DoublyLinkedList |
| Bounds | capacity: int |

```
class LRUCache:
- capacity: int
- cache: Dict[int, Node]
- list: DoublyLinkedList
+ get(key: int) -> int
+ put(key: int, value: int) -> None
- _move_to_front(node: Node) -> None
```

---

## Implementation

### Core Methods: get and put

**get core logic:**
1. Look up key in `cache` dict
2. If absent, return -1
3. If present, move node to front of list (mark as MRU)
4. Return node.value

**put core logic:**
1. If key exists: update value, move to front
2. If key is new:
   a. Create new node, add to front, add to cache dict
   b. If `len(cache) > capacity`: remove last node from list, delete its key from cache dict

**Edge cases:**
- capacity = 0 → never store anything; get always returns -1
- Updating existing key → must not double-count in size; move to front only

```java
import java.util.HashMap;
import java.util.Map;

class Node {
    int key;
    int value;
    Node prev;
    Node next;

    Node(int key, int value) {
        this.key = key;
        this.value = value;
        this.prev = null;
        this.next = null;
    }
}

class DoublyLinkedList {
    private final Node head; // MRU sentinel
    private final Node tail; // LRU sentinel

    DoublyLinkedList() {
        // Sentinels eliminate null checks at boundaries
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    /** Insert node right after head (MRU position). */
    void addToFront(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    /** Remove node from its current position in O(1). */
    void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }

    /** Remove and return the LRU node (just before tail). */
    Node removeLast() {
        if (tail.prev == head) {
            throw new IllegalStateException("List is empty");
        }
        Node lru = tail.prev;
        remove(lru);
        return lru;
    }
}

class LRUCache {
    protected final int capacity;
    protected final Map<Integer, Node> cache;
    protected final DoublyLinkedList list;

    LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.list = new DoublyLinkedList();
    }

    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        Node node = cache.get(key);
        moveToFront(node);
        return node.value;
    }

    public void put(int key, int value) {
        if (cache.containsKey(key)) {
            Node node = cache.get(key);
            node.value = value;
            moveToFront(node);
        } else {
            Node node = new Node(key, value);
            cache.put(key, node);
            list.addToFront(node);
            if (cache.size() > capacity) {
                Node evicted = list.removeLast();
                cache.remove(evicted.key);
            }
        }
    }

    protected void moveToFront(Node node) {
        list.remove(node);
        list.addToFront(node);
    }
}
```

---

## Verification

Trace: capacity=2, operations: put(1,1), put(2,2), get(1), put(3,3), get(2).

1. `put(1,1)` → cache={1:N1}, list: head ↔ N1(1,1) ↔ tail
2. `put(2,2)` → cache={1:N1,2:N2}, list: head ↔ N2(2,2) ↔ N1(1,1) ↔ tail
3. `get(1)` → found, move N1 to front → list: head ↔ N1(1,1) ↔ N2(2,2) ↔ tail; returns 1
4. `put(3,3)` → new key, size would be 3 > capacity 2; evict LRU (N2, key=2); cache={1:N1,3:N3}, list: head ↔ N3(3,3) ↔ N1(1,1) ↔ tail
5. `get(2)` → not in cache → returns -1 ✓

---

## Deep Dive & Extensibility

### 1. "Why doubly linked list + hashmap? Why not just a list or just a map?"

**Just a hashmap**: O(1) get/put, but no ordering — you can't identify the LRU entry in O(1).

**Just a list (singly linked)**: O(1) insert at head, O(n) access by key (must scan), O(n) removal (can't reach prev node in O(1)).

**Doubly linked list alone**: O(1) insert/remove, but O(n) lookup by key (scan from head).

**Both together**: HashMap provides O(1) key → node lookup. Doubly linked list provides O(1) remove (via prev pointer) and O(1) insert at head. The node stores both key and value — key is needed so that when we evict the tail, we know which key to delete from the hashmap.

Singly linked list fails because removing a node requires knowing the previous node, which requires O(n) traversal.

### 2. "How do you make LRU thread-safe?"

**Option 1 — synchronized (coarse-grained)**: Wrap both get and put with a single lock. Simple, correct, but a bottleneck under high concurrency.

```java
import java.util.concurrent.locks.ReentrantLock;

class ThreadSafeLRUCache extends LRUCache {
    private final ReentrantLock lock = new ReentrantLock();

    ThreadSafeLRUCache(int capacity) {
        super(capacity);
    }

    @Override
    public int get(int key) {
        lock.lock();
        try {
            return super.get(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(int key, int value) {
        lock.lock();
        try {
            super.put(key, value);
        } finally {
            lock.unlock();
        }
    }
}
```

**Option 2 — read/write lock**: Multiple readers can proceed concurrently; writers take exclusive lock. `get` acquires read lock, `put` acquires write lock. But get also mutates (moves node to front) so it needs a write lock too. True read/write separation only works if reads don't update recency.

**Option 3 — sharded LRU**: Partition keys across N independent LRU caches, each with its own lock. Reduces contention by factor of N.

**Option 4 — ConcurrentLinkedHashMap**: Java-style approach using CAS operations on the linked list and concurrent HashMap segments. Complex but highest throughput.

### 3. "LRU vs LFU — when do you choose each?"

| Property | LRU | LFU |
|----------|-----|-----|
| Eviction criterion | Least recently accessed | Least frequently accessed |
| Complexity | O(1) get/put | O(1) get/put (with frequency heap) |
| Cache pollution | Scans evict useful items | Old-but-popular items stick around |
| Best for | Temporal locality (recent = relevant) | Frequency locality (popular = relevant) |
| Worst case | One-time sequential scan evicts everything | New items evicted immediately (frequency=1) |

LFU needs an additional frequency → doubly-linked-list-of-nodes structure. New items start at frequency 1, so under write-heavy loads they get evicted before proving useful.

Use LRU for most web caches. Use LFU for content delivery or recommendation systems where item popularity matters more than recency.

### 4. "How would you implement a distributed LRU cache?"

Core challenge: ordering across nodes. Options:

**Central coordinator**: One node owns the LRU list; others call it on every access. Single point of failure, bottleneck.

**Consistent hashing**: Each key maps to one node. That node runs its own LRU. Global eviction order is approximate — no cross-node LRU. Redis cluster works this way.

**Approximate LRU (Redis's approach)**: On eviction, sample K random keys, evict the one with the oldest access timestamp. O(K) per eviction, approximate LRU without a global list.

### 5. "How would you add TTL (time-to-live) to the cache?"

Add an `expires_at: Optional[float]` field to Node. On get, check if the node has expired before returning it.

```java
class TTLNode extends Node {
    Long expiresAt; // null means no expiry

    TTLNode(int key, int value, Long ttlMillis) {
        super(key, value);
        this.expiresAt = (ttlMillis != null) ? System.currentTimeMillis() + ttlMillis : null;
    }

    boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() > expiresAt;
    }
}

class TTLLRUCache extends LRUCache {
    TTLLRUCache(int capacity) {
        super(capacity);
    }

    @Override
    public int get(int key) {
        if (!cache.containsKey(key)) {
            return -1;
        }
        TTLNode node = (TTLNode) cache.get(key);
        if (node.isExpired()) {
            list.remove(node);
            cache.remove(key);
            return -1;
        }
        moveToFront(node);
        return node.value;
    }
}
```

For proactive expiry (lazy expiry can accumulate stale entries), run a background sweeper that scans the tail of the list (oldest entries are most likely expired).

---

## Interviewer Questions by Level

**Junior**: Why do we need to store the key inside the Node? What breaks if we only store the value?

**Mid-level**: Walk me through what happens to the doubly linked list pointers when we call `_move_to_front`. Draw it out for a 3-node list.

**Senior**: Describe the full design of a distributed LRU cache that supports 1M ops/sec across 10 nodes. What consistency guarantees can you provide?

---

## Common Interview Questions

- **Q: Why does LRU require a doubly linked list instead of singly linked?**  
  A: Removing a node from the middle of a singly linked list requires knowing the previous node. Without a prev pointer, finding it takes O(n). Doubly linked list gives O(1) removal with direct node reference.

- **Q: Why do we use sentinel head/tail nodes?**  
  A: Eliminates null checks and special-cases for empty list, adding to empty list, or removing the only element. All pointer operations are uniform.

- **Q: What breaks if you use Python's OrderedDict instead?**  
  A: OrderedDict.move_to_end() gives O(1) reordering, and popitem(last=False) gives O(1) LRU eviction. It's essentially the same data structure — Python's OrderedDict is implemented with a doubly linked list + dict internally.

- **Q: LRU vs FIFO — which is better?**  
  A: LRU is better for most real workloads because it exploits temporal locality (recently used items are likely to be used again). FIFO is simpler but ignores access patterns.

- **Q: How would you implement LRU with a fixed size in Java?**  
  A: Extend LinkedHashMap with `removeEldestEntry` overridden to return true when size exceeds capacity. Or implement from scratch with LinkedHashMap in access-order mode.

- **Q: Can you implement LRU with O(1) operations using only a Python dict?**  
  A: Yes, using OrderedDict. `move_to_end(key)` is O(1) and `popitem(last=False)` is O(1). The built-in dict in Python 3.7+ preserves insertion order but lacks move_to_end — OrderedDict is the right choice.

- **Q: What's the memory overhead of this implementation?**  
  A: Each node uses 5 fields (key, value, prev, next, plus Python object overhead ~56 bytes). Plus 2 dict entries (hashmap key → node pointer). Roughly 200-300 bytes per cache entry in CPython.

---

## Concurrency Test Harness

Runnable tests that verify thread-safety of a reader-writer LRU cache. No external deps — stdlib only.

```java
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Random;

// ── Minimal thread-safe LRU implementation (self-contained) ──

class Node {
    int key;
    int value;
    Node prev;
    Node next;

    Node() {
        this(0, 0);
    }

    Node(int key, int value) {
        this.key = key;
        this.value = value;
        this.prev = null;
        this.next = null;
    }
}

class LRUCache {
    private final int capacity;
    final Map<Integer, Node> cache;
    final ReentrantLock lock = new ReentrantLock();
    final Node head; // sentinel MRU
    final Node tail; // sentinel LRU

    LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.head = new Node();
        this.tail = new Node();
        head.next = tail;
        tail.prev = head;
    }

    private void remove(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void addToFront(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    public int get(int key) {
        lock.lock();
        try {
            if (!cache.containsKey(key)) {
                return -1;
            }
            Node node = cache.get(key);
            remove(node);
            addToFront(node);
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    public void put(int key, int value) {
        lock.lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                node.value = value;
                remove(node);
                addToFront(node);
            } else {
                if (cache.size() == capacity) {
                    Node lru = tail.prev;
                    remove(lru);
                    cache.remove(lru.key);
                }
                Node node = new Node(key, value);
                cache.put(key, node);
                addToFront(node);
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }
}

public class LRUCacheConcurrencyTest {

    // ─────────────────────────────────────────────────────────────
    // TEST 1: Capacity never exceeded under concurrent writes
    // 500 threads each put a unique key. Cache capacity is 100.
    // At all times, cache.size() must not exceed capacity.
    // ─────────────────────────────────────────────────────────────
    static void testCapacityNeverExceeded() throws InterruptedException {
        LRUCache cache = new LRUCache(100);
        CopyOnWriteArrayList<String> errors = new CopyOnWriteArrayList<>();

        Thread[] threads = new Thread[500];
        for (int i = 0; i < 500; i++) {
            final int key = i;
            threads[i] = new Thread(() -> {
                cache.put(key, key * 10);
                int sz = cache.size();
                if (sz > 100) {
                    errors.add("Capacity exceeded: " + sz);
                }
            });
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assert errors.isEmpty() : "Invariant violated: " + errors;
        assert cache.size() <= 100;
        System.out.println("PASS: testCapacityNeverExceeded");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2: No structural corruption under concurrent reads + writes
    // Mixed threads do random get/put on overlapping keys.
    // The doubly linked list must remain intact (no cycles, no null ptr deref).
    // ─────────────────────────────────────────────────────────────
    static void testNoStructuralCorruption() throws InterruptedException {
        LRUCache cache = new LRUCache(20);
        Random random = new Random();

        Runnable worker = () -> {
            Random r = new Random();
            for (int i = 0; i < 200; i++) {
                int key = r.nextInt(30);
                if (r.nextDouble() < 0.5) {
                    cache.put(key, key);
                } else {
                    cache.get(key);
                }
            }
        };

        Thread[] threads = new Thread[20];
        for (int i = 0; i < 20; i++) {
            threads[i] = new Thread(worker);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        // Validate list structure under the lock
        cache.lock.lock();
        try {
            Set<Integer> visited = new HashSet<>();
            Node node = cache.head.next;
            int count = 0;
            while (node != cache.tail) {
                assert !visited.contains(node.key) : "Cycle detected in LRU list";
                visited.add(node.key);
                assert cache.cache.containsKey(node.key) : "Node key " + node.key + " not in cache dict";
                node = node.next;
                count++;
            }
            assert count == cache.cache.size() : "List length != dict length";
        } finally {
            cache.lock.unlock();
        }

        System.out.println("PASS: testNoStructuralCorruption");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3: LRU eviction order — most recently used survives
    // Fill cache to capacity with keys 0..N-1.
    // Access keys 0..49 to make them MRU.
    // Put 50 new keys — keys 50..99 (the LRU ones) should be evicted.
    // ─────────────────────────────────────────────────────────────
    static void testLruEvictionOrder() {
        LRUCache cache = new LRUCache(100);
        for (int i = 0; i < 100; i++) {
            cache.put(i, i);
        }

        // Access keys 0-49 to make them MRU
        for (int i = 0; i < 50; i++) {
            cache.get(i);
        }

        // Insert 50 new keys — should evict keys 50-99 (LRU)
        for (int i = 100; i < 150; i++) {
            cache.put(i, i);
        }

        // Keys 0-49 should still be present (MRU)
        for (int i = 0; i < 50; i++) {
            assert cache.get(i) == i : "Key " + i + " was evicted but should be MRU";
        }

        // Keys 50-99 should be gone (were LRU when new keys inserted)
        for (int i = 50; i < 100; i++) {
            assert cache.get(i) == -1 : "Key " + i + " should have been evicted";
        }

        System.out.println("PASS: testLruEvictionOrder");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 4: Concurrent readers see consistent values
    // Pre-load keys. 100 reader threads concurrently get the same keys.
    // All must return the correct value (not -1, not corrupted).
    // ─────────────────────────────────────────────────────────────
    static void testConcurrentReadsConsistent() throws InterruptedException {
        LRUCache cache = new LRUCache(50);
        for (int i = 0; i < 50; i++) {
            cache.put(i, i * 100);
        }

        CopyOnWriteArrayList<int[]> badReads = new CopyOnWriteArrayList<>();

        Runnable reader = () -> {
            for (int key = 0; key < 50; key++) {
                int val = cache.get(key);
                if (val != key * 100) {
                    badReads.add(new int[] { key, val });
                }
            }
        };

        Thread[] threads = new Thread[100];
        for (int i = 0; i < 100; i++) {
            threads[i] = new Thread(reader);
        }
        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        assert badReads.isEmpty() : "Inconsistent reads found: " + badReads.size();
        System.out.println("PASS: testConcurrentReadsConsistent");
    }

    public static void main(String[] args) throws InterruptedException {
        testCapacityNeverExceeded();
        testNoStructuralCorruption();
        testLruEvictionOrder();
        testConcurrentReadsConsistent();
        System.out.println("All concurrency tests passed.");
    }
}
```

**What each test verifies:**
- `test_capacity_never_exceeded`: The single `threading.Lock()` in `put` ensures eviction and insertion are atomic; without it, two threads can both see `len == capacity` and both insert, exceeding the limit.
- `test_no_structural_corruption`: Concurrent pointer manipulation of the doubly linked list without a lock causes torn reads — a thread can follow a `next` pointer mid-update and reach a detached node, causing `None` dereference or cycles.
- `test_lru_eviction_order`: Validates that the list's MRU-to-LRU ordering is preserved correctly across concurrent operations; the evicted keys are always the least recently used ones.
- `test_concurrent_reads_consistent`: Readers must not see `-1` for keys still in cache; the lock prevents a reader from observing a partially-removed node during a concurrent eviction.

---

## Related

**Patterns applied here**

- [Strategy Pattern](../../03-design-patterns/03-behavioral/strategy-pattern.md)

**SOLID focus**: [Single Responsibility](../../02-solid-principles/01-single-responsibility.md) · [Interface Segregation](../../02-solid-principles/04-interface-segregation.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md)

**Practice next**

- [Design a Concurrent LRU Cache](../04-advanced-niche/35-design-concurrent-lru-cache.md)
- [Design S3 Object Storage](../04-advanced-niche/26-design-s3-object-storage.md)

The concurrent variant adds locking to this exact structure.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
