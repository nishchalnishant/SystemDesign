# Singleton

> **Intent:** Singleton is a creational design pattern that lets you ensure that a class has **only
> one instance**, while providing a **global access point** to this instance.

---

## Problem

The Singleton pattern solves **two problems at the same time**, violating the *Single Responsibility
Principle*:

### 1. Ensure that a class has just a single instance.

Why would anyone want to control how many instances a class has? The most common reason is to
**control access to some shared resource** — for example, a database or a file.

Here's how it works: imagine that you created an object, but after a while decided to create a new
one. **Instead of receiving a fresh object, you'll get the one you already created.**

> Note that this behavior is **impossible to implement with a regular constructor**, since a
> constructor call must always return a new object by design.

*Clients may not even realize that they're working with the same object all the time.*

### 2. Provide a global access point to that instance.

Remember those **global variables** that you (all right, me) used to store some essential objects?
While they're very handy, they're also **very unsafe** since any code can potentially **overwrite**
the contents of those variables and crash the app.

Just like a global variable, the Singleton pattern lets you access some object from anywhere in the
program. **However, it also protects that instance from being overwritten by other code.**

There's another side to this problem: you don't want the code that solves problem #1 to be
**scattered all over your program**. It's much better to have it within **one class**, especially if
the rest of your code already depends on it.

> Nowadays, the Singleton pattern has become so popular that people may call something a singleton
> **even if it solves just one of the listed problems**.

---

## Solution

All implementations of the Singleton have these **two steps** in common:

1. **Make the default constructor private**, to prevent other objects from using the `new` operator
   with the Singleton class.
2. **Create a static creation method that acts as a constructor.** Under the hood, this method calls
   the private constructor to create an object and **saves it in a static field**. All following
   calls to this method **return the cached object**.

If your code has access to the Singleton class, then it's able to call the Singleton's static
method. So whenever that method is called, **the same object is always returned**.

---

## Real-World Analogy

**The government** is an excellent example of the Singleton pattern. A country can have only **one
official government**. Regardless of the personal identities of the individuals who form
governments, the title *"The Government of X"* is a **global point of access** that identifies the
group of people in charge.

---

## Structure

1. **The Singleton class** declares the static method `getInstance` that returns the same instance
   of its own class.
   - The Singleton's **constructor should be hidden** from the client code.
   - Calling the `getInstance` method should be the **only** way of getting the Singleton object.

```
┌──────────────────────────────────────┐
│            Singleton                 │
├──────────────────────────────────────┤
│ - static instance: Singleton         │
├──────────────────────────────────────┤
│ - Singleton()          «private»     │
│ + static getInstance(): Singleton    │
│     if (instance == null)            │
│       instance = new Singleton()     │
│     return instance                  │
└──────────────────────────────────────┘
              ▲
           Client
```

---

## Pseudocode

In this example, the **database connection class** acts as a Singleton. This class doesn't have a
public constructor, so the only way to get its object is to call the `getInstance` method. This
method **caches the first created object** and returns it in all subsequent calls.

```
 1  // The Database class defines the `getInstance` method that lets
 2  // clients access the same instance of a database connection
 3  // throughout the program.
 4  class Database is
 5    // The field for storing the singleton instance should be
 6    // declared static.
 7    private static field instance: Database
 8
 9    // The singleton's constructor should always be private to
10    // prevent direct construction calls with the `new`
11    // operator.
12    private constructor Database() is
13      // Some initialization code, such as the actual
14      // connection to a database server.
15      // ...
16
17    // The static method that controls access to the singleton
18    // instance.
19    public static method getInstance() is
20      if (Database.instance == null) then
21        acquireThreadLock() and then
22          // Ensure that the instance hasn't yet been
23          // initialized by another thread while this one
24          // has been waiting for the lock's release.
25          if (Database.instance == null) then
26            Database.instance = new Database()
27      return Database.instance
28
29    // Finally, any singleton should define some business logic
30    // which can be executed on its instance.
31    public method query(sql) is
32      // For instance, all database queries of an app go
33      // through this method. Therefore, you can place
34      // throttling or caching logic here.
35      // ...
36
37  class Application is
38    method main() is
39      Database foo = Database.getInstance()
40      foo.query("SELECT ...")
41      // ...
42      Database bar = Database.getInstance()
43      bar.query("SELECT ...")
44      // The variable `bar` will contain the same object as
45      // the variable `foo`.
```

> **Note the double-checked locking** in lines 20–26: the null check happens both before and after
> acquiring the thread lock.

---

## Java Implementation

A complete, compilable translation of the pseudocode above (`SingletonDemo.java`), including the
double-checked locking the book highlights.

```java
// ─── The Singleton ────────────────────────────────────────────────────
final class Database {

    /**
     * The field must be VOLATILE in Java. Without it, double-checked
     * locking is broken: another thread can observe a non-null
     * reference to a partially-constructed object, because the JIT and
     * the CPU are free to reorder the "allocate / construct / assign"
     * steps. volatile forbids that reordering.
     */
    private static volatile Database instance;

    private final String connectionString;

    /** Private: nobody outside can call `new Database()`. */
    private Database(String connectionString) {
        this.connectionString = connectionString;
        System.out.println("[Database] connecting to " + connectionString + " ...");
    }

    /** The static creation method that acts as the constructor. */
    public static Database getInstance() {
        if (instance == null) {                 // 1st check — no lock, the fast path
            synchronized (Database.class) {     // acquire the thread lock
                if (instance == null) {         // 2nd check — another thread may
                    instance = new Database("jdbc:demo://localhost/app");
                }                               //   have won the race while we waited
            }
        }
        return instance;
    }

    /** Business logic that runs on the single instance. */
    public void query(String sql) {
        System.out.println("[Database] executing: " + sql);
    }
}

// ─── Client ───────────────────────────────────────────────────────────
public class SingletonDemo {
    public static void main(String[] args) throws InterruptedException {
        Database foo = Database.getInstance();
        foo.query("SELECT * FROM users");

        Database bar = Database.getInstance();
        bar.query("SELECT * FROM orders");

        System.out.println("foo == bar ? " + (foo == bar));

        // Hammer it from several threads to show only one object is built.
        Runnable task = () -> Database.getInstance().query("SELECT 1");
        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("still one instance? " + (Database.getInstance() == foo));
    }
}
```

**Output**

```
[Database] connecting to jdbc:demo://localhost/app ...
[Database] executing: SELECT * FROM users
[Database] executing: SELECT * FROM orders
foo == bar ? true
[Database] executing: SELECT 1
[Database] executing: SELECT 1
still one instance? true
```

Note that `[Database] connecting ...` prints **exactly once**, even though `getInstance()` is called
five times across three threads.

### Notes on the Java translation

- `synchronized (Database.class)` is the Java form of the book's `acquireThreadLock()`. It locks on
  the class object, which is the natural monitor for a static method.
- The **first** null check exists purely for performance: once the instance is built, every
  subsequent call returns without touching a lock at all.
- The **second** null check exists for correctness: two threads can both pass check #1 and queue up
  on the lock; without check #2 the second one would build a second instance.
- `volatile` is not optional. Double-checked locking without it was a famously broken idiom in Java
  before the JSR-133 memory model (Java 5) gave `volatile` its happens-before guarantee.

### The four Java variants, and when to use each

**1. Eager initialization** — simplest, thread-safe for free (the JVM guarantees class
initialization is synchronized). Use it when construction is cheap and the instance is always used.

```java
final class Config {
    private static final Config INSTANCE = new Config();
    private Config() { }
    public static Config getInstance() { return INSTANCE; }
}
```

**2. Synchronized method** — correct but slow: every single call pays for the lock, forever.

```java
public static synchronized Database getInstance() {
    if (instance == null) instance = new Database();
    return instance;
}
```

**3. Initialization-on-demand holder idiom** — lazy *and* lock-free, with no `volatile` subtlety.
The nested class isn't loaded until `getInstance()` first references it, and the JVM handles the
synchronization. **This is the best lazy singleton in plain Java.**

```java
final class Database {
    private Database() { }

    private static class Holder {
        static final Database INSTANCE = new Database();
    }

    public static Database getInstance() {
        return Holder.INSTANCE;
    }
}
```

**4. Enum singleton** — *Effective Java* Item 3 calls this "the best way to implement a singleton."
It is serialization-safe and reflection-proof for free; the other three are not.

```java
enum Database {
    INSTANCE;

    public void query(String sql) {
        System.out.println("executing: " + sql);
    }
}

// Usage: Database.INSTANCE.query("SELECT 1");
```

### The attacks the naive versions don't survive

Variants 1–3 can all be broken; the enum cannot.

- **Reflection:** `Constructor<Database> c = Database.class.getDeclaredConstructor();`
  `c.setAccessible(true); c.newInstance();` — a brand-new second instance. Defend by throwing from
  the private constructor if `instance != null`.
- **Serialization:** deserializing produces a fresh object. Defend by implementing
  `private Object readResolve() { return getInstance(); }`.
- **Cloning:** override `clone()` to throw `CloneNotSupportedException` (or just don't implement
  `Cloneable`).
- **Multiple classloaders:** each classloader gets its own copy of the class, hence its own
  instance. Rarely fixable from inside the class.

### Where this appears in the JDK

- `java.lang.Runtime.getRuntime()`
- `java.awt.Desktop.getDesktop()`
- `java.awt.Toolkit.getDefaultToolkit()`
- `java.lang.System` (all-static utility, a close relative)
- Spring beans are singleton-scoped **by default** — which is the modern answer: let the DI
  container own the lifecycle instead of hard-coding it into the class. That preserves
  testability, which is the main practical criticism of the pattern.

---

## Applicability

### ▸ Use the Singleton pattern when a class in your program should have just a single instance available to all clients.

For example, a single database object shared by different parts of the program.

The Singleton pattern **disables all other means of creating objects of a class** except for the
special creation method. This method either creates a new object or returns an existing one if it
has already been created.

### ▸ Use the Singleton pattern when you need stricter control over global variables.

Unlike global variables, the Singleton pattern **guarantees that there's just one instance** of a
class. **Nothing, except for the Singleton class itself, can replace the cached instance.**

> Note that you can always adjust this limitation and allow creating **any number** of Singleton
> instances. The only piece of code that needs changing is the body of the `getInstance` method.

---

## How to Implement

1. Add a **private static field** to the class for storing the singleton instance.

2. Declare a **public static creation method** for getting the singleton instance.

3. Implement **"lazy initialization"** inside the static method. It should create a new object on
   its first call and put it into the static field. The method should always return that instance on
   all subsequent calls.

4. **Make the constructor of the class private.** The static method of the class will still be able
   to call the constructor, but not the other objects.

5. Go over the client code and **replace all direct calls to the singleton's constructor** with
   calls to its static creation method.

---

## Pros and Cons

**✅ Pros**

- You can be sure that a class has **only a single instance**.
- You gain a **global access point** to that instance.
- The singleton object is **initialized only when it's requested for the first time**.

**❌ Cons**

- **Violates the Single Responsibility Principle.** The pattern solves two problems at the time.
- The Singleton pattern can **mask bad design**, for instance, when the components of the program
  know too much about each other.
- The pattern requires **special treatment in a multithreaded environment** so that multiple threads
  won't create a singleton object several times.
- It may be **difficult to unit test** the client code of the Singleton because many test frameworks
  rely on inheritance when producing mock objects. Since the constructor of the singleton class is
  private and **overriding static methods is impossible** in most languages, you will need to think
  of a creative way to mock the singleton. *Or just don't write the tests. Or don't use the Singleton
  pattern.*

---

## Relations with Other Patterns

- A **Facade** class can often be transformed into a **Singleton** since a single facade object is
  sufficient in most cases.
- **Flyweight** would resemble Singleton if you somehow managed to reduce all shared states of the
  objects to just one flyweight object. But there are **two fundamental differences**:
  1. There should be only **one** Singleton instance, whereas a Flyweight class can have **multiple**
     instances with different intrinsic states.
  2. The Singleton object can be **mutable**. Flyweight objects are **immutable**.
- **Abstract Factories, Builders and Prototypes can all be implemented as Singletons.**
