> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Concurrency principles for Low-Level Design interviews. Multithreading is a core requirement for almost all LLD problems.
>
> **The Files:**
> - thread-safe-singleton.md: How to correctly implement a Singleton in a multithreaded environment (Double-Checked Locking, `volatile`, Enum singletons).
> - producer-consumer.md: The classic concurrency problem. How to use `wait()`/`notify()` and `BlockingQueue` to safely pass data between threads.
> - concurrency-patterns.md: Core tools from `java.util.concurrent` — Locks (ReadWriteLock), Semaphores (rate limiting), CountDownLatch (waiting for dependencies), and Thread Pools.
> - futures-async-patterns.md: Asynchronous programming using `CompletableFuture`. How to chain non-blocking calls, handle timeouts, and manage multiple parallel tasks (`allOf`/`anyOf`).
>
> **Key takeaway:** Never use raw Threads (`new Thread().start()`) in an interview. Always use an `ExecutorService` (Thread Pool). Understand `ConcurrentHashMap` and `BlockingQueue` — they solve 90% of concurrency state issues without manual locking.

---

# Concurrency

Concurrency patterns and examples for LLD and coding interviews.

## Contents

| Topic | File | Description |
|-------|------|-------------|
| **Producer-Consumer** | [producer-consumer.md](producer-consumer.md) | BlockingQueue, wait/notify, bounded buffer |
| **Thread-Safe Singleton** | [thread-safe-singleton.md](thread-safe-singleton.md) | Singleton with correct concurrency |
| **Concurrency Patterns** | [concurrency-patterns.md](concurrency-patterns.md) | Read-Write Lock, Semaphore, CountDownLatch, CyclicBarrier, ThreadPool, Atomic ops, volatile, concurrent data structures |
| **Futures & Async Patterns** | [futures-async-patterns.md](futures-async-patterns.md) | Future, CompletableFuture, allOf/anyOf, timeout, exception handling, promise bridge |

Useful when discussing thread safety, shared state, and synchronization in low-level design.
