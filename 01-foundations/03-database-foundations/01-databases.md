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
