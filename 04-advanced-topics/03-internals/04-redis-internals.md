> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How Redis works under the hood, and why it is the fastest database on Earth.
>
> **Key topics:**
> - **The Core Secret:** Redis stores 100% of its data in RAM (Memory), not on a physical Hard Drive. This makes it 100,000x faster than a normal database.
> - **Single-Threaded Magic:** Redis only processes one command at a time. It doesn't use multiple CPU cores. This sounds slow, but it actually eliminates all "traffic jams" (Locking), making it incredibly fast.
> - **Persistence (RDB vs AOF):** Because RAM forgets everything when the power goes out, Redis has to secretly back up its data to the hard drive.
>   - *RDB:* Takes a massive snapshot of the RAM every 5 minutes. (Fast, but you might lose 4 minutes of data).
>   - *AOF:* Writes every single command to a log file. (Safe, but a bit slower).
>
> **Key takeaway:** Redis is essentially a giant "Dictionary" (Key-Value store) that lives in RAM. It is perfect for Caching, Leaderboards (Sorted Sets), and Rate Limiting.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, caching]
---
# Redis Internals - System Design Guide

> This guide explains the internal magic of Redis using simple analogies.

---

## Why Should I Care?

If you try to read a user's profile from a standard PostgreSQL database, the computer has to physically spin a metal disk, find the data, and send it to you. This takes 10 milliseconds.

If you read the same profile from **Redis**, it takes 0.1 milliseconds. It is 100 times faster.

Why? Because Redis doesn't use the hard drive. It keeps everything in RAM.
> ** Analogy:**
> - **Hard Drive:** A giant filing cabinet in the basement. It holds a million files, but you have to walk downstairs to get them.
> - **RAM:** A sticky note on your forehead. It only holds 10 files, but you can read it instantly.

Because Redis is so fast, almost every major company uses it to **Cache** their most popular data, completely bypassing their slow databases.

---

## The Single-Threaded Secret

If you buy a massive, 64-core CPU server to run Redis... Redis will only use exactly **1 core**.

Wait, isn't that a bad thing? Why doesn't it use all 64 cores?

> ** Analogy:** Imagine a busy kitchen with 64 chefs (64 Cores). They are all trying to use the exact same salt shaker (A specific variable in RAM).
> If Chef 1 reaches for the salt, Chef 2 has to wait. If Chef 2 reaches for it, Chef 3 has to wait. You have to install a "Lock" on the salt shaker. Managing these Locks wastes a massive amount of time (Context Switching and Lock Contention).

Redis says: "Instead of 64 chefs fighting, let's just hire 1 insanely fast Chef (A Single Thread)."
The 1 Chef just rapidly cooks every meal, in order, one by one. There are no fights. There are no Locks.

Because manipulating RAM is so fast, that 1 Single Thread can process **100,000 requests per second** all by itself!
*(Note: Modern Redis uses other cores for background tasks like networking, but the actual data manipulation is strictly single-threaded).*

---

## Persistence (What if the power goes out?)

RAM is volatile. If you unplug a Redis server, all the data vanishes instantly.
To prevent a total disaster, Redis quietly backs up the RAM to the physical Hard Drive in the background. It offers two ways to do this:

### 1. RDB (Redis Database Backup) - The Polaroid Camera
- **How it works:** Every 5 minutes, Redis takes a massive snapshot (like a Polaroid photo) of the entire RAM and saves it to the hard drive.
- **Pros:** It's very efficient and doesn't slow down the single thread.
- **Cons:** If the server loses power at 4:59... you just permanently lost the last 4 minutes and 59 seconds of data.

### 2. AOF (Append-Only File) - The Court Stenographer
- **How it works:** Every single time a user writes data (`SET name "Alice"`), Redis instantly scribbles that exact command into a log file on the hard drive.
- **Pros:** You never lose data. If the power goes out, you just reboot, read the log file, and replay the commands to rebuild the RAM.
- **Cons:** It forces Redis to write to the physical hard drive constantly, which is slightly slower.

---

## Interview Questions to Practice

1. **"Redis is single-threaded. How does it handle 100,000 concurrent connections without blocking?"**
   *Answer:* It uses an Event Loop with I/O Multiplexing (like `epoll` or `kqueue`). The OS notifies the single thread whenever a socket is ready to be read from or written to. Because all data is in RAM, processing a command takes microseconds, allowing the single thread to quickly service the next event without getting bogged down by Locks or Thread Context Switching.
2. **"What happens to data in Redis if the server loses power?"**
   *Answer:* Because Redis stores data in RAM, any data not written to disk will be lost. To mitigate this, Redis provides two persistence mechanisms: RDB (point-in-time snapshots) and AOF (an append-only log of every write operation). If AOF is configured to sync every second, you will lose a maximum of 1 second of data.
3. **"In System Design, what are the most common use cases for Redis?"**
   *Answer:* The most common is Caching (using Cache-Aside) to reduce database load. Other massive use cases include Distributed Locks (using Redlock to prevent race conditions), Rate Limiting (using `INCR` commands and TTL), and Real-time Leaderboards (using Redis Sorted Sets, which are mathematically perfect for ranking).

---

# 🎯 SDE-3 Deep Dive

RAM + single-thread + RDB/AOF is the intro. Seniors are probed on **eviction and the memory ceiling, how Redis scales past one node (replication + Cluster), the atomicity guarantees that make it a coordination primitive, and the failure modes** (Redlock caveat, persistence latency spikes).

## What happens when RAM fills — eviction is a design decision

Redis is bounded by RAM, so `maxmemory` + `maxmemory-policy` is a first-class choice:

| Policy | Behavior | Use for |
|---|---|---|
| `noeviction` | Reject writes when full | Redis-as-database (can't lose data) |
| `allkeys-lru` / `allkeys-lfu` | Evict least-recently/frequently-used across all keys | Pure cache |
| `volatile-lru` / `volatile-ttl` | Evict only keys with a TTL | Mixed cache + persistent keys |

The senior point: **using Redis as a cache and a data store in the same instance is a trap** — an eviction policy that protects the cache can drop your "persistent" keys, or `noeviction` can reject cache writes. Separate instances. Also: Redis LRU/LFU is **approximate** (samples a few keys), not exact, to stay O(1).

## Scaling past one node

- **Replication (primary → replicas):** async by default → a failover can **lose the last few writes** (the replica was behind). Read scaling via replicas, but reads are eventually consistent.
- **Redis Sentinel:** monitors + automates failover for a single primary (HA, not sharding).
- **Redis Cluster:** shards the keyspace across **16384 hash slots**; each primary owns a slot range. This is how you scale writes/memory. The catch: **multi-key operations only work if all keys are in the same slot** — you force that with **hash tags** (`{user123}:profile`, `{user123}:cart` hash to the same slot). Cross-slot transactions/Lua aren't allowed.

## Atomicity — why Redis is a coordination primitive

Because command execution is single-threaded, **each command is atomic**. Beyond that:

- **Lua scripts / `MULTI`-`EXEC`** run to completion with nothing interleaved — this is how you make check-and-set atomic (e.g., rate limiters, `GETSET`, atomic token-bucket refills). See [`../../02-building-blocks/02-performance/02-rate-limiting.md`](../../02-building-blocks/02-performance/02-rate-limiting.md).
- Redis "transactions" (`MULTI`/`EXEC`) are **not rollback transactions** — a command that errors at runtime doesn't roll back the others. They're atomicity + isolation, not the ACID "A."

## The failure modes seniors must name

- **Redlock is contested:** Redis's own distributed-lock algorithm is not a safe fencing mechanism under GC pauses / clock skew (Kleppmann's critique). For correctness-critical locks, use **fencing tokens** ([`../../02-building-blocks/04-coordination/02-distributed-locks.md`](../../02-building-blocks/04-coordination/02-distributed-locks.md)) or a CP store (etcd/ZooKeeper). Redis locks are fine for *efficiency* (avoid duplicate work), not for *correctness* (prevent double-spend).
- **AOF `fsync=always` tanks throughput;** the default `everysec` risks ~1s of data on crash. **`fork()` for RDB/AOF-rewrite causes latency spikes** — copy-on-write can double memory and stall on huge datasets. This is why a big Redis on a memory-tight box gets latency blips at snapshot time.
- **Single-threaded means one slow command blocks everything:** `KEYS *`, a big `SORT`, or an O(N) `LRANGE` on a giant list stalls *all* clients. Use `SCAN` (cursored) instead of `KEYS`, and watch for big-O-N commands.
- **Hot key / big key:** a single hot key can't be sharded (it's one slot on one core) — cap it with client-side caching or key-splitting.

## Interview probes you should survive

- *"Redis is single-threaded — what's the risk of `KEYS *` in prod?"* → It's O(N) and blocks the one thread, stalling every other client for the scan. Use `SCAN`.
- *"How does Redis Cluster handle a transaction across two keys?"* → Only if both keys map to the same hash slot; force it with a hash tag `{tag}`. Cross-slot multi-key ops are rejected.
- *"Can you use Redis for a distributed lock guarding money?"* → For efficiency yes; for correctness, no — Redlock isn't safe under pauses/clock skew. Use fencing tokens or a CP coordinator.
- *"Your Redis has latency spikes every few minutes — cause?"* → Likely the `fork()` for RDB snapshot / AOF rewrite (copy-on-write stalls, memory pressure). Tune save points, use a replica for snapshots, or ensure memory headroom.
- *"You're using one Redis as cache + source of truth and lost data — why?"* → LRU/LFU eviction dropped keys under memory pressure; a cache eviction policy will discard your "persistent" data. Separate the instances.

---

## Applied In

This concept is used by **5 problems** in this repo:

**High-Level Design**

- [Design a Leaderboard](../../05-hld-problems/01-easy/leaderboard.md)
- [Design a Rate Limiter](../../05-hld-problems/01-easy/rate-limiter.md)
- [Design Twitter / News Feed](../../05-hld-problems/02-medium/twitter-news-feed.md)
- [Design a Distributed Cache](../../05-hld-problems/03-hard/distributed-cache.md)
- [Design a Real-Time Gaming Leaderboard](../../05-hld-problems/03-hard/realtime-gaming-leaderboard.md)

