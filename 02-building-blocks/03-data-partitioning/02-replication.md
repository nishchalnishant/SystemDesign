> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to copy your database so if a server explodes, you don't lose all your users' data.
>
> **Key topics:**
> - **The Problem:** Hard drives fail. If your Database only exists on one hard drive, you are one lightning strike away from your company going bankrupt.
> - **The Solution (Replication):** Making perfect copies of your database on different servers.
> - **Primary-Replica (Leader-Follower):** One boss takes all the new data (Writes), and copies it to 3 assistants (Reads). Most common setup!
> - **Multi-Primary (Multi-Leader):** Multiple bosses taking new data at the exact same time. Faster, but causes conflicts.
> - **Synchronous vs Asynchronous:** Do you force the user to wait until the copy is 100% finished? Or do you say "Done!" immediately and copy it in the background?
>
> **Key takeaway:** Sharding is for when you run out of *space*. Replication is for when you want to handle more *reads*, and protect against hardware failures. You almost always use both!

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, data-partitioning]
---
# Database Replication - System Design Guide

> This guide explains how to copy data across servers using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you are writing a 500-page novel. You save it on your laptop. 
One day, you spill coffee on your laptop, and the hard drive is destroyed. The novel is gone forever. 

To fix this, you start saving a copy of the novel to a USB drive every time you hit "Save". 
This is **Replication**. 

In System Design, servers fail all the time. Hard drives die, power goes out, cables get cut. If your database only lives on one server, your system is incredibly fragile (A Single Point of Failure). 
By creating Replicas (exact copies) of your database on other servers, you get two massive benefits:
1. **Durability:** If Server A catches on fire, Server B has an exact copy. You lose no data.
2. **Read Scaling:** If 10,000 users want to *read* a blog post, you don't have to send all of them to Server A. You can send 5,000 to Server A, and 5,000 to Server B. 

---

## 👑 1. Primary-Replica (Leader-Follower)

This is the most common replication setup in the world (used by PostgreSQL, MySQL, etc).

> **💡 Analogy:** A Head Chef and 3 Sous Chefs. The Head Chef is the only person allowed to invent new recipes and write them in the official cookbook (Writes). As soon as he writes a recipe, he photocopies it and hands it to the 3 Sous Chefs (Replicas). If a customer asks to see a recipe (Reads), any of the 3 Sous Chefs can hand them a copy. 

- **How it works:** 
  - ALL **Writes** (Insert, Update, Delete) must go to the Primary Database.
  - ALL **Reads** (Select) can go to any of the Replica Databases.
- **The Catch (Replication Lag):** If the Head Chef writes a recipe, but it takes 5 seconds for him to photocopy it and hand it to the Sous Chef... what happens if a customer asks the Sous Chef for the recipe during those 5 seconds? They get an error! The data hasn't synced yet. 

---

## ⚔️ 2. Multi-Primary (Multi-Leader)

What if you have a massive app where users are writing millions of messages per second? One Head Chef might be too slow to write everything down!

> **💡 Analogy:** You hire two Head Chefs. One works in the New York kitchen, one works in the Tokyo kitchen. Both of them are allowed to write new recipes at the exact same time. At the end of the day, they call each other and swap notes.

- **How it works:** You have multiple Primary databases that accept Writes. They sync up with each other in the background.
- **The Problem (Data Conflicts):** What if the NY Chef changes the "Cookie" recipe to use Chocolate Chips, and the Tokyo Chef changes the exact same "Cookie" recipe to use Raisins at the exact same millisecond? When they sync up later, the database crashes because of a conflict. You have to write complicated code to resolve it.

---

## ⏱️ Synchronous vs Asynchronous Copying

When a user clicks "Save Profile", the Primary database saves it. But when does it copy it to the Replicas?

### 1. Synchronous (Safe but Slow)
- **How it works:** The Primary saves it, sends it to the Replicas, *waits* for the Replicas to say "Got it!", and ONLY THEN tells the user "Profile Saved!"
- **Pros:** 100% safe. You never lose data.
- **Cons:** Very slow. The user has to wait. If a Replica is offline, the whole system freezes.

### 2. Asynchronous (Fast but Risky)
- **How it works:** The Primary saves it, instantly tells the user "Profile Saved!", and then quietly copies the data to the Replicas in the background a few seconds later. 
- **Pros:** Lightning fast for the user.
- **Cons:** If the Primary database blows up 1 second after telling the user "Saved", but *before* it had a chance to copy it to the Replicas... that data is permanently lost. (This is how most NoSQL databases work by default!).

---

## 🎤 Interview Questions to Practice

1. **"What is the main difference between Sharding and Replication?"**
   *Answer:* Sharding is splitting data into smaller chunks across multiple servers to handle more Writes and storage capacity. Replication is duplicating the exact same data across multiple servers to handle more Reads and provide high availability in case a server fails.
2. **"What is Replication Lag?"**
   *Answer:* It's the delay between when data is written to the Primary database and when that data is finally copied to the Replica databases. If a user reads from a Replica during this lag, they will see stale, outdated data.
3. **"Why might you choose Asynchronous replication over Synchronous?"**
   *Answer:* You choose Asynchronous for performance and availability. If you use Synchronous replication, a single slow or offline replica can freeze the entire write operation. Asynchronous returns success to the user instantly, trading strict consistency for speed.
