---
module: 01-foundations
topic: Consistency Models
status: unread
tags: [01-foundations, system-design, consistency, cap-theorem, distributed-systems]
---

# Consistency Models & CAP Theorem

You're designing a shopping cart. Two browser tabs are open. The user adds an item in tab 1. Tab 2 refreshes. Does it show the new item? Your answer determines your consistency model — and your database choice.

---

## Mindmap

```
[Consistency Models]
├── CAP Theorem
│   ├── Consistency: every read sees the most recent write
│   ├── Availability: every request gets a response (no timeout)
│   ├── Partition Tolerance: system works despite network splits
│   └── You can only guarantee 2 of 3 during a partition — choose C or A
├── Consistency Spectrum (weak → strong)
│   ├── Eventual Consistency → writes propagate eventually; reads may be stale
│   ├── Read-Your-Writes → you always see your own writes; others may not yet
│   ├── Monotonic Read → once you read a value, you never read an older one
│   ├── Causal Consistency → causally related writes seen in order by all
│   └── Strong (Linearizability) → reads always return latest write; single-copy illusion
├── CP Systems (Consistency + Partition Tolerance)
│   ├── HBase, Zookeeper, etcd, CockroachDB
│   └── Sacrifice: may return errors or timeout during partition
├── AP Systems (Availability + Partition Tolerance)
│   ├── Cassandra, DynamoDB, CouchDB, Riak
│   └── Sacrifice: may return stale data during partition
└── Interview Decision Rule
    ├── Financial / inventory / booking → CP (correctness over uptime)
    └── Social feed / profile / shopping cart → AP (stale ok, availability matters)
```

---

## 1. CAP in Plain Terms

Partitions always happen in real distributed systems — network cables get cut, switches fail, datacenters lose connectivity. CAP says: during a partition, pick one.

- **C**: block the request until all nodes agree (risk: timeout or error)
- **A**: respond immediately with whatever data is available (risk: stale read)

"CA systems" only exist on a single node. Any distributed system must tolerate partitions, so the real trade-off is always **CP vs AP**.

---

## 2. Consistency Models

| Model | What it guarantees | Real system | Use when |
|---|---|---|---|
| Strong / Linearizable | Read always returns latest write | etcd, Spanner, HBase | inventory count, seat booking, bank balance |
| Read-Your-Writes | You see your own writes immediately | Dynamo (conditional), Postgres with sticky sessions | user profile, settings |
| Monotonic Read | Reads never go backward in time | Cassandra (with session) | timeline, feed |
| Eventual | Writes propagate "eventually" | DNS, S3, Cassandra default | user bio, likes count, view counter |

---

## 3. What Happens During a Partition

```python
# CP system behavior during partition
def read(key):
    if not can_reach_quorum():
        raise ServiceUnavailableError("partition detected")  # returns error
    return db.get(key)

# AP system behavior during partition
def read(key):
    if not can_reach_quorum():
        return local_replica.get(key)  # returns stale data
    return db.get(key)
```

CP trades availability for correctness. AP trades correctness for availability. Neither is universally better — it depends on what failure mode your business can tolerate.

---

## 4. Quorum Reads and Writes

With N replicas, W write acknowledgments required, R read replicas queried:

```python
# W + R > N guarantees at least one node overlap → strong consistency
N = 3

# Strong consistency: overlap guaranteed
W, R = 2, 2  # W + R = 4 > 3 → at least 1 node has latest write

# Eventual consistency: no overlap guaranteed
W, R = 1, 1  # W + R = 2, not > 3 → may read stale replica

def quorum_read(key, replicas, R):
    responses = [r.get(key) for r in random.sample(replicas, R)]
    return max(responses, key=lambda x: x.timestamp)  # return latest version seen
```

DynamoDB default: W=1, R=1 (eventual). Strongly consistent read: R=quorum (2×read cost).

---

## 5. Amazon Interview Application

Lead with the trade-off, then justify the choice:

- **Shopping cart** — "I'd use DynamoDB (AP). A user tolerates seeing a cart from 100ms ago. Availability matters more than perfect consistency here."
- **Payment service** — "I'd use a CP system. I'd rather return an error than double-charge a customer."
- **Leaderboard** — "Eventual consistency is fine. A score 5 seconds stale is acceptable, and AP gives better write throughput at scale."
- **Inventory / seat booking** — "CP. Two users can't both see 'last seat available' and both confirm. Correctness is non-negotiable."

Pattern: **money, seats, stock → CP. Social, carts, counters → AP.**

---

## 6. Common Follow-ups

**"What is the difference between consistency in CAP and consistency in ACID?"**
CAP consistency = all nodes see the same data at the same time (distributed property). ACID consistency = data satisfies application-defined invariants before and after a transaction (e.g., account balance never goes negative). Completely different concepts that share a word.

**"Can you get both C and A?"**
Only without partitions. In practice, networks partition — cables fail, switches reboot, GC pauses drop heartbeats. You always choose.

**"What does DynamoDB give you by default?"**
AP. Eventually consistent reads by default. Strongly consistent reads are available but cost 2× read capacity units and have higher latency. Use them only where correctness is critical (e.g., reading inventory before decrement).
