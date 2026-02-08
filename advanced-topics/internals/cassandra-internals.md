# Cassandra Internals

## Overview
Apache Cassandra is a highly scalable, distributed NoSQL database designed for handling massive amounts of data across multiple nodes with no single point of failure.

## Architecture

### Ring Architecture & Consistent Hashing
- **Virtual Nodes (vnodes)**: Each physical node owns multiple virtual nodes (typically 256)
- **Token Ranges**: Data distributed across the ring based on partition key hash
- **No Master Node**: Fully peer-to-peer architecture
- **Gossip Protocol**: Nodes communicate state using gossip every second

```
Node A (tokens: 0-255)
Node B (tokens: 256-511)
Node C (tokens: 512-767)
→ Data with hash 100 goes to Node A
```

---

## Data Model

### Key Concepts
- **Keyspace**: Top-level namespace (like database in RDBMS)
- **Table (Column Family)**: Collection of rows
- **Partition Key**: Determines which node(s) store the data
- **Clustering Key**: Determines sort order within a partition
- **Primary Key**: Partition Key + Clustering Key

### Example
```sql
CREATE TABLE user_events (
    user_id UUID,           -- Partition Key
    event_time TIMESTAMP,   -- Clustering Key
    event_type TEXT,
    PRIMARY KEY (user_id, event_time)
) WITH CLUSTERING ORDER BY (event_time DESC);
```

**Data Distribution:**
- All events for `user_id=123` stored together on same node(s)
- Within partition, sorted by `event_time` descending
- Enables fast range queries within a partition

---

## Write Path (Why Cassandra is Write-Optimized)

### 1. Commit Log (Append-Only WAL)
- **Sequential disk writes** (extremely fast)
- Ensures durability before acknowledging write
- Append-only, no random I/O

### 2. MemTable (In-Memory)
- Sorted data structure in RAM
- Writes go here after commit log
- Per-table MemTable

### 3. SSTable Flush
- When MemTable reaches threshold (~128MB), flush to disk as **SSTables**
- **Immutable** on-disk files
- Sorted String Table format

### 4. Write Flow
```
Client Write
    ↓
Commit Log (disk, sequential)
    ↓
MemTable (memory)
    ↓ (when full)
SSTable (disk, immutable)
```

**Why Fast?**
- Sequential writes to commit log (no seeks)
- In-memory writes to MemTable
- No read-before-write (unlike B-trees)

---

## Read Path (Optimizations)

### Challenge
Data can be in multiple places:
- MemTable (most recent)
- Multiple SSTables (older data)
- Need to merge results

### Optimizations

#### 1. Bloom Filters
- Probabilistic data structure
- **False positives possible, false negatives impossible**
- Quickly determine "SSTable definitely doesn't have this key"
- Saves disk I/O

```
Query: user_id=123
SSTable1 Bloom Filter: Maybe has it
SSTable2 Bloom Filter: Definitely doesn't have it (skip)
SSTable3 Bloom Filter: Maybe has it
```

#### 2. Partition Summary & Index
- **Partition Summary**: In-memory, sparse index of partition keys
- **Partition Index**: On-disk, full index
- Enables quick seek to partition location

#### 3. Key Cache
- Caches partition key → SSTable offset mapping
- Reduces index lookups

#### 4. Row Cache
- Caches entire rows
- Disabled by default (high memory usage)

### Read Flow
```
1. Check MemTable
2. Check Bloom Filters for each SSTable
3. Check Key Cache
4. Read SSTables (using indexes)
5. Merge results from all sources
6. Return most recent (based on timestamp)
```

---

## Compaction (Background Maintenance)

### Problem
- Writes create many SSTables
- Deletes don't remove data immediately (tombstones)
- Reads get slower as SSTable count grows

### Solution: Compaction Strategies

#### 1. Size-Tiered Compaction (STCS)
- Default for write-heavy workloads
- Merges SSTables of similar size
- **Pros**: Good for time-series, write-heavy
- **Cons**: Reads can be slow (many SSTables), space amplification

#### 2. Leveled Compaction (LCS)
- SSTables organized into levels (like LSM-tree)
- Level 0: Small SSTables
- Level 1+: Fixed size (default 160MB), non-overlapping
- **Pros**: Better read performance, predictable space
- **Cons**: More I/O overhead

#### 3. Time-Window Compaction (TWCS)
- For time-series data with TTL
- Groups SSTables by time window
- Entire window expires together
- **Pros**: Efficient for TTL data, no tombstones
- **Cons**: Only for time-series with TTL

---

## Replication & Consistency

### Replication Factor (RF)
```
RF=3: Each partition stored on 3 nodes
```

### Consistency Levels

| Level | Reads | Writes | Use Case |
|-------|-------|--------|----------|
| **ONE** | 1 node | 1 node | Lowest latency, eventual consistency |
| **QUORUM** | RF/2 + 1 | RF/2 + 1 | Strong consistency (most common) |
| **ALL** | All nodes | All nodes | Strongest, but unavailable if any node down |
| **LOCAL_QUORUM** | Quorum in local DC | Quorum in local DC | Multi-DC strong consistency |

### Tunable Consistency
```
Write at QUORUM + Read at QUORUM = Strong Consistency
Write at ONE + Read at ONE = Eventual Consistency (faster)
```

**Formula for Strong Consistency:**
```
R + W > RF  (where R=read replicas, W=write replicas)
```

---

## Hinted Handoff & Read Repair

### Hinted Handoff
- When a replica node is down during write
- Coordinator stores a "hint" locally
- Replays hint when node comes back
- **Time window**: 3 hours (default)

### Read Repair
- During read, if replicas disagree on data
- **Read Repair Chance**: 10% by default
- Background process to sync replicas

---

## Anti-Entropy Repair
- **Full repair** across nodes
- Compares Merkle trees between replicas
- Repairs inconsistencies
- **Should run weekly** for tables without frequent reads

---

## Tombstones & Deletions

### Problem
Cassandra is immutable (append-only)

### Solution
- Deletes create **tombstones** (markers)
- Tombstones stored in SSTables
- Compaction removes tombstones after `gc_grace_seconds` (default 10 days)

### Why 10 Days?
- Allow time for failed nodes to recover
- Prevent deleted data from "resurrecting"

### Tombstone Overload
- Too many tombstones slow reads
- Monitor with `nodetool cfstats`

---

## Performance Tuning

### Best Practices

#### 1. Data Modeling
- **Denormalize**: No joins, duplicate data
- **Model by query**: Design tables for specific queries
- **Partition size**: Keep under 100MB
- **Avoid wide rows**: Don't have millions of columns

#### 2. Hardware
- **SSDs**: Essential for good performance
- **RAM**: More RAM = larger MemTable + caches
- **Network**: Low-latency network for multi-node

#### 3. Configuration
- `concurrent_reads`: 32 (default)
- `concurrent_writes`: 32 (default)
- `memtable_flush_writers`: 2 per disk

---

## Common Pitfalls

### ❌ Using SELECT * with Large Partitions
- Can timeout or OOM
- Use pagination (LIMIT or paging state)

### ❌ Allowing Unbounded Growth
- Partition size must be bounded
- Use TTL or bucketing strategies

### ❌ Not Running Repairs
- Data can drift across replicas
- Run `nodetool repair` regularly

### ❌ Secondary Indexes
- **Bad performance** in distributed system
- Creates a "partition per value" internally
- Use materialized views or denormalization instead

---

## Interview Questions

**Q: Why is Cassandra fast for writes?**
- Sequential append to commit log (no random I/O)
- In-memory MemTable writes
- No read-before-write (no B-tree updates)
- LSM-tree architecture optimized for writes

**Q: How does Cassandra achieve high availability?**
- Replication Factor (RF=3 typical)
- No single master (peer-to-peer)
- Tunable consistency (can tolerate node failures)
- Hinted handoff for temporary failures

**Q: Explain quorum consistency**
- Read/Write to majority of replicas
- Formula: `QUORUM = RF/2 + 1`
- Prevents split-brain scenarios
- Balances consistency and availability

**Q: What are the trade-offs of using Cassandra?**
- **Pros**: Write-optimized, linear scalability, no SPOF
- **Cons**: Eventual consistency (unless using QUORUM), no joins, data modeling complexity, tombstone accumulation
