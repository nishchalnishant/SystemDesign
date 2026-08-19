> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Core Concurrency Patterns and Utilities — the essential thread synchronization tools beyond basic `synchronized` blocks.
>
> **Key concepts:**
> - `ReadWriteLock`: allows multiple threads to read simultaneously, but only one thread to write (and blocks reads while writing). Crucial for cache implementations.
> - `Semaphore`: restricts the number of concurrent threads accessing a resource (e.g., max 10 DB connections). It's a bouncer with $N$ permits. Essential for Rate Limiters.
> - `CountDownLatch`: makes one or more threads wait until a set of operations being performed in other threads completes. Useful for scatter-gather (e.g., query 3 APIs in parallel, wait for all 3 to finish).
> - `ConcurrentHashMap`: thread-safe map that uses bucket-level locking (lock stripping) instead of locking the whole map. Much faster than `Collections.synchronizedMap()`.
> - Thread Pools (`ExecutorService`): never create threads manually (`new Thread()`). Use pools to reuse threads, bound resource usage, and handle task queuing.
> - `AtomicInteger` / `AtomicLong`: lock-free, thread-safe primitives using CAS (Compare-And-Swap) hardware instructions. Perfect for counters.
>
> **Key takeaway:** Learn to map LLD problems to the right concurrency tool: Cache = `ReadWriteLock` + `ConcurrentHashMap`. Rate Limiter = `Semaphore`. Scatter-Gather = `CountDownLatch`. Counter = `AtomicInteger`.

---
module: 06-lld
topic: Concurrency
status: unread
tags: [06-lld, system-design, concurrency]
---
# Concurrency Patterns

## Question

Before reading any pattern below, reason through each scenario:

1. **ReadWriteLock**: A cache is read 10,000 times/sec and written once every 5 minutes. You use `synchronized` on every read. What is the throughput cost? What constraint does the correct fix satisfy?

2. **Semaphore**: A service can handle 10 concurrent DB connections. Thread 11 arrives. What happens without a semaphore? What is the concrete failure?

3. **CountDownLatch**: You fan out work to 5 threads. The main thread calls `result.aggregate()` before all workers finish. What does it read?

4. **volatile**: A `boolean running` flag is written by Thread A and read by Thread B in a loop. Without `volatile`, Thread B's CPU caches the value. What is the failure mode? What does `volatile` fix, and what does it NOT fix?

Try each before reading the corresponding section below.

---

## Topic Mindmap

```
[Concurrency Patterns — Java]
├── Core Concept
│   ├── What → Patterns that coordinate multiple threads accessing shared resources safely
│   └── Why → Race conditions, deadlocks, and visibility bugs are invisible until production load
├── Key Patterns
│   ├── synchronized / ReentrantLock → mutual exclusion; only one thread executes the block at a time
│   ├── CountDownLatch / volatile flag → thread-safe signal; visibility across threads
│   ├── ReentrantReadWriteLock → N concurrent readers OR 1 exclusive writer; boosts read-heavy caches
│   ├── Semaphore → counting gate; limits concurrent access to K resources (DB connection pool)
│   ├── CyclicBarrier → fan-out sync; all threads wait for each other at a checkpoint
│   └── Condition (from ReentrantLock) → rendezvous; await()/signal()/signalAll() for producer-consumer
├── When to Use
│   ├── ✓ ReadWriteLock: high read/low write ratio (config cache, in-memory store)
│   ├── ✓ Semaphore: bounded resource (DB pool, API rate limit across threads)
│   └── ✓ CyclicBarrier/CountDownLatch: parallel init tasks before serving requests
├── When NOT to Use
│   ├── ✗ synchronized on read-heavy paths — blocks all readers; use ReentrantReadWriteLock
│   └── ✗ plain boolean flag for compound check-then-act — use a Lock + counter
├── Trade-offs
│   ├── Pro: Correctness over unsafe concurrent mutations
│   └── Con: Deadlock risk if lock ordering inconsistent; performance overhead vs lock-free
├── Real-World Examples
│   ├── Connection pool → Semaphore gates thread access to connection queue
│   └── App startup → CyclicBarrier/CountDownLatch waits for all init tasks to complete
└── Interview Angles
    ├── Deadlock → four conditions: mutual exclusion, hold-and-wait, no preemption, circular wait
    ├── CountDownLatch vs Lock+counter → latch for one-shot signaling; Lock+counter for atomic increment
    └── Code challenge: implement a thread-safe bounded cache with a ReadWriteLock pattern
```

---

**Race condition derivations per pattern:**

- **ReadWriteLock**: `synchronized` on reads means Thread 2–1000 queue behind Thread 1 even though reads don't mutate state. The constraint: concurrent reads are safe; only writes need exclusive access. Fix: allow N concurrent readers, one exclusive writer.

- **Semaphore**: Without a gate, Thread 11 opens an 11th DB connection. Under load, this becomes 100 connections, exhausting the pool and crashing the DB. The constraint: at most K threads may hold the resource simultaneously. Fix: a counting semaphore initialized to K.

- **CountDownLatch**: Without awaiting completion, the main thread reads partial results — some workers haven't written yet. The constraint: proceed only after all N events have fired. Fix: `latch.await()` blocks until `countDown()` has been called N times.

- **volatile**: Without `volatile`, the JIT hoists the read of `running` out of the loop (it looks like a constant to the optimizer). Thread B loops forever even after Thread A sets `running = false`. `volatile` guarantees visibility (no cache, no reorder) but NOT atomicity — `running++` is still a race.

---

> Core Java concurrency primitives and patterns for LLD interviews. Know these before tackling Tier 2 problems.

---

## 1. Read-Write Lock (ReentrantReadWriteLock)

**Problem**: Multiple readers can proceed concurrently, but writers need exclusive access.

```java
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Cache<K, V> {
    private final Map<K, V> map = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock(/* fair = */ true);

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
**Trade-off**: Writer starvation is possible if readers never release. Use the `fair` constructor parameter to avoid it.

---

## 2. Semaphore (Counting)

**Problem**: Limit concurrent access to a resource pool (DB connections, API rate limiting).

```java
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

public class ConnectionPool {
    private final Semaphore semaphore;
    private final ConcurrentLinkedQueue<Object> connections = new ConcurrentLinkedQueue<>();

    public ConnectionPool(int size) {
        this.semaphore = new Semaphore(size);
        for (Object conn : createConnections(size)) {
            connections.offer(conn);
        }
    }

    private Object[] createConnections(int size) {
        Object[] conns = new Object[size];
        for (int i = 0; i < size; i++) {
            conns[i] = new Object();   // placeholder connections
        }
        return conns;
    }

    public Object acquire() throws InterruptedException {
        semaphore.acquire();          // blocks if pool is exhausted
        return connections.poll();
    }

    public void release(Object conn) {
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

CountDownLatch latch = new CountDownLatch(3);   // 3 workers

Runnable worker = () -> {
    doWork();
    latch.countDown();   // signal completion
};

ExecutorService executor = Executors.newFixedThreadPool(3);
for (int i = 0; i < 3; i++) {
    executor.submit(worker);
}

latch.await();                 // main thread blocks until count reaches 0
System.out.println("All workers done");
executor.shutdown();
```

**One-shot**: cannot be reset. Use `CyclicBarrier` if you need reusable barriers.

---

## 4. CyclicBarrier

**Problem**: N threads must all reach a checkpoint before any proceeds (batch processing, parallel search).

```java
import java.util.concurrent.CyclicBarrier;

CyclicBarrier barrier = new CyclicBarrier(3,
        () -> System.out.println("All threads reached barrier — proceeding"));

Runnable task = () -> {
    doPartialWork();
    try {
        barrier.await();     // waits for the other 2; resets automatically after
    } catch (Exception e) {
        Thread.currentThread().interrupt();
    }
    doNextPhase();
};
```

**vs CountDownLatch**: CyclicBarrier resets and can be reused; CountDownLatch is one-shot.

---

## 5. Thread Pool (ExecutorService)

**Problem**: Unbounded thread creation kills performance. Pools reuse threads.

```java
import java.util.concurrent.*;

// Fixed pool — predictable resource use
ExecutorService pool = Executors.newFixedThreadPool(10);

// Scheduled / periodic task
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
scheduler.scheduleAtFixedRate(this::task, 0, intervalSeconds, TimeUnit.SECONDS);

// Custom pool with bounded queue and rejection policy (CallerRunsPolicy = backpressure)
ThreadPoolExecutor custom = new ThreadPoolExecutor(
        16,                                    // corePoolSize
        16,                                    // maximumPoolSize
        60L, TimeUnit.SECONDS,                 // idle thread keep-alive
        new ArrayBlockingQueue<>(1000),        // bounded work queue
        new ThreadPoolExecutor.CallerRunsPolicy()  // backpressure: caller runs task if queue is full
);
```

**Rejection policies**: `AbortPolicy` (throw), `CallerRunsPolicy` (backpressure), `DiscardPolicy` (drop silently).

---

## 6. Atomic Operations (Lock-Free)

**Problem**: Increment a counter from multiple threads without synchronized blocks.

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

AtomicInteger counter = new AtomicInteger(0);
counter.incrementAndGet();              // atomic read-modify-write (CAS loop under the hood)
counter.compareAndSet(5, 10);           // CAS: set to 10 only if current value is 5

// AtomicReference — CAS on object references
AtomicReference<Node> head = new AtomicReference<>(null);

void push(int value) {
    Node newNode = new Node(value);
    Node currentHead;
    do {
        currentHead = head.get();
        newNode.next = currentHead;
    } while (!head.compareAndSet(currentHead, newNode));  // retry until CAS succeeds
}
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
public class Worker extends Thread {
    private volatile boolean stopped = false;   // visible to worker thread immediately

    public void stopWorker() {
        stopped = true;
    }

    @Override
    public void run() {
        while (!stopped) {
            doWork();
        }
    }
}
```

**volatile guarantees**: visibility (no CPU cache stale reads), ordering (no reordering around the write/read).  
**volatile does NOT guarantee**: atomicity of compound operations (check-then-act, increment). Use Atomic* for those.

---

## Java Memory Model (JMM) and Happens-Before

**Question**: Thread A sets `flag = true` and writes `data = 42`. Thread B reads `flag == true` and then reads `data`. Is it guaranteed to see `data == 42`?

Without explicit synchronization: **no guarantee**. The CPU can reorder instructions. The compiler can cache values in registers. Each core has its own L1/L2 cache. Thread B may see a stale value of `data` even after observing `flag == true`.

The Java Memory Model (JMM, JSR-133, Java 5+) defines a **happens-before** relation. If action A happens-before action B, A's effects are visible to B.

### Happens-Before Rules

| Rule | Explanation |
|------|-------------|
| **Program order** | Each action in a thread happens-before every subsequent action in the same thread |
| **Monitor lock** | `unlock()` on a monitor happens-before any subsequent `lock()` on the same monitor |
| **Volatile write** | A write to a `volatile` field happens-before every subsequent read of that field |
| **Thread start** | `thread.start()` happens-before any action in the started thread |
| **Thread join** | All actions in a thread happen-before `thread.join()` returns |
| **Transitivity** | If A hb B and B hb C, then A hb C |

### Why Double-Checked Locking Requires volatile

```java
// BROKEN — no memory barrier; JIT/CPU may reorder writes
public class Singleton {
    private static Singleton instance = null;   // not volatile; not protected by any lock on first check

    public static Singleton getInstance() {
        if (instance == null) {                          // check 1 — no lock
            // another thread may also see null here before assignment completes
            instance = new Singleton();                  // PROBLEM: not atomic (alloc, init, assign)
        }
        return instance;
    }
}
```

**Why it breaks**: `instance = new Singleton()` compiles to three steps:
1. Allocate memory
2. Initialize object (run constructor)
3. Assign reference to `instance`

The JVM/CPU can reorder steps 2 and 3: assign the reference **before** the constructor runs. Thread B checks `instance != null` (step 3 happened), reads `instance`, and calls a method on a **partially constructed object** (step 2 not done yet). Crash or silent corruption.

```java
// CORRECT: double-checked locking with volatile
public class Singleton {
    private static volatile Singleton instance = null;

    public static Singleton getInstance() {
        if (instance == null) {                       // first check (no lock) — fast path
            synchronized (Singleton.class) {           // lock only during initialization
                if (instance == null) {                // second check (with lock)
                    instance = new Singleton();
                    // volatile write creates happens-before edge;
                    // subsequent readers see the fully constructed object
                }
            }
        }
        return instance;
    }
}
```

**What `volatile` gives you**:
- Visibility: every write is immediately visible to all threads (no CPU cache stale reads)
- Ordering: no reordering of instructions around the volatile write/read
- Does NOT give atomicity: `count++` is still not atomic with volatile

### CAS and ABA Problem

**CAS (Compare-And-Swap)**: atomic instruction `CMPXCHG` on x86. Reads current value, compares to expected, writes new value only if match — all atomically. Basis of all lock-free algorithms.

```java
import java.util.concurrent.atomic.AtomicInteger;

AtomicInteger counter = new AtomicInteger(0);
// CAS: only sets to 6 if current value is 5 — backed by CMPXCHG, no lock needed
boolean swapped = counter.compareAndSet(5, 6);

// Under the hood: the JVM emits a CPU-level CAS instruction (CMPXCHG on x86).
// No JVM-wide lock analogous to Python's GIL exists — true parallel execution,
// which is exactly why CAS-based atomics matter more in Java than in CPython.
```

**ABA Problem**: Thread 1 reads value A. Thread 2 changes A→B→A. Thread 1's CAS(A, C) succeeds but operates on a different "A" than it read.

```
Thread 1: reads head = NodeA (value=1)
Thread 2: pops NodeA, pushes NodeB, pushes NodeA back (reuses same object)
Thread 1: CAS(head, NodeA, newNode) succeeds — but NodeB is now lost!
```

**Fix**: `AtomicStampedReference<T>` — pairs the reference with a monotonically increasing stamp (version).

```java
import java.util.concurrent.atomic.AtomicStampedReference;

// Pairs a reference with a monotonically increasing stamp to prevent ABA
AtomicStampedReference<Node> head = new AtomicStampedReference<>(sentinel, 0);

int[] stampHolder = new int[1];
Node current = head.get(stampHolder);
int stamp = stampHolder[0];

// Only succeeds if both reference AND stamp match — ABA impossible
head.compareAndSet(current, newNode, stamp, stamp + 1);
```

### ThreadPoolExecutor Parameters

```java
import java.util.concurrent.*;

ThreadPoolExecutor executor = new ThreadPoolExecutor(
        8,                                       // corePoolSize
        16,                                       // maximumPoolSize
        60L, TimeUnit.SECONDS,                    // keepAliveTime for idle threads above core
        new ArrayBlockingQueue<>(100),            // bounded work queue
        new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy: caller runs the task itself
);
```

**Parameter semantics**:
1. If threads < `corePoolSize`: create new thread (even if idle threads exist)
2. If threads ≥ `corePoolSize` and queue not full: enqueue task
3. If queue full and threads < `maxPoolSize`: create new thread
4. If queue full and threads = `maxPoolSize`: apply **rejection policy**

**Rejection policies**:
- `AbortPolicy` (default): throw `RejectedExecutionException` — caller must handle
- `CallerRunsPolicy`: caller thread runs the task — natural backpressure (caller slows down)
- `DiscardPolicy`: silently drop task — use only when task loss is acceptable
- `DiscardOldestPolicy`: drop head of queue (oldest task), enqueue new one

**Sizing rule of thumb**:
- CPU-bound tasks: `corePoolSize` = number of CPU cores
- I/O-bound tasks: `corePoolSize` = cores × (1 + wait_time / compute_time) — more threads since most are blocked on I/O

### ForkJoinPool and Work Stealing

`ForkJoinPool` is designed for recursive divide-and-conquer tasks (e.g. merge sort, parallel streams).

**Work stealing**: each worker thread has a deque (double-ended queue) of tasks. Idle threads steal from the tail of other threads' deques. Stealers take from the tail (LIFO order avoids stealing freshly forked tasks), owners take from the head (FIFO for fairness to large tasks).

```java
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

public class SumTask extends RecursiveTask<Long> {
    private final int[] arr;
    private final int lo, hi;

    public SumTask(int[] arr, int lo, int hi) {
        this.arr = arr; this.lo = lo; this.hi = hi;
    }

    @Override
    protected Long compute() {
        if (hi - lo <= 1000) {
            // base case: compute sequentially
            long sum = 0;
            for (int i = lo; i < hi; i++) sum += arr[i];
            return sum;
        }
        int mid = (lo + hi) / 2;
        SumTask leftTask = new SumTask(arr, lo, mid);
        leftTask.fork();                                  // fork left — runs on another worker
        long rightResult = new SumTask(arr, mid, hi).compute();  // compute right in this thread
        long leftResult = leftTask.join();                // join left
        return leftResult + rightResult;
    }
}

// Usage
ForkJoinPool pool = new ForkJoinPool(8);
long total = pool.invoke(new SumTask(arr, 0, arr.length));
```

**When to use ForkJoinPool vs ThreadPoolExecutor**:
- ForkJoinPool: recursive tasks with fine-grained parallelism, work stealing reduces idle time
- ThreadPoolExecutor: independent tasks, I/O-bound work, explicit queue management needed

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

---

## Applied In

This concept is used by **18 problems** in this repo — a representative selection:

**Low-Level Design**

- [Design a Parking Lot](../06-problems/01-core-problems/01-design-parking-lot.md)
- [Design a Rate Limiter](../06-problems/01-core-problems/02-design-rate-limiter.md)
- [Design BookMyShow](../06-problems/02-frequent-problems/06-design-bookmyshow.md)
- [Design Elevator System](../06-problems/02-frequent-problems/09-design-elevator-system.md)
- [Design an LRU Cache](../06-problems/02-frequent-problems/13-design-lru-cache.md)
- [Design a Food Delivery System](../06-problems/02-frequent-problems/14-design-food-delivery.md)
- [Design Locker Service](../06-problems/02-frequent-problems/15-design-locker-service.md)
- [Design a Logger Library](../06-problems/03-domain-specific/19-design-logger-library.md)
- …and 10 more

