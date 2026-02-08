# Databases - System Design Guide

> **For SDE-3 Interview Preparation**  
> Comprehensive guide to database concepts, trade-offs, and production considerations

## Table of Contents

1. [Introduction to Databases](#introduction-to-databases)
2. [SQL vs NoSQL](#sql-vs-nosql)
3. [Database Models](#database-models)
4. [ACID vs BASE](#acid-vs-base)
5. [CAP Theorem](#cap-theorem)
6. [Database Replication](#database-replication)
7. [Database Sharding](#database-sharding)
8. [Indexes](#indexes)
9. [Normalization vs Denormalization](#normalization-vs-denormalization)
10. [Transactions & Isolation Levels](#transactions--isolation-levels)
11. [Distributed Transactions](#distributed-transactions)
12. [Database Selection Guide](#database-selection-guide)

---

## Introduction to Databases

A **database** is an organized collection of structured information, or data, typically stored electronically in a computer system. A **Database Management System (DBMS)** controls the database and provides an interface for users and applications to interact with it.

### Why Databases Matter in System Design

- **Data Persistence**: Store data beyond application lifetime
- **Concurrency Control**: Handle multiple users accessing data simultaneously
- **Data Integrity**: Enforce constraints and validation rules
- **Query Optimization**: Efficient data retrieval and manipulation
- **Backup & Recovery**: Protect against data loss
- **Scalability**: Handle growing data volumes and user load

---

## SQL vs NoSQL

### SQL Databases (Relational)

**Characteristics:**
- **Structured Schema**: Predefined tables with fixed columns
- **ACID Compliance**: Strong consistency guarantees
- **Relational**: Tables linked via foreign keys
- **SQL Language**: Standardized query language

**Best For:**
- Complex queries with JOINs
- Transactions requiring ACID guarantees (banking, e-commerce)
- Structured, predictable data
- Applications where data integrity is critical

**Examples:**
- **PostgreSQL**: Advanced features, JSON support, extensions
- **MySQL**: Popular, fast reads, good for web applications
- **Oracle**: Enterprise-grade, high performance
- **SQL Server**: Microsoft ecosystem, strong tooling

**Scaling:**
- **Vertical Scaling**: Add more CPU/RAM to server (easier but has limits)
- **Read Replicas**: Distribute reads across multiple copies
- **Sharding**: Partition data across multiple databases (complex)

### NoSQL Databases (Non-Relational)

**Characteristics:**
- **Flexible Schema**: Schema-less or dynamic schema
- **BASE Model**: Prioritizes availability over consistency
- **Horizontal Scaling**: Scale out by adding more nodes
- **Specialized**: Different types for different use cases

### NoSQL Types

#### 1. Document Databases

**Use Case**: Flexible, nested, hierarchical data

**Examples:**
-**MongoDB**: JSON-like documents, rich query language
- **Couchbase**: Memory-first, high performance
- **DynamoDB**: AWS managed, predictable performance

**Data Model:**
```json
{
  "_id": "user_123",
  "name": "John Doe",
  "email": "john@example.com",
  "addresses": [
    {
      "type": "home",
      "street": "123 Main St",
      "city": "San Francisco"
    }
  ],
  "preferences": {
    "theme": "dark",
    "notifications": true
  }
}
```

**When to Use:**
- Content management systems
- User profiles
- Product catalogs
- Real-time analytics

#### 2. Key-Value Stores

**Use Case**: Simple lookups, caching, session storage

**Examples:**
- **Redis**: In-memory, blazing fast, rich data structures
- **Memcached**: Simple caching
- **DynamoDB**: Also works as key-value store

**Data Model:**
```
user:123 → {"name": "John", "email": "john@example.com"}
session:abc → {"user_id": 123, "expires": "2026-01-01"}
```

**When to Use:**
- Caching layer
- Session management
- Real-time analytics
- Leaderboards (Redis sorted sets)

#### 3. Column-Family Stores

**Use Case**: Time-series data, analytics, high write throughput

**Examples:**
- **Cassandra**: Distributed, highly available, tunable consistency
- **HBase**: Hadoop ecosystem, billions of rows
- **ScyllaDB**: Cassandra-compatible, C++ rewrite

**Data Model:**
```
Row Key: user_123
  ├── profile:name = "John Doe"
  ├── profile:email = "john@example.com"
  ├── activity:2026-01-01 = "logged_in"
  └── activity:2026-01-02 = "updated_profile"
```

**When to Use:**
- Time-series data (sensor data, logs)
- Write-heavy workloads
- Event logging
- Analytics

#### 4. Graph Databases

**Use Case**: Highly connected data, relationship-heavy queries

**Examples:**
- **Neo4j**: Native graph, Cypher query language
- **Amazon Neptune**: AWS managed
- **JanusGraph**: Distributed graph database

**Data Model:**
```
Nodes: (Person:John), (Person:Jane), (Movie:Inception)
Relationships: (:John)-[:KNOWS]->(:Jane)
              (:John)-[:WATCHED]->(:Inception)
```

**When to Use:**
- Social networks (friend recommendations)
- Fraud detection
- Knowledge graphs
- Recommendation engines

---

## SQL vs NoSQL Decision Matrix

| Factor | SQL | NoSQL |
|--------|-----|-------|
| **Schema** | Fixed, predefined | Flexible, dynamic |
| **Scalability** | Vertical (scale up) | Horizontal (scale out) |
| **Transactions** | ACID, strong | BASE, eventual consistency |
| **Queries** | Complex JOINs supported | Limited JOIN support |
| **Data Structure** | Structured, relational | Flexible (JSON, key-value, graph) |
| **Consistency** | Immediate | Eventual (tunable) |
| **Use Case** | Finance, ERP, OLTP | Big data, real-time, high scale |

### When to Use SQL

✅ **ACID transactions required** (banking, e-commerce checkout)  
✅ **Complex queries with JOINs** (reporting, analytics)  
✅ **Structured, predictable data** (user management, inventory)  
✅ **Data integrity critical** (financial records)  
✅ **Moderate scale** (millions of rows, not billions)

### When to Use NoSQL

✅ **Massive scale** (billions of records, petabytes of data)  
✅ **High write throughput** (logging, time-series, IoT)  
✅ **Flexible schema** (evolving data models, rapid iteration)  
✅ **Horizontal scaling needed** (distributed across many nodes)  
✅ **Availability over consistency** (social media feeds, recommendation engines)

### Hybrid Approach (Polyglot Persistence)

Many systems use **both SQL and NoSQL** for different components:

```
E-commerce System:
├── PostgreSQL: Orders, payments, inventory (ACID)
├── MongoDB: Product catalog, user reviews (flexibility)
├── Redis: Shopping cart, session data (speed)
├── Elasticsearch: Product search (full-text search)
└── Cassandra: User activity logs (high write volume)
```

---

## ACID vs BASE

### ACID (SQL Databases)

**Atomicity**: All operations in a transaction succeed or all fail
```sql
BEGIN TRANSACTION;
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT; -- Both succeed or both fail
```

**Consistency**: Database moves from one valid state to another
- Constraints enforced (foreign keys, unique, check)
- Invariants maintained

**Isolation**: Concurrent transactions don't interfere
```
Transaction A: READ(x) → WRITE(x)
Transaction B: READ(x) → WRITE(x)
Result: Serializable (as if ran sequentially)
```

**Durability**: Committed data is never lost
- Writes persisted to disk
- Survives crashes, power failures

### BASE (NoSQL Databases)

**Basically Available**: System appears to work most of the time
- Partial failures tolerated
- Prioritize availability

**Soft state**: State may change over time, even without input
- Eventual consistency allows temporary inconsistency

**Eventually consistent**: System will converge to consistency given time
- No guarantees on when
- Reads may return stale data temporarily

**Example: DynamoDB Write**
```
1. Write to Node A → Success (201 Created)
2. Replicate to Node B → In progress
3. Client reads from Node B → May get old value
4. After replication completes → Consistent
```

---

## CAP Theorem

**CAP Theorem**: In a distributed system, you can only guarantee 2 out of 3:

- **C**onsistency: Every read receives the most recent write
- **A**vailability: Every request receives a response (success or failure)
- **P**artition Tolerance: System continues despite network partitions

### The Reality of CAP

**Partition tolerance is mandatory** in distributed systems (networks fail), so the real choice is:

```
During a network partition:
├── CP (Consistency + Partition Tolerance)
│   → Reject writes to maintain consistency
│   → Examples: MongoDB (default), HBase, Redis (cluster mode)
│
└── AP (Availability + Partition Tolerance)
    → Accept writes, resolve conflicts later
    → Examples: Cassandra, DynamoDB, Riak
```

### CAP in Practice

| Database | CAP | Consistency Model |
|----------|-----|-------------------|
| **PostgreSQL** | CA | Strong (single node) |
| **MongoDB** | CP | Strong (leader-based) |
| **Cassandra** | AP | Tunable (eventual → strong) |
| **DynamoDB** | AP | Tunable (eventual → strong) |
| **Spanner** | CA* | External consistency (TrueTime) |

*Spanner achieves CA-like properties using atomic clocks and TrueTime

### PACELC Theorem (Extended CAP)

**PACELC**: If **P**artition, choose **A** or **C**, **E**lse (no partition), choose **L**atency or **C**onsistency

```
Normal operation (no partition):
├── Low Latency: Eventual consistency (Cassandra, DynamoDB)
└── Strong Consistency: Higher latency (Spanner, MongoDB)
```

---

## Database Replication

**Replication**: Keeping copies of data on multiple nodes for redundancy and availability

### Leader-Follower (Master-Slave)

**How it works:**
1. **Leader** handles all writes
2. **Followers** replicate from leader
3. Reads can go to followers (scale reads)

**Synchronous Replication:**
```
Client → Write to Leader
Leader → Wait for ALL followers to confirm
Leader → Acknowledge to client
```
✅ Strong consistency  
❌ Higher latency (wait for followers)

**Asynchronous Replication:**
```
Client → Write to Leader
Leader → Acknowledge immediately
Leader → Replicate to followers (background)
```
✅ Low latency  
❌ Risk of data loss if leader fails before replication

**Use Cases:**
- PostgreSQL, MySQL, MongoDB (default)
- Read-heavy workloads (scale reads)

**Challenges:**
- Leader failure → Need failover
- Replication lag → Stale reads on followers

### Multi-Leader Replication

**How it works:**
- Multiple nodes accept writes
- Changes replicated to all other leaders

**Use Cases:**
- Multi-datacenter setups
- Offline-first applications (mobile apps)

**Challenges:**
- Conflict resolution (two writes to same key)
- Complexity

### Leaderless Replication

**How it works:**
- Client writes to multiple nodes (quorum)
- No designated leader

**Example: Cassandra, DynamoDB**
```
Write with W=2, R=2, N=3:
Client → Write to 2 out of 3 nodes → Success
Client → Read from 2 nodes → Get latest value
```

**Quorum:**
- **W** = Write quorum (nodes that must acknowledge write)
- **R** = Read quorum (nodes to read from)
- **N** = Total replicas
- **Rule**: W + R > N → Strong consistency

---

---

## Database Sharding

**Sharding** is partitioning data across multiple databases (nodes) to scale beyond the limits of a single server.

### Sharding Strategies

#### 1. Key-Based Sharding (Hash Based)
- `Shard_ID = hash(Key) % Number_Of_Shards`
- **Pros**: Even data distribution.
- **Cons**: **Resharding is painful**. If you add nodes, `% Number_Of_Shards` changes, moving almost ALL data.
- **Solution**: Use **Consistent Hashing** (Reduces movement to `K/N`).

#### 2. Range-Based Sharding
- `Shard 1: UserIds 1-1,000,000`
- `Shard 2: UserIds 1,000,001-2,000,000`
- **Pros**: Easy to query ranges.
- **Cons**: **Hotspots**. If recent users are most active, Shard 2 gets all traffic while Shard 1 is idle.

#### 3. Directory-Based Sharding
- Lookup table: `Key -> Shard_ID`
- **Pros**: Flexible (can move individual keys).
- **Cons**: Lookup table becomes single point of failure and bottleneck.

### Challenges of Sharding
1.  **Joins across shards**: Expensive or impossible. Stick to denormalization.
2.  **Transactions**: Distributed transactions are slow (2PC).
3.  **Resharding**: Data migration is complex (Double writes needed).

---

## Indexes

**Indexes** speed up read queries at the cost of slower writes and increased storage.

### 1. B-Tree / B+ Tree (Default in SQL)
- **Structure**: Balanced tree. Data sorted.
- **Complexity**: $O(\log N)$ for search, insert, delete.
- **Best For**: Read-heavy workloads, range queries (`WHERE age > 20`).
- **Used by**: PostgreSQL, MySQL (InnoDB), MongoDB.

### 2. LSM Tree (Log-Structured Merge Tree)
- **Structure**:
    - **MemTable**: In-memory sorted buffer.
    - **SSTable**: Immutable on-disk sorted files.
    - Periodically compacted.
- **Performance**:
    - **Writes**: $O(1)$ (Append only). Extremely fast.
    - **Reads**: Can be slower (check MemTable + multiple SSTables). Bloom filter used to optimize.
- **Best For**: Write-heavy workloads.
- **Used by**: Cassandra, RocksDB, Kafka.

---

## Normalization vs Denormalization

### Normalization (SQL)
- **Goal**: Eliminate redundancy.
- **1NF, 2NF, 3NF**: Rules to split tables.
- **Pros**: Data consistency, smaller size.
- **Cons**: Many JOINs (Slow reads).

### Denormalization (NoSQL)
- **Goal**: Optimize read performance.
- **Strategy**: Duplicate data. Store `AuthorName` inside `Book` table.
- **Pros**: No JOINs (Fast reads).
- **Cons**: Data inconsistency (update `AuthorName` needs updates in 100 places).

---

## Transactions & Isolation Levels

**ACID** transactions prevent concurrency bugs. **Isolation Levels** trade off consistency for performance.

| Isolation Level | Dirty Read? | Non-Repeatable Read? | Phantom Read? | Perf |
|-----------------|-------------|----------------------|---------------|------|
| **Read Uncommitted** | ✅ Yes | ✅ Yes | ✅ Yes | 🚀 Fastest |
| **Read Committed** | ❌ No | ✅ Yes | ✅ Yes | ⚡ Fast |
| **Repeatable Read** | ❌ No | ❌ No | ✅ Yes (Usually) | 🐢 Slow |
| **Serializable** | ❌ No | ❌ No | ❌ No | 🐌 Slowest |

1.  **Dirty Read**: Reading uncommitted data (that might be rolled back).
2.  **Non-Repeatable Read**: Re-reading a row gets different data (someone updated it).
3.  **Phantom Read**: Re-running a range query gets different rows (someone inserted new row).

---

## Database Selection Guide

| Requirement | Recommended DB | Why? |
|-------------|----------------|------|
| **Financial / Payments** | **PostgreSQL / MySQL** | ACID, Strong Consistency. |
| **Social Graph** | **Neo4j / Amazon Neptune** | Graph traversals, Friends of Friends. |
| **Product Catalog** | **MongoDB / DocumentDB** | Flexible schema, JSON, Read heavy. |
| **High Velocity Logs** | **Cassandra / ScyllaDB** | Write heavy (LSM Tree), Time series. |
| **Real-time Leaderboard** | **Redis** | In-memory Sorted Sets. |
| **Full Text Search** | **Elasticsearch** | Inverted Index. |
