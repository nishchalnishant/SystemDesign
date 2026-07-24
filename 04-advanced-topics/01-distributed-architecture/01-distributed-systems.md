> [!NOTE]
> ** 5-Minute Summary**
>
> **What this covers:** The fundamental philosophy of why building apps on 100 computers is so much harder than building apps on 1 computer.
>
> **Key topics:**
> - **The Definition:** A distributed system is a bunch of separate computers acting like one giant computer.
> - **The Orchestra Analogy:** 100 musicians playing together. If they are perfectly synced, it sounds like one giant instrument. If the conductor is bad, it sounds like chaos.
> - **The 8 Fallacies of Distributed Computing:** The lies that junior developers believe when they first start building distributed systems. (e.g., "The network is reliable", "Latency is zero").
> - **CAP Theorem:** The fundamental rule that proves you cannot have a perfect database. You must always sacrifice something.
>
> **Key takeaway:** In a distributed system, everything that can go wrong *will* go wrong. The network will drop packets, servers will randomly reboot, and clocks will drift out of sync. You must write code that expects failure at every step.

---
module: 04-advanced-topics
status: unread
tags: [04-advanced-topics, system-design, distributed-systems]
---
# Distributed Systems - System Design Guide

> This guide explains the core philosophy and dangers of Distributed Systems using simple analogies.

---

## Why Should I Care?

Imagine a guy playing a guitar on the street. He controls the strings, he controls the tempo, and he controls the volume. If he wants to stop, he stops. This is a **Monolith** (a single server doing everything). It is incredibly easy to manage.

Now imagine a Symphony Orchestra with 100 musicians.
The Violinist cannot hear the Drummer because he is too far away. The Flute player's sheet music blows away in the wind. The Trumpet player falls asleep.
To the audience, they are supposed to sound like ONE single, beautiful instrument. But behind the scenes, it requires extreme coordination, a Conductor, and backup plans for when things go wrong.

This is a **Distributed System**.
In tech, it means taking 100 separate computers (some in New York, some in Tokyo) and connecting them together so the user thinks they are just talking to "Netflix" or "Google."

It is infinitely more powerful than the single guitar player, but it introduces terrifying new problems.

---

## The 8 Fallacies (The Lies We Tell Ourselves)

When junior developers first start building Distributed Systems, they write code based on 8 assumptions. All 8 of these assumptions are completely false. (First defined by Peter Deutsch at Sun Microsystems).

1. **"The network is reliable."**
   - *Reality:* Wi-Fi drops, cables get cut by construction workers, routers crash. Your code MUST have a retry mechanism.
2. **"Latency is zero."**
   - *Reality:* Data cannot travel faster than the speed of light. Sending data from NY to Tokyo takes time.
3. **"Bandwidth is infinite."**
   - *Reality:* You cannot send a 10GB video file instantly. You will clog the pipes.
4. **"The network is secure."**
   - *Reality:* Hackers are constantly listening. You must encrypt everything (HTTPS/TLS).
5. **"Topology doesn't change."**
   - *Reality:* Servers are constantly being added, removed, or crashing. IP addresses change daily.
6. **"There is one administrator."**
   - *Reality:* Multiple teams control different parts of the network. You can't just reboot the whole system yourself.
7. **"Transport cost is zero."**
   - *Reality:* AWS charges you real money for every megabyte of data that leaves their data center.
8. **"The network is homogeneous."**
   - *Reality:* Some servers use Linux, some use Windows. Some use ARM chips, some use Intel.

**The Lesson:** If you write code that expects the network to be perfect, your app will crash on Day 1.

---

## The CAP Theorem (Pick Two)

In a Distributed System, you cannot have a perfect database. The CAP Theorem mathematically proves that you must choose **exactly two** of the following three guarantees:

1. **Consistency (C):** Every time a user asks the database a question, they get the most recent, accurate answer. (Even if it takes 10 seconds to calculate).
2. **Availability (A):** Every time a user asks the database a question, they get an answer *instantly*. (Even if the answer is slightly outdated).
3. **Partition Tolerance (P):** If the network cable between Server 1 and Server 2 gets cut, the system keeps working.

### The Harsh Reality
Because network cables *always* break eventually (Fallacy #1), you **must** choose 'P'. Therefore, the CAP Theorem is actually a choice between 'C' and 'A'.

- **CP (Consistency + Partition Tolerance):** A Bank. If the network breaks, the Bank freezes your account and refuses to show your balance, rather than risk showing you an incorrect balance. (You chose Accuracy over Speed).
- **AP (Availability + Partition Tolerance):** Facebook. If the network breaks, Facebook will instantly show you an old version of your Newsfeed from 10 minutes ago, rather than showing you an error screen. (You chose Speed over Accuracy).

---

## Interview Questions to Practice

1. **"What is a Distributed System?"**
   *Answer:* A collection of independent computers that appear to its users as a single coherent system. They communicate and coordinate their actions by passing messages over a network to achieve a common goal.
2. **"Can you name a few of the 8 Fallacies of Distributed Computing?"**
   *Answer:* The network is reliable, latency is zero, bandwidth is infinite, and the network is secure. Assuming these are true leads to brittle systems that crash during standard network hiccups.
3. **"Explain the CAP Theorem and how it affects database choice."**
   *Answer:* The CAP Theorem states that in the presence of a network Partition (P), a distributed system must choose between strict Consistency (C) and high Availability (A). For a banking app, you choose a CP database (like HBase or MongoDB) to guarantee accurate balances. For a social media app, you choose an AP database (like Cassandra or DynamoDB) to guarantee the app always loads instantly, even if the data is slightly stale.

---

# 🎯 SDE-3 Deep Dive

The 8 fallacies and "pick two" are the vocabulary. Seniors are expected to know **why CAP is an oversimplification (PACELC), the real spectrum of consistency models between C and eventual, why exactly-once is a myth, and the impossibility results (FLP, two generals) that bound what's even possible.**

## CAP is incomplete — use PACELC

CAP only describes behavior *during a partition*, which is rare. **PACELC** completes it: **if Partition, choose A or C; Else (normal operation), choose Latency or Consistency.** This is the more useful lens because the L-vs-C trade is a decision you make on *every request*, not just during outages:

- **PC/EC** — DynamoDB with `ConsistentRead`, Spanner: consistent always, paying latency. 
- **PA/EL** — Cassandra, Dynamo, Riak: available under partition *and* low-latency normally, at the cost of consistency (tunable via quorum `R+W>N`).
- The senior insight: even a perfectly connected system pays a **latency tax for strong consistency** (a quorum/leader round trip), so the trade never disappears.

## Consistency is a spectrum, not a binary

"C or A" hides that there are many consistency models between linearizable and eventual — know the ladder:

- **Linearizable / strong** — reads see the latest write, as if one copy (Spanner, etcd, a Raft leader read).
- **Sequential / causal** — operations respect causal order (if A caused B, everyone sees A before B); the strongest model achievable in an always-available system.
- **Read-your-writes / monotonic reads** — *session* guarantees that make eventual consistency tolerable for users (you always see your own post).
- **Eventual** — replicas converge *eventually*; conflict resolution via LWW / version vectors / CRDTs ([`../../03-scaling/04-global-distribution.md`](../../03-scaling/04-global-distribution.md)).

Interviewers push here: "eventually consistent" is not one thing — a system offering *causal + read-your-writes* feels far stronger than raw eventual.

## The impossibility results that bound the design space

- **FLP impossibility:** in a fully asynchronous network, no deterministic consensus can guarantee *both* safety and liveness if even one node can fail — which is *why* real consensus (Raft/Paxos) relies on timeouts/randomization to make progress ([`../03-internals/02-consensus-protocols.md`](../03-internals/02-consensus-protocols.md)). It explains why you can't just "wait forever" for agreement.
- **Two Generals:** you cannot guarantee agreement over a lossy channel with a bounded number of messages — the theoretical root of why **exactly-once delivery is impossible**; you get at-least-once + idempotency or at-most-once. Ties to idempotency keys and the outbox pattern.
- **Byzantine faults:** the above assume nodes fail by *stopping* (crash-fault). Nodes that lie (Byzantine) need BFT protocols (blockchains) — usually out of scope for internal systems, but name the distinction.

## Time is a lie — you can't trust clocks

Fallacy-adjacent but deeper: **wall-clock timestamps across machines are unreliable** (drift, NTP jumps), so you cannot order events by comparing timestamps. Distributed systems order events with **logical clocks** — **Lamport clocks** (total-ish order) and **vector clocks** (detect concurrency/causality). Spanner's trick is **TrueTime**: GPS/atomic clocks bound the uncertainty so it can order globally — a hardware answer to a software impossibility.

## Interview probes you should survive

- *"CAP says pick two — but what do you actually give up when there's no partition?"* → Latency vs consistency (PACELC's ELSE branch). Strong consistency always costs a quorum/leader round trip, partition or not.
- *"Is 'eventually consistent' good enough for a user's own posts?"* → Not raw eventual — you need read-your-writes/monotonic-read session guarantees so a user always sees their own action; those sit above plain eventual on the ladder.
- *"Can you guarantee a message is processed exactly once?"* → No (two-generals). You get at-least-once delivery plus idempotent consumers, which is *effectively* once. 'Exactly-once' products mean idempotent processing, not exactly-once delivery.
- *"Why can't nodes just agree by waiting?"* → FLP: async networks can't distinguish a slow node from a dead one, so deterministic consensus can't guarantee both safety and progress; real systems use timeouts/randomization to break the tie.
- *"Why not order events by timestamp?"* → Clocks drift and jump across machines; use logical/vector clocks for causal ordering, or bounded-uncertainty clocks (Spanner TrueTime) for global order.
