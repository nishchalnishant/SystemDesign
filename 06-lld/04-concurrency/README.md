# Concurrency

Concurrency primitives and patterns for LLD interviews.

## Contents

| Topic | File | Description |
|-------|------|-------------|
| **Locks and Semaphores** | [locks-semaphores.md](locks-semaphores.md) | Lock, RLock, Semaphore, Condition, deadlock avoidance |
| **Producer-Consumer** | [producer-consumer.md](producer-consumer.md) | BlockingQueue, wait/notify, bounded buffer |
| **Thread-Safe Singleton** | [thread-safe-singleton.md](thread-safe-singleton.md) | Double-checked locking singleton |

## When Each Primitive Is Correct

- **`threading.Lock`**: one thread at a time in a critical section (check-and-set, seat locking, counter updates).
- **`threading.Semaphore(N)`**: max N threads concurrently (connection pool, rate limiting).
- **`threading.Condition`**: a thread must wait until another thread changes state (producer-consumer with custom predicate).
- **`queue.Queue`**: thread-safe producer-consumer — prefer this over manual Condition when the predicate is just "not empty / not full".
