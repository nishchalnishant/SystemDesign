> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The Producer-Consumer Pattern — the fundamental concurrency pattern where one or more threads produce data and put it in a shared queue, while one or more threads consume it.
>
> **Key concepts:**
> - The problem: Producers generating data faster than consumers can process it causes OutOfMemory (if queue is unbounded). If they share an `ArrayList` without locking, race conditions corrupt the data.
> - The primitive fix: `wait()` and `notifyAll()`. Lock a shared object. If the queue is full, the producer calls `wait()`. If empty, the consumer calls `wait()`. They `notifyAll()` each other after adding/removing items.
> - The modern fix: use a `BlockingQueue` (like `ArrayBlockingQueue`). It handles all the locking, waiting, and notifying internally.
> - Bounded Buffers: always use a fixed-size queue (bounded buffer). This applies backpressure to producers if consumers are too slow, preventing the system from crashing.
> - Poison Pill: how to gracefully shut down consumer threads. The producer sends a special "poison pill" object. When the consumer reads it, it terminates its loop.
>
> **Key takeaway:** Never implement `wait()/notify()` manually in an interview unless explicitly asked. Say "I will use an `ArrayBlockingQueue` for thread-safe producer-consumer communication."

---
module: 06-lld
topic: Concurrency
status: unread
tags: [06-lld, system-design, concurrency]
---
# Producer-Consumer Pattern

## Question

Two threads share a fixed-size `ArrayList<Task> buffer`. Thread A adds tasks to the buffer as fast as it can. Thread B reads and processes tasks from the buffer. Both run concurrently, no synchronization. What happens?

Try to identify every failure mode before reading on.

---

## Topic Mindmap

```
[Producer-Consumer Pattern]
├── Problem It Solves
│   ├── Thread A adds to ArrayList; Thread B removes — no synchronization
│   ├── ConcurrentModificationException on concurrent structural modification
│   ├── Lost tasks: isEmpty() then remove() not atomic
│   ├── Buffer overflow: producer adds faster → heap exhaustion
│   └── Busy-wait: consumer spins on isEmpty() at 100% CPU
├── Root Constraints
│   ├── isEmpty() + remove() must be a single atomic operation
│   ├── Consumer must block (not spin) when buffer is empty
│   └── Producer must block when buffer is at capacity
├── Solution 1: BlockingQueue (Best)
│   ├── ArrayBlockingQueue<Integer>(capacity)
│   ├── put(): blocks if full — automatic backpressure
│   ├── take(): blocks if empty — no busy-wait
│   └── Both are thread-safe internally — no external locking needed
├── Poison Pill Shutdown
│   ├── Producer puts sentinel value (-1, null, STOP_SIGNAL)
│   ├── Consumer checks for sentinel → exits loop gracefully
│   └── One sentinel per consumer thread for clean multi-consumer shutdown
├── Solution 2: wait()/notify() Low-Level
│   ├── synchronized produce(): while(full) wait(); add; notifyAll()
│   ├── synchronized consume(): while(empty) wait(); remove; notifyAll()
│   ├── while loop required — spurious wakeups can happen
│   └── notifyAll() preferred over notify() when multiple waiters exist
├── Real-World Examples
│   ├── Web server: listener thread (producer) → worker thread pool (consumer)
│   ├── Logger: application threads (producer) → background disk writer (consumer)
│   └── Print spooler: apps submit docs (producer) → printer hardware (consumer)
├── When to Use
│   ├── Decoupling production rate from consumption rate
│   ├── Batching slow consumers behind fast producers
│   └── Thread pool task queues, async pipelines
└── Interview Angles
    ├── Why use while() not if() around wait()?
    ├── What is a poison pill and why is it needed?
    └── BlockingQueue vs wait/notify — when do you use each?
```

## Race Conditions Without Synchronization

```python
buffer = []  # shared, unsynchronized

# Thread A — Producer:
while True:
    buffer.append(generate())  # no check on size

# Thread B — Consumer:
while True:
    if buffer:
        t = buffer.pop(0)  # concurrent modification — race condition
        process(t)
```

**Concrete failures**:

1. **`ConcurrentModificationException`**: Thread B calls `buffer.isEmpty()` → returns false → Thread A also calls `buffer.add()` mid-structural-modification → `ArrayList`'s internal array is being resized → Thread B's `remove(0)` reads a partially-updated array → exception.

2. **Lost tasks**: Thread B sees `isEmpty() == false`, then Thread A removes the only element before Thread B calls `remove(0)` → `IndexOutOfBoundsException` or Thread B processes stale data.

3. **Buffer overflow**: Producer adds faster than consumer processes. With no size check on `buffer.add()`, `ArrayList` grows unboundedly → heap exhaustion → `OutOfMemoryError`.

4. **Busy-wait**: Consumer loops on `isEmpty()` at 100% CPU even when the buffer is empty — a spinning poll.

**Root constraints derived from these failures**:
- **Atomicity**: `isEmpty()` + `remove(0)` must be a single atomic operation.
- **Blocking on empty**: Consumer must sleep (not spin) when buffer is empty, and wake when producer adds.
- **Blocking on full**: Producer must sleep when buffer is at capacity, and wake when consumer removes.

---

## Derive the Fix

`BlockingQueue` encodes all three constraints atomically:
- `put()` blocks when full (no size check needed)
- `take()` blocks when empty (no busy-wait needed)
- Both operations are thread-safe internally

---

> **Problem**: Coordinate a producer thread generating data and a consumer thread processing it, ensuring they don't overfill buffer or read from empty buffer.

## The Challenge

1. **Buffer Overflow**: Producer must wait if buffer is full.
2. **Buffer Underflow**: Consumer must wait if buffer is empty.
3. **Thread Safety**: Concurrent access to buffer must be synchronized.

## Implementation

### 1. Using `BlockingQueue` (Simplest & Best in Java)

Java's `java.util.concurrent` package solves this automatically.

```python
import queue
import threading
import time

POISON_PILL = None  # sentinel to stop consumer

def producer(q):
    for i in range(20):
        print(f"Produced: {i}")
        q.put(i)          # blocks if full
        time.sleep(0.1)
    q.put(POISON_PILL)    # poison pill to stop consumer

def consumer(q):
    while True:
        value = q.get()   # blocks if empty
        if value is POISON_PILL:
            break
        print(f"Consumed: {value}")
        time.sleep(0.2)   # simulate slow processing

if __name__ == "__main__":
    # Shared buffer with capacity 10
    q = queue.Queue(maxsize=10)

    t_producer = threading.Thread(target=producer, args=(q,))
    t_consumer = threading.Thread(target=consumer, args=(q,))
    t_producer.start()
    t_consumer.start()
    t_producer.join()
    t_consumer.join()
```

---

### 2. Using `wait()` and `notify()` (Low-Level)

Implementing from scratch to understand under the hood.

```python
import threading
from collections import deque

class SharedBuffer:
    def __init__(self, capacity):
        self._queue = deque()
        self._capacity = capacity
        self._cond = threading.Condition()

    def produce(self, value):
        with self._cond:
            # while loop crucial — guards against spurious wakeups
            while len(self._queue) == self._capacity:
                self._cond.wait()   # release lock, wait for space
            self._queue.append(value)
            print(f"Produced: {value}")
            self._cond.notify_all()  # notify waiting consumers

    def consume(self):
        with self._cond:
            while not self._queue:
                self._cond.wait()   # release lock, wait for data
            value = self._queue.popleft()
            print(f"Consumed: {value}")
            self._cond.notify_all()  # notify waiting producers
            return value
```

**Key Concept**: `wait()` releases the lock. `notifyAll()` wakes up threads but doesn't release lock immediately (synch block must exit).

---

## Real-World Examples

### 1. Web Servant (Request Handling)
- **Producer**: Listener thread accepts HTTP connections -> puts socket into Queue.
- **Consumer**: Worker threads (Thread Pool) take socket -> process request.

### 2. Logger Framework
- **Producer**: Application threads call `log.info()`.
- **Consumer**: Background thread writes log messages to disk (batching).

### 3. Print Spooler
- **Producer**: Applications submit documents.
- **Consumer**: Printer hardware prints one by one.

---

## Interview Questions

**Q: Why use `BlockingQueue` instead of `wait()/notify()`?**
- A: `BlockingQueue` handles synchronization, locking, and condition waiting internally. It's less error-prone and cleaner.

**Q: What is a "Poison Pill"?**
- A: A special object put into the queue to signal consumers to stop (e.g., `-1` or `null`). Used for graceful shutdown.

**Q: What happens if you use `if` instead of `while` in wait() condition?**
- A: **Spurious Wakeups**: A thread might wake up without being notified. `while` ensures condition is re-checked. Also, another thread might have grabbed the lock and consumed the item/space before you got it.

---

## Applied In

This concept is used by **6 problems** in this repo:

**Low-Level Design**

- [Design Elevator System](../05-problems/02-frequent-problems/09-design-elevator-system.md)
- [Design Notification System](../05-problems/02-frequent-problems/16-design-notification-system.md)
- [Design a Pub-Sub Messaging System](../05-problems/03-domain-specific/23-design-pub-sub.md)
- [Design Search Engine (Inverted Index)](../05-problems/04-advanced-niche/27-design-search-engine.md)
- [Design Download Manager](../05-problems/04-advanced-niche/32-design-download-manager.md)
- [Design Lock-Free Queue](../05-problems/04-advanced-niche/34-design-lock-free-queue.md)

