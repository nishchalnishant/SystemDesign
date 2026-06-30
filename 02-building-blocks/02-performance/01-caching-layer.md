> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How to make your database 100x faster by saving the answers to common questions in memory.
>
> **Key topics:**
> - **The Problem:** Databases save data on hard drives, which are physically slow to read from. 
> - **The Solution (Caching):** Saving data in RAM (Memory), which is lightning fast. Tools like Redis or Memcached do this.
> - **Cache Aside (The Lazy Way):** The app checks the cache. If it's empty, it asks the database, and then saves the answer in the cache for next time.
> - **Write-Through (The Safe Way):** When saving data, the app writes to the cache *and* the database at the exact same time.
> - **Eviction Policies (LRU):** RAM is expensive. When the cache gets full, you have to throw something away. LRU (Least Recently Used) throws away the oldest, least popular data.
>
> **Key takeaway:** Caching is the ultimate cheat code for system design. If your app is slow, putting Redis in front of the database is almost always the first step to fixing it.

---
module: 02-building-blocks
status: unread
tags: [02-building-blocks, system-design, performance]
---
# Caching Layer - System Design Guide

> This guide explains how to use a Caching Layer to speed up your application using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you work at a library front desk. 
Every time someone asks you, "What is the capital of France?", you get up from your desk, walk into the basement, find the Encyclopedia, read the answer, walk back up the stairs, and say "Paris." This takes 5 minutes. 

If 100 people ask you the exact same question in one hour, you will spend your entire day walking up and down the stairs.

Instead, after the very first person asks, you write "Capital of France = Paris" on a sticky note and put it on your desk. The next 99 times someone asks, you look at the sticky note and answer in 1 second. 

In System Design:
- **The Basement** is your Main Database (PostgreSQL). It holds everything, but it is slow because it reads from physical hard drives.
- **The Sticky Note** is your Cache (Redis). It holds a tiny amount of data, but it is lightning fast because it stores data in RAM.

If you don't use a cache, a sudden spike of users will overwhelm your database and crash your entire application. 

---

## 📖 Caching Strategies (How do we update the sticky note?)

There are three main ways to connect your Application, your Cache, and your Database. 

### 1. Cache-Aside (The Lazy Way)
> **💡 Analogy:** The librarian only goes to the basement if the sticky note doesn't have the answer.
- **How it works:** 
  1. The app asks the Cache for a user profile. 
  2. If the Cache says "I don't have it" (Cache Miss), the app asks the Database.
  3. The app gives the profile to the user, AND saves a copy in the Cache for next time.
- **Pros:** The Cache only stores data that people actually ask for. 
- **Cons:** The very first person to ask for the data gets a slow response. 

### 2. Write-Through (The Safe Way)
> **💡 Analogy:** Whenever the librarian learns a new fact, they instantly write it on the sticky note AND run down to the basement to write it in the Encyclopedia. 
- **How it works:** When a user updates their profile picture, the app saves it to the Cache AND the Database at the exact same time. 
- **Pros:** The Cache is never out of date. 
- **Cons:** Saving data takes slightly longer because you have to wait for two systems to finish writing.

### 3. Write-Behind / Write-Back (The Risky/Fast Way)
> **💡 Analogy:** The librarian writes the new fact on the sticky note, tells the customer "Done!", and then goes to the basement at the end of the day to update the Encyclopedia.
- **How it works:** The app ONLY saves data to the Cache (which is blazing fast). Later, in the background, the Cache slowly syncs the data to the Main Database. 
- **Pros:** Insanely fast write speeds. Great for logging or view counts.
- **Cons:** If the Cache server loses power before it syncs to the Database, the data is permanently lost. 

---

## 🗑️ Eviction Policies (What happens when it gets full?)

RAM is very expensive. A database might hold 10 Terabytes of data, but your Redis Cache might only have 10 Gigabytes of RAM. 

When the Cache gets 100% full, and you try to add a new sticky note, you have to throw an old one away. How do you decide which one to throw away?

1. **LRU (Least Recently Used):** 
   - *How it works:* Throw away the sticky note that hasn't been looked at in the longest amount of time. 
   - *Why:* This is the industry standard. If nobody has looked at a profile in 6 months, throw it out!
2. **LFU (Least Frequently Used):**
   - *How it works:* Throw away the sticky note that has the lowest total number of views. 
3. **FIFO (First In, First Out):**
   - *How it works:* Just throw away the oldest sticky note, even if someone looked at it 5 seconds ago. (Rarely used).

---

## 🎤 Interview Questions to Practice

1. **"What is a Cache Miss?"**
   *Answer:* A Cache Miss happens when the application asks the caching layer for data, but the data isn't there. The application is then forced to query the much slower main database to get the data, increasing latency for that specific request.
2. **"What is the difference between Cache-Aside and Write-Through?"**
   *Answer:* In Cache-Aside, the cache is only updated *after* a Cache Miss occurs (data is loaded lazily). In Write-Through, the cache is updated proactively at the exact same time the database is updated, ensuring the cache is never stale.
3. **"If RAM is expensive, how do we prevent the Cache from running out of memory?"**
   *Answer:* We use an Eviction Policy, most commonly LRU (Least Recently Used). When the cache reaches its memory limit, it automatically deletes the data that hasn't been accessed in the longest amount of time to make room for new data.
