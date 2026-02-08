# Dependency Inversion Principle (DIP)

_High-level modules should not depend on low-level modules. Both should depend on abstractions._

**Explanation:**
Instead of directly depending on concrete implementations, depend on abstractions (interfaces).

## Java Example

### Bad Design (Violating DIP)

```java
class MySQLDatabase {
    void connect() {
        System.out.println("Connecting to MySQL...");
    }
}

class Application {
    MySQLDatabase database = new MySQLDatabase();

    void start() {
        database.connect();
    }
}
```

Here, `Application` is tightly coupled to `MySQLDatabase`, making it hard to switch to another database.

### Good Design (Following DIP)

```java
interface Database {
    void connect();
}

class MySQLDatabase implements Database {
    public void connect() {
        System.out.println("Connecting to MySQL...");
    }
}

class PostgreSQLDatabase implements Database {
    public void connect() {
        System.out.println("Connecting to PostgreSQL...");
    }
}

class Application {
    private Database database;

    Application(Database database) {
        this.database = database;
    }

    void start() {
        database.connect();
    }
}
```

Now, `Application` depends on the `Database` interface, making it flexible to use any database.

---

## Python Example

### Bad Design (Violating DIP)

```python
class MySQLDatabase:
    def connect(self):
        print("Connecting to MySQL...")

class Application:
    def __init__(self):
        self.database = MySQLDatabase()

    def start(self):
        self.database.connect()
```

👎 **Problem:**
The `Application` class is tightly coupled to `MySQLDatabase`. Switching to another database requires modifying the application.

### Good Design (Following DIP)

```python
from abc import ABC, abstractmethod

class Database(ABC):
    @abstractmethod
    def connect(self):
        pass

class MySQLDatabase(Database):
    def connect(self):
        print("Connecting to MySQL...")

class PostgreSQLDatabase(Database):
    def connect(self):
        print("Connecting to PostgreSQL...")

class Application:
    def __init__(self, database: Database):
        self.database = database

    def start(self):
        self.database.connect()
```

👍 **Fix:**
Now, `Application` depends on the `Database` interface, making it easy to switch databases.
