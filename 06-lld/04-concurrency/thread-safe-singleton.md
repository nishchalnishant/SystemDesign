> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Thread-Safe Singleton Pattern — how to ensure a class has exactly one instance, even when multiple threads try to create it simultaneously.
>
> **Key concepts:**
> - The problem: a basic lazy-loaded singleton (`if (instance == null) instance = new Singleton();`) causes race conditions. Two threads evaluating the `null` check simultaneously will create two instances.
> - The bad fix: adding `synchronized` to the method signature. This kills performance because *every* call to `getInstance()` acquires a lock, even after the instance is created.
> - The correct fix: Double-Checked Locking. Check for null, enter `synchronized(Singleton.class)` block, check for null again, then instantiate.
> - The trap: you MUST declare the instance variable as `volatile`. Without `volatile`, compiler instruction reordering can cause another thread to see a partially constructed object.
> - The best fix: Joshua Bloch's Enum Singleton (`public enum Singleton { INSTANCE; }`). The JVM guarantees thread safety and serialization safety automatically.
>
> **Key takeaway:** If asked to write a Singleton in an interview, write the Double-Checked Locking version and explicitly explain why the `volatile` keyword is absolutely necessary.

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
├── Solution 3: Enum (Best in Java)
│   ├── JVM guarantees single initialization (class loading is thread-safe)
│   ├── Prevents reflection attacks (can't call private constructor via reflection)
│   └── Serialization-safe: no extra readResolve() needed
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

```java
public class Database {
    private static Database instance = null;  // null initially

    public static Database getInstance() {
        if (instance == null) {                // Thread A checks: null → enters
                                                 // (Thread B also checks: null → enters)
            instance = new Database();          // Thread A creates instance A
                                                 // Thread B also creates instance B — RACE
        }
        return instance;
    }
}
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

## Solution 1: Synchronized Method (Simple but Slow)

```java
public class Singleton {
    private static Singleton instance = null;

    private Singleton() { }

    // Thread-safe but SLOW — acquires lock on every call
    public static synchronized Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}
```

**Problem**: Every call to `getInstance()` acquires lock, even after initialization (99.99% unnecessary).

---

## Solution 2: Double-Checked Locking (Optimal)

```java
public class Singleton {
    // volatile is mandatory — prevents reordering that exposes a
    // partially-constructed object to other threads
    private static volatile Singleton instance = null;

    private Singleton() { }

    public static Singleton getInstance() {
        if (instance == null) {                    // first check (no locking) — FAST PATH
            synchronized (Singleton.class) {        // lock only if null
                if (instance == null) {             // second check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;                            // subsequent calls skip lock entirely
    }
}
```

**Why `volatile`?**
- Without `volatile`, thread A might see partially constructed object
- `volatile` ensures all writes complete before instance is visible

---

## Solution 3: Enum Singleton (Best in Java)

```java
public enum Singleton {
    INSTANCE;

    public void doSomething() {
        System.out.println("Singleton is working!");
    }
}

// Usage:
// Singleton.INSTANCE.doSomething();
```

**Why best?**
- Thread-safe by JVM guarantee
- Prevents reflection attacks
- Serialization-safe automatically

---

## Real-World Example: Thread-Safe Connection Pool

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ConnectionPool {
    private static final int POOL_SIZE = 10;
    private static volatile ConnectionPool instance = null;

    private final BlockingQueue<Object> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    private ConnectionPool() {
        for (Object conn : createConnections(POOL_SIZE)) {
            pool.offer(conn);
        }
    }

    private Object[] createConnections(int size) {
        Object[] connections = new Object[size];
        for (int i = 0; i < size; i++) {
            connections[i] = new Object();  // placeholder connections
        }
        return connections;
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            synchronized (ConnectionPool.class) {
                if (instance == null) {
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }

    public Object borrowConnection() throws InterruptedException {
        return pool.take();   // blocks if pool empty
    }

    public void returnConnection(Object conn) {
        pool.offer(conn);
    }
}

// Usage (thread-safe)
ConnectionPool pool = ConnectionPool.getInstance();
Object conn = pool.borrowConnection();
try {
    // use connection
} finally {
    pool.returnConnection(conn);
}
```

---

## Testing Multi-Threaded Singleton

```java
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;

public class SingletonThreadSafetyTest {

    public static void testThreadSafety() throws InterruptedException {
        int numThreads = 100;
        Set<Integer> instances = Collections.newSetFromMap(new ConcurrentHashMap<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();                       // all threads start together
                    Singleton inst = Singleton.getInstance();
                    instances.add(System.identityHashCode(inst));  // identity, not equals()
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();   // release all threads simultaneously
        doneLatch.await();        // wait for all threads
        executor.shutdown();

        // Should be only 1 unique instance id despite 100 threads
        assert instances.size() == 1 : "Expected 1 instance, got " + instances.size();
    }

    public static void main(String[] args) throws InterruptedException {
        testThreadSafety();
    }
}
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

**Recommendation**: Use enum singleton in Java for its safety guarantees. Fall back to double-checked locking when you need lazy construction with constructor arguments that the enum idiom can't express cleanly.

---

## Applied In

This concept is used by **2 problems** in this repo:

**Low-Level Design**

- [Design a Logger Library](../05-problems/03-domain-specific/19-design-logger-library.md)
- [Design Concurrent LRU Cache](../05-problems/04-advanced-niche/35-design-concurrent-lru-cache.md)

