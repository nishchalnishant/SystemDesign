---
module: 04-advanced-topics
topic: internals
status: unread
tags: [04-advanced-topics, distributed-systems, consensus, raft, paxos]
---
# Raft and Paxos: Consensus Algorithms

Consensus is the problem of getting a cluster of nodes to agree on a single value — or a sequence of values — despite node failures and network partitions. Raft and Paxos are the two algorithms that underpin almost every distributed database, log, and coordination service in production.

For a broader survey of where consensus fits in the consistency landscape, see `01-foundations/05-advanced-distributed-theory/01-consistency-and-conflicts.md`.

---

## Why Consensus Is Hard

In a single-node system, "decide" is trivial: write to disk, done. In a distributed system with N nodes:

- Any node can crash at any time
- Messages can be delayed, reordered, or lost
- The network can partition: nodes can't tell if a peer is dead or just slow

**The impossibility result (FLP):** In an asynchronous network (no timing guarantees), it's impossible to guarantee consensus with even one faulty process. In practice, algorithms work around this by using timeouts (probabilistic, not provably safe) and requiring a majority quorum (survive up to (N-1)/2 failures).

---

## Quorum Intuition

With N nodes, require any write to be acknowledged by a **majority** (⌊N/2⌋ + 1).

```
N=3: quorum = 2   (can tolerate 1 failure)
N=5: quorum = 3   (can tolerate 2 failures)
N=7: quorum = 4   (can tolerate 3 failures)
```

Any two majorities overlap in at least one node. That overlapping node carries the latest accepted value. This is the core insight behind both Paxos and Raft.

---

## Paxos

Paxos (Lamport, 1989) is the foundational consensus algorithm. It operates in two phases for each value decided.

### Roles

| Role | Responsibility |
|------|----------------|
| Proposer | Initiates a new proposal; drives the two-phase protocol |
| Acceptor | Votes on proposals; persists accepted values |
| Learner | Learns the decided value (may be the same nodes) |

### Phase 1: Prepare

Proposer picks a **ballot number** `n` (monotonically increasing, globally unique — typically `round * num_nodes + node_id`).

```
Proposer → all Acceptors: PREPARE(n)

Each Acceptor:
  if n > max_promised_ballot:
    save n as max_promised_ballot  (persist to disk)
    reply PROMISE(n, previously_accepted_ballot, previously_accepted_value)
  else:
    reply NACK (you've already seen a higher ballot)
```

If the proposer receives PROMISE from a majority (⌊N/2⌋ + 1), it proceeds to Phase 2. If not, increment `n` and retry.

### Phase 2: Accept

```
Proposer:
  if any PROMISE carried an (accepted_ballot, accepted_value):
    v = the value with the highest accepted_ballot  (must use this value!)
  else:
    v = my own proposed value

Proposer → all Acceptors: ACCEPT(n, v)

Each Acceptor:
  if n >= max_promised_ballot:
    save (n, v) as (accepted_ballot, accepted_value)
    reply ACCEPTED(n, v)
  else:
    reply NACK

If Proposer receives ACCEPTED from majority: value v is decided
```

**Why must the proposer use the highest previously accepted value?**  
If an acceptor has already accepted a value from a previous round, that value might have been decided (learned by a learner) before the network partitioned. Using any other value would violate the invariant that only one value is ever decided.

### Paxos Failure Modes

**Dueling proposers (livelock):** Two proposers keep incrementing ballot numbers and preempting each other. Neither ever achieves a quorum of ACCEPT responses. Paxos provides no mechanism to prevent this — it's a liveness problem, not a safety one. Fix: elect a distinguished leader (Multi-Paxos).

**Multi-Paxos:** Run a leader election (using Paxos itself) to select a single proposer for a term. The leader skips Phase 1 for subsequent proposals in the same term. This is what ZooKeeper's ZAB protocol does.

---

## Raft

Raft (Ongaro & Ousterhout, 2014) was designed to be more understandable than Paxos without sacrificing correctness. It makes consensus a sequence of log entries, not a single value.

### Key Design Choices

1. **Strong leader**: All writes go through the leader; followers only accept entries from the current leader. Simplifies reasoning — there's only one source of truth.
2. **Log-centric**: Consensus is about agreeing on a sequence of log entries (like a WAL). A state machine replays the log to get current state.
3. **Explicit terms**: Each leader election is a new term (monotonically increasing integer). Any message from a stale term is rejected.

### Roles

| Role | Responsibility |
|------|----------------|
| Leader | Receives all writes; replicates to followers; heartbeats to prevent elections |
| Follower | Passive; accepts entries from leader; votes in elections |
| Candidate | Transitions to this role on election timeout; requests votes |

### Leader Election

```
Normal operation:
  Followers receive heartbeats (empty AppendEntries) every ~100ms.

Election timeout (150–300ms, randomized per node):
  If no heartbeat received:
    1. Follower → Candidate
    2. Increment current_term
    3. Vote for self
    4. Send RequestVote(term, last_log_index, last_log_term) to all peers

Vote granted if:
  (a) Candidate's term >= voter's current_term
  (b) Voter hasn't voted in this term already
  (c) Candidate's log is at least as up-to-date as voter's log
      (compare last_log_term, then last_log_index — ensures candidate has all committed entries)

If candidate receives votes from majority: becomes Leader
If another leader sends higher term: revert to Follower
```

**Randomized timeout prevents ties**: If all nodes timed out simultaneously, they'd all become candidates and split votes indefinitely. Randomization (e.g., 150–300ms uniform random per node) makes one node almost always time out first.

### Log Replication

```
Client → Leader: "SET x = 5"

Leader:
  1. Append entry to local log (index=N, term=T, command="SET x=5")
  2. Send AppendEntries(term, prev_log_index, prev_log_term, entries, leader_commit) to all followers

Follower:
  if prev_log_index/term matches: append entries, reply success
  else: reply failure (leader will retry with earlier index — log repair)

Once leader receives success from majority:
  3. Commit entry (apply to state machine)
  4. Respond to client
  5. Next heartbeat piggybacks leader_commit → followers commit too
```

**Log repair**: If a follower's log diverges (e.g., it was partitioned), the leader walks back `nextIndex[follower]` until a matching entry is found, then replays all entries from that point.

### Safety: The Log Matching Property

Raft guarantees that if two log entries in different nodes have the same index and term, all entries before that index are also identical. This is enforced by the `prev_log_index` + `prev_log_term` check in AppendEntries.

**Election safety**: Only a candidate with the most up-to-date log can win an election (log completeness property). This prevents a stale node from becoming leader and overwriting committed entries.

---

## Raft vs Paxos: Key Differences

| | Paxos | Raft |
|---|---|---|
| **Unit of consensus** | Single value | Log entry sequence |
| **Leader** | Optional (Multi-Paxos adds it) | Mandatory; always one leader |
| **Log gaps** | Possible (holes in log) | Not allowed; entries are contiguous |
| **Understandability** | Hard to implement correctly | Designed for clarity |
| **Log repair** | Complex, left to implementation | Explicit leader-driven repair |
| **Real implementations** | ZooKeeper (ZAB ≈ Multi-Paxos), Chubby | etcd, CockroachDB, TiKV, MongoDB (with modifications) |

### Log Holes in Paxos

In Multi-Paxos, if a leader proposes entries at indices 1, 2, 3, but only 2 and 3 are committed (1 never got a majority before leader crash), index 1 is a "hole." A new leader must fill holes with a no-op entry before the log is usable. Raft avoids this entirely.

---

## Where Consensus Appears in Production

| System | Algorithm | What Is Being Agreed On |
|--------|-----------|--------------------------|
| etcd (Kubernetes) | Raft | Configuration, service discovery, distributed locks |
| ZooKeeper | ZAB (Multi-Paxos variant) | Distributed coordination, leader election |
| CockroachDB | Raft (per-range) | Key-value log per shard |
| TiKV (TiDB) | Raft | Key-value log per region |
| MongoDB | Raft-like (replica sets) | Oplog replication |
| Google Spanner | Paxos (per-shard) | True-time ordered commits |
| Apache Kafka (KRaft) | Raft | Metadata log (replaces ZooKeeper dependency) |

---

## Common Failure Scenarios

### Split Brain

**Scenario**: Network partition splits 5 nodes into groups of 3 and 2. The group of 3 elects a new leader. The group of 2 still has the old leader.

**Paxos/Raft resolution**: The old leader can't commit new entries — it can only reach 2 nodes, not a majority (3). All writes to the minority partition fail. The majority partition elects a new leader and proceeds. When the partition heals, the minority nodes get their logs repaired by the new leader.

### Leader Crash After Commit

**Scenario**: Leader commits an entry (majority acknowledged), then crashes before informing the client.

**Resolution**: Client retries. The new leader already has the entry (it was on a majority). The retry is a no-op if idempotent, or returns a duplicate error if the client includes an operation ID. This is why distributed consensus alone doesn't give you exactly-once — you need idempotency keys at the application layer.

### Slow Follower

**Scenario**: One follower is slow (GC pause, disk I/O). Leader must wait for only a majority — the slow follower doesn't block commits. The slow follower catches up when it recovers (leader resends missing entries). For very far-behind followers, use a snapshot transfer (install snapshot RPC in Raft).

---

## Performance Characteristics

**Latency (per commit):**
- Minimum: 1 round trip (leader → majority followers → ack → commit)
- At speed-of-light in the same DC: ~1ms round trip
- Cross-DC (e.g., 3 regions for geo-redundant Raft): ~100–300ms per commit — this is why Spanner uses TrueTime to bound clock skew instead of synchronous cross-region Paxos for every write

**Throughput:**
- Single Raft group: ~10,000–50,000 ops/sec (limited by leader CPU and disk fsync)
- Scale via sharding: CockroachDB and TiKV use thousands of independent Raft groups, each responsible for a key range

**Leader bottleneck:**
- All writes in a Raft group go through the leader. For read scalability, followers can serve stale reads or use **ReadIndex** (ask leader for current commit index before serving the read) to serve linearizable reads without going through the leader's write path.

---

## Interview Questions to Practice

1. **"Why can't two leaders exist simultaneously in Raft?"**
   *Each term has at most one leader. A candidate can only win if it gets votes from a majority. Two candidates can't both get majority votes in the same term — any two majorities overlap. If a candidate wins an election, any other candidate in the same term can't also win (it won't receive enough votes). Old leaders are neutralized: any node that receives a message with a higher term immediately becomes a follower.*

2. **"What happens to uncommitted log entries when a Raft leader crashes?"**
   *Uncommitted entries may be overwritten. A new leader is elected from nodes with the most up-to-date log (by last log term, then last log index). Entries not yet on a majority are not considered committed — the new leader may overwrite them. Entries on a majority are always preserved because any majority quorum for the new election overlaps with the commit quorum. The new leader appends a no-op entry at the start of its term to establish the commit point.*

3. **"A client sends a write to a Raft leader. The leader crashes after persisting locally but before achieving quorum. What happens?"**
   *The write is not committed. The client's request times out. The client retries. The new leader may or may not have the entry — if it wasn't replicated to any follower, it's gone (the entry was local-only). If it was replicated to some followers but didn't reach a majority, the new election process may or may not preserve it depending on which node becomes leader. To handle this correctly, the client should use idempotency keys so a retry is safe.*

4. **"When would you choose Raft over Paxos for a new system?"**
   *Always choose Raft for new implementations unless you need to interface with an existing Paxos-based system. Raft is designed for implementability: it explicitly defines leader election, log repair, and snapshot transfer. Paxos leaves these as implementation details, and getting them right is notoriously hard (Google published a paper specifically on the complexity of implementing Paxos correctly). The understandability gap matters in practice — Raft implementations have fewer subtle bugs.*

5. **"How does etcd use Raft, and what does that mean for Kubernetes?"**
   *etcd is a distributed key-value store that uses Raft for consensus. Kubernetes stores all cluster state — pod specs, service definitions, secrets, config maps — in etcd. The Raft protocol guarantees that all etcd nodes agree on the current state. If etcd loses quorum (more than ⌊N/2⌋ nodes fail), the Kubernetes API server becomes read-only — no new pods can be scheduled. This is why production Kubernetes clusters run etcd with 3 or 5 nodes across failure domains.*

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design a Distributed Job Scheduler](../../05-hld-problems/03-hard/distributed-job-scheduler.md)
- [Design a Distributed Message Queue (Kafka)](../../05-hld-problems/03-hard/distributed-message-queue.md)
- [Design a Stock Exchange](../../05-hld-problems/03-hard/stock-exchange.md)

