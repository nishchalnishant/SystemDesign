# System Internals

This directory contains deep-dive guides into the internals of popular distributed systems and databases. Understanding these internals is crucial for SDE-3 level system design interviews.

## Available Internals

### Message Queues & Streaming
- **[Kafka](./kafka-internals.md)** - Distributed streaming platform
  - Partitions, ISR, Zero-Copy, Log Compaction
  - Exactly-once semantics, Consumer groups

### Databases

#### NoSQL
- **[Cassandra](./cassandra-internals.md)** - Wide-column distributed database
  - Ring architecture, Consistent hashing, LSM-tree
  - Compaction strategies, Tunable consistency

#### SQL
- **[PostgreSQL](./postgresql-internals.md)** - Advanced relational database
  - MVCC, WAL, VACUUM, Transaction isolation
  - Query planner, Indexes (B-tree, GIN, BRIN)

### Caching & In-Memory Stores
- **[Redis](./redis-internals.md)** - In-memory data structure store
  - Data structures, Persistence (RDB, AOF)
  - Replication, Cluster mode

### Search & Analytics
- **[Elasticsearch](./elasticsearch-internals.md)** - Distributed search engine
  - Inverted index, Sharding, Segment merging
  - Query execution, Aggregations

### Coordination Services
- **[ZooKeeper](./zookeeper-internals.md)** - Distributed coordination
  - Znodes, ZAB protocol, Leader election
  - Watches, Session management

---

## How to Use These Guides

### For Interview Prep
1. **Read sequentially**: Start with overview, then dive into specific sections
2. **Focus on trade-offs**: Understand why each design choice was made
3. **Practice explaining**: Can you explain MVCC or ZAB to an interviewer?
4. **Review interview questions**: Each guide has typical interview questions at the end

### During System Design
- **Reference architecture patterns**: Cassandra's ring, Kafka's partitioning
- **Compare alternatives**: When to use PostgreSQL vs Cassandra?
- **Justify technology choices**: "I'd use Kafka because of its log compaction for..."

### Deep Dive Topics by Role

**Backend Engineer:**
- PostgreSQL: MVCC, WAL, query optimization
- Redis: Persistence strategies, replication
- Kafka: Exactly-once semantics, consumer groups

**Distributed Systems Engineer:**
- Cassandra: Consistency levels, anti-entropy repair
- ZooKeeper: ZAB protocol, leader election
- Elasticsearch: Cluster state, shard allocation

**Data Engineer:**
- Kafka: Log retention, compaction policies
- Elasticsearch: Inverted index, aggregations
- Cassandra: Data modeling, compaction strategies

---

## Common Interview Patterns

### "How does X handle failures?"
- Cassandra: Hinted handoff, read repair
- PostgreSQL: WAL replay, replication
- Kafka: ISR, controlled shutdown
- ZooKeeper: Fast leader election, quorum

### "Explain the write path in X"
- Cassandra: Commit log → MemTable → SSTable
- PostgreSQL: WAL → Buffer pool → Disk
- Kafka: Append to partition → Replicate to ISR
- Elasticsearch: Translog → Refresh → Flush

### "How does X achieve consistency?"
- Cassandra: Tunable (ONE, QUORUM, ALL)
- PostgreSQL: Isolation levels (Read Committed, Repeatable Read)
- Kafka: ISR + acks configuration
- ZooKeeper: ZAB (total order, sequential consistency)

---

## Comparison Matrix

| System | Type | Read | Write | Consistency | Use Case |
|--------|------|------|-------|-------------|----------|
| **Cassandra** | NoSQL (Wide-column) | Fast | Very Fast | Tunable (eventual→strong) | Time-series, high write |
| **PostgreSQL** | RDBMS | Fast | Moderate | Strong (ACID) | Transactional, complex queries |
| **Redis** | In-memory | Very Fast | Very Fast | Eventual→Strong | Caching, real-time |
| **Elasticsearch** | Search Engine | Very Fast | Moderate | Near real-time | Full-text search, analytics |
| **Kafka** | Streaming | N/A | Very Fast | Configurable | Event streaming, logs |
| **ZooKeeper** | Coordination | Fast | Moderate | Sequential | Leader election, config |

---

## Further Learning

### Books
- **Designing Data-Intensive Applications** (DDIA) by Martin Kleppmann
- **Database Internals** by Alex Petrov
- **Kafka: The Definitive Guide** by Neha Narkhede

### Official Docs
- [Cassandra Architecture](https://cassandra.apache.org/doc/latest/architecture/)
- [PostgreSQL Internals](https://www.postgresql.org/docs/current/internals.html)
- [Elasticsearch Guide](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
- [ZooKeeper Programmer's Guide](https://zookeeper.apache.org/doc/current/zookeeperProgrammers.html)

### Practice
- Deploy these systems locally (Docker)
- Run benchmarks, observe behavior
- Intentionally fail nodes, observe recovery
