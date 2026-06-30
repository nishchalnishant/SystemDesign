> [!NOTE]
> **📋 5-Minute Summary**
>
> **What this covers:** How multiple computers vote to agree on a single truth, even if half of them are broken or offline.
>
> **Key topics:**
> - **The Problem:** If you have 3 database servers, and they all receive different data at the same time, how do they agree on which data is "correct"?
> - **Consensus:** The mathematical process of voting to find a single truth.
> - **Raft & Paxos:** The two most famous "voting" algorithms. (Raft is easier to understand, Paxos is the older, harder one).
> - **Leader Election:** The easiest way to avoid arguments is to elect a boss (The Leader). Everyone else just copies the boss.
> - **Heartbeats:** How the followers know if the boss is dead, so they can trigger a new election.
>
> **Key takeaway:** Distributed databases (like CockroachDB or etcd) use Raft to ensure they never lose your data, even if entire data centers lose power.

---
module: 01-foundations
status: unread
tags: [01-foundations, system-design, foundations]
---
# Consensus Algorithms (Raft & Paxos) - System Design Guide

> This guide explains how computers "vote" to agree on data, using simple analogies.

---

## 🤷‍♂️ Why Should I Care?

Imagine you own a bank with 3 servers (Server A, Server B, Server C). 

You deposit $100. Server A receives the deposit. 
At the exact same millisecond, your wife withdraws $100 from an ATM. Server B receives the withdrawal. 

Server A shouts: "The balance is $200!"
Server B shouts: "The balance is $0!"

If the servers can't agree on what the actual balance is, your bank will collapse. **Consensus Algorithms** (like Raft and Paxos) are the mathematical rules that force computers to hold a "vote" and agree on a single, permanent truth, even when chaos is happening. 

Without consensus algorithms, modern distributed databases (and cloud infrastructure) would be impossible.

---

## 🗳️ How Do Computers Vote? (The Raft Algorithm)

In the 1990s, scientists invented an algorithm called **Paxos** to solve this problem. It worked, but the math was so insanely complicated that almost nobody could actually program it correctly.

In 2013, scientists created **Raft**. Raft does the exact same thing as Paxos, but it was designed to be simple enough for normal humans to understand. Here is how Raft works in 3 simple steps:

### Step 1: Leader Election (Electing the Boss)
> **💡 Analogy:** A group of 5 friends are trying to decide where to eat dinner. If everyone shouts their favorite restaurant at once, they will never decide. The easiest solution is to elect a "Leader." The Leader picks the restaurant, and the other 4 friends just follow. 

In a database cluster with 5 servers, they start by holding an election. 
- One server says "Vote for me!" 
- If it gets a **Quorum** (a majority, which is 3 out of 5 votes), it becomes the **Leader**.
- The other 4 servers become **Followers**.

**The Golden Rule:** The Leader is the ONLY server allowed to accept new data from users. The Followers are only allowed to copy the Leader. 

### Step 2: Log Replication (Taking the Vote)
Now that we have a Leader, how do we save a new piece of data?

> **💡 Analogy:** The Leader says, "I want to save the new bank balance as $200. Do you guys agree?" The Leader writes it down on a piece of paper, but doesn't use ink yet. He asks the 4 followers to write it down. Once 3 out of 5 people (a majority) say "I wrote it down!", the Leader finally pulls out a pen and writes it in permanent ink. 

1. A user sends data to the Leader.
2. The Leader sends the data to the Followers, but doesn't save it permanently yet (This is called "Uncommitted").
3. The Followers reply "Got it!"
4. As soon as the Leader gets a majority of "Got it!" replies, the Leader saves it permanently (This is called "Committed"). 
5. The Leader tells the Followers, "Okay, the vote passed! You can all save it permanently now."

Because we waited for a majority vote, we absolutely guarantee that the data is safe, even if 2 of the 5 servers instantly catch on fire.

### Step 3: Heartbeats (What if the Boss dies?)
> **💡 Analogy:** The boss is driving the car, and the 4 friends are asleep in the back. The boss promises to honk the horn every 5 seconds to prove he is awake. If 10 seconds go by without a honk, the friends wake up in a panic, push the boss out of the driver's seat, and elect a new driver.

- The Leader constantly sends an empty message (a "Heartbeat") to the Followers every few milliseconds.
- If a Follower doesn't receive a Heartbeat for a while (The Election Timeout), the Follower assumes the Leader is dead.
- The Follower instantly promotes itself to a "Candidate" and begs the other servers to vote for it. 
- A new Leader is elected, and the system keeps running perfectly!

---

## 🧠 Paxos vs Raft (What's the difference?)

If you are in an interview and someone asks you about Paxos, you only need to know this:

- **Paxos** is older, extremely complicated, and doesn't rely on a single "Leader." Anyone can propose data at any time. It is very hard to build correctly. (Used by Google Spanner).
- **Raft** is newer, much easier to understand, and relies strictly on electing one single Leader at a time. (Used by etcd, Consul, CockroachDB, and MongoDB).

---

## 🎤 Interview Questions to Practice

1. **"What happens in Raft if the Leader server loses power?"**
   *Answer:* The Followers will notice that they stopped receiving "Heartbeat" messages from the Leader. After a short timeout, one of the Followers will become a Candidate and start a new election. Once a majority of servers vote for the new Candidate, it becomes the new Leader and the system continues.
2. **"Why do we need an odd number of servers (3, 5, 7) for a Consensus Algorithm?"**
   *Answer:* To prevent a tie vote (Split-Brain). To save data, you must get a majority vote (a Quorum). If you have 4 servers, and the network splits them 2-vs-2, neither side can get a majority (which requires 3). The database will completely freeze. If you have 5 servers, it will split 3-vs-2, and the group of 3 can continue working!
3. **"What is the difference between Raft and Paxos?"**
   *Answer:* Both solve the same problem (distributed consensus). Paxos is older, leaderless, and incredibly complex to implement. Raft was designed specifically to be understandable by relying on a strict Leader-Follower architecture.
