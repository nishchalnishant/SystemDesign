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
import threading

buffer = []  # shared, unsynchronized

# Thread A — Producer:
def producer():
    while True:
        buffer.append(generate())  # no check on size

# Thread B — Consumer:
def consumer():
    while True:
        if buffer:
            t = buffer.pop(0)  # concurrent modification
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

### 1. Using `queue.Queue` (Simplest & Best in Python)

Python's `queue` module solves this automatically.

```python
import queue
import threading
import time

_STOP = object()  # Poison pill sentinel

def producer(q):
    for i in range(20):
        print(f"Produced: {i}")
        q.put(i)           # BLOCKS if full
        time.sleep(0.1)
    q.put(_STOP)           # Poison pill to stop consumer

def consumer(q):
    while True:
        value = q.get()    # BLOCKS if empty
        if value is _STOP:
            break
        print(f"Consumed: {value}")
        time.sleep(0.2)    # Simulate slow processing

if __name__ == "__main__":
    q = queue.Queue(maxsize=10)  # Shared buffer with capacity 10
    t_prod = threading.Thread(target=producer, args=(q,))
    t_cons = threading.Thread(target=consumer, args=(q,))
    t_prod.start()
    t_cons.start()
    t_prod.join()
    t_cons.join()
```

---

### 2. Using `threading.Condition` (Low-Level)

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
            # while loop crucial (spurious wakeups)
            while len(self._queue) == self._capacity:
                self._cond.wait()  # Release lock, wait for space
            self._queue.append(value)
            print(f"Produced: {value}")
            self._cond.notify_all()  # Notify waiting consumers

    def consume(self):
        with self._cond:
            while not self._queue:
                self._cond.wait()  # Release lock, wait for data
            value = self._queue.popleft()
            print(f"Consumed: {value}")
            self._cond.notify_all()  # Notify waiting producers
            return value
```

**Key Concept**: `wait()` releases the lock. `notify_all()` wakes up threads but doesn't release the lock immediately (the `with` block must exit first).

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

## Interviewer Follow-Up Questions

- "Why use a bounded queue in producer-consumer instead of an unbounded one?" → Bounded queue provides backpressure: when the queue is full, producers block (or get an error). This prevents unbounded memory growth — if producers are faster than consumers, an unbounded queue grows until OOM crash. With a bounded queue: the system self-regulates. The producer slows down naturally when the consumer is a bottleneck. This is important for resilience: better to slow down than to crash.
- "What happens when multiple producers and multiple consumers share the same queue?" → Standard thread-safe queue (Python's `queue.Queue`, Java's `BlockingQueue`) handles concurrent access internally. Producers and consumers don't need external synchronization — the queue is the synchronization point. Multiple producers race to enqueue; multiple consumers race to dequeue. The queue's internal lock ensures only one operation runs at a time. This is correct and scalable.
- "How do you stop the consumer gracefully when the producer is done?" → Sentinel value: producer enqueues a `None` (or a special `STOP` sentinel) when done. Consumer checks: `if item is None: break`. For N consumers: enqueue N sentinel values (one per consumer). Alternative: use a separate `threading.Event` flag (`stop_event.set()`) that consumers check. The Event approach doesn't interfere with the queue's items — cleaner separation of data and control signals.
- "Python has the GIL. Does that mean Python producer-consumer threads are safe without locks?" → Partially. The GIL serializes CPython bytecode execution — but individual Python operations map to multiple bytecodes, so `balance += 1` is NOT atomic (it's read + add + write, and the GIL can release between any two). However, `queue.Queue.put()` and `queue.Queue.get()` are internally thread-safe — they use their own locks. For shared mutable state outside the queue: always use explicit locks. The GIL is not a substitute for synchronization.
