# Replication

> **Copying data across multiple nodes for availability, read scaling, and durability.**

---

## File Mindmap

```
Replication
├── Why It Exists
│   ├── Problem 1 → primary goes down 10 min = revenue impact; need failover
│   └── Problem 2 → 500K read queries at 95% CPU; writes queuing; no single machine upgrade solves it
├── Replication Topologies
│   ├── Leader-Follower (Primary-Replica)
│   │   ├── All writes → leader; reads → followers
│   │   ├── Failover → promote follower on leader failure
│   │   └── Cons → replication lag; reads from follower may be stale
│   ├── Multi-Leader
│   │   ├── Multiple primaries accept writes; sync to each other
│   │   ├── Use case → multi-datacenter active-active; offline-capable clients
│   │   └── Cons → write conflicts; require conflict resolution (last-write-wins / CRDT / manual)
│   └── Leaderless (Dynamo-style)
│       ├── Writes sent to W nodes; reads from R nodes
│       ├── Quorum: W + R > N guarantees overlap (read sees latest write)
│       └── Use case → Cassandra, DynamoDB; high availability; eventual consistency
├── Sync vs Async Replication
│   ├── Synchronous → leader waits for follower ack before confirming write
│   │   ├── Guarantees → no data loss on failover
│   │   └── Cons → increased write latency; follower slowness blocks leader
│   └── Asynchronous → leader confirms immediately; follower catches up later
│       ├── Guarantees → low latency writes
│       └── Cons → replication lag; data loss window on leader crash before sync
├── Replication Lag Problems
│   ├── Read-your-writes → user writes profile, reads it back from stale replica; fix: route own reads to leader
│   ├── Monotonic reads → user sees post, refreshes, post disappears (stale replica); fix: sticky routing per session
│   └── Consistent prefix reads → events appear out of order across replicas; fix: causality tracking
├── Quorum (Leaderless)
│   ├── N = total replicas, W = write quorum, R = read quorum
│   ├── W + R > N → at least one node in read set has the latest write
│   ├── Strong consistency: W = N/2 + 1, R = N/2 + 1 (e.g. N=3, W=2, R=2)
│   └── High availability: W=1, R=1 (fast; weak consistency)
├── Split Brain & Fencing
│   ├── Split brain → network partition; two leaders accept writes independently
│   ├── Fencing token → monotonically increasing token issued with each leadership; old leader's writes rejected by storage
│   └── STONITH → "Shoot The Other Node In The Head"; force-kill old leader before promoting new
├── Consensus (Raft)
│   ├── Leader election → nodes vote; majority required
│   ├── Log replication → leader sends log entries; majority ack → commit
│   └── Used in → etcd, CockroachDB, Kafka KRaft
├── Trade-offs
│   ├── Pros → read scale; fault tolerance; geographic distribution
│   └── Cons → replication lag; conflict resolution complexity; added latency for sync replication
├── Failure Scenarios
│   ├── Leader fails → promote follower; risk data loss if async; use semi-sync for critical data
│   ├── Follower lag → monitor replication delay; alert at N seconds
│   └── Network partition → quorum prevents split brain; minority partition rejects writes
└── Interview Angles
    ├── "Sync vs async replication trade-off?" → sync: no data loss but higher latency; async: fast but lose latest writes on crash
    ├── "How do you prevent split brain?" → fencing tokens; Raft consensus; STONITH
    ├── "What is W+R>N?" → quorum ensures read set overlaps with write set; always sees latest write
    └── Follow-up: "What is replication lag and how do you handle read-your-writes?" → route user's own reads to leader
```

---

## 1. Why Replication Exists

**Question**: Your database primary handles all reads and writes. It goes down for 10 minutes. What is your revenue impact? Now: your primary is healthy but 500,000 users are hammering it with read queries for a product catalog that changes once per hour. Reads are at 95% CPU while writes queue up. Which hardware upgrade solves this?

**Physical constraint**: A single disk can only serve so many concurrent I/O operations — SSDs top out at ~100k IOPS under random reads. A single CPU executing query plans saturates. Network between your datacenter and users adds 1–100ms per RTT depending on geography. No vertical upgrade escapes these limits: one machine can only absorb so many parallel reads before queueing.

**Minimal solution**: Take a nightly pg_dump backup. Restore on failure. Works until: 8 hours of data is lost in the gap, restore takes 30 minutes, and reads still all hit the same single machine during normal operation.

**Production generalization**: Streaming replication keeps one or more follower nodes continuously in sync with the leader. On leader failure, a follower is promoted in seconds (not 30 minutes). Read queries are distributed across followers, offloading the primary entirely. The tradeoff is replication lag: followers may be milliseconds to seconds behind, which matters when a user reads their own just-written data.

---

## 2. Core Principles

### Topologies

| Topology | How | Use case |
|----------|-----|----------|
| **Leader–follower (primary–replica)** | One leader for writes; followers replicate; reads can go to followers | Most RDBMS, MongoDB |
| **Multi-leader** | Multiple nodes accept writes; replicate to each other | Multi-datacenter; offline-first |
| **Leaderless** | No single leader; quorum writes and reads (e.g. W=2, R=2, N=3) | Cassandra, DynamoDB |

### Sync vs Async Replication

The newspaper analogy: sync means the newspaper doesn't go to print until all presses confirm receipt. One slow press holds up the entire evening edition. Async means the editor fires off the copy and keeps working — the presses catch up when they can, but a press crash between send and print loses that edition permanently.

| Mode | How | Pros | Cons |
|------|-----|------|------|
| **Synchronous** | Leader waits for replica(s) to ack before confirming write | No data loss on leader failover | Higher latency; availability tied to replica |
| **Asynchronous** | Leader acks immediately; replicas updated in background | Low latency; leader not blocked by replica | Replica can lag; possible data loss if leader fails before replication |

### Replication Lag

Async replicas are eventually consistent. If a user writes and immediately reads from a replica, they may not see their own write. Solutions:
- **Read-your-writes**: After a write, route that user's reads to the leader for a short window.
- **Monotonic reads**: Always route a given user to the same replica so they don't see time go backwards.

### Architecture (leader–follower)

```
  Writes ──▶ Leader ──▶ Replication stream ──▶ Follower 1
                │                              Follower 2
  Reads ──▶ Follower 1 / Follower 2 (stale reads possible)
  Reads ──▶ Leader (for read-your-writes consistency)
```

### Quorum (Leaderless)

A magazine with 5 printing presses. Write quorum W=3: the editor needs 3 presses to confirm before the edition is "written." Read quorum R=3: a reader needs to check 3 presses. Because W + R > N (3+3 > 5), at least one press in any read set must have the latest edition — so readers always see the most recent write.

---

## 3. Real-World Usage

- **PostgreSQL / MySQL**: Primary + read replicas; async or semi-sync; failover via promotion.
- **MongoDB**: Replica set; one primary, secondaries replicate; automatic failover.
- **Cassandra / DynamoDB**: Leaderless; quorum (W, R, N); tunable consistency.
- **Kafka**: Partition replicas; in-sync replicas (ISR); leader handles writes.

---

## 4. Trade-offs

| Choice | Pros | Cons |
|--------|------|------|
| **Sync replication** | No loss on failover | Latency; if replica is down, writes can block or fail |
| **Async replication** | Low latency; leader not blocked | Replication lag; possible loss on leader failure |
| **Read from replica** | Scale reads | Stale reads (lag); need to handle consistency (e.g. read-your-writes) |
| **Multi-leader** | Write locally in multiple DCs | Conflict resolution; complexity |

**When to use**: Need HA or read scaling; can tolerate eventual consistency for reads from replicas.  
**When not**: Single-node acceptable; or strong consistency with no lag (then sync and read from primary only).

---

## 5. Failure Scenarios

| Scenario | Mitigation |
|----------|------------|
| Leader fails | Promote replica to leader (manual or automatic); clients reconnect to new leader |
| Replica lag | Monitor lag; route critical reads to leader; increase replica capacity or reduce write load |
| Split brain (multi-leader) | Conflict resolution (LWW, vector clocks, CRDTs); or avoid multi-leader |
| Replication loop (multi-leader) | Use topology that avoids cycles; or use conflict-free structures |

**Split brain** is the nightmare scenario: two printing presses each believe they are the editor-in-chief. Both accept different edits. When they reconnect, you have two divergent editions and must merge them. CP systems (like etcd) avoid this by requiring quorum before accepting writes — if a node can't reach a majority, it stops accepting writes rather than risk split brain.

---

## 6. Performance Considerations

- **Write latency**: Sync replication adds round-trip(s) to replica(s); async does not.
- **Read scaling**: More replicas → more read capacity; balance with replication load and storage cost.
- **Replication lag**: Depends on write volume and replica capacity; can be seconds under load.

---

## 7. Implementation Patterns

### Read-Your-Writes Routing (Java)

```java
@Service
public class UserRepository {
    private final DataSource primary;    // leader
    private final DataSource replica;    // follower

    // After a write, this user needs to read from primary
    // to avoid seeing stale data (replication lag)
    private final Set<String> recentWriters = new ConcurrentHashSet<>();

    public void updateProfile(String userId, UserProfile profile) {
        jdbcTemplate(primary).update(
            "UPDATE users SET profile = ? WHERE id = ?", profile, userId);
        recentWriters.add(userId);
        // Remove after 5 seconds — enough for async replication to catch up
        scheduler.schedule(() -> recentWriters.remove(userId), 5, SECONDS);
    }

    public UserProfile getProfile(String userId) {
        // Route to primary if this user just wrote (read-your-writes guarantee)
        DataSource ds = recentWriters.contains(userId) ? primary : replica;
        return jdbcTemplate(ds).queryForObject(
            "SELECT profile FROM users WHERE id = ?", profileRowMapper, userId);
    }
}
```

### Quorum Reads and Writes (Cassandra-style)

```java
// W=3, R=3, N=5 (magazine with 5 printing presses)
// W + R > N ensures at least 1 overlap → always read the latest write
CqlSession session = CqlSession.builder()
    .withKeyspace("app")
    .build();

// Write with QUORUM consistency (3 of 5 presses must confirm)
session.execute(
    SimpleStatement.newInstance("INSERT INTO users (id, name) VALUES (?, ?)", id, name)
        .setConsistencyLevel(ConsistencyLevel.QUORUM));

// Read with QUORUM consistency (check 3 of 5 presses; at least 1 has the latest)
Row row = session.execute(
    SimpleStatement.newInstance("SELECT * FROM users WHERE id = ?", id)
        .setConsistencyLevel(ConsistencyLevel.QUORUM)).one();
```

- **Single leader + N replicas**: Standard for RDBMS; async or semi-sync; read replicas for reporting and read scaling.
- **Leaderless quorum**: W + R > N for strong consistency; tune W, R for latency vs durability.
- **Cross-datacenter**: Async replica in second DC for DR; or multi-leader if needed for local writes.

---

## Quick Revision

- **Purpose**: HA, read scaling, durability.
- **Leader–follower**: One writer; replicas copy; reads can go to replicas (stale possible).
- **Sync vs async**: Sync = no loss, higher latency; async = low latency, possible loss.
- **Leaderless**: Quorum (W, R, N); W + R > N guarantees reading the latest write.
- **Replication lag**: Handle with read-your-writes (route to primary after write) or monotonic reads.
- **Interview**: "We use a primary and two async read replicas so writes are fast and we scale reads; we accept replication lag and route read-your-writes to the primary when needed."

---

## Interview Questions Asked

### Conceptual
1. **"How does Raft consensus work?"** → One leader elected by majority vote. Leader appends to its log and replicates to followers; entry is committed once a majority acknowledges it. On leader failure, followers hold an election — the node with the most up-to-date log wins. Testing: do you understand why Raft needs an odd number of nodes and why it's preferred over Paxos for clarity.
2. **"What is replication lag and how does it affect reads?"** → Async replication means followers may be seconds (or more) behind the leader. A read from a follower immediately after a write can return stale data. Handle with: read-your-writes (route to leader after write), monotonic reads (always read from the same replica), or synchronous replication (latency cost). Testing: operational awareness of async replication's practical impact.
3. **"What are fencing tokens and why are they needed?"** → A monotonically increasing token issued by a lock service. When a process holds a lock but is paused (GC, network delay), another process gets a new lock with a higher token. The storage system rejects writes with an old token — preventing split-brain writes from a zombie process. Testing: distributed systems correctness under partial failure.

### Comparison / Trade-off
1. **"Leader-follower vs multi-leader vs leaderless — trade-offs?"** → Leader-follower: simple, consistent writes, single write bottleneck, failover needed. Multi-leader: low-latency local writes across datacenters, but write conflicts are hard to resolve. Leaderless (Dynamo/Cassandra): highly available, no failover, but reads must quorum-read and handle conflict resolution (last-write-wins or application merge).

### Scenario / Design
1. **"What is a split-brain scenario in replication?"** → Two nodes both believe they are the primary (e.g., after a network partition heals). Both accepted writes independently — data has diverged. Prevent with: majority quorum for leader election (Raft), fencing tokens, STONITH (Shoot The Other Node In The Head — forcibly terminate the old primary before promoting new one).
2. **"How do you ensure read-after-write consistency with async replication?"** → After a user writes, route their subsequent reads to the primary for a short window (e.g., 1 minute). Alternatively, pass the write's replication position to the read path and wait until the replica has caught up to that position before serving. Testing: practical consistency guarantee design.
