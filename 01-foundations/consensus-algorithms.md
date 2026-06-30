> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Consensus algorithms — how distributed systems get multiple nodes to agree on a single value when nodes can crash and messages can be lost.
>
> **Key topics:**
> - Why consensus is hard: no global clock, FLP Impossibility (can't guarantee consensus with even 1 faulty async node)
> - Raft algorithm: leader election → log replication → safety; node states (Follower, Candidate, Leader)
> - Raft terms: monotonically increasing logical clocks; election with randomized timeouts to avoid split vote
> - Log replication: leader writes to majority before committing; follower catches up via AppendEntries RPC
> - Paxos: the original consensus algorithm — Prepare/Promise/Accept/Commit phases; harder to understand than Raft
> - Zab (ZooKeeper): similar to Paxos; used by ZooKeeper for leader broadcast
> - Where used: etcd (Kubernetes), ZooKeeper (Kafka, HBase), CockroachDB, TiKV (Raft), Consul
>
> **Key takeaway:** Raft is the algorithm to know deeply — it powers etcd (Kubernetes) and is designed to be explainable; master leader election and log replication.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Consensus Algorithms

## Why Consensus Is Hard

In a distributed system, nodes can crash, messages can be delayed or dropped, and there is no global clock. **Consensus** is the problem of getting multiple nodes to agree on a single value — even when some nodes fail.

Real-world analogy: A group of generals must decide whether to attack or retreat. Messengers can be killed in transit. How do you reach agreement when you can't trust the communication channel?

**The FLP Impossibility** (Fischer, Lynch, Paterson 1985): In a fully asynchronous system, it is *impossible* to guarantee consensus in the presence of even one faulty process. The practical escape: systems use timeouts (partial synchrony assumption) to work around this.

---

## Raft

Raft was designed explicitly to be understandable. It decomposes consensus into three mostly-independent sub-problems:

1. **Leader election** — choose one leader at a time
2. **Log replication** — leader accepts entries, replicates to followers
3. **Safety** — only one leader is ever elected per term

### Node States

Every node is always in one of three states:

```
┌─────────────────────────────────────────────────────────┐
│  FOLLOWER  ──── timeout ────►  CANDIDATE                │
│     ▲                              │                    │
│     │                        wins election              │
│  receives                          │                    │
│  heartbeat                         ▼                    │
│     └──────────────────────── LEADER                    │
└─────────────────────────────────────────────────────────┘
```

- **Follower**: passive, accepts log entries and votes
- **Candidate**: runs for election after election timeout fires
- **Leader**: handles all writes, sends heartbeats to prevent elections

### Terms

Raft divides time into **terms** — monotonically increasing integers. Each term begins with an election. If no leader is elected (split vote), a new term starts.

- Terms act as a logical clock
- A node immediately reverts to follower if it sees a message with a higher term

### Leader Election — Step by Step

1. Follower's **election timeout** fires (150–300ms, randomized). It has not heard from a leader.
2. Follower increments its current term, transitions to **Candidate**, votes for itself.
3. Sends `RequestVote RPC` to all other nodes: `(term, candidateId, lastLogIndex, lastLogTerm)`
4. A node grants its vote if:
   - It hasn't already voted in this term
   - The candidate's log is **at least as up-to-date** as its own (term comparison, then length)
5. Candidate wins if it gets votes from **majority (n/2 + 1)** of nodes.
6. Candidate sends `AppendEntries RPC` with empty payload (heartbeat) to assert leadership.

**Split vote scenario**: Two candidates start elections simultaneously, each gets half the votes. Neither reaches majority. Both time out, increment term, and try again. Randomized timeouts make this resolve quickly (probability drops exponentially per round).

### Log Replication

```
Leader:   [1:set x=1] [2:set y=2] [3:set x=5]   ← committed up to index 2
Follower: [1:set x=1] [2:set y=2]                ← needs index 3
Follower: [1:set x=1]                             ← needs indexes 2, 3
```

1. Client sends write to leader.
2. Leader appends entry to its log (not yet committed).
3. Leader sends `AppendEntries RPC` to all followers in parallel.
4. Once **majority** acknowledge the entry, leader marks it **committed**.
5. Leader applies entry to state machine, returns response to client.
6. Next heartbeat informs followers of commit index; they apply the entry.

**AppendEntries consistency check**: Each RPC includes `(prevLogIndex, prevLogTerm)`. Follower rejects if its log doesn't match at that position. Leader retries with an earlier index, walking back until it finds the divergence point, then sends all missing entries.

### Safety Guarantee

Raft ensures: if an entry is committed in term T, no future leader will overwrite it.

**Why**: A leader can only be elected if its log is at least as up-to-date as the majority. The majority that committed an entry overlaps with the majority that elected the new leader. So the new leader must have seen the committed entry.

### Cluster Configuration Changes

Adding/removing nodes without downtime requires **joint consensus**: a two-phase approach where the cluster briefly operates under both the old and new configuration. (Raft paper §6). In practice: etcd, CockroachDB implement single-server changes one node at a time.

---

## Paxos

Paxos is the older algorithm (Lamport, 1989). It is more general but notoriously hard to understand ("Paxos is simple; implementing it is the problem" — Lamport).

### Roles

- **Proposer**: proposes a value to be agreed upon
- **Acceptor**: votes on proposals (usually the same nodes play all roles)
- **Learner**: learns the agreed-upon value

### Single-Decree Paxos (agreeing on one value)

**Phase 1: Prepare / Promise**

1. Proposer chooses a proposal number `n` (globally unique, monotonically increasing).
2. Sends `Prepare(n)` to majority of acceptors.
3. Each acceptor responds with `Promise(n)`:
   - Promises never to accept any proposal numbered < n
   - Returns the highest-numbered proposal it has already accepted, if any

**Phase 2: Accept / Accepted**

1. Proposer picks a value `v`:
   - If any acceptor returned a previously accepted value, use the value from the highest-numbered one
   - Otherwise, proposer is free to choose its own value
2. Sends `Accept(n, v)` to majority of acceptors.
3. Acceptor accepts if it has not promised a higher number; sends `Accepted(n, v)` to learners.
4. Consensus is reached when a majority of acceptors have accepted the same `(n, v)`.

### Why the Value Constraint Matters

```
Proposer 1 sends Prepare(5). Gets back: A1 accepted (3, "foo"), A2 accepted nothing.
Proposer 1 MUST propose "foo" in Phase 2 — it cannot use its own value.
```

This is the key safety mechanism: if a value was already accepted by a majority in a previous round, any new proposer will see it and carry it forward rather than overriding it.

### Multi-Paxos

Single-decree Paxos agrees on one value. For a replicated log (agreeing on a sequence of commands), you need Multi-Paxos:

- Run Phase 1 once to elect a stable leader
- Skip Phase 1 for subsequent entries (leader reuses its proposal number)
- This collapses to something similar to Raft in practice

### Paxos Weaknesses in Practice

1. **Liveness**: Two proposers can indefinitely pre-empt each other. Solution: elect a distinguished leader.
2. **Underspecified**: The original paper doesn't cover log replication, leader election, or reconfiguration — implementors must invent these.
3. **Hard to verify correctness**: Google's Chubby, Apache Zookeeper (ZAB), and etcd (Raft) all chose to reimplement rather than use raw Paxos.

---

## Raft vs Paxos

| Dimension | Raft | Paxos |
|---|---|---|
| **Understandability** | Explicit design goal | Notoriously opaque |
| **Log replication** | Built-in, strong leader | Underspecified, must extend |
| **Leader election** | Explicit, term-based | Distinguished proposer, not formalized |
| **Reconfiguration** | Joint consensus (§6) | Not covered in original paper |
| **Real-world use** | etcd, CockroachDB, TiKV, Consul | Chubby (Google), ZooKeeper (ZAB variant) |
| **Phases to commit** | 1 RTT (after leader elected) | 2 RTTs (Prepare + Accept) |
| **Safety mechanism** | Log up-to-date check at election | Value must be carried forward from highest accepted |

---

## ZAB (ZooKeeper Atomic Broadcast)

ZAB is the protocol ZooKeeper uses — a Paxos variant optimized for primary-backup replication.

Key differences from Raft:
- ZAB uses **epochs** (like Raft terms) but the recovery phase re-proposes all uncommitted entries from the previous epoch before accepting new ones
- ZAB guarantees **causal ordering** of all updates (Raft only guarantees order per leader)
- Kafka historically used ZooKeeper for controller election; KRaft (Kafka 3.x) replaces ZAB with Raft for the metadata quorum

---

## Quorum Mechanics

For a cluster of `n` nodes, **quorum = ⌊n/2⌋ + 1**.

| Cluster Size | Quorum | Fault Tolerance |
|---|---|---|
| 3 | 2 | 1 failure |
| 5 | 3 | 2 failures |
| 7 | 4 | 3 failures |

**Implication**: Always use an **odd number** of nodes. Adding a 4th node to a 3-node cluster does not improve fault tolerance (still 1 failure tolerated) but increases write latency.

**Why quorum works**: Any two majorities overlap in at least one node. That node carries the latest committed value from the previous term into the new one.

---

## Practical Consequences for System Design

### Database Replication

- CockroachDB, TiKV, YugabyteDB: each range/shard is a Raft group (typically 3 replicas)
- A write is ACKed to the client only after the leader commits to a quorum
- **Trade-off**: 3-replica Raft group can tolerate 1 AZ failure with ~2ms write latency increase (one extra RTT)

### etcd / Consul

- etcd is the backing store for Kubernetes control plane
- Raft log replication means etcd write throughput is bounded by leader RTT to followers
- Practical limit: ~10K writes/sec; use caching (informers) for read-heavy workloads

### Distributed Locks (Chubby, ZooKeeper)

- Chubby uses Paxos to maintain a replicated database of locks
- ZooKeeper uses ZAB to maintain a replicated tree of znodes
- Clients rely on **sessions with timeouts**: if a session expires, all ephemeral nodes (locks) are deleted automatically

### Leader Election Without Consensus

Many systems fake leader election using a database (Redis `SET NX`, SQL `SELECT FOR UPDATE`). These are weaker — they rely on lease expiry and have split-brain risk. For financial or coordination workloads, use a proper consensus service (etcd, ZooKeeper).

---

## Interview Deep-Dive Questions

1. **What happens in Raft if two candidates start elections at exactly the same time?**
   Both start with the same term, each votes for itself, split the remaining votes. Neither reaches majority. Both time out (random duration), one fires first, increments to the next term, and wins. The randomized timeout is the liveness mechanism.

2. **Can Raft have two leaders at the same time?**
   Not for the same term. It's possible to have an old leader that doesn't know it has been deposed (network partition) — it will continue to think it's leader. But it won't be able to commit new entries because it can't reach a quorum. The new leader has a higher term; any message from the old leader will be ignored by nodes that have updated.

3. **Why must a Raft leader have the most up-to-date log to be elected?**
   If a node with a stale log became leader, it might overwrite committed entries from the previous term. The up-to-date check (compare last log term, then last log index) ensures the winning candidate has seen everything committed by the previous majority.

4. **In Paxos Phase 2, why must the proposer use the value from the highest-numbered accepted proposal?**
   A previous Paxos round may have reached consensus on that value. If the proposer used a different value, it would violate the safety invariant (two different values could appear to be "decided"). By carrying forward the highest-accepted value, the proposer either continues a previous consensus or starts fresh if nothing was accepted.

---

## See Also

- `01-foundations/fundamentals.md` — CAP theorem, PACELC
- `02-building-blocks/distributed-locks.md` — Fencing tokens, Redlock
- `04-advanced-topics/internals/zookeeper-internals.md` — ZAB vs Raft comparison, KRaft migration
- `02-building-blocks/replication.md` — Leader-follower replication, synchronous vs asynchronous
