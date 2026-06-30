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
class DLinkedNode:
- key: K
- value: V
- prev: Optional[DLinkedNode]
- next: Optional[DLinkedNode]
```

### LRUCache (single-lock)

```
class LRUCache:
- capacity: int
- cache: dict[K, DLinkedNode]    # key → node
- head: DLinkedNode              # dummy head (MRU side)
- tail: DLinkedNode              # dummy tail (LRU side)
- lock: threading.RLock

+ get(key: K) -> Optional[V]
+ put(key: K, value: V)
+ _move_to_head(node: DLinkedNode)
+ _remove_node(node: DLinkedNode)
+ _add_to_head(node: DLinkedNode)
+ _pop_tail() -> DLinkedNode
```

### StripedLRUCache

```
class StripedLRUCache:
- segments: list[LRUCache]
- num_segments: int

+ get(key) -> Optional[V]
+ put(key, value)
+ _segment(key) -> LRUCache
```

---

## Implementation

### Single-Lock LRU Cache

```python
class DLinkedNode:
    def __init__(self, key=None, value=None):
        self.key = key
        self.value = value
        self.prev = None
        self.next = None

class LRUCache:
    def __init__(self, capacity):
        self.capacity = capacity
        self.cache = {}
        self.lock = threading.RLock()

        # Sentinel nodes simplify edge cases
        self.head = DLinkedNode()   # MRU sentinel
        self.tail = DLinkedNode()   # LRU sentinel
        self.head.next = self.tail
        self.tail.prev = self.head

    def get(self, key):
        with self.lock:
            if key not in self.cache:
                return None
            node = self.cache[key]
            self._move_to_head(node)
            return node.value

    def put(self, key, value):
        with self.lock:
            if key in self.cache:
                node = self.cache[key]
                node.value = value
                self._move_to_head(node)
            else:
                node = DLinkedNode(key, value)
                self.cache[key] = node
                self._add_to_head(node)
                if len(self.cache) > self.capacity:
                    evicted = self._pop_tail()
                    del self.cache[evicted.key]

    def _add_to_head(self, node):
        node.prev = self.head
        node.next = self.head.next
        self.head.next.prev = node
        self.head.next = node

    def _remove_node(self, node):
        node.prev.next = node.next
        node.next.prev = node.prev

    def _move_to_head(self, node):
        self._remove_node(node)
        self._add_to_head(node)

    def _pop_tail(self):
        lru = self.tail.prev   # the real last node
        self._remove_node(lru)
        return lru
```

### Striped LRU Cache

Each segment has its own `LRUCache` (with its own lock and its own capacity = total_capacity / num_segments). Key → segment mapping by hash. Only the relevant segment is locked per operation.

```python
class StripedLRUCache:
    def __init__(self, total_capacity, num_segments=16):
        self.num_segments = num_segments
        segment_capacity = max(1, total_capacity // num_segments)
        self.segments = [LRUCache(segment_capacity) for _ in range(num_segments)]

    def _segment(self, key):
        return self.segments[hash(key) % self.num_segments]

    def get(self, key):
        return self._segment(key).get(key)

    def put(self, key, value):
        self._segment(key).put(key, value)
```

**Trade-off**: Striped locking reduces lock contention by 1/num_segments. Trade-off: the total effective capacity is `num_segments × segment_capacity`, but items are not redistributed across segments — one segment may be full while another is empty (uneven distribution for skewed key spaces).

### Read-Write Lock variant for read-heavy workloads

For read-heavy caches, use `RWLock`: multiple readers can hold simultaneously, writer holds exclusively. In Python, use `threading.Lock` + a reader count:

```python
class ReadWriteLock:
    def __init__(self):
        self._read_lock = threading.Lock()
        self._write_lock = threading.Lock()
        self._readers = 0

    def acquire_read(self):
        with self._read_lock:
            self._readers += 1
            if self._readers == 1:
                self._write_lock.acquire()

    def release_read(self):
        with self._read_lock:
            self._readers -= 1
            if self._readers == 0:
                self._write_lock.release()

    def acquire_write(self):
        self._write_lock.acquire()

    def release_write(self):
        self._write_lock.release()
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

Add `expires_at: datetime` to `DLinkedNode`. On `get`, check if `expires_at < now` → treat as cache miss, remove node. Run a background "janitor" thread that periodically scans the LRU end (where old items accumulate) and removes expired entries.

```python
def get(self, key):
    with self.lock:
        if key not in self.cache:
            return None
        node = self.cache[key]
        if node.expires_at and datetime.utcnow() > node.expires_at:
            self._remove_node(node)
            del self.cache[key]
            return None
        self._move_to_head(node)
        return node.value
```

### 4. "What's the difference between LRU and LFU eviction?"

**LRU** (Least Recently Used): evicts the item not accessed for the longest time. Tracks recency — good for temporal locality.

**LFU** (Least Frequently Used): evicts the item accessed the fewest times. Tracks frequency — better for stable hot items. Implementation: frequency → doubly linked list of nodes at that frequency. More complex (need min-frequency pointer). Used by Redis with approximation.

---

## Interviewer Questions by Level

**Junior**: LRU cache with OrderedDict. get/put in O(1). Capacity enforcement with eviction.

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
