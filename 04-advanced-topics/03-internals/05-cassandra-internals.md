> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** The database that Apple uses to store 10 Petabytes of data across 100,000 servers without a single point of failure.
>
> **Key topics:**
> - **The Problem:** Standard databases have a "Leader". If the Leader dies, the database freezes while it holds an election.
> - **Masterless Architecture:** Cassandra has no Leaders. Every single server is completely equal. You can unplug 50 servers and it won't even flinch.
> - **The Ring (Consistent Hashing):** Cassandra places all its servers on a giant mathematical clock to distribute data perfectly evenly.
> - **Gossip Protocol:** How do 1,000 equal servers communicate without a Leader? They gossip. Server A whispers a secret to Server B. Server B whispers it to Server C. Within 1 second, all 1,000 servers know the secret.
> - **Tunable Consistency:** You can choose your Quorum. Do you want lightning-fast, risky writes? Or slow, perfectly accurate writes? You choose on every single query!
>
> **Key takeaway:** If you are building a system that requires 100% uptime (Availability) and massive Write speeds (like logging billions of IoT sensor metrics), Cassandra is the undisputed king.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, databases, nosql]
---
# Apache Cassandra Internals - System Design Guide

> This guide explains the internal magic of Cassandra using simple analogies.

---

## Why Should I Care?

Imagine a normal business with a CEO (Leader) and 100 employees (Followers).
All decisions must go through the CEO. If the CEO goes on vacation, the business stops until they elect an interim CEO. This is how PostgreSQL, MongoDB, and most databases work.

Now imagine a hippie commune with 100 people. There is no CEO. Everyone is completely equal. Anyone can make a decision. If 10 people get sick, the other 90 people just keep working perfectly fine.

In System Design, this is called a **Masterless Architecture**, and **Apache Cassandra** is the most famous example.
Because there is no CEO bottleneck, Cassandra can handle a truly absurd amount of traffic. Apple uses it to handle over *100 million operations per second*. Netflix uses it to keep their website running even if Amazon AWS deletes an entire data center by accident.

---

## The Ring (How data is stored)

If all 100 servers are equal, how does Cassandra know where to save your profile?

It uses **Consistent Hashing**.
> ** Analogy:** Cassandra draws a giant clock on the floor. It places the 100 servers randomly around the clock. When you try to save a user named "Alice", Cassandra runs "Alice" through a math formula. The formula spits out "4:15 PM". Cassandra walks clockwise from 4:15 until it bumps into a server. That server gets the data!

**Replication:** Because servers crash, saving data on just one server is dangerous. Cassandra doesn't just hand the data to the first server it bumps into. It keeps walking clockwise and hands a copy of the data to the *next two servers* as well. (A Replication Factor of 3).

---

## The Gossip Protocol (How they talk)

If there is no central Leader, how do the 100 servers know if one of them is broken?

> ** Analogy:** High school gossip.
> Server A notices that Server B isn't answering the phone.
> Server A randomly whispers to Server C: *"Hey, I think B is dead."*
> Next millisecond, Server C randomly whispers to Server D: *"Hey, A told me that B is dead."*
> Next millisecond, both C and D whisper the rumor to two other servers.

This is called the **Gossip Protocol**. It spreads exponentially. Within 1 second, all 100 servers have heard the rumor, and they all mathematically agree that Server B is dead, without anyone having to be the "Boss."

---

## Tunable Consistency (The Magic Dial)

In a Masterless system, you have 3 copies of "Alice's Profile" sitting on 3 different servers.
When a user wants to read Alice's profile, how many of those 3 servers do you need to check?

Cassandra lets you turn a dial (Tunable Consistency) on *every single query*.

1. **Consistency Level: ONE (Lightning Fast)**
   - *How it works:* The user asks Server 1 for the profile. Server 1 instantly hands it over.
   - *The Danger:* What if Server 2 actually has a newer, updated version of the profile? You just gave the user stale data! (You chose Speed over Accuracy).
2. **Consistency Level: ALL (Perfectly Accurate)**
   - *How it works:* The user asks for the profile. Cassandra asks all 3 servers, compares the answers, and returns the absolute newest one.
   - *The Danger:* If Server 3 is currently rebooting, the query fails! (You chose Accuracy over Speed).
3. **Consistency Level: QUORUM (The Goldilocks Zone)**
   - *How it works:* You ask a strict majority (2 out of 3 servers). As long as 2 agree, you trust them. If 1 server is dead, the system keeps working perfectly. This is what 99% of companies use.

---

## Healing Stale Replicas (Read Repair, Hints, Anti-Entropy)

Tunable consistency raises an obvious question: if you write at QUORUM (2 of 3), **the third replica is now wrong**. What fixes it?

Cassandra has three repair mechanisms, running at different timescales. Knowing all three — and when each one fires — is a standard senior follow-up.

### 1. Read Repair (fixes it on the next read)

When a read touches multiple replicas and they disagree, Cassandra notices:

1. Coordinator asks replicas for the data (and cheap digests from the rest).
2. The digests don't match → some replica is stale.
3. Coordinator picks the newest version by timestamp, returns it to the user.
4. **In the background, it writes the correct value back to the stale replica.**

> **💡 Analogy:** Three friends each wrote down a phone number. You ask all three, notice one has an old number, tell the user the correct one — and then correct your friend's notebook while you're at it.

The catch: read repair only fixes data that somebody actually *reads*. Cold data stays wrong forever.

### 2. Hinted Handoff (fixes it when a node comes back)

Replica 3 is down when the write arrives. Rather than lose it, the coordinator stores a **hint** — a note saying "when Replica 3 returns, give it this write."

When Replica 3 rejoins, the coordinator replays the hints and it catches up in seconds.

> **💡 Analogy:** Your neighbour isn't home, so you sign for their parcel and hand it over when they're back.

The catch: hints have a time limit (`max_hint_window_in_ms`, 3 hours by default). A node down longer than that gets no hints — it has to be repaired the slow way.

### 3. Anti-Entropy Repair (the scheduled deep clean)

The backstop for everything the other two missed: cold data never read, and nodes that were down longer than the hint window.

Nodes compare **Merkle trees** — a hash tree over their data ranges. Comparing root hashes is one comparison; if they differ, you walk down the tree and find the exact mismatched ranges without shipping the whole dataset over the network. Only the differing ranges get streamed.

This runs as an explicit operation (`nodetool repair`), typically weekly, and it's expensive.

| Mechanism | Trigger | Fixes | Misses |
|-----------|---------|-------|--------|
| Read repair | On read | Data being actively read | Cold data |
| Hinted handoff | Node rejoins | Short outages | Outages > hint window |
| Anti-entropy repair | Scheduled / manual | Everything | Nothing (but slow + costly) |

> **🎤 Interview note:** "Write at QUORUM and read at QUORUM gives you strong consistency because R + W > N" is the correct headline — but the follow-up is *"so what repairs the replica that missed the write?"* Naming all three mechanisms, and noting that read repair alone leaves cold data permanently stale, is what separates a memorized formula from real understanding. These same three mechanisms appear in DynamoDB and Riak — they all descend from the Dynamo paper.

---

## Interview Questions to Practice

1. **"What is the main architectural difference between Cassandra and MongoDB?"**
   *Answer:* MongoDB uses a Leader-Follower (Master-Slave) architecture, meaning writes must go through a single primary node, making it vulnerable to leader-election downtime. Cassandra uses a Masterless (Peer-to-Peer) architecture based on a Consistent Hashing ring, meaning any node can accept any read or write, providing 100% availability and massive write scalability with no single point of failure.
2. **"How does Cassandra maintain cluster health and detect dead nodes without a central controller?"**
   *Answer:* It uses a Gossip Protocol. Every second, each node randomly selects 1 to 3 other nodes and exchanges state information about itself and the rest of the cluster. This peer-to-peer communication ensures that topology changes and node failures propagate exponentially and quickly to the entire cluster.
3. **"What does 'Tunable Consistency' mean in Cassandra?"**
   *Answer:* Because Cassandra is a Masterless, distributed system, replicas can become temporarily out of sync. Tunable Consistency allows the developer to choose the `Consistency Level` (e.g., ONE, QUORUM, ALL) for each individual read or write operation. You can explicitly trade off latency and availability against strict data consistency based on the specific needs of the query.

---

# 🎯 SDE-3 Deep Dive

The ring + gossip + tunable consistency + repair is the architecture. Seniors get pushed on **the LSM write path (why Cassandra is a write monster), compaction strategy selection, the token ring / vnodes / partitioner mechanics, the data-modeling pitfalls that actually take clusters down (hot/wide partitions, tombstones), and lightweight transactions.**

## The LSM write path — why writes are so fast

Cassandra is an **LSM-tree** store, and that's the whole reason it eats writes. A write never seeks on disk:

1. Append to the **commit log** (durable, sequential write).
2. Update the **memtable** (in-memory, sorted by key).
3. Ack the client. *No disk seek, no read-before-write.*
4. When the memtable fills, flush it to an immutable **SSTable** on disk (sequential write).

Reads are the price you pay: a key may live in the memtable *plus* several SSTables, so a read merges them. To avoid touching every SSTable, each has a **bloom filter** (is this key possibly here?) and a **partition index/summary**. A read that misses the row cache checks bloom filters, reads only the SSTables that might contain the key, merges by timestamp (last-write-wins), and returns.

> **Senior signal:** "Cassandra is fast at writes because writes are append-only sequential I/O (LSM), and slower/more-complex at reads because a row is scattered across immutable SSTables that must be merged." Contrast with a B-tree store (Postgres/MySQL, [`07-mysql-internals.md`](07-mysql-internals.md)) which does read-modify-write in place — fast reads, write amplification.

## Compaction — the background tax, and choosing a strategy

Immutable SSTables accumulate; compaction merges them, drops tombstones, and reclaims space. **The strategy is a real interview question because it's a workload decision:**

| Strategy | Best for | Trade-off |
|---|---|---|
| **STCS** (Size-Tiered) | Write-heavy | Low write amplification, but a key can span many SSTables → read amplification + space spikes |
| **LCS** (Leveled) | Read-heavy | Bounds a key to ~1 SSTable per level → great reads, but high write amplification |
| **TWCS** (Time-Windowed) | Time-series / TTL data | Compacts by time window, expires whole SSTables cheaply → ideal for metrics/IoT |

Getting this wrong is a classic prod incident: STCS on a read-heavy table = read latency; LCS on a write-heavy table = compaction can't keep up and SSTables pile up.

## The token ring, vnodes, and partitioner

The "clock" analogy is consistent hashing, but the mechanics matter:

- The **partitioner** (Murmur3 by default) hashes the partition key to a **token**; the node owning that token range stores the row. **The partition key alone decides placement** — this is why data modeling is placement.
- **Virtual nodes (vnodes):** each physical node owns *many* small token ranges, not one. This spreads data evenly and, critically, means when a node dies its ranges are rebuilt from **many** peers in parallel (fast rebuild) instead of overloading its two ring neighbours.
- **Rack/DC awareness:** `NetworkTopologyStrategy` places the 3 replicas across different racks/AZs so a rack loss doesn't take out a quorum. `LOCAL_QUORUM` keeps quorum reads/writes inside one datacenter to avoid cross-region latency ([`../../03-scaling/04-global-distribution.md`](../../03-scaling/04-global-distribution.md)).

## The data-modeling pitfalls that kill clusters

These are what a senior is *really* being tested on — Cassandra punishes bad modeling brutally:

- **Hot partitions:** a bad partition key (e.g., keying by `country` or `status`) funnels all traffic to the few nodes owning those tokens. One key = one node's problem. **Model queries first; the partition key must spread load.**
- **Wide partitions / unbounded growth:** a partition that grows forever (e.g., all events keyed only by `user_id`) creates multi-GB partitions that blow up heap and compaction. Fix with a **composite/bucketed key** (`user_id + month`).
- **Tombstone hell:** deletes and TTL expiries write **tombstones**, not removals. A read scanning a partition full of tombstones (queue-like delete-heavy workloads) can time out reading thousands of dead cells. `gc_grace_seconds` (default 10 days) delays their purge so a down node doesn't resurrect deleted data — but it means Cassandra is a **terrible queue**. Naming "don't use Cassandra as a queue" is a senior tell.

## Lightweight transactions (LWT) — when you need CAS

Tunable consistency gives no isolation, so "check-then-set" (unique username, seat booking) is racy. **LWT** (`IF NOT EXISTS` / `IF col = ?`) runs **Paxos** across the replicas for linearizable compare-and-set:

- It's ~4 round trips vs a normal write's 1 → **an order of magnitude slower.** Use it only for the rare operation that genuinely needs it; never as the default write path.
- Ties to consensus ([`02-consensus-protocols.md`](02-consensus-protocols.md)) — LWT is Cassandra bolting a Paxos round onto a normally leaderless store precisely because leaderless can't do atomic CAS.

## Interview probes you should survive

- *"Why is Cassandra so fast at writes and comparatively slow at reads?"* → LSM tree: writes are append-only sequential I/O to commit log + memtable (no read-before-write); reads must merge the memtable and multiple immutable SSTables (mitigated by bloom filters + row cache).
- *"You have a read-heavy table with growing read latency — what would you check?"* → Compaction strategy (STCS scatters a key across many SSTables → read amplification); switch to LCS. Also check for tombstones and wide partitions.
- *"A few nodes are hot while the rest are idle — why?"* → Hot partition from a low-cardinality partition key funneling tokens to a few nodes; remodel the key to spread load (add a bucketing component).
- *"You need a unique-username constraint on a masterless store — how?"* → LWT (`IF NOT EXISTS`) runs Paxos for linearizable CAS; accept it's ~4× slower and confine it to that operation.
- *"Why is Cassandra a bad message queue?"* → Delete-heavy workloads generate tombstones that aren't purged until `gc_grace_seconds`; reads scan through them and time out. The access pattern fights the storage engine.

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design a Distributed Key-Value Store](../../05-hld-problems/01-easy/key-value-store.md)
- [Design an Ad Click Aggregator](../../05-hld-problems/03-hard/ad-click-aggregator.md)
- [Design a Metrics Monitoring System (Prometheus + Grafana)](../../05-hld-problems/03-hard/metrics-monitoring-system.md)

