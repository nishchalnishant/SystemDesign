> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** Amazon's proprietary NoSQL database that guarantees single-digit millisecond responses, no matter how massive your data gets.
>
> **Key topics:**
> - **The Problem:** Relational databases (like PostgreSQL) get slower as they get bigger. Amazon needed a database that runs at the exact same speed whether it holds 1 Gigabyte or 100 Petabytes of data.
> - **Partition Keys (The Aisle Number):** How DynamoDB guarantees O(1) performance. It chops your data into physical partitions. You *must* provide the exact Partition Key to find your data.
> - **Sort Keys (The Shelf Number):** Once you are in the correct Aisle, you can scan the items on the shelf using a Sort Key (e.g., "Find all orders in Aisle 5 between Jan 1st and Feb 1st").
> - **Provisioned Capacity (RCUs/WCUs):** You don't rent a "Server" from Amazon. You rent "Read Capacity Units" and "Write Capacity Units". If you go over your limit, Amazon throttles your database instantly.
> - **GSI (Global Secondary Index):** If you want to search by something other than the Partition Key, you have to pay Amazon to secretly copy all your data into a brand new table with a different Partition Key.
>
> **Key takeaway:** DynamoDB forces you to design your database backwards. You must know exactly what your search queries will be *before* you are allowed to design your tables.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, databases, nosql, aws]
---
# DynamoDB Internals - System Design Guide

> This guide explains the magic and frustration of AWS DynamoDB using simple analogies.

---

## Why Should I Care?

In a normal SQL database (like PostgreSQL), you just dump all your data into tables. Later, when the Product Manager says, "Can we add a search bar that filters users by Age and Zip Code?", you just write a `SELECT` query, add an Index, and it works.

If you try to do that in **Amazon DynamoDB**, it is literally impossible.

DynamoDB is a NoSQL Key-Value store. It is designed to be the fastest database on Earth at massive scale (Amazon.com uses it to handle 100 million requests per second on Prime Day).
To achieve this speed, DynamoDB removes all flexibility.
**You cannot do SQL `JOIN`s.**
**You cannot do complex `WHERE` filters.**

If you don't design your DynamoDB tables perfectly on Day 1, you will have to delete your entire database and start over.

---

## Partition Keys (The Warehouse Aisle)

To guarantee single-digit millisecond response times, DynamoDB chops your data across hundreds of physical servers (Partitions).

> ** Analogy:** Imagine a massive Amazon warehouse with 10,000 Aisles.
> If you ask a worker to "Find a red shirt", they will refuse. They will not walk down 10,000 Aisles for you. (DynamoDB forbids Full Table Scans).
> You MUST tell the worker exactly which Aisle the item is in.

This is the **Partition Key** (PK).
When you create a table, you declare a PK (e.g., `User_ID`). When you want to read data, you *must* provide the exact `User_ID`. DynamoDB uses a math formula (Hashing) to turn the `User_ID` into an Aisle number. It jumps instantly to that Aisle. `O(1)` performance.

---

## Sort Keys (The Shelf Number)

If you only use a Partition Key, you can only fetch one specific item. What if you want to fetch a list of things? (e.g., "All the orders for User 500").

You add a **Sort Key** (SK).
> ** Analogy:** The Partition Key takes the worker to Aisle 500. The Sort Key is the exact numerical order of the items on the shelf in that Aisle (e.g., sorted by `Order_Date`).
> The worker walks to Aisle 500, finds the `Jan 1st` marker on the shelf, and scoops up everything until the `Feb 1st` marker.

This is the ONLY way you are allowed to do "Range Queries" in DynamoDB.
A query must always look like this: `PK = "User_123" AND SK > "2024-01-01"`.

---

## 🪞 GSIs (Global Secondary Indexes)

What happens if your Partition Key is `User_ID`, but your boss asks you to find a user by their `Email_Address`?

Because `Email_Address` is not the Partition Key, DynamoDB has no idea which Aisle the data is in. It will refuse to run the query.
To fix this, you must create a **Global Secondary Index (GSI)**.

> ** Analogy:** You pay Amazon to build a second, identical warehouse right next door. But in this warehouse, the items are organized into Aisles based on `Email_Address` instead of `User_ID`.
> Every time you write a piece of data to Warehouse 1, Amazon secretly runs next door and copies it into Warehouse 2.

**The Catch:** You have to pay double the storage costs, and double the Write costs. In DynamoDB, flexibility is extremely expensive.

---

## Interview Questions to Practice

1. **"What is the difference between a Partition Key and a Sort Key in DynamoDB?"**
   *Answer:* The Partition Key (Hash Key) determines the physical server/partition where the data is stored; queries must provide an exact match for the Partition Key. The Sort Key (Range Key) physically sorts the items sharing the same Partition Key; you can perform range queries (>, <, BETWEEN, BEGINS_WITH) on the Sort Key.
2. **"Why do people say you must 'model your queries, not your data' when using DynamoDB?"**
   *Answer:* Because DynamoDB does not support SQL JOINs or complex ad-hoc filtering across partitions. To maintain O(1) performance at scale, you must know every exact access pattern (read/write query) your application will need *before* you design the table, and you structure your Partition/Sort Keys specifically to answer those exact queries.
3. **"What is a GSI (Global Secondary Index) and what is the cost of using it?"**
   *Answer:* A GSI allows you to query a table using a completely different attribute as the Partition Key. The cost is that DynamoDB physically duplicates your data into a hidden secondary table in the background. You must pay for the additional storage, and you must provision separate Read and Write Capacity Units (RCUs/WCUs) for the GSI, significantly increasing your AWS bill.

---

# 🎯 SDE-3 Deep Dive

PK/SK/GSI is the API surface. Seniors get asked about **the partition-throughput math that actually throttles you, the consistency and transaction model, LSI vs GSI trade-offs, and the single-table-design pattern that separates people who've run DynamoDB at scale from those who've read the docs.**

## The throttling model is per-partition, not per-table

The headline "elastic, single-digit ms" hides the real constraint: each physical partition caps at **~3000 RCU / 1000 WCU / ~10GB**. Your provisioned capacity is *spread across* partitions, so:

- A **hot partition** (all traffic to one PK, e.g., a celebrity, or `status=ACTIVE` as PK) throttles even though the *table's* total capacity is far from exhausted. This is the #1 DynamoDB production failure.
- **Adaptive capacity** now auto-borrows unused capacity for a hot partition, and **burst capacity** banks unused throughput — but neither saves a genuinely skewed key.
- The senior fix: **write sharding** — append a suffix (`user#42#3` for shard 0–N) to spread a hot key across partitions, then scatter-read across the suffixes. Same trick as the hot-partition fix in [`../../03-scaling/03-database-scaling.md`](../../03-scaling/03-database-scaling.md).

Also know the **capacity modes:** *provisioned* (cheap if predictable, throttles on spikes unless autoscaled) vs *on-demand* (pay-per-request, absorbs spikes, ~5–7× the per-request cost). On-demand for spiky/unknown traffic; provisioned + autoscaling for steady load.

## Consistency and transactions

- **Reads are eventually consistent by default** (may read a stale replica); pass `ConsistentRead=true` for a **strongly consistent read** at 2× RCU cost and no cross-region guarantee. GSIs are **always eventually consistent** — you can't do a strongly consistent read on a GSI.
- **`TransactWriteItems`** gives ACID across up to 100 items / multiple tables via two-phase commit — but at **2× WCU** and it fails the whole batch on any conflict. Use it for the rare cross-item invariant, not routinely.
- **Conditional writes** (`ConditionExpression`) are the idiomatic optimistic-concurrency primitive: `PutItem ... IF attribute_not_exists(PK)` is an atomic compare-and-set — the basis for idempotency and optimistic locking without a transaction.

## LSI vs GSI — a real trade

| | **GSI** | **LSI** |
|---|---|---|
| Partition key | *Different* PK | **Same PK**, different sort key |
| Consistency | Eventual only | Strongly-consistent reads allowed |
| Capacity | Own RCU/WCU | Shares the base table's |
| Created | Anytime | **Only at table creation** |
| Partition size limit | None | Item collection capped at **10GB** |

LSI when you need a strongly-consistent alternate sort within one PK; GSI for any other access pattern (the common case).

## Single-table design — the pattern

Because there are no JOINs, the advanced pattern is to store **multiple entity types in one table** with generic `PK`/`SK` attributes and overloaded keys (`USER#123` / `ORDER#456`), so one query fetches a heterogeneous item collection (a user *and* their orders) in a single round trip. This is how you model relationships without JOINs. It's also why DynamoDB's other primitives matter:

- **DynamoDB Streams** — a change log (CDC) of every item mutation, feeding Lambda/analytics/search indexing. This is the outbox/CDC mechanism for the DynamoDB world; see [`../01-distributed-architecture/07-outbox-cdc-pattern.md`](../01-distributed-architecture/07-outbox-cdc-pattern.md).
- **TTL** — auto-expire items (session/cache use) with no delete cost.

## Under the hood: it's a Dynamo-lineage system

DynamoDB descends from the Dynamo paper: **consistent hashing** for partition placement, **replication across 3 AZs**, quorum-style writes, and leaderless-ish availability. Knowing this lets you connect it to the CAP/quorum theory in [`../../02-building-blocks/03-data-partitioning/02-replication.md`](../../02-building-blocks/03-data-partitioning/02-replication.md) — it chose AP-leaning availability with tunable read consistency.

## Interview probes you should survive

- *"Your table has plenty of provisioned capacity but requests are throttled — why?"* → Hot partition: one PK (or low-cardinality key) concentrates traffic; per-partition limits bind before table limits. Write-shard the key.
- *"Can you read your own write immediately?"* → Only with `ConsistentRead=true` on the base table (2× RCU). Default and *all* GSI reads are eventually consistent.
- *"How do you model a user and their orders without a JOIN?"* → Single-table design: same PK (`USER#123`), different SK prefixes (`PROFILE`, `ORDER#...`); one query returns the whole item collection.
- *"How do you keep a search index / analytics store in sync with DynamoDB?"* → DynamoDB Streams (CDC) → Lambda → OpenSearch/warehouse. Async, at-least-once, idempotent consumers.
- *"On-demand vs provisioned?"* → Provisioned + autoscaling for steady, predictable load (cheaper); on-demand for spiky/unpredictable traffic that would otherwise throttle.

---

## Applied In

This concept is used by **2 problems** in this repo:

**High-Level Design**

- [Design a Distributed Key-Value Store](../../05-hld-problems/01-easy/key-value-store.md)
- [Design a URL Shortener (Bitly)](../../05-hld-problems/01-easy/url-shortener.md)

