> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Advanced distributed systems theory for SDE-3 — consistency models, consensus, distributed transactions, time and ordering, conflict resolution, and coordination.
>
> **Key topics:**
> - Consistency models spectrum: linearizability → sequential → causal → eventual — with real-world database mappings
> - Consensus protocols: Raft (leader election + log replication), Paxos (Prepare/Promise/Accept/Commit) — when each is used
> - Distributed transactions: 2PC (two-phase commit), 3PC, Saga pattern — trade-offs in failure atomicity
> - Time and ordering: Lamport timestamps (partial order), Vector clocks (causal order), TrueTime (Google Spanner, bounded uncertainty)
> - Conflict resolution: Last-Write-Wins, multi-value/siblings, CRDTs for automatic merge
> - Distributed coordination: ZooKeeper (configuration, leader election, distributed locks), etcd (Kubernetes config store)
> - Failure models: fail-stop, fail-slow, Byzantine — and which to design for in practice
>
> **Key takeaway:** Linearizability is expensive — know when eventual consistency + CRDTs is sufficient, and when you actually need strong consistency (financial transactions, inventory reservation).

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, advanced-topics]
---
# Distributed Systems - Advanced Topics

> **For SDE-3 Interview Preparation**  
> Deep dive into distributed systems concepts crucial for senior-level system design interviews

## Table of Contents

1. [Introduction to Distributed Systems](#introduction-to-distributed-systems)
2. [Consistency Models](#consistency-models)
3. [Consensus Protocols](#consensus-protocols)
4. [Distributed Transactions](#distributed-transactions)
5. [Time and Ordering](#time-and-ordering)
6. [Conflict Resolution](#conflict-resolution)
7. [Distributed Coordination](#distributed-coordination)

---

## File Mindmap

```
Distributed Systems - Advanced Topics
├── Why It Exists
│   ├── Problem → single server, traffic doubles every 6 months → 160,000 req/sec in 2 years
│   └── Physical limit → $500k machine handles ~500,000 req/sec, SPOF; distribution is the only exit
├── Consistency Models (weakest → strongest)
│   ├── Eventual → replicas converge "eventually"; Cassandra / DynamoDB default
│   ├── Read-your-writes → client sees its own writes; session consistency
│   ├── Causal → preserve happens-before ordering; vector clocks enforce this
│   ├── Sequential → all nodes see same order of operations
│   └── Strong (Linearizable) → every read sees most recent write; Spanner / etcd
├── Consensus Protocols
│   ├── Raft (understandable Paxos)
│   │   ├── Leader election → candidate wins if quorum grants vote for highest term
│   │   ├── Log replication → leader appends, sends AppendEntries, commits when quorum acks
│   │   └── Java state machine: FOLLOWER → CANDIDATE → LEADER
│   ├── Paxos
│   │   ├── Phase 1 (Prepare) → proposer gets promise from quorum
│   │   ├── Phase 2 (Accept) → proposer sends value, acceptors accept
│   │   └── Multi-Paxos → leader persists across rounds for efficiency
│   └── ZAB (ZooKeeper Atomic Broadcast) → discovery + broadcast; ZXID epoch:counter
├── Distributed Transactions
│   ├── 2PC → coordinator sends Prepare → all vote yes → commit; blocking on coordinator crash
│   ├── Saga (Choreography) → each service publishes event, next service reacts; decentralized
│   ├── Saga (Orchestration) → central orchestrator sends commands; easier to reason about
│   └── Outbox Pattern → write event to same DB table, CDC relay publishes to Kafka atomically
├── Time and Ordering
│   ├── Lamport Clocks → logical counter; send: increment; receive: max(local,received)+1
│   ├── Vector Clocks → per-node counter array; detects concurrent writes (Java HashMap impl)
│   └── TrueTime (Google Spanner) → GPS+atomic clocks; commit wait until uncertainty interval passes; enables external consistency
├── Conflict Resolution
│   ├── LWW (Last-Write-Wins) → highest timestamp wins; risk of clock skew data loss
│   ├── Multi-value (Dynamo) → return all conflicts, let client merge
│   └── CRDTs → mathematically merge-safe; G-Counter: sum all node counts; PN-Counter; OR-Set
├── Distributed Coordination (ZooKeeper)
│   ├── Ephemeral sequential znodes → leader election (smallest znode = leader)
│   ├── Watches → one-time triggers on znode change → re-register after fire
│   └── Ensemble quorum → odd N; (N/2)+1 required for writes
├── Trade-offs
│   ├── CAP → Partition-tolerant systems choose CP (Zookeeper) or AP (Cassandra)
│   ├── PACELC → even without partition: latency vs consistency trade-off
│   └── Strong consistency → higher latency (quorum round-trips add 1-5ms per hop)
└── Interview Angles
    ├── "What's the difference between Raft and Paxos?" → Raft designed for understandability; same safety guarantees
    ├── "How do you handle distributed transactions?" → Saga + compensating transactions, not 2PC
    └── Follow-up: how does Saga handle partial failure → dead letter queue + compensating event
```

## Introduction to Distributed Systems

**Question**: You have one server handling 10,000 req/sec. Traffic doubles every 6 months. In 2 years that's 160,000 req/sec. The fastest single server costs $500k, handles ~500,000 req/sec, and if it dies your entire product is down for hours. What is the only exit?

**Physical constraint**: Light travels 200,000 km/sec. New York to London = ~5,500 km = minimum 27ms one-way. You cannot make one machine serve users in both cities with <10ms latency. Geography alone forces distribution.

**Minimal solution**: Put a second identical server behind a load balancer. This works until: the two servers disagree on data (consistency problem), the load balancer itself dies (SPOF), or both servers need to write to the same shared row (distributed transaction problem).

**Production generalization**: Every hard problem in distributed systems — consensus, consistency models, distributed transactions, time and ordering — flows from this one decision to run on multiple machines. A **distributed system** is a collection of independent computers that appears to its users as a single coherent system. All the complexity is the price of that appearance.

**Reasons to distribute:**
- **Scalability**: Handle more load by adding machines
- **Availability**: Continue operating despite failures
- **Geographic Distribution**: Serve users from nearby locations
- **Fault Tolerance**: No single point of failure

**Challenges:**
- **Network Failures**: Partial failures, network partitions
- **Concurrency**: Coordination across nodes
- **Consistency**: Keeping data synchronized
- **Complexity**: Harder to reason about and debug

---

## Consistency Models

**Question**: You have two database replicas. A user updates their email on replica A. Half a second later, they hit the "save successful" page — which is served by replica B. Their old email is displayed. The user is angry. How do you prevent this without routing every read through the primary?

**Physical constraint**: Replication between nodes in the same DC takes ~1ms (network RTT). Cross-region replication takes 50–150ms. Any replica that is not the primary is, by definition, some number of milliseconds behind. You cannot have zero replication lag and also have replicas — those two goals are physically incompatible.

**Minimal solution**: Always read from the primary. Problem: primary becomes the bottleneck — you've added replicas but all reads still hit one node. You've paid the replication cost without getting the read-scale benefit.

**Production generalization**: The consistency spectrum exists to let you trade staleness tolerance for read throughput. You pick the weakest consistency your application can tolerate, not the strongest. Each model below is a different answer to that trade-off.

### Strong Consistency (Linearizability)

> **Analogy**: Everyone in a company editing the same Google Doc. The moment Alice saves a change, Bob sees it immediately — no one ever reads an old version. Every operation appears to take effect instantaneously and atomically, as if there's only one copy of the data.

**Definition**: All operations appear to execute atomically and in order, as if on a single machine.

```
Client A: WRITE(x, 1) at t1
Client B: READ(x) at t2 (where t2 > t1)
Result: B always reads 1 (never stale value)
```

**Characteristics:**
- **Total Order**: All operations have a global order
- **Real-time Guarantee**: If operation A completes before B starts, A < B in order
- **Atomic Visibility**: Operations take effect instantaneously

**Examples:**
- **Google Spanner**: Uses TrueTime (atomic clocks) for external consistency
- **Etcd**: Uses Raft consensus for strong consistency
- **ZooKeeper**: Linearizable writes, potentially stale reads

**Trade-offs:**
- ✅ Easy to reason about (behaves like single-node database)
- ✅ No surprises for application developers
- ❌ Higher latency (coordination overhead)
- ❌ Lower availability during partitions (CP in CAP)

**When to Use:**
- Financial transactions (bank account balance)
- Inventory management (prevent overselling)
- Leader election
- Distributed locking

**Interview talking point**: "Strong consistency requires coordination on every write, which means more latency and sacrificing availability during partitions. I'd use it for financial systems where correctness outweighs speed."

---

### Sequential Consistency

> **Analogy**: A soccer stadium scoreboard. Everyone in the stadium sees the same sequence of goals in the same order, but there's a 3-second broadcast delay vs. real life. The order is consistent for all observers — nobody sees Goal 2 before Goal 1 — but it doesn't reflect exact real-world timing.

**Definition**: All operations appear to execute in some sequential order, but not necessarily real-time order.

```
Client A: WRITE(x, 1)
Client B: WRITE(x, 2)
Client C might observe: x=1, then x=2
Client D might observe: x=2, then x=1
Both are valid if they see consistent order
```

**Weaker than** linearizability (no real-time guarantee).  
**Stronger than** eventual consistency (all clients see same order).

---

### Causal Consistency

> **Analogy**: A Twitter thread. If you reply to a tweet, your reply always appears after the original tweet — even if the original was just posted milliseconds ago. Causally related events are ordered correctly. If you see the reply, you must have seen the original first. Unrelated tweets (from different threads) can appear in any order.

**Definition**: Operations that are causally related are seen in the same order by all processes.

```
Client A: WRITE(x, 1)
Client A: WRITE(y, 2)  // Causally depends on WRITE(x, 1)
Client B: Will see y=2 only after seeing x=1
```

**Techniques:**
- **Vector Clocks**: Track causality across nodes
- **Version Vectors**: Similar but optimized for dynamo-style systems

**Examples:**
- **DynamoDB**: Optional causal consistency
- **Cosmos DB**: Session consistency (reads reflect writes in same session)

**Use Cases:**
- Social media feeds (see post before comments)
- Collaborative editing
- Chat applications

**Interview talking point**: "Causal consistency is the sweet spot for social features: you preserve the cause-before-effect ordering users expect (reply after post) without paying the latency cost of full linearizability."

---

### Eventual Consistency

> **Analogy**: A WhatsApp group message. You send a message and some members see it immediately, others see it 2 seconds later due to connectivity differences. Eventually, everyone sees the same message. No one is guaranteed to see it at the exact same time, but convergence is guaranteed once updates stop.

**Definition**: If no new updates are made, eventually all replicas will converge to the same value.

```
t0: WRITE(x, 1) to Node A
t1: READ(x) from Node B → May return old value
t2: (after replication) READ(x) from Node B → Returns 1
```

**Characteristics:**
- **No Ordering Guarantees**: Reads may return stale data
- **Convergence**: Eventually consistent (given time)
- **High Availability**: AP in CAP theorem

**Conflict Resolution Strategies:**
1. **Last-Write-Wins (LWW)**: Use timestamp, discard older writes
2. **Version Vectors**: Track causality, detect conflicts
3. **Application-Level**: Let application resolve (e.g., shopping cart merge)

**Examples:**
- **Cassandra**: Tunable consistency (can be eventual)
- **DynamoDB**: Default eventual consistency
- **DNS**: DNS propagation is eventually consistent

**Trade-offs:**
- ✅ High availability
- ✅ Low latency (no coordination)
- ✅ Partition tolerant
- ❌ Application complexity (handle stale reads)
- ❌ Conflict resolution needed

**When to Use:**
- Social media feeds
- Product catalogs (staleness acceptable)
- Analytics dashboards
- Caching layers

---

### Read-Your-Writes Consistency

**Definition**: After a client writes a value, their subsequent reads will always see that value or a newer one.

```
Client A: WRITE(profile, "updated")
Client A: READ(profile) → Always sees "updated" (not old value)
Client B: READ(profile) → May see old value
```

**Implementation:**
- Route user's reads to same replica
- Use session tokens with version numbers
- Read from leader for user's own data

**Examples:**
- User profile updates
- Comment systems (see your own comments)

---

## Consensus Protocols

**Question**: You have three database nodes. A network glitch causes Node A to stop hearing from the current leader. Node A declares itself a new leader and starts accepting writes. Meanwhile the old leader (still alive, just partitioned from A) also accepts writes. When the partition heals, you have two diverged logs. How do you prevent this without a human in the loop?

**Physical constraint**: You cannot distinguish a dead node from a slow network. A node that stops responding may be crashed, or the packet may still be in transit. Any timeout you pick is a guess. If you make it too short, live nodes get incorrectly evicted. Too long, and failover takes forever. This ambiguity is why you need a protocol, not just a timeout.

**Minimal solution**: "First one to time out becomes leader." This creates split-brain: two nodes both believe they are leader, both accept writes, data diverges. No recovery path without manual intervention.

**Production generalization**: Consensus protocols solve split-brain by requiring a majority (quorum) to agree before any decision takes effect. With N nodes, a quorum is N/2 + 1. Two separate partitions cannot both have a majority simultaneously — so only one partition can make progress. The minority partition stalls rather than diverging.

### Raft Consensus

> **Analogy**: A team of 5 colleagues deciding on a lunch restaurant. They elect a leader (whoever raises their hand first and gets 3+ votes). The leader proposes options, and a choice only sticks if 3 or more people (majority) agree and write it down. If the leader goes quiet, the team re-elects. No decision is final until the majority signs off — even if the leader disappears mid-decision.

**Problem Solved**: Leader election and log replication in distributed systems

**Key Concepts:**

**1. Leader Election**
```
State: Follower → Candidate → Leader

Election Process:
1. Node times out (no heartbeat from leader)
2. Increments term, becomes Candidate
3. Votes for itself, requests votes from others
4. If receives majority votes → becomes Leader
5. Leader sends heartbeats to prevent new elections
```

**2. Log Replication**
```
Client → Leader: Command
Leader → Followers: Append entry to log
Followers → Leader: Acknowledge
Leader: Commits entry (majority acknowledged)
Leader → Followers: Notify commit
Followers: Apply command to state machine
```

**Properties:**
- **Safety**: Never return incorrect results
- **Availability**: Available as long as majority operational
- **No clock dependency**: Doesn't depend on timing

**Examples:**
- **Etcd**: Kubernetes uses Etcd for cluster coordination
- **Consul**: Service discovery and configuration
- **CockroachDB**: Distributed SQL database

**When to Use:**
- Distributed configuration storage
- Leader election
- Service discovery
- Distributed locking

**Interview talking point**: "Raft is the go-to consensus algorithm today because it's designed for understandability. The key insight is that a committed log entry has been persisted on a majority of nodes, so it survives any single-node failure."

---

### Paxos

**Problem**: Achieve consensus in a distributed environment with unreliable network

**Phases:**
1. **Prepare**: Proposer sends proposal number, asks for promises
2. **Promise**: Acceptors promise not to accept lower-numbered proposals
3. **Accept**: Proposer sends value with proposal number
4. **Accepted**: Acceptors accept if they haven't promised to higher number

**Variants:**
- **Multi-Paxos**: Optimize by having stable leader (skip prepare phase)
- **Fast Paxos**: Reduce latency by allowing clients to propose directly

**Examples:**
- **Google Chubby**: Distributed lock service
- **Apache ZooKeeper**: Inspired by Paxos (uses Zab protocol)

**Raft vs Paxos:**
- **Raft**: Easier to understand, popular in industry
- **Paxos**: More complex, theoretically elegant, less common

---

## Distributed Transactions

**Question**: An e-commerce checkout must (1) deduct inventory in the Inventory DB, (2) charge the card via the Payment DB, and (3) create the order record in the Order DB — three separate databases, owned by separate services. If step 2 succeeds but step 3 crashes, the card is charged but no order exists. How do you make all three happen atomically across services you don't control?

**Physical constraint**: A local database transaction is atomic because one process controls both the lock and the commit. Across two machines, the moment you commit on machine A, machine B might crash before it commits. There is no way to make two independent commit operations happen at the exact same instant — the speed-of-light latency between them means there is always a window where they are in different states.

**Minimal solution**: Just do the operations sequentially and hope nothing crashes. Breaks immediately: real systems crash mid-sequence constantly (deploys, GC pauses, network blips). You need a protocol that either completes all steps or rolls all of them back.

**Production generalization**: Two approaches exist. 2PC gives you atomic commit at the cost of blocking during coordinator failure. The Saga pattern gives up atomicity in exchange for availability — each step is a local transaction, and if something fails you undo already-completed steps with compensating transactions. For most microservice systems, Saga is the right answer because services should be independently deployable and therefore should not share lock state.

### Two-Phase Commit (2PC)

> **Analogy**: Organizing a group dinner. The coordinator (you) texts everyone: "Can you make it Saturday?" (Phase 1: Prepare). Only after every single person replies "Yes" do you send: "Great, we're going!" (Phase 2: Commit). If anyone says no, everyone is told it's cancelled. The fatal problem: what if your phone dies after all said yes but before you sent the commit message? Everyone is stuck waiting — they've reserved their evening but don't know if the plan is on.

**Goal**: Ensure all-or-nothing execution across multiple databases

**Protocol:**

**Phase 1: Prepare**
```
Coordinator → Participants: Prepare to commit
Participants: Lock resources, write to WAL
Participants → Coordinator: Vote (Yes/No)
```

**Phase 2: Commit**
```
If all votes Yes:
  Coordinator → Participants: Commit
  Participants: Apply changes, release locks
Else:
  Coordinator → Participants: Abort
  Participants: Rollback, release locks
```

**Problems:**
- **Blocking**: If coordinator crashes, participants wait indefinitely
- **Single Point of Failure**: Coordinator failure blocks transaction
- **Not Partition Tolerant**: Network partition can cause inconsistency

**Example:**
```
E-commerce Checkout:
DB1: Deduct inventory
DB2: Charge credit card
DB3: Create order

2PC ensures ALL succeed or ALL fail
```

**Use Cases:**
- Traditional RDBMS distributed transactions
- Microservices with strong consistency needs
- Financial systems (rare, prefer compensation)

**Interview talking point**: "2PC is blocking — if the coordinator crashes between prepare and commit, all participants hold locks indefinitely. In practice I'd use the Saga pattern for cross-service transactions and reserve 2PC for cases within a single database cluster."

---

### Three-Phase Commit (3PC)

**Improvement over 2PC**: Adds timeout to avoid indefinite blocking

**Not widely used** because:
- Network partitions still cause issues
- Adds latency
- Saga pattern preferred

---

### Saga Pattern

> **Analogy**: Planning a wedding by booking vendors independently. You book the venue first, then the caterer, then the band — each with their own contract, no central coordinator. If the caterer cancels two weeks out, you need compensating actions: cancel the venue deposit, notify the band. Each vendor manages their own contract and cancellation policy. There's no single person holding all the contracts; instead, each booking triggers the next step.

**Alternative to distributed transactions**: Long-running business processes with compensation

**Two Approaches:**

**1. Choreography (Event-Driven)**
```
Service A: CreateOrder → Emits OrderCreated event
Service B: Listens → ReserveInventory → Emits InventoryReserved
Service C: Listens → ProcessPayment → Emits PaymentProcessed
```

**2. Orchestration (Centralized)**
```
Saga Orchestrator:
  1. Call CreateOrder (Service A)
  2. If success → Call ReserveInventory (Service B)
  3. If success → Call ProcessPayment (Service C)
  4. If any fails → Execute compensating transactions (rollback)
```

**Compensating Transactions:**
```
Forward:    CreateOrder → ReserveInventory → ChargeCard
Compensate: RefundCard  ← ReleaseInventory ← CancelOrder
```

**Trade-offs:**
- ✅ No distributed locks (better availability)
- ✅ Works across services, even HTTP APIs
- ❌ Eventual consistency (intermediate states visible)
- ❌ Compensations must be idempotent

**Examples:**
- E-commerce checkout (order, payment, shipping)
- Travel booking (flight, hotel, car rental)
- Food delivery (order, restaurant, driver assignment)

---

### Outbox Pattern

**Problem**: Ensure database write and message publish happen atomically

**Solution:**
```
TRANSACTION:
  1. UPDATE users SET balance = balance - 100
  2. INSERT INTO outbox (event_type, payload)
  COMMIT

Background Process:
  1. Poll outbox table
  2. Publish events to message queue
  3. DELETE from outbox
```

**Guarantees:**
- At-least-once delivery (may duplicate, make consumers idempotent)
- No lost messages (atomically written with data)

**Examples:**
- Order service writes order + publishes "OrderCreated" event
- Payment service writes payment + publishes "PaymentProcessed" event

---

## Time and Ordering

**Question**: Node A writes x=1 at timestamp 10:00:00.100. Node B writes x=2 at timestamp 10:00:00.099 (its clock is 1ms behind). You sort by timestamp and conclude A's write happened first, so x=2 wins. But B's write was issued after A's — you just ordered them backwards. How do you establish causality without trusting any node's clock?

**Physical constraint**: NTP synchronizes clocks to within ~1ms on a LAN, ~50ms on WAN. But clocks also drift between sync intervals — commodity server clocks drift ~200ms/day without correction. There is no shared physical clock across machines. Any system that uses timestamps for ordering is silently broken in the presence of clock skew.

**Minimal solution**: Use wall-clock timestamps. Works fine in a single datacenter where clock skew is small enough to not matter for your SLA. Breaks when: two writes happen within the skew window (wrong order), or when a node's clock jumps backward after NTP correction (timestamps go backward mid-session).

**Production generalization**: Logical clocks decouple "ordering" from "physical time." Lamport timestamps give you a total order that respects causality. Vector clocks go further and let you detect concurrent events (neither happened before the other). Google Spanner sidesteps the problem entirely by using GPS + atomic clocks to bound uncertainty to ~7ms, then explicitly waiting out that window before committing.

### The Problem with Time

In distributed systems, **there is no global clock**. Each node has its own clock, which may drift.

### Physical Clocks

**NTP (Network Time Protocol)**: Synchronize clocks across nodes
- Accuracy: ~1ms on LAN, ~50ms on WAN
- Problem: Clock skew, clock drift

**Consequences:**
```
Node A: timestamp=100 → WRITE(x, 1)
Node B: timestamp=99  → WRITE(x, 2) (clock behind)
Result: x=1 appears "after" x=2 (wrong ordering!)
```

---

### Lamport Timestamps (Logical Clocks)

> **Analogy**: A collaborative group project where every revision is stamped with a version number that must always be greater than any version the author has previously seen. If Alice is on version 5 and receives a doc from Bob stamped version 8, her next version is 9. Everyone's version numbers stay monotonically increasing and consistent across the group — without needing everyone's wall clock to be synchronized.

**Idea**: Use logical counter instead of physical time

**Rules:**
1. Each node has counter (initially 0)
2. Before event: increment counter
3. Send message: include counter
4. Receive message: counter = max(local, received) + 1

```java
// Lamport clock implementation
public class LamportClock {
    private int counter = 0;

    public synchronized int tick() {
        return ++counter;
    }

    public synchronized int send() {
        return ++counter; // include in outgoing message
    }

    public synchronized void receive(int receivedTimestamp) {
        counter = Math.max(counter, receivedTimestamp) + 1;
    }
}
```

```
Node A: counter=1 → WRITE(x, 1)  // timestamp=1
Node A: sends message to Node B with timestamp=1
Node B: receives, updates counter = max(0, 1) + 1 = 2
Node B: counter=2 → WRITE(y, 2)  // timestamp=2
```

**Property**: If event A happens-before event B, then timestamp(A) < timestamp(B)

**Limitation**: Converse not true (can't determine causality from timestamps alone)

**Interview talking point**: "Lamport timestamps give you a total order consistent with causality, but two events with different timestamps may still be concurrent — you can't infer causality from timestamps alone. For that you need vector clocks."

---

### Vector Clocks

> **Analogy**: Shared Google Docs version tracking where each collaborator has their own counter. When Alice edits (her counter becomes 3), when Bob edits (his counter becomes 2). A document state of [Alice:3, Bob:2] means it includes exactly Alice's third edit and Bob's second edit. If you see [Alice:3, Bob:2] and another state of [Alice:2, Bob:3], you know these two states are concurrent — neither happened before the other. If you see [Alice:3, Bob:2] vs [Alice:2, Bob:1], the first is clearly newer.

**Improvement**: Capture causality between events

**Structure**: Each node maintains vector of counters (one per node)

```java
// Vector clock implementation
public class VectorClock {
    private final Map<String, Integer> clock = new HashMap<>();
    private final String nodeId;

    public VectorClock(String nodeId) {
        this.nodeId = nodeId;
    }

    public void increment() {
        clock.merge(nodeId, 1, Integer::sum);
    }

    public Map<String, Integer> send() {
        increment();
        return new HashMap<>(clock); // include in outgoing message
    }

    public void receive(Map<String, Integer> received) {
        received.forEach((node, ts) ->
            clock.merge(node, ts, Math::max));
        increment(); // increment own counter on receive
    }

    // Returns true if this clock is causally after other
    public boolean isAfter(Map<String, Integer> other) {
        return other.entrySet().stream()
            .allMatch(e -> clock.getOrDefault(e.getKey(), 0) >= e.getValue());
    }
}
```

```
Node A: [A:1, B:0, C:0] → WRITE(x, 1)
Node A → Node B: sends [A:1, B:0, C:0]
Node B: merges, increments own counter → [A:1, B:1, C:0]
```

**Causality Detection:**
```
V1 = [A:2, B:1, C:0]
V2 = [A:1, B:2, C:0]

V1 and V2 are concurrent (neither happened-before the other)
```

**Examples:**
- **Dynamo (DynamoDB, Riak, Cassandra)**: Uses version vectors (optimized vector clocks)
- **Distributed version control (Git)**: Track causality of commits

---

### Google Spanner's TrueTime

**Idea**: Use GPS + atomic clocks for global time with bounded uncertainty

```
TrueTime API:
  TT.now() → returns interval [earliest, latest]
  Guarantee: Actual time is within this interval
```

**Commit Wait:**
```
Transaction commits at timestamp T
Wait until T < TT.now().earliest
Then: All future transactions will see this commit
```

**Result**: External consistency (linearizability across datacenters)

---

## Conflict Resolution

**Question**: Two users simultaneously update the same shopping cart from different devices — one adds an item, the other removes a different item. Both writes go to different replicas. When the replicas sync, whose version wins? If you pick one and discard the other, you silently lose data. If you block both writes until you get consensus, you've killed availability. What is the right model?

**Physical constraint**: With replicas in different regions (50–150ms RTT), you cannot synchronize every write without adding 50–150ms of latency to every operation. For user-facing writes, that is unacceptable. So replicas accept writes independently and merge later — which guarantees conflicts will happen.

**Minimal solution**: Last-write-wins: the write with the later timestamp survives. Fast and simple. Breaks at: concurrent writes within the clock-skew window get wrong ordering; data from the "losing" write is silently discarded (user loses their cart changes with no error).

**Production generalization**: The right conflict strategy depends on the data type. LWW works when losing writes is acceptable (user profile photo). Version vectors work when you need to detect conflicts and surface them to the application. CRDTs are the cleanest solution: design your data structure so that any merge is mathematically correct — no conflicts possible by construction.

### Last-Write-Wins (LWW)

**Strategy**: Most recent write (by timestamp) wins

```
Node A: WRITE(x, 1) at t=100
Node B: WRITE(x, 2) at t=105
Result: x=2 (105 > 100)
```

**Problems:**
- Requires synchronized clocks (or use logical timestamps)
- Data loss (earlier write discarded)

**Use Cases:**
- Shopping cart (last user action matters)
- User profile updates

---

### Multi-Value Resolution

**Strategy**: Keep all conflicting values, let application decide

```
WRITE(x, 1) from Node A
WRITE(x, 2) from Node B (concurrent)
Result: x = [1, 2] (both values kept)
Application: Merge or choose
```

**Examples:**
- **DynamoDB**: Returns all versions on conflict
- **Riak**: Siblings (multiple values for same key)

---

### CRDTs (Conflict-Free Replicated Data Types)

**Idea**: Data structures that automatically merge without conflicts

**Types:**

**1. G-Counter (Grow-only Counter)**
```
Each node has its own counter
Merge: sum all counters
Result: Monotonically increasing, no conflicts
```

**2. PN-Counter (Positive-Negative Counter)**
```
Two G-Counters: increments and decrements
Value = sum(increments) - sum(decrements)
```

**3. LWW-Register**
```
(value, timestamp)
Merge: keep value with higher timestamp
```

**4. OR-Set (Observed-Remove Set)**
```
Add: Include element with unique ID
Remove: Remove specific ID
Merge: Union, remove only if explicitly removed
```

```java
// G-Counter CRDT example
public class GCounter {
    private final Map<String, Long> counts = new HashMap<>();
    private final String nodeId;

    public GCounter(String nodeId) {
        this.nodeId = nodeId;
    }

    public void increment() {
        counts.merge(nodeId, 1L, Long::sum);
    }

    public long value() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    // Merge: take element-wise max, then sum
    public void merge(GCounter other) {
        other.counts.forEach((node, count) ->
            counts.merge(node, count, Math::max));
    }
}
```

**Examples:**
- **Redis**: CRDT support in Redis Enterprise
- **Riak**: CRDT data types (counters, sets, maps)
- **Cosmos DB**: Supports CRDTs

---

## Distributed Coordination

**Question**: You have 20 Kafka broker nodes. Each partition needs exactly one leader broker. When a broker dies, a new leader must be elected within seconds, without two brokers both believing they are the leader for the same partition. Every broker is a Java process on a different machine. How do you coordinate this without a human, and without every broker talking to every other broker?

**Physical constraint**: With N services all needing to agree on shared state (who is leader, what is the current config), you need O(N²) coordination links if they all talk to each other. At N=20 that is 380 connections, each with its own failure mode. You need a smaller, more reliable coordination surface.

**Minimal solution**: Elect one service as the "meta-leader" and have all others register with it. Breaks when that meta-leader crashes — you now need a way to elect the meta-leader, which is the original problem again (infinite recursion).

**Production generalization**: ZooKeeper solves this by being a purpose-built, highly available coordination service backed by a consensus protocol (Zab, similar to Raft). You pay the cost of running a 3-or-5-node ZooKeeper ensemble once, and every distributed service in your stack can use it for leader election, locks, and config — amortizing the complexity across all consumers.

### Apache ZooKeeper

**Purpose**: Centralized coordination service for distributed systems

**Features:**
- **Configuration Management**: Store configuration, notify on changes
- **Leader Election**: Elect leader among distributed nodes
- **Distributed Locks**: Coordinate access to shared resources
- **Group Membership**: Track which nodes are alive

**Data Model:**
```
/
├── /config
│   ├── /database (connection string)
│   └── /feature_flags
├── /locks
│   └── /payment_processing
└── /leader
    └── /partition_0 (ephemeral node)
```

**ZNode Types:**
- **Persistent**: Remain until explicitly deleted
- **Ephemeral**: Deleted when session ends (for leader election)
- **Sequential**: Auto-incrementing suffix (for locks)

**Example: Leader Election**
```
1. Each node creates ephemeral sequential node: /leader/node-000001, /leader/node-000002
2. Node with smallest sequence number is leader
3. Others watch next smallest node
4. If leader fails (ephemeral node deleted), next node becomes leader
```

**Used By:**
- **Kafka**: Broker coordination, topic metadata
- **HBase**: Master election, region server coordination
- **Solr**: Cluster state management

---

## Summary: Decision Matrix

| Need | Solution | Trade-off |
|------|----------|-----------|
| **Strong consistency** | Spanner, Etcd (Raft) | Lower availability, higher latency |
| **High availability** | Cassandra, DynamoDB | Eventual consistency |
| **Leader election** | ZooKeeper, Etcd | Centralized coordinator |
| **Distributed transactions** | 2PC (rare), Saga pattern | Saga = eventual consistency |
| **Ordering without consensus** | Lamport/Vector clocks | Logical time, not real-time |
| **Conflict-free updates** | CRDTs | Limited operations |

---

**For SDE-3 Interviews**: Be ready to discuss trade-offs between consistency, availability, and latency. Know when to use consensus (Raft), when to use eventual consistency, and how to handle conflicts.

---

## Quick Revision

- **Consistency models**: Strong (linearizability) → sequential → causal → eventual. Strong = single-node semantics; eventual = high availability, stale reads possible.
- **Consensus**: Raft (leader election + log replication); Paxos (equivalent, less intuitive). Used by etcd, Consul, ZooKeeper for coordination.
- **Distributed transactions**: 2PC (blocking, not partition-tolerant); Saga (compensation, eventual consistency); Outbox (DB + message atomically).
- **Time**: Lamport clocks (happens-before); vector clocks (causality); TrueTime (Spanner, bounded uncertainty).
- **Conflict resolution**: LWW (timestamp); version vectors; CRDTs (merge without conflict).
- **Interview talking points**: "For strong consistency we'd use a CP store (etcd/Raft); for scale and availability we'd use eventual consistency and handle conflicts with LWW or application merge. Cross-service we'd use Saga, not 2PC."
- **Common mistakes**: Assuming 2PC is always the answer; ignoring replication lag when reading from replicas; using physical time for ordering across nodes without TrueTime-like guarantees.

---

## See Also

- **Idempotency, retry, backpressure** (the practical patterns that make distributed systems robust): [distributed-concepts.md](distributed-concepts.md)
- **Event sourcing and CQRS** (alternative to 2PC for cross-service data consistency): [event-driven-architecture.md](event-driven-architecture.md)
- **Kafka internals** (partitioning, ISR, consumer groups — distributed log in practice): [internals/kafka-internals.md](internals/kafka-internals.md)
- **HLD problems where this matters most**: [Payment System](../05-hld-problems/03-hard/payment-system.md) (exactly-once, ledger consistency), [Distributed Cache](../05-hld-problems/03-hard/distributed-cache.md) (consistent hashing, replication), [Distributed Message Queue](../05-hld-problems/03-hard/distributed-message-queue.md) (partition tolerance, at-least-once)
