# Database Types & SQL vs NoSQL

> **Source**: Videos #30, #35, #42, #100 from the playlist
> - SQL vs NoSQL: 5 Critical Differences
> - 8 Most Popular Database Management Systems
> - Which Database Should You Use?
> - Database Replication Patterns - Reading From Replicas

---

## SQL vs NoSQL: Critical Differences

| Aspect | SQL | NoSQL |
|---|---|---|
| **Data Model** | Tables with rows and columns | Documents, key-value, graph, wide-column |
| **Schema** | Rigid, predefined | Flexible, schema-less |
| **Scaling** | Vertical (scale up) | Horizontal (scale out) |
| **Transactions** | ACID (strong consistency) | BASE (eventual consistency) |
| **Joins** | Native support | Application-level or none |
| **Query Language** | SQL (standardized) | Database-specific APIs |
| **Best For** | Structured data, complex queries | Unstructured data, high scale |

---

## 8 Popular Database Systems

### Relational (SQL)
| Database | Strengths |
|---|---|
| **PostgreSQL** | Feature-rich, extensible, JSON support, open-source |
| **MySQL** | Widely used, read-heavy workloads, mature ecosystem |
| **SQL Server** | Enterprise, .NET integration, analytics |
| **Oracle** | Enterprise, advanced features, RAC clustering |

### Non-Relational (NoSQL)
| Database | Type | Best For |
|---|---|---|
| **MongoDB** | Document | Flexible schemas, rapid development |
| **Redis** | Key-Value | Caching, sessions, real-time data |
| **Cassandra** | Wide-Column | Write-heavy, time-series, IoT |
| **Neo4j** | Graph | Social networks, recommendations, knowledge graphs |

### Specialized
| Database | Type | Best For |
|---|---|---|
| **Elasticsearch** | Search Engine | Full-text search, log analytics |
| **InfluxDB** | Time-Series | Metrics, monitoring, IoT |
| **DynamoDB** | Key-Value (managed) | Serverless, predictable scale |
| **CockroachDB** | Distributed SQL | Global ACID transactions |

---

## Which Database to Choose?

```
Need complex queries and joins?          → SQL (PostgreSQL)
Need flexible schema and fast dev?       → MongoDB
Need extreme write throughput?           → Cassandra
Need sub-ms latency caching?             → Redis
Need full-text search?                   → Elasticsearch
Need relationships/graph queries?        → Neo4j
Need time-series data?                   → InfluxDB / TimescaleDB
Need global distribution + SQL?          → CockroachDB / Spanner
Need serverless with auto-scaling?       → DynamoDB
```

---

## Database Replication Patterns

### Single-Leader Replication
```
Client writes → Primary → replicate → Replica 1 (reads)
                                    → Replica 2 (reads)
                                    → Replica 3 (reads)
```
- One primary handles all writes
- Replicas handle reads
- **Replication lag** → eventual consistency

### Multi-Leader Replication
```
Primary A ←→ Primary B ←→ Primary C
   ↓             ↓             ↓
Replicas      Replicas      Replicas
```
- Multiple primaries accept writes
- Used for **multi-datacenter** setups
- **Conflict resolution** needed (last-write-wins, merge)

### Leaderless Replication
```
Client → writes to N nodes simultaneously
Client → reads from N nodes, takes majority
```
- No single leader; any node can accept writes
- **Quorum**: Read R + Write W > N ensures consistency
- **Used by**: Cassandra, DynamoDB, Riak

### Reading from Replicas: Considerations
- **Replication lag**: Replicas may have stale data
- **Read-after-write consistency**: User should see their own writes
- **Monotonic reads**: Don't read older data after newer data
- **Solutions**: Sticky sessions, read-from-primary for recent writes
