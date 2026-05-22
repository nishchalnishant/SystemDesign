# Consistency Models and Conflict Resolution

> **When multiple nodes update shared data concurrently, how do you detect conflicts, order events, and converge to a consistent state?**

---

## File Mindmap

```
Consistency and Conflicts
├── Why This Exists
│   ├── Problem → two nodes update same value; network partition; who wins?
│   └── Forces → CAP theorem trade-off: CP vs AP systems
├── Logical Clocks
│   ├── Lamport Timestamps — happens-before ordering
│   └── Vector Clocks — causality tracking per-node
├── Consistency Levels
│   ├── Strong → linearizability (single copy illusion)
│   ├── Sequential → global order, no real-time guarantee
│   ├── Causal → causally related ops ordered; concurrent unordered
│   └── Eventual → all replicas converge given no new writes
├── Conflict Resolution Strategies
│   ├── LWW (Last Write Wins) — timestamp-based; simple but lossy
│   ├── Multi-Value (siblings) — keep all; client resolves
│   └── CRDTs — conflict-free by mathematical design
├── CRDTs
│   ├── G-Counter (grow-only)
│   ├── PN-Counter (increment + decrement)
│   ├── OR-Set (observed-remove set)
│   └── LWW-Register, MV-Register
└── Interview Angles
    ├── "How does DynamoDB resolve write conflicts?"
    ├── "What's the difference between causal and eventual?"
    └── "Why are CRDTs conflict-free?"
```

---

## Why This Exists

**Question**: Node A and Node B both receive writes to the same user profile at the same time. The network partition heals. Which write is "correct"? How do you even know they conflict?

**Physical constraint**: In a distributed system, there is no global clock. Two events at different nodes cannot be unambiguously ordered without communication. Light-speed latency makes simultaneous observation impossible — two servers 10ms apart cannot agree on "now." Any ordering mechanism must work without shared memory or synchronized clocks.

**Minimal solution**: Last write wins (LWW) — timestamp every write, keep the one with the highest timestamp. Works until: two writes arrive with identical millisecond timestamps (common at high QPS), or a node's clock drifts forward and its "future" writes always win, silently discarding valid data.

**Production generalization**: Choose consistency level based on what concurrent writes *mean* for your domain. For a shopping cart (additive operations), CRDTs converge correctly. For a bank balance (order-dependent), you need causal or strong consistency. The algorithm must match the semantics of the data.

---

## Lamport Timestamps

**Invented by Leslie Lamport, 1978. Problem: globally order events across nodes without synchronized clocks.**

### The happens-before relation (→)

- If event A and B are on the same process and A occurs before B: A → B
- If A is a send and B is the receive of the same message: A → B
- Transitivity: if A → B and B → C, then A → C
- If neither A → B nor B → A, they are **concurrent**: A ∥ B

### Lamport Clock Rules

```
Each node maintains integer counter L.

On local event:    L = L + 1
On message send:   L = L + 1; attach L to message
On message receive: L = max(L_local, L_message) + 1
```

### Example

```
Node A:  L=1 (write)   → send msg (L=2) ─────────────────▶  Node B receives (L=max(1,2)+1=3)
                                                               Node B: L=3 (process)
                                                               Node B: L=4 (write)

Node C:  L=1 (write, concurrent with all above)
```

### Limitation

Lamport timestamps guarantee: **if A → B then L(A) < L(B)**.  
They do NOT guarantee the converse: L(A) < L(B) does not mean A → B.  
Two events with different timestamps might still be concurrent. You cannot detect concurrency with Lamport clocks alone.

---

## Vector Clocks

**Solution to Lamport's limitation: detect causality AND concurrency between any two events.**

### Structure

Each node maintains a vector of counters, one per node in the cluster.

```
Node A: [A:0, B:0, C:0]
Node B: [A:0, B:0, C:0]
Node C: [A:0, B:0, C:0]
```

### Rules

```
On local event at node X:    VC[X] += 1
On send from node X:         VC[X] += 1; attach full VC to message
On receive at node Y from X: VC[Y] = max(VC_local, VC_message) element-wise; VC[Y][Y] += 1
```

### Example

```
Node A writes: VC_A = [1,0,0]  → sends to B
Node B receives: VC_B = max([0,0,0],[1,0,0]) → [1,0,0]; B increments → [1,1,0]
Node B writes: VC_B = [1,2,0]  → sends to C
Node C receives: VC_C = max([0,0,0],[1,2,0]) → [1,2,0]; C increments → [1,2,1]

Meanwhile, Node A writes again concurrently: VC_A = [2,0,0]

Compare [2,0,0] and [1,2,1]:
  A-component: 2 > 1  → A seems ahead
  B-component: 0 < 2  → B seems ahead
  Neither dominates → CONCURRENT (conflict!)
```

### Comparison Rules

For vectors V1 and V2:
- **V1 = V2**: all components equal → same event
- **V1 < V2**: all V1[i] ≤ V2[i], at least one strictly less → V1 happened-before V2
- **V1 > V2**: symmetric
- **V1 ∥ V2**: neither ≤ the other → **concurrent, must resolve conflict**

### Used By

- Amazon DynamoDB (original design, Dynamo paper 2007)
- Riak distributed database
- CRDTs internally track causality with version vectors

### Limitation

Vector size grows with number of nodes. With 1000 nodes, every write carries 1000 integers. Solutions: dotted version vectors, pruning inactive nodes.

---

## Consistency Levels Spectrum

```
STRONG (Linearizability)
  │  Every read sees the most recent committed write.
  │  Operations appear to execute atomically at a single point in time.
  │  Single-copy illusion. Highest latency.
  │  Examples: etcd, ZooKeeper, Google Spanner
  │
  ▼
SEQUENTIAL CONSISTENCY
  │  All operations appear in some total order consistent with program order.
  │  No real-time guarantee — "happened first" in wall clock may not matter.
  │  Cheaper than linearizability (no real-time constraint).
  │
  ▼
CAUSAL CONSISTENCY
  │  Causally related operations are seen in causal order by all nodes.
  │  Concurrent operations may be seen in different orders at different nodes.
  │  Examples: MongoDB causal sessions, COPS system
  │
  ▼
EVENTUAL CONSISTENCY
  │  Given no new writes, all replicas eventually converge.
  │  No ordering guarantee. Lowest latency, highest availability.
  │  Examples: DynamoDB (default), Cassandra (ONE), DNS
  │
  ▼
WEAK (No consistency guarantee)
```

### When to Use Each

| Level | Use When | Example |
|-------|----------|---------|
| Strong | Bank balances, inventory counts, leader election | Spanner, etcd |
| Sequential | Social feed ordering, chat message ordering | Kafka partition ordering |
| Causal | Comments on posts, threaded replies | MongoDB causal sessions |
| Eventual | Shopping cart (additive), user preferences, DNS | DynamoDB, Cassandra |

---

## Conflict Resolution Strategies

### 1. Last Write Wins (LWW)

Every write carries a timestamp. On conflict, higher timestamp wins. Lossy — the losing write is silently discarded.

```
Write A: {user: "alice", email: "a@x.com", ts: 1000}
Write B: {user: "alice", email: "b@y.com", ts: 1001}
Result:  email = "b@y.com"  (A's write is lost)
```

**Problems**:
- Clock skew: if Node A's clock is 1 second ahead, its writes always win regardless of actual order
- Silent data loss: no tombstone, no notification to the losing writer
- Not suitable for additive operations (counter increments)

**Used by**: Cassandra (with `writetime()`), Redis (in cluster replication), many time-series DBs

### 2. Multi-Value / Siblings

Keep all conflicting versions. Return all to the client. Client must merge.

```
Read: [
  {email: "a@x.com", vc: [1,0]},
  {email: "b@y.com", vc: [0,1]}
]
Client sees siblings → prompts user or applies business rule
```

**Used by**: Riak (siblings), DynamoDB (original Dynamo paper)

**Problem**: Conflict resolution logic leaks into application code.

### 3. Operational Transformation (OT)

Transform concurrent operations so they can be applied in any order and converge. Used in collaborative editing.

```
Base: "hello"
Op1: insert "!" at position 5 → "hello!"
Op2: delete "o" at position 4 → "hell"

If Op2 applied first: "hell"
Then Op1 (transformed): insert "!" at position 4 → "hell!"

Both orderings converge to "hell!"
```

**Used by**: Google Docs (original), collaborative text editors

---

## CRDTs (Conflict-Free Replicated Data Types)

**Core insight**: Design data structures where all concurrent operations commute. If A⊕B = B⊕A for all operations, there is no conflict — any merge order produces the same result.

Two families:
- **CvRDT (state-based)**: Merge entire state. `merge(s1, s2)` must be commutative, associative, idempotent.
- **CmRDT (operation-based)**: Broadcast operations. Operations must commute.

### G-Counter (Grow-Only Counter)

```
Structure: Map<NodeId, Integer>  (one slot per node)

Increment at Node A: counter[A] += 1   (only modify your own slot)
Merge: for each node i: result[i] = max(local[i], remote[i])
Value: sum(counter.values())

Example (3 nodes):
  Node A: [3, 0, 1]  → value = 4
  Node B: [2, 5, 0]  → value = 7
  Merge:  [3, 5, 1]  → value = 9
```

**Conflict-free because**: max is commutative and idempotent. No ordering needed.

**Used for**: page view counters, like counts, download counts

### PN-Counter (Positive-Negative Counter)

Supports increment and decrement by combining two G-Counters.

```
Structure: {P: G-Counter, N: G-Counter}

Increment: P[myNode] += 1
Decrement: N[myNode] += 1
Value: sum(P.values()) - sum(N.values())
Merge: merge(P_local, P_remote), merge(N_local, N_remote)

Example:
  Node A: P=[3,0], N=[1,0] → value = 3-1 = 2
  Node B: P=[2,2], N=[0,1] → value = 4-1 = 3
  Merge:  P=[3,2], N=[1,1] → value = 5-2 = 3
```

**Used for**: shopping cart item quantities, inventory with removals, upvote/downvote counts

### OR-Set (Observed-Remove Set)

Supports add and remove with correct semantics: "add wins" over concurrent remove.

**Problem with naive approach**: If A removes element E while B concurrently adds E, what's the result? With timestamps, whichever was "later" wins — but they're concurrent.

**OR-Set solution**: Tag each add with a unique token. Remove only removes specific tokens you have observed. A concurrent add creates a new token — it survives.

```
Add("apple") → {("apple", uid_1)}
Remove("apple") → removes all tokens of "apple" you know about: uid_1
Concurrent Add("apple") → {("apple", uid_2)}

After merge: uid_2 survives, so "apple" is in the set.
Add wins over concurrent remove.
```

**Used by**: Collaborative shopping lists, distributed membership sets

### LWW-Register

Single value with a timestamp. On merge, higher timestamp wins. The simplest CRDT — same as LWW conflict resolution. Subject to clock skew.

### MV-Register (Multi-Value Register)

Keeps all concurrent values (like siblings above) but uses vector clocks to detect concurrency precisely.

```java
// Conceptual MV-Register
public class MVRegister<T> {
    private Map<VectorClock, T> values = new HashMap<>();
    
    public void write(T value, VectorClock vc) {
        // Remove any values dominated by new vc
        values.entrySet().removeIf(e -> e.getKey().dominatedBy(vc));
        values.put(vc, value);
    }
    
    public Set<T> read() {
        return new HashSet<>(values.values()); // may have multiple on conflict
    }
    
    public MVRegister<T> merge(MVRegister<T> other) {
        MVRegister<T> result = new MVRegister<>();
        // Keep values not dominated by any value in the other register
        for (Map.Entry<VectorClock, T> e : this.values.entrySet()) {
            if (other.values.keySet().stream().noneMatch(vc -> e.getKey().dominatedBy(vc))) {
                result.values.put(e.getKey(), e.getValue());
            }
        }
        // same from other side
        for (Map.Entry<VectorClock, T> e : other.values.entrySet()) {
            if (this.values.keySet().stream().noneMatch(vc -> e.getKey().dominatedBy(vc))) {
                result.values.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
```

---

## CRDT Comparison

| CRDT | Operations | Conflict Behavior | Use Case |
|------|-----------|-------------------|----------|
| G-Counter | Increment only | No conflict possible | View counts, likes |
| PN-Counter | Increment + Decrement | No conflict possible | Cart quantities |
| OR-Set | Add + Remove | Add wins over concurrent remove | Collaborative sets |
| LWW-Register | Write | Last timestamp wins (lossy) | User preferences |
| MV-Register | Write | Keep all concurrent values | Profile fields |

---

## Real-World System Choices

| System | Model | Conflict Strategy |
|--------|-------|------------------|
| DynamoDB (default) | Eventual | LWW (last write wins) |
| DynamoDB (transactions) | Serializable | 2PC with pessimistic locks |
| Cassandra (ONE) | Eventual | LWW via write timestamp |
| Cassandra (QUORUM) | Causal | Read-repair + LWW |
| Riak | Eventual | Siblings + vector clocks |
| Redis (cluster) | Eventual | LWW |
| Spanner | Strong (external consistency) | No conflicts — serializable |
| CRDTs (Riak, collaborative apps) | Eventual | Conflict-free by design |

---

## Interview Q&A

**Q: DynamoDB says "eventual consistency." What happens if two clients write the same item concurrently?**

Last write wins based on internal timestamp. The earlier write is silently discarded. If this is a counter (user clicked "like" on two devices), you lose an increment. Solutions: (1) use DynamoDB conditional writes with version check, (2) model as a G-Counter CRDT, (3) use DynamoDB transactions (serializable but slower).

**Q: What's the difference between causal and eventual consistency?**

Eventual: all replicas converge eventually; no ordering guarantee. Causal: if you write then read, you see your write (read-your-writes); if your write was causally preceded by another write, you see that first. Example: you post a comment replying to Alice's comment — causal consistency ensures readers always see Alice's comment before yours. Eventual consistency would allow some readers to see your reply before Alice's original.

**Q: Why can't you use CRDTs for everything?**

CRDTs work for commutative operations. "Set counter to 5" is not commutative — two concurrent "set to 5" and "set to 3" have no correct resolution. Bank transfer between accounts requires reading A and writing B atomically — no CRDT captures this cross-object invariant. CRDTs excel at additive, independent per-object operations.

**Q: Vector clocks grow unboundedly. How do production systems handle this?**

Dotted version vectors (Riak's solution): instead of per-node counters, track (node, counter, dot) tuples. Prune entries for nodes that have been removed. Alternatively, use a fixed-size ring of node slots with eviction. DynamoDB moved away from vector clocks in 2012 (the Vogels post) toward LWW + application-level versioning because vector clock management at scale was operationally complex.

---

## See Also

- **Consensus** (leader decides order, eliminates conflicts): [01-foundations/consensus-algorithms.md](consensus-algorithms.md)
- **PACELC trade-offs**: [01-foundations/fundamentals.md](fundamentals.md)
- **CDC for replication**: [01-foundations/change-data-capture.md](change-data-capture.md)
