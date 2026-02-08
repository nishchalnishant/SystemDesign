# Design LRU Cache

> **Difficulty**: Medium (LLD Classic!)  
> **Data Structures**: HashMap + Doubly Linked List  
> **Time Complexity**: O(1) for get() and put()  
> **Companies**: Google, Amazon, Meta, Microsoft, Apple

## Problem Statement

Implement an LRU (Least Recently Used) cache with:
- `get(key)`: Return value if exists, otherwise -1
- `put(key, value)`: Insert or update. If capacity exceeded, evict LRU item.

Both operations must be **O(1)** time complexity.

**Example:**
```
LRUCache cache = new LRUCache(2);  // capacity = 2

cache.put(1, 1);  // cache: {1=1}
cache.put(2, 2);  // cache: {1=1, 2=2}
cache.get(1);     // returns 1, cache: {2=2, 1=1} (1 is most recent)
cache.put(3, 3);  // evicts 2, cache: {1=1, 3=3}
cache.get(2);     // returns -1 (not found)
```

---

## Approach

### Data Structures

1. **HashMap**: O(1) lookup by key
2. **Doubly Linked List**: O(1) add/remove for LRU tracking

```
HashMap: {key → Node}

Doubly Linked List (MRU → LRU):
head ⇄ [Node3] ⇄ [Node1] ⇄ [Node5] ⇄ tail
       (newest)                    (oldest)
```

**Key Insight:**
- Head = Most Recently Used (MRU)
- Tail = Least Recently Used (LRU)
- On access: Move node to head
- On eviction: Remove tail

---

## Implementation

```java
class LRUCache {
    // Doubly linked list node
    class Node {
        int key;
        int value;
        Node prev;
        Node next;
        
        Node(int key, int value) {
            this.key = key;
            this.value = value;
        }
    }
    
    private HashMap<Integer, Node> map;
    private int capacity;
    private Node head;  // Dummy head (MRU side)
    private Node tail;  // Dummy tail (LRU side)
    
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new HashMap<>();
        
        // Initialize dummy head and tail
        head = new Node(0, 0);
        tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }
    
    public int get(int key) {
        if (!map.containsKey(key)) {
            return -1;
        }
        
        Node node = map.get(key);
        
        // Move to head (most recently used)
        removeNode(node);
        addToHead(node);
        
        return node.value;
    }
    
    public void put(int key, int value) {
        if (map.containsKey(key)) {
            // Update existing key
            Node node = map.get(key);
            node.value = value;
            
            // Move to head
            removeNode(node);
            addToHead(node);
        } else {
            // Insert new key
            Node newNode = new Node(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            
            // Check capacity
            if (map.size() > capacity) {
                // Evict LRU (tail.prev)
                Node lru = tail.prev;
                removeNode(lru);
                map.remove(lru.key);
            }
        }
    }
    
    // Helper: Remove node from linked list
    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }
    
    // Helper: Add node right after head
    private void addToHead(Node node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }
}
```

---

## Time & Space Complexity

| Operation | Time | Space |
|-----------|------|-------|
| `get(key)` | O(1) | - |
| `put(key, value)` | O(1) | - |
| Total Space | - | O(capacity) |

---

## Walkthrough Example

```
LRUCache cache = new LRUCache(2);

1. put(1, 1):
   - Create Node(1, 1)
   - Add to head
   - map: {1 → Node(1,1)}
   - List: head ⇄ 1 ⇄ tail

2. put(2, 2):
   - Create Node(2, 2)
   - Add to head
   - map: {1 → Node(1,1), 2 → Node(2,2)}
   - List: head ⇄ 2 ⇄ 1 ⇄ tail

3. get(1):
   - Find Node(1,1) in map
   - Remove from current position
   - Add to head (MRU)
   - List: head ⇄ 1 ⇄ 2 ⇄ tail
   - Return 1

4. put(3, 3):
   - Create Node(3, 3)
   - Add to head
   - Capacity exceeded (3 > 2)
   - Evict LRU (tail.prev = Node(2,2))
   - Remove 2 from map
   - List: head ⇄ 3 ⇄ 1 ⇄ tail
   - map: {1 → Node(1,1), 3 → Node(3,3)}
```

---

## Testing

```java
@Test
public void testLRUCache() {
    LRUCache cache = new LRUCache(2);
    
    cache.put(1, 1);
    cache.put(2, 2);
    assertEquals(1, cache.get(1));  // returns 1
    
    cache.put(3, 3);  // evicts key 2
    assertEquals(-1, cache.get(2));  // returns -1 (evicted)
    
    cache.put(4, 4);  // evicts key 1
    assertEquals(-1, cache.get(1));  // returns -1 (evicted)
    assertEquals(3, cache.get(3));  // returns 3
    assertEquals(4, cache.get(4));  // returns 4
}
```

---

## Common Mistakes

❌ **Using ArrayList**: O(n) to find and remove  
❌ **Using only HashMap**: No way to track LRU order  
❌ **Forgetting to update on get()**: get() should also move to head!  
❌ **Not using dummy head/tail**: Simplifies edge cases (empty list)

---

## Extensions

**Q: How would you make it thread-safe?**
```java
public class ThreadSafeLRUCache {
    private final LRUCache cache;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    
    public int get(int key) {
        lock.readLock().lock();
        try {
            return cache.get(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(int key, int value) {
        lock.writeLock().lock();
        try {
            cache.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

**Q: How would you add TTL (time-to-live)?**
- Add `expirationTime` field to Node
- On `get()`, check if expired before returning
- Background thread periodically removes expired nodes

---

## Interview Tips

**Q: "Why HashMap + Doubly Linked List?"**
- A: "HashMap for O(1) lookup, Doubly linked list for O(1) add/remove at both ends for LRU tracking"

**Q: "Can you use LinkedHashMap?"**
- A: "Yes, in Java. But in interviews, they usually want you to implement from scratch to show understanding."

**Q: "What if we want LFU (Least Frequently Used) instead?"**
- A: "Need to track access frequency. Use HashMap<Key, Node> + HashMap<Frequency, DoublyLinkedList<Node>>"
