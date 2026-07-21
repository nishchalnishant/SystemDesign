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

## Applied In

This concept is used by **2 problems** in this repo:

**High-Level Design**

- [Design a Distributed Key-Value Store](../../05-hld-problems/01-easy/key-value-store.md)
- [Design a URL Shortener (Bitly)](../../05-hld-problems/01-easy/url-shortener.md)

