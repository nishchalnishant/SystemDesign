# Singleton Pattern

> **Purpose**: Ensure a class has only one instance and provide a global access point to it

## When to Use

✅ Database connection pool  
✅ Configuration manager  
✅ Logging service  
✅ Cache manager  

❌ Don't overuse - makes testing difficult (use dependency injection instead)

## Implementation

### Basic Singleton (Not Thread-Safe)

```java
public class Singleton {
    private static Singleton instance;
    
    private Singleton() {
        // Private constructor prevents instantiation
    }
    
    public static Singleton getInstance() {
        if (instance == null) {
            instance = new Singleton();
        }
        return instance;
    }
}
```

**Problem**: Not thread-safe! Two threads can create two instances.

---

### Thread-Safe Singleton (Double-Checked Locking)

```java
public class ThreadSafeSingleton {
    private static volatile ThreadSafeSingleton instance;
    
    private ThreadSafeSingleton() {}
    
    public static ThreadSafeSingleton getInstance() {
        if (instance == null) {  // First check (no locking)
            synchronized (ThreadSafeSingleton.class) {
                if (instance == null) {  // Second check (with lock)
                    instance = new ThreadSafeSingleton();
                }
            }
        }
        return instance;
    }
}
```

**Key**: `volatile` ensures visibility across threads, double-check reduces lock overhead.

---

### Enum Singleton (Best in Java)

```java
public enum Singleton {
    INSTANCE;
    
    public void doSomething() {
        // Business logic
    }
}

// Usage
Singleton.INST ANCE.doSomething();
```

**Why best**: Thread-safe by JVM, prevents reflection attacks, serialization-safe.

---

## Real-World Example: Database Connection Pool

```java
public class ConnectionPool {
    private static volatile ConnectionPool instance;
    private List<Connection> availableConnections;
    private List<Connection> usedConnections;
    private static final int MAX_POOL_SIZE = 10;
    
    private ConnectionPool() {
        availableConnections = new ArrayList<>();
        usedConnections = new ArrayList<>();
        
        // Initialize connection pool
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            availableConnections.add(createNewConnection());
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
    
    public synchronized Connection getConnection() {
        if (availableConnections.isEmpty()) {
            throw new RuntimeException("No available connections");
        }
        Connection connection = availableConnections.remove(0);
        usedConnections.add(connection);
        return connection;
    }
    
    public synchronized void releaseConnection(Connection connection) {
        usedConnections.remove(connection);
        availableConnections.add(connection);
    }
}

// Usage
ConnectionPool pool = ConnectionPool.getInstance();
Connection conn = pool.getConnection();
// Use connection
pool.releaseConnection(conn);
```

---

## Pros & Cons

**Pros:**
- ✅ Controlled access to single instance
- ✅ Reduced memory (one instance)
- ✅ Global access point

**Cons:**
- ❌ Violates Single Responsibility (manages own lifecycle + business logic)
- ❌ Difficult to unit test (global state)
- ❌ Can hide dependencies (not obvious from constructor)

---

## Alternative: Dependency Injection

Instead of Singleton, prefer DI:

```java
// Bad: Singleton
public class UserService {
    public void createUser() {
        DatabasePool.getInstance().getConnection();  // Hidden dependency!
    }
}

// Good: Dependency Injection
public class UserService {
    private final DatabasePool pool;
    
    public UserService(DatabasePool pool) {  // Explicit dependency
        this.pool = pool;
    }
    
    public void createUser() {
        pool.getConnection();
    }
}
```

---

## Interview Tips

**Q: "Why use Singleton?"**
- A: "Ensure single instance (e.g., config manager). But I'd prefer DI for testability."

**Q: "Is your Singleton thread-safe?"**
- A: "Yes, using double-checked locking with volatile keyword"
