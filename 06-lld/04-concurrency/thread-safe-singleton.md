# Thread-Safe Singleton Pattern

## Question

Two threads call `Database.getInstance()` at the exact same time. The lazy-initialization singleton checks `if (instance == null)`. What happens? Can both threads get different instances?

Reason through the exact interleaving before reading on.

---

## Race Condition Without Synchronization

```java
class Database {
    private static Database instance; // null initially

    public static Database getInstance() {
        if (instance == null) {                // Thread A checks: null → enters
                                               // (Thread B also checks: null → enters)
            instance = new Database();         // Thread A creates instance A
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
    private static Singleton instance;
    
    private Singleton() {}
    
    // Synchronized = thread-safe, but SLOW (locks every time)
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
    // volatile ensures visibility across threads
    private static volatile Singleton instance;
    
    private Singleton() {}
    
    public static Singleton getInstance() {
        if (instance == null) {  // First check (no locking) - FAST PATH
            synchronized (Singleton.class) {  // Lock only if null
                if (instance == null) {  // Second check (with lock)
                    instance = new Singleton();
                }
            }
        }
        return instance;  // Subsequent calls skip lock entirely
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
    
    // Add your methods here
    public void doSomething() {
        System.out.println("Singleton is working!");
    }
}

// Usage
Singleton.INSTANCE.doSomething();
```

**Why best?**
- Thread-safe by JVM guarantee
- Prevents reflection attacks
- Serialization-safe automatically

---

## Real-World Example: Thread-Safe Connection Pool

```java
public class ConnectionPool {
    private static volatile ConnectionPool instance;
    private final BlockingQueue<Connection> pool;
    private static final int POOL_SIZE = 10;
    
    private ConnectionPool() {
        pool = new ArrayBlockingQueue<>(POOL_SIZE);
        for (int i = 0; i < POOL_SIZE; i++) {
            pool.add(createConnection());
        }
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
    
    public Connection borrowConnection() throws InterruptedException {
        return pool.take();  // Blocks if pool empty
    }
    
    public void returnConnection(Connection conn) {
        pool.offer(conn);
    }
}

// Usage (thread-safe)
ConnectionPool pool = ConnectionPool.getInstance();
Connection conn = pool.borrowConnection();
try {
    // Use connection
} finally {
    pool.returnConnection(conn);
}
```

---

## Testing Multi-Threaded Singleton

```java
@Test
public void testThreadSafety() throws Exception {
    int numThreads = 100;
    Executor Executor = Executors.newFixedThreadPool(numThreads);
    
    Set<Singleton> instances = Collections.synchronizedSet(new HashSet<>());
    CountDownLatch latch = new CountDownLatch(numThreads);
    
    for (int i = 0; i < numThreads; i++) {
        executor.execute(() -> {
            instances.add(Singleton.getInstance());
            latch.countDown();
        });
    }
    
    latch.await();  // Wait for all threads
    executor.shutdown();
    
    // Should be only 1 instance despite 100 threads
    assertEquals(1, instances.size());
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

**Recommendation**: Use enum in Java. Use DCL in other languages (C++, Python).
