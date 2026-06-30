---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, lld, lock-free-queue, cas, aba-problem, michael-scott-queue]
---
# Design Lock-Free Queue

> **Difficulty**: Hard
> **Asked at**: Amazon, Jane Street, Two Sigma
> **Key Patterns**: Compare-and-Swap (CAS), ABA problem, Michael-Scott queue algorithm

---

## Understanding the Problem

Design a lock-free FIFO queue that supports concurrent enqueue and dequeue operations without using mutexes or locks. In Python (which lacks real CAS), we discuss the algorithm and simulate it, noting where a real implementation would use `AtomicReference` (Java) or `std::atomic` (C++).

---

## Clarifying Questions

**You**: "What's the concurrency model — multiple producers, multiple consumers?"
**Interviewer**: "MPMC — multiple producer, multiple consumer (the hardest case)."

**You**: "Can we use Python's threading primitives for simulation?"
**Interviewer**: "Yes — explain the lock-free algorithm in detail. Python simulation is acceptable."

**You**: "Should we handle bounded capacity?"
**Interviewer**: "Start with unbounded. Discuss bounded as extension."

**You**: "What's the ABA problem?"
**Interviewer**: "Explain it — it's a key concern with CAS-based data structures."

**You**: "Do we need blocking behavior (wait for item if empty)?"
**Interviewer**: "No — return None if empty. Pure non-blocking."

---

## Final Requirements

**In scope:**
1. Thread-safe enqueue (tail insert)
2. Thread-safe dequeue (head remove)
3. Non-blocking — return None if empty, no busy-wait beyond CAS retries
4. Explain Michael-Scott queue algorithm
5. Explain and handle the ABA problem

**Out of scope:**
- Bounded capacity (follow-up)
- Python GIL considerations
- Memory ordering (C++ memory_order) — mention at senior level

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Node` | Singly linked list node; holds value and atomic `next` pointer |
| `LockFreeQueue` | Head (sentinel) + tail pointers; CAS-based enqueue/dequeue |
| `AtomicRef` | Simulated atomic reference (wraps a value + version for ABA) |

The Michael-Scott queue uses a **sentinel node** (dummy head). Head always points to the sentinel; Tail always points to the last real node (or the sentinel when empty). Both are atomic pointers updated via CAS.

---

## Class Design

### Node

```
class Node:
- value: Any
- next: AtomicRef   # AtomicRef[Optional[Node]]
```

### AtomicRef (simulation)

```python
import threading

class AtomicRef:
    def __init__(self, value=None):
        self._value = value
        self._version = 0
        self._lock = threading.Lock()   # internal lock for simulation only

    def get(self):
        with self._lock:
            return self._value, self._version

    def compare_and_set(self, expected_val, expected_ver, new_val):
        with self._lock:
            if self._value is expected_val and self._version == expected_ver:
                self._value = new_val
                self._version += 1
                return True
            return False
```

In a real implementation (Java): `AtomicReference<Node>`. In C++: `std::atomic<Node*>` with ABA-safe tagged pointers or hazard pointers.

### LockFreeQueue (Michael-Scott Algorithm)

```
class LockFreeQueue:
- head: AtomicRef   # points to sentinel node
- tail: AtomicRef   # points to last node

+ enqueue(value)
+ dequeue() -> Optional[Any]
+ is_empty() -> bool
```

---

## Implementation

### Michael-Scott Queue Algorithm

The key insight: `tail` may lag behind the actual last node. Both enqueue and dequeue must tolerate (and help advance) a lagging tail.

```python
class LockFreeQueue:
    def __init__(self):
        sentinel = Node(value=None, next=AtomicRef(None))
        self.head = AtomicRef(sentinel)
        self.tail = AtomicRef(sentinel)

    def enqueue(self, value):
        new_node = Node(value=value, next=AtomicRef(None))
        while True:
            tail_node, tail_ver = self.tail.get()
            tail_next, tail_next_ver = tail_node.next.get()

            # Consistency check: tail hasn't moved since we read it
            current_tail, current_tail_ver = self.tail.get()
            if tail_node is not current_tail or tail_ver != current_tail_ver:
                continue   # tail moved — retry

            if tail_next is None:
                # Tail is truly the last node — try to append
                if tail_node.next.compare_and_set(None, tail_next_ver, new_node):
                    # Advance tail — best effort (another thread may do it)
                    self.tail.compare_and_set(tail_node, tail_ver, new_node)
                    return
            else:
                # Tail is lagging — help advance it
                self.tail.compare_and_set(tail_node, tail_ver, tail_next)

    def dequeue(self):
        while True:
            head_node, head_ver = self.head.get()
            tail_node, tail_ver = self.tail.get()
            head_next, head_next_ver = head_node.next.get()

            # Consistency check
            current_head, current_head_ver = self.head.get()
            if head_node is not current_head or head_ver != current_head_ver:
                continue

            if head_node is tail_node:
                # Queue appears empty OR tail is lagging
                if head_next is None:
                    return None   # truly empty
                # Tail is lagging behind — help advance it
                self.tail.compare_and_set(tail_node, tail_ver, head_next)
            else:
                # Read value before CAS (node may be reclaimed after CAS)
                value = head_next.value
                if self.head.compare_and_set(head_node, head_ver, head_next):
                    return value
                # CAS failed → another thread dequeued — retry

    def is_empty(self):
        head_node, _ = self.head.get()
        _, _ = head_node.next.get()
        head_next, _ = head_node.next.get()
        return head_next is None
```

### The ABA Problem Explained

ABA occurs when:
1. Thread T1 reads head → points to Node A (value=1)
2. Thread T2 dequeues Node A, then enqueues Node A (same memory address), then dequeues something else — head now points to Node A again
3. T1's CAS succeeds (head is still A!) but the queue state has changed

**Fix**: Tag each atomic reference with a version counter. CAS checks both the pointer AND the version. Even if the same node is re-used, the version is different — CAS fails.

```
AtomicRef stores (value, version):
  Before T2: head = (NodeA, version=5)
  After T2:  head = (NodeA, version=6)   ← version changed
  T1's CAS: expects (NodeA, version=5) → FAILS correctly
```

Our `AtomicRef` simulation already uses `_version` for this purpose. In Java: `AtomicStampedReference<Node>`. In C++: tagged pointer (store version in low bits of pointer).

---

## Verification

```
Queue initialized: sentinel → null
head = tail = sentinel

Thread A: enqueue(10)
  tail=sentinel, tail.next=None
  CAS sentinel.next: None → Node(10) → succeeds
  CAS tail: sentinel → Node(10) → succeeds
  Queue: sentinel → Node(10), head=sentinel, tail=Node(10)

Thread B: enqueue(20)
  tail=Node(10), tail.next=None
  CAS Node(10).next: None → Node(20) → succeeds
  CAS tail: Node(10) → Node(20) → succeeds
  Queue: sentinel → Node(10) → Node(20)

Thread C: dequeue()
  head=sentinel, tail=Node(20), head.next=Node(10)
  head != tail (not empty)
  value = Node(10).value = 10
  CAS head: sentinel → Node(10) → succeeds
  return 10
  Queue: Node(10)[new sentinel] → Node(20)

Thread D: dequeue()
  head=Node(10), head.next=Node(20)
  value = 20
  CAS head: Node(10) → Node(20) → succeeds
  return 20
  Queue: Node(20)[sentinel] → null

Thread E: dequeue()
  head=Node(20), head.next=None, head==tail
  head_next is None → return None (empty)
```

---

## Deep Dive & Extensibility

### 1. "How would you make this bounded (max capacity)?"

Add an `AtomicInteger` counter for the current size. Enqueue: increment size before adding node; if new size > capacity, decrement and return False. Dequeue: decrement size. The counter itself needs CAS to avoid races.

### 2. "What's the memory reclamation problem in lock-free queues?"

When Thread A dequeues Node X and frees it, Thread B might still hold a reference to X (read before the CAS). Freeing X while B holds it → use-after-free. Solutions:

- **Hazard Pointers**: each thread registers nodes it's currently accessing; reclamation is deferred until no thread holds the hazard pointer
- **Epoch-Based Reclamation**: threads announce their current epoch; memory freed in epoch E is only reclaimed when all threads have passed epoch E
- **Reference Counting**: AtomicReference with shared_ptr (C++) — reclaim when count drops to 0

In Java, GC handles this automatically (no manual reclamation).

### 3. "How does this compare to a lock-based queue?"

**Lock-based ConcurrentLinkedQueue**: Uses a lock (mutex). Simple to reason about correctness. One thread holds the lock — all others spin/block. Under high contention, throughput degrades.

**Lock-free queue**: Threads never block each other — one thread's delay doesn't stall others. Progress is guaranteed (at least one thread always makes progress — "lock-freedom"). Higher throughput under contention. Complex to implement and reason about.

### 4. "What is progress guarantee: lock-free vs wait-free?"

- **Lock-free**: At least one thread makes progress at all times. A single thread might starve (keep failing CAS).
- **Wait-free**: Every thread makes progress in a bounded number of steps — no starvation. Harder to implement (Michael-Scott is lock-free, not wait-free).

---

## Interviewer Questions by Level

**Junior**: Thread-safe queue using a mutex. Enqueue/dequeue with Lock. Explain why locks are needed.

**Mid-level**: Explain CAS. Sketch the Michael-Scott algorithm. Sentinel node and why tail may lag. ABA problem and version counter fix.

**Senior**: Full Michael-Scott implementation with AtomicRef + versioning. Memory reclamation (hazard pointers, epoch-based). Lock-free vs wait-free distinction. ABA problem and atomic tagged pointers.

---

## Common Interview Questions

- **Q**: What is Compare-and-Swap (CAS)?
  **A**: An atomic CPU instruction that reads a memory location, compares it to an expected value, and only updates it if they match — all as one uninterruptible operation. If the value changed (another thread modified it), CAS returns false and the caller retries. Enables lock-free synchronization.

- **Q**: Why does the Michael-Scott queue use a sentinel node?
  **A**: The sentinel (dummy head) simplifies edge cases. Head always points to the sentinel; the first real element is `head.next`. Empty queue: `head.next == null`. This avoids special-casing "enqueue to empty queue" vs "enqueue to non-empty queue."

- **Q**: What is the ABA problem?
  **A**: Thread T1 reads head=NodeA. Thread T2 dequeues NodeA, reuses its memory for a new node, re-enqueues it — head=NodeA again. T1's CAS(head, NodeA, newNode) succeeds, but the queue structure changed. Fix: version counter alongside the pointer — T2's reuse increments the version, so T1's CAS (expecting old version) fails.

- **Q**: Why does the tail in Michael-Scott queue lag behind?
  **A**: Enqueue is two-step: (1) CAS node.next to append, (2) CAS tail to advance. Between steps 1 and 2, tail is stale. Both enqueue and dequeue detect and help advance a lagging tail, ensuring eventual consistency.

- **Q**: Is the Michael-Scott queue wait-free?
  **A**: No — it's lock-free. A thread might repeatedly fail CAS (another thread always wins) and theoretically starve. In practice, random backoff prevents sustained starvation. True wait-free queues exist but are more complex.
