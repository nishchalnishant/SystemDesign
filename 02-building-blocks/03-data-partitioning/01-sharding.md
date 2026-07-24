> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to split a massive database into smaller pieces when it gets too big for one computer.
>
> **Key topics:**
> - **The Problem:** Your database has 10 Terabytes of data. The biggest hard drive you can buy is 8 Terabytes. What do you do?
> - **The Solution (Sharding):** Cutting the database in half. Put 5 Terabytes on Server A, and 5 Terabytes on Server B. 
> - **The Shard Key:** How do you decide who goes where? If you shard by Last Name, all the A-M people go to Server A. If you shard by User ID, all even numbers go to Server A.
> - **The Hotspot Problem (The Celebrity Problem):** If Justin Bieber joins your app, and he is on Server A, his millions of followers will overwhelm Server A while Server B sits completely empty.
> - **The Catch:** Sharding ruins your ability to do complex searches (JOINs). You should avoid sharding until it is your absolute last resort.
>
> **Key takeaway:** Sharding is horizontally scaling your data. It solves storage limits, but it makes your application code 10x more complicated.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, data-partitioning]
---
# Sharding (Data Partitioning) - System Design Guide

> This guide explains how to chop databases into smaller pieces using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine a library that keeps growing. Eventually, the library is 100% full. You cannot fit a single new book inside the building. 

You have two choices:
1. **Vertical Scaling (Scaling Up):** Tear down the library and build a massive, 10-story mega-library in its place. (This is buying a bigger server with a 10TB hard drive). Eventually, you can't build any higher. 
2. **Horizontal Scaling (Scaling Out):** Buy the empty lot next door and build a second, identical library. Books A-M go in Building 1. Books N-Z go in Building 2. 

In System Design, this second choice is called **Sharding**. 
When your database gets too big to fit on one physical hard drive, or when you have so many users writing data that the CPU catches on fire, you *must* shard. You buy a second server, and you split the users between them.

---

## 🔑 How do we split them? (The Shard Key)

If you have 2 servers (Shard A and Shard B), how do you decide which server a user belongs to? You have to pick a **Shard Key**. 

### 1. Range-Based Sharding (Alphabetical)
> **💡 Analogy:** The library split. Books A-M in Building 1, N-Z in Building 2.
- **How it works:** You shard based on a range of values (e.g., User IDs 1 to 500 go to Shard A, User IDs 501 to 1000 go to Shard B).
- **The Problem:** Uneven traffic. If all your new users (IDs 501-1000) are super active, Shard B will crash while Shard A sits empty. 

### 2. Hash-Based Sharding (The Randomizer)
> **💡 Analogy:** Dealing cards to players. One for you, one for me, one for you, one for me.
- **How it works:** You take the User ID, run a math formula on it (a Hash), and it spits out a random but consistent number. Even IDs go to Shard A, Odd IDs go to Shard B.
- **Pros:** It guarantees the data is spread perfectly evenly across all your servers!
- **The Problem:** If you want to add a 3rd Shard later, all the math changes, and you have to move millions of users to different servers. (To fix this, we use *Consistent Hashing*).

---

## 🔥 The "Hotspot" Problem (The Celebrity Problem)

Even if you use a perfect math formula to spread your users evenly, you will still run into the Celebrity Problem.

Imagine you build a Twitter clone. You shard it evenly across 5 servers. 
Justin Bieber joins your app. He gets assigned to Server 3. 

Every time Justin Bieber posts a photo, 100 million people click it at the exact same second. All 100 million people are routed to Server 3. Server 3 instantly melts and explodes. Servers 1, 2, 4, and 5 are completely fine. 

This is called a **Hotspot**. The traffic is technically spread out evenly, but one specific piece of data is so insanely popular that it ruins the whole system. 
*(How do you fix it? You can't fix it with sharding. You have to put Justin Bieber's data in a Cache!)*

---

## 🚫 Why you should avoid Sharding

Sharding sounds great, but it is a nightmare for developers.

> **💡 Analogy:** Imagine you want to find "Every book about Dogs written in 1995." 
> When you had one library, you just asked the librarian, and they gave you the list. 
> Now that you have two libraries, you have to walk to Building 1, ask the librarian, get a list. Then walk to Building 2, ask the librarian, get a list. Then sit at a desk, merge the two lists together, and sort them alphabetically yourself. 

When you shard a database, **SQL JOINs break.** 
If you want to search for "All users who live in New York," your application code now has to query Shard A, query Shard B, wait for both to reply, combine the data in RAM, and sort it. 
It makes your code 10x more complicated. Only use Sharding when you absolutely have to!

---

## 🎤 Interview Questions to Practice

1. **"What is the difference between Replication and Sharding?"**
   *Answer:* Replication is copying the *exact same* data to multiple servers to handle more "Reads" and prevent data loss. Sharding is splitting the data into *different pieces* across multiple servers to handle more "Writes" and total storage size.
2. **"What is a Shard Key?"**
   *Answer:* It's the specific column (like User_ID or Region) used to mathematically determine which shard a specific row of data belongs to.
3. **"What is the Celebrity Problem (Hotspotting)?"**
   *Answer:* It occurs when one specific shard receives an overwhelming amount of traffic compared to the others, usually because a highly active user (a celebrity) or piece of data resides entirely on that single shard, negating the benefits of distributing the load.

---

# 🎯 SDE-3 Deep Dive

The above covers *why* and *how to split*. Senior questions are about **choosing the shard key, rebalancing without downtime, and the operations that break once you're sharded.**

## Choosing a shard key — the decision that's hardest to undo

A shard key is chosen on three axes; you rarely get all three:

| Property | What you want | Failure if you get it wrong |
|---|---|---|
| **High cardinality** | Many distinct values | Low cardinality (e.g. `country`) caps your max shard count |
| **Even distribution** | Uniform request + storage spread | Skew → hotspots (the celebrity problem) |
| **Query alignment** | Common queries hit *one* shard | Misaligned key → **scatter-gather** across all shards |

The tension: a key that distributes evenly (random hash of user_id) often *scatters* your queries; a key that co-locates related data (tenant_id) often *skews*. Senior answer names the trade and picks for the dominant access pattern. **Composite keys** (`tenant_id + hash(user_id)`) are the usual compromise — co-locate a tenant, spread users within it.

## Partitioning strategies compared

| Strategy | Rebalancing | Range scans | Hotspot risk |
|---|---|---|---|
| **Range** | Split/merge ranges — easy | ✅ efficient | High (monotonic keys like timestamps all hit the newest shard) |
| **Hash** | Move ~1/N on resize (naive) | ❌ scatter-gather | Low, unless a single key is hot |
| **Consistent hashing** | Move only ~1/N on resize | ❌ | Low; see [`03-consistent-hashing.md`](03-consistent-hashing.md) |
| **Directory/lookup** | Move anything, per-entry | ✅ flexible | Low; lookup table is a SPOF/bottleneck |

**Monotonic-key trap:** sharding by auto-increment ID or timestamp with *range* partitioning sends 100% of writes to the last shard. Fix: hash the key, or use a **hash + time** composite so writes spread.

## Rebalancing without downtime

- **Fixed partition count (Cassandra/Dynamo style):** create *many* more partitions than nodes up front (e.g. 256). Adding a node just reassigns whole partitions — no re-hashing, minimal data movement.
- **Consistent hashing with virtual nodes:** each physical node owns many points on the ring, so adding a node steals a slice from *every* existing node evenly instead of one neighbor.
- **The live migration dance:** dual-write to old+new shard → backfill historical rows → verify → flip reads → stop old writes. This is a multi-day operation at scale, done behind a feature flag with a rollback path.

## What sharding costs you

- **Cross-shard JOINs / aggregations** → application-side scatter-gather + merge. Denormalize instead.
- **Cross-shard transactions** → no single-node ACID; you need **2PC** (slow, blocking) or a **saga** (eventual, compensating actions). Design to keep a transaction within one shard.
- **Secondary indexes** → a global index is itself a distributed system. Choices: **local index** (per-shard, needs scatter-gather to query) vs **global index** (separate shard set, adds write latency).
- **Re-sharding** is the hardest op you'll run — pick the key so you never have to.

## Interview probes you should survive

- *"Your shard key is `user_id` but your hottest query is 'all orders in region X' — problem?"* → That query scatter-gathers every shard. Either add a region-sharded read replica/secondary index, or reconsider the key. Name the read/write trade.
- *"How do you add a 4th shard to a 3-shard hash setup without moving everything?"* → Don't use `hash % N`. Use consistent hashing or a fixed large partition count so only ~1/N of keys move.
- *"A tenant grows to 10× everyone else — now what?"* → That tenant is a hotspot. Split it out to its own shard(s) (a dedicated "whale" shard), or sub-shard within the tenant. Uniform hashing alone won't save you from a single fat key.

---

## Applied In

This concept is used by **17 problems** in this repo — a representative selection:

**High-Level Design**

- [Design a Leaderboard](../../05-hld-problems/01-easy/leaderboard.md)
- [Design Pastebin](../../05-hld-problems/01-easy/pastebin.md)
- [Design a URL Shortener (Bitly)](../../05-hld-problems/01-easy/url-shortener.md)
- [Design an E-Commerce Platform (Amazon)](../../05-hld-problems/02-medium/e-commerce-platform.md)
- [Design Instagram](../../05-hld-problems/02-medium/instagram.md)
- [Design Twitter / News Feed](../../05-hld-problems/02-medium/twitter-news-feed.md)
- [Design WhatsApp (Real-Time Messaging)](../../05-hld-problems/02-medium/whatsapp.md)
- [Design YouTube](../../05-hld-problems/02-medium/youtube.md)
- …and 9 more

