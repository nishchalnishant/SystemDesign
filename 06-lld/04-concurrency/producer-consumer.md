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

```java
List<Task> buffer = new ArrayList<>();  // shared, unsynchronized

// Thread A — Producer:
while (true) {
    buffer.add(generate());  // no check on size
}

// Thread B — Consumer:
while (true) {
    if (!buffer.isEmpty()) {
        Task t = buffer.remove(0);  // concurrent modification — race condition
        process(t);
    }
}
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

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ProducerConsumerDemo {

    private static final Integer POISON_PILL = null;  // sentinel to stop consumer

    static void producer(BlockingQueue<Integer> q) throws InterruptedException {
        for (int i = 0; i < 20; i++) {
            System.out.println("Produced: " + i);
            q.put(i);              // blocks if full
            Thread.sleep(100);
        }
        q.put(POISON_PILL);        // poison pill to stop consumer
    }

    static void consumer(BlockingQueue<Integer> q) throws InterruptedException {
        while (true) {
            Integer value = q.take();   // blocks if empty
            if (value == POISON_PILL) {
                break;
            }
            System.out.println("Consumed: " + value);
            Thread.sleep(200);          // simulate slow processing
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Shared buffer with capacity 10
        BlockingQueue<Integer> q = new ArrayBlockingQueue<>(10);

        Thread tProducer = new Thread(() -> {
            try {
                producer(q);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread tConsumer = new Thread(() -> {
            try {
                consumer(q);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        tProducer.start();
        tConsumer.start();
        tProducer.join();
        tConsumer.join();
    }
}
```

---

### 2. Using `wait()` and `notify()` (Low-Level)

Implementing from scratch to understand under the hood.

```java
import java.util.ArrayDeque;
import java.util.Deque;

public class SharedBuffer<T> {
    private final Deque<T> queue = new ArrayDeque<>();
    private final int capacity;
    private final Object lock = new Object();

    public SharedBuffer(int capacity) {
        this.capacity = capacity;
    }

    public void produce(T value) throws InterruptedException {
        synchronized (lock) {
            // while loop crucial — guards against spurious wakeups
            while (queue.size() == capacity) {
                lock.wait();          // release lock, wait for space
            }
            queue.addLast(value);
            System.out.println("Produced: " + value);
            lock.notifyAll();         // notify waiting consumers
        }
    }

    public T consume() throws InterruptedException {
        synchronized (lock) {
            while (queue.isEmpty()) {
                lock.wait();          // release lock, wait for data
            }
            T value = queue.removeFirst();
            System.out.println("Consumed: " + value);
            lock.notifyAll();         // notify waiting producers
            return value;
        }
    }
}
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

- [Design Elevator System](../06-problems/02-frequent-problems/09-design-elevator-system.md)
- [Design Notification System](../06-problems/02-frequent-problems/16-design-notification-system.md)
- [Design a Pub-Sub Messaging System](../06-problems/03-domain-specific/23-design-pub-sub.md)
- [Design Search Engine (Inverted Index)](../06-problems/04-advanced-niche/27-design-search-engine.md)
- [Design Download Manager](../06-problems/04-advanced-niche/32-design-download-manager.md)
- [Design Lock-Free Queue](../06-problems/04-advanced-niche/34-design-lock-free-queue.md)

