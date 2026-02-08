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

## Introduction to Distributed Systems

A **distributed system** is a collection of independent computers that appears to its users as a single coherent system.

### Why Distributed Systems?

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

### Strong Consistency (Linearizability)

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

### Sequential Consistency

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

### Causal Consistency

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

### Eventual Consistency

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

Consensus protocols allow distributed nodes to agree on a single value despite failures.

### Raft Consensus

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

### Paxos

**Problem**: Achieve consensus in an distributed environment with unreliable network

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

### Two-Phase Commit (2PC)

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

### Three-Phase Commit (3PC)

**Improvement over 2PC**: Adds timeout to avoid indefinite blocking

**Not widely used** because:
- Network partitions still cause issues
- Adds latency
- Saga pattern preferred

### Saga Pattern

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
Forward: CreateOrder → ReserveInventory → ChargeCard
Compensate: RefundCard ← ReleaseInventory ← CancelOrder
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

### Lamport Timestamps (Logical Clocks)

**Idea**: Use logical counter instead of physical time

**Rules:**
1. Each node has counter (initially 0)
2. Before event: increment counter
3. Send message: include counter
4. Receive message: counter = max(local, received) + 1

```
Node A: counter=1 → WRITE(x, 1)  // timestamp=1
Node A: sends message to Node B with timestamp=1
Node B: receives, updates counter = max(0, 1) + 1 = 2
Node B: counter=2 → WRITE(y, 2)  // timestamp=2
```

**Property**: If event A happens-before event B, then timestamp(A) < timestamp(B)

**Limitation**: Converse not true (can't determine causality from timestamps alone)

### Vector Clocks

**Improvement**: Capture causality between events

**Structure**: Each node maintains vector of counters (one per node)

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

**Examples:**
- **Redis**: CRDT support in Redis Enterprise
- **Riak**: CRDT data types (counters, sets, maps)
- **Cosmos DB**: Supports CRDTs

---

## Distributed Coordination

### Apache ZooKeeper

**Purpose**: Centralized coordination service for distributed systems

**Features:**
-**Configuration Management**: Store configuration, notify on changes
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

**ZNodes Types:**
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
