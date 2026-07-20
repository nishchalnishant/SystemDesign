# Consistent Hashing: Easy Explanation for System Design Interviews

> **Source**: [Consistent Hashing: Easy Explanation for System Design Interviews](https://www.youtube.com/watch?v=1HOVtQ-_fcE&list=PL5q3E8eRUieVFeK1oLahJ8KONkAxDpqk2&index=9)

---

## The Problem with Simple Hashing

### Traditional Hash-Based Distribution
```
server = hash(key) % N    (N = number of servers)
```

When you **add or remove a server**, N changes, and almost **all keys get remapped**:
```
Before (3 servers): hash("user_1") % 3 = 1  → Server 1
After  (4 servers): hash("user_1") % 4 = 2  → Server 2  (MOVED!)
```
- ~(N-1)/N keys need to be moved (e.g., 75% for 4→5 servers)
- Causes massive cache misses and data migration
- Unacceptable at scale

---

## What is Consistent Hashing?

Consistent hashing maps **both servers and keys** onto a **circular hash space** (hash ring). When a server is added or removed, only **K/N keys** need to be remapped (K = total keys, N = total servers).

---

## How It Works

### Step 1: Create the Hash Ring
- Imagine a circular ring with values 0 to 2^32 - 1
- Apply a hash function to each **server** to place it on the ring

```
Hash Ring (0 → 2^32):

        Server A (hash = 100)
       /
  ----●-----------●---------- 
  |              Server B     |
  |             (hash = 300)  |
  |                           |
  ----●-----------●-----------
       \         Server C
        Server D (hash = 700)
       (hash = 900)
```

### Step 2: Map Keys to Servers
- Hash each **key** to a position on the ring
- Walk **clockwise** from the key's position to find the first server
- That server owns the key

```
Key "user_123" → hash = 250 → walks clockwise → Server B (300)
Key "user_456" → hash = 500 → walks clockwise → Server C (700)
Key "user_789" → hash = 800 → walks clockwise → Server D (900)
```

### Step 3: Adding a Server
- New server placed on the ring
- Only keys between the new server and its predecessor are remapped
- Minimal disruption!

```
Add Server E (hash = 600):
- Only keys in range (300, 600] move from Server C to Server E
- All other keys stay on their current server
```

### Step 4: Removing a Server
- Remove server from the ring
- Its keys automatically assigned to the next server clockwise
- Again, minimal disruption

---

## The Problem with Basic Consistent Hashing

### Uneven Distribution
- With few servers, data distribution can be very uneven
- One server might handle 60% of keys, another only 10%

### Solution: Virtual Nodes (Vnodes)

Each physical server gets **multiple positions** on the ring:
```
Physical Server A → Virtual nodes: A1 (hash=100), A2 (hash=400), A3 (hash=800)
Physical Server B → Virtual nodes: B1 (hash=200), B2 (hash=500), B3 (hash=900)
Physical Server C → Virtual nodes: C1 (hash=300), C2 (hash=600), C3 (hash=950)
```

**Benefits of Virtual Nodes**:
- More **even distribution** of keys across servers
- When a server goes down, its load is **spread across many servers** (not just one neighbor)
- Can assign **more vnodes to powerful servers** (weighted distribution)
- Typically use **100-200 vnodes per server**

---

## Visual Summary

```
Without Consistent Hashing:
  Add 1 server → ~75% of keys remapped 😱

With Consistent Hashing:
  Add 1 server → ~K/N keys remapped 🎉
  (If 1000 keys, 4 servers → only ~250 keys move)

With Virtual Nodes:
  Even better distribution + graceful degradation
```

---

## Real-World Usage

| System | How It Uses Consistent Hashing |
|---|---|
| **Amazon DynamoDB** | Partition data across storage nodes |
| **Apache Cassandra** | Distribute data across ring of nodes |
| **Memcached** | Client-side consistent hashing for cache servers |
| **Redis Cluster** | Hash slots (variant of consistent hashing) |
| **Akamai CDN** | Map content to edge servers |
| **Discord** | Route users to specific servers |

---

## Implementation Sketch

```python
import hashlib
from bisect import bisect_right

class ConsistentHash:
    def __init__(self, num_vnodes=150):
        self.num_vnodes = num_vnodes
        self.ring = {}          # hash_value → server_name
        self.sorted_keys = []   # sorted list of hash values on ring
    
    def _hash(self, key):
        return int(hashlib.md5(key.encode()).hexdigest(), 16) % (2**32)
    
    def add_server(self, server):
        for i in range(self.num_vnodes):
            vnode_key = f"{server}:vnode_{i}"
            hash_val = self._hash(vnode_key)
            self.ring[hash_val] = server
            self.sorted_keys.append(hash_val)
        self.sorted_keys.sort()
    
    def remove_server(self, server):
        for i in range(self.num_vnodes):
            vnode_key = f"{server}:vnode_{i}"
            hash_val = self._hash(vnode_key)
            del self.ring[hash_val]
            self.sorted_keys.remove(hash_val)
    
    def get_server(self, key):
        hash_val = self._hash(key)
        # Find the first server clockwise
        idx = bisect_right(self.sorted_keys, hash_val)
        if idx == len(self.sorted_keys):
            idx = 0  # Wrap around
        return self.ring[self.sorted_keys[idx]]
```

---

## Consistent Hashing vs Rendezvous Hashing

| Feature | Consistent Hashing | Rendezvous Hashing |
|---|---|---|
| **Concept** | Hash ring with vnodes | Highest random weight |
| **Key movement** | K/N on change | K/N on change |
| **Memory** | Ring data structure | No ring needed |
| **Implementation** | More complex | Simpler |
| **Lookup** | O(log N) with sorted ring | O(N) — check all nodes |

---

## Interview Tips

1. **Explain the problem first** — why simple `hash % N` fails at scale
2. **Draw the ring** — visual explanation is powerful
3. **Mention virtual nodes** — shows depth of understanding
4. **Connect to real systems** — "DynamoDB and Cassandra use this"
5. **Use when discussing**: sharding, distributed caching, load balancing, CDN routing
6. Key insight: **only K/N keys need to move** when adding/removing a node
