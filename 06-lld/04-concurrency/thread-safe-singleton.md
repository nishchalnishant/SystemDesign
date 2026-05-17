# Thread-Safe Singleton Pattern

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
