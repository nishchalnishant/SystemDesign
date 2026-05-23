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
List<Task> buffer = new ArrayList<>(); // shared, unsynchronized

// Thread A — Producer:
while (true) {
    buffer.add(generate()); // no check on size
}

// Thread B — Consumer:
while (true) {
    if (!buffer.isEmpty()) {
        Task t = buffer.remove(0); // concurrent modification
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

public class ProducerConsumerExample {

    public static void main(String[] args) {
        // Shared buffer with capacity 10
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        
        // Start Producer
        new Thread(new Producer(queue)).start();
        
        // Start Consumer
        new Thread(new Consumer(queue)).start();
    }
}

class Producer implements Runnable {
    private BlockingQueue<Integer> queue;
    
    public Producer(BlockingQueue<Integer> queue) { this.queue = queue; }
    
    @Override
    public void run() {
        try {
            for (int i = 0; i < 20; i++) {
                System.out.println("Produced: " + i);
                queue.put(i); // BLOCKS if full
                Thread.sleep(100);
            }
            queue.put(-1); // Poison pill to stop consumer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class Consumer implements Runnable {
    private BlockingQueue<Integer> queue;
    
    public Consumer(BlockingQueue<Integer> queue) { this.queue = queue; }
    
    @Override
    public void run() {
        try {
            while (true) {
                Integer value = queue.take(); // BLOCKS if empty
                if (value == -1) break; // Poison pill (stop signal)
                System.out.println("Consumed: " + value);
                Thread.sleep(200); // Simulate slow processing
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

### 2. Using `wait()` and `notify()` (Low-Level)

Implementing from scratch to understand under the hood.

```java
import java.util.LinkedList;
import java.util.Queue;

class SharedBuffer {
    private Queue<Integer> queue = new LinkedList<>();
    private int capacity;
    
    public SharedBuffer(int capacity) { this.capacity = capacity; }
    
    public synchronized void produce(int value) throws InterruptedException {
        // while loop crucial (spurious wakeups)
        while (queue.size() == capacity) {
            wait(); // Release lock, wait for space
        }
        
        queue.add(value);
        System.out.println("Produced: " + value);
        
        notifyAll(); // Notify waiting consumers
    }
    
    public synchronized int consume() throws InterruptedException {
        while (queue.isEmpty()) {
            wait(); // Release lock, wait for data
        }
        
        int value = queue.remove();
        System.out.println("Consumed: " + value);
        
        notifyAll(); // Notify waiting producers
        return value;
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
