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

## File Mindmap

```
Databases
├── Why It Exists
│   ├── Problem → two concurrent transfers from $250 account both read $250, both write, final balance wrong
│   └── Forces → filesystem has no concept of atomicity or isolation; concurrent writes corrupt state
├── SQL vs NoSQL
│   ├── SQL (relational) → structured schema; ACID; JOINs; strong consistency; vertical scaling primary
│   └── NoSQL → flexible schema; horizontal scale; eventual consistency; trade relational power for throughput
├── ACID vs BASE
│   ├── ACID → Atomicity, Consistency, Isolation, Durability; required for financial/transactional systems
│   └── BASE → Basically Available, Soft state, Eventually consistent; trade consistency for availability/scale
├── CAP Theorem
│   ├── Consistency → every read sees latest write
│   ├── Availability → every request gets a response (may not be latest)
│   ├── Partition Tolerance → system works despite network splits
│   └── Pick 2: CP (HBase, ZooKeeper) or AP (Cassandra, DynamoDB) — CA only possible without partitions
├── Database Models
│   ├── Relational (PostgreSQL, MySQL) → tables + FKs; ACID; best for structured, relational data
│   ├── Document (MongoDB) → JSON docs; flexible schema; no joins; best for hierarchical/polymorphic data
│   ├── Key-Value (Redis, DynamoDB) → O(1) lookup; no query; best for sessions, caching, counters
│   ├── Wide-column (Cassandra) → row key + dynamic columns; time-series; write-heavy workloads
│   └── Graph (Neo4j) → nodes + edges; traversal queries; social networks, recommendations
├── Indexes
│   ├── B-tree index → sorted; range queries; default for most DBs; O(log n) lookup
│   ├── Hash index → exact match only; O(1); no range support
│   ├── Composite index → multi-column; column order matters; leftmost prefix rule
│   └── Trade-off → index speeds reads; slows writes; consumes storage; don't index everything
├── Normalization vs Denormalization
│   ├── Normalization → eliminate redundancy; update one place; more JOINs; slower reads
│   └── Denormalization → duplicate data for read speed; fewer JOINs; stale copies on write
├── Sharding → horizontal partition across nodes; shard key choice is critical; enables write scaling
├── Replication → copies across nodes; primary-replica (read scale); multi-primary (write availability)
├── Failure Modes
│   ├── N+1 query → ORM loads list then fetches related N times → use JOIN or eager load
│   └── Missing index → full table scan at scale → EXPLAIN plan; add selective index
├── Real-World Usage
│   ├── PostgreSQL → financial systems, Stripe; ACID critical; JSONB for flexible columns
│   ├── Cassandra → Uber, Netflix; time-series writes; AP; wide-column; consistent hashing
│   └── DynamoDB → Amazon; single-digit ms; KV + document; serverless; AP by default
└── Interview Angles
    ├── "SQL vs NoSQL — how do you choose?" → consistency + relational needs → SQL; scale + flexibility → NoSQL
    ├── "Explain CAP theorem with a real example" → Cassandra: AP; writes succeed during partition, may diverge
    ├── "What is an index and what's the cost?" → B-tree for reads; write overhead; storage cost; over-indexing
    └── Follow-up: "How does a database guarantee ACID across a crash?" → WAL; write-ahead log; redo on restart
```

---

## Introduction to Databases

**Question**: Two users simultaneously try to transfer money from the same account — one for $100, one for $200. The balance is $250. Both reads see $250. Both subtract their amounts. Both writes succeed. The balance is now $50 when it should be -$50 or a rejection. Your file system happily wrote both values. What just happened, and what primitive do you need to prevent it?

**Physical constraint**: CPUs execute instructions in parallel across threads. Any read-then-write operation that isn't atomic can be interleaved with another read-then-write on the same data. On a single machine, this is a race condition. Across multiple machines sharing a network, the problem multiplies: writes travel at ~0.5ms within a datacenter, so two writes that appear sequential to humans can be effectively simultaneous to the machines.

**Minimal solution**: Wrap every read-write sequence in a lock. One operation holds the lock; others wait. This works on a single machine and prevents corruption. It breaks under concurrent load (all writers queue behind the lock) and completely fails across multiple machines (locks don't span processes without coordination overhead).

**Production generalization**: A DBMS is a piece of software that solves these problems at scale — it provides concurrency control (transactions, isolation levels), data integrity (constraints, foreign keys), query optimization (indexes, execution plans), and durability (WAL, crash recovery). The choice of which DBMS to use is driven entirely by the access patterns and consistency requirements of your system. Choosing wrong is the most expensive architectural mistake to undo.

### Why Databases Matter in System Design

- **Data Persistence**: Store data beyond application lifetime
- **Concurrency Control**: Handle multiple users accessing data simultaneously
- **Data Integrity**: Enforce constraints and validation rules
- **Query Optimization**: Efficient data retrieval and manipulation
- **Backup & Recovery**: Protect against data loss
- **Scalability**: Handle growing data volumes and user load

---

## SQL vs NoSQL

**Question**: You need to store user orders, each containing a list of items, and each item having a product reference with its own attributes. In SQL, this is 3 tables and a JOIN query. In MongoDB, this is one document. The MongoDB query is faster to write and faster to read. So why would you ever use SQL?

**Physical constraint**: A relational database enforces referential integrity — a foreign key constraint means the database verifies at write time that the referenced row exists. This check requires a read of the referenced table on every write. At high write throughput, those integrity checks add latency. NoSQL databases drop these constraints entirely, gaining write speed at the cost of the database never verifying that your data is internally consistent — that responsibility shifts entirely to the application.

**Minimal solution**: Use SQL for everything. The relational model handles arbitrary queries, joins, and transactions. This breaks when: schema changes require locking large tables (ALTER TABLE on 500M rows is dangerous), write throughput exceeds a single primary's capacity, or data is naturally hierarchical/polymorphic and doesn't map cleanly to tables.

**Production generalization**: SQL and NoSQL are not better or worse — they make different physical tradeoffs. SQL: schema enforced by the database, ACID transactions, arbitrary JOINs, vertical scaling. NoSQL: schema enforced by the application, BASE model, limited joins, horizontal scaling. The access pattern of your system determines which tradeoff is correct for each component.

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

---

## Database Models

### 1. Document Databases

**Question**: Your product catalog has 50 different product types. Each type has different attributes — a book has ISBN, author, pages; a TV has screen size, refresh rate, HDR support. In SQL, you'd need 50 tables or a generic key-value attribute table that makes querying a nightmare. What model fits naturally?

**Physical constraint**: A relational schema requires every row in a table to have the same columns. Sparse data (most columns NULL for most rows) wastes storage and makes queries awkward. Polymorphic data with fundamentally different shapes per record doesn't map cleanly to a fixed schema without either many NULLs or schema proliferation.

**Minimal solution**: Store each entity as a self-contained JSON document where each document has exactly the fields it needs and no more. Lookups by primary key are a single document fetch — no joins. This breaks when you need cross-document queries (find all products where `author = 'X'` across millions of documents with varying schemas) without proper indexing.

**Production generalization**: Document databases (MongoDB, DynamoDB, Couchbase) store entities as flexible JSON/BSON documents. Schema is enforced at the application layer, not the database layer. Rich query languages allow indexing and querying into nested fields. Use for: content management, user profiles, product catalogs, any entity with a variable and evolving shape.

> **Analogy:** Think of a document DB as a folder of JSON envelopes. Each envelope holds everything about one thing (a user, a product). You can open any envelope and find all the info without cross-referencing another folder.

**Use Case**: Flexible, nested, hierarchical data

**Examples:**
- **MongoDB**: JSON-like documents, rich query language
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

---

### 2. Key-Value Stores

**Question**: You need to look up a user's session data on every API request — 100,000 times per second. The session is a small blob: user ID, roles, expiry. A database query takes 5ms. At 100,000 requests/sec, that's 100,000 parallel DB queries. Your DB has 200 max connections. What breaks, and what do you reach for?

**Physical constraint**: A relational database connection involves TCP overhead, query parsing, query planning, lock acquisition, and disk I/O. Even a trivially simple `SELECT * FROM sessions WHERE id = ?` takes 1–5ms end-to-end. At 100,000 requests/sec all needing this lookup, you need 100–500 concurrent DB connections just for sessions — saturating most database connection pools.

**Minimal solution**: Store the session blob in RAM, keyed by session ID. A RAM lookup takes ~100ns. At 100,000 lookups/second, that's 10ms of total CPU time — trivial. A hash map in RAM is the minimal solution. It breaks when the process restarts (all sessions lost) or when you have multiple app servers (each has a different in-process map).

**Production generalization**: Key-value stores (Redis, Memcached, DynamoDB) are distributed RAM-backed hash maps accessible over the network. The network adds ~0.5ms but makes the store shared across all app instances. Redis adds persistence (optional), data structure richness (sorted sets for leaderboards, lists for queues), and pub/sub. Use for: caching, session storage, rate limit counters, leaderboards.

> **Analogy:** A coat-check counter. You hand in your coat, get a ticket number (the key). To retrieve it, you hand back the ticket — no queries, no searching, just instant retrieval. Blazing fast, but you can only look up by ticket number.

**Use Case**: Simple lookups, caching, session storage

**Examples:**
- **Redis**: In-memory, blazing fast, rich data structures
- **Memcached**: Simple caching
- **DynamoDB**: Also works as key-value store

**Data Model:**
```
user:123    → {"name": "John", "email": "john@example.com"}
session:abc → {"user_id": 123, "expires": "2026-01-01"}
```

**When to Use:**
- Caching layer
- Session management
- Real-time analytics
- Leaderboards (Redis sorted sets)

---

### 3. Column-Family Stores

**Question**: You're ingesting 500,000 IoT sensor readings per second. Each reading has: sensor ID, timestamp, temperature, humidity. Your access pattern is always: "give me all readings for sensor X between time T1 and T2." A relational DB primary can handle ~50,000 writes/second. You need 10× that throughput, and the data grows indefinitely. What data structure makes writes this fast?

**Physical constraint**: A relational database's B-tree index requires in-place updates — when a new row is inserted, the index structure is modified at its sorted position, potentially causing page splits and random I/O. At 500,000 writes/second, the I/O required for B-tree maintenance overwhelms the disk.

**Minimal solution**: Stop updating in place. Append every write to the end of a log (sequential I/O is 10–100× faster than random I/O). Reads must scan the log. This is fast to write but slow to read. An LSM tree is the formalization: buffer writes in RAM (MemTable), flush sorted batches to disk (SSTables), compact SSTables in the background to make reads faster over time.

**Production generalization**: Column-family stores (Cassandra, HBase, ScyllaDB) use LSM trees to achieve write throughput that B-tree databases cannot match. Data is organized by a partition key (sensor ID) and sorted by a clustering key (timestamp), making range scans over time extremely efficient. Use for: time-series data, event logs, IoT, write-heavy analytics.

> **Analogy:** A spreadsheet where each row can have completely different columns. Row 1 might have 3 columns, Row 2 might have 300. Columns are grouped into "families" (like tabs in a spreadsheet), and you only read the families you need — making wide-row analytics extremely fast.

**Use Case**: Time-series data, analytics, high write throughput

**Examples:**
- **Cassandra**: Distributed, highly available, tunable consistency
- **HBase**: Hadoop ecosystem, billions of rows
- **ScyllaDB**: Cassandra-compatible, C++ rewrite

**Data Model:**
```
Row Key: user_123
  ├── profile:name  = "John Doe"
  ├── profile:email = "john@example.com"
  ├── activity:2026-01-01 = "logged_in"
  └── activity:2026-01-02 = "updated_profile"
```

**When to Use:**
- Time-series data (sensor data, logs)
- Write-heavy workloads
- Event logging
- Analytics

---

### 4. Graph Databases

**Question**: "Find all people within 3 degrees of connection from user Alice." In SQL, this is 3 recursive self-JOINs on a `friendships` table with 1 billion rows. Each join multiplies the intermediate result set. The query plan explodes. Execution time: minutes. At Twitter's scale, this query never finishes. What data structure makes multi-hop traversals fast?

**Physical constraint**: A relational JOIN reads both tables and performs a hash or sort merge — cost scales with the size of the tables, not the depth of the traversal. For a 3-hop query on a 1B-row friendship table, the intermediate result sets are astronomical. The problem is that the data model (rows and tables) doesn't match the query shape (follow edges from node to node).

**Minimal solution**: Store the graph natively — nodes and edges as first-class objects, with edges physically pointing to their target nodes. A traversal is following pointers, not scanning tables. Traversal cost scales with the number of hops and the degree of each node, not the total number of rows in the database.

**Production generalization**: Graph databases (Neo4j, Amazon Neptune) store data as nodes and labeled directed edges. Multi-hop traversal is a pointer-following operation — orders of magnitude faster than equivalent SQL JOINs for relationship-heavy queries. Use for: social networks, fraud detection (fraud rings are graph patterns), recommendation engines, knowledge graphs.

> **Analogy:** A web of sticky-note people connected by labeled strings. "Alice KNOWS Bob", "Bob LIKES Movie X". Traversing three hops (friends of friends of friends) is trivial — just follow the strings. In a relational DB, the same query requires three expensive self-JOINs.

**Use Case**: Highly connected data, relationship-heavy queries

**Examples:**
- **Neo4j**: Native graph, Cypher query language
- **Amazon Neptune**: AWS managed
- **JanusGraph**: Distributed graph database

**Data Model:**
```
Nodes:         (Person:John), (Person:Jane), (Movie:Inception)
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

- **ACID transactions required** (banking, e-commerce checkout)
- **Complex queries with JOINs** (reporting, analytics)
- **Structured, predictable data** (user management, inventory)
- **Data integrity critical** (financial records)
- **Moderate scale** (millions of rows, not billions)

### When to Use NoSQL

- **Massive scale** (billions of records, petabytes of data)
- **High write throughput** (logging, time-series, IoT)
- **Flexible schema** (evolving data models, rapid iteration)
- **Horizontal scaling needed** (distributed across many nodes)
- **Availability over consistency** (social media feeds, recommendation engines)

### Hybrid Approach (Polyglot Persistence)

Many systems use **both SQL and NoSQL** for different components:

```
E-commerce System:
├── PostgreSQL   : Orders, payments, inventory (ACID)
├── MongoDB      : Product catalog, user reviews (flexibility)
├── Redis        : Shopping cart, session data (speed)
├── Elasticsearch: Product search (full-text search)
└── Cassandra    : User activity logs (high write volume)
```

> **Interview Tip:** Mentioning polyglot persistence immediately signals senior-level thinking. Justify each choice with the access pattern it serves, not just the technology name.

---

## ACID vs BASE

**Question**: You're processing a payment. Step 1: debit $100 from account A. Step 2: credit $100 to account B. Your server crashes after step 1. Account A is now $100 short and account B received nothing. How do you guarantee this never results in money disappearing?

**Physical constraint**: A server can crash at any instruction. Disk writes are not instantaneous — the OS buffers them, and an untimely crash can leave a partial write. Across two separate database tables (or two separate services), there is no built-in atomic primitive — success on the first operation does not imply success on the second.

**Minimal solution**: Write both operations to a log before executing either one. On recovery, replay the log — either both complete or neither does. This is the core of Write-Ahead Logging (WAL), and it's the mechanism that makes ACID's Atomicity and Durability possible.

**Production generalization**: ACID formalizes four guarantees that together make transactions safe. BASE is the alternative chosen when ACID's coordination cost is too high for the required throughput — it accepts eventual consistency in exchange for write speed and availability.

> **Analogy (ACID):** Imagine a restaurant order. The waiter either delivers your **whole** order or comes back and tells you it's cancelled — nothing arrives half-done (Atomicity). The bill is always mathematically correct (Consistency). Two tables' orders never get mixed up in the kitchen (Isolation). Even if the power cuts mid-service, the kitchen's printed ticket still has your order (Durability).

### ACID (SQL Databases)

**Atomicity**: All operations in a transaction succeed or all fail.
```java
// Spring @Transactional ensures atomicity
@Transactional
public void transferFunds(long fromId, long toId, BigDecimal amount) {
    accountRepository.debit(fromId, amount);   // Step 1
    accountRepository.credit(toId, amount);    // Step 2
    // If step 2 throws, step 1 is rolled back automatically
}
```

**Consistency**: Database moves from one valid state to another.
- Constraints enforced (foreign keys, unique, check constraints)
- Application-level invariants maintained (e.g., balance never goes negative)

**Isolation**: Concurrent transactions don't interfere with each other.
```
Transaction A: READ(balance=500) → WRITE(balance=400)
Transaction B: READ(balance=500) → WRITE(balance=450)
Serializable result: one runs first, the other sees the committed value
```

**Durability**: Committed data survives crashes and power failures.
- Writes persisted to disk via WAL (Write-Ahead Log)
- Database replays WAL on restart to recover committed state

---

### BASE (NoSQL Databases)

> **Analogy:** DNS propagation. You update a domain's IP, and most of the world sees the new IP within minutes — but a user in a remote region might still reach the old server for another hour. The system is "basically available" (it works), "soft state" (it's in transition), and "eventually consistent" (it will converge).

**Basically Available**: System appears to work most of the time. Partial failures are tolerated.

**Soft State**: State may change over time, even without new input, as replication catches up.

**Eventually Consistent**: The system will converge to consistency given enough time. Reads may return stale data temporarily.

**Example: DynamoDB Write Path**
```
1. Client writes to Node A   → 200 OK returned immediately
2. Node A replicates to B, C → Background async
3. Client reads from Node B  → May return the OLD value (replication lag)
4. After replication done    → All nodes return new value
```

**Trade-off:** You gain write speed and availability; you sacrifice the guarantee of reading your own write (unless you use strong consistency reads, which cost more in DynamoDB).

---

## CAP Theorem

**Question**: You have a database replicated across two datacenters. The network link between them fails. Writes are still coming in to both sides. What do you do? Option A: stop accepting writes until the link is restored (users see errors). Option B: accept writes on both sides and reconcile later (some users will see stale data). There is no Option C. Which do you choose, and what determines the right answer?

**Physical constraint**: Networks partition. A cross-datacenter link can fail due to a fiber cut, a BGP routing issue, or a hardware failure. This is not a hypothetical — it happens to every large system. When it happens, the two sides of the partition cannot communicate. Any write accepted on side A cannot reach side B until connectivity is restored. The latency of that gap is the speed of light across the link distance — there is no engineering solution that makes writes instantaneous across a partitioned network.

**Minimal solution**: Choose one: reject all writes during a partition to prevent divergence (CP), or accept writes on both sides and merge later (AP). There is no third choice that gives you both consistency and availability when the network has failed.

**Production generalization**: CAP Theorem formalizes this: in a distributed system you can guarantee at most 2 of Consistency, Availability, and Partition Tolerance. Since partition tolerance is required in any real distributed system, the practical choice is CP vs AP — and that choice should be driven by your domain's tolerance for stale reads versus its tolerance for write rejection.

> **Analogy:** A bank with branches in New York and London. The network cable between them cuts (partition). Now:
> - **CP choice**: Lock all withdrawals at both branches until the cable is restored. Customers wait, but no one overdrafts. (Consistent, not Available)
> - **AP choice**: Let each branch operate independently — withdrawals proceed at both. When the cable is restored, you reconcile and deal with the overdraft. (Available, not Consistent)
> Partition tolerance isn't optional — cables do cut. The real choice is what you do *during* the outage.

**CAP Theorem**: In a distributed system, you can only guarantee 2 out of 3:

- **C**onsistency: Every read receives the most recent write
- **A**vailability: Every request receives a response (not an error)
- **P**artition Tolerance: System continues despite network partitions

### The Reality of CAP

**Partition tolerance is mandatory** in any distributed system (networks fail), so the real choice is:

```
During a network partition:
├── CP (Consistency + Partition Tolerance)
│   → Reject writes/reads to maintain consistency
│   → Examples: MongoDB (default), HBase, Redis Cluster
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

*Spanner achieves CA-like properties using atomic clocks and TrueTime across datacenters.

### PACELC Theorem (Extended CAP)

**PACELC**: If **P**artition, choose **A** or **C**; **E**lse (no partition), choose **L**atency or **C**onsistency.

```
Normal operation (no partition):
├── Low Latency     → Eventual consistency (Cassandra, DynamoDB default)
└── Strong Consistency → Higher latency (Spanner, MongoDB readConcern:majority)
```

> **Interview Tip:** Most interviewers expect you to know CAP, but mentioning PACELC shows you understand that the consistency/latency trade-off exists even on the happy path, not just during failures.

---

## Database Replication

**Question**: Your primary database server dies at 2am. You have no replicas. Time to recovery: find someone on-call (5 min), diagnose (10 min), restore from last backup (30 min), replay WAL logs (20 min). Total: ~65 minutes of data loss and downtime. For a payment system, is 65 minutes of downtime acceptable? What's the minimum change to your architecture that reduces this to under 30 seconds?

**Physical constraint**: A single machine has no redundancy. When it fails, it fails completely — CPU, RAM, disk, all unavailable simultaneously. Disk failure rates for spinning drives are ~1–2% per year; SSDs are lower but non-zero. In a cluster of 100 machines, you expect at least one disk failure per year. Recovery from bare hardware requires restoring from backup, which is bounded by backup age and restore time.

**Minimal solution**: Keep a continuously updated copy of the data on a second machine (a replica). When the primary fails, promote the replica. Failover time: the time to detect failure + re-route traffic, typically 10–30 seconds with automated tooling. The replica is always slightly behind the primary — the lag depends on whether replication is synchronous (zero lag, higher write latency) or asynchronous (lower write latency, possible data loss on failover).

**Production generalization**: Replication is keeping copies of data on multiple nodes. The tradeoff is synchronous (strong consistency, higher write latency — every write waits for replica acknowledgment) vs asynchronous (low write latency, risk of data loss if the primary fails before replication completes). Choose based on whether your domain can tolerate any data loss at failover.

> **Analogy:** A general (the leader) issues orders. Lieutenants (followers) copy those orders down and can brief soldiers (handle reads) independently. Only the general issues new orders (handles writes). If the general falls, a lieutenant is promoted. Synchronous replication = the general waits for every lieutenant to confirm receipt before moving on. Asynchronous = the general moves on immediately and trusts lieutenants will catch up.

**Replication**: Keeping copies of data on multiple nodes for redundancy and availability.

### Leader-Follower (Master-Slave)

**How it works:**
1. **Leader** handles all writes
2. **Followers** replicate changes from the leader
3. Reads can be served by followers (scales read throughput)

**Synchronous Replication:**
```
Client → Write to Leader
Leader → Wait for ALL followers to confirm write
Leader → Acknowledge success to client
```
- Strong consistency — reads from followers are never stale
- Higher write latency — must wait for slowest follower

**Asynchronous Replication:**
```
Client → Write to Leader
Leader → Acknowledge success immediately
Leader → Replicate to followers (background, best-effort)
```
- Low write latency
- Risk of data loss if leader fails before replication completes
- Followers may serve stale reads (replication lag)

**Use Cases:** PostgreSQL, MySQL, MongoDB (default). Ideal for read-heavy workloads.

**Challenges:**
- Leader failure requires failover (promote a follower, update routing)
- Replication lag causes stale reads on followers — monitor lag and route time-sensitive reads to the leader

---

### Multi-Leader Replication

**How it works:** Multiple nodes accept writes; changes are replicated to all other leaders.

**Use Cases:**
- Multi-datacenter setups (each datacenter has a local leader for low latency)
- Offline-first mobile apps (device is its own leader, syncs when back online)

**Challenges:**
- Write conflicts (two leaders accept different values for the same key)
- Need conflict resolution strategy: last-write-wins, merge, application-level

---

### Leaderless Replication

> **Analogy:** A Dropbox shared folder with three copies on three laptops. When you save a file, it writes to all three. To be confident you have the latest, you read from two. If two out of three agree, you trust that value.

**How it works:** Client writes to multiple nodes simultaneously (quorum). No designated leader.

**Example (Cassandra, DynamoDB):**
```java
// Cassandra query with quorum consistency
String query = "INSERT INTO orders (id, status) VALUES (?, ?)";
SimpleStatement stmt = SimpleStatement.builder(query)
    .setConsistencyLevel(ConsistencyLevel.QUORUM) // W = majority
    .build();
session.execute(stmt);
```

**Quorum Rules:**
- **W** = Write quorum (nodes that must acknowledge the write)
- **R** = Read quorum (nodes that must respond to the read)
- **N** = Total replicas
- **Strong consistency**: W + R > N (e.g., W=2, R=2, N=3)
- **High availability**: W=1, R=1, N=3 (fast but stale reads possible)

---

## Database Sharding

**Question**: Your PostgreSQL primary is at 100% CPU and 100% disk I/O. You've added all the read replicas you can — but writes still all go to one machine. You cannot add more CPU or disk to that machine. The write throughput ceiling for one PostgreSQL instance is roughly 50,000 writes/sec. You need 200,000 writes/sec. What do you do?

**Physical constraint**: All writes in a leader-follower setup go to one primary. That primary has a single CPU, a single disk I/O budget, and a single WAL write path. Vertical scaling (bigger machine) has a hard ceiling — no single machine exists that can handle arbitrary write throughput. Replication only helps with reads. To scale writes, you must have multiple machines accepting writes simultaneously — which requires partitioning the data so each machine owns a subset.

**Minimal solution**: Divide the key space across N databases. Each database owns 1/N of the data and handles 1/N of the writes. A routing layer maps `key → shard`. This works until: the routing logic is wrong (uneven distribution creates hotspots), a shard grows too large (requiring re-sharding, which is painful), or a query needs data from multiple shards (cross-shard JOINs become network calls).

**Production generalization**: Sharding partitions data across multiple nodes. The shard key determines which node owns which rows, and the choice of shard key is the most important sharding decision. A bad shard key creates hotspots that negate the benefit of sharding entirely.

> **Analogy:** A library that has outgrown one building. You split the collection: authors A–M go to Floor 1, N–Z go to Floor 2 (range sharding). Now each floor handles half the traffic. Hash sharding is like randomly assigning each book to a shelf by running the title through a blender — perfectly even distribution, but you need a lookup card to find anything. Directory sharding is that lookup card: a catalog saying exactly which shelf holds which book.

**Sharding** is partitioning data across multiple databases (nodes) to scale beyond the limits of a single server.

### Sharding Strategies

#### 1. Key-Based Sharding (Hash Sharding)

```java
int shardId = Math.abs(key.hashCode()) % numberOfShards;
```

- **Pros**: Even data distribution, no hotspots.
- **Cons**: Resharding is painful — changing `numberOfShards` remaps almost all keys.
- **Solution**: Use **Consistent Hashing** — only `K/N` keys move when a node is added or removed.

#### 2. Range-Based Sharding

```
Shard 1: UserIds 1       – 1,000,000
Shard 2: UserIds 1,000,001 – 2,000,000
```

- **Pros**: Range queries are efficient (scan a single shard).
- **Cons**: **Hotspots** — if recent users are most active, the last shard gets all the traffic while earlier shards sit idle.

#### 3. Directory-Based Sharding

A lookup table maps `Key → Shard_ID`.

- **Pros**: Fully flexible — individual keys can be migrated without remapping everything.
- **Cons**: The lookup table is a single point of failure and a bottleneck; must be highly available and cached.

### Challenges of Sharding

| Challenge | Details |
|-----------|---------|
| **Cross-shard JOINs** | Expensive or impossible — denormalize or use application-side joins |
| **Distributed transactions** | Require 2PC (slow) or saga pattern; avoid when possible |
| **Resharding** | Requires double-writes and live data migration — plan for this upfront |
| **Uneven load** | Hash sharding solves distribution; range sharding needs careful key selection |

> **Interview Tip:** Always ask "what is the access pattern?" before choosing a shard key. A bad shard key (e.g., sharding by `created_at` in a time-series system) creates hotspots that defeat the purpose of sharding.

---

## Indexes

**Question**: Your `users` table has 100 million rows. You run `SELECT * FROM users WHERE email = 'alice@example.com'`. Without an index, the database scans all 100 million rows. At 1 microsecond per row comparison, that's 100 seconds. With an index, it's a single O(log N) lookup — ~26 comparisons. What data structure enables this, and what does it cost you?

**Physical constraint**: A full table scan reads every row from disk. At 100 million rows × 100 bytes each = 10 GB, and a disk reads at ~500 MB/s sequential, that's 20 seconds minimum just for I/O — before any CPU processing. The only way to avoid the full scan is to maintain a separate, sorted structure that maps query predicates to row locations. That structure must be updated on every write.

**Minimal solution**: Maintain a sorted copy of the indexed column(s) alongside the table. Binary search on this sorted copy takes O(log N) comparisons to find the target row's disk location. This is the B-tree index. The cost: every INSERT, UPDATE, or DELETE must also update the index — slower writes for faster reads.

**Production generalization**: B-trees (read-heavy workloads, point lookups, range queries) vs LSM trees (write-heavy workloads, append-only). Every index on a write-heavy table adds write latency. Auditing unused indexes is routine production maintenance.

> **Analogy (B-tree):** The alphabetical index at the back of a textbook. You flip to "R", scan a sorted list of entries, and jump straight to the page. Finding a page is O(log N) — fast. But every time a new page is added, the index must be updated in sorted order — moderately expensive.
>
> **Analogy (LSM Tree):** A to-do list where you always add new items to the *top*. Writes are instant — just prepend. But to find an item, you might have to scan from the top down through several pages of sticky notes. Periodic "compaction" re-sorts the list overnight so future reads are faster.

**Indexes** speed up read queries at the cost of slower writes and increased storage.

### 1. B-Tree / B+ Tree (Default in SQL)

- **Structure**: Self-balancing tree; data sorted at leaf nodes
- **Complexity**: O(log N) for search, insert, delete
- **Best For**: Read-heavy workloads, point lookups, range queries (`WHERE age > 20`)
- **Used by**: PostgreSQL, MySQL (InnoDB), MongoDB

```java
// This query uses a B-tree index efficiently
// CREATE INDEX idx_users_age ON users(age);
List<User> users = entityManager
    .createQuery("SELECT u FROM User u WHERE u.age > :minAge", User.class)
    .setParameter("minAge", 20)
    .getResultList();
```

### 2. LSM Tree (Log-Structured Merge Tree)

- **Structure**:
  - **MemTable**: In-memory sorted buffer for recent writes
  - **SSTable**: Immutable on-disk sorted files flushed from MemTable
  - Compaction merges SSTables periodically to reclaim space
- **Performance**:
  - **Writes**: O(1) append-only — extremely fast
  - **Reads**: May check MemTable + multiple SSTables; **Bloom filters** used to skip irrelevant SSTables
- **Best For**: Write-heavy workloads (logs, IoT, time-series)
- **Used by**: Cassandra, RocksDB, LevelDB, Kafka (log segments)

**Trade-off summary:**

| | B-Tree | LSM Tree |
|-|--------|----------|
| **Write speed** | Moderate (O(log N)) | Very fast (O(1)) |
| **Read speed** | Fast (O(log N)) | Moderate (multiple files) |
| **Write amplification** | Low | High (compaction rewrites) |
| **Best use case** | Read-heavy, OLTP | Write-heavy, time-series |

---

## Normalization vs Denormalization

**Question**: An author changes their name. In your database, the author's name appears in 5 million book records (denormalized). You have to update 5 million rows. Meanwhile, your read query for a book page returns in 1ms because it's a single row fetch. If you normalize (store the name once), the update is trivial — but every book page read requires a JOIN. At 100,000 book page reads/second, which cost is higher?

**Physical constraint**: Joins require reading from two tables and correlating rows by key — typically two separate I/O operations plus CPU for the merge. At high read throughput, the join's I/O cost multiplies across every request. Denormalization co-locates related data in one row, eliminating the join at the cost of write-time duplication.

**Minimal solution**: Normalize everything — each piece of data stored once. Correct and space-efficient. Breaks when join latency under load exceeds acceptable P99 thresholds.

**Production generalization**: Denormalize along your most frequent read path. If you read books 1,000× more often than you update author names, duplicating the name into each book record is a worthwhile tradeoff. The rule: normalize for write correctness, denormalize for read performance, and always tie the choice to observed access patterns.

> **Analogy:** Normalization is like a master address book: every person's address is stored in one place. When someone moves, you update one record and everyone sees the new address. Denormalization is printing that address on every letter in the mailroom — blazing fast to grab and send, but if the address changes, you have to update every letter.

### Normalization (SQL)

- **Goal**: Eliminate redundancy, ensure data integrity
- **1NF → 3NF**: Progressive rules to split tables and remove dependencies
- **Pros**: Consistent data; smaller storage; update one row, not many
- **Cons**: Many JOINs required for reads (slower at scale)

```sql
-- Normalized: author name stored once
SELECT b.title, a.name
FROM books b
JOIN authors a ON b.author_id = a.id
WHERE a.name = 'Martin Fowler';
```

### Denormalization (NoSQL / Read-Optimized SQL)

- **Goal**: Optimize read performance by co-locating related data
- **Strategy**: Duplicate data — store `author_name` directly inside the `books` document
- **Pros**: No JOINs; single document/row fetch; faster reads
- **Cons**: Update anomalies — changing `author_name` requires updating every book record

```java
// MongoDB denormalized document — no JOIN needed
Document book = new Document("title", "Refactoring")
    .append("author_name", "Martin Fowler")   // duplicated
    .append("author_bio", "Software engineer..."); // duplicated
```

> **Interview Tip:** Denormalize along your most frequent read path. If you read books 1000x more than you update author names, the duplication cost is worth the read speedup. Always tie the choice to observed or expected access patterns.

---

## Transactions & Isolation Levels

**Question**: Transaction A reads an account balance ($500), then Transaction B updates the balance to $400 (committed), then Transaction A reads the balance again — and sees $500 still (its original read). Transaction A makes a decision based on $500. Is this a bug? The answer is: it depends on what isolation level you configured. At which level does this happen, and when is it acceptable?

**Physical constraint**: A database running concurrent transactions must choose: either block readers until all pending writes commit (strong isolation, low throughput due to contention), or give readers a snapshot of the data as of some point in time (MVCC — Multi-Version Concurrency Control), which allows concurrent reads and writes at the cost of readers seeing slightly stale data.

**Minimal solution**: Block all reads until all writes in-flight commit. Serializable isolation. Perfectly consistent, but every write holds a lock that blocks all readers — throughput collapses under load.

**Production generalization**: Isolation levels are a spectrum: looser isolation allows higher concurrency but introduces specific anomalies (dirty reads, non-repeatable reads, phantom reads). The right level depends on what anomalies your application can tolerate. PostgreSQL defaults to Read Committed; most applications work correctly at that level with careful query design.

> **Analogy:** Reading a newspaper in four different ways:
> - **Read Uncommitted** = reading over the editor's shoulder mid-sentence — you see text that might be deleted before the final edition.
> - **Read Committed** = reading the edition as it's printed, but a new edition can come out while you're still reading.
> - **Repeatable Read** = you take your own copy; no one changes it while you read — but a new article can appear in the *next* section.
> - **Serializable** = you wait for the final, complete, sealed edition before reading anything. Nothing changes, nothing appears mid-read.

**Isolation Levels** trade off consistency for performance. Higher isolation = fewer concurrency bugs, but more locking and lower throughput.

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance |
|-----------------|:----------:|:-------------------:|:------------:|:-----------:|
| **Read Uncommitted** | Yes | Yes | Yes | Fastest |
| **Read Committed** | No | Yes | Yes | Fast |
| **Repeatable Read** | No | No | Yes (usually) | Slow |
| **Serializable** | No | No | No | Slowest |

**Concurrency anomalies explained:**

1. **Dirty Read**: You read a row that another transaction has modified but not yet committed. If that transaction rolls back, you read data that never officially existed.
2. **Non-Repeatable Read**: You read a row twice in the same transaction and get different values — another transaction committed an update in between.
3. **Phantom Read**: You run a range query twice and get a different number of rows — another transaction inserted a matching row in between.

```java
// Setting isolation level in Spring
@Transactional(isolation = Isolation.REPEATABLE_READ)
public Report generateReport(long accountId) {
    // Safe to read the same row multiple times — no non-repeatable reads
    Account account = accountRepository.findById(accountId).orElseThrow();
    List<Transaction> txns = transactionRepository.findByAccount(accountId);
    return buildReport(account, txns);
}
```

> **Interview Tip:** PostgreSQL default is **Read Committed**. MySQL InnoDB default is **Repeatable Read**. Know your DB's default — it often matters in production bug investigations.

---

## MVCC: How Isolation Is Actually Implemented

**Question**: How does PostgreSQL allow 1,000 concurrent readers and writers without blocking each other at Read Committed or Repeatable Read isolation, while still showing each reader a consistent snapshot?

The answer is MVCC (Multi-Version Concurrency Control). Instead of locking rows on read, the database keeps multiple versions of each row and shows each transaction the version that was current at the appropriate point in time. Readers never block writers; writers never block readers.

### PostgreSQL: xmin and xmax

Every row in PostgreSQL has two hidden system columns:

```sql
SELECT *, xmin, xmax FROM orders LIMIT 5;
-- xmin: transaction ID that inserted this row version
-- xmax: transaction ID that deleted (or updated) this row version; 0 = not deleted
```

```
Row lifecycle:
  INSERT: creates row with xmin=<inserting_txn_id>, xmax=0
  UPDATE: creates NEW row version (xmin=<updating_txn_id>); marks OLD version xmax=<updating_txn_id>
  DELETE: marks row xmax=<deleting_txn_id>

At any point, a row can have multiple versions in the heap:
  Old version: xmin=100, xmax=150  (inserted by txn 100, updated/deleted by txn 150)
  New version: xmin=150, xmax=0    (current, created by txn 150)
```

**Visibility rule** (simplified): Transaction T with snapshot at time S sees a row version if:
- `xmin` committed before S started (the row was inserted before my snapshot), AND
- `xmax = 0` OR `xmax` started after S (the row was not deleted before my snapshot)

```sql
-- See the hidden columns:
SELECT xmin, xmax, id, status FROM orders WHERE id = 42;
-- xmin=1234, xmax=0   → currently visible (not deleted)
-- xmin=1234, xmax=1500 → deleted by txn 1500
```

### Snapshot Isolation

At **Repeatable Read** level, PostgreSQL takes a snapshot of committed transaction IDs at the start of your transaction. Your transaction only sees rows where `xmin` is in the "committed before my snapshot" set.

```
Active transactions when T begins: {txn200, txn201, txn202}
T's snapshot: "see all transactions committed before txn200 started"

txn205 commits and inserts a row → T does not see it (committed after T's snapshot)
txn198 committed before → T sees its rows

This is snapshot isolation: T sees a consistent point-in-time view for the entire transaction duration.
```

**Non-repeatable read prevention**: since T's snapshot never changes within the transaction, reading the same row twice always returns the same version. This is why Repeatable Read prevents non-repeatable reads without locking.

**Phantom read in PostgreSQL**: PostgreSQL's Repeatable Read also prevents phantoms (unlike the SQL standard which only requires Serializable for phantom prevention). A new row inserted by another transaction won't appear in T's range scan because the new row's `xmin` is after T's snapshot.

### SSI: Serializable Snapshot Isolation (PostgreSQL 9.1+)

**Problem with Snapshot Isolation**: it prevents most anomalies but not all. The **write skew** anomaly:

```
Doctors on call: Alice and Bob (minimum 1 must always be on call)
T1 (Alice): reads doctors_on_call → [Alice, Bob] (2 doctors)
            sees ≥ 2, decides to take herself off call
T2 (Bob):   reads doctors_on_call → [Alice, Bob] (2 doctors)
            sees ≥ 2, decides to take himself off call
T1 commits: removes Alice from on-call
T2 commits: removes Bob from on-call
Result: 0 doctors on call — violated the invariant!

Both transactions read a consistent snapshot and wrote non-overlapping rows.
Snapshot isolation allows both to commit. → WRITE SKEW ANOMALY.
```

**SSI solution**: PostgreSQL tracks read-write dependencies between concurrent transactions. If a dependency cycle is detected (T1 read something T2 will write; T2 read something T1 will write), one transaction is aborted with `ERROR: could not serialize access due to read/write dependencies`.

```java
// Java: handle SSI serialization failure with retry
@Transactional(isolation = Isolation.SERIALIZABLE)
public void updateDoctorOnCall(String doctorId) {
    try {
        int onCallCount = doctorRepo.countOnCall();
        if (onCallCount > 1) {
            doctorRepo.removeFromOnCall(doctorId);
        }
    } catch (CannotSerializeTransactionException e) {
        // SSI detected a conflict — retry the transaction
        throw new RetryableException("Serialization conflict, retry");
    }
}
```

**SSI performance**: much better than 2PL (Two-Phase Locking, the traditional Serializable implementation). SSI uses optimistic concurrency — transactions proceed without locking, conflicts detected at commit. Under low-contention workloads (most production apps), SSI abort rate is very low.

### Isolation Level → Implementation Mapping

| Isolation Level | Implementation | What It Does |
|-----------------|---------------|--------------|
| Read Uncommitted | Same as RC in PG | PostgreSQL never reads dirty data regardless |
| Read Committed | Per-statement snapshot | Each SQL statement sees latest committed state |
| Repeatable Read | Per-transaction snapshot | Snapshot taken at transaction start; held for duration |
| Serializable (SSI) | Snapshot + dependency tracking | Detects and aborts transactions in dependency cycles |

### MVCC Overhead: Table Bloat and VACUUM

**The cost of MVCC**: old row versions accumulate in the heap. PostgreSQL's `VACUUM` process cleans them up.

```
High-UPDATE workload on 1M row table:
  Each update creates a new row version → old version stays until VACUUM
  1M updates/hour × 100 bytes/row = 100MB/hour of dead tuples

autovacuum: runs automatically when dead tuple ratio > 20% (default)
  Reclaims space, updates pg_statistic, prevents transaction ID wraparound

Manual: VACUUM ANALYZE table_name
  VACUUM FULL: rewrites the table (blocking), reclaims disk space back to OS
```

**Transaction ID wraparound** (xid wraparound): PostgreSQL uses 32-bit transaction IDs. After 2 billion transactions, IDs wrap around. Rows with old xmins look "in the future" to new transactions — PostgreSQL forcibly prevents wraparound with autovacuum (freezes old tuples). Let autovacuum fall behind → `VACUUM` becomes mandatory (emergency outage).

**Monitoring**:
```sql
-- Find tables with most dead tuples
SELECT relname, n_dead_tup, n_live_tup,
       n_dead_tup::float / nullif(n_live_tup + n_dead_tup, 0) AS dead_ratio
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC
LIMIT 10;

-- Check transaction ID age (proximity to wraparound)
SELECT datname, age(datfrozenxid) AS xid_age
FROM pg_database
ORDER BY age(datfrozenxid) DESC;
-- Alert if xid_age approaches 1.5 billion
```

---

## Distributed Transactions

> **Analogy:** Two-phase commit (2PC) is like a wedding officiant asking "Does everyone agree?" before pronouncing you married. Phase 1 (prepare): everyone says "yes, I'm ready." Phase 2 (commit): officiant says "done." If anyone hesitates in Phase 1, the whole thing is called off. The downside: if the officiant disappears between phases, everyone is frozen waiting for a decision.

Distributed transactions are needed when a single logical operation spans multiple databases or services.

### Two-Phase Commit (2PC)

```
Phase 1 - Prepare:
  Coordinator → Node A: "Can you commit?"
  Coordinator → Node B: "Can you commit?"
  Node A → Coordinator: "Yes, prepared"
  Node B → Coordinator: "Yes, prepared"

Phase 2 - Commit:
  Coordinator → Node A: "Commit"
  Coordinator → Node B: "Commit"
```

**Problems:**
- **Blocking**: If the coordinator crashes between phases, participants are locked waiting
- **Latency**: Two network round-trips per transaction
- **Availability**: Any participant can veto the whole transaction

### Saga Pattern (Preferred at Scale)

Break the distributed transaction into a sequence of local transactions, each with a compensating transaction for rollback.

```
Order Saga:
  1. Create Order (Orders DB)    → compensate: Cancel Order
  2. Reserve Inventory (Stock DB) → compensate: Release Inventory
  3. Charge Payment (Payment DB) → compensate: Refund
  4. Send Confirmation (Email)
```

**Choreography**: Each service emits an event; the next service listens and reacts.
**Orchestration**: A central saga orchestrator tells each service what to do next.

> **Interview Tip:** In microservices interviews, saying "use the Saga pattern with compensating transactions instead of 2PC" is almost always the right answer. Back it up with: "2PC holds locks across services and requires a coordinator, making it impractical at scale."

---

## Database Selection Guide

| Requirement | Recommended DB | Why |
|-------------|---------------|-----|
| **Financial / Payments** | PostgreSQL / MySQL | ACID, strong consistency, proven reliability |
| **Social Graph** | Neo4j / Amazon Neptune | Graph traversals, friends-of-friends queries |
| **Product Catalog** | MongoDB / DocumentDB | Flexible schema, nested JSON, read-heavy |
| **High Velocity Logs** | Cassandra / ScyllaDB | Write-heavy (LSM Tree), time-series optimized |
| **Real-time Leaderboard** | Redis | In-memory sorted sets, O(log N) rank updates |
| **Full Text Search** | Elasticsearch | Inverted index, relevance ranking, faceting |
| **Multi-region OLTP** | Google Spanner / CockroachDB | Distributed ACID, TrueTime, global consistency |
| **Analytics / OLAP** | BigQuery / Redshift / Snowflake | Columnar storage, MPP query engines |

---

## Senior Engineer Insights

- **Design trade-offs first**: Choose SQL when you need ACID and complex queries; choose NoSQL when you need horizontal scale and can tolerate eventual consistency. Polyglot persistence (PostgreSQL for orders + Redis for sessions + Elasticsearch for search) is the norm at scale — justify each choice by access pattern.

- **Scale in stages**: Add read replicas and caching before sharding. Sharding is expensive to operate and hard to undo. Many systems never need it.

- **Replication lag is real**: When reading from a follower, you may get stale data. Monitor replication lag (expose it as a metric). Route latency-sensitive reads (e.g., "show me my just-submitted order") to the leader.

- **Schema migrations are dangerous at scale**: Online DDL (`ALTER TABLE ... ALGORITHM=INPLACE`) or tools like `gh-ost` / `pglogical` are needed for large tables. Index creation with `CREATE INDEX CONCURRENTLY` in PostgreSQL avoids write locks.

- **Indexes are not free**: Every index speeds reads but slows all writes on that table. Over-indexing on a write-heavy table is a common production pitfall. Audit unused indexes periodically.

- **Observability checklist**: P99 query latency, replication lag, connection pool saturation (pool exhaustion is a silent killer), slow query log, deadlock rate.

- **Resilience**: Primary failure → promote a replica (automated with tools like Patroni/Orchestrator). Use connection poolers (PgBouncer, ProxySQL) so one slow query doesn't exhaust all DB connections. For critical writes, weigh synchronous replication (latency cost) vs async (data loss risk) explicitly.

---

## Quick Revision

- **SQL vs NoSQL**: SQL = ACID, schema, JOINs; NoSQL = scale-out, flexible schema, eventual consistency. Use both where each fits (polyglot).
- **ACID**: Atomicity (all-or-nothing), Consistency (valid state transitions), Isolation (concurrent transactions don't interfere), Durability (committed data survives crashes).
- **CAP**: Partition tolerance is required; choose CP (reject requests to stay consistent) or AP (serve possibly stale data). PACELC extends this: even without partition, choose latency vs consistency.
- **Replication**: Leader–follower (read scaling, HA); sync vs async (latency vs data loss risk); leaderless = quorum (W + R > N for strong consistency).
- **Sharding**: Hash (even distribution, resharding costly → use consistent hashing), range (range queries easy, hotspots), directory (flexible, lookup overhead). Cross-shard JOINs and transactions are hard — avoid by design.
- **Indexes**: B-tree (read-heavy, range queries, O(log N)); LSM (write-heavy, O(1) append, compaction). Indexes trade write speed and storage for read speed.
- **Isolation**: Read Committed (default in Postgres); Repeatable Read (default in MySQL InnoDB); Serializable (strongest, slowest). Dirty → Non-Repeatable → Phantom reads map to increasing isolation level.
- **Distributed Transactions**: Prefer Saga pattern with compensating transactions over 2PC at microservice scale.
- **Canonical interview answer**: "We use PostgreSQL for orders and payments (ACID). We add read replicas for reporting queries. If we outgrow one primary, we shard by `order_id` using consistent hashing. We use Redis for session and Elasticsearch for search — each tool chosen for its access pattern."
- **Common mistakes**: Picking NoSQL "because scale" without analysing access patterns; sharding prematurely; ignoring replication lag on follower reads; over-indexing write-heavy tables; skipping connection pooling.

---

## Interview Questions Asked

### Conceptual
1. **"Explain the difference between B-tree and LSM tree"** → B-tree: reads in O(log N), updates in-place, good for read-heavy; LSM: appends to memtable then flushes to SSTables, O(1) write path, background compaction pays the cost. Testing: do you know which storage engine to pick for write-heavy vs read-heavy workloads.
2. **"What is ACID and which NoSQL databases sacrifice which part?"** → Atomicity (all-or-nothing), Consistency (valid transitions), Isolation (no dirty reads between concurrent txns), Durability (survives crash). Cassandra sacrifices Isolation (no multi-row transactions) and relaxes Consistency (tunable). MongoDB (pre-4.0) sacrificed multi-document Atomicity. Testing: do you know that "NoSQL = no ACID" is a myth — it varies per system.
3. **"How does MVCC work in PostgreSQL?"** → Each row version is tagged with `xmin`/`xmax` (transaction IDs). Readers see the latest committed version as of their snapshot start; writers create new versions without blocking readers. `VACUUM` cleans dead versions. Testing: understanding of why Postgres reads never block writes.
4. **"What is write amplification and how does LSM handle it?"** → One logical write causes multiple physical writes (WAL + memtable + multiple SSTable levels). LSM accepts write amplification during compaction (background) to keep the write path fast (O(1) append). Testing: trade-off awareness for storage engine design.
5. **"Explain the N+1 query problem and how to fix it"** → Fetching N orders then querying comments for each = N+1 DB round trips. Fix: JOIN in a single query, or use batch loading (DataLoader pattern). Testing: ORM misuse awareness, a common SDE-3 production bug.

### Comparison / Trade-off
1. **"When would you use NoSQL over SQL?"** → NoSQL when: access patterns are simple (key-value or document lookup), schema is dynamic or rapidly evolving, you need horizontal write scale beyond what one primary can handle, or you need multi-region active-active. SQL when: you need JOINs, complex transactions, or strong consistency.
2. **"When would you use a wide-column store vs a document store?"** → Wide-column (Cassandra, HBase): time-series or event data where you always query by a fixed primary key + sort key (e.g., user's events ordered by timestamp). Document store (MongoDB): entities with varied, nested structure queried in many ways. Key differentiator: query flexibility vs write throughput.

### Scenario / Design
1. **"How do you handle schema migrations on a live table with 100M rows?"** → Never use a blocking `ALTER TABLE`. Use `CREATE INDEX CONCURRENTLY` in Postgres or `gh-ost`/`pt-online-schema-change` for MySQL. Strategy: add nullable column → backfill in batches → add constraint → drop old column. Testing: production operational awareness at scale.
