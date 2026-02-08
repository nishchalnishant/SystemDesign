# Designing Data-Intensive Applications (DDIA) - Summary

> **The "Bible" of System Design by Martin Kleppmann**
> **Must-read for SDE-3+ roles**

## Part I: Foundations of Data Systems

### Chapter 1: Reliable, Scalable, and Maintainable Applications

**Reliability:** System continues to work correctly even when things go wrong (hardware faults, software bugs, human error).
- **Fault-tolerant**: Anticipate faults and cope with them.
- **Resilience**: Ability to recover from failure.

**Scalability:** Ability to cope with increased load.
- **Load parameters**: Requests per second, read/write ratio, concurrent users.
- **Latency vs Response Time**: Latency is duration of request waiting; Response time involves service time + network delay.
- **Percentiles**: P50, P95, **P99** (crucial for tail latency).

**Maintainability:**
- **Operability**: Easy for ops team to keep running.
- **Simplicity**: Easy for new engineers to understand (clean abstractions).
- **Evolvability**: Easy to make changes in future (agile).

### Chapter 2: Data Models and Query Languages

**Relational Model (SQL):**
- Data organized in relations (tables).
- Default for transactional apps (OLTP).
- **Schema-on-write**: Strict structure enforced.

**Document Model (NoSQL):**
- JSON-like documents (MongoDB).
- Better for objects that are self-contained.
- **Schema-on-read**: Structure interpretation happens at application level.
- **Locality**: Fetch entire document at once (good for 1-to-many relationships).

**Graph Model (Neo4j):**
- Nodes and edges.
- Best for many-to-many relationships (social networks).

### Chapter 3: Storage and Retrieval

**Log-Structured Merge-Tree (LSM-Tree):**
- Used by: Cassandra, HBase, LevelDB.
- **Writes**: Append to MemTable (in-memory sorted tree). When full, flush to SSTable on disk.
- **Reads**: Check MemTable -> Check recent SSTables -> Check older SSTables.
- **Pros**: High write throughput (sequential writes).
- **Cons**: Compaction overhead, read amplification.

**B-Trees:**
- Used by: MySQL, PostgreSQL.
- Breakdown into pages (blocks).
- **Writes**: Overwrite page in place (random I/O).
- **Pros**: Good for reads (predictable performance).
- **Cons**: Write amplification (write entire page even for small update), fragmentation.

### Chapter 4: Encoding and Evolution

**Formats:**
- **JSON/XML**: Human readable, verbose, no schema enforcement.
- **Binary (Protobuf, Thrift, Avro)**: Compact, schema-based, backward/forward compatibility.

**Backward Compatibility**: New code can read old data.
**Forward Compatibility**: Old code can read new data.

---

## Part II: Distributed Data

### Chapter 5: Replication

**Leaders and Followers:**
- **Synchronous Replication**: Leader waits for follower ack. Durable but slow (latency).
- **Asynchronous Replication**: Leader confirms write immediately. Fast but potential data loss if leader fails.
- **Semi-Synchronous**: Wait for 1 replica, others async.

**Replication Lag Problems:**
- **Read-after-write consistency**: User posts comment, refresh page, comment missing. Fix: Read your own writes from leader.
- **Monotonic reads**: User reads updated data, refresh, sees old data (replica lag). Fix: Sticker session (user always reads from same replica).
- **Consistent prefix reads**: Causal dependencies violated. Fix: Causal dependency tracking.

### Chapter 6: Partitioning (Sharding)

**Strategies:**
- **Key Range**: Keys sorted (e.g., A-F, G-M). Good for range scans, bat for hotspots.
- **Hash of Key**: Uniform distribution. Good for hotspots, bad for range scans.

**Rebalancing:**
- **Fixed number of partitions**: Create 1000 partitions, assign to nodes. Move entire partition when adding node. (Best approach).
- **Dynamic partitioning**: Split partition when it gets too big (HBase).

### Chapter 7: Transactions

**ACID:**
- **Atomicity**: All or nothing.
- **Consistency**: Invariants preserved (app defined).
- **Isolation**: Concurrent transactions don't interfere.
- **Durability**: Committed data is saved.

**Isolation Levels:**
- **Read Uncommitted**: Dirty reads allowed.
- **Read Committed**: No dirty reads (default in many DBs).
- **Snapshot Isolation (MVCC)**: Readers don't block writers, writers don't block readers. Repeatable Read.
- **Serializable**: Strictest. Executed as if serial. (2PL or Serializable Snapshot Isolation).

### Chapter 8: The Trouble with Distributed Systems

**Ordering and Clocks:**
- **Physical Clocks**: Device time (NTP can drift). Unreliable for ordering.
- **Logical Clocks**: Lamport timestamps, Vector clocks. Capture causality.

**Byzantine Faults**: Nodes may lie/cheat (uncommon in trusted datacenters).

### Chapter 9: Consistency and Consensus

**Linearizability (Strong Consistency):**
- System behaves as if there is only ONE copy of data.
- **Head-of-line blocking**: Slow.

**CAP Theorem:**
- **Consistency**: Linearizability.
- **Availability**: Every request receives a response (non-error).
- **Partition Tolerance**: System continues despite network failure.
- **Trade-off**: In partition (P), choose C (unavailable) or A (inconsistent).

**Consensus Algorithms:**
- **Paxos/Raft**: Agree on value.
- **2-Phase Commit (2PC)**: Atomic commit across nodes (blocking).

---

## Part III: Derived Data

### Chapter 10: Batch Processing

**MapReduce:**
- **Map**: Extract key-value.
- **Shuffle**: Sort by key (distributed sort).
- **Reduce**: Aggregate values for key.

**Unix Philosophy**: Small tools, composable, pipes. MapReduce is "Unix pipes distributed".

### Chapter 11: Stream Processing

**Event Sourcing:**
- Store immutable log of events (facts), not current state.
- Derive state from event log.

**Processing Guarantees:**
- **At-most-once**: Fire and forget.
- **At-least-once**: Retry on failure (idempotency key needed).
- **Exactly-once**: Atomic update of state + offset.

### Chapter 12: The Future of Data Systems

**Data Integration:**
- Integrating disparate systems (DB, Search Index, Cache, Analytics).
- Change Data Capture (CDC): DB log -> Stream -> Downstream systems.
- **Unbundling the Database**: Using specialized tools for each part (Kafka for log, Elasticsearch for search, Redis for cache) tied together via streams.

## Key Takeaways for Interviews

1. **Trade-offs everywhere**: Consistency vs Availability, Latency vs Durability.
2. **There is no magic**: Every system has limitations.
3. **Partitioning + Replication**: The core of scalability.
4. **Asynchronous/Event-driven**: Decouples systems and improves resilience.
