---
module: 06-lld
topic: Problems
status: unread
tags: [06-lld, system-design, problems]
---
# LLD: Design a Lock-Free Queue

## Problem Statement

Design a thread-safe, unbounded FIFO queue that supports concurrent enqueue and dequeue operations **without using any locks or synchronized blocks**. The implementation must be correct under all interleavings of concurrent threads.

## Requirements

- `void enqueue(T item)` — add item to tail, non-blocking
- `T dequeue()` — remove and return item from head, return null if empty
- Linearizable (each operation appears atomic at some point during its execution)
- Progress guarantee: at least one thread makes progress at all times (lock-free)

---

## Background: Why Lock-Free?

Traditional `synchronized` or `ReentrantLock` queues serialize all operations. Under high contention:
- Threads block each other
- Context switches are expensive (~1-10μs)
- Priority inversion: a low-priority thread holding a lock blocks high-priority threads

**Lock-free** means: if threads are suspended at any point, other threads can still make progress. No thread can be "stuck" waiting for another.

Lock-free data structures use **Compare-And-Swap (CAS)** as the atomic primitive.

---

## Compare-And-Swap (CAS)

```java
// Pseudo-semantics of CAS (executed atomically by CPU):
boolean compareAndSwap(AtomicReference ref, T expected, T newValue) {
    if (ref.get() == expected) {
        ref.set(newValue);
        return true;
    }
    return false;
}
```

Real Java: `AtomicReference.compareAndSet(expected, update)` maps to a single CPU instruction (`CMPXCHG` on x86). The OS never suspends a thread mid-CAS.

**Key property**: CAS either succeeds (atomically swaps) or fails (no change) — no partial state.

---

## The ABA Problem

CAS checks if a value **equals** `expected`. But equality doesn't mean the value is unchanged:

```
Thread 1: reads head → Node A
Thread 1: suspended

Thread 2: dequeues A, enqueues new Node C, dequeues B, enqueues A back (same object, recycled)

Thread 1: resumes
Thread 1: CAS(head, A, A.next) → succeeds! (head is A again)
But A.next is now stale — Thread 1 uses freed/wrong memory
```

**Node A was removed and re-added, but its value appears unchanged to CAS.** This is the ABA problem.

### Solution: Stamped References

Java's `AtomicStampedReference<T>` pairs a reference with a monotonically increasing integer stamp:

```java
// CAS checks both reference AND stamp
atomicStampedRef.compareAndSet(expectedRef, newRef, expectedStamp, newStamp)
```

Every time a reference is updated, increment the stamp. ABA becomes impossible because stamp never repeats.

Simpler alternative: `AtomicMarkableReference<T>` — uses a single boolean mark instead of a counter.

---

## Michael-Scott Queue (the standard algorithm)

Published by Maged Michael and Michael Scott (1996). This is the lock-free queue used in:
- `java.util.concurrent.ConcurrentLinkedQueue`
- `java.util.concurrent.LinkedTransferQueue`

### Structure

```
        head                              tail
          │                                │
          ▼                                ▼
     ┌─────────┐   ┌─────────┐   ┌─────────┐
     │ sentinel │──►│ Node A  │──►│ Node B  │──► null
     │ (dummy)  │   │ data=1  │   │ data=2  │
     └─────────┘   └─────────┘   └─────────┘
```

- `head` points to a **sentinel/dummy node** — its `next` is the actual first element
- `tail` points to the last node (or second-to-last during a concurrent enqueue)
- Both `head` and `tail` are `AtomicReference`

### Why a Dummy Node?

Eliminates the special case where head == tail (empty queue). Also separates the head pointer (dequeue) from the tail pointer (enqueue) — they rarely contend.

### Implementation

```java
public class MichaelScottQueue<T> {

    private static class Node<T> {
        final T data;
        final AtomicReference<Node<T>> next = new AtomicReference<>(null);

        Node(T data) { this.data = data; }
    }

    private final AtomicReference<Node<T>> head;
    private final AtomicReference<Node<T>> tail;

    public MichaelScottQueue() {
        Node<T> sentinel = new Node<>(null);
        head = new AtomicReference<>(sentinel);
        tail = new AtomicReference<>(sentinel);
    }

    public void enqueue(T item) {
        Node<T> newNode = new Node<>(item);
        while (true) {
            Node<T> t = tail.get();
            Node<T> next = t.next.get();

            if (t == tail.get()) {               // tail still valid?
                if (next == null) {
                    // tail.next is null: try to link new node
                    if (t.next.compareAndSet(null, newNode)) {
                        // Success: try to advance tail (may fail — that's ok)
                        tail.compareAndSet(t, newNode);
                        return;
                    }
                } else {
                    // Tail is lagging: help advance it
                    tail.compareAndSet(t, next);
                }
            }
        }
    }

    public T dequeue() {
        while (true) {
            Node<T> h = head.get();
            Node<T> t = tail.get();
            Node<T> next = h.next.get();

            if (h == head.get()) {              // head still valid?
                if (h == t) {
                    if (next == null) return null;  // empty queue
                    tail.compareAndSet(t, next);    // tail lagging, help advance
                } else {
                    T data = next.data;
                    if (head.compareAndSet(h, next)) {
                        return data;
                    }
                    // CAS failed: another thread dequeued concurrently, retry
                }
            }
        }
    }
}
```

### Walkthrough: Concurrent Enqueue

Two threads enqueue simultaneously:

```
Initial: head → [sentinel] → [A] → null,  tail → [A]

Thread 1: t = [A], next = null → CAS(A.next, null, B) → SUCCESS
Thread 1: CAS(tail, A, B) → may succeed or fail

Thread 2: t = [A], next = null → CAS(A.next, null, C) → FAIL (A.next is now B)
Thread 2: next = tail.get().next = B (not null) → tail is lagging
Thread 2: CAS(tail, A, B) → advances tail, then loops
Thread 2: t = [B], next = null → CAS(B.next, null, C) → SUCCESS

Result: sentinel → A → B → C, tail → C
```

### Walkthrough: The "Helping" Mechanism

Notice the enqueue loop has two branches:
1. `next == null`: try to attach new node
2. `next != null`: tail is lagging — help the thread that attached the node move `tail` forward

This is the **helping** pattern: threads help each other complete their operations rather than waiting. This is what gives lock-free (not just obstruction-free) progress: even if Thread 1 crashes after linking B but before advancing tail, Thread 2 will advance tail on Thread 1's behalf.

### Progress Guarantee

- **Lock-free** (not wait-free): a thread may spin indefinitely if there is constant contention, but at least one thread always makes progress in any interval.
- To achieve **wait-free** (every thread completes in bounded steps), you need more complex "fetch and add" position-based queues (Kogan-Petrank, 2011) at higher implementation complexity.

---

## ABA Prevention in Michael-Scott Queue

The original M-S queue has a subtle ABA risk in the dequeue step if nodes are **reused** (memory pooled). 

**Solution in practice**: Java's GC prevents premature reuse. A dequeued node is not garbage-collected until all references to it are dropped. CAS will fail if it compares against a node that has been freed and reallocated at the same address (not possible in GC'd languages).

In C/C++ (no GC), you must use hazard pointers or epoch-based reclamation to safely free nodes.

---

## Segment-Based Queue (for very high throughput)

`ConcurrentLinkedQueue` has per-element CAS overhead. For extremely high throughput, use a **segmented/chunked queue**:

- Each segment holds an array of N slots (e.g., N=64)
- Enqueue: CAS on `tail.segment.slots[tail.index]` then increment index
- When a segment is full, CAS a new segment onto the chain
- Dequeue: similar logic on head segment

Used in: Disruptor (LMAX), Agrona ManyToOne/OneToOne queues.

---

## `java.util.concurrent.ConcurrentLinkedQueue` vs `LinkedBlockingQueue`

| | `ConcurrentLinkedQueue` | `LinkedBlockingQueue` |
|---|---|---|
| **Algorithm** | Michael-Scott (lock-free) | ReentrantLock + 2 conditions |
| **Blocking dequeue** | ❌ (returns null if empty) | ✅ (`take()` blocks) |
| **Backpressure** | ❌ | ✅ (bounded variant) |
| **Under low contention** | Slightly slower (CAS overhead) | Faster (no retry) |
| **Under high contention** | Much faster (no blocking) | Degrades (lock contention) |
| **Use case** | High-throughput producer/consumer | Task queues, thread pools |

**When to use lock-free**: high throughput + low latency + contention is common + you don't need blocking semantics.

---

## Interview Deep-Dive Questions

1. **What makes the Michael-Scott queue lock-free rather than just thread-safe?**
   Lock-free means at least one thread makes progress in any finite number of steps, regardless of other threads' states. The Michael-Scott queue achieves this via the "helping" mechanism in enqueue: if Thread A fails to advance `tail`, Thread B will advance it on A's behalf. There is no point where all threads can simultaneously be blocked — at worst, they spin, but the CAS that each thread attempts either succeeds for that thread or advances the queue for another thread.

2. **Why is the sentinel/dummy node necessary? What would break without it?**
   Without a dummy node, `head` and `tail` both point to the same node when the queue is empty. A concurrent dequeue reads `head.data` while an enqueue CASes `tail.next`. These two operations would need to be coordinated atomically — requiring a lock. The dummy node ensures `head` always points to a "wrapper" node whose data is never returned, so dequeue advances `head` to `head.next` (the real first element) without touching `tail`.

3. **Describe the ABA problem with a concrete scenario on this queue. How does Java's GC help?**
   Thread 1 reads `head` (pointing to Node X). Thread 2 dequeues X, dequeues Y, then enqueues a new node Z — but if memory is reused, Z might occupy the same address as X. Thread 1 CASes `head` from X to X.next — but X.next is now Z's next, which is wrong. Java's GC prevents this: Node X is not freed (and thus not reused) until Thread 1 drops its reference. The CAS will see the current object identity, not a recycled object. In C++, you need hazard pointers to protect against this.

---

## See Also

- `06-lld/04-concurrency/concurrency-patterns.md` — Java Memory Model, happens-before, volatile
- `06-lld/05-problems/25-design-concurrent-lru-cache.md` — Segment-based locking
- `06-lld/05-problems/26-design-high-contention-counter.md` — CAS-based counters, LongAdder
- `02-building-blocks/distributed-locks.md` — Distributed locking (Redis, ZooKeeper)
