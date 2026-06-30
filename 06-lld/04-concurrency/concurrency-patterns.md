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
[Concurrency Patterns — Python]
├── Core Concept
│   ├── What → Patterns that coordinate multiple threads accessing shared resources safely
│   └── Why → Race conditions, deadlocks, and visibility bugs are invisible until production load
├── Key Patterns
│   ├── threading.Lock / with lock → mutual exclusion; only one thread executes the block at a time
│   ├── threading.Event → thread-safe flag; set()/clear()/wait() for visibility across threads
│   ├── ReadWriteLock (custom) → N concurrent readers OR 1 exclusive writer; boosts read-heavy caches
│   ├── threading.Semaphore → counting gate; limits concurrent access to K resources (DB connection pool)
│   ├── threading.Barrier → fan-out sync; all threads wait for each other at a checkpoint
│   └── threading.Condition → rendezvous; wait()/notify()/notify_all() for producer-consumer
├── When to Use
│   ├── ✓ ReadWriteLock: high read/low write ratio (config cache, in-memory store)
│   ├── ✓ Semaphore: bounded resource (DB pool, API rate limit across threads)
│   └── ✓ Barrier/Event: parallel init tasks before serving requests
├── When NOT to Use
│   ├── ✗ threading.Lock on read-heavy paths — blocks all readers; use a ReadWriteLock pattern
│   └── ✗ plain bool flag for compound check-then-act — use threading.Lock + counter
├── Trade-offs
│   ├── Pro: Correctness over unsafe concurrent mutations
│   └── Con: Deadlock risk if lock ordering inconsistent; performance overhead vs lock-free
├── Real-World Examples
│   ├── Connection pool → Semaphore gates thread access to connection queue
│   └── App startup → Barrier/Event waits for all init tasks to complete
└── Interview Angles
    ├── Deadlock → four conditions: mutual exclusion, hold-and-wait, no preemption, circular wait
    ├── threading.Event vs Lock+counter → Event for flags; Lock+counter for atomic increment
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

```python
import threading

class ReadWriteLock:
    """Allow concurrent readers; one exclusive writer."""
    def __init__(self):
        self._read_ready = threading.Condition(threading.Lock())
        self._readers = 0

    def acquire_read(self):
        with self._read_ready:
            self._readers += 1

    def release_read(self):
        with self._read_ready:
            self._readers -= 1
            if self._readers == 0:
                self._read_ready.notify_all()

    def acquire_write(self):
        self._read_ready.acquire()
        while self._readers > 0:
            self._read_ready.wait()

    def release_write(self):
        self._read_ready.release()

class Cache:
    def __init__(self):
        self._map = {}
        self._lock = ReadWriteLock()

    def get(self, key):
        self._lock.acquire_read()
        try:
            return self._map.get(key)
        finally:
            self._lock.release_read()

    def put(self, key, value):
        self._lock.acquire_write()
        try:
            self._map[key] = value
        finally:
            self._lock.release_write()
```

**When to use**: Read-heavy shared state (caches, config stores, leaderboards).  
**Trade-off**: Writer starvation is possible if readers never release. Use `fair=true` constructor to avoid it.

---

## 2. Semaphore (Counting)

**Problem**: Limit concurrent access to a resource pool (DB connections, API rate limiting).

```python
import threading
import queue

class ConnectionPool:
    def __init__(self, size):
        self._semaphore = threading.Semaphore(size)
        self._connections = queue.Queue()
        for conn in self._create_connections(size):
            self._connections.put(conn)

    def _create_connections(self, size):
        return [object() for _ in range(size)]  # placeholder connections

    def acquire(self):
        self._semaphore.acquire()        # blocks if pool is exhausted
        return self._connections.get_nowait()

    def release(self, conn):
        self._connections.put(conn)
        self._semaphore.release()
```

**Binary semaphore** (permits=1) is equivalent to a mutex but doesn't have ownership — useful when one thread acquires and another releases.

---

## 3. CountDownLatch

**Problem**: Wait for N events to complete before proceeding (fan-out then join).

```python
import threading
from concurrent.futures import ThreadPoolExecutor

barrier = threading.Barrier(3 + 1)  # 3 workers + main thread

def worker():
    do_work()
    barrier.wait()   # signal completion

with ThreadPoolExecutor(max_workers=3) as executor:
    for _ in range(3):
        executor.submit(worker)

# Alternative: use threading.Event for one-shot fan-out/join
done_event = threading.Event()
counter_lock = threading.Lock()
count = [0]

def worker_with_event():
    do_work()
    with counter_lock:
        count[0] += 1
        if count[0] == 3:
            done_event.set()   # signal all done

done_event.wait()              # blocks until count reaches 3
print("All workers done")
```

**One-shot**: cannot be reset. Use CyclicBarrier if you need reusable barriers.

---

## 4. CyclicBarrier

**Problem**: N threads must all reach a checkpoint before any proceeds (batch processing, parallel search).

```python
import threading

def barrier_action():
    print("All threads reached barrier — proceeding")

barrier = threading.Barrier(3, action=barrier_action)

def task():
    do_partial_work()
    barrier.wait()       # waits for the other 2; resets automatically after
    do_next_phase()
```

**vs CountDownLatch**: CyclicBarrier resets and can be reused; CountDownLatch is one-shot.

---

## 5. Thread Pool (ExecutorService)

**Problem**: Unbounded thread creation kills performance. Pools reuse threads.

```python
import concurrent.futures
import threading
import queue
import time

# Fixed pool — predictable resource use
pool = concurrent.futures.ThreadPoolExecutor(max_workers=10)

# Scheduled / periodic task — use a background thread with sleep
def run_periodically(task, interval_seconds):
    def loop():
        while True:
            task()
            time.sleep(interval_seconds)
    t = threading.Thread(target=loop, daemon=True)
    t.start()

# Custom pool with bounded queue and backpressure
# Python's ThreadPoolExecutor accepts max_workers; for bounded queue + rejection,
# wrap submission with a Semaphore for backpressure:
_backpressure = threading.Semaphore(1000)  # bounded work queue equivalent

def submit_with_backpressure(executor, fn, *args):
    _backpressure.acquire()          # blocks caller if 1000 tasks already queued
    future = executor.submit(fn, *args)
    future.add_done_callback(lambda f: _backpressure.release())
    return future

custom = concurrent.futures.ThreadPoolExecutor(max_workers=16)
```

**Rejection policies**: AbortPolicy (throw), CallerRunsPolicy (backpressure), DiscardPolicy (drop silently).

---

## 6. Atomic Operations (Lock-Free)

**Problem**: Increment a counter from multiple threads without synchronized blocks.

```python
import threading

# Atomic counter — use a Lock around a plain int
class AtomicCounter:
    def __init__(self, initial=0):
        self._value = initial
        self._lock = threading.Lock()

    def increment_and_get(self):
        with self._lock:
            self._value += 1
            return self._value

    def compare_and_set(self, expected, new_value):
        with self._lock:
            if self._value == expected:
                self._value = new_value
                return True
            return False

counter = AtomicCounter(0)
counter.increment_and_get()         # atomic read-modify-write
counter.compare_and_set(5, 10)      # CAS: set to 10 only if current value is 5

# AtomicReference equivalent — use a Lock around a reference
class AtomicRef:
    def __init__(self, initial=None):
        self._ref = initial
        self._lock = threading.Lock()

    def get(self):
        with self._lock:
            return self._ref

    def compare_and_set(self, expected, new_value):
        with self._lock:
            if self._ref is expected:
                self._ref = new_value
                return True
            return False

# Lock-free push pattern (Python equivalent using AtomicRef)
head = AtomicRef(None)

def push(value):
    new_node = Node(value)
    while True:
        current_head = head.get()
        new_node.next = current_head
        if head.compare_and_set(current_head, new_node):
            break
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

```python
import threading

class Worker(threading.Thread):
    def __init__(self):
        super().__init__()
        self._stop_event = threading.Event()

    def stop(self):
        self._stop_event.set()     # visible to worker thread immediately

    def run(self):
        while not self._stop_event.is_set():
            do_work()
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

```python
# BROKEN — no memory barrier; interpreter may reorder writes
import threading

class Singleton:
    _instance = None  # not protected by any lock on first check

    @classmethod
    def get_instance(cls):
        if cls._instance is None:                    # check 1 — no lock
            # another thread may also see None here before assignment completes
            cls._instance = cls()                    # PROBLEM: not atomic
        return cls._instance
```

**Why it breaks**: `instance = new Singleton()` compiles to three steps:
1. Allocate memory
2. Initialize object (run constructor)
3. Assign reference to `instance`

The JVM/CPU can reorder steps 2 and 3: assign the reference **before** the constructor runs. Thread B checks `instance != null` (step 3 happened), reads `instance`, and calls a method on a **partially constructed object** (step 2 not done yet). Crash or silent corruption.

```python
# CORRECT: double-checked locking with a module-level lock
import threading

class Singleton:
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls):
        if cls._instance is None:             # first check (no lock) — fast path
            with cls._lock:                   # lock only during initialization
                if cls._instance is None:     # second check (with lock)
                    cls._instance = cls()
                    # lock release creates happens-before edge;
                    # subsequent readers see the fully constructed object
        return cls._instance
```

**What `volatile` gives you**:
- Visibility: every write is immediately visible to all threads (no CPU cache stale reads)
- Ordering: no reordering of instructions around the volatile write/read
- Does NOT give atomicity: `count++` is still not atomic with volatile

### CAS and ABA Problem

**CAS (Compare-And-Swap)**: atomic instruction `CMPXCHG` on x86. Reads current value, compares to expected, writes new value only if match — all atomically. Basis of all lock-free algorithms.

```python
import threading

class AtomicCounter:
    def __init__(self, initial=0):
        self._value = initial
        self._lock = threading.Lock()

    def compare_and_set(self, expected, new_value):
        with self._lock:
            if self._value == expected:
                self._value = new_value
                return True
            return False

counter = AtomicCounter(0)
# CAS: only sets to 6 if current value is 5
swapped = counter.compare_and_set(5, 6)

# Under the hood (CPython): the GIL serializes bytecode ops,
# but explicit locking is still needed for compound operations.
```

**ABA Problem**: Thread 1 reads value A. Thread 2 changes A→B→A. Thread 1's CAS(A, C) succeeds but operates on a different "A" than it read.

```
Thread 1: reads head = NodeA (value=1)
Thread 2: pops NodeA, pushes NodeB, pushes NodeA back (reuses same object)
Thread 1: CAS(head, NodeA, newNode) succeeds — but NodeB is now lost!
```

**Fix**: `AtomicStampedReference<T>` — pairs the reference with a monotonically increasing stamp (version).

```python
import threading

class AtomicStampedRef:
    """Pairs a reference with a monotonically increasing stamp to prevent ABA."""
    def __init__(self, initial, stamp=0):
        self._ref = initial
        self._stamp = stamp
        self._lock = threading.Lock()

    def get(self):
        with self._lock:
            return self._ref, self._stamp

    def compare_and_set(self, expected_ref, new_ref, expected_stamp, new_stamp):
        with self._lock:
            if self._ref is expected_ref and self._stamp == expected_stamp:
                self._ref = new_ref
                self._stamp = new_stamp
                return True
            return False

head = AtomicStampedRef(sentinel, stamp=0)

current, stamp = head.get()
# Only succeeds if both reference AND stamp match — ABA impossible
head.compare_and_set(current, new_node, stamp, stamp + 1)
```

### ThreadPoolExecutor Parameters

```python
import concurrent.futures
import threading
import queue

# Python's ThreadPoolExecutor: set max_workers (analogous to maximumPoolSize).
# For bounded queue + caller-runs backpressure, wrap with a Semaphore:
_semaphore = threading.Semaphore(100)  # bounded work queue of 100 tasks

def submit_with_caller_runs(executor, fn, *args):
    """CallerRunsPolicy: if semaphore is exhausted, caller runs the task itself."""
    acquired = _semaphore.acquire(blocking=False)
    if not acquired:
        fn(*args)   # caller runs — natural backpressure
        return
    def wrapped():
        try:
            return fn(*args)
        finally:
            _semaphore.release()
    return executor.submit(wrapped)

executor = concurrent.futures.ThreadPoolExecutor(max_workers=16)
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

```python
import concurrent.futures

# Python equivalent: recursive divide-and-conquer with ThreadPoolExecutor
# (no direct ForkJoinPool, but futures compose the same way)

def sum_task(arr, lo, hi, executor):
    if hi - lo <= 1000:
        # base case: compute sequentially
        return sum(arr[lo:hi])
    mid = (lo + hi) // 2
    left_future = executor.submit(sum_task, arr, lo, mid, executor)  # fork left
    right_result = sum_task(arr, mid, hi, executor)                   # compute right in this thread
    left_result = left_future.result()                                # join left
    return left_result + right_result

# Usage
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
    total = sum_task(arr, 0, len(arr), executor)
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
