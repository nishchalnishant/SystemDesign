> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The full consistency ladder — from the weakest guarantees all the way up to linearizability — and how to reason about conflict resolution in distributed systems.
>
> **Key topics:**
> - **The consistency ladder:** eventual → monotonic reads → read-your-writes → causal → sequential → linearizable
> - **Linearizability vs Serializability** — the distinction every Staff candidate must know
> - **Causal consistency** — vector clocks, happens-before, practical use in social apps
> - **CRDTs** — conflict-free merge without coordination
> - **Conflict resolution strategies** — LWW, vector clocks, application-level merge
> - **Split-brain and quorum** — how majority rules prevents silent data loss
>
> **Key takeaway:** "Strong consistency" is not a precise term. Interviewers expect you to name the exact model (linearizable, serializable, causal) and justify the trade-off for your specific use case.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations, distributed-systems]
---
# Consistency Models and Conflict Resolution

---

## The Consistency Ladder

Distributed systems offer a spectrum of consistency guarantees. Stronger models are easier to reason about but cost latency and availability. Weaker models are faster but push complexity to the application.

```
Strongest ──────────────────────────────────── Weakest
  Linearizable → Serializable → Causal → Monotonic → Eventual
```

### Eventual Consistency (Weakest)

Replicas that receive no new writes will *eventually* converge to the same value. No guarantee on when, and a read can return stale data indefinitely. Fast and highly available (AP in CAP terms).

**Use for:** DNS propagation, CDN cache invalidation, YouTube view counts, Instagram likes.

### Monotonic Reads

Once you've read a value at version V, you will never see a version older than V in a subsequent read. Prevents a user from seeing a tweet, refreshing, and having it disappear.

**Use for:** Any read-heavy social feed. Achieved by routing a user's reads to the same replica (sticky sessions or consistent hashing on user_id).

### Read-Your-Writes (RYW)

After you write, your own subsequent reads will reflect that write — even before the write has propagated to all replicas. Other users may still see the old value.

**Use for:** Profile updates ("I just changed my bio, why doesn't it show?"). Achieved by reading from primary after a write, or by tagging writes with a token that replicas check before serving reads.

### Causal Consistency

If event A causally precedes event B (A happens-before B), then every process sees A before B. Concurrent events (neither caused the other) may be seen in any order.

**Example:** Alice posts "anyone up for lunch?" → Bob replies "sure!". Under causal consistency, no reader sees Bob's reply without first seeing Alice's question. Under eventual consistency, they might.

**Implementation:** Vector clocks (each node maintains a counter per node; increment on write, take max on read/merge). A message with vector `[A:3, B:1]` causally follows `[A:2, B:1]`.

**Use for:** Collaborative tools, comment threads, shopping carts. DynamoDB's causal consistency option, MongoDB's session-level causal consistency.

### Sequential Consistency

All operations appear to execute in some total order, and each process's operations appear in that order. The total order doesn't have to match real-time wall clock.

**Distinction from linearizable:** Sequential consistency allows a lag between when an operation completes in real time and when it appears in the total order. Linearizable does not.

### Linearizability (Strongest)

Every operation appears to take effect instantaneously at some point between its invocation and its response. All clients see a single, consistent view of the system that respects real-time ordering.

**What this means in practice:** If write W completes before read R starts, R must see W's value. No stale reads, ever.

**Cost:** Requires coordination (consensus rounds) on every write. Latency = at least one round-trip across the quorum. Availability drops during partitions (CP in CAP terms).

**Use for:** Leader election, distributed locks, financial ledger balances, inventory counts for limited items (concert tickets, hotel rooms).

**Implementations:** Raft-based systems (etcd, ZooKeeper), Spanner's TrueTime + 2PC, single-leader databases with synchronous replication.

---

## Linearizability vs. Serializability

This is the most common mix-up at the Staff interview level.

| Property | Linearizability | Serializability |
|---|---|---|
| Scope | Single-object operations | Multi-object transactions |
| Time ordering | Respects real-time order | Only guarantees some serial order exists |
| Concurrency model | Single operations are atomic | Transactions are atomic |
| Where it comes from | Distributed systems theory | Database ACID theory |
| Typical enforcement | Consensus (Raft/Paxos) | 2PL, MVCC, OCC |

**Strict Serializability** = serializable transactions + linearizable real-time ordering. This is what Spanner provides and why it can charge a premium.

**Practical distinction:** A serializable database can let you read a value from "the past" (a snapshot from before your transaction started) as long as the result is equivalent to *some* serial order. A linearizable system cannot — you must see the latest committed value.

---

## Conflict Resolution Strategies

When you use a weak consistency model, concurrent writes to the same key will produce conflicts. How do you resolve them?

### Last Write Wins (LWW)

The write with the highest timestamp wins; the other is discarded.

- **Pro:** Simple to implement, no application logic needed.
- **Con:** Physical clocks on different servers can drift. A write from a clock 5ms fast can silently overwrite a newer write from a slow clock. **Data loss is silent.**
- **When to use:** Low-stakes data where "newest" is unambiguous (e.g., a user's current profile picture URL).
- **Cassandra uses LWW by default.** This surprises engineers who assume it offers stronger guarantees.

### Vector Clocks

Each write carries a version vector `{node_id: counter}`. A write at node A increments `A`'s counter. When two writes are being merged:
- If vector V1 dominates V2 (every counter in V1 ≥ V2), V1 is newer — no conflict.
- If V1 and V2 are concurrent (neither dominates), it's a conflict.

Conflicts are surfaced to the application or user for resolution.

```
Write at A: {A:1, B:0} → user email = "alice@old.com"
Write at B: {A:0, B:1} → user email = "alice@new.com"
Conflict: neither dominates → application must merge
```

**Amazon Dynamo** used this approach for shopping carts: both versions are stored and returned to the client, which merges (union) them.

### Application-Level Merge

For structured data types, write domain-specific merge logic. Google Docs: operational transforms merge concurrent character insertions. Git: 3-way merge on text diffs.

---

## CRDTs (Conflict-Free Replicated Data Types)

A CRDT is a data structure designed so that any two replicas can be merged in any order, any number of times, and always produce the same result — without coordination.

**How:** Operations and states are designed to be commutative, associative, and idempotent. The merge function is a join in a semilattice (always takes the "greater" value under some partial order).

| CRDT type | Example | Merge rule |
|---|---|---|
| G-Counter | View count (grow only) | `max(A[i], B[i])` per node |
| PN-Counter | Likes (grow and shrink) | separate increment + decrement G-counters |
| LWW-Register | Last-write-wins field | merge = take the one with higher timestamp |
| OR-Set | Distributed shopping cart | union of (element, unique-tag) pairs |
| Observed-Remove Set | Set with deletes | add with unique tag; delete tombstones the tag |

**Use for:** Collaborative editors (Figma, Notion), real-time counters, distributed shopping carts, offline-first mobile apps. Redis has native CRDT support for geo-distributed active-active setups (Redis Enterprise).

**Trade-off:** CRDTs are limited to data types where merge semantics are well-defined. Complex business rules ("you can't remove an item if it's already shipped") can't be expressed as a CRDT — use a serializable transaction instead.

---

## The Split-Brain Problem

When a network partition separates a cluster, both sides continue operating independently. Each side believes it is the authoritative leader. When the partition heals, both sides have made writes the other doesn't know about.

**Quorum (majority rules):** Use an odd number of nodes (3, 5, 7). Each write requires acknowledgement from `⌊N/2⌋ + 1` nodes. If a partition splits the cluster, only the majority partition can form a quorum; the minority must refuse writes.

```
Cluster of 5: quorum = 3
Network splits into [A, B, C] and [D, E]
  → [A, B, C] can accept writes (quorum met)
  → [D, E] must reject writes (quorum not met)
```

This prevents split-brain at the cost of availability for the minority partition. It is the core principle behind Raft, ZooKeeper's ZAB protocol, and Paxos.

**Fencing tokens:** Even with quorum, a slow leader might still send writes after losing the election. Clients use a monotonically increasing fencing token (issued by the lock service). Storage systems reject any write with a token older than the current one.

---

## Choosing a Consistency Model

| Use case | Model to reach for | Why |
|---|---|---|
| Bank balance, ticket inventory | Linearizable | Silent data loss is unacceptable |
| User profile update | Read-your-writes | User must see their own change immediately |
| Comment thread ordering | Causal | Replies must appear after parents |
| Shopping cart | CRDT (OR-Set) | Offline merge without coordination |
| Like counts, view counts | Eventual | Accuracy doesn't matter; speed does |
| Leader election, distributed lock | Linearizable | Correctness requires a single agreed value |
| Social media feed | Monotonic reads | Prevent posts from "disappearing" on refresh |

---

## PACELC (Beyond CAP)

CAP is often misapplied because network partitions are rare in practice. The real daily trade-off is **latency vs consistency during normal operation**.

**PACELC:** If there is a **P**artition, choose **A**vailability or **C**onsistency. **E**lse (normal operation), choose **L**atency or **C**onsistency.

| System | Partition behavior | Normal behavior | Classification |
|---|---|---|---|
| DynamoDB (default) | A | L | PA/EL |
| Spanner | C | C | PC/EC |
| Cassandra | A | L | PA/EL |
| HBase | C | C | PC/EC |
| MySQL (sync replica) | C | C | PC/EC |

---

## Interview Questions to Practice

1. **"What is the difference between linearizability and serializability?"**
   *Linearizability = a single operation on a single object appears atomic and respects real-time ordering. Serializability = a transaction across multiple objects appears equivalent to some serial execution, but doesn't have to respect wall-clock time. Strict serializability combines both.*

2. **"A user posts a comment and immediately refreshes — sometimes the comment is missing. What consistency model is violated, and how do you fix it?"**
   *Read-your-writes is violated. Fix: route this user's reads to the primary for a short window after a write (e.g., based on a session cookie), or use a write token that replicas check before serving stale reads.*

3. **"When would you choose CRDTs over a serializable transaction?"**
   *CRDTs when: the merge semantics are well-defined (counters, sets), you need offline-first or geo-distributed updates, and coordination latency is unacceptable. Serializable transactions when: you have multi-key invariants or business rules that can't be captured by a merge function.*

4. **"What is the risk of using Last Write Wins in Cassandra?"**
   *Physical clock skew between nodes. A write from a node with a fast clock can have a higher timestamp than a causally later write from a node with a slow clock, causing the newer write to be silently discarded. For critical data, use conditional writes (`IF` conditions) or model your data to avoid concurrent writes to the same key.*

5. **"Explain PACELC and why it's more useful than CAP for designing real systems."**
   *CAP only considers behavior during a partition (rare). PACELC also captures the latency-vs-consistency trade-off during normal operation (constant). For most systems, the partition scenario is a disaster-recovery concern; the latency/consistency trade-off is a daily architectural decision that directly affects user experience.*

---

## Applied In

This concept is used by **9 problems** in this repo — a representative selection:

**High-Level Design**

- [Design a Distributed Key-Value Store](../../05-hld-problems/01-easy/key-value-store.md)
- [Design WhatsApp (Real-Time Messaging)](../../05-hld-problems/02-medium/whatsapp.md)
- [Design a Chat System (Slack)](../../05-hld-problems/03-hard/chat-system.md)
- [Design a Distributed Cache](../../05-hld-problems/03-hard/distributed-cache.md)
- [Design a Distributed Message Queue (Kafka)](../../05-hld-problems/03-hard/distributed-message-queue.md)
- [Design Dropbox File Sync](../../05-hld-problems/03-hard/dropbox-sync.md)
- [Design GitHub (Code Repository Hosting)](../../05-hld-problems/03-hard/github-code-repo.md)
- [Design Google Drive](../../05-hld-problems/03-hard/google-drive.md)
- …and 1 more

