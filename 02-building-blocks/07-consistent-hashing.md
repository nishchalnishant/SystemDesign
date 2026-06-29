---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks]
---
# Consistent Hashing

> Distributes keys across nodes so that adding or removing a node remaps only K/N keys instead of all keys.

---

## File Mindmap

```
Consistent Hashing
├── Why It Exists
│   ├── Problem → Modulo hashing remaps all keys on node change
│   └── Forces → Cache invalidation storm; rebalancing cost is O(total keys)
├── Core Concepts
│   ├── Hash ring → keyspace [0, 2^32) arranged as a circle
│   ├── Node placement → hash(node_id) maps node to ring position
│   ├── Key lookup → walk clockwise to first node
│   └── Virtual nodes → each physical node has V positions on the ring
├── Strategies / Types
│   ├── Basic ring → simple, uneven distribution → small clusters
│   └── Vnodes → uniform distribution, heterogeneity support → all production systems
├── Trade-offs
│   ├── Pro: O(K/N) keys remapped on topology change
│   └── Con: Hot spots without vnodes; memory for ring metadata
├── Failure Modes
│   └── Node removal → load shifts to successor → monitor successor load
└── Interview Angles
    └── Why vnodes → load balance + heterogeneous node weights
```

---

## 1. Why Consistent Hashing Exists

**Question**: You have a cache cluster of 10 nodes. You use `node = hash(key) % 10`. A node dies. Now `% 10` becomes `% 9`. Every key that maps to a different slot — which is roughly 9/10 of all keys — is a cache miss. You just invalidated 90% of your cache simultaneously, sending a thundering herd to your database.

**Physical constraint**: Hash functions are deterministic but their output distribution depends on the modulus. Changing the modulus changes the mapping for almost all inputs. There is no mathematical shortcut: if N changes to N-1, the only keys unaffected are those where `hash(key) % N == hash(key) % (N-1)`, which is approximately 1/N of all keys.

**Minimal solution**: Sort nodes by some static ID. Assign each a fixed range of the keyspace. Resharding on topology changes is manual and causes downtime.

**Production generalization**: Consistent hashing maps both keys and nodes onto the same circular keyspace. Each key is owned by the first node clockwise from it. When a node is added, it takes over a contiguous slice from its clockwise successor — only K/N keys move. Virtual nodes (vnodes) give each physical node V positions on the ring, turning one large chunk into V small chunks, smoothing out load distribution.

---

## 2. Core Concepts / How It Works

### The Ring

```
                   0
               /       \
    Node C (210°)     Node A (60°)
          |                |
    Node B (150°) ------Node D (300°) [not present, example]

  Key K hashes to 100° → walks clockwise → hits Node A at 60°? No.
  100° is past 60° → continues → hits Node B at 150°. Node B owns K.
```

Ring is the integer space [0, 2^32). Both nodes and keys are hashed into this space.

**Lookup**: `TreeMap<Integer, String>` where key = ring position, value = node ID. `ceilingKey(hash(k))` returns the responsible node. Wraps around to the smallest key if none found.

### Virtual Nodes

Without vnodes, with 3 nodes the ring looks like:
```
Node A owns 33%, Node B owns 40%, Node C owns 27%   ← uneven
```

With V=100 vnodes per node:
```
Each node owns ~100 arcs, each arc ~0.33% of ring    ← smooth
```

When Node D is added, its 100 vnodes each steal a small piece from their clockwise predecessor. Load spreads across all existing nodes proportionally.

| Metric                      | Without vnodes  | With V=150 vnodes     |
|-----------------------------|-----------------|----------------------|
| Std dev of key distribution | ~15-30%         | ~1-3%                |
| Keys moved on +1 node       | ~K/N            | ~K/N (same)          |
| Keys moved on -1 node       | concentrated on 1 successor | spread across N predecessors |
| Memory per node             | O(1)            | O(V × N) on each node|

### Replication with Consistent Hashing

Cassandra and DynamoDB replicate to the next R nodes clockwise from the key's primary. This is called the **preference list**.

```
Key K → primary node P1 → replicate to P2 (next CW) → P3 (next CW)
Replication factor R=3: data lives on P1, P2, P3
```

---

## 3. Real-World Usage

| System        | Usage                                                                      |
|---------------|----------------------------------------------------------------------------|
| DynamoDB      | Consistent hashing maps partition keys to storage nodes; replication factor 3 |
| Cassandra     | Token ring; each node owns a set of tokens (vnodes=256 by default)         |
| Redis Cluster | 16,384 hash slots (fixed); nodes own ranges of slots; no true vnode system |
| Memcached     | Client-side consistent hashing via libketama (V=160 virtual nodes)         |
| Chord (P2P)   | Academic DHT protocol; consistent hashing for peer lookup                  |
| Akamai CDN    | Consistent hashing for mapping URLs to edge cache servers                  |

**Redis Cluster vs Consistent Hashing**: Redis Cluster uses 16,384 fixed slots. This is a pragmatic simplification — 16,384 is small enough that slot-to-node mapping fits in a small bitmap (2 KB) that can be gossiped cheaply. It behaves like consistent hashing with 16,384 virtual nodes but is deterministic and easier to reason about.

**Cassandra vnode design**: With 256 vnodes per node, a 6-node cluster has 1,536 tokens. When a new node joins, it picks 256 random tokens; Cassandra streams the data for those token ranges from existing nodes. Because each existing node loses only ~1/6 of its 256 slices, streaming is parallel and fast.

---

## 4. Trade-offs

| Dimension           | Pro                                                  | Con                                                     |
|---------------------|------------------------------------------------------|---------------------------------------------------------|
| Resharding cost     | O(K/N) keys moved on topology change                 | Still requires data migration — not zero cost           |
| Load balance        | Excellent with vnodes (std dev ~1-3%)                | Poor without vnodes (std dev ~15-30%)                   |
| Hotspots            | Mitigated by vnodes + good hash function             | Skewed key distributions still cause hot nodes          |
| Heterogeneous nodes | Assign more vnodes to bigger nodes (proportional weight) | Requires manual tuning or auto-weight calculation   |
| Memory              | Ring metadata is small: O(V×N) integers              | V=256, N=1000 → 256K integers ≈ 2 MB — negligible      |
| Lookup complexity   | O(log(V×N)) with TreeMap binary search               | Vs O(1) for modulo — but modulo doesn't handle resharding |

**When to use**: Any horizontally scaled distributed storage or cache where nodes are added/removed dynamically.

**When NOT to use**: Small static clusters where you can afford full reshards. Systems where keys must be co-located by prefix (use range partitioning instead).

---

## 5. Failure Scenarios

| Scenario                             | Symptom                                          | Mitigation                                                  |
|--------------------------------------|--------------------------------------------------|-------------------------------------------------------------|
| Node removed → successor overloaded  | Successor inherits all traffic for departed node | Replicate to R successors; route reads to any replica        |
| Hash function collision              | Two nodes at same ring position                  | Append tie-breaker (node ID) to hash input                   |
| Uneven vnode distribution            | One node gets 2× expected load                   | Re-randomize vnode tokens; use deterministic token assignment|
| Hotspot key (celebrity problem)      | Single key overwhelms one node                   | Client-side jitter: `hash(key + random_shard_suffix)`        |
| Split-brain: ring state inconsistent | Nodes disagree on ring membership                | Use consensus (Zookeeper/etcd) for ring metadata             |
| New node joins → rebalancing storm   | All existing nodes stream data simultaneously    | Rate-limit streaming; add nodes one at a time                |

---

## 6. Performance Considerations

**Lookup latency**: `TreeMap.ceilingKey()` is O(log(V×N)). For V=256, N=100 nodes → log(25,600) ≈ 15 comparisons. Sub-microsecond on modern hardware. Fully in-memory.

**Rebalancing throughput**: When adding a node to a 10-node Cassandra cluster with 100 GB/node, each existing node streams ~10 GB (its 1/10 share). With 100 MB/s streaming rate, rebalancing completes in ~100 s. With vnodes, streaming is parallel from multiple sources, reducing wall clock time.

**Hash function choice**: MD5 (used by libketama) gives good uniformity. MurmurHash3 is faster with similar quality. SHA-1 is overkill for distribution purposes. Do NOT use Java's default `hashCode()` — it changes between JVM runs and lacks uniformity guarantees.

---

## 7. Implementation Patterns

### Python Ring Implementation

```python
import hashlib
import bisect

class ConsistentHashRing:
    def __init__(self, virtual_nodes: int = 150):
        self.virtual_nodes = virtual_nodes
        self.ring: dict[int, str] = {}   # hash → node
        self.sorted_keys: list[int] = []

    def _hash(self, key: str) -> int:
        return int(hashlib.md5(key.encode()).hexdigest(), 16)

    def add_node(self, node: str) -> None:
        for i in range(self.virtual_nodes):
            h = self._hash(f"{node}#vnode-{i}")
            self.ring[h] = node
            bisect.insort(self.sorted_keys, h)

    def remove_node(self, node: str) -> None:
        for i in range(self.virtual_nodes):
            h = self._hash(f"{node}#vnode-{i}")
            del self.ring[h]
            self.sorted_keys.remove(h)

    def get_node(self, key: str) -> str:
        h = self._hash(key)
        idx = bisect.bisect_right(self.sorted_keys, h) % len(self.sorted_keys)
        return self.ring[self.sorted_keys[idx]]

# Usage
ring = ConsistentHashRing(virtual_nodes=150)
ring.add_node("cache-1:6379")
ring.add_node("cache-2:6379")
ring.add_node("cache-3:6379")

print(ring.get_node("user:12345"))  # → one of the cache nodes
ring.remove_node("cache-2:6379")    # only ~1/3 of keys remap
```

---

## Quick Revision

- Modulo hashing remaps ~(N-1)/N keys on node change; consistent hashing remaps ~K/N
- Ring is keyspace [0, 2^32); both nodes and keys are hashed onto it
- Key's owner = first node clockwise from key's hash position
- Virtual nodes (vnodes): each physical node gets V ring positions; standard production value is 150-256
- Vnodes fix uneven distribution and allow weighted nodes (give bigger machines more vnodes)
- Replication: replicate to next R distinct physical nodes clockwise (preference list)
- Redis Cluster uses 16,384 fixed hash slots — a pragmatic approximation
- Cassandra default: 256 vnodes per node; Dynamo: configurable, typically 128-256

---

## See Also

- [02-building-blocks/sharding.md](sharding.md) — range vs hash partitioning
- [02-building-blocks/replication.md](replication.md) — preference list and quorum reads
- [04-advanced-topics/internals/cassandra-internals.md](../04-advanced-topics/internals/cassandra-internals.md)
- [04-advanced-topics/internals/dynamodb-internals.md](../04-advanced-topics/internals/dynamodb-internals.md)
- [04-advanced-topics/distributed-concepts.md](../04-advanced-topics/distributed-concepts.md)

---

## Interview Questions Asked

### Conceptual

**Q1: Why does consistent hashing reduce key remapping to K/N instead of all K keys?**

A: Because the ring is circular and each node owns a contiguous arc. When a node is added, it only takes over the arc immediately preceding it (from its counter-clockwise neighbor to itself). All other arcs are untouched. The new node's arc is exactly 1/N of the ring on average, so it claims K/N keys. All other K×(N-1)/N keys stay on their existing nodes.

**Q2: What problem do virtual nodes solve, and what is the trade-off?**

A: Without vnodes, the hash of a node's ID might map it to an oversized or undersized arc by pure chance — load imbalance of 15-30% std dev. Vnodes spread each physical node across V positions on the ring, so its total ownership is the sum of V small arcs. By the law of large numbers, this converges to 1/N of the ring quickly. Trade-off: the ring now has V×N entries, adding memory and lookup time (still O(log(V×N))), and hot-node rebalancing requires tracking V arcs per physical node.

**Q3: How does Cassandra use consistent hashing for replication?**

A: Cassandra builds a token ring using consistent hashing. Each row's partition key is hashed to a token. The row's primary replica is the node that owns that token. Replicas 2..R are the next R-1 distinct physical nodes clockwise. This is the "preference list." With RF=3 and 6 nodes, every row has 3 copies on 3 different nodes. Consistency level QUORUM requires 2 of 3 replicas to respond — it can tolerate 1 node failure with no read/write degradation.

### Comparison / Trade-off

**Q: When would you choose range partitioning over consistent hashing?**

A: Range partitioning is preferable when you need range scans (e.g., "all orders from 2024-01 to 2024-03"). Consistent hashing distributes individual keys but destroys key ordering — adjacent keys in key-space can land on different nodes. For analytics and time-series data where you scan by ranges, range partitioning (as in HBase/BigTable with row key ranges) is better. The downside: range partitions are prone to hot spots if keys are monotonically increasing (all new writes go to the last partition). Solution: add a hash prefix to scatter writes while keeping secondary range index.

### Scenario / Design

**Q: You have a 10-node Memcached cluster behind 100 application servers. Each app server has a client-side consistent hash ring. You add a new Memcached node. Walk through what happens.**

A: The new node is added to each app server's local ring (via config push or service discovery). Each app server independently recalculates: for ~K/10 of keys, the new ring maps them to the new node instead of the old node. On the next cache lookup for those keys, the app server queries the new node — miss — and fetches from the database, populating the new node. The old node still holds the data (it wasn't evicted), but the app server no longer queries it for those keys. Over time the new node warms up. Problems: (1) cache stampede during warmup — mitigate with request coalescing or load shedding. (2) ring state divergence — if 99 of 100 app servers update their ring but 1 does not, that 1 server routes differently, causing cache misses on the mismatched portion. Mitigate: atomic config push with readiness checks.
