> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Design a distributed key-value store (like Redis, DynamoDB, Cassandra) — covers consistent hashing, replication, storage engines, and CAP theorem trade-offs.
>
> **Key design decisions:**
> - Data partitioning: consistent hashing with virtual nodes; each key maps to a node clockwise on the ring; vnodes ensure even distribution
> - Replication: replicate to N successor nodes (N=3); read from R, write to W; quorum W+R>N; tunable per-operation consistency
> - Storage engine: LSM tree for write-heavy (Cassandra approach) — MemTable → SSTable; Bloom filter to skip disk reads for non-existent keys
> - Write path: Commit log (durability) → MemTable (in-memory) → periodic SSTable flush → compaction; all sequential I/O
> - Failure handling: hinted handoff (queue writes for down nodes); read repair (fix inconsistency on read); Merkle tree anti-entropy
> - CAP choice: AP (Cassandra-style) vs CP (Zookeeper-style); most KV stores default to AP with tunable consistency
> - Operations: get, put, delete (tombstone marker); TTL via background sweeper; range scans only if using range sharding
>
> **Key takeaway:** Consistent hashing + LSM tree is the Cassandra blueprint — understand the write path (commit log → MemTable → SSTable) and quorum reads/writes cold.

---
module: 05-hld-problems
topic: Easy
status: unread
tags: [05-hld-problems, system-design, easy, key-value-store, consistent-hashing, replication]
---
# Design a Distributed Key-Value Store

> **Difficulty**: Easy | **Asked at**: Amazon (DynamoDB), Google (Bigtable), Meta

---

## Problem Statement

Design a distributed key-value store (like Redis, DynamoDB, or Cassandra) that supports `get(key)` and `put(key, value)` operations. The system must be scalable, fault-tolerant, and performant, handling large datasets that don't fit on a single machine.

---

## Functional Requirements

1. **put(key, value)**: Store a key-value pair; overwrite if key exists
2. **get(key)**: Retrieve the value for a key; return null if not found
3. **delete(key)**: Remove a key-value pair
4. **TTL**: Optional expiration per key
5. **Consistency model**: Configurable — strong or eventual consistency per request

---

## Non-Functional Requirements

- **Scale**: Handle 10 TB of data; no single machine can hold all data
- **Throughput**: 100K reads/sec, 10K writes/sec (10:1 read:write ratio)
- **Latency**: P99 get < 5ms, P99 put < 10ms
- **Availability**: 99.99% — survive single-node and multi-node failures
- **Partition tolerance**: Must continue operating during network partitions (favor availability over consistency per CAP)
- **Replication**: Data replicated across 3 nodes for durability

---

## Core Entities

| Entity | Key Fields |
|--------|-----------|
| `KeyValuePair` | key (string), value (bytes up to 1 MB), version (vector clock), ttl |
| `VNode` | virtual node ID, physical node assignment, key range |
| `Node` | node_id, host, port, status (up/down/joining) |

---

## API Design

**Client-facing (SDK)**:
```
get(key: string) → (value: bytes, version: VectorClock)
put(key: string, value: bytes, ttl: int?, consistency: "one" | "quorum" | "all") → VectorClock
delete(key: string) → void
```

**Internal node-to-node (gRPC)**:
```
Replicate(key, value, version, coordinator_id) → ack
AntiEntropy(range) → list[{key, version}]   // for repair
Heartbeat(node_id, timestamp) → status
```

**No REST API**: Key-value stores typically expose a binary protocol (Redis RESP, Memcached ASCII, DynamoDB over HTTPS) optimized for throughput, not a human-readable REST API.

---

## High-Level Design

```
Client SDK
  │  (consistent-hash lookup → target node)
  │
  ├─── Node A (coordinator)
  │      │  replicates to →  Node B, Node C  (W=2 quorum write)
  │      │                   Node B, Node C  (R=2 quorum read)
  │      │
  │    SSTable on disk (sorted, immutable)
  │    Memtable in memory (write buffer)
  │    Bloom filter (fast key existence check)
  │
  └─── Node D, E, F  (other nodes, different key ranges)

Gossip protocol: nodes discover each other's status
Ring: consistent hash ring maps keys → nodes
```

**Consistent hashing**: Hash the key space to a ring [0, 2^128). Each node is placed at multiple positions on the ring (virtual nodes / vnodes). A key maps to the first node at or after its position on the ring (clockwise). Vnodes ensure even distribution even with heterogeneous hardware.

**Replication**: A write to node N is automatically replicated to the next N-1 nodes clockwise on the ring (e.g., N=3). Reads and writes specify a consistency level:
- W=1, R=1: fastest, weak consistency
- W=2, R=2 (quorum, N=3): balanced — survives 1 node failure
- W=3, R=3: strongest, highest latency

**Storage engine**: Each node uses an LSM tree (Log-Structured Merge tree): writes go to an in-memory memtable, flushed to sorted SSTables on disk. Reads check memtable first, then SSTables newest-to-oldest, using bloom filters to skip irrelevant SSTables.

---

## Deep Dive 1: Consistent Hashing and Data Distribution

**Problem**: As nodes join or leave, we need to minimize data movement (rehashing) and maintain even distribution.

**Naive hashing** (`hash(key) % N`): Adding a node N changes to N+1, invalidating ~N/(N+1) of all keys — nearly all keys must be remapped. Catastrophic for operational changes.

**Consistent hashing**: Key space is a ring. Adding a node only remaps keys in the range from the new node to the next node clockwise — approximately 1/N of all keys.

**Virtual nodes (vnodes)**: Each physical node is assigned ~150 random positions on the ring. Benefits:
- Even distribution: with many vnodes per node, probability of hotspots is low
- Proportional assignment: a node with 2× the RAM gets 2× the vnodes → holds 2× the data
- Smooth rebalancing: when a node leaves, its vnodes are distributed across many other nodes rather than all keys going to one neighbor

**Data movement on rebalancing**: When node N joins, it claims vnodes from existing nodes. For each claimed vnode's key range, the previous owner streams that key range to the new node. Uses a background data copy, not a blocking migration.

> 🎯 **Staff signal:** The number to voice is *1/N keys move* on a membership change vs. *nearly all* under `hash(key) % N` — that single property is why consistent hashing exists, and it's what makes elastic scaling operationally survivable. The vnode layer is the second insight: it decouples *load distribution* from *physical node count*, so a bigger node simply owns more vnodes and rebalancing spreads across many donors instead of dumping everything on one neighbor. Naming "minimize remapped keys" as the actual design goal — not "distribute evenly" — is the E5→E6 framing.

---

## Deep Dive 2: Conflict Resolution with Vector Clocks

**Problem**: With eventual consistency, two nodes may accept conflicting writes to the same key. How do you resolve which value is correct?

**Last-write-wins (LWW)**: Use a physical timestamp. The write with the higher timestamp wins. Simple but requires synchronized clocks (NTP drift causes incorrect resolutions). Amazon DynamoDB uses this.

**Vector clocks**: Each value carries a vector clock `{nodeA: v1, nodeB: v2, ...}`. A write from node A increments nodeA's counter. Comparing two vector clocks:
- If all counters in VC1 ≤ VC2: VC2 is a descendant (newer). Use VC2.
- If some counters in VC1 > VC2 and vice versa: concurrent writes → conflict. Cannot auto-resolve.

**Conflict surfacing**: When a conflict is detected on read, return all conflicting versions to the client. Client-side resolution logic (or a merge function) decides the winner. Used by Amazon's Dynamo (original paper). Complex but correct.

**Read-repair**: During a quorum read, if different nodes return different versions, the coordinator sends the winning version back to lagging nodes. Keeps replicas in sync without a dedicated anti-entropy process.

> 🎯 **Staff signal:** The honest answer is that LWW *silently discards a concurrent write* — with NTP clock skew, "last" is a lie, and you lose data you can't even detect losing. Vector clocks trade that silent loss for *surfaced conflict*: they can't magically merge, but they can tell you two writes were genuinely concurrent and hand both to the client. State the tradeoff explicitly — "LWW for a shopping-cart's `last_updated` where loss is tolerable; vector clocks when a lost write is a correctness bug and the client can merge." Choosing consciously, and naming what LWW throws away, is the signal.

---

## Deep Dive 3: Failure Detection and Recovery

**Problem**: How do nodes detect that a peer has failed? How is data recovered when a node comes back?

**Gossip protocol**: Each node periodically (every 1s) sends its view of the cluster state (which nodes it thinks are up/down) to 3 random peers. Failed nodes are detected within ~O(log N) gossip rounds. No central coordinator needed — truly decentralized.

**Phi accrual failure detector**: Instead of a hard timeout, compute a probability `phi` that a node has failed based on inter-arrival times of heartbeats. `phi > 8` → declare failure. Adapts to variable network latency without false positives.

**Hinted handoff**: When node N is down, its coordinator writes the data to a "hint" on another available node. The hint says "this data belongs to N; forward it when N recovers." When N recovers, the hinting node transfers the hint. Provides write availability even when the target node is down.

**Anti-entropy (Merkle trees)**: After recovery, nodes synchronize by comparing Merkle trees of their key ranges. Two nodes hash their data into a tree; mismatches identify exactly which keys need to be synced. Reduces network bandwidth — only divergent data is transferred.

> 🎯 **Staff signal:** Two mechanisms carry the availability story, and the senior move is naming *why each exists separately*. Hinted handoff preserves **write availability** during a failure — the write lands somewhere and gets forwarded on recovery, so a downed node never causes a write to fail. Merkle-tree anti-entropy handles **convergence after** — it makes divergent-data detection O(log n) comparisons instead of shipping the whole key range, so repair cost scales with *how much drifted*, not how much data exists. Distinguishing "stay available during the failure" from "reconcile cheaply after it" is the E5→E6 line.

---

## Interviewer Questions by Level

**Junior**:
- What's the difference between a key-value store and a relational database?
- Why can't you store 10 TB of data on a single machine? What do you do instead?
- What is replication and why do you need it?

**Mid-level**:
- Explain consistent hashing. Why is it better than `hash(key) % N` for distributed systems?
- What is a quorum read/write? What's the trade-off between W=1 and W=all?
- How does an LSM tree differ from a B-tree for write-heavy workloads?

**Senior**:
- How do you detect and resolve conflicting writes in an eventually consistent system?
- Walk me through what happens when a node fails and then recovers — data flow, gossip, hinted handoff, anti-entropy.
- How would you support range queries (get all keys between A and Z) in a hash-based key-value store?
- Compare DynamoDB, Cassandra, and Redis — what workload is each optimized for?

---

## Related

**Concepts used in this design**

- [Consistent Hashing](../../02-building-blocks/03-data-partitioning/03-consistent-hashing.md)
- [Replication](../../02-building-blocks/03-data-partitioning/02-replication.md)
- [Consistency & Conflicts](../../01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md)
- [DynamoDB Internals](../../04-advanced-topics/03-internals/08-dynamodb-internals.md)
- [Cassandra Internals](../../04-advanced-topics/03-internals/05-cassandra-internals.md)

**Practice next**

- [Distributed Cache](../03-hard/distributed-cache.md)
- [URL Shortener](../01-easy/url-shortener.md)

The distributed cache is this design plus eviction and volatility.

**Frameworks**: [HLD Template](../../07-interview-templates/01-frameworks/01-hld-template.md) · [Capacity Estimation](../../07-interview-templates/02-cheat-sheets/02-capacity-estimation.md) · [Trade-offs Cheat Sheet](../../07-interview-templates/02-cheat-sheets/01-trade-offs-cheat-sheet.md)
