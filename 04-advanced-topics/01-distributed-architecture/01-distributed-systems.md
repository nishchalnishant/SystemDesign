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
