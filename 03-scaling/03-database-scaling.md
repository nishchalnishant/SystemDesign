> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** The step-by-step checklist to follow when your database starts getting slow.
>
> **Key topics:**
> - **Step 1: Indexing.** Creating a Table of Contents so the database doesn't have to read every single page to find an answer.
> - **Step 2: Caching.** Putting the answers to the most common questions on a sticky note (Redis) so you don't even have to ask the database.
> - **Step 3: Read Replicas.** Making 3 copies of the database so 3 different people can read from it at the exact same time.
> - **Step 4: Sharding.** Chopping the database in half when the hard drive gets completely full. (The absolute last resort).
>
> **Key takeaway:** Never jump straight to Sharding. In an interview, always solve database bottlenecks in this exact order: Index -> Cache -> Replicate -> Shard.

---
module: 03-scaling
status: unread
tags: [03-scaling, system-design, databases]
---
# Database Scaling Evolution - System Design Guide

> This guide explains the exact order of operations to scale a database using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

In a System Design interview, the interviewer will say: "We just launched our app, and the Database is too slow. How do you fix it?"

If your first answer is, "I will shard the database," you will fail the interview. Sharding is a nightmare of complexity. 
You must treat database scaling like a staircase. You start with the easiest, cheapest trick. When that stops working, you move up to the next step. 

Here is the exact 4-step staircase you must follow.

---

## 🪜 Step 1: Indexing (The Table of Contents)

Your database is just a giant book. When a user asks, "Find all users named Alex", the database has to read page 1, page 2, page 3... all the way to page 1,000. This is called a **Full Table Scan**, and it is painfully slow.

> **💡 Analogy:** Instead of reading every page of the book, you build a "Table of Contents" (An Index) at the back of the book. 
> You look at the "A" section, find "Alex", and it says "Page 42". You skip directly to Page 42.

- **The Fix:** Add a B-Tree Index to your database column (`CREATE INDEX idx_name ON users(name);`).
- **The Catch:** Every time you add a new user to the database, you have to update the Table of Contents. Indexes make Reads 100x faster, but they make Writes slightly slower. 

## 🪜 Step 2: Caching (The Sticky Note)

Okay, you added an Index. But now you have 1 million users asking for the same data over and over. Even with an index, the database CPU is struggling to keep up.

> **💡 Analogy:** Instead of looking up the same answer in the book 1 million times, you write the answer on a sticky note and put it on your desk. 

- **The Fix:** Put Redis or Memcached in front of the database. When the first user asks for the Top 10 High Scores, the database calculates it. You save the answer in Redis. The next 999,999 users get the answer instantly from Redis without ever touching the database.

## 🪜 Step 3: Read Replicas (The Photocopies)

Okay, you have a Cache. But you are building Twitter. People are writing 10,000 tweets a second, and reading 100,000 tweets a second. The single database server is catching on fire.

> **💡 Analogy:** You hire a Head Chef (Primary Database). The Head Chef only writes new recipes. You hire 3 Sous Chefs (Replicas). The Head Chef hands photocopies of the recipes to the Sous Chefs. If a customer asks to read a recipe, they ask the Sous Chefs. 

- **The Fix:** Use Primary-Replica Replication. You point all your `INSERT/UPDATE` code to the Primary Database. You point all your `SELECT` code to the 3 Replicas. You instantly triple your Read capacity!

## 🪜 Step 4: Sharding (The Last Resort)

Okay, you have Indexes, Caches, and 10 Read Replicas. But you have 10 Terabytes of data, and your physical hard drive can only hold 8 Terabytes. The physical metal disk is 100% full.

> **💡 Analogy:** You build a second library building. A-M in Building 1, N-Z in Building 2.

- **The Fix:** You shard the database. You split the users in half across two completely separate Primary databases. 
- **The Catch:** You can no longer run `JOIN` queries across all users easily. Your code becomes a nightmare to maintain. Only do this if you have no other choice!

---

## 🎤 Interview Questions to Practice

1. **"A database is performing slowly on SELECT queries. What is the very first thing you should check?"**
   *Answer:* I would check if the queries are using Indexes. A missing index causes a Full Table Scan, which destroys performance. I would use the `EXPLAIN` command in SQL to see the query execution plan.
2. **"If adding an Index doesn't solve the read performance issue, what is your next step?"**
   *Answer:* I would introduce a Caching layer (like Redis) to serve the most frequent, read-heavy queries from memory, completely bypassing the database.
3. **"When do you know it is finally time to Shard a database?"**
   *Answer:* You only shard when you hit hard physical limits that replication and caching cannot solve. Specifically: when your total dataset size exceeds the storage capacity of a single machine, or when your Write-throughput (INSERTs/UPDATEs) is so massive that a single Primary database CPU cannot process them fast enough.
