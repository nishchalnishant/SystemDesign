# CAP Theorem & ACID & BASE

> **Source**: Videos #13, #60, #65 from the playlist
> - CAP Theorem Simplified
> - ACID Properties in Databases With Examples
> - KISS, SOLID, CAP, BASE: Important Terms You Might Not Know!

---

## CAP Theorem

In a **distributed system**, you can only guarantee **two out of three**:

| Property | Definition |
|---|---|
| **C** — Consistency | Every read gets the most recent write |
| **A** — Availability | Every request gets a response (even if stale) |
| **P** — Partition Tolerance | System continues despite network failures |

### The Reality
- **Partition tolerance is non-negotiable** in distributed systems (networks WILL fail)
- So the real choice is: **CP** or **AP**

| Choice | Behavior During Partition | Examples |
|---|---|---|
| **CP** | Returns error or waits (sacrifices availability) | ZooKeeper, HBase, MongoDB, Redis (cluster) |
| **AP** | Returns potentially stale data (sacrifices consistency) | Cassandra, DynamoDB, CouchDB |
| **CA** | Only works without partitions (single node) | Traditional RDBMS (single server PostgreSQL) |

---

## ACID Properties (SQL/Relational Databases)

| Property | Description | Example |
|---|---|---|
| **Atomicity** | All-or-nothing transactions | Transfer $100: debit AND credit both succeed or both fail |
| **Consistency** | DB moves from one valid state to another | Constraints and rules always enforced |
| **Isolation** | Concurrent transactions don't interfere | Two users booking same seat — only one succeeds |
| **Durability** | Committed data survives crashes | Data written to disk before confirming |

### Isolation Levels (Weakest → Strongest)
1. **Read Uncommitted**: Can see uncommitted data (dirty reads)
2. **Read Committed**: Only see committed data
3. **Repeatable Read**: Same query returns same results within a transaction
4. **Serializable**: Transactions appear to execute serially (strongest, slowest)

---

## BASE Properties (NoSQL/Distributed Databases)

| Property | Description |
|---|---|
| **Basically Available** | System guarantees availability (may return stale data) |
| **Soft State** | State may change over time even without input |
| **Eventually Consistent** | System will become consistent given enough time |

### ACID vs BASE

| ACID | BASE |
|---|---|
| Strong consistency | Eventual consistency |
| Pessimistic (lock first) | Optimistic (resolve conflicts later) |
| Complex, slower at scale | Simple, highly scalable |
| SQL databases | NoSQL databases |

---

## Other Important Principles

### KISS (Keep It Simple, Stupid)
- Simplicity should be a key design goal
- Avoid unnecessary complexity

### SOLID (Object-Oriented Design)
| Principle | Description |
|---|---|
| **S** — Single Responsibility | A class should have one reason to change |
| **O** — Open/Closed | Open for extension, closed for modification |
| **L** — Liskov Substitution | Subtypes must be substitutable for their base types |
| **I** — Interface Segregation | Many specific interfaces > one general interface |
| **D** — Dependency Inversion | Depend on abstractions, not concretions |

### DRY (Don't Repeat Yourself)
- Every piece of knowledge should have a single representation

### YAGNI (You Aren't Gonna Need It)
- Don't build features until you actually need them
