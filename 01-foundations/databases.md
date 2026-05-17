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

> **Analogy:** A database is like a well-run warehouse. Without it, you'd pile boxes anywhere and spend hours searching. A DBMS is the warehouse management system: it knows exactly where every box is, who can access it, and how to keep two workers from grabbing the same box at once.

A **database** is an organized collection of structured information stored electronically. A **Database Management System (DBMS)** controls the database and provides an interface for users and applications to interact with it.

### Why Databases Matter in System Design

- **Data Persistence**: Store data beyond application lifetime
- **Concurrency Control**: Handle multiple users accessing data simultaneously
- **Data Integrity**: Enforce constraints and validation rules
- **Query Optimization**: Efficient data retrieval and manipulation
- **Backup & Recovery**: Protect against data loss
- **Scalability**: Handle growing data volumes and user load

---

## SQL vs NoSQL

> **Analogy:** SQL is a filing cabinet with labeled folders and strict dividers — every document must fit a predefined slot. NoSQL is a whiteboard covered in sticky notes — you can slap any shape of information anywhere, group them however makes sense today, and rearrange tomorrow. The filing cabinet is slower to update but trivially easy to audit; the whiteboard scales to a wall, then a room.

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
