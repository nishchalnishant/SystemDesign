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

```python
from typing import Optional, Dict


class Node:
    def __init__(self, key: int, value: int):
        self.key = key
        self.value = value
        self.prev: Optional['Node'] = None
        self.next: Optional['Node'] = None


class DoublyLinkedList:
    def __init__(self):
        # Sentinels eliminate null checks at boundaries
        self.head = Node(0, 0)  # MRU sentinel
        self.tail = Node(0, 0)  # LRU sentinel
        self.head.next = self.tail
        self.tail.prev = self.head

    def add_to_front(self, node: Node) -> None:
        """Insert node right after head (MRU position)."""
        node.prev = self.head
        node.next = self.head.next
        self.head.next.prev = node
        self.head.next = node

    def remove(self, node: Node) -> None:
        """Remove node from its current position in O(1)."""
        node.prev.next = node.next
        node.next.prev = node.prev
        node.prev = None
        node.next = None

    def remove_last(self) -> Node:
        """Remove and return the LRU node (just before tail)."""
        if self.tail.prev is self.head:
            raise IndexError("List is empty")
        lru = self.tail.prev
        self.remove(lru)
        return lru


class LRUCache:
    def __init__(self, capacity: int):
        if capacity <= 0:
            raise ValueError("Capacity must be positive")
        self.capacity = capacity
        self.cache: Dict[int, Node] = {}
        self.list = DoublyLinkedList()

    def get(self, key: int) -> int:
        if key not in self.cache:
            return -1
        node = self.cache[key]
        self._move_to_front(node)
        return node.value

    def put(self, key: int, value: int) -> None:
        if key in self.cache:
            node = self.cache[key]
            node.value = value
            self._move_to_front(node)
        else:
            node = Node(key, value)
            self.cache[key] = node
            self.list.add_to_front(node)
            if len(self.cache) > self.capacity:
                evicted = self.list.remove_last()
                del self.cache[evicted.key]

    def _move_to_front(self, node: Node) -> None:
        self.list.remove(node)
        self.list.add_to_front(node)
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

```python
import threading

class ThreadSafeLRUCache(LRUCache):
    def __init__(self, capacity: int):
        super().__init__(capacity)
        self._lock = threading.Lock()

    def get(self, key: int) -> int:
        with self._lock:
            return super().get(key)

    def put(self, key: int, value: int) -> None:
        with self._lock:
            super().put(key, value)
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

```python
import time

class TTLNode(Node):
    def __init__(self, key: int, value: int, ttl: Optional[float] = None):
        super().__init__(key, value)
        self.expires_at = time.time() + ttl if ttl else None

    def is_expired(self) -> bool:
        return self.expires_at is not None and time.time() > self.expires_at

class TTLLRUCache(LRUCache):
    def get(self, key: int) -> int:
        if key not in self.cache:
            return -1
        node = self.cache[key]
        if node.is_expired():
            self.list.remove(node)
            del self.cache[key]
            return -1
        self._move_to_front(node)
        return node.value
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
