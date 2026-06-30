> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Consensus protocols — ZAB, Raft, Paxos, and the protocols behind ZooKeeper, etcd, DynamoDB, Kafka KRaft. The shared idea: leader-based, quorum-acknowledged, total-order replication of state.
>
> **Key topics:**
> - Core problem: replicate state across N nodes tolerating (N-1)/2 failures; agree on a single value per write
> - Paxos (foundational): proposer-acceptor-learner; proven correct but famously hard to implement
> - Raft: leader-based, leader election + log replication; designed for understandability; used by etcd, Consul, TiKV, Kafka KRaft
> - ZAB (ZooKeeper Atomic Broadcast): leader-based, 3 phases (discovery/sync/broadcast); optimized for FIFO ordering; 64-bit ZXID = epoch + counter
> - KRaft: Kafka's internal Raft; replaced ZooKeeper in 3.3+ to remove the 200K-partition metadata ceiling
> - Comparison: ZAB has explicit recovery phase; Raft catches up via heartbeats; both are Paxos-derived
>
> **Key takeaway:** All these protocols solve the same problem with the same recipe: one leader, majority quorum, total-order broadcast. The differences are in failure-recovery and ordering guarantees.

---
module: 04-advanced-topics
topic: Internals
status: unread
tags: [04-advanced-topics, system-design, internals]
---
# Consensus Protocols: ZAB, Raft, Paxos

> Replicated-state-machine protocols that let a cluster of N nodes agree on a sequence of writes while tolerating (N-1)/2 failures.

---

## 1. Why Consensus Protocols Exist

**Question**: A configuration value (`db.host = 10.0.0.5`) must be visible to 50 services. Each service connects directly to a database to read it. The database is a single point of failure. Replicate it to 3 nodes — but now the values can diverge. Which value is correct?

**Physical constraint**: A single coordinator can fail (crash, network partition). Multiple replicas can disagree if they accept writes independently. We need a protocol that:
- Survives `f = (N-1)/2` simultaneous node failures
- Agrees on the same value across nodes (consensus)
- Rejects conflicting values (safety)
- Makes progress when a majority is alive (liveness)

**Minimal solution**: One leader. All writes go through the leader. The leader broadcasts each write as a log entry. Majority of followers must acknowledge before the entry is "committed." Followers apply committed entries in log order. If the leader dies, a new leader is elected from the majority.

**Production generalization**: This is the recipe behind every modern consensus protocol. Paxos proved the recipe is correct (1998). Raft re-stated it in a form engineers can actually implement (2014). ZAB specialized it for FIFO ordering (2007). KRaft is Raft applied to Kafka's metadata log (2022).

---

## 2. The Core Recipe

Every consensus protocol has the same skeleton:

```
1. LEADER ELECTION
   - Candidates request votes
   - Each node votes for at most one candidate per term/epoch
   - Candidate with majority votes becomes leader
   - Term/epoch monotonically increases; stale leaders step down

2. LOG REPLICATION
   - Client sends write to leader
   - Leader appends entry to its log
   - Leader sends entry to all followers
   - Followers append to their log, ACK
   - When majority ACKed, leader commits the entry
   - Leader notifies followers; followers apply to state machine

3. FAILURE DETECTION
   - Leader sends heartbeats (every ~100-200ms)
   - If a follower misses heartbeats for election_timeout, it becomes candidate
   - Election happens; new leader emerges
```

**Key invariant**: a committed entry is on a majority of nodes. Any future leader must have a majority → must have at least one of the committed entries → can never lose committed state.

---

## 3. Paxos (Foundational)

Leslie Lamport, 1998. The original proof that consensus is solvable. Famously difficult to implement correctly — the paper is deliberately abstract.

### Roles

- **Proposer**: Picks a value, sends to acceptors
- **Acceptor**: Votes on values; majority wins
- **Learner**: Replicates the chosen value

### Two Phases

```
Phase 1 (Prepare):
  Proposer sends prepare(n) to majority of acceptors
  Acceptors respond: "I promise not to accept any proposal < n" + "the highest-numbered proposal I already accepted"

Phase 2 (Accept):
  Proposer picks a value (from highest accepted, or its own)
  Proposer sends accept(n, value) to majority
  Acceptors accept if n >= promised

Chosen: when majority acceptors have accepted the same value
```

**Multi-Paxos**: a leader is elected for many rounds; subsequent rounds skip Phase 1. This is what Raft and ZAB effectively implement.

**Used by**: Google Chubby, Apache Cassandra's earlier variants, Spanner. Most production systems use a Multi-Paxos variant or have switched to Raft.

---

## 4. Raft

Diego Ongaro & John Ousterhout, 2014. Designed for understandability. Now the dominant consensus protocol.

### Components

- **Leader**: handles all client writes
- **Follower**: passive; responds to leader's AppendEntries
- **Candidate**: follower that timed out on heartbeat; running for election

### Term (logical clock)

A monotonically increasing integer. New term starts at every election. Each log entry is identified by `<term, index>`. Stale leaders see a higher term and step down.

### Leader Election

```
1. Follower times out without heartbeat (150-300ms random)
2. Becomes candidate, increments term, votes for itself
3. Sends RequestVote to all peers
4. If majority votes YES → becomes leader, sends heartbeats
5. If split vote → random backoff, retry

Election rules:
  - Each node votes YES for at most one candidate per term
  - Candidate must have log at least as up-to-date as voter
    (voter rejects if candidate's last log term < voter's, or same term but shorter log)
```

### Log Replication

```
1. Client sends command to leader
2. Leader appends entry to its log (uncommitted)
3. Leader sends AppendEntries to all followers
4. Follower appends if previous entry matches (consistency check)
5. Follower ACKs
6. When majority ACKed → leader commits entry
7. Leader applies to state machine, returns to client
8. Next AppendEntries includes commitIndex → followers apply too
```

### Safety

- **Election safety**: at most one leader per term
- **Leader append-only**: leader never overwrites/deletes entries in its log
- **Log matching**: if two entries have same `<term, index>`, all preceding entries are identical
- **Leader completeness**: if an entry is committed in term T, every leader in term > T has that entry
- **State machine safety**: if a node applies entry at index I to its state machine, no other node will apply a different entry at I

**Used by**: etcd, Consul, CockroachDB, TiKV, Kafka KRaft, Wechat (internal).

### Read Optimization

Naively, reads also go through the leader (linearizable). Two optimizations:
- **ReadIndex**: leader confirms it's still leader (heartbeat round), then serves local read. No log write.
- **Lease reads**: leader serves local read for a short lease without confirmation. Faster but bounded staleness.

---

## 5. ZAB (ZooKeeper Atomic Broadcast)

2007. Pre-dates Raft. Optimized for ZooKeeper's coordination use case where FIFO ordering within a session matters.

### Three Phases

```
Phase 1: DISCOVERY
  - New leader elected
  - Followers send their highest accepted zxid
  - Leader computes the union of all uncommitted transactions
  - New epoch (newLeader.epoch) is set

Phase 2: SYNCHRONIZATION
  - Leader sends missing transactions to followers
  - Followers acknowledge after applying
  - Once majority has the latest state → leader proposes NEW_LEADER packet
  - NEW_LEADER is committed once majority acks

Phase 3: BROADCAST
  - Normal operation
  - Client write → leader proposes txn → followers ACK → commit
```

### ZXID (64-bit transaction ID)

```
ZXID = <epoch (32 bits), counter (32 bits)>

Example: 0x100000001
  Epoch:   0x1     (1 in decimal — this leader's era)
  Counter: 0x00000001 (1st transaction in this epoch)
```

Each new leader election increments the epoch. The counter resets to 0. Stale leaders are detected when a follower sees a higher epoch.

### Why ZAB Differs from Raft

- **Explicit recovery phase**: ZAB synchronizes all followers before accepting writes. Raft catches up via heartbeats; followers learn the commit index on each AppendEntries.
- **FIFO ordering**: ZAB guarantees that messages from the same leader are processed in the order sent. Useful for ZooKeeper's watch semantics.
- **Causal ordering**: ZAB's NEW_LEADER packet ensures all followers see the same history before processing new writes.

### ZAB vs Raft Comparison

| Dimension | ZAB | Raft |
|-----------|-----|------|
| **Designed for** | ZooKeeper coordination | General replicated state machines |
| **Leader identifier** | `epoch` (in ZXID) | `term` (separate counter) |
| **Log entry ID** | `zxid = <epoch, counter>` | `<term, index>` |
| **Leader election** | Highest zxid wins | Highest `(term, log length)` wins |
| **Recovery** | Explicit sync phase before broadcast | Heartbeat-driven catch-up |
| **Commit rule** | Majority ACK → leader broadcasts COMMIT | Majority storage = committed |
| **Causal ordering** | Strict FIFO | Same in practice (sequential log) |
| **Read model** | Default stale; `sync()` for linearizable | Default stale; ReadIndex for linearizable |
| **Implementation** | Java (ZooKeeper) | Go (etcd), Rust (tikv) |
| **Spec complexity** | Higher (3 phases) | Lower (2 components) |

**When ZAB beats Raft**: hierarchical data model + per-session FIFO ordering (natural for ZooKeeper's watch API).
**When Raft beats ZAB**: anything greenfield. Raft's spec is shorter; implementations are easier to verify.

---

## 6. KRaft: Kafka's Internal Raft (KIP-500)

### Why Kafka Originally Used ZooKeeper

Pre-2.8, Kafka brokers depended on ZK for:
- **Controller election**: which broker is the active controller
- **Broker registration**: ephemeral nodes for liveness
- **Topic/partition metadata**: configs, ISR lists, replica assignments
- **Consumer offsets** (until 0.9; moved to `__consumer_offsets`)

### Why ZK Became the Bottleneck

At 100K+ partitions:
- **Metadata fan-out**: controller restart requires re-pushing all metadata to all brokers through ZK. Takes minutes at scale.
- **Two-system ops**: operators maintain Kafka + ZK + ZK's JVM/GC/quorum sizing.
- **Scalability ceiling**: ZK's in-memory data model limits partition count.

### KRaft Architecture

```
Before (ZooKeeper mode):
  ZooKeeper Ensemble (3 or 5 nodes, separate cluster)
         │ metadata reads/writes
  Kafka Brokers + 1 active Controller
         │ leader election, ISR updates → ZK

After (KRaft mode, Kafka 3.3+):
  KRaft Controllers (3 or 5 from broker pool, integrated Raft)
         │ Raft log for metadata changes
  Kafka Brokers (regular, non-controller)
         │ fetch metadata from KRaft controllers
```

- A subset of Kafka brokers act as **KRaft controllers** using internal Raft
- All cluster metadata is stored in an internal Kafka topic `__cluster_metadata`
- Controllers replicate this topic via Raft
- Regular brokers subscribe to `__cluster_metadata` and apply changes locally

### Failover

```
If leader controller fails:
  → KRaft election → new leader in milliseconds
  → New leader already has full metadata log → immediately ready to serve
  → vs ZooKeeper: new controller had to re-read state from ZK → minutes
```

### Scalability Improvement

- ZooKeeper mode: ~200K partitions practical limit
- KRaft mode: millions of partitions (metadata stored in log, not ZK in-memory)

### Migration Steps (ZK → KRaft)

1. Upgrade all brokers to Kafka 3.x
2. Run `kafka-storage.sh format` to initialize KRaft metadata directory
3. Start KRaft controllers in "migration mode" — they coexist with ZK temporarily
4. Dual-write period: both ZK and KRaft controllers active; ZK is source of truth
5. Run migration tool: transfers all ZK metadata to KRaft log
6. Decommission ZooKeeper ensemble
7. KRaft is now sole metadata store

**Interview insight**: "Kafka removed ZooKeeper because at 100K+ partitions, ZK's in-memory metadata model became a bottleneck for controller restart time. KRaft stores metadata in a Kafka topic replicated via Raft — Kafka becomes self-managing, brokers subscribe to the metadata log directly, and failover takes milliseconds instead of minutes."

---

## 7. When to Use Each

| System | Protocol | Why |
|--------|----------|-----|
| ZooKeeper | ZAB | Built into the project; hierarchical data + FIFO needs |
| etcd | Raft | Designed for K8s; REST API; cloud-native |
| Kafka (3.3+) | Raft (KRaft) | Removed ZK dependency; higher partition ceiling |
| Cassandra | Paxos (lightweight) | Per-partition consensus only; eventually consistent at cluster level |
| DynamoDB | Paxos-derived | Per-partition 3-AZ replication; AWS doesn't publish internals |
| Consul | Raft | Service discovery + config; same lineage as etcd |
| Spanner | Paxos | Google's global database; cross-DC Paxos |
| CockroachDB | Raft | Distributed SQL; per-range Raft groups |

---

## Real-World Usage

| System | Protocol | Lesson |
|--------|----------|--------|
| MongoDB | Raft (in newer versions) | Moved off custom master-slave |
| YugabyteDB | Raft | Per-tablet Raft; strong consistency |
| FoundationDB | Paxos (custom) | Per-shard Paxos; deterministic simulation testing |
| CockroachDB | Raft | Same per-range design as Yugabyte |

**Production insight**: Raft has won the greenfield consensus protocol race. ZAB persists in ZooKeeper. Multi-Paxos variants persist in academic and large-scale systems (Spanner, MegaStore). The "Paxos vs Raft" interview question is a trap — at the recipe level they're identical; the differences are in failure-recovery and ordering.

---

## Trade-offs

| Dimension | Pro | Con |
|-----------|-----|-----|
| **Single leader** | Simple; linear writes | Leader bottleneck on writes; ~10K-100K writes/sec typical |
| **Majority quorum** | Tolerates (N-1)/2 failures | Cannot make progress with majority down |
| **Total order** | Predictable; easy to reason about | Throughput capped at 1 leader's processing rate |
| **Leader election** | Self-healing | Adds latency on failure; split votes stall |
| **Linearizable reads** | Strong consistency | Read throughput lower; needs ReadIndex/lease |

---

## Failure Scenarios

| Scenario | Symptom | Mitigation |
|----------|---------|------------|
| Leader crash | Brief unavailability (election timeout) | Tune heartbeat/election timeout; pre-vote to prevent disruptions |
| Split vote | Election goes through multiple terms | Randomized election timeout (150-300ms) |
| Network partition | Minority side cannot make progress; majority continues | Built-in: minority side rejects writes |
| Stale leader | Old leader thinks it's still leader | Term/epoch check on every RPC; old leader steps down |
| Slow disk on follower | Log append blocks; leader waits for ACK | Use SSDs; monitor `follower.lag`; remove slow follower |
| Snapshot too large | Recovery takes minutes; replay blocked | Incremental snapshots; install snapshot in chunks |
| Log divergence | Old leader had uncommitted entries; new leader overwrites | Log matching property ensures followers truncate and catch up |

---

## Performance

- **Throughput**: 10K-100K writes/sec per leader (depends on disk and entry size)
- **Latency**: 1 RTT (leader → majority) for commit. With followers in same DC: 1-5 ms
- **Read latency**:
  - Stale read: O(1) on local follower
  - Linearizable via ReadIndex: 1 RTT (heartbeat) + local read
- **Election timeout**: 150-300 ms typical; tune to be > 1 RTT to avoid false elections
- **Snapshot frequency**: every ~10K-100K entries to bound log replay time

---

## Implementation Patterns

Most production code uses a library, not a hand-rolled protocol:

- **Java**: Apache Curator (ZooKeeper), Atomix Raft
- **Go**: etcd's Raft library (used in TiKV, CockroachDB, Consul), hashicorp/raft
- **C++**: braft (Apache), openraft (Rust port)
- **Multi-language**: FoundationDB's simulation approach (test against random failures)

**Don't implement your own consensus protocol.** Use a library. The edge cases (split votes, log divergence, snapshot alignment) are notoriously hard to get right.

---

## Quick Revision

- Consensus = agree on a sequence of values across N nodes, tolerating (N-1)/2 failures
- All modern protocols use one leader + majority quorum + total-order log
- **Paxos**: foundational, correct, hard to implement
- **Raft**: simplified Paxos; leader election + log replication; default for new systems
- **ZAB**: 3 phases (discovery/sync/broadcast); 64-bit ZXID = epoch + counter; FIFO ordering; ZooKeeper's protocol
- **KRaft**: Raft inside Kafka; replaced ZK; metadata stored in `__cluster_metadata` topic
- Election requires majority votes; each term/epoch has at most one leader
- Log entries committed when majority ACKed; new leader must have all committed entries
- Stale leaders detected via term/epoch and step down

---

## See Also

- [04-advanced-topics/internals/zookeeper-internals.md](zookeeper-internals.md) — ZAB in context
- [04-advanced-topics/internals/kafka-internals.md](kafka-internals.md) — KRaft migration story
- [04-advanced-topics/internals/dynamodb-internals.md](dynamodb-internals.md) — Paxos-derived 3-AZ replication
- [04-advanced-topics/internals/cassandra-internals.md](cassandra-internals.md) — lightweight Paxos per partition
- [04-advanced-topics/distributed-concepts.md](../distributed-concepts.md) — idempotency, retries
- [02-building-blocks/consistent-hashing.md](../../02-building-blocks/consistent-hashing.md) — partitioning (orthogonal to consensus)

---

## Interview Questions Asked

**Q: Compare Raft and ZAB. When would you use each?**

A: Both implement leader-based, majority-quorum, total-order replication. ZAB predates Raft and was designed specifically for ZooKeeper's hierarchical data model + FIFO ordering. ZAB has an explicit 3-phase protocol (discovery → sync → broadcast) with strict per-session ordering; Raft uses 2 components (leader election + log replication) and catches up followers via heartbeats. For a new system, choose Raft — the spec is shorter, more implementations exist (etcd, TiKV, CockroachDB), and the engineering community has converged on it. Choose ZAB only if you're extending ZooKeeper or need its specific FIFO guarantees.

**Q: Why does Kafka use Raft (KRaft) instead of ZAB?**

A: KRaft replaces ZooKeeper entirely. The motivation is operational: at 100K+ partitions, ZK's in-memory metadata model became a bottleneck because the controller restart required re-reading all metadata through ZK, taking minutes. KRaft stores metadata in an internal Kafka topic `__cluster_metadata` replicated via Raft, so the new leader already has the full log. Failover is milliseconds. The protocol itself (Raft vs ZAB) wasn't the issue — both would have worked. The win was removing the external dependency.

**Q: How does Paxos differ from Raft in practice?**

A: Both are leader-based majority-quorum protocols in production (Multi-Paxos ≈ Raft). The differences are in the abstract framing: Paxos is formulated as a series of independent "accept a value" rounds, which makes it general but harder to map to a real system's log. Raft re-organizes the same idea around a leader term and a log, which makes the implementation clearer. In practice, "we use Paxos" usually means "we use a Multi-Paxos variant that's effectively Raft."

**Q: What happens during a network partition in Raft?**

A: Minority side cannot make progress. The majority side continues accepting writes (leader is there, followers ACK, quorum is met). When the partition heals, the minority side rejoins as a follower. If the old leader was on the minority side, it steps down when it sees a higher term from the new leader. If the old leader was on the majority side, the new leader (elected on the minority side... wait, no — minority cannot elect) — correction: if majority stays with the old leader, the minority's new election never achieves quorum and the old leader continues. No data loss; no split brain.

**Q: What is the safety guarantee that prevents two leaders from both committing conflicting writes?**

A: The "no two leaders in same term" rule + the "leader must have all committed entries" rule. A new candidate must win a majority of votes to become leader. Each voter only votes for a candidate whose log is at least as up-to-date as its own (last term, then length). Therefore the new leader's log is guaranteed to contain all entries that were committed on a majority of nodes. Stale leaders detect a higher term in any incoming RPC and step down immediately, so they cannot commit anything new.
