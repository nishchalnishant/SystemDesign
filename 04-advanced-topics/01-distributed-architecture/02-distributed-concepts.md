> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** How multiple computers make a decision when the network breaks and they can't talk to each other.
>
> **Key topics:**
> - **The Problem:** You have 3 database servers. Server A thinks your password is "Dog". Server B thinks your password is "Cat". Who is right?
> - **Quorum (Majority Rules):** The computers hold a vote. To make any decision, a strict majority (more than 50%) of the servers must agree.
> - **Split Brain:** What happens if the network cable between New York and London is cut? Both cities think the other city died, and both cities try to become the "Boss". They start writing conflicting data!
> - **Clock Drift:** Time is an illusion in distributed systems. You cannot trust a computer's internal clock to figure out which event happened first.
>
> **Key takeaway:** Distributed systems require complex voting mechanisms to ensure data isn't corrupted during a network failure. You must design systems that can survive when half the computers suddenly stop responding.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, distributed-systems]
---
# Core Distributed Concepts - System Design Guide

> This guide explains how computers agree on things using simple analogies.

---

## Why Should I Care?

Imagine 3 friends (Alice, Bob, and Charlie) are trying to agree on where to eat for lunch.
They are standing in a circle talking. Alice says "Pizza!" Bob says "Tacos!" Charlie says "Pizza!" Pizza wins. (This is a working distributed system).

Now, imagine Alice is in New York, Bob is in London, and Charlie is in Tokyo. They can only communicate via text messages.
Alice texts Bob: "Pizza?"
Bob's phone dies. Alice waits for 10 minutes. Did Bob's phone die? Or did the cell tower break? Or is Bob just thinking really hard? Alice has absolutely no way to know!

This is the fundamental nightmare of Distributed Systems. When a computer stops responding, you cannot know *why* it stopped responding. You have to write code to handle the silence.

---

## Quorum (Majority Rules)

If you have 5 database servers, how many of them need to successfully save your data before you can tell the user "Profile Saved!"?

- **Option A (Wait for all 5):** The system is perfectly accurate, but incredibly slow. If just 1 server is broken, the entire system is frozen.
- **Option B (Wait for 1):** The system is lightning fast. But if that 1 server immediately catches on fire, your data is lost forever.
- **Option C (Quorum):** You wait for a strict majority (More than 50%).

> ** Analogy:** Supreme Court voting. If you have 5 judges, you need at least 3 to agree to pass a law.

In a 5-server cluster, a **Quorum** is 3.
As long as 3 servers say "I saved the profile!", you can tell the user "Success!" and ignore the other 2 servers.
This means you can have 2 servers completely explode, and your business stays online!

---

## Split Brain (The Two Kings)

Imagine you have a 4-server cluster. Server A is the "Leader" (The King). It takes all the Writes. Servers B, C, and D are followers.

Suddenly, a construction worker accidentally cuts the network cable dividing the building in half.
- Server A and B are stuck on the Left side.
- Server C and D are stuck on the Right side.

Server C and D can't talk to Server A anymore. They think Server A is dead! So they hold an election, and declare Server C the new King.

**The Disaster:** You now have two Kings (Server A and Server C) accepting new Writes at the exact same time. The database is literally splitting in half. When the network cable is fixed, the two databases will smash together and permanently corrupt all your data. This is called **Split Brain**.

**The Fix:** This is why you NEVER have an even number of servers! You must always have an odd number (3, 5, 7).
If you have 5 servers, and the network cuts them into groups of 3 and 2... the group of 2 will say: "We don't have a Quorum (Majority). We are not legally allowed to elect a King." They will safely pause themselves until the network is fixed.

---

## ⏰ Clock Drift (Time is an Illusion)

If User A posts a comment at 12:00:01 on Server 1, and User B posts a comment at 12:00:02 on Server 2... how do we know who posted first?

We just look at the timestamps, right? **WRONG.**

In a distributed system, you cannot trust the clock on the motherboard. Because of heat, physics, and battery issues, Server 1's clock might drift forward by 5 seconds over the course of a year.
Server 1 thinks it's 12:00:05. Server 2 thinks it's 12:00:00.

If you use internal clocks to sort data, the database will save the comments in the wrong order!
To fix this, distributed systems use **Logical Clocks (Lamport Timestamps)**. Instead of using "Time", every message just carries an incrementing counter (Event 1, Event 2, Event 3).

---

## Interview Questions to Practice

1. **"What is a Quorum in a distributed system, and why is it used?"**
   *Answer:* A Quorum is the minimum number of nodes in a distributed cluster that must agree on an operation (like a read or a write) for it to be considered successful. Usually `(N/2) + 1`. It is used to provide high availability and fault tolerance, allowing the system to continue working even if a minority of nodes fail or become unreachable.
2. **"What is the Split-Brain problem?"**
   *Answer:* It occurs when a network partition divides a cluster into two or more groups of nodes that cannot communicate with each other. If both groups independently elect a leader and accept writes, the data will diverge, causing irrecoverable conflicts when the network heals.
3. **"Why do distributed systems require odd numbers of nodes (3, 5, 7) instead of even numbers?"**
   *Answer:* Odd numbers prevent ties during a network partition. If a 4-node cluster splits down the middle (2 and 2), neither side has a strict majority, leading to a split-brain or a total system freeze. With 5 nodes, a split will always leave one side with a majority (3 and 2), allowing the majority side to continue functioning safely.

---

# 🎯 SDE-3 Deep Dive

Quorum + split-brain + clock drift is the vocabulary. Seniors get pushed on **tunable quorum math (R+W>N), how split-brain is actually prevented in practice (fencing, witnesses), the failure-detection problem underneath it all, and vector clocks vs Lamport for real causality.**

## Quorum is tunable, not just "majority"

Leaderless systems (Dynamo/Cassandra) expose quorum as **N (replicas), W (write acks), R (read acks)**. The consistency rule is **R + W > N** — it guarantees the read set and write set overlap in ≥1 up-to-date replica, so a read sees the latest write:

- `W=N, R=1` — fast reads, slow/fragile writes (write-once-read-many).
- `W=1, R=N` — fast writes, slow reads.
- `W=R=quorum` (e.g., N=3, W=R=2) — balanced, the common default.
- `R+W ≤ N` — you've chosen speed and *dropped* the freshness guarantee (possible stale reads). Naming this trade is the senior signal.

But quorum alone doesn't give linearizability — concurrent writes still need **read-repair + conflict resolution** (LWW / version vectors), and edge cases (sloppy quorum + hinted handoff for availability) weaken it further.

## Preventing split-brain in practice

"Use odd numbers" is the theory; production adds mechanisms:

- **Witness / tiebreaker node:** a lightweight arbiter that only votes, so a 2-node app tier + 1 witness gets you majority without a third full replica.
- **Fencing tokens:** even after a correct election, the *old* leader may not know it lost (GC pause). The new leader gets a monotonically increasing token; the shared resource **rejects any write with a stale token** — this is what actually stops a zombie leader from corrupting data ([`../03-internals/02-consensus-protocols.md`](../03-internals/02-consensus-protocols.md), [`../../02-building-blocks/04-coordination/02-distributed-locks.md`](../../02-building-blocks/04-coordination/02-distributed-locks.md)).
- **STONITH** ("shoot the other node in the head"): the surviving side forcibly power-fences the suspected-dead node before taking over, so it can't resurrect as a second writer.

## The problem under everything: you can't detect failure

Alice-can't-tell-if-Bob-is-dead-or-slow is the **failure detection** problem, and it's fundamental. Practical systems use **timeouts + heartbeats**, accepting that a timeout can't distinguish a crashed node from a slow/partitioned one. This forces a choice:

- **Aggressive timeout** → fast failover but **false positives** (evict a healthy-but-slow node, causing needless churn / flapping).
- **Conservative timeout** → stable but slow to react to real failures.
- **Phi-accrual failure detectors** (Cassandra) output a *suspicion level* instead of a boolean, adapting the threshold to observed network variance — the sophisticated answer.

## Ordering: Lamport vs vector clocks

Lamport timestamps give a *total order* but **can't tell you whether two events were concurrent or causally related** — if `L(A) < L(B)`, A might have caused B or might just be unrelated. **Vector clocks** (one counter per node) *can* detect concurrency: if neither vector dominates the other, the events are **concurrent** → a genuine conflict that needs resolution (LWW or app-level merge / CRDT). This is exactly how Dynamo detects sibling writes. Use Lamport when you only need *a* consistent order; vector clocks when you must detect real conflicts.

## Interview probes you should survive

- *"With N=3, what R and W give you consistent reads?"* → Any R+W>3 (e.g., W=2,R=2). It forces the read and write quorums to overlap on a fresh replica. R+W≤N trades freshness for latency.
- *"Odd node counts prevent split-brain — but the old leader kept writing after a GC pause. How do you stop corruption?"* → Fencing tokens: the resource rejects writes carrying a stale (lower) token, so the deposed leader can't commit.
- *"A healthy node keeps getting evicted from the cluster — why?"* → Failure detector timeout too aggressive; a slow/GC-pausing node trips it (false positive). Loosen the timeout or use a phi-accrual detector.
- *"How do you know two writes actually conflict vs one caused the other?"* → Vector clocks: incomparable vectors ⇒ concurrent ⇒ conflict. Lamport timestamps can't distinguish this.
- *"Why isn't quorum enough for strong consistency?"* → Concurrent writes can still create divergent versions; you need read-repair and conflict resolution, and sloppy-quorum/hinted-handoff further weakens freshness.
