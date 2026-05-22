# ZooKeeper Internals

## Overview
Apache ZooKeeper is a distributed coordination service used for maintaining configuration, naming, synchronization, and group services in distributed systems.

---

## File Mindmap

```
ZooKeeper Internals
├── Why It Exists
│   ├── Problem → distributed systems need shared configuration and coordination; no reliable single source of truth
│   └── Forces → coordination via shared DB fails under network partitions; need linearizable, fault-tolerant store
├── ZAB Protocol (ZooKeeper Atomic Broadcast)
│   ├── Leader-based → one leader handles all writes; followers replicate
│   ├── Phase 1 (discovery) → elect leader; followers sync epoch number
│   ├── Phase 2 (synchronization) → leader syncs followers to latest committed state
│   ├── Phase 3 (broadcast) → leader proposes txn; quorum acks → leader commits → followers apply
│   └── Quorum → N/2+1 followers must ack before commit; survives (N-1)/2 node failures
├── Znodes (Data Nodes)
│   ├── Hierarchical namespace → /app/config, /app/leader; like a filesystem
│   ├── Persistent znode → survives client disconnect; exists until explicitly deleted
│   ├── Ephemeral znode → auto-deleted when creating client session expires; used for leader election
│   ├── Sequential znode → ZK appends monotonic counter to name; /lock/request-0000000001
│   └── Data limit → max 1MB per znode; recommended <1KB; not a general-purpose data store
├── Watches
│   ├── One-shot listeners → client sets watch on znode; gets notification on change; must re-register
│   ├── Events → NodeCreated, NodeDeleted, NodeDataChanged, NodeChildrenChanged
│   ├── Guarantee → notification delivered before any subsequent reads by same client
│   └── Use case → config hot-reload; leader change detection; service membership changes
├── Leader Election
│   ├── Each candidate creates ephemeral sequential znode under /election/
│   ├── Lowest sequence number → current leader
│   ├── Others watch the znode just below them (not the leader directly) → avoids herd effect
│   └── Leader dies → ephemeral znode deleted → next in sequence gets notified → becomes leader
├── Distributed Locks
│   ├── Acquire → create ephemeral sequential znode under /locks/
│   ├── Check → if your znode has lowest sequence → you hold lock
│   ├── Wait → watch the znode with next lower sequence; wait for its deletion
│   ├── Release → delete your znode; next waiter gets notified
│   └── Fencing → lock holder gets a lock epoch; storage layer rejects writes with stale epoch
├── Service Discovery
│   ├── Service registers → create ephemeral znode /services/payment/instance-1 with host:port data
│   ├── Client reads → list children of /services/payment/ → get all live instances
│   ├── Instance crash → ephemeral znode deleted → clients watching get NodeDeleted event
│   └── Used by → Kafka (pre-KRaft), HBase, HDFS NameNode HA, Hadoop YARN
├── Session Management
│   ├── Session timeout → if ZK doesn't hear from client within timeout → session expires → ephemerals deleted
│   ├── Session ID → unique 64-bit token; reconnects resume same session if within timeout
│   └── Heartbeat → client sends PING every session_timeout/3; ZK responds with PONG
├── Trade-offs
│   ├── Pro: linearizable reads (with sync), strong ordering guarantees, mature ecosystem
│   └── Con: write throughput ~10k/s (not a data store); watch re-registration overhead; not partition-tolerant (CP)
├── Failure Modes
│   ├── Herd effect → all clients watch same znode (leader) → leader dies → thundering herd of watches
│   ├── Session expiry surprise → GC pause > session timeout → all ephemerals deleted → lock lost
│   └── Quorum loss → majority of nodes down → ZK rejects all writes; clients see CONNECTION_LOSS
├── Real-World Usage
│   ├── Kafka (pre-KRaft) → broker registration, controller election, topic metadata storage
│   ├── HBase → master election, region server registration, table metadata
│   └── Apache Solr → cluster state management, shard leader election in SolrCloud
└── Interview Angles
    ├── Leader election → "coordinate distributed election?" → ephemeral sequential znodes; watch predecessor
    ├── Distributed lock → "prevent concurrent writes?" → ZK lock recipe; fencing token for safety
    └── vs etcd → "ZK vs etcd?" → etcd uses Raft (simpler), REST API, better for cloud-native; ZK older, JVM
```

---

## Core Concepts

### Znodes (Data Nodes)
ZooKeeper's data model is a **hierarchical namespace**, like a file system.

```
/
├─ /app
│   ├─ /app/config
│   └─ /app/leader
├─ /services
│   ├─ /services/service1
│   └─ /services/service2
```

**Znode Properties:**
- **Data**: Small payload (typically < 1MB, recommended < 1KB)
- **Version**: Optimistic locking (CAS operations)
- **ACLs**: Access Control Lists
- **Stat**: Metadata (created time, modified time, version)

### Znode Types

#### 1. Persistent
- Created explicitly, deleted explicitly
- Survive client disconnection

```java
zk.create("/app/config", data, OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
```

#### 2. Ephemeral
- **Automatically deleted** when client session ends
- Cannot have children
- Used for **presence detection** (leader election, service discovery)

```java
zk.create("/services/node1", data, OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
```

#### 3. Sequential
- Appends monotonically increasing counter
- Example: `/app/lock-0000000001`, `/app/lock-0000000002`
- Used for **distributed locks** and **queues**

```java
zk.create("/app/lock-", data, OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
```

---

## Architecture

### Ensemble (Cluster)
- **Odd number** of servers (3, 5, 7 typical)
- **Quorum-based**: Majority must be alive
- Formula: `Quorum = (N / 2) + 1`

**Example:**
```
5-node ensemble
Quorum = (5 / 2) + 1 = 3
Can tolerate 2 failures
```

### Leader & Followers

```
Client → Follower 1
         Follower 2 → Leader ← Follower 3
```

**Roles:**
- **Leader**: Processes all **write** requests
- **Followers**: Process **read** requests, forward writes to leader

**Leader Election:**
- Uses **ZAB (ZooKeeper Atomic Broadcast)** protocol
- **Fast leader election** algorithm (< 200ms typical)

---

## ZAB (ZooKeeper Atomic Broadcast)

### Purpose
Ensures **total order** and **atomic delivery** of transactions across the ensemble.

### Two Phases

#### 1. Discovery Phase
- New leader elected
- Synchronizes with followers

#### 2. Broadcast Phase
- Leader processes writes
- Broadcasts changes to followers
- Commits when quorum acknowledges

### Write Flow

```
1. Client sends write to Follower
2. Follower forwards to Leader
3. Leader generates PROPOSAL (transaction)
4. Leader sends PROPOSAL to all Followers
5. Followers acknowledge (ACK)
6. Leader receives quorum of ACKs
7. Leader sends COMMIT
8. All nodes commit transaction
9. Response sent to client
```

**Key Property: Total Order**
- All nodes see transactions in same order
- Achieved via **ZXID (ZooKeeper Transaction ID)**

### ZXID
- 64-bit ID: `[epoch (32 bits)][counter (32 bits)]`
- **Epoch**: Changes with each leader election
- **Counter**: Monotonically increasing within epoch

**Example:**
```
ZXID: 0x100000001
  Epoch: 1 (0x1)
  Counter: 1 (0x00000001)
```

---

## Session Management

### Client-Server Session
- Client establishes session with one server
- **Session ID** + **timeout** (default 10s)
- **Heartbeats** (pings) keep session alive

### Session States
```
NOT_CONNECTED
    ↓
CONNECTING
    ↓
CONNECTED ←→ ASSOCIATING (reconnecting to different server)
    ↓
CLOSED
```

### Session Expiry
- If no heartbeat within timeout → session expires
- **Ephemeral znodes deleted**
- **Watches triggered**

---

## Watches (Notifications)

### One-Time Triggers
- Client sets watch on znode
- ZooKeeper sends **one** notification when znode changes
- Must re-set watch for continuous monitoring

**Example:**
```java
Stat stat = zk.exists("/app/config", true);  // Set watch

// Later, when /app/config changes:
process(WatchedEvent event) {
    if (event.getType() == EventType.NodeDataChanged) {
        // Re-set watch
        zk.exists("/app/config", true);
    }
}
```

### Watch Types
- **Data watches**: `getData()`, `exists()`
- **Child watches**: `getChildren()`

**Events:**
- `NodeDataChanged`
- `NodeChildrenChanged`
- `NodeCreated`
- `NodeDeleted`

---

## Consistency Model

### Sequential Consistency
- **Total order**: All clients see updates in same order
- **Client FIFO**: Client's requests executed in order sent

### Guarantees
1. **Linearizable writes**: All writes totally ordered
2. **Reads may be stale**: Read from local replica (no quorum read)
3. **Sync before read**: Use `sync()` for latest data

**Example:**
```java
// Ensure latest data
zk.sync("/app/config", null, null);
byte[] data = zk.getData("/app/config", false, null);
```

---

## Common Use Cases

### 1. Leader Election

```java
// Each node creates ephemeral sequential znode
String path = zk.create("/election/node-", data, 
                        OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

// Get all children, sort
List<String> children = zk.getChildren("/election", false);
Collections.sort(children);

// Smallest ID is leader
if (path.endsWith(children.get(0))) {
    // I am leader
} else {
    // Watch predecessor (avoid herd effect)
    String predecessor = children.get(myIndex - 1);
    zk.exists("/election/" + predecessor, watcherForPredecessor);
}
```

**Key Point: Watch Predecessor Only** (prevents herd effect)

### 2. Distributed Lock

```java
// Acquire lock
String lockPath = zk.create("/locks/lock-", data, 
                            OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

List<String> locks = zk.getChildren("/locks", false);
Collections.sort(locks);

if (lockPath.endsWith(locks.get(0))) {
    // Lock acquired
} else {
    // Wait on predecessor
}
```

**Fairness:** Guaranteed by sequential znodes

### 3. Service Discovery

```java
// Service registers itself
zk.create("/services/my-service/node-", endpoint,
          OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);

// Client discovers services
List<String> services = zk.getChildren("/services/my-service", watcher);
```

**Ephemeral znodes:** Automatically de-register on crash

### 4. Configuration Management

```java
// Centralized config
zk.create("/config/db-url", "jdbc:mysql://...", 
          OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

// Applications watch for changes
zk.getData("/config/db-url", watcher, null);
```

---

## Performance Characteristics

### Read Throughput
- **High**: Reads served by any follower
- Scales linearly with ensemble size
- ~100K reads/sec per server

### Write Throughput
- **Lower**: All writes go through leader
- Limited by leader capacity
- ~10K-40K writes/sec for ensemble

**Tuning:**
- **More followers** = higher read throughput
- **Faster leader** = higher write throughput
- **Closer quorum** = lower write latency

---

## Failure Scenarios

### Follower Failure
- Leader continues with remaining quorum
- **No downtime** if quorum alive

### Leader Failure
- New leader elected (~200ms)
- Brief write unavailability
- Reads continue on followers

### Network Partition
- Partition with quorum continues
- Partition without quorum becomes read-only

**Example: 5-node cluster splits 2-3**
- 3-node partition: Can read and write
- 2-node partition: Becomes read-only (no quorum)

---

## Tuning & Best Practices

### Session Timeout
```
tickTime = 2000          # Base time unit (2s)
minSessionTimeout = 4000  # 2 × tickTime
maxSessionTimeout = 40000 # 20 × tickTime
```

**Trade-offs:**
- **Short timeout**: Faster failure detection, more heartbeat overhead
- **Long timeout**: Slower failure detection, less overhead

### Data Size
- Keep znode data **< 1KB**
- ZooKeeper is coordination service, not storage
- Large data slows ensemble

### Ensemble Size
- **3 nodes**: 1 failure tolerance
- **5 nodes**: 2 failures tolerance (recommended for production)
- **7 nodes**: 3 failures tolerance (rare, high latency)

### Avoid Watches on Large Child Lists
- `getChildren()` with many children is expensive
- Consider hierarchical structure

---

## Common Pitfalls

### ❌ Using ZooKeeper as Database
- Designed for small metadata (< 1MB per znode)
- Not for large data storage

### ❌ Not Handling Session Expiry
- Ephemeral znodes deleted
- Must recreate on reconnect

### ❌ Herd Effect
- All clients watch same znode
- All wake up on change → thundering herd
- **Solution**: Chain watches (watch predecessor)

### ❌ Blocking in Watch Callback
- Callbacks run in ZooKeeper event thread
- Blocking stalls all callbacks
- **Solution**: Spawn async task

---

## ZAB vs Raft: Detailed Comparison

Both ZAB (ZooKeeper Atomic Broadcast) and Raft solve the same problem: replicated state machine with strong consistency. They share the same core insight (single leader, majority quorum) but differ in design details.

| Dimension | ZAB | Raft |
|-----------|-----|------|
| **Designed for** | ZooKeeper coordination service | General replicated state machines |
| **Leader epochs** | `epoch` (64-bit zxid high bits) | `term` (monotonically increasing) |
| **Log entry ID** | `zxid = <epoch, counter>` | `<term, index>` |
| **Leader election** | Any node with most up-to-date zxid can win | Any node can win; log completeness checked by vote |
| **Recovery phase** | Explicit RECOVERY phase: new leader syncs all followers before accepting writes | New leader sends heartbeats; followers catch up via AppendEntries |
| **Commit rule** | Write committed when majority acknowledge; leader broadcasts COMMIT separately | Entry committed when stored on majority; no separate commit message needed |
| **Causal ordering** | Strict FIFO ordering: every message from a given leader is processed in order | Same in practice (sequential log replication) |
| **Read model** | Reads can be stale (ZooKeeper default); use `sync()` for linearizable read | Reads stale by default; use `ReadIndex` or lease reads for linearizable reads |
| **Multi-master** | No — one active leader | No — one active leader |
| **Implementation** | Java, complex state machine | Go (etcd), Rust (tikv), simpler spec |

**Key ZAB-specific behavior — Recovery Phase**:

When a new leader is elected in ZAB, it enters a RECOVERY phase before accepting any client writes:
1. New leader broadcasts its highest zxid to all followers
2. Followers respond with their own highest zxid
3. Leader sends missing transactions to any follower that's behind
4. Only after majority are synchronized does the leader enter BROADCAST phase (normal operation)

This recovery phase adds latency to leader failover (seconds vs milliseconds for Raft) but ensures no committed transaction is ever lost.

**When ZAB beats Raft**:
- ZooKeeper's hierarchical data model needs causal ordering within a session — ZAB's FIFO guarantee is natural
- ZAB's epoch-based zxid makes it easy to detect if a message is from a stale leader (wrong epoch)

---

## KRaft: Kafka Removes ZooKeeper (KIP-500)

### Why Kafka Used ZooKeeper

Kafka originally used ZooKeeper for:
- **Controller election**: one Kafka broker is the "controller" that manages partition leaders
- **Broker registration**: brokers register as ZooKeeper ephemeral nodes; ZK detects crashes
- **Topic/partition metadata**: topic configs, partition assignments, ISR lists
- **Consumer group coordination** (pre-Kafka 0.9; moved to internal `__consumer_offsets`)

### Why ZooKeeper Became the Bottleneck

At scale (100K+ partitions), ZooKeeper became a bottleneck:
- **Metadata fan-out**: Controller must push all partition changes to all brokers. At 100K partitions, controller restart means sending 100K state updates through ZooKeeper — takes minutes.
- **Two-system ops**: Operators must maintain and monitor both Kafka and ZooKeeper. ZooKeeper has its own JVM, GC tuning, quorum sizing.
- **Scalability ceiling**: ZooKeeper's in-memory data model limits the number of partitions Kafka can handle.

### KRaft: Kafka's Internal Raft (Kafka 3.3+ production, 2.8 early access)

**Architecture change**: eliminate ZooKeeper entirely. A subset of Kafka brokers act as **KRaft controllers** using Raft consensus internally.

```
Before (ZooKeeper mode):
  ZooKeeper Ensemble (3 or 5 nodes, separate cluster)
         │ metadata reads/writes
  Kafka Brokers + 1 active Controller
         │ leader election, ISR updates → ZK

After (KRaft mode):
  KRaft Controllers (3 or 5 from broker pool, integrated Raft)
         │ Raft log for metadata changes
  Kafka Brokers (regular, non-controller)
         │ fetch metadata from KRaft controllers
```

**KRaft metadata log**: All cluster metadata (topic configs, partition assignments, ACLs) is stored in an internal Kafka topic `__cluster_metadata`. Controllers replicate this topic using Raft. Brokers are followers of this log — they subscribe and apply changes locally.

```
KRaft Controller (leader):
  Writes metadata changes to __cluster_metadata (Raft log)
  → majority of KRaft controllers acknowledge
  → brokers tail the log and apply changes

Failover:
  If leader controller fails → KRaft election → new leader in milliseconds
  New leader: already has full metadata log → immediately ready to serve
  (vs ZooKeeper: new controller had to re-read all state from ZK → slow)
```

**Scalability improvement**:
- ZooKeeper mode: ~200K partitions practical limit (controller restart too slow)
- KRaft mode: millions of partitions (metadata stored in log, not ZK in-memory)

**Migration steps** (ZK → KRaft):
1. Upgrade all brokers to Kafka 3.x
2. Run `kafka-storage.sh format` to initialize KRaft metadata directory
3. Start KRaft controllers in "migration mode" — they coexist with ZooKeeper temporarily
4. Dual-write period: both ZK and KRaft controllers active; ZK is source of truth
5. Run migration tool: transfers all ZK metadata to KRaft log
6. Decommission ZooKeeper ensemble
7. KRaft is now sole metadata store

**Interview insight**: "Kafka is removing ZooKeeper because at 100K+ partitions, ZooKeeper's in-memory metadata model became a bottleneck for controller restart time. KRaft stores metadata in a Kafka topic replicated via Raft — Kafka becomes self-managing, brokers subscribe to the metadata log directly, and failover takes milliseconds instead of minutes."

---

## Comparison: ZooKeeper vs etcd

| Feature | ZooKeeper | etcd |
|---------|-----------|------|
| **Protocol** | ZAB | Raft |
| **Language** | Java | Go |
| **API** | Custom (Zookeeper client) | REST + gRPC |
| **Data Model** | Hierarchical (tree) | Flat key-value |
| **Watch** | One-time | Continuous |
| **Use Case** | Coordination | Kubernetes, service mesh |

---

## Interview Questions

**Q: How does ZooKeeper achieve fault tolerance?**
- Quorum-based replication (majority must agree)
- 5-node ensemble tolerates 2 failures
- ZAB protocol ensures consistency across replicas
- Fast leader election (<200ms) on leader failure

**Q: Explain ephemeral znodes and their use case**
- Deleted automatically when client session ends
- Cannot have children
- **Use case**: Leader election (leader znode disappears if leader dies), service discovery (service de-registers on crash)

**Q: What is the herd effect and how do you avoid it?**
- All clients wake up when watched znode changes
- Causes spike in load
- **Solution**: Watch only predecessor in distributed lock/queue scenario
- Only one client wakes up per change

**Q: Why use ZooKeeper instead of a database for coordination?**
- **Specialized**: Built for coordination (leader election, locks, watches)
- **Low latency**: In-memory, optimized for small data
- **Ordering guarantees**: Sequential consistency, total order
- **Failure detection**: Session management, ephemeral znodes
