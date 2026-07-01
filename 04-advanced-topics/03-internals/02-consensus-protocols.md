> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** The exact mathematical algorithm computers use to agree on something when half the computers are broken.
>
> **Key topics:**
> - **The Problem:** If you have 5 computers, and the Leader dies, the remaining 4 computers will panic. Who is in charge now?
> - **Consensus Protocols:** Algorithms like Paxos or Raft that allow computers to hold a democratic election and pick a new leader without human intervention.
> - **Raft (The Easy One):** Every server has a random timer. The first timer to go off yells, "Vote for me!" If they get a majority of votes, they become the Leader. The Leader then forces everyone else to copy their notebook exactly.
> - **Heartbeats:** The Leader constantly sends a "Heartbeat" message ("I'm alive! I'm alive!") every 50 milliseconds. If the followers stop hearing the heartbeat, they assume the Leader died, and they start a new election.
>
> **Key takeaway:** Consensus protocols (specifically Raft) are the engine inside Apache ZooKeeper, etcd, and Kubernetes. They are the only way to build a truly self-healing distributed system.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, distributed-systems, internals]
---
# Consensus Protocols (Raft & Paxos) - System Design Guide

> This guide explains how computers hold democratic elections using simple analogies.

---

## Why Should I Care?

Imagine a company with a CEO (Leader) and 4 Vice Presidents (Followers).
The CEO makes all the decisions. He writes them down, and forces the 4 VPs to copy his notes exactly. Everything runs smoothly.

One day, the CEO gets stuck in traffic and doesn't show up to work.
The 4 VPs panic. They all start trying to make decisions at the same time. VP #1 tells the factory to build a car. VP #2 tells the factory to build a boat. The company is destroyed (Split Brain).

To prevent this, the company needs a strict **Protocol** (a set of rules) written in the employee handbook on exactly how to hold an election and pick a new CEO, without the company exploding in the process.

In System Design, this handbook is called a **Consensus Protocol**. (The two famous ones are Paxos and Raft). Without them, a distributed database cannot survive a server crash.

---

## How Raft Works (The Election)

Raft is the industry standard (used by etcd, Consul, CockroachDB). Here is exactly how it works in 3 steps:

### Step 1: The Heartbeat
The CEO is currently Server A. Every 50 milliseconds, Server A sends a blank "Heartbeat" text message to Servers B, C, D, and E.
The message just means: *"I am alive. Do not hold an election."*

### Step 2: The Timeout (The CEO Dies)
Server A's power cable is unplugged. The Heartbeats stop.
Servers B, C, D, and E are waiting. Inside each server is a secret, randomized timer (e.g., between 150ms and 300ms).

### Step 3: The Election
Server C's random timer happens to expire first (at 155ms).
Server C instantly stands up and yells: *"I haven't heard from the CEO! I am running for CEO! Vote for me!"*

Server C votes for itself (1 vote). It asks B, D, and E for votes. Because B, D, and E's timers haven't expired yet, they say, "Sure, you asked first." (3 more votes).
Server C got 4 votes. (A Quorum/Majority). Server C is officially the new Leader! It instantly starts sending out its own Heartbeats to suppress any other elections.

The entire process took less than a quarter of a second. The users never even knew the database crashed.

---

## Log Replication (The Dictatorship)

Once Server C is elected Leader, it becomes a dictator.

A user clicks "Update Password to 1234." The request goes to Server C.
1. Server C writes the password in its notebook (but doesn't save it permanently yet).
2. Server C sends the password to B, D, and E and says: *"Write this down."*
3. B, D, and E write it down, and reply: *"Done!"*
4. Once Server C gets a **Majority** of "Done!" replies, it permanently saves the password, and tells the user *"Success!"*

If Server E refuses to write it down, Server C just ignores Server E. As long as the Leader has a majority, it forces the system to move forward.

---

## Paxos vs Raft

In an interview, you might hear the word **Paxos**.
Paxos was invented in the 1990s. It solves the exact same problem as Raft.
The problem? Paxos is so mathematically complicated that almost nobody on earth actually understands how to program it.

In 2014, engineers got so frustrated with Paxos that they invented **Raft** specifically to be "understandable by normal humans." Today, almost every modern tool uses Raft.

---

## Interview Questions to Practice

1. **"What is the purpose of a Consensus Algorithm like Raft or Paxos?"**
   *Answer:* To allow a cluster of distributed nodes to agree on a single state or value (like who the Leader is, or what order transactions happened in), even if some of the nodes crash or the network partitions. It is the mathematical foundation for fault tolerance.
2. **"How does the Raft algorithm handle Leader Election?"**
   *Answer:* Every follower node has a randomized election timeout timer. If a node does not receive a heartbeat from the Leader before its timer expires, it transitions to a "Candidate" state, votes for itself, and requests votes from the other nodes. The first node to receive a quorum (majority) of votes becomes the new Leader.
3. **"In Raft, what happens if two nodes start an election at the exact same millisecond and get a tie?"**
   *Answer:* The election fails because neither candidate achieved a strict majority. Both nodes will reset their randomized election timers and try again. Because the timers are randomized, one node will almost certainly wake up before the other in the next round, preventing an infinite loop of ties.
