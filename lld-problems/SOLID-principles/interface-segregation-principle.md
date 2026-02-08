# Interface Segregation Principle (ISP)

_A client should not be forced to depend on methods it does not use._

**Explanation:**
Instead of having large interfaces, break them into smaller, more specific ones.

## Java Example

### Bad Design (Violating ISP)

```java
interface Worker {
    void work();
    void eat();
}

class Robot implements Worker {
    public void work() {
        System.out.println("Robot working...");
    }

    public void eat() {  // Robots don't eat, but are forced to implement this method.
        throw new UnsupportedOperationException("Robots don't eat");
    }
}
```

### Good Design (Following ISP)

```java
interface Workable {
    void work();
}

interface Eatable {
    void eat();
}

class Human implements Workable, Eatable {
    public void work() {
        System.out.println("Human working...");
    }

    public void eat() {
        System.out.println("Human eating...");
    }
}

class Robot implements Workable {
    public void work() {
        System.out.println("Robot working...");
    }
}
```

Now, `Robot` is not forced to implement `eat()`.

---

## Python Example

### Bad Design (Violating ISP)

```python
class Worker:
    def work(self):
        pass

    def eat(self):
        pass

class Robot(Worker):
    def work(self):
        print("Robot working...")

    def eat(self):  # Robots don't eat, but are forced to implement this method.
        raise NotImplementedError("Robots don't eat")
```

👎 **Problem:**
The `Robot` class is forced to implement `eat()`, even though it doesn’t make sense.

### Good Design (Following ISP)

```python
from abc import ABC, abstractmethod

class Workable(ABC):
    @abstractmethod
    def work(self):
        pass

class Eatable(ABC):
    @abstractmethod
    def eat(self):
        pass

class Human(Workable, Eatable):
    def work(self):
        print("Human working...")

    def eat(self):
        print("Human eating...")

class Robot(Workable):
    def work(self):
        print("Robot working...")
```

👍 **Fix:**
Now, `Robot` implements only what it needs.
