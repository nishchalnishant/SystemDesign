# Producer-Consumer Pattern

> **Problem**: Coordinate a producer thread generating data and a consumer thread processing it, ensuring they don't overfill buffer or read from empty buffer.

## The Challenge

1. **Buffer Overflow**: Producer must wait if buffer is full.
2. **Buffer Underflow**: Consumer must wait if buffer is empty.
3. **Thread Safety**: Concurrent access to buffer must be synchronized.

## Implementation

### 1. Using `BlockingQueue` (Simplest & Best in Java)

Java's `java.util.concurrent` package solves this automatically.

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;

public class ProducerConsumerExample {

    public static void main(String[] args) {
        // Shared buffer with capacity 10
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
        
        // Start Producer
        new Thread(new Producer(queue)).start();
        
        // Start Consumer
        new Thread(new Consumer(queue)).start();
    }
}

class Producer implements Runnable {
    private BlockingQueue<Integer> queue;
    
    public Producer(BlockingQueue<Integer> queue) { this.queue = queue; }
    
    @Override
    public void run() {
        try {
            for (int i = 0; i < 20; i++) {
                System.out.println("Produced: " + i);
                queue.put(i); // BLOCKS if full
                Thread.sleep(100);
            }
            queue.put(-1); // Poison pill to stop consumer
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class Consumer implements Runnable {
    private BlockingQueue<Integer> queue;
    
    public Consumer(BlockingQueue<Integer> queue) { this.queue = queue; }
    
    @Override
    public void run() {
        try {
            while (true) {
                Integer value = queue.take(); // BLOCKS if empty
                if (value == -1) break; // Poison pill (stop signal)
                System.out.println("Consumed: " + value);
                Thread.sleep(200); // Simulate slow processing
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

---

### 2. Using `wait()` and `notify()` (Low-Level)

Implementing from scratch to understand under the hood.

```java
import java.util.LinkedList;
import java.util.Queue;

class SharedBuffer {
    private Queue<Integer> queue = new LinkedList<>();
    private int capacity;
    
    public SharedBuffer(int capacity) { this.capacity = capacity; }
    
    public synchronized void produce(int value) throws InterruptedException {
        // while loop crucial (spurious wakeups)
        while (queue.size() == capacity) {
            wait(); // Release lock, wait for space
        }
        
        queue.add(value);
        System.out.println("Produced: " + value);
        
        notifyAll(); // Notify waiting consumers
    }
    
    public synchronized int consume() throws InterruptedException {
        while (queue.isEmpty()) {
            wait(); // Release lock, wait for data
        }
        
        int value = queue.remove();
        System.out.println("Consumed: " + value);
        
        notifyAll(); // Notify waiting producers
        return value;
    }
}
```

**Key Concept**: `wait()` releases the lock. `notifyAll()` wakes up threads but doesn't release lock immediately (synch block must exit).

---

## Real-World Examples

### 1. Web Servant (Request Handling)
- **Producer**: Listener thread accepts HTTP connections -> puts socket into Queue.
- **Consumer**: Worker threads (Thread Pool) take socket -> process request.

### 2. Logger Framework
- **Producer**: Application threads call `log.info()`.
- **Consumer**: Background thread writes log messages to disk (batching).

### 3. Print Spooler
- **Producer**: Applications submit documents.
- **Consumer**: Printer hardware prints one by one.

---

## Interview Questions

**Q: Why use `BlockingQueue` instead of `wait()/notify()`?**
- A: `BlockingQueue` handles synchronization, locking, and condition waiting internally. It's less error-prone and cleaner.

**Q: What is a "Poison Pill"?**
- A: A special object put into the queue to signal consumers to stop (e.g., `-1` or `null`). Used for graceful shutdown.

**Q: What happens if you use `if` instead of `while` in wait() condition?**
- A: **Spurious Wakeups**: A thread might wake up without being notified. `while` ensures condition is re-checked. Also, another thread might have grabbed the lock and consumed the item/space before you got it.
