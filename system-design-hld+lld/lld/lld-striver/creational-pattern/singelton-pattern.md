# Singelton pattern

* Ensures that a class has only one instance and provides a global point of access to that instance
* It restricts  an object has only one instance and parts of the application uses the same object.
* It is needed in scenarios where creating multiple objects of a class might be problamatic like logging, configuration handling or managing database connections
* ex — If we are in office printer and everyone uses it then the printer might overvhelmed, instead of that we have a print spooler which manages all print jobs . No matter who initiates the print job they all go through one central spooler instance that queues and handles the tasks in order
* Working of singleton pattern&#x20;
  * singleton pattern involves following patterns&#x20;
    * private constructor: prevents instamtoatopm from outside the class
    * static variable: Holds the single instance of the class
    * public static method: provides a global access point to get the instance
* Approaches to implement singleton patterns
  * eager loading
  * lazy loading
* Eager loading( early initialisation)
  * the singleton instance is created as soon as the class is loaded, regardless of whether it is ever used
  * Real world analogy
    * a fire extinguisher is always present even if the fire never occurs. Similarly eager loading created the singleton instances upfront just in case it needed
  * Understanding&#x20;
    * the object is created immediately when the class is loaded.
    * it is always available and inherently thread safe
  * Pros
    * very simple to implement
    * thread safe without any extra handling
  * Cons
    * wastes memory if the instance is never used
    * not suitable for heavy objects

```python
# Class implementing Eager Loading
class EagerSingleton:
    # Static instance created eagerly
    __instance = None

    # Private constructor simulation
    def __init__(self):
        if EagerSingleton.__instance is not None:
            raise Exception("This class is a singleton!")
        # Declaring it private prevents creation of its object using the new keyword
        EagerSingleton.__instance = self

    # Method to get the instance of class
    @staticmethod
    def getInstance():
        return EagerSingleton.__instance  # Always returns the same instance

# Eager initialization (happens at load time)
EagerSingleton._EagerSingleton__instance = EagerSingleton()

```

*   Lazy loading (on demand initialisation)

    * the singleton instance is created only when it is needed, the first time getInstance() method is called
    * ex — coffee machine , a coffee machine that only brews coffee when we press the button , it doesn't waste energy or resources until we actually want a cup of coffee.
    * Similarly lazy loading creates the singleton instance only when it is requested


* Understanding
  * The instance starts as null
  * it is only created when getInstances() is first called.
  * Future calls returns the already created instances.
* Pros
  * saves memory if the instance is never used.
  * Object creation is deferred untill required
*   Cons

    * Lazy loading is not thread-safe by default. Thus it requires synchronisation in multi threaded environments



```python
# Class implementing Lazy Loading
class LazySingleton:
    # Object declaration
    __instance = None

    # Private constructor simulation
    def __init__(self):
        if LazySingleton.__instance is not None:
            raise Exception("This class is a singleton!")
        # Declaring it private prevents creation of its object using the new keyword
        LazySingleton.__instance = self

    # Method to get the instance of class
    @staticmethod
    def getInstance():
        # If the object is not created
        if LazySingleton.__instance is None:
            # A new object is created
            LazySingleton()
        
        # Otherwise the already created object is returned
        return LazySingleton.__instance

```

* Thread safety
  * in a single-threaded environment, implementing a singleton is straightforward.&#x20;
  * However things get complicated in multi-threaded applications, which are very common in modern software (especially web servers, mobile apps etc).
  * The problem
    * lets say two threads simultaneously call getInstance() for the first time in a lazy-loaded singleton.
    * If the instance hasn't been created yet, both threads might pass the null check and end up creating two different instances completely breaking the singleton guarantee
    * this kind of bug is&#x20;
      * hard to detect
      * sever
      * costly
* Different ways to achieve thread safety
  * synchronised methods
    * simplest way to ensure thread safety
    * By synchronising thee method that creates the instance, we can prevent multiple threads from creating separate instances at the same time
    * But this approach can lead to performance issues due to overhead of synchronisation

```python
import threading

# Class implementing thread-safe Singleton
class Singleton:
    # Object declaration
    __instance = None

    # Lock for thread safety (similar to Java's 'synchronized')
    __lock = threading.Lock()

    # Private constructor simulation
    def __init__(self):
        if Singleton.__instance is not None:
            raise Exception("This class is a singleton!")
        Singleton.__instance = self

    # Thread-safe method to get the instance of class
    @staticmethod
    def getInstance():
        # Only one thread can enter this block at a time
        # This is equivalent to using 'synchronized' in Java
        with Singleton.__lock:  # Synchronized block
            if Singleton.__instance is None:
                Singleton()
        return Singleton.__instance

```

* What synchronised keyword does?
  * The synchronised keyword ensures that only one thread at a time can execute getInstance() method.
  * This prevents multiple threads from entering the method simultaneously and creating multiple instances
  * Pros
    * simple and easy to implement
    * thread-safe without needing complex logic
  * Cons&#x20;
    * performance overhead: every call to getInstance() is synchronized, even after the instance is created.
    * may slow down the application in high-concurrency scenarios.
* Double-checked locking&#x20;
  * this is more efficient way to achieve thread seafty.
  * Idea is to check if the instance is null before acquiring the lock.
  * If it is then we synchronize the block and check again, this reduces the overhead of synchronisation after the instance has been created
*   **Understanding**

    * The outer `if` check avoids synchronization **once the instance is created**.
    * The inner `if` inside `synchronized` ensures that **only one thread creates the instance**.
    * `volatile` keyword ensures changes made by one thread are visible to others. Without `volatile`, one thread might create the Singleton instance, but **other threads may not see the updated value** due to caching. `volatile` ensures that the instance is always read from the **main memory**, so all threads see the most up-to-date version.

    **Pros**

    * **Efficient:** Synchronization only happens once, when the instance is created.
    * Safe and fast in concurrent environments.

    **Cons**

    * Slightly more complex than the synchronized method.
    * Requires Java 1.5 or above due to reliance on `volatile`.

```python
import threading

# Class implementing double-checked locking Singleton
class Singleton:
    # Object declaration
    __instance = None
    __lock = threading.Lock()

    # Private constructor simulation
    def __init__(self):
        if Singleton.__instance is not None:
            raise Exception("This class is a singleton!")
        Singleton.__instance = self

    # Thread-safe method using double-checked locking
    @staticmethod
    def getInstance():
        if Singleton.__instance is None:  # First check (no lock)
            with Singleton.__lock:
                if Singleton.__instance is None:  # Second check (with lock)
                    Singleton()
        return Singleton.__instance

```

* **Bill Pugh Singleton (Best Practice for Lazy Loading)**
  * highly efficient way to implement the Singleton pattern.
  * uses a static inner helper class to hold the Singleton instance.
  * instance is created only when the inner class is loaded, which happens only when `getInstance()` is called for the first time.<br>
  *   **Explanation:**

      * The Singleton instance is not created until `getInstance()` is called.
      * The **static inner class** `(Holder)` is **not loaded until referenced**, thanks to Java's class loading mechanism.
      * It ensures **thread safety**, **lazy loading**, and **high performance** without synchronization overhead.

      **Pros**

      * Best of both worlds: **Lazy + Thread-safe**.
      * No need for synchronized or volatile.
      * Clean and efficient.

      **Cons**

      It is slightly less intuitive for beginners due to the use of a nested static class.<br>

```python
# Class implementing Singleton using holder-like lazy initialization
class Singleton:
    # Private constructor simulation
    def __init__(self):
        if hasattr(Singleton, "_created"):
            raise Exception("This class is a singleton!")
        Singleton._created = True

# Function acting like a static holder in Java
def getInstance():
    # This behaves like Java's Holder class:
    # - Singleton instance is not created until getInstance() is first called
    # - The attribute is only added once (lazy initialization)
    # - Thread-safety is not guaranteed unless protected externally
    if not hasattr(getInstance, "_instance"):
        getInstance._instance = Singleton()
    return getInstance._instance

```

* Eager loading&#x20;
  * eager loading does not face thread safety issues
  * This approach avoids thread issues altogether by creating the instance upfront — **at the cost of potential memory waste**.&#x20;
  * Thus, it is not a preferred method in most cases but is still a valid option.<br>

#### Pros of Singleton Pattern

* **Cleaner Implementation:** Singleton offers a straightforward and tidy way to manage a single instance of a class, especially when designed with thread safety and simplicity in mind.
* **Guarantees One Instance:** This pattern enforces that only one instance of the class can exist, making it ideal for shared resources.
* **Provides a Way to Maintain a Global Resource:** It allows centralized access to a global resource or service, which can be useful in managing application-wide configurations or state.
* **Supports Lazy Loading:** Many Singleton implementations allow the instance to be created only when it is first accessed, optimizing memory usage and startup performance.

***

#### Cons of Singleton Pattern

* **Used with Parameters and Confused with Factory:** When a Singleton class requires parameters for instantiation, it may blur lines with the Factory pattern, leading to design confusion.
* **Hard to Write Unit Tests:** Since the Singleton holds a global state, it becomes difficult to isolate and mock for unit testing, thus potentially hindering testability.
* **Classes Using It Are Highly Coupled to It:** Components that depend on the Singleton become tightly coupled to its implementation, which reduces flexibility and makes code harder to maintain or refactor.
* **Special Cases to Avoid Race Conditions:** In multi-threaded environments, care must be taken to avoid race conditions during the instance creation phase, complicating implementation.
* **Violates the Single Responsibility Principle (SRP):** A Singleton often handles both instance control and its core functionality, thereby violating the SRP, a key principle of clean software design.

***

**Conclusion**

The Singleton pattern can be a powerful tool when used appropriately, particularly for managing global states and shared resources. However, developers should be mindful of its drawbacks, especially regarding testing and maintainability. Consider alternatives or enhanced implementations (like dependency injection) where appropriate to maintain clean and scalable codebases.

<br>
