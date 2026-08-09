> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** A comprehensive glossary defining the most important vocabulary and terminology used in System Design interviews.
>
> **Key concepts:**
> - Data concepts: Sharding, Partitioning, Replication, Consistent Hashing.
> - Consistency models: Strong Consistency, Eventual Consistency, Linearizability.
> - Networking concepts: CDN, Load Balancer, API Gateway, Reverse Proxy.
> - Concurrency concepts: Race condition, Deadlock, Semaphore, Mutex.
>
> **Key takeaway:** Using the exact right terminology (e.g., "We will use Consistent Hashing to minimize cache invalidation during node failure") immediately signals seniority to the interviewer. Use this glossary to refine your technical vocabulary.

---
module: 08-reference
status: unread
tags: [08-reference, system-design, reference]
---
# System Design Glossary

> **Essential vocabulary for SDE-3 level system design interviews. Master these terms to speak with authority about distributed systems.**

---

## Reference Mindmap

```
System Design Glossary
├── Core Problem
│   └── Imprecise language signals junior thinking; precise vocabulary signals seniority
├── Consistency Models
│   ├── Linearizability → strongest; read always sees latest write; Spanner, Zookeeper
│   ├── Sequential Consistency → global order agreed by all nodes, not wall-clock
│   ├── Eventual Consistency → all replicas converge given no new writes; Cassandra, DynamoDB
│   └── Causal Consistency → causally related ops seen in order; unrelated ops may differ
├── CAP Theorem
│   ├── C → Consistency (every read sees latest write)
│   ├── A → Availability (every request gets a response)
│   ├── P → Partition Tolerance (system works despite network splits)
│   └── Rule → cannot guarantee all 3; partition is unavoidable → choose C or A
├── ACID vs BASE
│   ├── ACID → Atomicity, Consistency, Isolation, Durability (RDBMS guarantee)
│   └── BASE → Basically Available, Soft state, Eventually consistent (NoSQL trade-off)
├── SLA / SLO / SLI
│   ├── SLI → metric being measured (e.g., request success rate)
│   ├── SLO → target for the SLI (e.g., 99.9% success rate)
│   └── SLA → contract with penalty if SLO is missed
├── RPO / RTO
│   ├── RPO (Recovery Point Objective) → max acceptable data loss (time-based)
│   └── RTO (Recovery Time Objective) → max acceptable downtime to restore service
├── Key Abbreviations
│   ├── WAL → Write-Ahead Log (crash recovery in Postgres, Kafka)
│   ├── CDC → Change Data Capture (stream DB changes; Debezium)
│   ├── CAS → Compare-And-Swap (optimistic locking primitive)
│   └── HLC → Hybrid Logical Clock (distributed timestamp combining physical + logical)
└── Interview Angles
    ├── "What consistency does your DB provide?" → name the model and its cost
    ├── "What's the RPO for this system?" → drives backup frequency and replication lag budget
    └── "CP or AP system?" → know which side of CAP your design falls on and why
```

---

## Consistency

### Linearizability (Strong Consistency)

The strongest consistency model. Every operation appears to execute instantaneously at some point between its invocation and response, and all operations appear in a globally consistent order.

**Key property**: After a write completes, any subsequent read (from any client, on any replica) sees that write or a later one.

**Real example**: Google Spanner, Apache Zookeeper. Your bank balance — if you withdraw $100, any ATM in the world immediately shows the reduced balance.

**Cost**: Every read/write must go through a consensus round (Paxos/Raft) → higher latency.

---

### Sequential Consistency

Operations appear to execute in some global sequential order, consistent with the program order of each individual process. Weaker than linearizability (doesn't require real-time ordering).

**Key property**: All nodes agree on the order of operations, but that order doesn't have to match wall-clock time.

**Real example**: CPU memory models without special memory barriers.

---

### Causal Consistency

If operation A causally precedes operation B (A happened before B and B was aware of A), then every process sees A before B.

**Key property**: Causally related operations are seen in the same order by all nodes. Concurrent operations (no causal relationship) may be seen in different orders.

**Real example**: MongoDB sessions, DynamoDB consistent reads per partition key. Social media — if you post a reply to a comment, anyone who sees your reply must also see the original comment.

---

### Eventual Consistency

If no new updates are made, eventually all replicas will converge to the same value. No guarantees about when.

**Key property**: Reads may return stale data. The system guarantees convergence, not freshness.

**Real example**: DNS propagation, S3, Cassandra (default), DynamoDB (default). Shopping cart on Amazon — your cart may look different on different devices for a few seconds.

---

### Read-Your-Writes Consistency (Session Consistency)

A user always sees their own writes. Reads by the same user reflect their own previous writes.

**Key property**: Subset of causal consistency. Only guarantees your own writes are visible to you.

**Real example**: Standard web apps. You update your profile → you immediately see the update. Others may see the old version for a few seconds.

---

## Distributed Systems Fundamentals

### CAP Theorem

**C**onsistency + **A**vailability + **P**artition Tolerance — pick any two.

```
Partition: Network failure splits nodes into groups that can't communicate.

CP systems: Consistent + Partition Tolerant
  During partition: reject writes to maintain consistency
  Examples: HBase, Zookeeper, etcd
  
AP systems: Available + Partition Tolerant
  During partition: serve reads/writes (may be stale)
  Examples: Cassandra, DynamoDB, CouchDB

CA systems: Only possible without partitions (single server)
  Not realistic for distributed systems
```

**Modern nuance**: "C" in CAP = linearizability (very strong). Many "CP" systems provide weaker consistency. The real trade-off is **consistency vs latency vs availability** (PACELC theorem).

---

### PACELC Theorem

Extension of CAP. **PA/ELC**: When there's a **P**artition, trade **A**vailability for **C**onsistency (or vice versa). **E**lse (no partition), trade **L**atency for **C**onsistency.

```
DynamoDB: PA/EL — Available during partition; low latency (eventual consistency) normally
Spanner:  PC/EC — Consistent during partition; consistent (but higher latency) normally
MySQL:    PC/EC — Synchronous replication; trades latency for consistency
Cassandra: PA/EL — Available during partition; tunable (eventual by default)
```

---

### Linearizability vs Serializability

Often confused. They are **different** properties:

| | Linearizability | Serializability |
|--|----------------|----------------|
| **Applies to** | Single operations (reads/writes) | Transactions (groups of operations) |
| **Constraint** | Must respect real-time ordering | Must be equivalent to some serial execution |
| **Scope** | Any operation | Only transactions |
| **Level** | Object/register | Database |

**Strict serializability** = Linearizability + Serializability (both properties, strongest combined guarantee). Used by Google Spanner.

---

### Vector Clocks

A mechanism for tracking causality in distributed systems without requiring synchronized clocks.

```
Each node maintains a vector of counters, one per node.

Node A: [A:1, B:0, C:0]  ← A performed 1 operation
Node B: [A:0, B:1, C:0]
A sends message to B: B receives A's vector [A:1, B:0, C:0]
B merges: B's vector = [max(A:0,A:1), max(B:1,B:0), max(C:0,C:0)] = [A:1, B:1, C:0]

Causality: Event X happened-before Event Y if X's vector ≤ Y's vector (component-wise)
Concurrent events: Neither vector dominates the other
```

**Used by**: Amazon Dynamo, Riak, distributed version control systems (Git uses similar concepts).

---

### Hybrid Logical Clocks (HLC)

Combines physical time (wall clock) with logical time (vector clock-like counters). Allows events to be ordered by physical time when clocks are synchronized, and by logical time when they diverge.

```
HLC timestamp: (physical_time, logical_counter)

If physical clocks agree: timestamps sort by physical time → real-time ordering
If clocks disagree (drift): logical counter breaks ties → still causal ordering

Used by: CockroachDB, YugabyteDB, MongoDB cluster transactions
Advantage over pure vector clocks: timestamps are compact and comparable to wall time
```

---

### Paxos

A consensus algorithm for getting a distributed system to agree on a single value, even when nodes fail.

```
Phases:
  Prepare: Proposer sends "Prepare(n)" with ballot number n
           Acceptors promise not to accept lower ballot numbers
  Accept:  Proposer sends "Accept(n, value)"
           Acceptors accept if no higher ballot promised
  Learn:   Proposer announces the committed value

Properties:
  Safety: Only one value is ever chosen (no two values accepted)
  Liveness: Eventually a value is chosen (given sufficient non-faulty nodes)

Multi-Paxos: Extends to a sequence of values (log replication)
Used by: Google Chubby, Apache Zookeeper (ZAB — a variant), original Google Spanner
```

---

### Raft

A consensus algorithm designed to be more understandable than Paxos. Decomposes consensus into:

```
Leader Election:
  One node becomes leader (heartbeats to maintain authority)
  If leader fails: election with randomized timeout (prevents split vote)

Log Replication:
  All writes go through leader
  Leader appends to log, replicates to followers
  Committed when majority (quorum) acknowledge

Safety:
  Leader has all committed entries (election only succeeds if candidate's log ≥ majority)
  At most one leader per term

Used by: etcd (Kubernetes), CockroachDB, TiKV, Consul
```

---

### ZAB (Zookeeper Atomic Broadcast)

ZooKeeper's consensus protocol. Similar to Raft:
- **Leader** proposes changes
- **Followers** acknowledge
- Committed when majority acknowledge
- Primary ordering: all changes from a leader have consistent ordering

---

## Data Structures

### CRDT (Conflict-free Replicated Data Type)

A data structure that can be updated independently on multiple replicas without coordination, and guarantees convergence to a consistent value when replicas merge.

```
Types:
  G-Counter: Grow-only counter. Each node has its own count; total = sum of all.
  PN-Counter: Positive-Negative counter. Supports both increment and decrement.
  OR-Set: Observed-Remove Set. Add/remove operations with unique tags.
  LWW-Register: Last-Write-Wins Register. Each update has a timestamp; highest wins.
  
Merge operation:
  Always deterministic and commutative: merge(A, B) = merge(B, A)
  No coordination needed — merge wherever/whenever
  
Used by: Redis CRDT, Riak, Cassandra counters, Apple Notes sync
```

---

### LSM Tree (Log-Structured Merge-Tree)

A data structure that optimizes write performance by converting random writes into sequential writes.

```
Write path:
  1. Write to in-memory buffer (MemTable) — very fast
  2. When MemTable full: flush to immutable SSTable on disk (sequential write)
  3. Background compaction: merge multiple SSTables, remove duplicates/tombstones

Read path:
  1. Check MemTable (in-memory)
  2. Check SSTable level 0, then 1, then 2... (Bloom filter per SSTable reduces I/O)

Trade-off:
  Writes: Much faster than B-Tree (sequential vs random I/O)
  Reads: Slower than B-Tree (may check multiple SSTables)
  
Used by: LevelDB, RocksDB, Cassandra, HBase, ClickHouse, TiKV
```

---

### B-Tree vs LSM Tree

| | B-Tree | LSM Tree |
|--|--------|---------|
| Write pattern | Random I/O (in-place update) | Sequential I/O (append-only) |
| Read performance | Fast (O(log N) one lookup) | Slower (check multiple levels) |
| Write performance | Slower at high throughput | Much faster |
| Space amplification | Low | Higher (multiple copies during compaction) |
| Write amplification | Lower | Higher (data rewritten during compaction) |
| Use case | OLTP reads (PostgreSQL, MySQL) | Write-heavy (Cassandra, RocksDB, HBase) |

---

## Networking

### TCP vs UDP

| | TCP | UDP |
|--|-----|-----|
| Connection | Connection-oriented (3-way handshake) | Connectionless |
| Reliability | Guaranteed delivery, ordering, error detection | No guarantees |
| Throughput | Lower (overhead of ACKs, flow control) | Higher |
| Latency | Higher (ACKs, head-of-line blocking) | Lower |
| Use cases | HTTP, databases, file transfer | Video streaming, DNS, gaming, VoIP |

### HTTP/1.1 vs HTTP/2 vs HTTP/3

```
HTTP/1.1:
  One request per connection (or keep-alive with pipelining — buggy)
  Head-of-line blocking: requests queued, one slow response blocks others
  Text-based headers (repeated every request)

HTTP/2:
  Multiplexing: multiple requests on one TCP connection simultaneously
  Binary framing: more efficient than text
  Header compression (HPACK)
  Server push (proactively send resources before requested)
  Still has TCP head-of-line blocking (one dropped packet stalls all streams)

HTTP/3 (QUIC):
  Runs on UDP (not TCP)
  No head-of-line blocking (streams are independent)
  Built-in TLS 1.3 (faster handshake)
  Connection migration (seamless switch between WiFi → cellular)
  Used by: Chrome, Cloudflare, Facebook
```

---

## Operations

### RTO vs RPO

```
RTO (Recovery Time Objective):
  Maximum acceptable time a system can be down after a failure.
  "After a disaster, we must be back online within 4 hours."
  Drives: Failover speed, hot standby vs cold backup, automation.

RPO (Recovery Point Objective):
  Maximum acceptable amount of data that can be lost (measured in time).
  "We can afford to lose at most 1 hour of data."
  Drives: Backup frequency, replication mode (sync vs async).

Relationship:
  Lower RTO → More expensive (hot standby, faster provisioning)
  Lower RPO → More expensive (synchronous replication, frequent backups)
  RPO=0 + RTO=0 → Synchronous active-active (very expensive, complex)
```

### SLI / SLO / SLA

```
SLI (Service Level Indicator):
  A metric that measures a service's performance.
  Examples: request latency P99, error rate, uptime percentage.
  "P99 latency = 120ms, error rate = 0.02%"

SLO (Service Level Objective):
  A target value for an SLI.
  "P99 latency < 200ms, error rate < 0.1%"
  Internal goal — not a contract.

SLA (Service Level Agreement):
  A contractual commitment with penalties for breach.
  "99.9% uptime guaranteed; if we violate this, you get a credit."
  External commitment — legal/financial consequences.

Error Budget:
  Error budget = 1 - SLO = allowance for failures.
  99.9% SLO → 0.1% error budget → 8.76 hours/year of allowed downtime.
  When budget is consumed: prioritize reliability over feature work.
```

---

## Resilience & Scaling Patterns

### Idempotency

A property where performing the same operation multiple times produces the same result as performing it once — critical for safe retries over unreliable networks.

**Key property**: An idempotency key (client-generated UUID sent with the request) lets the server recognize and deduplicate a retried request instead of double-processing it.

**Real example**: Payment APIs (Stripe) require an `Idempotency-Key` header — retrying a charge request after a timeout doesn't charge the card twice.

---

### Backpressure

A mechanism for a consumer to signal to a producer that it's overwhelmed, so the producer slows down instead of the consumer's queue growing unbounded and crashing.

**Real example**: Reactive Streams (`request(n)`) — a subscriber pulls only as many items as it can handle; TCP flow control is backpressure at the transport layer.

**Without it**: Unbounded queue growth → OOM, or the consumer falls further and further behind (unbounded lag).

---

### Circuit Breaker

A pattern that stops calling a failing downstream dependency after a failure threshold, failing fast instead of piling up latency/threads on a doomed call.

```
States: Closed (calls flow normally) → Open (calls fail immediately, no network call)
        → Half-Open (a trial call checks if the dependency recovered) → Closed or Open
```

**Real example**: Netflix Hystrix / resilience4j — protects a service from cascading failure when one downstream dependency is slow or down.

---

### Fan-out (Push vs. Pull)

Distributing one event to many consumers. Fan-out-on-write (push) does the distribution work at write time; fan-out-on-read (pull) defers it to read time.

**Real example**: Twitter's feed — for most users, fan-out-on-write pushes a new tweet into every follower's feed cache immediately; for celebrity accounts with millions of followers, fan-out-on-read is used instead to avoid a write storm (the "celebrity problem").

---

### Geohashing / Quadtree

Techniques for indexing 2D location data so "find nearby points" is an efficient range query instead of a full scan.

**Geohash**: Encodes lat/long into a base32 string where shared prefixes mean spatial proximity — nearby locations share a longer prefix, enabling a simple string-prefix range query.

**Quadtree**: Recursively subdivides 2D space into 4 quadrants until each leaf holds few enough points — denser areas get subdivided more, giving natural load balancing for uneven point density.

**Real example**: Uber (H3 hexagonal grid, a geohash variant), Google Maps.

---

### Consumer Group / Offset

In a partitioned log (Kafka), a consumer group is a set of consumers that split a topic's partitions among themselves so each message is processed once per group; the offset is the position of the last-consumed message per partition.

**Key property**: Committing an offset after processing (not before) gives at-least-once delivery; committing before gives at-most-once.

---

### Rate Limiting Algorithms

```
Token Bucket:   bucket refills at fixed rate; each request consumes a token; allows bursts up to bucket size
Leaky Bucket:   requests queue and drain at fixed rate; smooths bursts, no burst allowance
Fixed Window:   counter resets every N seconds; simple but allows 2x burst at window boundary
Sliding Window: weights current + previous window by time elapsed; avoids the boundary burst problem
```

**Interview angle**: Naming "token bucket" vs. just "rate limiter" is the difference between an SDE-2 and SDE-3 answer — know the boundary-burst flaw of fixed window and why sliding window fixes it.

---

### Saga Pattern

Manages a distributed transaction across multiple services as a sequence of local transactions, each with a compensating action to undo it if a later step fails.

**Key property**: Avoids distributed locks/2PC's availability cost by trading atomicity for eventual consistency plus explicit rollback logic.

**Real example**: An order-placement saga: reserve inventory → charge payment → schedule shipping; if shipping fails, a compensating transaction refunds the payment and releases inventory.

---

### Two-Phase Commit (2PC)

A protocol for atomic commit across multiple nodes: a coordinator asks all participants to "prepare" (vote), then commits only if all voted yes, else aborts all.

**Cost**: Blocking — if the coordinator crashes after prepare but before commit, participants hold locks indefinitely. This is why Saga is often preferred for long-lived distributed transactions.

---

### Gossip Protocol

A peer-to-peer communication style where nodes periodically exchange state with a few random peers, spreading information across the cluster in O(log N) rounds without a central coordinator.

**Real example**: Cassandra and Riak use gossip for cluster membership and failure detection.

---

### Cache Stampede (Thundering Herd)

When a popular cache key expires, many concurrent requests simultaneously miss the cache and hit the database at once, potentially overwhelming it.

**Fix**: Request coalescing (only one request recomputes, others wait), probabilistic early expiration, or locking around the recompute.

---

### Hot Key / Hot Partition

A single key or shard receiving disproportionate traffic relative to others, becoming a bottleneck even though the system is horizontally scaled overall.

**Real example**: A celebrity's user ID in a sharded social graph, or a viral URL in a URL shortener's cache — consistent hashing distributes keys evenly but can't fix uneven *access* to one key.

**Fix**: Key-level replication/caching, splitting a hot key into sub-keys with a suffix (`key#1`, `key#2`, ...) and merging on read.

---

### Snowflake ID

A 64-bit unique ID scheme combining a timestamp, a machine/worker ID, and a per-millisecond sequence number, generated locally with no coordination between nodes.

```
| 1 bit unused | 41 bits timestamp | 10 bits worker ID | 12 bits sequence |
```

**Key property**: Roughly time-sortable (higher timestamp → higher ID) and collision-free across machines without a central counter.

**Real example**: Originated at Twitter; used in most "unique ID generator" HLD answers.

---

## Idiomatic Interview Phrasings

| Term | Use This Instead of... |
|------|----------------------|
| "Strong consistency" | ❌ "Up-to-date data" |
| "Eventual consistency" | ❌ "It might not be consistent" |
| "Linearizable" | Use for operations (not transactions) |
| "Serializable" | Use for transactions |
| "Idempotent" | ❌ "Safe to retry" |
| "Exactly-once semantics" | ❌ "No duplicates" |
| "Partition tolerant" | ❌ "Handles network failures" |
| "Consensus (Raft/Paxos)" | ❌ "Voting" |
| "Write amplification" | ❌ "Lots of writes to disk" |
| "Tombstone" | Deleted record marker in LSM/Cassandra |
| "Compaction" | Background merge of SSTables |
| "WAL" | Write-Ahead Log — pre-write for crash recovery |
| "Fence token" | Anti-phantom lock invalidation token |
