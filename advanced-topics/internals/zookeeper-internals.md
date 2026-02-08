# ZooKeeper Internals

## Overview
Apache ZooKeeper is a distributed coordination service used for maintaining configuration, naming, synchronization, and group services in distributed systems.

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
