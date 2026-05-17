# Concurrency Patterns

> Core Java concurrency primitives and patterns for LLD interviews. Know these before tackling Tier 2 problems.

---

## 1. Read-Write Lock (ReentrantReadWriteLock)

**Problem**: Multiple readers can proceed concurrently, but writers need exclusive access.

```java
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cache<K, V> {
    private final Map<K, V> map = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public V get(K key) {
        lock.readLock().lock();
        try {
            return map.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            map.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

**When to use**: Read-heavy shared state (caches, config stores, leaderboards).  
**Trade-off**: Writer starvation is possible if readers never release. Use `fair=true` constructor to avoid it.

---

## 2. Semaphore (Counting)

**Problem**: Limit concurrent access to a resource pool (DB connections, API rate limiting).

```java
import java.util.concurrent.Semaphore;

public class ConnectionPool {
    private final Semaphore semaphore;
    private final Queue<Connection> connections;

    public ConnectionPool(int size) {
        this.semaphore = new Semaphore(size);
        this.connections = new ArrayDeque<>(createConnections(size));
    }

    public Connection acquire() throws InterruptedException {
        semaphore.acquire();             // blocks if pool is exhausted
        return connections.poll();
    }

    public void release(Connection conn) {
        connections.offer(conn);
        semaphore.release();
    }
}
```

**Binary semaphore** (permits=1) is equivalent to a mutex but doesn't have ownership — useful when one thread acquires and another releases.

---

## 3. CountDownLatch

**Problem**: Wait for N events to complete before proceeding (fan-out then join).

```java
CountDownLatch latch = new CountDownLatch(3);

// In 3 worker threads:
executor.submit(() -> {
    doWork();
    latch.countDown();   // signal completion
});

latch.await();           // blocks until count reaches 0
System.out.println("All workers done");
```

**One-shot**: cannot be reset. Use CyclicBarrier if you need reusable barriers.

---

## 4. CyclicBarrier

**Problem**: N threads must all reach a checkpoint before any proceeds (batch processing, parallel search).

```java
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("All threads reached barrier — proceeding");
});

Runnable task = () -> {
    doPartialWork();
    barrier.await();     // waits for the other 2; resets automatically after
    doNextPhase();
};
```

**vs CountDownLatch**: CyclicBarrier resets and can be reused; CountDownLatch is one-shot.

---

## 5. Thread Pool (ExecutorService)

**Problem**: Unbounded thread creation kills performance. Pools reuse threads.

```java
// Fixed pool — predictable resource use
ExecutorService pool = Executors.newFixedThreadPool(10);

// Scheduled pool — for periodic tasks
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.SECONDS);

// Custom pool — full control
ExecutorService custom = new ThreadPoolExecutor(
    4,                          // corePoolSize
    16,                         // maxPoolSize
    60L, TimeUnit.SECONDS,      // keepAlive for idle threads above core
    new LinkedBlockingQueue<>(1000),  // bounded work queue
    new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure: caller executes
);
```

**Rejection policies**: AbortPolicy (throw), CallerRunsPolicy (backpressure), DiscardPolicy (drop silently).

---

## 6. Atomic Operations (Lock-Free)

**Problem**: Increment a counter from multiple threads without synchronized blocks.

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();          // atomic read-modify-write
counter.compareAndSet(5, 10);       // CAS: set to 10 only if current value is 5

// AtomicReference for objects
AtomicReference<Node> head = new AtomicReference<>(null);
Node newNode = new Node(value);
do {
    newNode.next = head.get();
} while (!head.compareAndSet(newNode.next, newNode));  // lock-free push
```

**When to use**: Simple counters, flags, node references in lock-free data structures.  
**Not for**: Complex multi-field invariants — use locks there.

---

## 7. Concurrent Data Structures

Prefer these over synchronized wrappers (`Collections.synchronizedMap`):

| Structure | Use Case | Notes |
|-----------|----------|-------|
| `ConcurrentHashMap` | Thread-safe map | Segment locking (Java 8+: CAS on individual buckets) |
| `CopyOnWriteArrayList` | Read-heavy list with rare writes | Each write copies the array; reads are lock-free |
| `LinkedBlockingQueue` | Producer-consumer bounded queue | Separate locks for head (take) and tail (put) |
| `PriorityBlockingQueue` | Priority-ordered producer-consumer | Unbounded; blocks on take when empty |
| `ConcurrentLinkedQueue` | Non-blocking FIFO | Uses CAS; never blocks but offers() never fails |

---

## 8. volatile keyword

**Problem**: Ensure visibility of a flag across threads without locking.

```java
public class Worker implements Runnable {
    private volatile boolean running = true;

    public void stop() {
        running = false;        // visible to worker thread immediately
    }

    @Override
    public void run() {
        while (running) {
            doWork();
        }
    }
}
```

**volatile guarantees**: visibility (no CPU cache stale reads), ordering (no reordering around the write/read).  
**volatile does NOT guarantee**: atomicity of compound operations (check-then-act, increment). Use Atomic* for those.

---

## Pattern Decision Matrix

| Need | Use |
|------|-----|
| Readers can be concurrent, writers exclusive | ReentrantReadWriteLock |
| Limit concurrent access to a pool | Semaphore |
| Wait for N tasks to finish (one-shot) | CountDownLatch |
| N threads synchronize at a checkpoint (repeatable) | CyclicBarrier |
| Bound thread creation, handle backpressure | ThreadPoolExecutor with bounded queue |
| Atomic counter/flag without locking | AtomicInteger / AtomicBoolean |
| Thread-safe map | ConcurrentHashMap |
| Visibility of a simple flag | volatile |
| Decoupled producer-consumer with blocking | LinkedBlockingQueue |

---

## Quick Revision

- **ReentrantReadWriteLock**: many concurrent readers, one exclusive writer — use for read-heavy shared state
- **Semaphore**: gate on a resource count — connection pools, rate limiting
- **CountDownLatch**: fan-out/join — one-shot; CyclicBarrier if you need repeats
- **ExecutorService**: always use a bounded pool with a bounded queue and a rejection policy
- **AtomicInteger/CAS**: lock-free for simple single-field updates; not for multi-field invariants
- **volatile**: visibility only; not atomicity — never use for increment
- **ConcurrentHashMap over HashMap + synchronized**: finer-grained locking, better throughput
