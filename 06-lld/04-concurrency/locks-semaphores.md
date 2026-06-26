---
module: 06-lld
topic: Concurrency
status: unread
tags: [06-lld, system-design, concurrency]
---
# Locks and Semaphores

The three primitives you need for SDE-2 concurrency problems. Know when each one is correct.

---

## threading.Lock — Mutual Exclusion

Ensures only one thread runs a critical section at a time. Use whenever shared mutable state is read and written by multiple threads.

```python
import threading

class Counter:
    def __init__(self) -> None:
        self._value = 0
        self._lock = threading.Lock()

    def increment(self) -> None:
        with self._lock:      # acquire on enter, release on exit (even on exception)
            self._value += 1  # check-and-modify is now atomic

    def get(self) -> int:
        with self._lock:
            return self._value
```

**Rule**: Always use `with lock:` — never call `acquire()` / `release()` manually. The `with` form releases on exception; manual release does not.

---

## threading.RLock — Reentrant Lock

Same as `Lock` but the same thread can acquire it multiple times without deadlocking itself. Use when a method holding a lock calls another method that also acquires the same lock.

```python
import threading

class SafeList:
    def __init__(self) -> None:
        self._items: list[int] = []
        self._lock = threading.RLock()  # reentrant

    def add(self, item: int) -> None:
        with self._lock:
            self._items.append(item)
            self._log_change()  # also acquires _lock — fine with RLock, deadlock with Lock

    def _log_change(self) -> None:
        with self._lock:       # second acquire by same thread
            print(f"size={len(self._items)}")
```

---

## threading.Semaphore — Bounded Concurrency

Controls how many threads can enter a section simultaneously. Use for connection pools, rate limiting, or any "max N concurrent" requirement.

```python
import threading
import time

class ConnectionPool:
    def __init__(self, max_connections: int) -> None:
        self._semaphore = threading.Semaphore(max_connections)

    def execute_query(self, query: str) -> str:
        with self._semaphore:     # blocks if max_connections threads are already inside
            return self._run(query)

    def _run(self, query: str) -> str:
        time.sleep(0.1)  # simulate DB query
        return f"result({query})"
```

**Lock vs Semaphore**:
- `Lock` = Semaphore(1): only one thread at a time, and only the acquirer can release.
- `Semaphore(N)`: up to N threads simultaneously; any thread can release.

---

## threading.Condition — Wait/Notify

Lets threads wait for a specific condition and be notified when it changes. Use for producer-consumer coordination.

```python
import threading
from collections import deque

class BoundedBuffer:
    def __init__(self, capacity: int) -> None:
        self._buffer: deque[int] = deque()
        self._capacity = capacity
        self._condition = threading.Condition()

    def put(self, item: int) -> None:
        with self._condition:
            while len(self._buffer) >= self._capacity:
                self._condition.wait()          # releases lock, sleeps, reacquires on wake
            self._buffer.append(item)
            self._condition.notify_all()        # wake waiting consumers

    def get(self) -> int:
        with self._condition:
            while not self._buffer:
                self._condition.wait()          # releases lock, sleeps, reacquires on wake
            item = self._buffer.popleft()
            self._condition.notify_all()        # wake waiting producers
            return item
```

**Why `while` not `if`**: Spurious wakeups — a thread can wake up even if `notify()` wasn't called. The `while` re-checks the condition and waits again if it's still not satisfied.

---

## Double-Checked Locking (Singleton)

Avoid the lock overhead on every call by checking first without the lock, then again inside:

```python
import threading

class Singleton:
    _instance = None
    _lock = threading.Lock()

    @classmethod
    def get_instance(cls) -> "Singleton":
        if cls._instance is None:           # fast path, no lock
            with cls._lock:
                if cls._instance is None:   # second check under lock
                    cls._instance = cls()
        return cls._instance
```

The inner check is necessary because two threads can both pass the outer `if` before either acquires the lock.

---

## Deadlock — Causes and Avoidance

**Deadlock requires all four conditions simultaneously**:
1. Mutual exclusion (lock grants exclusive access)
2. Hold and wait (thread holds one lock while waiting for another)
3. No preemption (locks can't be forcibly taken)
4. Circular wait (A waits for B's lock; B waits for A's lock)

**Prevention — lock ordering**: Always acquire locks in the same global order across all threads.

```python
# Deadlock: Thread 1 acquires lock_a then lock_b; Thread 2 acquires lock_b then lock_a
# Fix: both threads acquire in order (lock_a, lock_b)

import threading

lock_a = threading.Lock()
lock_b = threading.Lock()

def transfer(from_account, to_account, amount):
    # Always acquire in consistent order based on id()
    first, second = (lock_a, lock_b) if id(from_account) < id(to_account) else (lock_b, lock_a)
    with first:
        with second:
            from_account.balance -= amount
            to_account.balance += amount
```

**BookMyShow seat locking note**: That problem uses a single per-show mutex for all seat operations — it avoids per-seat locks precisely because per-seat locks risk deadlock when two users simultaneously request overlapping seat sets in different orders.

---

## Quick Reference

| Primitive | Use case | Key behavior |
|-----------|----------|-------------|
| `threading.Lock` | One thread at a time | Non-reentrant; `with` form is mandatory |
| `threading.RLock` | Same thread may re-enter | Same thread can acquire N times; must release N times |
| `threading.Semaphore(N)` | Max N threads concurrently | Any thread can release; use for pools and rate limits |
| `threading.Condition` | Wait for a state change | Always use `while` loop around `wait()` |
| `queue.Queue` | Thread-safe producer-consumer | Preferred over manual Condition; handles blocking internally |

---

## When to Reach for queue.Queue Instead

`queue.Queue` implements a thread-safe FIFO with built-in blocking. For producer-consumer problems, prefer it over a manual `Condition` + `deque`:

```python
import queue
import threading

q: queue.Queue[int] = queue.Queue(maxsize=10)

def producer() -> None:
    for i in range(20):
        q.put(i)        # blocks if full

def consumer() -> None:
    while True:
        item = q.get()  # blocks if empty
        print(item)
        q.task_done()
```

Use manual `Condition` only when you need a non-standard wait predicate (e.g., "wait until buffer has ≥ 3 items").
