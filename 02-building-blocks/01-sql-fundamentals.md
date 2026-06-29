---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, building-blocks, sql, database]
---
# SQL Fundamentals for System Design

> **The relational database primitives that come up in every HLD deep-dive: ACID, isolation, locking, and indexes.**

---

## File Mindmap

```
SQL Fundamentals
├── ACID Properties
│   ├── Atomicity — all or nothing
│   ├── Consistency — constraints always satisfied
│   ├── Isolation — concurrent transactions don't corrupt each other
│   └── Durability — committed data survives crashes
├── Isolation Levels
│   ├── Read Uncommitted → dirty reads possible
│   ├── Read Committed → no dirty reads; non-repeatable reads possible
│   ├── Repeatable Read → no dirty/non-repeatable reads; phantom reads possible (MySQL default)
│   └── Serializable → full isolation; highest cost
├── Locking
│   ├── Row-level lock (SELECT FOR UPDATE)
│   ├── Table lock
│   ├── Shared vs Exclusive locks
│   └── Deadlock detection and prevention
├── Indexes
│   ├── B-Tree — range queries, equality, ORDER BY
│   ├── Hash — equality only
│   ├── Composite — column order matters
│   └── Covering index — query satisfied from index alone
└── When to Use SQL vs NoSQL
```

---

## 1. ACID Properties

Every relational database guarantees ACID. Know what each means and when it breaks.

### Atomicity
A transaction is all-or-nothing. If any statement fails, the entire transaction rolls back.

```sql
BEGIN;
  UPDATE accounts SET balance = balance - 100 WHERE id = 'A';
  UPDATE accounts SET balance = balance + 100 WHERE id = 'B';
COMMIT;
-- If the second UPDATE fails, the first is rolled back. No money is lost.
```

**Why it matters in interviews**: Payment systems, booking confirmations, inventory decrements — anywhere two writes must succeed together.

### Consistency
After any transaction, the database remains in a valid state — all constraints, foreign keys, and triggers hold.

**Why it matters**: If you debit account A but fail to credit B (violating a business invariant), the DB itself can't enforce that — your application logic or a constraint must.

### Isolation
Concurrent transactions appear to execute serially. The degree of isolation is configurable (see Isolation Levels below).

**Why it matters**: Two users booking the same seat, two services decrementing the same inventory count — isolation determines whether they corrupt each other.

### Durability
Once a transaction commits, it survives crashes. The write-ahead log (WAL) ensures this — data is flushed to disk before the commit acknowledges.

**Why it matters**: Guarantees that a confirmed booking stays confirmed even if the DB crashes immediately after the COMMIT.

---

## 2. Isolation Levels

PostgreSQL and MySQL support four isolation levels. Default differs by engine.

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Cost |
|-------|-----------|---------------------|--------------|------|
| Read Uncommitted | ✅ possible | ✅ possible | ✅ possible | Lowest |
| Read Committed | ✗ | ✅ possible | ✅ possible | Low |
| Repeatable Read | ✗ | ✗ | ✅ possible (✗ in MySQL InnoDB) | Medium |
| Serializable | ✗ | ✗ | ✗ | Highest |

**Defaults**: PostgreSQL = Read Committed. MySQL InnoDB = Repeatable Read.

### What each anomaly means

**Dirty read**: Transaction A reads data written by Transaction B before B commits. If B rolls back, A read invalid data.

**Non-repeatable read**: Transaction A reads a row, Transaction B updates and commits it, A reads the same row again and sees a different value.

**Phantom read**: Transaction A queries rows matching a condition, Transaction B inserts a new matching row and commits, A re-queries and sees an extra row that wasn't there before.

### Which level to use

- **Read Committed**: OLTP applications where each statement should see the latest committed data. Default for most apps.
- **Repeatable Read**: Reports or aggregations where consistent snapshot within a transaction matters.
- **Serializable**: Financial transactions, seat booking — anywhere "read then write" must be fully safe. Use sparingly; it serializes all conflicting transactions.

---

## 3. Locking

### SELECT FOR UPDATE (Pessimistic Locking)

Acquires an exclusive row lock at read time. Other transactions that try to read the same row with `SELECT FOR UPDATE` will block until the lock is released.

```sql
BEGIN;
  SELECT * FROM seats WHERE seat_id = 'A12' AND show_id = 'S99' FOR UPDATE;
  -- Now we hold an exclusive lock on this row
  -- No other transaction can lock or modify this row until we COMMIT/ROLLBACK
  UPDATE seats SET status = 'BOOKED', user_id = 'U42' WHERE seat_id = 'A12' AND show_id = 'S99';
COMMIT;
```

**Use case**: BookMyShow seat booking, hotel room reservation, inventory decrement — anywhere "check then update" must be atomic.

**Cost**: High contention on popular rows (e.g., last seat at a concert) → all transactions queue up → throughput degrades. Mitigate with:
- Short transactions (lock for milliseconds, not seconds)
- Redis-based distributed locking for the hot path
- Optimistic locking for lower-contention scenarios

### SELECT FOR SHARE (Shared Lock)

Other transactions can read but not modify. Used when you need a consistent read that blocks writes.

```sql
SELECT * FROM accounts WHERE id = 'A' FOR SHARE;
```

### Optimistic Locking (No DB Lock)

Read data with a version column. On update, check that version hasn't changed. If it has, retry.

```sql
-- Read
SELECT balance, version FROM accounts WHERE id = 'A';
-- Got balance=1000, version=5

-- Update — fails if another transaction changed the row
UPDATE accounts
SET balance = 900, version = 6
WHERE id = 'A' AND version = 5;
-- If affected_rows = 0, someone else updated first → retry
```

**Use case**: Low-contention scenarios (user profile updates, settings). Bad for high-contention (seat booking — too many retries under load).

### Deadlock

Two transactions each hold a lock the other needs.

```
Transaction A: locks Row 1, waits for Row 2
Transaction B: locks Row 2, waits for Row 1
→ Neither can proceed
```

**DB behavior**: The database detects the cycle and rolls back one transaction (usually the one with the least work done). The application must retry.

**Prevention in application code**: Always acquire locks in the same order. If booking requires locking seats A and B, always lock them sorted by seat_id — never let one transaction lock A→B while another locks B→A.

---

## 4. Indexes

### B-Tree Index (default)

```sql
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_show_time ON shows(start_time);
```

Supports:
- Equality: `WHERE email = 'x@y.com'`
- Range: `WHERE start_time BETWEEN '2024-01-01' AND '2024-01-31'`
- Prefix: `WHERE name LIKE 'John%'` (not `'%John'`)
- `ORDER BY` without sort step

### Hash Index

Supports equality only. Faster for exact lookups, useless for range queries. PostgreSQL builds these automatically in memory for hash joins.

### Composite Index

```sql
CREATE INDEX idx_show_status ON seats(show_id, status);
```

**Column order matters**: The above index accelerates `WHERE show_id = X AND status = Y` and `WHERE show_id = X`, but NOT `WHERE status = Y` alone (leading column must be present).

**Rule**: Put the most selective column (most unique values) first, unless the query pattern dictates otherwise.

### Covering Index

An index that contains all columns the query needs — no table lookup required.

```sql
CREATE INDEX idx_covering ON bookings(user_id, status, created_at);
-- This query is satisfied entirely from the index:
SELECT status, created_at FROM bookings WHERE user_id = 'U42';
```

**When to use**: High-frequency read queries on large tables where table lookups are the bottleneck.

### Index Trade-offs

| | Benefit | Cost |
|--|---------|------|
| Adding an index | Faster reads | Slower writes (index must be maintained on INSERT/UPDATE/DELETE) |
| Too many indexes | — | Write throughput drops significantly on write-heavy tables |
| No index on FK | — | JOIN performance degrades to full table scan |

**Rule of thumb**: Index foreign keys, columns in WHERE clauses of frequent queries, columns used in ORDER BY / GROUP BY. Do NOT index columns with very low cardinality (e.g., `status` with 3 possible values on a table with 1M rows — full index scan is not much better than full table scan).

---

## 5. Common Interview Patterns

### Double-Booking Prevention

```sql
-- Pessimistic: lock the seat row before checking
BEGIN;
SELECT * FROM seats WHERE seat_id = 'A12' AND show_id = 'S99' FOR UPDATE;
-- Check status = AVAILABLE in application code
UPDATE seats SET status = 'BOOKED' WHERE seat_id = 'A12' AND show_id = 'S99';
COMMIT;

-- Optimistic: conditional update
UPDATE seats
SET status = 'BOOKED', booked_by = 'U42'
WHERE seat_id = 'A12' AND show_id = 'S99' AND status = 'AVAILABLE';
-- Check affected_rows = 1; if 0, seat was taken → return error
```

### Inventory Decrement (Atomic)

```sql
UPDATE inventory
SET quantity = quantity - 1
WHERE product_id = 'P1' AND quantity > 0;
-- affected_rows = 0 means out of stock
```

### Idempotent Insert (Deduplication)

```sql
-- PostgreSQL
INSERT INTO payments(payment_id, amount, status)
VALUES ('PAY-123', 100.0, 'COMPLETED')
ON CONFLICT (payment_id) DO NOTHING;

-- MySQL
INSERT IGNORE INTO payments(payment_id, amount, status)
VALUES ('PAY-123', 100.0, 'COMPLETED');
```

---

## 6. SQL vs NoSQL — The One-Paragraph Decision Rule

Use **SQL (PostgreSQL / MySQL / Aurora)** when:
- You need ACID transactions (payments, bookings, inventory)
- Data relationships exist and JOINs are natural
- Schema is stable and well-defined
- Write volume is < ~10K/sec (single primary)

Use **DynamoDB / Cassandra** when:
- Write volume exceeds what a single primary can handle
- Access pattern is known and simple (no ad-hoc JOINs)
- You can design a partition key that distributes load evenly
- You accept eventual consistency or can work around it

**The trap**: Teams move to NoSQL for "scale" before they need it, then spend months re-implementing transactions and consistency that SQL gave them for free.

---

## Quick Revision

| Concept | One Line |
|---------|----------|
| Atomicity | All writes in a transaction succeed or none do |
| Isolation | Concurrent transactions don't see each other's partial state |
| SELECT FOR UPDATE | Exclusive row lock held until transaction ends |
| Optimistic locking | Version column; retry if someone else updated first |
| Deadlock | Detected by DB; one transaction rolled back; app retries |
| B-Tree index | Equality + range + ORDER BY; default index type |
| Composite index | Leading column must be in WHERE clause to be used |
| Covering index | All needed columns in index; avoids table lookup |

---

## Interview Questions Asked

### Conceptual
- What does "serializable isolation" actually mean? Why is it expensive?
- What's the difference between a dirty read and a phantom read?
- When would you choose optimistic over pessimistic locking?

### Scenario / Design
- Two users try to book the last seat simultaneously. Walk me through exactly how your DB prevents double-booking.
- Your inventory decrement is causing deadlocks under high traffic. How do you fix it?
- You have a `payments` table with 500M rows. How do you design indexes for: (1) lookup by user, (2) lookup by payment_id, (3) monthly reports by date range?

### See Also
- `02-building-blocks/sharding.md` — when writes exceed single-node SQL capacity
- `02-building-blocks/replication.md` — read replicas for read scaling
- `07-interview-templates/database-selection-tree.md` — SQL vs NoSQL decision tree
