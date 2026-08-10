> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a Lock-Free Queue — an extremely advanced, low-level concurrency problem (Michael-Scott queue algorithm).
>
> **Key concepts:**
> - Core Entities: `Node` (contains `value` and `AtomicReference<Node> next`), `Queue` (contains `AtomicReference<Node> head` and `tail`).
> - CAS (Compare-And-Swap): Hardware-level atomic operation. `atomicRef.compareAndSet(expectedValue, newValue)`. It only updates if the current value matches what we *expect* it to be.
> - Enqueue: 
>   1. Read the `tail` and `tail.next`.
>   2. Use CAS to try and set `tail.next` to the new node.
>   3. If CAS fails (another thread snuck in), loop and try again (Spinlock).
>   4. If CAS succeeds, use CAS to update `tail` to the new node.
> - The ABA Problem: A thread reads 'A', another thread changes it to 'B', then back to 'A'. The first thread's CAS succeeds, but the queue state is corrupted. Solved by attaching a version number to the pointer (`AtomicStampedReference` in Java).
>
> **Key takeaway:** You are not expected to invent this algorithm in an interview. You are expected to know *how* CAS works, what the ABA problem is, and how `AtomicStampedReference` solves it.

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

Design a lock-free FIFO queue that supports concurrent enqueue and dequeue operations without using mutexes or locks, using Java's real hardware-backed CAS primitive (`AtomicStampedReference`) — the JVM equivalent of C++'s `std::atomic`.

---

## Clarifying Questions

**You**: "What's the concurrency model — multiple producers, multiple consumers?"
**Interviewer**: "MPMC — multiple producer, multiple consumer (the hardest case)."

**You**: "Can we use Java's real atomic primitives directly?"
**Interviewer**: "Yes — `AtomicStampedReference` gives you real CAS + versioning; use it directly rather than simulating."

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
- JVM memory-model / `volatile` ordering subtleties beyond what `AtomicStampedReference` already guarantees
- Memory ordering (C++ memory_order) — mention at senior level

---

## Core Entities and Relationships

| Entity | Responsibility |
|--------|---------------|
| `Node` | Singly linked list node; holds value and atomic `next` pointer |
| `LockFreeQueue` | Head (sentinel) + tail pointers; CAS-based enqueue/dequeue |
| `AtomicStampedReference<Node<T>>` | Real JVM atomic reference (wraps a reference + integer "stamp" version for ABA) |

The Michael-Scott queue uses a **sentinel node** (dummy head). Head always points to the sentinel; Tail always points to the last real node (or the sentinel when empty). Both are atomic pointers updated via CAS.

---

## Class Design

### Node

```
class Node<T>:
- value: T
- next: AtomicStampedReference<Node<T>>
```

### AtomicStampedReference (real CAS + versioning)

```java
import java.util.concurrent.atomic.AtomicStampedReference;

// AtomicStampedReference<Node<T>> IS the real CAS + version primitive —
// no hand-rolled lock-based simulation needed, unlike in Python.
// get() returns the referent; get(int[] stampHolder) also fills in the version ("stamp").
// compareAndSet(expectedRef, newRef, expectedStamp, newStamp) is a true
// hardware-backed CAS (LOCK CMPXCHG under the hood on x86), not lock-emulated.
```

In a real implementation (Java), this *is* the primitive: `AtomicStampedReference<Node<T>>`. In C++: `std::atomic<Node*>` with ABA-safe tagged pointers or hazard pointers.

### LockFreeQueue (Michael-Scott Algorithm)

```
class LockFreeQueue<T>:
- head: AtomicStampedReference<Node<T>>   # points to sentinel node
- tail: AtomicStampedReference<Node<T>>   # points to last node

+ enqueue(value: T): void
+ dequeue(): T or null
+ isEmpty(): boolean
```

---

## Implementation

### Michael-Scott Queue Algorithm

The key insight: `tail` may lag behind the actual last node. Both enqueue and dequeue must tolerate (and help advance) a lagging tail.

```java
import java.util.concurrent.atomic.AtomicStampedReference;

public class LockFreeQueue<T> {

    private static class Node<T> {
        final T value;
        final AtomicStampedReference<Node<T>> next;

        Node(T value) {
            this.value = value;
            this.next = new AtomicStampedReference<>(null, 0);
        }
    }

    private final AtomicStampedReference<Node<T>> head;
    private final AtomicStampedReference<Node<T>> tail;

    public LockFreeQueue() {
        Node<T> sentinel = new Node<>(null);
        this.head = new AtomicStampedReference<>(sentinel, 0);
        this.tail = new AtomicStampedReference<>(sentinel, 0);
    }

    public void enqueue(T value) {
        Node<T> newNode = new Node<>(value);
        int[] tailStampHolder = new int[1];
        int[] nextStampHolder = new int[1];

        while (true) {
            Node<T> tailNode = tail.get(tailStampHolder);
            int tailVer = tailStampHolder[0];
            Node<T> tailNext = tailNode.next.get(nextStampHolder);
            int tailNextVer = nextStampHolder[0];

            // Consistency check: tail hasn't moved since we read it
            int[] recheckStamp = new int[1];
            Node<T> currentTail = tail.get(recheckStamp);
            if (tailNode != currentTail || tailVer != recheckStamp[0]) {
                continue;   // tail moved — retry
            }

            if (tailNext == null) {
                // Tail is truly the last node — try to append
                if (tailNode.next.compareAndSet(null, newNode, tailNextVer, tailNextVer + 1)) {
                    // Advance tail — best effort (another thread may do it)
                    tail.compareAndSet(tailNode, newNode, tailVer, tailVer + 1);
                    return;
                }
            } else {
                // Tail is lagging — help advance it
                tail.compareAndSet(tailNode, tailNext, tailVer, tailVer + 1);
            }
        }
    }

    public T dequeue() {
        int[] headStampHolder = new int[1];
        int[] tailStampHolder = new int[1];
        int[] headNextStampHolder = new int[1];

        while (true) {
            Node<T> headNode = head.get(headStampHolder);
            int headVer = headStampHolder[0];
            Node<T> tailNode = tail.get(tailStampHolder);
            int tailVer = tailStampHolder[0];
            Node<T> headNext = headNode.next.get(headNextStampHolder);

            // Consistency check
            int[] recheckStamp = new int[1];
            Node<T> currentHead = head.get(recheckStamp);
            if (headNode != currentHead || headVer != recheckStamp[0]) {
                continue;
            }

            if (headNode == tailNode) {
                // Queue appears empty OR tail is lagging
                if (headNext == null) {
                    return null;   // truly empty
                }
                // Tail is lagging behind — help advance it
                tail.compareAndSet(tailNode, headNext, tailVer, tailVer + 1);
            } else {
                // Read value before CAS (node may be reclaimed after CAS by GC)
                T value = headNext.value;
                if (head.compareAndSet(headNode, headNext, headVer, headVer + 1)) {
                    return value;
                }
                // CAS failed → another thread dequeued — retry
            }
        }
    }

    public boolean isEmpty() {
        Node<T> headNode = head.getReference();
        Node<T> headNext = headNode.next.getReference();
        return headNext == null;
    }
}
```

### The ABA Problem Explained

ABA occurs when:
1. Thread T1 reads head → points to Node A (value=1)
2. Thread T2 dequeues Node A, then enqueues Node A (same memory address), then dequeues something else — head now points to Node A again
3. T1's CAS succeeds (head is still A!) but the queue state has changed

**Fix**: Tag each atomic reference with a version counter. CAS checks both the pointer AND the version. Even if the same node is re-used, the version is different — CAS fails.

```
AtomicStampedReference stores (reference, stamp):
  Before T2: head = (NodeA, stamp=5)
  After T2:  head = (NodeA, stamp=6)   ← stamp changed
  T1's CAS: expects (NodeA, stamp=5) → FAILS correctly
```

`AtomicStampedReference<Node<T>>` gives us exactly this for free. In C++: tagged pointer (store version in low bits of pointer).

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

**Senior**: Full Michael-Scott implementation with `AtomicStampedReference` + versioning. Memory reclamation (hazard pointers, epoch-based). Lock-free vs wait-free distinction. ABA problem and atomic tagged pointers.

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

---

## Related

**SOLID focus**: [Interface Segregation](../../02-solid-principles/04-interface-segregation.md)

**Concurrency**: [Concurrency Patterns](../../04-concurrency/concurrency-patterns.md) · [Producer-Consumer](../../04-concurrency/producer-consumer.md) · [Futures & Async Patterns](../../04-concurrency/futures-async-patterns.md)

**Practice next**

- [Design a Concurrent LRU Cache](35-design-concurrent-lru-cache.md)
- [Design a High-Contention Counter](36-design-high-contention-counter.md)

The three concurrency problems build on one another.

**Frameworks**: [LLD Template](../../../07-interview-templates/01-frameworks/02-lld-template.md) · [UML Diagrams](../../uml-diagrams.md) · [OOP Four Pillars](../../01-oop-fundamentals/four-pillars.md)
