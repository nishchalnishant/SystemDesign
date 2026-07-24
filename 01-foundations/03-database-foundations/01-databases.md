> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** Everything you need to know about Databases for System Design. 
>
> **Key topics:**
> - **SQL vs NoSQL:** SQL is a strict Excel spreadsheet (perfect for banking). NoSQL is a flexible folder of documents (perfect for social media).
> - **ACID vs BASE:** ACID ensures perfection (if a bank transfer fails halfway, it rolls back entirely). BASE accepts chaos for speed (it's okay if your Instagram like-count is a few seconds behind).
> - **The CAP Theorem:** During a network outage, you must choose between staying online (Availability) or freezing the system to protect data accuracy (Consistency).
> - **Indexes:** The database's table of contents. It makes reading 100x faster, but writing 2x slower.
> - **Sharding & Replication:** Replication is copying your database so if one dies, another takes over. Sharding is cutting a massive database in half because it's too big to fit on one computer.
>
> **Key takeaway:** Choosing the wrong database is the most expensive mistake you can make. Always choose based on what your app needs to do most: read fast, write fast, or never lose money.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Databases - System Design Guide

> This guide explains the core concepts of databases, how to choose the right one, and how they scale, using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you run a bank. User A has $100. User A tries to buy two $100 TVs at the exact same millisecond. 
If your database isn't built correctly, the computer might check the balance for Purchase 1 (it's $100!), check the balance for Purchase 2 (it's $100!), approve both, and suddenly the user has a negative balance. 

Databases aren't just "places to save text." They are complex pieces of software designed to prevent data corruption, handle millions of users saving data at the exact same time, and quickly search through petabytes of information without freezing. 

If you choose the wrong database architecture, your app will either be painfully slow, or worse, silently lose your users' data.

---

## 🏗️ SQL vs NoSQL

The biggest choice you will make is whether to use a Relational Database (SQL) or a Non-Relational Database (NoSQL).

### SQL (Relational Databases)
> **💡 Analogy: A strict Excel Spreadsheet.**  
> Before you can type anything, you must define the columns (ID, Name, Age). Every row MUST follow those rules. If you try to add a column for "Favorite Color" to one row, the database stops you. 
> You can also link spreadsheets together. A user ID in the "Orders" sheet must exist in the "Users" sheet. 

- **Pros:** Extremely strict rules (Data Integrity). Perfect for financial apps, e-commerce, and anything where data *must* be perfectly structured.
- **Cons:** Hard to scale across multiple servers (Vertical scaling preferred).
- **Examples:** PostgreSQL, MySQL, Oracle.

### NoSQL (Non-Relational Databases)
> **💡 Analogy: A flexible filing cabinet.**  
> You can just throw a manila folder into the cabinet. One folder might have a Name and Age. The next folder might have a Name, Favorite Color, and a list of 10 hobbies. The cabinet doesn't care.

- **Pros:** Incredibly flexible. Because the data isn't strictly linked, it's very easy to split the cabinet across 100 different servers (Horizontal scaling). 
- **Cons:** It's up to your application code to make sure the data makes sense. 
- **Examples:** MongoDB, DynamoDB, Cassandra.

---

## ⚖️ ACID vs BASE (The Rule of Consistency)

How does a database handle a power outage in the middle of a save?

### ACID (Used by SQL)
ACID stands for **Atomicity, Consistency, Isolation, Durability**. 
> **💡 Analogy:** A vending machine. You put money in, you press the button, the machine drops the snack. If the power cuts out right as the snack falls, the machine *cancels the entire transaction* and drops your coins back in the return slot. **It's all or nothing.**
- **Use for:** Bank transfers, billing, inventory counts.

### BASE (Used by NoSQL)
BASE stands for **Basically Available, Soft state, Eventually consistent**.
> **💡 Analogy:** Depositing a check at an ATM. The ATM says "Deposited!" instantly (Highly Available). But if you check your balance on your phone 2 seconds later, the money isn't there yet. The bank promises it will *eventually* show up by tomorrow morning (Eventually Consistent). 
- **Use for:** Social media feeds, YouTube view counts, sensor data. (It's okay if a view count is delayed by 5 seconds).

---

## 📚 Types of NoSQL Databases

If you don't use SQL, there are 4 main flavors of NoSQL to choose from:

1. **Document Databases (MongoDB, Couchbase)**
   - *Analogy:* A folder of JSON documents. 
   - *Best for:* User profiles, product catalogs.
2. **Key-Value Stores (Redis, Memcached)**
   - *Analogy:* A coat-check counter. You hand in a ticket number (Key), you instantly get your coat back (Value). Lightning fast.
   - *Best for:* Caching, session storage, shopping carts.
3. **Wide-Column Stores (Cassandra, HBase)**
   - *Analogy:* A massive spreadsheet designed specifically for writing data incredibly fast, but reading it is a bit harder.
   - *Best for:* Time-series data, IoT sensor readings, logging.
4. **Graph Databases (Neo4j)**
   - *Analogy:* A map of relationships. "User A knows User B who bought Product C."
   - *Best for:* Social networks, recommendation engines, fraud detection.

---

## 🔎 Indexes (How to search fast)

**The Problem:** You have 1 billion users. You want to find the user with the username "JohnDoe". By default, the database has to read every single row one-by-one until it finds John. This takes 5 seconds (A Full Table Scan).

**The Fix:** Create an Index on the "Username" column. 
> **💡 Analogy:** The index at the back of a textbook. Instead of reading the whole book to find the word "Photosynthesis", you look at the index, it says "Page 42", and you jump straight there.

**The Trade-off:** 
Indexes make *Reading* lightning fast. But they make *Writing* slower. Why? Because every time you add a new user, the database has to update the table *and* update the index. 
*Rule of thumb: Never index every column. Only index columns you frequently search by.*

---

## ✂️ Scaling: Sharding vs Replication

When your database gets too big for one computer, you have two tools:

### 1. Replication (Copying)
> **💡 Analogy:** Hiring 3 identical assistants. When the boss (Primary Database) gets a new memo, they copy it and hand it to the assistants (Replica Databases). If someone asks a question (a Read), the assistants can answer it. If someone needs to change a rule (a Write), they must talk to the boss.

- **Why use it?** If the Primary database crashes, a Replica instantly promotes itself to the new Primary (Availability). It also lets you handle millions of "Reads" per second by asking the replicas.

### 2. Sharding (Splitting)
> **💡 Analogy:** A library is out of shelf space. So, you buy a second building. Building A holds books A-M. Building B holds books N-Z. 

- **Why use it?** When your database is literally too big to fit on a 10 Terabyte hard drive, you *must* cut it in half. You put half the users on Server A, and half on Server B. 
- **The Catch:** Sharding is a nightmare. If you want to find "All users who like pizza," you now have to ask both servers, combine the results, and sort them. Avoid sharding until it is your absolute last resort.

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between SQL and NoSQL?"**
   *Answer:* SQL is a strict, relational table structure that guarantees ACID consistency (perfect for finance). NoSQL is flexible, document-based, and scales horizontally much easier, but usually sacrifices strict consistency for availability (perfect for social media).
2. **"What is the CAP Theorem?"**
   *Answer:* In a distributed database, if the network cable is cut between your servers (Partition), you have to choose. Do you stay online and potentially serve outdated data (Availability), or do you shut down the database to protect data integrity (Consistency)? You can't have both.
3. **"Why shouldn't I just create an index on every column?"**
   *Answer:* Because every index takes up physical hard drive space, and more importantly, it slows down writes. Every time you insert a row, the database has to update every single index.

---

# 🎯 SDE-3 Deep Dive

The section above is enough to *choose* a database. This section is what separates a senior answer: knowing the mechanisms — isolation levels, index internals, how ACID is actually enforced — well enough to reason about a concrete anomaly under load.

## Transaction isolation levels and the anomalies they prevent

"ACID" is not one thing — the **I (Isolation)** has levels, and each level permits specific anomalies. This is the single most common place a senior candidate is tested.

| Isolation level | Dirty read | Non-repeatable read | Phantom read | Cost |
|---|---|---|---|---|
| **Read Uncommitted** | ✅ possible | ✅ possible | ✅ possible | Cheapest |
| **Read Committed** (PG default) | ❌ prevented | ✅ possible | ✅ possible | Low |
| **Repeatable Read** (MySQL default) | ❌ | ❌ prevented | ✅ possible* | Medium |
| **Serializable** | ❌ | ❌ | ❌ prevented | Highest |

The anomalies:
- **Dirty read** — you read another transaction's *uncommitted* write, which may roll back.
- **Non-repeatable read** — you read a row twice in one transaction and get different values (someone committed an update between).
- **Phantom read** — you run the same `WHERE` query twice and get a different *set of rows* (someone inserted a matching row).
- **Write skew** — two transactions each read an overlapping set, make disjoint writes that are individually valid but jointly violate an invariant (e.g., two doctors both go off-call because each saw the other on-call). **Only Serializable prevents write skew** — this is the classic hard-mode question.

> *MySQL InnoDB's Repeatable Read prevents phantoms in practice via next-key (gap) locking, unlike the SQL standard's definition. Know this distinction; it's a favorite "gotcha."*

**How isolation is implemented:**
- **Pessimistic (2PL / two-phase locking):** acquire locks, hold to commit. Used by MySQL for Serializable. Blocks; risks deadlock.
- **Optimistic (MVCC — Multi-Version Concurrency Control):** each write creates a new version tagged with a transaction ID; readers see a consistent snapshot without blocking writers. Used by PostgreSQL and Oracle. "Readers don't block writers, writers don't block readers." The cost is version bloat, cleaned up by **VACUUM** (Postgres) or the undo log (MySQL).

## Index internals: B-Tree vs LSM-Tree

"Create an index" hides a fundamental storage-engine choice that dictates read/write performance.

| | **B+Tree** (Postgres, MySQL/InnoDB) | **LSM-Tree** (Cassandra, RocksDB, LevelDB) |
|---|---|---|
| Structure | Balanced tree, updated in place | In-memory memtable → flushed to immutable sorted files (SSTables) |
| **Reads** | Fast, predictable (O(log n), ~3–4 seeks) | Slower — may check memtable + several SSTables (mitigated by **Bloom filters**) |
| **Writes** | Slower — random in-place update, must find the page | **Fast** — sequential append to memtable, no seek |
| Write amplification | Lower | Higher (compaction rewrites data), but **sequential** I/O |
| Space | Fragmentation, some empty space per page | Compact; compaction reclaims space |
| Best for | Read-heavy, range scans, OLTP | Write-heavy ingest (time-series, logs, event streams) |

**The senior insight:** LSM turns random writes into sequential writes, which is why write-heavy stores (Cassandra) use it — sequential I/O is orders of magnitude faster on both SSD and HDD. The price is read amplification and **compaction** (background merging of SSTables), which competes for I/O and can cause latency spikes. B+Tree pays on every write to keep reads cheap. **Pick based on your read:write ratio.**

## How ACID is actually enforced

- **Atomicity + Durability** come from the **Write-Ahead Log (WAL)**: the change is appended to a sequential log and `fsync`'d *before* the data pages are updated. On crash, the DB replays the WAL to redo committed transactions and rolls back incomplete ones. This is also what physical replication ships (Postgres streaming replication = shipping the WAL).
- **Consistency** (the C in ACID — constraint consistency, *not* the CAP C) = enforcing declared invariants: foreign keys, unique constraints, check constraints. Your job is to declare them; the DB enforces them transactionally.
- **Isolation** = the levels above.

**The `fsync` cost:** durability requires the WAL to hit stable storage, and `fsync` is ~1–10ms on spinning disks. This is why databases **group-commit** — batching many transactions' log flushes into one `fsync` — to amortize the cost. It's also why "durable" and "fast" are in tension, and why some systems offer relaxed durability (`fsync` every N ms) as a knob.

## NoSQL data modeling: query-first, not entity-first

The real reason to reach for NoSQL is usually not "flexibility" — it's **access-pattern-driven modeling** for horizontal scale:
- **In SQL you normalize** (one fact in one place) and `JOIN` at read time. Joins don't shard — a cross-partition join is a scatter-gather.
- **In NoSQL you denormalize and duplicate** so each query hits **one partition**. You model the *table around the query*, not the entity. DynamoDB single-table design and Cassandra's "one table per query pattern" are this taken to its conclusion.
- **Partition key choice is the whole ballgame:** it must spread load evenly (avoid hot partitions) *and* colocate data you read together. A bad partition key (e.g., `country` for a US-heavy app) creates a hot shard that no amount of hardware fixes.

## Interview probes you should survive

- *"Two transactions both read a balance, both subtract $100 — how do you prevent the double-spend?"* → `SELECT ... FOR UPDATE` (pessimistic lock) or an optimistic version check / conditional update. At Read Committed the naive read-modify-write is a lost-update bug.
- *"You need Cassandra to serve a read-your-writes guarantee — how?"* → Tune consistency: `R + W > N` (e.g., N=3, W=QUORUM=2, R=QUORUM=2). Then any read overlaps any write on at least one replica.
- *"Your write-heavy service is I/O-bound on a B-Tree index — what do you change?"* → An LSM-based store converts random writes to sequential ones; or batch writes, or partition to spread the write hot spot. Name the compaction cost.
- *"What's the difference between the C in ACID and the C in CAP?"* → ACID-C is invariant/constraint preservation within a transaction. CAP-C is linearizability across nodes. Unrelated; conflating them is a red flag.
- *"When does Repeatable Read still bite you?"* → Write skew — disjoint writes that jointly break an invariant. Needs Serializable (or an explicit lock/constraint).
