---
module: 06-lld
topic: Concurrency
status: unread
tags: [06-lld, system-design, concurrency]
---
# Thread-Safe Singleton Pattern

## Question

Two threads call `Database.getInstance()` at the exact same time. The lazy-initialization singleton checks `if (instance == null)`. What happens? Can both threads get different instances?

Reason through the exact interleaving before reading on.

---

## Topic Mindmap

```
[Thread-Safe Singleton]
├── Problem It Solves
│   ├── Two threads both see instance == null → both call new Database()
│   ├── Thread B's instance is discarded; its state (connections, config) lost
│   └── Visibility issue: even without double-creation, partially-constructed object visible
├── Root Race Condition
│   ├── Thread A: check null → true → pause before assignment
│   ├── Thread B: check null → true → creates and assigns instance B
│   ├── Thread A: resumes → creates and assigns instance A (overwrites B)
│   └── Both threads got different objects; instance B's state is gone
├── Constraints
│   ├── Atomicity: check + create + assign must be atomic
│   └── Visibility: assignment must be visible to all threads immediately (volatile)
├── Solution 1: Synchronized Method
│   ├── public static synchronized getInstance()
│   ├── Thread-safe: lock prevents double creation
│   └── Slow: every call acquires lock even after first initialization
├── Solution 2: Double-Checked Locking (Optimal)
│   ├── private static volatile instance (volatile mandatory)
│   ├── First check (no lock): fast path for 99.99% of calls
│   ├── synchronized block: create only if still null
│   └── volatile prevents: partially-constructed object visibility across CPUs
├── Testing Thread Safety
│   ├── 100 threads simultaneously call getInstance()
│   ├── CountDownLatch ensures all threads start at the same time
│   ├── Collect all instances in ConcurrentHashSet
│   └── Assert set.size() == 1
└── Interview Angles
    ├── Why does DCL require volatile even with synchronized?
    ├── What is the publication safety problem?
    └── How does enum prevent reflection-based singleton breaking?
```

## Race Condition Without Synchronization

```python
class Database:
    _instance = None  # None initially

    @classmethod
    def get_instance(cls):
        if cls._instance is None:          # Thread A checks: None → enters
                                           # (Thread B also checks: None → enters)
            cls._instance = cls()          # Thread A creates instance A
                                           # Thread B also creates instance B — RACE
        return cls._instance
```

**Exact failure interleaving**:
1. Thread A calls `getInstance()`. Reads `instance == null` → `true`. Pauses.
2. Thread B calls `getInstance()`. Reads `instance == null` → `true` (Thread A hasn't assigned yet). Proceeds.
3. Thread B creates `new Database()` → assigns to `instance`.
4. Thread A resumes, creates another `new Database()` → overwrites `instance`.

Result: both threads got different objects. Thread B's instance is discarded. Any state Thread B stored in its instance (open connections, cached config) is lost. The invariant "exactly one instance" is broken.

**Additional problem — visibility without `volatile`**: Even without the double-creation race, the JVM can reorder the write to `instance`. Thread A might see a non-null but partially-constructed `Database` object where the constructor has not finished running yet. This is the publication safety problem.

**Constraints derived**:
- **Atomicity**: `check + create + assign` must be atomic (or structurally prevented after first assignment).
- **Visibility**: The assignment to `instance` must be visible to all threads immediately (`volatile`).

---

## Derive the Fix

Three approaches, ordered from correct-but-slow to correct-and-fast:

1. `synchronized` on the method → correct, but every `getInstance()` call acquires a lock (unnecessary after initialization).
2. Double-Checked Locking with `volatile` → check without lock, lock only to create, `volatile` ensures visibility.
3. Enum singleton → JVM class loading guarantees single initialization and thread safety without any manual synchronization.

---

> **Problem**: Implement a Singleton that works correctly with multiple threads accessing it simultaneously

## The Challenge

In multi-threaded environments, two threads can create two instances if not carefully implemented.

## Solution 1: Lock on Every Call (Simple but Slow)

```python
import threading

class Singleton:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        # Synchronized = thread-safe, but SLOW (locks every time)
        with cls._lock:
            if cls._instance is None:
                cls._instance = super().__new__(cls)
        return cls._instance
```

**Problem**: Every call to `Singleton()` acquires the lock, even after initialization (99.99% unnecessary).

---

## Solution 2: Double-Checked Locking (Optimal)

```python
import threading

class Singleton:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:          # First check (no locking) - FAST PATH
            with cls._lock:                # Lock only if None
                if cls._instance is None:  # Second check (with lock)
                    cls._instance = super().__new__(cls)
        return cls._instance               # Subsequent calls skip lock entirely
```

**Why does Python DCL work?**
- Python's GIL provides memory visibility guarantees that replace `volatile`
- The double check still matters for correctness when multiple threads race on the first check

---

## Solution 3: Module-Level Singleton (Best in Python)

```python
# singleton_module.py
# Python modules are loaded once and cached — the module object IS the singleton.

class _Singleton:
    def do_something(self):
        print("Singleton is working!")

instance = _Singleton()

# Usage (in any other file):
# from singleton_module import instance
# instance.do_something()
```

**Why best?**
- Thread-safe by Python's import system guarantee (modules initialized once)
- No boilerplate `__new__` or lock required
- Thread-safe by Python's import system — no extra locking needed

---

## Real-World Example: Thread-Safe Connection Pool

```python
import queue
import threading
from contextlib import contextmanager

POOL_SIZE = 10

class ConnectionPool:
    _instance = None  # ConnectionPool | None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._pool = queue.Queue(maxsize=POOL_SIZE)  # Queue of connections
                    for _ in range(POOL_SIZE):
                        cls._instance._pool.put(create_connection())
        return cls._instance

    def borrow_connection(self):
        return self._pool.get()   # Blocks if pool empty

    def return_connection(self, conn):
        self._pool.put(conn)

    @contextmanager
    def connection(self):
        conn = self.borrow_connection()
        try:
            yield conn
        finally:
            self.return_connection(conn)

# Usage (thread-safe)
pool = ConnectionPool()
with pool.connection() as conn:
    pass  # Use connection
```

---

## Testing Multi-Threaded Singleton

```python
import threading
from concurrent.futures import ThreadPoolExecutor

def test_thread_safety():
    num_threads = 100
    instances = set()  # collects id() of each returned instance
    lock = threading.Lock()
    barrier = threading.Barrier(num_threads)  # All threads start at the same time

    def grab_instance():
        barrier.wait()
        inst = Singleton()
        with lock:
            instances.add(id(inst))

    with ThreadPoolExecutor(max_workers=num_threads) as executor:
        futures = [executor.submit(grab_instance) for _ in range(num_threads)]
        for f in futures:
            f.result()

    # Should be only 1 instance despite 100 threads
    assert len(instances) == 1, f"Expected 1 instance, got {len(instances)}"

test_thread_safety()
```

---

## Common Interview Questions

**Q: Why use double-checked locking instead of synchronizing entire method?**
- A: Performance. Synchronized method locks on every call (slow). DCL locks only during initialization.

**Q: What happens without volatile keyword?**
- A: Thread B might see partially constructed object (instruction reordering). Volatile prevents this.

**Q: How does enum solve thread-safety?**
- A: JVM guarantees enum initialization is thread-safe and happens exactly once.

---

## Comparison

| Approach | Thread-Safe? | Performance | Serialization-Safe? |
|----------|-------------|-------------|---------------------|
| Synchronized method | ✅ | ❌ Slow | ❌ |
| Double-checked locking | ✅ | ✅ Fast | ❌ |
| Enum | ✅ | ✅ Fast | ✅ Best |

**Recommendation**: Use module-level singleton (Python import system guarantees it). Use DCL when you need lazy initialization with a class instance.

---

## Interviewer Follow-Up Questions

- "Why is the naive singleton pattern not thread-safe?" → Naive: `if instance is None: instance = Singleton()`. In multi-threaded code: Thread 1 checks `is None` (True), Thread 2 checks `is None` (True, before T1 creates the instance), Thread 1 creates instance, Thread 2 creates another instance. Two instances exist — singleton is violated. The check-then-act is not atomic.
- "How does double-checked locking fix this? Why is `volatile` necessary in Java?" → DCL: `if instance is None: with lock: if instance is None: instance = Singleton()`. Outer check avoids taking the lock on every call (fast path). Inner check handles the race between the outer check and lock acquisition. In Java: without `volatile`, the JVM can reorder memory writes — the reference to the new instance can become visible to other threads before the constructor finishes executing (partially-constructed object). `volatile` prevents this reordering. Python: GIL makes this less critical, but the double-check is still correct practice.
- "What's the simplest thread-safe singleton in Python?" → Module-level instantiation: `# module.py → instance = MySingleton()`. Python imports are protected by an import lock — the module body runs exactly once. `from module import instance` everywhere gives all callers the same object. No explicit synchronization needed. This is simpler and more Pythonic than class-based singletons.
- "How do you test code that uses a singleton without the singleton state leaking between tests?" → (1) Add a class-level `_reset()` method that clears `_instance = None` — call it in `setUp`/`tearDown`. (2) Better: don't use singletons — inject the dependency. In tests, inject a fresh instance; in production, inject the shared instance. (3) Use `unittest.mock.patch` to replace the singleton reference. The root cause of this problem is using singletons instead of dependency injection — the test friction is a signal.
