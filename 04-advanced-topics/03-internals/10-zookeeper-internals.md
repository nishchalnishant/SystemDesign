> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** The tiny, ultra-reliable database that coordinates all the other massive databases.
>
> **Key topics:**
> - **The Problem:** If you have 50 Kafka servers, they all need to agree on exactly who is the Leader, what IP address they should use, and what the configuration settings are. If they disagree, the cluster explodes.
> - **The Source of Truth:** ZooKeeper is a tiny, incredibly strict, CP (Consistent and Partition Tolerant) database. It uses a Consensus Protocol (ZAB, similar to Raft) to guarantee that the data it holds is mathematically perfect.
> - **Znodes (The Folder Structure):** ZooKeeper stores data like a Mac/Windows file system. It has folders and files.
> - **Ephemeral Nodes (The Dead Man's Switch):** A file that magically deletes itself if the server that created it stops responding. Perfect for detecting if a server crashed!
>
> **Key takeaway:** ZooKeeper is rarely used by developers directly. It is used *internally* by massive tools (Kafka, Hadoop) as a "Source of Truth" to coordinate their clusters and manage leader elections.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, internals, consensus, coordination]
---
# Apache ZooKeeper Internals - System Design Guide

> This guide explains the internal magic of ZooKeeper using simple analogies.

---

## Why Should I Care?

Imagine a construction site with 1,000 workers building a skyscraper.
If Worker A thinks the blueprint says "Use Steel", and Worker B thinks the blueprint says "Use Wood", the skyscraper will collapse.

To prevent this, you build a tiny, heavily guarded safe in the middle of the site. You put the *One True Blueprint* inside. Before any worker does anything, they must walk to the safe and check the blueprint.

In System Design, this safe is **Apache ZooKeeper**.
When you spin up a massive cluster of Apache Kafka or Hadoop, those servers need a central place to store critical metadata (e.g., "Which server is currently the Leader?", "What are the security settings?").
ZooKeeper is a tiny, highly-available database specifically designed to hold this exact, mathematically perfect truth.

*(Note: Newer versions of Kafka have actually removed ZooKeeper and built this feature directly into Kafka itself using Raft, but ZooKeeper remains a critical concept in System Design).*

---

## Znodes (The File System)

Unlike a SQL database (which has Tables and Rows), ZooKeeper stores data exactly like the File Explorer on your laptop.

It uses a tree structure. Each "folder" or "file" is called a **Znode**.
```text
/
├── /kafka
│   ├── /brokers
│   │   ├── /broker_1 (Data: IP Address 10.0.0.1)
│   │   └── /broker_2 (Data: IP Address 10.0.0.2)
│   └── /controller (Data: "Broker 2 is the Leader")
```

If Kafka wants to know who the Leader is, it just reads the data sitting inside the `/controller` Znode.

---

## ⏱ Ephemeral Nodes (The Dead Man's Switch)

This is ZooKeeper's superpower.

When you create a normal file on your computer, it stays there forever until you delete it.
In ZooKeeper, a server can create an **Ephemeral Znode**.
> ** Analogy:** A Dead Man's Switch on a train. The train engineer must keep their hand on a button. If they have a heart attack and their hand falls off the button, the train automatically hits the brakes.

**How it's used for Failure Detection:**
1. Server 5 boots up. It connects to ZooKeeper and creates an Ephemeral Znode called `/servers/server_5`.
2. Server 5 must send a "Heartbeat" ping to ZooKeeper every 2 seconds.
3. Suddenly, Server 5's power cable is unplugged. The Heartbeats stop.
4. ZooKeeper notices the silence, and *automatically deletes* `/servers/server_5`.
5. The other servers see the file vanish, and instantly know that Server 5 is dead!

---

## Leader Election (The Race)

How do 5 equal servers use ZooKeeper to elect a Leader without arguing?

> ** Analogy:** 5 people walk into a room. There is exactly 1 gold crown sitting on a table. The first person to grab it is the King.

1. Servers 1 through 5 all boot up at the exact same time.
2. They all rush to ZooKeeper and attempt to create an Ephemeral Znode called `/leader`.
3. ZooKeeper guarantees absolute strictness (Consistency). It will only allow exactly *one* server to succeed.
4. Server 3 happens to win the race. ZooKeeper creates the file.
5. Servers 1, 2, 4, and 5 get an error message. They say, "Okay, we lost. Server 3 is the King."
6. If Server 3 crashes, ZooKeeper deletes the `/leader` file (because it's Ephemeral). The other servers see it vanish, and the race instantly starts again!

---

## Interview Questions to Practice

1. **"What is Apache ZooKeeper primarily used for in a distributed system?"**
   *Answer:* It is used as a highly reliable coordination service. It manages configuration data, naming services, distributed synchronization, and group services (like Leader Election and Failure Detection) for massive distributed frameworks like Kafka or Hadoop.
2. **"What is the difference between a Persistent Znode and an Ephemeral Znode?"**
   *Answer:* A Persistent Znode remains in ZooKeeper indefinitely until it is explicitly deleted by a client. An Ephemeral Znode is tied to the active session of the client that created it. If the client disconnects or crashes (fails to send a heartbeat), ZooKeeper automatically deletes the Ephemeral Znode.
3. **"How does ZooKeeper facilitate Leader Election among a group of servers?"**
   *Answer:* All candidate servers attempt to create the exact same Ephemeral Znode (e.g., `/leader`). Because ZooKeeper enforces strict consistency, only one server will succeed and become the Leader. The other servers place a "Watch" on the `/leader` node. If the Leader crashes, the Ephemeral node is deleted, the Watch triggers, and the remaining servers immediately race to create the node and become the new Leader.

---

# 🎯 SDE-3 Deep Dive

Znodes + ephemeral nodes + "race for `/leader`" is the intro. Seniors get probed on **the ZAB protocol and its consistency guarantees, the herd-effect bug in naive leader election, watches vs polling, and when ZooKeeper is the wrong tool.**

## ZAB — and what "consistency" actually means here

ZooKeeper runs **ZAB (ZooKeeper Atomic Broadcast)**, a consensus protocol in the same family as Raft (see [`02-consensus-protocols.md`](02-consensus-protocols.md)). Key guarantees to state precisely:

- **Writes are linearizable** — every write goes through the leader, is assigned a monotonically increasing **zxid**, and is committed once a **quorum** (majority) of the ensemble acks it. Same `2f+1` fault tolerance: a 5-node ensemble survives 2 failures.
- **Reads are NOT linearizable by default** — a client reads from whatever server it's connected to, which may lag the leader. You get **sequential consistency** (a client sees its own writes in order) but *not* the latest global write unless you issue a **`sync`** before the read. This is the #1 senior gotcha: "ZooKeeper is CP" but its *reads* are stale-tolerant for throughput.
- Writes don't scale horizontally (all go through the leader + quorum) — ZooKeeper is for **low-volume, high-value coordination metadata**, never bulk data. Znodes cap at ~1MB for this reason.

## The herd effect — why the naive "everyone races for `/leader`" is wrong

The intro's "all servers watch `/leader`" has a real bug: when the leader dies, **all N-1 followers wake up simultaneously and stampede** to recreate the node — the **thundering herd**. The correct pattern is **sequential ephemeral nodes**:

- Each candidate creates `/election/n_0000000x` (ZooKeeper assigns a monotonic suffix). The **lowest sequence number is the leader.**
- Each node watches **only the node immediately before it**, not `/leader`. When a node dies, only its *one* successor wakes up. No herd.
- This same sequential-ephemeral pattern is how you build a **distributed lock / fair queue** on ZooKeeper — the canonical recipe.

## Watches are one-shot and edge-triggered

A **watch** fires **exactly once** then must be re-registered — miss that and you stop getting notifications. Watches are *edge-triggered* (notify "something changed"), not level-triggered, so between the notification and your re-read the state may have changed again — always re-read after a watch fires. Missing this causes "why did my client stop seeing updates" bugs.

## Sessions, ephemerals, and the false-failure trap

Ephemeral nodes vanish when the **session** (not the connection) expires. A **GC pause or network blip longer than the session timeout** expires the session → ZooKeeper deletes your ephemeral → **the cluster thinks you're dead and elects a new leader, while you think you're still leader.** This is split-brain by session timeout — the reason ZooKeeper-based leadership still needs **fencing tokens** (use the zxid) on the protected resource. Ties directly to [`../../02-building-blocks/04-coordination/02-distributed-locks.md`](../../02-building-blocks/04-coordination/02-distributed-locks.md).

## When ZooKeeper is the wrong tool

- **etcd** (Raft, gRPC, used by Kubernetes) is the modern alternative — simpler API, MVCC key-value with revisions and leases.
- **Kafka removed ZooKeeper (KRaft)** — folding metadata consensus into the brokers themselves — precisely because running a separate ZK ensemble is operational overhead and a scaling bottleneck.
- Don't use ZooKeeper as a config *database*, message queue, or for high write throughput. It's a coordination kernel, not a datastore.

## Interview probes you should survive

- *"Is a ZooKeeper read guaranteed to be the latest value?"* → No — reads can be served by a lagging follower (sequential consistency). Issue `sync` before the read for linearizability, or read through the leader.
- *"How do you do leader election without a thundering herd?"* → Sequential ephemeral nodes; each candidate watches only its immediate predecessor, so one node wakes on failure, not all.
- *"A GC pause caused two leaders — how?"* → The paused node's session expired, ZK deleted its ephemeral and elected a new leader, but the old node resumed still believing it leads. Fence the resource with the zxid.
- *"Why did Kafka drop ZooKeeper?"* → To eliminate a separate consensus system; KRaft embeds Raft-based metadata consensus in the brokers, reducing ops burden and the metadata-scaling ceiling.
- *"Why can't you store lots of data in ZooKeeper?"* → All writes serialize through the leader+quorum and znodes are ~1MB-capped; it's built for small, high-value coordination state, not bulk storage.

---

## Applied In

This concept is used by **3 problems** in this repo:

**High-Level Design**

- [Design a Unique ID Generator](../../05-hld-problems/01-easy/unique-id-generator.md)
- [Design a Chat System (Slack)](../../05-hld-problems/03-hard/chat-system.md)
- [Design a Distributed Job Scheduler](../../05-hld-problems/03-hard/distributed-job-scheduler.md)

